package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ArraySumProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore array sum program") {
    it("array sum over memory words") {
      val pb = new ProgramBuilder()

      pb.emit(iType(0x100, 0, 0x0, 1, 0x13)) // x1 = base pointer
      pb.emit(iType(4, 0, 0x0, 2, 0x13)) // x2 = count
      pb.emit(iType(0, 0, 0x0, 3, 0x13)) // x3 = sum
      pb.nops(3)

      pb.emit(bType(0x24, 0, 2, 0x0, 0x63)) // loop: beq x2, x0, done
      pb.emit(iType(0, 1, 0x2, 4, 0x03)) // lw x4, 0(x1)
      pb.nops(3)
      pb.emit(rType(0x00, 4, 3, 0x0, 3, 0x33)) // x3 += x4
      pb.emit(iType(4, 1, 0x0, 1, 0x13)) // x1 += 4
      pb.emit(iType(-1, 2, 0x0, 2, 0x13)) // x2 -= 1
      pb.emit(jType(-0x20, 0, 0x6f)) // jump loop

      pb.emit(iType(0, 3, 0x0, 5, 0x13)) // done: x5 = sum

      val init = Map[Int, BigInt](
        0x100 -> 1,
        0x104 -> 2,
        0x108 -> 3,
        0x10c -> 4
      )
      val (regs, _) = runProgram(pb.result, maxCycles = 120, initialData = init)

      assert(regs(3) == 10)
      assert(regs(5) == 10)
    }
  }
}
