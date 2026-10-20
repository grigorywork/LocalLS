package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;

/** Scoped-storage export; only newly inserted MediaStore entries may be deleted on failure. */
final class LocalExportDestination implements LocalFileExporter.Destination {
    private final ContentResolver resolver;
    private final String name,mime,parent;
    private Uri uri;
    private final boolean downloads;
    LocalExportDestination(Context context,String name,String mime,Uri selected) {
        this(context,name,mime,selected,"");
    }
    LocalExportDestination(Context context,String name,String mime,Uri selected,String parent) {
        this.parent=TransferPaths.relative(parent);
        resolver=context.getApplicationContext().getContentResolver();this.name=name;this.mime=mime;
        uri=selected;downloads=selected==null;
    }
    @Override public OutputStream open() throws Exception {
        if(downloads) {
            if(Build.VERSION.SDK_INT<29)throw new UnsupportedOperationException();
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/LocalLS"+(parent.isEmpty()?"":"/"+parent));
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("MediaStore insert failed");
        }
        if(uri==null || !"content".equals(uri.getScheme()))throw new IllegalArgumentException("Expected content URI");
        try {return resolver.openOutputStream(uri,"wt");}
        catch(IllegalArgumentException | UnsupportedOperationException unsupported) {
            return resolver.openOutputStream(uri,"w");
        }
    }
    @Override public void publish() throws Exception {
        if(downloads) {
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(uri,values,null,null)!=1)throw new IOException("MediaStore publish failed");
        }
    }
    @Override public void abort() {
        // SAF providers can return existing documents: never delete a selected user document.
        if(downloads && uri!=null)try{resolver.delete(uri,null,null);}catch(Exception ignored) { }
    }
    String location() { return downloads?"Загрузки / LocalLS":"Выбранная папка"; }
}
