#!/bin/bash
# Compila un plugin con el compilador PowerPC y lo empaqueta como XEX. Uso: ./build.sh plugin/hola.c build/hola.xex
set -e
cd "$(dirname "$0")"
SRC=${1:-plugin/hola.c}; OUT=${2:-build/hola.xex}; BASE=${BASE:-0x92000000}
mkdir -p build
CC=powerpc-linux-gnu-gcc
python3 tools/xexpack.py gen-imports sdk/imports.txt > build/imports.S
CFLAGS="-m32 -mbig-endian -mcpu=powerpc64 -msdata=none -fno-pic -fno-pie -ffreestanding -fno-builtin -nostdlib -fshort-wchar -fno-exceptions -fno-asynchronous-unwind-tables -mno-altivec -mno-multiple -mno-string -O2 -Wall -std=gnu11"
$CC $CFLAGS -c "$SRC" -o build/plugin.o
$CC -m32 -mbig-endian -mcpu=powerpc64 -c build/imports.S -o build/imports.o
powerpc-linux-gnu-ld -m elf32ppc -nostdlib -static --defsym=BASE=$BASE -T sdk/link.ld build/plugin.o build/imports.o -o build/plugin.elf
python3 tools/xexpack.py pack build/plugin.elf sdk/imports.txt "$OUT" --base $BASE ${FLAGS:+--flags $FLAGS}
