#!/usr/bin/env python3
"""Create the Wii Family Edition, Xbox Series S, Xbox 360 and Switch textures."""

import math
import struct
import zlib
from pathlib import Path


TEXTURES = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/beautifuldecor/textures/block"
)
ITEM_TEXTURES = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/beautifuldecor/textures/item"
)


class Canvas:
    def __init__(self, width=64, height=64, color=(0, 0, 0, 255)):
        self.width = width
        self.height = height
        self.pixels = [[color for _ in range(width)] for _ in range(height)]

    def dot(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y][x] = color

    def rect(self, x0, y0, x1, y1, color):
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.pixels[y][x] = color

    def ellipse(self, cx, cy, rx, ry, color, filled=True):
        rx = max(1, rx)
        ry = max(1, ry)
        for y in range(max(0, cy - ry), min(self.height, cy + ry + 1)):
            for x in range(max(0, cx - rx), min(self.width, cx + rx + 1)):
                radius = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                if radius <= 1 and (filled or radius >= 0.62):
                    self.pixels[y][x] = color

    def line(self, x0, y0, x1, y1, color):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for step in range(steps + 1):
            x = round(x0 + (x1 - x0) * step / steps)
            y = round(y0 + (y1 - y0) * step / steps)
            self.dot(x, y, color)

    def save(self, name, directory=TEXTURES):
        raw = b"".join(
            b"\x00" + bytes(channel for pixel in row for channel in pixel)
            for row in self.pixels
        )
        header = struct.pack(">2I5B", self.width, self.height, 8, 6, 0, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", header)
        png += _chunk(b"IDAT", zlib.compress(raw, level=9))
        png += _chunk(b"IEND", b"")
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"{name}.png").write_bytes(png)


def _chunk(kind, payload):
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def draw_wii_wordmark(canvas, x, y, color):
    glyphs = {
        "W": ("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        "i": ("1", "0", "1", "1", "1", "1", "1"),
    }
    cursor = x
    for letter in "Wii":
        pattern = glyphs[letter]
        for row, pixels in enumerate(pattern):
            for column, pixel in enumerate(pixels):
                if pixel == "1":
                    canvas.dot(cursor + column, y + row, color)
        cursor += len(pattern[0]) + 1


def make_wii_family_textures():
    shell = Canvas(color=(18, 20, 24, 255))
    shell.rect(0, 0, 64, 64, (24, 26, 31, 255))
    shell.rect(2, 2, 62, 62, (17, 19, 23, 255))
    shell.rect(3, 3, 61, 5, (52, 55, 61, 255))
    shell.rect(3, 59, 61, 61, (9, 11, 14, 255))
    for y in range(10, 58, 8):
        shell.rect(5, y, 59, y + 1, (28, 30, 35, 255))
    shell.save("wii_family_shell")

    top = Canvas(color=(20, 22, 26, 255))
    top.rect(0, 0, 64, 64, (23, 25, 29, 255))
    top.rect(2, 2, 62, 62, (29, 31, 36, 255))
    top.rect(4, 4, 60, 60, (23, 25, 29, 255))
    top.rect(7, 7, 57, 57, (27, 29, 34, 255))
    top.rect(9, 9, 55, 55, (24, 26, 30, 255))
    top.rect(11, 12, 53, 13, (39, 42, 47, 255))
    top.rect(11, 51, 53, 52, (15, 17, 20, 255))
    draw_wii_wordmark(top, 25, 23, (174, 179, 186, 255))
    top.rect(25, 33, 39, 34, (108, 113, 120, 255))
    # Tiny recessed service label and screws give the top shell a manufactured finish.
    top.rect(8, 42, 18, 48, (13, 15, 18, 255))
    top.rect(9, 43, 17, 44, (83, 87, 92, 255))
    top.ellipse(7, 7, 1, 1, (103, 108, 114, 255))
    top.ellipse(56, 7, 1, 1, (103, 108, 114, 255))
    top.ellipse(7, 56, 1, 1, (103, 108, 114, 255))
    top.ellipse(56, 56, 1, 1, (103, 108, 114, 255))
    top.save("wii_family_top")

    front = Canvas(color=(15, 17, 21, 255))
    front.rect(0, 0, 64, 16, (23, 25, 29, 255))
    front.rect(1, 1, 63, 2, (61, 64, 70, 255))
    front.rect(1, 14, 63, 15, (9, 11, 14, 255))
    front.rect(3, 3, 61, 13, (19, 21, 25, 255))
    front.rect(5, 4, 6, 12, (34, 36, 41, 255))
    front.rect(58, 4, 59, 12, (12, 14, 17, 255))
    front.save("wii_family_front")

    side = Canvas(color=(21, 23, 27, 255))
    side.rect(0, 0, 64, 64, (25, 27, 31, 255))
    side.rect(2, 1, 62, 3, (55, 58, 63, 255))
    side.rect(2, 61, 62, 63, (11, 13, 16, 255))
    side.rect(5, 6, 59, 58, (22, 24, 28, 255))
    for y in range(12, 55, 8):
        side.rect(8, y, 56, y + 1, (34, 36, 41, 255))
    side.save("wii_family_side")

    back = Canvas(color=(17, 19, 23, 255))
    back.rect(0, 0, 64, 16, (21, 23, 27, 255))
    back.rect(2, 1, 62, 2, (51, 54, 59, 255))
    back.rect(4, 4, 60, 13, (14, 16, 19, 255))
    for x in (10, 21, 32, 43, 54):
        back.rect(x, 6, x + 5, 11, (49, 52, 57, 255))
        back.rect(x + 1, 7, x + 4, 10, (7, 9, 12, 255))
    back.save("wii_family_back")

    vents = Canvas(color=(17, 19, 23, 255))
    vents.rect(0, 0, 64, 16, (17, 19, 23, 255))
    for x in range(4, 62, 4):
        vents.rect(x, 3, x + 2, 13, (7, 9, 12, 255))
        vents.rect(x + 2, 3, x + 3, 13, (48, 51, 56, 255))
    vents.save("wii_family_vents")

    for name, led in (
        ("wii_family_slot", (60, 66, 73, 255)),
        ("wii_family_slot_dim", (117, 124, 132, 255)),
        ("wii_family_slot_blue", (62, 182, 255, 255)),
    ):
        slot = Canvas(64, 16, (20, 22, 26, 255))
        slot.rect(3, 2, 61, 14, (74, 79, 86, 255))
        slot.rect(5, 3, 59, 13, (8, 10, 13, 255))
        slot.rect(8, 6, 56, 10, led)
        slot.rect(10, 7, 54, 9, (13, 16, 21, 255))
        slot.save(name)

    button = Canvas(color=(17, 19, 23, 255))
    button.ellipse(32, 32, 29, 29, (88, 93, 100, 255))
    button.ellipse(32, 32, 25, 25, (38, 41, 47, 255))
    button.ellipse(32, 32, 20, 20, (17, 19, 23, 255))
    button.ellipse(32, 32, 13, 13, (53, 56, 62, 255))
    button.ellipse(32, 32, 9, 9, (29, 32, 37, 255))
    button.save("wii_family_button")

    eject = Canvas(color=(17, 19, 23, 255))
    eject.ellipse(32, 32, 29, 29, (88, 93, 100, 255))
    eject.ellipse(32, 32, 24, 24, (39, 42, 48, 255))
    eject.ellipse(32, 32, 18, 18, (18, 20, 24, 255))
    eject.line(23, 39, 41, 39, (181, 185, 191, 255))
    for y in range(23, 35):
        half_width = (y - 23) // 2
        for x in range(32 - half_width, 33 + half_width):
            eject.dot(x, y, (181, 185, 191, 255))
    eject.save("wii_family_eject")


def make_xbox_series_s_textures():
    white = Canvas(color=(231, 232, 230, 255))
    white.rect(0, 0, 64, 64, (229, 230, 228, 255))
    white.rect(2, 2, 62, 62, (240, 241, 239, 255))
    white.rect(4, 4, 60, 60, (235, 236, 234, 255))
    white.rect(5, 5, 6, 59, (249, 249, 247, 255))
    white.rect(58, 5, 59, 59, (207, 209, 207, 255))
    white.rect(7, 7, 57, 8, (224, 225, 223, 255))
    white.rect(7, 56, 57, 57, (215, 216, 214, 255))
    white.save("xbox_series_s_white")

    front = Canvas(color=(236, 237, 235, 255))
    front.rect(0, 0, 64, 64, (238, 239, 237, 255))
    front.rect(2, 2, 62, 62, (246, 247, 245, 255))
    front.rect(4, 4, 60, 60, (238, 239, 237, 255))
    front.rect(5, 5, 6, 59, (252, 252, 250, 255))
    front.rect(58, 5, 59, 59, (216, 218, 216, 255))
    front.rect(8, 6, 56, 7, (244, 245, 243, 255))
    front.rect(8, 57, 56, 58, (223, 224, 222, 255))
    front.save("xbox_series_s_front")

    side = Canvas(color=(226, 228, 226, 255))
    side.rect(0, 0, 64, 64, (229, 230, 228, 255))
    side.rect(2, 2, 62, 62, (239, 240, 238, 255))
    side.rect(4, 4, 60, 60, (231, 232, 230, 255))
    side.rect(5, 5, 7, 59, (247, 247, 245, 255))
    side.rect(57, 5, 59, 59, (207, 209, 207, 255))
    side.rect(9, 8, 55, 9, (220, 221, 219, 255))
    side.rect(9, 55, 55, 56, (219, 220, 218, 255))
    side.save("xbox_series_s_side")

    back = Canvas(color=(232, 233, 231, 255))
    back.rect(0, 0, 64, 64, (232, 233, 231, 255))
    back.rect(2, 2, 62, 62, (242, 243, 241, 255))
    back.rect(7, 9, 57, 55, (33, 36, 40, 255))
    back.rect(9, 11, 55, 53, (53, 57, 62, 255))
    for x, width in ((13, 10), (27, 9), (40, 11)):
        back.rect(x, 17, x + width, 25, (16, 19, 23, 255))
        back.rect(x + 1, 18, x + width - 1, 24, (5, 7, 10, 255))
    back.rect(14, 32, 24, 39, (9, 11, 14, 255))
    back.rect(29, 32, 40, 39, (9, 11, 14, 255))
    back.rect(44, 32, 51, 39, (9, 11, 14, 255))
    back.rect(12, 45, 52, 47, (83, 87, 92, 255))
    back.save("xbox_series_s_back")

    top = Canvas(color=(229, 230, 228, 255))
    top.rect(0, 0, 64, 64, (226, 228, 226, 255))
    top.rect(2, 2, 62, 62, (244, 245, 243, 255))
    top.rect(4, 4, 60, 60, (234, 235, 233, 255))
    top.rect(7, 7, 57, 8, (250, 250, 248, 255))
    top.rect(7, 56, 57, 57, (213, 215, 213, 255))
    top.save("xbox_series_s_top")

    vent = Canvas(64, 64, (0, 0, 0, 0))
    vent.ellipse(32, 32, 30, 30, (18, 20, 23, 255))
    vent.ellipse(32, 32, 27, 27, (38, 41, 45, 255))
    vent.ellipse(32, 32, 24, 24, (12, 14, 17, 255))
    vent.ellipse(32, 32, 21, 21, (29, 32, 36, 255))
    for angle in range(0, 360, 18):
        radians = math.radians(angle)
        x1 = round(32 + math.cos(radians) * 7)
        y1 = round(32 + math.sin(radians) * 7)
        x2 = round(32 + math.cos(radians) * 20)
        y2 = round(32 + math.sin(radians) * 20)
        vent.line(x1, y1, x2, y2, (94, 99, 104, 255))
    vent.ellipse(32, 32, 7, 7, (13, 15, 18, 255))
    vent.ellipse(32, 32, 4, 4, (57, 62, 67, 255))
    vent.ellipse(32, 32, 2, 2, (21, 24, 28, 255))
    vent.save("xbox_series_s_vent")

    power = Canvas(64, 64, (0, 0, 0, 0))
    power.ellipse(32, 32, 29, 29, (196, 198, 196, 255))
    power.ellipse(32, 32, 25, 25, (42, 45, 49, 255))
    power.ellipse(32, 32, 21, 21, (22, 25, 29, 255))
    power.ellipse(32, 32, 15, 15, (99, 188, 89, 255))
    power.rect(29, 3, 35, 22, (22, 25, 29, 255))
    power.save("xbox_series_s_power")

    port = Canvas(64, 32, (232, 233, 231, 255))
    port.rect(2, 5, 62, 27, (194, 197, 195, 255))
    port.rect(6, 7, 58, 25, (40, 43, 47, 255))
    port.rect(11, 11, 53, 21, (6, 8, 11, 255))
    port.rect(16, 12, 48, 13, (90, 94, 98, 255))
    port.save("xbox_series_s_port")

    vents = Canvas(64, 64, (235, 236, 234, 255))
    vents.rect(0, 0, 64, 64, (233, 234, 232, 255))
    vents.rect(2, 2, 62, 62, (240, 241, 239, 255))
    for y in range(7, 59, 6):
        vents.rect(4, y, 60, y + 2, (34, 37, 41, 255))
        vents.rect(5, y + 2, 59, y + 3, (193, 195, 193, 255))
    vents.save("xbox_series_s_vents")


def make_purple_tab_icon():
    icon = Canvas(16, 16, (0, 0, 0, 0))
    rows = (
        (7, 9), (6, 10), (5, 11), (4, 12), (3, 13),
        (2, 14), (2, 14), (3, 13), (4, 12), (5, 11),
        (6, 10), (7, 9),
    )
    shades = (
        (74, 30, 135, 255), (101, 47, 177, 255), (133, 69, 214, 255),
        (177, 116, 245, 255), (208, 173, 255, 255),
    )
    for y, (left, right) in enumerate(rows, start=2):
        for x in range(left, right):
            shade = shades[min(4, max(0, (x + y) % 5))]
            if x < 5:
                shade = shades[0]
            elif x < 8:
                shade = shades[2]
            elif x < 11:
                shade = shades[3]
            icon.dot(x, y, shade)
    icon.line(5, 5, 8, 3, (234, 218, 255, 255))
    icon.line(4, 7, 5, 6, (197, 161, 248, 255))
    icon.line(7, 13, 9, 14, (77, 28, 135, 255))
    icon.dot(1, 4, (207, 173, 255, 255))
    icon.dot(14, 6, (207, 173, 255, 255))
    icon.dot(2, 12, (144, 91, 215, 255))
    icon.save("tab_icon", ITEM_TEXTURES)


def draw_mw3_wordmark(canvas, x, y, color):
    glyphs = {
        "M": ("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        "W": ("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        "3": ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    }
    cursor = x
    for letter in "MW3":
        for row, pattern in enumerate(glyphs[letter]):
            for column, pixel in enumerate(pattern):
                if pixel == "1":
                    canvas.dot(cursor + column, y + row, color)
        cursor += 7


def add_mw3_camo(canvas):
    # Irregular graphite and gunmetal blocks echo the special-edition console print.
    patches = (
        (2, 7, 14, 13), (18, 3, 27, 8), (43, 5, 59, 11),
        (9, 20, 17, 28), (48, 19, 61, 26), (1, 39, 12, 46),
        (20, 49, 32, 57), (42, 39, 52, 47), (54, 51, 63, 59),
        (28, 12, 35, 18), (31, 43, 40, 49), (5, 55, 14, 62),
    )
    shades = (
        (64, 69, 73, 255), (84, 89, 91, 255), (109, 114, 115, 255),
        (49, 54, 58, 255),
    )
    for index, (x0, y0, x1, y1) in enumerate(patches):
        canvas.rect(x0, y0, x1, y1, shades[index % len(shades)])
        if x1 - x0 > 6 and y1 - y0 > 5:
            canvas.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, shades[(index + 1) % len(shades)])
    for offset in (0, 12, 24, 36, 48):
        canvas.line(offset, 0, min(offset + 15, 63), 63, (57, 62, 66, 255))
        canvas.line(min(offset + 1, 63), 0, min(offset + 16, 63), 63, (126, 130, 130, 255))


def make_xbox_textures():
    # MW3 limited-edition treatment: dark silver, black/gray digital camo, and stencil marks.
    shell = Canvas(color=(83, 88, 91, 255))
    shell.rect(0, 0, 64, 64, (91, 96, 98, 255))
    add_mw3_camo(shell)
    shell.rect(0, 0, 64, 3, (161, 164, 162, 255))
    shell.rect(0, 61, 64, 64, (40, 44, 47, 255))
    shell.save("xbox_shell")

    front = Canvas(color=(78, 83, 87, 255))
    front.rect(0, 0, 64, 64, (92, 97, 99, 255))
    add_mw3_camo(front)
    # A smoked-metal inset keeps the printed MW3 mark visible above the drive.
    front.rect(13, 12, 51, 39, (54, 59, 62, 255))
    front.rect(15, 14, 49, 37, (66, 71, 73, 255))
    draw_mw3_wordmark(front, 22, 20, (226, 228, 224, 255))
    front.rect(18, 30, 46, 31, (178, 181, 178, 255))
    front.rect(21, 34, 43, 35, (39, 43, 46, 255))
    front.rect(1, 1, 63, 3, (160, 164, 163, 255))
    front.rect(1, 61, 63, 63, (43, 47, 49, 255))
    front.save("xbox_front")

    side = Canvas(color=(73, 78, 82, 255))
    side.rect(0, 0, 64, 64, (80, 85, 88, 255))
    add_mw3_camo(side)
    side.rect(9, 10, 55, 54, (44, 49, 52, 255))
    for y in range(14, 52, 5):
        side.rect(13, y, 52, y + 2, (18, 22, 25, 255))
        side.rect(14, y + 2, 51, y + 3, (126, 131, 132, 255))
    side.save("xbox_side")

    vents = Canvas(color=(49, 54, 57, 255))
    vents.rect(0, 0, 64, 64, (58, 63, 66, 255))
    vents.rect(5, 5, 59, 59, (38, 43, 46, 255))
    for y in range(10, 58, 6):
        vents.rect(9, y, 55, y + 2, (13, 17, 20, 255))
        vents.rect(10, y + 2, 54, y + 3, (117, 122, 123, 255))
    vents.save("xbox_vents")

    top = Canvas(color=(86, 91, 94, 255))
    top.rect(0, 0, 64, 64, (90, 95, 97, 255))
    add_mw3_camo(top)
    top.rect(15, 14, 49, 50, (48, 53, 57, 255))
    top.rect(17, 16, 47, 48, (73, 78, 81, 255))
    draw_mw3_wordmark(top, 22, 22, (221, 224, 220, 255))
    top.rect(19, 33, 45, 35, (131, 136, 136, 255))
    top.rect(3, 3, 61, 5, (153, 157, 156, 255))
    top.rect(3, 59, 61, 61, (41, 45, 48, 255))
    top.save("xbox_top")

    power = Canvas(color=(40, 44, 47, 255))
    power.ellipse(32, 32, 30, 30, (35, 39, 42, 255))
    power.ellipse(32, 32, 26, 26, (136, 141, 140, 255))
    power.ellipse(32, 32, 22, 22, (79, 84, 86, 255))
    power.ellipse(32, 32, 18, 18, (40, 45, 48, 255))
    # The classic green quadrant ring remains visible against the MW3 graphite finish.
    for angle in range(0, 360, 3):
        if 16 <= angle % 90 <= 74:
            radians = math.radians(angle)
            x = round(32 + math.cos(radians) * 14)
            y = round(32 + math.sin(radians) * 14)
            power.dot(x, y, (129, 191, 89, 255))
            if x + 1 < 64:
                power.dot(x + 1, y, (154, 207, 111, 255))
    power.line(27, 27, 37, 37, (193, 197, 194, 255))
    power.line(37, 27, 27, 37, (193, 197, 194, 255))
    power.ellipse(32, 32, 3, 3, (49, 54, 57, 255))
    power.save("xbox_power")

    tray = Canvas(color=(76, 81, 84, 255))
    tray.rect(0, 0, 64, 64, (78, 83, 86, 255))
    tray.rect(3, 17, 61, 47, (42, 47, 50, 255))
    tray.rect(5, 19, 59, 45, (111, 116, 117, 255))
    tray.rect(8, 27, 56, 35, (25, 30, 33, 255))
    tray.rect(13, 29, 51, 33, (9, 13, 16, 255))
    tray.rect(8, 36, 56, 38, (137, 142, 142, 255))
    tray.save("xbox_disc_tray")

    port = Canvas(color=(75, 80, 83, 255))
    port.rect(0, 0, 64, 64, (82, 87, 89, 255))
    port.rect(7, 12, 57, 52, (125, 130, 131, 255))
    port.rect(10, 15, 54, 49, (42, 47, 50, 255))
    port.rect(16, 21, 48, 43, (13, 17, 20, 255))
    port.rect(22, 24, 42, 40, (5, 9, 12, 255))
    port.save("xbox_port")

    eject = Canvas(color=(79, 84, 87, 255))
    eject.ellipse(32, 32, 30, 30, (49, 54, 57, 255))
    eject.ellipse(32, 32, 25, 25, (139, 144, 143, 255))
    eject.ellipse(32, 32, 20, 20, (66, 71, 74, 255))
    for y in range(22, 34):
        half_width = (y - 22) // 2
        for x in range(32 - half_width, 33 + half_width):
            eject.dot(x, y, (215, 218, 214, 255))
    eject.rect(21, 37, 43, 40, (213, 216, 212, 255))
    eject.save("xbox_eject")

def make_switch_textures():
    bezel = Canvas(color=(19, 21, 24, 255))
    bezel.rect(0, 0, 64, 64, (16, 18, 21, 255))
    bezel.rect(2, 2, 62, 62, (35, 38, 42, 255))
    bezel.rect(4, 4, 60, 60, (12, 14, 17, 255))
    bezel.rect(6, 6, 58, 58, (18, 20, 23, 255))
    bezel.rect(8, 3, 56, 4, (51, 54, 58, 255))
    bezel.save("switch_bezel")

    screen = Canvas(color=(14, 23, 33, 255))
    for y in range(64):
        shade = max(0, 14 - y // 8)
        screen.rect(0, y, 64, y + 1, (13 + shade, 21 + shade, 31 + shade, 255))
    screen.rect(5, 5, 59, 6, (37, 54, 68, 255))
    screen.rect(7, 7, 57, 8, (22, 34, 46, 255))
    # A subtle, narrow reflection reads as glass without making the display look powered on.
    for offset in range(7):
        screen.line(9 + offset, 9, 19 + offset, 4, (37, 57, 72, 255))
    screen.rect(8, 57, 56, 58, (20, 31, 42, 255))
    screen.save("switch_screen")

    back = Canvas(color=(28, 31, 34, 255))
    back.rect(0, 0, 64, 64, (31, 34, 37, 255))
    back.rect(3, 3, 61, 61, (24, 27, 30, 255))
    back.rect(7, 7, 57, 57, (35, 38, 41, 255))
    for y in range(12, 56, 6):
        back.rect(10, y, 54, y + 2, (17, 19, 22, 255))
        back.rect(11, y + 2, 53, y + 3, (48, 51, 53, 255))
    back.save("switch_back")

    blue = Canvas(color=(19, 105, 190, 255))
    blue.rect(0, 0, 64, 64, (20, 111, 201, 255))
    blue.rect(0, 0, 8, 64, (10, 77, 147, 255))
    blue.rect(8, 0, 15, 64, (28, 130, 220, 255))
    blue.rect(49, 0, 64, 64, (14, 91, 166, 255))
    blue.rect(15, 0, 49, 4, (42, 145, 227, 255))
    blue.rect(15, 60, 49, 64, (9, 81, 151, 255))
    blue.save("switch_joycon_blue")

    red = Canvas(color=(228, 55, 65, 255))
    red.rect(0, 0, 64, 64, (230, 59, 68, 255))
    red.rect(0, 0, 8, 64, (171, 31, 42, 255))
    red.rect(8, 0, 15, 64, (244, 82, 87, 255))
    red.rect(49, 0, 64, 64, (190, 35, 47, 255))
    red.rect(15, 0, 49, 4, (246, 99, 100, 255))
    red.rect(15, 60, 49, 64, (169, 30, 40, 255))
    red.save("switch_joycon_red")

    left = Canvas(color=(20, 111, 201, 255))
    left.rect(0, 0, 64, 64, (20, 111, 201, 255))
    left.rect(1, 0, 8, 64, (11, 79, 149, 255))
    left.rect(8, 0, 13, 64, (31, 137, 225, 255))
    left.rect(57, 0, 64, 64, (13, 86, 158, 255))
    dark = (25, 31, 37, 255)
    rim = (61, 68, 72, 255)
    light = (190, 197, 201, 255)
    # The front face is narrow and tall in the model. These flattened pixel circles
    # compensate for the face's proportions so the rendered buttons remain round.
    left.ellipse(32, 17, 8, 2, rim)
    left.ellipse(32, 17, 6, 1, dark)
    left.ellipse(32, 17, 3, 1, (84, 92, 96, 255))
    left.rect(26, 6, 38, 8, dark)
    left.rect(28, 6, 36, 7, light)
    for cx, cy in ((32, 44), (21, 48), (43, 48), (32, 52)):
        left.ellipse(cx, cy, 5, 1, rim)
        left.ellipse(cx, cy, 4, 1, dark)
    left.rect(13, 56, 22, 59, dark)
    left.rect(15, 57, 20, 58, (101, 181, 231, 255))
    left.save("switch_joycon_left_controls")

    right = Canvas(color=(230, 59, 68, 255))
    right.rect(0, 0, 64, 64, (230, 59, 68, 255))
    right.rect(1, 0, 8, 64, (172, 31, 42, 255))
    right.rect(8, 0, 13, 64, (246, 89, 91, 255))
    right.rect(57, 0, 64, 64, (184, 35, 47, 255))
    right.rect(26, 6, 38, 8, dark)
    right.rect(28, 6, 36, 7, light)
    for cx, cy, shade in (
        (32, 27, (45, 49, 51, 255)),
        (23, 30, (55, 58, 60, 255)),
        (41, 30, (44, 47, 49, 255)),
        (32, 33, (56, 59, 61, 255)),
    ):
        right.ellipse(cx, cy, 4, 1, (129, 48, 53, 255))
        right.ellipse(cx, cy, 3, 1, shade)
        right.dot(cx, cy, (202, 205, 202, 255))
    right.ellipse(32, 49, 8, 2, rim)
    right.ellipse(32, 49, 6, 1, dark)
    right.ellipse(32, 49, 3, 1, (84, 92, 96, 255))
    right.ellipse(48, 57, 4, 1, dark)
    right.ellipse(48, 57, 2, 1, (154, 160, 162, 255))
    right.save("switch_joycon_right_controls")

    rail = Canvas(color=(35, 38, 41, 255))
    rail.rect(0, 0, 64, 64, (34, 37, 40, 255))
    rail.rect(4, 0, 11, 64, (49, 53, 56, 255))
    rail.rect(13, 0, 15, 64, (20, 23, 26, 255))
    rail.rect(50, 0, 61, 64, (25, 28, 31, 255))
    rail.save("switch_rail")

    button = Canvas(color=(34, 37, 40, 255))
    button.rect(0, 0, 64, 64, (31, 34, 37, 255))
    button.rect(5, 5, 59, 59, (50, 54, 57, 255))
    button.rect(8, 8, 56, 56, (25, 28, 31, 255))
    button.rect(12, 30, 52, 34, (66, 71, 74, 255))
    button.save("switch_button")


def main():
    make_wii_family_textures()
    make_xbox_series_s_textures()
    make_purple_tab_icon()
    make_xbox_textures()
    make_switch_textures()
    print("Generated the black Wii Family Edition, Xbox Series S, purple tab icon, Xbox 360 and Switch textures.")


if __name__ == "__main__":
    main()
