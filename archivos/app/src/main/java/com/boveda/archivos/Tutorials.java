package com.boveda.archivos;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tutoriales y aprendizaje: películas cortas dibujadas con código, sin sonido ni videos. Un celular de mentira con
 * Bóveda adentro, un dedo que toca los botones y carteles flotantes que explican cada paso.
 */
final class Tutorials {
    private Tutorials() {}

    // ------------------------------------------------------------------ guion

    /** Lo que se ve en el celular de mentira en un paso. */
    static final class Frame {
        String title = "Bóveda";
        /** {ícono, nombre, detalle}: «d» carpeta, «f» archivo, «z» zip, «i» imagen. */
        List<String[]> rows = new ArrayList<>();
        int selected;                    // bits de las filas seleccionadas
        String[] bar;                    // botones de abajo (cuando hay algo seleccionado)
        String[] menu;                   // menú que sale al tocar un archivo
        String dlgTitle, dlgBody;
        String[] dlgButtons;
        float progress = -1;
        String progressText;
        String toast;

        Frame title(String t) {
            title = t;
            return this;
        }

        Frame row(String icon, String name, String sub) {
            rows.add(new String[]{icon, name, sub});
            return this;
        }

        Frame sel(int... idx) {
            for (int i : idx) selected |= 1 << i;
            return this;
        }

        Frame bar(String... b) {
            bar = b;
            return this;
        }

        Frame menu(String... m) {
            menu = m;
            return this;
        }

        Frame dialog(String t, String body, String... buttons) {
            dlgTitle = t;
            dlgBody = body;
            dlgButtons = buttons;
            return this;
        }

        Frame progress(float p, String text) {
            progress = p;
            progressText = text;
            return this;
        }

        Frame toast(String t) {
            toast = t;
            return this;
        }
    }

    static final int ROW = 1, BAR = 2, MENU = 3, BTN = 4, NONE = 0;

    static final class Step {
        final Frame frame;
        final String text;
        final int kind, index;
        final boolean longPress;

        Step(Frame frame, String text, int kind, int index, boolean longPress) {
            this.frame = frame;
            this.text = text;
            this.kind = kind;
            this.index = index;
            this.longPress = longPress;
        }
    }

    static final class Tutorial {
        final String id, emoji, title, sub;
        final int c1, c2;
        final List<Step> steps = new ArrayList<>();

        Tutorial(String id, String emoji, String title, String sub, int c1, int c2) {
            this.id = id;
            this.emoji = emoji;
            this.title = title;
            this.sub = sub;
            this.c1 = c1;
            this.c2 = c2;
        }

        Tutorial step(Frame f, String text, int kind, int index) {
            steps.add(new Step(f, text, kind, index, false));
            return this;
        }

        Tutorial hold(Frame f, String text, int index) {
            steps.add(new Step(f, text, ROW, index, true));
            return this;
        }

        Tutorial look(Frame f, String text) {
            steps.add(new Step(f, text, NONE, 0, false));
            return this;
        }
    }

    private static Frame base() {
        return new Frame().row("d", L.t("Fotos"), L.t("12 archivos")).row("d", L.t("Documentos"), L.t("5 archivos"))
                .row("z", L.t("Viaje.zip"), "48 MB").row("f", L.t("Notas.txt"), "2 KB");
    }

    private static Frame inicio() {
        return new Frame().title(L.t("Inicio")).row("z", "MEGA", L.t("Toca para iniciar sesión"))
                .row("d", L.t("La Bóveda"), L.t("Carpeta secreta cifrada · toca para crearla"))
                .row("f", L.t("Papelera"), L.t("Lo que borres se guarda 30 días"));
    }

    private static Frame inicioCerrada() {
        return new Frame().title(L.t("Inicio")).row("z", "MEGA", "correo@ejemplo.com")
                .row("d", L.t("La Bóveda"), L.t("Cerrada · toca para abrir con tu PIN o tu huella"))
                .row("f", L.t("Papelera"), L.t("Lo que borres se guarda 30 días"));
    }

    static List<Tutorial> all() {
        List<Tutorial> l = new ArrayList<>();

        Tutorial zip = new Tutorial("zip", "🗜️", L.t("Comprimir y extraer"), L.t("Haz un .zip y abre uno que te mandaron"), 0xFFB47CFF, 0xFF7C4DFF);
        zip.look(base(), L.t("Bóveda comprime en .zip y extrae sin instalar nada más. Veamos cómo, paso a paso."));
        zip.hold(base(), L.t("Mantén pulsada una carpeta o un archivo para seleccionarlo."), 0);
        zip.step(base().sel(0).bar(L.t("Copiar"), L.t("Mover"), L.t("Borrar"), L.t("Comprimir")),
                L.t("Abajo aparecen las acciones. Toca «Comprimir»."), BAR, 3);
        zip.step(base().sel(0).dialog(L.t("Comprimir en .zip"), "Fotos.zip", L.t("Cancelar"), L.t("Aceptar")),
                L.t("Elige el nombre del .zip y toca «Aceptar»."), BTN, 1);
        zip.look(base().sel(0).progress(0.62f, L.t("Comprimiendo… 62 %")),
                L.t("Sale una barra de progreso. Puedes seguir usando Bóveda mientras termina."));
        zip.look(new Frame().row("d", L.t("Fotos"), L.t("12 archivos")).row("z", "Fotos.zip", "31 MB").row("d", L.t("Documentos"), L.t("5 archivos")).row("z", L.t("Viaje.zip"), "48 MB")
                .toast(L.t("Listo ✓")), L.t("Listo: el .zip aparece junto a tu carpeta."));
        zip.step(base().menu(L.t("Abrir"), L.t("Extraer aquí"), L.t("Extraer a…"), L.t("Renombrar"), L.t("Compartir")),
                L.t("Para abrir un .zip que te mandaron, tócalo. Elige «Extraer aquí»."), MENU, 1);
        zip.look(new Frame().row("d", L.t("Viaje"), L.t("8 archivos")).row("d", L.t("Fotos"), L.t("12 archivos")).row("z", L.t("Viaje.zip"), "48 MB").toast(L.t("Extraído ✓")),
                L.t("Se crea una carpeta con todo lo que traía. «Extraer a…» te deja elegir dónde."));
        l.add(zip);

        Tutorial move = new Tutorial("move", "📂", L.t("Copiar, mover y borrar"), L.t("Pasar archivos de una carpeta a otra"), 0xFFFFB547, 0xFFF07C2C);
        move.hold(base(), L.t("Mantén pulsado el archivo que quieras copiar o mover."), 3);
        move.step(base().sel(3).bar(L.t("Copiar"), L.t("Mover"), L.t("Borrar"), L.t("Comprimir")),
                L.t("«Copiar» deja el original. «Mover» lo cambia de lugar. Toca «Copiar»."), BAR, 0);
        move.look(new Frame().title(L.t("Documentos")).row("f", L.t("Cuentas.xlsx"), "18 KB").bar(L.t("Pegar aquí")),
                L.t("Entra a la carpeta de destino. Aparece «Pegar aquí» abajo."));
        move.step(new Frame().title(L.t("Documentos")).row("f", L.t("Cuentas.xlsx"), "18 KB").bar(L.t("Pegar aquí")),
                L.t("Toca «Pegar aquí»."), BAR, 0);
        move.look(new Frame().title(L.t("Documentos")).row("f", L.t("Cuentas.xlsx"), "18 KB").row("f", L.t("Notas.txt"), "2 KB")
                .progress(0.4f, L.t("Copiando… 40 %")), L.t("La barra de abajo muestra la velocidad y el tiempo que falta."));
        move.hold(base(), L.t("Para borrar, selecciona igual: mantén pulsado."), 3);
        move.step(base().sel(3).bar(L.t("Copiar"), L.t("Mover"), L.t("Borrar"), L.t("Comprimir")), L.t("Toca «Borrar»."), BAR, 2);
        move.step(base().sel(3).dialog(L.t("Borrar"), L.t("Se envía a la papelera por 30 días."), L.t("Cancelar"), L.t("Borrar")),
                L.t("Bóveda te pregunta antes. Lo borrado va a la papelera, así que se puede recuperar."), BTN, 1);
        l.add(move);

        // ---------------------------------------------------------------- papelera
        Tutorial trash = new Tutorial("trash", "🗑️", L.t("Usar la papelera"), L.t("Lo que borras se puede recuperar"), 0xFF8B97AB, 0xFF5D6B83);
        trash.look(inicio(), L.t("Lo que borras en Bóveda no se pierde: se guarda 30 días en la papelera."));
        trash.step(inicio(), L.t("Para verla, entra a «Papelera» desde la pantalla de inicio."), ROW, 2);
        Frame tr = new Frame().title(L.t("Papelera")).row("f", L.t("Notas.txt"), L.t("Borrado hace 2 días")).row("i", L.t("Foto.jpg"), L.t("Borrado hace 9 días"))
                .row("z", L.t("Viaje.zip"), L.t("Borrado hace 20 días")).bar(L.t("Restaurar todo"), L.t("Vaciar papelera"));
        trash.step(tr, L.t("Aquí están, con los días que llevan. Toca uno para elegir qué hacer."), ROW, 0);
        trash.step(new Frame().title(L.t("Papelera")).row("f", L.t("Notas.txt"), L.t("Borrado hace 2 días")).row("i", L.t("Foto.jpg"), L.t("Borrado hace 9 días"))
                .menu(L.t("Restaurar"), L.t("Borrar para siempre")), L.t("«Restaurar» lo devuelve a su carpeta original."), MENU, 0);
        trash.look(new Frame().title(L.t("Papelera")).row("i", L.t("Foto.jpg"), L.t("Borrado hace 9 días")).toast(L.t("Restaurado ✓")),
                L.t("Listo: vuelve a donde estaba. Pasados los 30 días, la papelera lo borra sola."));
        trash.look(new Frame().title(L.t("Papelera")).row("i", L.t("Foto.jpg"), L.t("Borrado hace 9 días"))
                .menu(L.t("Restaurar"), L.t("Borrar para siempre")), L.t("«Borrar para siempre» no tiene vuelta atrás. Úsalo con cuidado."));
        l.add(trash);

        // ---------------------------------------------------------------- la nube
        Tutorial cloud = new Tutorial("cloud", "☁️", L.t("Configurar la nube"), L.t("MEGA, MediaFire, Google Drive y OneDrive"), 0xFF28A8EA, 0xFF0A64D6);
        cloud.look(inicio(), L.t("Bóveda se conecta a tu nube para que veas y muevas archivos como si fueran del celular."));
        cloud.step(inicio(), L.t("Mira la sección «Nube y red». Toca «MEGA» para conectarla."), ROW, 0);
        cloud.step(inicio().dialog(L.t("Iniciar sesión en MEGA"), L.t("Tu correo y tu contraseña de MEGA"), L.t("Cancelar"), L.t("Entrar")),
                L.t("Escribe tu correo y tu contraseña de MEGA, y toca «Entrar»."), BTN, 1);
        cloud.look(inicio().toast(L.t("MEGA conectado")), L.t("La contraseña no se guarda: solo queda la sesión, cifrada en tu celular."));
        cloud.look(new Frame().title("MEGA").row("d", L.t("Mis fotos"), L.t("34 archivos")).row("d", L.t("Trabajo"), L.t("12 archivos")).row("z", L.t("Respaldo.zip"), "210 MB"),
                L.t("Entra a MEGA y copia, mueve o descarga como con cualquier carpeta del celular."));
        cloud.look(inicio(), L.t("MediaFire se conecta igual. Google Drive y OneDrive piden una clave propia y gratuita: Bóveda te lo explica paso a paso en su pantalla."));
        l.add(cloud);

        // ---------------------------------------------------------------- qué es Bóveda
        Tutorial about = new Tutorial("about", "🗂️", L.t("¿Qué es Bóveda?"), L.t("Para qué sirve, en menos de un minuto"), 0xFF4C9DFF, 0xFF6FD6FF);
        about.look(base(), L.t("Bóveda es un gestor de archivos: mira, copia, mueve, comprime y borra lo que hay en tu celular, tu memoria y tu USB."));
        about.look(base(), L.t("Tiene tres pestañas que cambias deslizando el dedo, para tener varias carpetas abiertas a la vez."));
        about.look(inicio(), L.t("Se conecta a la nube (MEGA, MediaFire…) y a servidores FTP, como el de una Xbox 360 o una PC."));
        about.look(inicio(), L.t("Tiene una papelera de 30 días y una carpeta secreta cifrada, La Bóveda, que ninguna otra app puede abrir."));
        about.look(base(), L.t("Es gratis, no tiene anuncios y no te rastrea. Solo usa internet cuando tú abres la nube."));
        l.add(about);

        // ---------------------------------------------------------------- cómo funciona La Bóveda
        Tutorial vault = new Tutorial("vault", "🔐", L.t("Cómo funciona La Bóveda"), L.t("Tu carpeta secreta con PIN y huella"), 0xFF7B4DFF, 0xFFB86BFF);
        vault.look(inicio(), L.t("La Bóveda es una carpeta secreta: lo que guardas ahí queda cifrado, y ninguna otra app puede verlo."));
        vault.step(inicio(), L.t("Para crearla, toca «La Bóveda» en la pantalla de inicio."), ROW, 1);
        vault.step(inicio().dialog(L.t("Crear tu Bóveda"), L.t("Un PIN de 4 a 12 números"), L.t("Cancelar"), L.t("Crear")),
                L.t("Elige un PIN de 4 a 12 números y repítelo. Luego toca «Crear»."), BTN, 1);
        vault.step(inicio().dialog(L.t("¿Abrir también con la huella?"), L.t("Así no escribes el PIN cada vez"), L.t("Solo el PIN"), L.t("Usar la huella")),
                L.t("Puedes añadir tu huella. El PIN sigue sirviendo."), BTN, 1);
        vault.step(base().menu(L.t("Renombrar"), L.t("Compartir"), L.t("Mover a la Bóveda 🔐")), L.t("Para guardar algo, tócalo y elige «Mover a la Bóveda»."), MENU, 2);
        vault.look(inicioCerrada(), L.t("Cuando la cierras, nadie la ve sin tu PIN o tu huella."));
        vault.look(inicioCerrada(), L.t("¡Ojo! Si olvidas el PIN, nadie puede recuperar lo que guardaste, ni siquiera nosotros."));
        l.add(vault);

        return l;
    }

    // ------------------------------------------------------------------ vistos

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("tutoriales", Context.MODE_PRIVATE);
    }

    static Set<String> seen(Context c) {
        return new HashSet<>(prefs(c).getStringSet("vistos", new HashSet<>()));
    }

    static void markSeen(Context c, String id) {
        Set<String> s = seen(c);
        s.add(id);
        prefs(c).edit().putStringSet("vistos", s).apply();
    }

    // ------------------------------------------------------------------ pantalla del tutorial

    static void play(Activity a, Tutorial t, Runnable onClose) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(Ui.BG);
            d.getWindow().setNavigationBarColor(Ui.BG);
        }
        LinearLayout root = Ui.column(a);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        int dp4 = Ui.dp(a, 4);
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp4, Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 6));
        head.addView(Ui.iconButton(a, R.drawable.ic_back, 46, v -> d.dismiss()));
        TextView title = Ui.text(a, t.emoji + "  " + t.title, 19, Ui.TEXT, true);
        title.setSingleLine(true);
        head.addView(title, Ui.weight(1));
        root.addView(head);

        SceneView scene = new SceneView(a, t);
        root.addView(scene, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = Ui.row(a);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 14));
        TextView prev = pill(a, "‹  " + L.t("Anterior"), false);
        TextView play = pill(a, "⏸", false);
        TextView next = pill(a, L.t("Siguiente") + "  ›", true);
        controls.addView(prev, Ui.weight(1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(a, 56), -2);
        pp.setMargins(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
        controls.addView(play, pp);
        controls.addView(next, Ui.weight(1));
        root.addView(controls);

        Handler h = new Handler(Looper.getMainLooper());
        boolean[] auto = {true};
        Runnable[] tick = new Runnable[1];
        Runnable refreshButtons = () -> {
            boolean last = scene.step() >= t.steps.size() - 1;
            next.setText(last ? "✓  " + L.t("Listo") : L.t("Siguiente") + "  ›");
            prev.setAlpha(scene.step() == 0 ? 0.4f : 1f);
            play.setText(auto[0] ? "⏸" : "▶");
        };
        Runnable advance = () -> {
            if (scene.step() >= t.steps.size() - 1) {
                markSeen(a, t.id);
                d.dismiss();
                return;
            }
            scene.go(scene.step() + 1);
            refreshButtons.run();
        };
        tick[0] = () -> {
            if (!d.isShowing()) return;
            if (auto[0]) {
                if (scene.step() >= t.steps.size() - 1) {
                    auto[0] = false;
                    markSeen(a, t.id);
                    refreshButtons.run();
                } else {
                    advance.run();
                }
            }
            h.postDelayed(tick[0], 3800);
        };
        next.setOnClickListener(v -> {
            auto[0] = false;
            advance.run();
            refreshButtons.run();
        });
        prev.setOnClickListener(v -> {
            auto[0] = false;
            if (scene.step() > 0) scene.go(scene.step() - 1);
            refreshButtons.run();
        });
        play.setOnClickListener(v -> {
            auto[0] = !auto[0];
            refreshButtons.run();
        });
        d.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        d.setOnDismissListener(x -> {
            h.removeCallbacksAndMessages(null);
            if (onClose != null) onClose.run();
        });
        d.show();
        scene.go(0);
        refreshButtons.run();
        h.postDelayed(tick[0], 3800);
    }

    private static TextView pill(Activity a, String s, boolean primary) {
        TextView v = Ui.text(a, s, 15, primary ? 0xFF0A0E16 : Ui.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(a, 10), Ui.dp(a, 13), Ui.dp(a, 10), Ui.dp(a, 13));
        v.setBackground(primary ? Ui.gradient(Ui.dp(a, 22), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 22), Ui.dp(a, 1), Ui.STROKE));
        return v;
    }

    // ------------------------------------------------------------------ el celular de mentira

    /** Dibuja Bóveda en miniatura y anima el dedo. Unidades: el celular mide 100 × 178. */
    static final class SceneView extends View {
        private static final float W = 100f, H = 178f;
        private final Tutorial tut;
        private int step = -1;
        private float fx = 50, fy = 130, fromX = 50, fromY = 130, toX = 50, toY = 130, moveT = 1f, tap = 0f, appear = 1f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private float scale, ox, oy;
        private RectF target = new RectF();
        private boolean targetValid;

        SceneView(Context c, Tutorial t) {
            super(c);
            tut = t;
            tp.setSubpixelText(true);
            tp.setLinearText(true);
            p.setSubpixelText(true);
        }

        int step() {
            return step;
        }

        void go(int s) {
            step = Math.max(0, Math.min(tut.steps.size() - 1, s));
            fromX = fx;
            fromY = fy;
            targetValid = false;
            computeTarget();
            appear = 0f;
            moveT = 0f;
            tap = 0f;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1300);
            va.addUpdateListener(an -> {
                float v = (float) an.getAnimatedValue();
                appear = Math.min(1f, v * 3f);
                moveT = new DecelerateInterpolator().getInterpolation(Math.min(1f, v / 0.55f));
                tap = v > 0.6f ? (v - 0.6f) / 0.4f : 0f;
                fx = fromX + (toX - fromX) * moveT;
                fy = fromY + (toY - fromY) * moveT;
                invalidate();
            });
            va.start();
        }

        // posiciones de los elementos del celular
        private RectF rowRect(int i) {
            return new RectF(4, 20 + i * 15f, 96, 33 + i * 15f);
        }

        private RectF barRect(int i, int n) {
            float w = 92f / n;
            return new RectF(4 + i * w, 158, 4 + (i + 1) * w, 174);
        }

        private RectF menuRect(int i, int n) {
            float top = 178 - 6 - n * 14f;
            return new RectF(6, top + i * 14f, 94, top + (i + 1) * 14f);
        }

        private RectF btnRect(int i, int n) {
            float w = 38f;
            return new RectF(54 - (n - i) * (w + 3) + w + 3 - 3 + 0f, 108, 54 - (n - i) * (w + 3) + 2 * w + 3 - 3, 120);
        }

        private void computeTarget() {
            Step s = tut.steps.get(step);
            Frame f = s.frame;
            RectF r = null;
            switch (s.kind) {
                case ROW -> r = rowRect(s.index);
                case BAR -> r = f.bar == null ? null : barRect(s.index, f.bar.length);
                case MENU -> r = f.menu == null ? null : menuRect(s.index, f.menu.length);
                case BTN -> r = f.dlgButtons == null ? null : dlgButtonRect(s.index, f.dlgButtons.length);
                default -> r = null;
            }
            if (r == null) {
                toX = 50;
                toY = 130;
                targetValid = false;
            } else {
                toX = r.centerX();
                toY = r.centerY();
                target = r;
                targetValid = true;
            }
        }

        private RectF dlgButtonRect(int i, int n) {
            float w = 36f;
            float right = 88f;
            return new RectF(right - (n - i) * (w + 2), 110, right - (n - i - 1) * (w + 2) - 2, 124);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (step < 0) return;
            float vw = getWidth(), vh = getHeight();
            scale = Math.min((vw - 24) / W, (vh - 24) / H);
            ox = (vw - W * scale) / 2;
            oy = (vh - H * scale) / 2;
            c.save();
            c.translate(ox, oy);
            c.scale(scale, scale);
            Step s = tut.steps.get(step);
            Frame f = s.frame;

            // marco del celular
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF05070C);
            c.drawRoundRect(new RectF(-2, -2, W + 2, H + 2), 9, 9, p);
            p.setColor(Ui.BG);
            c.drawRoundRect(new RectF(0, 0, W, H), 7, 7, p);
            // barra superior
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setColor(Ui.TEXT);
            tp.setTextSize(7.5f);
            c.drawText(f.title, 6, 14, tp);
            p.setColor(Ui.STROKE);
            c.drawRect(0, 17, W, 17.4f, p);

            // filas
            for (int i = 0; i < f.rows.size(); i++) {
                RectF r = rowRect(i);
                boolean sel = (f.selected & (1 << i)) != 0;
                p.setColor(sel ? 0x334C9DFF : Ui.SURFACE);
                c.drawRoundRect(r, 3, 3, p);
                if (sel) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(0.6f);
                    p.setColor(Ui.ACCENT);
                    c.drawRoundRect(r, 3, 3, p);
                    p.setStyle(Paint.Style.FILL);
                }
                String[] row = f.rows.get(i);
                p.setColor("d".equals(row[0]) ? 0xFFFFB547 : "z".equals(row[0]) ? 0xFFB47CFF : "i".equals(row[0]) ? 0xFF35D08A : 0xFF6FD6FF);
                c.drawRoundRect(new RectF(r.left + 3, r.top + 3, r.left + 13, r.bottom - 3), 2, 2, p);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                tp.setColor(Ui.TEXT);
                tp.setTextSize(5.6f);
                c.drawText(row[1], r.left + 16, r.top + 6.2f, tp);
                tp.setTypeface(Typeface.DEFAULT);
                tp.setColor(Ui.SUB);
                tp.setTextSize(4.4f);
                c.drawText(row[2], r.left + 16, r.top + 10.8f, tp);
                if (sel) {
                    tp.setColor(Ui.ACCENT);
                    tp.setTextSize(7f);
                    c.drawText("✓", r.right - 9, r.top + 8.5f, tp);
                }
            }

            // barra de acciones
            if (f.bar != null) {
                p.setColor(Ui.SURFACE2);
                c.drawRect(0, 156, W, H, p);
                for (int i = 0; i < f.bar.length; i++) {
                    RectF r = barRect(i, f.bar.length);
                    boolean hot = s.kind == BAR && s.index == i;
                    p.setColor(hot ? 0x554C9DFF : Ui.SURFACE3);
                    c.drawRoundRect(new RectF(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1), 3, 3, p);
                    tp.setTypeface(Typeface.DEFAULT_BOLD);
                    tp.setColor(Ui.TEXT);
                    fit(f.bar[i], r.width() - 3f, 4.6f);
                    float tw = tp.measureText(f.bar[i]);
                    c.drawText(f.bar[i], r.centerX() - tw / 2, r.centerY() + 1.6f, tp);
                }
            }

            // progreso
            if (f.progress >= 0) {
                p.setColor(Ui.SURFACE2);
                c.drawRoundRect(new RectF(5, 130, 95, 150), 4, 4, p);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                tp.setColor(Ui.TEXT);
                tp.setTextSize(4.8f);
                c.drawText(f.progressText == null ? "" : f.progressText, 9, 138, tp);
                p.setColor(0xFF232A44);
                c.drawRoundRect(new RectF(9, 142, 91, 145), 1.5f, 1.5f, p);
                p.setColor(Ui.ACCENT2);
                c.drawRoundRect(new RectF(9, 142, 9 + 82 * f.progress, 145), 1.5f, 1.5f, p);
            }

            // menú
            if (f.menu != null) {
                p.setColor(0x99000000);
                c.drawRect(0, 17, W, H, p);
                float top = 178 - 6 - f.menu.length * 14f;
                p.setColor(Ui.SURFACE2);
                c.drawRoundRect(new RectF(4, top - 3, 96, 175), 5, 5, p);
                for (int i = 0; i < f.menu.length; i++) {
                    RectF r = menuRect(i, f.menu.length);
                    if (s.kind == MENU && s.index == i) {
                        p.setColor(0x444C9DFF);
                        c.drawRoundRect(r, 3, 3, p);
                    }
                    tp.setTypeface(Typeface.DEFAULT);
                    tp.setColor(Ui.TEXT);
                    tp.setTextSize(5.6f);
                    c.drawText(f.menu[i], r.left + 6, r.centerY() + 2f, tp);
                }
            }

            // diálogo
            if (f.dlgTitle != null) {
                p.setColor(0x99000000);
                c.drawRect(0, 17, W, H, p);
                p.setColor(Ui.SURFACE2);
                c.drawRoundRect(new RectF(10, 62, 90, 128), 5, 5, p);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                tp.setColor(Ui.TEXT);
                fit(f.dlgTitle, 68f, 6f);
                c.drawText(f.dlgTitle, 16, 74, tp);
                tp.setTypeface(Typeface.DEFAULT);
                tp.setColor(Ui.SUB);
                fit(f.dlgBody == null ? "" : f.dlgBody, 68f, 5f);
                c.drawText(f.dlgBody == null ? "" : f.dlgBody, 16, 88, tp);
                if (f.dlgButtons != null) {
                    for (int i = 0; i < f.dlgButtons.length; i++) {
                        RectF r = dlgButtonRect(i, f.dlgButtons.length);
                        boolean hot = s.kind == BTN && s.index == i;
                        p.setColor(hot ? 0x554C9DFF : Ui.SURFACE3);
                        c.drawRoundRect(r, 3, 3, p);
                        tp.setTypeface(Typeface.DEFAULT_BOLD);
                        tp.setColor(hot ? Ui.ACCENT2 : Ui.TEXT);
                        fit(f.dlgButtons[i], r.width() - 3f, 4.6f);
                        float tw = tp.measureText(f.dlgButtons[i]);
                        c.drawText(f.dlgButtons[i], r.centerX() - tw / 2, r.centerY() + 1.6f, tp);
                    }
                }
            }

            // aviso corto
            if (f.toast != null) {
                p.setColor(0xFF2B3550);
                c.drawRoundRect(new RectF(24, 140, 76, 152), 6, 6, p);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                tp.setColor(Ui.TEXT);
                tp.setTextSize(5f);
                float tw = tp.measureText(f.toast);
                c.drawText(f.toast, 50 - tw / 2, 147.5f, tp);
            }

            // resplandor en lo que se toca
            if (targetValid && moveT > 0.9f) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(0xFFFFC857);
                p.setAlpha((int) (140 + 100 * Math.sin(System.currentTimeMillis() / 180.0)));
                c.drawRoundRect(new RectF(target.left - 1, target.top - 1, target.right + 1, target.bottom + 1), 4, 4, p);
                p.setAlpha(255);
                p.setStyle(Paint.Style.FILL);
                postInvalidateOnAnimation();
            }

            // cartel flotante con su flechita
            drawCaption(c, s);

            // el dedo
            if (s.kind != NONE) {
                p.setColor(0x66FFFFFF);
                c.drawCircle(fx, fy, 6 + tap * 4, p);
                p.setColor(0xCCFFFFFF);
                c.drawCircle(fx, fy, s.longPress && tap > 0 ? 3.6f + tap * 1.5f : 3.6f, p);
                if (tap > 0 && tap < 1f) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1f);
                    p.setColor(0x99FFFFFF);
                    c.drawCircle(fx, fy, 6 + tap * 12, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
            c.restore();
        }

        /** Achica la letra hasta que el texto quepa en el ancho dado (unidades del celular). */
        private void fit(String text, float maxW, float size) {
            tp.setTextSize(size);
            while (tp.measureText(text) > maxW && size > 2.6f) {
                size -= 0.2f;
                tp.setTextSize(size);
            }
        }

        /** ¿Hay filas en la parte de arriba que un cartel taparía? */
        private boolean hasContent(Frame f) {
            return f.rows.size() >= 3 || f.progress >= 0;
        }

        private void drawCaption(Canvas c, Step s) {
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(22f);                         // se dibuja a 1/4: queda en 5,5 unidades del celular
            tp.setColor(0xFF10131C);
            StaticLayout lay = StaticLayout.Builder.obtain(s.text, 0, s.text.length(), tp, 280).build();
            float k = 0.25f;
            float h = lay.getHeight() * k + 8;
            boolean below = targetValid && (toY < 90 || s.kind == BTN);
            float top;
            if (!targetValid) {
                top = hasContent(s.frame) ? 96 : 30;                  // sin objetivo: en la parte vacía
            } else if (s.kind == MENU) {
                top = 24;                                             // el menú ocupa abajo: el cartel va arriba
            } else if (below) {
                top = Math.min(toY + 14, 168 - h);
            } else {
                top = Math.max(22, toY - 14 - h);
            }
            RectF box = new RectF(11, top, 89, top + h);
            p.setColor(0xFFFFE9A8);
            c.drawRoundRect(box, 5, 5, p);
            if (targetValid && s.kind != MENU) {
                Path tri = new Path();
                float ax = Math.max(18, Math.min(82, toX));
                if (below) {
                    tri.moveTo(ax - 3, box.top + 0.3f);
                    tri.lineTo(ax, box.top - 4);
                    tri.lineTo(ax + 3, box.top + 0.3f);
                } else {
                    tri.moveTo(ax - 3, box.bottom - 0.3f);
                    tri.lineTo(ax, box.bottom + 4);
                    tri.lineTo(ax + 3, box.bottom - 0.3f);
                }
                tri.close();
                c.drawPath(tri, p);
            }
            c.save();
            c.translate(box.left + 4, box.top + 4);
            c.scale(k, k);
            lay.draw(c);
            c.restore();
        }
    }
}
