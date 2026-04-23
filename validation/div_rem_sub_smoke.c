#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int dividend = 20u;
  unsigned int divisor = 6u;
  unsigned int q = 0u;

  while (dividend >= divisor) {
    dividend -= divisor;
    q++;
  }

  *TEST_OUT = q * 100u + dividend;
  return 0;
}
