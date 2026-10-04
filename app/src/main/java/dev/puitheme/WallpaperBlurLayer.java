// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Owned compositor effects INSIDE the wallpaper window, never a keyguard/ViewRoot blur.
 * All native wallpaper handles are borrowed read-only. Only our own children are changed.
 * Native callbacks/settings serialize here. Wallpaper-only GPU pixels are cached;
 * no screen/foreground capture, CPU pixel reads, software blur or polling is used.
 */
final class WallpaperBlurLayer {
    static final String ENGINE = "android.service.wallpaper.WallpaperService$Engine";
    static final String CANVAS_ENGINE = "com.android.systemui.wallpapers.ImageWallpaper$CanvasEngine";
    private static final Field SURFACE_INSETS = field(WindowManager.LayoutParams.class, "surfaceInsets");
    private final Map<Object, Host> hosts = new WeakHashMap<>();
    private final Map<Class<?>, EngineAccess> access = new WeakHashMap<>();
    private SurfaceAccess surface;
    private CaptureAccess capture;
    private boolean enabled, crossWindowEnabled = true;
    private float visibility, radius, range, transition;
    private int color;
    private long revision, created, transactions, fallback, snapshots, captures, renders;

    private static final class Host {
        final WeakReference<Object> engine;
        final EngineAccess access;
        Object parent, anchor, group;
        Object buffer, canvasSurface, hardwareBuffer;
        Bitmap bitmap;
        WallpaperBlurRenderer renderer;
        boolean sourceDirty = true;
        LockscreenBlurGeometry.Plan plan;
        final Rect crop = new Rect();
        int width, height, x, y;
        float density, alpha = -1f;
        long revision = -1;
        boolean capturing, unsupported, awaitingSource;
        Host(Object engine, EngineAccess access) { this.engine = new WeakReference<>(engine); this.access = access; }
    }
    private static final class EngineAccess {
        final Field parent, transform, screenshot, destroyed, frames, visibleRequested, layout, requestedWidth, requestedHeight;
        final Method flags, preview, visible, context, zoomOut;
        final boolean ready;
        EngineAccess(Class<?> type) {
            Class<?> base = namedClass(type, ENGINE);
            parent = field(base, "mSurfaceControl"); transform = field(base, "mTransformSurfaceControl");
            screenshot = field(base, "mScreenshotSurfaceControl"); destroyed = field(base, "mDestroyed");
            frames = field(base, "mWinFrames");
            visibleRequested = field(base, "mVisible");
            layout = field(base, "mLayout"); requestedWidth = field(base, "mWidth"); requestedHeight = field(base, "mHeight");
            flags = method(base, "getWallpaperFlags"); preview = method(base, "isPreview");
            visible = method(base, "isVisible"); context = method(base, "getDisplayContext");
            zoomOut = method(type, "shouldZoomOutWallpaper");
            ready = parent != null && transform != null && screenshot != null && destroyed != null
                    && flags != null && preview != null && visible != null && context != null && frames != null
                    && layout != null && requestedWidth != null && requestedHeight != null && zoomOut != null;
        }
    }
    /** Native owned buffer/crop APIs. No vendor blur-region array is guessed. */
    private static final class SurfaceAccess {
        final Constructor<?> builder, transaction, canvasSurface;
        final Method name, parent, container, hidden, build, valid, release;
        final Method relative, layer, crop, position, alpha, show, hide, remove, apply, applySync, close;
        final Method bufferSize, format, opaque, lockCanvas, postCanvas, releaseCanvas;
        SurfaceAccess() throws ReflectiveOperationException {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            Class<?> b = Class.forName("android.view.SurfaceControl$Builder");
            Class<?> t = Class.forName("android.view.SurfaceControl$Transaction");
            Class<?> s = Class.forName("android.view.Surface");
            builder = b.getDeclaredConstructor(); builder.setAccessible(true);
            transaction = t.getDeclaredConstructor(); transaction.setAccessible(true);
            canvasSurface = s.getDeclaredConstructor(sc); canvasSurface.setAccessible(true);
            bufferSize = required(b, "setBufferSize", int.class, int.class);
            format = required(b, "setFormat", int.class); opaque = required(b, "setOpaque", boolean.class);
            lockCanvas = required(s, "lockHardwareCanvas");
            postCanvas = required(s, "unlockCanvasAndPost", Canvas.class); releaseCanvas = required(s, "release");
            name = required(b, "setName", String.class); parent = required(b, "setParent", sc);
            container = required(b, "setContainerLayer"); hidden = required(b, "setHidden", boolean.class);
            build = required(b, "build"); valid = required(sc, "isValid"); release = required(sc, "release");
            relative = required(t, "setRelativeLayer", sc, sc, int.class);
            layer = required(t, "setLayer", sc, int.class); crop = required(t, "setCrop", sc, Rect.class);
            position = required(t, "setPosition", sc, float.class, float.class);
            alpha = required(t, "setAlpha", sc, float.class); show = required(t, "show", sc); hide = required(t, "hide", sc);
            remove = required(t, "remove", sc); apply = required(t, "apply");
            applySync = required(t, "apply", boolean.class); close = required(t, "close");
        }
        Object create(Object owner, String label) throws ReflectiveOperationException {
            Object b = builder.newInstance(); name.invoke(b, label); parent.invoke(b, owner); hidden.invoke(b, true);
            container.invoke(b);
            return build.invoke(b);
        }
        Object createBuffer(Object owner, int width, int height) throws ReflectiveOperationException {
            Object b = builder.newInstance(); name.invoke(b, "C17 cached wallpaper feather");
            parent.invoke(b, owner); hidden.invoke(b, true); bufferSize.invoke(b, width, height);
            // Builder defaults to PixelFormat.OPAQUE. Feather requires real per-pixel alpha.
            format.invoke(b, 1); opaque.invoke(b, false); // PixelFormat.RGBA_8888
            return build.invoke(b);
        }
        boolean valid(Object value) throws ReflectiveOperationException { return value != null && Boolean.TRUE.equals(valid.invoke(value)); }
        void close(Object t) { if (t != null) try { close.invoke(t); } catch (ReflectiveOperationException | RuntimeException ignored) { } }
    }

    /** Same scoped wallpaper-layer capture used by Engine.freeze; never captureDisplay. */
    private static final class CaptureAccess {
        final Constructor<?> builder;
        final Method children, exclude, sourceCrop, uid, build, capture, bitmap, hardware, secure, hdr, closeHardware;
        final Class<?> surfaceClass;
        CaptureAccess() throws ReflectiveOperationException {
            surfaceClass = Class.forName("android.view.SurfaceControl");
            Class<?> api = Class.forName("android.window.ScreenCaptureInternal");
            Class<?> args = Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs");
            Class<?> b = Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs$Builder");
            Class<?> result = Class.forName("android.window.ScreenCaptureInternal$ScreenshotHardwareBuffer");
            Class<?> hw = Class.forName("android.hardware.HardwareBuffer");
            builder = b.getDeclaredConstructor(surfaceClass); builder.setAccessible(true);
            children = required(b, "setChildrenOnly", boolean.class);
            exclude = required(b, "setExcludeLayers", Array.newInstance(surfaceClass, 0).getClass());
            sourceCrop = required(b, "setSourceCrop", Rect.class); uid = required(b, "setUid", long.class);
            build = required(b, "build"); capture = required(api, "captureLayers", args);
            bitmap = required(result, "asBitmap"); hardware = required(result, "getHardwareBuffer");
            secure = required(result, "containsSecureLayers"); hdr = required(result, "containsHdrLayers");
            closeHardware = required(hw, "close");
        }
        Object sample(Host host, Rect rect) throws ReflectiveOperationException {
            Object b = builder.newInstance(host.parent);
            children.invoke(b, true); sourceCrop.invoke(b, rect); uid.invoke(b, (long)android.os.Process.myUid());
            Object excluded = Array.newInstance(surfaceClass, 1); Array.set(excluded, 0, host.group);
            exclude.invoke(b, excluded);
            return capture.invoke(null, build.invoke(b));
        }
        void close(Object buffer) { if (buffer != null) try { closeHardware.invoke(buffer); } catch (ReflectiveOperationException | RuntimeException ignored) { } }
    }

    synchronized void configure(boolean enabled, boolean crossWindowEnabled, float visibility,
                                float radius, float range, float transition, int color) {
        float value = LockscreenBlurGeometry.finite(visibility, 0f, 0f, 1f);
        boolean geometry = this.radius != radius || this.range != range || this.transition != transition || this.color != color
                || this.crossWindowEnabled != crossWindowEnabled;
        boolean changed = this.enabled != enabled || this.crossWindowEnabled != crossWindowEnabled || this.visibility != value || geometry;
        this.enabled = enabled; this.crossWindowEnabled = crossWindowEnabled; this.visibility = value;
        this.radius = radius; this.range = range; this.transition = transition; this.color = color;
        if (geometry) revision++;
        if (!changed) return;
        if (!enabled) { for (Host host : new ArrayList<>(hosts.values())) release(host); return; }
        for (Host host : new ArrayList<>(hosts.values())) sync(host);
    }
    synchronized void onEngine(Object engine) {
        // The current SystemUI scope owns these real static wallpaper engines.
        // An external live-wallpaper process is NOT substituted with a foreground blur.
        if (engine == null || namedClass(engine.getClass(), CANVAS_ENGINE) == null) return;
        Host host = hosts.get(engine);
        if (host == null) {
            EngineAccess a = access.get(engine.getClass());
            if (a == null) { a = new EngineAccess(engine.getClass()); access.put(engine.getClass(), a); }
            if (!a.ready) { fallback++; return; }
            host = new Host(engine, a); hosts.put(engine, host);
        }
        sync(host);
    }
    synchronized void onDestroyed(Object engine) {
        Host host = hosts.remove(engine); if (host != null) release(host);
    }
    /** The native wallpaper has posted a new image. Rebuild only its cached GPU area. */
    synchronized void onNativeFrame(Object engine) {
        Host host = hosts.get(engine);
        if (host == null) { onEngine(engine); return; }
        host.awaitingSource = false;
        host.sourceDirty = true; sync(host);
    }
    /** Engine.captureLayers includes all wallpaper children. Remove only OUR effects
     * synchronously before its native snapshot, otherwise blur/mask are baked twice.
     * This runs only on native freeze/screenshot events, never every frame.
     */
    synchronized void beforeNativeSnapshot(Object engine) {
        Host host = hosts.get(engine);
        if (host == null || host.group == null || surface == null) return;
        host.capturing = true;
        Object t = null;
        try {
            if (surface.valid(host.group)) {
                t = surface.transaction.newInstance(); surface.hide.invoke(t, host.group);
                surface.applySync.invoke(t, true); host.alpha = -1f; snapshots++;
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            Object parent = host.parent; release(host); host.parent = parent; host.unsupported = true; fallback++;
        }
        finally { surface.close(t); }
    }
    synchronized void afterNativeSnapshot(Object engine) {
        Host host = hosts.get(engine); if (host == null) return;
        host.capturing = false; sync(host);
    }

    private void sync(Host host) {
        Object engine = host.engine.get();
        if (!enabled || engine == null) { release(host); return; }
        Object t = null;
        try {
            EngineAccess a = host.access;
            if (a.destroyed.getBoolean(engine) || Boolean.TRUE.equals(a.preview.invoke(engine))
                    || (((Number)a.flags.invoke(engine)).intValue() & 2) == 0) { release(host); return; }
            if (surface == null) surface = new SurfaceAccess();
            Object parent = a.parent.get(engine);
            if (!surface.valid(parent)) { release(host); return; }
            if (host.parent != parent) { release(host); host.parent = parent; host.unsupported = false; host.awaitingSource = false; }
            if (host.unsupported || host.capturing || host.awaitingSource) return;
            Object snapshot = a.screenshot.get(engine);
            boolean frozen = surface.valid(snapshot);
            boolean visible = Boolean.TRUE.equals(a.visible.invoke(engine))
                    || frozen && a.visibleRequested != null && a.visibleRequested.getBoolean(engine);
            float target = visible ? visibility : 0f;
            if (target <= 0f) { hide(host); return; }
            Object context = a.context.invoke(engine);
            if (!(context instanceof Context)) { hide(host); return; }
            Context displayContext = (Context)context;
            // The native CanvasEngine buffer is wallpaper-sized (setFixedSize), not
            // screen-sized. Blur stays at the display's upper edge, outside its zoom wrapper.
            Point size = displaySize(displayContext);
            if (size == null || size.x <= 0 || size.y <= 0) { hide(host); return; }
            // WMS can scale the ENTIRE wallpaper window, including our child. Only
            // the audited 1:1 full-screen mapping is supported; transient rotation
            // or an unknown live/zoomed mapping hides effects instead of blurring
            // the wrong area. This is not a sticky API failure and recovers on event.
            if (!identityViewport(host, engine, size)) { hide(host); return; }
            int x = 0, y = 0;
            Object anchor = frozen ? snapshot : a.transform.get(engine);
            if (!surface.valid(anchor)) { hide(host); return; }
            float density = displayContext.getResources().getDisplayMetrics().density;
            boolean geometry = host.plan == null || host.revision != revision || host.width != size.x
                    || host.height != size.y || host.density != density || host.x != x || host.y != y;
            boolean order = host.anchor != anchor;
            if (!geometry && !order && !host.sourceDirty && host.alpha == target) return;
            LockscreenBlurGeometry.Plan plan = geometry
                    ? LockscreenBlurGeometry.plan(size.y, density, radius, range, transition) : host.plan;
            if (plan.size() == 0) { hide(host); return; }
            if (host.buffer == null || host.width != size.x || host.plan == null || host.plan.end != plan.end) {
                Object borrowedParent = parent;
                release(host); host.parent = borrowedParent;
                host.group = surface.create(parent, "C17 wallpaper status blur"); created++;
                host.buffer = surface.createBuffer(host.group, size.x, plan.end); created++;
                host.canvasSurface = surface.canvasSurface.newInstance(host.buffer);
                host.renderer = new WallpaperBlurRenderer(); host.sourceDirty = true;
                geometry = order = true;
            }
            boolean render = geometry || order || host.sourceDirty;
            if (order) host.sourceDirty = true;
            if (render) renderWallpaper(host, plan, size.x, size.y, crossWindowEnabled ? plan.radius : 0);
            t = surface.transaction.newInstance();
            if (order) surface.relative.invoke(t, host.group, anchor, 1);
            if (geometry) {
                host.crop.set(0, 0, size.x, size.y); surface.crop.invoke(t, host.group, host.crop);
                surface.position.invoke(t, host.group, (float)x, (float)y);
                host.crop.set(0, 0, size.x, plan.end); surface.crop.invoke(t, host.buffer, host.crop);
                surface.layer.invoke(t, host.buffer, 0);
            }
            // The buffer already contains the continuous blur/mask alpha shader.
            // Native progress changes only compositor alpha; it never recomputes pixels.
            surface.alpha.invoke(t, host.group, target); surface.show.invoke(t, host.buffer);
            surface.show.invoke(t, host.group); surface.apply.invoke(t); transactions++;
            host.plan = plan; host.width = size.x; host.height = size.y; host.density = density; host.x = x; host.y = y;
            host.revision = revision; host.anchor = anchor; host.alpha = target;
        } catch (SourceUnavailableException pendingWallpaper) {
            Object parent = host.parent; release(host); host.parent = parent; host.awaitingSource = true; fallback++;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            Object parent = host.parent; release(host); host.parent = parent; host.unsupported = true; fallback++;
        } finally { if (surface != null) surface.close(t); }
    }
    private static final class SourceUnavailableException extends RuntimeException {
        SourceUnavailableException(String reason) { super(reason); }
    }
    private void renderWallpaper(Host host, LockscreenBlurGeometry.Plan plan, int width, int height, int blurRadius)
            throws ReflectiveOperationException {
        int sourceHeight = Math.min(height, plan.end + blurRadius * 3);
        if (blurRadius == 0) clearPixels(host);
        if (blurRadius > 0 && (host.sourceDirty || host.bitmap == null || host.bitmap.isRecycled()
                || host.bitmap.getWidth() != width || host.bitmap.getHeight() < sourceHeight)) {
            if (capture == null) capture = new CaptureAccess();
            Object result = capture.sample(host, new Rect(0, 0, width, sourceHeight)); captures++;
            Object hardware = result == null ? null : capture.hardware.invoke(result);
            Bitmap bitmap = null;
            boolean accepted = false;
            try {
                if (result == null || hardware == null || Boolean.TRUE.equals(capture.secure.invoke(result))
                        || Boolean.TRUE.equals(capture.hdr.invoke(result)))
                    throw new SourceUnavailableException("Unsupported isolated wallpaper buffer");
                Object pixels = capture.bitmap.invoke(result);
                if (!(pixels instanceof Bitmap)) throw new SourceUnavailableException("Missing wallpaper hardware bitmap");
                bitmap = (Bitmap)pixels;
                if (bitmap.isRecycled() || bitmap.getConfig() != Bitmap.Config.HARDWARE
                        || bitmap.getWidth() != width || bitmap.getHeight() < sourceHeight)
                    throw new SourceUnavailableException("Unexpected wallpaper buffer geometry");
                clearPixels(host); host.bitmap = bitmap; host.hardwareBuffer = hardware; accepted = true;
            } finally {
                if (!accepted) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); capture.close(hardware); }
            }
        }
        host.renderer.record(host.bitmap, width, plan, blurRadius, color);
        Object value = surface.lockCanvas.invoke(host.canvasSurface);
        if (!(value instanceof Canvas)) throw new IllegalStateException("Missing wallpaper hardware canvas");
        Canvas canvas = (Canvas)value;
        try { host.renderer.draw(canvas); }
        finally { surface.postCanvas.invoke(host.canvasSurface, canvas); }
        renders++; host.sourceDirty = false;
    }
    private void clearPixels(Host host) {
        if (host.renderer != null) host.renderer.release();
        if (host.bitmap != null && !host.bitmap.isRecycled()) host.bitmap.recycle();
        if (capture != null) capture.close(host.hardwareBuffer);
        host.bitmap = null; host.hardwareBuffer = null;
    }
    private void hide(Host host) {
        if (host.group == null || host.alpha == 0f || surface == null) return;
        Object t = null;
        try {
            if (surface.valid(host.group)) {
                t = surface.transaction.newInstance(); surface.hide.invoke(t, host.group); surface.apply.invoke(t); transactions++;
            }
            host.alpha = 0f;
        } catch (ReflectiveOperationException | RuntimeException unsupported) { release(host); }
        finally { surface.close(t); }
    }
    private void release(Host host) {
        clearPixels(host);
        if (surface != null) {
            Object t = null;
            try {
                if (surface.valid(host.group)) {
                    t = surface.transaction.newInstance(); surface.remove.invoke(t, host.group); surface.apply.invoke(t); transactions++;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            finally { surface.close(t); }
            if (host.canvasSurface != null) try { surface.releaseCanvas.invoke(host.canvasSurface); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
            releaseOwned(host.buffer);
            releaseOwned(host.group);
        }
        host.group = host.parent = host.anchor = host.buffer = host.canvasSurface = null; host.plan = null; host.renderer = null;
        host.sourceDirty = true;
        host.awaitingSource = false;
        host.alpha = -1f; host.revision = -1;
    }
    private void releaseOwned(Object value) {
        if (value != null) try { surface.release.invoke(value); } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    synchronized void releaseRuntime() {
        enabled = false; for (Host host : new ArrayList<>(hosts.values())) release(host);
        hosts.clear(); access.clear(); visibility = 0f;
    }
    synchronized String summary() {
        int active = 0; for (Host host : hosts.values()) if (host.alpha > 0f) active++;
        return "wallpaperEngines=" + hosts.size() + ",activeWallpaperLayers=" + active + ",ownedLayersCreated=" + created
                + ",transactions=" + transactions + ",nativeSnapshots=" + snapshots + ",nativeFallback=" + fallback
                + ",wallpaperGpuSamples=" + captures + ",cachedGpuRenders=" + renders;
    }
    private static Point displaySize(Context context) throws ReflectiveOperationException {
        Method getDisplay = method(context.getClass(), "getDisplay");
        Object display = getDisplay == null ? null : getDisplay.invoke(context);
        Method id = display == null ? null : method(display.getClass(), "getDisplayId");
        if (id == null || ((Number)id.invoke(display)).intValue() != 0) return null;
        Method size = display == null ? null : method(display.getClass(), "getRealSize", Point.class);
        if (size == null) return null;
        Point result = new Point(); size.invoke(display, result); return result;
    }
    private static boolean identityViewport(Host host, Object engine, Point display) throws ReflectiveOperationException {
        EngineAccess a = host.access;
        Object frames = a.frames.get(engine); Field field = frames == null ? null : field(frames.getClass(), "frame");
        Object frame = field == null ? null : field.get(frames);
        if (!(frame instanceof Rect)) return false;
        Rect viewport = (Rect)frame;
        if (viewport.left != 0 || viewport.top != 0 || viewport.right != display.x || viewport.bottom != display.y) return false;
        Object value = a.layout.get(engine);
        if (!(value instanceof WindowManager.LayoutParams) || !Boolean.FALSE.equals(a.zoomOut.invoke(engine))) return false;
        WindowManager.LayoutParams layout = (WindowManager.LayoutParams)value;
        Object insetValue = SURFACE_INSETS == null ? null : SURFACE_INSETS.get(layout);
        Rect insets = insetValue instanceof Rect ? (Rect)insetValue : null;
        if (insets == null || insets.left != 0 || insets.top != 0 || insets.right != 0 || insets.bottom != 0) return false;
        return (layout.flags & WindowManager.LayoutParams.FLAG_SCALED) == 0
                || layout.width > 0 && layout.height > 0 && layout.width == a.requestedWidth.getInt(engine)
                    && layout.height == a.requestedHeight.getInt(engine);
    }
    private static Class<?> namedClass(Class<?> type, String name) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) if (name.equals(at.getName())) return at;
        return null;
    }
    private static Method required(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method value = method(type, name, parameters);
        if (value == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return value;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) try {
            Method value = at.getDeclaredMethod(name, parameters); value.setAccessible(true); return value;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
    private static Field field(Class<?> type, String name) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) try {
            Field value = at.getDeclaredField(name); value.setAccessible(true); return value;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
}
