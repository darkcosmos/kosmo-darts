# Visit total rules (game screen)

Status: v1 (issue #36, with the owner answers Q1 to Q4 of 2026-10-20). This document is the single source of truth
for the visit total box of the game screen: tests cite these rules, never the issue text. Rule ids (VT1, VT2, ...)
are stable. They use the prefix VT so that they never collide with the X01 rules (`docs/rules/x01.md`), the
persistence rules (`docs/rules/persistence.md`) or the announcement rules (`docs/rules/announcements.md`).

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
| Q2 | The existing "Prev." / « Préc. » label (PR #28) stays; the visit total box is added next to it. | VT9 |
| Q3 | While a turn waits for "Next player", the next player's box keeps its last visit (or "not played"); it shows 0 only once "Next player" is tapped. The engine computes the states; the app picks which one to show. | VT6 |
| Q4 | TalkBack: "Current turn: 0" / « Volée en cours : 0 » for the player whose turn it is, "Not played yet" / « Pas encore joué » for a player who has thrown no dart yet. | VT2, VT5, VT8 |

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
  non-breaking space before the colon.
- VT9. Every player's box is framed and visible at all times during the game, next to his remaining score, for every
  player. The "Prev." / « Préc. » label and the "BUST" badge of a waiting bust (PR #28) stay unchanged (Q2).
- VT10. The highlighted player's box is inside his highlighted card and takes the card's existing colors; the other
  boxes take the existing colors of the player rows. No new color or style is created. Contrast is checked in the
  dark theme (Q1).
- VT11. Layout: in portrait and in landscape, with 1 to 4 players (X01 rule C4): no box text is truncated, no box
  pushes the remaining score out of the screen, and a box keeps the same size whether it shows `BUST`, `180`, or any
  other value. Player names keep their existing behaviour (cut with an ellipsis when too long).
- VT12. Translating states to texts happens outside composables (UI state mapping); composables only render the
  mapped texts.
