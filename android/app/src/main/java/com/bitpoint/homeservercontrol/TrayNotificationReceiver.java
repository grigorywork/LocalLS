package com.bitpoint.homeservercontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TrayNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("localls.tray.dismiss".equals(intent.getAction())) NotificationHelper.clearTray(context);
    }
}
