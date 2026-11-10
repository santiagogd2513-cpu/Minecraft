package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.example.beautifuldecor.block.entity.SecurityMonitorBlockEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * V4.3.0: las imágenes en vivo de las cámaras de seguridad. Para cada cámara que se ve en un monitor cercano se mantiene un
 * «visor» propio (su propio LevelRenderer, centrado en la cámara, para no desordenar el del jugador) que dibuja el mundo desde la
 * cámara en una textura pequeña (320x128), unas pocas veces por segundo. Si es de noche o no hay luz, la imagen pasa por un filtro
 * verde con ganancia (visión nocturna). El dibujo del monitor solo pide la textura ({@link #view}).
 * <p>
 * Pensado para gastar poco (se hace en celulares): como mucho {@link #MAX_LIVE} cámaras a la vez, solo las de monitores cercanos y
 * de frente al jugador, 8 imágenes por segundo (menos si el juego va lento), un solo visor dibujado por cuadro. Si algo falla, se
 * apaga todo el sistema (los monitores muestran «NO DISPONIBLE») en vez de cerrar el juego. No funciona con gráficos «Fabuloso» ni
 * con Embeddium/Rubidium/Oculus (cambian el dibujo del mundo).
 */
public final class CameraFeedManager {
    public static final int W = 320;
    public static final int H = 128;
    public static final int MAX_LIVE = 2;
    private static final int MAX_FEEDS = 3;
    private static final long GRACE_MS = 15000L;
    private static final long DIRTY_MS = 5000L;
    private static final int WARMUP_FRAMES = 12;
    private static final double MAX_DISTANCE = 22.0;
    private static final float FOV = 58f;
    /** Pasadas extra de suma para la visión nocturna (la imagen sale unas 5 veces más clara). */
    private static final int NIGHT_GAIN_PASSES = 4;

    public enum Status {
        LIVE, CONNECTING, NO_SIGNAL, STANDBY, UNAVAILABLE
    }

    /** Lo que el monitor debe dibujar de una cámara: el estado y, si hay imagen, su textura y si es nocturna. */
    public record View(Status status, ResourceLocation texture, boolean night, String detail) {
    }

    /** Envoltorio de la textura de color de un RenderTarget para poder dibujarla como textura normal. */
    private static final class TargetTexture extends AbstractTexture {
        TargetTexture(int glId) {
            this.id = glId;
        }

        @Override
        public void load(ResourceManager manager) {
            // la textura ya existe: es la del RenderTarget
        }

        @Override
        public void releaseId() {
            // la borra el RenderTarget, no esta clase
        }

        @Override
        public void close() {
            // idem
        }
    }

    private static final class Feed {
        final long key;
        final BlockPos camPos;
        final ClientLevel level;
        LevelRenderer renderer;
        TextureTarget raw;
        TextureTarget out;
        ResourceLocation texture;
        int frames;
        long lastRender;
        long lastWanted;
        long lastDirty;
        boolean night;

        Feed(long key, BlockPos camPos, ClientLevel level) {
            this.key = key;
            this.camPos = camPos;
            this.level = level;
        }
    }

    private static final Map<Long, Feed> FEEDS = new HashMap<>();
    private static boolean broken;
    private static String brokenReason = "";
    private static boolean rendering;
    private static Marker dummy;
    private static Camera camera;
    private static long lastFrameAt;
    private static long frameMs = 16;
    private static int counter;

    private CameraFeedManager() {
    }

    /** true mientras se dibuja una imagen de cámara (los monitores que salgan en ella se dibujan apagados, sin recursión). */
    public static boolean isRendering() {
        return rendering;
    }

    /** true si el sistema se apagó por un error de dibujo (los monitores muestran NO DISPONIBLE). */
    public static boolean isBroken() {
        return broken;
    }

    // ------------------------------------------------------------------ lo que ve el monitor

    public static View view(BlockPos camPos) {
        if (broken) {
            return new View(Status.UNAVAILABLE, null, false, brokenReason);
        }
        String unsupported = unsupportedReason();
        if (unsupported != null) {
            return new View(Status.UNAVAILABLE, null, false, unsupported);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(camPos) instanceof SecurityCameraBlockEntity)) {
            return new View(Status.NO_SIGNAL, null, false, "");
        }
        Feed f = FEEDS.get(camPos.asLong());
        if (f == null) {
            return new View(Status.STANDBY, null, false, "");
        }
        if (f.frames < WARMUP_FRAMES) {
            return new View(Status.CONNECTING, null, false, "");
        }
        return new View(Status.LIVE, f.texture, f.night, "");
    }

    /** Por qué no se pueden dibujar imágenes de cámara en esta instalación (null si sí se puede). */
    private static String unsupportedReason() {
        try {
            if (Minecraft.useShaderTransparency()) {
                return "Gráficos Fabuloso";
            }
            for (String id : new String[]{"embeddium", "rubidium", "sodium", "oculus", "iris"}) {
                if (ModList.get().isLoaded(id)) {
                    return id;
                }
            }
        } catch (Throwable t) {
            return "";
        }
        return null;
    }

    // ------------------------------------------------------------------ cada cuadro

    /** Al empezar cada cuadro, antes de dibujar el mundo del jugador. Nunca lanza errores. */
    public static void onRenderTick(Minecraft mc, float partialTick) {
        if (broken) {
            return;
        }
        try {
            tickInner(mc, partialTick);
        } catch (Throwable t) {
            fail(mc, t);
        }
    }

    private static void tickInner(Minecraft mc, float partialTick) {
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) {
            closeAll(mc);
            return;
        }
        long now = System.currentTimeMillis();
        long dt = lastFrameAt == 0 ? 16 : Math.min(500, now - lastFrameAt);
        lastFrameAt = now;
        frameMs = (frameMs * 7 + dt) / 8;
        if (mc.isPaused() || unsupportedReason() != null) {
            return;
        }

        // 1) cámaras que se ven en monitores cercanos y de frente
        Map<Long, Double> wanted = new HashMap<>();
        Map<Long, BlockPos> positions = new HashMap<>();
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getLookAngle();
        for (SecurityMonitorBlockEntity monitor : SecurityMonitorBlockEntity.CLIENT) {
            BlockPos cam = monitor.getCamera();
            if (cam == null || monitor.isRemoved()) {
                continue;
            }
            BlockPos mp = monitor.getBlockPos();
            Vec3 center = Vec3.atCenterOf(mp).add(Vec3.atLowerCornerOf(monitor.facing().getCounterClockWise().getNormal()).scale(0.5));
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > MAX_DISTANCE || dist < 0.01) {
                continue;
            }
            Vec3 toN = to.scale(1.0 / dist);
            if (toN.dot(look) < 0.15) {
                continue;                                       // no lo estás mirando
            }
            Vec3 faceDir = Vec3.atLowerCornerOf(monitor.facing().getNormal());
            if (toN.dot(faceDir) > 0.05) {
                continue;                                       // estás detrás del monitor
            }
            BlockEntity be = level.getBlockEntity(cam);
            if (!(be instanceof SecurityCameraBlockEntity)) {
                continue;
            }
            wanted.merge(cam.asLong(), dist, Math::min);
            positions.put(cam.asLong(), cam);
        }
        List<Long> chosen = new ArrayList<>(wanted.keySet());
        chosen.sort((a, b) -> Double.compare(wanted.get(a), wanted.get(b)));
        if (chosen.size() > MAX_LIVE) {
            chosen = new ArrayList<>(chosen.subList(0, MAX_LIVE));
        }

        // 2) visores de otro mundo o sin usar hace rato, fuera
        for (Iterator<Feed> it = FEEDS.values().iterator(); it.hasNext(); ) {
            Feed f = it.next();
            if (f.level != level || now - f.lastWanted > GRACE_MS) {
                closeFeed(mc, f);
                it.remove();
            }
        }
        // 3) los que hacen falta, creados (si no caben, sale el menos reciente)
        for (long key : chosen) {
            Feed f = FEEDS.get(key);
            if (f == null) {
                while (FEEDS.size() >= MAX_FEEDS) {
                    Feed oldest = null;
                    for (Feed c : FEEDS.values()) {
                        if (!chosen.contains(c.key) && (oldest == null || c.lastWanted < oldest.lastWanted)) {
                            oldest = c;
                        }
                    }
                    if (oldest == null) {
                        break;
                    }
                    closeFeed(mc, oldest);
                    FEEDS.remove(oldest.key);
                }
                if (FEEDS.size() >= MAX_FEEDS) {
                    continue;
                }
                f = createFeed(mc, key, positions.get(key), level);
                FEEDS.put(key, f);
            }
            f.lastWanted = now;
        }

        // 4) un solo visor por cuadro: el que lleva más tiempo sin actualizarse, si le toca
        long interval = frameMs < 30 ? 125 : frameMs < 50 ? 200 : 350;
        Feed due = null;
        for (long key : chosen) {
            Feed f = FEEDS.get(key);
            if (f != null && now - f.lastRender >= interval && (due == null || f.lastRender < due.lastRender)) {
                due = f;
            }
        }
        if (due != null) {
            BlockEntity be = level.getBlockEntity(due.camPos);
            if (be instanceof SecurityCameraBlockEntity cam) {
                renderFeed(mc, due, cam, partialTick, now);
                due.lastRender = now;
            }
        }
    }

    // ------------------------------------------------------------------ visores

    private static Feed createFeed(Minecraft mc, long key, BlockPos camPos, ClientLevel level) {
        Feed f = new Feed(key, camPos, level);
        f.raw = new TextureTarget(W, H, true, Minecraft.ON_OSX);
        f.out = new TextureTarget(W, H, false, Minecraft.ON_OSX);
        f.renderer = new LevelRenderer(mc, mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(), mc.renderBuffers());
        f.renderer.setLevel(level);
        f.texture = new ResourceLocation("beautifuldecor", "camera_feed/" + (counter++));
        mc.getTextureManager().register(f.texture, new TargetTexture(f.out.getColorTextureId()));
        f.lastDirty = System.currentTimeMillis();
        return f;
    }

    private static void closeFeed(Minecraft mc, Feed f) {
        try {
            if (f.renderer != null) {
                f.renderer.setLevel(null);                      // suelta sus bloques y su memoria
            }
        } catch (Throwable ignored) {
            // nada más que hacer
        }
        try {
            if (mc.level != null) {
                mc.getEntityRenderDispatcher().setLevel(mc.level);   // setLevel(null) del visor se lo había quitado al del jugador
            }
        } catch (Throwable ignored) {
            // idem
        }
        try {
            if (f.texture != null) {
                mc.getTextureManager().release(f.texture);
            }
        } catch (Throwable ignored) {
            // idem
        }
        try {
            if (f.raw != null) {
                f.raw.destroyBuffers();
            }
            if (f.out != null) {
                f.out.destroyBuffers();
            }
        } catch (Throwable ignored) {
            // idem
        }
        f.renderer = null;
        f.raw = null;
        f.out = null;
    }

    /** Suelta todos los visores (al salir del mundo o cambiar de dimensión). */
    public static void closeAll(Minecraft mc) {
        if (FEEDS.isEmpty()) {
            return;
        }
        for (Feed f : FEEDS.values()) {
            closeFeed(mc, f);
        }
        FEEDS.clear();
    }

    public static void reset() {
        closeAll(Minecraft.getInstance());
        dummy = null;
        camera = null;
        lastFrameAt = 0;
    }

    private static void fail(Minecraft mc, Throwable t) {
        broken = true;
        brokenReason = t.getClass().getSimpleName();
        System.err.println("[BeautifulDecor] Las cámaras de seguridad se apagaron por un error de dibujo: " + t);
        t.printStackTrace();
        rendering = false;
        restoreState(mc);
        try {
            closeAll(mc);
        } catch (Throwable ignored) {
            // idem
        }
    }

    // ------------------------------------------------------------------ el dibujo de una imagen

    private static void renderFeed(Minecraft mc, Feed f, SecurityCameraBlockEntity cam, float partial, long now) {
        ClientLevel level = mc.level;
        float yaw = cam.currentYaw(level, partial);
        float pitch = cam.pitch();
        Vec3 lens = cam.lensPosition(yaw);
        cam.updateNight(level);
        f.night = cam.isNightVision();

        if (now - f.lastDirty > DIRTY_MS) {
            f.lastDirty = now;
            int x = (int) Math.floor(lens.x);
            int y = (int) Math.floor(lens.y);
            int z = (int) Math.floor(lens.z);
            f.renderer.setBlocksDirty(x - 64, y - 24, z - 64, x + 64, y + 24, z + 64);   // así se ven los bloques que cambian
        }

        if (dummy == null || dummy.level() != level) {
            dummy = new Marker(EntityType.MARKER, level);
        }
        dummy.setPos(lens.x, lens.y, lens.z);
        dummy.xo = lens.x;
        dummy.yo = lens.y;
        dummy.zo = lens.z;
        dummy.xOld = lens.x;
        dummy.yOld = lens.y;
        dummy.zOld = lens.z;
        dummy.setYRot(yaw);
        dummy.setXRot(pitch);
        dummy.yRotO = yaw;
        dummy.xRotO = pitch;
        if (camera == null) {
            camera = new Camera();
        }

        rendering = true;
        mc.getProfiler().push("beautifuldecor_camera_feed");
        RenderSystem.backupProjectionMatrix();
        try {
            camera.setup(level, dummy, false, false, 1.0f);

            Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(FOV), (float) W / (float) H, 0.05f, mc.gameRenderer.getDepthFar());
            PoseStack view = new PoseStack();
            view.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            view.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0f));
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(view.last().normal()).invert());

            f.raw.setClearColor(0f, 0f, 0f, 1f);
            f.raw.clear(Minecraft.ON_OSX);
            f.raw.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            f.renderer.prepareCullFrustum(view, camera.getPosition(), projection);
            f.renderer.renderLevel(view, partial, Util.getNanos(), false, camera, mc.gameRenderer, mc.gameRenderer.lightTexture(), projection);
            mc.getProfiler().pop();
            mc.getProfiler().push("beautifuldecor_camera_filter");
            postProcess(f);
            f.frames++;
        } finally {
            mc.getProfiler().pop();
            rendering = false;
            restoreState(mc);
            RenderSystem.restoreProjectionMatrix();
        }
    }

    /** Pasa la imagen cruda a la textura final: igual, o con el filtro verde y la ganancia de la visión nocturna. */
    private static void postProcess(Feed f) {
        f.out.setClearColor(0f, 0f, 0f, 1f);
        f.out.clear(Minecraft.ON_OSX);
        f.out.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.colorMask(true, true, true, false);          // el canal alfa se queda en 1
        Matrix4f ortho = new Matrix4f().setOrtho(0f, (float) W, (float) H, 0f, 1000f, 3000f);
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0f, 0f, -2000f);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, f.raw.getColorTextureId());
        if (f.night) {
            RenderSystem.setShaderColor(0.30f, 1.0f, 0.35f, 1.0f);
        } else {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        drawFullQuad();
        if (f.night) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            for (int i = 0; i < NIGHT_GAIN_PASSES; i++) {
                drawFullQuad();
            }
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawFullQuad() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(0.0, H, 0.0).uv(0f, 0f).endVertex();
        b.vertex(W, H, 0.0).uv(1f, 0f).endVertex();
        b.vertex(W, 0.0, 0.0).uv(1f, 1f).endVertex();
        b.vertex(0.0, 0.0, 0.0).uv(0f, 1f).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** Deja el dibujo como lo encontró el cuadro del jugador: su pantalla principal, sin restos de nuestros ajustes. */
    private static void restoreState(Minecraft mc) {
        try {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            PoseStack mv = RenderSystem.getModelViewStack();
            while (!mv.clear()) {
                mv.popPose();
            }
            mv.setIdentity();
            RenderSystem.applyModelViewMatrix();
            FogRenderer.setupNoFog();
            mc.getMainRenderTarget().bindWrite(true);
        } catch (Throwable ignored) {
            // idem
        }
    }
}
