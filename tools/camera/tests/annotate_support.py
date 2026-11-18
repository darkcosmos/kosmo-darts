"""Fixtures of the annotation tool tests: truth files built from the golden one, JPEG headers of known size."""
import copy
import hashlib
import json
import os
from pathlib import Path

DATA = Path(__file__).parent / "data"
TRUTH_GOLDEN = json.loads((DATA / "truth-v2-golden.json").read_text(encoding="utf-8"))
NAME = "20261031-140322"
SIZE = (4080, 3072)


def jpeg_bytes(width, height, exif_padding=0):
    """SOI, an APP1 segment of `exif_padding` bytes, SOF0 with the size, EOI: enough for a size reader."""
    app1 = b"\xff\xe1" + (exif_padding + 2).to_bytes(2, "big") + bytes(exif_padding)
    sof = b"\xff\xc0" + (17).to_bytes(2, "big") + b"\x08" + height.to_bytes(2, "big") + width.to_bytes(2, "big") \
        + b"\x03\x01\x22\x00\x02\x11\x01\x03\x11\x01"
    return b"\xff\xd8" + app1 + sof + b"\xff\xd9"


def truth_dict(judged=(1, 3, 4), voided=(2,), no_photo=(5,), size=SIZE):
    """Truth with the given dart numbers: judged ones have a photo, voided ones are voided, no_photo ones lack a photo."""
    data = copy.deepcopy(TRUTH_GOLDEN)
    data["photo"]["width"], data["photo"]["height"] = size
    template = data["volleys"][0]["darts"]
    judged_template, voided_template = template[0], template[1]
    darts = []
    for number in sorted(set(judged) | set(voided) | set(no_photo)):
        if number in judged:
            dart = copy.deepcopy(judged_template)
            dart["photo"]["file"] = f"dart-{number:03d}.jpg"
        else:
            dart = copy.deepcopy(voided_template)
            dart["voided"] = number in voided
            if number not in voided:
                dart["voidedAt"] = None
        dart["number"] = number
        darts.append(dart)
    data["volleys"][0]["darts"] = darts
    return data


def make_session(captures, name=NAME, size=SIZE, jpeg_size=None, **kwargs):
    """Write `<captures>/<name>/truth.json` and the judged photos; returns (folder, sha256 of truth.json)."""
    data = truth_dict(size=size, **kwargs)
    data["session"] = name
    folder = Path(captures) / name
    folder.mkdir(parents=True)
    text = json.dumps(data, indent=2).encode("utf-8")
    (folder / "truth.json").write_bytes(text)
    for volley in data["volleys"]:
        for dart in volley["darts"]:
            if dart["photo"] is not None:
                (folder / dart["photo"]["file"]).write_bytes(jpeg_bytes(*(jpeg_size or size), exif_padding=300))
    return folder, hashlib.sha256(text).hexdigest()


def tree_listing(root):
    """Every path under root with the hash of each file, to prove that nothing changed or appeared."""
    result = {}
    for path in sorted(Path(root).rglob("*")):
        rel = str(path.relative_to(root))
        result[rel] = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "dir"
    return result


def stdlib_pythons():
    """Interpreters that must run the stdlib core: the test one, plus the system python3 when it is another one
    (Kosmo's WSL runs the stdlib tools with the system python3, 3.14)."""
    import shutil
    import sys
    found = [sys.executable]
    system = shutil.which("python3", path="/usr/bin:/bin")
    if system and not any(os.path.realpath(system) == os.path.realpath(p) for p in found):
        found.append(system)
    return found


class FakeSnapping:
    """Stands for `flow.apply`'s `snapping` and for the server's `Snapper`: a click snaps to `start + shift`, unless its
    start is listed in `failing` (then it stays unsnapped with that reason). `calls` records (photo or path, start)."""

    def __init__(self, available=True, shift=(0.0, 0.0), failing=None):
        self.available = available
        self.shift = shift
        self.failing = dict(failing or {})  # start (x, y) -> reason
        self.calls = []

    def snap(self, photo, start, expected_size=None):
        from kosmo_camera.annotate.snapping import Outcome
        start = (float(start[0]), float(start[1]))
        self.calls.append((str(photo), start))
        if start in self.failing:
            return Outcome(start, "unsnapped", self.failing[start])
        return Outcome((start[0] + self.shift[0], start[1] + self.shift[1]), "snapped", None)
