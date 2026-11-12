#!/bin/bash
# Arma discos de mentira con el formato de la Xbox 360 y prueba los lectores (XTAF y STFS).
# Lo usa la compilación de GitHub antes de publicar.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
W=$(mktemp -d)
J=$H/../app/src/main/java/com/forja/arca
javac -encoding UTF-8 -d $W/out $J/ByteSource.java $J/Xtaf.java $J/Stfs.java $J/StfsPackage.java $J/StfsEditor.java $J/XboxDisk.java $H/ArcaTest.java
python3 $H/hacer_imagenes.py $W/img
java -cp $W/out com.forja.arca.ArcaTest $W/img
rm -rf $W
