from unittest.mock import patch
import pytest
import pytest_check as check

from messaging.message_model import BaseMessage
from preprocess.preprocess_worker import process_req, mk_error_msg
import json
import os

def assert_successful_precompute(mock_publish, captured_upload, job_id="12345"):
    mock_publish.assert_called_once()
    args, kwargs = mock_publish.call_args
    msg = args[0]
    msg = msg.to_dict()
    check.equal(msg["status"], "success", "Response status was not success")
    check.equal(msg["job_id"], "12345", "JobId mismatch")

    expected_filename = f"pre-process-job-{job_id}-output.csv"
    check.equal(captured_upload["filename"], expected_filename, "Filename mismatch")

    return captured_upload["df"]

def test_process_req_accumulates_points_correctly_in_grid(very_small_las_file, tmp_path, load_json):
    os.chdir(tmp_path)

    # Resulting grid is 1m per cell and max points are 10m -> 100cells
    request = load_json("valid_precompute_job_small_las_file.json")

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=very_small_las_file), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")
    #Get the grid border which is x0 and y0
    points = {
        (row['x0'], row['y0']): row['count'] for _, row in df.iterrows()
    }

    #Check if points are inside the grid 0.0, 0.0 -> grid cell 1 which goes from x0<->x1 => 0.0 <-> 1.0 and y0<->y1 => 0.0 <-> 1.0
    expected_points = {
        (0.0, 0.0): 2,
        (2.0, 2.0): 1,
        (9.0, 9.0): 1,
    }
    for key, expected in expected_points.items():
        check.equal(points.get(key, None), expected, f"Point count mismatch at grid cell {key}")

    check.equal(len(points), 100, "Unexpected number of grid cells")

    z_maxes = {
        (row['x0'], row['y0']): row['z_max'] for _, row in df.iterrows()
    }
    expected_zmax = {
        (0.0, 0.0): 10.0,
        (2.0, 2.0): 20.0,
        (9.0, 9.0): 15.0,
    }
    for key, expected in expected_zmax.items():
        check.equal(z_maxes.get(key, None), expected, f"Z_MAX mismatch at grid cell {key}")

    check.equal(df.loc[0, 'z_min'], 5.0, "Z_MIN in grid cell 0 is not 5.0")
    check.equal(df['z_max'].max(), 20.0, "Max Z_MAX value mismatch")
    check.equal(df['z_min'].min(), 5.0, "Min Z_MAX value mismatch")

@pytest.mark.parametrize("generated_las_file", [{
    "num_points": 1000,
    "x_range": (0, 100),
    "y_range": (0, 100),
    "z_range": (0, 60),
}], indirect=True)
def test_precompute_generates_grid_with_all_points(generated_las_file, tmp_path, load_json):
    os.chdir(tmp_path)

    num_points = 1000
    request = load_json("valid_precompute_job_100_by_100.json")

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=generated_las_file[0]), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")

    check.equal(df['count'].sum(), num_points, f"Number of points should be {num_points}")

def test_invalid_job_msg_returns_error_msg(load_json):
    invalid_job_msg = load_json("invalid_precompute_job.json")
    job_id = invalid_job_msg["jobId"]
    with patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:
        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(invalid_job_msg))

        mock_publish.assert_called_once()

        expected_msg = mk_error_msg("Precompute job is cancelled because job request is invalid", file_id=2, comparison_id = 1) #TODO: This expected message might change later which could lead to failed test

        args, kwargs = mock_publish.call_args
        msg = args[0]

        assert isinstance(msg, BaseMessage)
        assert msg.payload == expected_msg


def test_percentile_masking_filters_lower_bound(small_las_file, tmp_path, load_json):
    """Test that percentile lower bound filtering works correctly.

    The small_las_file has vegetation heights: [1.5, 2.5, 3.5, 3.6, 3.7, 4.5, 4.6, 4.7, 1.4, 1.6, 1.55, 1.58]
    With a lower bound of 50th percentile, roughly half the points should be filtered out.
    """
    os.chdir(tmp_path)

    request = load_json("valid_precompute_job_small_las_file.json")
    # Filter to lower 50th percentile (exclude upper half)
    request["pointFilterLowerBound"] = 0
    request["pointFilterUpperBound"] = 50

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")

    # With 50th percentile cutoff, we should have fewer points than without filtering
    total_points = df['count'].sum()
    # Small las file has 12 points total
    # The lower 50% should be approximately 6 points
    check.is_true(0 < total_points < 12, f"Percentile filter should reduce points, got {total_points} out of 12")


def test_percentile_masking_filters_upper_bound(small_las_file, tmp_path, load_json):
    """Test that percentile upper bound filtering works correctly.

    With an upper bound of 50th percentile, only the lower half of points should pass.
    """
    os.chdir(tmp_path)

    request = load_json("valid_precompute_job_small_las_file.json")
    # Filter to upper 50th percentile (exclude lower half)
    request["pointFilterLowerBound"] = 50
    request["pointFilterUpperBound"] = 100

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")

    # With 50th percentile lower bound, we should have fewer points than without filtering
    total_points = df['count'].sum()
    # Small las file has 12 points total
    check.is_true(0 < total_points < 12, f"Percentile filter should reduce points, got {total_points} out of 12")


def test_percentile_masking_combined_with_bbox(small_las_file, tmp_path, load_json):
    """Test that percentile masking is properly combined with bounding box filtering.

    This tests the combined_mask logic that applies both bbox and percentile filters together.
    """
    os.chdir(tmp_path)

    request = load_json("valid_precompute_job_small_las_file.json")
    # Restrict bounding box to one region
    request["bboxes"] = [
        {
            "xMin": 0.0,
            "xMax": 3.0,
            "yMin": 0.0,
            "yMax": 3.0
        }
    ]
    # Also apply percentile filter
    request["pointFilterLowerBound"] = 25
    request["pointFilterUpperBound"] = 75

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")

    # All points should be within the restricted bbox (0,0) to (3,3)
    for _, row in df.iterrows():
        if row['count'] > 0:
            check.is_true(row['x0'] >= 0.0 and row['x0'] < 3.0, f"Point x0 {row['x0']} outside bbox")
            check.is_true(row['y0'] >= 0.0 and row['y0'] < 3.0, f"Point y0 {row['y0']} outside bbox")

    # Should have fewer points than all 12 in the file
    total_points = df['count'].sum()
    check.is_true(0 < total_points < 12, f"Combined filter should reduce points, got {total_points} out of 12")


def test_percentile_masking_no_filter_includes_all_points(small_las_file, tmp_path, load_json):
    """Test that when lower_bound=0 and upper_bound=100, all points pass the percentile filter."""
    os.chdir(tmp_path)

    request = load_json("valid_precompute_job_small_las_file.json")
    # No percentile filtering (0-100 range)
    request["pointFilterLowerBound"] = 0
    request["pointFilterUpperBound"] = 100

    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
            patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
            patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher:

        mock_instance = MockPublisher.return_value
        mock_publish = mock_instance.publish_preprocessing_result

        process_req(None, None, None, json.dumps(request))

    df = assert_successful_precompute(mock_publish, captured_upload, job_id="12345")

    # Small las file has 12 points, all should pass when no percentile filtering
    total_points = df['count'].sum()
    check.equal(total_points, 12, f"No percentile filter should include all 12 points, got {total_points}")

def test_precompute_skips_global_stats_when_all_disabled(small_las_file, tmp_path, load_json):
    """Test that global statistics are NOT calculated when both flags are disabled."""
    os.chdir(tmp_path)
    
    request = load_json("valid_precompute_job_small_las_file.json")
    request["pointFilterEnabled"] = False
    request["outlierDetectionEnabled"] = False
    
    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"
        
    # We patch calculate_global_stats to fail if called
    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats") as mock_stats:
            
        # If this is called, it should fail the test
        mock_stats.side_effect = AssertionError("Global stats should not be calculated")
        
        process_req(None, None, None, json.dumps(request))
        
        mock_stats.assert_not_called()
        
    df = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    # Should have all points and 0 outliers
    total_points = df['count'].sum()
    check.equal(total_points, 12, f"Should include all 12 points, got {total_points}")
    check.equal(df['veg_height_outlier_count'].sum(), 0, "Should have 0 outliers when detection disabled")


def test_precompute_runs_global_stats_for_outliers_only(small_las_file, tmp_path, load_json):
    """Test using outlier detection only."""
    os.chdir(tmp_path)
    
    request = load_json("valid_precompute_job_small_las_file.json")
    request["pointFilterEnabled"] = False
    request["outlierDetectionEnabled"] = True
    
    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"
    
    # We wrap the real calculate_global_stats to verify it is called
    from preprocess.preprocess_worker import calculate_global_stats as real_stats
    
    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats", side_effect=real_stats) as mock_stats:
            
        process_req(None, None, None, json.dumps(request))
        
        mock_stats.assert_called_once()    

    df = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    # In the small las file, there might not be real outliers given small sample size, 
    # but at least the code path was exercised and stats were calculated.
    # The important part is that global stats were calculated (asserted above).
    total_points = df['count'].sum()
    check.equal(total_points, 12, "Should include all points (filter disabled)")


def test_precompute_runs_global_stats_for_filter_only(small_las_file, tmp_path, load_json):
    """Test using percentile filter only."""
    os.chdir(tmp_path)
    
    request = load_json("valid_precompute_job_small_las_file.json")
    request["pointFilterEnabled"] = True
    request["outlierDetectionEnabled"] = False
    
    # Set filter to exclude some points
    request["pointFilterLowerBound"] = 0
    request["pointFilterUpperBound"] = 50
    
    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"
    
    from preprocess.preprocess_worker import calculate_global_stats as real_stats

    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats", side_effect=real_stats) as mock_stats:
            
        process_req(None, None, None, json.dumps(request))
        
        mock_stats.assert_called_once()

    df = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    # Should be filtered
    total_points = df['count'].sum()
    check.is_true(0 < total_points < 12, f"Filter should reduce points, got {total_points}")
    # But outliers should always be 0
    check.equal(df['veg_height_outlier_count'].sum(), 0, "Should have 0 outliers when detection disabled")


def test_precompute_applies_both_filter_and_outlier_detection(small_las_file, tmp_path, load_json):
    """Test using both percentile filter and outlier detection."""
    os.chdir(tmp_path)
    
    request = load_json("valid_precompute_job_small_las_file.json")
    request["pointFilterEnabled"] = True
    request["outlierDetectionEnabled"] = True
    
    # Filter 0-100 (loose) to allow most points but potentially catch outliers if any exist in the distribution
    request["pointFilterLowerBound"] = 0
    request["pointFilterUpperBound"] = 100
    
    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"
        
    from preprocess.preprocess_worker import calculate_global_stats as real_stats
    
    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats", side_effect=real_stats) as mock_stats:
            
        process_req(None, None, None, json.dumps(request))
        
        mock_stats.assert_called_once()    

    df = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    # Check that we have results
    total_points = df['count'].sum()
    check.greater(total_points, 0)
    
    # but we verify the code path executes without error and produces a result with potential outlier columns
    check.is_in('veg_height_outlier_count', df.columns)


def test_precompute_uses_configured_outlier_factor(small_las_file, tmp_path, load_json):
    """Test that configuring the outlier deviation factor changes detection behavior."""
    os.chdir(tmp_path)
    
    # CASE 1: Very strict factor (0.1 sigma) -> Should find many outliers
    request = load_json("valid_precompute_job_small_las_file.json")
    request["pointFilterEnabled"] = False
    request["outlierDetectionEnabled"] = True
    request["outlierDeviationFactor"] = 0.1
    
    captured_upload = {}
    def fake_upload_file_by_type(filename, df):
        captured_upload["filename"] = filename
        captured_upload["df"] = df
        return "http://minio.local/bucket/preprocess.csv"
        
    from preprocess.preprocess_worker import calculate_global_stats as real_stats
    
    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats", side_effect=real_stats) as mock_stats:
            
        process_req(None, None, None, json.dumps(request))
        
    df = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    outliers_strict = df['veg_height_outlier_count'].sum()
    check.greater(outliers_strict, 0, "Strict factor (0.1) should find outliers")

    # CASE 2: Very loose factor (10.0 sigma) -> Should find NO outliers
    request["outlierDeviationFactor"] = 10.0
    
    with patch("preprocess.preprocess_worker.file_handler.fetch_file", return_value=small_las_file), \
         patch("preprocess.preprocess_worker.file_handler.upload_file_by_type", side_effect=fake_upload_file_by_type), \
         patch("preprocess.preprocess_worker.ResultPublisher") as MockPublisher, \
         patch("preprocess.preprocess_worker.calculate_global_stats", side_effect=real_stats) as mock_stats:
            
        process_req(None, None, None, json.dumps(request))
        
    df_loose = assert_successful_precompute(MockPublisher.return_value.publish_preprocessing_result, captured_upload, job_id="12345")
    
    outliers_loose = df_loose['veg_height_outlier_count'].sum()
    check.equal(outliers_loose, 0, "Loose factor (10.0) should find NO outliers")
    
    check.greater(outliers_strict, outliers_loose, "Strict factor should find more outliers than loose factor")

