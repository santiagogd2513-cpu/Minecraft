#!/usr/bin/env python3
"""Comprueba SOLO la V7.7.1 (helicóptero: la caja ya no empuja la posición)."""
import sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/"src/main/java/com/example/beautifuldecor/entity"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.7.1'" in (R/"build.gradle").read_text() and "mod_version=7.7.1" in (R/"gradle.properties").read_text(),"versión")
need("V7.7.1" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.7.1"),"docs")
h=(J/"VehicleHull.java").read_text()
need("boolean centered" in h and "if (!centered)" in h and h.rstrip().endswith("}"),"fixPosition solo corrige si la caja es la del casco")
need("refresh(e, s);" in h.split("public static void fixPosition")[1],"fixPosition devuelve la caja del casco")
he=(J/"HelicopterEntity.java").read_text()
need("VehicleHull.fixPosition(this, HULL)" in he and "TAKEOFF_RPM = 0.6f" in he and "CLIMB_SPEED * margin" in he,"vuelo intacto")
for f in [J/"VehicleHull.java",J/"HelicopterEntity.java"]:
    s=f.read_text(); need(s.count("{")==s.count("}"),"llaves "+f.name)
# simulación: caja descentrada (casco) y caja normal (setPos) dan la misma posición tras fixPosition
import math
front=-1.6
for yaw in (0,37,90,200,-75):
    y=math.radians(yaw); px,pz=10.0,-4.0
    cx,cz=px-math.sin(y)*front,pz+math.cos(y)*front      # caja del casco
    ok_h=abs(cx-px)>=1e-7 or abs(cz-pz)>=1e-7
    nx,nz=(cx+math.sin(y)*front,cz-math.cos(y)*front) if ok_h else (px,pz)
    need(abs(nx-px)<1e-9 and abs(nz-pz)<1e-9,f"casco yaw {yaw}")
    cx,cz=px,pz                                           # caja normal tras setPos
    centered=abs(cx-px)<1e-7 and abs(cz-pz)<1e-7
    need(centered,f"normal yaw {yaw}")
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.7.1")
