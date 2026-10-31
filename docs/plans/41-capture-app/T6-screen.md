# T6: Capture screen, view model, captures

## Goal
The single landscape screen and its state holder, with a camera preview slot that tests replace by a placeholder.

## Files
- Added in `capture/.../capture/ui/`: `CaptureUiState` (permission, idle, running, error, confirm delete; counters;
  quality; enabled flags per CP2/CP3/CP4), `CaptureViewModel` (holds `CaptureSession`, applies events, exposes
  effects for the camera controller of T7 through an interface), `CaptureScreen` (renders state, forwards events,
  `preview: @Composable () -> Unit` slot), `DeleteAllDialog`, test tags.
- Strings in `values/` and `values-fr/`: root plan section "Screens and states" table.
- `CaptureActivity`: hosts the screen with a placeholder preview for now (T7 wires the camera).

## Depends on
T3, T5.

## Rules
CP2, CP3, CP4, CP7, CP8, CP11, CP12 (state flag `keepScreenOn` only; T7 applies it).

## Tests
- JVM tests of `CaptureViewModel` with fake clock and fake effects sink: enabled flags in every state, counters
  after undo, quality toggle refused while running, Delete all confirmation flow, error state after a failed write.
- Robolectric UI: Start then three darts disables the keypad; Undo re-enables it; Delete all shows the dialog with the
  reinstall note; every icon has a content description; buttons at least 48dp.
- Roborazzi captures (landscape, about 915x412dp, placeholder preview), English and French: idle, running with 2
  darts, 3 darts, permission missing, delete confirmation, error.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from a specified layout and a state holder with JVM tests.

## Implementation notes
