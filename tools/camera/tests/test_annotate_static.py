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


def test_double_click_guard_and_view_reset_are_present():
    text = read("app.js")
    assert "CLICK_GUARD_MS" in text and "lastActionAt" in text and "viewKey" in text


# ---- recalibration page and drift flags (T6d; static checks only: no browser nor node in the sandbox) -------------------

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


def test_string_table_has_the_recalibration_strings_and_no_drift_string_a7():
    table = string_table()
    assert table["btnRecalibrate"] == "Recalibrer à partir de cette fléchette (R)"
    assert table["oldPoint"] == "ancien {n}"
    assert not [key for key in table if "drift" in key.lower() or "stale" in key.lower() or "Suggested" in key]


def test_old_points_are_drawn_as_hollow_crosses_labelled_ancien_k():
    source = read("app.js")
    assert "function drawHollowCross" in source
    assert re.search(r"fill\(TEXT\.oldPoint,\s*\{\s*n:\s*index \+ 1\s*\}\)", source)
    assert "cursor.previousPoints" in source and "previousSegment" not in source and "view.segments" not in source  # A9


def test_picker_label_is_the_number_ticked_when_annotated_without_any_drift_mark_a7():
    body = function_body("pickerText")
    assert "TEXT.pickerDone" in body and "TEXT.pickerLabel" in body and "dart.status === null" in body
    assert "!" not in re.sub(r"!==|!=", "", body) and "pickerText(dart)" in read("app.js")


def test_the_page_shows_the_server_progress_badge_and_calibration_label_without_counting_a3():
    source = read("app.js")
    assert "calibrationStatus" not in source and "finished" not in source
    assert "Terminée" not in source.replace(BADGE_LINE(source), "")  # the badge table is the only place
    assert "item.badge" in function_body("sessionHead") and "item.steps" in function_body("stepRows")
    panel = function_body("renderPanel")
    assert "view.progress" in panel and "view.calibrationLabel" in panel
    for body in (panel, function_body("sessionHead"), function_body("stepRows"), function_body("stepText"),
                 function_body("sessionLine")):
        assert not re.search(r"\.filter\(|\.length|\.every\(|\.some\(|\.reduce\(", body)
    assert "calibrated" not in source and "segments" not in function_body("renderPanel")


def BADGE_LINE(source):
    return re.search(r"const BADGE_CLASS = \{.*?\};", source).group(0)


def test_no_dérive_text_nor_drift_field_remains_in_the_page_cv30_a7():
    for name in FILES:
        text = read(name)
        assert "dérive" not in text.lower() and "À recalibrer" not in text, name
        assert not re.search(r"driftLabel|driftStale|\.drift\b|item\.state|view\.state|staleMeasure|session-stale|"
                             r"banner\.drift", text), name


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
    assert "item.info" in source  # drift information is an info line of the card (CV30), never an instruction


def test_warning_colour_has_a_contrast_of_at_least_4_5_on_background_and_panel():
    declared = colors()
    assert contrast(declared["--warn"], declared["--bg"]) >= 4.5
    assert contrast(declared["--warn"], declared["--panel"]) >= 4.5


# ---- assisted point screen (T15f: CV16, CV25, CV31, D45 U25 to U29, U31; no browser nor node in the sandbox) ---------

ASSISTED_STRINGS = (
    "Enregistrer (Entrée)", "Point caché (H)", "Annuler (U)", "Quitter (Échap)", "Contrôle de calibration (K)",
    "Photos contrôlées : {photo} / {photoTotal} · Images vidéo : {video} / {videoTotal}",
    "Points prêts : {n} / 4 (Entrée pour enregistrer)", "Points prêts : {n} / 4 : placer les points manquants",
    "Point {n}, {status}", "recalé", "non recalé", "caché",
    "à cliquer", "Cliquer près du bon croisement : l'outil le recale.", "H seulement si une fléchette cache le point",
    "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)",
    "Photo illisible : le recalage est impossible pour cette photo")


def function_body(name):
    match = re.search(rf"function {name}\([^)]*\) \{{(.*?)\n\}}\n", read("app.js"), re.DOTALL)
    assert match, name
    return match.group(1)


@pytest.mark.parametrize("text", ASSISTED_STRINGS)
def test_each_assisted_screen_string_is_a_constant_written_once(text):
    assert read("app.js").count(f'"{text}"') == 1
    assert text in string_table().values()


def test_the_inset_zoom_is_60_source_px_shown_at_180_css_px_and_the_save_guard_is_300_ms():
    source = read("app.js")
    assert re.search(r"const SNAP_INSET_PX = 60;", source) and re.search(r"const INSET_SIZE = 180;", source)
    assert re.search(r"const SAVE_GUARD_MS = 300;", source) and re.search(r"const CLICK_GUARD_MS = 300;", source)
    assert source.index("const CLICK_GUARD_MS") < source.index("const SAVE_GUARD_MS")


def test_the_obsolete_strings_are_gone_and_the_panel_is_400px_on_the_assisted_screen():
    source = read("app.js")
    assert "Points posés" not in source and "zoomCentre" not in source
    assert re.search(r"#panel\.assisted\s*\{[^}]*flex:\s*0 1 400px", read("style.css"))


def test_the_keys_of_the_assisted_screen_are_bound_in_the_document_handler():
    source = read("app.js")
    assert 'document.addEventListener("keydown", onKey)' in source
    assert '"Enter"' in source and "/^[1-4]$/" in function_body("onAssistedKey")
    assistant = function_body("onAssistedKey")
    assert 'key === "h"' in assistant and '"hidden_point"' in assistant and 'key === "u"' in assistant
    handler = function_body("onKey")
    assert 'key === "Escape"' in handler and 'key === "0"' in handler and "Arrow" in handler
    assert re.search(r'stage\.addEventListener\("wheel", onWheel', source)


def test_entree_always_saves_with_the_repeat_and_save_guards_u25_u29():
    enter = function_body("onEnter")
    assert "event.preventDefault()" in enter and "event.repeat" in enter
    assert "SAVE_GUARD_MS" in enter and "state.lastSaveAt" in enter
    assert enter.index("preventDefault") < enter.index("saveItem()")
    handler = function_body("onKey")
    assert 'key === "Enter" && isAssisted(state.view)' in handler
    assert handler.index('"Enter"') < handler.index("state.busy")  # a busy Entrée is still not a button click
    assert "state.lastSaveAt = Date.now()" in read("app.js")


def test_save_sends_the_open_item_and_the_control_source():
    body = function_body("saveItem")
    assert 'action: "save", number, source: cursor.source' in body and "!cursor.canSave" in body and "POINT_COUNT" not in read("app.js")  # A8: the server says


def test_every_assisted_button_blurs_after_its_click_u25():
    assert "button.blur()" in function_body("assistedButton")
    body = function_body("renderAssistedActions")
    assert body.count("assistedButton(") == 4 and "makeButton(" not in body


def test_insets_are_drawn_from_the_loaded_image_with_an_aria_label_u31():
    body = function_body("drawInsets")
    assert "context.drawImage(image," in body
    assert not re.search(r"fetch|new Image|\.src\s*=|/photo|/api", body)  # no new request
    assert 'canvas.setAttribute("aria-label", fill(TEXT.insetAria' in function_body("renderAssistedPanel")
    assert 'id="inset-4"' in read("index.html") and 'role="img"' in read("index.html")
    for status in ("pointSnapped", "pointUnsnapped", "pointHidden", "pointEmpty"):
        assert f"TEXT.{status}" in function_body("pointStatusText")


def test_the_unreadable_and_unavailable_banners_and_the_ignored_clicks_are_wired():
    banner = function_body("bannerMessage")
    assert "TEXT.snapUnavailable" in banner and "TEXT.photoUnreadable" in banner and "view.cursor.unreadable" in banner
    assert "state.notice" in banner and "result.notice" in function_body("perform")
    assert "snapAvailable === false" in function_body("onStageClick")


def test_video_frames_load_from_the_frame_route_and_skip_the_truth_size_check():
    source = read("app.js")
    assert '"/api/frame"' in function_body("imageUrl") and '"/photo"' in function_body("imageUrl")
    assert 'cursor.source === "video"' in function_body("imageLoaded")


def test_the_page_builds_text_with_textcontent_only_on_the_assisted_screen():
    for name in ("renderAssistedPanel", "drawInsets", "bannerMessage", "controlProgressText"):
        assert not re.search(r"innerHTML|insertAdjacent", function_body(name))


def test_the_reopened_notice_comes_from_the_server_and_the_page_infers_no_flow_event_a4():
    source = read("app.js")
    assert "reopenedItem" not in source and "TEXT.reopened" not in source and "rouverte" not in source
    perform = function_body("perform")
    assert "result.notice" in perform and "before" not in perform and "cursor.number" not in perform
    assert "state.notice" in function_body("bannerMessage")
    assert "setText(banner," in function_body("renderPanel")  # textContent only


def test_the_page_draws_the_next_slot_and_the_diagram_flag_of_the_server_a5():
    source = read("app.js")
    assert "nextSlotIndex" not in source and "noProposals" not in source and "trackItem" not in source
    panel = function_body("renderAssistedPanel")
    assert "cursor.nextSlot" in panel and "findIndex" not in panel
    assert "cursor.diagram" in function_body("renderPanel")


def test_a_message_has_one_place_and_is_cleared_at_the_next_action_u36():
    perform = function_body("perform")
    assert perform.index('setStatus("")') < perform.index("await request(")  # refusals never outlive the next action
    assert "state.notice = null" in perform and perform.index("state.notice = null") < perform.index("await request(")
    assert 'setStatus("")' in function_body("openSession") and 'setStatus("")' in function_body("showList")
    assert "state.notice" in function_body("bannerMessage")  # the server notice goes to #banner, refusals to #status


def test_entree_with_fewer_than_four_points_says_why_u37():
    body = function_body("saveItem")
    assert "TEXT.pointsMissing" in body and "cursor.ready" in body and "setStatus(" in body
    assert body.index("TEXT.pointsMissing") < body.index("perform(")
    assert string_table()["pointsMissing"] == "Points prêts : {n} / 4 : placer les points manquants"


def test_the_diagram_follows_the_action_buttons_and_marks_done_points_without_colour_alone_u38_u39():
    html = read("index.html")
    assert html.index('id="actions"') < html.index('id="zoom-actions"') < html.index('id="diagram"')
    assert "max-width: 140px" in read("style.css")  # with the insets, Enregistrer stays visible at 1080p
    body = function_body("drawDiagramPoint")
    assert "DONE_MARK" in body and "isDone" in body and 'DONE_MARK = "✓"' in read("app.js")


# ---- journey page (T15g: CV16, CV30, D42 U23, U19, U35; the strings are pinned to the Python constants) --------------

def test_the_step_row_templates_and_card_strings_are_defined_once():
    table = string_table()
    assert table["stepCalibrationDone"] == "Calibration : faite" and table["stepCalibrationTodo"] == "Calibration : à faire"
    assert table["stepTips"] == "Pointes : {done} / {total}"
    assert table["stepControlPhoto"] == "Contrôle photo : {done} / {total}"
    assert table["stepControlVideo"] == "Contrôle vidéo : {done} / {total}"
    assert table["infoTitle"] == "Information" and table["nextStep"] == "Prochaine étape"
    assert table["btnContinue"] == "Continuer"
    for text in ("Prochaine étape", "Continuer", "Information"):
        assert len(re.findall(rf'\w+: "{text}"', read("app.js"))) == 1, text


def test_the_page_restates_no_instruction_of_the_server_u35():
    source = read("app.js")
    for fragment in ("Cliquer les 4 points", "Pointer les fléchettes", "Contrôle terminé", "Session terminée",
                     "Session illisible", "Contrôle indisponible", "Images vidéo absentes", "bouton Continuer"):
        assert fragment not in source, fragment


def test_journey_help_and_banner_are_three_distinct_elements_and_only_journey_has_the_instruction_style_u19():
    html, css, source = read("index.html"), read("style.css"), read("app.js")
    for element in ("journey", "help", "banner"):
        assert html.count(f'id="{element}"') == 1, element
    assert 'id="instruction"' not in html and '$("instruction")' not in source
    rules = dict((name.strip(), body) for name, body in re.findall(r"(?m)^([#.][\w-]+(?:, [#.][\w-]+)*)\s*\{([^}]*)\}", css)
                 if name.strip() in ("#journey", "#help, #recalibration-help", "#banner"))
    assert set(rules) == {"#journey", "#help, #recalibration-help", "#banner"}
    assert int(re.search(r"font-size:\s*(\d+)px", rules["#journey"]).group(1)) >= 20
    assert "font-weight: bold" in rules["#journey"]
    for other in ("#help, #recalibration-help", "#banner"):
        assert "font-weight: bold" not in rules[other]
        size = re.search(r"font-size:\s*(\d+)px", rules[other])
        assert size is None or int(size.group(1)) < 20
    assert css.count("border-bottom: 3px solid var(--primary-bg)") == 1  # the accent of the instruction line, once
    assert source.count('$("journey")') == 1 and 'setText($("banner")' not in source.replace("setText(banner,", "")
    calls = re.findall(r"setJourney\(([^)]*)\)", source)
    assert len(calls) >= 3  # the list, the session screen and setJourney's own definition
    assert [c for c in calls if c not in ('data.instruction', 'view.instruction', '""', "text")] == []


def test_the_focus_card_carries_the_mark_and_continuer_sends_the_server_payload_u15():
    card = function_body("sessionLine")
    assert "TEXT.nextStep" in card and "focus" in card
    assert "state.list.continuePayload" in function_body("sessionActions")
    assert "continueFocus" in function_body("sessionActions") and "TEXT.btnContinue" in function_body("sessionActions")
    body = function_body("continueFocus")
    assert "list.continuePayload" in body and "openSession(list.focus)" in body and "perform(list.continuePayload)" in body
    assert body.index("openSession(") < body.index("perform(")
    assert "item.role" in function_body("sessionHead")  # the role is rendered on each card (Q9)
    assert "state.list = data" in function_body("showList") and "state.list = null" in function_body("showList")


def test_entree_on_the_list_runs_continuer_only_when_no_button_or_link_has_the_focus_u23_d42():
    body = function_body("onListKey")
    assert 'key !== "Enter"' in body and "document.activeElement" in body
    assert '"BUTTON"' in body and '"A"' in body and "event.repeat" in body
    assert body.index("activeElement") < body.index("continueFocus()")
    handler = function_body("onKey")
    assert "!state.view" in handler and "onListKey(key, event)" in handler
    assert handler.index("onListKey(") < handler.index('key === "Enter" && isAssisted')  # the assisted rule stays
    assert 'key === "Enter" && isAssisted(state.view)' in handler


def test_the_card_shows_information_apart_and_skips_nothing_else_cv30():
    body = function_body("infoBox")
    assert "TEXT.infoTitle" in body and "item.info" in body and '"info-box"' in body
    css = read("style.css")
    assert re.search(r"\.info-box\s*\{[^}]*background", css)


# ---- fragility of the T15f static tests (T1, T2 of the code review) ---------------------------------------------------

def test_each_assisted_button_label_is_an_argument_of_assisted_button_t1():
    body = function_body("renderAssistedActions")
    for label in ("btnSave", "btnHidePoint", "btnUndo", "btnQuit"):
        assert re.search(rf"assistedButton\(\s*TEXT\.{label}\s*,", body), label


def test_the_save_time_is_stamped_only_by_a_save_action_t2():
    source = read("app.js")
    assert source.count("state.lastSaveAt = Date.now()") == 1
    perform = function_body("perform")
    stamp = re.search(r'if \(payload\.action === "save"\) \{\s*state\.lastSaveAt = Date\.now\(\);\s*\}', perform)
    assert stamp, "the stamp belongs to the save action of perform"
    assert "lastSaveAt" in function_body("onEnter") and "lastSaveAt" not in function_body("saveItem")
