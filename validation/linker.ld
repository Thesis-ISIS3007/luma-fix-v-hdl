OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY {
  RAM (rwx) : ORIGIN = 0x00000000, LENGTH = 0x00008000
}

SECTIONS {
  .text : {
    *(.text.start)
    *(.text*)
  } > RAM

  .rodata : {
    *(.rodata*)
  } > RAM

  . = ALIGN(4);
  .data : {
    *(.data*)
  } > RAM

  .bss (NOLOAD) : {
    *(.bss*)
    *(COMMON)
  } > RAM

  . = ALIGN(16);
  _stack_top = ORIGIN(RAM) + LENGTH(RAM);
}
