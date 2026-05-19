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

  /** Optional hard cap on clock steps (scripts/run_samples.sh sets this). If
    * unset, simulation runs until the program writes MMIO done (no bound).
    */
  private def maxSimStepsFromEnv: Long =
    sys.env
      .get("LUMAFIXV_SAMPLE_MAX_STEPS")
      .filter(_.nonEmpty)
      .flatMap { s =>
        try {
          val v = s.toLong
          if (v <= 0L) None else Some(v)
        } catch { case _: NumberFormatException => None }
      }
      .getOrElse(Long.MaxValue)

  private def peekUarch(c: CoreMemoryHarness): UarchProfilePeek =
    UarchProfilePeek(
      retire = c.io.uarch.retire.peek().litToBoolean,
      fetchFire = c.io.uarch.fetchFire.peek().litToBoolean,
      rawStall = c.io.uarch.rawStall.peek().litToBoolean,
      exBusy = c.io.uarch.exBusy.peek().litToBoolean,
      fxHoldFetch = c.io.uarch.fxHoldFetch.peek().litToBoolean,
      memStall = c.io.uarch.memStall.peek().litToBoolean,
      pipeStall = c.io.uarch.pipeStall.peek().litToBoolean,
      flush = c.io.uarch.flush.peek().litToBoolean,
      custom0Active = c.io.uarch.custom0Active.peek().litToBoolean,
      custom0FxAdd = c.io.uarch.custom0Ops.fxAdd.peek().litToBoolean,
      custom0FxSub = c.io.uarch.custom0Ops.fxSub.peek().litToBoolean,
      custom0FxMul = c.io.uarch.custom0Ops.fxMul.peek().litToBoolean,
      custom0FxNeg = c.io.uarch.custom0Ops.fxNeg.peek().litToBoolean,
      custom0Int2Fx = c.io.uarch.custom0Ops.int2Fx.peek().litToBoolean,
      custom0Fx2Int = c.io.uarch.custom0Ops.fx2Int.peek().litToBoolean,
      custom0FxAbs = c.io.uarch.custom0Ops.fxAbs.peek().litToBoolean,
      custom0FxDiv = c.io.uarch.custom0Ops.fxDiv.peek().litToBoolean
    )

  private def sampleUarch(c: CoreMemoryHarness, sampler: UarchSampler): Unit =
    if (UarchStats.uarchEnabled) {
      sampler.tick(peekUarch(c))
    }

  private def reportUarch(
      program: String,
      sampler: UarchSampler
  ): UarchStats = {
    val stats = sampler.finish(program)
    if (UarchStats.uarchEnabled) {
      println(stats.logLine)
      UarchStats.maybeWriteFromEnv(stats)
    }
    stats
  }

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
    val sampler = new UarchSampler
    simulate(
      new CoreMemoryHarness(
        programFile = programBinPath,
        imemWords = cProgramImemWords,
        dmemWords = cProgramDmemWords
      )
    ) { c =>
      c.io.dmemPeekAddr.poke(outAddr.U)
      var steps = 0L
      val cap = maxSimStepsFromEnv
      while (!c.io.programDone.peek().litToBoolean && steps < cap) {
        c.clock.step()
        steps += 1L
        sampleUarch(c, sampler)
      }
      assert(
        c.io.programDone.peek().litToBoolean,
        s"program did not finish within LUMAFIXV_SAMPLE_MAX_STEPS=$cap steps (resource=$resourcePath)"
      )
      c.io.dmemPeekData.expect(expected.U)
      c.io.programStatus.expect(0.U)
      val status = c.io.programStatus.peek().litValue
      println(
        s"[cbinary] program=$resourcePath steps=$steps status=0x${status.toString(16)} outAddr=0x${outAddr.toHexString}"
      )
      reportUarch(resourcePath, sampler)
      stepsOut = steps
    }
    stepsOut
  }

  /** Runs a C binary that streams pixels through the MMIO render log, appending
    * every valid render-log word to `logPath` as a little-endian uint32.
    * Returns the number of words captured and clock steps.
    *
    * The simulator runs until the program emits MMIO done, or until
    * LUMAFIXV_SAMPLE_MAX_STEPS clock steps if that env var is set.
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
    val sampler = new UarchSampler

    try {
      simulate(
        new CoreMemoryHarness(
          programFile = programBinPath,
          imemWords = cProgramImemWords,
          dmemWords = cProgramDmemWords
        )
      ) { c =>
        val cap = maxSimStepsFromEnv
        while (!c.io.programDone.peek().litToBoolean && steps < cap) {
          c.clock.step()
          steps += 1L
          sampleUarch(c, sampler)
          if (c.io.renderLogValid.peek().litToBoolean) {
            val word = c.io.renderLogData.peek().litValue.toLong & 0xffffffffL
            out.write((word & 0xff).toInt)
            out.write(((word >>> 8) & 0xff).toInt)
            out.write(((word >>> 16) & 0xff).toInt)
            out.write(((word >>> 24) & 0xff).toInt)
            count += 1L
          }
        }
        assert(
          c.io.programDone.peek().litToBoolean,
          s"program did not finish within LUMAFIXV_SAMPLE_MAX_STEPS=$cap steps (resource=$resourcePath, renderLogWords=$count)"
        )
        c.io.programStatus.expect(0.U)
        reportUarch(resourcePath, sampler)
      }
    } finally {
      out.flush()
      out.close()
    }
    println(
      s"[cbinary-log] program=$resourcePath steps=$steps words=$count path=$logPath"
    )
    (count, steps)
  }
}
