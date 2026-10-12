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
- Gradle Kotlin DSL and a version catalog (`gradle/libs.versions.toml`). No versions hardcoded in build files.
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
- Accessibility: content descriptions on icons, touch targets of at least 48dp, sufficient contrast in dark theme.

## Tests

- Every game rule has JVM unit tests in `:game-engine`, written with or before the rule.
- A bug fix starts with a failing test that reproduces it.
- UI logic that can be tested without a device lives outside composables (state holders, ViewModels).

## Code style

- Kotlin official code style. Small functions, explicit names, immutable data where possible.
- Do not suppress warnings without a comment explaining why.

## Git

- Branch names: `feat/...`, `fix/...`, `chore/...`, `build/...`, `test/...`, `docs/...`.
- PRs are squash-merged by the owner: write the PR title as the final commit message.
