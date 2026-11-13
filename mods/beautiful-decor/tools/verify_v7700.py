#!/usr/bin/env python3
"""Comprueba SOLO la V7.7.0 (subir un bloque)."""
import sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor/entity"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.7.0'" in (R/"build.gradle").read_text() and "mod_version=7.7.0" in (R/"gradle.properties").read_text(),"versión")
need("V7.7.0" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.7.0"),"docs")
v=(J/"VehicleEntity.java").read_text(); a=(J/"AirlinerEntity.java").read_text()
need("lift > 0.0 ? lift : vy" in v and "vy + lift" not in v,"vehículos: el escalón ya no pierde la gravedad")
need("-GRAVITY" not in v and "GRAVITY = 0.0375" in v,"gravedad intacta")
need("!this.isGearUp()" in a and "0.52, 1.02" in a and "lift > 0.0 ? new Vec3" in a,"avión sube un bloque")
for n in ["Pickup","Supercar","DeliveryTruck","Motorcycle"]:
    need("0.52, 1.02" in (J/f"{n}Entity.java").read_text().replace("{0.52, 1.02}","0.52, 1.02"),"escalones "+n)
need("0.52}" in (J/"BicycleEntity.java").read_text(),"escalón bici")
for f in [J/"VehicleEntity.java",J/"AirlinerEntity.java"]:
    s=f.read_text(); need(s.count("{")==s.count("}"),"llaves "+f.name)
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.7.0")
