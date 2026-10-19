# Sound effects credits

The sound effects of `app/src/main/res/raw/` were generated with ElevenLabs (https://elevenlabs.io), on the free plan,
in October 2026. The free plan requires the mention "elevenlabs.io": it is shown at the bottom of the "Settings"
screen ("Sound effects generated with elevenlabs.io" / « Effets sonores générés avec elevenlabs.io », rule AN13 of
`docs/rules/announcements.md`).

| File | Played when |
|---|---|
| `sfx_dart_hit.ogg` | A dart scores points |
| `sfx_dart_miss.ogg` | A dart scores 0 (outside the scoring area) |
| `sfx_dart_cancel.ogg` | A dart is undone, or the checkout dialog is refused |
| `sfx_bust.ogg` | A turn ends on a bust |
| `sfx_one_eighty.ogg` | A turn scores 180 |
| `sfx_victory.ogg` | A winning dart is entered, together with the win voice (any win, a big fish included) |

- Tool: ElevenLabs sound effects generator.
- Plan: free.
- Generated: October 2026.
- The files are used as delivered on the `assets/sound-effects` branch, unchanged.
- `sfx_victory.ogg` replaced `sfx_game_shot.ogg` and `sfx_big_fish.ogg` (owner decision A.2, October 2026). PENDING:
  its origin (tool, plan, licence) is to be confirmed by the owner (question on PR #33); this line is replaced by the
  answer before merge.
