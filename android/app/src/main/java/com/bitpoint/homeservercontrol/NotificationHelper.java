package com.bitpoint.homeservercontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class NotificationHelper {
    static final int TRAY_ID = 2409;
    private static final String CHANNEL_ID = "server_alerts";
    private static final int ALERT_ID = 8022;

    private NotificationHelper() {}

    static boolean showTray(android.app.Activity activity) {
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                "app_tray", "LocalLS в фоне", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(activity, activity instanceof FilesActivity ? FilesActivity.class : MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(activity, TRAY_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dismiss = new Intent(activity, TrayNotificationReceiver.class).setAction("localls.tray.dismiss");
        PendingIntent close = PendingIntent.getBroadcast(activity, TRAY_ID, dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(activity, "app_tray") : new Notification.Builder(activity);
        Notification notification = builder.setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_cloud_notification)
                .setContentTitle("LocalLS свёрнут")
                .setContentText("Нажми, чтобы вернуться в приложение")
                .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_close, "Убрать", close).build())
                .build();
        try { manager.notify(TRAY_ID, notification); return true; }
        catch (SecurityException e) { return false; }
    }

    static void clearTray(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(TRAY_ID);
    }

    static void postState(Context context, boolean online, String host, int port) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager);

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 0, open, flags);

        String title = online ? "Сервер снова доступен" : "Сервер недоступен";
        String text = host + ":" + port + (online ? " отвечает" : " не отвечает по SSH");
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_server)
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
