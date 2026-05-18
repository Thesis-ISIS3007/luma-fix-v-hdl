package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class BranchTortureProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore branch torture program") {
    it("branch torture over edge values") {
      val pb = new ProgramBuilder()

      pb.emit(iType(-1, 0, 0x0, 1, 0x13)) // x1 = -1
      pb.emit(iType(1, 0, 0x0, 2, 0x13)) // x2 = 1
      pb.emit(iType(0, 0, 0x0, 10, 0x13)) // x10 = score
      pb.nops(3)

      pb.emit(bType(0x08, 1, 1, 0x0, 0x63)) // beq taken
      pb.emit(iType(100, 10, 0x0, 10, 0x13))
      pb.emit(iType(1, 10, 0x0, 10, 0x13))

      pb.emit(bType(0x08, 2, 1, 0x1, 0x63)) // bne taken
      pb.emit(iType(100, 10, 0x0, 10, 0x13))
      pb.emit(iType(1, 10, 0x0, 10, 0x13))

      pb.emit(bType(0x08, 2, 1, 0x4, 0x63)) // blt (-1 < 1) taken
      pb.emit(iType(100, 10, 0x0, 10, 0x13))
      pb.emit(iType(1, 10, 0x0, 10, 0x13))

      pb.emit(bType(0x08, 1, 2, 0x5, 0x63)) // bge (1 >= -1) taken
      pb.emit(iType(100, 10, 0x0, 10, 0x13))
      pb.emit(iType(1, 10, 0x0, 10, 0x13))

      pb.emit(bType(0x08, 2, 1, 0x6, 0x63)) // bltu (0xFFFFFFFF > 1) not taken
      pb.emit(iType(1, 10, 0x0, 10, 0x13))
      pb.emit(iType(0, 0, 0x0, 0, 0x13))

      pb.emit(bType(0x08, 2, 1, 0x7, 0x63)) // bgeu (0xFFFFFFFF >= 1) taken
      pb.emit(iType(100, 10, 0x0, 10, 0x13))
      pb.emit(iType(1, 10, 0x0, 10, 0x13))

      val (regs, _) = runProgram(pb.result, maxCycles = 120)
      assert(regs(10) == 6)
    }
  }
}
