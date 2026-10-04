// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Colors only the audited expanded Live Alert background using its existing accent.
 *
 * The native BackgroundUtils builds its OplusBlurParam and gradient in one call.
 * Substitute only that call's background resource reads, keeping its alpha, shape,
 * glass, press effects and animation. No bitmap extraction, blur computation,
 * per-frame view traversal, shared resource mutation or global black-color hook.
 */
public final class FluidCloudAccent {
    public static final String ENABLED = "fluid_cloud_accent_enabled";
    public static final String CARD = "com.oplus.systemui.plugins.seedling.card.ui.view.CardView";
    public static final String BACKGROUND = "com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView";
    public static final String BACKGROUND_HELPER = "com.heytap.log.nx.obus.a";
    public static final String MEDIA_SECTION = "com.oplus.systemui.plugins.shared.template.section.media.s1";
    public static final String PRIMARY_MODEL = "com.oplus.systemui.plugins.shared.data.media.model.a";
    private static final String MANAGER = "com.oplus.view.ViewRootManager";
    private static final String TAG = "C17FluidAccent";
    private static final int MAX_PARENT_DEPTH = 32;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<ClassLoader, Hooks> contracts = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, BackgroundBinding> backgrounds = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, MediaBinding> media = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Integer> accents = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<BackgroundScope> activeBackground = new ThreadLocal<>();
    private final ThreadLocal<Boolean> restoring = new ThreadLocal<>();
    private final Object pendingLock = new Object();
    private volatile boolean enabled, released, loggedFailure;
    private boolean refreshPending;

    public boolean enabled() { return enabled && !released; }

    /** Resolve a structural fingerprint before installing any plugin hooks.
     * A missing/changed obfuscated contract is unsupported, never a guessed name.
     */
    public Hooks resolve(ClassLoader loader) throws ReflectiveOperationException {
        if (loader == null || released) return null;
        Hooks cached = contracts.get(loader);
        if (cached != null) return cached;
        Hooks result = new Hooks(loader);
        contracts.put(loader, result);
        return result;
    }

    /** Called after the exact s1.r(s1) native primary-color event or initial bind.
     * K0.N is the same native primary-color flow used by its progress bar. The OEM
     * already does artwork parsing off the UI path; we only read its final value.
     */
    public void mediaChanged(Object section) {
        if (section == null || released || !section.getClass().getName().equals(MEDIA_SECTION)) return;
        View root = null;
        try {
            Hooks access = contracts.get(section.getClass().getClassLoader());
            if (access == null) return;
            Object rootValue = access.mediaRoot.get(section);
            if (!(rootValue instanceof View)) return;
            root = (View) rootValue;
            Object model = access.mediaModel.get(section);
            Object boxed = access.primaryFlow.get(model);
            Object flow = access.wrappedFlow.get(boxed);
            Object primary = access.flowValue.invoke(flow);
            if (primary == null || !access.primaryType.isInstance(primary)) { clearMedia(root); return; }
            int accent = access.primaryColor.getInt(primary);
            // A zero/transparent value is the OEM's absent-color state, not black.
            if ((accent >>> 24) == 0) { clearMedia(root); return; }
            MediaBinding binding = media.get(root);
            if (binding == null) {
                binding = new MediaBinding(root);
                media.put(root, binding);
                root.addOnAttachStateChangeListener(binding);
            }
            if (binding.color != accent || !binding.hasColor) {
                binding.color = accent; binding.hasColor = true;
            }
            adopt(binding);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (root != null) clearMedia(root);
            reportFailure(unavailable);
        }
    }

    public void mediaDisposed(Object section) {
        if (section == null || !section.getClass().getName().equals(MEDIA_SECTION)) return;
        Hooks access = contracts.get(section.getClass().getClassLoader());
        if (access == null) return;
        try {
            Object root = access.mediaRoot.get(section);
            if (root instanceof View) clearMedia((View) root);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { reportFailure(unavailable); }
    }

    private void clearMedia(View root) {
        MediaBinding binding = media.remove(root);
        if (binding == null) return;
        root.removeOnAttachStateChangeListener(binding);
        View card = binding.card.get();
        if (card != null) accents.remove(card);
        scheduleRefresh();
    }

    /** Native card model bind/attach: resolve ownership once when content moves.
     * Iterates known media roots, not arbitrary children or UI every drawing frame.
     */
    public void cardChanged(View card) {
        if (released || card == null || !card.getClass().getName().equals(CARD)) return;
        for (MediaBinding binding : mediaSnapshot()) adopt(binding);
    }

    /** The root wraps only BackgroundUtils.b(View,ViewRootManager), before proceed.
     * A returned scope is always closed in finally; completed() follows a successful
     * native proceed. Only CardBackgroundView inside CardView can own this scope.
     */
    public BackgroundScope beginBackground(Object helper, View host, Object manager) {
        if (released || host == null || helper == null || manager == null
                || !host.getClass().getName().equals(BACKGROUND)
                || !helper.getClass().getName().equals(BACKGROUND_HELPER)) return null;
        Hooks access = contracts.get(host.getClass().getClassLoader());
        if (access == null || !access.managerType.isInstance(manager)) return null;
        View card = cardOf(host);
        if (card == null) return null;
        BackgroundBinding binding = backgrounds.get(host);
        if (binding == null) {
            binding = new BackgroundBinding(host, card, access);
            backgrounds.put(host, binding);
        }
        binding.helper = new WeakReference<>(helper);
        binding.manager = new WeakReference<>(manager);
        // Even when disabled, record the real native owner for later enabling.
        Integer accent = accents.get(card);
        boolean color = enabled && !Boolean.TRUE.equals(restoring.get()) && accent != null;
        BackgroundScope scope = new BackgroundScope(binding, color ? accent : 0, color,
                activeBackground.get());
        activeBackground.set(scope);
        return scope;
    }

    /** Resources.getColor(I) and getColor(I,Theme) result hooks call this method.
     * The short-lived scope makes unrelated resource reads retain their own value.
     * IDs are resolved by entry name in this plugin's Resources, never hardcoded.
     */
    public int resourceColor(Resources resources, int id, int nativeColor) {
        BackgroundScope scope = activeBackground.get();
        if (scope == null || !scope.colorEnabled || released || !enabled
                || Boolean.TRUE.equals(restoring.get()) || resources == null) return nativeColor;
        BackgroundBinding binding = scope.binding;
        View host = binding.host.get();
        if (host == null || host.getResources() != resources) return nativeColor;
        String name;
        try { name = resources.getResourceEntryName(id); }
        catch (Resources.NotFoundException invalid) { return nativeColor; }
        if (!isBackgroundColor(name)) return nativeColor;
        int result = preserveAlpha(nativeColor, scope.accent);
        if (result != nativeColor) scope.changed = true;
        return result;
    }

    /** Capture the native animation alpha once in its existing setter hook. */
    public void nativeBlurAlpha(View host, float value) {
        if (host == null || !Float.isFinite(value)) return;
        BackgroundBinding binding = backgrounds.get(host);
        if (binding != null) binding.nativeAlpha = Math.max(0f, Math.min(1f, value));
    }

    public void configure(Bundle settings) {
        if (released) return;
        boolean next = settings != null && settings.getBoolean(ENABLED, false) && !SafetyMode.enabled(settings);
        if (enabled == next) return;
        enabled = next; loggedFailure = false;
        scheduleRefresh();
    }

    /** Restore through the original native initializer, including after safe mode. */
    public void release() {
        if (released) return;
        enabled = false; released = true;
        ArrayList<BackgroundBinding> owned = backgroundSnapshot();
        ArrayList<MediaBinding> roots = mediaSnapshot();
        Runnable restore = () -> {
            for (BackgroundBinding binding : owned) replay(binding, false);
            for (MediaBinding binding : roots) {
                View root = binding.root.get();
                if (root != null) root.removeOnAttachStateChangeListener(binding);
            }
            activeBackground.remove(); restoring.remove();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) restore.run(); else main.post(restore);
        backgrounds.clear(); accents.clear(); media.clear(); contracts.clear();
    }

    public void detach(View view) {
        if (view == null) return;
        if (view.getClass().getName().equals(BACKGROUND)) {
            BackgroundBinding binding = backgrounds.remove(view);
            if (binding != null) replay(binding, false);
        } else if (view.getClass().getName().equals(CARD)) {
            accents.remove(view);
            for (BackgroundBinding binding : backgroundSnapshot()) if (binding.card.get() == view) {
                View host = binding.host.get();
                if (host != null) backgrounds.remove(host);
                replay(binding, false);
            }
        }
    }

    private void adopt(MediaBinding binding) {
        View root = binding.root.get();
        if (root == null || !binding.hasColor) return;
        View card = cardOf(root);
        if (card == null) return; // Initial native parsing can precede CardView bind.
        View previousCard = binding.card.get();
        if (previousCard != null && previousCard != card) accents.remove(previousCard);
        binding.card = new WeakReference<>(card);
        Integer previous = accents.put(card, binding.color);
        if (enabled && (previous == null || previous.intValue() != binding.color)) scheduleRefresh();
    }

    private void scheduleRefresh() {
        synchronized (pendingLock) {
            if (refreshPending) return;
            refreshPending = true;
        }
        main.post(() -> {
            synchronized (pendingLock) { refreshPending = false; }
            if (released) return;
            for (BackgroundBinding binding : backgroundSnapshot()) replay(binding, enabled);
        });
    }

    private void replay(BackgroundBinding binding, boolean tint) {
        View host = binding.host.get(), card = binding.card.get();
        Object manager = binding.manager.get();
        if (host == null || card == null || manager == null) return;
        Integer accent = tint ? accents.get(card) : null;
        boolean apply = tint && !released && accent != null && cardOf(host) == card;
        if (apply && binding.applied && !binding.dirty && binding.appliedColor == accent.intValue()) return;
        if (!apply && !binding.applied) return;
        Boolean previousRestore = restoring.get();
        if (!apply) restoring.set(Boolean.TRUE);
        try {
            Object helper = binding.helper.get();
            if (helper == null) helper = binding.access.helperConstructor.newInstance(host.getContext(), 3);
            // Reflection follows the installed native b hook when tinting. During
            // restore, that hook may already be detached; the same native API still
            // recreates its original material without depending on module state.
            binding.access.background.invoke(helper, host, manager);
            if (!apply) { binding.applied = false; binding.dirty = false; }
            binding.access.blurAlpha.invoke(host, binding.nativeAlpha);
            host.invalidate();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            reportFailure(unavailable); // Keep native result and permit next bind retry.
        } finally {
            if (previousRestore == null) restoring.remove(); else restoring.set(previousRestore);
        }
    }

    private static View cardOf(View source) {
        View current = source;
        for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
            if (current.getClass().getName().equals(CARD)) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    static boolean isBackgroundColor(String name) {
        return "card_background_mix_color".equals(name)
                || "card_background_blend_color".equals(name)
                || "card_background_blend_color_old".equals(name)
                || "card_background_gradient_top_color".equals(name)
                || "card_background_gradient_middle_color".equals(name)
                || "card_background_gradient_bottom_color".equals(name);
    }

    static int preserveAlpha(int nativeColor, int accent) {
        return (nativeColor >>> 24) == 0 ? nativeColor : (nativeColor & 0xff000000) | (accent & 0x00ffffff);
    }

    private ArrayList<BackgroundBinding> backgroundSnapshot() {
        synchronized (backgrounds) { return new ArrayList<>(backgrounds.values()); }
    }

    private ArrayList<MediaBinding> mediaSnapshot() {
        synchronized (media) { return new ArrayList<>(media.values()); }
    }

    private void reportFailure(Throwable error) {
        if (loggedFailure) return;
        loggedFailure = true;
        Log.w(TAG, "Native expanded Live Alert accent unavailable; keeping OEM background", error);
    }

    public final class BackgroundScope implements AutoCloseable {
        private final BackgroundBinding binding;
        private final int accent;
        private final boolean colorEnabled;
        private final BackgroundScope previous;
        private boolean changed, completed, closed;

        private BackgroundScope(BackgroundBinding binding, int accent, boolean colorEnabled, BackgroundScope previous) {
            this.binding = binding; this.accent = accent; this.colorEnabled = colorEnabled; this.previous = previous;
        }

        public void completed() { completed = true; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (completed) {
                binding.applied = colorEnabled && changed;
                binding.appliedColor = accent;
                binding.dirty = false;
            } else if (colorEnabled && changed) {
                // OEM writes blur parameters before its later gradient resource reads.
                // A failure after a substituted read can leave a partial native material.
                // Retain restoration ownership and don't treat that color as cached.
                binding.applied = true;
                binding.dirty = true;
            }
            if (previous == null) activeBackground.remove(); else activeBackground.set(previous);
        }
    }

    private static final class BackgroundBinding {
        final WeakReference<View> host, card;
        final Hooks access;
        WeakReference<Object> helper = new WeakReference<>(null), manager = new WeakReference<>(null);
        volatile float nativeAlpha = 1f;
        volatile boolean applied, dirty;
        volatile int appliedColor;
        BackgroundBinding(View host, View card, Hooks access) {
            this.host = new WeakReference<>(host); this.card = new WeakReference<>(card); this.access = access;
        }
    }

    private final class MediaBinding implements View.OnAttachStateChangeListener {
        final WeakReference<View> root;
        WeakReference<View> card = new WeakReference<>(null);
        int color; boolean hasColor;
        MediaBinding(View root) { this.root = new WeakReference<>(root); }
        @Override public void onViewAttachedToWindow(View view) { if (!released) adopt(this); }
        @Override public void onViewDetachedFromWindow(View view) {
            View previousCard = card.get();
            if (previousCard != null) accents.remove(previousCard);
            card = new WeakReference<>(null);
            if (enabled) scheduleRefresh();
        }
    }

    /** Exact method handles exposed for the module's one hook registry. */
    public static final class Hooks {
        public final Class<?> cardType, backgroundType, mediaSectionType, managerType, primaryType;
        public final Method background, mediaPrimaryEvent, mediaDispose, cardBind, blurAlpha;
        public final Constructor<?> mediaConstructor;
        private final Constructor<?> helperConstructor;
        private final Field mediaRoot, mediaModel, primaryFlow, wrappedFlow, primaryColor;
        private final Method flowValue;

        private Hooks(ClassLoader loader) throws ReflectiveOperationException {
            cardType = loader.loadClass(CARD); backgroundType = loader.loadClass(BACKGROUND);
            mediaSectionType = loader.loadClass(MEDIA_SECTION); managerType = loader.loadClass(MANAGER);
            primaryType = loader.loadClass(PRIMARY_MODEL);
            Class<?> helperType = loader.loadClass(BACKGROUND_HELPER);
            Class<?> mediaModelType = loader.loadClass("com.oplus.systemui.plugins.shared.template.section.media.K0");
            Class<?> wrapperType = loader.loadClass("com.oplus.systemui.plugins.r8.jj");
            Class<?> flowType = loader.loadClass("kotlinx.coroutines.flow.p");
            if (!View.class.isAssignableFrom(cardType) || !View.class.isAssignableFrom(backgroundType))
                throw new NoSuchMethodException("Expanded Live Alert view contract changed");
            background = helperType.getDeclaredMethod("b", View.class, managerType);
            helperConstructor = helperType.getDeclaredConstructor(android.content.Context.class, Integer.TYPE);
            blurAlpha = backgroundType.getDeclaredMethod("setBlurRadiusFollowAlpha", Float.TYPE);
            mediaPrimaryEvent = mediaSectionType.getDeclaredMethod("r", mediaSectionType);
            if (!Modifier.isStatic(mediaPrimaryEvent.getModifiers()))
                throw new NoSuchMethodException("Native media primary-color event changed");
            mediaConstructor = mediaSectionType.getDeclaredConstructor(android.content.Context.class,
                    mediaModelType, android.view.ViewGroup.class,
                    loader.loadClass("com.oplus.systemui.plugins.shared.template.page.entity.t"));
            mediaDispose = mediaSectionType.getDeclaredMethod("dispose");
            cardBind = cardType.getDeclaredMethod("h", loader.loadClass("com.oplus.systemui.plugins.seedling.card.ui.model.o"));
            mediaRoot = checkedField(mediaSectionType, "l", android.view.ViewGroup.class);
            mediaModel = checkedField(mediaSectionType, "k", mediaModelType);
            primaryFlow = checkedField(mediaModelType, "N", wrapperType);
            wrappedFlow = checkedField(wrapperType, "b", loader.loadClass("com.oplus.systemui.plugins.r8.Lg"));
            primaryColor = checkedField(primaryType, "a", Integer.TYPE);
            flowValue = flowType.getDeclaredMethod("getValue");
            background.setAccessible(true); helperConstructor.setAccessible(true);
            blurAlpha.setAccessible(true); mediaPrimaryEvent.setAccessible(true);
            mediaConstructor.setAccessible(true); mediaDispose.setAccessible(true);
            cardBind.setAccessible(true); flowValue.setAccessible(true);
        }

        private static Field checkedField(Class<?> owner, String name, Class<?> expected) throws NoSuchFieldException {
            Field result = owner.getDeclaredField(name);
            if (result.getType() != expected) throw new NoSuchFieldException(owner.getName() + "." + name + " changed type");
            result.setAccessible(true); return result;
        }
    }
}
