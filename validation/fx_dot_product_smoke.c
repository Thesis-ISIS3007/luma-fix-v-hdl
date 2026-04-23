#include "validation.h"
#include "fx16q16.h"

// Computes a 4-element 16Q16 dot product inside a counted loop, exercising
// FXMUL/FXADD interaction with normal RV32I control flow (branches, loads,
// pointer arithmetic) and the FX sequencer's hold-fetch behaviour.
//
//   xs = [1.0, 2.0, 3.0, 4.0]
//   ys = [0.5, 1.5, 0.25, 2.0]
//   result = 1.0*0.5 + 2.0*1.5 + 3.0*0.25 + 4.0*2.0
//          = 0.5 + 3.0 + 0.75 + 8.0
//          = 12.25
// The result is also converted to an int (rounded toward -inf via FX2INT) and
// stored separately so the test can probe the integer projection.

#define N 4

static volatile unsigned int *const TEST_OUT_FX  = (volatile unsigned int *)0x80u;
static volatile unsigned int *const TEST_OUT_INT = (volatile unsigned int *)0x84u;

__attribute__((noinline)) static fx_t dot(const fx_t *xs, const fx_t *ys, int n) {
  fx_t acc = 0;
  for (int i = 0; i < n; ++i) {
    acc = fxadd(acc, fxmul(xs[i], ys[i]));
  }
  return acc;
}

int main(void) {
  fx_t xs[N];
  fx_t ys[N];
  xs[0] = fx_from_double(1.0);
  xs[1] = fx_from_double(2.0);
  xs[2] = fx_from_double(3.0);
  xs[3] = fx_from_double(4.0);
  ys[0] = fx_from_double(0.5);
  ys[1] = fx_from_double(1.5);
  ys[2] = fx_from_double(0.25);
  ys[3] = fx_from_double(2.0);

  fx_t r = dot(xs, ys, N);
  int ri = fx2int(r);

  *TEST_OUT_FX  = (unsigned int)r;
  *TEST_OUT_INT = (unsigned int)ri;
  return 0;
}
