package com.bitpoint.homeservercontrol;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class LocalFileExporterTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static class Target implements LocalFileExporter.Destination {
        final File file;boolean opened,published,aborted;int maxWrite;
        Target(File file){this.file=file;}
        public OutputStream open() throws Exception {
            opened=true;return new FilterOutputStream(new FileOutputStream(file)) {
                @Override public void write(byte[] b,int offset,int length) throws IOException {maxWrite=Math.max(maxWrite,length);out.write(b,offset,length);}
            };
        }
        public void publish() throws Exception {published=true;}
        public void abort(){aborted=true;file.delete();}
    }
    private byte[] digest(File file)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)hash.update(b,0,n);}return hash.digest();
    }
    @Test public void sixtyMiBWithUnicodeNameCopiesExactlyWithoutLoadingWholeFile()throws Exception {
        File root=temp.newFolder(),source=new File(root,"Видео 60 МБ.bin");long size=60L*1024*1024+17;
        byte[] block=new byte[65536];new java.util.Random(66).nextBytes(block);
        try(OutputStream output=new FileOutputStream(source)){for(long left=size;left>0;left-=Math.min(left,block.length))output.write(block,0,(int)Math.min(left,block.length));}
        Target target=new Target(new File(temp.newFolder(),"saved.bin"));final long[] last={0};
        assertEquals(size,LocalFileExporter.copy(root,source.getName(),target,(copied,total)->{assertTrue(copied>=last[0]);assertEquals(size,total);last[0]=copied;}));
        assertEquals(size,last[0]);assertEquals(size,target.file.length());assertArrayEquals(digest(source),digest(target.file));
        assertTrue(target.published);assertFalse(target.aborted);assertTrue(target.maxWrite<=65536);assertTrue(source.exists());
    }
    @Test public void providerPermissionFailureNeverRemovesSource()throws Exception {
        File root=temp.newFolder();File source=new File(root,"x");Files.write(source.toPath(),new byte[]{1});
        Target target=new Target(new File(temp.newFolder(),"x")){public OutputStream open(){throw new SecurityException("content://private-uri");}};
        try{LocalFileExporter.copy(root,"x",target,(c,t)->{});fail();}catch(LocalFileExporter.Failure error){assertEquals("DESTINATION",error.stage);}
        assertTrue(source.exists());assertTrue(target.aborted);assertFalse(target.published);
    }
    @Test public void writeFailureAbortsIncompleteDestination()throws Exception {
        File root=temp.newFolder();Files.write(new File(root,"x").toPath(),new byte[100000]);
        Target target=new Target(new File(temp.newFolder(),"x")){public OutputStream open(){return new OutputStream(){public void write(int b)throws IOException{throw new IOException();}};}};
        try{LocalFileExporter.copy(root,"x",target,(c,t)->{});fail();}catch(LocalFileExporter.Failure error){assertEquals("COPY",error.stage);}
        assertTrue(target.aborted);assertFalse(target.published);
    }
    @Test public void closeFailureCannotBeReportedAsSuccessfulSave()throws Exception {
        File root=temp.newFolder();Files.write(new File(root,"x").toPath(),new byte[]{1});
        Target target=new Target(new File(temp.newFolder(),"x")){public OutputStream open(){return new ByteArrayOutputStream(){public void close()throws IOException{throw new IOException();}};}};
        try{LocalFileExporter.copy(root,"x",target,(c,t)->{});fail();}catch(LocalFileExporter.Failure error){assertEquals("CLOSE",error.stage);}
        assertTrue(target.aborted);assertFalse(target.published);
    }
    @Test public void sourceGrowingDuringTransferIsNotPublishedAsComplete()throws Exception {
        File root=temp.newFolder(),source=new File(root,"x");Files.write(source.toPath(),new byte[100000]);Target target=new Target(new File(temp.newFolder(),"x"));
        try{LocalFileExporter.copy(root,"x",target,(c,t)->{if(c==65536)try(OutputStream o=new FileOutputStream(source,true)){o.write(5);}catch(IOException e){throw new RuntimeException(e);}});fail();}
        catch(LocalFileExporter.Failure error){assertTrue(error.getCause() instanceof LocalFileExporter.SourceChanged);}
        assertTrue(target.aborted);assertFalse(target.published);assertTrue(source.exists());
    }
    @Test public void interruptionAbortsCopyAndPreservesReceivedFile()throws Exception {
        File root=temp.newFolder(),source=new File(root,"x");Files.write(source.toPath(),new byte[100000]);Target target=new Target(new File(temp.newFolder(),"x"));
        try{LocalFileExporter.copy(root,"x",target,(c,t)->{if(c>0)Thread.currentThread().interrupt();});fail();}
        catch(LocalFileExporter.Failure error){assertTrue(error.getCause() instanceof InterruptedIOException);}
        finally{Thread.interrupted();}
        assertTrue(target.aborted);assertFalse(target.published);assertEquals(100000,source.length());
    }
    @Test public void traversalAndMissingSourceNeverCreateDestination()throws Exception {
        File parent=temp.newFolder(),root=new File(parent,"share");root.mkdir();Files.write(new File(parent,"secret").toPath(),new byte[]{1});
        for(String path:new String[]{"../secret","missing"}) {
            Target target=new Target(new File(temp.newFolder(),"x"));
            try{LocalFileExporter.copy(root,path,target,(c,t)->{});fail();}catch(LocalFileExporter.Failure error){assertEquals("SOURCE",error.stage);}
            assertFalse(target.opened);assertFalse(target.published);
        }
    }
    @Test public void cancellationOnFinalBlockCannotPublishTheCopy()throws Exception{
        File root=temp.newFolder();Files.write(new File(root,"x").toPath(),new byte[]{1});Target target=new Target(new File(temp.newFolder(),"x"));
        try{LocalFileExporter.copy(root,"x",target,(done,total)->{if(done>0)Thread.currentThread().interrupt();});fail("Last-block cancellation reported success");}
        catch(LocalFileExporter.Failure error){assertTrue(error.getCause() instanceof InterruptedIOException);}
        finally{Thread.interrupted();}
        assertTrue(target.aborted);assertFalse(target.published);assertTrue(new File(root,"x").exists());
    }
    @Test public void cancellationDuringDestinationCloseIsCheckedBeforePublishing()throws Exception{
        File root=temp.newFolder();Files.write(new File(root,"x").toPath(),new byte[]{1});java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        Target target=new Target(new File(temp.newFolder(),"x")){
            @Override public OutputStream open(){return new ByteArrayOutputStream(){@Override public void close(){cancelled.set(true);}};}
        };
        try{LocalFileExporter.copy(root,"x",target,(done,total)->{if(cancelled.get())throw new java.util.concurrent.CancellationException();});fail("Close-time cancellation reported success");}
        catch(LocalFileExporter.Failure error){assertTrue(error.getCause() instanceof java.util.concurrent.CancellationException);}
        assertTrue(target.aborted);assertFalse(target.published);assertTrue(new File(root,"x").exists());
    }
    @Test public void alreadyInterruptedEmptyCopyDoesNotCreateDestination()throws Exception{
        File root=temp.newFolder();Files.write(new File(root,"empty").toPath(),new byte[0]);Target target=new Target(new File(temp.newFolder(),"copy"));
        Thread.currentThread().interrupt();
        try{LocalFileExporter.copy(root,"empty",target,(done,total)->{});fail("An interrupted empty copy was published");}
        catch(LocalFileExporter.Failure error){assertTrue(error.getCause() instanceof InterruptedIOException);}
        finally{Thread.interrupted();}
        assertFalse(target.opened);assertFalse(target.published);assertTrue(new File(root,"empty").exists());
    }

}
