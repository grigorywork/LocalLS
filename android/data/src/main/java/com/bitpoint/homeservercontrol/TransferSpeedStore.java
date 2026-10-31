package com.bitpoint.homeservercontrol;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Bounded process-local throughput from actual progress bytes, in fixed half-second buckets. */
final class TransferSpeedStore {
    private static final long STEP_MS = 500;
    private static final int INTERVALS = 120;
    private static final NavigableMap<Long, Double> bytes = new TreeMap<>();
    private static final NavigableMap<Long, Double> queueBytes = new TreeMap<>();
    private static long queueBegin;
    private static Object owner;
    private static long previousBytes, firstBucket = Long.MAX_VALUE;
    private static long jobBucket;
    private static double jobBucketBytes;
    private static float jobPeak;
    private static String state = "Передач пока нет";

    private TransferSpeedStore() {}

    static synchronized void begin(Object transfer) {
        long bucket = SystemClock.elapsedRealtime() / STEP_MS;
        trim(bucket);
        firstBucket = Math.min(firstBucket, bucket);
        owner = transfer;
        queueBegin=bucket;queueBytes.clear();
        previousBytes = 0;
        jobBucket = bucket;
        jobBucketBytes = 0;
        jobPeak = 0;
        state = "Передача";
    }

    static synchronized void recordBytes(Object transfer, long cumulativeBytes) {
        if (transfer == null || owner != transfer || cumulativeBytes < 0) return;
        long bucket = SystemClock.elapsedRealtime() / STEP_MS;
        trim(bucket);
        // Per-file counters restart within a folder job; bytes already received still count.
        long delta = cumulativeBytes >= previousBytes ? cumulativeBytes - previousBytes : cumulativeBytes;
        previousBytes = cumulativeBytes;
        bytes.merge(bucket, (double) delta, Double::sum);
        queueBytes.merge(bucket,(double)delta,Double::sum);queueBytes.headMap(bucket-10,false).clear();
        if(jobBucket!=bucket){jobBucket=bucket;jobBucketBytes=0;}
        jobBucketBytes+=delta;
        jobPeak=Math.max(jobPeak,toRate(jobBucketBytes));
    }

    static synchronized void end(Object transfer, String result) {
        if (transfer == null || owner != transfer) return;
        owner = null;
        state = result;
    }

    static synchronized Snapshot snapshot() {
        long bucket = SystemClock.elapsedRealtime() / STEP_MS;
        trim(bucket);
        List<Float> values = new ArrayList<>(INTERVALS + 1);
        float peak = 0;
        for (long at = bucket - INTERVALS; at <= bucket; at++) {
            float speed = at < firstBucket ? Float.NaN : smoothedSpeed(at);
            values.add(speed);
            if (!Float.isNaN(speed)) peak = Math.max(peak, speed);
        }
        // Only completed intervals drive the numeric label. An unfinished interval's
        // bytes remain in the chart and cannot be lost to a same-millisecond callback.
        float current = owner == null ? 0 : smoothedSpeed(bucket - 1);
        return new Snapshot(values, current, peak, jobPeak, owner != null, state);
    }

    /** Five completed seconds smooth the ETA without retaining a previous job's speed. */
    static synchronized float queueSpeed(){
        if(owner==null)return 0;long end=SystemClock.elapsedRealtime()/STEP_MS-1,start=Math.max(queueBegin,end-9);
        if(end<start)return 0;double total=0;for(long at=start;at<=end;at++)total+=queueBytes.getOrDefault(at,0d);
        return toRate(total/(end-start+1));
    }

    private static float smoothedSpeed(long bucket) {
        return toRate(bytes.getOrDefault(bucket, 0d));
    }
    private static float toRate(double amount){return (float)(amount*1000/STEP_MS/(1024d*1024d));}

    private static void trim(long bucket) {
        bytes.headMap(bucket - INTERVALS, false).clear();
        if (firstBucket != Long.MAX_VALUE) firstBucket = Math.max(firstBucket, bucket - INTERVALS);
    }

    static final class Snapshot {
        final List<Float> values;
        final float current, peak, jobPeak;
        final boolean active;
        final String state;
        Snapshot(List<Float> values, float current, float peak, float jobPeak, boolean active, String state) {
            this.values = values;
            this.current = current;
            this.peak = peak;
            this.jobPeak = jobPeak;
            this.active = active;
            this.state = state;
        }
    }
}
