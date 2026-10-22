# Match rules (legs and sets)

Status: v1 (issue #39, with the owner answers Q1 to Q11 and O1 of 2026-10-21, recorded in
`docs/plans/39-x01-legs-sets.md`, section "Decisions").
This document is the single source of truth for matches played over several legs and sets: tests cite these rules,
never the issue text. Rule ids (M1, M2, ...) are stable. They use the prefix M so that they never collide with the X01
rules (`docs/rules/x01.md`: C, D, T, S, U, E, A, P), the persistence rules (R), the announcement rules (AN), the visit
total rules (VT) or the checkout suggestion rules (CS).

Vocabulary:
- *Leg* (« manche »): one game of the mode. For X01, one leg under rules C to A of `docs/rules/x01.md`.
- *Set*: a group of legs. The French texts also say « set ».
- *Single game*: a match with the format 1 leg and 1 set, which is today's single X01 game.

## Engine

- M1. A match has a format: legs per set L and sets S, each from 1 to 9. Default 1 and 1. A format outside these
  bounds is rejected with an explicit error. Both are shown as "First to N" (« Premier à N »), never "Best of".
- M2. A leg is one game of the mode (for X01: one leg under rules C to A of `x01.md`). The first player to finish
  wins the leg.
- M3. A player wins a set on reaching L legs won in the current set. Every player's legs of the current set then go
  back to 0.
- M4. A player wins the match on reaching S sets won. The match is then finished: no further leg can be played.
- M5. With the format 1 and 1 (single game), the match is exactly one leg and every existing X01 behavior is
  unchanged: same screens, same texts (no leg or set mention), same ranking (E1, E2), same save content apart from
  the format version (R13).
- M6. Starting player: legs are numbered from 0 over the whole match, sets ignored. Leg k starts with the player at
  position k mod n in the configuration order (n players). The setup "Shuffle" only fixes the configuration order,
  hence the starter of the first leg. Cards keep the configuration order; only the highlight moves (owner Q9).
- M7. When the winning dart of a leg is thrown, the match tells what confirming it would decide: LEG (neither a set
  nor the match), SET (a set but not the match) or MATCH. With S = 1, a leg that reaches L legs decides the match.
- M8. A confirmed leg is final: undo never goes back into a previous leg. Inside the current leg, U1 to U3 apply,
  including undoing the winning dart before confirmation.
- M9. Statistics: a player's three-dart average is computed over the whole match: points scored in every leg
  (completed legs and the current one) divided by darts thrown in every leg, times 3 (bust darts count, A1). No dart
  thrown means no average. Darts thrown on the end screen are also counted over the whole match. Per-leg figures
  restart at each leg: remaining score, darts of the current turn, the visit total box (`visit-total.md`), which
  shows "not played" for everyone at the start of a leg.
- M10. Final ranking: the match winner first. The others by sets won (more is better), then by legs won over the
  whole match (owner Q1), then by the mode tie-break (X01: remaining score in the last leg, lower is better). Players
  equal on all criteria share a rank and the next rank is skipped (1, 2, 2, 4). With 1 and 1 this is E1 and E2.
- M11. The match logic knows nothing of X01: the mode provides the leg winner, the per-leg figures it needs and the
  tie-break value of each player.
- M12. A match has at most (n(L-1)+1) x (n(S-1)+1) legs (1,089 for 4 players, 9 and 9): this bound is used to
  validate saves.

## App

- M13. Setup: two settings, "Legs per set" and "Sets", each with − and + buttons, value shown as "First to N". − is
  disabled at 1, + at 9. When Sets = 1, the first label reads "Legs" (owner Q10). Legs and sets are allowed with a
  single player (owner Q8).
- M14. Indicators: with a format other than 1 and 1, every player card and row shows the sets won and the legs won
  in the current set, each group with a visible label ("Sets ●●", "Legs ○○○"). Nothing is shown in a single game.
  With Sets = 1, no set indicator is shown, and no text anywhere mentions sets. The current player's card is always
  scrolled fully into view.
- M15. Checkout dialog: the confirm button reads "Finish leg" (« Terminer la manche ») for LEG, "Finish set"
  (« Terminer le set ») for SET, "Finish game" (« Terminer la partie ») for MATCH. Title and "Undo" are unchanged.
  A single game always shows "Finish game".
- M16. Between legs: after "Finish leg" or "Finish set", a screen shows who won the leg or the set, the match score
  (each player's sets, when S > 1, and legs of the current set) and the player who starts the next leg, with one
  button "Next leg" (« Manche suivante »). System back opens "Abandon the game?" (owner Q6 i).
- M17. End screen: the match winner, then the M10 ranking with today's columns (average and darts over the match,
  owner Q2) plus each player's match score when the format is not 1 and 1. "Rematch" starts a match with the same
  format; its order follows today's rule (first player moved to the end, owner Q3). "New game" is unchanged.
- M18. Home summary of a saved match: today's content plus each player's match score (owner Q11). Between legs, the
  highlighted player is the starter of the next leg.

## Related rules

- Persistence: R13 to R16 (`docs/rules/persistence.md`), format version 2 (`docs/adr/0005-save-format-v2-match.md`).
- Announcements: AN27 (win voice and victory sound by outcome) and AN28 (turn start of a new leg)
  (`docs/rules/announcements.md`).
