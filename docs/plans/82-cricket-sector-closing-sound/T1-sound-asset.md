# T1: New sound file, SECTOR_CLOSING effect, credits and AN24

## Goal
Ship the issue's sound unchanged as a new `SoundEffect.SECTOR_CLOSING`, loaded by `SoundPoolPlayer`, and credit it
(root plan, Approach steps 2 and 3; "Rule changes", T1 part; Decisions D3, D4, D8). No cue plays it yet (T2).

## Files
- `app/src/main/res/raw/sfx_closed_sector.ogg`: download
  https://github.com/user-attachments/files/34870749/sfx.zip with `curl` into a new empty scratchpad directory,
  extract with `jar xf` (no `unzip` in the sandbox), check 51,006 bytes, SHA-256
  `0e10525b020682abed7835aa4a8c021435d22dd4485e61297c8fde2a2146e4bc` (`sha256sum`), "OggS" and "vorbis" headers
  (`head -c`, `od`), then copy it unchanged. If the download is blocked, stop and report.
- `app/.../audio/SoundEffect.kt`: `SECTOR_CLOSING` with KDoc "AN33: the Cricket dart that closes a target for the
  last player (CR17)"; class KDoc "eight sound effects".
- `app/.../audio/SoundPoolPlayer.kt`: `resourceOf` mapping to `R.raw.sfx_closed_sector`.
- Tests: `audio/AudioFakes.kt` (length `3_000L`), `audio/SoundPoolPlayerTest.kt`.
- `docs/sounds/CREDITS.md`: the row and the bullet given in the root plan, "Rule changes", word for word in substance;
  no claim about the file's tool or plan.
- `docs/rules/announcements.md`: line 11 and AN24, as in the root plan, "Rule changes", T1 part.

## Depends on
Nothing.

## Rules
AN9, AN24 (AN33 is written in T2).

## Tests
- `SoundPoolPlayerTest`: the expected resource list includes `R.raw.sfx_closed_sector` in enum order; the full-volume
  loop over non-victory effects runs over the new entry.

## Done when
`./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exit 0; committed (file, code, docs).
Notes record the size and SHA-256 actually checked.

## Model
haiku. Why: copy of a given file with checksum, an enum value, a mapping, listed tests and given doc text; no design
choice.

## Implementation notes
