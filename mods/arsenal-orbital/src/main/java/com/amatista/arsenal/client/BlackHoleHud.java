package com.amatista.arsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import static com.amatista.arsenal.BlackHoleTimeline.*;
import static com.amatista.arsenal.client.HudRenderer.pulse;
import static com.amatista.arsenal.client.HudRenderer.sec;
import static com.amatista.arsenal.client.HudRenderer.text;
import static com.amatista.arsenal.client.HudRenderer.vignette;

/** Interfaz de película del "Protocolo Singularidad". */
final class BlackHoleHud {
    private static final String[] TITLES = {
            "I · AUTORIZACIÓN", "II · ASCENSO", "III · SISTEMA SOLAR", "IV · ADIÓS, VÍA LÁCTEA",
            "V · 2,5 MILLONES DE AÑOS LUZ", "VI · ANDRÓMEDA", "VII · EL AGUJERO NEGRO", "VIII · ESTACIÓN ÁNCORA",
            "IX · AGUJERO DE GUSANO", "X · SINGULARIDAD", "XI · COLAPSO"
    };
    private static final float[] STARTS = {
            0f, B1_END, B2_END, SOLAR_END, MILKY_END, INTERGALACTIC_END, ANDROMEDA_END, HOLE_ORBIT_END,
            WORMHOLE_OPEN_END, SPACE_END, IMPACT
    };

    private BlackHoleHud() {}

    static void render(GuiGraphics g, float partialTick, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float t = Cinematic.progress(partialTick);
        float secs = Cinematic.seconds(partialTick);
        if (Cinematic.isSpace(partialTick)) vignette(g, w, h, 0f, 0f, 0f, 0.4f);
        if (t >= WORMHOLE_OPEN_END && t < SPACE_END) vignette(g, w, h, 0.5f, 0.2f, 1f, 0.35f + 0.15f * (float) Math.sin(secs * 6));
        if (t >= PULL_START && t < IMPACT) vignette(g, w, h, 0.25f, 0f, 0.4f, 0.3f + 0.4f * BlackHoleDirector.seg(t, PULL_START, IMPACT));

        float bars = Math.min(Cinematic.smooth(t / 0.012f), Cinematic.smooth((1f - t) / 0.012f));
        int barH = Math.round(h * 0.11f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        if (t < B1_END + 0.01f) drawConsole(g, font, w, t, barH);

        for (int i = 1; i < TITLES.length; i++) {
            float start = STARTS[i], len = 0.022f;
            if (t >= start && t < start + len) {
                float a = Math.min(Cinematic.smooth((t - start) / 0.004f), Cinematic.smooth((start + len - t) / 0.006f));
                text(g, font, TITLES[i], 14, h - barH - 26, 0xC9A6FF, a, 1.3f, false);
            }
        }

        int x = w - 240, y = barH + 12;
        if (t >= SPACE_START && t < SOLAR_END) {
            float u = BlackHoleDirector.seg(t, SPACE_START, SOLAR_END);
            text(g, font, String.format("DISTANCIA AL SOL   %6.2f UA", 1f + 30f * u * u), x, y, 0x9FE8FF, 1f, 1f, false);
            String near = u < 0.2f ? "LA TIERRA · LA LUNA" : u < 0.42f ? "MARTE" : u < 0.68f ? "JÚPITER" : u < 0.9f ? "SATURNO" : "SALIENDO DEL SISTEMA SOLAR";
            text(g, font, near, x, y + 12, 0xFFD27F, 1f, 1f, false);
        } else if (t >= SOLAR_END && t < MILKY_END) {
            float u = BlackHoleDirector.seg(t, SOLAR_END, MILKY_END);
            text(g, font, String.format("DISTANCIA RECORRIDA  %6.0f años luz", 26000f + 120000f * u), x, y, 0x9FE8FF, 1f, 1f, false);
            text(g, font, "VÍA LÁCTEA · 200 000 MILLONES DE ESTRELLAS", x, y + 12, 0xFFD27F, 1f, 1f, false);
        } else if (t >= MILKY_END && t < ANDROMEDA_END) {
            float u = BlackHoleDirector.seg(t, MILKY_END, ANDROMEDA_END);
            text(g, font, String.format("DISTANCIA A ANDRÓMEDA  %4.2f millones a.l.", 2.5f * (1f - Cinematic.smooth(u))), x, y, 0x9FE8FF, 1f, 1f, false);
            text(g, font, String.format("VELOCIDAD  %5.0f × LA LUZ", 1000f + 250000f * BlackHoleDirector.warp(t)), x, y + 12, 0xFFD27F, 1f, 1f, false);
        } else if (t >= ANDROMEDA_END && t < WORMHOLE_OPEN_END) {
            text(g, font, "AGUJERO NEGRO SUPERMASIVO · M31*", 14, y, 0xC9A6FF, 1f, 1f, false);
            text(g, font, "140 MILLONES DE MASAS SOLARES", 14, y + 12, 0x9F8AC0, 1f, 1f, false);
            if (t >= HOLE_ORBIT_END) {
                float u = BlackHoleDirector.seg(t, HOLE_ORBIT_END, EXTRACT_END);
                int pct = Math.round(u * 100);
                text(g, font, "EXTRACCIÓN DE SINGULARIDAD  " + pct + "%", 14, y + 28, pct < 100 ? 0xFFD27F : 0x7CFF9C, 1f, 1.1f, false);
                g.fill(14, y + 42, 14 + 180, y + 48, 0x80303040);
                g.fill(14, y + 42, 14 + Math.round(180 * u), y + 48, 0xFFB57BFF);
                if (t >= EXTRACT_END) text(g, font, "AGUJERO DE GUSANO: ABIERTO", 14, y + 54, 0xB57BFF, 1f, 1.1f, false);
            }
        } else if (t >= WORMHOLE_OPEN_END && t < SPACE_END) {
            float left = (SPACE_END - t) * Cinematic.durationSeconds();
            text(g, font, String.format("LLEGADA A LA TIERRA EN  %4.1f s", left), x, y, 0xC9A6FF, 1f, 1.1f, false);
        } else if (t >= SPACE_END && t < IMPACT) {
            float left = (IMPACT - t) * Cinematic.durationSeconds();
            BlockPos tp = Cinematic.target();
            text(g, font, t < PULL_START ? "SINGULARIDAD EN DESCENSO" : "SINGULARIDAD ACTIVA · ABSORBIENDO", x, y, 0xC9A6FF, 1f, 1.05f, false);
            text(g, font, String.format("COLAPSO EN  %4.1f s", left), x, y + 14, 0xFF6060, 1f, 1.1f, false);
            text(g, font, "OBJETIVO  " + tp.getX() + "  " + tp.getY() + "  " + tp.getZ(), x, y + 30, 0x9F8AC0, 1f, 1f, false);
        }
        // cuenta regresiva final
        float window = HudRenderer.countdownWindow();
        if (t >= IMPACT - window && t < IMPACT) {
            float into = (t - (IMPACT - window)) / window * 5f;
            int n = 5 - (int) Math.floor(into);
            float frac = into - (float) Math.floor(into);
            text(g, font, String.valueOf(Math.max(1, n)), w / 2, h / 2 - 40, 0xC060FF, 1f - frac * 0.7f, 6f - frac * 1.5f, true);
        }
        if (t >= IMPACT + 0.012f && t < 0.99f) {
            float a = Math.min(Cinematic.smooth((t - IMPACT - 0.012f) / 0.01f), Cinematic.smooth((0.99f - t) / 0.01f));
            text(g, font, "SINGULARIDAD COLAPSADA", w / 2, h / 2 - 50, 0xC060FF, a, 2.4f, true);
            text(g, font, "ESTACIÓN ÁNCORA · MISIÓN CUMPLIDA", w / 2, h / 2 - 22, 0x9FE8FF, a, 1.2f, true);
        }

        float flash = 0f;
        flash = Math.max(flash, pulse(t, SPACE_START, sec(0.5f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, SOLAR_END, sec(0.2f), sec(0.5f)) * 0.8f);
        flash = Math.max(flash, pulse(t, MILKY_END, sec(0.2f), sec(0.5f)) * 0.6f);
        flash = Math.max(flash, pulse(t, EXTRACT_END, sec(0.1f), sec(0.8f)) * 0.7f);
        flash = Math.max(flash, pulse(t, WORMHOLE_OPEN_END, sec(0.3f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, SPACE_END, sec(0.35f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, IMPACT, sec(0.1f), sec(2.4f)));
        if (flash > 0.001f) {
            int c = t >= WORMHOLE_OPEN_END - 0.01f && t < SPACE_END + 0.01f ? 0xE8D8FF : 0xFFFFFF;
            g.fill(0, 0, w, h, ((int) (Mth.clamp(flash, 0f, 1f) * 255) << 24) | c);
        }
    }

    private static void drawConsole(GuiGraphics g, Font font, int w, float t, int barH) {
        BlockPos tp = Cinematic.target();
        String[] lines = {
                "> PROTOCOLO SINGULARIDAD v0.9 (EXPERIMENTAL)",
                "> ENLAZANDO CON LA ESTACIÓN «ÁNCORA» · M31...",
                "> AUTENTICANDO OPERADOR: " + Cinematic.playerName().toUpperCase(),
                "> OBJETIVO: X " + tp.getX() + "  Y " + tp.getY() + "  Z " + tp.getZ(),
                "> DISTANCIA: 2 537 000 AÑOS LUZ",
                "> MOTOR DE PLEGADO ESPACIAL... LISTO",
                "> ANCLAJE GRAVITATORIO... SINCRONIZADO",
                "> RIESGO: COLAPSO LOCAL DEL ESPACIO-TIEMPO",
                "> AUTORIZACIÓN CONCEDIDA. QUE DIOS NOS AYUDE."
        };
        float fadeOut = Cinematic.smooth((B1_END + 0.01f - t) / 0.012f);
        int x = 18, y = barH + 16;
        g.fill(x - 8, y - 8, Math.min(w - 10, x + 350), y + lines.length * 12 + 6, ((int) (0x99 * fadeOut) << 24));
        float charsPerUnit = Cinematic.durationSeconds() * 60f;
        for (int i = 0; i < lines.length; i++) {
            float start = BlackHoleDirector.consoleLineStart(i);
            if (t < start) break;
            int chars = Math.min(lines[i].length(), (int) ((t - start) * charsPerUnit));
            boolean cursor = chars < lines[i].length() || (i == lines.length - 1 && ((int) (t * 400)) % 2 == 0);
            int color = i == lines.length - 1 ? 0xE0B0FF : i == 7 ? 0xFF6060 : 0xB08CFF;
            text(g, font, lines[i].substring(0, chars) + (cursor ? "_" : ""), x, y + i * 12, color, fadeOut, 1f, false);
        }
    }
}
