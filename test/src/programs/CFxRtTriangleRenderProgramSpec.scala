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

  override protected val cProgramImemWords: Int = 16384
  override protected val cProgramDmemWords: Int = 16384

  describe("FX 16Q16 triangle render (MMIO log)") {
    it(
      "fx_rt_triangle_render_smoke: streams a 32x24 image through the render log",
      CBinary
    ) {
      val hex = "/programs/c_fx_rt_triangle_render_smoke.hex"
      // No BVH; much cheaper per pixel than Cornell — a few M cycles total.
      val cycles = 5_000_000L
      val logPath = CornellRenderLogPaths.triangleHwLog

      val captured = runBinaryProgramWithLog(hex, logPath, cycles)
      val expected = 2L + RenderW.toLong * RenderH.toLong
      assert(
        captured == expected,
        s"expected $expected render-log words, got $captured"
      )
      assert(
        Files.size(logPath) == expected * 4L,
        s"log file size ${Files.size(logPath)} != ${expected * 4L} bytes"
      )
    }
  }
}
