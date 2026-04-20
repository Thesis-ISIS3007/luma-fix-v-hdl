package luma_fix_v

import chisel3._

class LumaFixV extends Module {
  val io = IO(new Bundle {
    val imem = new InstrBusIO
    val dmem = new DataBusIO
    val debugPC = Output(UInt(32.W))
    val debugWbValid = Output(Bool())
    val debugWbRd = Output(UInt(5.W))
    val debugWbData = Output(UInt(32.W))
  })

  val core = Module(new RV32ICore())

  core.io.imem <> io.imem
  core.io.dmem <> io.dmem

  io.debugPC := core.io.debugPC
  io.debugWbValid := core.io.debugWbValid
  io.debugWbRd := core.io.debugWbRd
  io.debugWbData := core.io.debugWbData
}
