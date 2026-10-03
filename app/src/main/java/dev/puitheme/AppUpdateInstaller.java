// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** A user-initiated, same-package/same-signer official release replacement. Never runs at boot. */
public final class AppUpdateInstaller {
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(task->{
        Thread worker=new Thread(task,"C17-official-update");worker.setDaemon(true);return worker;
    });
    private AppUpdateInstaller(){}
    public enum Stage { DOWNLOADING,VERIFYING,INSTALLING }
    public interface Callback {
        void onStage(Stage stage);
        void onResult(boolean success,String message);
    }
    public static final class Request {
        private volatile boolean cancelled,installing;
        private volatile HttpURLConnection connection;
        /** Downloads can be cancelled. Once PackageManager starts, only UI callbacks are detached. */
        public void cancel(){cancelled=true;HttpURLConnection current=connection;if(!installing&&current!=null)current.disconnect();}
        public boolean isInstalling(){return installing;}
    }

    public static Request install(Context context,GitHubUpdates.Result release,BooleanSupplier rootAuthorized,Callback callback) {
        if(context==null||!ModuleDiagnostics.PACKAGE_NAME.equals(context.getPackageName())||callback==null||rootAuthorized==null)
            throw new IllegalArgumentException("Own application and explicit Root authorization required");
        Request request=new Request();Handler main=new Handler(Looper.getMainLooper());
        if(release==null||!release.canInstall()||!rootAuthorized.getAsBoolean()) {
            main.post(()->{if(!request.cancelled)callback.onResult(false,"需要已授权的 Root 和具有完整校验信息的正式更新");});return request;
        }
        if(!ACTIVE.compareAndSet(false,true)) {
            main.post(()->{if(!request.cancelled)callback.onResult(false,"已有更新正在处理，请稍候");});return request;
        }
        Context app=context.getApplicationContext();Context owner=app==null?context:app;
        WORK.execute(()->{
            File temporary=null;boolean success=false;String message="更新未完成，请重试或到项目主页下载";
            try {
                PackageManager manager=owner.getPackageManager();PackageInfo installed=packageInfo(manager,owner.getPackageName());
                Integer newer=GitHubUpdates.compareVersion(release.version,installed.versionName);
                if(newer==null||newer<=0)throw new IOException("Release is no longer newer");
                File folder=updateDirectory(owner);clearOldDownloads(folder);
                temporary=new File(folder,"c17-update-"+UUID.randomUUID().toString().replace("-","")+".apk");
                if(!temporary.createNewFile())throw new IOException("Cannot create private update artifact");
                stage(main,request,callback,Stage.DOWNLOADING);
                download(release,temporary,request);
                if(request.cancelled)throw new IOException("Cancelled");
                stage(main,request,callback,Stage.VERIFYING);
                PackageInfo archive=manager.getPackageArchiveInfo(temporary.getAbsolutePath(),signingFlags());
                if(archive==null||!UpdateSecurity.packageMatches(archive.packageName,release.version,archive.versionName,
                        versionCode(installed),versionCode(archive),signers(installed),signers(archive)))
                    throw new IOException("Package identity, version or signer mismatch");
                if(request.cancelled||!rootAuthorized.getAsBoolean())throw new IOException("Root authorization unavailable");
                request.installing=true;stage(main,request,callback,Stage.INSTALLING);
                success=RootAccess.installVerified(temporary,release.apkSha256,Process.myUid()/100000);
                message=success?"更新已覆盖安装；重新打开应用后检查版本":"Root 覆盖安装未成功，原应用和配置已保留";
            }catch(IOException | PackageManager.NameNotFoundException | RuntimeException unavailable){
                message=request.cancelled?"更新已取消":"更新校验或安装失败，原应用和配置已保留";
            }finally {
                HttpURLConnection connected=request.connection;request.connection=null;
                if(connected!=null)connected.disconnect();
                if(temporary!=null&&temporary.exists()&&!temporary.delete())
                    message="更新处理已结束，但临时文件清理失败；下次检查更新会重试清理";
                request.installing=false;ACTIVE.set(false);
            }
            final boolean result=success;final String completed=message;
            main.post(()->{if(!request.cancelled)callback.onResult(result,completed);});
        });
        return request;
    }

    private static void stage(Handler main,Request request,Callback callback,Stage stage){main.post(()->{if(!request.cancelled)callback.onStage(stage);});}
    private static void download(GitHubUpdates.Result release,File destination,Request request) throws IOException {
        long expires=android.os.SystemClock.elapsedRealtime()+180000L;
        URL url=new URL(release.apkUrl);
        for(int redirect=0;redirect<=3;redirect++) {
            if(request.cancelled)throw new IOException("Cancelled");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();request.connection=connection;
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept","application/octet-stream");connection.setRequestProperty("User-Agent","C17-official-update");
            int response=connection.getResponseCode();
            if(response==301||response==302||response==303||response==307||response==308) {
                String target=connection.getHeaderField("Location");
                if(target==null||redirect==3)throw new IOException("Invalid release redirect");
                URL next=new URL(url,target);
                if(!UpdateSecurity.allowedRedirect(next.toExternalForm()))throw new IOException("Untrusted download origin");
                connection.disconnect();url=next;continue;
            }
            if(response!=200)throw new IOException("Release download unavailable");
            long length=connection.getContentLengthLong();
            if(length>=0&&length!=release.apkSize)throw new IOException("Unexpected download length");
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(destination)) {
                UpdateSecurity.copyVerified(input,output,release.apkSize,release.apkSha256,
                        ()->request.cancelled||android.os.SystemClock.elapsedRealtime()>expires);
                output.getFD().sync();
            }
            return;
        }
        throw new IOException("Too many release redirects");
    }
    private static int signingFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static PackageInfo packageInfo(PackageManager manager,String name)throws PackageManager.NameNotFoundException{return manager.getPackageInfo(name,signingFlags());}
    private static long versionCode(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static byte[][] signers(PackageInfo info) {
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        if(signatures==null)return null;byte[][] bytes=new byte[signatures.length][];
        for(int i=0;i<bytes.length;i++)bytes[i]=signatures[i]==null?null:signatures[i].toByteArray();return bytes;
    }
    private static File updateDirectory(Context owner)throws IOException {
        File cache=owner.getCacheDir().getCanonicalFile(),folder=new File(cache,"official-updates");
        if(!folder.getCanonicalFile().equals(folder.getAbsoluteFile())||!folder.getParentFile().equals(cache))throw new IOException("Invalid update cache");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create update cache");return folder;
    }
    static void clearOldDownloads(File folder)throws IOException {
        File[] leftovers=folder.listFiles();if(leftovers==null)throw new IOException("Cannot read update cache");
        for(File file:leftovers)if(file.getName().matches("c17-update-[0-9a-f]{32}\\.apk")) {
            if(!file.getCanonicalFile().equals(file.getAbsoluteFile())||!file.isFile()||!file.delete())throw new IOException("Cannot clear update cache");
        }
    }
}
