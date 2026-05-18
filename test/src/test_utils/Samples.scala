package luma_fix_v.test_utils

import org.scalatest.Tag

object Samples extends Tag("Samples") {
  def enabled: Boolean = {
    val prop = sys.props
      .get("luma.samples")
      .exists(v => v == "1" || v.equalsIgnoreCase("true"))
    val env = sys.env
      .get("LUMAFIXV_SAMPLES")
      .exists(v => v == "1" || v.equalsIgnoreCase("true"))
    prop || env
  }
}
