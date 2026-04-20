package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISAArithmeticSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32I arithmetic and immediate execution") {
    it("executes all OP-IMM instructions") {
      val pb = new ProgramBuilder()
      pb.emit(iType(16, 0, 0x0, 1, 0x13))
      pb.emit(iType(-16, 0, 0x0, 2, 0x13))
      pb.emit(uType(0x80000000, 3, 0x37))
      pb.nops(3)
      pb.emit(iType(5, 1, 0x0, 4, 0x13))
      pb.emit(iType(0, 2, 0x2, 5, 0x13))
      pb.emit(iType(0, 2, 0x3, 6, 0x13))
      pb.emit(iType(0x0ff, 1, 0x4, 7, 0x13))
      pb.emit(iType(0x0f0, 1, 0x6, 8, 0x13))
      pb.emit(iType(0x00f, 1, 0x7, 9, 0x13))
      pb.emit(iType(4, 1, 0x1, 10, 0x13))
      pb.emit(iType(4, 3, 0x5, 11, 0x13))
      pb.emit(iType(0x404, 3, 0x5, 12, 0x13))

      val (regs, _) = runProgram(pb.result, maxCycles = 48)

      assert(regs(1) == 16)
      assert(regs(2) == BigInt("FFFFFFF0", 16))
      assert(regs(3) == BigInt("80000000", 16))
      assert(regs(4) == 21)
      assert(regs(5) == 1)
      assert(regs(6) == 0)
      assert(regs(7) == BigInt("000000EF", 16))
      assert(regs(8) == BigInt("000000F0", 16))
      assert(regs(9) == BigInt("00000000", 16))
      assert(regs(10) == BigInt("00000100", 16))
      assert(regs(11) == BigInt("08000000", 16))
      assert(regs(12) == BigInt("F8000000", 16))
    }

    it("executes all OP instructions") {
      val pb = new ProgramBuilder()
      pb.emit(iType(16, 0, 0x0, 1, 0x13))
      pb.emit(iType(3, 0, 0x0, 2, 0x13))
      pb.emit(iType(-16, 0, 0x0, 3, 0x13))
      pb.emit(uType(0x80000000, 4, 0x37))
      pb.nops(3)
      pb.emit(rType(0x00, 2, 1, 0x0, 5, 0x33))
      pb.emit(rType(0x20, 2, 1, 0x0, 6, 0x33))
      pb.emit(rType(0x00, 2, 1, 0x1, 7, 0x33))
      pb.emit(rType(0x00, 1, 3, 0x2, 8, 0x33))
      pb.emit(rType(0x00, 1, 3, 0x3, 9, 0x33))
      pb.emit(rType(0x00, 2, 1, 0x4, 10, 0x33))
      pb.emit(rType(0x00, 2, 4, 0x5, 11, 0x33))
      pb.emit(rType(0x20, 2, 4, 0x5, 12, 0x33))
      pb.emit(rType(0x00, 2, 1, 0x6, 13, 0x33))
      pb.emit(rType(0x00, 2, 1, 0x7, 14, 0x33))

      val (regs, _) = runProgram(pb.result, maxCycles = 48)

      assert(regs(5) == 19)
      assert(regs(6) == 13)
      assert(regs(7) == 128)
      assert(regs(8) == 1)
      assert(regs(9) == 0)
      assert(regs(10) == 19)
      assert(regs(11) == BigInt("10000000", 16))
      assert(regs(12) == BigInt("F0000000", 16))
      assert(regs(13) == 19)
      assert(regs(14) == 0)
    }
  }
}
