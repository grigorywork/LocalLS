package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class TransferRecoveryTest {
    @org.robolectric.annotation.Implements(value=TransferLogStore.class,isInAndroidSdk=false)
    public static class BlockedHistory {
        static CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        @org.robolectric.annotation.Implementation protected static void add(Context c,String label,String result){
            if(result.startsWith("Ошибка")){entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("History wait timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        }
    }
    private android.app.Application app;
    public static class Documents extends LastModIntegrationTest.Documents {
        boolean holdClose;
        final CountDownLatch closeEntered=new CountDownLatch(1),closeRelease=new CountDownLatch(1);
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            ParcelFileDescriptor original=super.openDocument(id,mode,signal);
            if(!holdClose || !mode.contains("w"))return original;
            return new ParcelFileDescriptor(original){
                private boolean waited;
                @Override public void close()throws IOException{
                    super.close();
                    if(!waited){waited=true;closeEntered.countDown();try{if(!closeRelease.await(8,TimeUnit.SECONDS))throw new IOException("Close wait timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}
                }
            };
        }
    }
    private Documents provider;
    private Uri tree;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();provider=new Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"recovery-").toFile();
        ProviderInfo info=new ProviderInfo();info.authority="recovery.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);
        ShadowContentResolver.registerProviderInternal(info.authority,provider);tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");
    }
    private File source(String name,byte[] bytes)throws Exception{File root=EmbeddedServerService.share(app);root.mkdirs();File f=new File(root,name);Files.write(f.toPath(),bytes);return f;}
    private Intent save(String name,long size){
        Intent i=new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.SAVE).putExtra("request_id",UUID.randomUUID().toString());
        ArrayList<Bundle> nodes=new ArrayList<>();nodes.add(new FileNodes.Node("share",name,name,false,size).bundle());i.putParcelableArrayListExtra("sources",nodes);
        return i.putExtra("destination",new FileNodes.Node("doc",DocumentFiles.root(tree).toString(),"Документы",true,0).bundle());
    }
    private void await(java.util.function.BooleanSupplier condition)throws Exception{
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean() && System.nanoTime()<limit){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        assertTrue(TransferForegroundService.state.text+"\n"+TransferForegroundService.state.diagnostic,condition.getAsBoolean());
    }
    @Test public void cancellationWhileOpeningDestinationDoesNotPublishEmptyFile()throws Exception{
        String name="empty-"+System.nanoTime()+".bin";File original=source(name,new byte[0]);provider.hold=true;
        var c=Robolectric.buildService(TransferForegroundService.class).create();try{
            c.get().onStartCommand(save(name,0),0,1);assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
            c.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CANCEL),0,2);provider.release.countDown();
            await(()->!TransferForegroundService.state.active && TransferForegroundService.state.text.contains("отменена"));
            assertTrue(original.isFile());assertEquals(0,original.length());assertFalse("Cancelled empty copy was published",new File(provider.root,name).exists());
        }finally{provider.release.countDown();c.destroy();}
    }
    @Test public void cancellationWhileClosingDestinationRemovesOnlyNewCopy()throws Exception{
        String name="close-"+System.nanoTime()+".bin";File original=source(name,new byte[]{1,6,9});provider.holdClose=true;
        var c=Robolectric.buildService(TransferForegroundService.class).create();try{
            c.get().onStartCommand(save(name,3),0,1);boolean closing=provider.closeEntered.await(5,TimeUnit.SECONDS);assertTrue(TransferForegroundService.state.diagnostic,closing);
            c.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CANCEL),0,2);provider.closeRelease.countDown();
            await(()->!TransferForegroundService.state.active && TransferForegroundService.state.text.contains("отменена"));
            assertArrayEquals(new byte[]{1,6,9},Files.readAllBytes(original.toPath()));assertFalse(new File(provider.root,name).exists());
        }finally{provider.closeRelease.countDown();c.destroy();}
    }
    @Test public void cancellingIdleServiceDoesNotLeaveStoppingMessage(){
        var c=Robolectric.buildService(TransferForegroundService.class).create();try{
            c.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CANCEL),0,1);
            assertFalse(TransferForegroundService.state.active);assertFalse(TransferForegroundService.state.error);assertEquals(0,TransferForegroundService.state.queued);assertTrue(TransferForegroundService.state.text.contains("пуста"));
        }finally{c.destroy();}
    }
    @Test public void unrelatedSuccessDoesNotEraseFailedJobAndRetryClearsOnlyThatFailure()throws Exception{
        String bad="missing-"+System.nanoTime()+".bin",good="good-"+System.nanoTime()+".bin";source(good,new byte[]{3,4});
        var c=Robolectric.buildService(TransferForegroundService.class).create();try{
            c.get().onStartCommand(save(bad,1),0,1);await(()->!TransferForegroundService.state.active && TransferForegroundService.canRetry());
            c.get().onStartCommand(save(good,2),0,2);await(()->!TransferForegroundService.state.active && new File(provider.root,good).length()==2);
            assertTrue("Successful second job erased retry of the first",TransferForegroundService.canRetry());
            source(bad,new byte[]{7});TransferForegroundService.retry(app);Intent retry=Shadows.shadowOf(app).getNextStartedService();assertNotNull(retry);c.get().onStartCommand(retry,0,3);
            await(()->!TransferForegroundService.state.active && new File(provider.root,bad).length()==1);assertFalse(TransferForegroundService.canRetry());assertFalse(TransferForegroundService.state.error);
            assertArrayEquals(new byte[]{7},Files.readAllBytes(new File(provider.root,bad).toPath()));assertArrayEquals(new byte[]{3,4},Files.readAllBytes(new File(provider.root,good).toPath()));
        }finally{c.destroy();}
    }
    @Test @Config(shadows=BlockedHistory.class)
    public void retryIsAcceptedAsSoonAsFailureIsVisibleEvenIfHistoryIsStillFinishing()throws Exception{
        String name="history-"+System.nanoTime()+".bin";var c=Robolectric.buildService(TransferForegroundService.class).create();
        try{
            c.get().onStartCommand(save(name,1),0,1);assertTrue(BlockedHistory.entered.await(5,TimeUnit.SECONDS));assertTrue(TransferForegroundService.canRetry());
            source(name,new byte[]{9});TransferForegroundService.retry(app);Intent retry=Shadows.shadowOf(app).getNextStartedService();assertNotNull(retry);c.get().onStartCommand(retry,0,2);
            BlockedHistory.release.countDown();await(()->!TransferForegroundService.state.active && new File(provider.root,name).length()==1);
            assertFalse(TransferForegroundService.canRetry());assertArrayEquals(new byte[]{9},Files.readAllBytes(new File(provider.root,name).toPath()));
        }finally{BlockedHistory.release.countDown();c.destroy();}
    }
    @Test public void repeatedRetryDoesNotQueueOrReplayTheSameSuccessfulCopy()throws Exception{
        String name="retry-"+System.nanoTime()+".bin";Intent original=save(name,3);
        var c=Robolectric.buildService(TransferForegroundService.class).create();try{
            c.get().onStartCommand(original,0,1);await(()->!TransferForegroundService.state.active && TransferForegroundService.canRetry());source(name,new byte[]{2,5,8});provider.hold=true;
            TransferForegroundService.retry(app);Intent first=Shadows.shadowOf(app).getNextStartedService();assertNotNull(first);c.get().onStartCommand(first,0,2);assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
            TransferForegroundService.retry(app);Intent second=Shadows.shadowOf(app).getNextStartedService();assertNotNull(second);c.get().onStartCommand(second,0,3);provider.release.countDown();
            await(()->!TransferForegroundService.state.active && TransferForegroundService.state.queued==0);assertEquals("Retry queued duplicate copies",1,provider.root.listFiles().length);
            c.get().onStartCommand(new Intent(first),0,4);await(()->!TransferForegroundService.state.active && TransferForegroundService.state.queued==0);assertEquals("Completed request was replayed",1,provider.root.listFiles().length);
            assertArrayEquals(new byte[]{2,5,8},Files.readAllBytes(new File(provider.root,name).toPath()));
        }finally{provider.release.countDown();c.destroy();}
    }
}
