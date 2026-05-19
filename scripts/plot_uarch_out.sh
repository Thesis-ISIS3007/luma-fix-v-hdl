#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

usage() {
  cat <<'EOF'
Usage: scripts/plot_uarch_out.sh [samples|validation|all]

Plot stall and CUSTOM-0 PNGs for each *.json under test/uarch-out/.
Default target: samples.
EOF
}

target="${1:-samples}"

case "${target}" in
samples | validation | all) ;;
-h | --help)
  usage
  exit 0
  ;;
*)
  echo "error: unknown target: ${target}" >&2
  usage
  exit 1
  ;;
esac

plot_uarch_json() {
  local json="$1"
  local png="${json%.json}.png"
  if command -v uv >/dev/null 2>&1; then
    uv run "${repo_root}/scripts/plot_uarch_stats.py" "${json}" -o "${png}"
  else
    python3.14 "${repo_root}/scripts/plot_uarch_stats.py" "${json}" -o "${png}"
  fi
}

plot_dir() {
  local uarch_dir="$1"
  local label="$2"
  local count=0

  mkdir -p "${uarch_dir}"
  shopt -s nullglob
  for json in "${uarch_dir}"/*.json; do
    plot_uarch_json "${json}"
    count=$((count + 1))
  done
  shopt -u nullglob

  if [[ "${count}" -eq 0 ]]; then
    echo "No uarch JSON in ${uarch_dir}/ (nothing to plot)"
    return 0
  fi
  echo "Plotted ${count} ${label} program(s) under ${uarch_dir}/"
}

plot_samples() {
  plot_dir "${repo_root}/test/uarch-out/samples" "sample"
}

plot_validation() {
  plot_dir "${repo_root}/test/uarch-out/validation" "validation"
}

case "${target}" in
samples)
  plot_samples
  ;;
validation)
  plot_validation
  ;;
all)
  plot_samples
  plot_validation
  ;;
esac
