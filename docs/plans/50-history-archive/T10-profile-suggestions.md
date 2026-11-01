# T10: Profile suggestions in the setup

## Goal
Under the focused name field of the setup, earlier profiles are suggested, most recent first, and fill the field on
tap (PF3, D1, D2). Root plan "Screens and states", Setup.

## Files
- `app/src/main/kotlin/.../ui/app/`: setup state gains the profile list and the focused field; events
  `NameFieldFocused(index)`, `SuggestionPicked(index, name)`; pure function `suggestions(profiles, names, focused,
  typed)` in a new `ui/setup/ProfileSuggestions.kt` (or next to the setup state, as the code is organized).
- `ui/components/PlayerNameRow.kt` (suggestion rows under the field, 48dp, design system list style; each row has
  the button role and the description « Utiliser le nom Kevin »; the list stays above the keyboard: IME insets and
  bring-into-view; it takes its space without shifting the rows above the focused field; tapping a row keeps the
  focus and the keyboard state, review U3).
- `KosmoDartsViewModel.kt`: profiles loaded through the read executor when the setup opens; a `Failed` read shows no
  suggestion and the setup works as before (AR9). Filtering is in memory on name keys, never SQL `LIKE` (review S4).
- Strings `values/` and `values-fr/` (row description).
- Tests: `ProfileSuggestionsTest` (JVM), `SetupSuggestionsScreenTest` (Compose), captures in
  `CricketSetupScreenshotTest` / X01 setup captures.

## Depends on
T8.

## Rules
PF3, PC9, R7, R22, M13, AR9; D18.

## Tests
- Order by recency; at most 5; filter "contains" on name keys; empty field shows all (5 max); names used in other
  fields excluded; no suggestion when nothing matches.
- Typed text with accents and its NFC-equivalent decomposed form ("Zoé" typed as "Zoe" + combining accent) matches
  the profile "Zoé"; typed `%` and `_` are plain characters (review S4).
- The profile whose name is exactly the focused field's text is hidden; "kevin" typed shows "Kevin" (D18, review T7).
- Load failure (`Failed` fake): no suggestion, Start still works.
- Tapping fills the field and the prefill (last setup) is unchanged elsewhere.
- Captures: suggestions shown, X01 and Cricket setup, portrait, landscape, `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen change from written rules with a pure state function tested on the JVM.

## Implementation notes
- `ui/setup/ProfileSuggestions.kt`: pure `suggestions(profiles, names, focused)` (key "contains" in memory, other fields
  excluded by key, exact-text profile hidden, recency order, max 5). `SetupState` gained `profiles` and `focusedIndex`;
  the helpers `suggestionsFor`, `withProfiles`, `withFocusOn`, `withFocusLostBy` are extension functions (detekt
  function count). Remove and shuffle reset the focus.
- Events `NameFieldFocused`, `NameFieldFocusLost`, `SuggestionPicked` (reuses `withName`: focus and prefill untouched),
  `ProfilesLoaded`. Effect `LoadProfiles` is emitted by `persistenceEffects` when the setup opens from another screen;
  `Persistence.loadProfiles` answers on the queue, `Failed` or no repository gives an empty list. The two tests that
  expected `[DeleteGame]` on opening the setup now expect `[DeleteGame, LoadProfiles]`.
- Limitation: a setup shown at launch (restore-failed dialog) reads no profiles, so it has no suggestions.
- `PlayerNameRow`: `SuggestionSpec`, rows of at least 48dp under the row (button role, description « Utiliser le nom X »),
  brought into view once when the list first shows (not per key). When the list shrinks at the end of the scroll the
  content can move up or down by the height difference; this is scroll clamping, not a layout shift under a tap.
- Tests: `ProfileSuggestionsTest` (JVM), `SetupSuggestionsScreenTest` (Compose). Captures in `CricketSetupScreenshotTest`
  (6, portrait/landscape, fontScale 2, FR and EN). I viewed the portrait and landscape font 200 PNGs: rows readable,
  nothing clipped, START stays visible. The others were recorded, not inspected. The real IME is not simulated.
