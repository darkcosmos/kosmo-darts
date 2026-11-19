# syntax=docker/dockerfile:1
# Training image of the camera tooling (ADR 0016, decisions 6, 7, 9, 10 and 14). GPU check only for now: no entry
# point; the training entry point comes with PR B and the image is rebuilt then.
#
# Build, from the repository root (the weights SHA-256 is pinned in NANO_SHA256 below; a different file fails the build):
#   docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera
#
# Run (decision 10: CDI access to the GPU only, no network, no capability, runs writable, tmpfs caches):
#   docker run --rm --device nvidia.com/gpu=all --network none --cap-drop ALL --security-opt no-new-privileges \
#     --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-runs:/runs:rw \
#     kosmo-camera:gpu python -m kosmo_camera.gpu_check
#
# The GPU check needs no data. Tools that read data add the optional mount -v ~/kosmo-data:/data:ro (read-only).
#
# No USER on purpose (D7): with rootless Docker, uid 0 inside is Kosmo's own uid.
# Residual risk: the weights are a pickle, trusted on first download after the MD5 check, then pinned by SHA-256 and
# loaded only in an offline, read-only, capability-free container.
FROM python:3.13.18-slim

ARG NANO_SHA256="d8d6b9ee57d4d0ed2b1f305163624712a0532cb7bce0c747317984fc5457440d"
ENV HOME=/tmp TORCH_HOME=/tmp/torch HF_HOME=/tmp/hf MPLCONFIGDIR=/tmp/mpl HF_HUB_OFFLINE=1 \
    PYTHONDONTWRITEBYTECODE=1 PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app
COPY requirements-gpu.txt /app/requirements-gpu.txt
RUN pip install --no-cache-dir --require-hashes --index-url https://download.pytorch.org/whl/cu130 \
    --extra-index-url https://pypi.org/simple -r /app/requirements-gpu.txt

COPY kosmo_camera/ /app/kosmo_camera/
# config.py reads config/defaults.json at import time (the single source of the tool constants), so the image needs it.
COPY config/ /app/config/

RUN mkdir -p /opt/weights \
    && python -c "import sys, urllib.request; urllib.request.urlretrieve(sys.argv[1], sys.argv[2])" \
       https://repo.roboflow.com/rfdetr/nano_coco/checkpoint_best_regular.pth /opt/weights/rf-detr-nano.pth

RUN <<'EOF'
set -eu
w="${W:-/opt/weights/rf-detr-nano.pth}"
md5="fb6504cce7fbdc783f7a46991f07639f"
got_md5="$(md5sum "$w" | cut -d' ' -f1)"
if [ "$got_md5" != "$md5" ]; then
  echo "MD5 mismatch for $w: $got_md5" >&2
  exit 1
fi
got="$(sha256sum "$w" | cut -d' ' -f1)"
echo "SHA-256 of rf-detr-nano.pth: $got"
if [ -z "${NANO_SHA256:-}" ] || [ "$got" != "$NANO_SHA256" ]; then
  echo "NANO_SHA256 is empty or different: report this SHA-256 to the developer, then rebuild with --build-arg NANO_SHA256=$got" >&2
  exit 1
fi
EOF
