package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CountedLoopEarlyExitProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore counted loop program") {
    it("counted loop with early exit") {
      val pb = new ProgramBuilder()

      pb.emit(iType(10, 0, 0x0, 1, 0x13)) // x1 = loop counter
      pb.emit(iType(0, 0, 0x0, 2, 0x13)) // x2 = iteration count
      pb.emit(iType(3, 0, 0x0, 4, 0x13)) // x4 = early-exit threshold
      pb.nops(3)

      pb.emit(bType(0x18, 0, 1, 0x0, 0x63)) // loop: beq x1, x0, done
      pb.emit(iType(1, 2, 0x0, 2, 0x13)) // x2++
      pb.emit(iType(-1, 1, 0x0, 1, 0x13)) // x1--
      pb.emit(bType(0x08, 4, 2, 0x0, 0x63)) // beq x2, x4, done
      pb.emit(jType(-0x10, 0, 0x6f)) // jump loop

      pb.emit(iType(0, 2, 0x0, 5, 0x13)) // done: x5 = x2

      val (regs, _) = runProgram(pb.result, maxCycles = 80)
      assert(regs(2) == 3)
      assert(regs(1) == 7)
      assert(regs(5) == 3)
    }
  }
}
