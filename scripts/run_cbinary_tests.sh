#!/usr/bin/env bash
set -euo pipefail
echo "run_cbinary_tests.sh is deprecated; use run_validation_tests.sh" >&2
exec "$(dirname "${BASH_SOURCE[0]}")/run_validation_tests.sh" "$@"
