package luma_fix_v.test_utils

import org.scalatest.Tag

object Validation extends Tag("Validation") {
  def enabled: Boolean = {
    val prop = sys.props
      .get("luma.validation")
      .exists(v => v == "1" || v.equalsIgnoreCase("true"))
    val env = sys.env
      .get("LUMAFIXV_VALIDATION")
      .exists(v => v == "1" || v.equalsIgnoreCase("true"))
    prop || env
  }
}
