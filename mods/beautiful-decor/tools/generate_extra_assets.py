#!/usr/bin/env python3
"""Generate block models, states, and color textures for the new collection."""

import json
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
BLOCK_MODELS = ASSETS / "models/block"
ITEM_MODELS = ASSETS / "models/item"
BLOCK_STATES = ASSETS / "blockstates"
BLOCK_TEXTURES = ASSETS / "textures/block"

FACING_ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "south", "east", "west", "up", "down")

NEON_COLORS = {
    "white": "#F9FFFE",
    "orange": "#F9801D",
    "magenta": "#C74EBD",
    "light_blue": "#3AB3DA",
    "yellow": "#FED83D",
    "lime": "#80C71F",
    "pink": "#F38BAA",
    "gray": "#474F52",
    "light_gray": "#9D9D97",
    "cyan": "#169C9C",
    "purple": "#8932B8",
    "blue": "#3C44AA",
    "brown": "#835432",
    "green": "#5E7C16",
    "red": "#B02E26",
    "black": "#1D1D21",
}

SPANISH_COLORS = {
    "white": "blanca",
    "orange": "naranja",
    "magenta": "magenta",
    "light_blue": "celeste",
    "yellow": "amarilla",
    "lime": "lima",
    "pink": "rosa",
    "gray": "gris",
    "light_gray": "gris claro",
    "cyan": "cian",
    "purple": "morada",
    "blue": "azul",
    "brown": "marrón",
    "green": "verde",
    "red": "roja",
    "black": "negra",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def cube(start, end, texture, uv=None):
    faces = {}
    for side in FACES:
        face = {"texture": texture}
        if uv is not None:
            face["uv"] = uv
        faces[side] = face
    return {"from": start, "to": end, "faces": faces}


def model(textures, elements):
    return {
        "parent": "block/block",
        "textures": textures,
        "elements": elements,
    }


def write_model(name, textures, elements):
    write_json(BLOCK_MODELS / f"{name}.json", model(textures, elements))


def write_item_model(name):
    write_json(ITEM_MODELS / f"{name}.json", {"parent": f"beautifuldecor:block/{name}"})


def write_facing_state(name, model_name=None):
    model_name = model_name or name
    variants = {}
    for facing, rotation in FACING_ROTATION.items():
        variant = {"model": f"beautifuldecor:block/{model_name}"}
        if rotation:
            variant["y"] = rotation
        variants[f"facing={facing}"] = variant
    write_json(BLOCK_STATES / f"{name}.json", {"variants": variants})


def color_toward_white(hex_color, strength):
    rgb = tuple(int(hex_color[index:index + 2], 16) for index in (1, 3, 5))
    return tuple(round(channel + (255 - channel) * strength) for channel in rgb)


def png_chunk(kind, payload):
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_neon_texture(name, hex_color):
    color = tuple(int(hex_color[index:index + 2], 16) for index in (1, 3, 5))
    bright = color_toward_white(hex_color, 0.74)
    mid = color_toward_white(hex_color, 0.34)
    dark = tuple(round(channel * 0.28) for channel in color)
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = (10, 12, 18)
            if 4 <= x <= 11 and 0 <= y <= 15:
                shade = (18, 20, 28)
            if 5 <= x <= 10 and 1 <= y <= 14:
                shade = dark
            if 6 <= x <= 9 and 1 <= y <= 14:
                shade = mid
            if 7 <= x <= 8 and 2 <= y <= 13:
                shade = bright
            if y in (1, 14) and 6 <= x <= 9:
                shade = color
            row.append((*shade, 255))
        pixels.append(row)

    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    header = struct.pack(">2I5B", 16, 16, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header)
    png += png_chunk(b"IDAT", zlib.compress(raw, level=9)) + png_chunk(b"IEND", b"")
    BLOCK_TEXTURES.mkdir(parents=True, exist_ok=True)
    (BLOCK_TEXTURES / f"{name}.png").write_bytes(png)


def write_rgba_texture(name, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(
        b"\x00" + bytes(channel for pixel in row for channel in pixel)
        for row in pixels
    )
    header = struct.pack(">2I5B", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header)
    png += png_chunk(b"IDAT", zlib.compress(raw, level=9)) + png_chunk(b"IEND", b"")
    BLOCK_TEXTURES.mkdir(parents=True, exist_ok=True)
    (BLOCK_TEXTURES / f"{name}.png").write_bytes(png)


def write_xbox_one_textures():
    shell = []
    front = []
    edge = []
    slot = []
    power = []

    for y in range(16):
        shell_row = []
        front_row = []
        edge_row = []
        slot_row = []
        power_row = []
        for x in range(16):
            shell_shade = 28 + ((x + y) % 3) * 2
            if x in (0, 15) or y in (0, 15):
                shell_color = (15, 17, 21, 255)
            elif x in (1, 14) or y in (1, 14):
                shell_color = (45, 48, 54, 255)
            else:
                shell_color = (shell_shade, shell_shade + 2, shell_shade + 7, 255)
            shell_row.append(shell_color)

            if y in (0, 15):
                front_color = (12, 14, 18, 255)
            elif y in (1, 14):
                front_color = (50, 53, 59, 255)
            elif x in (0, 15):
                front_color = (25, 27, 32, 255)
            else:
                front_color = (32 + ((x + y) % 2) * 2, 34 + ((x + y) % 2) * 2, 40, 255)
            if x >= 10 and y in (4, 6, 8, 10, 12):
                front_color = (68, 71, 77, 255)
            front_row.append(front_color)

            edge_value = 74 if y < 3 else (60 if y < 12 else 42)
            if x in (0, 15):
                edge_value -= 14
            edge_row.append((edge_value, edge_value + 2, edge_value + 7, 255))

            if y in (6, 9):
                slot_color = (95, 99, 106, 255)
            elif y in (7, 8):
                slot_color = (8, 9, 12, 255)
            else:
                slot_color = (29, 31, 36, 255)
            slot_row.append(slot_color)

            dx = x - 7.5
            dy = y - 7.5
            radius_squared = dx * dx + dy * dy
            if 18 <= radius_squared <= 31 or (x in (7, 8) and 2 <= y <= 7):
                power_color = (87, 232, 94, 255)
            elif radius_squared < 38:
                power_color = (25, 73, 31, 255)
            else:
                power_color = (25, 28, 33, 255)
            power_row.append(power_color)

        shell.append(shell_row)
        front.append(front_row)
        edge.append(edge_row)
        slot.append(slot_row)
        power.append(power_row)

    write_rgba_texture("xbox_one_shell", shell)
    write_rgba_texture("xbox_one_front", front)
    write_rgba_texture("xbox_one_edge", edge)
    write_rgba_texture("xbox_one_slot", slot)
    write_rgba_texture("xbox_one_power", power)


def write_sofa_variants(color):
    base_path = BLOCK_MODELS / f"sofa_{color}.json"
    base = json.loads(base_path.read_text(encoding="utf-8"))
    original_elements = base["elements"]

    for left_connected, right_connected in ((True, False), (False, True), (True, True)):
        filtered = []
        for element in original_elements:
            x0, _, _ = element["from"]
            x1, _, _ = element["to"]
            is_left_arm = x0 == 0 and x1 == 2
            is_right_arm = x0 == 14 and x1 == 16
            if left_connected and is_left_arm:
                continue
            if right_connected and is_right_arm:
                continue
            filtered.append(element)

        suffix = (
            "both_connected" if left_connected and right_connected
            else "left_connected" if left_connected
            else "right_connected"
        )
        variant_model = dict(base)
        variant_model["elements"] = filtered
        write_json(BLOCK_MODELS / f"sofa_{color}_{suffix}.json", variant_model)

    variants = {}
    for facing, rotation in FACING_ROTATION.items():
        for left_connected in (False, True):
            for right_connected in (False, True):
                suffix = (
                    "both_connected" if left_connected and right_connected
                    else "left_connected" if left_connected
                    else "right_connected" if right_connected
                    else ""
                )
                model_name = f"sofa_{color}" + (f"_{suffix}" if suffix else "")
                variant = {"model": f"beautifuldecor:block/{model_name}"}
                if rotation:
                    variant["y"] = rotation
                variants[
                    f"facing={facing},left_connected={str(left_connected).lower()},"
                    f"right_connected={str(right_connected).lower()}"
                ] = variant
    write_json(BLOCK_STATES / f"sofa_{color}.json", {"variants": variants})


def new_device_assets():
    write_model(
        "mini_f150",
        {
            "particle": "minecraft:block/light_blue_concrete",
            "body": "minecraft:block/light_blue_concrete",
            "dark_body": "minecraft:block/blue_concrete",
            "glass": "minecraft:block/light_blue_stained_glass",
            "tire": "minecraft:block/black_concrete",
            "rim": "minecraft:block/iron_block",
            "grille": "minecraft:block/gray_concrete",
            "lamp": "minecraft:block/glowstone",
            "tail_lamp": "minecraft:block/redstone_block",
        },
        [
            cube([2, 1, 1], [14, 4, 15], "#dark_body"),
            cube([2, 2, 2], [14, 6, 14], "#body"),
            cube([2, 4, 1], [14, 7, 6], "#body"),
            cube([2, 4, 10], [14, 7, 15], "#body"),
            cube([3, 5, 6], [13, 10, 11], "#body"),
            cube([3, 9, 6], [13, 11, 10], "#body"),
            cube([4, 6, 5], [12, 9, 6], "#glass"),
            cube([4, 6, 10], [12, 9, 11], "#glass"),
            cube([3, 6, 7], [4, 9, 9], "#glass"),
            cube([12, 6, 7], [13, 9, 9], "#glass"),
            cube([4, 5, 10], [12, 6, 14], "#grille"),
            cube([5, 5, 0], [11, 6, 1], "#grille"),
            cube([2, 4, 0], [4, 6, 1], "#lamp"),
            cube([12, 4, 0], [14, 6, 1], "#lamp"),
            cube([1, 1, 0], [15, 2, 1], "#rim"),
            cube([1, 1, 15], [15, 2, 16], "#rim"),
            cube([0, 1, 3], [3, 5, 6], "#tire"),
            cube([13, 1, 3], [16, 5, 6], "#tire"),
            cube([0, 1, 11], [3, 5, 14], "#tire"),
            cube([13, 1, 11], [16, 5, 14], "#tire"),
            cube([0, 2, 4], [3, 4, 5], "#rim"),
            cube([13, 2, 4], [16, 4, 5], "#rim"),
            cube([0, 2, 12], [3, 4, 13], "#rim"),
            cube([13, 2, 12], [16, 4, 13], "#rim"),
            cube([2, 4, 14], [4, 6, 15], "#tail_lamp"),
            cube([12, 4, 14], [14, 6, 15], "#tail_lamp"),
        ],
    )

    write_model(
        "mini_mustang",
        {
            "particle": "minecraft:block/red_concrete",
            "body": "minecraft:block/red_concrete",
            "dark_body": "minecraft:block/black_concrete",
            "glass": "minecraft:block/light_blue_stained_glass",
            "tire": "minecraft:block/black_concrete",
            "rim": "minecraft:block/iron_block",
            "grille": "minecraft:block/gray_concrete",
            "lamp": "minecraft:block/glowstone",
            "stripe": "minecraft:block/white_concrete",
        },
        [
            cube([2, 1, 1], [14, 4, 15], "#dark_body"),
            cube([2, 2, 1], [14, 5, 15], "#body"),
            cube([2, 4, 1], [14, 6, 6], "#body"),
            cube([2, 3, 12], [14, 5, 15], "#body"),
            cube([4, 4, 5], [12, 8, 11], "#body"),
            cube([4, 7, 6], [12, 9, 10], "#body"),
            cube([5, 5, 4], [11, 7, 5], "#glass"),
            cube([5, 5, 11], [11, 7, 12], "#glass"),
            cube([3, 5, 6], [4, 7, 10], "#glass"),
            cube([12, 5, 6], [13, 7, 10], "#glass"),
            cube([5, 6, 1], [6, 6.35, 4], "#stripe"),
            cube([10, 6, 1], [11, 6.35, 4], "#stripe"),
            cube([4, 4, 0], [12, 5, 1], "#grille"),
            cube([2, 4, 0], [4, 5, 1], "#lamp"),
            cube([12, 4, 0], [14, 5, 1], "#lamp"),
            cube([3, 6, 14], [13, 7, 16], "#dark_body"),
            cube([1, 1, 0], [15, 2, 1], "#rim"),
            cube([1, 1, 15], [15, 2, 16], "#rim"),
            cube([0, 1, 3], [3, 5, 6], "#tire"),
            cube([13, 1, 3], [16, 5, 6], "#tire"),
            cube([0, 1, 11], [3, 5, 14], "#tire"),
            cube([13, 1, 11], [16, 5, 14], "#tire"),
            cube([0, 2, 4], [3, 4, 5], "#rim"),
            cube([13, 2, 4], [16, 4, 5], "#rim"),
            cube([0, 2, 12], [3, 4, 13], "#rim"),
            cube([13, 2, 12], [16, 4, 13], "#rim"),
        ],
    )

    write_model(
        "xbox_one",
        {
            "particle": "minecraft:block/black_concrete",
            "case": "minecraft:block/black_concrete",
            "edge": "minecraft:block/gray_concrete",
            "slot": "minecraft:block/light_gray_concrete",
            "button": "minecraft:block/green_concrete",
        },
        [
            cube([1, 1, 1], [15, 5, 15], "#case"),
            cube([1, 5, 1], [15, 6, 15], "#edge"),
            cube([3, 5.5, 1], [13, 5.8, 15], "#case"),
            cube([5, 2, 0], [11, 2.5, 1], "#slot"),
            cube([2, 2, 0], [4, 4, 1], "#edge"),
            cube([12, 2, 0], [14, 4, 1], "#edge"),
            cube([2, 4, 0], [3, 5, 1], "#button"),
            cube([13, 4, 0], [14, 5, 1], "#slot"),
            cube([2, 1, 14], [14, 2, 15], "#edge"),
        ],
    )

    write_model(
        "kinect_sensor",
        {
            "particle": "minecraft:block/black_concrete",
            "case": "minecraft:block/black_concrete",
            "trim": "minecraft:block/gray_concrete",
            "red_lens": "minecraft:block/red_stained_glass",
            "green_lens": "minecraft:block/green_stained_glass",
        },
        [
            cube([1, 5, 4], [15, 11, 12], "#case"),
            cube([2, 4, 5], [14, 5, 11], "#trim"),
            cube([2, 11, 5], [14, 12, 11], "#trim"),
            cube([3, 6, 3], [6, 10, 4], "#red_lens"),
            cube([7, 6, 3], [10, 10, 4], "#case"),
            cube([11, 6, 3], [14, 10, 4], "#green_lens"),
            cube([5, 2, 6], [11, 4, 10], "#trim"),
            cube([6, 0, 7], [10, 2, 9], "#case"),
        ],
    )

    chair_textures = {
        "particle": "minecraft:block/blue_concrete",
        "blue": "minecraft:block/blue_concrete",
        "dark": "minecraft:block/black_concrete",
        "metal": "minecraft:block/iron_block",
        "wheel": "minecraft:block/gray_concrete",
    }
    chair_parts = [
        cube([7, 2, 7], [9, 7, 9], "#metal"),
        cube([3, 6, 3], [13, 8, 12], "#dark"),
        cube([4, 7, 4], [12, 9, 11], "#blue"),
        cube([4, 9, 11], [12, 14, 14], "#blue"),
        cube([4, 10, 10], [12, 11, 11], "#metal"),
        cube([3, 8, 4], [4, 10, 10], "#metal"),
        cube([12, 8, 4], [13, 10, 10], "#metal"),
        cube([2, 10, 3], [5, 11, 10], "#blue"),
        cube([11, 10, 3], [14, 11, 10], "#blue"),
        cube([1, 1, 7], [8, 2, 9], "#metal"),
        cube([8, 1, 7], [15, 2, 9], "#metal"),
        cube([7, 1, 1], [9, 2, 8], "#metal"),
        cube([7, 1, 8], [9, 2, 15], "#metal"),
        cube([0, 0, 6], [3, 2, 10], "#wheel"),
        cube([13, 0, 6], [16, 2, 10], "#wheel"),
        cube([6, 0, 0], [10, 2, 3], "#wheel"),
        cube([6, 0, 13], [10, 2, 16], "#wheel"),
    ]
    write_model("office_chair", chair_textures, chair_parts)

    write_laptop_models()


def write_laptop_models():
    textures = {
        "particle": "minecraft:block/blue_concrete",
        "blue": "minecraft:block/blue_concrete",
        "blue_edge": "minecraft:block/light_blue_concrete",
        "keyboard": "minecraft:block/black_concrete",
        "key": "minecraft:block/light_gray_concrete",
        "screen": "minecraft:block/black_concrete",
        "logo": "minecraft:block/white_concrete",
        "trackpad": "minecraft:block/gray_concrete",
        "metal": "minecraft:block/iron_block",
    }

    base = [
        cube([1, 1, 1], [15, 3, 15], "#blue"),
        cube([2, 3, 2], [14, 3.35, 12], "#keyboard"),
        cube([6, 3.35, 10], [10, 3.4, 12], "#trackpad"),
    ]
    for row, z in enumerate((3.5, 5.0, 6.5, 8.0)):
        key_count = 8 if row < 3 else 6
        for index in range(key_count):
            x = 3.0 + index * 1.4
            base.append(cube([x, 3.35, z], [x + 0.85, 3.52, z + 0.85], "#key"))
    base.extend(
        [
            cube([2, 2.8, 13], [14, 3.2, 14], "#blue_edge"),
            cube([2, 3, 13], [14, 3.2, 15], "#metal"),
        ]
    )

    closed = list(base)
    closed.append(cube([1, 3.45, 1], [15, 4.1, 15], "#blue"))
    # Small HS mark on the closed lid.
    closed.extend(
        [
            cube([6, 4.1, 6], [6.8, 4.25, 10], "#logo"),
            cube([6, 4.1, 7.6], [8.2, 4.25, 8.3], "#logo"),
            cube([7.5, 4.1, 6], [8.3, 4.25, 10], "#logo"),
            cube([9, 4.1, 6], [11, 4.25, 6.8], "#logo"),
            cube([9, 4.1, 7.6], [11, 4.25, 8.3], "#logo"),
            cube([9, 4.1, 9.2], [11, 4.25, 10], "#logo"),
            cube([9, 4.1, 6], [9.8, 4.25, 8.3], "#logo"),
            cube([10.2, 4.1, 7.6], [11, 4.25, 10], "#logo"),
        ]
    )
    write_model("hs_laptop_closed", textures, closed)

    opened = list(base)
    opened.extend(
        [
            cube([1, 3, 13], [15, 14, 15], "#blue"),
            cube([2, 4, 12.8], [14, 13, 13.1], "#screen", [0, 0, 16, 16]),
            # HS monogram in the centre of the display.
            cube([6, 7, 12.7], [6.8, 10, 12.8], "#logo"),
            cube([6, 8, 12.7], [8.2, 8.7, 12.8], "#logo"),
            cube([7.5, 7, 12.7], [8.2, 10, 12.8], "#logo"),
            cube([9, 9.3, 12.7], [11, 10, 12.8], "#logo"),
            cube([9, 8.1, 12.7], [11, 8.8, 12.8], "#logo"),
            cube([9, 7, 12.7], [11, 7.7, 12.8], "#logo"),
            cube([9, 8.1, 12.7], [9.8, 10, 12.8], "#logo"),
            cube([10.2, 7, 12.7], [11, 8.8, 12.8], "#logo"),
        ]
    )
    write_model("hs_laptop_open", textures, opened)

    variants = {}
    for facing, rotation in FACING_ROTATION.items():
        for is_open in (False, True):
            variant = {
                "model": "beautifuldecor:block/hs_laptop_open" if is_open
                else "beautifuldecor:block/hs_laptop_closed"
            }
            if rotation:
                variant["y"] = rotation
            variants[f"facing={facing},open={str(is_open).lower()}"] = variant
    write_json(BLOCK_STATES / "hs_laptop.json", {"variants": variants})
    write_json(
        ITEM_MODELS / "hs_laptop.json",
        {"parent": "beautifuldecor:block/hs_laptop_closed"},
    )


def write_all_assets():
    BLOCK_MODELS.mkdir(parents=True, exist_ok=True)
    ITEM_MODELS.mkdir(parents=True, exist_ok=True)
    BLOCK_STATES.mkdir(parents=True, exist_ok=True)

    for color in ("red", "blue"):
        write_sofa_variants(color)

    write_xbox_one_textures()
    new_device_assets()

    for name in ("mini_f150", "mini_mustang", "xbox_one", "kinect_sensor", "office_chair"):
        write_facing_state(name)
        write_item_model(name)

    translations_es = {
        "mini_f150": "Ford F-150 miniatura de colección",
        "mini_mustang": "Ford Mustang miniatura de colección",
        "xbox_one": "Consola Xbox One",
        "kinect_sensor": "Sensor Kinect",
        "hs_laptop": "Portátil azul HS",
        "office_chair": "Silla de oficina giratoria",
    }
    translations_en = {
        "mini_f150": "Collectible Mini Ford F-150",
        "mini_mustang": "Collectible Mini Ford Mustang",
        "xbox_one": "Xbox One Console",
        "kinect_sensor": "Kinect Sensor",
        "hs_laptop": "HS Blue Laptop",
        "office_chair": "Swivel Office Chair",
    }

    for color, hex_color in NEON_COLORS.items():
        name = f"neon_bar_{color}"
        write_neon_texture(name, hex_color)
        write_model(
            name,
            {
                "particle": f"beautifuldecor:block/{name}",
                "tube": f"beautifuldecor:block/{name}",
                "frame": "minecraft:block/iron_block",
            },
            [
                cube([3, 0, 14], [13, 16, 16], "#frame"),
                cube([5, 1, 11], [11, 15, 14], "#tube", [0, 0, 16, 16]),
                cube([7, 2, 13], [9, 4, 14], "#frame"),
                cube([7, 12, 13], [9, 14, 14], "#frame"),
            ],
        )
        write_facing_state(name)
        write_item_model(name)
        spanish_name = f"Barra de neón {SPANISH_COLORS[color]}"
        translations_es[name] = spanish_name
        translations_en[name] = f"{color.replace('_', ' ').title()} Neon Bar"

    for locale, translations in (("es_es", translations_es), ("en_us", translations_en)):
        lang_path = ASSETS / f"lang/{locale}.json"
        current = json.loads(lang_path.read_text(encoding="utf-8"))
        for name, label in translations.items():
            current[f"block.beautifuldecor.{name}"] = label
            current[f"item.beautifuldecor.{name}"] = label
        write_json(lang_path, current)


if __name__ == "__main__":
    write_all_assets()
    print("Generated sofa connections, collectible models, laptop states, and 16 neon colors.")
