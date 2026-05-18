package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class DecoderProbe extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
    val illegal = Output(Bool())
    val rs1Used = Output(Bool())
    val rs2Used = Output(Bool())
    val rdWrite = Output(Bool())
    val aluSrc1PC = Output(Bool())
    val aluSrc2Imm = Output(Bool())
    val aluOp = Output(UInt(4.W))
    val branch = Output(Bool())
    val jump = Output(Bool())
    val jumpReg = Output(Bool())
    val memRead = Output(Bool())
    val memWrite = Output(Bool())
    val memUnsigned = Output(Bool())
    val memSize = Output(UInt(2.W))
    val wbSel = Output(UInt(2.W))
    val immI = Output(UInt(32.W))
  })

  val dec = RV32IDecoder.decode(io.inst)

  io.illegal := dec.ctrl.illegal
  io.rs1Used := dec.ctrl.rs1Used
  io.rs2Used := dec.ctrl.rs2Used
  io.rdWrite := dec.ctrl.rdWrite
  io.aluSrc1PC := dec.ctrl.aluSrc1PC
  io.aluSrc2Imm := dec.ctrl.aluSrc2Imm
  io.aluOp := dec.ctrl.aluOp.asUInt
  io.branch := dec.ctrl.branch
  io.jump := dec.ctrl.jump
  io.jumpReg := dec.ctrl.jumpReg
  io.memRead := dec.ctrl.memRead
  io.memWrite := dec.ctrl.memWrite
  io.memUnsigned := dec.ctrl.memUnsigned
  io.memSize := dec.ctrl.memSize
  io.wbSel := dec.ctrl.wbSel
  io.immI := ImmGen.i(io.inst)
}

class DecoderSpec extends AnyFunSpec with ChiselSim {
  describe("RV32IDecoder") {
    it("decodes addi and load/store class controls") {
      simulate(new DecoderProbe()) { c =>
        c.io.inst.poke("h00508193".U) // addi x3, x1, 5
        c.clock.step()
        c.io.illegal.expect(false.B)
        c.io.rs1Used.expect(true.B)
        c.io.rs2Used.expect(false.B)
        c.io.rdWrite.expect(true.B)
        c.io.aluSrc2Imm.expect(true.B)
        c.io.aluOp.expect(AluOp.add.asUInt)
        c.io.immI.expect(5.U)

        c.io.inst.poke("h00812203".U) // lw x4, 8(x2)
        c.clock.step()
        c.io.memRead.expect(true.B)
        c.io.memWrite.expect(false.B)
        c.io.memSize.expect(2.U)
        c.io.wbSel.expect(RV32IDecode.WbSelMem)

        c.io.inst.poke("h00512623".U) // sw x5, 12(x2)
        c.clock.step()
        c.io.memRead.expect(false.B)
        c.io.memWrite.expect(true.B)
        c.io.memSize.expect(2.U)
      }
    }

    it("decodes control-flow and rejects unknown opcodes") {
      simulate(new DecoderProbe()) { c =>
        c.io.inst.poke("h00208863".U) // beq x1, x2, +16
        c.clock.step()
        c.io.branch.expect(true.B)
        c.io.jump.expect(false.B)

        c.io.inst.poke("h004100E7".U) // jalr x1, 4(x2)
        c.clock.step()
        c.io.jump.expect(true.B)
        c.io.jumpReg.expect(true.B)
        c.io.wbSel.expect(RV32IDecode.WbSelPC4)

        c.io.inst.poke(0.U)
        c.clock.step()
        c.io.illegal.expect(true.B)
      }
    }
  }
}
