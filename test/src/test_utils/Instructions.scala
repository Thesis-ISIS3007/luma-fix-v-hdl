package luma_fix_v.test_utils

trait Instructions {
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

  // FX 16Q16 extension uses the RISC-V custom-0 opcode (0b0001011) with
  // R-type encoding. funct3 selects the sub-op; funct7 distinguishes
  // FXADD/FXSUB. Unary forms (FXNEG, INT2FX, FX2INT, FXABS) ignore rs2.
  val FX_OPCODE: Int = 0x0b
  val FX_F3_ADDSUB: Int = 0x0
  val FX_F3_MUL: Int = 0x1
  val FX_F3_NEG: Int = 0x2
  val FX_F3_INT2FX: Int = 0x3
  val FX_F3_FX2INT: Int = 0x4
  val FX_F3_ABS: Int = 0x5
  val FX_F3_DIV: Int = 0x6
  val FX_F7_ADD: Int = 0x00
  val FX_F7_SUB: Int = 0x20

  def fxAdd(rd: Int, rs1: Int, rs2: Int): Int =
    rType(FX_F7_ADD, rs2, rs1, FX_F3_ADDSUB, rd, FX_OPCODE)
  def fxSub(rd: Int, rs1: Int, rs2: Int): Int =
    rType(FX_F7_SUB, rs2, rs1, FX_F3_ADDSUB, rd, FX_OPCODE)
  def fxMul(rd: Int, rs1: Int, rs2: Int): Int =
    rType(0, rs2, rs1, FX_F3_MUL, rd, FX_OPCODE)
  def fxNeg(rd: Int, rs1: Int): Int =
    rType(0, 0, rs1, FX_F3_NEG, rd, FX_OPCODE)
  def int2Fx(rd: Int, rs1: Int): Int =
    rType(0, 0, rs1, FX_F3_INT2FX, rd, FX_OPCODE)
  def fx2Int(rd: Int, rs1: Int): Int =
    rType(0, 0, rs1, FX_F3_FX2INT, rd, FX_OPCODE)
  def fxAbs(rd: Int, rs1: Int): Int =
    rType(0, 0, rs1, FX_F3_ABS, rd, FX_OPCODE)
  def fxDiv(rd: Int, rs1: Int, rs2: Int): Int =
    rType(0, rs2, rs1, FX_F3_DIV, rd, FX_OPCODE)
}
