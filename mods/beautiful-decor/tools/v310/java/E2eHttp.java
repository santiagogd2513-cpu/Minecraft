package com.example.beautifuldecor.client;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** Baja un video de un servidor que corta las descargas y comprueba si se completa (o falla de frente con un aviso claro). */
public class E2eHttp {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        boolean expectOk = args[1].equals("ok");
        boolean expectPartial = args[1].equals("partial");
        HsVideoPlayer v = HsVideoPlayer.create();
        int before = DynamicTexture.uploads;
        v.play(url, null, "t", null, null);
        long t0 = System.nanoTime();
        boolean sawPlaying = false;
        while ((System.nanoTime() - t0) / 1_000_000L < 60_000) {
            v.update();
            if (v.state() == HsVideoPlayer.PLAYING) sawPlaying = true;
            if (v.state() == HsVideoPlayer.ENDED || v.state() == HsVideoPlayer.ERROR) break;
            Thread.sleep(16);
        }
        int ups = DynamicTexture.uploads - before;
        System.out.println("RESULT url=" + url + " state=" + v.state() + " uploads=" + ups + " sawPlaying=" + sawPlaying + " notice=[" + v.notice() + "]");
        boolean ok;
        if (expectPartial) {
            ok = v.state() == HsVideoPlayer.ENDED && ups > 20 && ups < 120 && v.notice().startsWith("gui.beautifuldecor.browser.video.cut");
        } else if (expectOk) {
            ok = v.state() == HsVideoPlayer.ENDED && ups > 50 && v.notice().isEmpty();
        } else {
            ok = v.state() == HsVideoPlayer.ERROR && !sawPlaying;
        }
        v.stop();
        v.update();
        Thread.sleep(200);
        System.out.println(ok ? "HTTP OK" : "HTTP FALLO");
        System.exit(ok ? 0 : 1);
    }
}
