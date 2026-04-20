package luma_fix_v

import chisel3._
import chisel3.util._

class InstrBusIO extends Bundle {
  val reqValid = Output(Bool())
  val reqAddr = Output(UInt(32.W))
  val reqReady = Input(Bool())
  val respValid = Input(Bool())
  val respData = Input(UInt(32.W))
}

class DataBusIO extends Bundle {
  val reqValid = Output(Bool())
  val reqAddr = Output(UInt(32.W))
  val reqWrite = Output(Bool())
  val reqWData = Output(UInt(32.W))
  val reqWMask = Output(UInt(4.W))
  val reqReady = Input(Bool())
  val respValid = Input(Bool())
  val respData = Input(UInt(32.W))
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

  val fetchedInst = io.imem.respData
  val decode = RV32IDecoder.decode(ifId.inst)

  regFile.io.rs1Addr := decode.rs1
  regFile.io.rs2Addr := decode.rs2
  regFile.io.rdAddr := memWb.rd
  regFile.io.rdData := memWb.wbData
  regFile.io.rdWrite := memWbValid && memWb.rdWrite

  val loadUseHazard = idExValid && idEx.ctrl.memRead && (idEx.rd =/= 0.U) && (
    (decode.ctrl.rs1Used && (decode.rs1 === idEx.rd)) ||
      (decode.ctrl.rs2Used && (decode.rs2 === idEx.rd))
  )

  val exCanForward =
    exMemValid && exMem.ctrl.rdWrite && !exMem.ctrl.memRead && (exMem.rd =/= 0.U)
  val wbCanForward = memWbValid && memWb.rdWrite && (memWb.rd =/= 0.U)

  val exRs1 = Wire(UInt(32.W))
  val exRs2 = Wire(UInt(32.W))

  exRs1 := idEx.rs1Val
  when(exCanForward && (exMem.rd === idEx.rs1)) {
    exRs1 := exMem.aluRes
  }.elsewhen(wbCanForward && (memWb.rd === idEx.rs1)) {
    exRs1 := memWb.wbData
  }

  exRs2 := idEx.rs2Val
  when(exCanForward && (exMem.rd === idEx.rs2)) {
    exRs2 := exMem.aluRes
  }.elsewhen(wbCanForward && (memWb.rd === idEx.rs2)) {
    exRs2 := memWb.wbData
  }

  val branchTaken = WireDefault(false.B)
  when(idEx.ctrl.branch) {
    switch(idEx.funct3) {
      is("b000".U) { branchTaken := exRs1 === exRs2 }
      is("b001".U) { branchTaken := exRs1 =/= exRs2 }
      is("b100".U) { branchTaken := exRs1.asSInt < exRs2.asSInt }
      is("b101".U) { branchTaken := exRs1.asSInt >= exRs2.asSInt }
      is("b110".U) { branchTaken := exRs1 < exRs2 }
      is("b111".U) { branchTaken := exRs1 >= exRs2 }
    }
  }

  val exJumpTaken = idEx.ctrl.jump || (idEx.ctrl.branch && branchTaken)
  val jumpBase = Mux(idEx.ctrl.jumpReg, exRs1, idEx.pc)
  val jumpTargetRaw = jumpBase + idEx.imm
  val jumpTarget =
    Mux(idEx.ctrl.jumpReg, Cat(jumpTargetRaw(31, 1), 0.U(1.W)), jumpTargetRaw)

  val exOp1 = Mux(idEx.ctrl.aluSrc1PC, idEx.pc, exRs1)
  val exOp2 = Mux(idEx.ctrl.aluSrc2Imm, idEx.imm, exRs2)

  alu.io.op := idEx.ctrl.aluOp
  alu.io.lhs := exOp1
  alu.io.rhs := exOp2

  val storeShift = exMem.aluRes(1, 0)
  val storeWData = (exMem.rs2Val << (storeShift << 3))(31, 0)
  val storeMask = MuxLookup(
    exMem.ctrl.memSize,
    "b1111".U
  )(
    Seq(
      0.U -> (1.U(4.W) << storeShift),
      1.U -> Mux(storeShift(1), "b1100".U, "b0011".U),
      2.U -> "b1111".U
    )
  )

  val loadShift = exMem.aluRes(1, 0)
  val loadByte = (io.dmem.respData >> (loadShift << 3))(7, 0)
  val loadHalf = (io.dmem.respData >> (loadShift(1) << 4))(15, 0)
  val loadData = Wire(UInt(32.W))
  loadData := io.dmem.respData
  switch(exMem.funct3) {
    is("b000".U) {
      loadData := Cat(
        Fill(24, loadByte(7) && !exMem.ctrl.memUnsigned),
        loadByte
      )
    }
    is("b001".U) {
      loadData := Cat(
        Fill(16, loadHalf(15) && !exMem.ctrl.memUnsigned),
        loadHalf
      )
    }
    is("b010".U) { loadData := io.dmem.respData }
    is("b100".U) { loadData := Cat(0.U(24.W), loadByte) }
    is("b101".U) { loadData := Cat(0.U(16.W), loadHalf) }
  }

  val memAccess = exMem.ctrl.memRead || exMem.ctrl.memWrite
  val memRespNeeded = exMem.ctrl.memRead
  val memReqAccepted = !memAccess || io.dmem.reqReady
  val memRespReady = !memRespNeeded || io.dmem.respValid
  val memStageReady = !exMemValid || (memReqAccepted && memRespReady)

  val fetchBlocked = loadUseHazard || !memStageReady
  val fetchFire = io.imem.respValid && !fetchBlocked

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

  io.imem.reqValid := true.B
  io.imem.reqAddr := pc

  io.dmem.reqValid := exMemValid && (exMem.ctrl.memRead || exMem.ctrl.memWrite)
  io.dmem.reqAddr := exMem.aluRes
  io.dmem.reqWrite := exMem.ctrl.memWrite
  io.dmem.reqWData := storeWData
  io.dmem.reqWMask := storeMask

  io.debugPC := pc
  io.debugWbValid := memWbValid && memWb.rdWrite
  io.debugWbRd := memWb.rd
  io.debugWbData := memWb.wbData

  dontTouch(nextPC)
}
