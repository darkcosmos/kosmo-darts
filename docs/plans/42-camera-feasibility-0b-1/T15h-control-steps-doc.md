# T15h: training.md, control clicking steps (K7.0 to K7.3 with K7.1b, checklist K7.C), K5 retired

## Goal
Give Kosmo the copy-paste steps to rebuild the image, create the WSL tool venv once, extract the video frames and
do the assisted control clicks, and retire the K5 drift loop from the guide (D30, D31, D39, D43).

## Files
- Changed `docs/camera/training.md`: the section "Dérive et recalibration" is replaced by a section "Contrôle de
  calibration" holding K7.0, K7.1, K7.1b (venv creation: `python3 -m venv ~/kosmo-tool-venv`, the
  `python3-venv` hint, `~/kosmo-tool-venv/bin/pip install --require-hashes -r tools/camera/requirements-tool.txt`,
  the `--help` check « Recalage : disponible »), K7.2, the new K7.3 (tool started with
  `~/kosmo-tool-venv/bin/python`, assisted click, proposals, Entrée, "non recalé", about 20 to 30 min) and the
  checklist K7.C, in French, copied from root plan "How Kosmo verifies" (commands as written there; the `docker
  run` line keeps `--network none`, `--cap-drop ALL`, `no-new-privileges`, `--read-only` and `~/kosmo-data`
  mounted `:ro`). One sentence says the former K5 loop is retired and why (D30). Until T15l, a line "K7.4 à K7.6 :
  le diagnostic arrive dans la prochaine livraison" so the tool's final instruction (K7.4) points to an existing
  place.
- Changed `tools/camera/tests/test_training_doc.py`: the same checks moved to the new section (no check dropped):
  the `video_frames` command parses with its parser, the annotate command of K7.3 (with `--split`, `--frames`,
  started by `~/kosmo-tool-venv/bin/python`) parses with its parser, the `docker run` block keeps the flags and
  read-only data mount, the steps K7.0, K7.1, K7.1b, K7.2, K7.3, K7.C and the expected texts (« Recalage :
  disponible », « Recalage indisponible », « non recalé », « Points prêts ») are named. New check for K7.1b: the
  pip command, split with `shlex`, is exactly `~/kosmo-tool-venv/bin/pip install --require-hashes -r
  tools/camera/requirements-tool.txt`, the named file exists in the repository and every line of it is pinned with
  a hash (reusing `test_requirements._parse`); the venv command is `python3 -m venv ~/kosmo-tool-venv`.

## Depends on
T15c, T15g, T15n (`requirements-tool.txt`, the `--help` « Recalage » line).

## Rules
S10 (container flags), CV15 (the guide says which files are local and never pasted: `frames.json`, the frames
folder), CV31 (assisted click), D10 (hashed requirements).

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan and the `--help` of two commands; doc test moved, the K7.1b
check fully specified above, not designed.

## Implementation notes
