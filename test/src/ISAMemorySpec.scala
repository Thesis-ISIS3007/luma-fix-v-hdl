package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISAMemorySpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32I memory execution") {
    it("executes loads and stores") {
      val pb = new ProgramBuilder()
      pb.emit(iType(0x081, 0, 0x0, 1, 0x13))
      pb.emit(sType(0x000, 1, 0, 0x0, 0x23))
      pb.emit(uType(0x00008000, 2, 0x37))
      pb.emit(sType(0x002, 2, 0, 0x1, 0x23))
      pb.emit(uType(0x12345000, 3, 0x37))
      pb.emit(sType(0x004, 3, 0, 0x2, 0x23))
      pb.emit(iType(0x000, 0, 0x0, 4, 0x03))
      pb.emit(iType(0x000, 0, 0x4, 5, 0x03))
      pb.emit(iType(0x002, 0, 0x1, 6, 0x03))
      pb.emit(iType(0x002, 0, 0x5, 7, 0x03))
      pb.emit(iType(0x004, 0, 0x2, 8, 0x03))

      val (regs, dataMem) = runProgram(pb.result, maxCycles = 48)

      assert(dataMem.getOrElse(0, BigInt(0)) == BigInt("80000081", 16))
      assert(dataMem.getOrElse(4, BigInt(0)) == BigInt("12345000", 16))
      assert(regs(4) == BigInt("FFFFFF81", 16))
      assert(regs(5) == BigInt("00000081", 16))
      assert(regs(6) == BigInt("FFFF8000", 16))
      assert(regs(7) == BigInt("00008000", 16))
      assert(regs(8) == BigInt("12345000", 16))
    }

    it(
      "detects and squashes misaligned half/word accesses (no trap path yet)"
    ) {
      val pb = new ProgramBuilder()
      pb.emit(iType(0x07b, 0, 0x0, 1, 0x13)) // x1 = 123
      pb.emit(sType(0x002, 1, 0, 0x2, 0x23)) // sw x1, 2(x0)  (misaligned)
      pb.emit(iType(0x002, 0, 0x2, 2, 0x03)) // lw x2, 2(x0)  (misaligned)
      pb.emit(iType(0x001, 0, 0x1, 3, 0x03)) // lh x3, 1(x0)  (misaligned)

      val (regs, dataMem) = runProgram(
        pb.result,
        maxCycles = 32,
        initialData = Map(0 -> BigInt("A5A5A5A5", 16))
      )

      assert(dataMem.getOrElse(0, BigInt(0)) == BigInt("A5A5A5A5", 16))
      assert(regs(2) == 0)
      assert(regs(3) == 0)
    }
  }
}
