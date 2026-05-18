package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class MemcpyProgramSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32ICore memcpy program") {
    it("memcpy copies two words") {
      val pb = new ProgramBuilder()

      pb.emit(iType(0x100, 0, 0x0, 1, 0x13)) // x1 = src
      pb.emit(iType(0x120, 0, 0x0, 2, 0x13)) // x2 = dst
      pb.emit(iType(2, 0, 0x0, 3, 0x13)) // x3 = words remaining
      pb.nops(4)

      pb.emit(bType(0x28, 0, 3, 0x0, 0x63)) // loop: beq x3, x0, done
      pb.emit(iType(0, 1, 0x2, 4, 0x03)) // lw x4, 0(x1)
      pb.nops(3)
      pb.emit(sType(0, 4, 2, 0x2, 0x23)) // sw x4, 0(x2)
      pb.emit(iType(4, 1, 0x0, 1, 0x13)) // x1 += 4
      pb.emit(iType(4, 2, 0x0, 2, 0x13)) // x2 += 4
      pb.emit(iType(-1, 3, 0x0, 3, 0x13)) // x3 -= 1
      pb.emit(jType(-0x24, 0, 0x6f)) // jump loop

      pb.emit(iType(-8, 2, 0x0, 2, 0x13)) // done: restore dst base
      pb.emit(iType(0, 2, 0x2, 5, 0x03)) // lw x5, 0(x2)
      pb.emit(iType(4, 2, 0x2, 6, 0x03)) // lw x6, 4(x2)

      val init = Map[Int, BigInt](
        0x100 -> BigInt("11223344", 16),
        0x104 -> BigInt("AABBCCDD", 16)
      )
      val (regs, mem) =
        runProgram(pb.result, maxCycles = 140, initialData = init)

      assert(mem.getOrElse(0x120, BigInt(0)) == BigInt("11223344", 16))
      assert(mem.getOrElse(0x124, BigInt(0)) == BigInt("AABBCCDD", 16))
      assert(regs(5) == BigInt("11223344", 16))
      assert(regs(6) == BigInt("AABBCCDD", 16))
    }
  }
}
