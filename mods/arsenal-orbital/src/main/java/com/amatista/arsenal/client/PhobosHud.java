package com.amatista.arsenal.client;

import com.amatista.arsenal.PhobosTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import static com.amatista.arsenal.client.HudRenderer.pulse;
import static com.amatista.arsenal.client.HudRenderer.sec;
import static com.amatista.arsenal.client.HudRenderer.text;
import static com.amatista.arsenal.client.HudRenderer.vignette;

/** Interfaz de película del "Protocolo Fobos". */
final class PhobosHud {
    private static final String[] ACT_TITLES = {
            "I · AUTORIZACIÓN", "II · ASCENSO", "III · RUMBO A MARTE", "IV · FOBOS",
            "V · IMPACTO DEL MISIL", "VI · HONDA GRAVITATORIA", "VII · RUMBO A LA TIERRA",
            "VIII · IMPACTO", "IX · CONSECUENCIAS"
    };
    private static final float[] ACT_STARTS = {
            0f, PhobosTimeline.P1_END, PhobosTimeline.P2_END, PhobosTimeline.TRAVEL_END, PhobosTimeline.MARS_END,
            PhobosTimeline.MISSILE_END, PhobosTimeline.SLING_END, PhobosTimeline.SPACE_END, PhobosTimeline.IMPACT
    };

    private PhobosHud() {}

    static float consoleLineStart(int i) {
        return 0.005f + i * (PhobosTimeline.P1_END - 0.02f) / HudRenderer.CONSOLE_LINES;
    }

    static void render(GuiGraphics g, float partialTick, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float t = Cinematic.progress(partialTick);
        float secs = Cinematic.seconds(partialTick);

        // calor: al salir disparada hacia la Tierra y al caer
        if (t >= PhobosTimeline.SLING_END + (PhobosTimeline.SPACE_END - PhobosTimeline.SLING_END) * 0.45f && t < PhobosTimeline.IMPACT) {
            float heat = 0.45f + 0.25f * (float) Math.sin(secs * 25);
            vignette(g, w, h, 1f, 0.42f, 0.08f, heat);
        }
        if (Cinematic.isSpace(partialTick)) vignette(g, w, h, 0f, 0f, 0f, 0.4f);

        float bars = Math.min(Cinematic.smooth(t / 0.012f), Cinematic.smooth((1f - t) / 0.012f));
        int barH = Math.round(h * 0.11f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        if (t < PhobosTimeline.P1_END + 0.01f) drawConsole(g, font, w, t, barH);

        for (int i = 1; i < ACT_TITLES.length; i++) {
            float start = ACT_STARTS[i];
            float len = 0.022f;
            if (t >= start && t < start + len) {
                float a = Math.min(Cinematic.smooth((t - start) / 0.004f), Cinematic.smooth((start + len - t) / 0.006f));
                text(g, font, ACT_TITLES[i], 14, h - barH - 26, 0xFFB27F, a, 1.3f, false);
            }
        }

        // viaje a Marte
        if (t >= 0.215f && t < PhobosTimeline.TRAVEL_END) {
            float u = (t - 0.215f) / (PhobosTimeline.TRAVEL_END - 0.215f);
            float mkm = 78f * (1f - Cinematic.smooth(u));
            float speed = 30f + 2970f * PhobosDirector.warp(t);
            text(g, font, String.format("DISTANCIA A MARTE  %5.1f millones km", mkm), w - 230, barH + 12, 0xFFB27F, 1f, 1f, false);
            text(g, font, String.format("VELOCIDAD          %6.0f km/s", speed), w - 230, barH + 24, 0xFFB27F, 1f, 1f, false);
        }
        // datos de Fobos
        if (t >= PhobosTimeline.TRAVEL_END && t < PhobosTimeline.SLING_END) {
            text(g, font, "FOBOS · LUNA DE MARTE", 14, barH + 12, 0xFFB27F, 1f, 1f, false);
            text(g, font, "27 × 22 × 18 km   MASA 1,07×10¹⁶ kg", 14, barH + 24, 0xD0A080, 1f, 1f, false);
            text(g, font, String.format("VELOCIDAD  %5.1f km/s", PhobosDirector.phobosSpeedKms(t)), 14, barH + 40,
                    t < PhobosTimeline.MISSILE_END ? 0xFFD27F : 0xFF7050, 1f, 1.2f, false);
            if (t >= PhobosTimeline.MISSILE_END) {
                float u = PhobosDirector.slingProgress(t);
                int orbits = Math.min(4, (int) Math.floor(Math.pow(u, 2.6) * 4f) + 1);
                text(g, font, "ÓRBITA " + orbits + " / 4   HONDA GRAVITATORIA", 14, barH + 56, 0xFFD27F, 1f, 1f, false);
                int bw = 180, bx = 14, by = barH + 70;
                g.fill(bx, by, bx + bw, by + 6, 0x80303040);
                g.fill(bx, by, bx + Math.round(bw * u), by + 6, 0xFFFF7040);
            }
        }
        // cuenta regresiva para soltar a Fobos
        float window = HudRenderer.countdownWindow();
        if (t >= PhobosTimeline.SLING_END - window && t < PhobosTimeline.SLING_END) {
            float into = (t - (PhobosTimeline.SLING_END - window)) / window * 5f;
            int n = 5 - (int) Math.floor(into);
            float frac = into - (float) Math.floor(into);
            text(g, font, "LIBERACIÓN EN", w / 2, h / 2 - 70, 0xFFB27F, 1f, 1.4f, true);
            text(g, font, String.valueOf(Math.max(1, n)), w / 2, h / 2 - 40, 0xFF5030, 1f - frac * 0.7f, 6f - frac * 1.5f, true);
        }
        // rumbo a la Tierra
        if (t >= PhobosTimeline.SLING_END && t < PhobosTimeline.IMPACT) {
            float total = (PhobosTimeline.IMPACT - PhobosTimeline.SLING_END) * Cinematic.durationSeconds();
            float left = Math.max(0f, (PhobosTimeline.IMPACT - t) * Cinematic.durationSeconds());
            BlockPos tp = Cinematic.target();
            text(g, font, String.format("TIEMPO AL IMPACTO  %5.1f s", left), w - 200, barH + 12, 0xFF6040, 1f, 1.1f, false);
            text(g, font, "OBJETIVO  " + tp.getX() + "  " + tp.getY() + "  " + tp.getZ(), w - 200, barH + 28, 0xFFB27F, 1f, 1f, false);
            int bw = 180, bx = w - 200, by = barH + 42;
            g.fill(bx, by, bx + bw, by + 5, 0x80303040);
            g.fill(bx, by, bx + Math.round(bw * (1f - left / Math.max(1f, total))), by + 5, 0xFFFF5030);
        }
        // texto final
        if (t >= PhobosTimeline.IMPACT + 0.012f && t < 0.99f) {
            float a = Math.min(Cinematic.smooth((t - PhobosTimeline.IMPACT - 0.012f) / 0.01f), Cinematic.smooth((0.99f - t) / 0.01f));
            text(g, font, "IMPACTO CONFIRMADO", w / 2, h / 2 - 50, 0xFF5030, a, 2.6f, true);
            text(g, font, "FOBOS · DESTRUIDA", w / 2, h / 2 - 22, 0xFFB27F, a, 1.2f, true);
        }

        float flash = 0f;
        // salida al espacio: sin destello blanco, el cielo ya está negro y la escena aparece desde la oscuridad
        HudRenderer.spaceDip(g, w, h, pulse(t, PhobosTimeline.SPACE_START, sec(0.6f), sec(1.8f)));
        flash = Math.max(flash, pulse(t, 0.215f, sec(0.15f), sec(0.5f)) * 0.6f);
        flash = Math.max(flash, pulse(t, PhobosTimeline.MISSILE_HIT, sec(0.1f), sec(1.2f)) * 0.9f);
        flash = Math.max(flash, pulse(t, PhobosTimeline.SLING_END, sec(0.1f), sec(0.6f)) * 0.7f);
        flash = Math.max(flash, pulse(t, PhobosTimeline.SLING_END + (PhobosTimeline.SPACE_END - PhobosTimeline.SLING_END) * 0.6f, sec(0.15f), sec(0.4f)));
        flash = Math.max(flash, pulse(t, PhobosTimeline.SPACE_END, sec(0.35f), sec(0.6f)));
        flash = Math.max(flash, pulse(t, PhobosTimeline.IMPACT, sec(0.1f), sec(2.4f)));
        if (flash > 0.001f) g.fill(0, 0, w, h, ((int) (Mth.clamp(flash, 0f, 1f) * 255) << 24) | 0xFFFFFF);
    }

    private static void drawConsole(GuiGraphics g, Font font, int w, float t, int barH) {
        BlockPos tp = Cinematic.target();
        String[] lines = {
                "> PROTOCOLO FOBOS v1.0",
                "> ENLAZANDO CON LA RED MARCIANA «ARES»...",
                "> AUTENTICANDO OPERADOR: " + Cinematic.playerName().toUpperCase(),
                "> OBJETIVO: X " + tp.getX() + "  Y " + tp.getY() + "  Z " + tp.getZ(),
                "> MASA DE FOBOS: 1,07×10¹⁶ kg",
                "> CALCULANDO ASISTENCIA GRAVITATORIA...",
                "> TRAYECTORIA DE RETORNO: CONFIRMADA",
                "> ENERGÍA DE IMPACTO ESTIMADA: EXTINCIÓN LOCAL",
                "> AUTORIZACIÓN CONCEDIDA. INICIANDO PROTOCOLO."
        };
        float fadeOut = Cinematic.smooth((PhobosTimeline.P1_END + 0.01f - t) / 0.012f);
        int x = 18, y = barH + 16;
        g.fill(x - 8, y - 8, Math.min(w - 10, x + 340), y + lines.length * 12 + 6, ((int) (0x99 * fadeOut) << 24));
        float charsPerUnit = Cinematic.durationSeconds() * 55f;
        for (int i = 0; i < lines.length; i++) {
            float start = consoleLineStart(i);
            if (t < start) break;
            int chars = Math.min(lines[i].length(), (int) ((t - start) * charsPerUnit));
            boolean cursor = chars < lines[i].length() || (i == lines.length - 1 && ((int) (t * 400)) % 2 == 0);
            int color = i == lines.length - 1 ? 0xFFD27F : i == 7 ? 0xFF6040 : 0xFFA060;
            text(g, font, lines[i].substring(0, chars) + (cursor ? "_" : ""), x, y + i * 12, color, fadeOut, 1f, false);
        }
    }
}
