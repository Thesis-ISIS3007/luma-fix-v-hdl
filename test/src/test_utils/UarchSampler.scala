package luma_fix_v.test_utils

/** Aggregates per-cycle uarch probe samples from simulation peeks. */
final class UarchSampler {
  private var cycles: Long = 0L
  private var retired: Long = 0L
  private var fetchFire: Long = 0L
  private var rawStall: Long = 0L
  private var exBusy: Long = 0L
  private var fxHoldFetch: Long = 0L
  private var memStall: Long = 0L
  private var pipeStall: Long = 0L
  private var flush: Long = 0L
  private var custom0Active: Long = 0L
  private val custom0OpCounts =
    scala.collection.mutable.Map.from(UarchStats.Custom0OpNames.map(_ -> 0L))

  def tick(peek: UarchProfilePeek): Unit = {
    cycles += 1L
    if (peek.retire) retired += 1L
    if (peek.fetchFire) fetchFire += 1L
    if (peek.rawStall) rawStall += 1L
    if (peek.exBusy) exBusy += 1L
    if (peek.fxHoldFetch) fxHoldFetch += 1L
    if (peek.memStall) memStall += 1L
    if (peek.pipeStall) pipeStall += 1L
    if (peek.flush) flush += 1L
    if (peek.custom0Active) custom0Active += 1L
    if (peek.custom0FxAdd) custom0OpCounts("FXADD") += 1L
    if (peek.custom0FxSub) custom0OpCounts("FXSUB") += 1L
    if (peek.custom0FxMul) custom0OpCounts("FXMUL") += 1L
    if (peek.custom0FxNeg) custom0OpCounts("FXNEG") += 1L
    if (peek.custom0Int2Fx) custom0OpCounts("INT2FX") += 1L
    if (peek.custom0Fx2Int) custom0OpCounts("FX2INT") += 1L
    if (peek.custom0FxAbs) custom0OpCounts("FXABS") += 1L
    if (peek.custom0FxDiv) custom0OpCounts("FXDIV") += 1L
  }

  def finish(program: String): UarchStats =
    UarchStats(
      program = program,
      cycles = cycles,
      retired = retired,
      fetchFire = fetchFire,
      rawStall = rawStall,
      exBusy = exBusy,
      fxHoldFetch = fxHoldFetch,
      memStall = memStall,
      pipeStall = pipeStall,
      flush = flush,
      custom0Active = custom0Active,
      custom0OpCounts = custom0OpCounts.toMap
    )
}

final case class UarchProfilePeek(
    retire: Boolean,
    fetchFire: Boolean,
    rawStall: Boolean,
    exBusy: Boolean,
    fxHoldFetch: Boolean,
    memStall: Boolean,
    pipeStall: Boolean,
    flush: Boolean,
    custom0Active: Boolean,
    custom0FxAdd: Boolean,
    custom0FxSub: Boolean,
    custom0FxMul: Boolean,
    custom0FxNeg: Boolean,
    custom0Int2Fx: Boolean,
    custom0Fx2Int: Boolean,
    custom0FxAbs: Boolean,
    custom0FxDiv: Boolean
)
