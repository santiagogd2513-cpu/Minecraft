package com.example.beautifuldecor.camera;

/**
 * V4.3.0: hacia dónde mira una cámara de seguridad con rotación activada, sin nada de Minecraft. Los ángulos son los de Minecraft:
 * 0 = sur, 90 = oeste, 180 = norte, 270 = este (el mismo número que {@code Direction.toYRot()} y que el índice 2D de la dirección por 90).
 * <p>
 * Un arco es el tramo por el que la cámara barre de ida y vuelta, despacio y con una pausa en cada extremo. Solo una cámara de TECHO sin
 * ningún bloque a sus cuatro lados puede girar sin parar los 360 grados; con bloques alrededor (una esquina, una pared) el arco se reduce
 * a las direcciones libres, y una cámara de pared solo barre un poco a cada lado de hacia donde mira.
 */
public final class CameraSweep {
    /** Tramo de barrido: de {@code start} a {@code end} (end >= start, puede pasar de 360). {@code full} = vuelta completa sin parar. */
    public record Arc(float start, float end, boolean full) {
        public float span() {
            return this.full ? 360f : this.end - this.start;
        }

        public boolean fixed() {
            return !this.full && this.end - this.start < 1f;
        }
    }

    /** Velocidad de barrido (grados por tick): 6 grados por segundo. */
    public static final float SWEEP_SPEED = 0.3f;
    /** Velocidad de la vuelta completa (grados por tick): 8 grados por segundo. */
    public static final float FULL_SPEED = 0.4f;
    /** Pausa en cada extremo del barrido (ticks). */
    public static final float PAUSE_TICKS = 40f;
    /** Lo que se le resta al arco junto a un bloque, para no mirar pegado a la pared (grados). */
    public static final float MARGIN = 10f;
    /** Cuánto barre a cada lado una cámara de pared con el lado libre y con el lado bloqueado (grados). */
    public static final float WALL_FREE = 50f;
    public static final float WALL_BLOCKED = 15f;

    private CameraSweep() {
    }

    /** Mínimo del ángulo en [0, 360). */
    public static float norm(float deg) {
        float r = deg % 360f;
        return r < 0 ? r + 360f : r;
    }

    /**
     * Arco de una cámara de techo. {@code blocked[d]} dice si el bloque vecino en la dirección d (índice 2D: 0 sur, 1 oeste, 2 norte,
     * 3 este) estorba. Los cuatro libres = vuelta completa; ninguno libre = fija; si no, el tramo seguido de direcciones libres que contiene
     * hacia donde mira al ponerla (si esa está libre) o el más largo.
     */
    public static Arc ceilingArc(boolean[] blocked, float baseYaw) {
        int free = 0;
        for (int d = 0; d < 4; d++) {
            if (!blocked[d]) {
                free++;
            }
        }
        if (free == 4) {
            return new Arc(0f, 360f, true);
        }
        if (free == 0) {
            return new Arc(baseYaw, baseYaw, false);
        }
        int baseDir = Math.round(norm(baseYaw) / 90f) % 4;
        int bestStart = -1;
        int bestLen = 0;
        boolean bestHasBase = false;
        // cada tramo seguido empieza en una dirección libre cuyo anterior (circular) está bloqueado
        for (int s = 0; s < 4; s++) {
            if (blocked[s] || !blocked[(s + 3) % 4]) {
                continue;
            }
            int len = 0;
            boolean hasBase = false;
            while (len < 4 && !blocked[(s + len) % 4]) {
                if ((s + len) % 4 == baseDir) {
                    hasBase = true;
                }
                len++;
            }
            boolean better = bestStart < 0 || (hasBase && !bestHasBase) || (hasBase == bestHasBase && len > bestLen);
            if (better) {
                bestStart = s;
                bestLen = len;
                bestHasBase = hasBase;
            }
        }
        float start = bestStart * 90f - 45f + MARGIN;
        float end = (bestStart + bestLen - 1) * 90f + 45f - MARGIN;
        return new Arc(start, end, false);
    }

    /** Arco de una cámara de pared: un poco a cada lado de hacia donde mira, menos del lado en que hay un bloque pegado. */
    public static Arc wallArc(float baseYaw, boolean blockedMinus, boolean blockedPlus) {
        return new Arc(baseYaw - (blockedMinus ? WALL_BLOCKED : WALL_FREE), baseYaw + (blockedPlus ? WALL_BLOCKED : WALL_FREE), false);
    }

    private static float smooth(float s) {
        return s * s * (3f - 2f * s);
    }

    /**
     * Hacia dónde mira en el tick {@code ticks} (tiempo del mundo, con decimales para suavizar). Sin rotación mira siempre a {@code baseYaw}.
     * {@code offset} desfasa el movimiento para que dos cámaras juntas no giren idénticas.
     */
    public static float yaw(Arc arc, float baseYaw, boolean rotate, double ticks, double offset) {
        if (!rotate || arc.fixed()) {
            return arc.fixed() && rotate ? arc.start() : baseYaw;
        }
        double t = ticks + offset;
        if (arc.full()) {
            return norm(baseYaw + (float) (t * FULL_SPEED));
        }
        float span = arc.span();
        float travel = span / SWEEP_SPEED;
        float cycle = 2f * (travel + PAUSE_TICKS);
        float u = (float) (((t % cycle) + cycle) % cycle);
        float s;
        if (u < PAUSE_TICKS) {
            s = 0f;
        } else if (u < PAUSE_TICKS + travel) {
            s = smooth((u - PAUSE_TICKS) / travel);
        } else if (u < 2f * PAUSE_TICKS + travel) {
            s = 1f;
        } else {
            s = 1f - smooth((u - 2f * PAUSE_TICKS - travel) / travel);
        }
        return norm(arc.start() + span * s);
    }
}
