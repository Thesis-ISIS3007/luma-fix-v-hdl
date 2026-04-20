typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 src[2] = {11u, 22u};
  u32 dst[2] = {0u, 0u};

  for (u32 i = 0u; i < 2u; ++i) {
    dst[i] = src[i];
  }

  *TEST_OUT = (dst[0] * 100u) + dst[1];
  return 0;
}
