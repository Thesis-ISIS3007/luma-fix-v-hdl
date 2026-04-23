#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x84u;

__attribute__((noinline)) static unsigned int gcd_sub(unsigned int a, unsigned int b) {
  while (a != b) {
    if (a > b) {
      a = a - b;
    } else {
      b = b - a;
    }
  }
  return a;
}

int main(void) {
  unsigned int a = 48u;
  unsigned int b = 18u;
  unsigned int g = gcd_sub(a, b);
  *TEST_OUT = g;
  return 0;
}
