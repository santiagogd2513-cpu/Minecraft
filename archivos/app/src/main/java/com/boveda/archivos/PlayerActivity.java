package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Locale;

/**
 * Reproductor de audio y video de Bóveda. Usa los decodificadores del celular: MP3, WAV, OGG,
 * Opus, FLAC, M4A/AAC, AMR y MIDI en audio; MP4, MKV, WebM, 3GP, MOV y TS en video (y otros si
 * el celular los trae). Pasa al siguiente de la carpeta al terminar.
 */
public class PlayerActivity extends Activity {
    private String[] paths;
    private int index;
    private VideoView video;
    private MediaPlayer player;
    private LinearLayout top, panel;
    private FrameLayout art;
    private TextView title, sub, time, total, speedBtn;
    private ImageView playBtn;
    private SeekBar seek;
    private boolean seeking, controlsShown = true, landscape;
    private float speed = 1f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshTime();
            main.postDelayed(this, 300);
        }
    };
    private final Runnable autoHide = () -> {
        if (isVideo() && video.isPlaying()) showControls(false);
    };

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        paths = getIntent().getStringArrayExtra("paths");
        index = getIntent().getIntExtra("index", 0);
        if (paths == null || paths.length == 0) {
            finish();
            return;
        }
        if (b != null) index = b.getInt("index", index);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        root.addView(video, vl);

        // carátula para el audio
        art = new FrameLayout(this);
        LinearLayout ac = Ui.column(this);
        ac.setGravity(Gravity.CENTER);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.gradient(dp(36), 0xFF7B52E8, 0xFFB58CFF));
        ImageView note = new ImageView(this);
        note.setImageDrawable(Ui.icon(this, R.drawable.ic_music, Color.WHITE));
        disc.addView(note, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));
        ac.addView(disc, new LinearLayout.LayoutParams(dp(200), dp(200)));
        art.addView(ac, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(art, new FrameLayout.LayoutParams(-1, -1));

        // barra de arriba
        top = Ui.row(this);
        top.setPadding(dp(4), dp(8), dp(8), dp(8));
        top.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0x00000000}));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> finish()));
        LinearLayout tt = Ui.column(this);
        title = Ui.text(this, "", 16, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sub = Ui.text(this, "", 12, Ui.SUB, false);
        tt.addView(title);
        tt.addView(sub);
        top.addView(tt, Ui.weight(1));
        top.addView(Ui.iconButton(this, R.drawable.ic_open, 44, v ->
                Viewers.openWithOther(this, paths[index], FileTypes.mime(Paths.name(paths[index])))));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // controles de abajo
        panel = Ui.column(this);
        panel.setPadding(dp(16), dp(14), dp(16), dp(18));
        panel.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x00000000}));
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) time.setText(fmt((long) p * Math.max(0, video.getDuration()) / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
                main.removeCallbacks(autoHide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                video.seekTo((int) ((long) s.getProgress() * Math.max(0, video.getDuration()) / 1000));
                scheduleHide();
            }
        });
        panel.addView(seek);
        LinearLayout times = Ui.row(this);
        time = Ui.text(this, "0:00", 12.5f, Ui.SUB, false);
        total = Ui.text(this, "0:00", 12.5f, Ui.SUB, false);
        times.addView(time, Ui.weight(1));
        times.addView(total);
        times.setPadding(dp(12), 0, dp(12), dp(6));
        panel.addView(times);
        LinearLayout btns = Ui.row(this);
        btns.setGravity(Gravity.CENTER);
        speedBtn = Ui.text(this, "1×", 14, Ui.TEXT, true);
        speedBtn.setGravity(Gravity.CENTER);
        speedBtn.setOnClickListener(v -> cycleSpeed());
        btns.addView(speedBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        btns.addView(Ui.iconButton(this, R.drawable.ic_back, 48, v -> previous()));
        TextView back10 = Ui.text(this, "−10", 14, Ui.TEXT, true);
        back10.setGravity(Gravity.CENTER);
        back10.setOnClickListener(v -> skip(-10_000));
        btns.addView(back10, new LinearLayout.LayoutParams(dp(48), dp(48)));
        playBtn = new ImageView(this);
        playBtn.setImageDrawable(Ui.icon(this, R.drawable.ic_pause, 0xFF06101F));
        playBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        playBtn.setBackground(Ui.ripple(Ui.gradient(dp(32), Ui.ACCENT, Ui.ACCENT2), Ui.round(Color.WHITE, dp(32))));
        playBtn.setOnClickListener(v -> togglePlay());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(64), dp(64));
        pl.setMargins(dp(12), 0, dp(12), 0);
        btns.addView(playBtn, pl);
        TextView fwd10 = Ui.text(this, "+10", 14, Ui.TEXT, true);
        fwd10.setGravity(Gravity.CENTER);
        fwd10.setOnClickListener(v -> skip(10_000));
        btns.addView(fwd10, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageView next = Ui.iconButton(this, R.drawable.ic_back, 48, v -> next(true));
        next.setRotation(180);
        btns.addView(next);
        ImageView rotate = Ui.iconButton(this, R.drawable.ic_refresh, 48, v -> toggleOrientation());
        btns.addView(rotate);
        panel.addView(btns);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnClickListener(v -> {
            if (!isVideo()) return;
            showControls(!controlsShown);
        });
        setContentView(root);

        video.setOnPreparedListener(mp -> {
            player = mp;
            applySpeed();
            total.setText(fmt(video.getDuration()));
            if (isVideo() && mp.getVideoWidth() > 0) {
                // video horizontal: pantalla horizontal
                landscape = mp.getVideoWidth() > mp.getVideoHeight();
                setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
            video.start();
            updatePlayIcon();
            scheduleHide();
        });
        video.setOnCompletionListener(mp -> next(false));
        video.setOnErrorListener((mp, what, extra) -> {
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle("No se puede reproducir aquí")
                    .setMessage("«" + Paths.name(paths[index]) + "» usa un formato que el celular no sabe reproducir"
                            + " (algunos AVI, WMV o FLV antiguos). Puedes abrirlo con otra app, como VLC.")
                    .setPositiveButton("Abrir con otra app", (d, w) ->
                            Viewers.openWithOther(this, paths[index], FileTypes.mime(Paths.name(paths[index]))))
                    .setNeutralButton("Siguiente", (d, w) -> next(true))
                    .setNegativeButton("Cerrar", null)
                    .show();
            return true;
        });
        load();
    }

    private boolean isVideo() {
        return Viewers.isVideo(paths[index]);
    }

    private void load() {
        String p = paths[index];
        title.setText(Paths.name(p));
        sub.setText((isVideo() ? "Video" : "Audio") + (paths.length > 1 ? "  ·  " + (index + 1) + " de " + paths.length : ""));
        art.setVisibility(isVideo() ? View.GONE : View.VISIBLE);
        if (!isVideo()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            showControls(true);
        }
        seek.setProgress(0);
        time.setText("0:00");
        total.setText("…");
        video.setVideoURI(StorageProvider.documentUri(this, p));
    }

    private void next(boolean user) {
        if (index + 1 < paths.length) {
            index++;
            load();
        } else if (user) {
            android.widget.Toast.makeText(this, "Es el último de la carpeta", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            updatePlayIcon();
            showControls(true);
        }
    }

    private void previous() {
        // como en los reproductores: primero vuelve al inicio, después al anterior
        if (video.getCurrentPosition() > 3000 || index == 0) {
            video.seekTo(0);
            return;
        }
        index--;
        load();
    }

    private void skip(int ms) {
        int to = Math.max(0, Math.min(video.getDuration(), video.getCurrentPosition() + ms));
        video.seekTo(to);
        scheduleHide();
    }

    private void togglePlay() {
        if (video.isPlaying()) video.pause();
        else video.start();
        updatePlayIcon();
        scheduleHide();
    }

    private void updatePlayIcon() {
        playBtn.setImageDrawable(Ui.icon(this, video.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play, 0xFF06101F));
    }

    private void cycleSpeed() {
        float[] s = {1f, 1.25f, 1.5f, 2f, 0.75f};
        int i = 0;
        for (int k = 0; k < s.length; k++) if (Math.abs(s[k] - speed) < 0.01) i = k;
        speed = s[(i + 1) % s.length];
        applySpeed();
    }

    private void applySpeed() {
        speedBtn.setText((speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed).replace('.', ',')) + "×");
        if (player == null) return;
        try {
            boolean was = video.isPlaying();
            player.setPlaybackParams(new PlaybackParams().setSpeed(speed));
            if (!was) player.pause();
        } catch (Exception ignored) {
        }
    }

    private void toggleOrientation() {
        landscape = !landscape;
        setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private void showControls(boolean show) {
        controlsShown = show;
        top.animate().alpha(show ? 1 : 0).setDuration(180).start();
        panel.animate().alpha(show ? 1 : 0).setDuration(180).start();
        top.setVisibility(View.VISIBLE);
        panel.setVisibility(View.VISIBLE);
        if (!show) main.postDelayed(() -> {
            if (!controlsShown) {
                top.setVisibility(View.INVISIBLE);
                panel.setVisibility(View.INVISIBLE);
            }
        }, 200);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null && isVideo()) {
            if (show) c.show(WindowInsets.Type.systemBars());
            else {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        if (show) scheduleHide();
    }

    private void scheduleHide() {
        main.removeCallbacks(autoHide);
        if (isVideo()) main.postDelayed(autoHide, 3500);
    }

    private void refreshTime() {
        if (seeking || video == null) return;
        int d = video.getDuration(), p = video.getCurrentPosition();
        if (d > 0) {
            seek.setProgress((int) ((long) p * 1000 / d));
            time.setText(fmt(p));
        }
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        if (video.isPlaying()) video.pause();
        updatePlayIcon();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
