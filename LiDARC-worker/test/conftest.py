import os
import sys
import tempfile

import laspy
import numpy as np
import pytest
import json
from importlib.resources import files

project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../src"))


sys.path.append(project_root)

def pytest_addoption(parser):
    parser.addoption(
        "--e2e",
        action="store_true",
        default=False,
        help="run e2e tests",
    )
    parser.addoption(
        "--external-only",
        action="store_true",
        default=False,
        help="run only tests that are marked with @pytest.mark.allow_external",
    )

def pytest_configure(config):
    config.addinivalue_line("markers", "e2e: slow/external integration tests")

def pytest_collection_modifyitems(config, items):
    if config.getoption("--external-only"):
        skip_it = pytest.mark.skip(reason="requires keyword allow_external")
        for item in items:
            if "allow_external" not in item.keywords:
                item.add_marker(skip_it)
        return
    if config.getoption("--e2e"):
        return
    skip_it = pytest.mark.skip(reason="requires --e2e")
    for item in items:
        if "e2e" in item.keywords:
            item.add_marker(skip_it)

@pytest.fixture(scope="session")
def load_fixture():
    """
    Provides a function to load a file from the fixture folder.
    """
    def _load(path):
        p = files("test.fixtures").joinpath(path)
        return p.read_text(encoding="utf-8")
    return _load


@pytest.fixture(scope="session")
def load_json(load_fixture):
    """
    Provides a function to load a JSON from the fixture folder.
    """
    def _load(path):
        return json.loads(load_fixture(path))
    return _load

@pytest.fixture(scope="module")
def very_small_las_file():
    header = laspy.LasHeader(point_format=3, version="1.4")


    with tempfile.NamedTemporaryFile(suffix=".las") as tmp:
        filename = tmp.name

    las = laspy.LasData(header)

    las.x = np.array([0.5, 0.9, 2.0, 9.5])
    las.y = np.array([0.5, 0.8, 2.5, 9.2])
    las.z = np.array([5.0, 10.0, 20.0, 15.0])

    veg_height_values = np.array([1.5, 2.5, 3.5, 4.5], dtype=np.float64)
    las.gps_time = veg_height_values

    las.write(filename)

    return filename

@pytest.fixture(scope="module")
def small_las_file():
    header = laspy.LasHeader(point_format=3, version="1.4")

    with tempfile.NamedTemporaryFile(suffix=".las", delete=False) as tmp:
        filename = tmp.name

    las = laspy.LasData(header)

    las.x = np.array([0.5, 0.9, 2.0, 2.1, 2.05, 9.5, 9.6, 9.7, 0.4, 0.6, 0.55, 0.58])
    las.y = np.array([0.5, 0.8, 2.5, 2.6, 2.55, 9.2, 9.3, 9.4, 0.45, 0.7, 0.52, 0.6])
    las.z = np.array([5.0, 10.0, 20.0, 18.0, 19.0, 15.0, 14.5, 16.0, 4.5, 6.0, 5.5, 6.2])

    veg_height_values = np.array([
        1.5, 2.5,           # cluster near (0.5, 0.5)
        3.5, 3.6, 3.7,      # cluster near (2.0, 2.5)
        4.5, 4.6, 4.7,      # cluster near (9.5, 9.2)
        1.4, 1.6, 1.55, 1.58 # cluster near (0.5, 0.6)
    ], dtype=np.float64)

    las.gps_time = veg_height_values

    las.write(filename)

    return filename

@pytest.fixture
def generated_las_file(request):
    num_points = getattr(request, 'param', {}).get("num_points", 1000)
    x_range = getattr(request, 'param', {}).get("x_range", (0, 100))
    y_range = getattr(request, 'param', {}).get("y_range", (0, 100))
    z_range = getattr(request, 'param', {}).get("z_range", (0, 100))

    filename, max_z = generate_las_points(num_points, x_range, y_range, z_range)
    yield filename, max_z

    if os.path.exists(filename):
        os.remove(filename)

def generate_las_points(num_points=1000, x_range=(0, 100), y_range=(0, 100), z_range=(0, 60), seed=42):
    """
    Generate a temporary LAS file with `num_points` random points within the specified bounding box ranges.

    :param num_points: Number of points to generate
    :param x_range: Bounding box x coordinates
    :param y_range: Bounding box y coordinates
    :param z_range: Bounding box z coordinates
    :return:
        filename (str): path to temporary LAS file
        max_z (float): maximum z value in the LAS file
    """
    header = laspy.LasHeader(point_format=3, version="1.4")

    with tempfile.NamedTemporaryFile(suffix=".las") as tmp:
        filename = tmp.name

    las = laspy.LasData(header)
    rng = np.random.default_rng(seed)

    las.x = rng.uniform(x_range[0], x_range[1], size=num_points)
    las.y = rng.uniform(y_range[0], y_range[1], size=num_points)
    las.z = rng.uniform(z_range[0], z_range[1], size=num_points)

    max_z = np.max(las.z)

    las.write(filename)

    return filename, max_z
