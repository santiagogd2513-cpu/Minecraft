package com.boveda.archivos;

import android.webkit.MimeTypeMap;

/** Ícono, color y tipo de cada archivo según su extensión. */
final class FileTypes {
    private FileTypes() {}

    static int icon(Entry e) {
        if (e.dir) return R.drawable.ic_folder;
        switch (e.extension()) {
            case "jpg", "jpeg", "jfif", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tga", "tif", "tiff",
                 "psd", "qoi", "ppm", "pgm", "pbm", "dng", "cr2", "nef", "arw", "orf", "rw2", "raf": return R.drawable.ic_image;
            case "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v": return R.drawable.ic_movie;
            case "mp3", "ogg", "wav", "flac", "m4a", "aac", "opus": return R.drawable.ic_music;
            case "zip", "rar", "7z", "tar", "gz", "tgz", "xz", "txz", "bz2", "tbz2", "lz4", "lzma", "z", "cpio", "arj", "deb", "iso", "obb", "jar", "cbz", "cb7":
                return R.drawable.ic_archive;
            case "apk", "apks", "xapk", "apkm": return R.drawable.ic_android;
            case "pdf", "doc", "docx", "txt", "md", "odt", "xls", "xlsx", "ppt", "pptx", "csv": return R.drawable.ic_doc;
            case "json", "xml", "html", "js", "java", "kt", "py", "c", "cpp", "sh", "yml", "yaml", "toml", "properties", "cfg", "ini", "log":
                return R.drawable.ic_code;
            default: return R.drawable.ic_file;
        }
    }

    static int color(Entry e) {
        if (e.dir) return 0xFF4C9DFF;
        int i = icon(e);
        if (i == R.drawable.ic_image) return 0xFF5CC8FF;
        if (i == R.drawable.ic_movie) return 0xFFFF6A7A;
        if (i == R.drawable.ic_music) return 0xFFB58CFF;
        if (i == R.drawable.ic_archive) return 0xFFFFC14D;
        if (i == R.drawable.ic_android) return 0xFF35D08A;
        if (i == R.drawable.ic_doc) return 0xFFFF8A5C;
        if (i == R.drawable.ic_code) return 0xFF7ED7C1;
        return 0xFF8B97AB;
    }

    static String mime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String ext = name.substring(dot + 1).toLowerCase();
            if (ext.equals("apk")) return "application/vnd.android.package-archive";
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "application/octet-stream";
    }

}
