package com.nocturna.browser;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Pantallas del gestor de contraseñas: guardar, rellenar, la lista de cuentas, importar y exportar. */
final class PasswordUi {
    static final int REQ_IMPORT = 21, REQ_EXPORT = 22;
    private static final long UNLOCK_MS = 3 * 60_000;

    private final MainActivity a;
    private final Passwords pw;
    private long unlockedUntil;
    private LinearLayout fillBar;

    /** Lo que la persona escribió en un inicio de sesión, esperando para ofrecer guardarlo. */
    static final class Pending {
        final String host, user, pass;

        Pending(String host, String user, String pass) {
            this.host = host;
            this.user = user;
            this.pass = pass;
        }
    }

    PasswordUi(MainActivity a) {
        this.a = a;
        this.pw = Passwords.get(a);
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    // ------------------------------------------------------------------ huella

    /** Pide huella (o el PIN/patrón del celular) antes de mostrar o usar contraseñas. */
    void unlock(String why, Runnable ok) {
        if (System.currentTimeMillis() < unlockedUntil) {
            ok.run();
            return;
        }
        KeyguardManager km = a.getSystemService(KeyguardManager.class);
        if (km == null || !km.isDeviceSecure()) {
            // sin bloqueo de pantalla no hay huella que pedir
            Toast.makeText(a, "Consejo: pon un bloqueo de pantalla para proteger tus contraseñas con la huella", Toast.LENGTH_LONG).show();
            ok.run();
            return;
        }
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                .setTitle("Contraseñas de Nocturna")
                .setSubtitle(why);
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            //noinspection deprecation
            b.setDeviceCredentialAllowed(true);
        }
        try {
            b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    unlockedUntil = System.currentTimeMillis() + UNLOCK_MS;
                    ok.run();
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(a, "No se pudo pedir la huella: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Al salir de la app se vuelve a pedir la huella. */
    void lock() {
        unlockedUntil = 0;
    }

    // ------------------------------------------------------------------ lo que avisa la página

    void candidate(MainActivity.Tab t, String user, String pass, boolean submitted, String pageUrl) {
        if (t.incognito || !a.store().pwOffer() || pass == null || pass.isEmpty()) return;
        // el sitio es el de la página que lo envió, siempre que sea la actual o la que se acaba de dejar
        String host = Passwords.host(pageUrl);
        if (host == null || !(host.equals(Passwords.host(t.url)) || host.equals(Passwords.host(t.prevUrl)))) return;
        if (pw.isNever(host)) return;
        user = user == null ? "" : user.trim();
        Passwords.Entry old = pw.find(host, user);
        if (old != null && pass.equals(pw.password(old))) {
            pw.touch(old);
            t.pwPending = null;
            return;
        }
        t.pwPending = new Pending(host, user, pass);
        if (submitted) {
            // si la página no cambia (inicio de sesión sin recargar), se ofrece igual en un momento
            final Pending p = t.pwPending;
            t.web.postDelayed(() -> {
                if (t.pwPending == p) offer(t);
            }, 1500);
        }
    }

    /** La página cambió: si quedó algo escrito en un inicio de sesión, se ofrece guardarlo. */
    void pageStarted(MainActivity.Tab t) {
        if (t == a.currentTab()) hideFillBar();
        if (t.pwPending != null) offer(t);
    }

    void found(MainActivity.Tab t, boolean hasPassword) {
        if (t != a.currentTab()) return;
        String host = Passwords.host(t.url);
        if (host == null) return;
        List<Passwords.Entry> list = pw.forHost(host);
        if (list.isEmpty()) return;
        showFillBar(t, host, list, hasPassword);
    }

    // ------------------------------------------------------------------ ofrecer guardar

    private void offer(MainActivity.Tab t) {
        Pending p = t.pwPending;
        t.pwPending = null;
        if (p == null || t != a.currentTab() || a.isFinishing()) return;
        Passwords.Entry old = pw.find(p.host, p.user);
        boolean update = old != null;

        Dialog d = a.sheet();
        LinearLayout box = a.sheetBox(d);
        LinearLayout head = Ui.row(a);
        ImageView ic = new ImageView(a);
        Favicons.into(a, ic, "https://" + p.host + "/", p.host);
        head.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout tx = Ui.column(a);
        tx.setPadding(dp(14), 0, 0, 0);
        tx.addView(Ui.text(a, update ? "¿Actualizar la contraseña?" : "¿Guardar la contraseña?", 18, Ui.TEXT, true));
        tx.addView(Ui.text(a, p.host, 13, Ui.SUB, false));
        head.addView(tx, Ui.weight(1));
        box.addView(head);

        EditText user = field("Usuario o correo", p.user, false);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.setMargins(0, dp(16), 0, 0);
        box.addView(user, fl);
        EditText pass = field("Contraseña", p.pass, true);
        box.addView(passRow(pass), fl);

        TextView save = primary(update ? "Actualizar" : "Guardar");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(0, dp(18), 0, 0);
        box.addView(save, sl);
        save.setOnClickListener(v -> {
            String u = user.getText().toString().trim(), pa = pass.getText().toString();
            if (pa.isEmpty()) {
                pass.setError("Falta la contraseña");
                return;
            }
            try {
                pw.save(p.host, u, pa);
                d.dismiss();
                Toast.makeText(a, update ? "Contraseña actualizada" : "Contraseña guardada 🔑", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(a, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout row = Ui.row(a);
        row.setPadding(0, dp(8), 0, 0);
        TextView later = secondary("Ahora no");
        later.setOnClickListener(v -> d.dismiss());
        TextView never = secondary("Nunca en este sitio");
        never.setOnClickListener(v -> {
            pw.setNever(p.host, true);
            d.dismiss();
            Toast.makeText(a, "No volveré a preguntar en " + p.host, Toast.LENGTH_SHORT).show();
        });
        row.addView(later, Ui.weight(1));
        row.addView(never, Ui.weight(1));
        box.addView(row);
        d.show();
    }

    // ------------------------------------------------------------------ barra para rellenar

    void hideFillBar() {
        if (fillBar != null && fillBar.getParent() != null) ((FrameLayout) fillBar.getParent()).removeView(fillBar);
        fillBar = null;
    }

    private void showFillBar(MainActivity.Tab t, String host, List<Passwords.Entry> list, boolean hasPassword) {
        hideFillBar();
        FrameLayout root = a.findViewById(android.R.id.content);
        LinearLayout bar = Ui.row(a);
        bar.setPadding(dp(14), dp(10), dp(6), dp(10));
        bar.setBackground(Ui.roundStroke(Ui.SURFACE3, dp(18), dp(1), 0xFF33405A));
        bar.setElevation(dp(8));
        ImageView key = new ImageView(a);
        key.setImageDrawable(Ui.icon(a, R.drawable.ic_key, Ui.WARN));
        bar.addView(key, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout tx = Ui.column(a);
        tx.setPadding(dp(12), 0, dp(6), 0);
        Passwords.Entry first = list.get(0);
        TextView t1 = Ui.text(a, (hasPassword ? "Iniciar sesión en " : "Tu cuenta de ") + Passwords.base(host), 14, Ui.TEXT, true);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        String who = list.size() == 1 ? (first.user.isEmpty() ? "(sin usuario)" : first.user) : list.size() + " cuentas guardadas";
        TextView t2 = Ui.text(a, who, 12.5f, Ui.SUB, false);
        t2.setSingleLine(true);
        t2.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tx.addView(t1);
        tx.addView(t2);
        bar.addView(tx, Ui.weight(1));
        TextView go = Ui.text(a, "Rellenar", 14, 0xFF06101F, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(14), dp(8), dp(14), dp(8));
        go.setBackground(Ui.ripple(Ui.gradient(dp(18), Ui.ACCENT, Ui.ACCENT2), Ui.round(Color.WHITE, dp(18))));
        bar.addView(go);
        bar.addView(Ui.iconButton(a, R.drawable.ic_close, 40, v -> hideFillBar()));
        go.setOnClickListener(v -> {
            if (list.size() == 1) fill(t, first);
            else pickAccount(t, list);
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(10), 0, dp(10), dp(60));
        root.addView(bar, lp);
        bar.setAlpha(0);
        bar.setTranslationY(dp(16));
        bar.animate().alpha(1).translationY(0).setDuration(180).start();
        fillBar = bar;
    }

    private void pickAccount(MainActivity.Tab t, List<Passwords.Entry> list) {
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            Passwords.Entry e = list.get(i);
            names[i] = (e.user.isEmpty() ? "(sin usuario)" : e.user) + "   ·   " + e.host;
        }
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("¿Con qué cuenta?")
                .setItems(names, (d, w) -> fill(t, list.get(w)))
                .show();
    }

    private void fill(MainActivity.Tab t, Passwords.Entry e) {
        unlock("Rellenar tu cuenta de " + Passwords.base(e.host), () -> {
            // la página pudo cambiar mientras se ponía la huella
            String host = Passwords.host(t.url);
            if (host == null || !Passwords.base(host).equals(Passwords.base(e.host))) return;
            String pass = pw.password(e);
            if (pass == null) {
                Toast.makeText(a, "No se pudo leer esta contraseña (¿restauraste el celular?). Edítala en Contraseñas.", Toast.LENGTH_LONG).show();
                return;
            }
            pw.touch(e);
            t.web.evaluateJavascript("window.__noctPw&&window.__noctPw.fill(" + DarkMode.jsString(e.user) + ","
                    + DarkMode.jsString(pass) + ")", r -> {
                if (r != null && r.contains("u")) {
                    Toast.makeText(a, "Usuario puesto. En el siguiente paso toca Rellenar otra vez.", Toast.LENGTH_SHORT).show();
                }
            });
            hideFillBar();
        });
    }

    // ------------------------------------------------------------------ lista de contraseñas

    void showManager() {
        unlock("Ver tus contraseñas guardadas", this::manager);
    }

    private void manager() {
        Dialog d = a.sheet();
        LinearLayout box = a.sheetBox(d);
        List<Passwords.Entry> all = pw.all();
        LinearLayout head = Ui.row(a);
        head.addView(Ui.text(a, "Contraseñas" + (all.isEmpty() ? "" : "  ·  " + all.size()), 20, Ui.TEXT, true), Ui.weight(1));
        head.addView(Ui.iconButton(a, R.drawable.ic_add, 40, v -> {
            d.dismiss();
            edit(null);
        }));
        box.addView(head);
        TextView note = Ui.text(a, "Cifradas con la llave de seguridad de este celular. Nocturna no las envía a ningún lado.", 12, Ui.SUB, false);
        note.setPadding(0, dp(2), 0, dp(10));
        box.addView(note);

        EditText search = null;
        if (all.size() > 6) {
            search = new EditText(a);
            search.setHint("Buscar sitio o usuario");
            search.setHintTextColor(0xFF5D6B83);
            search.setTextColor(Ui.TEXT);
            search.setTextSize(14.5f);
            search.setSingleLine(true);
            search.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE));
            search.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.setMargins(0, 0, 0, dp(8));
            box.addView(search, sl);
        }

        ScrollView sv = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        sv.addView(col);
        final EditText fSearch = search;
        Runnable render = () -> {
            col.removeAllViews();
            String q = fSearch == null ? "" : fSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (Passwords.Entry e : all) {
                if (!q.isEmpty() && !e.host.contains(q) && !e.user.toLowerCase(Locale.ROOT).contains(q)) continue;
                col.addView(entryRow(e, d));
                shown++;
            }
            if (all.isEmpty()) {
                TextView empty = Ui.text(a, "Aún no tienes contraseñas guardadas.\n\nCuando inicies sesión en una página, Nocturna te ofrecerá guardarla. "
                        + "También puedes traer las que tienes en Google con «Importar».", 14, Ui.SUB, false);
                empty.setPadding(dp(4), dp(8), dp(4), dp(8));
                col.addView(empty);
            } else if (shown == 0) {
                col.addView(Ui.text(a, "Nada coincide con «" + q + "»", 14, Ui.SUB, false));
            }
        };
        render.run();
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            public void onTextChanged(CharSequence s, int st, int b, int c) { }
            public void afterTextChanged(Editable s) { render.run(); }
        });
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f);
        box.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(maxH, dp(64) * Math.max(2, all.size()) + dp(8))));

        LinearLayout actions = Ui.row(a);
        actions.setPadding(0, dp(12), 0, 0);
        TextView imp = secondary("Importar");
        imp.setOnClickListener(v -> {
            d.dismiss();
            askImport();
        });
        TextView exp = secondary("Exportar");
        exp.setOnClickListener(v -> {
            d.dismiss();
            askExport();
        });
        actions.addView(imp, Ui.weight(1));
        actions.addView(exp, Ui.weight(1));
        List<String> never = pw.neverList();
        if (!never.isEmpty()) {
            TextView nv = secondary("Bloqueados (" + never.size() + ")");
            nv.setOnClickListener(v -> {
                d.dismiss();
                showNever();
            });
            actions.addView(nv, Ui.weight(1));
        }
        box.addView(actions);
        d.show();
    }

    private View entryRow(Passwords.Entry e, Dialog parent) {
        LinearLayout r = Ui.row(a);
        r.setPadding(dp(8), dp(9), dp(4), dp(9));
        r.setBackground(Ui.rippleRound(0x00000000, dp(14)));
        ImageView ic = new ImageView(a);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Favicons.into(a, ic, "https://" + e.host + "/", e.host);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(32), dp(32));
        il.setMargins(0, 0, dp(14), 0);
        r.addView(ic, il);
        LinearLayout texts = Ui.column(a);
        TextView t = Ui.text(a, e.host, 14.5f, Ui.TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        TextView u = Ui.text(a, e.user.isEmpty() ? "(sin usuario)" : e.user, 12, Ui.SUB, false);
        u.setSingleLine(true);
        u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(t);
        texts.addView(u);
        r.addView(texts, Ui.weight(1));
        r.addView(Ui.iconButton(a, R.drawable.ic_copy, 40, v -> copyPassword(e)));
        r.setOnClickListener(v -> {
            parent.dismiss();
            detail(e);
        });
        return r;
    }

    private void detail(Passwords.Entry e) {
        Dialog d = a.sheet();
        LinearLayout box = a.sheetBox(d);
        LinearLayout head = Ui.row(a);
        ImageView ic = new ImageView(a);
        Favicons.into(a, ic, "https://" + e.host + "/", e.host);
        head.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = Ui.column(a);
        tx.setPadding(dp(14), 0, 0, 0);
        TextView h = Ui.text(a, e.host, 18, Ui.TEXT, true);
        h.setSingleLine(true);
        h.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tx.addView(h);
        long when = Math.max(e.used, e.created);
        if (when > 0) tx.addView(Ui.text(a, "Usada " + DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString().toLowerCase(Locale.ROOT), 12.5f, Ui.SUB, false));
        head.addView(tx, Ui.weight(1));
        box.addView(head);

        box.addView(infoRow("Usuario", e.user.isEmpty() ? "(sin usuario)" : e.user, false, () -> copyPlain(e.user, "Usuario copiado")));
        String pass = pw.password(e);
        box.addView(infoRow("Contraseña", pass == null ? "No se puede leer: edítala" : pass, pass != null, () -> copyPassword(e)));

        TextView open = primary("Abrir " + e.host);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
        ol.setMargins(0, dp(18), 0, 0);
        box.addView(open, ol);
        open.setOnClickListener(v -> {
            d.dismiss();
            a.openInCurrent("https://" + e.host + "/");
        });
        LinearLayout row = Ui.row(a);
        row.setPadding(0, dp(8), 0, 0);
        TextView ed = secondary("Editar");
        ed.setOnClickListener(v -> {
            d.dismiss();
            edit(e);
        });
        TextView del = secondary("Borrar");
        del.setTextColor(Ui.BAD);
        del.setOnClickListener(v -> new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("¿Borrar esta contraseña?")
                .setMessage(e.host + "\n" + e.user)
                .setPositiveButton("Borrar", (x, y) -> {
                    pw.remove(e);
                    d.dismiss();
                    Toast.makeText(a, "Borrada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null).show());
        row.addView(ed, Ui.weight(1));
        row.addView(del, Ui.weight(1));
        box.addView(row);
        d.show();
    }

    private View infoRow(String label, String value, boolean secret, Runnable copy) {
        LinearLayout r = Ui.row(a);
        r.setPadding(dp(14), dp(10), dp(4), dp(10));
        r.setBackground(Ui.round(Ui.SURFACE2, dp(14)));
        LinearLayout tx = Ui.column(a);
        tx.addView(Ui.text(a, label, 12, Ui.SUB, false));
        TextView v = Ui.text(a, secret ? mask(value) : value, 15, Ui.TEXT, true);
        v.setTextIsSelectable(!secret);
        tx.addView(v);
        r.addView(tx, Ui.weight(1));
        if (secret) {
            boolean[] shown = {false};
            r.addView(Ui.iconButton(a, R.drawable.ic_eye, 40, x -> {
                shown[0] = !shown[0];
                v.setText(shown[0] ? value : mask(value));
            }));
        }
        r.addView(Ui.iconButton(a, R.drawable.ic_copy, 40, x -> copy.run()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        r.setLayoutParams(lp);
        return r;
    }

    private static String mask(String s) {
        return "•".repeat(Math.min(14, Math.max(8, s.length())));
    }

    private void edit(Passwords.Entry e) {
        LinearLayout box = Ui.column(a);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        EditText site = field("Sitio (ej. github.com)", e == null ? "" : e.host, false);
        EditText user = field("Usuario o correo", e == null ? "" : e.user, false);
        String old = e == null ? "" : pw.password(e);
        EditText pass = field("Contraseña", old == null ? "" : old, true);
        box.addView(site);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.setMargins(0, dp(10), 0, 0);
        box.addView(user, fl);
        box.addView(passRow(pass), fl);
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle(e == null ? "Añadir contraseña" : "Editar contraseña")
                .setView(box)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = site.getText().toString().trim();
            String host = Passwords.host(s.contains("://") ? s : "https://" + s);
            if (host == null) {
                site.setError("Escribe el sitio, por ejemplo github.com");
                return;
            }
            if (pass.getText().length() == 0) {
                pass.setError("Falta la contraseña");
                return;
            }
            try {
                if (e == null) pw.save(host, user.getText().toString().trim(), pass.getText().toString());
                else pw.update(e, host, user.getText().toString().trim(), pass.getText().toString());
                d.dismiss();
                manager();
            } catch (Exception ex) {
                Toast.makeText(a, "No se pudo guardar: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        d.show();
    }

    private void showNever() {
        List<String> never = pw.neverList();
        boolean[] checked = new boolean[never.size()];
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("Sitios donde no se ofrece guardar (marca para volver a ofrecer)")
                .setMultiChoiceItems(never.toArray(new String[0]), checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Listo", (d, w) -> {
                    for (int i = 0; i < checked.length; i++) if (checked[i]) pw.setNever(never.get(i), false);
                    manager();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ copiar

    private void copyPassword(Passwords.Entry e) {
        String p = pw.password(e);
        if (p == null) {
            Toast.makeText(a, "No se pudo leer esta contraseña", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = ClipData.newPlainText("contraseña", p);
        // Android no la muestra en la vista previa del portapapeles
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        a.getSystemService(ClipboardManager.class).setPrimaryClip(clip);
        Toast.makeText(a, "Contraseña copiada", Toast.LENGTH_SHORT).show();
    }

    private void copyPlain(String s, String msg) {
        a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("usuario", s));
        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ importar / exportar

    private void askImport() {
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("Importar contraseñas")
                .setMessage("Elige el archivo .csv que exportaste de otro gestor.\n\n"
                        + "• Gestor de contraseñas de Google (el de Ajustes del celular o Chrome): ábrelo → ⚙️ Ajustes → «Exportar contraseñas». "
                        + "Se guarda un archivo .csv en Descargas.\n"
                        + "• Firefox, Edge, Brave, Bitwarden: también sirven sus .csv.\n\n"
                        + "Después de importar, borra ese archivo: guarda las contraseñas sin cifrar.")
                .setPositiveButton("Elegir archivo", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "text/plain",
                            "application/csv", "application/vnd.ms-excel", "application/octet-stream"});
                    try {
                        a.startActivityForResult(i, REQ_IMPORT);
                    } catch (Exception e) {
                        Toast.makeText(a, "No hay selector de archivos", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askExport() {
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("Exportar contraseñas")
                .setMessage("Se crea un archivo .csv con todas tus contraseñas SIN cifrar, para llevarlas a otro gestor o tener una copia. "
                        + "Cualquiera que tenga ese archivo puede leerlas: guárdalo en un lugar seguro y bórralo cuando ya no lo necesites.")
                .setPositiveButton("Exportar", (d, w) -> unlock("Exportar tus contraseñas", () -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("text/csv");
                    i.putExtra(Intent.EXTRA_TITLE, "contraseñas-nocturna.csv");
                    try {
                        a.startActivityForResult(i, REQ_EXPORT);
                    } catch (Exception e) {
                        Toast.makeText(a, "No hay selector de archivos", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void onResult(int req, int res, Intent data) {
        if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_IMPORT) {
            new Thread(() -> {
                String msg;
                try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > 20 * 1024 * 1024) throw new Exception("El archivo es demasiado grande");
                    }
                    int[] r = pw.importCsv(bo.toString(StandardCharsets.UTF_8.name()));
                    msg = r[0] + " nuevas, " + r[1] + " actualizadas" + (r[2] > 0 ? ", " + r[2] + " omitidas (repetidas o de apps)" : "") + ".";
                } catch (Exception e) {
                    msg = "No se pudo importar: " + e.getMessage();
                }
                String m = msg;
                a.runOnUiThread(() -> new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                        .setTitle("Importar contraseñas")
                        .setMessage(m + "\n\nRecuerda borrar el archivo .csv: tiene las contraseñas sin cifrar.")
                        .setPositiveButton("Ver contraseñas", (d, w) -> manager())
                        .setNegativeButton("Cerrar", null)
                        .show());
            }).start();
        } else if (req == REQ_EXPORT) {
            new Thread(() -> {
                String msg;
                try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
                    out.write(pw.exportCsv().getBytes(StandardCharsets.UTF_8));
                    msg = "Listo: " + pw.size() + " contraseñas exportadas.";
                } catch (Exception e) {
                    msg = "No se pudo exportar: " + e.getMessage();
                }
                String m = msg;
                a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
            }).start();
        }
    }

    // ------------------------------------------------------------------ piezas

    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value);
        e.setHintTextColor(0xFF5D6B83);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    /** Campo de contraseña con el ojito para verla. */
    private View passRow(EditText pass) {
        FrameLayout f = new FrameLayout(a);
        f.addView(pass, new FrameLayout.LayoutParams(-1, -2));
        pass.setPadding(dp(14), dp(12), dp(52), dp(12));
        boolean[] shown = {false};
        ImageView eye = Ui.iconButton(a, R.drawable.ic_eye, 44, v -> {
            shown[0] = !shown[0];
            int sel = pass.getSelectionEnd();
            pass.setInputType(InputType.TYPE_CLASS_TEXT | (shown[0] ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD) | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            pass.setSelection(Math.max(0, Math.min(sel, pass.length())));
        });
        f.addView(eye, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        return f;
    }

    private TextView primary(String s) {
        TextView t = Ui.text(a, s, 16, 0xFF06101F, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        t.setBackground(Ui.ripple(Ui.gradient(dp(24), Ui.ACCENT, Ui.ACCENT2), Ui.round(Color.WHITE, dp(24))));
        return t;
    }

    private TextView secondary(String s) {
        TextView t = Ui.text(a, s, 14.5f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(12), dp(6), dp(12));
        t.setBackground(Ui.rippleRound(0x00000000, dp(18)));
        return t;
    }
}
