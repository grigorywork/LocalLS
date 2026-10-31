package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Deterministic discovery results; the Activity, native dialog and selection are real. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h640dp-mdpi",shadows={DiscoverySelectionTest.Discovery.class,RouteIdentityTest.Secrets.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DiscoverySelectionTest {
    @Implements(value=LocalServerDiscovery.class,isInAndroidSdk=false)
    public static class Discovery {
        static volatile List<String> hosts=List.of();
        static final AtomicInteger calls=new AtomicInteger();
        @Implementation protected static LocalServerDiscovery.Result discover(int port){
            calls.incrementAndGet();
            return new LocalServerDiscovery.Result("192.0.2.1",hosts,"Найдено устройств: "+hosts.size());
        }
    }
    private Context app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();Discovery.calls.set(0);
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3)
            .putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,8022).commit();
    }
    @Test public void multipleServersAreVisibleAndChoosingOnlyEditsTheDraft()throws Exception{
        Discovery.hosts=List.of("192.0.2.45","192.0.2.46");
        for(int theme:new int[]{0,4})for(boolean glass:new boolean[]{false,true})for(int[] size:new int[][]{{360,640},{1280,800}}){
            ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);
            try(var screen=Robolectric.buildActivity(NetworkActivity.class).setup()){
                var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=new HashMap<>(prefs.getAll());
                NetworkActivity a=screen.get();AlertDialog old=ShadowAlertDialog.getLatestAlertDialog();
                a.findViewById(R.id.networkDiscoverButton).performClick();await(()->ShadowAlertDialog.getLatestAlertDialog()!=old);
                AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();ListView list=dialog.getListView();
                assertNotNull("Discovery list exists",list);
                assertNotNull("Multiple discovered servers must be attached to the visible dialog",list.getParent());
                layout(dialog.getWindow().getDecorView(),size[0],size[1]);
                assertTrue("Server choices must be shown",list.isShown());assertEquals(2,list.getCount());
                assertNotNull("Second server must have a rendered row",list.getChildAt(1));
                assertTrue("Server selection retains a 48dp touch row",list.getChildAt(1).getHeight()>=48);
                assertEquals("192.0.2.46",list.getAdapter().getItem(1));
                list.performItemClick(list.getChildAt(1),1,list.getAdapter().getItemId(1));Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals("192.0.2.46",((EditText)a.findViewById(R.id.networkLocalHostInput)).getText().toString());
                assertFalse(dialog.isShowing());assertEquals("A discovered address must not save or grant trust automatically",before,prefs.getAll());
                assertEquals("",HostTrustStore.get(app,"192.0.2.46",8022));
            }
        }
        assertEquals(8,Discovery.calls.get());
    }
    @Test public void cancellingMultipleServersKeepsTheDraftAndSavedProfile()throws Exception{
        Discovery.hosts=List.of("192.0.2.45","192.0.2.46");
        try(var screen=Robolectric.buildActivity(NetworkActivity.class).setup()){
            NetworkActivity a=screen.get();EditText draft=a.findViewById(R.id.networkLocalHostInput);draft.setText("192.0.2.99");
            var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=new HashMap<>(prefs.getAll());
            AlertDialog old=ShadowAlertDialog.getLatestAlertDialog();a.findViewById(R.id.networkDiscoverButton).performClick();await(()->ShadowAlertDialog.getLatestAlertDialog()!=old);
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("192.0.2.99",draft.getText().toString());assertEquals(before,prefs.getAll());assertFalse(dialog.isShowing());
        }
    }
    @Test public void singleServerStillRequiresExplicitConfirmationAndSave()throws Exception{
        Discovery.hosts=List.of("192.0.2.45");
        try(var screen=Robolectric.buildActivity(NetworkActivity.class).setup()){
            NetworkActivity a=screen.get();var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=new HashMap<>(prefs.getAll());
            AlertDialog old=ShadowAlertDialog.getLatestAlertDialog();a.findViewById(R.id.networkDiscoverButton).performClick();await(()->ShadowAlertDialog.getLatestAlertDialog()!=old);
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains("192.0.2.45"));assertEquals(before,prefs.getAll());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("192.0.2.45",((EditText)a.findViewById(R.id.networkLocalHostInput)).getText().toString());assertEquals(before,prefs.getAll());
            a.findViewById(R.id.networkSaveButton).performClick();assertEquals("192.0.2.45",ConnectionSelector.selectedHost(app));
            assertEquals("",HostTrustStore.get(app,"192.0.2.45",8022));
        }
    }
    private void await(BooleanSupplier done)throws Exception{
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(!done.getAsBoolean()&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue("Discovery result reached the screen",done.getAsBoolean());
    }
    private void layout(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
}
