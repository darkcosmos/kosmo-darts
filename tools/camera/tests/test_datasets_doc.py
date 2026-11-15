"""`docs/camera/datasets.md` table (CV21, D20, D23, D28, S11) against the committed class map. Stdlib only."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DOC = ROOT / "docs" / "camera" / "datasets.md"
MAP = ROOT / "tools" / "camera" / "config" / "public-class-maps.json"
HEADER = ["slug (dossier)", "jeu", "lien", "licence", "attribution", "statut", "motif"]
STATUSES = ("accepted", "conditional", "rejected")
LICENCE = re.compile(r"CC BY 4\.0 \(page vue par Kosmo le \d{4}-\d{2}-\d{2}\)")


def _cells(line):
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def _table():
    lines = DOC.read_text(encoding="utf-8").splitlines()
    start = next(i for i, line in enumerate(lines) if _cells(line) == HEADER)
    assert re.fullmatch(r"\|(-+\|){7}", lines[start + 1].replace(" ", "")), "separator line of 7 columns"
    rows = []
    for line in lines[start + 2:]:
        if not line.startswith("|"):
            break
        rows.append(_cells(line))
    return rows


def _map():
    return json.loads(MAP.read_text(encoding="utf-8"))["sets"]


def test_header_has_the_seven_columns():
    assert _table() is not None


def _problems(row):
    """What is wrong with one table row (empty list when it is valid)."""
    if len(row) != 7:
        return ["7 cells expected"]
    slug, name, link, licence, attribution, status, motif = row
    problems = []
    if status not in STATUSES:
        problems.append("status")
    if not motif:
        problems.append("motif")
    if status in ("accepted", "conditional"):
        if not link.startswith("https://universe.roboflow.com/"):
            problems.append("link")
        if not LICENCE.fullmatch(licence):
            problems.append("licence")
        if not attribution.startswith(f"{name} par ") or ", CC BY 4.0 ; modifié : " not in attribution:
            problems.append("attribution")  # D28
    return problems


def test_every_row_is_valid_cells_status_motif_link_licence_attribution():
    for row in _table():
        assert _problems(row) == [], row


def test_the_row_checker_is_not_vacuous():
    good = ["s", "Jeu", "https://universe.roboflow.com/a/b", "CC BY 4.0 (page vue par Kosmo le 2026-11-15)",
            "Jeu par A, https://universe.roboflow.com/a/b, CC BY 4.0 ; modifié : boîtes reconstruites", "accepted", "ok"]
    assert _problems(good) == []
    assert _problems(good[:6]) == ["7 cells expected"]
    assert _problems(good[:3] + ["CC BY 4.0"] + good[4:]) == ["licence"]
    assert _problems(good[:2] + ["https://example.org/"] + good[3:]) == ["link"]
    assert _problems(good[:4] + ["Jeu par A, CC BY 4.0"] + good[5:]) == ["attribution"]
    assert _problems(good[:5] + ["maybe", "ok"]) == ["status"]
    assert _problems(good[:6] + [""]) == ["motif"]
    assert _problems(["s", "Jeu", "", "", "", "rejected", "non retenu"]) == []


def test_every_map_slug_is_a_row_and_conditional_exactly_with_a_pseudo_tip_box():
    rows = {row[0].strip("`"): row for row in _table()}
    for slug, entry in _map().items():
        assert slug in rows, slug
        pseudo = "pseudo-tip-box" in entry["categories"].values()
        assert rows[slug][5] == ("conditional" if pseudo else "accepted"), slug


def test_rejected_rows_have_no_map_entry():
    sets = _map()
    for row in _table():
        if row[5] == "rejected":
            assert row[0].strip("`") not in sets, row[0]


def test_slugs_are_unique():
    slugs = [row[0] for row in _table()]
    assert len(slugs) == len(set(slugs))
