package luma_fix_v

final case class CoreConfig(
    xlen: Int = 32,
    resetVector: BigInt = 0,
    debug: Boolean = true
) {
  require(xlen == 32, s"Only RV32 is currently supported, got xlen=$xlen")
}
