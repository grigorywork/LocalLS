package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class SecurityInstrumentedTest {
    private Context context;
    @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE).edit().clear().commit();}
    @After public void after(){context.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE).edit().clear().commit();}
    @Test public void generatedKeyIsStrongEncryptedAndBoundToEndpointAccount()throws Exception{
        JSONObject key=SshKeys.generate();String privateKey=key.getString(SshKeys.PRIVATE);
        assertTrue(key.getString(SshKeys.PUBLIC).startsWith("ssh-rsa "));assertTrue(key.getString("fingerprint").startsWith("SHA256:"));
        SshKeys.save(context,"key-fixture.invalid",8022,"fixture",key);
        String persisted=context.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE).getAll().toString();assertFalse(persisted.contains(privateKey));assertFalse(persisted.contains("BEGIN RSA PRIVATE KEY"));
        SshCredentials loaded=SshKeys.keyCredentials(context,"key-fixture.invalid",8022,"fixture");assertTrue(loaded.keyMode());assertTrue(privateKey.equals(loaded.privateKey));
        assertFalse(SshKeys.keyCredentials(context,"other.invalid",8022,"fixture").available());assertFalse(SshKeys.keyCredentials(context,"key-fixture.invalid",22,"fixture").available());assertFalse(SshKeys.keyCredentials(context,"key-fixture.invalid",8022,"other").available());
        SshKeys.prefs(context).edit().putString(SshKeys.MODE,"key").commit();SshCredentials missing=SshKeys.credentials(context,"other.invalid",8022,"fixture","a-password-must-not-be-used");assertTrue(missing.keyMode());assertFalse(missing.available());assertTrue(missing.password.isEmpty());
        SshKeys.clear(context,"key-fixture.invalid",8022,"fixture");assertFalse(SshKeys.keyCredentials(context,"key-fixture.invalid",8022,"fixture").available());
    }
    @Test public void actualSftpKeyEnrollmentAndOpenSshImportWorkWithoutPasswordFallback()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue(args.containsKey("fixtureHost"));
        String host=args.getString("fixtureHost"),user=args.getString("fixtureUser"),password=args.getString("fixturePassword");int port=Integer.parseInt(args.getString("fixturePort"));
        String pin=SshClient.probeFingerprint(host,port,user);
        JSONObject key=SshKeys.generate();SftpClient.installPublicKey(host,port,user,SshCredentials.password(password),pin,key.getString(SshKeys.PUBLIC));
        SshCredentials identity=SshCredentials.key(key.getString(SshKeys.PRIVATE),"");assertFalse(SftpClient.list(host,port,user,identity,pin,"/").isEmpty());
        ServerStats stats=SshClient.probe(host,port,user,identity,pin);assertTrue(stats.error,stats.authenticated);
        assertFalse(SshClient.probe(host,port,user,identity,pin+"changed").authenticated);
        JSONObject wrong=SshKeys.generate();assertFalse(SshClient.probe(host,port,user,SshCredentials.key(wrong.getString(SshKeys.PRIVATE),""),pin).authenticated);
        ByteArrayOutputStream imported=new ByteArrayOutputStream();SftpClient.download(host,port,user,password,pin,"/incoming-openssh-test-key",imported,0,null);
        String privateKey=imported.toString(StandardCharsets.UTF_8.name());JSONObject info=SshKeys.inspect(privateKey,"");SftpClient.installPublicKey(host,port,user,SshCredentials.password(password),pin,info.getString(SshKeys.PUBLIC));
        SshKeys.save(context,host,port,user,info.put(SshKeys.PRIVATE,privateKey).put(SshKeys.PHRASE,""));SshKeys.prefs(context).edit().putString(SshKeys.MODE,"key").commit();
        assertFalse(SftpClient.list(context,host,port,user,"",pin,"/").isEmpty());
    }
    @Test public void httpsAgentCertificateIsCheckedBeforeTokenIsSent()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();org.junit.Assume.assumeTrue(args.containsKey("fixtureTlsPort"));
        String host=args.getString("fixtureHost"),token=args.getString("fixtureAgentToken");int port=Integer.parseInt(args.getString("fixtureTlsPort"));
        String pin=AgentTls.probe(host,port);assertTrue(pin.matches("SHA256:[a-f0-9]{64}"));
        AgentResult noTrust=AgentClient.request(host,port,token,"status");assertFalse(noTrust.success);assertFalse(noTrust.error.contains(token));
        AgentResult good=AgentClient.request(host,port,token,"status",pin,false);assertTrue(good.error,good.success);
        AgentResult changed=AgentClient.request(host,port,token,"status","SHA256:"+new String(new char[64]).replace('\0','0'),false);assertFalse(changed.success);assertFalse(changed.error.contains(token));
        AgentResult wrong=AgentClient.request(host,port,java.util.UUID.randomUUID().toString(),"status",pin,false);assertFalse(wrong.success);
    }
}
