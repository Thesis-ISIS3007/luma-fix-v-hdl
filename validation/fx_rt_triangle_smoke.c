#include "validation.h"
#include "rt/fx_intersect.h"

// Drives one closed-form ray-vs-triangle intersection and dumps every field of
// the resulting hit info into dmem so the Chisel test can spot-check each
// word.
//
// Geometry:
//   triangle: v0=(0,0,0)  v1=(1,0,0)  v2=(0,1,0)   (xy unit right triangle)
//   ray:      O=(0.25, 0.25, 1)  D=(0, 0, -1)  t=1000.0
//
// Hand-checked Moller-Trumbore output:
//   hit       = 1
//   t         = 1.0      (0x00010000)
//   pos       = (0.25, 0.25, 0)
//   normal    = (0, 0, 1)        (dot(n, D) = -1 < 0, no flip)

#define TEST_OUT_HIT      ((volatile unsigned int *)0x80u)
#define TEST_OUT_T        ((volatile unsigned int *)0x84u)
#define TEST_OUT_POS_X    ((volatile unsigned int *)0x88u)
#define TEST_OUT_POS_Y    ((volatile unsigned int *)0x8Cu)
#define TEST_OUT_POS_Z    ((volatile unsigned int *)0x90u)
#define TEST_OUT_NRM_X    ((volatile unsigned int *)0x94u)
#define TEST_OUT_NRM_Y    ((volatile unsigned int *)0x98u)
#define TEST_OUT_NRM_Z    ((volatile unsigned int *)0x9Cu)

int main(void) {
  fx_triangle_t tri;
  tri.v0 = fx_vec3_make(FX_ZERO, FX_ZERO, FX_ZERO);
  tri.v1 = fx_vec3_make(FX_ONE, FX_ZERO, FX_ZERO);
  tri.v2 = fx_vec3_make(FX_ZERO, FX_ONE, FX_ZERO);

  fx_ray_t ray;
  ray.O = fx_vec3_make(fx_from_double(0.25), fx_from_double(0.25), FX_ONE);
  ray.D = fx_vec3_make(FX_ZERO, FX_ZERO, fxneg(FX_ONE));
  ray.t = fx_from_double(1000.0);

  fx_hit_info_t hit;
  hit.hit = 0;
  hit.t = 0;
  hit.pos = fx_vec3_zero();
  hit.normal = fx_vec3_zero();
  hit.primIdx = 0u;

  int ok = fx_intersect_triangle(&ray, &tri, &hit);

  *TEST_OUT_HIT = (unsigned int)ok;
  *TEST_OUT_T = (unsigned int)hit.t;
  *TEST_OUT_POS_X = (unsigned int)hit.pos.x;
  *TEST_OUT_POS_Y = (unsigned int)hit.pos.y;
  *TEST_OUT_POS_Z = (unsigned int)hit.pos.z;
  *TEST_OUT_NRM_X = (unsigned int)hit.normal.x;
  *TEST_OUT_NRM_Y = (unsigned int)hit.normal.y;
  *TEST_OUT_NRM_Z = (unsigned int)hit.normal.z;
  return 0;
}
