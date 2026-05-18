package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class FibonacciSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32ICore fibonacci") {
    it("computes fib(10)") {
      val pb = new ProgramBuilder()

      // Compact control-flow Fibonacci:
      // x1 = iteration counter, x2 = a, x3 = b
      // loop: if x1 == 0 stop; t = a + b; a = b; b = t; x1--; jump loop
      pb.emit(iType(10, 0, 0x0, 1, 0x13)) // addi x1, x0, 10
      pb.emit(iType(0, 0, 0x0, 2, 0x13)) // addi x2, x0, 0
      pb.emit(iType(1, 0, 0x0, 3, 0x13)) // addi x3, x0, 1
      pb.nops(3)

      pb.emit(bType(0x30, 0, 1, 0x0, 0x63)) // loop: beq x1, x0, done
      pb.emit(rType(0x00, 3, 2, 0x0, 4, 0x33)) // add x4, x2, x3
      pb.nops(3)
      pb.emit(iType(0, 3, 0x0, 2, 0x13)) // addi x2, x3, 0
      pb.nops(3)
      pb.emit(iType(0, 4, 0x0, 3, 0x13)) // addi x3, x4, 0
      pb.emit(iType(-1, 1, 0x0, 1, 0x13)) // addi x1, x1, -1
      pb.emit(jType(-0x2c, 0, 0x6f)) // jal x0, loop

      pb.emit(iType(0, 2, 0x0, 6, 0x13)) // done: addi x6, x2, 0

      val (regs, _) = runProgram(pb.result, maxCycles = 200)

      assert(regs(1) == 0)
      assert(regs(2) == 55)
      assert(regs(3) == 89)
      assert(regs(4) == 89)
      assert(regs(6) == 55)
    }
  }
}
