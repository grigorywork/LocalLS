package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AllButtonsPolishTest {
    @Before public void reset(){
        var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;EmbeddedServerService.status="Сервер выключен";ThemeCatalog.select(app,0);
    }
    private void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private void verify(View view){
        if(view.getVisibility()==View.GONE)return;
        if(view instanceof Button&&!(view instanceof CompoundButton)&&CompactActions.marked((Button)view)){
            Button button=(Button)view;assertTrue(button.getText()+" touch height",button.getHeight()>=56);assertEquals(button.getText()+" row spans the available width",((View)button.getParent()).getWidth()-((View)button.getParent()).getPaddingLeft()-((View)button.getParent()).getPaddingRight(),button.getWidth());
            assertNotNull(button.getText().toString(),button.getLayout());int last=button.getLayout().getLineCount()-1;
            assertEquals(button.getText().toString(),button.getText().length(),button.getLayout().getLineEnd(last));assertEquals(0,button.getLayout().getEllipsisCount(last));
            assertTrue(button.getText()+" caption clipped",button.getLayout().getHeight()<=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom());
            assertTrue("Rows have a flat background",button.getBackground() instanceof ListActionDrawable);
            assertNull("List actions keep icons inline",button.getCompoundDrawables()[1]);assertNotNull(button.getCompoundDrawablesRelative()[0]);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)verify(group.getChildAt(i));}
    }
    private void capture(BaseActivity activity,String name)throws Exception{
        View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);layout(root,411,890);verify(root);
        if(name.endsWith("-bottom")){ScrollView scrolling=scroll(root);assertNotNull(scrolling);int offset=scrolling.getChildAt(0).getHeight()-scrolling.getHeight()+scrolling.getPaddingTop()+scrolling.getPaddingBottom();assertTrue(offset>0);scrolling.scrollTo(0,offset);assertTrue("Bottom state must actually be scrolled",scrolling.getScrollY()>0);}
        if(name.equals("MainMenu")){View panel=activity.findViewById(R.id.advancedPanel);assertEquals(View.VISIBLE,panel.getVisibility());panel.animate().cancel();panel.setTranslationX(0);}
        Bitmap bitmap=Bitmap.createBitmap(411,890,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(ThemeCatalog.color(activity,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
        // Direct root.draw has no parent/window compositor to apply the root's scroll.
        if(name.endsWith("-bottom")&&root instanceof ScrollView){View child=((ScrollView)root).getChildAt(0);canvas.save();canvas.translate(child.getLeft()-root.getScrollX(),child.getTop()-root.getScrollY());child.draw(canvas);canvas.restore();}
        else root.draw(canvas);
        File folder=new File("build/ui-previews/list-actions-20261024");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private ScrollView scroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ScrollView found=scroll(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    @Test public void allSettingsPagesHaveReadableCompactCommandsAndNativePreviews()throws Exception{
        Class<?>[] screens={EmbeddedServerActivity.class,SecurityActivity.class,ProfileTransferActivity.class,SettingsActivity.class,NetworkActivity.class,DiagnosticsActivity.class,ServerSetupActivity.class,PhoneSetupGuideActivity.class,ShareUploadActivity.class,TransferQueueActivity.class,HistoryActivity.class,EmbeddedFilesActivity.class,ServerDevicesActivity.class,QrPairingActivity.class,ServerReceiptActivity.class};
        for(Class<?> screen:screens){
            @SuppressWarnings("unchecked") Class<? extends BaseActivity> type=(Class<? extends BaseActivity>)screen;
            Intent intent=new Intent(RuntimeEnvironment.getApplication(),type);if(type==QrPairingActivity.class)intent.putExtra("server",true);
            try(var controller=Robolectric.buildActivity(type,intent).setup()){
                BaseActivity activity=controller.get();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();capture(activity,screen.getSimpleName());
                View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                if(type==EmbeddedServerActivity.class||type==ServerSetupActivity.class)capture(activity,screen.getSimpleName()+"-bottom");
                for(int[] size:new int[][]{{320,568},{1280,800}}){layout(root,size[0],size[1]);verify(root);}
            }
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();activity.findViewById(R.id.toolsMenuButton).performClick();capture(activity,"MainMenu");
            activity.findViewById(R.id.drawerConnectionHeader).performClick();capture(activity,"Connection");capture(activity,"Connection-bottom");
            View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);scroll(root).scrollTo(0,0);layout(root,1280,800);verify(root);
            // Also capture a wide screen with the exact same full-width row treatment.
            Bitmap tablet=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);root.draw(new Canvas(tablet));
            try(OutputStream out=new FileOutputStream(new File("build/ui-previews/list-actions-20261024/Connection-tablet.png"))){assertTrue(tablet.compress(Bitmap.CompressFormat.PNG,100,out));}finally{tablet.recycle();}
        }
        float originalFont=RuntimeEnvironment.getFontScale();
        try{
            RuntimeEnvironment.setFontScale(2f);
            for(Class<? extends BaseActivity> type:Arrays.asList(EmbeddedServerActivity.class,SecurityActivity.class,SettingsActivity.class))try(var controller=Robolectric.buildActivity(type)){
                controller.setup();assertEquals(2,controller.get().getResources().getConfiguration().fontScale,0);
                View root=((ViewGroup)controller.get().findViewById(android.R.id.content)).getChildAt(0);layout(root,320,568);verify(root);
            }
        }finally{RuntimeEnvironment.setFontScale(originalFont);}
    }
    @Test public void actionRowsStayFlatAcrossThemesAndShapesAndExposePressAndDisabledStates(){
        var app=RuntimeEnvironment.getApplication();
        for(int theme=0;theme<ThemeCatalog.IDS.length;theme++){
            ThemeCatalog.select(app,theme);Context context=new ContextThemeWrapper(app,ThemeCatalog.style(app));
            LinearLayout column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);
            Button row=new Button(context);row.setText("Копировать адрес");column.addView(row,new LinearLayout.LayoutParams(-1,-2));
            for(int shape:new int[]{0,1,2,3}){
                IconShapeCatalog.select(app,shape);CompactActions.arrange(column);IconButtons.decorate(column);layout(column,360,100);
                assertEquals(shape,IconShapeCatalog.index(app));assertEquals(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary),row.getCurrentTextColor());
                assertNull(row.getBackgroundTintList());assertEquals(0,row.getElevation(),0);
                Bitmap bitmap=Bitmap.createBitmap(row.getWidth(),row.getHeight(),Bitmap.Config.ARGB_8888);
                try{
                    Canvas canvas=new Canvas(bitmap);row.draw(canvas);bitmap.eraseColor(Color.TRANSPARENT);row.getBackground().draw(canvas);
                    assertEquals("No filled button or outer border",0,Color.alpha(bitmap.getPixel(1,1)));assertEquals(0,Color.alpha(bitmap.getPixel(180,20)));
                    assertTrue("Thin inset separator",Color.alpha(bitmap.getPixel(180,bitmap.getHeight()-1))>0);assertEquals(0,Color.alpha(bitmap.getPixel(1,bitmap.getHeight()-1)));
                    row.setPressed(true);bitmap.eraseColor(Color.TRANSPARENT);row.getBackground().draw(canvas);assertTrue("Press feedback",Color.alpha(bitmap.getPixel(180,20))>0);
                    row.setPressed(false);row.setEnabled(false);bitmap.eraseColor(Color.TRANSPARENT);row.getBackground().draw(canvas);assertEquals("Disabled actions do not highlight",0,Color.alpha(bitmap.getPixel(180,20)));assertEquals(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary),row.getCurrentTextColor());row.setEnabled(true);
                }finally{bitmap.recycle();}
            }
        }
    }
    @Test public void regroupingPreservesButtonIdentityCallbacksEnabledAndHiddenStates(){
        var app=RuntimeEnvironment.getApplication();Context context=new ContextThemeWrapper(app,ThemeCatalog.style(app));LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);
        int[] taps={0};Button one=new Button(context),two=new Button(context),three=new Button(context);
        one.setId(View.generateViewId());two.setId(View.generateViewId());three.setId(View.generateViewId());one.setText("Копировать адрес");two.setText("Сохранить настройки");three.setText("Подтвердить вход");
        one.setOnClickListener(v->taps[0]++);two.setEnabled(false);three.setVisibility(View.GONE);
        for(Button button:new Button[]{one,two,three})root.addView(button,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<3;i++){CompactActions.arrange(root);IconButtons.decorate(root);layout(root,1000,400);assertSame(one,root.findViewById(one.getId()));assertSame(two,root.findViewById(two.getId()));assertSame(three,root.findViewById(three.getId()));assertFalse(two.isEnabled());assertEquals(View.GONE,three.getVisibility());verify(root);}
        LinearLayout pair=new LinearLayout(context);Button left=new Button(context),right=new Button(context);left.setText("Открыть папку");right.setText("Назад");
        pair.addView(left,new LinearLayout.LayoutParams(0,-2,1));pair.addView(right,new LinearLayout.LayoutParams(0,-2,1));root.addView(pair,new LinearLayout.LayoutParams(-1,-2));
        CompactActions.arrange(root);IconButtons.decorate(root);layout(root,1000,500);assertEquals(LinearLayout.VERTICAL,pair.getOrientation());assertTrue(right.getTop()>=left.getBottom());verify(root);
        one.performClick();assertEquals(1,taps[0]);one.setVisibility(View.GONE);three.setVisibility(View.VISIBLE);layout(root,1000,400);verify(root);assertEquals(1000,three.getWidth());assertEquals(one.getParent(),three.getParent());assertTrue(three.getTop()>=two.getBottom());assertFalse(two.isEnabled());
    }
}
