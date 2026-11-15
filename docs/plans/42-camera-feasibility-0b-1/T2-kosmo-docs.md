# T2: Kosmo docs: split, annotation, campaign protocol, group bins, datasets

## Goal
Give Kosmo, with part A, what he needs first: how to split and annotate the existing captures, then the group bins,
the protocol of a later confirmation campaign and the public dataset candidates to check. Prose for Kosmo in French (policy), headings and commands in English.

## Files
- Added `docs/camera/training.md` with these sections only (T14 adds the commands later):
  - "Data layout": `~/kosmo-data/captures/<session>/`, `~/kosmo-data/captures-eval/<session>/` (later campaign,
    CV17), `~/kosmo-data/public/<set>/`, `~/kosmo-data/annotations/`, `~/kosmo-runs/`.
  - "Dev and eval split": CV3 in plain words (about 70/30 by session, block mode "plus optimiste" otherwise), the
    command of step K1 of root "How Kosmo verifies", what to paste (its whole output, no name in it), that eval is
    never used to train or tune (CV4) and that the split never changes once locked.
  - "Annotate": step K2 of root "How Kosmo verifies" (launch, URL, where files go, what he should see), the keys of
    T6b, how to stop (`Ctrl+C`) and resume.
  - "Confirmation campaign protocol" (later, only if the preliminary report asks for it): sessions into
    `captures-eval/`; one dart entered right after each throw; after the last dart, remove the darts, leave the field,
    press "Volée suivante" within 3 s (CV11); no other person in front of the board; counts, positions and lightings
    given by the report.
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
T4b, T6b.

## Rules
CV3, CV4, CV11, CV14, CV17. Values copied from the root plan; no new value.

## Tests
None (documentation). Check every value against the root plan "Rules" and "Approach", and every command against `--help` of T4b and T6a.

## Done when
Python checks of root "Test plan" green (unchanged), `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation with values given here, no design choice.

## Implementation notes
