package com.bitpoint.homeservercontrol;

import android.content.Intent;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class NetworkSpeedAccountingTest {
    @Test public void localPublicationCompletesWithoutInventingAnySftpTraffic()throws Exception{
        DurableTransferTest fixture=new DurableTransferTest();fixture.setup();
        byte[] bytes=new byte[256*1024];new java.util.Random(37).nextBytes(bytes);
        String name="local-metrics-"+java.util.UUID.randomUUID()+".bin";
        ReflectionHelpers.callInstanceMethod(fixture,"source",from(String.class,name),from(byte[].class,bytes));
        Intent request=ReflectionHelpers.callInstanceMethod(fixture,"job",from(String.class,name),from(boolean.class,false),from(String.class,TransferJournal.KEEP),from(boolean.class,true));
        var service=Robolectric.buildService(TransferForegroundService.class).create();
        try{
            service.get().onStartCommand(request,0,1);
            ExecutorService worker=ReflectionHelpers.getField(service.get(),"worker");worker.submit(()->{}).get(10,TimeUnit.SECONDS);
            assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            TransferRecoveryTest.Documents destination=ReflectionHelpers.getField(fixture,"provider");
            assertArrayEquals(bytes,Files.readAllBytes(new java.io.File(destination.root,name).toPath()));
            assertEquals("Local staging/publication must not appear as SSH/SFTP throughput",0,TransferSpeedStore.snapshot().jobPeak,0);
        }finally{service.destroy();}
    }
}
