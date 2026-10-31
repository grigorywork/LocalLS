package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Durable queue, compact live timing and confirmed actions for the entire list. */
public class TransferQueueActivity extends BaseActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private ListView list;
    private TextView status,current,amount,fileTime,queueTime,percent;
    private ProgressBar progress;
    private LinearLayout live;
    private ActionGridLayout actions;
    private List<JSONObject> entries=new ArrayList<>();
    private boolean reading;
    private final Runnable refresh=new Runnable(){public void run(){load();if(isUiActive())ui.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(color(com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
        TextView title=text("Очередь",22,true);
        actions=QueueActions.row(this);root.addView(new QueueHeaderLayout(this,title,actions),new LinearLayout.LayoutParams(-1,-2));
        status=text("Загрузка списка…",12,false);status.setPadding(dp(10),dp(6),dp(10),dp(6));status.setBackground(SurfaceStyle.panel(this));add(root,status);
        live=new LinearLayout(this);live.setOrientation(LinearLayout.VERTICAL);live.setPadding(dp(12),dp(8),dp(12),dp(8));live.setBackground(SurfaceStyle.panel(this));
        current=text("",15,true);current.setMaxLines(1);current.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout fileHeader=new LinearLayout(this);fileHeader.setGravity(Gravity.CENTER_VERTICAL);fileHeader.addView(current,new LinearLayout.LayoutParams(0,-2,1));percent=text("",15,true);percent.setPadding(dp(8),0,0,0);percent.setTextColor(color(com.bitpoint.homeservercontrol.ui.R.attr.hscAccent));fileHeader.addView(percent,new LinearLayout.LayoutParams(-2,-2));live.addView(fileHeader);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(com.bitpoint.homeservercontrol.ui.R.attr.hscAccent)));live.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        amount=text("",12,false);queueTime=text("",12,false);fileTime=text("",12,false);live.addView(new TransferTimingStrip(this,amount,queueTime,fileTime),new LinearLayout.LayoutParams(-1,-2));add(root,live);live.setVisibility(View.GONE);
        list=new ListView(this);list.setDivider(SurfaceStyle.rule(this,12,12));list.setDividerHeight(dp(1));list.setCacheColorHint(android.graphics.Color.TRANSPARENT);list.setBackground(SurfaceStyle.panel(this));LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,0,1);listParams.topMargin=dp(8);root.addView(list,listParams);
        Button back=new Button(this);back.setText("Назад");back.setOnClickListener(v->finish());root.addView(back);setContentView(root);
        list.setOnItemClickListener((p,v,position,id)->{if(position<entries.size())details(entries.get(position));});load();
    }
    private int dp(int value){return SettingsUi.dp(this,value);}
    private int color(int attr){return ThemeCatalog.color(this,attr);}
    private TextView text(String label,int size,boolean bold){TextView t=new TextView(this);t.setText(label);t.setTextSize(size);t.setIncludeFontPadding(false);t.setTextColor(color(bold?com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private void add(LinearLayout root,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);root.addView(v,lp);}
    private void load(){if(reading||isDestroyed())return;reading=true;worker.execute(()->{
        try{List<JSONObject> result=new TransferJournal(this).list();TransferForegroundService.State snapshot=TransferForegroundService.state;float speed=TransferSpeedStore.queueSpeed();QueueEstimate estimate=QueueEstimate.read(this,result,snapshot,speed);
            ui.post(()->{reading=false;if(isDestroyed())return;render(result,snapshot,estimate,speed);});
        }catch(Exception e){ui.post(()->{reading=false;if(!isDestroyed())status.setText("Не удалось прочитать очередь. Журнал сохранён");});}
    });}
    private void render(List<JSONObject> result,TransferForegroundService.State snapshot,QueueEstimate estimate,float speed){
        entries=result;List<String> rows=new ArrayList<>();
        for(JSONObject j:entries){String action=j.optString("action");String direction="upload".equals(action)||"import".equals(action)?"На сервер":"На это устройство";String name="";try{name=j.getJSONArray("sources").getJSONObject(0).getString("name");int count=j.getJSONArray("sources").length();if(count>1)name+=" и ещё "+(count-1);}catch(Exception ignored){}
            rows.add(name+"\n"+direction+" · "+label(j.optString("status")));}
        // Retain the first visible row while live metadata refreshes.
        int first=list.getFirstVisiblePosition(),top=list.getChildCount()>0?list.getChildAt(0).getTop():0;
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows){
            @Override public View getView(int position,View recycled,ViewGroup parent){TextView row=(TextView)super.getView(position,recycled,parent);JSONObject source=entries.get(position).optJSONArray("sources")==null?null:entries.get(position).optJSONArray("sources").optJSONObject(0);row.setTextSize(14);row.setTextColor(color(com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinHeight(dp(56));FileTypeIcons.apply(row,source==null?"":source.optString("name"),source!=null&&source.optBoolean("directory"),24);return row;}
        });if(!entries.isEmpty())list.setSelectionFromTop(Math.min(first,entries.size()-1),top);
        status.setText(entries.isEmpty()?"Очередь пуста":estimate.pending>0?"Вся очередь · "+estimate.pending+" заданий · "+estimate.volume()+(speed>0?String.format(Locale.getDefault()," · %.1f МБ/с",speed):""):"Записей: "+entries.size());
        boolean pending=entries.stream().anyMatch(j->!Arrays.asList("done","cancelled").contains(j.optString("status")));QueueActions.enabled(actions,pending,!entries.isEmpty());
        live.setVisibility(snapshot.active?View.VISIBLE:View.GONE);current.setText(TransferForegroundService.currentName);FileTypeIcons.apply(current,current.getText().toString(),false,20);
        percent.setText(snapshot.total>0?(int)Math.min(100,snapshot.bytes*100d/snapshot.total)+"%":"");progress.setProgress(snapshot.total>0?(int)Math.min(1000,snapshot.bytes*1000d/snapshot.total):0);progress.setIndeterminate(snapshot.active&&snapshot.total<=0);
        amount.setText(snapshot.total>0?ServerTelemetry.bytes(snapshot.bytes)+" из "+ServerTelemetry.bytes(snapshot.total):"Подготовка…");
        long seconds=DashboardLayout.estimateSeconds(snapshot.bytes,snapshot.total,speed);fileTime.setText(snapshot.total>0&&snapshot.bytes>=snapshot.total?"Завершаем…":seconds<0?"Рассчитываем…":DashboardLayout.formatEta(seconds));queueTime.setText(estimate.timing);
    }
    private static String label(String status){switch(status){case "done":return "Готово";case "running":return TransferForegroundService.state.active?"В работе":"Прервано, можно продолжить";case "queued":return "В очереди";case "cancelled":return "Отменено";case "failed":return "Нужен повтор";default:return "Приостановлено";}}
    private void details(JSONObject j){
        String text=label(j.optString("status"))+"\n"+j.optString("message")+"\n"+j.optString("host")+"\nПроверка SHA-256: "+(j.optBoolean("verify")?"включена":"выключена")
                +"\nСовпадение имён: "+(TransferJournal.REPLACE.equals(j.optString("conflict"))?"замена файлов с подтверждением":TransferJournal.SKIP.equals(j.optString("conflict"))?"пропустить":"сохранить обе копии");
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Передача").setMessage(text).setNegativeButton("Закрыть",null);
        if(!"done".equals(j.optString("status"))&&(!"running".equals(j.optString("status"))||!TransferForegroundService.state.active)&&!TransferForegroundService.clearing)dialog.setPositiveButton("Продолжить",(d,w)->{try{TransferForegroundService.resume(this,j.getString("id"));load();}catch(Exception e){status.setText("Не удалось запустить задание. Оно сохранено в очереди");}});
        dialog.show();
    }
    @Override protected void onResume(){super.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
