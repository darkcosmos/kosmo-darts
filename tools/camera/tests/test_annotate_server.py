"""Annotation server (T6a, CV16, D8): routes, write-route attacks, error states, read-only captures, JPEG size."""
import hashlib
import json
import os
import re
import socket
import subprocess
import sys
import threading
from pathlib import Path

import pytest

from annotate_support import NAME, SIZE, jpeg_bytes, make_session, stdlib_pythons, tree_listing
from kosmo_camera import annotations as an
from kosmo_camera import drift_file
from kosmo_camera.annotate import server as server_module
from kosmo_camera.annotate.__main__ import main
from kosmo_camera.annotate.jpeg_size import JpegError, jpeg_size, read_size
from kosmo_camera.annotate.server import IGNORED_LABEL, make_server

POINTS = [(10, 20), (30, 40), (50, 60), (70, 80)]


class Tool:
    def __init__(self, root, static_dir):
        self.captures = root / "captures"
        self.annotations = root / "annotations"
        self.captures.mkdir()
        self.root = root
        self.static_dir = static_dir
        self.server = None

    def start(self, drift=None):
        self.server = make_server(self.captures, self.annotations, 0, self.static_dir, clock=lambda: "12:34:56",
                                  drift=drift)
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

    def calibrate(self, name=NAME):
        for x, y in POINTS:
            status, result = self.act("click", name, x=x, y=y)
            assert status == 200, result
        return result


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
    assert body["sessions"] == [{"name": NAME, "state": "ok", "message": None, "warning": None, "label": None,
                                 "progress": {"done": 0, "total": 3}, "calibrated": False,
                                 "driftLabel": None, "driftStale": False}]
    assert body["driftWarning"] is None
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"] == {"kind": "calibration", "number": None, "photo": "dart-001.jpg", "pending": []}
    assert [d["number"] for d in view["darts"]] == [1, 3, 4] and view["skipped"] == [2, 5]
    assert view["readOnly"] is False and view["photoSize"] == list(SIZE)
    assert all(re.fullmatch(r"[DT]?\d{1,2}", d["value"]) for d in view["darts"])  # true value shown large by the page
    assert all(isinstance(d["volley"], int) for d in view["darts"])


def test_full_flow_autosaves_each_change_and_reports_the_saved_time(tool):
    make_session(tool.captures)
    tool.start()
    tool.act("click", x=10, y=20)
    assert not annotation_file(tool).exists()  # a partial calibration is not a file change
    for x, y in POINTS[1:]:
        status, result = tool.act("click", x=x, y=y)
    assert result["saved"] == "Enregistré à 12:34:56"
    assert result["session"]["cursor"]["number"] == 1
    status, result = tool.act("click", x=100.5, y=200.5)
    assert result["saved"] == "Enregistré à 12:34:56" and result["session"]["cursor"]["number"] == 3
    tool.act("hidden")
    saved = an.read(annotation_file(tool))
    assert saved.calibrations[0].width == SIZE[0] and [d.number for d in saved.darts] == [1, 3]
    # reopening resumes at the first dart not annotated, with the earlier tips returned
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"]["number"] == 4 and view["darts"][0]["tip"] == [100.5, 200.5]
    status, listing = tool.json("GET", "/api/sessions")
    assert listing["sessions"][0]["progress"] == {"done": 2, "total": 3}


def test_clear_goes_through_the_post_route_and_there_is_no_delete_or_put(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
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
    tool.calibrate()
    before = tree_listing(tool.root)
    for body in ({"action": "bogus"}, {"action": "click"}, {"action": "click", "x": -1, "y": 5},
                 {"action": "click", "x": 5000, "y": 5}, {"action": "click", "x": 5, "y": 3073},
                 {"action": "click", "x": True, "y": 5}, {"action": "click", "x": "5", "y": 5},
                 {"action": "goto", "number": 2}, {"action": "goto", "number": "1"}, {"action": "hidden", "extra": 1},
                 [], "x", {}):
        status, _ = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert status == 400, body
    assert tree_listing(tool.root) == before


def test_clicks_are_checked_against_the_real_jpeg_size_not_only_the_truth_size_d8(tool):
    # truth says 4080x3072 but the stored JPEG is 2000x1000: the calibration takes the real size, clicks beyond it fail
    make_session(tool.captures, jpeg_size=(2000, 1000))
    tool.start()
    status, result = tool.act("click", x=2001, y=10)
    assert status == 400
    for x, y in POINTS:
        status, result = tool.act("click", x=x, y=y)
    calibration = an.read(annotation_file(tool)).calibrations[0]
    assert (calibration.width, calibration.height) == (2000, 1000)
    assert tool.act("click", x=10, y=1001)[0] == 400
    assert tool.act("click", x=1999.5, y=999.5)[0] == 200


def test_click_beyond_the_truth_photo_size_is_refused_when_the_jpeg_is_larger(tool):
    make_session(tool.captures, jpeg_size=(6000, 4000))
    tool.start()
    tool.calibrate()
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
    tool.calibrate()
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
    assert listed[NAME]["state"] == "truth_invalid" and "invalide" in listed[NAME]["message"] and "JSON" in listed[NAME]["message"]
    assert listed["20261101-100000"]["state"] == "ok"  # one bad session does not hide the others
    assert tool.act("hidden")[0] == 409
    assert not annotation_file(tool).exists()


def test_truth_of_another_session_name_is_invalid(tool):
    folder, _ = make_session(tool.captures)
    folder.rename(tool.captures / "20261101-100000")
    tool.start()
    assert states(tool)["20261101-100000"]["state"] == "truth_invalid"


def test_rejected_annotation_file_is_read_only_with_cause_and_never_overwritten(tool):
    make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text('{"format": "other"}', encoding="utf-8")
    tool.start()
    before = tree_listing(tool.root)
    listed = states(tool)[NAME]
    assert listed["state"] == "annotation_rejected" and "format" in listed["message"] and listed["progress"] is None
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
    assert states(tool)[NAME]["state"] == "annotation_rejected"


def test_annotation_file_over_1_mib_is_refused_and_untouched(tool):
    make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_bytes(b" " * (an.MAX_BYTES + 1))
    tool.start()
    before = tree_listing(tool.root)
    listed = states(tool)[NAME]
    assert listed["state"] == "annotation_too_large" and "1 Mio" in listed["message"]
    assert tool.act("hidden")[0] == 409
    assert tree_listing(tool.root) == before


def test_stale_annotation_warns_and_still_allows_editing(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    stale = an.Annotations(NAME, "0" * 64, (), ())
    annotation_file(tool).write_text(an.encode(stale), encoding="utf-8")
    tool.start()
    listed = states(tool)[NAME]
    assert listed["state"] == "ok" and "changé" in listed["warning"]
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
    assert listed["state"] == "ok" and listed["calibrated"] is True and listed["progress"] == {"done": 2, "total": 3}
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"]["number"] == 4 and view["calibration"]["points"] == [list(map(float, p)) for p in POINTS]
    for action in ("previous", "next"):
        assert tool.act(action)[0] == 200
    assert tool.act("goto", number=1)[0] == 200
    assert tree_listing(tool.root) == before


def test_first_saved_change_rewrites_a_v1_file_as_v2_with_every_tip_kept_cv16_cv20(tool):
    _, sha = make_session(tool.captures)
    tool.annotations.mkdir()
    annotation_file(tool).write_text(_v1_text(sha), encoding="utf-8")
    tool.start()
    status, result = tool.act("off_board")  # resume cursor: dart 4
    assert status == 200 and result["saved"] == "Enregistré à 12:34:56"
    saved = json.loads(annotation_file(tool).read_text(encoding="utf-8"))
    assert saved["version"] == 2 and "calibration" not in saved
    assert saved["calibrations"] == [{"fromDart": 1, "photo": "dart-001.jpg", "width": SIZE[0], "height": SIZE[1],
                                      "points": [{"x": float(x), "y": float(y)} for x, y in POINTS]}]
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
    assert listed["state"] == "ok" and "changé" in listed["warning"]


def test_session_without_judged_dart_is_listed_as_ignored_and_not_openable_cv3(tool):
    make_session(tool.captures, name="20261101-100000", judged=(), voided=(1, 2), no_photo=(3,))
    make_session(tool.captures, name="20261102-100000", judged=(), voided=(), no_photo=(1, 2))
    make_session(tool.captures)
    tool.start()
    listed = states(tool)
    for name in ("20261101-100000", "20261102-100000"):
        assert listed[name]["state"] == "ignored" and listed[name]["label"] == IGNORED_LABEL
        assert listed[name]["progress"] is None
        assert 400 <= tool.act("click", name, x=1, y=1)[0] < 500
        assert 400 <= tool.act("hidden", name)[0] < 500
        status, view = tool.json("GET", f"/api/sessions/{name}")
        assert view["readOnly"] is True and "cursor" not in view
    assert listed[NAME]["state"] == "ok" and IGNORED_LABEL == "Ignorée : aucune fléchette jugée"
    assert [p.name for p in tool.annotations.iterdir()] == []  # no file created


def test_save_error_keeps_the_screen_state_and_retries_on_the_next_action(tool, monkeypatch):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
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

    monkeypatch.setattr(server_module.SessionStore, "sessions_with_warning", boom)
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
    assert "--drift" in result.stdout


# ---- recalibration and drift flags (T6c, CV16, CV20) -----------------------------------------------------------------

NEW_POINTS = [(11, 12), (13, 14), (15, 16), (17, 18)]


def _go_to(tool, number):
    status, result = tool.act("goto", number=number)
    assert status == 200, result
    return result


def _recalibrate_at(tool, number, points=NEW_POINTS):
    _go_to(tool, number)
    tool.act("recalibrate")
    for x, y in points:
        status, result = tool.act("click", x=x, y=y)
    return status, result


def test_recalibration_from_a_dart_is_saved_with_its_photo_and_real_jpeg_size_cv20(tool):
    make_session(tool.captures, jpeg_size=(2000, 1000))
    tool.start()
    tool.act("click", x=10, y=20)  # partial calibration, then the real flow
    for x, y in POINTS[1:]:
        tool.act("click", x=x, y=y)
    tool.act("click", x=100.5, y=200.5)  # tip of dart 1
    status, result = _recalibrate_at(tool, 3)
    assert status == 200 and result["saved"] == "Enregistré à 12:34:56"
    saved = an.read(annotation_file(tool))
    assert [(c.from_dart, c.photo, c.width, c.height) for c in saved.calibrations] == [
        (1, "dart-001.jpg", 2000, 1000), (3, "dart-003.jpg", 2000, 1000)]
    assert [d.tip for d in saved.darts] == [(100.5, 200.5)]
    assert result["session"]["cursor"]["kind"] == "dart" and result["session"]["cursor"]["number"] == 3
    assert [s["fromDart"] for s in result["session"]["segments"]] == [1, 3]
    status, result = tool.act("recalibrate")
    assert status == 200 and result["session"]["cursor"]["kind"] == "recalibration"
    assert result["session"]["cursor"]["pending"] == [list(p) for p in NEW_POINTS]


def test_clear_on_a_saved_recalibration_removes_it_at_once_cv20(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    _recalibrate_at(tool, 3)
    tool.act("recalibrate")
    status, result = tool.act("clear")
    assert status == 200 and result["saved"] == "Enregistré à 12:34:56"
    assert [c.from_dart for c in an.read(annotation_file(tool)).calibrations] == [1]
    assert len(result["session"]["cursor"]["pending"]) == 3


def test_clear_of_the_calibration_screen_is_refused_while_later_segments_exist_cv20(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    _recalibrate_at(tool, 3)
    before = annotation_file(tool).read_bytes()
    _go_to(tool, 1)
    status, result = tool.act("recalibrate")
    assert result["session"]["cursor"]["kind"] == "calibration"
    status, result = tool.act("clear")
    assert status == 400 and result["error"] == "Supprimez d'abord les recalibrations suivantes."
    assert annotation_file(tool).read_bytes() == before


def test_the_fourth_click_at_the_segment_cap_answers_409_and_the_file_is_unchanged_cv20(tool):
    make_session(tool.captures, judged=tuple(range(1, 70)), voided=(), no_photo=())
    tool.start()
    tool.calibrate()
    saved = an.read(annotation_file(tool))
    filler = tuple(an.Calibration(n, f"dart-{n:03d}.jpg", *SIZE, tuple((float(n), 1.0) for _ in range(4)))
                   for n in range(4, 4 + an.MAX_SEGMENTS - 1))
    an.write_atomic(annotation_file(tool), an.Annotations(saved.session, saved.truth_sha256,
                                                           saved.calibrations + filler, ()))
    before = annotation_file(tool).read_bytes()
    _go_to(tool, 3)
    tool.act("recalibrate")
    for x, y in NEW_POINTS[:3]:
        assert tool.act("click", x=x, y=y)[0] == 200
    status, result = tool.act("click", x=17, y=18)
    assert status == 409 and result["error"] == "Nombre maximal de recalibrations atteint (64)"
    assert annotation_file(tool).read_bytes() == before


def _sha(tool):
    return an.segments_sha256(an.read(annotation_file(tool)))


def _flags(session=NAME, label="S01", sha="0" * 64, max_mm=0.4, beyond=(), suggested=(), unmeasurable=()):
    return drift_file.SessionFlags(session, label, sha, max_mm, tuple(beyond), tuple(suggested), tuple(unmeasurable))


def _write_drift(path, *flags):
    path.write_text(drift_file.encode(drift_file.DriftFile(1.0, tuple(flags))), encoding="utf-8")


def _list(tool):
    status, body = tool.json("GET", "/api/sessions")
    assert status == 200
    return body


@pytest.mark.parametrize("flags, label", [
    (dict(), "Dérive OK (max 0,4 mm)"),
    (dict(max_mm=None), "Dérive OK"),
    (dict(beyond=[3, 4], suggested=[3]), "À recalibrer à partir de la fléchette 3"),
    (dict(beyond=[1, 3], suggested=[1, 3]), "À recalibrer à partir des fléchettes 1, 3"),
    (dict(unmeasurable=[4]), "Dérive OK (max 0,4 mm) ; 1 photo non mesurable"),
    (dict(beyond=[3], suggested=[3], unmeasurable=[1, 4]),
     "À recalibrer à partir de la fléchette 3 ; 2 photos non mesurables"),
])
def test_session_list_labels_come_from_the_drift_file_cv16(tool, flags, label):
    make_session(tool.captures)
    drift = tool.root / "drift.json"
    tool.start(drift)
    tool.calibrate()
    _write_drift(drift, _flags(sha=_sha(tool), **flags))
    body = _list(tool)
    assert body["sessions"][0]["driftLabel"] == label and body["sessions"][0]["driftStale"] is False
    assert body["driftWarning"] is None


def test_session_absent_from_the_drift_file_is_not_measured_and_unlisted_ones_are_ignored(tool):
    make_session(tool.captures)
    drift = tool.root / "drift.json"
    _write_drift(drift, _flags(session="20250101-000000"))
    tool.start(drift)
    body = _list(tool)
    assert [s["driftLabel"] for s in body["sessions"]] == ["Dérive non mesurée"] and body["driftWarning"] is None


def test_stale_flags_keep_the_suggested_darts_with_the_stale_mention_and_mark_each_dart_u7(tool):
    make_session(tool.captures)
    drift = tool.root / "drift.json"
    tool.start(drift)
    tool.calibrate()
    _write_drift(drift, _flags(sha=_sha(tool), beyond=[3, 4], suggested=[3], unmeasurable=[1]))
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["driftStale"] is False
    assert [d["drift"] for d in view["darts"]] == ["unmeasurable", "suggested", "beyond"]
    _recalibrate_at(tool, 4)  # the segments change after the measure
    body = _list(tool)
    assert body["sessions"][0]["driftLabel"] == ("À recalibrer à partir de la fléchette 3 ; 1 photo non mesurable "
                                                 "(mesure périmée : relancer la mesure)")
    assert body["sessions"][0]["driftStale"] is True
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["driftStale"] is True and [d["drift"] for d in view["darts"]] == ["unmeasurable", "suggested", "beyond"]


def test_darts_without_flags_have_a_null_drift(tool):
    make_session(tool.captures)
    tool.start()
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert [d["drift"] for d in view["darts"]] == [None, None, None] and view["driftStale"] is False


@pytest.mark.parametrize("content", [b"{not json", b"", b"x" * (drift_file.MAX_BYTES + 1), None])
def test_unreadable_oversized_or_missing_drift_file_warns_once_and_the_tool_serves_cv16(tool, content):
    make_session(tool.captures)
    drift = tool.root / "drift.json"
    if content is not None:
        drift.write_bytes(content)
    tool.start(drift)
    body = _list(tool)
    assert body["driftWarning"].startswith("Mesure de dérive illisible : ")
    assert [s["driftLabel"] for s in body["sessions"]] == [None]
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert status == 200 and [d["drift"] for d in view["darts"]] == [None, None, None]
    assert tool.calibrate()["saved"]


def test_the_flag_file_is_read_again_at_every_list_and_never_written_cv16(tool):
    make_session(tool.captures)
    drift = tool.root / "drift.json"
    tool.start(drift)
    tool.calibrate()
    _write_drift(drift, _flags(sha=_sha(tool), beyond=[3], suggested=[3]))
    assert _list(tool)["sessions"][0]["driftLabel"].startswith("À recalibrer")
    _write_drift(drift, _flags(sha=_sha(tool), max_mm=0.2))
    digest = hashlib.sha256(drift.read_bytes()).hexdigest()
    assert _list(tool)["sessions"][0]["driftLabel"] == "Dérive OK (max 0,2 mm)"
    _recalibrate_at(tool, 3)
    tool.act("recalibrate")
    tool.act("clear")
    tool.json("GET", f"/api/sessions/{NAME}")
    assert hashlib.sha256(drift.read_bytes()).hexdigest() == digest
    assert sorted(p.name for p in tool.root.iterdir()) == ["annotations", "captures", "drift.json", "static"]


def test_drift_option_is_passed_to_the_server(tmp_path, monkeypatch):
    seen = {}

    def fake(captures, annotations, port, drift=None):
        seen["drift"] = drift
        raise ValueError("stop")

    monkeypatch.setattr("kosmo_camera.annotate.__main__.make_server", fake)
    (tmp_path / "c").mkdir()
    args = ["--captures", str(tmp_path / "c"), "--annotations", str(tmp_path / "a")]
    assert main(args + ["--drift", str(tmp_path / "d.json")]) == 2 and seen["drift"] == tmp_path / "d.json"
    assert main(args) == 2 and seen["drift"] is None
