package com.boveda.archivos;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Tarjeta flotante con el progreso de las tareas (copiar, mover, borrar, comprimir, extraer,
 * subir y bajar de MediaFire). La usan todas las pantallas; al terminar dice cuánto tardó.
 */
final class ProgressCard implements TransferEngine.Listener {
    private final Activity a;
    private final TransferEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout card;
    private final TextView stage, title, pct, info;
    private final ProgressBar bar;
    private TransferEngine.Job job;
    private boolean hidden;          // la cerró con la X: vuelve a salir con la próxima tarea
    private long seenUntil;          // tareas terminadas antes de esto ya se mostraron

    ProgressCard(Activity a, FrameLayout root, int bottomDp, Runnable onTap) {
        this.a = a;
        engine = TransferEngine.get(a);
        seenUntil = System.currentTimeMillis();
        card = Ui.column(a);
        card.setPadding(dp(16), dp(12), dp(8), dp(14));
        card.setBackground(Ui.roundStroke(0xF2182033, dp(18), dp(1), 0xFF2E3A55));
        card.setElevation(dp(10));
        card.setVisibility(View.GONE);
        if (onTap != null) card.setOnClickListener(v -> onTap.run());

        // qué está haciendo ahora (Copiando, Guardando, Verificando…), en chiquito encima
        stage = Ui.text(a, "", 11.5f, Ui.ACCENT2, true);
        stage.setLetterSpacing(0.06f);
        stage.setSingleLine(true);
        stage.setEllipsize(TextUtils.TruncateAt.END);
        stage.setPadding(0, 0, dp(8), dp(2));
        card.addView(stage);

        LinearLayout top = Ui.row(a);
        title = Ui.text(a, "", 14.5f, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(title, Ui.weight(1));
        pct = Ui.text(a, "", 15, Ui.ACCENT2, true);
        pct.setPadding(dp(8), 0, dp(4), 0);
        top.addView(pct);
        top.addView(Ui.iconButton(a, R.drawable.ic_close, 34, v -> {
            hidden = true;
            card.setVisibility(View.GONE);
        }));
        card.addView(top);

        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        tint(Ui.ACCENT);
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A3346));
        bar.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
        bl.setMargins(0, dp(6), dp(8), dp(6));
        card.addView(bar, bl);

        info = Ui.text(a, "", 12.5f, Ui.SUB, false);
        info.setMaxLines(2);
        info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        card.addView(info);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(12), dp(bottomDp));
        root.addView(card, lp);
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private void tint(int color) {
        bar.setProgressTintList(ColorStateList.valueOf(color));
    }

    void start() {
        engine.addListener(this);
        update();
    }

    void stop() {
        engine.removeListener(this);
    }

    @Override
    public void onChanged() {
        main.post(this::update);
    }

    private static String seconds(long ms) {
        double s = ms / 1000.0;
        if (s < 60) return String.format(Locale.US, "%.1f s", s).replace('.', ',');
        return Ui.eta(Math.round(s));
    }

    void update() {
        if (!new Prefs(a).floatingProgress()) {
            card.setVisibility(View.GONE);
            return;
        }
        TransferEngine.Job run = null;
        int waiting = 0;
        for (TransferEngine.Job j : engine.jobs()) {
            if (j.state == 1 && run == null) run = j;
            else if (j.state == 0) waiting++;
        }
        if (run == null) {
            for (TransferEngine.Job j : engine.jobs()) if (j.state == 0) { run = j; waiting--; break; }
        }
        if (run == null) {
            // terminó: el resultado con lo que tardó, un momento, y se va.
            // También las tareas tan rápidas que nunca se alcanzaron a ver corriendo.
            TransferEngine.Job last = null;
            for (TransferEngine.Job j : engine.jobs()) {
                if (j.state >= 2 && j.finished > seenUntil && (last == null || j.finished > last.finished)) last = j;
            }
            job = null;
            if (last == null) return;
            seenUntil = last.finished;
            if (hidden) return;
            show();
            boolean ok = last.state == 2;
            stage.setText(ok ? "✓ TERMINADO" : last.state == 4 ? "CANCELADO" : "ERROR");
            stage.setTextColor(ok ? Ui.GOOD : Ui.BAD);
            title.setText(last.title());
            pct.setText(ok ? "100%" : "");
            bar.setIndeterminate(false);
            bar.setProgress(ok ? 1000 : bar.getProgress());
            tint(ok ? Ui.GOOD : Ui.BAD);
            long ms = Math.max(1, last.finished - last.started);
            String txt;
            if (ok) {
                txt = "Listo ✓ en " + seconds(ms);
                if (last.totalBytes > 0) txt += "  ·  " + Ui.size(last.totalBytes) + "  ·  " + Ui.speed(last.totalBytes * 1000.0 / ms) + " promedio";
            } else {
                txt = last.state == 4 ? "Cancelado" : "Error: " + last.error;
            }
            info.setText(txt);
            final long shown = last.finished;
            main.postDelayed(() -> {
                if (job == null && seenUntil == shown) card.animate().alpha(0).setDuration(250)
                        .withEndAction(() -> { card.setVisibility(View.GONE); card.setAlpha(1); }).start();
            }, ok ? 4000 : 5000);
            return;
        }
        if (run != job) {
            job = run;
            hidden = false;
            tint(Ui.ACCENT);
        }
        if (hidden) return;
        show();
        stage.setTextColor(Ui.ACCENT2);
        stage.setText(stageText(run));
        title.setText(run.title() + (waiting > 0 ? "  (+" + waiting + " en cola)" : ""));
        float prog = run.totalBytes > 0 ? (float) run.doneBytes / run.totalBytes
                : run.totalItems > 0 ? (float) run.doneItems / run.totalItems : -1;
        if (run.state == 0 || prog < 0) {
            bar.setIndeterminate(true);
            pct.setText("");
        } else {
            bar.setIndeterminate(false);
            int pm = Math.round(Math.max(0, Math.min(1, prog)) * 1000);
            bar.setProgress(pm);
            pct.setText((pm / 10) + "%");
        }
        String txt;
        if (run.state == 0) txt = "En cola…";
        else if (run.totalBytes > 0) {
            boolean waitingDisk = run.phase != null && (run.phase.startsWith("Guardando") || run.phase.startsWith("Verificando")
                    || run.phase.startsWith("MediaFire"));
            txt = Ui.size(run.doneBytes) + " de " + Ui.size(run.totalBytes);
            if (!waitingDisk) {
                txt += "  ·  " + Ui.speed(run.speed);
                if (run.speed > 1) txt += "  ·  faltan " + Ui.eta((long) ((run.totalBytes - run.doneBytes) / run.speed));
            }
        } else txt = run.doneItems + " de " + run.totalItems + " elementos";
        if (run.started > 0) txt += "  ·  " + seconds(System.currentTimeMillis() - run.started);
        if (run.current != null && !run.current.isEmpty()) txt += "\n" + run.current;
        info.setText(txt);
    }

    /** Qué está haciendo, para que nadie crea que se trabó cuando la velocidad baja a cero. */
    static String stageText(TransferEngine.Job j) {
        if (j.state == 0) return "EN COLA";
        String p = j.phase;
        if (p == null) p = switch (j.kind) {
            case COPY -> "Copiando";
            case MOVE -> "Moviendo";
            case DELETE -> "Borrando";
            case ZIP -> "Comprimiendo";
            case EXTRACT -> "Extrayendo";
            case CLOUD_DOWN -> "Descargando";
            case CLOUD_UP -> "Subiendo";
        };
        return "● " + p.toUpperCase(java.util.Locale.ROOT) + "…";
    }

    private void show() {
        card.animate().cancel();
        card.setAlpha(1);
        if (card.getVisibility() != View.VISIBLE) {
            card.setAlpha(0);
            card.setTranslationY(dp(20));
            card.setVisibility(View.VISIBLE);
            card.animate().alpha(1).translationY(0).setDuration(200).start();
        }
    }
}
