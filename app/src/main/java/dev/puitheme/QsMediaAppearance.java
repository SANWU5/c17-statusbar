// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Album artwork inside the original media material; original cover and native optics remain intact. */
public final class QsMediaAppearance {
    public interface DrawAction { void draw(Canvas canvas) throws Throwable; }
    public interface ChildDrawAction { boolean draw(Canvas canvas) throws Throwable; }

    public static final String MASTER = "qs_media_cover_enabled";
    public static final String BACKGROUND = "qs_media_background_enabled";
    public static final String GLOW = "qs_media_glow_enabled";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, Integer> COLORS;
    static {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Map<String, Float> numbers = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        flags.put(MASTER, false);
        flags.put(BACKGROUND, true);
        flags.put(GLOW, true);
        for (String scene : new String[] { "light", "dark" }) {
            String key = prefix(scene);
            numbers.put(key + "_background_opacity", 70f);
            numbers.put(key + "_background_blur", 18f);
            numbers.put(key + "_glow_opacity", 60f);
            numbers.put(key + "_glow_radius", 12f);
            numbers.put(key + "_glow_spread", 2f);
            flags.put(key + "_glow_inverse_enabled", true);
            colors.put(key + "_glow_color", 0xff80bfff);
        }
        BOOLEANS = Collections.unmodifiableMap(flags);
        NUMBERS = Collections.unmodifiableMap(numbers);
        COLORS = Collections.unmodifiableMap(colors);
    }

    private static final String MEDIA = "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView";
    private static final String AUTO_BLUR = "com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable";
    private static final String MASK_BLUR = "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable";
    private static final String PLATFORM_BLUR = "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable";
    private static final String WALLPAPER_BLUR = "com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable";
    private static final String GLASS = "com.oplus.posteffect.drawable.BlendDrawable";
    private static final String MAIN = "half4 main(float2 position) {";
    private static final String SAMPLE = "half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);";
    private static final String COVER_CODE = "uniform shader c17MediaCover;\nuniform shader c17MediaPrevious;\n"
            + "uniform float c17MediaOpacity;\nuniform float c17MediaPreviousOpacity;\nuniform float c17MediaMix;\n";
    private static final int CACHE_EDGE = 384;
    private static final int COVER_EDGE = 256;
    private static final int MAX_BLUR_RADIUS = 64;
    static final long DEBOUNCE_MS = 1500L;
    static final long FADE_MS = 250L;
    private static final long FRAME_MS = 16L;
    interface Scheduler {
        long now();
        void after(Runnable task,long delay);
        void cancel(Runnable task);
        boolean animationsEnabled();
    }
    private static final class MainScheduler implements Scheduler {
        private Handler handler;
        private synchronized Handler handler() {
            if (handler == null) handler = new Handler(Looper.getMainLooper());
            return handler;
        }
        public long now() { return SystemClock.uptimeMillis(); }
        public void after(Runnable task,long delay) { handler().postDelayed(task,delay); }
        public void cancel(Runnable task) { synchronized (this) { if (handler != null) handler.removeCallbacks(task); } }
        public boolean animationsEnabled() { return ValueAnimator.areAnimatorsEnabled(); }
    }
    private static final Executor WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            task.run();
        }, "C17-media-artwork");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<View, State> states = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, WeakReference<View>> coverOwners = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, WeakReference<View>> backgrounds = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, WeakReference<View>> engines = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, WeakReference<Drawable>> engineSources = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Material> materials = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, FailedMaterial> failedMaterials = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, Members> members = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Boolean> knownOwners = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, BlurBinding> blurBindings = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Object> recording = new ThreadLocal<>();
    private final Set<String> traceStages = Collections.synchronizedSet(new HashSet<>());
    private final Executor worker;
    private final Scheduler scheduler;
    private volatile boolean enabled, backgroundEnabled = true, glowEnabled = true;
    private volatile Style light = Style.defaults(), dark = Style.defaults();
    private volatile boolean loggedError;
    private volatile boolean removed;

    public QsMediaAppearance() { this(WORKER,new MainScheduler()); }
    QsMediaAppearance(Executor worker) { this(worker,new MainScheduler()); }
    QsMediaAppearance(Executor worker,Scheduler scheduler) { this.worker = worker; this.scheduler = scheduler; }

    public static String prefix(String scene) { return "qs_media_" + scene; }
    public static boolean isMediaView(View view) { return QsTileAppearance.type(view, MEDIA); }

    public void configure(Bundle values) {
        if (removed) return;
        Style nextLight = readStyle(values, "light"), nextDark = readStyle(values, "dark");
        boolean nextBackground = values.getBoolean(BACKGROUND, true), nextGlow = values.getBoolean(GLOW, true);
        boolean nextEnabled = values.getBoolean(MASTER, false) && !SafetyMode.enabled(values);
        if (light.same(nextLight) && dark.same(nextDark) && backgroundEnabled == nextBackground
                && glowEnabled == nextGlow && enabled == nextEnabled) return;
        if (!light.same(nextLight)) light = nextLight;
        if (!dark.same(nextDark)) dark = nextDark;
        backgroundEnabled = nextBackground;
        glowEnabled = nextGlow;
        enabled = nextEnabled;
        loggedError = false;
        failedMaterials.clear();
        List<View> owners;
        synchronized (knownOwners) { owners = new ArrayList<>(knownOwners.keySet()); }
        for (View owner : owners) {
            Runnable update = () -> {
                if (removed) return;
                State state = states.get(owner);
                if (state != null) state.failureKey = null;
                if (!enabled) clearOwner(owner);
                else if (owner.isAttachedToWindow()) refresh(owner);
            };
            if (Looper.myLooper() == Looper.getMainLooper()) update.run();
            else owner.post(update);
        }
        if (!enabled) { materials.clear(); failedMaterials.clear(); blurBindings.clear(); }
    }

    /** Exact native events only; no resource fetch or recursive registration for an unchanged binding. */
    public void onNativeUpdate(View owner, String event) {
        if (removed || !isMediaView(owner)) return;
        knownOwners.put(owner, Boolean.TRUE);
        if (enabled && owner.isAttachedToWindow()) refresh(owner);
    }

    /** After native binding, drawable/theme/configuration updates and media size changes. */
    public void refresh(View owner) {
        if (removed || !enabled || !isMediaView(owner) || !owner.isAttachedToWindow()
                || Looper.myLooper() != Looper.getMainLooper()) return;
        knownOwners.put(owner, Boolean.TRUE);
        State state = states.get(owner);
        if (state == null) {
            state = new State(owner);
            states.put(owner, state);
            owner.addOnLayoutChangeListener(state.layout);
        }
        Object coverValue = nativeValue(owner, "coverImg", "getCoverImg");
        ImageView cover = coverValue instanceof ImageView ? (ImageView) coverValue : null;
        if (cover != null && !descendant(cover, owner)) cover = null;
        ImageView oldCover = state.cover.get();
        if (oldCover != cover) {
            if (oldCover != null) {
                oldCover.removeOnLayoutChangeListener(state.layout);
                coverOwners.remove(oldCover);
            }
            state.cover = new WeakReference<>(cover);
            if (cover != null) {
                coverOwners.put(cover, new WeakReference<>(owner));
                cover.addOnLayoutChangeListener(state.layout);
            }
        }
        Object bg = nativeValue(owner, "bg", "getBg");
        Object first = nativeValue(owner, "bgDrawable", "getBgDrawable");
        Object themed = nativeValue(owner, "themeBgDrawable", "getThemeBgDrawable");
        Object transition = nativeValue(owner, "transitionDrawable", "getTransitionDrawable");
        Drawable bodyBackground = bg instanceof View && descendant((View) bg, owner) ? ((View) bg).getBackground() : null;
        boolean materialChanged = !state.sameRoots(first, themed, transition, bodyBackground)
                || !state.sourcesStable();
        if (materialChanged) {
            dirtyOwner(owner);
            retireSources(owner);
            List<WeakReference<Drawable>> sources = new ArrayList<>();
            Map<Object, Boolean> seen = new IdentityHashMap<>();
            List<SourceNode> nodes = new ArrayList<>();
            register(owner, first, sources, nodes, seen, 0);
            register(owner, themed, sources, nodes, seen, 0);
            register(owner, transition, sources, nodes, seen, 0);
            register(owner, bodyBackground, sources, nodes, seen, 0);
            state.sources = Collections.unmodifiableList(sources);
            state.nodes = nodes;
            state.observeRoots(first, themed, transition, bodyBackground);
        }
        state.backgroundView = new WeakReference<>(bg instanceof View ? (View) bg : null);

        Style style = style(owner);
        boolean observedSame = state.matchesObserved(cover, bg instanceof View ? (View) bg : owner);
        boolean defaultCover = !Boolean.FALSE.equals(field(owner, "defaultCover"));
        float density = owner.getResources().getDisplayMetrics().density;
        float nativeCorner = QsTileAppearance.number(field(owner, "coverCornerRadius"), 0f);
        int configuration = owner.getResources().getConfiguration().hashCode();
        if (!state.radiusKnown || materialChanged || state.radiusConfiguration != configuration
                || state.radiusNative != nativeCorner || state.radiusDensity != density) {
            state.radius = Math.max(0f, QsTileAppearance.number(call(owner, "getCoverImgRadius"), nativeCorner));
            state.radiusKnown = true; state.radiusConfiguration = configuration;
            state.radiusNative = nativeCorner; state.radiusDensity = density;
        }
        float corner = state.radius;
        if (!materialChanged && state.style == style && observedSame && state.defaultCover == defaultCover
                && state.density == density && state.corner == corner && state.background == backgroundEnabled
                && state.glow == glowEnabled) return;
        state.style = style;
        state.defaultCover = defaultCover; state.density = density; state.corner = corner;
        state.background = backgroundEnabled; state.glow = glowEnabled;
        state.observe(cover, bg instanceof View ? (View) bg : owner);
        Drawable source = cover == null ? null : cover.getDrawable();
        boolean artwork = enabled && (backgroundEnabled || glowEnabled) && cover != null
                && cover.getVisibility() == View.VISIBLE && source != null
                && !defaultCover && supportedArtwork(source);
        if (ModuleDiagnostics.enabled()) trace("artwork:" + simpleType(source) + ":" + defaultCover,
                "Music artwork " + simpleType(source) + " default " + defaultCover + " supported " + artwork);
        int width = bg instanceof View ? ((View) bg).getWidth() : owner.getWidth();
        int height = bg instanceof View ? ((View) bg).getHeight() : owner.getHeight();
        if (!artwork || width <= 0 || height <= 0 || width > 16384 || height > 16384
                || cover.getWidth() <= 0 || cover.getHeight() <= 0) {
            scheduleUpdate(state,null,style);
        } else {
            Key key = new Key(source, width, height, cover.getWidth(), cover.getHeight(), density,
                    Math.max(0f, corner), style, backgroundEnabled, glowEnabled, state.coverRevision);
            if (state.failureKey != null && state.failureKey.same(key)) return;
            scheduleUpdate(state,key,style);
        }
    }

    /** ImageView artwork may arrive asynchronously after bindCoverImg returns. */
    public void onCoverChanged(View cover) {
        if (removed || !enabled) return;
        WeakReference<View> ref = coverOwners.get(cover);
        View owner = ref == null ? null : ref.get();
        if (owner != null && Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> child = new WeakReference<>(cover);
            owner.post(() -> { View target = child.get(); if (target != null) onCoverChanged(target); });
            return;
        }
        if (owner != null && nativeValue(owner, "coverImg", "getCoverImg") == cover) {
            State state = states.get(owner);
            if (state != null && state.matchesObserved((ImageView) cover, state.backgroundView.get())) return;
            if (state != null) state.failureKey = null;
            refresh(owner);
        }
    }

    public void detach(View owner) {
        knownOwners.remove(owner);
        clearOwner(owner);
    }

    private void clearOwner(View owner) {
        State state = states.remove(owner);
        if (state != null) {
            owner.removeOnLayoutChangeListener(state.layout);
            ImageView cover = state.cover.get();
            if (cover != null) {
                cover.removeOnLayoutChangeListener(state.layout);
                coverOwners.remove(cover);
                if (cover.getParent() instanceof View) ((View) cover.getParent()).invalidate();
            }
            cancelPreparation(owner, state);
        }
        dirtyOwner(owner);
        retireSources(owner);
    }

    private void retireSources(View owner) {
        synchronized (engines) {
            engines.entrySet().removeIf(item -> {
                View view = item.getValue().get();
                if (view == null || view == owner) {
                    Object materialOwner = field(item.getKey(), "drawableShader");
                    materials.remove(materialOwner); failedMaterials.remove(materialOwner);
                    engineSources.remove(item.getKey()); return true;
                }
                return false;
            });
        }
        synchronized (backgrounds) {
            backgrounds.entrySet().removeIf(item -> item.getValue().get() == null || item.getValue().get() == owner);
        }
        synchronized (blurBindings) {
            blurBindings.entrySet().removeIf(item -> item.getValue().owner.get() == null || item.getValue().owner.get() == owner);
        }
    }

    /** Permanent uninstall gate: late native events and worker completions cannot acquire ownership. */
    public void releaseRuntime() {
        removed = true; enabled = false;
        List<View> owners;
        synchronized (states) { owners = new ArrayList<>(states.keySet()); }
        for (View owner : owners) clearOwner(owner);
        knownOwners.clear(); blurBindings.clear(); backgrounds.clear(); engines.clear(); engineSources.clear();
        materials.clear(); failedMaterials.clear(); members.clear();
        traceStages.clear();
    }

    /** Scoped wrapper for inherited View.draw(Canvas); native background and children draw once. */
    public void drawMedia(View owner, Canvas canvas, DrawAction nativeDraw) throws Throwable {
        if (!removed && enabled && isMediaView(owner)) {
            State state = states.get(owner);
            if (state == null) refresh(owner);
            else {
                ImageView cover = state.cover.get();
                View bg = state.backgroundView.get();
                Style style = style(owner);
                // No per-frame bitmap processing, Key allocation or recursive background registration.
                if (state.style != style || !state.matchesObserved(cover, bg == null ? owner : bg)) refresh(owner);
            }
        }
        nativeDraw.draw(canvas);
    }

    /** The media AutoBlurDrawable lazily creates its native platform engine. */
    public void drawBlur(Drawable drawable, Canvas canvas, DrawAction nativeDraw) throws Throwable {
        // Native getBlurDrawable itself supplies the result through onNativeBlurResult.
        // Never call the provider a second time from the paint path.
        nativeDraw.draw(canvas);
    }

    public void onNativeBlurResult(Object proxy, Object actual) {
        if (removed || !enabled) return;
        BlurBinding binding = blurBindings.get(proxy);
        View owner = binding == null ? null : binding.owner.get();
        Drawable source = binding == null ? null : binding.source.get();
        State state = owner == null ? null : states.get(owner);
        if (!currentSource(state, source)) return;
        Object found = QsTileAppearance.type(actual, PLATFORM_BLUR) ? field(actual, "blurDrawable")
                : QsTileAppearance.type(actual, WALLPAPER_BLUR) ? field(actual, "blendDrawable")
                : QsTileAppearance.type(actual, GLASS) ? actual : null;
        Drawable engine = found instanceof Drawable && QsTileAppearance.type(found, GLASS) ? (Drawable) found : null;
        if (ModuleDiagnostics.enabled() && (!binding.tracedActual || binding.engine.get() != engine)) {
            binding.tracedActual = actual != null;
            trace("native:" + simpleType(actual) + ":" + simpleType(engine),
                    "Music native material " + simpleType(actual) + " engine " + simpleType(engine));
        }
        if (binding.engine.get() == engine) return;
        Drawable previous = binding.engine.get();
        if (previous != null) {
            dirtyGlass(previous); engines.remove(previous); engineSources.remove(previous);
            Object shaderOwner = field(previous, "drawableShader"); materials.remove(shaderOwner); failedMaterials.remove(shaderOwner);
        }
        binding.engine = new WeakReference<>(engine);
        if (engine != null) {
            engines.put(engine, new WeakReference<>(owner)); engineSources.put(engine, new WeakReference<>(source));
            dirtyGlass(engine);
        }
    }

    /** After exact native material setters; identical native content leaves all uploads untouched. */
    public void onNativeMaterialUpdate(Object shaderOwner) {
        if (removed || !enabled || recording.get() != null) return;
        Material material = materials.get(shaderOwner);
        FailedMaterial failed = failedMaterials.get(shaderOwner);
        if (material != null || failed != null) {
            Object signature = nativeSignature(shaderOwner, 0, new IdentityHashMap<>());
            if (failed != null && !signature.equals(failed.signature)) {
                failedMaterials.remove(shaderOwner);
                if (ModuleDiagnostics.enabled()) trace("retry:" + simpleType(shaderOwner), "Music material retry after native change");
            }
            if (material != null && !signature.equals(material.nativeSignature)) {
                material.nativeSignature = signature;
                material.nativeDirty = true;
            }
        }
    }

    public boolean tracksMaterial(Object shaderOwner) {
        return !removed && enabled && recording.get() == null
                && (materials.containsKey(shaderOwner) || failedMaterials.containsKey(shaderOwner));
    }

    public void onNativeMaterialUpdate(Object shaderOwner, String event, Object[] arguments) {
        if (!tracksMaterial(shaderOwner)) return;
        Material material = materials.get(shaderOwner);
        FailedMaterial failed = failedMaterials.get(shaderOwner);
        Map<String,Object> events = failed != null ? failed.events : material == null ? null : material.events;
        if (events == null) return;
        Object argumentsNow = nativeSignature(arguments, 0, new IdentityHashMap<>());
        if (argumentsNow.equals(events.get(event))) return;
        events.put(event, argumentsNow);
        onNativeMaterialUpdate(shaderOwner);
    }

    /** Exact BlendDrawable.onDrawContent(Canvas), before the native RenderNode is recorded. */
    public void drawGlassContent(Drawable engine, Canvas canvas, DrawAction nativeDraw) throws Throwable {
        if (!enabled) { nativeDraw.draw(canvas); return; }
        WeakReference<View> ref = engines.get(engine);
        View owner = ref == null ? null : ref.get();
        State state = owner == null ? null : states.get(owner);
        Prepared prepared = state == null ? null : state.prepared;
        Prepared previous = state == null ? null : state.previous;
        WeakReference<Drawable> source = engineSources.get(engine);
        if (!enabled || (!background(prepared) && !background(previous)) || source == null
                || !currentSource(state, source.get()) || !QsTileAppearance.type(engine, GLASS)) {
            nativeDraw.draw(canvas);
            return;
        }
        Object lock = field(engine, "dataLock");
        if (lock == null || recording.get() != null) { nativeDraw.draw(canvas); return; }
        synchronized (lock) {
            Swap swap = null;
            recording.set(engine);
            try {
                try { swap = prepareMaterial(engine, prepared, previous, state.fadeMix); }
                catch (ReflectiveOperationException | RuntimeException unavailable) {
                    Object shaderOwner = field(engine, "drawableShader");
                    Object nativeShader = field(shaderOwner, "shader");
                    if (shaderOwner != null && nativeShader instanceof RuntimeShader) {
                        failedMaterials.put(shaderOwner, new FailedMaterial((RuntimeShader) nativeShader,
                                nativeSignature(shaderOwner, 0, new IdentityHashMap<>())));
                        if (ModuleDiagnostics.enabled()) trace("materialFailure:" + simpleType(unavailable),
                                "Music material failed " + simpleType(unavailable));
                    }
                    error(unavailable);
                }
                nativeDraw.draw(canvas);
            } finally {
                if (swap != null) swap.restore();
                recording.remove();
            }
        }
    }

    /** Before the exact cover child's native drawChild; no overlay is added to ordinary QS children. */
    public boolean drawCover(View parent, View child, Canvas canvas, ChildDrawAction nativeDraw) throws Throwable {
        if (!enabled) return nativeDraw.draw(canvas);
        WeakReference<View> ref = coverOwners.get(child);
        if (ref == null) return nativeDraw.draw(canvas);
        View owner = ref == null ? null : ref.get();
        State state = owner == null ? null : states.get(owner);
        Prepared prepared = state == null ? null : state.prepared;
        Prepared previous = state == null ? null : state.previous;
        if (enabled && (glow(prepared) || glow(previous))
                && child.getParent() == parent && state.cover.get() == child
                && descendant(parent, owner) && child.getVisibility() == View.VISIBLE && child.getAlpha() > 0f
                && Looper.myLooper() == Looper.getMainLooper()) {
            int saved = canvas.save();
            try {
                // drawChild receives parent coordinates, before the child's matrix and clipping are applied.
                canvas.translate(child.getLeft() - parent.getScrollX(), child.getTop() - parent.getScrollY());
                canvas.concat(child.getMatrix());
                drawGlow(canvas,child,previous,1f-state.fadeMix);
                drawGlow(canvas,child,prepared,state.fadeMix);
            } catch (RuntimeException unavailable) { error(unavailable); }
            finally { canvas.restoreToCount(saved); }
        }
        return nativeDraw.draw(canvas);
    }

    private static boolean background(Prepared value) {
        return value != null && value.key.background && value.background != null && value.style.backgroundOpacity > 0f;
    }
    private static boolean glow(Prepared value) {
        return value != null && value.key.glow && value.glow != null && value.style.glowOpacity > 0f;
    }
    private static void drawGlow(Canvas canvas,View child,Prepared prepared,float fraction) {
        if (!glow(prepared) || fraction <= 0f) return;
        Paint paint = prepared.glowPaint;
        paint.setAlpha(Math.round(255f * prepared.style.glowOpacity / 100f
                * clamp(child.getAlpha(),0f,1f) * fraction));
        float scaleX = child.getWidth() / (float) prepared.glowInnerWidth;
        float scaleY = child.getHeight() / (float) prepared.glowInnerHeight;
        RectF dst = prepared.glowDestination;
        dst.set(-prepared.glowPadding * scaleX,-prepared.glowPadding * scaleY,
                (prepared.glow.getWidth()-prepared.glowPadding)*scaleX,
                (prepared.glow.getHeight()-prepared.glowPadding)*scaleY);
        canvas.drawBitmap(prepared.glow,null,dst,paint);
    }

    private void register(View owner, Object value, List<WeakReference<Drawable>> result, List<SourceNode> nodes,
            Map<Object, Boolean> seen, int depth) {
        if (!(value instanceof Drawable) || depth > 12 || seen.put(value, true) != null) return;
        Drawable drawable = (Drawable) value;
        backgrounds.put(drawable, new WeakReference<>(owner));
        result.add(new WeakReference<>(drawable));
        nodes.add(new SourceNode(drawable));
        if (QsTileAppearance.type(drawable, AUTO_BLUR) || QsTileAppearance.type(drawable, MASK_BLUR)) {
            Object proxy = nativeValue(drawable, "viewBlurProxy", "getViewBlurProxy");
            if (proxy != null) blurBindings.put(proxy, new BlurBinding(owner, drawable));
            if (ModuleDiagnostics.enabled()) trace("source:" + simpleType(drawable), "Music source " + simpleType(drawable) + " proxy " + simpleType(proxy));
        }
        // The media-specific MultiLightDrawable is deliberately not traversed or recolored.
        for (Object child : new Object[] { call(drawable, "getDrawable"), drawable.getCurrent(),
                field(drawable, "backgroundDrawable"), field(drawable, "foregroundDrawable") })
            if (child != drawable) register(owner, child, result, nodes, seen, depth + 1);
    }

    private static boolean currentSource(State state, Drawable drawable) {
        if (state == null || drawable == null) return false;
        for (int i = 0; i < state.sources.size(); i++) if (state.sources.get(i).get() == drawable) return true;
        return false;
    }

    private static boolean descendant(View child, View owner) {
        for (int i = 0; child != null && i < 16; i++) {
            if (child == owner) return true;
            ViewParent parent = child.getParent();
            child = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private Style style(View owner) {
        return (owner.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES ? dark : light;
    }

    private static Style readStyle(Bundle values, String scene) {
        String key = prefix(scene);
        return new Style(value(values, key + "_background_opacity", 70f, 100f),
                value(values, key + "_background_blur", 18f, 128f),
                value(values, key + "_glow_opacity", 60f, 100f),
                value(values, key + "_glow_radius", 12f, 96f),
                value(values, key + "_glow_spread", 2f, 64f),
                values.getBoolean(key + "_glow_inverse_enabled", true),
                QsTileAppearance.color(values.get(key + "_glow_color"), 0xff80bfff));
    }

    private static float value(Bundle values, String key, float fallback, float max) {
        return clamp(QsTileAppearance.number(values.get(key), fallback), 0f, max);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private void invalidate(View owner, ImageView cover) {
        dirtyOwner(owner);
        ViewParent parent = cover == null ? null : cover.getParent();
        if (parent instanceof View && parent != owner) ((View) parent).invalidate();
        owner.invalidate();
    }

    private void scheduleUpdate(State state,Key key,Style style) {
        boolean sameKey = key == null ? state.desiredKey == null : state.desiredKey != null && state.desiredKey.same(key);
        if (state.desiredSet && sameKey && state.desiredStyle.same(style)) return;
        scheduler.cancel(state.debounce);
        state.desiredSet = true;
        state.desiredKey = key;
        state.desiredStyle = style;
        state.queuedFade = null;
        state.hasQueuedFade = false;
        state.preparing = true;
        state.debouncing = true;
        state.deadline = scheduler.now() + DEBOUNCE_MS;
        synchronized (state) { state.generation++; state.pending = null; }
        // Keep the last complete material throughout native null/rebind bursts and worker delays.
        scheduler.after(state.debounce,DEBOUNCE_MS);
    }

    private void beginQuiet(State state) {
        View owner = state.owner.get();
        if (removed || !enabled || owner == null || states.get(owner) != state || !state.debouncing) return;
        if (!owner.isAttachedToWindow()) { clearOwner(owner); return; }
        long remaining = state.deadline - scheduler.now();
        if (remaining > 0) { scheduler.after(state.debounce,remaining); return; }
        long generation = state.generation;
        state.debouncing = false;
        // Verify the last real native values once at the deadline, not on every animation frame.
        refresh(owner);
        if (generation != state.generation || removed || !enabled || states.get(owner) != state) return;
        Key key = state.desiredKey;
        Style style = state.desiredStyle;
        if (key == null) { state.preparing = false; commitPrepared(state,null); return; }
        Prepared previous = state.prepared;
        if (previous != null && previous.key.same(key)) {
            state.preparing = false;
            if (!previous.style.same(style)) {
                commitPrepared(state,previous.withStyle(style));
            }
            return;
        }
        ImageView cover = state.cover.get();
        Drawable source = cover == null ? null : cover.getDrawable();
        if (!supportedArtwork(source) || key.artwork.get() != ((BitmapDrawable) source).getBitmap()
                || key.generation != generationOf(source)) { refresh(owner); return; }
        try {
            queue(state,new Work(key,style,snapshot(source),generation));
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            state.preparing = false;
            state.failureKey = key;
            error(unavailable);
        }
    }

    private void cancelPreparation(View owner, State state) {
        boolean changed = state.prepared != null || state.previous != null || state.desiredKey != null;
        scheduler.cancel(state.debounce);
        scheduler.cancel(state.fade);
        synchronized (state) { state.generation++; state.pending = null; }
        state.prepared = null;
        state.previous = null;
        state.queuedFade = null;
        state.hasQueuedFade = false;
        state.fading = false;
        state.fadeMix = 1f;
        state.desiredKey = null;
        state.failureKey = null;
        state.preparing = false;
        state.debouncing = false;
        state.desiredSet = false;
        state.desiredStyle = null;
        if (changed) invalidate(owner, state.cover.get());
    }

    private void commitPrepared(State state,Prepared next) {
        View owner = state.owner.get();
        if (removed || !enabled || owner == null || states.get(owner) != state) return;
        boolean animate = scheduler.animationsEnabled();
        if (state.fading && animate && scheduler.now()-state.fadeStarted < FADE_MS) {
            // Keep the current visible blend continuous; an exceptional overlapping completion
            // coalesces to one next target instead of allocating a screenshot of the blended frame.
            state.queuedFade = next;
            state.hasQueuedFade = true;
            return;
        }
        scheduler.cancel(state.fade);
        boolean wasFading = state.fading;
        Prepared previous = state.prepared;
        state.prepared = next;
        boolean changed = !sameVisual(previous,next);
        state.previous = changed && animate ? previous : null;
        state.fading = changed && animate;
        state.fadeMix = state.fading ? 0f : 1f;
        state.fadeStarted = scheduler.now();
        state.queuedFade = null;
        state.hasQueuedFade = false;
        if (!state.fading && !background(next)) releaseMaterials(owner);
        if (changed || wasFading) invalidate(owner,state.cover.get());
        if (state.fading) scheduler.after(state.fade,FRAME_MS);
    }

    private static boolean sameVisual(Prepared first,Prepared second) {
        if (first == second) return true;
        boolean backgrounds = background(first) == background(second)
                && (!background(first) || (first.background == second.background
                && first.style.backgroundOpacity == second.style.backgroundOpacity));
        boolean glows = glow(first) == glow(second)
                && (!glow(first) || (first.glow == second.glow && first.style.glowOpacity == second.style.glowOpacity));
        return backgrounds && glows;
    }

    private void animateFrame(State state) {
        View owner = state.owner.get();
        if (removed || !enabled || owner == null || states.get(owner) != state || !state.fading) return;
        if (!owner.isAttachedToWindow()) { clearOwner(owner); return; }
        long elapsed = Math.max(0L,scheduler.now()-state.fadeStarted);
        float time = scheduler.animationsEnabled() ? Math.min(1f,elapsed/(float)FADE_MS) : 1f;
        state.fadeMix = time*time*(3f-2f*time);
        if (time >= 1f) {
            state.fading = false;
            state.previous = null;
            if (!background(state.prepared)) releaseMaterials(owner);
            if (state.hasQueuedFade) {
                Prepared next = state.queuedFade;
                state.hasQueuedFade = false;
                state.queuedFade = null;
                invalidate(owner,state.cover.get());
                commitPrepared(state,next);
                return;
            }
        }
        invalidate(owner,state.cover.get());
        if (state.fading) scheduler.after(state.fade,Math.min(FRAME_MS,FADE_MS-elapsed));
    }

    private void releaseMaterials(View owner) {
        synchronized (engines) {
            for (Map.Entry<Drawable,WeakReference<View>> entry : engines.entrySet()) {
                if (entry.getValue().get() != owner) continue;
                Object shaderOwner = field(entry.getKey(),"drawableShader");
                materials.remove(shaderOwner);
                failedMaterials.remove(shaderOwner);
            }
        }
    }

    private void queue(State state, Work work) {
        synchronized (state) {
            state.pending = work;
            if (state.queued) return;
            state.queued = true;
        }
        // At most one queued runner per card; rapid config/artwork updates replace only its pending work.
        worker.execute(() -> runQueue(state));
    }

    private void runQueue(State state) {
        for (;;) {
            Work work;
            synchronized (state) {
                work = state.pending;
                state.pending = null;
                if (work == null) { state.queued = false; return; }
                if (work.generation != state.generation) continue;
            }
            Prepared result = null;
            Throwable failure = null;
            try { result = prepare(work.artwork, work.key, work.style,
                    () -> removed || !enabled || state.generation != work.generation); }
            catch (CancellationException superseded) { continue; }
            catch (RuntimeException | OutOfMemoryError unavailable) { failure = unavailable; }
            View owner = state.owner.get();
            if (owner == null) continue;
            final Prepared finished = result;
            final Throwable error = failure;
            owner.post(() -> {
                // A closed/detached/rebound card never receives an older generation's effects.
                if (removed || states.get(owner) != state || !enabled || state.generation != work.generation
                        || state.desiredKey == null || !state.desiredKey.same(work.key)) return;
                state.preparing = false;
                if (error != null) {
                    state.failureKey = work.key;
                    error(error);
                    return;
                } else {
                    state.failureKey = null;
                    commitPrepared(state,finished.style == state.style ? finished : finished.withStyle(state.style));
                    if (ModuleDiagnostics.enabled()) trace("prepared:" + work.generation, "Music artwork preparation ready");
                }
            });
        }
    }

    private static final class Work {
        final Key key;
        final Style style;
        final Bitmap artwork;
        final long generation;
        Work(Key key, Style style, Bitmap artwork, long generation) {
            this.key = key; this.style = style; this.artwork = artwork; this.generation = generation;
        }
    }

    private final class State {
        final WeakReference<View> owner;
        WeakReference<ImageView> cover = new WeakReference<>(null);
        WeakReference<View> backgroundView = new WeakReference<>(null);
        volatile Prepared prepared;
        volatile Prepared previous;
        volatile float fadeMix = 1f;
        boolean fading,hasQueuedFade;
        long fadeStarted;
        Prepared queuedFade;
        volatile Style style;
        volatile Key desiredKey;
        Style desiredStyle;
        boolean desiredSet,debouncing;
        long deadline;
        final Runnable debounce;
        final Runnable fade;
        volatile boolean preparing;
        Key failureKey;
        volatile long generation;
        long coverRevision;
        Work pending;
        boolean queued;
        WeakReference<Drawable> observedSource = new WeakReference<>(null), observedCurrent = new WeakReference<>(null);
        int observedGeneration, observedLevel, observedState, observedVisibility, observedWidth, observedHeight;
        int observedCoverWidth, observedCoverHeight;
        boolean defaultCover, background, glow;
        float density, corner;
        float radius, radiusNative, radiusDensity;
        boolean radiusKnown;
        int radiusConfiguration;
        final List<WeakReference<Object>> roots = new ArrayList<>();
        List<SourceNode> nodes = Collections.emptyList();
        volatile List<WeakReference<Drawable>> sources = Collections.emptyList();
        final View.OnLayoutChangeListener layout;
        State(View owner) {
            this.owner = new WeakReference<>(owner);
            debounce = () -> beginQuiet(this);
            fade = () -> animateFrame(this);
            WeakReference<View> ref = new WeakReference<>(owner);
            layout = (view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                View target = ref.get();
                if (target != null && (r - l != oldR - oldL || b - t != oldB - oldT)) refresh(target);
            };
        }
        void observe(ImageView cover, View background) {
            Drawable source = cover == null ? null : cover.getDrawable();
            observedSource = new WeakReference<>(source);
            observedCurrent = new WeakReference<>(source == null ? null : source.getCurrent());
            observedGeneration = generationOf(source);
            observedLevel = source == null ? 0 : source.getLevel();
            observedState = source == null ? 0 : Arrays.hashCode(source.getState());
            observedVisibility = cover == null ? View.GONE : cover.getVisibility();
            observedCoverWidth = cover == null ? 0 : cover.getWidth();
            observedCoverHeight = cover == null ? 0 : cover.getHeight();
            observedWidth = background.getWidth(); observedHeight = background.getHeight();
        }
        boolean matchesObserved(ImageView cover, View background) {
            if (background == null) background = owner.get();
            if (background == null) return false;
            Drawable source = cover == null ? null : cover.getDrawable();
            return observedSource.get() == source && observedCurrent.get() == (source == null ? null : source.getCurrent())
                    && observedGeneration == generationOf(source) && observedLevel == (source == null ? 0 : source.getLevel())
                    && observedState == (source == null ? 0 : Arrays.hashCode(source.getState()))
                    && observedVisibility == (cover == null ? View.GONE : cover.getVisibility())
                    && observedCoverWidth == (cover == null ? 0 : cover.getWidth())
                    && observedCoverHeight == (cover == null ? 0 : cover.getHeight())
                    && observedWidth == background.getWidth() && observedHeight == background.getHeight();
        }
        boolean sameRoots(Object first, Object theme, Object transition, Object body) {
            return roots.size() == 4 && roots.get(0).get() == first && roots.get(1).get() == theme
                    && roots.get(2).get() == transition && roots.get(3).get() == body;
        }
        void observeRoots(Object... values) {
            roots.clear(); for (Object value : values) roots.add(new WeakReference<>(value));
        }
        boolean sourcesStable() {
            for (int i = 0; i < nodes.size(); i++) if (!nodes.get(i).same()) return false;
            return true;
        }
    }

    private final class SourceNode {
        final WeakReference<Drawable> source, current;
        final Field[] links;
        final WeakReference<?>[] children;
        SourceNode(Drawable value) {
            source = new WeakReference<>(value); current = new WeakReference<>(value.getCurrent());
            List<Field> fields = new ArrayList<>();
            for (Field field : access(value).allFields()) if (Drawable.class.isAssignableFrom(field.getType())) fields.add(field);
            links = fields.toArray(new Field[0]); children = new WeakReference<?>[links.length];
            for (int i = 0; i < links.length; i++) children[i] = new WeakReference<>(read(links[i], value));
        }
        boolean same() {
            Drawable value = source.get(); if (value == null || current.get() != value.getCurrent()) return false;
            for (int i = 0; i < links.length; i++) if (children[i].get() != read(links[i], value)) return false;
            return true;
        }
    }

    private static final class BlurBinding {
        final WeakReference<View> owner;
        final WeakReference<Drawable> source;
        WeakReference<Drawable> engine = new WeakReference<>(null);
        boolean tracedActual;
        BlurBinding(View owner, Drawable source) { this.owner = new WeakReference<>(owner); this.source = new WeakReference<>(source); }
    }

    private static final class Style {
        final float backgroundOpacity, backgroundBlur, glowOpacity, glowRadius, glowSpread;
        final boolean inverse;
        final int glowColor;
        Style(float opacity, float blur, float glowOpacity, float radius, float spread, boolean inverse, int glowColor) {
            backgroundOpacity = opacity; backgroundBlur = blur; this.glowOpacity = glowOpacity;
            glowRadius = radius; glowSpread = spread;
            this.inverse = inverse; this.glowColor = glowColor;
        }
        static Style defaults() { return new Style(70f, 18f, 60f, 12f, 2f, true, 0xff80bfff); }
        boolean same(Style other) {
            return backgroundOpacity == other.backgroundOpacity && backgroundBlur == other.backgroundBlur
                    && glowOpacity == other.glowOpacity && glowRadius == other.glowRadius
                    && glowSpread == other.glowSpread && inverse == other.inverse && glowColor == other.glowColor;
        }
    }

    private static boolean supportedArtwork(Drawable source) {
        // Animated/opaque custom drawables have no dependable content revision. Keep their native
        // material instead of repeatedly snapshotting an animation or displaying stale artwork.
        Bitmap bitmap = source instanceof BitmapDrawable ? ((BitmapDrawable) source).getBitmap() : null;
        return bitmap != null && !bitmap.isRecycled();
    }

    private static final class Key {
        final WeakReference<Drawable> source, current;
        final WeakReference<Bitmap> artwork;
        final int width, height, coverWidth, coverHeight, generation, level, state;
        final long revision;
        final float density, corner, blur, radius, spread;
        final boolean background, glow, inverse;
        final int glowColor;
        Key(Drawable drawable, int width, int height, int coverWidth, int coverHeight,
                float density, float corner, Style style, boolean background, boolean glow, long revision) {
            source = new WeakReference<>(drawable); current = new WeakReference<>(drawable.getCurrent());
            this.width = width; this.height = height; this.coverWidth = coverWidth; this.coverHeight = coverHeight;
            this.density = density; this.corner = corner; blur = style.backgroundBlur;
            radius = style.glowRadius; spread = style.glowSpread; this.background = background; this.glow = glow;
            inverse = style.inverse; glowColor = inverse ? 0 : style.glowColor;
            this.revision = revision;
            Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
            artwork = new WeakReference<>(bitmap);
            generation = bitmap == null || bitmap.isRecycled() ? 0 : bitmap.getGenerationId();
            level = drawable.getLevel(); state = Arrays.hashCode(drawable.getState());
        }
        boolean same(Key other) {
            return artwork.get() != null && artwork.get() == other.artwork.get()
                    && width == other.width && height == other.height && coverWidth == other.coverWidth
                    && coverHeight == other.coverHeight && generation == other.generation && level == other.level
                    && state == other.state && density == other.density && corner == other.corner
                    && blur == other.blur && radius == other.radius && spread == other.spread
                    && background == other.background && glow == other.glow && inverse == other.inverse
                    && glowColor == other.glowColor && revision == other.revision;
        }
    }

    private static int generationOf(Drawable drawable) {
        Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
        return bitmap == null || bitmap.isRecycled() ? 0 : bitmap.getGenerationId();
    }

    private static final class Prepared {
        final Key key;
        final Style style;
        final Bitmap background, glow;
        final int glowInnerWidth, glowInnerHeight, glowPadding;
        final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF glowDestination = new RectF();
        Prepared(Key key, Style style, Bitmap background, Bitmap glow, int width, int height, int padding) {
            this.key = key; this.style = style; this.background = background; this.glow = glow;
            glowInnerWidth = width; glowInnerHeight = height; glowPadding = padding;
        }
        Prepared withStyle(Style style) {
            return new Prepared(key, style, background, glow, glowInnerWidth, glowInnerHeight, glowPadding);
        }
    }

    private static Prepared prepare(Bitmap artwork, Key key, Style style, BooleanSupplier cancelled) {
        checkpoint(cancelled);
        Bitmap background = null, glow = null;
        int innerWidth = 0, innerHeight = 0, padding = 0;
        if (key.background) {
            int[] size = fit(key.width, key.height, CACHE_EDGE);
            background = crop(artwork, size[0], size[1]);
            int radius = radius(style.backgroundBlur * key.density * size[0] / key.width);
            if (radius > 0) background = blur(background, radius, true, cancelled);
        }
        if (key.glow) {
            checkpoint(cancelled);
            int[] size = fit(key.coverWidth, key.coverHeight, COVER_EDGE);
            innerWidth = size[0]; innerHeight = size[1];
            float scale = innerWidth / (float) key.coverWidth;
            int radius = radius(style.glowRadius * key.density * scale);
            int spread = Math.max(0, Math.min(64, Math.round(style.glowSpread * key.density * scale)));
            padding = radius * 3 + spread + 2;
            Bitmap inverse = crop(artwork, innerWidth, innerHeight);
            int[] pixels = new int[innerWidth * innerHeight];
            inverse.getPixels(pixels, 0, innerWidth, 0, 0, innerWidth, innerHeight);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = style.inverse ? (pixels[i] & 0xff000000) | (~pixels[i] & 0x00ffffff)
                        : ((pixels[i] >>> 24) * (style.glowColor >>> 24) / 255 << 24) | (style.glowColor & 0x00ffffff);
            }
            inverse.setPixels(pixels, 0, innerWidth, 0, 0, innerWidth, innerHeight);
            glow = Bitmap.createBitmap(innerWidth + 2 * padding, innerHeight + 2 * padding, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(glow);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            float corner = Math.min(key.corner * scale, Math.min(innerWidth, innerHeight) / 2f);
            RectF rect = new RectF(padding - spread, padding - spread,
                    padding + innerWidth + spread, padding + innerHeight + spread);
            Path path = new Path();
            path.addRoundRect(rect, corner + spread, corner + spread, Path.Direction.CW);
            canvas.clipPath(path);
            canvas.drawBitmap(inverse, null, rect, paint);
            if (radius > 0) glow = blur(glow, radius, false, cancelled);
        }
        return new Prepared(key, style, background, glow, innerWidth, innerHeight, padding);
    }

    private static int radius(float sigma) { return Math.max(0, Math.min(MAX_BLUR_RADIUS, Math.round(sigma / 1.732f))); }

    private static int[] fit(int width, int height, int edge) {
        float scale = Math.min(1f, edge / (float) Math.max(width, height));
        return new int[] { Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)) };
    }

    private static Bitmap snapshot(Drawable source) {
        if (source instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) source).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) throw new IllegalArgumentException("Media artwork unavailable");
            int[] size = fit(bitmap.getWidth(), bitmap.getHeight(), CACHE_EDGE);
            // A hardware album bitmap cannot be drawn onto the software processing canvas directly.
            // Scale through the public bitmap API first; never copy a full-resolution hardware album.
            Bitmap small = Bitmap.createScaledBitmap(bitmap, size[0], size[1], true);
            Bitmap readable = small.getConfig() == Bitmap.Config.HARDWARE ? small.copy(Bitmap.Config.ARGB_8888, false) : small;
            if (readable == null) throw new IllegalArgumentException("Media artwork copy unavailable");
            // Always return an independent snapshot, even if createScaledBitmap returned the source object.
            return crop(readable, size[0], size[1]);
        }
        int width = source.getIntrinsicWidth(), height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) { width = CACHE_EDGE; height = CACHE_EDGE; }
        int[] size = fit(width, height, CACHE_EDGE);
        Bitmap result = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        Drawable.ConstantState constant = source.getConstantState();
        if (constant == null) throw new IllegalArgumentException("Media artwork cannot be safely copied");
        Drawable copy = constant.newDrawable().mutate();
        copy.setState(source.getState()); copy.setLevel(source.getLevel());
        copy.setBounds(0, 0, size[0], size[1]); copy.draw(new Canvas(result));
        return result;
    }

    private static Bitmap crop(Bitmap source, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        float drawWidth = source.getWidth() * scale, drawHeight = source.getHeight() * scale;
        canvas.drawBitmap(source, null, new RectF((width - drawWidth) / 2f, (height - drawHeight) / 2f,
                (width + drawWidth) / 2f, (height + drawHeight) / 2f), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return result;
    }

    /** Three separable box passes. Background clamps edges; glow samples transparent padding. */
    private static Bitmap blur(Bitmap source, int radius, boolean clampEdges, BooleanSupplier cancelled) {
        checkpoint(cancelled);
        int width = source.getWidth(), height = source.getHeight();
        int[] a = new int[width * height], b = new int[a.length];
        source.getPixels(a, 0, width, 0, 0, width, height);
        // Work in premultiplied channels to avoid colored fringes around transparent glow pixels.
        for (int i = 0; i < a.length; i++) {
            int color = a[i], alpha = color >>> 24;
            a[i] = (alpha << 24) | (((color >>> 16) & 255) * alpha / 255 << 16)
                    | (((color >>> 8) & 255) * alpha / 255 << 8) | ((color & 255) * alpha / 255);
        }
        for (int pass = 0; pass < 3; pass++) {
            box(a, b, width, height, radius, true, clampEdges, cancelled);
            box(b, a, width, height, radius, false, clampEdges, cancelled);
        }
        for (int i = 0; i < a.length; i++) {
            int color = a[i], alpha = color >>> 24;
            a[i] = alpha == 0 ? 0 : (alpha << 24)
                    | (Math.min(255, ((color >>> 16) & 255) * 255 / alpha) << 16)
                    | (Math.min(255, ((color >>> 8) & 255) * 255 / alpha) << 8)
                    | Math.min(255, (color & 255) * 255 / alpha);
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(a, 0, width, 0, 0, width, height);
        return result;
    }

    private static void box(int[] source, int[] target, int width, int height,
            int radius, boolean horizontal, boolean clampEdges, BooleanSupplier cancelled) {
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int divisor = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            checkpoint(cancelled);
            int alpha = 0, red = 0, green = 0, blue = 0;
            for (int pos = -radius; pos <= radius; pos++) {
                int color = sample(source, line, pos, width, length, horizontal, clampEdges);
                alpha += color >>> 24; red += (color >>> 16) & 255; green += (color >>> 8) & 255; blue += color & 255;
            }
            for (int pos = 0; pos < length; pos++) {
                target[horizontal ? line * width + pos : pos * width + line] = (alpha / divisor << 24)
                        | (red / divisor << 16) | (green / divisor << 8) | blue / divisor;
                int old = sample(source, line, pos - radius, width, length, horizontal, clampEdges);
                int next = sample(source, line, pos + radius + 1, width, length, horizontal, clampEdges);
                alpha += (next >>> 24) - (old >>> 24); red += ((next >>> 16) & 255) - ((old >>> 16) & 255);
                green += ((next >>> 8) & 255) - ((old >>> 8) & 255); blue += (next & 255) - (old & 255);
            }
        }
    }

    private static void checkpoint(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException("Superseded media artwork");
    }

    private static int sample(int[] pixels, int line, int pos, int width, int length,
            boolean horizontal, boolean clampEdges) {
        if (pos < 0 || pos >= length) {
            if (!clampEdges) return 0;
            pos = Math.max(0, Math.min(length - 1, pos));
        }
        return pixels[horizontal ? line * width + pos : pos * width + line];
    }

    private static final class Material {
        final String source;
        final RuntimeShader shader;
        Bitmap artwork,previousArtwork;
        BitmapShader coverShader,previousShader;
        float width, height;
        float opacity = -1f;
        float previousOpacity = -1f,fadeMix = -1f;
        boolean nativeDirty = true;
        Object nativeSignature;
        final Map<String, Object> events = new LinkedHashMap<>();
        WeakReference<RuntimeShader> original = new WeakReference<>(null);
        Swap swap;
        Material(String source) { this.source = source; shader = new RuntimeShader(source); }
    }

    private static final class FailedMaterial {
        final WeakReference<RuntimeShader> original;
        final Object signature;
        final Map<String,Object> events = new LinkedHashMap<>();
        FailedMaterial(RuntimeShader original,Object signature) {
            this.original = new WeakReference<>(original); this.signature = signature;
        }
    }

    private final class Swap {
        final WeakReference<Object> owner;
        RuntimeShader original;
        final RuntimeShader replacement;
        final Paint paint;
        Shader paintShader;
        Swap(Object owner, RuntimeShader original, RuntimeShader replacement, Paint paint) {
            this.owner = new WeakReference<>(owner); this.original = original; this.replacement = replacement;
            this.paint = paint; paintShader = paint.getShader();
        }
        void restore() {
            Object target = owner.get();
            if (field(target, "shader") == replacement) setField(target, "shader", original);
            if (paint.getShader() == replacement) paint.setShader(paintShader);
        }
    }

    private Swap prepareMaterial(Drawable engine,Prepared prepared,Prepared previous,float fadeMix) throws ReflectiveOperationException {
        boolean nextBackground = background(prepared),oldBackground = background(previous);
        if ((!nextBackground && !oldBackground) || !Boolean.TRUE.equals(field(engine, "enableShader")))
            return unavailable("background-or-shader-disabled",engine);
        Object owner = field(engine, "drawableShader");
        Object original = field(owner, "shader");
        FailedMaterial failed = failedMaterials.get(owner);
        if (failed != null && failed.original.get() == original) return unavailable("native-shader-failed",engine);
        Object paint = field(engine, "drawableShaderPaint");
        if (!(original instanceof RuntimeShader) || !(paint instanceof Paint)
                || !Boolean.TRUE.equals(field(owner, "isValid"))) return unavailable("native-shader-not-ready",engine);
        if (Boolean.TRUE.equals(field(field(owner, "metaBallParams"), "valid"))) return unavailable("native-metaball-active",engine);
        float width = QsTileAppearance.number(field(owner, "width"), 0f);
        float height = QsTileAppearance.number(field(owner, "height"), 0f);
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f || width > 16384f || height > 16384f) return unavailable("native-size-not-ready",engine);
        Material material = materials.get(owner);
        boolean nativeChanged = material == null || material.nativeDirty || material.original.get() != original
                || material.width != width || material.height != height;
        if (nativeChanged) {
            Object corner = call(field(owner, "mCornerParams"), "getType");
            Object summary = field(owner, "summaryBlendParam");
            Object effects = call(owner, "getAllEffects");
            if (corner == null || !(summary instanceof List) || !(effects instanceof List)) return unavailable("native-effects-unavailable",engine);
            // OEM shaderStringBuilder is scratch storage: constructor and updateShader clear it
            // after compiling. Reconstruct only at material changes, never once per native draw.
            Object cachedSource = field(owner, "shaderStringBuilder");
            String source = cachedSource instanceof StringBuilder ? decorate(cachedSource.toString()) : null;
            if (source == null) {
                Class<?> builder = Class.forName("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt", false, owner.getClass().getClassLoader());
                StringBuilder sourceBuilder = new StringBuilder();
                invoke(builder, "buildShaderString", sourceBuilder, field(owner, "blendAlgorithmMask"), ((List<?>) summary).size(), corner, false, effects);
                source = decorate(sourceBuilder.toString());
                if (ModuleDiagnostics.enabled()) trace("shaderSource:" + simpleType(owner), "Music native shader scratch reconstructed " + simpleType(owner));
            }
            if (source == null) throw new IllegalArgumentException("Native media material sampling unsupported");
            if (material == null || !material.source.equals(source)) { material = new Material(source); materials.put(owner, material); }
            RuntimeShader replacement = material.shader;
            // Exact routines preserve OEM corners/optics. Upload only after native material changes.
            invoke(owner, "setBaseUniform", replacement);
            invoke(owner, "setCommonArray", replacement);
            replacement.setIntUniform("uEnableBlend", Boolean.TRUE.equals(field(owner, "enableBlend")) ? 1 : 0);
            replacement.setFloatUniform("u_multiBlendParams", QsNativeGlassFill.uniforms((List<?>) summary));
            for (Object effect : (List<?>) effects)
                if (Boolean.TRUE.equals(call(effect, "isEnabled"))) invoke(effect, "pushUniforms", replacement);
            material.nativeSignature = nativeSignature(owner, 0, new IdentityHashMap<>());
            material.original = new WeakReference<>((RuntimeShader) original);
            material.nativeDirty = false;
            failedMaterials.remove(owner);
            if (ModuleDiagnostics.enabled()) trace("shaderReady:" + simpleType(engine), "Music artwork material prepared " + simpleType(engine));
        }
        RuntimeShader replacement = material.shader;
        Bitmap nextBitmap = nextBackground ? prepared.background : previous.background;
        Bitmap oldBitmap = oldBackground ? previous.background : nextBitmap;
        boolean resized = material.width != width || material.height != height;
        if (material.previousArtwork != oldBitmap || resized) {
            material.previousArtwork = oldBitmap;
            material.previousShader = !resized && material.artwork == oldBitmap && material.coverShader != null
                    ? material.coverShader : artworkShader(oldBitmap,width,height);
            replacement.setInputShader("c17MediaPrevious",material.previousShader);
        }
        if (material.artwork != nextBitmap || resized) {
            material.artwork = nextBitmap;
            material.width = width; material.height = height;
            material.coverShader = material.previousArtwork == nextBitmap ? material.previousShader : artworkShader(nextBitmap,width,height);
            replacement.setInputShader("c17MediaCover", material.coverShader);
        }
        float opacity = nextBackground ? prepared.style.backgroundOpacity : 0f;
        float previousOpacity = oldBackground ? previous.style.backgroundOpacity : 0f;
        if (material.opacity != opacity) {
            material.opacity = opacity;
            replacement.setFloatUniform("c17MediaOpacity", material.opacity / 100f);
        }
        if (material.previousOpacity != previousOpacity) {
            material.previousOpacity = previousOpacity;
            replacement.setFloatUniform("c17MediaPreviousOpacity",previousOpacity/100f);
        }
        if (material.fadeMix != fadeMix) {
            material.fadeMix = fadeMix;
            replacement.setFloatUniform("c17MediaMix",fadeMix);
        }
        if (material.swap == null || material.swap.paint != paint)
            material.swap = new Swap(owner, (RuntimeShader) original, replacement, (Paint) paint);
        material.swap.original = (RuntimeShader) original;
        material.swap.paintShader = ((Paint) paint).getShader();
        return setField(owner, "shader", replacement) ? material.swap : null;
    }

    private static BitmapShader artworkShader(Bitmap artwork,float width,float height) {
        BitmapShader shader = new BitmapShader(artwork,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        float scale = Math.max(width/artwork.getWidth(),height/artwork.getHeight());
        Matrix matrix = new Matrix(); matrix.setScale(scale,scale);
        matrix.postTranslate((width-artwork.getWidth()*scale)/2f,(height-artwork.getHeight()*scale)/2f);
        shader.setLocalMatrix(matrix);
        return shader;
    }

    private static String decorate(String source) {
        int sample = source.indexOf(SAMPLE);
        if (sample < 0 || source.indexOf(SAMPLE, sample + SAMPLE.length()) >= 0
                || source.indexOf(MAIN) < 0 || source.indexOf("c17MediaCover") >= 0) return null;
        // Only wallpaper sampling changes; blend rules and all original optical code continue after this line.
        return source.replace(SAMPLE, SAMPLE + "\n half4 c17Art=c17MediaCover.eval(position);\n"
                + " float c17NewWeight=c17MediaOpacity*c17MediaMix;\n"
                + " float c17OldWeight=c17MediaPreviousOpacity*(1.0-c17MediaMix);\n"
                + " float3 c17Native=float3(outputCol.rgb);\n"
                + " float3 c17Result=c17Native*(1.0-c17NewWeight*float(c17Art.a))+float3(c17Art.rgb)*c17NewWeight;\n"
                // Uniform branch: the second cached texture is sampled only during a real fade.
                + " if(c17OldWeight>0.0){half4 c17Old=c17MediaPrevious.eval(position);"
                + "c17Result+=float3(c17Old.rgb)*c17OldWeight-c17Native*c17OldWeight*float(c17Old.a);}\n"
                + " outputCol.rgb=half3(c17Result);")
                .replace(MAIN, COVER_CODE + MAIN);
    }

    private static String simpleType(Object value) { return value == null ? "none" : value.getClass().getSimpleName(); }
    private Swap unavailable(String reason, Object engine) {
        if (ModuleDiagnostics.enabled()) trace("materialGate:" + reason, "Music material gate " + reason + " " + simpleType(engine));
        return null;
    }
    private void trace(String stage, String message) {
        if (!ModuleDiagnostics.enabled()) return;
        synchronized (traceStages) {
            if (traceStages.size() >= 32 || !traceStages.add(stage)) return;
        }
        ModuleDiagnostics.info("qs_style", message);
    }

    private void dirtyGlass(Drawable engine) {
        Object lock = field(engine, "dataLock");
        if (lock != null) synchronized (lock) { setField(engine, "contentDirty", true); }
        engine.invalidateSelf();
    }

    private void dirtyOwner(View owner) {
        List<Drawable> dirty = new ArrayList<>();
        synchronized (engines) {
            for (Map.Entry<Drawable, WeakReference<View>> entry : engines.entrySet())
                if (entry.getValue().get() == owner) dirty.add(entry.getKey());
        }
        for (Drawable engine : dirty) dirtyGlass(engine);
    }

    /** Resolved members, including unavailable members, are cached for this runtime only. */
    private Members access(Object target) {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        synchronized (members) {
            Members found = members.get(type);
            if (found == null) { found = new Members(type); members.put(type, found); }
            return found;
        }
    }

    private Object field(Object target, String name) {
        return target == null ? null : read(access(target).field(name), target);
    }
    private static Object read(Field field, Object target) {
        if (field == null) return null;
        try { return field.get(target); } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private boolean setField(Object target, String name, Object value) {
        if (target == null) return false;
        Field field = access(target).field(name);
        if (field == null) return false;
        try { field.set(target, value); return true; } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    private Object nativeValue(Object target, String name, String getter) {
        if (target == null) return null;
        Field field = access(target).field(name);
        return field == null ? call(target, getter) : read(field, target);
    }
    private Object call(Object target, String name, Object... args) {
        try { return invoke(target, name, args); } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        if (target == null) throw new NoSuchMethodException(name);
        Method method = access(target).method(name, args);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(target instanceof Class ? null : target, args);
    }
    private static final class Members {
        final Class<?> type;
        final Map<String, Field> fields = new LinkedHashMap<>();
        final Map<String, Method> methods = new LinkedHashMap<>();
        Field[] all;
        Members(Class<?> type) { this.type = type; }
        synchronized Field field(String name) {
            if (fields.containsKey(name)) return fields.get(name);
            Field found = null;
            for (Class<?> cls = type; cls != null; cls = cls.getSuperclass()) {
                try { found = cls.getDeclaredField(name); found.setAccessible(true); break; }
                catch (ReflectiveOperationException | RuntimeException unavailable) { }
            }
            fields.put(name, found); return found;
        }
        synchronized Field[] allFields() {
            if (all == null) {
                List<Field> found = new ArrayList<>();
                for (Class<?> cls = type; cls != null; cls = cls.getSuperclass()) for (Field field : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    try { field.setAccessible(true); found.add(field); } catch (RuntimeException unavailable) { }
                }
                all = found.toArray(new Field[0]);
            }
            return all;
        }
        synchronized Method method(String name, Object[] args) {
            StringBuilder key = new StringBuilder(name);
            for (Object arg : args) key.append('#').append(arg == null ? "null" : arg.getClass().getName());
            String signature = key.toString();
            if (methods.containsKey(signature)) return methods.get(signature);
            Method found = null;
            for (Class<?> cls = type; cls != null && found == null; cls = cls.getSuperclass()) for (Method method : cls.getDeclaredMethods()) {
                if (!name.equals(method.getName()) || method.getParameterTypes().length != args.length) continue;
                Class<?>[] types = method.getParameterTypes(); boolean matches = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !boxed(types[i]).isInstance(args[i])) { matches = false; break; }
                if (matches) { method.setAccessible(true); found = method; break; }
            }
            methods.put(signature, found); return found;
        }
        static Class<?> boxed(Class<?> type) {
            if (type == int.class) return Integer.class; if (type == float.class) return Float.class;
            if (type == boolean.class) return Boolean.class; if (type == long.class) return Long.class;
            return type;
        }
    }

    /** Only native update callbacks inspect material content. Paint never walks these structures. */
    private Object nativeSignature(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return Boolean.FALSE;
        if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Enum) return value;
        if (value instanceof Bitmap) return Arrays.asList(new Identity(value), ((Bitmap) value).getGenerationId());
        if (value instanceof Matrix) { float[] data = new float[9]; ((Matrix) value).getValues(data); return floatSignature(data); }
        if (value instanceof PointF) return Arrays.asList(((PointF) value).x, ((PointF) value).y);
        if (value instanceof float[]) return floatSignature((float[]) value);
        if (value instanceof int[]) { List<Integer> data = new ArrayList<>(); for (int n : (int[]) value) data.add(n); return data; }
        if (depth >= 5 || seen.put(value, Boolean.TRUE) != null) return new Identity(value);
        if (value instanceof Object[]) {
            List<Object> values = new ArrayList<>();
            for (Object item : (Object[]) value) values.add(nativeSignature(item, depth + 1, seen));
            return values;
        }
        List<Object> result = new ArrayList<>(); result.add(new Identity(value));
        if (value instanceof List) {
            for (Object item : (List<?>) value) { if (result.size() > 64) break; result.add(nativeSignature(item, depth + 1, seen)); }
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> item : ((Map<?, ?>) value).entrySet()) {
                if (result.size() > 64) break;
                result.add(nativeSignature(item.getKey(), depth + 1, seen)); result.add(nativeSignature(item.getValue(), depth + 1, seen));
            }
        } else if (value.getClass().getName().startsWith("com.oplus.posteffect.")) {
            for (Field field : access(value).allFields()) {
                if (field.getName().equals("shader") || field.getName().equals("shaderStringBuilder")) continue;
                result.add(field.getName()); result.add(nativeSignature(read(field, value), depth + 1, seen));
            }
        }
        return result;
    }
    private static List<Float> floatSignature(float[] values) {
        List<Float> result = new ArrayList<>(values.length); for (float value : values) result.add(value); return result;
    }
    private static final class Identity {
        final WeakReference<Object> value;
        Identity(Object value) { this.value = new WeakReference<>(value); }
        @Override public boolean equals(Object other) { return other instanceof Identity && value.get() != null && value.get() == ((Identity) other).value.get(); }
        @Override public int hashCode() { return System.identityHashCode(value.get()); }
    }

    private void error(Throwable failure) {
        if (!loggedError) {
            loggedError = true;
            ModuleDiagnostics.error("qs_media", "Media cover effect unavailable; native material retained", failure);
        }
    }
}
