package luma_fix_v

import scala.collection.mutable

object test_utils {
  def iType(imm: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): Int = {
    ((imm & 0xfff) << 20) | ((rs1 & 0x1f) << 15) | ((funct3 & 0x7) << 12) |
      ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  def rType(
      funct7: Int,
      rs2: Int,
      rs1: Int,
      funct3: Int,
      rd: Int,
      opcode: Int
  ): Int = {
    ((funct7 & 0x7f) << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) |
      ((funct3 & 0x7) << 12) | ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  def sType(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int): Int = {
    val immHi = (imm >> 5) & 0x7f
    val immLo = imm & 0x1f
    (immHi << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) |
      ((funct3 & 0x7) << 12) | (immLo << 7) | (opcode & 0x7f)
  }

  def bType(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int): Int = {
    val bit12 = (imm >> 12) & 0x1
    val bit11 = (imm >> 11) & 0x1
    val bits10_5 = (imm >> 5) & 0x3f
    val bits4_1 = (imm >> 1) & 0xf
    (bit12 << 31) | (bits10_5 << 25) | ((rs2 & 0x1f) << 20) | ((rs1 & 0x1f) << 15) |
      ((funct3 & 0x7) << 12) | (bits4_1 << 8) | (bit11 << 7) | (opcode & 0x7f)
  }

  def uType(imm: Int, rd: Int, opcode: Int): Int = {
    (imm & 0xfffff000) | ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  def jType(imm: Int, rd: Int, opcode: Int): Int = {
    require(
      (imm & 0x1) == 0,
      s"J-type immediate must be 2-byte aligned, got $imm"
    )
    require(
      imm >= -(1 << 20) && imm < (1 << 20),
      s"J-type immediate out of signed 21-bit range, got $imm"
    )
    val bit20 = (imm >> 20) & 0x1
    val bits10_1 = (imm >> 1) & 0x3ff
    val bit11 = (imm >> 11) & 0x1
    val bits19_12 = (imm >> 12) & 0xff
    (bit20 << 31) | (bits19_12 << 12) | (bit11 << 20) | (bits10_1 << 21) |
      ((rd & 0x1f) << 7) | (opcode & 0x7f)
  }

  def nop: Int = iType(0, 0, 0x0, 0, 0x13)

  final class ProgramBuilder(startPc: Int = 0) {
    private val program = mutable.Map[Int, Int]()
    private var pc = startPc

    def setPc(nextPc: Int): ProgramBuilder = {
      pc = nextPc
      this
    }

    def emit(inst: Int): ProgramBuilder = {
      program(pc) = inst
      pc += 4
      this
    }

    def nops(count: Int): ProgramBuilder = {
      for (_ <- 0 until count) {
        emit(nop)
      }
      this
    }

    def currentPc: Int = pc

    def result: Map[Int, Int] = program.toMap
  }

  def applyMask(oldWord: BigInt, writeData: BigInt, mask: Int): BigInt = {
    var out = oldWord
    for (i <- 0 until 4) {
      if (((mask >> i) & 0x1) == 1) {
        val byteMask = BigInt(0xff) << (i * 8)
        out = (out & ~byteMask) | (writeData & byteMask)
      }
    }
    out & BigInt("FFFFFFFF", 16)
  }
}
