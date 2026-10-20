package com.bitpoint.homeservercontrol;

/** Shared path validation for untrusted SFTP/provider names. Never interpret names as paths. */
final class TransferPaths {
    static String name(String value) {
        if(value==null || value.isEmpty() || value.equals(".") || value.equals("..") || value.length()>240
                || value.indexOf('/')>=0 || value.indexOf('\\')>=0)
            throw new IllegalArgumentException("Недопустимое имя файла или папки");
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))
            throw new IllegalArgumentException("Недопустимое имя файла или папки");
        return value;
    }
    static String relative(String value) {
        if(value==null || value.length()>4000 || value.startsWith("/"))throw new IllegalArgumentException("Недопустимый путь");
        if(value.isEmpty())return value;
        String[] parts=value.split("/",-1);
        if(parts.length>48)throw new IllegalArgumentException("Слишком много вложенных папок");
        for(String part:parts)name(part);
        return value;
    }
    static String duplicate(String value,int number){
        name(value);String suffix=" ("+number+")";int dot=value.lastIndexOf('.');
        String extension=dot>0 && value.length()-dot<=40?value.substring(dot):"";
        String stem=extension.isEmpty()?value:value.substring(0,dot);
        return stem.substring(0,Math.min(stem.length(),220-suffix.length()-extension.length()))+suffix+extension;
    }
    static boolean temporary(String value) {
        for(String part:value.replace('\\','/').split("/"))
            if(part.startsWith(".localls-upload-") || part.startsWith(".import-"))return true;
        return false;
    }
}
