package com.bitpoint.homeservercontrol;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** User actions with real profile/history storage and controlled Android document/system handlers. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class AppFunctionsTest {
    private android.app.Application app;private SharedPreferences prefs;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences(ServerConfig.PREFS,0);prefs.edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,8022).putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL).commit();
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();MetricHistoryStore.clear(app);TransferLogStore.clear(app);MonitoringScheduler.setEnabled(app,false);
    }
    private Button button(View v,String text){if(v instanceof Button && ((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){Button b=button(((ViewGroup)v).getChildAt(n),text);if(b!=null)return b;}return null;}
    private void await(java.util.function.BooleanSupplier ready)throws Exception{Shadows.shadowOf(Looper.getMainLooper()).idle();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!ready.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(ready.getAsBoolean());}
    private KeyScreenFlowTest.KeyFile provider()throws Exception{KeyScreenFlowTest.KeyFile p=new KeyScreenFlowTest.KeyFile();p.file=java.io.File.createTempFile("function-",".json",app.getCacheDir());ProviderInfo info=new ProviderInfo();info.authority="function.files";p.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,p);return p;}
    @Test public void clearingChartsRequiresConfirmationAndPreservesTransfersAndConnection(){
        HostTrustStore.trust(app,"192.0.2.44",8022,"SHA256:keep");TransferLogStore.add(app,"copy","done");MetricHistoryStore.addProbe(app,true,35,"50%");MetricHistoryStore.addSpeed(app,2);
        try(var c=Robolectric.buildActivity(HistoryActivity.class).create()){
            c.get().findViewById(R.id.historyClearButton).performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(2,MetricHistoryStore.read(app).size());
            c.get().findViewById(R.id.historyClearButton).performClick();dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(MetricHistoryStore.read(app).isEmpty());
            assertTrue(TransferLogStore.render(app).contains("copy"));assertEquals("SHA256:keep",HostTrustStore.get(app,"192.0.2.44",8022));assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));
        }
    }
    @Test public void diagnosticCopyAndTransferClearDoNotExposeSecretsOrClearCharts(){
        prefs.edit().putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER,"fixture-cipher-must-not-export").putString("ssh_identity_fixture","fixture-private-must-not-export").commit();MetricHistoryStore.addAvailability(app,true);TransferLogStore.add(app,"copy","done");
        try(var c=Robolectric.buildActivity(DiagnosticsActivity.class).create()){
            c.get().findViewById(R.id.diagnosticsCopyButton).performClick();String text=((ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString();
            assertTrue(text.contains("192.0.2.44"));assertFalse(text.contains("fixture-cipher"));assertFalse(text.contains("fixture-private"));assertTrue(text.contains("copy"));
            c.get().findViewById(R.id.diagnosticsClearTransfersButton).performClick();assertFalse(TransferLogStore.render(app).contains("copy"));assertEquals(1,MetricHistoryStore.read(app).size());assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));
        }
    }
    @Test public void profileExportSurvivesPickerRecreationAndContainsOnlyOriginalPublicParameters()throws Exception{
        KeyScreenFlowTest.KeyFile target=provider();Bundle saved=new Bundle();prefs.edit().putString("ssh_identity_fixture","fixture-private").putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER,"fixture-token").commit();
        try(var c=Robolectric.buildActivity(ProfileTransferActivity.class).create()){button(c.get().findViewById(android.R.id.content),"Сохранить параметры в файл").performClick();c.saveInstanceState(saved);}
        prefs.edit().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.45").commit();
        try(var c=Robolectric.buildActivity(ProfileTransferActivity.class).create(saved)){
            c.get().onActivityResult(901,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://function.files/export")));await(()->target.file.length()>0);String json=Files.readString(target.file.toPath());assertEquals("192.0.2.44",ConnectionProfile.decode(json).local);assertFalse(json.contains("fixture-private"));assertFalse(json.contains("fixture-token"));
        }
    }
    @Test public void profileImportNeedsConfirmationPreservesKeysAndClearsOldAgentSession()throws Exception{
        KeyScreenFlowTest.KeyFile source=provider();Files.writeString(source.file.toPath(),new ConnectionProfile("192.0.2.88","","other",ServerConfig.MODE_LOCAL,9000,8787).encode());
        prefs.edit().putString("ssh_identity_fixture","keep-key").putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER,"old-agent").putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).commit();HostTrustStore.trust(app,"192.0.2.44",8022,"SHA256:keep");Intent data=new Intent().setData(Uri.parse("content://function.files/import"));
        try(var c=Robolectric.buildActivity(ProfileTransferActivity.class).create()){
            c.get().onActivityResult(902,Activity.RESULT_OK,data);await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=null);AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));
            c.get().onActivityResult(902,Activity.RESULT_OK,data);AlertDialog previous=dialog;await(()->org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()!=previous);dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("192.0.2.88",ConnectionSelector.selectedHost(app));assertEquals("keep-key",prefs.getString("ssh_identity_fixture",""));assertEquals("SHA256:keep",HostTrustStore.get(app,"192.0.2.44",8022));assertFalse(prefs.contains(ServerConfig.KEY_AGENT_TOKEN_CIPHER));assertFalse(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true));
        }
    }
    @Test public void malformedProfileNeverChangesSavedConnection()throws Exception{
        KeyScreenFlowTest.KeyFile source=provider();Files.writeString(source.file.toPath()," ".repeat(8193));
        try(var c=Robolectric.buildActivity(ProfileTransferActivity.class).create()){
            c.get().onActivityResult(902,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://function.files/import")));await(()->org.robolectric.shadows.ShadowToast.getTextOfLatestToast()!=null);assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().startsWith("Файл не подходит"));assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
        }
    }
    @Test public void clientMonitoringCanBeEnabledAndDisabledWithoutChangingConnection(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).create()){
            CheckBox check=c.get().findViewById(R.id.settingsBackgroundMonitorCheck);check.setChecked(true);c.get().findViewById(R.id.settingsSaveButton).performClick();JobScheduler scheduler=app.getSystemService(JobScheduler.class);assertNotNull(scheduler.getPendingJob(8032));assertTrue(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));
            check.setChecked(false);c.get().findViewById(R.id.settingsSaveButton).performClick();assertNull(scheduler.getPendingJob(8032));assertFalse(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true));assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));
        }
    }
    @Test public void appearanceChangesPersistWithoutChangingConnectionOrTrust(){
        HostTrustStore.trust(app,"192.0.2.44",8022,"SHA256:keep");
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){
            IconShapeCatalog.select(app,1);assertEquals("Removed shape control",0,c.get().getResources().getIdentifier("iconShapeButton","id",app.getPackageName()));
            c.get().findViewById(R.id.themeChooserButton).performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,1,1);
        }
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){assertEquals(1,ThemeCatalog.index(app));assertEquals(1,IconShapeCatalog.index(app));assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));assertEquals("SHA256:keep",HostTrustStore.get(app,"192.0.2.44",8022));}
    }
    @Test public void wifiCredentialsGoOnlyToExplicitSystemHandlerAndAreCleared(){
        Intent intent=new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);ResolveInfo resolve=new ResolveInfo();resolve.activityInfo=new ActivityInfo();resolve.activityInfo.packageName="android.settings";resolve.activityInfo.name="android.settings.AddNetwork";resolve.activityInfo.exported=true;resolve.activityInfo.enabled=true;resolve.activityInfo.applicationInfo=new ApplicationInfo();resolve.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;resolve.activityInfo.applicationInfo.enabled=true;
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(intent,resolve);
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){
            EditText ssid=c.get().findViewById(R.id.wifiSsidInput),secret=c.get().findViewById(R.id.wifiPasswordInput);ssid.setText("fixture-net");secret.setText("fixture-passphrase");c.get().findViewById(R.id.wifiConnectButton).performClick();
            Intent dispatched=Shadows.shadowOf(c.get()).getNextStartedActivityForResult().intent;assertEquals(new ComponentName("android.settings","android.settings.AddNetwork"),dispatched.getComponent());assertEquals(1,dispatched.getParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST).size());assertEquals("",secret.getText().toString());assertFalse(secret.isSaveEnabled());assertEquals("192.0.2.44",ConnectionSelector.selectedHost(app));
        }
    }
    @Test public void exportedDiagnosticReportSurvivesPickerRecreation()throws Exception{
        KeyScreenFlowTest.KeyFile target=provider();Bundle state=new Bundle();
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){c.get().findViewById(R.id.exportButton).performClick();c.saveInstanceState(state);}
        try(var c=Robolectric.buildActivity(MainActivity.class).create(state)){
            String pending=org.robolectric.util.ReflectionHelpers.getField(c.get(),"pendingReport");assertTrue("Pending diagnostic report lost while picker was open",pending.startsWith("LocalLS "));
            c.get().onActivityResult(601,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://function.files/report")));await(()->target.file.length()>0);String text=Files.readString(target.file.toPath());assertTrue(text.contains("192.0.2.44"));assertFalse(text.contains("LocalLS v0.9"));
        }
    }
    @Test public void networkPortCheckDistinguishesOpenPortFromAuthenticatedSsh()throws Exception{
        // The duplicate TCP probe has been removed. Discovery must not claim SSH authentication.
        prefs.edit().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").commit();
        try(var c=Robolectric.buildActivity(NetworkActivity.class).create().start().resume()){
            assertEquals(0,c.get().getResources().getIdentifier("networkTestButton","id",c.get().getPackageName()));
            assertEquals("192.0.2.44",((EditText)c.get().findViewById(R.id.networkLocalHostInput)).getText().toString());
            assertFalse(((TextView)c.get().findViewById(R.id.networkResultText)).getText().toString().contains("SSH подтверждён"));
        }
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){assertNotNull(c.get().findViewById(R.id.connectionCheckButton));}
    }

}
