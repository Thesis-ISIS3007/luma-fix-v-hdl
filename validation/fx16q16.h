#ifndef FX16Q16_H
#define FX16Q16_H

// 16Q16 fixed-point intrinsics for the LumaFixV FX extension. All ops live in
// the RISC-V custom-0 opcode space (0b0001011); GAS' .insn r directive emits
// the right R-type encoding without needing a custom binutils.
//
// The compiler does not know these instructions exist so it cannot reorder
// across them or fold their results; "volatile" is therefore unnecessary.
//
// Encoding (must stay in sync with src/Instr.scala):
//   funct3 = 0, funct7 = 0x00 -> FXADD
//   funct3 = 0, funct7 = 0x20 -> FXSUB
//   funct3 = 1, funct7 = any  -> FXMUL
//   funct3 = 2, funct7 = any  -> FXNEG  (rs2 ignored)
//   funct3 = 3, funct7 = any  -> INT2FX (rs2 ignored)
//   funct3 = 4, funct7 = any  -> FX2INT (rs2 ignored)
//   funct3 = 5, funct7 = any  -> FXABS  (rs2 ignored)
//   funct3 = 6, funct7 = any  -> FXDIV

typedef int fx_t;

#define FX_OPCODE 0x0b
#define FX_FRAC_BITS 16
#define FX_ONE ((fx_t)(1 << FX_FRAC_BITS))

// Convenience: build a 16Q16 literal from a C double at compile time.
#define fx_from_double(d) ((fx_t)((d) * (double)FX_ONE))

static inline fx_t fxadd(fx_t a, fx_t b) {
  fx_t r;
  __asm__(".insn r 0x0b, 0, 0x00, %0, %1, %2"
          : "=r"(r)
          : "r"(a), "r"(b));
  return r;
}

static inline fx_t fxsub(fx_t a, fx_t b) {
  fx_t r;
  __asm__(".insn r 0x0b, 0, 0x20, %0, %1, %2"
          : "=r"(r)
          : "r"(a), "r"(b));
  return r;
}

static inline fx_t fxmul(fx_t a, fx_t b) {
  fx_t r;
  __asm__(".insn r 0x0b, 1, 0x00, %0, %1, %2"
          : "=r"(r)
          : "r"(a), "r"(b));
  return r;
}

static inline fx_t fxneg(fx_t a) {
  fx_t r;
  __asm__(".insn r 0x0b, 2, 0x00, %0, %1, x0"
          : "=r"(r)
          : "r"(a));
  return r;
}

static inline fx_t int2fx(int a) {
  fx_t r;
  __asm__(".insn r 0x0b, 3, 0x00, %0, %1, x0"
          : "=r"(r)
          : "r"(a));
  return r;
}

static inline int fx2int(fx_t a) {
  int r;
  __asm__(".insn r 0x0b, 4, 0x00, %0, %1, x0"
          : "=r"(r)
          : "r"(a));
  return r;
}

static inline fx_t fxabs(fx_t a) {
  fx_t r;
  __asm__(".insn r 0x0b, 5, 0x00, %0, %1, x0"
          : "=r"(r)
          : "r"(a));
  return r;
}

static inline fx_t fxdiv(fx_t a, fx_t b) {
  fx_t r;
  __asm__(".insn r 0x0b, 6, 0x00, %0, %1, %2"
          : "=r"(r)
          : "r"(a), "r"(b));
  return r;
}

#endif
