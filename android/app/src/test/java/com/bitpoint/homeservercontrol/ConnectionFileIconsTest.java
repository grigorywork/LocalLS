package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
public class ConnectionFileIconsTest {
    private Application app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();
        RouteIdentityTest.Secrets.store.clear();
    }
    private void await(java.util.function.BooleanSupplier done)throws Exception{
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while(!done.getAsBoolean()&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}
        assertTrue("SSH check completed",done.getAsBoolean());
    }
    private void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private void capture(View view,String name,int width,int height)throws Exception{
        layout(view,width,height);Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        File folder=new File("build/ui-previews/dashboard-final-20261023");folder.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }
    @Test public void typesUseExtensionsWithCyrillicNamesAndLocaleIndependentCase(){
        Locale before=Locale.getDefault();try{Locale.setDefault(new Locale("tr","TR"));
            String[] names={"Фото.JPG","Документ.docx","Инструкция.PDF","План.DWG","Копия.DXF","Архив.tar.gz","Видео.MP4","Музыка.flac","Без расширения","secret.unknown"};
            FileTypeIcons.Kind[] expected={FileTypeIcons.Kind.IMAGE,FileTypeIcons.Kind.DOCUMENT,FileTypeIcons.Kind.PDF,FileTypeIcons.Kind.CAD,FileTypeIcons.Kind.CAD,FileTypeIcons.Kind.ARCHIVE,FileTypeIcons.Kind.VIDEO,FileTypeIcons.Kind.AUDIO,FileTypeIcons.Kind.FILE,FileTypeIcons.Kind.FILE};
            for(int i=0;i<names.length;i++)assertEquals(names[i],expected[i],FileTypeIcons.kind(names[i],false));
            assertEquals(FileTypeIcons.Kind.FOLDER,FileTypeIcons.kind("Folder.pdf",true));assertEquals(FileTypeIcons.Kind.FILE,FileTypeIcons.kind(null,false));
        }finally{Locale.setDefault(before);}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void distinctFileGlyphsReuseRowsWithoutChangingSelection()throws Exception{
        Context context=new ContextThemeWrapper(app,ThemeCatalog.style(app));LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,12,12,12);root.setBackgroundColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
        TextView title=new TextView(context);title.setText("Файлы");title.setTextSize(22);title.setTextColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));root.addView(title,new LinearLayout.LayoutParams(-1,48));
        String[] names={"Фотография.jpg","Документ.docx","Инструкция.PDF","План_этажа.DWG","Архив.7z","Видео.mp4","Музыка.flac","Неизвестный.bin","Папка"};
        Set<Integer> glyphs=new HashSet<>();
        for(int i=0;i<names.length;i++){
            FileEntryRow row=new FileEntryRow(context);row.bind(new FileNodes.Node("share",names[i],names[i],i==8,123456),false,value->fail("Binding must not select a file"));root.addView(row,new LinearLayout.LayoutParams(-1,64));
            Drawable icon=FileTypeIcons.drawable(context,names[i],i==8);Bitmap b=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,48,48);icon.draw(new Canvas(b));int[] pixels=new int[48*48];b.getPixels(pixels,0,48,0,0,48,48);glyphs.add(Arrays.hashCode(pixels));assertTrue(Arrays.stream(pixels).anyMatch(p->Color.alpha(p)>0));b.recycle();
        }
        assertEquals("Each file category has a distinct glyph",names.length,glyphs.size());
        FileEntryRow recycled=(FileEntryRow)root.getChildAt(1);List<Boolean> selection=new ArrayList<>();recycled.bind(new FileNodes.Node("share","x.pdf","Фото.jpg",false,7),true,selection::add);assertTrue(recycled.isChecked());recycled.bind(new FileNodes.Node("share","x.dwg","План.DWG",false,8),false,selection::add);assertFalse(recycled.isChecked());assertTrue(selection.isEmpty());((CheckBox)recycled.getChildAt(0)).performClick();assertEquals(List.of(true),selection);
        recycled.bind(new FileNodes.Node("share","photo.jpg",names[0],false,123456),false,value->{});
        capture(root,"08-file-types.png",411,720);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void bothCheckButtonsUsePinnedSshAndExposeTheResult()throws Exception{
        var keys=SshKeys.generate();var gen=java.security.KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var host=gen.generateKeyPair();File root=Files.createTempDirectory(app.getCacheDir().toPath(),"ssh-button").toFile();
        try(var server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(keys.getString(SshKeys.PUBLIC)),host,root.toPath());String pin=EmbeddedSftpServer.fingerprint(host);
            app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,"127.0.0.1").putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,port).putString(SshKeys.MODE,"key").commit();SshKeys.save(app,"127.0.0.1",port,"localls",keys);HostTrustStore.pair(app,"127.0.0.1",port,"localls",pin);
            try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=screen.get();View view=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);layout(view,411,890);
                Button home=a.findViewById(R.id.checkButton),ssh=a.findViewById(R.id.connectionCheckButton);assertEquals("Главная",home.getText().toString());assertEquals("Главная. Проверить сервер",home.getContentDescription());assertEquals("Проверить SSH",ssh.getText().toString());assertTrue(((CenteredActionButton)home).hasTileIcon());RectF bounds=((CenteredActionButton)home).contentBounds();assertEquals(home.getHeight()/2f,bounds.centerY(),1f);
                a.findViewById(R.id.drawerConnectionHeader).performClick();assertTrue(ssh.isShown());ssh.performClick();assertFalse(home.isEnabled());assertFalse(ssh.isEnabled());assertEquals(ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscWarning),((TextView)a.findViewById(R.id.connectionCheckStatus)).getCurrentTextColor());await(()->!ReflectionHelpers.<Boolean>getField(a,"checking"));assertTrue(home.isEnabled());assertTrue(ssh.isEnabled());TextView status=a.findViewById(R.id.connectionCheckStatus);assertTrue(status.getText().toString().contains(((TextView)a.findViewById(R.id.statusText)).getText()));assertEquals(pin,HostTrustStore.get(app,"127.0.0.1",port));assertEquals(ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess),status.getCurrentTextColor());
                capture(view,"09-check-ssh.png",411,890);
                server.close();server.start("127.0.0.1",port,"localls",EmbeddedSftpServer.authorizedKey(keys.getString(SshKeys.PUBLIC)),gen.generateKeyPair(),root.toPath());a.findViewById(R.id.connectionCloseButton).performClick();assertTrue(home.isShown());home.performClick();await(()->!ReflectionHelpers.<Boolean>getField(a,"checking"));assertEquals(pin,HostTrustStore.get(app,"127.0.0.1",port));assertEquals(ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscError),status.getCurrentTextColor());
                ((EditText)a.findViewById(R.id.localHostInput)).setText("192.0.2.8");assertEquals(a.getString(R.string.ssh_check_changed),status.getText().toString());
            }
        }
    }
}
