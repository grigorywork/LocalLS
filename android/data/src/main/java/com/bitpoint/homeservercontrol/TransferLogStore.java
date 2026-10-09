package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class TransferLogStore {
    private static final int MAX_LINES = 25;

    private TransferLogStore() {}

    static void add(Context context, String label, String result) {
        SharedPreferences prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(ServerConfig.KEY_TRANSFER_LOG, "");
        List<String> lines = new ArrayList<>();
        String time = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date());
        lines.add(time + " • " + clean(label) + " • " + clean(result));
        if (old != null && !old.trim().isEmpty()) {
            String[] prior = old.split("\\n");
            for (String line : prior) {
                if (!line.trim().isEmpty() && lines.size() < MAX_LINES) lines.add(line.trim());
            }
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        prefs.edit().putString(ServerConfig.KEY_TRANSFER_LOG, out.toString()).apply();
    }

    static String render(Context context) {
        String value = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE)
                .getString(ServerConfig.KEY_TRANSFER_LOG, "");
        return value == null || value.trim().isEmpty() ? "История передач пока пуста." : value;
    }

    static void clear(Context context) {
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE)
                .edit().remove(ServerConfig.KEY_TRANSFER_LOG).apply();
    }

    private static String clean(String text) {
        if (text == null) return "—";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 140) return oneLine.substring(0, 140) + "…";
        return oneLine;
    }
}
