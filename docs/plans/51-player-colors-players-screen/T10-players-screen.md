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
- `values/strings.xml`, `values-fr/strings.xml`: every text of root "Screens and states", Players. Match counts and
  the PM6 erased count are `plurals` with an explicit zero form text ("No match yet", "No match will be erased";
  review U5).
- Layout (U4, U6): the four actions in a 2 x 2 grid of 48dp buttons (Rename, Merge / Reset, then Delete alone on
  the last row, separated by 16dp and written in `dangerText`), stacked vertically when the 200% font does not fit.
  The list is a `LazyColumn`; an expanded card, its panel and the focused rename field are brought into view above
  the keyboard (`imePadding`, `bringIntoViewRequester`); the merge target list scrolls with the screen.
- Tests: `PlayersScreenTest`, `PlayersFlowTest` (Compose JVM), `PlayersScreenshotTest`, `HomeScreenScreenshotTest`.

## Depends on
T9.

## Rules
PM1, PM2, PM11; HS1.

## Tests
- Home: both buttons, icons, 48dp, tap opens each screen; back returns home.
- Expand one card at a time; each panel and its buttons; refusal and error texts shown and announced.
- Rename field with IME action; clash shows "Merge into ..." button.
- Captures, Pixel 7, portrait, landscape and 200% font: list, empty, expanded card with its actions, rename clash,
  merge confirmation, delete confirmation; home with the Players button reachable in landscape (D19).
- Zero and one match texts from the plurals; PM8 message names the player.

## Done when
`./gradlew :app:testDebugUnitTest` (records the captures under `app/build/outputs/roborazzi`), `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan's description with Compose tests and captures.

## Implementation notes

- Players screen: `ui/players/PlayersScreen.kt` (frame, list, cards) and `PlayersPanels.kt` (actions, rename, merge, confirmations). It reuses `HistoryScaffold`, `LoadingIndicator` and `formatEndDate` from `ui/history`. `KosmoDartsApp` routes `Screen.Players` to it.
- `ButtonKind.DESTRUCTIVE` added (danger text on the raised surface), and `KosmoButton` gained an optional decorative `iconRes` (contentDescription null, D13). Home: History gets `ic_history`, the new Players button (`HomeTags.PLAYERS`) sits under it, both reachable by scroll above the version footer (D19, tested in landscape).
- Deviation: zero forms ("No match yet", "no match will be erased") are separate strings (`players_no_match`, `players_delete_erased_none`), because en/fr plurals never select `zero`; `one`/`other` use plurals.
- Layout: Rename and Merge share a row, Reset is full width, Delete is full width 16dp below. Everything stacks when `LocalDensity.fontScale >= 1.3` (`STACK_FONT_SCALE`), not by measuring.
- Test helpers: `AppProcess` takes an optional `players` repository; `PlayersScreenTest` uses an `unmerged()` finder for nodes merged into their parent. Captures: `PlayersScreenshotTest` (20) and 3 new in `HomeScreenScreenshotTest`, Pixel 7 only.
