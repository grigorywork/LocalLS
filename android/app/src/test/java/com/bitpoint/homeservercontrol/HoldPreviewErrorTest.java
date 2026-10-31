package com.bitpoint.homeservercontrol;
import android.app.Activity;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class HoldPreviewErrorTest {
    private ActivityController<Activity> activity;private HoldPreview preview;private File source;private FileNodes.Node node;
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup();activity.get().setContentView(new FrameLayout(activity.get()));preview=new HoldPreview(activity.get());File root=EmbeddedServerService.share(activity.get());root.mkdirs();source=new File(root,"preview-error-"+UUID.randomUUID()+".mp4");Files.write(source.toPath(),new byte[]{2,5,9});node=new FileNodes.Node("share",source.getName(),source.getName(),false,source.length());
    }
    @After public void close(){preview.destroy();source.delete();activity.pause().stop().destroy();}
    private VideoView open(){preview.open(node,FileConnection.current(activity.get()));VideoView player=ReflectionHelpers.getField(preview,"video");assertNotNull(player);assertNotNull(Shadows.shadowOf(player).getOnErrorListener());return player;}
    private boolean visibleError(View view){if(view.getVisibility()!=View.VISIBLE)return false;if(view instanceof TextView&&"Не удалось воспроизвести файл".contentEquals(((TextView)view).getText()))return true;if(view instanceof android.view.ViewGroup){var group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(visibleError(group.getChildAt(i)))return true;}return false;}
    @Test public void playbackErrorIsVisibleAndReleaseStillStopsPlaybackAndPreservesSource()throws Exception{
        VideoView player=open();MediaPlayer media=new MediaPlayer();try{Shadows.shadowOf(player).getOnPreparedListener().onPrepared(media);assertTrue(player.isPlaying());assertTrue(Shadows.shadowOf(player).getOnErrorListener().onError(media,MediaPlayer.MEDIA_ERROR_UNKNOWN,0));LinearLayout content=ReflectionHelpers.getField(preview,"content");assertTrue("Playback error was written into a detached label",visibleError(content));preview.close();assertFalse(preview.showing());assertEquals(org.robolectric.shadows.ShadowVideoView.STOP,Shadows.shadowOf(player).getCurrentVideoState());assertArrayEquals(new byte[]{2,5,9},Files.readAllBytes(source.toPath()));assertEquals(0,activity.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE);}finally{media.release();}
    }
    @Test public void stalePlaybackFailureCannotChangeNewPreviewOrClearPreexistingSecureFlag(){
        activity.get().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);VideoView first=open();var oldError=Shadows.shadowOf(first).getOnErrorListener();VideoView second=open();LinearLayout content=ReflectionHelpers.getField(preview,"content");assertTrue(oldError.onError(null,MediaPlayer.MEDIA_ERROR_UNKNOWN,0));assertFalse(visibleError(content));assertSame(second,ReflectionHelpers.getField(preview,"video"));assertTrue(preview.showing());preview.close();assertTrue(oldError.onError(null,MediaPlayer.MEDIA_ERROR_UNKNOWN,0));assertFalse(preview.showing());assertTrue((activity.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
    }
}
