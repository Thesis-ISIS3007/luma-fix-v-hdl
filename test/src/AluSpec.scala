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
  }
}
