package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Небольшая миграция настроек между версиями приложения.
 * Секреты не переносятся в открытый текст и не логируются.
 */
final class ConfigMigrator {
    static final int CURRENT_VERSION = 2;

    private ConfigMigrator() {}

    static void migrate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        int version = prefs.getInt(ServerConfig.KEY_CONFIG_VERSION, 0);
        SharedPreferences.Editor edit = prefs.edit();

        if (version < 1) {
            if (TextUtils.isEmpty(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""))) {
                edit.putString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST);
            }
            if (TextUtils.isEmpty(prefs.getString(ServerConfig.KEY_USER, ""))) {
                edit.putString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER);
            }
            int port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
            if (port < 1 || port > 65535) edit.putInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
            int agentPort = prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT);
            if (agentPort < 1 || agentPort > 65535) {
                edit.putInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT);
            }
            String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
            if (!ServerConfig.MODE_LOCAL.equals(mode) && !ServerConfig.MODE_VPN.equals(mode)) {
                edit.putString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
            }
            // На случай очень ранних экспериментальных сборок: не оставляем plaintext-секреты.
            edit.remove("password").remove("agent_token").remove("plain_password");
            version = 1;
        }

        if (version < 2) {
            edit.putBoolean(ServerConfig.KEY_AUTO_FALLBACK,
                    prefs.getBoolean(ServerConfig.KEY_AUTO_FALLBACK, false));
            edit.putString(ServerConfig.KEY_LAST_WORKING_HOST,
                    safeTrim(prefs.getString(ServerConfig.KEY_LAST_WORKING_HOST, "")));
            version = 2;
        }

        edit.putInt(ServerConfig.KEY_CONFIG_VERSION, version).apply();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
