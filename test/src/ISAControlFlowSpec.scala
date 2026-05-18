package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISAControlFlowSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32I control flow execution") {
    it("executes JAL and JALR") {
      val jalPb = new ProgramBuilder()
      jalPb.emit(jType(0x008, 1, 0x6f))
      jalPb.emit(iType(1, 0, 0x0, 2, 0x13))
      jalPb.emit(iType(2, 0, 0x0, 3, 0x13))

      val (jalRegs, _) = runProgram(jalPb.result, maxCycles = 16)
      assert(jalRegs(1) == 4)
      assert(jalRegs(2) == 0)
      assert(jalRegs(3) == 2)

      val jalrPb = new ProgramBuilder()
      jalrPb.emit(iType(5, 0, 0x0, 1, 0x13))
      jalrPb.emit(iType(2, 1, 0x0, 2, 0x67))
      jalrPb.setPc(0x06).emit(iType(7, 0, 0x0, 3, 0x13))
      jalrPb.emit(iType(9, 0, 0x0, 4, 0x13))

      val (jalrRegs, _) = runProgram(jalrPb.result, maxCycles = 16)
      assert(jalrRegs(1) == 5)
      assert(jalrRegs(2) == 8)
      assert(jalrRegs(3) == 7)
      assert(jalrRegs(4) == 9)
    }

    it("executes all branch instructions") {
      val branchCases = Seq(
        (0x0, 7, 7),
        (0x1, 7, 8),
        (0x4, -1, 1),
        (0x5, 2, -3),
        (0x6, 1, 2),
        (0x7, 2, 1)
      )

      for ((funct3, left, right) <- branchCases) {
        val pb = new ProgramBuilder()
        pb.emit(iType(left, 0, 0x0, 1, 0x13))
        pb.emit(iType(right, 0, 0x0, 2, 0x13))
        pb.emit(bType(0x008, 2, 1, funct3, 0x63))
        pb.emit(iType(1, 0, 0x0, 3, 0x13))
        pb.emit(iType(2, 0, 0x0, 4, 0x13))

        val (regs, _) = runProgram(pb.result, maxCycles = 24)
        assert(regs(4) == 2, s"branch funct3=$funct3 did not reach target")
        assert(regs(3) == 0, s"branch funct3=$funct3 should skip fallthrough")
      }
    }
  }
}
