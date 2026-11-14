#!/bin/bash
# Prueba en el computador del protocolo «celular a celular» (sin Android): java + una clase falsa de android.os.Build.
set -e
cd "$(dirname "$0")"
W=$(mktemp -d); mkdir -p $W/stub/android/os $W/out
cat > $W/stub/android/os/Build.java <<'J'
package android.os;
public class Build { public static class VERSION { public static int SDK_INT = 34; } }
J
S=../app/src/main/java/com/boveda/archivos
javac -encoding UTF-8 -d $W/out $W/stub/android/os/Build.java $S/LinkWire.java $S/LinkVfs.java $S/LinkServer.java $S/LinkClient.java $S/Paths.java LinkTest.java
java -cp $W/out com.boveda.archivos.LinkTest
