package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.database.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FilePaneFlowTest {
    private Context app;private LastModIntegrationTest.Documents provider;private Uri phoneTree,autoTree;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();
        provider=new LastModIntegrationTest.Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"panes-").toFile();
        assertTrue(new File(provider.root,"Phone/Inside").mkdirs());assertTrue(new File(provider.root,"Auto").mkdirs());
        Files.write(new File(provider.root,"Phone/file.bin").toPath(),new byte[]{3,4,5});
        ProviderInfo info=new ProviderInfo();info.authority=LastModIntegrationTest.AUTHORITY;info.name=LastModIntegrationTest.Documents.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;
        provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);
        android.content.pm.ResolveInfo resolved=new android.content.pm.ResolveInfo();resolved.providerInfo=info;
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(DocumentsContract.PROVIDER_INTERFACE),resolved);
        phoneTree=DocumentsContract.buildTreeDocumentUri(info.authority,"root/Phone");autoTree=DocumentsContract.buildTreeDocumentUri(info.authority,"root/Auto");
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,autoTree.toString()).commit();
    }
    private org.robolectric.android.controller.ActivityController<FilesActivity> screen(){return Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create();}
    private void choose(FilesActivity a){a.onActivityResult(505,Activity.RESULT_OK,new Intent().setData(phoneTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));}
    private void await(java.util.function.BooleanSupplier ready)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!ready.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(15);}assertTrue(ready.getAsBoolean());
    }
    private FileNodes.Node current(FilesActivity a){return ReflectionHelpers.getField(a,"phone");}
    private Button button(View root,String text){if(root instanceof Button && text.equals(((Button)root).getContentDescription()))return (Button)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
    @Test public void choosingPhoneFolderDoesNotChangeServerAutoSaveAndSurvivesReopen()throws Exception{
        try(var c=screen()){choose(c.get());assertEquals(autoTree.toString(),ReceiveFolder.selected(app));assertEquals(DocumentFiles.root(phoneTree).toString(),current(c.get()).path);}
        try(var c=screen()){assertEquals(DocumentFiles.root(phoneTree).toString(),current(c.get()).path);assertEquals(autoTree.toString(),ReceiveFolder.selected(app));}
    }
    @Test public void upNavigationRemainsAvailableAfterScreenRecreation()throws Exception{
        Bundle state=new Bundle();
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);ListView list=a.findViewById(R.id.phoneFileList);await(()->list.getCount()==2);
            list.performItemClick(null,0,0);await(()->current(a).name.equals("Inside"));a.onSaveInstanceState(state);
        }
        try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create(state)){
            FilesActivity a=c.get();Button up=button(a.findViewById(android.R.id.content),"Вверх");assertNotNull(up);up.performClick();
            assertEquals(DocumentFiles.root(phoneTree).toString(),current(a).path);
        }
    }
    @Test public void refreshingPhoneClearsOldSelectionBeforeAsyncListingFinishes()throws Exception{
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);ListView list=a.findViewById(R.id.phoneFileList);await(()->list.getCount()==2);list.performItemClick(null,1,1);
            Set<?> selected=ReflectionHelpers.getField(a,"phoneSelected");assertEquals(1,selected.size());
            ReflectionHelpers.callInstanceMethod(a,"refreshPhone");assertTrue("Stale selections must not be submitted while a pane loads",selected.isEmpty());
        }
    }
    @Test public void downloadsRootSurvivesSavedStateWithoutInvalidDisplayNameCrash(){
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().remove(ReceiveFolder.TREE).commit();Bundle state=new Bundle();
        try(var c=screen()){assertEquals("downloads",current(c.get()).kind);c.get().onSaveInstanceState(state);}
        try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create(state)){assertEquals("downloads",current(c.get()).kind);}
    }
    @Test public void folderPickerKeepsDestinationAcrossRecreationWithoutProfileChange()throws Exception{
        Bundle saved=new Bundle();
        try(var c=screen()){
            ReflectionHelpers.callInstanceMethod(c.get(),"pickTree",ReflectionHelpers.ClassParameter.from(int.class,504));c.get().onSaveInstanceState(saved);
        }
        try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create(saved)){
            c.get().onActivityResult(504,Activity.RESULT_OK,new Intent().setData(phoneTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            Intent job=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());
            ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(1,sources.size());assertEquals(DocumentFiles.root(phoneTree).toString(),sources.get(0).getString("path"));
            assertEquals("share",job.getBundleExtra("destination").getString("kind"));assertEquals(autoTree.toString(),ReceiveFolder.selected(app));
        }
    }
    private EditText input(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){EditText result=input(g.getChildAt(i));if(result!=null)return result;}}return null;}
    @Test public void phoneMenuCreatesFolderOnlyInSelectedDestination()throws Exception{
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady"));
            ReflectionHelpers.callInstanceMethod(a,"phoneMenu");AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();menu.getListView().performItemClick(null,0,0);
            AlertDialog create=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();input(create.getWindow().getDecorView()).setText("Created");create.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(()->new File(provider.root,"Phone/Created").isDirectory());assertFalse(new File(provider.root,"Auto/Created").exists());assertEquals(autoTree.toString(),ReceiveFolder.selected(app));
        }
    }
    public static class Downloads extends ContentProvider {
        final Map<Long,String> names=new LinkedHashMap<>();volatile int updates;
        public Downloads(){names.put(1L,"first.txt");names.put(2L,"second.txt");}
        @Override public boolean onCreate(){return true;}
        @Override public String getType(Uri uri){return "text/plain";}
        @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
        @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
        @Override public synchronized int update(Uri uri,ContentValues values,String where,String[] args){names.put(ContentUris.parseId(uri),values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME));updates++;return 1;}
        @Override public synchronized Cursor query(Uri uri,String[] columns,String where,String[] args,String order){
            MatrixCursor out=new MatrixCursor(columns);for(var entry:names.entrySet()){
                MatrixCursor.RowBuilder row=out.newRow();for(String name:columns){switch(name){case "_id":row.add(entry.getKey());break;case "_display_name":row.add(entry.getValue());break;case "relative_path":row.add("Download/LocalLS/");break;case "_size":row.add(3);break;default:row.add(null);}}
            }return out;
        }
    }
    private void rename(FilesActivity a,String name){
        FileNodes.Node node=new FileNodes.Node("doc","content://media/external/downloads/1","first.txt",false,3);
        ReflectionHelpers.callInstanceMethod(a,"rename",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,node),ReflectionHelpers.ClassParameter.from(boolean.class,true));
        AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();input(dialog.getWindow().getDecorView()).setText(name);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }
    @Test public void appDownloadsRenameUsesMediaStoreAndDoesNotOverwriteExistingName()throws Exception{
        Downloads downloads=new Downloads();ProviderInfo info=new ProviderInfo();info.authority="media";downloads.attachInfo(app,info);ShadowContentResolver.registerProviderInternal("media",downloads);
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().remove(ReceiveFolder.TREE).commit();
        try(var c=screen()){
            FilesActivity a=c.get();await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady"));rename(a,"SECOND.TXT");
            await(()->org.robolectric.shadows.ShadowToast.getTextOfLatestToast()!=null && org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("Операция не выполнена"));assertEquals(0,downloads.updates);
            rename(a,"renamed.txt");await(()->downloads.updates==1);assertEquals("renamed.txt",downloads.names.get(1L));assertEquals("second.txt",downloads.names.get(2L));
        }
    }
}
