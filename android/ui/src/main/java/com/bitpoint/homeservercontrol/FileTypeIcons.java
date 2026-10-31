package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.bitpoint.homeservercontrol.ui.R;
import java.util.Locale;

/** Offline Lucide vectors, shared by local/remote files and transfer history. */
final class FileTypeIcons {
    enum Kind { FOLDER, IMAGE, DOCUMENT, PDF, CAD, ARCHIVE, VIDEO, AUDIO, FILE }

    static Kind kind(String name, boolean directory) {
        if (directory) return Kind.FOLDER;
        String safe = name == null ? "" : name;
        int dot = safe.lastIndexOf('.');
        String ext = dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic":
            case "heif": case "avif": case "bmp": case "tif": case "tiff": case "svg": return Kind.IMAGE;
            case "pdf": return Kind.PDF;
            case "dwg": case "dxf": case "dwt": return Kind.CAD;
            case "zip": case "7z": case "rar": case "tar": case "gz": case "bz2":
            case "xz": case "zst": case "cab": case "iso": return Kind.ARCHIVE;
            case "mp4": case "mkv": case "mov": case "avi": case "webm": case "m4v":
            case "mpg": case "mpeg": case "3gp": case "ts": return Kind.VIDEO;
            case "mp3": case "m4a": case "aac": case "ogg": case "opus": case "wav": case "flac": return Kind.AUDIO;
            case "txt": case "md": case "doc": case "docx": case "odt": case "rtf":
            case "xls": case "xlsx": case "ods": case "csv": case "ppt": case "pptx": case "odp": return Kind.DOCUMENT;
            default: return Kind.FILE;
        }
    }

    static Drawable drawable(Context context, String name, boolean directory) {
        Kind type = kind(name, directory);
        int resource;
        int color;
        String badge = "";
        boolean light = ThemeCatalog.index(context) == 4;
        switch (type) {
            case FOLDER: resource=R.drawable.ic_type_folder; color=light?0xff997014:0xffe4bf6d; break;
            case IMAGE: resource=R.drawable.ic_type_file_image; color=light?0xff2563eb:0xff60a5fa; break;
            case PDF: resource=R.drawable.ic_type_file_text; color=light?0xffd92d51:0xffff637a; badge="PDF"; break;
            case CAD: resource=R.drawable.ic_type_file_pen_line; color=light?0xff0f766e:0xff5dd6bb; badge=name.substring(name.lastIndexOf('.')+1).toUpperCase(Locale.ROOT); break;
            case ARCHIVE: resource=R.drawable.ic_type_file_archive; color=light?0xffa16207:0xffe3b754; break;
            case VIDEO: resource=R.drawable.ic_type_file_play; color=light?0xff7540c7:0xffb785ff; break;
            case AUDIO: resource=R.drawable.ic_type_file_music; color=light?0xffc0266e:0xffef8ccc; break;
            case DOCUMENT: resource=R.drawable.ic_type_file_text; color=ThemeCatalog.color(context,R.attr.hscAccent); break;
            default: resource=R.drawable.ic_type_file; color=ThemeCatalog.color(context,R.attr.hscTextSecondary);
        }
        Drawable vector=context.getDrawable(resource).mutate();
        vector.setTint(color);
        return new FileIcon(vector,badge,color);
    }

    static void apply(android.widget.TextView view,String name,boolean directory,int sizeDp){
        Drawable icon=drawable(view.getContext(),name,directory);int size=Math.round(sizeDp*view.getResources().getDisplayMetrics().density);icon.setBounds(0,0,size,size);
        Drawable right=view.getCompoundDrawablesRelative()[2];view.setCompoundDrawablesRelative(icon,null,right,null);view.setCompoundDrawablePadding(Math.round(10*view.getResources().getDisplayMetrics().density));
    }

    private static final class FileIcon extends Drawable {
        private final Drawable vector;
        private final String badge;
        private final int color;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha=255;
        FileIcon(Drawable vector,String badge,int color){this.vector=vector;this.badge=badge;this.color=color;}
        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();if(bounds.isEmpty())return;
            int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);
            vector.setBounds(0,0,24,24);vector.setAlpha(alpha);vector.draw(canvas);
            if(!badge.isEmpty()){
                paint.setColor(color);paint.setAlpha(alpha);canvas.drawRoundRect(1,14,23,23,2,2,paint);
                paint.setColor(Color.WHITE);paint.setAlpha(alpha);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(6.7f);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(badge,12,21,paint);
            }
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){vector.setColorFilter(filter);paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
