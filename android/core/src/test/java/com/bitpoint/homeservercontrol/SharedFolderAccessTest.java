package com.bitpoint.homeservercontrol;
import java.io.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class SharedFolderAccessTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void permitsShareAndNestedFiles() throws Exception {
        File root=temp.newFolder("share");File child=new File(root,"folder/file");
        assertEquals(root.getCanonicalFile(),SharedFolderAccess.within(root,root));
        assertEquals(child.getCanonicalFile(),SharedFolderAccess.within(root,child));
    }
    @Test public void rejectsSiblingTraversalAndSymlinkEscape() throws Exception {
        File root=temp.newFolder("share"),outside=temp.newFolder("share-other");
        Files.createSymbolicLink(new File(root,"escape").toPath(),outside.toPath());
        for(File f:new File[]{outside,new File(root,"../secret"),new File(root,"escape/secret")}) {
            try{SharedFolderAccess.within(root,f);fail("Outside share");}catch(IOException expected){}
        }
    }
}
