package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MetricHistoryStore {
    private static final String PREFS = "metric_history_v04";
    private static final String KEY = "samples";
    private static final int MAX_SAMPLES = 96;
    private static final Pattern DISK_PERCENT = Pattern.compile("(\\d{1,3})%");

    private MetricHistoryStore() {}

    static void addProbe(Context context, boolean online, long latencyMs, String diskText) {
        Sample sample = new Sample();
        sample.timestamp = System.currentTimeMillis();
        sample.availability = online ? 100f : 0f;
        sample.latencyMs = online ? Math.max(0f, latencyMs) : Float.NaN;
        sample.diskPercent = parseDiskPercent(diskText);
        sample.speedMbps = Float.NaN;
        append(context, sample);
    }

    static void addAvailability(Context context, boolean online) {
        Sample sample = new Sample();
        sample.timestamp = System.currentTimeMillis();
        sample.availability = online ? 100f : 0f;
        sample.latencyMs = Float.NaN;
        sample.diskPercent = Float.NaN;
        sample.speedMbps = Float.NaN;
        append(context, sample);
    }

    static void addSpeed(Context context, double speedMbps) {
        if (Double.isNaN(speedMbps) || Double.isInfinite(speedMbps) || speedMbps < 0) return;
        Sample sample = new Sample();
        sample.timestamp = System.currentTimeMillis();
        sample.availability = Float.NaN;
        sample.latencyMs = Float.NaN;
        sample.diskPercent = Float.NaN;
        sample.speedMbps = (float) speedMbps;
        append(context, sample);
    }

    static List<Sample> read(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        List<Sample> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            Sample sample = parseLine(line);
            if (sample != null) out.add(sample);
        }
        return out;
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    static double parseIperfMbps(String output) {
        if (output == null) return Double.NaN;
        Pattern p = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s+Mbits/sec", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(output);
        double last = Double.NaN;
        while (m.find()) {
            try {
                last = Double.parseDouble(m.group(1).replace(',', '.'));
            } catch (Exception ignored) {}
        }
        return last;
    }

    private static synchronized void append(Context context, Sample sample) {
        List<Sample> list = read(context);
        list.add(sample);
        while (list.size() > MAX_SAMPLES) list.remove(0);
        StringBuilder raw = new StringBuilder();
        for (Sample item : list) {
            raw.append(item.timestamp).append('|')
                    .append(value(item.availability)).append('|')
                    .append(value(item.latencyMs)).append('|')
                    .append(value(item.diskPercent)).append('|')
                    .append(value(item.speedMbps)).append('\n');
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY, raw.toString()).apply();
    }

    private static String value(float v) {
        return Float.isNaN(v) ? "-" : String.format(Locale.US, "%.3f", v);
    }

    private static Sample parseLine(String line) {
        try {
            String[] p = line.split("\\|", -1);
            if (p.length != 5) return null;
            Sample s = new Sample();
            s.timestamp = Long.parseLong(p[0]);
            s.availability = parseFloat(p[1]);
            s.latencyMs = parseFloat(p[2]);
            s.diskPercent = parseFloat(p[3]);
            s.speedMbps = parseFloat(p[4]);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static float parseFloat(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) return Float.NaN;
        try { return Float.parseFloat(value); }
        catch (Exception e) { return Float.NaN; }
    }

    private static float parseDiskPercent(String text) {
        if (text == null) return Float.NaN;
        Matcher m = DISK_PERCENT.matcher(text);
        float last = Float.NaN;
        while (m.find()) {
            try { last = Float.parseFloat(m.group(1)); }
            catch (Exception ignored) {}
        }
        return last;
    }

    static final class Sample {
        long timestamp;
        float availability = Float.NaN;
        float latencyMs = Float.NaN;
        float diskPercent = Float.NaN;
        float speedMbps = Float.NaN;
    }
}
