package com.bitpoint.homeservercontrol;

final class ServerConfig {
    static final String PREFS = "server_config";

    static final String KEY_LOCAL_HOST = "local_host";
    static final String KEY_VPN_HOST = "vpn_host";
    static final String KEY_MODE = "connection_mode";
    static final String KEY_PORT = "port";
    static final String KEY_USER = "user";
    static final String KEY_SAVE_PASSWORD = "save_password";
    static final String KEY_PASSWORD_CIPHER = "password_cipher";
    static final String KEY_TRUSTED_FINGERPRINT_PREFIX = "trusted_fp_";

    static final String KEY_IPERF_TARGET = "iperf_target";

    static final String KEY_AGENT_PORT = "agent_port";
    static final String KEY_SAVE_AGENT_TOKEN = "save_agent_token";
    static final String KEY_AGENT_TOKEN_CIPHER = "agent_token_cipher";

    static final String KEY_BACKGROUND_MONITOR = "background_monitor";
    static final String KEY_CONFIG_VERSION = "config_version";
    static final String KEY_AUTO_FALLBACK = "auto_fallback";
    static final String KEY_LAST_WORKING_HOST = "last_working_host";
    static final String KEY_TRANSFER_LOG = "transfer_log";
    static final String KEY_MONITOR_LAST_ONLINE = "monitor_last_online";
    static final String KEY_MONITOR_HAS_STATE = "monitor_has_state";
    static final String KEY_DISK_HISTORY = "disk_history";

    static final String MODE_LOCAL = "local";
    static final String MODE_VPN = "vpn";

    static final String DEFAULT_LOCAL_HOST = "192.168.1.82";
    static final String DEFAULT_VPN_HOST = "";
    static final int DEFAULT_PORT = 8022;
    static final String DEFAULT_USER = "u0_a606";
    static final int DEFAULT_AGENT_PORT = 8787;

    static final String DEFAULT_REMOTE_PATH = "/data/data/com.termux/files/home/storage/shared";

    private ServerConfig() {}
}
