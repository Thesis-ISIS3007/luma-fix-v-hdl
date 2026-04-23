package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CGcdProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {
  describe("RV32ICore C GCD program") {
    it("runs a GCC-compiled C GCD program from a binary memory file", CBinary) {
      runBinaryProgram(
        "/programs/c_gcd_smoke.hex",
        outAddr = 0x84,
        expected = 6
      )
    }
  }
}
