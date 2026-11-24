package com.miuing.reader;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Test APK only: synthetic text, no access to host files or user content. */
public class ExternalBooksProvider extends ContentProvider {
    private static final java.util.concurrent.ConcurrentHashMap<String,Boolean> opened=new java.util.concurrent.ConcurrentHashMap<>();
    public boolean onCreate(){return true;}
    public String getType(Uri uri){return "text/plain";}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{"external-"+uri.getLastPathSegment()});return cursor;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(uri.getLastPathSegment().startsWith("denied"))throw new FileNotFoundException("Access revoked");
        if(uri.getLastPathSegment().startsWith("once") && opened.putIfAbsent(uri.getLastPathSegment(),true)!=null)throw new FileNotFoundException("One-shot source expired");
        try {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                byte[] text=("第一章\n"+uri.getLastPathSegment()+"\n"+"外部分享导入正文。Reading shared book.\n".repeat(500)).getBytes(StandardCharsets.UTF_8);
                for(int at=0;at<text.length;at+=512){out.write(text,at,Math.min(512,text.length-at));if(uri.getBooleanQueryParameter("slow",false))Thread.sleep(10);}
            }catch(Exception ignored){}}).start();return pipe[0];
        }catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
