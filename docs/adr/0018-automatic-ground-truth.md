# ADR 0018: Automatic ground truth and a 10-minute manual budget for the camera work (phase 0b-1 and later)

- Status: Accepted (Kosmo's firm constraint of 2026-11-18, D48). Date: 2026-11-18.

## Context

In phase 0b-1 Kosmo clicked the 4 calibration points of every control photo (step K7.3, rules CV25 and CV26). The
plan estimated 20 to 30 minutes; the real cost was about one hour for an eval session of 104 photos, with 4 clicks
per photo, because the snapping of the clicks (CV31) almost never worked on the real photos although it passed
98 to 100 % of its synthetic cases (D49). The estimate came from an average case and the algorithm was declared
ready on synthetic targets only.

## Decision

1. Every future ground truth of the camera work (calibration, drift, calibration of the measuring instrument, new
   capture campaign) is produced automatically. The human only validates the exceptions that the tool flags.
2. Kosmo's manual-entry budget is at most 10 minutes per campaign. A plan that exceeds it stops before any code and
   proposes an automated alternative.
3. The K7 control clicks of phase 0b-1 are a one-time investment: they validate the automatic measurement, which then
   replaces any calibration click. They are never redone.
4. Every time estimate given to Kosmo is computed from real data (measured durations and real counts), never from an
   average case.
5. An algorithm that must run on Kosmo's images is never declared ready on synthetic targets alone: it is measured on
   the real data, numbers only, before anything is asked of Kosmo.

## Phase 0b-1

Superseded by ADR 0020 on 2026-11-18 (snapping stopped).

- The snapping is diagnosed and fixed on Kosmo's stored clicks, with no new click (D49): offline diagnosis, fix,
  acceptance on the real data (at least 90 % of the photo control points snapped from Kosmo's clicks, no point on
  another crossing), then automatic snapping of every stored click into the pixel-level truth.
- Each step asks Kosmo only to run one command and paste a report of numbers; the plan counts his time per step.

## Re-snap file

Superseded by ADR 0020 on 2026-11-18 (no re-snap file).

- The pixel-level truth is `snapped-points.json`, a derived and regenerable local file (format
  `"kosmo-camera-snapped"`, version 1, strict reader, 4 MiB cap). It is never pasted and never leaves the PC.
- It carries the SHA-256 of the segments and controls of each annotation file it was computed from; a reader
  refuses it when they differ from the current annotation files (stale truth).
- It is written only when the acceptance on the real data passed; a refused run leaves no such file. The annotation
  files keep Kosmo's clicks and are never rewritten.
- Per point it holds the snapped point or, when the snap failed, the click with its reason; a failed restart is the
  string `"failed"` (JSON has no infinity).

## Alternatives rejected

- Clicking again with a better tool: the clicks are already there; a new manual pass breaks the budget.
- Accepting an algorithm on synthetic results: the 0b-1 snapping shows that synthetic targets did not represent the
  real ones.

## Consequences

- Plans list Kosmo's time per step, computed from real figures, with the 10-minute total.
- Tools that produce a ground truth report the share of automatic results and the list of flagged exceptions.
- A plan step that would ask Kosmo for clicks (for example clicking every dev photo) is replaced by an automatic
  measure with flagged exceptions.
