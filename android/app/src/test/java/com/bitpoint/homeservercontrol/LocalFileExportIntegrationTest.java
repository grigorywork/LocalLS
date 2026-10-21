package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LocalFileExportIntegrationTest {
    public static class Downloads extends ContentProvider {
        File file;boolean deny,hold,publishDenied;int inserted,deleted,published;ContentValues insertion;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        @Override public boolean onCreate(){return true;}
        @Override public Uri insert(Uri uri,ContentValues values){inserted++;insertion=new ContentValues(values);return Uri.parse("content://media/external/downloads/41");}
        @Override public int update(Uri uri,ContentValues values,String where,String[] args){if(publishDenied)return 0;published++;assertEquals(Integer.valueOf(0),values.getAsInteger(MediaStore.MediaColumns.IS_PENDING));return 1;}
        @Override public int delete(Uri uri,String where,String[] args){deleted++;file.delete();return 1;}
        @Override public Cursor query(Uri uri,String[] columns,String where,String[] args,String order){return null;}
        @Override public String getType(Uri uri){return "application/octet-stream";}
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
            entered.countDown();if(deny)throw new SecurityException("private-name.bin content://private-uri");
            if(hold)try{if(!release.await(5,TimeUnit.SECONDS))throw new FileNotFoundException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException();}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
        }
    }
    private Downloads provider;private Context app;
    @Before public void setup()throws Exception {
        app=RuntimeEnvironment.getApplication();provider=new Downloads();provider.file=new File(app.getCacheDir(),"export-output.bin");
        ProviderInfo info=new ProviderInfo();info.authority="media";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal("media",provider);
        LocalFileExportService.state=new LocalFileExportService.State(false,false,0,0,"","");
    }
    private File source(long size)throws Exception {
        File root=EmbeddedServerService.share(app);root.mkdirs();File file=new File(root,"Видео 60 МБ.bin");byte[] block=new byte[65536];new java.util.Random(51).nextBytes(block);
        try(OutputStream output=new FileOutputStream(file)){for(long left=size;left>0;left-=Math.min(left,block.length))output.write(block,0,(int)Math.min(left,block.length));}return file;
    }
    private byte[] digest(File file)throws Exception {MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)hash.update(b,0,n);}return hash.digest();}
    private void drained(LocalFileExportService service)throws Exception {
        ExecutorService worker=org.robolectric.util.ReflectionHelpers.getField(service,"worker");worker.submit(()->{}).get(8,TimeUnit.SECONDS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private Intent request(File file){LocalFileExportService.start(app,file.getName(),null,0);Intent request=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(request);return request;}
    @Test public void sixtyMiBExportContinuesAfterBrowserActivityIsDestroyed()throws Exception {
        File file=source(60L*1024*1024+17);provider.hold=true;
        var screen=Robolectric.buildActivity(EmbeddedFilesActivity.class).create();
        var controller=Robolectric.buildService(LocalFileExportService.class).create();
        try {
            LocalFileExportService.start(screen.get(),file.getName(),null,0);
            Intent request=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();
            controller.get().onStartCommand(request,0,1);assertTrue(provider.entered.await(3,TimeUnit.SECONDS));
            screen.destroy();provider.release.countDown();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(LocalFileExportService.state.active && System.nanoTime()<until){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
            assertTrue(LocalFileExportService.state.text,LocalFileExportService.state.success);
            assertEquals(1,provider.inserted);assertEquals(1,provider.published);assertEquals(0,provider.deleted);
            assertEquals(Integer.valueOf(1),provider.insertion.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
            assertEquals("Download/LocalLS",provider.insertion.getAsString(MediaStore.MediaColumns.RELATIVE_PATH));
            assertEquals(file.length(),provider.file.length());assertArrayEquals(digest(file),digest(provider.file));assertTrue(file.exists());
        } finally{provider.release.countDown();controller.destroy();}
    }
    @Test public void permissionFailureHasSafeDiagnosticsAndCleansOnlyNewDownload()throws Exception {
        File file=source(17);provider.deny=true;
        LocalExportDestination destination=new LocalExportDestination(app,file.getName(),"application/octet-stream",null);
        try{LocalFileExporter.copy(file.getParentFile(),file.getName(),destination,(c,t)->{});fail();}
        catch(LocalFileExporter.Failure error) {
            assertTrue(LocalExportFailure.hint(error.stage,error).contains("Нет разрешения"));
            String report=LocalExportFailure.report(error.stage,error);assertTrue(report.contains("DESTINATION"));assertTrue(report.contains("SecurityException"));
            assertFalse(report.contains("content://"));assertFalse(report.contains("private-name"));
        }
        assertEquals(1,provider.deleted);assertEquals(0,provider.published);assertTrue(file.exists());
    }
    @Test public void selectedDocumentIsNeverDeletedAndFailedPublicationIsNotSuccess()throws Exception {
        File file=source(5);provider.deny=true;
        try{LocalFileExporter.copy(file.getParentFile(),file.getName(),new LocalExportDestination(app,file.getName(),"application/octet-stream",Uri.parse("content://media/external/downloads/41")),(c,t)->{});fail();}
        catch(LocalFileExporter.Failure expected){assertEquals("DESTINATION",expected.stage);}
        assertEquals(0,provider.deleted);assertEquals(0,provider.inserted);
        provider.deny=false;provider.publishDenied=true;
        try{LocalFileExporter.copy(file.getParentFile(),file.getName(),new LocalExportDestination(app,file.getName(),"application/octet-stream",null),(c,t)->{});fail();}
        catch(LocalFileExporter.Failure expected){assertEquals("PUBLISH",expected.stage);}
        assertEquals(1,provider.deleted);assertEquals(0,provider.published);assertEquals(5,file.length());
    }
    @Test public void duplicateIntentDuringCopyDoesNotCreateSecondDownload()throws Exception {
        File file=source(17);provider.hold=true;var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            Intent request=request(file);c.get().onStartCommand(request,0,1);assertTrue(provider.entered.await(3,TimeUnit.SECONDS));c.get().onStartCommand(new Intent(request),0,2);provider.release.countDown();drained(c.get());
            assertTrue(LocalFileExportService.state.text,LocalFileExportService.state.success);assertEquals("Duplicate command created another download",1,provider.inserted);assertEquals(1,provider.published);assertEquals(2,Shadows.shadowOf(c.get()).getStopSelfId());assertArrayEquals(digest(file),digest(provider.file));
        }finally{provider.release.countDown();c.destroy();}
    }
    @Test public void completedIntentCannotReplayAfterServiceRecreation()throws Exception {
        File file=source(17);Intent request=request(file);var first=Robolectric.buildService(LocalFileExportService.class).create();try{first.get().onStartCommand(request,0,1);drained(first.get());assertTrue(LocalFileExportService.state.success);}finally{first.destroy();}
        var second=Robolectric.buildService(LocalFileExportService.class).create();try{second.get().onStartCommand(new Intent(request),android.app.Service.START_FLAG_RETRY,1);drained(second.get());assertEquals("Completed request was copied again",1,provider.inserted);assertEquals(1,provider.published);assertTrue(file.isFile());}finally{second.destroy();}
    }
    @Test public void invalidCommandCannotStopAnAlreadyAcceptedExport()throws Exception {
        File file=source(17);provider.hold=true;var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            c.get().onStartCommand(request(file),0,1);assertTrue(provider.entered.await(3,TimeUnit.SECONDS));c.get().onStartCommand(null,0,2);assertTrue("Empty command replaced active export state",LocalFileExportService.state.active);
            provider.release.countDown();drained(c.get());assertTrue(LocalFileExportService.state.text,LocalFileExportService.state.success);assertEquals(1,provider.published);assertEquals(2,Shadows.shadowOf(c.get()).getStopSelfId());
        }finally{provider.release.countDown();c.destroy();}
    }
    @Test public void completedCopyReleasesPowerWithoutWaitingForServiceDestroy()throws Exception {
        File file=source(17);provider.hold=true;var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            c.get().onStartCommand(request(file),0,1);assertTrue(provider.entered.await(3,TimeUnit.SECONDS));android.os.PowerManager.WakeLock wake=org.robolectric.util.ReflectionHelpers.getField(c.get(),"wake");assertNotNull(wake);assertTrue(wake.isHeld());
            provider.release.countDown();drained(c.get());assertTrue(LocalFileExportService.state.success);assertFalse("Completed export still holds CPU lock",wake.isHeld());
        }finally{provider.release.countDown();c.destroy();}
    }
    @Test public void receivedFilesScreenHidesPartialUploadsButKeepsOrdinaryHiddenFiles()throws Exception {
        File root=EmbeddedServerService.share(app);root.mkdirs();java.nio.file.Files.write(new File(root,".localls-upload-pending.part").toPath(),new byte[]{1});java.nio.file.Files.write(new File(root,".notes").toPath(),new byte[]{2});
        try(var c=Robolectric.buildActivity(EmbeddedFilesActivity.class).create()){
            android.widget.ListView list=org.robolectric.util.ReflectionHelpers.getField(c.get(),"list");java.util.List<String> labels=new java.util.ArrayList<>();for(int n=0;n<list.getCount();n++)labels.add(list.getItemAtPosition(n).toString());
            assertTrue(labels.stream().anyMatch(x->x.startsWith(".notes")));assertFalse("Incomplete upload is exposed as a received file",labels.stream().anyMatch(x->x.contains(".localls-upload-")));
        }
    }
    @Test public void deliberateSecondExportUsesNewRequestAndReleasesSamePowerLock()throws Exception {
        File file=source(17);var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            c.get().onStartCommand(request(file),0,1);drained(c.get());assertTrue(LocalFileExportService.state.success);android.os.PowerManager.WakeLock wake=org.robolectric.util.ReflectionHelpers.getField(c.get(),"wake");assertFalse(wake.isHeld());
            c.get().onStartCommand(request(file),0,2);drained(c.get());assertTrue(LocalFileExportService.state.success);assertEquals("A deliberate second copy must remain available",2,provider.inserted);assertEquals(2,provider.published);assertFalse(wake.isHeld());assertArrayEquals(digest(file),digest(provider.file));
        }finally{c.destroy();}
    }
    @Test public void fullStorageDuringWriteAbortsOnlyNewDownloadAndCanBeRetried()throws Exception {
        File file=source(100000);byte[] original=digest(file);LocalExportDestination actual=new LocalExportDestination(app,file.getName(),"application/octet-stream",null);
        LocalFileExporter.Destination full=new LocalFileExporter.Destination(){
            @Override public OutputStream open()throws Exception{return new FilterOutputStream(actual.open()){
                @Override public void write(byte[] bytes,int offset,int length)throws IOException{out.write(bytes,offset,Math.min(3,length));throw new IOException("private-path",new android.system.ErrnoException("write",android.system.OsConstants.ENOSPC));}
            };}
            @Override public void publish()throws Exception{actual.publish();}
            @Override public void abort(){actual.abort();}
        };
        try{LocalFileExporter.copy(file.getParentFile(),file.getName(),full,(done,total)->{});fail("Full-storage write reported success");}catch(LocalFileExporter.Failure error){
            assertEquals("COPY",error.stage);assertTrue(LocalExportFailure.hint(error.stage,error).contains("Недостаточно свободного места"));assertFalse(LocalExportFailure.report(error.stage,error).contains("private-path"));
        }
        assertEquals(1,provider.deleted);assertEquals(0,provider.published);assertFalse(provider.file.exists());assertArrayEquals(original,digest(file));
        assertEquals(file.length(),LocalFileExporter.copy(file.getParentFile(),file.getName(),new LocalExportDestination(app,file.getName(),"application/octet-stream",null),(done,total)->{}));assertEquals(1,provider.published);assertArrayEquals(original,digest(provider.file));assertArrayEquals(original,digest(file));
    }
    @Test public void foregroundDenialCreatesNoDownloadAndSameRequestCanRetry()throws Exception {
        File file=source(17);var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            Intent request=request(file);Shadows.shadowOf(c.get()).setThrowInStartForeground(new IllegalStateException("private-error"));c.get().onStartCommand(request,0,1);
            assertFalse(LocalFileExportService.state.active);assertFalse(LocalFileExportService.state.success);assertTrue(LocalFileExportService.state.diagnostic.contains("FOREGROUND"));assertFalse(LocalFileExportService.state.diagnostic.contains("private-error"));assertEquals(0,provider.inserted);assertTrue(file.isFile());
            Shadows.shadowOf(c.get()).setThrowInStartForeground(null);c.get().onStartCommand(new Intent(request),0,2);drained(c.get());assertTrue(LocalFileExportService.state.text,LocalFileExportService.state.success);assertEquals(1,provider.published);
        }finally{c.destroy();}
    }
    @Test public void androidTimeoutAbortsPendingCopyAndReleasesPowerImmediately()throws Exception {
        File file=source(17);provider.hold=true;var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            c.get().onStartCommand(request(file),0,1);assertTrue(provider.entered.await(3,TimeUnit.SECONDS));android.os.PowerManager.WakeLock wake=org.robolectric.util.ReflectionHelpers.getField(c.get(),"wake");assertTrue(wake.isHeld());
            c.get().onTimeout(1,0);ExecutorService worker=org.robolectric.util.ReflectionHelpers.getField(c.get(),"worker");assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(LocalFileExportService.state.active);assertFalse(LocalFileExportService.state.success);assertTrue(LocalFileExportService.state.diagnostic.contains("STOPPED"));assertFalse(wake.isHeld());assertEquals(0,provider.published);assertEquals(1,provider.deleted);assertEquals(17,file.length());
        }finally{provider.release.countDown();c.destroy();}
    }
    @Test public void lateAndroidTimeoutCannotReplaceAlreadyCompletedSuccess()throws Exception {
        File file=source(17);var c=Robolectric.buildService(LocalFileExportService.class).create();try{
            c.get().onStartCommand(request(file),0,1);drained(c.get());assertTrue(LocalFileExportService.state.success);String text=LocalFileExportService.state.text;
            c.get().onTimeout(1,0);assertTrue("Late timeout replaced successful result",LocalFileExportService.state.success);assertEquals(text,LocalFileExportService.state.text);assertEquals(1,provider.published);assertArrayEquals(digest(file),digest(provider.file));
        }finally{c.destroy();}
    }
}
