package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

class CProgramsAnalogSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  describe("RV32ICore C analog programs") {
    it("array sum") {
      runBinaryProgram(
        "/programs/c_array_sum.bin",
        outAddr = 0x80,
        expected = 10
      )
    }

    it("memcpy") {
      runBinaryProgram(
        "/programs/c_memcpy_smoke.bin",
        outAddr = 0x80,
        expected = 1122
      )
    }

    it("memset") {
      runBinaryProgram(
        "/programs/c_memset_smoke.bin",
        outAddr = 0x80,
        expected = BigInt("7A7A7A7A", 16)
      )
    }

    it("counted loop early exit") {
      runBinaryProgram(
        "/programs/c_counted_loop_smoke.bin",
        outAddr = 0x80,
        expected = 3
      )
    }

    it("signed min max control flow") {
      runBinaryProgram(
        "/programs/c_signed_min_max_smoke.bin",
        outAddr = 0x80,
        expected = 902
      )
    }

    it("software multiply via shift-add") {
      runBinaryProgram(
        "/programs/c_shift_add_mul_smoke.bin",
        outAddr = 0x80,
        expected = 18
      )
    }

    it("division remainder via subtraction") {
      runBinaryProgram(
        "/programs/c_div_rem_sub_smoke.bin",
        outAddr = 0x80,
        expected = 302
      )
    }

    it("dot kernel with shifts") {
      runBinaryProgram(
        "/programs/c_dot_kernel_smoke.bin",
        outAddr = 0x80,
        expected = 21
      )
    }

    it("call return") {
      runBinaryProgram(
        "/programs/c_call_return_smoke.bin",
        outAddr = 0x80,
        expected = 1
      )
    }

    it("branch torture") {
      runBinaryProgram(
        "/programs/c_branch_torture_smoke.bin",
        outAddr = 0x80,
        expected = 6
      )
    }

    it("unsupported opcodes in stream") {
      runBinaryProgram(
        "/programs/c_unsupported_opcodes_smoke.bin",
        outAddr = 0x80,
        expected = 8
      )
    }
  }
}
