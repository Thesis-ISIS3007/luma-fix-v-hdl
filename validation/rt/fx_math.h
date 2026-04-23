#ifndef FX_RT_MATH_H
#define FX_RT_MATH_H

#include <stdint.h>

#include "../fx16q16.h"

// Scalar extras layered on top of the FX16Q16 instruction intrinsics. Kept
// header-only and free of FXDIV/FXMUL so callers can budget cycles per
// operation: only the explicit `fx*` intrinsics issue FX hardware ops.

#define FX_ZERO ((fx_t)0)
#define FX_HALF ((fx_t)(1 << (FX_FRAC_BITS - 1)))

// fx_from_double(1e-4) rounds to 6 in 16Q16; used as the |a| early-out in the
// Moller-Trumbore test where divide-by-zero would otherwise alias to a hit.
#define FX_EPS ((fx_t)6)

// fx_from_double(1e-6) rounds to 0; AABB code (deferred milestone) needs a
// safe non-zero floor, so anchor it here for future reuse.
#define FX_AABB_EPS ((fx_t)1)

// Saturation bounds used by AABB init_empty ("+inf" / "-inf" in fixed point).
// Using the full int32 range guarantees the first expand always clamps the
// real extent in, regardless of sign.
#define FX_MAX ((fx_t)INT32_MAX)
#define FX_MIN ((fx_t)INT32_MIN)

// Software 32x32 integer multiply. The LumaFixV core is pure RV32I (no M
// extension), and the freestanding link has no libgcc, so a plain C `a * b`
// generates an unresolved call to __mulsi3. This shift-add substitute keeps
// everything resolved inside the compilation unit.
static inline int32_t fx_imul_i32(int32_t a, int32_t b) {
  int neg = ((a < 0) ^ (b < 0)) ? 1 : 0;
  uint32_t ua = (a < 0) ? (uint32_t)(-a) : (uint32_t)a;
  uint32_t ub = (b < 0) ? (uint32_t)(-b) : (uint32_t)b;
  uint32_t r = 0;
  while (ub != 0u) {
    if ((ub & 1u) != 0u) {
      r += ua;
    }
    ua <<= 1;
    ub >>= 1;
  }
  return neg ? -(int32_t)r : (int32_t)r;
}

// Bit-by-bit integer sqrt of a 16Q16 fixed-point number.
//
// We need y such that y*y ~= x (16Q16), i.e. y = sqrt(x * 2^16) treated as a
// plain integer. Because (x << 16) needs 48 bits we lift the running value to
// uint64_t and walk 24 pairs of bits with the classic try-subtract recurrence:
// no FXDIV, no FXMUL, deterministic latency. Negative input is clamped to 0.
static inline fx_t fx_sqrt(fx_t x) {
  if (x <= 0) {
    return 0;
  }
  uint64_t n = ((uint64_t)(uint32_t)x) << FX_FRAC_BITS;
  uint64_t rem = 0;
  uint64_t root = 0;
  for (int i = 23; i >= 0; --i) {
    root <<= 1;
    rem = (rem << 2) | ((n >> (i * 2)) & 0x3ULL);
    uint64_t trial = (root << 1) | 1ULL;
    if (rem >= trial) {
      rem -= trial;
      root |= 1ULL;
    }
  }
  return (fx_t)root;
}

#endif
