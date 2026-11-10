package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends BaseActivity {
    private MetricChartView availabilityChart, latencyChart, diskChart, speedChart;
    private TextView availabilitySummary, latencySummary, diskSummary, speedSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        availabilityChart = findViewById(R.id.availabilityChart);
        latencyChart = findViewById(R.id.latencyChart);
        diskChart = findViewById(R.id.diskChart);
        speedChart = findViewById(R.id.speedChart);
        availabilitySummary = findViewById(R.id.availabilitySummary);
        latencySummary = findViewById(R.id.latencySummary);
        diskSummary = findViewById(R.id.diskSummary);
        speedSummary = findViewById(R.id.speedSummary);

        findViewById(R.id.historyBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.historyClearButton).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(AppText.tr("Очистить графики?"))
                .setMessage(AppText.tr("Будет удалена только локальная история мониторинга в приложении."))
                .setNegativeButton(AppText.tr("Отмена"), null)
                .setPositiveButton(AppText.tr("Очистить"), (d, w) -> {
                    MetricHistoryStore.clear(this);
                    render();
                })
                .show());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        List<MetricHistoryStore.Sample> samples = MetricHistoryStore.read(this);
        List<Float> availability = new ArrayList<>();
        List<Float> latency = new ArrayList<>();
        List<Float> disk = new ArrayList<>();
        List<Float> speed = new ArrayList<>();
        for (MetricHistoryStore.Sample s : samples) {
            if (!Float.isNaN(s.availability)) availability.add(s.availability);
            if (!Float.isNaN(s.latencyMs)) latency.add(s.latencyMs);
            if (!Float.isNaN(s.diskPercent)) disk.add(s.diskPercent);
            if (!Float.isNaN(s.speedMbps)) speed.add(s.speedMbps);
        }
        availabilityChart.setValues(availability, 0f, 100f);
        latencyChart.setValues(latency, Float.NaN, Float.NaN);
        diskChart.setValues(disk, 0f, 100f);
        speedChart.setValues(speed, 0f, Float.NaN);

        availabilitySummary.setText(summary(availability, "%", true));
        latencySummary.setText(summary(latency, AppText.tr(" мс"), false));
        diskSummary.setText(summary(disk, "%", false));
        speedSummary.setText(summary(speed, AppText.tr(" Мбит/с"), false));
    }

    private String summary(List<Float> values, String suffix, boolean averageOnly) {
        float min = Float.NaN, max = Float.NaN, sum = 0f;
        int count = 0;
        for (Float v : values) {
            if (v == null || Float.isNaN(v)) continue;
            min = Float.isNaN(min) ? v : Math.min(min, v);
            max = Float.isNaN(max) ? v : Math.max(max, v);
            sum += v;
            count++;
        }
        if (count == 0) return AppText.tr("Нет данных");
        float avg = sum / count;
        if (averageOnly) return String.format(Locale.getDefault(), AppText.tr("Среднее: %.0f%s • точек: %d"), avg, suffix, count);
        return String.format(Locale.getDefault(), AppText.tr("Мин: %.1f%s • среднее: %.1f%s • макс: %.1f%s"), min, suffix, avg, suffix, max, suffix);
    }
}
