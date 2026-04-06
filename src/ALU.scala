package prototype

import chisel3._
import chisel3.util._

class Alu extends Module {
  val io = IO(new Bundle {
    val op = Input(AluOp())
    val lhs = Input(UInt(32.W))
    val rhs = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  val shamt = io.rhs(4, 0)

  io.out := MuxLookup(
    io.op.asUInt,
    (io.lhs + io.rhs)(31, 0)
  )(
    Seq(
      AluOp.add.asUInt -> (io.lhs + io.rhs)(31, 0),
      AluOp.sub.asUInt -> (io.lhs - io.rhs)(31, 0),
      AluOp.and.asUInt -> (io.lhs & io.rhs),
      AluOp.or.asUInt -> (io.lhs | io.rhs),
      AluOp.xor.asUInt -> (io.lhs ^ io.rhs),
      AluOp.sll.asUInt -> (io.lhs << shamt)(31, 0),
      AluOp.srl.asUInt -> (io.lhs >> shamt),
      AluOp.sra.asUInt -> (io.lhs.asSInt >> shamt).asUInt,
      AluOp.slt.asUInt -> (io.lhs.asSInt < io.rhs.asSInt).asUInt,
      AluOp.sltu.asUInt -> (io.lhs < io.rhs).asUInt,
      AluOp.copyB.asUInt -> io.rhs
    )
  )
}
