package luma_fix_v

import circt.stage.ChiselStage

object Main extends App {
  private val parsed = MainArgs.parse(args)

  ChiselStage.emitSystemVerilogFile(
    new LumaFixV(parsed.cfg),
    args =
      (Seq("--target-dir", parsed.targetDir) ++ parsed.chiselForward).toArray,
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info"
    )
  )
}

private[luma_fix_v] object MainArgs {
  final case class Parsed(
      cfg: CoreConfig,
      targetDir: String,
      chiselForward: Seq[String]
  )

  def parse(argv: Array[String]): Parsed =
    go(argv.toIndexedSeq, CoreConfig(), "generated", Seq.empty)

  private def parseBigInt(s: String): BigInt =
    if (s.startsWith("0x") || s.startsWith("0X")) BigInt(s.drop(2), 16)
    else BigInt(s)

  private def parseOptValue(
      rest: Seq[String],
      name: String
  ): (String, Seq[String]) =
    rest match {
      case value +: tail => (value, tail)
      case _             =>
        throw new IllegalArgumentException(s"Missing value for $name")
    }

  private def parseInlineOpt(arg: String, name: String): String =
    arg.drop(name.length + 1)

  @scala.annotation.tailrec
  private def go(
      rest: Seq[String],
      cfg: CoreConfig,
      targetDir: String,
      forward: Seq[String]
  ): Parsed =
    rest match {
      case Seq() =>
        Parsed(cfg, targetDir, forward.reverse)
      case "--reset-vector" +: tail =>
        val (value, next) = parseOptValue(tail, "--reset-vector")
        go(next, cfg.copy(resetVector = parseBigInt(value)), targetDir, forward)
      case head +: tail if head.startsWith("--reset-vector=") =>
        val value = parseInlineOpt(head, "--reset-vector")
        go(tail, cfg.copy(resetVector = parseBigInt(value)), targetDir, forward)
      case "--target-dir" +: tail =>
        val (value, next) = parseOptValue(tail, "--target-dir")
        go(next, cfg, value, forward)
      case head +: tail if head.startsWith("--target-dir=") =>
        val value = parseInlineOpt(head, "--target-dir")
        go(tail, cfg, value, forward)
      case head +: tail =>
        go(tail, cfg, targetDir, head +: forward)
    }
}
