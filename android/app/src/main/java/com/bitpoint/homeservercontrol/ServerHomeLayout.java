package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Host-only dashboard. UI visibility never changes the server lifetime, keys or authorizations. */
public final class ServerHomeLayout extends ViewGroup {
    private LinearLayout status,resources,access,devices,folders,footer,options,deviceHeader,incoming;private StatusRibbon timing;
    private TextView battery,space,uptime,caption,deviceTitle,receipt,receive,deviceSummary,folderTitle,deviceCount;
    private final List<ServerDeviceRow> deviceRows=new ArrayList<>();
    private final List<TextView> sectionHeadings=new ArrayList<>();
    private final Map<TextView,Float> fonts=new LinkedHashMap<>();
    private ServerHealthPanel health;private long lastProbe;private int probePort;private String probePin="";
    private ImageView image;private Button toggle;private View qrFrame;private BatteryGaugeDrawable gauge=new BatteryGaugeDrawable(getContext());
    private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int qrRevision,allowedCount,headerInset;
    void setHeaderInset(int inset,int viewportHeight){headerInset=inset==0?0:Math.max(0,inset+gapForHeight(viewportHeight-getPaddingTop()-getPaddingBottom())-getPaddingTop());}
    // The shell and dashboard must use the same usable viewport and responsive gutter.
    boolean hasHeaderColumn(int width,int height){
        int aw=width-getPaddingLeft()-getPaddingRight(),ah=height-getPaddingTop()-getPaddingBottom();
        return aw>ah*1.25f&&ah>=dp(500);
    }
    int headerColumnWidth(int width,int height){
        int aw=width-getPaddingLeft()-getPaddingRight(),ah=height-getPaddingTop()-getPaddingBottom();
        return (aw-gapForHeight(ah))*48/100+getPaddingLeft()+getPaddingRight();
    }
    private int gapForHeight(int available){return dp(available>=dp(740)?8:available>=dp(700)?4:available>=dp(680)?2:1);}
    private android.app.AlertDialog inlineApproval;private String lastPrompt="",submittedKey="";
    private boolean shown,reading,closed,addedSecure,wide,tight;private QrPairing invitation;private Bitmap bitmap;private String lastQrFailure="";private String payload="";private int renderedSize;
    private final List<View> left=new ArrayList<>(),right=new ArrayList<>();private int column,gap;private final Map<View,Integer> heights=new LinkedHashMap<>();
    public ServerHomeLayout(Context c,AttributeSet attrs){super(c,attrs);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private <T extends View>T take(int id){T v=findViewById(id);((ViewGroup)v.getParent()).removeView(v);return v;}
    private LinearLayout box(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);v.setBackground(SurfaceStyle.panel(getContext()));v.setPadding(dp(10),dp(6),dp(10),dp(6));addView(v);return v;}
    private TextView text(LinearLayout p,String s,int size,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(ThemeCatalog.color(getContext(),bold?com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);p.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void heading(TextView view){SurfaceStyle.heading(view);sectionHeadings.add(view);}
    private void row(Button b){b.setTextSize(14);b.setTag("server_panel_action");b.setTag(com.bitpoint.homeservercontrol.ui.R.id.compact_action_marker,true);b.setMinWidth(0);b.setMaxWidth(Integer.MAX_VALUE);b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));}
    private Button button(LinearLayout p,int id,String title){Button b=id==R.id.serverQrToggle?new StripActionButton(getContext()):new Button(getContext());b.setId(id);b.setText(title);p.addView(b);row(b);return b;}
    @Override protected void onFinishInflate(){
        super.onFinishInflate();View wifi=take(R.id.currentWifiAddress);TextView summary=take(R.id.localServerSummary),accessSummary=take(R.id.serverAccessSummary);Button power=take(R.id.serverPowerButton),files=take(R.id.serverLocalFilesButton),destination=take(R.id.serverReceiveButton),settings=take(R.id.localServerQuickButton),background=take(R.id.serverBackgroundButton);receive=take(R.id.serverReceiveSummary);receive.setMaxLines(1);receive.setEllipsize(android.text.TextUtils.TruncateAt.END);receive.setTextSize(11);receive.setPadding(dp(50),0,0,0);Switch client=take(R.id.showClientSwitch);removeAllViews();
        ((WifiAddressView)wifi).compact();
        status=box();status.setId(R.id.localServerCard);LinearLayout stateLine=new LinearLayout(getContext());stateLine.setGravity(Gravity.CENTER_VERTICAL);stateLine.setBaselineAligned(false);status.addView(stateLine,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout stateInfo=new LinearLayout(getContext());stateInfo.setOrientation(LinearLayout.VERTICAL);stateInfo.setGravity(Gravity.CENTER_VERTICAL);stateLine.addView(stateInfo,new LinearLayout.LayoutParams(0,-1,1));stateInfo.addView(summary,new LinearLayout.LayoutParams(-1,-2));stateInfo.addView(wifi,new LinearLayout.LayoutParams(-1,-2));stateInfo.addView(accessSummary,new LinearLayout.LayoutParams(-1,-2));
        row(power);power.setTag("server_panel_power");power.setTextSize(12);LinearLayout.LayoutParams powerSize=new LinearLayout.LayoutParams(dp(88),dp(64));powerSize.gravity=Gravity.CENTER_VERTICAL;stateLine.addView(power,powerSize);
        resources=box();resources.setBackground(null);resources.setPadding(0,0,0,0);resources.setOrientation(LinearLayout.HORIZONTAL);LinearLayout batteryBox=new LinearLayout(getContext());batteryBox.setOrientation(LinearLayout.VERTICAL);batteryBox.setGravity(Gravity.CENTER);batteryBox.setBackground(SurfaceStyle.panel(getContext()));batteryBox.setPadding(dp(10),dp(4),dp(10),dp(4));resources.addView(batteryBox,new LinearLayout.LayoutParams(0,-1,1));battery=text(batteryBox,"—",26,true);battery.setId(R.id.serverPanelBattery);batteryBox.removeView(battery);batteryBox.addView(new MetricValueRow(getContext(),battery,gauge,34,24),new LinearLayout.LayoutParams(-1,-2));TextView charging=text(batteryBox,"Батарея телефона",12,false);charging.setGravity(Gravity.CENTER);charging.setTag("charging");
        LinearLayout diskBox=new LinearLayout(getContext());diskBox.setOrientation(LinearLayout.VERTICAL);diskBox.setGravity(Gravity.CENTER_VERTICAL);diskBox.setBackground(SurfaceStyle.panel(getContext()));diskBox.setPadding(dp(10),dp(4),dp(10),dp(4));resources.addView(diskBox,new LinearLayout.LayoutParams(0,-1,1));space=text(diskBox,"—",24,true);space.setId(R.id.serverPanelSpace);icon(space,com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_storage,22);TextView diskDetail=text(diskBox,"Свободное место",12,false);diskDetail.setPadding(0,0,0,0);diskDetail.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);diskDetail.setTag("disk_detail");((LinearLayout.LayoutParams)batteryBox.getLayoutParams()).rightMargin=dp(4);((LinearLayout.LayoutParams)diskBox.getLayoutParams()).leftMargin=dp(4);
        uptime=new TextView(getContext());uptime.setText("Время работы: —");TextView refreshed=new TextView(getContext());refreshed.setText("Сейчас");timing=new StatusRibbon(getContext(),uptime,refreshed,true);timing.setId(R.id.serverStatusRibbon);addView(timing);
        access=box();heading(text(access,"Подключить устройство",15,true));LinearLayout pairing=new LinearLayout(getContext());pairing.setGravity(Gravity.CENTER_VERTICAL);access.addView(pairing,new LinearLayout.LayoutParams(-1,0,1));
        image=new ImageView(getContext());image.setId(R.id.serverQrButton);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Открыть QR-код и подтверждение нового устройства");image.setImageResource(com.bitpoint.homeservercontrol.ui.R.drawable.ic_qr_shield);image.setClickable(true);qrAppearance(false);qrFrame=image;pairing.addView(image,new LinearLayout.LayoutParams(dp(104),-1));
        LinearLayout explanation=new LinearLayout(getContext());explanation.setOrientation(LinearLayout.VERTICAL);explanation.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams explanatory=new LinearLayout.LayoutParams(0,-1,1);explanatory.leftMargin=dp(12);pairing.addView(explanation,explanatory);caption=text(explanation,"QR-код скрыт",14,true);caption.setId(R.id.serverPanelQrCaption);caption.setVisibility(GONE);health=new ServerHealthPanel(getContext());explanation.addView(health,new LinearLayout.LayoutParams(-1,0,1));toggle=button(explanation,R.id.serverQrToggle,"Показать QR-код");toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(184),-2));toggle.setOnClickListener(v->setQrShown(!shown));
        devices=box();deviceHeader=new LinearLayout(getContext());deviceHeader.setGravity(Gravity.CENTER_VERTICAL);devices.addView(deviceHeader,new LinearLayout.LayoutParams(-1,-2));deviceTitle=text(deviceHeader,"Устройства с доступом",15,true);deviceTitle.setMaxLines(1);deviceTitle.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));heading(deviceTitle);deviceTitle.setBackground(null);deviceHeader.setBackground(SurfaceStyle.rule(getContext(),0,0));deviceCount=text(deviceHeader,"0 из 7",12,false);deviceCount.setId(R.id.serverPanelDeviceCount);deviceCount.setMaxLines(1);deviceCount.setPadding(0,0,0,dp(4));deviceCount.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));deviceHeader.setOnClickListener(v->openDevices());deviceSummary=text(devices,"Разрешённых устройств пока нет",13,false);deviceSummary.setMinHeight(dp(48));deviceTitle.setOnClickListener(v->openDevices());deviceTitle.setClickable(true);deviceTitle.setFocusable(true);deviceSummary.setOnClickListener(v->openDevices());deviceSummary.setClickable(true);
        for(int i=0;i<3;i++){ServerDeviceRow device=new ServerDeviceRow(getContext());devices.addView(device,new LinearLayout.LayoutParams(-1,dp(48)));device.setVisibility(GONE);deviceRows.add(device);}
        folders=box();folderTitle=text(folders,"Файлы и папки",14,true);heading(folderTitle);row(files);row(destination);folders.addView(files);folders.addView(destination);folders.addView(receive,new LinearLayout.LayoutParams(-1,-2));files.setText("Файлы раздачи");destination.setText("Сохранять входящие");
        incoming=box();incoming.setTag("receipts");incoming.setPadding(dp(10),dp(4),dp(10),dp(4));heading(text(incoming,"Последний приём",14,true));receipt=text(incoming,"Поступлений пока нет",13,false);receipt.setId(R.id.serverPanelReceipt);receipt.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));incoming.setOnClickListener(v->getContext().startActivity(new Intent(getContext(),ServerReceiptActivity.class)));incoming.setClickable(true);incoming.setFocusable(true);
        footer=box();footer.setOrientation(LinearLayout.HORIZONTAL);row(settings);row(background);background.setTag("server_panel_caption");background.setTextSize(11);settings.setText("Настройки сервера");background.setText("Работает в фоне");footer.addView(settings,new LinearLayout.LayoutParams(0,-1,2));footer.addView(background,new LinearLayout.LayoutParams(0,-1,1));
        summary.setIncludeFontPadding(false);accessSummary.setIncludeFontPadding(false);summary.setMaxLines(1);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setTextSize(14);accessSummary.setTextSize(12);accessSummary.setMaxLines(1);accessSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        options=new LinearLayout(getContext());options.setOrientation(LinearLayout.VERTICAL);options.setId(R.id.serverHomeOptions);addView(options);options.addView(client);options.setVisibility(GONE);
        left.addAll(Arrays.asList(status,resources,folders,footer));right.addAll(Arrays.asList(access,timing,devices,incoming));remember(this);
    }
    private void icon(TextView label,int resource,int size){android.graphics.drawable.Drawable d=getContext().getDrawable(resource).mutate();d.setTint(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscAccent));d.setBounds(0,0,dp(size),dp(size));label.setCompoundDrawablesRelative(d,null,null,null);label.setCompoundDrawablePadding(dp(8));}
    private void remember(View v){if(v instanceof TextView)fonts.put((TextView)v,((TextView)v).getTextSize());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)remember(((ViewGroup)v).getChildAt(i));}
    private void openDevices(){getContext().startActivity(new Intent(getContext(),ServerDevicesActivity.class));}
    void setQrShown(boolean visible){if(visible&&!shown)lastPrompt="";if(visible&&!shown&&ServerInvitation.renewCompleted(getContext())){invitation=null;submittedKey="";}if(visible&&image.getWidth()>0&&Math.min(image.getWidth(),image.getHeight())<144){getContext().startActivity(new Intent(getContext(),QrPairingActivity.class).putExtra("server",true));return;}qrRevision++;lastQrFailure="";shown=visible;qrAppearance(visible);if(!visible){payload="";image.setImageResource(com.bitpoint.homeservercontrol.ui.R.drawable.ic_qr_shield);caption.setText("QR-код скрыт");toggle.setText("Показать QR-код");if(addedSecure&&getContext() instanceof Activity){((Activity)getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);addedSecure=false;}}else{caption.setText("Подготавливаем QR-код…");toggle.setText("Скрыть QR-код");if(getContext() instanceof Activity){Window w=((Activity)getContext()).getWindow();addedSecure=(w.getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0;w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);}refresh();}IconButtons.decorate(toggle);requestLayout();}
    private void qrAppearance(boolean visible){image.setContentDescription(visible?"QR-код для подключения нового устройства":"Открыть QR-код и подтверждение нового устройства");image.setBackground(visible?null:new CompactControlDrawable(getContext()));int inset=visible?0:dp(8);image.setPadding(inset,inset,inset,inset);image.setClipToOutline(!visible);}
    boolean qrShown(){return shown;}
    void suspend(){if(inlineApproval!=null){inlineApproval.dismiss();inlineApproval=null;}if(shown)setQrShown(false);}
    void close(){closed=true;if(inlineApproval!=null){inlineApproval.dismiss();inlineApproval=null;}worker.shutdownNow();ui.removeCallbacksAndMessages(null);if(bitmap!=null){image.setImageDrawable(null);bitmap.recycle();bitmap=null;}}
    void refresh(){
        if(closed||reading||getVisibility()!=VISIBLE)return;reading=true;final boolean wantsQr=shown;final int revision=qrRevision;final long now=SystemClock.elapsedRealtime();final boolean running=EmbeddedServerService.running;final int port=EmbeddedServerService.boundPort;final String pin=EmbeddedServerService.fingerprint;final boolean probe=running&&(lastProbe==0||now-lastProbe>=5000||port!=probePort||!pin.equals(probePin));if(probe){lastProbe=now;probePort=port;probePin=pin;}final int qrSize=Math.max(dp(64),Math.min(image.getWidth(),image.getHeight()));
        worker.execute(()->{final float response=probe?ServerHealthProbe.read(port):Float.NaN;ServerTelemetry telemetry=DeviceTelemetry.read(getContext());List<ServerDevices.Device> allowed;try{allowed=ServerDevices.list(getContext());}catch(Exception e){allowed=Collections.emptyList();}Set<String> online=EmbeddedServerService.onlineDevices();List<ServerReceiptStore.Entry> receipts;try(ServerReceiptStore store=new ServerReceiptStore(getContext())){receipts=store.list();}catch(Exception e){receipts=Collections.emptyList();}
            QrPairing code=null;Bitmap next=null;String problem="";try{if(wantsQr){code=ServerInvitation.get(getContext(),invitation);String encoded=code.encode();if(!encoded.equals(payload)||renderedSize!=qrSize)next=QrPairingActivity.codeBitmap(encoded,qrSize);}}catch(Exception e){problem=e.getMessage();}
            final var entries=allowed;final var received=receipts;final var q=code;final Bitmap qr=next;final String failure=problem;
            ui.post(()->{reading=false;if(closed){if(qr!=null)qr.recycle();return;}render(telemetry,entries,online,received);if(!EmbeddedServerService.running){lastProbe=0;health.sample(now,Float.NaN,false);}else if(probe&&port==EmbeddedServerService.boundPort&&pin.equals(EmbeddedServerService.fingerprint))health.sample(now,response,true);if(shown&&wantsQr&&revision==qrRevision&&q!=null&&EmbeddedServerService.running&&q.expires>System.currentTimeMillis()&&q.pin.equals(EmbeddedServerService.fingerprint)&&q.port==EmbeddedServerService.boundPort){invitation=q;try{payload=q.encode();}catch(Exception ignored){}renderedSize=qrSize;image.setContentDescription("QR-код для подключения нового устройства");if(qr!=null){Bitmap old=bitmap;bitmap=qr;image.setImageBitmap(qr);if(old!=null)old.recycle();}caption.setText(EmbeddedServerService.pendingQrKey(q.nonce).isEmpty()?"Сканируйте QR-код":"Запрос входа · нажми QR");}else if(qr!=null)qr.recycle();if(shown&&wantsQr&&revision==qrRevision&&!failure.isEmpty()){caption.setText(failure);image.setContentDescription(failure);image.setImageResource(com.bitpoint.homeservercontrol.ui.R.drawable.ic_qr_shield);if(!failure.equals(lastQrFailure)){lastQrFailure=failure;Toast.makeText(getContext(),failure,Toast.LENGTH_LONG).show();}}if(!shown)caption.setText(invitation!=null&&!EmbeddedServerService.pendingQrKey(invitation.nonce).isEmpty()?"Запрос входа · нажми щит":"QR-код скрыт");offerPendingApproval();requestLayout();});
        });
    }
    private void offerPendingApproval(){
        if(closed||getVisibility()!=VISIBLE||!(getContext() instanceof BaseActivity))return;BaseActivity owner=(BaseActivity)getContext();if(!owner.isUiActive())return;
        QrPairing active=ServerInvitation.active(getContext());if(active==null)return;invitation=active;String key=EmbeddedServerService.pendingQrKey(active.nonce);if(key.isEmpty())return;
        if(ServerDevices.contains(getContext(),key)){if(key.equals(submittedKey)){submittedKey="";if(shown)setQrShown(false);}return;}
        String transcript=active.nonce+"\n"+key;if(transcript.equals(lastPrompt)||inlineApproval!=null&&inlineApproval.isShowing())return;lastPrompt=transcript;
        inlineApproval=QrApproval.confirm(owner,active,key,()->{submittedKey=key;caption.setText("Подтверждаем вход…");},message->{if(!closed)caption.setText(message);});
    }
    private void render(ServerTelemetry t,List<ServerDevices.Device> allowed,Set<String> online,List<ServerReceiptStore.Entry> received){
        allowedCount=allowed.size();battery.setText(t.battery<0?"—":t.battery+"%");gauge.level(t.battery);space.setText(t.total>0?SurfaceStyle.quantity(ServerTelemetry.bytes(t.free)):"—");for(int i=0;i<resources.getChildCount();i++){LinearLayout box=(LinearLayout)resources.getChildAt(i);for(int j=0;j<box.getChildCount();j++){View v=box.getChildAt(j);if("charging".equals(v.getTag())){((TextView)v).setText(t.charging==1?"Заряжается":t.charging==0?"От батареи":"Зарядка: —");((TextView)v).setTextColor(ThemeCatalog.color(getContext(),t.charging==1?com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));}if("disk_detail".equals(v.getTag()))((TextView)v).setText(t.total>0?"свободно из "+SurfaceStyle.quantity(ServerTelemetry.bytes(t.total)):"Объём недоступен");}}
        ServerStats metrics=new ServerStats();t.apply(metrics);uptime.setText("Работает "+metrics.uptime);timing.second.setText("Сейчас");timing.online(online.size());deviceTitle.setText("Устройства с доступом");deviceCount.setText(allowed.size()+" из 7");deviceSummary.setText(allowed.isEmpty()?"Разрешённых устройств пока нет":"Открыть устройства · "+allowed.size()+" из 7");
        for(int i=0;i<3;i++){ServerDeviceRow row=deviceRows.get(i);if(i>=allowed.size()){row.setVisibility(GONE);continue;}var device=allowed.get(i);String id="";try{id=EmbeddedSftpServer.publicFingerprint(device.key);}catch(Exception ignored){}row.bind(device.name,online.contains(id));row.setLast(i==Math.min(3,allowed.size())-1);row.setOnClickListener(v->openDevices());row.setVisibility(tight?GONE:VISIBLE);}
        deviceSummary.setVisibility(tight||allowed.isEmpty()?VISIBLE:GONE);
        if(received.isEmpty()){receipt.setText("Поступлений пока нет");receipt.setCompoundDrawables(null,null,null,null);}else{var row=received.get(0);String full=row.name()+"\n"+ServerTelemetry.bytes(row.bytes)+" · Получено "+android.text.format.DateUtils.getRelativeTimeSpanString(row.received,System.currentTimeMillis(),android.text.format.DateUtils.MINUTE_IN_MILLIS);android.text.SpannableString label=new android.text.SpannableString(full);label.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,row.name().length(),0);label.setSpan(new android.text.style.ForegroundColorSpan(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary)),row.name().length()+1,full.length(),0);label.setSpan(new android.text.style.RelativeSizeSpan(.92f),row.name().length()+1,full.length(),0);receipt.setText(label);if(FileTypeIcons.kind(row.name(),false)==FileTypeIcons.Kind.IMAGE)icon(receipt,com.bitpoint.homeservercontrol.ui.R.drawable.ic_media_image,26);else FileTypeIcons.apply(receipt,row.name(),false,24);android.graphics.drawable.Drawable check=getContext().getDrawable(com.bitpoint.homeservercontrol.ui.R.drawable.ic_status_tick).mutate();check.setTint(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess));check.setBounds(0,0,dp(22),dp(22));receipt.setCompoundDrawablesRelative(receipt.getCompoundDrawablesRelative()[0],null,check,null);}

    }
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs),aw=Math.max(1,w-getPaddingLeft()-getPaddingRight()),ah=Math.max(1,h-getPaddingTop()-getPaddingBottom());
        wide=aw>ah*1.25f;
        // Fit the complete panel first. A phone must not lose whole sections because a
        // preferred card height missed the viewport by a few dp.
        gap=gapForHeight(ah);
        ((LinearLayout.LayoutParams)resources.getChildAt(0).getLayoutParams()).rightMargin=0;
        ((LinearLayout.LayoutParams)resources.getChildAt(1).getLayoutParams()).leftMargin=gap;
        tight=wide?ah<dp(500):ah<dp(656)+7*gap;
        column=wide?(aw-gap)*(tight?46:48)/100:aw;
        float accessibility=Math.max(1,getResources().getDisplayMetrics().scaledDensity/getResources().getDisplayMetrics().density);
        // Portrait tablets need readable type too; empty height belongs to cards,
        // never to the single-line uptime between them.
        boolean portraitTablet=!wide&&w>=dp(600)&&ah>=dp(860);
        boolean largePortrait=portraitTablet&&ah>=dp(1000);
        boolean enlarged=wide&&!tight||portraitTablet;
        float visualScale=largePortrait||wide&&!tight?1.45f:portraitTablet?1.2f:1;
        float scale=visualScale/accessibility;
        for(var e:fonts.entrySet())e.getKey().setTextSize(TypedValue.COMPLEX_UNIT_PX,e.getKey().getId()==R.id.currentWifiIpValue?dp(enlarged?36:tight?20:24):e.getValue()*scale);
        for(int i=0;i<deviceRows.size();i++)deviceRows.get(i).setVisibility(tight||i>=allowedCount?GONE:VISIBLE);
        deviceSummary.setVisibility(tight||allowedCount==0?VISIBLE:GONE);deviceTitle.setVisibility(tight?GONE:VISIBLE);deviceHeader.setVisibility(tight?GONE:VISIBLE);folderTitle.setVisibility(tight?GONE:VISIBLE);
        boolean spaciousPhone=!wide&&!tight&&ah-dp(656)-7*gap>=dp(100);
        heights.clear();
        if(!wide&&!tight){
            View[] cards={status,resources,access,timing,devices,folders,incoming,footer};
            int[] minimum=portraitTablet?(largePortrait?new int[]{100,82,284,28,190,160,90,56}:new int[]{88,70,260,28,174,152,72,52}):spaciousPhone?new int[]{68,54,256,28,128,140,64,48}:new int[]{68,54,214,28,122,132,64,48};
            float[] growth=portraitTablet?new float[]{.12f,.08f,.58f,.00f,.00f,.12f,.10f,.00f}:spaciousPhone?new float[]{.06f,.04f,.80f,.00f,.00f,.00f,.10f,.00f}:new float[]{.04f,.02f,.80f,.00f,.00f,.04f,.10f,.00f};
            minimum[4]=Math.max(76,28+48*Math.min(3,Math.max(1,allowedCount)));
            int base=0;for(int size:minimum)base+=dp(size);
            int overflow=Math.max(0,base+7*gap-ah);if(overflow>0){int shrink=Math.min(overflow,dp(minimum[2]-86));minimum[2]-=(int)Math.ceil(shrink/getResources().getDisplayMetrics().density);base=0;for(int size:minimum)base+=dp(size);}
            int extra=Math.max(0,ah-base-7*gap),used=0;float cumulative=0;
            for(int i=0;i<cards.length;i++){cumulative+=growth[i];int allocated=i==cards.length-1?extra:Math.round(extra*cumulative);int value=dp(minimum[i])+allocated-used;used=allocated;heights.put(cards[i],value);}
        }else{
            heights.put(status,dp(tight?70:112));heights.put(resources,dp(tight?54:82));heights.put(timing,dp(28));heights.put(access,dp(tight?86:190));heights.put(devices,dp(tight?48:180));heights.put(folders,dp(tight?110:140));heights.put(incoming,tight?0:dp(90));heights.put(footer,dp(52));
            if(wide&&!tight){
                int leftAvailable=ah-headerInset;int leftExtra=Math.max(0,leftAvailable-dp(386)-3*gap);
                heights.put(status,dp(112)+leftExtra/4);heights.put(resources,dp(82)+leftExtra/4);heights.put(folders,dp(140)+leftExtra/3);heights.put(footer,leftAvailable-heights.get(status)-heights.get(resources)-heights.get(folders)-3*gap);
                int receiptHeight=dp(114),availableRight=ah-receiptHeight-dp(28)-3*gap;int deviceHeight=dp(28+48*Math.min(3,Math.max(1,allowedCount)));heights.put(devices,deviceHeight);heights.put(access,availableRight-deviceHeight);heights.put(incoming,receiptHeight);
            }
        }
        caption.setVisibility(GONE);
        int headingTop=!tight&&(wide||spaciousPhone)?dp(6):0;
        for(TextView title:sectionHeadings){title.setPadding(0,headingTop,0,dp(4));title.setMinHeight(dp(22)+headingTop);}
        deviceCount.setPadding(0,headingTop,0,dp(4));
        ((MetricValueRow)battery.getParent()).glyphSize(Math.round(34*visualScale),Math.round(24*visualScale));
        resizeLeadingIcon(space,Math.round(22*visualScale));
        int accessWidth=wide?aw-column-gap:aw;
        TextView accessHeading=(TextView)access.getChildAt(0);
        accessHeading.measure(exact(accessWidth-dp(20)),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int accessPadding=!wide&&!tight?dp(1):dp(6);
        int bodyHeight=heights.get(access)-2*accessPadding-accessHeading.getMeasuredHeight();
        int side=Math.max(dp(48),Math.min((accessWidth-dp(32))/2,bodyHeight));image.getLayoutParams().width=side;image.getLayoutParams().height=side;
        // Both columns share a square-height body; their top edges stay aligned
        // when surplus card height grows on a tablet or with fewer devices.
        ((View)health.getParent()).getLayoutParams().height=side;
        toggle.getLayoutParams().width=-1;toggle.getLayoutParams().height=dp(48);
        for(ServerDeviceRow device:deviceRows){
            device.getLayoutParams().height=dp(48);
            View glyph=device.getChildAt(0);glyph.getLayoutParams().width=dp(24*visualScale);glyph.getLayoutParams().height=dp(24*visualScale);
        }
        Button power=findViewById(R.id.serverPowerButton);power.getLayoutParams().width=dp(enlarged?120:88);power.getLayoutParams().height=enlarged?dp(76):Math.min(dp(64),heights.get(status)-dp(8));
        for(int id:new int[]{R.id.serverLocalFilesButton,R.id.serverReceiveButton})findViewById(id).getLayoutParams().height=wide&&!tight?Math.max(dp(48),(heights.get(folders)-dp(60))/2):-2;
        ((LinearLayout)incoming).setGravity(wide&&!tight?Gravity.CENTER_VERTICAL:Gravity.TOP);
        status.setPadding(dp(10),dp(4),dp(10),dp(4));
        TextView state=findViewById(R.id.localServerSummary);state.setBackground(null);state.setPadding(0,0,0,0);state.setTextColor(ThemeCatalog.color(getContext(),EmbeddedServerService.running?com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        footer.setPadding(dp(6),tight?dp(2):0,dp(6),tight?dp(2):0);
        access.setPadding(dp(10),!wide&&!tight?dp(1):dp(6),dp(10),!wide&&!tight?dp(1):dp(6));
        for(var e:heights.entrySet()){View v=e.getKey();v.setVisibility(e.getValue()==0?GONE:VISIBLE);if(v==folders)folders.setPadding(dp(10),0,dp(10),0);if(v==devices)devices.setPadding(dp(10),0,dp(10),0);int width=wide?((tight?Arrays.asList(status,resources,footer,devices).contains(v):left.contains(v))?column:aw-column-gap):aw;v.measure(exact(width),exact(e.getValue()));}
        options.setVisibility(GONE);setMeasuredDimension(w,h);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int x=getPaddingLeft(),y=getPaddingTop();if(wide){y+=headerInset;List<View> a=tight?Arrays.asList(status,resources,footer,devices):left;List<View> z=tight?Arrays.asList(access,timing,folders):right;for(View v:a)if(v.getVisibility()!=GONE){v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());y+=v.getMeasuredHeight()+gap;}x+=column+gap;y=getPaddingTop();for(View v:z)if(v.getVisibility()!=GONE){v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());y+=v.getMeasuredHeight()+gap;}}else{for(View v:Arrays.asList(status,resources,access,timing,devices,folders,incoming,footer))if(v.getVisibility()!=GONE){v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());y+=v.getMeasuredHeight()+gap;}}}
    private void resizeLeadingIcon(TextView label,int size){
        android.graphics.drawable.Drawable leading=label.getCompoundDrawablesRelative()[0];
        if(leading!=null&&(leading.getBounds().width()!=dp(size)||leading.getBounds().height()!=dp(size))){
            leading.setBounds(0,0,dp(size),dp(size));
            android.graphics.drawable.Drawable[] icons=label.getCompoundDrawablesRelative();
            label.setCompoundDrawablesRelative(leading,icons[1],icons[2],icons[3]);
        }
    }
    private static int exact(int size){return MeasureSpec.makeMeasureSpec(Math.max(0,size),MeasureSpec.EXACTLY);}
}
