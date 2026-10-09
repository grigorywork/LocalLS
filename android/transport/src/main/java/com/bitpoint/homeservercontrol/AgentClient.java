package com.bitpoint.homeservercontrol;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AgentClient {
    private AgentClient() {}

    static AgentResult request(String host, int port, String token, String action) {
        AgentResult result = new AgentResult();
        HttpURLConnection connection = null;
        try {
            String method = "status".equals(action) ? "GET" : "POST";
            URL url = new URL("http", host, port, "/" + action);
            connection = (HttpURLConnection) url.openConnection();
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
            if (!result.success) result.error = "HTTP " + result.httpCode + ": " + result.body;
        } catch (Exception e) {
            result.error = e.getMessage() == null ? e.getClass().getSimpleName()
                    : SecretRedactor.redact(e.getMessage(), token);
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
