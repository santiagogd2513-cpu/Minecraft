package com.boveda.archivos;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Un plano de Beautiful Decor (.bdplan): un NBT comprimido (gzip) con una paleta de bloques, un arreglo de bloques (números en
 * formato «varint», en el orden y, z, x) y las entidades. Aquí solo se LEE; no usa nada de Android, para probarlo en el computador.
 */
final class BdPlan {
    String name = "", author = "", description = "";
    long created;
    int format, sx, sy, sz, ox, oy, oz;
    String[] palette = new String[0];
    /** Índice de paleta de cada bloque, en el orden (y * sz + z) * sx + x. */
    short[] blocks = new short[0];
    long[] counts = new long[0];
    final Map<String, Integer> entities = new LinkedHashMap<>();

    /** Una entidad del plano (un lobo, un tren, una moto…): su posición está en bloques del plano. */
    static final class Ent {
        String id = "?", name = "";
        double x, y, z;
        float yaw;
    }

    final List<Ent> entityList = new ArrayList<>();
    int blockEntities;

    interface Progress {
        void on(String what);
    }

    static final long MAX_VOXELS = 150_000_000L;

    int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    long volume() {
        return (long) sx * sy * sz;
    }

    static BdPlan read(InputStream raw, Progress p) throws IOException {
        java.io.BufferedInputStream b = new java.io.BufferedInputStream(raw, 1 << 16);
        b.mark(4);
        int b0 = b.read(), b1 = b.read();
        b.reset();
        InputStream in = (b0 == 0x1f && b1 == 0x8b) ? new GZIPInputStream(b, 1 << 16) : b;
        if (p != null) p.on("Descomprimiendo…");
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 1 << 16));
        int t = d.readUnsignedByte();
        if (t != 10) throw new IOException("no parece un plano de Beautiful Decor");
        d.skipBytes(d.readUnsignedShort());     // nombre de la raíz
        BdPlan plan = new BdPlan();
        byte[] blocksRaw = null;
        while (true) {
            int tag = d.readUnsignedByte();
            if (tag == 0) break;
            String key = d.readUTF();
            switch (key) {
                case "Palette" -> {
                    if (tag != 9) throw new IOException("paleta dañada");
                    int et = d.readUnsignedByte(), n = d.readInt();
                    if (et != 10 || n < 0 || n > 100000) throw new IOException("paleta dañada");
                    plan.palette = new String[n];
                    for (int i = 0; i < n; i++) plan.palette[i] = readPaletteEntry(d);
                }
                case "Blocks" -> {
                    if (tag != 7) throw new IOException("bloques dañados");
                    int n = d.readInt();
                    if (n < 0 || n > 600_000_000) throw new IOException("el plano es demasiado grande");
                    if (p != null) p.on("Leyendo bloques…");
                    blocksRaw = new byte[n];
                    d.readFully(blocksRaw);
                }
                case "Name" -> plan.name = tag == 8 ? d.readUTF() : skip(d, tag);
                case "Author" -> plan.author = tag == 8 ? d.readUTF() : skip(d, tag);
                case "Description" -> plan.description = tag == 8 ? d.readUTF() : skip(d, tag);
                case "Created" -> plan.created = tag == 4 ? d.readLong() : Long.valueOf(skipNum(d, tag));
                case "Format" -> plan.format = tag == 3 ? d.readInt() : (int) skipNum(d, tag);
                case "SizeX" -> plan.sx = (int) intTag(d, tag);
                case "SizeY" -> plan.sy = (int) intTag(d, tag);
                case "SizeZ" -> plan.sz = (int) intTag(d, tag);
                case "OffsetX" -> plan.ox = (int) intTag(d, tag);
                case "OffsetY" -> plan.oy = (int) intTag(d, tag);
                case "OffsetZ" -> plan.oz = (int) intTag(d, tag);
                case "Entities" -> {
                    if (tag != 9) throw new IOException("entidades dañadas");
                    int et = d.readUnsignedByte(), n = d.readInt();
                    for (int i = 0; i < n; i++) {
                        if (et != 10) {
                            skipPayload(d, et);
                            continue;
                        }
                        Ent en = readEntity(d);
                        plan.entityList.add(en);
                        plan.entities.merge(en.id, 1, Integer::sum);
                    }
                }
                case "BlockEntities" -> {
                    if (tag != 9) throw new IOException("bloques con datos dañados");
                    int et = d.readUnsignedByte(), n = d.readInt();
                    plan.blockEntities = Math.max(0, n);
                    for (int i = 0; i < n; i++) skipPayload(d, et);
                }
                default -> skipPayload(d, tag);
            }
        }
        if (blocksRaw == null || plan.palette.length == 0) throw new IOException("al plano le faltan datos");
        if (plan.sx <= 0 || plan.sy <= 0 || plan.sz <= 0) throw new IOException("el plano no trae su tamaño");
        long vol = plan.volume();
        if (vol > MAX_VOXELS) throw new IOException("el plano es demasiado grande para este celular (" + vol / 1_000_000 + " millones de bloques)");
        if (p != null) p.on("Ordenando los bloques…");
        plan.blocks = new short[(int) vol];
        plan.counts = new long[plan.palette.length];
        int pos = 0, n = 0;
        for (int i = 0; i < blocksRaw.length; ) {
            int v = 0, shift = 0, bt;
            do {
                if (i >= blocksRaw.length || shift > 28) throw new IOException("bloques dañados");
                bt = blocksRaw[i++] & 0xFF;
                v |= (bt & 0x7F) << shift;
                shift += 7;
            } while ((bt & 0x80) != 0);
            if (v >= plan.palette.length) throw new IOException("un bloque apunta a un tipo que no existe");
            if (n >= vol) throw new IOException("el plano trae más bloques de los que dice");
            plan.blocks[n++] = (short) v;
            plan.counts[v]++;
        }
        if (n != vol) throw new IOException("al plano le faltan bloques (" + n + " de " + vol + ")");
        return plan;
    }

    private static long intTag(DataInputStream d, int tag) throws IOException {
        return switch (tag) {
            case 1 -> d.readByte();
            case 2 -> d.readShort();
            case 3 -> d.readInt();
            case 4 -> d.readLong();
            default -> skipNum(d, tag);
        };
    }

    private static long skipNum(DataInputStream d, int tag) throws IOException {
        skipPayload(d, tag);
        return 0;
    }

    private static String skip(DataInputStream d, int tag) throws IOException {
        skipPayload(d, tag);
        return "";
    }

    private static String readPaletteEntry(DataInputStream d) throws IOException {
        String name = "minecraft:air";
        while (true) {
            int tag = d.readUnsignedByte();
            if (tag == 0) break;
            String key = d.readUTF();
            if (key.equals("Name") && tag == 8) name = d.readUTF();
            else skipPayload(d, tag);
        }
        return name;
    }

    private static Ent readEntity(DataInputStream d) throws IOException {
        Ent e = new Ent();
        while (true) {
            int tag = d.readUnsignedByte();
            if (tag == 0) break;
            String key = d.readUTF();
            switch (key) {
                case "id" -> {
                    if (tag == 8) e.id = d.readUTF();
                    else skipPayload(d, tag);
                }
                case "CustomName" -> {
                    if (tag == 8) e.name = plainName(d.readUTF());
                    else skipPayload(d, tag);
                }
                case "Pos" -> {
                    if (tag == 9) {
                        int et = d.readUnsignedByte(), n = d.readInt();
                        double[] v = new double[Math.max(0, n)];
                        for (int i = 0; i < n; i++) {
                            if (et == 6) v[i] = d.readDouble();
                            else if (et == 5) v[i] = d.readFloat();
                            else skipPayload(d, et);
                        }
                        if (n >= 3 && (et == 6 || et == 5)) {
                            e.x = v[0];
                            e.y = v[1];
                            e.z = v[2];
                        }
                    } else skipPayload(d, tag);
                }
                case "Rotation" -> {
                    if (tag == 9) {
                        int et = d.readUnsignedByte(), n = d.readInt();
                        for (int i = 0; i < n; i++) {
                            if (et == 5) {
                                float f = d.readFloat();
                                if (i == 0) e.yaw = f;
                            } else skipPayload(d, et);
                        }
                    } else skipPayload(d, tag);
                }
                default -> skipPayload(d, tag);
            }
        }
        return e;
    }

    /** De un nombre en JSON de Minecraft ({"text":"papá"}) saca solo el texto. */
    private static String plainName(String s) {
        int i = s.indexOf("\"text\":\"");
        if (i < 0) return s.startsWith("{") ? "" : s;
        int j = s.indexOf('"', i + 8);
        return j < 0 ? "" : s.substring(i + 8, j);
    }

    private static void skipPayload(DataInputStream d, int tag) throws IOException {
        switch (tag) {
            case 1 -> d.skipBytes(1);
            case 2 -> d.skipBytes(2);
            case 3, 5 -> d.skipBytes(4);
            case 4, 6 -> d.skipBytes(8);
            case 7 -> skipFully(d, d.readInt());
            case 8 -> skipFully(d, d.readUnsignedShort());
            case 9 -> {
                int et = d.readUnsignedByte(), n = d.readInt();
                for (int i = 0; i < n; i++) skipPayload(d, et);
            }
            case 10 -> {
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) break;
                    skipFully(d, d.readUnsignedShort());
                    skipPayload(d, t);
                }
            }
            case 11 -> skipFully(d, 4L * d.readInt());
            case 12 -> skipFully(d, 8L * d.readInt());
            default -> throw new IOException("dato desconocido en el plano (" + tag + ")");
        }
    }

    private static void skipFully(DataInputStream d, long n) throws IOException {
        while (n > 0) {
            int k = d.skipBytes((int) Math.min(Integer.MAX_VALUE, n));
            if (k <= 0) {
                if (d.read() < 0) throw new IOException("el plano está cortado");
                k = 1;
            }
            n -= k;
        }
    }

    // ---------------------------------------------------------------- para las pruebas: escribir un plano

    /** Escribe un plano con el mismo formato (para las pruebas y para generar ejemplos). */
    static byte[] write(BdPlan p) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream o = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(bo));
        o.writeByte(10);
        o.writeShort(0);
        o.writeByte(9); o.writeUTF("Palette"); o.writeByte(10); o.writeInt(p.palette.length);
        for (String n : p.palette) { o.writeByte(8); o.writeUTF("Name"); o.writeUTF(n); o.writeByte(0); }
        o.writeByte(8); o.writeUTF("Description"); o.writeUTF(p.description);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (short s : p.blocks) {
            int v = s;
            while (v >= 0x80) { raw.write((v & 0x7F) | 0x80); v >>>= 7; }
            raw.write(v);
        }
        o.writeByte(7); o.writeUTF("Blocks"); o.writeInt(raw.size()); o.write(raw.toByteArray());
        o.writeByte(3); o.writeUTF("OffsetX"); o.writeInt(p.ox);
        o.writeByte(3); o.writeUTF("OffsetZ"); o.writeInt(p.oz);
        o.writeByte(3); o.writeUTF("OffsetY"); o.writeInt(p.oy);
        o.writeByte(8); o.writeUTF("Name"); o.writeUTF(p.name);
        o.writeByte(4); o.writeUTF("Created"); o.writeLong(p.created);
        o.writeByte(9); o.writeUTF("Entities"); o.writeByte(10); o.writeInt(p.entityList.size());
        for (Ent e : p.entityList) {
            o.writeByte(8); o.writeUTF("id"); o.writeUTF(e.id);
            if (!e.name.isEmpty()) { o.writeByte(8); o.writeUTF("CustomName"); o.writeUTF("{\"text\":\"" + e.name + "\"}"); }
            o.writeByte(9); o.writeUTF("Pos"); o.writeByte(6); o.writeInt(3); o.writeDouble(e.x); o.writeDouble(e.y); o.writeDouble(e.z);
            o.writeByte(9); o.writeUTF("Rotation"); o.writeByte(5); o.writeInt(2); o.writeFloat(e.yaw); o.writeFloat(0f);
            o.writeByte(0);
        }
        o.writeByte(3); o.writeUTF("Format"); o.writeInt(1);
        o.writeByte(3); o.writeUTF("SizeX"); o.writeInt(p.sx);
        o.writeByte(8); o.writeUTF("Author"); o.writeUTF(p.author);
        o.writeByte(3); o.writeUTF("SizeZ"); o.writeInt(p.sz);
        o.writeByte(3); o.writeUTF("SizeY"); o.writeInt(p.sy);
        o.writeByte(9); o.writeUTF("BlockEntities"); o.writeByte(10); o.writeInt(p.blockEntities);
        for (int i = 0; i < p.blockEntities; i++) { o.writeByte(3); o.writeUTF("I"); o.writeInt(i); o.writeByte(0); }
        o.writeByte(0);
        o.close();
        return bo.toByteArray();
    }
}
