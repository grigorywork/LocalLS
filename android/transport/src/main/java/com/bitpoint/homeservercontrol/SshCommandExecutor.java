package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** One bounded SSH command. stderr is drained but never exposed to the UI. */
final class SshCommandExecutor {
    private static final int MAX_BYTES = 64 * 1024;

    static final class Result {
        final String output;
        final int exitCode;
        Result(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }

    private SshCommandExecutor() { }

    static Result execute(Session session, String command, int timeoutMs) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try {
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(4000);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int received = 0;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1000, timeoutMs));
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (System.nanoTime() >= deadline) throw new java.net.SocketTimeoutException("SSH command timed out");
                // Read one block per stream per iteration, so neither stderr nor a
                // continuous stdout stream can starve cancellation or the deadline.
                received += drain(stdout, output, buffer);
                received += drain(stderr, null, buffer);
                if (received > MAX_BYTES) throw new java.io.IOException("SSH response exceeds limit");
                if (channel.isClosed() && stdout.available() == 0 && stderr.available() == 0)
                    return new Result(output.toString(StandardCharsets.UTF_8.name()), channel.getExitStatus());
                if (stdout.available() == 0 && stderr.available() == 0) Thread.sleep(20);
            }
        } finally {
            channel.disconnect();
        }
    }

    private static int drain(InputStream stream, ByteArrayOutputStream output, byte[] buffer) throws Exception {
        int available = stream.available();
        if (available == 0) return 0;
        int count = stream.read(buffer, 0, Math.min(available, buffer.length));
        if (count <= 0) return 0;
        if (output != null) output.write(buffer, 0, count);
        return count;
    }
}
