package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.browser.Http;
import com.example.beautifuldecor.browser.Layout;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Imágenes de la página del navegador HS (V2.4.0): se bajan y decodifican en hilos aparte (con el decodificador STB de
 * Minecraft: PNG, JPEG, GIF -primer cuadro-, BMP) y se suben a la tarjeta gráfica desde el hilo principal.
 * WebP y SVG no se pueden leer: la página muestra el texto alternativo en su lugar.
 */
final class PageImages implements Layout.Images {
    private static final int MAX_IMAGES = 60;
    private static final int MAX_BYTES = 8 << 20;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "HS Images");
        t.setDaemon(true);
        return t;
    });

    private static final int LOADING = 0;
    private static final int DECODED = 1;
    private static final int READY = 2;
    private static final int FAILED = 3;

    private static final class Entry {
        volatile int state = LOADING;
        volatile int w;
        volatile int h;
        volatile NativeImage image;
        ResourceLocation id;
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private volatile int version;
    private volatile boolean closed;
    private volatile int generation;

    /** Sube cada vez que llega una imagen (o falla) para que la pantalla vuelva a acomodar la página. */
    int version() {
        return this.version;
    }

    @Override
    public int[] size(String src) {
        Entry e = this.map.get(src);
        if (e == null || e.state == LOADING) {
            return null;
        }
        if (e.state == FAILED) {
            return new int[]{0, 0};
        }
        return new int[]{e.w, e.h};
    }

    void request(Collection<String> sources, String referer) {
        int gen = this.generation;
        for (String src : sources) {
            if (this.map.size() >= MAX_IMAGES) {
                return;
            }
            Entry fresh = new Entry();
            if (this.map.putIfAbsent(src, fresh) != null) {
                continue;
            }
            POOL.execute(() -> load(gen, src, fresh, referer));
        }
    }

    private void load(int gen, String src, Entry e, String referer) {
        NativeImage img = null;
        try (Http.Response r = Http.get(src, referer)) {
            if (r.status >= 400) {
                throw new IllegalStateException("HTTP " + r.status);
            }
            boolean[] cut = new boolean[1];
            byte[] data = Http.readLimited(r.body, MAX_BYTES, cut);
            if (cut[0]) {
                throw new IllegalStateException("too big");
            }
            img = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(data));
            if ((long) img.getWidth() * img.getHeight() > MAX_PIXELS) {
                throw new IllegalStateException("too many pixels");
            }
            if (this.closed || gen != this.generation) {
                img.close();
                return;
            }
            e.w = img.getWidth();
            e.h = img.getHeight();
            e.image = img;
            e.state = DECODED;
            this.version++;
        } catch (Throwable t) {
            if (img != null) {
                try {
                    img.close();
                } catch (Throwable ignored) {
                    // ya estaba cerrada
                }
            }
            e.state = FAILED;
            this.version++;
        }
    }

    /** Desde el hilo principal: sube a la GPU las imágenes que ya están decodificadas (unas pocas por cuadro). */
    void pump() {
        int budget = 2;
        for (Map.Entry<String, Entry> me : this.map.entrySet()) {
            Entry e = me.getValue();
            if (e.state != DECODED || budget <= 0) {
                continue;
            }
            NativeImage img = e.image;
            e.image = null;
            if (img == null) {
                continue;
            }
            try {
                DynamicTexture tex = new DynamicTexture(img);
                tex.setFilter(true, false);
                e.id = new ResourceLocation(BeautifulDecor.MOD_ID, "hs_browser/img_" + IDS.incrementAndGet());
                Minecraft.getInstance().getTextureManager().register(e.id, tex);
                e.state = READY;
            } catch (Throwable t) {
                img.close();
                e.state = FAILED;
                this.version++;
            }
            budget--;
        }
    }

    /** Dibuja la imagen si ya está lista. Devuelve false si todavía no. */
    boolean draw(GuiGraphics gui, String src, int x, int y, int w, int h) {
        Entry e = this.map.get(src);
        if (e == null || e.state != READY || e.id == null) {
            return false;
        }
        gui.setColor(0.82f, 0.82f, 0.82f, 1.0f);
        gui.blit(e.id, x, y, w, h, 0.0f, 0.0f, e.w, e.h, e.w, e.h);
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        return true;
    }

    /** Suelta todas las imágenes (al cambiar de página o al cerrar la pantalla). */
    void clear() {
        this.generation++;
        for (Entry e : this.map.values()) {
            release(e);
        }
        this.map.clear();
        this.version++;
    }

    void close() {
        this.closed = true;
        clear();
    }

    private static void release(Entry e) {
        NativeImage img = e.image;
        e.image = null;
        if (img != null) {
            try {
                img.close();
            } catch (Throwable ignored) {
                // ya estaba cerrada
            }
        }
        if (e.state == READY && e.id != null) {
            Minecraft.getInstance().getTextureManager().release(e.id);
            e.state = FAILED;
        }
    }
}
