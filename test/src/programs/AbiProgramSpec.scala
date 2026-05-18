package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class AbiProgramSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32ICore ABI program") {
    it("preserves callee-saved registers across a function call") {
      val pb = new ProgramBuilder()

      // main:
      pb.emit(iType(0x200, 0, 0x0, 2, 0x13)) // sp = 0x200
      pb.emit(iType(33, 0, 0x0, 8, 0x13)) // s0 = 33
      pb.emit(iType(44, 0, 0x0, 9, 0x13)) // s1 = 44
      pb.emit(iType(5, 0, 0x0, 10, 0x13)) // a0 = 5
      pb.emit(iType(7, 0, 0x0, 11, 0x13)) // a1 = 7

      val funcPc = 0x100
      pb.emit(jType(funcPc - pb.currentPc, 1, 0x6f)) // jal ra, func

      pb.emit(iType(0, 10, 0x0, 5, 0x13)) // x5 = return value (a0)
      pb.emit(iType(0, 8, 0x0, 6, 0x13)) // x6 = s0 after call
      pb.emit(iType(0, 9, 0x0, 7, 0x13)) // x7 = s1 after call
      pb.emit(iType(0, 2, 0x0, 28, 0x13)) // x28 = sp after call
      pb.emit(
        jType(0, 0, 0x6f)
      ) // stay in place; do not fall through into function area

      // func:
      pb.setPc(funcPc)
      pb.emit(iType(-8, 2, 0x0, 2, 0x13)) // addi sp, sp, -8
      pb.emit(sType(0, 8, 2, 0x2, 0x23)) // sw s0, 0(sp)
      pb.emit(sType(4, 9, 2, 0x2, 0x23)) // sw s1, 4(sp)

      pb.emit(rType(0x00, 11, 10, 0x0, 10, 0x33)) // add a0, a0, a1
      pb.emit(iType(99, 0, 0x0, 8, 0x13)) // clobber s0 in callee
      pb.emit(iType(88, 0, 0x0, 9, 0x13)) // clobber s1 in callee

      pb.emit(iType(0, 2, 0x2, 8, 0x03)) // lw s0, 0(sp)
      pb.emit(iType(4, 2, 0x2, 9, 0x03)) // lw s1, 4(sp)
      pb.emit(iType(8, 2, 0x0, 2, 0x13)) // addi sp, sp, 8
      pb.emit(iType(0, 1, 0x0, 0, 0x67)) // jalr x0, ra, 0

      val (regs, mem) = runProgram(pb.result, maxCycles = 160)

      assert(regs(5) == 12, s"a0 return value expected 12, got ${regs(5)}")
      assert(regs(6) == 33, s"s0 expected preserved value 33, got ${regs(6)}")
      assert(regs(7) == 44, s"s1 expected preserved value 44, got ${regs(7)}")
      assert(
        regs(28) == 0x200,
        s"sp expected restored value 0x200, got ${regs(28)}"
      )

      assert(mem.getOrElse(0x1f8, BigInt(0)) == 33)
      assert(mem.getOrElse(0x1fc, BigInt(0)) == 44)
    }
  }
}
