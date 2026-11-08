"""Candelabro colgante Bugerati (dorado, plateado y negro): se enciende y apaga con clic derecho."""
import math

from .common import *

FINISHES = {
    "gold": ("bg_gold", "Gold", "dorado"),
    "silver": ("bg_silver", "Silver", "plateado"),
    "black": ("bg_black", "Black", "negro"),
}


class Chandelier:
    def __init__(self, finish, on):
        self.m = Model()
        self.metal = self.m.tex("metal", FINISHES[finish][0])
        self.crystal = self.m.tex("crystal", "bg_crystal")
        self.candle = self.m.tex("candle", "bg_candle")
        self.flame = self.m.tex("flame", "bg_flame")
        self.wick = self.m.tex("wick", "bg_wick")
        self.chain = self.m.tex("chain", "bg_chain")
        self.on = on

    def box(self, frm, to, tex, rot=None):
        self.m.box(frm, to, tex, rot=rot)

    def ring(self, y, a, th=0.6, h=0.5):
        """Aro octogonal de apotema a (cuatro lados rectos + cuatro girados 45°)."""
        half = a * math.tan(math.radians(22.5)) * 1.08
        c = 8.0
        zp = ([c - half, y, c + a - th], [c + half, y + h, c + a])
        zn = ([c - half, y, c - a], [c + half, y + h, c - a + th])
        xp = ([c + a - th, y, c - half], [c + a, y + h, c + half])
        xn = ([c - a, y, c - half], [c - a + th, y + h, c + half])
        for frm, to in (zp, zn, xp, xn):
            self.box(frm, to, self.metal)
        for frm, to in (zp, zn):
            for ang in (45, -45):
                self.box(frm, to, self.metal, rot={"origin": [c, y, c], "axis": "y", "angle": ang})

    def spokes(self, y, length, diagonals):
        c = 8.0
        self.box([c - length, y, c - 0.4], [c + length, y + 0.5, c + 0.4], self.metal)
        self.box([c - 0.4, y, c - length], [c + 0.4, y + 0.5, c + length], self.metal)
        if diagonals:
            for ang in (45, -45):
                self.box([c - length, y, c - 0.4], [c + length, y + 0.5, c + 0.4], self.metal,
                         rot={"origin": [c, y, c], "axis": "y", "angle": ang})

    def candle_at(self, x, y, z, h):
        self.box([x - 0.75, y, z - 0.75], [x + 0.75, y + 0.5, z + 0.75], self.metal)
        self.box([x - 0.45, y + 0.5, z - 0.45], [x + 0.45, y + 0.5 + h, z + 0.45], self.candle)
        top = y + 0.5 + h
        if self.on:
            self.box([x - 0.4, top, z - 0.4], [x + 0.4, top + 1.1, z + 0.4], self.flame)
        else:
            self.box([x - 0.12, top, z - 0.12], [x + 0.12, top + 0.45, z + 0.12], self.wick)

    def crystal_at(self, x, z):
        self.box([x - 0.45, 3.4, z - 0.45], [x + 0.45, 5.6, z + 0.45], self.crystal)
        self.box([x - 0.25, 2.8, z - 0.25], [x + 0.25, 3.4, z + 0.25], self.crystal)


def build(finish, on):
    ch = Chandelier(finish, on)
    c = 8.0
    ch.box([6, 15.2, 6], [10, 16, 10], ch.metal)
    ch.box([7.4, 11.4, 7.4], [8.6, 15.2, 8.6], ch.chain)
    ch.box([6.2, 10.4, 6.2], [9.8, 11.4, 9.8], ch.metal)
    ch.box([7.2, 5.2, 7.2], [8.8, 10.4, 8.8], ch.metal)
    ch.box([6.4, 7.6, 6.4], [9.6, 9.2, 9.6], ch.metal)
    # nivel alto: aro pequeño con 4 velas
    ch.ring(9.3, 3.6)
    ch.spokes(9.3, 3.6, False)
    for (dx, dz) in ((3.3, 0), (-3.3, 0), (0, 3.3), (0, -3.3)):
        ch.candle_at(c + dx, 9.8, c + dz, 1.8)
    # nivel bajo: aro grande con 8 velas y 8 cristales colgantes
    ch.ring(5.6, 6.1)
    ch.spokes(5.6, 6.1, True)
    for k in range(8):
        ang = math.radians(45 * k)
        ch.candle_at(c + 5.8 * math.cos(ang), 6.1, c + 5.8 * math.sin(ang), 2.4)
    r_vertex = 6.1 / math.cos(math.radians(22.5))
    for k in range(8):
        ang = math.radians(22.5 + 45 * k)
        ch.crystal_at(c + r_vertex * math.cos(ang), c + r_vertex * math.sin(ang))
    # remate inferior
    ch.box([7.4, 3.6, 7.4], [8.6, 5.2, 8.6], ch.metal)
    ch.box([6.8, 2.4, 6.8], [9.2, 3.8, 9.2], ch.metal)
    ch.box([7.5, 1.4, 7.5], [8.5, 2.4, 8.5], ch.crystal)
    return ch.m


def generate():
    for finish, (_, en, es) in FINISHES.items():
        bid = f"bugerati_chandelier_{finish}"
        build(finish, False).save(bid, FINISHES[finish][0])
        build(finish, True).save(bid + "_on", FINISHES[finish][0])
        facing_state(bid, lambda e, b=bid: b + ("_on" if e["open"] == "true" else ""),
                     [{"open": "false"}, {"open": "true"}])
        write_item_model(bid)
        names(bid, f"Bugerati Chandelier ({en})", f"Candelabro colgante Bugerati ({es})")


if __name__ == "__main__":
    generate()
