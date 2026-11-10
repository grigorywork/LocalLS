package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class EnglishStatusRibbonTest {
 @Test public void compactRibbonRemovesLocalizedPrefixesAndKeepsCompleteValues(){Context app=RuntimeEnvironment.getApplication();try{for(String language:new String[]{"ru","en"}){AppLanguage.select(app,language);Context c=new android.view.ContextThemeWrapper(AppLanguage.apply(app),com.bitpoint.homeservercontrol.ui.R.style.Theme_HomeServerControl);StatusRibbon ribbon=new StatusRibbon(c,new TextView(c),new TextView(c),true);ribbon.first.setText(AppText.tr("Работает ")+"3"+AppText.tr(" ч"));ribbon.second.setText(AppText.tr("Обновлено ")+"17"+AppText.tr(" с назад"));assertEquals("3"+AppText.tr(" ч"),ribbon.first.getTransformationMethod().getTransformation(ribbon.first.getText(),ribbon.first).toString());assertEquals("17"+AppText.tr(" с назад"),ribbon.second.getTransformationMethod().getTransformation(ribbon.second.getText(),ribbon.second).toString());}}finally{AppLanguage.select(app,"ru");}}
}
