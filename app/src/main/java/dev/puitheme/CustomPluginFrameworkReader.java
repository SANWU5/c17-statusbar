// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import io.github.libxposed.api.error.XposedFrameworkError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads only the current package's verified plan from the main settings boot snapshot. */
final class CustomPluginFrameworkReader implements AutoCloseable {
    private final CustomPluginRuntime.Install install;
    private final String target;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private volatile SharedPreferences preferences;
    private volatile boolean closed;
    private volatile long snapshotRevision;
    private boolean snapshotWithdrawn;

    CustomPluginFrameworkReader(CustomPluginRuntime.Install install, String target, Runnable changed) {
        this.install = install;
        this.target = target;
        listener = (owner, key) -> {
            if (!closed && (key == null || SettingsFrameworkMirror.REVISION.equals(key))) changed.run();
        };
    }

    // Each scope's startup worker owns observation and reading. close() may race with that worker.
    boolean observe() {
        if (closed) return false;
        if (preferences != null) return true;
        try {
            SharedPreferences current = install.frameworkPreferences();
            if (current == null || closed) return false;
            current.registerOnSharedPreferenceChangeListener(listener);
            synchronized (this) {
                if (!closed) {
                    preferences = current;
                    return true;
                }
            }
            unobserve(current);
        } catch (Exception | LinkageError | XposedFrameworkError unavailable) {
            // Embedded frameworks may not support remote storage. The live reader still works.
        }
        return false;
    }

    long snapshotRevision() { return snapshotRevision; }
    boolean snapshotWithdrawn() { return snapshotWithdrawn; }

    JSONObject read() {
        snapshotWithdrawn = false;
        if (!observe()) return null;
        SharedPreferences current = preferences;
        if (current == null) return null;
        try {
            Map<String, ?> values = current.getAll();
            Object mainRevision = values.get(SettingsFrameworkMirror.REVISION);
            Object mainSchema = values.get(SettingsFrameworkMirror.SCHEMA);
            Object mainSafety = values.get(StatusBarSettings.SAFE_MODE);
            Object encoded = values.get(CustomPluginFrameworkPublisher.KEY);
            if (!(mainRevision instanceof Long) || (Long) mainRevision <= 0
                    || !Integer.valueOf(SettingsFrameworkMirror.VERSION).equals(mainSchema)
                    || !(mainSafety instanceof Boolean) || !(encoded instanceof String)
                    || ((String) encoded).length() > CustomPluginFrameworkPublisher.MAX_METADATA_CHARS) return null;
            if (CustomPluginFrameworkPublisher.WITHDRAWN.equals(encoded)) {
                snapshotRevision = (Long) mainRevision;
                snapshotWithdrawn = true;
                return null;
            }
            JSONObject metadata = new JSONObject((String) encoded);
            long revision = integer(metadata, "revision");
            if (integer(metadata, "format") != CustomPluginFrameworkPublisher.FORMAT || revision < 0
                    || !mainSafety.equals(metadata.opt("safeMode"))) return null;
            JSONObject plans = metadata.getJSONObject("plans");
            if (closed) return null;
            snapshotRevision = (Long) mainRevision;
            // A verified revision with an unusable target file must not keep old effects running.
            snapshotWithdrawn = true;
            boolean systemUi = ModuleRuntimeStatus.SYSTEM_UI.equals(target);
            JSONObject plan;
            if (!plans.has(target)) {
                // A missing scope in a valid snapshot means it no longer has any installed plugins.
                if (systemUi) return null;
                plan = new JSONObject().put("revision", revision).put("targetPackage", target)
                        .put("safeMode", mainSafety).put("runtimes", new JSONArray())
                        .put("installedPlugins", new JSONArray());
            } else {
                plan = new JSONObject(readVerified(plans.getJSONObject(target)));
                if (!target.equals(plan.opt("targetPackage")) || revision != integer(plan, "revision")
                        || !mainSafety.equals(plan.opt("safeMode")) || plan.has("adminCommands")
                        || plan.has("error") || !(plan.opt("runtimes") instanceof JSONArray)
                        || !(plan.opt("installedPlugins") instanceof JSONArray)) return null;
                if (systemUi) {
                    if (!(plan.opt("settings") instanceof JSONObject) || !(plan.opt("hooks") instanceof JSONArray)) return null;
                } else if (plan.has("settings") || plan.has("hooks")) return null;
            }
            if (closed) return null;
            snapshotWithdrawn = false;
            return plan;
        } catch (Exception | LinkageError | XposedFrameworkError unavailable) {
            return null;
        }
    }

    private String readVerified(JSONObject reference) throws Exception {
        String sha = reference.getString("sha256"), file = reference.getString("file");
        long size = integer(reference, "bytes");
        if (size <= 0 || size > CustomPluginFrameworkPublisher.MAX_PLAN_BYTES || !sha.matches("[0-9a-f]{64}")
                || !file.equals(CustomPluginFrameworkPublisher.PLAN_PREFIX + sha))
            throw new IOException("Invalid boot plan reference");
        ParcelFileDescriptor descriptor = install.openFrameworkFile(file);
        if (descriptor == null) throw new IOException("Missing boot plan");
        byte[] data;
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            data = new byte[(int) size];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count <= 0) throw new IOException("Truncated boot plan");
                offset += count;
            }
            if (input.read() != -1) throw new IOException("Oversized boot plan");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        for (int i = 0; i < digest.length; i++) {
            if (Character.digit(sha.charAt(i * 2), 16) != ((digest[i] & 0xf0) >>> 4)
                    || Character.digit(sha.charAt(i * 2 + 1), 16) != (digest[i] & 0x0f))
                throw new IOException("Unverified boot plan");
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static long integer(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value instanceof Number) {
            Number number = (Number) value;
            if (number.doubleValue() == number.longValue()) return number.longValue();
        }
        return -1L;
    }

    @Override public void close() {
        SharedPreferences current;
        synchronized (this) {
            closed = true;
            current = preferences;
            preferences = null;
        }
        if (current != null) unobserve(current);
    }

    private void unobserve(SharedPreferences current) {
        try { current.unregisterOnSharedPreferenceChangeListener(listener); }
        catch (Exception | LinkageError | XposedFrameworkError ignored) { /* Closed readers discard all late events. */ }
    }
}
