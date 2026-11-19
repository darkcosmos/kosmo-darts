"""Pinned requirements (decision D10, S6): CPU and GPU files agree apart from the CUDA-specific packages."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TORCH_PACKAGES = {"torch", "torchvision", "torchao"}
# Packages that only the CUDA build of torch pulls in (NVIDIA runtime wheels, triton, cuda helpers).
CUDA_ONLY = re.compile(r"^(nvidia-.*|triton|cuda-.*)$")
LINE = re.compile(r"^([a-z0-9][a-z0-9.-]*)==(\S+) \\$")


def _parse(name):
    """Return {package: (version, [hashes])}, failing on any line that is not pinned with `==` and a hash."""
    packages, current = {}, None
    for raw in (ROOT / name).read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.startswith("#"):
            continue
        match = LINE.match(raw)
        if match:
            current = match.group(1)
            assert current not in packages
            packages[current] = (match.group(2), [])
            continue
        hash_match = re.match(r"^\s+--hash=sha256:([0-9a-f]{64})( \\)?$", raw)
        assert hash_match and current, f"unexpected line in {name}: {raw!r}"
        packages[current][1].append(hash_match.group(1))
    return packages


def test_every_package_is_pinned_with_at_least_one_hash():
    for name in ("requirements-cpu.txt", "requirements-gpu.txt"):
        packages = _parse(name)
        assert packages
        for package, (version, hashes) in packages.items():
            assert version and hashes, f"{package} in {name}"


def test_cpu_and_gpu_files_agree_apart_from_cuda_packages():
    cpu, gpu = _parse("requirements-cpu.txt"), _parse("requirements-gpu.txt")
    shared = {k: v[0] for k, v in cpu.items() if k not in TORCH_PACKAGES}
    gpu_shared = {k: v[0] for k, v in gpu.items() if k not in TORCH_PACKAGES and not CUDA_ONLY.match(k)}
    assert gpu_shared == shared
    assert {k for k in gpu if CUDA_ONLY.match(k)}, "the CUDA runtime wheels must be pinned"
    for name, (version, _) in gpu.items():
        if name in TORCH_PACKAGES:
            assert version.endswith("+cu130"), name
    for name, (version, _) in cpu.items():
        if name in TORCH_PACKAGES:
            assert version.endswith("+cpu"), name
    assert set(cpu) & TORCH_PACKAGES == set(gpu) & TORCH_PACKAGES == TORCH_PACKAGES


def test_torch_pair_versions_match_apart_from_the_local_suffix():
    cpu, gpu = _parse("requirements-cpu.txt"), _parse("requirements-gpu.txt")
    assert cpu["torch"][0].split("+")[0] == gpu["torch"][0].split("+")[0]
    assert cpu["torchao"][0].split("+")[0] == gpu["torchao"][0].split("+")[0]


# ---- tool environment files (T15n, D44, CV16): numpy and Pillow for Kosmo's WSL, pytest for the 3.14 test run ----------

TOOL_PACKAGES = {"numpy", "pillow"}  # no longer needed by the tool (ADR 0020); never in its test file
TOOL_TEST_PACKAGES = {"pytest", "iniconfig", "packaging", "pluggy", "pygments"}


def test_tool_test_requirements_hold_pytest_and_its_four_dependencies_at_the_cpu_pins_and_hashes():
    test_file, cpu = _parse("requirements-tool-test.txt"), _parse("requirements-cpu.txt")
    assert set(test_file) == TOOL_TEST_PACKAGES
    for name, pin in test_file.items():
        assert pin == cpu[name]
    assert set(test_file) & TOOL_PACKAGES == set()
