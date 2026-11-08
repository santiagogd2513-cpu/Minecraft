import com.ventoy.nooficial.*;
import java.io.*;
public class FileDev implements BlockDevice {
    final RandomAccessFile f; final long n;
    public FileDev(String p, long sectors) throws IOException { f = new RandomAccessFile(p, "rw"); n = sectors; if (f.length() < n*512) f.setLength(n*512); }
    public long sectors() { return n; }
    public void read(long lba, byte[] b, int off, int c) throws IOException { f.seek(lba*512); f.readFully(b, off, c*512); }
    public void write(long lba, byte[] b, int off, int c) throws IOException { f.seek(lba*512); f.write(b, off, c*512); }
    public void flush() throws IOException { f.getFD().sync(); }
}
