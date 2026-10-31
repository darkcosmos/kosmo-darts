# T5: Copy theme and keypad into the module

## Goal
The X01 keypad, identical in look, inside `:capture`, with its dark theme, without any dependency on `:app`.

## Files
- Copied from `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/theme/` into `capture/.../capture/ui/theme/`:
  only the files the keypad and the screen need (colors, typography, shapes, `FixedSp`, `Theme`); drop player
  palette and animation settings unless required.
- Copied from `app/.../ui/game/Keypad.kt`, `KeypadKeys.kt`, `KeypadTags.kt` into `capture/.../capture/ui/keypad/`:
  replace `AppEvent` with a capture event (`onDart(Dart)`), keep `Dart`/`Multiplier` from `:game-engine`.
- Strings and fonts the keypad uses copied to `capture/src/main/res/values/` and `values-fr/` (and `font/` if any).
- Header comment in each copied file: "Copied from :app (issue #41, decision Q11); not kept in sync."
- Not changed: `app/`.

## Depends on
T1.

## Rules
Engine rule D2 (no triple bull, no double outer bull) as already enforced by the keypad. Decision Q11.

## Tests
- Robolectric: tapping T then 20 emits `Dart.triple(20)`; `25`, `Bull`, `Miss` emit segments 25, 50, 0; the triple
  key is disabled for bull keys as in `:app`; a disabled keypad emits nothing.
- Keys keep the `:app` landscape policy: 42dp tall (decision Q14), labels in `fixedSp` as in `:app`.
- Roborazzi capture of the keypad alone, English and French, enabled and disabled.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0, `git diff origin/main -- app` empty.

## Model
sonnet. Why: copy with small adaptations (event type, resources).

## Implementation notes
