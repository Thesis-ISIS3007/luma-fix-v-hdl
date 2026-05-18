package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class AluSpec extends AnyFunSpec with ChiselSim {
  describe("ALU") {
    it("executes arithmetic and logical ops") {
      simulate(new Alu()) { c =>
        c.io.op.poke(AluOp.add)
        c.io.lhs.poke(11.U)
        c.io.rhs.poke(31.U)
        c.clock.step()
        c.io.out.expect(42.U)

        c.io.op.poke(AluOp.sub)
        c.io.lhs.poke(31.U)
        c.io.rhs.poke(11.U)
        c.clock.step()
        c.io.out.expect(20.U)

        c.io.op.poke(AluOp.and)
        c.io.lhs.poke("hAA55AA55".U)
        c.io.rhs.poke("h0F0F0F0F".U)
        c.clock.step()
        c.io.out.expect("h0A050A05".U)

        c.io.op.poke(AluOp.or)
        c.clock.step()
        c.io.out.expect("hAF5FAF5F".U)

        c.io.op.poke(AluOp.xor)
        c.clock.step()
        c.io.out.expect("hA55AA55A".U)
      }
    }

    it("executes shifts and comparisons") {
      simulate(new Alu()) { c =>
        c.io.op.poke(AluOp.sll)
        c.io.lhs.poke(1.U)
        c.io.rhs.poke(4.U)
        c.clock.step()
        c.io.out.expect(16.U) // 1 << 4

        c.io.op.poke(AluOp.srl)
        c.io.lhs.poke("h80000000".U)
        c.io.rhs.poke(4.U)
        c.clock.step()
        c.io.out.expect("h08000000".U)

        c.io.op.poke(AluOp.sra)
        c.clock.step()
        c.io.out.expect("hF8000000".U)

        c.io.op.poke(AluOp.slt)
        c.io.lhs.poke("hFFFFFFFF".U)
        c.io.rhs.poke(1.U)
        c.clock.step()
        c.io.out.expect(1.U)

        c.io.op.poke(AluOp.sltu)
        c.clock.step()
        c.io.out.expect(0.U)

        c.io.op.poke(AluOp.copyB)
        c.io.rhs.poke("h12345678".U)
        c.clock.step()
        c.io.out.expect("h12345678".U)
      }
    }

    it("computes 16Q16 multiply with sign and wrap-around") {
      simulate(new Alu()) { c =>
        // 1.0 * 1.0 = 1.0  (1.0 in 16Q16 is 0x00010000)
        c.io.op.poke(AluOp.fxmul)
        c.io.lhs.poke("h00010000".U)
        c.io.rhs.poke("h00010000".U)
        c.clock.step()
        c.io.out.expect("h00010000".U)

        // 0.5 * 2.0 = 1.0
        c.io.lhs.poke("h00008000".U)
        c.io.rhs.poke("h00020000".U)
        c.clock.step()
        c.io.out.expect("h00010000".U)

        // (-1.5) * 2.0 = -3.0
        c.io.lhs.poke("hFFFE8000".U) // -1.5
        c.io.rhs.poke("h00020000".U) // 2.0
        c.clock.step()
        c.io.out.expect("hFFFD0000".U) // -3.0

        // (-1.0) * (-1.0) = 1.0
        c.io.lhs.poke("hFFFF0000".U)
        c.io.rhs.poke("hFFFF0000".U)
        c.clock.step()
        c.io.out.expect("h00010000".U)

        // Wrap-around: large positive * large positive overflows the 16Q16
        // range. Result is the low 32 bits of (lhs * rhs) >> 16.
        c.io.lhs.poke("h7FFF0000".U) // ~32767.0
        c.io.rhs.poke("h00020000".U) // 2.0
        c.clock.step()
        // 0x7FFF0000 * 0x00020000 = 0x0FFFE_00000000; >>16 = 0xFFFE_00000000
        // low 32 bits = 0x00000000... actually compute precisely below.
        // 32767.0 * 2.0 = 65534.0 in real, but max representable is ~32767.999.
        // Wraps: signed (0x7FFF0000 << 1) = 0xFFFE0000 = -1.0... but we right
        // shift the 64-bit product, not the operand. (32767 * 2 = 65534) in
        // integer-bits which doesn't fit in [-32768, 32767], wraps to -2.
        c.io.out.expect("hFFFE0000".U) // -2.0
      }
    }
  }
}
