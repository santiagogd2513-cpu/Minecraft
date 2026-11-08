package com.example.beautifuldecor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Prueba de VehiclePaint (V3.2.0): limpieza de colores, colores efectivos con vista previa, y ida y vuelta por NBT e ítem. */
public final class PaintLogicTest {
    static int fails;

    static void check(boolean cond, String msg) {
        if (!cond) {
            fails++;
            System.out.println("FALLA: " + msg);
        }
    }

    static int[] newRaw() {
        int[] r = new int[VehiclePaint.CHANNELS];
        java.util.Arrays.fill(r, -1);
        return r;
    }

    static final class Fake implements PaintableVehicle {
        final VehiclePaint.Kind kind;
        final int[] raw = newRaw();
        int[] preview;

        Fake(VehiclePaint.Kind kind) {
            this.kind = kind;
        }

        public VehiclePaint.Kind paintKind() {
            return this.kind;
        }

        public int getPaintRaw(int channel) {
            return channel >= 0 && channel < VehiclePaint.CHANNELS ? this.raw[channel] : -1;
        }

        public void setPaintRaw(int channel, int v) {
            if (channel >= 0 && channel < VehiclePaint.CHANNELS) {
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

    public static void main(String[] args) {
        // Qué canales tiene cada vehículo (el carro RC y el dron no son tipos pintables).
        check(VehiclePaint.Kind.values().length == 5, "debe haber 5 tipos pintables");
        for (VehiclePaint.Kind k : VehiclePaint.Kind.values()) {
            check(k.has(VehiclePaint.BODY), k + " debe tener pintura");
            check(!k.has(VehiclePaint.CHANNELS) && !k.has(-1), k + ": canales fuera de rango no existen");
        }
        check(!VehiclePaint.Kind.MOTORCYCLE.has(VehiclePaint.HANDLE) && VehiclePaint.Kind.MOTORCYCLE.has(VehiclePaint.RIM), "moto: sin manijas, con rines");
        check(!VehiclePaint.Kind.BICYCLE.has(VehiclePaint.HANDLE) && VehiclePaint.Kind.BICYCLE.has(VehiclePaint.RIM), "bici: sin manijas, con rines");
        check(VehiclePaint.Kind.PICKUP.has(VehiclePaint.HANDLE) && VehiclePaint.Kind.PICKUP.has(VehiclePaint.RIM), "camioneta: manijas y rines");
        check(VehiclePaint.Kind.DELIVERY_TRUCK.has(VehiclePaint.HANDLE) && VehiclePaint.Kind.DELIVERY_TRUCK.has(VehiclePaint.RIM), "camión: manijas y rines");
        check(VehiclePaint.Kind.HELICOPTER.has(VehiclePaint.HANDLE) && !VehiclePaint.Kind.HELICOPTER.has(VehiclePaint.RIM), "helicóptero: manijas, sin rines");

        // clean(): original, canal que no existe, basura en el canal alfa
        VehiclePaint.Kind pickup = VehiclePaint.Kind.PICKUP;
        check(VehiclePaint.clean(pickup, VehiclePaint.BODY, 0x122664) == -1, "el color original se guarda como -1");
        check(VehiclePaint.clean(pickup, VehiclePaint.BODY, 0xFF122664) == -1, "el canal alfa no cuenta");
        check(VehiclePaint.clean(pickup, VehiclePaint.BODY, 0xFFCE1C24) == 0xCE1C24, "un color nuevo se guarda sin alfa");
        check(VehiclePaint.clean(pickup, VehiclePaint.BODY, -1) == -1, "-1 = original");
        check(VehiclePaint.clean(pickup, VehiclePaint.BODY, 0x1000000 | 0xAB0000) == 0xAB0000, "los bits de más se descartan");
        check(VehiclePaint.clean(VehiclePaint.Kind.MOTORCYCLE, VehiclePaint.HANDLE, 0x123456) == -1, "la moto no tiene manijas: se ignora");
        check(VehiclePaint.clean(pickup, VehiclePaint.CHANNELS, 0x123456) == -1, "canal inexistente: se ignora");

        // colors(): original, repintado, vista previa
        Fake f = new Fake(pickup);
        int[] c = VehiclePaint.colors(f);
        check(c[0] == 0x122664 && c[1] == 0xB0B6C2 && c[2] == 0xCED2DA, "sin repintar, los colores son los de referencia");
        check(!VehiclePaint.isCustom(f), "sin repintar no es custom");
        f.setPaintRaw(VehiclePaint.BODY, 0x00FF00);
        c = VehiclePaint.colors(f);
        check(c[0] == 0x00FF00 && c[1] == 0xB0B6C2, "pintura nueva, manijas originales");
        check(VehiclePaint.isCustom(f), "repintado es custom");
        f.setPaintPreview(new int[] {0x111111, 0x222222, 0x333333});
        c = VehiclePaint.colors(f);
        check(c[0] == 0x111111 && c[1] == 0x222222 && c[2] == 0x333333, "la vista previa manda sobre lo guardado");
        f.setPaintPreview(null);
        check(VehiclePaint.colors(f)[0] == 0x00FF00, "al quitar la vista previa vuelve lo guardado");
        Fake bike = new Fake(VehiclePaint.Kind.BICYCLE);
        bike.setPaintPreview(new int[] {0x010203, 0x040506, 0x070809});
        c = VehiclePaint.colors(bike);
        check(c[1] == -1 && c[0] == 0x010203 && c[2] == 0x070809, "la bici no tiene manijas: ese canal vale -1 aunque la vista previa traiga algo");

        // ítem: se lleva los colores y los devuelve
        Fake a = new Fake(pickup);
        ItemStack plain = VehiclePaint.carry(a, new ItemStack());
        check(plain.getTag() == null, "un vehículo sin repintar no le agrega NBT al ítem (sigue apilándose)");
        a.setPaintRaw(VehiclePaint.BODY, 0xAA0000);
        a.setPaintRaw(VehiclePaint.RIM, 0x00AA00);
        ItemStack stack = VehiclePaint.carry(a, new ItemStack());
        check(stack.getTag() != null, "un vehículo repintado deja sus colores en el ítem");
        Fake b = new Fake(pickup);
        VehiclePaint.restore(b, stack);
        check(b.raw[0] == 0xAA0000 && b.raw[1] == -1 && b.raw[2] == 0x00AA00, "el vehículo nuevo estrena los colores del ítem");
        VehiclePaint.restore(new Fake(pickup), new ItemStack());      // sin NBT: no pasa nada

        // NBT del mundo: ida y vuelta, y datos viejos (sin la clave) o raros
        CompoundTag tag = new CompoundTag();
        VehiclePaint.write(a, tag);
        Fake d = new Fake(pickup);
        VehiclePaint.read(d, tag);
        check(d.raw[0] == 0xAA0000 && d.raw[1] == -1 && d.raw[2] == 0x00AA00, "NBT: ida y vuelta");
        Fake e = new Fake(pickup);
        e.setPaintRaw(VehiclePaint.BODY, 0x123456);
        VehiclePaint.read(e, new CompoundTag());
        check(e.raw[0] == 0x123456, "un mundo de antes de la V3.2.0 (sin la clave) no borra nada");
        CompoundTag odd = new CompoundTag();
        odd.putIntArray("VehiclePaint", new int[] {0x445566});
        Fake g = new Fake(VehiclePaint.Kind.MOTORCYCLE);
        VehiclePaint.read(g, odd);
        check(g.raw[0] == 0x445566 && g.raw[1] == -1 && g.raw[2] == -1, "NBT con menos de 3 valores se lee sin reventar");
        CompoundTag moto = new CompoundTag();
        moto.putIntArray("VehiclePaint", new int[] {0x010101, 0x020202, 0x030303});
        Fake h = new Fake(VehiclePaint.Kind.MOTORCYCLE);
        VehiclePaint.read(h, moto);
        check(h.raw[1] == -1 && h.raw[2] == 0x030303, "NBT: el canal que la moto no tiene se descarta");

        if (fails == 0) {
            System.out.println("PAINT LOGIC OK");
        } else {
            System.exit(1);
        }
    }
}
