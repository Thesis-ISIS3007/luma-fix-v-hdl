package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

import test_utils._

// Probe wrapping FxSequencer so the test can poke inputs and observe the
// emitted micro-op fields. Bool/UInt-valued enums are exposed as plain UInts
// to keep the poke/expect helpers simple.
class FxSequencerProbe extends Module {
  val io = IO(new Bundle {
    val inValid = Input(Bool())
    val inInst = Input(UInt(32.W))
    val advance = Input(Bool())
    val flush = Input(Bool())
    val rs1 = Output(UInt(6.W))
    val rs2 = Output(UInt(6.W))
    val rd = Output(UInt(6.W))
    val imm = Output(UInt(32.W))
    val aluOp = Output(UInt(4.W))
    val aluSrc2Imm = Output(Bool())
    val rs1Used = Output(Bool())
    val rs2Used = Output(Bool())
    val rdWrite = Output(Bool())
    val illegal = Output(Bool())
    val isFx = Output(Bool())
    val isLastMicroOp = Output(Bool())
    val holdFetch = Output(Bool())
    val step = Output(UInt(2.W))
  })

  val seq = Module(new FxSequencer)
  seq.io.inValid := io.inValid
  seq.io.inInst := io.inInst
  seq.io.advance := io.advance
  seq.io.flush := io.flush

  io.rs1 := seq.io.out.rs1
  io.rs2 := seq.io.out.rs2
  io.rd := seq.io.out.rd
  io.imm := seq.io.out.imm
  io.aluOp := seq.io.out.ctrl.aluOp.asUInt
  io.aluSrc2Imm := seq.io.out.ctrl.aluSrc2Imm
  io.rs1Used := seq.io.out.ctrl.rs1Used
  io.rs2Used := seq.io.out.ctrl.rs2Used
  io.rdWrite := seq.io.out.ctrl.rdWrite
  io.illegal := seq.io.out.ctrl.illegal
  io.isFx := seq.io.isFx
  io.isLastMicroOp := seq.io.isLastMicroOp
  io.holdFetch := seq.io.holdFetch
  io.step := seq.io.step
}

class FxSequencerSpec extends AnyFunSpec with ChiselSim {

  private def driveCommon(c: FxSequencerProbe, inst: Long): Unit = {
    c.io.inValid.poke(true.B)
    c.io.inInst.poke((inst & 0xffffffffL).U(32.W))
    c.io.advance.poke(true.B)
    c.io.flush.poke(false.B)
  }

  describe("FxSequencer") {
    it("forwards non-FX instructions unchanged in a single cycle") {
      simulate(new FxSequencerProbe()) { c =>
        // addi x3, x1, 5
        driveCommon(c, iType(5, 1, 0, 3, 0x13).toLong)
        c.io.isFx.expect(false.B)
        c.io.isLastMicroOp.expect(true.B)
        c.io.holdFetch.expect(false.B)
        c.io.rs1.expect(1.U)
        c.io.rd.expect(3.U)
        c.io.aluOp.expect(AluOp.add.asUInt)
        c.io.aluSrc2Imm.expect(true.B)
        c.io.imm.expect(5.U)
      }
    }

    it("cracks 1-uop FX ops into the right ALU op without holding fetch") {
      simulate(new FxSequencerProbe()) { c =>
        // FXADD x4, x1, x2
        driveCommon(c, fxAdd(4, 1, 2).toLong)
        c.io.isFx.expect(true.B)
        c.io.isLastMicroOp.expect(true.B)
        c.io.holdFetch.expect(false.B)
        c.io.rs1.expect(1.U)
        c.io.rs2.expect(2.U)
        c.io.rd.expect(4.U)
        c.io.aluOp.expect(AluOp.add.asUInt)
        c.io.rs1Used.expect(true.B)
        c.io.rs2Used.expect(true.B)
        c.io.rdWrite.expect(true.B)

        // FXSUB x5, x1, x2
        driveCommon(c, fxSub(5, 1, 2).toLong)
        c.io.aluOp.expect(AluOp.sub.asUInt)

        // FXMUL x6, x1, x2
        driveCommon(c, fxMul(6, 1, 2).toLong)
        c.io.aluOp.expect(AluOp.fxmul.asUInt)

        // FXNEG x7, x1  ==>  sub x7, x0, x1
        driveCommon(c, fxNeg(7, 1).toLong)
        c.io.aluOp.expect(AluOp.sub.asUInt)
        c.io.rs1.expect(0.U)
        c.io.rs2.expect(1.U)
        c.io.rd.expect(7.U)

        // INT2FX x8, x1  ==>  slli x8, x1, 16
        driveCommon(c, int2Fx(8, 1).toLong)
        c.io.aluOp.expect(AluOp.sll.asUInt)
        c.io.aluSrc2Imm.expect(true.B)
        c.io.imm.expect(16.U)
        c.io.rd.expect(8.U)

        // FX2INT x9, x1  ==>  srai x9, x1, 16
        driveCommon(c, fx2Int(9, 1).toLong)
        c.io.aluOp.expect(AluOp.sra.asUInt)
        c.io.aluSrc2Imm.expect(true.B)
        c.io.imm.expect(16.U)
        c.io.rd.expect(9.U)
      }
    }

    it("expands FXABS into three micro-ops with scratch s0/s1 and last-on-step-2") {
      simulate(new FxSequencerProbe()) { c =>
        // FXABS x10, x1
        driveCommon(c, fxAbs(10, 1).toLong)

        // Step 0: srai s0, x1, 31
        c.io.isFx.expect(true.B)
        c.io.step.expect(0.U)
        c.io.isLastMicroOp.expect(false.B)
        c.io.holdFetch.expect(true.B)
        c.io.rs1.expect(1.U)
        c.io.rd.expect("b100000".U) // s0
        c.io.aluOp.expect(AluOp.sra.asUInt)
        c.io.aluSrc2Imm.expect(true.B)
        c.io.imm.expect(31.U)
        c.clock.step()

        // Step 1: xor s1, x1, s0
        c.io.step.expect(1.U)
        c.io.isLastMicroOp.expect(false.B)
        c.io.holdFetch.expect(true.B)
        c.io.rs1.expect(1.U)
        c.io.rs2.expect("b100000".U) // s0
        c.io.rd.expect("b100001".U) // s1
        c.io.aluOp.expect(AluOp.xor.asUInt)
        c.io.aluSrc2Imm.expect(false.B)
        c.clock.step()

        // Step 2: sub x10, s1, s0
        c.io.step.expect(2.U)
        c.io.isLastMicroOp.expect(true.B)
        c.io.holdFetch.expect(false.B)
        c.io.rs1.expect("b100001".U) // s1
        c.io.rs2.expect("b100000".U) // s0
        c.io.rd.expect(10.U)
        c.io.aluOp.expect(AluOp.sub.asUInt)
        c.clock.step()

        // After last micro-op the step counter resets so the next instruction
        // starts cleanly at step 0.
        c.io.step.expect(0.U)
      }
    }

    it("flush during a multi-uop sequence resets step immediately") {
      simulate(new FxSequencerProbe()) { c =>
        driveCommon(c, fxAbs(10, 1).toLong)
        c.clock.step() // step now 1
        c.io.step.expect(1.U)

        // Pulse flush; step should drop back to 0 next cycle even if the FX
        // instruction is still being driven on the input.
        c.io.flush.poke(true.B)
        c.clock.step()
        c.io.step.expect(0.U)
      }
    }

    it("does not advance step when the consumer cannot accept the micro-op") {
      simulate(new FxSequencerProbe()) { c =>
        c.io.inValid.poke(true.B)
        c.io.inInst.poke((fxAbs(10, 1) & 0xffffffffL).U(32.W))
        c.io.advance.poke(false.B) // pipeline stalled
        c.io.flush.poke(false.B)
        c.io.step.expect(0.U)
        c.clock.step()
        c.io.step.expect(0.U)
        c.clock.step()
        c.io.step.expect(0.U)

        // Releasing the stall lets the sequence progress one step at a time.
        c.io.advance.poke(true.B)
        c.clock.step()
        c.io.step.expect(1.U)
      }
    }

    it("FXDIV is reserved and decoded as illegal in v1") {
      simulate(new FxSequencerProbe()) { c =>
        driveCommon(c, fxDiv(11, 1, 2).toLong)
        c.io.isFx.expect(true.B)
        c.io.illegal.expect(true.B)
        c.io.rdWrite.expect(false.B)
      }
    }
  }
}
