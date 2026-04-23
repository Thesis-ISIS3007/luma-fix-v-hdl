package luma_fix_v

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils.CornellRenderLogPaths
import test_utils._

// 720p = 1280x720 (16:9). Build:
//   make -C validation out/c_fx_rt_cornell_720p.hex
//   cp validation/out/c_fx_rt_cornell_720p.hex test/resources/programs/
//
// ~4x the pixels of 360p; wall time can be **days** on ChiselSim. Without
// LUMAFIXV_CORNELL_720P=1 the test cancels immediately.
class CFxRtCornell720pProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  private val RenderW = 1280
  private val RenderH = 720
  private val Sentinel = 0x5254524cL

  override protected val cProgramImemWords: Int = 16384
  override protected val cProgramDmemWords: Int = 16384

  describe("FX 16Q16 Cornell 720p render (opt-in)") {
    it(
      "fx_rt_cornell_720p: 1280x720 framebuffer via MMIO (~55B cycles; set LUMAFIXV_CORNELL_720P=1)",
      CBinary
    ) {
      val run720 = Option(System.getenv("LUMAFIXV_CORNELL_720P")).map(_.toLowerCase)
      if (!run720.exists(Set("1", "true", "yes"))) {
        cancel(
          "Skipped: set LUMAFIXV_CORNELL_720P=1 to run the 1280x720 sim (multi-day wall time possible)."
        )
      }

      val hex = "/programs/c_fx_rt_cornell_720p.hex"
      // ~4x pixel count vs 360p; budget ~4x the 360p 12B cycle allowance.
      val cycles = 55_000_000_000L
      val logPath = CornellRenderLogPaths.p720Log

      val captured = runBinaryProgramWithLog(hex, logPath, cycles)
      val expectedWords = 2L + RenderW.toLong * RenderH.toLong
      assert(
        captured == expectedWords,
        s"expected $expectedWords render-log words, got $captured"
      )

      val bytes = Files.readAllBytes(logPath)
      val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      val header = buf.getInt().toLong & 0xffffffffL
      val width = (header >>> 16) & 0xffffL
      val height = header & 0xffffL
      assert(
        width == RenderW.toLong && height == RenderH.toLong,
        s"header ${width}x${height}, expected ${RenderW}x${RenderH}"
      )
      val sentinel = buf.getInt().toLong & 0xffffffffL
      assert(sentinel == Sentinel, f"sentinel 0x$sentinel%08x")
    }
  }
}
