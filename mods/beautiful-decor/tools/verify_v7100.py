#!/usr/bin/env python3
"""Comprueba SOLO la V7.10.0 (remolque niñera de dos pisos)."""
import sys, json
from pathlib import Path
R=Path(__file__).resolve().parent.parent
S=R/"src/main"; J=S/"java/com/example/beautifuldecor"; A=S/"resources/assets/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.10.0'" in (R/"build.gradle").read_text() and "mod_version=7.10.0" in (R/"gradle.properties").read_text(),"versión")
need("V7.10.0" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.10.0"),"docs")
need('"trailer_carrier"' in (J/"init/ModItems.java").read_text() and '"trailer_carrier"' in (J/"init/ModCreativeTabs.java").read_text(),"item/pestaña")
need((A/"models/item/trailer_carrier.json").exists() and (A/"textures/item/trailer_carrier.png").exists(),"modelo/icono item")
for t in ("trailer_carrier","trailer_carrier_glow"): need((A/f"textures/entity/{t}.png").exists(),"textura "+t)
for l in ("es_es","en_us"):
    t=json.loads((A/f"lang/{l}.json").read_text(encoding="utf-8"))
    for k in ("item.beautifuldecor.trailer_carrier","hud.beautifuldecor.ramp_closed","hud.beautifuldecor.ramp_low","hud.beautifuldecor.ramp_high","hud.beautifuldecor.ramp_busy",
              "hud.beautifuldecor.ramp_none","hud.beautifuldecor.ramp_no_trailer","hud.beautifuldecor.ramp_blocked","hud.beautifuldecor.trailer_has_cars"):
        need(k in t,f"lang {l} {k}")
cl=(J/"client/ClientEvents.java").read_text(); need("TrailerCarrierModel.LAYER" in cl,"capa del modelo")
rd=(J/"client/TrailerRenderer.java").read_text(); need("TrailerCarrierModel" in rd and "getRampLow" in rd,"renderer")
mdl=(J/"client/TrailerCarrierModel.java").read_text(); need("ramp_low" in mdl and "ramp_high" in mdl and "wheel_1r" in mdl,"modelo con rampas")
sem=(J/"entity/SemiTruckEntity.java").read_text(); need("ACTION_RAMP" in sem and "cycleRamp" in sem,"botón de rampa")
need("btn_ramp" in (R/"tools/v790/semi_truck.py").read_text(),"botón en el modelo del tractor")
ve=(J/"entity/VehicleEntity.java").read_text(); need("carriedTick" in ve and "attachTo" in ve and "GRAVITY = 0.0375" in ve,"VehicleEntity")
te=(J/"entity/TrailerEntity.java").read_text(); need("loadScan" in te and "wantsOut" in te and "CARRIER = 3" in te,"TrailerEntity")
for f in ("entity/SemiTruckEntity.java","entity/TrailerEntity.java","entity/VehicleEntity.java","client/TrailerRenderer.java","client/TrailerCarrierModel.java","client/SemiTruckModel.java"):
    s=(J/f).read_text(); need(s.count("{")==s.count("}"),"llaves "+f)
# el paso a la masa de la niñera y las casillas: 3 + 3
need("SLOT_D = {3.3, 8.4, 13.5}" in te and "DECK_H = {1.1875, 3.75}" in te,"casillas 3+3")
# máscara de pintura del tractor regenerada junto con el botón
need((A/"textures/entity/semi_truck_paint.png").stat().st_mtime >= (A/"textures/entity/semi_truck.png").stat().st_mtime-5,"máscara al día")
if bad: print("FALLÓ:",bad); sys.exit(1)
print("V7.10.0 OK")
