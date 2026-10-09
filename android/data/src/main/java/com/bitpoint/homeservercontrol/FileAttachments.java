package com.bitpoint.homeservercontrol;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;

/** URI references only; no file bytes or secrets in saved state/Intent extras. */
final class FileAttachments {
    static final String EXTRA = "attached_files";
    static final String PICK_ON_OPEN = "pick_attachment_on_open";
    static final int MAX = 64;

    private FileAttachments() {}

    static boolean add(ArrayList<Uri> list, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) || uri.getAuthority() == null || uri.getAuthority().isEmpty()
                || list.size() >= MAX || list.contains(uri)) return false;
        list.add(uri);
        return true;
    }

    static ArrayList<Uri> from(Intent intent) {
        ArrayList<Uri> out = new ArrayList<>();
        if (intent == null) return out;
        try {
            add(out, intent.getData());
            ClipData clip = intent.getClipData();
            if (clip != null) for (int i = 0; i < Math.min(MAX, clip.getItemCount()); i++)
                add(out, clip.getItemAt(i).getUri());
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                Parcelable item = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (item instanceof Uri) add(out, (Uri) item);
            }
            String key = Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) ? Intent.EXTRA_STREAM : EXTRA;
            ArrayList<Parcelable> many = intent.getParcelableArrayListExtra(key);
            if (many != null) for (int i = 0; i < Math.min(MAX, many.size()); i++)
                if (many.get(i) instanceof Uri) add(out, (Uri) many.get(i));
        } catch (RuntimeException ignored) {
            // Malformed incoming parcel/extras must not crash the share entry point.
        }
        return out;
    }

    static void put(Intent intent, Collection<Uri> files) {
        ArrayList<Uri> safe = new ArrayList<>();
        for (Uri uri : files) add(safe, uri);
        if (safe.isEmpty()) return;
        intent.putParcelableArrayListExtra(EXTRA, safe);
        ClipData clip = ClipData.newRawUri("LocalLS files", safe.get(0));
        for (int i = 1; i < safe.size(); i++) clip.addItem(new ClipData.Item(safe.get(i)));
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    static String safeName(String name) {
        if (name == null) return "file.bin";
        String safe = name.replace('/', '_').replace('\\', '_').replace('\0', '_')
                .replace('\n', '_').replace('\r', '_').trim();
        return safe.isEmpty() || ".".equals(safe) || "..".equals(safe) ? "file.bin" : safe;
    }
}
