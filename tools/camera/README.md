# Camera tooling (phase 0b)

Python 3.13 tools that measure whether dart detection from photos is feasible (issue #42): dev/eval split, annotation
tool, detector measures and reports. Install the pinned environment with the command at the top of
`requirements-cpu.txt`, run the tests with `.venv/bin/python -m pytest` from this folder, and read
`docs/camera/training.md` for the data layout and the protocol.

The annotation tool (rule CV16) enters the dart tips and shows the calibration segments, the controls and the video
frames read-only. It is stdlib only: run it with `python3` from this folder, then open the address it prints:

```bash
python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations
```
