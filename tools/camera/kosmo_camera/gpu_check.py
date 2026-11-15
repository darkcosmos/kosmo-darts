"""GPU check run inside the training image: python -m kosmo_camera.gpu_check (standard library and torch only)."""
import shutil
import subprocess
import sys


def _nvidia_smi_output():
    exe = shutil.which("nvidia-smi")
    if exe is None:
        return "nvidia-smi: absent"
    try:
        done = subprocess.run([exe], capture_output=True, text=True, timeout=30, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        return f"nvidia-smi: failed ({error})"
    return (done.stdout + done.stderr).rstrip()


def run(out=None):
    """Print the report and return the exit code: 0 when CUDA is available, 1 otherwise."""
    import torch

    out = out if out is not None else sys.stdout
    print(_nvidia_smi_output(), file=out)
    print(f"torch version: {torch.__version__}", file=out)
    print(f"cuda version: {torch.version.cuda}", file=out)
    available = bool(torch.cuda.is_available())
    print(f"cuda available: {available}", file=out)
    if not available:
        return 1
    print(f"device name: {torch.cuda.get_device_name(0)}", file=out)
    return 0


if __name__ == "__main__":
    sys.exit(run())
