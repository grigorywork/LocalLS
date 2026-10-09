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
                android.widget.EditText password = root.findViewById(R.id.passwordInput);
                assertTrue("Password input clipped", password.getLayout().getHeight() <= password.getHeight()
                        - password.getCompoundPaddingTop() - password.getCompoundPaddingBottom());
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
    private void checkCaptions(View view, float scale) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof Button && ((Button) view).getCompoundDrawables()[1] != null) {
            Button button = (Button) view;
            android.text.Layout layout = button.getLayout();
            assertNotNull(button.getText().toString(), layout);
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
