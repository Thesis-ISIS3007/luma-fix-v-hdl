package luma_fix_v.test_utils

import luma_fix_v.CoreMemoryHarness

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

trait CBinaryProgramSupport { this: ChiselSim =>
  protected val cProgramImemWords: Int = 256
  protected val cProgramDmemWords: Int = 256

  protected def runBinaryProgram(
      resourcePath: String,
      outAddr: Int,
      expected: BigInt,
      cycles: Int = 1000
  ): Unit = {
    val resource = Option(getClass.getResource(resourcePath)).getOrElse {
      throw new RuntimeException(s"missing test resource $resourcePath")
    }
    val programBinPath = java.nio.file.Paths.get(resource.toURI).toString

    simulate(
      new CoreMemoryHarness(
        programFile = programBinPath,
        imemWords = cProgramImemWords,
        dmemWords = cProgramDmemWords
      )
    ) { c =>
      c.io.dmemPeekAddr.poke(outAddr.U)
      for (_ <- 0 until cycles) {
        c.clock.step()
      }
      c.io.dmemPeekData.expect(expected.U)
    }
  }
}
