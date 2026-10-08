package com.bitpoint.homeservercontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Private explicit one-shot callback; raw shell output is never displayed or logged. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String nonce = prefs.getString("pending", "");
        if (nonce.isEmpty() || !("localls.setup." + nonce).equals(intent.getAction())
                || System.currentTimeMillis() - prefs.getLong("pending_at", 0) > 1800000) return;
        Bundle result = intent.getBundleExtra("result");
        SharedPreferences.Editor edit = prefs.edit().remove("pending");
        if (result == null || result.getInt("err", 0) != 0 || result.getInt("exitCode", -1) != 0) {
            edit.putString("status", "Подготовка не завершена. Проверь интернет, разрешения Termux и повтори.").apply();
            return;
        }
        String output = result.getString("stdout", "");
        String user = "", port = "";
        if (output.length() <= 16384) for (String line : output.split("\\r?\\n")) {
            if (line.startsWith("USER=")) user = line.substring(5);
            else if (line.startsWith("PORT=")) port = line.substring(5);
        }
        if (!user.matches("[a-zA-Z0-9_]{1,64}") || !port.matches("[0-9]{1,5}")) {
            edit.putString("status", "Termux не вернул корректные параметры сервера.").apply();
            return;
        }
        int number = Integer.parseInt(port);
        if (number < 1 || number > 65535) { edit.putString("status", "Некорректный порт сервера.").apply(); return; }
        edit.putString("user", user).putInt("port", number)
                .putString("status", "OpenSSH подготовлен. Проверь подключение перед экспортом.").apply();
    }
}
