from unittest.mock import patch

import pandas as pd
import pytest
import pytest_check as check
from preprocess_worker import process_req, calculate_grid
import json
import os

def test_process_req_accumulates_points_correctly_in_grid(small_las_file, tmp_path):
    os.chdir(tmp_path)

    # Resulting grid is 1m per cell and max points are 10m -> 100cells
    grid_def = {
        "x_min": 0.0,
        "x_max": 10.0,
        "y_min": 0.0,
        "y_max": 10.0,
        "x": 1.0,
        "y": 1.0,
    }

    request = {
        "url": "http://example.com/test.las",
        "grid": grid_def,
        "job_id": "12345"
    }

    with patch("preprocess_worker.file_handler.download_file", return_value=small_las_file), \
        patch("preprocess_worker.file_handler.upload_file_by_type"), \
        patch("preprocess_worker.write_result_to_minio"):

        process_req(None, None, None, json.dumps(request))

    output_csv = tmp_path / "pre-process-job-12345-output.csv"
    assert (output_csv.exists())

    df = pd.read_csv(output_csv, index_col=0)
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
def test_precompute_generates_grid_with_all_points(generated_las_file, tmp_path):
    os.chdir(tmp_path)

    num_points = 1000
    grid_def = {
        "x_min": 0.0,
        "x_max": 100.0,
        "y_min": 0.0,
        "y_max": 100.0,
        "x": 1.0,
        "y": 1.0,
    }

    request = {
        "url": "http://example.com/test.las",
        "grid": grid_def,
        "job_id": "12345"
    }

    with patch("preprocess_worker.file_handler.download_file", return_value=generated_las_file[0]), \
            patch("preprocess_worker.file_handler.upload_file_by_type"), \
            patch("preprocess_worker.write_result_to_minio"):

        process_req(None, None, None, json.dumps(request))

    output_csv = tmp_path / "pre-process-job-12345-output.csv"
    df = pd.read_csv(output_csv, index_col=0)
    assert df['count'].sum() == num_points, f"Number of points should be {num_points}"