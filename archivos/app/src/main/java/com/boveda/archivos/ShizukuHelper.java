package com.boveda.archivos;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

/** Conexión con Shizuku: pide permiso y levanta el servicio que corre como "shell". */
final class ShizukuHelper {
    static final int REQUEST = 4242;
    private static volatile IFileService service;
    private static Shizuku.UserServiceArgs args;
    private static boolean binding;
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ShizukuHelper() {}

    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            if (binder != null && binder.pingBinder()) {
                service = IFileService.Stub.asInterface(binder);
                notifyAll_();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            notifyAll_();
        }
    };

    static void init(Context c) {
        String pkg = c.getPackageName();
        args = new Shizuku.UserServiceArgs(new ComponentName(pkg, FileService.class.getName()))
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(() -> {
            if (granted()) bind();
            notifyAll_();
        });
        Shizuku.addBinderDeadListener(() -> {
            service = null;
            notifyAll_();
        });
        Shizuku.addRequestPermissionResultListener((code, result) -> {
            if (code == REQUEST && result == PackageManager.PERMISSION_GRANTED) bind();
            notifyAll_();
        });
    }

    static void addListener(Runnable r) {
        listeners.add(r);
    }

    static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private static void notifyAll_() {
        for (Runnable r : listeners) r.run();
    }

    /** Shizuku instalado y funcionando. */
    static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean granted() {
        try {
            return running() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean ready() {
        return service != null;
    }

    static IFileService service() {
        return service;
    }

    /** Pide permiso (si hace falta) y conecta. */
    static void connect() {
        if (!running()) return;
        if (granted()) bind();
        else Shizuku.requestPermission(REQUEST);
    }

    private static void bind() {
        if (service != null || binding) return;
        try {
            binding = true;
            Shizuku.bindUserService(args, CONN);
        } catch (Throwable t) {
            binding = false;
        }
    }

    static String status() {
        if (!running()) return L.t("Shizuku no está activo");
        if (!granted()) return L.t("Falta darle permiso a Bóveda");
        if (!ready()) return L.t("Conectando…");
        return L.t("Conectado: acceso completo a Android/data y obb");
    }
}
