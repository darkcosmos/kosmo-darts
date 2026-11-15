"""Entry point: python3.13 -m kosmo_camera.annotate --captures DIR --annotations DIR [--port 8765]."""
import argparse
import errno
import sys
from pathlib import Path

from kosmo_camera.annotate.server import DEFAULT_PORT, HOST, make_server


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="python3.13 -m kosmo_camera.annotate",
                                     description="Outil d'annotation des photos de capture (local, 127.0.0.1).")
    parser.add_argument("--captures", required=True, help="dossier des captures (lecture seule)")
    parser.add_argument("--annotations", required=True, help="dossier des fichiers d'annotations (écriture)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"port local (défaut {DEFAULT_PORT})")
    args = parser.parse_args(argv)
    captures = Path(args.captures).expanduser()
    if not captures.is_dir():
        print(f"Dossier des captures introuvable : {captures}", file=sys.stderr)
        return 2
    try:
        server = make_server(captures, Path(args.annotations).expanduser(), args.port)
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
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
