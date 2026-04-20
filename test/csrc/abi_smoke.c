typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;
volatile int seed;

__attribute__((noinline))
static int sum10(
    int a0,
    int a1,
    int a2,
    int a3,
    int a4,
    int a5,
    int a6,
    int a7,
    int a8,
    int a9
) {
  return a0 + a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9;
}

int main(void) {
  seed = 0;
  int r = sum10(1 + seed, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  *TEST_OUT = (u32)r;
  return 0;
}
