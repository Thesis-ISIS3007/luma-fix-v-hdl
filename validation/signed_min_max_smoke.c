#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  int x1 = 5;
  int x2 = 9;
  int x3 = -3;
  int x4 = 2;

  int p1 = (x1 < x2) ? x2 : x1;
  int p2 = (x3 < x4) ? x4 : x3;

  *TEST_OUT = (unsigned int)(p1 * 100 + p2);
  return 0;
}
