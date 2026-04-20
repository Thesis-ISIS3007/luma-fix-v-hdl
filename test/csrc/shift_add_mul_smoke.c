typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 multiplicand = 3u;
  u32 multiplier = 6u;
  u32 acc = 0u;

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
