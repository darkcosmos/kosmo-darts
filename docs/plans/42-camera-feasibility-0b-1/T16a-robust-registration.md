# T16a (CONDITIONAL): Robust registration of a photo against its reference

Status: conditional. Built only if the T15o verdict says H1 (alone or with H2) and Kosmo confirms at plan update
P2. Its content is finalized at P2 from the diagnostic numbers; this file fixes the frame.

## Goal
Replace the CV18 measure by one that does not lock onto a neighbouring wire and ignores darts, validated on the
re-snapped control points of `snapped-points.json` (T17c; error at most `VALIDATION_MAX_MM` on at least
`VALIDATION_MIN_SHARE` of the sample; controls with a non-snapped point apart, as T15i) before any compensation
(D30 point 4, CV30, D49). Measured on the real data, numbers only, before anything is asked of Kosmo (D48).

## Files
- Changed `tools/camera/kosmo_camera/drift.py` (or a new `registration.py`): ambiguous-peak rejection (second to
  first peak ratio above `PEAK_RATIO_MAX`: point refused), masking of known darts (disc around every clicked tip of
  the volley so far, `DART_MASK_RADIUS_MM`), static zones only (outside the scoring area, number ring and board
  edge first), plausible shift bound (`MAX_SHIFT_MM` from the diagnosis), refusal with a reason instead of a
  wrong value.
- Changed `diagnose.py`: section "Validation" (CV30 gate, dev then eval confirmation, per role).
- Changed `defaults.json`, `config.py`, `test_config.py`; `docs/rules/camera.md` (CV18 replaced or amended).
- Changed tests `test_drift.py` (or `test_registration.py`), `test_diagnose.py`.

## Depends on
T15o, T17c; plan update P2.

## Rules
CV18 (amended at P2), CV30, CV4 (parameters chosen on dev controls only; eval controls confirm, never tune).

## Tests
- Periodic trap of T15i: refused or correct, never wrong by one wire.
- Darts masked: a synthetic volley with 3 darts drawn on the patches: error unchanged within 0.05 mm.
- Bound: a shift beyond `MAX_SHIFT_MM` refused with its reason.
- Gate: passes and fails at the boundary values on scripted errors; eval never read by the parameter choice
  (the tuning function receives dev controls only, checked by a test).

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, algorithm with an open design: the robust measure is chosen from the diagnosis, no rule
specifies it.

## Implementation notes
