package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.GarageColors;
import com.example.beautifuldecor.client.PaintMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Prueba del botón "Otros" (V3.6.0), con el Java de verdad: la tabla de partes de cada vehículo, el texto sincronizado de los colores
 * (PaintExtras), VehiclePaint con los canales de "Otros", el repintado (PaintMath) con las texturas y máscaras REALES contra la cuenta de
 * Python, que los neumáticos y las hélices o el rotor NUNCA cambien de color, y los colores de la puerta de garaje (GarageColors).
 * Uso: OthersTest <carpeta de texturas> <archivo de casos>
 * Casos:  EXTRA nombre canal color esperado.png | ALL nombre esperado.png | FORBIDDEN nombre mascara.png
 */
public final class OthersTest {
    static int fails;

    static void check(boolean cond, String msg) {
        if (!cond) {
            fails++;
            System.out.println("FALLA: " + msg);
        }
    }

    static final class Fake implements PaintableVehicle {
        final VehiclePaint.Kind kind;
        final int[] raw = new int[VehiclePaint.CHANNELS];
        int[] preview;

        Fake(VehiclePaint.Kind kind) {
            this.kind = kind;
            Arrays.fill(this.raw, -1);
        }

        public VehiclePaint.Kind paintKind() {
            return this.kind;
        }

        public int getPaintRaw(int channel) {
            return channel >= 0 && channel < this.raw.length ? this.raw[channel] : -1;
        }

        public void setPaintRaw(int channel, int v) {
            if (channel >= 0 && channel < this.raw.length) {
                this.raw[channel] = VehiclePaint.clean(this.kind, channel, v);
            }
        }

        public int[] getPaintPreview() {
            return this.preview;
        }

        public void setPaintPreview(int[] colors) {
            this.preview = colors;
        }
    }

    // ------------------------------------------------------------------ imágenes
    static int[] pixels(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                out[y * w + x] = ((argb >>> 24) << 24) | ((argb & 255) << 16) | (((argb >> 8) & 255) << 8) | ((argb >> 16) & 255);   // NativeImage
            }
        }
        return out;
    }

    static int[] load(File f) throws Exception {
        return pixels(ImageIO.read(f));
    }

    static int[] refs(VehiclePaint.Kind kind) {
        int[] ref = new int[VehiclePaint.CHANNELS];
        for (int ch = 0; ch < ref.length; ch++) {
            ref[ch] = kind.reference(ch);
        }
        return ref;
    }

    static VehiclePaint.Kind kindOf(String texture) {
        for (VehiclePaint.Kind k : VehiclePaint.Kind.values()) {
            if (k.texture().equals(texture)) {
                return k;
            }
        }
        throw new IllegalArgumentException(texture);
    }

    static int worstDiff(int[] a, int[] b) {
        int worst = 0;
        for (int i = 0; i < a.length; i++) {
            for (int sh = 0; sh < 32; sh += 8) {
                worst = Math.max(worst, Math.abs(((a[i] >>> sh) & 255) - ((b[i] >>> sh) & 255)));
            }
        }
        return worst;
    }

    /** Colores llamativos y distintos para pedir "todo cambiado". */
    static int[] garish(VehiclePaint.Kind kind) {
        int[] c = refs(kind);
        for (int ch = 0; ch < c.length; ch++) {
            if (c[ch] >= 0) {
                c[ch] = new int[] {0xFF00FF, 0x00FFFF, 0xFFFF00, 0x00FF00, 0xFF0000, 0x0000FF}[ch % 6];
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ tabla de partes
    static void tables() {
        check(VehiclePaint.MAIN == 3 && VehiclePaint.EXTRA_FIRST == 3, "los tres canales de siempre van primero");
        check(VehiclePaintExtras.MAX == 14, "tope de partes de Otros: 14");
        check(VehiclePaint.CHANNELS == VehiclePaint.MAIN + VehiclePaintExtras.MAX, "CHANNELS = 3 + tope");
        for (VehiclePaint.Kind k : VehiclePaint.Kind.values()) {
            String[] keys = VehiclePaintExtras.keys(k.texture());
            int[] refs = VehiclePaintExtras.refs(k.texture());
            check(keys.length == refs.length, k + ": claves y colores de referencia no tienen el mismo largo");
            check(keys.length >= 6 && keys.length <= VehiclePaintExtras.MAX, k + ": debe tener entre 6 y 14 partes (tiene " + keys.length + ")");
            check(k.extraCount() == keys.length, k + ": extraCount no coincide con la tabla");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < keys.length; i++) {
                check(seen.add(keys[i]), k + ": clave repetida " + keys[i]);
                check(refs[i] >= 0 && refs[i] <= 0xFFFFFF, k + ": color de referencia fuera de rango en " + keys[i]);
                String low = keys[i].toLowerCase();
                check(!low.contains("tire") && !low.contains("wheel") && !low.contains("rotor") && !low.contains("blade") && !low.contains("propeller")
                        && !low.contains("neum") && !low.contains("helice"), k + ": " + keys[i] + " no se puede cambiar (neumáticos, hélices o rotor)");
                check(k.extraKey(i).equals(keys[i]), k + ": extraKey " + i);
            }
            check(keys[keys.length - 1].equals("details"), k + ": la última parte es 'details' (lo que no cae en ninguna otra)");
            for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
                boolean want = ch < VehiclePaint.MAIN ? k.reference(ch) >= 0 : ch - VehiclePaint.EXTRA_FIRST < keys.length;
                check(k.has(ch) == want, k + ": has(" + ch + ") debía ser " + want);
                if (ch >= VehiclePaint.EXTRA_FIRST) {
                    int expected = ch - VehiclePaint.EXTRA_FIRST < refs.length ? refs[ch - VehiclePaint.EXTRA_FIRST] : -1;
                    check(k.reference(ch) == expected, k + ": reference(" + ch + ")");
                }
            }
            check(!k.has(VehiclePaint.CHANNELS) && !k.has(-1) && !k.has(99), k + ": canales fuera de rango no existen");
        }
        // lo que cada vehículo debe poder cambiar
        for (VehiclePaint.Kind k : VehiclePaint.Kind.values()) {
            List<String> keys = Arrays.asList(VehiclePaintExtras.keys(k.texture()));
            check(keys.contains("glass") || k == VehiclePaint.Kind.BICYCLE, k + ": los vidrios se pueden cambiar");
            check(keys.contains("seat") || k == VehiclePaint.Kind.BICYCLE, k + ": los asientos se pueden cambiar");
        }
        check(Arrays.asList(VehiclePaintExtras.keys("helicopter")).contains("skid") && Arrays.asList(VehiclePaintExtras.keys("helicopter")).contains("accent"),
                "helicóptero: patines y acentos");
        check(VehiclePaintExtras.keys("nada").length == 0 && VehiclePaintExtras.refs("nada").length == 0, "textura desconocida: sin partes");
    }

    // ------------------------------------------------------------------ el texto sincronizado
    static void codec() {
        int[] none = PaintExtras.decode("");
        check(none.length == VehiclePaintExtras.MAX && Arrays.stream(none).allMatch(v -> v == -1), "texto vacío = todo original");
        check(PaintExtras.decode(null).length == VehiclePaintExtras.MAX, "texto nulo no revienta");
        int[] all = new int[VehiclePaintExtras.MAX];
        Arrays.fill(all, -1);
        check(PaintExtras.encode(all).isEmpty(), "todo original se codifica como texto vacío");
        all[2] = 0x123456;
        all[13] = 0;
        String text = PaintExtras.encode(all);
        check(!text.isEmpty() && text.split(",", -1).length == VehiclePaintExtras.MAX, "un texto por canal");
        int[] back = PaintExtras.decode(text);
        check(Arrays.equals(back, all), "ida y vuelta del texto: " + text + " -> " + Arrays.toString(back));
        check(back[13] == 0, "el negro (0) no es lo mismo que 'original' (-1)");
        Random rnd = new Random(7);
        for (int n = 0; n < 200; n++) {
            int[] v = new int[VehiclePaintExtras.MAX];
            for (int i = 0; i < v.length; i++) {
                v[i] = rnd.nextInt(3) == 0 ? -1 : rnd.nextInt(0x1000000);
            }
            check(Arrays.equals(PaintExtras.decode(PaintExtras.encode(v)), v), "ida y vuelta aleatoria " + n);
        }
        String t = PaintExtras.with("", 4, 0xABCDEF);
        PaintExtras cache = new PaintExtras();
        check(cache.get(t, 4) == 0xABCDEF && cache.get(t, 3) == -1, "with() cambia solo el canal pedido");
        String t2 = PaintExtras.with(t, 7, 0x010203);
        check(cache.get(t2, 4) == 0xABCDEF && cache.get(t2, 7) == 0x010203, "get() al cambiar el texto vuelve a leer");
        check(cache.get(t, 7) == -1, "get() con el texto anterior vuelve a leer (no se queda con lo viejo)");
        check(PaintExtras.with(t, 4, -1).isEmpty(), "quitar el único color deja el texto vacío");
        check(PaintExtras.with(t, 99, 5).equals(t) && PaintExtras.with(t, -1, 5).equals(t), "canal fuera de rango se ignora");
        check(cache.get(t, -1) == -1 && cache.get(t, 99) == -1, "get() fuera de rango = original");
        int[] odd = PaintExtras.decode("abc,12,,-5,99999999, 77 ");
        check(odd[0] == -1 && odd[1] == 12 && odd[2] == -1 && odd[3] == -1 && odd[4] == (99999999 & 0xFFFFFF) && odd[5] == 77 && odd[6] == -1,
                "texto raro: " + Arrays.toString(Arrays.copyOf(odd, 7)));
    }

    // ------------------------------------------------------------------ VehiclePaint con Otros
    static void logic() {
        VehiclePaint.Kind pickup = VehiclePaint.Kind.PICKUP;
        int glass = VehiclePaint.EXTRA_FIRST + Arrays.asList(VehiclePaintExtras.keys("ford_f150")).indexOf("glass");
        int glassRef = pickup.reference(glass);
        check(glassRef == VehiclePaintExtras.refs("ford_f150")[glass - VehiclePaint.EXTRA_FIRST], "la referencia del vidrio sale de la tabla");
        check(VehiclePaint.clean(pickup, glass, glassRef) == -1, "el color original de una parte se guarda como -1");
        check(VehiclePaint.clean(pickup, glass, 0xFF000000 | glassRef) == -1, "el alfa no cuenta en las partes");
        check(VehiclePaint.clean(pickup, glass, 0x00FF00) == 0x00FF00, "un color nuevo en una parte se guarda");
        check(VehiclePaint.clean(pickup, pickup.extraCount() + VehiclePaint.EXTRA_FIRST, 0x123456) == -1, "una parte que el vehículo no tiene se ignora");
        check(VehiclePaint.clean(VehiclePaint.Kind.BICYCLE, VehiclePaint.EXTRA_FIRST + 9, 0x123456) == -1, "la bici tiene menos partes que la camioneta");

        Fake f = new Fake(pickup);
        int[] c = VehiclePaint.colors(f);
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            check(c[ch] == (pickup.has(ch) ? pickup.reference(ch) : -1), "colores sin repintar, canal " + ch);
        }
        check(!VehiclePaint.isCustom(f), "sin repintar no es custom");
        f.setPaintRaw(glass, 0x00FF00);
        check(VehiclePaint.isCustom(f) && VehiclePaint.colors(f)[glass] == 0x00FF00 && VehiclePaint.colors(f)[VehiclePaint.BODY] == 0x122664,
                "pintar solo el vidrio");
        int[] prev = VehiclePaint.colors(f).clone();
        prev[glass] = 0x112233;
        f.setPaintPreview(prev);
        check(VehiclePaint.colors(f)[glass] == 0x112233, "la vista previa manda sobre lo guardado también en Otros");
        f.setPaintPreview(null);

        // ítem y NBT con partes de Otros
        ItemStack stack = VehiclePaint.carry(f, new ItemStack());
        Fake g = new Fake(pickup);
        VehiclePaint.restore(g, stack);
        check(g.raw[glass] == 0x00FF00 && g.raw[0] == -1, "el ítem se lleva el color de una parte de Otros");
        CompoundTag tag = new CompoundTag();
        VehiclePaint.write(f, tag);
        Fake h = new Fake(pickup);
        VehiclePaint.read(h, tag);
        check(Arrays.equals(h.raw, f.raw), "NBT: ida y vuelta con partes de Otros");
        CompoundTag old = new CompoundTag();
        old.putIntArray("VehiclePaint", new int[] {0xAA0000, -1, 0x00AA00});
        Fake o = new Fake(pickup);
        o.setPaintRaw(glass, 0x00FF00);
        VehiclePaint.read(o, old);
        check(o.raw[0] == 0xAA0000 && o.raw[2] == 0x00AA00 && o.raw[glass] == -1, "NBT de la V3.5 (3 valores): las partes de Otros quedan originales");
        CompoundTag moto = new CompoundTag();
        int[] big = new int[VehiclePaint.CHANNELS];
        Arrays.fill(big, 0x010203);
        moto.putIntArray("VehiclePaint", big);
        Fake m = new Fake(VehiclePaint.Kind.MOTORCYCLE);
        VehiclePaint.read(m, moto);
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            check(m.raw[ch] == (VehiclePaint.Kind.MOTORCYCLE.has(ch) ? 0x010203 : -1), "NBT: la moto solo se queda con sus canales (" + ch + ")");
        }
    }

    // ------------------------------------------------------------------ repintado con las texturas de verdad
    static void paint(File dir, List<String> lines) throws Exception {
        int cases = 0;
        int worstOverall = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            VehiclePaint.Kind kind = kindOf(t[1]);
            int[] base = load(new File(dir, kind.texture() + ".png"));
            int[] mask = load(new File(dir, kind.texture() + "_paint.png"));
            int[] regions = load(new File(dir, kind.texture() + "_regions.png"));
            int[] ref = refs(kind);
            switch (t[0]) {
                case "EXTRA" -> {
                    int ch = Integer.parseInt(t[2]);
                    int[] news = ref.clone();
                    news[ch] = Integer.parseInt(t[3], 16);
                    int[] got = PaintMath.recolor(base, mask, regions, ref, news);
                    int[] want = load(new File(t[4]));
                    int worst = worstDiff(got, want);
                    worstOverall = Math.max(worstOverall, worst);
                    cases++;
                    check(worst <= 1, kind + " canal " + ch + " -> " + t[3] + ": difiere de Python (peor " + worst + ")");
                    check(!Arrays.equals(got, base), kind + " canal " + ch + ": cambiar el color no cambió nada");
                }
                case "ALL" -> {
                    int[] news = garish(kind);
                    int[] got = PaintMath.recolor(base, mask, regions, ref, news);
                    int[] want = load(new File(t[2]));
                    int worst = worstDiff(got, want);
                    worstOverall = Math.max(worstOverall, worst);
                    cases++;
                    check(worst <= 1, kind + " todo cambiado: difiere de Python (peor " + worst + ")");
                }
                case "FORBIDDEN" -> {
                    int[] forbidden = load(new File(t[2]));
                    int[] got = PaintMath.recolor(base, mask, regions, ref, garish(kind));
                    int touched = 0;
                    int total = 0;
                    for (int i = 0; i < base.length; i++) {
                        if ((forbidden[i] & 255) > 0) {
                            total++;
                            if (got[i] != base[i]) {
                                touched++;
                            }
                        }
                    }
                    cases++;
                    check(total > 100, kind + ": la máscara de lo que no se puede cambiar está vacía");
                    check(touched == 0, kind + ": " + touched + " píxeles de neumáticos o hélices cambiaron de color");
                }
                default -> throw new IllegalArgumentException(line);
            }
        }
        for (VehiclePaint.Kind k : VehiclePaint.Kind.values()) {
            int[] base = load(new File(dir, k.texture() + ".png"));
            int[] mask = load(new File(dir, k.texture() + "_paint.png"));
            int[] regions = load(new File(dir, k.texture() + "_regions.png"));
            check(Arrays.equals(PaintMath.recolor(base, mask, regions, refs(k), refs(k)), base), k + ": con los colores de siempre cambió la textura");
            int[] same = PaintMath.recolor(base, mask, refs(k), refs(k));
            check(Arrays.equals(same, base), k + ": la versión sin Otros dejó de dar la textura de siempre");
            // pedir color para una parte que el vehículo no tiene no cambia nada
            int[] ask = refs(k);
            for (int ch = 0; ch < ask.length; ch++) {
                if (ask[ch] < 0) {
                    ask[ch] = 0xFF00FF;
                }
            }
            check(Arrays.equals(PaintMath.recolor(base, mask, regions, refs(k), ask), base), k + ": un canal que no existe cambió la textura");
            // una región con un solo pixel de otro número (0 = nada) no rompe: región fuera de la tabla se ignora
            int[] weird = regions.clone();
            weird[0] = 0xFF000000 | (100 << 8) | 200;
            int[] w = PaintMath.recolor(base, mask, weird, refs(k), garish(k));
            check(w.length == base.length, k + ": una región fuera de la tabla no rompe");
        }
        System.out.println("OTHERS PAINT OK (" + cases + " casos, peor diferencia " + worstOverall + " de 255)");
    }

    // ------------------------------------------------------------------ puerta de garaje
    static void garage() {
        check(GarageColors.SLOTS == 8 && GarageColors.OTHERS_FIRST == 3, "la puerta tiene 8 ranuras, 3 de siempre");
        int[] d = GarageColors.defaults();
        check(d.length == GarageColors.SLOTS, "defaults() tiene una ranura por color");
        check(d[GarageColors.WINDOW] == 0x607C98 && d[GarageColors.HANDLE] == 0xD6DAE2 && d[GarageColors.SEAL] == 0x202024
                && d[GarageColors.HARDWARE] == 0xB0B4BE && d[GarageColors.VENTS] == 0x0E0E11, "colores por defecto de Otros");
        // lo de siempre no cambió
        check(GarageColors.tint(0, d) == 0x434753, "tinte de la puerta con el color por defecto: " + Integer.toHexString(GarageColors.tint(0, d)));
        check(GarageColors.tint(1, d) == 0xCE1C24, "tinte de la franja");
        check(GarageColors.tint(2, d) == 0x23252B, "tinte del marco: " + Integer.toHexString(GarageColors.tint(2, d)));
        check(GarageColors.tint(3, d) == 0x9097A4, "tinte de la cara interior: " + Integer.toHexString(GarageColors.tint(3, d)));
        // lo nuevo
        check(GarageColors.tint(4, d) == GarageColors.DEFAULT_WINDOW, "ventanas por fuera");
        check(GarageColors.tint(5, d) == GarageColors.DEFAULT_WINDOW_INSIDE, "ventanas por dentro con el color por defecto");
        check(GarageColors.tint(6, d) == GarageColors.DEFAULT_HANDLE, "manija");
        check(GarageColors.tint(7, d) == GarageColors.DEFAULT_HARDWARE, "bisagras y rodillos");
        check(GarageColors.tint(8, d) == GarageColors.DEFAULT_SEAL, "sello");
        check(GarageColors.tint(9, d) == GarageColors.DEFAULT_VENTS, "rejillas");
        check(GarageColors.tint(10, d) == 0xFFFFFF && GarageColors.tint(-1, d) == 0xFFFFFF, "tinte desconocido = sin tinte");
        int[] c = d.clone();
        c[GarageColors.WINDOW] = 0x112233;
        c[GarageColors.HANDLE] = 0x445566;
        c[GarageColors.SEAL] = 0x778899;
        c[GarageColors.HARDWARE] = 0xAABBCC;
        c[GarageColors.VENTS] = 0xDDEEFF;
        check(GarageColors.tint(4, c) == 0x112233 && GarageColors.tint(6, c) == 0x445566 && GarageColors.tint(8, c) == 0x778899
                && GarageColors.tint(7, c) == 0xAABBCC && GarageColors.tint(9, c) == 0xDDEEFF, "cada tinte sale de su ranura");
        check(GarageColors.tint(5, c) == GarageColors.windowInside(0x112233), "el tinte de dentro sale del color de las ventanas");
        check(GarageColors.windowInside(0xFFFFFF) == 0xFFFFFF, "ventanas blancas: por dentro también");
        check(GarageColors.windowInside(0) == 0xADE2FF, "ventanas negras: por dentro " + Integer.toHexString(GarageColors.windowInside(0)));
        int prev = -1;
        for (int g = 0; g <= 255; g += 15) {
            int inside = GarageColors.windowInside(g << 8) >> 8 & 255;
            check(inside >= prev, "ventanas: más claro por fuera = no más oscuro por dentro");
            prev = inside;
        }
        for (int slot = GarageColors.OTHERS_FIRST; slot < GarageColors.SLOTS; slot++) {
            check(GarageColors.visible(slot, 0x123456) == 0x123456, "las ranuras de Otros se ven tal cual (" + slot + ")");
        }
        Set<String> keys = new HashSet<>();
        for (int s = 0; s < GarageColors.SLOTS; s++) {
            check(keys.add(GarageColors.slotKey(s)), "clave de ranura repetida " + s);
        }
        check(GarageColors.slotKey(GarageColors.WINDOW).equals("window") && GarageColors.slotKey(GarageColors.VENTS).equals("vents"), "claves de las ranuras");
    }

    public static void main(String[] args) throws Exception {
        tables();
        codec();
        logic();
        garage();
        paint(new File(args[0]), Files.readAllLines(new File(args[1]).toPath()));
        if (fails == 0) {
            System.out.println("OTHERS OK");
        } else {
            System.out.println(fails + " fallas");
            System.exit(1);
        }
    }
}
