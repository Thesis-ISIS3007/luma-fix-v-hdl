package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class GCDSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32ICore gcd") {
    it("computes gcd(48, 18)") {
      val pb = new ProgramBuilder()

      // Simple control-flow Euclid by subtraction:
      // while (x2 != x3) {
      //   if (x2 < x3) x3 = x3 - x2 else x2 = x2 - x3
      // }
      // gcd is left in x2 (and x3).
      pb.emit(iType(48, 0, 0x0, 2, 0x13))
      pb.emit(iType(18, 0, 0x0, 3, 0x13))
      pb.nops(3)

      pb.emit(bType(0x28, 3, 2, 0x0, 0x63)) // loop: beq x2, x3, done
      pb.emit(bType(0x14, 3, 2, 0x4, 0x63)) // blt x2, x3, less
      pb.emit(rType(0x20, 3, 2, 0x0, 2, 0x33)) // x2 = x2 - x3
      pb.nops(2)
      pb.emit(jType(-0x14, 0, 0x6f)) // jal x0, loop

      pb.emit(rType(0x20, 2, 3, 0x0, 3, 0x33)) // less: x3 = x3 - x2
      pb.nops(2)
      pb.emit(jType(-0x24, 0, 0x6f)) // jal x0, loop

      pb.emit(iType(0, 2, 0x0, 8, 0x13)) // done: x8 = gcd

      val (regs, _) = runProgram(pb.result, maxCycles = 72)

      assert(regs(2) == 6)
      assert(regs(3) == 6)
      assert(regs(8) == 6)
    }
  }
}
