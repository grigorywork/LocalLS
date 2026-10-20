package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ru-rRU-w360dp-h640dp-mdpi")
public class ServerSetupReadinessTest {
    private final Uri tree = DocumentsContract.buildTreeDocumentUri("com.termux.documents", TermuxSetup.HOME);
    private Context context;
    private SharedPreferences setup;
    private HomeProvider provider;

    public static class HomeProvider extends ContentProvider {
        boolean available = true;
        String documentId = TermuxSetup.HOME;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
            if (!available) throw new SecurityException();
            MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
            cursor.addRow(new String[]{documentId});
            return cursor;
        }
        @Override public String getType(Uri uri) { return DocumentsContract.Document.MIME_TYPE_DIR; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    }

    @Before public void initialize() {
        context = RuntimeEnvironment.getApplication();
        setup = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        setup.edit().clear().commit();
        provider = Robolectric.buildContentProvider(HomeProvider.class).create("com.termux.documents").get();
        ShadowContentResolver.registerProviderInternal("com.termux.documents", provider);
    }

    @Test public void savedFolderRequiresBothPersistedGrantsAndReadableHome() {
        setup.edit().putString("tree", tree.toString()).commit();
        assertFalse(TermuxSetup.hasFolderAccess(context, tree));
        context.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertFalse(TermuxSetup.hasFolderAccess(context, tree));
        context.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertTrue(TermuxSetup.hasFolderAccess(context, tree));
        provider.available = false;
        assertFalse(TermuxSetup.hasFolderAccess(context, tree));
        provider.available = true;
        context.getContentResolver().releasePersistableUriPermission(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertFalse(TermuxSetup.hasFolderAccess(context, tree));
    }

    @Test public void anotherProviderSubfolderOrWrongDocumentCannotPass() {
        context.getContentResolver().takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertFalse(TermuxSetup.hasFolderAccess(context, Uri.parse("content://other/tree/home")));
        assertFalse(TermuxSetup.hasFolderAccess(context,
                DocumentsContract.buildTreeDocumentUri("com.termux.documents", TermuxSetup.HOME + "/LocalLS")));
        provider.documentId = TermuxSetup.HOME + "/LocalLS";
        assertFalse(TermuxSetup.hasFolderAccess(context, tree));
        assertFalse(TermuxSetup.hasFolderAccess(context, Uri.parse("invalid")));
    }

    @Test public void onlyValidatedCallbackMarksThePreparationReady() {
        accept("good", "USER=u0_a123\nPORT=8022\n");
        assertEquals("good", setup.getString("prepared_session", ""));
        accept("bad", "USER=u0_a123\nPORT=0\n");
        assertEquals("", setup.getString("prepared_session", ""));
        accept("missing-output", null);
        assertEquals("", setup.getString("prepared_session", ""));
        assertTrue(setup.getString("status", "").contains("корректные параметры"));
    }

    @Test public void expiredOrFutureCallbackCannotFinishPreparation() {
        for (long elapsed : new long[]{TermuxSetup.COMMAND_TIMEOUT_MS + 1000, -10000}) {
            setup.edit().putString("pending", "late").putLong("pending_at", System.currentTimeMillis() - elapsed).commit();
            new TermuxResultReceiver().onReceive(context, new Intent("localls.setup.late")
                    .putExtra("result", success("USER=u0_a123\nPORT=8022\n")));
            assertEquals("late", setup.getString("pending", ""));
            assertEquals("", setup.getString("prepared_session", ""));
        }
    }

    @Test public void oldUserAndSavedUriDoNotEnableVerification() throws Exception {
        installTermuxAndGrantHome();
        setup.edit().putString("user", "u0_a123").putInt("port", 8022).putString("device_role", "server").commit();
        ActivityController<ServerSetupActivity> controller = Robolectric.buildActivity(ServerSetupActivity.class).setup();
        try {
            ServerSetupActivity activity = controller.get(); drain(activity);
            assertFalse(activity.findViewById(R.id.verifyServerSetupButton).isEnabled());
            assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            accept("fresh", "USER=u0_a123\nPORT=8022\n");
            ReflectionHelpers.callInstanceMethod(activity, "updateSteps");
            assertTrue(activity.findViewById(R.id.verifyServerSetupButton).isEnabled());
            assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            assertTrue(((TextView) activity.findViewById(R.id.serverSetupSteps)).getText().toString().contains("нужно проверить паролем"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void newPreparationInvalidatesPreviouslyVerifiedExport() throws Exception {
        installTermuxAndGrantHome();
        accept("first", "USER=u0_a123\nPORT=8022\n");
        ActivityController<ServerSetupActivity> controller = Robolectric.buildActivity(ServerSetupActivity.class).setup();
        try {
            ServerSetupActivity activity = controller.get(); drain(activity);
            markVerified(activity, "first");
            ReflectionHelpers.setField(activity, "verifiedKeyFingerprint", "checked-key");
            ReflectionHelpers.setField(activity, "verifiedAgentFingerprint", "checked-agent");
            assertTrue(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            assertTrue(activity.findViewById(R.id.verifyLocalKeyButton).isEnabled());
            assertTrue(activity.findViewById(R.id.verifyLocalAgentButton).isEnabled());
            assertTrue(activity.findViewById(R.id.prepareLocalAgentButton).isEnabled());
            // Even identical endpoint parameters require verification of the new preparation.
            accept("second", "USER=u0_a123\nPORT=8022\n");
            ReflectionHelpers.callInstanceMethod(activity, "updateSteps");
            assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            assertEquals("", ReflectionHelpers.getField(activity, "verifiedFingerprint"));
            assertEquals("", ReflectionHelpers.getField(activity, "verifiedKeyFingerprint"));
            assertEquals("", ReflectionHelpers.getField(activity, "verifiedAgentFingerprint"));
            assertFalse(activity.findViewById(R.id.verifyLocalKeyButton).isEnabled());
            assertFalse(activity.findViewById(R.id.verifyLocalAgentButton).isEnabled());
            assertFalse(activity.findViewById(R.id.prepareLocalAgentButton).isEnabled());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void revokedFolderAccessOnReturnInvalidatesVerification() throws Exception {
        installTermuxAndGrantHome();
        accept("first", "USER=u0_a123\nPORT=8022\n");
        ActivityController<ServerSetupActivity> controller = Robolectric.buildActivity(ServerSetupActivity.class).setup();
        try {
            ServerSetupActivity activity = controller.get(); drain(activity);
            markVerified(activity, "first");
            assertTrue(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            controller.pause();
            context.getContentResolver().releasePersistableUriPermission(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            controller.resume(); drain(activity);
            assertFalse(activity.findViewById(R.id.verifyServerSetupButton).isEnabled());
            assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            assertEquals("", ReflectionHelpers.getField(activity, "verifiedFingerprint"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void agentSetupErrorsExposeOnlyKnownRecoveryMessages() {
        assertTrue(ServerSetupSecurity.preparationFailure("AGENT_SETUP_PORT_BUSY").contains("Порт"));
        assertTrue(ServerSetupSecurity.preparationFailure("AGENT_SETUP_TLS_INCOMPLETE").contains("Существующий ключ сохранён"));
        String untrusted = "private details from a failed command";
        assertFalse(ServerSetupSecurity.preparationFailure(untrusted).contains(untrusted));
    }

    private void installTermuxAndGrantHome() {
        PackageInfo info = new PackageInfo(); info.packageName = TermuxSetup.PACKAGE; info.versionName = "0.118.3";
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(TermuxSetup.PERMISSION);
        context.getContentResolver().takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        setup.edit().putString("tree", tree.toString()).commit();
    }
    private void markVerified(ServerSetupActivity activity, String session) {
        ReflectionHelpers.setField(activity, "verifiedFingerprint", "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        ReflectionHelpers.setField(activity, "verifiedSession", session);
        ReflectionHelpers.setField(activity, "verifiedUser", "u0_a123");
        ReflectionHelpers.setField(activity, "verifiedPort", 8022);
        ReflectionHelpers.callInstanceMethod(activity, "updateSteps");
    }
    private void drain(ServerSetupActivity activity) throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
        worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void accept(String nonce, String output) {
        setup.edit().putString("pending", nonce).putLong("pending_at", System.currentTimeMillis()).commit();
        new TermuxResultReceiver().onReceive(context,
                new Intent("localls.setup." + nonce).putExtra("result", success(output)));
    }
    private Bundle success(String output) {
        Bundle result = new Bundle(); result.putInt("err", -1); result.putInt("exitCode", 0);
        result.putString("stdout", output); return result;
    }
}
