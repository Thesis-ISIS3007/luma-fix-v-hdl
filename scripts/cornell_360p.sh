#!/usr/bin/env bash
# Build the 640x360 Cornell render hex and stage it for ChiselSim / tests.
# Full simulation is not part of default CI. For a full e2e (sim + PPM) use
#   ./scripts/cornell_e2e_360p.sh
# (requires LUMAFIXV_CORNELL_360P=1 inside that script; many hours of wall time.)
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"
make -C validation out/c_fx_rt_cornell_360p.hex
mkdir -p test/resources/programs
cp validation/out/c_fx_rt_cornell_360p.hex test/resources/programs/
echo "Staged test/resources/programs/c_fx_rt_cornell_360p.hex"
echo "E2E (long):  ./scripts/cornell_e2e_360p.sh"
echo "Suite only:  (no Mill program spec for 640x360 Cornell in this repo)"
