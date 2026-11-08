#!/usr/bin/env python3
"""Genera la moto (v2.0.0): textura, brillo, MotorcycleModel.java, ícono del ítem y vistas previas.

Uso (desde la raíz del proyecto):
  python3 -m tools.v200.moto_gen --preview vista.png        # solo mira: escribe la hoja de vistas, nada del proyecto
  python3 -m tools.v200.moto_gen --write                    # escribe texturas, modelo Java e ícono en el proyecto
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from . import moto_model
from .moto_model import TEX_H, TEX_W
from .moto_paint import pack, paint_atlas
from .softrender import Camera, render

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"


def build_all():
    b = moto_model.build()
    used = pack(b.cubes)
    atlas, glow = paint_atlas(b.cubes)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA"), used


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = moto_model.build()
    pack(b.cubes)
    return paint_atlas(b.cubes, raw=True)[0]


# ----------------------------------------------------------------------------------------------- vistas
def views(b, tex, glow, light_on, size=(360, 300), poses=None):
    out = []
    specs = poses or [(150, 14), (210, 12), (90, 6), (0, 8), (180, 8), (180, 78)]
    for az, el in specs:
        cam = Camera(az, el, 7.4, (0, 12.0, 0), size)
        im = render(b.root, tex, cam, (TEX_W, TEX_H), glow=glow if light_on else None,
                    background=(78, 86, 98, 255), translucent_mats=("glass",))
        out.append(im)
    return out


def sheet(images, cols=3, label=None):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    s = Image.new("RGBA", (cols * w, rows * h), (50, 56, 66, 255))
    for i, im in enumerate(images):
        s.paste(im, ((i % cols) * w, (i // cols) * h))
    return s


# ----------------------------------------------------------------------------------------------- icono
def make_icon(b, tex, glow):
    moto_model.pose(b, 0.0, 0.0, False)
    cam = Camera(145, 15, 20.0, (0, 12.0, 0), (640, 640))
    big = render(b.root, tex, cam, (TEX_W, TEX_H), glow=glow, background=(0, 0, 0, 0), supersample=2,
                 translucent_mats=("glass",))
    bbox = big.getbbox()
    big = big.crop(bbox)
    scale = 30.0 / max(big.size)
    small = big.resize((max(1, round(big.width * scale)), max(1, round(big.height * scale))), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float32)
    alpha = arr[..., 3] > 90
    arr[~alpha] = 0
    arr[alpha, 3] = 255
    small = Image.fromarray(arr.astype(np.uint8), "RGBA")
    icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    ox, oy = (32 - small.width) // 2, (32 - small.height) // 2
    icon.paste(small, (ox, oy), small)
    a = np.asarray(icon).astype(np.float32)
    sil = a[..., 3] > 0
    pad = np.pad(sil, 1)
    near = np.zeros_like(sil)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        near |= pad[1 + dy:33 + dy, 1 + dx:33 + dx]
    edge = near & ~sil
    a[edge] = (20, 14, 16, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# ----------------------------------------------------------------------------------------------- Java
def f(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return (s if s not in ("", "-0") else "0") + "f"


def java_part(part, parent_var, names, indent):
    """Código Java que crea 'part' (y sus hijos) colgando de parent_var."""
    var = names[part.name]
    pad = "        " + "    " * indent
    lines = []
    if part.cubes:
        cubes = "CubeListBuilder.create()"
        for c in part.cubes:
            cubes += (f"\n{pad}        .texOffs({c.u}, {c.v}).addBox({f(c.x)}, {f(c.y)}, {f(c.z)}, "
                      f"{f(c.w)}, {f(c.h)}, {f(c.d)})")
    else:
        cubes = "CubeListBuilder.create()"
    px, py, pz = (float(v) for v in part.pivot)
    rx, ry, rz = part.rot
    if rx or ry or rz:
        pose = f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)})"
    else:
        pose = f"PartPose.offset({f(px)}, {f(py)}, {f(pz)})"
    lines.append(f'{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("{part.name}",\n'
                 f'{pad}        {cubes},\n{pad}        {pose});')
    for ch in part.children:
        lines.extend(java_part(ch, var, names, indent))
    return lines


def java_model(b):
    names = {name: "p_" + name for name in b.parts}
    names["root"] = "bike"
    # "bike" lleva los cubos sueltos de la raíz de Python y todo cuelga de él; el suelo está en y = 24
    cubes = "CubeListBuilder.create()"
    for c in b.root.cubes:
        cubes += (f"\n                        .texOffs({c.u}, {c.v}).addBox({f(c.x)}, {f(c.y)}, {f(c.z)}, "
                  f"{f(c.w)}, {f(c.h)}, {f(c.d)})")
    lines = [f'        PartDefinition bike = root.addOrReplaceChild("bike",\n                {cubes},\n'
             f'                PartPose.offset(0.0f, 24.0f, 0.0f));']
    for ch in b.root.children:
        lines.extend(java_part(ch, "bike", names, 0))
    body = "\n".join(lines)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.MotorcycleEntity;
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
 * Moto de carreras (V2.0.0). ARCHIVO GENERADO por tools/v200/moto_gen.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Piezas animadas: rueda delantera y trasera (giran), dirección (gira con el manubrio) y pata de cabra (solo parada).
 */
public class MotorcycleModel extends EntityModel<MotorcycleEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "motorcycle"), "main");

    private final ModelPart root;
    private final ModelPart frontWheel;
    private final ModelPart rearWheel;
    private final ModelPart steer;
    private final ModelPart kickstand;

    public MotorcycleModel(ModelPart root) {{
        this.root = root;
        ModelPart bike = root.getChild("bike");
        this.rearWheel = bike.getChild("rear_wheel");
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

    /** wheelRot en radianes (las dos ruedas), steerRot = giro del manubrio en radianes, parked = pata de cabra puesta. */
    public void pose(float wheelRot, float steerRot, boolean parked) {{
        this.frontWheel.xRot = wheelRot;
        this.rearWheel.xRot = wheelRot;
        this.steer.yRot = steerRot;
        this.kickstand.visible = parked;
    }}

    @Override
    public void setupAnim(MotorcycleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="escribe una hoja de vistas aquí (no toca el proyecto)")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--wheel", type=float, default=0.4)
    ap.add_argument("--steer", type=float, default=0.0)
    ap.add_argument("--parked", action="store_true")
    ap.add_argument("--dark", action="store_true", help="vista con la luz apagada")
    args = ap.parse_args()

    b, tex, glow, used = build_all()
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX_H}")
    moto_model.pose(b, args.wheel, args.steer, args.parked)
    if args.preview:
        imgs = views(b, tex, glow, not args.dark)
        sheet(imgs).convert("RGB").save(args.preview)
        print("vista previa:", args.preview)
        tex_big = tex.resize((TEX_W * 3, TEX_H * 3), Image.NEAREST)
        bg = Image.new("RGBA", tex_big.size, (90, 96, 108, 255))
        bg.alpha_composite(tex_big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/motorcycle.png")
        glow.save(ASSETS / "textures/entity/motorcycle_glow.png")
        (JAVA / "client/MotorcycleModel.java").write_text(java_model(b), encoding="utf-8")
        icon = make_icon(b, tex, glow)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/motorcycle.png")
        print("escrito: textura, brillo, MotorcycleModel.java, ícono")


if __name__ == "__main__":
    main()
