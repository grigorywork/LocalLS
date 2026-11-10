package com.bitpoint.homeservercontrol;
import android.content.Context;import android.graphics.Color;import android.view.*;import android.widget.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LightAppearanceContrastTest {
 @Test public void appearanceSwitchLabelsRemainReadableInEveryPaletteAndLanguage(){Context c=RuntimeEnvironment.getApplication();try{for(String lang:new String[]{"ru","en"})for(boolean light:new boolean[]{false,true})for(boolean glass:new boolean[]{false,true})for(int theme:new int[]{0,1,2,3,5,6,7}){AppLanguage.select(c,lang);ThemeCatalog.select(c,theme);ThemeCatalog.light(c,light);ThemeCatalog.glass(c,glass);try(var screen=Robolectric.buildActivity(AppearanceActivity.class).setup()){var a=screen.get();int panel=ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel),background=ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground);check(a.findViewById(android.R.id.content),blend(panel,background));}}}finally{AppLanguage.select(c,"ru");ThemeCatalog.light(c,false);ThemeCatalog.glass(c,false);}}
 private static void check(View view,int background){if(view instanceof Switch){Switch label=(Switch)view;double a=Color.luminance(label.getCurrentTextColor()),b=Color.luminance(background);double contrast=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);assertTrue(label.getText()+" contrast="+contrast,contrast>=4.5);}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)check(g.getChildAt(i),background);}}
 private static int blend(int front,int back){float alpha=Color.alpha(front)/255f;return Color.rgb(Math.round(Color.red(front)*alpha+Color.red(back)*(1-alpha)),Math.round(Color.green(front)*alpha+Color.green(back)*(1-alpha)),Math.round(Color.blue(front)*alpha+Color.blue(back)*(1-alpha)));}
}
