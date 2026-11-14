package com.forja.orbita;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Órbita (beta): sistema solar con gravedad real, lanzar asteroides y planetas, y ver los choques. */
public class MainActivity extends Activity {
    private SpaceView space;
    private TextView status, info, banner, speedBtn, pauseBtn;
    private int speed = 2;
    private float d;

    private int dp(float v) {
        return Math.round(v * d);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF03050D);
        space = new SpaceView(this);
        root.addView(space, new FrameLayout.LayoutParams(-1, -1));

        status = text("", 12.5f, 0xCCE8EEFF);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(round(0x66000000, 14));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        sl.setMargins(dp(10), dp(10), 0, 0);
        root.addView(status, sl);

        info = text("", 12.5f, 0xFFE8EEFF);
        info.setPadding(dp(14), dp(10), dp(14), dp(10));
        info.setBackground(round(0x88101A3A, 14));
        info.setVisibility(View.GONE);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        il.setMargins(0, dp(10), dp(10), 0);
        root.addView(info, il);

        banner = text("", 13.5f, Color.WHITE);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setBackground(round(0xCC1E4FD6, 16));
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(v -> stopPlacing());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bl.setMargins(0, dp(64), 0, 0);
        root.addView(banner, bl);

        // barra de abajo
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(6), dp(8), dp(10));
        pauseBtn = button("⏸", v -> togglePause());
        speedBtn = button("", v -> speedMenu());
        bar.addView(pauseBtn);
        bar.addView(button("⏪", v -> setSpeed(speed - 1)));
        bar.addView(speedBtn);
        bar.addView(button("⏩", v -> setSpeed(speed + 1)));
        bar.addView(button("🎯 Objetos", v -> objectsMenu()));
        bar.addView(button("👁 Seguir", v -> followMenu()));
        bar.addView(button("↺", v -> new AlertDialog.Builder(this).setMessage("¿Reiniciar el sistema solar?")
                .setPositiveButton("Reiniciar", (dd, w) -> {
                    space.reset();
                    info.setVisibility(View.GONE);
                    stopPlacing();
                }).setNegativeButton("Cancelar", null).show()));
        hs.addView(bar);
        root.addView(hs, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        space.listener = new SpaceView.Listener() {
            @Override
            public void onInfo(String text) {
                info.setText(text);
                info.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onStatus(String text) {
                status.setText(text);
                if (space.follow >= 0 && space.follow < space.sim.n) {
                    info.setText(space.info(space.follow));
                    info.setVisibility(View.VISIBLE);
                } else if (space.followName == null && info.getVisibility() == View.VISIBLE && space.placing == null) {
                    info.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlaced() {
            }
        };
        setContentView(root);
        setSpeed(speed);
        immersive();
    }

    // ------------------------------------------------------------------ pequeños ayudantes de pantalla

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView button(String s, View.OnClickListener l) {
        TextView t = text(s, 15, 0xFFE8EEFF);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setMinHeight(dp(48));
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(round(0x99101A3A, 24));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void immersive() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.statusBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // ------------------------------------------------------------------ controles

    private void togglePause() {
        space.paused = !space.paused;
        pauseBtn.setText(space.paused ? "▶" : "⏸");
    }

    private void setSpeed(int i) {
        speed = Math.max(0, Math.min(SpaceView.SPEED_DAYS.length - 1, i));
        space.setSpeedIndex(speed);
        double dd = SpaceView.SPEED_DAYS[speed];
        speedBtn.setText(dd >= 365 ? (Math.round(dd / 365) + (dd / 365 == 1 ? " año/s" : " años/s")) : (Math.round(dd) + (dd == 1 ? " día/s" : " días/s")));
    }

    private void speedMenu() {
        String[] names = new String[SpaceView.SPEED_DAYS.length];
        for (int i = 0; i < names.length; i++) {
            double dd = SpaceView.SPEED_DAYS[i];
            names[i] = dd >= 365 ? Math.round(dd / 365) + (dd / 365 == 1 ? " año por segundo" : " años por segundo") : Math.round(dd) + (dd == 1 ? " día por segundo" : " días por segundo");
        }
        new AlertDialog.Builder(this).setTitle("Velocidad del tiempo").setItems(names, (dd, w) -> setSpeed(w)).show();
    }

    private void followMenu() {
        int n = space.sim.n;
        String[] names = new String[n + 1];
        names[0] = "🔭 Cámara libre";
        for (int i = 0; i < n; i++) names[i + 1] = space.sim.name[i];
        new AlertDialog.Builder(this).setTitle("Seguir").setItems(names, (dd, w) -> {
            if (w == 0) {
                space.follow(null);
                info.setVisibility(View.GONE);
            } else {
                space.follow(names[w]);
            }
        }).show();
    }

    private void objectsMenu() {
        final SpaceView.Kind[] kinds = {
                new SpaceView.Kind("Asteroide", 0, 0, 0, Sim.ROCK, 0),
                new SpaceView.Kind("Lluvia de asteroides", 1, 0, 0, Sim.ROCK, 0),
                new SpaceView.Kind("Roca grande", 2, 1.0e-9, 3.0e-6, Sim.ROCK, 0xFF9A8F80),
                new SpaceView.Kind("Planeta", 2, 3.003e-6, 4.26e-5, Sim.PLANET, 0xFF58D68D),
                new SpaceView.Kind("Gigante gaseoso", 2, 9.55e-4, 4.78e-4, Sim.PLANET, 0xFFE59866),
                new SpaceView.Kind("Estrella roja", 2, 0.3, 0.0016, Sim.STAR, 0xFFFF6B4A)};
        String[] names = {"☄ Asteroide (uno)", "🌠 Lluvia de asteroides (200)", "🪨 Roca grande", "🌍 Planeta como la Tierra", "🪐 Gigante gaseoso", "⭐ Estrella roja", "💫 Cinturón de asteroides (2000)"};
        new AlertDialog.Builder(this).setTitle("Objetos").setItems(names, (dd, w) -> {
            if (w == 6) {
                space.addBelt();
                return;
            }
            space.placing = kinds[w];
            banner.setText("Toca y arrastra para lanzar: " + names[w].substring(2).replaceAll(" \\(.*\\)", "") + "\n(la raya es la velocidad; los puntos, por dónde irá)  ·  Toca aquí para terminar");
            banner.setVisibility(View.VISIBLE);
        }).show();
    }

    private void stopPlacing() {
        space.placing = null;
        banner.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        space.start();
        immersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        space.stop();
    }
}
