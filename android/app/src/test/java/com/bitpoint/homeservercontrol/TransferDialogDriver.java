package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import org.robolectric.*;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Follow the real preflight confirmation, without bypassing it or using a worker-pool marker. */
final class TransferDialogDriver {
    static void confirm(Context context)throws Exception{
        assertNull("Transfer started before preflight confirmation",Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);AlertDialog dialog;
        do{
            Shadows.shadowOf(Looper.getMainLooper()).runOneTask();dialog=ShadowAlertDialog.getLatestAlertDialog();
            if(dialog!=null&&dialog.isShowing()&&dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null
                    &&"В очередь".contentEquals(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText()))break;
            Thread.sleep(10);
        }while(System.nanoTime()<end);
        assertNotNull("Preflight dialog did not appear",dialog);assertTrue(dialog.isShowing());
        assertEquals("В очередь",dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertNull("Preflight copied without confirmation",Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while((Shadows.shadowOf(RuntimeEnvironment.getApplication()).peekNextStartedService()==null||dialog.isShowing())&&System.nanoTime()<end){
            Shadows.shadowOf(Looper.getMainLooper()).runOneTask();Thread.sleep(10);
        }
        assertNotNull("Confirmed transfer was not queued",Shadows.shadowOf(RuntimeEnvironment.getApplication()).peekNextStartedService());
        assertFalse("Preflight dialog was not dismissed",dialog.isShowing());
    }
}
