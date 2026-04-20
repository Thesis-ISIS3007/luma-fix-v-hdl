typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  u32 dividend = 20u;
  u32 divisor = 6u;
  u32 q = 0u;

  while (dividend >= divisor) {
    dividend -= divisor;
    q++;
  }

  *TEST_OUT = q * 100u + dividend;
  return 0;
}
