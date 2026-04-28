package luma_fix_v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

class CoreMemoryHarness(
    programFile: String,
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
    // MMIO render-log channel: when the core stores to RenderLogAddr the
    // store is suppressed from dmem and instead surfaced one cycle later
    // on these outputs for the test harness to capture.
    val renderLogValid = Output(Bool())
    val renderLogData = Output(UInt(32.W))
    val programDone = Output(Bool())
    val programStatus = Output(UInt(32.W))
  })

  val core = Module(
    new RV32ICore(
      CoreConfig(resetVector = resetVector)
    )
  )
  val imem = Mem(imemWords, UInt(32.W))
  val dmem = SyncReadMem(dmemWords, Vec(4, UInt(8.W)))

  loadMemoryFromFile(imem, programFile)
  loadMemoryFromFile(dmem, programFile)

  val iWordAddr = core.io.imem.req.bits(iAddrWidth + 1, 2)
  core.io.imem.req.ready := true.B
  core.io.imem.resp.valid := true.B
  core.io.imem.resp.bits := imem(iWordAddr)

  val dWordAddr = core.io.dmem.req.bits.addr(dAddrWidth + 1, 2)

  val dWriteVec = Wire(Vec(4, UInt(8.W)))
  dWriteVec(0) := core.io.dmem.req.bits.wData(7, 0)
  dWriteVec(1) := core.io.dmem.req.bits.wData(15, 8)
  dWriteVec(2) := core.io.dmem.req.bits.wData(23, 16)
  dWriteVec(3) := core.io.dmem.req.bits.wData(31, 24)

  // The dmem is a 1-cycle SyncReadMem so a read request issued at cycle T
  // returns its data at cycle T+1. Track an in-flight read with a pending
  // bit and only issue a fresh memory read on the first cycle that the core
  // asserts a new request. This guarantees `resp.bits` always corresponds
  // to the request whose response was just produced, even when the core
  // holds the same request asserted across multiple stall cycles or issues
  // back-to-back loads.
  val readPending = RegInit(false.B)
  val reqValid = core.io.dmem.req.valid
  val isWrite = core.io.dmem.req.bits.write
  val firstReadFire = reqValid && !isWrite && !readPending
  val firstWriteFire = reqValid && isWrite && !readPending

  readPending := firstReadFire

  val dReadVec = dmem.read(dWordAddr, firstReadFire)

  core.io.dmem.req.ready := true.B
  core.io.dmem.resp.valid := readPending
  core.io.dmem.resp.bits := Cat(
    dReadVec(3),
    dReadVec(2),
    dReadVec(1),
    dReadVec(0)
  )

  // MMIO render-log address. Pinned well above any plausible dmem mask so
  // the existing dWordAddr slice keeps decoding the in-window dmem cleanly.
  // Stores to this address never reach dmem; instead they get surfaced on
  // renderLogValid/renderLogData so a host-side test can stream pixels out.
  val RenderLogAddr = "h40000000".U(32.W)
  val ValidationStatusAddr = "h40000004".U(32.W)
  val ValidationDoneAddr = "h40000008".U(32.W)
  val ValidationDoneMagic = "hC001D00D".U(32.W)

  val logHit = firstWriteFire && (core.io.dmem.req.bits.addr === RenderLogAddr)
  val statusHit = firstWriteFire && (core.io.dmem.req.bits.addr === ValidationStatusAddr)
  val doneHit = firstWriteFire && (core.io.dmem.req.bits.addr === ValidationDoneAddr)

  val mmioHit = logHit || statusHit || doneHit

  val programDoneReg = RegInit(false.B)
  val programStatusReg = RegInit(0.U(32.W))

  when(statusHit) {
    programStatusReg := core.io.dmem.req.bits.wData
  }

  when(doneHit && (core.io.dmem.req.bits.wData === ValidationDoneMagic)) {
    programDoneReg := true.B
  }

  when(firstWriteFire && !mmioHit) {
    dmem.write(dWordAddr, dWriteVec, core.io.dmem.req.bits.wMask.asBools)
  }

  // Register one cycle so a peek on the falling edge captures the value
  // that lined up with the store.
  io.renderLogValid := RegNext(logHit, false.B)
  io.renderLogData := RegNext(core.io.dmem.req.bits.wData, 0.U)
  io.programDone := programDoneReg
  io.programStatus := programStatusReg

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
