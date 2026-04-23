package luma_fix_v

import chisel3._
import chisel3.util._

// Multi-cycle 16Q16 fixed-point divider used by the EX stage for FXDIV.
//
// 16Q16 / 16Q16 = (lhs * 2^16) / rhs in raw two's-complement integers, so we
// shift the unsigned absolute value of `lhs` left by 16 to get a 48-bit
// dividend, then perform a restoring long division against `|rhs|`. The sign
// of the quotient is the XOR of the operand signs; a zero divisor returns 0
// (matches the "well-defined garbage" semantics documented in docs/ISA.md,
// no trap in v2). All overflow wraps modulo 2^32 - the upper bits of the
// 48-bit raw quotient are silently dropped to 32.
//
// Cycle accounting: 1 setup cycle (sIdle -> sBusy) + 48 iteration cycles in
// sBusy (one per dividend bit) + 1 result cycle (sDone, where `done` is
// asserted) = 50 cycles from `start` to `done`. `done` is a one-cycle pulse;
// the EX stage latches the result so the divider can return to sIdle and
// accept a new FXDIV next cycle.
private object FxDivUnitParams {
  val IterBits: Int = 48
}
class FxDivUnit extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val lhs = Input(UInt(32.W))
    val rhs = Input(UInt(32.W))
    val out = Output(UInt(32.W))
    val done = Output(Bool())
    val busy = Output(Bool())
  })

  private def absVal(x: UInt): UInt = Mux(x(31), (~x).asUInt + 1.U, x)

  val sIdle :: sBusy :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Numerator is |lhs| << 16, a 48-bit value living in the low bits of a
  // 48-bit register that we shift left one bit per iteration.
  val numerator = Reg(UInt(FxDivUnitParams.IterBits.W))
  val divisor = Reg(UInt(32.W))
  // Quotient is technically up to 48 bits when the divisor is sub-1.0; we
  // accumulate it in 48 bits and keep only the low 32 on output (wrap).
  val quotient = Reg(UInt(FxDivUnitParams.IterBits.W))
  val remainder = Reg(UInt(33.W))
  val signNeg = Reg(Bool())
  val divByZero = Reg(Bool())
  val count = Reg(UInt(log2Ceil(FxDivUnitParams.IterBits + 1).W))

  // Output is the low 32 bits of the (possibly negated) quotient. Negation is
  // done modulo 2^32 so it matches Scala's two's-complement wrap semantics.
  val quotient32 = quotient(31, 0)
  io.busy := state =/= sIdle
  io.done := state === sDone
  io.out := Mux(
    divByZero,
    0.U(32.W),
    Mux(signNeg, ((~quotient32).asUInt + 1.U)(31, 0), quotient32)
  )

  switch(state) {
    is(sIdle) {
      when(io.start) {
        val lhsAbs = absVal(io.lhs)
        val rhsAbs = absVal(io.rhs)
        // (|lhs| << 16) as a 48-bit dividend.
        numerator := Cat(lhsAbs, 0.U(16.W))
        divisor := rhsAbs
        signNeg := io.lhs(31) ^ io.rhs(31)
        divByZero := io.rhs === 0.U
        quotient := 0.U
        remainder := 0.U
        count := 0.U
        state := sBusy
      }
    }
    is(sBusy) {
      // Restoring division step: shift the next numerator bit into the
      // remainder, trial-subtract the divisor, and commit only if non-negative.
      val msb = numerator(FxDivUnitParams.IterBits - 1)
      val shifted = Cat(remainder(31, 0), msb)
      val sub = shifted -& divisor
      val take = !sub(32)
      remainder := Mux(take, sub, shifted)
      numerator := Cat(numerator(FxDivUnitParams.IterBits - 2, 0), 0.U(1.W))
      quotient := Cat(quotient(FxDivUnitParams.IterBits - 2, 0), take.asUInt)
      count := count + 1.U
      when(count === (FxDivUnitParams.IterBits - 1).U) {
        state := sDone
      }
    }
    is(sDone) {
      // Result is presented for exactly one cycle. The EX-stage controller
      // latches it into a side register so the rest of the pipeline can stall
      // arbitrarily long, and so a back-to-back FXDIV can re-`start` cleanly
      // from sIdle without seeing a stale `done` from the previous quotient.
      state := sIdle
    }
  }
}
