package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ru-rRU-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRenderingTest {
    public static class ProgrammaticActivity extends BaseActivity { }

    @Test @Config(sdk = 35)
    public void programmaticScreensRespectSystemBars() {
        org.robolectric.android.controller.ActivityController<ProgrammaticActivity> controller =
                org.robolectric.Robolectric.buildActivity(ProgrammaticActivity.class).create();
        try {
            ProgrammaticActivity activity = controller.get();
            Button button = new Button(activity);
            button.setTag("tile");
            button.setText("Проверить SSH");
            activity.setContentView(button);
            View content = activity.findViewById(android.R.id.content);
            content.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(3, 20, 4, 30))
                    .build());
            assertEquals(3, content.getPaddingLeft());
            assertEquals(20, content.getPaddingTop());
            assertEquals(4, content.getPaddingRight());
            assertEquals(30, content.getPaddingBottom());
            content.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.systemBars(), android.graphics.Insets.of(3, 20, 4, 30))
                    .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 280))
                    .build());
            assertEquals("Keyboard must leave content accessible", 280, content.getPaddingBottom());
            assertNotNull("Programmatic buttons use the common icon treatment", button.getCompoundDrawables()[1]);
        } finally {
            controller.destroy();
        }
    }

    @Test public void dashboardCaptionsFitEveryThemeAndFontSize() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (int theme = 0; theme < ThemeCatalog.IDS.length; theme++) {
            ThemeCatalog.select(app, theme);
            for (float fontScale : new float[]{1f, 1.5f, 2f}) {
                Configuration config = new Configuration(app.getResources().getConfiguration());
                config.fontScale = fontScale;
                Context context = new ContextThemeWrapper(app.createConfigurationContext(config), ThemeCatalog.style(app));
                View root = LayoutInflater.from(context).inflate(R.layout.activity_main, null);
                CompactActions.arrange(root);IconButtons.decorate(root);
                root.findViewById(R.id.homeClientTab).setSelected(true);
                root.findViewById(R.id.homeClientTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscButton)));
                root.findViewById(R.id.homeServerTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));
                ((android.widget.TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер работает · доступ разрешён");
                StatusBadge.apply(root.findViewById(R.id.localServerSummary),StatusBadge.Tone.SUCCESS);
                StatusBadge.apply(root.findViewById(R.id.statusText),StatusBadge.Tone.WARNING);
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 360, 640);
                checkCaptions(root, fontScale);
                assertTrue(root.findViewById(R.id.homeClientTab).getHeight()>=48);
                assertEquals("Role tabs must share one height",root.findViewById(R.id.homeServerTab).getHeight(),root.findViewById(R.id.homeClientTab).getHeight());
                assertSame("Key setup belongs in the connection action list", root.findViewById(R.id.connectionActionsGrid),
                        root.findViewById(R.id.sshKeySetupButton).getParent());
                ViewGroup content = root.findViewById(R.id.dashboardScroll);
                assertNoScrolling(content);
                checkDashboardBounds(content, content);
                assertDashboardCharts(content);
                root.findViewById(R.id.connectionDetailsSheet).setVisibility(View.VISIBLE);
                root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,360,640);
                android.widget.TextView username=root.findViewById(R.id.userValue);
                assertEquals("User label should stay whole",1,username.getLayout().getLineCount());
                root.findViewById(R.id.connectionDetailsSheet).setVisibility(View.GONE);
                root.findViewById(R.id.connectionSheet).setVisibility(View.VISIBLE);
                ActionGridLayout connectionActions=root.findViewById(R.id.connectionActionsGrid);
                root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,360,640);
                checkCaptions(root,fontScale);
                for(int n=0;n<connectionActions.getChildCount();n++)assertTrue("Connection touch area",connectionActions.getChildAt(n).getHeight()>=48);
                root.findViewById(R.id.connectionSheet).setVisibility(View.GONE);
                root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,360,640);
                View preview=root.findViewById(R.id.homeShell);
                Bitmap bitmap = Bitmap.createBitmap(preview.getWidth(), preview.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(ThemeCatalog.color(context, com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
                preview.draw(canvas);
                File directory = new File("build/ui-previews"); directory.mkdirs();
                File file = new File(directory, "dashboard-" + ThemeCatalog.IDS[theme] + "-font-" + fontScale + ".png");
                try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
                bitmap.recycle();
            }
        }
    }
    private void assertNoScrolling(View v){assertFalse("Home must stay static",v instanceof ScrollView);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)assertNoScrolling(((ViewGroup)v).getChildAt(i));}
    @Test @Config(qualifiers="ru-rRU-w640dp-h360dp-land-mdpi")
    public void staticDashboardActionsFitLandscapeWithoutHidingNavigation()throws Exception{
        Context app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,0);
        for(float scale:new float[]{1f,1.5f,2f}){
            Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=scale;
            Context context=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);IconButtons.decorate(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));root.layout(0,0,640,360);
            ViewGroup content=root.findViewById(R.id.dashboardScroll);
            assertNoScrolling(content);checkCaptions(content,scale);checkDashboardBounds(content,content);
            assertDashboardCharts(content);

        }
    }
    private void assertDashboardCharts(ViewGroup home) {
        for (int id : new int[]{R.id.dashboardSpeedChart, R.id.dashboardLatencyChart}) {
            View chart=home.findViewById(id);
            assertNotNull("Both original graphs belong on home",chart);
            for(View at=chart;at!=home;at=(View)at.getParent())
                assertEquals("Graph must be visible without navigation",View.VISIBLE,at.getVisibility());
            assertTrue("Plot needs readable height: "+chart.getHeight(),chart.getHeight()>=48);
            android.graphics.Rect bounds=new android.graphics.Rect(0,0,chart.getWidth(),chart.getHeight());
            home.offsetDescendantRectToMyCoords(chart,bounds);
            assertTrue("Chart outside home: "+bounds,bounds.top>=0&&bounds.bottom<=home.getHeight());
        }
    }
    private void checkDashboardBounds(View v,ViewGroup viewport){
        if(v.getVisibility()==View.GONE)return;
        if(v instanceof Button){android.graphics.Rect bounds=new android.graphics.Rect(0,0,v.getWidth(),v.getHeight());viewport.offsetDescendantRectToMyCoords(v,bounds);assertTrue(((Button)v).getText()+" outside static page: "+bounds+" / "+viewport.getHeight(),bounds.top>=0&&bounds.bottom<=viewport.getHeight());assertTrue(((Button)v).getText()+" touch target is "+v.getHeight(),v.getHeight()>=48);}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checkDashboardBounds(((ViewGroup)v).getChildAt(i),viewport);
    }
    @Test public void fileListRemainsUsableWithLargeControls() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        ThemeCatalog.select(app, 0);
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.fontScale = scale;
            Context context = new ContextThemeWrapper(app.createConfigurationContext(config), ThemeCatalog.style(app));
            Configuration original=new Configuration(app.getResources().getConfiguration());
            app.getResources().updateConfiguration(config,app.getResources().getDisplayMetrics());
            var screen=org.robolectric.Robolectric.buildActivity(FilesActivity.class,new android.content.Intent(app,FilesActivity.class).putExtra("own_server",true)).create().start().resume().visible();
            View root=((ViewGroup)screen.get().findViewById(android.R.id.content)).getChildAt(0);
            IconButtons.decorate(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 360, 640);
            for(boolean local:new boolean[]{true,false}){
                Button tab=org.robolectric.util.ReflectionHelpers.getField(screen.get(),local?"phoneTab":"serverTab");tab.performClick();
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));root.layout(0,0,360,640);
                View list=root.findViewById(local?R.id.phoneFileList:R.id.remoteFileList);View pane=(View)list.getParent();assertTrue(pane.isShown());assertTrue("File list disappeared at fontScale="+scale+" height="+pane.getHeight(),pane.getHeight()>=160);
                assertFalse(((View)root.findViewById(local?R.id.remoteFileList:R.id.phoneFileList).getParent()).isShown());
                android.graphics.Rect bounds=new android.graphics.Rect(0,0,pane.getWidth(),pane.getHeight());((ViewGroup)root).offsetDescendantRectToMyCoords(pane,bounds);assertTrue(bounds.toString(),bounds.top>=0&&bounds.bottom<=640);
                checkDrawerText(root, scale*((FileBrowserLayout)root).controlScale());checkFileControls(root, scale*((FileBrowserLayout)root).controlScale());
            }
            Button send=org.robolectric.util.ReflectionHelpers.getField(screen.get(),"send");
            Button receive=org.robolectric.util.ReflectionHelpers.getField(screen.get(),"receive");
            int[] at=new int[2];receive.getLocationOnScreen(at);
            assertTrue("Copy controls must remain on screen",at[1]>=0 && at[1]+receive.getHeight()<=640);
            android.widget.TextView status=root.findViewById(R.id.transferStatusText);
            assertTrue("Transfer status is readable",status.getTextSize()>=16*scale*((FileBrowserLayout)root).controlScale()-.1f);

            Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ThemeCatalog.color(context, com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
            root.draw(canvas);
            File directory = new File("build/ui-previews"); directory.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "files-font-" + scale + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            bitmap.recycle();
            screen.destroy();app.getResources().updateConfiguration(original,app.getResources().getDisplayMetrics());
        }
    }
    @Test public void filePanesKeepListsAndAllActionsInsideSmallPortraitAndShortLandscapeWindows(){
        Context app=RuntimeEnvironment.getApplication();Configuration original=new Configuration(app.getResources().getConfiguration());
        try{for(int[] size:new int[][]{{320,568},{640,300}})for(float scale:new float[]{1f,1.5f,2f}){
            Configuration config=new Configuration(original);config.fontScale=scale;config.screenWidthDp=size[0];config.screenHeightDp=size[1];app.getResources().updateConfiguration(config,app.getResources().getDisplayMetrics());
            try(var screen=org.robolectric.Robolectric.buildActivity(FilesActivity.class,new android.content.Intent(app,FilesActivity.class).putExtra("own_server",true)).create().start().resume().visible()){
                FileBrowserLayout root=(FileBrowserLayout)((ViewGroup)screen.get().findViewById(android.R.id.content)).getChildAt(0);
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);
                assertNoScrolling(root);checkDashboardBounds(root,root);assertTrue(root.controlScale()>=1/scale-.001f);assertEquals(scale,app.getResources().getConfiguration().fontScale,0);
                for(int id:new int[]{R.id.phoneFileList,R.id.remoteFileList}){View frame=(View)root.findViewById(id).getParent();if(((View)frame.getParent()).getVisibility()!=View.GONE)assertTrue(size[0]+"x"+size[1]+" font="+scale+" list="+frame.getHeight(),frame.getHeight()>=80);}
            }
        }}finally{app.getResources().updateConfiguration(original,app.getResources().getDisplayMetrics());}
    }
    @Test public void serverWizardFitsSmallScreenWithLargeText() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.fontScale = scale;
            Context context = new ContextThemeWrapper(app.createConfigurationContext(config), ThemeCatalog.style(app));
            ScrollView root = (ScrollView) LayoutInflater.from(context).inflate(R.layout.activity_server_setup, null);
            ((android.widget.TextView) root.findViewById(R.id.serverSetupSteps)).setText(
                    "1. Termux: установлен; открой для первого запуска\n2. Папка Termux: доступ подтверждён\n"
                    + "3. Команды Termux: разрешены Android\n4. OpenSSH: подготовлен\n5. Вход SSH: нужно проверить паролем\n"
                    + "6. SSH-ключ: ещё не проверен\n7. HTTPS-агент: ещё не проверен / не установлен");
            IconButtons.decorate(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 360, 640);
            checkCaptions(root, scale);
            android.widget.TextView steps = root.findViewById(R.id.serverSetupSteps);
            assertTrue("Wizard steps clipped at fontScale=" + scale, steps.getLayout().getHeight()
                    <= steps.getHeight() - steps.getCompoundPaddingTop() - steps.getCompoundPaddingBottom());
            View content = root.getChildAt(0);
            Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ThemeCatalog.color(context, com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
            content.draw(canvas);
            File directory = new File("build/ui-previews"); directory.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "server-wizard-font-" + scale + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            bitmap.recycle();
        }
    }
    @Test public void drawerCaptionsFitWithAccessibleWidthAndLargeFont() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.fontScale = scale;
            Context context = new ContextThemeWrapper(app.createConfigurationContext(config), ThemeCatalog.style(app));
            View root = LayoutInflater.from(context).inflate(R.layout.activity_main, null);
            View panel = root.findViewById(R.id.advancedPanel);
            panel.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams params = panel.getLayoutParams();
            params.width = DrawerLayoutPolicy.width(320, scale, 1f);
            assertTrue(params.width >= 160 && params.width <= 296);
            if (scale == 1f) assertEquals(160, params.width);
            if (scale == 2f) assertEquals(296, params.width);
            panel.setLayoutParams(params);
            for (int id : new int[]{R.id.drawerConnectionPane,R.id.drawerWifiPane,R.id.drawerAppearancePane,R.id.drawerToolsPane}) root.findViewById(id).setVisibility(View.VISIBLE);
            IconButtons.decorate(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 320, 640);
            checkDrawerText(panel, scale);
            ScrollView scroll = root.findViewById(R.id.toolsContentScroll);
            View content = scroll.getChildAt(0);
            Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(bitmap));
            File directory = new File("build/ui-previews"); directory.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "drawer-font-" + scale + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            bitmap.recycle();
        }
    }

    @Test public void serverActionTilesAreCenteredAtEveryFontSize() throws Exception {
        Context app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,0);
        for(float scale:new float[]{1f,1.5f,2f}) {
            Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=scale;
            Context context=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));
            View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
            assertEquals(0,context.getResources().getIdentifier("advancedToggleButton","id",context.getPackageName()));
            View panel=root.findViewById(R.id.applicationToolsCard);((ViewGroup)panel.getParent()).removeView(panel);panel.setVisibility(View.VISIBLE);
            ScrollView scroll=new ScrollView(context);scroll.addView(panel);IconButtons.decorate(panel);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));
            scroll.layout(0,0,360,640);checkCaptions(panel,scale);
            Bitmap bitmap=Bitmap.createBitmap(360,panel.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));panel.draw(canvas);
            File directory=new File("build/ui-previews");directory.mkdirs();
            try(FileOutputStream stream=new FileOutputStream(new File(directory,"server-actions-font-"+scale+".png"))) {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
            bitmap.recycle();
        }
    }

    private void checkFileControls(View view,float scale) {
        if(view.getVisibility()==View.GONE)return;
        if(view instanceof Button&&"file_tab".equals(view.getTag())){Button tab=(Button)view;assertTrue(tab.getHeight()>=48);assertTrue(tab.getTextSize()>=16*scale-.1f);assertNull(tab.getCompoundDrawables()[1]);return;}
        if(view instanceof Button){Button b=(Button)view;assertTrue(b.getText()+" too small",b.getTextSize()>=16*scale-.1f);assertTrue(b.getText()+" touch target",b.getHeight()>=48);assertFalse("Unlabelled symbol",java.util.Arrays.asList("↑","↻","⌂","⋮","↓").contains(b.getText().toString()));
            assertTrue("File controls need an icon above a centered caption",b instanceof CenteredActionButton&&((CenteredActionButton)b).hasTileIcon());
            android.graphics.RectF content=((CenteredActionButton)b).contentBounds();assertEquals(b.getText()+" vertical center",b.getHeight()/2f,content.centerY(),.6f);assertEquals(b.getText()+" horizontal center",b.getWidth()/2f,content.centerX(),.6f);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)checkFileControls(group.getChildAt(i),scale);}
    }

    private void checkDrawerText(View view, float scale) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof Button) {
            Button button = (Button) view;
            android.text.Layout layout = button.getLayout();
            assertNotNull(layout);
            assertTrue(button.getText() + " clipped at " + scale, layout.getHeight() <= button.getHeight()
                    - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
            assertEquals(button.getText().toString(), button.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
            for (int i = 0; i < layout.getLineCount(); i++) assertEquals(button.getText().toString(), 0, layout.getEllipsisCount(i));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkDrawerText(group.getChildAt(i), scale);
        }
    }

    private void checkCaptions(View view, float scale) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof Button && ("dashboard_tab".equals(view.getTag()) || "compact_action".equals(view.getTag()) || ((Button) view).getCompoundDrawables()[1] != null
                || (view instanceof CenteredActionButton && ((CenteredActionButton)view).hasTileIcon()))) {
            Button button = (Button) view;
            android.text.Layout layout = button.getLayout();
            assertNotNull(button.getText().toString(), layout);
            if(button instanceof CenteredActionButton) {
                android.graphics.RectF content=((CenteredActionButton)button).contentBounds();
                assertEquals(button.getText()+" vertical center",button.getHeight()/2f,content.centerY(),0.6f);
                assertEquals(button.getText()+" horizontal center",button.getWidth()/2f,content.centerX(),0.6f);
                assertTrue(button.getText()+" content top",content.top>=button.getPaddingTop()-1);
                assertTrue(button.getText()+" content bottom",content.bottom<=button.getHeight()-button.getPaddingBottom()+1);
            }
            assertTrue(button.getText() + " at fontScale=" + scale, layout.getLineCount() <= 2);
            assertEquals(button.getText().toString(), button.getText().length(), layout.getLineEnd(layout.getLineCount()-1));
            assertTrue(button.getText().toString(), layout.getHeight() <= button.getHeight()
                    - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkCaptions(group.getChildAt(i), scale);
        }
    }
}
