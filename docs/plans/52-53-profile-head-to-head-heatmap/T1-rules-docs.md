# T1: Rule documents and ADR 0015

## Goal
Write the rules of the mission into the rule documents, exactly as the root plan section "Rules (new and rewritten)"
gives them, and the ADR of root "Approach" 5. No code.

## Files
- `docs/adr/0015-derived-statistics.md` (new), in the format of `docs/adr/0014-anonymous-profile.md`: statistics are
  derived on read from the archive and the journal (D1, D2), invalid matches skipped and counted (D4, ST10),
  `BoardGeometry` and `BoardZone` made public, back stack as a parent chain (D5). Alternatives rejected, with the
  reason from the root plan: a stored statistics cache, saved-game format v3 for CR13, whole-load failure on invalid
  data, a `List<Screen>` back stack, keeping the board model internal.
- `docs/rules/statistics.md` (new): header in the style of `docs/rules/history.md` (status, single source of truth,
  prefixes ST, STX, STC, PS, HM, HH and the list of prefixes they never collide with), then the root rules, then
  "Related rules" (CR13, CR15, PM2 to PM11, HS5, HS8, HS9, BC2, BC4, BC5, AR5, AR6).
- `docs/rules/history.md`: replace PM2, PM4, PM11; add HS8, HS9 at the end of "History screens"; add BC4, BC5 at the
  end of "Board coordinates"; PF1 unchanged; status line mentions issues #52 and #53.
- `docs/rules/cricket.md`: replace CR13.
- `app/.../history/HistoryEntities.kt`: only the KDoc of `ProfileEntity` (line 16), which still says the reset date
  never changes: point to PM4 and PM5.

## Depends on
None.

## Rules
All rules of the root section "Rules (new and rewritten)". HM5 keeps the text "decided at gate G1" until T20.

## Tests
None (documents only). Check that every rule id of the root section appears once in the documents
(`grep -c` per id).

## Done when
`./gradlew build` green; `kosmo-detekt` exit 0.

## Model
haiku. Why: text copied from the root plan (rules, "Approach", "Decisions"), no design choice.

## Implementation notes
- Done as specified; no deviation from the root "Rules (new and rewritten)" section. Rule texts copied with their owner
  references; the "(rewritten)" and "(new ...)" markers dropped.
- `docs/rules/statistics.md` (new): 50 rule ids (ST, STX, STC, PS, HM, HH), each once. Sub-sections: Common, X01,
  Cricket, Profile screen, Heatmap, Head-to-head, Related rules. HM5 still says "decided at gate G1".
- `docs/rules/history.md`: PM2, PM4, PM11 replaced; HS8, HS9 after HS7; BC4, BC5 after BC3; status line amended.
  PF1 unchanged.
- `docs/rules/cricket.md`: CR13 replaced (points to STC1; CR14 unchanged).
- `docs/adr/0015-derived-statistics.md` (new), format of ADR 0014, status Proposed.
- `app/.../history/HistoryEntities.kt`: KDoc of `ProfileEntity` only, now points to PM4 and PM5 (path to the rule
  file left out to keep the line under 120 characters).
- Checks on this head (before commit): `./gradlew build` BUILD SUCCESSFUL (5 min 26 s); `kosmo-detekt` exit 0.
