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
      expected: BigInt
  ): Long = {
    val resource = Option(getClass.getResource(resourcePath)).getOrElse {
      throw new RuntimeException(s"missing test resource $resourcePath")
    }
    val programBinPath = java.nio.file.Paths.get(resource.toURI).toString

    var stepsOut = 0L
    simulate(
      new CoreMemoryHarness(
        programFile = programBinPath,
        imemWords = cProgramImemWords,
        dmemWords = cProgramDmemWords
      )
    ) { c =>
      c.io.dmemPeekAddr.poke(outAddr.U)
      var steps = 0L
      while (!c.io.programDone.peek().litToBoolean) {
        c.clock.step()
        steps += 1L
      }
      c.io.dmemPeekData.expect(expected.U)
      c.io.programStatus.expect(0.U)
      val status = c.io.programStatus.peek().litValue
      println(
        s"[cbinary] program=$resourcePath steps=$steps status=0x${status.toString(16)} outAddr=0x${outAddr.toHexString}"
      )
      stepsOut = steps
    }
    stepsOut
  }

  /** Runs a C binary that streams pixels through the MMIO render log,
    * appending every valid render-log word to `logPath` as a little-endian
    * uint32. Returns the number of words captured.
    *
    * The simulator runs until the program emits MMIO done.
    */
  protected def runBinaryProgramWithLog(
      resourcePath: String,
      logPath: Path
  ): (Long, Long) = {
    val resource = Option(getClass.getResource(resourcePath)).getOrElse {
      throw new RuntimeException(s"missing test resource $resourcePath")
    }
    val programBinPath = java.nio.file.Paths.get(resource.toURI).toString

    Files.createDirectories(logPath.getParent)
    val out = new BufferedOutputStream(
      new FileOutputStream(logPath.toFile, false)
    )
    var count: Long = 0L
    var steps: Long = 0L

    try {
      simulate(
        new CoreMemoryHarness(
          programFile = programBinPath,
          imemWords = cProgramImemWords,
          dmemWords = cProgramDmemWords
        )
      ) { c =>
        while (!c.io.programDone.peek().litToBoolean) {
          c.clock.step()
          steps += 1L
          if (c.io.renderLogValid.peek().litToBoolean) {
            val word = c.io.renderLogData.peek().litValue.toLong & 0xffffffffL
            out.write((word & 0xff).toInt)
            out.write(((word >>> 8) & 0xff).toInt)
            out.write(((word >>> 16) & 0xff).toInt)
            out.write(((word >>> 24) & 0xff).toInt)
            count += 1L
          }
        }
        c.io.programStatus.expect(0.U)
      }
    } finally {
      out.flush()
      out.close()
    }
    println(s"[cbinary-log] program=$resourcePath steps=$steps words=$count path=$logPath")
    (count, steps)
  }
}
