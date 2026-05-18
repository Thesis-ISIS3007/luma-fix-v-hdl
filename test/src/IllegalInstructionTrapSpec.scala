package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class IllegalInstructionTrapSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore illegal-instruction trap") {
    val TrapVector = 0x80
    val IllegalCause = 2
    val IllegalInst = 0x12300073 // reserved SYSTEM encoding

    it("redirects to mtvec and records trap CSRs") {
      val pb = new ProgramBuilder()
      pb.emit(iType(TrapVector, 0, 0x0, 1, 0x13)) // x1 = mtvec
      pb.emit(csrrw(0, 0x305, 1)) // mtvec <- x1
      pb.emit(IllegalInst) // trap
      pb.emit(iType(1, 0, 0x0, 2, 0x13)) // should be skipped by trap redirect
      pb.setPc(TrapVector)
      pb.emit(iType(7, 0, 0x0, 3, 0x13)) // trap handler marker
      pb.emit(csrrs(4, 0x342, 0)) // x4 <- mcause
      pb.emit(csrrs(5, 0x341, 0)) // x5 <- mepc
      pb.emit(csrrs(6, 0x305, 0)) // x6 <- mtvec (must stay unchanged)
      pb.emit(csrrs(7, 0x300, 0)) // x7 <- mstatus (still reset value here)

      val (regs, _) = runProgram(pb.result, maxCycles = 80)
      assert(regs(2) == 0)
      assert(regs(3) == 7)
      assert(regs(4) == IllegalCause)
      assert(regs(5) == 8) // faulting instruction PC
      assert(regs(6) == TrapVector)
      assert(regs(7) == 0)
    }

    it("records the exact faulting PC for different illegal placement") {
      val pb = new ProgramBuilder()
      pb.emit(iType(TrapVector, 0, 0x0, 1, 0x13)) // x1 = mtvec
      pb.emit(csrrw(0, 0x305, 1)) // mtvec <- x1
      pb.nops(3) // push illegal instruction deeper in the stream
      pb.emit(IllegalInst) // PC = 20
      pb.emit(iType(1, 0, 0x0, 2, 0x13)) // should not retire
      pb.setPc(TrapVector)
      pb.emit(csrrs(3, 0x342, 0)) // x3 <- mcause
      pb.emit(csrrs(4, 0x341, 0)) // x4 <- mepc

      val (regs, _) = runProgram(pb.result, maxCycles = 100)
      assert(regs(2) == 0)
      assert(regs(3) == IllegalCause)
      assert(regs(4) == 20)
    }
  }
}
