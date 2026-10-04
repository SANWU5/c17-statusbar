// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native wallpaper-window blur, below every keyguard/AOD foreground window.
 * View callbacks only observe actual native state; they never draw/clear a blur
 * in the shade, keyguard or AOD window. No capture, bitmap, timer or polling.
 */
public final class LockscreenStatusBlur {
    public static final String ENABLED = "lockscreen_status_blur_enabled";
    public static final String RADIUS = "lockscreen_status_blur_radius";
    public static final String MASK_COLOR = "lockscreen_status_blur_mask_color";
    public static final String RANGE = "lockscreen_status_blur_range";
    public static final String TRANSITION = "lockscreen_status_blur_transition";
    public static final float DEFAULT_RADIUS = 16f, DEFAULT_RANGE = 56f, DEFAULT_TRANSITION = 32f;
    public static final int DEFAULT_MASK_COLOR = 0x22000000;
    public static final String SHADE_ROOT = "com.android.systemui.shade.NotificationShadeWindowView";
    public static final String KEYGUARD_ROOT = "com.android.systemui.keyguard.ui.view.KeyguardRootView";
    public static final String AOD_ROOT = "com.oplus.systemui.aod.aodclock.off.BaseRootLayout";
    public static final String STATE_CONTROLLER = "com.android.systemui.statusbar.StatusBarStateControllerImpl";
    public static final String KEYGUARD_CONTROLLER = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl";
    public static final String SCRIM_CONTROLLER = "com.android.systemui.statusbar.phone.ScrimController";
    public static final String SCRIM_VIEW = "com.android.systemui.scrim.ScrimView";
    private static final String SCRIM_DRAWABLE = "com.android.systemui.scrim.ScrimDrawable";
    private static final Method TRANSITION_ALPHA = method(View.class, "getTransitionAlpha");
    private final Map<View, Integer> hosts = new WeakHashMap<>();
    private final WallpaperBlurLayer wallpaper = new WallpaperBlurLayer();
    private final Map<Class<?>, Integer> hostKinds = new WeakHashMap<>();
    private final Map<Class<?>, StateAccess> stateAccess = new WeakHashMap<>();
    private final Map<Class<?>, KeyguardAccess> keyguardAccess = new WeakHashMap<>();
    private final Map<Class<?>, ScrimAccess> scrimAccess = new WeakHashMap<>();
    private volatile ScrimHost behindScrim, frontScrim;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean scrimRefreshPosted = new AtomicBoolean();
    private volatile WeakReference<Object> scrimController = new WeakReference<>(null);
    private volatile long runtimeGeneration;
    private volatile boolean enabled;
    private boolean stateKnown, keyguard, dozing, keyguardKnown, showing, occluded;
    private boolean crossWindowEnabled = true;
    private float dismissAmount, radius = DEFAULT_RADIUS, range = DEFAULT_RANGE, transition = DEFAULT_TRANSITION;
    private float easedDoze = Float.NaN, behindOpacity = Float.NaN, frontOpacity = Float.NaN;
    private int maskColor = DEFAULT_MASK_COLOR;
    private WindowManager blurWindowManager;
    private Consumer<Boolean> blurListener;

    private static final class StateAccess {
        final Method state, dozing, easedDoze;
        StateAccess(Class<?> type) {
            state = method(type, "getState"); dozing = method(type, "isDozing");
            easedDoze = method(type, "getInterpolatedDozeAmount");
        }
    }
    private static final class ScrimAccess {
        final Method behind, front;
        ScrimAccess(Class<?> type) {
            behind = method(type, "getScrimBehind"); front = method(type, "getScrimInFront");
        }
    }
    private static final class ScrimHost {
        final WeakReference<View> view;
        WeakReference<View>[] nativeAlphaPath;
        WeakReference<Object> nativeParent;
        final Method drawable;
        ScrimHost(View owner) {
            view = new WeakReference<>(owner); drawable = method(owner.getClass(), "getDrawable");
            nativeAlphaPath = alphaPath(owner);
            nativeParent = new WeakReference<>(owner.getParent());
        }
        float opacity() {
            View owner = view.get();
            if (owner == null || !owner.isAttachedToWindow() || !owner.isShown() || drawable == null) return Float.NaN;
            if (nativeParent.get() != owner.getParent()) {
                // A constructor can expose a scrim before its native hierarchy is
                // attached. Capture the real parent path once it is assigned.
                nativeAlphaPath = alphaPath(owner); nativeParent = new WeakReference<>(owner.getParent());
            }
            try {
                Object value = drawable.invoke(owner);
                // Exact native solid scrim only; no guessed opacity for OEM shaders.
                if (!(value instanceof Drawable) || !hasType(value.getClass(), SCRIM_DRAWABLE)) return Float.NaN;
                Drawable nativeDrawable = (Drawable) value;
                if (nativeDrawable.getColorFilter() != null) return Float.NaN;
                float inherited = nativeAlpha(nativeAlphaPath);
                // A hidden shade's old transparent scrim cannot authorize wallpaper
                // in a different AOD window. Let native eased doze own that case.
                if (inherited <= 0f) return Float.NaN;
                return LockscreenBlurGeometry.finite(nativeDrawable.getAlpha() / 255f, 1f, 0f, 1f)
                        * inherited;
            } catch (ReflectiveOperationException | RuntimeException unsupported) { return Float.NaN; }
        }
    }
    private static final class KeyguardAccess {
        final Field showing, occluded;
        final Method dismiss;
        KeyguardAccess(Class<?> type) {
            showing = field(type, "mShowing"); occluded = field(type, "mOccluded"); dismiss = method(type, "getDismissAmount");
        }
    }

    /** Existing main-thread settings delivery only. Default, safe mode and retirement all disable. */
    public void configure(Bundle source) {
        boolean next = source != null && !SafetyMode.enabled(source) && !ModuleLifecycle.removed()
                && Boolean.TRUE.equals(source.get(ENABLED)) && Build.VERSION.SDK_INT >= 31;
        float nextRadius = number(source, RADIUS, DEFAULT_RADIUS, 0f, 80f);
        float nextRange = number(source, RANGE, DEFAULT_RANGE, 0f, 640f);
        float nextTransition = number(source, TRANSITION, DEFAULT_TRANSITION, 0f, 320f);
        int nextColor = color(source == null ? null : source.get(MASK_COLOR), DEFAULT_MASK_COLOR);
        if (enabled == next && radius == nextRadius && range == nextRange && transition == nextTransition && maskColor == nextColor) return;
        enabled = next; radius = nextRadius; range = nextRange; transition = nextTransition; maskColor = nextColor;
        if (!enabled) removeBlurListener();
        else for (View view : new ArrayList<>(hosts.keySet())) if (view != null) ensureBlurListener(view.getContext());
        syncWallpaper();
    }
    public boolean enabled() { return enabled; }

    /** Only native state registration. These foreground views never own a blur layer. */
    public void onHostAttached(View owner) {
        int kind = hostKind(owner);
        if (kind == 0 || !owner.isAttachedToWindow() || hosts.containsKey(owner)) return;
        hosts.put(owner, kind);
        if (enabled) ensureBlurListener(owner.getContext());
    }
    public void onHostDetached(View owner) { hosts.remove(owner); }
    public void onHostVisibilityChanged(View owner) { onNativeScrimView(owner); }

    public void onWallpaperEngine(Object engine) {
        wallpaper.onEngine(engine);
        if (!enabled || engine == null) return;
        // Engine callbacks may use the native wallpaper worker. Listener/state maps
        // stay on the SystemUI main thread; the wallpaper helper serializes transactions.
        final long generation = runtimeGeneration;
        WeakReference<Object> reference = new WeakReference<>(engine);
        Runnable bind = () -> {
            if (generation != runtimeGeneration || !enabled) return;
            Object current = reference.get(); if (current == null) return;
            Method context = method(current.getClass(), "getDisplayContext");
            try {
                Object value = context == null ? null : context.invoke(current);
                if (value instanceof Context) ensureBlurListener((Context)value);
            } catch (ReflectiveOperationException | RuntimeException unsupported) { }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) bind.run(); else main.post(bind);
    }
    public void onWallpaperDestroyed(Object engine) { wallpaper.onDestroyed(engine); }
    public void onWallpaperFrame(Object engine) { wallpaper.onNativeFrame(engine); }
    public void beforeWallpaperSnapshot(Object engine) { wallpaper.beforeNativeSnapshot(engine); }
    public void afterWallpaperSnapshot(Object engine) { wallpaper.afterNativeSnapshot(engine); }

    /** Observe AFTER native state writes. No decisions based on guessed doze enums. */
    public void onNativeState(Object controller) {
        if (controller == null || !hasType(controller.getClass(), STATE_CONTROLLER)) return;
        StateAccess access = stateAccess.get(controller.getClass());
        if (access == null) { access = new StateAccess(controller.getClass()); stateAccess.put(controller.getClass(), access); }
        try {
            if (access.state == null || access.dozing == null) return;
            Object state = access.state.invoke(controller), doze = access.dozing.invoke(controller);
            if (!(state instanceof Integer) || !(doze instanceof Boolean)) return;
            boolean k = ((Integer) state) == 1, d = (Boolean) doze;
            float amount = Float.NaN;
            if (access.easedDoze != null) {
                Object value = access.easedDoze.invoke(controller);
                if (value instanceof Number && Float.isFinite(((Number) value).floatValue()))
                    amount = LockscreenBlurGeometry.finite(((Number) value).floatValue(), 0f, 0f, 1f);
            }
            if (!stateKnown || keyguard != k || dozing != d || !same(easedDoze, amount)) {
                stateKnown = true; keyguard = k; dozing = d; easedDoze = amount; updateVisibility();
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) { /* Native rendering retained. */ }
    }
    /** After actual native scrim writes; never use target animation alpha or notification-card scrims. */
    public void onNativeScrimController(Object controller) {
        if (controller == null || !hasType(controller.getClass(), SCRIM_CONTROLLER)) return;
        if (scrimController.get() != controller) scrimController = new WeakReference<>(controller);
        if (Looper.myLooper() != Looper.getMainLooper()) { scheduleScrimRefresh(); return; }
        ScrimAccess access = scrimAccess.get(controller.getClass());
        if (access == null) { access = new ScrimAccess(controller.getClass()); scrimAccess.put(controller.getClass(), access); }
        try {
            if (access.behind == null || access.front == null) return;
            behindScrim = bindScrim(behindScrim, access.behind.invoke(controller));
            frontScrim = bindScrim(frontScrim, access.front.invoke(controller));
            refreshScrims(true);
        } catch (ReflectiveOperationException | RuntimeException unsupported) { /* Eased native doze fallback. */ }
    }
    /** Exact registered native behind/front views only, including async drawable invalidations. */
    public void onNativeScrimView(View view) {
        ScrimHost behind = behindScrim, front = frontScrim;
        if (view != null && ((behind != null && behind.view.get() == view)
                || (front != null && front.view.get() == view))) {
            if (Looper.myLooper() != Looper.getMainLooper()) scheduleScrimRefresh(); else refreshScrims(true);
        }
    }
    private void scheduleScrimRefresh() {
        if (!scrimRefreshPosted.compareAndSet(false, true)) return;
        final long generation = runtimeGeneration;
        if (!main.post(() -> {
            scrimRefreshPosted.set(false);
            if (generation != runtimeGeneration) return;
            Object controller = scrimController.get();
            if (controller != null) onNativeScrimController(controller); else refreshScrims(true);
        })) scrimRefreshPosted.set(false);
    }
    private static ScrimHost bindScrim(ScrimHost current, Object value) {
        if (!(value instanceof View) || !hasType(value.getClass(), SCRIM_VIEW)) return null;
        return current != null && current.view.get() == value ? current : new ScrimHost((View) value);
    }
    private void refreshScrims(boolean notify) {
        float behind = behindScrim == null ? Float.NaN : behindScrim.opacity();
        float front = frontScrim == null ? Float.NaN : frontScrim.opacity();
        if (!same(behindOpacity, behind) || !same(frontOpacity, front)) {
            behindOpacity = behind; frontOpacity = front;
            if (notify) updateVisibility();
        }
    }
    private static boolean same(float a, float b) { return a == b || (Float.isNaN(a) && Float.isNaN(b)); }
    public void onNativeKeyguard(Object controller) {
        if (controller == null || !hasType(controller.getClass(), KEYGUARD_CONTROLLER)) return;
        KeyguardAccess access = keyguardAccess.get(controller.getClass());
        if (access == null) { access = new KeyguardAccess(controller.getClass()); keyguardAccess.put(controller.getClass(), access); }
        try {
            if (access.showing == null || access.occluded == null || access.dismiss == null) return;
            Object value = access.dismiss.invoke(controller); if (!(value instanceof Number)) return;
            float amount = ((Number) value).floatValue(); if (!Float.isFinite(amount)) return;
            boolean s = access.showing.getBoolean(controller), o = access.occluded.getBoolean(controller);
            amount = Math.max(0f, Math.min(1f, amount));
            if (!keyguardKnown || showing != s || occluded != o || dismissAmount != amount) {
                keyguardKnown = true; showing = s; occluded = o; dismissAmount = amount; updateVisibility();
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) { /* Native rendering retained. */ }
    }
    private float weight() {
        return enabled ? LockscreenBlurGeometry.visibility(stateKnown, keyguard, dozing,
                keyguardKnown, showing, occluded, dismissAmount)
                * LockscreenBlurGeometry.wallpaperVisibility(dozing, easedDoze, behindOpacity, frontOpacity) : 0f;
    }
    private void syncWallpaper() {
        wallpaper.configure(enabled, crossWindowEnabled, weight(), radius, range, transition, maskColor);
    }
    private void updateVisibility() { syncWallpaper(); }

    /** Existing native frames may finish an async scrim update. Observe only:
     * do NOT draw BackgroundBlurDrawable or a mask into any foreground window.
     */
    public void drawBefore(View owner, Canvas canvas) {
        if (!enabled || owner == null) return;
        onHostAttached(owner); refreshScrims(true);
    }
    private void ensureBlurListener(Context context) {
        if (!enabled || blurListener != null || Build.VERSION.SDK_INT < 31) return;
        Object service = context.getSystemService(Context.WINDOW_SERVICE);
        if (!(service instanceof WindowManager)) return;
        WindowManager manager = (WindowManager) service;
        try {
            crossWindowEnabled = manager.isCrossWindowBlurEnabled();
            Consumer<Boolean> listener = value -> {
                boolean next = Boolean.TRUE.equals(value); if (crossWindowEnabled == next) return;
                crossWindowEnabled = next; syncWallpaper();
            };
            manager.addCrossWindowBlurEnabledListener(command -> main.post(command), listener);
            blurWindowManager = manager; blurListener = listener; syncWallpaper();
        } catch (RuntimeException unsupported) { crossWindowEnabled = false; syncWallpaper(); }
    }
    private void removeBlurListener() {
        if (Build.VERSION.SDK_INT >= 31 && blurWindowManager != null && blurListener != null) {
            try { blurWindowManager.removeCrossWindowBlurEnabledListener(blurListener); } catch (RuntimeException ignored) { }
        }
        blurWindowManager = null; blurListener = null;
    }
    private int hostKind(View view) {
        if (view == null) return 0;
        Integer known = hostKinds.get(view.getClass()); if (known != null) return known;
        int kind = hasType(view.getClass(), AOD_ROOT) ? 3 : hasType(view.getClass(), KEYGUARD_ROOT) ? 2
                : hasType(view.getClass(), SHADE_ROOT) ? 1 : 0;
        hostKinds.put(view.getClass(), kind); return kind;
    }
    @SuppressWarnings("unchecked")
    private static WeakReference<View>[] alphaPath(View owner) {
        ArrayList<WeakReference<View>> path = new ArrayList<>();
        View at = owner;
        for (int i = 0; at != null && i < 32; i++) {
            path.add(new WeakReference<>(at));
            at = at.getParent() instanceof View ? (View) at.getParent() : null;
        }
        return path.toArray(new WeakReference[0]);
    }
    private static float nativeAlpha(WeakReference<View>[] path) {
        float alpha = 1f;
        for (WeakReference<View> reference : path) {
            View view = reference.get(); if (view == null || view.getVisibility() != View.VISIBLE) return 0f;
            alpha *= LockscreenBlurGeometry.finite(view.getAlpha(), 0f, 0f, 1f);
            if (TRANSITION_ALPHA != null) try {
                Object transition = TRANSITION_ALPHA.invoke(view);
                if (transition instanceof Number) alpha *= LockscreenBlurGeometry.finite(((Number) transition).floatValue(), 0f, 0f, 1f);
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* Ordinary alpha still follows the native tree. */ }
        }
        return alpha;
    }
    public void releaseRuntime() {
        runtimeGeneration++; scrimController.clear();
        enabled = false; wallpaper.releaseRuntime(); hosts.clear();
        stateAccess.clear(); keyguardAccess.clear(); hostKinds.clear(); removeBlurListener();
        scrimAccess.clear(); behindScrim = frontScrim = null;
        easedDoze = behindOpacity = frontOpacity = Float.NaN;
        stateKnown = keyguardKnown = false; keyguard = showing = dozing = occluded = false; dismissAmount = 0f;
    }
    public String summary() {
        return "enabled=" + enabled + ",stateKnown=" + stateKnown + ",keyguard=" + keyguard
                + ",dozing=" + dozing + ",windowBlur=" + crossWindowEnabled + ",easedDoze=" + easedDoze
                + ",behindOpacity=" + behindOpacity + ",frontOpacity=" + frontOpacity
                + "," + wallpaper.summary() + ",source=WallpaperEngineSurfaceOnly+nativeDismiss+nativeScrim";
    }
    private static float number(Bundle b, String key, float fallback, float low, float high) {
        Object value = b == null ? null : b.get(key);
        return value instanceof Number ? LockscreenBlurGeometry.finite(((Number) value).floatValue(), fallback, low, high) : fallback;
    }
    private static int color(Object value, int fallback) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) try { return Color.parseColor((String) value); } catch (IllegalArgumentException ignored) { }
        return fallback;
    }
    private static boolean hasType(Class<?> type, String name) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) if (name.equals(at.getName())) return true;
        return false;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) try {
            Method method = at.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
    private static Field field(Class<?> type, String name) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) try {
            Field field = at.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
}
