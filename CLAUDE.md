# Kosmo Darts - project conventions

Owner-maintained file (protected by CI). Sandbox rules come from the managed policy and always take precedence.
To suggest a change here, use the "Proposed owner changes" section of your PR description.

## Product

- Offline Android app to keep score of darts games between friends, on a single phone.
- MVP: 501 and 301 (optional double-out), then Cricket. "Around the clock" later.
- 1 to 4 players, manual input (keypad or board), undo of the last dart.
- Games in progress and history stored locally on the device. No account, no server.
- Later milestone: automatic dart detection with the camera, feeding the game engine through
  a "list of impacts" interface. Do not start camera work until the owner asks for it.

## Technical baseline

- Kotlin, Jetpack Compose, Material 3, dark theme. Single activity.
- Gradle Kotlin DSL and a version catalog (`gradle/libs.versions.toml`). Dependency and plugin versions live only
  in the catalog. SDK levels and `buildToolsVersion` required by the sandbox policy are set directly in the build file.
- Application id and namespace: `io.github.darkcosmos.kosmodarts`. `minSdk = 30`, `targetSdk = 37`.
- Modules:
  - `:game-engine`: pure Kotlin/JVM module with all game rules (scores, checkouts, turns, undo).
    No Android dependency, ever.
  - `:app`: Android UI and platform code only. It depends on `:game-engine`, never the other way round.
- Keep the dependency list minimal. Justify every new dependency in the PR description.

## Privacy and offline

- The app must work fully offline. Do not declare the `INTERNET` permission.
- No analytics, advertising, crash-reporting or tracking SDKs.

## User-facing text

- No hardcoded user-facing strings in code: use resources.
- `values/` is English (default, fallback), `values-fr/` is French. Always update both in the same PR.
- Accessibility: content descriptions on icons, touch targets of at least 48dp (exception: in landscape, buttons may be 42dp tall), sufficient contrast in dark theme.

## Tests

- Every game rule has JVM unit tests in `:game-engine`, written with or before the rule.
- A bug fix starts with a failing test that reproduces it.
- UI logic that can be tested without a device lives outside composables (state holders, ViewModels).

## Code style

- Kotlin official code style. Small functions, explicit names, immutable data where possible.
- Do not suppress warnings without a comment explaining why.

## Verification

- Kosmo verifies every PR on his Pixel 7 with the debug APK built by the CI for the latest commit. He uninstalls the
  previous version first: the debug key changes on every CI build. "How to verify" steps start from that fresh install.

## Review focus

Project-specific points the squad reviewers check in addition to their own checklist.

- Tests: game rules have JVM tests in `:game-engine` that cite the rule identifiers of `docs/rules/` (new identifiers
  are added there). Edge cases to list for changed behavior: bust, double-out and single-out, checkout dialog, undo
  at every step, 1 to 4 players, 501 and 301, restart, and process death when state must survive it.
- Architecture: a game is its configuration plus the ordered list of darts; undo, persistence, statistics and camera
  input all derive from this single source of truth. Counting or filtering game data in a composable (for example the
  180s of a player) is a blocking finding; game-rule values (180, 501, double-out) are named in `:game-engine`. Side
  effects go through explicit hooks such as the "turn completed" hook of the ViewModel. Next milestones: voice
  announcements, look and feel, then the camera milestone.
- Security: offline and private (see above); Android backup stays disabled (`allowBackup="false"` and the data
  extraction rules) until the owner decides otherwise; the camera permission, later, is requested at the point of
  use. A saved game that breaks the rules (for example a dart after the win) is rejected or migrated, never replayed.
- UX: a player standing at the dartboard, phone in one hand, in French or English, in portrait or landscape. The
  current player, remaining score and darts left stay readable at arm's length; clear feedback for bust, checkout,
  turn end and undo; few taps per dart and per turn; undo always reachable during a game; ending or leaving a game is
  confirmed; 1 to 4 players and long names fit (known issue: the list can overflow at 3 to 4 players in portrait).

## Git

- Branch names: `feat/...`, `fix/...`, `chore/...`, `build/...`, `test/...`, `docs/...`.
- PRs are squash-merged by the owner: write the PR title as the final commit message.
