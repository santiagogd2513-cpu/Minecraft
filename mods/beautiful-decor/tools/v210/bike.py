#!/usr/bin/env python3
"""Bicicleta de BeautifulDecor (v2.1.0): geometría, textura, brillo, BicycleModel.java, ícono y vistas previas.

Uso (desde la raíz del proyecto):
  python3 -m tools.v210.bike --preview vista.png      # solo mira
  python3 -m tools.v210.bike --write                  # escribe textura, brillo, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está bajo el asiento, en el suelo.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from . import vcommon as vc
from .vcommon import (ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline, vshade)
from ..v320.paintkit import tone

TEX_W = TEX_H = 128
CLASS = "BicycleModel"

R = 5.5
REAR = (5.5, -8.5)               # (altura, z) del eje trasero
FRONT = (5.5, 8.5)
BB = (6.8, -0.5)                 # caja del pedalier
HEAD = (15.5, 5.8)               # tope de la pipa de dirección
DIR = (FRONT[0] - HEAD[0], FRONT[1] - HEAD[1])
FORK_LEN = math.hypot(*DIR)
RAKE = math.atan2(DIR[1], -DIR[0])          # la horquilla se inclina hacia delante por abajo
SEAT_TOP = (13.2, -2.7)


def head_point(t):
    """Punto a t px del tope de la pipa, bajando por el eje de dirección."""
    k = t / FORK_LEN
    return (HEAD[0] + DIR[0] * k, HEAD[1] + DIR[1] * k)


def build():
    b = vc.new_builder()
    bike = b.part("bike", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- cuadro en diamante
    vc.tube(b, bike, "seat_tube", BB, SEAT_TOP, 2, "frame", kind="frame", extra=0.5)
    vc.tube(b, bike, "top_tube", (SEAT_TOP[0] + 0.3, SEAT_TOP[1]), head_point(1.6), 2, "frame", kind="frame", extra=0.5)
    vc.tube(b, bike, "down_tube", BB, head_point(4.0), 2, "frame", kind="frame_down", extra=0.5)
    for sx, nm in ((1, "l"), (-1, "r")):
        vc.tube(b, bike, f"chain_stay_{nm}", BB, (REAR[0], REAR[1]), 1, "frame", kind="stay", x=1.5 * sx, extra=0.3)
        vc.tube(b, bike, f"seat_stay_{nm}", (SEAT_TOP[0] - 0.4, SEAT_TOP[1] - 0.3), (REAR[0], REAR[1]), 1, "frame",
                kind="stay", x=1.5 * sx, extra=0.3)
    ht0, ht1 = head_point(0.8), head_point(4.6)
    vc.tube(b, bike, "head_tube", ht0, ht1, 2, "frame", kind="head", extra=0.3)
    b.cube(bike, "bb_shell", (0, BB[0], BB[1]), (3, 2, 2), "chrome", kind="plain")

    # ---------------------------------------------------------------- asiento
    b.cube(bike, "seat_post", (0, 14.1, -2.8), (1, 3, 1), "chrome", kind="plain")
    b.cube(bike, "saddle", (0, 15.2, -1.0), (3, 1, 6), "saddle", kind="saddle")
    b.cube(bike, "saddle_nose", (0, 15.2, 3.0), (2, 1, 3), "saddle", kind="saddle")
    b.cube(bike, "saddle_pad", (0, 14.5, -1.8), (2, 1, 3), "black", kind="plain")

    # ---------------------------------------------------------------- guardabarros, portaequipajes y luz trasera
    b.cube(bike, "rear_fender", (0, 11.6, REAR[1]), (2, 1, 9), "frame", kind="fender")
    b.cube(bike, "rear_fender_tip", (0, 10.6, REAR[1] - 5.2), (2, 1, 2), "frame", kind="fender")
    b.cube(bike, "rack", (0, 12.9, REAR[1] - 1.2), (4, 1, 6), "chrome", kind="rack")
    b.cube(bike, "rack_strut_l", (1.4, 9.2, REAR[1]), (1, 5, 1), "chrome", kind="plain")
    b.cube(bike, "rack_strut_r", (-1.4, 9.2, REAR[1]), (1, 5, 1), "chrome", kind="plain")
    b.cube(bike, "tail_light", (0, 11.0, REAR[1] - 5.6), (2, 2, 1), "taillight", kind="taillight", glow=True)

    # ---------------------------------------------------------------- ruedas
    rear = vc.add_wheel(b, bike, "rear_wheel", (0, REAR[0], REAR[1]), R, 1, hub_w=5, tire_depth=4)
    b.cube(rear, "cog", (-2.0, 0, 0), (1, 3, 3), "chrome", kind="plain")

    # ---------------------------------------------------------------- pedalier
    crank = b.part("crank", bike, (0, BB[0], BB[1]))
    b.cube(crank, "chainring_a", (-2.2, 0, 0), (1, 4, 4), "chrome", kind="ring")
    p45 = b.part("crank_45", crank, (0, 0, 0), (deg(45.0), 0, 0))
    b.cube(p45, "chainring_b", (-2.2, 0, 0), (1, 4, 4), "chrome", kind="ring")
    b.cube(crank, "axle", (0, 0, 0), (7, 1, 1), "chrome", kind="plain")
    b.cube(crank, "arm_l", (2.7, -1.6, 0), (1, 4, 1), "chrome", kind="plain")
    b.cube(crank, "arm_r", (-3.2, 1.6, 0), (1, 4, 1), "chrome", kind="plain")
    b.cube(crank, "pedal_l", (3.7, -3.3, 0), (3, 1, 2), "black", kind="pedal")
    b.cube(crank, "pedal_r", (-4.2, 3.3, 0), (3, 1, 2), "black", kind="pedal")

    # cadena (dos tramos entre el plato y el piñón)
    b.cube(bike, "chain_top", (-2.2, (BB[0] + 2.4 + REAR[0] + 1.4) / 2, (BB[1] + REAR[1]) / 2), (1, 1, 9), "chain",
           kind="chain")
    b.cube(bike, "chain_bottom", (-2.2, (BB[0] - 2.4 + REAR[0] - 1.4) / 2, (BB[1] + REAR[1]) / 2), (1, 1, 9), "chain",
           kind="chain")

    # ---------------------------------------------------------------- pata de apoyo (solo parada)
    ks = b.part("kickstand", bike, (1.7, 5.4, -3.6), (0, 0, deg(-14.0)))
    b.cube(ks, "stand_leg", (0, -2.4, 0), (1, 5, 1), "black", kind="plain")
    b.cube(ks, "stand_foot", (0, -5.2, 0.4), (1, 1, 2), "black", kind="plain")

    # ---------------------------------------------------------------- dirección
    steer = b.part("steer", bike, (0, HEAD[0], HEAD[1]))
    b.cube(steer, "stem", (0, 1.4, -0.3), (1, 3, 1), "chrome", kind="plain")
    b.cube(steer, "bar", (0, 2.8, -1.0), (10, 1, 1), "chrome", kind="bar")
    b.cube(steer, "bar_riser_l", (3.4, 2.1, -1.0), (1, 2, 1), "chrome", kind="plain")
    b.cube(steer, "bar_riser_r", (-3.4, 2.1, -1.0), (1, 2, 1), "chrome", kind="plain")
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(steer, f"grip_{nm}", (5.6 * sx, 2.8, -1.0), (2, 1, 1), "saddle", kind="grip")
        b.cube(steer, f"lever_{nm}", (4.2 * sx, 2.1, 0.7), (1, 1, 3), "chrome", kind="plain")
    b.cube(steer, "bell_base", (1.6, 3.5, -1.0), (2, 1, 2), "chrome", kind="plain")
    b.cube(steer, "bell", (1.6, 4.4, -1.0), (2, 1, 2), "bell", kind="bell")
    b.cube(steer, "lamp_bracket", (0, 1.0, 1.0), (1, 1, 2), "black", kind="plain")
    b.cube(steer, "lamp", (0, 1.2, 2.6), (2, 2, 2), "lamp", kind="lamp", glow=True)

    fork = b.part("fork", steer, (0, 0, 0), (-RAKE, 0, 0))
    b.cube(fork, "crown", (0, -0.8, 0), (5, 1, 2), "frame", kind="frame")
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(fork, f"blade_{nm}", (1.9 * sx, -(FORK_LEN / 2.0) - 0.4, 0), (1, int(round(FORK_LEN)) - 1, 1), "frame",
               kind="stay")
    b.cube(fork, "front_fender", (0, -FORK_LEN + R + 1.0, 0.3), (2, 1, 9), "frame", kind="fender")
    b.cube(fork, "front_fender_tip", (0, -FORK_LEN + R - 0.1, 4.8), (2, 1, 2), "frame", kind="fender")
    vc.add_wheel(b, fork, "front_wheel", (0, -FORK_LEN, 0), R, 1, hub_w=5, tire_depth=4)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, parked=False):
    """Pose del modelo (mismo cálculo que BicycleModel.pose)."""
    b.parts["rear_wheel"].rot = [wheel_rot, 0.0, 0.0]
    b.parts["front_wheel"].rot = [wheel_rot, 0.0, 0.0]
    b.parts["crank"].rot = [wheel_rot * 0.45, 0.0, 0.0]
    b.parts["steer"].rot = [0.0, steer, 0.0]
    b.parts["kickstand"].visible = parked


# ----------------------------------------------------------------------------------------------- pintura
# Colores de referencia de los canales que el jugador puede cambiar (V3.2.0): pintura del cuadro y rines (sin manijas).
FRAME_REF, FRAME_HI_REF, FRAME_LO_REF = (38, 168, 184), (110, 224, 232), (16, 100, 120)
RIM_REF, RIM_SPOKE_REF, RIM_HUB_REF = (196, 200, 208), (150, 154, 164), (176, 182, 194)
FRAME = FRAME_REF
FRAME_HI = FRAME_HI_REF
FRAME_LO = FRAME_LO_REF
RIM = RIM_REF
RIM_SPOKE = RIM_SPOKE_REF
RIM_HUB = RIM_HUB_REF
LEATHER = (112, 66, 36)
MAT_COLOR = {
    "frame": FRAME, "chrome": (176, 182, 194), "black": (26, 26, 30), "saddle": LEATHER, "chain": (58, 58, 64),
    "taillight": (150, 12, 16), "lamp": (150, 160, 172), "bell": (196, 202, 214), "tire": (28, 28, 30),
    "hub": (176, 182, 194),
}


REFS = {"body": FRAME_REF, "rim": RIM_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global FRAME, FRAME_HI, FRAME_LO, RIM, RIM_SPOKE, RIM_HUB
    FRAME = tone(FRAME_REF, FRAME_REF, body)
    FRAME_HI = tone(FRAME_HI_REF, FRAME_REF, body)
    FRAME_LO = tone(FRAME_LO_REF, FRAME_REF, body)
    RIM = tone(RIM_REF, RIM_REF, rim)
    RIM_SPOKE = tone(RIM_SPOKE_REF, RIM_REF, rim)
    RIM_HUB = tone(RIM_HUB_REF, RIM_REF, rim)
    MAT_COLOR["frame"] = FRAME


def wheel_art(Y, Z, kind):
    r = math.hypot(Y, Z)
    ang = math.atan2(Z, Y)
    if kind == "tire":
        if r >= 5.0:
            return (30, 30, 33, 255)
        if r >= 4.45:
            return RIM + (255,)                            # aro
        if r < 1.1:
            return RIM_HUB + (255,)                        # buje
        for k in range(8):                                  # 8 rayos finos
            a0 = math.radians(45 * k)
            d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
            if d * r < 0.3:
                return RIM_SPOKE + (255,)
        return None
    return (176, 182, 194, 255)


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((30, 30, 33), (44, 44, 48)))
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    kind = c.opts.get("kind", "plain")
    gloss = c.mat in ("frame", "chrome", "bell")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    glow = {}
    top = vc.natural_top(faces)

    if kind in ("frame", "frame_down", "head", "stay"):
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            vline(a, 0, FRAME_HI, alpha=0.55)
            vline(a, a.shape[1] - 1, FRAME_LO, alpha=0.65)
            hline(a, 0, FRAME_HI, alpha=0.45)
        if kind == "frame_down":
            for f in ("west", "east"):                     # franja blanca con el nombre del cuadro
                a = vc.natural_side(faces, f)
                if a.shape[0] >= 2 and a.shape[1] >= 8:
                    rect(a, 2, a.shape[0] // 2, a.shape[1] - 2, a.shape[0] // 2 + 1, (240, 244, 248))
                vc.store_side(faces, f, a)
    elif kind == "fender":
        stripe = top.shape[1] // 2
        vline(top, stripe, FRAME_LO, alpha=0.5)
    elif kind == "saddle":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] *= 1.14
        if top.shape[1] >= 3:
            vline(top, 0, (70, 40, 22), alpha=0.8)
            vline(top, top.shape[1] - 1, (70, 40, 22), alpha=0.8)
    elif kind == "grip":
        for f in ("west", "east", "top", "bottom"):
            a = faces[f] if f != "top" else top
            for x in range(0, a.shape[1], 2):
                a[:, x, :3] = (84, 48, 26)
    elif kind == "rack":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] = (118, 122, 132)
    elif kind == "chain":
        for f in ("west", "east"):
            a = faces[f]
            for x in range(0, a.shape[1], 2):
                a[:, x, :3] = (30, 30, 34)
    elif kind == "lamp":
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (44, 46, 52)
        a = faces["front"]
        a[..., :3] = (232, 236, 244)
        outline_rect(a, (40, 42, 48))
        g = solid(a.shape[0], a.shape[1], (255, 250, 226), 255)
        glow["front"] = g
    elif kind == "taillight":
        for ff in ("west", "east", "top", "bottom", "front"):
            faces[ff][..., :3] = (110, 10, 14)
        faces["back"][..., :3] = (214, 26, 30)
        glow["back"] = solid(faces["back"].shape[0], faces["back"].shape[1], (255, 40, 30), 255)
    elif kind == "bell":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x, y) == (0, 0):
                    top[y, x, :3] = (255, 236, 160)
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.25, 0.3)
    elif kind == "ring":
        for f in ("west", "east"):
            a = faces[f]
            h, w = a.shape[:2]
            for y in range(h):
                for x in range(w):
                    if math.hypot(x - (w - 1) / 2.0, y - (h - 1) / 2.0) < 1.2:
                        a[y, x, :3] = (84, 88, 98)
    elif kind == "pedal":
        for f in ("top", "bottom"):
            if f == "top":
                for x in range(0, top.shape[1], 2):
                    top[:, x, :3] = (52, 52, 58)
    elif kind in ("plain", "bar"):
        pass

    vc.store_top(faces, top)
    if c.mat in ("chrome",):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.10, 0.18)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=2110)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=2110, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    body = vc.java_tree(b)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.BicycleEntity;
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
 * Bicicleta (V2.1.0). ARCHIVO GENERADO por tools/v210/bike.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Piezas animadas: las dos ruedas (giran), el pedalier (gira más despacio), la dirección (gira con el manubrio)
 * y la pata de apoyo (solo cuando está parada).
 */
public class BicycleModel extends EntityModel<BicycleEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "bicycle"), "main");

    private final ModelPart root;
    private final ModelPart rearWheel;
    private final ModelPart frontWheel;
    private final ModelPart crank;
    private final ModelPart steer;
    private final ModelPart kickstand;

    public BicycleModel(ModelPart root) {{
        this.root = root;
        ModelPart bike = root.getChild("bike");
        this.rearWheel = bike.getChild("rear_wheel");
        this.crank = bike.getChild("crank");
        this.steer = bike.getChild("steer");
        this.frontWheel = this.steer.getChild("fork").getChild("front_wheel");
        this.kickstand = bike.getChild("kickstand");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
    }}

    /** wheelRot en radianes (las dos ruedas), steerRot = giro del manubrio en radianes, parked = pata puesta. */
    public void pose(float wheelRot, float steerRot, boolean parked) {{
        this.frontWheel.xRot = wheelRot;
        this.rearWheel.xRot = wheelRot;
        this.crank.xRot = wheelRot * 0.45f;
        this.steer.yRot = steerRot;
        this.kickstand.visible = parked;
    }}

    @Override
    public void setupAnim(BicycleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {{
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {{
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }}
}}
'''


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(150, 12), (210, 12), (90, 4), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 9.0, 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--wheel", type=float, default=0.5)
    ap.add_argument("--steer", type=float, default=0.0)
    ap.add_argument("--parked", action="store_true")
    ap.add_argument("--dark", action="store_true")
    args = ap.parse_args()

    b, tex, glow = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX_H}")
    pose(b, args.wheel, args.steer, args.parked)
    if args.preview:
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 11.5, light_on=not args.dark)
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 4, TEX_H * 4), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/bicycle.png")
        glow.save(ASSETS / "textures/entity/bicycle_glow.png")
        (JAVA / "client/BicycleModel.java").write_text(java_model(b), encoding="utf-8")
        pose(b, 0.0, 0.0, False)
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 118, 10, CENTER, 26.0, brighten=1.08)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/bicycle.png")
        print("escrito: textura, brillo, BicycleModel.java, ícono")


if __name__ == "__main__":
    main()
