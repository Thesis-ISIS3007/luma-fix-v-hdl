package prototype

import chisel3._
import chisel3.util._

object AluOp extends ChiselEnum {
  val add, sub, and, or, xor, sll, srl, sra, slt, sltu, copyB = Value
}

object ImmSel extends ChiselEnum {
  val i, s, b, u, j = Value
}

object Rv32iOpcode {
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

class DecodeSignals extends Bundle {
  val illegal = Bool()
  val rs1Used = Bool()
  val rs2Used = Bool()
  val rdWrite = Bool()
  val aluSrc1Pc = Bool()
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

object Rv32iDecode {
  val WbSelAlu: UInt = 0.U(2.W)
  val WbSelMem: UInt = 1.U(2.W)
  val WbSelPc4: UInt = 2.U(2.W)
}

object ImmGen {
  private def signExtend(raw: UInt, fromBits: Int): UInt = {
    val sign = raw(fromBits - 1)
    Cat(Fill(32 - fromBits, sign), raw(fromBits - 1, 0))
  }

  def i(inst: UInt): UInt = signExtend(inst(31, 20), 12)
  def s(inst: UInt): UInt = signExtend(Cat(inst(31, 25), inst(11, 7)), 12)
  def b(inst: UInt): UInt = signExtend(Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)), 13)
  def u(inst: UInt): UInt = Cat(inst(31, 12), 0.U(12.W))
  def j(inst: UInt): UInt = signExtend(Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W)), 21)

  def select(inst: UInt, immSel: ImmSel.Type): UInt = {
    MuxLookup(
      immSel.asUInt,
      i(inst)
    )(
      Seq(
        ImmSel.i.asUInt -> i(inst),
        ImmSel.s.asUInt -> s(inst),
        ImmSel.b.asUInt -> b(inst),
        ImmSel.u.asUInt -> u(inst),
        ImmSel.j.asUInt -> j(inst)
      )
    )
  }
}

object Rv32iDecoder {
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
    decoded.ctrl.aluSrc1Pc := false.B
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
    decoded.ctrl.wbSel := Rv32iDecode.WbSelAlu

    switch(opcode) {
      is(Rv32iOpcode.LUI) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.copyB
        decoded.ctrl.immSel := ImmSel.u
      }
      is(Rv32iOpcode.AUIPC) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc1Pc := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.u
      }
      is(Rv32iOpcode.JAL) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.jump := true.B
        decoded.ctrl.immSel := ImmSel.j
        decoded.ctrl.wbSel := Rv32iDecode.WbSelPc4
      }
      is(Rv32iOpcode.JALR) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.jump := true.B
        decoded.ctrl.jumpReg := true.B
        decoded.ctrl.immSel := ImmSel.i
        decoded.ctrl.wbSel := Rv32iDecode.WbSelPc4
      }
      is(Rv32iOpcode.BRANCH) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.branch := true.B
        decoded.ctrl.immSel := ImmSel.b
      }
      is(Rv32iOpcode.LOAD) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.i
        decoded.ctrl.memRead := true.B
        decoded.ctrl.memUnsigned := funct3(2)
        decoded.ctrl.memSize := MuxLookup(funct3, 2.U)(Seq(
          "b000".U -> 0.U,
          "b001".U -> 1.U,
          "b010".U -> 2.U,
          "b100".U -> 0.U,
          "b101".U -> 1.U
        ))
        decoded.ctrl.wbSel := Rv32iDecode.WbSelMem
      }
      is(Rv32iOpcode.STORE) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.add
        decoded.ctrl.immSel := ImmSel.s
        decoded.ctrl.memWrite := true.B
        decoded.ctrl.memSize := MuxLookup(funct3, 2.U)(Seq(
          "b000".U -> 0.U,
          "b001".U -> 1.U,
          "b010".U -> 2.U
        ))
      }
      is(Rv32iOpcode.OPIMM) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rdWrite := true.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.immSel := ImmSel.i
        switch(funct3) {
          is("b000".U) { decoded.ctrl.aluOp := AluOp.add }
          is("b010".U) { decoded.ctrl.aluOp := AluOp.slt }
          is("b011".U) { decoded.ctrl.aluOp := AluOp.sltu }
          is("b100".U) { decoded.ctrl.aluOp := AluOp.xor }
          is("b110".U) { decoded.ctrl.aluOp := AluOp.or }
          is("b111".U) { decoded.ctrl.aluOp := AluOp.and }
          is("b001".U) { decoded.ctrl.aluOp := AluOp.sll }
          is("b101".U) {
            decoded.ctrl.aluOp := Mux(bit30, AluOp.sra, AluOp.srl)
          }
        }
      }
      is(Rv32iOpcode.OP) {
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs1Used := true.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.rdWrite := true.B
        switch(funct3) {
          is("b000".U) { decoded.ctrl.aluOp := Mux(bit30, AluOp.sub, AluOp.add) }
          is("b001".U) { decoded.ctrl.aluOp := AluOp.sll }
          is("b010".U) { decoded.ctrl.aluOp := AluOp.slt }
          is("b011".U) { decoded.ctrl.aluOp := AluOp.sltu }
          is("b100".U) { decoded.ctrl.aluOp := AluOp.xor }
          is("b101".U) { decoded.ctrl.aluOp := Mux(bit30, AluOp.sra, AluOp.srl) }
          is("b110".U) { decoded.ctrl.aluOp := AluOp.or }
          is("b111".U) { decoded.ctrl.aluOp := AluOp.and }
        }
      }
    }

    decoded
  }
}
