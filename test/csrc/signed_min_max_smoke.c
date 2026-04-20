typedef int s32;
typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  s32 x1 = 5;
  s32 x2 = 9;
  s32 x3 = -3;
  s32 x4 = 2;

  s32 p1 = (x1 < x2) ? x2 : x1;
  s32 p2 = (x3 < x4) ? x4 : x3;

  *TEST_OUT = (u32)(p1 * 100 + p2);
  return 0;
}
