package com.bitpoint.homeservercontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Private explicit one-shot callback; raw shell output is never displayed or logged. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String nonce = prefs.getString("pending", "");
        if (nonce.isEmpty() || !("localls.setup." + nonce).equals(intent.getAction())
                || System.currentTimeMillis() - prefs.getLong("pending_at", 0) > 1800000) return;
        Bundle result = intent.getBundleExtra("result");
        // Official Termux Errno.ERRNO_SUCCESS is -1; 0 is an error, not success.
        int error = result == null ? -2 : result.getInt("err", -1);
        int exit = result == null ? -1 : result.getInt("exitCode", -1);
        String kind = prefs.getString("pending_kind", "ssh");
        SharedPreferences.Editor edit = prefs.edit().remove("pending")
                .remove("pending_kind").putString("last_kind", kind).putLong("completed_at", System.currentTimeMillis())
                .putInt("last_termux_error", error).putInt("last_exit_code", exit);
        if (result == null || error != -1 || exit != 0) {
            String detail = result == null ? "" : result.getString("errmsg", "");
            if (detail != null && detail.contains("allow-external-apps")) edit.remove("applied_termux_version");
            edit.putString("status", failureMessage(result != null, error, exit,
                    detail)
                    + "\nКод Termux: " + error + "; завершение: " + exit).apply();
            return;
        }
        String output = result.getString("stdout", "");
        if ("agent".equals(kind)) {
            boolean ready = false;
            int agentPort = 0;
            if (output != null && output.length() <= 16384) for (String line : output.split("\\r?\\n")) {
                if ("AGENT_READY=1".equals(line)) ready = true;
                if (line.matches("AGENT_PORT=[0-9]{1,5}")) agentPort = Integer.parseInt(line.substring(11));
            }
            if (ready && agentPort > 0 && agentPort <= 65535)
                edit.putString("status", "Агент подготовлен. Проверяем HTTPS и токен.").apply();
            else edit.putInt("last_exit_code", 56).putString("status", "Агент не подтвердил готовность. Повтори этот шаг.").apply();
            return;
        }
        String user = "", port = "";
        StringBuilder fingerprints = new StringBuilder();
        if (output == null) output = "";
        if (output.length() <= 16384) for (String line : output.split("\\r?\\n")) {
            if (line.startsWith("USER=")) user = line.substring(5);
            else if (line.startsWith("PORT=")) port = line.substring(5);
            else if (line.startsWith("HOSTKEY=") && line.substring(8).matches("SHA256:[A-Za-z0-9+/]{43}"))
                fingerprints.append(line.substring(8)).append('\n');
        }
        if (!user.matches("[a-zA-Z0-9_]{1,64}") || !port.matches("[0-9]{1,5}")) {
            edit.putInt("last_exit_code", 43).putString("status", "Termux не вернул корректные параметры сервера.").apply();
            return;
        }
        int number = Integer.parseInt(port);
        if (number < 1 || number > 65535) { edit.putInt("last_exit_code", 43).putString("status", "Некорректный порт сервера.").apply(); return; }
        edit.putString("user", user).putInt("port", number).putString("device_role", "server")
                .putString("host_fingerprints", fingerprints.toString())
                .putString("applied_termux_version", TermuxSetup.version(context))
                .putString("status", "OpenSSH подготовлен. Проверь подключение перед экспортом.").apply();
    }
    static String failureMessage(boolean received, int error, int exit, String rawError) {
        String prefix = "Подготовка не завершена. ";
        if (!received) return prefix + "Termux не вернул результат. Проверь первый запуск и повтори.";
        if (error != -1) {
            // Inspect only known markers; never return or persist raw Termux errors.
            String detail = rawError == null ? "" : rawError.substring(0, Math.min(rawError.length(), 16384))
                    .toLowerCase(java.util.Locale.ROOT);
            if (detail.contains("allow-external-apps"))
                return prefix + "Termux не применил разрешение внешних команд. Нажми «Подготовить сервер»: мастер откроет Termux для обновления настроек. Вернись кнопкой «Назад» через несколько секунд.";
            if (detail.contains("executable") || detail.contains("/usr/bin/bash"))
                return prefix + "Termux не может открыть исполняемый файл. Открой Termux и дождись установки среды; затем повтори.";
            if (detail.contains("permission") || detail.contains("разрешени"))
                return prefix + "Termux отказал в доступе. Проверь LocalLS → Разрешения → Выполнение команд Termux.";
            if (detail.contains("working directory") || detail.contains("working-directory"))
                return prefix + "Termux не может открыть домашнюю папку. Открой Termux, затем повторно выбери его корневую папку.";
            return prefix + "Termux отклонил RUN_COMMAND. Открой Termux и проверь текст его уведомления; затем вернись и повтори.";
        }
        if (exit == 40) return prefix + "Не удалось обновить список пакетов. Проверь интернет и повтори.";
        if (exit == 41) return prefix + "Не удалось установить OpenSSH. Проверь интернет и свободное место.";
        if (exit == 50) return prefix + "Не удалось установить Python/OpenSSL для HTTPS-агента. Проверь интернет и место, повтори шаг агента.";
        if (exit == 51) return prefix + "Существующий агент использует HTTP. Мастер сохранил его настройки и процесс. Обновление на HTTPS требует отдельного действия; SSH продолжает работать.";
        if (exit == 52) return prefix + "Настройки агента некорректны. Существующий файл сохранён; проверь порт, TLS и токен в настройках агента.";
        if (exit == 53) return prefix + "Порт агента занят другим процессом или сертификат не совпал. Существующий процесс сохранён; проверь адрес и порт.";
        if (exit == 54) return prefix + "Файлы сертификата, ключа или настройки агента требуют исправления доступа. Они не заменены; SSH сохранён.";
        if (exit == 55 || exit == 56) return prefix + "HTTPS-агент не запустился. Рабочий SSH сохранён; проверь настройки агента и повтори этот шаг.";
        return prefix + "Команда Termux завершилась с ошибкой. Проверь среду Termux и повтори.";
    }
}
