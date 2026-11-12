// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** One permissionless service session compiles a script. Caller must be off the UI thread. */
public final class CustomPluginSandbox {
    static final int EVALUATE = 1;
    private static final Object LOCK = new Object();
    private CustomPluginSandbox() { }

    public static JSONObject evaluate(Context context,String source,JSONObject state) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("插件编译必须在后台进行");
        if (source == null) throw new IllegalArgumentException("缺少插件脚本");
        String stateJson = state == null ? "{}" : state.toString();
        synchronized (LOCK) {
            Context app = context.getApplicationContext(); if (app == null) app = context;
            // Descriptors can contain an inline runtime larger than a Binder transaction.
            // The isolated UID receives open file descriptors, never access to app storage.
            File inputFile=File.createTempFile("c17-plugin-input-",".json",app.getCacheDir());
            try {
            final File outputFile=File.createTempFile("c17-plugin-output-",".json",app.getCacheDir());
            try {
            try(FileOutputStream output=new FileOutputStream(inputFile)) {
                output.write(new JSONObject().put("source",source).put("state",new JSONObject(stateJson)).toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();
            }
            try(ParcelFileDescriptor inputDescriptor=ParcelFileDescriptor.open(inputFile,ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor outputDescriptor=ParcelFileDescriptor.open(outputFile,ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_TRUNCATE)) {
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<String> answer = new AtomicReference<>(), error = new AtomicReference<>();
            Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> {
                if (message.what != EVALUATE || completed.getCount() == 0) return true;
                Bundle data = message.getData();
                error.set(data.getString("error")); answer.set(data.getString("result")); completed.countDown(); return true;
            }));
            ServiceConnection connection = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                    try {
                        Message message = Message.obtain(null,EVALUATE); message.replyTo = reply;
                        Bundle request = new Bundle(); request.putParcelable("input",inputDescriptor);request.putParcelable("output",outputDescriptor);
                        message.setData(request); new Messenger(binder).send(message);
                    } catch (Exception failed) { error.set("无法连接插件隔离进程"); completed.countDown(); }
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    if (completed.getCount() > 0) { error.set("插件隔离进程已终止，可能超出执行或内存限额"); completed.countDown(); }
                }
                @Override public void onNullBinding(ComponentName name) { error.set("插件隔离服务不可用"); completed.countDown(); }
                @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            };
            boolean bound = false;
            try {
                bound = app.bindService(new Intent(app,CustomPluginService.class),connection,Context.BIND_AUTO_CREATE);
                if (!bound) throw new IOException("无法启动插件隔离服务");
                if (!completed.await(5,TimeUnit.SECONDS)) throw new IOException("插件隔离进程超时，已停止等待");
                if (error.get() != null) throw new IOException(error.get());
                String json = new String(Files.readAllBytes(outputFile.toPath()),StandardCharsets.UTF_8);
                if (json.isEmpty()) throw new IOException("插件返回结果无效");
                JSONObject descriptor = new JSONObject(json); CustomPluginSchema.validate(descriptor); return descriptor;
            } finally {
                if (bound) try { app.unbindService(connection); }
                catch (IllegalArgumentException alreadyDisconnected) { /* Binding died during teardown. */ }
            }
            }
            } finally {Files.deleteIfExists(outputFile.toPath());}
            } finally {Files.deleteIfExists(inputFile.toPath());}
        }
    }
}
