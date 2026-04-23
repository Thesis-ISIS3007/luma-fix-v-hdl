#include "validation.h"
#include "rt/fx_bvh.h"

// Builds a BVH over 4 triangles (two xy quads at z=0 and z=-1) and traverses
// it with a ray aimed at the front quad. With FX_BVH_MAX_LEAF_SZ=2 the
// builder must split at least once, so numNodes > 1 is also a useful sanity
// signal that the SAH path executed.
//
// Expected:
//   hit      = 1
//   t        = 1.0                (0x00010000)
//   pos      = (0.25, 0.25, 0)
//   normal   = (0, 0, 1)          (dot(n, D) = -1 < 0, no flip)
//   numNodes > 1

#define TEST_OUT_NUM_NODES ((volatile unsigned int *)0x80u)
#define TEST_OUT_HIT       ((volatile unsigned int *)0x84u)
#define TEST_OUT_T         ((volatile unsigned int *)0x88u)
#define TEST_OUT_POS_X     ((volatile unsigned int *)0x8Cu)
#define TEST_OUT_POS_Y     ((volatile unsigned int *)0x90u)
#define TEST_OUT_POS_Z     ((volatile unsigned int *)0x94u)
#define TEST_OUT_NRM_X     ((volatile unsigned int *)0x98u)
#define TEST_OUT_NRM_Y     ((volatile unsigned int *)0x9Cu)
#define TEST_OUT_NRM_Z     ((volatile unsigned int *)0xA0u)

// The BVH is static so its ~3 KiB of scratch lives in .bss, keeping the
// freestanding stack usage tiny.
static fx_bvh_t g_bvh;

static fx_triangle_t make_tri(fx_vec3_t a, fx_vec3_t b, fx_vec3_t c) {
  fx_triangle_t t;
  t.v0 = a;
  t.v1 = b;
  t.v2 = c;
  return t;
}

int main(void) {
  fx_t zero = FX_ZERO;
  fx_t one = FX_ONE;
  fx_t negOne = fxneg(FX_ONE);

  fx_vec3_t f00 = fx_vec3_make(zero, zero, zero);
  fx_vec3_t f10 = fx_vec3_make(one, zero, zero);
  fx_vec3_t f01 = fx_vec3_make(zero, one, zero);
  fx_vec3_t f11 = fx_vec3_make(one, one, zero);

  fx_vec3_t b00 = fx_vec3_make(zero, zero, negOne);
  fx_vec3_t b10 = fx_vec3_make(one, zero, negOne);
  fx_vec3_t b01 = fx_vec3_make(zero, one, negOne);
  fx_vec3_t b11 = fx_vec3_make(one, one, negOne);

  fx_triangle_t tris[4];
  tris[0] = make_tri(f00, f10, f01);
  tris[1] = make_tri(f10, f11, f01);
  tris[2] = make_tri(b00, b10, b01);
  tris[3] = make_tri(b10, b11, b01);

  fx_bvh_build(&g_bvh, tris, 4);

  fx_ray_t ray;
  ray.O = fx_vec3_make(fx_from_double(0.25), fx_from_double(0.25), FX_ONE);
  ray.D = fx_vec3_make(FX_ZERO, FX_ZERO, negOne);
  ray.t = fx_from_double(1000.0);

  fx_hit_info_t hit;
  hit.hit = 0;
  hit.t = 0;
  hit.pos = fx_vec3_zero();
  hit.normal = fx_vec3_zero();

  int ok = fx_bvh_traverse(&g_bvh, &ray, &hit);

  *TEST_OUT_NUM_NODES = (unsigned int)g_bvh.numNodes;
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
