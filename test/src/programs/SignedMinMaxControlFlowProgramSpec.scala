package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class SignedMinMaxControlFlowProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore signed min/max program") {
    it("signed min and max control flow") {
      val pb = new ProgramBuilder()

      pb.emit(iType(5, 0, 0x0, 1, 0x13)) // x1 = 5
      pb.emit(iType(9, 0, 0x0, 2, 0x13)) // x2 = 9
      pb.emit(iType(-3, 0, 0x0, 3, 0x13)) // x3 = -3
      pb.emit(iType(2, 0, 0x0, 4, 0x13)) // x4 = 2
      pb.nops(3)

      pb.emit(bType(0x0c, 2, 1, 0x4, 0x63)) // if x1 < x2 goto p1_b
      pb.emit(iType(0, 1, 0x0, 5, 0x13)) // x5 = x1
      pb.emit(jType(0x08, 0, 0x6f)) // jump p1_done
      pb.emit(iType(0, 2, 0x0, 5, 0x13)) // p1_b: x5 = x2

      pb.emit(bType(0x0c, 4, 3, 0x4, 0x63)) // if x3 < x4 goto p2_b
      pb.emit(iType(0, 3, 0x0, 6, 0x13)) // x6 = x3
      pb.emit(jType(0x08, 0, 0x6f)) // jump p2_done
      pb.emit(iType(0, 4, 0x0, 6, 0x13)) // p2_b: x6 = x4

      val (regs, _) = runProgram(pb.result, maxCycles = 90)
      assert(regs(5) == 9)
      assert(regs(6) == 2)
    }
  }
}
