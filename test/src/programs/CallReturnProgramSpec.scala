package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CallReturnProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore call/return program") {
    it("call and return with JAL/JALR") {
      val pb = new ProgramBuilder()

      pb.emit(jType(0x0c, 1, 0x6f)) // jal x1, func
      pb.emit(iType(1, 0, 0x0, 3, 0x13)) // addi x3, x0, 1 (after return)
      pb.emit(jType(0x10, 0, 0x6f)) // jump end

      pb.emit(iType(7, 0, 0x0, 2, 0x13)) // func: x2 = 7
      pb.emit(iType(0, 1, 0x0, 0, 0x67)) // jalr x0, x1, 0
      pb.emit(iType(99, 0, 0x0, 3, 0x13)) // should be skipped

      pb.emit(iType(2, 0, 0x0, 4, 0x13)) // end: x4 = 2

      val (regs, _) = runProgram(pb.result, maxCycles = 80)
      assert(regs(1) == 4)
      assert(regs(2) == 7)
      assert(regs(3) == 1)
      assert(regs(4) == 2)
    }
  }
}
