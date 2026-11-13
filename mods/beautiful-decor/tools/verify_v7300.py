#!/usr/bin/env python3
"""Comprueba SOLO la V7.3.0 (carga de vagones)."""
import sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.3.0'" in (R/"build.gradle").read_text() and "mod_version=7.3.0" in (R/"gradle.properties").read_text(),"versión")
need("V7.3.0" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.3.0"),"docs")
t=(J/"entity/TrainCarEntity.java").read_text(); r=(J/"client/TrainCarRenderer.java").read_text()
need("cargoKind()" in t and t.count("refreshKind()")>=4 and "KIND, \"\"" in t,"tipo de carga")
need("Cargo.MINERAL, 10000" in t and "Cargo.COAL, 8000" in t,"capacidades")
need("setTint" in r and "cargo_mineral_light" in r and "tintR" in r,"color")
need((R/"src/main/resources/assets/beautifuldecor/textures/entity/cargo_mineral_light.png").exists(),"textura")
need(t.count("{")==t.count("}") and r.count("{")==r.count("}"),"llaves")
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.3.0")
