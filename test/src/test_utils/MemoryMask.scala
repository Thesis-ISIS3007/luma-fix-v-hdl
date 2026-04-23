package luma_fix_v.test_utils

trait MemoryMask {

  /** Apply a 4-bit byte-write mask (LSB = byte 0) over `oldWord` using bytes
    * from `writeData`, returning the updated 32-bit word.
    */
  def applyMask(oldWord: BigInt, writeData: BigInt, mask: Int): BigInt = {
    var out = oldWord
    for (i <- 0 until 4) {
      if (((mask >> i) & 0x1) == 1) {
        val byteMask = BigInt(0xff) << (i * 8)
        out = (out & ~byteMask) | (writeData & byteMask)
      }
    }
    out & BigInt("FFFFFFFF", 16)
  }
}
