#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int src[2] = {11u, 22u};
  unsigned int dst[2] = {0u, 0u};

  memcpy(dst, src, 2u * sizeof(unsigned int));

  *TEST_OUT = (dst[0] * 100u) + dst[1];
  return 0;
}
