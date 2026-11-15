"""Annotation server (CV16): stdlib `ThreadingHTTPServer` on 127.0.0.1, captures read-only, one write route.

Every route resolves the session name through `SessionStore.resolve_session`, which accepts only a name of the current
session list; file paths are built from that listed name, never from the request. The only write is an atomic write of
`<annotations folder>/<session>.json` through `annotations.write_atomic`. All messages shown to Kosmo are in French.
"""
import json
import math
import re
import threading
import time
from dataclasses import dataclass, replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Optional
from urllib.parse import unquote, urlsplit

from kosmo_camera import annotations as annotation_file
from kosmo_camera.annotate import flow
from kosmo_camera.annotate.jpeg_size import JpegError, jpeg_size
from kosmo_camera.truth import TruthError, read_truth

HOST = "127.0.0.1"
DEFAULT_PORT = 8765
MAX_BODY = 64 * 1024
STATIC_DIR = Path(__file__).parent / "static"
STATIC_FILES = {"index.html": "text/html; charset=utf-8", "app.js": "text/javascript; charset=utf-8",
                "style.css": "text/css; charset=utf-8"}
_PHOTO_FILE = re.compile(r"dart-\d{3,}\.jpg")
_ACTION_KEYS = ("action", "x", "y", "number")
IGNORED_LABEL = "Ignorée : aucune fléchette jugée"
OK, IGNORED, TRUTH_INVALID, TOO_LARGE, REJECTED = "ok", "ignored", "truth_invalid", "annotation_too_large", "annotation_rejected"


class HttpError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


@dataclass(frozen=True)
class Loaded:
    name: str
    folder: Path
    state: str
    message: Optional[str] = None
    warning: Optional[str] = None
    truth: object = None
    annotations: object = None
    plan: object = None


def _clock():
    return time.strftime("%H:%M:%S")


class SessionStore:
    """Sessions of a captures folder and their annotation files, with the in-memory state of the open sessions."""

    def __init__(self, captures, annotations_dir, clock=_clock):
        self.captures = Path(captures)
        self.annotations_dir = Path(annotations_dir)
        self.clock = clock
        self.lock = threading.RLock()
        self.cursors = {}  # session name -> flow.Cursor (partial calibration lives here)
        self.unsaved = {}  # session name -> annotations whose write failed (kept for the retry)

    # ---- session list --------------------------------------------------------------------------------------------

    def listing(self) -> dict:
        """Name to folder of every sub-folder holding a truth.json (rescanned at each call)."""
        found = {}
        for folder in sorted(p for p in self.captures.iterdir() if p.is_dir()):
            if (folder / "truth.json").is_file():
                found[folder.name] = folder
        return found

    def resolve_session(self, name) -> Path:
        """The single gate of every route: the folder of a listed session, else 404."""
        folder = self.listing().get(name)
        if folder is None:
            raise HttpError(404, "Session inconnue.")
        return folder

    # ---- loading ---------------------------------------------------------------------------------------------------

    def load(self, name) -> Loaded:
        folder = self.resolve_session(name)
        try:
            truth = read_truth(folder / "truth.json")
        except TruthError as error:
            return Loaded(name, folder, TRUTH_INVALID, f"Fichier de vérité illisible ou invalide : {error}")
        if truth.session != name:
            return Loaded(name, folder, TRUTH_INVALID,
                          f"Fichier de vérité invalide : la session '{truth.session}' ne correspond pas au dossier.")
        plan = flow.plan_of(truth)
        if plan.first_photo is None:
            return Loaded(name, folder, IGNORED, IGNORED_LABEL, truth=truth, plan=plan)
        return self._load_annotations(name, folder, truth, plan)

    def _annotation_path(self, name) -> Path:
        return self.annotations_dir / f"{name}.json"

    def _load_annotations(self, name, folder, truth, plan) -> Loaded:
        if name in self.unsaved:
            return Loaded(name, folder, OK, truth=truth, plan=plan, annotations=self.unsaved[name])
        path = self._annotation_path(name)
        if not path.exists():
            empty = annotation_file.Annotations(name, truth.sha256, None, ())
            return Loaded(name, folder, OK, truth=truth, plan=plan, annotations=empty)
        try:
            if path.stat().st_size > annotation_file.MAX_BYTES:
                return Loaded(name, folder, TOO_LARGE, "Fichier d'annotations trop gros (plus de 1 Mio) : "
                              "session en lecture seule, le fichier n'est pas modifié.", truth=truth, plan=plan)
            annotations = annotation_file.read(path)
            warnings = annotation_file.validate_against(annotations, truth)
        except (annotation_file.AnnotationError, OSError) as error:
            return Loaded(name, folder, REJECTED, f"Fichier d'annotations refusé : {error}. Session en lecture "
                          "seule, le fichier n'est pas modifié.", truth=truth, plan=plan)
        warning = ("Attention : le fichier de vérité a changé depuis les annotations (session recopiée ?). "
                   "Vérifiez les points." if warnings else None)
        return Loaded(name, folder, OK, warning=warning, truth=truth, plan=plan, annotations=annotations)

    # ---- views -----------------------------------------------------------------------------------------------------

    @staticmethod
    def _progress(loaded):
        if loaded.state != OK:
            return None
        numbers = {n for n, _ in loaded.plan.darts}
        return {"done": len({d.number for d in loaded.annotations.darts} & numbers), "total": len(numbers)}

    def summary(self, loaded) -> dict:
        return {"name": loaded.name, "state": loaded.state, "message": loaded.message, "warning": loaded.warning,
                "label": IGNORED_LABEL if loaded.state == IGNORED else None, "progress": self._progress(loaded),
                "calibrated": loaded.annotations.calibration is not None if loaded.state == OK else None}

    def sessions(self) -> list:
        with self.lock:
            return [self.summary(self.load(name)) for name in self.listing()]

    def view(self, loaded, cursor) -> dict:
        view = self.summary(loaded)
        view["readOnly"] = loaded.state != OK
        if loaded.state != OK:  # read-only states show their message, with no photo flow
            return view
        by_number = {d.number: d for d in loaded.annotations.darts}
        view["photoSize"] = [loaded.truth.photo_width, loaded.truth.photo_height]
        by_dart = {d.number: (_value_label(d), v.index) for v in loaded.truth.volleys for d in v.darts}
        view["darts"] = [{"number": number, "photo": photo, "value": by_dart[number][0], "volley": by_dart[number][1],
                          "status": by_number[number].status.value if number in by_number else None,
                          "tip": list(by_number[number].tip) if number in by_number and by_number[number].tip
                          else None} for number, photo in loaded.plan.darts]
        view["skipped"] = list(loaded.plan.skipped)
        view["calibrationPhoto"] = loaded.plan.first_photo
        calibration = loaded.annotations.calibration
        view["calibration"] = None if calibration is None else {
            "photo": calibration.photo, "width": calibration.width, "height": calibration.height,
            "points": [list(p) for p in calibration.points]}
        view["cursor"] = {"kind": cursor.kind, "number": cursor.number, "photo": cursor.photo,
                          "pending": [list(p) for p in cursor.pending]}
        return view

    def open_session(self, name) -> dict:
        with self.lock:
            loaded = self.load(name)
            cursor = None
            if loaded.state == OK:
                previous = self.cursors.get(name)
                pending = previous.pending if previous is not None and previous.kind == flow.CALIBRATION else ()
                cursor = flow.resume(loaded.annotations, loaded.truth, pending)
                self.cursors[name] = cursor
            return self.view(loaded, cursor)

    # ---- the write route -------------------------------------------------------------------------------------------

    def act(self, name, payload) -> dict:
        """Apply one action of the flow and save at once. Returns the session view and `saved` or `error`."""
        with self.lock:
            loaded = self.load(name)
            if loaded.state != OK:
                raise HttpError(409, loaded.message)
            action = self._action(payload)
            cursor = self.cursors.get(name) or flow.resume(loaded.annotations, loaded.truth)
            if action.kind == "click":
                action = replace(action, size=self._photo_size(loaded, cursor))
            try:
                updated, new_cursor = flow.apply(loaded.annotations, loaded.truth, action, cursor)
                annotation_file.validate_against(updated, loaded.truth)
            except (flow.FlowError, annotation_file.AnnotationError) as error:
                raise HttpError(400, str(error)) from error
            self.cursors[name] = new_cursor
            result = {}
            if updated != loaded.annotations:
                result.update(self._save(name, updated))
            view = self.view(replace(loaded, annotations=updated), new_cursor)
            result["session"] = view
            return result

    @staticmethod
    def _photo_size(loaded, cursor):
        if cursor.kind == flow.COMPLETE or cursor.photo is None:
            return None
        try:
            return jpeg_size(loaded.folder / cursor.photo)
        except JpegError as error:
            raise HttpError(500, str(error)) from error

    def _save(self, name, annotations) -> dict:
        try:
            annotation_file.write_atomic(self._annotation_path(name), annotations)
        except annotation_file.AnnotationError as error:
            self.unsaved[name] = annotations  # nothing lost on screen; written again at the next action
            return {"error": f"Enregistrement impossible : {error}. Il sera retenté à la prochaine action."}
        self.unsaved.pop(name, None)
        return {"saved": f"Enregistré à {self.clock()}"}

    @staticmethod
    def _action(payload) -> flow.Action:
        if not isinstance(payload, dict) or any(key not in _ACTION_KEYS for key in payload):
            raise HttpError(400, "Action invalide.")
        kind = payload.get("action")
        if not isinstance(kind, str) or kind not in flow.KINDS:
            raise HttpError(400, "Action inconnue.")
        number = payload.get("number")
        if number is not None and (isinstance(number, bool) or not isinstance(number, int)):
            raise HttpError(400, "Numéro de fléchette invalide.")
        for key in ("x", "y"):
            value = payload.get(key)
            if value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))
                                      or not math.isfinite(value)):
                raise HttpError(400, "Coordonnées invalides.")
        return flow.Action(kind, payload.get("x"), payload.get("y"), number)

    # ---- photos ----------------------------------------------------------------------------------------------------

    def photo(self, name, file) -> bytes:
        folder = self.resolve_session(name)
        try:
            truth = read_truth(folder / "truth.json")
        except TruthError as error:
            raise HttpError(404, "Photo introuvable.") from error
        named = {d.photo.file for v in truth.volleys for d in v.darts if d.photo is not None}
        if file not in named or not _PHOTO_FILE.fullmatch(file):
            raise HttpError(404, "Photo introuvable.")
        try:
            return (folder / file).read_bytes()
        except OSError as error:
            raise HttpError(404, "Photo introuvable.") from error


def _value_label(dart) -> str:
    """The true value as Kosmo reads it on the board: "T20", "D16", "20", "25", "50" or "0" (miss)."""
    prefix = {"DOUBLE": "D", "TRIPLE": "T"}.get(dart.multiplier, "")
    return f"{prefix}{dart.segment}"


def _parse_json(data):
    def refuse(name):
        raise ValueError(name)

    try:
        return json.loads(data.decode("utf-8"), parse_constant=refuse)
    except (ValueError, RecursionError) as error:
        raise HttpError(400, "Corps JSON invalide.") from error


def _make_handler(store, static_dir):
    class Handler(BaseHTTPRequestHandler):
        server_version = "KosmoAnnotate"
        timeout = 10  # a client that announces a body and never sends it must not hold a thread for ever

        # ---- plumbing ----------------------------------------------------------------------------------------------

        def log_message(self, format, *args):  # noqa: A002 (signature of the base class); the terminal stays quiet
            pass

        def _send(self, status, content_type, body):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Content-Security-Policy", "default-src 'self'")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)

        def _json(self, status, value):
            self._send(status, "application/json; charset=utf-8", json.dumps(value, ensure_ascii=False).encode("utf-8"))

        def send_error(self, code, message=None, explain=None):
            self._json(code, {"error": message or self.responses.get(code, ("Erreur",))[0]})

        def _guarded(self, route):
            try:
                self._check_host()
                route()
            except HttpError as error:
                self._json(error.status, {"error": error.message})
            except (BrokenPipeError, ConnectionResetError, TimeoutError):
                pass
            except Exception:  # noqa: BLE001 - the thread must never die: every failure becomes a JSON 500
                self._json(500, {"error": "Erreur interne du serveur d'annotation."})

        def _check_host(self):
            port = self.server.server_address[1]
            if self.headers.get("Host") not in (f"127.0.0.1:{port}", f"localhost:{port}"):
                raise HttpError(403, "Hôte refusé.")

        def _segments(self):
            path = urlsplit(self.path).path
            if not path.startswith("/"):
                raise HttpError(404, "Introuvable.")
            try:
                return [unquote(part, errors="strict") for part in path[1:].split("/")]
            except UnicodeDecodeError as error:
                raise HttpError(404, "Introuvable.") from error

        # ---- GET ---------------------------------------------------------------------------------------------------

        def do_GET(self):  # noqa: N802
            self._guarded(self._get)

        def _static(self, file):
            content_type = STATIC_FILES.get(file)
            if content_type is None:
                raise HttpError(404, "Introuvable.")
            try:
                body = (static_dir / file).read_bytes()
            except OSError as error:
                raise HttpError(404, "Page absente.") from error
            self._send(200, content_type, body)

        def _get(self):
            parts = self._segments()
            if parts == [""]:
                return self._static("index.html")
            if len(parts) == 2 and parts[0] == "static":
                return self._static(parts[1])
            if parts == ["api", "sessions"]:
                with store.lock:
                    return self._json(200, {"sessions": store.sessions()})
            if len(parts) == 3 and parts[:2] == ["api", "sessions"]:
                return self._json(200, store.open_session(parts[2]))
            if len(parts) == 3 and parts[0] == "photo":
                return self._send(200, "image/jpeg", store.photo(parts[1], parts[2]))
            raise HttpError(404, "Introuvable.")

        # ---- POST: the only write route ----------------------------------------------------------------------------

        def do_POST(self):  # noqa: N802
            self._guarded(self._post)

        def _method_not_allowed(self):
            self._guarded(lambda: (_ for _ in ()).throw(HttpError(405, "Méthode non autorisée.")))

        do_PUT = do_DELETE = do_PATCH = do_OPTIONS = _method_not_allowed  # noqa: N815

        def _post(self):
            parts = self._segments()
            if not (len(parts) == 4 and parts[:2] == ["api", "sessions"] and parts[3] == "actions"):
                raise HttpError(404, "Introuvable.")
            store.resolve_session(parts[2])
            length = self._content_length()
            if (self.headers.get("Content-Type") or "").split(";")[0].strip().lower() != "application/json":
                raise HttpError(415, "Type de contenu refusé : application/json attendu.")
            port = self.server.server_address[1]
            if self.headers.get("Origin") not in (f"http://127.0.0.1:{port}", f"http://localhost:{port}"):
                raise HttpError(403, "Origine refusée.")
            body = self.rfile.read(length)
            if len(body) != length:
                raise HttpError(400, "Corps incomplet.")
            self._json(200, store.act(parts[2], _parse_json(body)))

        def _content_length(self):
            if self.headers.get("Transfer-Encoding") is not None:
                raise HttpError(411, "Content-Length requis (corps fragmenté refusé).")
            raw = self.headers.get("Content-Length")
            if raw is None:
                raise HttpError(411, "Content-Length requis.")
            if not raw.isascii() or not raw.isdigit():
                raise HttpError(400, "Content-Length invalide.")
            length = int(raw)
            if length > MAX_BODY:
                raise HttpError(413, "Corps trop gros.")
            return length

    return Handler


class AnnotationServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, store, port=DEFAULT_PORT, static_dir=STATIC_DIR):
        self.store = store
        super().__init__((HOST, port), _make_handler(store, Path(static_dir)))


def make_server(captures, annotations_dir, port=DEFAULT_PORT, static_dir=STATIC_DIR, clock=_clock):
    """Bind the server on 127.0.0.1 (raises OSError when the port is in use)."""
    captures, annotations_dir = Path(captures).resolve(), Path(annotations_dir).resolve()
    if annotations_dir == captures or captures in annotations_dir.parents:
        raise ValueError("Le dossier d'annotations ne doit pas être dans le dossier des captures (lecture seule).")
    annotations_dir.mkdir(parents=True, exist_ok=True)
    return AnnotationServer(SessionStore(captures, annotations_dir, clock), port, static_dir)
