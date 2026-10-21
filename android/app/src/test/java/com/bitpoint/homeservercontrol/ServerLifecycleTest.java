package com.bitpoint.homeservercontrol;

import android.content.*;
import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Real SSH listener with controlled identity/receive resources at Android lifecycle boundaries. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class})
public class ServerLifecycleTest {
    @Implements(value=EmbeddedServerIdentity.class,isInAndroidSdk=false)
    public static class Identity {
        static KeyPair pair;
        @Implementation protected static KeyPair loadOrCreate(Context c){return pair;}
    }
    @Implements(value=AutoReceive.class,isInAndroidSdk=false)
    public static class Receive {
        static AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger();
        static volatile boolean hold;static CountDownLatch entered,release;
        private boolean disposed;
        @Implementation protected void __constructor__(Context c){
            created.incrementAndGet();entered.countDown();
            if(hold)try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Receive constructor timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        }
        @Implementation protected void retry(){}
        @Implementation protected void received(String path){}
        @Implementation protected synchronized void close(){if(!disposed){disposed=true;closed.incrementAndGet();}}
    }
    private android.app.Application app;private int port;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);Identity.pair=generator.generateKeyPair();
        try(ServerSocket pick=new ServerSocket(0)){port=pick.getLocalPort();}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putInt("port",port).putString("user","localls").commit();
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.waitingForClient=false;EmbeddedServerService.status="Сервер выключен";
        Receive.created.set(0);Receive.closed.set(0);Receive.hold=false;Receive.entered=new CountDownLatch(1);Receive.release=new CountDownLatch(1);
    }
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!condition.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(EmbeddedServerService.status+" / "+EmbeddedServerService.diagnostic,condition.getAsBoolean());}
    private void cleanup(EmbeddedServerService s)throws Exception{ExecutorService worker=ReflectionHelpers.getField(s,"worker");assertTrue(worker.awaitTermination(8,TimeUnit.SECONDS));}
    private boolean open(){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),200);return true;}catch(Exception e){return false;}}
    @Test public void startRepeatedStartAndStopReleasePortAndReceiveResources()throws Exception{
        var c=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=c.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);assertTrue(open());assertTrue(EmbeddedServerService.waitingForClient);
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,2);assertEquals(1,Receive.created.get());
        }finally{c.destroy();cleanup(service);}
        assertFalse(open());assertFalse(EmbeddedServerService.running);assertFalse(EmbeddedServerService.waitingForClient);assertEquals(Receive.created.get(),Receive.closed.get());
    }
    @Test public void stopDuringReceiverCreationCannotLeakQueueOrPublishLateWaitingState()throws Exception{
        Receive.hold=true;var c=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=c.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);assertTrue(Receive.entered.await(5,TimeUnit.SECONDS));c.destroy();
        }finally{Receive.release.countDown();cleanup(service);}
        assertFalse(open());assertFalse("Destroyed startup restored waiting state",EmbeddedServerService.waitingForClient);
        assertEquals("Receiver created during destruction was never closed",Receive.created.get(),Receive.closed.get());assertFalse(EmbeddedServerService.running);assertFalse(EmbeddedServerService.starting);
    }
    @Test public void bindFailureDoesNotLeaveReceiveQueueOpenAfterServiceDestroy()throws Exception{
        try(ServerSocket occupied=new ServerSocket(port)){
            var c=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=c.get();
            try{service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->!EmbeddedServerService.starting && !EmbeddedServerService.diagnostic.isEmpty());assertFalse(EmbeddedServerService.running);}
            finally{c.destroy();cleanup(service);}
            assertEquals(Receive.created.get(),Receive.closed.get());
        }
    }
    @Test public void approvedClientCanTransferAndSecondApprovalCannotReplaceIt()throws Exception{
        var c=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=c.get();var client=SshKeys.generate();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE).putExtra("public_key",client.getString(SshKeys.PUBLIC)),0,2);await(()->!EmbeddedServerService.waitingForClient);
            String approved=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key","");assertEquals(EmbeddedSftpServer.normalizeAuthorizedKey(client.getString(SshKeys.PUBLIC)),approved);
            byte[] data={2,3,5,7};
            try(SftpClient.TreeSession files=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(client.getString(SshKeys.PRIVATE),""),EmbeddedServerService.fingerprint)){
                String path=files.write("/","lifecycle.bin",new java.io.ByteArrayInputStream(data),data.length,(n,total)->true);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();files.read(path,out,(n,total)->true);assertArrayEquals(data,out.toByteArray());
            }
            service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE).putExtra("public_key",SshKeys.generate().getString(SshKeys.PUBLIC)),0,3);
            assertEquals(approved,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key",""));assertTrue(EmbeddedServerService.running);
        }finally{c.destroy();cleanup(service);}
        assertFalse(open());assertEquals(Receive.created.get(),Receive.closed.get());
    }
    @Test public void immediateRestartWaitsForPreviousListenerCleanup()throws Exception{
        var first=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService old=first.get();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);org.robolectric.android.controller.ServiceController<EmbeddedServerService> second=null;
        try{
            old.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            ExecutorService before=ReflectionHelpers.getField(old,"worker");before.execute(()->{entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
            first.destroy();assertTrue("Old listener is deliberately still closing",open());
            second=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService next=second.get();next.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);
            ExecutorService after=ReflectionHelpers.getField(next,"worker");java.util.concurrent.Future<?> startupFinished=after.submit(()->{});
            try{startupFinished.get(200,TimeUnit.MILLISECONDS);fail("New startup overtook previous cleanup");}catch(java.util.concurrent.TimeoutException expected){}
            release.countDown();cleanup(old);await(()->EmbeddedServerService.running && !EmbeddedServerService.starting);assertTrue(open());assertTrue(EmbeddedServerService.diagnostic.isEmpty());
        }finally{release.countDown();if(second!=null){EmbeddedServerService next=second.get();second.destroy();cleanup(next);}if(!((ExecutorService)ReflectionHelpers.getField(old,"worker")).isShutdown())first.destroy();cleanup(old);}
        assertFalse(open());assertEquals(Receive.created.get(),Receive.closed.get());
    }

}
