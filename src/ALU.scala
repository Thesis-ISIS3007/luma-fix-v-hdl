package luma_fix_v

import chisel3._
import chisel3.util._

class Alu extends Module {
  val io = IO(new Bundle {
    val op = Input(AluOp())
    val lhs = Input(UInt(32.W))
    val rhs = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  val shiftAmount = io.rhs(4, 0)

  // 16Q16 fixed-point multiply: signed 32x32 -> 64-bit product, arithmetic
  // shift right by 16, truncate to 32 bits. Wrap-around on overflow.
  val fxmulProduct = io.lhs.asSInt * io.rhs.asSInt
  val fxmulOut = (fxmulProduct >> 16).asUInt(31, 0)
  val subResult = (io.lhs - io.rhs)(31, 0)
  val sltResult = subResult(31).asUInt
  val sltuResult = (!io.lhs(31) && io.rhs(31)).asUInt |
    ((io.lhs(31) === io.rhs(31)) && subResult(31)).asUInt

  io.out := MuxLookup(
    io.op,
    (io.lhs + io.rhs)(31, 0)
  )(
    Seq(
      AluOp.add -> (io.lhs + io.rhs)(31, 0),
      AluOp.sub -> subResult,
      AluOp.and -> (io.lhs & io.rhs),
      AluOp.or -> (io.lhs | io.rhs),
      AluOp.xor -> (io.lhs ^ io.rhs),
      AluOp.sll -> (io.lhs << shiftAmount)(31, 0),
      AluOp.srl -> (io.lhs >> shiftAmount),
      AluOp.sra -> (io.lhs.asSInt >> shiftAmount).asUInt,
      AluOp.slt -> sltResult,
      AluOp.sltu -> sltuResult,
      AluOp.copyB -> io.rhs,
      AluOp.fxmul -> fxmulOut,
      // FXDIV is computed by the multi-cycle FxDivUnit in the EX stage. The
      // ALU output for fxdiv is unused (the core muxes in the divider's
      // result instead); we still produce a stable 0 so simulation waveforms
      // are clean while the pipeline is stalled.
      AluOp.fxdiv -> 0.U(32.W)
    )
  )
}
