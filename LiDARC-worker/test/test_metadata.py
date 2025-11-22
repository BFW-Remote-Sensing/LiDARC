import pytest
import laspy
from metadata_worker import parse_coordinate_system, extract_metadata

# unit tests for parse_coordinate_system
def test_parse_coordinate_system_with_vvalid_cs_in_header(las_with_header):
    file_path = las_with_header(
        overrides={"system_identifier": "OTHER"},
        with_crs_header=True
    )
    with laspy.open(file_path) as file:
        header = file.header
        crs = parse_coordinate_system(header)
    assert crs == "EPSG:31256"

def test_parse_coordinate_system_with_valid_epsg_cs_in_system_identifier(las_with_header):
    file_path = las_with_header()
    with laspy.open(file_path) as file:
        header = file.header
        crs = parse_coordinate_system(header)
    assert crs == "EPSG:31256"

def test_parse_coordinate_system_with_valid_esri_cs_in_system_identifier(las_with_header):
    file_path = las_with_header(overrides={
        "system_identifier": "102067;some_creator"
    })
    with laspy.open(file_path) as file:
        header = file.header
        crs = parse_coordinate_system(header)
    assert crs == "ESRI:102067"

def test_parse_coordinate_system_with_invalid_cs_in_system_identifier(las_with_header):
    file_path = las_with_header(overrides={
        "system_identifier": "9999;some_creator"
    })
    with laspy.open(file_path) as file:
        header = file.header
        crs = parse_coordinate_system(header)
    assert crs == "UNKNOWN:UNKNOWN"

# unit test for extract_metadata
def test_extract_metadata_all_fields(las_with_header):
    file_path = las_with_header()
    metadata = extract_metadata(file_path)

    assert metadata["filename"] == "graz2021_block6_060_065_elv.las"
    assert metadata["capture_year"] == 2021
    assert metadata["size_bytes"] > 0
    assert metadata["min_x"] == pytest.approx(0.5)
    assert metadata["min_y"] == pytest.approx(0.5)
    assert metadata["min_z"] == pytest.approx(5.0)
    assert metadata["max_x"] == pytest.approx(9.5)
    assert metadata["max_y"] == pytest.approx(9.2)
    assert metadata["max_z"] == pytest.approx(20.0)
    assert metadata["system_identifier"] == "31256;austria2022"
    assert metadata["las_version"] == "1.4"
    assert metadata["capture_software"] == "bfwLasProcessing;MatchT 8.0"
    assert metadata["point_count"] == 4
    assert metadata["file_creation_date"] == "2024-03-14"
    assert metadata["coordinate_system"] == "EPSG:31256"

# TODO integration tests: test some errors --> error messages published; check whats published when everything works;