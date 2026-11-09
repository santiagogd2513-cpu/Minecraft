package com.arsenalatomico.client;

import com.arsenalatomico.BaseLayout;
import com.arsenalatomico.BasePlan;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Lo que el cliente dibuja en la Base Sol Negro: los misiles parados en sus silos (si están cargados),
 * las compuertas que se abren al lanzar, las balizas, el radar del techo, la luz del fuego en el silo y
 * las pantallas del centro de mando (el mapa con el blanco y la trayectoria, y el estado de los silos).
 * <p>
 * Se dibuja en el sistema de la base: x = -b (derecha), y = alto desde la plataforma, z = a (adelante).
 */
public final class BaseRenderer {
    private static Vec3 site;
    private static float yaw;
    private static int loaded = 0b111110;
    private static BasePlan active;
    private static long until;

    private BaseRenderer() {}

    /** Dónde está la base y qué silos tienen misil (lo manda el servidor). */
    public static void base(Vec3 s, float y, int mask) {
        site = s;
        yaw = com.arsenalatomico.NukePlan.snap(y);
        loaded = mask;
    }

    static void show(BasePlan p, long start, int duration) {
        active = p;
        until = start + duration + 600;
        if (site == null) base(p.site, p.yaw, loaded);
    }

    private static final float F = BaseLayout.FLOOR;

    public static void render(PoseStack ps, Camera cam, float partial) {
        if (site == null) return;
        Vec3 c = cam.getPosition();
        if (c.distanceToSqr(site) > 700 * 700) return;
        boolean act = active != null && Cinematic.isActive() && Cinematic.base == active && Cinematic.clientTicks() <= until;
        float s = act ? Cinematic.seconds(partial) : 999;
        float time = Cinematic.clientTicks() + partial;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        ps.pushPose();
        ps.translate(Mth.floor(site.x) + 0.5 - c.x, Mth.floor(site.y) - c.y, Mth.floor(site.z) + 0.5 - c.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        Matrix4f m = ps.last().pose();

        // ================================================================ piezas sólidas
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < BaseLayout.COUNT; i++) {
            double[] si = BaseLayout.SILOS[i];
            float x = (float) -si[1], z = (float) si[0], r = (float) si[2];
            boolean mine = act && active.silo == i;
            // el misil esperando (el del silo que se lanza, hasta que despega)
            boolean has = i == 0 || (loaded & (1 << i)) != 0;
            if (mine ? s * 20 < active.launchTick : has) {
                float my = (float) (-si[3] + 1 + BaseLayout.missileLength(i) / 2);
                Matrix4f mm = new Matrix4f(m).translate(x, my, z).rotateX((float) Math.toRadians(-90));
                if (i == 0) {
                    BigMissileModel.body(bb, mm);
                } else {
                    float k = BaseLayout.missileScale(i);
                    NukeModel.body(bb, new Matrix4f(mm).scale(k));
                }
            }
            // la compuerta: dos hojas de acero que se corren a los lados
            float open = mine ? BaseScenes.hatch(active, s) : 0;
            hatchDoors(bb, m, x, z, r, open);
            // balizas en el borde
            for (int k = 0; k < 4; k++) {
                double ang = Math.PI / 4 + k * Math.PI / 2;
                float lx = x + (float) Math.cos(ang) * (r + 1.6f), lz = z + (float) Math.sin(ang) * (r + 1.6f);
                RenderUtil.box(bb, m, lx - 0.12f, 0f, lz - 0.12f, lx + 0.12f, 1.2f, lz + 0.12f, 0.25f, 0.25f, 0.27f, 1);
                boolean on = mine && s < active.launchSecond() + 4 && ((int) (time / 3) + k) % 2 == 0;
                RenderUtil.box(bb, m, lx - 0.25f, 1.2f, lz - 0.25f, lx + 0.25f, 1.6f, lz + 0.25f,
                        on ? 1f : 0.45f, on ? 0.35f : 0.12f, on ? 0.05f : 0.04f, 1);
            }
        }
        // el radar del techo del edificio de acceso
        Matrix4f rm = new Matrix4f(m).translate(5f, 7.2f, -36f).rotateY(time * 0.05f);
        RenderUtil.box(bb, rm, -0.15f, -0.2f, -0.15f, 0.15f, 0.5f, 0.15f, 0.35f, 0.36f, 0.38f, 1);
        RenderUtil.box(bb, rm, -1.4f, 0.5f, -0.1f, 1.4f, 1.6f, 0.1f, 0.78f, 0.8f, 0.82f, 1);
        RenderUtil.box(bb, rm, -0.06f, 0.9f, 0.1f, 0.06f, 1.2f, 0.9f, 0.3f, 0.3f, 0.32f, 1);
        screens(bb, m, act, s, time);
        BufferUploader.drawWithShader(bb.end());

        // ================================================================ luces (aditivas)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (act) {
            double[] si = BaseLayout.SILOS[active.silo];
            float x = (float) -si[1], z = (float) si[0], r = (float) si[2], depth = (float) si[3];
            float ig = s - active.launchSecond();
            if (ig > -0.5f && ig < 9f) {
                float k = Mth.clamp(ig + 0.5f, 0, 1) * Mth.clamp((9f - ig) / 4f, 0, 1);
                Matrix4f sh = new Matrix4f(m).translate(x, -depth, z);
                RenderUtil.cylinder(bb, sh, r - 0.1f, 0, depth, 24, 1f, 0.55f, 0.15f, 0.6f * k);
                RenderUtil.cylinder(bb, sh, r - 0.3f, 0, depth + 6, 24, 1f, 0.8f, 0.4f, 0.3f * k);
            }
            // la luz roja de alarma en el silo antes del despegue
            if (s < active.launchSecond()) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.4f);
                Matrix4f sh = new Matrix4f(m).translate(x, -depth, z);
                RenderUtil.cylinder(bb, sh, r - 0.05f, 0, depth, 24, 1f, 0.1f, 0.05f, 0.08f * pulse);
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        ps.popPose();
    }

    private static void hatchDoors(BufferBuilder bb, Matrix4f m, float x, float z, float r, float open) {
        float half = r + 0.6f, slide = open * (r + 1.2f);
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? x - half - slide : x + slide, x1 = side < 0 ? x - slide : x + half + slide;
            RenderUtil.box(bb, m, x0, 0.02f, z - half, x1, 0.3f, z + half, 0.42f, 0.44f, 0.46f, 1);
            // franjas de peligro en el borde de cada hoja
            int n = Math.max(4, (int) (half * 2));
            for (int i = 0; i < n; i++) {
                if (i % 2 == 1) continue;
                float za = z - half + i * (2 * half / n), zb = za + 2 * half / n;
                float ex = side < 0 ? x1 - 0.35f : x0, ex1 = side < 0 ? x1 : x0 + 0.35f;
                RenderUtil.box(bb, m, ex, 0.3f, za, ex1, 0.32f, zb, 0.98f, 0.8f, 0.08f, 1);
            }
        }
    }

    /** Las pantallas del centro de mando: la grande de la pared de atrás y las de los puestos. */
    private static void screens(BufferBuilder bb, Matrix4f m, boolean act, float s, float time) {
        // la pantalla grande (pared de atrás, a = -5, mirando hacia adelante)
        float z = -4.47f, y0 = F + 1.05f, y1 = F + 5.95f, xl = 8.45f, xr = -8.45f;
        quadZ(bb, m, z, xr, y0, xl, y1, 0.02f, 0.05f, 0.12f);
        // la cuadrícula del mapa
        for (int i = 0; i <= 8; i++) {
            float x = xr + (xl - xr) * i / 8;
            quadZ(bb, m, z + 0.005f, x - 0.02f, y0 + 0.9f, x + 0.02f, y1 - 0.4f, 0.05f, 0.2f, 0.28f);
        }
        for (int i = 0; i <= 4; i++) {
            float y = y0 + 0.9f + (y1 - 0.4f - y0 - 0.9f) * i / 4;
            quadZ(bb, m, z + 0.005f, xr, y - 0.02f, xl, y + 0.02f, 0.05f, 0.2f, 0.28f);
        }
        // la base (punto verde) y, si hay lanzamiento, el blanco, la trayectoria y el misil
        float bx = 5.5f, by = y0 + 2.2f;
        quadZ(bb, m, z + 0.01f, bx - 0.18f, by - 0.18f, bx + 0.18f, by + 0.18f, 0.2f, 1f, 0.4f);
        if (act) {
            float tx = -5.5f, ty = y0 + 3.4f;
            boolean blink = ((int) (time / 6)) % 2 == 0;
            quadZ(bb, m, z + 0.01f, tx - 0.35f, ty - 0.05f, tx + 0.35f, ty + 0.05f, 1f, blink ? 0.2f : 0.5f, 0.15f);
            quadZ(bb, m, z + 0.01f, tx - 0.05f, ty - 0.35f, tx + 0.05f, ty + 0.35f, 1f, blink ? 0.2f : 0.5f, 0.15f);
            float prog = Mth.clamp((s * 20 - active.launchTick) / active.path.flightTicks, 0, 1);
            for (int i = 0; i < 24; i++) {
                float u = i / 23f;
                float x = Mth.lerp(u, bx, tx), y = Mth.lerp(u, by, ty) + (float) Math.sin(u * Math.PI) * 1.4f;
                boolean done = u <= prog;
                quadZ(bb, m, z + 0.008f, x - 0.05f, y - 0.05f, x + 0.05f, y + 0.05f, done ? 1f : 0.9f, done ? 0.85f : 0.6f, done ? 0.2f : 0.1f);
            }
            if (prog > 0 && prog < 1) {
                float x = Mth.lerp(prog, bx, tx), y = Mth.lerp(prog, by, ty) + (float) Math.sin(prog * Math.PI) * 1.4f;
                quadZ(bb, m, z + 0.012f, x - 0.15f, y - 0.15f, x + 0.15f, y + 0.15f, 1f, 1f, 1f);
            }
            // barra de alerta arriba
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.35f);
            quadZ(bb, m, z + 0.01f, xr, y1 - 0.35f, xl, y1 - 0.05f, 0.5f + 0.5f * pulse, 0.05f, 0.05f);
        } else {
            quadZ(bb, m, z + 0.01f, xr, y1 - 0.35f, xl, y1 - 0.05f, 0.05f, 0.35f, 0.15f);
        }
        // el estado de los seis silos: verde cargado, rojo vacío, amarillo lanzando
        for (int i = 0; i < BaseLayout.COUNT; i++) {
            float x = xl - 1.4f - i * 2.7f;
            boolean has = i == 0 || (loaded & (1 << i)) != 0;
            boolean launching = act && active.silo == i && s < active.impactSecond();
            float r = launching ? 1f : has ? 0.15f : 0.85f, g = launching ? 0.8f : has ? 0.9f : 0.12f, b = launching ? 0.1f : has ? 0.3f : 0.1f;
            float w = i == 0 ? 0.9f : 0.6f;
            quadZ(bb, m, z + 0.01f, x - w, y0 + 0.15f, x + w, y0 + 0.75f, r, g, b);
        }
        // las pantallas de los puestos (miran hacia las sillas, a -a)
        for (int a : new int[]{-2, 4}) {
            for (int side = -1; side <= 1; side += 2) {
                for (int b = 5; b <= 10; b++) {
                    float zz = a - 0.52f, x = -side * b;
                    quadZ(bb, m, zz, x - 0.42f, F + 1.1f, x + 0.42f, F + 1.9f, 0.02f, 0.06f, 0.05f);
                    int kind = Math.floorMod(a * 7 + b * 3 + side, 3);
                    if (kind == 0) {
                        // barras que suben y bajan
                        for (int j = 0; j < 5; j++) {
                            float hgt = 0.1f + 0.55f * (0.5f + 0.5f * (float) Math.sin(time * 0.15f + j + b));
                            float bx0 = x - 0.36f + j * 0.15f;
                            quadZ(bb, m, zz - 0.005f, bx0, F + 1.18f, bx0 + 0.1f, F + 1.18f + hgt, 0.2f, 0.95f, 0.4f);
                        }
                    } else if (kind == 1) {
                        // radar con su barrido
                        float ang = time * 0.08f + b;
                        float cx = x, cy = F + 1.5f;
                        quadZ(bb, m, zz - 0.004f, cx - 0.32f, cy - 0.005f, cx + 0.32f, cy + 0.005f, 0.1f, 0.5f, 0.2f);
                        quadZ(bb, m, zz - 0.004f, cx - 0.005f, cy - 0.32f, cx + 0.005f, cy + 0.32f, 0.1f, 0.5f, 0.2f);
                        for (int j = 1; j <= 6; j++) {
                            float rr = 0.05f * j;
                            float px = cx + (float) Math.cos(ang) * rr, py = cy + (float) Math.sin(ang) * rr;
                            quadZ(bb, m, zz - 0.006f, px - 0.025f, py - 0.025f, px + 0.025f, py + 0.025f, 0.3f, 1f, 0.4f);
                        }
                    } else {
                        // líneas de texto (de colores), como si fueran datos
                        for (int j = 0; j < 5; j++) {
                            float len = 0.25f + 0.45f * (float) Math.abs(Math.sin(j * 1.7 + b + (int) (time / 40)));
                            float yy = F + 1.78f - j * 0.13f;
                            boolean warn = act && j == 0;
                            quadZ(bb, m, zz - 0.005f, x - 0.36f, yy - 0.03f, x - 0.36f + len, yy + 0.03f,
                                    warn ? 1f : 0.95f, warn ? 0.3f : 0.7f, warn ? 0.2f : 0.25f);
                        }
                    }
                }
            }
        }
    }

    /** Un rectángulo en el plano z = cte (x0..x1, y0..y1). */
    private static void quadZ(BufferBuilder bb, Matrix4f m, float z, float x0, float y0, float x1, float y1, float r, float g, float b) {
        RenderUtil.quad(bb, m, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, r, g, b, 1);
    }
}
