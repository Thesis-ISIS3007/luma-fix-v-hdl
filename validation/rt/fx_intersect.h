#ifndef FX_RT_INTERSECT_H
#define FX_RT_INTERSECT_H

#include "fx_ray.h"
#include "fx_triangle.h"

// Moller-Trumbore ray-triangle intersection ported to 16Q16 fixed point.
//
// IMPORTANT: the |a| < FX_EPS early-out is REQUIRED, not just an optimization.
// On divide-by-zero our FXDIV unit returns 0 (per ISA), so without the guard
// f would be 0 and u/v/t would all collapse to 0, falsely passing every range
// check and reporting a bogus hit at t=0.
//
// On hit, fills `hit` with t / world-space pos / unit normal (flipped to face
// the ray origin, matching the reference C++) and updates ray->t so callers
// can use this in a closest-hit traversal.
static inline int fx_intersect_triangle(fx_ray_t *ray,
                                        const fx_triangle_t *prim,
                                        fx_hit_info_t *hit) {
  fx_vec3_t edge1 = fx_vec3_sub(prim->v1, prim->v0);
  fx_vec3_t edge2 = fx_vec3_sub(prim->v2, prim->v0);
  fx_vec3_t h = fx_vec3_cross(ray->D, edge2);
  fx_t a = fx_vec3_dot(edge1, h);

  if (fxabs(a) < FX_EPS) {
    return 0;
  }

  fx_t f = fxdiv(FX_ONE, a);
  fx_vec3_t s = fx_vec3_sub(ray->O, prim->v0);
  fx_t u = fxmul(f, fx_vec3_dot(s, h));

  if (u < FX_ZERO || u > FX_ONE) {
    return 0;
  }

  fx_vec3_t q = fx_vec3_cross(s, edge1);
  fx_t v = fxmul(f, fx_vec3_dot(ray->D, q));

  if (v < FX_ZERO || fxadd(u, v) > FX_ONE) {
    return 0;
  }

  fx_t t = fxmul(f, fx_vec3_dot(edge2, q));

  if (t > FX_EPS && t < ray->t) {
    ray->t = t;
    hit->hit = 1;
    hit->t = t;
    hit->pos = fx_vec3_add(ray->O, fx_vec3_scale(ray->D, t));

    fx_vec3_t n = fx_triangle_normal(prim);
    if (fx_vec3_dot(n, ray->D) > FX_ZERO) {
      n = fx_vec3_neg(n);
    }
    hit->normal = n;
    return 1;
  }

  return 0;
}

#endif
