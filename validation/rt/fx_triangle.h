#ifndef FX_RT_TRIANGLE_H
#define FX_RT_TRIANGLE_H

#include "fx_vec3.h"

typedef struct {
  fx_vec3_t v0;
  fx_vec3_t v1;
  fx_vec3_t v2;
} fx_triangle_t;

// Geometric (unit) normal of the triangle. Mirrors the reference C++:
//   normalize(cross(v1 - v0, v2 - v0))
// Issues one fx_sqrt + one FXDIV through fx_vec3_normalize. Degenerate
// (collinear) triangles produce a zero vector instead of NaN.
static inline fx_vec3_t fx_triangle_normal(const fx_triangle_t *tri) {
  fx_vec3_t edge1 = fx_vec3_sub(tri->v1, tri->v0);
  fx_vec3_t edge2 = fx_vec3_sub(tri->v2, tri->v0);
  return fx_vec3_normalize(fx_vec3_cross(edge1, edge2));
}

#endif
