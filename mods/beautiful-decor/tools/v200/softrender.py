"""Renderizador de software para modelos de entidad de Minecraft (cubos con textura).

Sirve para ver cómo queda un modelo SIN abrir el juego: respeta el orden de rotación de ModelPart (Rz·Ry·Rx),
la numeración de caras/UV de ModelPart.Cube y el sistema de coordenadas de los modelos de entidad
(y hacia abajo, el frente es -z, la x positiva es la izquierda de la entidad).
"""
import math

import numpy as np
from PIL import Image


# ---------------------------------------------------------------------------------------------- matemática
def rot_xyz(rx, ry, rz):
    """Matriz de ModelPart.translateAndRotate: Rz · Ry · Rx (se aplica primero Rx)."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


class Cube:
    def __init__(self, name, x, y, z, w, h, d, mat):
        self.name, self.x, self.y, self.z, self.w, self.h, self.d = name, x, y, z, w, h, d
        self.mat = mat
        self.u = self.v = 0
        self.pad = 0.0

    def footprint(self):
        return (int(math.ceil(2 * (self.w + self.d))), int(math.ceil(self.h + self.d)))


class Part:
    def __init__(self, name, parent, pivot, rot=(0.0, 0.0, 0.0)):
        self.name, self.parent = name, parent
        self.pivot = np.array(pivot, dtype=float)      # relativo al pivote del padre, en px, sistema del modelo
        self.rot = list(rot)                            # xRot, yRot, zRot
        self.cubes, self.children = [], []
        self.visible = True
        if parent is not None:
            parent.children.append(self)

    def path(self):
        names, p = [], self
        while p is not None and p.parent is not None:
            names.append(p.name)
            p = p.parent
        return list(reversed(names))


# ---------------------------------------------------------------------------------------------- geometría
def cube_polys(c, tex_w, tex_h):
    """Las 6 caras de ModelPart.Cube (sin espejo): (vértices [4x3 px], uv [4x2 normalizados], nombre)."""
    x0, y0, z0 = c.x - c.pad, c.y - c.pad, c.z - c.pad
    x1, y1, z1 = c.x + c.w + c.pad, c.y + c.h + c.pad, c.z + c.d + c.pad
    v7 = (x0, y0, z0); v = (x1, y0, z0); vv1 = (x1, y1, z0); v2 = (x0, y1, z0)
    v3 = (x0, y0, z1); v4 = (x1, y0, z1); v5 = (x1, y1, z1); v6 = (x0, y1, z1)
    u, vo, dx, dy, dz = c.u, c.v, c.w, c.h, c.d
    f6, f7, f8, f9 = u, u + dz, u + dz + dx, u + dz + dx + dx
    f10, f11 = u + dz + dx + dz, u + dz + dx + dz + dx
    f12, f13, f14 = vo, vo + dz, vo + dz + dy

    def poly(verts, u1, v1, u2, v2, name):
        uv = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        return (np.array(verts, dtype=float), np.array([(a / tex_w, b / tex_h) for a, b in uv]), name)

    return [
        poly([v4, v3, v7, v], f7, f12, f8, f13, "up"),         # cara y-min (arriba en el mundo)
        poly([vv1, v2, v6, v5], f8, f12, f9, f13, "down"),
        poly([v7, v3, v6, v2], f6, f13, f7, f14, "xmin"),
        poly([v, v7, v2, vv1], f7, f13, f8, f14, "front"),
        poly([v4, v, vv1, v5], f8, f13, f10, f14, "xmax"),
        poly([v3, v4, v5, v6], f10, f13, f11, f14, "back"),
    ]


def collect(part, parent_m, parent_t, tex_w, tex_h, out):
    if not part.visible:
        return
    m = parent_m @ rot_xyz(*part.rot)
    t = parent_m @ part.pivot + parent_t
    for c in part.cubes:
        for verts, uv, name in cube_polys(c, tex_w, tex_h):
            world = (verts @ m.T) + t
            out.append((world, uv, name, c.mat))
    for ch in part.children:
        collect(ch, m, t, tex_w, tex_h, out)


def model_to_world(pts):
    """Coordenadas de modelo (px, y abajo, frente -z) -> mundo (x este, y arriba, z sur) con la entidad mirando al sur."""
    out = pts.copy()
    out[..., 1] = 24.0 - pts[..., 1]
    out[..., 2] = -pts[..., 2]
    return out


# ---------------------------------------------------------------------------------------------- raster
class Camera:
    def __init__(self, azimuth, elevation, scale, center, size):
        self.az, self.el, self.scale, self.center, self.size = azimuth, elevation, scale, np.array(center, float), size
        a, e = math.radians(azimuth), math.radians(elevation)
        ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        rx = np.array([[1, 0, 0], [0, math.cos(e), -math.sin(e)], [0, math.sin(e), math.cos(e)]])
        self.m = rx @ ry

    def project(self, p):
        q = (p - self.center) @ self.m.T
        sx = self.size[0] / 2 + q[..., 0] * self.scale
        sy = self.size[1] / 2 - q[..., 1] * self.scale
        return np.stack([sx, sy, q[..., 2]], axis=-1)


def shade(normal_view, base=0.58):
    light = np.array([-0.35, 0.8, 0.5])
    light /= np.linalg.norm(light)
    return base + (1.0 - base) * max(0.0, float(np.dot(normal_view, light)))


def render(root, textures, cam, tex_size, glow=None, background=(0, 0, 0, 0), supersample=3, translucent_mats=()):
    """Dibuja el árbol de partes. textures: imagen RGBA. glow: imagen RGBA que se suma a pleno brillo."""
    tex_w, tex_h = tex_size
    polys = []
    collect(root, np.eye(3), np.zeros(3), tex_w, tex_h, polys)
    ss = supersample
    W, H = cam.size[0] * ss, cam.size[1] * ss
    cam_ss = Camera(cam.az, cam.el, cam.scale * ss, cam.center, (W, H))
    color = np.zeros((H, W, 4), dtype=np.float32)
    color[..., :] = np.array(background, dtype=np.float32)
    zbuf = np.full((H, W), -1e9, dtype=np.float32)
    tex = np.asarray(textures.convert("RGBA"), dtype=np.float32)
    glow_arr = np.asarray(glow.convert("RGBA"), dtype=np.float32) if glow is not None else None

    opaque, trans = [], []
    for world_model, uv, name, mat in polys:
        world = model_to_world(world_model)
        (trans if mat in translucent_mats else opaque).append((world, uv, name))

    def draw(items, blend):
        for world, uv, name in items:
            scr = cam_ss.project(world)
            nw = np.cross(world[1] - world[0], world[2] - world[0])
            ln = np.linalg.norm(nw)
            if ln < 1e-9:
                continue
            nw = nw / ln
            if (cam_ss.m @ nw)[2] < 0:       # la cara se ve desde fuera: la normal apunta hacia la cámara
                nw = -nw
            sh = shade(nw, 0.58)
            for tri in ((0, 1, 2), (0, 2, 3)):
                p = scr[list(tri)]
                t = uv[list(tri)]
                minx = int(max(0, math.floor(p[:, 0].min()))); maxx = int(min(W - 1, math.ceil(p[:, 0].max())))
                miny = int(max(0, math.floor(p[:, 1].min()))); maxy = int(min(H - 1, math.ceil(p[:, 1].max())))
                if minx > maxx or miny > maxy:
                    continue
                xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
                (x0, y0, _), (x1, y1, _), (x2, y2, _) = p
                den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
                if abs(den) < 1e-9:
                    continue
                l0 = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
                l1 = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
                l2 = 1 - l0 - l1
                inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
                if not inside.any():
                    continue
                z = l0 * p[0, 2] + l1 * p[1, 2] + l2 * p[2, 2]
                uu = (l0 * t[0, 0] + l1 * t[1, 0] + l2 * t[2, 0]) * tex_w
                vv = (l0 * t[0, 1] + l1 * t[1, 1] + l2 * t[2, 1]) * tex_h
                ui = np.clip(np.floor(uu - 1e-4).astype(int), 0, tex_w - 1)
                vi = np.clip(np.floor(vv - 1e-4).astype(int), 0, tex_h - 1)
                texel = tex[vi, ui]
                alpha = texel[..., 3] / 255.0
                ok = inside & (alpha > 0.04) & (z > zbuf[miny:maxy + 1, minx:maxx + 1])
                if not ok.any():
                    continue
                rgb = texel[..., :3] * sh
                if glow_arr is not None:
                    g = glow_arr[vi, ui]
                    ga = (g[..., 3] / 255.0)[..., None]
                    rgb = rgb * (1 - ga) + g[..., :3] * ga
                reg = color[miny:maxy + 1, minx:maxx + 1]
                if blend:
                    a = alpha[..., None]
                    new = reg[..., :3] * (1 - a) + rgb * a
                    reg[..., :3] = np.where(ok[..., None], new, reg[..., :3])
                    reg[..., 3] = np.where(ok, np.maximum(reg[..., 3], 255 * alpha), reg[..., 3])
                else:
                    reg[..., :3] = np.where(ok[..., None], rgb, reg[..., :3])
                    reg[..., 3] = np.where(ok, 255, reg[..., 3])
                    zbuf[miny:maxy + 1, minx:maxx + 1] = np.where(ok, z, zbuf[miny:maxy + 1, minx:maxx + 1])

    draw(opaque, False)
    draw(trans, True)
    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")
    if ss > 1:
        img = img.resize(cam.size, Image.LANCZOS)
    return img
