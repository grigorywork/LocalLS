package com.bitpoint.homeservercontrol;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.io.*;
import java.util.*;

/** Decode untrusted images off the UI thread, without camera or storage permissions. */
final class QrImageReader {
    private static final long MAX_BYTES=32L*1024*1024,MAX_PIXELS=20_000_000;
    private QrImageReader(){}
    static String read(ContentResolver resolver,Uri uri)throws IOException {
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("Выбери изображение через системное окно Android");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=open(resolver,uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<21||options.outHeight<21||(long)options.outWidth*options.outHeight>MAX_PIXELS)throw new IOException("Изображение слишком большое или повреждено");
        options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inSampleSize=1;
        while((long)options.outWidth*options.outHeight/(options.inSampleSize*options.inSampleSize)>4_000_000)options.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=open(resolver,uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("Не удалось открыть изображение");
        try {
            int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
            // Transparent PNGs must be read on white, just like a printed QR.
            for(int i=0;i<pixels.length;i++){int p=pixels[i],a=p>>>24;if(a!=255){int inv=255-a;pixels[i]=0xff000000|(((p>>16&255)*a/255+inv)<<16)|(((p>>8&255)*a/255+inv)<<8)|((p&255)*a/255+inv);}}
            LuminanceSource source=new RGBLuminanceSource(w,h,pixels);
            Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
            for(int attempt=0;attempt<2;attempt++)try{return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(attempt==0?source:source.invert())),hints).getText();}catch(ReaderException missing){/* Try the inverse without changing pairing validation. */}
            throw new IOException("QR-код не найден. Выбери чёткую картинку целиком");
        } finally {bitmap.recycle();}
    }
    private static InputStream open(ContentResolver resolver,Uri uri)throws IOException {
        InputStream input=resolver.openInputStream(uri);if(input==null)throw new IOException("Изображение недоступно");
        return new FilterInputStream(input){long count;private void add(long n)throws IOException{if(n>0&&(count+=n)>MAX_BYTES)throw new IOException("Изображение больше 32 МБ");}
            @Override public int read()throws IOException{int value=in.read();if(value>=0)add(1);return value;}
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}
            @Override public long skip(long n)throws IOException{long skipped=in.skip(n);add(skipped);return skipped;}
        };
    }
}
