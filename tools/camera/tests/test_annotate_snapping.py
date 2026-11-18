"""Tool side of the assisted click (T15n, CV16, CV31, A32, S14): availability, decoding failures, cache, end to end.

The snapper is imported without numpy and Pillow (stdlib at import time); the tests that need them skip on a bare
Python. The real `luma.py` and `snap.py` are used, never a fake, except for the availability cases (A32)."""
import os
import subprocess
import sys
import types
from pathlib import Path

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import config
from kosmo_camera.annotate.snapping import UNREADABLE, Outcome, Snapper

ROOT = Path(__file__).resolve().parent.parent


@pytest.fixture
def real():
    pytest.importorskip("numpy")
    pytest.importorskip("PIL")


def _png(path, size=(64, 48), value=128):
    from PIL import Image
    Image.new("RGB", size, (value, value, value)).save(path, "PNG")
    return path


# ---- availability (A32) ----------------------------------------------------------------------------------------------

def _module_that_raises(monkeypatch, name, error):
    """Importing `name` raises `error`, as a broken or absent dependency would."""
    monkeypatch.delitem(sys.modules, name, raising=False)

    class Finder:
        def find_spec(self, fullname, path=None, target=None):
            if fullname == name:
                raise error
            return None

    monkeypatch.setattr(sys, "meta_path", [Finder(), *sys.meta_path])


@pytest.mark.parametrize("missing", ["numpy", "PIL", "numpy.core"])
def test_a32_numpy_or_pillow_missing_gives_unavailable(monkeypatch, missing):
    _module_that_raises(monkeypatch, "kosmo_camera.snap", ModuleNotFoundError(f"No module named {missing!r}",
                                                                              name=missing))
    snapper = Snapper()
    assert snapper.available is False
    assert snapper.available is False  # known once, not tried again
    with pytest.raises(RuntimeError):
        snapper.snap("x.jpg", (1, 1))


@pytest.mark.parametrize("error", [RuntimeError("bug in snap.py"), ModuleNotFoundError("other", name="other_module"),
                                   ImportError("broken install")])
def test_a32_any_other_import_error_is_not_hidden_as_an_absent_venv(monkeypatch, error):
    _module_that_raises(monkeypatch, "kosmo_camera.snap", error)
    with pytest.raises(type(error)):
        Snapper().available  # noqa: B018 (the property is the call under test)


def test_availability_with_the_real_modules(real):
    assert Snapper().available is True


@pytest.mark.parametrize("python", stdlib_pythons())
def test_the_snapper_imports_and_reports_unavailable_without_numpy(python):
    code = ("import sys; sys.path.insert(0, %r)\n"
            "import kosmo_camera.annotate.snapping as s, kosmo_camera.annotate.server, kosmo_camera.annotate.flow\n"
            "assert 'numpy' not in sys.modules and 'PIL' not in sys.modules\n"
            "print(s.Snapper().available)" % str(ROOT))
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True,
                            env={"PATH": os.environ.get("PATH", "")})
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "False"


def test_a32_the_tool_starts_and_says_so_when_numpy_cannot_be_imported(tmp_path):
    blocker = ("import sys\n"
               "class B:\n"
               "    def find_spec(self, name, path=None, target=None):\n"
               "        if name.split('.')[0] in ('numpy', 'PIL'):\n"
               "            raise ModuleNotFoundError('No module named ' + repr(name), name=name)\n"
               "sys.meta_path.insert(0, B())\n"
               "sys.path.insert(0, %r)\n"
               "import runpy; sys.argv = ['kosmo_camera.annotate', '--help']\n"
               "runpy.run_module('kosmo_camera.annotate', run_name='__main__')" % str(ROOT))
    result = subprocess.run([sys.executable, "-I", "-c", blocker], capture_output=True, text=True,
                            env={"PATH": os.environ.get("PATH", "")})
    assert result.returncode == 0, result.stderr
    assert "Recalage : indisponible (numpy ou Pillow absent)" in result.stdout


def test_help_says_the_snapping_is_available_with_numpy_and_pillow(real):
    result = subprocess.run([sys.executable, "-I", "-c",
                             "import sys, runpy; sys.path.insert(0, %r); sys.argv = ['x', '--help']; "
                             "runpy.run_module('kosmo_camera.annotate', run_name='__main__')" % str(ROOT)],
                            capture_output=True, text=True)
    assert result.returncode == 0 and "Recalage : disponible" in result.stdout


# ---- decoding failures with the real luma (S14, S8) ------------------------------------------------------------------

@pytest.mark.parametrize("content", [b"", b"random bytes that are no image" * 50, b"\xff\xd8\xff\xe0" + b"x" * 40])
def test_s14_an_unreadable_image_leaves_the_point_at_the_click_unsnapped_unreadable(real, tmp_path, content):
    path = tmp_path / "dart-001.jpg"
    path.write_bytes(content)
    assert Snapper().snap(path, (12.5, 7.25)) == Outcome((12.5, 7.25), "unsnapped", UNREADABLE)


def test_s14_a_missing_file_and_a_folder_are_unreadable_too(real, tmp_path):
    snapper = Snapper()
    assert snapper.snap(tmp_path / "none.jpg", (1, 2)) == Outcome((1.0, 2.0), "unsnapped", UNREADABLE)
    assert snapper.snap(tmp_path, (1, 2)).reason == UNREADABLE
    assert snapper.cached_images() == 0


def test_s14_a_link_is_never_followed(real, tmp_path):
    target = _png(tmp_path / "real.png")
    link = tmp_path / "link.jpg"
    link.symlink_to(target)
    assert Snapper().snap(link, (5, 5)).reason == UNREADABLE


def test_s14_a_size_other_than_the_listed_one_is_unreadable(real, tmp_path):
    path = _png(tmp_path / "a.png", size=(64, 48))
    snapper = Snapper()
    assert snapper.snap(path, (5, 5), expected_size=(64, 48)).reason != UNREADABLE
    assert snapper.snap(path, (5, 5), expected_size=(65, 48)) == Outcome((5.0, 5.0), "unsnapped", UNREADABLE)


def test_s14_a_decompression_bomb_is_unreadable_and_the_snapper_keeps_working(real, tmp_path, monkeypatch):
    from PIL import Image
    path = _png(tmp_path / "bomb.png", size=(64, 48))
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 100)  # lowered in this test only; monkeypatch restores it
    snapper = Snapper()
    assert snapper.snap(path, (5, 5)).reason == UNREADABLE
    monkeypatch.undo()
    assert snapper.snap(_png(tmp_path / "fine.png"), (5, 5)).reason != UNREADABLE


# ---- cache (CV16, CV31) ----------------------------------------------------------------------------------------------

class CountingLuma:
    """Wraps the real luma module: counts the decodes."""

    def __init__(self):
        from kosmo_camera import luma
        self._luma = luma
        self.calls = []

    def decode_luma(self, path, expected_size=None):
        self.calls.append(str(path))
        return self._luma.decode_luma(path, expected_size)


@pytest.fixture
def counting(real, monkeypatch):
    counter = CountingLuma()
    monkeypatch.setitem(sys.modules, "counting_luma", types.SimpleNamespace(decode_luma=counter.decode_luma))
    return counter


def test_the_cache_keeps_at_most_snap_cache_images_and_the_third_evicts_the_first(counting, tmp_path):
    assert config.SNAP_CACHE_IMAGES == 2
    paths = [_png(tmp_path / f"{n}.png") for n in "abc"]
    snapper = Snapper(luma_module="counting_luma")
    for path in paths:
        snapper.snap(path, (10, 10))
    assert snapper.cached_images() == 2 and len(counting.calls) == 3
    snapper.snap(paths[2], (10, 10))
    snapper.snap(paths[1], (10, 10))
    assert len(counting.calls) == 3  # b and c are cached
    snapper.snap(paths[0], (10, 10))
    assert len(counting.calls) == 4  # a was evicted by c


def test_the_least_recently_used_image_is_the_one_evicted(counting, tmp_path):
    a, b, c = (_png(tmp_path / f"{n}.png") for n in "abc")
    snapper = Snapper(luma_module="counting_luma")
    snapper.snap(a, (10, 10))
    snapper.snap(b, (10, 10))
    snapper.snap(a, (10, 10))  # a is now the most recent
    snapper.snap(c, (10, 10))  # evicts b
    snapper.snap(a, (10, 10))
    assert len(counting.calls) == 3
    snapper.snap(b, (10, 10))
    assert len(counting.calls) == 4


def test_a_changed_file_is_decoded_again_and_its_old_image_is_dropped(counting, tmp_path):
    path = _png(tmp_path / "a.png", value=100)
    snapper = Snapper(luma_module="counting_luma")
    snapper.snap(path, (10, 10))
    snapper.snap(path, (10, 10))
    assert len(counting.calls) == 1
    _png(path, size=(80, 60), value=200)
    os.utime(path, ns=(1, 1))  # a modification time the cache has not seen
    snapper.snap(path, (10, 10))
    assert len(counting.calls) == 2 and snapper.cached_images() == 1


def test_an_unreadable_image_is_not_cached_so_a_repaired_file_is_read(counting, tmp_path):
    path = tmp_path / "a.png"
    path.write_bytes(b"junk")
    snapper = Snapper(luma_module="counting_luma")
    assert snapper.snap(path, (10, 10)).reason == UNREADABLE and snapper.cached_images() == 0
    _png(path)
    assert snapper.snap(path, (10, 10)).reason != UNREADABLE


def test_propose_snaps_each_old_point_and_keeps_none(real, tmp_path):
    path = _png(tmp_path / "a.png")
    outcomes = Snapper().propose(path, [(5, 5), None, (6, 6), None])
    assert [o is None for o in outcomes] == [False, True, False, True]
    assert all(o.status == "unsnapped" and o.reason is not None for o in outcomes if o)  # a flat photo has no wire


# ---- the real snap, end to end through the tool (D46) ----------------------------------------------------------------

def test_d46_calibration_on_a_board_with_ring_wires_clicks_5_px_off_snap_within_a_quarter_pixel(real, tmp_path):
    import http.client
    import json
    import math
    import threading

    from PIL import Image

    from annotate_support import SIZE, make_session
    from kosmo_camera import synth
    from kosmo_camera.annotate.server import make_server

    pixels, truth = synth.render_board(synth.View(yaw=10, pitch=5), [], SIZE, synth.Lighting(noise=2.0), 3,
                                       ring_wires=True)
    folder, _ = make_session(tmp_path / "captures", size=SIZE)
    Image.fromarray(pixels).save(folder / "dart-001.jpg", "JPEG", quality=98, subsampling=0)
    server = make_server(tmp_path / "captures", tmp_path / "annotations", 0, clock=lambda: "00:00:00")
    threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
    try:
        port = server.server_address[1]

        def act(body):
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=60)
            connection.request("POST", "/api/sessions/20261031-140322/actions", json.dumps(body),
                               {"Content-Type": "application/json", "Origin": f"http://127.0.0.1:{port}"})
            response = connection.getresponse()
            return response.status, json.loads(response.read())

        from test_snap import _inner_crossings
        targets = _inner_crossings(truth)
        for x, y in targets:
            status, result = act({"action": "click", "x": x + 3.5, "y": y - 3.5})  # about 5 px off
            assert status == 200, result
        slots = result["session"]["cursor"]["slots"]
        assert [slot["status"] for slot in slots] == ["snapped"] * 4, slots
        assert all(math.dist(slot["point"], target) <= 0.25 for slot, target in zip(slots, targets)), slots
    finally:
        server.shutdown()
        server.server_close()
