package luma_fix_v

import chisel3._
import chisel3.util._

class InstrBusIO extends Bundle {
  val req = Decoupled(UInt(32.W))
  val resp = Flipped(Valid(UInt(32.W)))
}

class DataReq extends Bundle {
  val addr = UInt(32.W)
  val write = Bool()
  val wData = UInt(32.W)
  val wMask = UInt(4.W)
}

class DataBusIO extends Bundle {
  val req = Decoupled(new DataReq)
  val resp = Flipped(Valid(UInt(32.W)))
}

class IfIdPipe extends Bundle {
  val pc = UInt(32.W)
  val inst = UInt(32.W)
}

class IdExPipe extends Bundle {
  val pc = UInt(32.W)
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
  val rs1Val = UInt(32.W)
  val rs2Val = UInt(32.W)
  val imm = UInt(32.W)
  val funct3 = UInt(3.W)
  val ctrl = new DecodeSignals
}

class ExMemPipe extends Bundle {
  val pc4 = UInt(32.W)
  val rd = UInt(5.W)
  val aluRes = UInt(32.W)
  val rs2Val = UInt(32.W)
  val funct3 = UInt(3.W)
  val ctrl = new DecodeSignals
}

class MemWbPipe extends Bundle {
  val rd = UInt(5.W)
  val wbData = UInt(32.W)
  val rdWrite = Bool()
}

object BranchUnit {
  def apply(funct3: UInt, rs1: UInt, rs2: UInt): Bool = {
    MuxLookup(funct3, false.B)(
      Seq(
        RV32IBranchFunct3.BEQ -> (rs1 === rs2),
        RV32IBranchFunct3.BNE -> (rs1 =/= rs2),
        RV32IBranchFunct3.BLT -> (rs1.asSInt < rs2.asSInt),
        RV32IBranchFunct3.BGE -> (rs1.asSInt >= rs2.asSInt),
        RV32IBranchFunct3.BLTU -> (rs1 < rs2),
        RV32IBranchFunct3.BGEU -> (rs1 >= rs2)
      )
    )
  }
}

object ForwardingUnit {
  def apply(
      regVal: UInt,
      regAddr: UInt,
      exCanFwd: Bool,
      exRd: UInt,
      exData: UInt,
      wbCanFwd: Bool,
      wbRd: UInt,
      wbData: UInt
  ): UInt = {
    val out = Wire(UInt(32.W))
    out := regVal
    when(exCanFwd && (exRd === regAddr)) {
      out := exData
    }.elsewhen(wbCanFwd && (wbRd === regAddr)) {
      out := wbData
    }
    out
  }
}

object HazardUnit {
  def apply(
      idExValid: Bool,
      idExMemRead: Bool,
      idExRd: UInt,
      rs1Used: Bool,
      rs1: UInt,
      rs2Used: Bool,
      rs2: UInt
  ): Bool =
    idExValid && idExMemRead && (idExRd =/= 0.U) &&
      ((rs1Used && (rs1 === idExRd)) || (rs2Used && (rs2 === idExRd)))
}

object JumpUnit {
  def apply(jumpReg: Bool, rs1: UInt, pc: UInt, imm: UInt): UInt = {
    val raw = Mux(jumpReg, rs1, pc) + imm
    Mux(jumpReg, Cat(raw(31, 1), 0.U(1.W)), raw)
  }
}

object StoreUnit {
  def apply(addr: UInt, data: UInt, memSize: UInt): (UInt, UInt) = {
    val shift = addr(1, 0)
    val wData = (data << (shift << 3))(31, 0)
    val wMask = MuxLookup(memSize, "b1111".U)(
      Seq(
        RV32IMemSize.Byte -> (1.U(4.W) << shift),
        RV32IMemSize.Half -> Mux(shift(1), "b1100".U, "b0011".U),
        RV32IMemSize.Word -> "b1111".U
      )
    )
    (wData, wMask)
  }
}

object LoadUnit {
  def apply(rawData: UInt, addr: UInt, funct3: UInt, unsigned: Bool): UInt = {
    val shift = addr(1, 0)
    val byte = (rawData >> (shift << 3))(7, 0)
    val half = (rawData >> (shift(1) << 4))(15, 0)
    val out = Wire(UInt(32.W))
    out := rawData
    switch(funct3) {
      is(RV32IMemFunct3.B) { out := Cat(Fill(24, byte(7) && !unsigned), byte) }
      is(RV32IMemFunct3.H) { out := Cat(Fill(16, half(15) && !unsigned), half) }
      is(RV32IMemFunct3.W) { out := rawData }
      is(RV32IMemFunct3.BU) { out := Cat(0.U(24.W), byte) }
      is(RV32IMemFunct3.HU) { out := Cat(0.U(16.W), half) }
    }
    out
  }
}

class RV32ICore(resetVector: BigInt = 0) extends Module {
  val io = IO(new Bundle {
    val imem = new InstrBusIO
    val dmem = new DataBusIO
    val debugPC = Output(UInt(32.W))
    val debugWbValid = Output(Bool())
    val debugWbRd = Output(UInt(5.W))
    val debugWbData = Output(UInt(32.W))
  })

  val regFile = Module(new RegFile)
  val alu = Module(new Alu)

  val pc = RegInit(resetVector.U(32.W))

  val ifId = RegInit(0.U.asTypeOf(new IfIdPipe))
  val ifIdValid = RegInit(false.B)

  val idEx = RegInit(0.U.asTypeOf(new IdExPipe))
  val idExValid = RegInit(false.B)

  val exMem = RegInit(0.U.asTypeOf(new ExMemPipe))
  val exMemValid = RegInit(false.B)

  val memWb = RegInit(0.U.asTypeOf(new MemWbPipe))
  val memWbValid = RegInit(false.B)

  val fetchedInst = io.imem.resp.bits
  val decode = RV32IDecoder.decode(ifId.inst)

  regFile.io.rs1Addr := decode.rs1
  regFile.io.rs2Addr := decode.rs2
  regFile.io.rdAddr := memWb.rd
  regFile.io.rdData := memWb.wbData
  regFile.io.rdWrite := memWbValid && memWb.rdWrite

  val loadUseHazard = HazardUnit(
    idExValid,
    idEx.ctrl.memRead,
    idEx.rd,
    decode.ctrl.rs1Used,
    decode.rs1,
    decode.ctrl.rs2Used,
    decode.rs2
  )

  val exCanForward =
    exMemValid && exMem.ctrl.rdWrite && !exMem.ctrl.memRead && (exMem.rd =/= 0.U)
  val wbCanForward = memWbValid && memWb.rdWrite && (memWb.rd =/= 0.U)

  val exRs1 = ForwardingUnit(
    idEx.rs1Val,
    idEx.rs1,
    exCanForward,
    exMem.rd,
    exMem.aluRes,
    wbCanForward,
    memWb.rd,
    memWb.wbData
  )
  val exRs2 = ForwardingUnit(
    idEx.rs2Val,
    idEx.rs2,
    exCanForward,
    exMem.rd,
    exMem.aluRes,
    wbCanForward,
    memWb.rd,
    memWb.wbData
  )

  val branchTaken = idEx.ctrl.branch && BranchUnit(idEx.funct3, exRs1, exRs2)

  val exJumpTaken = idEx.ctrl.jump || (idEx.ctrl.branch && branchTaken)
  val jumpTarget = JumpUnit(idEx.ctrl.jumpReg, exRs1, idEx.pc, idEx.imm)

  val exOp1 = Mux(idEx.ctrl.aluSrc1PC, idEx.pc, exRs1)
  val exOp2 = Mux(idEx.ctrl.aluSrc2Imm, idEx.imm, exRs2)

  alu.io.op := idEx.ctrl.aluOp
  alu.io.lhs := exOp1
  alu.io.rhs := exOp2

  val (storeWData, storeMask) =
    StoreUnit(exMem.aluRes, exMem.rs2Val, exMem.ctrl.memSize)

  val loadData = LoadUnit(
    io.dmem.resp.bits,
    exMem.aluRes,
    exMem.funct3,
    exMem.ctrl.memUnsigned
  )

  val memAccess = exMem.ctrl.memRead || exMem.ctrl.memWrite
  val memRespNeeded = exMem.ctrl.memRead
  val memReqAccepted = !memAccess || io.dmem.req.ready
  val memRespReady = !memRespNeeded || io.dmem.resp.valid
  val memStageReady = !exMemValid || (memReqAccepted && memRespReady)

  val fetchBlocked = loadUseHazard || !memStageReady
  val fetchFire = io.imem.resp.valid && !fetchBlocked

  val nextPC = Mux(exJumpTaken && idExValid, jumpTarget, pc + 4.U)
  when(exJumpTaken && idExValid) {
    pc := jumpTarget
  }.elsewhen(fetchFire) {
    pc := pc + 4.U
  }

  when(!fetchBlocked) {
    when(fetchFire) {
      ifId.pc := pc
      ifId.inst := fetchedInst
      ifIdValid := true.B
    }
    when(exJumpTaken && idExValid) {
      ifIdValid := false.B
    }
  }

  val injectBubble = loadUseHazard || (exJumpTaken && idExValid)
  when(memStageReady) {
    when(injectBubble) {
      idExValid := false.B
    }.otherwise {
      idExValid := ifIdValid
      when(ifIdValid) {
        idEx.pc := ifId.pc
        idEx.rs1 := decode.rs1
        idEx.rs2 := decode.rs2
        idEx.rd := decode.rd
        idEx.rs1Val := regFile.io.rs1Data
        idEx.rs2Val := regFile.io.rs2Data
        idEx.imm := ImmGen.select(ifId.inst, decode.ctrl.immSel)
        idEx.funct3 := decode.funct3
        idEx.ctrl := decode.ctrl
      }
    }
  }

  when(memStageReady) {
    exMemValid := idExValid
    when(idExValid) {
      exMem.pc4 := idEx.pc + 4.U
      exMem.rd := idEx.rd
      exMem.aluRes := alu.io.out
      exMem.rs2Val := exRs2
      exMem.funct3 := idEx.funct3
      exMem.ctrl := idEx.ctrl
    }
  }

  val wbData = MuxLookup(
    exMem.ctrl.wbSel,
    exMem.aluRes
  )(
    Seq(
      RV32IDecode.WbSelAlu -> exMem.aluRes,
      RV32IDecode.WbSelMem -> loadData,
      RV32IDecode.WbSelPC4 -> exMem.pc4
    )
  )

  when(memStageReady) {
    memWbValid := exMemValid
    memWb.rd := exMem.rd
    memWb.wbData := wbData
    memWb.rdWrite := exMem.ctrl.rdWrite
  }

  io.imem.req.valid := true.B
  io.imem.req.bits := pc

  io.dmem.req.valid := exMemValid && (exMem.ctrl.memRead || exMem.ctrl.memWrite)
  io.dmem.req.bits.addr := exMem.aluRes
  io.dmem.req.bits.write := exMem.ctrl.memWrite
  io.dmem.req.bits.wData := storeWData
  io.dmem.req.bits.wMask := storeMask

  io.debugPC := pc
  io.debugWbValid := memWbValid && memWb.rdWrite
  io.debugWbRd := memWb.rd
  io.debugWbData := memWb.wbData

  dontTouch(nextPC)
}
