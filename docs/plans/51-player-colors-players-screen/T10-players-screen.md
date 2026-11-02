# T10: Players screen and home entries

## Goal
The Players screen composable and the two home entries with icons. Root "Screens and states", Home and Players.

## Files
- `app/.../ui/players/PlayersScreen.kt`, `PlayersTags.kt` (new): list cards, expanded actions, inline panels, live
  region messages, states of `PlayersState`; uses `ScreenParts` top bar and `KosmoButton`.
- `app/.../ui/home/HomeScreen.kt`, `HomeTags.kt`: "Players" button under "History", both with a leading icon
  (decorative, D13).
- `app/src/main/res/drawable/ic_history.xml`, `ic_players.xml` (new vector drawables, drawn by hand, no new
  dependency).
- `MainActivity` or the screen switch that renders `Screen`: render `Screen.Players`.
- `values/strings.xml`, `values-fr/strings.xml`: every text of root "Screens and states", Players.
- Tests: `PlayersScreenTest`, `PlayersFlowTest` (Compose JVM), `PlayersScreenshotTest`, `HomeScreenScreenshotTest`.

## Depends on
T9.

## Rules
PM1, PM2, PM11; HS1.

## Tests
- Home: both buttons, icons, 48dp, tap opens each screen; back returns home.
- Expand one card at a time; each panel and its buttons; refusal and error texts shown and announced.
- Rename field with IME action; clash shows "Merge into ..." button.
- Captures, Pixel 7, portrait, landscape and 200% font: list, empty, rename clash, merge confirmation, delete
  confirmation; home.

## Done when
`./gradlew :app:testDebugUnitTest` (records the captures under `app/build/outputs/roborazzi`), `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan's description with Compose tests and captures.

## Implementation notes
