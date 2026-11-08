package com.example.beautifuldecor.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Descargas del navegador: se guardan en la carpeta "HS Downloads" de la raíz del juego. */
public final class Downloads {
    public static final String FOLDER = "HS Downloads";
    public static final long MAX_BYTES = 1L << 30;

    public static final int RUNNING = 0;
    public static final int DONE = 1;
    public static final int FAILED = 2;
    public static final int CANCELLED = 3;

    public static final class Job {
        public final int id;
        public final String url;
        public volatile String name;
        public volatile long done;
        public volatile long total = -1;
        public volatile int state = RUNNING;
        public volatile String error = "";
        private volatile boolean cancel;

        Job(int id, String url, String name) {
            this.id = id;
            this.url = url;
            this.name = name;
        }

        public void cancel() {
            this.cancel = true;
        }
    }

    public static final class FileInfo {
        public final String name;
        public final long size;
        public final long modified;

        FileInfo(String name, long size, long modified) {
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
    }

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final List<Job> JOBS = new CopyOnWriteArrayList<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "HS Downloads");
        t.setDaemon(true);
        return t;
    });
    private static volatile Path dir = Paths.get(FOLDER);

    private Downloads() {
    }

    /** Indica la carpeta raíz del juego; dentro se crea "HS Downloads". */
    public static void init(Path gameDir) {
        dir = gameDir.resolve(FOLDER);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // se vuelve a intentar al guardar el primer archivo
        }
    }

    public static Path folder() {
        return dir;
    }

    public static List<Job> jobs() {
        return JOBS;
    }

    public static boolean anyRunning() {
        for (Job j : JOBS) {
            if (j.state == RUNNING) {
                return true;
            }
        }
        return false;
    }

    /** Baja una dirección en segundo plano. */
    public static Job start(String url, String referer) {
        Job job = newJob(url, Url.fileName(url, null, null));
        POOL.execute(() -> {
            try (Http.Response r = Http.get(url, referer)) {
                if (r.status >= 400) {
                    throw new IOException("Error " + r.status);
                }
                job.name = Url.fileName(r.url, r.disposition, r.mime);
                copy(job, r);
            } catch (Throwable e) {
                fail(job, e);
            }
        });
        return job;
    }

    /** Sigue una descarga cuya respuesta ya se abrió (la cierra al terminar). */
    public static Job adopt(Http.Response response) {
        Job job = newJob(response.url, Url.fileName(response.url, response.disposition, response.mime));
        POOL.execute(() -> {
            try (Http.Response r = response) {
                copy(job, r);
            } catch (Throwable e) {
                fail(job, e);
            }
        });
        return job;
    }

    private static Job newJob(String url, String name) {
        Job job = new Job(IDS.incrementAndGet(), url, name);
        JOBS.add(job);
        while (JOBS.size() > 40) {
            Job old = JOBS.get(0);
            if (old.state == RUNNING) {
                break;
            }
            JOBS.remove(old);
        }
        return job;
    }

    private static void fail(Job job, Throwable e) {
        if (job.state != RUNNING) {
            return;
        }
        if (job.cancel) {
            job.state = CANCELLED;
        } else {
            String m = e.getMessage();
            job.error = m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
            job.state = FAILED;
        }
    }

    private static void copy(Job job, Http.Response r) throws IOException {
        job.total = r.length;
        if (r.length > MAX_BYTES) {
            throw new IOException("Archivo demasiado grande");
        }
        Files.createDirectories(dir);
        if (r.length > 0 && Files.getFileStore(dir).getUsableSpace() < r.length + (64L << 20)) {
            throw new IOException("No hay espacio suficiente");
        }
        Path part = dir.resolve(Url.sanitizeFileName(job.name) + "." + job.id + ".part");
        boolean ok = false;
        try (InputStream in = r.body; OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (job.cancel) {
                    throw new IOException("Cancelada");
                }
                out.write(buf, 0, n);
                job.done += n;
                if (job.done > MAX_BYTES) {
                    throw new IOException("Archivo demasiado grande");
                }
            }
            ok = true;
        } finally {
            if (!ok) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                    // queda el .part, no pasa nada
                }
            }
        }
        Path target;
        synchronized (Downloads.class) {
            target = uniquePath(job.name);
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(part, target);
            }
        }
        job.name = target.getFileName().toString();
        job.state = DONE;
    }

    /** Ruta libre dentro de la carpeta: "nombre.ext", "nombre (1).ext", "nombre (2).ext"... */
    static Path uniquePath(String wanted) {
        String name = Url.sanitizeFileName(wanted);
        String ext = Url.extension(name);
        String stem = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
        Path p = dir.resolve(name);
        int i = 1;
        while (Files.exists(p) && i < 10000) {
            p = dir.resolve(stem + " (" + i + ")" + (ext.isEmpty() ? "" : "." + ext));
            i++;
        }
        return p;
    }

    /** Archivos ya descargados, los más nuevos primero. */
    public static List<FileInfo> files() {
        List<FileInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).limit(500).forEach(p -> {
                String n = p.getFileName().toString();
                if (n.toLowerCase(Locale.ROOT).endsWith(".part")) {
                    return;
                }
                try {
                    out.add(new FileInfo(n, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
                } catch (IOException ignored) {
                    // se salta
                }
            });
        } catch (IOException ignored) {
            // carpeta ilegible: lista vacía
        }
        out.sort(Comparator.comparingLong((FileInfo f) -> f.modified).reversed());
        return out;
    }

    /** Archivo de la carpeta con ese nombre, o null (nunca sale de la carpeta). */
    public static Path resolveFile(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.equals("..") || name.equals(".")) {
            return null;
        }
        Path p = dir.resolve(name).normalize();
        if (!p.startsWith(dir.normalize()) || !Files.isRegularFile(p)) {
            return null;
        }
        return p;
    }

    public static Job find(int id) {
        for (Job j : JOBS) {
            if (j.id == id) {
                return j;
            }
        }
        return null;
    }
}
