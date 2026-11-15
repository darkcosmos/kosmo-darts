# T2: Kosmo docs: capture protocol, group bins, dataset candidates

## Goal
Give Kosmo, early, what he needs before any run: the eval capture protocol, the group bins and the public dataset
candidates to check. Prose for Kosmo in French (policy), headings and commands in English.

## Files
- Added `docs/camera/training.md` with these sections only (T14 adds the commands later):
  - "Data layout": `~/kosmo-data/captures/<session>/`, `~/kosmo-data/public/<set>/`, `~/kosmo-data/annotations/`,
    `~/kosmo-runs/`.
  - "Dev and eval split": CV3 and the cutoff of decision D1; eval is never used to train or tune (CV4).
  - "Eval capture protocol": at least 500 darts over several sessions; lightings and tripod positions different from
    the dev captures; one dart entered right after each throw; after the last dart, remove the darts, leave the field,
    press "Volée suivante" within 3 s (CV11); no other person in front of the board.
  - "Groups" (CV14): lighting classes by median luma (0-255) of the board region: `dark` < 70, `medium` 70 to 140,
    `bright` > 140; tripod angle bins `front` < 15 degrees, `oblique` 15 to 35, `steep` > 35; side `left`, `right`,
    `above`, `below` (dominant foreshortening direction, `front` has no side); distance bins by board diameter over
    image width divided by zoom ratio: `far` < 0.35, `mid` 0.35 to 0.6, `near` > 0.6; zoom bins `1x` < 1.5, `2x`
    1.5 to 3, `zoom` > 3.
- Added `docs/camera/datasets.md`: acceptance rule (CC BY 4.0 only, licence checked by Kosmo on the source page,
  attribution recorded; AGPL, non-commercial, unlicensed and DeepDarts excluded); a table with columns set, link,
  licence, attribution, status (`to check`, `accepted`, `rejected`); rows for the search entry points
  `https://universe.roboflow.com/search?q=dartboard`, `https://huggingface.co/datasets?search=dart`,
  `https://www.kaggle.com/datasets?search=dartboard`, `https://zenodo.org/search?q=dartboard`, all `to check`; how a
  set is added (Kosmo gives the page, the squad adds a row and a class map for T9).

## Depends on
T1.

## Rules
CV3, CV4, CV11, CV14. Values copied from the root plan; no new value.

## Tests
None (documentation). Check every value against the root plan "Rules" and "Approach".

## Done when
`.venv/bin/python -m pytest tools/camera -q` green (unchanged), `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation with values given here, no design choice.

## Implementation notes
