#!/usr/bin/env bash
# End-to-end: ChiselSim renders Cornell at 32x24, writes MMIO log, decodes PPM.
# Usage: ./scripts/cornell_e2e.sh
#   Optional: LUMAFIXV_OUT_DIR (default: $REPO_ROOT/scripts/out)
# Output:   $LUMAFIXV_OUT_DIR/cornell_smoke.log.bin
#           $REPO_ROOT/scripts/out/cornell_e2e.ppm
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"
mkdir -p "${repo_root}/scripts/out"
export LUMAFIXV_OUT_DIR="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
touch "${repo_root}/.run-cbinary"
echo >&2 "cornell_e2e.sh: disabled — CFxRtCornellProgramSpec (fx_rt_cornell_smoke) was removed from the repo."
echo >&2 "Build hex with make -C validation; run ChiselSim manually if you need cornell_smoke.log.bin."
exit 1
