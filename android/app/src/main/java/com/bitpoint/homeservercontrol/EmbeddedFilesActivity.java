package com.bitpoint.homeservercontrol;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Browse the app-managed share locally and explicitly export received files through Android SAF. */
public class EmbeddedFilesActivity extends BaseActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private File current,root;
    private String pending="";
    private TextView title;
    private ListView list;
    private boolean closed;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);root=EmbeddedServerService.share(this);root.mkdirs();current=root;
        if(saved!=null) {pending=saved.getString("pending","");try{current=SharedFolderAccess.within(root,new File(root,saved.getString("folder","")));}catch(IOException ignored){}}
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(16,16,16,16);
        title=new TextView(this);title.setTextSize(18);layout.addView(title);
        TextView help=new TextView(this);help.setText("Папки открываются нажатием. Нажми на файл, чтобы сохранить его в выбранную папку телефона.");layout.addView(help);
        Button up=new Button(this);up.setText("Вверх");layout.addView(up);up.setOnClickListener(v->{if(!current.equals(root))current=current.getParentFile();render();});
        Button refresh=new Button(this);refresh.setText("Обновить");layout.addView(refresh);refresh.setOnClickListener(v->render());
        list=new ListView(this);layout.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button back=new Button(this);back.setText("Назад");layout.addView(back);back.setOnClickListener(v->finish());setContentView(layout);render();
    }
    private void render() {
        try {
            current=SharedFolderAccess.within(root,current);if(!current.isDirectory())current=root;
            title.setText("Файлы сервера: /"+root.toURI().relativize(current.toURI()).getPath());
            File[] found=current.listFiles();ArrayList<File> entries=new ArrayList<>();
            if(found!=null)for(File f:found) {try {SharedFolderAccess.within(root,f);if(!f.getName().startsWith(".import-"))entries.add(f);}catch(IOException ignored){}}
            entries.sort((a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
            ArrayList<String> labels=new ArrayList<>();for(File f:entries)labels.add((f.isDirectory()?"▸ ":"")+f.getName());
            list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));
            list.setOnItemClickListener((p,v,position,id)->{
                File chosen=entries.get(position);
                try {chosen=SharedFolderAccess.within(root,chosen);
                    if(chosen.isDirectory()){current=chosen;render();return;}
                    pending=root.toURI().relativize(chosen.toURI()).getPath();
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE,chosen.getName());startActivityForResult(intent,913);
                }catch(Exception e){Toast.makeText(this,"Файл недоступен",Toast.LENGTH_SHORT).show();}
            });
        }catch(IOException e){current=root;Toast.makeText(this,"Папка недоступна",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=913 || result!=RESULT_OK || data==null || data.getData()==null || pending.isEmpty())return;
        final String relative=pending;pending="";final android.net.Uri destination=data.getData();
        io.execute(()->{
            boolean success=false;
            try {File source=SharedFolderAccess.within(root,new File(root,relative));
                try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination,"w")) {
                    if(out==null)throw new IOException();byte[] b=new byte[65536];int n;
                    while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();out.write(b,0,n);}success=true;
                }
            }catch(Exception ignored){}
            final boolean ok=success;runOnUiThread(()->{if(!closed)Toast.makeText(this,ok?"Файл сохранён на телефон":"Не удалось сохранить файл",Toast.LENGTH_LONG).show();});
        });
    }
    @Override protected void onSaveInstanceState(Bundle s) {s.putString("pending",pending);s.putString("folder",root.toURI().relativize(current.toURI()).getPath());super.onSaveInstanceState(s);}
    @Override protected void onDestroy(){closed=true;io.shutdownNow();super.onDestroy();}
}
