package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

/**
 * Keeps CPU and Wi-Fi awake only while the in-app SFTP queue is active.
 * This is intentionally scoped to transfers and never used as a permanent daemon lock.
 */
final class TransferPowerGuard {
    private final PowerManager.WakeLock wakeLock;
    private final WifiManager.WifiLock wifiLock;
    private boolean held;
    private long lastAcquireMs;

    TransferPowerGuard(Context context) {
        Context app = context.getApplicationContext();
        PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        WifiManager wifi = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);

        PowerManager.WakeLock wake = null;
        WifiManager.WifiLock wlan = null;
        try {
            if (power != null) {
                wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "HomeServerControl:SftpTransfer");
                wake.setReferenceCounted(false);
            }
        } catch (Exception ignored) {
        }
        try {
            if (wifi != null) {
                wlan = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "HomeServerControl:SftpTransfer");
                wlan.setReferenceCounted(false);
            }
        } catch (Exception ignored) {
        }
        wakeLock = wake;
        wifiLock = wlan;
    }

    synchronized void acquire() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (held && now - lastAcquireMs < 5 * 60 * 1000L) return;
        try {
            if (wakeLock != null) wakeLock.acquire(10 * 60 * 1000L);
        } catch (Exception ignored) {
        }
        try {
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception ignored) {
        }
        held = true;
        lastAcquireMs = now;
    }

    synchronized void release() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        held = false;
    }
}
