package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

// End-to-end test for the FX 16Q16 ray tracer math scaffold. Drives one
// closed-form ray-triangle intersection through the validation/rt/* headers,
// the inline-asm intrinsics in validation/fx16q16.h, and the FX hardware
// path in the core, then peeks every dmem word the C program writes.
//
// The smoke binary is ~6 KiB at -O0 (lots of inlined fx_sqrt + 16Q16 ops),
// so we bump imem/dmem to 2048 words to match the 8 KiB linker region. The
// horizon also has to absorb the 24-iteration software fx_sqrt and a couple
// of multi-cycle FXDIV stalls (1/a + 1/len for normalize).
class CFxRtProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  override protected val cProgramImemWords: Int = 2048
  override protected val cProgramDmemWords: Int = 2048

  describe("FX 16Q16 ray tracer math scaffold") {
    it(
      "fx_rt_triangle_smoke: ray vs. xy unit triangle hits at t=1, normal=+z",
      CBinary
    ) {
      val hex = "/programs/c_fx_rt_triangle_smoke.hex"
      val cycles = 30000
      val fxOne = BigInt("10000", 16)
      val fxQuarter = BigInt("4000", 16)

      runBinaryProgram(hex, outAddr = 0x80, expected = 1, cycles = cycles)
      runBinaryProgram(hex, outAddr = 0x84, expected = fxOne, cycles = cycles)
      runBinaryProgram(
        hex,
        outAddr = 0x88,
        expected = fxQuarter,
        cycles = cycles
      )
      runBinaryProgram(
        hex,
        outAddr = 0x8c,
        expected = fxQuarter,
        cycles = cycles
      )
      runBinaryProgram(hex, outAddr = 0x90, expected = 0, cycles = cycles)
      runBinaryProgram(hex, outAddr = 0x94, expected = 0, cycles = cycles)
      runBinaryProgram(hex, outAddr = 0x98, expected = 0, cycles = cycles)
      runBinaryProgram(hex, outAddr = 0x9c, expected = fxOne, cycles = cycles)
    }
  }
}
