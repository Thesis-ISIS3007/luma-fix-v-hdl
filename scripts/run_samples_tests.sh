#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

uarch_dir="${repo_root}/test/uarch-out/samples"
mkdir -p "${uarch_dir}"

echo "==> Running Samples-tagged tests (requires SW hex under test/resources/samples)"
echo "    Uarch JSON: ${uarch_dir}/"
export LUMAFIXV_SAMPLES=1
export LUMAFIXV_UARCH_STATS="${uarch_dir}/"
./mill test "$@"
