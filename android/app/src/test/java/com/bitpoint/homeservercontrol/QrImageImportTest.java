package com.bitpoint.homeservercontrol;

import android.app.*;import android.content.*;import android.content.pm.ProviderInfo;import android.graphics.*;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;
import java.io.*;import java.nio.file.Files;import java.util.concurrent.TimeUnit;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QrImageImportTest {
 private Application app;private KeyScreenFlowTest.KeyFile source;private final Uri uri=Uri.parse("content://qr.images/code.png");
 @Before public void setup()throws Exception{
  app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").commit();
  source=new KeyScreenFlowTest.KeyFile();source.file=File.createTempFile("qr-image-",".png",app.getCacheDir());ProviderInfo info=new ProviderInfo();info.authority="qr.images";source.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,source);
 }
 @After public void cleanup(){source.file.delete();}
 private QrPairing invitation() {return QrPairing.invite("192.0.2.99",8022,"localls","SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",System.currentTimeMillis());}
 private void write(Bitmap bitmap)throws Exception{try(OutputStream out=new FileOutputStream(source.file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
 private void await(java.util.function.BooleanSupplier done)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!done.getAsBoolean()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(done.getAsBoolean());}
 private Button button(View view,String text){if(view instanceof Button&&((Button)view).getText().toString().equals(text))return (Button)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button b=button(((ViewGroup)view).getChildAt(i),text);if(b!=null)return b;}return null;}
 @Test public void pngPickerNeedsNoCameraAndOnlyAsksForConsentBeforeChangingConnection()throws Exception{
  QrPairing code=invitation();write(QrPairingActivity.codeBitmap(code.encode()));var before=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
  try(var c=Robolectric.buildActivity(QrPairingActivity.class).create()){
   Button open=button(c.get().findViewById(android.R.id.content),"Открыть QR-код из файла");assertNotNull(open);open.performClick();Intent picker=Shadows.shadowOf(c.get()).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());assertEquals("image/*",picker.getType());assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));
   c.get().onActivityResult(7403,Activity.RESULT_OK,new Intent().setData(uri));await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=null);AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains(code.host));assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
  }
 }
 @Test public void transparentAndInvertedPngsDecodeTheSamePublicOffer()throws Exception{
  String payload=invitation().encode();Bitmap qr=QrPairingActivity.codeBitmap(payload);int w=qr.getWidth();int[] pixels=new int[w*w];qr.getPixels(pixels,0,w,0,0,w,w);qr.recycle();
  int[] transparent=pixels.clone();for(int i=0;i<transparent.length;i++)if(transparent[i]==Color.WHITE)transparent[i]=Color.TRANSPARENT;
  write(Bitmap.createBitmap(transparent,w,w,Bitmap.Config.ARGB_8888));assertEquals(payload,QrImageReader.read(app.getContentResolver(),uri));
  for(int i=0;i<pixels.length;i++)pixels[i]=pixels[i]==Color.WHITE?Color.BLACK:Color.WHITE;write(Bitmap.createBitmap(pixels,w,w,Bitmap.Config.ARGB_8888));assertEquals(payload,QrImageReader.read(app.getContentResolver(),uri));
 }
 @Test public void expiredImageAndNonQrImageNeverMutateTrustOrSettings()throws Exception{
  QrPairing code=invitation();String expired=new org.json.JSONObject(code.encode()).put("expires",System.currentTimeMillis()-1).toString();write(QrPairingActivity.codeBitmap(expired));var before=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
  try(var c=Robolectric.buildActivity(QrPairingActivity.class).create()){
   c.get().onActivityResult(7403,Activity.RESULT_OK,new Intent().setData(uri));TextView status=ReflectionHelpers.getField(c.get(),"status");await(()->status.getText().toString().contains("QR истёк"));assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
   Bitmap blank=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);blank.eraseColor(Color.WHITE);write(blank);c.get().onActivityResult(7403,Activity.RESULT_OK,new Intent().setData(uri));await(()->status.getText().toString().contains("QR-код не найден"));assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
  }
 }
 @Test public void damagedImageAndNonDocumentUriAreRejected()throws Exception{
  Files.writeString(source.file.toPath(),"not an image");try{QrImageReader.read(app.getContentResolver(),uri);fail();}catch(IOException expected){}
  try{QrImageReader.read(app.getContentResolver(),Uri.fromFile(source.file));fail();}catch(IOException expected){}
 }
}
