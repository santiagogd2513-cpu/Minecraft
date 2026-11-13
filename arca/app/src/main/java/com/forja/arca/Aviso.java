package com.forja.arca;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;

/**
 * El cartel de los errores: sale desde abajo con el anillo rojo, dice qué pasó y por qué, y a los pocos
 * segundos se va solo. «Ver detalles» muestra lo técnico para copiarlo.
 */
final class Aviso {
    private static View shown;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable hide;

    private Aviso() {}

    /** Qué pasó y por qué, explicado en palabras normales a partir de un error. */
    static String[] explain(Throwable e) {
        String m = String.valueOf(e.getMessage());
        String low = m.toLowerCase(java.util.Locale.ROOT);
        if (e instanceof OutOfMemoryError) {
            return new String[]{"Se acabó la memoria del celular", "Esta carpeta es muy grande para tu celular ahora mismo. Cierra otras apps y vuelve a intentarlo."};
        }
        if (low.contains("no respondió") || low.contains("dejó de responder") || low.contains("desconect") || low.contains("comunicación")
                || low.contains("respuesta inválida")) {
            return new String[]{"Se desconectó la memoria", "El cable se movió o el celular dejó de darle corriente al puerto. Vuelve a conectarla y ábrela otra vez."};
        }
        if (low.contains("xtaf") || low.contains("no es una partición")) {
            return new String[]{"Formato desconocido", "Esa unidad no tiene el formato de una Xbox 360 (XTAF). Si la usaste en un computador, Arca no la puede leer."};
        }
        if (low.contains("incompleto") || low.contains("dañad") || low.contains("tabla")) {
            return new String[]{"Archivo o tabla dañados", "Parte de los datos están corruptos. La consola tampoco podría abrirlos. Verificar solo lee: no cambia nada."};
        }
        if (low.contains("permiso") || low.contains("no dejó abrir") || low.contains("otra app")) {
            return new String[]{"No pude abrir la unidad", "Android no me dio acceso o hay otra app usándola. Cierra las otras apps de archivos y vuelve a darle permiso."};
        }
        if (low.contains("protegida") || low.contains("rechazó")) {
            return new String[]{"La unidad rechazó la lectura", "La memoria o el adaptador no dejó leer esa parte. Prueba con otro cable o puerto."};
        }
        if (e instanceof IOException) return new String[]{"No se pudo leer", m};
        return new String[]{"Algo salió mal", m};
    }

    static String details(Throwable e) {
        java.io.StringWriter w = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(w));
        String s = w.toString();
        return s.length() > 3000 ? s.substring(0, 3000) : s;
    }

    static void show(Activity a, FrameLayout host, Throwable e) {
        String[] t = explain(e);
        show(a, host, t[0], t[1], details(e));
    }

    static void show(Activity a, FrameLayout host, String title, String why, String technical) {
        if (a.isFinishing()) return;
        if (shown != null && shown.getParent() instanceof ViewGroup) ((ViewGroup) shown.getParent()).removeView(shown);
        if (hide != null) H.removeCallbacks(hide);
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(px(dp, 14), px(dp, 14), px(dp, 14), px(dp, 14));
        card.setBackground(Ui.roundStroke(0xFF1F1415, px(dp, 20), px(dp, 1), 0x66FF4B4B));
        card.setElevation(px(dp, 8));
        Ring ring = new Ring(a);
        ring.setMode(Ring.ERROR);
        card.addView(ring, new LinearLayout.LayoutParams(px(dp, 46), px(dp, 46)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(dp, 12), 0, 0, 0);
        texts.addView(Ui.text(a, title, 15, Ui.TEXT, true));
        TextView why2 = Ui.text(a, why, 12.5f, 0xFFD9B8B8, false);
        why2.setPadding(0, px(dp, 3), 0, 0);
        texts.addView(why2);
        TextView more = Ui.text(a, "Ver detalles", 13, 0xFFFF8F8F, true);
        more.setPadding(0, px(dp, 8), px(dp, 12), px(dp, 2));
        more.setOnClickListener(v -> {
            dismiss();
            new AlertDialog.Builder(a, R.style.Theme_Arca_Dialog)
                    .setTitle(title)
                    .setMessage(why + "\n\nDetalles técnicos:\n" + technical)
                    .setPositiveButton("Copiar", (d, w) -> {
                        a.getSystemService(android.content.ClipboardManager.class)
                                .setPrimaryClip(android.content.ClipData.newPlainText("error", title + "\n" + why + "\n" + technical));
                        android.widget.Toast.makeText(a, "Copiado", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cerrar", null)
                    .show();
        });
        texts.addView(more);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(px(dp, 12), 0, px(dp, 12), px(dp, 16));
        host.addView(card, lp);
        shown = card;
        card.setTranslationY(px(dp, 220));
        card.animate().translationY(0).setDuration(260).start();
        hide = () -> dismiss();
        H.postDelayed(hide, 8000);
    }

    static void dismiss() {
        View v = shown;
        if (v == null) return;
        shown = null;
        if (hide != null) H.removeCallbacks(hide);
        v.animate().translationY(v.getHeight() + 80f).setDuration(240).withEndAction(() -> {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        }).start();
    }

    private static int px(float dp, float v) {
        return Math.round(v * dp);
    }
}
