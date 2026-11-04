package com.nocturna.browser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

/** Mantiene vivas las descargas con la app en segundo plano y muestra su progreso. */
public class DownloadService extends Service implements DownloadEngine.Listener {
    private static final int ID = 7;
    private DownloadEngine engine;
    private PowerManager.WakeLock wake;
    private long lastUpdate;

    static void ensure(Context c) {
        try {
            c.startForegroundService(new Intent(c, DownloadService.class));
        } catch (Exception ignored) {
            // sin permiso para servicio en primer plano: la descarga sigue mientras la app esté abierta
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = DownloadEngine.get(this);
        engine.addListener(this);
        PowerManager pm = getSystemService(PowerManager.class);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nocturna:descargas");
        wake.acquire(6 * 60 * 60 * 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (!engine.anyActive()) stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public void onDownloadsChanged() {
        if (!engine.anyActive()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 900) return;
        lastUpdate = now;
        getSystemService(NotificationManager.class).notify(ID, build());
    }

    private Notification build() {
        int count = 0;
        long done = 0, total = 0;
        double speed = 0;
        String first = null;
        boolean unknown = false;
        for (DownloadItem d : engine.items()) {
            if (d.status != DownloadItem.RUNNING && d.status != DownloadItem.QUEUED) continue;
            count++;
            if (first == null) first = d.name;
            done += d.done.get();
            if (d.total > 0) total += d.total; else unknown = true;
            speed += d.speed;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 1, new Intent(this, DownloadsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = count <= 1 ? (first == null ? "Preparando descarga" : first) : "Descargando " + count + " archivos";
        String text = Ui.size(done) + (total > 0 && !unknown ? " de " + Ui.size(total) : "") + " · " + Ui.speed(speed);
        int pct = total > 0 && !unknown ? (int) (done * 100 / total) : 0;
        return new Notification.Builder(this, DownloadEngine.CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setColor(Ui.ACCENT)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, pct, total <= 0 || unknown)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        engine.removeListener(this);
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
