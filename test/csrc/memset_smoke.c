typedef unsigned int u32;
typedef unsigned char u8;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u8 buf[4] = {0u, 0u, 0u, 0u};
  for (u32 i = 0u; i < 4u; ++i) {
    buf[i] = 0x7au;
  }

  u32 out = ((u32)buf[0]) | ((u32)buf[1] << 8) | ((u32)buf[2] << 16) | ((u32)buf[3] << 24);
  *TEST_OUT = out;
  return 0;
}
