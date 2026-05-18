#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

out_dir="${LUMAFIXV_OUT_DIR:-${repo_root}/scripts/out}"
mkdir -p "${out_dir}"

SAMPLE_IDS=(
  "triangle_render_30x20"
  "tilted_triangle_30x20"
  "color_triangle_30x20"
  "square_30x20"
  "color_square_30x20"
  "cornell_30x20"
  "cornell_bvh_embed_30x20"
  "suzanne_30x20"
)

usage() {
  echo "Usage:"
  echo "  ./scripts/run_samples.sh all"
  echo "  ./scripts/run_samples.sh single <sample-id>"
  echo
  echo "Sample IDs:"
  local sample_id
  for sample_id in "${SAMPLE_IDS[@]}"; do
    echo "  ${sample_id}"
  done
}

run_sample() {
  local sample_id="$1"
  local hex_name="c_${sample_id}.hex"
  local log_path="${out_dir}/c_${sample_id}.log.bin"
  local png_path="${out_dir}/${sample_id}.png"
  local imem_words="16384"
  local dmem_words="16384"

  case "${sample_id}" in
  suzanne_30x20)
    # Hex is ~74k words; dmem must cover the full flat image (same file as imem).
    imem_words="131072"
    dmem_words="131072"
    ;;
  esac

  echo "==> Running sample: ${sample_id}"
  if ! LUMAFIXV_SAMPLES=1 \
    LUMAFIXV_SAMPLE_HEX="${hex_name}" \
    LUMAFIXV_SAMPLE_LOG="${log_path}" \
    LUMAFIXV_SAMPLE_IMEM_WORDS="${imem_words}" \
    LUMAFIXV_SAMPLE_DMEM_WORDS="${dmem_words}" \
    ./mill test.testOnly luma_fix_v.CFxRtTriangleRenderProgramSpec; then
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
  local known
  for known in "${SAMPLE_IDS[@]}"; do
    if [[ "${sample_id}" == "${known}" ]]; then
      run_sample "${sample_id}"
      return
    fi
  done
  echo "error: unknown sample id: ${sample_id}" >&2
  usage
  exit 1
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
