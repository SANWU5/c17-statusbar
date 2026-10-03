// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.RuntimeShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Native notification/QS acrylic: removes optical highlights, retaining blur, shape and interaction. */
public final class C17HighlightRemoval {
    public static final String ENABLED = "c17_highlight_removal_enabled";
    public static final String NOTIFICATION_ENABLED = "c17_highlight_removal_notification_enabled";
    public static final String CONTROL_ENABLED = "c17_highlight_removal_control_center_enabled";
    public static final String UNIFORM_NOTIFICATION_ENABLED = "c17_notification_color_unified_enabled";
    public static final String BACKGROUND_ENABLED = "c17_acrylic_background_enabled";
    public static final String LIGHT_BACKGROUND = "c17_acrylic_light_background_color";
    public static final String DARK_BACKGROUND = "c17_acrylic_dark_background_color";
    public static final int DEFAULT_LIGHT_BACKGROUND = 0xb2f4f4f4;
    public static final int DEFAULT_DARK_BACKGROUND = 0xb22a2a2a;
    private static final String EDGE = "u_edgeArray", OPTICS = "u_opticsArray", INNER = "u_shadowArray";
    private static final String BASE = "com.oplus.posteffect.drawable.BaseDrawable";
    private static final String BLEND = "com.oplus.posteffect.drawable.BlendDrawable";
    private static final String MATERIAL = "com.oplus.posteffect.agsl.DrawableShader";
    private static final String STROKE_EFFECT = "com.oplus.posteffect.agsl.effects.GradientStrokeEffect";
    private static final String OPTICS_EFFECT = "com.oplus.posteffect.agsl.effects.OpticsEffect";
    private static final String INNER_EFFECT = "com.oplus.posteffect.agsl.effects.InnerShadowEffect";
    private static final String AUTO_BLUR = "com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable";
    private static final String MASK_BLUR = "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable";
    private static final String VIEW_BLUR_PROXY = "com.oplusos.systemui.common.blurability.ViewBlurProxy";
    private static final String PLATFORM_BLUR = "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable";
    private static final String WALLPAPER_BLUR = "com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable";
    private static final String SEEK_BAR = "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar";
    private static final String NOTIFICATION_BACKGROUND = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String NOTIFICATION_EXTENSION = "com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp";
    private static final String NOTIFICATION_ROW = "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String MEDIA_LIGHT = "com.oplus.systemui.qs.media.multilight.MultiLightDrawable";
    private static final String MEDIA_BACKGROUND = "com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable";
    private static final String MEDIA_SPOTLIGHT = "com.oplus.systemui.qs.media.QsMediaSpotLightHelper";
    private static final int UNKNOWN = 0, NOTIFICATION = 1, CONTROL = 2;
    private static final String[] SURFACE_TYPES = {
            "com.android.systemui.statusbar.notification.row.ActivatableNotificationView",
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
            "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
            "com.oplus.systemui.qs.base.tile.OplusQSTileBaseView",
            "com.oplus.systemui.qs.base.tile.OplusQSTileBaseView$SpotLightIconFrame",
            "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView",
            "com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout",
            "com.oplus.systemui.qs.base.spotlight.SpotLightImageButton",
            "com.oplus.systemui.qs.base.spotlight.SpotLightLinearLayout",
            "com.oplus.systemui.qs.base.spotlight.SpotLightConstraintLayout",
            "com.oplus.systemui.qs.OplusQsFooterSettingsSpotLightImageView",
            "com.oplus.systemui.qs.media.OplusQsMediaButton",
            "com.oplus.systemui.qs.media.OplusQsMediaOutputSpotlightLayout",
            "com.oplus.systemui.notification.clearall.OplusClearAllButton",
            "com.oplus.systemui.qs.widget.OplusQSMultiUserSwitch",
            "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
            "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView",
            "com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder$realPluginContainer$2$1",
            "com.oplus.systemui.plugins.qs.customize.view.viewholder.EditableBrightnessVolumeViewHolder$bvLayoutContainer$2$1",
            "com.oplus.deviceplugin.sdk.ui.view.separatecardview.d"};
    private static final int MAX_SCOPE_DEPTH = 64, MAX_SURFACE_TYPES = 512;
    private final Map<View, Boolean> surfaces = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, Binding> engines = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<RuntimeShader, Uniforms> shaders = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, EngineAccess> engineAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, MaterialAccess> materialAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, EffectAccess> effectAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> surfaceTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> scopeTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, BlurAccess> blurAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, ProxyAccess> proxyAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, WrapperAccess> wrapperAccess = new ConcurrentHashMap<>();
    private final Map<Class<?>, NotificationAccess> notificationAccess = new ConcurrentHashMap<>();
    private final Map<Object, BlurOwner> blurOwners = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, StrokePaint> sliderStrokes = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, Boolean> mediaLights = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, BlurOwner> mediaSpotHosts = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<NotificationTint> tintingNotification = new ThreadLocal<>();
    private final ThreadLocal<View> surface = new ThreadLocal<>();
    private final ThreadLocal<Drawable> recording = new ThreadLocal<>();
    // The outer native recording owns material policy. QS and media construct their
    // own shader in this same scope, so the innermost acrylic layer must preserve it.
    private static final ThreadLocal<RecordScope> ACTIVE_RECORD = new ThreadLocal<>();
    private final ThreadLocal<SurfacePool> surfacePool = new ThreadLocal<>();
    private final ThreadLocal<RecordPool> recordPool = new ThreadLocal<>();
    private final ThreadLocal<Boolean> restoring = new ThreadLocal<>();
    private final SurfaceScope noSurface = new SurfaceScope();
    private final RecordScope noRecord = new RecordScope();
    private volatile boolean enabled, released, restorationPending;
    private volatile long revision;
    private final C17AcrylicMaterial acrylic = new C17AcrylicMaterial();
    private volatile boolean backgroundEnabled;
    private volatile boolean notificationEnabled = true, controlEnabled = true, uniformNotificationEnabled;
    private volatile CardColors cardColors = new CardColors(DEFAULT_LIGHT_BACKGROUND, DEFAULT_DARK_BACKGROUND);
    private volatile int lightBackground = DEFAULT_LIGHT_BACKGROUND, darkBackground = DEFAULT_DARK_BACKGROUND;

    @FunctionalInterface
    public interface UniformWrite { Object write(float[] values) throws Throwable; }
    @FunctionalInterface
    public interface ContentDraw { Object draw(Canvas canvas) throws Throwable; }

    public boolean enabled() { return enabled; }

    static boolean removesOptics(Drawable engine) {
        RecordScope scope = ACTIVE_RECORD.get();
        return scope != null && scope.removesOptics(engine);
    }

    static void preparedCustomMaterial(Drawable engine, RuntimeShader shader, boolean opticsRemoved) {
        RecordScope scope = ACTIVE_RECORD.get();
        if (scope != null && opticsRemoved && scope.removesOptics(engine)) scope.customShader = shader;
    }

    /** Exact native hierarchies, resolved once per class for the existing View.draw hook. */
    public boolean isSurface(View host) {
        if (released || host == null) return false;
        Class<?> type = host.getClass(); Boolean cached = surfaceTypes.get(type);
        if (cached != null) return cached;
        if (surfaceTypes.size() >= MAX_SURFACE_TYPES) return false;
        boolean result = false;
        for (String name : SURFACE_TYPES) if (type(type, name)) { result = true; break; }
        surfaceTypes.putIfAbsent(type, result); return result;
    }

    /** Auto/Mask render outside one-argument View.draw on the hardware path. The actual
     * ViewBlurProxy retains the owner; do not ask the provider for a second blur drawable. */
    public SurfaceScope beginBlur(Drawable drawable) {
        if (released || drawable == null) return noSurface;
        BlurAccess access = blurAccess.get(drawable.getClass());
        if (access == null) {
            BlurAccess created = new BlurAccess(drawable.getClass());
            BlurAccess raced = blurAccess.putIfAbsent(drawable.getClass(),created);
            access = raced == null ? created : raced;
        }
        View host = blurOwner(get(access.proxy,drawable));
        return host == null ? noSurface : beginSurface(host);
    }

    /** Observe the one real ViewBlurProxy.getBlurDrawable result. Directly bind the
     * audited Platform/Wallpaper native engine before its first RenderNode recording. */
    public void onNativeBlurResult(Object proxy, Drawable drawable) {
        if (released || drawable == null) return;
        View host = blurOwner(proxy);
        bindReturned(host,drawable,0);
    }

    private View blurOwner(Object proxy) {
        if (proxy == null || !type(proxy.getClass(),VIEW_BLUR_PROXY)) return null;
        ProxyAccess access = proxyAccess.get(proxy.getClass());
        if (access == null) {
            ProxyAccess created = new ProxyAccess(proxy.getClass());
            ProxyAccess raced = proxyAccess.putIfAbsent(proxy.getClass(),created);
            access = raced == null ? created : raced;
        }
        Object nativeView = get(access.view,proxy);
        if (!(nativeView instanceof View)) return null;
        View view = (View)nativeView;
        BlurOwner cached = blurOwners.get(proxy);
        View host = cached == null ? null : cached.host.get();
        if (cached != null && cached.view.get() == view && host != null && host.isAttachedToWindow()
                && (view == host || view.getParent() == host)) return host;
        host = nearestSurface(view);
        if (host != null && host.isAttachedToWindow()) {
            blurOwners.put(proxy,new BlurOwner(view,host));
            return host;
        }
        blurOwners.remove(proxy);
        return null;
    }

    private View nearestSurface(View view) {
        for (int depth = 0; view != null && depth < 16; depth++) {
            if (isSurface(view)) return view;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View)parent : null;
        }
        return null;
    }

    private void bindReturned(View host,Drawable drawable,int depth) {
        if (depth > 4) return;
        EngineAccess access = engineAccess(drawable.getClass());
        if (access.valid()) {
            if (host == null) forgetEngine(drawable); else bindDrawable(host,drawable);
            return;
        }
        WrapperAccess wrapper = wrapperAccess.get(drawable.getClass());
        if (wrapper == null) {
            WrapperAccess created = new WrapperAccess(drawable.getClass());
            WrapperAccess raced = wrapperAccess.putIfAbsent(drawable.getClass(),created);
            wrapper = raced == null ? created : raced;
        }
        Object child = get(wrapper.engine,drawable);
        if (child instanceof Drawable && child != drawable) bindReturned(host,(Drawable)child,depth+1);
    }

    private void forgetEngine(Drawable engine) {
        Binding binding = engines.remove(engine);
        if (binding == null) return;
        acrylic.detach(engine); dirty(engine,binding);
        ArrayList<Map.Entry<RuntimeShader, Uniforms>> snapshot;
        synchronized (shaders) { snapshot = new ArrayList<>(shaders.entrySet()); }
        for (Map.Entry<RuntimeShader, Uniforms> entry : snapshot) {
            Uniforms state = entry.getValue();
            synchronized (state) {
                if (state.engine.get() != engine) continue;
                restore(entry.getKey(),state);
                if (!state.owned()) shaders.remove(entry.getKey());
            }
        }
    }

    /** Register exact notification/QS hosts even while OFF, so an existing RenderNode can refresh. */
    public SurfaceScope beginSurface(View host) {
        if (released || host == null) return noSurface;
        if (!surfaces.containsKey(host)) surfaces.put(host, Boolean.TRUE);
        SurfacePool pool = surfacePool.get();
        if (pool == null) { pool = new SurfacePool(); surfacePool.set(pool); }
        if (pool.depth == MAX_SCOPE_DEPTH) return noSurface;
        View previous = surface.get(); surface.set(host);
        SurfaceScope result = pool.acquire(); result.current = host; result.previous = previous; result.pool = pool; result.active = true;
        return result;
    }

    public final class SurfaceScope implements AutoCloseable {
        private View current, previous;
        private SurfacePool pool;
        private SurfaceScope next;
        private NotificationTint tint;
        private boolean active;
        private SurfaceScope() { }
        @Override public void close() {
            if (!active) return; active = false;
            if (surface.get() == current) { if (previous == null) surface.remove(); else surface.set(previous); }
            pool.depth--; current = null; previous = null; pool = null;
        }
    }

    /** Before BaseDrawable.draw(Canvas). Never scans a View/drawable tree. */
    public void bindDrawable(Drawable engine) {
        View host = surface.get();
        if (released || engine == null) return;
        if (host == null) {
            Binding known = engines.get(engine);
            host = known == null ? null : known.host.get();
            if (host == null) host = callbackSurface(engine);
            if (host == null || !host.isAttachedToWindow()) return;
        }
        bindDrawable(host,engine);
    }

    private View callbackSurface(Drawable engine) {
        Drawable current = engine;
        for (int depth = 0; depth < 16; depth++) {
            Drawable.Callback callback = current.getCallback();
            if (callback instanceof View) return nearestSurface((View)callback);
            if (!(callback instanceof Drawable) || callback == current) return null;
            current = (Drawable)callback;
        }
        return null;
    }

    private void bindDrawable(View host,Drawable engine) {
        if (!surfaces.containsKey(host)) surfaces.put(host,Boolean.TRUE);
        Binding binding = engines.get(engine);
        if (binding == null) {
            EngineAccess access = engineAccess(engine.getClass());
            if (!access.valid()) return;
            binding = new Binding(host, access); engines.put(engine, binding);
        } else if (binding.host.get() != host) {
            // A native engine can be recycled into a different page. Scoped removal
            // must restore the previous owner's uniforms before accepting the new one.
            restoreEngine(engine); binding.host = new WeakReference<>(host); binding.primed = Long.MIN_VALUE;
        }
        RuntimeShader shader = observe(engine, binding);
        if (opticsEnabled(host) && shader != null && binding.primed != revision) prime(engine, binding);
    }

    /** Wrap BlendDrawable.onDrawContent before other material helpers prepare replacement shaders. */
    public RecordScope beginRecord(Drawable engine) {
        Binding binding = engine == null ? null : engines.get(engine);
        if (released || binding == null || !opticsEnabled(binding.host.get()))
            return noRecord;
        observe(engine, binding);
        RecordPool pool = recordPool.get();
        if (pool == null) { pool = new RecordPool(); recordPool.set(pool); }
        if (pool.depth == MAX_SCOPE_DEPTH) return noRecord;
        Drawable previous = recording.get(); recording.set(engine);
        RecordScope result = pool.acquire(); result.current = engine; result.previous = previous; result.pool = pool; result.active = true;
        result.previousPolicy = ACTIVE_RECORD.get(); result.customShader = null; ACTIVE_RECORD.set(result);
        return result;
    }

    public final class RecordScope implements AutoCloseable {
        private Drawable current, previous;
        private RecordScope previousPolicy;
        private RuntimeShader customShader;
        private RecordPool pool;
        private RecordScope next;
        private boolean active;
        private RecordScope() { }
        private boolean removesOptics(Drawable engine) {
            Binding binding = current == null ? null : engines.get(current);
            return active && current == engine && !released && binding != null && opticsEnabled(binding.host.get());
        }
        @Override public void close() {
            if (!active) return; active = false;
            if (recording.get() == current) { if (previous == null) recording.remove(); else recording.set(previous); }
            if (ACTIVE_RECORD.get() == this) { if (previousPolicy == null) ACTIVE_RECORD.remove(); else ACTIVE_RECORD.set(previousPolicy); }
            pool.depth--; current = null; previous = null; pool = null; previousPolicy = null; customShader = null;
        }
    }

    /** Nested draw calls reuse one scope per depth after their first recording. */
    private final class SurfacePool {
        final SurfaceScope first = new SurfaceScope();
        int depth;
        SurfaceScope acquire() {
            SurfaceScope result = first;
            for (int i = 0; i < depth; i++) {
                if (result.next == null) result.next = new SurfaceScope();
                result = result.next;
            }
            depth++; return result;
        }
    }

    private final class RecordPool {
        final RecordScope first = new RecordScope();
        int depth;
        RecordScope acquire() {
            RecordScope result = first;
            for (int i = 0; i < depth; i++) {
                if (result.next == null) result.next = new RecordScope();
                result = result.next;
            }
            depth++; return result;
        }
    }

    /** Hook only RuntimeShader.setFloatUniform(String,float[]); caller arrays are never edited. */
    public Object writeUniform(RuntimeShader shader, String name, float[] values, UniformWrite nativeWrite) throws Throwable {
        int kind = kind(name, values);
        if (kind < 0 || released || Boolean.TRUE.equals(restoring.get()) || (!enabled && !restorationPending))
            return nativeWrite.write(values);
        Uniforms state = shaders.get(shader);
        if (state == null && enabled) {
            Drawable engine = recording.get(); Binding binding = engine == null ? null : engines.get(engine);
            if (binding != null && opticsEnabled(binding.host.get())) state = register(shader, engine);
        }
        if (state == null) return nativeWrite.write(values);
        synchronized (state) {
            Slot slot = state.get(kind);
            Drawable owner = state.engine.get(); Binding binding = owner == null ? null : engines.get(owner);
            if (binding == null || !opticsEnabled(binding.host.get())) {
                if (enabled) restore(shader, state);
                Object result = nativeWrite.write(values);
                if (slot != null) relinquish(slot);
                return result;
            }
            if (slot == null) {
                slot = new Slot(values.length);
                state.set(kind, slot);
            }
            long started = revision, ticket = ++slot.sequence;
            if (slot.depth == MAX_SCOPE_DEPTH) {
                Object result = nativeWrite.write(values);
                if (ticket > slot.successful) { slot.successful = ticket; relinquish(slot); }
                return result;
            }
            WriteBuffer buffer = slot.acquire();
            try {
                System.arraycopy(values, 0, buffer.pending, 0, values.length);
                System.arraycopy(values, 0, buffer.masked, 0, values.length);
                if (kind == 0) { buffer.masked[7] = 0f; buffer.masked[8] = 0f; }
                else if (kind == 1) buffer.masked[3] = 0f;
                else { buffer.masked[3] = 0f; buffer.masked[4] = 0f; buffer.masked[5] = 0f; }
                Object result = nativeWrite.write(buffer.masked);
                if (ticket > slot.successful) {
                    slot.successful = ticket;
                    System.arraycopy(buffer.pending, 0, slot.latest, 0, values.length);
                    slot.owned = true;
                    // A synchronous configuration change inside another hook cannot leave an OFF write owned.
                    if (!opticsEnabled(binding.host.get()) || started != revision) restore(shader, name, slot);
                }
                return result;
            } finally { slot.depth--; }
        }
    }

    /** Pure Spotlight draw calls only. Never disable a native session, touch handler or whole glass. */
    public boolean skipSpotlight(View host) {
        if (!enabled || released) return false;
        View current = surface.get();
        if (current != null && opticsEnabled(current)) return true;
        View owner = host == null ? null : isSurface(host) ? host : nearestSurface(host);
        return owner != null && surfaces.containsKey(owner) && opticsEnabled(owner);
    }

    /** Native media draws this separate optical layer after both background drawables.
     * Only the audited QS parent is accepted; it never suppresses its transition/cover draw. */
    public boolean skipNativeMediaLight(Drawable light) {
        if (released || light == null || !type(light.getClass(), MEDIA_LIGHT)) return false;
        Drawable.Callback callback = light.getCallback();
        if (!(callback instanceof Drawable) || !type(callback.getClass(), MEDIA_BACKGROUND)) return false;
        mediaLights.put(light, Boolean.TRUE);
        return enabled && controlEnabled;
    }

    /** Its Canvas-only draw can bypass View.draw on the hardware path. The final native
     * helper host is cached once, rather than searching its parents every frame. */
    public boolean skipNativeMediaSpotlight(Object helper) {
        if (released || helper == null || !type(helper.getClass(), MEDIA_SPOTLIGHT)) return false;
        BlurOwner cached = mediaSpotHosts.get(helper);
        View view = cached == null ? null : cached.view.get();
        View host = cached == null ? null : cached.host.get();
        if (view == null || host == null || !host.isAttachedToWindow() || view != host && view.getParent() != host) {
            Object candidate = get(field(helper.getClass(), "host"), helper);
            if (!(candidate instanceof View)) return false;
            view = (View)candidate; host = isSurface(view) ? view : nearestSurface(view);
            if (host == null || scope(host) != CONTROL) { mediaSpotHosts.remove(helper); return false; }
            mediaSpotHosts.put(helper, new BlurOwner(view, host));
        }
        return host.isAttachedToWindow() && opticsEnabled(host);
    }

    /** The final OEM draw chooses normal/colorized/MCS, platform/wallpaper blur,
     * material-color and stacked overlays. The AOSP draw is its fallback, not its caller. */
    public Object drawNotificationBackgroundExtension(Object extension, Canvas canvas, ContentDraw nativeDraw) throws Throwable {
        NotificationAccess access = notificationAccess(extension);
        Object value = access == null ? null : get(access.host, extension);
        return value instanceof View ? drawNotificationBackground((View)value, canvas, nativeDraw) : nativeDraw.draw(canvas);
    }

    /** Only the audited OEM drawIcon body is deferred: it does exactly zoomAppIcon.draw.
     * Draw that original icon after the background layer in the same call/frame. Retaining
     * a hook chain or mutating native icon fields would be unsafe. Text/content views are
     * never inside this layer; notification clicks and app colors remain native. */
    public boolean deferNotificationIcon(Object extension, Canvas canvas) {
        NotificationTint tint = tintingNotification.get();
        if (tint == null || tint.canvas != canvas || released) return false;
        NotificationAccess access = notificationAccess(extension);
        if (access == null || get(access.host, extension) != tint.host) return false;
        Object icon = get(access.icon, extension);
        if (!(icon instanceof Drawable) || tint.count == tint.icons.length) return false;
        tint.icons[tint.count++] = (Drawable)icon;
        return true;
    }

    /** Bounded native-card compositing covers all OEM background layers while keeping
     * their clipping, alpha and shape. OFF costs only the cached host scope check. */
    public Object drawNotificationBackground(View host, Canvas canvas, ContentDraw nativeDraw) throws Throwable {
        try (SurfaceScope owner = beginSurface(host)) {
            NotificationTint previous = tintingNotification.get();
            if (!uniformNotification(host) || !type(host.getClass(), NOTIFICATION_BACKGROUND)
                    || canvas == null || !owner.active || previous != null && previous.host == host && previous.canvas == canvas)
                return nativeDraw.draw(canvas);
            NotificationTint tint = owner.tint;
            if (tint == null) { tint = new NotificationTint(); owner.tint = tint; }
            if (!notificationBounds(host, tint.bounds)) return nativeDraw.draw(canvas);
            boolean night = (host.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Paint fill = night ? cardColors.dark : cardColors.light;
            final int saved;
            try { saved = canvas.saveLayer(tint.bounds, fill); }
            catch (RuntimeException unavailable) { return nativeDraw.draw(canvas); }
            tint.host = host; tint.canvas = canvas; tint.count = 0; tintingNotification.set(tint);
            try { return nativeDraw.draw(canvas); }
            finally {
                if (tintingNotification.get() == tint) {
                    if (previous == null) tintingNotification.remove(); else tintingNotification.set(previous);
                }
                try {
                    canvas.restoreToCount(saved);
                    for (int i = 0; i < tint.count; i++) tint.icons[i].draw(canvas);
                } finally {
                    for (int i = 0; i < tint.count; i++) tint.icons[i] = null;
                    tint.host = null; tint.canvas = null; tint.count = 0;
                }
            }
        }
    }

    private NotificationAccess notificationAccess(Object extension) {
        if (released || extension == null || !type(extension.getClass(), NOTIFICATION_EXTENSION)) return null;
        Class<?> type = extension.getClass(); NotificationAccess cached = notificationAccess.get(type);
        if (cached != null) return cached;
        if (notificationAccess.size() >= MAX_SURFACE_TYPES) return null;
        NotificationAccess created = new NotificationAccess(type, true);
        NotificationAccess raced = notificationAccess.putIfAbsent(type, created); return raced == null ? created : raced;
    }

    private boolean notificationBounds(View host, RectF bounds) {
        Class<?> type = host.getClass(); NotificationAccess access = notificationAccess.get(type);
        if (access == null) {
            if (notificationAccess.size() >= MAX_SURFACE_TYPES) return false;
            NotificationAccess created = new NotificationAccess(type, false);
            NotificationAccess raced = notificationAccess.putIfAbsent(type, created); access = raced == null ? created : raced;
        }
        if (access.width == null || access.height == null) return false;
        try {
            int actualWidth = (Integer)access.width.invoke(host), actualHeight = (Integer)access.height.invoke(host);
            int width = host.getWidth(), height = Math.max(host.getHeight(), actualHeight);
            if (actualWidth <= 0 || actualHeight <= 0 || width <= 0 || height <= 0) return false;
            // OEM expansion may center a wider card or align it at either edge. Include
            // that native extent, with the 1 px panoramic-AOD inset from the actual DEX.
            float left = Math.min(0f, width - actualWidth) - 1f;
            float right = Math.max(width, actualWidth) + 1f;
            bounds.set(left, -1f, right, height + 1f); return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return false; }
    }

    /** OplusQsVerticalSeekBar.onDraw also paints a separate Canvas path after the
     * native blur. Replace only its exact getStrokePaint return; never mutate the native Paint. */
    public Paint sliderStroke(View host,Paint nativePaint) {
        if (!opticsEnabled(host) || released || nativePaint == null || !type(host.getClass(),SEEK_BAR)) return nativePaint;
        StrokePaint cached = sliderStrokes.get(host);
        if (cached == null || cached.original.get() != nativePaint) {
            cached = new StrokePaint(nativePaint); sliderStrokes.put(host,cached);
        }
        return cached.masked;
    }

    /** Innermost onDrawContent wrapper, after native/QS/media fill preparation. */
    public Object drawContent(Drawable engine, Canvas canvas, ContentDraw nativeDraw) throws Throwable {
        Binding binding = engine == null ? null : engines.get(engine);
        View host = binding == null ? null : binding.host.get();
        if (released || !opticsEnabled(host) || uniformNotification(host)
                || !backgroundEnabled && !controlSurface(host)) return nativeDraw.draw(canvas);
        RecordScope record = ACTIVE_RECORD.get();
        if (record != null && record.removesOptics(engine) && record.customShader != null
                && QsTileAppearance.field(QsTileAppearance.field(engine,"drawableShader"),"shader") == record.customShader)
            return nativeDraw.draw(canvas);
        C17AcrylicMaterial.Swap swap = acrylic.prepare(engine, host, lightBackground, darkBackground,backgroundEnabled);
        try { return nativeDraw.draw(canvas); } finally { if (swap != null) swap.restore(); }
    }

    public synchronized void configure(Bundle settings) {
        if (released) return;
        boolean next = settings != null && Boolean.TRUE.equals(settings.get(ENABLED)) && !SafetyMode.enabled(settings);
        boolean nextNotification = settings == null || settings.getBoolean(NOTIFICATION_ENABLED, true);
        boolean nextControl = settings == null || settings.getBoolean(CONTROL_ENABLED, true);
        boolean nextUniform = settings != null && settings.getBoolean(UNIFORM_NOTIFICATION_ENABLED, false) && !SafetyMode.enabled(settings);
        boolean nextBackground = settings != null && Boolean.TRUE.equals(settings.get(BACKGROUND_ENABLED));
        int nextLight = color(settings, LIGHT_BACKGROUND, DEFAULT_LIGHT_BACKGROUND);
        int nextDark = color(settings, DARK_BACKGROUND, DEFAULT_DARK_BACKGROUND);
        if (enabled == next && backgroundEnabled == nextBackground && lightBackground == nextLight && darkBackground == nextDark
                && notificationEnabled == nextNotification && controlEnabled == nextControl && uniformNotificationEnabled == nextUniform) {
            if (!next && restorationPending) restoreAll();
            return;
        }
        if (!next) restorationPending = true;
        boolean changedScope = notificationEnabled != nextNotification || controlEnabled != nextControl;
        revision++; enabled = next; backgroundEnabled = nextBackground;
        notificationEnabled = nextNotification; controlEnabled = nextControl; uniformNotificationEnabled = nextUniform;
        if (lightBackground != nextLight || darkBackground != nextDark) cardColors = new CardColors(nextLight, nextDark);
        lightBackground = nextLight; darkBackground = nextDark;
        if (changedScope) restoreAll();
        if (next) {
            for (Map.Entry<Drawable, Binding> entry : engineSnapshot()) prime(entry.getKey(), entry.getValue());
        } else restoreAll();
        invalidateSurfaces();
        invalidateMediaLights();
    }

    public synchronized void release() {
        restorationPending = true; enabled = false; uniformNotificationEnabled = false; revision++;
        restoreAll(); invalidateSurfaces(); released = true;
        engines.clear(); shaders.clear(); surfaces.clear();
        engineAccess.clear(); materialAccess.clear(); effectAccess.clear(); surfaceTypes.clear(); scopeTypes.clear();
        blurAccess.clear(); proxyAccess.clear(); wrapperAccess.clear(); notificationAccess.clear(); blurOwners.clear(); sliderStrokes.clear();
        acrylic.clear();
        invalidateMediaLights(); mediaLights.clear(); mediaSpotHosts.clear(); tintingNotification.remove();
        surface.remove(); recording.remove(); surfacePool.remove(); recordPool.remove(); restoring.remove();
    }

    /** Optional native detach/recycle hook; a reused drawable must leave its previous host's ownership. */
    public void detach(View host) {
        if (host == null) return;
        surfaces.remove(host);
        sliderStrokes.remove(host);
        synchronized (blurOwners) {
            java.util.Iterator<Map.Entry<Object,BlurOwner>> owners = blurOwners.entrySet().iterator();
            while (owners.hasNext()) if (owners.next().getValue().host.get() == host) owners.remove();
        }
        ArrayList<Drawable> removed = new ArrayList<>();
        for (Map.Entry<Drawable, Binding> entry : engineSnapshot()) if (entry.getValue().host.get() == host) {
            Drawable engine = entry.getKey(); engines.remove(engine); removed.add(engine); acrylic.detach(engine); dirty(engine, entry.getValue());
        }
        ArrayList<Map.Entry<RuntimeShader, Uniforms>> snapshot;
        synchronized (shaders) { snapshot = new ArrayList<>(shaders.entrySet()); }
        for (Map.Entry<RuntimeShader, Uniforms> entry : snapshot) {
            if (entry.getKey() == null) continue;
            Uniforms state = entry.getValue();
            synchronized (state) {
                Drawable owner = state.engine.get();
                if (owner != null && !removed.contains(owner)) continue;
                restore(entry.getKey(), state);
                if (!state.owned()) shaders.remove(entry.getKey());
            }
        }
    }

    private static int kind(String name, float[] values) {
        if (values == null) return -1;
        if (EDGE.equals(name) && values.length == 16) return 0;
        if (OPTICS.equals(name) && values.length == 12) return 1;
        return INNER.equals(name) && values.length == 16 ? 2 : -1;
    }

    private RuntimeShader observe(Drawable engine, Binding binding) {
        Object material = get(binding.access.material, engine);
        if (material == null) return null;
        MaterialAccess access = materialAccess(material.getClass());
        Object value = get(access.shader, material);
        if (!(value instanceof RuntimeShader)) return null;
        RuntimeShader shader = (RuntimeShader) value;
        if (binding.observed == null || binding.observed.get() != shader) {
            binding.observed = new WeakReference<>(shader); binding.primed = Long.MIN_VALUE;
            register(shader, engine);
        }
        return shader;
    }

    private Uniforms register(RuntimeShader shader, Drawable engine) {
        synchronized (shaders) {
            Uniforms result = shaders.get(shader);
            if (result == null) { result = new Uniforms(engine); shaders.put(shader, result); }
            else if (result.engine.get() != engine) result.engine = new WeakReference<>(engine);
            return result;
        }
    }

    private void prime(Drawable engine, Binding binding) {
        if (engine == null || !opticsEnabled(binding.host.get())) return;
        Object lock = get(binding.access.lock, engine);
        if (lock == null) return;
        synchronized (lock) {
            RuntimeShader shader = observe(engine, binding);
            if (shader == null || binding.primed == revision) return;
            binding.primed = revision;
            Object material = get(binding.access.material, engine);
            if (material != null) {
                MaterialAccess access = materialAccess(material.getClass());
                push(access, material, "gradientStroke", STROKE_EFFECT, shader);
                push(access, material, "optics", OPTICS_EFFECT, shader);
                push(access, material, "innerShadow", INNER_EFFECT, shader);
            }
            dirty(engine, binding);
        }
    }

    private void push(MaterialAccess access, Object material, String name, String className, RuntimeShader shader) {
        if (access.effect == null) return;
        try {
            Object effect = access.effect.invoke(material, name);
            if (effect == null || !className.equals(effect.getClass().getName())) return;
            EffectAccess methods = effectAccess(effect.getClass());
            if (methods.active != null && methods.push != null && Boolean.TRUE.equals(methods.active.invoke(effect)))
                methods.push.invoke(effect, shader);
        } catch (ReflectiveOperationException | RuntimeException unsupported) { /* Keep the native material. */ }
    }

    private void restoreAll() {
        restorationPending = true;
        boolean retry = false;
        ArrayList<Map.Entry<RuntimeShader, Uniforms>> snapshot;
        synchronized (shaders) { snapshot = new ArrayList<>(shaders.entrySet()); }
        for (Map.Entry<RuntimeShader, Uniforms> entry : snapshot) {
            RuntimeShader shader = entry.getKey(); Uniforms state = entry.getValue();
            if (shader == null || state == null) continue;
            synchronized (state) {
                restore(shader, state);
                retry |= state.owned();
            }
        }
        for (Map.Entry<Drawable, Binding> entry : engineSnapshot()) dirty(entry.getKey(), entry.getValue());
        restorationPending = retry;
    }

    private void restoreEngine(Drawable engine) {
        ArrayList<Map.Entry<RuntimeShader, Uniforms>> snapshot;
        synchronized (shaders) { snapshot = new ArrayList<>(shaders.entrySet()); }
        for (Map.Entry<RuntimeShader, Uniforms> entry : snapshot) {
            Uniforms state = entry.getValue();
            if (state.engine.get() != engine) continue;
            synchronized (state) { restore(entry.getKey(), state); }
        }
    }

    private void restore(RuntimeShader shader, Uniforms state) {
        restore(shader, EDGE, state.edge); restore(shader, OPTICS, state.optics); restore(shader, INNER, state.inner);
    }

    private static int color(Bundle settings, String key, int fallback) {
        Object value = settings == null ? null : settings.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private void restore(RuntimeShader shader, String name, Slot slot) {
        if (slot == null || !slot.owned) return;
        Boolean previous = restoring.get(); restoring.set(Boolean.TRUE);
        try { shader.setFloatUniform(name, slot.latest); relinquish(slot); }
        catch (RuntimeException unsupported) { restorationPending = true; /* Retry later; never write unrelated state. */ }
        finally { if (previous == null) restoring.remove(); else restoring.set(previous); }
    }

    private void relinquish(Slot slot) {
        slot.owned = false;
    }

    private void dirty(Drawable engine, Binding binding) {
        if (engine == null) return;
        Object lock = get(binding.access.lock, engine);
        if (lock == null) return;
        synchronized (lock) {
            try { binding.access.dirty.setBoolean(engine, true); }
            catch (ReflectiveOperationException | RuntimeException unsupported) { return; }
        }
        engine.invalidateSelf();
    }

    private ArrayList<Map.Entry<Drawable, Binding>> engineSnapshot() {
        synchronized (engines) { return new ArrayList<>(engines.entrySet()); }
    }

    private void invalidateSurfaces() {
        ArrayList<View> snapshot;
        synchronized (surfaces) { snapshot = new ArrayList<>(surfaces.keySet()); }
        for (View host : snapshot) if (host != null) host.invalidate();
    }

    private void invalidateMediaLights() {
        ArrayList<Drawable> snapshot;
        synchronized (mediaLights) { snapshot = new ArrayList<>(mediaLights.keySet()); }
        for (Drawable light : snapshot) if (light != null) light.invalidateSelf();
    }

    private boolean opticsEnabled(View host) {
        if (!enabled || released || host == null) return false;
        int value = scope(host);
        return value == NOTIFICATION ? notificationEnabled : value == CONTROL && controlEnabled;
    }

    private boolean uniformNotification(View host) {
        return !released && uniformNotificationEnabled && notificationEnabled && host != null && scope(host) == NOTIFICATION;
    }

    private int scope(View host) {
        if (host == null) return UNKNOWN;
        Class<?> type = host.getClass(); Integer cached = scopeTypes.get(type);
        if (cached != null) return cached;
        if (scopeTypes.size() >= MAX_SURFACE_TYPES || !isSurface(host)) return UNKNOWN;
        int value = UNKNOWN;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.equals(NOTIFICATION_BACKGROUND) || name.equals(NOTIFICATION_ROW)
                    || name.equals("com.oplus.systemui.notification.clearall.OplusClearAllButton")) { value = NOTIFICATION; break; }
            if (name.startsWith("com.oplus.systemui.qs.") || name.startsWith("com.oplus.systemui.plugins.qs.")
                    || name.equals("com.oplus.deviceplugin.sdk.ui.view.separatecardview.d")) { value = CONTROL; break; }
        }
        scopeTypes.putIfAbsent(type, value); return value;
    }

    private static final class CardColors {
        final Paint light, dark;
        CardColors(int light, int dark) { this.light = fill(light); this.dark = fill(dark); }
        private static Paint fill(int color) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Same color mix as C17AcrylicMaterial: native coverage/alpha and blur are
            // retained, while the configured alpha controls the common acrylic tint.
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)); return paint;
        }
    }

    private static final class NotificationTint {
        final RectF bounds = new RectF();
        final Drawable[] icons = new Drawable[4];
        View host; Canvas canvas; int count;
    }

    private static final class NotificationAccess {
        final Field host, icon; final Method width, height;
        NotificationAccess(Class<?> type, boolean extension) {
            host = extension ? field(type, "bgView") : null;
            icon = extension ? field(type, "zoomAppIcon") : null;
            width = extension ? null : method(type, "getActualWidth");
            height = extension ? null : method(type, "getActualHeight");
        }
    }

    private EngineAccess engineAccess(Class<?> type) {
        EngineAccess cached = engineAccess.get(type);
        if (cached != null) return cached;
        EngineAccess created = new EngineAccess(type); EngineAccess raced = engineAccess.putIfAbsent(type, created);
        return raced == null ? created : raced;
    }

    private MaterialAccess materialAccess(Class<?> type) {
        MaterialAccess cached = materialAccess.get(type);
        if (cached != null) return cached;
        MaterialAccess created = new MaterialAccess(type); MaterialAccess raced = materialAccess.putIfAbsent(type, created);
        return raced == null ? created : raced;
    }

    private EffectAccess effectAccess(Class<?> type) {
        EffectAccess cached = effectAccess.get(type);
        if (cached != null) return cached;
        EffectAccess created = new EffectAccess(type); EffectAccess raced = effectAccess.putIfAbsent(type, created);
        return raced == null ? created : raced;
    }

    private static boolean type(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (name.equals(current.getName())) return true;
        return false;
    }

    private boolean controlSurface(View host) {
        if (!isSurface(host)) return false;
        for (Class<?> current = host.getClass(); current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.startsWith("com.oplus.systemui.qs.") || name.startsWith("com.oplus.systemui.plugins.qs.")
                    || name.equals("com.oplus.deviceplugin.sdk.ui.view.separatecardview.d")) return true;
        }
        return false;
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException missing) { /* Only the confirmed field name, including its native superclass. */ }
            catch (RuntimeException denied) { return null; }
        }
        return null;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try { Method result = type.getMethod(name, parameters); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException | RuntimeException missing) { return null; }
    }

    private static Object get(Field field, Object owner) {
        if (field == null) return null;
        try { return field.get(owner); }
        catch (ReflectiveOperationException | RuntimeException unsupported) { return null; }
    }

    private static final class EngineAccess {
        final Field material, lock, dirty;
        EngineAccess(Class<?> type) {
            boolean nativeType = type(type, BASE) || type(type, BLEND);
            material = nativeType ? field(type, "drawableShader") : null;
            lock = nativeType ? field(type, "dataLock") : null;
            dirty = nativeType ? field(type, "contentDirty") : null;
        }
        boolean valid() { return material != null && lock != null && dirty != null && dirty.getType() == boolean.class; }
    }

    private static final class BlurAccess {
        final Field proxy;
        BlurAccess(Class<?> type) { proxy = type(type,AUTO_BLUR) || type(type,MASK_BLUR) ? field(type,"viewBlurProxy") : null; }
    }

    private static final class ProxyAccess {
        final Field view;
        ProxyAccess(Class<?> type) { view = type(type,VIEW_BLUR_PROXY) ? field(type,"view") : null; }
    }

    private static final class WrapperAccess {
        final Field engine;
        WrapperAccess(Class<?> type) {
            engine = type(type,PLATFORM_BLUR) ? field(type,"blurDrawable")
                    : type(type,WALLPAPER_BLUR) ? field(type,"blendDrawable") : null;
        }
    }

    private static final class BlurOwner {
        final WeakReference<View> view,host;
        BlurOwner(View view,View host) { this.view = new WeakReference<>(view); this.host = new WeakReference<>(host); }
    }

    private static final class StrokePaint {
        final WeakReference<Paint> original;
        final Paint masked = new Paint(Paint.ANTI_ALIAS_FLAG);
        StrokePaint(Paint source) { original = new WeakReference<>(source); masked.set(source); masked.setAlpha(0); }
    }

    private static final class MaterialAccess {
        final Field shader; final Method effect;
        MaterialAccess(Class<?> type) {
            boolean nativeType = type(type, MATERIAL);
            shader = nativeType ? field(type, "shader") : null;
            effect = nativeType ? method(type, "getEffect", String.class) : null;
        }
    }

    private static final class EffectAccess {
        final Method active, push;
        EffectAccess(Class<?> type) {
            active = method(type, "isEnabled"); push = method(type, "pushUniforms", RuntimeShader.class);
        }
    }

    private static final class Binding {
        volatile WeakReference<View> host;
        volatile WeakReference<RuntimeShader> observed;
        final EngineAccess access; volatile long primed = Long.MIN_VALUE;
        Binding(View host, EngineAccess access) { this.host = new WeakReference<>(host); this.access = access; }
    }

    private static final class Uniforms {
        volatile WeakReference<Drawable> engine;
        Slot edge, optics, inner;
        Uniforms(Drawable engine) { this.engine = new WeakReference<>(engine); }
        Slot get(int kind) { return kind == 0 ? edge : kind == 1 ? optics : inner; }
        void set(int kind, Slot slot) { if (kind == 0) edge = slot; else if (kind == 1) optics = slot; else inner = slot; }
        boolean owned() { return edge != null && edge.owned || optics != null && optics.owned || inner != null && inner.owned; }
    }

    private static final class Slot {
        final float[] latest;
        final WriteBuffer first;
        int depth;
        long sequence, successful;
        boolean owned;
        Slot(int size) { latest = new float[size]; first = new WriteBuffer(size); }
        WriteBuffer acquire() {
            WriteBuffer result = first;
            for (int i = 0; i < depth; i++) {
                if (result.next == null) result.next = new WriteBuffer(latest.length);
                result = result.next;
            }
            depth++; return result;
        }
    }

    private static final class WriteBuffer {
        final float[] pending, masked;
        WriteBuffer next;
        WriteBuffer(int size) { pending = new float[size]; masked = new float[size]; }
    }
}
