package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

class CProgramsAnalogSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  describe("RV32ICore C analog programs") {
    it("array sum", CBinary) {
      runBinaryProgram(
        "/programs/c_array_sum.hex",
        outAddr = 0x80,
        expected = 10
      )
    }

    it("memcpy", CBinary) {
      runBinaryProgram(
        "/programs/c_memcpy_smoke.hex",
        outAddr = 0x80,
        expected = 1122
      )
    }

    it("memset", CBinary) {
      runBinaryProgram(
        "/programs/c_memset_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("7A7A7A7A", 16)
      )
    }

    it("counted loop early exit", CBinary) {
      runBinaryProgram(
        "/programs/c_counted_loop_smoke.hex",
        outAddr = 0x80,
        expected = 3
      )
    }

    it("signed min max control flow", CBinary) {
      runBinaryProgram(
        "/programs/c_signed_min_max_smoke.hex",
        outAddr = 0x80,
        expected = 902
      )
    }

    it("software multiply via shift-add", CBinary) {
      runBinaryProgram(
        "/programs/c_shift_add_mul_smoke.hex",
        outAddr = 0x80,
        expected = 18
      )
    }

    it("division remainder via subtraction", CBinary) {
      runBinaryProgram(
        "/programs/c_div_rem_sub_smoke.hex",
        outAddr = 0x80,
        expected = 302
      )
    }

    it("call return", CBinary) {
      runBinaryProgram(
        "/programs/c_call_return_smoke.hex",
        outAddr = 0x80,
        expected = 1
      )
    }

    it("branch torture", CBinary) {
      runBinaryProgram(
        "/programs/c_branch_torture_smoke.hex",
        outAddr = 0x80,
        expected = 6
      )
    }

    it("unsupported opcodes in stream", CBinary) {
      runBinaryProgram(
        "/programs/c_unsupported_opcodes_smoke.hex",
        outAddr = 0x80,
        expected = 8
      )
    }
  }
}
