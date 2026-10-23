# Visit total rules (game screen)

Status: v3 (issue #36, with the owner answers Q1 to Q4 of 2026-10-20 and his review PR37-R1, PR37-R2 of PR #37;
VT9 to VT11 and VT13 amended by issue #49, design system).
This document is the single source of truth for the visit total box of the game screen: tests cite these rules, never
the issue text. Rule ids (VT1, VT2, ...) are stable. They use the prefix VT so that they never collide with the
X01 rules (`docs/rules/x01.md`), the persistence rules (`docs/rules/persistence.md`) or the announcement rules
(`docs/rules/announcements.md`).

Vocabulary:
- *Visit*: a player's turn of at most three darts (X01 rules T1, T2). The app's English texts say "turn"; the French
  texts say « volée ».
- *Engine current player*: `X01State.currentPlayerIndex`, the player who throws the next dart for the engine.
- *Waiting turn*: a completed turn that waits for "Next player" (« Joueur suivant ») or for the checkout dialog
  (`GameScreenState.isAwaitingConfirmation`, PR #28). The engine has already passed the turn to the next player.
- *Highlighted player*: the player shown in the highlighted card (`GameScreenState.highlightedPlayerIndex`): the
  player whose turn waits, else the engine current player.

## Owner decisions (issue #36 comments, 2026-10-20)

They complete or replace the issue body; in case of contradiction they prevail.

| Id | Decision | Rules |
|---|---|---|
| Q1 | The app has no light theme (a future look and feel mission will add one). Contrast is checked in the existing dark theme only. | VT10 |
| Q2 | The existing "Prev." / « Préc. » label (PR #28) stays; the visit total box is added next to it. **Replaced by PR37-R1.** | VT9 |
| Q3 | While a turn waits for "Next player", the next player's box keeps its last visit (or "not played"); it shows 0 only once "Next player" is tapped. The engine computes the states; the app picks which one to show. | VT6 |
| Q4 | TalkBack: "Current turn: 0" / « Volée en cours : 0 » for the player whose turn it is, "Not played yet" / « Pas encore joué » for a player who has thrown no dart yet. | VT2, VT5, VT8 |

## Owner review of PR #37 (darkcosmos, 2026-10-20)

Kosmo tested PR #37 on his phone: no readability or visibility problem (so the box needs no visible label). His
requests prevail over Q2 and over the issue body. His ids R1 and R2 are written PR37-R1 and PR37-R2 here, so that
they never mix with the persistence rules R1 to R12.

| Id | Decision | Rules |
|---|---|---|
| PR37-R1 | Remove the "Prev." / « Préc. » label: it is redundant with the visit total box. | VT9, VT11 |
| PR37-R2 | When a player busts, only the big BUST badge is shown while his bust waits for "Next player": for this case only, it replaces the visit total box. After "Next player", the current behaviour stays (his box shows `BUST`, VT4). | VT8, VT9, VT13 |

## States (`:game-engine`)

The engine derives one state per player from the game state (configuration and ordered darts, X01 rule P1).

- VT1. Every player has exactly one of four states: *not played*, *visit in progress* (points), *last visit*
  (points), *bust*. A dart's points are its value (X01 rule D1); a miss counts 0.
- VT2. The engine current player is in *visit in progress*, with the sum of the darts of his turn in progress. Before
  his first dart, the sum is 0, at the start of the game as well (Q4).
- VT3. Any other player whose last completed turn (X01 rule A3) ended normally or by a checkout is in *last visit*,
  with the sum of the darts of that turn. The winning turn counts like any other turn.
- VT4. Any other player whose last completed turn ended by a bust (X01 rule S4) is in *bust*, whatever the darts
  scored before the bust dart.
- VT5. Any other player with no completed turn is in *not played*.
- VT6. The engine also gives, for any player, his *last completed visit*: *not played*, *last visit* or *bust*,
  following VT3 to VT5 and ignoring the turn in progress. While a turn waits (vocabulary), the app shows the engine
  current player in this state instead of *visit in progress*; once "Next player" is tapped, VT2 applies (Q3).
- VT7. Nothing is stored for these states: they are derived each time from the game and the existing waiting flag.
  Undo (X01 rules U1 to U3), including the undo of a dart of a turn already confirmed, a resumed game (persistence
  rules R1, R4) and a screen rotation therefore show exactly the values that the same game shows without them. The
  save file format does not change.

## Display (`:app`)

- VT8. Box content and TalkBack description:

  | State | Box text | Description (en) | Description (fr) |
  |---|---|---|---|
  | not played | `0` | Not played yet | Pas encore joué |
  | visit in progress | points | Current turn: {points} | Volée en cours : {points} |
  | last visit | points | Last turn: {points} | Dernière volée : {points} |
  | bust | `BUST` | Last turn: bust | Dernière volée : bust |

  `BUST` is the same text in English and French (existing `game_bust` resource). French descriptions use a
  non-breaking space before the colon. The description does not repeat the player name: TalkBack reads the box
  after the player's name and scores, in the order of the screen (top to bottom, then start to end).
- VT9. Every player's box is visible at all times during the game, next to his remaining score, without a label
  (PR37-R1). Since issue #49 (design system, `VisitBox`): the current player's box is **filled** (white, text
  `background`) at the end of the dart slots of his zone; the other players' boxes are **outlined** (border
  `onBackgroundMuted`) on their tiles, after their remaining score.
- VT10. The filled box keeps its white fill whatever the zone color (player color, `danger` during a BUST); the
  outlined boxes sit on the tiles' `surface`. Contrast is checked in the dark theme (Q1; `PaletteContrastTest`).
- VT11. Layout: in portrait and in landscape, with 1 to 4 players (X01 rule C4), at every font size up to the
  largest one offered by Android (scale 2.0):
  - no box text is truncated, and a box keeps the same size whether it shows `BUST`, `180`, or any other value;
  - the remaining score of a tile is never truncated; on a narrow tile at a large font size, the box goes under the
    score;
  - the score of the zone keeps its place and size while a turn is played, waits, busts or is confirmed;
  - a player name longer than its space is cut with an ellipsis (on two lines at most on the landscape tiles at
    4 players, U3 of issue #49).
- VT12. Translating states to texts happens outside composables (UI state mapping); composables only render the
  mapped texts.
- VT13. While a bust waits for "Next player", the zone turns `danger` with the white BUST banner in the route area
  (CS8: under the score in portrait, right of it on one line in landscape), and the thrower's filled box shows
  `0` (issue #49, design system 6.2; it replaces PR37-R2), read by TalkBack as "Last
  turn: bust" / « Dernière volée : bust ». Once "Next player" is tapped, his box (now on a tile) shows `BUST` (VT4);
  if the bust dart is undone, the box follows VT2 to VT6 again. A waiting normal turn and a pending checkout keep
  their box (VT3, VT6).
