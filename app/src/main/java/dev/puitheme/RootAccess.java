// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A real, bounded su authorization check. No permission is inferred from an installed manager. */
public final class RootAccess {
    private static final long TIMEOUT_SECONDS = 30L;
    private static final String COMMAND = "uid=$(id -u) || exit 21; "
            + "printf 'C17_ROOT_UID:%s\\n' \"$uid\"; [ \"$uid\" = 0 ]";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "C17-root-check");
        worker.setDaemon(true);
        return worker;
    });

    private RootAccess() { }
    public enum State { GRANTED, DENIED, UNAVAILABLE, TIMEOUT, FAILED }
    public interface Callback { void onResult(Result result); }

    public static final class Result {
        public final State state;
        public final boolean granted;
        public final String message;
        private Result(State state, String message) {
            this.state = state;
            this.granted = state == State.GRANTED;
            this.message = message;
        }
    }

    public static final class Request {
        private volatile boolean cancelled;
        private java.lang.Process process;
        private Future<?> future;

        /** Cancels a pending authorization check and suppresses any queued UI callback. */
        public synchronized void cancel() {
            cancelled = true;
            if (process != null) process.destroyForcibly();
            if (future != null) future.cancel(true);
        }
        private synchronized void bind(java.lang.Process started) {
            if (cancelled) started.destroyForcibly();
            else process = started;
        }
        private synchronized void bind(Future<?> scheduled) {
            future = scheduled;
            if (cancelled) scheduled.cancel(true);
        }
        private synchronized void finished() { process = null; future = null; }
    }

    /** Worker performs su/id; callback is delivered on the main thread unless cancelled. */
    public static Request check(Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Missing Root callback");
        Request request = new Request();
        Handler main = new Handler(Looper.getMainLooper());
        request.bind(WORK.submit(() -> {
            if (request.cancelled) return;
            java.lang.Process process = null;
            Result result;
            BoundedOutput output = new BoundedOutput();
            try {
                process = new ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true).start();
                request.bind(process);
                if (request.cancelled) return;
                final InputStream stream = process.getInputStream();
                Thread reader = new Thread(() -> output.drain(stream), "C17-root-output");
                reader.setDaemon(true);
                reader.start();
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    result = classify(-1, "", true);
                } else {
                    reader.join(500L);
                    result = classify(process.exitValue(), output.text(), false);
                }
            } catch (IOException missingRoot) {
                result = new Result(State.UNAVAILABLE, "未找到可用的 Root 环境，请确认设备已 Root。" );
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (process != null) process.destroyForcibly();
                result = new Result(State.FAILED, "Root 检测已中断，请重试。" );
            } catch (RuntimeException unavailable) {
                if (process != null) process.destroyForcibly();
                result = new Result(State.FAILED, "无法完成 Root 检测，请检查授权后重试。" );
            } finally {
                if (process != null) {
                    try { process.getInputStream().close(); } catch (IOException ignored) { }
                    try { process.getOutputStream().close(); } catch (IOException ignored) { }
                    try { process.getErrorStream().close(); } catch (IOException ignored) { }
                }
                request.finished();
            }
            final Result completed = result;
            main.post(() -> { if (!request.cancelled) callback.onResult(completed); });
        }));
        return request;
    }

    static Result classify(int exit, String output, boolean timedOut) {
        if (timedOut) return new Result(State.TIMEOUT, "Root 授权等待超时，请在 Root 管理器中允许本应用后重试。" );
        if (exit == 0 && output != null) for (String line : output.split("\\R"))
            if ("C17_ROOT_UID:0".equals(line.trim()))
                return new Result(State.GRANTED, "已获得 Root 权限，可保存配置和切换安全模式。" );
        return new Result(State.DENIED, "未获得 Root 权限，请在 Root 管理器中允许本应用。" );
    }

    /** Only the verified fixed-project installer calls this; no arbitrary command or URL API is exposed. */
    static boolean installVerified(File apk,String sha256,int userId) throws IOException {
        String command=installCommand(apk.getCanonicalPath(),sha256,userId);
        java.lang.Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        BoundedOutput output=new BoundedOutput();
        Thread reader=new Thread(()->output.drain(process.getInputStream()),"C17-update-root-output");
        reader.setDaemon(true);reader.start();
        try {
            if(!process.waitFor(120L,TimeUnit.SECONDS)) {
                // TERM permits the root shell's EXIT trap to remove both copies; installation itself is atomic.
                process.destroy();throw new IOException("Root installation timed out");
            }
            reader.join(500L);
            if(process.exitValue()!=0)return false;
            for(String line:output.text().split("\\R"))if("C17_UPDATE_INSTALLED".equals(line.trim()))return true;
            return false;
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();process.destroy();throw new IOException("Root installation interrupted",interrupted);}
        finally {
            try{process.getInputStream().close();}catch(IOException ignored){}
            try{process.getOutputStream().close();}catch(IOException ignored){}
            try{process.getErrorStream().close();}catch(IOException ignored){}
        }
    }

    static String installCommand(String source,String sha256,int userId) {
        if(source==null||!source.startsWith("/data/")||source.indexOf('\n')>=0||source.indexOf('\r')>=0
                ||sha256==null||!sha256.matches("[0-9a-f]{64}")||userId<0||userId>99999)
            throw new IllegalArgumentException("Invalid verified APK");
        String leaf=new File(source).getName();
        if(!leaf.matches("c17-update-[0-9a-f]{32}\\.apk"))throw new IllegalArgumentException("Invalid update artifact");
        String stage="/data/local/tmp/"+leaf;
        String cleanup="rm -f -- "+shellQuote(stage)+" "+shellQuote(source);
        return "set -eu; [ \"$(id -u)\" = 0 ]; trap "+shellQuote(cleanup)+" EXIT HUP INT TERM; "
                +"cp -- "+shellQuote(source)+" "+shellQuote(stage)+"; chmod 644 "+shellQuote(stage)+"; "
                +"actual=$(sha256sum "+shellQuote(stage)+"); actual=${actual%% *}; [ \"$actual\" = "+shellQuote(sha256)+" ]; "
                +"pm install -r --user "+userId+" "+shellQuote(stage)+"; printf 'C17_UPDATE_INSTALLED\\n'";
    }
    private static String shellQuote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}

    private static final class BoundedOutput {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        void drain(InputStream stream) {
            byte[] buffer = new byte[1024];
            try {
                int count;
                while ((count = stream.read(buffer)) != -1) synchronized (this) {
                    int remaining = 4096 - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(remaining, count));
                }
            } catch (IOException closed) { }
        }
        synchronized String text() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
    }
}
