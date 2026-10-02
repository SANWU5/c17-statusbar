// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

/** Worker-only, pinned downloads. Publication is the existing lifecycle-guarded font transaction. */
public final class FontDownloadRepository {
    private static final long MAX_CACHE_BYTES=64L*1024*1024,MAX_DOWNLOAD_MS=180000L;
    private static final int CONNECT_TIMEOUT_MS=12000,READ_TIMEOUT_MS=12000;
    private FontDownloadRepository() { }
    public interface Progress { void onProgress(long received,long total); }
    /** cancel() also interrupts an active network read; no settings are changed by this token. */
    public static final class Cancellation implements BooleanSupplier {
        private volatile boolean cancelled;
        private volatile HttpsURLConnection connection;
        @Override public boolean getAsBoolean() { return cancelled; }
        public void cancel() {
            cancelled=true;HttpsURLConnection active=connection;if(active!=null)active.disconnect();
        }
        private void attach(HttpsURLConnection active) {
            connection=active;if(cancelled)active.disconnect();
        }
        private void detach(HttpsURLConnection active) { if(connection==active)connection=null; }
    }

    /** Progress runs on the caller's worker. The caller owns main-thread callbacks and commit/rollback. */
    public static FontRepository.PreparedFont prepare(Context context,FontCatalog.Entry entry,
            Progress progress,BooleanSupplier cancelled) throws Exception {
        if(context==null||entry==null||FontCatalog.find(entry.id)!=entry)
            throw new IllegalArgumentException("请选择字体库中的字体");
        FontFileValidator.checkCancelled(cancelled);
        File fonts=new File(context.createDeviceProtectedStorageContext().getFilesDir(),"fonts");
        File cache=new File(fonts,"catalog");
        if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("无法创建私有字体缓存");
        clearStaleDownloads(cache);
        File cached=new File(cache,entry.id+"-"+entry.sha256+".font");
        boolean valid=false;
        if(cached.isFile()) {
            try { FontFileValidator.validate(cached,entry,cancelled);valid=true; }
            catch(java.io.InterruptedIOException aborted) { throw aborted; }
            catch(IOException stale) { /* A stale cache is replaced only after a valid download. */ }
        }
        if(!valid)download(entry,cached,progress,cancelled);
        else if(progress!=null)progress.onProgress(entry.bytes,entry.bytes);
        FontFileValidator.checkCancelled(cancelled);
        if(new Typeface.Builder(cached).setFontVariationSettings("'wght' "+entry.clampWeight(400)).build()==null)
            throw new IOException("字体无法加载，原字体已保留");
        FontFileValidator.checkCancelled(cancelled);
        File staged=File.createTempFile("catalog-",".font",fonts);boolean prepared=false;
        try {
            // A hard link keeps the verified immutable inode when cache pruning or another selection runs.
            android.system.Os.remove(staged.getAbsolutePath());
            try { android.system.Os.link(cached.getAbsolutePath(),staged.getAbsolutePath()); }
            catch(Exception unsupportedLink) { copy(cached,staged,entry,cancelled); }
            FontFileValidator.checkCancelled(cancelled);
            cached.setLastModified(System.currentTimeMillis());
            prune(cache,cached);
            FontRepository.PreparedFont result=new FontRepository.PreparedFont(staged,new File(fonts,"custom.font"),entry.sha256);
            prepared=true;return result;
        } finally { if(!prepared&&staged.exists())staged.delete(); }
    }
    public static FontRepository.PreparedFont prepare(Context context,FontCatalog.Entry entry) throws Exception {
        return prepare(context,entry,null,null);
    }
    public static URL downloadUrl(FontCatalog.Entry entry) throws IOException {
        if(entry==null||FontCatalog.find(entry.id)!=entry)throw new IOException("未知字体来源");
        URL url=new URL(entry.downloadUrl);
        if(!"https".equals(url.getProtocol())||!"raw.githubusercontent.com".equals(url.getHost())
                ||(url.getPort()!=-1&&url.getPort()!=443)||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null
                ||!url.getPath().startsWith("/google/fonts/"+FontCatalog.DISTRIBUTION_COMMIT+"/ofl/"))
            throw new IOException("字体下载来源不受支持");
        return url;
    }
    private static void download(FontCatalog.Entry entry,File target,Progress progress,
            BooleanSupplier cancelled) throws Exception {
        File temporary=File.createTempFile("download-",".part",target.getParentFile());
        HttpsURLConnection connection=null;boolean published=false;
        try {
            connection=(HttpsURLConnection)downloadUrl(entry).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding","identity");
            connection.setRequestProperty("User-Agent","C17-FontCatalog");
            if(cancelled instanceof Cancellation)((Cancellation)cancelled).attach(connection);
            FontFileValidator.checkCancelled(cancelled);
            long started=System.nanoTime(),lastProgress=0;
            if(connection.getResponseCode()!=200)throw new IOException("字体下载失败，请检查网络后重试");
            long declared=connection.getContentLengthLong();
            if(declared>FontCatalog.MAX_DOWNLOAD_BYTES||(declared>=0&&declared!=entry.bytes))
                throw new IOException("字体下载大小与已验证版本不符");
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[32768];long received=0;int count;
                while((count=input.read(buffer))!=-1) {
                    FontFileValidator.checkCancelled(cancelled);
                    received+=count;
                    if(received>FontCatalog.MAX_DOWNLOAD_BYTES||received>entry.bytes)
                        throw new IOException("字体下载超过大小限制");
                    if((System.nanoTime()-started)/1000000L>MAX_DOWNLOAD_MS)throw new IOException("字体下载超时，请稍后重试");
                    output.write(buffer,0,count);
                    long now=System.nanoTime();
                    if(progress!=null&&(now-lastProgress>=200000000L||received==entry.bytes)) {
                        progress.onProgress(received,entry.bytes);lastProgress=now;
                    }
                }
                output.getFD().sync();
            }
            FontFileValidator.validate(temporary,entry,cancelled);
            FontFileValidator.checkCancelled(cancelled);
            android.system.Os.rename(temporary.getAbsolutePath(),target.getAbsolutePath());published=true;
        } finally {
            if(cancelled instanceof Cancellation)((Cancellation)cancelled).detach(connection);
            if(connection!=null)connection.disconnect();
            if(!published&&temporary.exists())temporary.delete();
        }
    }
    private static void copy(File source,File target,FontCatalog.Entry entry,BooleanSupplier cancelled) throws IOException {
        try(FileInputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(target)) {
            byte[] buffer=new byte[32768];long length=0;int count;
            while((count=input.read(buffer))!=-1) {
                FontFileValidator.checkCancelled(cancelled);length+=count;
                if(length>entry.bytes)throw new IOException("字体缓存大小改变");
                output.write(buffer,0,count);
            }
            output.getFD().sync();
        }
        FontFileValidator.validate(target,entry,cancelled);
    }
    private static void prune(File folder,File keep) {
        File[] files=folder.listFiles(file->file.isFile()&&file.getName().matches("[a-z0-9]+-[a-f0-9]{64}\\.font"));
        if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        long size=0;for(File file:files)size+=file.length();
        for(File file:files)if(size>MAX_CACHE_BYTES&&!file.equals(keep)) { long bytes=file.length();if(file.delete())size-=bytes; }
    }
    private static void clearStaleDownloads(File folder) {
        // Normal cancellation removes its file immediately. This handles a process killed during a download.
        File[] files=folder.listFiles(file->file.isFile()&&file.getName().matches("download-[-0-9]+\\.part"));
        if(files==null)return;
        long cutoff=System.currentTimeMillis()-20L*60*1000;
        for(File file:files)if(file.lastModified()<cutoff)file.delete();
    }
}
