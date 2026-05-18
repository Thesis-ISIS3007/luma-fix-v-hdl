#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

echo "==> Running Validation-tagged tests (requires SW hex under test/resources/validation)"
export LUMAFIXV_VALIDATION=1
./mill test "$@"
