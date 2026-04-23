#ifndef FX_RT_VEC3_H
#define FX_RT_VEC3_H

#include <stdint.h>

#include "fx_math.h"

// 3-component 16Q16 vector. Operations are deliberately small inline wrappers
// around the FX intrinsics so the reader can match each op to a single
// hardware micro-op (or a short fxmul chain for dot/cross). Range expectation:
// inputs in [-4, 4] keep dot/cross intermediates well inside 16Q16 limits.

typedef struct {
  fx_t x;
  fx_t y;
  fx_t z;
} fx_vec3_t;

static inline fx_vec3_t fx_vec3_make(fx_t x, fx_t y, fx_t z) {
  fx_vec3_t v = {x, y, z};
  return v;
}

static inline fx_vec3_t fx_vec3_zero(void) {
  return fx_vec3_make(FX_ZERO, FX_ZERO, FX_ZERO);
}

static inline fx_vec3_t fx_vec3_add(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fxadd(a.x, b.x), fxadd(a.y, b.y), fxadd(a.z, b.z));
}

static inline fx_vec3_t fx_vec3_sub(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fxsub(a.x, b.x), fxsub(a.y, b.y), fxsub(a.z, b.z));
}

static inline fx_vec3_t fx_vec3_mul_elem(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fxmul(a.x, b.x), fxmul(a.y, b.y), fxmul(a.z, b.z));
}

// Multiplies each lane by a scalar. The reference C++ provides both `v * t`
// and `v / t`; we expose only the multiply form so callers explicitly invert
// the divisor with fxdiv at the call site (one FXDIV stall vs three).
static inline fx_vec3_t fx_vec3_scale(fx_vec3_t v, fx_t s) {
  return fx_vec3_make(fxmul(v.x, s), fxmul(v.y, s), fxmul(v.z, s));
}

static inline fx_vec3_t fx_vec3_neg(fx_vec3_t v) {
  return fx_vec3_make(fxneg(v.x), fxneg(v.y), fxneg(v.z));
}

static inline fx_t fx_vec3_dot(fx_vec3_t a, fx_vec3_t b) {
  return fxadd(fxadd(fxmul(a.x, b.x), fxmul(a.y, b.y)), fxmul(a.z, b.z));
}

static inline fx_vec3_t fx_vec3_cross(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fxsub(fxmul(a.y, b.z), fxmul(a.z, b.y)),
                      fxsub(fxmul(a.z, b.x), fxmul(a.x, b.z)),
                      fxsub(fxmul(a.x, b.y), fxmul(a.y, b.x)));
}

// |v| is computed with fx_sqrt (no FX hw op) and the reciprocal with a single
// FXDIV. Zero-length input returns the zero vector instead of dividing by 0.
static inline fx_vec3_t fx_vec3_normalize(fx_vec3_t v) {
  fx_t len2 = fx_vec3_dot(v, v);
  fx_t len = fx_sqrt(len2);
  if (len == 0) {
    return fx_vec3_zero();
  }
  fx_t inv = fxdiv(FX_ONE, len);
  return fx_vec3_scale(v, inv);
}

static inline fx_t fx_min(fx_t a, fx_t b) { return (a < b) ? a : b; }
static inline fx_t fx_max(fx_t a, fx_t b) { return (a > b) ? a : b; }

static inline fx_vec3_t fx_vec3_min(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fx_min(a.x, b.x), fx_min(a.y, b.y), fx_min(a.z, b.z));
}

static inline fx_vec3_t fx_vec3_max(fx_vec3_t a, fx_vec3_t b) {
  return fx_vec3_make(fx_max(a.x, b.x), fx_max(a.y, b.y), fx_max(a.z, b.z));
}

// Lane accessor used by the (deferred) BVH/AABB code; kept here so that
// landing AABB later requires no edits to fx_vec3.h.
static inline fx_t fx_vec3_comp(fx_vec3_t v, uint32_t axis) {
  if (axis == 0) {
    return v.x;
  }
  if (axis == 1) {
    return v.y;
  }
  return v.z;
}

#endif
