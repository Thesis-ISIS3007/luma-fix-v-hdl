#!/usr/bin/env bash
# End-to-end: first 640x360 (cornell_360p.ppm), then 1280x720 (cornell_720p.ppm).
# Wall time: many hours for 360p plus multi-day for 720p on ChiselSim. Not for CI.
# Usage: ./scripts/cornell_e2e_720p.sh
#
# Output:
#   $LUMAFIXV_OUT_DIR/cornell_360p.log.bin, scripts/out/cornell_360p.ppm
#   $LUMAFIXV_OUT_DIR/cornell_720p.log.bin, scripts/out/cornell_720p.ppm
#
# Skip the 360p phase if you already have cornell_360p.ppm:
#   LUMAFIXV_SKIP_360P=1 ./scripts/cornell_e2e_720p.sh
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

if [[ "${LUMAFIXV_SKIP_360P:-}" != "1" ]]; then
  echo "==> Phase 1: 360p e2e -> scripts/out/cornell_360p.ppm"
  "${repo_root}/scripts/cornell_e2e_360p.sh"
else
  echo "==> Skipping 360p (LUMAFIXV_SKIP_360P=1)"
fi

echo "==> Phase 2: 720p e2e -> scripts/out/cornell_720p.ppm"
make -C "${repo_root}/validation" out/c_fx_rt_cornell_720p.hex
mkdir -p "${repo_root}/test/resources/programs"
cp -f "${repo_root}/validation/out/c_fx_rt_cornell_720p.hex" \
  "${repo_root}/test/resources/programs/"
mkdir -p "${repo_root}/scripts/out"
export LUMAFIXV_OUT_DIR="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
export LUMAFIXV_CORNELL_720P=1
touch "${repo_root}/.run-cbinary"
# Unique substring in the 720p test name; see CFxRtCornell720pProgramSpec.
./mill test -z 1280x720
python3 "${repo_root}/scripts/fx_rt_log_to_ppm.py" \
  "${LUMAFIXV_OUT_DIR}/cornell_720p.log.bin" \
  "${repo_root}/scripts/out/cornell_720p.ppm"
echo "Wrote ${repo_root}/scripts/out/cornell_720p.ppm"
