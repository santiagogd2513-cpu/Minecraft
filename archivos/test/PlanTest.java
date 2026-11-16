package com.boveda.archivos;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class PlanTest {
    static int fails;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLA: " + m); } else System.out.println("ok: " + m); }
    public static void main(String[] a) throws Exception {
        // 1) plano sintético: ida y vuelta del formato
        BdPlan p = new BdPlan();
        p.name = "prueba"; p.author = "Yo"; p.sx = 5; p.sy = 4; p.sz = 6; p.palette = new String[]{"minecraft:air", "minecraft:stone", "minecraft:red_concrete", "beautifuldecor:farola"};
        p.blocks = new short[5 * 4 * 6];
        for (int z = 0; z < 6; z++) for (int x = 0; x < 5; x++) p.blocks[p.index(x, 0, z)] = 1;
        p.blocks[p.index(2, 1, 3)] = 2; p.blocks[p.index(2, 2, 3)] = 3;
        p.entities.put("beautifuldecor:motorcycle", 2); p.blockEntities = 3; p.created = 12345L;
        byte[] bytes = BdPlan.write(p);
        BdPlan q = BdPlan.read(new ByteArrayInputStream(bytes), null);
        check(q.sx == 5 && q.sy == 4 && q.sz == 6 && q.name.equals("prueba") && q.author.equals("Yo"), "ida y vuelta: nombre, autor y tamaño");
        check(java.util.Arrays.equals(p.blocks, q.blocks) && q.counts[1] == 30 && q.counts[2] == 1, "ida y vuelta: los bloques son los mismos");
        check(q.entities.get("beautifuldecor:motorcycle") == 2 && q.blockEntities == 3 && q.created == 12345L, "entidades, bloques con datos y fecha");
        VoxelMesh m = VoxelMesh.build(q, null);
        // piso 5x6 (arriba+abajo+4 lados) + columna de 2 bloques: caras grandes juntas
        check(m.quads > 0 && m.quads < 40, "malla chica: " + m.quads + " caras");
        // cortado / dañado: errores claros, no cierres
        try { BdPlan.read(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length / 2)), null); check(false, "plano cortado no detectado"); } catch (IOException e) { check(true, "un plano cortado da un error claro: " + e.getMessage()); }
        try { BdPlan.read(new ByteArrayInputStream("hola, esto no es un plano".getBytes()), null); check(false, "archivo ajeno"); } catch (IOException e) { check(true, "un archivo que no es plano da un error claro"); }

        // 2) el plano real, si lo dan
        if (a.length > 0) {
            long t0 = System.nanoTime();
            BdPlan r;
            try (InputStream in = new FileInputStream(a[0])) { r = BdPlan.read(in, null); }
            long t1 = System.nanoTime();
            check(r.sx == 331 && r.sy == 145 && r.sz == 311 && r.palette.length == 596, "plano real: " + r.name + " de " + r.author + " (" + r.sx + "x" + r.sy + "x" + r.sz + ", " + r.palette.length + " tipos) leído en " + (t1 - t0) / 1_000_000 + " ms");
            long air = 0; for (int i = 0; i < r.palette.length; i++) if (BlockColors.kind(r.palette[i]) == BlockColors.AIR) air += r.counts[i];
            System.out.println("aire: " + air + " de " + r.volume() + "; entidades " + r.entities + "; con datos " + r.blockEntities);
            VoxelMesh mm = VoxelMesh.build(r, null);
            long t2 = System.nanoTime();
            long verts = (long) mm.quads * 4;
            check(mm.quads > 1000, "malla: " + mm.quads + " caras (" + mm.chunks.size() + " trozos, " + verts + " vértices) en " + (t2 - t1) / 1_000_000 + " ms");
            // dibujar las caras de arriba (+Y) en un mapa para comparar con la vista de arriba
            int W = r.sx, H = r.sz;
            int[] hgt = new int[W * H]; java.util.Arrays.fill(hgt, -1); int[] col = new int[W * H];
            for (VoxelMesh.Chunk c : mm.chunks) for (int k = 0; k < c.quads; k++) {
                int y0 = c.pos[k * 12 + 1], y1 = c.pos[k * 12 + 4], y2 = c.pos[k * 12 + 7];
                if (!(y0 == y1 && y1 == y2)) continue;                      // solo caras horizontales
                int cc = c.col[k * 4];
                // arriba: el brillo es 1.0 (las de abajo son 0.5): se distingue porque su normal apunta a +y; aquí se usa el orden de los vértices
                int xa = Math.min(Math.min(c.pos[k * 12], c.pos[k * 12 + 3]), Math.min(c.pos[k * 12 + 6], c.pos[k * 12 + 9])), xb = Math.max(Math.max(c.pos[k * 12], c.pos[k * 12 + 3]), Math.max(c.pos[k * 12 + 6], c.pos[k * 12 + 9]));
                int za = Math.min(Math.min(c.pos[k * 12 + 2], c.pos[k * 12 + 5]), Math.min(c.pos[k * 12 + 8], c.pos[k * 12 + 11])), zb = Math.max(Math.max(c.pos[k * 12 + 2], c.pos[k * 12 + 5]), Math.max(c.pos[k * 12 + 8], c.pos[k * 12 + 11]));
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
