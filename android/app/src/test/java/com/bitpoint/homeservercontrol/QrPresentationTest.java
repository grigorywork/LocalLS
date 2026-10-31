package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.net.ServerSocket;
import java.security.KeyPairGenerator;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Tests the pixels actually displayed, not just an off-screen source bitmap. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h560dp-mdpi",shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class,RouteIdentityTest.Secrets.class,QrLoginIntegrationTest.Addresses.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QrPresentationTest {
    private android.app.Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;RouteIdentityTest.Secrets.store.clear();ServerLifecycleTest.Receive.entered=new CountDownLatch(1);ServerLifecycleTest.Receive.release=new CountDownLatch(1);ServerLifecycleTest.Receive.hold=false;app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();}
    private void measure(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    private View root(android.app.Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private String decode(Bitmap b)throws Exception{int[] p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(b.getWidth(),b.getHeight(),p)))).getText();}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(EmbeddedServerService.status,condition.getAsBoolean());}
    @Test public void serverExportsReadablePublicQrAndRejectsAStaleFilePickerResult()throws Exception{
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);ServerLifecycleTest.Identity.pair=generator.generateKeyPair();int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putInt("port",port).putString("user","localls").commit();
        var documents=new LastModIntegrationTest.Documents();documents.root=java.nio.file.Files.createTempDirectory(app.getCacheDir().toPath(),"qr-export-").toFile();File exported=new File(documents.root,"qr.png");java.nio.file.Files.write(exported.toPath(),new byte[]{1});
        var info=new android.content.pm.ProviderInfo();info.authority="qr.export.documents";info.name=LastModIntegrationTest.Documents.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;documents.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,documents);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);
        android.net.Uri tree=android.provider.DocumentsContract.buildTreeDocumentUri(info.authority,"root"),target=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"root/qr.png");
        var service=Robolectric.buildService(EmbeddedServerService.class).create();
        try{service.get().onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).setup()){
                QrPairingActivity a=screen.get();QrPairing invite=ReflectionHelpers.getField(a,"invitation");var before=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();var client=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
                ReflectionHelpers.callInstanceMethod(a,"exportQr");Intent picker=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());assertEquals("image/png",picker.getType());
                a.onActivityResult(7402,android.app.Activity.RESULT_OK,new Intent().setData(target));await(()->!ReflectionHelpers.<Boolean>getField(a,"busy"));
                Bitmap bitmap=BitmapFactory.decodeFile(exported.toString());assertNotNull(bitmap);String text=decode(bitmap);bitmap.recycle();assertEquals(invite.encode(),text);assertFalse(text.contains("PRIVATE"));assertEquals(before,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());assertEquals(client,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
                byte[] marker={9,8,7};java.nio.file.Files.write(exported.toPath(),marker);ReflectionHelpers.callInstanceMethod(a,"exportQr");Shadows.shadowOf(a).getNextStartedActivityForResult();String original=EmbeddedServerService.fingerprint;
                try{EmbeddedServerService.fingerprint="SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";a.onActivityResult(7402,android.app.Activity.RESULT_OK,new Intent().setData(target));await(()->!ReflectionHelpers.<Boolean>getField(a,"busy"));assertArrayEquals(marker,java.nio.file.Files.readAllBytes(exported.toPath()));}finally{EmbeddedServerService.fingerprint=original;}
            }
        }finally{service.destroy();assertTrue(ReflectionHelpers.<ExecutorService>getField(service.get(),"worker").awaitTermination(8,TimeUnit.SECONDS));}
    }
    @Test public void serverQrIsEntirelyVisibleBeforeScrollingAndItsDisplayedPixelsDecodeAcrossThemesAndFonts()throws Exception{
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);ServerLifecycleTest.Identity.pair=gen.generateKeyPair();int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putInt("port",port).putString("user","localls").commit();ServerLifecycleTest.Receive.hold=false;
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();Configuration original=new Configuration(app.getResources().getConfiguration());
        try{service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);
            for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(float scale:new float[]{1f,1.5f,2f}){
                ThemeCatalog.select(app,theme);Configuration config=new Configuration(original);config.fontScale=scale;app.getResources().updateConfiguration(config,app.getResources().getDisplayMetrics());
                try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).create().start().resume().visible()){
                    View root=root(screen.get());measure(root,360,560);ImageView qr=ReflectionHelpers.getField(screen.get(),"qr");assertNotNull("Missing QR: "+ReflectionHelpers.<TextView>getField(screen.get(),"status").getText(),qr);assertTrue("QR hidden",qr.isShown());assertEquals(qr.getWidth(),qr.getHeight());assertTrue("QR size "+qr.getWidth(),qr.getWidth()>=280);
                    Rect bounds=new Rect(0,0,qr.getWidth(),qr.getHeight());((ViewGroup)root).offsetDescendantRectToMyCoords(qr,bounds);assertTrue("QR below the fold: "+bounds,bounds.top>=0&&bounds.bottom<=560);assertEquals(0,((ScrollView)ReflectionHelpers.getField(screen.get(),"scroll")).getScrollY());
                    Bitmap shown=Bitmap.createBitmap(qr.getWidth(),qr.getHeight(),Bitmap.Config.ARGB_8888);qr.draw(new Canvas(shown));QrPairing invitation=QrPairing.decode(decode(shown),System.currentTimeMillis());assertEquals(EmbeddedServerService.fingerprint,invitation.pin);assertEquals(port,invitation.port);assertEquals("server",invitation.type);shown.recycle();
                    if(theme==0&&scale==1f){Bitmap preview=Bitmap.createBitmap(360,560,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(preview);canvas.drawColor(ThemeCatalog.color(screen.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));root.draw(canvas);File dir=new File("build/ui-previews");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,"qr-server-visible.png"))){assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,out));}preview.recycle();}
                }
            }
        }finally{app.getResources().updateConfiguration(original,app.getResources().getDisplayMetrics());controller.destroy();ExecutorService worker=ReflectionHelpers.getField(service,"worker");assertTrue(worker.awaitTermination(8,TimeUnit.SECONDS));}
    }
    @Test public void cameraViewportAndActualDecoderFramingAreSquareAndPermissionDenialDoesNotGrantAccess()throws Exception{
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class).create()){
            var a=screen.get();ReflectionHelpers.callInstanceMethod(a,"scan");assertNull(ReflectionHelpers.getField(a,"scanner"));assertTrue(HostTrustStore.get(app,"192.168.1.3",8022).isEmpty());
            String publicQr=QrPairing.invite("192.168.1.3",8022,"localls","SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",System.currentTimeMillis()).encode();ReflectionHelpers.callInstanceMethod(a,"showCode",ReflectionHelpers.ClassParameter.from(String.class,publicQr));
            Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.CAMERA);ReflectionHelpers.callInstanceMethod(a,"scan");measure(root(a),360,560);
            com.journeyapps.barcodescanner.DecoratedBarcodeView scanner=ReflectionHelpers.getField(a,"scanner");assertNotNull(scanner);assertEquals(scanner.getWidth(),scanner.getHeight());assertTrue(scanner.getWidth()>=280);var frame=scanner.getBarcodeView().getFramingRectSize();assertNotNull(frame);assertEquals(frame.width,frame.height);assertTrue(frame.width>0);
            ReflectionHelpers.callInstanceMethod(a,"stopScanner");assertNull(ReflectionHelpers.getField(a,"scanner"));assertEquals(View.VISIBLE,ReflectionHelpers.<ImageView>getField(a,"qr").getVisibility());assertEquals(View.VISIBLE,ReflectionHelpers.<View>getField(a,"qrFrame").getVisibility());assertTrue(HostTrustStore.get(app,"192.168.1.3",8022).isEmpty());
        }
    }
    @Test public void denseClientReplyDecodesFromTheDisplayedImageOnNarrowScreens()throws Exception{
        String payload;try(var input=getClass().getResourceAsStream("/qr-dense-regression.json")){assertNotNull(input);payload=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class).create().start().resume().visible()){
            ReflectionHelpers.callInstanceMethod(screen.get(),"showCode",ReflectionHelpers.ClassParameter.from(String.class,payload));
            for(int width:new int[]{320,360}){measure(root(screen.get()),width,568);ImageView qr=ReflectionHelpers.getField(screen.get(),"qr");Bitmap visible=Bitmap.createBitmap(qr.getWidth(),qr.getHeight(),Bitmap.Config.ARGB_8888);qr.draw(new Canvas(visible));assertEquals(payload,decode(visible));visible.recycle();}
        }
    }
    @Test public void qrScreenCanStartExistingServerOnceWithoutEditingItsKeyOrPreferences(){
        var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);prefs.edit().putString("allowed_key","preserved-authorized-key").putInt("port",9122).putString("user","saved-user").commit();var before=prefs.getAll();
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).create().start().resume().visible()){
            Button start=ReflectionHelpers.getField(screen.get(),"startServer");assertEquals(View.VISIBLE,start.getVisibility());start.performClick();start.performClick();Intent command=Shadows.shadowOf(app).getNextStartedService();assertNotNull(command);assertEquals(EmbeddedServerService.class.getName(),command.getComponent().getClassName());assertNull(Shadows.shadowOf(app).getNextStartedService());assertEquals(before,prefs.getAll());assertNull(ReflectionHelpers.getField(screen.get(),"qr"));
        }
    }
}
