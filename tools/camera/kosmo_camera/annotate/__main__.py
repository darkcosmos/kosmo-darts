"""Entry point: python3 -m kosmo_camera.annotate --captures DIR --annotations DIR [--split FILE] [--frames DIR]
[--port 8765]."""
import argparse
import errno
import sys
from pathlib import Path

from kosmo_camera.annotate import flow
from kosmo_camera.annotate.control_state import DEFAULT_LOCK, ControlSources
from kosmo_camera.annotate.server import DEFAULT_PORT, HOST, make_server
from kosmo_camera.annotate.snapping import Snapper

SNAP_AVAILABLE = "Recalage : disponible"
SNAP_UNAVAILABLE = "Recalage : indisponible (numpy ou Pillow absent)"


def _path(value):
    return Path(value).expanduser() if value else None


def main(argv=None) -> int:
    snapper = Snapper()
    parser = argparse.ArgumentParser(prog="python3 -m kosmo_camera.annotate",
                                     description="Outil d'annotation des photos de capture (local, 127.0.0.1).",
                                     epilog=SNAP_AVAILABLE if snapper.available else SNAP_UNAVAILABLE)
    parser.add_argument("--captures", required=True, help="dossier des captures (lecture seule)")
    parser.add_argument("--annotations", required=True, help="dossier des fichiers d'annotations (écriture)")
    parser.add_argument("--drift", help="fichier de dérive drift.json (lecture seule, facultatif)")
    parser.add_argument("--split", help="split.json du jeu de contrôle (active le contrôle de calibration, lecture seule)")
    parser.add_argument("--lock", default=str(DEFAULT_LOCK), help="verrou du split (défaut : celui du dépôt)")
    parser.add_argument("--frames", help="dossier des images vidéo extraites, avec frames.json (lecture seule)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"port local (défaut {DEFAULT_PORT})")
    args = parser.parse_args(argv)
    captures = Path(args.captures).expanduser()
    if not captures.is_dir():
        print(f"Dossier des captures introuvable : {captures}", file=sys.stderr)
        return 2
    try:
        server = make_server(captures, Path(args.annotations).expanduser(), args.port,
                             drift=Path(args.drift).expanduser() if args.drift else None, snapper=snapper,
                             controls=ControlSources(_path(args.split), _path(args.lock), _path(args.frames)))
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2
    except OSError as error:
        if error.errno == errno.EADDRINUSE:
            print(f"Le port {args.port} est déjà utilisé : fermez l'autre outil ou choisissez --port.", file=sys.stderr)
        else:
            print(f"Impossible de démarrer le serveur : {error.strerror or error}", file=sys.stderr)
        return 2
    print(f"Outil d'annotation prêt : http://{HOST}:{server.server_address[1]}/  (Ctrl+C pour arrêter)")
    print(SNAP_AVAILABLE if snapper.available else f"{SNAP_UNAVAILABLE} : {flow.UNAVAILABLE}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
