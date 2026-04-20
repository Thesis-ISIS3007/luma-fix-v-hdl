typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 iterations = 0u;
  for (u32 i = 0u; i < 10u; ++i) {
    iterations++;
    if (iterations == 3u) {
      break;
    }
  }

  *TEST_OUT = iterations;
  return 0;
}
