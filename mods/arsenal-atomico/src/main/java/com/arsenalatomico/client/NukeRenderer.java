package com.arsenalatomico.client;

import com.arsenalatomico.NukePlan;
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
import org.joml.Vector3f;

/**
 * Lo que se mueve y se prende en el búnker del Protocolo Hongo (los bloques los pone el servidor):
 * la compuerta blindada de dos mitades con el símbolo nuclear, las balizas, el misil esperando con sus
 * brazos de servicio, las luces del pozo, las pantallas del cuarto de control (radar, gráficos y el
 * mapa con la trayectoria), el reloj de la cuenta regresiva, las llaves que giran, las cámaras de la
 * caseta, el radar del techo, la sirena y los reflectores.
 * <p>
 * Se dibuja en el sistema del búnker: x = -b (derecha), y = alto desde la plataforma, z = a (adelante).
 */
public final class NukeRenderer {
    private static NukePlan plan;
    private static long until;

    private NukeRenderer() {}

    static void show(NukePlan p, long start, int duration) {
        plan = p;
        until = start + duration + 1200;
    }

    // ------------------------------------------------------------------ tiempos

    /** Compuerta: 0 cerrada, 1 abierta. */
    static float hatch(float s) {
        return Cinematic.smooth((s - 100) / 8) - Cinematic.smooth((s - 196) / 8);
    }

    /** Brazos de servicio: 0 pegados al misil, 1 recogidos. */
    static float arms(float s) {
        return Cinematic.smooth((s - 91) / 5);
    }

    /** Llaves: 0 sin girar, 1 giradas. */
    static float keys(float s) {
        return Cinematic.smooth((s - 100) / 0.6f);
    }

    static boolean alarm(float s) {
        return s >= 46 && s < 114;
    }

    /** Luces del pozo: se prenden de abajo hacia arriba mientras se mira por la ventana. */
    static float shaftLight(float s, int ring) {
        return Cinematic.smooth((s - (83 + ring * 0.45f)) / 0.4f);
    }

    private static final float FLOOR = -(NukePlan.DEPTH - NukePlan.ROOM_UP);   // piso del cuarto, desde la plataforma

    public static void render(PoseStack ps, Camera cam, float partial) {
        if (plan == null || Cinematic.clientTicks() > until) return;
        Vec3 c = cam.getPosition();
        Vec3 site = plan.site;
        if (c.distanceToSqr(site) > 600 * 600) return;
        boolean active = Cinematic.isActive() && Cinematic.nuke == plan;
        float s = active ? Cinematic.seconds(partial) : 999;
        float time = Cinematic.clientTicks() + partial;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        ps.pushPose();
        ps.translate(site.x - c.x, Math.floor(site.y) - c.y, site.z - c.z);
        ps.mulPose(Axis.YP.rotationDegrees(-plan.yaw));
        Matrix4f m = ps.last().pose();

        // ================================================================ piezas sólidas
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float open = active ? hatch(s) : 0;
        for (int side = -1; side <= 1; side += 2) hatchHalf(bb, m, side, open);
        // balizas en el borde del silo
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI / 4 + i * Math.PI / 2;
            float lx = (float) Math.cos(ang) * 7.2f, lz = (float) Math.sin(ang) * 7.2f;
            RenderUtil.box(bb, m, lx - 0.16f, 0f, lz - 0.16f, lx + 0.16f, 1.7f, lz + 0.16f, 0.25f, 0.25f, 0.27f, 1);
            boolean on = active && alarm(s) && ((int) (time / 3) + i) % 2 == 0;
            RenderUtil.box(bb, m, lx - 0.32f, 1.7f, lz - 0.32f, lx + 0.32f, 2.25f, lz + 0.32f, on ? 1f : 0.45f, on ? 0.55f : 0.25f, on ? 0.05f : 0.04f, 1);
        }
        // el misil esperando en el pozo, con sus brazos de servicio
        Vec3 st = NukePlan.start(site);
        float my = (float) (st.y - Math.floor(site.y));
        if (active && s * 20 < plan.launchTick) {
            Matrix4f mm = new Matrix4f(m).translate(0, my, 0).rotateX((float) Math.toRadians(-90));
            NukeModel.body(bb, mm);
            float in = NukeModel.R + (float) (NukePlan.SHAFT_R - 0.15 - NukeModel.R) * arms(s);
            for (float y : new float[]{-5.5f, 0f, 5.5f}) {
                for (int k = 0; k < 4; k++) {
                    Matrix4f am = new Matrix4f(m).translate(0, my + y, 0).rotateY((float) Math.toRadians(45 + k * 90));
                    RenderUtil.box(bb, am, in, -0.22f, -0.28f, (float) NukePlan.SHAFT_R, 0.22f, 0.28f, 0.85f, 0.62f, 0.1f, 1);
                    RenderUtil.box(bb, am, in - 0.05f, -0.38f, -0.42f, in + 0.28f, 0.38f, 0.42f, 0.22f, 0.22f, 0.24f, 1);
                    // la manguera que cuelga
                    RenderUtil.box(bb, am, in + 0.4f, -0.9f, -0.08f, in + 0.55f, -0.22f, 0.08f, 0.1f, 0.1f, 0.1f, 1);
                }
            }
        }
        // las llaves de lanzamiento (en las dos estaciones del cuarto)
        float kt = active ? keys(s) : 1;
        for (int side = -1; side <= 1; side += 2) {
            Matrix4f km = new Matrix4f(m).translate(-side * 4f, FLOOR + 2.02f, -13f).rotateY((float) Math.toRadians(90 * kt));
            RenderUtil.box(bb, km, -0.05f, 0f, -0.18f, 0.05f, 0.12f, 0.18f, 0.75f, 0.68f, 0.3f, 1);
            RenderUtil.box(bb, km, -0.09f, 0.12f, -0.07f, 0.09f, 0.3f, 0.07f, 0.7f, 0.62f, 0.25f, 1);
        }
        // el radar del techo de la caseta: un plato que gira
        Matrix4f rm = new Matrix4f(m).translate(-3f, 7.3f, NukePlan.STAIR_A + 3).rotateY(time * 0.06f);
        RenderUtil.box(bb, rm, -0.2f, -0.3f, -0.2f, 0.2f, 0.3f, 0.2f, 0.35f, 0.36f, 0.38f, 1);
        Matrix4f dish = new Matrix4f(rm).translate(0, 0.5f, 0.3f).rotateX((float) Math.toRadians(-70));
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
            float r0 = 1.6f;
            RenderUtil.quad(bb, dish, 0, -0.35f, 0, (float) Math.cos(a0) * r0, 0, (float) Math.sin(a0) * r0,
                    (float) Math.cos(a1) * r0, 0, (float) Math.sin(a1) * r0, 0, -0.35f, 0, 0.8f, 0.82f, 0.84f, 1);
        }
        RenderUtil.box(bb, dish, -0.05f, -0.35f, -0.05f, 0.05f, 1.2f, 0.05f, 0.3f, 0.3f, 0.32f, 1);
        BufferUploader.drawWithShader(bb.end());

        // ================================================================ luces y pantallas (se suman)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f camRight = new Vector3f(cam.getLeftVector()).negate(), camUp = new Vector3f(cam.getUpVector());
        // en el sistema girado, los ejes de la cámara también se giran
        Matrix4f inv = new Matrix4f().rotateY((float) Math.toRadians(plan.yaw));
        Vector3f lr = inv.transformDirection(new Vector3f(camRight)), lu = inv.transformDirection(new Vector3f(camUp));
        Vector3f up = new Vector3f(0, 1, 0);
        if (active) {
            if (alarm(s)) {
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI / 4 + i * Math.PI / 2;
                    float lx = (float) Math.cos(ang) * 7.2f, lz = (float) Math.sin(ang) * 7.2f;
                    double rot = time * 0.24 + i * 1.3;
                    Vector3f dir = new Vector3f((float) Math.cos(rot), 0, (float) Math.sin(rot));
                    RenderUtil.softBeam(bb, m, lx + dir.x * 7f, 1.95f, lz + dir.z * 7f, dir, 7f, up, 1f, 1f, 0.45f, 0.05f, 0.35f);
                    RenderUtil.softDisc(bb, m, lx, 1.95f, lz, lr, lu, 1.5f, 12, 1f, 0.5f, 0.08f, 0.7f, 0f);
                }
                // sirena roja que gira sobre la caseta
                float sy = 5.4f, sz = NukePlan.STAIR_A;
                double rot = time * 0.3;
                Vector3f dir = new Vector3f((float) Math.cos(rot), 0, (float) Math.sin(rot));
                RenderUtil.softBeam(bb, m, dir.x * 9f, sy, sz + dir.z * 9f, dir, 9f, up, 1.4f, 1f, 0.1f, 0.05f, 0.4f);
                RenderUtil.softDisc(bb, m, 0, sy, sz, lr, lu, 1.2f, 12, 1f, 0.15f, 0.08f, 0.8f, 0f);
                // luz roja que late dentro del pozo
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.3f);
                Matrix4f sh = new Matrix4f(m).translate(0, -NukePlan.DEPTH, 0);
                RenderUtil.cylinder(bb, sh, (float) NukePlan.SHAFT_R - 0.05f, 0, NukePlan.DEPTH, 24, 1f, 0.1f, 0.05f, 0.07f * pulse);
            }
            // anillos de luz del pozo
            for (int i = 0; i < 8; i++) {
                float on = shaftLight(s, i);
                if (on <= 0.01f || s * 20 > plan.launchTick + 200) continue;
                Matrix4f rmx = new Matrix4f(m).translate(0, -NukePlan.DEPTH + 2 + i * 4, 0);
                RenderUtil.cylinder(bb, rmx, (float) NukePlan.SHAFT_R - 0.04f, 0, 0.8f, 32, 0.75f, 0.9f, 1f, 0.45f * on);
                RenderUtil.cylinder(bb, rmx, (float) NukePlan.SHAFT_R - 0.05f, -1.4f, 2.2f, 32, 0.6f, 0.75f, 1f, 0.1f * on);
            }
            // reflectores de las torres apuntando a la boca del silo
            for (int i = 0; i < 4; i++) {
                float ta = (i < 2 ? 1 : -1) * 22, tb = (i % 2 == 0 ? 1 : -1) * 22;
                Vector3f from = new Vector3f(-tb, 9f, ta);
                float sweep = (float) Math.sin(time * 0.02 + i) * 3;
                Vector3f to = new Vector3f(sweep, 0, sweep * 0.5f);
                Vector3f dir = new Vector3f(to).sub(from);
                float len = dir.length();
                dir.normalize();
                Vector3f side = new Vector3f(dir).cross(up).normalize();
                Vector3f mid = new Vector3f(from).add(to).mul(0.5f);
                RenderUtil.softBeam(bb, m, mid.x, mid.y, mid.z, dir, len / 2, side, 2.2f, 1f, 0.96f, 0.85f, 0.1f);
                RenderUtil.softBeam(bb, m, mid.x, mid.y, mid.z, dir, len / 2, new Vector3f(dir).cross(side).normalize(), 2.2f, 1f, 0.96f, 0.85f, 0.1f);
                RenderUtil.softDisc(bb, m, from.x, from.y, from.z, lr, lu, 1.3f, 12, 1f, 0.97f, 0.85f, 0.8f, 0f);
            }
            // fuego en el pozo y en las ventilas al encender
            float ig = s - NukePlan.LAUNCH_S;
            if (ig > -0.5f && ig < 10f) {
                float k = Mth.clamp(ig + 0.5f, 0, 1) * Mth.clamp((10f - ig) / 4f, 0, 1);
                Matrix4f sh = new Matrix4f(m).translate(0, -NukePlan.DEPTH, 0);
                RenderUtil.cylinder(bb, sh, (float) NukePlan.SHAFT_R - 0.1f, 0, NukePlan.DEPTH, 24, 1f, 0.55f, 0.15f, 0.55f * k);
                for (int side = -1; side <= 1; side += 2) {
                    RenderUtil.softDisc(bb, m, -side * NukePlan.VENT_B, 3f, NukePlan.VENT_A, lr, lu, 6f * k, 16, 1f, 0.6f, 0.2f, 0.8f * k, 0f);
                    RenderUtil.softDisc(bb, m, -side * NukePlan.VENT_B, 9f, NukePlan.VENT_A, lr, lu, 9f * k, 16, 1f, 0.45f, 0.12f, 0.4f * k, 0f);
                }
                RenderUtil.softDisc(bb, m, 0, 2f, 0, lr, lu, 14f * k, 20, 1f, 0.7f, 0.3f, 0.6f * k, 0f);
            }
        }
        // las pantallas del cuarto de control (siempre prendidas mientras se ve el búnker)
        consoles(bb, m, s, time, active);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        ps.popPose();
    }

    /** Una mitad de la compuerta: losa gruesa con franjas de peligro en la orilla y el símbolo nuclear pintado. */
    private static void hatchHalf(BufferBuilder bb, Matrix4f m, int side, float open) {
        float off = side * open * 5.4f;
        float x0 = side > 0 ? 0f : -4.7f, x1 = side > 0 ? 4.7f : 0f;
        x0 += off;
        x1 += off;
        RenderUtil.box(bb, m, x0, 0.02f, -4.7f, x1, 0.75f, 4.7f, 0.42f, 0.43f, 0.42f, 1);
        // refuerzos
        for (int k = -2; k <= 2; k++) RenderUtil.box(bb, m, x0 + 0.2f, 0.75f, k * 1.8f - 0.15f, x1 - 0.2f, 0.85f, k * 1.8f + 0.15f, 0.3f, 0.31f, 0.3f, 1);
        // franjas en el borde que se junta
        float edge = side > 0 ? x0 : x1;
        for (int k = 0; k < 12; k++) {
            float z0 = -4.7f + k * 0.785f;
            boolean y = k % 2 == 0;
            float ex0 = side > 0 ? edge : edge - 0.7f, ex1 = side > 0 ? edge + 0.7f : edge;
            RenderUtil.box(bb, m, ex0, 0.75f, z0, ex1, 0.8f, z0 + 0.785f, y ? 0.98f : 0.06f, y ? 0.8f : 0.06f, y ? 0.08f : 0.06f, 1);
        }
        // ruedas sobre el riel
        for (int k = -1; k <= 1; k += 2) {
            float wz = k * 5.05f, wx = (x0 + x1) / 2;
            RenderUtil.box(bb, m, wx - 1.2f, 0.02f, wz - 0.35f, wx + 1.2f, 0.6f, wz + 0.35f, 0.18f, 0.18f, 0.2f, 1);
        }
        // el símbolo nuclear pintado encima
        Matrix4f tm = new Matrix4f(m).translate((x0 + x1) / 2, 0.865f, 0).rotateZ((float) Math.PI / 2);
        NukeModel.trefoil(bb, tm, 0, 0, 0, 1.7f);
    }

    // ------------------------------------------------------------------ pantallas

    private static void glowRect(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        RenderUtil.quad(bb, m, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, r, g, b, a);
    }

    private static void consoles(BufferBuilder bb, Matrix4f m, float s, float time, boolean active) {
        float fy = FLOOR;
        // pantallas de los puestos (en a = -8, mirando hacia atrás, al operador)
        int[] bs = {-6, -5, -4, 4, 5, 6};
        for (int i = 0; i < bs.length; i++) {
            float cx = -bs[i], z = -8.52f, y0 = fy + 1.08f, y1 = fy + 1.92f;
            float x0 = cx - 0.44f, x1 = cx + 0.44f;
            boolean red = active && alarm(s) && ((int) (time / 8) + i) % 3 == 0;
            glowRect(bb, m, x0, y0, x1, y1, z, red ? 0.6f : 0.08f, red ? 0.05f : 0.35f, red ? 0.03f : 0.18f, 0.55f);
            if (i % 3 == 0) {
                // radar con su línea que barre y puntos
                float cxr = cx, cyr = (y0 + y1) / 2, rr = 0.36f;
                double ang = time * 0.12 + i;
                RenderUtil.quad(bb, m, cxr, cyr, z - 0.005f, cxr + (float) Math.cos(ang) * rr, cyr + (float) Math.sin(ang) * rr, z - 0.005f,
                        cxr + (float) Math.cos(ang + 0.25) * rr, cyr + (float) Math.sin(ang + 0.25) * rr, z - 0.005f, cxr, cyr, z - 0.005f, 0.3f, 1f, 0.45f, 0.8f);
                for (int k = 0; k < 4; k++) {
                    float bx = cxr + (float) Math.cos(k * 1.7 + i) * rr * 0.6f, by = cyr + (float) Math.sin(k * 2.3 + i) * rr * 0.6f;
                    float blink = 0.5f + 0.5f * Mth.sin(time * 0.3f + k);
                    glowRect(bb, m, bx - 0.025f, by - 0.025f, bx + 0.025f, by + 0.025f, z - 0.006f, 0.6f, 1f, 0.6f, blink);
                }
            } else if (i % 3 == 1) {
                // barras de datos que suben y bajan
                for (int k = 0; k < 7; k++) {
                    float h = 0.15f + 0.55f * (0.5f + 0.5f * Mth.sin(time * 0.15f + k * 1.3f + i));
                    float bx = x0 + 0.08f + k * 0.11f;
                    glowRect(bb, m, bx, y0 + 0.08f, bx + 0.07f, y0 + 0.08f + h, z - 0.005f, 1f, 0.7f, 0.15f, 0.75f);
                }
            } else {
                // líneas de texto que corren
                for (int k = 0; k < 6; k++) {
                    float w = 0.3f + 0.5f * (0.5f + 0.5f * Mth.sin(k * 3.1f + (int) (time / 10) * 0.7f));
                    float ly = y1 - 0.1f - k * 0.12f;
                    glowRect(bb, m, x0 + 0.06f, ly - 0.04f, x0 + 0.06f + w * 0.76f, ly, z - 0.005f, 0.4f, 0.95f, 0.55f, 0.7f);
                }
            }
        }
        // el mapa grande en la pared de atrás (a = -14,5, mirando hacia adelante)
        {
            float z = -14.48f, x0 = -6.4f, x1 = -2.6f, y0 = fy + 1.1f, y1 = fy + 3.6f;
            glowRect(bb, m, x0, y0, x1, y1, z, 0.03f, 0.1f, 0.25f, 0.6f);
            for (int k = 1; k < 8; k++) {
                float gx = x0 + (x1 - x0) * k / 8;
                glowRect(bb, m, gx - 0.01f, y0, gx + 0.01f, y1, z + 0.004f, 0.2f, 0.45f, 0.9f, 0.35f);
            }
            for (int k = 1; k < 5; k++) {
                float gy = y0 + (y1 - y0) * k / 5;
                glowRect(bb, m, x0, gy - 0.01f, x1, gy + 0.01f, z + 0.004f, 0.2f, 0.45f, 0.9f, 0.35f);
            }
            // la trayectoria (una parábola) y el punto que avanza cuando el misil va en el aire
            float prog = active ? Mth.clamp((s * 20 - plan.launchTick) / plan.path.flightTicks, 0, 1) : 0;
            int segs = 24;
            for (int k = 0; k < segs; k++) {
                float u0 = k / (float) segs, u1 = (k + 1) / (float) segs;
                float px0 = Mth.lerp(u0, x1 - 0.3f, x0 + 0.3f), px1 = Mth.lerp(u1, x1 - 0.3f, x0 + 0.3f);
                float py0 = y0 + 0.3f + 1.7f * 4 * u0 * (1 - u0), py1 = y0 + 0.3f + 1.7f * 4 * u1 * (1 - u1);
                boolean done = u1 <= prog;
                RenderUtil.quad(bb, m, px0, py0 - 0.02f, z + 0.006f, px1, py1 - 0.02f, z + 0.006f, px1, py1 + 0.02f, z + 0.006f, px0, py0 + 0.02f, z + 0.006f,
                        done ? 1f : 0.9f, done ? 0.4f : 0.9f, done ? 0.15f : 0.4f, done ? 0.9f : 0.5f);
            }
            float tx = x0 + 0.3f, ty = y0 + 0.3f;
            float blink = ((int) (time / 6)) % 2 == 0 ? 1f : 0.3f;
            RenderUtil.quad(bb, m, tx - 0.15f, ty - 0.15f, z + 0.007f, tx + 0.15f, ty + 0.15f, z + 0.007f, tx + 0.13f, ty + 0.17f, z + 0.007f, tx - 0.17f, ty - 0.13f, z + 0.007f, 1f, 0.1f, 0.1f, blink);
            RenderUtil.quad(bb, m, tx + 0.15f, ty - 0.15f, z + 0.007f, tx - 0.15f, ty + 0.15f, z + 0.007f, tx - 0.17f, ty + 0.13f, z + 0.007f, tx + 0.13f, ty - 0.17f, z + 0.007f, 1f, 0.1f, 0.1f, blink);
            if (prog > 0 && prog < 1) {
                float px = Mth.lerp(prog, x1 - 0.3f, x0 + 0.3f), py = y0 + 0.3f + 1.7f * 4 * prog * (1 - prog);
                glowRect(bb, m, px - 0.07f, py - 0.07f, px + 0.07f, py + 0.07f, z + 0.008f, 1f, 1f, 0.6f, 1f);
            }
        }
        // las luces de las llaves: rojas, y verdes al girarlas
        float kt = active ? keys(s) : 1;
        for (int side = -1; side <= 1; side += 2) {
            float lx = -side * 4f + 0.32f, lz = -13f + 0.3f, ly = fy + 2.05f;
            RenderUtil.box(bb, m, lx - 0.06f, ly, lz - 0.06f, lx + 0.06f, ly + 0.1f, lz + 0.06f, kt > 0.5f ? 0.2f : 1f, kt > 0.5f ? 1f : 0.1f, 0.1f, 0.9f);
        }
        // el reloj de la cuenta regresiva encima de la ventana (en a = -7,5, mirando hacia atrás)
        {
            float z = -7.53f, y0 = fy + 4.12f, h = 0.76f;
            int left = active ? Math.max(0, (int) Math.ceil(NukePlan.LAUNCH_S - s)) : 0;
            boolean launched = active && s >= NukePlan.LAUNCH_S;
            int mm = left / 60, ss = left % 60;
            int[] digits = {mm / 10, mm % 10, -1, ss / 10, ss % 10};
            float x = 2.9f;
            glowRect(bb, m, -3.2f, y0 - 0.05f, 3.2f, y0 + h + 0.05f, z + 0.003f, 0.12f, 0.02f, 0.02f, 0.6f);
            for (int dgt : digits) {
                if (dgt < 0) {
                    glowRect(bb, m, x - 0.18f, y0 + 0.2f, x - 0.08f, y0 + 0.28f, z, 1f, 0.15f, 0.1f, 1f);
                    glowRect(bb, m, x - 0.18f, y0 + 0.5f, x - 0.08f, y0 + 0.58f, z, 1f, 0.15f, 0.1f, 1f);
                    x -= 0.35f;
                    continue;
                }
                seven(bb, m, x, y0, z, launched ? 8 : dgt, launched && ((int) (time / 5)) % 2 == 0 ? 0.25f : 1f);
                x -= 1.15f;
            }
        }
        // las cámaras de vigilancia en la caseta (en a = -27,5, mirando hacia adelante)
        for (int b : new int[]{-3, -2}) {
            float cx = -b, z = NukePlan.STAIR_A - 2.48f, y0 = 1.1f, y1 = 1.9f;
            glowRect(bb, m, cx - 0.44f, y0, cx + 0.44f, y1, z, 0.25f, 0.28f, 0.3f, 0.5f);
            float scan = (time * 0.03f + b) % 1f;
            glowRect(bb, m, cx - 0.44f, y0 + scan * 0.8f, cx + 0.44f, y0 + scan * 0.8f + 0.04f, z + 0.004f, 0.8f, 0.85f, 0.9f, 0.4f);
            // la boca del silo vista desde arriba
            glowRect(bb, m, cx - 0.18f, 1.35f, cx + 0.18f, 1.65f, z + 0.005f, 0.6f, 0.6f, 0.55f, 0.5f);
            if (((int) (time / 10)) % 2 == 0) glowRect(bb, m, cx + 0.3f, 1.78f, cx + 0.38f, 1.86f, z + 0.006f, 1f, 0.1f, 0.1f, 1f);
        }
    }

    /** Un dígito de 7 segmentos (ancho 0,8, alto 0,76) con la esquina de abajo a la derecha en (x, y). */
    private static void seven(BufferBuilder bb, Matrix4f m, float x, float y, float z, int d, float bright) {
        boolean[][] seg = {
                {true, true, true, true, true, true, false}, {false, true, true, false, false, false, false},
                {true, true, false, true, true, false, true}, {true, true, true, true, false, false, true},
                {false, true, true, false, false, true, true}, {true, false, true, true, false, true, true},
                {true, false, true, true, true, true, true}, {true, true, true, false, false, false, false},
                {true, true, true, true, true, true, true}, {true, true, true, true, false, true, true}};
        boolean[] on = seg[Mth.clamp(d, 0, 9)];
        float w = 0.62f, h = 0.76f, t = 0.08f;
        // x crece hacia la izquierda en este sistema (x = -b): el dígito ocupa de x a x - w... se dibuja de x-w a x
        float l = x - w, r = x;
        float[][] rects = {
                {l + t, y + h - t, r - t, y + h},          // a arriba
                {l, y + h / 2, l + t, y + h - t},          // b arriba a la derecha (visto de frente)
                {l, y + t, l + t, y + h / 2},              // c abajo a la derecha
                {l + t, y, r - t, y + t},                  // d abajo
                {r - t, y + t, r, y + h / 2},              // e abajo a la izquierda
                {r - t, y + h / 2, r, y + h - t},          // f arriba a la izquierda
                {l + t, y + h / 2 - t / 2, r - t, y + h / 2 + t / 2}};  // g centro
        for (int i = 0; i < 7; i++) {
            float k = on[i] ? bright : 0.08f;
            glowRect(bb, m, rects[i][0], rects[i][1], rects[i][2], rects[i][3], z, 1f, 0.12f, 0.08f, k);
        }
    }
}
