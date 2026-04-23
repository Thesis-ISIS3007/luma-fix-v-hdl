#ifndef FX_RT_AABB_H
#define FX_RT_AABB_H

#include "fx_triangle.h"
#include "fx_vec3.h"

// Axis-aligned bounding box in 16Q16. Mirrors the reference C++ AABB: an
// empty box is encoded by min = +inf, max = -inf so the first `expand` call
// correctly snaps the extent to the real geometry.

typedef struct {
  fx_vec3_t minB;
  fx_vec3_t maxB;
} fx_aabb_t;

static inline void fx_aabb_init_empty(fx_aabb_t *a) {
  a->minB = fx_vec3_make(FX_MAX, FX_MAX, FX_MAX);
  a->maxB = fx_vec3_make(FX_MIN, FX_MIN, FX_MIN);
}

static inline void fx_aabb_expand_point(fx_aabb_t *a, fx_vec3_t p) {
  a->minB = fx_vec3_min(a->minB, p);
  a->maxB = fx_vec3_max(a->maxB, p);
}

static inline void fx_aabb_expand_aabb(fx_aabb_t *a, const fx_aabb_t *o) {
  a->minB = fx_vec3_min(a->minB, o->minB);
  a->maxB = fx_vec3_max(a->maxB, o->maxB);
}

// center = min + (max - min) * 0.5 — no FXDIV, the halving is folded into a
// single fxmul against FX_HALF.
static inline fx_vec3_t fx_aabb_center(const fx_aabb_t *a) {
  fx_vec3_t extent = fx_vec3_sub(a->maxB, a->minB);
  return fx_vec3_add(a->minB, fx_vec3_scale(extent, FX_HALF));
}

// Surface area = 2 * (ex*ey + ey*ez + ez*ex). For meshes scaled outside
// roughly [-4, 4] this can wrap fx_t; documented as a limitation.
static inline fx_t fx_aabb_surface_area(const fx_aabb_t *a) {
  fx_vec3_t e = fx_vec3_sub(a->maxB, a->minB);
  fx_t xy = fxmul(e.x, e.y);
  fx_t yz = fxmul(e.y, e.z);
  fx_t zx = fxmul(e.z, e.x);
  fx_t sum = fxadd(fxadd(xy, yz), zx);
  return fxadd(sum, sum);
}

static inline void fx_aabb_from_triangle(fx_aabb_t *out,
                                         const fx_triangle_t *tri) {
  fx_aabb_init_empty(out);
  fx_aabb_expand_point(out, tri->v0);
  fx_aabb_expand_point(out, tri->v1);
  fx_aabb_expand_point(out, tri->v2);
}

#endif
