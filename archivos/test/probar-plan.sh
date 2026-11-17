#!/bin/bash
# Prueba en el computador el lector de planos .bdplan y la malla 3D (sin Android). Con un plano real: probar-plan.sh ruta/al/plano.bdplan
set -e
cd "$(dirname "$0")"
W=$(mktemp -d)
S=../app/src/main/java/com/boveda/archivos
javac -encoding UTF-8 -d $W $S/BdPlan.java $S/BlockColors.java $S/EntityModels.java $S/VoxelMesh.java PlanTest.java
java -Xmx700m -cp $W com.boveda.archivos.PlanTest "$@"
