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

- "Manage player" toggle and the inline actions panel live in `ui/profile/ProfileHeader.kt` (`ManageActions`), tags
  `ProfileTags.MANAGE` and `ACTIONS_PANEL`; shown in every Profile state, including no match (PM11, PS7).
- The action tags (RENAME, MERGE, RESET, DELETE, CONFIRM...) still live in `PlayersTags`; reused as is.
- Players screen is a plain list: a row is one 48 dp target opening the Profile (PM2, PS1); no action button.
- HS8: new `AppEvent.DetailPlayerTapped(profileId)`; `HistoryPlayer` gained `profileId`; `reduceMatchDetail` and
  `reduceStacked` take the `stamp`. Only the ranking rows of the match detail are links (visit rows and leg lines
  use clearAndSetSemantics, so they are not). A deleted player is plain text, never a link (PM6).
- "Compare with..." is not added here: it belongs to T25.
- `PlayersFlowTest` has no `@Ignore` any more: its flow runs through the Profile; reads are chained, so the tests
  settle several rounds. Shared `FakeProfileStatsRepository`; `AppProcess` takes a `profileStats` parameter.
- New tests: `ProfileManageTest`, additions in `PlayersScreenTest`, `MatchDetailScreenTest`, `NavigationStackTest`;
  13 Roborazzi captures `profile_actions_*` (Pixel 7, portrait/landscape, en/fr, 200% font).
