package com.bitpoint.homeservercontrol;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** Bounded, process-local telemetry for real SFTP progress, never synthetic traffic. */
final class TransferSpeedStore {
    private static final long STEP_MS = 500;
    private static final int INTERVALS = 120;
    private static final List<Point> points = new ArrayList<>();
    private static Object owner;
    private static float current, peak;
    private static String state = "Передач пока нет";
    private static long lastTime;

    private TransferSpeedStore() {}

    static synchronized void begin(Object transfer) {
        owner = transfer;
        points.clear();
        current = peak = 0;
        state = "Передача";
        lastTime = SystemClock.elapsedRealtime();
        points.add(new Point(lastTime, 0));
    }

    static synchronized void record(Object transfer, double bytesPerSecond) {
        if (owner != transfer || !Double.isFinite(bytesPerSecond) || bytesPerSecond < 0) return;
        current = (float) (bytesPerSecond / (1024.0 * 1024.0));
        peak = Math.max(peak, current);
        long now = SystemClock.elapsedRealtime();
        Point last = points.get(points.size() - 1);
        if (now - last.time < STEP_MS) {
            points.set(points.size() - 1, new Point(last.time, current));
        } else {
            points.add(new Point(now, current));
            if (points.size() > INTERVALS + 1) points.remove(0);
        }
        lastTime = now;
    }

    static synchronized void end(Object transfer, String result) {
        if (transfer == null || owner != transfer) return;
        owner = null;
        current = 0;
        state = result;
    }

    static synchronized Snapshot snapshot() {
        long now = owner == null ? lastTime : SystemClock.elapsedRealtime();
        List<Float> values = new ArrayList<>(INTERVALS + 1);
        for (int i = 0; i <= INTERVALS; i++) values.add(Float.NaN);
        for (Point point : points) {
            int index = INTERVALS - (int) ((now - point.time) / STEP_MS);
            if (index >= 0 && index <= INTERVALS) values.set(index, point.speed);
        }
        return new Snapshot(values, current, peak, owner != null, state);
    }

    static final class Snapshot {
        final List<Float> values;
        final float current, peak;
        final boolean active;
        final String state;
        Snapshot(List<Float> values, float current, float peak, boolean active, String state) {
            this.values = values;
            this.current = current;
            this.peak = peak;
            this.active = active;
            this.state = state;
        }
    }

    private static final class Point {
        final long time;
        final float speed;
        Point(long time, float speed) { this.time = time; this.speed = speed; }
    }
}
