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
  // 6-bit register addresses (bit 5 selects scratch). See RegFile.scala.
  val rs1 = UInt(6.W)
  val rs2 = UInt(6.W)
  val rd = UInt(6.W)
  val rs1Val = UInt(32.W)
  val rs2Val = UInt(32.W)
  val imm = UInt(32.W)
  val funct3 = UInt(3.W)
  val ctrl = new DecodeSignals
}

class ExMemPipe extends Bundle {
  val pc4 = UInt(32.W)
  val rd = UInt(6.W)
  val aluRes = UInt(32.W)
  val rs2Val = UInt(32.W)
  val funct3 = UInt(3.W)
  val ctrl = new DecodeSignals
}

class MemWbPipe extends Bundle {
  val rd = UInt(6.W)
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
    // Loads only ever target architectural rd, so a 6-bit compare is safe:
    // a scratch consumer (bit 5 = 1) cannot match a load producer (bit 5 = 0).
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
  def apply(xlen: Int, addr: UInt, data: UInt, memSize: UInt): (UInt, UInt) = {
    require(xlen == 32, s"StoreUnit currently supports RV32 only, got xlen=$xlen")
    val shift = addr(1, 0)
    val wData = (data << (shift << 3))(xlen - 1, 0)
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
  def apply(
      xlen: Int,
      rawData: UInt,
      addr: UInt,
      funct3: UInt,
      unsigned: Bool
  ): UInt = {
    require(xlen == 32, s"LoadUnit currently supports RV32 only, got xlen=$xlen")
    val shift = addr(1, 0)
    val byte = (rawData >> (shift << 3))(7, 0)
    val half = (rawData >> (shift(1) << 4))(15, 0)
    val out = Wire(UInt(xlen.W))
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

class RV32ICore(cfg: CoreConfig = CoreConfig()) extends Module {
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
  val seq = Module(new FxSequencer)
  val divUnit = Module(new FxDivUnit)

  val pc = RegInit(cfg.resetVector.U(cfg.xlen.W))

  val ifId = RegInit(0.U.asTypeOf(new IfIdPipe))
  val ifIdValid = RegInit(false.B)

  val idEx = RegInit(0.U.asTypeOf(new IdExPipe))
  val idExValid = RegInit(false.B)

  val exMem = RegInit(0.U.asTypeOf(new ExMemPipe))
  val exMemValid = RegInit(false.B)

  val memWb = RegInit(0.U.asTypeOf(new MemWbPipe))
  val memWbValid = RegInit(false.B)

  val fetchedInst = io.imem.resp.bits
  // Decode goes through the FX sequencer: non-FX instructions pass straight
  // through; FX instructions are cracked into one micro-op per cycle while
  // `seq.io.holdFetch` keeps the IF/ID latch stable.
  seq.io.inValid := ifIdValid
  seq.io.inInst := ifId.inst
  val decode = seq.io.out

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

  // FXDIV uses the multi-cycle divider parked next to the ALU. The divider
  // captures its operands on `start`, so even though `exOp1`/`exOp2` may be
  // forwarded values, they're stable while EX is stalled (the upstream stages
  // are frozen by `exBusy`). `done` is a one-cycle pulse, so we latch the
  // quotient locally to handle the case where MEM is also stalled and the
  // result has to wait several extra cycles before it can move into EX/MEM.
  val isFxDivInEx = idExValid && (idEx.ctrl.aluOp === AluOp.fxdiv)
  val divResultLatched = RegInit(false.B)
  val divResultData = Reg(UInt(32.W))
  val divResultReady = divResultLatched || divUnit.io.done
  val effectiveDivOut = Mux(divUnit.io.done, divUnit.io.out, divResultData)

  // Only kick off a new FXDIV when no quotient is currently held and the unit
  // is idle. This guarantees back-to-back FXDIVs each get a fresh sBusy run.
  divUnit.io.start := isFxDivInEx && !divResultLatched && !divUnit.io.busy
  divUnit.io.lhs := exOp1
  divUnit.io.rhs := exOp2

  // While FXDIV occupies EX without a result yet, stall the rest of the
  // pipeline. Once a quotient is available (either fresh from `done` or held
  // in `divResultLatched`), the FXDIV is allowed to move into EX/MEM.
  val exBusy = isFxDivInEx && !divResultReady

  // Use the divider's quotient instead of the ALU result for FXDIV.
  val exAluResult = Mux(isFxDivInEx, effectiveDivOut, alu.io.out)

  val (storeWData, storeMask) =
    StoreUnit(cfg.xlen, exMem.aluRes, exMem.rs2Val, exMem.ctrl.memSize)

  val loadData = LoadUnit(
    cfg.xlen,
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

  // pipeReady gates the ID->EX and EX->MEM register updates. It requires both
  // the downstream MEM stage to be drainable AND any multi-cycle EX op (FXDIV)
  // to have produced its result this cycle. memWb still gets fed straight
  // from exMem so an in-flight load can drain even while FXDIV is stalling.
  val pipeReady = memStageReady && !exBusy

  // While the FX sequencer is mid-sequence it holds fetch so the same FX
  // instruction stays latched in IF/ID and the next micro-op can be cracked.
  // FXDIV adds another fetch-block reason via pipeReady.
  val fetchBlocked = loadUseHazard || !pipeReady || seq.io.holdFetch
  val fetchFire = io.imem.resp.valid && !fetchBlocked

  val flush = exJumpTaken && idExValid

  val nextPC = Mux(flush, jumpTarget, pc + 4.U)
  when(flush) {
    pc := jumpTarget
  }.elsewhen(fetchFire) {
    pc := pc + 4.U
  }

  when(!fetchBlocked && fetchFire) {
    ifId.pc := pc
    ifId.inst := fetchedInst
    ifIdValid := true.B
  }
  // Flush must clear IF/ID even when the sequencer is currently holding fetch,
  // otherwise a stale FX instruction would resume after the branch resolves.
  when(flush) {
    ifIdValid := false.B
  }

  val injectBubble = loadUseHazard || flush
  // The FX sequencer advances its step counter on every cycle that a micro-op
  // actually commits to ID/EX (matches the !injectBubble && pipeReady &&
  // ifIdValid branch below). A flush resets the step instead.
  seq.io.advance := pipeReady && !injectBubble && ifIdValid
  seq.io.flush := flush

  // FXDIV result-latch bookkeeping. `done` is a 1-cycle pulse so we capture
  // the quotient any time it fires, then clear the latch on the same cycle
  // the FXDIV moves from ID/EX into EX/MEM (`pipeReady` && FXDIV in idEx).
  // FXDIV cannot be the producer of a load-use hazard or a taken branch, so
  // injectBubble is always false here and we don't need to handle it.
  val divConsume = isFxDivInEx && pipeReady
  when(divUnit.io.done) {
    divResultLatched := true.B
    divResultData := divUnit.io.out
  }
  when(divConsume) {
    divResultLatched := false.B
  }

  when(memStageReady) {
    when(injectBubble) {
      idExValid := false.B
    }.elsewhen(pipeReady) {
      idExValid := ifIdValid
      when(ifIdValid) {
        idEx.pc := ifId.pc
        idEx.rs1 := decode.rs1
        idEx.rs2 := decode.rs2
        idEx.rd := decode.rd
        idEx.rs1Val := regFile.io.rs1Data
        idEx.rs2Val := regFile.io.rs2Data
        idEx.imm := decode.imm
        idEx.funct3 := decode.funct3
        idEx.ctrl := decode.ctrl
      }
    }
    // else (memStageReady && exBusy && !injectBubble): hold idEx so FXDIV
    // operands stay latched until the divider finishes.
  }

  when(memStageReady) {
    when(pipeReady) {
      exMemValid := idExValid
      when(idExValid) {
        exMem.pc4 := idEx.pc + 4.U
        exMem.rd := idEx.rd
        exMem.aluRes := exAluResult
        exMem.rs2Val := exRs2
        exMem.funct3 := idEx.funct3
        exMem.ctrl := idEx.ctrl
      }
    }.otherwise {
      // EX is busy producing the FXDIV quotient; drain exMem to MEM/WB so any
      // pending load completes, then leave the EX/MEM register empty until
      // the divider produces its result.
      exMemValid := false.B
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
  // Debug stream only exposes architectural register writebacks. Scratch
  // writes (bit 5 of memWb.rd set) are intentionally hidden so tests that
  // observe arch-reg writes don't see internal sequencer bookkeeping.
  io.debugWbValid := memWbValid && memWb.rdWrite && !memWb.rd(5)
  io.debugWbRd := memWb.rd(4, 0)
  io.debugWbData := memWb.wbData

  dontTouch(nextPC)
}
