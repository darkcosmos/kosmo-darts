"""Public import (CV21, S10, D22, D23, D27): strict class map, validated COCO exports, untrusted files. Synthetic data."""
import builtins
import json
import os

import pytest

pytest.importorskip("PIL")  # only this module needs Pillow; conftest.py stays free of importorskip

from coco_fixtures import SLUG, class_map, tree_hashes, write_document, write_image, write_subset  # noqa: E402
from kosmo_camera import config  # noqa: E402
from kosmo_camera import public_import as pi  # noqa: E402

TIP_MAP = {"dart_tip": "tip", "Dart": "whole-dart-checked", "extra": "ignore"}


def _map_json(sets=None, **changes):
    data = {"format": pi.MAP_FORMAT, "version": 1, "sets": {} if sets is None else sets}
    data.update(changes)
    return json.dumps(data).encode()


def _entry(**changes):
    entry = {"licence": "CC BY 4.0", "categories": {"dart_tip": "tip"}}
    entry.update(changes)
    return entry


def _images(root, imgs, anns, mapping=None, subset="train", **kwargs):
    write_subset(root, subset, imgs, anns, **kwargs)
    importer = pi.PublicImporter(root, class_map(TIP_MAP if mapping is None else mapping))
    result = list(importer.iter_images(SLUG))
    return result, importer.dropped[SLUG]


def _one(root, name="a.jpg", bbox=(10, 10, 20, 20), size=(200, 100), cid=1):
    """Write one image with one box of category `cid`; returns (images, dropped)."""
    return _images(root, [(1, name, *size)], [(1, cid, list(bbox))])


def _refused(root, importer=None, slug=SLUG):
    importer = importer or pi.PublicImporter(root, class_map(TIP_MAP))
    with pytest.raises(pi.PublicImportError) as info:
        importer.iter_images(slug)
    return str(info.value)


# ---- class map reader -------------------------------------------------------------------------------------------

def test_committed_map_loads_with_no_set_yet():
    assert pi.load_class_map().sets is not None


def test_map_valid_entry_with_every_target():
    entry = _entry(categories={"a": "tip", "b": "whole-dart-checked", "c": "pseudo-tip-box", "d": "ignore",
                               "e": "drop-image"})
    loaded = pi.decode_class_map(_map_json({"good-set_1": entry}))
    assert loaded.sets["good-set_1"].categories["c"] == "pseudo-tip-box"


@pytest.mark.parametrize("data", [
    _map_json(extra=1),
    _map_json(format="other"),
    _map_json(version=2),
    _map_json({"s": _entry(extra=1)}),
    _map_json({"s": _entry(licence="CC BY-NC 4.0")}),
    _map_json({"s": _entry(categories={})}),
    _map_json({"s": _entry(categories={"x": "calibration"})}),
    _map_json({"s": _entry(categories={"x": "Tip"})}),
    _map_json({"s": _entry(categories={"x": "cal1"})}),
    b'{"format": "kosmo-camera-public-class-maps", "version": 1, "sets": {}, "sets": {}}',
    b"not json",
])
def test_map_rejections(data):
    with pytest.raises(pi.PublicImportError):
        pi.decode_class_map(data)


@pytest.mark.parametrize("slug", ["Upper", "../x", "a/b", "", "a" * 65, "dot.dot", "sp ace"])
def test_map_slug_must_match_pattern(slug):
    with pytest.raises(pi.PublicImportError, match="slug"):
        pi.decode_class_map(_map_json({slug: _entry()}))


def test_map_cap_64_kib():
    base = _map_json({"s": _entry(categories={"x" * 10: "tip"})})
    pad = pi.MAX_MAP_BYTES - len(base)
    at_cap = base[:-1] + b" " * pad + base[-1:]
    assert len(at_cap) == pi.MAX_MAP_BYTES
    pi.decode_class_map(at_cap)
    with pytest.raises(pi.PublicImportError, match="larger"):
        pi.decode_class_map(at_cap + b" ")


# ---- targets ----------------------------------------------------------------------------------------------------

def test_unannotated_supercategory_at_id_0_is_ignored(tmp_path):
    images, dropped = _one(tmp_path)
    assert len(images) == 1 and not dropped


def test_tip_box_centred_with_box_fraction_side(tmp_path):
    (image,), _ = _one(tmp_path, bbox=(40, 20, 20, 10), size=(200, 100))
    side = config.BOX_FRACTION * 200
    (x, y, w, h), = image.tips
    assert (w, h) == (side, side)
    assert (x + side / 2, y + side / 2) == (50.0, 25.0)
    assert (image.width, image.height, image.subset, image.file_name) == (200, 100, "train", "a.jpg")


def test_tip_side_uses_the_longer_image_side(tmp_path):
    (image,), _ = _one(tmp_path, size=(100, 400))
    assert image.tips[0][2] == config.BOX_FRACTION * 400


def test_sha256_and_path_of_the_image(tmp_path):
    (image,), _ = _one(tmp_path)
    assert image.sha256 == tree_hashes(tmp_path)[f"{SLUG}/train/a.jpg"]
    assert image.path == tmp_path / SLUG / "train" / "a.jpg"


def test_whole_dart_with_a_tip_inside_is_kept_and_not_labelled(tmp_path):
    images, dropped = _images(tmp_path, [(1, "a.jpg", 200, 100)],
                              [(1, 2, [10, 10, 100, 60]), (1, 1, [40, 30, 10, 10])])
    assert len(images) == 1 and len(images[0].tips) == 1 and not dropped


def test_whole_dart_without_a_tip_inside_drops_the_image_counted_under_its_rule(tmp_path):
    images, dropped = _images(tmp_path, [(1, "a.jpg", 200, 100), (2, "b.jpg", 200, 100)],
                              [(1, 2, [10, 10, 50, 50]), (1, 1, [150, 70, 10, 10]), (2, 1, [5, 5, 10, 10])])
    assert [image.file_name for image in images] == ["b.jpg"]
    assert dict(dropped) == {"whole-dart-checked": 1}


def test_ignore_target_drops_the_annotation_only(tmp_path):
    images, dropped = _images(tmp_path, [(1, "a.jpg", 200, 100)], [(1, 3, [0, 0, 50, 50]), (1, 1, [10, 10, 10, 10])])
    assert len(images) == 1 and len(images[0].tips) == 1 and not dropped


def test_drop_image_target_drops_the_image_counted(tmp_path):
    images, dropped = _images(tmp_path, [(1, "a.jpg", 200, 100), (2, "b.jpg", 200, 100)],
                              [(1, 3, [10, 10, 10, 10]), (2, 1, [10, 10, 10, 10])],
                              mapping={"dart_tip": "tip", "extra": "drop-image", "Dart": "ignore"})
    assert [image.file_name for image in images] == ["b.jpg"]
    assert dict(dropped) == {"drop-image": 1}


def test_pseudo_tip_boxes_are_returned_raw_and_never_labelled(tmp_path):
    images, _ = _images(tmp_path, [(1, "a.jpg", 200, 100)], [(1, 1, [12, 8, 30, 40])],
                        mapping={"dart_tip": "pseudo-tip-box", "Dart": "ignore", "extra": "ignore"})
    assert images[0].tips == () and images[0].pseudo_boxes == ((12.0, 8.0, 30.0, 40.0),)


def test_every_subset_is_read_in_order(tmp_path):
    for subset in ("test", "train", "valid"):
        write_subset(tmp_path, subset, [(1, f"{subset}.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert [(i.subset, i.file_name) for i in importer.iter_images(SLUG)] == [
        ("train", "train.jpg"), ("valid", "valid.jpg"), ("test", "test.jpg")]


# ---- set refusals -----------------------------------------------------------------------------------------------

def test_annotated_category_absent_from_the_map_refuses_the_set(tmp_path):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10]), (1, 3, [0, 0, 9, 9])])
    importer = pi.PublicImporter(tmp_path, class_map({"dart_tip": "tip", "Dart": "ignore"}))
    assert "extra" in _refused(tmp_path, importer)


def test_mapped_name_absent_from_the_set_refuses_it(tmp_path):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    importer = pi.PublicImporter(tmp_path, class_map({**TIP_MAP, "Dart-Tip": "tip"}))
    assert "Dart-Tip" in _refused(tmp_path, importer)


def test_differing_category_lists_between_subsets_refuse_the_set(tmp_path):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    write_subset(tmp_path, "valid", [(1, "b.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])],
                 categories=[(0, "darts"), (1, "dart_tip"), (2, "Dart"), (3, "other")])
    assert "differ" in _refused(tmp_path)


def test_slug_absent_from_the_map_is_refused_and_its_folder_not_opened(tmp_path):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])], slug="rejected-set")
    assert "class map" in _refused(tmp_path, slug="rejected-set")


def test_missing_set_folder_and_empty_set_are_refused(tmp_path):
    assert "folder" in _refused(tmp_path)
    (tmp_path / SLUG).mkdir()
    assert "no subset" in _refused(tmp_path)


def test_unmapped_folder_is_never_opened(tmp_path, monkeypatch):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    write_subset(tmp_path, "train", [(1, "z.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])], slug="unmapped-set")
    touched = []
    for owner, name in ((os, "open"), (os, "lstat"), (os, "stat"), (os, "scandir"), (os, "listdir"),
                        (builtins, "open")):
        real = getattr(owner, name)

        def spy(path, *args, _real=real, **kwargs):
            touched.append(str(path))
            return _real(path, *args, **kwargs)
        monkeypatch.setattr(owner, name, spy)
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert len(list(importer.iter_images(SLUG))) == 1
    assert touched and not any("unmapped-set" in path for path in touched)


# ---- untrusted files (S10) --------------------------------------------------------------------------------------

@pytest.mark.parametrize("name", ["../x.jpg", "sub/x.jpg", "..\\x.jpg", "a\\b.jpg", "a..b.jpg", "x\0.jpg", "", "..",
                                  "/etc/passwd", "a" * 256])
def test_file_name_not_bare_is_dropped_and_nothing_outside_is_read(tmp_path, name):
    folder = write_subset(tmp_path, "train", [(1, name, 200, 100)], [(1, 1, [10, 10, 10, 10])], write_files=False)
    write_image(tmp_path / SLUG / "x.jpg")  # a real image outside the subset folder, one level up
    write_image(folder / "x.jpg")
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"bad-file-name": 1}


def test_symlinked_image_is_dropped(tmp_path):
    folder = write_subset(tmp_path, "train", [(1, "link.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])],
                          write_files=False)
    write_image(tmp_path / "outside.jpg")
    try:
        (folder / "link.jpg").symlink_to(tmp_path / "outside.jpg")
    except (OSError, NotImplementedError):
        pytest.skip("this filesystem cannot create symbolic links")
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"not-regular-file": 1}


def test_symlinked_annotation_file_refuses_the_set(tmp_path):
    folder = write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    real = tmp_path / "real.json"
    (folder / pi.ANNOTATIONS_FILE).rename(real)
    try:
        (folder / pi.ANNOTATIONS_FILE).symlink_to(real)
    except (OSError, NotImplementedError):
        pytest.skip("this filesystem cannot create symbolic links")
    assert "regular file" in _refused(tmp_path)


def test_directory_named_like_an_image_is_dropped(tmp_path):
    folder = write_subset(tmp_path, "train", [(1, "dir.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])], write_files=False)
    (folder / "dir.jpg").mkdir()
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"not-regular-file": 1}


def test_missing_image_is_dropped(tmp_path):
    write_subset(tmp_path, "train", [(1, "gone.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])], write_files=False)
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"not-regular-file": 1}


def test_oversized_image_is_dropped_before_decoding(tmp_path, monkeypatch):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    size = (tmp_path / SLUG / "train" / "a.jpg").stat().st_size
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    monkeypatch.setattr(pi, "MAX_IMAGE_BYTES", size)
    assert len(list(importer.iter_images(SLUG))) == 1  # at the cap
    monkeypatch.setattr(pi, "MAX_IMAGE_BYTES", size - 1)
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"image-too-large": 1}


def test_default_caps_are_the_documented_ones():
    assert pi.MAX_JSON_BYTES == 64 * 1024 * 1024 and pi.MAX_IMAGE_BYTES == 32 * 1024 * 1024


def test_decompression_bomb_is_dropped_with_pillows_limit_kept(tmp_path, monkeypatch):
    from PIL import Image
    write_subset(tmp_path, "train", [(1, "a.jpg", 100, 100)], [(1, 1, [10, 10, 10, 10])])
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 1000)  # 10000 px is above twice the limit: Pillow raises
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"undecodable": 1}


def test_image_between_one_and_two_times_the_pixel_limit_is_dropped_too(tmp_path, monkeypatch):
    from PIL import Image
    write_subset(tmp_path, "train", [(1, "a.jpg", 100, 100)], [(1, 1, [10, 10, 10, 10])])
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 7000)  # Pillow only warns here: the warning is an error for us
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"undecodable": 1}


def test_undecodable_and_truncated_images_are_dropped(tmp_path):
    folder = write_subset(tmp_path, "train", [(1, "junk.jpg", 200, 100), (2, "cut.jpg", 200, 100)],
                          [(1, 1, [10, 10, 10, 10]), (2, 1, [10, 10, 10, 10])])
    (folder / "junk.jpg").write_bytes(b"not an image")
    whole = (folder / "cut.jpg").read_bytes()
    (folder / "cut.jpg").write_bytes(whole[: len(whole) // 2])
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"undecodable": 2}


def test_size_different_from_the_json_is_dropped(tmp_path):
    folder = write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    write_image(folder / "a.jpg", (100, 200))
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"size-mismatch": 1}


@pytest.mark.parametrize("bbox", [
    [10, 10, 20], [10, 10, 20, 20, 1], "box", None, [10, 10, "a", 20], [True, 10, 20, 20],
    [float("nan"), 10, 20, 20], [10, float("inf"), 20, 20], [10, 10, float("-inf"), 20], [1e999, 1, 1, 1],
    [-1, 10, 20, 20], [10, -1, 20, 20], [190, 10, 20, 20], [10, 95, 20, 20], [10, 10, 0, 20], [10, 10, 20, -5],
    [10, 10, 191, 20],
])
def test_bad_boxes_drop_the_image_counted(tmp_path, bbox):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100)], [(1, 1, [10, 10, 10, 10])])
    document = json.loads((tmp_path / SLUG / "train" / pi.ANNOTATIONS_FILE).read_text())
    document["annotations"][0]["bbox"] = bbox
    write_document(tmp_path / SLUG / "train", document)
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    assert list(importer.iter_images(SLUG)) == []
    assert dict(importer.dropped[SLUG]) == {"bad-box": 1}


def test_box_touching_the_image_edge_is_kept(tmp_path):
    images, dropped = _one(tmp_path, bbox=(180, 80, 20, 20))
    assert len(images) == 1 and not dropped


def test_a_2700_by_3600_jpeg_is_kept(tmp_path):
    images, dropped = _one(tmp_path, name="big.jpg", size=(2700, 3600), bbox=(1000, 1000, 24, 24))
    assert len(images) == 1 and not dropped
    assert (images[0].width, images[0].height) == (2700, 3600)


def test_one_bad_image_does_not_hide_a_good_one(tmp_path):
    images, dropped = _images(tmp_path, [(1, "../x.jpg", 200, 100), (2, "b.jpg", 200, 100)],
                              [(1, 1, [10, 10, 10, 10]), (2, 1, [10, 10, 10, 10])])
    assert [image.file_name for image in images] == ["b.jpg"] and dict(dropped) == {"bad-file-name": 1}


# ---- untrusted JSON (S10) ---------------------------------------------------------------------------------------

def _valid_document():
    return {"categories": [{"id": 0, "name": "darts"}, {"id": 1, "name": "dart_tip"}, {"id": 2, "name": "Dart"},
                           {"id": 3, "name": "extra"}],
            "images": [{"id": 1, "file_name": "a.jpg", "width": 200, "height": 100}],
            "annotations": [{"id": 1, "image_id": 1, "category_id": 1, "bbox": [10, 10, 10, 10]}]}


def _refused_document(tmp_path, document):
    folder = tmp_path / SLUG / "train"
    folder.mkdir(parents=True)
    write_image(folder / "a.jpg")
    write_document(folder, document)
    return _refused(tmp_path)


def test_valid_document_baseline(tmp_path):
    folder = tmp_path / SLUG / "train"
    folder.mkdir(parents=True)
    write_image(folder / "a.jpg")
    write_document(folder, _valid_document())
    assert len(list(pi.PublicImporter(tmp_path, class_map(TIP_MAP)).iter_images(SLUG))) == 1


def _mutated(mutate):
    document = _valid_document()
    mutate(document)
    return document


@pytest.mark.parametrize("document, fragment", [
    (b"{not json", "invalid JSON"),
    (b"", "invalid JSON"),
    (b'{"images": [], "images": []}', "invalid JSON"),
    (b"[" * 100000, "invalid JSON"),
    (b"[]", "required"),
    (_mutated(lambda d: d["images"].append(dict(d["images"][0]))), "duplicated image id"),
    (_mutated(lambda d: d["categories"].append({"id": 1, "name": "again"})), "duplicated category id"),
    (_mutated(lambda d: d["categories"].append({"id": 9, "name": "Dart"})), "duplicated category name"),
    (_mutated(lambda d: d["annotations"][0].update(image_id=77)), "dangling image_id"),
    (_mutated(lambda d: d["annotations"][0].update(category_id=77)), "dangling category_id"),
    (_mutated(lambda d: d["images"][0].update(width=0)), "width"),
    (_mutated(lambda d: d["images"][0].update(id="1")), "integer id"),
    (_mutated(lambda d: d["categories"][0].update(id=True)), "integer id"),
    (_mutated(lambda d: d.update(images={})), "list"),
])
def test_bad_json_refuses_the_set(tmp_path, document, fragment):
    assert fragment in _refused_document(tmp_path, document)


def test_oversized_json_is_refused_at_the_cap_and_not_read_past_it(tmp_path, monkeypatch):
    folder = tmp_path / SLUG / "train"
    folder.mkdir(parents=True)
    write_image(folder / "a.jpg")
    write_document(folder, _valid_document())
    size = (folder / pi.ANNOTATIONS_FILE).stat().st_size
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    monkeypatch.setattr(pi, "MAX_JSON_BYTES", size)
    assert len(list(importer.iter_images(SLUG))) == 1  # at the cap
    monkeypatch.setattr(pi, "MAX_JSON_BYTES", size - 1)
    assert "larger" in _refused(tmp_path, importer)


def test_a_growing_file_is_not_read_past_the_cap(tmp_path, monkeypatch):
    """The size seen by lstat is below the cap but the file holds more: the bounded read still refuses it."""
    path = tmp_path / "big.json"
    path.write_bytes(b"x" * 100)
    real = os.lstat

    def small(target, *args, **kwargs):
        result = real(target, *args, **kwargs)
        return os.stat_result((result.st_mode, *result[1:6], 10, *result[7:]))
    monkeypatch.setattr(os, "lstat", small)
    with pytest.raises(pi._Unreadable):
        pi._read_regular(path, 50, "too-large", "unreadable")


# ---- sources stay unchanged -------------------------------------------------------------------------------------

def test_source_folders_are_never_written(tmp_path):
    write_subset(tmp_path, "train", [(1, "a.jpg", 200, 100), (2, "../x.jpg", 200, 100), (3, "b.jpg", 200, 100)],
                 [(1, 1, [10, 10, 10, 10]), (2, 1, [10, 10, 10, 10]), (3, 1, [999, 10, 10, 10])])
    write_subset(tmp_path, "valid", [(1, "c.jpg", 200, 100)], [(1, 2, [0, 0, 5, 5])])
    before = tree_hashes(tmp_path)
    names = sorted(path.name for path in tmp_path.rglob("*"))
    importer = pi.PublicImporter(tmp_path, class_map(TIP_MAP))
    list(importer.iter_images(SLUG))
    assert tree_hashes(tmp_path) == before
    assert sorted(path.name for path in tmp_path.rglob("*")) == names
    assert dict(importer.dropped[SLUG]) == {"bad-file-name": 1, "bad-box": 1, "whole-dart-checked": 1}

