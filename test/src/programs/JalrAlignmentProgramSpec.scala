package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class JalrAlignmentProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore JALR alignment program") {
    it("JALR target alignment masks low bit") {
      val pb = new ProgramBuilder()

      pb.emit(iType(0x15, 0, 0x0, 1, 0x13)) // x1 = odd target
      pb.emit(iType(0, 1, 0x0, 2, 0x67)) // jalr x2, x1, 0 -> 0x14
      pb.emit(iType(1, 0, 0x0, 3, 0x13)) // should be skipped

      pb.setPc(0x14)
      pb.emit(iType(2, 0, 0x0, 4, 0x13)) // aligned target

      val (regs, _) = runProgram(pb.result, maxCycles = 60)
      assert(regs(2) == 8)
      assert(regs(3) == 0)
      assert(regs(4) == 2)
    }
  }
}
