# ADR 0001: Save format of the game in progress and of the last setup

- Status: Proposed (design review of issue #30; becomes Accepted when the persistence PR is merged)
- Date: 2026-10-18
- Rules: `docs/rules/persistence.md` (R8, R9, R10, R11, R12), `docs/rules/x01.md` (P1)

## Context

Issue #30 makes the game in progress and the last setup survive process death, force stop and reboot. The app
stores two small files in `Context.filesDir`, written with `android.util.AtomicFile`.

What must be stored is small and already defined by the engine. X01 rule P1 says a game is fully defined by its
configuration and its ordered list of darts. One thing is not derivable from that: whether the last completed turn
is still waiting for "Next player" (see "Stored fields" below). Sizes: at most 4 names of at most 20 code points, and
a list of darts (typically 30 to 200, bounded at 10,000 here).

Loading must be bounded, strict and versioned (issue #30, T3). Any invalid content is rejected as a whole, never
repaired (R9, R10). No input may crash the app (R11).

Issue #30 (T5) asks to compare a hand-written format with kotlinx.serialization on five criteria: dependency surface,
parsing strictness, versioning and migration, test effort, and code size.

## Options

### A. Hand-written line-based text format (no dependency)

UTF-8 text with a fixed sequence of `key=value` lines. It is parsed by about 150 lines of Kotlin in the `:app`
module, with no Android import, so the parser is testable with plain JUnit.

### B. kotlinx.serialization JSON

The candidate versions were checked on 2026-10-18 against the official sources:

| Name | Version | Official source checked |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 (latest stable; 1.12.0-RC is a release candidate) | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml, https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.11.0 ("based on Kotlin 2.3.20") |
| `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 (= the project's Kotlin version) | https://repo1.maven.org/maven2/org/jetbrains/kotlin/plugin/serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin/maven-metadata.xml |

### Also considered and rejected without a full comparison

- **`org.json`** (built into Android): lenient. For example, it accepts duplicate keys in some versions and coerces
  types. It is also missing from plain JVM tests: the Android stub jar throws there.
- **`java.util.Properties`**: lenient. It silently accepts duplicate keys, has no line order, and uses ISO-8859-1
  escapes.
- **Jetpack DataStore, Room or SQLite**: new dependencies, or more machinery than two small files need. They also
  cannot be used with `AtomicFile`, which issue #30 (T2) requires.

## Comparison

| Criterion | A. Hand-written | B. kotlinx.serialization JSON |
|---|---|---|
| Dependency surface | None. Uses only the JDK and Android APIs already available (`java.util.Base64`, `java.util.zip.CRC32`, `java.nio.charset`). | One compiler plugin, whose version must equal the Kotlin version, so every Kotlin upgrade moves it too. Two runtime artifacts (`-core`, `-json`) ship in the APK (not minified: `isMinifyEnabled = false`). The runtime is built with an older Kotlin (2.3.20) than ours (2.4.21), which works but adds one more pair of versions to keep compatible. |
| Parsing strictness | Complete and explicit. One canonical encoding per value: exact key order, no duplicate or unknown key, canonical numbers, LF only, mandatory final newline and end marker, checksum. | The defaults are strict on syntax (unknown keys rejected, lenient mode off). Duplicate keys are not rejected (the last one wins). Canonical forms (key order, number forms) are not checked. Range checks are needed anyway, after decoding. |
| Versioning and migration | An explicit `version=` line read before anything else. One decoder per supported version, plus a pure migration function to the current model. | A version field in the object. Reading it before the full decode needs a two-step parse (`JsonElement`, then a typed decode) and one class per version. Migration is the same amount of work as in A. |
| Test effort | Every rejection path is ours, so each has a test. That is needed anyway for R9 and R11: the issue asks for every invalid case. | The same R9 and R11 tests are still needed (behaviour, not library internals). There are fewer encoding tests, but more risk from library behaviour changes on upgrades. |
| Code size | About 150 lines (codec) and 60 lines (checks), plus tests. | About 60 lines (data classes, configuration, checks), plus tests, plus the plugin and dependency declarations. |

## Decision

Option A, the hand-written format.

This is not a new dependency, so no dependency justification is needed. It gives the strictest parsing, and it
keeps Kotlin upgrades independent of a serialization plugin. The extra code is small and fully covered by the tests
that R9 and R11 require anyway.

## Format v1

### Common rules (both files)

- UTF-8 without BOM. Lines end with LF (`\n`) only; CR anywhere is invalid. The file ends with LF.
- Each line is `key=value`. Keys come in exactly the order given below, each exactly once. Any other line, an empty
  line, a missing line or an extra line makes the file invalid.
- Integers are canonical decimals: digits only, no sign, no leading zero, at most 5 digits.
- The first line identifies the file kind. The second gives the format version and is checked before the other
  lines are parsed: any version other than `1` (lower, higher, or not an integer) is rejected.
- The second-to-last line is `crc32=` followed by 8 lowercase hexadecimal digits: the CRC-32
  (`java.util.zip.CRC32`) of every byte before this line. The last line is `end`.
- A player name is stored as the standard Base64 (`java.util.Base64`, with padding) of its UTF-8 bytes. Names are
  stored exactly as `X01Config.players` normalizes them (trimmed, NFC). On load, the Base64 must be canonical
  (decoding then re-encoding gives the same text). The bytes must be valid UTF-8, decoded with a reporting decoder.
  The decoded name must already be in normalized form (normalizing it changes nothing).

### Game file: `filesDir/saved_game`

Example (the checksum value is illustrative):

```
kosmodarts=saved-game
version=1
game=x01
start-score=501
checkout=double-out
players=3
player=QWxpY2U=
player=Qm9i
player=Q2zDqW1lbnQ=
darts=T20 20 D5 0 25 50
awaiting-next-player=false
crc32=0a1b2c3d
end
```

- `start-score`: `501` or `301`. `checkout`: `double-out` or `single-out`.
- `players`: `1` to `4`, followed by exactly that many `player` lines, in throwing order.
- `darts`: tokens separated by one space, in throw order. The value is empty if no dart was thrown. There are at most
  10,000 tokens. Tokens:
  - `1` to `20`: single;
  - `D1` to `D20`: double;
  - `T1` to `T20`: triple;
  - `25`: outer bull;
  - `50`: bull;
  - `0`: miss.

  Nothing else is accepted (for example `S5`, `D25`, `T50`, `05` or `D0`).
- `awaiting-next-player`: `true` or `false`, see "Stored fields".

### Last setup file: `filesDir/last_setup`

Example (the checksum value is illustrative):

```
kosmodarts=last-setup
version=1
game=x01
start-score=301
checkout=single-out
players=2
player=QWxpY2U=
player=Qm9i
crc32=0a1b2c3d
end
```

### Size limits (checked before parsing)

- Game file: at most 65,536 bytes. With 4 names of 20 code points (at most 4 × 116 bytes) and 10,000 darts (at most
  40,006 bytes), the largest valid file is under 41 KB, so a valid save is never rejected for its size.
- Last setup file: at most 4,096 bytes.
- The size is checked first with the file length, then the read itself stops after limit + 1 bytes. A file that is
  oversized or still growing is therefore never read into memory beyond the limit.

### Validation order (game file)

The steps below run in order. The first failure makes the whole file invalid, with a reason used by the tests:
`READ_FAILED`, `TOO_LARGE`, `MALFORMED`, `UNSUPPORTED_VERSION`, `CHECKSUM_MISMATCH`, `OUT_OF_RANGE`,
`INVALID_CONFIG`, `REJECTED_BY_ENGINE`, `INCONSISTENT_WAITING_FLAG`. `UNEXPECTED_ERROR` is given by
`Persistence.load` if the loader throws an exception that none of these checks foresaw (ADR 0002, decision 5).

1. Bounded read (`READ_FAILED`, `TOO_LARGE`).
2. Strict UTF-8, no CR, split into lines, kind line (`MALFORMED`).
3. Version line (`UNSUPPORTED_VERSION`).
4. Trailer: final LF, `end` line, well-formed `crc32` line (`MALFORMED`).
5. Checksum (`CHECKSUM_MISMATCH`).
6. Body lines read in order, each with its exact key, with no line missing or extra (`MALFORMED`). Each value is
   checked for syntax and range as it is read (`OUT_OF_RANGE`; an empty dart token is `MALFORMED`).
7. `X01Config` built from the decoded values. Any `InvalidX01ConfigException` is `INVALID_CONFIG`.
8. Full replay with `X01Game(config, darts)`. `LegFinishedException` is `REJECTED_BY_ENGINE`.
9. Waiting flag consistency.

A rejected file is always deleted (ADR 0002, decision 5).

The store catches `IOException` on read (`READ_FAILED`). The decoder catches only the exceptions it expects:
`CharacterCodingException`, `IllegalArgumentException` from Base64, `InvalidX01ConfigException`, and
`LegFinishedException`. Dart tokens are mapped by a fixed table, so the `Dart` constructor can never throw. R11 is
proven by tests, including a seeded random mutation test. There is no catch-all.

### Stored fields (issue #30, T4)

Only the configuration, the darts and one flag are stored. Everything else is derived on load by replaying through
the engine: scores, the player to throw, the turn darts, BUST, the checkout dialog, and statistics.

- `awaiting-next-player` is the only stored UI field. It is needed because after a third dart or a bust, the list of
  darts is the same whether or not "Next player" was tapped. The engine has already moved on to the next player in
  both cases, and only the screen state `GameScreenState.isAwaitingConfirmation` tells them apart. Recording "Next
  player" as an event token in the dart list was considered too. It carries the same information, but adds one token
  per turn and a second kind of event to validate.
- The checkout dialog is not stored: a finished game on the game screen always shows it. A finished game's
  `awaiting-next-player` must therefore be `true`.
- `awaiting-next-player=true` is only valid when the last dart completed a turn. This is read from the replayed engine
  state, not counted by the codec: the replayed `X01State` has at least one completed turn, and either the leg is
  finished or the current player has no dart in the current turn (`currentTurnDarts` is empty). The codec holds no
  turn rule of its own: if the engine changes how a turn ends, the check follows.
- An active Double/Triple modifier and the abandon dialog are not stored (owner decision, issue #30 Q5).

## Versioning and migration

- A change that old code cannot read (a new key, a new token, a new game mode) increments `version`.
- A new version of the app keeps a decoder for every older version still in use. It migrates the old content in
  memory to the current model, and the next write uses the current version.
- An older app that finds a newer version rejects the file (R9): the game is lost, with the R9 message. Since there
  is only one user, who installs builds from PRs, this is accepted (issue #30, P9).
- Planned extensions (see `docs/rules/x01.md`: optional board position of a dart, more game modes, history) get a
  new version, never an optional field slipped into version 1.

## Consequences

- No new dependency. The format is documented here and frozen by an exact-bytes ("golden") test.
- The save is not human-readable for names (Base64). This is intentional: Base64 needs no escaping and decodes
  strictly. The save is not encrypted (out of scope in issue #30). Its CRC detects accidental corruption, not
  tampering.
- Any later change to the format needs a new version and an update of this ADR (or a new ADR that supersedes it).
