package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class CAbiSmokeProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {
  override protected val cProgramImemWords: Int = 512
  override protected val cProgramDmemWords: Int = 512

  describe("RV32ICore C ABI smoke program") {
    it("runs a GCC-compiled C program from a binary memory file", CBinary) {
      runBinaryProgram(
        "/validation/c_abi_smoke.hex",
        outAddr = 0x80,
        expected = 55
      )
    }
  }
}
