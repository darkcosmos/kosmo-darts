# T15: Actions panel UI, Players list, detail links

## Goal
"Manage player" unfolds the existing action panels in the Profile; the Players screen becomes a list of rows that
open the Profile; player names in a match detail open the Profile. Root "Approach" 5.

## Files
- `app/.../ui/profile/ProfileActionsPanel.kt` (moved from `ui/players/PlayersPanels.kt`), `ProfileHeader.kt`:
  "Manage player" toggle (P1 A), "Compare with…" button (disabled placeholder until T25, hidden if simpler).
- `app/.../ui/players/PlayersScreen.kt`, `PlayersTags.kt`, `PlayersText.kt`: rows only, whole row tappable (48 dp),
  TalkBack "Open <name>'s profile".
- `app/.../ui/history/MatchDetailScreen.kt`: tappable names (HS8), "Deleted player" plain text.
- Strings en and fr (moved keys kept where possible).
- Tests: `PlayersScreenTest`, `PlayersFlowTest` adapted; `ProfileActionsTest.kt`; `MatchDetailScreenTest` name tap;
  captures `PlayersScreenshotTest` (list) and the panels in `ProfileScreenshotTest`.

## Depends on
T13.

## Rules
PM2, PM11, PS2, PS7, PS8, HS8.

## Tests
- Every Players UI test case kept, now in the Profile (panels, confirmations, refusals, errors announced).
- Row tap opens the Profile; detail name tap opens the Profile; anonymous not clickable.
- Captures: Players list; Profile with manage open, rename clash, merge confirm, delete confirm; portrait, landscape,
  200 % font.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: move of existing panels with their tests and captures.

## Implementation notes
