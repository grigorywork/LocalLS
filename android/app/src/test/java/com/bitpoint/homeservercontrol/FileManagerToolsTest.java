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
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"large.bin"),0);filter(a,true,".txt");assertTrue(selected(a,true).isEmpty());assertEquals(2,list.getCount());all(a,true);ReflectionHelpers.callInstanceMethod(a,"sendSelection");Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(2,sources.size());for(Bundle source:sources)assertTrue(source.getString("name").endsWith(".txt"));assertEquals("share",job.getBundleExtra("destination").getString("kind"));}
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
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);int at=index(list,"small.txt");list.getOnItemLongClickListener().onItemLongClick(list,null,at,at);ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,3,3);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();assertTrue(text.contains("small.txt"));assertTrue(text.contains("Телефон"));assertTrue(text.contains("Размер в списке"));assertFalse(text.contains("content://"));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(documents.root,"small.txt").toPath()));}
    }
    @Test public void cancelledFilterKeepsExistingViewAndSelection()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"small.txt"),0);ReflectionHelpers.callInstanceMethod(a,"promptFilter",ReflectionHelpers.ClassParameter.from(boolean.class,true));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();input(dialog.getWindow().getDecorView()).setText("large");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(4,list.getCount());assertEquals(List.of("small.txt"),selectedNames(a,true));}
    }
    @Test public void filterResetDoesNotRestoreHiddenSelection()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);list.performItemClick(null,index(list,"large.bin"),0);filter(a,true,".txt");ReflectionHelpers.callInstanceMethod(a,"promptFilter",ReflectionHelpers.ClassParameter.from(boolean.class,true));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(4,list.getCount());assertTrue(selected(a,true).isEmpty());}
    }
    @Test public void serverSelectAllCopiesOnlyItsVisibleSourcesToPhoneFolder()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);filter(a,false,"remote.bin");all(a,false);ReflectionHelpers.callInstanceMethod(a,"receiveSelection");Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.SAVE,job.getAction());ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(1,sources.size());assertEquals("remote.bin",sources.get(0).getString("name"));assertEquals(DocumentFiles.root(tree).toString(),job.getBundleExtra("destination").getString("path"));}
    }
    @Test public void selectedFolderSurvivesSortingAndFilteringAndIsQueuedAsDirectory()throws Exception{
        try(var c=screen()){FilesActivity a=c.get();ready(a);ListView list=list(a,true);int at=index(list,"Folder");list.getOnItemLongClickListener().onItemLongClick(list,null,at,at);ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,0,0);sort(a,FileListQuery.SIZE_DESC);filter(a,true,"Folder");assertEquals(List.of("Folder"),selectedNames(a,true));assertTrue(list.isItemChecked(0));ReflectionHelpers.callInstanceMethod(a,"sendSelection");Intent job=Shadows.shadowOf(app).getNextStartedService();assertNotNull(job);assertTrue(job.<Bundle>getParcelableArrayListExtra("sources").get(0).getBoolean("directory"));}
    }
}
