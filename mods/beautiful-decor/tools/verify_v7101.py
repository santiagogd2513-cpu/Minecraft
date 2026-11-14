#!/usr/bin/env python3
"""Comprueba SOLO la V7.10.1 (vagones de carga sobrecargables)."""
import sys, json
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor"; A=R/"src/main/resources/assets/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.10.1'" in (R/"build.gradle").read_text() and "mod_version=7.10.1" in (R/"gradle.properties").read_text(),"versión")
need("V7.10.1" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.10.1"),"docs")
t=(J/"entity/TrainCarEntity.java").read_text()
need("OVERFILL = 2.0" in t and "maxUnits()" in t and "(this.maxUnits() - this.units) / per" in t,"tope nuevo en addCargo")
need("this.fillUnits() < this.maxUnits()" in t,"agachado también usa el tope nuevo")
need("this.fillUnits() * this.variant().kgPerUnit" in t,"el peso sigue sumando por unidad")
need("v.isSteam() ? v.capacity" in t,"el ténder conserva su tope")
r=(J/"client/TrainCarRenderer.java").read_text(); need("float over =" in r and "LAYERS * 2" in r,"montón desbordado")
for l in ("es_es","en_us"): need("hud.beautifuldecor.train_cargo_over" in json.loads((A/f"lang/{l}.json").read_text(encoding="utf-8")),"lang "+l)
for f in ("entity/TrainCarEntity.java","client/TrainCarRenderer.java"):
    s=(J/f).read_text(); need(s.count("{")==s.count("}"),"llaves "+f)
if bad: print("FALLÓ:",bad); sys.exit(1)
print("V7.10.1 OK")
