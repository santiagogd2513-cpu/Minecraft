// Prueba en la PC: instala Ventoy en un archivo que hace de USB (lo usa la compilación de GitHub).
import com.ventoy.nooficial.*;
import java.io.*;
import java.nio.file.*;
public class Test {
    public static void main(String[] a) throws Exception {
        String dir = a[0], img = a[1]; long mb = Long.parseLong(a[2]); String mode = a[3];
        FileDev d = new FileDev(img, mb * 2048);
        VentoyInstaller.Files f = new VentoyInstaller.Files() {
            public byte[] bootImg() throws IOException { return Files.readAllBytes(Paths.get(dir, "boot.img")); }
            public InputStream coreImg() throws IOException { return new FileInputStream(dir + "/core.img"); }
            public InputStream diskImg() throws IOException { return new FileInputStream(dir + "/ventoy.disk.img"); }
            public String version() { return "1.1.10"; }
        };
        Progress p = new Progress() { String last=""; public void update(float fr, String s) { if (!s.equals(last)) { last=s; if (!s.contains("%")) System.out.println(s); } } public boolean cancelled() { return false; } };
        if (mode.equals("install")) VentoyInstaller.install(d, f, a.length > 4 ? com.forja.formateador.FsType.valueOf(a[4]) : com.forja.formateador.FsType.EXFAT, p);
        if (mode.equals("update")) VentoyInstaller.update(d, f, p);
        VentoyInstaller.Info i = VentoyInstaller.inspect(d);
        System.out.println("hasVentoy=" + i.hasVentoy + " version=" + i.version + " gpt=" + i.gpt + " p1=" + i.part1Start + "+" + i.part1Sectors + " p2=" + i.part2Start + "+" + i.part2Sectors + " fs=" + i.part1Fs);
    }
}
