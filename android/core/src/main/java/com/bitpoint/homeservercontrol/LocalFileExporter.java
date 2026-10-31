package com.bitpoint.homeservercontrol;

import java.io.*;

/** Streams a copy of a received file; never modifies/removes the original. */
final class LocalFileExporter {
    interface Destination {
        OutputStream open() throws Exception;
        void publish() throws Exception;
        void abort();
        default InputStream readBack()throws Exception{throw new IOException("Хранилище не разрешает проверку записанной копии");}
    }
    interface Progress { void update(long bytes,long total); }
    static final class Failure extends Exception {
        final String stage;
        Failure(String stage,Exception cause) {super(cause);this.stage=stage;}
    }
    static final class SourceChanged extends IOException { }
    static long copy(File root,String relative,Destination target,Progress progress) throws Failure {
        String stage="SOURCE";boolean opened=false;
        try {
            checkInterrupted();
            if(TransferPaths.temporary(relative))throw new IOException("Upload has not finished");
            File source=SharedFolderAccess.within(root,new File(root,relative));
            if(!source.isFile())throw new FileNotFoundException();
            long expected=source.length(),modified=source.lastModified(),copied=0;
            try(InputStream input=new FileInputStream(source)) {
                stage="DESTINATION";checkInterrupted();opened=true;
                try(OutputStream output=target.open()) {
                    if(output==null)throw new IOException("Destination stream unavailable");
                    stage="COPY";byte[] buffer=new byte[65536];int count;progress.update(0,expected);
                    while((count=input.read(buffer))!=-1) {
                        checkInterrupted();
                        if(copied+count>expected)throw new SourceChanged();
                        output.write(buffer,0,count);copied+=count;progress.update(copied,expected);
                    }
                    if(copied!=expected || source.length()!=expected || source.lastModified()!=modified)throw new SourceChanged();
                    output.flush();stage="CLOSE";
                }
            }
            stage="PUBLISH";checkInterrupted();
            // A provider can block in close(). Recheck the owner's cancellation after both streams close.
            progress.update(copied,expected);checkInterrupted();target.publish();return copied;
        } catch(Exception error) {
            if(opened)try{target.abort();}catch(Exception ignored) { }
            throw new Failure(stage,error);
        }
    }
    private static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
}
