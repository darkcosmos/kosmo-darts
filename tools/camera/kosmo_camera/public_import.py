"""Public datasets as untrusted input (CV21, S10): strict class map reader and validated COCO export reader.

Only sets with an entry in `config/public-class-maps.json` are ever opened, under `<public_root>/<slug>/`, read-only:
nothing in this module writes, renames or deletes. Downloaded files are untrusted: every JSON is capped at
`MAX_JSON_BYTES`, every image at `MAX_IMAGE_BYTES` (both checked before reading and never read past the cap); a
`file_name` must be a bare name of a regular file (checked with `lstat`) inside `<slug>/<subset>/`, opened with
`O_NOFOLLOW`. A bad JSON or a bad set refuses the whole set (`PublicImportError`); a bad image is dropped and counted
per reason in `PublicImporter.dropped`.
"""
import hashlib
import io
import json
import math
import os
import re
import stat
import warnings
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Optional

from PIL import Image

from kosmo_camera.config import BOX_FRACTION

MAX_JSON_BYTES = 64 * 1024 * 1024
MAX_IMAGE_BYTES = 32 * 1024 * 1024
MAX_MAP_BYTES = 64 * 1024
MAP_FORMAT = "kosmo-camera-public-class-maps"
MAP_VERSION = 1
LICENCE = "CC BY 4.0"
SUBSETS = ("train", "valid", "test")
ANNOTATIONS_FILE = "_annotations.coco.json"
DEFAULT_MAP_PATH = Path(__file__).resolve().parent.parent / "config" / "public-class-maps.json"

TIP = "tip"
WHOLE_DART_CHECKED = "whole-dart-checked"
PSEUDO_TIP_BOX = "pseudo-tip-box"
IGNORE = "ignore"
DROP_IMAGE = "drop-image"
TARGETS = (TIP, WHOLE_DART_CHECKED, PSEUDO_TIP_BOX, IGNORE, DROP_IMAGE)

# Reasons under which an image is dropped and counted (keys of `PublicImporter.dropped`).
BAD_FILE_NAME = "bad-file-name"
NOT_REGULAR_FILE = "not-regular-file"
IMAGE_TOO_LARGE = "image-too-large"
UNDECODABLE = "undecodable"
SIZE_MISMATCH = "size-mismatch"
BAD_BOX = "bad-box"

_SLUG = re.compile(r"[a-z0-9_-]{1,64}")
_MAX_NAME = 200
_MAX_FILE_NAME = 255

Box = tuple  # (x, y, width, height) in source pixels


class PublicImportError(ValueError):
    """A class map or a public set is refused; the message names the cause."""


@dataclass(frozen=True)
class SetMap:
    licence: str
    categories: dict  # source category name -> target


@dataclass(frozen=True)
class ClassMap:
    sets: dict  # slug -> SetMap


@dataclass(frozen=True)
class PublicImage:
    slug: str
    subset: str
    file_name: str
    path: Path
    width: int
    height: int
    sha256: str
    tips: tuple  # square boxes (x, y, side, side) centred on the source box centres, side BOX_FRACTION * longer side
    pseudo_boxes: tuple  # raw `pseudo-tip-box` boxes (x, y, width, height), source pixels; never labels


def _no_duplicates(pairs):
    keys = [key for key, _ in pairs]
    if len(set(keys)) != len(keys):
        raise ValueError("duplicate key")
    return dict(pairs)


def _parse(data, what):
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=_no_duplicates)
    except (ValueError, RecursionError) as error:
        raise PublicImportError(f"{what}: invalid JSON ({error})") from error


def _is_int(value):
    return isinstance(value, int) and not isinstance(value, bool)


def _is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _only_keys(obj, keys, where):
    if not isinstance(obj, dict) or set(obj) != set(keys):
        raise PublicImportError(f"{where}: expected an object with exactly the keys {sorted(keys)}")


def decode_class_map(data: bytes) -> ClassMap:
    """Strict reader of the class map: cap, unknown or duplicated key, target outside `TARGETS`, bad slug."""
    if len(data) > MAX_MAP_BYTES:
        raise PublicImportError(f"class map: larger than {MAX_MAP_BYTES} bytes")
    root = _parse(data, "class map")
    _only_keys(root, ("format", "version", "sets"), "class map")
    if root["format"] != MAP_FORMAT or not _is_int(root["version"]) or root["version"] != MAP_VERSION:
        raise PublicImportError(f"class map: expected format {MAP_FORMAT} version {MAP_VERSION}")
    if not isinstance(root["sets"], dict):
        raise PublicImportError("class map: sets must be an object")
    sets = {}
    for slug, entry in root["sets"].items():
        if not _SLUG.fullmatch(slug):
            raise PublicImportError(f"class map: slug {slug!r} does not match {_SLUG.pattern}")
        _only_keys(entry, ("licence", "categories"), f"class map set {slug}")
        if entry["licence"] != LICENCE:
            raise PublicImportError(f"class map set {slug}: licence must be {LICENCE}")
        categories = entry["categories"]
        if not isinstance(categories, dict) or not categories:
            raise PublicImportError(f"class map set {slug}: categories must be a non-empty object")
        for name, target in categories.items():
            if not name or len(name) > _MAX_NAME:
                raise PublicImportError(f"class map set {slug}: bad category name")
            if target not in TARGETS:
                raise PublicImportError(f"class map set {slug}: target {target!r} of {name!r} is not in {TARGETS}")
        sets[slug] = SetMap(LICENCE, dict(categories))
    return ClassMap(sets)


def load_class_map(path=DEFAULT_MAP_PATH) -> ClassMap:
    return decode_class_map(_read_capped(Path(path), MAX_MAP_BYTES, "class map"))


class _Unreadable(Exception):
    """A file cannot be read safely; `reason` is the drop counter key."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def _read_regular(path: Path, cap: int, too_large: str, not_regular: str) -> bytes:
    """Bytes of a regular file of at most `cap` bytes, never following a link, never reading past the cap."""
    try:
        before = os.lstat(path)
    except OSError as error:
        raise _Unreadable(not_regular) from error
    if not stat.S_ISREG(before.st_mode):
        raise _Unreadable(not_regular)
    if before.st_size > cap:
        raise _Unreadable(too_large)
    try:
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0))
    except OSError as error:
        raise _Unreadable(not_regular) from error
    with os.fdopen(descriptor, "rb") as handle:
        if not stat.S_ISREG(os.fstat(handle.fileno()).st_mode):
            raise _Unreadable(not_regular)
        data = handle.read(cap + 1)
    if len(data) > cap:
        raise _Unreadable(too_large)
    return data


def _read_capped(path: Path, cap: int, what: str) -> bytes:
    try:
        return _read_regular(path, cap, "too-large", "unreadable")
    except _Unreadable as error:
        detail = f"larger than {cap} bytes" if error.reason == "too-large" else "not a readable regular file"
        raise PublicImportError(f"{what} {path.name}: {detail}") from error


def _is_dir(path: Path) -> bool:
    try:
        return stat.S_ISDIR(os.lstat(path).st_mode)
    except OSError:
        return False


@dataclass(frozen=True)
class _Subset:
    name: str
    folder: Path
    images: list
    annotations: list
    categories: dict  # id -> name


def _check_categories(raw, where):
    if not isinstance(raw, list):
        raise PublicImportError(f"{where}: categories must be a list")
    categories = {}
    for item in raw:
        if not isinstance(item, dict) or not _is_int(item.get("id")) or not isinstance(item.get("name"), str):
            raise PublicImportError(f"{where}: a category needs an integer id and a name")
        if item["id"] in categories:
            raise PublicImportError(f"{where}: duplicated category id {item['id']}")
        categories[item["id"]] = item["name"]
    if len(set(categories.values())) != len(categories):
        raise PublicImportError(f"{where}: duplicated category name")
    return categories


def _check_images(raw, where):
    if not isinstance(raw, list):
        raise PublicImportError(f"{where}: images must be a list")
    ids = set()
    for item in raw:
        if not isinstance(item, dict) or not _is_int(item.get("id")) or not isinstance(item.get("file_name"), str):
            raise PublicImportError(f"{where}: an image needs an integer id and a file_name")
        if not all(_is_int(item.get(key)) and item[key] > 0 for key in ("width", "height")):
            raise PublicImportError(f"{where}: image {item['id']} needs a positive integer width and height")
        if item["id"] in ids:
            raise PublicImportError(f"{where}: duplicated image id {item['id']}")
        ids.add(item["id"])
    return ids


def _check_annotations(raw, image_ids, category_ids, where):
    if not isinstance(raw, list):
        raise PublicImportError(f"{where}: annotations must be a list")
    for item in raw:
        if not isinstance(item, dict) or not _is_int(item.get("image_id")) or not _is_int(item.get("category_id")):
            raise PublicImportError(f"{where}: an annotation needs integer image_id and category_id")
        if item["image_id"] not in image_ids:
            raise PublicImportError(f"{where}: dangling image_id {item['image_id']}")
        if item["category_id"] not in category_ids:
            raise PublicImportError(f"{where}: dangling category_id {item['category_id']}")


def _read_subset(slug_folder: Path, name: str) -> Optional[_Subset]:
    folder = slug_folder / name
    if not _is_dir(folder):
        return None
    where = f"{slug_folder.name}/{name}"
    data = _read_capped(folder / ANNOTATIONS_FILE, MAX_JSON_BYTES, f"{where}/")
    root = _parse(data, f"{where}/{ANNOTATIONS_FILE}")
    if not isinstance(root, dict) or not all(key in root for key in ("images", "annotations", "categories")):
        raise PublicImportError(f"{where}: images, annotations and categories are required")
    categories = _check_categories(root["categories"], where)
    image_ids = _check_images(root["images"], where)
    _check_annotations(root["annotations"], image_ids, categories, where)
    return _Subset(name, folder, root["images"], root["annotations"], categories)


def _bare_name(name: str) -> bool:
    return (0 < len(name) <= _MAX_FILE_NAME and not any(char in name for char in ("/", "\\", "\0"))
            and ".." not in name and name != "." and not os.path.isabs(name))


def _valid_box(raw, width, height) -> Optional[Box]:
    if not isinstance(raw, list) or len(raw) != 4 or not all(_is_number(value) for value in raw):
        return None
    try:
        x, y, w, h = (float(value) for value in raw)
    except OverflowError:
        return None
    if not all(math.isfinite(value) for value in (x, y, w, h)):
        return None
    if w <= 0 or h <= 0 or x < 0 or y < 0 or x + w > width or y + h > height:
        return None
    return (x, y, w, h)


def _decode_size(data: bytes):
    """Decoded size of an image; any decoding failure, truncation or decompression bomb raises `_Unreadable`."""
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as image:
                size = image.size
                image.load()
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
        raise _Unreadable(UNDECODABLE) from error
    return size


def _tip_box(box: Box, width, height) -> Box:
    side = BOX_FRACTION * max(width, height)
    return (box[0] + box[2] / 2 - side / 2, box[1] + box[3] / 2 - side / 2, side, side)


def _inside(box: Box, x, y) -> bool:
    return box[0] <= x <= box[0] + box[2] and box[1] <= y <= box[1] + box[3]


class PublicImporter:
    """Validated reader of the mapped public sets under `public_root`; `dropped[slug]` counts drops per reason."""

    def __init__(self, public_root, class_map: ClassMap):
        self.public_root = Path(public_root)
        self.class_map = class_map
        self.dropped = {}

    def _load_set(self, slug):
        set_map = self.class_map.sets.get(slug)
        if set_map is None:
            raise PublicImportError(f"{slug}: no entry in the class map")
        slug_folder = self.public_root / slug
        if not _is_dir(slug_folder):
            raise PublicImportError(f"{slug}: not a folder of {self.public_root.name}")
        subsets = [subset for subset in (_read_subset(slug_folder, name) for name in SUBSETS) if subset is not None]
        if not subsets:
            raise PublicImportError(f"{slug}: no subset folder ({', '.join(SUBSETS)})")
        first = subsets[0].categories
        for subset in subsets:
            if subset.categories != first:
                raise PublicImportError(f"{slug}: category lists differ between {subsets[0].name} and {subset.name}")
        names = set(first.values())
        absent = sorted(set(set_map.categories) - names)
        if absent:
            raise PublicImportError(f"{slug}: mapped categories absent from the set: {absent}")
        annotated = {item["category_id"] for subset in subsets for item in subset.annotations}
        unmapped = sorted(first[cid] for cid in annotated if first[cid] not in set_map.categories)
        if unmapped:
            raise PublicImportError(f"{slug}: annotated categories absent from the class map: {unmapped}")
        return set_map, subsets

    def iter_images(self, slug) -> Iterator[PublicImage]:
        """Refuses the set at once (`PublicImportError`), then yields the images that pass every check."""
        set_map, subsets = self._load_set(slug)
        counter = Counter()
        self.dropped[slug] = counter
        return self._generate(slug, set_map, subsets, counter)

    def _generate(self, slug, set_map, subsets, counter):
        for subset in subsets:
            targets = {cid: set_map.categories.get(name) for cid, name in subset.categories.items()}
            by_image = {}
            for item in subset.annotations:
                by_image.setdefault(item["image_id"], []).append(item)
            for entry in subset.images:
                reason, image = None, None
                try:
                    image = self._check_image(slug, subset, entry, by_image.get(entry["id"], ()), targets)
                except _Unreadable as error:
                    reason = error.reason
                if reason is not None:
                    counter[reason] += 1
                elif image is not None:
                    yield image

    def _check_image(self, slug, subset, entry, annotations, targets):
        """The `PublicImage`, or None when the image is dropped by a counted target rule (counter updated here)."""
        counter = self.dropped[slug]
        used = [(item, targets[item["category_id"]]) for item in annotations]
        if any(target == DROP_IMAGE for _, target in used):
            counter[DROP_IMAGE] += 1
            return None
        name = entry["file_name"]
        if not _bare_name(name):
            raise _Unreadable(BAD_FILE_NAME)
        path = subset.folder / name
        data = _read_regular(path, MAX_IMAGE_BYTES, IMAGE_TOO_LARGE, NOT_REGULAR_FILE)
        width, height = entry["width"], entry["height"]
        if _decode_size(data) != (width, height):
            raise _Unreadable(SIZE_MISMATCH)
        boxes = {}
        for item, target in used:
            if target == IGNORE:
                continue
            box = _valid_box(item.get("bbox"), width, height)
            if box is None:
                raise _Unreadable(BAD_BOX)
            boxes.setdefault(target, []).append(box)
        tip_centres = [(box[0] + box[2] / 2, box[1] + box[3] / 2) for box in boxes.get(TIP, ())]
        for dart in boxes.get(WHOLE_DART_CHECKED, ()):
            if not any(_inside(dart, x, y) for x, y in tip_centres):
                counter[WHOLE_DART_CHECKED] += 1
                return None
        return PublicImage(
            slug, subset.name, name, path, width, height, hashlib.sha256(data).hexdigest(),
            tuple(_tip_box(box, width, height) for box in boxes.get(TIP, ())),
            tuple(boxes.get(PSEUDO_TIP_BOX, ())),
        )
