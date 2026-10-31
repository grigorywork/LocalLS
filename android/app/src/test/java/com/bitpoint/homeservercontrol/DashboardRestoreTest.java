package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30, qualifiers="ru-rRU-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardRestoreTest {
    @Test public void graphsAndEveryHomeActionFitSmallWindowsWithoutScrollingOrTabs() {
        Context app=RuntimeEnvironment.getApplication();
        for(int[] size:new int[][]{{360,560},{320,568},{640,300},{411,800}}) {
            for(float scale:new float[]{1f,1.5f,2f}) {
                Configuration config=new Configuration(app.getResources().getConfiguration());
                config.fontScale=scale;
                Context context=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));
                View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
                IconButtons.decorate(root);
                ((TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер работает · доступ разрешён");
                StatusBadge.apply(root.findViewById(R.id.localServerSummary),StatusBadge.Tone.SUCCESS);
                StatusBadge.apply(root.findViewById(R.id.statusText),StatusBadge.Tone.WARNING);
                for(boolean active:new boolean[]{false,true}) {
                    ((TextView)root.findViewById(R.id.dashboardSpeedSummary)).setText(active
                            ? "Сейчас 123.45 МБ/с • пик 678.90" : "Передач пока нет • МБ/с");
                    ((TextView)root.findViewById(R.id.dashboardLatencySummary)).setText(active
                            ? "Последний 125 мс • средний 130 мс • 20 измерений" : "Нет замеров");
                    measure(root,size[0],size[1]);
                    ViewGroup home=root.findViewById(R.id.dashboardScroll);
                    check(home,home);
                    for(int id:new int[]{R.id.dashboardSpeedChart,R.id.dashboardLatencyChart}) {
                        View chart=home.findViewById(id);
                        assertNotNull("Both graphs stay on home",chart);
                        assertTrue(size[0]+"x"+size[1]+" font "+scale+" plot height "+chart.getHeight()+" overview "+root.findViewById(R.id.dashboardOverview).getHeight()+" actions "+root.findViewById(R.id.applicationToolsCard).getHeight(),chart.getHeight()>=48);
                        assertOnScreen(chart,home);
                    }
                }
                assertEquals("No page switching to see graphs",0,root.getResources().getIdentifier("dashboardPages","id",context.getPackageName()));
            }
        }
    }

    @Test public void serverOnlyScreenHidesClientGraphsAndPreservesKeysAndSettings() {
        Context app=RuntimeEnvironment.getApplication();
        var server=app.getSharedPreferences(EmbeddedServerService.PREFS,0);
        server.edit().clear().putBoolean("server_role",true).putBoolean("show_client",false)
                .putString("allowed_key","existing-public-key").putInt("port",9022).commit();
        var client=app.getSharedPreferences(ServerConfig.PREFS,0);
        client.edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(SshKeys.MODE,"key")
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD,false)
                .putString("ssh_identity_fixture","existing-encrypted-identity")
                .putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.9").putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).commit();
        var beforeServer=server.getAll();var beforeClient=client.getAll();
        try(var screen=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=screen.get();
            View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            measure(root,360,560);
            ViewGroup home=activity.findViewById(R.id.dashboardScroll);
            for(int id:new int[]{R.id.dashboardSpeedChart,R.id.dashboardLatencyChart}) {
                View graph=home.findViewById(id);
                assertFalse("Server role must hide client graphs",graph.isShown());
            }
            assertEquals(View.VISIBLE,activity.findViewById(R.id.serverHome).getVisibility());
            assertEquals(View.GONE,activity.findViewById(R.id.dashboardScroll).getVisibility());
            assertEquals(View.GONE,activity.findViewById(R.id.homeClientTab).getVisibility());
            assertEquals(View.GONE,activity.findViewById(R.id.remoteConnectionCard).getVisibility());
            for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.attachFilesButton})
                assertEquals(View.GONE,activity.findViewById(id).getVisibility());
            assertEquals(beforeServer,server.getAll());assertEquals(beforeClient,client.getAll());
            activity.findViewById(R.id.toolsMenuButton).performClick();
            assertEquals("Client menu is blocked in server role",View.GONE,activity.findViewById(R.id.advancedPanel).getVisibility());
            assertEquals(View.GONE,activity.findViewById(R.id.advancedPanel).getVisibility());
            assertEquals(beforeServer,server.getAll());assertEquals(beforeClient,client.getAll());
        }
    }

    @Test public void restoredHomeAndConnectionFormContainNoSshPasswordFieldOrLabel() {
        Context app=RuntimeEnvironment.getApplication();
        Context context=new ContextThemeWrapper(app,ThemeCatalog.style(app));
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
        checkNoSshPassword(root);
        for(String name:new String[]{"passwordInput","sshPasswordInput","savePasswordCheck"})
            assertEquals(name,0,app.getResources().getIdentifier(name,"id",app.getPackageName()));
        assertNotNull(root.findViewById(R.id.sshKeySetupButton));
        assertNotNull(root.findViewById(R.id.scanQrButton));
        assertNotNull(root.findViewById(R.id.serverQrButton));
    }

    private void checkNoSshPassword(View view) {
        if(view instanceof TextView) {
            TextView text=(TextView)view;
            String caption=(text.getText()+" "+text.getHint()).toLowerCase(java.util.Locale.ROOT);
            assertFalse(caption,caption.contains("пароль ssh")||caption.contains("ssh password"));
            if(view instanceof EditText && view.getId()!=R.id.wifiPasswordInput)
                assertFalse("Connection must not request a password",((EditText)view).getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checkNoSshPassword(((ViewGroup)view).getChildAt(i));
    }
    private void check(View view,ViewGroup home) {
        if(view.getVisibility()==View.GONE)return;
        assertFalse("Home must not scroll",view instanceof ScrollView);
        if(view instanceof Button) {
            Button button=(Button)view;assertOnScreen(view,home);
            assertTrue(button.getText()+" touch height="+view.getHeight()+" home="+home.getWidth()+"x"+home.getHeight()+" font="+home.getResources().getConfiguration().fontScale+" connection="+home.findViewById(R.id.remoteConnectionCard).getHeight(),view.getHeight()>=48);
            assertTrue(button.getText()+" touch width",view.getWidth()>=48);
            assertNotNull(button.getLayout());
            assertEquals(button.getText().toString(),button.getText().length(),button.getLayout().getLineEnd(button.getLayout().getLineCount()-1));
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)check(((ViewGroup)view).getChildAt(i),home);
    }
    private void assertOnScreen(View view,ViewGroup home) {
        Rect bounds=new Rect(0,0,view.getWidth(),view.getHeight());home.offsetDescendantRectToMyCoords(view,bounds);
        assertTrue("Outside home: "+bounds+" / "+home.getWidth()+"x"+home.getHeight(),bounds.top>=0&&bounds.left>=0&&bounds.right<=home.getWidth()&&bounds.bottom<=home.getHeight());
    }
    private void measure(View root,int width,int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
    }
}
