#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

sentinel="${repo_root}/.run-cbinary"
touch "${sentinel}"
trap 'rm -f "${sentinel}"' EXIT

echo "==> Running full mill test suite (including CBinary-tagged specs)"
./mill test "$@"
