# Player rules (colors)

Status: v2 (issue #51, plan `docs/plans/51-player-colors-players-screen.md`, section "Decisions"). It replaces v1
(issue #49, color slots).
This document is the single source of truth for the color of each player: tests cite these rules, never the issue
text. Rule ids (PC1, PC2, ...) are stable. They use the prefix PC so that they never collide with the X01 rules
(`docs/rules/x01.md`), the match rules (M), the persistence rules (R), the announcement rules (AN), the visit total
rules (VT) or the checkout suggestion rules (CS).

Vocabulary:
- *Player color*: an opaque ARGB color, `PlayerColor`, that each player has. The engine knows the colors and their
  rules; the app draws them (`docs/design/design-system.md`, section 2).
- *Palette*: blue `#1F63D6`, orange `#E07020`, teal `#0C7C6A`, lilac `#C9A7FF`, in this order.

## Rules

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

## Related rules

- Persistence: R17 (colors saved with the game and the last setup) and R31 (format version 2, one ARGB value per
  player) (`docs/rules/persistence.md`).
- Match: M13 (setup player list), M14 (indicators) (`docs/rules/match.md`).
