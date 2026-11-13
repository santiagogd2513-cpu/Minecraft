package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executors;

/** Pide la contraseña de un archivo comprimido cifrado antes de leerlo, y la comprueba. */
final class ArchivePass {
    private ArchivePass() {}

    /** Si el archivo no tiene contraseña (o ya la tenemos), ejecuta go enseguida; si no, la pide. */
    static void ensure(Activity a, String archive, Runnable go) {
        if (Archive.hasPassword(archive)) {
            go.run();
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean need = Archive.needsPassword(a, archive);
            main.post(() -> {
                if (!need) go.run();
                else ask(a, archive, null, go);
            });
        });
    }

    /** Para «Reducir peso»: las carpetas pasan directo; los comprimidos piden contraseña si la tienen. */
    static void ensureForRead(Activity a, String path, boolean folder, Runnable go) {
        if (folder) go.run();
        else ensure(a, path, go);
    }

    private static void ask(Activity a, String archive, String error, Runnable go) {
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView msg = Ui.text(a, error != null ? error : L.t("«") + Paths.name(archive) + L.t("» está protegido con contraseña."), 13.5f, error != null ? Ui.BAD : Ui.SUB, false);
        box.addView(msg);
        EditText pass = new EditText(a);
        pass.setHint(L.t("Contraseña"));
        pass.setHintTextColor(0xFF5E6884);
        pass.setTextColor(Ui.TEXT);
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.passwordEye(pass);
        box.addView(pass);
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Contraseña"))
                .setView(box)
                .setPositiveButton(L.t("Aceptar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            char[] pw = pass.getText().toString().toCharArray();
            if (pw.length == 0) {
                pass.setError(L.t("Escribe la contraseña"));
                return;
            }
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            msg.setText(L.t("Comprobando…"));
            Handler main = new Handler(Looper.getMainLooper());
            Executors.newSingleThreadExecutor().execute(() -> {
                boolean ok = Archive.checkPassword(a, archive, pw);
                main.post(() -> {
                    d.dismiss();
                    if (ok) go.run();
                    else ask(a, archive, L.t("Contraseña incorrecta, prueba otra vez."), go);
                });
            });
        }));
        d.show();
        pass.requestFocus();
    }
}
