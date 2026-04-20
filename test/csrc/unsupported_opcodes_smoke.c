typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 x = 5u;

  // Inject unsupported/system-style instructions.
  asm volatile(".word 0x0000000f\n\t.word 0x00000073" ::: "memory");

  x = x + 3u;
  *TEST_OUT = x;
  return 0;
}
