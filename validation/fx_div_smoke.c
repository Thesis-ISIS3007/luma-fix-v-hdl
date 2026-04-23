#include "validation.h"
#include "fx16q16.h"

// Exercises FXDIV across positive, negative, zero-divisor, and back-to-back
// scenarios to make sure the multi-cycle divider in EX correctly stalls the
// pipeline and produces the right quotient each time.
//
//   p1 = 10.0 / 2.5    = 4.0
//   p2 = 2.5  / 0.25   = 10.0
//   p3 = 10.0 / 0.25   = 40.0
//   n1 = -10.0 / 2.5   = -4.0
//   n2 = 10.0  / -2.5  = -4.0
//   n3 = -10.0 / -2.5  =  4.0
//   z  = 1.0 / 0.0     = 0 (no trap)
//   sum = p1 + p2 + p3 + n1 + n2 + n3 + z
//       = 4 + 10 + 40 - 4 - 4 + 4 + 0 = 50
// We also store p2 separately so the test can pull it as a sanity check.

static volatile unsigned int *const TEST_OUT_SUM = (volatile unsigned int *)0x80u;
static volatile unsigned int *const TEST_OUT_P2  = (volatile unsigned int *)0x84u;

int main(void) {
  fx_t a = fx_from_double(10.0);
  fx_t b = fx_from_double(2.5);
  fx_t c = fx_from_double(0.25);
  fx_t na = fx_from_double(-10.0);
  fx_t nb = fx_from_double(-2.5);
  fx_t one = fx_from_double(1.0);
  fx_t zero = 0;

  fx_t p1 = fxdiv(a, b);
  fx_t p2 = fxdiv(b, c);
  fx_t p3 = fxdiv(a, c);
  fx_t n1 = fxdiv(na, b);
  fx_t n2 = fxdiv(a, nb);
  fx_t n3 = fxdiv(na, nb);
  fx_t z  = fxdiv(one, zero);

  fx_t sum = fxadd(p1, p2);
  sum = fxadd(sum, p3);
  sum = fxadd(sum, n1);
  sum = fxadd(sum, n2);
  sum = fxadd(sum, n3);
  sum = fxadd(sum, z);

  *TEST_OUT_SUM = (unsigned int)sum;
  *TEST_OUT_P2  = (unsigned int)p2;
  return 0;
}
