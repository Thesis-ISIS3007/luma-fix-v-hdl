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
  val csrAddr = UInt(12.W)
  val ctrl = new DecodeSignals
}

class ExMemPipe extends Bundle {
  val pc4 = UInt(32.W)
  val rd = UInt(6.W)
  val aluRes = UInt(32.W)
  val rs2Val = UInt(32.W)
  val ctrl = new DecodeSignals
}

class MemWbPipe extends Bundle {
  val rd = UInt(6.W)
  val wbData = UInt(32.W)
  val rdWrite = Bool()
}

object BranchUnit {
  def apply(cond: BranchCond.Type, rs1: UInt, rs2: UInt): Bool = {
    MuxLookup(cond, false.B)(
      Seq(
        BranchCond.eq -> (rs1 === rs2),
        BranchCond.ne -> (rs1 =/= rs2),
        BranchCond.lt -> (rs1.asSInt < rs2.asSInt),
        BranchCond.ge -> (rs1.asSInt >= rs2.asSInt),
        BranchCond.ltu -> (rs1 < rs2),
        BranchCond.geu -> (rs1 >= rs2)
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
      idExWritesRd: Bool,
      idExResultReadyInEx: Bool,
      idExRd: UInt,
      rs1Used: Bool,
      rs1: UInt,
      rs2Used: Bool,
      rs2: UInt
  ): Bool =
    // Stall on any unresolved RAW dependency from the current ID/EX producer.
    // Loads are the current concrete case (result not ready until MEM), but
    // this keeps the policy explicit for future multi-cycle producers that
    // cannot be forwarded from EX in the same cycle.
    //
    // Loads only ever target architectural rd, so a 6-bit compare is safe:
    // a scratch consumer (bit 5 = 1) cannot match a load producer (bit 5 = 0).
    idExValid && idExWritesRd && !idExResultReadyInEx && (idExRd =/= 0.U) &&
      ((rs1Used && (rs1 === idExRd)) || (rs2Used && (rs2 === idExRd)))
}

object JumpUnit {
  def apply(jumpReg: Bool, rs1: UInt, pc: UInt, imm: UInt): UInt = {
    val raw = Mux(jumpReg, rs1, pc) + imm
    Mux(jumpReg, Cat(raw(31, 1), 0.U(1.W)), raw)
  }
}

object StoreUnit {
  def apply(
      xlen: Int,
      addr: UInt,
      data: UInt,
      memOp: MemOp.Type
  ): (UInt, UInt) = {
    require(
      xlen == 32,
      s"StoreUnit currently supports RV32 only, got xlen=$xlen"
    )
    val shift = addr(1, 0)
    val wData = (data << (shift << 3))(xlen - 1, 0)
    val wMask = MuxLookup(memOp, 0.U(4.W))(
      Seq(
        MemOp.sb -> (1.U(4.W) << shift),
        MemOp.sh -> Mux(shift(1), "b1100".U, "b0011".U),
        MemOp.sw -> "b1111".U
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
      memOp: MemOp.Type
  ): UInt = {
    require(
      xlen == 32,
      s"LoadUnit currently supports RV32 only, got xlen=$xlen"
    )
    val shift = addr(1, 0)
    val byte = (rawData >> (shift << 3))(7, 0)
    val half = (rawData >> (shift(1) << 4))(15, 0)
    val out = Wire(UInt(xlen.W))
    out := rawData
    switch(memOp) {
      is(MemOp.lb) { out := Cat(Fill(24, byte(7)), byte) }
      is(MemOp.lh) { out := Cat(Fill(16, half(15)), half) }
      is(MemOp.lw) { out := rawData }
      is(MemOp.lbu) { out := Cat(0.U(24.W), byte) }
      is(MemOp.lhu) { out := Cat(0.U(16.W), half) }
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

  // Minimal machine-mode CSR storage.
  val csrMstatus = RegInit(0.U(32.W)) // 0x300
  val csrMscratch = RegInit(0.U(32.W)) // 0x340
  val csrMepc = RegInit(0.U(32.W)) // 0x341
  val csrMcause = RegInit(0.U(32.W)) // 0x342
  val csrMtvec = RegInit(0.U(32.W)) // 0x305

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

  val idExWritesRd = idEx.ctrl.rdWrite
  val idExResultReadyInEx = !idEx.ctrl.memRead
  val rawHazardStall = HazardUnit(
    idExValid,
    idExWritesRd,
    idExResultReadyInEx,
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

  val branchTaken =
    idEx.ctrl.branch && BranchUnit(idEx.ctrl.branchCond, exRs1, exRs2)

  val exJumpTaken = idEx.ctrl.jump || (idEx.ctrl.branch && branchTaken)
  val jumpTarget = JumpUnit(idEx.ctrl.jumpReg, exRs1, idEx.pc, idEx.imm)

  val exOp1 = Mux(idEx.ctrl.aluSrc1PC, idEx.pc, exRs1)
  val exOp2 = Mux(idEx.ctrl.aluSrc2Imm, idEx.imm, exRs2)

  val csrReadData = MuxLookup(idEx.csrAddr, 0.U(32.W))(
    Seq(
      "h300".U -> csrMstatus,
      "h305".U -> csrMtvec,
      "h340".U -> csrMscratch,
      "h341".U -> csrMepc,
      "h342".U -> csrMcause
    )
  )
  val csrOperand = Mux(idEx.ctrl.csrImm, Cat(0.U(27.W), idEx.rs1(4, 0)), exRs1)
  val csrWriteEnable = idEx.ctrl.csrCmd =/= CsrCmd.none
  val csrShouldWrite = MuxLookup(csrWriteEnable, false.B)(
    Seq(
      true.B -> MuxLookup(idEx.ctrl.csrCmd, false.B)(
        Seq(
          CsrCmd.rw -> true.B,
          CsrCmd.rs -> (csrOperand =/= 0.U),
          CsrCmd.rc -> (csrOperand =/= 0.U)
        )
      )
    )
  )
  val csrWriteData = MuxLookup(idEx.ctrl.csrCmd, csrReadData)(
    Seq(
      CsrCmd.rw -> csrOperand,
      CsrCmd.rs -> (csrReadData | csrOperand),
      CsrCmd.rc -> (csrReadData & ~csrOperand)
    )
  )

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
  val exAluResult = MuxCase(
    alu.io.out,
    Seq(
      isFxDivInEx -> effectiveDivOut,
      (idEx.ctrl.csrCmd =/= CsrCmd.none) -> csrReadData
    )
  )

  val (storeWData, storeMask) =
    StoreUnit(cfg.xlen, exMem.aluRes, exMem.rs2Val, exMem.ctrl.memOp)

  val loadData = LoadUnit(
    cfg.xlen,
    io.dmem.resp.bits,
    exMem.aluRes,
    exMem.ctrl.memOp
  )

  val misalignedMemAccess = MuxLookup(exMem.ctrl.memOp, false.B)(
    Seq(
      MemOp.lb -> false.B,
      MemOp.lbu -> false.B,
      MemOp.sb -> false.B,
      MemOp.lh -> exMem.aluRes(0),
      MemOp.lhu -> exMem.aluRes(0),
      MemOp.sh -> exMem.aluRes(0),
      MemOp.lw -> (exMem.aluRes(1, 0) =/= 0.U),
      MemOp.sw -> (exMem.aluRes(1, 0) =/= 0.U)
    )
  )
  // No trap path yet: detect and squash misaligned accesses so they don't hit
  // memory and don't commit a bogus load result.
  val memAccess =
    (exMem.ctrl.memRead || exMem.ctrl.memWrite) && !misalignedMemAccess
  val memRespNeeded = exMem.ctrl.memRead && !misalignedMemAccess
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
  val branchFlush = exJumpTaken && idExValid
  val illegalTrap = ifIdValid && decode.ctrl.illegal
  // ID-stage illegal trap only takes effect when no older EX-stage redirect
  // is in flight.
  val trapFlush = illegalTrap && !branchFlush
  val flush = branchFlush || trapFlush

  val fetchBlocked =
    rawHazardStall || !pipeReady || seq.io.holdFetch || trapFlush
  // IF only advances when the request is accepted and an instruction is
  // available in the same cycle.
  val fetchFire = io.imem.req.fire && io.imem.resp.valid

  val nextPC = Mux(branchFlush, jumpTarget, Mux(trapFlush, csrMtvec, pc + 4.U))
  when(branchFlush) {
    pc := jumpTarget
  }.elsewhen(trapFlush) {
    pc := csrMtvec
  }.elsewhen(fetchFire) {
    pc := pc + 4.U
  }

  when(fetchFire) {
    ifId.pc := pc
    ifId.inst := fetchedInst
    ifIdValid := true.B
  }
  // Flush must clear IF/ID even when the sequencer is currently holding fetch,
  // otherwise a stale FX instruction would resume after the branch resolves.
  when(flush) {
    ifIdValid := false.B
  }

  when(trapFlush) {
    csrMepc := ifId.pc
    csrMcause := 2.U // Illegal instruction
  }

  val injectBubble = rawHazardStall || flush
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
        idEx.csrAddr := decode.csrAddr
        idEx.ctrl := decode.ctrl
      }
    }
    // else (memStageReady && exBusy && !injectBubble): hold idEx so FXDIV
    // operands stay latched until the divider finishes.
  }

  when(idExValid && pipeReady && csrShouldWrite) {
    switch(idEx.csrAddr) {
      is("h300".U) { csrMstatus := csrWriteData }
      is("h305".U) { csrMtvec := csrWriteData }
      is("h340".U) { csrMscratch := csrWriteData }
      is("h341".U) { csrMepc := csrWriteData }
      is("h342".U) { csrMcause := csrWriteData }
    }
  }

  when(memStageReady) {
    when(pipeReady) {
      exMemValid := idExValid
      when(idExValid) {
        exMem.pc4 := idEx.pc + 4.U
        exMem.rd := idEx.rd
        exMem.aluRes := exAluResult
        exMem.rs2Val := exRs2
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
    memWb.rdWrite := exMem.ctrl.rdWrite && !misalignedMemAccess
  }

  io.imem.req.valid := !fetchBlocked
  io.imem.req.bits := pc

  io.dmem.req.valid := exMemValid && memAccess
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

  // Invariants to catch control/commit bugs early in simulation.
  when(exMemValid) {
    assert(
      !(exMem.ctrl.memRead && exMem.ctrl.memWrite),
      "EX/MEM instruction cannot read and write memory simultaneously"
    )
  }
  when(idExValid) {
    assert(
      !(idEx.ctrl.branch && idEx.ctrl.jump),
      "ID/EX control cannot mark instruction as both branch and jump"
    )
    assert(!idEx.ctrl.jumpReg || idEx.ctrl.jump, "ID/EX jumpReg requires jump")
  }
  when(exMemValid && exMem.ctrl.memRead) {
    assert(exMem.ctrl.rdWrite, "Load in EX/MEM must enable rd writeback")
    assert(
      exMem.ctrl.wbSel === RV32IDecode.WbSelMem,
      "Load in EX/MEM must select memory writeback source"
    )
  }
  when(exMemValid && exMem.ctrl.memWrite) {
    assert(!exMem.ctrl.rdWrite, "Store in EX/MEM must not enable rd writeback")
  }
}
