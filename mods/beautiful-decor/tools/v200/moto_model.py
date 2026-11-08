"""Moto de carreras de BeautifulDecor (v2.0.0): definición ÚNICA de la geometría.

De aquí salen (moto_gen.py) el modelo Java (MotorcycleModel), la textura 256x256, la textura de brillo
(faro y luz trasera) y las vistas previas. Así el código y la textura nunca se desalinean.

Coordenadas "amigables" (px; 16 px = 1 bloque): x = izquierda de la moto (+), y = altura sobre el suelo,
z = hacia delante (+). El origen está en el asiento, a nivel del suelo: ahí va sentado el jugador.
Internamente se pasa al sistema de los modelos de entidad de Minecraft (y hacia abajo, el frente es -z).
"""
import math

from .softrender import Cube, Part

TEX_W = TEX_H = 256


def deg(a):
    return math.radians(a)


class Builder:
    def __init__(self):
        self.root = Part("root", None, (0.0, 24.0, 0.0))
        self.parts = {"root": self.root}
        self.cubes = []

    def part(self, name, parent, pivot, rot=(0.0, 0.0, 0.0)):
        """pivot: (x, y arriba, z adelante) relativo al pivote del padre; rot en radianes (sistema del modelo)."""
        x, y, z = pivot
        p = Part(name, parent, (x, -y, -z), rot)
        self.parts[name] = p
        return p

    def cube(self, part, name, center, size, mat, **opts):
        """center: (x, y arriba, z adelante) en el sistema local de la pieza. size: (ancho x, alto y, fondo z) enteros."""
        cx, cy, cz = center
        w, h, d = size
        c = Cube(name, cx - w / 2.0, -(cy + h / 2.0), -(cz + d / 2.0), w, h, d, mat)
        c.opts = opts
        c.part = part
        part.cubes.append(c)
        self.cubes.append(c)
        return c


# ----------------------------------------------------------------------------------------------- la moto
R_TIRE = 5.5
REAR_AXLE = (5.5, -10.0)        # (altura, z)
FRONT_AXLE = (5.5, 12.0)
HEAD = (16.5, 7.0)              # cabeza de dirección (altura, z)
RAKE = 24.0
FORK_LEN = 12.0


def wheel(b, parent, name, pivot, tire_w, hub_w, disc_w):
    """Rueda: llanta octagonal hecha con 4 cajas giradas 0/45/90/135 grados, disco de freno y tapa del buje."""
    w = b.part(name, parent, pivot)
    for i in range(4):
        p = b.part(f"{name}_t{i}", w, (0, 0, 0), (deg(45.0 * i), 0, 0))
        c = b.cube(p, f"{name}_tire{i}", (0, 0, 0), (tire_w, 10, 4), "tire", wheel=True, kind="tire", phi=deg(45.0 * i))
    for i in range(2):
        p = b.part(f"{name}_d{i}", w, (0, 0, 0), (deg(45.0 * i), 0, 0))
        b.cube(p, f"{name}_disc{i}", (0, 0, 0), (disc_w, 5, 2), "disc", wheel=True, kind="disc", phi=deg(45.0 * i))
    b.cube(w, f"{name}_hub", (0, 0, 0), (hub_w, 2, 2), "hub", kind="hub")
    return w


def build():
    b = Builder()
    root = b.root

    # ---------------------------------------------------------------- motor y chasis
    b.cube(root, "engine_block", (0, 8.6, 1.0), (5, 5, 8), "engine", kind="engine")
    b.cube(root, "engine_head", (0, 12.0, 2.4), (4, 2, 5), "engine", kind="engine")
    b.cube(root, "cover_left", (3.0, 8.6, 0.5), (1, 4, 4), "red_cover", kind="cover")
    b.cube(root, "cover_right", (-3.0, 8.6, 1.5), (1, 4, 3), "engine_dark", kind="cover")
    b.cube(root, "gear_cover", (2.9, 6.2, -4.5), (1, 3, 3), "engine_dark", kind="cover")
    b.cube(root, "radiator", (0, 8.2, 6.4), (4, 5, 1), "radiator", kind="radiator")
    b.cube(root, "belly", (0, 3.8, 2.5), (6, 2, 9), "black_gloss", kind="belly")
    b.cube(root, "belly_tip", (0, 4.6, 8.8), (4, 2, 3), "black_gloss", kind="belly")

    # ---------------------------------------------------------------- carenado rojo
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(root, f"cheek_{nm}", (3.6 * sx, 8.4, 5.5), (1, 5, 9), "red", kind="cheek", side=sx)
        b.cube(root, f"winglet_{nm}", (5.2 * sx, 12.4, 11.5), (3, 1, 3), "black_gloss", kind="wing", side=sx)
        b.cube(root, f"winglet_tip_{nm}", (6.9 * sx, 12.4, 11.5), (1, 1, 3), "red", kind="wingtip", side=sx)
    # cúpula delantera: tapa superior + costados, inclinada hacia abajo en la punta
    shell = b.part("shell", root, (0, 18.0, 8.6), (deg(15.0), 0, 0))
    b.cube(shell, "shell_top", (0, -0.6, 4.5), (6, 2, 9), "red", kind="shell_top")
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(shell, f"shell_side_{nm}", (3.5 * sx, -2.6, 4.5), (1, 4, 9), "red", kind="shell_side", side=sx)
    b.cube(shell, "shell_front", (0, -2.9, 8.5), (6, 1, 2), "red", kind="shell_front")
    b.cube(shell, "chin", (0, -4.6, 4.5), (6, 1, 9), "black_gloss", kind="belly")
    b.cube(shell, "lens_l", (1.6, -2.4, 9.3), (2, 2, 1), "lens", kind="lens", glow=True)
    b.cube(shell, "lens_r", (-1.6, -2.4, 9.3), (2, 2, 1), "lens", kind="lens", glow=True)
    b.cube(shell, "intake", (0, -2.4, 9.4), (1, 2, 1), "black_gloss", kind="beak")
    b.cube(shell, "inner_dark", (0, -2.6, 5.0), (4, 3, 7), "black", kind="plain")

    ws = b.part("windscreen", root, (0, 18.4, 9.8), (-deg(34.0), 0, 0))
    b.cube(ws, "glass", (0, 2.4, 0), (5, 5, 1), "glass", kind="glass", translucent=True)

    # espejos
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(root, f"mirror_stalk_{nm}", (3.7 * sx, 18.9, 9.4), (1, 2, 1), "black_gloss", kind="stalk")
        b.cube(root, f"mirror_{nm}", (4.9 * sx, 20.3, 8.8), (3, 2, 1), "red", kind="mirror", side=sx)

    # ---------------------------------------------------------------- tanque, asiento y cola
    b.cube(root, "tank", (0, 14.5, 2.6), (5, 4, 9), "red", kind="tank")
    b.cube(root, "tank_ridge", (0, 17.0, 3.0), (3, 1, 7), "red", kind="tank_ridge")
    b.cube(root, "tank_pad_l", (2.8, 14.0, 2.0), (1, 3, 5), "black", kind="pad")
    b.cube(root, "tank_pad_r", (-2.8, 14.0, 2.0), (1, 3, 5), "black", kind="pad")
    b.cube(root, "seat", (0, 10.9, -2.8), (5, 2, 9), "seat", kind="seat")
    tail = b.part("tail", root, (0, 11.0, -6.0), (deg(13.0), 0, 0))
    b.cube(tail, "tail_cowl", (0, 1.4, -3.0), (5, 3, 8), "red", kind="tail")
    b.cube(tail, "tail_light", (0, 1.6, -7.5), (4, 1, 1), "taillight", kind="taillight", glow=True)
    b.cube(tail, "tail_under", (0, -0.4, -2.5), (3, 1, 6), "black", kind="plain")
    b.cube(root, "hugger", (0, 11.6, -11.0), (3, 1, 6), "black", kind="plain")
    b.cube(root, "plate_arm", (0, 10.2, -13.6), (1, 2, 2), "black", kind="plain")
    b.cube(root, "plate", (0, 9.4, -14.8), (3, 2, 1), "plate", kind="plate")

    # ---------------------------------------------------------------- brazo oscilante, amortiguador, escape, estribos
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(root, f"swing_{nm}", (3.6 * sx, 6.0, -7.5), (1, 2, 6), "frame", kind="swing", side=sx)
        b.cube(root, f"peg_plate_{nm}", (3.6 * sx, 5.4, -1.8), (1, 3, 3), "black", kind="plain")
        b.cube(root, f"peg_{nm}", (4.2 * sx, 4.4, -1.8), (3, 1, 1), "chrome", kind="peg")
    b.cube(root, "shock", (0, 8.0, -6.0), (2, 5, 2), "spring", kind="spring")

    ex = b.part("exhaust", root, (-3.8, 6.4, -1.0), (deg(10.0), 0, 0))
    b.cube(ex, "exhaust_pipe", (0, 0, -5.0), (2, 2, 10), "exhaust", kind="exhaust")
    b.cube(ex, "exhaust_cap", (0, 0.2, -10.3), (2, 2, 1), "chrome", kind="cap")
    b.cube(ex, "exhaust_header", (0, -1.8, 0.2), (2, 3, 2), "chrome", kind="plain")

    ks = b.part("kickstand", root, (3.6, 5.0, -1.0), (0, 0, deg(-20.0)))
    b.cube(ks, "stand_leg", (0, -2.5, 0), (1, 5, 1), "black", kind="plain")
    b.cube(ks, "stand_foot", (0, -5.3, 0), (2, 1, 2), "black", kind="plain")

    # ---------------------------------------------------------------- ruedas
    wheel(b, root, "rear_wheel", (0, REAR_AXLE[0], REAR_AXLE[1]), 4, 6, 4)

    # ---------------------------------------------------------------- dirección: horquilla + manubrio + tablero
    steer = b.part("steer", root, (0, HEAD[0], HEAD[1]))
    b.cube(steer, "triple_clamp", (0, -0.4, 0.2), (7, 2, 3), "black_gloss", kind="clamp")
    b.cube(steer, "dash", (0, 1.4, -1.2), (4, 2, 3), "dash", kind="dash")
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(steer, f"bar_{nm}", (3.9 * sx, -1.2, -0.4), (4, 1, 1), "black_gloss", kind="bar")
        b.cube(steer, f"grip_{nm}", (6.4 * sx, -1.4, -0.6), (2, 1, 1), "rubber", kind="grip")
        b.cube(steer, f"lever_{nm}", (6.4 * sx, -0.6, 1.8), (1, 1, 3), "chrome", kind="lever")
    fork = b.part("fork", steer, (0, 0, 0), (-deg(RAKE), 0, 0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(fork, f"fork_up_{nm}", (3.3 * sx, -4.0, 0), (2, 6, 2), "black_gloss", kind="fork")
        b.cube(fork, f"fork_low_{nm}", (3.3 * sx, -9.0, 0), (2, 5, 2), "chrome", kind="fork_low")
        b.cube(fork, f"axle_clamp_{nm}", (3.3 * sx, -12.0, 0), (2, 2, 3), "red", kind="clamp_red")
    b.cube(fork, "caliper", (3.0, -10.4, -2.0), (1, 3, 2), "red", kind="clamp_red")
    b.cube(fork, "fender", (0, -5.6, 0.5), (4, 1, 8), "red", kind="fender")
    b.cube(fork, "fender_tip", (0, -6.2, 5.0), (4, 1, 2), "red", kind="fender")
    wheel(b, fork, "front_wheel", (0, -FORK_LEN, 0), 3, 5, 5)
    return b


# ----------------------------------------------------------------------------------------------- animación
def pose(b, wheel_rot=0.0, steer=0.0, parked=False):
    """Deja el modelo en una pose (mismo cálculo que MotorcycleModel.setupAnim)."""
    b.parts["rear_wheel"].rot = [wheel_rot, 0.0, 0.0]
    b.parts["front_wheel"].rot = [wheel_rot, 0.0, 0.0]
    b.parts["steer"].rot = [0.0, steer, 0.0]
    b.parts["kickstand"].visible = parked
