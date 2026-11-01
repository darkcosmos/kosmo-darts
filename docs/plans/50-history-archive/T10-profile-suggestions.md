# T10: Profile suggestions in the setup

## Goal
Under the focused name field of the setup, earlier profiles are suggested, most recent first, and fill the field on
tap (PF3, D1, D2). Root plan "Screens and states", Setup.

## Files
- `app/src/main/kotlin/.../ui/app/`: setup state gains the profile list and the focused field; events
  `NameFieldFocused(index)`, `SuggestionPicked(index, name)`; pure function `suggestions(profiles, names, focused,
  typed)` in a new `ui/setup/ProfileSuggestions.kt` (or next to the setup state, as the code is organized).
- `ui/components/PlayerNameRow.kt` (suggestion rows under the field, 48dp, design system list style).
- `KosmoDartsViewModel.kt`: profiles loaded through the read executor when the setup opens.
- Strings `values/` and `values-fr/` (content description of the list if needed).
- Tests: `ProfileSuggestionsTest` (JVM), `SetupSuggestionsScreenTest` (Compose), captures in
  `CricketSetupScreenshotTest` / X01 setup captures.

## Depends on
T8.

## Rules
PF3, PC9, R7, R22, M13.

## Tests
- Order by recency; at most 5; filter "contains" ignoring case; empty field shows all (5 max); names used in other
  fields excluded; no suggestion when nothing matches.
- Tapping fills the field and the prefill (last setup) is unchanged elsewhere.
- Captures: suggestions shown, X01 and Cricket setup, portrait, landscape, `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen change from written rules with a pure state function tested on the JVM.

## Implementation notes
