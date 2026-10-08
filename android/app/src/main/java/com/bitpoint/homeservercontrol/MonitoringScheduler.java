package com.bitpoint.homeservercontrol;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class MonitoringScheduler {
    private static final int JOB_ID = 8032;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    private MonitoringScheduler() {}

    static boolean setEnabled(Context context, boolean enabled) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;
        if (!enabled) {
            scheduler.cancel(JOB_ID);
            return true;
        }

        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, ServerMonitorJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }
}
