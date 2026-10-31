package com.bitpoint.homeservercontrol;

import android.app.*;
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
public class QueueActionsTest {
    Application app;TransferJournal journal;
    @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();journal=new TransferJournal(app);journal.clearAll();TransferForegroundService.clearing=false;}
    @Test public void cancelAllIncludesPausedFailedAndQueuedRecordsButPreservesDoneAndSources()throws Exception{
        File source=new File(EmbeddedServerService.share(app),"cancel-original.bin");source.getParentFile().mkdirs();Files.write(source.toPath(),new byte[]{5,0,9});
        List<String> ids=new ArrayList<>();for(String status:new String[]{"paused","failed","queued","done"}){Intent i=QueueEstimateTest.request(app,new FileNodes.Node("share",source.getName(),source.getName(),false,3));journal.ensure(i);String id=i.getStringExtra("request_id");journal.status(id,status,"");ids.add(id);}
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{
            service.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CANCEL),0,1);
            for(var j:journal.list())assertEquals(ids.get(3).equals(j.getString("id"))?"done":"cancelled",j.getString("status"));
            assertFalse(TransferForegroundService.canRetry());assertArrayEquals(new byte[]{5,0,9},Files.readAllBytes(source.toPath()));
        }finally{service.destroy();}
    }
    @Test public void clearWaitsForAnOpenDestinationAndDoesNotPublishOrRemoveUserFiles()throws Exception{
        TransferRecoveryTest.Documents provider=new TransferRecoveryTest.Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"clear-destination").toFile();provider.holdClose=true;
        ProviderInfo info=new ProviderInfo();info.authority="queue-clear.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        File source=new File(EmbeddedServerService.share(app),"clear-original.bin");source.getParentFile().mkdirs();Files.write(source.toPath(),new byte[]{9,1,7});File keep=new File(provider.root,"keep.txt");Files.write(keep.toPath(),new byte[]{2,3});
        Intent job=QueueEstimateTest.request(app,new FileNodes.Node("share",source.getName(),source.getName(),false,3));Uri tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");job.putExtra("destination",new FileNodes.Node("doc",DocumentFiles.root(tree).toString(),"Documents",true,0).bundle());
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{
            service.get().onStartCommand(job,0,1);assertTrue(provider.closeEntered.await(5,TimeUnit.SECONDS));service.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CLEAR),0,2);
            assertTrue(TransferForegroundService.clearing);assertFalse("Must retain the journal until writer cleanup finishes",journal.list().isEmpty());
            try{TransferForegroundService.resume(app,job.getStringExtra("request_id"));fail("Retry during clear was accepted");}catch(IllegalStateException expected){}
            provider.closeRelease.countDown();await(()->!TransferForegroundService.clearing&&TransferForegroundService.state.queued==0);
            assertTrue(journal.list().isEmpty());assertArrayEquals(new byte[]{9,1,7},Files.readAllBytes(source.toPath()));assertArrayEquals(new byte[]{2,3},Files.readAllBytes(keep.toPath()));assertFalse(new File(provider.root,source.getName()).exists());
        }finally{provider.closeRelease.countDown();service.destroy();}
    }
    @Test public void decliningBothBulkConfirmationsDoesNotLaunchAService(){
        try(var screen=Robolectric.buildActivity(TransferQueueActivity.class).setup()){
            var a=screen.get();for(boolean clear:new boolean[]{false,true}){QueueActions.confirm(a,clear);AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertNull(Shadows.shadowOf(a).getNextStartedService());}
        }
    }
    @Test public void interruptedRunningRecordStillOffersResumeAfterProcessLoss()throws Exception{
        Intent job=QueueEstimateTest.request(app,new FileNodes.Node("share","resume.bin","resume.bin",false,3));journal.ensure(job);journal.status(job.getStringExtra("request_id"),"running","");TransferForegroundService.State previous=TransferForegroundService.state;TransferForegroundService.state=new TransferForegroundService.State(false,true,"Остановлено","",0,0,1);
        try(var screen=Robolectric.buildActivity(TransferQueueActivity.class).setup()){
            var a=screen.get();org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"details",org.robolectric.util.ReflectionHelpers.ClassParameter.from(org.json.JSONObject.class,journal.list().get(0)));AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("Продолжить",dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();Intent resumed=Shadows.shadowOf(a).getNextStartedService();assertNotNull(resumed);assertEquals(job.getStringExtra("request_id"),resumed.getStringExtra("request_id"));
        }finally{TransferForegroundService.state=previous;}
    }
    private void await(java.util.function.BooleanSupplier test)throws Exception{long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!test.getAsBoolean()&&System.nanoTime()<limit){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(TransferForegroundService.state.text,test.getAsBoolean());}
}
