"""Fast tests of the detector pre- and post-processing with fake output tensors (rule CV4)."""

import math

import numpy as np
import pytest

from kosmo_camera.detector import decode, preprocess, resize_bilinear
from kosmo_camera.model_spec import CLASS_NAMES, IMAGENET_MEAN, IMAGENET_STD, RESOLUTION, check_resolution

NO_OBJECT = -20.0


def fake_outputs(queries):
    """queries: list of (cx, cy, class index or None, logit). Returns boxes (1, Q, 4), logits (1, Q, 6)."""
    boxes = np.zeros((1, len(queries), 4), dtype=np.float32)
    logits = np.full((1, len(queries), len(CLASS_NAMES) + 1), NO_OBJECT, dtype=np.float32)
    for q, (cx, cy, cls, logit) in enumerate(queries):
        boxes[0, q] = (cx, cy, 0.1, 0.2)
        if cls is not None:
            logits[0, q, cls] = logit
    return boxes, logits


def test_centre_scales_to_pixels_of_a_non_square_image():
    boxes, logits = fake_outputs([(0.25, 0.5, 0, 3.0)])
    point = decode(boxes, logits, width=640, height=480)[0]
    assert point.x == pytest.approx(160.0)
    assert point.y == pytest.approx(240.0)
    assert point.class_name == "tip"


def test_aspect_ratio_is_not_square_so_axes_scale_independently():
    boxes, logits = fake_outputs([(0.5, 0.5, 1, 1.0)])
    wide = decode(boxes, logits, width=1000, height=100)[0]
    tall = decode(boxes, logits, width=100, height=1000)[0]
    assert (wide.x, wide.y) == pytest.approx((500.0, 50.0))
    assert (tall.x, tall.y) == pytest.approx((50.0, 500.0))


def test_score_is_sigmoid_and_points_sorted_by_decreasing_score():
    boxes, logits = fake_outputs([(0.1, 0.1, 2, -1.0), (0.2, 0.2, 3, 2.0), (0.3, 0.3, 4, 0.0)])
    points = decode(boxes, logits, width=100, height=100)
    assert [p.class_name for p in points] == ["cal3", "cal4", "cal2"]
    assert points[0].score == pytest.approx(1 / (1 + math.exp(-2.0)))
    assert points[1].score == pytest.approx(0.5)
    scores = [p.score for p in points]
    assert scores == sorted(scores, reverse=True)


def test_no_object_slot_is_ignored():
    boxes = np.zeros((1, 1, 4), dtype=np.float32)
    logits = np.full((1, 1, 6), -20.0, dtype=np.float32)
    logits[0, 0, 5] = 30.0
    point = decode(boxes, logits, width=10, height=10)[0]
    assert point.score < 1e-6


def test_decode_rejects_wrong_shapes():
    with pytest.raises(ValueError):
        decode(np.zeros((1, 3, 5), np.float32), np.zeros((1, 3, 6), np.float32), 10, 10)
    with pytest.raises(ValueError):
        decode(np.zeros((1, 3, 4), np.float32), np.zeros((1, 2, 6), np.float32), 10, 10)


def test_resize_identity_and_constant_image():
    rng = np.random.default_rng(1)
    image = rng.integers(0, 256, size=(8, 8, 3), dtype=np.uint8)
    assert resize_bilinear(image, 8, 8) == pytest.approx(image.astype(np.float32))
    flat = np.full((5, 9, 3), 77, dtype=np.uint8)
    assert resize_bilinear(flat, 16, 16) == pytest.approx(np.full((16, 16, 3), 77.0))


def test_resize_half_pixel_two_pixels_to_four():
    image = np.array([[[0.0], [100.0]]])  # height 1, width 2
    out = resize_bilinear(image, 1, 4)[0, :, 0]
    assert out == pytest.approx([0.0, 25.0, 75.0, 100.0])


def test_preprocess_layout_and_normalisation():
    image = np.zeros((6, 8, 3), dtype=np.uint8)
    image[..., 0] = 255
    tensor = preprocess(image, 32)
    assert tensor.shape == (1, 3, 32, 32)
    assert tensor.dtype == np.float32
    assert tensor[0, 0, 0, 0] == pytest.approx((1.0 - IMAGENET_MEAN[0]) / IMAGENET_STD[0])
    assert tensor[0, 1, 5, 5] == pytest.approx((0.0 - IMAGENET_MEAN[1]) / IMAGENET_STD[1])


def test_preprocess_rejects_grayscale():
    with pytest.raises(ValueError):
        preprocess(np.zeros((4, 4), dtype=np.uint8), 32)


def test_resolution_is_valid_for_the_nano_backbone():
    assert check_resolution(RESOLUTION) == RESOLUTION
    with pytest.raises(ValueError):
        check_resolution(RESOLUTION + 1)
