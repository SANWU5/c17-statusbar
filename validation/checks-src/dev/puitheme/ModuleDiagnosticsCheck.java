package dev.puitheme;

import android.os.Bundle;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure local fixtures: no phone, provider IPC, chooser, SAF, or application storage is used. */
public final class ModuleDiagnosticsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static final class Cyclic extends RuntimeException {
        @Override public synchronized Throwable getCause() { return this; }
    }

    public static void main(String[] args) throws Exception {
        equal("diagnostics_enabled", ModuleDiagnostics.KEY_ENABLED);
        equal("record_statusbar_diagnostic", ModuleDiagnostics.METHOD_RECORD);
        equal("event_json", ModuleDiagnostics.EVENT_JSON);
        equal("初始化 hook 完成", ModuleDiagnostics.sanitizeMessage("初始化 hook 完成"));
        equal("敏感内容已省略", ModuleDiagnostics.sanitizeMessage("PIN 1234"));
        equal("敏感内容已省略", ModuleDiagnostics.sanitizeMessage("密码错误"));
        equal("敏感内容已省略", ModuleDiagnostics.sanitizeMessage("font_uri selected"));
        equal("字体失败 [路径]", ModuleDiagnostics.sanitizeMessage("字体失败 /storage/emulated/0/private.ttf"));
        equal("打开失败 [地址]", ModuleDiagnostics.sanitizeMessage("打开失败 content://private/user/text"));
        equal("打开失败 [地址]", ModuleDiagnostics.sanitizeMessage("打开失败 https://example.invalid/private"));
        equal("错误 [路径]", ModuleDiagnostics.sanitizeMessage("错误 C:\\private\\font.ttf"));
        equal("失败 [内容]", ModuleDiagnostics.sanitizeMessage("失败 \"自定义用户内容\""));
        equal("失败 [内容]", ModuleDiagnostics.sanitizeMessage("失败 ‘自定义用户内容’"));
        equal("设置 [参数]", ModuleDiagnostics.sanitizeMessage("设置 offset_x=13.27"));
        equal("错误 [数字]", ModuleDiagnostics.sanitizeMessage("错误 13100000000"));
        equal("错误 [标识]", ModuleDiagnostics.sanitizeMessage("错误 7abcd8ef"));
        equal("换行已去除", ModuleDiagnostics.sanitizeMessage("换行\n已去除\u0000"));
        equal("", ModuleDiagnostics.sanitizeMessage(null));
        for (String[] alias : new String[][]{{"systemui", "hook"}, {"hooks", "hook"}, {"settings", "setting"},
                {"tiles", "tile"}, {"radio", "radio"}, {"font", "font"}, {"export", "export"}})
            equal(alias[1], ModuleDiagnostics.event(alias[0], "fixed stage", null).getString("source"));
        equal(240, ModuleDiagnostics.sanitizeMessage(new String(new char[300]).replace('\0', 'x')).length());

        IllegalArgumentException cause = new IllegalArgumentException("秘密 1234 /data/user/0/font.ttf");
        cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("dev.puitheme.TextControls", "configure", "/private/user.ttf", 42)});
        RuntimeException error = new RuntimeException("https://private.invalid/token?password=1234", cause);
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("dev.puitheme.StatusBarModule", "hook", "secret-font.ttf", 28)});
        JSONObject exception = ModuleDiagnostics.serializeThrowable(error);
        String serialized = exception.toString();
        for (String privateValue : new String[]{"秘密", "1234", "/data/user", "secret-font", "private.invalid", "user.ttf"})
            equal(false, serialized.contains(privateValue));
        JSONArray causes = exception.getJSONArray("causes");
        equal(2, causes.length());
        equal("java.lang.RuntimeException", causes.getJSONObject(0).getString("class"));
        equal("java.lang.IllegalArgumentException", causes.getJSONObject(1).getString("class"));
        equal("dev.puitheme.TextControls", causes.getJSONObject(1).getJSONArray("stack").getJSONObject(0).getString("class"));
        equal(42, causes.getJSONObject(1).getJSONArray("stack").getJSONObject(0).getInt("line"));
        equal(false, exception.getBoolean("truncated"));
        equal(true, ModuleDiagnostics.serializeThrowable(new Cyclic()).getBoolean("truncated"));
        RuntimeException deep = new RuntimeException();
        for (int i = 0; i < 8; i++) deep = new RuntimeException("ignored", deep);
        JSONObject truncated = ModuleDiagnostics.serializeThrowable(deep);
        equal(4, truncated.getJSONArray("causes").length());
        equal(true, truncated.getBoolean("truncated"));
        StackTraceElement[] longTrace = new StackTraceElement[30];
        for (int i = 0; i < longTrace.length; i++) longTrace[i] = new StackTraceElement("dev.puitheme.Test", "safe", "ignored.ttf", i);
        error.setStackTrace(longTrace);
        equal(8, ModuleDiagnostics.serializeThrowable(error).getJSONArray("causes").getJSONObject(0).getJSONArray("stack").length());
        equal(true, ModuleDiagnostics.serializeThrowable(error).getBoolean("truncated"));

        JSONObject event = ModuleDiagnostics.event("hook", "固定 hook 失败", error);
        event.put("user_text", "用户自定义内容");event.put("private_uri", "content://secret");
        event.getJSONObject("exception").put("message", "secret-user-text");
        event.getJSONObject("exception").getJSONArray("causes").getJSONObject(0).put("message", "secret-cause");
        event.getJSONObject("exception").getJSONArray("causes").getJSONObject(0).getJSONArray("stack").getJSONObject(0).put("file", "/secret/user.ttf");
        JSONObject validated = ModuleDiagnostics.validateEvent(event);
        String safe = validated.toString();
        for (String secret : new String[]{"用户自定义内容", "content://secret", "secret-user-text", "secret-cause", "/secret/user.ttf"})
            equal(false, safe.contains(secret));
        equal("hook", validated.getString("source"));
        equal("error", validated.getString("level"));
        equal(true, validated.getString("timestamp").endsWith("Z"));
        event.put("source", "private-value");event.put("level", "invalid");event.put("timestamp", "private-time");
        validated = ModuleDiagnostics.validateEvent(event);
        equal("app", validated.getString("source"));
        equal("info", validated.getString("level"));
        equal(false, validated.getString("timestamp").contains("private-time"));

        ModuleDiagnostics.RateGate gate = new ModuleDiagnostics.RateGate();
        equal(true, gate.accept("same", 1000));
        equal(false, gate.accept("same", 1001));
        equal(false, gate.accept("same", 5999));
        equal(true, gate.accept("same", 6000));
        gate.clear();
        for (int i = 0; i < 40; i++) equal(true, gate.accept("event" + i, 10000));
        equal(false, gate.accept("extra", 10000));
        equal(true, gate.accept("next", 11000));

        Field pendingField = ModuleDiagnostics.class.getDeclaredField("PENDING");pendingField.setAccessible(true);
        @SuppressWarnings("unchecked") ArrayDeque<String> pending = (ArrayDeque<String>) pendingField.get(null);
        for (int i = 0; i < 40; i++) ModuleDiagnostics.info("hook", "startup stage " + i);
        equal(32, pending.size());
        Bundle disabled = new Bundle();disabled.putBoolean(ModuleDiagnostics.KEY_ENABLED, false);
        ModuleDiagnostics.configure(disabled);
        equal(0, pending.size());
        ModuleDiagnostics.info("hook", "disabled stage");ModuleDiagnostics.error("hook", "disabled error", error);
        equal(0, pending.size());
        Bundle active = new Bundle();active.putBoolean(ModuleDiagnostics.KEY_ENABLED, true);
        ModuleDiagnostics.configure(active);ModuleDiagnostics.error("hook", "fixed error without throwable", null);
        equal(1, pending.size());
        equal("error", new JSONObject(pending.peekLast()).getString("level"));
        ModuleDiagnostics.configure(disabled);equal(0, pending.size());

        File directory = Files.createTempDirectory("c17-diagnostic-check-").toFile();
        try {
            String line = ModuleDiagnostics.event("app", "fixed stage", null).toString();
            for (int i = 0; i < 6000; i++) ModuleDiagnostics.append(directory, line);
            File current = new File(directory, "c17.log"), previous = new File(directory, "c17.log.1");
            equal(true, current.isFile());equal(true, previous.isFile());
            equal(true, current.length() <= ModuleDiagnostics.MAX_FILE_BYTES);
            equal(true, previous.length() <= ModuleDiagnostics.MAX_FILE_BYTES);
            equal(2, directory.listFiles().length);
            byte[] full = ModuleDiagnostics.snapshotBytes(directory, "1.13.1");
            equal(true, full.length <= ModuleDiagnostics.MAX_FILE_BYTES * 2L + 4096);
            equal(true, new String(full, StandardCharsets.UTF_8).startsWith("C17 状态栏诊断日志"));
            equal(true, new String(full, StandardCharsets.UTF_8).contains("版本：1.13.1"));
            byte[] preview = ModuleDiagnostics.readTail(current, 65536);
            equal(true, preview.length <= 65536);
            equal(true, new String(preview, StandardCharsets.UTF_8).endsWith("\n"));
            equal(true, new String(preview, StandardCharsets.UTF_8).startsWith("{"));
            try {
                ModuleDiagnostics.append(directory, new String(new char[ModuleDiagnostics.MAX_EVENT_BYTES + 1]).replace('\0', 'x'));
                throw new AssertionError("Oversized event accepted");
            } catch (IOException expected) { checks++; }
            File unicode = new File(directory, "unicode.txt");
            Files.write(unicode.toPath(), "旧行\n一二三四五六七八九\n最新行\n".getBytes(StandardCharsets.UTF_8));
            equal("最新行\n", new String(ModuleDiagnostics.readTail(unicode, 14), StandardCharsets.UTF_8));
            equal(0, ModuleDiagnostics.readTail(unicode, 0).length);
        } finally {
            File[] temporary = directory.listFiles();
            if (temporary != null) for (File file : temporary) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory.toPath());
        }
        System.out.println("ModuleDiagnosticsCheck passed: " + checks);
    }
}
