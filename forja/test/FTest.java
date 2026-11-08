// Prueba en la PC: formatea un archivo que hace de disco (lo usa la compilación de GitHub).
import com.forja.formateador.*;
public class FTest {
    // FTest img bytes sectorSize FS TABLE cluster label
    public static void main(String[] a) throws Exception {
        FileDev d = new FileDev(a[0], Long.parseLong(a[1]), Integer.parseInt(a[2]));
        Forja.Options o = new Forja.Options();
        o.fs = FsType.valueOf(a[3]); o.table = Forja.Table.valueOf(a[4]); o.clusterBytes = Integer.parseInt(a[5]); o.label = a.length > 6 ? a[6] : "";
        long t = System.currentTimeMillis();
        Forja.format(d, o, Progress.NONE);
        long pb = Forja.partitionBytes(d.sectors() * d.sectorSize(), d.sectorSize(), o.table);
        System.out.println("ok " + (System.currentTimeMillis() - t) + " ms, cluster por defecto " + o.fs.defaultCluster(pb, d.sectorSize()) + ", opciones " + o.fs.clusterChoices(pb, d.sectorSize()));
    }
}
