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
    private static final int FILES=501,FOLDER=504,PHONE=505,BROWSE=507;
    private static final String PHONE_SORT="file_sort_phone",SERVER_SORT="file_sort_server";
    private String phoneFilter="",serverFilter="";private int phoneOrder,serverOrder;
    private boolean own,wasActive,remoteReady,phoneReady,wideLayout,restarting;private volatile boolean closed;
    private FileConnection endpoint;private Bundle pickerConnection;private String remotePath=".";private volatile int requestGeneration,phoneGeneration;
    private FileNodes.Node phone,server=FileNodes.shareRoot();
    private final ArrayDeque<FileNodes.Node> phoneBack=new ArrayDeque<>(),serverBack=new ArrayDeque<>();
    private final List<FileNodes.Node> phoneEntries=new ArrayList<>(),serverEntries=new ArrayList<>();
    private final LinkedHashSet<FileNodes.Node> phoneSelected=new LinkedHashSet<>(),serverSelected=new LinkedHashSet<>();
    private ListView phoneList,serverList;private TextView phonePath,serverPath,status,phoneEmpty,serverEmpty;private ProgressBar progress;
    private Button send,receive,phoneTab,serverTab;
    private LinearLayout phonePane,serverPane;
    private boolean phoneVisible=true;
    private boolean phoneReadOnly;
    private DeviceKind remoteKind=DeviceKind.UNKNOWN;
    private boolean deviceIdentified;
    private DeviceKind localKind(){return DeviceLabels.local(this);}
    private String remoteLabel(){return own?"Мой сервер":remoteKind.label;}
    private boolean preparing;
    private final ExecutorService browse=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){renderTransfer();if(!closed)ui.postDelayed(this,300);}};
    private HoldPreview preview;
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);preview=new HoldPreview(this);ConfigMigrator.migrate(this);
        own=expectedOwn();endpoint=FileConnection.current(this);if(!own)remotePath=places().last();
        var viewPrefs=getSharedPreferences(ServerConfig.PREFS,0);phoneOrder=FileListQuery.order(viewPrefs.getInt(PHONE_SORT,0));serverOrder=FileListQuery.order(viewPrefs.getInt(SERVER_SORT,0));
        phone=FileNodes.browseRoot(this);
        phoneReadOnly=getSharedPreferences(EmbeddedServerService.PREFS,0).getBoolean(FileNodes.PHONE_READ_ONLY,false);
        if(saved!=null){
            phoneVisible=saved.getBoolean("phone_visible",true);phoneReadOnly=saved.getBoolean("phone_read_only",phoneReadOnly);
            phoneFilter=FileListQuery.filter(saved.getString("phone_filter",""));
            Bundle b=saved.getBundle("phone");if(b!=null)phone=FileNodes.Node.from(b);restoreBack(saved,"phone_back",phoneBack);pickerConnection=saved.getBundle("picker_connection");
            if(sameScreen(saved.getBundle("file_connection"))){serverFilter=FileListQuery.filter(saved.getString("server_filter",""));remotePath=saved.getString("remote_path",".");b=saved.getBundle("server");if(b!=null)server=FileNodes.Node.from(b);restoreBack(saved,"server_back",serverBack);}
        }
        LinearLayout screen=new FileBrowserLayout(this);
        screen.setBackgroundColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
        screen.setPadding(dp(8),dp(4),dp(8),dp(4));
        wideLayout=getResources().getConfiguration().screenWidthDp>=600;
        LinearLayout tabs=new LinearLayout(this);tabs.setBaselineAligned(false);screen.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        phoneTab=tab(tabs,localKind().label,()->showPane(true));serverTab=tab(tabs,remoteLabel(),()->showPane(false));((LinearLayout.LayoutParams)serverTab.getLayoutParams()).leftMargin=dp(6);
        LinearLayout panes=new LinearLayout(this);panes.setOrientation(wideLayout?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        screen.addView(panes,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=column(),right=column();phonePane=left;serverPane=right;for(LinearLayout pane:new LinearLayout[]{left,right}){pane.setBackground(SurfaceStyle.panel(this));pane.setPadding(dp(4),dp(4),dp(4),dp(4));}
        panes.addView(left,new LinearLayout.LayoutParams(wideLayout?0:-1,wideLayout?-1:0,1));panes.addView(right,new LinearLayout.LayoutParams(wideLayout?0:-1,wideLayout?-1:0,1));if(wideLayout)((LinearLayout.LayoutParams)right.getLayoutParams()).leftMargin=dp(8);
        phonePath=label(left,localKind().label,18);phonePath.setTag("file_path");phonePath.setTypeface(null,android.graphics.Typeface.BOLD);phonePath.setMaxLines(2);phonePath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        phonePath.setOnClickListener(v->pickTree(BROWSE));phonePath.setContentDescription("Папка "+localKind().folder+". Нажми, чтобы выбрать другую папку");
        LinearLayout nav=row(left);nav.setTag("file_navigation");button(nav,"Выше",()->{if(!phoneBack.isEmpty()){phone=phoneBack.pop();refreshPhone();}else pickTree(BROWSE);}).setContentDescription("Вверх");button(nav,"Обновить",this::refreshPhone);button(nav,"Меню",this::phoneMenu).setContentDescription("Меню файлов "+localKind().folder);
        phoneList=listPanel(left,R.id.phoneFileList);
        serverPath=label(right,remoteLabel(),18);serverPath.setTag("file_path");serverPath.setTypeface(null,android.graphics.Typeface.BOLD);serverPath.setMaxLines(2);serverPath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nav=row(right);nav.setTag("file_navigation");button(nav,"Выше",()->{if(own){if(!serverBack.isEmpty())server=serverBack.pop();}else remotePath=SftpClient.parent(remotePath);refreshServer();}).setContentDescription("Вверх");button(nav,"Обновить",this::refreshServer);button(nav,"Меню",this::serverMenu).setContentDescription("Меню файлов сервера");
        serverList=listPanel(right,R.id.remoteFileList);serverPath.setOnClickListener(v->promptPath());
        status=label(screen,"",16);status.setId(R.id.transferStatusText);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(10000);android.content.res.ColorStateList transferTint=android.content.res.ColorStateList.valueOf(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent));progress.setProgressTintList(transferTint);progress.setIndeterminateTintList(transferTint);progress.setMinimumHeight(0);progress.setPadding(0,0,0,0);screen.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        LinearLayout actions=row(screen);send=button(actions,"На сервер",this::sendSelection);receive=button(actions,"На "+localKind().target,this::receiveSelection);send.setTag(com.bitpoint.homeservercontrol.ui.R.id.fileTransferUpload,true);
        status.setOnClickListener(v->{if(restoredQueue())startActivity(new Intent(this,TransferQueueActivity.class));else transferDetails();});status.setContentDescription("Состояние передачи. Нажми для подробностей");
        setContentView(screen);showPane(phoneVisible);TransferForegroundService.restore(this);if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},506);refreshPhone();refreshServer();renderTransfer();
        ArrayList<Uri> attached=FileAttachments.from(getIntent());
        if(saved==null && !attached.isEmpty())receivePicked(attached);
        else if(saved==null && getIntent().getBooleanExtra(FileAttachments.PICK_ON_OPEN,false))pickFiles();
    }
    private Button tab(LinearLayout parent,String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setTag("file_tab");parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->action.run());return b;
    }
    private void showPane(boolean local){
        phoneVisible=local;phonePane.setVisibility(wideLayout||local?View.VISIBLE:View.GONE);serverPane.setVisibility(wideLayout||!local?View.VISIBLE:View.GONE);
        phoneTab.setSelected(local);serverTab.setSelected(!local);
        phoneTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(this,local?com.bitpoint.homeservercontrol.ui.R.attr.hscButton:com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));
        serverTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(this,!local?com.bitpoint.homeservercontrol.ui.R.attr.hscButton:com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));
        IconButtons.fileTab(phoneTab,local);IconButtons.fileTab(serverTab,!local);
    }
    private ListView listPanel(LinearLayout parent,int id){
        FrameLayout frame=new FrameLayout(this);parent.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setBackgroundColor(android.graphics.Color.TRANSPARENT);list.setCacheColorHint(android.graphics.Color.TRANSPARENT);list.setDivider(new android.graphics.drawable.ColorDrawable(SurfaceStyle.lineColor(this)));list.setDividerHeight(dp(1));list.setNestedScrollingEnabled(true);list.setId(id);frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=new TextView(this);empty.setText("Нет файлов");empty.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));empty.setTextSize(18);empty.setGravity(Gravity.CENTER);frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);if(id==R.id.phoneFileList)phoneEmpty=empty;else serverEmpty=empty;return list;
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
    private LinearLayout row(LinearLayout parent){LinearLayout v=new ActionGridLayout(this);v.setBaselineAligned(false);parent.addView(v);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout p,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));t.setTextSize(size);t.setPadding(dp(4),dp(4),dp(4),dp(4));p.addView(t);return t;}
    private Button button(LinearLayout p,String text,Runnable action){Button b=new CenteredActionButton(this);b.setText(text);b.setTag("file_toolbar");b.setTextSize(16);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(56));b.setPadding(dp(8),dp(10),dp(8),dp(10));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(2),dp(2),dp(2),dp(2));p.addView(b,params);b.setContentDescription(text);b.setOnClickListener(v->action.run());return b;}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private String filter(boolean local){return local?phoneFilter:serverFilter;}
    private List<FileNodes.Node> visible(boolean local){return FileListQuery.apply(local?phoneEntries:serverEntries,filter(local),local?phoneOrder:serverOrder);}
    private String phoneTitle(){return ("downloads".equals(phone.kind)?"Загрузки / LocalLS/"+phone.path:phone.name)+" ▾"+(phoneReadOnly?" · только чтение":"");}
    private void updatePaneTitle(boolean local,int visibleCount){
        String base=local?phoneTitle():own?"Мой сервер · /"+server.path:remoteLabel()+" · "+remotePath;
        if(!filter(local).isEmpty())base+=" · "+visibleCount+"/"+(local?phoneEntries:serverEntries).size()+" · "+filter(local);
        Set<FileNodes.Node> selection=local?phoneSelected:serverSelected;
        if(!selection.isEmpty()){long bytes=0;boolean known=true;for(FileNodes.Node n:selection){if(n.directory||n.size<0){known=false;break;}try{bytes=Math.addExact(bytes,n.size);}catch(ArithmeticException overflow){known=false;break;}}base+="\nВыбрано: "+selection.size()+(known?" · "+android.text.format.Formatter.formatShortFileSize(this,bytes):"");}
        if(selection.isEmpty()&&wideLayout)base+="\nЭлементов: "+visibleCount;
        (local?phonePath:serverPath).setText(base);
    }
    private void listTools(boolean local){
        if(!local && !ensureConnection())return;
        if(local?!phoneReady:!remoteReady){message("Дождись загрузки доступной папки.");return;}
        new AlertDialog.Builder(this).setTitle((local?localKind().label:remoteLabel())+" · список и выбор").setItems(new String[]{"Фильтр по имени","Сортировка","Обратить выбор"},(d,w)->{if(w==0)promptFilter(local);else if(w==1)sortMenu(local);else invertSelection(local);}).show();
    }
    private void promptFilter(boolean local){
        EditText input=new EditText(this);input.setSingleLine(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});input.setText(filter(local));input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Фильтр по имени").setMessage("В открытой папке. Выбор скрытых элементов будет снят.").setView(input).setNegativeButton("Отмена",null).setNeutralButton("Сбросить",(d,w)->setFilter(local,"")).setPositiveButton("Показать",(d,w)->setFilter(local,input.getText().toString())).show();
    }
    private void setFilter(boolean local,String value){if(!local && !ensureConnection())return;if(local)phoneFilter=FileListQuery.filter(value);else serverFilter=FileListQuery.filter(value);renderList(local);}
    private void sortMenu(boolean local){
        new AlertDialog.Builder(this).setTitle("Сортировка").setSingleChoiceItems(new String[]{"Имя: А–Я","Имя: Я–А","Размер: сначала большие","Размер: сначала маленькие"},local?phoneOrder:serverOrder,(d,w)->{
            if(!local && !ensureConnection()){d.dismiss();return;}int order=FileListQuery.order(w);if(local)phoneOrder=order;else serverOrder=order;
            getSharedPreferences(ServerConfig.PREFS,0).edit().putInt(local?PHONE_SORT:SERVER_SORT,order).apply();renderList(local);d.dismiss();
        }).setNegativeButton("Отмена",null).show();
    }
    private void invertSelection(boolean local){
        if(!local && !ensureConnection())return;if(local?!phoneReady:!remoteReady)return;
        Set<FileNodes.Node> selected=local?phoneSelected:serverSelected;for(FileNodes.Node node:visible(local)){if(!selected.remove(node))selected.add(node);}renderList(local);
    }
    private void properties(FileNodes.Node node,boolean local){
        String text="Имя: "+node.name+"\nТип: "+(node.directory?"Папка":"Файл")+"\nОбласть: "+(local?localKind().label:remoteLabel());
        if(!node.directory)text+="\nРазмер в списке: "+android.text.format.Formatter.formatFileSize(this,Math.max(0,node.size));
        if(!local)text+="\nПуть: "+("share".equals(node.kind)?"/":"")+node.path;
        new AlertDialog.Builder(this).setTitle("Сведения").setMessage(text).setPositiveButton("Закрыть",null).show();
    }
    private void renderList(boolean local){
        List<FileNodes.Node> nodes=visible(local);LinkedHashSet<FileNodes.Node> selected=local?phoneSelected:serverSelected;ListView list=local?phoneList:serverList;
        selected.retainAll(nodes);ArrayList<String> text=new ArrayList<>();for(FileNodes.Node n:nodes)text.add((n.directory?"▸ ":"")+n.name+(!n.directory?"\n"+LocalFileExportService.format(n.size):""));
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_multiple_choice,text){
            @Override public View getView(int position,View recycled,ViewGroup parent){
                FileEntryRow row=recycled instanceof FileEntryRow?(FileEntryRow)recycled:new FileEntryRow(FilesActivity.this);
                FileNodes.Node node=nodes.get(position);
                row.bind(node,selected.contains(node),checked->{
                    if(local?!phoneReady:!remoteReady)return;
                    if(checked)selected.add(node);else selected.remove(node);
                    list.setItemChecked(position,checked);buttons();
                });row.actions(()->fileActions(node,local,list,selected,position));return row;
            }
        });
        list.clearChoices();for(int i=0;i<nodes.size();i++)list.setItemChecked(i,selected.contains(nodes.get(i)));
        (local?phoneEmpty:serverEmpty).setText(filter(local).isEmpty()?"Нет файлов":"Нет совпадений");updatePaneTitle(local,nodes.size());
        list.setOnItemClickListener((p,v,position,id)->{FileNodes.Node n=nodes.get(position);
            if(n.directory){if(local){phoneBack.push(phone);phone=n;refreshPhone();}else{if(own){serverBack.push(server);server=n;}else remotePath=n.path;refreshServer();}}
            else{if(!selected.add(n))selected.remove(n);list.setItemChecked(position,selected.contains(n));buttons();}});
        list.setOnTouchListener((v,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)preview.close();return false;});
        list.setOnItemLongClickListener((p,v,position,id)->{FileNodes.Node node=nodes.get(position);if(node.directory)fileActions(node,local,list,selected,position);else if(local||own||ensureConnection())preview.open(node,endpoint);return true;});buttons();
    }
    private void fileActions(FileNodes.Node n,boolean local,ListView list,Set<FileNodes.Node> selected,int position){
        new AlertDialog.Builder(this).setTitle(n.name).setItems(new String[]{selected.contains(n)?"Снять выбор":"Выбрать для копирования","Переименовать","Удалить","Сведения"},(d,w)->{
            if(w==0){if(!selected.add(n))selected.remove(n);list.setItemChecked(position,selected.contains(n));buttons();}else if(w==1)rename(n,local);else if(w==2)remove(n,local);else properties(n,local);
        }).show();
    }
    private void refreshPhone(){
        int generation=++phoneGeneration;phoneReady=false;phoneSelected.clear();phoneList.clearChoices();phoneList.setEnabled(false);buttons();FileNodes.Node folder=phone;phonePath.setText(phoneTitle());
        browse.execute(()->{try{List<FileNodes.Node> result=FileNodes.children(this,folder);ui.post(()->{if(!closed && generation==phoneGeneration && phone==folder){phoneReady=true;phoneList.setEnabled(true);phoneEntries.clear();phoneEntries.addAll(result);renderList(true);}});}catch(Exception e){ui.post(()->{if(!closed && generation==phoneGeneration && phone==folder){phoneEntries.clear();renderList(true);message("Папка "+localKind().folder+" недоступна. Выбери её заново через «Меню → Папка "+localKind().folder+"».");}});}});
    }
    private void refreshServer(){
        if(restarting)return;
        int generation=++requestGeneration;String path=remotePath;FileNodes.Node folder=server;remoteReady=false;serverSelected.clear();serverList.setEnabled(false);buttons();
        serverPath.setText((own?"Мой сервер · /"+folder.path:remoteLabel()+" · "+path)+"\nЗагрузка…");
        browse.execute(()->{if(closed || generation!=requestGeneration)return;try{
            List<FileNodes.Node> result=new ArrayList<>();String actual=path;DeviceKind identifiedKind=remoteKind;
            if(own)result=FileNodes.children(this,folder);
            else{SftpClient.DirectoryListing listing=SftpClient.browse(endpoint.host,endpoint.port,endpoint.user,SshKeys.keyCredentials(this,endpoint.host,endpoint.port,endpoint.user),endpoint.pin,path,!deviceIdentified);actual=listing.path;identifiedKind=listing.deviceKind;for(RemoteEntry e:listing.entries)if(!TransferPaths.temporary(e.name))result.add(new FileNodes.Node("remote",e.path,e.name,e.directory,e.size));}
            List<FileNodes.Node> finalResult=result;String finalPath=actual;DeviceKind finalKind=identifiedKind;
            ui.post(()->{if(closed || generation!=requestGeneration)return;remoteReady=true;serverList.setEnabled(true);if(!own){if(!deviceIdentified){remoteKind=finalKind;deviceIdentified=true;}remotePath=finalPath;places().remember(finalPath);}serverEntries.clear();serverEntries.addAll(finalResult);renderList(false);});
        }catch(Exception e){ui.post(()->{if(!closed && generation==requestGeneration){serverEntries.clear();renderList(false);serverPath.setText(own?"Мой сервер · ошибка":"Сервер недоступен · проверь подключение и ключ");}});}});
    }
    private void buttons(){if(send==null)return;phoneTab.setText(localKind().label+(phoneSelected.isEmpty()?"":" · "+phoneSelected.size()));serverTab.setText(remoteLabel()+(serverSelected.isEmpty()?"":" · "+serverSelected.size()));send.setEnabled(phoneReady && remoteReady && !phoneSelected.isEmpty());receive.setEnabled(!phoneReadOnly && phoneReady && remoteReady && !serverSelected.isEmpty());send.setText("На "+(own?"сервер":remoteKind.target)+" ("+phoneSelected.size()+")");receive.setText("На "+localKind().target+" ("+serverSelected.size()+")");send.setContentDescription("Отправить на "+(own?"свой сервер":remoteKind.target));receive.setContentDescription("Скачать на "+localKind().target);phoneTab.setContentDescription("Это устройство: "+localKind().label);serverTab.setContentDescription(own?"Мой сервер":"Подключённое устройство: "+remoteKind.label);if(phoneReady)updatePaneTitle(true,phoneList.getCount());if(remoteReady)updatePaneTitle(false,serverList.getCount());}
    private void sendSelection(){enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,new ArrayList<>(phoneSelected),own?server:null);}
    private void receiveSelection(){if(phoneReadOnly){message("В этой папке разрешено только чтение. Выбери папку с доступом к записи");return;}enqueue(own?TransferForegroundService.SAVE:TransferForegroundService.DOWNLOAD,new ArrayList<>(serverSelected),phone);}
    private void enqueue(String action,List<FileNodes.Node> nodes,FileNodes.Node target){
        if(!ensureConnection())return;
        if(!own && (endpoint.pin.isEmpty() || endpoint.host.isEmpty())){message("Сначала проверь SSH и подтверди fingerprint на главном экране.");return;}
        if(nodes.size()>64){message("Выбери до 64 файлов или папок за один раз. Вложенные файлы папки копируются вместе с ней.");return;}
        if(preparing||nodes.isEmpty())return;
        preparing=true;String path=remotePath;Bundle connection=screenConnection();FileNodes.Node fixed=target==null&&own?server:target;
        message("Проверяем объём и свободное место…");
        browse.execute(()->{
            try{TransferPreview preview=TransferPreview.inspect(this,action,nodes,path,fixed,endpoint);
                ui.post(()->{preparing=false;if(closed)return;if(!sameScreen(connection)||!ensureConnection()){message("Подключение изменилось. Выбери файлы заново");return;}confirmTransfer(action,nodes,path,fixed,preview);});
            }catch(Exception error){ui.post(()->{preparing=false;if(!closed)message("Передача не начата: "+(error instanceof IOException?error.getMessage():"проверь сеть, ключ и доступ к папке"));});}
        });
    }
    private void confirmTransfer(String action,List<FileNodes.Node> nodes,String path,FileNodes.Node target,TransferPreview preview){
        LinearLayout options=column();options.setPadding(dp(16),dp(8),dp(16),dp(8));
        label(options,preview.summary(),16);
        label(options,"Если имя уже есть",16);RadioGroup policies=new RadioGroup(this);String[] captions={"Сохранить обе копии","Пропустить","Заменить файлы с подтверждением"};
        for(int n=0;n<captions.length;n++){RadioButton b=new RadioButton(this);b.setId(View.generateViewId());b.setText(captions[n]);policies.addView(b);if(n==0)b.setChecked(true);}options.addView(policies);
        CheckBox verify=new CheckBox(this);verify.setText("Проверить содержимое по SHA-256 (дольше)");options.addView(verify);
        ScrollView optionScroll=new ScrollView(this);optionScroll.addView(options);
        new AlertDialog.Builder(this).setTitle("Передача файлов").setView(optionScroll).setNegativeButton("Отмена",null).setPositiveButton("В очередь",(d,w)->{
            int selected=policies.indexOfChild(policies.findViewById(policies.getCheckedRadioButtonId()));String policy=selected==1?TransferJournal.SKIP:selected==2?TransferJournal.REPLACE:TransferJournal.KEEP;
            if(!TransferJournal.SKIP.equals(policy)&&preview.available>=0&&preview.bytes>preview.available){message("Недостаточно места. Освободи память перед передачей");return;}
            Runnable add=()->{
                if(!ensureConnection())return;
                try{TransferForegroundService.enqueue(this,action,nodes,path,target,endpoint,policy,verify.isChecked());phoneSelected.clear();serverSelected.clear();renderList(true);renderList(false);message("Очередь сохранена. Можно свернуть приложение");}
                catch(Exception e){message("Не удалось начать передачу. Проверь сохранённую очередь");}
            };
            if(TransferJournal.REPLACE.equals(policy))new AlertDialog.Builder(this).setTitle("Заменять совпадающие файлы?")
                    .setMessage("Прежняя копия сохраняется до готовности новой. Совпадающие папки объединяются; лишние файлы в них сохраняются. Исходники копирования не удаляются.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Заменять",(dialog,which)->add.run()).show();else add.run();
        }).show();
    }
    private void pickFiles(){if(!ensureConnection())return;pickerConnection=screenConnection();try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),FILES);}catch(Exception e){message("Не удалось открыть выбор файлов");}}
    private void pickTree(int code){if(code!=PHONE&&code!=BROWSE){if(!ensureConnection())return;pickerConnection=screenConnection();}try{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);if(Build.VERSION.SDK_INT>=26&&"doc".equals(phone.kind)&&DocumentsContract.isDocumentUri(this,Uri.parse(phone.path)))picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(phone.path));startActivityForResult(picker,code);}catch(Exception e){message("Не удалось открыть выбор папки");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        if(request==FILES || request==FOLDER){
            Bundle picked=pickerConnection;pickerConnection=null;
            if(!sameScreen(picked) || !ensureConnection()){message("Подключение изменилось. Выбери файлы заново для текущего сервера.");return;}
        }
        try{
            if(request==PHONE || request==BROWSE || request==FOLDER){if(request==PHONE&&(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException("Папка приёма только для чтения");DocumentFiles.grant(this,data);FileNodes.Node folder=FileNodes.document(this,DocumentFiles.root(data.getData()));
                if(!folder.directory)throw new IOException("Нужна папка");
                if(request==PHONE||request==BROWSE)selectPhoneFolder(data.getData(),folder,(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0);
                else enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,Collections.singletonList(folder),own?server:null);
            }else if(request==FILES){ArrayList<Uri> uris=FileAttachments.from(data);for(Uri uri:uris)try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}receivePicked(uris);}
        }catch(Exception e){message("Нет доступа к папке. Выбери доступную папку, например созданную внутри «Документы».");}
    }
    private void selectPhoneFolder(Uri tree,FileNodes.Node folder,boolean readOnly)throws IOException{
        var edit=getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.BROWSE_TREE,tree.toString())
                .putString(FileNodes.BROWSE_NAME,folder.name).putBoolean(FileNodes.PHONE_READ_ONLY,readOnly);
        // Read-only browsing never changes the writable fallback used by queued transfers.
        if(!readOnly)edit.putString(FileNodes.PHONE_TREE,tree.toString()).putString(FileNodes.PHONE_NAME,folder.name);
        if(!edit.commit())throw new IOException("Не удалось сохранить папку");
        phoneReadOnly=readOnly;phone=folder;phoneBack.clear();refreshPhone();
        message(readOnly?"Папка сохранена для просмотра и отправки. Папка приёма не изменена.":"Папка сохранена. Копирование на "+localKind().target+" будет в открытую здесь папку.");
    }
    private void receivePicked(List<Uri> uris){int generation=requestGeneration;FileNodes.Node folder=server;browse.execute(()->{try{ArrayList<FileNodes.Node> nodes=new ArrayList<>();for(Uri uri:uris)nodes.add(FileNodes.document(this,uri));ui.post(()->{if(!closed){if(generation!=requestGeneration || folder!=server){message("Папка изменилась. Выбери файлы заново.");return;}enqueue(own?TransferForegroundService.IMPORT:TransferForegroundService.UPLOAD,nodes,own?folder:null);}});}catch(Exception e){ui.post(()->message("Не удалось прочитать выбранные файлы"));}});}
    private void serverMenu(){
        ArrayList<String> items=new ArrayList<>(Arrays.asList("Новая папка","Выбрать всё","Снять выбор","Список и выбор","Открыть папку","Корень сервера"));if(!own)items.add("Избранное");
        new AlertDialog.Builder(this).setTitle(remoteLabel()).setItems(items.toArray(new String[0]),(d,w)->{
            String item=items.get(w);if(item.equals("Новая папка"))newFolder(false);else if(item.equals("Выбрать всё"))selectAll(false,true);else if(item.equals("Снять выбор"))selectAll(false,false);else if(item.equals("Список и выбор"))listTools(false);else if(item.equals("Открыть папку"))promptPath();else if(item.equals("Избранное"))favorites();else{server=FileNodes.shareRoot();serverBack.clear();remotePath=".";refreshServer();}
        }).show();
    }
    private void selectAll(boolean local,boolean all){
        if(local?!phoneReady:!remoteReady)return;
        List<FileNodes.Node> entries=visible(local);Set<FileNodes.Node> selected=local?phoneSelected:serverSelected;ListView list=local?phoneList:serverList;
        selected.clear();if(all)selected.addAll(entries);for(int n=0;n<entries.size();n++)list.setItemChecked(n,all);buttons();
    }
    private void phoneMenu(){
        boolean create=!phoneReadOnly && phoneReady && "doc".equals(phone.kind) && DocumentsContract.isDocumentUri(this,Uri.parse(phone.path));
        ArrayList<String> items=new ArrayList<>();if(create)items.add("Новая папка");Collections.addAll(items,"Выбрать всё","Снять выбор","Список и выбор","Выбрать файлы","Отправить папку","Папка "+localKind().folder,"Загрузки");
        new AlertDialog.Builder(this).setTitle(localKind().label).setItems(items.toArray(new String[0]),(d,w)->{
            String item=items.get(w);if(item.equals("Новая папка"))newFolder(true);else if(item.equals("Выбрать всё"))selectAll(true,true);else if(item.equals("Снять выбор"))selectAll(true,false);else if(item.equals("Список и выбор"))listTools(true);else if(item.equals("Выбрать файлы"))pickFiles();else if(item.equals("Отправить папку"))pickTree(FOLDER);else if(item.equals("Папка "+localKind().folder))pickTree(PHONE);else{phone=FileNodes.downloadsRoot();phoneReadOnly=false;getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.PHONE_TREE,"").putString(FileNodes.BROWSE_TREE,"").putBoolean(FileNodes.PHONE_READ_ONLY,false).apply();phoneBack.clear();refreshPhone();}
        }).show();
    }

    private void newFolder(boolean local){
        if(local&&phoneReadOnly){message("Папка доступна только для чтения");return;}
        if(local?!phoneReady:!remoteReady){message("Сначала открой доступную папку и дождись её загрузки.");return;}
        FileNodes.Node folder=local?phone:server;String path=remotePath;EditText input=new EditText(this);
        new AlertDialog.Builder(this).setTitle("Новая папка · "+(local?localKind().label:remoteLabel())).setView(input).setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{
            if(!local && !ensureConnection())return;String raw=input.getText().toString().trim();operation(()->{String name=TransferPaths.name(raw);
                if(local){if(!"doc".equals(folder.kind))throw new IOException();Uri at=Uri.parse(folder.path);DocumentFiles.create(this,at,DocumentFiles.unique(this,at,name),DocumentFiles.DIRECTORY);}
                else if(own){File parent=new File(EmbeddedServerService.share(this),folder.path);if(!FileNodes.uniqueShare(this,parent,name).mkdir())throw new IOException();}
                else SftpClient.mkdir(this,endpoint.host,endpoint.port,endpoint.user,"",endpoint.pin,SftpClient.join(path,name));
            });
        }).show();
    }
    private void rename(FileNodes.Node node,boolean local){if(local&&phoneReadOnly){message("Папка доступна только для чтения");return;}FileNodes.Node parent=phone;EditText input=new EditText(this);input.setText(node.name);new AlertDialog.Builder(this).setTitle("Переименовать").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{if(!local && !ensureConnection())return;operation(()->{
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
    private void remove(FileNodes.Node node,boolean local){if(local&&phoneReadOnly){message("Папка доступна только для чтения");return;}new AlertDialog.Builder(this).setTitle("Удалить «"+node.name+"»?").setMessage(node.directory?"Удаляется только пустая папка.":"Файл будет удалён из выбранной области.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{if(!local && !ensureConnection())return;operation(()->{
        if("share".equals(node.kind)){File root=EmbeddedServerService.share(this);if(!SharedFolderAccess.within(root,new File(root,node.path)).delete())throw new IOException();}
        else if("remote".equals(node.kind))SftpClient.delete(this,endpoint.host,endpoint.port,endpoint.user,"",endpoint.pin,new RemoteEntry(node.name,node.path,node.directory,node.size,0));
        else if("doc".equals(node.kind)){if(node.directory && !DocumentFiles.children(this,Uri.parse(node.path)).isEmpty())throw new IOException();Uri uri=Uri.parse(node.path);if(DocumentsContract.isDocumentUri(this,uri)){if(!DocumentsContract.deleteDocument(getContentResolver(),uri))throw new IOException();}else if(getContentResolver().delete(uri,null,null)!=1)throw new IOException();}
        else throw new IOException();
    });}).show();}
    private interface Operation{void run()throws Exception;}
    private void operation(Operation action){browse.execute(()->{try{action.run();ui.post(()->{if(!closed){refreshPhone();refreshServer();}});}catch(Exception e){ui.post(()->{if(!closed)message("Операция не выполнена. Дождись загрузки папки и проверь доступ, имя и наличие файлов.");});}});}
    private void transferDetails(){
        TransferForegroundService.State value=TransferForegroundService.state;
        String text=value.text+(own?"\n"+AutoReceive.status:"");
        if(!value.diagnostic.isEmpty())text+="\n\nДиагностика: "+value.diagnostic;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Передача файлов").setMessage(text).setNegativeButton("Закрыть",null)
                .setNeutralButton("Очередь и результаты",(d,w)->startActivity(new Intent(this,TransferQueueActivity.class)));
        if(value.active)dialog.setPositiveButton("Отменить передачу",(d,w)->TransferForegroundService.cancel(this));
        else if(TransferForegroundService.canRetry())dialog.setPositiveButton("Повторить",(d,w)->TransferForegroundService.retry(this));
        dialog.show();
    }
    private boolean restoredQueue(){TransferForegroundService.State v=TransferForegroundService.state;return !(own&&AutoReceive.error)&&!v.active&&v.error&&v.queued>0&&v.text.startsWith("Сохранено незавершённых заданий: ");}
    private void renderTransfer(){TransferForegroundService.State value=TransferForegroundService.state;boolean saved=restoredQueue();String full=value.text+(own&&(AutoReceive.active||AutoReceive.error)?"\n"+AutoReceive.status:"");boolean error=value.error||(own&&AutoReceive.error);int sentence=full.indexOf('.');String brief=own&&AutoReceive.error?"Ошибка сохранения.":sentence>=0?full.substring(0,sentence+1):full;status.setText(saved?"В очереди: "+value.queued+" · Продолжить ›":error?brief+" Нажми для подробностей и повтора.":full);status.setContentDescription(full+". Нажми для подробностей");StatusBadge.apply(status,saved?StatusBadge.Tone.WARNING:value.error || (own&&AutoReceive.error)?StatusBadge.Tone.ERROR:value.active || (own&&AutoReceive.active)?StatusBadge.Tone.WARNING:value.text.startsWith("Готово")?StatusBadge.Tone.SUCCESS:StatusBadge.Tone.NEUTRAL);
        progress.setVisibility(value.active?View.VISIBLE:View.GONE);progress.setIndeterminate(value.total<=0);progress.setProgress(value.total<=0?0:(int)Math.min(10000,value.bytes*10000d/value.total));
        if(wasActive && !value.active){refreshPhone();refreshServer();}wasActive=value.active;}
    private void restoreBack(Bundle b,String name,ArrayDeque<FileNodes.Node> back){ArrayList<Bundle> raw=b.getParcelableArrayList(name);if(raw!=null)for(Bundle item:raw)back.addLast(FileNodes.Node.from(item));}
    private void saveBack(Bundle b,String name,ArrayDeque<FileNodes.Node> back){ArrayList<Bundle> raw=new ArrayList<>();for(FileNodes.Node node:back)raw.add(node.bundle());b.putParcelableArrayList(name,raw);}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(wideLayout!=(config.screenWidthDp>=600))recreate();}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("phone_visible",phoneVisible);b.putBoolean("phone_read_only",phoneReadOnly);b.putString("phone_filter",phoneFilter);b.putString("server_filter",serverFilter);saveBack(b,"phone_back",phoneBack);saveBack(b,"server_back",serverBack);b.putBundle("file_connection",screenConnection());b.putBundle("picker_connection",pickerConnection);b.putString("remote_path",remotePath);b.putBundle("phone",phone.bundle());b.putBundle("server",server.bundle());super.onSaveInstanceState(b);}
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
    @Override protected void onPause(){preview.close();ui.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){if(preview!=null)preview.destroy();closed=true;requestGeneration++;phoneGeneration++;ui.removeCallbacksAndMessages(null);browse.shutdownNow();super.onDestroy();}
}
