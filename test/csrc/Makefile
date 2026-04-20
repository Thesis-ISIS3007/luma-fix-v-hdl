PREFIX ?= riscv32-linux-gnu

BUILD_SCRIPT := ./build_c_hex.sh
LINKER := linker.ld
CRT0 := crt0.S

OUT_DIR := ../resources/programs

.PHONY: all abi gcd analogs array_sum memcpy_smoke memset_smoke counted_loop_smoke signed_min_max_smoke shift_add_mul_smoke div_rem_sub_smoke dot_kernel_smoke call_return_smoke branch_torture_smoke unsupported_opcodes_smoke clean

all: abi gcd analogs

analogs: array_sum memcpy_smoke memset_smoke counted_loop_smoke signed_min_max_smoke shift_add_mul_smoke div_rem_sub_smoke dot_kernel_smoke call_return_smoke branch_torture_smoke unsupported_opcodes_smoke

abi:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) abi_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_abi_smoke.bin

gcd:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) gcd_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_gcd_smoke.bin

array_sum:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) array_sum.c $(CRT0) $(LINKER) $(OUT_DIR)/c_array_sum.bin

memcpy_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) memcpy_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_memcpy_smoke.bin

memset_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) memset_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_memset_smoke.bin

counted_loop_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) counted_loop_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_counted_loop_smoke.bin

signed_min_max_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) signed_min_max_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_signed_min_max_smoke.bin

shift_add_mul_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) shift_add_mul_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_shift_add_mul_smoke.bin

div_rem_sub_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) div_rem_sub_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_div_rem_sub_smoke.bin

dot_kernel_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) dot_kernel_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_dot_kernel_smoke.bin

call_return_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) call_return_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_call_return_smoke.bin

branch_torture_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) branch_torture_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_branch_torture_smoke.bin

unsupported_opcodes_smoke:
	PREFIX=$(PREFIX) $(BUILD_SCRIPT) unsupported_opcodes_smoke.c $(CRT0) $(LINKER) $(OUT_DIR)/c_unsupported_opcodes_smoke.bin

clean:
	rm -f c_abi_smoke.elf c_abi_smoke.bin c_abi_smoke.map
	rm -f $(OUT_DIR)/c_abi_smoke.bin $(OUT_DIR)/c_gcd_smoke.bin
	rm -f $(OUT_DIR)/c_array_sum.bin
	rm -f $(OUT_DIR)/c_memcpy_smoke.bin
	rm -f $(OUT_DIR)/c_memset_smoke.bin
	rm -f $(OUT_DIR)/c_counted_loop_smoke.bin
	rm -f $(OUT_DIR)/c_signed_min_max_smoke.bin
	rm -f $(OUT_DIR)/c_shift_add_mul_smoke.bin
	rm -f $(OUT_DIR)/c_div_rem_sub_smoke.bin
	rm -f $(OUT_DIR)/c_dot_kernel_smoke.bin
	rm -f $(OUT_DIR)/c_call_return_smoke.bin
	rm -f $(OUT_DIR)/c_branch_torture_smoke.bin
	rm -f $(OUT_DIR)/c_unsupported_opcodes_smoke.bin
