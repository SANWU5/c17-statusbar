// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Manifest isolatedProcess=true: no app permissions, private files, network or Root identity. */
public final class CustomPluginService extends Service {
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task,"C17-plugin-watchdog"); thread.setDaemon(true); return thread;
    });
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(),this::receive));

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    private boolean receive(Message request) {
        if (request.what != CustomPluginSandbox.EVALUATE || request.replyTo == null) return true;
        AtomicBoolean finished = new AtomicBoolean();
        // This independent watchdog also stops native regular expressions/allocations that
        // cannot be interrupted by Rhino's interpreter instruction counter.
        ScheduledFuture<?> deadline = watchdog.schedule(() -> {
            if (!finished.get()) Process.killProcess(Process.myPid());
        },1500,TimeUnit.MILLISECONDS);
        Bundle result = new Bundle();
        boolean memoryFailure = false;
        try {
            ParcelFileDescriptor input=request.getData().getParcelable("input"),output=request.getData().getParcelable("output");
            if(input==null||output==null)throw new IllegalArgumentException("插件隔离文件通道无效");
            try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(input);
                    ParcelFileDescriptor.AutoCloseOutputStream destination=new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int count;
                while((count=stream.read(buffer))!=-1)if(count!=0)bytes.write(buffer,0,count);
                JSONObject data=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                destination.write(CustomPluginEngine.evaluate(data.getString("source"),data.getJSONObject("state")).toString().getBytes(StandardCharsets.UTF_8));destination.flush();
                result.putString("result","ok");
            }
        } catch (OutOfMemoryError failed) {
            memoryFailure = true;
        } catch (Throwable failed) {
            String reason = failed.getMessage();
            result.putString("error",reason == null ? "插件运行失败" : reason.substring(0,Math.min(reason.length(),512)));
        } finally {
            finished.set(true); deadline.cancel(false);
        }
        if (memoryFailure) { Process.killProcess(Process.myPid()); return true; }
        try {
            Message reply = Message.obtain(null,CustomPluginSandbox.EVALUATE); reply.setData(result); request.replyTo.send(reply);
        } catch (Exception disconnected) { /* The requesting activity can leave while a script is being checked. */ }
        return true;
    }

    @Override public void onDestroy() { watchdog.shutdownNow(); super.onDestroy(); }
}
