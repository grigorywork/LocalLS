package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Identity;
import com.jcraft.jsch.KeyPair;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

final class SshKeys {
    static final String MODE = "ssh_auth_method";
    static final String REVISION = "ssh_key_revision";
    static final String PUBLIC = "publicKey", PRIVATE = "privateKey", PHRASE = "passphrase";
    private SshKeys() {}
    static boolean keyMode(Context c) { return "key".equals(prefs(c).getString(MODE, "password")); }
    static SshCredentials credentials(Context c, String host, int port, String user, String password) {
        if (!keyMode(c)) return SshCredentials.password(password);
        return keyCredentials(c, host, port, user);
    }
    static SshCredentials keyCredentials(Context c, String host, int port, String user) {
        try { JSONObject obj = new JSONObject(SecurePrefs.loadNamed(c, keyId(host, port, user)));
            return SshCredentials.key(obj.getString(PRIVATE), obj.optString(PHRASE));
        } catch (Exception e) { return SshCredentials.key("", ""); }
    }
    static JSONObject inspect(String privateKey, String passphrase) throws Exception {
        if (privateKey == null || privateKey.getBytes(StandardCharsets.UTF_8).length > 128 * 1024
                || passphrase == null || passphrase.length() > 4096) throw new Exception("Ключ не распознан");
        JSch jsch = new JSch();
        byte[] raw = privateKey.getBytes(StandardCharsets.UTF_8), phrase = passphrase.getBytes(StandardCharsets.UTF_8);
        try {
            jsch.addIdentity("LocalLS", raw, null, phrase);
            if (jsch.getIdentityRepository().getIdentities().size() != 1) throw new Exception("Один приватный ключ требуется");
            Identity identity = jsch.getIdentityRepository().getIdentities().get(0);
            if (identity.isEncrypted()) throw new Exception("Неверна парольная фраза");
            String type = identity.getAlgName();
            if (!type.equals("ssh-rsa") && !type.equals("ssh-ed25519") && !type.startsWith("ecdsa-sha2-nistp"))
                throw new Exception("Тип ключа не поддерживается");
            byte[] blob = identity.getPublicKeyBlob();
            if (type.equals("ssh-rsa")) {
                ByteBuffer b = ByteBuffer.wrap(blob); byte[] n = null;
                for (int i = 0; i < 3; i++) { int length = b.getInt(); if (length < 1 || length > b.remaining()) throw new Exception("Ключ не распознан"); n = new byte[length]; b.get(n); }
                if (new java.math.BigInteger(1, n).bitLength() < 3072) throw new Exception("RSA должен быть не менее 3072 бит");
            }
            return new JSONObject().put(PUBLIC, type + " " + Base64.encodeToString(blob, Base64.NO_WRAP) + " LocalLS")
                    .put("fingerprint", "SHA256:" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(blob), Base64.NO_WRAP | Base64.NO_PADDING));
        } finally { Arrays.fill(raw, (byte) 0); Arrays.fill(phrase, (byte) 0); jsch.removeAllIdentity(); }
    }
    static JSONObject generate() throws Exception {
        JSch jsch = new JSch(); KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072);
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); pair.writePrivateKey(out);
            String privateKey = out.toString(StandardCharsets.UTF_8.name());
            JSONObject info = inspect(privateKey, ""); return info.put(PRIVATE, privateKey).put(PHRASE, "");
        } finally { pair.dispose(); }
    }
    static void save(Context c, String host, int port, String user, JSONObject value) throws Exception {
        inspect(value.getString(PRIVATE), value.optString(PHRASE));
        SecurePrefs.saveNamed(c, keyId(host, port, user), value.toString());
        prefs(c).edit().putLong(REVISION, System.currentTimeMillis()).apply();
    }
    static JSONObject info(Context c, String host, int port, String user) {
        try { JSONObject obj = new JSONObject(SecurePrefs.loadNamed(c, keyId(host, port, user))); return inspect(obj.getString(PRIVATE), obj.optString(PHRASE)); }
        catch (Exception | LinkageError e) { return new JSONObject(); }
    }
    static void clear(Context c, String host, int port, String user) { SecurePrefs.clearNamed(c, keyId(host, port, user)); prefs(c).edit().putLong(REVISION, System.currentTimeMillis()).apply(); }
    private static String keyId(String host, int port, String user) {
        try { String value = host.trim().toLowerCase(java.util.Locale.ROOT) + ":" + port + "@" + user;
            return "ssh_identity_" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP | Base64.URL_SAFE); }
        catch (Exception e) { throw new IllegalStateException("Не удалось определить профиль"); }
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE); }
}
