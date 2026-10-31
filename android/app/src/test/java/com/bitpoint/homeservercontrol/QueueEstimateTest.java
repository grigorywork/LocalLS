package com.bitpoint.homeservercontrol;

import android.app.Application;
import android.content.Intent;
import android.os.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class QueueEstimateTest {
    Application app;TransferJournal journal;
    @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();journal=new TransferJournal(app);journal.clearAll();TransferForegroundService.clearing=false;}
    static Intent request(Application app,FileNodes.Node... nodes){Intent i=new Intent(app,TransferForegroundService.class).setAction("save").putExtra("request_id",UUID.randomUUID().toString());ArrayList<Bundle> raw=new ArrayList<>();for(FileNodes.Node n:nodes)raw.add(n.bundle());return i.putParcelableArrayListExtra("sources",raw);}
    TransferForegroundService.State state(String id,String key,long bytes,long size){return new TransferForegroundService.State(true,false,"","",bytes,size,2,id,key);}
    @Test public void wholeQueueIncludesEveryJobBeyondScreenAndSubtractsOnlyTheMatchingFile()throws Exception{
        FileNodes.Node node=new FileNodes.Node("share","a","a",false,10*1048576);Intent first=request(app,node);journal.ensure(first);
        for(int n=0;n<199;n++)journal.ensure(request(app,new FileNodes.Node("share","b"+n,"b"+n,false,1048576)));
        QueueEstimate result=QueueEstimate.read(app,journal.list(),state(first.getStringExtra("request_id"),TransferJournal.key(node),4*1048576,10*1048576),1);
        assertEquals(200,result.pending);assertEquals(205*1048576L,result.remaining);assertTrue(result.known);assertEquals("Вся очередь: ≈ 3 мин 25 с",result.timing);
    }
    @Test public void foldersUseSnapshotsAndExcludeAlreadyPublishedChildrenAndCompletedRequests()throws Exception{
        FileNodes.Node folder=new FileNodes.Node("share","folder","folder",true,0),a=new FileNodes.Node("share","folder/a","a",false,100),b=new FileNodes.Node("share","folder/b","b",false,200);
        Intent job=request(app,folder);journal.ensure(job);String id=job.getStringExtra("request_id");
        journal.item(id,TransferJournal.key(folder),new JSONObject().put("children",new JSONArray().put(TransferJournal.node(a.bundle())).put(TransferJournal.node(b.bundle()))));journal.item(id,TransferJournal.key(a),new JSONObject().put("done",true));
        Intent done=request(app,a);journal.ensure(done);journal.status(done.getStringExtra("request_id"),"done","");
        QueueEstimate result=QueueEstimate.read(app,journal.list(),state(id,TransferJournal.key(b),50,200),1);assertEquals(1,result.pending);assertEquals(150,result.remaining);assertTrue(result.known);
    }
    @Test public void unknownFolderOrOverflowNeverShowsAFabricatedTime()throws Exception{
        journal.ensure(request(app,new FileNodes.Node("share","folder","folder",true,0)));
        assertEquals("Вся очередь: Уточняем объём…",QueueEstimate.read(app,journal.list(),state("","",0,0),1).timing);
        journal.clearAll();journal.ensure(request(app,new FileNodes.Node("share","a","a",false,Long.MAX_VALUE),new FileNodes.Node("share","b","b",false,Long.MAX_VALUE)));
        assertFalse(QueueEstimate.read(app,journal.list(),state("","",0,0),1).known);
    }
    @Test public void pausedWaitingAndFinalizingNeverPromiseZeroSeconds()throws Exception{
        FileNodes.Node source=new FileNodes.Node("share","a","a",false,100);Intent job=request(app,source);journal.ensure(job);String id=job.getStringExtra("request_id");
        assertEquals("Вся очередь: Рассчитываем время…",QueueEstimate.read(app,journal.list(),state(id,TransferJournal.key(source),5,100),0).timing);
        assertEquals("Вся очередь: Завершаем передачу…",QueueEstimate.read(app,journal.list(),state(id,TransferJournal.key(source),100,100),1).timing);
        assertEquals("Вся очередь: Ожидает продолжения",QueueEstimate.read(app,journal.list(),new TransferForegroundService.State(false,true,"","",0,0,1),1).timing);
        journal.status(id,"failed","");assertEquals(0,QueueEstimate.read(app,journal.list(),state("","",0,0),1).pending);
    }
    @Test public void smoothedRateExpiresAndNewJobDoesNotInheritThePreviousSpeed(){
        Object owner=new Object();TransferSpeedStore.begin(owner);TransferSpeedStore.recordBytes(owner,1048576);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));assertTrue(TransferSpeedStore.queueSpeed()>0);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));assertEquals(0,TransferSpeedStore.queueSpeed(),0);TransferSpeedStore.end(owner,"done");TransferSpeedStore.begin(new Object());assertEquals(0,TransferSpeedStore.queueSpeed(),0);
    }
}
