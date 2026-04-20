package luma_fix_v

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

import java.nio.file.Paths

class LoadMemoryFromFileProgramSpec extends AnyFunSpec with ChiselSim {
  describe("RV32ICore load memory from file program") {
    it("loads a program image with loadMemoryFromFile") {
      val resource =
        Option(getClass.getResource("/programs/add_store_load.hex"))
          .getOrElse(fail("missing test resource /programs/add_store_load.hex"))
      val programHexPath = Paths.get(resource.toURI).toString

      simulate(
        new CoreMemoryHarness(
          programHex = programHexPath,
          imemWords = 64,
          dmemWords = 64
        )
      ) { c =>
        c.io.dmemPeekAddr.poke(0.U)

        for (_ <- 0 until 60) {
          c.clock.step()
        }

        c.io.dmemPeekData.expect(12.U)
      }
    }
  }
}
