package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class UnsupportedOpcodesProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore unsupported opcode program") {
    it("unsupported opcodes in stream behave like no-op") {
      val pb = new ProgramBuilder()

      pb.emit(iType(5, 0, 0x0, 1, 0x13)) // x1 = 5
      pb.emit(0x0000000f) // fence (unsupported)
      pb.emit(0x00000073) // ecall/system (unsupported)
      pb.nops(2)
      pb.emit(iType(3, 1, 0x0, 2, 0x13)) // x2 = x1 + 3

      val (regs, _) = runProgram(pb.result, maxCycles = 50)
      assert(regs(1) == 5)
      assert(regs(2) == 8)
    }
  }
}
