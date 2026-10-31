# Plan addendum: Cricket game screen rework (issue #40, PR #60)

Addendum to `docs/plans/40-cricket-mode.md`, written after Kosmo's review of PR #60 on head `2f1fc6a`. It only
changes the Cricket game screen (CR22 to CR24, the zone of CR25 stays). Everything else in the main plan (engine, save
format, between-legs and end screens, setup, home) is unchanged and stays in force.

## Goal

Answer Kosmo's three comments on PR #60 (issue #40):
1. the player zone is decluttered: the player's total (in white) is removed from it;
2. the total in the Cricket board becomes much more visible;
3. the separate keypad, with its Double and Triple modifiers, is replaced by three buttons per target (single,
   double, triple) aligned with the target's row of the board, in portrait and in landscape. The board's first column
   (the target labels) is removed, since the buttons carry the target.

The dartsmind screenshot given by Kosmo could not be viewed: its download is redirected to
`github-production-user-asset-*.s3.amazonaws.com`, which is outside the sandbox allowlist (connection refused). Nothing
in this plan is taken from it; the layout below is built only from Kosmo's words and answers.

## Scope and out of scope

In scope:
- the Cricket game screen in portrait and landscape: zone, board, target buttons, Miss, action bar;
- the layout policy and its JVM tests, the Robolectric fit and TalkBack tests, the game captures;
- the rules text CR22, CR23, CR24 in `docs/rules/cricket.md`, `docs/design/design-system.md` (components table, 6.5),
  the strings in `values/` and `values-fr/`, the phone checks.

Out of scope:
- any game rule, the engine, the save format: a dart is still a `Dart(segment, multiplier)`, and the multiplier was
  never saved (it is UI state only);
- the X01 screens and keypad: the X01 captures must stay byte-identical to the `e7372f8` baseline as amended by T10
  (the 15 `home_*` and `match_home_summary_portrait_en` captures);
- the Cricket between-legs, end, setup and home screens and their captures;
- the pending minors of earlier missions.

## Approach

### 1. Input: three buttons per target, inside the board

Each target row starts with its own buttons, in the place of the removed label column:
- rows 20 to 15: three buttons, single, double, triple. Labels: "20", "D20", "T20" (the single button also reads as
  the row label). TalkBack: « 20 », « Double 20 », « Triple 20 » / "20", "Double 20", "Triple 20";
- Bull row (only with 25-Bull on, CR1): two buttons sharing the width of the three: "25" (1 mark) and "BULL"
  (2 marks). There is no triple bull on a dartboard, so no third button;
- the footer row of the board starts with the "RATÉ" / "MISS" button, over the same width;
- "ANNULER" (undo the last dart) stays in the action bar with "JOUEUR SUIVANT" (or the CR25 confirm label).

One tap records one dart, there is no modifier state any more: `CricketGameScreenState.multiplier`,
`toggleMultiplier`, the Cricket branch of `AppEvent.MultiplierTapped` and `CricketKeypad` are removed. A new
`AppEvent.CricketDartTapped(dart: Dart)` goes to `CricketGameScreenState.throwDart(dart)`, which records it only
while the input is enabled (not while a turn waits) and only if the dart is offered by the current configuration
(no 25 or Bull with 25-Bull off). The X01 keypad and its events are untouched.

Buttons of a dead row (CR15) stay enabled: a dart in a dead sector is a real dart and plays the closed-sector sound
(AN30). They take the muted style of the row, and the strike-through line crosses only the player cells, not the
buttons, so that they do not look disabled. Every button is locked (35 %, as today) while a turn waits.

Alternatives considered:
- keep a separate keypad below the board (rejected by Kosmo);
- one button per target and a long press for double or triple (hidden gesture, not discoverable, rejected);
- the target buttons in a central column between players (classic chalkboard). Rejected: Kosmo asked to remove the
  *first* column, and with 3 or 4 players the columns would not be split evenly.

### 2. Totals: a pinned footer row, large

The total is removed from the zone (Kosmo comment 1). In the board, the points footer row:
- is pinned at the bottom of the board (like the header is pinned at the top), so it never scrolls away;
- is 48 dp tall (the height of the button rows, since it holds "RATÉ");
- shows each total in `tileScore` (the large display style the zone used), in `onBackground`, centered in the
  player's column, inside the current player's `accent` border;
- picks its text size from the space: `CricketLayoutPolicy.totalTextSize(digits, columnWidth, labelScale)` returns
  the largest of the sizes 28, 24, 20, 16, 14 sp whose line fits the column (digits x 0.6 em + 4 dp); a total is never
  cut (CR22 rule kept);
- in a solo game, shows "4/7" ("4/6" without the Bull) in the same place and style, since the zone no longer shows it
  (see question Q2).

Alternatives for the UX review: (b) the total under the name in the pinned column header; (c) both. (a), the
footer, is proposed because it keeps the column header small and puts the total next to "RATÉ", where the eye
already is.

### 3. Zone

`CricketPlayerZone` keeps: line 1, name (1 line, ellipsis), "MPR 2,4", S/M chips; line 2, the `DartSlots` (scoring
darts, or "RATÉ"), and the "VICTOIRE" banner of CR25. The `ScoreLine` loses the points and the "n/7". Its height goes
from 96 (minimum 76) to 84 (minimum 68), see the layout contract.

### 4. Layout contract (new CR22), pure `CricketLayoutPolicy`

Board columns: a button column then the player columns, which share the rest equally.
- Button column: 3 x 48 = 144 dp at least (48 x 48 dp per button, the touch-target floor), up to 3 x 56 = 168 dp when
  the player columns keep at least 76 dp each.
- Rows: 48 dp at least (button floor), up to 56 dp; the text line of the 14 sp texts is still 22.5 dp x label scale,
  so a row is `max(48, 22.5 x scale + 2)`.
- Column header: 3 dp strip + name line, as today; pinned. Footer: pinned, 48 dp at least.
- Only the target rows scroll, between the pinned header and footer, when they do not fit at 48 dp.

Portrait at 360 x 640 (about 568 dp usable), 4 players, 7 rows, scale 1.0, all at their minimums:
top margin 4 + `MatchHeader` 24 + gap 4 + zone 68 + gap 4 + column header 25.5 + 7 rows x 48 (336) + footer 48 +
gap 4 + action bar 48 + bottom margin 0 = 565.5 dp, which fits in 568 without scrolling (2.5 dp spare). Width: 360 -
2 x 12 = 336 dp, so 336 - 144 = 192 dp, 48 dp per player column. With 2 players: 96 dp each; 3 players: 64 dp.
Without the Bull row, 48 dp more for the rows. At scale 2.0 the rows region scrolls; header, footer, "RATÉ" and the
action bar stay visible.

Landscape (640 x 360, about 336 dp usable): 7 rows of 48 dp take 336 dp alone, so the same board cannot fit without
scrolling. This is question Q1 for Kosmo; task R4 builds his answer:
- option A (same board as portrait): left column, the board with its pinned header and footer and scrolling rows
  (about 5 rows visible at once, so 16, 15 and Bull need a scroll); right column, `MatchHeader`, zone, action bar;
- option B (board turned a quarter, recommended): one column per target (20 ... 15, Bull), one row per player. Under
  each target column, its three buttons stacked (single at the top), and "RATÉ" at the end of the button area. The
  first column holds each player's name and his large total. Budget: width 7 x 48 = 336 dp for the targets + 96 dp
  for names and totals = 432 dp, then 160 dp on the right for the `MatchHeader`, the zone and the action bar; height
  4 players x 32 dp + 3 x 48 dp of buttons + gaps = about 284 dp of 336, no scrolling at scale 1.0.

### 5. TalkBack order (CR23, replaces review U7's order)

1. the column headers, the current player's first (« Anna, au lancer, dernière volée : 3 marques »), then the others;
2. row by row from 20 to Bull: the row's buttons, then its cells in column order (a dead row: its buttons, then its
   single merged node);
3. the footer: « Raté », then « Points : Anna 60, Bob 40 » (solo: « Cibles fermées : 4 sur 7 »).

## Files and types added or changed

`app/src/main/kotlin/io/github/darkcosmos/kosmodarts/` (below, `ui/...`):
- `ui/cricket/CricketGameScreenState.kt`: `throwDart`, `CricketTargetButton` (dart, label, description, enabled),
  `buttonRows`, `footerTotals`; `multiplier`, `toggleMultiplier`, `throwNumber`, `throwOuterBull`, `throwBull`,
  `areBullKeysEnabled` removed (R5); `zoneScoreOf` moves to the footer;
- `ui/cricket/CricketLayoutPolicy.kt`: new contract (button column, 48 dp rows, pinned footer, zone 84/68,
  `totalTextSize`, landscape per Q1);
- `ui/cricket/CricketBoard.kt`: no label column; `CricketTargetButtons` per row; pinned footer with "RATÉ" and the
  totals; dead-row strike over the cells only;
- `ui/cricket/CricketTargetButtons.kt` (new): the buttons of one row;
- `ui/cricket/CricketGameScreen.kt`, `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`;
- `ui/cricket/CricketKeypad.kt`: deleted (R5);
- `ui/app/AppState.kt` (`AppEvent.CricketDartTapped`), `ui/app/CricketReducer.kt`;
- `res/values/strings.xml`, `res/values-fr/strings.xml`;
- `docs/rules/cricket.md` (CR22, CR23, CR24), `docs/design/design-system.md` (`CricketBoard`, `CricketTargetButtons`
  instead of `CricketKeypad`, section 6.5);
- tests: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketLayoutPolicyTest`, `CricketScreenTest`,
  `CricketScreenshotTest`, `CricketGameCuesTest`, `CricketPersistenceEffectsTest`, `CricketResumeScreensTest`
  (the last three only where they use the modifier or keypad tags).

## Data read, stored or exposed

No change. The removed multiplier was UI state, never saved (`persistence/` does not read it). No new permission, no
new dependency.

## Screens and states

Cricket game, each in portrait and landscape where marked:
- turn in progress, 2 and 4 players (portrait and landscape);
- turn waiting for "Next player": all buttons locked;
- a dead row: muted, struck cells, buttons enabled;
- 25-Bull off: no Bull row, so no 25/BULL buttons;
- solo: footer with "n/7";
- pending win (CR25), also by a non-thrower;
- totals with 1, 3 and 4 digits in a 48 dp column;
- font scale 2.0 with 4 players (rows scroll, footer and action bar pinned).

### Strings (new, `values/` and `values-fr/`)

| Strings | English | French |
|---|---|---|
| Double button label | "D%1$s" | identical |
| Triple button label | "T%1$s" | identical |
| TalkBack, double | "Double %1$s" | « Double %1$s » |
| TalkBack, triple | "Triple %1$s" | « Triple %1$s » |
| TalkBack, solo footer | "Closed targets: %1$d of %2$d" | « Cibles fermées : %1$d sur %2$d » |

"25", "BULL" and "RATÉ" / "MISS" reuse the existing strings. The X01 Double/Triple strings stay (X01 keypad).

## Test plan

Rules cited: CR1, CR7, CR15, CR22, CR23, CR24, CR25 (new text).

`:app` pure JVM:
- `CricketGameScreenStateTest`: CR24 `throwDart` records D20 and T20 as one dart each; refuses 25 and Bull with
  25-Bull off; refuses any dart while a turn waits (CR7); a dart on a dead row is recorded; `buttonRows` gives 3
  buttons for 20 to 15 and 2 for Bull, none with 25-Bull off; buttons all disabled while a turn waits and enabled on a
  dead row; `footerTotals` gives points, or "n/7" solo.
- `CricketReducerTest`: `CricketDartTapped` reaches `throwDart`; undo after a triple removes the triple.
- `CricketLayoutPolicyTest` (CR22): portrait 360 x 568, 4 players, 7 rows, scale 1.0 does not scroll and sums to at
  most 568; every button is at least 48 x 48 at every player count and scale 1.0 to 2.0; player columns 48/64/96 dp
  at 4/3/2 players; scale 2.0 scrolls the rows only; `totalTextSize` for 1 to 5 digits at 48 and 96 dp, never above
  the column; landscape per Q1 (B: no scrolling at 640 x 336, 4 players).

Robolectric (`CricketScreenTest`):
- CR24: each button sends its dart (20, D20, T20, 25, BULL, RATÉ); no 25/BULL node with 25-Bull off; locked while a
  turn waits;
- CR22: fit at 360 x 640 scale 1.0 with 4 players (no scroll), at scale 2.0 (rows scroll, footer, "RATÉ" and action bar
  fully visible), landscape 640 x 360 at 1.0 and 2.0; each button's bounds at least 48 x 48 dp; buttons vertically
  aligned with their row's cells (same top and bottom); totals and button labels not clipped;
- CR22: the zone has no total and no "n/7";
- CR23: TalkBack order of section 5; dead-row merged node and strike node kept.

Roborazzi captures (record only, Pixel 7, French unless stated), re-recorded: `board_2p_portrait_fr`,
`zone_miss_2p_portrait_fr`, `board_2p_landscape_fr`, `board_4p_portrait_fr`, `board_4p_landscape_fr`,
`board_4p_portrait_en`, `board_4p_portrait_fr_font200`, `board_4p_360x640_fr`, `board_4p_640x360_fr`, the dead rows
(2 and 4 players, with the grayscale copy), solo, 25-Bull off, waiting turn, the three win captures. Removed:
`modifier_triple_portrait_fr`, `keypad_with_bull_fr`, `keypad_without_bull_fr`. Added: `buttons_with_bull_fr`,
`buttons_without_bull_fr` (board crops), `totals_4digits_4p_portrait_fr`. X01 captures checked with `cmp` against the
baseline (see Out of scope).

Local checks, every task: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build`,
`kosmo-detekt` (exit 0).

## How Kosmo verifies on the phone

Replaces steps 3, 4, 7, 13, 14, 16 and 17 of the main plan for the game screen; the other steps stay.
1. Start a Cricket game with 2 players: there is no keypad and no Double/Triple key. Each row of the board starts with
   "20", "D20", "T20" (Bull row: "25", "BULL"); the target column on the left is gone.
2. Tap "T20": one dart, three marks for the player. Tap "D19", then "18": three darts in the zone.
3. The zone shows the name, the MPR and the darts, but no total. The totals are in large type on the bottom row of
   the board, next to "RATÉ", and always visible.
4. "RATÉ" counts a miss; "ANNULER" removes the last dart, including a triple.
5. While "JOUEUR SUIVANT" waits, no target button reacts.
6. Close the 20 for everyone: the 20 row is greyed and struck across the players' cells; its buttons still work and
   play the closed-sector sound.
7. 25-Bull off: no Bull row and no 25/BULL buttons.
8. Solo: the bottom row shows "4/7" instead of points.
9. 4 players on a small phone, portrait: every row and its buttons visible without scrolling; totals readable.
10. Landscape with 4 players: the layout chosen in Q1; buttons still lined up with their target; nothing cut.
11. Largest system font, 4 players: the rows scroll, but the names row, the totals row, "RATÉ" and "ANNULER" stay
    visible.
12. TalkBack: the headers, then each row's buttons (« Double 20 ») and cells, then « Raté » and the points.

## Tasks

### R1 - Rules text, dart buttons model and event

- Goal: rewrite CR22 (zone without total, layout contract of section 4 for portrait; landscape left "per Q1"), CR23
  (no label column, buttons, pinned footer with totals, TalkBack order of section 5) and CR24 (target buttons) in
  `docs/rules/cricket.md`; add `throwDart`, `CricketTargetButton`, `buttonRows`, `footerTotals` to
  `CricketGameScreenState`; `AppEvent.CricketDartTapped` and its `CricketReducer` branch; the new strings. The old
  keypad and modifier stay working until R5, so the build stays green.
- Files: `docs/rules/cricket.md`, `ui/cricket/CricketGameScreenState.kt`, `ui/app/AppState.kt`,
  `ui/app/CricketReducer.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`, `CricketGameScreenStateTest`,
  `CricketReducerTest`.
- Depends on: approval of this addendum, answer to Q2 (and Q3 if it changes the zone text).
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: a state holder extension and its JVM tests following the existing reducer pattern.

### R2 - Layout policy for the button board

- Goal: rewrite `CricketLayoutPolicy` to the contract of section 4 (button column, 48 dp rows, pinned header and
  footer, zone 84/68, `totalTextSize`, player column widths), portrait and the landscape option chosen in Q1, with
  `CricketLayoutPolicyTest` rewritten (old cases about the label column and keypad rows removed only where the
  contract they prove is removed; each new case cites CR22).
- Files: `ui/cricket/CricketLayoutPolicy.kt`, `CricketLayoutPolicyTest`.
- Depends on: R1, answer to Q1.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exits 0.
- Model: opus
- Why this model: the dp budget is tight (2.5 dp spare at 360 x 640) and this contract needed two review rounds
  before (U1); the arithmetic and font-scale cases are subtle.

### R3 - Portrait screen: buttons in the board, pinned totals, zone without total

- Goal: `CricketBoard` without label column, `CricketTargetButtons` per row (sending `CricketDartTapped`), pinned
  footer with "RATÉ" and the totals, dead-row strike over the cells only; `CricketPlayerZone` without total; portrait
  `CricketGameScreen` without `CricketKeypad`; tags and TalkBack order; the Robolectric portrait cases and the
  portrait captures; `docs/design/design-system.md` (components table, 6.5 portrait).
- Files: `ui/cricket/CricketBoard.kt`, `CricketTargetButtons.kt` (new), `CricketGameScreen.kt`,
  `CricketPlayerZone.kt`, `CricketTags.kt`, `CricketSemantics.kt`, `docs/design/design-system.md`,
  `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R2.
- Done when: `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; X01 captures `cmp`-identical to the
  baseline; `kosmo-detekt` exits 0. If beyond about 60 tool calls, stop green and report what is left.
- Model: sonnet
- Why this model: composables built from the existing board and button components against a fixed policy.

### R4 - Landscape screen

- Goal: the landscape layout chosen in Q1 (A: same board in the left column; B: board turned with target columns
  and stacked buttons), its Robolectric fit cases at 640 x 360 (scale 1.0 and 2.0), its captures, and the 6.5
  landscape paragraph of the design system.
- Files: `ui/cricket/CricketGameScreen.kt`, `CricketBoard.kt` (or a new `CricketBoardLandscape.kt` for option B),
  `CricketTargetButtons.kt`, `docs/design/design-system.md`, `CricketScreenTest`, `CricketScreenshotTest`.
- Depends on: R3.
- Done when: same checks as R3.
- Model: sonnet (opus if Kosmo picks B)
- Why this model: A reuses the portrait board; B is a new transposed board with its own TalkBack order and fit, a
  subtler layout.

### R5 - Remove the modifier keypad, finish tests and captures

- Goal: delete `CricketKeypad.kt`, the Cricket `multiplier` state, `toggleMultiplier`, `throwNumber`,
  `throwOuterBull`, `throwBull`, `areBullKeysEnabled` and the Cricket `MultiplierTapped`/`NumberTapped`/
  `OuterBullTapped`/`BullTapped` branches; move every test that used them to `CricketDartTapped` (same cases, same
  assertions, no case dropped: `CricketGameScreenStateTest`, `CricketReducerTest`, `CricketGameCuesTest`,
  `CricketPersistenceEffectsTest`, `CricketResumeScreensTest`); remove the three keypad/modifier captures; check the
  unused strings (Cricket-only ones removed in both languages); update the main plan's phone steps reference and the
  Implementation notes.
- Files: `ui/cricket/CricketKeypad.kt` (deleted), `CricketGameScreenState.kt`, `ui/app/CricketReducer.kt`, the five
  test files above, `CricketScreenshotTest`, `res/values*/strings.xml`.
- Depends on: R4.
- Done when: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; X01 captures
  `cmp`-identical; `kosmo-detekt` exits 0.
- Model: sonnet
- Why this model: mechanical removal, but across several test files where each case must be kept, not dropped.

## Decisions

- Kosmo, PR #60 review: the zone loses the player's total; the total stays only in the board and becomes much more
  visible; no Double/Triple modifier keys; every dart in direct access, aligned with the board, portrait and
  landscape; the board's label column is removed.
- Kosmo, answer 1: three buttons per target (single, double, triple) on the target's row.
- Kosmo, answer 2: the total is removed from the zone and kept only in the board, made much more visible (design
  proposed in section 2, for the UX review).
- Kosmo, answer 3: "RATÉ" and "ANNULER" stay; the Bull follows the 25-Bull option. Read here as CR1 unchanged: with
  25-Bull on, a Bull row with "25" and "BULL"; with it off, no Bull row and no Bull button. The wording relayed to the
  developer ("a single Bull button when the option is off") does not match CR1; see Q3.
- Developer, pending design review: buttons of a dead row stay enabled (closed-sector sound, AN30); the strike crosses
  only the cells.
- Developer, pending design review: "RATÉ" sits in the pinned footer, left of the totals, rather than in the action
  bar, to keep the action bar's two buttons at their size.

## Open questions

Product questions for Kosmo (in French, for main to relay):

- Q1. En paysage, 7 lignes de cibles avec des boutons d'au moins 48 dp prennent toute la hauteur de l'écran : le
  tableau ne tient pas tel quel.
  - A : même tableau qu'en portrait (cibles en lignes, boutons à gauche de chaque ligne) ; les lignes défilent, on
    voit environ 5 cibles à la fois et il faut faire défiler pour 16, 15 et Bull.
  - B (recommandée) : tableau tourné d'un quart de tour en paysage : une colonne par cible (20 à Bull), une ligne par
    joueur avec son nom et son total en grand ; sous chaque cible, ses trois boutons empilés. Tout tient sans
    défilement.
- Q2. En solo, la vignette affichait « 4/7 » (cibles fermées) à la place des points. Puisque le total quitte la
  vignette :
  - A (recommandée) : « 4/7 » passe en grand dans la ligne du bas du tableau, à la place des points ;
  - B : « 4/7 » reste dans la vignette en solo.
- Q3. Le Bull : aujourd'hui, avec l'option 25-Bull activée, on joue le Bull avec deux boutons « 25 » (1 marque) et
  « BULL » (2 marques) ; désactivée, le Bull n'est pas une cible et il n'y a aucun bouton Bull.
  - A (recommandée, sans changement de règle) : on garde ce fonctionnement ;
  - B : option désactivée, le Bull reste une cible avec un seul bouton « BULL » (le 25 ne compte pas). C'est un
    changement de règle du jeu et de sauvegarde, qui demanderait une tâche moteur en plus.
- Q4. « Épurer la vignette » : en dehors du total, on garde le nom, le MPR, les pastilles sets/manches et les
  fléchettes de la volée ?
  - A (recommandée) : oui, on garde tout cela ;
  - B : on retire aussi autre chose (à préciser).

## Risks

- Portrait 360 x 640 with 4 players and the Bull: 2.5 dp spare at all minimums; the zone minimum (68 dp) must be
  measured against the real name line and `DartSlots` (36 dp) height, or the rows scroll on small phones.
- Player columns of 48 dp at 4 players: names get a strong ellipsis, totals of 4 digits fall to 20 sp, and at font
  scale 2.0 even 3 digits may need 14 sp; to be asserted by the clipping tests.
- Button labels "T20" in 48 dp at font scale 2.0 (about 46 dp of text): at the limit; the clipping test covers it.
- Landscape: option A scrolls the rows (buttons move with their rows); option B is a second board layout to build
  and test.
- About 25 existing Robolectric cases and 20 game captures use the keypad or the modifier and are rewritten; the
  X01 captures must stay byte-identical.

## Implementation notes

(One line per task: task id, deviations, where the main pieces live.)
