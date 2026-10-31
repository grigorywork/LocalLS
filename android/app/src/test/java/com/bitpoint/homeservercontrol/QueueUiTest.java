package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QueueUiTest {
    @Before public void setup(){var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;TransferForegroundService.clearing=false;}
    @Test public void timingStaysSmallDisjointAndReadableAcrossPhoneTabletLightAndGlass()throws Exception{
        var app=RuntimeEnvironment.getApplication();TransferJournal journal=new TransferJournal(app);journal.clearAll();
        FileNodes.Node file=new FileNodes.Node("share","video.mp4","Видео_отпуск.mp4",false,200*1048576);Intent job=QueueEstimateTest.request(app,file);journal.ensure(job);
        for(int n=0;n<66;n++)journal.ensure(QueueEstimateTest.request(app,new FileNodes.Node("share","file"+n+".png","Фото_"+n+".png",false,16*1048576)));
        TransferForegroundService.State previous=TransferForegroundService.state;
        try{TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",136*1048576,200*1048576,67,job.getStringExtra("request_id"),TransferJournal.key(file));TransferForegroundService.currentName=file.name;
            QueueEstimate estimate=QueueEstimate.read(app,journal.list(),TransferForegroundService.state,2);
            for(int theme:new int[]{0,4})for(boolean glass:new boolean[]{false,true})for(float font:new float[]{1,2}){
                ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(cfg),ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);
                View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);
                try(var screen=Robolectric.buildActivity(Activity.class).setup()){
                    screen.get().setContentView(root);IconButtons.decorate(root);ThemeCatalog.surfaces(root);root.findViewById(R.id.homeClientTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscButton)));root.findViewById(R.id.homeServerTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));home.transfers(file.name,"136 МБ из 200 МБ",68,"✓ Фото.jpg · 12 МБ · Отправлено\n✓ Архив.zip · 240 МБ · Отправлено\n✓ Документы.pdf · 8 МБ · Отправлено");home.transferTiming(2,136*1048576,200*1048576);home.queueSummary(estimate,true);
                    for(int[] size:new int[][]{{320,568},{360,640},{411,890},{890,411},{800,1280},{1280,800}}){layout(root,size[0],size[1]);TransferTimingStrip strip=ReflectionHelpers.getField(home,"queueMeta");assertStrip(strip);ViewGroup transfer=ReflectionHelpers.getField(home,"transfer");assertInside(transfer,home);ViewGroup recent=ReflectionHelpers.getField(home,"recent");assertInside(recent,home);assertTrue("Transfer does not overlap recent card",transfer.getBottom()<=recent.getTop());if(recent.getBottom()>root.findViewById(R.id.applicationToolsCard).getTop())capture(root,"debug-overlap");assertTrue("Recent card does not overlap navigation theme="+theme+" glass="+glass+" font="+font+" size="+Arrays.toString(size)+" recent="+recent.getTop()+".."+recent.getBottom()+" navigation="+root.findViewById(R.id.applicationToolsCard).getTop(),recent.getBottom()<=root.findViewById(R.id.applicationToolsCard).getTop());assertTrue("All metadata remains in transfer card",strip.getBottom()<=transfer.getHeight()-transfer.getPaddingBottom());
                        TextView queue=ReflectionHelpers.getField(home,"transferEta");assertTrue("Timing stays small",queue.getTextSize()<=14*c.getResources().getDisplayMetrics().scaledDensity);ActionGridLayout actions=ReflectionHelpers.getField(home,"queueActions");if(actions.isShown())for(int n=0;n<actions.getChildCount();n++)assertTrue("Accessible action height",actions.getChildAt(n).getHeight()>=48);
                        if(theme==0&&!glass&&font==2&&size[0]==320)capture(root,"debug-font2-small");if(theme==0&&!glass&&font==1&&(size[0]==411||size[0]==1280))capture(root,"home-"+size[0]+"x"+size[1]);
                    }
                }finally{server.close();}
                if(font==1)try(var queueScreen=Robolectric.buildActivity(TransferQueueActivity.class).setup()){
                    TransferQueueActivity a=queueScreen.get();ReflectionHelpers.callInstanceMethod(a,"render",ReflectionHelpers.ClassParameter.from(List.class,journal.list()),ReflectionHelpers.ClassParameter.from(TransferForegroundService.State.class,TransferForegroundService.state),ReflectionHelpers.ClassParameter.from(QueueEstimate.class,estimate),ReflectionHelpers.ClassParameter.from(float.class,2f));View queueRoot=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
                    for(int[] size:new int[][]{{411,890},{1280,800}}){layout(queueRoot,size[0],size[1]);LinearLayout live=ReflectionHelpers.getField(a,"live");assertStrip((TransferTimingStrip)live.getChildAt(2));ActionGridLayout toolbar=ReflectionHelpers.getField(a,"actions");for(int k=0;k<toolbar.getChildCount();k++){Button button=(Button)toolbar.getChildAt(k);assertEquals("Readable button text in light/glass",ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary),button.getCurrentTextColor());assertTrue(button.getBackground().getClass().getName(),SurfaceStyle.owned(button.getBackground()));}if(!glass||theme==4)capture(queueRoot,"queue-"+(theme==4?"light":"dark")+(glass?"-glass":"")+"-"+size[0]+"x"+size[1]);}
                }
            }
        }finally{TransferForegroundService.state=previous;}
    }
    @Test public void unknownPausedAndFinishingCaptionsKeepTheirOwnRows(){
        var app=RuntimeEnvironment.getApplication();Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));
        for(String timing:new String[]{"Вся очередь: Уточняем объём…","Вся очередь: Завершаем передачу…","Вся очередь: Ожидает продолжения"}){
            TextView amount=new TextView(c),queue=new TextView(c),file=new TextView(c);for(TextView t:new TextView[]{amount,queue,file})t.setTextSize(12);amount.setText("136 МБ из 200 МБ");queue.setText(timing);file.setText("Осталось ≈ 32 с");TransferTimingStrip strip=new TransferTimingStrip(c,amount,queue,file);layout(strip,320,100);assertStrip(strip);
        }
    }
    @Test public void waitingInterruptedAndFinalizingStatesKeepRecentRowsAndNavigationVisible()throws Exception{
        var app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,0);TransferJournal journal=new TransferJournal(app);journal.clearAll();journal.ensure(QueueEstimateTest.request(app,new FileNodes.Node("share","file.mp4","file.mp4",false,200*1048576)));TransferForegroundService.State previous=TransferForegroundService.state;
        try{for(float font:new float[]{1,2}){Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(cfg),ThemeCatalog.style(app));View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);
            try(var screen=Robolectric.buildActivity(Activity.class).setup()){screen.get().setContentView(root);IconButtons.decorate(root);for(int phase=0;phase<3;phase++){
                boolean active=phase!=0;long total=phase==2?200*1048576:0,bytes=phase==2?total:0;String detail=phase==0?"Сохранено незавершённых заданий: 2. Открой очередь для продолжения.":phase==1?"Подготовка передачи…":"200 МБ из 200 МБ";TransferForegroundService.state=new TransferForegroundService.State(active,phase==0,detail,"",bytes,total,1);home.transfers(active?"Видео.mp4":"Передача не выполняется",detail,phase==2?100:0,"✓ Фото.jpg · 12 МБ · Отправлено\n✓ Архив.zip · 240 МБ · Отправлено\n✓ Документы.pdf · 8 МБ · Отправлено");home.transferTiming(0,bytes,total);home.queueSummary(QueueEstimate.read(app,journal.list(),TransferForegroundService.state,0),true);
                for(int[] size:new int[][]{{320,568},{411,890},{890,411},{1280,800}}){layout(root,size[0],size[1]);ViewGroup recent=ReflectionHelpers.getField(home,"recent");assertInside(recent,home);assertTrue("Recent/navigation collision in phase="+phase+" font="+font+" size="+Arrays.toString(size),recent.getBottom()<=root.findViewById(R.id.applicationToolsCard).getTop());for(TextView name:ReflectionHelpers.<List<TextView>>getField(home,"recentNames"))assertTrue("Recent filename remains visible phase="+phase,name.getHeight()>=name.getLineHeight());}
            }}finally{server.close();}
        }}finally{TransferForegroundService.state=previous;}
    }
    private void assertStrip(TransferTimingStrip strip){List<Rect> areas=new ArrayList<>();for(TextView t:new TextView[]{strip.amount,strip.queue,strip.file}){Rect r=new Rect(t.getLeft(),t.getTop(),t.getRight(),t.getBottom());assertTrue("Metadata stays inside strip "+t.getText()+" "+r+" / "+strip.getHeight(),r.left>=0&&r.right<=strip.getWidth()&&r.top>=0&&r.bottom<=strip.getHeight());assertTrue("Full line height",t.getHeight()>=t.getLayout().getHeight());for(int n=0;n<t.getLineCount();n++)assertEquals("No timing ellipsis",0,t.getLayout().getEllipsisCount(n));for(Rect previous:areas)assertFalse("Metadata never overlaps",Rect.intersects(r,previous));areas.add(r);}}
    private void assertInside(View child,ViewGroup parent){Rect r=new Rect();child.getDrawingRect(r);parent.offsetDescendantRectToMyCoords(child,r);assertTrue("Card within dashboard "+r,r.top>=0&&r.left>=0&&r.right<=parent.getWidth()&&r.bottom<=parent.getHeight());}
    private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)force(((ViewGroup)v).getChildAt(n));}
    private void layout(View root,int w,int h){force(root);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    private void capture(View root,String name)throws Exception{File out=new File("build/ui-previews/queue-1.39-20261031");out.mkdirs();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(FileOutputStream stream=new FileOutputStream(new File(out,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{b.recycle();}}
}
