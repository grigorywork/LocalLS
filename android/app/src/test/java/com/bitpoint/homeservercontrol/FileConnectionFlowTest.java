package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.ListView;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Exercises real pinned SSH listings while Android screen state and the selected profile change. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class FileConnectionFlowTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private android.app.Application app;private EmbeddedSftpServer server;private int port;private Path root;private KeyPair hostKey;private org.json.JSONObject clientKey;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();
        root=temp.newFolder("server").toPath();Files.createDirectory(root.resolve("OnlyA"));Files.write(root.resolve("original.bin"),new byte[]{1,2,3});
        var key=SshKeys.generate();clientKey=key;KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair host=generator.generateKeyPair();hostKey=host;
        server=new EmbeddedSftpServer();port=server.start("127.0.0.2",0,"localls",EmbeddedSftpServer.authorizedKey(key.getString(SshKeys.PUBLIC)),host,root);
        profile("127.0.0.2");SshKeys.save(app,"127.0.0.2",port,"localls",key);HostTrustStore.trust(app,"127.0.0.2",port,EmbeddedSftpServer.fingerprint(host));
    }
    @After public void stop()throws Exception{server.close();}
    private void profile(String host){app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,host).putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL).putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,port).commit();}
    private org.robolectric.android.controller.ActivityController<FilesActivity> screen(Bundle state){return Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",false)).create(state);}
    private void await(java.util.function.BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!ready.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(15);}assertTrue(ready.getAsBoolean());}
    private void listed(FilesActivity a)throws Exception{await(()->(boolean)ReflectionHelpers.getField(a,"remoteReady"));}
    @Test public void changingProfileCannotSendOldServerSelectionToNewAddress()throws Exception{
        try(var c=screen(null)){
            FilesActivity a=c.get();listed(a);ListView list=a.findViewById(R.id.remoteFileList);assertEquals(2,list.getCount());list.performItemClick(null,1,1);
            profile("127.0.0.3");ReflectionHelpers.callInstanceMethod(a,"receiveSelection");
            assertNull("An old selection must never be queued against the new global profile",Shadows.shadowOf(app).getNextStartedService());
            assertFalse((boolean)ReflectionHelpers.getField(a,"remoteReady"));
        }
    }
    @Test public void newProfileDoesNotRestorePreviousServersPath()throws Exception{
        Bundle state=new Bundle();
        try(var c=screen(null)){listed(c.get());assertTrue(c.get().openRemotePath("/OnlyA"));listed(c.get());c.get().onSaveInstanceState(state);}
        profile("127.0.0.3");
        try(var c=screen(state)){assertEquals(".",ReflectionHelpers.getField(c.get(),"remotePath"));}
    }
    @Test public void sameProfileKeepsItsFolderAcrossRecreation()throws Exception{
        Bundle state=new Bundle();
        try(var c=screen(null)){listed(c.get());assertTrue(c.get().openRemotePath("/OnlyA"));listed(c.get());c.get().onSaveInstanceState(state);}
        try(var c=screen(state)){listed(c.get());assertEquals("/OnlyA",ReflectionHelpers.getField(c.get(),"remotePath"));}
    }
    @Test public void queuedTransferKeepsDisplayedDestinationWhenGlobalProfileChanges()throws Exception{
        Path otherRoot=temp.newFolder("other-server").toPath();
        try(EmbeddedSftpServer other=new EmbeddedSftpServer()){
            other.start("127.0.0.3",port,"localls",EmbeddedSftpServer.authorizedKey(clientKey.getString(SshKeys.PUBLIC)),hostKey,otherRoot);
            SshKeys.save(app,"127.0.0.3",port,"localls",clientKey);HostTrustStore.trust(app,"127.0.0.3",port,EmbeddedSftpServer.fingerprint(hostKey));
            FileConnection displayed=FileConnection.current(app);profile("127.0.0.3");
            java.io.File share=EmbeddedServerService.share(app);assertTrue(share.isDirectory() || share.mkdirs());byte[] bytes=new byte[]{8,6,7,5,3,0,9};Files.write(share.toPath().resolve("queued.bin"),bytes);
            TransferForegroundService.enqueue(app,TransferForegroundService.UPLOAD,java.util.Collections.singletonList(new FileNodes.Node("share","queued.bin","queued.bin",false,bytes.length)),"/",null,displayed);
            Intent request=Shadows.shadowOf(app).getNextStartedService();assertNotNull(request);assertEquals("127.0.0.2",request.getStringExtra("host"));
            var worker=Robolectric.buildService(TransferForegroundService.class).create();try{
                worker.get().onStartCommand(request,0,1);await(()->!TransferForegroundService.state.active);
                assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
                assertArrayEquals(bytes,Files.readAllBytes(root.resolve("queued.bin")));assertFalse(Files.exists(otherRoot.resolve("queued.bin")));
            }finally{worker.destroy();}
        }
    }
    @Test public void switchingToServerRoleDoesNotRestoreClientPane()throws Exception{
        Bundle state=new Bundle();
        try(var c=screen(null)){listed(c.get());c.get().onSaveInstanceState(state);}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",true).putBoolean("show_client",false).commit();
        try(var c=screen(state)){assertTrue((boolean)ReflectionHelpers.getField(c.get(),"own"));listed(c.get());assertEquals("share",((FileNodes.Node)ReflectionHelpers.getField(c.get(),"server")).kind);}
    }
    @Test public void changedFingerprintInvalidatesOldScreenWhenResumed()throws Exception{
        try(var c=screen(null)){
            listed(c.get());HostTrustStore.trust(app,"127.0.0.2",port,"SHA256:changed");c.get().onResume();
            assertFalse((boolean)ReflectionHelpers.getField(c.get(),"remoteReady"));assertTrue((boolean)ReflectionHelpers.getField(c.get(),"restarting"));
        }
    }
    @Test public void pickerResultFromPreviousProfileIsNotSentAfterRecreation()throws Exception{
        Bundle state=new Bundle();
        try(var c=screen(null)){listed(c.get());ReflectionHelpers.callInstanceMethod(c.get(),"pickTree",ReflectionHelpers.ClassParameter.from(int.class,504));c.get().onSaveInstanceState(state);}
        profile("127.0.0.3");
        try(var c=screen(state)){
            // Reject stale results before granting or inspecting an untrusted provider URI.
            c.get().onActivityResult(504,Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://missing/tree/root")));
            assertEquals("Подключение изменилось. Выбери файлы заново для текущего сервера.",org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertNull(Shadows.shadowOf(app).getNextStartedService());
        }
    }
}
