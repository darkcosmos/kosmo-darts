# Z1: Rules CP14 to CP17 and ADR 0010

## Goal
Write the zoom rules and the ADR so that later tasks cite them.

## Files
- Changed: `docs/rules/capture.md`: append CP14 to CP17 copied verbatim from the root plan section "Rules"; status
  line: add "v2: zoom (CP14 to CP17), `docs/plans/41-capture-app-zoom.md`".
- Added: `docs/adr/0010-capture-zoom.md`, same layout as ADR 0009 (Status Proposed, Date, Rules CP14 to CP17, format
  `docs/camera/capture-format.md` version 2), sections:
  - Context: Pixel 7 test, board too far; Kosmo wants the zoom reusable later in Kosmo Darts.
  - Decisions: the portable package and its isolation (DZ3, with alternatives (a) to (c) of root "Approach");
    CameraX `setZoomRatio` and `ZoomState`, re-applied after binding (DZ4, `setLinearZoom` and pinch rejected);
    logarithmic mapping and 0.1 rounding on the shortest decimal form (DZ1, DZ2, DZ10); "−" / "+" buttons, 0.1×
    per press, saved per press, no long-press repeat (KZ7, DZ11, DZ12); truth format version 2 with
    `requestedRatio` and `ratio` (DZ5, no upper bound, S9); version 1 rejected, not migrated (DZ6).
  - Consequences: format version 2 is a contract change for the phase 0b tools; "Proposed owner decision: move
    `camera/zoom/` into a shared pure Kotlin module when Kosmo Darts gets camera features", followed by this
    sentence (A2): "The move of the package happens in a single PR, together with the shared module decision.";
    Pixel 7 zoom is digital.
- Changed: `docs/adr/0009-capture-app.md`: one line under Consequences "Zoom: see ADR 0010."

## Depends on
None.

## Rules
CP14, CP15, CP16, CP17 (created here). Root plan "Decisions" DZ1 to DZ6, DZ10 to DZ12, KZ7, A2.

## Tests
None (documentation). Checks: `grep -c '^- CP1[4-7] ' docs/rules/capture.md` prints 4;
`grep -c 'single PR' docs/adr/0010-capture-zoom.md` prints at least 1.

## Done when
Files committed (`docs: add the capture zoom rules and ADR 0010 (#41)`); `./gradlew :capture:testDebugUnitTest`
still green (no code change); `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan, no design choice.

## Implementation notes
