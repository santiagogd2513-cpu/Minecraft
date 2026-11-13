package com.boveda.archivos;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Qué archivos abre Bóveda por su cuenta (reproductor, visor de imágenes, editor y visor de PDF) y cómo. */
final class Viewers {
    private Viewers() {}

    static final Set<String> AUDIO = set("mp3", "wav", "ogg", "oga", "opus", "flac", "m4a", "aac", "amr", "awb",
            "mid", "midi", "xmf", "mxmf", "imy", "ota", "rtttl", "rtx", "3ga", "mka", "weba");
    static final Set<String> VIDEO = set("mp4", "m4v", "mkv", "webm", "3gp", "3g2", "mov", "ts", "m2ts", "mts",
            "avi", "flv", "wmv", "mpg", "mpeg", "ogv");
    static final Set<String> TEXT = set("txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "jsonc", "json5",
            "xml", "html", "htm", "xhtml", "css", "scss", "less", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "java", "kt", "kts", "gradle", "groovy", "scala", "py", "pyw", "c", "h", "cpp", "cc", "cxx", "hpp", "hh",
            "cs", "go", "rs", "swift", "dart", "php", "rb", "lua", "pl", "r", "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "yml", "yaml", "toml", "ini", "cfg", "conf", "config", "properties", "prop", "env", "sql", "svg",
            "mcmeta", "lang", "mcfunction", "gitignore", "gitattributes", "editorconfig", "srt", "vtt", "ass", "sub", "nfo",
            "tex", "rtf", "vbs", "asm", "s", "makefile", "cmake", "dockerfile", "m3u", "m3u8", "pls", "cue");

    static final Set<String> IMAGE = set("jpg", "jpeg", "jpe", "jfif", "pjpeg", "pjp", "png", "apng", "gif", "webp", "bmp", "dib",
            "wbmp", "ico", "cur", "heic", "heif", "hif", "avif", "dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf",
            "rw2", "raf", "pef", "srw", "x3f", "jxl", "svg", "svgz",
            "tga", "tpic", "icb", "vda", "vst", "tif", "tiff", "psd", "psb", "qoi", "ppm", "pgm", "pbm", "pnm", "pam");

    /** Conjunto que tolera repetidos (Set.of se cae si hay uno, y eso tumbaba la app). */
    private static Set<String> set(String... items) {
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(items)));
    }

    /** Verdadero mientras está el modo prestado: los visores no dejan compartir ni mandar a otras apps. */
    static volatile boolean loan;

    static String ext(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return n.equals("makefile") || n.equals("dockerfile") ? n : "";
        return n.substring(dot + 1);
    }

    static boolean isAudio(String name) {
        return AUDIO.contains(ext(name));
    }

    static boolean isVideo(String name) {
        return VIDEO.contains(ext(name));
    }

    static boolean isMedia(String name) {
        return isAudio(name) || isVideo(name);
    }

    static boolean isText(String name) {
        return TEXT.contains(ext(name));
    }

    static boolean isImage(String name) {
        return IMAGE.contains(ext(name));
    }

    static boolean isPdf(String name) {
        return ext(name).equals("pdf");
    }

    /** Abre el archivo con un visor propio si lo hay; false si hay que mandarlo a otra app. */
    static boolean open(Activity a, Entry e, List<Entry> siblings) {
        if (e.dir) return false;
        if (isMedia(e.name)) {
            // lista de reproducción: lo del mismo tipo en la misma carpeta
            boolean audio = isAudio(e.name);
            ArrayList<String> list = new ArrayList<>();
            int index = 0;
            if (siblings != null) {
                for (Entry s : siblings) {
                    if (s.dir || (audio ? !isAudio(s.name) : !isVideo(s.name))) continue;
                    if (s.path.equals(e.path)) index = list.size();
                    list.add(s.path);
                }
            }
            if (list.isEmpty()) list.add(e.path);
            a.startActivity(new Intent(a, PlayerActivity.class)
                    .putExtra("paths", list.toArray(new String[0]))
                    .putExtra("index", index));
            return true;
        }
        if (isImage(e.name)) {
            // galería: las imágenes de la misma carpeta, para pasar con el dedo
            ArrayList<String> list = new ArrayList<>();
            int index = 0;
            if (siblings != null) {
                for (Entry s : siblings) {
                    if (s.dir || !isImage(s.name)) continue;
                    if (s.path.equals(e.path)) index = list.size();
                    list.add(s.path);
                }
            }
            if (list.isEmpty()) list.add(e.path);
            a.startActivity(new Intent(a, ImageActivity.class)
                    .putExtra("paths", list.toArray(new String[0]))
                    .putExtra("index", index));
            return true;
        }
        if (isText(e.name)) {
            a.startActivity(new Intent(a, EditorActivity.class).putExtra("path", e.path));
            return true;
        }
        if (isPdf(e.name)) {
            a.startActivity(new Intent(a, PdfActivity.class).putExtra("path", e.path));
            return true;
        }
        return false;
    }

    /** Abre con un visor propio un archivo suelto (por ejemplo, el que se sacó de un comprimido). */
    static boolean openPath(Activity a, String path) {
        java.io.File f = new java.io.File(path);
        return open(a, new Entry(path, Paths.name(path), false, f.length(), f.lastModified()), null);
    }

    /** «Abrir con otra app» desde los visores. */
    static void openWithOther(Activity a, String path, String mime) {
        if (loan) {
            android.widget.Toast.makeText(a, L.t("No disponible en modo prestado"), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(StorageProvider.documentUri(a, path), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, L.t("Abrir con")));
        } catch (Exception ex) {
            android.widget.Toast.makeText(a, L.t("No hay otra app para abrir este archivo"), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
