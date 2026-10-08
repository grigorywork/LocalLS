package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecurePrefs {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "home_server_control_secret_key";

    private SecurePrefs() {}

    static void savePassword(Context context, String password) throws Exception {
        saveSecret(context, ServerConfig.KEY_PASSWORD_CIPHER, password);
    }

    static String loadPassword(Context context) {
        return loadSecret(context, ServerConfig.KEY_PASSWORD_CIPHER);
    }

    static void clearPassword(Context context) {
        clearSecret(context, ServerConfig.KEY_PASSWORD_CIPHER);
    }

    static void saveAgentToken(Context context, String token) throws Exception {
        saveSecret(context, ServerConfig.KEY_AGENT_TOKEN_CIPHER, token);
    }

    static String loadAgentToken(Context context) {
        return loadSecret(context, ServerConfig.KEY_AGENT_TOKEN_CIPHER);
    }

    static void clearAgentToken(Context context) {
        clearSecret(context, ServerConfig.KEY_AGENT_TOKEN_CIPHER);
    }

    private static void saveSecret(Context context, String prefKey, String value) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        prefs(context).edit().putString(prefKey, payload).apply();
    }

    private static String loadSecret(Context context, String prefKey) {
        try {
            String payload = prefs(context).getString(prefKey, "");
            if (payload == null || payload.isEmpty() || !payload.contains(":")) return "";
            String[] parts = payload.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);

            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(ALIAS, null);
            if (key == null) return "";

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void clearSecret(Context context, String prefKey) {
        prefs(context).edit().remove(prefKey).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
