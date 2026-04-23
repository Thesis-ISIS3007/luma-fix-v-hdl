#include "validation.h"
#include "rt/fx_bvh.h"
#include "rt/scene_cornell.h"

// Renders the baked Cornell Box scene with an orthographic camera and
// flat per-material shading. Pixels are streamed out one at a time
// through the harness MMIO render-log address (see CoreMemoryHarness for
// the receiving side); nothing is ever stored in dmem, so the only
// memory cost in the freestanding build is the BVH arena and the scene
// constants.
//
// Resolution is a compile-time knob: 32x24 keeps the smoke under a few
// minutes of ChiselSim wall time, which is what the regression actually
// runs. Bumping to 640x480 ("the image with 480p resolution" goal) is a
// one-line change but takes hours of sim - intended as a manual run.

#define FX_RENDER_LOG ((volatile unsigned int *)0x40000000u)
#define RT_RTRL_SENTINEL 0x5254524Cu

#ifndef RES_W
#define RES_W 32
#endif
#ifndef RES_H
#define RES_H 24
#endif

#define BG_PIXEL 0xFF101010u

static fx_bvh_t g_bvh;

static inline unsigned int fx_to_u8(fx_t c) {
  if (c <= FX_ZERO) {
    return 0u;
  }
  if (c >= FX_ONE) {
    return 255u;
  }
  // fx in [0, FX_ONE) -> [0, 256). Top byte after shifting is the
  // 8-bit color quantum without needing a 32x32 multiply.
  return (unsigned int)((c >> 8) & 0xFFu);
}

static inline unsigned int encode_pixel(fx_vec3_t color) {
  return 0xFF000000u
       | (fx_to_u8(color.x) << 16)
       | (fx_to_u8(color.y) <<  8)
       |  fx_to_u8(color.z);
}

int main(void) {
  fx_bvh_build(&g_bvh, g_prims, SCENE_NUM_PRIMS);

  *FX_RENDER_LOG = ((unsigned int)RES_W << 16) | (unsigned int)RES_H;
  *FX_RENDER_LOG = RT_RTRL_SENTINEL;

  // Baked Cornell extents (after node 0 transform):
  //   X in [-1.02, 1.00]   width  ~2.02
  //   Y in [ 0.00, 1.99]   height ~1.99
  //   Z in [-1.04, 0.99]   open face at +Z
  // Camera sits just outside the open face, looking down -Z.
  const fx_t xMin = fx_from_double(-1.02);
  const fx_t xMax = fx_from_double(1.00);
  const fx_t yMin = fx_from_double(0.00);
  const fx_t yMax = fx_from_double(1.99);
  const fx_t camZ = fx_from_double(1.50);
  const fx_t farT = fx_from_double(1000.0);
  // Oblique ortho direction: rays go down-right while heading into the
  // box (-Z). A pure (0, 0, -1) sweep would only see surfaces with a +Z
  // facing normal (back wall + box +Z faces, all same neutral color in
  // this scene). Tilting +X/-Y exposes the colored right wall and the
  // floor too. Direction is left unnormalized; the intersection code
  // doesn't care about |D| as long as it's consistent.
  const fx_vec3_t rayDir = fx_vec3_make(
      fx_from_double(0.30), fx_from_double(-0.50), fxneg(FX_ONE));
  const fx_t invW1 = fxdiv(FX_ONE, int2fx(RES_W - 1));
  const fx_t invH1 = fxdiv(FX_ONE, int2fx(RES_H - 1));
  const fx_t xExt = fxsub(xMax, xMin);
  const fx_t yExt = fxsub(yMax, yMin);

  for (int py = 0; py < RES_H; py++) {
    fx_t v = fxmul(int2fx(py), invH1);
    fx_t sy = fxsub(yMax, fxmul(yExt, v));
    for (int px = 0; px < RES_W; px++) {
      fx_t u = fxmul(int2fx(px), invW1);
      fx_t sx = fxadd(xMin, fxmul(xExt, u));

      fx_ray_t ray;
      ray.O = fx_vec3_make(sx, sy, camZ);
      ray.D = rayDir;
      ray.t = farT;

      fx_hit_info_t hit;
      hit.hit = 0;
      hit.t = FX_ZERO;
      hit.normal = fx_vec3_zero();
      hit.pos = fx_vec3_zero();
      hit.primIdx = 0u;

      unsigned int pixel;
      if (fx_bvh_traverse(&g_bvh, &ray, &hit)) {
        unsigned int mat = (unsigned int)g_matIdx[hit.primIdx];
        pixel = encode_pixel(g_matColor[mat]);
      } else {
        pixel = BG_PIXEL;
      }
      *FX_RENDER_LOG = pixel;
    }
  }
  return 0;
}
