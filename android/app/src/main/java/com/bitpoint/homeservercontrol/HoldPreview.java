package com.bitpoint.homeservercontrol;
import android.app.Activity;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A temporary, read-only preview; release invalidates every pending callback immediately. */
final class HoldPreview {
 private final Activity activity;private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private PopupWindow window;private LinearLayout content;private VideoView video;private Bitmap bitmap;private File temporary;private Future<?> task;private volatile long revision;private boolean secureBefore;
 HoldPreview(Activity activity){this.activity=activity;}
 boolean showing(){return window!=null;}
 void open(FileNodes.Node node,FileConnection route){
  close();if(node.directory||activity.isFinishing()||activity.isDestroyed())return;final long token=++revision;secureBefore=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0;activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER);content.setPadding(20,20,20,20);content.setBackgroundColor(0xf513101b);
  TextView title=label(node.name+"\nОтпусти, чтобы закрыть");content.addView(title);TextView status=label("Подготовка просмотра…");content.addView(status);
  window=new PopupWindow(content,-1,-1,false);window.setTouchable(false);window.setClippingEnabled(true);window.showAtLocation(activity.findViewById(android.R.id.content),Gravity.CENTER,0,0);
  int dot=node.name.lastIndexOf('.');String ext=dot>=0?node.name.substring(dot+1):"";String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
  boolean media=mime!=null&&(mime.startsWith("video/")||mime.startsWith("audio/"));
  if(media&&!"remote".equals(node.kind)){
   try{Uri uri;if("doc".equals(node.kind))uri=Uri.parse(node.path);else if("share".equals(node.kind))uri=Uri.fromFile(SharedFolderAccess.within(EmbeddedServerService.share(activity),new File(EmbeddedServerService.share(activity),TransferPaths.relative(node.path))));else throw new IOException();play(uri,token,status);return;}catch(Exception e){status.setText("Не удалось открыть файл");return;}
  }
  final String type=mime;if(!media&&!"application/pdf".equals(type)&&(type==null||(!type.startsWith("image/")&&!type.startsWith("text/")))){status.setText("Предпросмотр этого формата недоступен. Поддерживаются фото, видео, аудио, PDF и текст.");return;}
  task=worker.submit(()->{File file=null;try{
   if(token!=revision)throw new InterruptedIOException();long limit=128L*1024*1024;if(node.size>limit)throw new IOException("Сначала скачай большой файл на телефон");
   File folder=new File(activity.getCacheDir(),"hold-preview");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException();file=File.createTempFile("preview-",".bin",folder);final File result=file;
   try(OutputStream raw=new FileOutputStream(file);OutputStream out=new FilterOutputStream(raw){long count;@Override public void write(byte[] b,int o,int n)throws IOException{if(token!=revision||Thread.currentThread().isInterrupted())throw new InterruptedIOException();count+=n;if(count>limit)throw new IOException("Сначала скачай большой файл на телефон");out.write(b,o,n);}}){
    if("remote".equals(node.kind))try(var session=new SftpClient.TreeSession(route.host,route.port,route.user,SshKeys.keyCredentials(activity,route.host,route.port,route.user),route.pin)){final long[] last={0};session.read(node.path,out,(n,total)->{if(token!=revision)return false;long now=SystemClock.elapsedRealtime();if(now-last[0]<200)return true;last[0]=now;ui.post(()->{if(token==revision)status.setText("Загрузка просмотра: "+ServerTelemetry.bytes(n));});return true;});}
    else try(InputStream in=FileNodes.open(activity,node)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   }
   if(token!=revision)throw new InterruptedIOException();
   if(media){ui.post(()->{if(token!=revision){result.delete();return;}temporary=result;play(Uri.fromFile(result),token,status);});file=null;return;}
   Bitmap image=null;String text=null;
   if("application/pdf".equals(type))try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)){
    if(pdf.getPageCount()<1)throw new IOException();try(PdfRenderer.Page page=pdf.openPage(0)){float scale=Math.min(2f,1800f/Math.max(page.getWidth(),page.getHeight()));image=Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*scale)),Math.max(1,(int)(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
   }else if(type!=null&&type.startsWith("image/")){
    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1800||options.outHeight/options.inSampleSize>1800)options.inSampleSize*=2;options.inJustDecodeBounds=false;image=BitmapFactory.decodeFile(file.getPath(),options);if(image==null)throw new IOException();
   }else if(type!=null&&type.startsWith("text/")){try(InputStream in=new FileInputStream(file)){byte[] b=new byte[128*1024];int n=in.read(b);text=n>0?new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8):"Пустой документ";}}
   else text="Предпросмотр этого формата недоступен. Поддерживаются фото, видео, аудио, PDF и текст.";
   final Bitmap rendered=image;final String caption=text;
   ui.post(()->{if(token!=revision){if(rendered!=null)rendered.recycle();return;}content.removeView(status);if(rendered!=null){bitmap=rendered;ImageView view=new ImageView(activity);view.setImageBitmap(rendered);view.setScaleType(ImageView.ScaleType.FIT_CENTER);content.addView(view,new LinearLayout.LayoutParams(-1,0,1));}else{TextView view=label(caption);view.setMaxLines(20);content.addView(view,new LinearLayout.LayoutParams(-1,0,1));}});
  }catch(Exception|OutOfMemoryError e){ui.post(()->{if(token==revision)status.setText("Просмотр недоступен. Для большого файла сначала выполни скачивание.");});}finally{if(file!=null)file.delete();}});
 }
 private TextView label(String text){TextView view=new TextView(activity);view.setText(text);view.setTextColor(Color.WHITE);view.setTextSize(18);view.setGravity(Gravity.CENTER);return view;}
 private void play(Uri uri,long token,TextView status){if(token!=revision)return;content.removeView(status);video=new VideoView(activity);content.addView(video,new LinearLayout.LayoutParams(-1,0,1));video.setOnPreparedListener(player->{if(token==revision){player.setLooping(false);video.start();}else player.release();});video.setOnErrorListener((p,w,e)->{if(token==revision){video.setVisibility(View.GONE);status.setText("Не удалось воспроизвести файл");if(status.getParent()==null)content.addView(status);}return true;});video.setVideoURI(uri);}
 void close(){boolean wasShowing=window!=null;revision++;if(task!=null){task.cancel(true);task=null;}if(video!=null){video.stopPlayback();video=null;}if(window!=null){window.dismiss();window=null;}if(content!=null)content.removeAllViews();if(bitmap!=null){bitmap.recycle();bitmap=null;}if(temporary!=null){temporary.delete();temporary=null;}if(wasShowing&&!secureBefore)activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
 void destroy(){close();worker.shutdownNow();}
}
