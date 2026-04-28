package luma_fix_v

import java.nio.file.Files

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils.CornellRenderLogPaths
import test_utils._

// MMIO framebuffer from validation/fx_rt_triangle_render_smoke.c (32x24,
// one ray-triangle test per pixel, no BVH). Log -> PNG via
// scripts/fx_rt_log_to_png.py (PEP 723 + Typer; `uv run` or python3.14 + typer).
class CFxRtTriangleRenderProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  private val RenderW = 32
  private val RenderH = 24
  private val DefaultHex = "/samples/c_triangle_render_32x24.hex"
  private val DefaultImemWords = 16384
  private val DefaultDmemWords = 16384

  override protected val cProgramImemWords: Int =
    Option(System.getenv("LUMAFIXV_SAMPLE_IMEM_WORDS"))
      .filter(_.nonEmpty)
      .map(_.toInt)
      .getOrElse(DefaultImemWords)
  override protected val cProgramDmemWords: Int =
    Option(System.getenv("LUMAFIXV_SAMPLE_DMEM_WORDS"))
      .filter(_.nonEmpty)
      .map(_.toInt)
      .getOrElse(DefaultDmemWords)

  describe("FX 16Q16 triangle render (MMIO log)") {
    it(
      "fx_rt_triangle_render_smoke: streams a 32x24 image through the render log",
      CBinary
    ) {
      val hex = Option(System.getenv("LUMAFIXV_SAMPLE_HEX"))
        .filter(_.nonEmpty)
        .map { h => if (h.startsWith("/")) h else s"/samples/$h" }
        .getOrElse(DefaultHex)
      val logPath = CornellRenderLogPaths.sampleLogFor(hex)

      val (captured, steps) = runBinaryProgramWithLog(hex, logPath)
      val expected = 2L + RenderW.toLong * RenderH.toLong
      assert(
        captured == expected,
        s"expected $expected render-log words, got $captured"
      )
      assert(
        Files.size(logPath) == expected * 4L,
        s"log file size ${Files.size(logPath)} != ${expected * 4L} bytes"
      )
      println(s"[cbinary-log-assert] hex=$hex words=$captured steps=$steps")
    }
  }
}
