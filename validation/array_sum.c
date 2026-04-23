#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int a[4] = {1u, 2u, 3u, 4u};
  unsigned int sum = 0u;
  for (unsigned int i = 0u; i < 4u; ++i) {
    sum += a[i];
  }
  *TEST_OUT = sum;
  return 0;
}
