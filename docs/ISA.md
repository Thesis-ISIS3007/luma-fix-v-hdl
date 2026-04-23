# LumaFixV ISA

## RV32I Coverage

This design implements the core RV32I integer instruction set needed for
register-register arithmetic, immediates, branches, jumps, and byte/halfword/
word memory access.

Unsupported instructions are treated as illegal by the decoder.

### Implemented

| Instruction | Status | Notes |
| --- | --- | --- |
| LUI | implemented | Loads a 20-bit upper immediate into `rd`. |
| AUIPC | implemented | Adds the upper immediate to the current `pc`. |
| JAL | implemented | PC-relative jump with `pc + 4` written to `rd`. |
| JALR | implemented | Register-indirect jump with `pc + 4` written to `rd`. |
| BEQ | implemented | Branch if equal. |
| BNE | implemented | Branch if not equal. |
| BLT | implemented | Signed branch if less than. |
| BGE | implemented | Signed branch if greater than or equal. |
| BLTU | implemented | Unsigned branch if less than. |
| BGEU | implemented | Unsigned branch if greater than or equal. |
| LB | implemented | Load signed byte. |
| LH | implemented | Load signed halfword. |
| LW | implemented | Load word. |
| LBU | implemented | Load unsigned byte. |
| LHU | implemented | Load unsigned halfword. |
| SB | implemented | Store byte. |
| SH | implemented | Store halfword. |
| SW | implemented | Store word. |
| ADDI | implemented | Add immediate. |
| SLTI | implemented | Signed set-less-than immediate. |
| SLTIU | implemented | Unsigned set-less-than immediate. |
| XORI | implemented | Bitwise XOR immediate. |
| ORI | implemented | Bitwise OR immediate. |
| ANDI | implemented | Bitwise AND immediate. |
| SLLI | implemented | Logical left shift immediate. |
| SRLI | implemented | Logical right shift immediate. |
| SRAI | implemented | Arithmetic right shift immediate. |
| ADD | implemented | Add registers. |
| SUB | implemented | Subtract registers. |
| SLL | implemented | Logical left shift register. |
| SLT | implemented | Signed set-less-than register. |
| SLTU | implemented | Unsigned set-less-than register. |
| XOR | implemented | Bitwise XOR register. |
| SRL | implemented | Logical right shift register. |
| SRA | implemented | Arithmetic right shift register. |
| OR | implemented | Bitwise OR register. |
| AND | implemented | Bitwise AND register. |

### Not Implemented

| Instruction | Status | Notes |
| --- | --- | --- |
| FENCE | not implemented | Memory ordering fence. |
| FENCE.I | not implemented | Instruction cache fence. |
| ECALL | not implemented | Environment call. |
| EBREAK | not implemented | Environment breakpoint. |
| CSRRW | not implemented | CSR access instruction. |
| CSRRS | not implemented | CSR access instruction. |
| CSRRC | not implemented | CSR access instruction. |
| CSRRWI | not implemented | CSR access instruction. |
| CSRRSI | not implemented | CSR access instruction. |
| CSRRCI | not implemented | CSR access instruction. |

## Summary

Implemented: 32 integer instructions across the U, J, B, load/store, OP-IMM,
and OP groups.

Not implemented: 10 system/fence/CSR instructions.

## FX32

The FX32 extension adds signed 16Q16 fixed-point arithmetic (16 integer bits +
16 fractional bits, two's complement, wrap-around on overflow). All values
live in the existing 32-bit architectural register file; the encoding occupies
the RISC-V custom-0 opcode space.

### Encoding

All FX instructions use the R-type layout with `opcode = 0b0001011`. The
sub-operation is selected by `funct3` (and `funct7` for FXADD vs FXSUB):

| Mnemonic | funct7      | funct3 | Form | Semantics                              |
| ---      | ---         | ---    | ---  | ---                                    |
| FXADD    | `0000000`   | `000`  | R    | `rd = rs1 + rs2`                       |
| FXSUB    | `0100000`   | `000`  | R    | `rd = rs1 - rs2`                       |
| FXMUL    | (any)       | `001`  | R    | `rd = (rs1*rs2) >>s 16`                |
| FXNEG    | (any)       | `010`  | R\*  | `rd = -rs1` (rs2 ignored)              |
| INT2FX   | (any)       | `011`  | R\*  | `rd = rs1 << 16`                       |
| FX2INT   | (any)       | `100`  | R\*  | `rd = rs1 >>s 16`                      |
| FXABS    | (any)       | `101`  | R\*  | `rd = |rs1|` (rs2 ignored)             |
| FXDIV    | (any)       | `110`  | R    | reserved (illegal in v1; phase 2)      |

R\* = unary form, `rs2` field ignored by hardware.

### Implementation

Each FX instruction is cracked at decode time by an `FxSequencer` into a short
sequence of one or more existing pipeline micro-ops. Most ops are bit-identical
to a single RV32I ALU op and execute in one cycle; FXMUL adds a new ALU
operation; FXABS is decomposed into three micro-ops using an internal,
ABI-invisible scratch register file (4 entries, `s0..s3`) addressed via a 6th
bit on the internal register ports. Multi-µop sequences hold the IF/ID latch
stable until the last µop commits, so PC only advances per architectural
instruction. Scratch writes are suppressed from the debug writeback stream.

### Micro-op decomposition

| Mnemonic | µops | Sequence                                                              |
| ---      | ---  | ---                                                                   |
| FXADD    | 1    | `add  rd, rs1, rs2`                                                   |
| FXSUB    | 1    | `sub  rd, rs1, rs2`                                                   |
| FXMUL    | 1    | `fxmul rd, rs1, rs2` (new ALU op: signed 32x32, `>>s 16`, truncate)   |
| FXNEG    | 1    | `sub  rd, x0,  rs1`                                                   |
| INT2FX   | 1    | `slli rd, rs1, 16`                                                    |
| FX2INT   | 1    | `srai rd, rs1, 16`                                                    |
| FXABS    | 3    | `srai s0, rs1, 31; xor s1, rs1, s0; sub rd, s1, s0`                   |

### Implemented

| Instruction | Status      | Notes                                                |
| ---         | ---         | ---                                                  |
| FXADD       | implemented | 1-µop, reuses `add`.                                 |
| FXSUB       | implemented | 1-µop, reuses `sub`.                                 |
| FXMUL       | implemented | 1-µop, new `fxmul` ALU op (no rounding).             |
| FXNEG       | implemented | 1-µop, `sub rd, x0, rs1`.                            |
| INT2FX      | implemented | 1-µop, `slli rd, rs1, 16`.                           |
| FX2INT      | implemented | 1-µop, `srai rd, rs1, 16` (truncates fraction).      |
| FXABS       | implemented | 3-µop branchless absolute value via scratch s0/s1.   |

### Not Implemented

| Instruction | Status          | Notes                                            |
| ---         | ---             | ---                                              |
| FXDIV       | not implemented | Phase 2: requires multi-cycle iterative divider. |

### Semantics notes

- All overflow is wrap-around (no saturation in v1).
- FXMUL truncates the fractional bits (no rounding, no overflow detection).
- FXABS of `INT_MIN` (`0x80000000`) returns `INT_MIN` due to two's-complement
  wrap-around, matching the algebraic identity of the branchless expansion.
- FXDIV is reserved; executing it raises the illegal-instruction signal.

### Summary

Implemented: 7 FX 16Q16 instructions cracked at decode by the FX sequencer.
Reserved: 1 (FXDIV).

## RT

### Implemented

### Not Implemented

| Instruction | Status | Notes |
| --- | --- | --- |
| RTPACK | not implemented | |
| RTBVH | not implemented | |
| RTHIT | not implemented | |

### Summary