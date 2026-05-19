package luma_fix_v.test_utils

import java.nio.file.{Files, Path, Paths}
final case class UarchStats(
    program: String,
    cycles: Long,
    retired: Long,
    fetchFire: Long,
    rawStall: Long,
    exBusy: Long,
    fxHoldFetch: Long,
    memStall: Long,
    pipeStall: Long,
    flush: Long,
    custom0Active: Long,
    custom0OpCounts: Map[String, Long] = Map.empty
) {
  val ipc: Double =
    if (cycles > 0L) retired.toDouble / cycles.toDouble else 0.0

  def fractionOfCycles(count: Long): Double =
    if (cycles > 0L) count.toDouble / cycles.toDouble else 0.0

  def custom0OpFractions: Map[String, Double] =
    custom0OpCounts.view.mapValues(fractionOfCycles).toMap

  def fractions: Map[String, Double] = Map(
    "fetch_fire" -> fractionOfCycles(fetchFire),
    "raw_stall" -> fractionOfCycles(rawStall),
    "ex_busy" -> fractionOfCycles(exBusy),
    "fx_hold_fetch" -> fractionOfCycles(fxHoldFetch),
    "mem_stall" -> fractionOfCycles(memStall),
    "pipe_stall" -> fractionOfCycles(pipeStall),
    "flush" -> fractionOfCycles(flush),
    "custom0_active" -> fractionOfCycles(custom0Active)
  )

  def counters: Map[String, Long] = Map(
    "fetch_fire" -> fetchFire,
    "raw_stall" -> rawStall,
    "ex_busy" -> exBusy,
    "fx_hold_fetch" -> fxHoldFetch,
    "mem_stall" -> memStall,
    "pipe_stall" -> pipeStall,
    "flush" -> flush,
    "custom0_active" -> custom0Active
  )

  def logLine: String =
    s"[uarch] program=$program cycles=$cycles retired=$retired ipc=${"%.4f".format(ipc)} " +
      s"fetch_fire=$fetchFire raw_stall=$rawStall ex_busy=$exBusy " +
      s"fx_hold_fetch=$fxHoldFetch mem_stall=$memStall pipe_stall=$pipeStall flush=$flush " +
      s"custom0_active=$custom0Active"

  def toJson: String = {
    val counterEntries =
      counters.map { case (k, v) => s""""$k": $v""" }.mkString(", ")
    val fractionEntries = fractions
      .map { case (k, v) =>
        s""""$k": ${"%.6f".format(v)}"""
      }
      .mkString(", ")
    val opCountEntries = UarchStats.Custom0OpNames
      .map { name =>
        s""""$name": ${custom0OpCounts.getOrElse(name, 0L)}"""
      }
      .mkString(", ")
    val opFracEntries = UarchStats.Custom0OpNames
      .map { name =>
        val frac = custom0OpFractions.getOrElse(name, 0.0)
        s""""$name": ${"%.6f".format(frac)}"""
      }
      .mkString(", ")
    s"""{
  "program": ${escapeJson(program)},
  "cycles": $cycles,
  "retired": $retired,
  "ipc": ${"%.6f".format(ipc)},
  "counters": { $counterEntries },
  "fractions": { $fractionEntries },
  "custom0_ops": { $opCountEntries },
  "custom0_op_fractions": { $opFracEntries }
}"""
  }

  private def escapeJson(s: String): String = {
    val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
    s"\"$escaped\""
  }
}

object UarchStats {
  val Custom0OpNames: Seq[String] =
    Seq(
      "FXADD",
      "FXSUB",
      "FXMUL",
      "FXNEG",
      "INT2FX",
      "FX2INT",
      "FXABS",
      "FXDIV"
    )

  def writeJson(stats: UarchStats, path: Path): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(
      path,
      stats.toJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )
  }

  def maybeWriteFromEnv(stats: UarchStats): Unit = {
    uarchStatsPathFromEnv(stats.program).foreach { path =>
      writeJson(stats, path)
    }
  }

  def uarchStatsPathFromEnv(program: String): Option[Path] =
    sys.env
      .get("LUMAFIXV_UARCH_STATS")
      .filter(_.nonEmpty)
      .map { raw =>
        val path = Paths.get(raw)
        if (raw.endsWith("/") || Files.isDirectory(path)) {
          val dir =
            if (raw.endsWith("/")) path else path
          Files.createDirectories(dir)
          dir.resolve(s"${sanitizeProgramName(program)}.json")
        } else {
          path
        }
      }

  private def sanitizeProgramName(program: String): String =
    program.stripPrefix("/").replace('/', '_')

  def uarchEnabled: Boolean =
    sys.env.get("LUMAFIXV_UARCH") match {
      case Some(v)
          if v == "0" || v.equalsIgnoreCase("false") || v.equalsIgnoreCase(
            "no"
          ) =>
        false
      case Some(v) =>
        v == "1" || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes")
      case None => true
    }
}
