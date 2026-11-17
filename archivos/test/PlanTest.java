package com.boveda.archivos;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class PlanTest {
    static int fails;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLA: " + m); } else System.out.println("ok: " + m); }
    /** Escribe una región de Litematica con los bloques empaquetados en bits, como lo hace el mod. */
    static void writeRegion(DataOutputStream o, String name, int px, int py, int pz, int sx, int sy, int sz, String[] pal, int[] vals) throws IOException {
        o.writeByte(10); o.writeUTF(name);
        o.writeByte(10); o.writeUTF("Position"); o.writeByte(3); o.writeUTF("x"); o.writeInt(px); o.writeByte(3); o.writeUTF("y"); o.writeInt(py); o.writeByte(3); o.writeUTF("z"); o.writeInt(pz); o.writeByte(0);
        o.writeByte(10); o.writeUTF("Size"); o.writeByte(3); o.writeUTF("x"); o.writeInt(sx); o.writeByte(3); o.writeUTF("y"); o.writeInt(sy); o.writeByte(3); o.writeUTF("z"); o.writeInt(sz); o.writeByte(0);
        o.writeByte(9); o.writeUTF("BlockStatePalette"); o.writeByte(10); o.writeInt(pal.length);
        for (String n : pal) { o.writeByte(8); o.writeUTF("Name"); o.writeUTF(n); o.writeByte(0); }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, pal.length - 1)));
        long[] arr = new long[(int) (((long) vals.length * bits + 63) / 64)];
        for (int i = 0; i < vals.length; i++) {
            long bit = (long) i * bits; int li = (int) (bit >>> 6), off = (int) (bit & 63);
            arr[li] |= ((long) vals[i]) << off;
            if (off + bits > 64) arr[li + 1] |= ((long) vals[i]) >>> (64 - off);
        }
        o.writeByte(12); o.writeUTF("BlockStates"); o.writeInt(arr.length); for (long v : arr) o.writeLong(v);
        o.writeByte(0);
    }

    static float[] extent(BdPlan.Ent e) {
        float[] mn = {1e9f, 1e9f, 1e9f}, mx = {-1e9f, -1e9f, -1e9f}, c = new float[24];
        for (EntityModels.Box b : EntityModels.of(e.id).boxes) {
            EntityModels.corners(e, b, c);
            for (int i = 0; i < 8; i++) for (int k = 0; k < 3; k++) { mn[k] = Math.min(mn[k], c[i * 3 + k]); mx[k] = Math.max(mx[k], c[i * 3 + k]); }
        }
        return new float[]{mn[0], mn[1], mn[2], mx[0], mx[1], mx[2]};
    }

    public static void main(String[] a) throws Exception {
        // 1) plano sintético: ida y vuelta del formato
        BdPlan p = new BdPlan();
        p.name = "prueba"; p.author = "Yo"; p.sx = 5; p.sy = 4; p.sz = 6; p.palette = new String[]{"minecraft:air", "minecraft:stone", "minecraft:red_concrete", "beautifuldecor:farola"};
        p.blocks = new short[5 * 4 * 6];
        for (int z = 0; z < 6; z++) for (int x = 0; x < 5; x++) p.blocks[p.index(x, 0, z)] = 1;
        p.blocks[p.index(2, 1, 3)] = 2; p.blocks[p.index(2, 2, 3)] = 3;
        p.entities.put("beautifuldecor:motorcycle", 2); p.blockEntities = 3; p.created = 12345L;
        BdPlan.Ent e1 = new BdPlan.Ent(); e1.id = "minecraft:wolf"; e1.name = "Rex"; e1.x = 1.5; e1.y = 1; e1.z = 2.5; e1.yaw = 90;
        BdPlan.Ent e2 = new BdPlan.Ent(); e2.id = "beautifuldecor:train_car"; e2.x = 2.5; e2.y = 1; e2.z = 3; e2.yaw = 0;
        p.entityList.add(e1); p.entityList.add(e2);
        byte[] bytes = BdPlan.write(p);
        BdPlan q = BdPlan.read(new ByteArrayInputStream(bytes), null);
        check(q.sx == 5 && q.sy == 4 && q.sz == 6 && q.name.equals("prueba") && q.author.equals("Yo"), "ida y vuelta: nombre, autor y tamaño");
        check(java.util.Arrays.equals(p.blocks, q.blocks) && q.counts[1] == 30 && q.counts[2] == 1, "ida y vuelta: los bloques son los mismos");
        check(q.entities.get("minecraft:wolf") == 1 && q.entities.get("beautifuldecor:train_car") == 1 && q.blockEntities == 3 && q.created == 12345L, "entidades, bloques con datos y fecha");
        check(q.entityList.size() == 2 && q.entityList.get(0).id.equals("minecraft:wolf") && q.entityList.get(0).name.equals("Rex") && Math.abs(q.entityList.get(0).x - 1.5) < 1e-9 && q.entityList.get(0).yaw == 90f, "las entidades conservan id, nombre, posición y giro");
        check(EntityModels.label(q.entityList.get(0)).equals("Lobo «Rex»") && EntityModels.label(q.entityList.get(1)).equals("Vagón de tren"), "nombres para mostrar: " + EntityModels.label(q.entityList.get(0)));
        // un vagón de tren (largo en Z) mide casi 20 bloques; girado 90° mide lo mismo pero a lo largo de X
        float[] ext = extent(q.entityList.get(1)); check(Math.abs((ext[5] - ext[2]) - 19.7f) < 0.3f && (ext[3] - ext[0]) < 3.5f, "el vagón sin girar es largo en Z: " + (ext[5] - ext[2]));
        q.entityList.get(1).yaw = 90; ext = extent(q.entityList.get(1)); check(Math.abs((ext[3] - ext[0]) - 19.7f) < 0.3f && (ext[5] - ext[2]) < 3.5f, "girado 90° es largo en X: " + (ext[3] - ext[0]));
        q.entityList.get(1).yaw = 0;
        VoxelMesh m = VoxelMesh.build(q, null);
        // piso 5x6 (arriba+abajo+4 lados) + columna de 2 bloques: caras grandes juntas
        check(m.quads > 0 && m.quads < 120, "malla chica (con entidades): " + m.quads + " caras");
        // cortado / dañado: errores claros, no cierres
        try { BdPlan.read(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length / 2)), null); check(false, "plano cortado no detectado"); } catch (IOException e) { check(true, "un plano cortado da un error claro: " + e.getMessage()); }
        try { BdPlan.read(new ByteArrayInputStream("hola, esto no es un plano".getBytes()), null); check(false, "archivo ajeno"); } catch (IOException e) { check(true, "un archivo que no es plano da un error claro"); }

        // 1b) Litematica: se arma uno con dos regiones (una con tamaño negativo) y se lee
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(new java.util.zip.GZIPOutputStream(bo));
            o.writeByte(10); o.writeShort(0);
            o.writeByte(3); o.writeUTF("Version"); o.writeInt(6);
            o.writeByte(10); o.writeUTF("Metadata"); o.writeByte(8); o.writeUTF("Name"); o.writeUTF("casita"); o.writeByte(8); o.writeUTF("Author"); o.writeUTF("Santi"); o.writeByte(0);
            o.writeByte(10); o.writeUTF("Regions");
            // región A: posición (0,0,0) tamaño 3x2x2, paleta [air, stone, oak_planks, glass, lava] -> 3 bits
            int[] vals = new int[12]; for (int i = 0; i < 12; i++) vals[i] = i % 5;
            writeRegion(o, "A", 0, 0, 0, 3, 2, 2, new String[]{"minecraft:air", "minecraft:stone", "minecraft:oak_planks", "minecraft:glass", "minecraft:lava"}, vals);
            // región B: posición (5,1,1) tamaño NEGATIVO (-2,1,1): ocupa x 4..5, y 1, z 1
            writeRegion(o, "B", 5, 1, 1, -2, 1, 1, new String[]{"minecraft:air", "minecraft:gold_block"}, new int[]{1, 1});
            o.writeByte(0);
            o.writeByte(0); o.close();
            BdPlan l = BdPlan.readAny("casita.litematic", new ByteArrayInputStream(bo.toByteArray()), null);
            check(l.name.equals("casita") && l.author.equals("Santi"), "litematic: nombre y autor");
            check(l.sx == 6 && l.sy == 2 && l.sz == 2, "litematic: la caja junta las dos regiones: " + l.sx + "x" + l.sy + "x" + l.sz);
            boolean ok = true;
            for (int i = 0; i < 12; i++) {
                int x = i % 3, z = (i / 3) % 2, y = i / 6;
                String nm = l.palette[l.blocks[l.index(x, y, z)]];
                String esp = new String[]{"minecraft:air", "minecraft:stone", "minecraft:oak_planks", "minecraft:glass", "minecraft:lava"}[i % 5];
                if (!nm.equals(esp)) { ok = false; System.out.println("  distinto en " + x + "," + y + "," + z + ": " + nm + " (esperaba " + esp + ")"); }
            }
            check(ok, "litematic: los 12 bloques de la región A, empaquetados en 3 bits, salen en su sitio");
            check(l.palette[l.blocks[l.index(4, 1, 1)]].equals("minecraft:gold_block") && l.palette[l.blocks[l.index(5, 1, 1)]].equals("minecraft:gold_block") && l.palette[l.blocks[l.index(3, 1, 1)]].equals("minecraft:air"), "litematic: la región de tamaño negativo queda en su lugar");
        }
        // 1c) Litematica con 20 tipos (5 bits por bloque): los valores cruzan de un long al siguiente
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(new java.util.zip.GZIPOutputStream(bo));
            o.writeByte(10); o.writeShort(0);
            o.writeByte(10); o.writeUTF("Regions");
            String[] pal = new String[20]; pal[0] = "minecraft:air"; for (int i = 1; i < 20; i++) pal[i] = "minecraft:tipo_" + i;
            int[] vals = new int[100]; for (int i = 0; i < 100; i++) vals[i] = (i * 7 + 3) % 20;
            writeRegion(o, "grande", 0, 0, 0, 5, 4, 5, pal, vals);
            o.writeByte(0); o.writeByte(0); o.close();
            BdPlan l = BdPlan.readAny("x.litematic", new ByteArrayInputStream(bo.toByteArray()), null);
            boolean ok = true;
            for (int i = 0; i < 100; i++) { int x = i % 5, z = (i / 5) % 4, y = i / 20; }
            // índice = y*az*ax + z*ax + x con ax=5, az=5, ay=4
            for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
                int i = y * 25 + z * 5 + x;
                if (!l.palette[l.blocks[l.index(x, y, z)]].equals(pal[vals[i]])) ok = false;
            }
            check(ok, "litematic: 100 bloques de 5 bits, cruzando entre longs, salen todos bien");
        }
        // 2) el plano real, si lo dan
        if (a.length > 0) {
            long t0 = System.nanoTime();
            BdPlan r;
            try (InputStream in = new FileInputStream(a[0])) { r = BdPlan.read(in, null); }
            long t1 = System.nanoTime();
            check(r.sx == 331 && r.sy == 145 && r.sz == 311 && r.palette.length == 596, "plano real: " + r.name + " de " + r.author + " (" + r.sx + "x" + r.sy + "x" + r.sz + ", " + r.palette.length + " tipos) leído en " + (t1 - t0) / 1_000_000 + " ms");
            long air = 0; for (int i = 0; i < r.palette.length; i++) if (BlockColors.kind(r.palette[i]) == BlockColors.AIR) air += r.counts[i];
            for (BdPlan.Ent en : r.entityList) System.out.println("  entidad: " + EntityModels.label(en) + " en " + String.format("%.1f, %.1f, %.1f", en.x, en.y, en.z));
            System.out.println("aire: " + air + " de " + r.volume() + "; entidades " + r.entities + "; con datos " + r.blockEntities);
            VoxelMesh mm = VoxelMesh.build(r, null);
            long t2 = System.nanoTime();
            long verts = (long) mm.quads * 4;
            check(mm.quads > 1000, "malla: " + mm.quads + " caras (" + mm.chunks.size() + " trozos, " + verts + " vértices) en " + (t2 - t1) / 1_000_000 + " ms");
            // dibujar las caras de arriba (+Y) en un mapa para comparar con la vista de arriba
            int W = r.sx, H = r.sz;
            int[] hgt = new int[W * H]; java.util.Arrays.fill(hgt, -1); int[] col = new int[W * H];
            for (VoxelMesh.Chunk c : mm.chunks) for (int k = 0; k < c.quads; k++) {
                if ((c.col[k * 4] >>> 24) == 255) continue;     // las entidades no
                int y0 = Math.round(c.pos[k * 12 + 1]), y1 = Math.round(c.pos[k * 12 + 4]), y2 = Math.round(c.pos[k * 12 + 7]);
                if (!(y0 == y1 && y1 == y2)) continue;                      // solo caras horizontales
                int cc = c.col[k * 4] | 0xFF000000;
                // arriba: el brillo es 1.0 (las de abajo son 0.5): se distingue porque su normal apunta a +y; aquí se usa el orden de los vértices
                int xa = Math.round(Math.min(Math.min(c.pos[k * 12], c.pos[k * 12 + 3]), Math.min(c.pos[k * 12 + 6], c.pos[k * 12 + 9]))), xb = Math.round(Math.max(Math.max(c.pos[k * 12], c.pos[k * 12 + 3]), Math.max(c.pos[k * 12 + 6], c.pos[k * 12 + 9])));
                int za = Math.round(Math.min(Math.min(c.pos[k * 12 + 2], c.pos[k * 12 + 5]), Math.min(c.pos[k * 12 + 8], c.pos[k * 12 + 11]))), zb = Math.round(Math.max(Math.max(c.pos[k * 12 + 2], c.pos[k * 12 + 5]), Math.max(c.pos[k * 12 + 8], c.pos[k * 12 + 11])));
                for (int z = za; z < zb; z++) for (int x = xa; x < xb; x++) if (y0 >= hgt[z * W + x]) { hgt[z * W + x] = y0; col[z * W + x] = cc; }
            }
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < W * H; i++) img.setRGB(i % W, i / W, hgt[i] < 0 ? 0x03050D : col[i]);
            ImageIO.write(img, "png", new File(""+System.getProperty("java.io.tmpdir")+"/malla_arriba.png"));
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
