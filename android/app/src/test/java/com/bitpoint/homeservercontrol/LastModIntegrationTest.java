package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class LastModIntegrationTest {
    static final String AUTHORITY="mod.documents";
    public static class Documents extends DocumentsProvider {
        File root;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);boolean hold,deny;String deniedName;
        @Override public boolean onCreate(){return true;}
        File file(String id)throws FileNotFoundException{try{File f=new File(root,id.equals("root")?"":id.substring(5)).getCanonicalFile();if(!f.toPath().startsWith(root.getCanonicalFile().toPath()))throw new IOException();return f;}catch(Exception e){throw new FileNotFoundException();}}
        String id(File f){String p=root.toURI().relativize(f.toURI()).getPath();if(p.endsWith("/"))p=p.substring(0,p.length()-1);return p.isEmpty()?"root":"root/"+p;}
        Cursor cursor(String[] columns,List<File> files){if(columns==null)columns=new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_FLAGS};MatrixCursor out=new MatrixCursor(columns);
            for(File f:files){MatrixCursor.RowBuilder row=out.newRow();for(String col:columns){switch(col){case "document_id":row.add(id(f));break;case "_display_name":row.add(f.equals(root)?"Документы":f.getName());break;case "mime_type":row.add(f.isDirectory()?DocumentFiles.DIRECTORY:"application/octet-stream");break;case "_size":row.add(f.length());break;case "flags":row.add(DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);break;default:row.add(null);}}}return out;}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});}
        @Override public Cursor queryDocument(String id,String[] columns)throws FileNotFoundException{return cursor(columns,Collections.singletonList(file(id)));}
        @Override public Cursor queryChildDocuments(String id,String[] columns,String sort)throws FileNotFoundException{File[] files=file(id).listFiles();return cursor(columns,files==null?Collections.emptyList():Arrays.asList(files));}
        @Override public boolean isChildDocument(String parent,String child){return child.startsWith(parent+"/");}
        @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{
            File folder=file(parent),target=new File(folder,name);for(int n=1;target.exists();n++)target=new File(folder,name+" ("+n+")");
            try{if(DocumentFiles.DIRECTORY.equals(mime)){if(!target.mkdir())throw new IOException();}else if(!target.createNewFile())throw new IOException();return id(target);}catch(IOException e){throw new FileNotFoundException();}}
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            if((deny || (deniedName!=null && file(id).getName().equals(deniedName))) && mode.contains("w"))throw new SecurityException("write denied");
            if(hold && mode.contains("w")){entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new FileNotFoundException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException();}}
            return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));}
        @Override public void deleteDocument(String id)throws FileNotFoundException{if(!file(id).delete())throw new FileNotFoundException();}
        @Override public String renameDocument(String id,String name)throws FileNotFoundException{File f=file(id),next=new File(f.getParentFile(),name);if(next.exists()||!f.renameTo(next))throw new FileNotFoundException();return id(next);}
    }
    private Context app;private Documents provider;private Uri tree;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();app.deleteDatabase("completed-uploads.db");
        AutoReceive.error=false;AutoReceive.active=false;AutoReceive.diagnostic="";
        provider=new Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"documents-").toFile();
        ProviderInfo info=new ProviderInfo();info.authority=AUTHORITY;info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY,provider);tree=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root");
    }
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}assertTrue("Timed out: "+AutoReceive.diagnostic+" / "+TransferForegroundService.state.diagnostic,condition.getAsBoolean());}
    @Test public void folderCopyUsesServiceAfterScreenClosesAndPreservesEmptyFoldersAndCollisions()throws Exception{
        File share=EmbeddedServerService.share(app);File folder=new File(share,"tree-"+System.nanoTime());assertTrue(new File(folder,"empty").mkdirs());File source=new File(folder,"файл.bin");byte[] bytes=new byte[4*1024*1024+17];new Random(59).nextBytes(bytes);Files.write(source.toPath(),bytes);
        assertTrue(new File(provider.root,folder.getName()).mkdir());provider.hold=true;
        var activity=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create();
        var controller=Robolectric.buildService(TransferForegroundService.class).create();try{
            Intent job=new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.SAVE);
            ArrayList<Bundle> sources=new ArrayList<>();sources.add(new FileNodes.Node("share",folder.getName(),folder.getName(),true,0).bundle());job.putParcelableArrayListExtra("sources",sources);
            job.putExtra("destination",new FileNodes.Node("doc",DocumentFiles.root(tree).toString(),"Документы",true,0).bundle());
            controller.get().onStartCommand(job,0,1);boolean opened=provider.entered.await(5,TimeUnit.SECONDS);assertTrue(TransferForegroundService.state.diagnostic,opened);activity.destroy();provider.release.countDown();
            await(()->!TransferForegroundService.state.active);assertFalse(TransferForegroundService.state.text,TransferForegroundService.state.error);
            File target=new File(provider.root,folder.getName()+" (1)");assertTrue(new File(target,"empty").isDirectory());assertArrayEquals(bytes,Files.readAllBytes(new File(target,"файл.bin").toPath()));assertArrayEquals(bytes,Files.readAllBytes(source.toPath()));
        }finally{provider.release.countDown();controller.destroy();}
    }
    @Test public void phoneSelectionAndServerAutomaticSaveUseIndependentFolders()throws Exception{
        assertTrue(new File(provider.root,"Phone").mkdir());assertTrue(new File(provider.root,"Auto").mkdir());
        Uri phoneTree=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root/Phone"),autoTree=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root/Auto");
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,autoTree.toString()).commit();
        try(var screen=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create()){
            screen.get().onActivityResult(505,android.app.Activity.RESULT_OK,new Intent().setData(phoneTree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
        }
        File root=EmbeddedServerService.share(app);root.mkdirs();String name="independent-"+System.nanoTime()+".bin";
        byte[] bytes={1,4,7,9};Files.write(new File(root,name).toPath(),bytes);
        var service=Robolectric.buildService(TransferForegroundService.class).create();
        try(AutoReceive automatic=new AutoReceive(app)){
            automatic.received(name);ArrayList<Bundle> sources=new ArrayList<>();sources.add(new FileNodes.Node("share",name,name,false,bytes.length).bundle());
            service.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.SAVE).putParcelableArrayListExtra("sources",sources),0,1);
            File manual=new File(provider.root,"Phone/"+name),auto=new File(provider.root,"Auto/"+name);
            await(()->manual.length()==bytes.length && auto.length()==bytes.length && !TransferForegroundService.state.active && !AutoReceive.active);
            assertArrayEquals(bytes,Files.readAllBytes(manual.toPath()));assertArrayEquals(bytes,Files.readAllBytes(auto.toPath()));assertArrayEquals(bytes,Files.readAllBytes(new File(root,name).toPath()));
        }finally{service.destroy();}
    }
    @Test public void completedUploadAutomaticallySavesWithoutServerUiAndDeduplicatesEvents()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,tree.toString()).commit();
        String path="auto-"+System.nanoTime()+"/фото.bin";File source=new File(EmbeddedServerService.share(app),path);assertTrue(source.getParentFile().mkdirs());byte[] bytes={1,3,5,7};Files.write(source.toPath(),bytes);
        try(AutoReceive receiver=new AutoReceive(app)){
            receiver.received(path);await(()->{try(AutoReceive.Queue q=new AutoReceive.Queue(app)){return q.next()==null && !AutoReceive.active && new File(provider.root,path).isFile();}});assertFalse(AutoReceive.diagnostic,AutoReceive.error);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,path).toPath()));
            receiver.received(path);Thread.sleep(100);assertEquals(1,source.getParentFile().listFiles().length);assertEquals(1,new File(provider.root,path).getParentFile().listFiles().length);
        }
        assertArrayEquals(bytes,Files.readAllBytes(source.toPath()));
    }
    @Test public void deniedAutomaticDestinationLeavesOriginalAndRetriesWithoutReauthorizingClient()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,tree.toString()).commit();
        String path="retry-"+System.nanoTime()+".bin";File source=new File(EmbeddedServerService.share(app),path);source.getParentFile().mkdirs();Files.write(source.toPath(),new byte[]{2,4,6});provider.deny=true;
        try(AutoReceive receiver=new AutoReceive(app)){
            receiver.received(path);await(()->AutoReceive.error);assertArrayEquals(new byte[]{2,4,6},Files.readAllBytes(source.toPath()));
            try(AutoReceive.Queue q=new AutoReceive.Queue(app)){assertNotNull(q.next());}
            assertFalse(new File(provider.root,path).exists());provider.deny=false;receiver.retry();
            await(()->{try(AutoReceive.Queue q=new AutoReceive.Queue(app)){return q.next()==null && !AutoReceive.active;}});
            assertArrayEquals(new byte[]{2,4,6},Files.readAllBytes(new File(provider.root,path).toPath()));
        }
    }
    @Test public void missingPendingSourceDoesNotBlockNextFile()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,tree.toString()).commit();
        File root=EmbeddedServerService.share(app);root.mkdirs();String good="after-missing-"+System.nanoTime()+".bin";Files.write(new File(root,good).toPath(),new byte[]{7,8,9});
        try(AutoReceive.Queue q=new AutoReceive.Queue(app)){q.offer("deleted-pending.bin",12,1);File f=new File(root,good);q.offer(good,f.length(),f.lastModified());}
        try(AutoReceive receiver=new AutoReceive(app)){
            await(()->new File(provider.root,good).length()==3 && !AutoReceive.active);
            assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(new File(provider.root,good).toPath()));
            try(AutoReceive.Queue q=new AutoReceive.Queue(app)){assertNull(q.next());}
        }
    }
    @Test public void oneDeniedFileDoesNotBlockNextAndOnlyFailureIsRetried()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,tree.toString()).commit();
        File root=EmbeddedServerService.share(app);root.mkdirs();String bad="denied-"+System.nanoTime()+".bin",good="after-denied-"+System.nanoTime()+".bin";
        Files.write(new File(root,bad).toPath(),new byte[]{1});Files.write(new File(root,good).toPath(),new byte[]{2});provider.deniedName=bad;
        try(AutoReceive.Queue q=new AutoReceive.Queue(app)){for(String path:new String[]{bad,good}){File f=new File(root,path);q.offer(path,f.length(),f.lastModified());}}
        try(AutoReceive receiver=new AutoReceive(app)){
            await(()->new File(provider.root,good).length()==1 && !AutoReceive.active);assertTrue(AutoReceive.error);
            try(AutoReceive.Queue q=new AutoReceive.Queue(app)){assertEquals(bad,q.next().path);}
            provider.deniedName=null;receiver.retry();await(()->{try(AutoReceive.Queue q=new AutoReceive.Queue(app)){return q.next()==null && !AutoReceive.active;}});
            assertFalse(AutoReceive.diagnostic,AutoReceive.error);assertEquals(2,provider.root.listFiles().length);
            assertArrayEquals(new byte[]{1},Files.readAllBytes(new File(provider.root,bad).toPath()));assertArrayEquals(new byte[]{2},Files.readAllBytes(new File(provider.root,good).toPath()));
        }
    }
    @Test public void discardedPathCanBeReceivedAgainWithSameMetadata(){
        try(AutoReceive.Queue q=new AutoReceive.Queue(app)){
            q.offer("recreated.bin",5,100);AutoReceive.Queue.Item old=q.next();q.discard(old);assertNull(q.next());
            q.offer("recreated.bin",5,100);AutoReceive.Queue.Item fresh=q.next();assertNotNull(fresh);assertNotEquals(old.id,fresh.id);
            q.done(old);assertNotNull(q.next());q.done(fresh);assertNull(q.next());
        }
    }
    @Test public void incomingBurstWhileCopyIsBusyIsSavedExactlyOnceWithoutUi()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,tree.toString()).commit();
        File root=EmbeddedServerService.share(app);root.mkdirs();provider.hold=true;
        try(AutoReceive receiver=new AutoReceive(app)){
            for(int n=0;n<16;n++){
                String name="burst-"+n+".bin";Files.write(new File(root,name).toPath(),new byte[]{(byte)n});receiver.received(name);
                if(n==0)assertTrue(provider.entered.await(5,TimeUnit.SECONDS));
            }
            provider.release.countDown();await(()->{try(AutoReceive.Queue q=new AutoReceive.Queue(app)){return q.next()==null && !AutoReceive.active && provider.root.listFiles().length==16;}});
            for(int n=0;n<16;n++)assertArrayEquals(new byte[]{(byte)n},Files.readAllBytes(new File(provider.root,"burst-"+n+".bin").toPath()));
        }finally{provider.release.countDown();}
    }
    @Test public void existingVersion0116QueueOpensWithoutReset(){
        File path=app.getDatabasePath("completed-uploads.db");path.getParentFile().mkdirs();
        try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null)){
            db.execSQL("CREATE TABLE uploads(path TEXT PRIMARY KEY, size INTEGER NOT NULL, modified INTEGER NOT NULL, saved INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO uploads(path,size,modified,saved) VALUES('old-pending.bin',123,456,0)");db.setVersion(1);
        }
        try(AutoReceive.Queue q=new AutoReceive.Queue(app)){
            AutoReceive.Queue.Item item=q.next();assertEquals("old-pending.bin",item.path);assertEquals(123,item.size);assertEquals(456,item.modified);q.done(item);assertNull(q.next());
        }
    }
    @Test public void durableQueueDoesNotLoseNewVersionWhilePreviousCopyFinishes(){try(AutoReceive.Queue q=new AutoReceive.Queue(app)){
        q.offer("a.bin",3,1);AutoReceive.Queue.Item old=q.next();q.offer("a.bin",4,2);q.done(old);assertNotNull(q.next());q.done(q.next());assertNull(q.next());q.offer("a.bin",4,2);assertNull(q.next());}
    }
    @Test public void roleSwitchHidesOnlyRemoteSettingsAndKeepsLocalServer(){
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",true).commit();
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){
            MainActivity a=c.get();assertEquals(View.GONE,a.findViewById(R.id.remoteConnectionCard).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.localServerCard).getVisibility());
            ((android.widget.Switch)a.findViewById(R.id.showClientSwitch)).setChecked(true);assertEquals(View.VISIBLE,a.findViewById(R.id.remoteConnectionCard).getVisibility());
        }
    }
}
