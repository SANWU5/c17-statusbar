// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded colored application artwork. All package-manager reads and rasterization run off draw. */
public final class NotificationAppIconRuntime {
    static final int MAX_ICONS=NotificationIconOverrides.MAX_RULES,MAX_SIDE=128;
    interface Loader { Bitmap load(Context context,String packageName)throws Exception; }
    private static final class Entry {Bitmap icon;int generation;boolean pending,removed;}
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final Executor worker,completion;
    private final Loader loader;
    private final Runnable changed;
    private final ExecutorService ownedWorker;
    private Context context,receiverContext;
    private boolean released;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context owner,Intent intent){
            if(intent==null||intent.getData()==null)return;
            String pkg=intent.getData().getSchemeSpecificPart();
            packageChanged(pkg,Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()));
        }
    };
    public NotificationAppIconRuntime(Runnable changed){
        Handler main=new Handler(Looper.getMainLooper());
        ExecutorService background=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"C17-notification-app-icon");thread.setDaemon(true);return thread;});
        this.worker=background;this.ownedWorker=background;this.completion=task->main.post(task);
        this.loader=NotificationAppIconRuntime::load;this.changed=changed;
    }
    /** Deterministic desktop execution uses the same cache and generation transaction. */
    NotificationAppIconRuntime(Executor worker,Executor completion,Loader loader,Runnable changed){
        this.worker=worker;this.completion=completion;this.loader=loader;this.changed=changed;this.ownedWorker=null;
    }
    public synchronized void configure(Context owner,Collection<String> requested){
        if(released)return;
        if(owner!=null){Context application=owner.getApplicationContext();context=application==null?owner:application;}
        LinkedHashSet<String> sources=new LinkedHashSet<>();
        if(requested!=null)for(String pkg:requested){
            NotificationIconOverrides.validatePackage(pkg,"图标来源应用包名格式不正确");
            sources.add(pkg);if(sources.size()>MAX_ICONS)throw new IllegalArgumentException("最多缓存128个应用图标");
        }
        cache.keySet().retainAll(sources);
        if(sources.isEmpty())unregister();else register();
        for(String pkg:sources){
            Entry entry=cache.get(pkg);
            if(entry==null){entry=new Entry();cache.put(pkg,entry);request(pkg,entry);}
            else if(context!=null&&!entry.pending&&entry.icon==null&&!entry.removed&&entry.generation==0)request(pkg,entry);
        }
    }
    public synchronized Bitmap icon(String packageName){Entry entry=cache.get(packageName);return released||entry==null?null:entry.icon;}
    /** Native package events revoke old pixels immediately, including already-recorded glyphs. */
    synchronized void packageChanged(String packageName,boolean removed){
        if(released)return;Entry entry=cache.get(packageName);if(entry==null)return;
        entry.icon=null;entry.generation++;entry.removed=removed;
        // A pending load owns one worker slot. Its stale result reschedules the latest package
        // only once, even if a replacement emits added, changed and replaced together.
        if(!removed&&!entry.pending)request(packageName,entry);
        changed.run();
    }
    private void request(String packageName,Entry entry){
        if(context==null||entry.pending||released)return;
        entry.pending=true;final int generation=++entry.generation;final Context owner=context;
        worker.execute(()->{
            synchronized(NotificationAppIconRuntime.this){if(released||cache.get(packageName)!=entry)return;}
            Bitmap icon=null;try{icon=loader.load(owner,packageName);}catch(Exception|LinkageError|OutOfMemoryError unavailable){ }
            final Bitmap ready=icon;
            completion.execute(()->{
                synchronized(NotificationAppIconRuntime.this){
                    if(released||cache.get(packageName)!=entry)return;
                    entry.pending=false;
                    if(entry.generation!=generation){if(!entry.removed)request(packageName,entry);return;}
                    entry.icon=ready;
                }
                changed.run();
            });
        });
    }
    private void register(){
        if(context==null||receiverContext!=null)return;
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
        try{
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver,filter);
            receiverContext=context;
        }
        catch(RuntimeException unavailable){ModuleDiagnostics.info("hooks","Application icon package events unavailable; native artwork retained on load failure");}
    }
    private void unregister(){
        if(receiverContext==null)return;
        try{receiverContext.unregisterReceiver(receiver);}catch(RuntimeException alreadyDetached){ }
        receiverContext=null;
    }
    public synchronized void releaseRuntime(){
        if(released)return;released=true;unregister();cache.clear();context=null;
        if(ownedWorker!=null)ownedWorker.shutdownNow();
    }
    private static Bitmap load(Context context,String packageName)throws Exception{
        NotificationIconOverrides.validatePackage(packageName,"图标来源应用包名格式不正确");
        Drawable source=context.getPackageManager().getApplicationIcon(packageName);
        if(source==null)return null;
        source=source.mutate();
        int width=source.getIntrinsicWidth(),height=source.getIntrinsicHeight();
        if(width<=0||height<=0){width=MAX_SIDE;height=MAX_SIDE;}
        int largest=Math.max(width,height);
        int targetWidth=Math.max(1,(int)((long)width*MAX_SIDE/largest));
        int targetHeight=Math.max(1,(int)((long)height*MAX_SIDE/largest));
        Bitmap result=Bitmap.createBitmap(targetWidth,targetHeight,Bitmap.Config.ARGB_8888);
        source.setBounds(0,0,targetWidth,targetHeight);source.draw(new Canvas(result));return result;
    }
}
