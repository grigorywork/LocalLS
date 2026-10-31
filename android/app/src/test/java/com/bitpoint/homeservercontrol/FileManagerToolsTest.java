package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FileManagerToolsTest {
    private android.app.Application app;private LastModIntegrationTest.Documents documents;private Uri tree;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();
        documents=new LastModIntegrationTest.Documents();documents.root=Files.createTempDirectory(app.getCacheDir().toPath(),"manager-tools-").toFile();assertTrue(new File(documents.root,"Folder").mkdir());Files.write(new File(documents.root,"small.txt").toPath(),new byte[]{1,2,3});Files.write(new File(documents.root,"notes.txt").toPath(),new byte[5]);Files.write(new File(documents.root,"large.bin").toPath(),new byte[19]);
        ProviderInfo info=new ProviderInfo();info.authority="tools.documents";info.name=LastModIntegrationTest.Documents.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;documents.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,documents);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);android.content.pm.ResolveInfo resolved=new android.content.pm.ResolveInfo();resolved.providerInfo=info;Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(DocumentsContract.PROVIDER_INTERFACE),resolved);
        tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.PHONE_TREE,tree.toString()).commit();File share=EmbeddedServerService.share(app);share.mkdirs();Files.write(new File(share,"remote.bin").toPath(),new byte[]{7});Files.write(new File(share,"hidden.bin").toPath(),new byte[]{8});
    }
    @Test public void readablePhoneMenuOpensBothPickersAndIndependentDestination()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);
            for(String name:new String[]{"Выбрать файлы","Отправить папку","Папка телефона"}){
                ReflectionHelpers.callInstanceMethod(a,"phoneMenu");AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();ListView items=dialog.getListView();int at=-1;for(int i=0;i<items.getCount();i++)if(name.equals(items.getItemAtPosition(i)))at=i;assertTrue(at>=0);items.performItemClick(null,at,at);
                Intent launched=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;
                assertEquals(name.equals("Выбрать файлы")?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT_TREE,launched.getAction());
                assertEquals(tree.toString(),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString(FileNodes.PHONE_TREE,""));
            }
        }
    }
    @Test public void phonePathOpensAnyFolderPickerAndReadOnlyTreeRemainsUsableAfterRestart()throws Exception{
        Files.write(new File(documents.root,"Folder/source.txt").toPath(),new byte[]{3,1,4});
        try(var c=screen()){
            FilesActivity a=c.get();ready(a);ReflectionHelpers.<TextView>getField(a,"phonePath").performClick();
            var request=Shadows.shadowOf(a).getNextStartedActivityForResult();assertNotNull("Folder selection is hidden",request);
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,request.intent.getAction());
            Uri nested=DocumentsContract.buildTreeDocumentUri("tools.documents","root/Folder");
            a.onActivityResult(507,android.app.Activity.RESULT_OK,new Intent().setData(nested).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            ready(a);assertEquals("Folder",ReflectionHelpers.<FileNodes.Node>getField(a,"phone").name);
            list(a,false).performItemClick(null,index(list(a,false),"remote.bin"),0);
            assertFalse(ReflectionHelpers.<Button>getField(a,"receive").isEnabled());
            assertEquals(nested.toString(),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString(FileNodes.BROWSE_TREE,""));
            assertEquals(tree.toString(),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString(FileNodes.PHONE_TREE,""));
            assertEquals(DocumentFiles.root(tree).toString(),FileNodes.phoneRoot(app).path);
            FileNodes.Node source=ReflectionHelpers.<List<FileNodes.Node>>getField(a,"phoneEntries").get(0);
            try(var bytes=FileNodes.open(a,source)){assertArrayEquals(new byte[]{3,1,4},bytes.readAllBytes());}
            AlertDialog previous=ShadowAlertDialog.getLatestAlertDialog();
            ReflectionHelpers.callInstanceMethod(a,"remove",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,source),ReflectionHelpers.ClassParameter.from(boolean.class,true));
            ReflectionHelpers.callInstanceMethod(a,"rename",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,source),ReflectionHelpers.ClassParameter.from(boolean.class,true));
            assertSame(previous,ShadowAlertDialog.getLatestAlertDialog());assertTrue(new File(documents.root,"Folder/source.txt").exists());
            list(a,true).performItemClick(null,index(list(a,true),"source.txt"),0);assertTrue(ReflectionHelpers.<Button>getField(a,"send").isEnabled());
            ReflectionHelpers.callInstanceMethod(a,"sendSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());
        }
        try(var c=screen()){ready(c.get());assertTrue(ReflectionHelpers.<FileNodes.Node>getField(c.get(),"phone").path.contains("Folder"));assertEquals("Folder",ReflectionHelpers.<FileNodes.Node>getField(c.get(),"phone").name);}
    }
    @Test public void cancelledFolderPickerKeepsGrantAndDownloadsChoicePersistsAfterRestart()throws Exception{
        try(var c=screen()){
            FilesActivity a=c.get();ready(a);var before=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();
            ReflectionHelpers.<TextView>getField(a,"phonePath").performClick();var request=Shadows.shadowOf(a).getNextStartedActivityForResult();
            assertEquals(Uri.parse(ReflectionHelpers.<FileNodes.Node>getField(a,"phone").path),request.intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI));
            a.onActivityResult(507,android.app.Activity.RESULT_CANCELED,null);assertEquals(before,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());
            ReflectionHelpers.callInstanceMethod(a,"phoneMenu");ListView menu=ShadowAlertDialog.getLatestAlertDialog().getListView();int at=-1;for(int i=0;i<menu.getCount();i++)if("Загрузки".equals(menu.getItemAtPosition(i)))at=i;assertTrue(at>=0);menu.performItemClick(null,at,at);ready(a);assertEquals("downloads",ReflectionHelpers.<FileNodes.Node>getField(a,"phone").kind);
        }
        try(var c=screen()){ready(c.get());assertEquals("downloads",ReflectionHelpers.<FileNodes.Node>getField(c.get(),"phone").kind);}
    }
    @Test public void transferErrorCanBeOpenedWithoutTruncatingDetails()throws Exception{
        TransferForegroundService.State old=TransferForegroundService.state;
        try(var c=screen()){
            FilesActivity a=c.get();TransferForegroundService.state=new TransferForegroundService.State(false,true,"Передача не завершена. Исходники сохранены. Проверь сеть, папку и свободное место.","EXPORT / IOException",0,0,0);
            ReflectionHelpers.callInstanceMethod(a,"renderTransfer");TextView status=a.findViewById(R.id.transferStatusText);assertTrue(status.getText().toString().contains("Нажми для подробностей"));assertNull(status.getEllipsize());status.performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();assertTrue(text.contains("Исходники сохранены"));assertTrue(text.contains("EXPORT / IOException"));
        }finally{TransferForegroundService.state=old;}
    }
    private org.robolectric.android.controller.ActivityController<FilesActivity> screen(){return Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create();}
    private void ready(FilesActivity a)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!((boolean)ReflectionHelpers.getField(a,"phoneReady") && (boolean)ReflectionHelpers.getField(a,"remoteReady")) && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue((boolean)ReflectionHelpers.getField(a,"phoneReady"));assertTrue((boolean)ReflectionHelpers.getField(a,"remoteReady"));}
    private ListView list(FilesActivity a,boolean local){return a.findViewById(local?R.id.phoneFileList:R.id.remoteFileList);}
    private int index(ListView list,String name){for(int i=0;i<list.getCount();i++){String text=list.getItemAtPosition(i).toString();if(text.equals(name) || text.startsWith(name+"\n") || text.equals("▸ "+name))return i;}throw new AssertionError("Missing "+name);}
    private void filter(FilesActivity a,boolean local,String text){ReflectionHelpers.callInstanceMethod(a,"setFilter",ReflectionHelpers.ClassParameter.from(boolean.class,local),ReflectionHelpers.ClassParameter.from(String.class,text));}
    private void all(FilesActivity a,boolean local){ReflectionHelpers.callInstanceMethod(a,"selectAll",ReflectionHelpers.ClassParameter.from(boolean.class,local),ReflectionHelpers.ClassParameter.from(boolean.class,true));}
    private Set<FileNodes.Node> selected(FilesActivity a,boolean local){return ReflectionHelpers.getField(a,local?"phoneSelected":"serverSelected");}
    private List<String> selectedNames(FilesActivity a,boolean local){ArrayList<String> result=new ArrayList<>();for(FileNodes.Node n:selected(a,local))result.add(n.name);Collections.sort(result);return result;}
    private void sort(FilesActivity a,int order){ReflectionHelpers.callInstanceMethod(a,"sortMenu",ReflectionHelpers.ClassParameter.from(boolean.class,true));ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,order,order);}
    private EditText input(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText found=input(g.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void filteredSelectAllQueuesOnlyVisiblePhoneSources()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"large.bin"),0);filter(a,true,".txt");assertTrue(selected(a,true).isEmpty());assertEquals(2,list.getCount());all(a,true);ReflectionHelpers.callInstanceMethod(a,"sendSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(2,sources.size());for(Bundle source:sources)assertTrue(source.getString("name").endsWith(".txt"));assertEquals("share",job.getBundleExtra("destination").getString("kind"));}
    }
    @Test public void sizeSortPreservesSelectedFileAndCheckboxIdentity()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"small.txt"),0);sort(a,FileListQuery.SIZE_DESC);assertEquals("Folder",((FileNodes.Node)ReflectionHelpers.<List<FileNodes.Node>>callInstanceMethod(a,"visible",ReflectionHelpers.ClassParameter.from(boolean.class,true)).get(0)).name);assertEquals(1,index(list,"large.bin"));assertEquals(3,index(list,"small.txt"));assertTrue(list.isItemChecked(index(list,"small.txt")));assertFalse(list.isItemChecked(index(list,"large.bin")));assertEquals(List.of("small.txt"),selectedNames(a,true));}
    }
    @Test public void inverseSelectionTouchesOnlyVisibleItems()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);filter(a,true,".txt");ListView list=list(a,true);list.performItemClick(null,index(list,"small.txt"),0);ReflectionHelpers.callInstanceMethod(a,"invertSelection",ReflectionHelpers.ClassParameter.from(boolean.class,true));assertEquals(List.of("notes.txt"),selectedNames(a,true));ReflectionHelpers.callInstanceMethod(a,"invertSelection",ReflectionHelpers.ClassParameter.from(boolean.class,true));assertEquals(List.of("small.txt"),selectedNames(a,true));}
    }
    @Test public void phoneAndServerFiltersAreIndependentAndNoMatchesIsExplicit()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);filter(a,true,"small");filter(a,false,"remote.bin");assertEquals(1,list(a,true).getCount());assertEquals(1,list(a,false).getCount());filter(a,true,"not-found");assertEquals(0,list(a,true).getCount());assertEquals("Нет совпадений",((TextView)list(a,true).getEmptyView()).getText().toString());assertEquals(1,list(a,false).getCount());}
    }
    @Test public void recreationKeepsFiltersAndFreshOpenKeepsOnlySort()throws Exception{
        Bundle state=new Bundle();try(var c=screen()){FilesActivity a=c.get();ready(a);filter(a,true,".txt");filter(a,false,"remote.bin");sort(a,FileListQuery.SIZE_DESC);a.onSaveInstanceState(state);}
        try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create(state)){FilesActivity a=c.get();ready(a);assertEquals(2,list(a,true).getCount());assertEquals(1,list(a,false).getCount());assertEquals(0,index(list(a,true),"notes.txt"));}
        try(var c=screen()){FilesActivity a=c.get();ready(a);assertEquals(4,list(a,true).getCount());assertEquals(1,index(list(a,true),"large.bin"));assertEquals("",ReflectionHelpers.getField(a,"phoneFilter"));}
    }
    @Test public void propertiesShowsHumanMetadataWithoutDocumentUri()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);int at=index(list,"small.txt");View item=list.getAdapter().getView(at,null,list);((Button)((ViewGroup)item).getChildAt(((ViewGroup)item).getChildCount()-1)).performClick();ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,3,3);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();assertTrue(text.contains("small.txt"));assertTrue(text.contains("Телефон"));assertTrue(text.contains("Размер в списке"));assertFalse(text.contains("content://"));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(documents.root,"small.txt").toPath()));}
    }
    @Test public void cancelledFilterKeepsExistingViewAndSelection()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"small.txt"),0);ReflectionHelpers.callInstanceMethod(a,"promptFilter",ReflectionHelpers.ClassParameter.from(boolean.class,true));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();input(dialog.getWindow().getDecorView()).setText("large");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(4,list.getCount());assertEquals(List.of("small.txt"),selectedNames(a,true));}
    }
    @Test public void filterResetDoesNotRestoreHiddenSelection()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"large.bin"),0);filter(a,true,".txt");ReflectionHelpers.callInstanceMethod(a,"promptFilter",ReflectionHelpers.ClassParameter.from(boolean.class,true));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(4,list.getCount());assertTrue(selected(a,true).isEmpty());}
    }
    @Test public void serverSelectAllCopiesOnlyItsVisibleSourcesToPhoneFolder()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);filter(a,false,"remote.bin");all(a,false);ReflectionHelpers.callInstanceMethod(a,"receiveSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.SAVE,job.getAction());ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(1,sources.size());assertEquals("remote.bin",sources.get(0).getString("name"));assertEquals(DocumentFiles.root(tree).toString(),job.getBundleExtra("destination").getString("path"));}
    }
    @Test public void selectedFolderSurvivesSortingAndFilteringAndIsQueuedAsDirectory()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);int at=index(list,"Folder");list.getOnItemLongClickListener().onItemLongClick(list,null,at,at);ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,0,0);sort(a,FileListQuery.SIZE_DESC);filter(a,true,"Folder");assertEquals(List.of("Folder"),selectedNames(a,true));assertTrue(list.isItemChecked(0));ReflectionHelpers.callInstanceMethod(a,"sendSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertTrue(job.<Bundle>getParcelableArrayListExtra("sources").get(0).getBoolean("directory"));}
    }
    private CheckBox checkbox(View view){if(view instanceof CheckBox)return (CheckBox)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){CheckBox found=checkbox(group.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void actualCheckboxSelectsAndUnselectsFoldersWithoutNavigatingEitherPane()throws Exception{
        File shared=EmbeddedServerService.share(app);assertTrue(new File(shared,"SharedFolder").mkdir());
        try(var c=screen()){FilesActivity a=c.get();ready(a);
            for(boolean local:new boolean[]{true,false}){
                ListView list=list(a,local);String folder=local?"Folder":"SharedFolder";int at=index(list,folder);Object before=ReflectionHelpers.getField(a,local?"phone":"server");
                View row=list.getAdapter().getView(at,null,list);CheckBox check=checkbox(row);assertNotNull("Folder needs a separately tappable checkbox",check);
                check.performClick();assertEquals(List.of(folder),selectedNames(a,local));assertTrue(list.isItemChecked(at));assertSame("Checking a folder navigated into it",before,ReflectionHelpers.getField(a,local?"phone":"server"));
                check.performClick();assertTrue(selected(a,local).isEmpty());assertFalse(list.isItemChecked(at));
                check.performClick();row=list.getAdapter().getView(at,row,list);assertTrue(checkbox(row).isChecked());checkbox(row).performClick();assertTrue(selected(a,local).isEmpty());
                list.performItemClick(null,at,at);ready(a);assertEquals(folder,ReflectionHelpers.<FileNodes.Node>getField(a,local?"phone":"server").name);
            }
        }
    }
    @Test public void checkboxFileSelectionSurvivesRebindAndSortAndQueuesOnlyCheckedSources()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);int at=index(list,"small.txt");View row=list.getAdapter().getView(at,null,list);CheckBox check=checkbox(row);assertNotNull(check);
            check.performClick();assertEquals(List.of("small.txt"),selectedNames(a,true));sort(a,FileListQuery.SIZE_DESC);at=index(list,"small.txt");row=list.getAdapter().getView(at,row,list);assertTrue(checkbox(row).isChecked());
            ReflectionHelpers.callInstanceMethod(a,"sendSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(1,sources.size());assertEquals("small.txt",sources.get(0).getString("name"));
        }
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void touchingCheckboxInTheLaidOutListSelectsExactlyOnceWithoutOpeningFolder()throws Exception{
        try(var c=screen()){c.start().resume().visible();FilesActivity a=c.get();ready(a);View root=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
            root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,360,640);
            ListView list=list(a,true);int at=index(list,"Folder");View row=list.getChildAt(at-list.getFirstVisiblePosition());assertNotNull(row);CheckBox check=checkbox(row);assertNotNull(check);
            android.graphics.Rect hit=new android.graphics.Rect(0,0,check.getWidth(),check.getHeight());list.offsetDescendantRectToMyCoords(check,hit);assertTrue(hit.width()>=48);assertTrue(hit.height()>=48);
            long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,hit.centerX(),hit.centerY(),0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,hit.centerX(),hit.centerY(),0);
            try{assertTrue(list.dispatchTouchEvent(down));assertTrue(list.dispatchTouchEvent(up));Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));}finally{down.recycle();up.recycle();}
            assertEquals("checked="+check.isChecked()+" path="+ReflectionHelpers.<FileNodes.Node>getField(a,"phone").name+" hit="+hit,List.of("Folder"),selectedNames(a,true));assertTrue(list.isItemChecked(at));assertNotEquals("Checkbox touch opened folder","Folder",ReflectionHelpers.<FileNodes.Node>getField(a,"phone").name);
        }
    }

    @Test public void fileRowsShowNonzeroSmallFileSizesAndDistinguishUnknownFromEmpty(){
        FileEntryRow row=new FileEntryRow(new android.view.ContextThemeWrapper(app,ThemeCatalog.style(app)));
        for(long bytes:new long[]{3,1280}){
            row.bind(new FileNodes.Node("share","small.txt","small.txt",false,bytes),false,checked->{});String detail=ReflectionHelpers.<TextView>getField(row,"detail").getText().toString();
            assertFalse("Nonempty small file looked empty: "+detail,detail.startsWith("0"));assertFalse(detail.contains("МБ"));assertFalse(detail.contains("MB"));
        }
        row.bind(new FileNodes.Node("share","unknown.txt","unknown.txt",false,-1),false,checked->{});assertEquals("Размер неизвестен",ReflectionHelpers.<TextView>getField(row,"detail").getText().toString());
        row.bind(new FileNodes.Node("share","empty.txt","empty.txt",false,0),false,checked->{});assertTrue(ReflectionHelpers.<TextView>getField(row,"detail").getText().toString().startsWith("0"));
    }

}
