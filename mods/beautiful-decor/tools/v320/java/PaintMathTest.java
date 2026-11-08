package com.example.beautifuldecor.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Prueba de PaintMath (V3.2.0): con las texturas y máscaras REALES del mod, el Java tiene que dar lo mismo que la fórmula
 * de Python (tools/v320/paint_masks.py apply). Uso: PaintMathTest <carpeta de texturas> <archivo de casos>
 * Cada línea de casos: nombre refCuerpo refManijas refRines nuevoCuerpo nuevoManijas nuevoRines esperado.png (colores en hex, - = no tiene).
 */
public final class PaintMathTest {
    static int[] pixels(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24;
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                out[y * w + x] = (a << 24) | (b << 16) | (g << 8) | r;     // el formato de NativeImage
            }
        }
        return out;
    }

    static int color(String s) {
        return s.equals("-") ? -1 : Integer.parseInt(s, 16);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        List<String> lines = Files.readAllLines(new File(args[1]).toPath());
        int fails = 0;
        int cases = 0;
        long worstOverall = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            int[] base = pixels(ImageIO.read(new File(dir, t[0] + ".png")));
            int[] mask = pixels(ImageIO.read(new File(dir, t[0] + "_paint.png")));
            int[] ref = {color(t[1]), color(t[2]), color(t[3])};
            int[] news = {color(t[4]), color(t[5]), color(t[6])};
            int[] want = pixels(ImageIO.read(new File(t[7])));
            int[] got = PaintMath.recolor(base, mask, ref, news);
            int worst = 0;
            int bad = 0;
            for (int i = 0; i < got.length; i++) {
                int d = 0;
                for (int sh = 0; sh < 32; sh += 8) {
                    d = Math.max(d, Math.abs(((got[i] >>> sh) & 255) - ((want[i] >>> sh) & 255)));
                }
                worst = Math.max(worst, d);
                if (d > 1) {
                    bad++;
                }
            }
            worstOverall = Math.max(worstOverall, worst);
            cases++;
            if (bad > 0) {
                fails++;
                System.out.println("FALLA " + t[0] + " " + line + ": " + bad + " píxeles con diferencia > 1 (peor " + worst + ")");
            }
        }
        // con los colores originales sale la textura de siempre, sin cambiar un solo píxel
        for (String name : new String[] {"motorcycle", "bicycle", "ford_f150", "delivery_truck", "helicopter"}) {
            int[] base = pixels(ImageIO.read(new File(dir, name + ".png")));
            int[] mask = pixels(ImageIO.read(new File(dir, name + "_paint.png")));
            int[][] refs = {{0xC81018, -1, 0xCE121A}, {0x26A8B8, -1, 0xC4C8D0}, {0x122664, 0xB0B6C2, 0xCED2DA},
                    {0xECEEF4, 0xB0B6C2, 0xCED2DA}, {0xEAECF2, 0xB0B6C2, -1}};
            int idx = java.util.Arrays.asList("motorcycle", "bicycle", "ford_f150", "delivery_truck", "helicopter").indexOf(name);
            int[] same = PaintMath.recolor(base, mask, refs[idx], refs[idx]);
            if (!java.util.Arrays.equals(same, base)) {
                fails++;
                System.out.println("FALLA identidad en " + name);
            }
            // un color pedido para un canal que el vehículo no tiene no cambia nada
            int[] ask = {refs[idx][0], refs[idx][1] < 0 ? 0xFF00FF : refs[idx][1], refs[idx][2] < 0 ? 0x00FFFF : refs[idx][2]};
            int[] ignored = PaintMath.recolor(base, mask, refs[idx], ask);
            if (!java.util.Arrays.equals(ignored, base)) {
                fails++;
                System.out.println("FALLA: un canal inexistente cambió la textura de " + name);
            }
        }
        if (fails == 0) {
            System.out.println("PAINT MATH OK (" + cases + " casos, peor diferencia " + worstOverall + " de 255)");
        } else {
            System.exit(1);
        }
    }
}
