#include "validation.h"
#include "rt/fx_intersect.h"

// Orthographic sweep of the same xy equilateral fixture as
// fx_rt_triangle_smoke.c, streaming ARGB pixels through the MMIO render log
// (0x40000000) in the same framing as fx_rt_cornell_smoke. Decode a captured
// log to PPM with scripts/fx_rt_log_to_ppm.py. The (sx, sy) frustum is a
// square centered on the triangle centroid (0,0) with half-extent 1.1; hits
// are solid red. No BVH.

#define FX_RENDER_LOG ((volatile unsigned int *)0x40000000u)
#define RT_RTRL_SENTINEL 0x5254524Cu

#ifndef RES_W
#define RES_W 32
#endif
#ifndef RES_H
#define RES_H 24
#endif

#define BG_PIXEL 0xFF101010u
// Opaque red (ARGB).
#define HIT_PIXEL 0xFFFF0000u

static inline unsigned int encode_hit(void) { return HIT_PIXEL; }

int main(void) {
  const fx_t rt3_2 = fx_from_double(0.8660254037844387); /* sqrt(3)/2 */
  const fx_t h = fx_from_double(0.5);

  fx_triangle_t tri;
  tri.v0 = fx_vec3_make(FX_ZERO, fxneg(FX_ONE), FX_ZERO);
  tri.v1 = fx_vec3_make(rt3_2, h, FX_ZERO);
  tri.v2 = fx_vec3_make(fxneg(rt3_2), h, FX_ZERO);

  *FX_RENDER_LOG = ((unsigned int)RES_W << 16) | (unsigned int)RES_H;
  *FX_RENDER_LOG = RT_RTRL_SENTINEL;

  // Centroid at (0,0); 1.1 half-extent frames the unit-circle equilateral.
  const fx_t tri_cx = FX_ZERO;
  const fx_t tri_cy = FX_ZERO;
  const fx_t half = fx_from_double(1.1);
  const fx_t w2 = fxadd(half, half);

  const fx_t camZ = FX_ONE;
  const fx_t farT = fx_from_double(1000.0);
  const fx_vec3_t rayDir = fx_vec3_make(FX_ZERO, FX_ZERO, fxneg(FX_ONE));
  const fx_t invW1 = fxdiv(FX_ONE, int2fx(RES_W - 1));
  const fx_t invH1 = fxdiv(FX_ONE, int2fx(RES_H - 1));

  for (int py = 0; py < RES_H; py++) {
    fx_t v = fxmul(int2fx(py), invH1);
    // top row (py=0) -> high sy (north of centroid in image space)
    fx_t sy = fxsub(fxadd(tri_cy, half), fxmul(w2, v));
    for (int px = 0; px < RES_W; px++) {
      fx_t u = fxmul(int2fx(px), invW1);
      fx_t sx = fxadd(fxsub(tri_cx, half), fxmul(w2, u));

      fx_ray_t ray;
      ray.O = fx_vec3_make(sx, sy, camZ);
      ray.D = rayDir;
      ray.t = farT;

      fx_hit_info_t hit;
      hit.hit = 0;
      hit.t = FX_ZERO;
      hit.pos = fx_vec3_zero();
      hit.normal = fx_vec3_zero();
      hit.primIdx = 0u;

      unsigned int pixel = BG_PIXEL;
      if (fx_intersect_triangle(&ray, &tri, &hit)) {
        pixel = encode_hit();
      }
      *FX_RENDER_LOG = pixel;
    }
  }
  return 0;
}
