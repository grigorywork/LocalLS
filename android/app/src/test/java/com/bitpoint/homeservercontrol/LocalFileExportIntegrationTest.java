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
}
