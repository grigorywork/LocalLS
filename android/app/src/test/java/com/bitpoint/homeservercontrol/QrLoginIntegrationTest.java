package com.bitpoint.homeservercontrol;

import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.net.ServerSocket;
import java.security.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Two Android roles, actual QR pixels, actual SSH and durable approval through Service restart. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class,RouteIdentityTest.Secrets.class,QrLoginIntegrationTest.Addresses.class})
public class QrLoginIntegrationTest {
    @Implements(value=LocalServerAddresses.class,isInAndroidSdk=false)
    public static class Addresses {
        @Implementation protected static java.util.List<String> current(Context c){return java.util.Collections.singletonList("127.0.0.1");}
    }
    private android.app.Application app;
    private int port;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);ServerLifecycleTest.Identity.pair=gen.generateKeyPair();
        try(ServerSocket pick=new ServerSocket(0)){port=pick.getLocalPort();}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putInt("port",port).putString("user","localls").commit();
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).putString(ServerConfig.KEY_VPN_HOST,"remembered.example").commit();
        ServerLifecycleTest.Receive.hold=false;ServerLifecycleTest.Receive.entered=new CountDownLatch(1);ServerLifecycleTest.Receive.release=new CountDownLatch(1);
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.waitingForClient=false;
    }
    private void await(java.util.function.BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        assertTrue(EmbeddedServerService.status+" / "+EmbeddedServerService.diagnostic,condition.getAsBoolean());
    }
    private void cleanup(EmbeddedServerService service)throws Exception{ExecutorService worker=ReflectionHelpers.getField(service,"worker");assertTrue(worker.awaitTermination(8,TimeUnit.SECONDS));}
    private String decode(QrPairingActivity screen)throws Exception{
        ImageView view=ReflectionHelpers.getField(screen,"qr");android.graphics.Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)view.getDrawable()).getBitmap();int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        return new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels)))).getText();
    }
    @Test public void oneServerQrAndOneLocalApprovalGivePermanentAccessAfterBothRolesRestart()throws Exception{pairAndRestart(false);}
    @Test public void opticalClientReplyAlsoGivesPermanentAccessWithoutCopyingAnyKey()throws Exception{pairAndRestart(true);}
    @Test public void confirmedBindingRecreatedWhileServerIsOfflineDoesNotRestartQrSetup()throws Exception{pairAndRestart(false,true);}
    @Test public void pendingApprovalResumesAfterClientClosesWithoutAnotherScanOrNewKey()throws Exception{
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=controller.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            QrPairing invite=QrPairing.invite("127.0.0.1",port,"localls",EmbeddedServerService.fingerprint,System.currentTimeMillis());EmbeddedServerService.beginQrPairing(invite);
            String publicKey;
            try(var client=Robolectric.buildActivity(QrPairingActivity.class).create()){
                client.get().readCode(invite.encode());await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=null);
                org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                await(()->!EmbeddedServerService.pendingQrKey(invite.nonce).isEmpty());publicKey=EmbeddedServerService.pendingQrKey(invite.nonce);
            }
            android.app.AlertDialog previous=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            try(var client=Robolectric.buildActivity(QrPairingActivity.class).create()){
                TextView code=ReflectionHelpers.getField(client.get(),"syncCode");await(()->code.getVisibility()==View.VISIBLE);
                assertEquals("Код сверки\n"+invite.syncCode(publicKey),code.getText().toString());
                assertSame("Restoring public parameters asked for a new scan/confirmation",previous,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
                assertEquals(publicKey,SshKeys.info(app,invite.host,invite.port,invite.user).getString(SshKeys.PUBLIC));
                assertFalse(SftpClient.loginAttempt(invite.host,invite.port,invite.user,SshKeys.keyCredentials(app,invite.host,invite.port,invite.user),invite.pin));
                service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce",invite.nonce).putExtra("public_key",publicKey),0,2);
                Button files=ReflectionHelpers.getField(client.get(),"openFiles");await(()->files.getVisibility()==View.VISIBLE);
                assertNull(QrPairingState.pending(app));assertTrue(QrPairingState.confirmed(app,invite,publicKey));
            }
        }finally{controller.destroy();cleanup(service);}
    }
    @Test public void independentDesktopDecodesQrImageAndGetsSftpOnlyAfterServerApproval()throws Exception{
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();java.lang.Process process=null;
        File offerFile=File.createTempFile("desktop-invite-",".json",app.getCacheDir());File result=File.createTempFile("desktop-result-",".txt",app.getCacheDir());
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            QrPairing invite=QrPairing.invite("127.0.0.1",port,"localls",EmbeddedServerService.fingerprint,System.currentTimeMillis());EmbeddedServerService.beginQrPairing(invite);
            java.nio.file.Files.writeString(offerFile.toPath(),invite.encode());
            File tool=new File("../../desktop/tools/android-qr-interop.cjs").getCanonicalFile();assertTrue(tool.toString(),tool.isFile());
            process=new ProcessBuilder("node",tool.toString(),offerFile.toString()).redirectErrorStream(true).redirectOutput(result).start();
            await(()->!EmbeddedServerService.pendingQrKey(invite.nonce).isEmpty());String key=EmbeddedServerService.pendingQrKey(invite.nonce);
            java.util.List<String> lines=java.nio.file.Files.readAllLines(result.toPath());assertFalse(lines.isEmpty());org.json.JSONObject publicInfo=new org.json.JSONObject(lines.get(0));
            assertEquals(invite.syncCode(key),publicInfo.getString("code"));assertEquals(key,EmbeddedSftpServer.normalizeAuthorizedKey(publicInfo.getString("publicKey")));assertTrue(EmbeddedServerService.waitingForClient);
            service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce",invite.nonce).putExtra("public_key",key),0,2);
            assertTrue("Desktop did not finish",process.waitFor(25,TimeUnit.SECONDS));assertEquals(java.nio.file.Files.readString(result.toPath()),0,process.exitValue());
            assertTrue(java.nio.file.Files.readString(result.toPath()).contains("INTEROP_OK"));assertEquals("PC QR to Android SFTP\n",java.nio.file.Files.readString(new File(EmbeddedServerService.share(app),"computer.txt").toPath()));
        }finally{if(process!=null&&process.isAlive())process.destroyForcibly();controller.destroy();cleanup(service);offerFile.delete();result.delete();}
    }
    @Test public void addingAndRevokingOneDevicePersistsWithoutChangingOtherClientsOrServerIdentity()throws Exception{
        org.json.JSONObject one=SshKeys.generate(),two=SshKeys.generate();String firstKey=one.getString(SshKeys.PUBLIC),secondKey=two.getString(SshKeys.PUBLIC);String pin;
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);pin=EmbeddedServerService.fingerprint;
            for(org.json.JSONObject identity:new org.json.JSONObject[]{one,two}){
                String key=identity.getString(SshKeys.PUBLIC);QrPairing invite=QrPairing.invite("127.0.0.1",port,"localls",pin,System.currentTimeMillis());EmbeddedServerService.beginQrPairing(invite);
                assertFalse(SftpClient.pairingAttempt(invite.host,port,invite.user,invite.nonce,SshCredentials.key(identity.getString(SshKeys.PRIVATE),""),pin));
                service.onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce",invite.nonce).putExtra("public_key",key),0,2);
                await(()->ServerDevices.contains(app,key));assertTrue(SftpClient.loginAttempt(invite.host,port,invite.user,SshCredentials.key(identity.getString(SshKeys.PRIVATE),""),pin));
            }
            assertEquals(2,ServerDevices.list(app).size());assertEquals(EmbeddedSftpServer.normalizeAuthorizedKey(firstKey),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key",""));
            EmbeddedServerService.revokeDevice(app,firstKey);assertEquals(1,ServerDevices.list(app).size());assertFalse(SftpClient.loginAttempt("127.0.0.1",port,"localls",SshCredentials.key(one.getString(SshKeys.PRIVATE),""),pin));
        }finally{controller.destroy();cleanup(service);}
        var restarted=Robolectric.buildService(EmbeddedServerService.class).create();
        try{restarted.get().onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);assertEquals(pin,EmbeddedServerService.fingerprint);
            assertFalse(SftpClient.loginAttempt("127.0.0.1",port,"localls",SshCredentials.key(one.getString(SshKeys.PRIVATE),""),pin));assertTrue(SftpClient.loginAttempt("127.0.0.1",port,"localls",SshCredentials.key(two.getString(SshKeys.PRIVATE),""),pin));assertTrue(ServerDevices.contains(app,secondKey));
        }finally{restarted.destroy();cleanup(restarted.get());}
    }
    @Test public void revokedAndroidDeviceCanEnrollAgainWithSameKeyOnlyAfterFreshApproval()throws Exception{
        org.json.JSONObject identity=SshKeys.generate();String key=identity.getString(SshKeys.PUBLIC);var service=Robolectric.buildService(EmbeddedServerService.class).create();
        try{
            service.get().onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);String pin=EmbeddedServerService.fingerprint;
            QrPairing initial=QrPairing.invite("127.0.0.1",port,"localls",pin,System.currentTimeMillis());EmbeddedServerService.beginQrPairing(initial);
            service.get().onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce",initial.nonce).putExtra("public_key",key).putExtra("optical",true),0,2);await(()->ServerDevices.contains(app,key));
            SshKeys.save(app,initial.host,port,initial.user,identity);HostTrustStore.pair(app,initial.host,port,initial.user,pin);QrPairingState.complete(app,initial,key);
            EmbeddedServerService.revokeDevice(app,key);QrPairing next=QrPairing.invite(initial.host,port,initial.user,pin,System.currentTimeMillis());EmbeddedServerService.beginQrPairing(next);
            try(var client=Robolectric.buildActivity(QrPairingActivity.class).create()){
                client.get().readCode(next.encode());await(()->!EmbeddedServerService.pendingQrKey(next.nonce).isEmpty());assertFalse(QrPairingState.confirmed(app,next,key));assertEquals(EmbeddedSftpServer.normalizeAuthorizedKey(key),EmbeddedServerService.pendingQrKey(next.nonce));
            }
            try(var resumed=Robolectric.buildActivity(QrPairingActivity.class).create()){
                TextView code=ReflectionHelpers.getField(resumed.get(),"syncCode");await(()->code.getVisibility()==View.VISIBLE);assertEquals("Код сверки\n"+next.syncCode(key),code.getText().toString());
                assertFalse(SftpClient.loginAttempt(next.host,port,next.user,SshCredentials.key(identity.getString(SshKeys.PRIVATE),""),pin));assertEquals(key,SshKeys.info(app,next.host,port,next.user).getString(SshKeys.PUBLIC));
                service.get().onStartCommand(new Intent(app,EmbeddedServerService.class).setAction(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce",next.nonce).putExtra("public_key",key),0,3);
                Button files=ReflectionHelpers.getField(resumed.get(),"openFiles");await(()->files.getVisibility()==View.VISIBLE);assertTrue(QrPairingState.confirmed(app,next,key));assertEquals(1,ServerDevices.count(app));
            }
        }finally{service.destroy();cleanup(service.get());}
    }
    private void pairAndRestart(boolean optical)throws Exception{pairAndRestart(optical,false);}
    private void pairAndRestart(boolean optical,boolean offlineRecreation)throws Exception{
        String pin;String clientPublic;SshCredentials credentials;var first=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=first.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);pin=EmbeddedServerService.fingerprint;
            try(var server=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).create().start().resume();var client=Robolectric.buildActivity(QrPairingActivity.class).create().start().resume()){
                String serverText=decode(server.get());assertFalse(serverText.contains("PRIVATE"));QrPairing invitation=QrPairing.decode(serverText,System.currentTimeMillis());client.get().readCode(serverText);
                await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=null);org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                await(()->!EmbeddedServerService.pendingQrKey(invitation.nonce).isEmpty());clientPublic=SshKeys.info(app,"127.0.0.1",port,"localls").getString(SshKeys.PUBLIC);credentials=SshKeys.keyCredentials(app,"127.0.0.1",port,"localls");
                assertFalse("Files were accessible before server approval",SftpClient.loginAttempt("127.0.0.1",port,"localls",credentials,pin));assertTrue(EmbeddedServerService.waitingForClient);
                ReflectionHelpers.callInstanceMethod(server.get(),"refreshPending");TextView serverCode=ReflectionHelpers.getField(server.get(),"syncCode"),clientCode=ReflectionHelpers.getField(client.get(),"syncCode");assertEquals(clientCode.getText().toString(),serverCode.getText().toString());
                android.app.AlertDialog previous=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
                if(optical){Button reply=ReflectionHelpers.getField(client.get(),"replyQr");reply.performClick();String replyText=decode(client.get());assertTrue(invitation.matches(QrPairing.decode(replyText,System.currentTimeMillis())));assertFalse(replyText.contains("PRIVATE"));server.get().readCode(replyText);}
                else{Button approve=ReflectionHelpers.getField(server.get(),"approveQr");assertEquals(View.VISIBLE,approve.getVisibility());approve.performClick();}
                await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=previous);org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();Intent command=Shadows.shadowOf(app).getNextStartedService();assertNotNull(((TextView)ReflectionHelpers.getField(server.get(),"status")).getText().toString(),command);assertEquals(EmbeddedServerService.AUTHORIZE_QR,command.getAction());assertEquals(optical,command.getBooleanExtra("optical",false));assertFalse(command.hasExtra("privateKey"));service.onStartCommand(command,0,2);
                Button files=ReflectionHelpers.getField(client.get(),"openFiles");await(()->!EmbeddedServerService.waitingForClient&&files.getVisibility()==View.VISIBLE);
                ReflectionHelpers.callInstanceMethod(server.get(),"refreshPending");Button approveAgain=ReflectionHelpers.getField(server.get(),"approveQr");assertEquals("Server requested approval again after successful pairing",View.GONE,approveAgain.getVisibility());
                assertEquals(EmbeddedSftpServer.normalizeAuthorizedKey(clientPublic),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key",""));assertEquals(pin,HostTrustStore.get(app,"127.0.0.1",port));
                if(offlineRecreation){
                    Bundle state=new Bundle();client.saveInstanceState(state);
                    ReflectionHelpers.<EmbeddedSftpServer>getField(service,"server").close();
                    try(var recreated=Robolectric.buildActivity(QrPairingActivity.class).create(state)){
                        Button savedFiles=ReflectionHelpers.getField(recreated.get(),"openFiles");await(()->savedFiles.getVisibility()==View.VISIBLE);
                        assertNull("Confirmed access restarted the QR exchange",ReflectionHelpers.getField(recreated.get(),"invitation"));
                        assertEquals(View.GONE,ReflectionHelpers.<TextView>getField(recreated.get(),"syncCode").getVisibility());
                        assertFalse(ReflectionHelpers.<TextView>getField(recreated.get(),"status").getText().toString().contains("Ждём разрешения"));
                    }
                    return;
                }
            }
            assertTrue(SftpClient.loginAttempt("127.0.0.1",port,"localls",credentials,pin));
        }finally{first.destroy();cleanup(service);}
        var second=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService restarted=second.get();
        try{
            restarted.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);assertFalse(EmbeddedServerService.waitingForClient);assertEquals(pin,EmbeddedServerService.fingerprint);
            android.app.AlertDialog previous=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            try(var client=Robolectric.buildActivity(QrPairingActivity.class).create()){
                Button files=ReflectionHelpers.getField(client.get(),"openFiles");await(()->files.getVisibility()==View.VISIBLE);assertSame("Restart requested another confirmation",previous,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
                assertEquals(clientPublic,SshKeys.info(app,"127.0.0.1",port,"localls").getString(SshKeys.PUBLIC));
                try(SftpClient.TreeSession session=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshKeys.keyCredentials(app,"127.0.0.1",port,"localls"),pin)){
                    byte[] bytes={2,3,5,7};String path=session.write("/","remembered.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true);ByteArrayOutputStream read=new ByteArrayOutputStream();session.read(path,read,(n,total)->true);assertArrayEquals(bytes,read.toByteArray());
                }
                var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);assertTrue(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));assertEquals("remembered.example",prefs.getString(ServerConfig.KEY_VPN_HOST,""));
            }
        }finally{second.destroy();cleanup(restarted);}
    }
}
