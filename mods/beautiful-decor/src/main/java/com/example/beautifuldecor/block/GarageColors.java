package com.example.beautifuldecor.block;

/**
 * Colores de la puerta de garaje (V2.3.0). Tres ranuras que el jugador puede cambiar: la puerta, la franja y el marco.
 * V3.6.0, botón "Otros": cinco ranuras más para el resto de las partes de la puerta: ventanas, manija y cerradura, sello de goma,
 * bisagras y rodillos, y rejillas del cajón del motor. Estas no llevan margen K (su color por defecto es el más claro de sus
 * píxeles; ver tools/v230/garage_colors.py).
 * <p>
 * Las texturas de la puerta están dibujadas para tintarse: cada capa guarda pixel / (color por defecto * K) y el tinte
 * que se manda al modelo vale color * K (un tinte solo puede oscurecer, K deja margen para los brillos). Con los colores
 * por defecto la puerta se ve exactamente como antes. Los valores de K y los colores por defecto tienen que ser los
 * mismos que en tools/v230/garage_colors.py.
 * <p>
 * Índices de tinte de los modelos: 0 = puerta, 1 = franja, 2 = marco, 3 = cara interior (sale del color de la puerta),
 * 4 = ventanas por fuera, 5 = ventanas por dentro (salen del color de las ventanas), 6 = manija y cerradura, 7 = bisagras y rodillos,
 * 8 = sello de goma, 9 = rejillas del cajón.
 */
public final class GarageColors {
    public static final int BODY = 0;
    public static final int ACCENT = 1;
    public static final int FRAME = 2;
    /** V3.6.0: "Otros". */
    public static final int WINDOW = 3;
    public static final int HANDLE = 4;
    public static final int SEAL = 5;
    public static final int HARDWARE = 6;
    public static final int VENTS = 7;
    public static final int SLOTS = 8;
    /** Las ranuras de "Otros" empiezan aquí. */
    public static final int OTHERS_FIRST = WINDOW;

    public static final int DEFAULT_BODY = 0x2E3139;
    public static final int DEFAULT_ACCENT = 0xCE1C24;
    public static final int DEFAULT_FRAME = 0x1E2025;
    /** Color de la cara interior cuando la puerta tiene el color por defecto. */
    public static final int DEFAULT_INSIDE = 0x787E89;

    public static final int DEFAULT_WINDOW = 0x607C98;
    /** Color de las ventanas vistas desde dentro cuando las de fuera tienen el color por defecto. */
    public static final int DEFAULT_WINDOW_INSIDE = 0xCCF0FF;
    public static final int DEFAULT_HANDLE = 0xD6DAE2;
    public static final int DEFAULT_SEAL = 0x202024;
    public static final int DEFAULT_HARDWARE = 0xB0B4BE;
    public static final int DEFAULT_VENTS = 0x0E0E11;

    public static final double K_BODY = 1.45;
    public static final double K_ACCENT = 1.0;
    public static final double K_FRAME = 1.15;
    public static final double K_INSIDE = 1.2;

    private GarageColors() {
    }

    public static int[] defaults() {
        return new int[]{DEFAULT_BODY, DEFAULT_ACCENT, DEFAULT_FRAME, DEFAULT_WINDOW, DEFAULT_HANDLE, DEFAULT_SEAL,
                DEFAULT_HARDWARE, DEFAULT_VENTS};
    }

    private static int clamp(long v) {
        return (int) Math.max(0L, Math.min(255L, v));
    }

    /** Multiplica por k; si algún canal se pasa de 255 baja los tres por igual (así no cambia el tono). */
    private static int scaled(int color, double k) {
        double r = ((color >> 16) & 255) * k;
        double g = ((color >> 8) & 255) * k;
        double b = (color & 255) * k;
        double max = Math.max(r, Math.max(g, b));
        if (max > 255.0) {
            double s = 255.0 / max;
            r *= s;
            g *= s;
            b *= s;
        }
        return (clamp(Math.round(r)) << 16) | (clamp(Math.round(g)) << 8) | clamp(Math.round(b));
    }

    /** Color de la cara de adentro: el de la puerta aclarado, de modo que con el color por defecto salga DEFAULT_INSIDE. */
    public static int inside(int body) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int b = (body >> shift) & 255;
            int db = (DEFAULT_BODY >> shift) & 255;
            int di = (DEFAULT_INSIDE >> shift) & 255;
            double f = (di - db) / (255.0 - db);
            out |= clamp(Math.round(b + (255 - b) * f)) << shift;
        }
        return out;
    }

    /** V3.6.0: color de las ventanas vistas desde dentro: el de fuera aclarado, de modo que con el por defecto salga DEFAULT_WINDOW_INSIDE. */
    public static int windowInside(int window) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (window >> shift) & 255;
            int dw = (DEFAULT_WINDOW >> shift) & 255;
            int di = (DEFAULT_WINDOW_INSIDE >> shift) & 255;
            double f = (di - dw) / (255.0 - dw);
            out |= clamp(Math.round(c + (255 - c) * f)) << shift;
        }
        return out;
    }

    /** Nombre de la ranura (clave de idioma gui.beautifuldecor.garage.NOMBRE). */
    public static String slotKey(int slot) {
        return switch (slot) {
            case BODY -> "body";
            case ACCENT -> "accent";
            case FRAME -> "frame";
            case WINDOW -> "window";
            case HANDLE -> "handle";
            case SEAL -> "seal";
            case HARDWARE -> "hardware";
            default -> "vents";
        };
    }

    /** Tinte (0xRRGGBB) para el tintindex del modelo. */
    public static int tint(int tintIndex, int[] colors) {
        return switch (tintIndex) {
            case 0 -> scaled(colors[BODY], K_BODY);
            case 1 -> scaled(colors[ACCENT], K_ACCENT);
            case 2 -> scaled(colors[FRAME], K_FRAME);
            case 3 -> scaled(inside(colors[BODY]), K_INSIDE);
            case 4 -> colors[WINDOW] & 0xFFFFFF;
            case 5 -> windowInside(colors[WINDOW]);
            case 6 -> colors[HANDLE] & 0xFFFFFF;
            case 7 -> colors[HARDWARE] & 0xFFFFFF;
            case 8 -> colors[SEAL] & 0xFFFFFF;
            case 9 -> colors[VENTS] & 0xFFFFFF;
            default -> 0xFFFFFF;
        };
    }

    /** Color que se ve de verdad en la puerta para el color elegido en una ranura (los muy claros salen algo apagados). */
    public static int visible(int slot, int color) {
        if (slot >= OTHERS_FIRST) {
            return color & 0xFFFFFF;        // sin margen K: sale tal cual
        }
        double k = slot == BODY ? K_BODY : slot == FRAME ? K_FRAME : K_ACCENT;
        int t = scaled(color, k);
        return (clamp(Math.round(((t >> 16) & 255) / k)) << 16) | (clamp(Math.round(((t >> 8) & 255) / k)) << 8)
                | clamp(Math.round((t & 255) / k));
    }
}
