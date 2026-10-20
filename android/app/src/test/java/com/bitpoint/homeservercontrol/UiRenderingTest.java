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
                IconButtons.decorate(root);
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 360, 640);
                checkCaptions(root, fontScale);
                assertSame("Key setup belongs in connection settings", root.findViewById(R.id.drawerConnectionPane),
                        root.findViewById(R.id.sshKeySetupButton).getParent());
                android.widget.TextView username = root.findViewById(R.id.userValue);
                assertEquals("User label should stay whole", 1, username.getLayout().getLineCount());
                ScrollView scroll = root.findViewById(R.id.dashboardScroll);
                View content = scroll.getChildAt(0);
                Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(ThemeCatalog.color(context, com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
                content.draw(canvas);
                File directory = new File("build/ui-previews"); directory.mkdirs();
                File file = new File(directory, "dashboard-" + ThemeCatalog.IDS[theme] + "-font-" + fontScale + ".png");
                try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
                bitmap.recycle();
            }
        }
    }
    @Test public void fileListRemainsUsableWithLargeControls() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        ThemeCatalog.select(app, 0);
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.fontScale = scale;
            Context context = new ContextThemeWrapper(app.createConfigurationContext(config), ThemeCatalog.style(app));
            View root = LayoutInflater.from(context).inflate(R.layout.activity_files, null);
            IconButtons.decorate(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 360, 640);
            assertTrue("File list disappeared at fontScale=" + scale, root.findViewById(R.id.remoteFileList).getHeight() >= 80);
            checkCaptions(root, scale);
            Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ThemeCatalog.color(context, com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
            root.draw(canvas);
            File directory = new File("build/ui-previews"); directory.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "files-font-" + scale + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            bitmap.recycle();
        }
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
            for (int id : new int[]{R.id.drawerConnectionPane, R.id.drawerSettingsPane,
                    R.id.drawerToolsPane, R.id.drawerVpnPane}) root.findViewById(id).setVisibility(View.VISIBLE);
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
            assertEquals(View.GONE,root.findViewById(R.id.advancedToggleButton).getVisibility());
            View panel=root.findViewById(R.id.serverManagementPanel);((ViewGroup)panel.getParent()).removeView(panel);panel.setVisibility(View.VISIBLE);
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
        if (view instanceof Button && (((Button) view).getCompoundDrawables()[1] != null
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
