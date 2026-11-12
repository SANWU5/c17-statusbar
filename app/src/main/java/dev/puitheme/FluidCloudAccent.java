// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
    public static final String LOCKSCREEN_ENABLED = "fluid_cloud_accent_lockscreen_enabled";
    public static final String MASK_ENABLED = "fluid_cloud_accent_mask_enabled";
    public static final String MASK_COLOR = "fluid_cloud_accent_mask_color";
    public static final String MASK_OPACITY = "fluid_cloud_accent_mask_opacity";
    public static final int DEFAULT_MASK_COLOR = 0xff000000;
    public static final float DEFAULT_MASK_OPACITY = 20f;
    /** Fill opacity of the media colour (the card's own accent and the colour other cards follow). */
    public static final String OPACITY = "fluid_cloud_accent_opacity";
    public static final float DEFAULT_OPACITY = 100f;
    /** Lock screen and AOD can have their own opacity and mask; they replace the shared ones there. */
    public static final String LOCK_CUSTOM = "fluid_cloud_lock_custom_enabled";
    public static final String LOCK_OPACITY = "fluid_cloud_lock_opacity";
    public static final String LOCK_MASK_COLOR = "fluid_cloud_lock_mask_color";
    public static final String LOCK_MASK_OPACITY = "fluid_cloud_lock_mask_opacity";
    /** A white base under the colour on the lock screen and AOD (0..100): the colour comes out as if laid over white, like the
     * unlocked glass, instead of dark and dull over the wallpaper. */
    public static final String LOCK_LIGHT = "fluid_cloud_lock_light";
    /** The music card's accent on the lock screen and AOD is a gradient (main colour at the top fading to the glass at the
     * bottom), as before the unified fill. Off: the whole card is filled like every other card. */
    public static final String LOCK_GRADIENT = "fluid_cloud_lock_gradient_enabled";
    /**
     * AOD has its own copy of the lock-screen look: its switch, opacity, mask, white base and gradient. It starts out equal to the lit
     * lock screen's and is always set on its own page; "unify style" does not change it (it only makes the notifications follow the cards).
     */
    public static final String AOD_CUSTOM = "fluid_cloud_aod_custom_enabled";
    public static final String AOD_OPACITY = "fluid_cloud_aod_opacity";
    public static final String AOD_MASK_COLOR = "fluid_cloud_aod_mask_color";
    public static final String AOD_MASK_OPACITY = "fluid_cloud_aod_mask_opacity";
    public static final String AOD_LIGHT = "fluid_cloud_aod_light";
    public static final String AOD_GRADIENT = "fluid_cloud_aod_gradient_enabled";
    /** How far down the card the lock-screen gradient runs before the colour has faded out (percent of the card's height). */
    public static final String GRADIENT_RANGE = "fluid_cloud_lock_gradient_range";
    public static final float DEFAULT_GRADIENT_RANGE = 100f;
    public static final float DEFAULT_LOCK_OPACITY = 100f;
    public static final float DEFAULT_LOCK_MASK_OPACITY = 20f;
    public static final float DEFAULT_LOCK_LIGHT = 70f;
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
    private final Map<ClassLoader, MediaColorHooks> mediaColors = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, BackgroundBinding> backgrounds = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, MediaBinding> media = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, MediaBinding> accents = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<BackgroundScope> activeBackground = new ThreadLocal<>();
    private final ThreadLocal<Boolean> restoring = new ThreadLocal<>();
    private final Object pendingLock = new Object();
    private volatile boolean enabled, released, loggedFailure;
    private volatile boolean maskEnabled, lockscreenEnabled;
    private volatile boolean onKeyguard, materialBlocked;
    private volatile boolean lockEffectsEnabled=true;
    private volatile int maskColor = DEFAULT_MASK_COLOR;
    private volatile float maskOpacity = DEFAULT_MASK_OPACITY;
    private volatile boolean lockCustom, lockGradient;
    private volatile int lockMaskColor = DEFAULT_MASK_COLOR;
    private volatile float lockMaskOpacity = DEFAULT_LOCK_MASK_OPACITY;
    private volatile float fillOpacity = DEFAULT_OPACITY, lockOpacity = DEFAULT_LOCK_OPACITY, lockLight = DEFAULT_LOCK_LIGHT;
    private volatile float gradientRange = DEFAULT_GRADIENT_RANGE;
    private volatile boolean aodCustom, aodGradient;
    private volatile int aodMaskColor = DEFAULT_MASK_COLOR;
    private volatile float aodMaskOpacity = DEFAULT_LOCK_MASK_OPACITY, aodOpacity = DEFAULT_LOCK_OPACITY, aodLight = DEFAULT_LOCK_LIGHT;
    private boolean refreshPending;
    private volatile long colorRevision;
    private long mediaSequence;
    private volatile Runnable colorListener;

    public boolean enabled() { return enabled && !released; }
    /** The media accent is the highest priority and applies in every scene once enabled; the old lock-screen switch is retired. */
    public boolean lockscreenEnabled() { return enabled()&&lockEffectsEnabled; }
    public long colorRevision() { return colorRevision; }
    public void setColorListener(Runnable listener) { colorListener = listener; }
    void scene(boolean keyguard, boolean blocked) {
        if(onKeyguard==keyguard&&materialBlocked==blocked)return;
        onKeyguard=keyguard;materialBlocked=blocked;
        for(BackgroundBinding binding:backgroundSnapshot())binding.dirty=true;
        colorRevision++;scheduleRefresh();notifyColorsChanged();
    }
    private boolean desktopAllowed() { return !materialBlocked&&(!onKeyguard||lockscreenEnabled()); }

    /** The latest audited s1/K0 primary-colour flow, for exact non-CardView owners.
     * No bitmap parsing, cached album colours or fallback black are introduced.
     */
    public Integer mediaAccent(Object section) {
        if (!enabled() || section == null || !MEDIA_SECTION.equals(section.getClass().getName())) return null;
        Integer color = nativeMediaColor(section);
        return color == null ? null : maskedAccent(color);
    }
    Integer mediaAccentColor(Integer nativeColor) { return mediaAccentColor(nativeColor, false); }
    /** lockScene: lock screen and AOD, where the lock-screen custom mask replaces the shared one. */
    Integer mediaAccentColor(Integer nativeColor, boolean lockScene) { return mediaAccentColor(nativeColor, lockScene, false); }
    /** aod: the scene is AOD, which has its own look unless the style is unified. */
    Integer mediaAccentColor(Integer nativeColor, boolean lockScene, boolean aod) {
        return !accentWanted(lockScene, aod) || nativeColor == null ? null : maskedAccent(nativeColor, lockScene, aod);
    }

    /**
     * The lock screen's (or AOD's) own page asks for its look. These settings also
     * govern neutral glass, so they remain available when media colour is off.
     */
    boolean lockOwn(boolean aod) { return !released && lockEffectsEnabled && (ownAod(aod) ? aodGradient || aodCustom : lockGradient || lockCustom); }

    /** The media-accent master gates media colour in every scene, including lock screen and AOD. */
    boolean accentWanted(boolean lockScene, boolean aod) { return enabled() && (!lockScene || lockEffectsEnabled); }

    /** The follow-media policy uses the same mask settings as the accent. */
    Integer maskedMediaColor(Integer nativeColor) { return maskedMediaColor(nativeColor, false); }
    Integer maskedMediaColor(Integer nativeColor, boolean lockScene) { return maskedMediaColor(nativeColor, lockScene, false); }
    Integer maskedMediaColor(Integer nativeColor, boolean lockScene, boolean aod) {
        return released || nativeColor == null || lockScene && (!enabled() || !lockEffectsEnabled) ? null : maskedAccent(nativeColor, lockScene, aod);
    }

    /** True in AOD, which has a look of its own. */
    private boolean ownAod(boolean aod) { return aod; }

    /** The accent gradient on the lock screen and AOD. */
    boolean lockGradient() { return lockGradient(false); }
    boolean lockGradient(boolean aod) { return (ownAod(aod) ? aodGradient : lockGradient) && !released; }

    /** How far down the card or capsule the lock-screen gradient runs before the colour has faded out (0.05..1). */
    float gradientRange() { return gradientRange / 100f; }

    /** The white base (0..1) under the colour on the lock screen and AOD. */
    float lockLight() { return lockLight(false); }
    float lockLight(boolean aod) { return (ownAod(aod) ? aodLight : lockLight) / 100f; }

    /** The lock-screen custom opacity (0..1) while "lock custom" is on, else -1: the custom base colour follows it too. */
    float lockCustomOpacity() { return lockCustomOpacity(false); }
    float lockCustomOpacity(boolean aod) {
        boolean own = ownAod(aod);
        return (own ? aodCustom : lockCustom) && !released ? (own ? aodOpacity : lockOpacity) / 100f : -1f;
    }

    /** Fill opacity (0..1) of the media colour in this scene: the lock-screen custom value there, else the shared one. */
    float fillFactor(boolean lockScene) { return fillFactor(lockScene, false); }
    float fillFactor(boolean lockScene, boolean aod) {
        boolean own = ownAod(aod);
        return (lockScene && (own ? aodCustom : lockCustom) ? (own ? aodOpacity : lockOpacity) : fillOpacity) / 100f;
    }

    /** Final OEM primary colour without enabling the independent accent policy. */
    public Integer nativeMediaColor(Object section) {
        if (released || section == null || !MEDIA_SECTION.equals(section.getClass().getName())) return null;
        try {
            return nativeMediaAccent(section, mediaColorHooks(section.getClass().getClassLoader()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            reportFailure(unavailable); return null;
        }
    }

    /** Bind-time ownership also exists while the accent switch is OFF. */
    public Integer nativeBackgroundMediaColor(View host) {
        if (released) return null;
        View card = cardOf(host);
        MediaBinding binding = card == null ? null : accents.get(card);
        return binding == null || !binding.hasColor || !binding.attached ? null : binding.color;
    }

    /** Explicit follow-media fill can borrow the latest live native media colour.
     * An actual media card keeps its own colour; unrelated cards never acquire
     * independent accent ownership merely by choosing this fill source.
     */
    public Integer nativeCurrentMediaColor(View host) {
        Integer own = nativeBackgroundMediaColor(host);
        if (own != null || released) return own;
        MediaBinding latest = null;
        for (MediaBinding binding : mediaSnapshot()) {
            View root = binding.root.get();
            if (binding.hasColor && binding.attached && root != null && root.isAttachedToWindow()
                    && binding.section.get() != null && (latest == null || binding.sequence > latest.sequence)) latest = binding;
        }
        return latest == null ? null : latest.color;
    }

    private MediaColorHooks mediaColorHooks(ClassLoader loader) throws ReflectiveOperationException {
        MediaColorHooks access = mediaColors.get(loader);
        if (access == null) { access = new MediaColorHooks(loader); mediaColors.put(loader, access); }
        return access;
    }
    private static Integer nativeMediaAccent(Object section, MediaColorHooks access) throws ReflectiveOperationException {
        Object model = access.mediaModel.get(section);
        if (model == null) return null;
        Object boxed = access.primaryFlow.get(model);
        if (boxed == null) return null;
        Object flow = access.wrappedFlow.get(boxed);
        if (flow == null) return null;
        Object primary = access.flowValue.invoke(flow);
        if (primary == null || !access.primaryType.isInstance(primary)) return null;
        int color = access.primaryColor.getInt(primary);
        return (color >>> 24) == 0 ? null : color;
    }

    /** Native material removal must not erase an active media accent. */
    public boolean protectsBackground(View host) {
        if (!enabled() || !desktopAllowed()) return false;
        View card = cardOf(host);
        return card != null && accents.containsKey(card);
    }

    /** The already resolved owner of the short-lived BackgroundUtils call. */
    public boolean activeAccentBackground() {
        BackgroundScope scope = activeBackground.get();
        return enabled() && scope != null && scope.colorEnabled && !Boolean.TRUE.equals(restoring.get());
    }

    /** A paired controller detach restores one native material without re-adopting
     * the departing card or allowing the other controller to recolor that replay.
     */
    public NativeRestoreScope beginNativeRestore() { return new NativeRestoreScope(); }
    public final class NativeRestoreScope implements AutoCloseable {
        private final Boolean previous = restoring.get();
        private boolean closed;
        private NativeRestoreScope() { restoring.set(Boolean.TRUE); }
        @Override public void close() {
            if (closed) return; closed = true;
            if (previous == null) restoring.remove(); else restoring.set(previous);
        }
    }

    /** Resolve a structural fingerprint before installing any plugin hooks.
     * A missing/changed obfuscated contract is unsupported, never a guessed name.
     */
    public Hooks resolve(ClassLoader loader) throws ReflectiveOperationException {
        if (loader == null || released) return null;
        Hooks cached = contracts.get(loader);
        if (cached != null) return cached;
        Hooks result = new Hooks(loader);
        contracts.put(loader, result);
        contracts.put(result.backgroundType.getClassLoader(), result);
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
            MediaColorHooks access = mediaColorHooks(section.getClass().getClassLoader());
            Object rootValue = access.mediaRoot.get(section);
            if (!(rootValue instanceof View)) return;
            root = (View) rootValue;
            Integer nativeAccent = nativeMediaAccent(section, access);
            mediaChanged(section, root, nativeAccent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (root != null) clearMedia(root);
            reportFailure(unavailable);
        }
    }

    /** Share the same audited read when only the media contract is installed. */
    void mediaChanged(Object section, View root, Integer nativeAccent) {
        if(released||section==null||root==null)return;
        MediaBinding existing = media.get(root);
        if (existing != null && existing.loss != null) { main.removeCallbacks(existing.loss); existing.loss = null; }
        // The native colour is empty for a moment while the track changes. Everything that follows the media colour
        // used to drop to another colour for that moment and come back: keep the last colour a little while.
        if (nativeAccent == null && existing != null && existing.hasColor && existing.section.get() == section && existing.attached) {
            final MediaBinding keeping = existing;
            keeping.loss = () -> { keeping.loss = null; applyMedia(section, root, null); };
            main.postDelayed(keeping.loss, COLOR_GRACE_MS);
            return;
        }
        applyMedia(section, root, nativeAccent);
    }
    static final long COLOR_GRACE_MS = 1500L;
    /** Check seam: the grace for a missing native colour has run out. */
    void expireColorGraceForCheck() {
        for (MediaBinding binding : mediaSnapshot()) if (binding.loss != null) {
            Runnable loss = binding.loss; main.removeCallbacks(loss); binding.loss = null; loss.run();
        }
    }
    private void applyMedia(Object section, View root, Integer nativeAccent) {
        if(released)return;
        MediaBinding binding = media.get(root);
        if (binding == null) {
            binding = new MediaBinding(section, root);
            media.put(root, binding);
            root.addOnAttachStateChangeListener(binding);
        }
        boolean changed = binding.section.get() != section || binding.hasColor != (nativeAccent != null)
                || nativeAccent != null && binding.color != nativeAccent;
        binding.section = new WeakReference<>(section);
        binding.attached = root.isAttachedToWindow();
        binding.hasColor = nativeAccent != null;
        if (nativeAccent != null) binding.color = nativeAccent;
        if (changed) binding.sequence = ++mediaSequence;
        boolean moved = adopt(binding);
        if (changed || moved) colorsChanged();
    }

    public void mediaDisposed(Object section) {
        if (section == null || !section.getClass().getName().equals(MEDIA_SECTION)) return;
        MediaColorHooks access = mediaColors.get(section.getClass().getClassLoader());
        if (access == null) return;
        try {
            Object root = access.mediaRoot.get(section);
            if (root instanceof View) {
                MediaBinding binding = media.get(root);
                if (binding != null && binding.section.get() == section) clearMedia((View) root);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { reportFailure(unavailable); }
    }

    private void clearMedia(View root) {
        MediaBinding binding = media.remove(root);
        if (binding == null) return;
        root.removeOnAttachStateChangeListener(binding);
        View card = binding.card.get();
        if (card != null && accents.get(card) == binding) accents.remove(card);
        colorsChanged();
    }

    /** Native card model bind/attach: resolve ownership once when content moves.
     * Iterates known media roots, not arbitrary children or UI every drawing frame.
     */
    public void cardChanged(View card) {
        if (released || card == null || !card.getClass().getName().equals(CARD)) return;
        boolean changed = false;
        for (MediaBinding binding : mediaSnapshot()) changed |= adopt(binding);
        if (changed) colorsChanged();
    }

    /** The root wraps only BackgroundUtils.b(View,ViewRootManager), before proceed.
     * A returned scope is always closed in finally; completed() follows a successful
     * native proceed. Only CardBackgroundView inside CardView can own this scope.
     */
    public BackgroundScope beginBackground(Object helper, View host, Object manager) {
        if (released || Boolean.TRUE.equals(restoring.get()) || host == null || helper == null
                || !host.getClass().getName().equals(BACKGROUND)
                || !helper.getClass().getName().equals(BACKGROUND_HELPER)) return null;
        Hooks access = contracts.get(host.getClass().getClassLoader());
        if (access == null || manager != null && !access.managerType.isInstance(manager)) return null;
        View card = cardOf(host);
        if (card == null) return null;
        BackgroundBinding binding = backgrounds.get(host);
        if (binding == null) {
            binding = new BackgroundBinding(host, card, access);
            backgrounds.put(host, binding);
        }
        binding.helper = new WeakReference<>(helper);
        binding.manager = new WeakReference<>(manager);
        binding.hasManager = manager != null;
        // Even when disabled, record the real native owner for later enabling.
        Integer accent = nativeBackgroundMediaColor(host);
        boolean color = enabled && desktopAllowed() && !Boolean.TRUE.equals(restoring.get()) && accent != null;
        BackgroundScope scope = new BackgroundScope(binding, color ? maskedAccent(accent) : 0, color,
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
        boolean nextLockEffects=!SafetyMode.enabled(settings)&&!CustomPluginMaterialIsolation.lockOwned(settings);
        boolean next = settings != null && settings.getBoolean(ENABLED, false) && !SafetyMode.enabled(settings);
        boolean nextLockscreen = settings != null && settings.getBoolean(LOCKSCREEN_ENABLED, false);
        boolean nextMask = settings != null && settings.getBoolean(MASK_ENABLED, false);
        int nextColor = color(settings == null ? null : settings.get(MASK_COLOR), DEFAULT_MASK_COLOR);
        Object alphaMode = settings == null ? null : settings.get(StatusBarSettings.alphaKey(MASK_COLOR));
        boolean customAlpha = alphaMode instanceof Boolean ? (Boolean) alphaMode : (nextColor >>> 24) != 255;
        if (!customAlpha) nextColor |= 0xff000000;
        Object opacity = settings == null ? null : settings.get(MASK_OPACITY);
        float nextOpacity = opacity instanceof Number ? ((Number) opacity).floatValue() : DEFAULT_MASK_OPACITY;
        if (!Float.isFinite(nextOpacity)) nextOpacity = DEFAULT_MASK_OPACITY;
        nextOpacity = Math.max(0f, Math.min(100f, nextOpacity));
        boolean nextLockCustom = nextLockEffects && settings != null && settings.getBoolean(LOCK_CUSTOM, false);
        // A complete saved snapshot always carries the key (default on); a partial bundle without it keeps the fill path.
        boolean nextGradient = nextLockEffects && settings != null && settings.getBoolean(LOCK_GRADIENT, false);
        int nextLockColor = color(settings == null ? null : settings.get(LOCK_MASK_COLOR), DEFAULT_MASK_COLOR);
        Object lockAlpha = settings == null ? null : settings.get(StatusBarSettings.alphaKey(LOCK_MASK_COLOR));
        if (!(lockAlpha instanceof Boolean ? (Boolean) lockAlpha : (nextLockColor >>> 24) != 255)) nextLockColor |= 0xff000000;
        float nextLockMask = percent(settings, LOCK_MASK_OPACITY, DEFAULT_LOCK_MASK_OPACITY);
        float nextFill = percent(settings, OPACITY, DEFAULT_OPACITY);
        float nextLockFill = percent(settings, LOCK_OPACITY, DEFAULT_LOCK_OPACITY);
        float nextLockLight = percent(settings, LOCK_LIGHT, DEFAULT_LOCK_LIGHT);
        float nextRange = Math.max(5f, percent(settings, GRADIENT_RANGE, DEFAULT_GRADIENT_RANGE));
        boolean nextAodCustom = nextLockEffects && settings != null && settings.getBoolean(AOD_CUSTOM, false);
        boolean nextAodGradient = nextLockEffects && settings != null && settings.getBoolean(AOD_GRADIENT, false);
        int nextAodColor = color(settings == null ? null : settings.get(AOD_MASK_COLOR), DEFAULT_MASK_COLOR);
        Object aodAlpha = settings == null ? null : settings.get(StatusBarSettings.alphaKey(AOD_MASK_COLOR));
        if (!(aodAlpha instanceof Boolean ? (Boolean) aodAlpha : (nextAodColor >>> 24) != 255)) nextAodColor |= 0xff000000;
        float nextAodMask = percent(settings, AOD_MASK_OPACITY, DEFAULT_LOCK_MASK_OPACITY);
        float nextAodFill = percent(settings, AOD_OPACITY, DEFAULT_LOCK_OPACITY);
        float nextAodLight = percent(settings, AOD_LIGHT, DEFAULT_LOCK_LIGHT);
        if (enabled == next && lockEffectsEnabled==nextLockEffects && aodCustom == nextAodCustom && aodGradient == nextAodGradient && aodMaskColor == nextAodColor
                && aodMaskOpacity == nextAodMask && aodOpacity == nextAodFill && aodLight == nextAodLight && lockscreenEnabled == nextLockscreen && maskEnabled == nextMask
                && maskColor == nextColor && maskOpacity == nextOpacity && lockCustom == nextLockCustom && lockGradient == nextGradient
                && lockMaskColor == nextLockColor && lockMaskOpacity == nextLockMask
                && fillOpacity == nextFill && lockOpacity == nextLockFill && lockLight == nextLockLight && gradientRange == nextRange) return;
        enabled = next; lockscreenEnabled = nextLockscreen;lockEffectsEnabled=nextLockEffects;
        maskEnabled = nextMask; maskColor = nextColor; maskOpacity = nextOpacity;
        lockCustom = nextLockCustom; lockGradient = nextGradient; lockMaskColor = nextLockColor; lockMaskOpacity = nextLockMask;
        fillOpacity = nextFill; lockOpacity = nextLockFill; lockLight = nextLockLight; gradientRange = nextRange;
        aodCustom = nextAodCustom; aodGradient = nextAodGradient; aodMaskColor = nextAodColor; aodMaskOpacity = nextAodMask;
        aodOpacity = nextAodFill; aodLight = nextAodLight;
        loggedFailure = false;
        colorRevision++;
        scheduleRefresh();
        notifyColorsChanged();
    }

    /** Restore through the original native initializer, including after safe mode. */
    public void release() {
        if (released) return;
        enabled = false; released = true;
        try { CustomPluginValues.publish(CustomPluginValues.HOST, MEDIA_ACCENT_VALUE, null); } catch (RuntimeException ignored) { }
        ArrayList<BackgroundBinding> owned = backgroundSnapshot();
        ArrayList<MediaBinding> roots = mediaSnapshot();
        for (MediaBinding binding : roots) if (binding.loss != null) { main.removeCallbacks(binding.loss); binding.loss = null; }
        Runnable restore = () -> {
            for (BackgroundBinding binding : owned) replay(binding, false);
            for (MediaBinding binding : roots) {
                View root = binding.root.get();
                if (root != null) root.removeOnAttachStateChangeListener(binding);
            }
            activeBackground.remove(); restoring.remove();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) restore.run(); else main.post(restore);
        backgrounds.clear(); accents.clear(); media.clear(); contracts.clear(); mediaColors.clear();
        colorListener = null;
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

    private boolean adopt(MediaBinding binding) {
        View root = binding.root.get();
        if (root == null) return false;
        View card = binding.attached && binding.hasColor ? cardOf(root) : null;
        View previousCard = binding.card.get();
        boolean changed = previousCard != card;
        if (previousCard != null && previousCard != card && accents.get(previousCard) == binding) accents.remove(previousCard);
        binding.card = new WeakReference<>(card);
        if (card != null && accents.get(card) != binding) { accents.put(card, binding); changed = true; }
        return changed;
    }

    private void colorsChanged() {
        colorRevision++;
        if (enabled) scheduleRefresh();
        notifyColorsChanged();
        publishLatest();
    }

    /** App289 runtime.values "host/media_accent": the newest live OEM media colour, for plugins such as glass.
     * Published whether or not this host accent switch is on; attached media are preferred over detached ones. */
    private void publishLatest() {
        MediaBinding latest = null; boolean latestAttached = false;
        for (MediaBinding binding : mediaSnapshot()) {
            if (!binding.hasColor || binding.section.get() == null) continue;
            View root = binding.root.get();
            boolean attached = binding.attached && root != null && root.isAttachedToWindow();
            if (latest == null || attached && !latestAttached || attached == latestAttached && binding.sequence > latest.sequence) {
                latest = binding; latestAttached = attached;
            }
        }
        try { CustomPluginValues.publish(CustomPluginValues.HOST, MEDIA_ACCENT_VALUE, released || latest == null ? null : latest.color); }
        catch (RuntimeException ignored) { }
    }
    static final String MEDIA_ACCENT_VALUE = "media_accent";
    private void notifyColorsChanged() {
        Runnable listener = colorListener;
        if (listener != null && !released) listener.run();
    }

    private void scheduleRefresh() {
        synchronized (pendingLock) {
            if (refreshPending) return;
            refreshPending = true;
        }
        main.post(() -> {
            try {
                if (released) return;
                for (BackgroundBinding binding : backgroundSnapshot()) replay(binding, enabled);
            } finally { synchronized (pendingLock) { refreshPending = false; } }
        });
    }

    private void replay(BackgroundBinding binding, boolean tint) {
        View host = binding.host.get(), card = binding.card.get();
        Object manager = binding.manager.get();
        if (host == null || card == null || binding.hasManager && manager == null) return;
        Integer accent = tint ? nativeBackgroundMediaColor(host) : null;
        boolean apply = tint && !released && desktopAllowed() && accent != null && cardOf(host) == card;
        if (apply && binding.applied && !binding.dirty && binding.appliedColor == maskedAccent(accent)) return;
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

    /** RGB-only source-over mask: native background alpha and animations stay native.
     * This runs only when the OEM builds material, never extracts artwork or blurs it.
     */
    private int maskedAccent(int accent) { return maskedAccent(accent, false, false); }
    private int maskedAccent(int accent, boolean lockScene, boolean aod) {
        boolean own = ownAod(aod);
        if (lockScene && (own ? aodCustom : lockCustom)) return own ? mask(accent, aodMaskColor, aodMaskOpacity) : mask(accent, lockMaskColor, lockMaskOpacity);
        return maskEnabled ? mask(accent, maskColor, maskOpacity) : accent;
    }

    private static float percent(Bundle settings, String key, float fallback) {
        Object value = settings == null ? null : settings.get(key);
        float result = value instanceof Number ? ((Number) value).floatValue() : fallback;
        if (!Float.isFinite(result)) result = fallback;
        return Math.max(0f, Math.min(100f, result));
    }

    static int mask(int accent, int overlay, float opacityPercent) {
        if (!Float.isFinite(opacityPercent)) return accent;
        float alpha = (overlay >>> 24) / 255f * Math.max(0f, Math.min(100f, opacityPercent)) / 100f;
        if (alpha <= 0f) return accent;
        int red = Math.round(((accent >>> 16) & 255) * (1f - alpha) + ((overlay >>> 16) & 255) * alpha);
        int green = Math.round(((accent >>> 8) & 255) * (1f - alpha) + ((overlay >>> 8) & 255) * alpha);
        int blue = Math.round((accent & 255) * (1f - alpha) + (overlay & 255) * alpha);
        return (accent & 0xff000000) | red << 16 | green << 8 | blue;
    }

    private static int color(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) try { return Color.parseColor((String) value); }
        catch (IllegalArgumentException ignored) { }
        return fallback;
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

        public void completed() {
            if (colorEnabled && enabled() && !Boolean.TRUE.equals(restoring.get())) {
                View host = binding.host.get();
                if (host != null && cardOf(host) == binding.card.get()) {
                    Drawable nativeBackground = host.getBackground();
                    Drawable recolored = FluidCloudFallback.derive(nativeBackground, host.getResources(), accent, false, false);
                    if (recolored != nativeBackground) {
                        host.setBackground(recolored);
                        changed = true;
                    }
                }
            }
            completed = true;
        }

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
        volatile boolean applied, dirty, hasManager;
        volatile int appliedColor;
        BackgroundBinding(View host, View card, Hooks access) {
            this.host = new WeakReference<>(host); this.card = new WeakReference<>(card); this.access = access;
        }
    }

    private final class MediaBinding implements View.OnAttachStateChangeListener {
        final WeakReference<View> root;
        WeakReference<Object> section;
        WeakReference<View> card = new WeakReference<>(null);
        int color; boolean hasColor, attached; long sequence; Runnable loss;
        MediaBinding(Object section, View root) { this.root = new WeakReference<>(root); this.section = new WeakReference<>(section); }
        @Override public void onViewAttachedToWindow(View view) {
            if (released || media.get(view) != this) return;
            attached = true; sequence = ++mediaSequence; adopt(this); colorsChanged();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if (media.get(view) != this) return;
            View previousCard = card.get();
            if (previousCard != null && accents.get(previousCard) == this) accents.remove(previousCard);
            card = new WeakReference<>(null);
            attached = false; colorsChanged();
        }
    }

    /** Media colour does not depend on support for the separate desktop helper.
     * s1.l/k -> K0.N -> jj.b -> p.getValue -> primary.a is the audited native
     * final-colour contract, also available to lockscreen-only media instances.
     */
    private static final class MediaColorHooks {
        final Field mediaRoot, mediaModel, primaryFlow, wrappedFlow, primaryColor;
        final Class<?> primaryType;
        final Method flowValue;
        MediaColorHooks(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> sectionType = loader.loadClass(MEDIA_SECTION);
            Class<?> modelType = loader.loadClass("com.oplus.systemui.plugins.shared.template.section.media.K0");
            Class<?> wrapperType = loader.loadClass("com.oplus.systemui.plugins.r8.jj");
            primaryType = loader.loadClass(PRIMARY_MODEL);
            mediaRoot = Hooks.checkedField(sectionType, "l", android.view.ViewGroup.class);
            mediaModel = Hooks.checkedField(sectionType, "k", modelType);
            primaryFlow = Hooks.checkedField(modelType, "N", wrapperType);
            wrappedFlow = Hooks.checkedField(wrapperType, "b", loader.loadClass("com.oplus.systemui.plugins.r8.Lg"));
            primaryColor = Hooks.checkedField(primaryType, "a", Integer.TYPE);
            flowValue = loader.loadClass("kotlinx.coroutines.flow.p").getDeclaredMethod("getValue");
            flowValue.setAccessible(true);
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
