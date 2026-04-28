package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

// End-to-end test for the FX32 BVH scaffold: builds a 4-triangle BVH with
// two xy quads (front at z=0, back at z=-1), then traverses it with a ray
// aimed straight down. With FX_BVH_MAX_LEAF_SZ=2 the builder must split,
// so we also assert numNodes > 1 (expected value = 3: root + 2 leaves).
//
// Sizing notes:
//   - Binary is ~15 KiB at -O0 (inlined fx_sqrt + BVH build + software
//     int multiply), so imem/dmem are 8192 words (32 KiB) to match the
//     linker's RAM region.
//   - The cycle budget has to absorb: two recursive build calls, 11 SAH
//     bucket evaluations (each calling the shift-add fx_imul_i32 twice),
//     3 AABB tests along the traversal path (3 FXDIVs each), one
//     successful ray-triangle intersection (with a normalize / fx_sqrt
//     call), and one rejected triangle test.
class CFxRtBvhProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  override protected val cProgramImemWords: Int = 8192
  override protected val cProgramDmemWords: Int = 8192

  describe("FX 16Q16 BVH scaffold") {
    it(
      "fx_rt_bvh_smoke: builds a 4-triangle BVH and traverses to a front-quad hit",
      CBinary
    ) {
      val hex = "/samples/c_fx_rt_bvh_smoke.hex"
      val fxOne = BigInt("10000", 16)
      val fxQuarter = BigInt("4000", 16)

      runBinaryProgram(hex, outAddr = 0x80, expected = 3)
      runBinaryProgram(hex, outAddr = 0x84, expected = 1)
      runBinaryProgram(hex, outAddr = 0x88, expected = fxOne)
      runBinaryProgram(
        hex,
        outAddr = 0x8c,
        expected = fxQuarter
      )
      runBinaryProgram(
        hex,
        outAddr = 0x90,
        expected = fxQuarter
      )
      runBinaryProgram(hex, outAddr = 0x94, expected = 0)
      runBinaryProgram(hex, outAddr = 0x98, expected = 0)
      runBinaryProgram(hex, outAddr = 0x9c, expected = 0)
      runBinaryProgram(hex, outAddr = 0xa0, expected = fxOne)
    }
  }
}
