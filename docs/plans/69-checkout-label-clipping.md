# Plan: CHECKOUT banner cut at large font sizes (issue #69)

## Goal

Issue [#69](https://github.com/darkcosmos/kosmo-darts/issues/69): while an X01 checkout waits for confirmation (M15),
the `accent` "CHECKOUT" banner of the current player's zone is half cut in landscape at font scale 2 (seen in the
capture `archive_x01_failed_landscape_fr_font200`, reproduced on `main` at 7441ae3). The word must always be whole,
in portrait and landscape, at font scales 1.0, 1.3, 1.5 and 2.0.

## Scope and out of scope

- In scope: the X01 zone in CHECKOUT mode (`CheckoutBanner`, `NameRow` and the checkout line in
  `CurrentPlayerZone.kt`), the least zone height in CHECKOUT mode of `GameLayoutPolicy`, their tests and captures,
  and the wording of M15 / design system 6.3.
- Out of scope: the route bar "SORTIE"/"CHECKOUT" (`CheckoutRouteBar.kt`, not affected), the BUST banner, the
  Cricket "VICTOIRE" banner, the score size, the keypad and the action bar, strings (no text changes:
  `checkout_banner` stays non-translatable, so nothing in `values/` or `values-fr/`).

## Approach

1. **Measure first (T1).** Before any fix, a Robolectric probe in the failing configuration
   (`fr-land`, Robolectric default landscape device, font scale 2, and `fr-w800dp-h360dp-land-xxhdpi`) records, in
   the task's Implementation notes: the zone height and its `zoneMinHeightDp`, the chip height, the inner height
   left after its padding, the font size chosen by `TextAutoSize`, and the text layout height. It confirms or
   corrects these candidate causes (intake read the code only; nothing was measured):
   - a. the landscape policy takes `minOf(checkoutZoneMinHeight, scoreZoneMin)`, so the zone can be lower than the
     CHECKOUT least height (name + 40 dp chip + line + slots) it estimates for itself;
   - b. the name row and the checkout line grow with the font scale and the chip gets only the rest (`weight(1f)`),
     with no least height;
   - c. `TextAutoSize.StepBased` uses the default `minFontSize` (12 sp, so 24 dp at scale 2): when the chip is lower
     than that, the text overflows and is clipped by `clip(KosmoShapes.control)`.
   If the measurement shows another cause, T1 stops and reports to main before fixing (open question 1).
2. **Kosmo's rule (D1).** The chip keeps a least height, `CHECKOUT_BANNER_MIN` (40 dp, the value the policy
   already reserves, made `internal` and shared so the zone and the policy use one token). Its text has a least size
   that is not multiplied by the font scale (`fixedSp`), and is the largest step that fits the inner height of a
   40 dp chip (measured in T1, at least 24 dp expected). When the zone is too low for the name row, the chip at its
   least height and the checkout line at the user's font scale, the name row (name, average, chips) and the checkout
   line are drawn at a reduced text scale, the largest that fits, never below 1.0 (D2). The banner still grows up to
   1.5 x `banner` in landscape when there is room (6.3, unchanged).
3. **Where the computation lives.** A pure function `checkoutTextScale(available, fixed, textHeightsAtScale1,
   fontScale): Float` in `ui/components/CheckoutFit.kt` (no Compose type, JVM-tested): returns
   `clamp((available - fixed) / sum(textHeightsAtScale1), 1f, fontScale)`. The zone, in CHECKOUT mode only, measures
   its height (`BoxWithConstraints`), the heights of the name row and the line at scale 1 (`TextMeasurer`), and
   provides a `LocalDensity` with the reduced `fontScale` to `NameRow` and the checkout line only. Other modes are
   unchanged.
4. **Policy.** `checkoutZoneMinHeight` stays the estimate at the user's scale; the landscape `minOf` stays (U10:
   the zone gives height back). A new policy test proves that, for every supported screen and scale 1.0 to 2.0,
   `zoneMinHeightDp` in CHECKOUT mode is at least the zone frame at scale 1.0 + `CHECKOUT_BANNER_MIN` + gap + one
   checkout line at scale 1.0, so the floor of D2 always fits. If it does not, T1 raises the landscape least
   height to that floor (and reports it).
- Alternatives rejected: ellipsis or scrolling of the banner (refused by Kosmo, Q1); letting the banner shrink
  below 40 dp (option a, not chosen); a `SubcomposeLayout` for the whole zone (more code for the same result: the
  measured heights are linear in the font scale since every zone text style uses `lineHeight = 1.em` or sp).

## Files and types added or changed

- Added `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/CheckoutFit.kt` (`checkoutTextScale`).
- Changed `ui/components/CurrentPlayerZone.kt` (`CurrentPlayerZone`, `CheckoutBanner`: least chip height, least
  text size, reduced scale for `NameRow` and the line in CHECKOUT mode).
- Changed `ui/layout/GameLayoutPolicy.kt` (`CHECKOUT_BANNER_MIN` becomes `internal const`; floor only if T1's
  policy test needs it).
- Tests: added `ui/components/CheckoutFitTest.kt` (JVM), `ui/x01/CheckoutBannerFitTest.kt` (Robolectric); changed
  `ui/x01/ZoneLayoutTest.kt` (text height inside the chip), `ui/layout/GameLayoutPolicyTest.kt`; captures in T2.
- Docs (T2): `docs/rules/match.md` M15, `docs/design/design-system.md` 6.3.

## Data read, stored or exposed

None. No stored format, no permission, no string.

## Screens and states

X01 game screen, CHECKOUT mode (after the final dart, before "VALIDER LA MANCHE/LE SET/LA PARTIE"), including the
archive-failure state of #67. Portrait and landscape, 1 to 4 players, with and without the S/M chips. TalkBack text
and live region unchanged ("Checkout, 121 en 3 fléchettes"). No touch target changes (no button is touched); the
action bar keeps its sizes and the landscape 42 dp exception of CLAUDE.md is not used.

## Test plan

Rule identifiers: M15, CS8, design system 6.3, U10.

- `CheckoutFitTest` (JVM): enough room returns `fontScale`; no room returns 1.0 (floor); exact fit; `fontScale`
  1.0 always returns 1.0; result is monotonic in `available`; negative room returns 1.0.
- `CheckoutBannerFitTest` (Robolectric, X01 with ANN at 121, darts T20 T20 1, as in `ZoneLayoutTest`), written and
  run red **before** the fix, its failing output pasted in T1's notes. Parameters: `fr`, portrait
  `w411dp-h814dp-port-420dpi` and `w360dp-h640dp-port-xhdpi`, landscape the Robolectric default `+fr-land` and
  `w640dp-h360dp-land-xhdpi`, each at font scale 1.3, 1.5, 2.0. Assertions:
  - text layout height of `CHECKOUT_BANNER_LABEL`'s text <= chip inner height (chip minus vertical padding), one line,
    width <= chip width (the missing check that let the bug pass);
  - chip height >= `CHECKOUT_BANNER_MIN` - epsilon;
  - name, line, dart slots whole on screen and apart (`assertWhole`, `assertApart` of `ZoneLayoutTest`);
  - edge cases: 4 players (tiles row), a sets match (S/M chips), the archive-failure state (message in the right
    column).
- `ZoneLayoutTest` M15 landscape tests: add the text-height assertion.
- `GameLayoutPolicyTest`: the floor of Approach 4 for all screens already listed in it, scales 1.0, 1.3, 1.5, 2.0.
- Captures (T2, Roborazzi, record-only): CHECKOUT mode, `+fr-port`, `+en-port`, `+fr-land`, `+en-land`, each at
  1.0, 1.3, 1.5, 2.0 (16), plus `archive_x01_failed_landscape_fr_font200` re-recorded. Every other PNG is compared
  with `cmp` to `main` (7441ae3): only CHECKOUT-mode captures may differ; T2 lists them.

## How Kosmo verifies on the phone

Set the phone's font size to the maximum, start a 301 (or 501) game, bring a player to a checkout and throw the
final dart: the yellow "CHECKOUT" band shows the whole word, and the name, average and "121 EN 3 FLÉCHETTES" are
smaller than elsewhere if needed but whole. Same in portrait and landscape, then at a medium font size. At normal
font size nothing changes.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Measure, failing fit test, fix the CHECKOUT zone fitting | `69-checkout-label-clipping/T1-fit-fix.md` | - | sonnet | Specified UI fix with listed JVM and Robolectric tests |
| T2 | Captures, capture comparison, M15 and 6.3 wording | `69-checkout-label-clipping/T2-captures-docs.md` | T1 | sonnet | Captures need judgment on which PNG may change |

No `opus` task.

## Decisions

- D1 (Kosmo, Q1 = b): the CHECKOUT banner keeps a least readable height; the name, average and checkout line shrink
  instead. No ellipsis, no scrolling.
- D2 (proposed, to confirm in design review): the name row and the line never go below their size at font scale
  1.0. Reason: they stay at least as readable as for a user with the default font size.
- D3 (Kosmo, Q2): covered at font scales 1.3, 1.5 and 2.0, portrait and landscape, X01 pending checkout.
- D4: one shared token, `CHECKOUT_BANNER_MIN` (40 dp), for the policy and the zone; the least text size uses
  `fixedSp` (design system tokens, no new magic number in the composable).
- D5: the failing test is written and run red before the fix, inside T1 (a task never hands over a red build).

## Open questions

1. If T1's measurement shows a cause other than a, b or c, T1 stops and main decides whether the approach still
   applies.
