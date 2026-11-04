package com.boveda.archivos;

/** Un archivo o carpeta. */
final class Entry {
    final String path;
    final String name;
    final boolean dir;
    final long size;
    final long modified;

    Entry(String path, String name, boolean dir, long size, long modified) {
        this.path = path;
        this.name = name;
        this.dir = dir;
        this.size = size;
        this.modified = modified;
    }

    String extension() {
        int dot = name.lastIndexOf('.');
        return dir || dot <= 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    boolean hidden() {
        return name.startsWith(".");
    }
}
