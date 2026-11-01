# Plan addendum: zoom in Kosmo Capture (issue #41, PR #63)

## Goal

[Issue #41](https://github.com/darkcosmos/kosmo-darts/issues/41), follow-up of the phone test on the Pixel 7: the
images are too far from the board and the phone cannot move closer. Kosmo Capture gets a zoom slider with "−" and "+"
buttons (0.1× per press), set outside a session, remembered between launches and recorded in `truth.json`. Delivered in PR #63, on top of the approved root
plan `docs/plans/41-capture-app.md` (its rules CP1 to CP13 and decisions stay in force).

## Scope and out of scope

- In scope: a continuous zoom slider and "−" / "+" fine adjustment buttons on the capture screen (CP14), locked
  during a session (CP15), remembered in the
  private settings (CP16), recorded per session in the truth file version 2 (CP17); the zoom logic in a portable,
  self-contained package for later reuse in Kosmo Darts; ADR 0010; format document version 2.
- Out of scope: repeat on long press of "−" / "+" (DZ12), pinch to zoom, tap to focus, exposure, lens choice (Pixel 7 has no telephoto lens: CameraX zoom on it
  is a crop of the main camera); any change to `:app`, `:game-engine` or `.github/`; a shared module (see Decisions
  DZ3: proposed to Kosmo for later, not created); migration of version 1 files (DZ6).

## Rules (appended to `docs/rules/capture.md` by Z1, verbatim)

- CP14 Zoom control: outside a session, a slider sets the zoom ratio of the camera (preview, videos and photos alike)
  between the minimum and maximum ratio the camera reports. The slider position `p` (0 to 1) maps to the ratio on a
  logarithmic scale, `min x (max / min)^p`; the ratio is rounded to the nearest 0.1 (half up, applied to the shortest
  decimal form of the value, so 1.05 gives 1.1), then kept inside the camera range. Next to the slider, a "−" and a
  "+" button lower or raise the current ratio by 0.1 per press: the current ratio is rounded to the nearest 0.1 as
  above, 0.1 is subtracted or added, and the result is kept inside the camera range. Holding a button does not
  repeat. The preview follows while the slider moves and after each press. The current ratio is shown next to the
  slider with one decimal and the sign `×` (`2.4×`; `2,4×` in French).
- CP15 Zoom lock: the slider and both buttons are enabled only when no session runs or saves (like the quality
  setting, CP2), the camera permission is granted, the camera is bound and its range is at least 0.1 wide; in
  addition, "−" is disabled when the ratio is at the camera minimum and "+" when it is at the camera maximum.
  Otherwise they are disabled and the ratio stays shown; when the bound camera's range is narrower than 0.1, "Zoom
  not available" replaces the ratio. A zoom change or press received while disabled is ignored.
- CP16 Zoom memory: the ratio chosen is stored in the private settings when the slider is released (not on every
  move) or after each button press that changed it, and read at launch; default 1.0; a missing, unreadable, non-finite, zero or negative stored value reads as
  1.0. After every camera binding (launch, quality change, return to the app, rotation) the stored ratio, kept inside
  the camera range, is applied to the camera, also during a session; keeping it inside the range never changes the
  stored value. If the setting cannot be written, the ratio still applies until the app is closed, without a message.
- CP17 Zoom record: the truth file (version 2) records once per session, at Start, the ratio the app last asked the
  camera for (`zoom.requestedRatio`, null when it asked none) and the ratio the camera reported at Start
  (`zoom.ratio`, null when it reported none).

## Approach

### Portable zoom package (owner reuse intent)

The zoom logic lives in a new package `io.github.darkcosmos.kosmodarts.camera.zoom`, in the `:capture` source set
(`capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/`). It is pure Kotlin: it imports only
`kotlin.*` (plus `java.math.BigDecimal`), nothing from Android, CameraX, Compose, coroutines or any other `:capture`
package. A JVM test enforces this
(`ZoomPackageIsolationTest`, Z2). The package name has no `capture` segment, so moving the folder into a future shared
camera module (or into `:app`) is a file move without edits. Contents (Z2):

- `ZoomRange(min, max)`: validated (finite, `0 < min <= max`; a factory returns null otherwise), `adjustable`
  (`max - min >= ZOOM_STEP`), `clamp(ratio)`, `ratioAt(position)` (CP14 mapping, NaN or out of 0..1 positions
  clamped), `positionOf(ratio)` (inverse, 0 when not adjustable).
- `quantizeZoom(ratio)`: nearest 0.1, half up, computed on the shortest decimal form of the float (DZ10):
  `BigDecimal(ratio.toString()).setScale(1, RoundingMode.HALF_UP).toFloat()`, so 1.05f gives 1.1 and 2.35f gives
  2.4 although their binary values are slightly below. Constants `ZOOM_STEP = 0.1f`, `DEFAULT_ZOOM_RATIO = 1f`.
- `ZoomSelection(remembered, range)`: immutable state of a zoom control: `ratio` (remembered, quantized, kept inside
  the range when known), `position`, `adjustable`, `unavailable` (range known and not adjustable), `withRange(range)`,
  `movedTo(position)` (new remembered ratio, unchanged when not adjustable), `steppedBy(steps: Int)` (CP14 buttons:
  `quantizeZoom(ratio)` plus `steps` x 0.1 in `BigDecimal`, then kept inside the range; unchanged when not
  adjustable), `canStepDown` / `canStepUp` (adjustable and `ratio` above the range minimum / below its maximum).
- `ZoomSettingFormat`: `parse(text: String?)` (CP16 fallbacks to 1.0) and `format(ratio)`, so any settings store
  (the capture `key=value` file now, a Kosmo Darts store later) reads and writes the value the same way.
- Allowed imports in the package: `kotlin.*` and `java.math.*` (for `BigDecimal`, JVM and Android alike) only.

What stays capture specific, outside the package: the lock rule (CP15, it depends on the session phase), the settings
file (`CaptureSettings`), the CameraX calls, the truth record (CP17) and the composable.

Alternatives: (a) a new pure Kotlin module `:camera-zoom` now: cleanest boundary, but a new module and Gradle wiring
for four small files, and a structural choice the owner has not asked for (DZ3, proposed for the camera milestone);
(b) inside `capture.camera` with capture types: rejected, not reusable; (c) in `:game-engine`: rejected, zoom is not a
game rule.

### Camera (CameraX, no new dependency)

`CameraXCaptureCamera` keeps the bound `Camera`. After each binding it reads `camera.cameraInfo.zoomState.value`
(`ZoomState.minZoomRatio`, `maxZoomRatio`) into a `ZoomRange` (null when no value); `zoomRatio` reads
`ZoomState.zoomRatio` live; `setZoomRatio(ratio)` calls `camera.cameraControl.setZoomRatio(ratio)` with an already
clamped ratio and ignores the returned future's failure (`OperationCanceledException` when a newer call replaces it).
A rebinding resets CameraX to its default zoom, hence CP16 re-applies after every binding. Rejected:
`CameraControl.setLinearZoom` (mapping owned by CameraX, no 0.1 rounding, and the truth needs a ratio); pinch (Kosmo:
less reliable). Zoom failures never end a session.

### Data flow

Activity bind callback -> `viewModel.onZoomRange(camera.zoomRange)` -> the view model updates its `ZoomSelection` and,
when adjustable, sends the effect `setZoomRatio(selection.ratio)` (CP16). Slider move -> `onZoomChanged(position)`
(refused unless `zoomEnabled`, CP15) -> new selection -> `setZoomRatio`. Release -> `onZoomChangeFinished()` -> saved
once if changed (CP16). "−" / "+" press -> `onZoomStep(-1 | +1)` (refused unless `zoomEnabled`, CP15) ->
`steppedBy` -> when the ratio changed, `setZoomRatio` and save at once (CP16). Start -> `CaptureSetup.zoom = ZoomRecord(requestedRatio = last ratio sent, ratio =
camera.zoomRatio)` -> truth (CP17).

## Files and types added or changed

- Added: `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/` (`ZoomRange.kt`, `ZoomSelection.kt`,
  `ZoomSettingFormat.kt`) and tests under `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/` (Z2).
- Changed, `capture.camera`: `CaptureCamera` (`zoomRange`, `zoomRatio`, `setZoomRatio`), `CameraXCaptureCamera`,
  `CaptureController` (Z3, Z4); `capture.ui`: `CaptureEffects.setZoomRatio` (Z3), `CaptureUiState`,
  `CaptureViewModel` (Z5), `CaptureActions`, `CaptureScreen`, new `ZoomRow.kt`, `CaptureTags` (Z6);
  `capture.storage.CaptureSettings` (Z5); `capture.truth`: `TruthSession.zoom: ZoomRecord`, codec, `VERSION = 2`
  (Z4); `capture.session.CaptureSetup.zoom` (Z4); `CaptureActivity` (Z5); `res/values{,-fr}/strings.xml` (Z6).
- Docs: `docs/rules/capture.md` (CP14 to CP17), new `docs/adr/0010-capture-zoom.md` (Z1);
  `docs/camera/capture-format.md` version 2 (Z4).
- Not changed: `app/`, `game-engine/`, `.github/`, `gradle/libs.versions.toml` (no new dependency).

## Data read, stored or exposed

- Private settings file `capture-settings.txt` (`filesDir`, backup disabled): new line `zoom=<ratio>`. Old files
  without it read as 1.0 (CP16); quality and total are kept by every write. Existing behavior kept: unknown keys
  are ignored on read and not written back (each write rewrites the known keys only).
- `truth.json` version 2: new root object `zoom` (two floats or null). Nothing else new; no image metadata added.
- Read from the camera: `ZoomState` (min, max, current ratio). No new permission.

## Truth file version 2 (finalized by Z4 in `docs/camera/capture-format.md`)

Version 2 = version 1 plus one root object after `photo`, always present:

```json
  "zoom": {
    "requestedRatio": 2.4,
    "ratio": 2.4
  },
```

`requestedRatio`, `ratio`: float or null, finite and greater than 0 when not null (CP17). Floats written like
`gravity` (`Float.toString()`). No upper bound is enforced (S9): the document states that the values are whatever
the camera reported or the app asked, and that phase 0b tools must not assume a plausible range (they must handle,
for example, a ratio above 1000 themselves). The writer writes version 2 only; the reference decoder accepts version 2 only and
rejects version 1 with "unsupported version 1" (DZ6). The document keeps the version 1 description as a section
"Version 1" (identical minus `zoom`) and states that every version 1 file was recorded without zoom control, so at
the CameraX default ratio 1.0: a phase 0b tool may read a version 1 file as `zoom` 1.0.

## Screens and states

Left column of the landscape screen, top to bottom: status line, preview (weight 1), **zoom row (new)**, control row.

- Zoom row: full column width, exactly 42dp tall at every font scale (`CONTROL_HEIGHT`, landscape exception Q14),
  4dp gap above (like the other rows) and 8dp below, towards the control row (DZ13). Left to right, 4dp apart:
  - value text (`2.4×` or "Zoom not available"): width between 64dp and 140dp, `WordSafeText` (the existing
    `capture.ui.keypad` helper) with `KosmoTheme.typography.button`, `onBackground`, at most 2 lines, shrinking
    instead of breaking a word or overflowing (DZ14);
  - "−" button, 42dp x 42dp;
  - Material 3 `Slider`, weight 1, continuous (no `steps`, no tick marks), 42dp tall: the minimum interactive size
    is set to 42dp inside the row (`LocalMinimumInteractiveComponentSize`), so the touch target is 42dp x slider width;
  - "+" button, 42dp x 42dp.
  Buttons: the style of the unselected quality button of the control row, glyph `−` (U+2212) or `+` in
  `typography.button`; one press = one step (no repeat on long press, DZ12).
- Width budget (left column = 55 % of the width minus padding: about 490dp at 915dp, about 339dp at 640dp): slider
  = column - value text - 2 x 42 - 3 x 4. Normal state (value at 64dp): about 330dp at 915, about 179dp at 640.
  Worst case "Zoom not available" with the value at its 140dp maximum: about 254dp at 915 (also at font scale 2.0,
  where the text shrinks inside 140dp) and about 103dp at 640. Accepted minimums, tested in Z6 in the "not
  available" state EN and FR: slider at least 120dp at 915x412 font scale 2.0, at least 96dp at 640x360 font scale 1.0.
- Colors: thumb and active track `accent`, inactive track `surfaceHigher`; disabled: the design system disabled
  style (`States.DISABLED_ALPHA` on the slider, buttons and text), never color alone; the ratio stays readable when
  greyed. Accent on background meets 3:1 (non-text component) and `onBackground` text 4.5:1, as already checked for
  the design system tokens.
- Accessibility: the slider has `contentDescription` "Zoom" and `stateDescription` "2.4 times" (or "Zoom not
  available"); the value text is cleared from semantics so TalkBack reads the zoom once. TalkBack swipe up/down uses
  the slider's own adjust actions; the new value is read through `stateDescription`. The buttons have
  `contentDescription` "Zoom out" / "Zoom in" and the same `stateDescription` as the slider, so the new value is
  read on the focused button after a press. Disabled is announced by the framework.
- States: permission missing or camera starting: disabled, shows the remembered ratio (`1.0×` on first launch);
  idle and error phase with camera bound: enabled ("−" disabled at the camera minimum, "+" at its maximum); session
  running or saving: all disabled, shows the session ratio; camera range narrower than 0.1: all disabled, "Zoom not
  available"; dialogs open: unchanged underneath.
- Height budget at 412dp (about 364dp usable): left column = status about 24 + preview + zoom 42 + control 42 +
  gaps 4 + 4 + 8 = 16, so the preview gets about 240dp (was about 290; the 16:9 image stays centered,
  `FIT_CENTER`). At 640x360 (about 312dp usable): preview about 188dp, accepted minimum 160dp (tested, Z6). Right
  column unchanged (348dp); the action row is in the right column and is re-checked by `CaptureActionRowTest`. At
  font scale 2.0 (915x412) the zoom row stays 42dp (the text shrinks) and the preview stays at least 96dp tall
  (tested, Z6).

| Strings | English | French |
|---|---|---|
| `zoom_description` | "Zoom" | « Zoom » |
| `zoom_value` (visible) | "%1$.1f×" | « %1$.1f× » (locale decimal: `2,4×`) |
| `zoom_state` (spoken) | "%1$.1f times" | « %1$.1f fois » |
| `zoom_unavailable` | "Zoom not available" | « Zoom non disponible » |
| `zoom_out_description` | "Zoom out" | « Réduire le zoom » |
| `zoom_in_description` | "Zoom in" | « Augmenter le zoom » |
| `zoom_out_symbol` (`translatable="false"`, values only) | "−" (U+2212) | - |
| `zoom_in_symbol` (`translatable="false"`, values only) | "+" | - |

## Test plan

Every test name or comment cites its CP id. Task files list the cases; summary:

- Z2 (JVM): CP14 mapping, rounding (shortest decimal form: 1.05, 2.35, 2.45), clamping, inverse, NaN and edge
  ranges, button steps at and near the range ends; CP16 parse fallbacks; `ZoomSelection`; package isolation (pinned
  match rules).
- Z3 (JVM, fake camera): controller forwards `setZoomRatio`; a throwing camera reports no error (zoom never ends a
  session).
- Z4 (JVM): CP17 golden text version 2, round trips (zoom values, both null), rejections (version 1, missing `zoom`,
  unknown field in `zoom`, 0, negative, string, missing member), setup carries the camera ratio.
- Z5 (JVM): CP15 `zoomEnabled` per state; CP16 settings file and view model (apply after binding, clamp without
  saving, save on release only, save per effective button press, failure silent); CP17 requested ratio at Start,
  including the sequence adjustable range -> null range.
- Z6 (Robolectric + Roborazzi): value text EN/FR, state descriptions, button content descriptions, enabled per state
  and at the range ends, slider gesture and button clicks forward events, 42dp row, slider width minimums, preview
  height at 915x412 font scale 2.0 and at 640x360, control row inside the screen, `CaptureActionRowTest` re-run;
  all existing captures regenerated, new "zoom not available" capture EN and FR.
- Not testable here: the real CameraX zoom on a device (no device or emulator in the sandbox); verified by Kosmo
  only (steps below) and listed in the PR "Known limitations".

## How Kosmo verifies on the phone (Pixel 7)

Uninstall Kosmo Capture, install the `capture-debug.apk` of the latest commit, then:
1. Allow the camera. Under the preview, a zoom row shows `1.0×`, a "−" button, the bar and a "+" button; at `1.0×`
   (the phone's minimum) the "−" button is greyed.
2. Drag the bar to the right: the preview zooms in live, the value goes up by tenths up to the phone's maximum.
   Release at about `2.5×`. Drag back to the left end: `1.0×` (or the phone's minimum).
3. Press "+" several times: each press adds exactly `0.1×` and the preview follows; press "−": `0.1×` less.
   Reach exactly `2.5×` with the buttons. Holding a button changes the value once only. Drag the bar to the right
   end, then press "+": nothing changes and "+" is greyed.
4. Set `2.5×` with the buttons, switch 1080p then 4K: the zoom stays `2.5×` and the preview stays zoomed.
5. Close the app (remove it from recent apps) and reopen it: `2.5×` again (also when the last change was a button
   press).
6. Start a session: the bar and both buttons are greyed and do not react, and the value `2.5×` stays readable on
   the greyed row; enter a volley; Stop and confirm: after "Saving..." the bar and the buttons work again.
7. Copy the session to the PC: `truth.json` shows `"version": 2` and `"zoom": { "requestedRatio": 2.5, "ratio": 2.5 }`
   (the second value may differ slightly); photos and videos are framed like the zoomed preview.
8. Phone in French: the value reads `2,5×`. With TalkBack: the bar is read "Zoom, 2,5 fois"; swiping up or down
   changes the value; the buttons are read « Réduire le zoom » and « Augmenter le zoom », and after a double tap
   the new value is read.
9. Display size and font size at their maximum in the phone settings: the zoom row stays on one line, the bar keeps
   a usable length and nothing is cut.
10. Revoke the camera permission in the settings and come back: the bar and buttons are greyed and still show the
   remembered value.
11. Kosmo Darts is unchanged.

## PR description material (for developer-fix)

- For the product owner: zoom slider with "−" / "+" buttons (0.1× per press, no repeat when held), locked during a
  session, remembered, recorded; the Pixel 7 zoom is digital (a crop of the main camera): the more zoom, the less
  detail per dart; prefer the smallest zoom that frames the board.
- Known limitations: digital zoom only; the real CameraX zoom behavior (preview, videos and photos zoomed, value
  applied after a rebinding) is verified manually on the phone only, the automated tests use a fake camera; version
  1 truth files are no longer read by the reference reader (doc keeps their description, zoom 1.0); holding "−" /
  "+" does not repeat.
- No Versions section (no dependency change). Local checks: Gradle commands of Z6 "Done when" and `kosmo-detekt`.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| Z1 | Rules CP14 to CP17 and ADR 0010 | `41-capture-app-zoom/Z1-rules-adr.md` | - | haiku | Documentation copied from this plan, no design choice |
| Z2 | Portable zoom package (slider mapping, button steps) and its tests | `41-capture-app-zoom/Z2-zoom-package.md` | Z1 | sonnet | Pure logic fully specified (CP14, CP16) with listed tests |
| Z3 | Camera zoom port, CameraX and controller | `41-capture-app-zoom/Z3-camera-zoom.md` | Z2 | sonnet | Wiring of two CameraX calls behind a tested interface |
| Z4 | Truth format version 2 with the zoom record | `41-capture-app-zoom/Z4-truth-v2.md` | Z3 | opus | New stored format version (case 1): schema, strict decoder, version 1 rejected |
| Z5 | Zoom setting, UI state and view model | `41-capture-app-zoom/Z5-view-model.md` | Z4 | sonnet | State holder with JVM tests, behavior specified (CP15 to CP17) |
| Z6 | Zoom row (slider, "−" / "+" buttons) on the screen, strings, captures | `41-capture-app-zoom/Z6-zoom-row.md` | Z5 | sonnet | Screen change from a specified layout, width and height budget |

One `opus` task (Z4).

## Decisions

| Id | Decision | Rules |
|---|---|---|
| KZ1 | Kosmo: continuous slider ("molette"), from the camera's minimum to maximum ratio; no pinch, no fixed steps. | CP14 |
| KZ2 | Kosmo: zoom adjustable outside a session only; disabled while running or saving. | CP15 |
| KZ3 | Kosmo: the zoom used is recorded per session in `truth.json`; new format version, documented, strict decoder. | CP17 |
| KZ4 | Kosmo: the last zoom is remembered in the private settings, like the quality. | CP16 |
| KZ5 | Kosmo: delivered in PR #63. | - |
| KZ6 | Kosmo: the zoom logic must be reusable later in Kosmo Darts. | - |
| DZ1 | Logarithmic slider mapping: the same hand movement gives the same relative zoom change at 1.2× and at 6×. Linear rejected (crowds the useful 1× to 3× into the first fifth of the bar). | CP14 |
| DZ2 | Ratio rounded to 0.1 then clamped: the shown, applied, remembered and recorded values are the same number. | CP14, CP16 |
| DZ3 | Placement: package `io.github.darkcosmos.kosmodarts.camera.zoom` inside `:capture`, pure Kotlin, isolation enforced by a JVM test. A shared module is not created: it is proposed to Kosmo for the camera milestone (ADR 0010). | - |
| DZ4 | Zoom applied through `CameraControl.setZoomRatio`, range from `ZoomState`; re-applied after every binding, including during a session (CameraX resets zoom on rebinding). | CP16 |
| DZ5 | Truth `zoom` has `requestedRatio` and `ratio`, both nullable: what the app asked and what the camera reported, honest when they differ or are unknown. | CP17 |
| DZ6 | Version 1 files rejected by the reference decoder, not migrated: the app never reads truth files back (decode is used by tests only), version 1 files were made at zoom 1.0 and the document says so for the phase 0b tools; a migration would be dead code. | CP17 |
| DZ7 | Slider saved on release only (no file write per frame), button saved per effective press (DZ11); a failed write is silent, the zoom applies until the app closes. | CP16 |
| DZ8 | Zoom row between the preview and the control row, always visible, greyed during a session (the locked value stays readable). | CP15 |
| DZ9 | New ADR `docs/adr/0010-capture-zoom.md` (format change and reuse intent), not an addendum to 0009. | - |
| KZ7 | Kosmo (plan approval): agrees with the UX remark, add "−" and "+" buttons next to the slider ("on peut également ajouter des boutons plus et moins"). 0.1× per press, same lock, rounding, clamping and memory rules as the slider. | CP14 to CP16 |
| DZ10 | Rounding (T1) is done on the shortest decimal form of the float (`Float.toString()` into `BigDecimal`, half up), not on its binary value: 1.05 gives 1.1 and 2.35 gives 2.4, as a person reading the number expects. `java.math` is allowed in the portable package (JVM and Android). | CP14 |
| DZ11 | A button press is one complete gesture: the new ratio is applied and saved at once when it changed (no save when clamped at a range end). The step starts from the rounded current ratio, so from 8.37 (shown `8.4×`) "−" gives 8.3. Each button is disabled at its range end, so a useless press is visible. | CP14 to CP16 |
| DZ12 | No repeat on long press: the slider covers large moves and the buttons only fine aim (a few presses); a repeat needs timing code in the composable, a save policy per repeat and risks overshooting; Kosmo marked it optional. Can be added later without a format change. | CP14 |
| DZ13 | 8dp gap between the zoom row and the control row (U18; 4dp elsewhere): two rows of 42dp targets (slider and buttons above, Start and quality below) need a clear separation against mistaken taps; costs 4dp of preview. | - |
| DZ14 | Value text 64dp to 140dp wide, `WordSafeText` with at most 2 lines, shrinking (U17): "Zoom not available" / « Zoom non disponible » at font scale 2.0 would otherwise take the slider's width; the row stays 42dp at every font scale. | CP15 |

Reviewer minors of the design review, how each is handled:

| Id | Handling |
|---|---|
| A1 | Z2 isolation test asserts listed (no `capture` in package and import lines; no import from `android`, `androidx`, `kotlinx.coroutines`; only `kotlin.` and `java.math.` imports). |
| A2 | Z1: ADR 0010 states that moving the package into a shared module happens in a single PR together with the shared module decision. |
| A3 | Z5: `CaptureSettings` calls `ZoomSettingFormat.parse` / `format` and contains no zoom fallback of its own; a test proves `zoom=0` reads 1.0 through it. |
| S9 | No cap; root "Truth file version 2" and Z4: the format document states that phase 0b tools must not assume a plausible range. |
| S10 | Z5 and root "Data read, stored or exposed": unknown settings keys are ignored on read and dropped by the next write (existing behavior, kept and tested). |
| U17 | DZ14; Z6 checks slider width at least 120dp at 915x412 font scale 2.0 and at least 96dp at 640x360, "not available" state, EN and FR. |
| U18 | DZ13 (8dp). |
| U19 | Resolved by the "−" / "+" buttons (KZ7): exact values reachable and repeatable. |
| U20 | "How Kosmo verifies" step 6: the greyed row keeps its value readable. |
| T1 | DZ10; Z2 cases 1.05 -> 1.1, 2.35 -> 2.4, 2.45 -> 2.5. |
| T2 | Z2: match rules pinned (case-sensitive, lowercase, `package` and `import` lines only, comments ignored). |
| T3 | Z5: sequence adjustable range -> null range, then Start: `requestedRatio` null. |
| T4 | Z6: at 640x360, preview at least 160dp, zoom row 42dp, control row inside the screen; `CaptureActionRowTest` re-run unchanged. |
| T5 | "PR description material": Known limitations says the CameraX zoom is verified manually only. |

## Open questions

None.
