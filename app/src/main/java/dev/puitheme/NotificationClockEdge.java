// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** A local notification boundary; native drawing and touch remain owned by the stack. */
public final class NotificationClockEdge {
    public interface DrawAction { Object draw(Canvas canvas) throws Throwable; }
    private static final float EDGE_DP = 24f, BLUR_DP = 8f;
    private static final int MAX_WIDTH = 4096, MAX_HEIGHT = 8192;
    private static final long MAX_SOURCE_PIXELS = 8388608L, MAX_EDGE_PIXELS = 1048576L;
    private static final float[] STOPS = {0f, .125f, .25f, .375f, .5f, .625f, .75f, .875f, 1f};
    private static final int[] INCREASING = maskColors(false), DECREASING = maskColors(true);
    private static final Map<View, Entry> ENTRIES = new WeakHashMap<>();
    private static final Map<Class<?>, NativeAccess> NATIVE_ACCESS = new WeakHashMap<>();
    private static final Map<Class<?>, FadeAccess> FADE_ACCESS = new WeakHashMap<>();
    private static final Map<Class<?>, RowAccess> ROW_ACCESS = new WeakHashMap<>();
    private static final String NATIVE_FADE = "com.oplus.systemui.statusbar.notification.stack.NotificationStackScrollLayoutControllerExtImpl$TopFadeDrawer";
    private static final String NATIVE_ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private NotificationClockEdge() { }

    private static int[] maskColors(boolean decreasing) {
        int[] colors = new int[STOPS.length];
        for (int i = 0; i < STOPS.length; i++) {
            float t = STOPS[i], alpha = t * t * (3f - 2f * t);
            colors[i] = (Math.round((decreasing ? 1f - alpha : alpha) * 255f) << 24) | 0xffffff;
        }
        return colors;
    }

    private static final class Entry {
        final Paint fade = maskPaint(), sharp = maskPaint(), blur = maskPaint();
        final Rect visible = new Rect();
        final Rect geometryClip = new Rect();
        final Rect rowClip = new Rect();
        final Matrix rowToStack = new Matrix();
        final float[] corners = new float[8];
        float maskTop = Float.NaN, maskBottom = Float.NaN;
        int width, height;
        boolean busy, blurFailed, loggedFailure, recoveryRequested;
        Object renderer;
        void masks(float top, float bottom) {
            if (maskTop == top && maskBottom == bottom) return;
            maskTop = top; maskBottom = bottom;
            Shader ramp = new LinearGradient(0f, top, 0f, bottom, INCREASING, STOPS, Shader.TileMode.CLAMP);
            fade.setShader(ramp); sharp.setShader(ramp);
            blur.setShader(new LinearGradient(0f, top, 0f, bottom, DECREASING, STOPS, Shader.TileMode.CLAMP));
        }
    }

    private static final class Geometry {
        final int width, height, captureTop, captureHeight;
        final int left, right;
        final float hardTop, top, bottom, radius;
        Geometry(View stack, Canvas canvas, float hardTop, float safeTop, float edgeRange, float blurRadius, Rect clip) {
            boolean clipped = canvas.getClipBounds(clip);
            // Native horizontal transitions and glass shadows can extend beyond stack.width.
            // Preserve the existing viewport instead of introducing a new vertical side wall.
            left = clipped ? Math.min(0, clip.left) : 0;
            right = clipped ? Math.max(stack.getWidth(), clip.right) : stack.getWidth();
            width = right - left;
            height = Math.max(stack.getHeight(), canvas.getHeight());
            this.hardTop = Math.max(0f, Math.min(height, hardTop));
            bottom = Math.max(this.hardTop, Math.min(height, safeTop));
            float range = finite(edgeRange) ? Math.max(0f, edgeRange) : 0f;
            top = Math.max(this.hardTop, bottom - range);
            radius = finite(blurRadius) ? Math.max(0f, Math.min(48f, blurRadius)) : 0f;
            int padding = (int) Math.ceil(radius * 3f + 2f);
            captureTop = Math.max(0, (int) Math.floor(top) - padding);
            int captureBottom = Math.min(height, (int) Math.ceil(bottom) + padding);
            captureHeight = Math.max(0, captureBottom - captureTop);
        }
        boolean sourceSafe() {
            return width > 0 && height > 0 && width <= MAX_WIDTH && height <= MAX_HEIGHT
                    && (long) width * height <= MAX_SOURCE_PIXELS;
        }
        boolean blurSafe() { return radius > 0f && sourceSafe() && captureHeight > 0 && (long) width * captureHeight <= MAX_EDGE_PIXELS; }
    }

    /**
     * Replace only this native invocation's Canvas: c -> chain.proceed(new Object[]{c}).
     * Nested dispatchDraw/drawDispatchedChildren hooks pass straight through on the same stack.
     */
    public static Object draw(View stack, Canvas canvas, float clipTop, float density, DrawAction nativeDraw) throws Throwable {
        return draw(stack, canvas, clipTop, density, nativeDraw, false);
    }

    /** Legacy density entry; capture permission never controls whether the spatial edge is active. */
    public static Object draw(View stack, Canvas canvas, float clipTop, float density, DrawAction nativeDraw,
            boolean allowCapture) throws Throwable {
        float d = finite(density) ? Math.max(0f, Math.min(8f, density)) : 0f;
        return draw(stack, canvas, clipTop, clipTop + EDGE_DP * d, EDGE_DP * d,
                BLUR_DP * d, nativeDraw, allowCapture);
    }

    /**
     * H is the visible header bottom, B is H plus the requested safe distance. Only notification
     * pixels entering [max(H, B-range), B] soften. This is independent of scroll/gesture state;
     * allowCapture is false during native horizontal paging, where the original Canvas fades.
     */
    public static Object draw(View stack, Canvas canvas, float hardTop, float safeTop, float edgeRange,
            float blurRadius, DrawAction nativeDraw, boolean allowCapture) throws Throwable {
        Objects.requireNonNull(nativeDraw);
        if (stack == null || canvas == null || !finite(hardTop) || !finite(safeTop))
            return nativeDraw.draw(canvas);
        Entry entry;
        try {
            synchronized (ENTRIES) {
                entry = ENTRIES.get(stack);
                if (entry == null) { entry = new Entry(); ENTRIES.put(stack, entry); }
            }
        } catch (Throwable unavailable) { return nativeDraw.draw(canvas); }
        NativeCall call = new NativeCall(nativeDraw);
        boolean acquired;
        synchronized (entry) { acquired = !entry.busy; if (acquired) entry.busy = true; }
        if (!acquired) return call.draw(canvas);
        NativeBoundary boundary = null;
        try {
            Geometry geometry = new Geometry(stack, canvas, hardTop, safeTop, edgeRange, blurRadius, entry.geometryClip);
            // This invocation owns one spatial edge. A fixed native TopFade would otherwise fog
            // a fully visible first notification, or add a second fade over the same pixels.
            boundary = nativeBoundary(stack, true);
            if (entry.width != geometry.width || entry.height != geometry.height) {
                closeRenderer(entry); entry.blurFailed = false; entry.recoveryRequested = false;
                entry.width = geometry.width; entry.height = geometry.height;
            }
            if (geometry.bottom <= geometry.top || !notificationInBand(stack, canvas, geometry, entry)) {
                closeRenderer(entry);
                return drawClipped(canvas, geometry, call);
            }
            // Unknown native drawing and an already active full-tree blur remain native. The
            // precise TopFade above is replaced locally, never a global glass/background effect.
            if (!boundary.known || boundary.nativeBlur || !geometry.sourceSafe()) {
                closeRenderer(entry);
                return drawClipped(canvas, geometry, call);
            }
            entry.masks(geometry.top, geometry.bottom);
            if (allowCapture && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()
                    && !entry.blurFailed && geometry.blurSafe())
                return Api31.draw(stack, canvas, geometry, entry, call);
            closeRenderer(entry);
            return drawFade(canvas, geometry, entry, call);
        } catch (Throwable unavailable) {
            // Preserve native exceptions, but never turn an optional graphics failure into a crash.
            if (call.nativeFailure != null) throw call.nativeFailure;
            entry.blurFailed = true; failure(entry, "Notification edge unavailable; retaining native drawing", unavailable);
            if (!call.started) return call.draw(canvas);
            recover(stack, entry);
            return call.result;
        } finally {
            if (boundary != null) try { boundary.restore(); }
            catch (Throwable unavailable) { failure(entry, "Native notification edge restore unavailable", unavailable); }
            synchronized (entry) { entry.busy = false; }
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** Native visible row bounds, including clipping and all ancestor transforms, in stack space. */
    private static boolean notificationInBand(View stack, Canvas canvas, Geometry geometry, Entry entry) {
        if (!(stack instanceof ViewGroup)) return false;
        boolean canvasClip = canvas.getClipBounds(entry.visible);
        ViewGroup group = (ViewGroup) stack;
        for (int i = 0, count = group.getChildCount(); i < count; i++) {
            View row = group.getChildAt(i);
            if (row == null || row.getVisibility() != View.VISIBLE || row.getAlpha() <= 0f || row.getWidth() <= 0)
                continue;
            try {
                RowAccess access;
                synchronized (ROW_ACCESS) {
                    access = ROW_ACCESS.get(row.getClass());
                    if (access == null) { access = new RowAccess(row.getClass()); ROW_ACCESS.put(row.getClass(), access); }
                }
                if (!access.notification || Boolean.TRUE.equals(invoke(access.childInGroup, row))) continue;
                int height = number(access.height, row, row.getHeight());
                if (height <= 0) continue;
                float top = Math.max(0, number(access.clipTop, row, 0));
                float bottom = height - Math.max(0, number(access.clipBottom, row, 0));
                float left = 0f, right = row.getWidth();
                if (row.getClipBounds(entry.rowClip)) {
                    left = Math.max(left, entry.rowClip.left); right = Math.min(right, entry.rowClip.right);
                    top = Math.max(top, entry.rowClip.top); bottom = Math.min(bottom, entry.rowClip.bottom);
                }
                if (right <= left || bottom <= top) continue;
                float[] p = entry.corners;
                p[0] = left; p[1] = top; p[2] = right; p[3] = top;
                p[4] = right; p[5] = bottom; p[6] = left; p[7] = bottom;
                entry.rowToStack.reset();
                row.transformMatrixToGlobal(entry.rowToStack);
                stack.transformMatrixToLocal(entry.rowToStack);
                entry.rowToStack.mapPoints(p);
                float minX = p[0], maxX = p[0], minY = p[1], maxY = p[1];
                for (int c = 0; c < p.length; c += 2) {
                    if (!finite(p[c]) || !finite(p[c + 1])) { minY = Float.NaN; break; }
                    minX = Math.min(minX, p[c]); maxX = Math.max(maxX, p[c]);
                    minY = Math.min(minY, p[c + 1]); maxY = Math.max(maxY, p[c + 1]);
                }
                if (!finite(minY)) continue;
                if (canvasClip) {
                    if (maxX <= entry.visible.left || minX >= entry.visible.right) continue;
                    minY = Math.max(minY, entry.visible.top); maxY = Math.min(maxY, entry.visible.bottom);
                }
                // A row entirely behind the hard boundary cannot trigger an effect over another
                // full card. The band is driven by its actual visible pixels, never ownScrollY.
                if (maxY > Math.max(geometry.hardTop, minY) && minY < geometry.bottom) return true;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                // Unsupported rows retain native drawing; shelf/footer/media never enter this path.
            }
        }
        return false;
    }

    private static final class RowAccess {
        final boolean notification;
        final Method height, clipTop, clipBottom, childInGroup;
        RowAccess(Class<?> type) {
            boolean found = false;
            for (Class<?> c = type; c != null; c = c.getSuperclass())
                if (NATIVE_ROW.equals(c.getName())) { found = true; break; }
            notification = found;
            height = found ? method(type, "getActualHeight") : null;
            clipTop = found ? method(type, "getClipTopAmount") : null;
            clipBottom = found ? method(type, "getClipBottomAmount") : null;
            childInGroup = found ? method(type, "isChildInGroup") : null;
        }
    }

    private static Method method(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try {
            Method value = type.getDeclaredMethod(name); value.setAccessible(true); return value;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Object invoke(Method method, Object owner) throws ReflectiveOperationException {
        return method == null ? null : method.invoke(owner);
    }

    private static int number(Method method, Object owner, int fallback) throws ReflectiveOperationException {
        Object value = invoke(method, owner);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /** Only the exact OEM fade drawer is scoped; no row, native setting or global controller changes. */
    private static final class NativeAccess {
        final Field drawer, nativeBlur;
        NativeAccess(Class<?> type) {
            drawer = field(type, "mOplusDispatchDrawHook");
            nativeBlur = field(type, "mBlurEffect");
        }
    }

    private static final class FadeAccess {
        final Field height;
        FadeAccess(Class<?> type) {
            Field candidate = NATIVE_FADE.equals(type.getName()) ? field(type, "fadeHeight") : null;
            height = candidate != null && candidate.getType() == Integer.TYPE ? candidate : null;
        }
    }

    private static final class NativeBoundary {
        final boolean known, nativeBlur;
        final Object drawer;
        final Field fade;
        final int fadeHeight;
        final boolean suppressed;
        NativeBoundary(boolean known, boolean nativeBlur, Object drawer, Field fade, int height,
                boolean suppressed) {
            this.known = known; this.nativeBlur = nativeBlur; this.drawer = drawer;
            this.fade = fade; fadeHeight = height; this.suppressed = suppressed;
        }
        void restore() throws IllegalAccessException {
            // A native writer inside the invocation wins; only restore our own temporary zero.
            if (suppressed && fade.getInt(drawer) == 0) fade.setInt(drawer, fadeHeight);
        }
    }

    private static NativeBoundary nativeBoundary(View stack, boolean suppressFade) {
        try {
            NativeAccess access;
            synchronized (NATIVE_ACCESS) {
                access = NATIVE_ACCESS.get(stack.getClass());
                if (access == null) { access = new NativeAccess(stack.getClass()); NATIVE_ACCESS.put(stack.getClass(), access); }
            }
            boolean blur = access.nativeBlur != null && access.nativeBlur.get(stack) != null;
            if (access.drawer == null) return new NativeBoundary(false, blur, null, null, 0, false);
            Object drawer = access.drawer.get(stack);
            if (drawer == null) return new NativeBoundary(true, blur, null, null, 0, false);
            FadeAccess nativeFade;
            synchronized (FADE_ACCESS) {
                nativeFade = FADE_ACCESS.get(drawer.getClass());
                if (nativeFade == null) { nativeFade = new FadeAccess(drawer.getClass()); FADE_ACCESS.put(drawer.getClass(), nativeFade); }
            }
            Field fade = nativeFade.height;
            if (fade == null)
                return new NativeBoundary(false, blur, null, null, 0, false);
            int height = Math.max(0, fade.getInt(drawer));
            boolean suppress = suppressFade && height > 0;
            NativeBoundary boundary = new NativeBoundary(true, blur, drawer, fade, height, suppress);
            if (suppress) fade.setInt(drawer, 0);
            return boundary;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return new NativeBoundary(false, false, null, null, 0, false);
        }
    }

    private static Field field(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try {
            Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    /** Single dispatch even if optional capture, composition or restore fails after native drawing. */
    private static final class NativeCall implements DrawAction {
        final DrawAction action;
        boolean started;
        Object result;
        Throwable nativeFailure;
        NativeCall(DrawAction action) { this.action = action; }
        @Override public Object draw(Canvas canvas) throws Throwable {
            if (started) {
                if (nativeFailure != null) throw nativeFailure;
                return result;
            }
            started = true;
            try { result = action.draw(canvas); return result; }
            catch (Throwable failure) { nativeFailure = failure; throw failure; }
        }
    }

    /** Discard display lists at stack detach; this helper never adds a timer or animation. */
    public static void detach(View stack) {
        Entry entry;
        synchronized (ENTRIES) { entry = ENTRIES.remove(stack); }
        if (entry != null) closeRenderer(entry);
    }

    /** Feature disable can release every source and blur node without waiting for a detach. */
    public static void releaseResources() {
        ArrayList<Entry> entries;
        synchronized (ENTRIES) { entries = new ArrayList<>(ENTRIES.values()); ENTRIES.clear(); }
        for (Entry entry : entries) closeRenderer(entry);
    }

    private static void recover(View stack, Entry entry) {
        if (!entry.recoveryRequested) { entry.recoveryRequested = true; stack.invalidate(); }
    }

    private static Paint maskPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE); paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        return paint;
    }

    private static Object drawClipped(Canvas canvas, Geometry geometry, DrawAction nativeDraw) throws Throwable {
        int save = canvas.save();
        try {
            Rect nativeClip = new Rect();
            if (canvas.getClipBounds(nativeClip))
                canvas.clipRect(nativeClip.left, geometry.hardTop, nativeClip.right, nativeClip.bottom);
            return nativeDraw.draw(canvas);
        } finally { canvas.restoreToCount(save); }
    }

    /** Software fallback masks only the narrow edge; everything below it retains its native alpha. */
    private static Object drawFade(Canvas canvas, Geometry geometry, Entry entry, DrawAction nativeDraw) throws Throwable {
        int save = canvas.save();
        try {
            float left = geometry.left, top = geometry.hardTop, right = geometry.right, bottom = geometry.height;
            if (canvas.getClipBounds(entry.visible)) {
                left = entry.visible.left; top = Math.max(top, entry.visible.top);
                right = entry.visible.right; bottom = entry.visible.bottom;
            }
            canvas.clipRect(left, top, right, bottom);
            if (right <= left || bottom <= top) return nativeDraw.draw(canvas);
            int layer;
            try { layer = canvas.saveLayer(left, top, right, bottom, null); }
            catch (Throwable unavailable) { failure(entry, "Notification edge fade layer unavailable", unavailable); return nativeDraw.draw(canvas); }
            try {
                Object result = nativeDraw.draw(canvas);
                // DST_IN must cover the whole layer. An antialiased rectangle at a fractional
                // boundary retains destination pixels through its partial geometric coverage.
                // CLAMP already leaves everything below the gradient fully opaque.
                try { canvas.drawPaint(entry.fade); }
                catch (Throwable unavailable) { failure(entry, "Notification edge fade unavailable", unavailable); }
                return result;
            } finally { canvas.restoreToCount(layer); }
        } finally { canvas.restoreToCount(save); }
    }

    private static void failure(Entry entry, String message, Throwable failure) {
        if (entry.loggedFailure) return;
        entry.loggedFailure = true;
        try { ModuleDiagnostics.error("notification-edge", message, failure); }
        catch (Throwable unavailable) { /* Diagnostics never alter native drawing. */ }
    }

    private static void closeRenderer(Entry entry) {
        if (entry.renderer != null && Build.VERSION.SDK_INT >= 31) {
            try { ((Api31.Renderer) entry.renderer).close(); }
            catch (Throwable unavailable) { failure(entry, "Notification edge display-list release unavailable", unavailable); }
        }
        entry.renderer = null;
    }

    /** Isolated so API 26-30 never load RenderEffect or RecordingCanvas implementations. */
    private static final class Api31 {
        private static final class Renderer {
            final RenderNode source = new RenderNode("C17NotificationEdgeSource");
            final RenderNode blurred = new RenderNode("C17NotificationEdgeBand");
            final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius = -1f;
            boolean outputDrawn;
            Renderer() {
                source.setClipToBounds(false); blurred.setClipToBounds(false);
                add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            }
            RecordingCanvas begin(Geometry geometry) {
                source.setPosition(geometry.left, 0, geometry.right, geometry.height);
                RecordingCanvas recording = source.beginRecording(geometry.width, geometry.height);
                recording.translate(-geometry.left, 0f);
                return recording;
            }
            void blur(Geometry geometry) {
                blurred.setPosition(geometry.left, geometry.captureTop, geometry.right, geometry.captureTop + geometry.captureHeight);
                if (radius != geometry.radius) {
                    blurred.setRenderEffect(RenderEffect.createBlurEffect(geometry.radius, geometry.radius, Shader.TileMode.DECAL));
                    radius = geometry.radius;
                }
                RecordingCanvas recording = blurred.beginRecording(geometry.width, geometry.captureHeight);
                try {
                    recording.clipRect(0f, 0f, geometry.width, geometry.captureHeight);
                    recording.translate(-geometry.left, -geometry.captureTop);
                    recording.drawRenderNode(source);
                } finally { blurred.endRecording(); }
            }
            void clear(Canvas target, Geometry geometry) {
                clear(target, geometry, geometry.height);
            }
            void clear(Canvas target, Geometry geometry, float bottom) {
                int save = target.save();
                try {
                    target.clipRect(geometry.left, geometry.hardTop, geometry.right, bottom);
                    target.drawRenderNode(source);
                } finally { target.restoreToCount(save); }
            }
            void compose(Canvas target, Geometry geometry, Entry entry) {
                outputDrawn = false;
                float left = geometry.left, right = geometry.right;
                float top = geometry.hardTop, bottom = geometry.height;
                if (target.getClipBounds(entry.visible)) {
                    left = Math.max(left, entry.visible.left); right = Math.min(right, entry.visible.right);
                    top = Math.max(top, entry.visible.top); bottom = Math.min(bottom, entry.visible.bottom);
                }
                if (right <= left || bottom <= top) return;
                int save = target.save();
                try {
                    target.clipRect(left, top, right, bottom);
                    // Publish one continuous notification surface. Separate sharp/band clips
                    // share a moving fractional edge and can overlap a glass highlight by a pixel.
                    int output = target.saveLayer(left, top, right, bottom, null);
                    int outputDepth = target.getSaveCount();
                    try {
                        target.drawRenderNode(source);
                        // CLAMP keeps sharp=1 below the band, so the original list stays intact.
                        target.drawPaint(entry.sharp);
                        float blurTop = Math.max(top, geometry.captureTop);
                        float blurBottom = Math.min(bottom, geometry.captureTop + geometry.captureHeight);
                        if (blurBottom > blurTop) {
                            int blur = target.saveLayer(left, blurTop, right, blurBottom, add);
                            try {
                                target.drawRenderNode(blurred);
                                // The complementary mask reaches zero before the capture edge.
                                target.drawPaint(entry.blur);
                            } finally { target.restoreToCount(blur); }
                        }
                        // Fade=1 below the band and 0 above it; no unmasked blur halo escapes.
                        // Apply alpha to every output pixel, without a second antialiased
                        // geometry edge that can preserve a thin native glass highlight.
                        target.drawPaint(entry.fade);
                    } catch (Throwable unavailable) {
                        entry.blurFailed = true; failure(entry, "Notification edge blur composition unavailable", unavailable);
                        target.restoreToCount(outputDepth); target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        target.drawRenderNode(source);
                    } finally { target.restoreToCount(output); outputDrawn = true; }
                } finally { target.restoreToCount(save); }
            }
            void close() { source.discardDisplayList(); blurred.discardDisplayList(); blurred.setRenderEffect(null); }
        }

        static Object draw(View stack, Canvas target, Geometry geometry, Entry entry, DrawAction nativeDraw) throws Throwable {
            Renderer renderer;
            RecordingCanvas recording;
            try {
                if (entry.renderer == null) entry.renderer = new Renderer();
                renderer = (Renderer) entry.renderer; recording = renderer.begin(geometry);
            } catch (Throwable unavailable) {
                entry.blurFailed = true; failure(entry, "Notification edge source recording unavailable", unavailable);
                return drawFade(target, geometry, entry, nativeDraw);
            }
            Object result;
            try { result = nativeDraw.draw(recording); }
            catch (Throwable nativeFailure) {
                try { renderer.source.endRecording(); } catch (Throwable endFailure) { nativeFailure.addSuppressed(endFailure); }
                throw nativeFailure;
            }
            try { renderer.source.endRecording(); }
            catch (Throwable unavailable) {
                // Never replay an old frame after a failed recording: recover with one fresh fade draw.
                entry.blurFailed = true; failure(entry, "Notification edge source finalization unavailable", unavailable);
                closeRenderer(entry); recover(stack, entry); return result;
            }
            try { renderer.blur(geometry); }
            catch (Throwable unavailable) {
                entry.blurFailed = true; failure(entry, "Notification edge band recording unavailable", unavailable);
                try { renderer.clear(target, geometry); }
                catch (Throwable fallback) { failure(entry, "Notification edge clear-source fallback unavailable", fallback); recover(stack, entry); }
                return result;
            }
            try { renderer.compose(target, geometry, entry); }
            catch (Throwable unavailable) {
                entry.blurFailed = true; failure(entry, "Notification edge output unavailable", unavailable);
                try { if (!renderer.outputDrawn) renderer.clear(target, geometry); }
                catch (Throwable fallback) { failure(entry, "Notification edge clear-source fallback unavailable", fallback); recover(stack, entry); }
            }
            return result;
        }
    }
}
