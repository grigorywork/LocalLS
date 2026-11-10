package com.bitpoint.homeservercontrol;
import android.app.Service;
import android.content.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows={RouteIdentityTest.Secrets.class,ServerLifecycleTest.Receive.class})
public class StickyServerRestartTest {
 private android.app.Application app;private int port;
 @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putInt("port",port).putString("user","localls").commit();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;ServerLifecycleTest.Receive.created.set(0);ServerLifecycleTest.Receive.closed.set(0);ServerLifecycleTest.Receive.hold=false;ServerLifecycleTest.Receive.entered=new CountDownLatch(1);ServerLifecycleTest.Receive.release=new CountDownLatch(1);}
 private void ready()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!EmbeddedServerService.running&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(EmbeddedServerService.status,EmbeddedServerService.running);}
 private void destroy(org.robolectric.android.controller.ServiceController<EmbeddedServerService> controller)throws Exception{controller.destroy();assertTrue(ReflectionHelpers.<ExecutorService>getField(controller.get(),"worker").awaitTermination(10,TimeUnit.SECONDS));}
 @Test public void closingUiAndStickyRestartPreserveIdentityAuthorizationAndTransferredBytes()throws Exception{
  var key=SshKeys.generate();String pub=key.getString(SshKeys.PUBLIC);ServerDevices.add(app,pub);SshCredentials credential=SshCredentials.key(key.getString(SshKeys.PRIVATE),"");byte[] bytes=new byte[65536];new java.util.Random(19).nextBytes(bytes);
  var first=Robolectric.buildService(EmbeddedServerService.class).create();String pin;
  try{assertEquals(Service.START_STICKY,first.get().onStartCommand(new Intent(app,EmbeddedServerService.class),0,1));ready();pin=EmbeddedServerService.fingerprint;
   try(var screen=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){assertTrue(EmbeddedServerService.running);}
   assertTrue("Closing the UI must not stop the service",EmbeddedServerService.running);
   AppLanguage.select(app,"en");Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(EmbeddedServerService.running);assertEquals(pin,EmbeddedServerService.fingerprint);var notifications=app.getSystemService(android.app.NotificationManager.class).getActiveNotifications();boolean translated=false;for(var notice:notifications)if(notice.getId()==2210)translated=notice.getNotification().extras.getString(android.app.Notification.EXTRA_TITLE).equals("LocalLS SFTP server");assertTrue(translated);AppLanguage.select(app,"ru");Shadows.shadowOf(Looper.getMainLooper()).idle();
   try(var session=new SftpClient.TreeSession("127.0.0.1",port,"localls",credential,pin)){session.write("/","before-restart.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true);}
  }finally{destroy(first);}
  var second=Robolectric.buildService(EmbeddedServerService.class).create();try{assertEquals(Service.START_STICKY,second.get().onStartCommand(null,0,2));ready();assertEquals(pin,EmbeddedServerService.fingerprint);assertTrue(ServerDevices.contains(app,pub));try(var session=new SftpClient.TreeSession("127.0.0.1",port,"localls",credential,pin)){ByteArrayOutputStream result=new ByteArrayOutputStream();session.read("/before-restart.bin",result,(n,total)->true);assertArrayEquals(bytes,result.toByteArray());}
   second.get().onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.STOP),0,3);assertFalse(app.getSharedPreferences(EmbeddedServerService.PREFS,0).getBoolean("server_enabled",true));assertTrue(ServerDevices.contains(app,pub));
  }finally{destroy(second);}
  var stopped=Robolectric.buildService(EmbeddedServerService.class).create();try{assertEquals(Service.START_NOT_STICKY,stopped.get().onStartCommand(null,0,4));assertFalse(EmbeddedServerService.running);}finally{destroy(stopped);}
 }
}
