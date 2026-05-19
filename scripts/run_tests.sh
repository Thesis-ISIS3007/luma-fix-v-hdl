#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

usage() {
  cat <<EOF
Usage: $0 [options] [-- mill-test-args...]

Runs the Mill test suite. Validation and sample hex specs are opt-in.

Options:
  --with-validation   Include Validation-tagged specs (needs test/resources/validation).
  --with-samples      Include Samples-tagged specs (needs test/resources/samples).
  -h, --help          Show this help.

Environment:
  LUMAFIXV_VALIDATION=1   Same as --with-validation
  LUMAFIXV_SAMPLES=1      Same as --with-samples

Examples:
  $0
  $0 --with-validation
  LUMAFIXV_SAMPLES=1 $0
  $0 --with-validation --with-samples
EOF
}

mill_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
  --with-validation)
    export LUMAFIXV_VALIDATION=1
    shift
    ;;
  --with-samples)
    export LUMAFIXV_SAMPLES=1
    shift
    ;;
  -h | --help)
    usage
    exit 0
    ;;
  --)
    shift
    mill_args+=("$@")
    break
    ;;
  *)
    mill_args+=("$1")
    shift
    ;;
  esac
done

validation_on=0
samples_on=0
[[ "${LUMAFIXV_VALIDATION:-}" == "1" || "${LUMAFIXV_VALIDATION:-}" == "true" || "${LUMAFIXV_VALIDATION:-}" == "TRUE" ]] && validation_on=1
[[ "${LUMAFIXV_SAMPLES:-}" == "1" || "${LUMAFIXV_SAMPLES:-}" == "true" || "${LUMAFIXV_SAMPLES:-}" == "TRUE" ]] && samples_on=1

echo "==> Running mill test suite"
if [[ "${validation_on}" -eq 0 ]]; then
  echo "    Validation hex specs: off (LUMAFIXV_VALIDATION=1 or --with-validation)"
else
  echo "    Validation hex specs: on"
fi
if [[ "${samples_on}" -eq 0 ]]; then
  echo "    Sample hex specs: off (LUMAFIXV_SAMPLES=1 or --with-samples)"
else
  echo "    Sample hex specs: on"
fi

mill_cmd=(./mill)
if [[ "${validation_on}" -eq 1 ]]; then
  mill_cmd+=(--define luma.validation=1)
fi
if [[ "${samples_on}" -eq 1 ]]; then
  mill_cmd+=(--define luma.samples=1)
fi

"${mill_cmd[@]}" test "${mill_args[@]}"
