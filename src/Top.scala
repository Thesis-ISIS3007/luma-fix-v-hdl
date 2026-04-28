package luma_fix_v

import chisel3._

class LumaFixVIO extends Bundle {
  val imem = new InstrBusIO
  val dmem = new DataBusIO
}

class LumaFixVDebugIO extends LumaFixVIO {
  val debugPC = Output(UInt(32.W))
  val debugWbValid = Output(Bool())
  val debugWbRd = Output(UInt(5.W))
  val debugWbData = Output(UInt(32.W))
}

class LumaFixV(cfg: CoreConfig = CoreConfig()) extends Module {
  val io = if (cfg.debug) IO(new LumaFixVDebugIO) else IO(new LumaFixVIO)

  val core = Module(new RV32ICore(cfg))

  core.io.imem <> io.imem
  core.io.dmem <> io.dmem

  if (cfg.debug) {
    val debugIO = io.asInstanceOf[LumaFixVDebugIO]
    debugIO.debugPC := core.io.debugPC
    debugIO.debugWbValid := core.io.debugWbValid
    debugIO.debugWbRd := core.io.debugWbRd
    debugIO.debugWbData := core.io.debugWbData
  }
}
