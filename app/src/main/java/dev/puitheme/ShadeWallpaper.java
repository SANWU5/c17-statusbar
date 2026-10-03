// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Paints beneath native children in one exact shade root; the native gesture owns every frame. */
public final class ShadeWallpaper {
    private static final int CACHE_BYTES = 24 * 1024 * 1024;
    private static final Executor INACTIVE = runnable -> { };
    private final Handler main = new Handler(Looper.getMainLooper());
    interface ImageReader { Bitmap read(Context context, String scene, String revision) throws Exception; }
    private final Executor worker, completion;
    private final ImageReader imageReader;
    // Accessed only by the worker. Eviction releases references; displayed bitmaps are never recycled.
    private final Map<String, Bitmap> cache = new LinkedHashMap<>(4, .75f, true);
    private final Set<String> failed = new HashSet<>();
    private int cacheBytes;
    private volatile long generation;
    private Context context;
    private Config config = new Config(null);
    private WeakReference<View> root = new WeakReference<>(null);
    private String mode = "unknown";
    private boolean landscape, visible;
    private float controlWeight;
    private Frame frame;
    private LoadKey pending;

    public ShadeWallpaper() {
        worker = !ShadeWallpaperSettings.available() ? INACTIVE : new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "C17ShadeWallpaper"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
        completion = ShadeWallpaperSettings.available() ? main::post : INACTIVE;
        imageReader = ShadeWallpaper::readNormalized;
    }
    /** Deterministic scheduling fixture; the public constructor always uses the private PNG reader. */
    ShadeWallpaper(Executor worker, Executor completion, ImageReader imageReader) {
        this.worker = worker; this.completion = completion; this.imageReader = imageReader;
    }

    /** Called by the existing settings delivery, never by draw(). */
    public void configure(Context candidate, Bundle values) {
        if (!ShadeWallpaperSettings.available()) { release(true); return; }
        Config replacement = new Config(values);
        if (candidate == null || !"com.android.systemui".equals(candidate.getPackageName())) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> configure(candidate, replacement));
        } else configure(candidate, replacement);
    }
    private void configure(Context candidate, Config replacement) {
        if (!ShadeWallpaperSettings.available()) { release(true); return; }
        if (context != null && config.equals(replacement)) return;
        Context application = candidate.getApplicationContext();
        context = application != null ? application : candidate;
        Config previous = config; config = replacement;
        if (config.disabled) { invalidateLoad(); frame = null; invalidate(); return; }
        if (frame != null && previous.sameAssets(config)) {
            frame = new Frame(frame.first.bitmap, frame.second.bitmap,
                    sceneConfig(false).brightness, sceneConfig(true).brightness);
            invalidate(); return;
        }
        frame = null; invalidateLoad(); requestLoad(); invalidate();
    }
    /** Bind a fixed native shade drawing root, not an animated notification or QS page. */
    public void onPanelChanged(View nativeRoot, String nativeMode, int barState, float fraction, boolean closed) {
        if (!ShadeWallpaperSettings.available()) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        String nextMode = "classic".equals(nativeMode) ? "classic" : "separate".equals(nativeMode) ? "separate" : "unknown";
        boolean nextLandscape = nativeRoot != null && nativeRoot.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean changed = root.get() != nativeRoot || !mode.equals(nextMode) || landscape != nextLandscape;
        if (changed) {
            root = new WeakReference<>(nativeRoot); mode = nextMode; landscape = nextLandscape;
            // An orientation/mode switch never displays the previous scene while loading the new one.
            frame = null; invalidateLoad();
        }
        visible = nativeRoot != null && barState == 0 && !closed && Float.isFinite(fraction) && fraction > 0f;
        if (changed) requestLoad();
        invalidate();
    }
    /** Absolute control-center visibility 0..1 from the native horizontal animated value. */
    public void onHorizontalProgress(float nativeControlWeight) {
        if (!ShadeWallpaperSettings.available()) return;
        if (!Float.isFinite(nativeControlWeight)) return;
        float next = Math.max(0f, Math.min(1f, nativeControlWeight));
        if (Float.compare(controlWeight, next) == 0) return;
        controlWeight = next; invalidate();
    }
    public void onLayoutChanged(View nativeRoot) { if (ShadeWallpaperSettings.available() && nativeRoot == root.get()) invalidate(); }
    public void onDetached(View nativeRoot) { if (nativeRoot == root.get()) release(false); }
    /** Lifecycle retirement cancels queued publication and releases native view/image references. */
    public void reset() {
        release(true);
    }
    private void release(boolean clearContext) {
        invalidateLoad(); root.clear(); visible = false; mode = "unknown"; frame = null; controlWeight = 0f;
        if (clearContext) context = null;
        if (!ShadeWallpaperSettings.available()) {
            cache.clear(); cacheBytes = 0; failed.clear();
            return;
        }
        worker.execute(() -> { cache.clear(); cacheBytes = 0; failed.clear(); });
    }
    private void invalidateLoad() { generation++; pending = null; }
    private void invalidate() { View view = root.get(); if (view != null) view.invalidate(); }
    private SceneConfig sceneConfig(boolean control) {
        String scene = ShadeWallpaperSettings.scene(mode, control, landscape);
        return scene == null ? SceneConfig.EMPTY : config.scenes[ShadeWallpaperSettings.SCENES.indexOf(scene)];
    }
    private void requestLoad() {
        if (!ShadeWallpaperSettings.available() || context == null || config.disabled || "unknown".equals(mode)) return;
        SceneConfig first = sceneConfig(false), second = "separate".equals(mode) ? sceneConfig(true) : SceneConfig.EMPTY;
        LoadKey key = new LoadKey(first, second);
        if (key.equals(pending)) return;
        pending = key;
        final long token = generation;
        final Context reader = context;
        worker.execute(() -> {
            if (!ShadeWallpaperSettings.available() || token != generation) return;
            Bitmap a = load(reader, key.first), b = load(reader, key.second);
            if (!ShadeWallpaperSettings.available() || token != generation) return;
            // Retain only the active mode/orientation pair. At most two normalized images stay cached.
            retain(key.first.asset(), key.second.asset());
            completion.execute(() -> {
                if (!ShadeWallpaperSettings.available() || token != generation || !key.equals(pending) || config.disabled) return;
                frame = new Frame(a, b, sceneConfig(false).brightness, sceneConfig(true).brightness);
                invalidate();
            });
        });
    }
    private Bitmap load(Context reader, SceneConfig settings) {
        if (!ShadeWallpaperSettings.available()) return null;
        String asset = settings.asset();
        if (asset.isEmpty() || failed.contains(asset)) return null;
        Bitmap cached = cache.get(asset);
        if (cached != null) return cached;
        Bitmap image = null;
        try {
            image = imageReader.read(reader, settings.scene, settings.revision);
            if (image == null || image.getAllocationByteCount() > ShadeWallpaperRepository.MAX_PIXELS * 4)
                throw new java.io.IOException("Malformed normalized wallpaper");
            cache.put(asset, image); cacheBytes += image.getAllocationByteCount(); trim();
            return image;
        } catch (Exception | OutOfMemoryError failure) {
            if (image != null) image.recycle();
            // No per-frame retry. A new revision or lifecycle reset allows a new bounded attempt.
            failed.add(asset);
            if (failed.size() > 12) failed.clear();
            ModuleDiagnostics.error("shade-wallpaper", "Private wallpaper unavailable; native background retained", failure);
            return null;
        }
    }
    private static Bitmap readNormalized(Context reader, String scene, String revision) throws Exception {
        if (!ShadeWallpaperSettings.available()) throw new java.io.IOException(ShadeWallpaperSettings.unavailableReason());
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream input = reader.getContentResolver().openInputStream(ShadeWallpaperRepository.uri(scene, revision))) {
            if (input == null) throw new java.io.IOException("Unavailable wallpaper");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (!"image/png".equals(bounds.outMimeType) || bounds.outWidth < 1 || bounds.outHeight < 1
                || bounds.outWidth > ShadeWallpaperRepository.MAX_EDGE || bounds.outHeight > ShadeWallpaperRepository.MAX_EDGE
                || (long) bounds.outWidth * bounds.outHeight > ShadeWallpaperRepository.MAX_PIXELS)
            throw new java.io.IOException("Malformed normalized wallpaper");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888; options.inScaled = false;
        try (InputStream input = reader.getContentResolver().openInputStream(ShadeWallpaperRepository.uri(scene, revision))) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }
    private void retain(String first, String second) {
        Iterator<Map.Entry<String, Bitmap>> items = cache.entrySet().iterator();
        while (items.hasNext()) {
            Map.Entry<String, Bitmap> item = items.next();
            if (!item.getKey().equals(first) && !item.getKey().equals(second)) {
                cacheBytes -= item.getValue().getAllocationByteCount(); items.remove();
            }
        }
    }
    private void trim() {
        Iterator<Map.Entry<String, Bitmap>> items = cache.entrySet().iterator();
        while (cacheBytes > CACHE_BYTES && items.hasNext()) {
            cacheBytes -= items.next().getValue().getAllocationByteCount(); items.remove();
        }
    }
    /** Hook before the native panel pager is drawn, after scrims, on the exact bound shade root. */
    public void draw(View nativeRoot, Canvas canvas) {
        if (!ShadeWallpaperSettings.available()) return;
        Frame current = frame;
        if (nativeRoot == null || nativeRoot != root.get() || canvas == null || !visible || config.disabled
                || ModuleLifecycle.removed() || current == null) return;
        int width = nativeRoot.getWidth(), height = nativeRoot.getHeight();
        if (width <= 0 || height <= 0) return;
        current.resize(width, height);
        float mix = "separate".equals(mode) ? controlWeight : 0f;
        if (mix <= 0f) { current.first.draw(canvas, 255, false); return; }
        if (mix >= 1f) { current.second.draw(canvas, 255, false); return; }
        if (current.first.bitmap == null && current.second.bitmap == null) return;
        // Add premultiplied image weights in a temporary layer; this avoids the midpoint dimming
        // of two sequential SRC_OVER fades and preserves native fallback through transparent pixels.
        int saved = canvas.saveLayer(current.bounds, null);
        int secondAlpha = Math.round(mix * 255f);
        current.first.draw(canvas, 255 - secondAlpha, false);
        current.second.draw(canvas, secondAlpha, true);
        canvas.restoreToCount(saved);
    }
    private static final class Image {
        final Bitmap bitmap;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Rect source = new Rect();
        final RectF destination = new RectF();
        final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        Image(Bitmap bitmap, float brightness) {
            this.bitmap = bitmap;
            float multiplier = brightness <= 100f ? brightness / 100f : 2f - brightness / 100f;
            float offset = brightness > 100f ? (brightness - 100f) * 2.55f : 0f;
            if (brightness != 100f) paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    multiplier, 0, 0, 0, offset, 0, multiplier, 0, 0, offset,
                    0, 0, multiplier, 0, offset, 0, 0, 0, 1, 0})));
        }
        void resize(int width, int height) {
            destination.set(0f, 0f, width, height);
            if (bitmap == null) return;
            int bitmapWidth = bitmap.getWidth(), bitmapHeight = bitmap.getHeight();
            if ((long) bitmapWidth * height > (long) bitmapHeight * width) {
                int cropWidth = Math.max(1, Math.round(bitmapHeight * width / (float) height));
                int left = (bitmapWidth - cropWidth) / 2; source.set(left, 0, left + cropWidth, bitmapHeight);
            } else {
                int cropHeight = Math.max(1, Math.round(bitmapWidth * height / (float) width));
                int top = (bitmapHeight - cropHeight) / 2; source.set(0, top, bitmapWidth, top + cropHeight);
            }
        }
        void draw(Canvas canvas, int alpha, boolean additive) {
            if (bitmap == null || alpha <= 0) return;
            paint.setAlpha(alpha); paint.setXfermode(additive ? add : null);
            canvas.drawBitmap(bitmap, source, destination, paint);
        }
    }
    private static final class Frame {
        final Image first, second;
        final RectF bounds = new RectF();
        int width = -1, height = -1;
        Frame(Bitmap first, Bitmap second, float firstBrightness, float secondBrightness) {
            this.first = new Image(first, firstBrightness); this.second = new Image(second, secondBrightness);
        }
        void resize(int width, int height) {
            if (this.width == width && this.height == height) return;
            this.width = width; this.height = height; bounds.set(0f, 0f, width, height);
            first.resize(width, height); second.resize(width, height);
        }
    }
    private static final class SceneConfig {
        static final SceneConfig EMPTY = new SceneConfig("", false, "", 100f);
        final String scene, revision;
        final boolean enabled;
        final float brightness;
        SceneConfig(String scene, boolean enabled, String revision, float brightness) {
            this.scene = scene; this.enabled = enabled; this.revision = revision; this.brightness = brightness;
        }
        String asset() { return enabled && !revision.isEmpty() ? scene + "/" + revision : ""; }
        @Override public boolean equals(Object object) {
            if (!(object instanceof SceneConfig)) return false;
            SceneConfig other = (SceneConfig) object;
            return enabled == other.enabled && brightness == other.brightness && scene.equals(other.scene) && revision.equals(other.revision);
        }
        @Override public int hashCode() { return Objects.hash(scene, enabled, revision, brightness); }
    }
    private static final class LoadKey {
        final SceneConfig first, second;
        LoadKey(SceneConfig first, SceneConfig second) { this.first = first; this.second = second; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof LoadKey)) return false;
            LoadKey other = (LoadKey) value;
            return first.asset().equals(other.first.asset()) && second.asset().equals(other.second.asset());
        }
        @Override public int hashCode() { return Objects.hash(first.asset(), second.asset()); }
    }
    private static final class Config {
        final boolean disabled;
        final SceneConfig[] scenes = new SceneConfig[6];
        Config(Bundle source) {
            disabled = !ShadeWallpaperSettings.available() || SafetyMode.enabled(source) || source == null || !Boolean.TRUE.equals(source.get(ShadeWallpaperSettings.MASTER));
            for (int i = 0; i < scenes.length; i++) {
                String scene = ShadeWallpaperSettings.SCENES.get(i);
                Object revision = source == null ? null : source.get(ShadeWallpaperSettings.revisionKey(scene));
                String valid = revision instanceof String && ShadeWallpaperSettings.validRevision((String) revision) ? (String) revision : "";
                scenes[i] = new SceneConfig(scene, ShadeWallpaperSettings.enabled(source, scene), valid,
                        ShadeWallpaperSettings.brightness(source == null ? null : source.get(ShadeWallpaperSettings.brightnessKey(scene))));
            }
        }
        boolean sameAssets(Config other) {
            if (disabled != other.disabled) return false;
            for (int i = 0; i < scenes.length; i++) if (!scenes[i].asset().equals(other.scenes[i].asset())) return false;
            return true;
        }
        @Override public boolean equals(Object value) {
            return value instanceof Config && disabled == ((Config) value).disabled && Arrays.equals(scenes, ((Config) value).scenes);
        }
        @Override public int hashCode() { return 31 * Boolean.hashCode(disabled) + Arrays.hashCode(scenes); }
    }
}
