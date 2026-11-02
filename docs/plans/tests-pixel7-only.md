# Tests on the Pixel 7 only

## Goal

Mission asked by Kosmo directly, without a GitHub issue: the test suite only targets the smallest supported screen,
the Pixel 7, in portrait and landscape, at every font scale already tested. Screens smaller than a Pixel 7 (for
example 360 x 640 portrait, 640 x 360 landscape, Robolectric's "small" or default devices) are out of scope for ever.
No app behaviour changes: this mission only changes tests and documents.

## Scope and out of scope

In scope:
- Every Robolectric test and Roborazzi capture of `:app` and `:capture` that runs on a size smaller than the Pixel 7,
  including the tests that name no size and so run on Robolectric's default device (320 x 470 dp, mdpi).
- Named test devices shared by the tests (`TestDevices`), so the supported sizes are written once.
- The list of production code that exists only for smaller screens (section "Production code for smaller screens"),
  as a proposal only.

Out of scope:
- Removing or changing production code (layout policies, scroll fallbacks): proposed for a later mission.
- Font scales: every font scale tested today stays (1.0, 1.3, 1.5, 2.0).
- Tall test windows of Pixel 7 width used to keep long lists unvirtualised (`w411dp-h2400dp`, `w411dp-h3600dp`):
  they are not smaller than the Pixel 7 and stay.
- `:game-engine` (pure JVM, no screen).
- Old plan files (historical), `.github/**`, `CLAUDE.md`, `.claude/**`.

## Reference sizes

`RobolectricDeviceQualifiers.Pixel7` (Roborazzi 1.76.0, read with `javap`) is
`w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav`: 411 x 914 dp portrait, 914 x 411 dp landscape
(Robolectric swaps width and height for `land`). Robolectric draws no system bars, so the app gets the whole screen.

On the real phone the app gets less: the plan of the Cricket rework (#40, section "Reference device") measured the
Pixel 7 bars as upper bounds, and the owner review of PR #57 named the Pixel 7 landscape inside its bars and cutout
"about 800 x 360 dp". Two sizes of the suite stand for that real usable area and are **the Pixel 7, not a smaller
phone** (Q1, D4):
- portrait inside its bars: `w411dp-h814dp-port-420dpi` (914 - 52 status - 48 navigation);
- landscape inside its bars and cutout: `w800dp-h360dp-land-xxhdpi`.

Every size of the inventory is classified against these four: smaller than the Pixel 7 inside its bars in width or
height means "smaller than the Pixel 7".

## Inventory

Tests counted with `@Test` per class; "capture" means a Roborazzi PNG recorded under `build/outputs/roborazzi`
(record only, never compared; no PNG reference is tracked in git: `git ls-files '*.png'` lists only the 10 mockups of
`docs/design/mockups`).

Size groups found (`grep -rnE 'qualifiers' app/src/test capture/src/test`):

| Group | Size (dp) | Where | Smaller than Pixel 7? |
|---|---|---|---|
| S1 | 360 x 640 portrait, xhdpi (Robolectric `SmallPhone`) | `:app` | yes |
| S2 | 640 x 360 landscape, xhdpi (`:app`) and xxhdpi (`:capture`) | `:app`, `:capture` | yes |
| S3 | 393 x 852 portrait, xxhdpi | `:app` | yes (width) |
| S4 | 411 x 760 portrait, 420 dpi | `:app` | yes (height, under 814) |
| S5 | 731 x 411 landscape, xxhdpi | `:app` | yes (width) |
| S6 | no size: Robolectric default 320 x 470 mdpi (`+fr-land`: 470 x 320) | `:app` | yes |
| K1 | 411 x 814 portrait / 800 x 360 landscape | `:app` | no: Pixel 7 inside its bars (Q1, D4), kept |
| K2 | 412 x 915, 915 x 412 (xxhdpi), 914 x 411 (420 dpi) | `:app`, `:capture` | no: Pixel 7 rounded, moved to the Pixel 7 class |
| K3 | 411 x 2400, 411 x 3600 | `:app` | no: tall windows for lists, kept |

Per test, with what it protects besides the size and its disposition (D5: a rule keeps at least one Pixel 7 test):

| File (tests on a smaller size) | Group | Rule protected | Pixel 7 test already there? | Disposition | Task |
|---|---|---|---|---|---|
| `GameScreenFitTest`: U10 action bar 360x640 (x2), 640x360 (x1), U10 checkout confirm 360x640 | S1, S2 | U10 action bar and confirm whole, 4 players | only at the full screen (412x915, 915x412), not inside the bars; no checkout confirm | retarget all four: bar to 411x814 (4 players, scale 1 and 2) and 800x360 (4 players, scale 1 and 2, scale 1 added); checkout confirm to 411x814 | T2 |
| `GameScreenFitTest`: T9 touch targets 360x640 (x2), 640x360 (x1) | S1, S2 | T9 keys and bar at least 48 dp | none | retarget to 411x814 (scale 1, 2) and 800x360 (scale 2) | T2 |
| `GameScreenFitTest`: U11 labels 640x360, 360x640; U12 header 360x640 | S1, S2 | U11 no break inside a word, U12 header two lines at most | none | retarget to 800x360 and 411x814, scale 2 | T2 |
| `RedesignScreensTest`: U1 small portrait / landscape at 1.0 and 2.0 (x4) | S1, S2 | U1 zone and every key reachable | none | retarget to 411x814 and 800x360 | T2 |
| `RedesignScreensTest`: T5 zone does not move with a route | S1 | T5, CS8 portrait | landscape only (U3 test) | retarget to 411x814 | T2 |
| `RedesignScreensTest`: U7 Start visible under the focused field, scale 2 | S1 | U7 | none | retarget to 411x814 | T2 |
| `CheckoutBannerFitTest`: M15 360x640 at 1.0/1.3/1.5/2.0 (x4) | S1 | M15 banner whole on one line | yes, inside the bars: 411x814 at the same scales | delete | T2 |
| `CheckoutBannerFitTest`: M15 640x360 at 1.0/1.3/1.5/2.0 (x4) | S2 | M15 | yes, inside the bars: 800x360 at 1.0 and 2.0 | delete; add 800x360 at 1.3 and 1.5 | T2 |
| `CheckoutBannerFitTest`: M15 sets match, long name (360x640, 640x360 at 2.0, x4), undo restores name row (640x360) | S1, S2 | M15 with S/M chips, long name; T4 undo | none | retarget to 411x814 and 800x360 at 2.0 | T2 |
| `CheckoutBannerFitTest`: M15 `+fr-land` at 1.0/1.3/1.5/2.0 (x4) | S6 | M15 | yes, inside the bars: 800x360 (and 1.3, 1.5 added above) | moves to the full Pixel 7 landscape by T1 and stays; `frland` pre-fix value dropped (D6) | T1, T2 |
| `VisitTotalLayoutTest`: VT11 narrower phone 393x852 at 2.0 | S3 | VT11 | only at the full screen (Pixel 7 portrait and landscape, scale 1 and 2) | retarget to 411x814 at 2.0, renamed "VT11 Pixel 7 portrait inside its bars at the largest font scale" | T2 |
| `DesignSystemScreenshotTest`: `game_4p_small_360x640_fr`, `_font200`, `game_4p_small_640x360_fr`, `_font200` | S1, S2 | captures U1 | `game_4p_route_800x360_fr` (scale 1, route, not the long-name case); others at the full screen | retarget the 4 to 411x814 and 800x360, renamed `ds_game_4p_411x814_fr`, `_font200`, `ds_game_4p_800x360_fr`, `_font200` | T2 |
| `DesignSystemScreenshotTest`: `setup_4p_focused_small_360x640_fr_font200` | S1 | capture U7 | none | retarget to 411x814, renamed `setup_4p_focused_411x814_fr_font200` | T2 |
| `GameLayoutPolicyTest` (pure JVM): steps at 360x640 and 640x360, 360x640 inputs in 3 other tests | S1, S2 | step order 1-2-3-4, scroll fallback, 48 dp floor | Pixel 7 inputs in the same tests | D7 | T2 |
| `ScreenLayoutPolicyTest` (pure JVM): `hasTwoColumns(640, 360)`, `(360, 640)` | S1, S2 | two-column threshold | yes, inside the bars: 800x360 (and 412x915, 915x412) | delete the two smaller inputs | T2 |
| `CricketEndScreenTest` class at 360x640 (6 tests: CR26, CR27 at 1.0 and 2.0, CR28, next leg) | S1 | CR26, CR27 fit with 4 players | no | class device to 411x814, all kept | T3 |
| `CricketScreenTest`: CR22 two-line zone at 411x760, scale 2 | S4 | CR22 fallback (two-line zone, MPR on the name line) | no: unreachable at 411x814 | D8: delete, keep the policy test; T3 confirms a 4-player CR22 test at scale 2.0 exists at 411x814 and at 800x360, and adds the missing one | T3 |
| `CricketBoardLayoutPolicyTest` (pure JVM): "small screen" scroll (portrait, landscape 360 high), fixtures measured at 360 dp wide | S1, S2 | CR22 fallback (scroll, 42 dp rows) | Pixel 7 inputs exist | D7 | T3 |
| `CricketScreenshotTest`: `between_legs_4p_360x640_fr`, `end_4p_360x640_fr` | S1 | captures CR26, CR27 | only at the full screen (class device Pixel 7): `between_legs_4p_portrait_fr_font200`, `end_4p_portrait_fr_font200`, `between_legs_4p_landscape_fr` | retarget both to 411x814, renamed `cricket_between_legs_4p_411x814_fr`, `cricket_end_4p_411x814_fr` | T3 |
| `SetupSuggestionsScreenTest` class at 360x640 (4 tests) | S1 | profile suggestions under the focused field | no | class device to Pixel 7 portrait, all kept | T3 |
| `HistoryScreenTest` HS2 U3, `MatchDetailScreenTest` HS5 U3 at 731x411, scale 2 | S5 | HS2, HS5 last item reachable in landscape | no | retarget to 800x360 | T3 |
| UI classes with no size (S6): `AnnouncementsScreenTest` (18), `ArchiveFlowTest` (13), `CricketResumeScreensTest` (8), `HomeScreenTest` (6), `OwnerEvolutionsScreenTest` (16), `PersistenceScreensTest` (24), `SettingsScreenTest` (17), `VisitTotalScreensTest` (4), `VisitTotalResumeTest` (5), `VisitTotalFrenchScreensTest` (1), text-only classes | S6 | their own rules (none about size) | n/a | Pixel 7 by default (T1), all kept | T1 |
| `ZoomRowTest` (`:capture`): 640x360 zoom row 42 dp, preview 160 dp, slider 96 dp en/fr (x4) | S2 | capture zoom row layout, U17 | 915x412 for row and slider | retarget to 800x360 | T4 |
| `CaptureActionRowTest` U11 at 640x360 | S2 | U11 Undo and Next volley whole | only at the full screen (915x412) | retarget to 800x360 | T4 |
| `CaptureScreenshotsTest`: `640x360_en`, `640x360_fr` | S2 | captures | only at the full screen (915x412) | retarget the 2 sets to 800x360, renamed `800x360_en`, `800x360_fr` | T4 |

## Production code for smaller screens

Listed for a later decision, **not changed in this mission**: none is trivially dead (P1 step 4 and P2 are live code
on the Pixel 7, see the last column).

| Id | Code | What it does | Reached on the Pixel 7? |
|---|---|---|---|
| P1 | `GameLayoutPolicy` step 4 (`isScrolling`, `SCORE_FLOOR_*`) and the scroll of `GameScreen`; test helper `RevealNode` | the X01 game screen scrolls below the score floor | yes: step 4 is reached at 411x814 at scale 2.0 (T2 records it, D7) |
| P2 | `CricketBoardLayoutPolicy.scrolls` and the `verticalScroll` of `CricketBoard` | the Cricket rows scroll ("a screen smaller than the Pixel 7", KDoc) | yes in landscape: at 800x360 (Pixel 7 inside its bars), scale 2.0, 7 rows when the bull is in play (T3 measured it; the KDoc is wrong for landscape) |
| P3 | `CricketBoardLayoutPolicy` `showsTotal = false` (two-line zone, R2.1) | Cricket zone without the big total | no Robolectric test reaches it at a supported size; proven only by the policy tests (T3) |
| P4 | `ScreenLayoutPolicy` and `ScrollingScreen` scroll | other screens scroll, buttons pinned | yes at font scale 2.0: not a fallback, keep |

Proposal: Kosmo answered Q2 (D9), so a separate mission may remove P3 with its policy tests; only the default
display size of the Pixel 7 is supported. **P1 step 4 and P2 must not be removed** by that mission: the Pixel 7 inside
its bars reaches both (P1 at 411x814 scale 2.0, P2 at 800x360 scale 2.0), they are not fallbacks.

## Approach

- A1. One file of named devices per test source set (`TestDevices`, `object` of `const val String`):
  `PIXEL7_PORTRAIT` = `w411dp-h914dp-port-420dpi`, `PIXEL7_LANDSCAPE` = `w914dp-h411dp-land-420dpi`,
  `PIXEL7_PORTRAIT_INSIDE_BARS` = `w411dp-h814dp-port-420dpi`, `PIXEL7_LANDSCAPE_INSIDE_BARS` =
  `w800dp-h360dp-land-xxhdpi`, plus `FR_` / `EN_` variants built by `const` concatenation (`"fr-" + PIXEL7_LANDSCAPE`).
  The sizes are literals with a KDoc naming `RobolectricDeviceQualifiers.Pixel7` of Roborazzi 1.76.0 as their source:
  that constant is a compile-time `const` (javap shows its `ConstantValue`; it is already used in `@Config`), but
  appending `-port` / `-land` to it would put the orientation after `-nonav`, an invalid qualifier order. Classes that
  only need the full Pixel 7 with `+fr-land`-style method configs keep `RobolectricDeviceQualifiers.Pixel7`.
  Alternative: keep string literals in every test (today's state): rejected, the same sizes are spelled 5 ways.
- A2. `robolectric.properties` of `:app` sets `qualifiers=` the Pixel 7, so a test that names no size runs on the
  Pixel 7. Alternative: a class `@Config` on every UI class: rejected, easy to forget in a new class. `:capture` gets
  no default: every Robolectric UI test there already names its size (checked: `CaptureScreenTest` and
  `KeypadScreenTest` at class level, every test of `ZoomRowTest` (18), `CaptureActionRowTest` (2) and
  `CaptureScreenshotsTest` (10) at method level); T4 re-checks it with the final grep below.
- A3. Each smaller-size test is deleted only when a test of the same rule runs at the Pixel 7 **inside its bars**
  (411x814 or 800x360, the tightest supported sizes); a test at the full screen (411x914, 914x411 and the rounded
  412x915, 915x412) does not count, because the real phone gives the app less. Otherwise it is retargeted to the
  inside-bars size of its orientation with its rule identifier and assertions kept. The same applies to captures.
- A4. Pure JVM policy tests: D7.

## Files and types added or changed

- Added: `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/TestDevices.kt`,
  `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/TestDevices.kt` (`object` of `const val String`,
  usable in `@Config`).
- Changed: `app/src/test/resources/robolectric.properties`; the test files listed in the "Inventory" table and in the
  task files. No production file, no resource, no dependency.

## Data read, stored or exposed

None. Tests only.

## Screens and states

No screen changes. The captures change as listed in "Test plan".

## Test plan

- No production behaviour changes, so no new rule test; every rule in the "Inventory" keeps at least one Pixel 7 test.
- Retargeted tests keep their assertions (never weakened). A test that turns red once moved to the Pixel 7 is a
  finding: the build agent stops and reports to main (it may be a real bug on the Pixel 7).
- Checks per task: `./gradlew build` green and `kosmo-detekt` exit 0.
- T1 proves the default device applies: a temporary assertion of 411 x 914 dp on the screen of one class without a
  size (for example `HomeScreenTest`), run red-then-green by changing the expected value, then reverted file by file;
  the task notes name the check and its result.
- Final grep (T4), each prints nothing:
  - `grep -rnE 'w(360|640|393|731)dp|h760dp|SmallPhone' app/src/test capture/src/test`;
  - `grep -rnE 'RobolectricDeviceQualifiers\.' app/src/test capture/src/test | grep -v 'RobolectricDeviceQualifiers.Pixel7\b'`;
  - in `:capture`, every class using `createComposeRule` / `createAndroidComposeRule` has a class `@Config` with a size,
    or each of its tests has one (T4 lists them).
- Test classes before and after: each build task reports the list of test classes it touched with their test counts
  before and after, and confirms that no test of persistence, corrupt-save handling or input validation was removed
  (security note of the design review). T4 compares the full class lists of `:app` and `:capture` (JUnit XML file
  names) before and after: no class disappears.

### Measures

Before: `main` at 2f9e437, command `time ./gradlew build --rerun-tasks` on the sandbox. After: T4 runs the same.

| Measure | Before | After (T4) |
|---|---|---|
| Tests `:app` (JUnit XML, `testDebugUnitTest`) | 1595 (0 skipped, 0 failures) | 1590 (0 skipped, 0 failures) |
| Tests `:capture` | 225 | 225 (0 skipped, 0 failures) |
| Tests `:game-engine` | 424 (unchanged expected) | 424 |
| Captures `:app` (PNG under `app/build/outputs/roborazzi`) | 279 (expected after: 279, 7 renamed) | 279 |
| Captures `:capture` | 68 (expected after: 68, 20 renamed) | 68 (20 renamed to `800x360`, plus 44 moved from `915x412` to `914x411`, see T4 notes) |
| `./gradlew build --rerun-tasks` wall time | 5 min 50 s (BUILD SUCCESSFUL, 205 tasks executed) | 5 min 27 s (BUILD SUCCESSFUL, 205 tasks executed) |
| `kosmo-detekt` exit code | 0 | 0 |

Captures on a smaller size, as recorded at 2f9e437 (`find */build/outputs/roborazzi -name '*.png'`): none is
deleted, all 27 move to the Pixel 7 inside its bars (A3: the Pixel 7 captures of the same screens are full-screen
only) and are renamed with the new size:
- `:app` (7): `ds_game_4p_small_360x640_fr` and `_font200` to `ds_game_4p_411x814_fr` and `_font200`;
  `ds_game_4p_small_640x360_fr` and `_font200` to `ds_game_4p_800x360_fr` and `_font200`;
  `ds_setup_4p_focused_small_360x640_fr_font200` to `ds_setup_4p_focused_411x814_fr_font200`;
  `cricket_between_legs_4p_360x640_fr` and `cricket_end_4p_360x640_fr` to `cricket_between_legs_4p_411x814_fr` and
  `cricket_end_4p_411x814_fr`;
- `:capture` (20): `capture_<state>_640x360_en` and `_fr` to `capture_<state>_800x360_en` and `_fr`, for the 10 states
  `delete_confirm`, `error`, `idle`, `permission_denied`, `permission_missing`, `photo_failure`, `running`, `saving`,
  `stop_confirm`, `volley_full`.

No capture comes from a test without a size (S6): every screenshot class already names the Pixel 7.

## How Kosmo verifies on the phone

Nothing changes on the phone: no screen, text or behaviour of the app changes. Kosmo checks the pull request
description: the before/after table above, and that the CI build is green. In the screenshot artifact of the CI run,
the 27 images that were at sizes smaller than the Pixel 7 (`360x640`, `640x360`) are now at the Pixel 7 sizes inside
its bars (`411x814`, `800x360`); no image is at a size smaller than the Pixel 7.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Pixel 7 as the default test device | `tests-pixel7-only/T1-pixel7-default-device.md` | none | `sonnet` | configuration and wiring with a red-test check |
| T2 | X01 game tests on the Pixel 7 only | `tests-pixel7-only/T2-x01-tests.md` | T1 | `sonnet` | test edits from the table, per-test judgement |
| T3 | Cricket, setup and History tests on the Pixel 7 only | `tests-pixel7-only/T3-cricket-setup-history-tests.md` | T1 | `sonnet` | test edits from the table, per-test judgement |
| T4 | Kosmo Capture tests and the after measures | `tests-pixel7-only/T4-capture-tests-and-measures.md` | T1, T2, T3 | `sonnet` | test edits and measures, no design choice |

`opus` tasks: 0.

## Decisions

- D1 (Kosmo): the smallest supported screen is the Pixel 7, portrait and landscape; smaller screens are out of scope
  for ever; font scales stay.
- D2: the Pixel 7 reference is `RobolectricDeviceQualifiers.Pixel7` (411 x 914 dp, 420 dpi); the near values
  412 x 915 and 915 x 412 move to it (a test red after that 1 dp change is reported, not adjusted).
- D3: `TestDevices` is duplicated in `:capture` (4 lines) rather than shared: `:capture` does not depend on `:app` and
  a shared test fixture module would be a new module for four constants.
- D4 (Kosmo, Q1 = keep): 411 x 814 (portrait) and 800 x 360 (landscape) are the Pixel 7 inside its bars and stay as
  Pixel 7 sizes.
- D5 (design review U1, T3, T4): no rule loses its last test at the Pixel 7 inside its bars: a smaller-size test or
  capture is deleted only when a test of the same rule runs at 411 x 814 or 800 x 360; full-screen tests do not
  count (A3). Hence the U10 action-bar tests, VT11 at 393 x 852, the design-system and Cricket 4-player captures and
  the `:capture` 640 x 360 tests and captures are retargeted, not deleted.
- D6: `CheckoutBannerFitTest` drops the `frland` pre-fix value (it was measured on Robolectric's 470 x 320 default
  device); the pre-fix guard of #69 stays at 411 x 814 and 800 x 360.
- D7: pure JVM policy tests: a smaller-size input moves to the Pixel 7 inside its bars when that size still reaches the
  branch under test (the existing "branch reached" assertions prove it); when no supported size reaches it, the input
  stays with a one-line comment naming P1, P2 or P3, because the branch is still production code. It goes with that
  code if a later mission removes it.
- D8: the Robolectric CR22 fallback test at 411 x 760 is deleted (no supported size reaches P3; P2 is reached, see
  "Production code for smaller screens"); the fallback stays
  proven by `CricketBoardLayoutPolicyTest` under D7 until P3 is decided. T3 confirms that `CricketScreenTest` keeps a
  4-player test at scale 2.0 with the CR22 fit assertions at 411 x 814 and at 800 x 360, and adds the missing one
  (design review U2).
- D9 (Kosmo, Q2 = default only): only the default display size of the Pixel 7 is supported; a larger "Display size"
  setting is out of scope like smaller phones. P1 to P3 stay in this mission; a later one may remove P3 only (P1 step 4 and P2 are reached on the Pixel 7 inside its bars).
- D10 (design review A1): `TestDevices` sizes are literals sourced from Roborazzi 1.76.0 `Pixel7` (see A1).
- D11 (design review A2): `:capture` gets no `robolectric.properties` default; all its UI tests name their size.
- D12 (design review T1, T2): T1 proves the default device with a temporary, reverted assertion; the final grep also
  covers `SmallPhone`, other `RobolectricDeviceQualifiers` devices and size-less configs in `:capture`.
- D13 (design review, security note): each build task reports the test classes it touched with counts before and
  after, and confirms no persistence, corrupt-save or input-validation test was removed.

## Open questions

None. Q1 (keep 411 x 814 and 800 x 360 as the Pixel 7) and Q2 (default display size only) were answered by Kosmo:
see D4 and D9.
