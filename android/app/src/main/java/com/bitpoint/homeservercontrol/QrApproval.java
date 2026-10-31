package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.Consumer;

/** The same explicit, fresh-request check for inline and full-screen owner approval. */
final class QrApproval {
    static AlertDialog confirm(Activity owner,QrPairing invitation,String key,Runnable submitted,Consumer<String> failed){
        try{
            validate(owner,invitation,key);
            return new AlertDialog.Builder(owner).setTitle("Коды на телефонах совпадают?")
                    .setMessage(invitation.syncCode(key)+"\n\nКлюч клиента:\n"+EmbeddedSftpServer.publicFingerprint(key))
                    .setNegativeButton("Отмена",null).setPositiveButton("Коды совпадают — разрешить",(dialog,which)->{
                        try{validate(owner,invitation,key);EmbeddedServerService.approveQrPairing(owner,invitation.nonce,key);submitted.run();}
                        catch(Exception error){failed.accept(error.getMessage());}
                    }).show();
        }catch(Exception error){failed.accept(error.getMessage());return null;}
    }
    private static void validate(Activity owner,QrPairing invitation,String key)throws Exception{
        if(owner.isFinishing()||owner.isDestroyed()||invitation==null||!"server".equals(invitation.type)
                ||invitation.expires<=System.currentTimeMillis()||!EmbeddedServerService.running
                ||!invitation.pin.equals(EmbeddedServerService.fingerprint)||invitation.port!=EmbeddedServerService.boundPort
                ||!EmbeddedSftpServer.normalizeAuthorizedKey(key).equals(EmbeddedServerService.pendingQrKey(invitation.nonce)))
            throw new IllegalStateException("Запрос изменился или истёк. Покажи новый QR");
        if(!ServerDevices.contains(owner,key)&&ServerDevices.count(owner)>=7)throw new IllegalStateException("Уже добавлено 7 устройств");
    }
}
