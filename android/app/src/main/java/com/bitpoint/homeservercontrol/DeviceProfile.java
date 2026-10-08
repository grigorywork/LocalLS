package com.bitpoint.homeservercontrol;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

final class DeviceProfile {
    private DeviceProfile() {}

    static boolean isLowRam(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return manager.isLowRamDevice();
        }
        return false;
    }

    static long totalRamMb(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return 0L;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.totalMem <= 0L ? 0L : info.totalMem / (1024L * 1024L);
    }

    static String summary(Context context) {
        String manufacturer = safe(Build.MANUFACTURER);
        String model = safe(Build.MODEL);
        long ram = totalRamMb(context);
        StringBuilder out = new StringBuilder();
        out.append(manufacturer).append(' ').append(model);
        if (ram > 0) out.append(" • RAM ~").append(ram).append(" МБ");
        if (isLowRam(context)) out.append(" • low-RAM режим Android");
        return out.toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
