// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides when the native privacy capsule shows and what it says. The input is the privacy item list SystemUI hands its
 * callbacks; the capsule itself is the system fluid cloud ({@link PrivacyLiveAlert}), which also handles the shade, the
 * lock screen and screen-off on its own. Everything here runs on the main thread.
 *
 * <p>SystemUI keeps a privacy item for a few seconds after the app stops (so the indicator does not flicker). The capsule
 * follows the real camera/microphone state instead: an app-op "active" watcher marks a use as stopped the moment it stops,
 * so a brief use closes the capsule at once. Location has no active state and follows the item list.</p>
 */
final class PrivacyCapsule {
    private static final String[] ORDER = {"TYPE_CAMERA", "TYPE_MICROPHONE", "TYPE_LOCATION"};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::expired;
    private PrivacyIndicatorSettings settings = PrivacyIndicatorSettings.from(null);
    private Context context;
    private List<Use> uses = Collections.emptyList();
    /** "op|uid|package" -> last reported active state. Main thread; pruned to the current uses. */
    private final Map<String, Boolean> opActive = new HashMap<>();
    private AppOpsManager.OnOpActiveChangedListener watcher;
    private static final String[] CAMERA_OPS = {AppOpsManager.OPSTR_CAMERA, "android:phone_call_camera"};
    private static final String[] MICROPHONE_OPS = {AppOpsManager.OPSTR_RECORD_AUDIO, "android:phone_call_microphone"};

    private static final class Use {
        final Object type; final int uid; final String packageName;
        Use(Object type, int uid, String packageName) { this.type = type; this.uid = uid; this.packageName = packageName; }
    }
    private String shownKey = "";
    private int shownIcon;
    private boolean showing, timedOut;

    void configure(PrivacyIndicatorSettings next) { main.post(() -> { settings = next; shownKey = ""; update(); }); }

    /** The privacy item list changed; called on whichever thread SystemUI publishes it. */
    void itemsChanged(Context systemUi, List<?> items) {
        List<Use> next = new ArrayList<>();
        if (items != null) for (Object item : items) {
            Object type = read(item, "privacyType");
            if (type == null || Boolean.TRUE.equals(read(item, "paused"))) continue;
            Object application = read(item, "application"), uid = read(application, "uid"), name = read(application, "packageName");
            next.add(new Use(type, uid instanceof Integer ? (Integer) uid : -1, name instanceof String ? (String) name : null));
        }
        main.post(() -> {
            if (context == null) context = systemUi;
            uses = next; watch(); prune(); update();
        });
    }

    /** Starts the real camera/microphone watcher once SystemUI's context is known. */
    private void watch() {
        if (watcher != null || context == null || Build.VERSION.SDK_INT < 30) return;
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops == null) return;
        AppOpsManager.OnOpActiveChangedListener listener = (op, uid, packageName, active) -> {
            opActive.put(op + "|" + uid + "|" + packageName, active);
            update();
        };
        String[] all = {CAMERA_OPS[0], CAMERA_OPS[1], MICROPHONE_OPS[0], MICROPHONE_OPS[1]};
        try { ops.startWatchingActive(all, main::post, listener); watcher = listener; return; }
        catch (IllegalArgumentException unknownOp) { /* a ROM without the phone-call ops: the public two still work */ }
        catch (RuntimeException failed) { return; }
        try { ops.startWatchingActive(new String[]{CAMERA_OPS[0], MICROPHONE_OPS[0]}, main::post, listener); watcher = listener; }
        catch (RuntimeException failed) { /* fall back to the item list alone */ }
    }

    /** Forget apps that no longer appear in the list; a new use starts from "trust the list". */
    private void prune() {
        if (opActive.isEmpty()) return;
        opActive.keySet().removeIf(key -> {
            for (Use use : uses) if (key.endsWith("|" + use.uid + "|" + use.packageName)) return false;
            return true;
        });
    }

    /** False only once the system has said every camera/microphone op of this app stopped; location follows the list. */
    private boolean running(Use use) {
        String name = ((Enum<?>) use.type).name();
        String[] ops = "TYPE_CAMERA".equals(name) ? CAMERA_OPS : "TYPE_MICROPHONE".equals(name) ? MICROPHONE_OPS : null;
        if (ops == null || use.packageName == null) return true;
        boolean reported = false;
        for (String op : ops) {
            Boolean active = opActive.get(op + "|" + use.uid + "|" + use.packageName);
            if (Boolean.TRUE.equals(active)) return true;
            reported |= active != null;
        }
        return !reported;
    }

    private void update() {
        Object type = settings.capsule && context != null ? wanted() : null;
        String key = type == null ? "" : ((Enum<?>) type).name();
        if (!key.equals(shownKey)) {
            // A different use starts its own display time.
            shownKey = key; timedOut = false;
            main.removeCallbacks(expire);
            if (!key.isEmpty() && settings.durationMillis > 0) main.postAtTime(expire, SystemClock.uptimeMillis() + settings.durationMillis);
        }
        boolean show = type != null && !timedOut && Build.VERSION.SDK_INT >= 30;
        int icon = show ? iconOf(type) : 0;
        if (show && icon != 0) {
            Object name = call(type, "getName");
            if (showing && shownIcon != icon) PrivacyLiveAlert.cancel(context, shownIcon);
            PrivacyLiveAlert.show(context, icon, name == null ? "" : name.toString());
            showing = true; shownIcon = icon;
        } else if (showing) {
            PrivacyLiveAlert.cancel(context, shownIcon);
            showing = false;
        }
    }

    private void expired() { timedOut = true; update(); }

    /** The first chosen type in camera, microphone, location order. */
    private Object wanted() {
        boolean[] allowed = {settings.camera, settings.microphone, settings.location};
        for (int i = 0; i < ORDER.length; i++) {
            if (!allowed[i]) continue;
            for (Use use : uses)
                if (use.type instanceof Enum && ORDER[i].equals(((Enum<?>) use.type).name()) && running(use)) return use.type;
        }
        return null;
    }

    private static int iconOf(Object type) {
        try { return (Integer) type.getClass().getMethod("getIconId").invoke(type); }
        catch (ReflectiveOperationException | RuntimeException failed) { return 0; }
    }

    private Object call(Object type, String method) {
        try { return type.getClass().getMethod(method, Context.class).invoke(type, context); }
        catch (ReflectiveOperationException | RuntimeException failed) { return null; }
    }

    static Object read(Object owner, String name) {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException next) { /* superclass */ }
            catch (ReflectiveOperationException | RuntimeException failed) { return null; }
        }
        return null;
    }

    void release() {
        main.post(() -> {
            main.removeCallbacks(expire);
            if (showing) PrivacyLiveAlert.cancel(context, shownIcon);
            showing = false;
            if (watcher != null && context != null && Build.VERSION.SDK_INT >= 30) {
                AppOpsManager ops = context.getSystemService(AppOpsManager.class);
                try { if (ops != null) ops.stopWatchingActive(watcher); } catch (RuntimeException ignored) { /* already gone */ }
            }
            watcher = null; opActive.clear();
        });
    }
}
