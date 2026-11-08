#!/usr/bin/env python3
"""Comprueba la V1.8.4: orden de los ítems en las dos pestañas del inventario creativo.

Lee las listas de ModCreativeOrder.java y las aplica igual que el código Java (primera entrada que coincide, luego
por nombre) a todos los ítems del mod; comprueba que ninguno quede sin lugar, que no haya entradas muertas y que se
cumpla el orden pedido. Uso: python3 tools/verify_v184_order.py [--print]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor/init"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
errors = []

order_src = (JAVA / "ModCreativeOrder.java").read_text(encoding="utf-8")
tabs_src = (JAVA / "ModCreativeTabs.java").read_text(encoding="utf-8")


def read_array(name):
    m = re.search(name + r"\s*=\s*\{(.*?)\n    \};", order_src, re.S)
    if not m:
        errors.append(f"no encuentro {name} en ModCreativeOrder.java")
        return []
    body = re.sub(r"//[^\n]*", "", m.group(1))
    return re.findall(r'"([^"]+)"', body)


DECOR = read_array("DECOR_ORDER")
TECH = read_array("TECH_ORDER")


def matches(entry, path):
    return path.startswith(entry[:-1]) if entry.endswith("*") else path == entry


def rank(order, path):
    for i, e in enumerate(order):
        if matches(e, path):
            return i
    return len(order)


# pertenencia a cada pestaña, tal como la decide ModCreativeTabs.isTechTabItem
tech_body = tabs_src[tabs_src.index("public static boolean isTechTabItem"):]
tech_body = tech_body[:tech_body.index("private static ItemStack iconOrFallback")]
tech_exact = set(re.findall(r'path\.equals\("([^"]+)"\)', tech_body))
tech_prefix = tuple(re.findall(r'path\.startsWith\("([^"]+)"\)', tech_body))


def is_tech(p):
    return p in tech_exact or p.startswith(tech_prefix)


# V2.0.1: los vehículos conducibles tienen su propia (tercera) pestaña, sin orden propio de ModCreativeOrder
_veh = re.search(r"VEHICLE_IDS\s*=\s*List\.of\((.*?)\);", tabs_src, re.S)
vehicle_ids = set(re.findall(r'"([^"]+)"', _veh.group(1))) if _veh else set()
ids = sorted(f.stem for f in (ASSETS / "models/item").glob("*.json") if f.stem != "tab_icon" and f.stem not in vehicle_ids)
decor_ids = [i for i in ids if not is_tech(i)]
tech_ids = [i for i in ids if is_tech(i)]

for label, order, group in (("decoración", DECOR, decor_ids), ("tecnología", TECH, tech_ids)):
    dup = {e for e in order if order.count(e) > 1}
    if dup:
        errors.append(f"{label}: entradas repetidas {sorted(dup)}")
    lost = [i for i in group if rank(order, i) == len(order)]
    if lost:
        errors.append(f"{label}: ítems sin lugar en el orden: {lost}")
    winners = {rank(order, i) for i in group}
    dead = [e for k, e in enumerate(order) if k not in winners]
    if dead:
        errors.append(f"{label}: entradas que no ordenan nada (¿nombre mal escrito?): {dead}")

decor_sorted = sorted(decor_ids, key=lambda p: (rank(DECOR, p), p))
tech_sorted = sorted(tech_ids, key=lambda p: (rank(TECH, p), p))


def positions(seq, pred):
    return [i for i, p in enumerate(seq) if pred(p)]


def before(seq, pred_a, pred_b, what):
    a, b = positions(seq, pred_a), positions(seq, pred_b)
    if not a or not b:
        errors.append(f"{what}: no se encontraron ítems para comparar")
    elif max(a) >= min(b):
        errors.append(f"orden incorrecto: {what}")


# --- pestaña 1: mesas < sillas y sofás < camas < luces < cocina (y la cocina es lo último)
tables = lambda p: p == "wooden_table" or p.startswith(("table_", "pedestal_table_", "shelf_table_", "writing_desk_"))
chairs = lambda p: p in ("wooden_chair", "modern_chair", "office_chair", "bar_stool") or p.startswith(("chair_", "sofa_"))
beds = lambda p: p.startswith(("bed_", "dog_bed_"))
lights = lambda p: (p in ("ceiling_light", "ceiling_light_round", "ceiling_light_square", "ceiling_bulb", "round_bulb",
                          "circular_bulb", "office_led_panel", "wall_light", "floor_lamp", "night_lamp", "desk_lamp",
                          "desk_lamp_flexo", "street_lamp", "wall_switch")
                    or p.startswith(("neon_bar_", "bugerati_")))
kitchen = lambda p: (p in ("kitchen_counter", "stove", "kitchen_oven", "microwave", "dishwasher", "washing_machine",
                           "coffee_maker", "orange_juicer", "freezer_chest", "cold_room", "glass_red", "glass_blue",
                           "glass_green", "glass_yellow")
                     or p.startswith(("kitchen_sink_", "kitchen_drawers_", "stove_", "microwave_", "crate_", "dish_rack_", "frying_pan_",
                                      "pan_lid_", "cutlery_")))
before(decor_sorted, tables, chairs, "las mesas van antes que las sillas y sofás")
before(decor_sorted, chairs, beds, "las sillas y sofás van antes que las camas")
before(decor_sorted, beds, lights, "las camas van antes que las luces")
before(decor_sorted, lights, kitchen, "las luces van antes que la cocina")
k = positions(decor_sorted, kitchen)
if k and k != list(range(len(decor_sorted) - len(k), len(decor_sorted))):
    errors.append("la cocina no es lo último de la pestaña de decoración")
if positions(decor_sorted, tables) != list(range(len(positions(decor_sorted, tables)))):
    errors.append("las mesas no son lo primero de la pestaña de decoración")
# camas y camas para perros juntas
b = positions(decor_sorted, lambda p: p.startswith(("bed_", "dog_bed_")))
if b != list(range(b[0], b[0] + len(b))):
    errors.append("las camas y camas para perros no quedaron juntas")

# --- pestaña 2: HS < carros < ventiladores < (extras) < pantallas y consolas
hs = lambda p: p in ("hs_laptop", "hs_desktop", "printer") or p.startswith("hs_desktop_")   # V2.3.0: 6 modelos más
cars = lambda p: p.startswith("mini_")
fans = lambda p: p.startswith(("fan_", "ceiling_fan_", "pedestal_fan_"))   # V2.3.0: ventiladores de pie
screens = lambda p: p in ("television", "wii_console", "nintendo_switch", "kinect_sensor") or p.startswith(("tv_", "xbox_"))
before(tech_sorted, hs, cars, "la marca HS va antes que los carros")
before(tech_sorted, cars, fans, "los carros van antes que los ventiladores")
before(tech_sorted, fans, screens, "los ventiladores van antes que las pantallas y consolas")
if positions(tech_sorted, hs) != list(range(len(positions(tech_sorted, hs)))) or not positions(tech_sorted, hs):
    errors.append("la marca HS no es lo primero de la pestaña de tecnología")
s = positions(tech_sorted, screens)
if s != list(range(len(tech_sorted) - len(s), len(tech_sorted))):
    errors.append("las pantallas y consolas no son lo último de la pestaña de tecnología")

if not re.search(r"version = '(1\.8\.([4-9]|\d{2,})|[2-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")):
    errors.append("build.gradle no está en 1.8.4 o superior")
if not re.search(r"mod_version=(1\.8\.([4-9]|\d{2,})|[2-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")):
    errors.append("gradle.properties no está en 1.8.4 o superior")

if "--print" in sys.argv:
    print("PESTAÑA DECORACIÓN:\n  " + ", ".join(decor_sorted))
    print("PESTAÑA TECNOLOGÍA:\n  " + ", ".join(tech_sorted))

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"decoración: {len(decor_sorted)} ítems ordenados; tecnología: {len(tech_sorted)} ítems ordenados")
print("OK")
