package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class DurableTransferTest {
    @org.robolectric.annotation.Implements(value=TransferSpeedStore.class,isInAndroidSdk=false)
    public static class PausedStart {
        static CountDownLatch entered,release;
        @org.robolectric.annotation.Implementation protected static void begin(Object transfer){entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Start wait timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
    public static class PendingDownloads extends ContentProvider{
        static final class Row {final File file;final ContentValues values;Row(File f,ContentValues v){file=f;values=new ContentValues(v);}}
        final Map<Long,Row> rows=new LinkedHashMap<>();long next;int inserted;File directory;
        @Override public boolean onCreate(){return true;}
        @Override public Uri insert(Uri uri,ContentValues values){try{long id=++next;File file=new File(directory,id+".bin");assertTrue(file.createNewFile());Row row=new Row(file,values);String path=row.values.getAsString("relative_path");if(path!=null&&!path.endsWith("/"))row.values.put("relative_path",path+"/");rows.put(id,row);inserted++;return ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,id);}catch(IOException e){throw new IllegalStateException(e);}}
        @Override public android.database.Cursor query(Uri uri,String[] columns,String where,String[] args,String order){
            if(columns==null)columns=new String[]{"_display_name","_size"};android.database.MatrixCursor out=new android.database.MatrixCursor(columns);Long requested=null;try{requested=ContentUris.parseId(uri);}catch(NumberFormatException ignored){}
            for(var entry:rows.entrySet()){
                Row row=entry.getValue();if(requested!=null&&!requested.equals(entry.getKey()))continue;if(where!=null&&where.contains("is_pending=0")&&row.values.getAsInteger("is_pending")!=0)continue;
                var values=out.newRow();for(String column:columns)values.add("_id".equals(column)?entry.getKey():"_size".equals(column)?row.file.length():row.values.get(column));
            }return out;
        }
        @Override public int update(Uri uri,ContentValues values,String where,String[] args){Row row=rows.get(ContentUris.parseId(uri));if(row==null)return 0;row.values.putAll(values);return 1;}
        @Override public int delete(Uri uri,String where,String[] args){Row row=rows.remove(ContentUris.parseId(uri));if(row==null)return 0;row.file.delete();return 1;}
        @Override public String getType(Uri uri){return "application/octet-stream";}
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{Row row=rows.get(ContentUris.parseId(uri));if(row==null)throw new FileNotFoundException();return ParcelFileDescriptor.open(row.file,ParcelFileDescriptor.parseMode(mode));}
    }
    public static class AppendingPendingDownloads extends PendingDownloads{
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
            if("wt".equals(mode))throw new UnsupportedOperationException("Truncation not supported");
            if("w".equals(mode))return ParcelFileDescriptor.open(rows.get(ContentUris.parseId(uri)).file,ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_APPEND);
            return super.openFile(uri,mode);
        }
    }
    public static class NoSpaceDocuments extends TransferRecoveryTest.Documents{
        @Override public android.database.Cursor queryRoots(String[] columns){android.database.MatrixCursor c=new android.database.MatrixCursor(columns);var row=c.newRow();for(String column:columns)row.add("document_id".equals(column)?"root":"available_bytes".equals(column)?0L:null);return c;}
    }
    public static class AliasingCreateDocuments extends TransferRecoveryTest.Documents{
        String returnedId="root";int deletes;
        @Override public String createDocument(String parent,String mime,String name){return returnedId;}
        @Override public void deleteDocument(String id)throws FileNotFoundException{deletes++;try(var paths=Files.walk(file(id).toPath())){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}catch(IOException e){throw new FileNotFoundException();}}
    }
    public static class AliasingFolderDocuments extends TransferRecoveryTest.Documents{
        int deletes;
        @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{return DocumentFiles.DIRECTORY.equals(mime)?parent:super.createDocument(parent,mime,name);}
        @Override public void deleteDocument(String id)throws FileNotFoundException{deletes++;super.deleteDocument(id);}
    }
    public static class AppendingWriteDocuments extends TransferRecoveryTest.Documents{
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            if("wt".equals(mode))throw new UnsupportedOperationException("Truncation not supported");
            if("w".equals(mode))return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_APPEND);
            return super.openDocument(id,mode,signal);
        }
    }
    public static class UninterruptibleOpenDocuments extends TransferRecoveryTest.Documents{
        final java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();
        final CountDownLatch firstOpened=new CountDownLatch(1),secondOpened=new CountDownLatch(1),allowFirst=new CountDownLatch(1);
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            boolean interrupted=false;
            if(mode.contains("w")){
                if(writes.incrementAndGet()==1){firstOpened.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                    while(allowFirst.getCount()>0&&System.nanoTime()<end)try{allowFirst.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){interrupted=true;}
                    if(allowFirst.getCount()>0)throw new FileNotFoundException("Controlled open timed out");
                }else secondOpened.countDown();
            }
            ParcelFileDescriptor result=super.openDocument(id,mode,signal);if(interrupted)Thread.currentThread().interrupt();return result;
        }
    }
    public static class CorruptingDocuments extends TransferRecoveryTest.Documents{
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            ParcelFileDescriptor original=super.openDocument(id,mode,signal);if(!mode.contains("w"))return original;
            return new ParcelFileDescriptor(original){@Override public void close()throws IOException{super.close();Files.write(file(id).toPath(),new byte[]{6,6,6});}};
        }
    }
    private android.app.Application app;
    private TransferRecoveryTest.Documents provider;
    private Uri tree;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();provider=new TransferRecoveryTest.Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"durable-").toFile();
        ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,provider);tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");
        forgetProcess();
    }
    private File source(String path,byte[] bytes)throws Exception{File root=EmbeddedServerService.share(app);root.mkdirs();File f=new File(root,path);f.getParentFile().mkdirs();Files.write(f.toPath(),bytes);return f;}
    private Intent job(String path,boolean directory,String policy,boolean verify){
        File f=new File(EmbeddedServerService.share(app),path);ArrayList<Bundle> sources=new ArrayList<>();sources.add(new FileNodes.Node("share",path,f.getName(),directory,directory?0:f.length()).bundle());
        return new Intent(app,TransferForegroundService.class).setAction("save").putExtra("request_id",UUID.randomUUID().toString()).putParcelableArrayListExtra("sources",sources)
                .putExtra("destination",new FileNodes.Node("doc",DocumentFiles.root(tree).toString(),"Документы",true,0).bundle()).putExtra("conflict",policy).putExtra("verify",verify);
    }
    private void drained(TransferForegroundService service)throws Exception{((ExecutorService)org.robolectric.util.ReflectionHelpers.getField(service,"worker")).submit(()->{}).get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void forgetProcess(){org.robolectric.util.ReflectionHelpers.setStaticField(TransferForegroundService.class,"lastFailed",null);Set<?> completed=org.robolectric.util.ReflectionHelpers.getStaticField(TransferForegroundService.class,"completedRequests");completed.clear();}
    @Test public void diskJournalRestoresFixedEndpointAndOptionsWithoutCredentials()throws Exception{
        source("a.bin",new byte[]{1,3,7});Intent i=job("a.bin",false,TransferJournal.SKIP,true).putExtra("host","192.168.1.4").putExtra("port",8022).putExtra("user","localls").putExtra("pin","SHA256:test");
        i.putExtra("privateKey","MUST_NOT_PERSIST");TransferJournal first=new TransferJournal(app);assertTrue(first.ensure(i));forgetProcess();
        Intent restored=new TransferJournal(app).request(i.getStringExtra("request_id"));
        assertEquals(i.getStringExtra("host"),restored.getStringExtra("host"));assertEquals(TransferJournal.SKIP,restored.getStringExtra("conflict"));assertTrue(restored.getBooleanExtra("verify",false));assertFalse(restored.hasExtra("privateKey"));
        assertEquals(i.getBundleExtra("destination").getString("path"),restored.getBundleExtra("destination").getString("path"));
        assertFalse(new String(Files.readAllBytes(new File(first.root,i.getStringExtra("request_id")+".json").toPath()),"UTF-8").contains("MUST_NOT_PERSIST"));
    }
    @Test public void interruptedFolderResumesOnlyUnfinishedChildrenAfterProcessStateIsForgotten()throws Exception{
        source("folder/a.bin",new byte[]{1,2});source("folder/b.bin",new byte[]{3,4,5});provider.deniedName="b.bin";
        Intent i=job("folder",true,TransferJournal.KEEP,true);var first=Robolectric.buildService(TransferForegroundService.class).create();
        try{first.get().onStartCommand(i,0,1);drained(first.get());assertTrue(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(new byte[]{1,2},Files.readAllBytes(new File(provider.root,"folder/a.bin").toPath()));assertFalse(new File(provider.root,"folder/b.bin").exists());}finally{first.destroy();}
        forgetProcess();provider.deniedName=null;Intent restored=new TransferJournal(app).request(i.getStringExtra("request_id"));
        var second=Robolectric.buildService(TransferForegroundService.class).create();try{second.get().onStartCommand(restored,0,1);drained(second.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            assertEquals(1,provider.root.listFiles().length);assertEquals(2,new File(provider.root,"folder").listFiles().length);assertArrayEquals(new byte[]{3,4,5},Files.readAllBytes(new File(provider.root,"folder/b.bin").toPath()));
        }finally{second.destroy();}
        forgetProcess();var third=Robolectric.buildService(TransferForegroundService.class).create();try{third.get().onStartCommand(restored,0,1);drained(third.get());assertEquals("Completed request replayed after process restart",1,provider.root.listFiles().length);}finally{third.destroy();}
    }
    @Test public void conflictPoliciesPreserveSkipAndKeepBothAndReplaceOnlyAfterCompleteCopy()throws Exception{
        source("same.bin",new byte[]{1,2,3});Files.write(new File(provider.root,"same.bin").toPath(),new byte[]{9});
        for(String policy:new String[]{TransferJournal.SKIP,TransferJournal.KEEP,TransferJournal.REPLACE}){
            var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(job("same.bin",false,policy,true),0,1);drained(service.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);}finally{service.destroy();}
            if(!TransferJournal.REPLACE.equals(policy))assertArrayEquals(new byte[]{9},Files.readAllBytes(new File(provider.root,"same.bin").toPath()));
        }
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(provider.root,"same.bin").toPath()));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(provider.root,"same (1).bin").toPath()));assertEquals(2,provider.root.listFiles().length);
    }
    @Test public void failedReplacementKeepsOriginalAndCapacityShortageIsRejected()throws Exception{
        source("same.bin",new byte[]{1,2,3});Files.write(new File(provider.root,"same.bin").toPath(),new byte[]{9,8});provider.deny=true;
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(job("same.bin",false,TransferJournal.REPLACE,true),0,1);drained(service.get());assertTrue(TransferForegroundService.state.error);assertArrayEquals(new byte[]{9,8},Files.readAllBytes(new File(provider.root,"same.bin").toPath()));assertEquals(1,provider.root.listFiles().length);}finally{service.destroy();}
        try{TransferCapacity.require(11,10);fail("Shortage allowed");}catch(IOException expected){}TransferCapacity.require(11,-1);
    }
    @Test public void corruptedRetainedCopyCannotBeAppendedOrPublished()throws Exception{
        source("a.bin",new byte[]{1,2,3,4});Intent i=job("a.bin",false,TransferJournal.KEEP,true);TransferJournal journal=new TransferJournal(app);journal.ensure(i);
        String key=TransferJournal.key(new FileNodes.Node("share","a.bin","a.bin",false,4));Files.write(journal.partial(i.getStringExtra("request_id"),key).toPath(),new byte[]{1,9});
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(i,0,1);drained(service.get());assertTrue(TransferForegroundService.state.error);assertEquals(0,provider.root.listFiles().length);assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(new File(EmbeddedServerService.share(app),"a.bin").toPath()));}finally{service.destroy();}
    }
    @Test public void verificationRejectsCorruptedDestinationWithoutReplacingOldFile()throws Exception{
        CorruptingDocuments corrupt=new CorruptingDocuments();corrupt.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;corrupt.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,corrupt);
        source("same.bin",new byte[]{1,2,3});Files.write(new File(provider.root,"same.bin").toPath(),new byte[]{9,8});Intent i=job("same.bin",false,TransferJournal.REPLACE,true);
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(i,0,1);drained(service.get());assertTrue("Corrupt destination was reported as verified",TransferForegroundService.state.error);assertArrayEquals(new byte[]{9,8},Files.readAllBytes(new File(provider.root,"same.bin").toPath()));assertEquals(1,provider.root.listFiles().length);}finally{service.destroy();}
    }
    @Test public void stoppingServiceDuringDestinationOpenKeepsDiskQueueForExplicitResume()throws Exception{
        byte[] bytes=new byte[131073];new Random(64).nextBytes(bytes);source("paused.bin",bytes);Intent i=job("paused.bin",false,TransferJournal.KEEP,true);provider.hold=true;
        var old=Robolectric.buildService(TransferForegroundService.class).create();old.get().onStartCommand(i,0,1);assertTrue(provider.entered.await(5,TimeUnit.SECONDS));ExecutorService executor=org.robolectric.util.ReflectionHelpers.getField(old.get(),"worker");old.destroy();provider.release.countDown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
        forgetProcess();provider.hold=false;TransferForegroundService.restore(app);assertTrue(TransferForegroundService.state.text.contains("незавершённых"));
        Intent restored=new TransferJournal(app).request(i.getStringExtra("request_id"));var next=Robolectric.buildService(TransferForegroundService.class).create();try{next.get().onStartCommand(restored,0,1);drained(next.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"paused.bin").toPath()));assertEquals(1,provider.root.listFiles().length);}finally{next.destroy();}
    }
    @Test public void skipPolicyResumesItsOwnPartlyCopiedFolderInsteadOfSkippingTheRest()throws Exception{
        source("partial-tree/a.bin",new byte[]{1,2});source("partial-tree/b.bin",new byte[]{3,4,5});provider.deniedName="b.bin";
        Intent i=job("partial-tree",true,TransferJournal.SKIP,true);var first=Robolectric.buildService(TransferForegroundService.class).create();
        try{first.get().onStartCommand(i,0,1);drained(first.get());assertTrue(TransferForegroundService.state.error);assertTrue(new File(provider.root,"partial-tree/a.bin").isFile());assertFalse(new File(provider.root,"partial-tree/b.bin").exists());}finally{first.destroy();}
        forgetProcess();provider.deniedName=null;var next=Robolectric.buildService(TransferForegroundService.class).create();
        try{next.get().onStartCommand(new TransferJournal(app).request(i.getStringExtra("request_id")),0,1);drained(next.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            assertTrue("Retry silently skipped unfinished children of its own folder",new File(provider.root,"partial-tree/b.bin").isFile());assertArrayEquals(new byte[]{3,4,5},Files.readAllBytes(new File(provider.root,"partial-tree/b.bin").toPath()));assertEquals(2,new File(provider.root,"partial-tree").listFiles().length);
        }finally{next.destroy();}
    }
    private void hardDeathPartial(String policy)throws Exception{
        byte[] bytes={1,2,3,4};source("hard-death.bin",bytes);Intent i=job("hard-death.bin",false,policy,true);TransferJournal journal=new TransferJournal(app);journal.ensure(i);
        Uri made=DocumentFiles.create(app,DocumentFiles.root(tree),"hard-death.bin","application/octet-stream");Files.write(new File(provider.root,"hard-death.bin").toPath(),new byte[]{1,2});
        String key=TransferJournal.key(new FileNodes.Node("share","hard-death.bin","hard-death.bin",false,4));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","hard-death.bin").put("madeUri",made.toString());
        journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","hard-death.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        forgetProcess();var resumed=Robolectric.buildService(TransferForegroundService.class).create();
        try{resumed.get().onStartCommand(journal.request(i.getStringExtra("request_id")),0,1);drained(resumed.get());assertFalse("A journal-owned partial could not resume: "+TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            assertArrayEquals("Incomplete output was treated as a completed/skipped file",bytes,Files.readAllBytes(new File(provider.root,"hard-death.bin").toPath()));assertEquals(1,provider.root.listFiles().length);
        }finally{resumed.destroy();}
    }
    @Test public void hardDeathDuringSafWriteCanResumeKeepBothWithoutCreatingDuplicate()throws Exception{hardDeathPartial(TransferJournal.KEEP);}
    @Test public void hardDeathDuringSafWriteCannotBeReportedAsSuccessfulSkip()throws Exception{hardDeathPartial(TransferJournal.SKIP);}
    @Test public void hardDeathAfterSafBackupRenameRecoversOwnedOutputAndRemovesOnlyItsBackup()throws Exception{
        byte[] bytes={1,2,3,4};source("replace-death.bin",bytes);Files.write(new File(provider.root,"replace-death.bin").toPath(),new byte[]{9,8,7});
        Intent i=job("replace-death.bin",false,TransferJournal.REPLACE,true);TransferJournal journal=new TransferJournal(app);journal.ensure(i);FileNodes.Node prior=TransferOutput.find(app,FileNodes.Node.from(i.getBundleExtra("destination")),"replace-death.bin");
        String temp=".import-"+UUID.randomUUID()+".part",backup=".import-"+UUID.randomUUID()+".previous";Uri made=DocumentFiles.create(app,DocumentFiles.root(tree),temp,"application/octet-stream");Files.write(new File(provider.root,temp).toPath(),new byte[]{1,2});
        assertNotNull(DocumentsContract.renameDocument(app.getContentResolver(),Uri.parse(prior.path),backup));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","replace-death.bin").put("madeUri",made.toString()).put("prior",TransferJournal.node(prior.bundle())).put("backupName",backup);
        String key=TransferJournal.key(new FileNodes.Node("share","replace-death.bin","replace-death.bin",false,4));journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","replace-death.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        forgetProcess();var resumed=Robolectric.buildService(TransferForegroundService.class).create();try{resumed.get().onStartCommand(journal.request(i.getStringExtra("request_id")),0,1);drained(resumed.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"replace-death.bin").toPath()));assertEquals("Crash left untracked temporary/backup files",1,provider.root.listFiles().length);}finally{resumed.destroy();}
    }
    private PendingDownloads downloads()throws Exception{return downloads(new PendingDownloads());}
    private PendingDownloads downloads(PendingDownloads downloads)throws Exception{
        downloads.directory=Files.createTempDirectory(app.getCacheDir().toPath(),"pending-media-").toFile();ProviderInfo info=new ProviderInfo();info.authority="media";info.applicationInfo=app.getApplicationInfo();downloads.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",downloads);android.os.Environment.getExternalStorageDirectory().mkdirs();return downloads;
    }
    private Uri mediaRow(PendingDownloads downloads,String name,byte[] bytes,int pending)throws Exception{
        ContentValues values=new ContentValues();values.put("_display_name",name);values.put("relative_path",android.os.Environment.DIRECTORY_DOWNLOADS+"/LocalLS/");values.put("is_pending",pending);Uri uri=downloads.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);Files.write(downloads.rows.get(ContentUris.parseId(uri)).file.toPath(),bytes);return uri;
    }
    @Test public void hardDeathResumesItsPendingMediaStoreRowWithoutInsertingAnother()throws Exception{
        byte[] bytes={1,2,3,4};source("media.bin",bytes);PendingDownloads downloads=downloads();Uri row=mediaRow(downloads,"media.bin",new byte[]{1,2},1);
        Intent i=job("media.bin",false,TransferJournal.KEEP,true).putExtra("destination",FileNodes.downloadsRoot().bundle());TransferJournal journal=new TransferJournal(app);journal.ensure(i);String key=TransferJournal.key(new FileNodes.Node("share","media.bin","media.bin",false,4));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","media.bin").put("mediaUri",row.toString());journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","media.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(i,0,1);drained(service.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertEquals(1,downloads.inserted);assertEquals(1,downloads.rows.size());assertEquals(Integer.valueOf(0),downloads.rows.get(ContentUris.parseId(row)).values.getAsInteger("is_pending"));assertArrayEquals(bytes,Files.readAllBytes(downloads.rows.get(ContentUris.parseId(row)).file.toPath()));}finally{service.destroy();}
    }
    @Test public void completedPublicationIsRecoveredBeforeCapacityChecksEvenWhenStorageIsFull()throws Exception{
        NoSpaceDocuments full=new NoSpaceDocuments();full.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;full.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,full);
        byte[] bytes={1,2,3,4};source("already-published.bin",bytes);Uri made=DocumentFiles.create(app,DocumentFiles.root(tree),"already-published.bin","application/octet-stream");Files.write(new File(provider.root,"already-published.bin").toPath(),bytes);full.deny=true;
        Intent i=job("already-published.bin",false,TransferJournal.KEEP,true);TransferJournal journal=new TransferJournal(app);journal.ensure(i);String key=TransferJournal.key(new FileNodes.Node("share","already-published.bin","already-published.bin",false,4));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","already-published.bin").put("madeUri",made.toString());journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","already-published.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(i,0,1);drained(service.get());assertFalse("Published file was incorrectly blocked by zero free space: "+TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertEquals(1,provider.root.listFiles().length);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"already-published.bin").toPath()));}finally{service.destroy();}
    }
    @Test public void providerReturningParentInsteadOfNewDocumentCannotCauseRecursiveDeletion()throws Exception{
        AliasingCreateDocuments alias=new AliasingCreateDocuments();alias.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;alias.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,alias);
        byte[] original={9,8,7};Files.write(new File(provider.root,"important.bin").toPath(),original);source("new.bin",new byte[]{1,2,3});
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(job("new.bin",false,TransferJournal.KEEP,true),0,1);drained(service.get());assertTrue("Invalid creation response was accepted",TransferForegroundService.state.error);assertEquals("Cleanup tried deleting the user's directory",0,alias.deletes);assertArrayEquals(original,Files.readAllBytes(new File(provider.root,"important.bin").toPath()));}finally{service.destroy();}
    }
    @Test public void filesystemReplacementResumesTrackedTemporaryAndBackupAfterHardDeath()throws Exception{
        byte[] bytes={1,2,3,4};source("local-replace.bin",bytes);File directory=new File(EmbeddedServerService.share(app),"destination");assertTrue(directory.mkdir());
        String temporary=".import-"+UUID.randomUUID()+".part",backup=".import-"+UUID.randomUUID()+".previous";Files.write(new File(directory,temporary).toPath(),new byte[]{1,2});Files.write(new File(directory,backup).toPath(),new byte[]{9,8,7});
        FileNodes.Node parent=new FileNodes.Node("share","destination","destination",true,0),prior=new FileNodes.Node("share","destination/local-replace.bin","local-replace.bin",false,3);
        Intent i=job("local-replace.bin",false,TransferJournal.REPLACE,true).putExtra("destination",parent.bundle());TransferJournal journal=new TransferJournal(app);journal.ensure(i);String key=TransferJournal.key(new FileNodes.Node("share","local-replace.bin","local-replace.bin",false,4));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(parent.bundle())).put("name","local-replace.bin").put("prior",TransferJournal.node(prior.bundle())).put("temporaryName",temporary).put("backupName",backup);
        journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","local-replace.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{service.get().onStartCommand(i,0,1);drained(service.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(directory,"local-replace.bin").toPath()));assertEquals(1,directory.listFiles().length);assertArrayEquals(bytes,Files.readAllBytes(new File(EmbeddedServerService.share(app),"local-replace.bin").toPath()));}finally{service.destroy();}
    }
    @Test public void resumedSafReplacementCannotMoveAUserDirectoryIntoItsBackup()throws Exception{replacementDirectoryGuard(false);}
    @Test @Config(sdk=24) public void localCopyAndReplacementRemainSupportedOnAndroid7()throws Exception{
        byte[] bytes={1,2,3};source("android7-local.bin",bytes);File directory=new File(EmbeddedServerService.share(app),"android7-target");assertTrue(directory.mkdir());
        FileNodes.Node parent=new FileNodes.Node("share",directory.getName(),directory.getName(),true,0);
        for(String policy:new String[]{TransferJournal.KEEP,TransferJournal.REPLACE}){
            var service=Robolectric.buildService(TransferForegroundService.class).create();try{
                service.get().onStartCommand(job("android7-local.bin",false,policy,true).putExtra("destination",parent.bundle()),0,1);drained(service.get());
                assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(directory,"android7-local.bin").toPath()));assertEquals(1,directory.listFiles().length);
            }finally{service.destroy();}
            if(TransferJournal.KEEP.equals(policy))Files.write(new File(directory,"android7-local.bin").toPath(),new byte[]{9});
        }
    }
    @Test @Config(shadows=PausedStart.class) public void cancellationBeforeJobStartsCannotBeOverwrittenByRunningStatus()throws Exception{
        PausedStart.entered=new CountDownLatch(1);PausedStart.release=new CountDownLatch(1);source("cancel-start.bin",new byte[]{1,2});Intent i=job("cancel-start.bin",false,TransferJournal.KEEP,true);
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{
            service.get().onStartCommand(i,0,1);assertTrue(PausedStart.entered.await(5,TimeUnit.SECONDS));service.get().onStartCommand(new Intent(app,TransferForegroundService.class).setAction(TransferForegroundService.CANCEL),0,2);PausedStart.release.countDown();drained(service.get());
            org.json.JSONObject entry=new TransferJournal(app).list().stream().filter(j->i.getStringExtra("request_id").equals(j.optString("id"))).findFirst().orElseThrow();
            assertEquals("Cancelled request was presented as resumable after restart","cancelled",entry.getString("status"));assertFalse(new File(provider.root,"cancel-start.bin").exists());
        }finally{PausedStart.release.countDown();service.destroy();}
    }
    @Test public void providerReturningParentForNewFolderCannotRedirectReplacementIntoParent()throws Exception{
        AliasingFolderDocuments alias=new AliasingFolderDocuments();alias.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;alias.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,alias);
        byte[] original={9,8,7};Files.write(new File(provider.root,"important.bin").toPath(),original);source("new-folder/important.bin",new byte[]{1,2,3});
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{
            service.get().onStartCommand(job("new-folder",true,TransferJournal.REPLACE,true),0,1);drained(service.get());
            assertArrayEquals("Creating a folder redirected replacement into the parent",original,Files.readAllBytes(new File(provider.root,"important.bin").toPath()));
            assertTrue("Invalid folder creation was accepted",TransferForegroundService.state.error);assertEquals(0,alias.deletes);assertFalse(new File(provider.root,"new-folder").exists());
        }finally{service.destroy();}
    }
    @Test public void fallbackWriteModeCannotPublishAppendedPartialWhenOptionalVerificationIsOff()throws Exception{
        AppendingWriteDocuments append=new AppendingWriteDocuments();append.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;append.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,append);
        byte[] bytes={1,2,3,4};source("fallback.bin",bytes);Intent i=job("fallback.bin",false,TransferJournal.KEEP,false);TransferJournal journal=new TransferJournal(app);journal.ensure(i);
        Uri made=DocumentFiles.create(app,DocumentFiles.root(tree),"fallback.bin","application/octet-stream");Files.write(new File(provider.root,"fallback.bin").toPath(),new byte[]{1,2});
        String key=TransferJournal.key(new FileNodes.Node("share","fallback.bin","fallback.bin",false,4));org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","fallback.bin").put("madeUri",made.toString());
        journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","fallback.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        var first=Robolectric.buildService(TransferForegroundService.class).create();try{first.get().onStartCommand(i,0,1);drained(first.get());assertTrue("Append-only fallback published a corrupted copy as successful",TransferForegroundService.state.error);assertFalse(new File(provider.root,"fallback.bin").exists());assertArrayEquals(bytes,Files.readAllBytes(new File(EmbeddedServerService.share(app),"fallback.bin").toPath()));}finally{first.destroy();}
        var resumed=Robolectric.buildService(TransferForegroundService.class).create();try{resumed.get().onStartCommand(journal.request(i.getStringExtra("request_id")),0,1);drained(resumed.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"fallback.bin").toPath()));assertEquals(1,provider.root.listFiles().length);}finally{resumed.destroy();}
    }
    @Test public void pendingDownloadsFallbackCannotPublishAppendedPartialWithoutVerification()throws Exception{
        byte[] bytes={1,2,3,4};source("media-fallback.bin",bytes);PendingDownloads downloads=downloads(new AppendingPendingDownloads());Uri row=mediaRow(downloads,"media-fallback.bin",new byte[]{1,2},1);
        Intent i=job("media-fallback.bin",false,TransferJournal.KEEP,false).putExtra("destination",FileNodes.downloadsRoot().bundle());TransferJournal journal=new TransferJournal(app);journal.ensure(i);String key=TransferJournal.key(new FileNodes.Node("share","media-fallback.bin","media-fallback.bin",false,4));
        org.json.JSONObject output=new org.json.JSONObject().put("parent",TransferJournal.node(i.getBundleExtra("destination"))).put("name","media-fallback.bin").put("mediaUri",row.toString());journal.item(i.getStringExtra("request_id"),key,new org.json.JSONObject().put("targetName","media-fallback.bin").put("sha256",VerifiedCopy.sha256(new ByteArrayInputStream(bytes))).put("publishing",true).put("output",output));
        var first=Robolectric.buildService(TransferForegroundService.class).create();try{first.get().onStartCommand(i,0,1);drained(first.get());assertTrue("Corrupted pending row was published",TransferForegroundService.state.error);assertTrue(downloads.rows.isEmpty());assertArrayEquals(bytes,Files.readAllBytes(new File(EmbeddedServerService.share(app),"media-fallback.bin").toPath()));}finally{first.destroy();}
        var resumed=Robolectric.buildService(TransferForegroundService.class).create();try{resumed.get().onStartCommand(journal.request(i.getStringExtra("request_id")),0,1);drained(resumed.get());assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertEquals(2,downloads.inserted);assertEquals(1,downloads.rows.size());var result=downloads.rows.values().iterator().next();assertArrayEquals(bytes,Files.readAllBytes(result.file.toPath()));assertEquals(Integer.valueOf(0),result.values.getAsInteger("is_pending"));}finally{resumed.destroy();}
    }
    @Test @Config(sdk=24) public void failedLocalPublicationRestoresOriginalOnAndroid7()throws Exception{
        File directory=new File(EmbeddedServerService.share(app),"android7-restore");assertTrue(directory.mkdirs());File original=new File(directory,"original.bin");byte[] bytes={9,8,7};Files.write(original.toPath(),bytes);
        FileNodes.Node parent=new FileNodes.Node("share",directory.getName(),directory.getName(),true,0);TransferOutput output=new TransferOutput(app,parent,original.getName(),TransferOutput.find(app,parent,original.getName()));
        try(OutputStream out=output.open()){out.write(new byte[]{1,2});}
        for(File file:directory.listFiles())if(file.getName().endsWith(".part"))assertTrue(file.delete());boolean rejected=false;
        try{output.publish();}catch(IOException expected){rejected=true;}finally{output.abort();}
        assertTrue(rejected);assertArrayEquals(bytes,Files.readAllBytes(original.toPath()));assertEquals(1,directory.listFiles().length);
    }
    @Test public void resumedFilesystemReplacementCannotMoveAUserDirectoryIntoItsBackup()throws Exception{replacementDirectoryGuard(true);}
    @Test @Config(sdk=24) public void safReplacementRemainsSupportedOnAndroid7()throws Exception{
        byte[] bytes={1,2,3};source("android7.bin",bytes);Files.write(new File(provider.root,"android7.bin").toPath(),new byte[]{9});
        var service=Robolectric.buildService(TransferForegroundService.class).create();try{
            service.get().onStartCommand(job("android7.bin",false,TransferJournal.REPLACE,true),0,1);drained(service.get());
            assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"android7.bin").toPath()));assertEquals(1,provider.root.listFiles().length);
        }finally{service.destroy();}
    }
    private void replacementDirectoryGuard(boolean filesystem)throws Exception{
        for(boolean beforeOpen:new boolean[]{true,false}){
            String name="became-folder-"+beforeOpen+".bin";File directory=filesystem?new File(EmbeddedServerService.share(app),"guard-"+beforeOpen):provider.root;directory.mkdirs();
            File target=new File(directory,name);Files.write(target.toPath(),new byte[]{9});
            FileNodes.Node parent=filesystem?new FileNodes.Node("share",directory.getName(),directory.getName(),true,0):FileNodes.Node.from(job("unused",false,TransferJournal.REPLACE,true).getBundleExtra("destination"));
            FileNodes.Node prior=TransferOutput.find(app,parent,name);
            org.json.JSONObject saved=new org.json.JSONObject().put("parent",TransferJournal.node(parent.bundle())).put("name",name).put("prior",TransferJournal.node(prior.bundle()));
            TransferOutput output=new TransferOutput(app,parent,name,prior,saved,s->{});
            File sentinel=new File(target,"important.bin");boolean rejected=false;
            try{
                if(beforeOpen){assertTrue(target.delete());assertTrue(target.mkdir());Files.write(sentinel.toPath(),new byte[]{7,8});}
                try(OutputStream out=output.open()){out.write(new byte[]{1,2,3});}
                if(!beforeOpen){assertTrue(target.delete());assertTrue(target.mkdir());Files.write(sentinel.toPath(),new byte[]{7,8});}
                output.publish();
            }catch(IOException expected){rejected=true;}finally{output.abort();}
            assertTrue("Replacement moved the user's directory away from its original path",target.isDirectory());
            assertArrayEquals(new byte[]{7,8},Files.readAllBytes(sentinel.toPath()));
            assertTrue("Replacement accepted a directory as a file",rejected);
        }
    }
    @Test public void replacementServiceWaitsForOldUninterruptibleWriterBeforeResumingSameRequest()throws Exception{
        UninterruptibleOpenDocuments held=new UninterruptibleOpenDocuments();held.root=provider.root;ProviderInfo info=new ProviderInfo();info.authority="durable.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;held.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,held);
        byte[] bytes={1,2,3,4};source("overlap.bin",bytes);Intent i=job("overlap.bin",false,TransferJournal.KEEP,true);
        var old=Robolectric.buildService(TransferForegroundService.class).create();old.get().onStartCommand(i,0,1);assertTrue(held.firstOpened.await(5,TimeUnit.SECONDS));ExecutorService oldWorker=org.robolectric.util.ReflectionHelpers.getField(old.get(),"worker");old.destroy();
        var next=Robolectric.buildService(TransferForegroundService.class).create();try{next.get().onStartCommand(new TransferJournal(app).request(i.getStringExtra("request_id")),0,1);
            assertFalse("Replacement service opened the same output while the old writer still owned it",held.secondOpened.await(400,TimeUnit.MILLISECONDS));held.allowFirst.countDown();assertTrue(oldWorker.awaitTermination(5,TimeUnit.SECONDS));drained(next.get());
            assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);assertArrayEquals(bytes,Files.readAllBytes(new File(provider.root,"overlap.bin").toPath()));assertEquals(1,provider.root.listFiles().length);
        }finally{held.allowFirst.countDown();next.destroy();assertTrue(oldWorker.awaitTermination(5,TimeUnit.SECONDS));}
    }
}
