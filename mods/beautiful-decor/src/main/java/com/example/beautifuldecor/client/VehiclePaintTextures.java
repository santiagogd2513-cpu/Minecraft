package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.PaintableVehicle;
import com.example.beautifuldecor.entity.VehiclePaint;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Texturas repintadas de los vehículos (V3.2.0). Si un vehículo tiene sus colores originales se usa la textura de siempre;
 * si no, se calcula UNA vez una textura nueva con {@link PaintMath} y se comparte entre todos los vehículos que tengan
 * esos mismos colores. Hay un tope de texturas repintadas a la vez: al pasarse se suelta la que lleva más tiempo sin usarse.
 */
public final class VehiclePaintTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXTURES = 48;

    /** {@code regions} es la máscara de "Otros" (V3.6.0); null si el vehículo no la tiene. */
    private record Layers(int width, int height, int[] base, int[] mask, int[] regions) {
    }

    private static final Map<VehiclePaint.Kind, Layers> LAYERS = new EnumMap<>(VehiclePaint.Kind.class);
    private static final Set<VehiclePaint.Kind> BROKEN = EnumSet.noneOf(VehiclePaint.Kind.class);
    private static final LinkedHashMap<String, ResourceLocation> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static int counter;

    private VehiclePaintTextures() {
    }

    /** La textura con la que hay que dibujar este vehículo ahora mismo (la original si no está repintado). */
    public static ResourceLocation textureFor(PaintableVehicle vehicle, ResourceLocation original) {
        VehiclePaint.Kind kind = vehicle.paintKind();
        int[] colors = VehiclePaint.colors(vehicle);
        boolean custom = false;
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            if (kind.has(ch) && colors[ch] != kind.reference(ch)) {
                custom = true;
            }
        }
        if (!custom || BROKEN.contains(kind)) {
            return original;
        }
        StringBuilder keyBuilder = new StringBuilder(kind.name());
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            keyBuilder.append(':').append(colors[ch]);
        }
        String key = keyBuilder.toString();
        ResourceLocation id = CACHE.get(key);
        if (id != null) {
            return id;
        }
        id = build(kind, colors, key);
        return id != null ? id : original;
    }

    private static ResourceLocation build(VehiclePaint.Kind kind, int[] colors, String key) {
        try {
            Layers layers = LAYERS.get(kind);
            if (layers == null) {
                layers = load(kind);
                LAYERS.put(kind, layers);
            }
            int[] ref = new int[VehiclePaint.CHANNELS];
            for (int ch = 0; ch < ref.length; ch++) {
                ref[ch] = kind.reference(ch);
            }
            int[] pixels = PaintMath.recolor(layers.base(), layers.mask(), layers.regions(), ref, colors);
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, layers.width(), layers.height(), false);
            for (int y = 0; y < layers.height(); y++) {
                for (int x = 0; x < layers.width(); x++) {
                    image.setPixelRGBA(x, y, pixels[y * layers.width() + x]);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID,
                    "vehicle_paint/" + kind.texture() + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(key, id);
            evict();
            return id;
        } catch (Throwable t) {
            // Sin máscara o sin textura: ese vehículo se queda con sus colores de siempre.
            BROKEN.add(kind);
            LOGGER.error("[BeautifulDecor] No se pudo repintar el vehículo {}: {}", kind, t.toString());
            return null;
        }
    }

    private static Layers load(VehiclePaint.Kind kind) throws IOException {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        int[][] base = read(manager, "textures/entity/" + kind.texture() + ".png");
        int[][] mask = read(manager, "textures/entity/" + kind.texture() + "_paint.png");
        if (base[0][0] != mask[0][0] || base[0][1] != mask[0][1]) {
            throw new IOException("la máscara de " + kind.texture() + " no mide lo mismo que la textura");
        }
        // V3.6.0: la máscara de "Otros" es opcional (sin ella el vehículo solo cambia pintura, manijas y rines)
        int[] regions = null;
        if (manager.getResource(ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID,
                "textures/entity/" + kind.texture() + "_regions.png")).isPresent()) {
            int[][] extra = read(manager, "textures/entity/" + kind.texture() + "_regions.png");
            if (extra[0][0] != base[0][0] || extra[0][1] != base[0][1]) {
                throw new IOException("la máscara de otros de " + kind.texture() + " no mide lo mismo que la textura");
            }
            regions = extra[1];
        }
        return new Layers(base[0][0], base[0][1], base[1], mask[1], regions);
    }

    /** {{ancho, alto}, píxeles 0xAABBGGRR}. */
    private static int[][] read(ResourceManager manager, String path) throws IOException {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, path);
        Optional<Resource> resource = manager.getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("falta " + location);
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = image.getPixelRGBA(x, y);
                }
            }
            return new int[][] {{w, h}, pixels};
        }
    }

    private static void evict() {
        Iterator<Map.Entry<String, ResourceLocation>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Map.Entry<String, ResourceLocation> eldest = it.next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            it.remove();
        }
    }

    /** Suelta todas las texturas repintadas (al recargar recursos o al salir del mundo). */
    public static void reset() {
        for (ResourceLocation id : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        CACHE.clear();
        LAYERS.clear();
        BROKEN.clear();
    }
}
