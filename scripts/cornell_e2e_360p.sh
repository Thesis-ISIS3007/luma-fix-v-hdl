#!/usr/bin/env bash
# End-to-end: 640x360 Cornell (many hours of wall time). Do not use in CI.
# Usage: ./scripts/cornell_e2e_360p.sh
# Prereq:  make -C validation out/c_fx_rt_cornell_360p.hex
#          cp validation/out/c_fx_rt_cornell_360p.hex test/resources/programs/
# Output:  $LUMAFIXV_OUT_DIR/cornell_360p.log.bin, scripts/out/cornell_360p.ppm
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"
make -C "${repo_root}/validation" out/c_fx_rt_cornell_360p.hex
mkdir -p "${repo_root}/test/resources/programs"
cp -f "${repo_root}/validation/out/c_fx_rt_cornell_360p.hex" \
  "${repo_root}/test/resources/programs/"
mkdir -p "${repo_root}/scripts/out"
export LUMAFIXV_OUT_DIR="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
export LUMAFIXV_CORNELL_360P=1
touch "${repo_root}/.run-cbinary"
# Substring matches the single 360p test name; without LUMAFIXV_CORNELL_360P=1
# it cancels immediately. With the env var set, expect many hours of sim.
./mill test -z 640x360
python3 "${repo_root}/scripts/fx_rt_log_to_ppm.py" \
  "${LUMAFIXV_OUT_DIR}/cornell_360p.log.bin" \
  "${repo_root}/scripts/out/cornell_360p.ppm"
echo "Wrote ${repo_root}/scripts/out/cornell_360p.ppm"
