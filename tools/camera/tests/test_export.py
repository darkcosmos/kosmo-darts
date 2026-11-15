"""Slow: LiteRT export of an untrained Nano, run with ai-edge-litert, parity with eager PyTorch (rule CV4)."""

import hashlib

import numpy as np
import pytest
import torch

from kosmo_camera.detector import LiteRtDetector, preprocess
from kosmo_camera.export import build_nano, export_model, rebuild_command
from kosmo_camera.model_spec import NUM_CLASSES, RESOLUTION

pytestmark = pytest.mark.slow

TORCH_SEED = 42
IMAGE_SEED = 7
TOP_QUERIES = 10
BOX_TOLERANCE = 1e-3  # absolute, regression bound quoted on the rfdetr LiteRT export page
LOGIT_TOLERANCE = 0.1  # absolute, same page


@pytest.fixture(scope="module")
def exported(tmp_path_factory):
    torch.manual_seed(TORCH_SEED)
    model = build_nano(None, RESOLUTION)
    out_dir = tmp_path_factory.mktemp("export")
    info = export_model(model, out_dir, RESOLUTION, rebuild_command(None, out_dir, RESOLUTION))
    return model, info


def test_export_file_hash_size_and_command(exported):
    _, info = exported
    assert info.path.is_file() and info.path.suffix == ".tflite"
    assert info.size_bytes == info.path.stat().st_size > 0
    assert info.sha256 == hashlib.sha256(info.path.read_bytes()).hexdigest()
    assert "kosmo_camera.export" in info.command and str(RESOLUTION) in info.command


def test_litert_loads_with_expected_shapes(exported):
    _, info = exported
    detector = LiteRtDetector(info.path)
    assert detector.input_size == RESOLUTION
    image = np.random.default_rng(IMAGE_SEED).integers(0, 256, size=(480, 640, 3), dtype=np.uint8)
    boxes, logits = detector.run_raw(preprocess(image, RESOLUTION))
    assert boxes.shape == (1, 300, 4)
    assert logits.shape == (1, 300, NUM_CLASSES + 1)
    points = detector.detect(image)
    assert len(points) == 300
    assert [p.score for p in points] == sorted((p.score for p in points), reverse=True)


def test_litert_matches_eager_pytorch_on_top_queries(exported):
    model, info = exported
    image = np.random.default_rng(IMAGE_SEED).integers(0, 256, size=(480, 640, 3), dtype=np.uint8)
    tensor = preprocess(image, RESOLUTION)
    lite_boxes, lite_logits = LiteRtDetector(info.path).run_raw(tensor)
    net = model.model.model
    net.eval()
    net.export()  # export graph: 300 queries, (boxes, logits), as converted
    with torch.no_grad():
        eager_boxes, eager_logits = (t.numpy() for t in net(torch.from_numpy(tensor)))
    top = np.argsort(-eager_logits[0, :, :NUM_CLASSES].max(axis=1))[:TOP_QUERIES]
    assert np.abs(lite_boxes[0, top] - eager_boxes[0, top]).max() < BOX_TOLERANCE
    assert np.abs(lite_logits[0, top] - eager_logits[0, top]).max() < LOGIT_TOLERANCE
