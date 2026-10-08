package com.bitpoint.homeservercontrol;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Official Termux SAF and RUN_COMMAND bridge. Never sends a password to Termux commands. */
final class TermuxSetup {
    static final String PACKAGE = "com.termux";
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String HOME = "/data/data/com.termux/files/home";
    static final String PREFS = "server_setup";
    static final String SCRIPT = "set -eu\n" +
            "export PATH=/data/data/com.termux/files/usr/bin:$PATH\n" +
            "if ! command -v sshd >/dev/null 2>&1; then\n" +
            " apt-get update </dev/null >/dev/null 2>&1 || exit 40\n" +
            " DEBIAN_FRONTEND=noninteractive apt-get -o Dpkg::Options::=--force-confold install -y openssh </dev/null >/dev/null 2>&1 || exit 41\n" +
            "fi\n" +
            "if ! find /data/data/com.termux/files/usr/etc/ssh -maxdepth 1 -name 'ssh_host_*_key' -type f | grep -q .; then ssh-keygen -A >/dev/null 2>&1; fi\n" +
            "chmod 600 \"$HOME/.termux_authinfo\" 2>/dev/null || true\n" +
            "mkdir -p \"$HOME/LocalLS\"\n" +
            "if ! pgrep -x sshd >/dev/null 2>&1; then sshd; fi\n" +
            "printf 'USER=%s\\n' \"$(whoami)\"\n" +
            "printf 'PORT=%s\\n' \"$(sshd -T 2>/dev/null | awk 'tolower($1) == \"port\" {print $2; exit}')\"\n";

    static boolean installed(Context context) {
        try { context.getPackageManager().getPackageInfo(PACKAGE, 0); return true; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return false; }
    }

    static String enableExternalApps(String properties) {
        StringBuilder result = new StringBuilder();
        for (String line : properties.split("\\r?\\n")) {
            if (!line.matches("\\s*allow-external-apps\\s*[=:].*")) result.append(line).append('\n');
        }
        // Android Properties skips blank continuation lines. Remove an unmatched
        // EOF escape (the parser already discards it) before appending a new key.
        int end = result.length();
        while (end > 0 && (result.charAt(end - 1) == '\n' || result.charAt(end - 1) == '\r')) end--;
        int slash = end;
        while (slash > 0 && result.charAt(slash - 1) == '\\') slash--;
        if ((end - slash) % 2 == 1) result.deleteCharAt(end - 1);
        return result.append("\nallow-external-apps=true\n").toString();
    }

    static boolean externalAppsAllowed(String properties) {
        java.util.Properties parsed = new java.util.Properties();
        try {
            parsed.load(new java.io.StringReader(properties));
            return "true".equalsIgnoreCase(parsed.getProperty("allow-external-apps", ""));
        } catch (java.io.IOException | IllegalArgumentException e) { return false; }
    }

    static final class ExternalAppsNotEnabledException extends java.io.IOException { }

    static String version(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(PACKAGE, 0).versionName;
            return name == null ? "unknown" : name;
        }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return ""; }
    }

    static boolean needsSettingsReload(Context context) {
        String version = version(context);
        boolean cached = !version.matches("(?:v)?0\\.11[0-8](?:\\..*)?");
        return cached && !version.equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("applied_termux_version", ""));
    }

    static void openForSettingsReload(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (launch == null) throw new IllegalStateException();
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        // The official receiver processes reloads only while Termux is visible.
        // Refresh in place: neither recreate the Activity nor stop its service.
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Context app = context.getApplicationContext();
        for (long delay : new long[]{300, 800, 1600, 3000}) handler.postDelayed(() ->
                app.sendBroadcast(new Intent("com.termux.app.reload_style").setPackage(PACKAGE)
                        .putExtra("com.termux.app.TermuxActivity.EXTRA_RECREATE_ACTIVITY", false)), delay);
    }

    static byte[] passwordHash(char[] password) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, "Termux!".getBytes(StandardCharsets.UTF_8), 65536, 160);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    static void configure(Context context, Uri tree, char[] password) throws Exception {
        if (!"com.termux.documents".equals(tree.getAuthority())
                || !HOME.equals(DocumentsContract.getTreeDocumentId(tree))) {
            throw new IllegalArgumentException("Выбери корневую папку Termux, а не её подпапку.");
        }
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, HOME);
        Uri dir = child(context, tree, root, ".termux", true);
        Uri properties = child(context, tree, dir, "termux.properties", false);
        String previous = readProperties(context, properties);
        String enabled = enableExternalApps(previous);
        if (!externalAppsAllowed(enabled)) throw new ExternalAppsNotEnabledException();
        write(context, properties, enabled.getBytes(StandardCharsets.UTF_8));
        if (!externalAppsAllowed(readProperties(context, properties))) throw new ExternalAppsNotEnabledException();
        if (password.length > 0) {
            byte[] hash = passwordHash(password);
            try { write(context, child(context, tree, root, ".termux_authinfo", false), hash); }
            finally { Arrays.fill(hash, (byte) 0); }
        }
    }

    private static String readProperties(Context context, Uri properties) throws Exception {
        try (InputStream stream = context.getContentResolver().openInputStream(properties)) {
            if (stream == null) throw new java.io.IOException();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] block = new byte[4096]; int count;
            while ((count = stream.read(block)) != -1) {
                buffer.write(block, 0, count);
                if (buffer.size() > 65536) throw new IllegalArgumentException("Слишком большой файл настроек Termux.");
            }
            byte[] data = buffer.toByteArray();
            if (data.length > 65536) throw new IllegalArgumentException("Слишком большой файл настроек Termux.");
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static Uri child(Context context, Uri tree, Uri parent, String name, boolean directory) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = context.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new java.io.IOException();
            while (cursor.moveToNext()) if (name.equals(cursor.getString(1)))
                return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
        }
        Uri created = DocumentsContract.createDocument(context.getContentResolver(), parent,
                directory ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream", name);
        if (created == null) throw new java.io.IOException();
        return created;
    }

    private static void write(Context context, Uri uri, byte[] value) throws Exception {
        try (OutputStream stream = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (stream == null) throw new java.io.IOException();
            stream.write(value);
        }
    }

    static void run(Context context) {
        String nonce = UUID.randomUUID().toString();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("last_termux_error").remove("last_exit_code")
                .putString("pending", nonce).putLong("pending_at", System.currentTimeMillis())
                .putString("status", "Подготовка OpenSSH…").apply();
        Intent result = new Intent(context, TermuxResultReceiver.class).setAction("localls.setup." + nonce);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent callback = PendingIntent.getBroadcast(context, 8103, result, flags);
        Intent command = new Intent("com.termux.RUN_COMMAND")
                .setClassName(PACKAGE, "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", SCRIPT})
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL", "0")
                .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "LocalLS: подготовка сервера")
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback);
        try {
            if (context.startService(command) == null) throw new IllegalStateException();
        }
        catch (RuntimeException e) {
            callback.cancel();
            String message = e instanceof SecurityException
                    ? "Android запретил RUN_COMMAND. Разреши выполнение команд Termux в настройках LocalLS → Разрешения и повтори."
                    : "Не удалось запустить RUN_COMMAND. Открой Termux, дождись подготовки среды, вернись в LocalLS и повтори.";
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending")
                    .putString("status", message).apply();
        }
    }
}
