"""Fixed contract of the detector model: classes, input size, normalisation (rule CV4, root plan "Export").

The class order is the logit order of the exported file: slot k is the class `CLASS_NAMES[k]`, so the training
dataset must use 0-based category ids in this order. The head emits one extra trailing slot (no-object), which the
detector ignores.
"""

from __future__ import annotations

from rfdetr.config import RFDETRNanoConfig

CLASS_NAMES: tuple[str, ...] = ("tip", "cal1", "cal2", "cal3", "cal4")
NUM_CLASSES = len(CLASS_NAMES)

_NANO = RFDETRNanoConfig()
PATCH_SIZE: int = _NANO.patch_size
NUM_WINDOWS: int = _NANO.num_windows
# Square input side in pixels; both sides must be divisible by patch size times windows.
RESOLUTION: int = _NANO.resolution

# Same normalisation as rfdetr (RGB, pixel / 255, then ImageNet mean and std).
IMAGENET_MEAN: tuple[float, float, float] = (0.485, 0.456, 0.406)
IMAGENET_STD: tuple[float, float, float] = (0.229, 0.224, 0.225)
PIXEL_SCALE = 1.0 / 255.0


def check_resolution(resolution: int) -> int:
    """Return `resolution` if the Nano backbone accepts it, else raise ValueError."""
    step = PATCH_SIZE * NUM_WINDOWS
    if resolution <= 0 or resolution % step != 0:
        raise ValueError(f"resolution {resolution} must be a positive multiple of {step}")
    return resolution
