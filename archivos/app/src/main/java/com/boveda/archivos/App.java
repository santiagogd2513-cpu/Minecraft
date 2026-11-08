package com.boveda.archivos;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // si algo tumba la app, se guarda el error para mostrarlo al volver a abrirla
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.File(getFilesDir(), "ultimo_error.txt"))) {
                w.println("Hilo: " + t.getName());
                e.printStackTrace(w);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
        try {
            ShizukuHelper.init(this);
        } catch (Throwable ignored) {
            // sin Shizuku la app funciona igual
        }
    }
}
