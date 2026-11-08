"""Paleta recoloreable de los vehículos (V3.2.0).

Cada vehículo tiene hasta tres "canales" de color que el jugador puede cambiar con la tecla R: pintura de la carrocería,
manijas de las puertas y rines. Los generadores de textura (tools/v200/moto_paint.py, tools/v210/{truck,bike}.py,
tools/v300/{delivery_truck,helicopter}.py) leen los colores de cada canal desde variables de módulo, y
`set_paint(...)` las vuelve a calcular desplazándolas por un delta. Con delta cero salen EXACTAMENTE los colores de siempre,
así que las texturas que ya existen no cambian ni un píxel.

Máscara: se pinta la textura de referencia y una vez más por canal con el color de ese canal desplazado. Lo que cambió
en cada píxel, dividido por el desplazamiento, es la "sensibilidad" k del píxel a ese canal (0 = nada que ver con la pintura,
1 = color liso del canal, más de 1 = tono claro, menos de 1 = tono oscuro). En el juego el color nuevo se aplica como
    salida = original + k * (color_nuevo - color_de_referencia)
Con el color de referencia da el original; con otro color conserva el relieve, los ruidos y las líneas de panel.
"""

CHANNELS = ("body", "handle", "rim")


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def tone(c, ref, delta):
    """Tono `c` (un color derivado del canal) desplazado: cuanto más claro es, más se desplaza (como si se multiplicara)."""
    if not any(delta):
        return tuple(c)
    s = lum(c) / lum(ref)
    return tuple(float(c[i]) + s * delta[i] for i in range(3))


def probe_delta(ref):
    """Desplazamiento de prueba: 40 hacia el lado del canal que más espacio tiene (sin salirse de 0..255 con tonos claros)."""
    return tuple(40.0 if ref[i] < 128 else -40.0 for i in range(3))
