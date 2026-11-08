import com.forja.formateador.*;
import java.io.*;
public class FileDev implements BlockDevice {
    final RandomAccessFile f; final long n; final int ss;
    public FileDev(String p, long bytes, int ss) throws IOException { f = new RandomAccessFile(p, "rw"); this.ss = ss; n = bytes / ss; if (f.length() < bytes) f.setLength(bytes); }
    public int sectorSize() { return ss; }
    public long sectors() { return n; }
    public void read(long lba, byte[] b, int off, int c) throws IOException { f.seek(lba*ss); f.readFully(b, off, c*ss); }
    public void write(long lba, byte[] b, int off, int c) throws IOException { f.seek(lba*ss); f.write(b, off, c*ss); }
    public void flush() throws IOException { }
}
