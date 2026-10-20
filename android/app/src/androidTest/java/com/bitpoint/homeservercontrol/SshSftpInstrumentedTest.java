package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;

import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Optional isolated fixture. No connection to, or change on, the user's Realme. */
@RunWith(AndroidJUnit4.class)
public class SshSftpInstrumentedTest {
    private Context context;
    private String host, user, secret, pin;
    private int port;

    @Before
    public void prepare() {
        Bundle args = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue(args.containsKey("fixtureHost"));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        host = args.getString("fixtureHost");
        port = Integer.parseInt(args.getString("fixturePort"));
        user = args.getString("fixtureUser");
        secret = args.getString("fixturePassword");
        ServerStats probe = SshClient.probe(host, port, user, secret, "");
        assertTrue(probe.error, probe.hostKeyNeedsTrust);
        assertFalse(probe.authenticated);
        pin = probe.hostFingerprint;
    }

    private void installFixtureKey() throws Exception {
        org.json.JSONObject key = SshKeys.generate();
        SftpClient.installPublicKey(host, port, user, SshCredentials.password(secret), pin,
                key.getString(SshKeys.PUBLIC));
        SshKeys.save(context, host, port, user, key);
    }

    @After
    public void cleanup() {
        if (context != null) {
            TransferForegroundService.stop(context);
            context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test
    public void sshAuthenticatesRejectsWrongPasswordAndBlocksFingerprintMismatch() {
        ServerStats good = SshClient.probe(host, port, user, secret, pin);
        assertTrue(good.error, good.authenticated);
        ServerStats bad = SshClient.probe(host, port, user, java.util.UUID.randomUUID().toString(), pin);
        assertFalse(bad.authenticated);
        assertFalse(bad.error.contains(secret));
        ServerStats changed = SshClient.probe(host, port, user, secret, pin + "changed");
        assertTrue(changed.hostKeyMismatch);
        assertFalse(changed.authenticated);
        SshCommandResult command = SshClient.runCommand(host, port, user, secret, pin, "fixture-command", 5000);
        assertTrue(command.error, command.success);
        assertTrue(command.output.contains("fixture-ok"));
    }

    @Test
    public void sftpRoundTripMkdirRenameAndSafeDelete() throws Exception {
        String folder = "/roundtrip-" + java.util.UUID.randomUUID();
        byte[] data = new byte[128 * 1024];
        new java.util.Random(42).nextBytes(data);
        SftpClient.mkdir(host, port, user, secret, pin, folder);
        SftpClient.upload(host, port, user, secret, pin, new ByteArrayInputStream(data), folder + "/file.bin", data.length, null);
        List<RemoteEntry> entries = SftpClient.list(host, port, user, secret, pin, folder);
        assertEquals(1, entries.size());
        assertEquals(data.length, entries.get(0).size);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SftpClient.download(host, port, user, secret, pin, folder + "/file.bin", output, data.length, null);
        assertArrayEquals(data, output.toByteArray());
        try {
            SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("folder", folder, true, 0, 0));
            fail("Non-empty directory must not be removed");
        } catch (Exception expected) { }
        SftpClient.rename(host, port, user, secret, pin, folder + "/file.bin", folder + "/renamed.bin");
        SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("file", folder + "/renamed.bin", false, data.length, 0));
        SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("folder", folder, true, 0, 0));
        try {
            SftpClient.list(host, port, user, secret, pin + "changed", "/");
            fail("Changed host key must block SFTP");
        } catch (Exception expected) { }
    }

    @Test public void serviceUploadsAfterFileScreenCloses()throws Exception{
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29);
        context.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,host).putInt(ServerConfig.KEY_PORT,port).putString(ServerConfig.KEY_USER,user).commit();
        HostTrustStore.trust(context,host,port,pin);installFixtureKey();
        String name="background-"+java.util.UUID.randomUUID()+".bin";byte[] data=new byte[8*1024*1024+17];new java.util.Random(77).nextBytes(data);
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");
        Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        try{
            try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){assertNotNull(out);out.write(data);}
            android.content.Intent launch=new android.content.Intent(context,FilesActivity.class).putExtra("own_server",false);
            try(ActivityScenario<FilesActivity> scenario=ActivityScenario.launch(launch)){
                scenario.onActivity(activity->{try{TransferForegroundService.enqueue(activity,TransferForegroundService.UPLOAD,java.util.Collections.singletonList(FileNodes.document(activity,uri)),"/",null);}catch(Exception e){throw new AssertionError(e);}});
                long end=System.currentTimeMillis()+15000;while(!guardRunning() && !TransferForegroundService.state.active && System.currentTimeMillis()<end)Thread.sleep(50);
                assertTrue(guardRunning() || TransferForegroundService.state.active);
            }
            long end=System.currentTimeMillis()+120000;while(TransferForegroundService.state.active && System.currentTimeMillis()<end)Thread.sleep(100);
            assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);
            ByteArrayOutputStream copy=new ByteArrayOutputStream();SftpClient.download(host,port,user,secret,pin,"/"+name,copy,data.length,null);assertArrayEquals(data,copy.toByteArray());
            assertFalse(TransferLogStore.render(context).contains(secret));SftpClient.delete(host,port,user,secret,pin,new RemoteEntry(name,"/"+name,false,data.length,0));
        }finally{context.getContentResolver().delete(uri,null,null);}
    }
    @Test public void pickerSendsIntoChosenFolderAndRecreationDoesNotReplayAttachment()throws Exception{
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29);String folder="/picker-"+java.util.UUID.randomUUID(),name="file.bin";byte[] data={5,6,7};
        context.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,host).putInt(ServerConfig.KEY_PORT,port).putString(ServerConfig.KEY_USER,user).commit();
        HostTrustStore.trust(context,host,port,pin);installFixtureKey();SftpClient.mkdir(host,port,user,secret,pin,folder);
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        try{
            try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){assertNotNull(out);out.write(data);}
            try(ActivityScenario<FilesActivity> scenario=ActivityScenario.launch(new android.content.Intent(context,FilesActivity.class).putExtra("own_server",false))){
                scenario.onActivity(activity->{assertFalse(activity.openRemotePath("relative/path"));assertTrue(activity.openRemotePath(folder));activity.onActivityResult(501,android.app.Activity.RESULT_OK,new android.content.Intent().setData(uri));});
                long end=System.currentTimeMillis()+30000;while(SftpClient.list(host,port,user,secret,pin,folder).isEmpty() && System.currentTimeMillis()<end)Thread.sleep(100);
                while(TransferForegroundService.state.active && System.currentTimeMillis()<end)Thread.sleep(100);
                assertFalse(TransferForegroundService.state.diagnostic,TransferForegroundService.state.error);scenario.recreate();Thread.sleep(300);
                List<RemoteEntry> files=SftpClient.list(host,port,user,secret,pin,folder);assertEquals(1,files.size());ByteArrayOutputStream copy=new ByteArrayOutputStream();SftpClient.download(host,port,user,secret,pin,files.get(0).path,copy,3,null);assertArrayEquals(data,copy.toByteArray());
                SftpClient.delete(host,port,user,secret,pin,files.get(0));
            }
            SftpClient.delete(host,port,user,secret,pin,new RemoteEntry("folder",folder,true,0,0));
        }finally{context.getContentResolver().delete(uri,null,null);}
    }

    private boolean guardRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(100)) {
            if (service.service.getClassName().equals(TransferForegroundService.class.getName()) && service.foreground) return true;
        }
        return false;
    }
}
