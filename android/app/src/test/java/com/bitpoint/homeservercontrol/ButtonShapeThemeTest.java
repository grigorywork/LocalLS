package com.bitpoint.homeservercontrol;

import android.graphics.*;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual button pixels and Android shadow outlines must agree with the selected shape and theme. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ButtonShapeThemeTest {
    private Bitmap draw(Button button){
        button.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(64,View.MeasureSpec.EXACTLY));button.layout(0,0,200,64);
        Bitmap bitmap=Bitmap.createBitmap(200,64,Bitmap.Config.ARGB_8888);button.draw(new Canvas(bitmap));return bitmap;
    }
    @Test public void shapeSwitchesKeepPrimaryAndSecondaryColoursIndependentOfTheGlyphInAllThemes(){
        var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();
        for(int theme=0;theme<ThemeCatalog.IDS.length;theme++){
            ThemeCatalog.select(app,theme);Context context=new ContextThemeWrapper(app,ThemeCatalog.style(app));
            for(boolean secondary:new boolean[]{false,true}){
                Button button=new Button(context,null,0,secondary?com.bitpoint.homeservercontrol.ui.R.style.Widget_HSC_SecondaryButton:com.bitpoint.homeservercontrol.ui.R.style.Widget_HSC_CompactButton);
                button.setText("QR");button.setTag("compact_action");button.setLayoutParams(new LinearLayout.LayoutParams(200,64));
                int expected=ThemeCatalog.color(context,secondary?com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton:com.bitpoint.homeservercontrol.ui.R.attr.hscButton);
                for(int shape:new int[]{1,3,2,0,1,3}){
                    IconShapeCatalog.select(app,shape);IconButtons.decorate(button);Drawable glyph=button.getCompoundDrawablesRelative()[0];assertNotNull(glyph);glyph.setTint(Color.MAGENTA);
                    Bitmap normal=draw(button);assertEquals("Semantic fill survives shape changes",expected,normal.getPixel(180,32));
                    assertNotEquals("Border is not the glyph tint",Color.MAGENTA,normal.getPixel(1,32));assertNotEquals("Border is not white",Color.WHITE,normal.getPixel(1,32));
                    assertEquals("Fixed rounded outside corner",0,Color.alpha(normal.getPixel(0,0)));normal.recycle();
                    Outline outline=new Outline();button.getBackground().getOutline(outline);assertFalse(outline.isEmpty());Rect bounds=new Rect();
                    assertTrue(outline.getRect(bounds));assertEquals("Retired shape is ignored",12,outline.getRadius(),0);assertEquals(new Rect(1,1,199,63),bounds);
                    button.setPressed(true);Bitmap pressed=draw(button);int pressedColour=pressed.getPixel(180,32);assertNotEquals(expected,pressedColour);assertNotEquals("No hardcoded purple pressed state",0xff8044cc,pressedColour);pressed.recycle();button.setPressed(false);
                    button.setEnabled(false);Bitmap disabled=draw(button);assertNotEquals(expected,disabled.getPixel(180,32));assertNotEquals(0xff292433,disabled.getPixel(180,32));disabled.recycle();button.setEnabled(true);
                }
            }
        }
        IconShapeCatalog.select(app,0);
    }
}
