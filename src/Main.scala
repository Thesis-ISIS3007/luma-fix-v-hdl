package luma_fix_v

import circt.stage.ChiselStage

object Main extends App {
  ChiselStage.emitSystemVerilogFile(
    new LumaFixV(CoreConfig()),
    args = Array(
      "--target-dir",
      "generated"
    ),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info"
    )
  )
}
