package luma_fix_v

import chisel3._
import chisel3.util._

object AluOp extends ChiselEnum {
  val add, sub, and, or, xor, sll, srl, sra, slt, sltu, copyB, fxmul, fxdiv =
    Value
}

object ImmSel extends ChiselEnum {
  val i, s, b, u, j = Value
}

// Custom-0 opcode reserved by the RISC-V spec for non-standard extensions.
// Carries the FX 16Q16 instructions defined below.
object FxOpcode {
  val CUSTOM_0: UInt = "b0001011".U(7.W)
}

// FX sub-op selector. R-type encoding: funct3 picks the operation, funct7
// distinguishes FXADD from FXSUB. Unary ops ignore rs2.
object FxFunct3 {
  val FXADDSUB: UInt = "b000".U(3.W)
  val FXMUL: UInt = "b001".U(3.W)
  val FXNEG: UInt = "b010".U(3.W)
  val INT2FX: UInt = "b011".U(3.W)
  val FX2INT: UInt = "b100".U(3.W)
  val FXABS: UInt = "b101".U(3.W)
  val FXDIV: UInt = "b110".U(3.W)
}

object FxFunct7 {
  val FXADD: UInt = "b0000000".U(7.W)
  val FXSUB: UInt = "b0100000".U(7.W)
}

// Internal scratch registers used by multi-uop FX sequences. Addressed via
// the high bit of the (now 6-bit) register port; bits[1:0] index the small
// scratch file. Architectural register x0 is still address 0 with bit 5 = 0,
// so it cannot collide with any scratch entry.
object ScratchReg {
  val Width: Int = 6
  val S0: UInt = "b100000".U(Width.W)
  val S1: UInt = "b100001".U(Width.W)
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
  // 6-bit register addresses: bit 5 = 1 selects the scratch file. Arch x0..x31
  // live at addresses 0..31 unchanged, so RV32I decoding just zero-extends.
  val rs1 = UInt(6.W)
  val rs2 = UInt(6.W)
  val rd = UInt(6.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  // Pre-resolved immediate. RV32I uses ImmGen.select(inst, immSel); FX micro-ops
  // can override with a literal value (e.g. 16 for INT2FX shift amount).
  val imm = UInt(32.W)
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

    decoded.rs1 := Cat(0.U(1.W), inst(19, 15))
    decoded.rs2 := Cat(0.U(1.W), inst(24, 20))
    decoded.rd := Cat(0.U(1.W), inst(11, 7))
    decoded.funct3 := funct3
    decoded.funct7 := funct7
    decoded.imm := 0.U

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

    decoded.imm := ImmGen.select(inst, decoded.ctrl.immSel)

    decoded
  }
}

// FX 16Q16 instruction cracker. Translates a single FX instruction into a
// short sequence of micro-ops, one per call: `step` selects which micro-op to
// emit, and the returned `last` flag indicates this micro-op completes the
// instruction. Most FX ops are 1-uop and reuse existing AluOps verbatim;
// FXABS expands into three uops using the internal scratch file (s0, s1).
object FxDecoder {
  def crack(inst: UInt, step: UInt): (DecodedInstruction, Bool) = {
    val decoded = Wire(new DecodedInstruction)
    val last = WireDefault(true.B)

    val archRs1 = Cat(0.U(1.W), inst(19, 15))
    val archRs2 = Cat(0.U(1.W), inst(24, 20))
    val archRd = Cat(0.U(1.W), inst(11, 7))
    val funct3 = inst(14, 12)
    val funct7 = inst(31, 25)

    // Sensible defaults; each op below overrides what it needs.
    decoded.rs1 := archRs1
    decoded.rs2 := archRs2
    decoded.rd := archRd
    decoded.funct3 := funct3
    decoded.funct7 := funct7
    decoded.imm := 0.U(32.W)

    decoded.ctrl.illegal := true.B
    decoded.ctrl.rs1Used := true.B
    decoded.ctrl.rs2Used := false.B
    decoded.ctrl.rdWrite := true.B
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

    switch(funct3) {
      is(FxFunct3.FXADDSUB) {
        // FXADD/FXSUB: bit-identical to integer ADD/SUB on 16Q16.
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluOp := Mux(
          funct7 === FxFunct7.FXSUB,
          AluOp.sub,
          AluOp.add
        )
      }
      is(FxFunct3.FXMUL) {
        // FXMUL: needs new ALU op (signed 32x32 -> >>16 truncate).
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluOp := AluOp.fxmul
      }
      is(FxFunct3.FXNEG) {
        // FXNEG rd, rs1  ==>  sub rd, x0, rs1
        decoded.ctrl.illegal := false.B
        decoded.rs1 := 0.U(6.W)
        decoded.rs2 := archRs1
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluOp := AluOp.sub
      }
      is(FxFunct3.INT2FX) {
        // INT2FX rd, rs1  ==>  slli rd, rs1, 16
        decoded.ctrl.illegal := false.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.sll
        decoded.imm := 16.U(32.W)
      }
      is(FxFunct3.FX2INT) {
        // FX2INT rd, rs1  ==>  srai rd, rs1, 16
        decoded.ctrl.illegal := false.B
        decoded.ctrl.aluSrc2Imm := true.B
        decoded.ctrl.aluOp := AluOp.sra
        decoded.imm := 16.U(32.W)
      }
      is(FxFunct3.FXABS) {
        // Branchless |x|: t = x >>s 31; rd = (x ^ t) - t.
        // 3 micro-ops using scratch s0 (= sign mask) and s1 (= x ^ s0).
        decoded.ctrl.illegal := false.B
        last := step === 2.U
        switch(step) {
          is(0.U) {
            decoded.rs1 := archRs1
            decoded.rd := ScratchReg.S0
            decoded.ctrl.aluSrc2Imm := true.B
            decoded.ctrl.aluOp := AluOp.sra
            decoded.imm := 31.U(32.W)
          }
          is(1.U) {
            decoded.rs1 := archRs1
            decoded.rs2 := ScratchReg.S0
            decoded.rd := ScratchReg.S1
            decoded.ctrl.rs2Used := true.B
            decoded.ctrl.aluOp := AluOp.xor
          }
          is(2.U) {
            decoded.rs1 := ScratchReg.S1
            decoded.rs2 := ScratchReg.S0
            decoded.rd := archRd
            decoded.ctrl.rs2Used := true.B
            decoded.ctrl.aluOp := AluOp.sub
          }
        }
      }
      is(FxFunct3.FXDIV) {
        // FXDIV: 1 architectural micro-op, but EX stalls the pipeline for 34
        // cycles while the multi-cycle FxDivUnit produces the quotient.
        decoded.ctrl.illegal := false.B
        decoded.ctrl.rs2Used := true.B
        decoded.ctrl.aluOp := AluOp.fxdiv
      }
    }

    (decoded, last)
  }
}
