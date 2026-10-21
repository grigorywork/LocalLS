package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One file manager: phone on one side, shared server on the other, with one set of copy actions. */
public class FilesActivity extends BaseActivity {
    private static final int FILES=501,FOLDER=504,PHONE=505;
    private boolean own,wasActive,remoteReady,phoneReady,wideLayout,restarting;private volatile boolean closed;
    private FileConnection endpoint;private Bundle pickerConnection;private String remotePath=".";private volatile int requestGeneration,phoneGeneration;
    private FileNodes.Node phone,server=FileNodes.shareRoot();
    private final ArrayDeque<FileNodes.Node> phoneBack=new ArrayDeque<>(),serverBack=new ArrayDeque<>();
    private final List<FileNodes.Node> phoneEntries=new ArrayList<>(),serverEntries=new ArrayList<>();
    private final LinkedHashSet<FileNodes.Node> phoneSelected=new LinkedHashSet<>(),serverSelected=new LinkedHashSet<>();
    private ListView phoneList,serverList;private TextView phonePath,serverPath,status,help;private ProgressBar progress;
    private Button send,receive;
    private final ExecutorService browse=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){renderTransfer();if(!closed)ui.postDelayed(this,300);}};
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);ConfigMigrator.migrate(this);
        own=expectedOwn();endpoint=FileConnection.current(this);if(!own)remotePath=places().last();
        phone=FileNodes.phoneRoot(this);
        if(saved!=null){
            Bundle b=saved.getBundle("phone");if(b!=null)phone=FileNodes.Node.from(b);restoreBack(saved,"phone_back",phoneBack);pickerConnection=saved.getBundle("picker_connection");
            if(sameScreen(saved.getBundle("file_connection"))){remotePath=saved.getString("remote_path",".");b=saved.getBundle("server");if(b!=null)server=FileNodes.Node.from(b);restoreBack(saved,"server_back",serverBack);}
        }
        LinearLayout root=column();root.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView title=label(root,"Файлы",20);title.setGravity(Gravity.CENTER);if(getResources().getConfiguration().fontScale>=1.5f)title.setVisibility(View.GONE);
        help=label(root,"Файл: нажми для выбора. Папка: удерживай для выбора. Операции — в меню удержания.",13);if(getResources().getConfiguration().fontScale>1.25f)help.setVisibility(View.GONE);
        LinearLayout tools=row(root);
        button(tools,"Файлы",()->pickFiles());button(tools,"Папку",()->pickTree(FOLDER));button(tools,"Место",()->pickTree(PHONE));
        LinearLayout panes=new LinearLayout(this);panes.setOrientation(getResources().getConfiguration().screenWidthDp>=600?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(panes,new LinearLayout.LayoutParams(-1,0,1));
        boolean wide=panes.getOrientation()==LinearLayout.HORIZONTAL;wideLayout=wide;
        LinearLayout left=column(),right=column();panes.addView(left,new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1));panes.addView(right,new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1));
        phonePath=label(left,"Телефон",14);phonePath.setMaxLines(1);phonePath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout nav=row(left);button(nav,"↑",()->{if(!phoneBack.isEmpty()){phone=phoneBack.pop();refreshPhone();}});button(nav,getResources().getConfiguration().fontScale>1.25f?"↓":"Загрузки",()->{phone=FileNodes.downloadsRoot();phoneBack.clear();refreshPhone();});button(nav,"↻",this::refreshPhone);button(nav,"⋮",this::phoneMenu).setContentDescription("Меню файлов телефона");
        phoneList=listPanel(left,R.id.phoneFileList);
        serverPath=label(right,own?"Мой сервер":"Сервер",14);serverPath.setMaxLines(1);serverPath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nav=row(right);button(nav,"↑",()->{if(own){if(!serverBack.isEmpty())server=serverBack.pop();}else remotePath=SftpClient.parent(remotePath);refreshServer();});button(nav,"⌂",()->{server=FileNodes.shareRoot();serverBack.clear();remotePath=".";refreshServer();});button(nav,"↻",this::refreshServer);button(nav,"⋮",this::serverMenu).setContentDescription("Меню файлов сервера");
        serverList=listPanel(right,R.id.remoteFileList);serverPath.setOnClickListener(v->promptPath());
        status=label(root,"",12);status.setId(R.id.transferStatusText);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(10000);root.addView(progress);
        LinearLayout actions=row(root);send=button(actions,"На сервер ↓",this::sendSelection);receive=button(actions,"На телефон ↑",this::receiveSelection);
        status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        setContentView(root);if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},506);refreshPhone();refreshServer();renderTransfer();
        ArrayList<Uri> attached=FileAttachments.from(getIntent());
        if(saved==null && !attached.isEmpty())receivePicked(attached);
        else if(saved==null && getIntent().getBooleanExtra(FileAttachments.PICK_ON_OPEN,false))pickFiles();
    }
    private ListView listPanel(LinearLayout parent,int id){
        FrameLayout frame=new FrameLayout(this);parent.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setId(id);frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=new TextView(this);empty.setText("Нет файлов");empty.setTextSize(14);empty.setGravity(Gravity.CENTER);frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);return list;
    }
    private RemotePlaces places(){return new RemotePlaces(getSharedPreferences(ServerConfig.PREFS,0),endpoint.host,endpoint.port,endpoint.user);}
    public boolean openRemotePath(String path){
        try{if(path==null || !path.startsWith("/"))return false;String relative=path.substring(1);TransferPaths.relative(relative);
            if(own)server=new FileNodes.Node("share",relative,relative.isEmpty()?"Мой сервер":new File(relative).getName(),true,0);else remotePath=path;
            refreshServer();return true;
        }catch(IllegalArgumentException e){return false;}
    }
    private void promptPath(){EditText input=new EditText(this);input.setText(own?"/"+server.path:remotePath);new AlertDialog.Builder(this).setTitle("Открыть папку сервера").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Открыть",(d,w)->{if(!openRemotePath(input.getText().toString().trim()))message("Укажи абсолютный путь, начиная с /");}).show();}
    private void favorites(){if(own){message("Избранное относится к подключённому серверу");return;}ArrayList<String> saved=places().favorites();ArrayList<String> items=new ArrayList<>();items.add("Добавить текущую папку");items.add("Удалить текущую закладку");items.addAll(saved);new AlertDialog.Builder(this).setTitle("Избранные папки").setItems(items.toArray(new String[0]),(d,w)->{try{if(w==0 && remoteReady)places().add(remotePath);else if(w==1)places().remove(remotePath);else if(w>=2)openRemotePath(saved.get(w-2));}catch(IllegalArgumentException e){message(e.getMessage());}}).show();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(LinearLayout parent){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setBaselineAligned(false);parent.addView(v);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout p,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setPadding(dp(4),dp(4),dp(4),dp(4));p.addView(t);return t;}
    private Button button(LinearLayout p,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTag("file_toolbar");b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(4),dp(8),dp(4),dp(8));p.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setContentDescription(text.equals("↑")?"Вверх":text.equals("↻")?"Обновить":text.equals("⌂")?"Домой":text.equals("↓")?"Загрузки":text.equals("⋮")?"Меню файлов":text.equals("Место")?"Выбрать папку телефона":text);b.setOnClickListener(v->action.run());return b;}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void renderList(boolean local){
        List<FileNodes.Node> nodes=local?phoneEntries:serverEntries;LinkedHashSet<FileNodes.Node> selected=local?phoneSelected:serverSelected;ListView list=local?phoneList:serverList;
        selected.clear();ArrayList<String> text=new ArrayList<>();for(FileNodes.Node n:nodes)text.add((n.directory?"▸ ":"")+n.name+(!n.directory?"\n"+LocalFileExportService.format(n.size):""));
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,text));
        list.setOnItemClickListener((p,v,position,id)->{FileNodes.Node n=nodes.get(position);
            if(n.directory){if(local){phoneBack.push(phone);phone=n;refreshPhone();}else{if(own){serverBack.push(server);server=n;}else remotePath=n.path;refreshServer();}}
            else{if(!selected.add(n))selected.remove(n);list.setItemChecked(position,selected.contains(n));buttons();}});
        list.setOnItemLongClickListener((p,v,position,id)->{FileNodes.Node n=nodes.get(position);new AlertDialog.Builder(this).setTitle(n.name)
                .setItems(new String[]{selected.contains(n)?"Снять выбор":"Выбрать для копирования","Переименовать","Удалить"},(d,w)->{
                    if(w==0){if(!selected.add(n))selected.remove(n);list.setItemChecked(position,selected.contains(n));buttons();}
                    else if(w==1)rename(n,local);else remove(n,local);
                }).show();return true;});buttons();
    }
    private void refreshPhone(){
        int generation=++phoneGeneration;phoneReady=false;phoneSelected.clear();phoneList.clearChoices();phoneList.setEnabled(false);buttons();FileNodes.Node folder=phone;phonePath.setText("Телефон · "+("downloads".equals(folder.kind)?"Загрузки / LocalLS/"+folder.path:folder.name));
        browse.execute(()->{try{List<FileNodes.Node> result=FileNodes.children(this,folder);ui.post(()->{if(!closed && generation==phoneGeneration && phone==folder){phoneReady=true;phoneList.setEnabled(true);phoneEntries.clear();phoneEntries.addAll(result);renderList(true);}});}catch(Exception e){ui.post(()->{if(!closed && generation==phoneGeneration && phone==folder){phoneEntries.clear();renderList(true);message("Папка телефона недоступна. Выбери её заново кнопкой «Место».");}});}});
    }
    private void refreshServer(){
        if(restarting)return;
        int generation=++requestGeneration;String path=remotePath;FileNodes.Node folder=server;remoteReady=false;serverSelected.clear();serverList.setEnabled(false);buttons();
        serverPath.setText((own?"Мой сервер · "+folder.name:"Сервер · "+path)+"\nЗагрузка…");
        browse.execute(()->{if(closed || generation!=requestGeneration)return;try{
            List<FileNodes.Node> result=new ArrayList<>();String actual=path;
            if(own)result=FileNodes.children(this,folder);
            else{SftpClient.DirectoryListing listing=SftpClient.browse(endpoint.host,endpoint.port,endpoint.user,SshKeys.keyCredentials(this,endpoint.host,endpoint.port,endpoint.user),endpoint.pin,path);actual=listing.path;for(RemoteEntry e:listing.entries)if(!TransferPaths.temporary(e.name))result.add(new FileNodes.Node("remote",e.path,e.name,e.directory,e.size));}
            List<FileNodes.Node> finalResult=result;String finalPath=actual;
            ui.post(()->{if(closed || generation!=requestGeneration)return;remoteReady=true;serverList.setEnabled(true);if(!own){remotePath=finalPath;places().remember(finalPath);}serverEntries.clear();serverEntries.addAll(finalResult);renderList(false);serverPath.setText(own?"Мой сервер · /"+server.path:"Сервер · "+remotePath);});
        }catch(Exception e){ui.post(()->{if(!closed && generation==requestGeneration){serverEntries.clear();renderList(false);serverPath.setText(own?"Мой сервер · ошибка":"Сервер недоступен · проверь подключение и ключ");}});}});
    }
    private void buttons(){if(send==null)return;send.setEnabled(phoneReady && remoteReady && !phoneSelected.isEmpty());receive.setEnabled(phoneReady && remoteReady && !serverSelected.isEmpty());send.setText("На сервер ↓ ("+phoneSelected.size()+")");receive.setText("На телефон ↑ ("+serverSelected.size()+")");}
    private void sendSelection(){enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,new ArrayList<>(phoneSelected),own?server:null);}
    private void receiveSelection(){enqueue(own?TransferForegroundService.SAVE:TransferForegroundService.DOWNLOAD,new ArrayList<>(serverSelected),phone);}
    private void enqueue(String action,List<FileNodes.Node> nodes,FileNodes.Node target){
        if(!ensureConnection())return;
        if(!own && (endpoint.pin.isEmpty() || endpoint.host.isEmpty())){message("Сначала проверь SSH и подтверди fingerprint на главном экране.");return;}
        if(nodes.size()>64){message("Выбери до 64 файлов или папок за один раз. Вложенные файлы папки копируются вместе с ней.");return;}
        try{TransferForegroundService.enqueue(this,action,nodes,remotePath,target,endpoint);phoneSelected.clear();serverSelected.clear();renderList(true);renderList(false);message("Добавлено в очередь. Можно свернуть приложение.");}catch(Exception e){message("Android не разрешил начать передачу. Открой приложение и повтори.");}
    }
    private void pickFiles(){if(!ensureConnection())return;pickerConnection=screenConnection();try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),FILES);}catch(Exception e){message("Не удалось открыть выбор файлов");}}
    private void pickTree(int code){if(code!=PHONE){if(!ensureConnection())return;pickerConnection=screenConnection();}try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),code);}catch(Exception e){message("Не удалось открыть выбор папки");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        if(request==FILES || request==FOLDER){
            Bundle picked=pickerConnection;pickerConnection=null;
            if(!sameScreen(picked) || !ensureConnection()){message("Подключение изменилось. Выбери файлы заново для текущего сервера.");return;}
        }
        try{
            if(request==PHONE || request==FOLDER){DocumentFiles.grant(this,data);FileNodes.Node folder=FileNodes.document(this,DocumentFiles.root(data.getData()));
                if(request==PHONE){if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException("Папка только для чтения");getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.PHONE_TREE,data.getData().toString()).apply();phone=folder;phoneBack.clear();refreshPhone();message("Папка телефона сохранена. Копирование будет в открытую здесь папку. Автосохранение сервера настраивается в «Мой сервер».");}
                else enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,Collections.singletonList(folder),own?server:null);
            }else if(request==FILES){ArrayList<Uri> uris=FileAttachments.from(data);for(Uri uri:uris)try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}receivePicked(uris);}
        }catch(Exception e){message("Нет доступа к папке. Выбери доступную папку, например созданную внутри «Документы».");}
    }
    private void receivePicked(List<Uri> uris){int generation=requestGeneration;FileNodes.Node folder=server;browse.execute(()->{try{ArrayList<FileNodes.Node> nodes=new ArrayList<>();for(Uri uri:uris)nodes.add(FileNodes.document(this,uri));ui.post(()->{if(!closed){if(generation!=requestGeneration || folder!=server){message("Папка изменилась. Выбери файлы заново.");return;}enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,nodes,own?folder:null);}});}catch(Exception e){ui.post(()->message("Не удалось прочитать выбранные файлы"));}});}
    private void serverMenu(){new AlertDialog.Builder(this).setTitle(own?"Мой сервер":"Сервер").setItems(new String[]{"Новая папка","Выбрать всё","Снять выбор","Отменить очередь","Повторить неудачную передачу","Открыть папку","Избранное","Назад"},(d,w)->{if(w==0)newFolder(false);else if(w==3)TransferForegroundService.cancel(this);else if(w==4){if(TransferForegroundService.canRetry())TransferForegroundService.retry(this);else message("Неудачных передач для повтора нет");}else if(w==5)promptPath();else if(w==6)favorites();else if(w==7)finish();else selectAll(false,w==1);}).show();}
    private void selectAll(boolean local,boolean all){
        if(local?!phoneReady:!remoteReady)return;
        List<FileNodes.Node> entries=local?phoneEntries:serverEntries;Set<FileNodes.Node> selected=local?phoneSelected:serverSelected;ListView list=local?phoneList:serverList;
        selected.clear();if(all)selected.addAll(entries);for(int n=0;n<entries.size();n++)list.setItemChecked(n,all);buttons();
    }
    private void phoneMenu(){
        boolean create=phoneReady && "doc".equals(phone.kind) && DocumentsContract.isDocumentUri(this,Uri.parse(phone.path));
        String[] items=create?new String[]{"Новая папка","Выбрать всё","Снять выбор"}:new String[]{"Выбрать всё","Снять выбор"};
        new AlertDialog.Builder(this).setTitle("Телефон").setItems(items,(d,w)->{if(create && w==0)newFolder(true);else selectAll(true,w==(create?1:0));}).show();
    }
    private void newFolder(boolean local){
        if(local?!phoneReady:!remoteReady){message("Сначала открой доступную папку и дождись её загрузки.");return;}
        FileNodes.Node folder=local?phone:server;String path=remotePath;EditText input=new EditText(this);
        new AlertDialog.Builder(this).setTitle(local?"Новая папка на телефоне":"Новая папка на сервере").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{
            if(!local && !ensureConnection())return;String raw=input.getText().toString().trim();operation(()->{String name=TransferPaths.name(raw);
                if(local){if(!"doc".equals(folder.kind))throw new IOException();Uri at=Uri.parse(folder.path);DocumentFiles.create(this,at,DocumentFiles.unique(this,at,name),DocumentFiles.DIRECTORY);}
                else if(own){File parent=new File(EmbeddedServerService.share(this),folder.path);if(!FileNodes.uniqueShare(this,parent,name).mkdir())throw new IOException();}
                else SftpClient.mkdir(this,endpoint.host,endpoint.port,endpoint.user,"",endpoint.pin,SftpClient.join(path,name));
            });
        }).show();
    }
    private void rename(FileNodes.Node node,boolean local){FileNodes.Node parent=phone;EditText input=new EditText(this);input.setText(node.name);new AlertDialog.Builder(this).setTitle("Переименовать").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{if(!local && !ensureConnection())return;operation(()->{
        String name=TransferPaths.name(input.getText().toString().trim());if(name.equals(node.name))return;
        if(local)for(FileNodes.Node entry:FileNodes.children(this,parent))if(!entry.path.equals(node.path) && entry.name.equalsIgnoreCase(name))throw new IOException("Имя занято");
        if("share".equals(node.kind)){File root=EmbeddedServerService.share(this),file=SharedFolderAccess.within(root,new File(root,node.path)),target=new File(file.getParentFile(),name);if(target.exists() || !file.renameTo(target))throw new IOException();}
        else if("remote".equals(node.kind)){String next=SftpClient.join(SftpClient.parent(node.path),name);try(SftpClient.TreeSession s=new SftpClient.TreeSession(endpoint.host,endpoint.port,endpoint.user,SshKeys.keyCredentials(this,endpoint.host,endpoint.port,endpoint.user),endpoint.pin)){String unique=s.unique(SftpClient.parent(node.path),name);if(!next.equals(unique))throw new IOException("Имя занято");}SftpClient.rename(this,endpoint.host,endpoint.port,endpoint.user,"",endpoint.pin,node.path,next);}
        else if("doc".equals(node.kind)){Uri uri=Uri.parse(node.path);
            if(DocumentsContract.isDocumentUri(this,uri)){if(DocumentsContract.renameDocument(getContentResolver(),uri,name)==null)throw new IOException();}
            else if("media".equals(uri.getAuthority())){ContentValues values=new ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);if(getContentResolver().update(uri,values,null,null)!=1)throw new IOException();}
            else throw new IOException();
        }
        else throw new IOException();
    });}).show();}
    private void remove(FileNodes.Node node,boolean local){new AlertDialog.Builder(this).setTitle("Удалить «"+node.name+"»?").setMessage(node.directory?"Удаляется только пустая папка.":"Файл будет удалён из выбранной области.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{if(!local && !ensureConnection())return;operation(()->{
        if("share".equals(node.kind)){File root=EmbeddedServerService.share(this);if(!SharedFolderAccess.within(root,new File(root,node.path)).delete())throw new IOException();}
        else if("remote".equals(node.kind))SftpClient.delete(this,endpoint.host,endpoint.port,endpoint.user,"",endpoint.pin,new RemoteEntry(node.name,node.path,node.directory,node.size,0));
        else if("doc".equals(node.kind)){if(node.directory && !DocumentFiles.children(this,Uri.parse(node.path)).isEmpty())throw new IOException();Uri uri=Uri.parse(node.path);if(DocumentsContract.isDocumentUri(this,uri)){if(!DocumentsContract.deleteDocument(getContentResolver(),uri))throw new IOException();}else if(getContentResolver().delete(uri,null,null)!=1)throw new IOException();}
        else throw new IOException();
    });}).show();}
    private interface Operation{void run()throws Exception;}
    private void operation(Operation action){browse.execute(()->{try{action.run();ui.post(()->{if(!closed){refreshPhone();refreshServer();}});}catch(Exception e){ui.post(()->{if(!closed)message("Операция не выполнена. Проверь доступ, имя и наличие файлов в папке.");});}});}
    private void renderTransfer(){TransferForegroundService.State value=TransferForegroundService.state;status.setText(value.text+(own?"\n"+AutoReceive.status:""));StatusBadge.apply(status,value.error || (own&&AutoReceive.error)?StatusBadge.Tone.ERROR:value.active || (own&&AutoReceive.active)?StatusBadge.Tone.WARNING:value.text.startsWith("Готово")?StatusBadge.Tone.SUCCESS:StatusBadge.Tone.NEUTRAL);
        progress.setVisibility(value.active?View.VISIBLE:View.GONE);progress.setIndeterminate(value.total<=0);progress.setProgress(value.total<=0?0:(int)Math.min(10000,value.bytes*10000d/value.total));
        if(wasActive && !value.active){refreshPhone();refreshServer();}wasActive=value.active;}
    private void restoreBack(Bundle b,String name,ArrayDeque<FileNodes.Node> back){ArrayList<Bundle> raw=b.getParcelableArrayList(name);if(raw!=null)for(Bundle item:raw)back.addLast(FileNodes.Node.from(item));}
    private void saveBack(Bundle b,String name,ArrayDeque<FileNodes.Node> back){ArrayList<Bundle> raw=new ArrayList<>();for(FileNodes.Node node:back)raw.add(node.bundle());b.putParcelableArrayList(name,raw);}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(wideLayout!=(config.screenWidthDp>=600))recreate();}
    @Override protected void onSaveInstanceState(Bundle b){saveBack(b,"phone_back",phoneBack);saveBack(b,"server_back",serverBack);b.putBundle("file_connection",screenConnection());b.putBundle("picker_connection",pickerConnection);b.putString("remote_path",remotePath);b.putBundle("phone",phone.bundle());b.putBundle("server",server.bundle());super.onSaveInstanceState(b);}
    private boolean expectedOwn(){return DeviceRole.serverOnly(this) || getIntent().getBooleanExtra("own_server",false);}
    private Bundle screenConnection(){Bundle b=endpoint.bundle();b.putBoolean("own_server",own);return b;}
    private boolean sameScreen(Bundle b){return b!=null && own==b.getBoolean("own_server") && (own || endpoint.matches(b));}
    private boolean ensureConnection(){
        if(restarting)return false;
        if(own==expectedOwn() && (own || endpoint.matches(FileConnection.current(this).bundle())))return true;
        restarting=true;ui.removeCallbacks(poll);requestGeneration++;remoteReady=false;serverSelected.clear();serverEntries.clear();serverList.setEnabled(false);renderList(false);
        message("Подключение изменилось. Открываем файлы текущего сервера.");recreate();return false;
    }
    @Override protected void onResume(){super.onResume();closed=false;if(ensureConnection())ui.post(poll);}
    @Override protected void onPause(){ui.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){closed=true;requestGeneration++;phoneGeneration++;ui.removeCallbacksAndMessages(null);browse.shutdownNow();super.onDestroy();}
}
