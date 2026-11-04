package com.nocturna.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** Pide el permiso de "Acceso a todos los archivos" para usar la carpeta Nocturna Downloads. */
final class StoragePermission {
    private StoragePermission() {}

    static void ask(Activity a, Runnable onSkip) {
        if (Build.VERSION.SDK_INT < 30 || DownloadEngine.canUseRootFolder()) return;
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("Carpeta Nocturna Downloads")
                .setMessage("Para guardar tus descargas en la carpeta «Nocturna Downloads», en el almacenamiento principal "
                        + "(junto a Download, DCIM, Android…), Android pide el permiso «Acceso a todos los archivos».\n\n"
                        + "En la pantalla que se abre, activa el interruptor de Nocturna y vuelve.\n\n"
                        + "Si no lo das, las descargas se guardan en Descargas/Nocturna.")
                .setPositiveButton("Dar permiso", (d, w) -> open(a))
                .setNegativeButton("Ahora no", (d, w) -> {
                    if (onSkip != null) onSkip.run();
                })
                .show();
    }

    private static void open(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
            }
        }
    }
}
