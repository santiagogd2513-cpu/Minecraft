package com.arsenalatomico;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * El plano de la Base Sol Negro (la quinta arma), hecha desde cero. Medidas en el sistema de la base:
 * «a» hacia adelante (donde mira), «b» a la derecha y la altura desde la plataforma (0 = donde se pisa).
 * <pre>
 *   SUPERFICIE (plataforma de 98 × 89, cerca con portón atrás)
 *     portón a = -56 · parqueadero del camión · edificio de acceso (a -42 a -28) con la escalera
 *     silo principal (Sol Negro) en a = 16 · tres silos chiquitos a la izquierda · dos medianos a la derecha
 *
 *   NIVEL DE ABAJO (todo a la misma altura, y = -22)
 *                    [silo chico]  [silo chico]  [silo chico]        ← b = -31
 *                    [ cabina  ]   [ cabina  ]   [ cabina  ]
 *     ═════════ pasillo amarillo (silos tácticos) ═════════════    ← b -22 a -18
 *     ║ dormitorio ║ galería ║ ── enlace ── [ CENTRO DE MANDO ] ─ ventana ─ ( SILO SOL NEGRO )
 *     ║ escalera ─►║         ║ ── espina ─► [   consola       ]
 *     ║ generador  ║         ║ ── enlace ── [                 ]
 *     ═════════ pasillo naranja (silos medianos) ══════════════    ← b 18 a 22
 *                         [ cabina  ]          [ cabina  ]
 *                         [silo mediano]       [silo mediano]      ← b = 32
 * </pre>
 * Cada puerta tiene marco iluminado y letrero, y cada zona su franja de color en el piso.
 */
public final class BaseLayout {
    private BaseLayout() {}

    /** Versión del diseño (las bases viejas, iguales al búnker del Hongo, se reemplazan). */
    public static final int VERSION = 2;

    /** Altura a la que se pisa en el nivel de abajo. */
    public static final int FLOOR = -22;

    /** Los silos: 0 = Sol Negro, 1–3 = chiquitos, 4–5 = medianos. {a, b, radio, hondo}. */
    public static final double[][] SILOS = {
            {16, 0, 6, 46},
            {-4, -31, 2.5, 24}, {6, -31, 2.5, 24}, {16, -31, 2.5, 24},
            {0, 32, 3.5, 32}, {14, 32, 3.5, 32},
    };
    public static final int COUNT = SILOS.length;

    /** Tamaño de la bomba de cada silo (1 = Protocolo Hongo). */
    public static double size(int silo) {
        return silo == 0 ? 1.45 : silo <= 3 ? 0.55 : 0.85;
    }

    /** Largo del misil de cada silo, en bloques. */
    public static double missileLength(int silo) {
        return silo == 0 ? 28 : silo <= 3 ? 18 * 0.55 : 18 * 0.75;
    }

    /** Escala del modelo del misil chico y mediano (el del Hongo). */
    public static float missileScale(int silo) {
        return silo == 0 ? 1f : silo <= 3 ? 0.55f : 0.75f;
    }

    /** Lo más cerca de la base que puede caer cada bomba. */
    public static int safe(int silo) {
        return (int) Math.round(NukePlan.SCORCH_R * size(silo)) + 60;
    }

    public static boolean small(int silo) {
        return silo >= 1 && silo <= 3;
    }

    public static boolean medium(int silo) {
        return silo >= 4;
    }

    /** Dónde se para el jugador en la cabina de cada silo (frente a su panel). */
    public static double[] panel(int silo) {
        double[] s = SILOS[silo];
        return new double[]{s[0], s[1] < 0 ? -25 : 25};
    }

    /** La consola del Sol Negro en el centro de mando. */
    public static final int CONSOLE_A = 1, CONSOLE_B = 0;

    /** La puerta del edificio de acceso (por fuera) y el parqueadero del camión. */
    public static final int DOOR_A = -44, PARK_A = -49, PARK_B = 12, GATE_A = -56;

    /** Del sistema de la base al mundo (alineado a bloques; «site» es el centro de la plataforma). */
    public static Vec3 local(Vec3 site, float yaw, double a, double b, double y) {
        return NukePlan.local(new Vec3(Mth.floor(site.x) + 0.5, Mth.floor(site.y), Mth.floor(site.z) + 0.5), yaw, a, b, y);
    }

    /** Lo más bajo que puede quedar la plataforma (para que el silo principal quepa en el mundo). */
    public static double rimHeight(double minBuild, double ground) {
        return Math.max(ground, minBuild + 6 + SILOS[0][3]);
    }
}
