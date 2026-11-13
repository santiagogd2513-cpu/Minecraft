#!/usr/bin/env python3
"""Comprueba SOLO la V7.6.0 (física activa desde el inicio, marcha atrás se sale de la vía)."""
import sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.6.0'" in (R/"build.gradle").read_text() and "mod_version=7.6.0" in (R/"gradle.properties").read_text(),"versión")
need("V7.6.0" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.6.0"),"docs")
b=(J/"entity/BridgeLoads.java").read_text()
need("BooleanValue.create(true)" in b and "BooleanValue.create(false)" not in b,"gamerule activa por defecto")
t=(J/"entity/TrainCarEntity.java").read_text()
need("Math.abs(v) > RUN_OFF_SPEED" in t,"marcha atrás se sale")
for f in [J/"entity/TrainCarEntity.java",J/"entity/BridgeLoads.java"]:
    s=f.read_text(); need(s.count("{")==s.count("}"),"llaves "+f.name)
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.6.0")
