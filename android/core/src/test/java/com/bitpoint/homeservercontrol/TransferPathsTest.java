package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransferPathsTest {
    @Test public void unicodeNestedPathsAndTemporaryFiles(){assertEquals("Фото/Отпуск/кадр.jpg",TransferPaths.relative("Фото/Отпуск/кадр.jpg"));assertTrue(TransferPaths.temporary("a/.localls-upload-123.part"));assertFalse(TransferPaths.temporary("a/photo.jpg"));}
    @Test public void duplicateNamesKeepFileExtensions(){assertEquals("фото (2).jpg",TransferPaths.duplicate("фото.jpg",2));assertEquals("Папка (1)",TransferPaths.duplicate("Папка",1));}
    @Test public void providerAndRemoteTraversalCannotBecomeLocalPaths(){for(String path:new String[]{"/root","../file","a/../file","a//file","a\\file","a/","a/\nfile","."}){try{TransferPaths.relative(path);fail(path);}catch(IllegalArgumentException expected){}}}
}
