// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
