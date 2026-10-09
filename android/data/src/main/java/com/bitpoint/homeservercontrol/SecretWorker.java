package com.bitpoint.homeservercontrol;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Serializes Keystore work across screens without blocking the UI thread. */
final class SecretWorker {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    static void execute(Runnable task) { WORKER.execute(task); }
    private SecretWorker() { }
}
