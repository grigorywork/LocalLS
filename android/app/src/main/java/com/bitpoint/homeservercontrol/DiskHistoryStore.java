package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DiskHistoryStore {
    private static final int MAX_ITEMS = 20;
    private static final String SEP = "\n";

    private DiskHistoryStore() {}

    static void add(Context context, String diskText) {
        if (TextUtils.isEmpty(diskText) || "—".equals(diskText)) return;
        SharedPreferences prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        List<String> items = loadItems(prefs.getString(ServerConfig.KEY_DISK_HISTORY, ""));
        String normalized = diskText.replace('\n', ' ');
        if (!items.isEmpty() && items.get(0).endsWith(normalized)) return;
        String time = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date());
        items.add(0, time + " • " + normalized);
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
        prefs.edit().putString(ServerConfig.KEY_DISK_HISTORY, TextUtils.join(SEP, items)).apply();
    }

    static String render(Context context, int maxItems) {
        String raw = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE)
                .getString(ServerConfig.KEY_DISK_HISTORY, "");
        List<String> items = loadItems(raw);
        if (items.isEmpty()) return "История пока пустая";
        int count = Math.min(maxItems, items.size());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append('\n');
            out.append(items.get(i));
        }
        return out.toString();
    }

    static void clear(Context context) {
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE)
                .edit().remove(ServerConfig.KEY_DISK_HISTORY).apply();
    }

    private static List<String> loadItems(String raw) {
        List<String> items = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return items;
        String[] lines = raw.split("\\n");
        for (String line : lines) if (!TextUtils.isEmpty(line)) items.add(line);
        return items;
    }
}
