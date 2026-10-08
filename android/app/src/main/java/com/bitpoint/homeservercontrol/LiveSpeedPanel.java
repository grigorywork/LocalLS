package com.bitpoint.homeservercontrol;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.Locale;

/** Updates a visible chart twice a second, with no network calls or background polling. */
final class LiveSpeedPanel {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MetricChartView chart;
    private final TextView summary;
    private boolean running;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!running) return;
            TransferSpeedStore.Snapshot sample = TransferSpeedStore.snapshot();
            chart.setValues(sample.values, 0, Math.max(1f, sample.peak * 1.15f));
            summary.setText(sample.active
                    ? String.format(Locale.getDefault(), "Сейчас %.2f МБ/с • пик %.2f", sample.current, sample.peak)
                    : sample.peak > 0
                        ? String.format(Locale.getDefault(), "%s • пик %.2f МБ/с", sample.state, sample.peak)
                        : "Передач пока нет • МБ/с");
            handler.postDelayed(this, 500);
        }
    };

    LiveSpeedPanel(MetricChartView chart, TextView summary) {
        this.chart = chart;
        this.summary = summary;
        chart.setLabels("МБ/с", "60 с");
    }

    void start() {
        if (running) return;
        running = true;
        handler.post(refresh);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(refresh);
    }
}
