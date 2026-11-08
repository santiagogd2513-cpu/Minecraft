package com.example.beautifuldecor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Colores que el jugador puede cambiar de un vehículo con la tecla R (V3.2.0): pintura de la carrocería, manijas de las
 * puertas y rines. Cada vehículo tiene los canales que de verdad tiene (la moto no tiene puertas, el helicóptero no tiene
 * ruedas...). El color "original" de cada canal es el de la textura de siempre; se guarda como -1 para no tocarla.
 * <p>
 * V3.6.0, botón "Otros": después de esos tres canales vienen hasta {@link VehiclePaintExtras#MAX} canales más, uno por cada parte del
 * vehículo (vidrios, asientos, luces, cromados...; ver {@link VehiclePaintExtras}). Lo único que no se puede pintar: los neumáticos y
 * las hélices o el rotor.
 * <p>
 * Los colores de referencia de abajo tienen que ser EXACTAMENTE los de los generadores de texturas
 * (tools/v200/moto_paint.py, tools/v210/{bike,truck}.py, tools/v300/{delivery_truck,helicopter}.py, tools/v370/supercar.py: variable REFS);
 * tools/verify_v320.py lo comprueba.
 */
public final class VehiclePaint {
    public static final int BODY = 0;
    public static final int HANDLE = 1;
    public static final int RIM = 2;
    /** Los tres canales de siempre (tienen su propia ficha en el menú). */
    public static final int MAIN = 3;
    /** V3.6.0: primer canal de "Otros". */
    public static final int EXTRA_FIRST = MAIN;
    /** Todos los canales: los tres de siempre y los de "Otros". */
    public static final int CHANNELS = MAIN + VehiclePaintExtras.MAX;
    /** Valor guardado cuando el canal tiene su color original. */
    public static final int ORIGINAL = -1;

    private static final String TAG = "VehiclePaint";

    /** Tipos de vehículo que se pueden pintar. El carro RC y el dron NO están aquí a propósito: no cuentan como vehículos. */
    public enum Kind {
        //          textura            pintura   manijas   rines
        MOTORCYCLE("motorcycle",     0xC81018, ORIGINAL, 0xCE121A),
        BICYCLE("bicycle",           0x26A8B8, ORIGINAL, 0xC4C8D0),
        PICKUP("ford_f150",          0x122664, 0xB0B6C2, 0xCED2DA),
        DELIVERY_TRUCK("delivery_truck", 0xECEEF4, 0xB0B6C2, 0xCED2DA),
        HELICOPTER("helicopter",     0xEAECF2, 0xB0B6C2, ORIGINAL),
        SUPERCAR("bugotti_chillon",  0x1854D6, 0x282A32, 0xEC9618),
        AIRLINER("airliner",         0xEAECF2, 0xB0B6C2, ORIGINAL);

        private final String texture;
        private final int[] reference;
        private final String[] extraKeys;
        private final int[] extraRefs;

        Kind(String texture, int body, int handle, int rim) {
            this.texture = texture;
            this.reference = new int[] {body, handle, rim};
            this.extraKeys = VehiclePaintExtras.keys(texture);
            this.extraRefs = VehiclePaintExtras.refs(texture);
        }

        /** Nombre del archivo de textura (textures/entity/NOMBRE.png y NOMBRE_paint.png). */
        public String texture() {
            return this.texture;
        }

        /** Si este vehículo tiene ese canal (manijas o rines pueden faltar; los de "Otros" son los de su lista). */
        public boolean has(int channel) {
            if (channel < 0 || channel >= CHANNELS) {
                return false;
            }
            return channel < MAIN ? this.reference[channel] >= 0 : channel - EXTRA_FIRST < this.extraKeys.length;
        }

        /** Color original (0xRRGGBB) del canal, o -1 si el vehículo no lo tiene. */
        public int reference(int channel) {
            if (!has(channel)) {
                return ORIGINAL;
            }
            return channel < MAIN ? this.reference[channel] : this.extraRefs[channel - EXTRA_FIRST];
        }

        /** Cuántos canales de "Otros" tiene este vehículo. */
        public int extraCount() {
            return this.extraKeys.length;
        }

        /** Clave de nombre del canal de "Otros" número {@code index} (gui.beautifuldecor.paint.CLAVE). */
        public String extraKey(int index) {
            return this.extraKeys[index];
        }
    }

    private VehiclePaint() {
    }

    /** Valor que se guarda/manda: -1 si es el original o el canal no existe; si no, el color sin canal alfa. */
    public static int clean(Kind kind, int channel, int rgb) {
        if (!kind.has(channel) || rgb == ORIGINAL || (rgb & 0xFFFFFF) == kind.reference(channel)) {
            return ORIGINAL;
        }
        return rgb & 0xFFFFFF;
    }

    /** Colores efectivos de todos los canales (0xRRGGBB; los canales que el vehículo no tiene valen -1). */
    public static int[] colors(PaintableVehicle vehicle) {
        Kind kind = vehicle.paintKind();
        int[] preview = vehicle.getPaintPreview();
        int[] out = new int[CHANNELS];
        for (int ch = 0; ch < CHANNELS; ch++) {
            if (!kind.has(ch)) {
                out[ch] = ORIGINAL;
                continue;
            }
            int raw = preview != null && ch < preview.length ? preview[ch] : vehicle.getPaintRaw(ch);
            out[ch] = raw < 0 ? kind.reference(ch) : raw & 0xFFFFFF;
        }
        return out;
    }

    /** true si algún canal tiene un color distinto del original. */
    public static boolean isCustom(PaintableVehicle vehicle) {
        for (int ch = 0; ch < CHANNELS; ch++) {
            if (vehicle.getPaintRaw(ch) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Al recoger un vehículo repintado, el ítem se lleva sus colores (así vuelve a salir igual al ponerlo). */
    public static ItemStack carry(PaintableVehicle vehicle, ItemStack stack) {
        if (isCustom(vehicle)) {
            int[] raw = new int[CHANNELS];
            for (int ch = 0; ch < CHANNELS; ch++) {
                raw[ch] = vehicle.getPaintRaw(ch);
            }
            stack.getOrCreateTag().putIntArray(TAG, raw);
        }
        return stack;
    }

    /** Lo contrario de {@link #carry}: al poner el ítem, el vehículo nuevo estrena los colores del ítem. */
    public static void restore(PaintableVehicle vehicle, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            read(vehicle, tag);
        }
    }

    public static void write(PaintableVehicle vehicle, CompoundTag tag) {
        int[] raw = new int[CHANNELS];
        for (int ch = 0; ch < CHANNELS; ch++) {
            raw[ch] = vehicle.getPaintRaw(ch);
        }
        tag.putIntArray(TAG, raw);
    }

    public static void read(PaintableVehicle vehicle, CompoundTag tag) {
        if (!tag.contains(TAG)) {
            return;
        }
        int[] raw = tag.getIntArray(TAG);
        Kind kind = vehicle.paintKind();
        for (int ch = 0; ch < CHANNELS; ch++) {
            vehicle.setPaintRaw(ch, ch < raw.length ? clean(kind, ch, raw[ch]) : ORIGINAL);
        }
    }
}
