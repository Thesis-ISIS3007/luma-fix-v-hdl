package luma_fix_v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

class CoreMemoryHarness(
    programFile: String,
    programFileType: MemoryLoadFileType.FileType = MemoryLoadFileType.Hex,
    imemWords: Int = 1024,
    dmemWords: Int = 1024,
    resetVector: BigInt = 0
) extends Module {
  require(imemWords > 0, "imemWords must be > 0")
  require(dmemWords > 0, "dmemWords must be > 0")

  private val iAddrWidth = log2Ceil(imemWords)
  private val dAddrWidth = log2Ceil(dmemWords)

  val io = IO(new Bundle {
    val imemPeekAddr = Input(UInt(32.W))
    val imemPeekData = Output(UInt(32.W))
    val dmemPeekAddr = Input(UInt(32.W))
    val dmemPeekData = Output(UInt(32.W))
    val debugPC = Output(UInt(32.W))
    val debugWbValid = Output(Bool())
    val debugWbRd = Output(UInt(5.W))
    val debugWbData = Output(UInt(32.W))
  })

  val core = Module(new RV32ICore(resetVector))
  val imem = Mem(imemWords, UInt(32.W))
  val dmem = SyncReadMem(dmemWords, Vec(4, UInt(8.W)))

  loadMemoryFromFile(imem, programFile, programFileType)

  val iWordAddr = core.io.imem.req.bits(iAddrWidth + 1, 2)
  core.io.imem.req.ready := true.B
  core.io.imem.resp.valid := true.B
  core.io.imem.resp.bits := imem(iWordAddr)

  val dWordAddr = core.io.dmem.req.bits.addr(dAddrWidth + 1, 2)
  val dIsRead = core.io.dmem.req.valid && !core.io.dmem.req.bits.write
  val dReadVec = dmem.read(dWordAddr, dIsRead)

  val dWriteVec = Wire(Vec(4, UInt(8.W)))
  dWriteVec(0) := core.io.dmem.req.bits.wData(7, 0)
  dWriteVec(1) := core.io.dmem.req.bits.wData(15, 8)
  dWriteVec(2) := core.io.dmem.req.bits.wData(23, 16)
  dWriteVec(3) := core.io.dmem.req.bits.wData(31, 24)

  core.io.dmem.req.ready := true.B
  core.io.dmem.resp.valid := RegNext(dIsRead, false.B)
  core.io.dmem.resp.bits := Cat(
    dReadVec(3),
    dReadVec(2),
    dReadVec(1),
    dReadVec(0)
  )

  when(core.io.dmem.req.valid && core.io.dmem.req.bits.write) {
    dmem.write(dWordAddr, dWriteVec, core.io.dmem.req.bits.wMask.asBools)
  }

  val peekWordAddr = io.dmemPeekAddr(dAddrWidth + 1, 2)
  val peekVec = dmem.read(peekWordAddr, true.B)
  io.dmemPeekData := Cat(peekVec(3), peekVec(2), peekVec(1), peekVec(0))

  val iPeekWordAddr = io.imemPeekAddr(iAddrWidth + 1, 2)
  io.imemPeekData := imem(iPeekWordAddr)

  io.debugPC := core.io.debugPC
  io.debugWbValid := core.io.debugWbValid
  io.debugWbRd := core.io.debugWbRd
  io.debugWbData := core.io.debugWbData
}
