package com.bitpoint.homeservercontrol;

import android.content.Context;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class AccessRecoveryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Context app;
    private final String old="127.0.0.2",current="127.0.0.3",user="localls";
    @Before public void setup(){app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();}
    private final class Fixture implements AutoCloseable {
        final EmbeddedSftpServer server=new EmbeddedSftpServer();final JSONObject identity;final int port;final String pin;
        Fixture()throws Exception{
            identity=SshKeys.generate();KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);KeyPair host=generator.generateKeyPair();pin=EmbeddedSftpServer.fingerprint(host);
            Path root=temp.newFolder().toPath();Files.write(root.resolve("ok.txt"),new byte[]{2,4,6});
            port=server.start(current,0,user,EmbeddedSftpServer.authorizedKey(identity.getString(SshKeys.PUBLIC)),host,root);
            SshKeys.save(app,old,port,user,identity);HostTrustStore.trust(app,old,port,pin);
            app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,current).putString(ServerConfig.KEY_USER,user).putInt(ServerConfig.KEY_PORT,port).commit();
        }
        ServerStats check(){return SshClient.probe(app,current,port,user,"",HostTrustStore.get(app,current,port));}
        @Override public void close()throws Exception{server.close();}
    }
    @Test public void oldVersionLostAddressBindingRecoversOnlyExistingApprovedKey()throws Exception{
        try(Fixture f=new Fixture()){
            ServerStats result=f.check();assertTrue(result.error,result.authenticated && result.hostKeyTrusted && result.sftpOnly);
            assertEquals(f.pin,HostTrustStore.get(app,current,f.port));assertEquals(f.pin,HostTrustStore.get(app,old,f.port));
            assertEquals(f.identity.getString(SshKeys.PUBLIC),SshKeys.info(app,current,f.port,user).getString(SshKeys.PUBLIC));
            try(SftpClient.TreeSession files=new SftpClient.TreeSession(current,f.port,user,SshKeys.credentials(app,current,f.port,user,""),f.pin)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();files.read("/ok.txt",out,(done,total)->true);assertArrayEquals(new byte[]{2,4,6},out.toByteArray());
            }
        }
    }
    @Test public void alreadyConfirmedNewAddressCanRecoverMissingKey()throws Exception{
        try(Fixture f=new Fixture()){
            HostTrustStore.trust(app,current,f.port,f.pin);ServerStats result=f.check();assertTrue(result.error,result.authenticated);
            assertEquals(f.identity.getString(SshKeys.PUBLIC),SshKeys.info(app,current,f.port,user).getString(SshKeys.PUBLIC));
        }
    }
    @Test public void unknownServerDoesNotReceiveTrustOrOldClientKey()throws Exception{
        try(Fixture f=new Fixture()){
            HostTrustStore.trust(app,old,f.port,"SHA256:different-server");ServerStats result=f.check();
            assertFalse(result.authenticated);assertTrue(result.hostKeyNeedsTrust);assertEquals("",HostTrustStore.get(app,current,f.port));assertEquals(0,SshKeys.info(app,current,f.port,user).length());
            assertEquals(f.identity.getString(SshKeys.PUBLIC),SshKeys.info(app,old,f.port,user).getString(SshKeys.PUBLIC));
        }
    }
    @Test public void multipleDifferentKeysForSameServerRequireManualChoice()throws Exception{
        try(Fixture f=new Fixture()){
            String alternate="127.0.0.4";SshKeys.save(app,alternate,f.port,user,SshKeys.generate());HostTrustStore.trust(app,alternate,f.port,f.pin);
            ServerStats result=f.check();assertFalse(result.authenticated);assertEquals("",HostTrustStore.get(app,current,f.port));assertEquals(0,SshKeys.info(app,current,f.port,user).length());
        }
    }
    @Test public void existingTargetKeyIsNeverReplacedByRecovery()throws Exception{
        try(Fixture f=new Fixture()){
            JSONObject other=SshKeys.generate();SshKeys.save(app,current,f.port,user,other);ServerStats result=f.check();
            assertFalse(result.authenticated);assertEquals("",HostTrustStore.get(app,current,f.port));assertEquals(other.getString(SshKeys.PUBLIC),SshKeys.info(app,current,f.port,user).getString(SshKeys.PUBLIC));
        }
    }
    @Test public void conflictingSavedFingerprintBlocksRecovery()throws Exception{
        try(Fixture f=new Fixture()){
            HostTrustStore.trust(app,current,f.port,"SHA256:different-server");ServerStats result=f.check();
            assertFalse(result.authenticated);assertTrue(result.hostKeyMismatch);assertEquals("SHA256:different-server",HostTrustStore.get(app,current,f.port));assertEquals(0,SshKeys.info(app,current,f.port,user).length());
        }
    }
    @Test public void identityForDifferentUserIsNotReused()throws Exception{
        try(Fixture f=new Fixture()){
            SshKeys.clear(app,old,f.port,user);SshKeys.save(app,old,f.port,"other",f.identity);
            assertFalse(f.check().authenticated);assertEquals("",HostTrustStore.get(app,current,f.port));assertEquals(0,SshKeys.info(app,current,f.port,user).length());
        }
    }
    @Test public void identityForDifferentPortIsNotReused()throws Exception{
        try(Fixture f=new Fixture()){
            int otherPort=f.port==65535?f.port-1:f.port+1;
            HostTrustStore.clear(app,old,f.port);SshKeys.save(app,old,otherPort,user,f.identity);HostTrustStore.trust(app,old,otherPort,f.pin);
            assertFalse(f.check().authenticated);assertEquals("",HostTrustStore.get(app,current,f.port));assertEquals(0,SshKeys.info(app,current,f.port,user).length());
        }
    }
    @Test public void unreadableExistingTargetSecretIsNotOverwritten()throws Exception{
        try(Fixture f=new Fixture()){
            SshKeys.save(app,current,f.port,user,f.identity);
            String id=org.robolectric.util.ReflectionHelpers.callStaticMethod(SshKeys.class,"storageId",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(Context.class,app),org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,current),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,f.port),org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,user));
            RouteIdentityTest.Secrets.store.put(id,"unreadable-existing-data");assertFalse(f.check().authenticated);
            assertEquals("unreadable-existing-data",RouteIdentityTest.Secrets.store.get(id));assertEquals("",HostTrustStore.get(app,current,f.port));
        }
    }
}
