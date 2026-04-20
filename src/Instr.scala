package luma_fix_v

import chisel3._
import chisel3.util._

object AluOp extends ChiselEnum {
  val add, sub, and, or, xor, sll, srl, sra, slt, sltu, copyB = Value
}

object ImmSel extends ChiselEnum {
  val i, s, b, u, j = Value
}

object RV32IOpcode {
  val LUI: UInt = "b0110111".U(7.W)
  val AUIPC: UInt = "b0010111".U(7.W)
  val JAL: UInt = "b1101111".U(7.W)
  val JALR: UInt = "b1100111".U(7.W)
  val BRANCH: UInt = "b1100011".U(7.W)
  val LOAD: UInt = "b0000011".U(7.W)
  val STORE: UInt = "b0100011".U(7.W)
  val OPIMM: UInt = "b0010011".U(7.W)
  val OP: UInt = "b0110011".U(7.W)
}

object RV32IBranchFunct3 {
  val BEQ: UInt = "b000".U(3.W)
  val BNE: UInt = "b001".U(3.W)
  val BLT: UInt = "b100".U(3.W)
  val BGE: UInt = "b101".U(3.W)
  val BLTU: UInt = "b110".U(3.W)
  val BGEU: UInt = "b111".U(3.W)
}

object RV32IAluFunct3 {
  val ADD_SUB: UInt = "b000".U(3.W)
  val SLL: UInt = "b001".U(3.W)
  val SLT: UInt = "b010".U(3.W)
  val SLTU: UInt = "b011".U(3.W)
  val XOR: UInt = "b100".U(3.W)
  val SRL_SRA: UInt = "b101".U(3.W)
  val OR: UInt = "b110".U(3.W)
  val AND: UInt = "b111".U(3.W)
}

object RV32IMemFunct3 {
  val B: UInt = "b000".U(3.W)
  val H: UInt = "b001".U(3.W)
  val W: UInt = "b010".U(3.W)
  val BU: UInt = "b100".U(3.W)
  val HU: UInt = "b101".U(3.W)
}

object RV32IMemSize {
  val Byte: UInt = 0.U(2.W)
  val Half: UInt = 1.U(2.W)
  val Word: UInt = 2.U(2.W)
}

class DecodeSignals extends Bundle {
  val illegal = Bool()
  val rs1Used = Bool()
  val rs2Used = Bool()
  val rdWrite = Bool()
  val aluSrc1PC = Bool()
  val aluSrc2Imm = Bool()
  val aluOp = AluOp()
  val immSel = ImmSel()
  val branch = Bool()
  val jump = Bool()
  val jumpReg = Bool()
  val memRead = Bool()
  val memWrite = Bool()
  val memUnsigned = Bool()
  val memSize = UInt(2.W)
  val wbSel = UInt(2.W)
}

class DecodedInstruction extends Bundle {
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val ctrl = new DecodeSignals
}

object RV32IDecode {
  val WbSelAlu: UInt = 0.U(2.W)
  val WbSelMem: UInt = 1.U(2.W)
  val WbSelPC4: UInt = 2.U(2.W)
}

object ImmGen {
  private def signExtend(raw: UInt, fromBits: Int): UInt = {
    val sign = raw(fromBits - 1)
    Cat(Fill(32 - fromBits, sign), raw(fromBits - 1, 0))
  }

  def i(inst: UInt): UInt = signExtend(inst(31, 20), 12)
  def s(inst: UInt): UInt = signExtend(Cat(inst(31, 25), inst(11, 7)), 12)
  def b(inst: UInt): UInt =
    signExtend(Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)), 13)
  def u(inst: UInt): UInt = Cat(inst(31, 12), 0.U(12.W))
  def j(inst: UInt): UInt = signExtend(
    Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W)),
    21
  )

  def select(inst: UInt, immSel: ImmSel.Type): UInt = {
    MuxLookup(
      immSel,
      i(inst)
    )(
      Seq(
        ImmSel.i -> i(inst),
        ImmSel.s -> s(inst),
        ImmSel.b -> b(inst),
        ImmSel.u -> u(inst),
        ImmSel.j -> j(inst)
      )
    )
  }
}

object RV32IDecoder {
  def decode(inst: UInt): DecodedInstruction = {
    val decoded = Wire(new DecodedInstruction)
    val opcode = inst(6, 0)
    val funct3 = inst(14, 12)
    val funct7 = inst(31, 25)
    val bit30 = inst(30)

    decoded.rs1 := inst(19, 15)
    decoded.rs2 := inst(24, 20)
    decoded.rd := inst(11, 7)
    decoded.funct3 := funct3
    decoded.funct7 := funct7

    decoded.ctrl.illegal := true.B
    decoded.ctrl.rs1Used := false.B
    decoded.ctrl.rs2Used := false.B
    decoded.ctrl.rdWrite := false.B
    decoded.ctrl.aluSrc1PC := false.B
    decoded.ctrl.aluSrc2Imm := false.B
    decoded.ctrl.aluOp := AluOp.add
    decoded.ctrl.immSel := ImmSel.i
    decoded.ctrl.branch := false.B
    decoded.ctrl.jump := false.B
    decoded.ctrl.jumpReg := false.B
    decoded.ctrl.memRead := false.B
    decoded.ctrl.memWrite := false.B
    decoded.ctrl.memUnsigned := false.B
    decoded.ctrl.memSize := 2.U
    decoded.ctrl.wbSel := RV32IDecode.WbSelAlu

    switch(opcode) {
      is(RV32IOpcode.LUI) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.copyB
        decoded.ctrl.immSel := ImmSel.u
      }
      is(RV32IOpcode.AUIPC) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc1PC := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.u
      }
      is(RV32IOpcode.JAL) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.jump := true.B
        decoded.ctrl.immSel := ImmSel.j
        decoded.ctrl.wbSel := RV32IDecode.WbSelPC4
      }
      is(RV32IOpcode.JALR) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.jump := true.B
        decoded.ctrl.jumpReg := true.B
        decoded.ctrl.immSel := ImmSel.i
        decoded.ctrl.wbSel := RV32IDecode.WbSelPC4
      }
      is(RV32IOpcode.BRANCH) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.branch := true.B
        decoded.ctrl.immSel := ImmSel.b
      }
      is(RV32IOpcode.LOAD) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.i
        decoded.ctrl.memRead := true.B
        decoded.ctrl.memUnsigned := funct3(2)
        decoded.ctrl.memSize := MuxLookup(funct3, RV32IMemSize.Word)(
          Seq(
            RV32IMemFunct3.B -> RV32IMemSize.Byte,
            RV32IMemFunct3.H -> RV32IMemSize.Half,
            RV32IMemFunct3.W -> RV32IMemSize.Word,
            RV32IMemFunct3.BU -> RV32IMemSize.Byte,
            RV32IMemFunct3.HU -> RV32IMemSize.Half
          )
        )
        decoded.ctrl.wbSel := RV32IDecode.WbSelMem
      }
      is(RV32IOpcode.STORE) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.s
        decoded.ctrl.memWrite := true.B
        decoded.ctrl.memSize := MuxLookup(funct3, RV32IMemSize.Word)(
          Seq(
            RV32IMemFunct3.B -> RV32IMemSize.Byte,
            RV32IMemFunct3.H -> RV32IMemSize.Half,
            RV32IMemFunct3.W -> RV32IMemSize.Word
          )
        )
      }
      is(RV32IOpcode.OPIMM) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.immSel := ImmSel.i
        switch(funct3) {
          is(RV32IAluFunct3.ADD_SUB) { decoded.ctrl.aluOp := AluOp.add }
          is(RV32IAluFunct3.SLT) { decoded.ctrl.aluOp := AluOp.slt }
          is(RV32IAluFunct3.SLTU) { decoded.ctrl.aluOp := AluOp.sltu }
          is(RV32IAluFunct3.XOR) { decoded.ctrl.aluOp := AluOp.xor }
          is(RV32IAluFunct3.OR) { decoded.ctrl.aluOp := AluOp.or }
          is(RV32IAluFunct3.AND) { decoded.ctrl.aluOp := AluOp.and }
          is(RV32IAluFunct3.SLL) { decoded.ctrl.aluOp := AluOp.sll }
          is(RV32IAluFunct3.SRL_SRA) {
            decoded.ctrl.aluOp := Mux(bit30, AluOp.sra, AluOp.srl)
          }
        }
      }
      is(RV32IOpcode.OP) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.rdWrite := true.B
        switch(funct3) {
          is(RV32IAluFunct3.ADD_SUB) {
            decoded.ctrl.aluOp := Mux(bit30, AluOp.sub, AluOp.add)
          }
          is(RV32IAluFunct3.SLL) { decoded.ctrl.aluOp := AluOp.sll }
          is(RV32IAluFunct3.SLT) { decoded.ctrl.aluOp := AluOp.slt }
          is(RV32IAluFunct3.SLTU) { decoded.ctrl.aluOp := AluOp.sltu }
          is(RV32IAluFunct3.XOR) { decoded.ctrl.aluOp := AluOp.xor }
          is(RV32IAluFunct3.SRL_SRA) {
            decoded.ctrl.aluOp := Mux(bit30, AluOp.sra, AluOp.srl)
          }
          is(RV32IAluFunct3.OR) { decoded.ctrl.aluOp := AluOp.or }
          is(RV32IAluFunct3.AND) { decoded.ctrl.aluOp := AluOp.and }
        }
      }
    }

    decoded
  }
}
