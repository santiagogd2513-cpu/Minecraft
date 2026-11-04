package com.boveda.archivos;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;

/** Elige cómo llegar a cada ruta: normal, por el permiso del selector del sistema, o por Shizuku. */
final class FsRouter {
    private FsRouter() {}

    static Fs get(Context c, String path) throws Fs.NeedAccess {
        if (!Paths.restricted(path)) return LocalFs.I;
        IFileService s = ShizukuHelper.service();
        if (s != null) return new ShizukuFs(s);
        SafFs saf = safFor(c, path);
        if (saf != null) return saf;
        throw new Fs.NeedAccess(path);
    }

    /** Busca un permiso guardado (del selector del sistema) que cubra esta ruta. Gana el más específico. */
    static SafFs safFor(Context c, String path) {
        SafFs best = null;
        for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
            Uri u = p.getUri();
            if (!SafFs.AUTHORITY.equals(u.getAuthority()) || !p.isReadPermission()) continue;
            String docId;
            try {
                docId = DocumentsContract.getTreeDocumentId(u);
            } catch (Exception e) {
                continue;
            }
            String base = basePath(docId);
            if (base == null || !Paths.under(path, base)) continue;
            if (best == null || base.length() > best.base.length()) best = new SafFs(c.getContentResolver(), u, base, docId);
        }
        return best;
    }

    /** "primary:Android/data" → /storage/emulated/0/Android/data ; "1234-ABCD:x" → /storage/1234-ABCD/x */
    static String basePath(String docId) {
        int i = docId.indexOf(':');
        if (i < 0) return null;
        String vol = docId.substring(0, i), rel = docId.substring(i + 1);
        String root = vol.equals("primary") ? Paths.ROOT : "/storage/" + vol;
        return rel.isEmpty() ? root : root + "/" + rel;
    }

    /** "/storage/emulated/0/Android/data/x" → "primary:Android/data/x" */
    static String docIdFor(String path) {
        if (Paths.under(path, Paths.ROOT)) return "primary:" + (path.equals(Paths.ROOT) ? "" : path.substring(Paths.ROOT.length() + 1));
        if (path.startsWith("/storage/")) {
            String rest = path.substring(9);
            int i = rest.indexOf('/');
            return i < 0 ? rest + ":" : rest.substring(0, i) + ":" + rest.substring(i + 1);
        }
        return null;
    }

    /** ¿Hay alguna forma de entrar ya a esta ruta? */
    static boolean hasAccess(Context c, String path) {
        try {
            get(c, path);
            return true;
        } catch (Fs.NeedAccess e) {
            return false;
        }
    }
}
