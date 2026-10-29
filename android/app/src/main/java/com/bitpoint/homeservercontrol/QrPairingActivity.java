package com.bitpoint.homeservercontrol;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.*;
import java.util.concurrent.*;

/** One scan, pinned SSH key exchange and explicit local approval; optical reply remains compatible. */
public class QrPairingActivity extends BaseActivity {
    private static final int CAMERA=7401,EXPORT_QR=7402,IMPORT_QR=7403;
    private String pendingExport="";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService loginWorker=Executors.newSingleThreadExecutor();
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private Future<?> loginTask;
    private volatile int loginRevision,flowRevision;
    private LinearLayout content;
    private TextView status;
    private TextView syncCode;
    private Button approveQr,replyQr,openFiles,startServer;
    private QrSquareView qrFrame,scannerFrame;
    private ScrollView scroll;
    private String pendingNetworkKey="";
    private boolean approvalRequested;private String announcedPending="";
    private final Runnable refresh=new Runnable(){@Override public void run(){if(closed||!isUiActive())return;if(serverMode){if(invitation==null&&EmbeddedServerService.running)showInvitation();else refreshPending();if(startServer!=null){boolean pending=EmbeddedServerService.launchPending();startServer.setVisibility(EmbeddedServerService.running?android.view.View.GONE:android.view.View.VISIBLE);startServer.setEnabled(!pending);startServer.setText(pending?"Запуск сервера…":"Включить сервер и показать QR");}}ui.postDelayed(this,500);}};
    private ImageView qr;
    private String qrPayload="";
    private int qrBitmapSize;
    private DecoratedBarcodeView scanner;
    private QrPairing invitation;
    private boolean serverMode,busy,qrBeforeScan;
    private volatile boolean closed;
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        serverMode=getIntent().getBooleanExtra("server",false);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setPadding(SettingsUi.dp(this,16),SettingsUi.dp(this,8),SettingsUi.dp(this,16),SettingsUi.dp(this,8));
        scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        label(serverMode?"QR доступа к серверу":"Подключиться по QR",20);status=label("",16);
        syncCode=label("",28);syncCode.setGravity(android.view.Gravity.CENTER);syncCode.setVisibility(android.view.View.GONE);
        button(serverMode?"Сканировать ответный QR":"Сканировать QR",this::scan);
        button("Открыть QR-код из файла",this::importQr);
        if(serverMode){
            startServer=button("Включить сервер и показать QR",this::startOwnServer);
            startServer.setVisibility(EmbeddedServerService.running?android.view.View.GONE:android.view.View.VISIBLE);
            approveQr=button("Коды совпадают — разрешить вход",this::confirmNetwork);approveQr.setVisibility(android.view.View.GONE);
            button("Ключ безопасности сервера",()->new AlertDialog.Builder(this).setTitle("Ключ сервера").setMessage(EmbeddedServerService.running?EmbeddedServerService.fingerprint:"Сначала включи сервер").setPositiveButton("Закрыть",null).show());
            button("Сохранить QR для компьютера",this::exportQr);
            button("Обновить QR",()->{ServerInvitation.reset();if(invitation!=null)EmbeddedServerService.endQrPairing(invitation.nonce);invitation=null;pendingNetworkKey="";approvalRequested=false;syncCode.setVisibility(android.view.View.GONE);showInvitation();});
        }else{
            replyQr=button("Показать ответный QR",()->{if(qr!=null){setQrVisible(true);scroll.post(()->scroll.smoothScrollTo(0,0));}});replyQr.setVisibility(android.view.View.GONE);
            openFiles=button("Открыть файлы",()->{startActivity(new Intent(this,FilesActivity.class));finish();});openFiles.setVisibility(android.view.View.GONE);
        }
        if(serverMode)button("Разрешённые устройства (до 7)",()->startActivity(new Intent(this,ServerDevicesActivity.class)));
        Button back=button("Назад",this::finish);back.setOnClickListener(v->finish());setContentView(screen);
        if(saved!=null)pendingExport=saved.getString("qr_export","");
        if(saved!=null)try{invitation=QrPairing.decode(saved.getString("invitation"),System.currentTimeMillis());}catch(Exception ignored){}
        if(!serverMode&&invitation==null)invitation=QrPairingState.pending(this);
        if(!serverMode&&invitation!=null&&!QrPairingState.matchesCurrent(this,invitation))invitation=null;
        if(serverMode)showInvitation();else if(invitation!=null)acceptServer(invitation,false);
        else if(!showSavedBinding())status.setText("На другом телефоне открой «Сервер → Доступ и QR». Сканируй QR один раз. Сохранённый доступ действует без ограничения времени.");
    }
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setPadding(SettingsUi.dp(this,4),SettingsUi.dp(this,8),SettingsUi.dp(this,4),SettingsUi.dp(this,8));content.addView(t);return t;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTag("qr_action");content.addView(b);b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    private void startOwnServer(){
        if(android.os.Build.VERSION.SDK_INT<26){status.setText("Для сервера нужен Android 8 или новее");return;}
        if(EmbeddedServerService.running||EmbeddedServerService.launchPending())return;
        try{EmbeddedServerService.start(this);startServer.setEnabled(false);status.setText("Запускаем сервер с сохранёнными ключами…");}
        catch(Exception error){status.setText("Сервер не запущен. Проверь его настройки");}
    }
    private void showInvitation(){
        try{
            if(!EmbeddedServerService.running)throw new IllegalStateException("Сначала включи «Мой сервер». Без разрешённого ключа вход остаётся закрыт");
            if(ServerInvitation.renewCompleted(this))invitation=null;invitation=ServerInvitation.get(this,invitation);
            status.setText("Сканируй QR другим телефоном. Сверь код и разреши вход один раз. Привязка бессрочная.\n"+invitation.host+":"+invitation.port);
            showCode(invitation.encode());
        }catch(Exception error){status.setText(error.getMessage());if(qr!=null)setQrVisible(false);}
    }
    private void showCode(String text)throws Exception{
        if(qr==null){
            qrFrame=new QrSquareView(this);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.gravity=android.view.Gravity.CENTER_HORIZONTAL;
            content.addView(qrFrame,1,size);qr=new ImageView(this);qr.setBackgroundColor(Color.WHITE);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            qr.setContentDescription("QR LocalLS: публичные данные подключения");qrFrame.addView(qr,new android.widget.FrameLayout.LayoutParams(-1,-1));
            qr.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
                int side=Math.min(right-left,bottom-top);
                if(side>0&&side!=qrBitmapSize&&!qrPayload.isEmpty())try{renderQr(side);}catch(Exception error){status.setText("Не удалось подготовить читаемый QR. Обнови код");setQrVisible(false);}
            });
        }
        qrPayload=text;renderQr(qr.getWidth()>0?Math.min(qr.getWidth(),qr.getHeight()):720);setQrVisible(true);
    }
    private void renderQr(int size)throws Exception{qr.setImageBitmap(codeBitmap(qrPayload,size));qrBitmapSize=size;}
    private void setQrVisible(boolean visible){qr.setVisibility(visible?android.view.View.VISIBLE:android.view.View.GONE);qrFrame.setVisibility(qr.getVisibility());}
    static Bitmap codeBitmap(String text)throws Exception{return codeBitmap(text,720);}
    static Bitmap codeBitmap(String text,int size)throws Exception{
        java.util.Map<EncodeHintType,Object> hints=new java.util.EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,4);
        // Dense RSA replies can confuse a finder's alignment search for some masks.
        // Keep the payload intact and display only an image that round-trips exactly.
        for(int mask=-1;mask<8;mask++){
            if(mask>=0)hints.put(EncodeHintType.QR_MASK_PATTERN,mask);
            BitMatrix matrix=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size,hints);
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            try{
                Result general=new MultiFormatReader().decode(qrPixels(pixels,size));
                if(general.getBarcodeFormat()!=BarcodeFormat.QR_CODE||!text.equals(general.getText()))continue;
                Result camera=new com.google.zxing.qrcode.QRCodeReader().decode(qrPixels(pixels,size));
                if(text.equals(camera.getText()))return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
            }catch(ReaderException unreadable){/* Try another standard mask, preserving all encoded bytes. */}
        }
        throw new WriterException("Не удалось подготовить читаемый QR. Обнови код");
    }
    private static BinaryBitmap qrPixels(int[] pixels,int size){
        return new BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new RGBLuminanceSource(size,size,pixels)));
    }
    private void scan(){
        if(closed||isFinishing())return;
        if(!serverMode){flowRevision++;busy=false;loginRevision++;if(loginTask!=null)loginTask.cancel(true);openFiles.setVisibility(android.view.View.GONE);}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}
        if(scanner!=null)return;
        scanner=new DecoratedBarcodeView(this);scanner.setStatusText("Наведи камеру на QR LocalLS");
        scanner.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(java.util.Collections.singletonList(BarcodeFormat.QR_CODE)));
        scannerFrame=new QrSquareView(this);scannerFrame.addView(scanner,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams frame=new LinearLayout.LayoutParams(-1,-2);frame.gravity=android.view.Gravity.CENTER_HORIZONTAL;content.addView(scannerFrame,0,frame);
        qrBeforeScan=qr!=null&&qr.getVisibility()==android.view.View.VISIBLE;if(qr!=null)setQrVisible(false);scroll.post(()->scroll.smoothScrollTo(0,0));
        scanner.decodeSingle(new BarcodeCallback(){@Override public void barcodeResult(BarcodeResult result){String text=result.getText();stopScanner();readCode(text);}});scanner.resume();
    }
    private void stopScanner(){if(scanner!=null){scanner.pause();content.removeView(scannerFrame);scanner=null;scannerFrame=null;if(qrBeforeScan&&qr!=null&&!closed)setQrVisible(true);}}
    private boolean currentFlow(int revision){return !closed&&!isFinishing()&&!isDestroyed()&&revision==flowRevision;}
    void readCode(String text){
        if(closed||isFinishing())return;
        final int revision=++flowRevision;loginRevision++;if(loginTask!=null)loginTask.cancel(true);
        busy=true;worker.execute(()->{
            try{QrPairing code=QrPairing.decode(text,System.currentTimeMillis());runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;confirm(code);});}
            catch(Exception error){runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;status.setText("Не удалось прочитать QR: "+error.getMessage());});}
        });
    }
    private void confirm(QrPairing code){
        if(closed||isFinishing()||isDestroyed())return;
        if(serverMode){
            if(invitation==null || invitation.expires<=System.currentTimeMillis() || !invitation.matches(code)
                    || !EmbeddedServerService.running || !invitation.pin.equals(EmbeddedServerService.fingerprint)){
                status.setText("Ответ не относится к действующему QR этого сервера. Покажи новый код");return;
            }
            if(ServerDevices.contains(this,code.publicKey)){status.setText("Этот ключ уже разрешён. Можно подключаться");return;}
            if(ServerDevices.count(this)>=7){status.setText("Уже добавлено 7 устройств. Сначала отзови один доступ");return;}
            final String fp;try{fp=EmbeddedSftpServer.publicFingerprint(code.publicKey);}catch(Exception e){status.setText("Ключ недоступен");return;}
            String shortCode;try{shortCode=invitation.syncCode(code.publicKey);}catch(Exception e){status.setText("Не удалось сверить ключ");return;}
            new AlertDialog.Builder(this).setTitle("Разрешить этот телефон?").setMessage("Код на обоих телефонах должен совпадать:\n"+shortCode+"\n\nКлюч клиента:\n"+fp)
                    .setNegativeButton("Отмена",null).setPositiveButton("Разрешить",(d,w)->{
                        try{if(closed||invitation==null||invitation.expires<=System.currentTimeMillis()||!invitation.matches(code)||!EmbeddedServerService.running||!code.pin.equals(EmbeddedServerService.fingerprint)||code.port!=EmbeddedServerService.boundPort)throw new IllegalStateException("Приглашение изменилось или истекло");EmbeddedServerService.approveOpticalPairing(this,code.nonce,code.publicKey);status.setText("Ключ передан серверу. Дождись подтверждения в «Мой сервер»");}
                        catch(Exception error){status.setText("Сервер не принял ключ: "+error.getMessage());}
                    }).show();
        }else{
            if(!"server".equals(code.type)){status.setText("На клиенте нужен QR сервера");return;}
            String old=HostTrustStore.get(this,code.host,code.port);
            if(!old.isEmpty()&&!old.equals(code.pin)){status.setText("Ключ сервера отличается от сохранённого. Подключение заблокировано");return;}
            if(old.equals(code.pin)&&SshKeys.hasStoredKey(this,code.host,code.port,code.user)){acceptServer(code,true);return;}
            new AlertDialog.Builder(this).setTitle("Подключиться к серверу?")
                    .setMessage(code.host+":"+code.port+" · "+code.user+"\n\nСверь ключ безопасности на экране сервера:\n"+code.pin+"\n\nИспользуем прежний ключ клиента. Если ключа для этого сервера ещё нет, создадим его на этом телефоне.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Ключ совпадает",(d,w)->acceptServer(code,true)).show();
        }
    }
    private boolean showSavedBinding(){
        FileConnection saved=FileConnection.current(this);if(saved.pin.isEmpty()||!SshKeys.hasStoredKey(this,saved.host,saved.port,saved.user))return false;
        final int revision=flowRevision;
        status.setText("Проверяем сохранённую привязку…");worker.execute(()->{
            org.json.JSONObject info=SshKeys.info(this,saved.host,saved.port,saved.user);boolean ready=info.has(SshKeys.PUBLIC);boolean durable=false;try{durable=ready&&QrPairingState.confirmed(this,saved,info.getString(SshKeys.PUBLIC));}catch(Exception ignored){}final boolean confirmed=durable;
            runOnUiThread(()->{if(!currentFlow(revision)||invitation!=null||!saved.matches(FileConnection.current(this).bundle()))return;
                status.setText(ready?(confirmed?"Ключи подключения к "+saved.host+":"+saved.port+" сохранены без ограничения времени. Для разрешённого телефона повторный код не нужен.":"Адрес и ключ сохранены. Если этот телефон ещё не разрешён, подтверди вход на сервере. Для уже разрешённого телефона повторный QR не нужен."):"Сохранённый ключ недоступен. Открой «Ключ доступа» для восстановления");
                if(ready)openFiles.setVisibility(android.view.View.VISIBLE);
            });
        });return true;
    }
    private void acceptServer(QrPairing code,boolean configure){
        if(openFiles!=null)openFiles.setVisibility(android.view.View.GONE);
        final int revision=++flowRevision;loginRevision++;if(loginTask!=null)loginTask.cancel(true);
        busy=true;status.setText("Готовим публичный ключ…");worker.execute(()->{
            try{
                VerifiedCopy.check();if(closed||revision!=flowRevision||code.expires<=System.currentTimeMillis())throw new IllegalStateException("QR истёк или экран закрыт");
                String old=HostTrustStore.get(this,code.host,code.port);if(!old.isEmpty()&&!old.equals(code.pin))throw new IllegalStateException("Ключ сервера изменился");
                org.json.JSONObject info=configure?SshKeys.pairingInfo(this,code.host,code.port,code.user,code.pin):SshKeys.info(this,code.host,code.port,code.user);
                VerifiedCopy.check();if(closed||revision!=flowRevision)throw new java.io.InterruptedIOException("Экран закрыт");
                if(!info.has(SshKeys.PUBLIC))throw new IllegalStateException("Прежний ключ недоступен. Восстанови его в «Ключ доступа»");
                String encoded=code.reply(info.getString(SshKeys.PUBLIC)).encode(),fp=info.getString("fingerprint");
                runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;
                    try{if(code.expires<=System.currentTimeMillis())throw new IllegalStateException("QR истёк");
                        if(configure){HostTrustStore.pair(this,code.host,code.port,code.user,code.pin);getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("show_client",true).apply();}
                        if(!QrPairingState.matchesCurrent(this,code))throw new IllegalStateException("Подключение изменилось");
                        if(!configure&&QrPairingState.confirmed(this,code,info.getString(SshKeys.PUBLIC))){invitation=null;showSavedBinding();return;}
                        QrPairingState.remember(this,code,info.getString(SshKeys.PUBLIC));
                        invitation=code;showCode(encoded);setQrVisible(false);replyQr.setVisibility(android.view.View.VISIBLE);
                        syncCode.setText("Код сверки\n"+code.syncCode(info.getString(SshKeys.PUBLIC)));syncCode.setVisibility(android.view.View.VISIBLE);
                        status.setText("Сверь код на сервере и подтверди там вход. Ждём разрешения…\n\nКлюч клиента:\n"+fp);startLogin(code,info.getString(SshKeys.PUBLIC));
                    }catch(Exception error){status.setText("Подключение не настроено: "+error.getMessage());}
                });
            }catch(Exception error){runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;status.setText("Подключение не настроено: "+error.getMessage());});}
        });
    }
    private void refreshPending(){
        if(invitation==null)return;
        try{
            if(invitation.expires<=System.currentTimeMillis()||!EmbeddedServerService.running||!invitation.pin.equals(EmbeddedServerService.fingerprint)||invitation.port!=EmbeddedServerService.boundPort){
                EmbeddedServerService.endQrPairing(invitation.nonce);approveQr.setVisibility(android.view.View.GONE);syncCode.setVisibility(android.view.View.GONE);if(qr!=null)setQrVisible(false);
                boolean bound=ServerDevices.count(this)>0;
                status.setText(bound?"Сохранённый доступ действует без ограничения времени. Повторный код не нужен. Если сервер выключен, включи его снова.":"QR истёк или сервер изменился. Обнови код");return;
            }
            EmbeddedServerService.beginQrPairing(invitation);String key=EmbeddedServerService.pendingQrKey(invitation.nonce);if(key.isEmpty())return;
            boolean newRequest=!key.equals(pendingNetworkKey);if(newRequest)approvalRequested=false;pendingNetworkKey=key;syncCode.setText("Код сверки\n"+invitation.syncCode(key));syncCode.setVisibility(android.view.View.VISIBLE);
            boolean allowed=ServerDevices.contains(this,key);
            if(allowed&&!EmbeddedServerService.waitingForClient){status.setText("Телефон привязан без ограничения времени. Повторный код не нужен");approveQr.setVisibility(android.view.View.GONE);if(qr!=null)setQrVisible(false);return;}
            if(!allowed&&ServerDevices.count(this)>=7){status.setText("Добавлено 7 устройств. Сначала отзови один доступ");approveQr.setVisibility(android.view.View.GONE);return;}
            status.setText(approvalRequested?"Подтверждаем вход…":"Телефон запрашивает вход. Сверь код на обоих экранах и подтверди совпадение");approveQr.setVisibility(android.view.View.VISIBLE);approveQr.setEnabled(!approvalRequested);
            String request=invitation.nonce+"\n"+key;if(!request.equals(announcedPending)&&isUiActive()){announcedPending=request;revealPendingApproval(request);}
        }catch(Exception error){status.setText("Не удалось подготовить подключение. Обнови QR");}
    }
    private void revealPendingApproval(String request){
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener=new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
            private void remove(){if(content.getViewTreeObserver().isAlive())content.getViewTreeObserver().removeOnGlobalLayoutListener(this);}
            @Override public void onGlobalLayout(){
                if(closed||!isUiActive()||invitation==null||!request.equals(invitation.nonce+"\n"+pendingNetworkKey)||approveQr.getVisibility()!=android.view.View.VISIBLE){remove();return;}
                if(approveQr.getWidth()==0||approveQr.getHeight()==0)return;remove();approveQr.requestRectangleOnScreen(new android.graphics.Rect(0,0,approveQr.getWidth(),approveQr.getHeight()),true);
            }
        };
        content.getViewTreeObserver().addOnGlobalLayoutListener(listener);scroll.post(()->{if(!content.isLayoutRequested()&&!approveQr.isLayoutRequested())listener.onGlobalLayout();});
    }
    private void confirmNetwork(){
        if(invitation==null||pendingNetworkKey.isEmpty()||approvalRequested)return;
        QrApproval.confirm(this,invitation,pendingNetworkKey,()->{approvalRequested=true;refreshPending();},message->status.setText(message));
    }
    private void startLogin(QrPairing code,String publicKey){
        final int revision=++loginRevision;if(loginTask!=null)loginTask.cancel(true);
        loginTask=loginWorker.submit(()->{
            try{
                final SshCredentials credential;synchronized(SshKeys.class){
                    if(!publicKey.equals(SshKeys.info(this,code.host,code.port,code.user).optString(SshKeys.PUBLIC)))throw new SecurityException("Ключ клиента изменился");
                    credential=SshKeys.keyCredentials(this,code.host,code.port,code.user);
                }
                while(!closed&&revision==loginRevision&&System.currentTimeMillis()<code.expires){
                    VerifiedCopy.check();if(!code.pin.equals(HostTrustStore.get(this,code.host,code.port)))throw new SecurityException("Сохранённый ключ сервера изменился");
                    try{
                        if(SftpClient.loginAttempt(code.host,code.port,code.user,credential,code.pin)||SftpClient.pairingAttempt(code.host,code.port,code.user,code.nonce,credential,code.pin)){
                            runOnUiThread(()->{if(closed||isFinishing()||revision!=loginRevision)return;
                                if(!code.pin.equals(HostTrustStore.get(this,code.host,code.port))||!publicKey.equals(SshKeys.info(this,code.host,code.port,code.user).optString(SshKeys.PUBLIC))){status.setText("Сохранённые ключи изменились. Проверь подключение");return;}
                                try{QrPairingState.complete(this,code,publicKey);}catch(Exception error){status.setText("Параметры сохранены, но подтверждение привязки не записано. Проверь свободное место");return;}
                                syncCode.setVisibility(android.view.View.GONE);replyQr.setVisibility(android.view.View.GONE);
                                status.setText("Телефон привязан. Вход подтверждён; повторный код больше не нужен");openFiles.setVisibility(android.view.View.VISIBLE);if(qr!=null)setQrVisible(false);});return;
                        }
                        runOnUiThread(()->{if(!closed&&revision==loginRevision)status.setText("Сервер найден. Сверь код и разреши вход на телефоне-сервере. Ждём подтверждения…");});
                    }catch(SecurityException error){throw error;}catch(Exception unavailable){VerifiedCopy.check();runOnUiThread(()->{if(!closed&&revision==loginRevision)status.setText("Нет соединения с сервером "+code.host+":"+code.port+". Подключи устройства к одной сети Wi-Fi и проверь, что сервер включён. Повторяю подключение…");});}
                    Thread.sleep(1200);
                }
                if(!closed&&revision==loginRevision)runOnUiThread(()->{if(!closed&&revision==loginRevision){status.setText("Адрес и ключ сохранены. Для разрешённого телефона повторный QR не нужен: проверь сеть и включи сервер. Новый QR нужен только для ещё не подтверждённой привязки.");syncCode.setVisibility(android.view.View.GONE);replyQr.setVisibility(android.view.View.GONE);}});
            }catch(Exception error){runOnUiThread(()->{if(!closed&&!isFinishing()&&revision==loginRevision)status.setText(error instanceof SecurityException?"Ключ сервера или клиента изменился. Подключение заблокировано; сохранённые ключи не изменены.":"Подключение остановлено. Сохранённые ключи не изменены. Проверь сеть и доступность ключа");});}
        });
    }
    private void exportQr(){
        try{
            if(!serverMode||!EmbeddedServerService.running)throw new IllegalStateException();showInvitation();if(invitation==null||invitation.expires<=System.currentTimeMillis())throw new IllegalStateException();
            pendingExport=invitation.encode();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"LocalLS-server-QR.png"),EXPORT_QR);
        }catch(Exception error){status.setText("Сначала включи сервер и покажи действующий QR");}
    }
    private void importQr(){
        stopScanner();
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),IMPORT_QR);}
        catch(android.content.ActivityNotFoundException unavailable){status.setText("Системный выбор изображений недоступен. Можно сканировать камерой");}
    }
    private void readImage(android.net.Uri source){
        final int revision=++flowRevision;loginRevision++;if(loginTask!=null)loginTask.cancel(true);
        busy=true;status.setText("Читаем QR-код из изображения…");worker.execute(()->{
            try{QrPairing code=QrPairing.decode(QrImageReader.read(getContentResolver(),source),System.currentTimeMillis());runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;confirm(code);});}
            catch(Exception error){runOnUiThread(()->{if(!currentFlow(revision))return;busy=false;status.setText("Не удалось прочитать QR-код: "+(error instanceof java.io.IOException||error instanceof IllegalArgumentException?error.getMessage():"изображение недоступно"));});}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==IMPORT_QR){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&!closed)readImage(data.getData());return;}
        if(request!=EXPORT_QR)return;String payload=pendingExport;pendingExport="";
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri target=data.getData();if(!"content".equals(target.getScheme())){status.setText("Выбери файл через системное окно Android");return;}
        busy=true;worker.execute(()->{
            try{
                QrPairing code=QrPairing.decode(payload,System.currentTimeMillis());
                if(closed||!serverMode||!EmbeddedServerService.running||invitation==null||!invitation.nonce.equals(code.nonce)||!code.pin.equals(EmbeddedServerService.fingerprint)||code.port!=EmbeddedServerService.boundPort)throw new IllegalStateException();
                Bitmap bitmap=codeBitmap(payload);
                try(java.io.OutputStream out=getContentResolver().openOutputStream(target,"w")){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException();}finally{bitmap.recycle();}
                runOnUiThread(()->{if(!closed){busy=false;status.setText("Публичный QR сохранён. На компьютере открой «Подключение → Считать QR из фото». Ключ компьютера отдельно подтверди на сервере. QR действует 10 минут; сохранённая привязка бессрочная.");}});
            }catch(Exception error){runOnUiThread(()->{if(!closed){busy=false;status.setText("QR не сохранён: сервер или приглашение изменились. Покажи новый QR");}});}
        });
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==CAMERA){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();else status.setText("Для сканирования разреши камеру в настройках Android. Ручное подключение остаётся доступным");}}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("qr_export",pendingExport);if(invitation!=null)try{state.putString("invitation",invitation.encode());}catch(Exception ignored){}super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);announcedPending="";stopScanner();super.onPause();}
    @Override protected void onDestroy(){closed=true;flowRevision++;loginRevision++;if(loginTask!=null)loginTask.cancel(true);loginWorker.shutdownNow();ui.removeCallbacksAndMessages(null);stopScanner();worker.shutdownNow();super.onDestroy();}
}
