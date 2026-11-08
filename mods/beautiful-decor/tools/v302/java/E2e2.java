package com.example.beautifuldecor.client;

import java.nio.file.Path;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Reinicio a mitad, archivo que no es video, y parar con el video cargando. */
public class E2e2 {
    static void pump(HsVideoPlayer v, long ms) throws Exception {
        long t0 = System.nanoTime();
        while ((System.nanoTime() - t0) / 1_000_000L < ms) {
            v.update();
            if (v.state() == HsVideoPlayer.ENDED || v.state() == HsVideoPlayer.ERROR) return;
            Thread.sleep(16);
        }
    }

    public static void main(String[] args) throws Exception {
        HsVideoPlayer v = HsVideoPlayer.create();
        // 1) reiniciar a mitad
        v.play(null, Path.of("hd.mp4"), "hd", null, null);
        pump(v, 2500);
        int u1 = DynamicTexture.uploads;
        v.play(null, Path.of("sd.mp4"), "sd", null, null);
        pump(v, 20000);
        boolean ok = v.state() == HsVideoPlayer.ENDED && DynamicTexture.lastW == 640 && DynamicTexture.uploads > u1 + 100;
        System.out.println("REINICIO state=" + v.state() + " uploads=" + (DynamicTexture.uploads - u1) + " tex=" + DynamicTexture.lastW + "x" + DynamicTexture.lastH);
        // 2) un archivo que no es video
        Path junk = Path.of("junk.mp4");
        java.nio.file.Files.write(junk, new byte[4000]);
        v.play(null, junk, "junk", null, null);
        pump(v, 5000);
        System.out.println("BASURA state=" + v.state() + " msg='" + v.message() + "'");
        ok &= v.state() == HsVideoPlayer.ERROR;
        // 3) parar enseguida, muchas veces
        for (int i = 0; i < 20; i++) {
            v.play(null, Path.of("hd.mp4"), "hd", null, null);
            pump(v, 60);
            v.stop();
            v.update();
        }
        v.play(null, Path.of("silent60.mp4"), "s60", null, null);
        int u2 = DynamicTexture.uploads;
        long t0 = System.nanoTime();
        pump(v, 20000);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        int up = DynamicTexture.uploads - u2;
        System.out.println("SIN SONIDO 60fps state=" + v.state() + " uploads=" + up + " en " + ms + " ms (" + (up * 1000L / Math.max(1, ms)) + " cuadros/s subidos)");
        ok &= v.state() == HsVideoPlayer.ENDED && ms < 6500 && up > 40;
        v.stop();
        v.update();
        Thread.sleep(300);
        System.out.println(ok ? "E2E2 OK" : "E2E2 FALLO");
        System.exit(ok ? 0 : 1);
    }
}
