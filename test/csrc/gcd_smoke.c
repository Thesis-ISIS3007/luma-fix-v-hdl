typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x84u;

__attribute__((noinline))
static u32 gcd_sub(u32 a, u32 b) {
  while (a != b) {
    if (a > b) {
      a = a - b;
    } else {
      b = b - a;
    }
  }
  return a;
}

int main(void) {
  u32 a = 48u;
  u32 b = 18u;
  u32 g = gcd_sub(a, b);
  *TEST_OUT = g;
  return 0;
}
