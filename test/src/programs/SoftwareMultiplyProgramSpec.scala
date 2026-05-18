package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class SoftwareMultiplyProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore software multiply program") {
    it("software multiply by shift-add") {
      val pb = new ProgramBuilder()

      pb.emit(iType(3, 0, 0x0, 1, 0x13)) // x1 = multiplicand
      pb.emit(iType(6, 0, 0x0, 2, 0x13)) // x2 = multiplier
      pb.emit(iType(0, 0, 0x0, 3, 0x13)) // x3 = acc
      pb.nops(4)

      pb.emit(bType(0x1c, 0, 2, 0x0, 0x63)) // loop: beq x2, x0, done
      pb.emit(iType(1, 2, 0x7, 5, 0x13)) // andi x5, x2, 1
      pb.emit(bType(0x08, 0, 5, 0x0, 0x63)) // if bit==0 skip add
      pb.emit(rType(0x00, 1, 3, 0x0, 3, 0x33)) // x3 += x1
      pb.emit(iType(1, 1, 0x1, 1, 0x13)) // slli x1, x1, 1
      pb.emit(iType(1, 2, 0x5, 2, 0x13)) // srli x2, x2, 1
      pb.emit(jType(-0x18, 0, 0x6f)) // jump loop

      pb.emit(iType(0, 3, 0x0, 6, 0x13)) // done: x6 = product

      val (regs, _) = runProgram(pb.result, maxCycles = 120)
      assert(regs(6) == 18)
    }
  }
}
