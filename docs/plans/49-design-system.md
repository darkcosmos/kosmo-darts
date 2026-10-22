# Plan: design system and redesign of the existing screens (issue #49)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/49

Apply the "Retransmission TV / Plein écran" design system (`docs/design/design-system.md`, authoritative for every
value; `docs/design/mockups/` give the intent) to the Compose theme and redesign every existing screen with it: game
(portrait and landscape, normal, BUST, checkout), home, X01 setup, between legs ("next leg"), match end, settings,
and the remaining dialogs. The current player's remaining score must be readable from the throwing line (about
2.5 m), in a fixed zone in the player's color. Each player owns a color slot (1 to 4) assigned by the engine, kept
through shuffle, the whole match, a resume, a rematch and the next setup.

## Scope and out of scope

In scope:
- Theme: color tokens (section 2), typography with the embedded Saira variable font (section 3), shapes, spacing,
  touch targets and states (section 4), the only animation (section 7).
- Engine: player color slots (new rules file `docs/rules/players.md`, prefix `PC`), match position (set number and
  leg number) for the headers.
- Persistence: format version 3 for both files (color slots), new ADR 0006.
- Screens and components of sections 5 and 6, restyled dialogs (abandon, new game, no voice, restore failed).
- Rules amended: `match.md` (M6, M13 to M18), `visit-total.md` (VT9, VT10, VT13), `checkout-suggestions.md`
  (CS5, CS7, CS8), `persistence.md` (R3, R6 wording, new R17 to R19).
- Strings in `values/` and `values-fr/`; Roborazzi captures; JVM and Robolectric tests.

Out of scope: the Cricket mode card of mockup 06 (issue: only the X01 card), any change to scoring, bust, checkout,
undo, ranking or announcement rules, new settings, statistics or history, any dependency change.

## Approach

### 1. Engine: player color slots (`engine/player`, pure Kotlin)

- `ColorSlot`: enum `SLOT_1` to `SLOT_4`, in palette order (blue, orange, teal, lilac on the UI side). The engine
  knows nothing about colors, only slots.
- `Roster`: immutable setup-time list of `RosterEntry(name: String, slot: ColorSlot)` (1 to 4 entries):
  `Roster.initial(count)` (slots 1..count), `withPlayerAdded()` (first free slot in slot order, PC2),
  `withPlayerRemoved(index)` (any position, frees its slot, PC3), `withName(index, name)`, `shuffled(random)`
  (each entry keeps its slot, PC4), `canAdd`, `canRemove`. Name validation stays in `X01Config` (C4, C5).
- `X01MatchConfig(config, format, colorSlots: List<ColorSlot> = ColorSlot.byPosition(config.players.size))`:
  one slot per player, aligned with `config.players`, all distinct, else `InvalidColorSlotsException` (PC5). The
  slots never change during a match (PC6); `X01Match` exposes `colorSlots`.
- Rematch (`rematchSetup`, M17): the first player moves to the end together with his slot (PC7).
- Alternatives considered for where the slots live:
  - In `X01Config` (with the names): rejected, a leg (`X01Game`, replay, cues) has no use for colors and every
    `X01Config` construction site (tests, persistence, audio) would change.
  - A `Player(name, slot)` type replacing the name list everywhere: rejected for this mission, large ripple through
    the engine, audio and persistence for no rule gain.
  - Chosen: a parallel list in `X01MatchConfig`, validated at construction, plus `Roster` for the setup rules.

### 2. Engine: match position

`MatchScore` gains `currentSetNumber` (sets completed + 1) and `currentLegNumber` (legs played in the current set
+ 1), and the same numbers for the last confirmed leg (`lastLegSetNumber`, `lastLegNumber`) for the between-legs
title. New rule M19. Tested in `MatchScoreTest` with sets, legs only and a single game.

### 3. App: theme (`ui/theme`)

- `KosmoColors` (every token of section 2, including `player1..4` and `onPlayer1..4`), provided by a
  `CompositionLocal`, plus `PlayerPalette.colorOf(slot)` / `onColorOf(slot)`. The Material 3 `darkColorScheme` is
  mapped from the tokens (background, surface, primary = accent, error = danger) so restyled `AlertDialog`s and
  text fields follow. No dynamic color. `values/colors.xml` window background becomes `#0A0F1E`.
- Font: `docs/design/font/Saira[wdth,wght].ttf` copied unchanged to `app/src/main/res/font/saira_variable.ttf`;
  `OFL.txt` copied to `app/src/main/assets/licenses/saira-OFL.txt` (shipped in the APK, never shown, owner Q7).
  One `Font(R.font.saira_variable, weight, variationSettings = FontVariation.Settings(weight(w), width(75f)))` per
  weight used (700, 800). minSdk 30 is above the required API 26: no change.
- `KosmoTypography`: the styles of section 3, all display styles with `fontFeatureSettings = "tnum"`, upper-case
  labels with 0.04 to 0.12 em letter spacing; body text uses the system font. "Fixed" styles (scoreHuge,
  scoreHugeLandscape, keypad, dart, visit, tileScore) use a `fixedSp(dp)` helper (`dp / fontScale` in sp) so they
  ignore the font scale; the others are plain `sp`.
- `KosmoShapes` (4 and 8, no pill) and `Spacing` (4 to 16, screen margins 12/16).
- States: disabled = alpha 0.40, locked keypad = alpha 0.35, focus and selection = 2 dp `accent` border.
- Touch targets: at least 48 dp everywhere. The Stepper − and + buttons are drawn 44 dp (section 5) inside a 48 dp
  touch area (`minimumInteractiveComponentSize`); keypad keys 54 dp high, action buttons 58 to 60 dp.
- Motion (section 7): the zone background fades in 200 ms on a player change. `zoneColorAnimationSpec(enabled)`
  returns `tween(200)` or `snap()`; `enabled` comes from `ValueAnimator.areAnimatorsEnabled()` read once per
  composition through a small `AnimationSettings` provider (fakeable). Captures run with animations disabled.

### 4. App: state mapping (pure JVM, outside composables)

- `GameScreenState`: `zoneMode` (`NORMAL`, `BUST`, `CHECKOUT`, derived from the pending turn, replaces
  `isCheckoutDialogShown` in rendering), `tileOrder` (other players in play order starting from the next one,
  M6 amended), `colorSlotOf(index)`, `header` (mode + finish; set and leg position per M14/M19), `checkoutSummary`
  (points = remaining at turn start, darts of the turn), `confirmLabel` (LEG/SET/MATCH, M15), `routeDarts` (the
  checkout suggestion, null when none, CS8 amended). The `Multiplier` enum already guarantees at most one of
  Double/Triple active (owner extra); kept as is.
- `SetupState`: `names: List<String>` becomes `roster: Roster`; `withPlayerRemoved(index)` replaces
  `withLastPlayerRemoved()`; name errors are re-indexed after a removal (errors are recomputed by `edited`).
  `prefilled(lastSetup)` restores names, order and slots (PC8).
- `ResumeSummary`: adds slots; `isCheckoutToConfirm` is removed, the card always says "À <joueur> de lancer"
  (owner Q11).
- `BetweenLegsState` and `EndRow`: add slots, title texts per M19, columns per M14 (see screens), checkout line of
  the winning visit for the between-legs zone.
- Text formatting (headers "SET 2 · MANCHE 3", "S1 · M1", averages "62,40") in small pure functions with JVM tests.

### 5. App: components (`ui/components`) and screens

Components of section 5: `MatchHeader`, `CurrentPlayerZone`, `CheckoutRouteBar`, `DartSlots`, `VisitBox`,
`PlayerTile`, `Keypad` (restyled in place), `ActionBar`, `Scoreboard`, `ModeCard`, `ResumeCard`,
`SegmentedChoice`, `Stepper`, `PlayerNameRow`, `SettingsSwitch`, `KosmoDialog` (restyled `AlertDialog` wrapper).
Composables only render state and forward events. Screens are described below under "Screens and states".

### 6. Persistence (format version 3, ADR 0006)

- New body key `SaveKey.COLORS` (`colors`), one line after the `player` lines in both files, values `1` to `4`
  separated by spaces, one per player, all distinct; anything else is `OUT_OF_RANGE` / `MALFORMED` and follows R9
  (game) or R10 (last setup).
- Both codecs write version 3 and read versions 1, 2 and 3; versions 1 and 2 get slots by position (R18).
- An older app finding a version 3 file rejects it and deletes it (R9/R10, as ADR 0005 S3). Documented in ADR 0006.
- `SaveFuzzTest`, `GameSaveDecodeInvalidTest`, fixtures updated; size limits unchanged (one short line).

### 7. Rules documents

- New `docs/rules/players.md`: PC1 to PC9 (below).
- `match.md`: M6 (tiles in play order from the next player; zone fixed), M13 (setup: "Sets · first to" then
  "Legs · first to", label "Legs" whatever the sets; player list with a remove cross and "+ Add a player"), M14
  (notation "S n · M n" / "S n · L n", sets part only when Sets > 1, nothing in a single game), M15 (checkout in the
  zone, buttons "VALIDER LA MANCHE / LE SET / LA PARTIE", undo = "ANNULER", system back opens the abandon
  dialog), M16 (between-legs layout), M17 (end screen columns, darts thrown kept, "2 SETS À 0" removed), M18 (home card), new M19 (match position).
- `visit-total.md`: VT9/VT10 (box styles: filled for the current visit, outlined on tiles), VT13 (during a bust the
  zone shows the BUST banner and the box shows `0`, section 6.2).
- `checkout-suggestions.md`: CS5 (hidden in BUST and CHECKOUT modes), CS7 (label "SORTIE"/"CHECKOUT" chip, darts
  without separator dots), CS8 (bar absent when no route; the zone's score area absorbs the height so the zone does
  not jump: the bar's height stays reserved).
- `persistence.md`: R3 and R6 wording ("Valider la partie"), new R17 (slots saved with the game and the last setup),
  R18 (older formats read with slots by position), R19 (a resume shows a pending checkout in the zone).

## Rules (new `docs/rules/players.md`, prefix PC)

- PC1. A player has exactly one color slot from 1 to 4; two players of a setup or a match never share a slot.
- PC2. Adding a player in the setup gives him the first free slot in slot order.
- PC3. Removing a player (any position) frees his slot; the other players keep theirs.
- PC4. Shuffle changes the order only; each player keeps his slot.
- PC5. A match configuration with slots of the wrong count, or repeated slots, is rejected.
- PC6. Slots never change during a match (every leg and set).
- PC7. Rematch keeps each player's slot (the first player moves to the end with his slot).
- PC8. The setup prefilled from the last setup restores each player's slot.
- PC9. The UI shows slot n with token `playerN`; the color never carries information alone (name always shown).

## Files and types added or changed

- `game-engine/.../engine/player/ColorSlot.kt`, `Roster.kt` (new); `x01/X01MatchConfig.kt`, `x01/X01Match.kt`,
  `match/MatchScore.kt` (changed).
- `app/.../ui/theme/` `Theme.kt` (changed), `KosmoColors.kt`, `KosmoTypography.kt`, `KosmoShapes.kt`,
  `PlayerPalette.kt`, `FixedSp.kt`, `AnimationSettings.kt` (new).
- `app/.../ui/components/` (new, one file per component of section 5).
- `app/.../ui/game/` `GameScreen.kt`, `GameScreenState.kt`, `Keypad.kt`, `ScoreLine.kt`, `MatchIndicators.kt`,
  `CheckoutSuggestionLine.kt`, `VisitBoxText.kt`, `NoVoiceDialog.kt` (rewritten or removed when absorbed by a
  component; the checkout dialog is removed).
- `app/.../ui/home/`, `ui/setup/`, `ui/between/`, `ui/end/`, `ui/settings/` screens and state holders.
- `app/.../ui/app/SetupTransitions.kt` (rematch slots), `AppReducer.kt` (remove-player event, Back in checkout
  mode), `AppState.kt` (events).
- `app/.../persistence/SaveBody.kt`, `GameSaveCodec.kt`, `LastSetupCodec.kt`, `SaveFormat.kt` (version 3).
- `app/src/main/res/font/saira_variable.ttf`, `app/src/main/assets/licenses/saira-OFL.txt` (new);
  `res/values/colors.xml`, `strings.xml`, `values-fr/strings.xml` (changed).
- `docs/rules/players.md`, `docs/adr/0006-save-format-v3-colors.md` (new); rules files listed above (changed).
- Tests: see the test plan.

## Data read, stored or exposed

- Stored: one more line per file (the color slots); nothing personal beyond the names already stored. Files stay in
  app-private storage with the existing backup rules. No new permission, no `INTERNET`, the font is embedded and
  never downloaded.
- Read: `ValueAnimator.areAnimatorsEnabled()` (system animation setting), the font scale (already used).
- Exposed: nothing new.

## Screens and states

All texts below are the French version; English is in the strings table.

- **Game, portrait** (mockups 01, 02): `MatchHeader` ("501 · SORTIE DOUBLE" left; "SET 2 · MANCHE 3", "MANCHE 3"
  with Sets = 1, nothing in a single game, right). `CurrentPlayerZone` takes the remaining height: name (ellipsis),
  "MOY 62,40" (hidden before the first dart, as today), chips "S n" / "M n" (M14), `scoreHuge` 220 (204 at 4
  players), `CheckoutRouteBar`, `DartSlots` (next dart underlined in accent) and the filled `VisitBox`. Tiles: 2 per
  line up to 3 players, 3 at 4 players, play order from the next player, no "▸ SUIVANT" marker (owner Q10). Keypad 5
  columns; Double/Triple: inactive = `surfaceHigh` with `onBackground` text (like the other keys), active =
  `accent` background with `onAccent` text and TalkBack "selected"; at most one active. `ActionBar`: "ANNULER" /
  "JOUEUR SUIVANT" (enabled only while a turn waits, as today).
- **Game, landscape** (mockup 05): left column 480 dp (header, zone with the route bar right of the score, tiles),
  right column keypad and action bar. Tiles: compact single-line tiles for 2 and 3 players; 3 compact tiles on one
  line at 4 players (owner Q9), name ellipsis first, score and box never truncated.
- **BUST** (mockup 03): zone in `danger`, a square in the player's color bordered white before the name, white
  "BUST" banner in place of the route bar, score back to the turn start, box `0`, keypad locked at 35 %. Both
  "ANNULER" and "JOUEUR SUIVANT" stay enabled (owner Q3).
- **Checkout** (mockup 04): zone keeps the player's color, score replaced by the accent "CHECKOUT" banner and
  "141 EN 3 FLÉCHETTES", final dart in accent, keypad locked; `ActionBar` "ANNULER" (undo the winning dart) /
  "VALIDER LA MANCHE", "VALIDER LE SET" or "VALIDER LA PARTIE" (owner Q5; a single game says "VALIDER LA PARTIE").
  System back opens "Abandonner la partie ?" (owner O1); dismissing it returns to the pending checkout.
- **Home** (mockup 06): "KOSMO DARTS" title ("DARTS" in accent), settings icon button (48 dp, TalkBack
  "Réglages"); `ResumeCard` when a game is saved: "PARTIE EN COURS", format line, mini-tiles in play order with
  color strip, score and "S n · M n" (M14), no ▸ and no accent outline (owner Q10), button "REPRENDRE · À <joueur>
  DE LANCER" always (owner Q11); section "NOUVELLE PARTIE" with the X01 `ModeCard` only. Tapping it keeps R3 (the
  new-game dialog when a game is saved).
- **Setup** (mockup 07): back arrow, "NOUVELLE PARTIE · X01"; "JEU": `SegmentedChoice` 501/301 and sortie
  double/simple; "MATCH": steppers "Sets · premier à" and "Manches · premier à"; "JOUEURS · ORDRE DE JEU" with
  "MÉLANGER"; `PlayerNameRow`s (color strip, order number, name field with placeholder "Nom du joueur n", error
  under the field, remove cross 48 dp with TalkBack "Retirer <nom ou Joueur n>", hidden with a single player); "+
  AJOUTER UN JOUEUR" (dashed outline, hidden at 4 players); "COMMENCER" at the bottom.
- **Between legs** (mockup 08): format line; zone in the winner's color with chip "SET 2 · MANCHE 3 GAGNÉE"
  ("MANCHE 3 GAGNÉE" with Sets = 1, "SET 2 GAGNÉ" on a set win), winner name, "CHECKOUT 141 · T20 T19 D12";
  `Scoreboard` columns SETS (Sets > 1), MAN., MOY MATCH, the leg winner's figures in accent; info bar "SET 2 ·
  MANCHE 4 ▌<joueur> COMMENCE"; button "MANCHE SUIVANTE". Back opens the abandon dialog (M16, unchanged).
- **End** (mockup 09): format line; zone in the winner's color, chip "VAINQUEUR DU MATCH", winner name; no "2 SETS À
  0" line (owner Q8); `Scoreboard` with rank cell (rank 1 on `accent`), color square, name, SETS column (MANCHES with
  Sets = 1, no score column in a single game) and MOY MATCH, then the darts thrown over the match (owner O2, header "FLÉCH." / "DARTS"); "REVANCHE"
  (primary) and "NOUVELLE PARTIE" (secondary).
- **Settings** (mockup 10): back arrow, "RÉGLAGES"; "SON ET VOIX": Voix (with its status line and the existing
  "Installer une voix" button when needed), Annoncer chaque fléchette, Sons, with `SettingsSwitch`; new section
  "CRÉDITS": the existing sound credit line and "Police Saira, licence SIL Open Font License 1.1" (no link, owner Q7).
- **Dialogs** (owner Q4): abandon, new game, no voice, restore failed keep their content and buttons, restyled
  with `KosmoDialog` (surface `surface`, radius 8, primary button accent, secondary `surfaceHigh`).
- Every screen at font scale 2.0: names ellipsized, labels wrap, fixed styles unchanged, scrolling where needed
  (setup, settings, end, between legs).

### Strings (new or changed, both `values/` and `values-fr/`)

| Key (indicative) | English | French |
|---|---|---|
| `match_position_set_leg` | SET %1$d · LEG %2$d | SET %1$d · MANCHE %2$d |
| `match_position_leg` | LEG %1$d | MANCHE %1$d |
| `chip_sets` / `chip_legs` | S%1$d / L%1$d | S%1$d / M%1$d |
| `tile_score_sets_legs` | S%1$d · L%2$d | S%1$d · M%2$d |
| `game_average` | AVG %1$s | MOY %1$s |
| `checkout_route_label` | CHECKOUT | SORTIE |
| `checkout_banner` / `checkout_darts` | CHECKOUT / %1$d IN %2$d DARTS | CHECKOUT / %1$d EN %2$d FLÉCHETTES |
| `checkout_confirm_leg/set/match` | CONFIRM LEG / SET / MATCH | VALIDER LA MANCHE / LE SET / LA PARTIE |
| `key_undo`, `key_next_player` | UNDO, NEXT PLAYER | ANNULER, JOUEUR SUIVANT |
| `home_resume` | RESUME · %1$s TO THROW | REPRENDRE · À %1$s DE LANCER |
| `home_new_game_section`, `mode_x01_description` | NEW GAME, 501 or 301, double or single out | NOUVELLE PARTIE, 501 ou 301, sortie double ou simple |
| `setup_sets_first_to`, `setup_legs_first_to` | Sets · first to, Legs · first to | Sets · premier à, Manches · premier à |
| `setup_player_placeholder`, `setup_remove_player` | Player %1$d name, Remove %1$s | Nom du joueur %1$d, Retirer %1$s |
| `between_leg_won` / `between_set_won` | SET %1$d · LEG %2$d WON / SET %1$d WON | SET %1$d · MANCHE %2$d GAGNÉE / SET %1$d GAGNÉ |
| `between_starts` | %1$s STARTS | %1$s COMMENCE |
| `end_match_winner` | MATCH WINNER | VAINQUEUR DU MATCH |
| `settings_credits`, `settings_font_credit` | CREDITS, Saira font, SIL Open Font License 1.1 | CRÉDITS, Police Saira, licence SIL Open Font License 1.1 |

Removed: `checkout_title`, `checkout_finish*`, `home_saved_game_checkout`, `home_welcome`, `setup_remove_symbol`,
`setup_remove_player` (old meaning), `match_count_overflow` and the dot indicators. `FrenchStringsTest` keeps both
files in sync.

## Test plan

### `:game-engine` (JUnit, JVM; each test cites its rule)

- `RosterTest`: initial roster slots 1..n (PC1); add gives the first free slot, including after removing slot 2 of
  4 (PC2); remove at first, middle, last position frees the slot and keeps the others (PC3); add up to 4 then
  `canAdd` false; remove down to 1 then `canRemove` false; shuffle with a seeded `Random` keeps every name with its
  slot (PC4).
- `X01MatchConfigTest`: default slots by position; wrong count and repeated slots rejected (PC5).
- `X01MatchTest` additions: slots unchanged after legs and sets are confirmed (PC6).
- `MatchScoreTest` additions: set and leg numbers at start, mid-set, after a set change, legs only, single game
  (M19).
- Rematch slots (PC7) tested in `:app` where `rematchSetup` lives (see below).

### `:app` pure JVM

- `GameSaveCodecTest`, `LastSetupCodecTest`: v3 round trip with non-positional slots (R17, resume of PC6/PC8);
  v1 and v2 files read with slots by position (R18); invalid colors lines (count, value 0 or 5, duplicate, missing
  line, extra line) rejected (R9/R10); `SaveFuzzTest` over v3.
- `SetupStateTest`: remove any player re-indexes errors and frees the slot; prefill restores slots (PC8).
- `AppReducerTest` / `AppReducerResumeTest`: rematch keeps slots with rotation (PC7); resume restores slots and a
  pending checkout shows the CHECKOUT zone (R19); Back in checkout mode opens the abandon dialog, and dismissing it
  keeps the checkout pending (M15, owner O1).
- `EndRowsTest`: the darts-thrown column is still mapped for every format (M17, owner O2).
- `GameScreenStateTest` additions: `zoneMode` NORMAL/BUST/CHECKOUT; `tileOrder` from the next player for 1 to 4
  players and after each "Next player" (M6); `confirmLabel` LEG/SET/MATCH (M15); header position per format
  (M14/M19); checkout summary points and dart count; at most one multiplier active (toggle Double then Triple, then
  Triple again).
- `ResumeSummaryTest`: always "to throw" (M18, owner Q11). `BetweenLegsStateTest`, `EndRowsTest`: columns per
  format (M14, M17), titles (M19).
- Formatters: header, chips, averages (decimal comma in French).
- `zoneColorAnimationSpec(false)` is a snap, `(true)` a 200 ms tween.

### Robolectric (Compose UI)

Existing UI tests are updated to the new behavior where the rules changed (the checkout dialog becomes the zone,
remove-last becomes remove-any, keys move to the action bar), keeping their checks: none is removed or weakened.
New: BUST keeps both action buttons enabled and the keypad disabled; CHECKOUT validates and undoes from the action
bar; setup remove cross and "+ Add a player" visibility at 1 and 4 players; TalkBack labels of the settings icon,
remove cross, steppers and Double/Triple "selected" state; minimum 48 dp touch targets (stepper, cross, settings
icon); `scoreHuge` text height identical at font scale 1.0 and 2.0 (fixed size).

**Saira under Robolectric (risk, first build step).** Before the screens, a spike test renders "501" with the Saira
style at width 75 and at width 100 in native graphics mode and asserts the first is narrower. If Robolectric
ignores the variation axes, the captures still use Saira at its default width; this goes to "Known limitations" and
the width is checked on the phone by Kosmo. No workaround such as a static font instance is added without asking.

### Roborazzi captures (record only, Pixel 7, as the existing tests; animations disabled)

Required by the issue: game portrait 4 players, 4 players with long names, landscape, BUST, checkout, home,
setup, next leg, match end, settings. Also:
- game portrait 3 players (mockup 01) and landscape 4 players (owner Q9); checkout with "VALIDER LA PARTIE";
  header for legs only and single game;
- home with and without a saved game; setup with 4 players (cross and no "add"), and with a name error;
- next leg in sets and in legs only; end in sets, legs only and single game;
- the four dialogs;
- font scale 2.0 (section 8): game 4 long names, setup, home, end, settings;
- French for every screen, English for game portrait and setup.
Obsolete captures (checkout dialog, dot indicators) are replaced in the same screenshot classes.

### Local checks

`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`, then `kosmo-detekt`.

## How Kosmo verifies on the phone (Pixel 7)

The PR description will hold the full scenario. Outline:
1. Fresh install. Home: title, settings icon, X01 card only. Settings: switches, "CRÉDITS" with the sound and Saira
   lines.
2. Setup: 2 players by default (blue, orange). Add a 3rd (teal), a 4th (lilac); remove the 2nd: orange is freed;
   add one: he gets orange. Shuffle: each name keeps its color. Sets 2, Manches 2, start.
3. Game from 2.5 m: the score is readable; the zone takes the player's color with a short fade; tiles show the
   others from the next player on. Double then Triple: only one is highlighted.
4. Bust: red zone, white "BUST", "ANNULER" still works.
5. Checkout: "CHECKOUT", "x EN n FLÉCHETTES", "VALIDER LA MANCHE"; "ANNULER" reopens the turn; validate: next-leg
   screen, then "MANCHE SUIVANTE": colors unchanged.
6. Kill the app mid-leg and on a pending checkout: home card "REPRENDRE · À <joueur> DE LANCER", resume shows the
   same colors and the checkout zone.
7. End: columns, no "2 SETS À 0"; "REVANCHE": same colors; "NOUVELLE PARTIE": setup prefilled with the same colors.
8. Landscape with 4 players; largest font size: names cut with "…", score and keys unchanged in size.
9. Update over the previous version with a game in progress: it resumes, colors by position.

## Decisions

Owner answers to the intake questions (issue #49 comments, relayed by main):

| Id | Decision | Rules |
|---|---|---|
| Q1 | Keep M14: sets mentioned only when Sets > 1 ("MANCHE n", "M n" for legs only); nothing in a single game. | M14, M19 |
| Q2 | A cross on each player row removes any player. | M13, PC3 |
| Q3 | Current behavior: "ANNULER" stays available during a BUST (section 6.2 text overridden). | VT13 |
| Q4 | The other pop-ups (abandon, new game, no voice, restore failed) stay, restyled. | R3, R9 |
| Q5 | Checkout buttons: "VALIDER LA MANCHE / LE SET / LA PARTIE". | M15 |
| Q6 | Players keep their colors, rematch and next setup prefill included. | PC6 to PC8, R17 |
| Q7 | "CRÉDITS" block in settings: the existing sound credit plus "Police Saira, licence SIL Open Font License 1.1" (EN equivalent); no link; the license text is embedded in the APK only. | - |
| Q8 | No "2 SETS À 0" line on the end screen; column display kept; MANCHES column instead of SETS with legs only. | M17 |
| Q9 | Landscape with 4 players: 3 compact tiles on one line. | M6 |
| Q10 | No "▸ SUIVANT" marker on tiles and no ▸/accent outline on the home card (mockup leftovers, UX reviewer to confirm); the current player's zone is the key information. | M6, M18 |
| Q11 | Home card: always the single message "À <joueur> de lancer"; the "Checkout à confirmer" line is dropped. | M18 |
| Q12 | English notation "S1 · L1", "SET 2 · LEG 3". | M14 |
| Q13 | One single PR. | - |
| X1 | (owner extra) Double and Triple: at most one active at a time; active = accent background, inactive = like the other keys. | - |
| D1 | (plan) Color slots live in `X01MatchConfig`, with `Roster` for the setup rules. | PC1 to PC5 |
| D2 | (plan) Save format v3 for both files, v1/v2 read with slots by position, ADR 0006. | R17, R18 |
| D3 | (plan) Stepper − and + drawn 44 dp in a 48 dp touch area. | - |
| D4 | (plan) Fade 200 ms, snapped when Android animations are off. | - |
| D5 | (plan) Setup steppers in the mockup order: sets first, then legs, label "Manches · premier à" in every format. | M13 |
| O1 | (owner, after the plan) System back while a checkout waits opens "Abandonner la partie ?", like anywhere else in a game (today it does nothing because of the modal dialog). | M15, R6 |
| O2 | (owner, after the plan) The end screen keeps the darts-thrown column (M17, owner Q2 of #39), restyled, after MOY MATCH. | M17 |

## Open questions

None.
