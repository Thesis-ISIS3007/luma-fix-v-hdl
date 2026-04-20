#!/usr/bin/env bash
set -euo pipefail

PREFIX="${PREFIX:-riscv32-linux-gnu}"
if command -v "${PREFIX}-gcc" >/dev/null 2>&1; then
  CC="${CC:-${PREFIX}-gcc}"
else
  CC="${CC:-${PREFIX}-g++}"
fi
OBJCOPY="${OBJCOPY:-${PREFIX}-objcopy}"

SRC_C="${1:-abi_smoke.c}"
SRC_S="${2:-crt0.S}"
LDSCRIPT="${3:-linker.ld}"
OUT_BIN="${4:-../resources/programs/c_abi_smoke.bin}"

ELF="c_abi_smoke.elf"
BIN="c_abi_smoke.bin"

command -v "${CC}" >/dev/null
command -v "${OBJCOPY}" >/dev/null

"${CC}" \
  -march=rv32i -mabi=ilp32 \
  -ffreestanding -fno-pic -fno-pie \
  -nostdlib -nostartfiles \
  -O2 \
  "${SRC_S}" "${SRC_C}" \
  -Wl,-T,"${LDSCRIPT}" \
  -Wl,-Map,c_abi_smoke.map \
  -o "${ELF}"

"${OBJCOPY}" -O binary "${ELF}" "${BIN}"

od -An -tx4 -v -w4 "${BIN}" | sed 's/^[[:space:]]*//' | awk '
function nibble_to_bin(c) {
  if (c == "0") return "0000"
  if (c == "1") return "0001"
  if (c == "2") return "0010"
  if (c == "3") return "0011"
  if (c == "4") return "0100"
  if (c == "5") return "0101"
  if (c == "6") return "0110"
  if (c == "7") return "0111"
  if (c == "8") return "1000"
  if (c == "9") return "1001"
  if (c == "a") return "1010"
  if (c == "b") return "1011"
  if (c == "c") return "1100"
  if (c == "d") return "1101"
  if (c == "e") return "1110"
  if (c == "f") return "1111"
  return "0000"
}
{
  line = tolower($0)
  out = ""
  for (i = 1; i <= length(line); i++) {
    out = out nibble_to_bin(substr(line, i, 1))
  }
  print out
}
' > "${OUT_BIN}"

echo "Generated ${OUT_BIN}"
