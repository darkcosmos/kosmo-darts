"""Static checks of the annotation page (T6b): security scan, French strings, key letters, contrast, touch targets.

Rules: CV16 (local tool, no external resource, CSP default-src 'self'), D5 (French page), D8 (natural pixels).
"""
import re
from pathlib import Path

import pytest

STATIC = Path(__file__).parent.parent / "kosmo_camera" / "annotate" / "static"
FILES = ("index.html", "app.js", "style.css")
ACTION_KEYS = {"btnHidden": "H", "btnOffBoard": "O", "btnClear": "U", "btnNext": "N", "btnPrevious": "P"}
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
    for letter, action in (("h", "hidden"), ("o", "off_board"), ("u", "clear"), ("n", "next"), ("p", "previous")):
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
         ("--ok", "--panel"), ("--link", "--panel"), ("--legend-text", "--panel"), ("--legend-text", "--bg"))


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


def test_double_click_guard_and_view_reset_are_present():
    text = read("app.js")
    assert "CLICK_GUARD_MS" in text and "lastActionAt" in text and "viewKey" in text


# ---- key letters, picker, counts (static checks only: no browser nor node in the sandbox) --------------------------------

def key_actions():
    match = re.search(r"const KEY_ACTIONS = \{(.*?)\};", read("app.js"))
    assert match
    return dict(re.findall(r'(\w+):\s*"(\w+)"', match.group(1)))


def test_key_actions_match_the_flow_action_names_and_no_letter_is_used_twice():
    from kosmo_camera.annotate import flow
    mapping = key_actions()
    assert set(mapping.values()) <= set(flow.KINDS)
    assert len(re.findall(r'\b(\w):\s*"\w+"', re.search(r"const KEY_ACTIONS = \{(.*?)\};", read("app.js")).group(1))) \
        == len(mapping)
    assert len(set(mapping.values())) == len(mapping)
    labels = {letter: table_label for table_label, letter in ACTION_KEYS.items()}
    assert sorted(labels) == sorted(letter.upper() for letter in mapping)


def test_picker_label_is_the_number_ticked_when_annotated_without_any_drift_mark_a7():
    body = function_body("pickerText")
    assert "TEXT.pickerDone" in body and "TEXT.pickerLabel" in body and "dart.status === null" in body
    assert "!" not in re.sub(r"!==|!=", "", body) and "pickerText(dart)" in read("app.js")


def test_no_dérive_text_nor_drift_field_remains_in_the_page_cv30_a7():
    for name in FILES:
        text = read(name)
        assert "dérive" not in text.lower() and "À recalibrer" not in text, name
        assert not re.search(r"driftLabel|driftStale|\.drift\b|item\.state|view\.state|staleMeasure|session-stale|"
                             r"banner\.drift", text), name



def test_warning_colour_has_a_contrast_of_at_least_4_5_on_background_and_panel():
    declared = colors()
    assert contrast(declared["--warn"], declared["--bg"]) >= 4.5
    assert contrast(declared["--warn"], declared["--panel"]) >= 4.5



COUNTING = r"\.filter\(|\.length|\.every\(|\.some\(|\.reduce\("


def function_body(name):
    match = re.search(rf"function {name}\([^)]*\) \{{(.*?)\n\}}\n", read("app.js"), re.DOTALL)
    assert match, name
    return match.group(1)


def test_the_page_shows_the_server_progress_badge_and_calibration_label_without_counting_a3():
    source = read("app.js")
    assert "calibrationStatus" not in source and "finished" not in source and "badge" not in source.lower()
    assert "pointes saisies" not in source  # the label is the server's string, as given (T5)
    panel, card = function_body("renderPanel"), function_body("sessionLine")
    assert "view.progressLabel" in panel and "view.calibrationLabel" in panel
    assert "item.progressLabel" in card and "item.calibrationLabel" in card
    for body in (panel, card):
        assert not re.search(COUNTING, body)
    assert "segments" not in panel and "calibrated" not in source


def test_list_reads_the_sessions_object_and_the_frames_warning_and_uses_text_content_only():
    source = read("app.js")
    assert "data.sessions" in source and "data.framesWarning" in source
    card = function_body("sessionLine")
    assert "item.message" in card and "item.warning" in card and "item.label" in card
    assert not re.search(r"innerHTML|insertAdjacent", source)
    for fragment in ("data.instruction", "continuePayload", "item.badge", "item.steps", "item.info", "data.focus"):
        assert fragment not in source, fragment


def test_video_frames_load_from_the_frame_route_and_skip_the_truth_size_check():
    url = function_body("imageUrl")
    assert "/api/frame/" in url and "/photo/" in url and "dart.frame.file" in url
    loaded = function_body("imageLoaded")
    assert re.search(r"if \(!state\.showFrame\) \{[^}]*truthWidth", loaded, re.DOTALL)  # the check is for photos only


def test_v_calls_the_frame_route_only_when_the_dart_has_a_frame_else_the_button_is_disabled_and_says_why():
    from kosmo_camera.annotate import flow
    source, table = read("app.js"), string_table()
    assert table["btnFrame"] == "Image vidéo (V)" and table["btnFrame"].endswith(" (V)")
    assert 'const FRAME_KEY = "v";' in source and "key === FRAME_KEY" in function_body("onKey")
    assert "v" not in key_actions() and "toggleFrame" not in "".join(key_actions().values())
    assert "frame" not in flow.KINDS and "v" not in flow.KINDS
    toggle = function_body("toggleFrame")
    assert "!dart.frame" in toggle and "TEXT.noFrame" in toggle
    assert toggle.index("TEXT.noFrame") < toggle.index("loadImage()")  # no image request without a listed frame
    assert "dart.frame.file" in function_body("imageUrl") and "/api/frame/" not in toggle
    button = function_body("frameButton")  # U15: the disabled button carries the reason in its name and title
    assert "button.disabled = true" in button and "button.title = TEXT.noFrame" in button
    assert re.search(r'setAttribute\("aria-label", `\$\{TEXT\.btnFrame\} : \$\{TEXT\.noFrame\}`\)', button)
    assert "!dart.frame" in button and "frameButton()" in function_body("renderZoomActions")


STATES = (  # (key, French text, functions that use it at its trigger)
    ("noFrame", "Pas d'image vidéo pour cette fléchette.", ("toggleFrame", "frameButton")),
    ("legendNoControl", "○ C : contrôle — aucun pour cette fléchette", ("renderLegend",)),
    ("allEntered", "Toutes les pointes sont saisies. Précédente (P) ou la liste pour corriger.",
     ("applyView", "onStageClick")),
    ("frameReadOnly", "Image vidéo en lecture seule : V pour revenir à la photo.", ("perform", "onStageClick")),
    ("frameUnreadable", "Image vidéo illisible. Retour à la photo.", ("imageFailed",)),
    ("photoUnreadable", "Photo illisible : saisie impossible sur cette photo.", ("imageFailed",)),
)


@pytest.mark.parametrize("key,text,users", STATES)
def test_each_states_row_text_is_a_constant_of_the_page_used_at_its_trigger_u12(key, text, users):
    assert string_table()[key] == text and read("app.js").count(f'"{text}"') == 1
    for user in users:
        assert f"TEXT.{key}" in function_body(user), (key, user)
    assert "\n" not in text  # one line


def test_the_frame_view_sets_the_label_and_sends_no_request_on_click_or_h_o_u_u13():
    html, css, source = read("index.html"), read("style.css"), read("app.js")
    assert string_table()["frameLabel"] == "Image vidéo (lecture seule)"
    assert re.search(r'<p id="frame-label" hidden></p>', html) and html.index("frame-label") < html.index('id="stage"')
    assert '$("frame-label").hidden = !state.showFrame' in function_body("renderPanel")
    assert 'setText($("frame-label"), TEXT.frameLabel)' in function_body("start")
    edits = re.search(r"const EDIT_ACTIONS = \[(.*?)\]", source).group(1)
    assert set(re.findall(r'"(\w+)"', edits)) == {"click", "hidden", "off_board", "clear"}
    perform = function_body("perform")
    assert perform.index("state.showFrame && EDIT_ACTIONS.includes(payload.action)") < perform.index("await request(")
    assert "TEXT.frameReadOnly" in perform.split("await request(")[0]
    click = function_body("onStageClick")
    assert click.index("state.showFrame") < click.index("perform(") and "TEXT.frameReadOnly" in click
    rule = re.search(r"#frame-label\s*\{([^}]*)\}", css).group(1)  # text style: bold text, no colour tint
    assert "font-weight: bold" in rule and "background" not in rule and "var(--text)" in rule


LEGEND = ("+ pointe", "□ 1 à 4 : points du segment (lecture seule)", "○ C : contrôle (lecture seule)")


def test_the_legend_has_the_three_text_lines_with_their_shapes_and_passes_4_5_on_the_panel_u13():
    table, html, css = string_table(), read("index.html"), read("style.css")
    assert (table["legendTip"], table["legendSegment"], table["legendControl"]) == LEGEND
    assert '<ul id="legend"></ul>' in html and html.index('id="panel"') < html.index('id="legend"')
    legend = function_body("renderLegend")
    for key in ("legendTip", "legendSegment", "legendControl", "legendNoControl"):
        assert f"TEXT.{key}" in legend
    assert ".hidden" not in legend and '$("legend").hidden' not in read("app.js")  # always visible
    declared = colors()
    for back in ("--panel", "--bg"):
        assert contrast(declared["--legend-text"], declared[back]) >= 4.5, back
    assert "color: var(--legend-text)" in re.search(r"#legend\s*\{([^}]*)\}", css).group(1)
    segment, control = function_body("drawSegmentPoint"), function_body("drawControlPoint")
    assert "strokeRect" in segment and "label(context, text," in segment  # a square and its number
    assert "arc(" in control and "CONTROL_LETTER" in control and 'CONTROL_LETTER = "C"' in read("app.js")


def test_overlays_read_segments_control_and_frame_control_from_the_view():
    stage, control = function_body("drawStage"), function_body("controlPoints")
    assert "state.view.segments" in stage and "s.photo === dart.photo" in stage and "s.points" in stage
    assert "dart.frame.control" in control and "dart.control" in control
    assert "controlPoints(dart)" in stage and "state.showFrame" in stage
    assert stage.index("if (!state.showFrame)") > stage.index("controlPoints(dart)")  # tip and segments: photo only


def test_no_recalibration_nor_assisted_text_remains_and_no_key_r_k_or_1_to_4():
    for name in FILES:
        text = read(name)
        for fragment in ("recalé", "Recalage", "Points prêts", "Continuer", "Contrôle de calibration", "Recalibrer",
                         "Enregistrer (Entrée)", "ASSISTED_KINDS", "journey", "inset", "diagram", "recalibrate"):
            assert fragment not in text, (name, fragment)
    source = read("app.js")
    assert set(key_actions()) == {"h", "o", "u", "n", "p"} and "/^[1-4]$/" not in source and '"Enter"' not in source
    assert not re.search(r'key === "[rk1-4]"', source)


def test_the_page_sends_only_the_actions_of_flow_kinds():
    from kosmo_camera.annotate import flow
    sent = set(re.findall(r'action:\s*"(\w+)"', read("app.js"))) | set(key_actions().values())
    assert sent == {"click", "goto", "hidden", "off_board", "clear", "next", "previous"}
    assert sent <= set(flow.KINDS)


def test_an_unreadable_photo_ignores_clicks_and_keeps_h_o_u_n_p_and_a_failed_frame_goes_back_to_the_photo():
    failed = function_body("imageFailed")
    assert "state.clicksBlocked = TEXT.photoUnreadable" in failed
    assert failed.index("state.showFrame = false") < failed.index("loadImage()")
    click = function_body("onStageClick")
    assert click.index("state.clicksBlocked") < click.index("perform(")
    for name in ("perform", "renderActions", "renderPicker", "toggleFrame"):
        assert "clicksBlocked" not in function_body(name), name  # only the click is ignored; V stays enabled
    assert "button.disabled = true" not in failed and "TEXT.frameUnreadable" in failed


def test_when_all_tips_are_entered_the_page_opens_on_the_last_dart_and_n_p_and_the_picker_work():
    shown = function_body("shownDart")
    assert "view.darts[view.darts.length - 1]" in shown and 'cursor.kind === "dart"' in shown
    actions = function_body("renderActions")
    assert '[TEXT.btnPrevious, "previous", true]' in actions and '[TEXT.btnNext, "next", true]' in actions
    assert "complete" not in function_body("renderPicker") and "return" not in function_body("renderPicker")
    assert 'setStatus(TEXT.allEntered)' in function_body("applyView")


def test_a_message_has_one_place_and_is_cleared_at_the_next_action_u36():
    perform = function_body("perform")
    assert perform.index('setStatus("")') < perform.index("await request(")  # refusals never outlive the next action
    for name in ("openSession", "showList", "toggleFrame"):
        assert 'setStatus("")' in function_body(name), name
    assert "state.notice" not in read("app.js") and "view.warning" in function_body("bannerMessage")
