package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils._

class DotProductKernelProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with ISATestSupport {
  describe("RV32ICore dot-product kernel program") {
    it("dot-product style kernel with shifts") {
      val pb = new ProgramBuilder()

      pb.emit(iType(0x180, 0, 0x0, 1, 0x13)) // x1 = base
      pb.nops(4)
      pb.emit(iType(0, 1, 0x2, 2, 0x03)) // x2 = a0
      pb.nops(1)
      pb.emit(iType(4, 1, 0x2, 3, 0x03)) // x3 = a1
      pb.nops(1)
      pb.emit(iType(8, 1, 0x2, 4, 0x03)) // x4 = a2
      pb.nops(3)
      pb.emit(iType(1, 3, 0x1, 5, 0x13)) // x5 = a1 << 1
      pb.emit(rType(0x00, 5, 2, 0x0, 6, 0x33)) // x6 = a0 + 2*a1
      pb.emit(rType(0x00, 4, 6, 0x0, 7, 0x33)) // x7 = a0 + 2*a1 + a2

      val init = Map[Int, BigInt](
        0x180 -> 4,
        0x184 -> 7,
        0x188 -> 3
      )
      val (regs, _) = runProgram(pb.result, maxCycles = 90, initialData = init)
      assert(regs(7) == 21)
    }
  }
}
