package com.boveda.archivos;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Las apps instaladas como carpetas con sus instaladores (APK), para llevarlas al otro celular. Sin sus datos ni sus cuentas: Android no lo permite. */
final class LinkApps implements LinkVfs.Source {
    private final Context ctx;
    private final PackageManager pm;

    LinkApps(Context c) {
        ctx = c.getApplicationContext();
        pm = ctx.getPackageManager();
    }

    public String name() {
        return "Aplicaciones";
    }

    public boolean writable() {
        return false;
    }

    private static String clean(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    }

    private List<ApplicationInfo> apps() {
        List<ApplicationInfo> out = new ArrayList<>();
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            boolean system = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system || a.packageName.equals(ctx.getPackageName())) continue;
            out.add(a);
        }
        return out;
    }

    private String folderName(ApplicationInfo a) {
        return clean(String.valueOf(pm.getApplicationLabel(a))) + " [" + a.packageName + "]";
    }

    private ApplicationInfo find(String dir) {
        for (ApplicationInfo a : apps()) if (folderName(a).equals(dir)) return a;
        return null;
    }

    private List<File> files(ApplicationInfo a) {
        List<File> fs = new ArrayList<>();
        fs.add(new File(a.sourceDir));
        if (a.splitSourceDirs != null) for (String s : a.splitSourceDirs) fs.add(new File(s));
        return fs;
    }

    public List<LinkVfs.Item> list(String rel) throws IOException {
        List<LinkVfs.Item> out = new ArrayList<>();
        if (rel.isEmpty()) {
            for (ApplicationInfo a : apps()) out.add(new LinkVfs.Item(folderName(a), true, 0, new File(a.sourceDir).lastModified()));
            return out;
        }
        ApplicationInfo a = find(rel);
        if (a == null) throw new IOException("esa app ya no está");
        for (File f : files(a)) out.add(new LinkVfs.Item(f.getName().equals("base.apk") ? clean(String.valueOf(pm.getApplicationLabel(a))) + ".apk" : f.getName(), false, f.length(), f.lastModified()));
        return out;
    }

    private File file(String rel) throws IOException {
        int i = rel.indexOf('/');
        if (i < 0) throw new IOException("no es un archivo");
        ApplicationInfo a = find(rel.substring(0, i));
        if (a == null) throw new IOException("esa app ya no está");
        String name = rel.substring(i + 1);
        for (File f : files(a)) {
            String shown = f.getName().equals("base.apk") ? clean(String.valueOf(pm.getApplicationLabel(a))) + ".apk" : f.getName();
            if (shown.equals(name)) return f;
        }
        throw new IOException("no existe ese archivo");
    }

    public LinkVfs.Item stat(String rel) throws IOException {
        if (rel.indexOf('/') < 0) {
            ApplicationInfo a = find(rel);
            return a == null ? null : new LinkVfs.Item(rel, true, 0, 0);
        }
        File f = file(rel);
        return new LinkVfs.Item(Paths.name(rel), false, f.length(), f.lastModified());
    }

    public InputStream open(String rel, long offset) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(file(rel));
        long skipped = 0;
        while (skipped < offset) {
            long s = in.skip(offset - skipped);
            if (s <= 0) break;
            skipped += s;
        }
        return in;
    }

    public LinkVfs.Sink create(String rel, long size, long modified) {
        throw new SecurityException("las aplicaciones son solo de lectura");
    }

    public void mkdir(String rel) {
        throw new SecurityException("las aplicaciones son solo de lectura");
    }

    public void delete(String rel) {
        throw new SecurityException("las aplicaciones son solo de lectura");
    }
}
