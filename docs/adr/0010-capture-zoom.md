# ADR 0010: Zoom control for Kosmo Capture

- Status: Proposed (design review of issue #41, zoom addendum); accepted when its PR is merged
- Date: 2026-11-01
- Rules: `docs/rules/capture.md` (CP14 to CP17); format: `docs/camera/capture-format.md` version 2
- Supersedes nothing; extends ADR 0009 (see its Consequences)

## Context

The Pixel 7 test showed that the board is too far away to be read well from the usual position of the phone. The
owner wants a zoom control on the capture screen, and wants the zoom logic to be reusable later in Kosmo Darts
(KZ6), for the camera milestone. The zoom is recorded per session in the truth file (KZ3), so the format changes.

## Decisions

### Zoom control: continuous slider, buttons for fine aim

A slider covers the camera's whole range (KZ1, CP14), locked outside a session (KZ2, CP15), and remembered in the
private settings like the quality (KZ4, CP16). "−" and "+" buttons, 0.1× per press, complete the slider for exact
values (KZ7, CP14). A button press is one complete gesture: the new ratio is applied and saved at once when it
changed, and no save happens when it is clamped at a range end. The step starts from the rounded current ratio, so
from 8.37 (shown `8.4×`) "−" gives 8.3. Each button is disabled at its range end (DZ11). Holding a button does not
repeat (DZ12): the slider covers large moves, the buttons only fine aim, and a repeat would need timing code in the
composable and a save policy per repeat. A repeat can be added later without a format change.

### Logarithmic mapping and rounding to 0.1

The slider position `p` maps to `min x (max / min)^p` (DZ1): the same hand movement gives the same relative zoom
change at 1.2× and at 6×. A linear mapping was rejected: it crowds the useful 1× to 3× into the first fifth of the
bar. The ratio is rounded to the nearest 0.1 and then clamped into the camera range (DZ2), so the shown, applied,
remembered and recorded values are the same number. The rounding is done on the shortest decimal form of the float
(`Float.toString()` into `BigDecimal`, half up), not on its binary value: 1.05 gives 1.1 and 2.35 gives 2.4, as a
person reading the number expects (DZ10). `java.math` is allowed in the portable package, on the JVM and on Android
alike.

### Portable zoom package, isolated from capture

The zoom logic lives in the package `io.github.darkcosmos.kosmodarts.camera.zoom`, inside `:capture`, in pure
Kotlin (DZ3). A JVM test enforces the isolation: the package imports only `kotlin.*` and `java.math.*`, nothing from
Android, CameraX, Compose, coroutines or other `:capture` packages (`ZoomPackageIsolationTest`). The package name has
no `capture` segment, so that moving the folder is a file move without edits. The lock rule (CP15), the settings
file, the CameraX calls, the truth record (CP17) and the composable stay capture specific.

Alternatives, for the placement of the package (DZ3):

- (a) A new pure Kotlin module `:camera-zoom` now: the cleanest boundary, but a new module and Gradle wiring for four
  small files, and a structural choice the owner has not asked for. Proposed to the owner for the camera milestone.
  This is the choice taken: the package is created in `:capture` and the module decision waits.
- (b) Inside `capture.camera`, with capture types: rejected, not reusable.
- (c) In `:game-engine`: rejected, zoom is not a game rule.

### CameraX `setZoomRatio` and `ZoomState`, reapplied after binding

The range comes from `ZoomState` (`minZoomRatio`, `maxZoomRatio`) and the zoom is applied with
`CameraControl.setZoomRatio` (DZ4). A rebinding resets CameraX to its default zoom, so the stored ratio, kept inside
the camera range, is re-applied after every binding (launch, quality change, return to the app, rotation), also
during a session (CP16). Rejected: `CameraControl.setLinearZoom` (the mapping is owned by CameraX, there is no 0.1
rounding, and the truth needs a ratio) and pinch (less reliable, according to the owner). A zoom failure never ends
a session.

### Memory: saved on release and per button press

The slider is saved once, when it is released, not on every move; a button press is saved when it changed the ratio
(DZ7, CP16). A failed write is silent: the zoom still applies until the app is closed. A missing, unreadable,
non-finite, zero or negative stored value reads as 1.0.

### Truth file version 2: requested and reported ratio

The truth file version 2 records once per session, at Start, a `zoom` object with `requestedRatio` (the ratio the app
last asked the camera for) and `ratio` (the ratio the camera reported at Start), both nullable (DZ5, CP17). They are
honest when they differ or are unknown. There is no upper bound on the values (S9): the phase 0b tools must not
assume a plausible range.

Version 1 files are rejected by the reference decoder and not migrated (DZ6). The app never reads a truth file back
(the decoder is used by the tests only), version 1 files were all made at zoom 1.0, and the format document says so
for the phase 0b tools. A migration would be dead code.

### Placement in the screen

The zoom row sits between the preview and the control row, always visible and greyed during a session, so that the
locked value stays readable (DZ8). The 8dp gap between the zoom row and the control row is specified in the plan
(DZ13), and so are the sizes of the value text and of the buttons (DZ14, DZ15). Disabled buttons lose their fill as
well as their opacity (DZ16). The slider is a polite live region, so that TalkBack announces the new value after a
button press (DZ17).

### Separate document, not an addendum

This zoom is a format change and a reuse intent, so it gets its own ADR rather than an addendum to ADR 0009 (DZ9).

## Consequences

- The truth format is a contract with the phase 0b tools: format version 2 is a contract change, documented in
  `docs/camera/capture-format.md`.
- The zoom package is portable, but it stays in `:capture` for now. Proposed owner decision: move `camera/zoom/` into
  a shared pure Kotlin module when Kosmo Darts gets camera features. The move of the package happens in a single PR,
  together with the shared module decision.
- Pixel 7 zoom is digital, not optical.
- ADR 0009 is extended by one line: "Zoom: see ADR 0010."
