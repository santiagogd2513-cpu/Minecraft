#!/usr/bin/env python3
"""Remolques de la tractomula KENWORT (V7.9.0): furgón de carga, maderero (troncos) y cisterna (líquidos).

Uso (desde la raíz del proyecto):
  python3 -m tools.v790.trailers --preview /tmp/vistas      # solo mira (una imagen por remolque)
  python3 -m tools.v790.trailers --write                    # escribe texturas, brillos, Trailer*Model.java e íconos
Coordenadas (px, 16 px = 1 bloque): x = izquierda (+), y = altura, z = adelante (+). El ORIGEN es el perno de enganche (en la quinta rueda
del tractor) y el remolque se extiende hacia atrás (z negativa). Los ejes traseros están a 10 bloques del perno (AXLE_BACK en TrailerEntity).
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline
from ..v300.common300 import draw_text, text_width

TEX = {"cargo": (512, 448), "logs": (512, 288), "tank": (512, 576), "carrier": (512, 960)}
WHEEL_R = 8.0
WHEEL_X = 18.0
AXLES = (-152.0, -168.0)          # a 10 bloques (160 px) del perno, el promedio
RED = (181, 18, 27)
GOLD = (226, 178, 40)
MAT_COLOR = {
    "box": (236, 238, 244), "frame": (24, 25, 29), "chrome": (190, 196, 208), "dark": (22, 23, 27), "tire": (26, 26, 29),
    "taillight": (150, 12, 16), "marker": (230, 150, 30), "wood": (150, 112, 70), "steel": (120, 126, 138),
    "tank": (204, 208, 218), "leg": (60, 62, 70), "hub": (190, 196, 208), "rubber": (30, 30, 34), "ramp": (88, 92, 102), "yellow": (232, 184, 36),
}


def box(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    for v in (w, h, d):
        assert abs(v - round(v)) < 1e-6 and v >= 1, (name, w, h, d)
    return b.cube(part, name, ((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0),
                  (int(round(w)), int(round(h)), int(round(d))), mat, **opts)


def lr(b, part, name, xa, xb, y0, y1, z0, z1, mat, **opts):
    box(b, part, name + "_l", xa, xb, y0, y1, z0, z1, mat, side=1, **opts)
    box(b, part, name + "_r", -xb, -xa, y0, y1, z0, z1, mat, side=-1, **opts)


def wheel_art(Y, Z, kind):
    r = math.hypot(Y, Z)
    ang = math.atan2(Z, Y)
    if r >= 7.0:
        return (24, 24, 27, 255)
    if r >= 5.8:
        return (38, 38, 43, 255)
    if r >= 5.0:
        return (206, 210, 218, 255)
    if r < 1.9:
        return (204, 208, 216, 255) if r >= 1.0 else (90, 94, 104, 255)
    for k in range(10):
        a0 = math.radians(36 * k + 18)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.9:
            return (184, 190, 202, 255)
    return (52, 54, 62, 255)


def chassis(b, body, deck_top=19, axles=AXLES, end=-184):
    """Chasis común: vigas, ejes con 4 ruedas (dobles), patas de apoyo, perno de enganche, luces y guardafangos.
    V7.10.0: axles y end para la niñera (más larga)."""
    box(b, body, "kingpin", -3, 3, 14, 17, -3, 3, "chrome", kind="plain")
    box(b, body, "pin_plate", -16, 16, 15, 17, -24, 4, "dark", kind="plain")
    z = -28
    k = 0
    while z > end:
        z1 = max(end, z - 76)
        box(b, body, f"beam_{k}", -12, 12, 15, 17, int(z1), int(z), "frame", kind="plain")
        z, k = z1, k + 1
    for k, z in enumerate(axles):
        for sx, nm in ((1, "l"), (-1, "r")):
            vc.add_wheel(b, body, f"wheel_{k}{nm}", (WHEEL_X * sx, WHEEL_R, z), WHEEL_R, 8, tire_depth=6)
        box(b, body, f"axle_{k}", -18, 18, 7, 9, int(z) - 1, int(z) + 1, "dark", kind="plain")
    lr(b, body, "fender", 14, 24, 17, 19, int(axles[-1]) - 8, int(axles[0]) + 8, "frame", kind="plain")
    lr(b, body, "flap", 12, 22, 3, 9, int(axles[-1]) - 10, int(axles[-1]) - 9, "rubber", kind="plain")
    legs = b.part("legs", body, (0, 0, 0))
    lr(b, legs, "leg", 12, 15, 0, 15, -40, -37, "leg", kind="plain")
    lr(b, legs, "foot", 10, 17, 0, 1, -42, -35, "dark", kind="plain")
    box(b, legs, "leg_bar", -12, 12, 9, 11, -40, -37, "leg", kind="plain")
    lr(b, body, "tail", 14, 20, 16, 21, end, end + 1, "taillight", kind="taillight", glow=True)
    box(b, body, "bumper", -20, 20, 10, 15, end - 2, end + 1, "chrome", kind="bumper")
    return legs


def build_cargo():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    chassis(b, body)
    for k, (z0, z1) in enumerate(((-184, -124), (-124, -64), (-64, -4))):
        box(b, body, f"cargo_{k}", -20, 20, 17, 61, z0, z1, "box", kind="cargo", seg=k)
    lr(b, body, "skirt", 20, 21, 12, 17, -150, -30, "frame", kind="plain")
    lr(b, body, "marker", 18, 20, 58, 60, -183, -181, "marker", kind="marker")
    box(b, body, "marker_f", -2, 2, 59, 61, -5, -4, "marker", kind="marker")
    return b


def build_logs():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    chassis(b, body)
    for k, (z0, z1) in enumerate(((-184, -124), (-124, -64), (-64, -4))):
        box(b, body, f"deck_{k}", -20, 20, 17, 19, z0, z1, "wood", kind="deck")
    box(b, body, "headboard", -20, 20, 19, 60, -8, -4, "steel", kind="headboard")
    for k, z in enumerate((-24, -72, -120, -168)):
        lr(b, body, f"stake_{k}", 20, 22, 19, 56, z, z + 3, "steel", kind="stake")
    lr(b, body, "tail_stake", 18, 20, 19, 22, -184, -183, "marker", kind="marker")
    return b


def build_tank():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    chassis(b, body)
    R = 18
    d = 15
    cy = 17 + R + 1                                                          # centro del tanque: encima del chasis
    for k, (z0, z1) in enumerate(((-176, -120), (-120, -64), (-64, -8))):
        for i in range(4):
            part = b.part(f"tank_{k}_{i}", body, (0, cy, (z0 + z1) / 2.0), (0.0, 0.0, deg(45.0 * i)))
            b.cube(part, f"tank_slab_{k}_{i}", (0, 0, 0), (2 * R, d, z1 - z0), "tank", kind="tank_slab", seg=k, ring=i)
    for k, z in enumerate((-176, -120, -64, -8)):
        box(b, body, f"band_{k}", -3, 3, cy + R + 1, cy + R + 2, z - 1 if z > -176 else z, (z + 2) if z > -176 else z + 2, "steel", kind="plain")
    box(b, body, "manhole", -5, 5, cy + R + 1, cy + R + 4, -100, -90, "steel", kind="plain")
    box(b, body, "manhole_lid", -4, 4, cy + R + 4, cy + R + 5, -99, -91, "chrome", kind="plain")
    box(b, body, "valve_box", -8, 8, 17, 27, -190, -184, "steel", kind="valve")
    lr(b, body, "ladder", 19, 21, 17, 50, -150, -148, "steel", kind="plain")
    lr(b, body, "marker", 18, 20, 36, 38, -183, -181, "marker", kind="marker")
    return b


def boxs(b, part, name, x0, x1, y0, y1, z0, z1, mat, chunk=46, **opts):
    """box() partido en tramos a lo largo de z (cada uno ocupa menos atlas que un cubo largo)."""
    n = max(1, -(-(z1 - z0) // chunk))
    step = (z1 - z0) / n
    for i in range(n):
        box(b, part, f"{name}{i}", x0, x1, y0, y1, int(round(z0 + step * i)), int(round(z0 + step * (i + 1))), mat, **opts)


def lrs(b, part, name, xa, xb, y0, y1, z0, z1, mat, chunk=24, **opts):
    """lr() partido en tramos de hasta chunk px (un cubo largo no cabe en el atlas: su desarrollo mide 2 x (ancho + largo))."""
    n = max(1, -(-(z1 - z0) // chunk))
    step = (z1 - z0) / n
    for i in range(n):
        lr(b, part, f"{name}{i}", xa, xb, y0, y1, int(round(z0 + step * i)), int(round(z0 + step * (i + 1))), mat, **opts)


CARRIER_END = -260
CARRIER_AXLES = (-204.0, -220.0)          # promedio a 13,25 bloques del perno (AXLE_BACK de la niñera en TrailerEntity)
UPPER_Y = 60                              # cubierta alta (arriba del piso); la baja está en y = 19


def build_carrier():
    """V7.10.0: niñera de dos pisos: 3 autos abajo y 3 arriba. Dos rampas atrás (baja y alta) que giran en las bisagras del borde."""
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    chassis(b, body, axles=CARRIER_AXLES, end=CARRIER_END)
    # cubierta baja y alta
    for k, (z0, z1) in enumerate(((-260, -196), (-196, -132), (-132, -68), (-68, -4))):
        box(b, body, f"deck_lo_{k}", -20, 20, 17, 19, z0, z1, "steel", kind="deck")
        box(b, body, f"deck_hi_{k}", -20, 20, UPPER_Y - 2, UPPER_Y, z0, z1, "steel", kind="deck")
    lrs(b, body, "curb_lo", 20, 22, 19, 22, CARRIER_END, -4, "steel", kind="plain")
    lrs(b, body, "curb_hi", 20, 22, UPPER_Y, UPPER_Y + 3, CARRIER_END, -4, "steel", kind="plain")
    # postes y larguero del techo
    for k, z in enumerate((-8, -86, -164, -258)):
        lr(b, body, f"post_{k}", 20, 23, 19, 100, z, z + 3, "frame", kind="plain")
    lrs(b, body, "roof_beam", 20, 23, 98, 101, CARRIER_END, -4, "frame", kind="plain")
    box(b, body, "roof_front", -20, 20, 98, 101, -8, -4, "frame", kind="plain")
    # vigas intermedias: sostienen la cubierta alta
    lrs(b, body, "mid_beam", 20, 22, UPPER_Y - 4, UPPER_Y - 2, CARRIER_END, -4, "frame", kind="plain")
    # tablero del frente de la cubierta alta (con el nombre)
    box(b, body, "headboard", -20, 20, UPPER_Y, UPPER_Y + 28, -8, -4, "steel", kind="headboard")
    lrs(b, body, "skirt", 21, 22, 10, 17, -230, -40, "frame", chunk=48, kind="skirt")
    lr(b, body, "marker", 18, 20, 98, 100, CARRIER_END, CARRIER_END + 2, "marker", kind="marker")
    box(b, body, "marker_f", -2, 2, 99, 101, -5, -4, "marker", kind="marker")
    # rampas (bisagra en el borde trasero de cada cubierta; cerradas quedan planas como prolongación del piso)
    lo = b.part("ramp_low", body, (0, 19, CARRIER_END))
    boxs(b, lo, "ramp_lo_panel", -18, 18, -2, 0, -64, 0, "ramp", chunk=32, kind="ramp")
    lrs(b, lo, "ramp_lo_rail", 18, 20, -2, 2, -64, 0, "yellow", chunk=32, kind="plain")
    hi = b.part("ramp_high", body, (0, UPPER_Y, CARRIER_END))
    boxs(b, hi, "ramp_hi_panel", -18, 18, -2, 0, -136, 0, "ramp", chunk=34, kind="ramp")
    lrs(b, hi, "ramp_hi_rail", 18, 20, -2, 2, -136, 0, "yellow", chunk=34, kind="plain")
    return b


BUILDERS = {"cargo": build_cargo, "logs": build_logs, "tank": build_tank, "carrier": build_carrier}


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((26, 26, 29), (42, 42, 47)))
    kind = c.opts.get("kind", "plain")
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    faces = vc.base_faces(c, color, rng, gloss=c.mat in ("box", "chrome", "tank"))
    glow = {}
    top = vc.natural_top(faces)
    if kind == "cargo":
        seg = c.opts.get("seg", 0)
        for f in ("west", "east"):
            a = faces[f]
            h, w = a.shape[:2]
            rect(a, 0, h - 6, w, h - 5, GOLD)
            rect(a, 0, h - 5, w, h, (26, 8, 10))
            rect(a, 0, h - 9, w, h - 8, RED)
            for x in range(8, w - 2, 12):
                vline(a, x, (190, 194, 206), y0=0, y1=h - 10, alpha=0.5)
            if seg == 1:
                draw_text(a, "KENWORT", (w - text_width("KENWORT", 2)) // 2, 8, RED, scale=2, shadow=(190, 194, 210))
                draw_text(a, "CARGA PESADA", (w - text_width("CARGA PESADA")) // 2, 24, (84, 96, 140))
            faces[f] = a
        for y in range(top.shape[0]):
            if y % 8 == 0:
                top[y, :, :3] *= 0.86
        if seg == 0:
            a = faces["back"]
            h, w = a.shape[:2]
            outline_rect(a, (190, 194, 206), alpha=0.9)
            vline(a, w // 2, (110, 114, 128), alpha=0.95)
            vline(a, w // 2 - 1, (110, 114, 128), alpha=0.5)
            for hx in (w // 2 - 4, w // 2 + 3):
                rect(a, hx, 20, hx + 1, 30, (150, 154, 166))
            rect(a, 0, h - 6, w, h, (26, 8, 10))
            draw_text(a, "KENWORT", (w - text_width("KENWORT")) // 2, 6, RED)
            faces["back"] = a
        if seg == 2:
            a = faces["front"]
            h, w = a.shape[:2]
            rect(a, 0, h - 6, w, h, (26, 8, 10))
            draw_text(a, "KENWORT", (w - text_width("KENWORT")) // 2, 8, RED)
            faces["front"] = a
        faces["bottom"][..., :3] = (60, 62, 70)
    elif kind == "deck":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if y % 6 == 0:
                    top[y, x, :3] *= 0.78
    elif kind == "headboard":
        a = faces["front"]
        for y in range(0, a.shape[0], 4):
            a[y, :, :3] *= 0.8
        a = faces["back"]
        for y in range(0, a.shape[0], 4):
            a[y, :, :3] *= 0.8
        draw_text(faces["back"], "KENWORT", (faces["back"].shape[1] - text_width("KENWORT")) // 2, 4, RED)
    elif kind == "ramp":
        for y in range(top.shape[0]):
            if y % 6 < 2:
                top[y, :, :3] *= 0.7                                     # tacos antideslizantes
        for x in (0, top.shape[1] - 1):
            vline(top, x, (232, 184, 36), alpha=0.9)
    elif kind == "skirt":
        for f in ("west", "east"):
            a = faces[f]
            draw_text(a, "KENWORT", (a.shape[1] - text_width("KENWORT")) // 2, 1, (236, 238, 244))
            faces[f] = a
    elif kind == "stake":
        for f in list(faces):
            hline(faces[f], 0, (200, 204, 216), alpha=0.6)
    elif kind == "tank_slab":
        seg, ring = c.opts.get("seg", 0), c.opts.get("ring", 0)
        for f in ("west", "east"):
            a = faces[f]
            rows, cols = a.shape[:2]
            a[..., :3] = (206, 210, 220)
            for x in range(cols):
                a[:, x, :3] = np.array((214, 218, 228)) * (0.88 + 0.12 * math.sin(x * 0.12))
            vline(a, 0, (150, 154, 166), alpha=0.9)
            vline(a, cols - 1, (150, 154, 166), alpha=0.9)
            rect(a, 0, rows // 2 - 1, cols, rows // 2 + 1, RED)                  # franja roja a lo largo
            if seg == 1 and f == "west" and ring == 0:
                draw_text(a, "KENWORT", 4, rows // 2 - 7, (60, 8, 12))
            faces[f] = a
        for f in ("front", "back"):
            faces[f][..., :3] = (150, 154, 166)
        for f in ("top", "bottom"):
            faces[f][..., :3] = (120, 124, 136)
        top = vc.natural_top(faces)
    elif kind == "valve":
        for f in list(faces):
            faces[f][..., :3] = (90, 94, 104)
        a = faces["back"]
        rect(a, 2, 2, 6, 6, RED)
        rect(a, a.shape[1] - 6, 2, a.shape[1] - 2, 6, (40, 90, 200))
    elif kind == "taillight":
        for ff in ("west", "east", "top", "bottom", "front"):
            faces[ff][..., :3] = (96, 8, 12)
        a = faces["back"]
        a[..., :3] = (206, 24, 30)
        outline_rect(a, (60, 6, 8))
        glow["back"] = solid(a.shape[0], a.shape[1], (255, 40, 30), 255)
        glow["west"] = solid(*faces["west"].shape[:2], (255, 40, 30), 255)
        glow["east"] = solid(*faces["east"].shape[:2], (255, 40, 30), 255)
    elif kind == "marker":
        for f in list(faces):
            faces[f][..., :3] = (230, 150, 30)
    elif kind == "bumper":
        for f in ("front", "back", "west", "east"):
            hline(faces[f], 0, (240, 244, 250), alpha=0.8)
        rect(faces["back"], 0, 1, faces["back"].shape[1], faces["back"].shape[0] - 1, (206, 24, 30))
        for x in range(0, faces["back"].shape[1], 4):
            rect(faces["back"], x, 1, x + 2, faces["back"].shape[0] - 1, (236, 238, 244))
    vc.store_top(faces, top)
    return faces, glow


def build_all(kind):
    b = BUILDERS[kind]()
    tw, th = TEX[kind]
    vc.pack(b.cubes, tw, th)
    atlas, glow = vc.paint_atlas(b.cubes, tw, th, paint_cube, seed=7950)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


CLASS = {"cargo": "TrailerCargoModel", "logs": "TrailerLogsModel", "tank": "TrailerTankModel", "carrier": "TrailerCarrierModel"}
JAVA_TEMPLATE = '''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.TrailerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Remolque de la tractomula: {desc} (V7.9.0). ARCHIVO GENERADO por tools/v790/trailers.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {tw}x{th}. El origen es el perno de enganche y el remolque se extiende hacia atrás. Animado: las 4 ruedas y las patas de apoyo.
 */
public class {cls} extends EntityModel<TrailerEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "trailer_{kind}"), "main");

    private final ModelPart root;
    private final ModelPart legs;
    private final ModelPart[] wheels;

    public {cls}(ModelPart root) {{
        this.root = root;
        ModelPart body = root.getChild("body");
        this.legs = body.getChild("legs");
        this.wheels = new ModelPart[] {{
                body.getChild("wheel_0l"), body.getChild("wheel_0r"), body.getChild("wheel_1l"), body.getChild("wheel_1r")}};
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {tw}, {th});
    }}

    /** wheelRot en radianes (las 4 ruedas); legsDown = patas de apoyo abajo (sin tractor). */
    public void pose(float wheelRot, boolean legsDown) {{
        for (ModelPart wheel : this.wheels) {{
            wheel.xRot = wheelRot;
        }}
        this.legs.visible = legsDown;
    }}

    @Override
    public void setupAnim(TrailerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {{
        // La pose se aplica desde el renderizador con pose(...).
    }}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {{
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }}
}}
'''
DESC = {"cargo": "furgón de carga", "logs": "maderero (troncos)", "tank": "cisterna (líquidos)", "carrier": "niñera de dos pisos con rampas"}


def java_model(kind, b):
    tw, th = TEX[kind]
    tpl = JAVA_TEMPLATE
    if kind == "carrier":
        tpl = tpl.replace("private final ModelPart legs;", "private final ModelPart legs;\n    private final ModelPart rampLow;\n    private final ModelPart rampHigh;")
        tpl = tpl.replace('this.legs = body.getChild("legs");', 'this.legs = body.getChild("legs");\n        this.rampLow = body.getChild("ramp_low");\n        this.rampHigh = body.getChild("ramp_high");')
        tpl = tpl.replace("public void pose(float wheelRot, boolean legsDown) {{", "public void pose(float wheelRot, boolean legsDown, float lowAngle, float highAngle) {{\n        this.rampLow.xRot = RAMP_SIGN * lowAngle;\n        this.rampHigh.xRot = RAMP_SIGN * highAngle;")
        tpl = tpl.replace("/** wheelRot en radianes", "/** Signo de xRot que baja el borde libre de las rampas (se comprueba en el juego). */\n    private static final float RAMP_SIGN = -1.0f;\n\n    /** wheelRot en radianes (lowAngle/highAngle = cuánto bajó cada rampa, en radianes, 0 = plana)")
        tpl = tpl.replace("Animado: las 4 ruedas y las patas de apoyo.", "Animado: las 4 ruedas, las patas de apoyo y las dos rampas.")
    return tpl.format(body=vc.java_tree(b), tw=tw, th=th, cls=CLASS[kind], kind=kind, desc=DESC[kind])


def pose(b, wheel_rot=0.0):
    for nm in b.parts:
        if nm.startswith("wheel_") and "_t" not in nm:
            b.parts[nm].rot = [wheel_rot, 0.0, 0.0]


VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 26.0, -90)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--only")
    args = ap.parse_args()
    for kind in BUILDERS:
        if args.only and kind != args.only:
            continue
        b, tex, glow = build_all(kind)
        tw, th = TEX[kind]
        used = max(c.v + int(c.h + c.d) for c in b.cubes)
        print(f"{kind}: {len(b.cubes)} cubos, atlas usado hasta y={used}/{th}")
        pose(b, 0.4)
        if args.preview:
            Path(args.preview).mkdir(parents=True, exist_ok=True)
            imgs = vc.views(b, tex, glow, (tw, th), VIEWS, CENTER, 1.9, light_on=True, size=(420, 330))
            vc.sheet(imgs).convert("RGB").save(Path(args.preview) / f"{kind}.png")
        if args.write:
            (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
            tex.save(ASSETS / f"textures/entity/trailer_{kind}.png")
            glow.save(ASSETS / f"textures/entity/trailer_{kind}_glow.png")
            (JAVA / f"client/{CLASS[kind]}.java").write_text(java_model(kind, b), encoding="utf-8")
            pose(b, 0.0)
            icon = vc.make_icon(b, tex, glow, (tw, th), 140, 16, CENTER, 2.5, outline=(24, 24, 30, 255), brighten=1.0)
            (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
            icon.save(ASSETS / f"textures/item/trailer_{kind}.png")
    print("listo")


if __name__ == "__main__":
    main()
