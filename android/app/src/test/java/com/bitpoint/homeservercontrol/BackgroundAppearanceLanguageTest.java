package com.bitpoint.homeservercontrol;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.content.*;
import android.app.Service;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class BackgroundAppearanceLanguageTest {
 @Test public void lightBackgroundSurvivesColorChanges(){var c=RuntimeEnvironment.getApplication();ThemeCatalog.light(c,true);ThemeCatalog.select(c,1);assertTrue(ThemeCatalog.light(c));try(var a=Robolectric.buildActivity(AppearanceActivity.class).setup()){assertEquals(0xFFF3F4F6,ThemeCatalog.color(a.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));assertEquals(0xFF166AAB,ThemeCatalog.color(a.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscAccent));}ThemeCatalog.light(c,false);assertFalse(ThemeCatalog.light(c));}
 @Test public void explicitStopAndUnrequestedStickyRestartNeverStartAListener(){var c=RuntimeEnvironment.getApplication();c.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_enabled",false).commit();var service=Robolectric.buildService(EmbeddedServerService.class).create();try{assertEquals(Service.START_NOT_STICKY,service.get().onStartCommand(null,0,1));assertFalse(EmbeddedServerService.running);assertFalse(EmbeddedServerService.starting);}finally{service.destroy();} }
 @Test public void permissionApprovalDoesNotDisableStickyServerLifetime(){EmbeddedServerService.running=true;var s=Robolectric.buildService(EmbeddedServerService.class).create();try{assertEquals(Service.START_STICKY,s.get().onStartCommand(new Intent(EmbeddedServerService.AUTHORIZE_QR).putExtra("nonce","expired"),0,1));}finally{s.destroy();EmbeddedServerService.running=false;}}
 @Test public void englishUiAndSystemResourcesUseTheSelectedLanguage(){var c=RuntimeEnvironment.getApplication();AppLanguage.select(c,"en");try(var a=Robolectric.buildActivity(AppearanceActivity.class).setup()){assertEquals("en",a.get().getResources().getConfiguration().getLocales().get(0).getLanguage());assertEquals("Appearance",((android.widget.Button)a.get().findViewById(R.id.settingsBackButton)).getText().toString());assertEquals("Server",AppText.tr("Сервер"));}finally{AppLanguage.select(c,"ru");}}
}
