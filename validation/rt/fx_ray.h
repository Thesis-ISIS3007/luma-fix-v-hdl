#ifndef FX_RT_RAY_H
#define FX_RT_RAY_H

#include "fx_vec3.h"

// Ray = (origin O, direction D, current best parametric distance t). The
// reference C++ tracer mutates `t` during traversal so we keep the same
// convention (intersect functions take a pointer).
typedef struct {
  fx_vec3_t O;
  fx_vec3_t D;
  fx_t t;
} fx_ray_t;

// Result of a primitive-vs-ray test. `hit` is 0/1 instead of bool to avoid
// pulling stdbool into the freestanding build.
typedef struct {
  int hit;
  fx_t t;
  fx_vec3_t normal;
  fx_vec3_t pos;
} fx_hit_info_t;

#endif
