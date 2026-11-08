#!/bin/bash
# Compila el motor NTFS de Forja (mkntfs + puente JNI) para esta PC, en la carpeta $1.
# Lo usan las pruebas de Forja y de Ventoy no oficial.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
OUT=$1
C=$H/../app/src/main/cpp; V=$C/ntfs-3g
JH=$(dirname $(dirname $(readlink -f $(which javac))))
mkdir -p $OUT/obj
INC="-I$C/ntfs-config -I$V/include/ntfs-3g -I$V/ntfsprogs -I$C -I$JH/include -I$JH/include/linux -DHAVE_CONFIG_H -D_FILE_OFFSET_BITS=64 -O1 -w -fPIC -include $C/forja_compat.h"
for f in $V/libntfs-3g/*.c $V/ntfsprogs/{attrdef,boot,sd,utils}.c $C/forja_ntfs.c $C/forja_jni.c; do gcc $INC -c $f -o $OUT/obj/$(basename $f .c).o; done
gcc $INC -Dmain=forja_mkntfs_main -Dntfs_device_unix_io_ops=forja_io_ops -Dntfs_log_handler_outerr=forja_log_handler -c $V/ntfsprogs/mkntfs.c -o $OUT/obj/mkntfs.o
gcc -shared $OUT/obj/*.o -o $OUT/libforjantfs.so
