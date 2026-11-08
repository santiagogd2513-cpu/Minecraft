#!/usr/bin/env python3
"""Generate custom pixel textures for the bathroom fixtures and lamps."""

import math
import struct
import zlib
from pathlib import Path


TEXTURES = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/beautifuldecor/textures/block"
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
        for y in range(max(0, cy - ry), min(self.height, cy + ry + 1)):
            for x in range(max(0, cx - rx), min(self.width, cx + rx + 1)):
                radius = ((x - cx) / max(rx, 1)) ** 2 + ((y - cy) / max(ry, 1)) ** 2
                if radius <= 1 and (filled or radius >= 0.76):
                    self.pixels[y][x] = color

    def line(self, x0, y0, x1, y1, color):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for step in range(steps + 1):
            x = round(x0 + (x1 - x0) * step / steps)
            y = round(y0 + (y1 - y0) * step / steps)
            self.dot(x, y, color)

    def save(self, name):
        raw = b"".join(
            b"\x00" + bytes(channel for pixel in row for channel in pixel)
            for row in self.pixels
        )
        header = struct.pack(">2I5B", self.width, self.height, 8, 6, 0, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", header)
        png += _chunk(b"IDAT", zlib.compress(raw, level=9))
        png += _chunk(b"IEND", b"")
        TEXTURES.mkdir(parents=True, exist_ok=True)
        (TEXTURES / f"{name}.png").write_bytes(png)


def _chunk(kind, payload):
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def make_fixture_metal():
    metal = Canvas(color=(120, 126, 132, 255))
    metal.rect(0, 0, 64, 64, (112, 118, 124, 255))
    metal.rect(2, 2, 62, 62, (148, 154, 160, 255))
    metal.rect(4, 4, 60, 60, (126, 132, 138, 255))
    for y in range(8, 60, 5):
        metal.rect(6, y, 58, y + 1, (166, 171, 176, 255))
        metal.rect(6, y + 1, 58, y + 2, (119, 125, 131, 255))
    for x, y in ((7, 7), (56, 7), (7, 56), (56, 56)):
        metal.ellipse(x, y, 2, 2, (72, 78, 84, 255))
        metal.ellipse(x, y, 1, 1, (198, 202, 206, 255))
    metal.save("fixture_metal")

    dark = Canvas(color=(49, 54, 60, 255))
    dark.rect(0, 0, 64, 64, (47, 52, 58, 255))
    dark.rect(2, 2, 62, 62, (66, 71, 77, 255))
    dark.rect(4, 4, 60, 60, (48, 53, 59, 255))
    for y in range(8, 60, 6):
        dark.rect(7, y, 57, y + 1, (75, 80, 86, 255))
    dark.save("fixture_dark_metal")

    brass = Canvas(color=(145, 108, 54, 255))
    brass.rect(0, 0, 64, 64, (146, 110, 57, 255))
    brass.rect(2, 2, 62, 62, (195, 158, 92, 255))
    brass.rect(4, 4, 60, 60, (165, 126, 64, 255))
    for y in range(10, 60, 8):
        brass.rect(7, y, 57, y + 2, (211, 178, 111, 255))
    brass.save("fixture_brass")


def make_light_glass_and_shades():
    glass = Canvas(color=(255, 239, 180, 255))
    glass.rect(0, 0, 64, 64, (255, 230, 151, 255))
    glass.rect(3, 3, 61, 61, (255, 247, 208, 255))
    glass.rect(6, 6, 58, 58, (255, 236, 173, 255))
    glass.rect(8, 8, 56, 12, (255, 251, 225, 255))
    glass.rect(8, 52, 56, 56, (238, 204, 125, 255))
    for y in range(16, 51, 7):
        glass.rect(9, y, 55, y + 1, (255, 244, 202, 255))
    glass.save("fixture_glass")

    shade = Canvas(color=(226, 210, 171, 255))
    shade.rect(0, 0, 64, 64, (220, 203, 163, 255))
    shade.rect(2, 2, 62, 62, (239, 225, 192, 255))
    shade.rect(4, 4, 60, 60, (229, 214, 180, 255))
    for x in range(7, 60, 6):
        shade.rect(x, 5, x + 2, 59, (213, 196, 157, 255))
    shade.rect(6, 7, 58, 9, (247, 235, 208, 255))
    shade.rect(6, 55, 58, 57, (190, 174, 139, 255))
    shade.save("lamp_shade_cream")

    wood = Canvas(color=(91, 58, 36, 255))
    wood.rect(0, 0, 64, 64, (91, 58, 36, 255))
    wood.rect(2, 2, 62, 62, (128, 83, 50, 255))
    wood.rect(4, 4, 60, 60, (103, 66, 41, 255))
    for y in (10, 18, 28, 38, 47, 56):
        wood.rect(6, y, 58, y + 1, (151, 100, 62, 255))
        wood.rect(10, y + 2, 54, y + 3, (77, 49, 33, 255))
    wood.save("lamp_wood")


def make_shower_textures():
    head = Canvas(color=(67, 72, 78, 255))
    head.rect(0, 0, 64, 64, (58, 63, 69, 255))
    head.rect(2, 2, 62, 62, (94, 100, 106, 255))
    head.ellipse(32, 32, 29, 29, (51, 56, 62, 255))
    head.ellipse(32, 32, 26, 26, (121, 127, 133, 255))
    head.ellipse(32, 32, 23, 23, (69, 74, 80, 255))
    head.ellipse(32, 32, 20, 20, (81, 86, 92, 255))
    for y in range(15, 50, 7):
        for x in range(15, 50, 7):
            if ((x - 32) ** 2 + (y - 32) ** 2) <= 17 ** 2:
                head.ellipse(x, y, 2, 2, (38, 43, 49, 255))
                head.ellipse(x, y, 1, 1, (177, 183, 188, 255))
    head.ellipse(32, 32, 2, 2, (42, 47, 53, 255))
    head.save("shower_head_holes")


def make_sink_textures():
    ceramic = Canvas(color=(232, 234, 234, 255))
    ceramic.rect(0, 0, 64, 64, (226, 229, 229, 255))
    ceramic.rect(2, 2, 62, 62, (247, 248, 247, 255))
    ceramic.rect(4, 4, 60, 60, (235, 238, 237, 255))
    ceramic.rect(6, 6, 58, 8, (255, 255, 253, 255))
    ceramic.rect(6, 56, 58, 58, (206, 211, 211, 255))
    ceramic.save("sink_ceramic")

    basin = Canvas(color=(238, 240, 239, 255))
    basin.rect(0, 0, 64, 64, (236, 238, 237, 255))
    basin.ellipse(32, 32, 30, 29, (202, 207, 207, 255))
    basin.ellipse(32, 31, 27, 26, (250, 251, 249, 255))
    basin.ellipse(32, 32, 22, 21, (194, 202, 203, 255))
    basin.ellipse(32, 30, 19, 18, (183, 222, 226, 255))
    basin.ellipse(32, 29, 16, 15, (207, 236, 237, 255))
    basin.ellipse(32, 38, 4, 3, (111, 135, 137, 255))
    basin.ellipse(32, 38, 2, 2, (68, 83, 85, 255))
    basin.line(15, 18, 26, 13, (255, 255, 255, 255))
    basin.line(18, 22, 24, 19, (255, 255, 255, 255))
    basin.save("sink_basin_top")


def make_toilet_paper_textures():
    roll = Canvas(color=(229, 228, 222, 255))
    roll.rect(0, 0, 64, 64, (217, 216, 210, 255))
    roll.rect(2, 2, 62, 62, (245, 244, 238, 255))
    roll.rect(5, 7, 59, 56, (255, 254, 248, 255))
    for y in (12, 18, 24, 30, 36, 42, 48, 53):
        roll.rect(7, y, 57, y + 1, (232, 231, 225, 255))
    roll.rect(8, 10, 56, 11, (255, 255, 253, 255))
    roll.save("toilet_paper_roll")

    end = Canvas(color=(222, 220, 212, 255))
    end.ellipse(32, 32, 30, 29, (194, 193, 186, 255))
    end.ellipse(32, 32, 27, 26, (252, 251, 246, 255))
    end.ellipse(32, 32, 20, 19, (235, 234, 227, 255))
    end.ellipse(32, 32, 12, 11, (157, 112, 72, 255))
    end.ellipse(32, 32, 8, 7, (106, 72, 48, 255))
    end.ellipse(32, 32, 4, 3, (46, 38, 31, 255))
    end.save("toilet_paper_end")

    holder = Canvas(color=(118, 124, 130, 255))
    holder.rect(0, 0, 64, 64, (112, 118, 124, 255))
    holder.rect(3, 3, 61, 61, (166, 171, 176, 255))
    holder.rect(6, 6, 58, 58, (126, 132, 138, 255))
    holder.rect(8, 8, 56, 15, (193, 197, 201, 255))
    holder.rect(8, 49, 56, 56, (84, 90, 96, 255))
    holder.save("toilet_paper_holder")


def main():
    make_fixture_metal()
    make_light_glass_and_shades()
    make_shower_textures()
    make_sink_textures()
    make_toilet_paper_textures()
    print("Generated bathroom and lighting textures.")


if __name__ == "__main__":
    main()
