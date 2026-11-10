package com.bitpoint.homeservercontrol;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class TransferRedeliveryTest {
 private android.app.Application app;private File share,destination;private byte[] bytes;
 @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();share=EmbeddedServerService.share(app);share.mkdirs();destination=new File(share,"destination");destination.mkdirs();bytes=new byte[32768];new Random(7).nextBytes(bytes);Files.write(new File(share,"source.bin").toPath(),bytes);}
 private Intent request(){var nodes=new ArrayList<Bundle>();nodes.add(new FileNodes.Node("share","source.bin","source.bin",false,bytes.length).bundle());return new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.IMPORT).putExtra("request_id",UUID.randomUUID().toString()).putParcelableArrayListExtra("sources",nodes).putExtra("destination",new FileNodes.Node("share","destination","destination",true,0).bundle()).putExtra("conflict",TransferJournal.KEEP).putExtra("verify",true);}
 private void drained(TransferForegroundService s)throws Exception{ReflectionHelpers.<ExecutorService>getField(s,"worker").submit(()->{}).get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();}
 private void destroy(org.robolectric.android.controller.ServiceController<TransferForegroundService> s)throws Exception{s.destroy();assertTrue(ReflectionHelpers.<ExecutorService>getField(s.get(),"worker").awaitTermination(10,TimeUnit.SECONDS));}
 @Test public void systemRedeliveryKeepsRequestIdentityAndCompletedTransfersAreNotRepeated()throws Exception{
  Intent i=request();TransferJournal journal=new TransferJournal(app);journal.ensure(i);journal.status(i.getStringExtra("request_id"),"paused","System killed process");var first=Robolectric.buildService(TransferForegroundService.class).create();
  try{assertEquals(Service.START_REDELIVER_INTENT,first.get().onStartCommand(i,Service.START_FLAG_REDELIVERY,1));drained(first.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(destination,"source.bin").toPath()));}finally{destroy(first);}
  var again=Robolectric.buildService(TransferForegroundService.class).create();try{assertEquals(Service.START_NOT_STICKY,again.get().onStartCommand(i,Service.START_FLAG_REDELIVERY,2));drained(again.get());assertEquals(1,destination.listFiles().length);assertArrayEquals(bytes,Files.readAllBytes(new File(destination,"source.bin").toPath()));}finally{destroy(again);}
 }
 @Test public void cancelledOrFailedJobsCannotBeResurrectedBySystemRedelivery()throws Exception{
  for(String state:new String[]{"cancelled","failed"}){Intent i=request();TransferJournal journal=new TransferJournal(app);journal.ensure(i);journal.status(i.getStringExtra("request_id"),state,"terminal");var s=Robolectric.buildService(TransferForegroundService.class).create();try{assertEquals(Service.START_NOT_STICKY,s.get().onStartCommand(i,Service.START_FLAG_REDELIVERY,1));drained(s.get());assertEquals(0,destination.listFiles().length);assertArrayEquals(bytes,Files.readAllBytes(new File(share,"source.bin").toPath()));}finally{destroy(s);}}
 }
}
