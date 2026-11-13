package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * La ventana de las herramientas de imágenes: cambiar el tamaño (con medidas exactas), pasar a otro formato y quitar los
 * datos ocultos. Siempre guarda una copia nueva; el original no se toca.
 */
final class ImageToolDialog {
    private ImageToolDialog() {}

    interface Saver {
        /** Guarda la imagen ya lista con ese nombre (en otro hilo). Devuelve un texto para mostrar. */
        String save(String fileName, byte[] data) throws IOException;
    }

    /** mode: «resize», «convert» o «exif». */
    static void show(Activity a, String mode, String fileName, byte[] data, Saver saver) {
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        int dp8 = Ui.dp(a, 8);
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";

        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView info = Ui.text(a, L.t("Abriendo la imagen…"), 13.5f, Ui.SUB, false);
        box.addView(info);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        String title = mode.equals("resize") ? L.t("Cambiar tamaño de la imagen")
                : mode.equals("convert") ? L.t("Convertir formato de imagen") : L.t("Quitar datos ocultos");
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton(L.t("Guardar copia"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        d.setOnDismissListener(x -> io.shutdownNow());

        io.execute(() -> {
            Bitmap bmp;
            try {
                bmp = ImageTools.load(data, ext);
            } catch (Throwable t) {
                String m = t.getMessage() == null ? t.toString() : t.getMessage();
                main.post(() -> {
                    info.setTextColor(Ui.BAD);
                    info.setText(m);
                });
                return;
            }
            final Bitmap src = bmp;
            main.post(() -> build(a, mode, d, box, info, src, base, ext, data.length, saver, main, io));
        });
    }

    // estado de la ventana
    private static final class State {
        int w, h, ow, oh;
        boolean lock = true, smooth = true;
        ImageTools.Fit fit = ImageTools.Fit.STRETCH;
        ImageTools.Out out = ImageTools.Out.PNG;
        boolean userNamed;
    }

    private static void build(Activity a, String mode, AlertDialog d, LinearLayout box, TextView info, Bitmap src, String base,
                              String ext, int bytes, Saver saver, Handler main, ExecutorService io) {
        State s = new State();
        s.ow = s.w = src.getWidth();
        s.oh = s.h = src.getHeight();
        String e = ext.toLowerCase(Locale.ROOT);
        s.out = e.equals("jpg") || e.equals("jpeg") ? ImageTools.Out.JPG : e.equals("webp") ? ImageTools.Out.WEBP
                : e.equals("bmp") ? ImageTools.Out.BMP : ImageTools.Out.PNG;
        info.setText(L.t("Original: ") + s.ow + "×" + s.oh + " · " + Ui.size(bytes) + " · " + ext.toUpperCase(Locale.ROOT));

        EditText ew = numField(a, String.valueOf(s.w)), eh = numField(a, String.valueOf(s.h));
        EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        TextView ext2 = Ui.text(a, s.out.ext, 15, Ui.ACCENT2, true);
        TextView note = Ui.text(a, "", 12.5f, Ui.SUB, false);
        TextView[] fmtTabs = new TextView[ImageTools.Out.values().length];
        TextView[] fitTabs = new TextView[3];
        TextView[] smoothTabs = new TextView[2];
        Runnable[] refresh = new Runnable[1];

        boolean resizeUi = mode.equals("resize");
        if (resizeUi) {
            box.addView(label(a, L.t("Tamaño (píxeles)")));
            LinearLayout sizes = Ui.row(a);
            sizes.setGravity(Gravity.CENTER_VERTICAL);
            sizes.addView(ew, Ui.weight(1));
            sizes.addView(Ui.text(a, "  ×  ", 16, Ui.SUB, true));
            sizes.addView(eh, Ui.weight(1));
            box.addView(sizes);
            LinearLayout presets = Ui.row(a);
            for (int p : new int[]{16, 32, 64, 128, 256, 512, 1024}) {
                TextView c = chip(a, String.valueOf(p));
                c.setOnClickListener(v -> {
                    if (s.lock && s.ow != s.oh) {
                        float k = p / (float) Math.max(s.ow, s.oh);
                        s.w = Math.max(1, Math.round(s.ow * k));
                        s.h = Math.max(1, Math.round(s.oh * k));
                    } else {
                        s.w = p;
                        s.h = p;
                    }
                    ew.setText(String.valueOf(s.w));
                    eh.setText(String.valueOf(s.h));
                    refresh[0].run();
                });
                presets.addView(c, chipParams(a));
            }
            box.addView(scroll(a, presets));
            TextView lock = Ui.text(a, "", 13.5f, Ui.TEXT, false);
            lock.setPadding(Ui.dp(a, 2), Ui.dp(a, 10), 0, Ui.dp(a, 2));
            Runnable showLock = () -> lock.setText((s.lock ? "☑ " : "☐ ") + L.t("Mantener las proporciones"));
            showLock.run();
            lock.setOnClickListener(v -> {
                s.lock = !s.lock;
                showLock.run();
            });
            box.addView(lock);
            TextWatcher tw = new TextWatcher() {
                boolean busy;

                public void beforeTextChanged(CharSequence c, int x, int y, int z) {}

                public void onTextChanged(CharSequence c, int x, int y, int z) {}

                public void afterTextChanged(Editable ed) {
                    if (busy) return;
                    busy = true;
                    try {
                        int nw = parse(ew), nh = parse(eh);
                        if (s.lock && ed == ew.getText() && nw > 0) {
                            nh = Math.max(1, Math.round(nw * s.oh / (float) s.ow));
                            eh.setText(String.valueOf(nh));
                        } else if (s.lock && ed == eh.getText() && nh > 0) {
                            nw = Math.max(1, Math.round(nh * s.ow / (float) s.oh));
                            ew.setText(String.valueOf(nw));
                        }
                        s.w = nw;
                        s.h = nh;
                        refresh[0].run();
                    } finally {
                        busy = false;
                    }
                }
            };
            ew.addTextChangedListener(tw);
            eh.addTextChangedListener(tw);

            box.addView(label(a, L.t("Cuando la forma cambia")));
            LinearLayout fits = Ui.row(a);
            String[] fitNames = {L.t("Estirar"), L.t("Ajustar (con bordes)"), L.t("Recortar al centro")};
            for (int i = 0; i < 3; i++) {
                fitTabs[i] = chip(a, fitNames[i]);
                final int k = i;
                fitTabs[i].setOnClickListener(v -> {
                    s.fit = ImageTools.Fit.values()[k];
                    refresh[0].run();
                });
                fits.addView(fitTabs[i], chipParams(a));
            }
            box.addView(scroll(a, fits));

            box.addView(label(a, L.t("Calidad al cambiar de tamaño")));
            LinearLayout sm = Ui.row(a);
            String[] smNames = {L.t("Suave"), L.t("Pixelado nítido (texturas y pixel art)")};
            for (int i = 0; i < 2; i++) {
                smoothTabs[i] = chip(a, smNames[i]);
                final int k = i;
                smoothTabs[i].setOnClickListener(v -> {
                    s.smooth = k == 0;
                    refresh[0].run();
                });
                sm.addView(smoothTabs[i], chipParams(a));
            }
            box.addView(scroll(a, sm));
        }

        box.addView(label(a, L.t("Formato")));
        LinearLayout fm = Ui.row(a);
        ImageTools.Out[] outs = ImageTools.Out.values();
        for (int i = 0; i < outs.length; i++) {
            fmtTabs[i] = chip(a, outs[i].name());
            final int k = i;
            fmtTabs[i].setOnClickListener(v -> {
                s.out = outs[k];
                refresh[0].run();
            });
            fm.addView(fmtTabs[i], chipParams(a));
        }
        box.addView(scroll(a, fm));

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
                if (name.hasFocus()) s.userNamed = true;
            }
        });
        note.setPadding(Ui.dp(a, 2), Ui.dp(a, 12), 0, Ui.dp(a, 2));
        box.addView(note);

        refresh[0] = () -> {
            for (int i = 0; i < fmtTabs.length; i++) mark(a, fmtTabs[i], outs[i] == s.out);
            for (int i = 0; i < fitTabs.length; i++) if (fitTabs[i] != null) mark(a, fitTabs[i], s.fit.ordinal() == i);
            if (smoothTabs[0] != null) {
                mark(a, smoothTabs[0], s.smooth);
                mark(a, smoothTabs[1], !s.smooth);
            }
            ext2.setText(s.out.ext);
            if (!s.userNamed) {
                String suffix = mode.equals("resize") ? "_" + s.w + "x" + s.h : mode.equals("convert") ? "_convertida" : "_sin-datos";
                name.setText(base + suffix);
            }
            StringBuilder n = new StringBuilder();
            n.append(L.t("Se guarda una copia nueva; la imagen original no se toca. Al guardar se quitan los datos ocultos (ubicación, cámara, fecha de la foto)."));
            boolean ok = true;
            if (s.w <= 0 || s.h <= 0) {
                ok = false;
            } else if ((long) s.w * s.h > 16_000_000L) {
                n.append("\n⚠ ").append(L.t("Ese tamaño es enorme y puede quedarse sin memoria. Prueba con algo menor."));
                ok = false;
            }
            if (s.out == ImageTools.Out.ICO && (s.w > 256 || s.h > 256)) {
                n.append("\n⚠ ").append(L.t("Un ícono .ico no puede pasar de 256×256."));
                ok = false;
            }
            if (s.out == ImageTools.Out.JPG || s.out == ImageTools.Out.BMP) {
                n.append("\nℹ ").append(L.t("Este formato no guarda transparencia: lo transparente quedará en blanco."));
            }
            if (resizeUi && s.fit == ImageTools.Fit.FIT && (long) s.w * s.oh != (long) s.h * s.ow) {
                n.append("\nℹ ").append(L.t("Con «Ajustar» la imagen entera cabe y sobran bordes."));
            }
            if (resizeUi && s.fit == ImageTools.Fit.COVER && (long) s.w * s.oh != (long) s.h * s.ow) {
                n.append("\nℹ ").append(L.t("Con «Recortar» se pierde lo que sobra en los bordes."));
            }
            note.setText(n.toString());
            note.setTextColor(ok ? Ui.SUB : Ui.WARN);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(ok);
        };
        refresh[0].run();

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            if (nm.toLowerCase(Locale.ROOT).endsWith(s.out.ext)) nm = nm.substring(0, nm.length() - s.out.ext.length());
            if (!Paths.validName(nm + s.out.ext)) {
                name.setError(L.t("Nombre no válido"));
                return;
            }
            final String file = nm + s.out.ext;
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setText(L.t("Guardando…"));
            io.execute(() -> {
                String msg;
                boolean ok = true;
                try {
                    Bitmap result = src;
                    if (resizeUi && (s.w != s.ow || s.h != s.oh)) {
                        int bg = s.fit == ImageTools.Fit.FIT && !s.out.alpha() ? Color.WHITE : Color.TRANSPARENT;
                        result = ImageTools.resize(src, s.w, s.h, s.fit, s.smooth, bg);
                    }
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    ImageTools.encode(result, s.out, 92, o);
                    msg = saver.save(file, o.toByteArray());
                } catch (Throwable t) {
                    ok = false;
                    msg = t instanceof OutOfMemoryError ? L.t("Faltó memoria. Prueba con un tamaño menor.") : (t.getMessage() == null ? t.toString() : t.getMessage());
                }
                String finalMsg = msg;
                boolean fo = ok;
                main.post(() -> {
                    if (fo) {
                        d.dismiss();
                        new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog).setTitle(L.t("Listo ✅")).setMessage(finalMsg)
                                .setPositiveButton(L.t("Entendido"), null)
                                .setNegativeButton(L.t("Abrir carpeta"), (x, w) -> {
                                    if (a instanceof MainActivity m) m.openToolFolder();
                                }).show();
                    } else {
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setText(L.t("Guardar copia"));
                        info.setTextColor(Ui.BAD);
                        info.setText(finalMsg);
                    }
                });
            });
        });
    }

    // ------------------------------------------------------------------ piezas

    private static int parse(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static EditText numField(Activity a, String v) {
        EditText e = new EditText(a);
        e.setText(v);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setGravity(Gravity.CENTER);
        return e;
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
