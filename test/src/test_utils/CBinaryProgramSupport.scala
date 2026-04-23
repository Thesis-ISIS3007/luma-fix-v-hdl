package luma_fix_v.test_utils

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

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

  /** Runs a C binary that streams pixels through the MMIO render log,
    * appending every valid render-log word to `logPath` as a little-endian
    * uint32. Returns the number of words captured.
    *
    * The simulator runs for the full `cycles` budget regardless of whether
    * the program already finished, so callers should size the budget to the
    * resolution baked into the C program.
    */
  protected def runBinaryProgramWithLog(
      resourcePath: String,
      logPath: Path,
      cycles: Int
  ): Long = {
    val resource = Option(getClass.getResource(resourcePath)).getOrElse {
      throw new RuntimeException(s"missing test resource $resourcePath")
    }
    val programBinPath = java.nio.file.Paths.get(resource.toURI).toString

    Files.createDirectories(logPath.getParent)
    val out = new BufferedOutputStream(
      new FileOutputStream(logPath.toFile, false)
    )
    var count: Long = 0L

    try {
      simulate(
        new CoreMemoryHarness(
          programFile = programBinPath,
          imemWords = cProgramImemWords,
          dmemWords = cProgramDmemWords
        )
      ) { c =>
        for (_ <- 0 until cycles) {
          c.clock.step()
          if (c.io.renderLogValid.peek().litToBoolean) {
            val word = c.io.renderLogData.peek().litValue.toLong & 0xffffffffL
            out.write((word & 0xff).toInt)
            out.write(((word >>> 8) & 0xff).toInt)
            out.write(((word >>> 16) & 0xff).toInt)
            out.write(((word >>> 24) & 0xff).toInt)
            count += 1L
          }
        }
      }
    } finally {
      out.flush()
      out.close()
    }
    count
  }
}
