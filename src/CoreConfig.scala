package luma_fix_v

final case class CoreConfig(
    xlen: Int = 32,
    resetVector: BigInt = 0,
    debug: Boolean = true
) {
  require(xlen == 32, s"Only RV32 is currently supported, got xlen=$xlen")
  require(resetVector >= 0, s"resetVector must be non-negative, got $resetVector")
  require(
    resetVector % 4 == 0,
    s"resetVector must be 4-byte aligned, got 0x${resetVector.toString(16)}"
  )
}
