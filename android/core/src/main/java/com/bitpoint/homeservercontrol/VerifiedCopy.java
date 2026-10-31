package com.bitpoint.homeservercontrol;

import java.io.*;
import java.security.*;

/** Bounded memory integrity and resume checks. Callers own all supplied streams. */
final class VerifiedCopy {
    static void check()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Передача отменена");}
    static byte[] digest(InputStream input)throws IOException{
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];int n;
            while((n=input.read(block))!=-1){check();if(n>0)md.update(block,0,n);}check();return md.digest();
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    static String sha256(InputStream input)throws IOException{return hex(digest(input));}
    /** Both streams are advanced by exactly length on success, including short stream reads. */
    static void prefix(InputStream source,InputStream partial,long length)throws IOException{
        prefix(source,partial,length,null);
    }
    static void prefix(InputStream source,InputStream partial,long length,LocalFileExporter.Progress progress)throws IOException{
        if(length<0)throw new IOException("Неверный размер частичного файла");
        byte[] a=new byte[65536],b=new byte[65536];long remaining=length;
        while(remaining>0){check();int n=(int)Math.min(a.length,remaining);fully(source,a,n);fully(partial,b,n);
            for(int i=0;i<n;i++)if(a[i]!=b[i])throw new IOException("Частичный файл отличается от исходника. Дозагрузка заблокирована");remaining-=n;if(progress!=null)progress.update(length-remaining,length);}
    }
    private static void fully(InputStream in,byte[] b,int count)throws IOException{
        int at=0;while(at<count){check();int n=in.read(b,at,count-at);if(n<0)throw new EOFException("Исходник или частичный файл изменился");if(n==0){int one=in.read();if(one<0)throw new EOFException();b[at++]=(byte)one;}else at+=n;}
    }
    static long copy(InputStream in,OutputStream out,long offset,long expected,LocalFileExporter.Progress progress)throws IOException{
        long count=offset;byte[] buffer=new byte[65536];int n;
        while((n=in.read(buffer))!=-1){check();if(n==0)continue;if(expected>=0&&count+n>expected)throw new IOException("Исходный файл изменился");
            out.write(buffer,0,n);count+=n;if(progress!=null)progress.update(count,expected);}
        check();if(expected>=0&&count!=expected)throw new IOException("Исходный файл изменился");out.flush();return count;
    }
}
