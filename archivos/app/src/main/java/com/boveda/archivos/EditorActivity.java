package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Editor de texto y código: TXT, Markdown, JSON, XML, HTML, CSS, JavaScript, Java, Kotlin,
 * Python, C/C++, configuraciones de Minecraft (.json, .mcmeta, .lang, .properties)… Funciona en
 * cualquier carpeta que Bóveda pueda abrir (también Android/data, USB y FTP).
 */
public class EditorActivity extends Activity {
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private String path;
    private EditText edit;
    private TextView title, status;
    private Charset charset = StandardCharsets.UTF_8;
    private String saved = "";
    private boolean loaded, wrap;
    private String lineSep = "\n";
    private final Handler main = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(Ui.BG);
        col.setFitsSystemWindows(true);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(4), dp(6), dp(6), dp(6));
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> onBackPressed()));
        LinearLayout tt = Ui.column(this);
        title = Ui.text(this, Paths.name(path), 16, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        status = Ui.text(this, "Abriendo…", 11.5f, Ui.SUB, false);
        tt.addView(title);
        tt.addView(status);
        bar.addView(tt, Ui.weight(1));
        bar.addView(Ui.iconButton(this, R.drawable.ic_search, 44, v -> find()));
        bar.addView(Ui.iconButton(this, R.drawable.ic_menu, 44, v -> menu()));
        TextView save = Ui.text(this, "Guardar", 14, 0xFF06101F, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(14), dp(8), dp(14), dp(8));
        save.setBackground(Ui.ripple(Ui.gradient(dp(18), Ui.ACCENT, Ui.ACCENT2), Ui.round(0xFFFFFFFF, dp(18))));
        save.setOnClickListener(v -> save(null));
        bar.addView(save);
        col.addView(bar);

        FrameLayout body = new FrameLayout(this);
        edit = new EditText(this);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(13.5f);
        edit.setTextColor(Ui.TEXT);
        edit.setHighlightColor(0x663D8BFF);
        edit.setBackground(null);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setPadding(dp(12), dp(8), dp(12), dp(40));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        edit.setHorizontallyScrolling(true);
        edit.setEnabled(false);
        body.addView(edit, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar spin = new ProgressBar(this);
        body.addView(spin, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);

        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable s) {
                if (loaded) refreshStatus();
            }
        });
        edit.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                super.sendAccessibilityEvent(host, eventType);
                if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && loaded) refreshStatus();
            }
        });

        new Thread(() -> {
            String text = null, error = null;
            try {
                Fs fs = FsRouter.get(this, path);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = fs.in(path)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > MAX_BYTES) throw new Exception("El archivo pasa de 6 MB: es muy grande para editarlo en el celular");
                    }
                }
                byte[] data = bo.toByteArray();
                for (int i = 0; i < Math.min(data.length, 8192); i++) {
                    if (data[i] == 0) throw new Exception("No es un archivo de texto (tiene datos binarios)");
                }
                text = decode(data);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String t = text, err = error;
            main.post(() -> {
                body.removeView(spin);
                if (err != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle("No se pudo abrir")
                            .setMessage(err)
                            .setPositiveButton("Abrir con otra app", (d, w) -> {
                                Viewers.openWithOther(this, path, "text/plain");
                                finish();
                            })
                            .setNegativeButton("Cerrar", (d, w) -> finish())
                            .setOnCancelListener(d -> finish())
                            .show();
                    return;
                }
                if (t.contains("\r\n")) lineSep = "\r\n";
                String normalized = t.replace("\r\n", "\n");
                saved = normalized;
                edit.setText(normalized);
                edit.setEnabled(true);
                loaded = true;
                refreshStatus();
            });
        }, "abrir-texto").start();
    }

    /** UTF-8 si se puede; si no, Latin-1 (archivos viejos de Windows). Se guarda igual que llegó. */
    private String decode(byte[] data) {
        int off = data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, off, data.length - off)).toString();
        } catch (CharacterCodingException e) {
            charset = StandardCharsets.ISO_8859_1;
            return new String(data, StandardCharsets.ISO_8859_1);
        }
    }

    private boolean modified() {
        return loaded && !edit.getText().toString().equals(saved);
    }

    private void refreshStatus() {
        String s = edit.getText().toString();
        int pos = Math.max(0, edit.getSelectionStart());
        int line = 1, colN = 1;
        for (int i = 0; i < Math.min(pos, s.length()); i++) {
            if (s.charAt(i) == '\n') {
                line++;
                colN = 1;
            } else colN++;
        }
        int lines = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') lines++;
        status.setText((modified() ? "● Sin guardar  ·  " : "") + "Línea " + line + ", col " + colN + "  ·  " + lines + " líneas  ·  "
                + (charset == StandardCharsets.UTF_8 ? "UTF-8" : "Latin-1") + (lineSep.equals("\r\n") ? " (Windows)" : ""));
        title.setText((modified() ? "● " : "") + Paths.name(path));
    }

    private void save(Runnable after) {
        if (!loaded) return;
        String text = edit.getText().toString();
        String out = lineSep.equals("\n") ? text : text.replace("\n", lineSep);
        status.setText("Guardando…");
        new Thread(() -> {
            String error = null;
            try {
                Fs fs = FsRouter.get(this, path);
                try (OutputStream o = fs.out(path)) {
                    o.write(out.getBytes(charset));
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String err = error;
            main.post(() -> {
                if (err != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle("No se pudo guardar")
                            .setMessage(err)
                            .setPositiveButton("Entendido", null).show();
                    refreshStatus();
                    return;
                }
                saved = text;
                refreshStatus();
                Toast.makeText(this, "Guardado ✓", Toast.LENGTH_SHORT).show();
                if (after != null) after.run();
            });
        }, "guardar-texto").start();
    }

    private String lastFind = "";

    private void find() {
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setText(lastFind);
        q.setSelectAllOnFocus(true);
        q.setHint("Buscar…");
        q.setPadding(dp(20), dp(12), dp(20), dp(12));
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Buscar en el archivo")
                .setView(q)
                .setPositiveButton("Siguiente", (d, w) -> {
                    lastFind = q.getText().toString();
                    findNext();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void findNext() {
        if (lastFind.isEmpty()) return;
        String s = edit.getText().toString().toLowerCase(Locale.ROOT);
        String f = lastFind.toLowerCase(Locale.ROOT);
        int from = Math.max(0, edit.getSelectionEnd());
        int i = s.indexOf(f, from);
        if (i < 0) i = s.indexOf(f);
        if (i < 0) {
            Toast.makeText(this, "No aparece «" + lastFind + "»", Toast.LENGTH_SHORT).show();
            return;
        }
        edit.requestFocus();
        edit.setSelection(i, i + f.length());
    }

    private void menu() {
        String[] items = {
                "Buscar el siguiente «" + (lastFind.isEmpty() ? "…" : lastFind) + "»",
                wrap ? "No ajustar las líneas largas" : "Ajustar las líneas largas a la pantalla",
                "Ir al principio",
                "Ir al final",
                "Abrir con otra app",
        };
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0 -> findNext();
                        case 1 -> {
                            wrap = !wrap;
                            edit.setHorizontallyScrolling(!wrap);
                        }
                        case 2 -> edit.setSelection(0);
                        case 3 -> edit.setSelection(edit.length());
                        default -> Viewers.openWithOther(this, path, FileTypes.mime(Paths.name(path)));
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!modified()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("¿Guardar los cambios?")
                .setMessage("«" + Paths.name(path) + "» tiene cambios sin guardar.")
                .setPositiveButton("Guardar", (d, w) -> save(this::finish))
                .setNeutralButton("Cancelar", null)
                .setNegativeButton("Descartar", (d, w) -> finish())
                .show();
    }
}
