package com.amatista.arsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import static com.amatista.arsenal.GenesisTimeline.*;
import static com.amatista.arsenal.client.HudRenderer.pulse;
import static com.amatista.arsenal.client.HudRenderer.sec;
import static com.amatista.arsenal.client.HudRenderer.text;
import static com.amatista.arsenal.client.HudRenderer.vignette;

/** Interfaz de película del "Protocolo Génesis". */
final class GenesisHud {
    private static final String[] TITLES = {
            "I · LA ÚLTIMA ORDEN", "II · ASCENSO", "III · ADIÓS, SOL", "IV · NUESTRA GALAXIA",
            "V · LA RED CÓSMICA", "VI · EL BORDE DEL UNIVERSO", "VII · MÁS ALLÁ", "VIII · GÉNESIS DESPIERTA",
            "IX · LA COSECHA DE GALAXIAS", "X · LA FORJA", "XI · LA CHISPA DE LA CREACIÓN", "XII · EL VIAJE DE VUELTA",
            "XIII · CASA", "XIV · FLORECIMIENTO", "XV · UNA NUEVA ESTRELLA"
    };
    private static final float[] STARTS = {
            0f, G1_END, SPACE_START, SOLAR_END, HOME_END, WEB_END, EDGE_END, BEYOND_END,
            AWAKEN_END, HARVEST_END, FORGE_END, LAUNCH_END, RETURN_TRIP_END, BLOOM_START, IMPACT
    };
    private static final String[][] POEM = {
            {"Antes del tiempo no había nada.", WEB_END + ""},
            {"Y de la nada nació la luz.", WEB_END + 0.025f + ""},
            {"Lo que ves es el eco del Big Bang:", EDGE_END - 0.035f + ""},
            {"13 800 millones de años de antigüedad.", EDGE_END - 0.02f + ""},
            {"Aquí termina todo lo que existe.", BEYOND_END - 0.05f + ""},
            {"…y aquí empieza Génesis.", BEYOND_END - 0.02f + ""},
    };

    private GenesisHud() {}

    static void render(GuiGraphics g, float partialTick, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float t = Cinematic.progress(partialTick);
        float secs = Cinematic.seconds(partialTick);
        if (Cinematic.isSpace(partialTick)) vignette(g, w, h, 0f, 0f, 0f, 0.4f);
        if (t >= BEYOND_END && t < FORGE_END) {
            float[] c = GenesisRenderer.hue(secs * 0.03f);
            vignette(g, w, h, c[0], c[1], c[2], 0.12f);
        }
        if (t >= SPACE_END && t < RETURN_START) {
            float[] c = GenesisRenderer.hue(secs * 0.1f);
            vignette(g, w, h, c[0], c[1], c[2], 0.22f + 0.1f * (float) Math.sin(secs * 3));
        }

        float bars = Math.min(Cinematic.smooth(t / 0.01f), Cinematic.smooth((1f - t) / 0.01f));
        int barH = Math.round(h * 0.11f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        if (t < G1_END + 0.008f) drawConsole(g, font, w, t, barH);
        for (int i = 1; i < TITLES.length; i++) {
            float start = STARTS[i], len = 0.016f;
            if (t >= start && t < start + len) {
                float a = Math.min(Cinematic.smooth((t - start) / 0.003f), Cinematic.smooth((start + len - t) / 0.005f));
                text(g, font, TITLES[i], 14, h - barH - 26, i >= 7 ? 0xFFE3A0 : 0xD8B8FF, a, 1.3f, false);
            }
        }

        // poema del borde del universo (centrado)
        for (String[] line : POEM) {
            float at = Float.parseFloat(line[1]), len = 0.022f;
            if (t >= at && t < at + len) {
                float a = Math.min(Cinematic.smooth((t - at) / 0.005f), Cinematic.smooth((at + len - t) / 0.006f));
                text(g, font, line[0], w / 2, h / 2 + 40, 0xF2E8FF, a, 1.5f, true);
            }
        }

        int x = w - 260, y = barH + 12;
        if (t >= SPACE_START && t < BEYOND_END) {
            float u = GenesisDirector.seg(t, SPACE_START, EDGE_END);
            double ly = Math.pow(10, 0.5 + 9.64 * u);
            String d = ly < 1e6 ? String.format("%,.0f años luz", ly) : ly < 1e9 ? String.format("%,.1f millones de años luz", ly / 1e6)
                    : String.format("%,.2f mil millones de años luz", ly / 1e9);
            if (t >= EDGE_END) d = "FUERA DEL UNIVERSO OBSERVABLE";
            text(g, font, "DISTANCIA  " + d, x, y, 0xD8C8FF, 1f, 1f, false);
            text(g, font, String.format("VELOCIDAD  %,.0f × LA LUZ", 1e3 + 9e10 * GenesisDirector.warp(t) * u), x, y + 12, 0xFFD27F, 1f, 1f, false);
            if (t >= HOME_END && t < WEB_END) text(g, font, "CADA PUNTO DE LUZ ES UNA GALAXIA", x, y + 24, 0x9F9FC0, 1f, 1f, false);
            if (t >= WEB_END && t < EDGE_END) text(g, font, "TEMPERATURA DEL FONDO  2,725 K", x, y + 24, 0xFFB080, 1f, 1f, false);
        }
        if (t >= BEYOND_END && t < FORGE_END) {
            text(g, font, "«GÉNESIS» · LA MÁQUINA QUE CREA ESTRELLAS", 14, y, 0xFFE3A0, 1f, 1.05f, false);
            text(g, font, "EDAD: DESCONOCIDA · CONSTRUCTOR: DESCONOCIDO", 14, y + 13, 0xC0A8E0, 1f, 1f, false);
            if (t >= AWAKEN_END) {
                float hv = GenesisDirector.harvest(t);
                int gal = Math.min(GenesisDirector.HARVEST_GALAXIES, (int) Math.floor(hv * GenesisDirector.HARVEST_GALAXIES * 1.05f));
                text(g, font, "GALAXIAS COSECHADAS  " + gal + " / " + GenesisDirector.HARVEST_GALAXIES, 14, y + 30, 0x9FE8FF, 1f, 1.05f, false);
                text(g, font, String.format("ESTRELLAS ABSORBIDAS  %,.0f", 1.2e12 * Math.pow(hv, 1.3)), 14, y + 43, 0x9FE8FF, 1f, 1f, false);
            }
            if (t >= HARVEST_END) {
                float f = GenesisDirector.forge(t);
                int pct = Math.round(f * 100);
                text(g, font, "COMPRESIÓN DE LA CHISPA  " + pct + "%", 14, y + 60, pct < 100 ? 0xFFD27F : 0x7CFF9C, 1f, 1.05f, false);
                g.fill(14, y + 74, 14 + 220, y + 81, 0x80303040);
                int segs = 22;
                for (int k = 0; k < Math.round(segs * f); k++) {
                    float[] c = GenesisRenderer.hue(k / (float) segs);
                    int col = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
                    g.fill(14 + k * 10, y + 74, 14 + k * 10 + 9, y + 81, col);
                }
            }
        }
        float window = HudRenderer.countdownWindow();
        if (t >= FORGE_END - window && t < FORGE_END) {
            float into = (t - (FORGE_END - window)) / window * 5f;
            int n = 5 - (int) Math.floor(into);
            float frac = into - (float) Math.floor(into);
            text(g, font, "QUE SE HAGA LA LUZ", w / 2, h / 2 - 72, 0xFFE3A0, 1f, 1.4f, true);
            float[] c = GenesisRenderer.hue(n / 5f);
            int col = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            text(g, font, String.valueOf(Math.max(1, n)), w / 2, h / 2 - 40, col, 1f - frac * 0.7f, 6f - frac * 1.5f, true);
        }
        if (t >= FORGE_END && t < SPACE_END) {
            float left = (IMPACT - t) * Cinematic.durationSeconds();
            text(g, font, "LA CHISPA VIAJA HACIA LA TIERRA", x, y, 0xFFE3A0, 1f, 1.05f, false);
            text(g, font, String.format("LLEGADA EN  %4.1f s", left), x, y + 13, 0xD8C8FF, 1f, 1f, false);
        }
        if (t >= SPACE_END && t < IMPACT) {
            BlockPos tp = Cinematic.target();
            float left = (IMPACT - t) * Cinematic.durationSeconds();
            text(g, font, t < BLOOM_START ? "LA CHISPA DESCIENDE" : "LA TIERRA SE CRISTALIZA", x, y, 0xFFE3A0, 1f, 1.05f, false);
            text(g, font, String.format("NACIMIENTO EN  %4.1f s", left), x, y + 14, 0xFF9AE0, 1f, 1.1f, false);
            text(g, font, "OBJETIVO  " + tp.getX() + "  " + tp.getY() + "  " + tp.getZ(), x, y + 30, 0xC0A8E0, 1f, 1f, false);
        }
        if (t >= IMPACT + 0.01f && t < 0.992f) {
            float a = Math.min(Cinematic.smooth((t - IMPACT - 0.01f) / 0.01f), Cinematic.smooth((0.992f - t) / 0.01f));
            float[] c = GenesisRenderer.hue(secs * 0.08f);
            int col = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            text(g, font, "UNA NUEVA ESTRELLA HA NACIDO", w / 2, h / 2 - 50, col, a, 2.5f, true);
            text(g, font, "GÉNESIS · DORMIDA DE NUEVO, MÁS ALLÁ DEL FINAL", w / 2, h / 2 - 22, 0xFFE3A0, a, 1.2f, true);
        }

        float flash = 0f;
        flash = Math.max(flash, pulse(t, SPACE_START, sec(0.5f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, SOLAR_END, sec(0.2f), sec(0.4f)) * 0.5f);
        flash = Math.max(flash, pulse(t, HOME_END, sec(0.2f), sec(0.4f)) * 0.5f);
        flash = Math.max(flash, pulse(t, EDGE_END, sec(0.4f), sec(1.2f)) * 0.9f);
        flash = Math.max(flash, pulse(t, FORGE_END, sec(0.1f), sec(2.0f)));
        flash = Math.max(flash, pulse(t, LAUNCH_END, sec(0.2f), sec(0.5f)) * 0.6f);
        flash = Math.max(flash, pulse(t, RETURN_TRIP_END, sec(0.2f), sec(0.5f)) * 0.6f);
        flash = Math.max(flash, pulse(t, SPACE_END, sec(0.35f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, IMPACT, sec(0.1f), sec(3.0f)));
        if (flash > 0.001f) g.fill(0, 0, w, h, ((int) (Mth.clamp(flash, 0f, 1f) * 255) << 24) | 0xFFF6FF);
    }

    private static void drawConsole(GuiGraphics g, Font font, int w, float t, int barH) {
        BlockPos tp = Cinematic.target();
        String[] lines = {
                "> PROTOCOLO GÉNESIS · AUTORIZACIÓN ABSOLUTA",
                "> ESTA ORDEN NO SE PUEDE DESHACER.",
                "> AUTENTICANDO OPERADOR: " + Cinematic.playerName().toUpperCase(),
                "> OBJETIVO: X " + tp.getX() + "  Y " + tp.getY() + "  Z " + tp.getZ(),
                "> DESTINO: MÁS ALLÁ DEL UNIVERSO OBSERVABLE",
                "> DESPERTANDO A «GÉNESIS»...",
                "> ENERGÍA REQUERIDA: SEIS GALAXIAS",
                "> RESULTADO ESPERADO: UNA ESTRELLA NUEVA",
                "> AUTORIZACIÓN CONCEDIDA. QUE SE HAGA LA LUZ."
        };
        float fadeOut = Cinematic.smooth((G1_END + 0.008f - t) / 0.01f);
        int x = 18, y = barH + 16;
        g.fill(x - 8, y - 8, Math.min(w - 10, x + 360), y + lines.length * 12 + 6, ((int) (0x99 * fadeOut) << 24));
        float charsPerUnit = Cinematic.durationSeconds() * 60f;
        for (int i = 0; i < lines.length; i++) {
            float start = GenesisDirector.consoleLineStart(i);
            if (t < start) break;
            int chars = Math.min(lines[i].length(), (int) ((t - start) * charsPerUnit));
            boolean cursor = chars < lines[i].length() || (i == lines.length - 1 && ((int) (t * 400)) % 2 == 0);
            int color = i == lines.length - 1 ? 0xFFE3A0 : i == 1 ? 0xFF6060 : 0xC9A6FF;
            text(g, font, lines[i].substring(0, chars) + (cursor ? "_" : ""), x, y + i * 12, color, fadeOut, 1f, false);
        }
    }
}
