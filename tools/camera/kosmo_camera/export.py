"""Export RF-DETR Nano (5 classes) to a float32 LiteRT `.tflite` on the CPU (rule CV4, root plan "Export").

Run `python -m kosmo_camera.export --checkpoint <file> --out-dir <dir>` to export a trained checkpoint. No model file is
ever committed (K0): the output goes to a folder the caller chooses.
"""

from __future__ import annotations

import argparse
import hashlib
import shlex
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

from kosmo_camera.model_spec import NUM_CLASSES, RESOLUTION, check_resolution

EXPORT_NAME = "rfdetr-nano.tflite"


@dataclass(frozen=True)
class ExportInfo:
    path: Path
    sha256: str
    size_bytes: int
    command: str


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def build_nano(checkpoint: Path | None, resolution: int = RESOLUTION):
    """Build RF-DETR Nano with our classes on the CPU; `None` gives untrained random weights (tests only)."""
    from rfdetr import RFDETRNano  # heavy import, kept out of module load

    check_resolution(resolution)
    weights = None if checkpoint is None else str(checkpoint)
    return RFDETRNano(num_classes=NUM_CLASSES, pretrain_weights=weights, resolution=resolution, device="cpu")


def rebuild_command(checkpoint: Path | None, out_dir: Path, resolution: int) -> str:
    parts = ["python", "-m", "kosmo_camera.export", "--out-dir", str(out_dir), "--resolution", str(resolution)]
    if checkpoint is not None:
        parts += ["--checkpoint", str(checkpoint)]
    return " ".join(shlex.quote(part) for part in parts)


def export_model(model, out_dir: Path, resolution: int, command: str) -> ExportInfo:
    """Export an already built model; `model.export` leaves the network in export mode (used by the parity test)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    written = Path(model.export(output_dir=str(out_dir), format="litert", shape=(resolution, resolution), verbose=False))
    path = out_dir / EXPORT_NAME
    if written != path:
        shutil.move(written, path)
    return ExportInfo(path=path, sha256=sha256_of(path), size_bytes=path.stat().st_size, command=command)


def export_litert(checkpoint: Path | None, out_dir: Path, resolution: int = RESOLUTION) -> ExportInfo:
    command = rebuild_command(checkpoint, out_dir, resolution)
    return export_model(build_nano(checkpoint, resolution), out_dir, resolution, command)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, default=None)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--resolution", type=int, default=RESOLUTION)
    args = parser.parse_args(argv)
    info = export_litert(args.checkpoint, args.out_dir, args.resolution)
    print(f"path: {info.path}\nsha256: {info.sha256}\nsize_bytes: {info.size_bytes}\ncommand: {info.command}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
