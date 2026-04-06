package prototype

import circt.stage.ChiselStage

object Main extends App {
  ChiselStage.emitSystemVerilogFile(
    new LumaFixV,
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
