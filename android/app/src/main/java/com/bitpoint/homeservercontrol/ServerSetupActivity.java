package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerSetupActivity extends BaseActivity {
    private static final int FOLDER = 8110, IMPORT = 8111, EXPORT = 8112, PERMISSION = 8113;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences setup;
    private EditText password, confirmation, host;
    private TextView status;
    private CheckBox createPassword;
    private boolean busy, quickPrepare, awaitingReload, leftForReload;
    private boolean folderReady, prerequisitesChecked, checkingPrerequisites, prepareAfterCheck;
    private long reloadOpenedAt;
    private String verifiedFingerprint = "";
    private String verifiedSession = "", verifiedUser = "";
    private String verifiedKeyFingerprint = "", verifiedAgentFingerprint = "";
    private int verifiedPort;
    private JSONObject exportProfile;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            String message = setup.getString("status", "Сервер ещё не проверен");
            if (!busy && !message.contentEquals(status.getText())) status.setText(message);
            boolean pending = !setup.getString("pending", "").isEmpty();
            long elapsed = System.currentTimeMillis() - setup.getLong("pending_at", 0);
            if (pending && (elapsed < 0 || elapsed > TermuxSetup.COMMAND_TIMEOUT_MS)) {
                setup.edit().remove("pending").remove("prepared_session")
                        .putString("status", "Время ожидания истекло. Проверь Termux и повтори подготовку.").apply();
            }
            updateSteps();
            handler.postDelayed(this, 1000);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_server_setup);
        setup = getSharedPreferences(TermuxSetup.PREFS, MODE_PRIVATE);
        if (state != null) {
            awaitingReload = state.getBoolean("awaiting_termux_reload", false);
            leftForReload = awaitingReload;
            reloadOpenedAt = state.getLong("termux_reload_opened_at", 0);
        }
        status = findViewById(R.id.serverSetupStatus);
        password = findViewById(R.id.serverSetupPasswordInput);
        confirmation = findViewById(R.id.serverSetupPasswordConfirm);
        host = findViewById(R.id.serverSetupHostInput);
        host.setText(localIp());
        createPassword = findViewById(R.id.createServerPasswordCheck);
        createPassword.setOnCheckedChangeListener((button, checked) -> {
            confirmation.setVisibility(checked ? android.view.View.VISIBLE : android.view.View.GONE);
            confirmation.setText("");
        });
        findViewById(R.id.prepareTermuxButton).setOnClickListener(v -> openTermux());
        findViewById(R.id.termuxHelpButton).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Доступ к Termux")
                .setMessage("1. Открой Termux один раз и дождись установки среды.\n2. Выбери корневую папку Termux в этом мастере.\n3. В разрешениях LocalLS разреши выполнение команд Termux.\n4. Нажми «Подготовить сервер».\n\nПри ERROR RUN_COMMAND прочитай текст окна Termux. Не удаляй данные Termux: в них находится существующий сервер.")
                .setNegativeButton("Понятно", null)
                .setNeutralButton("Termux", (dialog, which) -> openTermux())
                .setPositiveButton("Разрешения", (dialog, which) -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))); }
                    catch (RuntimeException e) { message("Открой системные настройки → Приложения → LocalLS → Разрешения."); }
                }).show());
        findViewById(R.id.selectTermuxFolderButton).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (android.os.Build.VERSION.SDK_INT >= 26) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.termux.documents/root/" + Uri.encode(TermuxSetup.HOME)));
            startActivityForResult(intent, FOLDER);
        });
        findViewById(R.id.startServerSetupButton).setOnClickListener(v -> prepareServer());
        findViewById(R.id.verifyServerSetupButton).setOnClickListener(v -> verify());
        findViewById(R.id.verifyLocalKeyButton).setOnClickListener(v -> securityCheck(true));
        findViewById(R.id.verifyLocalAgentButton).setOnClickListener(v -> securityCheck(false));
        findViewById(R.id.prepareLocalAgentButton).setOnClickListener(v -> prepareLocalAgent());
        findViewById(R.id.exportServerProfileButton).setOnClickListener(v -> export());
        findViewById(R.id.importServerProfileButton).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT));
        findViewById(R.id.closeServerSetupButton).setOnClickListener(v -> finish());
        showFolder();
        quickPrepare = state == null && getIntent().getBooleanExtra("auto_prepare", false);
    }
    @Override protected void onResume() {
        super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh);
        checkPrerequisites(quickPrepare);
        if (awaitingReload && leftForReload) {
            awaitingReload = leftForReload = false;
            if (System.currentTimeMillis() - reloadOpenedAt < 3200) {
                setup.edit().putString("status", "Termux был закрыт слишком быстро. Нажми «Подготовить сервер» и вернись из Termux через несколько секунд.").apply();
            } else handler.post(() -> { if (isUiActive() && !busy && setup.getString("pending", "").isEmpty()) TermuxSetup.run(this); });
        }
    }
    @Override protected void onPause() {
        if (awaitingReload) leftForReload = true;
        handler.removeCallbacks(refresh); super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); worker.shutdownNow(); password.setText(""); confirmation.setText(""); super.onDestroy(); }
    @Override protected boolean canRecreateForTheme() { return !busy; }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) prepareServer();
            else message("Для графической настройки разреши LocalLS выполнять команды Termux.");
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaiting_termux_reload", awaitingReload);
        state.putLong("termux_reload_opened_at", reloadOpenedAt);
        super.onSaveInstanceState(state);
    }
    private void openTermux() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(TermuxSetup.PACKAGE);
            startActivity(launch != null ? launch : new Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")));
        } catch (RuntimeException e) { message("Установи официальный Termux с F-Droid."); }
    }
    private void prepareServer() {
        if (busy || !setup.getString("pending", "").isEmpty()) return;
        if (!prerequisitesChecked) { checkPrerequisites(true); return; }
        if (!TermuxSetup.installed(this)) { message("Сначала установи Termux на серверном устройстве."); return; }
        if (!folderReady) { message("Открой Termux и выбери его корневую папку. Сохранённый доступ мог быть отозван Android."); return; }
        if (checkSelfPermission(TermuxSetup.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{TermuxSetup.PERMISSION}, PERMISSION); return;
        }
        boolean change = createPassword.isChecked();
        String value = password.getText().toString();
        if (change && (value.length() < 8 || value.length() > 128 || value.indexOf('\0') >= 0
                || !value.equals(confirmation.getText().toString()))) {
            message("Новый пароль: 8–128 символов; оба поля должны совпадать."); return;
        }
        if (quickPrepare && !change) { quickPrepare = false; beginPreparation(false, ""); return; }
        new AlertDialog.Builder(this).setTitle(change ? "Задать пароль сервера?" : "Подготовить сервер?")
                .setMessage(change ? "Пароль Termux будет заменён. Работающие SSH-сеансы и способ автозапуска сохранятся."
                        : "Текущий пароль и работающий sshd сохранятся. OpenSSH установится при необходимости.")
                .setNegativeButton("Отмена", null).setPositiveButton("Подготовить", (dialog, which) -> beginPreparation(change, value)).show();
    }
    private void beginPreparation(boolean change, String value) {
        quickPrepare = false;
        invalidateVerification();
        setup.edit().remove("prepared_session").apply();
        busy = true;
        status.setText(com.bitpoint.homeservercontrol.ui.R.string.status_6ecd2c217d67);
        char[] secret = change ? value.toCharArray() : new char[0];
        Uri tree = Uri.parse(setup.getString("tree", ""));
        worker.execute(() -> {
            String failure = "";
            try { TermuxSetup.configure(this, tree, secret); }
            catch (TermuxSetup.ExternalAppsNotEnabledException e) {
                failure = "Доступ к внешним командам не подтверждён в настройках Termux. Выбери его папку повторно. Если ошибка остаётся, пришли текст окна ERROR RUN_COMMAND без пароля.";
            }
            catch (Exception e) { failure = "Не удалось подготовить папку Termux. Выбери её повторно и проверь первый запуск."; }
            finally { Arrays.fill(secret, '\0'); }
            final String problem = failure;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (problem.isEmpty()) {
                    if (TermuxSetup.needsSettingsReload(this)) {
                        new AlertDialog.Builder(this).setTitle("Применить доступ в Termux")
                                .setMessage("Termux нужно обновить настройки в памяти. Открой его кнопкой ниже, подожди несколько секунд и вернись в LocalLS кнопкой «Назад». Если первый раз скрылась клавиатура, нажми «Назад» ещё раз. Вводить команды не нужно. Работающая SSH-служба сохранится.")
                                .setNegativeButton("Позже", null).setPositiveButton("Открыть и применить", (dialog, which) -> {
                                    awaitingReload = true; leftForReload = false;
                                    reloadOpenedAt = System.currentTimeMillis();
                                    try { TermuxSetup.openForSettingsReload(this); }
                                    catch (RuntimeException e) {
                                        awaitingReload = false;
                                        message("Не удалось открыть Termux. Открой его через значок приложения и повтори.");
                                    }
                                }).show();
                    } else TermuxSetup.run(this);
                }
                else {
                    prerequisitesChecked = true;
                    folderReady = false;
                    setup.edit().putString("status", problem).apply();
                    status.setText(problem);
                    message(problem);
                }
            });
        });
    }

    private void verify() {
        if (busy || !setup.getString("pending", "").isEmpty()) return;
        if (!prerequisitesChecked || !folderReady || !commandAllowed() || !prepared()) {
            message("Сначала выполни шаги доступа и подготовь OpenSSH."); return;
        }
        final String secret = password.getText().toString();
        if (secret.isEmpty()) { message("Введи пароль сервера для проверки входа."); return; }
        busy = true;
        invalidateVerification();
        status.setText(com.bitpoint.homeservercontrol.ui.R.string.status_681fa4dbaeb8);
        int port = setup.getInt("port", 8022);
        String user = setup.getString("user", "");
        String session = setup.getString("prepared_session", "");
        worker.execute(() -> {
            String fingerprint = "";
            try {
                fingerprint = SshClient.probeFingerprint("127.0.0.1", port, user);
                if (!SshClient.runCommand("127.0.0.1", port, user, secret, fingerprint, ":", 5000).success) fingerprint = "";
            } catch (Exception e) { /* Never log password or raw exception. */ }
            final String result = fingerprint;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (!session.equals(setup.getString("prepared_session", ""))
                        || !user.equals(setup.getString("user", "")) || port != setup.getInt("port", 8022)
                        || !setup.getString("pending", "").isEmpty()) {
                    invalidateVerification();
                    String text = "Параметры сервера изменились. Повтори проверку SSH.";
                    setup.edit().putString("status", text).apply();
                    status.setText(text);
                    return;
                }
                verifiedFingerprint = result;
                verifiedSession = result.isEmpty() ? "" : session;
                verifiedUser = user;
                verifiedPort = port;
                String text = result.isEmpty() ? "Вход не подтверждён. Проверь пароль и активный порт sshd."
                        : "SSH и пароль проверены. Пользователь: " + user + ", порт: " + port + "\nКлюч: " + result;
                setup.edit().putString("status", text).apply();
                status.setText(text);
                updateSteps();
            });
        });
    }
    private void export() {
        try {
            if (!prerequisitesChecked || !folderReady || !commandAllowed() || !verificationCurrent())
                throw new IllegalArgumentException();
            exportProfile = ServerSetupProfile.create(host.getText().toString().trim(), verifiedPort,
                    verifiedUser, verifiedFingerprint);
            ServerSetupProfile.validate(exportProfile);
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "LocalLS-server.json"), EXPORT);
        } catch (Exception e) { message("Проверь IP сервера и выполни проверку SSH."); }
    }

    private void securityCheck(boolean key) {
        if (busy) return;
        if (!prerequisitesChecked || !folderReady || !commandAllowed() || !verificationCurrent()) {
            message("Сначала подготовь сервер и проверь вход SSH."); return;
        }
        if (key && password.getText().length() == 0) {
            message("Введи действующий пароль для добавления публичного ключа."); return;
        }
        if (key) new AlertDialog.Builder(this).setTitle("Проверить ключевой доступ?")
                .setMessage("Ключ этого мастера будет сохранён зашифрованным и добавлен к существующим authorized_keys. Затем проверим отдельный вход только по ключу. Парольный резервный доступ и профиль удалённого сервера сохранятся.")
                .setNegativeButton("Отмена", null).setPositiveButton("Добавить и проверить", (dialog, which) -> beginSecurityCheck(true)).show();
        else beginSecurityCheck(false);
    }

    private void beginSecurityCheck(boolean key) {
        beginSecurityCheck(key, false);
    }

    private void prepareLocalAgent() {
        if (busy || !verificationCurrent() || !prerequisitesChecked || !folderReady || !commandAllowed()) return;
        new AlertDialog.Builder(this).setTitle("Подготовить HTTPS-агент?")
                .setMessage("На новом телефоне установим необходимые пакеты, создадим token и TLS-сертификат и запустим независимый агент. При повторе сохраним существующие секреты и проверенный процесс. Работающий sshd и автозапуск сохранятся. Нужен интернет для пакетов; проверка может занять несколько минут.")
                .setNegativeButton("Отмена", null).setPositiveButton("Подготовить", (dialog, which) -> beginSecurityCheck(false, true)).show();
    }

    private void beginSecurityCheck(boolean key, boolean prepareAgent) {
        if (busy || !verificationCurrent() || !prerequisitesChecked || !folderReady || !commandAllowed()) return;
        final String session = verifiedSession, user = verifiedUser, fingerprint = verifiedFingerprint;
        final int port = verifiedPort;
        final Uri tree = Uri.parse(setup.getString("tree", ""));
        final String keyFingerprint = verifiedKeyFingerprint;
        final String secret = key || (prepareAgent && keyFingerprint.isEmpty()) ? password.getText().toString() : "";
        if ((key || (prepareAgent && keyFingerprint.isEmpty())) && secret.isEmpty()) { message("Введи действующий пароль сервера."); return; }
        if (key) verifiedKeyFingerprint = ""; else verifiedAgentFingerprint = "";
        busy = true;
        status.setText(key ? "Добавление ключа и проверка нового SSH-входа…" : prepareAgent
                ? "Подготовка HTTPS-агента. Дождись завершения установки пакетов…" : "Проверка существующего HTTPS-агента…");
        updateSteps();
        worker.execute(() -> {
            String result = "";
            String preparationFailure = "";
            try {
                if (!TermuxSetup.hasFolderAccess(this, tree)) throw new java.io.IOException();
                if (prepareAgent) {
                    SshCredentials credential = SshCredentials.password(secret);
                    if (!keyFingerprint.isEmpty()) {
                        JSONObject identity = ServerSetupSecurity.localKey(this, port, user);
                        if (!keyFingerprint.equals(SshKeys.inspect(identity.getString(SshKeys.PRIVATE), identity.optString(SshKeys.PHRASE)).getString("fingerprint")))
                            throw new java.io.IOException();
                        credential = SshCredentials.key(identity.getString(SshKeys.PRIVATE), identity.optString(SshKeys.PHRASE));
                    }
                    ServerSetupSecurity.prepareAgent(this, port, user, credential, fingerprint);
                }
                result = key ? ServerSetupSecurity.installAndVerifyKey("127.0.0.1", port, user, secret, fingerprint,
                        ServerSetupSecurity.localKey(this, port, user)) : ServerSetupSecurity.verifyExistingAgent(this, tree);
            } catch (ServerSetupSecurity.AgentPreparationException e) { preparationFailure = e.getMessage(); }
            catch (Exception | LinkageError e) { /* No raw error or credential reaches UI, state or logs. */ }
            final String checked = result;
            final String setupFailure = preparationFailure;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (!verificationCurrent() || !session.equals(verifiedSession) || !user.equals(verifiedUser)
                        || port != verifiedPort || !fingerprint.equals(verifiedFingerprint)
                        || !tree.toString().equals(setup.getString("tree", "")) || !folderReady || !commandAllowed()) {
                    invalidateVerification();
                    String text = "Состояние сервера изменилось. Повтори проверку SSH и безопасности.";
                    setup.edit().putString("status", text).apply(); status.setText(text); updateSteps(); return;
                }
                if (key) verifiedKeyFingerprint = checked; else verifiedAgentFingerprint = checked;
                String text = checked.isEmpty() ? key
                        ? "Ключевой вход не подтверждён. Проверь пароль и настройки доступа SSH. Существующий ключ мастера сохранён для повтора."
                        : prepareAgent ? setupFailure.isEmpty() ? "Агент подготовлен или ещё запускается, но HTTPS-статус не подтверждён. Повтори проверку агента; работающий sshd сохранён." : setupFailure
                        : "HTTPS-агент не подтверждён. Проверь существующий агент, config.json, сертификат в HOME Termux и локальный порт. HTTP не используется."
                        : key ? "Отдельный вход только по ключу проверен. Парольный доступ сохранён.\nКлюч: " + checked
                        : "Сертификат проверен по файлу Termux, статус получен через HTTPS.\nСертификат: " + checked;
                setup.edit().putString("status", text).apply(); status.setText(text); updateSteps();
            });
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == FOLDER) {
                if (!"com.termux.documents".equals(uri.getAuthority()) || !TermuxSetup.HOME.equals(DocumentsContract.getTreeDocumentId(uri))) {
                    message("Выбери корневую папку в разделе Termux."); return;
                }
                if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0
                        || (data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
                    message("Мастеру нужен доступ к чтению и записи папки Termux."); return;
                }
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                setup.edit().putString("tree", uri.toString()).apply();
                checkPrerequisites(false);
            } else if (request == EXPORT && exportProfile != null) {
                if (!verificationCurrent()) {
                    exportProfile = null;
                    message("Состояние сервера изменилось. Повтори проверку SSH и экспорт."); return;
                }
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new java.io.IOException();
                    stream.write(exportProfile.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                exportProfile = null;
                message("Профиль сохранён без пароля. Передай его на устройство, с которого будешь подключаться.");
            } else if (request == IMPORT) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new java.io.IOException();
                    byte[] block = new byte[1024]; int count;
                    while ((count = stream.read(block)) != -1) {
                        buffer.write(block, 0, count);
                        if (buffer.size() > 16384) throw new java.io.IOException();
                    }
                }
                JSONObject profile = new JSONObject(buffer.toString("UTF-8"));
                ServerSetupProfile.validate(profile);
                new AlertDialog.Builder(this).setTitle("Подключить этот сервер?")
                        .setMessage(profile.getString("host") + ":" + profile.getInt("port") + "\n" + profile.getString("user")
                                + "\n\nСверь ключ с экраном сервера:\n" + profile.getString("fingerprint"))
                        .setNegativeButton("Отмена", null).setPositiveButton("Ключ совпадает", (dialog, which) -> {
                            try { ServerSetupProfile.apply(this, profile); message("Профиль импортирован. На главном экране введи пароль сервера."); }
                            catch (Exception e) { message("Импорт заблокирован: ключ отличается от уже доверенного."); }
                        }).show();
            }
        } catch (Exception e) { message("Не удалось прочитать или сохранить профиль. Проверь файл и доступ."); }
    }
    private boolean commandAllowed() {
        return checkSelfPermission(TermuxSetup.PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean prepared() {
        return !setup.getString("prepared_session", "").isEmpty() && setup.getString("pending", "").isEmpty();
    }
    private boolean verificationCurrent() {
        return !verifiedFingerprint.isEmpty() && prepared()
                && verifiedSession.equals(setup.getString("prepared_session", ""))
                && verifiedUser.equals(setup.getString("user", "")) && verifiedPort == setup.getInt("port", 8022);
    }
    private void invalidateVerification() {
        verifiedFingerprint = verifiedSession = verifiedUser = "";
        verifiedKeyFingerprint = verifiedAgentFingerprint = "";
        exportProfile = null;
        findViewById(R.id.exportServerProfileButton).setEnabled(false);
    }
    private void checkPrerequisites(boolean prepare) {
        prepareAfterCheck |= prepare;
        prerequisitesChecked = false;
        updateSteps();
        if (checkingPrerequisites) return;
        checkingPrerequisites = true;
        String savedTree = setup.getString("tree", "");
        worker.execute(() -> {
            boolean accessible = !savedTree.isEmpty() && TermuxSetup.hasFolderAccess(this, Uri.parse(savedTree));
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkingPrerequisites = false;
                if (!savedTree.equals(setup.getString("tree", ""))) { checkPrerequisites(false); return; }
                prerequisitesChecked = true;
                folderReady = accessible;
                if (!accessible || !commandAllowed()) invalidateVerification();
                updateSteps();
                boolean continuePreparation = prepareAfterCheck;
                prepareAfterCheck = false;
                if (continuePreparation && isUiActive()) prepareServer();
            });
        });
    }
    private void updateSteps() {
        boolean installed = TermuxSetup.installed(this), command = commandAllowed();
        boolean pending = !setup.getString("pending", "").isEmpty();
        boolean ready = installed && prerequisitesChecked && folderReady && command;
        if (!verifiedFingerprint.isEmpty() && (!verificationCurrent() || !installed || !command
                || (prerequisitesChecked && !folderReady))) invalidateVerification();
        String text = "1. Termux: " + (!installed ? "нужно установить" : prerequisitesChecked && folderReady
                ? "среда доступна" : "установлен; открой для первого запуска")
                + "\n2. Папка Termux: " + (!prerequisitesChecked ? "проверяется…" : folderReady ? "доступ подтверждён" : "нужно выбрать заново")
                + "\n3. Команды Termux: " + (command ? "разрешены Android" : "нужно разрешение")
                + "\n4. OpenSSH: " + (pending || busy ? "выполняется проверка / подготовка…" : prepared() ? "подготовлен" : "нужно подготовить")
                + "\n5. Вход SSH: " + (verificationCurrent() ? "подтверждён" : "нужно проверить паролем")
                + "\n6. SSH-ключ: " + (verifiedKeyFingerprint.isEmpty() ? "ещё не проверен" : "новый вход подтверждён")
                + "\n7. HTTPS-агент: " + (verifiedAgentFingerprint.isEmpty() ? "ещё не проверен / не установлен" : "статус подтверждён");
        TextView steps = findViewById(R.id.serverSetupSteps);
        if (!text.contentEquals(steps.getText())) steps.setText(text);
        showFolder();
        findViewById(R.id.startServerSetupButton).setEnabled(!busy && !pending && prerequisitesChecked);
        findViewById(R.id.verifyServerSetupButton).setEnabled(!busy && !pending && ready && prepared());
        findViewById(R.id.exportServerProfileButton).setEnabled(!busy && !pending && ready && verificationCurrent());
        findViewById(R.id.verifyLocalKeyButton).setEnabled(!busy && !pending && ready && verificationCurrent());
        findViewById(R.id.verifyLocalAgentButton).setEnabled(!busy && !pending && ready && verificationCurrent());
        findViewById(R.id.prepareLocalAgentButton).setEnabled(!busy && !pending && ready && verificationCurrent());
    }
    private void showFolder() {
        TextView folder = findViewById(R.id.termuxFolderStatus);
        String text = !prerequisitesChecked ? "Проверка доступа к папке…" : folderReady
                ? "Доступ к корневой папке Termux подтверждён" : "Выбери корневую папку Termux и разреши чтение и запись";
        if (!text.contentEquals(folder.getText())) folder.setText(text);
    }
    private void message(String text) { new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Понятно", null).show(); }
    private String localIp() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.getName().startsWith("wlan")) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses()))
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
            }
        } catch (Exception e) { /* Editable field fallback. */ }
        return "";
    }
}
