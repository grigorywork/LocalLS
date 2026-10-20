package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.security.*;
import java.security.spec.*;

/** Persistent host identity is encrypted by the app's Android Keystore wrapper, outside the share. */
final class EmbeddedServerIdentity {
    private static final String NAME="embedded_sftp_host_v1";
    static synchronized KeyPair loadOrCreate(Context context) throws Exception {
        String saved=SecurePrefs.loadNamed(context,NAME);
        if(saved!=null && !saved.isEmpty()) {
            JSONObject j=new JSONObject(saved);KeyFactory factory=KeyFactory.getInstance("RSA");
            return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.decode(j.getString("public"),Base64.NO_WRAP))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(j.getString("private"),Base64.NO_WRAP))));
        }
        if(SecurePrefs.containsNamed(context,NAME)) throw new IllegalStateException("Сохранённый host key недоступен; автоматическая замена запрещена");
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);
        KeyPair pair=generator.generateKeyPair();
        SecurePrefs.saveNamed(context,NAME,new JSONObject().put("public",Base64.encodeToString(pair.getPublic().getEncoded(),Base64.NO_WRAP))
                .put("private",Base64.encodeToString(pair.getPrivate().getEncoded(),Base64.NO_WRAP)).toString());
        return pair;
    }
}
