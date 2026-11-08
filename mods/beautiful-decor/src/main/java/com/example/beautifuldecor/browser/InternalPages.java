package com.example.beautifuldecor.browser;

import java.util.List;
import java.util.Locale;

/** Páginas que arma el propio navegador (inicio, descargas, errores...) como HTML, para pasarlas por el mismo motor. */
public final class InternalPages {
    /** Idioma de los textos del navegador: true = español, false = inglés. */
    public static volatile boolean spanish = true;

    private InternalPages() {
    }

    public static String t(String es, String en) {
        return spanish ? es : en;
    }

    static String esc(String s) {
        return Http.escapeHtml(s == null ? "" : s);
    }

    private static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + esc(title) + "</title></head><body>" + body + "</body></html>";
    }

    public static String size(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String home() {
        String body = "<h1>HS Browser</h1>"
                + "<p>" + t("Navegador sencillo del computador HS. Siempre en modo oscuro.",
                "Simple browser for the HS computer. Always in dark mode.") + "</p>"
                + "<p>" + t("Escribe una dirección o una búsqueda arriba y pulsa Enter.",
                "Type an address or a search above and press Enter.") + "</p>"
                + "<h3>" + t("Atajos", "Shortcuts") + "</h3><ul>"
                + "<li><a href=\"https://html.duckduckgo.com/html/\">" + t("Buscar con DuckDuckGo", "Search with DuckDuckGo") + "</a></li>"
                + "<li><a href=\"https://lite.duckduckgo.com/lite/\">DuckDuckGo Lite</a></li>"
                + "<li><a href=\"https://es.wikipedia.org/\">Wikipedia</a></li>"
                + "<li><a href=\"https://text.npr.org/\">" + t("Noticias en texto (NPR)", "Text news (NPR)") + "</a></li>"
                + "<li><a href=\"https://example.com/\">example.com</a></li></ul>"
                + "<p>" + t("Pulsa <b>H</b> para salir del navegador y volver al mundo.", "Press <b>H</b> to leave the browser and go back to the world.") + "</p>"
                + "<h3>" + t("Descargas", "Downloads") + "</h3>"
                + "<p><a href=\"hs:downloads\">" + t("Ver descargas", "View downloads") + "</a> — "
                + t("los archivos se guardan en la carpeta ", "files are saved in the folder ") + "<code>" + Downloads.FOLDER + "</code> "
                + t("de la raíz del juego.", "in the game root.") + "</p>"
                + "<h3>" + t("Audio", "Audio") + "</h3>"
                + "<p>" + t("Abre un enlace directo a un MP3, OGG o WAV y suena desde el computador. También sirven las radios en vivo "
                + "(enlaces de transmisión MP3, como los de Icecast o Shoutcast) y las listas .m3u / .pls.",
                "Open a direct link to an MP3, OGG or WAV and it plays from the computer. Live radio streams "
                + "(MP3 links such as Icecast or Shoutcast) and .m3u / .pls lists work too.") + "</p>"
                + "<h3>" + t("Qué no hace", "What it does not do") + "</h3><ul>"
                + "<li>" + t("No ejecuta JavaScript: las páginas que dependen de él salen incompletas.",
                "It does not run JavaScript: pages that depend on it look incomplete.") + "</li>"
                + "<li>" + t("No reproduce video (YouTube y similares no funcionan).",
                "It does not play video (YouTube and similar do not work).") + "</li>"
                + "<li>" + t("No carga imágenes WebP ni SVG.", "It does not load WebP or SVG images.") + "</li></ul>";
        return page("HS Browser", body);
    }

    public static String downloads() {
        StringBuilder b = new StringBuilder("<h1>" + t("Descargas", "Downloads") + "</h1>");
        b.append("<p>").append(t("Carpeta: ", "Folder: ")).append("<code>").append(Downloads.FOLDER).append("</code> ")
                .append(t("(en la raíz del juego)", "(in the game root)")).append("</p>");
        List<Downloads.Job> jobs = Downloads.jobs();
        boolean any = false;
        StringBuilder active = new StringBuilder();
        for (Downloads.Job j : jobs) {
            if (j.state == Downloads.RUNNING) {
                any = true;
                long total = j.total;
                String pct = total > 0 ? " — " + Math.min(100, j.done * 100 / total) + "% (" + size(j.done) + " / " + size(total) + ")"
                        : " — " + size(j.done);
                active.append("<li>").append(esc(j.name)).append(pct).append(" <a href=\"hs:cancel?id=").append(j.id).append("\">")
                        .append(t("Cancelar", "Cancel")).append("</a></li>");
            }
        }
        if (any) {
            b.append("<h3>").append(t("En curso", "In progress")).append("</h3><ul>").append(active).append("</ul>");
        }
        StringBuilder failed = new StringBuilder();
        for (Downloads.Job j : jobs) {
            if (j.state == Downloads.FAILED) {
                failed.append("<li>").append(esc(j.name)).append(" — ").append(esc(j.error)).append("</li>");
            } else if (j.state == Downloads.CANCELLED) {
                failed.append("<li>").append(esc(j.name)).append(" — ").append(t("cancelada", "cancelled")).append("</li>");
            }
        }
        if (failed.length() > 0) {
            b.append("<h3>").append(t("Con problemas", "With problems")).append("</h3><ul>").append(failed).append("</ul>");
        }
        List<Downloads.FileInfo> files = Downloads.files();
        b.append("<h3>").append(t("Archivos", "Files")).append("</h3>");
        if (files.isEmpty()) {
            b.append("<p>").append(t("Todavía no hay archivos descargados.", "No downloaded files yet.")).append("</p>");
        } else {
            b.append("<ul>");
            for (Downloads.FileInfo f : files) {
                b.append("<li>").append(esc(f.name)).append(" (").append(size(f.size)).append(")");
                if (Url.isAudioName(f.name) || Url.isVideoName(f.name)) {
                    b.append(" <a href=\"hs:file?name=").append(Url.encode(f.name)).append("\">▶ ").append(t("Reproducir", "Play")).append("</a>");
                }
                b.append("</li>");
            }
            b.append("</ul>");
        }
        b.append("<p><a href=\"hs:home\">").append(t("Volver al inicio", "Back to start")).append("</a></p>");
        return page(t("Descargas", "Downloads"), b.toString());
    }

    /** Pantalla de una pista de audio (la reproducción la maneja la barra de abajo). En vivo no hay nada que descargar. */
    public static String track(String name, String url, boolean live) {
        StringBuilder b = new StringBuilder("<h1>\u266a " + esc(name) + "</h1>");
        if (live) {
            b.append("<p>").append(t("Transmisión en vivo (radio).", "Live stream (radio).")).append("</p>");
        }
        b.append("<p><a href=\"hs:play?src=").append(Url.encode(url)).append(live ? "&live=1" : "").append("\">\u25b6 ")
                .append(t("Reproducir", "Play")).append("</a>");
        if (!live) {
            b.append(" \u00b7 <a href=\"hs:download?url=").append(Url.encode(url)).append("\">").append(t("Descargar", "Download")).append("</a>");
        }
        b.append("</p>");
        b.append("<p>").append(t("Los controles (pausa, parar, volumen) están en la barra de abajo.",
                "Controls (pause, stop, volume) are in the bar at the bottom.")).append("</p>");
        b.append("<p><a href=\"hs:home\">").append(t("Volver al inicio", "Back to start")).append("</a></p>");
        return page(name, b.toString());
    }

    public static String video(String name, String url) {
        StringBuilder b = new StringBuilder("<h1>\u25b6 " + esc(name) + "</h1>");
        b.append("<p><a href=\"hs:playvideo?src=").append(Url.encode(url)).append("\">\u25b6 ").append(t("Reproducir video", "Play video"))
                .append("</a> \u00b7 <a href=\"hs:download?url=").append(Url.encode(url)).append("\">").append(t("Descargar", "Download"))
                .append("</a></p>");
        b.append("<p>").append(t("El video se baja completo antes de empezar. Haz clic en la imagen para pausar; el volumen y la parada están en la barra de abajo.",
                "The video is downloaded completely before it starts. Click the picture to pause; volume and stop are in the bar at the bottom."))
                .append("</p>");
        b.append("<p><a href=\"hs:home\">").append(t("Volver al inicio", "Back to start")).append("</a></p>");
        return page(name, b.toString());
    }

    public static String videoFile(String name) {
        String body = "<h1>\u25b6 " + esc(name) + "</h1><p>"
                + t("Haz clic en la imagen para pausar; el volumen y la parada están en la barra de abajo.",
                "Click the picture to pause; volume and stop are in the bar at the bottom.")
                + "</p><p><a href=\"hs:file?name=" + Url.encode(name) + "\">\u25b6 " + t("Reproducir de nuevo", "Play again") + "</a> \u00b7 <a href=\"hs:downloads\">"
                + t("Descargas", "Downloads") + "</a></p>";
        return page(name, body);
    }

    public static String error(String url, String message) {
        String body = "<h1>" + t("No se pudo cargar la página", "The page could not be loaded") + "</h1>"
                + "<p>" + esc(message) + "</p><p><code>" + esc(url) + "</code></p>"
                + "<p><a href=\"hs:home\">" + t("Volver al inicio", "Back to start") + "</a></p>";
        return page(t("Error", "Error"), body);
    }

    public static String text(String url, String text, boolean truncated) {
        String body = "<pre>" + esc(text) + "</pre>"
                + (truncated ? "<p><i>" + t("(se cortó el texto: es muy largo)", "(text cut: it is too long)") + "</i></p>" : "");
        return page(url, body);
    }

    public static String image(String url) {
        return page(Url.fileName(url, null, null), "<p><img src=\"" + esc(url) + "\" alt=\"" + t("Imagen", "Image") + "\"></p>");
    }

    public static String message(String title, String text) {
        return page(title, "<h1>" + esc(title) + "</h1><p>" + esc(text) + "</p><p><a href=\"hs:home\">"
                + t("Volver al inicio", "Back to start") + "</a></p>");
    }
}
