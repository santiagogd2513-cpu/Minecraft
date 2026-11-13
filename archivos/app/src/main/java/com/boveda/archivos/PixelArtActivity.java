package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creador de pixel art: lienzo de hasta 512×512, herramientas, paleta, espejo, vista en mosaico y exportar PNG ampliado. */
public class PixelArtActivity extends Activity {
    private PixelView pv;
    private TextView undoB, redoB, info;
    private final List<TextView> toolBtns = new ArrayList<>();
    private final PixelView.Tool[] tools = {PixelView.Tool.PENCIL, PixelView.Tool.ERASER, PixelView.Tool.FILL, PixelView.Tool.PICKER,
            PixelView.Tool.LINE, PixelView.Tool.RECT, PixelView.Tool.RECT_FILL, PixelView.Tool.CIRCLE, PixelView.Tool.CIRCLE_FILL};
    private final String[] toolIcons = {"✏️", "🧽", "🪣", "💧", "📏", "▭", "▮", "◯", "⬤"};
    private LinearLayout palette;
    private View colorChip;
    private final List<Integer> colors = new ArrayList<>();
    private String dir, name = "pixel-art";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView gridB, mxB, myB, prB;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dir = getIntent().getStringExtra("dir");
        if (dir == null) dir = Paths.ROOT + "/Pictures/Bóveda";
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        int[] base = {0xFF000000, 0xFFFFFFFF, 0xFF7F7F7F, 0xFFC3C3C3, 0xFFB97A57, 0xFFED1C24, 0xFFFF7F27, 0xFFFFF200, 0xFF22B14C, 0xFF00A2E8, 0xFF3F48CC, 0xFFA349A4,
                0xFFFFAEC9, 0xFF880015, 0xFF7092BE, 0xFF99D9EA};
        for (int c : base) colors.add(c);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        // barra superior
        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.addView(btn("←", v -> askExit()));
        info = Ui.text(this, "", 13, Ui.SUB, false);
        top.addView(info, Ui.weight(1));
        undoB = btn("↩", v -> pv.undo());
        redoB = btn("↪", v -> pv.redo());
        top.addView(undoB);
        top.addView(redoB);
        top.addView(btn("🔲", v -> tilePreview()));
        top.addView(btn("🗑", v -> new AlertDialog.Builder(this).setMessage(L.t("¿Borrar todo el lienzo? Se puede deshacer."))
                .setPositiveButton(L.t("Borrar"), (d, w) -> pv.clear()).setNegativeButton(L.t("Cancelar"), null).show()));
        top.addView(btn("💾", v -> saveDialog()));
        root.addView(top);

        pv = new PixelView(this);
        pv.listener = new PixelView.Listener() {
            @Override
            public void onColorPicked(int c) {
                addColor(c);
                refreshChip();
            }

            @Override
            public void onChanged() {
                refresh();
            }
        };
        root.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // herramientas
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tl = Ui.row(this);
        tl.setPadding(dp(6), dp(4), dp(6), dp(2));
        for (int i = 0; i < tools.length; i++) {
            final int k = i;
            TextView t = btn(toolIcons[i], v -> {
                pv.setTool(tools[k]);
                refresh();
            });
            toolBtns.add(t);
            tl.addView(t);
        }
        tl.addView(sep());
        gridB = btn("▦", v -> {
            pv.grid = !pv.grid;
            pv.invalidate();
            refresh();
        });
        mxB = btn("⇋", v -> {
            pv.mirrorX = !pv.mirrorX;
            pv.invalidate();
            refresh();
        });
        myB = btn("⇅", v -> {
            pv.mirrorY = !pv.mirrorY;
            pv.invalidate();
            refresh();
        });
        prB = btn("🎯", v -> {
            pv.precision = !pv.precision;
            Toast.makeText(this, pv.precision ? L.t("Precisión: el punto se pinta arriba de tu dedo") : L.t("Precisión apagada"), Toast.LENGTH_SHORT).show();
            refresh();
        });
        tl.addView(gridB);
        tl.addView(mxB);
        tl.addView(myB);
        tl.addView(prB);
        tl.addView(btn("🔍", v -> pv.fit()));
        tl.addView(btn("●1", v -> brushDialog(v)));
        hs.addView(tl);
        root.addView(hs);

        // paleta
        LinearLayout pr = Ui.row(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setPadding(dp(6), dp(2), dp(6), dp(8));
        colorChip = new View(this);
        colorChip.setOnClickListener(v -> colorDialog());
        pr.addView(colorChip, new LinearLayout.LayoutParams(dp(44), dp(44)));
        HorizontalScrollView ps = new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        palette = Ui.row(this);
        ps.addView(palette);
        pr.addView(ps, Ui.weight(1));
        root.addView(pr);
        setContentView(root);
        buildPalette();

        String src = getIntent().getStringExtra("src");
        if (src != null) {
            openSource(src);
        } else {
            newDialog();
        }
        refresh();
    }

    private View sep() {
        View v = new View(this);
        v.setBackgroundColor(Ui.STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(28));
        lp.setMargins(dp(4), dp(8), dp(4), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView btn(String s, View.OnClickListener l) {
        TextView t = Ui.text(this, s, 18, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44));
        t.setMinHeight(dp(44));
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setOnClickListener(l);
        t.setBackground(Ui.rippleRound(Color.TRANSPARENT, dp(10)));
        return t;
    }

    private void tint(TextView t, boolean on) {
        t.setBackground(on ? Ui.round(Ui.SURFACE3, dp(10)) : Ui.rippleRound(Color.TRANSPARENT, dp(10)));
    }

    private void refresh() {
        for (int i = 0; i < tools.length; i++) tint(toolBtns.get(i), pv.tool == tools[i]);
        tint(gridB, pv.grid);
        tint(mxB, pv.mirrorX);
        tint(myB, pv.mirrorY);
        tint(prB, pv.precision);
        undoB.setAlpha(pv.canUndo() ? 1f : 0.35f);
        redoB.setAlpha(pv.canRedo() ? 1f : 0.35f);
        info.setText(pv.w + "×" + pv.h + (pv.dirty ? " •" : ""));
        refreshChip();
    }

    private void refreshChip() {
        colorChip.setBackground(Ui.roundStroke(pv.color, dp(10), dp(2), Color.WHITE));
    }

    private void addColor(int c) {
        c |= 0; 
        colors.remove((Integer) c);
        colors.add(0, c);
        while (colors.size() > 40) colors.remove(colors.size() - 1);
        buildPalette();
    }

    private void buildPalette() {
        palette.removeAllViews();
        for (int c : colors) {
            View v = new View(this);
            v.setBackground(Ui.roundStroke(c, dp(6), dp(1), Ui.STROKE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(dp(3), 0, dp(3), 0);
            v.setLayoutParams(lp);
            final int cc = c;
            v.setOnClickListener(x -> {
                pv.color = cc;
                if (pv.tool == PixelView.Tool.ERASER || pv.tool == PixelView.Tool.PICKER) pv.setTool(PixelView.Tool.PENCIL);
                refresh();
            });
            palette.addView(v);
        }
    }

    // ------------------------------------------------------------------ cuadros de diálogo

    private void newDialog() {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        final int[] size = {32, 32};
        EditText ew = num("32"), eh = num("32");
        LinearLayout chips = Ui.row(this);
        for (int s : new int[]{16, 32, 64, 128}) {
            TextView t = btn(s + "", v -> {
                ew.setText(String.valueOf(s));
                eh.setText(String.valueOf(s));
            });
            t.setTextSize(15);
            chips.addView(t);
        }
        box.addView(chips);
        LinearLayout r = Ui.row(this);
        r.addView(ew, Ui.weight(1));
        TextView x = Ui.text(this, "  ×  ", 16, Ui.TEXT, false);
        r.addView(x);
        r.addView(eh, Ui.weight(1));
        box.addView(r);
        box.addView(Ui.text(this, L.t("Entre 1 y 512 píxeles de lado"), 12, Ui.SUB, false));
        new AlertDialog.Builder(this).setTitle(L.t("Lienzo nuevo")).setView(box).setCancelable(false)
                .setPositiveButton(L.t("Crear"), (d, w) -> {
                    int a = clamp(ew.getText().toString()), c = clamp(eh.getText().toString());
                    pv.setCanvas(a, c, null);
                    refresh();
                })
                .setNegativeButton(L.t("Salir"), (d, w) -> finish()).show();
    }

    private int clamp(String s) {
        try {
            return Math.max(1, Math.min(512, Integer.parseInt(s.trim())));
        } catch (Exception e) {
            return 32;
        }
    }

    private EditText num(String s) {
        EditText e = new EditText(this);
        e.setText(s);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine();
        return e;
    }

    private void brushDialog(View anchor) {
        String[] o = {"1", "2", "3", "4", "6", "8"};
        new AlertDialog.Builder(this).setTitle(L.t("Grosor del pincel (píxeles)")).setItems(o, (d, i) -> {
            pv.brush = Integer.parseInt(o[i]);
            ((TextView) anchor).setText("●" + pv.brush);
        }).show();
    }

    private void colorDialog() {
        float[] hsv = new float[3];
        Color.colorToHSV(pv.color, hsv);
        final int[] alpha = {Color.alpha(pv.color)};
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        View prev = new View(this);
        box.addView(prev, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText hex = new EditText(this);
        hex.setSingleLine();
        hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        boolean[] lock = {false};
        SeekBar[] bars = new SeekBar[4];
        String[] nm = {"Tono", "Saturación", "Brillo", "Opacidad"};
        int[] max = {360, 100, 100, 255};
        Runnable upd = () -> {
            int c = Color.HSVToColor(alpha[0], hsv);
            prev.setBackground(Ui.roundStroke(c, dp(8), dp(1), Ui.STROKE));
            lock[0] = true;
            hex.setText(String.format("%02X%02X%02X%s", Color.red(c), Color.green(c), Color.blue(c), alpha[0] == 255 ? "" : String.format("%02X", alpha[0])));
            lock[0] = false;
        };
        SeekBar.OnSeekBarChangeListener sl = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (!u) return;
                hsv[0] = bars[0].getProgress();
                hsv[1] = bars[1].getProgress() / 100f;
                hsv[2] = bars[2].getProgress() / 100f;
                alpha[0] = bars[3].getProgress();
                upd.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        };
        for (int i = 0; i < 4; i++) {
            box.addView(Ui.text(this, L.t(nm[i]), 12, Ui.SUB, false));
            bars[i] = new SeekBar(this);
            bars[i].setMax(max[i]);
            bars[i].setOnSeekBarChangeListener(sl);
            box.addView(bars[i]);
        }
        bars[0].setProgress((int) hsv[0]);
        bars[1].setProgress((int) (hsv[1] * 100));
        bars[2].setProgress((int) (hsv[2] * 100));
        bars[3].setProgress(alpha[0]);
        box.addView(Ui.text(this, "Hex (RRGGBB o RRGGBBAA)", 12, Ui.SUB, false));
        box.addView(hex);
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                if (lock[0]) return;
                String t = e.toString().trim();
                if (t.length() != 6 && t.length() != 8) return;
                try {
                    int rgb = (int) Long.parseLong(t.substring(0, 6), 16);
                    int a = t.length() == 8 ? Integer.parseInt(t.substring(6), 16) : 255;
                    Color.colorToHSV(rgb | 0xFF000000, hsv);
                    alpha[0] = a;
                    bars[0].setProgress((int) hsv[0]);
                    bars[1].setProgress((int) (hsv[1] * 100));
                    bars[2].setProgress((int) (hsv[2] * 100));
                    bars[3].setProgress(a);
                    prev.setBackground(Ui.roundStroke(Color.HSVToColor(a, hsv), dp(8), dp(1), Ui.STROKE));
                } catch (Exception ignored) {
                }
            }
        });
        upd.run();
        new AlertDialog.Builder(this).setTitle(L.t("Color")).setView(box)
                .setPositiveButton(L.t("Usar"), (d, w) -> {
                    int c = Color.HSVToColor(alpha[0], hsv);
                    pv.color = c;
                    if (pv.tool == PixelView.Tool.ERASER || pv.tool == PixelView.Tool.PICKER) pv.setTool(PixelView.Tool.PENCIL);
                    addColor(c);
                    refresh();
                }).setNegativeButton(L.t("Cancelar"), null).show();
    }

    /** Muestra el dibujo repetido 3×3 para ver si las orillas encajan (texturas). */
    private void tilePreview() {
        int tw = pv.w * 3, th = pv.h * 3;
        Bitmap one = pv.snapshot();
        int zoom = Math.max(1, 300 / Math.max(tw, th));
        Bitmap big = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(big);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) c.drawBitmap(one, x * pv.w, y * pv.h, null);
        ImageView iv = new ImageView(this);
        BitmapDrawable bd = new BitmapDrawable(getResources(), big);
        bd.setFilterBitmap(false);
        bd.setAntiAlias(false);
        iv.setImageDrawable(bd);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF808080);
        iv.setMinimumHeight(dp(300));
        iv.setAdjustViewBounds(true);
        new AlertDialog.Builder(this).setTitle(L.t("Mosaico 3×3")).setView(iv).setPositiveButton("OK", null).show();
    }

    private void saveDialog() {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        EditText en = new EditText(this);
        en.setSingleLine();
        en.setText(name);
        box.addView(en);
        box.addView(Ui.text(this, L.t("Ampliar (sin borrosidad)"), 12, Ui.SUB, false));
        int[] scales = {1, 2, 4, 8, 16};
        final int[] sel = {1};
        TextView out = Ui.text(this, "", 12, Ui.SUB, false);
        LinearLayout chips = Ui.row(this);
        List<TextView> cs = new ArrayList<>();
        Runnable upd = () -> out.setText(L.t("Tamaño final: ") + (pv.w * sel[0]) + "×" + (pv.h * sel[0]));
        for (int s : scales) {
            TextView t = btn("×" + s, null);
            t.setTextSize(15);
            t.setOnClickListener(v -> {
                sel[0] = s;
                for (TextView o : cs) tint(o, o == t);
                upd.run();
            });
            cs.add(t);
            chips.addView(t);
        }
        tint(cs.get(0), true);
        box.addView(chips);
        box.addView(out);
        upd.run();
        new AlertDialog.Builder(this).setTitle(L.t("Guardar PNG")).setView(box)
                .setPositiveButton(L.t("Guardar"), (d, w) -> {
                    String n = en.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (n.isEmpty()) n = "pixel-art";
                    if (n.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) n = n.substring(0, n.length() - 4);
                    name = n;
                    save(n, sel[0]);
                }).setNegativeButton(L.t("Cancelar"), null).show();
    }

    private void save(String n, int scale) {
        Bitmap src = pv.snapshot();
        io.execute(() -> {
            try {
                Bitmap b = scale == 1 ? src : Bitmap.createScaledBitmap(src, src.getWidth() * scale, src.getHeight() * scale, false);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, bo);
                byte[] bytes = bo.toByteArray();
                if (!FsRouter.hasAccess(this, dir)) throw new java.io.IOException(L.t("No tengo acceso a esa carpeta"));
                Fs f = FsRouter.get(this, dir);
                if (f.stat(dir) == null) new java.io.File(dir).mkdirs();
                String file = n + ".png", target = Paths.join(dir, file);
                for (int i = 1; f.stat(target) != null; i++) {
                    file = n + " (" + i + ").png";
                    target = Paths.join(dir, file);
                }
                try (OutputStream o = f.out(target)) {
                    o.write(bytes);
                }
                UndoLog.recordCreated(this, target, false);
                final String fn = file;
                main.post(() -> {
                    pv.dirty = false;
                    refresh();
                    setResult(RESULT_OK, new Intent().putExtra("dir", dir));
                    Toast.makeText(this, L.t("Guardé «") + fn + L.t("» (") + Ui.size(bytes.length) + ")", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                main.post(() -> Toast.makeText(this, t.getMessage() == null ? t.toString() : t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void openSource(String src) {
        io.execute(() -> {
            try (InputStream in = FsRouter.get(this, src).in(src)) {
                byte[] data = ImageTools.readAll(in, 40L << 20);
                String ext = src.substring(src.lastIndexOf('.') + 1);
                Bitmap bm = ImageTools.load(data, ext);
                if (bm.getWidth() > 512 || bm.getHeight() > 512) {
                    main.post(() -> {
                        Toast.makeText(this, L.t("La imagen es mayor de 512×512; para pixel art redúcela primero con «Cambiar tamaño»."), Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                int w = bm.getWidth(), h = bm.getHeight();
                int[] px = new int[w * h];
                bm.getPixels(px, 0, w, 0, 0, w, h);
                String base = src.substring(src.lastIndexOf('/') + 1);
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                final String nb = base;
                main.post(() -> {
                    name = nb;
                    pv.setCanvas(w, h, px);
                    refresh();
                });
            } catch (Throwable t) {
                main.post(() -> {
                    Toast.makeText(this, t.getMessage() == null ? t.toString() : t.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void askExit() {
        if (!pv.dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this).setMessage(L.t("Tienes cambios sin guardar."))
                .setPositiveButton(L.t("Guardar"), (d, w) -> saveDialog())
                .setNegativeButton(L.t("Salir sin guardar"), (d, w) -> finish())
                .setNeutralButton(L.t("Seguir"), null).show();
    }

    @Override
    public void onBackPressed() {
        askExit();
    }
}
