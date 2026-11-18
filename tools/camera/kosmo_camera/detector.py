"""Detector that runs the exported `.tflite` directly with ai-edge-litert (rule CV4, root plan "Measure on the exported
file only"). This is the exact chain 0b-2 ports to Kotlin: squash resize, NCHW float32 ImageNet normalisation, decode.

Preprocessing is a plain bilinear resize of the whole photo to the square input (half-pixel centres, no antialias),
the same as `rfdetr` training and `predict`, so there are no letterbox offsets: x and y scale independently.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np

from kosmo_camera.model_spec import CLASS_NAMES, IMAGENET_MEAN, IMAGENET_STD, NUM_CLASSES, PIXEL_SCALE


@dataclass(frozen=True)
class Point:
    """One detection reduced to its box centre, in pixels of the original image."""

    class_name: str
    x: float
    y: float
    score: float


class Detector(Protocol):
    def detect(self, image: np.ndarray) -> list[Point]:
        """`image` is an RGB uint8 array (height, width, 3). Returns points sorted by decreasing score."""


def resize_bilinear(image: np.ndarray, out_h: int, out_w: int) -> np.ndarray:
    """Bilinear resize of (H, W, C) to (out_h, out_w, C), float32, matching F.interpolate(align_corners=False)."""
    src = image.astype(np.float32)
    src_h, src_w = src.shape[:2]
    ys = np.clip((np.arange(out_h, dtype=np.float32) + 0.5) * (src_h / out_h) - 0.5, 0.0, src_h - 1)
    xs = np.clip((np.arange(out_w, dtype=np.float32) + 0.5) * (src_w / out_w) - 0.5, 0.0, src_w - 1)
    y0 = np.floor(ys).astype(np.int64)
    x0 = np.floor(xs).astype(np.int64)
    y1 = np.minimum(y0 + 1, src_h - 1)
    x1 = np.minimum(x0 + 1, src_w - 1)
    wy = (ys - y0).astype(np.float32)[:, None, None]
    wx = (xs - x0).astype(np.float32)[None, :, None]
    top = src[y0][:, x0] * (1 - wx) + src[y0][:, x1] * wx
    bottom = src[y1][:, x0] * (1 - wx) + src[y1][:, x1] * wx
    return (top * (1 - wy) + bottom * wy).astype(np.float32)


def preprocess(image: np.ndarray, size: int) -> np.ndarray:
    """RGB uint8 (H, W, 3) to a normalised float32 tensor (1, 3, size, size)."""
    if image.ndim != 3 or image.shape[2] != 3:
        raise ValueError(f"expected an RGB image (H, W, 3), got shape {image.shape}")
    resized = resize_bilinear(image, size, size) * np.float32(PIXEL_SCALE)
    mean = np.asarray(IMAGENET_MEAN, dtype=np.float32)
    std = np.asarray(IMAGENET_STD, dtype=np.float32)
    normalised = (resized - mean) / std
    return np.ascontiguousarray(normalised.transpose(2, 0, 1)[None], dtype=np.float32)


def sigmoid(values: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-values))


def decode(boxes: np.ndarray, logits: np.ndarray, width: int, height: int) -> list[Point]:
    """Decode model outputs: boxes (1, Q, 4) normalised cxcywh, logits (1, Q, classes + 1).

    Each query becomes one point: the box centre in image pixels with its best class among `CLASS_NAMES` and that
    class's sigmoid score (the trailing no-object slot is ignored). Sorted by decreasing score.
    """
    if boxes.ndim != 3 or boxes.shape[0] != 1 or boxes.shape[2] != 4:
        raise ValueError(f"boxes must have shape (1, Q, 4), got {boxes.shape}")
    if logits.shape[:2] != boxes.shape[:2] or logits.shape[2] < NUM_CLASSES:
        raise ValueError(f"logits shape {logits.shape} does not match boxes {boxes.shape}")
    scores = sigmoid(logits[0, :, :NUM_CLASSES].astype(np.float64))
    best = scores.argmax(axis=1)
    points = [
        Point(
            class_name=CLASS_NAMES[int(best[q])],
            x=float(boxes[0, q, 0]) * width,
            y=float(boxes[0, q, 1]) * height,
            score=float(scores[q, best[q]]),
        )
        for q in range(boxes.shape[1])
    ]
    return sorted(points, key=lambda point: -point.score)


class LiteRtDetector:
    """Runs a `.tflite` produced by `kosmo_camera.export` on the CPU."""

    def __init__(self, path: Path) -> None:
        from ai_edge_litert.interpreter import Interpreter

        self._interpreter = Interpreter(model_path=str(path))
        self._interpreter.allocate_tensors()
        inputs = self._interpreter.get_input_details()
        if len(inputs) != 1:
            raise ValueError(f"expected one input, found {len(inputs)}")
        shape = [int(v) for v in inputs[0]["shape"]]
        if len(shape) != 4 or shape[0] != 1 or shape[1] != 3 or shape[2] != shape[3]:
            raise ValueError(f"expected an input (1, 3, S, S), found {shape}")
        self._input_index = inputs[0]["index"]
        self.input_size = shape[2]
        outputs = self._interpreter.get_output_details()
        self._boxes_index, self._logits_index = self._find_outputs(outputs)

    @staticmethod
    def _find_outputs(outputs: list[dict]) -> tuple[int, int]:
        boxes = [o for o in outputs if int(o["shape"][-1]) == 4]
        logits = [o for o in outputs if int(o["shape"][-1]) == NUM_CLASSES + 1]
        if len(outputs) != 2 or len(boxes) != 1 or len(logits) != 1:
            raise ValueError(f"expected boxes (1, Q, 4) and logits (1, Q, {NUM_CLASSES + 1}), found {outputs}")
        return boxes[0]["index"], logits[0]["index"]

    def run_raw(self, tensor: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        """Run on a preprocessed (1, 3, S, S) tensor; returns copies of boxes and logits."""
        self._interpreter.set_tensor(self._input_index, tensor)
        self._interpreter.invoke()
        boxes = self._interpreter.get_tensor(self._boxes_index).copy()
        logits = self._interpreter.get_tensor(self._logits_index).copy()
        return boxes, logits

    def detect(self, image: np.ndarray) -> list[Point]:
        boxes, logits = self.run_raw(preprocess(image, self.input_size))
        return decode(boxes, logits, image.shape[1], image.shape[0])
