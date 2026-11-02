# ADR 0013: Save formats version 2: ARGB player colors

- Status: Proposed (design review of issue #51); accepted when its PR is merged. Amends ADR 0011: the saved game and
  the last setup keep every line of their version 1 layout; only the `colors` value and the version number change.

## Context

Issue #51 lets each player pick any acceptable color (PC11 to PC15 of `docs/rules/players.md`), not only one of the
four palette colors. Version 1 of both files (ADR 0011, the `colors` line of ADR 0006) stores a slot number 1 to 4
per player: it cannot hold a generated color. Rules R17 and R31 of `docs/rules/persistence.md` require the saved game
and the last setup to keep every player's color, so that a resume, a rematch (PC7) and the next setup (PC8) keep it.

## Options

1. Keep the slot numbers and add a second line for generated colors. Two sources of truth for one color, and a
   reader must merge them.
2. Store every color as ARGB in the existing `colors` line, at a new version number (chosen).

## Decision

- Both files move to `version=2`. Each codec still accepts exactly one version (`GameSaveCodec.VERSION`,
  `LastSetupCodec.VERSION`).
- The `colors` line keeps its key and its place in each layout. Its value is one color per player, in player order,
  separated by single spaces: 8 uppercase hex digits, ARGB, alpha `FF` (for example `colors=FF1F63D6 FFF2D94E`).
- On read (`parseColors` in `persistence/SaveColors.kt`):
  - a wrong number of tokens or an empty token (double space, trailing space, empty value) is `MALFORMED`;
  - a token that is not `FF` followed by 6 characters of `0-9A-F` (lowercase, a sign, a `0x` prefix, 6 or 9 digits,
    another alpha, a slot number of version 1) is `OUT_OF_RANGE`;
  - two equal colors are `OUT_OF_RANGE` (PC1).

  The saved game is then rejected with the R9 message and deleted; the last setup is ignored silently (R10). Nothing
  throws out of the decoder (R11).
- Only format, opacity and uniqueness are checked on load. PC11 to PC13 (contrast, distance) are not checked again
  (decision D15 of the plan of #51): their thresholds may change later, and a resume must never fail for a color that
  was valid when chosen. A file with opaque, distinct colors such as `FF000000` is accepted as it is.
- Writing uses `String.format(Locale.ROOT, "%08X", argb)`: the written form is the only accepted form, so every valid
  file re-encodes to the same bytes (the round trip checked by the fuzz test).

### Version 1 files are not read

The app has no release that can be updated yet, so R30 (clean slate, owner decisions D24 and D25 of issue #50) still
applies: a version 1 file is not converted. It fails the version check like any unknown version
(`UNSUPPORTED_VERSION`): a saved game gives the R9 message once and is deleted, a last setup is ignored (R10, plan
decision D9). Converting slot numbers would be easy, but nobody has such a file: the app is always installed on empty
storage before the first release.

## Consequences

- A saved game or last setup written by an earlier build of this branch or of `main` is lost once, as R30 accepts.
- From the first release that can be updated, R30 no longer applies: the next change to either file must read
  version 2 and convert it, with its own ADR and tests.
- ADR 0006 (slot numbers) is historical only, like the other ADRs listed in ADR 0011.
