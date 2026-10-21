package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.widget.EditText;
import android.widget.RadioButton;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real TCP/SSH/SFTP on distinct loopback IPs. It does not emulate a Wi-Fi radio/router. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class LanConnectionIntegrationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Context app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();
    }
    private void profile(String host,int port){
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,host)
                .putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL).putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,port).commit();
    }
    private void endpoint(android.app.Activity activity,int id,String value){((EditText)activity.findViewById(id)).setText(value);}
    private byte[] digest(Path path)throws Exception{
        MessageDigest sum=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];
        try(InputStream in=Files.newInputStream(path)){int count;while((count=in.read(block))!=-1)sum.update(block,0,count);}return sum.digest();
    }
    private void differentEndpoints(SftpClient.TreeSession tree,String target){
        Object connection=ReflectionHelpers.getField(tree,"c");Object session=ReflectionHelpers.getField(connection,"session");
        Socket socket=ReflectionHelpers.getField(session,"socket");
        assertEquals(target,socket.getInetAddress().getHostAddress());assertNotEquals(socket.getInetAddress(),socket.getLocalAddress());
    }
    @Test public void differentIpsTransfer60MiBAndBothEditorsKeepAccessWhenServerAddressChanges()throws Exception{
        String first="127.0.0.2",second="127.0.0.3",third="127.0.0.4";String user="localls";
        JSONObject identity=SshKeys.generate();KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);KeyPair host=generator.generateKeyPair();
        Path root=temp.newFolder("server").toPath(),source=temp.newFile("source.bin").toPath(),download=temp.newFile("download.bin").toPath();
        byte[] block=new byte[65536];Random random=new Random(2006);
        try(OutputStream out=Files.newOutputStream(source)){for(int n=0;n<960;n++){random.nextBytes(block);out.write(block);}}
        String pin=EmbeddedSftpServer.fingerprint(host),remote;
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start(first,0,user,EmbeddedSftpServer.authorizedKey(identity.getString(SshKeys.PUBLIC)),host,root);
            profile(first,port);SshKeys.save(app,first,port,user,identity);HostTrustStore.trust(app,first,port,pin);
            ServerStats initial=SshClient.probe(app,first,port,user,"",pin);assertTrue(initial.error,initial.authenticated && initial.sftpOnly);
            try(SftpClient.TreeSession files=new SftpClient.TreeSession(first,port,user,SshKeys.credentials(app,first,port,user,""),pin)){
                differentEndpoints(files,first);
                String folder=files.folder("/","Тест Wi-Fi");files.folder(folder,"Пустая папка");
                try(InputStream in=Files.newInputStream(source)){remote=files.write(folder,"60 MiB.bin",in,Files.size(source),(done,total)->true);}
                assertEquals(2,files.list(folder).size());
                try(OutputStream out=Files.newOutputStream(download)){files.read(remote,out,(done,total)->true);}
            }
            assertEquals(60L*1024*1024,Files.size(download));assertArrayEquals(digest(source),digest(download));
            assertArrayEquals(digest(source),digest(root.resolve(remote.substring(1))));
            server.close();assertEquals(port,server.start(second,port,user,EmbeddedSftpServer.authorizedKey(identity.getString(SshKeys.PUBLIC)),host,root));
            try(var c=Robolectric.buildActivity(NetworkActivity.class).create()){
                endpoint(c.get(),R.id.networkLocalHostInput,second);c.get().findViewById(R.id.networkSaveButton).performClick();
            }
            assertEquals(second,ConnectionSelector.selectedHost(app));
            assertEquals("Changing the server IP in Network must preserve its trusted identity",pin,HostTrustStore.get(app,second,port));
            assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(app,second,port,user).getString(SshKeys.PUBLIC));
            ServerStats moved=SshClient.probe(app,second,port,user,"",HostTrustStore.get(app,second,port));assertTrue(moved.error,moved.authenticated && moved.sftpOnly);
            assertFalse(SshClient.probe(app,first,port,user,"",pin).reachable);
            try(SftpClient.TreeSession files=new SftpClient.TreeSession(second,port,user,SshKeys.credentials(app,second,port,user,""),pin)){
                differentEndpoints(files,second);assertEquals(2,files.list(SftpClient.parent(remote)).size());
            }
            server.close();assertEquals(port,server.start(third,port,user,EmbeddedSftpServer.authorizedKey(identity.getString(SshKeys.PUBLIC)),host,root));
            try(var c=Robolectric.buildActivity(MainActivity.class).create()){
                endpoint(c.get(),R.id.localHostInput,third);c.get().findViewById(R.id.saveButton).performClick();
            }
            assertEquals(third,ConnectionSelector.selectedHost(app));assertEquals(pin,HostTrustStore.get(app,third,port));
            ServerStats again=SshClient.probe(app,third,port,user,"",pin);assertTrue(again.error,again.authenticated && again.sftpOnly);
            assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(app,third,port,user).getString(SshKeys.PUBLIC));
        }
    }
    @Test public void networkEditorRejectsInvalidAddressWithoutReplacingWorkingProfile(){
        profile("192.0.2.5",8022);
        try(var c=Robolectric.buildActivity(NetworkActivity.class).create()){
            endpoint(c.get(),R.id.networkLocalHostInput,"999.1.1.1");c.get().findViewById(R.id.networkSaveButton).performClick();
            assertEquals("192.0.2.5",ConnectionSelector.selectedHost(app));assertNotNull(((EditText)c.get().findViewById(R.id.networkLocalHostInput)).getError());
        }
    }
    @Test public void networkEditorNeverReplacesKnownDestinationKeyOrFingerprint()throws Exception{
        String first="192.0.2.51",target="192.0.2.52";profile(first,8022);
        JSONObject sourceKey=SshKeys.generate(),targetKey=SshKeys.generate();
        SshKeys.save(app,first,8022,"localls",sourceKey);SshKeys.save(app,target,8022,"localls",targetKey);
        HostTrustStore.trust(app,first,8022,"SHA256:source");HostTrustStore.trust(app,target,8022,"SHA256:other");
        try(var c=Robolectric.buildActivity(NetworkActivity.class).create()){
            endpoint(c.get(),R.id.networkLocalHostInput,target);c.get().findViewById(R.id.networkSaveButton).performClick();
        }
        assertEquals("SHA256:other",HostTrustStore.get(app,target,8022));assertEquals("SHA256:source",HostTrustStore.get(app,first,8022));
        assertEquals(sourceKey.getString(SshKeys.PUBLIC),SshKeys.info(app,first,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals(targetKey.getString(SshKeys.PUBLIC),SshKeys.info(app,target,8022,"localls").getString(SshKeys.PUBLIC));
    }
    @Test public void networkEditorCanSaveValidVpnWithoutInventingLocalAddress(){
        profile("192.0.2.5",8022);
        try(var c=Robolectric.buildActivity(NetworkActivity.class).create()){
            ((RadioButton)c.get().findViewById(R.id.networkVpnModeButton)).setChecked(true);
            endpoint(c.get(),R.id.networkLocalHostInput,"");endpoint(c.get(),R.id.networkVpnHostInput,"100.64.0.8");
            c.get().findViewById(R.id.networkSaveButton).performClick();assertEquals("100.64.0.8",ConnectionSelector.selectedHost(app));
        }
    }
}
