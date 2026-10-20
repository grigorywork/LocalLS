package com.bitpoint.homeservercontrol;

/** Diagnostics contain stages/types only, never document URIs or file names/contents. */
final class LocalExportFailure {
    static String hint(String stage,Throwable error) {
        java.util.Set<Throwable> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(Throwable cause=error;cause!=null && seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof SecurityException)return "Нет разрешения на запись. Выбери «Загрузки» или другую папку.";
            if(cause instanceof LocalFileExporter.SourceChanged)return "Файл ещё изменяется. Дождись окончания передачи и повтори сохранение.";
            if(cause instanceof java.io.InterruptedIOException)return "Сохранение прервано. Исходный файл остался в LocalLS.";
            if(cause instanceof android.system.ErrnoException && ((android.system.ErrnoException)cause).errno==android.system.OsConstants.ENOSPC)
                return "Недостаточно свободного места. Освободи память и повтори сохранение.";
        }
        if("SOURCE".equals(stage))return "Принятый файл недоступен. Обнови список и проверь завершение передачи.";
        if("FOREGROUND".equals(stage))return "Android не разрешил фоновое сохранение. Открой LocalLS и повтори.";
        if("STOPPED".equals(stage))return "Сохранение остановлено Android. Исходный файл остался в LocalLS.";
        return "Не удалось записать файл. Попробуй «Загрузки» или другое место и скопируй диагностику.";
    }
    static String report(String stage,Throwable error) {
        String code=java.util.Arrays.asList("SOURCE","DESTINATION","COPY","CLOSE","PUBLISH","FOREGROUND","STOPPED").contains(stage)?stage:"EXPORT";
        StringBuilder out=new StringBuilder("LocalLS export / SDK "+android.os.Build.VERSION.SDK_INT+"\nЭтап: "+code+"\n");
        java.util.Set<Throwable> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(Throwable cause=error;cause!=null && seen.size()<8 && seen.add(cause);cause=cause.getCause()) {
            out.append(cause.getClass().getSimpleName()).append('\n');int limit=0;
            for(StackTraceElement frame:cause.getStackTrace()) {if(limit++==4)break;out.append("  ").append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber()).append('\n');}
        }
        return out.toString();
    }
}
