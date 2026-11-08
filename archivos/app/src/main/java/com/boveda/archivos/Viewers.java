package com.boveda.archivos;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Qué archivos abre Bóveda por su cuenta (reproductor, editor y visor de PDF) y cómo. */
final class Viewers {
    private Viewers() {}

    static final Set<String> AUDIO = Set.of("mp3", "wav", "ogg", "oga", "opus", "flac", "m4a", "aac", "amr", "awb",
            "mid", "midi", "xmf", "mxmf", "imy", "ota", "rtttl", "rtx", "3ga", "mka", "weba");
    static final Set<String> VIDEO = Set.of("mp4", "m4v", "mkv", "webm", "3gp", "3g2", "mov", "ts", "m2ts", "mts",
            "avi", "flv", "wmv", "mpg", "mpeg", "ogv");
    static final Set<String> TEXT = Set.of("txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "jsonc", "json5",
            "xml", "html", "htm", "xhtml", "css", "scss", "less", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "java", "kt", "kts", "gradle", "groovy", "scala", "py", "pyw", "c", "h", "cpp", "cc", "cxx", "hpp", "hh",
            "cs", "go", "rs", "swift", "dart", "php", "rb", "lua", "pl", "r", "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "yml", "yaml", "toml", "ini", "cfg", "conf", "config", "properties", "prop", "env", "sql", "svg",
            "mcmeta", "lang", "mcfunction", "gitignore", "gitattributes", "editorconfig", "srt", "vtt", "ass", "sub", "nfo",
            "tex", "rtf", "vbs", "asm", "s", "makefile", "cmake", "dockerfile", "toml", "m3u", "m3u8", "pls", "cue");

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

    /** «Abrir con otra app» desde los visores. */
    static void openWithOther(Activity a, String path, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(StorageProvider.documentUri(a, path), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, "Abrir con"));
        } catch (Exception ex) {
            android.widget.Toast.makeText(a, "No hay otra app para abrir este archivo", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
