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
    private final ServerWizardState wizard = new ServerWizardState();
    private long completionSeen;
    private int checkedAgentPort;
    private TextView wizardSummary;
    private TextView[] stepViews;
    private long reloadOpenedAt;
    private String verifiedFingerprint = "";
    private JSONObject exportProfile;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            String message = setup.getString("status", "Сервер ещё не проверен");
            if (!busy && !message.contentEquals(status.getText())) status.setText(message);
            boolean pending = !setup.getString("pending", "").isEmpty();
            if (pending && System.currentTimeMillis() - setup.getLong("pending_at", 0) > 600000) {
                wizard.set("agent".equals(setup.getString("pending_kind", "ssh")) ? ServerWizardState.Step.AGENT : ServerWizardState.Step.SSH,
                        ServerWizardState.Status.FAILED, "Время ожидания истекло. Открой Termux и повтори этот шаг; существующий сервер сохранён.");
                setup.edit().remove("pending").putString("status", "Время ожидания истекло. Проверь Termux и повтори подготовку.").apply();
                pending = false;
            }
            findViewById(R.id.startServerSetupButton).setEnabled(!busy && !pending);
            findViewById(R.id.verifyServerSetupButton).setEnabled(!busy && !pending && !setup.getString("user", "").isEmpty());
            refreshPrerequisites();
            long completed = setup.getLong("completed_at", 0);
            if (!busy && completed > completionSeen && !pending) {
                completionSeen = completed;
                String kind = setup.getString("last_kind", "ssh");
                if (setup.getInt("last_termux_error", -2) == -1 && setup.getInt("last_exit_code", -1) == 0) {
                    if ("agent".equals(kind)) verifyAgent();
                    else {
                        verifiedFingerprint = "";
                        wizard.resetFrom(ServerWizardState.Step.SSH);
                        if (!password.getText().toString().isEmpty() || hasLocalKey()) verify();
                        else wizard.set(ServerWizardState.Step.SSH, ServerWizardState.Status.WAITING,
                                getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_need_credential));
                    }
                } else wizard.set("agent".equals(kind) ? ServerWizardState.Step.AGENT : ServerWizardState.Step.SSH,
                        ServerWizardState.Status.FAILED, setup.getString("status", ""));
            }
            renderWizard();
            handler.postDelayed(this, 1000);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_server_setup);
        setup = getSharedPreferences(TermuxSetup.PREFS, MODE_PRIVATE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        completionSeen = setup.getLong("completed_at", 0);
        wizardSummary = findViewById(R.id.serverWizardSummary);
        stepViews = new TextView[]{findViewById(R.id.wizardTermuxStep), findViewById(R.id.wizardFolderStep),
                findViewById(R.id.wizardPermissionStep), findViewById(R.id.wizardSshStep),
                findViewById(R.id.wizardKeyStep), findViewById(R.id.wizardAgentStep)};
        if (state != null) {
            awaitingReload = state.getBoolean("awaiting_termux_reload", false);
            leftForReload = awaitingReload;
            reloadOpenedAt = state.getLong("termux_reload_opened_at", 0);
        }
        if (state != null && state.containsKey("public_export_profile")) {
            try { exportProfile = new JSONObject(state.getString("public_export_profile")); }
            catch (Exception e) { exportProfile = null; }
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
        findViewById(R.id.exportServerProfileButton).setOnClickListener(v -> export());
        findViewById(R.id.setupServerKeyButton).setOnClickListener(v -> installKey());
        findViewById(R.id.setupServerAgentButton).setOnClickListener(v -> prepareAgent());
        findViewById(R.id.importServerProfileButton).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT));
        findViewById(R.id.closeServerSetupButton).setOnClickListener(v -> finish());
        showFolder(); refreshPrerequisites(); renderWizard();
        quickPrepare = state == null && getIntent().getBooleanExtra("auto_prepare", false);
        if (quickPrepare) handler.post(this::prepareServer);
    }
    @Override protected void onResume() {
        super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh);
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
        if (exportProfile != null) state.putString("public_export_profile", exportProfile.toString());
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
        if (!TermuxSetup.installed(this)) { message(getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_termux_missing)); return; }
        if (!TermuxPackage.supported(this)) { message(getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_termux_untrusted)); return; }
        if (setup.getString("tree", "").isEmpty()) { message("Сначала выбери корневую папку Termux."); return; }
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
        if (busy || !setup.getString("pending", "").isEmpty()) return;
        verifiedFingerprint = "";
        wizard.set(ServerWizardState.Step.SSH, ServerWizardState.Status.RUNNING,
                getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_preparing_ssh));
        renderWizard();
        findViewById(R.id.exportServerProfileButton).setEnabled(false);
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
                    wizard.set(ServerWizardState.Step.FOLDER, ServerWizardState.Status.FAILED, problem);
                    setup.edit().putString("status", problem).apply();
                    status.setText(problem);
                    message(problem);
                }
            });
        });
    }

    private boolean hasLocalKey() {
        return SshKeys.keyCredentials(this, LocalServerVerification.LOOPBACK, setup.getInt("port", 0),
                setup.getString("user", "")).available();
    }
    private void refreshPrerequisites() {
        if (!TermuxSetup.installed(this)) {
            wizard.set(ServerWizardState.Step.TERMUX, ServerWizardState.Status.FAILED,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_termux_missing)); return;
        }
        if (!TermuxPackage.supported(this)) {
            wizard.set(ServerWizardState.Step.TERMUX, ServerWizardState.Status.FAILED,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_termux_untrusted)); return;
        }
        wizard.set(ServerWizardState.Step.TERMUX, ServerWizardState.Status.READY, TermuxSetup.version(this));
        String tree = setup.getString("tree", "");
        boolean folderGranted = false;
        for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions())
            if (permission.getUri().toString().equals(tree) && permission.isReadPermission() && permission.isWritePermission())
                folderGranted = true;
        if (tree.isEmpty() || !folderGranted) {
            if (wizard.status(ServerWizardState.Step.FOLDER) == ServerWizardState.Status.READY)
                wizard.resetFrom(ServerWizardState.Step.FOLDER);
            wizard.set(ServerWizardState.Step.FOLDER, ServerWizardState.Status.WAITING,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_need_folder));
        } else wizard.set(ServerWizardState.Step.FOLDER, ServerWizardState.Status.READY, "");
        boolean allowed = checkSelfPermission(TermuxSetup.PERMISSION) == PackageManager.PERMISSION_GRANTED;
        if (!allowed && wizard.status(ServerWizardState.Step.PERMISSION) == ServerWizardState.Status.READY)
            wizard.resetFrom(ServerWizardState.Step.PERMISSION);
        wizard.set(ServerWizardState.Step.PERMISSION, allowed ? ServerWizardState.Status.READY : ServerWizardState.Status.WAITING,
                allowed ? "" : getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_need_permission));
    }
    private boolean prerequisitesReady() {
        refreshPrerequisites();
        for (ServerWizardState.Step step : new ServerWizardState.Step[]{ServerWizardState.Step.TERMUX,
                ServerWizardState.Step.FOLDER, ServerWizardState.Step.PERMISSION})
            if (wizard.status(step) != ServerWizardState.Status.READY) { message(wizard.detail(step)); return false; }
        return true;
    }
    private void renderWizard() {
        int[] labels = {com.bitpoint.homeservercontrol.ui.R.string.wizard_step_termux,
                com.bitpoint.homeservercontrol.ui.R.string.wizard_step_folder,
                com.bitpoint.homeservercontrol.ui.R.string.wizard_step_permission,
                com.bitpoint.homeservercontrol.ui.R.string.wizard_step_ssh,
                com.bitpoint.homeservercontrol.ui.R.string.wizard_step_key,
                com.bitpoint.homeservercontrol.ui.R.string.wizard_step_agent};
        for (ServerWizardState.Step step : ServerWizardState.Step.values()) {
            int stateText = wizard.status(step) == ServerWizardState.Status.READY ? com.bitpoint.homeservercontrol.ui.R.string.wizard_ready
                    : wizard.status(step) == ServerWizardState.Status.RUNNING ? com.bitpoint.homeservercontrol.ui.R.string.wizard_running
                    : wizard.status(step) == ServerWizardState.Status.FAILED ? com.bitpoint.homeservercontrol.ui.R.string.wizard_failed
                    : com.bitpoint.homeservercontrol.ui.R.string.wizard_waiting;
            String detail = wizard.detail(step);
            String text = detail.isEmpty() ? getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_step_status,
                    getString(labels[step.ordinal()]), getString(stateText))
                    : getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_step_status_detail,
                    getString(labels[step.ordinal()]), getString(stateText), detail);
            if (!text.contentEquals(stepViews[step.ordinal()].getText())) stepViews[step.ordinal()].setText(text);
        }
        String summary = wizard.complete() ? getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_complete)
                : wizard.sshReady() ? getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_only)
                : getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_not_ready);
        if (!summary.contentEquals(wizardSummary.getText())) wizardSummary.setText(summary);
        boolean pending = !setup.getString("pending", "").isEmpty();
        findViewById(R.id.setupServerKeyButton).setEnabled(wizard.sshReady() && !busy && !pending);
        findViewById(R.id.setupServerAgentButton).setEnabled(wizard.sshReady() && !busy && !pending);
        findViewById(R.id.exportServerProfileButton).setEnabled(wizard.sshReady() && !verifiedFingerprint.isEmpty() && !busy && !pending);
    }
    interface CheckedWork { void run() throws Exception; }
    private void task(ServerWizardState.Step step, CheckedWork work, Runnable success) {
        if (busy || !setup.getString("pending", "").isEmpty()) return;
        busy = true; wizard.set(step, ServerWizardState.Status.RUNNING, ""); renderWizard();
        worker.execute(() -> {
            String reason = "";
            try { work.run(); }
            catch (LocalServerVerification.Failure failure) { reason = failure.reason; }
            catch (Exception | LinkageError failure) { reason = "operation"; }
            final String problem = reason;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (problem.isEmpty()) success.run();
                else {
                    String text = recovery(problem, step);
                    if ("ssh_stopped".equals(problem)) wizard.set(ServerWizardState.Step.SSH, ServerWizardState.Status.FAILED, text);
                    wizard.set(step, ServerWizardState.Status.FAILED, text);
                    status.setText(text);
                }
                renderWizard();
            });
        });
    }
    private String recovery(String reason, ServerWizardState.Step step) {
        int id;
        switch (reason) {
            case "host_key": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_host_key_failure; break;
            case "credential": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_need_credential; break;
            case "ssh_unreachable": case "parameters": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_failure; break;
            case "authentication": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_auth_failure; break;
            case "key_storage": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_key_storage_failure; break;
            case "agent_http": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_http_failure; break;
            case "agent_certificate": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_certificate_failure; break;
            case "agent_token": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_token_failure; break;
            case "agent_config": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_config_failure; break;
            case "agent_status": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_status_failure; break;
            case "agent_unreachable": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_unreachable_failure; break;
            case "ssh_stopped": id = com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_stopped; break;
            default: id = step == ServerWizardState.Step.KEY ? com.bitpoint.homeservercontrol.ui.R.string.wizard_key_failure
                    : step == ServerWizardState.Step.AGENT ? com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_failure
                    : com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_failure;
        }
        return getString(id);
    }
    private void verify() {
        if (!prerequisitesReady() || busy) return;
        final String secret = password.getText().toString();
        final String[] fingerprint = {""};
        verifiedFingerprint = "";
        task(ServerWizardState.Step.SSH, () -> fingerprint[0] = LocalServerVerification.verifySsh(this, setup, secret), () -> {
            verifiedFingerprint = fingerprint[0];
            wizard.set(ServerWizardState.Step.SSH, ServerWizardState.Status.READY,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_endpoint,
                            setup.getString("user", ""), LocalServerVerification.LOOPBACK, setup.getInt("port", 0),
                            getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_network_address,
                            localIp().isEmpty() ? getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_no_network_address) : localIp()), verifiedFingerprint));
            if (secret.isEmpty()) wizard.set(ServerWizardState.Step.KEY, ServerWizardState.Status.READY,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_key_verified));
            status.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_ssh_verified));
            if (secret.isEmpty() && setup.getBoolean("agent_setup_approved", false)) runAgentPreparation();
        });
    }
    private void installKey() {
        if (!wizard.sshReady() || !prerequisitesReady() || busy) return;
        final String secret = password.getText().toString();
        final String fingerprint = verifiedFingerprint;
        new AlertDialog.Builder(this).setTitle(com.bitpoint.homeservercontrol.ui.R.string.wizard_key_button)
                .setMessage(com.bitpoint.homeservercontrol.ui.R.string.wizard_key_confirmation)
                .setNegativeButton("Отмена", null).setPositiveButton("Настроить", (dialog, which) ->
                task(ServerWizardState.Step.KEY, () -> LocalServerVerification.installAndVerifyKey(this, setup, secret, fingerprint), () -> {
                    password.setText(""); confirmation.setText(""); createPassword.setChecked(false);
                    wizard.set(ServerWizardState.Step.KEY, ServerWizardState.Status.READY,
                            getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_key_verified));
                    status.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_key_verified));
                })).show();
    }
    private void prepareAgent() {
        if (!wizard.sshReady() || !prerequisitesReady() || busy) return;
        new AlertDialog.Builder(this).setTitle(com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_button)
                .setMessage(com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_confirmation)
                .setNegativeButton("Отмена", null).setPositiveButton("Подготовить", (dialog, which) ->
                { setup.edit().putBoolean("agent_setup_approved", true).apply(); runAgentPreparation(); }).show();
    }
    private void runAgentPreparation() {
        Uri tree = Uri.parse(setup.getString("tree", ""));
        task(ServerWizardState.Step.AGENT, () -> TermuxSetup.prepareAgent(this, tree), () -> {
                    status.setText(setup.getString("status", ""));
                    if (setup.getString("pending", "").isEmpty()) wizard.set(ServerWizardState.Step.AGENT,
                            ServerWizardState.Status.FAILED, getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_failure));
                });
    }
    private void verifyAgent() {
        if (!prerequisitesReady()) return;
        Uri tree = Uri.parse(setup.getString("tree", ""));
        task(ServerWizardState.Step.AGENT, () -> checkedAgentPort = LocalServerVerification.verifyAgent(this, tree), () -> {
            wizard.set(ServerWizardState.Step.AGENT, ServerWizardState.Status.READY,
                    getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_verified, checkedAgentPort));
            status.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.wizard_agent_verified, checkedAgentPort));
        });
    }
    private void export() {
        try {
            if (verifiedFingerprint.isEmpty()) throw new IllegalArgumentException();
            exportProfile = ServerSetupProfile.create(host.getText().toString().trim(), setup.getInt("port", 8022),
                    setup.getString("user", ""), verifiedFingerprint);
            ServerSetupProfile.validate(exportProfile);
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "LocalLS-server.json"), EXPORT);
        } catch (Exception e) { message("Проверь IP сервера и выполни проверку SSH."); }
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
                setup.edit().putString("tree", uri.toString()).apply(); showFolder();
            } else if (request == EXPORT && exportProfile != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new java.io.IOException();
                    stream.write(exportProfile.toString(2).getBytes(StandardCharsets.UTF_8));
                }
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
    private void showFolder() { ((TextView) findViewById(R.id.termuxFolderStatus)).setText(
            setup.getString("tree", "").isEmpty() ? "Папка не выбрана" : "Корневая папка Termux выбрана"); }
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
