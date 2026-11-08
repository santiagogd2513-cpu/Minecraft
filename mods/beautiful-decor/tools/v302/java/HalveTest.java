package com.example.beautifuldecor.client;

import java.util.Random;

public class HalveTest {
    public static void main(String[] a) {
        Random rnd = new Random(7);
        int[][] sizes = {{1280, 720}, {7, 5}, {2, 2}, {1001, 601}, {640, 481}};
        for (int[] sz : sizes) {
            int sw = sz[0], sh = sz[1];
            int[] src = new int[sw * sh];
            for (int i = 0; i < src.length; i++) {
                src[i] = 0xFF000000 | rnd.nextInt(1 << 24);
            }
            // casos extremos
            if (src.length > 8) { src[0] = src[1] = src[sw] = src[sw + 1] = 0xFFFFFFFF; src[2] = src[3] = src[sw + 2] = src[sw + 3] = 0xFF000000; }
            int dw = sw / 2, dh = sh / 2;
            int[] dst = new int[dw * dh];
            HsVideoPlayer.halve(src, sw, dst, dw, dh);
            for (int y = 0; y < dh; y++) {
                for (int x = 0; x < dw; x++) {
                    int p0 = src[2 * y * sw + 2 * x], p1 = src[2 * y * sw + 2 * x + 1];
                    int p2 = src[(2 * y + 1) * sw + 2 * x], p3 = src[(2 * y + 1) * sw + 2 * x + 1];
                    int want = 0xFF000000;
                    for (int sh2 = 0; sh2 <= 16; sh2 += 8) {
                        int v = ((p0 >> sh2 & 255) + (p1 >> sh2 & 255) + (p2 >> sh2 & 255) + (p3 >> sh2 & 255) + 2) >> 2;
                        want |= v << sh2;
                    }
                    if (dst[y * dw + x] != want) {
                        throw new AssertionError("difiere en " + sw + "x" + sh + " (" + x + "," + y + "): " + Integer.toHexString(dst[y * dw + x]) + " vs " + Integer.toHexString(want));
                    }
                }
            }
            System.out.println("halve OK " + sw + "x" + sh + " -> " + dw + "x" + dh);
        }
    }
}
