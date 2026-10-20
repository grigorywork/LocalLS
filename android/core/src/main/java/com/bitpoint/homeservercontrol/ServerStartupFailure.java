package com.bitpoint.homeservercontrol;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** No exception messages or user-supplied strings: safe to copy without keys/passwords. */
final class ServerStartupFailure {
    static String report(String stage,Throwable error) {
        String code;
        switch(stage) {
            case "KEY":code="PUBLIC_KEY";break;
            case "IDENTITY":code="HOST_KEY";break;
            case "NETWORK":code="NETWORK";break;
            case "POWER":code="POWER";break;
            default:code="FOREGROUND";
        }
        StringBuilder out=new StringBuilder("Этап: "+code+"\n");
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=error;cause!=null && seen.size()<8 && seen.add(cause);cause=cause.getCause()) {
            out.append(cause.getClass().getSimpleName()).append('\n');int limit=0;
            for(StackTraceElement frame:cause.getStackTrace()) {if(limit++>=5)break;out.append("  ").append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber()).append('\n');}
        }
        return out.toString();
    }
    static String hint(String stage,Throwable error) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=error;cause!=null && seen.add(cause);cause=cause.getCause())
            if(cause instanceof java.net.BindException)return "Порт занят. Останови другой сервер или выбери другой порт.";
        if("KEY".equals(stage))return "Не удалось прочитать публичный ключ. Нужен RSA 3072+ или ECDSA P-256 (.pub).";
        if("IDENTITY".equals(stage))return "Не удалось открыть host key в Android Keystore. Ключ не заменён.";
        if("POWER".equals(stage))return "Android не разрешил поддержку сервера в фоне.";
        if("NETWORK".equals(stage))return "Ошибка запуска сетевого сервера. Скопируй диагностику кнопкой ниже.";
        return "Android не разрешил запуск службы. Скопируй диагностику кнопкой ниже.";
    }
}
