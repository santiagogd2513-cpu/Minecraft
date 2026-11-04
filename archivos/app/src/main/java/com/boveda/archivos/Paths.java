package com.boveda.archivos;

/** Utilidades de rutas. */
final class Paths {
    static final String ROOT = "/storage/emulated/0";
    static final String DATA = ROOT + "/Android/data";
    static final String OBB = ROOT + "/Android/obb";

    private Paths() {}

    static String parent(String p) {
        int i = p.lastIndexOf('/');
        return i <= 0 ? "/" : p.substring(0, i);
    }

    static String name(String p) {
        return p.substring(p.lastIndexOf('/') + 1);
    }

    static String join(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    /** Android/data y Android/obb (y lo de adentro) están protegidas desde Android 11. */
    static boolean restricted(String p) {
        if (android.os.Build.VERSION.SDK_INT < 30) return false;
        for (String base : new String[]{DATA, OBB}) {
            if (under(p, base)) return true;
            // también en tarjetas SD: /storage/XXXX-XXXX/Android/data
            int i = p.indexOf("/Android/");
            if (i > 0) {
                String rest = p.substring(i);
                if (rest.equals("/Android/data") || rest.startsWith("/Android/data/")
                        || rest.equals("/Android/obb") || rest.startsWith("/Android/obb/")) return true;
            }
        }
        return false;
    }

    static boolean validName(String n) {
        return n != null && !n.isEmpty() && !n.equals(".") && !n.equals("..") && n.indexOf('/') < 0 && n.indexOf('\0') < 0;
    }
}
