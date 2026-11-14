#!/bin/bash
# Prueba el motor de física en el computador (sin Android).
set -e
cd "$(dirname "$0")"
W=$(mktemp -d)
javac -encoding UTF-8 -d $W ../app/src/main/java/com/forja/orbita/Sim.java SimTest.java
java -cp $W com.forja.orbita.SimTest
