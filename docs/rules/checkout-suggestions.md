# Checkout suggestion rules (game screen)

Status: v2 (issue #38, with the owner answers Q1 to Q5 of 2026-10-21; CS5 and CS7 to CS9 amended by issue #49).
This document is the single source of truth for the checkout suggestion shown on the highlighted card of the game
screen: tests cite these rules, never the issue text. Rule ids (CS1, CS2, ...) are stable. They use the prefix CS so
that they never collide with the X01 rules (`docs/rules/x01.md`), the persistence rules (`docs/rules/persistence.md`),
the announcement rules (`docs/rules/announcements.md`) or the visit total rules (`docs/rules/visit-total.md`).

Vocabulary:
- *Scoring dart*: any dart of X01 rule D1 except a miss: singles, doubles and triples of 1 to 20, the outer bull (25)
  and the bull (50).
- *Finishing dart*: in double out, a double or the bull (X01 rule D3); in single out, any scoring dart.
- *Waiting turn*, *highlighted player*, *engine current player*: as in `docs/rules/visit-total.md`.

## Owner decisions (issue #38 comment, 2026-10-21)

They complete or replace the issue body; in case of contradiction they prevail.

| Id | Decision | Rules |
|---|---|---|
| Q1 | At equal value, prefer the easiest dart: single, then double, then triple. | CS3 |
| Q2 | A translated label, "Checkout: " / « Sortie : », placed under the remaining score. | CS7, CS8 |
| Q3 | The suggestion line is always reserved, empty when there is no suggestion: nothing moves. | CS8 |
| Q4 | TalkBack reads the route with the localized words of the voice announcements. | CS9 |
| Q5 | A single is written without "S", like the dart slots (`20 · D20`). | CS7 |

## Route search (`:game-engine`)

- CS1. A *route* for a remaining score R and k darts left (1 <= k <= 3) is a sequence of 1 to k scoring darts whose
  values total exactly R and whose last dart is a finishing dart. Misses are never part of a route. The number of
  darts left is always 0 to 3; any other value is a programming error.
- CS2. A suggestion exists if and only if at least one route exists. There is none for R < 1, for k = 0, or when no
  route exists (for example 101 with 1 dart, 159 with 3 darts in double out, 1 in double out). The announcement rule
  AN3 ("a checkout is possible") is exactly "a suggestion exists with 3 darts left".
- CS3. Among the routes, the suggestion is the first one in this order:
  1. the fewest darts;
  2. in double out only, the last dart in this order of preference: D20, D16, D8, D18, D12, D10, then the other
     doubles from highest to lowest (D19, D17, D15, D14, D13, D11, D9, D7, D6, D5, D4, D3, D2, D1), the bull last;
  3. the preceding darts (in single out: all the darts, since there is no last-dart preference), compared position
     by position, the highest value first;
  4. at equal value at a position, the easiest dart first: single, then double, then triple (Q1).

  This order is total: two different routes never tie. It is computed by enumerating the darts of the board; there
  is no table of routes in the code.
- CS4. The suggestion of a game is computed for the engine current player: their remaining score, 3 minus the darts
  already thrown in their turn, and the game's checkout rule. A finished leg has no suggestion, and neither has a
  state whose number of darts left is outside 1 to 3 (never an exception, for example on a restored save).

Reference cases (owner table, issue #38, with Q5):

| Remaining | Darts left | Rule | Suggestion |
|---|---|---|---|
| 170 | 3 | double | T20 · T20 · Bull |
| 167 | 3 | double | T20 · T19 · Bull |
| 160 | 3 | double | T20 · T20 · D20 |
| 110 | 2 | double | T20 · Bull |
| 100 | 3 | double | T20 · D20 |
| 60 | 3 | double | 20 · D20 |
| 50 | 3 | double | Bull |
| 40 | 1 | double | D20 |
| 3 | 3 | double | 1 · D1 |
| 101 | 1 | double | none |
| 159 | 3 | double | none |
| 180 | 3 | single | T20 · T20 · T20 |

## Display (`:app`)

- CS5. The suggestion is shown only in the current player's zone, and only when no turn waits: it is hidden while a
  turn waits for "Next player" (after the third dart or a bust: BUST mode) and while a checkout waits for its
  confirmation (CHECKOUT mode; issue #49). Tiles and the end screen have no suggestion.
- CS6. It is recomputed after every dart and every undo: it is derived from the game, never stored, and needs no
  setting.
- CS7. Text (as amended by issue #49, design system `CheckoutRouteBar`): a label chip "CHECKOUT" / « SORTIE » on
  `accent`, then the darts in `accent` on `background`, separated by a wide space (no separator dots), written like
  the dart slots: `T20`, `D16`, `20` (single), `25` (outer bull), `BULL`. The notation is the same in English and
  French.
- CS8. The route area of the zone is always present, under the score and above the dart slots. When there is no
  route it is empty and silent for TalkBack; during a BUST it holds the BUST banner (VT13). Its height is reserved
  for the widest possible route (the label then three times the widest dart text) at the current width and font
  scale, in every state, so the zone and the score never move when a route appears or disappears. The text is never
  truncated: it may wrap only between two darts.
- CS9. TalkBack reads the route area as one item: the label, then the darts with the words of the voice
  announcements, separated by commas. The words are identical in English and French: single `20`, double
  `Double 16`, triple `Triple 20`, outer bull `25`, bull `Bull`. Examples: "Checkout: Triple 20, Triple 20, Bull",
  « Sortie : 20, Double 20 ». In the zone, TalkBack reads in screen order: the name, the score, the route, then the
  dart slots and the visit total box.
