package com.ventoy.nooficial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import java.security.MessageDigest;

/**
 * El sello de autenticidad: la app revisa con qué llave fue firmada. Las versiones oficiales de Forja
 * Estudios están firmadas con una llave que solo tiene el estudio; si alguien copia la app, le mete
 * cosas y la vuelve a firmar, la huella cambia y la app avisa que es una copia modificada.
 */
final class Integrity {
    /** Huella SHA-256 del certificado oficial. */
    private static final String OFFICIAL = "8e67ea48a79a65a9cfc8b0397ea2fe4c6ab75811817d35199786f34e8bf4fe2c";
    private static boolean shown;

    private Integrity() {}

    static boolean genuine(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            // las compilaciones de prueba (versión 1) no se revisan
            if (pi.getLongVersionCode() <= 1) return true;
            if (pi.signingInfo == null) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : pi.signingInfo.getApkContentsSigners()) {
                StringBuilder sb = new StringBuilder();
                for (byte b : md.digest(s.toByteArray())) sb.append(String.format("%02x", b));
                if (sb.toString().equals(OFFICIAL)) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** Si no es la original, avisa (una vez por sesión). */
    static void check(Activity a) {
        if (shown || genuine(a)) return;
        shown = true;
        new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                .setTitle("⚠️ Copia modificada")
                .setMessage("Esta app no es la versión original de Forja Estudios: alguien la modificó y la volvió a firmar. Puede traer anuncios, virus o robar tus datos." + "\n\n"
                        + "Desinstálala y descarga la original, gratis y sin anuncios, en santiagogd2513-cpu.github.io")
                .setCancelable(false)
                .setPositiveButton("Descargar la original", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://santiagogd2513-cpu.github.io/")));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("Seguir de todos modos", null)
                .show();
    }
}
