PREFIX ?= riscv32-linux-gnu
CC := $(PREFIX)-gcc
OBJCOPY := $(PREFIX)-objcopy
OBJDUMP := $(PREFIX)-objdump

BUILD_SCRIPT := ../scripts/build_c_hex.py
LINKER := linker.ld
CRT0 := crt0.S
HEADER := validation.h
FX_HEADER := fx16q16.h
RT_HEADERS := $(wildcard rt/*.h)

OUT_DIR := out

CFLAGS := -march=rv32i -mabi=ilp32 \
-ffreestanding -fno-pic -fno-pie \
-nostdlib -nostartfiles \
-O0

PROGRAMS := abi_smoke gcd_smoke array_sum memcpy_smoke memset_smoke \
counted_loop_smoke signed_min_max_smoke shift_add_mul_smoke \
div_rem_sub_smoke call_return_smoke \
branch_torture_smoke unsupported_opcodes_smoke \
fx_arith_smoke fx_div_smoke fx_dot_product_smoke \
fx_rt_triangle_smoke fx_rt_triangle_render_smoke fx_rt_bvh_smoke fx_rt_cornell_smoke

BINS := $(patsubst %,$(OUT_DIR)/c_%.hex,$(PROGRAMS))
ASMS := $(patsubst %,$(OUT_DIR)/c_%.S,$(PROGRAMS))

# 360p = 640x360 (16:9). Same C source as fx_rt_cornell_smoke, different
# -DRES_*. Staged as c_fx_rt_cornell_360p.hex for optional long ChiselSim runs.
CORNELL_360P_ELF := $(OUT_DIR)/c_fx_rt_cornell_360p.elf
CORNELL_360P_BIN := $(OUT_DIR)/c_fx_rt_cornell_360p.raw.bin
CORNELL_360P_HEX := $(OUT_DIR)/c_fx_rt_cornell_360p.hex

# 720p = 1280x720 (16:9). Same C source; multi-day ChiselSim wall time.
CORNELL_720P_ELF := $(OUT_DIR)/c_fx_rt_cornell_720p.elf
CORNELL_720P_BIN := $(OUT_DIR)/c_fx_rt_cornell_720p.raw.bin
CORNELL_720P_HEX := $(OUT_DIR)/c_fx_rt_cornell_720p.hex

.PHONY: all clean
.SECONDARY:

all: $(BINS) $(ASMS) $(CORNELL_360P_HEX) $(CORNELL_720P_HEX)

$(OUT_DIR):
	mkdir -p $(OUT_DIR)

$(CORNELL_360P_ELF): fx_rt_cornell_smoke.c $(CRT0) $(LINKER) $(HEADER) $(FX_HEADER) $(RT_HEADERS) | $(OUT_DIR)
	$(CC) $(CFLAGS) -DRES_W=640 -DRES_H=360 $(CRT0) $< \
	-Wl,-T,$(LINKER) \
	-Wl,-Map,$(@:.elf=.map) \
	-o $@

$(CORNELL_360P_BIN): $(CORNELL_360P_ELF)
	$(OBJCOPY) -O binary $< $@

$(CORNELL_360P_HEX): $(CORNELL_360P_BIN)
	$(BUILD_SCRIPT) $< $@

$(CORNELL_720P_ELF): fx_rt_cornell_smoke.c $(CRT0) $(LINKER) $(HEADER) $(FX_HEADER) $(RT_HEADERS) | $(OUT_DIR)
	$(CC) $(CFLAGS) -DRES_W=1280 -DRES_H=720 $(CRT0) $< \
	-Wl,-T,$(LINKER) \
	-Wl,-Map,$(@:.elf=.map) \
	-o $@

$(CORNELL_720P_BIN): $(CORNELL_720P_ELF)
	$(OBJCOPY) -O binary $< $@

$(CORNELL_720P_HEX): $(CORNELL_720P_BIN)
	$(BUILD_SCRIPT) $< $@

$(OUT_DIR)/c_%.elf: %.c $(CRT0) $(LINKER) $(HEADER) $(FX_HEADER) $(RT_HEADERS) | $(OUT_DIR)
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
