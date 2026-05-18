package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class ISAUnsupportedSpec extends AnyFunSpec with ChiselSim {
  describe("RV32I unsupported opcodes") {
    it("marks reserved system encodings illegal") {
      simulate(new ISADecoderProbe()) { c =>
        val illegalInstructions = Seq(
          0x02000073, // reserved system encoding
          0x12300073 // arbitrary unsupported system opcode pattern
        )

        for (inst <- illegalInstructions) {
          c.io.inst.poke(inst.U(32.W))
          c.clock.step()
          c.io.illegal.expect(true.B)
        }
      }
    }
  }
}
