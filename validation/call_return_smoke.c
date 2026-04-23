#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

__attribute__((noinline)) static unsigned int func(void) { return 7u; }

int main(void) {
  unsigned int v = func();
  unsigned int out = (v == 7u) ? 1u : 0u;
  *TEST_OUT = out;
  return 0;
}
