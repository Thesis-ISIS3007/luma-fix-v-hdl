package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class FxDivUnitSpec extends AnyFunSpec with ChiselSim {

  // Total cycles from `start` to `done` is bounded by the divider's FSM:
  // 1 cycle in sIdle (setup), 32 in sBusy, 1 in sDone. This loop polls the
  // `done` strobe so the harness doesn't depend on that exact constant.
  private def runDiv(
      c: FxDivUnit,
      lhs: Long,
      rhs: Long,
      maxCycles: Int = 64
  ): BigInt = {
    c.io.lhs.poke((lhs & 0xffffffffL).U(32.W))
    c.io.rhs.poke((rhs & 0xffffffffL).U(32.W))
    c.io.start.poke(true.B)

    var seen = false
    var result: BigInt = BigInt(0)
    for (_ <- 0 until maxCycles if !seen) {
      if (c.io.done.peek().litToBoolean) {
        result = c.io.out.peek().litValue
        seen = true
      }
      c.clock.step()
    }
    require(seen, s"divider never asserted done within $maxCycles cycles")

    // Drop start so the divider returns to sIdle and is ready for the next
    // operation in the same simulation.
    c.io.start.poke(false.B)
    c.clock.step()
    result
  }

  private def fx(real: Double): Long =
    math.round(real * (1L << 16)) & 0xffffffffL
  private def asUnsigned(x: Long): BigInt = BigInt(x & 0xffffffffL)

  describe("FxDivUnit") {
    it("computes positive 16Q16 quotients") {
      simulate(new FxDivUnit()) { c =>
        // 6.0 / 2.0 = 3.0
        assert(runDiv(c, fx(6.0), fx(2.0)) == asUnsigned(fx(3.0)))
        // 1.0 / 4.0 = 0.25
        assert(runDiv(c, fx(1.0), fx(4.0)) == asUnsigned(fx(0.25)))
        // 5.0 / 2.5 = 2.0
        assert(runDiv(c, fx(5.0), fx(2.5)) == asUnsigned(fx(2.0)))
      }
    }

    it("computes signed quotients with correct sign combinations") {
      simulate(new FxDivUnit()) { c =>
        // -6.0 / 2.0 = -3.0
        assert(runDiv(c, fx(-6.0), fx(2.0)) == asUnsigned(fx(-3.0)))
        // 6.0 / -2.0 = -3.0
        assert(runDiv(c, fx(6.0), fx(-2.0)) == asUnsigned(fx(-3.0)))
        // -6.0 / -2.0 = 3.0
        assert(runDiv(c, fx(-6.0), fx(-2.0)) == asUnsigned(fx(3.0)))
        // -1.0 / 4.0 = -0.25
        assert(runDiv(c, fx(-1.0), fx(4.0)) == asUnsigned(fx(-0.25)))
      }
    }

    it("returns 0 for divide-by-zero (no trap, see ISA.md)") {
      simulate(new FxDivUnit()) { c =>
        assert(runDiv(c, fx(1.0), 0L) == BigInt(0))
        assert(runDiv(c, fx(-100.0), 0L) == BigInt(0))
      }
    }

    it("supports back-to-back issues without losing the next quotient") {
      simulate(new FxDivUnit()) { c =>
        // First op: 8.0 / 2.0 = 4.0. Drop start, then immediately issue the
        // next op in the same simulation; runDiv handles the start-toggle for
        // both ops so the divider FSM has to come back to sIdle cleanly.
        assert(runDiv(c, fx(8.0), fx(2.0)) == asUnsigned(fx(4.0)))
        assert(runDiv(c, fx(9.0), fx(3.0)) == asUnsigned(fx(3.0)))
        assert(runDiv(c, fx(-9.0), fx(3.0)) == asUnsigned(fx(-3.0)))
      }
    }

    it("truncates non-representable quotients via 32-bit wrap-around") {
      simulate(new FxDivUnit()) { c =>
        // 32767.0 / 0.0001525... (= 0x0001 = 1/65536 in 16Q16) overflows the
        // 32-bit quotient. We just check that the divider doesn't hang and
        // produces *some* deterministic 32-bit value (matches `(lhs<<16)/rhs`
        // truncated).
        val lhs = 0x7fff0000L // 32767.0
        val rhs = 0x00000001L // ~1.5e-5
        // (32767 << 16) << 16 = 0x7FFF_0000_0000_0000; / 1 = same; truncate to
        // low 32 bits = 0.
        val expected =
          (((BigInt(lhs) << 16) / BigInt(rhs)) & BigInt("FFFFFFFF", 16))
        assert(runDiv(c, lhs, rhs) == expected)
      }
    }
  }
}
