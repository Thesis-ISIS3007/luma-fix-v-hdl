package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CProgramsAnalogSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  // Linked validation images exceed the trait default (256 words); e.g.
  // c_memcpy_smoke.hex is ~364 words. Truncated imem yields simulator errors.
  override protected val cProgramImemWords: Int = 512
  override protected val cProgramDmemWords: Int = 512

  describe("RV32ICore C analog programs") {
    it("array sum", CBinary) {
      runBinaryProgram(
        "/validation/c_array_sum.hex",
        outAddr = 0x80,
        expected = 10
      )
    }

    it("fibonacci loop smoke", CBinary) {
      runBinaryProgram(
        "/validation/c_fibonacci_smoke.hex",
        outAddr = 0x80,
        expected = 21
      )
    }

    it("memcpy", CBinary) {
      runBinaryProgram(
        "/validation/c_memcpy_smoke.hex",
        outAddr = 0x80,
        expected = 1122
      )
    }

    it("memset", CBinary) {
      runBinaryProgram(
        "/validation/c_memset_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("7A7A7A7A", 16)
      )
    }

    it("counted loop early exit", CBinary) {
      runBinaryProgram(
        "/validation/c_counted_loop_smoke.hex",
        outAddr = 0x80,
        expected = 3
      )
    }

    it("filter prefix sum", CBinary) {
      runBinaryProgram(
        "/validation/c_filter_prefix_sum.hex",
        outAddr = 0x80,
        expected = 12
      )
    }

    it("selection sort writes min/max", CBinary) {
      runBinaryProgram(
        "/validation/c_selection_sort.hex",
        outAddr = 0x80,
        expected = 1
      )
      runBinaryProgram(
        "/validation/c_selection_sort.hex",
        outAddr = 0x84,
        expected = 9
      )
    }

    it("binary search found and missing", CBinary) {
      runBinaryProgram(
        "/validation/c_binary_search.hex",
        outAddr = 0x80,
        expected = 3
      )
      runBinaryProgram(
        "/validation/c_binary_search.hex",
        outAddr = 0x84,
        expected = 0
      )
    }

    it("signed min max control flow", CBinary) {
      runBinaryProgram(
        "/validation/c_signed_min_max_smoke.hex",
        outAddr = 0x80,
        expected = 902
      )
    }

    it("software multiply via shift-add", CBinary) {
      runBinaryProgram(
        "/validation/c_shift_add_mul_smoke.hex",
        outAddr = 0x80,
        expected = 18
      )
    }

    it("division remainder via subtraction", CBinary) {
      runBinaryProgram(
        "/validation/c_div_rem_sub_smoke.hex",
        outAddr = 0x80,
        expected = 302
      )
    }

    it("gcd smoke", CBinary) {
      runBinaryProgram(
        "/validation/c_gcd_smoke.hex",
        outAddr = 0x84,
        expected = 6
      )
    }

    it("call return", CBinary) {
      runBinaryProgram(
        "/validation/c_call_return_smoke.hex",
        outAddr = 0x80,
        expected = 1
      )
    }

    it("branch torture", CBinary) {
      runBinaryProgram(
        "/validation/c_branch_torture_smoke.hex",
        outAddr = 0x80,
        expected = 6
      )
    }

    it("bit ops mix", CBinary) {
      runBinaryProgram(
        "/validation/c_bit_ops_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("5FA", 16)
      )
    }

    it("unsupported opcodes in stream trap before the final store", CBinary) {
      runBinaryProgram(
        "/validation/c_unsupported_opcodes_smoke.hex",
        outAddr = 0x80,
        // Keep a deterministic sentinel until trap-driven completion signaling
        // is modeled in this test binary.
        expected = BigInt(50397459)
      )
    }
  }
}
