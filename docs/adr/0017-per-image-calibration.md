# ADR 0017: Per-image calibration and automatic recalibration in the game app (camera phases 1 to 3)

- Status: Superseded by ADR 0020 on 2026-11-18 (its requirement on the game app is carried in ADR 0020 decision 6). Was: Proposed (Kosmo's requirement of 2026-11-15, D30 point 6). Date: 2026-11-15.

## Context

In phase 0b-1 the phone on its tripod moved during Kosmo's captures (D15). A calibration clicked once per session and
reused was wrong on part of the photos, and a drift measure against one reference photo per segment was not reliable
(D30). In real games the tripod is bumped, games are interrupted and resumed, and the player must not have to manage
the camera.

## Decision

In phases 1 to 3 the game app:

1. calibrates the board on every image it scores, never reusing a calibration from another image as truth;
2. detects a displacement of the board in the image (tripod bumped, phone moved, game interrupted then resumed) and
   recalibrates automatically, with no action from Kosmo; the game state (configuration and ordered darts) is kept
   across a recalibration;
3. when an image cannot be calibrated, its dart is not scored automatically: it is flagged "à vérifier" for manual
   entry, never guessed.

## Verification

Each phase that ships camera scoring is tested with deliberate tripod displacements: a small nudge, a rotation of the
phone on the tripod, a full repositioning of the tripod, and a game interrupted then resumed after moving the tripod.
For each, darts after the move are scored with the same criteria as before it.

## Phase 0b-1 report

The report of phase 0b-1 measures the automatic calibration of every eval photo against Kosmo's control clicks (CV9
per photo, CV25), not only one photo per volley.

## Alternatives rejected

- One calibration at the start of a game: it breaks at the first bump, the 0b-1 finding.
- Asking the player to recalibrate: Kosmo stands at the board, phone in one hand, so no camera handling during a game.
- A drift threshold with compensation from a reference image: not reliable in 0b-1 (D30).

## Consequences

- The detector must find the calibration points on every image (already a model class, CV2).
- The cost per image includes calibration; it is measured in 0b-2 on the Pixel 7.
- The test protocols of phases 1 to 3 include the displacement cases above.
