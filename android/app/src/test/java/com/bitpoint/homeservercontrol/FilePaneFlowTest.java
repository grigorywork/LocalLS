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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FilePaneFlowTest {
    public static class LoadingDocuments extends LastModIntegrationTest.Documents {
        volatile boolean loading,partial;volatile String failure;volatile int deletes;
        volatile String heldChildListing;
        final CountDownLatch childListingEntered=new CountDownLatch(1),releaseChildListing=new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger listings=new java.util.concurrent.atomic.AtomicInteger();
        @Override public Cursor queryChildDocuments(String id,String[] columns,String sort)throws FileNotFoundException{
            listings.incrementAndGet();
            if(id.equals(heldChildListing)){
                childListingEntered.countDown();
                try{if(!releaseChildListing.await(8,TimeUnit.SECONDS))throw new FileNotFoundException("Controlled listing was not released");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new FileNotFoundException("Controlled listing interrupted");}
            }
            if(!loading && failure==null)return super.queryChildDocuments(id,columns,sort);
            Cursor data=partial?super.queryChildDocuments(id,columns,sort):cursor(columns,Collections.emptyList());return new CursorWrapper(data){@Override public Bundle getExtras(){Bundle extras=new Bundle();extras.putBoolean(DocumentsContract.EXTRA_LOADING,loading);if(failure!=null)extras.putString(DocumentsContract.EXTRA_ERROR,failure);return extras;}};
        }
        @Override public void deleteDocument(String id)throws FileNotFoundException{
            deletes++;File target=file(id);try(var paths=Files.walk(target.toPath())){for(java.nio.file.Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}catch(IOException error){throw new FileNotFoundException();}
        }
    }
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
            TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());
            ArrayList<Bundle> sources=job.getParcelableArrayListExtra("sources");assertEquals(1,sources.size());assertEquals(DocumentFiles.root(phoneTree).toString(),sources.get(0).getString("path"));
            assertEquals("share",job.getBundleExtra("destination").getString("kind"));assertEquals(autoTree.toString(),ReceiveFolder.selected(app));
        }
    }
    @Test public void preflightConfirmationRemainsBoundedWhenUiContinuouslyPostsMessages()throws Exception{
        android.os.Handler handler=new android.os.Handler(Looper.getMainLooper());Runnable[] recurring=new Runnable[1];recurring[0]=()->handler.post(recurring[0]);
        try(var screen=screen()){
            FilesActivity a=screen.get();ReflectionHelpers.callInstanceMethod(a,"pickTree",ReflectionHelpers.ClassParameter.from(int.class,504));
            a.onActivityResult(504,Activity.RESULT_OK,new Intent().setData(phoneTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            handler.post(recurring[0]);try{TransferDialogDriver.confirm(app);}finally{handler.removeCallbacks(recurring[0]);}
            Intent job=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());
            assertEquals(DocumentFiles.root(phoneTree).toString(),job.<Bundle>getParcelableArrayListExtra("sources").get(0).getString("path"));assertEquals(autoTree.toString(),ReceiveFolder.selected(app));
        }finally{handler.removeCallbacks(recurring[0]);}
    }
    private LoadingDocuments loadingProvider(){
        LoadingDocuments replacement=new LoadingDocuments();replacement.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="loading.documents."+UUID.randomUUID().toString().replace("-","");info.name=LoadingDocuments.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;replacement.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,replacement);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);android.content.pm.ResolveInfo resolved=new android.content.pm.ResolveInfo();resolved.providerInfo=info;Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(DocumentsContract.PROVIDER_INTERFACE),resolved);phoneTree=DocumentsContract.buildTreeDocumentUri(info.authority,"root/Phone");return replacement;
    }
    @Test public void loadingDocumentCursorIsNotAConfirmedEmptyFolder()throws Exception{
        LoadingDocuments replacement=loadingProvider();replacement.loading=true;
        try{DocumentFiles.children(app,DocumentFiles.root(phoneTree));fail("Loading cursor was accepted as an empty folder");}catch(IOException expected){}
    }
    @Test public void providerErrorCursorIsNotAConfirmedEmptyFolder()throws Exception{
        LoadingDocuments replacement=loadingProvider();replacement.failure="private-provider-details";
        try{DocumentFiles.children(app,DocumentFiles.root(phoneTree));fail("Provider error was accepted as an empty folder");}catch(IOException expected){assertFalse(expected.getMessage().contains("private-provider-details"));}
    }
    @Test public void deleteCannotTreatLoadingCloudFolderAsEmpty()throws Exception{
        LoadingDocuments replacement=loadingProvider();
        File child=new File(provider.root,"Phone/Inside/keep.bin");
        try(var c=screen()){
            FilesActivity a=c.get();
            choose(a);
            await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady"));
            FileNodes.Node node=new FileNodes.Node("doc",DocumentsContract.buildDocumentUriUsingTree(phoneTree,"root/Phone/Inside").toString(),"Inside",true,0);
            assertTrue("Controlled provider must be recognised as a DocumentsProvider",DocumentsContract.isDocumentUri(a,Uri.parse(node.path)));
            ReflectionHelpers.callInstanceMethod(a,"remove",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,node),ReflectionHelpers.ClassParameter.from(boolean.class,true));
            AlertDialog control=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            control.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            // A marker submitted to the two-worker browse pool can finish before deletion.
            await(()->replacement.deletes==1 && !child.getParentFile().exists());
            assertEquals("Control delete of a fully listed empty folder must reach provider",1,replacement.deletes);
            assertFalse(child.getParentFile().exists());
            assertTrue(child.getParentFile().mkdir());
            Files.write(child.toPath(),new byte[]{7});
            replacement.loading=true;
            replacement.heldChildListing="root/Phone/Inside";
            int queried=replacement.listings.get();
            org.robolectric.shadows.ShadowToast.reset();
            try{
                ReflectionHelpers.callInstanceMethod(a,"remove",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,node),ReflectionHelpers.ClassParameter.from(boolean.class,true));
                AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertTrue("Removal did not enter the target folder listing",replacement.childListingEntered.await(5,TimeUnit.SECONDS));
                assertEquals("Delete reached the provider before its listing completed",1,replacement.deletes);
                replacement.releaseChildListing.countDown();
                // The error callback is posted after the real operation rejects EXTRA_LOADING.
                String rejected="Операция не выполнена. Дождись загрузки папки и проверь доступ, имя и наличие файлов.";
                await(()->replacement.deletes>1 || org.robolectric.shadows.ShadowToast.showedToast(rejected));
                assertTrue("Removal did not query the controlled provider",replacement.listings.get()>queried);
                assertEquals("Delete was delegated to a cloud provider without a complete empty listing",1,replacement.deletes);
                assertArrayEquals(new byte[]{7},Files.readAllBytes(child.toPath()));
                assertTrue("Loading folder rejection was not reported",org.robolectric.shadows.ShadowToast.showedToast(rejected));
            }finally{replacement.releaseChildListing.countDown();}
        }
    }
    @Test public void nullDownloadsCursorIsAnAccessFailureInsteadOfEmptySuccess()throws Exception{
        ContentProvider failed=new Downloads(){@Override public Cursor query(Uri uri,String[] columns,String where,String[] args,String order){return null;}};ProviderInfo info=new ProviderInfo();info.authority="media";failed.attachInfo(app,info);ShadowContentResolver.registerProviderInternal("media",failed);
        try{FileNodes.children(app,FileNodes.downloadsRoot());fail("Failed MediaStore query appeared as an empty directory");}catch(IOException expected){}
    }
    @Test public void partialLoadingCursorIsRejectedUntilProviderFinishes()throws Exception{
        LoadingDocuments replacement=loadingProvider();replacement.loading=true;replacement.partial=true;
        try{DocumentFiles.children(app,DocumentFiles.root(phoneTree));fail("Partial directory listing was accepted as complete");}catch(IOException expected){}
        replacement.loading=false;assertEquals(2,DocumentFiles.children(app,DocumentFiles.root(phoneTree)).size());
    }
    @Test public void phonePaneRecoversAfterLoadingProviderFinishes()throws Exception{
        LoadingDocuments replacement=loadingProvider();replacement.loading=true;
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);java.util.concurrent.ExecutorService worker=ReflectionHelpers.getField(a,"browse");worker.submit(()->{}).get(5,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse("Loading phone pane was marked ready",(boolean)ReflectionHelpers.getField(a,"phoneReady"));
            replacement.loading=false;ReflectionHelpers.callInstanceMethod(a,"refreshPhone");await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady"));assertEquals(2,((ListView)a.findViewById(R.id.phoneFileList)).getCount());
        }
    }
    @Test public void readOnlyFolderCannotReplacePhoneDestinationButCanBeImported()throws Exception{
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);String chosen=current(a).path;
            a.onActivityResult(505,Activity.RESULT_OK,new Intent().setData(autoTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));assertEquals(chosen,current(a).path);assertEquals(phoneTree.toString(),app.getSharedPreferences(EmbeddedServerService.PREFS,0).getString(FileNodes.PHONE_TREE,""));assertEquals(autoTree.toString(),ReceiveFolder.selected(app));
            ReflectionHelpers.callInstanceMethod(a,"pickTree",ReflectionHelpers.ClassParameter.from(int.class,504));a.onActivityResult(504,Activity.RESULT_OK,new Intent().setData(autoTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());assertEquals(DocumentFiles.root(autoTree).toString(),job.<Bundle>getParcelableArrayListExtra("sources").get(0).getString("path"));
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
    @Test public void nonEmptyPhoneFolderIsNotDeletedAndEmptyFolderCanBeRemoved()throws Exception{
        File child=new File(provider.root,"Phone/Inside/keep.bin");Files.write(child.toPath(),new byte[]{7});
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady"));FileNodes.Node node=new FileNodes.Node("doc",DocumentsContract.buildDocumentUriUsingTree(phoneTree,"root/Phone/Inside").toString(),"Inside",true,0);
            ReflectionHelpers.callInstanceMethod(a,"remove",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,node),ReflectionHelpers.ClassParameter.from(boolean.class,true));AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(()->org.robolectric.shadows.ShadowToast.getTextOfLatestToast()!=null && org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("Операция не выполнена"));assertTrue(child.isFile());assertTrue(child.delete());
            ReflectionHelpers.callInstanceMethod(a,"remove",ReflectionHelpers.ClassParameter.from(FileNodes.Node.class,node),ReflectionHelpers.ClassParameter.from(boolean.class,true));dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await(()->!child.getParentFile().exists());assertTrue(new File(provider.root,"Phone/file.bin").isFile());
        }
    }
    @Test public void selectAllQueuesCurrentPhoneItemsForCurrentOwnServerFolder()throws Exception{
        try(var c=screen()){
            FilesActivity a=c.get();choose(a);await(()->(boolean)ReflectionHelpers.getField(a,"phoneReady") && (boolean)ReflectionHelpers.getField(a,"remoteReady"));
            ReflectionHelpers.callInstanceMethod(a,"phoneMenu");AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();menu.getListView().performItemClick(null,1,1);
            ReflectionHelpers.callInstanceMethod(a,"sendSelection");TransferDialogDriver.confirm(app);Intent job=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(job);assertEquals(TransferForegroundService.IMPORT,job.getAction());assertEquals(2,job.getParcelableArrayListExtra("sources").size());assertEquals("share",job.getBundleExtra("destination").getString("kind"));assertEquals("",job.getBundleExtra("destination").getString("path"));
        }
    }

}
