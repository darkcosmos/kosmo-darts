# T1: Rule documents

## Goal
Write the rules of the mission into the rule documents, exactly as the root plan section "Rules (new and rewritten)"
gives them. No code.

## Files
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
haiku. Why: text copied from the root plan, no design choice.

## Implementation notes
