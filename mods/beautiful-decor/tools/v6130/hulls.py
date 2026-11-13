#!/usr/bin/env python3
"""V6.13.0: mide cada modelo (ancho, alto, largo y centro en metros) para las cajas de colisión de VehicleHull. Solo lee los modelos: no escribe nada.
Uso: python3 -m tools.v6130.hulls   (las medidas sin retrovisores son las que se copiaron a los vehículos en Java)."""
import numpy as np
from tools.v200.softrender import collect, model_to_world
def run(name, mod, pxb=16.0, skip=()):
    try:
        b=mod.build()
        out=[]
        collect(b.root,np.eye(3),np.zeros(3),256,256,out)
        pts=np.concatenate([model_to_world(w) for w,_,m,mt in [(o[0],0,o[2],o[3]) for o in out] if True]) if not skip else np.concatenate([model_to_world(o[0]) for o in out if o[3] not in skip])
        mn=pts.min(0)/pxb; mx=pts.max(0)/pxb
        print(f"{name:16s} x[{mn[0]:.2f},{mx[0]:.2f}] y[{mn[1]:.2f},{mx[1]:.2f}] z[{mn[2]:.2f},{mx[2]:.2f}]  W={mx[0]-mn[0]:.2f} H={mx[1]-mn[1]:.2f} L={mx[2]-mn[2]:.2f}")
    except Exception as e:
        print(name, "ERR", repr(e)[:150])
from tools.v200 import moto_model
from tools.v210 import bike, truck
from tools.v300 import delivery_truck, helicopter
from tools.v380 import airliner
from tools.v310 import rc_car
run("motorcycle", moto_model); run("bicycle", bike); run("pickup", truck); run("delivery", delivery_truck)
run("heli+rotor", helicopter); run("heli body", helicopter, skip=("blade","tip","hub"))
run("airliner", airliner, 16.0); run("rc_car", rc_car)
print("---- sin retrovisores")
from tools.v370 import supercar
run("supercar", supercar, skip=("mirror",))
run("pickup", truck, skip=("mirror",)); run("delivery", delivery_truck, skip=("mirror",)); run("moto", moto_model, skip=("mirror","mirror_glass"))
run("bike", bike, skip=("bell",))
