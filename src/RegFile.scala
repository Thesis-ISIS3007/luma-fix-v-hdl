package luma_fix_v

import chisel3._

class RegFile extends Module {
  val io = IO(new Bundle {
    val rs1Addr = Input(UInt(5.W))
    val rs2Addr = Input(UInt(5.W))
    val rs1Data = Output(UInt(32.W))
    val rs2Data = Output(UInt(32.W))
    val rdAddr = Input(UInt(5.W))
    val rdData = Input(UInt(32.W))
    val rdWrite = Input(Bool())
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  io.rs1Data := Mux(io.rs1Addr === 0.U, 0.U, regs(io.rs1Addr))
  io.rs2Data := Mux(io.rs2Addr === 0.U, 0.U, regs(io.rs2Addr))

  when(io.rdWrite && io.rdAddr =/= 0.U) {
    regs(io.rdAddr) := io.rdData
  }
}
