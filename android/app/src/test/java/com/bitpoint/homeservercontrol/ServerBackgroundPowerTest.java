package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Checks Android power ownership. Virtual screen-off time is not a physical Doze test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class})
public class ServerBackgroundPowerTest {
    private android.app.Application app;
    private int port;
    private org.robolectric.shadows.ShadowWifiManager wifi;
    @Before public void setup()throws Exception {
        new ServerLifecycleTest().setup();
        app=RuntimeEnvironment.getApplication();
        port=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getInt("port",0);
        wifi=Shadows.shadowOf(app.getSystemService(WifiManager.class));
        assertEquals(0,wifi.getActiveLockCount());
    }
    private void await(java.util.function.BooleanSupplier condition)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean() && System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        assertTrue(EmbeddedServerService.status+" / "+EmbeddedServerService.diagnostic,condition.getAsBoolean());
    }
    private void cleanup(EmbeddedServerService service)throws Exception {
        ExecutorService worker=ReflectionHelpers.getField(service,"worker");assertTrue(worker.awaitTermination(8,TimeUnit.SECONDS));
    }
    @Test public void screenOffServerRetainsWifiAndCpuAcrossRenewalThenReleasesBoth()throws Exception {
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();
        PowerManager.WakeLock cpu=null;
        try {
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            cpu=ReflectionHelpers.getField(service,"wake");assertTrue(cpu.isHeld());
            assertEquals("Listening server must hold Wi-Fi even before a file is selected",1,wifi.getActiveLockCount());
            var key=SshKeys.generate();service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE).putExtra("public_key",key.getString(SshKeys.PUBLIC)),0,2);
            await(()->!EmbeddedServerService.waitingForClient);
            try(var files=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(key.getString(SshKeys.PRIVATE),""),EmbeddedServerService.fingerprint)) {
                Shadows.shadowOf(app.getSystemService(PowerManager.class)).setIsInteractive(false);
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(16));
                assertTrue(EmbeddedServerService.running);assertTrue("CPU lease renewed while screen off",cpu.isHeld());
                assertEquals(1,wifi.getActiveLockCount());
                byte[] bytes={2,3,5,7};String remote=files.write("/","screen-off.bin",new java.io.ByteArrayInputStream(bytes),bytes.length,(n,total)->true);
                var output=new java.io.ByteArrayOutputStream();files.read(remote,output,(n,total)->true);assertArrayEquals(bytes,output.toByteArray());
            }
        } finally {controller.destroy();cleanup(service);}
        assertNotNull(cpu);assertFalse(cpu.isHeld());assertEquals(0,wifi.getActiveLockCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(20));
        assertFalse(cpu.isHeld());assertEquals("Stopped renewal must not reacquire Wi-Fi",0,wifi.getActiveLockCount());
    }
    @Test public void repeatedStartOwnsOneWifiLockAndRestartDoesNotAccumulateLocks()throws Exception {
        for(int round=0;round<2;round++) {
            var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();
            try {
                service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
                service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,2);
                assertEquals("Only one lock per explicit server lifetime",1,wifi.getActiveLockCount());
            } finally {controller.destroy();cleanup(service);}
            assertEquals(0,wifi.getActiveLockCount());
        }
    }
    @Test public void failedListenerStartDoesNotAcquireWifiLock()throws Exception {
        try(var occupied=new ServerSocket(port)) {
            var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();
            try {
                service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);
                await(()->!EmbeddedServerService.starting&&!EmbeddedServerService.diagnostic.isEmpty());
                assertFalse(EmbeddedServerService.running);assertEquals(0,wifi.getActiveLockCount());
            } finally {controller.destroy();cleanup(service);}
            assertEquals(0,wifi.getActiveLockCount());
        }
    }
}
