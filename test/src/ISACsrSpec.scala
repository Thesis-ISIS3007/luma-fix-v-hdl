package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISACsrSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32I Zicsr execution") {
    it("executes CSRRW/CSRRS/CSRRC and immediate variants") {
      val pb = new ProgramBuilder()
      // x1 = 0xAA
      pb.emit(iType(0x0AA, 0, 0x0, 1, 0x13))
      // old(mstatus=0) -> x2 ; mstatus = x1 (0xAA)
      pb.emit(csrrw(2, 0x300, 1))
      // old(0xAA) -> x3 ; mstatus |= x1 (still 0xAA)
      pb.emit(csrrs(3, 0x300, 1))
      // old(0xAA) -> x4 ; mstatus &= ~x1 (0)
      pb.emit(csrrc(4, 0x300, 1))
      // old(0) -> x5 ; mstatus = 3
      pb.emit(csrrwi(5, 0x300, 3))
      // old(3) -> x6 ; mstatus |= 4 => 7
      pb.emit(csrrsi(6, 0x300, 4))
      // old(7) -> x7 ; mstatus &= ~1 => 6
      pb.emit(csrrci(7, 0x300, 1))
      // read back final mstatus into x8 via csrrs x8, mstatus, x0
      pb.emit(csrrs(8, 0x300, 0))

      val (regs, _) = runProgram(pb.result, maxCycles = 80)
      assert(regs(2) == 0)
      assert(regs(3) == BigInt("AA", 16))
      assert(regs(4) == BigInt("AA", 16))
      assert(regs(5) == 0)
      assert(regs(6) == 3)
      assert(regs(7) == 7)
      assert(regs(8) == 6)
    }
  }
}
