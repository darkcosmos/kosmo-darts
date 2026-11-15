"""GPU check (decision 14): report and exit code with CUDA patched, no GPU needed."""
import io
from unittest import mock

import torch

from kosmo_camera import gpu_check


def test_cuda_unavailable_and_nvidia_smi_absent_exits_1():
    out = io.StringIO()
    with mock.patch("shutil.which", return_value=None), mock.patch("torch.cuda.is_available", return_value=False):
        code = gpu_check.run(out)
    text = out.getvalue()
    assert code == 1
    assert "nvidia-smi: absent" in text
    assert f"torch version: {torch.__version__}" in text
    assert "cuda available: False" in text


def test_cuda_available_prints_device_name_and_exits_0():
    out = io.StringIO()
    with mock.patch("shutil.which", return_value=None), mock.patch("torch.cuda.is_available", return_value=True), \
            mock.patch("torch.cuda.get_device_name", return_value="Fake GPU"):
        code = gpu_check.run(out)
    assert code == 0
    assert "cuda available: True" in out.getvalue()
    assert "device name: Fake GPU" in out.getvalue()


def test_nvidia_smi_output_is_printed_when_present():
    out = io.StringIO()
    fake = mock.Mock(stdout="SMI TABLE\n", stderr="")
    with mock.patch("shutil.which", return_value="/usr/bin/nvidia-smi"), mock.patch("subprocess.run", return_value=fake), \
            mock.patch("torch.cuda.is_available", return_value=False):
        gpu_check.run(out)
    assert "SMI TABLE" in out.getvalue()
