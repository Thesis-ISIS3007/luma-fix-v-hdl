typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

__attribute__((noinline))
static u32 func(void) {
  return 7u;
}

int main(void) {
  u32 v = func();
  u32 out = (v == 7u) ? 1u : 0u;
  *TEST_OUT = out;
  return 0;
}
