"""Dev/eval split of the existing captures (CV3), its lock (K0) and the eval guard (CV4, CV17).

Stdlib only. The split uses the judged darts of the truth file (CV5) only, so it runs before annotation.
"""
import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from kosmo_camera import config
from kosmo_camera.truth import TruthError, read_truth

SPLIT_FORMAT = "kosmo-camera-split"
LOCK_FORMAT = "kosmo-camera-split-lock"
VERSION = 1
KEY_PREFIX = "kosmo-0b1-split-v1:"
EVAL_FOLDER = "captures-eval"
MODES = ("session", "block")
SETS = ("dev", "eval")
MAX_BYTES = 10 * 1024 * 1024
_HEX64 = re.compile(r"[0-9a-f]{64}")
DEFAULT_OUT = Path("~/kosmo-runs/split.json")


class SplitError(ValueError):
    """Raised for an invalid split, lock or input."""


@dataclass(frozen=True)
class SessionInput:
    """What the split needs of a session: its name and, per volley, the number of judged darts."""
    name: str
    volleys: tuple  # tuple of (volley index, judged darts), in session order
    path: Optional[Path] = None

    @property
    def judged(self) -> int:
        return sum(count for _, count in self.volleys)


@dataclass(frozen=True)
class Unit:
    session: str
    first_volley: Optional[int]  # None in session mode
    last_volley: Optional[int]
    set: str
    darts: int = field(default=0, compare=False)


@dataclass(frozen=True)
class Split:
    mode: str
    units: tuple
    counts: dict
    # Not encoded: what the run knew, for the printed summary.
    session_criteria: tuple = field(default=(), compare=False)
    block_criteria: tuple = field(default=(), compare=False)


def from_truth(session, path=None) -> SessionInput:
    volleys = tuple((v.index, sum(1 for d in v.darts if d.judged)) for v in session.volleys)
    return SessionInput(session.session, volleys, path)


def list_sessions(root) -> list:
    """Sessions of a captures folder: every sub-folder holding a truth.json (CV17: not the eval folder)."""
    root = Path(root)
    if EVAL_FOLDER in root.resolve().parts:
        raise SplitError("the confirmation campaign folder is never part of the split (CV17)")
    found = []
    for folder in sorted(p for p in root.iterdir() if p.is_dir()):
        truth = folder / "truth.json"
        if truth.is_file():
            found.append(from_truth(read_truth(truth), folder))
    return found


def unit_key(session, block=None) -> str:
    name = session if isinstance(session, str) else session.name
    text = KEY_PREFIX + name + ("" if block is None else ":%d" % block)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _guard(sessions):
    names = set()
    for s in sessions:
        if s.path is not None and EVAL_FOLDER in Path(s.path).parts:
            raise SplitError("a session of the confirmation campaign cannot be split (CV17)")
        if s.name in names:
            raise SplitError("duplicate session")
        names.add(s.name)


def _walk(items):
    """items: (key, unit without set, darts). Returns units with their set (CV3 walk, key order)."""
    total = sum(d for _, _, d in items)
    eval_darts = 0
    out = []
    for _, unit, darts in sorted(items, key=lambda i: i[0]):
        into_eval = (total > 0 and eval_darts / total < config.SPLIT_TARGET_EVAL_SHARE
                     and (eval_darts + darts) / total <= config.SPLIT_ACCEPTED_MAX)
        if into_eval:
            eval_darts += darts
        out.append(Unit(unit.session, unit.first_volley, unit.last_volley,
                        "eval" if into_eval else "dev", darts))
    return out


def _counts(units, ignored):
    counts = {s: {"units": 0, "darts": 0} for s in SETS}
    for u in units:
        counts[u.set]["units"] += 1
        counts[u.set]["darts"] += u.darts
    counts["ignoredSessions"] = ignored
    return counts


def _criteria(units, counts):
    """Returns the (name, met) list of the CV3 acceptance criteria."""
    total = counts["dev"]["darts"] + counts["eval"]["darts"]
    share = counts["eval"]["darts"] / total if total else 0.0
    per_set = {s: len({u.session for u in units if u.set == s and u.darts > 0}) for s in SETS}
    return (
        ("eval share between %.0f%% and %.0f%%" % (config.SPLIT_ACCEPTED_MIN * 100,
                                                   config.SPLIT_ACCEPTED_MAX * 100),
         config.SPLIT_ACCEPTED_MIN <= share <= config.SPLIT_ACCEPTED_MAX),
        ("at least %d sessions per set" % config.SPLIT_MIN_SESSIONS_PER_SET,
         min(per_set.values()) >= config.SPLIT_MIN_SESSIONS_PER_SET),
        ("eval has at least %d judged darts" % config.SPLIT_MIN_EVAL_DARTS,
         counts["eval"]["darts"] >= config.SPLIT_MIN_EVAL_DARTS),
    )


def _session_units(judged):
    items = [(unit_key(s), Unit(s.name, None, None, "dev"), s.judged) for s in judged]
    units = _walk(items)
    return units


def _block_units(judged):
    size = config.SPLIT_BLOCK_VOLLEYS
    items = []
    for s in judged:
        for block, start in enumerate(range(0, len(s.volleys), size)):
            chunk = s.volleys[start:start + size]
            unit = Unit(s.name, chunk[0][0], chunk[-1][0], "dev")
            items.append((unit_key(s, block), unit, sum(c for _, c in chunk)))
    return _walk(items)


def compute_split(sessions) -> Split:
    """CV3: session mode, then block mode when the criteria fail. Sessions without judged dart are ignored."""
    sessions = list(sessions)
    _guard(sessions)
    judged = [s for s in sessions if s.judged > 0]
    ignored = len(sessions) - len(judged)
    units = _session_units(judged)
    counts = _counts(units, ignored)
    criteria = _criteria(units, counts)
    if all(ok for _, ok in criteria):
        return Split("session", _ordered(units), counts, criteria)
    block_units = _block_units(judged)
    block_counts = _counts(block_units, ignored)
    return Split("block", _ordered(block_units), block_counts, criteria,
                 _criteria(block_units, block_counts))


def _ordered(units):
    return tuple(sorted(units, key=lambda u: (u.session, -1 if u.first_volley is None else u.first_volley)))


def set_of(session, volley_index, split) -> str:
    name = session if isinstance(session, str) else session.name
    for u in split.units:
        if u.session != name:
            continue
        if u.first_volley is None or u.first_volley <= volley_index <= u.last_volley:
            return u.set
    raise SplitError("session or volley is not in the split (ignored session or unknown volley)")


def encode(split) -> bytes:
    units = []
    for u in split.units:
        item = {"session": u.session, "set": u.set}
        if split.mode == "block":
            item["firstVolley"] = u.first_volley
            item["lastVolley"] = u.last_volley
        units.append(item)
    root = {"format": SPLIT_FORMAT, "version": VERSION, "mode": split.mode,
            "units": units, "counts": split.counts}
    return (json.dumps(root, sort_keys=True, indent=2) + "\n").encode("utf-8")


def _load(data, what):
    if len(data) > MAX_BYTES:
        raise SplitError("%s is too large" % what)
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=_no_duplicates)
    except (UnicodeDecodeError, ValueError) as error:
        raise SplitError("%s is not valid JSON" % what) from error


def _no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate key")
    return dict(pairs)


def _object(value, keys, what):
    if not isinstance(value, dict) or set(value) != set(keys):
        raise SplitError("%s: unexpected or missing key" % what)
    return value


def _int(value, what, minimum=0):
    if type(value) is not int or value < minimum:
        raise SplitError("%s: expected an integer >= %d" % (what, minimum))
    return value


def _counts_shape(value, what):
    _object(value, SETS, what)
    for s in SETS:
        _object(value[s], ("units", "darts"), what + "." + s)
        _int(value[s]["units"], what, 0)
        _int(value[s]["darts"], what, 0)
    return value


def decode(data) -> Split:
    root = _object(_load(data, "split"), ("format", "version", "mode", "units", "counts"), "split")
    if root["format"] != SPLIT_FORMAT or root["version"] != VERSION or type(root["version"]) is not int:
        raise SplitError("split: wrong format or version")
    mode = root["mode"]
    if mode not in MODES:
        raise SplitError("split: unknown mode")
    counts = _object(root["counts"], SETS + ("ignoredSessions",), "counts")
    _counts_shape({s: counts[s] for s in SETS}, "counts")
    _int(counts["ignoredSessions"], "counts.ignoredSessions")
    if not isinstance(root["units"], list):
        raise SplitError("split: units must be a list")
    units = []
    keys = ("session", "set") + (("firstVolley", "lastVolley") if mode == "block" else ())
    for raw in root["units"]:
        _object(raw, keys, "unit")
        if not isinstance(raw["session"], str) or not raw["session"] or raw["set"] not in SETS:
            raise SplitError("unit: invalid session or set")
        first = last = None
        if mode == "block":
            first, last = _int(raw["firstVolley"], "unit"), _int(raw["lastVolley"], "unit")
            if last < first:
                raise SplitError("unit: empty volley range")
        units.append(Unit(raw["session"], first, last, raw["set"]))
    for s in SETS:
        if counts[s]["units"] != sum(1 for u in units if u.set == s):
            raise SplitError("counts do not match the units")
    ordered = _ordered(units)
    if ordered != tuple(units) or len({(u.session, u.first_volley) for u in units}) != len(units):
        raise SplitError("units are not canonical (sorted, unique)")
    return Split(mode, tuple(units), counts)


def sha256_hex(data) -> str:
    return hashlib.sha256(data).hexdigest()


def make_lock(split_bytes) -> bytes:
    split = decode(split_bytes)
    root = {"format": LOCK_FORMAT, "version": VERSION, "mode": split.mode,
            "splitSha256": sha256_hex(split_bytes),
            "counts": {s: split.counts[s] for s in SETS}}
    return (json.dumps(root, sort_keys=True, indent=2) + "\n").encode("utf-8")


def read_lock(path) -> dict:
    path = Path(path)
    if path.stat().st_size > MAX_BYTES:
        raise SplitError("lock is too large")
    root = _object(_load(path.read_bytes(), "lock"),
                   ("format", "version", "mode", "splitSha256", "counts"), "lock")
    if root["format"] != LOCK_FORMAT or type(root["version"]) is not int or root["version"] != VERSION:
        raise SplitError("lock: wrong format or version")
    if root["mode"] not in MODES:
        raise SplitError("lock: unknown mode")
    if not isinstance(root["splitSha256"], str) or not _HEX64.fullmatch(root["splitSha256"]):
        raise SplitError("lock: splitSha256 must be 64 lowercase hex characters")
    _counts_shape(root["counts"], "lock.counts")
    return root


def check_lock(split_bytes, lock) -> Split:
    """K0: the split bytes must be the locked ones. Returns the decoded split."""
    if sha256_hex(split_bytes) != lock["splitSha256"]:
        raise SplitError("split does not match the lock (SHA-256)")
    split = decode(split_bytes)
    if split.mode != lock["mode"]:
        raise SplitError("split mode does not match the lock")
    if {s: split.counts[s] for s in SETS} != lock["counts"]:
        raise SplitError("split counts do not match the lock")
    return split


def summary(split, sha) -> str:
    """Printed report: counts and labels only, no session name (CV15)."""
    lines = ["mode: %s" % split.mode]
    if split.mode == "block":
        lines.append("flag: more optimistic (same tripod, lighting and calibration clicks on both sides)")
    total = split.counts["dev"]["darts"] + split.counts["eval"]["darts"]
    for s in SETS:
        c = split.counts[s]
        share = 100.0 * c["darts"] / total if total else 0.0
        lines.append("%s: units %d, judged darts %d, share %.1f%%" % (s, c["units"], c["darts"], share))
    lines.append("ignored sessions (no judged dart): %d" % split.counts["ignoredSessions"])
    for label, criteria in (("session mode", split.session_criteria), ("block mode", split.block_criteria)):
        for name, ok in criteria:
            lines.append("%s criterion %s: %s" % (label, name, "met" if ok else "failed"))
    lines.append("split sha256: %s" % sha)
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="kosmo_camera.split", description="Compute the CV3 dev/eval split.")
    parser.add_argument("--captures", required=True, help="folder of the existing capture sessions")
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="split file to write")
    parser.add_argument("--lock", default=None, help="also write split-lock.json here (computed once)")
    args = parser.parse_args(argv)
    out = Path(args.out).expanduser()
    lock = Path(args.lock).expanduser() if args.lock else None
    if out.exists() or (lock is not None and lock.exists()):
        print("refusing to overwrite an existing split or lock (the split is computed once)", file=sys.stderr)
        return 1
    try:
        split = compute_split(list_sessions(Path(args.captures).expanduser()))
    except (SplitError, TruthError, OSError):
        print("cannot compute the split: captures folder or truth file invalid", file=sys.stderr)
        return 1
    data = encode(split)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)
    if lock is not None:
        lock.parent.mkdir(parents=True, exist_ok=True)
        lock.write_bytes(make_lock(data))
    print(summary(split, sha256_hex(data)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
