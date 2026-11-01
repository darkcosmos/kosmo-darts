# T15: GitHub issue for a real release version

## Goal
Open the GitHub issue Kosmo asked for (decision D25): move to a real release version of the app that can be
updated. No repository file changes.

## Files
None. One GitHub issue, created with `gh issue create` (no label, no assignee).

## Depends on
T13 (the backlog entry links to the issue number: add it to `docs/backlog.md` in this task's commit if T13 is
merged in the branch; otherwise report the number).

## Rules
D25 (root plan "Decisions"). Issue text in French, for Kosmo; no jargon without a short explanation.

## Content
- Title: « Passer à une vraie version release pouvant être mise à jour ».
- Body, in French:
  1. Contexte: jusqu'ici chaque version installée est une version de test signée avec une clé qui change à chaque
     build; il faut désinstaller puis réinstaller, ce qui efface tout. Citation de Kosmo (D25).
  2. À faire: une vraie clé de signature conservée qui permet les mises à jour; une version release de l'application.
  3. Conséquence: à partir de cette version release seulement, la compatibilité ascendante devient obligatoire pour
     les parties sauvegardées, la dernière configuration, la base de l'historique et les réglages (chaque changement
     de format arrive avec sa migration testée). Avant, table rase autorisée (issue #50, D24, D25).
  4. Questions pour Kosmo à trancher dans cette issue (où et comment garder la clé, nom et numéro de version).

## Tests
None. Check with `gh issue view <n>` that title and body are stored as written.

## Done when
The issue exists and its number is in the notes; if a file changed, `./gradlew build` green and `kosmo-detekt`
exit 0.

## Model
haiku. Why: text given above, one command, no design choice.

## Implementation notes
- GitHub issue created: #66, https://github.com/darkcosmos/kosmo-darts/issues/66, title as written, state OPEN, no
  label, no assignee. Body in French (context, to do, consequence for compatibility, questions for Kosmo: where to keep
  the signing key, release name and version number). Verified with `gh issue view 66`.
- `docs/backlog.md`: the release line now links to issue #66 (`decision D25, issue #66`).
- No other repository file changed. No secret mentioned.
