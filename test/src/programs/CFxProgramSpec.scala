package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

// End-to-end tests for the FX 16Q16 extension driven by GCC-compiled C
// programs. The C source uses the inline-asm wrappers in
// validation/fx16q16.h, so these tests effectively cover both the FX
// hardware path and the .insn-based encoding contract with the toolchain.
//
// The generated programs are larger than the default 256-word footprint that
// CBinaryProgramSupport uses, so we override imem/dmem to 1024 words apiece
// and bump the simulation horizon to give the multi-cycle FXDIV time to
// commit all of its writebacks.
class CFxProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  override protected val cProgramImemWords: Int = 1024
  override protected val cProgramDmemWords: Int = 1024

  describe("FX 16Q16 C-language end-to-end programs") {
    it(
      "fx_arith_smoke: chained FXADD/FXSUB/FXMUL/FXNEG/INT2FX/FX2INT/FXABS",
      CBinary
    ) {
      // h = 6.375 = 0x66000 in 16Q16. The companion store at 0x84 is the
      // integer projection (12 + 12 = 24).
      runBinaryProgram(
        "/validation/c_fx_arith_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("66000", 16),
        cycles = 5000
      )
      runBinaryProgram(
        "/validation/c_fx_arith_smoke.hex",
        outAddr = 0x84,
        expected = 24,
        cycles = 5000
      )
    }

    it("fx_div_smoke: FXDIV across positive/negative/zero divisors", CBinary) {
      // sum = 50.0 = 0x320000 in 16Q16; p2 (10.0 in 16Q16) = 0xA0000.
      // FXDIV stalls ~50 cycles per issue and the program issues 7 of them,
      // so we give the simulator a generous horizon.
      runBinaryProgram(
        "/validation/c_fx_div_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("320000", 16),
        cycles = 8000
      )
      runBinaryProgram(
        "/validation/c_fx_div_smoke.hex",
        outAddr = 0x84,
        expected = BigInt("A0000", 16),
        cycles = 8000
      )
    }

    it(
      "fx_dot_product_smoke: 4-element dot product in a counted loop",
      CBinary
    ) {
      // r = 12.25 = 0xC4000 in 16Q16; integer projection = 12.
      runBinaryProgram(
        "/validation/c_fx_dot_product_smoke.hex",
        outAddr = 0x80,
        expected = BigInt("C4000", 16),
        cycles = 5000
      )
      runBinaryProgram(
        "/validation/c_fx_dot_product_smoke.hex",
        outAddr = 0x84,
        expected = 12,
        cycles = 5000
      )
    }
  }
}
