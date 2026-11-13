#!/usr/bin/env python3
"""Comprueba SOLO la V7.2.1 (enganche rápido y máximo de 500 coches)."""
import json,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.2.1'" in (R/"build.gradle").read_text() and "mod_version=7.2.1" in (R/"gradle.properties").read_text(),"versión")
need("V7.2.1" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.2.1"),"docs")
t=(J/"entity/TrainCarEntity.java").read_text(); i=(J/"item/TrainCarItem.java").read_text()
need("MAX_TRAIN_CARS = 500" in t and "attachToTrain" in t and "tailOfTrain" in t and "trainLength" in t,"entidad")
need("attachToTrain" in i and i.count("MAX_TRAIN_CARS")>=2,"ítem")
need(t.count("{")==t.count("}") and i.count("{")==i.count("}"),"llaves")
for l in ("es_es","en_us"):
    need("message.beautifuldecor.train_max_cars" in json.loads((R/f"src/main/resources/assets/beautifuldecor/lang/{l}.json").read_text()),l)
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.2.1")
