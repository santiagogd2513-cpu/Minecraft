import com.example.beautifuldecor.browser.video.H264Decoder;
import com.example.beautifuldecor.browser.video.H264Pic;
import com.example.beautifuldecor.browser.video.Mp4;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Decodifica el video H.264 de un MP4 y compara cuadro a cuadro con un yuv420p crudo hecho por ffmpeg. */
public class H264Test {
    static final class Frame {
        long pts;
        byte[] data;
    }

    public static void main(String[] a) throws Exception {
        Path mp4 = Path.of(a[0]);
        byte[] ref = Files.readAllBytes(Path.of(a[1]));
        int maxFrames = a.length > 2 ? Integer.parseInt(a[2]) : Integer.MAX_VALUE;
        List<Frame> frames = new ArrayList<>();
        int w = 0;
        int h = 0;
        long t0 = System.nanoTime();
        try (Mp4 m = Mp4.open(mp4)) {
            Mp4.Track v = m.video;
            if (v == null) {
                System.out.println("SIN VIDEO: " + m.unsupportedVideo);
                System.exit(2);
            }
            H264Decoder dec = new H264Decoder();
            try {
                dec.setParameterSets(v.sps, v.pps, v.nalLengthSize);
                for (int i = 0; i < v.sampleCount() && frames.size() < maxFrames; i++) {
                    H264Pic p = dec.decode(m.readSample(v, i));
                    if (p != null) {
                        Frame f = new Frame();
                        f.pts = v.ptsUs(i);
                        w = p.visibleWidth();
                        h = p.visibleHeight();
                        f.data = copy(p, w, h);
                        frames.add(f);
                        dec.release(p);
                    }
                }
            } catch (H264Decoder.Unsupported u) {
                System.out.println("UNSUPPORTED: " + u.getMessage());
                System.exit(2);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        frames.sort((x, y) -> Long.compare(x.pts, y.pts));
        int fs = w * h * 3 / 2;
        int refFrames = ref.length / fs;
        int n = Math.min(frames.size(), Math.min(refFrames, maxFrames));
        int bad = 0;
        int firstBad = -1;
        for (int k = 0; k < n; k++) {
            byte[] mine = frames.get(k).data;
            int off = k * fs;
            int diff = -1;
            for (int i = 0; i < fs; i++) {
                if (mine[i] != ref[off + i]) {
                    diff = i;
                    break;
                }
            }
            if (diff >= 0) {
                bad++;
                if (firstBad < 0) {
                    firstBad = k;
                    String plane;
                    int x;
                    int y;
                    if (diff < w * h) {
                        plane = "Y";
                        x = diff % w;
                        y = diff / w;
                    } else if (diff < w * h * 5 / 4) {
                        plane = "U";
                        x = (diff - w * h) % (w / 2) * 2;
                        y = (diff - w * h) / (w / 2) * 2;
                    } else {
                        plane = "V";
                        x = (diff - w * h * 5 / 4) % (w / 2) * 2;
                        y = (diff - w * h * 5 / 4) / (w / 2) * 2;
                    }
                    int firstMb = Integer.MAX_VALUE;
                    int mbw = (w + 15) / 16;
                    String where = "";
                    for (int i = 0; i < w * h; i++) {
                        if (mine[i] != ref[off + i]) {
                            int mbi = (i / w / 16) * mbw + (i % w) / 16;
                            if (mbi < firstMb) {
                                firstMb = mbi;
                                where = "Y";
                            }
                        }
                    }
                    for (int i = w * h; i < fs; i++) {
                        if (mine[i] != ref[off + i]) {
                            int q = (i - w * h) % (w * h / 4);
                            int mbi = (q / (w / 2) / 8) * mbw + (q % (w / 2)) / 8;
                            if (mbi < firstMb) {
                                firstMb = mbi;
                                where = i - w * h < w * h / 4 ? "U" : "V";
                            }
                        }
                    }
                    if (firstMb != Integer.MAX_VALUE) {
                        System.out.println("  primer MB (luma) con diferencia: " + firstMb + " (" + (firstMb % mbw) + "," + (firstMb / mbw) + ") plano " + where);
                        if (System.getenv("MBMAP") != null) {
                            for (int j = 0; j < 16; j++) {
                                StringBuilder sb = new StringBuilder("   ");
                                for (int i = 0; i < 16; i++) {
                                    int px = (firstMb % mbw) * 16 + i;
                                    int py = (firstMb / mbw) * 16 + j;
                                    if (px < w && py < h) {
                                        int d = (mine[py * w + px] & 0xff) - (ref[off + py * w + px] & 0xff);
                                        sb.append(String.format("%4d", d));
                                    }
                                }
                                System.out.println(sb);
                            }
                        }
                    }
                    int count = 0;
                    int max = 0;
                    for (int i = 0; i < fs; i++) {
                        int d = Math.abs((mine[i] & 0xff) - (ref[off + i] & 0xff));
                        if (d != 0) {
                            count++;
                            max = Math.max(max, d);
                        }
                    }
                    System.out.println("PRIMER ERROR: cuadro " + k + " plano " + plane + " x=" + x + " y=" + y + " (MB " + (x / 16) + "," + (y / 16)
                            + ") muestras distintas=" + count + " dif max=" + max
                            + " mio=" + (mine[diff] & 0xff) + " ffmpeg=" + (ref[off + diff] & 0xff));
                }
            }
        }
        String status = bad == 0 && frames.size() == refFrames ? "IGUAL" : "DIFERENTE";
        System.out.println(status + " cuadros=" + frames.size() + " ffmpeg=" + refFrames + " malos=" + bad + " " + w + "x" + h + " " + ms + "ms");
        System.exit(bad == 0 && frames.size() == refFrames ? 0 : 1);
    }

    static byte[] copy(H264Pic p, int w, int h) {
        byte[] out = new byte[w * h * 3 / 2];
        int o = 0;
        for (int j = 0; j < h; j++) {
            System.arraycopy(p.y, (j + p.cropTop) * p.width + p.cropLeft, out, o, w);
            o += w;
        }
        int cw = p.width / 2;
        for (int j = 0; j < h / 2; j++) {
            System.arraycopy(p.u, (j + p.cropTop / 2) * cw + p.cropLeft / 2, out, o, w / 2);
            o += w / 2;
        }
        for (int j = 0; j < h / 2; j++) {
            System.arraycopy(p.v, (j + p.cropTop / 2) * cw + p.cropLeft / 2, out, o, w / 2);
            o += w / 2;
        }
        return out;
    }
}
