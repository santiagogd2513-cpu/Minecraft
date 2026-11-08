#!/bin/bash
# Formatea discos de mentira (archivos dispersos) con todas las combinaciones y los revisa con
# las herramientas oficiales de Linux. Lo usa la compilación de GitHub antes de publicar.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
W=$(mktemp -d)
javac -d $W/out $H/../app/src/main/java/com/forja/formateador/{BlockDevice,Progress,Io,Partitioner,FsType,Fat32Formatter,ExFatFormatter,Forja}.java $H/FileDev.java $H/FTest.java
G=$((1<<30)); TB=$((1<<40))
fallas=0
probar() {   # bytes sector FS TABLA cluster etiqueta
  rm -f $W/d.img $W/p.img
  java -cp $W/out FTest $W/d.img "$@" > $W/log.txt
  echo "== $3 $4 sector=$2 $(( $1 >> 30 )) GB clúster=$5: $(cat $W/log.txt)"
  if [ "$4" = GPT ] && [ "$2" = 512 ]; then sgdisk -v $W/d.img | grep -q "No problems found" || { echo "   GPT con problemas"; fallas=$((fallas+1)); }; fi
  python3 $H/extract.py $W/d.img $W/p.img $2 >/dev/null
  case "$3" in
    FAT32) fsck.fat -n $W/p.img | tail -1 ;;
    EXFAT) fsck.exfat -n $W/p.img | tail -1 ;;
  esac || { echo "   FALLÓ"; fallas=$((fallas+1)); }
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
rm -rf $W
echo "fallas: $fallas"
test $fallas = 0
