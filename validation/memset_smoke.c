#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  unsigned char buf[4] = {0u, 0u, 0u, 0u};
  memset(buf, 0x7au, 4u);

  unsigned int out = ((unsigned int)buf[0]) | ((unsigned int)buf[1] << 8) | ((unsigned int)buf[2] << 16) |
            ((unsigned int)buf[3] << 24);
  *TEST_OUT = out;
  return 0;
}
