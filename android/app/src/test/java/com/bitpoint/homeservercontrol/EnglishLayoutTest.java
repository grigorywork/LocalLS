package com.bitpoint.homeservercontrol;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EnglishLayoutTest {
 @Test public void englishScreensFitPhonesTabletsBothOrientationsLargeFontsAndLightAccents()throws Exception{
  Context c=RuntimeEnvironment.getApplication();float previous=RuntimeEnvironment.getFontScale();AppLanguage.select(c,"en");
  try{
   for(boolean light:new boolean[]{false,true})for(int theme:new int[]{0,1,2,3,5,6,7})for(float font:new float[]{1,2}){
    RuntimeEnvironment.setFontScale(font);ThemeCatalog.select(c,theme);ThemeCatalog.light(c,light);ThemeCatalog.glass(c,true);
    for(Class<? extends BaseActivity> kind:java.util.Arrays.asList(MainActivity.class,FilesActivity.class,AppearanceActivity.class,EmbeddedServerActivity.class,TransferQueueActivity.class,AboutActivity.class)){
     try(var owner=Robolectric.buildActivity(kind).setup()){
      BaseActivity a=owner.get();assertEquals("en",a.getResources().getConfiguration().getLocales().get(0).getLanguage());View root=a.findViewById(android.R.id.content);
      for(int[] size:new int[][]{{320,568},{411,890},{890,411},{1280,800},{800,1280}}){
       root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);check(root);
       if(font==1&&theme==1&&size[0]==411&&(kind==MainActivity.class||kind==AppearanceActivity.class||kind==FilesActivity.class)){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size[0],size[1],android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));File folder=new File("build/ui-previews/english-20261110");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,kind.getSimpleName()+"-"+(light?"light":"dark")+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
       }
      }
     }
    }
   }
  }finally{RuntimeEnvironment.setFontScale(previous);AppLanguage.select(c,"ru");ThemeCatalog.light(c,false);ThemeCatalog.glass(c,false);}
 }
 private static void check(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof TextView){TextView text=(TextView)v;assertFalse("English UI label left Russian: "+text.getText(),java.util.regex.Pattern.compile("[А-Яа-яЁё]").matcher(text.getText()).find());if(text.getParent() instanceof View){View p=(View)text.getParent();assertTrue("Negative width",text.getWidth()>=0);assertTrue("Horizontal overflow: "+text.getText(),text.getRight()<=p.getWidth()+2||p instanceof HorizontalScrollView);}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)check(g.getChildAt(i));}}
}
