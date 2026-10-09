package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.HashSet;

/** SAF destination, using fresh names so downloading never overwrites an existing local file. */
final class LocalDownloadFolder {
    static Uri createFile(Context context, Uri tree, String remoteName) throws Exception {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        HashSet<String> names = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException();
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
                if (names.size() > 50000) throw new IOException("Destination too large");
            }
        }
        String original = FileAttachments.safeName(remoteName), name = original;
        int index = 2;
        while (names.contains(name)) {
            int dot = original.lastIndexOf('.');
            name = dot > 0 ? original.substring(0,dot) + " (" + index + ")" + original.substring(dot)
                    : original + " (" + index + ")";
            if (++index > 10000) throw new IOException();
        }
        Uri file = DocumentsContract.createDocument(context.getContentResolver(), parent, "application/octet-stream", name);
        if (file == null) throw new IOException();
        return file;
    }
}
