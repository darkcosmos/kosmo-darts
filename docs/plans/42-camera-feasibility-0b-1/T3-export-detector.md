# T3: LiteRT export feasibility and detector runtime

## Goal
Prove early, on CPU, that RF-DETR Nano with our 5 classes exports to `.tflite` with `litert-torch` 0.10.0, and provide
the detector that runs that file with `ai-edge-litert` and returns points per class.

## Files
- Added `tools/camera/kosmo_camera/model_spec.py`: `CLASS_NAMES = ("tip", "cal1", "cal2", "cal3", "cal4")`,
  `RESOLUTION` (Nano default read from `rfdetr` config, must be divisible by patch size times windows, see the
  `rfdetr` LiteRT export page), ImageNet mean and std.
- Added `tools/camera/kosmo_camera/export.py`: `export_litert(checkpoint | None, out_dir, resolution) -> ExportInfo`
  (path, SHA-256, size in bytes, the exact rebuild command line). `None` builds an untrained Nano (tests only).
- Added `tools/camera/kosmo_camera/detector.py`: `Detector` protocol `detect(image: np.ndarray) -> list[Point]`
  (`Point`: class name, x, y in image pixels, score); `LiteRtDetector(path)`: letterbox or resize as the export
  expects, NCHW float32 normalised, `ai_edge_litert.interpreter`, decode `boxes [1,300,4]` cxcywh and logits
  (sigmoid), keep the box centre; one input shape, checked at load.
- Added tests `tools/camera/tests/test_export.py` (slow), `test_detector_decode.py`.

## Depends on
T1.

## Rules
CV4 (measures use only the `.tflite`), root plan "Approach, Export" and "Measure on the exported file only".

## Tests
- Slow: untrained Nano exports; file exists, SHA-256 and size match the file; LiteRT loads it; input shape matches
  `RESOLUTION`; outputs 300 boxes and 5 class logits.
- Slow: on one generated image, LiteRT and eager PyTorch top-10 boxes agree within 1e-3 (bound of the `rfdetr` page).
- Decode (fast, fake output tensors): cxcywh to pixel centre with letterbox offsets, sigmoid, score order, an image
  not matching the aspect ratio.

## Done when
`.venv/bin/python -m pytest tools/camera -q` and `.venv/bin/python -m pytest tools/camera -m slow -q` green;
`kosmo-detekt` exit 0. If the export fails with `litert-torch` 0.10.0: stop, commit nothing broken, report the error
to main (issue rule: Kosmo decides).

## Model
sonnet. Why: library wiring with a specified output contract.

## Implementation notes
