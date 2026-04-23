#include "validation.h"
#include "fx16q16.h"

// Exercises FXADD, FXSUB, FXMUL, FXNEG, INT2FX, FX2INT, FXABS together. The
// computation is:
//   a = 1.5,  b = 2.25,  c = -3.0
//   d = a + b           = 3.75
//   e = a * b           = 3.375
//   f = -c              = 3.0
//   g = |c|             = 3.0
//   h = (d - e) + f + g = (3.75 - 3.375) + 3.0 + 3.0 = 6.375
//   t = h * fx(2)       = 12.75
//   k = int(t) + int(t) = 12 + 12 = 24
// We store both the raw fixed-point sum (h, fx encoding) and the integer
// projection (k) to two distinct dmem addresses so the tests can pick.

static volatile unsigned int *const TEST_OUT_FX = (volatile unsigned int *)0x80u;
static volatile unsigned int *const TEST_OUT_INT = (volatile unsigned int *)0x84u;

int main(void) {
  fx_t a = fx_from_double(1.5);
  fx_t b = fx_from_double(2.25);
  fx_t c = fx_from_double(-3.0);

  fx_t d = fxadd(a, b);
  fx_t e = fxmul(a, b);
  fx_t f = fxneg(c);
  fx_t g = fxabs(c);

  fx_t h = fxadd(fxadd(fxsub(d, e), f), g);
  fx_t t = fxmul(h, int2fx(2));
  int k = fx2int(t) + fx2int(t);

  *TEST_OUT_FX = (unsigned int)h;
  *TEST_OUT_INT = (unsigned int)k;
  return 0;
}
