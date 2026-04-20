package luma_fix_v

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class ISADecoderProbe extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
    val illegal = Output(Bool())
    val rs1Used = Output(Bool())
    val rs2Used = Output(Bool())
    val rdWrite = Output(Bool())
    val aluSrc1PC = Output(Bool())
    val aluSrc2Imm = Output(Bool())
    val aluOp = Output(UInt(4.W))
    val immSel = Output(UInt(3.W))
    val branch = Output(Bool())
    val jump = Output(Bool())
    val jumpReg = Output(Bool())
    val memRead = Output(Bool())
    val memWrite = Output(Bool())
    val memUnsigned = Output(Bool())
    val memSize = Output(UInt(2.W))
    val wbSel = Output(UInt(2.W))
  })

  val dec = RV32IDecoder.decode(io.inst)

  io.illegal := dec.ctrl.illegal
  io.rs1Used := dec.ctrl.rs1Used
  io.rs2Used := dec.ctrl.rs2Used
  io.rdWrite := dec.ctrl.rdWrite
  io.aluSrc1PC := dec.ctrl.aluSrc1PC
  io.aluSrc2Imm := dec.ctrl.aluSrc2Imm
  io.aluOp := dec.ctrl.aluOp.asUInt
  io.immSel := dec.ctrl.immSel.asUInt
  io.branch := dec.ctrl.branch
  io.jump := dec.ctrl.jump
  io.jumpReg := dec.ctrl.jumpReg
  io.memRead := dec.ctrl.memRead
  io.memWrite := dec.ctrl.memWrite
  io.memUnsigned := dec.ctrl.memUnsigned
  io.memSize := dec.ctrl.memSize
  io.wbSel := dec.ctrl.wbSel
}

trait ISATestSupport { this: ChiselSim =>
  protected def runProgram(
      program: Map[Int, Int],
      maxCycles: Int,
      initialData: Map[Int, BigInt] = Map.empty
  ): (Array[BigInt], scala.collection.mutable.Map[Int, BigInt]) = {
    var regs = Array.fill[BigInt](32)(BigInt(0))
    var dataMem = scala.collection.mutable.Map.from(initialData)

    simulate(new RV32ICore()) { c =>
      dataMem = scala.collection.mutable.Map.from(initialData)
      regs = Array.fill[BigInt](32)(BigInt(0))

      c.io.imem.req.ready.poke(true.B)
      c.io.imem.resp.valid.poke(false.B)
      c.io.imem.resp.bits.poke(0.U)

      c.io.dmem.req.ready.poke(true.B)
      c.io.dmem.resp.valid.poke(false.B)
      c.io.dmem.resp.bits.poke(0.U)

      for (_ <- 0 until maxCycles) {
        val pc = c.io.imem.req.bits.peek().litValue.toInt
        val inst = program.getOrElse(pc, iType(0, 0, 0, 0, 0x13))
        c.io.imem.resp.valid.poke(true.B)
        c.io.imem.resp.bits.poke((inst & 0xffffffffL).U(32.W))

        c.io.dmem.resp.valid.poke(false.B)
        c.io.dmem.resp.bits.poke(0.U)

        if (c.io.dmem.req.valid.peek().litToBoolean) {
          val addr = c.io.dmem.req.bits.addr.peek().litValue.toInt & ~0x3
          val write = c.io.dmem.req.bits.write.peek().litToBoolean
          val wdata = c.io.dmem.req.bits.wData.peek().litValue
          val wmask = c.io.dmem.req.bits.wMask.peek().litValue.toInt
          val oldWord = dataMem.getOrElse(addr, BigInt(0))

          if (write) {
            dataMem(addr) = applyMask(oldWord, wdata, wmask)
          } else {
            c.io.dmem.resp.valid.poke(true.B)
            c.io.dmem.resp.bits.poke(dataMem.getOrElse(addr, BigInt(0)).U(32.W))
          }
        }

        c.clock.step()

        if (c.io.debugWbValid.peek().litToBoolean) {
          val rd = c.io.debugWbRd.peek().litValue.toInt
          val data = c.io.debugWbData.peek().litValue & BigInt("FFFFFFFF", 16)
          if (rd != 0) {
            regs(rd) = data
          }
        }
      }

      ()
    }

    (regs, dataMem)
  }
}
