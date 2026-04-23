package luma_fix_v

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

import org.scalatest.funspec.AnyFunSpec

import chisel3.simulator.scalatest.ChiselSim

import test_utils.CornellRenderLogPaths
import test_utils._

// 360p here means 640x360 (the usual 16:9 "360p" frame size). Build the
// hex with:
//   make -C validation out/c_fx_rt_cornell_360p.hex
//   cp validation/out/c_fx_rt_cornell_360p.hex test/resources/programs/
//
// Wall time is on the order of hours (billions of clock steps). Without
// LUMAFIXV_CORNELL_360P=1 the test **cancels** immediately so `./mill test`
// stays fast. To run the full sim, set the env var, then decode:
//   export LUMAFIXV_CORNELL_360P=1
//   export LUMAFIXV_OUT_DIR=$PWD/scripts/out
//   python3 scripts/fx_rt_log_to_ppm.py $LUMAFIXV_OUT_DIR/cornell_360p.log.bin \
//     scripts/out/cornell_360p.ppm
class CFxRtCornell360pProgramSpec
    extends AnyFunSpec
    with ChiselSim
    with CBinaryProgramSupport {

  private val RenderW = 640
  private val RenderH = 360
  private val Sentinel = 0x5254524cL

  override protected val cProgramImemWords: Int = 16384
  override protected val cProgramDmemWords: Int = 16384

  describe("FX 16Q16 Cornell 360p render (opt-in)") {
    it(
      "fx_rt_cornell_360p: 640x360 framebuffer via MMIO (~12B cycles; set LUMAFIXV_CORNELL_360P=1)",
      CBinary
    ) {
      val run360 = Option(System.getenv("LUMAFIXV_CORNELL_360P")).map(_.toLowerCase)
      if (!run360.exists(Set("1", "true", "yes"))) {
        cancel(
          "Skipped: set LUMAFIXV_CORNELL_360P=1 to run the 640x360 sim (many hours of wall time)."
        )
      }

      val hex = "/programs/c_fx_rt_cornell_360p.hex"
      val cycles = 12_000_000_000L
      val logPath = CornellRenderLogPaths.p360Log

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
