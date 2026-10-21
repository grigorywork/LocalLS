package com.bitpoint.homeservercontrol;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A view of one folder: literal name filtering and stable, directory-first sorting. */
final class FileListQuery {
    static final int NAME_ASC=0,NAME_DESC=1,SIZE_DESC=2,SIZE_ASC=3;
    interface Entry {String displayName();long bytes();boolean folder();}
    private FileListQuery(){}
    static int order(int value){return value>=NAME_ASC && value<=SIZE_ASC?value:NAME_ASC;}
    static String filter(String value){
        if(value==null)return "";String text=value.trim();
        if(text.length()>160){int end=160;if(Character.isHighSurrogate(text.charAt(end-1)))end--;text=text.substring(0,end);}
        return text;
    }
    private static String folded(String value){return Normalizer.normalize(value,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    static <T extends Entry> List<T> apply(List<T> source,String filter,int requestedOrder){
        String query=folded(filter(filter));ArrayList<T> result=new ArrayList<>();
        for(T item:source)if(folded(item.displayName()).contains(query))result.add(item);
        int order=order(requestedOrder);
        Comparator<T> names=Comparator.comparing((T item)->folded(item.displayName())).thenComparing(Entry::displayName);
        result.sort((a,b)->{
            if(a.folder()!=b.folder())return a.folder()?-1:1;
            if(!a.folder() && (order==SIZE_DESC || order==SIZE_ASC)){
                int size=Long.compare(Math.max(0,a.bytes()),Math.max(0,b.bytes()));
                if(size!=0)return order==SIZE_DESC?-size:size;
            }
            return order==NAME_DESC?names.compare(b,a):names.compare(a,b);
        });
        return result;
    }
}
