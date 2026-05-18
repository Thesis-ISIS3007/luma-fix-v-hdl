package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISASystemSpec extends AnyFunSpec with ChiselSim with ISATestSupport {
  describe("RV32I system and fence decode/execute") {
    it("marks fence/fence.i/ecall/ebreak legal") {
      simulate(new ISADecoderProbe()) { c =>
        val legalInstructions = Seq(
          0x0000000f, // fence
          0x0000100f, // fence.i
          0x00000073, // ecall
          0x00100073 // ebreak
        )
        for (inst <- legalInstructions) {
          c.io.inst.poke(inst.U(32.W))
          c.clock.step()
          c.io.illegal.expect(false.B)
        }
      }
    }

    it("treats fence/fence.i/ecall/ebreak as no-ops in program flow") {
      val pb = new ProgramBuilder()
      pb.emit(iType(5, 0, 0x0, 1, 0x13)) // x1 = 5
      pb.emit(0x0000000f) // fence
      pb.emit(0x0000100f) // fence.i
      pb.emit(0x00000073) // ecall
      pb.emit(0x00100073) // ebreak
      pb.emit(iType(3, 1, 0x0, 2, 0x13)) // x2 = x1 + 3

      val (regs, _) = runProgram(pb.result, maxCycles = 64)
      assert(regs(1) == 5)
      assert(regs(2) == 8)
    }
  }
}
