PREFIX ?= riscv32-linux-gnu
CC := $(PREFIX)-gcc
OBJCOPY := $(PREFIX)-objcopy
OBJDUMP := $(PREFIX)-objdump

BUILD_SCRIPT := ../scripts/build_c_hex.py
LINKER := linker.ld
CRT0 := crt0.S
HEADER := validation.h

OUT_DIR := out

CFLAGS := -march=rv32i -mabi=ilp32 \
-ffreestanding -fno-pic -fno-pie \
-nostdlib -nostartfiles \
-O0

PROGRAMS := abi_smoke gcd_smoke array_sum memcpy_smoke memset_smoke \
counted_loop_smoke signed_min_max_smoke shift_add_mul_smoke \
div_rem_sub_smoke call_return_smoke \
branch_torture_smoke unsupported_opcodes_smoke

BINS := $(patsubst %,$(OUT_DIR)/c_%.hex,$(PROGRAMS))
ASMS := $(patsubst %,$(OUT_DIR)/c_%.S,$(PROGRAMS))

.PHONY: all clean
.SECONDARY:

all: $(BINS) $(ASMS)

$(OUT_DIR):
	mkdir -p $(OUT_DIR)

$(OUT_DIR)/c_%.elf: %.c $(CRT0) $(LINKER) $(HEADER) | $(OUT_DIR)
	$(CC) $(CFLAGS) $(CRT0) $< \
	-Wl,-T,$(LINKER) \
	-Wl,-Map,$(@:.elf=.map) \
	-o $@

$(OUT_DIR)/%.raw.bin: $(OUT_DIR)/%.elf
	$(OBJCOPY) -O binary $< $@

$(OUT_DIR)/%.hex: $(OUT_DIR)/%.raw.bin
	$(BUILD_SCRIPT) $< $@

$(OUT_DIR)/%.S: $(OUT_DIR)/%.elf
	$(OBJDUMP) -d -M no-aliases,numeric -S $< > $@

clean:
	rm -rf $(OUT_DIR)
