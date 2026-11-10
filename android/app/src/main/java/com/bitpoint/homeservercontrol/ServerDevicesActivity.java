package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** Each client owns its own private key; revocation disconnects that client's live sessions. */
public class ServerDevicesActivity extends BaseActivity {
    private LinearLayout content;
    @Override protected void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(SettingsUi.dp(this,16),SettingsUi.dp(this,8),SettingsUi.dp(this,16),SettingsUi.dp(this,8));scroll.addView(content);setContentView(scroll);}
    @Override protected void onResume(){super.onResume();render();}
    private void text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(SettingsUi.dp(this,4),SettingsUi.dp(this,8),SettingsUi.dp(this,4),SettingsUi.dp(this,8));content.addView(t);}
    private void button(String title,Runnable action){Button b=new Button(this);b.setText(title);content.addView(b);b.setOnClickListener(v->action.run());}
    private void render(){
        content.removeAllViews();text(AppText.tr("Разрешённые устройства"),22);
        try{
            var devices=ServerDevices.list(this);text(devices.size()+AppText.tr(" из 10 · отдельный ключ у каждого устройства"),16);
            for(var device:devices){
                text(device.name+" · "+(EmbeddedServerService.onlineDevices().contains(EmbeddedSftpServer.publicFingerprint(device.key))?AppText.tr("В сети"):AppText.tr("Не в сети")),18);text(EmbeddedSftpServer.publicFingerprint(device.key),12);
                button(AppText.tr("Переименовать «")+device.name+"»",()->{EditText input=new EditText(this);input.setText(device.name);new AlertDialog.Builder(this).setTitle(AppText.tr("Имя устройства")).setView(input).setNegativeButton(AppText.tr("Отмена"),null).setPositiveButton(AppText.tr("Сохранить"),(d,w)->{try{ServerDevices.rename(this,device.key,input.getText().toString());render();}catch(Exception e){message(e.getMessage());}}).show();});
                button(AppText.tr("Отозвать доступ"),()->new AlertDialog.Builder(this).setTitle(AppText.tr("Отозвать доступ «")+device.name+"»?").setMessage(AppText.tr("Его активные соединения будут закрыты. Остальные устройства сохранят доступ. Для повторного добавления понадобится новое подтверждение на сервере.")).setNegativeButton(AppText.tr("Отмена"),null).setPositiveButton(AppText.tr("Отозвать"),(d,w)->{try{EmbeddedServerService.revokeDevice(this,device.key);render();}catch(Exception e){message(AppText.tr("Не удалось отозвать доступ. ")+e.getMessage());}}).show());
            }
            if(devices.size()<EmbeddedSftpServer.MAX_DEVICES)button(AppText.tr("Добавить устройство по QR"),()->startActivity(new Intent(this,QrPairingActivity.class).putExtra("server",true)));
            else text(AppText.tr("Лимит достигнут. Для нового устройства сначала отзови один доступ."),14);
        }catch(Exception error){text(AppText.tr("Список устройств недоступен. Прежние ключи не изменены."),16);}
        button(AppText.tr("Назад"),this::finish);
        polishControls();
    }
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
