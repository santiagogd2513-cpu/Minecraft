#!/usr/bin/env python3
"""Carro a control remoto LAMBORGONZO HURACANITO (parodia de un superdeportivo italiano) de BeautifulDecor, V3.1.0.

UNA sola definición de geometría produce todo, así el bloque y la entidad son idénticos en forma, color y diseño:
  * la textura (atlas 128x128) que usan la entidad (textures/entity/rc_car.png) y el bloque (textures/block/rc_car.png);
  * el modelo Java de la entidad (RcCarModel.java, ARCHIVO GENERADO);
  * el modelo JSON del bloque (models/block/rc_car.json), con las mismas piezas y los mismos UV;
  * el ícono del ítem y las vistas previas.

Escala: 1 unidad del modelo = 1/32 de bloque (el auto mide 32 unidades = 1 bloque de largo). La entidad se dibuja a escala 0,5
y el bloque usa medio píxel por unidad. Coordenadas "amigables": x = izquierda del auto (+), y = altura, z = adelante (+);
el origen está en el centro, a nivel del suelo.

Uso (desde la raíz del proyecto):
  python3 -m tools.v310.rc_car --preview vista.png      # solo mira
  python3 -m tools.v310.rc_car --write                  # escribe textura, modelo Java, modelo del bloque e ícono
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v200.softrender import cube_polys, rot_xyz, model_to_world
from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, rect, solid, vline

TEX = 128                      # atlas cuadrado: un bloque con textura más alta que ancha se tomaría por animado
SCALE = 0.5                    # píxeles de bloque por unidad de modelo

LIME = (142, 216, 26)          # verde "mantis" de la parodia
LIME_HI = (176, 238, 58)
LIME_LO = (92, 150, 16)
BLACK = (16, 16, 19)
CARBON = (30, 31, 36)
GLASS = (22, 32, 48)
CHROME = (196, 202, 214)
GOLD = (222, 170, 40)

MAT_COLOR = {"paint": LIME, "carbon": CARBON, "black": BLACK, "glass": GLASS, "chrome": CHROME, "tire": (24, 24, 27),
             "hub": CHROME}

WHEEL_R = 2.5
WHEEL_W = 3
WHEEL_X = 6.0
AXLE_Y = 2.5
REAR_Z, FRONT_Z = -9.0, 10.0


def box(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    for v in (w, h, d):
        assert abs(v - round(v)) < 1e-6 and v >= 1, (name, w, h, d)
    return b.cube(part, name, ((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0),
                  (int(round(w)), int(round(h)), int(round(d))), mat, **opts)


def lr(b, part, name, xa, xb, y0, y1, z0, z1, mat, **opts):
    """Pareja izquierda (x entre xa y xb, >= 0) y derecha (espejo)."""
    box(b, part, name + "_l", xa, xb, y0, y1, z0, z1, mat, side=1, **opts)
    box(b, part, name + "_r", -xb, -xa, y0, y1, z0, z1, mat, side=-1, **opts)


def plate(b, parent, name, center, size, angle_deg, mat, **opts):
    """Placa inclinada (parabrisas, tapa del motor): una pieza con pivote en su centro, girada alrededor del eje x."""
    p = b.part(name, parent, center, (deg(angle_deg), 0.0, 0.0))
    b.cube(p, name + "_c", (0, 0, 0), size, mat, **opts)
    return p


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- ruedas (4 cajas giradas 0/45/90/135 = rueda octagonal)
    for sx, nm in ((1, "l"), (-1, "r")):
        vc.add_wheel(b, body, f"wheel_r{nm}", (WHEEL_X * sx, AXLE_Y, REAR_Z), WHEEL_R, WHEEL_W, tire_depth=2)
        st = b.part(f"steer_{nm}", body, (WHEEL_X * sx, AXLE_Y, FRONT_Z))
        vc.add_wheel(b, st, f"wheel_f{nm}", (0, 0, 0), WHEEL_R, WHEEL_W, tire_depth=2)

    # ---------------------------------------------------------------- bajos
    box(b, body, "floor", -6, 6, 1, 2, -14, 14, "carbon", kind="plain")
    box(b, body, "splitter", -6, 6, 0, 1, 13, 16, "carbon", kind="splitter")
    box(b, body, "diffuser", -5, 5, 0, 1, -16, -12, "carbon", kind="diffuser")
    lr(b, body, "exhaust", 1, 3, 2, 4, -17, -16, "chrome", kind="exhaust")

    # ---------------------------------------------------------------- carrocería
    box(b, body, "tail", -6, 6, 2, 5, -16, -12, "paint", kind="tail")
    b.cube(body, "deck", (0, 4, -9), (9, 4, 6), "paint", kind="deck")
    box(b, body, "mid", -6, 6, 2, 6, -6, 7, "paint", kind="flank")
    b.cube(body, "hood", (0, 3.5, 10), (9, 3, 6), "paint", kind="hood")
    box(b, body, "nose", -6, 6, 1, 4, 13, 16, "paint", kind="nose")
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(body, f"flare_r{nm}", (6.0 * sx, 5.5, -9), (3, 1, 6), "paint", kind="flare", side=sx)
        b.cube(body, f"flare_f{nm}", (6.0 * sx, 5.5, 10), (3, 1, 6), "paint", kind="flare", side=sx)

    # ---------------------------------------------------------------- cabina
    box(b, body, "cabin_glass", -4, 4, 6, 8, -6, 1, "glass", kind="cabin")
    b.cube(body, "roof", (0, 8.5, -2.5), (7, 1, 7), "paint", kind="roof")
    plate(b, body, "windshield", (0, 7.2, 3.0), (8, 1, 6), 22.5, "glass", kind="windshield")
    plate(b, body, "engine_cover", (0, 7.2, -9.0), (8, 1, 6), -22.5, "paint", kind="engine_cover")
    lr(b, body, "mirror", 4, 6, 7, 8, 2, 3, "carbon", kind="mirror")
    box(b, body, "cowl_fill", -4, 4, 6, 7, 1, 4, "black", kind="fill")
    box(b, body, "engine_fill_a", -4, 4, 6, 7, -10, -6, "black", kind="fill")
    box(b, body, "engine_fill_b", -4, 4, 7, 8, -8, -6, "black", kind="fill")
    return b


# ----------------------------------------------------------------------------------------------- pintura
def wheel_art(Y, Z, kind):
    r = math.hypot(Y, Z)
    ang = math.atan2(Z, Y)
    if r >= 1.85:
        return (22, 22, 25, 255)                           # neumático
    if r >= 1.2:
        return (200, 205, 216, 255)                        # aro cromado
    if r < 0.5:
        return GOLD + (255,)                               # tapa dorada
    for k in range(5):                                      # 5 rayos
        a0 = math.radians(72 * k + 18)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.5:
            return (170, 176, 190, 255)
    return (40, 41, 48, 255)


def glass_faces(c, rng):
    faces = {}
    for f, (rows, cols) in vc.face_sizes(c).items():
        a = solid(rows, cols, GLASS)
        noise(a, rng, 0.03)
        if f in ("west", "east", "front", "back") and cols >= 3 and rows >= 2:
            for x in range(cols):
                y = (cols - 1 - x) // 2
                if 0 <= y < rows and x % 4 < 2:
                    a[y, x, :3] = (96, 120, 150)
        elif f == "top" and rows >= 3 and cols >= 3:
            for x in range(cols):
                for y in range(rows):
                    if (x + y) % 5 == 0:
                        a[y, x, :3] = (84, 108, 138)
        faces[f] = a
    return faces, {}


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((24, 24, 27), (38, 38, 43)))
    kind = c.opts.get("kind", "plain")
    if kind in ("cabin", "windshield"):
        return glass_faces(c, rng)
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("paint", "chrome")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    top = vc.natural_top(faces)
    side = c.opts.get("side", 1)
    outer, inner = ("east", "west") if side > 0 else ("west", "east")

    if kind == "hood":
        rect(top, 3, 0, 5, top.shape[0], BLACK)            # franja negra de capó (dos líneas finas)
        rect(top, 2, 0, 3, top.shape[0], LIME_HI)
        rect(top, 5, 0, 6, top.shape[0], LIME_HI)
    elif kind in ("roof", "engine_cover", "deck"):
        rect(top, top.shape[1] // 2 - 1, 0, top.shape[1] // 2 + 1, top.shape[0], BLACK)
        if kind == "engine_cover":
            for y in range(1, top.shape[0] - 1, 2):
                rect(top, 1, y, top.shape[1] - 1, y + 1, (40, 56, 12))      # persianas del motor
    elif kind == "flank":
        for f in ("west", "east"):
            img = vc.natural_side(faces, f)
            h, w = img.shape[:2]
            rect(img, 0, h - 1, w, h, BLACK)                # faldón negro
            vline(img, 7, LIME_LO, y0=0, y1=h - 1)           # línea de la puerta
            vline(img, w - 3, LIME_LO, y0=0, y1=h - 1)
            rect(img, 1, 1, 5, h - 1, BLACK)                 # toma de aire lateral detrás de la puerta
            for x in (2, 4):
                vline(img, x, (50, 54, 62), y0=1, y1=h - 1)
            rect(img, 8, 1, 9, 2, LIME_HI)
            vc.store_side(faces, f, img)
    elif kind == "tail":
        a = faces["back"]
        h, w = a.shape[:2]
        rect(a, 0, 0, w, 1, LIME_HI)
        rect(a, 1, 1, 4, 2, (216, 18, 24)); rect(a, w - 4, 1, w - 1, 2, (216, 18, 24))   # luces traseras en Y
        rect(a, 3, 2, 5, 3, (216, 18, 24)); rect(a, w - 5, 2, w - 3, 3, (216, 18, 24))
        rect(a, 4, 2, w - 4, 3, BLACK)
    elif kind == "nose":
        a = faces["front"]
        h, w = a.shape[:2]
        rect(a, 0, 2, w, h, BLACK)                          # entrada de aire baja
        rect(a, 2, 0, 5, 1, BLACK); rect(a, w - 5, 0, w - 2, 1, BLACK)     # faros en Y
        rect(a, 2, 1, 4, 2, (210, 236, 255)); rect(a, w - 4, 1, w - 2, 2, (210, 236, 255))
        rect(a, 5, 1, 6, 2, (210, 236, 255)); rect(a, w - 6, 1, w - 5, 2, (210, 236, 255))
        rect(a, w // 2 - 1, 0, w // 2 + 1, 1, GOLD)         # escudo dorado (sin toro)
    elif kind == "exhaust":
        for f in ("front", "back"):
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], (60, 60, 68))
            rect(faces[f], 0, 0, 1, 1, (16, 16, 18))
    elif kind in ("splitter", "diffuser"):
        for y in range(0, top.shape[0], 2):
            rect(top, 0, y, top.shape[1], y + 1, (46, 48, 56))

    vc.store_top(faces, top)
    if c.mat == "paint" and kind not in ("flank", "tail", "nose"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.05, 0.10)
    return faces, {}


def build_all():
    b = build()
    vc.pack(b.cubes, TEX, TEX)
    atlas, glow = vc.paint_atlas(b.cubes, TEX, TEX, paint_cube, seed=3100)
    return b, Image.fromarray(atlas, "RGBA")


def pose(b, wheel_rot=0.0, steer=0.0):
    for nm in ("wheel_rl", "wheel_rr", "wheel_fl", "wheel_fr"):
        b.parts[nm].rot = [wheel_rot, 0.0, 0.0]
    b.parts["steer_l"].rot = [0.0, steer, 0.0]
    b.parts["steer_r"].rot = [0.0, steer, 0.0]


# ----------------------------------------------------------------------------------------------- bloque (JSON)
# Orden de los vértices de cada cara en un modelo de bloque de vanilla (FaceInfo): el primero lleva (u1, v1), el tercero (u2, v2).
FACE_CORNERS = {
    "down": (("min", "min", "max"), ("min", "min", "min"), ("max", "min", "min"), ("max", "min", "max")),
    "up": (("min", "max", "min"), ("min", "max", "max"), ("max", "max", "max"), ("max", "max", "min")),
    "north": (("max", "max", "min"), ("max", "min", "min"), ("min", "min", "min"), ("min", "max", "min")),
    "south": (("min", "max", "max"), ("min", "min", "max"), ("max", "min", "max"), ("max", "max", "max")),
    "west": (("min", "max", "min"), ("min", "min", "min"), ("min", "min", "max"), ("min", "max", "max")),
    "east": (("max", "max", "max"), ("max", "min", "max"), ("max", "min", "min"), ("max", "max", "min")),
}
DIRECTIONS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def _rx(deg_):
    """Giro de vanilla para "rotation" de un elemento (eje x): mano derecha, ángulo en grados."""
    a = math.radians(deg_)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def _walk(part, pm, pt, out):
    m = pm @ rot_xyz(*part.rot)
    t = pm @ part.pivot + pt
    for c in part.cubes:
        polys = []
        for verts, uv, name in cube_polys(c, TEX, TEX):
            w = model_to_world(verts @ m.T + t)
            blk = np.stack([8.0 - SCALE * w[:, 0], SCALE * w[:, 1], 8.0 - SCALE * w[:, 2]], axis=1)
            polys.append((blk, uv * TEX))
        o = model_to_world((t)[None, :])[0]
        origin = np.array([8.0 - SCALE * o[0], SCALE * o[1], 8.0 - SCALE * o[2]])
        out.append((c, polys, origin))
    for ch in part.children:
        _walk(ch, m, t, out)


def _aligned(points):
    for axis in range(3):
        if len(set(np.round(points[:, axis], 3))) > 2:
            return False
    return True


def block_model(b):
    """Modelo JSON del bloque con las MISMAS piezas y UV que la entidad (frente hacia el norte; el blockstate lo gira)."""
    items = []
    _walk(b.root, np.eye(3), np.zeros(3), items)
    elements = []
    for c, polys, origin in items:
        allv = np.concatenate([p[0] for p in polys])
        angle = None
        for cand in (0.0, 22.5, -22.5, 45.0, -45.0):
            un = (allv - origin) @ _rx(-cand).T + origin
            if _aligned(un):
                angle = cand
                break
        assert angle is not None, f"{c.name}: no se puede expresar como elemento de bloque"
        unpolys = [((p[0] - origin) @ _rx(-angle).T + origin, p[1]) for p in polys]
        pts = np.concatenate([p[0] for p in unpolys])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        center = (lo + hi) / 2.0
        faces = {}
        for name, normal in DIRECTIONS.items():
            chosen = None
            for verts, uvpx in unpolys:
                n = np.cross(verts[1] - verts[0], verts[2] - verts[0])
                if np.linalg.norm(n) < 1e-9:
                    continue
                n = n / np.linalg.norm(n)
                if np.dot(n, verts.mean(axis=0) - center) < 0:
                    n = -n
                if np.allclose(n, normal, atol=1e-3):
                    chosen = (verts, uvpx)
                    break
            assert chosen is not None, (c.name, name)
            verts, uvpx = chosen
            want = []
            for corner in FACE_CORNERS[name]:
                pos = np.array([(lo if k == "min" else hi)[i] for i, k in enumerate(corner)])
                idx = int(np.argmin(np.linalg.norm(verts - pos, axis=1)))
                assert np.linalg.norm(verts[idx] - pos) < 1e-3, (c.name, name, corner)
                want.append(uvpx[idx])
            done = None
            for k in range(4):
                base = [want[(j - k) % 4] for j in range(4)]       # base[j] = uv que tendría el vértice j sin rotación
                u1, v1 = base[0]
                u2, v2 = base[2]
                if np.allclose(base[1], (u1, v2)) and np.allclose(base[3], (u2, v1)):
                    done = (k, u1, v1, u2, v2)
                    break
            assert done is not None, (c.name, name, want)
            k, u1, v1, u2, v2 = done
            face = {"uv": [round(float(v) * 16.0 / TEX, 4) for v in (u1, v1, u2, v2)], "texture": "#0"}
            if k:
                face["rotation"] = 90 * k
            faces[name] = face
        el = {"name": c.name, "from": [round(float(v), 4) for v in lo], "to": [round(float(v), 4) for v in hi]}
        if abs(angle) > 1e-9:
            el["rotation"] = {"origin": [round(float(v), 4) for v in origin], "axis": "x", "angle": angle}
        el["faces"] = faces
        elements.append(el)
    return {"textures": {"particle": "beautifuldecor:block/rc_car", "0": "beautifuldecor:block/rc_car"},
            "elements": elements}


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    body = vc.java_tree(b)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.RcCarEntity;
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
 * Carro RC Lamborgonzo Huracanito (V3.1.0). ARCHIVO GENERADO por tools/v310/rc_car.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX}x{TEX}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Una unidad del modelo es 1/32 de bloque: el renderizador lo dibuja a escala 0,5 (el bloque usa el mismo diseño).
 * Piezas animadas: las 4 ruedas (giran) y las dos delanteras (doblan).
 */
public class RcCarModel extends EntityModel<RcCarEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "rc_car"), "main");

    private final ModelPart root;
    private final ModelPart wheelFl;
    private final ModelPart wheelFr;
    private final ModelPart wheelRl;
    private final ModelPart wheelRr;
    private final ModelPart steerL;
    private final ModelPart steerR;

    public RcCarModel(ModelPart root) {{
        this.root = root;
        ModelPart body = root.getChild("body");
        this.steerL = body.getChild("steer_l");
        this.steerR = body.getChild("steer_r");
        this.wheelFl = this.steerL.getChild("wheel_fl");
        this.wheelFr = this.steerR.getChild("wheel_fr");
        this.wheelRl = body.getChild("wheel_rl");
        this.wheelRr = body.getChild("wheel_rr");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX}, {TEX});
    }}

    /** wheelRot en radianes (las 4 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {{
        this.wheelFl.xRot = wheelRot;
        this.wheelFr.xRot = wheelRot;
        this.wheelRl.xRot = wheelRot;
        this.wheelRr.xRot = wheelRot;
        this.steerL.yRot = steerRot;
        this.steerR.yRot = steerRot;
    }}

    @Override
    public void setupAnim(RcCarEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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


VIEWS = [(145, 18), (215, 18), (90, 4), (0, 8), (180, 8), (180, 80)]
CENTER = (0, 4.5, 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--steer", type=float, default=0.0)
    args = ap.parse_args()
    b, tex = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX}")
    pose(b, 0.3, args.steer)
    if args.preview:
        glow = Image.new("RGBA", tex.size, (0, 0, 0, 0))
        imgs = vc.views(b, tex, glow, (TEX, TEX), VIEWS, CENTER, 9.5, light_on=False, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX * 4, TEX * 4), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        pose(b, 0.0, 0.0)
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/rc_car.png")
        tex.save(ASSETS / "textures/block/rc_car.png")
        (JAVA / "client/RcCarModel.java").write_text(java_model(b), encoding="utf-8")
        (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
        (ASSETS / "models/block/rc_car.json").write_text(json.dumps(block_model(b), indent=1) + "\n", encoding="utf-8")
        glow = Image.new("RGBA", tex.size, (0, 0, 0, 0))
        icon = vc.make_icon(b, tex, glow, (TEX, TEX), 148, 24, CENTER, 14.0, outline=(20, 30, 8, 255))
        icon.save(ASSETS / "textures/item/rc_car.png")
        print("escrito: texturas (entidad y bloque), RcCarModel.java, models/block/rc_car.json, ícono")


if __name__ == "__main__":
    main()
