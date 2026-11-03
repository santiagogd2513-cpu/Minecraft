package com.amatista.arsenal.client;

import com.amatista.arsenal.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * La interfaz "de película" de la cinemática: barras negras, consola militar, telemetría,
 * títulos de cada acto, cuenta regresiva, destellos y textos finales.
 */
public final class HudRenderer {
    static final int CONSOLE_LINES = 9;
    private static final String[] ACT_TITLES = {
            "I · AUTORIZACIÓN", "II · ASCENSO", "III · ÓRBITA BAJA", "IV · ÉGIDA-7 EN LÍNEA",
            "V · CARGA DEL CAÑÓN DE PARTÍCULAS", "VI · DISPARO", "VII · IMPACTO", "VIII · CONSECUENCIAS"
    };
    private static final float[] ACT_STARTS = {
            0f, Timeline.A1_END, Timeline.A2_END, Timeline.A3_END, Timeline.A4_END, Timeline.A5_END,
            Timeline.A6_END, Timeline.IMPACT
    };

    private HudRenderer() {}

    static float consoleLineStart(int i) {
        return 0.006f + i * (Timeline.A1_END - 0.025f) / CONSOLE_LINES;
    }

    /** Fracción de la cinemática que ocupa la cuenta regresiva (5 segundos). */
    static float countdownWindow() {
        return Math.min(0.06f, 5f / 180f * (180f / Math.max(40f, Cinematic.durationSeconds())));
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!Cinematic.isActive()) return;
        if (Cinematic.type() == com.amatista.arsenal.StrikeType.PHOBOS) {
            PhobosHud.render(g, partialTick, w, h);
            return;
        }
        if (Cinematic.type() == com.amatista.arsenal.StrikeType.BLACKHOLE) {
            BlackHoleHud.render(g, partialTick, w, h);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float t = Cinematic.progress(partialTick);
        float secs = Cinematic.seconds(partialTick);

        // calor al entrar en la atmósfera (bordes naranjas)
        if (t >= Timeline.SPACE_END && t < Timeline.A6_END) {
            float u = (t - Timeline.SPACE_END) / (Timeline.A6_END - Timeline.SPACE_END);
            float heat = (1f - u) * (0.75f + 0.25f * (float) Math.sin(secs * 30));
            vignette(g, w, h, 1f, 0.45f, 0.1f, heat);
        }
        if (Cinematic.isSpace(partialTick)) vignette(g, w, h, 0f, 0f, 0f, 0.45f);

        // barras de cine
        float bars = Math.min(Cinematic.smooth(t / 0.012f), Cinematic.smooth((1f - t) / 0.012f));
        int barH = Math.round(h * 0.11f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        // consola de autorización
        if (t < Timeline.A1_END + 0.01f) drawConsole(g, font, w, h, t, barH);

        // título de cada acto
        for (int i = 0; i < ACT_TITLES.length; i++) {
            float start = ACT_STARTS[i];
            float len = 0.022f;
            if (t >= start && t < start + len && i > 0) {
                float a = Math.min(Cinematic.smooth((t - start) / 0.004f), Cinematic.smooth((start + len - t) / 0.006f));
                text(g, font, ACT_TITLES[i], 14, h - barH - 26, 0xC9A6FF, a, 1.3f, false);
            }
        }

        // telemetría durante el ascenso
        if (t >= Timeline.A1_END && t < Timeline.A2_END) {
            float u = (t - Timeline.A1_END) / (Timeline.A2_END - Timeline.A1_END);
            float km = 100f * (float) Math.pow(u, 2.6);
            float speed = 0.3f + 7.6f * u * u;
            text(g, font, String.format("ALTITUD  %6.1f km", km), w - 150, barH + 12, 0x9FE8FF, 1f, 1f, false);
            text(g, font, String.format("VELOC.   %6.2f km/s", speed), w - 150, barH + 24, 0x9FE8FF, 1f, 1f, false);
            text(g, font, km < 12 ? "TROPOSFERA" : km < 50 ? "ESTRATOSFERA" : km < 85 ? "MESOSFERA" : "TERMOSFERA",
                    w - 150, barH + 36, 0xFFD27F, 1f, 1f, false);
        }

        // panel del satélite
        if (t >= Timeline.A3_END && t < Timeline.A5_END) {
            float u5 = Mth.clamp((t - Timeline.A4_END) / (Timeline.A5_END - Timeline.A4_END), 0f, 1f);
            text(g, font, "ÉGIDA-7 · ARMA ORBITAL DE PARTÍCULAS", 14, barH + 12, 0x9FE8FF, 1f, 1f, false);
            text(g, font, "ÓRBITA: 412 km   INCLINACIÓN: 51,6°", 14, barH + 24, 0x7FB8D0, 1f, 1f, false);
            if (t >= Timeline.A4_END) {
                int pct = Math.round(u5 * 100);
                text(g, font, "CARGA  " + pct + "%", 14, barH + 40, pct < 100 ? 0xFFD27F : 0xFF5050, 1f, 1.2f, false);
                int bw = 160, bx = 14, by = barH + 56;
                g.fill(bx, by, bx + bw, by + 6, 0x80303040);
                g.fill(bx, by, bx + Math.round(bw * u5), by + 6, 0xFF4FC3FF);
                BlockPos tp = Cinematic.target();
                text(g, font, "OBJETIVO  " + tp.getX() + "  " + tp.getY() + "  " + tp.getZ(), 14, by + 12, 0xFF6060, 1f, 1f, false);
            }
        }

        // cuenta regresiva
        float window = countdownWindow();
        if (t >= Timeline.A5_END - window && t < Timeline.A5_END) {
            float into = (t - (Timeline.A5_END - window)) / window * 5f;
            int n = 5 - (int) Math.floor(into);
            float frac = into - (float) Math.floor(into);
            float a = 1f - frac * 0.7f;
            float scale = 6f - frac * 1.5f;
            text(g, font, String.valueOf(Math.max(1, n)), w / 2, h / 2 - 20, 0xFF4040, a, scale, true);
        }

        // texto final
        if (t >= Timeline.IMPACT + 0.012f && t < 0.99f) {
            float a = Math.min(Cinematic.smooth((t - Timeline.IMPACT - 0.012f) / 0.01f), Cinematic.smooth((0.99f - t) / 0.01f));
            text(g, font, "OBJETIVO ELIMINADO", w / 2, h / 2 - 50, 0xFF5050, a, 2.6f, true);
            text(g, font, "ÉGIDA-7 · RECARGANDO", w / 2, h / 2 - 22, 0xC9A6FF, a, 1.2f, true);
        }

        // destellos blancos
        float flash = 0f;
        // duraciones en segundos (cortas: un destello, no una pantalla blanca)
        flash = Math.max(flash, pulse(t, Timeline.SPACE_START, sec(0.5f), sec(0.6f)));        // salida al espacio
        flash = Math.max(flash, pulse(t, Timeline.A5_END, sec(0.15f), sec(0.9f)) * 0.85f);    // disparo
        flash = Math.max(flash, pulse(t, Timeline.SPACE_END, sec(0.35f), sec(0.6f)));         // vuelta al mundo
        flash = Math.max(flash, pulse(t, Timeline.IMPACT, sec(0.1f), sec(1.8f)));             // impacto
        if (flash > 0.001f) g.fill(0, 0, w, h, ((int) (flash * 255) << 24) | 0xFFFFFF);

    }

    private static void drawConsole(GuiGraphics g, Font font, int w, int h, float t, int barH) {
        BlockPos tp = Cinematic.target();
        String[] lines = {
                "> ENLACE ORBITAL AMATISTA v2.7",
                "> CONECTANDO CON SATÉLITE «ÉGIDA-7»...",
                "> SEÑAL: ████████░░ 82%   LATENCIA: 41 ms",
                "> AUTENTICANDO OPERADOR: " + Cinematic.playerName().toUpperCase(),
                "> COORDENADAS DEL OBJETIVO: X " + tp.getX() + "  Y " + tp.getY() + "  Z " + tp.getZ(),
                "> CALCULANDO TRAYECTORIA BALÍSTICA...",
                "> COMPENSANDO ROTACIÓN PLANETARIA... OK",
                "> RADIO DE IMPACTO ESTIMADO: CATASTRÓFICO",
                "> AUTORIZACIÓN CONCEDIDA. INICIANDO SECUENCIA."
        };
        float fadeOut = Cinematic.smooth((Timeline.A1_END + 0.01f - t) / 0.012f);
        int x = 18, y = barH + 16;
        g.fill(x - 8, y - 8, Math.min(w - 10, x + 330), y + lines.length * 12 + 6, ((int) (0x99 * fadeOut) << 24));
        float charsPerUnit = Cinematic.durationSeconds() * 45f;
        for (int i = 0; i < lines.length; i++) {
            float start = consoleLineStart(i);
            if (t < start) break;
            int chars = Math.min(lines[i].length(), (int) ((t - start) * charsPerUnit));
            String s = lines[i].substring(0, chars);
            boolean cursor = chars < lines[i].length() || (i == lines.length - 1 && ((int) (t * 400)) % 2 == 0);
            int color = i == lines.length - 1 ? 0x55FF77 : i == 7 ? 0xFF6060 : 0x66FF99;
            text(g, font, s + (cursor ? "_" : ""), x, y + i * 12, color, fadeOut, 1f, false);
        }
    }

    /** Convierte segundos a fracción de la cinemática. */
    static float sec(float seconds) {
        return seconds / Math.max(1f, Cinematic.durationSeconds());
    }

    static float pulse(float t, float at, float in, float out) {
        if (t < at - in || t > at + out) return 0f;
        if (t < at) return Cinematic.smooth((t - (at - in)) / in);
        return 1f - Cinematic.smooth((t - at) / out);
    }

    static void vignette(GuiGraphics g, int w, int h, float r, float gr, float b, float a) {
        if (a <= 0.01f) return;
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float f = (steps - i) / (float) steps;
            int alpha = (int) (a * 255 * f * f * 0.35f);
            int color = (alpha << 24) | ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
            int inset = Math.round(i * Math.min(w, h) * 0.035f);
            g.fill(inset, inset, w - inset, inset + Math.max(2, Math.round(Math.min(w, h) * 0.035f)), color);
            g.fill(inset, h - inset - Math.max(2, Math.round(Math.min(w, h) * 0.035f)), w - inset, h - inset, color);
            g.fill(inset, inset, inset + Math.max(2, Math.round(Math.min(w, h) * 0.035f)), h - inset, color);
            g.fill(w - inset - Math.max(2, Math.round(Math.min(w, h) * 0.035f)), inset, w - inset, h - inset, color);
        }
    }

    static void text(GuiGraphics g, Font font, String s, int x, int y, int rgb, float alpha, float scale, boolean centered) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        if (a < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int dx = centered ? -font.width(s) / 2 : 0;
        g.drawString(font, s, dx, 0, (a << 24) | (rgb & 0xFFFFFF), true);
        g.pose().popPose();
    }
}
