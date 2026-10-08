package com.bitpoint.homeservercontrol;

import android.content.Context;
import org.json.JSONObject;

/** Public connection parameters only. Import never silently replaces an existing trusted host key. */
final class ServerSetupProfile {
    static JSONObject create(String host, int port, String user, String fingerprint) throws Exception {
        return new JSONObject().put("format", "localls-server-v1").put("host", host).put("port", port)
                .put("user", user).put("folder", TermuxSetup.HOME + "/LocalLS").put("fingerprint", fingerprint);
    }
    static void validate(JSONObject profile) throws Exception {
        if (!"localls-server-v1".equals(profile.optString("format"))
                || !profile.optString("host").matches("[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}")
                || profile.optInt("port") < 1 || profile.optInt("port") > 65535
                || !profile.optString("user").matches("[a-zA-Z0-9_]{1,64}")
                || !profile.optString("fingerprint").matches("SHA256:[A-Za-z0-9+/]{43}"))
            throw new IllegalArgumentException("Это не корректный профиль LocalLS.");
    }
    static void apply(Context context, JSONObject profile) throws Exception {
        validate(profile);
        String host = profile.getString("host"), fingerprint = profile.getString("fingerprint");
        int port = profile.getInt("port");
        String previous = HostTrustStore.get(context, host, port);
        if (!previous.isEmpty() && !previous.equals(fingerprint))
            throw new IllegalArgumentException("Ключ отличается от уже доверенного. Импорт заблокирован.");
        HostTrustStore.trust(context, host, port, fingerprint);
        SecurePrefs.clearPassword(context);
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit()
                .putString(ServerConfig.KEY_LOCAL_HOST, host).putInt(ServerConfig.KEY_PORT, port)
                .putString(ServerConfig.KEY_USER, profile.getString("user"))
                .putString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL)
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD, false)
                .putString("last_remote_path", TermuxSetup.HOME + "/LocalLS").apply();
    }
}
