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
  hence the starter of the first leg. Since issue #49, the current player is always shown in the same fixed zone,
  and the other players' tiles are in play order starting from the next player (the order moves with each "Next
  player"); the first tile is read by TalkBack as the next player.
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
- M19. Match position (issue #49): the next leg belongs to set number "sets completed + 1" and is leg number "legs
  played in the current set + 1" of its set. The last confirmed leg has the set and leg numbers it had while it was
  played. The app shows these numbers as given by the engine and never counts legs or sets itself.

## App

Rewritten by issue #49 (design system, owner answers Q1 to Q13, O1 and O2, `docs/plans/49-design-system.md`).

- M13. Setup: "MATCH" with two steppers, "Sets · first to" then "Legs · first to" (« Sets · premier à »,
  « Manches · premier à »), with these labels in every format, each with − and +; − is disabled at 1, + at 9. Legs
  and sets are allowed with a single player (owner Q8 of #39). "PLAYERS · THROWING ORDER": one row per player with
  his color strip, order number and name field, and a remove cross on each row (any player, while more than one is
  left; it removes the player and his name at once, owner Q2), then "+ ADD A PLAYER" while fewer than 4 players.
- M14. Match texts: with a format other than 1 and 1, the current player's zone shows the chips "S n" and "M n"
  (English "S n", "L n"), each tile the line "S n · M n", the home card the same line; the header reads
  "SET 2 · MANCHE 3" (English "SET 2 · LEG 3"). With Sets = 1, no set is mentioned anywhere: "M n", "MANCHE 3"
  (owner Q1). Nothing is shown in a single game. Numbers come from the engine (M19).
- M15. Pending checkout: no dialog. The zone shows the CHECKOUT banner (design system 6.3) and the action bar reads
  "ANNULER" / "VALIDER LA MANCHE" for LEG, "VALIDER LE SET" for SET, "VALIDER LA PARTIE" for MATCH (English
  "UNDO" / "CONFIRM LEG", "CONFIRM SET", "CONFIRM MATCH"; owner Q5). A single game always shows "VALIDER LA PARTIE".
  "ANNULER" undoes the winning dart. System back opens "Abandon the game?" like anywhere else in a game; dismissing it
  leaves the checkout pending (owner O1).
- M16. Between legs: after confirming a leg or a set, a screen shows the format line, the winner's zone in his color
  with the chip "SET 2 · MANCHE 3 GAGNÉE" ("MANCHE 3 GAGNÉE" with one set, "SET 2 GAGNÉ" on a set win), his name and
  the winning checkout ("CHECKOUT 141 · T20 T19 D12", not shown after a resume on this screen), the scoreboard (SETS
  when S > 1, MAN. for the legs of the set, MOY MATCH; the leg winner's figures in accent), the bar "SET 2 · MANCHE 4
  ▌ CID COMMENCE" and the button "MANCHE SUIVANTE". System back opens "Abandon the game?" (owner Q6 i of #39).
- M17. End screen: the format line, the winner's zone with "VAINQUEUR DU MATCH" and his name (no "2 SETS À 0" line,
  owner Q8), then the M10 ranking: rank cell (rank 1 on accent), color square, name, the SETS column (MANCHES with one
  set, no score column in a single game), MOY MATCH, and the darts thrown over the match (owner O2, header « FLÉCH. »).
  "REVANCHE" starts a match with the same format; its order follows today's rule (first player moved to the end,
  owner Q3 of #39), each player keeping his color (PC7). "NOUVELLE PARTIE" is unchanged.
- M18. Home card of a saved game: "PARTIE EN COURS", the format line, one mini tile per player (color strip, name,
  score, "S n · M n" in a match), and the button "REPRENDRE · À <joueur> DE LANCER" always (owner Q11), the player
  being the one the game screen shows as throwing after resuming. Between legs, it is the starter of the next leg.

## Related rules

- Persistence: R13 to R19 (`docs/rules/persistence.md`), format versions 2 and 3 (`docs/adr/0005-save-format-v2-match.md`,
  `docs/adr/0006-save-format-v3-colors.md`).
- Player colors: PC1 to PC9 (`docs/rules/players.md`).
- Announcements: AN27 (win voice and victory sound by outcome) and AN28 (turn start of a new leg)
  (`docs/rules/announcements.md`).
