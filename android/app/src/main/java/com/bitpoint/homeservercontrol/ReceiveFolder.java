package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.io.File;

final class ReceiveFolder {
    static final String TREE="receive_folder_tree";
    static String selected(Context c){return c.getSharedPreferences(EmbeddedServerService.PREFS,0).getString(TREE,"");}
    static String label(Context c){return label(selected(c));}
    static String label(String tree){return tree.isEmpty()?(Build.VERSION.SDK_INT>=29?"Загрузки / LocalLS":"Выбери папку для автоматического сохранения"):"Выбранная папка телефона";}
    static LocalFileExporter.Destination destination(Context c,String relative){
        return destination(c,relative,selected(c));
    }
    static LocalFileExporter.Destination destination(Context c,String relative,String tree){
        TransferPaths.relative(relative);
        if(!tree.isEmpty())return new DocumentFiles.Destination(c,Uri.parse(tree),relative);
        int slash=relative.lastIndexOf('/');String parent=slash<0?"":relative.substring(0,slash);
        return new LocalExportDestination(c,new File(relative).getName(),"application/octet-stream",null,parent);
    }
}
