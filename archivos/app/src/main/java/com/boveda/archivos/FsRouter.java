package com.boveda.archivos;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;

/** Elige cómo llegar a cada ruta: normal, por el permiso del selector del sistema, o por Shizuku. */
final class FsRouter {
    private FsRouter() {}

    static Fs get(Context c, String path) throws Fs.NeedAccess {
        if (VaultFs.isVault(path)) {
            if (!Vault.unlocked()) throw new Fs.NeedAccess(path);
            return VaultFs.get(c);
        }
        if (FtpServers.isFtp(path)) {
            try {
                return FtpServers.get(c).fsFor(path);
            } catch (java.io.IOException e) {
                throw new Fs.NeedAccess(path);
            }
        }
        if (OneDriveFs.isOneDrive(path)) {
            if (!OneDrive.get(c).connected()) throw new Fs.NeedAccess(path);
            return OneDriveFs.get(c);
        }
        if (MegaFs.isMega(path)) {
            if (!Mega.get(c).loggedIn()) throw new Fs.NeedAccess(path);
            return MegaFs.get(c);
        }
        if (DriveFs.isDrive(path)) {
            if (!Drive.get(c).connected()) throw new Fs.NeedAccess(path);
            return DriveFs.get(c);
        }
        if (!Paths.restricted(path)) {
            // USB o SD donde Android no deja escribir directo: por Shizuku o por el permiso de esa carpeta
            String vol = removableRoot(path);
            if (vol != null && !directWrite(vol)) {
                IFileService s = ShizukuHelper.service();
                if (s != null) return new ShizukuFs(s);
                SafFs saf = safFor(c, path);
                if (saf != null) return saf;
            }
            return LocalFs.I;
        }
        IFileService s = ShizukuHelper.service();
        if (s != null) return new ShizukuFs(s);
        SafFs saf = safFor(c, path);
        if (saf != null) return saf;
        throw new Fs.NeedAccess(path);
    }

    private static final java.util.Map<String, Boolean> writable = new java.util.concurrent.ConcurrentHashMap<>();

    /** "/storage/1234-ABCD/x/y" → "/storage/1234-ABCD" (USB, SD); null para la memoria interna. */
    static String removableRoot(String path) {
        if (!path.startsWith("/storage/") || Paths.under(path, Paths.ROOT) || path.startsWith("/storage/emulated")) return null;
        int i = path.indexOf('/', 9);
        return i < 0 ? path : path.substring(0, i);
    }

    /** ¿Se puede escribir directo en ese volumen? (se pregunta una vez por volumen) */
    static boolean directWrite(String volRoot) {
        Boolean b = writable.get(volRoot);
        if (b == null) {
            b = new java.io.File(volRoot).canWrite();
            writable.put(volRoot, b);
        }
        return b;
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
