package com.bitpoint.homeservercontrol;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeAlignmentTest {
    @Before public void setup(){
        var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();ThemeCatalog.select(app,1);
    }
    private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private void layout(View v,int width,int height){v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);}
    private void capture(View v,String name,int width,int height)throws Exception{
        layout(v,width,height);Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));File folder=new File("build/ui-previews/dashboard-final-20261023");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }
    private void stats(MainActivity a,boolean known){
        ServerStats stats=new ServerStats();if(known){new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080000,1).apply(stats);stats.authenticated=true;stats.reachable=true;stats.hostKeyTrusted=true;}
        ReflectionHelpers.callInstanceMethod(a,"renderStats",ReflectionHelpers.ClassParameter.from(ServerStats.class,stats),ReflectionHelpers.ClassParameter.from(String.class,"192.168.1.151"),ReflectionHelpers.ClassParameter.from(int.class,8022),ReflectionHelpers.ClassParameter.from(String.class,"localls"));
    }
    @Test public void titlesValuesAndActionGroupsStayCenteredAtSmallAndLargeSizes()throws Exception{
        var previous=TransferForegroundService.state;
        try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=screen.get();DashboardLayout home=a.findViewById(R.id.dashboardScroll);TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",68,100,1);
            for(boolean known:new boolean[]{false,true}){
                stats(a,known);home.transfers("Видео_отпуск.mp4","68% · 1,36 из 2 ГБ",68,"✓ Фото.zip · Отправлено\n! Документы.pdf · Ошибка\n× Архив.zip · Отменено");
                for(int[] size:new int[][]{{320,568},{360,640},{411,890},{890,411},{1280,800}}){
                    layout(root(a),size[0],size[1]);ViewGroup actions=a.findViewById(R.id.applicationToolsCard);
                    assertEquals("Главная",((Button)a.findViewById(R.id.checkButton)).getText().toString());
                    for(int i=0;i<actions.getChildCount();i++){CenteredActionButton b=(CenteredActionButton)actions.getChildAt(i);assertEquals("Every action occupies full bar height",actions.getHeight(),b.getHeight());assertTrue(b.getHeight()>=48);assertEquals("Icon and caption center",b.getHeight()/2f,b.contentBounds().centerY(),1f);}
                    for(String field:new String[]{"storage","battery","transfer","recent"}){
                        LinearLayout card=ReflectionHelpers.getField(home,field);TextView heading=(TextView)card.getChildAt(0);assertTrue("Title inset "+field,heading.getTop()>=4);assertTrue("Heading space "+field,heading.getHeight()>=20);
                        Paint.FontMetrics fm=heading.getPaint().getFontMetrics();assertEquals("Text vertically centered "+field,heading.getHeight()/2f,heading.getBaseline()+(fm.ascent+fm.descent)/2f,2f);
                    }
                    for(int id:new int[]{R.id.diskValue,R.id.batteryValue}){TextView value=a.findViewById(id);Rect visible=new Rect();assertTrue(value.getGlobalVisibleRect(visible));assertTrue("Visible metric "+size[0]+"x"+size[1]+" id "+id+" height "+visible.height(),visible.height()>=value.getLineHeight());}
                    List<ImageView> icons=ReflectionHelpers.getField(home,"recentIcons");List<LinearLayout> rows=ReflectionHelpers.getField(home,"recentRows");for(int i=0;i<icons.size();i++)assertTrue("History glyph is not cropped",icons.get(i).getHeight()<=rows.get(i).getHeight());
                    if(size[0]<size[1]){CenteredActionButton link=a.findViewById(R.id.drawerConnectionHeader);View connection=a.findViewById(R.id.remoteConnectionCard);assertTrue(link.hasTileIcon());assertEquals(connection.getHeight()/2f,link.getTop()+link.getHeight()/2f,1f);assertEquals(link.getHeight()/2f,link.contentBounds().centerY(),1f);}
                }
                capture(root(a),known?"10-ocean-home.png":"11-ocean-home-unknown.png",411,890);
            }
            capture(root(a),"12-ocean-tablet.png",1280,800);
        }finally{TransferForegroundService.state=previous;}
    }
    @Test public void qrEntrySitsDirectlyAfterAccessKeyAndStillOpensTheScanner()throws Exception{
        try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=screen.get();ActionGridLayout pane=a.findViewById(R.id.connectionActionsGrid);assertSame(a.findViewById(R.id.drawerConnectionPane),pane.getParent());Button key=a.findViewById(R.id.sshKeySetupButton),qr=a.findViewById(R.id.scanQrButton);assertSame(pane,qr.getParent());assertEquals(pane.indexOfChild(key)+1,pane.indexOfChild(qr));assertEquals("Вход по QR-коду",qr.getText().toString());
            a.findViewById(R.id.drawerConnectionHeader).performClick();assertTrue(qr.isShown());capture(root(a),"13-ocean-connection.png",411,890);
            for(int[] size:new int[][]{{320,568},{360,640},{411,890},{1280,800}}){
                layout(root(a),size[0],size[1]);assertTrue("QR follows the access key as its own row",qr.getTop()>=key.getBottom());assertEquals(key.getLeft(),qr.getLeft());assertTrue(key.getHeight()>=48);assertTrue(qr.getHeight()>=48);
                for(int i=0;i<pane.getChildCount();i++){Button action=(Button)pane.getChildAt(i);assertNull("List actions keep the icon beside the caption",action.getCompoundDrawables()[1]);assertEquals("Each action spans the whole list width",pane.getWidth(),action.getWidth());assertTrue(action.getBackground() instanceof ListActionDrawable);if(size[0]==411)assertEquals("Normal phone captions stay on one line",1,action.getLayout().getLineCount());assertEquals(action.getText().length(),action.getLayout().getLineEnd(action.getLayout().getLineCount()-1));assertEquals("Caption fits without ellipsis",0,action.getLayout().getEllipsisCount(action.getLayout().getLineCount()-1));}
            }
            capture(root(a),"14-list-connection-tablet.png",1280,800);
            for(int shape:new int[]{1,2,3}){IconShapeCatalog.select(a,shape);a.polishControls();assertEquals("No retired shape control",0,a.getResources().getIdentifier("iconShapeButton","id",a.getPackageName()));assertEquals(shape,IconShapeCatalog.index(a));capture(root(a),"15-connection-shape-"+shape+".png",411,890);}
            qr.performClick();var intent=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(intent);assertEquals(QrPairingActivity.class.getName(),intent.getComponent().getClassName());
        }
    }
}
