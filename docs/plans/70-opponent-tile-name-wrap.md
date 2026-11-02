# Plan: opponent tile names never break inside a word (issue #70)

## Goal

Fix [issue #70](https://github.com/darkcosmos/kosmo-darts/issues/70): on the small tiles of the other players, a name
is never broken inside a word ("BO / B", "DA / N"). It shrinks, never below its size at font scale 1.0, then ends with
"…" on one line. Landscape and portrait, 2 to 4 players, with and without a sets match.

## Scope and out of scope

In scope:
- `PlayerTile` name (`TileName` in `ui/components/PlayerTile.kt`), every `TileVariant` (WIDE, NARROW, COMPACT,
  COMPACT_TWO_LINES).
- Tests that state the old "at least 8 characters before …" rule (U3, T8): kept where it still holds, relaxed where
  Kosmo accepted it gives way (D3).
- Docs: design system `PlayerTile` row, rule VT11 (`docs/rules/visit-total.md`).
- Captures: re-record, compare with `main`, add the issue's case.

Out of scope:
- The tile layout itself (where the "S n · M n" line sits, tile height): option C of intake, possible follow-up issue.
- Other names (current player zone, Cricket headers, history, resume card `MiniTile`): unchanged.
- `breaksOnlyBetweenWords` keeps its definition (a break is allowed only at whitespace; see Open questions).

## Approach

Measured at intake (Pixel 7, French, probe never committed): the tile name is a plain `Text` with `maxLines` 1 or 2,
no autosize, so a word wider than the line is broken anywhere. In landscape at 4 players in a match the
"S n · M n" line leaves the name 44 dp at scale 2.0 (BOB needs 47, DAN 48, MIM 51, CID 22), 57 dp at 1.5
(KOSMO, JULIEN, MAXIME break), 68 dp at 1.3 (MAXIME breaks). Without a match or in portrait only long names break
(BARTHOLOMEW, "BARTHOLOM / EW" at 4 players portrait even at 1.0).

New `TileName` behaviour (D1, D2):
1. Lines allowed: 2 only on the variants that allow two lines today (NARROW, COMPACT, COMPACT_TWO_LINES) **and** when
   the name has at least two words (`wordCount`); otherwise 1. A one-word name can then never be broken.
2. Size: `autoSize` = the word-safe autosize of `WordSafeText.kt` (`WordSafeAutoSize`, made `internal`), from the
   style size (`playerNameTile`, 18 sp, follows the font scale) down to the size at scale 1.0
   (`density.fixedSp(18f)`, a new constant next to the style). It keeps the largest size with no overflow and every
   line break at a space. At scale 1.0 min = max: no change of size.
3. Overflow: `TextOverflow.Ellipsis`, left-aligned (the `Text` keeps its default start alignment; only
   `WordSafeText` centres, and it is not used here).
4. Fallback for a multi-word name that does not fit word-safe on 2 lines even at the minimum size (a word alone wider
   than the line, e.g. "MAXIME DUPONT" in a 44 dp name box): `onTextLayout` checks `breaksOnlyBetweenWords` on the
   result; if it fails, a remembered flag (keys: name, variant, the layout's max width) switches the name to one line,
   so it ends with "…". One extra composition, only in that case.
5. TalkBack: unchanged mechanism. The `Text` semantics keep the full name whatever is drawn; the next player's tile
   keeps its content description "Prochain joueur : <full name>, n restants" (U4).

Alternatives for the structural choice (how to pick the number of lines from the available width):
- `BoxWithConstraints` + `TextMeasurer`: rejected, the landscape tile uses `height(IntrinsicSize.Min)` and a
  `SubcomposeLayout` does not answer intrinsic measurements (crash).
- A custom `Layout` measuring a 2-line and a 1-line candidate and placing one: no extra frame, but two text nodes per
  name (tags, semantics, tests) for a rare case: too heavy for this fix.
- Chosen: static lines from the word count (covers every one-word name, the issue's case, with no extra frame) plus
  the `onTextLayout` fallback for the rare multi-word case.

`GameLayoutPolicy.tileHeight` is unchanged: it reserves `labelLines` name lines at the full style size, an upper bound
that stays valid (the name never gets more lines nor a larger size than today). `GameLayoutPolicyTest` is unchanged.

## Files and types added or changed

- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/PlayerTile.kt`: `TileName` (lines, autosize,
  fallback), comments U3/T8/U19 updated.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/WordSafeText.kt`: `WordSafeAutoSize` becomes
  `internal` (no behaviour change for buttons and keypad).
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/theme/KosmoTypography.kt`: the 18 constant of
  `playerNameTile` named so `TileName` can use its 1.0 size (no value change).
- New `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/TileNameFitTest.kt` (Robolectric, native graphics).
- Changed tests: `RedesignScreensTest` (U3), `GameScreenFitTest` (T8), `ZoneLayoutTest` (U3 comment and
  assertions).
- New capture test or captures in `screenshots/` (T2), `docs/design/design-system.md`, `docs/rules/visit-total.md`.

## Data read, stored or exposed

None. Display only.

## Screens and states

X01 game screen (single game and match), portrait and landscape, 2 to 4 players, every state that shows tiles
(normal, BUST, CHECKOUT waiting). Cricket has no `PlayerTile`.

## Test plan

Rules: VT11 (as amended, D3), design system `PlayerTile`, U3, U4, T8, U11 (`breaksOnlyBetweenWords`).

Write `TileNameFitTest` first and show it red on the current code (BOB/DAN at 2.0 landscape match), then fix.
- Runner: `ParameterizedRobolectricTestRunner`, Pixel 7, French; one test method per configuration
  (`+fr-land`, `+fr-port` × font scale 1.0, 1.3, 1.5, 2.0 = 8 methods). Parameter names in ASCII only (a "é" in a
  parameter name breaks the Gradle HTML report).
- Parameters (game started with `MatchScript`, sets match = 3 legs, 3 sets; single game = 1 leg, 1 set):
  - 4 players, match and single game: Ann + (Bob, Cid, Dan); Ann + (Mim, Kevin, Kosmo); Ann + (Julien, Maxime,
    Jean Paul); Ann + (Bartholomew, Maxime Dupont, Bartholomew Jonathan).
  - 3 players, match and single game: Ann + (Kosmo, Bartholomew).
  - 2 players, match: Ann + (Bartholomew).
- For every tile name (`GameTags.playerName(i)`, layout from `GetTextLayoutResult`):
  - every line break between words: `breaksOnlyBetweenWords(text, layout.lineEnds())`;
  - no width overflow; at most 2 lines, 1 line for a one-word name;
  - font size between the 1.0 size and the style size; when "…" shows, the size is the 1.0 size (shrink first);
  - left-aligned (`getLineLeft(0)` is 0);
  - semantics text = the full displayed name (`displayName`), even when "…" shows.
- CID unchanged: at every configuration CID is on one line at the full style size (as today).
- Next player (U4): tile 1's name keeps the content description built by `nextPlayerDescription` with the full
  name (compared with the string resource, not a literal) when that name ends with "…" (set Ann + (Bartholomew, …):
  BARTHOLOMEW is next and ends with "…" in landscape at 2.0 in a match).
- Existing tests: U3 (`RedesignScreensTest`, no match, landscape 4 long names, 1.0 and 2.0), T8 (`GameScreenFitTest`,
  portrait 4 players at 2.0) and U3 in `ZoneLayoutTest` (2 and 3 players, match, 1.0) still require 8 characters:
  they must stay green unchanged except their comments; if one goes red, report it to main (never weaken it
  without a decision). Only the new match/landscape/4-player test at large scales allows fewer than 8 (D3).
- `GameLayoutPolicyTest`: unchanged, must stay green.
- Captures (T2): re-record all, `cmp` every PNG against `main` ec4ee46; new captures
  `tile_names_4p_match_landscape_fr_font{130,150,200}` (Ann, Kevin, Kosmo, Maxime Dupont) and
  `tile_names_4p_portrait_fr_font200` (Ann, Bartholomew, Maxime Dupont, Cid).

## How Kosmo verifies on the phone

1. Police au maximum (200 %), paysage, nouvelle partie X01 301 en sets avec Ann, Bob, Cid, Dan : BOB, CID et DAN
   sont sur une seule ligne, en entier.
2. Même réglage, joueurs Ann, Kevin, Kosmo, Julien : aucun nom coupé au milieu ; un nom trop long finit par « … »
   sur une seule ligne, dans une taille pas plus petite qu'à 100 %.
3. Portrait, 4 joueurs dont « Bartholomew » : plus de « BARTHOLOM / EW » ; le nom tient en entier ou finit par « … ».
4. Avec TalkBack, toucher une tuile dont le nom finit par « … » : le nom complet est lu.
5. Police à 100 % : les tuiles sont comme avant.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Failing tests, word-safe tile name | `70-opponent-tile-name-wrap/T1-tile-name-fit.md` | - | sonnet | Specified UI fix with listed Robolectric tests |
| T2 | Captures, comparison, VT11 and design-system wording | `70-opponent-tile-name-wrap/T2-captures-docs.md` | T1 | sonnet | Judging which PNG may change needs care |

No `opus` task.

## Decisions

- D1 (Kosmo, Q1 = A): a tile name shrinks word-safe from the user's size down to its size at 100 %, then is cut with
  "…" on one line. Two lines only when the break falls between two words. Left-aligned. TalkBack reads the full name.
- D2 (Kosmo, Q2): the same treatment everywhere the tiles show, portrait included (4 players, long names): no break
  inside a word anywhere.
- D3 (Kosmo, Q3): the rule "at least 8 characters before …" (U3, T8) gives way where the space is too small (sets
  match, landscape, 4 players, large font scales); docs and tests are updated to say so.
- D4 (developer, for review): lines picked from the word count plus an `onTextLayout` fallback (see Approach).
- D5 (developer, for review): `GameLayoutPolicy` unchanged (upper bound still valid).

## Open questions

- Is a break after a hyphen allowed ("MAXIMILIEN- / ALEXANDRE")? Default in this plan: no (only spaces separate
  words, as for buttons, U11), so such a name is on one line with "…". Ask Kosmo only if a reviewer contests it.
