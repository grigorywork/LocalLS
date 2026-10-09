package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TypographyInstrumentedTest {
    @Test public void captionsFitAtNormalAndLargeSystemFontSizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (float scale : new float[]{1f, 1.5f, 2f}) {
                Configuration config = new Configuration(base.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = new ContextThemeWrapper(base.createConfigurationContext(config),
                        com.bitpoint.homeservercontrol.ui.R.style.Theme_HomeServerControl);
                Button button = new Button(context);
                button.setTag("tile");
                button.setText("Р¤Р°Р№Р»С‹\nСЃРµСЂРІРµСЂР°");
                button.setLayoutParams(new LinearLayout.LayoutParams(0, 52));
                IconButtons.apply(button, com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_folder);
                int width = Math.round(120 * context.getResources().getDisplayMetrics().density);
                int height = button.getLayoutParams().height;
                button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                button.layout(0, 0, width, height);
                int available = height - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom();
                assertNotNull(button.getLayout());
                assertTrue("Caption clipped at fontScale=" + scale, button.getLayout().getHeight() <= available);
                assertEquals(2, button.getLayout().getLineCount());
            }
        });
    }
    @Test public void actionRowsKeepFullCaptionsOnNarrowScreens() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (float scale : new float[]{1f, 1.5f, 2f}) {
                Configuration config = new Configuration(base.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = new ContextThemeWrapper(base.createConfigurationContext(config),
                        com.bitpoint.homeservercontrol.ui.R.style.Theme_HomeServerControl);
                ActionGridLayout grid = new ActionGridLayout(context);
                for (String caption : new String[]{"Проверить SSH", "Диагностика", "Перезапуск SSH"}) {
                    Button button = new Button(context);
                    button.setTag("tile"); button.setText(caption);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 64);
                    lp.leftMargin = lp.rightMargin = Math.round(3 * context.getResources().getDisplayMetrics().density);
                    grid.addView(button, lp);
                    IconButtons.apply(button, com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_server);
                }
                int width = Math.round(320 * context.getResources().getDisplayMetrics().density);
                grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
                grid.layout(0, 0, width, grid.getMeasuredHeight());
                for (int i = 0; i < grid.getChildCount(); i++) {
                    Button button = (Button) grid.getChildAt(i);
                    android.text.Layout layout = button.getLayout();
                    assertTrue("Caption needs more than two lines at fontScale=" + scale, layout.getLineCount() <= 2);
                    assertEquals(button.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
                    assertTrue(layout.getHeight() <= button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
                    assertTrue(button.getLeft() >= 0 && button.getRight() <= width);
                }
                if (scale == 2f) assertTrue(grid.getChildAt(2).getTop() > grid.getChildAt(0).getTop());
                else if (scale == 1f) assertEquals(grid.getChildAt(0).getTop(), grid.getChildAt(2).getTop());
            }
        });
    }
}
