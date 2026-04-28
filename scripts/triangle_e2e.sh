#!/usr/bin/env bash
# End-to-end triangle render on the core:
#   1) fx_rt_triangle_smoke  — dmem (no MMIO), fast; no CBinary sentinel needed.
#   2) fx_rt_triangle_render_smoke — MMIO log -> triangle_hw.log.bin + PNG (needs
#      .run-cbinary for CBinary-tagged spec).
# Usage: ./scripts/triangle_e2e.sh
# Output:  scripts/out/triangle_e2e.png  (from hardware log)
#          dmem test only validates ray–triangle math; no PNG from that path.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

echo "==> 1/2: fx_rt_triangle_smoke (dmem / intersection)"
./mill test.testOnly luma_fix_v.CFxRtProgramSpec
echo "OK: intersection smoke passed."

echo "==> 2/2: fx_rt_triangle_render_smoke (MMIO log -> PNG)"
mkdir -p "${repo_root}/scripts/out"
export LUMAFIXV_OUT_DIR="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
touch "${repo_root}/.run-cbinary"
./mill test.testOnly luma_fix_v.CFxRtTriangleRenderProgramSpec
if command -v uv >/dev/null 2>&1; then
  uv run "${repo_root}/scripts/fx_rt_log_to_png.py" \
    "${LUMAFIXV_OUT_DIR}/triangle_hw.log.bin" \
    "${repo_root}/scripts/out/triangle_e2e.png"
else
  python3.14 "${repo_root}/scripts/fx_rt_log_to_png.py" \
    "${LUMAFIXV_OUT_DIR}/triangle_hw.log.bin" \
    "${repo_root}/scripts/out/triangle_e2e.png"
fi
echo "Wrote ${repo_root}/scripts/out/triangle_e2e.png (from ${LUMAFIXV_OUT_DIR}/triangle_hw.log.bin)"
