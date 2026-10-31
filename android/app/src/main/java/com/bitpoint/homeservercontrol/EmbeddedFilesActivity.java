package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Local browser; foreground export service owns copies independently of this screen. */
public class EmbeddedFilesActivity extends BaseActivity {
    private File current,root;
    private String pending="";
    private TextView title,status;
    private ProgressBar progress;
    private Button diagnostics;
    private ListView list;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable update=new Runnable(){public void run(){renderStatus();ui.postDelayed(this,250);}};
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);root=EmbeddedServerService.share(this);root.mkdirs();current=root;
        if(saved!=null) {pending=saved.getString("pending","");try{current=SharedFolderAccess.within(root,new File(root,saved.getString("folder","")));}catch(IOException ignored){}}
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        int gap=Math.round(16*getResources().getDisplayMetrics().density);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(gap,gap,gap,gap);
        title=new TextView(this);title.setTextSize(18);layout.addView(title);
        TextView help=new TextView(this);help.setText("Это принятые файлы внутри LocalLS. Нажми на файл и выбери сохранение в «Загрузки» или другую папку. Исходный файл останется здесь.");layout.addView(help);
        status=new TextView(this);status.setTextSize(16);layout.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(10000);layout.addView(progress);
        diagnostics=new Button(this);diagnostics.setText("Скопировать ошибку сохранения");layout.addView(diagnostics);
        diagnostics.setOnClickListener(v -> {
            String report=LocalFileExportService.state.diagnostic;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("LocalLS export",report));
            Toast.makeText(this,"Диагностика скопирована без имён файлов и адресов",Toast.LENGTH_LONG).show();
        });
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.HORIZONTAL);layout.addView(navigation);
        Button up=new CenteredActionButton(this);up.setText("Выше");up.setContentDescription("Вверх");up.setTag("file_toolbar");navigation.addView(up,new LinearLayout.LayoutParams(0,-2,1));up.setOnClickListener(v->{if(!current.equals(root))current=current.getParentFile();render();});
        Button refresh=new CenteredActionButton(this);refresh.setText("Обновить");refresh.setTag("file_toolbar");navigation.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));refresh.setOnClickListener(v->render());
        ScrollView controls=new ScrollView(this);controls.addView(layout);outer.addView(controls,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this);outer.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=new TextView(this);empty.setText("Файлов пока нет. Дождись завершения передачи и нажми «Обновить».");outer.addView(empty);list.setEmptyView(empty);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);outer.addView(actions);
        Button downloads=new Button(this);downloads.setText("Открыть «Загрузки»");actions.addView(downloads,new LinearLayout.LayoutParams(0,-2,1));downloads.setOnClickListener(v -> {
            try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}
            catch(Exception error){Toast.makeText(this,"Открой системные «Файлы → Загрузки → LocalLS»",Toast.LENGTH_LONG).show();}
        });
        Button back=new Button(this);back.setText("Назад");actions.addView(back,new LinearLayout.LayoutParams(0,-2,1));back.setOnClickListener(v->finish());setContentView(outer);render();renderStatus();
    }
    private void renderStatus() {
        LocalFileExportService.State value=LocalFileExportService.state;
        if(!value.text.contentEquals(status.getText()))status.setText(value.text);
        StatusBadge.apply(status,value.active?StatusBadge.Tone.WARNING:value.success?StatusBadge.Tone.SUCCESS:value.diagnostic.isEmpty()?StatusBadge.Tone.NEUTRAL:StatusBadge.Tone.ERROR);
        progress.setVisibility(value.active?android.view.View.VISIBLE:android.view.View.GONE);
        progress.setIndeterminate(value.total==0);progress.setProgress(value.total==0?0:(int)Math.min(10000,value.copied*10000d/value.total));
        diagnostics.setVisibility(value.diagnostic.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);list.setEnabled(!value.active);
    }
    private void render() {
        try {
            current=SharedFolderAccess.within(root,current);if(!current.isDirectory())current=root;
            title.setText("Принятые файлы: /"+root.toURI().relativize(current.toURI()).getPath());
            File[] found=current.listFiles();ArrayList<File> entries=new ArrayList<>();
            if(found!=null)for(File f:found) {try {SharedFolderAccess.within(root,f);if(!TransferPaths.temporary(f.getName()))entries.add(f);}catch(IOException ignored){}}
            entries.sort((a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
            ArrayList<String> labels=new ArrayList<>();for(File f:entries)labels.add((f.isDirectory()?"▸ ":"")+f.getName()+(f.isFile()?"\n"+LocalFileExportService.format(f.length()):""));
            list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
                @Override public android.view.View getView(int position,android.view.View recycled,android.view.ViewGroup parent){TextView row=(TextView)super.getView(position,recycled,parent);File file=entries.get(position);FileTypeIcons.apply(row,file.getName(),file.isDirectory(),28);return row;}
            });
            list.setOnItemClickListener((p,v,position,id)->{
                File chosen=entries.get(position);
                try {chosen=SharedFolderAccess.within(root,chosen);
                    if(chosen.isDirectory()){current=chosen;render();return;}
                    final String relative=root.toURI().relativize(chosen.toURI()).getPath();
                    if(Build.VERSION.SDK_INT>=29)new AlertDialog.Builder(this).setTitle("Сохранить файл на телефон")
                        .setItems(new String[]{"В «Загрузки → LocalLS»","Выбрать другое место"},(dialog,which) -> {if(which==0)export(relative,null,0);else chooseLocation(relative);}).show();
                    else chooseLocation(relative);
                }catch(Exception error){Toast.makeText(this,"Файл недоступен. Обнови список.",Toast.LENGTH_LONG).show();}
            });
        }catch(IOException error){current=root;Toast.makeText(this,"Папка недоступна",Toast.LENGTH_LONG).show();}
    }
    private void chooseLocation(String relative) {
        try {pending=relative;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE,new File(relative).getName());startActivityForResult(intent,913);
        }catch(Exception error){Toast.makeText(this,"Не удалось открыть выбор места сохранения",Toast.LENGTH_LONG).show();}
    }
    private void export(String relative,android.net.Uri destination,int flags) {
        try{LocalFileExportService.start(this,relative,destination,flags);}
        catch(Exception error){Toast.makeText(this,LocalFileExportService.state.text,Toast.LENGTH_LONG).show();}
        renderStatus();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=913 || result!=RESULT_OK || data==null || data.getData()==null || pending.isEmpty())return;
        String relative=pending;pending="";export(relative,data.getData(),data.getFlags());
    }
    @Override protected void onSaveInstanceState(Bundle s) {s.putString("pending",pending);s.putString("folder",root.toURI().relativize(current.toURI()).getPath());super.onSaveInstanceState(s);}
    @Override protected void onResume(){super.onResume();ui.removeCallbacks(update);ui.post(update);}
    @Override protected void onPause(){ui.removeCallbacks(update);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
