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

### Implemented

### Not Implemented\

| Instruction | Status | Notes |
| --- | --- | --- |
| FXADD | not implemented | |
| FXSUB | not implemented | |

### Summary

## RT

### Implemented

### Not Implemented

| Instruction | Status | Notes |
| --- | --- | --- |
| RTPACK | not implemented | |
| RTBVH | not implemented | |
| RTHIT | not implemented | |

### Summary