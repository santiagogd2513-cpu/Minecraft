package com.example.beautifuldecor.client;

/**
 * La cuenta del repintado de vehículos (V3.2.0), sin nada de Minecraft para poder probarla suelta.
 * <p>
 * Cada vehículo tiene, junto a su textura, una máscara {@code NOMBRE_paint.png} (la genera tools/v320/paint_masks.py):
 * en el byte rojo, verde y azul de cada píxel guarda cuánto depende ese píxel de la pintura, de las manijas y de los rines
 * (valor = k * 100; 0 = nada, 100 = color liso del canal, más = tono claro, menos = tono oscuro). El color nuevo se aplica así:
 * <pre>salida = original + k * (color_nuevo - color_original)</pre>
 * Con el color original da la textura de siempre; con otro conserva el relieve, el ruido y las líneas de panel.
 * <p>
 * V3.6.0, "Otros": una segunda máscara {@code NOMBRE_regions.png} (tools/v360/extra_regions.py) dice, en el byte rojo, de qué región
 * es cada píxel (1 = la primera parte de la lista del vehículo, 0 = ninguna) y en el verde su k * 100. Es la misma cuenta, con el color
 * del canal {@code 3 + región - 1}. Los píxeles de neumáticos y de hélices o rotor no tienen región: nunca cambian de color.
 * Los píxeles van en el formato de NativeImage: 0xAABBGGRR.
 */
public final class PaintMath {
    private static final float SCALE = 100.0f;
    /** Canales con máscara propia (pintura, manijas, rines): el resto vienen de las regiones. */
    private static final int MAIN = 3;

    private PaintMath() {
    }

    /** Sin regiones de "Otros": solo los tres canales de siempre. */
    public static int[] recolor(int[] base, int[] mask, int[] ref, int[] colors) {
        return recolor(base, mask, null, ref, colors);
    }

    /**
     * @param base    textura original (0xAABBGGRR)
     * @param mask    máscara del mismo tamaño: R = pintura, G = manijas, B = rines (k * 100)
     * @param regions máscara de "Otros" del mismo tamaño (R = región, G = k * 100) o null si el vehículo no tiene
     * @param ref     colores originales 0xRRGGBB de cada canal (-1 = el vehículo no tiene ese canal)
     * @param colors  colores pedidos 0xRRGGBB de cada canal
     */
    public static int[] recolor(int[] base, int[] mask, int[] regions, int[] ref, int[] colors) {
        int channels = Math.min(ref.length, colors.length);
        int[] out = new int[base.length];
        float[][] delta = new float[channels][3];
        boolean[] active = new boolean[channels];
        for (int ch = 0; ch < channels; ch++) {
            if (ref[ch] >= 0 && colors[ch] >= 0 && (ref[ch] & 0xFFFFFF) != (colors[ch] & 0xFFFFFF)) {
                active[ch] = true;
                delta[ch][0] = ((colors[ch] >> 16) & 255) - ((ref[ch] >> 16) & 255);
                delta[ch][1] = ((colors[ch] >> 8) & 255) - ((ref[ch] >> 8) & 255);
                delta[ch][2] = (colors[ch] & 255) - (ref[ch] & 255);
            }
        }
        for (int i = 0; i < base.length; i++) {
            int p = base[i];
            int m = mask[i];
            int rm = regions != null ? regions[i] : 0;
            if (((m & 0xFFFFFF) == 0 && (rm & 0xFFFF) == 0) || (p >>> 24) == 0) {
                out[i] = p;
                continue;
            }
            float r = p & 255;
            float g = (p >> 8) & 255;
            float b = (p >> 16) & 255;
            for (int ch = 0; ch < MAIN && ch < channels; ch++) {
                int kb = (m >> (8 * ch)) & 255;
                if (kb != 0 && active[ch]) {
                    float k = kb / SCALE;
                    r += k * delta[ch][0];
                    g += k * delta[ch][1];
                    b += k * delta[ch][2];
                }
            }
            int region = rm & 255;
            if (region != 0) {
                int ch = MAIN + region - 1;
                int kb = (rm >> 8) & 255;
                if (ch < channels && kb != 0 && active[ch]) {
                    float k = kb / SCALE;
                    r += k * delta[ch][0];
                    g += k * delta[ch][1];
                    b += k * delta[ch][2];
                }
            }
            out[i] = (p & 0xFF000000) | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r);
        }
        return out;
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}
