package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;

/** Unknown provider/server capacity stays unknown; a known shortage blocks before copying. */
final class TransferCapacity {
    static long local(Context context,FileNodes.Node destination){
        if(destination==null||"share".equals(destination.kind))return EmbeddedServerService.share(context).getUsableSpace();
        if("downloads".equals(destination.kind))return Environment.getExternalStorageDirectory().getUsableSpace();
        if(!"doc".equals(destination.kind))return -1;
        Uri uri=Uri.parse(destination.path);
        if(!DocumentsContract.isDocumentUri(context,uri))return Environment.getExternalStorageDirectory().getUsableSpace();
        try{
            String root=DocumentsContract.getTreeDocumentId(uri);
            Uri roots=DocumentsContract.buildRootsUri(uri.getAuthority());
            try(Cursor c=context.getContentResolver().query(roots,new String[]{DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_AVAILABLE_BYTES},null,null,null)){
                if(c==null)return -1;while(c.moveToNext())if(root.equals(c.getString(0))&&!c.isNull(1))return Math.max(-1,c.getLong(1));
            }
        }catch(Exception ignored){}return -1;
    }
    static void require(long bytes,long free)throws IOException{if(bytes<0)throw new IOException("Слишком большой объём передачи");if(free>=0&&bytes>free)throw new IOException("Недостаточно места: нужно "+LocalFileExportService.format(bytes)+", доступно "+LocalFileExportService.format(free));}
    static String describe(long bytes,boolean unknown,long free){return "Выбрано: "+(unknown?"не менее ":"")+LocalFileExportService.format(bytes)+(unknown?"; часть размеров недоступна":"")+"\nСвободно в назначении: "+(free<0?"не сообщается хранилищем":LocalFileExportService.format(free));}
}
