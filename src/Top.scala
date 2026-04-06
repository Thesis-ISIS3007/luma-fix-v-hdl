package prototype

import chisel3._

class LumaFixV extends Module {
	val io = IO(new Bundle {
		val imem = new InstrBusIO
		val dmem = new DataBusIO
		val debugPc = Output(UInt(32.W))
		val debugWbValid = Output(Bool())
		val debugWbRd = Output(UInt(5.W))
		val debugWbData = Output(UInt(32.W))
	})

	val core = Module(new RV32ICore())

	core.io.imem.reqReady := io.imem.reqReady
	core.io.imem.respValid := io.imem.respValid
	core.io.imem.respData := io.imem.respData
	io.imem.reqValid := core.io.imem.reqValid
	io.imem.reqAddr := core.io.imem.reqAddr

	core.io.dmem.reqReady := io.dmem.reqReady
	core.io.dmem.respValid := io.dmem.respValid
	core.io.dmem.respData := io.dmem.respData
	io.dmem.reqValid := core.io.dmem.reqValid
	io.dmem.reqAddr := core.io.dmem.reqAddr
	io.dmem.reqWrite := core.io.dmem.reqWrite
	io.dmem.reqWData := core.io.dmem.reqWData
	io.dmem.reqWMask := core.io.dmem.reqWMask

	io.debugPc := core.io.debugPc
	io.debugWbValid := core.io.debugWbValid
	io.debugWbRd := core.io.debugWbRd
	io.debugWbData := core.io.debugWbData
}
