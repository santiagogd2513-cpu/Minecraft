package com.example.beautifuldecor.client;

import java.nio.file.Path;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class E2e {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        int expectW = Integer.parseInt(args[1]);
        int expectH = Integer.parseInt(args[2]);
        HsVideoPlayer v = HsVideoPlayer.create();
        int before = DynamicTexture.uploads;
        v.play(null, file, file.getFileName().toString(), null, null);
        long t0 = System.nanoTime();
        long lastUploadNs = 0, maxGapMs = 0;
        int lastCount = before;
        int firstFrameMs = -1;
        int minGapMs = 1000;
        while ((System.nanoTime() - t0) / 1_000_000L < 25_000) {
            v.update();
            int c = DynamicTexture.uploads;
            long now = System.nanoTime();
            if (c != lastCount) {
                if (firstFrameMs < 0) firstFrameMs = (int) ((now - t0) / 1_000_000L);
                if (lastUploadNs != 0) {
                    long gap = (now - lastUploadNs) / 1_000_000L;
                    maxGapMs = Math.max(maxGapMs, gap);
                    minGapMs = (int) Math.min(minGapMs, gap);
                }
                lastUploadNs = now;
                lastCount = c;
            }
            int st = v.state();
            if (st == HsVideoPlayer.ENDED || st == HsVideoPlayer.ERROR) break;
            Thread.sleep(16);
        }
        long total = (System.nanoTime() - t0) / 1_000_000L;
        int[] last = DynamicTexture.last;
        int nonBlack = 0, opaque = 0;
        if (last != null) for (int p : last) { if ((p & 0xFFFFFF) != 0) nonBlack++; if ((p >>> 24) == 0xFF) opaque++; }
        System.out.println("RESULT state=" + v.state() + " msg='" + v.message() + "' uploads=" + (DynamicTexture.uploads - before)
            + " tex=" + DynamicTexture.lastW + "x" + DynamicTexture.lastH + " videoSize=" + v.videoWidth() + "x" + v.videoHeight()
            + " totalMs=" + total + " firstFrameMs=" + firstFrameMs + " minGapMs=" + minGapMs + " maxGapMs=" + maxGapMs
            + " nonBlack=" + nonBlack + " opaque=" + opaque + "/" + (last == null ? 0 : last.length));
        boolean ok = v.state() == HsVideoPlayer.ENDED && DynamicTexture.lastW == expectW && DynamicTexture.lastH == expectH
            && last != null && opaque == last.length && nonBlack > last.length / 4;
        v.stop();
        v.update();
        Thread.sleep(300);
        System.out.println(ok ? "E2E OK" : "E2E FALLO");
        System.exit(ok ? 0 : 1);
    }
}
