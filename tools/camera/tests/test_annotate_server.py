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

from annotate_support import NAME, SIZE, FakeSnapping, jpeg_bytes, make_session, stdlib_pythons, tree_listing
from kosmo_camera import annotations as an
from kosmo_camera import drift_file
from kosmo_camera.annotate import server as server_module
from kosmo_camera.annotate.__main__ import main
from kosmo_camera.annotate.jpeg_size import JpegError, jpeg_size, read_size
from kosmo_camera.annotate.server import IGNORED_LABEL, make_server

POINTS = [(300, 300), (1700, 300), (1700, 900), (300, 900)]  # far apart (U27), inside a 2000 x 1000 photo


class Tool:
    def __init__(self, root, static_dir):
        self.captures = root / "captures"
        self.annotations = root / "annotations"
        self.captures.mkdir()
        self.root = root
        self.static_dir = static_dir
        self.server = None

    def start(self, drift=None, snapper=None, controls=None):
        self.snapper = FakeSnapping() if snapper is None else snapper
        self.server = make_server(self.captures, self.annotations, 0, self.static_dir, clock=lambda: "12:34:56",
                                  drift=drift, snapper=self.snapper, controls=controls)
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
        """The four clicks, then Entrée (the `save` action, which names the open item: the calibration is dart 1)."""
        for x, y in POINTS:
            status, result = self.act("click", name, x=x, y=y)
            assert status == 200, result
        status, result = self.act("save", name, number=1)
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
                                 "driftLabel": None, "driftStale": False,
                                 "control": {"available": False, "reason": "Contrôle : indisponible (option --split "
                                             "absente)", "role": None}}]
    assert body["driftWarning"] is None and body["controlWarning"] is None
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["cursor"] == {"kind": "calibration", "number": None, "photo": "dart-001.jpg", "pending": [],
                              "slots": [{"point": None, "status": None, "reason": None}] * 4, "source": None,
                              "selected": None, "ready": 0, "unreadable": False}
    assert view["snapAvailable"] is True
    assert [d["number"] for d in view["darts"]] == [1, 3, 4] and view["skipped"] == [2, 5]
    assert view["readOnly"] is False and view["photoSize"] == list(SIZE)
    assert all(re.fullmatch(r"[DT]?\d{1,2}", d["value"]) for d in view["darts"])  # true value shown large by the page
    assert all(isinstance(d["volley"], int) for d in view["darts"])


def test_full_flow_autosaves_each_change_and_reports_the_saved_time(tool):
    make_session(tool.captures)
    tool.start()
    tool.act("click", x=POINTS[0][0], y=POINTS[0][1])
    assert not annotation_file(tool).exists()  # a partial calibration is not a file change
    for x, y in POINTS[1:]:
        status, result = tool.act("click", x=x, y=y)
    assert "saved" not in result and not annotation_file(tool).exists()  # four ready points are not written either
    status, result = tool.act("save", number=1)
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


def test_a_huge_integer_coordinate_answers_400_not_500_and_changes_nothing(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    before = tree_listing(tool.root)
    for body in ({"action": "click", "x": 10 ** 400, "y": 5}, {"action": "click", "x": 5, "y": -(10 ** 400)}):
        status, result = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert (status, result) == (400, {"error": "Coordonnées invalides."}), body
    assert tree_listing(tool.root) == before
    assert tool.act("hidden")[0] == 200  # the server still serves


def test_s14_a_click_on_an_unreadable_photo_answers_200_unsnapped_unreadable_and_the_next_request_is_served(tool):
    pytest.importorskip("numpy")
    pytest.importorskip("PIL")
    from kosmo_camera.annotate.snapping import Snapper
    make_session(tool.captures)  # the photos hold a JPEG header only: the decoder cannot read them
    tool.start(snapper=Snapper())
    status, result = tool.act("click", x=300, y=300)
    assert status == 200, result
    slot = result["session"]["cursor"]["slots"][0]
    assert (slot["point"], slot["status"], slot["reason"]) == ([300.0, 300.0], "unsnapped", "unreadable")
    assert tool.json("GET", "/api/sessions")[0] == 200
    assert tool.act("click", x=1700, y=300)[0] == 200


def test_clicks_are_checked_against_the_real_jpeg_size_not_only_the_truth_size_d8(tool):
    # truth says 4080x3072 but the stored JPEG is 2000x1000: the calibration takes the real size, clicks beyond it fail
    make_session(tool.captures, jpeg_size=(2000, 1000))
    tool.start()
    status, result = tool.act("click", x=2001, y=10)
    assert status == 400
    for x, y in POINTS:
        status, result = tool.act("click", x=x, y=y)
    tool.act("save", number=1)
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
    for index, (x, y) in enumerate(points, start=1):
        tool.act("select", number=index)  # the proposals sit far from the new points: the key chooses the slot
        status, result = tool.act("click", x=x, y=y)
        assert status == 200, result
    return tool.act("save", number=number)


def test_recalibration_from_a_dart_is_saved_with_its_photo_and_real_jpeg_size_cv20(tool):
    make_session(tool.captures, jpeg_size=(2000, 1000))
    tool.start()
    tool.calibrate()
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


def test_the_save_at_the_segment_cap_answers_409_and_the_file_is_unchanged_cv20(tool):
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
    status, result = tool.act("save", number=3)  # the proposals of the segment in force are four ready points
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

    def fake(captures, annotations, port, drift=None, snapper=None, controls=None):
        seen["drift"] = drift
        raise ValueError("stop")

    monkeypatch.setattr("kosmo_camera.annotate.__main__.make_server", fake)
    (tmp_path / "c").mkdir()
    args = ["--captures", str(tmp_path / "c"), "--annotations", str(tmp_path / "a")]
    assert main(args + ["--drift", str(tmp_path / "d.json")]) == 2 and seen["drift"] == tmp_path / "d.json"
    assert main(args) == 2 and seen["drift"] is None


# ---- assisted calibration (T15n, CV31, U27, U29, A32) ----------------------------------------------------------------

def test_a_click_is_snapped_on_the_photo_of_the_session_and_the_view_shows_the_slots_cv31(tool):
    make_session(tool.captures)
    tool.start(snapper=FakeSnapping(shift=(0.5, 0.25), failing={(1700.0, 300.0): "contrast"}))
    tool.act("click", x=300, y=300)
    status, result = tool.act("click", x=1700, y=300)
    assert status == 200 and "saved" not in result
    slots = result["session"]["cursor"]["slots"]
    assert slots[0] == {"point": [300.5, 300.25], "status": "snapped", "reason": None}
    assert slots[1] == {"point": [1700.0, 300.0], "status": "unsnapped", "reason": "contrast"}
    assert slots[2]["point"] is None and result["session"]["cursor"]["ready"] == 2
    assert [Path(photo) for photo, _ in tool.snapper.calls] == [tool.captures / NAME / "dart-001.jpg"] * 2


def test_select_then_click_goes_to_that_slot_and_a_far_click_is_refused_u27(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    tool.act("previous")
    status, result = tool.act("click", x=1000, y=600)
    assert status == 409 and result["error"].startswith("Clic loin des 4 points")
    assert tool.act("select", number=4)[0] == 200
    status, result = tool.act("click", x=1000, y=600)
    assert status == 200 and result["session"]["cursor"]["slots"][3]["point"] == [1000.0, 600.0]
    assert result["session"]["cursor"]["selected"] is None
    assert tool.act("select", number=5)[0] == 400


def test_save_below_four_ready_points_answers_409_and_writes_nothing_u29(tool):
    make_session(tool.captures)
    tool.start()
    tool.act("click", x=300, y=300)
    tool.act("click", x=1700, y=300)
    status, result = tool.act("save", number=1)
    assert status == 409 and result["error"] == "Points prêts : 2 / 4"
    assert not annotation_file(tool).exists()


def test_a_repeated_save_answers_409_and_writes_one_segment_u29(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    before = annotation_file(tool).read_bytes()
    status, result = tool.act("save", number=1)
    assert status == 409 and result["error"] == "Photo déjà enregistrée"
    assert tool.act("save")[0] == 409  # a save that names no open item is no save
    assert annotation_file(tool).read_bytes() == before and len(an.read(annotation_file(tool)).calibrations) == 1


def test_hide_is_refused_with_409_on_the_calibration_screen_cv20(tool):
    make_session(tool.captures)
    tool.start()
    status, result = tool.act("hidden")
    assert status == 409 and "4 points" in result["error"]
    assert not annotation_file(tool).exists()


def test_the_saved_segment_keeps_the_snap_statuses_of_the_clicks_cv31(tool):
    make_session(tool.captures)
    tool.start(snapper=FakeSnapping(failing={(300.0, 900.0): "unreadable"}))
    tool.calibrate()
    assert an.read(annotation_file(tool)).calibrations[0].snap == ("snapped",) * 3 + ("unsnapped",)


def test_without_numpy_or_pillow_the_tool_serves_and_refuses_the_calibration_a32(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    tool.act("click", x=100.5, y=200.5)  # tip of dart 1
    before = annotation_file(tool).read_bytes()
    tool.start(snapper=FakeSnapping(available=False))
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert status == 200 and view["snapAvailable"] is False
    expected = "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)"
    tool.act("goto", number=3)
    status, result = tool.act("recalibrate")
    assert status == 409 and result["error"] == expected
    status, result = tool.act("click", x=100.5, y=200.5)  # a tip does not need the snapper
    assert status == 200 and result["saved"]
    after = annotation_file(tool).read_bytes()
    assert after != before and an.read(annotation_file(tool)).calibrations == an.decode(before.decode()).calibrations


def test_without_numpy_or_pillow_a_new_session_refuses_clicks_and_save_and_writes_nothing_a32(tool):
    make_session(tool.captures)
    tool.start(snapper=FakeSnapping(available=False))
    for body in ({"action": "click", "x": 300, "y": 300}, {"action": "save", "number": 1}):
        status, result = tool.json("POST", f"/api/sessions/{NAME}/actions", body)
        assert status == 409 and result["error"].startswith("Recalage indisponible"), body
    assert not annotation_file(tool).exists()


# ---- control mode (T15d: CV16, CV25, CV26, CV27, CV31) ----------------------------------------------------------------

VIDEO_SIZE = (1920, 1080)
OTHER = "20261101-090000"


def control_sources(tool, dev=(NAME,), eval=(), frames=None, lock_of=None):
    """A split with the given session names, its lock and, when `frames` is a dict (session -> {dart: reason or
    position_ms}), a frames folder: an int is a frame at that position, a string is a missing reason."""
    from kosmo_camera import frames_file as ff
    from kosmo_camera import split as sp
    from kosmo_camera.annotate.control_state import ControlSources
    units = tuple(sp.Unit(name, None, None, set_name) for set_name, names in (("dev", dev), ("eval", eval))
                  for name in names)
    counts = {s: {"units": sum(1 for u in units if u.set == s), "darts": 0} for s in sp.SETS}
    counts["ignoredSessions"] = 0
    data = sp.encode(sp.Split("session", units, counts))
    split_path, lock_path = tool.root / "split.json", tool.root / "split-lock.json"
    split_path.write_bytes(data)
    lock_path.write_bytes(sp.make_lock(data if lock_of is None else lock_of))
    frames_dir = None
    if frames is not None:
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
    return ControlSources(split_path, lock_path, frames_dir)


def open_control(tool, name=NAME):
    status, result = tool.act("control", name)
    assert status == 200, result
    return result["session"]


def save_item(tool, view, name=NAME):
    cursor = view["cursor"]
    return tool.act("save", name, number=cursor["number"], source=cursor["source"])


def control_tool(tool, **kwargs):
    make_session(tool.captures)
    tool.start(controls=control_sources(tool, **kwargs))
    tool.calibrate()
    return tool


def test_without_split_the_control_is_off_with_its_text_and_nothing_changes_cv16(tool):
    make_session(tool.captures)
    tool.start()
    tool.calibrate()
    before = tree_listing(tool.root)
    status, result = tool.act("control")
    assert (status, result["error"]) == (409, "Contrôle : indisponible (option --split absente)")
    assert tree_listing(tool.root) == before


def test_control_photos_then_video_are_saved_with_snap_size_and_position_cv25_cv26(tool):
    control_tool(tool, frames={NAME: {1: 1850, 4: 3850}})  # dev sample: darts 1 and 4, photo and video
    view = open_control(tool)
    cursor = view["cursor"]
    assert (cursor["kind"], cursor["source"], cursor["number"], cursor["photo"]) == ("control", "photo", 1, "dart-001.jpg")
    assert [slot["point"] for slot in cursor["slots"]] == [list(map(float, p)) for p in POINTS]  # the segment, snapped
    assert view["control"]["photo"] == {"done": 0, "total": 2} and view["control"]["video"] == {"done": 0, "total": 2}
    assert view["control"]["role"] == "dev 1" and NAME not in json.dumps(view["control"])
    status, result = save_item(tool, view)
    assert status == 200 and result["saved"] == "Enregistré à 12:34:56", result
    cursor = result["session"]["cursor"]
    assert (cursor["source"], cursor["number"]) == ("photo", 4)
    status, result = save_item(tool, result["session"])
    cursor = result["session"]["cursor"]
    assert (cursor["source"], cursor["number"], cursor["photo"]) == ("video", 1, "video-001.jpg")
    assert [slot["status"] for slot in cursor["slots"]] == [None] * 4  # the first video item has no proposal
    for x, y in POINTS:
        status, result = tool.act("click", x=x, y=y)
        assert status == 200, result
    status, result = save_item(tool, result["session"])
    assert status == 200 and result["session"]["control"]["video"] == {"done": 1, "total": 2}
    saved = an.read(annotation_file(tool))
    assert [(c.dart, c.source, c.position_ms, (c.width, c.height)) for c in saved.controls] == [
        (1, "photo", None, SIZE), (1, "video", 1850, VIDEO_SIZE), (4, "photo", None, SIZE)]
    assert all(c.snap == ("snapped",) * 4 for c in saved.controls) and len(saved.calibrations) == 1


def test_a_repeated_save_answers_409_and_writes_one_control_u29(tool):
    control_tool(tool)
    view = open_control(tool)
    assert save_item(tool, view)[0] == 200
    before = annotation_file(tool).read_bytes()
    status, result = save_item(tool, view)  # the same item again: it is no longer the open one
    assert (status, result["error"]) == (409, "Photo déjà enregistrée")
    assert annotation_file(tool).read_bytes() == before and len(an.read(annotation_file(tool)).controls) == 1


def test_a_proposal_that_does_not_snap_must_be_clicked_before_save_and_a_hide_follows_the_rule_cv31(tool):
    make_session(tool.captures)
    tool.start(snapper=FakeSnapping(failing={POINTS[2]: "contrast"}), controls=control_sources(tool))
    tool.calibrate()
    view = open_control(tool)
    assert [slot["point"] is None for slot in view["cursor"]["slots"]] == [False, False, True, False]
    status, result = save_item(tool, view)
    assert (status, result["error"]) == (409, "Points prêts : 3 / 4")
    status, result = tool.act("hidden_point")
    assert status == 200 and result["session"]["cursor"]["slots"][2]["status"] == "hidden"
    hidden_view = result["session"]
    status, result = tool.act("hidden_point")  # nothing left to click, no point chosen
    assert (status, result["error"]) == (409, "Choisir le point à cacher : touche 1 à 4, puis H")
    status, result = save_item(tool, hidden_view)
    assert status == 200, result
    control = an.read(annotation_file(tool)).controls[0]
    assert control.points[2] is None and control.snap[2] is None and control.snap[:2] == ("snapped", "snapped")


def test_leaving_the_control_screen_by_reopening_the_session_changes_nothing_u16(tool):
    control_tool(tool)
    open_control(tool)
    tool.act("select", number=1)
    tool.act("click", x=11, y=12)
    before = annotation_file(tool).read_bytes()
    status, view = tool.json("GET", f"/api/sessions/{NAME}")  # Escape: the page reopens the session
    assert status == 200 and view["cursor"]["kind"] == "dart" and view["cursor"]["slots"] == []
    assert annotation_file(tool).read_bytes() == before and an.read(annotation_file(tool)).controls == ()


def test_resume_after_a_restart_opens_the_first_item_without_a_control_cv26(tool):
    control_tool(tool)
    assert save_item(tool, open_control(tool))[0] == 200
    tool.server.shutdown()
    tool.server.server_close()
    tool.start(controls=control_sources(tool))  # a new process: the cursors are lost
    view = open_control(tool)
    assert (view["cursor"]["source"], view["cursor"]["number"]) == ("photo", 4)


def test_undo_with_no_change_reopens_the_previous_item_and_removes_its_control_u28(tool):
    control_tool(tool)
    assert save_item(tool, open_control(tool))[0] == 200
    assert len(an.read(annotation_file(tool)).controls) == 1
    status, result = tool.act("clear")
    assert status == 200 and result["saved"]
    assert an.read(annotation_file(tool)).controls == ()
    cursor = result["session"]["cursor"]
    assert (cursor["number"], cursor["source"]) == (1, "photo") and cursor["ready"] == 4


def test_counts_follow_the_sample_eval_all_judged_dev_sample_reference_photos_counted_cv26(tool):
    make_session(tool.captures)
    make_session(tool.captures, OTHER)
    tool.start(controls=control_sources(tool, dev=(NAME,), eval=(OTHER,)))
    tool.calibrate()
    counts = {s["name"]: s["control"] for s in tool.json("GET", "/api/sessions")[1]["sessions"]}
    assert counts[NAME]["photo"] == {"done": 0, "total": 2} and counts[NAME]["role"] == "dev 1"  # darts 1 and 4
    assert counts[OTHER]["photo"] == {"done": 0, "total": 3} and counts[OTHER]["role"] == "eval 1"  # every judged
    tool.act("goto", number=3)
    tool.act("recalibrate")
    for x, y in NEW_POINTS:
        tool.act("select", number=NEW_POINTS.index((x, y)) + 1)
        tool.act("click", x=x, y=y)
    assert tool.act("save", number=3)[0] == 200  # the segment from dart 3: its photo joins the dev sample
    tool.act("goto", number=4)
    status, result = tool.act("click", x=50.5, y=60.5)  # a tip changes nothing
    assert result["session"]["control"]["photo"] == {"done": 0, "total": 3}


def test_a_split_that_does_not_match_its_lock_warns_in_the_list_and_switches_control_off_cv16(tool):
    make_session(tool.captures)
    other = control_sources(tool, dev=(OTHER,))
    other_data = other.split.read_bytes()
    tool.start(controls=control_sources(tool, lock_of=other_data))
    tool.calibrate()
    status, body = tool.json("GET", "/api/sessions")
    assert body["controlWarning"].startswith("Split refusé : split does not match the lock")
    assert body["sessions"][0]["control"] == {"available": False, "reason": "Contrôle : indisponible (split refusé)",
                                              "role": None}
    assert tool.act("control")[0] == 409
    assert tool.act("goto", number=3)[0] == 200  # the rest of the tool is unchanged


def test_a_session_absent_from_the_split_has_no_control_and_is_still_annotated(tool):
    make_session(tool.captures)
    tool.start(controls=control_sources(tool, dev=(OTHER,)))
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["control"]["reason"] == "Contrôle : indisponible (session absente du split)"
    assert tool.act("control")[0] == 409


def test_a_session_with_every_frame_missing_has_no_video_item_and_its_reasons_are_counted_s13(tool):
    control_tool(tool, frames={NAME: {1: "video_missing", 4: "past_end"}})
    status, view = tool.json("GET", f"/api/sessions/{NAME}")
    assert view["control"]["video"] == {"done": 0, "total": 0} and view["control"]["framesPresent"] is True
    assert view["control"]["frameMissing"] == {"video_missing": 1, "past_end": 1}
    view = open_control(tool)
    for _ in range(2):
        status, result = save_item(tool, view)
        assert status == 200
        view = result["session"]
    assert view["cursor"]["kind"] == "control_done"  # the photos done, no video item to skip to
    assert tool.act("control")[1]["session"]["cursor"]["kind"] == "control_done"


def test_a_corrupt_frame_manifest_warns_and_leaves_the_photo_controls_s13(tool):
    sources = control_sources(tool, frames={})
    make_session(tool.captures)
    (sources.frames / "frames.json").write_text("{", encoding="utf-8")
    tool.start(controls=sources)
    assert tool.json("GET", "/api/sessions")[1]["controlWarning"].startswith("Images vidéo illisibles")


def test_frame_route_serves_only_listed_files_of_listed_sessions_s13(tool):
    control_tool(tool, frames={NAME: {1: 850}})
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
    control_tool(tool)
    assert tool.request("GET", f"/api/frame/{NAME}/video-001.jpg")[0] == 404


def test_s14_a_click_on_an_unreadable_video_frame_is_unsnapped_unreadable_and_the_tool_keeps_serving(tool):
    pytest.importorskip("numpy")
    pytest.importorskip("PIL")
    from kosmo_camera.annotate.snapping import Snapper
    make_session(tool.captures)
    tool.start(snapper=Snapper(), controls=control_sources(tool, frames={NAME: {1: 850}}))
    tool.calibrate()
    for _ in range(2):  # photos 1 and 4: header-only JPEGs, unreadable too; hide every point to get to the video
        view = open_control(tool)
        for number in range(1, 5):
            tool.act("select", number=number)
            tool.act("hidden_point")
        assert tool.act("save", number=view["cursor"]["number"], source="photo")[0] == 200
    status, result = tool.act("control")
    cursor = result["session"]["cursor"]
    assert (cursor["source"], cursor["photo"]) == ("video", "video-001.jpg")
    status, result = tool.act("click", x=300, y=300)
    assert status == 200, result
    slot = result["session"]["cursor"]["slots"][0]
    assert (slot["status"], slot["reason"]) == ("unsnapped", "unreadable")
    assert result["session"]["cursor"]["unreadable"] is True
    assert tool.json("GET", "/api/sessions")[0] == 200


def test_the_source_of_an_action_is_checked_and_unknown_keys_are_refused(tool):
    control_tool(tool)
    for body in ({"action": "save", "number": 1, "source": "audio"}, {"action": "save", "number": 1, "source": 1},
                 {"action": "save", "number": 1, "dart": 1}):
        assert tool.json("POST", f"/api/sessions/{NAME}/actions", body)[0] == 400, body


def test_without_numpy_or_pillow_control_is_refused_with_409_and_nothing_changes_a32(tool):
    make_session(tool.captures)
    tool.start(snapper=FakeSnapping(available=False), controls=control_sources(tool))
    status, result = tool.act("control")
    assert status == 409 and result["error"].startswith("Recalage indisponible")
    assert not annotation_file(tool).exists()
