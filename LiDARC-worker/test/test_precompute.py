from unittest.mock import patch
import pytest
import pytest_check as check

from messaging.message_model import BaseMessage
from preprocess.preprocess_worker import process_req, calculate_grid, mk_error_msg
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

    check.equal(len(points), 3, "Unexpected number of grid cells")

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

        expected_msg = mk_error_msg("Precompute job is cancelled because job request is invalid") #TODO: This expected message might change later which could lead to failed test

        args, kwargs = mock_publish.call_args
        msg = args[0]

        assert isinstance(msg, BaseMessage)
        assert msg.payload == expected_msg