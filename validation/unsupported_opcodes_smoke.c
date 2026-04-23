#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned int x = 5u;

  // Inject unsupported instruction
  asm volatile(".word 0x00000000" ::: "memory");

  x = x + 3u;
  *TEST_OUT = x;
  return 0;
}
