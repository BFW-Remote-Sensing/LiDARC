import math
import pytest

from messaging.message_model import BaseMessage
from visualization_chunking.visualization_worker import chunking_func


def make_cell(x0, y0, dx=1.0, dy=1.0, a=0.0, b=0.0):
    """Helper to create a cell dict compatible with chunking_func."""
    return {
        "x0": float(x0),
        "y0": float(y0),
        "x1": float(x0 + dx),
        "y1": float(y0 + dy),
        "veg_height_max_a": float(a),
        "veg_height_max_b": float(b),
    }


def assert_cell_coords(cell, x0, y0, x1, y1, tol=1e-9):
    assert abs(cell["x0"] - x0) <= tol
    assert abs(cell["y0"] - y0) <= tol
    assert abs(cell["x1"] - x1) <= tol
    assert abs(cell["y1"] - y1) <= tol


def assert_close(a, b, tol=1e-9):
    assert abs(a - b) <= tol


def test_empty_input_returns_empty():
    assert chunking_func([], 2) == []
    assert chunking_func(None, 2) == []  # type: ignore[arg-type]


def test_invalid_chunk_size_raises():
    with pytest.raises(ValueError):
        chunking_func([[make_cell(0, 0)]], 0)


def test_cols_zero_returns_empty():
    # matrix with rows but all rows empty
    assert chunking_func([[], [], []], 2) == []


def test_simple_2x2_chunk_to_1x1_averages_and_coords():
    # 2x2 grid, chunk_size=2 => 1 output cell
    # Values A: 1,2,3,4 => avg 2.5
    # Values B: 10,20,30,40 => avg 25
    m = [
        [make_cell(0, 0, a=1, b=10), make_cell(1, 0, a=2, b=20)],
        [make_cell(0, 1, a=3, b=30), make_cell(1, 1, a=4, b=40)],
    ]

    out = chunking_func(m, 2)
    assert len(out) == 1
    assert len(out[0]) == 1

    cell = out[0][0]
    # origin=(0,0), dx=1, dy=1, bc=0, br=0 => block is (0,0)-(2,2)
    assert_cell_coords(cell, 0.0, 0.0, 2.0, 2.0)
    assert_close(cell["veg_height_max_a"], 2.5)
    assert_close(cell["veg_height_max_b"], 25.0)
    # your delta_z is sum_b - sum_a (not mean diff)
    assert_close(cell["delta_z"], ((10+20+30+40) - (1+2+3+4)) / 4)
    assert cell["count"] == 4
    assert_close(cell["coverage"], 1.0)


def test_ragged_rows_missing_entries_are_ignored_but_block_still_created():
    # rows=2, cols=maxlen=2, chunk_size=2 => out 1x1 block
    # second row has only one cell -> missing second cell is a ragged hole
    m = [
        [make_cell(0, 0, a=1, b=10), make_cell(1, 0, a=2, b=20)],
        [make_cell(0, 1, a=3, b=30)],  # missing (1,1)
    ]

    out = chunking_func(m, 2)
    cell = out[0][0]

    # 3 cells counted
    assert cell["count"] == 3
    assert_close(cell["coverage"], 3 / 4)

    # avg over existing cells only
    assert_close(cell["veg_height_max_a"], (1+2+3) / 3)
    assert_close(cell["veg_height_max_b"], (10+20+30) / 3)


def test_explicit_none_cells_are_ignored():
    # 2x2 but with a None hole
    m = [
        [make_cell(0, 0, a=1, b=10), None],
        [make_cell(0, 1, a=3, b=30), make_cell(1, 1, a=4, b=40)],
    ]

    out = chunking_func(m, 2)
    cell = out[0][0]
    assert cell["count"] == 3
    assert_close(cell["veg_height_max_a"], (1+3+4) / 3)
    assert_close(cell["veg_height_max_b"], (10+30+40) / 3)


def test_empty_block_is_emitted_with_zeros_and_correct_coords():
    # 3x3, chunk_size=2 => out_rows=2, out_cols=2
    # Put data only in top-left cell. All other blocks mostly empty.
    # IMPORTANT: we still need at least one valid cell so dx/dy/origin can be inferred.
    m = [
        [make_cell(0, 0, a=5, b=7), None, None],
        [None, None, None],
        [None, None, None],
    ]

    out = chunking_func(m, 2)

    assert len(out) == 2
    assert len(out[0]) == 2
    assert len(out[1]) == 2

    # Block (0,0): covers rows 0-1, cols 0-1 => has one cell
    c00 = out[0][0]
    assert c00["count"] == 1
    assert_close(c00["veg_height_max_a"], 5.0)
    assert_close(c00["veg_height_max_b"], 7.0)
    assert_cell_coords(c00, 0.0, 0.0, 2.0, 2.0)

    # Block (0,1): br=0 bc=2 -> coords (2,0)-(4,2) even if empty
    c01 = out[0][1]
    assert c01["count"] == 0
    assert_close(c01["veg_height_max_a"], 0.0)
    assert_close(c01["veg_height_max_b"], 0.0)
    assert_close(c01["coverage"], 0.0)
    assert_cell_coords(c01, 2.0, 0.0, 4.0, 2.0)

    # Block (1,0): br=2 bc=0 -> (0,2)-(2,4)
    c10 = out[1][0]
    assert c10["count"] == 0
    assert_cell_coords(c10, 0.0, 2.0, 2.0, 4.0)

    # Block (1,1): br=2 bc=2 -> (2,2)-(4,4)
    c11 = out[1][1]
    assert c11["count"] == 0
    assert_cell_coords(c11, 2.0, 2.0, 4.0, 4.0)


def test_output_dimensions_use_ceil_not_floor():
    # rows=5 cols=5 chunk=2 -> out 3x3
    m = [[make_cell(c, r, a=1, b=2) for c in range(5)] for r in range(5)]
    out = chunking_func(m, 2)
    assert len(out) == 3
    assert all(len(row) == 3 for row in out)


def test_origin_is_global_min_x0_y0():
    # Place a cell with negative x0/y0 somewhere; origin should pick it
    m = [
        [None, make_cell(-5, -10, a=1, b=1)],
        [make_cell(0, 0, a=2, b=2)],
    ]
    out = chunking_func(m, 2)
    # out is 1x1 (rows=2 cols=2 chunk=2)
    cell = out[0][0]
    # origin should be (-5, -10), dx/dy from first found valid cell (may be that negative one)
    # block coords: origin + bc*dx/br*dy with bc=0 br=0 => x0=-5 y0=-10 x1=-5+2 y1=-10+2
    assert_cell_coords(cell, -5.0, -10.0, -3.0, -8.0)
