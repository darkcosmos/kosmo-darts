"""Synthetic Roboflow-style COCO exports for the public import tests (generated data only, never Kosmo's files).

Layout: `<root>/<slug>/<subset>/_annotations.coco.json` next to the images. Pillow is imported inside the helpers that
write images, so importing this module needs no third-party package.
"""
import hashlib
import json
from pathlib import Path

from kosmo_camera import public_import as pi

SLUG = "demo-set-v1i-coco"
CATEGORIES = [(0, "darts"), (1, "dart_tip"), (2, "Dart"), (3, "extra")]  # id 0: Roboflow supercategory, no annotation


def class_map(categories, slug=SLUG):
    """A `ClassMap` for one set; `categories` maps source names to targets."""
    entry = {"licence": "CC BY 4.0", "categories": categories}
    data = {"format": pi.MAP_FORMAT, "version": 1, "sets": {slug: entry}}
    return pi.decode_class_map(json.dumps(data).encode())


def write_image(path, size=(200, 100), color=(200, 30, 30), fmt="JPEG"):
    from PIL import Image
    Image.new("RGB", size, color).save(path, fmt)


def write_subset(root, subset="train", images=(), annotations=(), categories=CATEGORIES, slug=SLUG, write_files=True):
    """Write one subset: `images` = (id, file_name, width, height), `annotations` = (image_id, category_id, bbox)."""
    folder = Path(root) / slug / subset
    folder.mkdir(parents=True, exist_ok=True)
    for _, name, width, height in images:
        if write_files and "/" not in name and "\\" not in name and name not in ("", ".."):
            write_image(folder / name, (width, height))
    document = {
        "categories": [{"id": cid, "name": name} for cid, name in categories],
        "images": [{"id": iid, "file_name": name, "width": w, "height": h} for iid, name, w, h in images],
        "annotations": [{"id": n + 1, "image_id": iid, "category_id": cid, "bbox": bbox}
                        for n, (iid, cid, bbox) in enumerate(annotations)],
    }
    write_document(folder, document)
    return folder


def write_document(folder, document):
    """The raw JSON file of a subset: a dict is dumped (NaN allowed, as Python writes it), bytes are written as is."""
    data = document if isinstance(document, bytes) else json.dumps(document).encode()
    (Path(folder) / pi.ANNOTATIONS_FILE).write_bytes(data)


def tree_hashes(root):
    """SHA-256 of every file under `root`, by relative path (to prove the sources stay unchanged)."""
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(Path(root).rglob("*")) if path.is_file()}
