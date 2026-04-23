#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int iterations = 0u;
  for (unsigned int i = 0u; i < 10u; ++i) {
    iterations++;
    if (iterations == 3u) {
      break;
    }
  }

  *TEST_OUT = iterations;
  return 0;
}
