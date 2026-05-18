package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class MemsetProgramSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32ICore memset program") {
    it("memset writes repeated bytes") {
      val pb = new ProgramBuilder()

      pb.emit(iType(0x140, 0, 0x0, 1, 0x13)) // x1 = dst
      pb.emit(iType(4, 0, 0x0, 2, 0x13)) // x2 = count
      pb.emit(iType(0x7a, 0, 0x0, 3, 0x13)) // x3 = byte value
      pb.nops(3)

      pb.emit(bType(0x14, 0, 2, 0x0, 0x63)) // loop: beq x2, x0, done
      pb.emit(sType(0, 3, 1, 0x0, 0x23)) // sb x3, 0(x1)
      pb.emit(iType(1, 1, 0x0, 1, 0x13)) // x1 += 1
      pb.emit(iType(-1, 2, 0x0, 2, 0x13)) // x2 -= 1
      pb.emit(jType(-0x10, 0, 0x6f)) // jump loop

      pb.emit(iType(0x140, 0, 0x0, 4, 0x13)) // done: base
      pb.emit(iType(0, 4, 0x2, 5, 0x03)) // lw x5, 0(x4)

      val (regs, mem) = runProgram(pb.result, maxCycles = 120)
      assert(mem.getOrElse(0x140, BigInt(0)) == BigInt("7A7A7A7A", 16))
      assert(regs(5) == BigInt("7A7A7A7A", 16))
    }
  }
}
