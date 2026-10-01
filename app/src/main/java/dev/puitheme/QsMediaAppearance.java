// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private static final String PLATFORM_BLUR = "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable";
    private static final String GLASS = "com.oplus.posteffect.drawable.BlendDrawable";
    private static final String MAIN = "half4 main(float2 position) {";
    private static final String SAMPLE = "half4 outputCol = half4(uniBDFBitmap.eval(mapCoords).rgb, 1.0);";
    private static final String COVER_CODE = "uniform shader c17MediaCover;\nuniform float c17MediaOpacity;\n";
    private static final int CACHE_EDGE = 384;
    private static final int COVER_EDGE = 256;
    private static final int MAX_BLUR_RADIUS = 64;
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
    private final Map<Object, WeakReference<RuntimeShader>> failedMaterials = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Object> recording = new ThreadLocal<>();
    private volatile boolean enabled, backgroundEnabled = true, glowEnabled = true;
    private volatile Style light = Style.defaults(), dark = Style.defaults();
    private volatile boolean loggedError;

    public static String prefix(String scene) { return "qs_media_" + scene; }
    public static boolean isMediaView(View view) { return QsTileAppearance.type(view, MEDIA); }

    public void configure(Bundle values) {
        light = readStyle(values, "light");
        dark = readStyle(values, "dark");
        backgroundEnabled = values.getBoolean(BACKGROUND, true);
        glowEnabled = values.getBoolean(GLOW, true);
        enabled = values.getBoolean(MASTER, false);
        loggedError = false;
        failedMaterials.clear();
        List<View> owners;
        synchronized (states) { owners = new ArrayList<>(states.keySet()); }
        for (View owner : owners) {
            Runnable update = () -> {
                State state = states.get(owner);
                if (state != null) state.failureKey = null;
                refresh(owner);
            };
            if (Looper.myLooper() == Looper.getMainLooper()) update.run();
            else owner.post(update);
            owner.invalidate();
        }
        dirtyAllEngines();
    }

    /** After native binding, drawable/theme/configuration updates and media size changes. */
    public void refresh(View owner) {
        if (!isMediaView(owner) || Looper.myLooper() != Looper.getMainLooper()) return;
        State state = states.get(owner);
        if (state == null) {
            state = new State(owner);
            states.put(owner, state);
            owner.addOnLayoutChangeListener(state.layout);
        }
        Object coverValue = QsTileAppearance.call(owner, "getCoverImg");
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
        List<WeakReference<Drawable>> sources = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        register(owner, QsTileAppearance.call(owner, "getBgDrawable"), sources, seen, 0);
        register(owner, QsTileAppearance.call(owner, "getThemeBgDrawable"), sources, seen, 0);
        register(owner, QsTileAppearance.call(owner, "getTransitionDrawable"), sources, seen, 0);
        Object bg = QsTileAppearance.call(owner, "getBg");
        state.backgroundView = new WeakReference<>(bg instanceof View ? (View) bg : null);
        if (bg instanceof View && descendant((View) bg, owner))
            register(owner, ((View) bg).getBackground(), sources, seen, 0);
        state.sources = Collections.unmodifiableList(sources);

        Style style = style(owner);
        state.style = style;
        state.observe(cover, bg instanceof View ? (View) bg : owner);
        Drawable source = cover == null ? null : cover.getDrawable();
        boolean artwork = enabled && (backgroundEnabled || glowEnabled) && cover != null
                && cover.getVisibility() == View.VISIBLE && source != null
                && Boolean.FALSE.equals(QsTileAppearance.field(owner, "defaultCover"));
        int width = bg instanceof View ? ((View) bg).getWidth() : owner.getWidth();
        int height = bg instanceof View ? ((View) bg).getHeight() : owner.getHeight();
        float density = owner.getResources().getDisplayMetrics().density;
        if (!artwork || width <= 0 || height <= 0 || width > 16384 || height > 16384
                || cover.getWidth() <= 0 || cover.getHeight() <= 0) {
            cancelPreparation(owner, state);
        } else {
            float corner = QsTileAppearance.number(QsTileAppearance.call(owner, "getCoverImgRadius"), 0f);
            Key key = new Key(source, width, height, cover.getWidth(), cover.getHeight(), density,
                    Math.max(0f, corner), style, backgroundEnabled, glowEnabled, state.coverRevision);
            Key desired = state.desiredKey;
            Prepared previous = state.prepared;
            if (desired != null && desired.same(key) && (previous != null || state.preparing)) {
                // Opacity changes do not decode, resample or blur the cover again.
                if (previous != null && previous.style != style) {
                    state.prepared = previous.withStyle(style);
                    invalidate(owner, cover);
                }
                return;
            }
            if (state.failureKey != null && state.failureKey.same(key)) return;
            // Capture a small, immutable artwork snapshot on the UI thread; all filters run on WORKER.
            state.desiredKey = key;
            state.prepared = null;
            state.preparing = true;
            long generation;
            synchronized (state) { generation = ++state.generation; state.pending = null; }
            invalidate(owner, cover);
            try {
                Bitmap artworkBitmap = snapshot(source);
                queue(state, new Work(key, style, artworkBitmap, generation));
            } catch (RuntimeException | OutOfMemoryError unavailable) {
                state.preparing = false;
                state.failureKey = key;
                error(unavailable);
            }
        }
    }

    /** ImageView artwork may arrive asynchronously after bindCoverImg returns. */
    public void onCoverChanged(View cover) {
        WeakReference<View> ref = coverOwners.get(cover);
        View owner = ref == null ? null : ref.get();
        if (owner != null && Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> child = new WeakReference<>(cover);
            owner.post(() -> { View target = child.get(); if (target != null) onCoverChanged(target); });
            return;
        }
        if (owner != null && QsTileAppearance.call(owner, "getCoverImg") == cover) {
            State state = states.get(owner);
            if (state != null) { state.coverRevision++; state.failureKey = null; }
            refresh(owner);
        }
    }

    public void detach(View owner) {
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
        synchronized (engines) {
            engines.entrySet().removeIf(item -> {
                View view = item.getValue().get();
                if (view == null || view == owner) { engineSources.remove(item.getKey()); return true; }
                return false;
            });
        }
        synchronized (backgrounds) {
            backgrounds.entrySet().removeIf(item -> item.getValue().get() == null || item.getValue().get() == owner);
        }
    }

    /** Scoped wrapper for inherited View.draw(Canvas); native background and children draw once. */
    public void drawMedia(View owner, Canvas canvas, DrawAction nativeDraw) throws Throwable {
        if (enabled && isMediaView(owner)) {
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
        if (enabled && backgroundEnabled && QsTileAppearance.type(drawable, AUTO_BLUR)) {
            WeakReference<View> ref = backgrounds.get(drawable);
            View owner = ref == null ? null : ref.get();
            State state = owner == null ? null : states.get(owner);
            if (state != null && state.prepared != null && currentSource(state, drawable)) {
                Object proxy = QsTileAppearance.call(drawable, "getViewBlurProxy");
                if (proxy == null) proxy = QsTileAppearance.field(drawable, "viewBlurProxy");
                Object actual = QsTileAppearance.call(proxy, "getBlurDrawable", QsTileAppearance.field(drawable, "defaultDrawable"));
                if (QsTileAppearance.type(actual, PLATFORM_BLUR)) {
                    Object nativeEngine = QsTileAppearance.field(actual, "blurDrawable");
                    if (nativeEngine instanceof Drawable && QsTileAppearance.type(nativeEngine, GLASS)) {
                        Drawable engine = (Drawable) nativeEngine;
                        WeakReference<View> old = engines.put(engine, new WeakReference<>(owner));
                        WeakReference<Drawable> oldSource = engineSources.put(engine, new WeakReference<>(drawable));
                        if (old == null || old.get() != owner || oldSource == null || oldSource.get() != drawable)
                            dirtyGlass(engine);
                    }
                }
            }
        }
        nativeDraw.draw(canvas);
    }

    /** Exact BlendDrawable.onDrawContent(Canvas), before the native RenderNode is recorded. */
    public void drawGlassContent(Drawable engine, Canvas canvas, DrawAction nativeDraw) throws Throwable {
        if (!enabled || !backgroundEnabled) { nativeDraw.draw(canvas); return; }
        WeakReference<View> ref = engines.get(engine);
        View owner = ref == null ? null : ref.get();
        State state = owner == null ? null : states.get(owner);
        Prepared prepared = state == null ? null : state.prepared;
        WeakReference<Drawable> source = engineSources.get(engine);
        if (!enabled || !backgroundEnabled || prepared == null || source == null
                || !currentSource(state, source.get()) || !QsTileAppearance.type(engine, GLASS)) {
            nativeDraw.draw(canvas);
            return;
        }
        Object lock = QsTileAppearance.field(engine, "dataLock");
        if (lock == null || recording.get() != null) { nativeDraw.draw(canvas); return; }
        synchronized (lock) {
            Swap swap = null;
            recording.set(engine);
            try {
                try { swap = prepareMaterial(engine, prepared); }
                catch (ReflectiveOperationException | RuntimeException unavailable) {
                    Object shaderOwner = QsTileAppearance.field(engine, "drawableShader");
                    Object nativeShader = QsTileAppearance.field(shaderOwner, "shader");
                    if (shaderOwner != null && nativeShader instanceof RuntimeShader)
                        failedMaterials.put(shaderOwner, new WeakReference<>((RuntimeShader) nativeShader));
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
        if (!enabled || !glowEnabled) return nativeDraw.draw(canvas);
        WeakReference<View> ref = coverOwners.get(child);
        if (ref == null) return nativeDraw.draw(canvas);
        View owner = ref == null ? null : ref.get();
        State state = owner == null ? null : states.get(owner);
        Prepared prepared = state == null ? null : state.prepared;
        if (enabled && glowEnabled && prepared != null && prepared.glow != null
                && child.getParent() == parent && state.cover.get() == child
                && descendant(parent, owner) && child.getVisibility() == View.VISIBLE && child.getAlpha() > 0f
                && Looper.myLooper() == Looper.getMainLooper()) {
            int saved = canvas.save();
            try {
                // drawChild receives parent coordinates, before the child's matrix and clipping are applied.
                canvas.translate(child.getLeft() - parent.getScrollX(), child.getTop() - parent.getScrollY());
                canvas.concat(child.getMatrix());
                Paint paint = prepared.glowPaint;
                paint.setAlpha(Math.round(255f * prepared.style.glowOpacity / 100f * clamp(child.getAlpha(), 0f, 1f)));
                float scaleX = child.getWidth() / (float) prepared.glowInnerWidth;
                float scaleY = child.getHeight() / (float) prepared.glowInnerHeight;
                RectF dst = prepared.glowDestination;
                dst.set(-prepared.glowPadding * scaleX, -prepared.glowPadding * scaleY,
                        (prepared.glow.getWidth() - prepared.glowPadding) * scaleX,
                        (prepared.glow.getHeight() - prepared.glowPadding) * scaleY);
                canvas.drawBitmap(prepared.glow, null, dst, paint);
            } catch (RuntimeException unavailable) { error(unavailable); }
            finally { canvas.restoreToCount(saved); }
        }
        return nativeDraw.draw(canvas);
    }

    private void register(View owner, Object value, List<WeakReference<Drawable>> result,
            Map<Object, Boolean> seen, int depth) {
        if (!(value instanceof Drawable) || depth > 12 || seen.put(value, true) != null) return;
        Drawable drawable = (Drawable) value;
        backgrounds.put(drawable, new WeakReference<>(owner));
        result.add(new WeakReference<>(drawable));
        // The media-specific MultiLightDrawable is deliberately not traversed or recolored.
        for (Object child : new Object[] { QsTileAppearance.call(drawable, "getDrawable"),
                QsTileAppearance.call(drawable, "getCurrent"),
                QsTileAppearance.field(drawable, "backgroundDrawable"), QsTileAppearance.field(drawable, "foregroundDrawable") })
            if (child != drawable) register(owner, child, result, seen, depth + 1);
    }

    private static boolean currentSource(State state, Drawable drawable) {
        if (state == null || drawable == null) return false;
        for (WeakReference<Drawable> ref : state.sources) if (ref.get() == drawable) return true;
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
        if (cover != null && cover.getParent() instanceof View) ((View) cover.getParent()).invalidate();
        owner.invalidate();
    }

    private void cancelPreparation(View owner, State state) {
        boolean changed = state.prepared != null || state.desiredKey != null;
        synchronized (state) { state.generation++; state.pending = null; }
        state.prepared = null;
        state.desiredKey = null;
        state.failureKey = null;
        state.preparing = false;
        if (changed) invalidate(owner, state.cover.get());
    }

    private void queue(State state, Work work) {
        synchronized (state) {
            state.pending = work;
            if (state.queued) return;
            state.queued = true;
        }
        // At most one queued runner per card; rapid config/artwork updates replace only its pending work.
        WORKER.execute(() -> runQueue(state));
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
            try { result = prepare(work.artwork, work.key, work.style); }
            catch (RuntimeException | OutOfMemoryError unavailable) { failure = unavailable; }
            View owner = state.owner.get();
            if (owner == null) continue;
            final Prepared finished = result;
            final Throwable error = failure;
            owner.post(() -> {
                // A closed/detached/rebound card never receives an older generation's effects.
                if (states.get(owner) != state || !enabled || state.generation != work.generation
                        || state.desiredKey == null || !state.desiredKey.same(work.key)) return;
                state.preparing = false;
                if (error != null) {
                    state.failureKey = work.key;
                    state.prepared = null;
                    error(error);
                } else {
                    state.failureKey = null;
                    state.prepared = finished.style == state.style ? finished : finished.withStyle(state.style);
                }
                invalidate(owner, state.cover.get());
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
        volatile Style style;
        volatile Key desiredKey;
        volatile boolean preparing;
        Key failureKey;
        volatile long generation;
        long coverRevision;
        Work pending;
        boolean queued;
        WeakReference<Drawable> observedSource = new WeakReference<>(null), observedCurrent = new WeakReference<>(null);
        int observedGeneration, observedLevel, observedState, observedVisibility, observedWidth, observedHeight;
        int observedCoverWidth, observedCoverHeight;
        volatile List<WeakReference<Drawable>> sources = Collections.emptyList();
        final View.OnLayoutChangeListener layout;
        State(View owner) {
            this.owner = new WeakReference<>(owner);
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
            Drawable source = cover == null ? null : cover.getDrawable();
            return observedSource.get() == source && observedCurrent.get() == (source == null ? null : source.getCurrent())
                    && observedGeneration == generationOf(source) && observedLevel == (source == null ? 0 : source.getLevel())
                    && observedState == (source == null ? 0 : Arrays.hashCode(source.getState()))
                    && observedVisibility == (cover == null ? View.GONE : cover.getVisibility())
                    && observedCoverWidth == (cover == null ? 0 : cover.getWidth())
                    && observedCoverHeight == (cover == null ? 0 : cover.getHeight())
                    && observedWidth == background.getWidth() && observedHeight == background.getHeight();
        }
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
    }

    private static final class Key {
        final WeakReference<Drawable> source, current;
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
            generation = bitmap == null || bitmap.isRecycled() ? 0 : bitmap.getGenerationId();
            level = drawable.getLevel(); state = Arrays.hashCode(drawable.getState());
        }
        boolean same(Key other) {
            return source.get() != null && source.get() == other.source.get() && current.get() == other.current.get()
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

    private static Prepared prepare(Bitmap artwork, Key key, Style style) {
        Bitmap background = null, glow = null;
        int innerWidth = 0, innerHeight = 0, padding = 0;
        if (key.background) {
            int[] size = fit(key.width, key.height, CACHE_EDGE);
            background = crop(artwork, size[0], size[1]);
            int radius = radius(style.backgroundBlur * key.density * size[0] / key.width);
            if (radius > 0) background = blur(background, radius, true);
        }
        if (key.glow) {
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
            if (radius > 0) glow = blur(glow, radius, false);
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
    private static Bitmap blur(Bitmap source, int radius, boolean clampEdges) {
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
            box(a, b, width, height, radius, true, clampEdges);
            box(b, a, width, height, radius, false, clampEdges);
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
            int radius, boolean horizontal, boolean clampEdges) {
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int divisor = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
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
        Bitmap artwork;
        BitmapShader coverShader;
        float width, height;
        Material(String source) { this.source = source; shader = new RuntimeShader(source); }
    }

    private static final class Swap {
        final Object owner;
        final RuntimeShader original, replacement;
        final Paint paint;
        final Shader paintShader;
        Swap(Object owner, RuntimeShader original, RuntimeShader replacement, Paint paint) {
            this.owner = owner; this.original = original; this.replacement = replacement;
            this.paint = paint; paintShader = paint.getShader();
        }
        void restore() {
            if (QsTileAppearance.field(owner, "shader") == replacement) QsTileAppearance.setField(owner, "shader", original);
            if (paint.getShader() == replacement) paint.setShader(paintShader);
        }
    }

    private Swap prepareMaterial(Drawable engine, Prepared prepared) throws ReflectiveOperationException {
        if (prepared.background == null || prepared.style.backgroundOpacity <= 0f
                || !Boolean.TRUE.equals(QsTileAppearance.field(engine, "enableShader"))) return null;
        Object owner = QsTileAppearance.field(engine, "drawableShader");
        Object original = QsTileAppearance.field(owner, "shader");
        WeakReference<RuntimeShader> failed = failedMaterials.get(owner);
        if (failed != null && failed.get() == original) return null;
        Object paint = QsTileAppearance.field(engine, "drawableShaderPaint");
        if (!(original instanceof RuntimeShader) || !(paint instanceof Paint)
                || !Boolean.TRUE.equals(QsTileAppearance.field(owner, "isValid"))) return null;
        Object meta = QsTileAppearance.field(owner, "metaBallParams");
        if (Boolean.TRUE.equals(QsTileAppearance.field(meta, "valid"))) return null;
        Object corner = QsTileAppearance.call(QsTileAppearance.field(owner, "mCornerParams"), "getType");
        Object summary = QsTileAppearance.field(owner, "summaryBlendParam");
        Object effects = QsTileAppearance.invoke(owner, "getAllEffects");
        if (corner == null || !(summary instanceof List) || !(effects instanceof List)) return null;
        float width = QsTileAppearance.number(QsTileAppearance.field(owner, "width"), 0f);
        float height = QsTileAppearance.number(QsTileAppearance.field(owner, "height"), 0f);
        if (width <= 0f || height <= 0f || width > 16384f || height > 16384f) return null;
        Class<?> builder = Class.forName("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt", false, owner.getClass().getClassLoader());
        StringBuilder nativeSource = new StringBuilder();
        QsTileAppearance.invoke(builder, "buildShaderString", nativeSource,
                QsTileAppearance.field(owner, "blendAlgorithmMask"), ((List<?>) summary).size(), corner, false, effects);
        String source = decorate(nativeSource.toString());
        if (source == null) throw new IllegalArgumentException("Native media material sampling unsupported");
        Material material = materials.get(owner);
        if (material == null || !material.source.equals(source)) { material = new Material(source); materials.put(owner, material); }
        RuntimeShader replacement = material.shader;
        // Exact native base/common/effect routines preserve native smooth corners, optics and gradient stroke.
        QsTileAppearance.invoke(owner, "setBaseUniform", replacement);
        QsTileAppearance.invoke(owner, "setCommonArray", replacement);
        replacement.setIntUniform("uEnableBlend", Boolean.TRUE.equals(QsTileAppearance.field(owner, "enableBlend")) ? 1 : 0);
        replacement.setFloatUniform("u_multiBlendParams", QsNativeGlassFill.uniforms((List<?>) summary));
        for (Object effect : (List<?>) effects)
            if (Boolean.TRUE.equals(QsTileAppearance.call(effect, "isEnabled"))) QsTileAppearance.invoke(effect, "pushUniforms", replacement);
        if (material.artwork != prepared.background || material.width != width || material.height != height) {
            material.artwork = prepared.background;
            material.width = width; material.height = height;
            material.coverShader = new BitmapShader(prepared.background, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(width / prepared.background.getWidth(), height / prepared.background.getHeight());
            Matrix matrix = new Matrix(); matrix.setScale(scale, scale);
            matrix.postTranslate((width - prepared.background.getWidth() * scale) / 2f,
                    (height - prepared.background.getHeight() * scale) / 2f);
            material.coverShader.setLocalMatrix(matrix);
        }
        replacement.setInputShader("c17MediaCover", material.coverShader);
        replacement.setFloatUniform("c17MediaOpacity", prepared.style.backgroundOpacity / 100f);
        Swap swap = new Swap(owner, (RuntimeShader) original, replacement, (Paint) paint);
        return QsTileAppearance.setField(owner, "shader", replacement) ? swap : null;
    }

    private static String decorate(String source) {
        int sample = source.indexOf(SAMPLE);
        if (sample < 0 || source.indexOf(SAMPLE, sample + SAMPLE.length()) >= 0
                || source.indexOf(MAIN) < 0 || source.indexOf("c17MediaCover") >= 0) return null;
        // Only wallpaper sampling changes; blend rules and all original optical code continue after this line.
        return source.replace(SAMPLE, SAMPLE + "\n half4 c17Art=c17MediaCover.eval(position);\n"
                + " outputCol.rgb=half3(mix(float3(outputCol.rgb),float3(c17Art.rgb)/max(float(c17Art.a),0.000001),c17MediaOpacity*float(c17Art.a)));")
                .replace(MAIN, COVER_CODE + MAIN);
    }

    private static void dirtyGlass(Drawable engine) {
        Object lock = QsTileAppearance.field(engine, "dataLock");
        if (lock != null) synchronized (lock) { QsTileAppearance.setField(engine, "contentDirty", true); }
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

    private void dirtyAllEngines() {
        List<Drawable> dirty;
        synchronized (engines) { dirty = new ArrayList<>(engines.keySet()); }
        for (Drawable engine : dirty) dirtyGlass(engine);
    }

    private void error(Throwable failure) {
        if (!loggedError) {
            loggedError = true;
            ModuleDiagnostics.error("qs_media", "Media cover effect unavailable; native material retained", failure);
        }
    }
}
