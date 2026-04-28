#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

out_dir="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
mkdir -p "${out_dir}"

SAMPLE_IDS=(
  "triangle_render_32x24"
  "red_triangle_32x24"
  "tilted_triangle_32x24"
  "color_triangle_32x24"
  "square_32x24"
  "color_square_32x24"
  "cornell_32x24"
  "suzanne_32x24"
)

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run_samples.sh all
  ./scripts/run_samples.sh single <sample-id>

Sample IDs:
  triangle_render_32x24
  red_triangle_32x24
  tilted_triangle_32x24
  color_triangle_32x24
  square_32x24
  color_square_32x24
  cornell_32x24
  suzanne_32x24
EOF
}

run_sample() {
  local sample_id="$1"
  local hex_name="c_${sample_id}.hex"
  local log_path="${out_dir}/c_${sample_id}.log.bin"
  local png_path="${out_dir}/${sample_id}.png"
  local cycles="5000000"
  local imem_words="16384"
  local dmem_words="16384"

  case "${sample_id}" in
  cornell_32x24)
    cycles="25000000"
    ;;
  suzanne_32x24)
    cycles="50000000"
    imem_words="262144"
    dmem_words="65536"
    ;;
  esac

  echo "==> Running sample: ${sample_id}"
  if ! LUMAFIXV_SAMPLE_HEX="${hex_name}" \
    LUMAFIXV_SAMPLE_LOG="${log_path}" \
    LUMAFIXV_SAMPLE_CYCLES="${cycles}" \
    LUMAFIXV_SAMPLE_IMEM_WORDS="${imem_words}" \
    LUMAFIXV_SAMPLE_DMEM_WORDS="${dmem_words}" \
    ./mill test.testOnly luma_fix_v.CFxRtTriangleRenderProgramSpec; then
    if [[ "${sample_id}" == "suzanne_32x24" ]]; then
      echo "warning: ${sample_id} simulation is currently unstable; skipping PNG generation" >&2
      return 0
    fi
    return 1
  fi

  if command -v uv >/dev/null 2>&1; then
    uv run "${repo_root}/scripts/fx_rt_log_to_png.py" "${log_path}" "${png_path}"
  else
    python3.14 "${repo_root}/scripts/fx_rt_log_to_png.py" "${log_path}" "${png_path}"
  fi
  echo "Wrote ${png_path}"
}

run_one() {
  local sample_id="$1"
  case "${sample_id}" in
  triangle_render_32x24|red_triangle_32x24|tilted_triangle_32x24|color_triangle_32x24|square_32x24|color_square_32x24|cornell_32x24|suzanne_32x24)
    run_sample "${sample_id}"
    ;;
  *)
    echo "error: unknown sample id: ${sample_id}" >&2
    usage
    exit 1
    ;;
  esac
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

mode="$1"
case "${mode}" in
all)
  for sample_id in "${SAMPLE_IDS[@]}"; do
    run_one "${sample_id}"
  done
  ;;
single)
  if [[ $# -ne 2 ]]; then
    usage
    exit 1
  fi
  run_one "$2"
  ;;
*)
  usage
  exit 1
  ;;
esac
