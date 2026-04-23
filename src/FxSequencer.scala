package luma_fix_v

import chisel3._

// Sits between IF/ID and ID/EX. For non-FX instructions it forwards the
// existing RV32I decode unchanged in a single cycle. For FX instructions it
// drives one micro-op per cycle into the rest of the pipeline by combining
// the held instruction with an internal step counter, asserting `holdFetch`
// while there are more micro-ops to emit so the IF/ID latch is preserved.
//
// Step is advanced only when the emitted micro-op actually commits to the
// ID/EX register (signalled by `advance`). A pipeline flush (taken
// branch/jump) resets the step so a partially-emitted FX instruction is
// abandoned cleanly.
class FxSequencer extends Module {
  val io = IO(new Bundle {
    val inValid = Input(Bool())
    val inInst = Input(UInt(32.W))
    val advance = Input(Bool())
    val flush = Input(Bool())
    val out = Output(new DecodedInstruction)
    val isLastMicroOp = Output(Bool())
    val holdFetch = Output(Bool())
    val isFx = Output(Bool())
    val step = Output(UInt(2.W))
  })

  // FXABS is the longest sequence at 3 micro-ops, so 2 bits suffices.
  val step = RegInit(0.U(2.W))

  val isFx = io.inInst(6, 0) === FxOpcode.CUSTOM_0
  val (fxDecoded, fxLast) = FxDecoder.crack(io.inInst, step)
  val rvDecoded = RV32IDecoder.decode(io.inInst)

  io.out := Mux(isFx, fxDecoded, rvDecoded)
  io.isLastMicroOp := !isFx || fxLast
  // Hold IF/ID stable while there's still more micro-ops left to emit for the
  // current FX instruction. On the cycle the last micro-op is emitted, fetch
  // is allowed to advance so the next instruction can stream in.
  io.holdFetch := isFx && io.inValid && !fxLast
  io.isFx := isFx
  io.step := step

  when(io.flush) {
    step := 0.U
  }.elsewhen(io.advance && io.inValid && isFx) {
    step := Mux(fxLast, 0.U, step + 1.U)
  }
}
