package com.bitpoint.homeservercontrol;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The same generic guide is bundled offline and published with the release. */
public class PhoneSetupGuideActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int gap=Math.round(16*getResources().getDisplayMetrics().density);content.setPadding(gap,gap,gap,gap);
        TextView guide=new TextView(this);guide.setTextSize(16);guide.setTextIsSelectable(true);
        try(InputStream input=getAssets().open("phone-setup.txt");ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096];int read;
            while((read=input.read(bytes))!=-1) {if(output.size()+read>65536)throw new java.io.IOException();output.write(bytes,0,read);}
            guide.setText(new String(output.toByteArray(),StandardCharsets.UTF_8));
        } catch(Exception error) {guide.setText("Не удалось открыть встроенную инструкцию. Она также доступна рядом с APK на GitHub.");}
        content.addView(guide);
        Button online=new Button(this);online.setText("Инструкция на GitHub");content.addView(online);
        online.setOnClickListener(v -> {
            try {
                Uri uri=Uri.parse("https://github.com/grigorywork/LocalLS/blob/main/android/app/src/main/assets/phone-setup.txt");
                startActivity(new Intent(Intent.ACTION_VIEW,uri));
            } catch(Exception error) {Toast.makeText(this,"Открой страницу релиза LocalLS на GitHub",Toast.LENGTH_LONG).show();}
        });
        Button back=new Button(this);back.setText("Назад");content.addView(back);back.setOnClickListener(v -> finish());
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
    }
}
