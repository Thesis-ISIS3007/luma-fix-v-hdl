#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

uarch_dir="${repo_root}/test/uarch-out/validation"
mkdir -p "${uarch_dir}"

plot_uarch_json() {
  local json="$1"
  local png="${json%.json}.png"
  if command -v uv >/dev/null 2>&1; then
    uv run "${repo_root}/scripts/plot_uarch_stats.py" "${json}" -o "${png}"
  else
    python3.14 "${repo_root}/scripts/plot_uarch_stats.py" "${json}" -o "${png}"
  fi
}

plot_all_uarch() {
  local count=0
  shopt -s nullglob
  for json in "${uarch_dir}"/*.json; do
    plot_uarch_json "${json}"
    count=$((count + 1))
  done
  shopt -u nullglob
  if [[ "${count}" -eq 0 ]]; then
    echo "No uarch JSON in ${uarch_dir}/ (nothing to plot)" >&2
    return 1
  fi
  echo "Plotted ${count} validation program(s) under ${uarch_dir}/"
}

echo "==> Running Validation-tagged tests (requires SW hex under test/resources/validation)"
echo "    Uarch JSON: ${uarch_dir}/"
export LUMAFIXV_VALIDATION=1
export LUMAFIXV_UARCH_STATS="${uarch_dir}/"
./mill test.testOnly -- -n Validation "$@"

echo "==> Plotting uarch stats"
plot_all_uarch
