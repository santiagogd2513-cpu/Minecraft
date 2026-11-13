OUTPUT_FORMAT("elf32-powerpc")
ENTRY(_dll_start)
SECTIONS
{
  . = BASE;
  .hdr : { . += 0x1000; }
  .text ALIGN(0x1000) : { *(.text .text.*) *(.xthunk) }
  .rdata ALIGN(0x1000) : { *(.rodata .rodata.*) *(.sdata2 .sdata2.*) }
  .data ALIGN(0x1000) : { *(.data .data.*) *(.sdata .sdata.*) *(.xslot) *(.bss .bss.* .sbss .sbss.* COMMON) . = ALIGN(0x1000); }
  /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.gnu*) *(.PPC.EMB*) }
}
