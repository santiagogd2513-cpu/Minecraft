package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** La ventana del convertidor de audio y del extractor de audio de un video. Guarda siempre una copia nueva. */
final class AudioToolDialog {
    private AudioToolDialog() {}

    interface Saver {
        /** Pasa el archivo ya listo (en la memoria temporal) a su destino. Devuelve un texto para mostrar. */
        String save(String fileName, File produced) throws IOException;
    }

    /** mode: «convert» (un audio) o «extract» (sacar el audio de un video). */
    static void show(Activity a, String mode, File input, String displayName, Saver saver) {
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        AtomicBoolean cancel = new AtomicBoolean(false);
        String base = displayName.contains(".") ? displayName.substring(0, displayName.lastIndexOf('.')) : displayName;
        boolean extract = mode.equals("extract");

        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView info = Ui.text(a, L.t("Leyendo el archivo…"), 13.5f, Ui.SUB, false);
        box.addView(info);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                .setTitle(extract ? L.t("Extraer el audio de un video") : L.t("Convertir audio"))
                .setView(sv)
                .setPositiveButton(extract ? L.t("Extraer") : L.t("Convertir"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setCancelable(false);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            cancel.set(true);
            d.dismiss();
            io.shutdownNow();
            //noinspection ResultOfMethodCallIgnored
            input.delete();
        });

        io.execute(() -> {
            AudioTools.Info probe;
            try {
                probe = AudioTools.probe(input);
                if (!probe.hasAudio) throw new IOException(L.t("Este archivo no tiene audio"));
            } catch (Throwable t) {
                String m = t.getMessage() == null ? t.toString() : t.getMessage();
                main.post(() -> {
                    info.setTextColor(Ui.BAD);
                    info.setText(m);
                });
                return;
            }
            final AudioTools.Info pi = probe;
            main.post(() -> build(a, extract, d, box, info, pi, base, input, saver, main, io, cancel));
        });
    }

    private static void build(Activity a, boolean extract, AlertDialog d, LinearLayout box, TextView info, AudioTools.Info pi,
                              String base, File input, Saver saver, Handler main, ExecutorService io, AtomicBoolean cancel) {
        long secs = pi.durationUs / 1_000_000L;
        info.setText((secs > 0 ? fmt(secs) + " · " : "") + pi.channels + (pi.channels == 1 ? L.t(" canal") : L.t(" canales")) + " · "
                + (pi.sampleRate / 1000.0) + " kHz · " + pi.mime.replace("audio/", "").toUpperCase(Locale.ROOT));

        final AudioTools.Out[] fmtSel = {AudioTools.Out.M4A};
        final int[] kbps = {128};
        final boolean[] named = {false};
        EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        TextView ext2 = Ui.text(a, fmtSel[0].ext, 15, Ui.ACCENT2, true);
        TextView note = Ui.text(a, "", 12.5f, Ui.SUB, false);
        AudioTools.Out[] outs = AudioTools.Out.values();
        TextView[] fmtTabs = new TextView[outs.length];
        int[] rates = {64, 96, 128, 192, 256};
        TextView[] rateTabs = new TextView[rates.length];
        LinearLayout rateBox = Ui.column(a);
        Runnable[] refresh = new Runnable[1];

        box.addView(label(a, L.t("Convertir a")));
        LinearLayout fm = Ui.row(a);
        for (int i = 0; i < outs.length; i++) {
            fmtTabs[i] = chip(a, outs[i].label);
            final int k = i;
            fmtTabs[i].setOnClickListener(v -> {
                fmtSel[0] = outs[k];
                refresh[0].run();
            });
            fm.addView(fmtTabs[i], chipParams(a));
        }
        box.addView(scroll(a, fm));

        rateBox.addView(label(a, L.t("Calidad (kilobits por segundo)")));
        LinearLayout rr = Ui.row(a);
        for (int i = 0; i < rates.length; i++) {
            rateTabs[i] = chip(a, String.valueOf(rates[i]));
            final int k = i;
            rateTabs[i].setOnClickListener(v -> {
                kbps[0] = rates[k];
                refresh[0].run();
            });
            rr.addView(rateTabs[i], chipParams(a));
        }
        rateBox.addView(scroll(a, rr));
        box.addView(rateBox);

        box.addView(label(a, L.t("Nombre de la copia")));
        LinearLayout nameRow = Ui.row(a);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(name, Ui.weight(1));
        nameRow.addView(ext2);
        box.addView(nameRow);
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int x, int y, int z) {}

            public void onTextChanged(CharSequence c, int x, int y, int z) {}

            public void afterTextChanged(Editable ed) {
                if (name.hasFocus()) named[0] = true;
            }
        });
        note.setPadding(Ui.dp(a, 2), Ui.dp(a, 12), 0, Ui.dp(a, 2));
        box.addView(note);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setVisibility(View.GONE);
        box.addView(bar);

        refresh[0] = () -> {
            for (int i = 0; i < fmtTabs.length; i++) mark(a, fmtTabs[i], outs[i] == fmtSel[0]);
            for (int i = 0; i < rateTabs.length; i++) mark(a, rateTabs[i], rates[i] == kbps[0]);
            rateBox.setVisibility(fmtSel[0].lossy() ? View.VISIBLE : View.GONE);
            ext2.setText(fmtSel[0].ext);
            if (!named[0]) name.setText(base + (extract ? "_audio" : "_convertido"));
            StringBuilder n = new StringBuilder();
            n.append(L.t("Se guarda una copia nueva; el original no se toca."));
            if (extract && fmtSel[0] == AudioTools.Out.M4A && "audio/mp4a-latm".equals(pi.mime)) {
                n.append("\n✓ ").append(L.t("El audio de este video ya es AAC: se copia sin recomprimir (rápido y sin perder calidad)."));
            }
            if (fmtSel[0] == AudioTools.Out.WAV) {
                n.append("\nℹ ").append(L.t("WAV no comprime: el archivo puede pesar 10 veces más."));
            }
            if (fmtSel[0] == AudioTools.Out.FLAC) {
                n.append("\nℹ ").append(L.t("FLAC no pierde calidad, pero pesa mucho más que MP3 o AAC; y si el original ya estaba comprimido, no recupera lo perdido."));
            }
            if (fmtSel[0] == AudioTools.Out.OPUS) {
                n.append("\nℹ ").append(L.t("Es OGG con Opus. Minecraft pide OGG Vorbis, que Android no puede crear."));
            }
            if (fmtSel[0].lossy() && !(extract && fmtSel[0] == AudioTools.Out.M4A && "audio/mp4a-latm".equals(pi.mime))) {
                n.append("\nℹ ").append(L.t("Convertir comprime de nuevo: si el original ya estaba comprimido (MP3, AAC), se pierde algo de calidad."));
            }
            note.setText(n.toString());
        };
        refresh[0].run();

        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            AudioTools.Out fmt = fmtSel[0];
            if (nm.toLowerCase(Locale.ROOT).endsWith(fmt.ext)) nm = nm.substring(0, nm.length() - fmt.ext.length());
            if (!Paths.validName(nm + fmt.ext)) {
                name.setError(L.t("Nombre no válido"));
                return;
            }
            final String file = nm + fmt.ext;
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            bar.setVisibility(View.VISIBLE);
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setText(L.t("Detener"));
            note.setText(L.t("Convirtiendo… no cierres la ventana."));
            io.execute(() -> {
                File out = new File(a.getCacheDir(), "tool_out_" + System.nanoTime() + fmt.ext);
                String msg;
                boolean ok = true;
                try {
                    AudioTools.convert(input, out, fmt, kbps[0], extract, new AudioTools.Progress() {
                        public void onProgress(float f) {
                            main.post(() -> bar.setProgress(Math.round(f * 1000)));
                        }

                        public boolean cancelled() {
                            return cancel.get() || Thread.currentThread().isInterrupted();
                        }
                    });
                    msg = saver.save(file, out);
                } catch (Throwable t) {
                    ok = false;
                    msg = t instanceof OutOfMemoryError ? L.t("Faltó memoria.") : (t.getMessage() == null ? t.toString() : t.getMessage());
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    //noinspection ResultOfMethodCallIgnored
                    input.delete();
                }
                String finalMsg = msg;
                boolean fo = ok;
                main.post(() -> {
                    if (cancel.get()) return;
                    if (fo) {
                        d.dismiss();
                        new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog).setTitle(L.t("Listo ✅")).setMessage(finalMsg)
                                .setPositiveButton(L.t("Entendido"), null)
                                .setNegativeButton(L.t("Abrir carpeta"), (x, w) -> {
                                    if (a instanceof MainActivity m) m.openToolFolder();
                                }).show();
                    } else {
                        bar.setVisibility(View.GONE);
                        d.getButton(AlertDialog.BUTTON_NEGATIVE).setText(L.t("Cerrar"));
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
                        note.setTextColor(Ui.BAD);
                        note.setText(finalMsg);
                    }
                });
            });
        });
    }

    private static String fmt(long secs) {
        return String.format(Locale.US, "%d:%02d", secs / 60, secs % 60);
    }

    private static TextView label(Activity a, String s) {
        TextView t = Ui.text(a, s.toUpperCase(), 11.5f, Ui.ACCENT2, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 14), 0, Ui.dp(a, 6));
        return t;
    }

    private static TextView chip(Activity a, String s) {
        TextView t = Ui.text(a, s, 13.5f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 9), Ui.dp(a, 14), Ui.dp(a, 9));
        return t;
    }

    private static void mark(Activity a, TextView t, boolean on) {
        t.setTextColor(on ? 0xFF0A0E16 : Ui.TEXT);
        t.setBackground(on ? Ui.gradient(Ui.dp(a, 18), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 18), Ui.dp(a, 1), Ui.STROKE));
    }

    private static LinearLayout.LayoutParams chipParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, 0, Ui.dp(a, 8), 0);
        return p;
    }

    private static HorizontalScrollView scroll(Activity a, View v) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }
}
