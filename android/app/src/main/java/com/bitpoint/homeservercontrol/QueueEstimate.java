package com.bitpoint.homeservercontrol;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Read-only estimate from durable source snapshots; unknown folders never invent a size. */
final class QueueEstimate {
    final int pending;
    final long remaining;
    final boolean known,active;
    final String timing;
    private QueueEstimate(int count,long bytes,boolean known,boolean active,String timing){
        pending=count;remaining=bytes;this.known=known;this.active=active;this.timing=timing;
    }
    static QueueEstimate read(Context c,List<JSONObject> entries,TransferForegroundService.State state,float speed)throws Exception{
        TransferJournal journal=new TransferJournal(c);Totals sum=new Totals();int pending=0;
        for(JSONObject job:entries){
            if(Arrays.asList("done","cancelled","failed").contains(job.optString("status")))continue;
            pending++;Set<String> visited=new HashSet<>();JSONArray sources=job.getJSONArray("sources");
            for(int n=0;n<sources.length();n++)count(journal,job.getString("id"),sources.getJSONObject(n),state,sum,visited,0);
        }
        String time;
        if(TransferForegroundService.clearing)time=AppText.tr("Очищаем список…");
        else if(pending==0)time=AppText.tr("Очередь пуста");
        else if(!state.active)time=AppText.tr("Ожидает продолжения");
        else if(!sum.known)time=AppText.tr("Уточняем объём…");
        else if(sum.bytes==0)time=AppText.tr("Завершаем передачу…");
        else {long seconds=DashboardLayout.estimateSeconds(0,sum.bytes,speed);time=seconds<0?AppText.tr("Рассчитываем время…"):DashboardLayout.formatEta(seconds).replace(AppText.tr("Осталось "),"");}
        return new QueueEstimate(pending,sum.bytes,sum.known,state.active,AppText.tr("Вся очередь: ")+time);
    }
    private static final class Totals {long bytes;boolean known=true;}
    private static void count(TransferJournal journal,String id,JSONObject node,TransferForegroundService.State state,Totals sum,Set<String> visited,int depth)throws Exception{
        if(depth>48||visited.size()>=10000){sum.known=false;return;}
        FileNodes.Node source=FileNodes.Node.from(TransferJournal.bundle(node));String key=TransferJournal.key(source);
        if(!visited.add(key))return;
        JSONObject item=journal.item(id,key);if(item.optBoolean("done"))return;
        if(source.directory){JSONArray children=item.optJSONArray("children");if(children==null){sum.known=false;return;}for(int n=0;n<children.length();n++)count(journal,id,children.getJSONObject(n),state,sum,visited,depth+1);return;}
        long size=item.has("size")?item.optLong("size",-1):source.size;if(size<0){sum.known=false;return;}
        long sent=id.equals(state.requestId)&&key.equals(state.sourceKey)?Math.max(0,Math.min(size,state.bytes)):0;
        try{sum.bytes=Math.addExact(sum.bytes,size-sent);}catch(ArithmeticException e){sum.known=false;}
    }
    static String countLabel(int count){int last=count%10,hundred=count%100;return count+" "+(hundred>=11&&hundred<=14?AppText.tr("заданий"):last==1?AppText.tr("задание"):last>=2&&last<=4?AppText.tr("задания"):AppText.tr("заданий"));}
    String volume(){if(!known&&remaining==0)return AppText.tr("Объём уточняется");return (known?AppText.tr("Осталось "):AppText.tr("Не менее "))+ServerTelemetry.bytes(remaining);}
}
