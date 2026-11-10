package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
final class AppLanguage {
    static String get(Context c){return "en".equals(c.getSharedPreferences("localls_language",0).getString("language","ru"))?"en":"ru";}
    static Context apply(Context c){String language=get(c);AppText.language(language);Locale locale=Locale.forLanguageTag(language);Locale.setDefault(locale);Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(locale);return c.createConfigurationContext(config);}
    static void select(Context c,String language){c.getSharedPreferences("localls_language",0).edit().putString("language",language).commit();AppText.language(language);EmbeddedServerService.languageChanged();}
}
