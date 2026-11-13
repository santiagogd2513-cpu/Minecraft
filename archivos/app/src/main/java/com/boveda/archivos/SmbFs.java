package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Una carpeta compartida de red SMB (Windows, Samba, un NAS) como una carpeta más de Bóveda: /smb/<id>/…
 * Usa SMB2 y SMB3, que son los que hoy dejan activos Windows y los NAS.
 */
final class SmbFs implements Fs {
    private final NetServers.Server server;
    private final String root;
    private final File cacheDir;
    private SMBClient client;
    private Connection conn;
    private Session session;
    private DiskShare share;
    private final Map<String, Object[]> cache = new HashMap<>();

    SmbFs(NetServers.Server s, File cacheDir) {
        server = s;
        root = s.root();
        this.cacheDir = cacheDir;
    }

    // ------------------------------------------------------------------ conexión

    private synchronized DiskShare share() throws IOException {
        if (share != null && share.isConnected()) return share;
        closeQuiet();
        try {
            SmbConfig cfg = SmbConfig.builder().withTimeout(25, TimeUnit.SECONDS).withSoTimeout(60, TimeUnit.SECONDS)
                    .withMultiProtocolNegotiate(true).build();
            client = new SMBClient(cfg);
            conn = client.connect(server.address.trim(), server.port <= 0 ? 445 : server.port);
            AuthenticationContext ac = server.user.isEmpty() ? AuthenticationContext.guest()
                    : new AuthenticationContext(server.user, server.pass.toCharArray(), server.domain.isEmpty() ? null : server.domain);
            session = conn.authenticate(ac);
            com.hierynomus.smbj.share.Share sh = session.connectShare(server.share.trim());
            if (!(sh instanceof DiskShare)) {
                closeQuiet();
                throw new IOException(L.t("«") + server.share + L.t("» no es una carpeta compartida (es otro tipo de recurso)"));
            }
            share = (DiskShare) sh;
            return share;
        } catch (SMBApiException e) {
            closeQuiet();
            throw new IOException(friendly(e), e);
        } catch (IOException e) {
            closeQuiet();
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("Connect") || m.contains("connect") || m.contains("timed out") || m.contains("route")) {
                throw new IOException(L.t("No se pudo conectar a ") + server.address + L.t(" (¿están en la misma red y el equipo tiene compartida la carpeta?)"), e);
            }
            throw e;
        } catch (RuntimeException | LinkageError e) {
            closeQuiet();
            throw new IOException(L.t("No pude abrir la carpeta de red: ") + e, e);
        }
    }

    private static String friendly(SMBApiException e) {
        String n = String.valueOf(e.getStatus());
        if (n.contains("LOGON_FAILURE") || n.contains("ACCOUNT") || n.contains("WRONG_PASSWORD")) return L.t("Usuario o contraseña incorrectos");
        if (n.contains("BAD_NETWORK_NAME")) return L.t("Esa carpeta compartida no existe en el equipo (revisa el nombre)");
        if (n.contains("ACCESS_DENIED")) return L.t("No tienes permiso para esto");
        if (n.contains("OBJECT_NAME_NOT_FOUND") || n.contains("PATH_NOT_FOUND")) return L.t("No existe en el equipo");
        if (n.contains("SHARING_VIOLATION")) return L.t("Otro programa lo está usando");
        if (n.contains("DISK_FULL")) return L.t("No hay espacio en el equipo");
        if (n.contains("NAME_COLLISION") || n.contains("OBJECT_NAME_COLLISION")) return L.t("Ya existe algo con ese nombre");
        return L.t("Error de red: ") + n;
    }

    private synchronized void closeQuiet() {
        try {
            if (share != null) share.close();
        } catch (Exception ignored) {
        }
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {
        }
        try {
            if (conn != null) conn.close();
        } catch (Exception ignored) {
        }
        try {
            if (client != null) client.close();
        } catch (Exception ignored) {
        }
        share = null;
        session = null;
        conn = null;
        client = null;
    }

    synchronized void close() {
        closeQuiet();
    }

    /** "/smb/12/a/b" → "a\b" (la ruta dentro de la carpeta compartida). */
    private String remote(String path) {
        String r = path.length() <= root.length() ? "" : path.substring(root.length());
        while (r.startsWith("/")) r = r.substring(1);
        return r.replace('/', '\\');
    }

    private static boolean isNotFound(SMBApiException e) {
        String n = String.valueOf(e.getStatus());
        return n.contains("OBJECT_NAME_NOT_FOUND") || n.contains("OBJECT_PATH_NOT_FOUND") || n.contains("NO_SUCH_FILE");
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public List<Entry> list(String dir) throws IOException {
        try {
            List<FileIdBothDirectoryInformation> raw = share().list(remote(dir));
            List<Entry> out = new ArrayList<>();
            for (FileIdBothDirectoryInformation f : raw) {
                String name = f.getFileName();
                if (name.equals(".") || name.equals("..")) continue;
                boolean isDir = EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
                long mod = f.getLastWriteTime() == null ? 0 : f.getLastWriteTime().toEpochMillis();
                out.add(new Entry(Paths.join(dir, name), name, isDir, isDir ? 0 : f.getEndOfFile(), mod));
            }
            synchronized (cache) {
                cache.put(dir, new Object[]{System.currentTimeMillis(), out});
            }
            return out;
        } catch (SMBApiException e) {
            throw new IOException(friendly(e), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Entry> cachedList(String dir) throws IOException {
        synchronized (cache) {
            Object[] c = cache.get(dir);
            if (c != null && System.currentTimeMillis() - (Long) c[0] < 3000) return (List<Entry>) c[1];
        }
        return list(dir);
    }

    @Override
    public Entry stat(String path) throws IOException {
        if (path.equals(root) || remote(path).isEmpty()) return new Entry(path, server.name, true, 0, 0);
        try {
            FileAllInformation i = share().getFileInformation(remote(path));
            boolean dir = i.getStandardInformation().isDirectory();
            long mod = i.getBasicInformation().getLastWriteTime() == null ? 0 : i.getBasicInformation().getLastWriteTime().toEpochMillis();
            return new Entry(path, Paths.name(path), dir, dir ? 0 : i.getStandardInformation().getEndOfFile(), mod);
        } catch (SMBApiException e) {
            if (isNotFound(e)) return null;
            throw new IOException(friendly(e), e);
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        try {
            com.hierynomus.smbj.share.File f = share().openFile(remote(path), EnumSet.of(AccessMask.GENERIC_READ), null,
                    SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
            return new FilterInputStream(f.getInputStream()) {
                boolean done;

                @Override
                public void close() throws IOException {
                    if (done) return;
                    done = true;
                    try {
                        super.close();
                    } finally {
                        f.close();
                    }
                }
            };
        } catch (SMBApiException e) {
            throw new IOException(L.t("No pude abrir ") + Paths.name(path) + ": " + friendly(e), e);
        }
    }

    @Override
    public OutputStream out(String path) throws IOException {
        forget(path);
        try {
            com.hierynomus.smbj.share.File f = share().openFile(remote(path), EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
            OutputStream raw = f.getOutputStream();
            return new FilterOutputStream(raw) {
                boolean done;

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    if (done) return;
                    done = true;
                    try {
                        super.close();
                    } finally {
                        f.close();
                        forget(path);
                    }
                }
            };
        } catch (SMBApiException e) {
            throw new IOException(L.t("No pude guardar ") + Paths.name(path) + ": " + friendly(e), e);
        }
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException(L.t("En carpetas de red solo se puede abrir para leer"));
        File dir = new File(cacheDir, "smb");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, server.id + "_" + Integer.toHexString(path.hashCode()) + "_" + Paths.name(path));
        Entry e = stat(path);
        if (!(f.exists() && e != null && f.length() == e.size)) {
            try (InputStream in = in(path); FileOutputStream o = new FileOutputStream(f)) {
                byte[] buf = new byte[1 << 20];
                int r;
                while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public void mkdir(String path) throws IOException {
        forget(path);
        try {
            share().mkdir(remote(path));
        } catch (SMBApiException e) {
            throw new IOException(L.t("No pude crear la carpeta: ") + friendly(e), e);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Entry e = stat(path);
        if (e == null) return;
        try {
            if (e.dir) share().rmdir(remote(path), true);
            else share().rm(remote(path));
        } catch (SMBApiException ex) {
            if (!isNotFound(ex)) throw new IOException(L.t("No pude borrar ") + Paths.name(path) + ": " + friendly(ex), ex);
        }
        forget(path);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        forget(from);
        forget(to);
        try (DiskEntry e = share().open(remote(from), EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {
            e.rename(remote(to), false);
        } catch (SMBApiException ex) {
            throw new IOException(L.t("No pude mover ") + Paths.name(from) + ": " + friendly(ex), ex);
        } finally {
            forget(from);
            forget(to);
        }
    }

    @Override
    public void setModified(String path, long time) {
        // se queda la fecha que pone el equipo al guardar
    }

    @Override
    public int maxParallel() {
        return 6;
    }

    @Override
    public String key(String path) {
        return "smb:" + server.id;
    }

    private void forget(String path) {
        synchronized (cache) {
            cache.remove(Paths.parent(path));
            cache.remove(path);
        }
    }

    /** Para probar la conexión al guardar el servidor. */
    void test() throws IOException {
        list(root);
    }
}
