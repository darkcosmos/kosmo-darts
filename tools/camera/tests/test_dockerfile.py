"""Static checks of the training image (decisions 6, 7, 9, 10): the build itself runs only on Kosmo's GPU station."""
import hashlib
import re
import subprocess
from pathlib import Path

DOCKERFILE = Path(__file__).resolve().parent.parent / "Dockerfile"
MD5 = "fb6504cce7fbdc783f7a46991f07639f"


def _text():
    return DOCKERFILE.read_text(encoding="utf-8")


def _check_script():
    match = re.search(r"^RUN <<'EOF'\n(.*?)^EOF$", _text(), re.S | re.M)
    assert match, "weights check script not found"
    return match.group(1)


def _run(tmp_path, content, sha_arg, md5=None):
    weights = tmp_path / "w.pth"
    weights.write_bytes(content)
    script = _check_script().replace(MD5, md5 or hashlib.md5(content).hexdigest())
    env = {"W": str(weights), "NANO_SHA256": sha_arg, "PATH": "/usr/bin:/bin"}
    return subprocess.run(["sh", "-c", script], env=env, capture_output=True, text=True, check=False)


def test_base_image_tag_is_exact_and_weights_md5_is_present():
    assert re.search(r"^FROM python:3\.13\.\d+-slim$", _text(), re.M)
    assert MD5 in _text()
    assert "repo.roboflow.com/rfdetr/nano_coco/checkpoint_best_regular.pth" in _text()


def test_pip_installs_with_require_hashes_and_only_from_requirements_files():
    installs = [line for line in _text().replace("\\\n", " ").splitlines() if "pip install" in line]
    assert installs
    for line in installs:
        assert "--require-hashes" in line and "-r " in line and "--no-cache-dir" in line
        assert "https://download.pytorch.org/whl/cu130" in line


def test_no_user_no_entry_point_and_cache_environment():
    text = _text()
    assert not re.search(r"^(USER|ENTRYPOINT|CMD)\b", text, re.M)
    for pair in ("HOME=/tmp", "TORCH_HOME=/tmp/torch", "HF_HOME=/tmp/hf", "MPLCONFIGDIR=/tmp/mpl", "HF_HUB_OFFLINE=1"):
        assert pair in text


def test_image_copies_the_package_and_the_config_folder_next_to_it():
    copies = re.findall(r"^COPY (\S+) (\S+)$", _text(), re.M)
    assert ("kosmo_camera/", "/app/kosmo_camera/") in copies
    assert ("config/", "/app/config/") in copies  # config.py reads ../config/defaults.json at import
    assert (DOCKERFILE.parent / "config" / "defaults.json").is_file()
    assert copies.index(("config/", "/app/config/")) > copies.index(("kosmo_camera/", "/app/kosmo_camera/"))


def test_header_documents_the_drift_run():
    header = "\n".join(line for line in _text().splitlines() if line.startswith("#"))
    assert "python -m kosmo_camera.drift --captures /data/captures --annotations /data/annotations --runs /runs" in header
    assert "-v ~/kosmo-data:/data:ro" in header


def test_header_documents_the_run_flags_of_decision_10():
    header = "\n".join(line for line in _text().splitlines() if line.startswith("#"))
    for flag in ("--device nvidia.com/gpu=all", "--network none", "--cap-drop ALL", "--security-opt no-new-privileges",
                 "--read-only", "--tmpfs", "~/kosmo-data:/data:ro", "~/kosmo-runs:/runs:rw", "docker build"):
        assert flag in header, flag


def test_weights_check_fails_and_prints_sha_when_arg_is_empty(tmp_path):
    content = b"weights"
    sha = hashlib.sha256(content).hexdigest()
    done = _run(tmp_path, content, "")
    assert done.returncode != 0
    assert sha in done.stdout and sha in done.stderr


def test_weights_check_fails_when_sha_differs(tmp_path):
    done = _run(tmp_path, b"weights", "0" * 64)
    assert done.returncode != 0
    assert hashlib.sha256(b"weights").hexdigest() in done.stdout


def test_weights_check_fails_when_md5_differs(tmp_path):
    content = b"weights"
    done = _run(tmp_path, content, hashlib.sha256(content).hexdigest(), md5="0" * 32)
    assert done.returncode != 0 and "MD5" in done.stderr


def test_weights_check_passes_when_both_hashes_match(tmp_path):
    content = b"weights"
    assert _run(tmp_path, content, hashlib.sha256(content).hexdigest()).returncode == 0
