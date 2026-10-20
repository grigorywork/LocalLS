package com.bitpoint.homeservercontrol;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;

/** Public connection fields only. Imported files never establish trust or carry credentials. */
final class ConnectionProfile {
    final String local, vpn, user, mode;
    final int port, agentPort;
    ConnectionProfile(String local, String vpn, String user, String mode, int port, int agentPort) {
        if (!(ServerConfig.MODE_LOCAL.equals(mode) || ServerConfig.MODE_VPN.equals(mode))
                || (!local.isEmpty() && !ConnectionEndpoint.validHost(local))
                || (!vpn.isEmpty() && !ConnectionEndpoint.validHost(vpn))
                || (ServerConfig.MODE_LOCAL.equals(mode) ? local.isEmpty() : vpn.isEmpty())
                || !ConnectionEndpoint.validUser(user) || port < 1 || port > 65535
                || agentPort < 1 || agentPort > 65535) throw new IllegalArgumentException("Проверь адреса, пользователя и порты");
        this.local=local; this.vpn=vpn; this.user=user; this.mode=mode; this.port=port; this.agentPort=agentPort;
    }
    static ConnectionProfile fromPrefs(SharedPreferences p) {
        return new ConnectionProfile(p.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST),
                p.getString(ServerConfig.KEY_VPN_HOST, ""), p.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER),
                p.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL), p.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT),
                p.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT));
    }
    String encode() throws Exception {
        return new JSONObject().put("format", "LocalLS-connection").put("version", 1).put("local", local)
                .put("vpn", vpn).put("user", user).put("mode", mode).put("port", port).put("agentPort", agentPort).toString(2);
    }
    static ConnectionProfile decode(String text) throws Exception {
        if (text.length() > 8192) throw new IllegalArgumentException("Файл слишком большой");
        org.json.JSONTokener parser = new org.json.JSONTokener(text);
        Object parsed = parser.nextValue();
        if (!(parsed instanceof JSONObject) || parser.nextClean()!=0) throw new IllegalArgumentException("Неверный JSON");
        JSONObject j = (JSONObject) parsed;
        HashSet<String> keys = new HashSet<>(Arrays.asList("format","version","local","vpn","user","mode","port","agentPort"));
        if (j.length()!=keys.size()) throw new IllegalArgumentException("Неверный формат профиля");
        java.util.Iterator<String> iterator=j.keys();
        while(iterator.hasNext()) if(!keys.contains(iterator.next())) throw new IllegalArgumentException("Неизвестное поле");
        if (!"LocalLS-connection".equals(j.get("format")) || !(j.get("version") instanceof Integer) || j.getInt("version")!=1)
            throw new IllegalArgumentException("Неподдерживаемая версия профиля");
        for(String key:new String[]{"local","vpn","user","mode"}) if(!(j.get(key) instanceof String)) throw new IllegalArgumentException("Неверный тип поля");
        for(String key:new String[]{"port","agentPort"}) if(!(j.get(key) instanceof Integer)) throw new IllegalArgumentException("Неверный порт");
        return new ConnectionProfile(j.getString("local"),j.getString("vpn"),j.getString("user"),j.getString("mode"),j.getInt("port"),j.getInt("agentPort"));
    }
    void apply(SharedPreferences p) {
        p.edit().putString(ServerConfig.KEY_LOCAL_HOST,local).putString(ServerConfig.KEY_VPN_HOST,vpn)
                .putString(ServerConfig.KEY_USER,user).putString(ServerConfig.KEY_MODE,mode)
                .putInt(ServerConfig.KEY_PORT,port).putInt(ServerConfig.KEY_AGENT_PORT,agentPort)
                .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN,false).remove(ServerConfig.KEY_AGENT_TOKEN_CIPHER)
                .remove(ServerConfig.KEY_LAST_WORKING_HOST).putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false).apply();
    }
    String summary() {
        return "Local: "+(local.isEmpty()?"—":local)+"\nVPN: "+(vpn.isEmpty()?"—":vpn)+"\nРежим: "+mode
                +"\nПользователь: "+user+"\nSSH: "+port+"\nHTTPS-агент: "+agentPort;
    }
}
