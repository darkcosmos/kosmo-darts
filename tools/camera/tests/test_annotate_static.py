"""Static checks of the annotation page (T6b): security scan, French strings, key letters, contrast, touch targets.

Rules: CV16 (local tool, no external resource, CSP default-src 'self'), D5 (French page), D8 (natural pixels).
"""
import re
from pathlib import Path

import pytest

STATIC = Path(__file__).parent.parent / "kosmo_camera" / "annotate" / "static"
FILES = ("index.html", "app.js", "style.css")
ACTION_KEYS = {"btnHidden": "H", "btnOffBoard": "O", "btnClear": "U", "btnNext": "N", "btnPrevious": "P",
               "btnRecalibrate": "R"}
ENGLISH_WORDS = re.compile(r"\b(next|previous|hidden|clear|cancel|loading|error|back|save|saved|dart|darts"
                           r"|the|and|please|click|off board|undo|none|yes|no)\b", re.IGNORECASE)


def read(name):
    return (STATIC / name).read_text(encoding="utf-8")


def string_table():
    source = read("app.js")
    match = re.search(r"const TEXT = \{(.*?)\n\};", source, re.DOTALL)
    assert match, "app.js must start with `const TEXT = { ... };`"
    entries = re.findall(r'^\s*(\w+):\s*"((?:[^"\\]|\\.)*)",?\s*$', match.group(1), re.MULTILINE)
    assert entries
    return dict(entries)


@pytest.mark.parametrize("name", FILES)
def test_files_exist_and_are_utf8(name):
    assert read(name).strip()


@pytest.mark.parametrize("name", FILES)
def test_no_external_resource_no_forbidden_call(name):
    text = read(name)
    assert not re.search(r"https?://", text), "external URL"
    assert "@import" not in text
    assert not re.search(r"\b(innerHTML|outerHTML|insertAdjacentHTML|eval|document\.write|XMLHttpRequest|WebSocket"
                         r"|importScripts|localStorage|sendBeacon)\b", text)
    assert not re.search(r"new\s+Function\b", text)


def test_no_inline_handler_nor_inline_script_nor_style_in_html():
    html = read("index.html")
    assert not re.search(r"<[^>]+\son[a-z]+\s*=", html, re.IGNORECASE)
    assert not re.search(r"<script(?![^>]*\ssrc=)[^>]*>", html), "inline script"
    assert not re.search(r"<style\b|\sstyle\s*=", html, re.IGNORECASE), "inline style (CSP)"
    sources = re.findall(r"<(?:script|link|img)\b[^>]*?\s(?:src|href)=\"([^\"]*)\"", html)
    assert sources and all(s.startswith("/static/") for s in sources), sources


def test_fetch_goes_to_the_same_origin_only():
    for url in re.findall(r"fetch\(\s*([^,)]+)", read("app.js")):
        assert url.strip().startswith(("`/", "'/", '"/', "apiPath", "url")), url
    assert "image-orientation: none" in read("style.css")  # D8


def test_each_action_button_label_ends_with_its_key_letter():
    table = string_table()
    for key, letter in ACTION_KEYS.items():
        assert table[key].endswith(f" ({letter})"), key
    assert table["btnHidden"] == "Cachée (H)" and table["btnOffBoard"] == "Hors cible (O)"
    assert table["btnClear"] == "Effacer (U)" and table["btnNext"] == "Suivante (N)"
    assert table["btnPrevious"] == "Précédente (P)"


def test_the_key_handler_maps_the_same_letters_to_the_same_actions():
    source = read("app.js")
    for letter, action in (("h", "hidden"), ("o", "off_board"), ("u", "clear"), ("n", "next"), ("p", "previous"),
                           ("r", "recalibrate")):
        assert re.search(rf'{letter}:\s*"{action}"', source), letter


def test_every_visible_string_is_french_and_not_empty():
    table = string_table()
    ignored = "Ignorée : aucune fléchette jugée"
    assert table["ignoredLabel"] == ignored
    for key, value in table.items():
        assert value.strip(), key
        assert not ENGLISH_WORDS.search(value), (key, value)
    html = read("index.html")
    assert re.search(r"<html[^>]*\slang=\"fr\"", html)
    visible = re.sub(r"<script.*?</script>|<[^>]+>", "\n", html, flags=re.DOTALL)
    texts = [t.strip() for t in visible.splitlines() if t.strip()]
    assert texts
    for text in texts:
        assert not ENGLISH_WORDS.search(text), text
    assert "<title>" in html and re.search(r"<title>\s*\S", html)


def test_html_has_no_hardcoded_text_outside_french_or_symbols():
    html = read("index.html")
    for label in re.findall(r"aria-label=\"([^\"]*)\"", html):
        assert label.strip() and not ENGLISH_WORDS.search(label), label


# ---- style.css ------------------------------------------------------------------------------------------------------

def luminance(color):
    channels = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contrast(first, second):
    high, low = sorted((luminance(first), luminance(second)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def colors():
    root = re.search(r":root\s*\{(.*?)\}", read("style.css"), re.DOTALL).group(1)
    found = dict(re.findall(r"(--[\w-]+):\s*(#[0-9a-fA-F]{6})\s*;", root))
    assert found
    return found


PAIRS = (("--text", "--bg"), ("--text", "--panel"), ("--muted", "--bg"), ("--muted", "--panel"),
         ("--button-text", "--button-bg"), ("--primary-text", "--primary-bg"), ("--danger", "--panel"),
         ("--ok", "--panel"), ("--link", "--panel"))


@pytest.mark.parametrize("fore,back", PAIRS)
def test_declared_text_colours_have_a_contrast_of_at_least_4_5(fore, back):
    declared = colors()
    assert contrast(declared[fore], declared[back]) >= 4.5, (fore, back)


def test_buttons_are_at_least_48px_high_and_wide():
    css = read("style.css")
    rules = re.findall(r"(?:^|\})\s*([^{}]*\b(?:button|\.btn)\b[^{}]*)\{([^}]*)\}", css)
    assert rules
    heights = [re.search(r"min-height:\s*(\d+)px", body) for _, body in rules]
    widths = [re.search(r"min-width:\s*(\d+)px", body) for _, body in rules]
    assert any(h and int(h.group(1)) >= 48 for h in heights)
    assert any(w and int(w.group(1)) >= 48 for w in widths)
    for _, body in rules:
        for found in re.findall(r"min-(?:height|width):\s*(\d+)px", body):
            assert int(found) >= 48


def test_system_fonts_only():
    css = read("style.css")
    assert "@font-face" not in css and "url(" not in css


def test_double_click_guard_finished_label_and_view_reset_are_present():
    text = read("app.js")
    assert "CLICK_GUARD_MS" in text and "lastActionAt" in text
    assert "Terminée" in text and "viewKey" in text


# ---- recalibration page and drift flags (T6d; static checks only: no browser nor node in the sandbox) -------------------

def key_actions():
    match = re.search(r"const KEY_ACTIONS = \{(.*?)\};", read("app.js"))
    assert match
    return dict(re.findall(r'(\w+):\s*"(\w+)"', match.group(1)))


def test_key_actions_match_the_flow_action_names_and_no_letter_is_used_twice():
    from kosmo_camera.annotate import flow
    mapping = key_actions()
    assert mapping["r"] == "recalibrate"
    assert set(mapping.values()) <= set(flow.KINDS)
    assert len(re.findall(r'\b(\w):\s*"\w+"', re.search(r"const KEY_ACTIONS = \{(.*?)\};", read("app.js")).group(1))) \
        == len(mapping)
    assert len(set(mapping.values())) == len(mapping)
    labels = {letter: table_label for table_label, letter in ACTION_KEYS.items()}
    assert sorted(labels) == sorted(letter.upper() for letter in mapping)


def test_string_table_has_the_recalibration_and_drift_strings():
    table = string_table()
    assert table["btnRecalibrate"] == "Recalibrer à partir de cette fléchette (R)"
    assert table["recalibrationTitle"] == "Recalibration à partir de la fléchette {number} : point {n} sur 4"
    assert table["recalibrationHint"] == "Effacer (U) retire le dernier point ; sans point, retour à la fléchette"
    assert table["driftSuggestedBanner"] == "Dérive au-delà du seuil : recalibrer à partir d'ici (R)"
    assert table["driftBeyond"] == "Dérive" and table["driftUnmeasurable"] == "Dérive non mesurable"
    assert table["calibratedSegments"] == "Calibrée ({count} segments)"
    assert table["staleMeasure"] == "(mesure périmée)"
    assert table["pickerSuggested"] == "{number} !"
    assert table["pickerSuggestedLabel"] == "Fléchette {number}, à recalibrer"
    assert table["pickerSuggestedDone"] == "{number} ✓ !"
    assert table["pickerSuggestedDoneLabel"] == "Fléchette {number}, annotée, à recalibrer"
    assert table["oldPoint"] == "ancien {n}"


def test_old_points_are_drawn_as_hollow_crosses_labelled_ancien_k():
    source = read("app.js")
    assert "function drawHollowCross" in source
    assert re.search(r"fill\(TEXT\.oldPoint,\s*\{\s*n:\s*index \+ 1\s*\}\)", source)
    assert "previousSegment(view, cursor.number)" in source


def test_picker_label_building_keeps_the_marker_and_the_stale_suffix():
    source = read("app.js")
    body = re.search(r"function pickerText\(dart, stale\) \{(.*?)\n\}\n", source, re.DOTALL).group(1)
    assert "TEXT.pickerSuggested" in body and "TEXT.pickerSuggestedLabel" in body
    assert re.search(r"if \(stale\) \{\s*aria = `\$\{aria\} \$\{TEXT\.staleMeasure\}`;", body)
    assert "pickerText(dart, state.view.driftStale)" in source


def test_picker_keeps_the_annotated_mark_on_a_suggested_dart():
    body = re.search(r"function pickerText\(dart, stale\) \{(.*?)\n\}\n", read("app.js"), re.DOTALL).group(1)
    assert "TEXT.pickerSuggestedDone" in body and "TEXT.pickerSuggestedDoneLabel" in body
    assert "dart.status !== null" in body


def test_calibration_status_distinguishes_none_one_and_several_segments():
    body = re.search(r"function calibrationStatus\(view\) \{(.*?)\n\}\n", read("app.js"), re.DOTALL).group(1)
    assert "TEXT.notCalibrated" in body and "TEXT.calibratedSegments" in body and "TEXT.calibrated" in body
    assert re.search(r"count === 0", body) and re.search(r"count > 1", body)


def test_stale_measure_text_is_shown_in_the_banner_and_the_state_text():
    source = read("app.js")
    panel = re.search(r"function renderPanel\(\) \{(.*?)\n\}\n", source, re.DOTALL).group(1)
    assert "TEXT.staleMeasure" in panel and "view.driftStale" in panel
    drift_text = re.search(r"function driftText\(dart, stale\) \{(.*?)\n\}\n", source, re.DOTALL).group(1)
    assert "TEXT.staleMeasure" in drift_text
    assert "driftText(dart, state.view.driftStale)" in source


def test_the_recalibration_row_is_full_width_and_at_least_48px_high():
    html, css = read("index.html"), read("style.css")
    assert 'id="recalibrate-actions"' in html
    assert html.index("recalibrate-actions") < html.index("zoom-actions")
    rule = re.search(r"#recalibrate-actions button\s*\{([^}]*)\}", css).group(1)
    assert int(re.search(r"min-height:\s*(\d+)px", rule).group(1)) >= 48
    assert re.search(r"width:\s*100%", rule) and "white-space: normal" in rule


def test_list_reads_the_sessions_object_and_the_drift_warning_and_uses_text_content_only():
    source = read("app.js")
    assert "data.sessions" in source and "data.driftWarning" in source
    assert "item.driftLabel" in source


def test_warning_colour_has_a_contrast_of_at_least_4_5_on_background_and_panel():
    declared = colors()
    assert contrast(declared["--warn"], declared["--bg"]) >= 4.5
    assert contrast(declared["--warn"], declared["--panel"]) >= 4.5
