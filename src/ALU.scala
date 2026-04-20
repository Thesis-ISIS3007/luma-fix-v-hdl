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

  io.out := MuxLookup(
    io.op,
    (io.lhs + io.rhs)(31, 0)
  )(
    Seq(
      AluOp.add -> (io.lhs + io.rhs)(31, 0),
      AluOp.sub -> (io.lhs - io.rhs)(31, 0),
      AluOp.and -> (io.lhs & io.rhs),
      AluOp.or -> (io.lhs | io.rhs),
      AluOp.xor -> (io.lhs ^ io.rhs),
      AluOp.sll -> (io.lhs << shiftAmount)(31, 0),
      AluOp.srl -> (io.lhs >> shiftAmount),
      AluOp.sra -> (io.lhs.asSInt >> shiftAmount).asUInt,
      AluOp.slt -> (io.lhs.asSInt < io.rhs.asSInt).asUInt,
      AluOp.sltu -> (io.lhs < io.rhs).asUInt,
      AluOp.copyB -> io.rhs
    )
  )
}
