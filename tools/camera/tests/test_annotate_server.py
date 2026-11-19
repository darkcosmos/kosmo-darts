"""Annotation server (T6a, CV16, D8): routes, write-route attacks, error states, read-only captures, JPEG size."""
import hashlib
import json
import os
import re
import socket
import subprocess
import threading
from pathlib import Path

import pytest

from annotate_support import NAME, SIZE, jpeg_bytes, make_session, stdlib_pythons, tree_listing
from kosmo_camera import annotations as an
from kosmo_camera import frames_file as ff
from kosmo_camera.annotate import server as server_module
from kosmo_camera.annotate.__main__ import main
from kosmo_camera.annotate.frames_state import FRAMES_UNREADABLE, FrameSources
from kosmo_camera.annotate.jpeg_size import JpegError, jpeg_size, read_size
from kosmo_camera.annotate.server import IGNORED_LABEL, make_server

POINTS = [(300, 300), (1700, 300), (1700, 900), (300, 900)]  # inside a 2000 x 1000 photo
SEGMENT = an.Calibration(1, "dart-001.jpg", *SIZE, tuple((float(x), float(y)) for x, y in POINTS))
REMOVED_KINDS = ("recalibrate", "select", "save", "control", "hidden_point")  # stopped by ADR 0020 (Q1 B)
VIDEO_SIZE = (1920, 1080)
OTHER = "20261101-090000"


class Tool:
    def __init__(self, root, static_dir):
        self.captures = root / "captures"
        self.annotations = root / "annotations"
        self.captures.mkdir()
        self.root = root
        self.static_dir = static_dir
        self.server = None

    def start(self, frames=None):
        self.server = make_server(self.captures, self.annotations, 0, self.static_dir, clock=lambda: "12:34:56",
                                  frames=frames)
        self.port = self.server.server_address[1]
        threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
        return self

    def request(self, method, path, body=None, headers=None, raw_body=None):
        sent = {"Host": f"127.0.0.1:{self.port}", "Connection": "close"}
        if body is not None:
            raw_body = json.dumps(body).encode("utf-8")
            sent.update({"Content-Type": "application/json", "Origin": f"http://127.0.0.1:{self.port}"})
        if raw_body is not None:
            sent["Content-Length"] = str(len(raw_body))
        sent.update(headers or {})
        head = f"{method} {path} HTTP/1.1\r\n" + "".join(f"{k}: {v}\r\n" for k, v in sent.items() if v is not None)
        with socket.create_connection(("127.0.0.1", self.port), timeout=5) as connection:
            connection.sendall(head.encode("latin-1") + b"\r\n" + (raw_body or b""))
            data = b""
            while chunk := connection.recv(65536):
                data += chunk
        header_text, _, payload = data.partition(b"\r\n\r\n")
        lines = header_text.decode("latin-1").split("\r\n")
        response_headers = dict(line.split(": ", 1) for line in lines[1:])
        return int(lines[0].split()[1]), response_headers, payload

    def json(self, method, path, body=None, **kwargs):
        status, headers, payload = self.request(method, path, body, **kwargs)
        return status, json.loads(payload)

    def act(self, action, name=NAME, **fields):
        return self.json("POST", f"/api/sessions/{name}/actions", {"action": action, **fields})

    def write_annotations(self, annotations, name=NAME):
        """An annotation file as an earlier tool left it (segments and controls are no longer entered, N24)."""
        self.annotations.mkdir(exist_ok=True)
        an.write_atomic(self.annotations / f"{name}.json", annotations)


@pytest.fixture
def tool(tmp_path):
    static = tmp_path / "static"
    static.mkdir()
    (static / "index.html").write_text("<!doctype html><title>test</title>", encoding="utf-8")
    created = Tool(tmp_path, static)
    yield created
    if created.server is not None:
        created.server.shutdown()
        created.server.server_close()


def annotation_file(tool, name=NAME):
    return tool.annotations / f"{name}.json"


# ---- jpeg_size ------------------------------------------------------------------------------------------------------

def test_jpeg_size_reads_the_start_of_frame_after_a_large_exif_segment(tmp_path):
    path = tmp_path / "a.jpg"
    path.write_bytes(jpeg_bytes(4080, 3072, exif_padding=60000))
    assert jpeg_size(path) == (4080, 3072)


def test_jpeg_size_is_the_stored_pixel_order_whatever_the_exif_orientation(tmp_path):
    # D8: a portrait-oriented phone photo is stored 4080x3072; an Orientation tag in the APP1 segment is not read.
    exif = b"\xff\xe1" + (12).to_bytes(2, "big") + b"Exif\x00\x00" + b"\x06\x00\x00\x00"
    data = jpeg_bytes(4080, 3072)
    path = tmp_path / "o.jpg"
    path.write_bytes(data[:2] + exif + data[2:])
    assert jpeg_size(path) == (4080, 3072)


@pytest.mark.parametrize("data", [b"", b"\xff\xd8", b"GIF89a....", b"\xff\xd8\xff\xda\x00\x02", b"\xff\xd8\xff\xc0\x00\x11\x08",
                                  jpeg_bytes(0, 5), b"\xff\xd8\xff\xe0\x00\x01"])
def test_jpeg_size_refuses_what_is_not_a_readable_jpeg(tmp_path, data):
    path = tmp_path / "bad.jpg"
    path.write_bytes(data)
    with pytest.raises(JpegError):
        jpeg_size(path)


def test_jpeg_size_missing_file_is_a_jpeg_error(tmp_path):
    with pytest.raises(JpegError):
        jpeg_size(tmp_path / "none.jpg")


def test_jpeg_size_skips_dht_that_looks_like_a_frame(tmp_path):
    data = jpeg_bytes(300, 200)
    dht = b"\xff\xc4" + (4).to_bytes(2, "big") + b"\x00\x00"
    path = tmp_path / "d.jpg"
    path.write_bytes(data[:2] + dht + data[2:])
    assert jpeg_size(path) == (300, 200)


def test_read_size_reads_a_stream():
    import io
    assert read_size(io.BytesIO(jpeg_bytes(7, 9))) == (7, 9)


# ---- routes, headers, flow over HTTP --------------------------------------------------------------------------------

def test_socket_is_bound_to_loopback_only(tool):
    make_session(tool.captures)
    tool.start()
    assert tool.server.server_address[0] == "127.0.0.1"


def test_index_static_and_headers_on_every_answer(tool):
    make_session(tool.captures)
    tool.start()
    for path in ("/", "/api/sessions", f"/api/sessions/{NAME}", "/static/index.html", "/photo/" + NAME + "/dart-001.jpg",
                 "/nope"):
        status, headers, _ = tool.request("GET", path)
        assert headers["Content-Security-Policy"] == "default-src 'self'", path
        assert headers["X-Content-Type-Options"] == "nosniff", path
        assert headers["Cache-Control"] == "no-store", path
    status, headers, body = tool.request("GET", "/")
    assert status == 200 and b"<title>test</title>" in body
    assert tool.request("GET", "/static/other.js")[0] == 404  # fixed list only
    assert tool.request("GET", "/static/app.js")[0] == 404  # in the list but absent from the folder


def test_photo_route_serves_only_files_named_in_the_truth_file(tool):
    make_session(tool.captures)
    (tool.captures / NAME / "dart-009.jpg").write_bytes(jpeg_bytes(10, 10))
    (tool.captures / NAME / "notes.jpg").write_bytes(b"x")
    tool.start()
    status, headers, body = tool.request("GET", f"/photo/{NAME}/dart-001.jpg")
    assert (status, headers["Content-Type"], body[:2]) == (200, "image/jpeg", b"\xff\xd8")
    for file in ("dart-009.jpg", "notes.jpg", "truth.json", "dart-002.jpg", "..%2ftruth.json", "dart-001.jpg%00.png"):
        assert tool.request("GET", f"/photo/{NAME}/{file}")[0] == 404, file


def test_session_list_and_open_with_progress_and_resume_cursor(tool):
    make_session(tool.captures)
    tool.start()
    status, body = tool.json("GET", "/api/sessions")
    session = body["sessions"][0]
    assert session == {"name": NAME, "message": None, "warning": None, "label": None,
                       "progressLabel": "0 / 3 pointes saisies", "calibrationLabel": "Non calibrée"}
    assert body == {"sessions": [session], "framesWarning": None}
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    # no calibration screen: a session without segment opens on its first dart (N24)
    assert view["cursor"] == {"kind": "dart", "number": 1, "photo": "dart-001.jpg"}
    assert view["segments"] == []
    assert [d["number"] for d in view["darts"]] == [1, 3, 4] and view["skipped"] == [2, 5]
    assert view["readOnly"] is False and view["photoSize"] == list(SIZE)
    assert all(re.fullmatch(r"[DT]?\d{1,2}", d["value"]) for d in view["darts"])  # true value shown large by the page
    assert all(isinstance(d["volley"], int) for d in view["darts"])


def test_full_flow_autosaves_each_change_and_reports_the_saved_time(tool):
    make_session(tool.captures)
    tool.start()
    assert not annotation_file(tool).exists()  # opening writes nothing
    status, result = tool.act("click", x=100.5, y=200.5)
    assert result["saved"] == "Enregistré à 12:34:56" and result["session"]["cursor"]["number"] == 3
    tool.act("hidden")
    saved = an.read(annotation_file(tool))
    assert saved.calibrations == () and [d.number for d in saved.darts] == [1, 3]  # tips need no segment (N24)
    # reopening resumes at the first dart not annotated, with the earlier tips returned
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"]["number"] == 4 and view["darts"][0]["tip"] == [100.5, 200.5]
    status, listing = tool.json("GET", "/api/sessions")
    assert listing["sessions"][0]["progressLabel"] == "2 / 3 pointes saisies"


def test_clear_goes_through_the_post_route_and_there_is_no_delete_or_put(tool):
    make_session(tool.captures)
    tool.start()
    tool.act("hidden")
    status, view = tool.act("previous")
    status, result = tool.act("clear")
    assert result["session"]["darts"][0]["status"] is None
    assert an.read(annotation_file(tool)).darts == ()
    before = tree_listing(tool.root)
    for method in ("DELETE", "PUT", "PATCH"):
        assert tool.request(method, f"/api/sessions/{NAME}/actions", {"action": "clear"})[0] == 405
        assert tool.request(method, f"/api/sessions/{NAME}")[0] == 405
    assert tree_listing(tool.root) == before


def test_flow_errors_answer_400_and_change_nothing(tool):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.root)
    for body in ({"action": "bogus"}, {"action": "click"}, {"action": "click", "x": -1, "y": 5},
                 {"action": "click", "x": 5000, "y": 5}, {"action": "click", "x": 5, "y": 3073},
                 {"action": "click", "x": True, "y": 5}, {"action": "click", "x": "5", "y": 5},
                 {"action": "goto", "number": 2}, {"action": "goto", "number": "1"}, {"action": "hidden", "extra": 1},
                 [], "x", {}):
        status, _ = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert status == 400, body
    assert tree_listing(tool.root) == before


def test_a_huge_integer_coordinate_answers_400_not_500_and_changes_nothing(tool):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.root)
    for body in ({"action": "click", "x": 10 ** 400, "y": 5}, {"action": "click", "x": 5, "y": -(10 ** 400)}):
        status, result = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert (status, result) == (400, {"error": "Coordonnées invalides."}), body
    assert tree_listing(tool.root) == before
    assert tool.act("hidden")[0] == 200  # the server still serves


def test_clicks_are_checked_against_the_real_jpeg_size_not_only_the_truth_size_d8(tool):
    # truth says 4080x3072 but the stored JPEG is 2000x1000: a tip beyond the real size fails
    make_session(tool.captures, jpeg_size=(2000, 1000))
    tool.start()
    assert tool.act("click", x=2001, y=10)[0] == 400
    assert tool.act("click", x=10, y=1001)[0] == 400
    assert not annotation_file(tool).exists()
    assert tool.act("click", x=1999.5, y=999.5)[0] == 200


def test_click_beyond_the_truth_photo_size_is_refused_when_the_jpeg_is_larger(tool):
    make_session(tool.captures, jpeg_size=(6000, 4000))
    tool.start()
    assert tool.act("click", x=5000, y=100)[0] == 400  # validate_against: tip outside the truth photo size


def test_unreadable_jpeg_answers_a_json_500_and_the_server_keeps_serving(tool):
    make_session(tool.captures)
    (tool.captures / NAME / "dart-001.jpg").write_bytes(b"not a jpeg")
    tool.start()
    status, result = tool.act("click", x=1, y=1)
    assert status == 500 and "Photo" in result["error"]
    assert tool.json("GET", "/api/sessions")[0] == 200


# ---- write route attacks ----------------------------------------------------------------------------------------------

@pytest.mark.parametrize("name", ["..%2f..%2fx", "%2e%2e", "%2e%2e%2f%2e%2e", "a%2fb", "a%5cb", "..%5c..%5cx", "x%00y",
                                  "20261031-999999", "", "%FF"])
def test_unlisted_or_hostile_session_names_answer_4xx_and_write_nothing(tool, name):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.root)
    for method, path in (("POST", f"/api/sessions/{name}/actions"), ("GET", f"/api/sessions/{name}"),
                         ("GET", f"/photo/{name}/dart-001.jpg")):
        body = {"action": "hidden"} if method == "POST" else None
        status, _, _ = tool.request(method, path, body)
        assert 400 <= status < 500, (method, path, status)
    for path in ("/api/sessions//actions", "/api/sessions/a/b/actions", "/api/sessions/\\x/actions"):
        assert 400 <= tool.request("POST", path, {"action": "hidden"})[0] < 500
    assert tree_listing(tool.root) == before


def test_literal_slash_and_backslash_in_a_name_are_not_sessions(tool):
    make_session(tool.captures)
    tool.start()
    for path in ("/api/sessions/../actions", f"/api/sessions/{NAME}/../{NAME}/actions", "/api/sessions/..\\x/actions"):
        assert 400 <= tool.request("POST", path, {"action": "hidden"})[0] < 500


def test_nothing_outside_the_annotations_folder_is_ever_created(tool):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.captures)
    tool.act("hidden")
    tool.act("off_board")
    assert tree_listing(tool.captures) == before  # captures untouched (hashes)
    created = sorted(p.name for p in tool.annotations.iterdir())
    assert created == [f"{NAME}.json"]  # no temporary file left behind


@pytest.mark.parametrize("headers,expected", [
    ({"Content-Length": None}, 411),
    ({"Transfer-Encoding": "chunked", "Content-Length": None}, 411),
    ({"Transfer-Encoding": "chunked"}, 411),
    ({"Content-Length": str(64 * 1024 + 1)}, 413),
    ({"Content-Length": "abc"}, 400),
    ({"Content-Length": "-1"}, 400),
    ({"Content-Type": "text/plain"}, 415),
    ({"Content-Type": None}, 415),
    ({"Content-Type": "application/x-www-form-urlencoded"}, 415),
    ({"Origin": None}, 403),
    ({"Origin": "http://evil.example"}, 403),
    ({"Origin": "http://127.0.0.1:1"}, 403),
    ({"Origin": "null"}, 403),
    ({"Host": "evil.example"}, 403),
    ({"Host": None}, 403),
    ({"Host": "127.0.0.1:1"}, 403)])
def test_write_route_refuses_bad_headers_and_changes_nothing(tool, headers, expected):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.root)
    status, _, _ = tool.request("POST", f"/api/sessions/{NAME}/actions", {"action": "hidden"}, headers=headers)
    assert status == expected
    assert tree_listing(tool.root) == before


def test_write_route_accepts_localhost_origin_host_and_json_charset(tool):
    make_session(tool.captures)
    tool.start()
    port = tool.port
    status, _, _ = tool.request("POST", f"/api/sessions/{NAME}/actions", {"action": "next"},
                                headers={"Origin": f"http://localhost:{port}", "Host": f"localhost:{port}",
                                         "Content-Type": "application/json; charset=utf-8"})
    assert status == 200


def test_oversized_body_is_refused_before_reading_it(tool):
    make_session(tool.captures)
    tool.start()
    status, _, _ = tool.request("POST", f"/api/sessions/{NAME}/actions", headers={
        "Content-Type": "application/json", "Origin": f"http://127.0.0.1:{tool.port}",
        "Content-Length": str(10 * 1024 * 1024)})
    assert status == 413


@pytest.mark.parametrize("raw", [b"{", b"\xff\xfe", b'{"action": NaN}', b"[1]", b"nul"])
def test_malformed_json_bodies_answer_400(tool, raw):
    make_session(tool.captures)
    tool.start()
    status, _, _ = tool.request("POST", f"/api/sessions/{NAME}/actions", raw_body=raw, headers={
        "Content-Type": "application/json", "Origin": f"http://127.0.0.1:{tool.port}"})
    assert status == 400


def test_host_is_checked_on_every_route(tool):
    make_session(tool.captures)
    tool.start()
    for path in ("/", "/static/index.html", "/api/sessions", f"/api/sessions/{NAME}", f"/photo/{NAME}/dart-001.jpg"):
        assert tool.request("GET", path, headers={"Host": "rebind.example:80"})[0] == 403, path


# ---- error states -------------------------------------------------------------------------------------------------

def states(tool):
    status, body = tool.json("GET", "/api/sessions")
    assert status == 200
    return {s["name"]: s for s in body["sessions"]}


def test_invalid_truth_reaches_the_list_with_its_cause_and_is_not_writable(tool):
    folder, _ = make_session(tool.captures)
    (folder / "truth.json").write_text("{", encoding="utf-8")
    make_session(tool.captures, name="20261101-100000")
    tool.start()
    listed = states(tool)
    assert "invalide" in listed[NAME]["message"] and "JSON" in listed[NAME]["message"]
    assert listed["20261101-100000"]["message"] is None  # one bad session does not hide the others
    assert tool.act("hidden")[0] == 409
    assert not annotation_file(tool).exists()


def test_truth_of_another_session_name_is_invalid(tool):
    folder, _ = make_session(tool.captures)
    folder.rename(tool.captures / "20261101-100000")
    tool.start()
    assert "invalide" in states(tool)["20261101-100000"]["message"]


def test_rejected_annotation_file_is_read_only_with_cause_and_never_overwritten(tool):
    make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text('{"format": "other"}', encoding="utf-8")
    tool.start()
    before = tree_listing(tool.root)
    listed = states(tool)[NAME]
    assert "format" in listed["message"] and listed["progressLabel"] is None
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["readOnly"] is True
    assert tool.act("hidden")[0] == 409 and tool.act("click", x=1, y=1)[0] == 409
    assert tree_listing(tool.root) == before


def test_annotation_contradicting_the_truth_is_rejected_too(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    bad = an.Annotations(NAME, sha, (), (an.DartAnnotation(2, an.Status.HIDDEN, None),))  # dart 2 is voided
    annotation_file(tool).write_text(an.encode(bad), encoding="utf-8")
    tool.start()
    assert states(tool)[NAME]["message"] and states(tool)[NAME]["progressLabel"] is None


def test_annotation_file_over_1_mib_is_refused_and_untouched(tool):
    make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_bytes(b" " * (an.MAX_BYTES + 1))
    tool.start()
    before = tree_listing(tool.root)
    listed = states(tool)[NAME]
    assert "1 Mio" in listed["message"]
    assert tool.act("hidden")[0] == 409
    assert tree_listing(tool.root) == before


def test_stale_annotation_warns_and_still_allows_editing(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    stale = an.Annotations(NAME, "0" * 64, (), ())
    annotation_file(tool).write_text(an.encode(stale), encoding="utf-8")
    tool.start()
    listed = states(tool)[NAME]
    assert listed["message"] is None and "changé" in listed["warning"]
    status, result = tool.act("click", x=1, y=1)
    assert status == 200 and result["session"]["warning"]


def _v1_text(sha):
    """A version 1 file as the part A tool wrote it: calibration on dart 1, dart 1 visible, dart 3 hidden."""
    return json.dumps({"format": an.FORMAT, "version": 1, "session": NAME, "truthSha256": sha,
                       "calibration": {"photo": "dart-001.jpg", "width": SIZE[0], "height": SIZE[1],
                                       "points": [{"x": float(x), "y": float(y)} for x, y in POINTS]},
                       "darts": [{"number": 1, "status": "VISIBLE", "tip": {"x": 100.5, "y": 200.5}},
                                 {"number": 3, "status": "HIDDEN", "tip": None}]}, indent=2)


def test_v1_file_opened_listed_and_browsed_stays_byte_identical_cv16_cv20(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text(_v1_text(sha), encoding="utf-8")
    tool.start()
    before = tree_listing(tool.root)
    listed = states(tool)[NAME]
    assert listed["message"] is None and listed["calibrationLabel"] == "Calibrée"
    assert listed["progressLabel"] == "2 / 3 pointes saisies"
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"]["number"] == 4
    assert view["segments"] == [{"fromDart": 1, "photo": "dart-001.jpg", "points": [list(map(float, p)) for p in POINTS]}]
    for action in ("previous", "next"):
        assert tool.act(action)[0] == 200
    assert tool.act("goto", number=1)[0] == 200
    assert tree_listing(tool.root) == before


def test_first_saved_change_rewrites_a_v1_file_as_v3_with_every_tip_kept_cv16_cv20_cv25(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text(_v1_text(sha), encoding="utf-8")
    tool.start()
    status, result = tool.act("off_board")  # resume cursor: dart 4
    assert status == 200 and result["saved"] == "Enregistré à 12:34:56"
    saved = json.loads(annotation_file(tool).read_text(encoding="utf-8"))
    assert saved["version"] == 3 and "calibration" not in saved and saved["controls"] == []
    assert saved["calibrations"] == [{"fromDart": 1, "photo": "dart-001.jpg", "width": SIZE[0], "height": SIZE[1],
                                      "points": [{"x": float(x), "y": float(y)} for x, y in POINTS],
                                      "snap": ["manual"] * 4}]
    assert saved["truthSha256"] == sha
    assert saved["darts"] == [{"number": 1, "status": "VISIBLE", "tip": {"x": 100.5, "y": 200.5}},
                              {"number": 3, "status": "HIDDEN", "tip": None},
                              {"number": 4, "status": "OFF_BOARD", "tip": None}]


def test_stale_v1_file_keeps_its_warning_after_migration_cv20(tool):
    make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text(_v1_text("0" * 64), encoding="utf-8")
    tool.start()
    listed = states(tool)[NAME]
    assert listed["message"] is None and "changé" in listed["warning"]


def test_session_without_judged_dart_is_listed_as_ignored_and_not_openable_cv3(tool):
    make_session(tool.captures, name="20261101-100000", judged=(), voided=(1, 2), no_photo=(3,))
    make_session(tool.captures, name="20261102-100000", judged=(), voided=(), no_photo=(1, 2))
    make_session(tool.captures)
    tool.start()
    listed = states(tool)
    for name in ("20261101-100000", "20261102-100000"):
        assert listed[name]["label"] == IGNORED_LABEL
        assert listed[name]["progressLabel"] is None
        assert 400 <= tool.act("click", name, x=1, y=1)[0] < 500
        assert 400 <= tool.act("hidden", name)[0] < 500
        status, view = tool.json("GET", f"/api/sessions/{name}")
        assert view["readOnly"] is True and "cursor" not in view
    assert listed[NAME]["label"] is None and IGNORED_LABEL == "Ignorée : aucune fléchette jugée"
    assert [p.name for p in tool.annotations.iterdir()] == []  # no file created


def test_save_error_keeps_the_screen_state_and_retries_on_the_next_action(tool, monkeypatch):
    make_session(tool.captures)
    tool.start()
    real = an.write_atomic

    def failing(path, annotations):
        raise an.AnnotationError("$: cannot write: disque plein")

    monkeypatch.setattr(server_module.annotation_file, "write_atomic", failing)
    status, result = tool.act("hidden")
    assert "Enregistrement impossible" in result["error"] and "disque plein" in result["error"]
    assert result["session"]["darts"][0]["status"] == "HIDDEN"  # nothing lost on screen
    assert result["session"]["cursor"]["number"] == 3
    monkeypatch.setattr(server_module.annotation_file, "write_atomic", real)
    status, result = tool.act("off_board")
    assert result["saved"] and "error" not in result
    saved = an.read(annotation_file(tool))
    assert [(d.number, d.status.value) for d in saved.darts] == [(1, "HIDDEN"), (3, "OFF_BOARD")]


def test_handler_failure_answers_a_json_500_and_the_thread_survives(tool, monkeypatch):
    make_session(tool.captures)
    tool.start()

    def boom(self):
        raise RuntimeError("boom")

    monkeypatch.setattr(server_module.SessionStore, "session_list", boom)
    status, body = tool.json("GET", "/api/sessions")
    assert status == 500 and "error" in body
    monkeypatch.undo()
    assert tool.json("GET", "/api/sessions")[0] == 200


def test_session_list_rescans_so_a_new_session_appears_without_restart(tool):
    make_session(tool.captures)
    tool.start()
    assert list(states(tool)) == [NAME]
    make_session(tool.captures, name="20261101-100000")
    assert list(states(tool)) == [NAME, "20261101-100000"]


def test_annotations_folder_inside_the_captures_folder_is_refused(tmp_path):
    (tmp_path / "captures").mkdir()
    with pytest.raises(ValueError):
        make_server(tmp_path / "captures", tmp_path / "captures" / "annotations", 0)
    with pytest.raises(ValueError):
        make_server(tmp_path / "captures", tmp_path / "captures", 0)


# ---- command line -------------------------------------------------------------------------------------------------

def test_port_in_use_prints_a_message_and_exits_2(tmp_path, capsys):
    (tmp_path / "captures").mkdir()
    with socket.socket() as taken:
        taken.bind(("127.0.0.1", 0))
        taken.listen()
        code = main(["--captures", str(tmp_path / "captures"), "--annotations", str(tmp_path / "ann"),
                     "--port", str(taken.getsockname()[1])])
    assert code == 2
    assert "déjà utilisé" in capsys.readouterr().err


def test_missing_captures_folder_exits_2(tmp_path, capsys):
    assert main(["--captures", str(tmp_path / "none"), "--annotations", str(tmp_path / "ann")]) == 2
    assert "introuvable" in capsys.readouterr().err


@pytest.mark.parametrize("python", stdlib_pythons())
def test_help_works_with_the_stdlib_only(tmp_path, python):
    root = Path(__file__).resolve().parent.parent
    # -I ignores PYTHONPATH and the current folder, so the package root is put on sys.path by the command itself.
    code = ("import sys, runpy; sys.path.insert(0, %r); sys.argv = ['kosmo_camera.annotate', '--help']; "
            "runpy.run_module('kosmo_camera.annotate', run_name='__main__')" % str(root))
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True,
                            env={"PATH": os.environ.get("PATH", "")})
    assert result.returncode == 0, result.stderr
    assert "--captures" in result.stdout and "--annotations" in result.stdout and "--port" in result.stdout
    assert "--frames" in result.stdout
    assert not [flag for flag in ("--drift", "--split", "--lock") if flag in result.stdout]  # ADR 0020


@pytest.mark.parametrize("flag", ["--drift", "--split", "--lock"])
def test_the_removed_options_exit_2(tmp_path, flag):
    captures = tmp_path / "captures"
    captures.mkdir()
    with pytest.raises(SystemExit) as exit_info:
        main(["--captures", str(captures), "--annotations", str(tmp_path / "ann"), flag, str(tmp_path / "x.json")])
    assert exit_info.value.code == 2

def frame_sources(tool, frames):
    """A frames folder (session -> {dart: position_ms or missing reason}) and its `frames.json` (CV27)."""
    frames_dir = tool.root / "frames"
    frames_dir.mkdir(exist_ok=True)
    sessions = []
    for name, darts in frames.items():
        (frames_dir / name).mkdir(parents=True, exist_ok=True)
        listed, missing = [], []
        for dart, value in sorted(darts.items()):
            if isinstance(value, str):
                missing.append(ff.Missing(dart, value))
                continue
            file = ff.video_file_name(dart)
            content = jpeg_bytes(*VIDEO_SIZE, exif_padding=50)
            (frames_dir / name / file).write_bytes(content)
            listed.append(ff.Frame(dart, value, *VIDEO_SIZE, file, hashlib.sha256(content).hexdigest()))
        sessions.append(ff.SessionFrames(name, tuple(listed), tuple(missing)))
    ff.write_atomic(frames_dir / "frames.json", ff.FramesFile(1000, tuple(sessions)))
    return FrameSources(frames_dir)


def test_a_corrupt_frame_manifest_warns_and_leaves_the_photo_controls_s13(tool):
    sources = frame_sources(tool, {})
    make_session(tool.captures)
    (sources.frames / "frames.json").write_text("{", encoding="utf-8")
    tool.start(frames=sources)
    status, body = tool.json("GET", "/api/sessions")
    warning = body["framesWarning"]
    assert status == 200 and [s["name"] for s in body["sessions"]] == [NAME]
    assert warning == ("Images vidéo illisibles : frames.json est invalide ou illisible "
                       "(relancer l'extraction, K7.2)")
    assert str(tool.root) not in warning and "frames.json:" not in warning  # never the raw error text (S-minor1)
    assert all(d["frame"] is None for d in tool.json("GET", f"/api/sessions/{NAME}")[1]["darts"])


def _frames_of_view(tool):
    return {d["number"]: d["frame"] for d in tool.json("GET", f"/api/sessions/{NAME}")[1]["darts"]}


def test_a_dart_without_a_usable_frame_has_frame_null_and_the_right_warning_cv27_t1(tool):
    """The V key of the page depends on `frame`: null gives the « Pas d'image vidéo » message, no warning when the
    frame is simply not listed, the fixed warning when the manifest is missing."""
    make_session(tool.captures)
    sources = frame_sources(tool, {NAME: {1: 850, 3: "video_missing"}})
    tool.start(frames=sources)
    listed = _frames_of_view(tool)  # frame not listed: no warning, the other darts keep theirs
    assert listed[3] is None and listed[4] is None and listed[1]["file"] == "video-001.jpg"
    assert tool.json("GET", "/api/sessions")[1]["framesWarning"] is None
    (sources.frames / "frames.json").unlink()  # manifest missing: every dart without frame, one fixed warning
    assert set(_frames_of_view(tool).values()) == {None}
    assert tool.json("GET", "/api/sessions")[1]["framesWarning"] == FRAMES_UNREADABLE


def test_a_listed_frame_whose_file_is_missing_is_listed_and_its_route_answers_404_cv27_t1(tool):
    """The view lists what the manifest lists; the page meets the missing file as an unreadable image (U12)."""
    make_session(tool.captures)
    sources = frame_sources(tool, {NAME: {1: 850}})
    (sources.frames / NAME / "video-001.jpg").unlink()
    tool.start(frames=sources)
    assert _frames_of_view(tool)[1]["file"] == "video-001.jpg"
    assert tool.json("GET", "/api/sessions")[1]["framesWarning"] is None
    assert tool.request("GET", f"/api/frame/{NAME}/video-001.jpg")[0] == 404


def test_the_source_of_an_action_is_checked_and_unknown_keys_are_refused(tool):
    make_session(tool.captures)
    tool.start()
    before = tree_listing(tool.root)
    for body in ({"action": "hidden", "source": "photo"}, {"action": "click", "x": 5, "y": 5, "source": "video"},
                 {"action": "hidden", "dart": 1}):  # `source` is no longer a key of the route (N24)
        assert tool.json("POST", f"/api/sessions/{NAME}/actions", body) == (400, {"error": "Action invalide."}), body
    assert tree_listing(tool.root) == before


def test_frame_route_serves_only_listed_files_of_listed_sessions_s13(tool):
    make_session(tool.captures)
    tool.start(frames=frame_sources(tool, {NAME: {1: 850}}))
    (tool.root / "frames" / NAME / "video-004.jpg").write_bytes(jpeg_bytes(*VIDEO_SIZE))  # on disk, not in the manifest
    (tool.root / "frames" / OTHER).mkdir()
    (tool.root / "frames" / OTHER / "video-001.jpg").write_bytes(jpeg_bytes(*VIDEO_SIZE))
    status, headers, payload = tool.request("GET", f"/api/frame/{NAME}/video-001.jpg")
    assert status == 200 and headers["Content-Type"] == "image/jpeg" and payload.startswith(b"\xff\xd8")
    for path in (f"/api/frame/{NAME}/video-004.jpg", f"/api/frame/{OTHER}/video-001.jpg",
                 f"/api/frame/{NAME}/..%2Fvideo-001.jpg", f"/api/frame/{NAME}/../{NAME}/video-001.jpg",
                 f"/api/frame/{NAME}/video-001.jpg%00", f"/api/frame/{NAME}/dart-001.jpg",
                 f"/api/frame/{NAME}%2F..%2F{NAME}/video-001.jpg", "/api/frame/nosuch/video-001.jpg"):
        assert tool.request("GET", path)[0] in (400, 404), path


def test_frame_route_without_the_frames_option_is_a_404_s13(tool):
    make_session(tool.captures)
    tool.start()
    assert tool.request("GET", f"/api/frame/{NAME}/video-001.jpg")[0] == 404


# ---- tip entry only (ADR 0020, Q1 B, N24) -----------------------------------------------------------------------------

def _photo_control(dart):
    return an.Control(dart, an.PHOTO, None, *SIZE, ((10.0, 10.0), None, (30.0, 30.0), (40.0, 40.0)),
                      (an.SNAPPED, None, an.UNSNAPPED, an.SNAPPED))


def _video_control(dart):
    return an.Control(dart, an.VIDEO, 850, *VIDEO_SIZE, ((1.0, 2.0), (3.0, 4.0), (5.0, 6.0), (7.0, 8.0)),
                      (an.UNSNAPPED,) * 4)


def _v3_file(tool, sha):
    """A version 3 file with one segment and controls, as the stopped tool could leave it."""
    darts = (an.DartAnnotation(1, an.Status.VISIBLE, (100.5, 200.5)),)
    annotations = an.Annotations(NAME, sha, (SEGMENT,), darts, (_photo_control(1), _video_control(1)))
    tool.write_annotations(annotations)
    return annotations


@pytest.mark.parametrize("body", [{"action": kind} for kind in REMOVED_KINDS]
                         + [{"action": "save", "number": 1}, {"action": "control", "number": 1},
                            {"action": "click", "x": 5, "y": 5, "source": "photo"},
                            {"action": "hidden", "source": "video"}, {"action": "hidden", "dart": 1}])
def test_the_removed_actions_and_the_source_key_answer_400_and_leave_the_folder_byte_identical_n24(tool, body):
    _, sha = make_session(tool.captures)
    _v3_file(tool, sha)
    tool.start()
    before = tree_listing(tool.root)
    status, result = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
    assert status == 400 and result["error"] in ("Action inconnue.", "Action invalide."), body
    assert tree_listing(tool.root) == before


def test_a_tip_write_on_a_v3_file_keeps_its_segments_and_controls_cv20_cv25(tool):
    _, sha = make_session(tool.captures)
    stored = _v3_file(tool, sha)
    tool.start()
    for body in ({"action": "click", "x": 1.5, "y": 2.5}, {"action": "hidden"}, {"action": "goto", "number": 1},
                 {"action": "clear"}, {"action": "off_board"}):
        status, result = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert status == 200, (body, result)
    saved = an.read(annotation_file(tool))
    assert saved.calibrations == stored.calibrations and saved.controls == stored.controls
    assert [(d.number, d.status) for d in saved.darts] == [(1, an.Status.OFF_BOARD), (3, an.Status.VISIBLE),
                                                             (4, an.Status.HIDDEN)]


def test_a_tips_only_file_reads_strictly_and_feeds_the_view_segment_for_and_control_sample_n24(tool):
    from kosmo_camera import control
    from kosmo_camera.truth import read_truth
    folder, _ = make_session(tool.captures)
    tool.start()
    for _ in range(3):
        assert tool.act("click", x=50, y=60)[0] == 200
    saved = an.read(annotation_file(tool))
    assert saved.calibrations == () and saved.controls == () and len(saved.darts) == 3
    view = tool.json("GET", f"/api/sessions/{NAME}")[1]
    assert view["segments"] == [] and view["cursor"] == {"kind": "complete", "number": None, "photo": None}
    assert [an.segment_for(saved, n) for n in (1, 3, 4)] == [None] * 3
    sample = control.sample(read_truth(folder / "truth.json"), saved, "dev")
    assert sample.photo_darts == (1, 4)  # stride and last dart only: no segment photo is added


def test_the_progress_label_counts_the_entered_tips_of_24_darts(tool):
    make_session(tool.captures, judged=tuple(range(1, 25)), voided=(), no_photo=())
    tool.start()
    assert states(tool)[NAME]["progressLabel"] == "0 / 24 pointes saisies"
    for _ in range(24):
        assert tool.act("click", x=50, y=60)[0] == 200
    assert states(tool)[NAME]["progressLabel"] == "24 / 24 pointes saisies"


def test_the_session_view_carries_the_photo_control_and_the_listed_frame_of_each_dart_cv25_cv27(tool):
    _, sha = make_session(tool.captures)
    _v3_file(tool, sha)
    tool.start(frames=frame_sources(tool, {NAME: {1: 850, 3: "video_missing"}}))
    darts = {d["number"]: d for d in tool.json("GET", f"/api/sessions/{NAME}")[1]["darts"]}
    assert darts[1]["control"] == [[10.0, 10.0], None, [30.0, 30.0], [40.0, 40.0]]
    assert darts[1]["frame"] == {"file": "video-001.jpg", "control": [[1.0, 2.0], [3.0, 4.0], [5.0, 6.0], [7.0, 8.0]]}
    assert (darts[3]["control"], darts[3]["frame"]) == (None, None)  # a missing frame is not listed
    assert (darts[4]["control"], darts[4]["frame"]) == (None, None)


@pytest.mark.parametrize("python", stdlib_pythons())
def test_the_tool_starts_lists_and_saves_a_tip_with_numpy_and_pillow_blocked(tmp_path, python):
    root = Path(__file__).resolve().parent.parent
    captures, annotations = tmp_path / "captures", tmp_path / "annotations"
    make_session(captures)
    script = tmp_path / "run_tool.py"
    script.write_text(
        "import importlib.abc, json, sys, threading, urllib.request\n"
        "class Block(importlib.abc.MetaPathFinder):\n"
        "    def find_spec(self, name, path=None, target=None):\n"
        "        if name.split('.')[0] in ('numpy', 'PIL'):\n"
        "            raise ImportError('blocked: ' + name)\n"
        "sys.meta_path.insert(0, Block())\n"
        f"sys.path.insert(0, {str(root)!r})\n"
        "from pathlib import Path\n"
        "from kosmo_camera.annotate.server import make_server\n"
        f"server = make_server(Path({str(captures)!r}), Path({str(annotations)!r}), 0,\n"
        f"                     Path({str(root / 'kosmo_camera' / 'annotate' / 'static')!r}))\n"
        "threading.Thread(target=server.serve_forever, daemon=True).start()\n"
        "base = 'http://127.0.0.1:%d' % server.server_address[1]\n"
        "names = [s['name'] for s in json.load(urllib.request.urlopen(base + '/api/sessions'))['sessions']]\n"
        "body = json.dumps({'action': 'click', 'x': 5, 'y': 6}).encode()\n"
        f"request = urllib.request.Request(base + '/api/sessions/{NAME}/actions', body,\n"
        "                                 {'Content-Type': 'application/json', 'Origin': base}, method='POST')\n"
        "saved = json.load(urllib.request.urlopen(request))['saved']\n"
        "server.shutdown()\n"
        "assert not [m for m in sys.modules if m.split('.')[0] in ('numpy', 'PIL')]\n"
        "print(names, saved)\n", encoding="utf-8")
    result = subprocess.run([python, "-I", "-S", str(script)], capture_output=True, text=True,
                            env={"PATH": os.environ.get("PATH", "")}, timeout=60)
    assert result.returncode == 0, result.stderr
    assert result.stdout.startswith(f"['{NAME}'] Enregistré à ")
