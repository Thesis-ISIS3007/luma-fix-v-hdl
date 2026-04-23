#include "validation.h"

static volatile unsigned int *const TEST_OUT = (volatile unsigned int *)0x80u;

int main(void) {
  int s_neg1 = -1;
  int s_pos1 = 1;
  unsigned int u_neg1 = 0xffffffffu;
  unsigned int u_pos1 = 1u;

  unsigned int score = 0u;

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
