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
//
// This test is intentionally **not** tagged CBinary: it loads a sample hex
// under `/samples/` (not `/validation/`) and is kept as a fast ray–triangle
// check separate from the CBinary-tagged validation corpus.
class CFxRtProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  override protected val cProgramImemWords: Int = 2048
  override protected val cProgramDmemWords: Int = 2048

  describe("FX 16Q16 ray tracer math scaffold") {
    it("fx_rt_triangle_smoke: ray vs. xy equilateral hits at t=1, normal=+z") {
      val hex = "/samples/c_fx_rt_triangle_smoke.hex"
      val fxZero = BigInt(0)
      // 16.16: t and unit normal z are 1.0 - 1 ulp; hit z = 1.0 - t is ~1/65536.
      val fxT = BigInt(65535L)
      val fxPosZ = BigInt(1L)
      val fxNz = BigInt(65535L)

      runBinaryProgram(hex, outAddr = 0x80, expected = 1)
      runBinaryProgram(hex, outAddr = 0x84, expected = fxT)
      runBinaryProgram(
        hex,
        outAddr = 0x88,
        expected = fxZero
      )
      runBinaryProgram(
        hex,
        outAddr = 0x8c,
        expected = fxZero
      )
      runBinaryProgram(hex, outAddr = 0x90, expected = fxPosZ)
      runBinaryProgram(hex, outAddr = 0x94, expected = 0)
      runBinaryProgram(hex, outAddr = 0x98, expected = 0)
      runBinaryProgram(hex, outAddr = 0x9c, expected = fxNz)
    }
  }
}
