package com.bitpoint.homeservercontrol;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FileDeviceLabelsTest {
    @Before public void setup(){
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();
        c.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putBoolean("server_role",true).commit();
    }
    @Test @Config(qualifiers="sw360dp-w800dp-h360dp-land") public void landscapePhoneIsNotMisclassifiedByWindowWidth(){
        assertEquals(DeviceKind.PHONE,DeviceLabels.local(RuntimeEnvironment.getApplication()));
        checkScreen(DeviceKind.PHONE);
    }
    @Test @Config(qualifiers="sw600dp-w800dp-h1280dp") public void tabletUsesTabletInTabsActionsAndFolderMenu(){
        assertEquals(DeviceKind.TABLET,DeviceLabels.local(RuntimeEnvironment.getApplication()));checkScreen(DeviceKind.TABLET);
    }
    private void checkScreen(DeviceKind local){
        try(var owner=Robolectric.buildActivity(FilesActivity.class).setup()){
            FilesActivity a=owner.get();assertEquals(local.label,ReflectionHelpers.<Button>getField(a,"phoneTab").getText());
            ReflectionHelpers.setField(a,"own",false);
            for(DeviceKind peer:DeviceKind.values()){
                ReflectionHelpers.setField(a,"remoteKind",peer);ReflectionHelpers.callInstanceMethod(a,"buttons");
                assertEquals(peer.label,ReflectionHelpers.<Button>getField(a,"serverTab").getText());
                assertEquals("На "+peer.target+" (0)",ReflectionHelpers.<Button>getField(a,"send").getText());
                assertEquals("На "+local.target+" (0)",ReflectionHelpers.<Button>getField(a,"receive").getText());
                assertEquals(Boolean.TRUE,ReflectionHelpers.<Button>getField(a,"send").getTag(com.bitpoint.homeservercontrol.ui.R.id.fileTransferUpload));
            }
            ReflectionHelpers.callInstanceMethod(a,"phoneMenu");AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            boolean found=false;for(int i=0;i<menu.getListView().getCount();i++)if(("Папка "+local.folder).equals(menu.getListView().getItemAtPosition(i)))found=true;
            assertTrue(found);menu.dismiss();
        }
    }
    @Test public void deviceTelemetryAdvertisesLocalKindAndGuideIsShortAndOnlineLinkExists()throws Exception{
        Context c=RuntimeEnvironment.getApplication();assertEquals(DeviceLabels.local(c),DeviceTelemetry.read(c).deviceKind);
        String guide;try(var in=c.getAssets().open("phone-setup.txt")){guide=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        assertTrue(guide.length()<1600);assertTrue(guide.contains("Сканировать QR"));assertTrue(guide.contains("Не очищай данные"));
        try(var owner=Robolectric.buildActivity(PhoneSetupGuideActivity.class).setup()){
            Button button=find(owner.get().findViewById(android.R.id.content));assertNotNull(button);button.performClick();
            Intent link=Shadows.shadowOf(owner.get()).getNextStartedActivity();assertEquals("https://github.com/grigorywork/LocalLS/blob/main/android/app/src/main/assets/phone-setup.txt",link.getDataString());
        }
    }
    private Button find(View v){if(v instanceof Button&&((Button)v).getText().toString().equals("Инструкция на GitHub"))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i));if(b!=null)return b;}return null;}
}
