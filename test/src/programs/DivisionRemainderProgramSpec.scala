package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class DivisionRemainderProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore division remainder program") {
    it("division and remainder by repeated subtraction") {
      val pb = new ProgramBuilder()

      pb.emit(iType(20, 0, 0x0, 1, 0x13)) // x1 = dividend
      pb.emit(iType(6, 0, 0x0, 2, 0x13)) // x2 = divisor
      pb.emit(iType(0, 0, 0x0, 3, 0x13)) // x3 = quotient
      pb.nops(3)

      pb.emit(bType(0x10, 2, 1, 0x4, 0x63)) // loop: blt x1, x2, done
      pb.emit(rType(0x20, 2, 1, 0x0, 1, 0x33)) // x1 -= x2
      pb.emit(iType(1, 3, 0x0, 3, 0x13)) // x3++
      pb.emit(jType(-0x0c, 0, 0x6f)) // jump loop

      pb.emit(iType(0, 1, 0x0, 4, 0x13)) // done: x4 = remainder

      val (regs, _) = runProgram(pb.result, maxCycles = 100)
      assert(regs(3) == 3)
      assert(regs(4) == 2)
    }
  }
}
