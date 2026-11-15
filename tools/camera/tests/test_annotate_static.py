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
