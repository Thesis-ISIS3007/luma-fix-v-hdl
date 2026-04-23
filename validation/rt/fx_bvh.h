#ifndef FX_RT_BVH_H
#define FX_RT_BVH_H

#include <stdint.h>

#include "fx_aabb.h"
#include "fx_intersect.h"
#include "fx_ray.h"
#include "fx_triangle.h"

// Device-side BVH port. Everything is statically sized and lives inside a
// single fx_bvh_t so the freestanding build stays heap-free. The scratch
// arenas are part of the struct deliberately so a caller can snapshot /
// reuse a single allocation across multiple build+traverse rounds.

// Cornell Box clocks in at 32 prims; cap at 64 for headroom. Worst-case
// node count is `2 * MAX_PRIMS - 1`, hence 128. Stack depth follows
// log2(MAX_PRIMS / MAX_LEAF_SZ), which is comfortably under 32.
#define FX_BVH_MAX_PRIMS 64
#define FX_BVH_MAX_NODES 128
#define FX_BVH_N_BUCKETS 12
#define FX_BVH_MAX_LEAF_SZ 2
#define FX_BVH_MAX_STACK 32

typedef struct {
  fx_t bounds[6];
  uint32_t firstChildOrPrim;
  uint32_t primCount;
} fx_bvh_node_t;

typedef struct {
  uint32_t primIdx;
  fx_vec3_t centroid;
  fx_aabb_t aabb;
} fx_bvh_build_data_t;

typedef struct {
  uint32_t count;
  fx_aabb_t bounds;
} fx_bvh_bucket_t;

typedef struct {
  fx_triangle_t prims[FX_BVH_MAX_PRIMS];
  uint32_t numPrims;
  fx_bvh_node_t nodes[FX_BVH_MAX_NODES];
  uint32_t numNodes;

  fx_bvh_build_data_t bData[FX_BVH_MAX_PRIMS];
  fx_aabb_t leftBoundsPrefix[FX_BVH_N_BUCKETS - 1];
  uint32_t leftCountPrefix[FX_BVH_N_BUCKETS - 1];
  fx_aabb_t rightBoundsSuffix[FX_BVH_N_BUCKETS - 1];
  uint32_t rightCountSuffix[FX_BVH_N_BUCKETS - 1];
  // Buckets live in the struct (not a recursive stack frame) so deep
  // recursion doesn't blow the freestanding stack. Safe to share because
  // they're consumed before the recursive calls fire.
  fx_bvh_bucket_t buckets[FX_BVH_N_BUCKETS];
} fx_bvh_t;

static inline void fx_bvh_node_set_bounds(fx_bvh_node_t *n,
                                          const fx_aabb_t *a) {
  n->bounds[0] = a->minB.x;
  n->bounds[1] = a->maxB.x;
  n->bounds[2] = a->minB.y;
  n->bounds[3] = a->maxB.y;
  n->bounds[4] = a->minB.z;
  n->bounds[5] = a->maxB.z;
}

// Per-axis slab test, faithful to the reference intersectAABB. Burns one
// FXDIV per axis on a finite rayDir; the parallel-to-slab case skips the
// division entirely, matching the reference's |rayDir| < eps branch.
static inline int fx_intersect_aabb_node(const fx_ray_t *ray,
                                         const fx_bvh_node_t *node,
                                         fx_t *tNear, fx_t *tFar) {
  *tNear = FX_ZERO;
  *tFar = ray->t;

  for (int i = 0; i < 3; i++) {
    fx_t rayDir;
    fx_t rayO;
    if (i == 0) {
      rayDir = ray->D.x;
      rayO = ray->O.x;
    } else if (i == 1) {
      rayDir = ray->D.y;
      rayO = ray->O.y;
    } else {
      rayDir = ray->D.z;
      rayO = ray->O.z;
    }

    fx_t boxMin = node->bounds[i * 2];
    fx_t boxMax = node->bounds[i * 2 + 1];

    if (fxabs(rayDir) < FX_EPS) {
      if (rayO < boxMin || rayO > boxMax) {
        return 0;
      }
    } else {
      fx_t invDir = fxdiv(FX_ONE, rayDir);
      fx_t t1 = fxmul(fxsub(boxMin, rayO), invDir);
      fx_t t2 = fxmul(fxsub(boxMax, rayO), invDir);
      if (t1 > t2) {
        fx_t tmp = t1;
        t1 = t2;
        t2 = tmp;
      }
      if (t1 > *tNear) {
        *tNear = t1;
      }
      if (t2 < *tFar) {
        *tFar = t2;
      }
      if (*tNear > *tFar) {
        return 0;
      }
    }
  }

  return (*tNear <= *tFar) && (*tFar > FX_ZERO);
}

// Translates a centroid coordinate into a bucket index 0..N_BUCKETS-1. The
// fxmul keeps the computation inside 16Q16; the final integer scale uses a
// 32-bit arithmetic shift (N_BUCKETS * f fits in int32 for f in [0, FX_ONE]).
static inline uint32_t fx_bvh_bucket_index(fx_t c, fx_t minC, fx_t invRange) {
  fx_t f = fxmul(fxsub(c, minC), invRange);
  // N_BUCKETS = 12 = 8 + 4, so the multiply collapses into two shifts and
  // an add, no __mulsi3 needed.
  int32_t scaled = ((int32_t)f << 3) + ((int32_t)f << 2);
  int32_t b = scaled >> FX_FRAC_BITS;
  if (b < 0) {
    b = 0;
  } else if (b >= (int32_t)FX_BVH_N_BUCKETS) {
    b = (int32_t)FX_BVH_N_BUCKETS - 1;
  }
  return (uint32_t)b;
}

static inline void fx_bvh_build_into_node(fx_bvh_t *bvh, uint32_t start,
                                          uint32_t end, uint32_t nodeIdx) {
  fx_bvh_node_t *node = &bvh->nodes[nodeIdx];

  fx_aabb_t nodeAABB;
  fx_aabb_init_empty(&nodeAABB);
  for (uint32_t i = start; i < end; i++) {
    fx_aabb_expand_aabb(&nodeAABB, &bvh->bData[i].aabb);
  }
  fx_bvh_node_set_bounds(node, &nodeAABB);

  uint32_t primCount = end - start;
  if (primCount <= FX_BVH_MAX_LEAF_SZ) {
    node->firstChildOrPrim = start;
    node->primCount = primCount;
    return;
  }

  fx_aabb_t aabbCentroid;
  fx_aabb_init_empty(&aabbCentroid);
  for (uint32_t i = start; i < end; i++) {
    fx_aabb_expand_point(&aabbCentroid, bvh->bData[i].centroid);
  }

  fx_vec3_t centroidExtent =
      fx_vec3_sub(aabbCentroid.maxB, aabbCentroid.minB);
  uint32_t splitAxis = 0;
  if (centroidExtent.y > centroidExtent.x) {
    splitAxis = 1;
  }
  fx_t cmp = (splitAxis == 0) ? centroidExtent.x : centroidExtent.y;
  if (centroidExtent.z > cmp) {
    splitAxis = 2;
  }

  fx_t minC = fx_vec3_comp(aabbCentroid.minB, splitAxis);
  fx_t maxC = fx_vec3_comp(aabbCentroid.maxB, splitAxis);

  // `range` is clamped to FX_AABB_EPS so the fxdiv below never hits
  // divide-by-zero for coplanar centroids along the split axis.
  fx_t range = fxsub(maxC, minC);
  if (range < FX_AABB_EPS) {
    range = FX_AABB_EPS;
  }
  fx_t invRange = fxdiv(FX_ONE, range);

  for (uint32_t i = 0; i < FX_BVH_N_BUCKETS; i++) {
    bvh->buckets[i].count = 0;
    fx_aabb_init_empty(&bvh->buckets[i].bounds);
  }

  for (uint32_t i = start; i < end; i++) {
    fx_t c = fx_vec3_comp(bvh->bData[i].centroid, splitAxis);
    uint32_t b = fx_bvh_bucket_index(c, minC, invRange);
    bvh->buckets[b].count++;
    fx_aabb_expand_aabb(&bvh->buckets[b].bounds, &bvh->bData[i].aabb);
  }

  fx_aabb_t accL;
  fx_aabb_init_empty(&accL);
  uint32_t accCountL = 0;
  for (uint32_t i = 0; i < FX_BVH_N_BUCKETS - 1; i++) {
    if (bvh->buckets[i].count > 0) {
      fx_aabb_expand_aabb(&accL, &bvh->buckets[i].bounds);
    }
    accCountL += bvh->buckets[i].count;
    bvh->leftBoundsPrefix[i] = accL;
    bvh->leftCountPrefix[i] = accCountL;
  }

  fx_aabb_t accR;
  fx_aabb_init_empty(&accR);
  uint32_t accCountR = 0;
  for (int32_t i = (int32_t)FX_BVH_N_BUCKETS - 1; i > 0; --i) {
    if (bvh->buckets[i].count > 0) {
      fx_aabb_expand_aabb(&accR, &bvh->buckets[i].bounds);
    }
    accCountR += bvh->buckets[i].count;
    bvh->rightBoundsSuffix[i - 1] = accR;
    bvh->rightCountSuffix[i - 1] = accCountR;
  }

  // int32 accumulation is fine for our scaffold bounds: with unit-scale
  // meshes, leftArea stays below ~6.0 (=0x60000 in 16Q16) and primitive
  // counts are capped at FX_BVH_MAX_PRIMS=16, so the worst-case product
  // fits comfortably in int32. Avoiding int64_t keeps us from pulling
  // libgcc's __muldi3 into the freestanding link.
  int32_t bestCost = INT32_MAX;
  uint32_t bestSplit = 0;
  int haveValidSplit = 0;

  for (uint32_t i = 0; i < FX_BVH_N_BUCKETS - 1; i++) {
    uint32_t ln = bvh->leftCountPrefix[i];
    uint32_t rn = bvh->rightCountSuffix[i];
    if (ln == 0 || rn == 0) {
      continue;
    }
    fx_t leftArea = fx_aabb_surface_area(&bvh->leftBoundsPrefix[i]);
    fx_t rightArea = fx_aabb_surface_area(&bvh->rightBoundsSuffix[i]);
    int32_t cost = fx_imul_i32((int32_t)leftArea, (int32_t)ln) +
                   fx_imul_i32((int32_t)rightArea, (int32_t)rn);
    if (cost < bestCost) {
      bestCost = cost;
      bestSplit = i;
      haveValidSplit = 1;
    }
  }

  uint32_t mid;
  if (!haveValidSplit) {
    mid = start + primCount / 2;
  } else {
    // In-place two-pointer partition: [start, left) <= bestSplit,
    // [right, end) > bestSplit.
    uint32_t left = start;
    uint32_t right = end;
    while (left < right) {
      fx_t c = fx_vec3_comp(bvh->bData[left].centroid, splitAxis);
      uint32_t b = fx_bvh_bucket_index(c, minC, invRange);
      if (b <= bestSplit) {
        left++;
      } else {
        right--;
        fx_bvh_build_data_t tmp = bvh->bData[left];
        bvh->bData[left] = bvh->bData[right];
        bvh->bData[right] = tmp;
      }
    }
    mid = left;

    if (mid == start || mid == end) {
      mid = start + primCount / 2;
    }
  }

  uint32_t leftIdx = bvh->numNodes++;
  uint32_t rightIdx = bvh->numNodes++;

  fx_bvh_build_into_node(bvh, start, mid, leftIdx);
  fx_bvh_build_into_node(bvh, mid, end, rightIdx);

  // Re-fetch the parent pointer: recursive calls may have appended nodes,
  // potentially invalidating the previous local `node` reference depending
  // on how the compiler reloads it. Using the index is the safe form.
  bvh->nodes[nodeIdx].firstChildOrPrim = leftIdx;
  bvh->nodes[nodeIdx].primCount = 0;
}

static inline void fx_bvh_build(fx_bvh_t *bvh, const fx_triangle_t *prims,
                                uint32_t count) {
  if (count > FX_BVH_MAX_PRIMS) {
    count = FX_BVH_MAX_PRIMS;
  }

  bvh->numPrims = count;
  bvh->numNodes = 0;

  for (uint32_t i = 0; i < count; i++) {
    bvh->bData[i].primIdx = i;
    fx_aabb_from_triangle(&bvh->bData[i].aabb, &prims[i]);
    bvh->bData[i].centroid = fx_aabb_center(&bvh->bData[i].aabb);
  }

  if (count == 0) {
    return;
  }

  uint32_t rootIdx = bvh->numNodes++;
  fx_bvh_build_into_node(bvh, 0, count, rootIdx);

  // Re-order the primitive array to match the post-partition bData ordering
  // so leaf nodes can index Prim with a contiguous run.
  for (uint32_t i = 0; i < count; i++) {
    bvh->prims[i] = prims[bvh->bData[i].primIdx];
  }
}

static inline int fx_bvh_traverse(const fx_bvh_t *bvh, fx_ray_t *ray,
                                  fx_hit_info_t *hit) {
  hit->hit = 0;
  if (bvh->numNodes == 0) {
    return 0;
  }

  uint32_t stack[FX_BVH_MAX_STACK];
  int32_t sp = 0;
  stack[sp++] = 0;

  int anyHit = 0;

  while (sp > 0) {
    uint32_t nodeIdx = stack[--sp];
    const fx_bvh_node_t *node = &bvh->nodes[nodeIdx];

    fx_t tNear;
    fx_t tFar;
    if (!fx_intersect_aabb_node(ray, node, &tNear, &tFar)) {
      continue;
    }

    if (node->primCount != 0) {
      for (uint32_t i = 0; i < node->primCount; i++) {
        fx_hit_info_t tmp;
        tmp.hit = 0;
        tmp.t = 0;
        tmp.pos = fx_vec3_zero();
        tmp.normal = fx_vec3_zero();
        if (fx_intersect_triangle(
                ray, &bvh->prims[node->firstChildOrPrim + i], &tmp)) {
          if (!anyHit || tmp.t < hit->t) {
            anyHit = 1;
            *hit = tmp;
            // bvh->prims[] was reshuffled by the partition step but the
            // matching bvh->bData[] entry still carries the *original*
            // primitive index, which is what the caller's per-prim
            // attribute tables (matIdx, etc.) are keyed on.
            hit->primIdx = bvh->bData[node->firstChildOrPrim + i].primIdx;
          }
        }
      }
    } else {
      if (sp + 2 <= FX_BVH_MAX_STACK) {
        stack[sp++] = node->firstChildOrPrim;
        stack[sp++] = node->firstChildOrPrim + 1;
      }
    }
  }

  return anyHit;
}

#endif
