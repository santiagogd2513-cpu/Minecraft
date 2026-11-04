package com.boveda.archivos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

/** Mantiene vivas las copias con la app en segundo plano y muestra el progreso. */
public class TransferService extends Service implements TransferEngine.Listener {
    private static final int ID = 9;
    private static final String CHANNEL = "transferencias";
    private TransferEngine engine;
    private PowerManager.WakeLock wake;
    private long last;

    static void ensure(Context c) {
        try {
            c.startForegroundService(new Intent(c, TransferService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Copias y movimientos", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        engine = TransferEngine.get(this);
        engine.addListener(this);
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boveda:copias");
        wake.acquire(12 * 60 * 60 * 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancelar".equals(intent.getAction())) {
            for (TransferEngine.Job j : engine.jobs()) if (j.state <= 1) j.cancel = true;
        }
        startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (!engine.busy()) stop();
        return START_NOT_STICKY;
    }

    @Override
    public void onChanged() {
        if (!engine.busy()) {
            stop();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - last < 800) return;
        last = now;
        getSystemService(NotificationManager.class).notify(ID, build());
    }

    private void stop() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification build() {
        TransferEngine.Job cur = null;
        int waiting = 0;
        for (TransferEngine.Job j : engine.jobs()) {
            if (j.state == 1) cur = j;
            if (j.state == 0) waiting++;
        }
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class).setAction("transferencias"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 2, new Intent(this, TransferService.class).setAction("cancelar"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_swap)
                .setColor(Ui.ACCENT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Cancelar", cancel).build());
        if (cur == null) return b.setContentTitle("Preparando…").build();
        String text = cur.totalBytes > 0
                ? Ui.size(cur.doneBytes) + " de " + Ui.size(cur.totalBytes) + " · " + Ui.speed(cur.speed)
                : cur.doneItems + " de " + cur.totalItems;
        if (waiting > 0) text += " · " + waiting + " en cola";
        return b.setContentTitle(cur.title())
                .setContentText(text)
                .setSubText(cur.current)
                .setProgress(1000, (int) (cur.progress() * 1000), cur.totalBytes == 0 && cur.totalItems == 0)
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
