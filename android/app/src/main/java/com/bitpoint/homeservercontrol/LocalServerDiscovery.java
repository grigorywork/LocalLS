package com.bitpoint.homeservercontrol;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Local /24 discovery for a Termux SSH endpoint. No Wi-Fi scan/location permission is used. */
final class LocalServerDiscovery {
    private static final int CONNECT_TIMEOUT_MS = 220;
    private static final int MAX_WORKERS = 32;

    private LocalServerDiscovery() {}

    static Result discover(int port) {
        String localIp = localIpv4();
        if (localIp == null) return new Result(localIp, Collections.emptyList(),
                "Не удалось определить IPv4 этого телефона. Проверь Wi-Fi.");

        int lastDot = localIp.lastIndexOf('.');
        if (lastDot <= 0) return new Result(localIp, Collections.emptyList(),
                "Не удалось определить локальную /24 сеть.");
        String prefix = localIp.substring(0, lastDot + 1);

        ExecutorService pool = Executors.newFixedThreadPool(MAX_WORKERS);
        ExecutorCompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        try {
            for (int i = 1; i <= 254; i++) {
                final String candidate = prefix + i;
                if (candidate.equals(localIp)) continue;
                completion.submit(new PortProbe(candidate, port));
                submitted++;
            }

            List<String> hits = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            for (int i = 0; i < submitted; i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<String> future = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) break;
                try {
                    String hit = future.get();
                    if (hit != null) hits.add(hit);
                } catch (Exception ignored) {
                }
            }
            Collections.sort(hits, LocalServerDiscovery::compareIpv4);
            String message = hits.isEmpty()
                    ? "SSH-порт " + port + " в сети " + prefix + "0/24 не найден."
                    : "Найдено устройств с открытым портом " + port + ": " + hits.size();
            return new Result(localIp, hits, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(localIp, Collections.emptyList(), "Поиск отменён.");
        } finally {
            pool.shutdownNow();
        }
    }

    private static String localIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                String name = network.getName() == null ? "" : network.getName().toLowerCase(java.util.Locale.ROOT);
                // Prefer Android Wi-Fi interfaces and avoid Tailscale/tun when possible.
                if (!(name.startsWith("wlan") || name.startsWith("wifi"))) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            // Fallback: any site-local IPv4 if OEM uses a nonstandard Wi-Fi interface name.
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int compareIpv4(String a, String b) {
        return Integer.compare(lastOctet(a), lastOctet(b));
    }

    private static int lastOctet(String value) {
        try {
            return Integer.parseInt(value.substring(value.lastIndexOf('.') + 1));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static final class PortProbe implements Callable<String> {
        private final String host;
        private final int port;

        PortProbe(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override public String call() {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(350);
                InputStream in = socket.getInputStream();
                byte[] banner = new byte[48];
                int n = in.read(banner);
                if (n <= 0) return null;
                String greeting = new String(banner, 0, n, StandardCharsets.US_ASCII);
                return greeting.startsWith("SSH-") ? host : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    static final class Result {
        final String localIp;
        final List<String> hosts;
        final String message;

        Result(String localIp, List<String> hosts, String message) {
            this.localIp = localIp;
            this.hosts = hosts;
            this.message = message;
        }
    }
}
