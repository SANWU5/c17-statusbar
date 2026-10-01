// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Root authorization and process checks run outside the UI thread. */
public final class SystemUiRestart {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final long TIMEOUT_SECONDS = 45L;
    // Only the exact SystemUI process is signalled; success requires a different live PID.
    private static final String COMMAND =
            "uid=$(id -u) || exit 21; "
            + "if [ \"$uid\" != 0 ]; then echo C17_ERROR:NOT_ROOT; exit 21; fi; "
            + "old=$(pidof com.android.systemui); "
            + "if [ -z \"$old\" ]; then echo C17_ERROR:NOT_RUNNING; exit 22; fi; "
            + "kill -TERM $old || { echo C17_ERROR:KILL_FAILED; exit 23; }; "
            + "for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do "
            + "sleep 0.5; new=$(pidof com.android.systemui); same=0; "
            + "for oldpid in $old; do case \" $new \" in *\" $oldpid \"*) same=1;; esac; done; "
            + "if [ -n \"$new\" ] && [ \"$same\" = 0 ]; then echo C17_OK:$new; exit 0; fi; done; "
            + "echo C17_ERROR:NOT_RESTARTED; exit 24";

    private SystemUiRestart() { }
    public enum State { RESTARTED, ROOT_REQUIRED, NOT_RUNNING, FAILED, TIMEOUT }
    public interface Callback { void onResult(Result result); }

    public static final class Result {
        public final State state;
        public final boolean success;
        public final String message;
        private Result(State state, String message) {
            this.state = state;
            this.success = state == State.RESTARTED;
            this.message = message;
        }
    }

    public static final class Request {
        private volatile boolean cancelled;
        public void cancel() { cancelled = true; }
    }

    /** Call only from the user's restart button; cancelling suppresses callbacks, not an issued restart. */
    public static Request restart(Callback callback) {
        Request request = new Request();
        Handler main = new Handler(Looper.getMainLooper());
        WORK.execute(() -> {
            if (request.cancelled) return;
            Result result;
            java.lang.Process process = null;
            Thread reader = null;
            BoundedOutput output = new BoundedOutput();
            try {
                process = new ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true).start();
                final InputStream stream = process.getInputStream();
                reader = new Thread(() -> output.drain(stream), "C17RestartOutput");
                reader.setDaemon(true);
                reader.start();
                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    result = classify(-1, "", true);
                } else {
                    reader.join(1000L);
                    result = classify(process.exitValue(), output.text(), false);
                }
            } catch (IOException missingRoot) {
                result = new Result(State.ROOT_REQUIRED, "无法获得 Root 权限。请确认设备已 Root，并在 Root 管理器中允许本应用。" );
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (process != null) process.destroyForcibly();
                result = new Result(State.FAILED, "重启操作已中断，请重新检测系统 UI 状态。" );
            } catch (RuntimeException unavailable) {
                if (process != null) process.destroyForcibly();
                result = new Result(State.FAILED, "无法执行系统 UI 重启，请检查 Root 授权后重试。" );
            } finally {
                if (process != null) {
                    try { process.getInputStream().close(); } catch (IOException ignored) { }
                    try { process.getOutputStream().close(); } catch (IOException ignored) { }
                    try { process.getErrorStream().close(); } catch (IOException ignored) { }
                }
            }
            final Result completed = result;
            main.post(() -> { if (!request.cancelled) callback.onResult(completed); });
        });
        return request;
    }

    static Result classify(int exit, String output, boolean timedOut) {
        if (timedOut) return new Result(State.TIMEOUT, "Root 授权或重启等待超时，请检查授权并重新检测系统 UI 状态。" );
        String text = output == null ? "" : output;
        if (exit == 0) for (String line : text.split("\\R")) {
            if (line.trim().matches("C17_OK:[1-9][0-9]*( [1-9][0-9]*)*"))
                return new Result(State.RESTARTED, "系统 UI 已重启，正在重新检测模块激活状态。" );
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (exit == 21 || text.contains("C17_ERROR:NOT_ROOT") || lower.contains("permission denied")
                || lower.contains("not allowed") || lower.contains("access denied"))
            return new Result(State.ROOT_REQUIRED, "未获得 Root 权限。请在 Root 管理器中允许本应用后重试。" );
        if (exit == 22 || text.contains("C17_ERROR:NOT_RUNNING"))
            return new Result(State.NOT_RUNNING, "未找到正在运行的 SystemUI，请检查系统 UI 状态后重试。" );
        if (exit == 24 || text.contains("C17_ERROR:NOT_RESTARTED"))
            return new Result(State.FAILED, "已请求重启，但未确认新的 SystemUI 进程。请重新检测，必要时手动重启手机。" );
        return new Result(State.FAILED, "系统 UI 重启失败，请检查 Root 授权后重试。" );
    }

    private static final class BoundedOutput {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        void drain(InputStream stream) {
            byte[] buffer = new byte[1024];
            try {
                int count;
                while ((count = stream.read(buffer)) != -1) synchronized (this) {
                    int remaining = 8192 - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(remaining, count));
                }
            } catch (IOException closed) { }
        }
        synchronized String text() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
    }
}
