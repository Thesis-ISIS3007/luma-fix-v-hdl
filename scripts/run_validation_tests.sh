#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

uarch_dir="${repo_root}/test/uarch-out/validation"
mkdir -p "${uarch_dir}"

echo "==> Running Validation-tagged tests (requires SW hex under test/resources/validation)"
echo "    Uarch JSON: ${uarch_dir}/"
export LUMAFIXV_VALIDATION=1
export LUMAFIXV_UARCH_STATS="${uarch_dir}/"
./mill test.testOnly -- -n Validation "$@"

echo "==> Plotting uarch stats"
./scripts/plot_uarch_out.sh validation
