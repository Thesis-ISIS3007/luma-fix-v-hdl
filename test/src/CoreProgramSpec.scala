package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class CoreProgramSpec extends AnyFunSpec with ChiselSim {
  private def iType(
      imm: Int,
      rs1: Int,
      funct3: Int,
      rd: Int,
      opcode: Int
  ): Int = {
    ((imm & 0xfff) << 20) | ((rs1 & 0x1f) << 15) | ((funct3 & 0x7) << 12) | ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  private def rType(
      funct7: Int,
      rs2: Int,
      rs1: Int,
      funct3: Int,
      rd: Int,
      opcode: Int
  ): Int = {
    ((funct7 & 0x7f) << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) | ((funct3 & 0x7) << 12) | ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  private def sType(
      imm: Int,
      rs2: Int,
      rs1: Int,
      funct3: Int,
      opcode: Int
  ): Int = {
    val immHi = (imm >> 5) & 0x7f
    val immLo = imm & 0x1f
    (immHi << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) | ((funct3 & 0x7) << 12) | (immLo << 7) | (opcode & 0x7f)
  }

  private def bType(
      imm: Int,
      rs2: Int,
      rs1: Int,
      funct3: Int,
      opcode: Int
  ): Int = {
    val bit12 = (imm >> 12) & 0x1
    val bit11 = (imm >> 11) & 0x1
    val bits10_5 = (imm >> 5) & 0x3f
    val bits4_1 = (imm >> 1) & 0xf
    (bit12 << 31) | (bits10_5 << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) |
      ((funct3 & 0x7) << 12) | (bits4_1 << 8) | (bit11 << 7) | (opcode & 0x7f)
  }

  private def applyMask(
      oldWord: BigInt,
      writeData: BigInt,
      mask: Int
  ): BigInt = {
    var out = oldWord
    for (i <- 0 until 4) {
      if (((mask >> i) & 0x1) == 1) {
        val byteMask = BigInt(0xff) << (i * 8)
        out = (out & ~byteMask) | (writeData & byteMask)
      }
    }
    out & BigInt("FFFFFFFF", 16)
  }

  describe("RV32ICore") {
    it("runs a short mixed RV32I sequence") {
      val prog = Map[Int, Int](
        0x00 -> iType(5, 0, 0, 1, 0x13), // addi x1, x0, 5
        0x04 -> iType(7, 0, 0, 2, 0x13), // addi x2, x0, 7
        0x08 -> rType(0, 2, 1, 0, 3, 0x33), // add x3, x1, x2
        0x0c -> sType(0, 3, 0, 2, 0x23), // sw x3, 0(x0)
        0x10 -> iType(0, 0, 2, 4, 0x03), // lw x4, 0(x0)
        0x14 -> bType(8, 3, 4, 0, 0x63), // beq x4, x3, +8
        0x18 -> iType(1, 0, 0, 5, 0x13), // addi x5, x0, 1 (skipped)
        0x1c -> iType(2, 0, 0, 5, 0x13) // addi x5, x0, 2
      )

      simulate(new RV32ICore()) { c =>
        val dataMem = scala.collection.mutable.Map[Int, BigInt]()
        val regs = Array.fill[BigInt](32)(BigInt(0))

        c.io.imem.req.ready.poke(true.B)
        c.io.imem.resp.valid.poke(false.B)
        c.io.imem.resp.bits.poke(0.U)

        c.io.dmem.req.ready.poke(true.B)
        c.io.dmem.resp.valid.poke(false.B)
        c.io.dmem.resp.bits.poke(0.U)

        for (_ <- 0 until 80) {
          val pc = c.io.imem.req.bits.peek().litValue.toInt
          val inst = prog.getOrElse(pc, iType(0, 0, 0, 0, 0x13))
          c.io.imem.resp.valid.poke(true.B)
          c.io.imem.resp.bits.poke((inst & 0xffffffffL).U(32.W))

          c.io.dmem.resp.valid.poke(false.B)
          c.io.dmem.resp.bits.poke(0.U)

          if (c.io.dmem.req.valid.peek().litToBoolean) {
            val addr = c.io.dmem.req.bits.addr.peek().litValue.toInt & ~0x3
            val write = c.io.dmem.req.bits.write.peek().litToBoolean
            val wdata = c.io.dmem.req.bits.wData.peek().litValue
            val wmask = c.io.dmem.req.bits.wMask.peek().litValue.toInt
            val oldWord = dataMem.getOrElse(addr, BigInt(0))

            if (write) {
              dataMem(addr) = applyMask(oldWord, wdata, wmask)
            } else {
              c.io.dmem.resp.valid.poke(true.B)
              c.io.dmem.resp.bits
                .poke(dataMem.getOrElse(addr, BigInt(0)).U(32.W))
            }
          }

          c.clock.step()

          if (c.io.debugWbValid.peek().litToBoolean) {
            val rd = c.io.debugWbRd.peek().litValue.toInt
            val data = c.io.debugWbData.peek().litValue & BigInt("FFFFFFFF", 16)
            if (rd != 0) regs(rd) = data
          }
        }

        assert(regs(3) == 12, s"x3 expected 12, got ${regs(3)}")
        assert(regs(4) == 12, s"x4 expected 12, got ${regs(4)}")
        assert(regs(5) == 2, s"x5 expected 2, got ${regs(5)}")
        assert(
          dataMem.getOrElse(0, BigInt(0)) == 12,
          s"mem[0] expected 12, got ${dataMem.getOrElse(0, BigInt(0))}"
        )
      }
    }
  }
}
