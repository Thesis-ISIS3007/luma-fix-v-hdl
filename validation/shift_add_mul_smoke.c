#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int multiplicand = 3u;
  unsigned int multiplier = 6u;
  unsigned int acc = 0u;

  while (multiplier != 0u) {
    if (multiplier & 1u) {
      acc += multiplicand;
    }
    multiplicand <<= 1;
    multiplier >>= 1;
  }

  *TEST_OUT = acc;
  return 0;
}
