package com.bitpoint.homeservercontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class NotificationHelper {
    private static final String CHANNEL_ID = "server_alerts";
    private static final int ALERT_ID = 8022;

    private NotificationHelper() {}

    static void postState(Context context, boolean online, String host, int port) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager);

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 0, open, flags);

        String title = online ? "Сервер снова доступен" : "Сервер недоступен";
        String text = host + ":" + port + (online ? " отвечает" : " не отвечает по SSH");
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_server)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        try {
            manager.notify(ALERT_ID, notification);
        } catch (SecurityException ignored) {
            // Android 13+: пользователь может запретить уведомления.
        }
    }

    private static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Состояние домашнего сервера",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Предупреждает, если SSH-сервер пропал или снова появился");
            manager.createNotificationChannel(channel);
        }
    }
}
