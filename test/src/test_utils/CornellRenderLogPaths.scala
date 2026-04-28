package luma_fix_v.test_utils

import java.nio.file.Path
import java.nio.file.Paths

/** Stable locations for MMIO render logs so e2e scripts can `export
  * LUMAFIXV_OUT_DIR=.../scripts/out` and find files next to decoded images.
  * Mill may run tests from a worker sandbox, but a repo-relative `scripts/out`
  * is still used unless overridden.
  */
object CornellRenderLogPaths {

  def outDir: Path =
    Option(System.getenv("LUMAFIXV_OUT_DIR")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     => Paths.get("scripts", "out")
    }

  def previewLog: Path =
    Option(System.getenv("LUMAFIXV_CORNELL_PREVIEW_LOG")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     => outDir.resolve("cornell_smoke.log.bin")
    }

  def p360Log: Path =
    Option(System.getenv("LUMAFIXV_CORNELL_360_LOG")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     => outDir.resolve("cornell_360p.log.bin")
    }

  def p720Log: Path =
    Option(System.getenv("LUMAFIXV_CORNELL_720_LOG")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     => outDir.resolve("cornell_720p.log.bin")
    }

  /** Single-triangle MMIO sweep (fx_rt_triangle_render_smoke.c). */
  def triangleHwLog: Path =
    Option(System.getenv("LUMAFIXV_TRIANGLE_HW_LOG")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     => outDir.resolve("triangle_hw.log.bin")
    }

  def sampleLogFor(sampleHex: String): Path =
    Option(System.getenv("LUMAFIXV_SAMPLE_LOG")) match {
      case Some(s) if s.nonEmpty => Paths.get(s)
      case _                     =>
        val baseName = sampleHex.stripPrefix("/samples/").stripSuffix(".hex")
        outDir.resolve(s"${baseName}.log.bin")
    }
}
