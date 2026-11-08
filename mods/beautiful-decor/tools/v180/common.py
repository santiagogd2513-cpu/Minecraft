"""Utilidades compartidas del paquete v1.8.0 (texturas, modelos, blockstates, idiomas, fuente 3x5)."""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
MODELS, ITEMS, STATES = ASSETS / "models/block", ASSETS / "models/item", ASSETS / "blockstates"
TEX, LOOT, LANG = ASSETS / "textures/block", DATA / "loot_tables/blocks", ASSETS / "lang"

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACE_NAMES = ("north", "south", "east", "west", "up", "down")

LANG_EN, LANG_ES = {}, {}
NEW_BLOCKS = []          # ids nuevos (para verificación y loot tables)


# ------------------------------------------------------------------ archivos
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def names(block_id, en, es):
    for kind in ("block", "item"):
        LANG_EN[f"{kind}.beautifuldecor.{block_id}"] = en
        LANG_ES[f"{kind}.beautifuldecor.{block_id}"] = es


def tex_ref(name):
    return f"beautifuldecor:block/{name}"


def save_tex(name, im):
    TEX.mkdir(parents=True, exist_ok=True)
    im.save(TEX / f"{name}.png")


def write_loot(block_id):
    write_json(LOOT / f"{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{block_id}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def write_loot_part0(block_id, prop="part"):
    """Loot de un bloque de varias piezas: solo la pieza 0 suelta el ítem."""
    write_json(LOOT / f"{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{block_id}"}],
                   "conditions": [
                       {"condition": "minecraft:block_state_property", "block": f"beautifuldecor:{block_id}",
                        "properties": {prop: "0"}},
                       {"condition": "minecraft:survives_explosion"}]}]})


def write_item_model(block_id, model=None):
    write_json(ITEMS / f"{block_id}.json", {"parent": f"beautifuldecor:block/{model or block_id}"})


def facing_state(block_id, model_for, extra_keys=None):
    """Blockstate con facing (y 0/90/180/270). model_for(extra) -> nombre de modelo.
    extra_keys: lista de dicts de propiedades adicionales, p.ej. [{"open": "false"}, {"open": "true"}]."""
    variants = {}
    for facing, rot in FACINGS.items():
        for extra in (extra_keys or [{}]):
            key = ",".join([f"facing={facing}"] + [f"{k}={v}" for k, v in extra.items()])
            v = {"model": f"beautifuldecor:block/{model_for(extra)}"}
            if rot:
                v["y"] = rot
            variants[key] = v
    write_json(STATES / f"{block_id}.json", {"variants": variants})


# ------------------------------------------------------------------ modelos
def default_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
        "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
        "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
        "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def rnd(v):
    v = round(v * 1000) / 1000
    return int(v) if v == int(v) else v


class Model:
    """Constructor de modelos de bloque. Los nombres de textura se registran con tex(clave, archivo)."""

    def __init__(self, render_type=None):
        self.textures = {}
        self.elements = []
        self.render_type = render_type

    def tex(self, key, name):
        self.textures[key] = tex_ref(name)
        return "#" + key

    def box(self, frm, to, default=None, rot=None, name=None, uv=None, **faces):
        """default = textura para todas las caras; faces = north=..., up=... con None (sin cara),
        '#clave' o ('#clave', [u0,v0,u1,v1]). uv = dict cara -> [u0,v0,u1,v1] para sobreescribir el uv."""
        out = {}
        for fn in FACE_NAMES:
            spec = faces.get(fn, default)
            if spec is None:
                continue
            face = {}
            if isinstance(spec, tuple):
                face["texture"] = spec[0]
                face["uv"] = [rnd(v) for v in spec[1]]
            else:
                face["texture"] = spec
                if uv and fn in uv:
                    face["uv"] = [rnd(v) for v in uv[fn]]
            out[fn] = face
        el = {"from": [rnd(v) for v in frm], "to": [rnd(v) for v in to], "faces": out}
        if name:
            el = {"name": name, **el}
        if rot:
            el["rotation"] = {"origin": [rnd(v) for v in rot["origin"]], "axis": rot["axis"], "angle": rot["angle"]}
            if rot.get("rescale"):
                el["rotation"]["rescale"] = True
        self.elements.append(el)
        return el

    def to_json(self, particle):
        data = {"parent": "minecraft:block/block"}
        if self.render_type:
            data["render_type"] = self.render_type
        textures = {"particle": tex_ref(particle) if "/" not in particle else particle}
        textures.update(self.textures)
        data["textures"] = textures
        data["elements"] = self.elements
        return data

    def save(self, name, particle):
        write_json(MODELS / f"{name}.json", self.to_json(particle))


# ------------------------------------------------------------------ pixeles
def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def new(w=16, h=None, color=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h or w), color)


def fill_noise(im, base, amp, seed, vgrad=0.0, box=None):
    rng = random.Random(seed)
    px = im.load()
    x0, y0, x1, y1 = box or (0, 0, im.width, im.height)
    for y in range(y0, y1):
        for x in range(x0, x1):
            d = rng.uniform(-amp, amp) - vgrad * ((y - y0) / max(1, y1 - y0 - 1))
            px[x, y] = shade(base, d)
    return im


def rect(im, x0, y0, x1, y1, color):
    """Rectángulo relleno con límites inclusivos (x1,y1 incluidos)."""
    px = im.load()
    for y in range(max(0, y0), min(im.height - 1, y1) + 1):
        for x in range(max(0, x0), min(im.width - 1, x1) + 1):
            px[x, y] = color


def frame(im, x0, y0, x1, y1, color):
    rect(im, x0, y0, x1, y0, color)
    rect(im, x0, y1, x1, y1, color)
    rect(im, x0, y0, x0, y1, color)
    rect(im, x1, y0, x1, y1, color)


def disc(im, cx, cy, r, color):
    px = im.load()
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if 0 <= x < im.width and 0 <= y < im.height and (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                px[x, y] = color


def line(im, x0, y0, x1, y1, color):
    ImageDraw.Draw(im).line([(x0, y0), (x1, y1)], fill=color)


# ------------------------------------------------------------------ fuente 3x5
FONT = {
    "A": "010 101 111 101 101", "B": "110 101 110 101 110", "C": "011 100 100 100 011",
    "D": "110 101 101 101 110", "E": "111 100 110 100 111", "F": "111 100 110 100 100",
    "G": "011 100 101 101 011", "H": "101 101 111 101 101", "I": "111 010 010 010 111",
    "J": "001 001 001 101 010", "K": "101 101 110 101 101", "L": "100 100 100 100 111",
    "M": "101 111 111 101 101", "N": "110 101 101 101 101", "O": "010 101 101 101 010",
    "P": "111 101 111 100 100", "Q": "010 101 101 111 011", "R": "110 101 110 101 101",
    "S": "111 100 111 001 111", "T": "111 010 010 010 010", "U": "101 101 101 101 111",
    "V": "101 101 101 101 010", "W": "101 101 111 111 101", "X": "101 101 010 101 101",
    "Y": "101 101 010 010 010", "Z": "111 001 010 100 111",
    "0": "111 101 101 101 111", "1": "010 110 010 010 111", "2": "110 001 010 100 111",
    "3": "110 001 010 001 110", "4": "101 101 111 001 001", "5": "111 100 110 001 110",
    "6": "011 100 111 101 111", "7": "111 001 010 010 010", "8": "111 101 111 101 111",
    "9": "111 101 111 001 110",
    "-": "000 000 111 000 000", ".": "000 000 000 000 010", ":": "000 010 000 010 000",
    "/": "001 001 010 100 100", " ": "000 000 000 000 000", "'": "010 010 000 000 000",
    "+": "000 010 111 010 000", "%": "101 001 010 100 101", "!": "010 010 010 000 010",
}


def text_width(s, scale=1, gap=1):
    return (len(s) * (3 + gap) - gap) * scale


def draw_text(im, s, x, y, color, scale=1, gap=1):
    px = im.load()
    cx = x
    for ch in s.upper():
        rows = FONT.get(ch, FONT[" "]).split()
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    for sy in range(scale):
                        for sx in range(scale):
                            xx, yy = cx + rx * scale + sx, y + ry * scale + sy
                            if 0 <= xx < im.width and 0 <= yy < im.height:
                                px[xx, yy] = color
        cx += (3 + gap) * scale


def draw_pattern(im, rows, x0, y0, color, scale=1):
    px = im.load()
    for ry, row in enumerate(rows):
        for rx, bit in enumerate(row):
            if bit not in "0 .":
                c = color if bit == "1" else bit
                for sy in range(scale):
                    for sx in range(scale):
                        xx, yy = x0 + rx * scale + sx, y0 + ry * scale + sy
                        if 0 <= xx < im.width and 0 <= yy < im.height:
                            px[xx, yy] = c
