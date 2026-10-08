package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewGroup;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FilesActivity extends BaseActivity {
    private static final int REQ_UPLOAD = 501;
    private static final int REQ_DOWNLOAD = 502;
    private static final String KEY_LAST_REMOTE_PATH = "last_remote_path";
    private static final int MAX_TRANSFER_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 1500L;

    private TextView pathText, transferStatusText, emptyText;
    private ProgressBar transferProgressBar;
    private Button cancelTransferButton, clearQueueButton;
    private ListView fileList;
    private final List<RemoteEntry> entries = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private final ExecutorService browseExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService transferExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger queuedTransfers = new AtomicInteger(0);
    private final CopyOnWriteArrayList<TransferJob> transferJobs = new CopyOnWriteArrayList<>();
    private volatile TransferJob currentTransfer;
    private volatile boolean closing;
    private TransferPowerGuard powerGuard;
    private LiveSpeedPanel liveSpeedPanel;

    private String host;
    private int port;
    private String user;
    private String password;
    private String fingerprint;
    private String currentPath;
    private RemoteEntry pendingDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        setContentView(R.layout.activity_files);
        powerGuard = new TransferPowerGuard(this);
        bindViews();
        liveSpeedPanel = new LiveSpeedPanel(findViewById(R.id.filesSpeedChart), findViewById(R.id.filesSpeedSummary));
        loadConnection();
        wireActions();
        if (TextUtils.isEmpty(password)) promptPassword();
        else refresh();
    }

    private void bindViews() {
        pathText = findViewById(R.id.remotePathText);
        transferStatusText = findViewById(R.id.transferStatusText);
        transferProgressBar = findViewById(R.id.transferProgressBar);
        cancelTransferButton = findViewById(R.id.filesCancelTransferButton);
        clearQueueButton = findViewById(R.id.filesClearQueueButton);
        emptyText = findViewById(R.id.emptyText);
        fileList = findViewById(R.id.remoteFileList);
        adapter = new ArrayAdapter<String>(this, R.layout.row_remote_file, R.id.remoteFileLabel, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                if (position < entries.size()) {
                    RemoteEntry entry = entries.get(position);
                    ((ImageView) row.findViewById(R.id.remoteFileIcon)).setImageResource(
                            entry.directory ? R.drawable.ic_folder : R.drawable.ic_file);
                    SpannableString label = new SpannableString(labels.get(position));
                    int metadata = label.toString().indexOf('\n');
                    if (metadata >= 0) {
                        label.setSpan(new RelativeSizeSpan(0.85f), metadata + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        label.setSpan(new ForegroundColorSpan(getColor(R.color.text_secondary)), metadata + 1,
                                label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    ((TextView) row.findViewById(R.id.remoteFileLabel)).setText(label);
                }
                return row;
            }
        };
        fileList.setAdapter(adapter);
        fileList.setEmptyView(emptyText);
        renderQueueState();
    }

    private void loadConnection() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        host = ConnectionSelector.hostForFiles(this);
        port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        user = prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER);
        password = SessionPassword.take();
        if (TextUtils.isEmpty(password) && prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false)) {
            password = SecurePrefs.loadPassword(this);
        }
        fingerprint = HostTrustStore.get(this, host, port);
        currentPath = prefs.getString(KEY_LAST_REMOTE_PATH, ServerConfig.DEFAULT_REMOTE_PATH);
    }

    private void wireActions() {
        findViewById(R.id.filesBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.filesRefreshButton).setOnClickListener(v -> refresh());
        findViewById(R.id.filesUpButton).setOnClickListener(v -> {
            String parent = SftpClient.parent(currentPath);
            if (!parent.equals(currentPath)) {
                currentPath = parent;
                refresh();
            }
        });
        findViewById(R.id.filesUploadButton).setOnClickListener(v -> chooseUploads());
        findViewById(R.id.filesMkdirButton).setOnClickListener(v -> promptCreateFolder());
        cancelTransferButton.setOnClickListener(v -> cancelCurrentTransfer());
        clearQueueButton.setOnClickListener(v -> clearQueuedTransfers());

        fileList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= entries.size()) return;
            RemoteEntry entry = entries.get(position);
            if (entry.directory) {
                currentPath = entry.path;
                refresh();
            } else {
                chooseDownloadDestination(entry);
            }
        });
        fileList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= entries.size()) return true;
            showEntryActions(entries.get(position));
            return true;
        });
    }

    private void promptPassword() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSaveEnabled(false);
        input.setHint("Пароль SSH");
        new AlertDialog.Builder(this)
                .setTitle("Пароль сервера")
                .setMessage("Пароль нужен только для этой сессии и не будет сохранён.")
                .setView(input)
                .setCancelable(false)
                .setNegativeButton("Назад", (d, w) -> finish())
                .setPositiveButton("Подключиться", (d, w) -> {
                    password = input.getText().toString();
                    refresh();
                })
                .show();
    }

    private void refresh() {
        if (closing || !validConnection()) return;
        pathText.setText(currentPath);
        emptyText.setText("Загрузка…");
        findViewById(R.id.filesRefreshButton).setEnabled(false);
        final String path = currentPath;
        browseExecutor.execute(() -> {
            try {
                String usedHost = host;
                String usedFingerprint = fingerprint;
                List<RemoteEntry> loaded;
                try {
                    loaded = SftpClient.list(usedHost, port, user, password, usedFingerprint, path);
                } catch (Exception primaryError) {
                    String alternate = ConnectionSelector.alternateHost(this);
                    boolean canFallback = ConnectionSelector.autoFallback(this)
                            && queuedTransfers.get() == 0
                            && !TextUtils.isEmpty(alternate)
                            && !alternate.equals(usedHost);
                    if (!canFallback) throw primaryError;

                    String alternateFingerprint = HostTrustStore.get(this, alternate, port);
                    try {
                        loaded = SftpClient.list(alternate, port, user, password, alternateFingerprint, path);
                        usedHost = alternate;
                        usedFingerprint = alternateFingerprint;
                    } catch (Exception alternateError) {
                        throw new Exception("Основной: " + safeMessage(primaryError)
                                + "; резервный: " + safeMessage(alternateError));
                    }
                }

                final List<RemoteEntry> finalLoaded = loaded;
                final String finalHost = usedHost;
                final String finalFingerprint = usedFingerprint;
                ConnectionSelector.rememberWorkingHost(this, finalHost);
                runOnUiThread(() -> {
                    if (!path.equals(currentPath)) return;
                    boolean switched = !finalHost.equals(host);
                    host = finalHost;
                    fingerprint = finalFingerprint;
                    entries.clear();
                    entries.addAll(finalLoaded);
                    rebuildLabels();
                    pathText.setText(currentPath + (switched ? "\nчерез " + host : ""));
                    emptyText.setText("Папка пустая");
                    findViewById(R.id.filesRefreshButton).setEnabled(true);
                    getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_LAST_REMOTE_PATH, currentPath).apply();
                    if (switched) Toast.makeText(this, "SFTP переключён на резервный host: " + host, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (path.equals(currentPath)) {
                        emptyText.setText("Не удалось открыть папку");
                        findViewById(R.id.filesRefreshButton).setEnabled(true);
                    }
                    Toast.makeText(this, "SFTP: " + safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void rebuildLabels() {
        labels.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        for (RemoteEntry entry : entries) {
            if (entry.directory) {
                labels.add(entry.name + "\nПапка");
            } else {
                String when = entry.mtime > 0 ? sdf.format(new Date(entry.mtime * 1000L)) : "";
                labels.add(entry.name + "\n" + formatBytes(entry.size) + (when.isEmpty() ? "" : " • " + when));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void chooseUploads() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_UPLOAD);
    }

    private void chooseDownloadDestination(RemoteEntry entry) {
        pendingDownload = entry;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, entry.name);
        startActivityForResult(intent, REQ_DOWNLOAD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_UPLOAD) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    enqueueUpload(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                enqueueUpload(data.getData());
            }
        } else if (requestCode == REQ_DOWNLOAD && data.getData() != null && pendingDownload != null) {
            RemoteEntry entry = pendingDownload;
            pendingDownload = null;
            enqueueDownload(entry, data.getData());
        }
    }

    private void enqueueUpload(Uri uri) {
        persistUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final String name = displayName(uri);
        final long size = displaySize(uri);
        final String remotePath = SftpClient.join(currentPath, name);
        final TransferJob job = new TransferJob("На сервер: " + name, size);
        transferJobs.add(job);
        if (queuedTransfers.incrementAndGet() == 1) {
            if (powerGuard != null) powerGuard.acquire();
            TransferForegroundService.start(this);
        }
        renderQueueState();

        transferExecutor.execute(() -> {
            if (job.cancelled) {
                finishTransfer(job, true, null);
                return;
            }
            job.started = true;
            job.startedAtMs = System.currentTimeMillis();
            currentTransfer = job;
            TransferSpeedStore.begin(job);
            runOnUiThread(() -> showProgress(job, 0L, size));
            Exception failure = null;
            for (int attempt = 1; attempt <= MAX_TRANSFER_ATTEMPTS && !job.cancelled; attempt++) {
                job.attempt = attempt;
                job.resetSpeedWindow();
                if (attempt > 1) {
                    runOnUiThread(() -> transferStatusText.setText(job.label + " • повторное подключение…"));
                    if (!sleepRetry(job)) break;
                }
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("Не удалось открыть выбранный файл");
                    SftpClient.upload(host, port, user, password, fingerprint, in, remotePath, size,
                            (bytes, total) -> onTransferProgress(job, bytes, total));
                    failure = null;
                    break;
                } catch (Exception e) {
                    failure = e;
                }
            }
            if (failure == null && !job.cancelled) {
                runOnUiThread(() -> Toast.makeText(this, "Загружено: " + name, Toast.LENGTH_SHORT).show());
            }
            finishTransfer(job, job.cancelled, failure);
        });
    }

    private void enqueueDownload(RemoteEntry entry, Uri destination) {
        persistUriPermission(destination, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        final TransferJob job = new TransferJob("С сервера: " + entry.name, entry.size);
        transferJobs.add(job);
        if (queuedTransfers.incrementAndGet() == 1) {
            if (powerGuard != null) powerGuard.acquire();
            TransferForegroundService.start(this);
        }
        renderQueueState();

        transferExecutor.execute(() -> {
            if (job.cancelled) {
                finishTransfer(job, true, null);
                return;
            }
            job.started = true;
            job.startedAtMs = System.currentTimeMillis();
            currentTransfer = job;
            TransferSpeedStore.begin(job);
            runOnUiThread(() -> showProgress(job, 0L, entry.size));
            Exception failure = null;
            for (int attempt = 1; attempt <= MAX_TRANSFER_ATTEMPTS && !job.cancelled; attempt++) {
                job.attempt = attempt;
                job.resetSpeedWindow();
                if (attempt > 1) {
                    runOnUiThread(() -> transferStatusText.setText(job.label + " • повторное подключение…"));
                    if (!sleepRetry(job)) break;
                }
                try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                    if (out == null) throw new Exception("Не удалось открыть файл назначения");
                    SftpClient.download(host, port, user, password, fingerprint, entry.path, out, entry.size,
                            (bytes, total) -> onTransferProgress(job, bytes, total));
                    failure = null;
                    break;
                } catch (Exception e) {
                    failure = e;
                }
            }
            if (failure == null && !job.cancelled) {
                runOnUiThread(() -> Toast.makeText(this, "Скачано: " + entry.name, Toast.LENGTH_SHORT).show());
            }
            finishTransfer(job, job.cancelled, failure);
        });
    }

    private boolean onTransferProgress(TransferJob job, long bytes, long total) {
        if (job.cancelled) return false;
        if (powerGuard != null) powerGuard.acquire();
        long now = System.currentTimeMillis();
        job.transferred = bytes;
        if (job.lastSpeedAtMs > 0L && now > job.lastSpeedAtMs && bytes >= job.lastSpeedBytes) {
            double instant = (bytes - job.lastSpeedBytes) * 1000.0 / (now - job.lastSpeedAtMs);
            job.smoothedBytesPerSecond = job.smoothedBytesPerSecond <= 0.0
                    ? instant : (job.smoothedBytesPerSecond * 0.70 + instant * 0.30);
        }
        job.lastSpeedAtMs = now;
        job.lastSpeedBytes = bytes;
        double measured = job.smoothedBytesPerSecond > 0 ? job.smoothedBytesPerSecond
                : bytes * 1000.0 / Math.max(1, now - job.startedAtMs);
        TransferSpeedStore.record(job, measured);
        if (now - job.lastUiUpdateMs >= 250L || (total > 0 && bytes >= total)) {
            job.lastUiUpdateMs = now;
            runOnUiThread(() -> showProgress(job, bytes, total));
        }
        return !job.cancelled;
    }

    private void showProgress(TransferJob job, long bytes, long total) {
        if (currentTransfer != job) return;
        long effectiveTotal = total > 0 ? total : job.totalBytes;
        long elapsedMs = Math.max(1L, System.currentTimeMillis() - job.startedAtMs);
        double averageBytesPerSecond = bytes * 1000.0 / elapsedMs;
        double bytesPerSecond = job.smoothedBytesPerSecond > 0.0
                ? job.smoothedBytesPerSecond : averageBytesPerSecond;
        double mbPerSecond = bytesPerSecond / (1024.0 * 1024.0);

        StringBuilder text = new StringBuilder(job.label).append(" • ").append(formatBytes(bytes));
        if (job.attempt > 1) text.append(" • попытка ").append(job.attempt).append('/').append(MAX_TRANSFER_ATTEMPTS);
        if (effectiveTotal > 0) {
            int percent = (int) Math.min(100L, (bytes * 100L) / effectiveTotal);
            transferProgressBar.setIndeterminate(false);
            transferProgressBar.setProgress(percent);
            text.append(" / ").append(formatBytes(effectiveTotal)).append(" • ").append(percent).append('%');
            if (bytesPerSecond > 1.0 && bytes < effectiveTotal) {
                long etaSeconds = (long) ((effectiveTotal - bytes) / bytesPerSecond);
                text.append(" • ETA ").append(formatDuration(etaSeconds));
            }
        } else {
            transferProgressBar.setIndeterminate(true);
        }
        if (elapsedMs >= 500L) {
            text.append(String.format(Locale.getDefault(), " • %.2f МБ/с", mbPerSecond));
        }
        int queued = Math.max(0, queuedTransfers.get() - 1);
        if (queued > 0) text.append(" • ещё ").append(queued).append(" в очереди");
        transferStatusText.setText(text.toString());
        cancelTransferButton.setEnabled(true);
        clearQueueButton.setEnabled(queued > 0);
    }

    private void finishTransfer(TransferJob job, boolean cancelled, Exception failure) {
        runOnUiThread(() -> {
            if (closing) return;
            TransferSpeedStore.end(job, cancelled ? "Отменено" : failure != null ? "Ошибка передачи" : "Завершено");
            transferJobs.remove(job);
            int remainingTransfers = queuedTransfers.updateAndGet(value -> Math.max(0, value - 1));
            if (remainingTransfers == 0) {
                if (powerGuard != null) powerGuard.release();
                TransferForegroundService.stop(this);
            }
            if (currentTransfer == job) currentTransfer = null;
            if (cancelled) {
                TransferLogStore.add(this, job.label, "отменено");
                Toast.makeText(this, "Передача отменена: " + job.label, Toast.LENGTH_SHORT).show();
            } else if (failure != null) {
                TransferLogStore.add(this, job.label, "ошибка: " + safeMessage(failure));
                Toast.makeText(this, "Ошибка передачи: " + safeMessage(failure), Toast.LENGTH_LONG).show();
            } else {
                long elapsedMs = Math.max(1L, System.currentTimeMillis() - job.startedAtMs);
                double mbPerSecond = (job.transferred * 1000.0 / elapsedMs) / (1024.0 * 1024.0);
                String result = "готово • " + formatBytes(job.transferred)
                        + String.format(Locale.getDefault(), " • %.2f МБ/с", mbPerSecond);
                if (job.attempt > 1) result += " • попытка " + job.attempt;
                MetricHistoryStore.addSpeed(this, job.transferred * 8000.0 / elapsedMs / 1_000_000.0);
                TransferLogStore.add(this, job.label, result);
            }
            renderQueueState();
            if (queuedTransfers.get() == 0) refresh();
        });
    }

    private void cancelCurrentTransfer() {
        TransferJob job = currentTransfer;
        if (job == null) {
            Toast.makeText(this, "Сейчас ничего не передаётся", Toast.LENGTH_SHORT).show();
            return;
        }
        job.cancelled = true;
        transferStatusText.setText("Отмена текущей передачи…");
        cancelTransferButton.setEnabled(false);
    }

    private void clearQueuedTransfers() {
        int cancelled = 0;
        for (TransferJob job : transferJobs) {
            if (!job.started && !job.cancelled) {
                job.cancelled = true;
                cancelled++;
            }
        }
        Toast.makeText(this, cancelled == 0 ? "Очередь уже пуста" : "Отменено в очереди: " + cancelled,
                Toast.LENGTH_SHORT).show();
        renderQueueState();
    }

    private void renderQueueState() {
        TransferJob active = currentTransfer;
        int count = queuedTransfers.get();
        if (active == null) {
            transferProgressBar.setIndeterminate(false);
            transferProgressBar.setProgress(0);
            transferStatusText.setText(count == 0 ? "Очередь пуста" : "В очереди: " + count);
            cancelTransferButton.setEnabled(false);
        }
        int waiting = 0;
        for (TransferJob job : transferJobs) {
            if (!job.started && !job.cancelled) waiting++;
        }
        clearQueueButton.setEnabled(waiting > 0);
    }

    private void promptCreateFolder() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Имя новой папки");
        new AlertDialog.Builder(this)
                .setTitle("Создать папку")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = validFileName(input.getText().toString());
                    if (name == null) return;
                    runFileOperation("Создание папки", () ->
                            SftpClient.mkdir(host, port, user, password, fingerprint, SftpClient.join(currentPath, name)));
                })
                .show();
    }

    private void showEntryActions(RemoteEntry entry) {
        List<String> actions = new ArrayList<>();
        if (entry.directory) actions.add("Открыть");
        else actions.add("Скачать");
        actions.add("Переименовать");
        actions.add("Удалить");

        new AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Открыть".equals(action)) {
                        currentPath = entry.path;
                        refresh();
                    } else if ("Скачать".equals(action)) {
                        chooseDownloadDestination(entry);
                    } else if ("Переименовать".equals(action)) {
                        promptRename(entry);
                    } else if ("Удалить".equals(action)) {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void promptRename(RemoteEntry entry) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(entry.name);
        input.setSelection(entry.name.length());
        new AlertDialog.Builder(this)
                .setTitle("Переименовать")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Переименовать", (d, w) -> {
                    String name = validFileName(input.getText().toString());
                    if (name == null || name.equals(entry.name)) return;
                    String target = SftpClient.join(SftpClient.parent(entry.path), name);
                    runFileOperation("Переименование", () ->
                            SftpClient.rename(host, port, user, password, fingerprint, entry.path, target));
                })
                .show();
    }

    private void confirmDelete(RemoteEntry entry) {
        String extra = entry.directory
                ? "\n\nДля безопасности приложение удаляет только пустые папки."
                : "";
        new AlertDialog.Builder(this)
                .setTitle("Удалить?")
                .setMessage(entry.name + extra)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> runFileOperation("Удаление", () ->
                        SftpClient.delete(host, port, user, password, fingerprint, entry)))
                .show();
    }

    private void runFileOperation(String title, CheckedAction action) {
        if (!validConnection()) return;
        browseExecutor.execute(() -> {
            try {
                action.run();
                runOnUiThread(() -> {
                    Toast.makeText(this, title + ": готово", Toast.LENGTH_SHORT).show();
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, title + ": " + safeMessage(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String validFileName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name) || name.contains("/") || name.contains("\\")) {
            Toast.makeText(this, "Некорректное имя", Toast.LENGTH_SHORT).show();
            return null;
        }
        return name;
    }

    private void persistUriPermission(Uri uri, int modeFlag) {
        try {
            getContentResolver().takePersistableUriPermission(uri, modeFlag);
        } catch (Exception ignored) {
            // Некоторые провайдеры не поддерживают persistable permission — текущая сессия всё равно работает.
        }
    }

    private boolean sleepRetry(TransferJob job) {
        long deadline = System.currentTimeMillis() + RETRY_DELAY_MS;
        while (!job.cancelled && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !job.cancelled;
    }

    private boolean validConnection() {
        if (TextUtils.isEmpty(host) || port <= 0 || TextUtils.isEmpty(user) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Не хватает данных подключения", Toast.LENGTH_LONG).show();
            return false;
        }
        if (TextUtils.isEmpty(fingerprint)) {
            Toast.makeText(this, "Сначала подтверди fingerprint на главном экране", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private String displayName(Uri uri) {
        String name = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (TextUtils.isEmpty(name)) name = "upload_" + System.currentTimeMillis();
        return name.replace("/", "_").replace("\\", "_");
    }

    private long displaySize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return Math.max(0L, cursor.getLong(index));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0L;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(Locale.getDefault(), "%.1f КБ", value);
        value /= 1024.0;
        if (value < 1024) return String.format(Locale.getDefault(), "%.1f МБ", value);
        value /= 1024.0;
        return String.format(Locale.getDefault(), "%.2f ГБ", value);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " с";
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) return minutes + " мин " + rest + " с";
        long hours = minutes / 60;
        return hours + " ч " + (minutes % 60) + " мин";
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName()
                : SecretRedactor.redact(message, password);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (liveSpeedPanel != null) liveSpeedPanel.start();
    }

    @Override
    protected void onPause() {
        if (liveSpeedPanel != null) liveSpeedPanel.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closing = true;
        if (liveSpeedPanel != null) liveSpeedPanel.stop();
        TransferSpeedStore.end(currentTransfer, "Отменено");
        for (TransferJob job : transferJobs) job.cancelled = true;
        browseExecutor.shutdownNow();
        transferExecutor.shutdownNow();
        if (powerGuard != null) powerGuard.release();
        TransferForegroundService.stop(this);
        super.onDestroy();
    }

    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class TransferJob {
        final String label;
        final long totalBytes;
        volatile boolean started;
        volatile boolean cancelled;
        volatile long transferred;
        volatile long startedAtMs;
        volatile long lastUiUpdateMs;
        volatile int attempt = 1;
        volatile long lastSpeedBytes;
        volatile long lastSpeedAtMs;
        volatile double smoothedBytesPerSecond;

        void resetSpeedWindow() {
            transferred = 0L;
            lastUiUpdateMs = 0L;
            lastSpeedBytes = 0L;
            lastSpeedAtMs = 0L;
            smoothedBytesPerSecond = 0.0;
            startedAtMs = System.currentTimeMillis();
        }

        TransferJob(String label, long totalBytes) {
            this.label = label;
            this.totalBytes = totalBytes;
        }
    }
}
