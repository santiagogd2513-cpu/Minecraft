package com.boveda.archivos;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ShizukuHelper.init(this);
        } catch (Throwable ignored) {
            // sin Shizuku la app funciona igual
        }
    }
}
