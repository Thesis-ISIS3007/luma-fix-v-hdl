package luma_fix_v

import chisel3._

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

  val step = RegInit(0.U(2.W))

  val isFx = io.inInst(6, 0) === FxOpcode.CUSTOM_0
  val (fxDecoded, fxLast) = FxDecoder.crack(io.inInst, step)
  val rvDecoded = RV32IDecoder.decode(io.inInst)

  io.out := Mux(isFx, fxDecoded, rvDecoded)
  io.isLastMicroOp := !isFx || fxLast
  io.holdFetch := isFx && io.inValid && !fxLast
  io.isFx := isFx
  io.step := step

  when(io.flush) {
    step := 0.U
  }.elsewhen(io.advance && io.inValid && isFx) {
    step := Mux(fxLast, 0.U, step + 1.U)
  }
}
