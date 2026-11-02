# Plan: player colors per match and Players screen (issue #51)

## Goal

Issue [#51](https://github.com/darkcosmos/kosmo-darts/issues/51), second part of "History and statistics". In the match
setup each player gets his own color, always readable: the four palette colors by default, then a row of swatches
completed with randomly generated colors. A new Players screen, opened from the home screen, lists the profiles and
lets Kosmo rename, merge, reset and delete them. Kosmo's answers are in "Decisions" (Q1 to Q8).

## Scope and out of scope

In scope:
- Engine: player colors as ARGB values (`PlayerColor`), replacing the 1-to-4 color slots (`ColorSlot`); contrast,
  OKLab distance, readability, text color on a player color, default attribution, swatch choices and random
  generation (rules PC1 to PC17).
- Saved game and last setup at format version 2, colors stored as ARGB (R17, R31). Room schema unchanged (version 1).
- Setup: a swatch row per player, without popup (PC14 to PC16).
- Text on a player color (giant score, keypad zone, tiles, History) chosen by contrast (PC13).
- Players screen: list, rename, merge, reset, delete (PM1 to PM11), with the single anonymous profile (PM7).
- Home: Players entry with an icon, History entry gets an icon (PM1).
- Rules rewritten: PF1, PF3, PF5, AR8, HS2, HS3, HS5 (`docs/rules/history.md`), PC1 to PC9 (`docs/rules/players.md`),
  R17 (`docs/rules/persistence.md`).

Out of scope: statistics and the player profile screen (H3), heatmap and head-to-head (H4), a free hue picker (Q3),
showing the reset date (Q7), any new dependency, any Room schema change, screens smaller than a Pixel 7.

## Approach

1. **Colors in the engine.** `PlayerColor(argb: Int)` (value class, opaque colors only) replaces `ColorSlot` in every
   engine type (`Roster`, `X01MatchConfig`, `CricketMatchConfig`, matches, `ArchivedPlayer`) and in the app.
   `PlayerColorRules` holds the palette, the design system background and surface, the WCAG contrast ratio, the OKLab
   distance and the named thresholds; `ColorChoices` builds the swatches of a player with an injected `Random`.
   The engine still knows no Compose type: the app maps `PlayerColor` to `Color` in `PlayerPalette`.
   - Alternative rejected: keep `ColorSlot` and add a side table of generated colors. Two sources of truth for one
     color; every screen would need both.
2. **Two steps for the stored format.** T3 swaps the type everywhere while the files still store slot numbers (only
   palette colors exist before the picker); T4 moves the saved game and the last setup to format version 2 with ARGB
   colors. Version 1 files follow R30 (clean slate, no release yet): saved game rejected with the R9 message, last
   setup ignored (R10). ADR 0013 records it.
   - Alternative rejected: one task for both. Too large for one build agent (about 55 files touched by the swap).
3. **Generated colors** (PC15): random hue, lightness and chroma in OKLCH ranges, converted to sRGB, kept only when in
   gamut and conforming to PC11, PC12 and PC13; at most `MAX_GENERATION_ATTEMPTS` draws per color, never a
   non-conforming color. Drawn when the swatch row opens; unused ones are forgotten when it closes.
4. **Profile rules in the engine** (`engine.profile.ProfileRules`): rename outcome, merge check and merged dates,
   deletion plan (which matches become anonymous, which are deleted), in-progress guard, and the last setup roster
   updates (PM9). Pure functions over ids, name keys and match player lists.
5. **Room without a new schema.** The anonymous profile is a normal `profile` row with the reserved name key `""`
   (no real name has it: `PlayerListRules` rejects blank names) and the name `""`; the app shows it with a string
   resource. Merge, reset and delete each run in one `runInTransaction`. Deletion removes rows child first (dart,
   leg, match_player, match_record, session), since every foreign key is NO ACTION.
   - Alternative rejected: a `hidden` column. Needs schema version 2 and a migration for a single flag.
   - ADR 0014 (T8) records the convention, the rejected alternative and the invariant: every query that lists
     profiles, suggestions or player filters excludes the reserved key.
   - Colors read from Room (`match_player.color_argb`) are mapped without throwing: a value whose alpha is not `FF`
     is drawn opaque (alpha forced to `FF`) in History, never a crash (D16).
6. **Players screen** follows the History pattern: `PlayersState` state holder (JVM tests), `Screen.Players`, effects
   queued on the persistence executor, results as events. Every confirmation is an inline panel under the profile,
   never a dialog. After a successful action the last setup file is rewritten (PM9).

## Files and types added or changed

- Engine `engine/player/`: `PlayerColor.kt`, `PlayerColorRules.kt`, `ColorChoices.kt`, `Oklab.kt` (new);
  `ColorSlot.kt` (removed in T3); `Roster.kt`; `x01/X01MatchConfig.kt`, `x01/X01Match.kt`,
  `cricket/CricketMatchConfig.kt`, `cricket/CricketMatch.kt`, `archive/MatchArchiver.kt`, `archive/ArchivedPlayer.kt`.
- Engine `engine/profile/ProfileRules.kt`, `engine/profile/RosterProfileUpdates.kt` (new).
- App persistence: `SaveBody.kt`, `CricketSaveBody.kt`, `LastSetupCodec.kt`, `GameSaveCodec.kt`, `LastSetups.kt`.
- App history: `ProfileDao.kt`, `HistoryDao.kt`, `HistoryRepository.kt`, `ProfileRepository.kt`,
  `ArchiveRepository.kt`, `PlayersRepository.kt` (new), `PlayersDao.kt` (new).
- App UI: `ui/theme/PlayerPalette.kt`, every user of `ColorSlot` (components, game, cricket, between, end, home,
  setup); `ui/setup/SetupState.kt`, `ui/components/PlayerNameRow.kt`, `ui/setup/ColorSwatchRow.kt` (new);
  `ui/players/` (new: `PlayersState.kt`, `PlayersScreen.kt`, `PlayersTags.kt`); `ui/app/AppState.kt`,
  `AppReducer.kt`, `PlayersReducer.kt` (new), `PersistenceEffect.kt`; `ui/home/HomeScreen.kt`, `HomeTags.kt`.
- Resources: `drawable/ic_history.xml`, `drawable/ic_players.xml` (new vector drawables), `values/strings.xml`,
  `values-fr/strings.xml`.
- Docs: `docs/rules/players.md`, `docs/rules/history.md`, `docs/rules/persistence.md`,
  `docs/design/design-system.md`, `docs/adr/0013-save-format-v2-argb-colors.md` (new, T4),
  `docs/adr/0014-anonymous-profile.md` (new, T8).

## Data read, stored or exposed

- Saved game and last setup (private app storage): `colors` line becomes one ARGB value per player (8 uppercase hex
  digits, alpha `FF`), format version 2 (R17, R31). Names unchanged.
- Room `profile`: rename updates `name` and `name_key`; reset updates `reset_at`; merge updates the target's dates
  and deletes the source; delete moves participations to the anonymous profile, deletes the profile and the matches
  left with only the anonymous profile (PM4 to PM7). `match_player.color_argb` now holds any conforming color.
- Read for the Players screen: per profile, match count and latest `session.ended_at`. Nothing leaves the device, no
  name is logged.

## Rules (new and rewritten)

T1 copies this section into `docs/rules/`. Rule ids are stable; a rewritten rule keeps its id.

`docs/rules/players.md` (title becomes "Player rules (colors)"; vocabulary: *player color*, an opaque ARGB color
`PlayerColor`; *palette*: blue `#1F63D6`, orange `#E07020`, teal `#0C7C6A`, lilac `#C9A7FF`, in this order):
- PC1. A player has exactly one color; two players of a setup or a match never share a color.
- PC2. Adding a player gives him the first palette color acceptable for him (PC12), in palette order. When none is
  (other players hold generated colors close to the free palette colors), he gets a generated color (PC15).
- PC3. Removing a player frees his color; the other players keep theirs.
- PC4. Shuffle changes the order only; each player keeps his color.
- PC5. A match configuration with colors of the wrong count or repeated colors is rejected. Without explicit colors,
  each player gets the palette color of his position.
- PC6. Colors never change during a match (every leg and set).
- PC7. Rematch keeps each player's color (the first player moves to the end with his color, M17).
- PC8. The setup prefilled from the last setup restores each player's color.
- PC9. The UI draws each player with his color; the color never carries information alone (name always shown).
- PC10. The background is `#0A0F1E` and the tile surface `#121A33` (design system section 2).
- PC11. Readable: a player color has a WCAG contrast ratio of at least `MIN_COLOR_CONTRAST` = 3.0 against the
  background and against the surface. A name written in the player color (History) uses this threshold (Q4).
- PC12. Distinct: the OKLab Euclidean distance between two colors of a setup is at least `MIN_COLOR_DISTANCE` = 0.12.
  The four palette colors satisfy it pairwise. A color is *acceptable* for a player when it satisfies PC11, PC13 and
  this distance against the colors of every other player (`PlayerColorRules.isAcceptable(color, others)`); every
  color given by PC2 or offered by PC14 and PC15 is acceptable. One function serves all three.
- PC13. Text on a player color is white `#FFFFFF` or dark `#0A0F1E`, whichever has the higher WCAG contrast ratio
  (white on a tie). For every palette color and every generated color that ratio is at least `MIN_TEXT_CONTRAST`
  = 4.5. This applies to the giant score, the current player zone and keypad area, the tiles and History.
- PC14. The swatches offered to a player: his own color first, then the palette colors acceptable for him (PC12), in
  palette order, then generated colors until at least `MIN_CHOICES` = 4 swatches are offered. A color of another
  player, or a palette color too close to one, is never offered.
- PC15. A generated color is drawn at random (injected random source) and is acceptable (PC12) against every other
  player's color and every swatch already offered. After `MAX_GENERATION_ATTEMPTS` = 200 failed draws for one
  swatch, fewer swatches are offered; a non-conforming color is never offered.
- PC16. Tapping a swatch gives that color to the player and closes the swatch row; the other players keep theirs.
  Closing the row without a choice changes nothing.
- PC17. All color logic and thresholds live in `:game-engine` as named constants with tests.

`docs/rules/history.md`:
- PF1 (rewritten). A profile has a name, a name key (unique), a reset date (equal to the creation date at creation,
  then changed only by PM4 and PM5; statistics count from it), a creation date and a last-used date. It has no color.
- PF3 (completed). The anonymous profile (PM7) is never suggested.
- PF5 (rewritten). A profile is deleted only by PM4 (merge source) and PM6. The anonymous profile is never deleted.
- AR8 (rewritten). A match is deleted only by PM6, when every one of its players is the anonymous profile.
- HS2, HS5 (completed). A player of the anonymous profile is shown as "Deleted player" ("Joueur supprimé").
- HS3 (completed). The anonymous profile is not a player filter.
- PM1. The home screen has a "Players" button under "History", same form; both carry an icon (decorative, the text is
  the accessible name), 48dp touch targets.
- PM2. The Players screen lists every profile except the anonymous one, most recently used first. Each shows its
  name, its number of matches and the end date of its last match ("No match yet" when none). The reset date is not
  shown (Q7). With no profile, the screen says so.
- PM3. Rename: the new name is trimmed and normalized and must be valid under `PlayerListRules` (non-blank, at most
  20 code points). Same name key as the profile: the spelling changes (case-only rename allowed). Name key of another
  profile: refused, and the screen offers to merge this profile (source) into that one (target). History shows the
  current name.
- PM4. Merge a source into a target: every participation of the source moves to the target, then the source is
  deleted. The target keeps its name; its reset date and last-used date become the latest of the two, its creation
  date the earliest. Refused, with a short message, when both profiles played a same match. One transaction.
  Irreversible, so preceded by an explicit confirmation inside the screen.
- PM5. Reset: the reset date becomes now; nothing else changes and no match leaves History. Preceded by an explicit
  confirmation inside the screen.
- PM6. Delete: in one transaction, every participation of the profile moves to the anonymous profile, every match
  whose players are then all the anonymous profile is deleted (with its legs, darts and players), then the profile is
  deleted. Preceded by an explicit confirmation inside the screen that gives the number of matches that will be
  erased. Irreversible.
- PM7. One anonymous profile for the whole app, with the reserved name key `""`, created at the first deletion. It is
  never listed, suggested, filtered on, renamed, merged, reset or deleted. It is shown as "Deleted player".
- PM8. Rename, merge (source or target) and delete are refused, with a short message, while the saved game in
  progress has a player whose name key is that of a profile concerned.
- PM9. After a successful rename, merge or delete, the last setup is updated: rename gives the entry the new spelling;
  merge gives the source entry the target's name, or removes it when the target is already in the roster; delete
  removes the entry, and when it was the only one, leaves one entry with an empty name and its color. Other entries
  and colors are unchanged. A failure to write the last setup is ignored (R10).
- PM10. A database error leaves everything unchanged (transaction) and shows a short message in the screen; the
  action can be retried.
- PM11. No popup: confirmations, refusals and errors are shown inside the Players screen, announced by TalkBack, in
  portrait and landscape and at 200% font size.

`docs/rules/persistence.md`:
- R17 (rewritten). Each player's color (ARGB, PC1) is saved with the game and with the last setup, so a resume, a
  rematch (PC7) and the next setup (PC8) keep every player's color.
- R31. The saved game and the last setup are at format version 2: the `colors` line holds one 8-digit uppercase hex
  ARGB value per player, alpha `FF`, all distinct; anything else rejects the file (R9, R10). Version 1 files are
  handled as R30 says (clean slate). On load, PC11 to PC13 are not checked again: a stored opaque, distinct color is
  accepted as it is (D15).

## Screens and states

- **Setup** (X01 and Cricket): the color strip of each `PlayerNameRow` becomes a 48dp tappable swatch ("Change the
  color of Kevin"). Tapping it opens, under the row, a single row of 48dp round swatches (PC14), the current one with
  a check mark and "selected" for TalkBack; palette swatches are described by their name (blue, orange, teal, lilac),
  generated ones as "Color 5", "Color 6"... Tapping a swatch applies it (PC16); tapping the player's swatch again
  closes the row. One row open at a time. The swatches (4 to 7) wrap onto a second line when needed, and the open row is
  brought into view.
- **Home**: "History" and "Players" secondary buttons with leading icons.
- **Players**: top bar "Players" with back. States: loading, failed (HS7 message and retry), empty (PM2), list.
  Tapping a profile card expands it (one at a time) with four buttons: Rename, Merge, Reset, Delete.
  - Rename: text field prefilled, "Save" and "Cancel"; error under the field (invalid name; name taken, with a
    "Merge into Lea" button); in-progress refusal (PM8).
  - Merge: "Merge Kevin into:" then the other profiles as rows; picking one shows the confirmation panel ("Kevin's
    matches move to Lea; Kevin disappears. This cannot be undone.", "Merge" and "Cancel"); refusal message for a
    shared match.
  - Reset: confirmation panel ("Statistics of Kevin will count from today. No match is erased.", "Reset", "Cancel").
  - Delete: confirmation panel ("Kevin will be deleted. His matches with other players stay, under Deleted player.
    N matches will be erased. This cannot be undone.", "Delete", "Cancel"); warning text in `dangerText`.
  - Each panel shows the PM10 error in place. Messages are live regions.
- **History**: anonymous players shown as "Deleted player"; text on player colors by PC13.

## Test plan

- Engine (JVM, cite PC/PM ids): palette constants and pairwise PC12; PC11 and PC13 for every palette color; WCAG
  ratio against known pairs (white/black 21:1, equal colors 1:1); OKLab distance against reference values; default
  attribution and removal (PC2, PC3); choices with 1 to 4 colors taken (PC14), never a color of another player;
  generation with a seeded `Random`, 1 to 4 colors taken, every generated color conforming PC11 to PC13 and distinct
  from all others (PC15); a random source that always fails gives fewer swatches, never a bad one; a property loop
  over many seeds; rename outcomes (invalid, same key, case-only, clash), merge check and dates, deletion plan (solo
  match, match with another player, match with two deleted players, match with the anonymous profile already), PM8
  guard, PM9 roster updates (only entry, target present).
- Data (Robolectric): v2 codecs round trip with generated colors; v1 file rejected (saved game R9, last setup R10);
  bad ARGB, alpha not FF, repeated color rejected. Rename, reset (no row lost: same counts of sessions, legs, darts),
  merge in one transaction (a failure in the middle leaves both profiles unchanged), merge refused on a shared match,
  delete with match removal and anonymous reuse, anonymous excluded from Players list, suggestions and filter.
- UI (Compose JVM tests): swatch row opens, applies, closes; taken color absent (the lightened "refused color" test,
  Q3); Players actions and confirmations, refusals, errors; home entries.
- Captures (Roborazzi, Pixel 7 only, portrait, landscape, 200% font): setup with the palette, swatch row open with
  generated colors (seeded), swatch row of a 4-player setup; Players list, rename clash, merge confirmation, delete
  confirmation, empty state; home.

## How Kosmo verifies on the phone

1. Fresh install. X01 setup with 4 players: blue, orange, teal, lilac in order.
2. Tap a player's color: a row of at least 4 swatches, the colors of the others absent; pick a generated one; the
   text on it (score, tiles, History) stays readable.
3. Shuffle, play, rematch: colors follow the players. Next setup restores them.
4. Home, Players: profiles with match count and last match date.
5. Rename "Kevin" to "kevin" (accepted), then to an existing name (refused, merge offered).
6. Merge two profiles that never played together (confirmation, then one profile left, History shows the target
   name); try two that played together (refused).
7. Reset a profile: confirmation, History unchanged.
8. Delete a profile: confirmation with the count; History shows "Joueur supprimé" in shared matches, his solo matches
   are gone.
9. With a game in progress, try to rename one of its players: refused with a message.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Rules and design system text | `51-player-colors-players-screen/T1-rules-docs.md` | - | haiku | Text copied from this plan, no choice |
| T2 | Engine color rules | `51-player-colors-players-screen/T2-engine-colors.md` | T1 | sonnet | Rules PC10 to PC15 specified with thresholds and tests |
| T3 | `PlayerColor` replaces `ColorSlot` everywhere | `51-player-colors-players-screen/T3-player-color-swap.md` | T2 | opus | Case: refactoring across modules |
| T4 | Saved game and last setup format v2 (ARGB) | `51-player-colors-players-screen/T4-save-format-v2.md` | T3 | opus | Case: changed stored format |
| T5 | Setup swatch row | `51-player-colors-players-screen/T5-setup-swatches.md` | T4 | sonnet | Screen and state holder from specified rules |
| T6 | Engine profile rules | `51-player-colors-players-screen/T6-engine-profile-rules.md` | T1 | sonnet | Rules PM3 to PM9 specified with tests |
| T7 | Players data: list, rename, reset, merge | `51-player-colors-players-screen/T7-players-data.md` | T6 | sonnet | DAO and repository over the existing schema |
| T8 | Delete and anonymous profile | `51-player-colors-players-screen/T8-delete-anonymous.md` | T7 | sonnet | Specified rules PM6, PM7, no schema change |
| T9 | Players state holder and wiring | `51-player-colors-players-screen/T9-players-state.md` | T8, T4 | sonnet | State holder and effects with JVM tests |
| T10 | Players screen and home entries | `51-player-colors-players-screen/T10-players-screen.md` | T9 | sonnet | Screen from this plan, Compose tests and captures |

Two `opus` tasks (T3, T4).

## Decisions

- Q1 (Kosmo): option A, one single anonymous profile for the whole app, labelled "Joueur supprimé" (English
  resource "Deleted player"). PM7.
- Q2 (Kosmo): yes, matches whose players are all deleted are deleted, with the count in the confirmation;
  irreversible. PM6, AR8.
- Q3 (Kosmo): option A, a row of swatches (free palette colors completed with generated ones to reach at least 4), no
  free hue wheel. "On allège les tests": no free-picker refusal tests. Generated colors drawn at random, the random
  source injectable so engine tests are deterministic. PC14, PC15.
- Q4 (Kosmo): player color at least 3:1 on background and tiles; text on it at least 4.5:1, white or dark; OKLab
  distance with a named threshold; a name written in the player color keeps the 3:1 threshold. PC11 to PC13.
- Q5 (Kosmo): option A, rename, merge and delete refused while a game in progress contains that player, with a short
  message; the last setup is updated. PM8, PM9.
- Q6 (Kosmo): case-only rename allowed; on a name clash, merge proposed with the renamed profile as source and the
  existing one as target. PM3.
- Q7 (Kosmo): the reset date is not displayed; it is still stored and updated by reset and merge. PM2, PF1.
- Q8 (Kosmo): Players entry with the same form as History, with an icon; History gets an icon too; content
  descriptions and 48dp targets. PM1.
- D9: format version 2 for the saved game and the last setup, version 1 handled by R30 (no release yet, clean slate).
- D10: the anonymous profile is a reserved name key `""`, no schema change (Approach 5).
- D11: on merge, last-used date = latest, creation date = earliest (not set by the issue; neither is shown).
- D12: PM8 covers both merge source and target, the strict reading of "that player".
- D13: the icons of the home buttons are decorative (`contentDescription = null`): the button text is already its
  accessible name, a description would make TalkBack read it twice. This is how Q8's "content descriptions" is met.
- D14: Players list order is most recently used first, as the setup suggestions (PF3).
- D15 (review S2): on load, a stored color is checked for format, opacity and uniqueness only, not PC11 to PC13:
  thresholds may change later, and a resume must never fail for a color that was valid when chosen.
- D16 (review S1): Room colors are never passed to the throwing `PlayerColor` constructor; the History mapping
  forces alpha `FF` (`PlayerColor.fromStored(argb)`), tested with a stored alpha `00` row.
- D17 (review T1): PC12 applies to every color given or offered (PC2, PC14, PC15) through one function,
  `PlayerColorRules.isAcceptable(color, others)`.
- D18 (review T4): T3's "no visual change" is checked by a byte comparison (`cmp`) of the captures recorded on
  `main` and on the branch, run by hand: the build records Roborazzi images but has no compare task, and adding one
  is out of scope; the colors are proved by
  `PlayerPaletteTest` (same ARGB and text colors as the former tokens).
- D19 (review U7): the home screen already scrolls (`verticalScroll` in `HomeScreen.kt`); the landscape capture shows the Players button
  reachable.
- D20 (design review minors, details in the task files): A2 theme and engine palette equality test (T2); A3/S3
  child-first deletion under enforced foreign keys and rollback tests (T8); A4 `Random` injected through the
  reducer (T5); T2/T3 swatch stability and generated color survival tests (T5); T5 collapsed card after reload and
  in-transaction erased count (T8, T9); U1/U2 wrapping swatches, ring plus check in the PC13 text color (T5); U3
  "Color %1$d of %2$d" (T5); U4/U6 2 x 2 actions with Delete apart, scroll into view above the keyboard (T10); U5
  plurals with a zero form and a PM8 message naming the player (T9, T10); U8 every seed reaches `MIN_CHOICES` (T2).

## Open questions

None.
