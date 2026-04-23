package luma_fix_v

import chisel3._

// Register file with two read ports and one write port. Now uses 6-bit
// addresses: bit 5 == 0 selects an architectural register x0..x31 (with x0
// hardwired to zero), bit 5 == 1 selects one of four sequencer scratch
// entries (s0..s3) used by multi-uop FX expansions. Scratch slots are not
// visible to RV32I programs and have no zero-hardwired entry.
class RegFile extends Module {
  val io = IO(new Bundle {
    val rs1Addr = Input(UInt(6.W))
    val rs2Addr = Input(UInt(6.W))
    val rs1Data = Output(UInt(32.W))
    val rs2Data = Output(UInt(32.W))
    val rdAddr = Input(UInt(6.W))
    val rdData = Input(UInt(32.W))
    val rdWrite = Input(Bool())
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val scratch = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  private def isScratch(addr: UInt): Bool = addr(5)
  private def archIdx(addr: UInt): UInt = addr(4, 0)
  private def scratchIdx(addr: UInt): UInt = addr(1, 0)

  private def writeAllowed(addr: UInt): Bool =
    isScratch(addr) || (archIdx(addr) =/= 0.U)

  private def readPort(addr: UInt): UInt = {
    val bypass = io.rdWrite && writeAllowed(io.rdAddr) && (io.rdAddr === addr)
    val archRead = Mux(archIdx(addr) === 0.U, 0.U, regs(archIdx(addr)))
    val scratchRead = scratch(scratchIdx(addr))
    val stored = Mux(isScratch(addr), scratchRead, archRead)
    Mux(bypass, io.rdData, stored)
  }

  io.rs1Data := readPort(io.rs1Addr)
  io.rs2Data := readPort(io.rs2Addr)

  when(io.rdWrite && writeAllowed(io.rdAddr)) {
    when(isScratch(io.rdAddr)) {
      scratch(scratchIdx(io.rdAddr)) := io.rdData
    }.otherwise {
      regs(archIdx(io.rdAddr)) := io.rdData
    }
  }
}
