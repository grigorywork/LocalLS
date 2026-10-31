package com.bitpoint.homeservercontrol;

import android.content.Intent;
import android.os.SystemClock;
import java.time.Duration;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exercise the actual service payload callbacks, not a substitute speed calculator. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SpeedContinuityTest {
    private org.robolectric.android.controller.ServiceController<TransferForegroundService> controller;
    private Object token,job;
    private java.lang.reflect.Method progress;
    private java.lang.reflect.Method transferred;
    private long previousCallback;
    private static final long MIB=1024*1024;
    private static long fixtureTime;
    @Before public void setup()throws Exception{
        // A previous test's process-local history must age out, as it would in production.
        long now=SystemClock.elapsedRealtime();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(Math.max(61000,fixtureTime+61000-now)));
        fixtureTime=SystemClock.elapsedRealtime();
        controller=Robolectric.buildService(TransferForegroundService.class).create();
        next();
    }
    private void next()throws Exception{
        token=new Object();TransferSpeedStore.begin(token);
        Class<?> type=Class.forName(TransferForegroundService.class.getName()+"$Job");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        job=constructor.newInstance(controller.get(),new Intent(),0,token);
        progress=type.getDeclaredMethod("progress",long.class,long.class);progress.setAccessible(true);
        transferred=type.getDeclaredMethod("transferred",long.class);transferred.setAccessible(true);previousCallback=0;
    }
    private void tick(long ms,long bytes)throws Exception{
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(ms));
        transferred.invoke(job,bytes>=previousCallback?bytes-previousCallback:bytes);previousCallback=bytes;
        progress.invoke(job,bytes,100*MIB);
    }
    @After public void cleanup(){TransferSpeedStore.end(token,"Завершено");controller.destroy();fixtureTime=SystemClock.elapsedRealtime();}
    @Test public void queueBoundaryKeepsEarlierTransferHistory()throws Exception{
        tick(500,MIB);tick(500,2*MIB);tick(500,3*MIB);
        TransferSpeedStore.end(token,"Завершено");
        long before=TransferSpeedStore.snapshot().values.stream().filter(v->!Float.isNaN(v)&&v>0).count();
        assertTrue(before>0);next();
        long after=TransferSpeedStore.snapshot().values.stream().filter(v->!Float.isNaN(v)&&v>0).count();
        assertEquals("Starting the next queued file erased measured speed history",before,after);
        assertEquals("The new job must not inherit the previous job's peak in saved history",0,TransferSpeedStore.snapshot().jobPeak,0);
        assertTrue("The chart must still retain the earlier peak",TransferSpeedStore.snapshot().peak>0);
    }
    @Test public void jitteredCallbacksDoNotLeaveHolesInsideAnObservedTransfer()throws Exception{
        tick(0,0);for(int i=1;i<=12;i++)tick(600,i*MIB);
        List<Float> values=TransferSpeedStore.snapshot().values;
        int first=0;while(first<values.size()&&(Float.isNaN(values.get(first))||values.get(first)==0))first++;
        assertTrue(first<values.size()-3);
        // The open rightmost bucket is allowed to be incomplete.
        for(int i=first;i<values.size()-1;i++)assertFalse("Missing speed sample in a live interval: "+i,Float.isNaN(values.get(i)));
    }
    @Test public void callbacksInSameMillisecondAreIncludedInMeasuredRate()throws Exception{
        tick(0,0);tick(500,MIB);tick(0,2*MIB);tick(500,3*MIB);tick(500,4*MIB);
        float peak=TransferSpeedStore.snapshot().peak;
        assertTrue("Same-timestamp bytes were discarded: "+peak,peak>=3.9f&&peak<=4.1f);
    }
    @Test public void stalledTransferDoesNotKeepShowingItsOldNonzeroSpeed()throws Exception{
        tick(0,0);tick(500,MIB);tick(500,2*MIB);
        assertTrue(TransferSpeedStore.snapshot().current>0);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(3));
        assertEquals("An idle connection still reports its old speed",0,TransferSpeedStore.snapshot().current,0);
    }
}
