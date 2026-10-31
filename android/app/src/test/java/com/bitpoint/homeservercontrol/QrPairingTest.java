package com.bitpoint.homeservercontrol;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class QrPairingTest {
    @org.robolectric.annotation.Implements(value=SshKeys.class,isInAndroidSdk=false)
    public static class HeldIdentity {
        static JSONObject info;
        static java.util.concurrent.CountDownLatch entered,release;
        @org.robolectric.annotation.Implementation protected static JSONObject info(android.content.Context c,String host,int port,String user){
            entered.countDown();try{release.await(8,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return info;
        }
    }
    private static final String PIN="SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Test public void expiredServerQrDoesNotAskForNewCodeWhenPermanentAccessAlreadyExists()throws Exception{
        var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString("allowed_key",SshKeys.generate().getString(SshKeys.PUBLIC)).commit();
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new android.content.Intent(app,QrPairingActivity.class).putExtra("server",true)).create()){
            QrPairing expired=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis()-QrPairing.LIFETIME-1);org.robolectric.util.ReflectionHelpers.setField(screen.get(),"invitation",expired);org.robolectric.util.ReflectionHelpers.callInstanceMethod(screen.get(),"refreshPending");
            android.widget.TextView text=org.robolectric.util.ReflectionHelpers.getField(screen.get(),"status");assertTrue(text.getText().toString().contains("без ограничения времени"));assertTrue(text.getText().toString().contains("Повторный код не нужен"));android.widget.Button approve=org.robolectric.util.ReflectionHelpers.getField(screen.get(),"approveQr");assertEquals(android.view.View.GONE,approve.getVisibility());assertFalse(app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key","").isEmpty());
        }
    }
    private String bitmapText(android.graphics.Bitmap bitmap)throws Exception{
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        com.google.zxing.Result result=new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels))));
        assertEquals(com.google.zxing.BarcodeFormat.QR_CODE,result.getBarcodeFormat());return result.getText();
    }
    @Test public void denseQrFinderRegressionStillDecodesEveryOriginalByte()throws Exception{
        String payload;try(java.io.InputStream in=getClass().getResourceAsStream("/qr-dense-regression.json")){assertNotNull(in);payload=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        assertEquals(payload,bitmapText(QrPairingActivity.codeBitmap(payload)));
    }
    @Test public void denseRsaRepliesRemainReadableAcrossDifferentInvitations()throws Exception{
        String publicKey=SshKeys.generate().getString(SshKeys.PUBLIC);long now=System.currentTimeMillis();
        for(int n=0;n<24;n++){
            String reply=QrPairing.invite("192.168.1.3",8022,"localls",PIN,now).reply(publicKey).encode();assertEquals(reply,bitmapText(QrPairingActivity.codeBitmap(reply)));
        }
    }
    @Test public void bothScreensDeriveTheSameCodeFromPublicKeysAndTheExactInvitation()throws Exception{
        long now=System.currentTimeMillis();QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,now);String key=SshKeys.generate().getString(SshKeys.PUBLIC);
        QrPairing reply=QrPairing.decode(invite.reply(key).encode(),now);String code=invite.syncCode(key);assertTrue(code.matches("[0-9]{4} [0-9]{4}"));assertEquals(code,reply.syncCode(reply.publicKey));
        assertNotEquals(code,QrPairing.invite(invite.host,invite.port,invite.user,invite.pin,now).syncCode(key));assertNotEquals(code,invite.syncCode(SshKeys.generate().getString(SshKeys.PUBLIC)));
        assertFalse(invite.encode().contains("PRIVATE"));assertFalse(reply.encode().contains("PRIVATE"));
    }
    @Test public void savedBindingOpensAfterScreenRecreationWithoutRequestingAnotherQr()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();HostTrustStore.pair(app,"192.168.1.3",8022,"localls",PIN);JSONObject identity=SshKeys.generate();SshKeys.save(app,"192.168.1.3",8022,"localls",identity);
        for(int n=0;n<2;n++)try(var activity=Robolectric.buildActivity(QrPairingActivity.class).create()){
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);android.widget.Button open=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"openFiles");
            while(open.getVisibility()!=android.view.View.VISIBLE&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
            assertEquals(android.view.View.VISIBLE,open.getVisibility());assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());assertEquals(PIN,HostTrustStore.get(app,"192.168.1.3",8022));assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(app,"192.168.1.3",8022,"localls").getString(SshKeys.PUBLIC));
        }
    }
    @Test public void scanningAnAlreadyTrustedServerReusesItsKeyWithoutAnotherConfirmation()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();HostTrustStore.pair(app,"192.168.1.3",8022,"localls",PIN);JSONObject identity=SshKeys.generate();SshKeys.save(app,"192.168.1.3",8022,"localls",identity);
        try(var activity=Robolectric.buildActivity(QrPairingActivity.class).create()){
            QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis());org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"confirm",org.robolectric.util.ReflectionHelpers.ClassParameter.from(QrPairing.class,invite));awaitFinished(activity.get());
            assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(app,invite.host,invite.port,invite.user).getString(SshKeys.PUBLIC));assertNotNull(org.robolectric.util.ReflectionHelpers.getField(activity.get(),"qr"));
        }
    }
    @Test public void nestedUntrustedQrIsRejectedBeforeRecursiveJsonParsing()throws Exception{
        for(int depth:new int[]{1,50,1400}){
            String payload="{\"format\":"+"[".repeat(depth)+"0"+"]".repeat(depth)+"}";
            try{QrPairing.decode(payload,System.currentTimeMillis());fail("Nested QR parsed");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void completedPublicProgressCannotApplyToChangedServerOrClientIdentity()throws Exception{
        var app=RuntimeEnvironment.getApplication();HostTrustStore.pair(app,"192.168.1.3",8022,"localls",PIN);
        String first=SshKeys.generate().getString(SshKeys.PUBLIC),other=SshKeys.generate().getString(SshKeys.PUBLIC);
        QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis());
        QrPairingState.complete(app,invite,first);assertTrue(QrPairingState.confirmed(app,invite,first));assertFalse(QrPairingState.confirmed(app,invite,other));
        HostTrustStore.trust(app,invite.host,invite.port,"SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
        assertFalse(QrPairingState.confirmed(app,invite,first));assertNull(QrPairingState.pending(app));
    }
    @Test public void messagesArePublicBoundToOneInvitationAndExpire()throws Exception{
        long now=System.currentTimeMillis();QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,now);
        JSONObject key=SshKeys.generate();String encoded=invite.reply(key.getString(SshKeys.PUBLIC)).encode();
        assertFalse(encoded.contains("PRIVATE"));assertFalse(encoded.contains(key.getString(SshKeys.PRIVATE)));
        QrPairing reply=QrPairing.decode(encoded,now);assertTrue(invite.matches(reply));
        assertFalse(QrPairing.invite(invite.host,invite.port,invite.user,invite.pin,now).matches(reply));
        try{QrPairing.decode(encoded,invite.expires);fail("Expired invitation accepted");}catch(IllegalArgumentException expected){}
        JSONObject invalid=new JSONObject(encoded).put("privateKey","forbidden");try{QrPairing.decode(invalid.toString(),now);fail("Unexpected field accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void malformedEndpointAndLifetimeCannotBecomeTrusted()throws Exception{
        long now=System.currentTimeMillis();JSONObject j=new JSONObject(QrPairing.invite("192.168.1.3",8022,"localls",PIN,now).encode());
        for(String host:new String[]{"ssh://user:secret@host","a/b","host\n"}){j.put("host",host);try{QrPairing.decode(j.toString(),now);fail(host);}catch(IllegalArgumentException expected){}}
        j.put("host","192.168.1.3").put("expires",now+QrPairing.LIFETIME+1);try{QrPairing.decode(j.toString(),now);fail("Unbounded lifetime accepted");}catch(IllegalArgumentException expected){}
        j.put("expires",now+QrPairing.LIFETIME).put("pin","MD5:bad");try{QrPairing.decode(j.toString(),now);fail("Invalid pin accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void scanningChangedPinDoesNotOverwriteTrustOrClientIdentity()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();String original="SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        HostTrustStore.trust(app,"192.168.1.3",8022,original);JSONObject key=SshKeys.generate();SshKeys.save(app,"192.168.1.3",8022,"localls",key);
        try(var activity=Robolectric.buildActivity(QrPairingActivity.class).create()){
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"confirm",org.robolectric.util.ReflectionHelpers.ClassParameter.from(QrPairing.class,QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis())));
            assertEquals(original,HostTrustStore.get(app,"192.168.1.3",8022));assertEquals(key.getString(SshKeys.PUBLIC),SshKeys.info(app,"192.168.1.3",8022,"localls").getString(SshKeys.PUBLIC));
        }
    }
    @Test public void confirmedServerUsesExistingIdentityAndActualQrContainsOnlyPublicReply()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);prefs.edit().clear().putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).putString(ServerConfig.KEY_VPN_HOST,"remembered.example").commit();
        JSONObject key=SshKeys.generate();SshKeys.save(app,"192.168.1.3",8022,"localls",key);QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis());
        try(var activity=Robolectric.buildActivity(QrPairingActivity.class).create()){
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"confirm",org.robolectric.util.ReflectionHelpers.ClassParameter.from(QrPairing.class,invite));
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals("",HostTrustStore.get(app,invite.host,invite.port));dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while((boolean)org.robolectric.util.ReflectionHelpers.getField(activity.get(),"busy")&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
            assertFalse((boolean)org.robolectric.util.ReflectionHelpers.getField(activity.get(),"busy"));assertEquals(PIN,HostTrustStore.get(app,invite.host,invite.port));
            assertEquals(key.getString(SshKeys.PUBLIC),SshKeys.info(app,invite.host,invite.port,invite.user).getString(SshKeys.PUBLIC));assertTrue(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));assertEquals("remembered.example",prefs.getString(ServerConfig.KEY_VPN_HOST,""));
            android.widget.ImageView qr=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"qr");assertNotNull(qr);android.graphics.Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)qr.getDrawable()).getBitmap();int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
            com.google.zxing.BinaryBitmap binary=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels)));
            String text=new com.google.zxing.MultiFormatReader().decode(binary).getText();assertTrue(invite.matches(QrPairing.decode(text,System.currentTimeMillis())));assertFalse(text.contains("PRIVATE"));
        }
    }
    private void startHeldConfirmation(QrPairingActivity activity,QrPairing invite){
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"confirm",org.robolectric.util.ReflectionHelpers.ClassParameter.from(QrPairing.class,invite));
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private void awaitFinished(QrPairingActivity activity)throws Exception{
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);while((boolean)org.robolectric.util.ReflectionHelpers.getField(activity,"busy")&&System.nanoTime()<end){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}assertFalse((boolean)org.robolectric.util.ReflectionHelpers.getField(activity,"busy"));
    }
    @Test @Config(shadows={RouteIdentityTest.Secrets.class,HeldIdentity.class})
    public void changedPinDuringQrPreparationCannotBeOverwrittenByDelayedConfirmation()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();var p=app.getSharedPreferences(ServerConfig.PREFS,0);p.edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.55").commit();
        HeldIdentity.info=SshKeys.inspect(SshKeys.generate().getString(SshKeys.PRIVATE),"");HeldIdentity.entered=new java.util.concurrent.CountDownLatch(1);HeldIdentity.release=new java.util.concurrent.CountDownLatch(1);
        QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis());String changed="SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        try(var activity=Robolectric.buildActivity(QrPairingActivity.class).create()){
            startHeldConfirmation(activity.get(),invite);assertTrue(HeldIdentity.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));HostTrustStore.trust(app,invite.host,invite.port,changed);HeldIdentity.release.countDown();awaitFinished(activity.get());
            assertEquals("Delayed QR replaced a newer trusted key",changed,HostTrustStore.get(app,invite.host,invite.port));assertEquals("192.0.2.55",p.getString(ServerConfig.KEY_LOCAL_HOST,""));
        }finally{HeldIdentity.release.countDown();}
    }
    @Test @Config(shadows={RouteIdentityTest.Secrets.class,HeldIdentity.class})
    public void closingQrScreenWhileIdentityLoadsCannotChangeTheSelectedProfileLater()throws Exception{
        var app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();var p=app.getSharedPreferences(ServerConfig.PREFS,0);p.edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.55").commit();
        HeldIdentity.info=SshKeys.inspect(SshKeys.generate().getString(SshKeys.PRIVATE),"");HeldIdentity.entered=new java.util.concurrent.CountDownLatch(1);HeldIdentity.release=new java.util.concurrent.CountDownLatch(1);
        QrPairing invite=QrPairing.invite("192.168.1.3",8022,"localls",PIN,System.currentTimeMillis());var activity=Robolectric.buildActivity(QrPairingActivity.class).create();
        try{startHeldConfirmation(activity.get(),invite);assertTrue(HeldIdentity.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));var executor=org.robolectric.util.ReflectionHelpers.<java.util.concurrent.ExecutorService>getField(activity.get(),"worker");activity.destroy();HeldIdentity.release.countDown();assertTrue(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("Closed QR screen changed profile from a worker", "192.0.2.55",p.getString(ServerConfig.KEY_LOCAL_HOST,""));assertEquals("",HostTrustStore.get(app,invite.host,invite.port));
        }finally{HeldIdentity.release.countDown();}
    }
}
