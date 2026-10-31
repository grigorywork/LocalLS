package com.bitpoint.homeservercontrol;

import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class AutoSaveOwnershipTest {
    public static class Documents extends LastModIntegrationTest.Documents {
        String fileAlias,folderAlias;int writes,deletes;
        @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{
            String alias=DocumentFiles.DIRECTORY.equals(mime)?folderAlias:fileAlias;
            return alias==null?super.createDocument(parent,mime,name):alias;
        }
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{if(mode.contains("w"))writes++;return super.openDocument(id,mode,signal);}
        @Override public void deleteDocument(String id)throws FileNotFoundException{
            deletes++;try(var paths=Files.walk(file(id).toPath())){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}catch(IOException e){throw new FileNotFoundException();}
        }
    }
    private android.app.Application app;private Documents provider;private Uri tree;private File share,canary;
    private final byte[] important={8,13,21,34,55},incoming={2,3,5};
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();provider=new Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"autosave-owned-").toFile();
        ProviderInfo info=new ProviderInfo();info.authority="autosave.owned.documents";info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");
        share=Files.createTempDirectory(app.getCacheDir().toPath(),"autosave-source-").toFile();Files.write(new File(share,"incoming.bin").toPath(),incoming);canary=new File(provider.root,"important.bin");Files.write(canary.toPath(),important);
    }
    private LocalFileExporter.Failure copy(String relative,LocalFileExporter.Progress progress){try{LocalFileExporter.copy(share,relative,ReceiveFolder.destination(app,relative,tree.toString()),progress);return null;}catch(LocalFileExporter.Failure e){return e;}}
    private void refusedAlias(String alias)throws Exception{
        provider.fileAlias=alias;LocalFileExporter.Failure failure=copy("incoming.bin",(n,total)->{});
        assertNotNull("An unowned create response was accepted",failure);assertEquals("Unowned document opened for writing",0,provider.writes);assertEquals("Cleanup deleted a document not created by this transfer",0,provider.deletes);assertArrayEquals(important,Files.readAllBytes(canary.toPath()));assertArrayEquals(incoming,Files.readAllBytes(new File(share,"incoming.bin").toPath()));
    }
    @Test public void parentReturnedInsteadOfNewFileIsNeverWrittenOrDeleted()throws Exception{refusedAlias("root");}
    @Test public void existingFileReturnedInsteadOfNewCopyIsNeverWrittenOrDeleted()throws Exception{refusedAlias("root/important.bin");}
    @Test public void sameNameFileOutsideSelectedParentIsNeverWrittenOrDeleted()throws Exception{
        File elsewhere=new File(provider.root,"Elsewhere");assertTrue(elsewhere.mkdir());File outside=new File(elsewhere,"incoming.bin");Files.write(outside.toPath(),important);refusedAlias("root/Elsewhere/incoming.bin");assertArrayEquals(important,Files.readAllBytes(outside.toPath()));
    }
    @Test public void parentReturnedInsteadOfNestedDirectoryCannotRedirectTheCopy()throws Exception{
        provider.folderAlias="root";File folder=new File(share,"nested");assertTrue(folder.mkdir());Files.write(new File(folder,"incoming.bin").toPath(),incoming);
        assertNotNull("Invalid folder creation redirected the copy",copy("nested/incoming.bin",(n,total)->{}));assertEquals(0,provider.writes);assertEquals(0,provider.deletes);assertFalse(new File(provider.root,"incoming.bin").exists());assertArrayEquals(important,Files.readAllBytes(canary.toPath()));
    }
    @Test public void validNestedDestinationStillCopiesAndKeepsSourceAndOtherDocuments()throws Exception{
        File folder=new File(share,"nested");assertTrue(folder.mkdir());Files.write(new File(folder,"incoming.bin").toPath(),incoming);assertNull(copy("nested/incoming.bin",(n,total)->{}));assertArrayEquals(incoming,Files.readAllBytes(new File(provider.root,"nested/incoming.bin").toPath()));assertArrayEquals(incoming,Files.readAllBytes(new File(folder,"incoming.bin").toPath()));assertArrayEquals(important,Files.readAllBytes(canary.toPath()));assertEquals(1,provider.writes);assertEquals(0,provider.deletes);
    }
    @Test public void failedCopyDeletesOnlyItsNewlyCreatedFile()throws Exception{
        assertNotNull(copy("incoming.bin",(n,total)->{if(n>0)throw new IllegalStateException("Controlled copy failure");}));assertEquals(1,provider.writes);assertEquals(1,provider.deletes);assertFalse(new File(provider.root,"incoming.bin").exists());assertArrayEquals(important,Files.readAllBytes(canary.toPath()));assertArrayEquals(incoming,Files.readAllBytes(new File(share,"incoming.bin").toPath()));
    }
}
