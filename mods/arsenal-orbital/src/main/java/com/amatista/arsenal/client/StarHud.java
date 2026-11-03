package com.amatista.arsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import static com.amatista.arsenal.StarTimeline.*;
import static com.amatista.arsenal.client.HudRenderer.pulse;
import static com.amatista.arsenal.client.HudRenderer.sec;
import static com.amatista.arsenal.client.HudRenderer.text;
import static com.amatista.arsenal.client.HudRenderer.vignette;

/** Interfaz de película del "Protocolo Prometeo". */
final class StarHud {
    private static final String[] TITLES = {
            "I · AUTORIZACIÓN", "II · ASCENSO", "III · ADIÓS, SISTEMA SOLAR", "IV · FUERA DE LA VÍA LÁCTEA",
            "V · ANDRÓMEDA", "VI · EL CÚMULO DE VIRGO", "VII · LA MITAD DE LA NADA", "VIII · «ZAFIRO»",
            "IX · LA SUPERFICIE", "X · ESTACIÓN PROMETEO", "XI · ROBANDO UNA ESTRELLA", "XII · DISPARO",
            "XIII · A TRAVÉS DEL UNIVERSO", "XIV · IMPACTO", "XV · CONSECUENCIAS"
    };
    private static final float[] STARTS = {
            0f, S1_END, S2_END, SOLAR_END, MILKY_END, ANDROMEDA_END, FIELD_END, VOID_END,
            APPROACH_END, FLYBY_END, STATION_END, CHARGE_END, FIRE_END, SPACE_END, IMPACT
    };

    private StarHud() {}

    static void render(GuiGraphics g, float partialTick, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float t = Cinematic.progress(partialTick);
        float secs = Cinematic.seconds(partialTick);
        if (Cinematic.isSpace(partialTick)) vignette(g, w, h, 0f, 0f, 0f, 0.4f);
        if (t >= VOID_END && t < CHARGE_END) vignette(g, w, h, 0.2f, 0.35f, 0.8f, 0.18f);
        if (t >= SPACE_END && t < IMPACT) vignette(g, w, h, 0.5f, 0.75f, 1f, 0.3f + 0.2f * (float) Math.sin(secs * 12));

        float bars = Math.min(Cinematic.smooth(t / 0.012f), Cinematic.smooth((1f - t) / 0.012f));
        int barH = Math.round(h * 0.11f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        if (t < S1_END + 0.01f) drawConsole(g, font, w, t, barH);
        for (int i = 1; i < TITLES.length; i++) {
            float start = STARTS[i], len = 0.02f;
            if (t >= start && t < start + len) {
                float a = Math.min(Cinematic.smooth((t - start) / 0.004f), Cinematic.smooth((start + len - t) / 0.006f));
                text(g, font, TITLES[i], 14, h - barH - 26, 0x9FD8FF, a, 1.3f, false);
            }
        }

        int x = w - 250, y = barH + 12;
        if (t >= SPACE_START && t < VOID_END) {
            float u = StarDirector.seg(t, SPACE_START, VOID_END);
            double ly = Math.pow(10, 1 + 8.6 * u);
            String d = ly < 1e6 ? String.format("%,.0f años luz", ly) : String.format("%,.1f millones de años luz", ly / 1e6);
            text(g, font, "DISTANCIA  " + d, x, y, 0x9FE8FF, 1f, 1f, false);
            text(g, font, String.format("VELOCIDAD  %,.0f × LA LUZ", 1e3 + 4e9 * StarDirector.warp(t) * u), x, y + 12, 0xFFD27F, 1f, 1f, false);
            if (t >= FIELD_END) text(g, font, "NO HAY NADA EN 300 MILLONES DE AÑOS LUZ", x, y + 24, 0x7F8FA0, 1f, 1f, false);
        }
        if (t >= VOID_END && t < STATION_END) {
            text(g, font, "«ZAFIRO» · HIPERGIGANTE AZUL", 14, y, 0x9FD8FF, 1f, 1.05f, false);
            text(g, font, "180 MASAS SOLARES · 45 000 K · 2 MILLONES DE SOLES", 14, y + 13, 0x8FB0D0, 1f, 1f, false);
        }
        if (t >= FLYBY_END && t < SPACE_END) {
            text(g, font, "ESTACIÓN PROMETEO · CAÑÓN DE FOTONES ESTELAR", 14, t >= STATION_END ? y : y + 30, 0xFFD27F, 1f, 1f, false);
        }
        if (t >= STATION_END && t < CHARGE_END) {
            float u = StarDirector.seg(t, STATION_END, CHARGE_END);
            int pct = Math.round(StarDirector.charge(t) * 100);
            text(g, font, "ENERGÍA ABSORBIDA DE LA ESTRELLA  " + pct + "%", 14, y + 16, pct < 100 ? 0x9FE8FF : 0x7CFF9C, 1f, 1.05f, false);
            g.fill(14, y + 30, 14 + 220, y + 37, 0x80303040);
            g.fill(14, y + 30, 14 + Math.round(220 * StarDirector.charge(t)), y + 37, 0xFF6FC8FF);
            int caps = Mth.clamp((int) Math.floor((u - 0.4f) / 0.035f) + 1, 0, 8);
            StringBuilder sb = new StringBuilder("CONDENSADORES  ");
            for (int k = 0; k < 8; k++) sb.append(k < caps ? "■ " : "□ ");
            text(g, font, sb.toString(), 14, y + 42, 0x9FD8FF, 1f, 1f, false);
        }
        float window = HudRenderer.countdownWindow();
        if (t >= CHARGE_END - window && t < CHARGE_END) {
            float into = (t - (CHARGE_END - window)) / window * 5f;
            int n = 5 - (int) Math.floor(into);
            float frac = into - (float) Math.floor(into);
            text(g, font, "DISPARO EN", w / 2, h / 2 - 72, 0x9FD8FF, 1f, 1.4f, true);
            text(g, font, String.valueOf(Math.max(1, n)), w / 2, h / 2 - 40, 0x60C0FF, 1f - frac * 0.7f, 6f - frac * 1.5f, true);
        }
        if (t >= CHARGE_END && t < SPACE_END) {
            float left = (BURN_START - t) * Cinematic.durationSeconds();
            text(g, font, String.format("LLEGADA DEL RAYO A LA TIERRA EN  %4.1f s", left), x, y, 0x9FD8FF, 1f, 1.05f, false);
        }
        if (t >= SPACE_END && t < IMPACT) {
            BlockPos tp = Cinematic.target();
            float left = (IMPACT - t) * Cinematic.durationSeconds();
            text(g, font, t < BURN_START ? "EL RAYO ESTÁ LLEGANDO" : "EL RAYO ESTÁ DERRITIENDO EL SUELO", x, y, 0x9FD8FF, 1f, 1.05f, false);
            text(g, font, String.format("DETONACIÓN EN  %4.1f s", left), x, y + 14, 0xFF6060, 1f, 1.1f, false);
            text(g, font, "OBJETIVO  " + tp.getX() + "  " + tp.getY() + "  " + tp.getZ(), x, y + 30, 0x8FB0D0, 1f, 1f, false);
        }
        if (t >= IMPACT + 0.012f && t < 0.99f) {
            float a = Math.min(Cinematic.smooth((t - IMPACT - 0.012f) / 0.01f), Cinematic.smooth((0.99f - t) / 0.01f));
            text(g, font, "OBJETIVO VAPORIZADO", w / 2, h / 2 - 50, 0x60C0FF, a, 2.5f, true);
            text(g, font, "PROMETEO · RECARGANDO DESDE «ZAFIRO»", w / 2, h / 2 - 22, 0xFFD27F, a, 1.2f, true);
        }

        float flash = 0f;
        // salida al espacio: sin destello blanco, el cielo ya está negro y la escena aparece desde la oscuridad
        HudRenderer.spaceDip(g, w, h, pulse(t, SPACE_START, sec(0.6f), sec(1.8f)));
        flash = Math.max(flash, pulse(t, SOLAR_END, sec(0.2f), sec(0.4f)) * 0.6f);
        flash = Math.max(flash, pulse(t, MILKY_END, sec(0.2f), sec(0.4f)) * 0.5f);
        flash = Math.max(flash, pulse(t, ANDROMEDA_END, sec(0.2f), sec(0.4f)) * 0.5f);
        flash = Math.max(flash, pulse(t, VOID_END, sec(0.3f), sec(0.8f)) * 0.8f);
        flash = Math.max(flash, pulse(t, CHARGE_END, sec(0.1f), sec(1.6f)));
        flash = Math.max(flash, pulse(t, FIRE_END, sec(0.2f), sec(0.4f)) * 0.6f);
        flash = Math.max(flash, pulse(t, CHASE_END, sec(0.3f), sec(0.5f)));
        flash = Math.max(flash, pulse(t, SPACE_END, sec(0.35f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, IMPACT, sec(0.1f), sec(2.6f)));
        if (flash > 0.001f) g.fill(0, 0, w, h, ((int) (Mth.clamp(flash, 0f, 1f) * 255) << 24) | 0xE8F4FF);
    }

    private static void drawConsole(GuiGraphics g, Font font, int w, float t, int barH) {
        BlockPos tp = Cinematic.target();
        String[] lines = {
                "> PROTOCOLO PROMETEO · NIVEL DE AUTORIZACIÓN OMEGA",
                "> ENLAZANDO CON LA ESTACIÓN «PROMETEO»...",
                "> AUTENTICANDO OPERADOR: " + Cinematic.playerName().toUpperCase(),
                "> OBJETIVO: X " + tp.getX() + "  Y " + tp.getY() + "  Z " + tp.getZ(),
                "> FUENTE DE ENERGÍA: HIPERGIGANTE AZUL «ZAFIRO»",
                "> DISTANCIA: 4 100 MILLONES DE AÑOS LUZ",
                "> ABRIENDO VÍA DE PLEGADO A TRAVÉS DEL VACÍO...",
                "> POTENCIA ESTIMADA: 10^38 VATIOS",
                "> AUTORIZACIÓN CONCEDIDA. QUE BRILLE."
        };
        float fadeOut = Cinematic.smooth((S1_END + 0.01f - t) / 0.012f);
        int x = 18, y = barH + 16;
        g.fill(x - 8, y - 8, Math.min(w - 10, x + 360), y + lines.length * 12 + 6, ((int) (0x99 * fadeOut) << 24));
        float charsPerUnit = Cinematic.durationSeconds() * 65f;
        for (int i = 0; i < lines.length; i++) {
            float start = StarDirector.consoleLineStart(i);
            if (t < start) break;
            int chars = Math.min(lines[i].length(), (int) ((t - start) * charsPerUnit));
            boolean cursor = chars < lines[i].length() || (i == lines.length - 1 && ((int) (t * 400)) % 2 == 0);
            int color = i == lines.length - 1 ? 0x9FE8FF : i == 7 ? 0xFF6060 : 0x6FB8FF;
            text(g, font, lines[i].substring(0, chars) + (cursor ? "_" : ""), x, y + i * 12, color, fadeOut, 1f, false);
        }
    }
}
