# T7: Capture screen and captures

## Goal
The single landscape screen that renders `CaptureUiState` of T6 and forwards events, with a preview slot that tests
replace by a placeholder.

## Files
- Added in `capture/.../capture/ui/`: `CaptureScreen` (two columns, status line, volley readout, keypad of T5,
  action and control rows; `preview: @Composable () -> Unit` slot), `StopConfirmDialog`, `DeleteAllDialog`,
  `PermissionPanel`, test tags. No logic: the readout and every enabled flag come from the state.
- Strings in `values/` and `values-fr/`: root plan section "Screens and states" table (French plurals: `one` covers
  0 and 1).
- `CaptureActivity`: `enableEdgeToEdge`, safe drawing insets, hosts the screen with a placeholder preview (T8 wires
  the camera).

## Depends on
T5, T6.

## Rules
CP2, CP3, CP6, CP7, CP8, CP13. Root plan "Screens and states": layout, height budget, primary action per state,
42dp landscape buttons (Q14), separated Delete all (Q16). Test names or comments cite the CP id.

## Tests
- Robolectric UI at 915x412dp: Start then T20, 5, Miss shows "Volley 1" with `T20`, `5`, `Miss` and the volley full
  hint; Undo clears the third slot; Stop shows the confirmation and Continue keeps the session; Delete all shows the
  dialog with the reinstall note; every icon, the REC dot, the preview and the toggle have a content description.
- CP13 (U9): the photo failure message is a Material snackbar, announced by TalkBack (live region), shown at least
  4 seconds; its dismiss action stays reachable and at least 42dp tall at font scale 2.0.
- CP3 (U10): when the volley is full, the readout's content description includes the "Volley full" hint.
- At 412dp height and at font scale 2.0: no text clipped, buttons and keys at least 42dp tall and 48dp wide.
- Roborazzi captures, English and French, at 915x412dp, at 640x360dp and at font scale 2.0: idle, running with 2
  darts, volley full, Stop confirmation, Delete all confirmation, permission missing, permanently denied, photo
  failure message, saving, error.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from a specified layout and height budget.

## Implementation notes
- `capture.ui`: `CaptureScreen(state, actions: CaptureActions, preview, modifier)`; `CaptureActions` is a lambda bundle,
  `CaptureActions.of(viewModel, onAllowCamera, onOpenSettings)` builds it (T8 supplies the permission lambdas).
  `CaptureDialogs.kt` (Stop, Delete all), `PermissionPanel`, `CaptureTags`. Strings added in en and fr.
- `CaptureActivity` shows a static idle `CaptureUiState` with no-op actions and an empty preview: T8 must create the
  view model and replace it. Insets: `safeDrawing` padding around the screen.
- Additions beyond the plan table: `error_delete_all` message for `CaptureError.DELETE_ALL`, Dismiss buttons (error
  banner, snackbar; fr "Fermer"). Photo snackbar: `SnackbarDuration.Short` with an action (4 s), live region.
- Control row is a `FlowRow` (wraps at 640x360); right column scrolls vertically as a safety net, and at 640x360 the
  Next volley button is slightly cut at the bottom without scrolling (about 8dp over budget).
- Tests: `CaptureScreenTest` (behavior, harness in `CaptureScreenHarness.kt`), `CaptureScreenshotsTest` (60 images
  `capture_<state>_<size>_<lang>.png` in `capture/build/outputs/roborazzi/`). Roborazzi qualifiers: language first.
