typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 a0 = 4u;
  u32 a1 = 7u;
  u32 a2 = 3u;
  u32 out = a0 + (a1 << 1) + a2;
  *TEST_OUT = out;
  return 0;
}
