package com.bitpoint.homeservercontrol;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VerifiedCopyTest {
    @Test public void prefixHandlesDifferentShortReadsAndLeavesExactlyTheResumeOffset()throws Exception{
        byte[] bytes=new byte[190003];new Random(72).nextBytes(bytes);
        InputStream source=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int o,int n){return super.read(b,o,Math.min(n,7));}};
        InputStream saved=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int o,int n){return super.read(b,o,Math.min(n,31));}};
        VerifiedCopy.prefix(source,saved,65537);ByteArrayOutputStream result=new ByteArrayOutputStream();result.write(bytes,0,65537);
        VerifiedCopy.copy(source,result,65537,bytes.length,null);assertArrayEquals(bytes,result.toByteArray());
    }
    @Test public void mismatchingOrTruncatedPrefixCannotBeUsed()throws Exception{
        for(byte[] partial:new byte[][]{{1,8,3},{1,2}})try{VerifiedCopy.prefix(new ByteArrayInputStream(new byte[]{1,2,3}),new ByteArrayInputStream(partial),3);fail();}catch(IOException expected){}
    }
    @Test public void knownSizeAndDigestDetectChanges()throws Exception{
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",VerifiedCopy.sha256(new ByteArrayInputStream("abc".getBytes("UTF-8"))));
        for(int size:new int[]{2,4})try{VerifiedCopy.copy(new ByteArrayInputStream(new byte[]{1,2,3}),new ByteArrayOutputStream(),0,size,null);fail();}catch(IOException expected){}
    }
}
