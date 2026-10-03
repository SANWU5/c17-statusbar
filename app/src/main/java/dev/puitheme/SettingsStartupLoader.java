// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** One bounded startup/recovery window. IPC is single-flight off the UI thread;
 * only complete fresh snapshots are delivered on the main thread. */
final class SettingsStartupLoader {
    private static final long[] RETRIES = {250L, 500L, 1000L, 2000L, 4000L, 8000L, 16000L};
    interface Scheduler {
        void main(Runnable task);
        void worker(Runnable task);
        void after(Runnable task,long delay);
        void cancel(Runnable task);
        void close();
    }
    interface Source { Bundle read() throws Exception; }
    interface Apply { boolean accept(Bundle snapshot,boolean force) throws Exception; }
    private final Scheduler scheduler;
    private final Source source;
    private final Apply apply;
    private final BooleanSupplier removed;
    private final BooleanSupplier beforeRead;
    private volatile boolean requested, stopped;
    // Mutable state below belongs exclusively to the main scheduler.
    private boolean reading, force;
    private int attempts;
    private long generation;
    private Runnable timer;

    SettingsStartupLoader(Handler main,Source source,Apply apply,BooleanSupplier removed,BooleanSupplier beforeRead) {
        this(new Scheduler() {
            final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
                Thread thread=new Thread(task,"C17-settings-startup");thread.setDaemon(true);return thread;
            });
            public void main(Runnable task){main.post(task);}
            public void worker(Runnable task){worker.execute(task);}
            public void after(Runnable task,long delay){main.postDelayed(task,delay);}
            public void cancel(Runnable task){main.removeCallbacks(task);}
            public void close(){worker.shutdownNow();}
        },source,apply,removed,beforeRead);
    }
    SettingsStartupLoader(Scheduler scheduler,Source source,Apply apply,BooleanSupplier removed,BooleanSupplier beforeRead) {
        this.scheduler=scheduler;this.source=source;this.apply=apply;this.removed=removed;this.beforeRead=beforeRead;
    }
    /** Repeated view/context bindings must never restart an exhausted polling window. */
    synchronized void start() {
        if(requested||stopped)return;
        requested=true;scheduler.main(()->begin(false));
    }
    /** Called only by a real settings/unlock/boot event or a new native consumer. */
    void signal(boolean force) {
        if(stopped)return;
        requested=true;scheduler.main(()->begin(force));
    }
    private boolean inactive() {
        if(stopped)return true;
        if(removed.getAsBoolean()){stop();return true;}
        return false;
    }
    private void begin(boolean force) {
        if(inactive())return;
        cancelTimer();generation++;attempts=0;this.force|=force;
        if(!reading)launch();
    }
    private void launch() {
        if(inactive()||reading)return;
        cancelTimer();reading=true;attempts++;
        final long token=generation;
        boolean observed=false;
        try {observed=beforeRead.getAsBoolean();}catch(RuntimeException unavailable){/* Retry observation registration too. */}
        final boolean observedBeforeRead=observed;
        try {
            scheduler.worker(()->{
                Bundle snapshot=null;
                try {if(!stopped&&!removed.getAsBoolean())snapshot=source.read();}
                catch(Exception unavailable){/* Provider/application is not ready yet. */}
                final Bundle delivered=snapshot;
                scheduler.main(()->complete(token,delivered,observedBeforeRead));
            });
        } catch(RuntimeException unavailable){complete(token,null,observedBeforeRead);}
    }
    private void complete(long token,Bundle snapshot,boolean observedBeforeRead) {
        reading=false;
        if(inactive())return;
        // A setting changed while IPC was pending: never apply that older result.
        if(token!=generation){launch();return;}
        boolean ready=false;
        if(snapshot!=null)try {ready=apply.accept(snapshot,force);}
        catch(Exception rejected){/* A failed apply must retry, retaining its prior snapshot. */}
        // A snapshot read while observation was unavailable may have raced a
        // missed write. Require one successful read after registration too.
        if(ready&&observedBeforeRead){force=false;cancelTimer();return;}
        if(attempts>RETRIES.length)return;
        final long retryToken=generation;
        Runnable retry=()->{
            if(inactive()||retryToken!=generation)return;
            timer=null;launch();
        };
        timer=retry;scheduler.after(retry,RETRIES[attempts-1]);
    }
    private void cancelTimer(){if(timer!=null){scheduler.cancel(timer);timer=null;}}
    void stop() {
        if(stopped)return;
        stopped=true;generation++;cancelTimer();scheduler.close();
    }
}
