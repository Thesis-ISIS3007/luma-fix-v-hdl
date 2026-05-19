package luma_fix_v

import chisel3._

/** Per CUSTOM-0 macro in IF/ID (funct3/funct7); mutually exclusive per cycle.
  */
class Custom0OpProfile extends Bundle {
  val fxAdd = Output(Bool())
  val fxSub = Output(Bool())
  val fxMul = Output(Bool())
  val fxNeg = Output(Bool())
  val int2Fx = Output(Bool())
  val fx2Int = Output(Bool())
  val fxAbs = Output(Bool())
  val fxDiv = Output(Bool())
}

/** Per-cycle microarchitecture probe outputs for simulation profiling. */
class UarchProfileIO extends Bundle {
  val retire = Output(Bool())
  val fetchFire = Output(Bool())
  val rawStall = Output(Bool())
  val exBusy = Output(Bool())
  val fxHoldFetch = Output(Bool())
  val memStall = Output(Bool())
  val pipeStall = Output(Bool())
  val flush = Output(Bool())

  /** IF/ID holds a CUSTOM-0 (0x0B) FX macro-op, including multi-uop crack. */
  val custom0Active = Output(Bool())
  val custom0Ops = new Custom0OpProfile
}
