package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

// End-to-end tests for the FX 16Q16 extension. Each program loads operands
// into architectural registers via existing RV32I instructions, runs an FX
// op, and verifies the architectural register file via the debug writeback
// stream (scratch writes are deliberately hidden, see RV32ICore.scala).
class FxProgramSpec extends AnyFunSpec with ChiselSim with ISATestSupport {

  // 16Q16 helpers: convert Scala doubles / signed integers into the 32-bit
  // wire encoding the hardware sees.
  private val ONE: Long = 1L << 16
  private def fx(real: Double): Long =
    (math.round(real * ONE.toDouble) & 0xffffffffL)
  private def asUnsigned(x: Long): BigInt = BigInt(x & 0xffffffffL)

  // Loads a 32-bit value into `rd` using LUI + ADDI. Handles the sign-extended
  // ADDI immediate by bumping the upper immediate when bit 11 of the lower
  // 12 bits is set.
  private def loadConst(pb: ProgramBuilder, rd: Int, value: Long): Unit = {
    val low = (value & 0xfff).toInt
    val signedLow = if ((low & 0x800) != 0) low - 0x1000 else low
    val upper = (((value - signedLow) >> 12) & 0xfffff).toInt
    pb.emit(uType(upper << 12, rd, 0x37)) // LUI rd, upper
    pb.emit(iType(signedLow & 0xfff, rd, 0x0, rd, 0x13)) // ADDI rd, rd, low
  }

  private def runAndCheck(
      pb: ProgramBuilder,
      maxCycles: Int = 200
  )(checks: Array[BigInt] => Unit): Unit = {
    val (regs, _) = runProgram(pb.result, maxCycles)
    checks(regs)
  }

  describe("FX 16Q16 extension - end-to-end programs") {

    it("FXADD computes 1.5 + 2.25 = 3.75") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(1.5))
      loadConst(pb, 2, fx(2.25))
      pb.emit(fxAdd(3, 1, 2))
      runAndCheck(pb) { regs =>
        assert(
          regs(3) == asUnsigned(fx(3.75)),
          s"x3 = 0x${regs(3).toString(16)}"
        )
      }
    }

    it("FXSUB computes 1.0 - 0.5 = 0.5 and supports negative results") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(1.0))
      loadConst(pb, 2, fx(0.5))
      pb.emit(fxSub(3, 1, 2))
      pb.emit(fxSub(4, 2, 1))
      runAndCheck(pb) { regs =>
        assert(regs(3) == asUnsigned(fx(0.5)))
        assert(regs(4) == asUnsigned(fx(-0.5)))
      }
    }

    it("FXMUL computes signed 16Q16 products with truncation") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(1.5))
      loadConst(pb, 2, fx(2.0))
      loadConst(pb, 3, fx(-1.5))
      loadConst(pb, 4, fx(-1.0))
      pb.emit(fxMul(5, 1, 2)) // 3.0
      pb.emit(fxMul(6, 3, 2)) // -3.0
      pb.emit(fxMul(7, 3, 4)) // 1.5
      pb.emit(fxMul(8, 1, 1)) // 2.25
      runAndCheck(pb) { regs =>
        assert(regs(5) == asUnsigned(fx(3.0)))
        assert(regs(6) == asUnsigned(fx(-3.0)))
        assert(regs(7) == asUnsigned(fx(1.5)))
        assert(regs(8) == asUnsigned(fx(2.25)))
      }
    }

    it("FXNEG negates a signed 16Q16 value") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(1.25))
      loadConst(pb, 2, fx(-3.5))
      pb.emit(fxNeg(3, 1))
      pb.emit(fxNeg(4, 2))
      runAndCheck(pb) { regs =>
        assert(regs(3) == asUnsigned(fx(-1.25)))
        assert(regs(4) == asUnsigned(fx(3.5)))
      }
    }

    it("INT2FX/FX2INT round-trip integers and truncate fractions") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, 7)
      loadConst(pb, 2, fx(3.75))
      pb.emit(int2Fx(3, 1)) // 7 -> 7.0
      pb.emit(
        fx2Int(4, 2)
      ) // 3.75 -> 3 (arithmetic shift truncates toward -inf)
      pb.emit(int2Fx(5, 0)) // 0 -> 0
      runAndCheck(pb) { regs =>
        assert(regs(3) == asUnsigned(fx(7.0)))
        assert(regs(4) == 3)
        assert(regs(5) == 0)
      }
    }

    it("FXABS computes absolute value via the 3-uop scratch sequence") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(2.5))
      loadConst(pb, 2, fx(-2.5))
      loadConst(pb, 3, 0)
      pb.emit(fxAbs(4, 1))
      pb.emit(fxAbs(5, 2))
      pb.emit(fxAbs(6, 3))
      runAndCheck(pb) { regs =>
        assert(regs(4) == asUnsigned(fx(2.5)))
        assert(regs(5) == asUnsigned(fx(2.5)))
        assert(regs(6) == 0)
      }
    }

    it("FXABS of INT_MIN matches branchless wrap-around (returns INT_MIN)") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, 0x80000000L)
      pb.emit(fxAbs(2, 1))
      runAndCheck(pb) { regs =>
        // (x ^ (x>>s31)) - (x>>s31) for INT_MIN evaluates back to INT_MIN.
        assert(regs(2) == asUnsigned(0x80000000L))
      }
    }

    it("Back-to-back FXABS keeps scratch bookkeeping isolated between uses") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(-1.5))
      loadConst(pb, 2, fx(-3.0))
      pb.emit(fxAbs(3, 1))
      pb.emit(fxAbs(4, 2))
      pb.emit(fxAdd(5, 3, 4))
      runAndCheck(pb) { regs =>
        assert(regs(3) == asUnsigned(fx(1.5)))
        assert(regs(4) == asUnsigned(fx(3.0)))
        assert(regs(5) == asUnsigned(fx(4.5)))
      }
    }

    it("FXMUL chained with FXADD (a*b + c) computes correctly") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(1.5))
      loadConst(pb, 2, fx(2.0))
      loadConst(pb, 3, fx(0.25))
      pb.emit(fxMul(4, 1, 2)) // 3.0
      pb.emit(fxAdd(5, 4, 3)) // 3.25
      runAndCheck(pb) { regs =>
        assert(regs(4) == asUnsigned(fx(3.0)))
        assert(regs(5) == asUnsigned(fx(3.25)))
      }
    }

    it("FXMUL wrap-around: 32767.0 * 2.0 overflows to -2.0 in 16Q16") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, 0x7fff0000L) // 32767.0
      loadConst(pb, 2, fx(2.0))
      pb.emit(fxMul(3, 1, 2))
      runAndCheck(pb) { regs =>
        assert(regs(3) == asUnsigned(0xfffe0000L))
      }
    }

    // FXDIV uses the multi-cycle iterative divider: each instruction stalls
    // the pipeline for ~34 cycles, so we bump maxCycles per program so the
    // simulator has time to commit every writeback we expect.
    it("FXDIV computes basic 16Q16 quotients") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(6.0))
      loadConst(pb, 2, fx(2.0))
      loadConst(pb, 3, fx(1.0))
      loadConst(pb, 4, fx(4.0))
      pb.emit(fxDiv(5, 1, 2)) // 3.0
      pb.emit(fxDiv(6, 3, 4)) // 0.25
      runAndCheck(pb, maxCycles = 400) { regs =>
        assert(
          regs(5) == asUnsigned(fx(3.0)),
          s"x5 = 0x${regs(5).toString(16)}"
        )
        assert(
          regs(6) == asUnsigned(fx(0.25)),
          s"x6 = 0x${regs(6).toString(16)}"
        )
      }
    }

    it("FXDIV handles negative operands and zero divisor") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(-6.0))
      loadConst(pb, 2, fx(2.0))
      loadConst(pb, 3, fx(6.0))
      loadConst(pb, 4, fx(-2.0))
      loadConst(pb, 5, fx(1.0))
      loadConst(pb, 6, 0)
      pb.emit(fxDiv(7, 1, 2)) // -3.0
      pb.emit(fxDiv(8, 3, 4)) // -3.0
      pb.emit(fxDiv(9, 1, 4)) // 3.0
      pb.emit(fxDiv(10, 5, 6)) // div by 0 -> 0
      runAndCheck(pb, maxCycles = 600) { regs =>
        assert(regs(7) == asUnsigned(fx(-3.0)))
        assert(regs(8) == asUnsigned(fx(-3.0)))
        assert(regs(9) == asUnsigned(fx(3.0)))
        assert(regs(10) == 0)
      }
    }

    it("Back-to-back FXDIV preserves divider isolation between issues") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(8.0))
      loadConst(pb, 2, fx(2.0))
      loadConst(pb, 3, fx(9.0))
      loadConst(pb, 4, fx(3.0))
      pb.emit(fxDiv(5, 1, 2)) // 4.0
      pb.emit(fxDiv(6, 3, 4)) // 3.0
      pb.emit(fxAdd(7, 5, 6)) // 7.0 (also exercises forwarding from FXDIV)
      runAndCheck(pb, maxCycles = 400) { regs =>
        assert(regs(5) == asUnsigned(fx(4.0)))
        assert(regs(6) == asUnsigned(fx(3.0)))
        assert(regs(7) == asUnsigned(fx(7.0)))
      }
    }

    it("FXDIV result is consumable by an immediately-following FXMUL") {
      val pb = new ProgramBuilder()
      loadConst(pb, 1, fx(10.0))
      loadConst(pb, 2, fx(4.0))
      loadConst(pb, 3, fx(2.0))
      pb.emit(fxDiv(4, 1, 2)) // 2.5
      pb.emit(fxMul(5, 4, 3)) // 5.0
      runAndCheck(pb, maxCycles = 300) { regs =>
        assert(regs(4) == asUnsigned(fx(2.5)))
        assert(regs(5) == asUnsigned(fx(5.0)))
      }
    }
  }
}
