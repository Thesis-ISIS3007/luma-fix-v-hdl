package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CProgramsAnalogSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  describe("RV32ICore C analog programs") {
    it("array sum", CBinary) {
      runBinaryProgram(
        "/validation/c_array_sum.hex",
        outAddr = 0x80,
        expected = 10
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

    it("unsupported opcodes in stream trap before the final store", CBinary) {
      runBinaryProgram(
        "/validation/c_unsupported_opcodes_smoke.hex",
        outAddr = 0x80,
        // The injected .word 0x00000000 now triggers illegal-instruction trap,
        // so the result store is never reached and dmem[0x80] remains untouched.
        expected = BigInt(50397459)
      )
    }
  }
}
