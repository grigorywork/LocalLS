package com.bitpoint.homeservercontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the app process in foreground priority while SFTP transfers are active.
 * The actual transfer remains in FilesActivity; this service only protects the
 * process from aggressive background killing on low-RAM phones.
 */
public class TransferForegroundService extends Service {
    private static final String CHANNEL_ID = "file_transfers";
    private static final int NOTIFICATION_ID = 2208;
    private static final String ACTION_START = "com.bitpoint.homeservercontrol.transfer.START";

    static void start(Context context) {
        Intent intent = new Intent(context, TransferForegroundService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, TransferForegroundService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15+ limits dataSync foreground services to six hours per day.
        stopSelf();
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, FilesActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, open, pendingFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_cloud_notification)
                .setContentTitle("LocalLS")
                .setContentText("SFTP-передача файлов активна")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Передача файлов",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Показывает активную SFTP-передачу и помогает Android не останавливать её в фоне.");
        manager.createNotificationChannel(channel);
    }
}
