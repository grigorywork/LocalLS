package com.bitpoint.homeservercontrol;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FileListQueryTest {
    private record Item(String displayName,long bytes,boolean folder) implements FileListQuery.Entry{}
    private List<Item> items(){return List.of(new Item("small.txt",3,false),new Item("large.bin",900,false),new Item("Folder",0,true),new Item("Other",9999,true));}
    private List<String> names(List<Item> items){ArrayList<String> result=new ArrayList<>();for(Item i:items)result.add(i.displayName());return result;}
    @Test public void directoriesStayFirstAndSourceListIsNotMutated(){
        List<Item> source=items();assertEquals(List.of("Other","Folder","small.txt","large.bin"),names(FileListQuery.apply(source,"",FileListQuery.NAME_DESC)));assertEquals("small.txt",source.get(0).displayName());
    }
    @Test public void sizeOrderIsNumericAndDoesNotUseDirectoryStorageSize(){
        assertEquals(List.of("Folder","Other","large.bin","small.txt"),names(FileListQuery.apply(items(),"",FileListQuery.SIZE_DESC)));
        assertEquals(List.of("Folder","Other","small.txt","large.bin"),names(FileListQuery.apply(items(),"",FileListQuery.SIZE_ASC)));
    }
    @Test public void unicodeCaseAndCombiningMarksMatchButWildcardsRemainLiteral(){
        List<Item> source=List.of(new Item("ФОТО-é.jpg",1,false),new Item("a*.bin",2,false),new Item("abc.bin",3,false));
        assertEquals(List.of("ФОТО-é.jpg"),names(FileListQuery.apply(source,"фото-e\u0301",0)));assertEquals(List.of("a*.bin"),names(FileListQuery.apply(source,"*",0)));assertTrue(FileListQuery.apply(source,"missing",0).isEmpty());
    }
    @Test public void invalidSavedOrderAndLongFilterAreBounded(){
        assertEquals(FileListQuery.NAME_ASC,FileListQuery.order(-20));assertEquals(FileListQuery.NAME_ASC,FileListQuery.order(100));assertEquals("",FileListQuery.filter(null));assertEquals(160,FileListQuery.filter("a".repeat(5000)).length());assertEquals(159,FileListQuery.filter("a".repeat(159)+"😀").length());
    }
}
