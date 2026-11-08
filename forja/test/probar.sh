#!/bin/bash
# Formatea discos de mentira (archivos dispersos) con todas las combinaciones y los revisa con
# las herramientas oficiales de Linux. Lo usa la compilación de GitHub antes de publicar.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
W=$(mktemp -d)
javac -d $W/out $H/../app/src/main/java/com/forja/formateador/{BlockDevice,Progress,Io,Partitioner,FsType,Fat32Formatter,ExFatFormatter,Ext4Formatter,Forja}.java $H/FileDev.java $H/FTest.java
G=$((1<<30)); TB=$((1<<40))
fallas=0
sudo_() { if [ -n "${USAR_LOOP:-}" ]; then sudo "$@"; else "$@"; fi; }
probar() {   # bytes sector FS TABLA cluster etiqueta
  rm -f $W/d.img $W/p.img
  java -cp $W/out FTest $W/d.img "$@" > $W/log.txt
  echo "== $3 $4 sector=$2 $(( $1 >> 30 )) GB clúster=$5: $(cat $W/log.txt)"
  if [ "$4" = GPT ] && [ "$2" = 512 ]; then sgdisk -v $W/d.img | grep -q "No problems found" || { echo "   GPT con problemas"; fallas=$((fallas+1)); }; fi
  # la partición se revisa en el lugar (dispositivo loop con desplazamiento) si se puede;
  # si no, se copia a un archivo aparte
  read START COUNT < <(python3 $H/extract.py $W/d.img - $2)
  P=$W/p.img; LOOP=""
  if [ -n "${USAR_LOOP:-}" ]; then
    LOOP=$(sudo losetup --sector-size $2 -o $((START*$2)) --sizelimit $((COUNT*$2)) -f --show $W/d.img); P=$LOOP
  else
    python3 $H/extract.py $W/d.img $W/p.img $2 >/dev/null
  fi
  ok=1
  case "$3" in
    FAT32) sudo_ fsck.fat -n $P | tail -1 ;;
    EXFAT) sudo_ fsck.exfat -n $P | tail -1 ;;
    EXT4) sudo_ e2fsck -fn $P | tail -1 ;;
  esac || ok=0
  if [ $ok = 0 ]; then
    echo "   FALLÓ"; fallas=$((fallas+1))
    if [ "$3" = EXFAT ]; then sudo_ dump.exfat $P 2>&1 | head -25; fi
  fi
  if [ -n "$LOOP" ]; then sudo losetup -d $LOOP; fi
}
probar $G 512 FAT32 MBR 0 FORJA
probar $G 4096 FAT32 GPT 0 FORJA
probar $((40*G)) 512 FAT32 GPT 0 JUEGOS
probar $((40*G)) 512 FAT32 MBR 4096 JUEGOS
probar $((2*TB-G)) 512 FAT32 MBR 0 GRANDE
probar $((3*TB)) 4096 FAT32 GPT 0 GRANDE
probar $G 512 EXFAT MBR 0 Forja
probar $((40*G)) 512 EXFAT GPT 0 Forja
probar $((40*G)) 4096 EXFAT GPT 0 Forja
probar $((40*G)) 512 EXFAT MBR 512 Forja
probar $((4*TB)) 512 EXFAT GPT 0 Discazo
probar $((4*TB)) 4096 EXFAT GPT 0 Discazo
probar $G 512 EXT4 MBR 1024 Linux
probar $G 512 EXT4 GPT 2048 Linux
probar $((40*G)) 4096 EXT4 GPT 0 Linux
probar $((200<<20)) 512 EXT4 MBR 0 Linux
probar $((4*TB)) 512 EXT4 GPT 0 Linux
rm -rf $W
echo "fallas: $fallas"
test $fallas = 0
