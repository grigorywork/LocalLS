package com.bitpoint.homeservercontrol;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AgentClient {
    private AgentClient() {}

    static AgentResult request(android.content.Context context,String host,int port,String token,String action) {
        return request(host,port,token,action,AgentTls.pin(context,host,port),SshKeys.prefs(context).getBoolean(AgentTls.LEGACY,false));
    }
    static AgentResult request(String host,int port,String token,String action) { return request(host,port,token,action,"",false); }
    static AgentResult request(String host,int port,String token,String action,String pin,boolean legacyHTTP) {
        AgentResult result = new AgentResult();
        HttpURLConnection connection = null;
        try {
            if (!java.util.Arrays.asList("status","start","stop","restart").contains(action) || token==null || token.isEmpty()
                    || token.length()>4096 || token.contains("\r") || token.contains("\n")) throw new Exception("Неверные параметры агента");
            if(!legacyHTTP && (pin==null || !pin.matches("SHA256:[a-f0-9]{64}")))throw new Exception("Подтвердите HTTPS-сертификат агента");
            String method = "status".equals(action) ? "GET" : "POST";
            URL url = new URL(legacyHTTP?"http":"https", host, port, "/" + action);
            connection = (HttpURLConnection) url.openConnection();
            if(!legacyHTTP){javax.net.ssl.HttpsURLConnection https=(javax.net.ssl.HttpsURLConnection)connection;
                https.setSSLSocketFactory(AgentTls.factory(pin,null));
                // Identity is the exact pinned leaf certificate; local/Tailscale names may differ.
                https.setHostnameVerifier((name,session)->true);
            }
            connection.setRequestMethod(method);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(new byte[0]);
            }

            result.httpCode = connection.getResponseCode();
            InputStream input = result.httpCode >= 200 && result.httpCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            try (InputStream response = input) {
                result.body = SecretRedactor.redact(response == null ? "" : readAll(response), token);
            }
            result.success = result.httpCode >= 200 && result.httpCode < 300;
            if (!result.success) { result.error = "Агент отклонил запрос: HTTP " + result.httpCode; result.body=""; }
        } catch (Exception e) {
            result.error = legacyHTTP ? "Агент не выполнил запрос. Проверьте адрес, порт и токен."
                    : "HTTPS-агент недоступен. Проверьте сертификат, TLS, адрес, порт и токен. Токен не отправляется до проверки сертификата.";
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int n;
        while ((n = input.read(buffer)) >= 0) {
            if (out.size() + n > 64 * 1024) throw new Exception("Ответ агента слишком большой");
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
