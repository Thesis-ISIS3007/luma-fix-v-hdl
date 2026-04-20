typedef int s32;
typedef unsigned int u32;

static volatile u32 *const TEST_OUT = (volatile u32 *)0x80u;

int main(void) {
  s32 s_neg1 = -1;
  s32 s_pos1 = 1;
  u32 u_neg1 = 0xffffffffu;
  u32 u_pos1 = 1u;

  u32 score = 0u;

  if (s_neg1 == s_neg1) {
    score++;
  }
  if (s_pos1 != s_neg1) {
    score++;
  }
  if (s_neg1 < s_pos1) {
    score++;
  }
  if (s_pos1 >= s_neg1) {
    score++;
  }
  if (!(u_neg1 < u_pos1)) {
    score++;
  }
  if (u_neg1 >= u_pos1) {
    score++;
  }

  *TEST_OUT = score;
  return 0;
}
