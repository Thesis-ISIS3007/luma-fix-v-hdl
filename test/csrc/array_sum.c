typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 a[4] = {1u, 2u, 3u, 4u};
  u32 sum = 0u;
  for (u32 i = 0u; i < 4u; ++i) {
    sum += a[i];
  }
  *TEST_OUT = sum;
  return 0;
}
