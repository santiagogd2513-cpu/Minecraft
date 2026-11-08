"""Lámparas Bugerati: de pie (deco), de arco, de mesa (jarrón), esfera, colgante y aplique de pared."""
from .common import *


def mats(m, on):
    return dict(
        gold=m.tex("gold", "bg_gold"), silver=m.tex("silver", "bg_silver"), black=m.tex("black", "bg_black"),
        marble=m.tex("marble", "bg_marble"),
        shade=m.tex("shade", "bg_shade_on" if on else "bg_shade_off"),
        globe=m.tex("globe", "bg_globe_on" if on else "bg_globe_off"),
        bulb=m.tex("bulb", "bg_globe_on" if on else "bg_bulb_off"),
        badge=m.tex("badge", "bg_badge"))


def octa(m, frm, to, tex, **faces):
    """Tambor de pantalla (caja simple; antes se giraba una copia, pero formaba una estrella)."""
    m.box(frm, to, tex, **faces)


def floor_lamp(on):
    m = Model()
    t = mats(m, on)
    m.box([4, 0, 4], [12, 1, 12], t["marble"])
    m.box([5, 1, 5], [11, 2, 11], t["gold"])
    m.box([6.2, 0.1, 3.86], [9.8, 0.9, 4], t["badge"], north=(t["badge"], [0, 0, 16, 16]),
          south=None, east=None, west=None, up=None, down=None)
    m.box([7.2, 2, 7.2], [8.8, 10.5, 8.8], t["gold"])
    for y in (4.2, 7.2):
        m.box([6.6, y, 6.6], [9.4, y + 0.8, 9.4], t["gold"])
    octa(m, [4.4, 10.4, 4.4], [11.6, 10.8, 11.6], t["gold"])
    octa(m, [4.4, 10.8, 4.4], [11.6, 15.2, 11.6], t["shade"], up=None, down=t["bulb"])
    octa(m, [4.4, 15.2, 4.4], [11.6, 15.6, 11.6], t["gold"])
    m.box([6.5, 11.2, 6.5], [9.5, 14.6, 9.5], t["bulb"], up=None, down=None)
    m.save(f"bugerati_floor_lamp{'_on' if on else ''}", "bg_gold")


def arc_lamp(on):
    m = Model()
    t = mats(m, on)
    m.box([3, 0, 3], [13, 1.4, 13], t["marble"])
    m.box([4, 1.4, 4], [12, 2.0, 12], t["silver"])
    m.box([7.3, 2, 7.3], [8.7, 11, 8.7], t["silver"])
    m.box([7.0, 5.5, 7.0], [9.0, 6.3, 9.0], t["silver"])
    # brazo en arco (escalones que suben y bajan)
    m.box([7.3, 10.4, 7.3], [10.2, 11.9, 8.7], t["silver"])
    m.box([9.2, 11.4, 7.3], [11.8, 12.9, 8.7], t["silver"])
    m.box([11.0, 12.1, 7.3], [13.4, 13.4, 8.7], t["silver"])
    m.box([12.4, 10.6, 7.5], [13.6, 12.6, 8.5], t["silver"])
    # pantalla en cúpula
    m.box([10.2, 7.6, 5.4], [15.8, 10.6, 10.6], t["silver"], up=t["silver"], down=t["bulb"])
    m.box([11.2, 10.6, 6.4], [14.8, 11.2, 9.6], t["silver"])
    m.box([11.6, 8.4, 6.6], [14.4, 9.8, 9.4], t["bulb"], up=None, down=None)
    m.box([5, 0.2, 2.86], [8, 1.2, 3], t["badge"], north=(t["badge"], [0, 0, 16, 16]),
          south=None, east=None, west=None, up=None, down=None)
    m.save(f"bugerati_arc_lamp{'_on' if on else ''}", "bg_silver")


def table_lamp(on):
    m = Model()
    t = mats(m, on)
    m.box([4.5, 0, 4.5], [11.5, 0.8, 11.5], t["gold"])
    m.box([5, 0.8, 5], [11, 6, 11], t["black"])
    m.box([4.8, 3, 4.8], [11.2, 3.8, 11.2], t["gold"])
    m.box([5.8, 6, 5.8], [10.2, 7.5, 10.2], t["black"])
    m.box([7, 7.5, 7], [9, 9.2, 9], t["gold"])
    m.box([6.8, 1.0, 4.86], [9.2, 2.6, 5], t["badge"], north=(t["badge"], [0, 0, 16, 16]),
          south=None, east=None, west=None, up=None, down=None)
    octa(m, [4.2, 9.0, 4.2], [11.8, 10.2, 11.8], t["shade"], up=None, down=t["bulb"])
    octa(m, [4.6, 10.2, 4.6], [11.4, 11.6, 11.4], t["shade"], up=None, down=None)
    octa(m, [5.1, 11.6, 5.1], [10.9, 12.9, 10.9], t["shade"], up=None, down=None)
    octa(m, [5.6, 12.9, 5.6], [10.4, 14.0, 10.4], t["shade"], up=t["gold"], down=None)
    m.box([7.5, 14.0, 7.5], [8.5, 15.6, 8.5], t["gold"])
    m.box([7.1, 10.0, 7.1], [8.9, 12.0, 8.9], t["bulb"], up=None, down=None)
    m.save(f"bugerati_table_lamp{'_on' if on else ''}", "bg_gold")


def sphere(m, tex, c, r):
    """Esfera aproximada: tres cajas cruzadas (un cubo con las aristas biseladas)."""
    cx, cy, cz = c
    k = r * 0.74
    full = {f: [0, 0, 16, 16] for f in FACE_NAMES}
    m.box([cx - r, cy - k, cz - k], [cx + r, cy + k, cz + k], tex, uv=full)
    m.box([cx - k, cy - r, cz - k], [cx + k, cy + r, cz + k], tex, uv=full)
    m.box([cx - k, cy - k, cz - r], [cx + k, cy + k, cz + r], tex, uv=full)


def orb_lamp(on):
    m = Model()
    t = mats(m, on)
    m.box([4.5, 0, 4.5], [11.5, 1, 11.5], t["gold"])
    m.box([5.5, 1, 5.5], [10.5, 1.8, 10.5], t["black"])
    m.box([6.8, 1.8, 6.8], [9.2, 3.6, 9.2], t["gold"])
    sphere(m, t["globe"], (8, 7.6, 8), 3.4)
    m.box([6.4, 11.3, 6.4], [9.6, 12.1, 9.6], t["gold"])
    m.box([7.3, 12.1, 7.3], [8.7, 13.3, 8.7], t["gold"])
    m.box([6.2, 0.2, 4.36], [9.8, 0.9, 4.5], t["badge"], north=(t["badge"], [0, 0, 16, 16]),
          south=None, east=None, west=None, up=None, down=None)
    m.save(f"bugerati_orb_lamp{'_on' if on else ''}", "bg_gold")


def pendant_lamp(on):
    m = Model()
    t = mats(m, on)
    m.box([5.4, 15.2, 5.4], [10.6, 16, 10.6], t["gold"])
    m.box([7.6, 10.2, 7.6], [8.4, 15.2, 8.4], t["black"])
    m.box([6.4, 9.4, 6.4], [9.6, 10.4, 9.6], t["gold"])
    sphere(m, t["globe"], (8, 5.8, 8), 3.5)
    m.box([7, 1.6, 7], [9, 2.4, 9], t["gold"])
    m.box([7.5, 1.0, 7.5], [8.5, 1.6, 8.5], t["gold"])
    m.save(f"bugerati_pendant_lamp{'_on' if on else ''}", "bg_gold")


def wall_sconce(on):
    """Mirando al norte: la pared queda hacia +z."""
    m = Model()
    t = mats(m, on)
    m.box([5.6, 3.2, 14.3], [10.4, 12.8, 16], t["gold"])
    m.box([6.3, 4.0, 14.0], [9.7, 5.4, 14.3], t["badge"], north=(t["badge"], [0, 0, 16, 16]),
          south=None, east=None, west=None, up=None, down=None)
    m.box([7.3, 6.4, 12.4], [8.7, 7.6, 14.3], t["gold"])
    m.box([7.3, 7.0, 10.8], [8.7, 8.2, 12.6], t["gold"])
    m.box([6.5, 8.0, 9.4], [9.5, 8.8, 12.4], t["gold"])
    octa(m, [6.4, 8.8, 9.6], [9.6, 11.6, 12.2], t["globe"], up=None) if False else None
    sphere(m, t["globe"], (8, 10.4, 11), 1.9)
    m.box([6.9, 12.0, 10.1], [9.1, 12.6, 11.9], t["gold"])
    m.save(f"bugerati_wall_sconce{'_on' if on else ''}", "bg_gold")


LAMPS = {
    "bugerati_floor_lamp": (floor_lamp, "Bugerati Deco Floor Lamp", "Lámpara de pie Bugerati Deco"),
    "bugerati_arc_lamp": (arc_lamp, "Bugerati Arc Lamp", "Lámpara de arco Bugerati"),
    "bugerati_table_lamp": (table_lamp, "Bugerati Vase Table Lamp", "Lámpara de mesa Bugerati (jarrón)"),
    "bugerati_orb_lamp": (orb_lamp, "Bugerati Orb Lamp", "Lámpara esfera Bugerati"),
    "bugerati_pendant_lamp": (pendant_lamp, "Bugerati Pendant Lamp", "Lámpara colgante Bugerati"),
    "bugerati_wall_sconce": (wall_sconce, "Bugerati Wall Sconce", "Aplique de pared Bugerati"),
}


def generate():
    for bid, (fn, en, es) in LAMPS.items():
        fn(False)
        fn(True)
        facing_state(bid, lambda e, b=bid: b + ("_on" if e["open"] == "true" else ""),
                     [{"open": "false"}, {"open": "true"}])
        write_item_model(bid)
        names(bid, en, es)


if __name__ == "__main__":
    generate()
