package com.bitpoint.homeservercontrol;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ServerMonitorJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> currentTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        currentTask = executor.submit(() -> {
            try {
                ConfigMigrator.migrate(this);
                SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
                if (!prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false)) return;

                String host = ConnectionSelector.selectedHost(this);
                int port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);

                if (!TextUtils.isEmpty(host) && port > 0) {
                    boolean online = tcpReachable(host, port);
                    if (!online && ConnectionSelector.autoFallback(this)) {
                        String alternate = ConnectionSelector.alternateHost(this);
                        if (!TextUtils.isEmpty(alternate) && !alternate.equals(host) && tcpReachable(alternate, port)) {
                            host = alternate;
                            online = true;
                        }
                    }
                    if (online) ConnectionSelector.rememberWorkingHost(this, host);
                    MetricHistoryStore.addAvailability(this, online);
                    boolean hasState = prefs.getBoolean(ServerConfig.KEY_MONITOR_HAS_STATE, false);
                    boolean previous = prefs.getBoolean(ServerConfig.KEY_MONITOR_LAST_ONLINE, false);
                    if ((hasState && previous != online) || (!hasState && !online)) {
                        NotificationHelper.postState(this, online, host, port);
                    }
                    prefs.edit()
                            .putBoolean(ServerConfig.KEY_MONITOR_HAS_STATE, true)
                            .putBoolean(ServerConfig.KEY_MONITOR_LAST_ONLINE, online)
                            .apply();
                }
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (currentTask != null) currentTask.cancel(true);
        return true;
    }

    @Override
    public void onDestroy() {
        if (currentTask != null) currentTask.cancel(true);
        executor.shutdownNow();
        super.onDestroy();
    }

    private static boolean tcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 4000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
