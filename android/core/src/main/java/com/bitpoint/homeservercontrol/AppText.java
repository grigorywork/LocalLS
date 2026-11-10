package com.bitpoint.homeservercontrol;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Literal message catalog. User names, paths and protocol fields are never translated. */
public final class AppText {
    private static volatile String language="ru";
    private static final Map<String,String> english=new HashMap<>(),russian=new HashMap<>();
    static {
        try(InputStream in=AppText.class.getResourceAsStream("/localls-en.properties")){
            if(in!=null){Properties p=new Properties();p.load(new InputStreamReader(in,StandardCharsets.UTF_8));for(String key:p.stringPropertyNames()){String value=p.getProperty(key);english.put(key,value);russian.putIfAbsent(value,key);}}
        }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static void language(String value){language="en".equals(value)?"en":"ru";}
    public static String language(){return language;}
    public static String tr(String value){if(value==null)return null;return "en".equals(language)?english.getOrDefault(value,value):russian.getOrDefault(value,value);}
    public static String[] trs(String[] values){String[] out=values.clone();for(int i=0;i<out.length;i++)out[i]=tr(out[i]);return out;}
    private AppText(){}
}
