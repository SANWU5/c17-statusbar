// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.BlendModeColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.AccessNetworkConstants;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.TypedValue;
import java.lang.reflect.Field;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.error.XposedFrameworkError;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes.dex */
public final class StatusBarModule extends XposedModule {
    private static final String MOBILE_VM = "com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconViewModel";
    private static final String MODERN_VIEW = "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView";
    private static final String MODULE_PACKAGE = ModuleVariant.PACKAGE;
    private static final float NETWORK_GLYPH_CENTER_Y_DP = 6.625f;
    private static final float NETWORK_GLYPH_HEIGHT_DP = 11.5f;
    private static final String NETWORK_TYPE = "com.android.systemui.statusbar.pipeline.mobile.data.OplusNetworkTypeText";
    private static final String TAG = "PuiThemeIOSStatusBar";
    private static final String WIFI_VIEW = "com.oplus.systemui.statusbar.phone.signal.widget.OplusModernStatusBarWifiView";
    private NativePhoneAnchorReader phoneAnchorReader;
    private static final float ICON_SLOT_DP = 20f;
    private static volatile boolean composeStatusBar;
    private static volatile boolean composeRenderingReady;
    private static volatile boolean networkObserverRegistered;
    private static volatile boolean mobileDataObserverRegistered;
    private static boolean wifiNetworkObserverRegistered, defaultNetworkObserverRegistered;
    private static volatile boolean airplaneMode;
    private static Context context;
    private static String lastIconTraceProbe;
    private static Method getActiveDataSubId;
    private static Method getAppearAmount;
    private static Method getDefaultDataSubId;
    private static Method getFlowValue;
    private static Method getLastAppliedTint;
    private static Method getNetworkTypeFlow;
    private static Method getSubscriptionId;
    private static Method getTypeModel;
    private static Method getTypeName;
    private static Method getVisibleState;
    private static volatile Resources moduleResources;
    private static volatile boolean receiverRegistered;
    private static boolean refreshPending;
    private static boolean systemTextRefreshPending;
    private static final Runnable SYSTEM_TEXT_CHANGED = StatusBarModule::refreshSystemText;
    private static volatile boolean settingsObserverRegistered;
    private static ContentObserver panelModeObserver;
    private static final Runnable PANEL_MODE_CHANGED = () -> {
        if (context == null || ModuleLifecycle.removed()) return;
        Bundle saved = SettingsSnapshot.lastApplied(context);
        if (saved != null) applyStyleSnapshot(saved, true);
        StatusBarModule.WALLPAPER_PANEL.publish();
        StatusBarModule.TEXT.refresh();
    };
    private static SettingsStartupLoader settingsStartup;
    private static SettingsFrameworkReader frameworkSettings;
    private static volatile boolean preferSettingsProvider;
    private static final Runnable FRAMEWORK_SETTINGS_CHANGED=()->{
        if(settingsStartup!=null&&!ModuleLifecycle.removed())settingsStartup.signal(false);
    };
    private static volatile StatusBarModule frameworkLogger;
    private static volatile float wifiOffsetXdp = 0;
    private static volatile float wifiOffsetYdp = 0;
    private static volatile float wifiIconScalePercent = 100;
    private static volatile float dataOffsetXdp = 0;
    private static volatile float dataOffsetYdp = 0;
    private static volatile float dataIconScalePercent = 100;
    private static volatile float labelOffsetXdp = 0;
    private static volatile float labelOffsetYdp = 0;
    private static volatile float labelScalePercent = 100;
    private static volatile boolean roamingHiddenLogged;
    private static volatile float speedOffsetXdp, speedOffsetYdp, speedLineGapDp;
    private static volatile float speedScalePercent = 100, speedNumberScalePercent = 100, speedUnitScalePercent = 100;
    private static volatile Map<String, Integer> styleColors = StatusBarSettings.COLOR_DEFAULTS;
    private static volatile Map<String, Boolean> customColorAlpha = Collections.emptyMap();
    private static volatile FeatureOptions FEATURES = FeatureOptions.from(Collections.emptyMap());
    private static volatile boolean singleSignal;
    private static volatile int speedWeight = 600;
    private static Field speedNumberField, speedUnitField;
    private static final Map<View, SpeedStyle> SPEED_VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class SpeedStyle {
        float numberSize, unitSize;
        float appliedNumberSize, appliedUnitSize;
        float numberY, unitY;
        float appliedNumberY, appliedUnitY;
        boolean gapCaptured, tintCaptured;
        int nativeTint = Color.BLACK;
        Typeface numberFace, unitFace, appliedNumberFace, appliedUnitFace;
        String numberAxes, unitAxes, appliedNumberAxes, appliedUnitAxes;
        boolean numberFontCaptured, unitFontCaptured;
    }
    private static volatile int fontWeight = StatusBarSettings.DEFAULT_FONT_WEIGHT;
    private static volatile String networkLabel = "";
    private static volatile boolean wifiConnected;
    private static volatile Boolean wifiSwitchEnabled, mobileDataSwitchEnabled;
    private static final Map<View,WeakReference<Object>> NATIVE_WIFI=Collections.synchronizedMap(new WeakHashMap<>());
    private static Method nativeWifiFlow,nativeWifiResource;
    private static Class<?> nativeWifiIconClass,nativeWifiVisibleClass;
    private static int observedDataSub=-1;
    private static TelephonyManager observedTelephony;
    private static DataRadioCallback dataRadioCallback;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CustomPluginHooks CUSTOM_PLUGINS = new CustomPluginHooks(MAIN);
    private static final TextControls TEXT = new TextControls(MAIN);
    private static final StatusBarLeftCopySpacing LEFT_COPY_SPACING = new StatusBarLeftCopySpacing(TEXT::clockDrawOffsetX);
    private static final ShadeWallpaper SHADE_WALLPAPER = new ShadeWallpaper();
    private static final ShadeWallpaperScrimBlur SHADE_SCRIM_BLUR = new ShadeWallpaperScrimBlur(SHADE_WALLPAPER);
    private static final NotificationBigClock.WallpaperLink WALLPAPER_LINK = new NotificationBigClock.WallpaperLink() {
        @Override public void clockBottom(float bottomPixels) { SHADE_WALLPAPER.onClockBottom(bottomPixels); }
        @Override public ShadeWallpaper.Glass glass() { return SHADE_WALLPAPER.glass(); }
    };
    private static final WallpaperPanelState WALLPAPER_PANEL = new WallpaperPanelState();
    private static final LockscreenControls LOCKSCREEN = new LockscreenControls();
    private static final TilePageEffects TILE_EFFECTS = new TilePageEffects();
    private static final QsTileAppearance QS_APPEARANCE = new QsTileAppearance();
    private static final QsTileFullFill QS_FULL_FILL = new QsTileFullFill(QS_APPEARANCE);
    private static final QsTileCorners QS_CORNERS = new QsTileCorners();
    private static final QsTileIconSize QS_ICON_SIZE = new QsTileIconSize();
    private static final IconNativeControlCenter ICON_NATIVE_CONTROLS = new IconNativeControlCenter();
    private static final IconNativeQs ICON_NATIVE_QS = new IconNativeQs();
    private static final QsPanelCorners QS_PANEL_CORNERS = new QsPanelCorners();
    private static final QsMediaAppearance QS_MEDIA = new QsMediaAppearance();
    private static final C17HighlightRemoval C17_HIGHLIGHTS = new C17HighlightRemoval();
    private static final FluidCloudAccent FLUID_ACCENT = new FluidCloudAccent();
    private static final FluidCloudStyle FLUID_STYLE = new FluidCloudStyle();
    private static final AodNotificationBackground AOD_NOTIFICATION_BACKGROUND = new AodNotificationBackground();
    private static final FluidCloudCapsule FLUID_CAPSULE = new FluidCloudCapsule(FLUID_ACCENT);
    private static final PrivacyDotHider PRIVACY_DOT = new PrivacyDotHider();
    private static final PrivacyCapsule PRIVACY_CAPSULE = new PrivacyCapsule();
    private static volatile PrivacyIndicatorSettings privacySettings = PrivacyIndicatorSettings.from(null);
    private static final CapsuleEffects CAPSULE_EFFECTS = new CapsuleEffects();
    private static final CapsulePersistence CAPSULE_PERSISTENCE = new CapsulePersistence();
    private static final CapsuleMediaPersistence CAPSULE_MEDIA_PERSISTENCE = new CapsuleMediaPersistence();
    private static final ResidentCache RESIDENT_CACHE = new ResidentCache();
    private static final LockCapsuleTint LOCK_CAPSULE = new LockCapsuleTint();
    private static final CapsuleSpectrum CAPSULE_SPECTRUM = new CapsuleSpectrum(FLUID_CAPSULE::spectrumAccent);
    static {
        FLUID_STYLE.setAccentController(FLUID_ACCENT);
        LOCK_CAPSULE.setPolicy(FLUID_STYLE::lockCapsulePlan);
        FLUID_STYLE.setLockCapsuleListener(LOCK_CAPSULE::invalidateAll);
        FLUID_STYLE.setNotificationStyler(new FluidCloudStyle.NotificationStyler() {
            @Override public void update(View body, boolean follow, boolean keepHighlight) {
                // Native scene BlurProxy/MetaBall owns fill, gradient and blur. The C17 controller owns the
                // optics (only while the fluid-cloud policy owns this card) and the unified colour layer,
                // which covers every notification card; a music card that keeps its highlight skips the optics.
                C17_HIGHLIGHTS.setFluidNotificationBackground(body, follow, keepHighlight);
            }
            @Override public void forget(View body) { C17_HIGHLIGHTS.forgetFluidNotificationBackground(body); }
            @Override public void lockMediaComposition(View body, boolean active) { C17_HIGHLIGHTS.setLockMediaComposition(body, active); }
            @Override public void gradient(View body, Integer argb) { C17_HIGHLIGHTS.setFluidGradient(body, argb); }
            @Override public void overlay(View body, Integer argb, java.util.function.BooleanSupplier needed) { C17_HIGHLIGHTS.setFluidOverlay(body, argb, needed); }
        });
    }
    private static final MediaPlaybackObserver MEDIA_PLAYBACK = new MediaPlaybackObserver(FLUID_STYLE::setMediaPlaying);
    private static final LockscreenStatusBlur LOCKSCREEN_BLUR = new LockscreenStatusBlur();
    private static final Set<ClassLoader> FLUID_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> FLUID_NOTIFICATION_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> AOD_MEDIA_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> FLUID_MEDIA_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final IconPackDrawing ICON_PACK_DRAWING = new IconPackDrawing();
    private static final Set<ClassLoader> HIGHLIGHT_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> KEYGUARD_PLUGIN_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> WEATHER_WIDGET_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> WEATHER_INSTANT_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Map<ClassLoader,Integer> WEATHER_WIDGET_HOOKS = new WeakHashMap<>(),WEATHER_INSTANT_HOOKS = new WeakHashMap<>();
    private static final Map<Object,Boolean> WEATHER_INSTANT_PENDING = new WeakHashMap<>();
    private static boolean weatherInstantQueued;
    private static boolean weatherWidgetRefreshQueued;
    private static final Map<View,Boolean> WEATHER_WIDGET_VIEWS = new WeakHashMap<>();
    private static final Map<View,Boolean> WEATHER_WIDGET_ROOTS = new WeakHashMap<>();
    private static final Map<View,String> WEATHER_WIDGET_DIAGNOSTIC_STATES = new WeakHashMap<>();
    private static final Map<ClassLoader,Integer> WEATHER_DIAGNOSTIC_HOOKS = new WeakHashMap<>();
    private static final Set<String> WEATHER_DIAGNOSTIC_VIEWS = new java.util.HashSet<>();
    private static final Map<Object,Long> WEATHER_DIAGNOSTIC_REVISIONS = new WeakHashMap<>();
    private static WeakReference<Object> weatherDiagnosticOwner = new WeakReference<>(null);
    private static WeakReference<View> weatherDiagnosticView = new WeakReference<>(null);
    private static long weatherDiagnosticRevision = Long.MIN_VALUE;
    private static final NotificationBigClock BIG_CLOCK = new NotificationBigClock();
    private static final SystemWeatherProvider WEATHER_SOURCE = new SystemWeatherProvider();
    private static volatile Context weatherContext;
    private static volatile boolean weatherNeeded;
    static { BIG_CLOCK.setWallpaperLink(WALLPAPER_LINK); }
    private static final NotificationClearAppearance CLEAR_APPEARANCE = new NotificationClearAppearance();
    private static final NotificationClearMotion CLEAR_MOTION = new NotificationClearMotion();
    private static final NotificationIconArea NOTIFICATION_ICONS = new NotificationIconArea();
    private static final NotificationRowIcons NOTIFICATION_ROW_ICONS = new NotificationRowIcons(NOTIFICATION_ICONS);
    private static final NativeStatusIcons STATUS_HINTS = new NativeStatusIcons();
    private static final NativeNetworkBadgeControls NATIVE_BADGE = new NativeNetworkBadgeControls();
    private static final NativeDataActivity DATA_ACTIVITY = new NativeDataActivity();
    private static final NetworkLabelFormats LABEL_FORMATS = new NetworkLabelFormats();
    private static final QsWifiAlias WIFI_ALIAS = new QsWifiAlias();
    private static final SingleNetworkLabelControls SINGLE_LABEL = new SingleNetworkLabelControls();
    private static final SingleMobileIconControls SINGLE_SIGNAL = new SingleMobileIconControls();
    private static final NativeNetworkBadgeBindings NATIVE_BADGE_BINDINGS = new NativeNetworkBadgeBindings(NATIVE_BADGE, SINGLE_SIGNAL);
    private static final NotificationNativeStack NATIVE_STACK = new NotificationNativeStack();
    private static final NotificationMiniStack MINI_STACK = new NotificationMiniStack(MAIN);
    static { MINI_STACK.setOuterStackPolicy(NATIVE_STACK::manages); }
    private static final NetworkSpeedControls NETWORK_SPEED = new NetworkSpeedControls();
    private static final SpeedPosition SPEED_POSITION = new SpeedPosition();
    private static volatile boolean bigClockEnabled;
    private static volatile NativeBackdropMotion notificationBackdropMotion;
    private static boolean tileDispatchLogged, tileScrollLogged, tileStateLogged;
    private static final NetworkBadgeControls BADGES = new NetworkBadgeControls(MAIN);
    private static final DataBatterySpacing DATA_BATTERY_SPACING = new DataBatterySpacing(MAIN);
    private static final NetworkIconOrder NETWORK_ORDER = new NetworkIconOrder();
    private static final NetworkSharedSlotLayout NETWORK_SHARED_SLOT = new NetworkSharedSlotLayout();
    private static final StatusBarFlowLayout STATUS_FLOW = new StatusBarFlowLayout();
    private static final Map<View,String> OVERFLOW_OWNERS=Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile BatteryControls BATTERY;
    private static final Map<View, Slot> SLOTS = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Integer, WeakReference<Object>> MODELS = new HashMap();
    private static final Object REFRESH_LOCK = new Object();
    private static final Object SYSTEMUI_HOOK_LOCK = new Object();
    /** Hooks on framework-wide draw paths (every Canvas, every child draw) exist only while their feature is on.
     * They hold no state of their own, so installing one later changes nothing that was already drawn. */
    private interface LazyInstall { void run() throws Throwable; }
    private static final class LazyHook {
        BooleanSupplier wanted; LazyInstall install, uninstall; boolean installed, installing, cleanupPending;
        LazyHook(BooleanSupplier wanted, LazyInstall install, LazyInstall uninstall) {
            this.wanted = wanted; this.install = install; this.uninstall = uninstall;
        }
    }
    private static final Map<String, LazyHook> LAZY_HOOKS = new LinkedHashMap<>();
    private static volatile StatusBarModule instance;

    private void lazyHook(String name, BooleanSupplier wanted, LazyInstall install) {
        lazyHook(name, wanted, install, null);
    }
    /** A framework-wide index observer can leave entirely when its native allocation is not in use. */
    private void lazyHook(String name, BooleanSupplier wanted, LazyInstall install, LazyInstall uninstall) {
        synchronized (SYSTEMUI_HOOK_LOCK) {
            LazyHook known = LAZY_HOOKS.get(name);
            if (known == null) { LAZY_HOOKS.put(name, new LazyHook(wanted, install, uninstall)); LAZY_STATE.putIfAbsent(name, "off"); }
            else if (!known.installed && !known.installing && !known.cleanupPending) { known.wanted = wanted; known.install = install; known.uninstall = uninstall; }
        }
        installLazyHooks();
    }

    /** Fixed structural counters of the same-app mini stack, for the on-demand hook cost report. */
    static String miniStackSummary() { return MINI_STACK.diagnosticSummary(); }

    /** What really got installed, for the runtime probe answer: fixed ids and counts only. */
    static Bundle hookHealth() {
        Bundle report = new Bundle();
        ArrayList<String> groups = new ArrayList<>(), complete = new ArrayList<>(), lazy = new ArrayList<>();
        for (HookGroup group : HookGroup.values()) {
            String id = HookHealth.toId(group);
            Integer count = HOOK_COUNTS.get(group);
            groups.add(id + "=" + (count == null ? 0 : count));
            if (COMPLETED_HOOK_GROUPS.contains(group)) complete.add(id);
        }
        for (Map.Entry<String, String> entry : LAZY_STATE.entrySet()) lazy.add(entry.getKey() + "=" + entry.getValue());
        report.putStringArrayList(HookHealth.GROUPS, groups);
        report.putStringArrayList(HookHealth.COMPLETE, complete);
        report.putStringArrayList(HookHealth.LAZY, lazy);
        CUSTOM_PLUGINS.health(report);
        return report;
    }

    /** Settings signals also remove explicitly reversible hooks when their exact native contract is no longer used. */
    private static void installLazyHooks() {
        StatusBarModule module = instance;
        if (module == null) return;
        synchronized (SYSTEMUI_HOOK_LOCK) {
            // An installer may register another optional contract. Work on a
            // snapshot and let that registration handle its own pending entry.
            for (Map.Entry<String, LazyHook> entry : new ArrayList<>(LAZY_HOOKS.entrySet())) {
                LazyHook hook = entry.getValue();
                if (hook.installing || hook.installed && hook.uninstall == null) continue;
                boolean starting = false;
                try {
                    // A failed reversible installer may already own live handles.
                    // Finish its original rollback even when the feature is now off.
                    if (hook.cleanupPending) {
                        hook.installing = true; hook.uninstall.run();
                        hook.cleanupPending = false; hook.installed = false;
                    }
                    boolean wanted = hook.wanted.getAsBoolean();
                    if (hook.installed && hook.uninstall != null && !wanted) {
                        hook.installing = true;
                        hook.uninstall.run(); hook.installed = false; LAZY_STATE.put(entry.getKey(), "off");
                        module.moduleLog(Log.INFO, TAG, "Hook removed on demand: " + entry.getKey());
                    }
                    if (hook.installed || !wanted) continue;
                    hook.installing = true; starting = true;
                    hook.install.run(); hook.installed = true; LAZY_STATE.put(entry.getKey(), "on");
                    module.moduleLog(Log.INFO, TAG, "Hook installed on demand: " + entry.getKey());
                }
                catch (Throwable failure) {
                    if (starting && hook.uninstall != null) {
                        hook.installed = false; hook.cleanupPending = true;
                        try { hook.uninstall.run(); hook.cleanupPending = false; }
                        catch (Throwable rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
                    }
                    LAZY_STATE.put(entry.getKey(), "failed");
                    module.moduleLog(Log.WARN, TAG, "On-demand hook unavailable: " + entry.getKey(), failure);
                }
                finally { hook.installing = false; }
            }
        }
    }
    private enum HookGroup {
        WIFI_ALIAS,
        CONTEXT, PANEL_MODE, SHADE_WALLPAPER, MODEL, DRAWABLE, BADGE, WIFI, SPEED, COMPOSE, NETWORK_ORDER, STATUS_FLOW, STATUS_HINTS, TEXT, BATTERY, DATA_SPACING, TILES, QS_APPEARANCE, QS_FULL_FILL, QS_CORNERS, QS_ICON_SIZE, ICON_MANAGEMENT, QS_PANEL_CORNERS, QS_MEDIA, BIG_CLOCK, NOTIFICATION_CLEAR, NOTIFICATION_ICONS, NOTIFICATION_ROW_ICONS, C17_HIGHLIGHTS, LOCKSCREEN_BLUR, FLUID_ACCENT, FLUID_MEDIA, FLUID_NOTIFICATION, MINI_STACK, PRIVACY
    }
    private static final Set<HookGroup> COMPLETED_HOOK_GROUPS = Collections.newSetFromMap(new ConcurrentHashMap<HookGroup, Boolean>());
    /** Lock-free copies of the installation state, read by the runtime probe answer without waiting for an installer. */
    private static final Map<HookGroup, Integer> HOOK_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, String> LAZY_STATE = new ConcurrentHashMap<>();
    private static final Map<HookGroup, Set<Executable>> INSTALLED_HOOKS = new EnumMap<>(HookGroup.class);
    private static final Set<ClassLoader> QS_CORNER_LOADERS = Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<View> QS_PLUGIN_REBINDS = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static Object composeStyleRevision;
    private static Method readComposeRevision;
    private static Method writeComposeRevision;
    private static boolean composeTextObserved;
    private static final Map<String, Drawable.ConstantState> ICONS = new ConcurrentHashMap();
    private static final List<WeakReference<ScaledDrawable>> LIVE_DRAWABLES = Collections.synchronizedList(new ArrayList());

    /* JADX INFO: Access modifiers changed from: private */
    static final class Slot {
        boolean appliedCellularMode;
        String appliedLabel;
        final int originalMinimumWidth;
        final WifiVisibilityPolicy visibility;
        int visibleState = 2;
        int tint = -855638017;
        float appearAmount = 1.0f;
        final Paint paint = new Paint(129);
        final Rect textBounds = new Rect();
        NetworkLabelGeometry.Plan labelGeometry;

        Slot(View view) {
            this.originalMinimumWidth = view.getMinimumWidth();
            this.visibility = new WifiVisibilityPolicy(view.getVisibility());
            this.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            this.paint.setFakeBoldText(true);
            this.paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private static final class ScaledDrawable extends Drawable implements Drawable.Callback {
        private Drawable delegate;
        private Drawable styled;
        private final Drawable nativeDrawable;
        private String sourceName, assetName;
        private String roleSourceName, signalRole;
        private boolean roleSingleSignal;
        private Resources baseResources;
        private int nativeAlpha = 255;
        private ColorFilter nativeFilter;
        private ColorStateList nativeTintList;
        private boolean nativeTintSet;
        private PorterDuff.Mode nativeTintMode;
        private final float density;
        private final boolean wifiIcon;
        private final int originalWidth;
        private final int originalHeight;
        private boolean composePlacement;
        private int nativeTint = Color.BLACK;
        private int appliedTint;
        private boolean tintApplied;
        private Boolean mirrored;
        private boolean hasHotspot;
        private float hotspotX,hotspotY;
        private Rect hotspotBounds;

        ScaledDrawable(Drawable drawable, Resources resources, boolean z) {
            this(drawable, resources, z, null);
        }

        ScaledDrawable(Drawable drawable, Resources resources, boolean z, Drawable nativeDrawable) {
            this.delegate = drawable;
            this.styled = drawable;
            this.nativeDrawable = nativeDrawable;
            this.density = resources.getDisplayMetrics().density;
            this.wifiIcon = z;
            this.originalWidth = Math.max(1, drawable.getIntrinsicWidth());
            this.originalHeight = Math.max(1, drawable.getIntrinsicHeight());
            Drawable initial=nativeDrawable!=null?nativeDrawable:drawable;
            nativeAlpha=initial.getAlpha();nativeFilter=initial.getColorFilter();
            nativeTint=nativeFilterColor(nativeFilter,nativeTint);
            delegate.setAlpha(nativeAlpha);delegate.setColorFilter(nativeFilter);
            setState(initial.getState());setLevel(initial.getLevel());
            setVisible(initial.isVisible(),false);setLayoutDirection(initial.getLayoutDirection());
            this.delegate.setCallback(this);
            onBoundsChange(getBounds());
            reportNetworkWidth();
        }

        void reloadSignal() {
            if (sourceName == null || wifiIcon) { chooseDelegate(); return; }
            String name = SignalResources.moduleName(sourceName, singleSignal);
            if (name == null || name.equals(assetName)) { chooseDelegate(); return; }
            Drawable replacement = loadModuleDrawable(name, baseResources);
            if (replacement == null) return;
            styled = replacement; assetName = name;
            chooseDelegate(); tintApplied = false;
        }

        private String sourceRole() {
            if(!java.util.Objects.equals(roleSourceName,sourceName)||roleSingleSignal!=singleSignal){
                roleSourceName=sourceName;roleSingleSignal=singleSignal;
                signalRole=IconPackDrawing.signalRole(sourceName,singleSignal);
            }
            return signalRole;
        }
        /** Both size controls belong outside Compose Image's native clip, exactly once. */
        private float composeScale(float dimension) {
            String group=wifiIcon?"wifi":"data";
            float legacy=FEATURES.enabled(group)?(wifiIcon?wifiIconScalePercent:dataIconScalePercent)/100f:1f;
            return NumericPolicy.scale(legacy*IconManagementRuntime.sizeForRole(sourceRole()),dimension);
        }
        private void chooseDelegate() {
            String group=wifiIcon?"wifi":"data";
            boolean systemSource=IconManagementRuntime.systemSource(sourceRole());
            Drawable wanted=nativeDrawable!=null&&(systemSource||!FEATURES.effective(group,group+"_icon_enabled"))?nativeDrawable:styled;
            if(delegate==wanted)return;
            nativeAlpha=delegate.getAlpha();
            if(!tintApplied){nativeFilter=delegate.getColorFilter();nativeTint=nativeFilterColor(nativeFilter,nativeTint);}
            delegate.setCallback(null);delegate=wanted;delegate.setCallback(this);
            delegate.setState(getState());delegate.setLevel(getLevel());delegate.setAlpha(nativeAlpha);
            delegate.setVisible(isVisible(),false);delegate.setLayoutDirection(getLayoutDirection());
            if(mirrored!=null)delegate.setAutoMirrored(mirrored);
            if(hasHotspot)delegate.setHotspot(hotspotX,hotspotY);
            if(hotspotBounds!=null)delegate.setHotspotBounds(hotspotBounds.left,hotspotBounds.top,hotspotBounds.right,hotspotBounds.bottom);
            restoreNativeFilter();setDelegateBounds(getBounds());tintApplied=false;
            reportNetworkWidth();
        }
        private void reportNetworkWidth() {
            if (!wifiIcon && (sourceName == null || sourceName.contains("_stacked_secondary_"))) return;
            SPEED_POSITION.networkDrawable(wifiIcon, Math.max(1, delegate.getIntrinsicWidth()), density);
        }
        private void restoreNativeFilter() {
            if(nativeTintList!=null)delegate.setTintList(nativeTintList);
            else if(nativeTintSet)delegate.setTint(nativeTint);
            else delegate.setTintList(null);
            if(nativeTintMode!=null)delegate.setTintMode(nativeTintMode);
            delegate.setColorFilter(nativeFilter);
        }

        @Override // android.graphics.drawable.Drawable
        public void draw(Canvas canvas) {
            chooseDelegate();
            // Layout selection is independent of the iOS glyph switch. A native
            // delegate must not paint the secondary row after its style is off.
            if(!wifiIcon&&singleSignal&&sourceName!=null&&sourceName.contains("_stacked_secondary_"))return;
            Rect bounds = getBounds();
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float offsetX = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetXdp : StatusBarModule.dataOffsetXdp);
            float offsetY = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetYdp : StatusBarModule.dataOffsetYdp);
            float scalePercent = this.composePlacement ? 100 : (this.wifiIcon ? StatusBarModule.wifiIconScalePercent : StatusBarModule.dataIconScalePercent);
            if (!this.composePlacement && FEATURES.color(wifiIcon ? "wifi" : "data")) {
                int tint = styleColor(this.wifiIcon ? "wifi" : "data", this.nativeTint);
                if (!this.tintApplied || this.appliedTint != tint) {
                    if(!tintApplied)nativeFilter=delegate.getColorFilter();
                    this.tintApplied = true;
                    this.appliedTint = tint;
                    this.delegate.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
                }
            } else if(tintApplied) { restoreNativeFilter(); tintApplied=false; }
            int iSave = canvas.save();
            try {
                float scale = NumericPolicy.scale(scalePercent / 100.0f,Math.max(bounds.width(),bounds.height()));
                canvas.translate(NumericPolicy.pixels((double)centerX+NumericPolicy.pixels(offsetX,this.density)),NumericPolicy.pixels((double)centerY+NumericPolicy.pixels(offsetY,this.density)));
                canvas.scale(scale, scale);
                canvas.translate(-centerX, -centerY);
                setDelegateBounds(bounds);
                // Drawable bounds are integers. Keep their exact half-pixel correction
                // inside this same transform for both native and replacement graphics.
                Rect graphicBounds=delegate.getBounds();
                canvas.translate(centerX-graphicBounds.exactCenterX(),centerY-graphicBounds.exactCenterY());
                int maskTint = tintApplied ? appliedTint : nativeTintList!=null
                        ? nativeTintList.getColorForState(getState(),nativeTintList.getDefaultColor()) : nativeTintSet ? nativeTint : Color.WHITE;
                if (!ICON_PACK_DRAWING.drawSignal(sourceName,delegate,canvas,maskTint,delegate.getAlpha(),
                        delegate.getColorFilter(),singleSignal&&sourceName!=null&&sourceName.contains("_stacked_secondary_"),singleSignal,composePlacement))
                    this.delegate.draw(canvas);
            } finally { canvas.restoreToCount(iSave); }
        }

        @Override // android.graphics.drawable.Drawable
        protected void onBoundsChange(Rect rect) {
            setDelegateBounds(rect);
        }

        private void setDelegateBounds(Rect outerBounds) {
            int centerX = Math.round(outerBounds.exactCenterX());
            int centerY = Math.round(outerBounds.exactCenterY());
            int width=Math.max(1,delegate.getIntrinsicWidth()),height=Math.max(1,delegate.getIntrinsicHeight());
            int left = centerX - (width / 2);
            int top = centerY - (height / 2);
            this.delegate.setBounds(left, top, left + width, top + height);
        }

        @Override // android.graphics.drawable.Drawable
        protected boolean onStateChange(int[] iArr) {
            return this.delegate.setState(iArr);
        }

        @Override // android.graphics.drawable.Drawable
        protected boolean onLevelChange(int i) {
            return this.delegate.setLevel(i);
        }

        @Override // android.graphics.drawable.Drawable
        public boolean isStateful() {
            return this.delegate.isStateful();
        }

        @Override // android.graphics.drawable.Drawable
        public int getIntrinsicWidth() {
            // A drawing offset must not change the neighboring label's layout anchor.
            chooseDelegate();return Math.max(1,delegate.getIntrinsicWidth());
        }

        @Override // android.graphics.drawable.Drawable
        public int getIntrinsicHeight() {
            chooseDelegate();return this.composePlacement && FEATURES.enabled(wifiIcon?"wifi":"data")
                    ? Math.round(ICON_SLOT_DP * this.density) : Math.max(1,delegate.getIntrinsicHeight());
        }

        @Override // android.graphics.drawable.Drawable
        public int getMinimumWidth() {
            chooseDelegate();
            return this.delegate.getMinimumWidth();
        }

        @Override // android.graphics.drawable.Drawable
        public int getMinimumHeight() {
            chooseDelegate();
            return this.delegate.getMinimumHeight();
        }

        @Override // android.graphics.drawable.Drawable
        public int getOpacity() {
            chooseDelegate();
            return this.delegate.getOpacity();
        }

        @Override // android.graphics.drawable.Drawable
        public void setAlpha(int i) {
            nativeAlpha = i;
            this.delegate.setAlpha(i);
            invalidateSelf();
        }
        @Override public int getAlpha(){return delegate.getAlpha();}
        @Override public ColorFilter getColorFilter(){return delegate.getColorFilter();}
        @Override public boolean setVisible(boolean visible,boolean restart){return super.setVisible(visible,restart)|delegate.setVisible(visible,restart);}
        @Override public boolean onLayoutDirectionChanged(int direction){return delegate.setLayoutDirection(direction);}
        @Override public void setAutoMirrored(boolean value){mirrored=value;delegate.setAutoMirrored(value);}
        @Override public boolean isAutoMirrored(){return delegate.isAutoMirrored();}
        @Override public void setHotspot(float x,float y){hasHotspot=true;hotspotX=x;hotspotY=y;delegate.setHotspot(x,y);}
        @Override public void setHotspotBounds(int left,int top,int right,int bottom){if(hotspotBounds==null)hotspotBounds=new Rect();hotspotBounds.set(left,top,right,bottom);delegate.setHotspotBounds(left,top,right,bottom);}
        @Override public void getHotspotBounds(Rect result){delegate.getHotspotBounds(result);}
        @Override public void jumpToCurrentState(){delegate.jumpToCurrentState();}

        @Override // android.graphics.drawable.Drawable
        public void setColorFilter(ColorFilter colorFilter) {
            nativeFilter = colorFilter;
            this.nativeTint = nativeFilterColor(colorFilter, this.nativeTint);
            this.tintApplied = false;
            this.delegate.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public void setTint(int color) {
            this.nativeTint = color;
            nativeTintSet=true;nativeTintList=null;
            this.tintApplied = false;
            this.delegate.setTint(color);
            invalidateSelf();
        }

        @Override public void setTintList(ColorStateList colors) {
            nativeTintList=colors;
            nativeTintSet=false;
            if (colors != null) this.nativeTint = colors.getColorForState(getState(), colors.getDefaultColor());
            this.tintApplied = false;
            this.delegate.setTintList(colors);
            invalidateSelf();
        }

        @Override // android.graphics.drawable.Drawable
        public void setTintMode(PorterDuff.Mode mode) {
            nativeTintMode=mode;
            this.delegate.setTintMode(mode);
            invalidateSelf();
        }

        @Override // android.graphics.drawable.Drawable
        public boolean getPadding(Rect rect) {
            chooseDelegate();
            return this.delegate.getPadding(rect);
        }

        @Override // android.graphics.drawable.Drawable.Callback
        public void invalidateDrawable(Drawable drawable) {
            invalidateSelf();
        }

        @Override // android.graphics.drawable.Drawable.Callback
        public void scheduleDrawable(Drawable drawable, Runnable runnable, long j) {
            scheduleSelf(runnable, j);
        }

        @Override // android.graphics.drawable.Drawable.Callback
        public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
            unscheduleSelf(runnable);
        }
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        moduleLog(Log.INFO, TAG, "Module entry reached");
        frameworkLogger = this;
        instance = this;
        try {
            moduleLog(Log.INFO, TAG, "Module loaded: process=" + param.getProcessName()
                    + " api=" + getApiVersion()
                    + " framework=" + getFrameworkName()
                    + " version=" + getFrameworkVersion());
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not read framework details after module entry", error);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if ("com.android.systemui".equals(param.getPackageName())) {
            installSystemUiHooks(param.getDefaultClassLoader(), "package-loaded");
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if ("com.android.systemui".equals(param.getPackageName())) {
            installSystemUiHooks(param.getClassLoader(), "package-ready");
        }
    }

    private void installSystemUiHooks(ClassLoader classLoader, String phase) {
        if (classLoader == null) {
            moduleLog(Log.WARN, TAG, "SystemUI callback had no class loader at " + phase);
            return;
        }
        if (!ModuleVariant.DABING) CUSTOM_PLUGINS.rememberLoader(classLoader);
        if (!ModuleVariant.DABING && "package-ready".equals(phase)) CUSTOM_PLUGINS.retryReadyLoader();
        synchronized (SYSTEMUI_HOOK_LOCK) {
            if ("package-ready".equals(phase)) refreshPendingSystemUiHooks(classLoader);
            // A real readiness event retries pending sites before the completed-group fast path.
            installLazyHooks();
            // Optional media contracts may appear after the initial factory loader becomes available.
            installCapsuleMediaPersistence(classLoader);
            if (COMPLETED_HOOK_GROUPS.size() == HookGroup.values().length) {
                moduleLog(Log.INFO, TAG, "All SystemUI hook groups already installed at " + phase);
                return;
            }
            moduleLog(Log.INFO, TAG, "Installing unfinished SystemUI hook groups at " + phase);
            installC17HighlightRemoval(classLoader);
            try {
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.CONTEXT) && installSystemUiContextHook(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.CONTEXT);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.PANEL_MODE) && installPanelModeHooks(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.PANEL_MODE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.SHADE_WALLPAPER)) {
                    // The window-wide draw hook exists only while a panel wallpaper is on and has a picture.
                    COMPLETED_HOOK_GROUPS.add(HookGroup.SHADE_WALLPAPER);
                    final ClassLoader shadeLoader = classLoader;
                    lazyHook("shade_wallpaper", SHADE_WALLPAPER::wanted, () -> {
                        if (!installShadeWallpaperHooks(shadeLoader)) throw new IllegalStateException("shade wallpaper hosts unavailable");
                    });
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.PRIVACY)) {
                    // The privacy hooks exist only while the dot is hidden or the capsule is on.
                    COMPLETED_HOOK_GROUPS.add(HookGroup.PRIVACY);
                    final ClassLoader privacyLoader = classLoader;
                    final List<XposedInterface.HookHandle> privacyHooks = new ArrayList<>();
                    lazyHook("privacy_indicator", () -> privacySettings.wanted(),
                            () -> installPrivacyHooks(privacyLoader, privacyHooks),
                            () -> { for (XposedInterface.HookHandle handle : privacyHooks) handle.unhook(); privacyHooks.clear(); });
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.MODEL) && installNetworkModelHooks(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.MODEL);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.DRAWABLE) && installDrawableOverrides(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.DRAWABLE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.BADGE) && installBadgeHiding(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.BADGE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.WIFI) && installWifiSlot(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.WIFI);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.WIFI_ALIAS) && installWifiAlias(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.WIFI_ALIAS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.SPEED) && installSpeedControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.SPEED);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.COMPOSE) && installComposeSupport(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.COMPOSE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.NETWORK_ORDER) && installNetworkIconOrder(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.NETWORK_ORDER);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.STATUS_FLOW) && installStatusBarFlow(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.STATUS_FLOW);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.STATUS_HINTS) && installNativeStatusIcons(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.STATUS_HINTS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.TEXT) && installTextControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.TEXT);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.LOCKSCREEN_BLUR) && installLockscreenBlur(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.LOCKSCREEN_BLUR);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.FLUID_ACCENT) && installFluidAccentLoaders(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.FLUID_ACCENT);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.FLUID_NOTIFICATION) && installFluidNotificationStyle(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.FLUID_NOTIFICATION);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.BATTERY) && installBatteryControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.BATTERY);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.DATA_SPACING) && installDataBatterySpacing(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.DATA_SPACING);
                }
                // The QS page-turn effect was withdrawn in code161; its saved values stay importable but inert.
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_APPEARANCE) && installQsAppearance(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_APPEARANCE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_FULL_FILL) && installQsFullFill(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_FULL_FILL);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_CORNERS) && installQsCorners(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_CORNERS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_ICON_SIZE) && installQsIconSize(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_ICON_SIZE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.ICON_MANAGEMENT) && installIconManagement(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.ICON_MANAGEMENT);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_PANEL_CORNERS) && installQsPanelCorners(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_PANEL_CORNERS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_MEDIA) && installQsMediaAppearance(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_MEDIA);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.BIG_CLOCK) && installNotificationBigClock(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.BIG_CLOCK);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.NOTIFICATION_CLEAR) && installNotificationClearAppearance(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.NOTIFICATION_CLEAR);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.NOTIFICATION_ICONS) && installNotificationIcons(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.NOTIFICATION_ICONS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.NOTIFICATION_ROW_ICONS) && installNotificationRowIcons(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.NOTIFICATION_ROW_ICONS);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.MINI_STACK) && installNotificationMiniStack(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.MINI_STACK);
                }
                // TILES and FLUID_MEDIA install from their own installers and are never marked complete here; a group that
                // created hooks is installed whether or not it is in the completed set.
                // They install on demand (see HookHealth), so absent at startup is normal and must not raise a warning.
                List<HookGroup> missing = new ArrayList<>();
                for (HookGroup group : HookGroup.values())
                    if (group != HookGroup.TILES && group != HookGroup.FLUID_MEDIA
                            && !COMPLETED_HOOK_GROUPS.contains(group)
                            && !HOOK_COUNTS.containsKey(group)) missing.add(group);
                moduleLog(missing.isEmpty() ? Log.INFO : Log.WARN, TAG, missing.isEmpty()
                        ? "SystemUI hook groups after " + phase + ": all installed"
                        : "SystemUI hook groups after " + phase + ": not installed " + missing);
            } catch (Throwable error) {
                moduleLog(Log.ERROR, TAG, "SystemUI hook setup failed at " + phase, error);
            }
        }
    }



    private void retryPendingSite(String name, LazyInstall registration) {
        LazyHook hook = LAZY_HOOKS.get(name);
        if (hook == null || hook.installed || hook.installing) return;
        try { registration.run(); }
        catch (Throwable unavailable) { moduleLog(Log.WARN, TAG, "Pending native hook registration unavailable: " + name, unavailable); }
    }
    /** The factory loader can differ from the final package loader. Rebind only
     * still-pending main-package sites; plugin-owned contracts retain their own loader. */
    private void refreshPendingSystemUiHooks(ClassLoader loader) {
        retryPendingSite("statusbar_flow_layout", () -> installStatusBarFlow(loader));
        retryPendingSite("network_order_child_index", () -> installNetworkIconOrder(loader));
        retryPendingSite("network_shared_native_placement", () -> installNetworkIconOrder(loader));
        retryPendingSite("status_hint_child_index", () -> installNativeStatusIcons(loader));
        retryPendingSite("lock_hide", () -> installLockscreenControls(loader));
        retryPendingSite("notification_mini_stack", () -> installNotificationMiniStack(loader));
        retryPendingSite("qs_media_child", () -> installQsMediaAppearance(loader));
        retryPendingSite("shade_wallpaper", () -> lazyHook("shade_wallpaper", SHADE_WALLPAPER::wanted, () -> {
            if (!installShadeWallpaperHooks(loader)) throw new IllegalStateException("shade wallpaper hosts unavailable");
        }));
    }

    private void installHook(HookGroup group, Executable method, XposedInterface.Hooker hooker) {
        synchronized (SYSTEMUI_HOOK_LOCK) {
            Set<Executable> installed = INSTALLED_HOOKS.get(group);
            if (installed == null) {
                installed = new HashSet<>();
                INSTALLED_HOOKS.put(group, installed);
            }
            if (installed.contains(method)) {
                return;
            }
            createHook(group, method, hooker);
            installed.add(method);
            HOOK_COUNTS.merge(group, 1, Integer::sum);
        }
    }
    private XposedInterface.HookHandle createHook(HookGroup group, Executable method, XposedInterface.Hooker hooker) {
        final boolean safeObserver = observesInSafeMode(group, method);
        final HookTrace.Site site = HookTrace.site(group.name(), method);
        return hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                (ModuleLifecycle.removed() || FEATURES.safeMode() && !safeObserver) ? chain.proceed()
                        : HookTrace.on() ? HookTrace.run(site, hooker, chain) : hooker.intercept(chain));
    }

    /** Keep native bindings/current values alive; drawing and resource replacement remain bypassed. */
    private static boolean observesInSafeMode(HookGroup group, Executable executable) {
        if (group == HookGroup.CONTEXT || group == HookGroup.MODEL || group == HookGroup.PANEL_MODE) return true;
        String ownerName = executable.getDeclaringClass().getName();
        boolean lockscreenOwner = group == HookGroup.TEXT && (ownerName.equals(LockscreenControls.SINGLE)
                || ownerName.equals(LockscreenControls.DUAL) || ownerName.equals(LockscreenControls.RED)
                || ownerName.equals(LockscreenControls.CUSTOM) || ownerName.equals(LockscreenControls.LOCK)
                || ownerName.startsWith("com.oplus.keyguard.clock."));
        // Only the Wi-Fi constructor installs a native owner/state binding. Painter/resource
        // constructors can replace drawing and must continue straight into their native code.
        if (!(executable instanceof Method)) return group == HookGroup.WIFI || lockscreenOwner
                || group == HookGroup.BIG_CLOCK&&ownerName.equals("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable")
                || group == HookGroup.NOTIFICATION_ROW_ICONS
                || group == HookGroup.LOCKSCREEN_BLUR || group == HookGroup.FLUID_ACCENT || group == HookGroup.FLUID_MEDIA;
        Method method = (Method) executable;
        for (Class<?> parameter : method.getParameterTypes()) if (Canvas.class.isAssignableFrom(parameter)) return false;
        String name = method.getName();
        switch (name) {
            case "onInit": case "initView": case "onFinishInflate":
            case "onAttachedToWindow": case "onDetachedFromWindow": case "onLayout":
            case "onConfigurationChanged": case "onConfigChanged": case "onDensityOrFontScaleChanged":
            case "onVisibilityChanged": case "onWindowVisibilityChanged": case "onRecycle":
                return true;
            default: break;
        }
        switch (group) {
            case ICON_MANAGEMENT:
                // Fixed binding callbacks retain the latest native object while replacement is disabled.
                return true;
            case LOCKSCREEN_BLUR:
                return name.matches("start|setState|setIsDozing|setDozeAmountInternal|setAndInstrumentDozeAmount"
                        + "|notifyKeyguardState|notifyKeyguardDismissAmountChanged|notifyKeyguardDoneFading"
                        + "|notifyKeyguardFadingAway|notifyKeyguardGoingAway|update"
                        + "|updateScrimColor|updateScrims|applyAndDispatchState|onPreDraw"
                        + "|setViewAlpha|setTint|updateColorWithTint|invalidateDrawable"
                        + "|initializeSurface|updateSurface|reportSurfaceDestroyed|detach|reportVisibility|doVisibilityChanged"
                        + "|doAmbientModeChanged|onScreenTurningOnChanged|showScreenshotOfWallpaper|freeze|cleanUpScreenshotSurfaceControl|drawFrameOnCanvas"
                        + "|onWallpaperSurfaceChanged|onPictorialWallpaperSurfaceChanged|onSystemWallpaperChange|checkIsWallpaperMatrixChanged|setValue");
            case FLUID_ACCENT: case FLUID_MEDIA: case FLUID_NOTIFICATION:
                return !name.equals("getColor");
            case BADGE:
                return name.matches("setVisibility|setId|inflate|bind|bindEx");
            case TEXT:
                // Each native setter records its original value; runtime masters are off,
                // so the corresponding TEXT method returns that same native value.
                return (ownerName.equals(SystemWeatherText.WIDGET_CARD)&&name.equals("a")
                        ||ownerName.equals(SystemWeatherText.WIDGET_ENGINE)&&name.equals("k")
                        ||ownerName.equals(SystemWeatherText.WIDGET_VIEW)&&name.equals("access$setInstantCardWidget$p")
                        ||ownerName.equals("kswc.Ys")&&name.equals("onRenderSuccess")
                        ||ownerName.equals("a.a.a.v110017.ip0")&&name.equals("g"))
                        ||lockscreenOwner&&name.matches("sendWeatherData|getCurrentData|sendWeatherData\\$lambda\\$15|getAndListenWeather|initWeatherEffectController|getExtraInfoManager|setExtWeather")
                        ||name.matches("setText|setContentDescription|setTextColor|setTextSize|setTypeface|setFontVariationSettings|setLetterSpacing|setTextWithAnima|updateTextSize\\$1|setId|assignParent")
                        || lockscreenOwner && name.matches("updateDate|updateTime|refresh|refreshDate|updateIcon|updateColorAndBackgroundVisibility"
                                +"|tryUpdateText|render|refreshDateMessageContent|updateDateContent");
            case SPEED:
                return name.equals("setIconTint");
            case WIFI:
                return name.matches("setVisibility|setVisibleState|setAppearAmount|onDarkChangedWithContrast|setStaticDrawableColor|bind|emit");
            case BATTERY:
                return name.matches("setColors|setBatteryCharge|setBatteryStyleDrawable|bind\\$updateChargingView|bind\\$updateBatteryContentView|bind\\$updateBatteryIconStyle");
            case DATA_SPACING:
                return name.matches("onMeasure|onViewAdded|onViewRemoved|setLayoutParams|getNetworkTypeTextInfo|NetworkTypeText.*");
            case NETWORK_ORDER:
                return name.matches("onMeasure|updateStates|onViewAdded|onViewRemoved|setModifier|SignalIcon.*|.*NetworkTypeText.*");
            case COMPOSE:
                // Native restart groups may hold our badge parameters. Safe mode must return
                // their original values rather than bypassing the restoration observer.
                return name.matches(".*NetworkTypeText.*|StackedMobileText-.*|getStrategy")
                        || name.equals("Image") && method.getDeclaringClass().getName().equals("androidx.compose.foundation.ImageKt")
                        || name.equals("paint$default") && method.getDeclaringClass().getName().equals("androidx.compose.ui.draw.PainterModifierKt")
                        || name.equals("invoke") && method.getDeclaringClass().getName().endsWith("OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda2");
            case BIG_CLOCK:
                if(ownerName.equals("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurHelper")
                        ||ownerName.equals("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable")
                        ||ownerName.equals("com.oplus.posteffect.BlurParam")
                        ||ownerName.equals("com.oplus.posteffect.drawable.BaseDrawable")
                        ||ownerName.equals("com.oplus.posteffect.drawable.ContinuousBlurDrawable"))return true;
                // Preserve actual spring progress, source/header binding, scroll and native
                // padding snapshots even on a boot that started with safe mode enabled.
                return name.matches("setExpandedHeightInternal|onPanelStateChanged\\$1|onStateChanged|setExpansionHeight|setExpanded|updateExpansion"
                        + "|onFractionChanged|onFractionEnd|onFlingEnd|notifyExpandingFinished|instantCollapse|onTrackingStarted\\$2|onTrackingStopped"
                        + "|setTranslationYValue|ensureProgressiveHelperReady|resetAllViewsTransitionAlpha|updateClickAbilities"
                        + "|onAnimatedValue|resetHorizontalTranslationView|syncHorizontalTranslationDown|setMView|setFakeView|bindRealView|unbindRealView|clearFakeView"
                        + "|onViewAttached|onViewDetached|onDestroy|onTrackingStarted\\$1|onTrackingStopped\\$1|expandQSPanelWithoutAnimate|collapseQSPanelWithoutAnimate"
                        + "|updateStatusBarTopMargin|setStatusBarAlpha|updateElementLoc\\$1|onSystemIconScaleChanged|resetState|updateLeftAreaState"
                        + "|onPageScrolled|onScrollStateChanged|setFakeTransitionAlpha|setAlpha|setVisibility|setTranslationY|setTransitionAlpha"
                        + "|setOwnScrollY|updateOverDistance|resetOverDistance|setIntrinsicPadding|updateTopPadding|setRawFraction|setBlurFraction|updateClock|onMeasure|onAttachedToWindow|setId");
            case QS_APPEARANCE:
                return name.matches("onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged|onDrawableUpdate|g");
            case QS_FULL_FILL:
                return name.matches("onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged|onDrawableUpdate|onMeasure|handleCellLayoutConstraintChanged|getColorByTileState|setBeingDragged|setEditMode");
            case QS_CORNERS:
                return name.matches("onOutlineUpdate|onDrawableUpdate|onMeasure|onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged|setBlockPathProvider");
            case QS_ICON_SIZE:
                return name.matches("onMeasure|handleCellLayoutConstraintChanged|onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged"
                        + "|setLayoutParams|setAlpha|g|i|setCustomConfig|setDeviceIcon|setDeviceIconWithAnimation");
            case NOTIFICATION_ICONS:
                return name.matches("setMaxIconsAmount|calculateIconXTranslations|onMeasure|applyIconStates|onViewAdded|onViewRemoved"
                        + "|setVisibleState|setNotification|setStaticDrawableColor|onDarkChanged|bindWhileAttached");
            case NOTIFICATION_ROW_ICONS:
                return name.matches("onContentUpdated|onReinflated|resolveHeaderViews|setEntryAdapter");
            case QS_PANEL_CORNERS:
                return name.matches("onOutlineUpdate|onDrawableUpdate|onMeasure|onSizeChanged|initView"
                        + "|updateBaseMixColorDrawableRadius|setBackgroundRadius|setProgressRadius|g|h|setCornerRadiusToDrawable|setBlurDrawable"
                        + "|bindData|onPanelSizeChanged|updateColor|onRecycled|setListening|onCreateView");
            case QS_MEDIA:
                return name.matches("bindCoverImg|resetCoverImg|bindMediaDataInner|onDrawableUpdate|updateColor|onThemeApplied|resetView|setCoverImg|setImageDrawable");
            case NOTIFICATION_CLEAR:
                return name.matches("bindViews|updatePlatformBlurDrawable(?:\\$default)?|onHeightUpdated|updateClearAllPosition"
                        + "|updateLayoutParams|setVisible|onStateChanged|updateQsExpansion|updateVisibility|setShowDismiss|setQsCustomizerShowing"
                        + "|setAlpha|setTranslationY");
            case TILES:
                return name.matches("onPageScrolled|onScrollStateChanged|onPageScrollStateChanged");
            default:
                return false;
        }
    }

    private static void logFrameworkStage(int priority, String message) {
        ModuleDiagnostics.info("systemui", diagnosticMessage(message));
    }

    private static void logFrameworkStage(int priority, String message, Throwable error) {
        ModuleDiagnostics.error("systemui", diagnosticMessage(message), error);
    }

    private void moduleLog(int priority, String tag, String message) {
        // Warnings and errors keep their severity so the in-app log view can separate them from routine progress.
        if (priority >= Log.ERROR) ModuleDiagnostics.error("hooks", diagnosticMessage(message), null);
        else if (priority >= Log.WARN) ModuleDiagnostics.warn("hooks", diagnosticMessage(message));
        else ModuleDiagnostics.info("hooks", diagnosticMessage(message));
    }

    private void moduleLog(int priority, String tag, String message, Throwable error) {
        ModuleDiagnostics.error("hooks", diagnosticMessage(message), error);
    }

    private boolean installNotificationIcons(ClassLoader loader) {
        try {
            NOTIFICATION_ICONS.resolve(loader);
            Class<?> container=loader.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer");
            Method draw=ViewGroup.class.getDeclaredMethod("dispatchDraw",Canvas.class);
            deoptimize(draw);
            installHook(HookGroup.NOTIFICATION_ICONS,draw,chain -> {
                if(!NOTIFICATION_ICONS.ownsDrawing(chain.getThisObject()))return chain.proceed();
                return NOTIFICATION_ICONS.draw((ViewGroup)chain.getThisObject(),(Canvas)chain.getArg(0),chain::proceed);
            });
            for(Method method:container.getDeclaredMethods())if(method.getName().matches("applyIconStates|onLayout|onViewAdded|onViewRemoved|onConfigurationChanged")) {
                deoptimize(method);
                installHook(HookGroup.NOTIFICATION_ICONS,method,chain -> {Object result=chain.proceed();NOTIFICATION_ICONS.changed((View)chain.getThisObject());return result;});
            }
            try {
                Method maximum=container.getDeclaredMethod("setMaxIconsAmount",Integer.TYPE);
                deoptimize(maximum);
                installHook(HookGroup.NOTIFICATION_ICONS,maximum,chain -> {
                    int requested=(Integer)chain.getArg(0);
                    int applied=NOTIFICATION_ICONS.nativeMaxIcons((View)chain.getThisObject(),requested);
                    return applied==requested?chain.proceed():chain.proceed(new Object[]{applied});
                });
            } catch (NoSuchMethodException olderContainer) {
                moduleLog(Log.INFO,TAG,"Native notification maximum unavailable; visual customization retained");
            }
            for (Method event:container.getDeclaredMethods()) {
                String name=event.getName();
                if (!name.matches("onMeasure|calculateIconXTranslations|onAttachedToWindow|onDetachedFromWindow")) continue;
                deoptimize(event);
                installHook(HookGroup.NOTIFICATION_ICONS,event,chain -> {
                    View owner=(View)chain.getThisObject();
                    if (name.equals("onMeasure") || name.equals("calculateIconXTranslations")) NOTIFICATION_ICONS.beforeLayout(owner);
                    Object result;
                    if(name.equals("calculateIconXTranslations")) {
                        try(NotificationIconArea.SpacingScope scope=NOTIFICATION_ICONS.enterSpacingCalculation(owner)){result=chain.proceed();}
                    }else result=chain.proceed();
                    if(name.equals("onMeasure"))NOTIFICATION_ICONS.measureSpacing(owner,(Integer)chain.getArg(0));
                    if (name.equals("onDetachedFromWindow")) NOTIFICATION_ICONS.detach(owner);
                    else if (name.equals("onAttachedToWindow")) NOTIFICATION_ICONS.changed(owner);
                    return result;
                });
            }
            installNotificationIconSpacing(loader,container);
            Class<?> icon=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
            Method iconDraw=icon.getDeclaredMethod("onDraw",Canvas.class);deoptimize(iconDraw);
            installHook(HookGroup.NOTIFICATION_ICONS,iconDraw,chain->NOTIFICATION_ICONS.drawNativeIcon(
                    (View)chain.getThisObject(),(Canvas)chain.getArg(0),chain::proceed));
            Method glyphDraw=ImageView.class.getDeclaredMethod("onDraw",Canvas.class);deoptimize(glyphDraw);
            installHook(HookGroup.NOTIFICATION_ICONS,glyphDraw,chain->NOTIFICATION_ICONS.drawNativeGlyph(
                    (View)chain.getThisObject(),(Canvas)chain.getArg(0),chain::proceed));
            for(Method method:icon.getDeclaredMethods())if(method.getName().matches("setVisibleState|setNotification|setStaticDrawableColor|setDecorColor|onDarkChanged|set|updateDrawable|maybeUpdateIconScaleDimens|updateIconDimens|setIncreasedSize|onConfigurationChanged|onLayout")) {
                deoptimize(method);
                String name=method.getName();
                boolean notificationChanged=name.matches("setNotification|set|updateDrawable");
                installHook(HookGroup.NOTIFICATION_ICONS,method,chain -> {
                    Object result=chain.proceed();View view=(View)chain.getThisObject();
                    if(notificationChanged)NOTIFICATION_ICONS.notificationChanged(view);
                    else NOTIFICATION_ICONS.changed(view);return result;
                });
            }
            try {
                Class<?> binder=loader.loadClass("com.oplus.systemui.statusbar.icon.ui.binder.OplusNotificationIconAreaBinder");
                Method bind=binder.getDeclaredMethod("bindWhileAttached",ViewGroup.class,int.class);
                deoptimize(bind);
                installHook(HookGroup.NOTIFICATION_ICONS,bind,chain -> {
                    Object result=chain.proceed();
                    NOTIFICATION_ICONS.bindPhoneArea((ViewGroup)chain.getArg(0));
                    return result;
                });
            } catch(ClassNotFoundException|NoSuchMethodException olderStatusBar) { }
            return true;
        } catch(Throwable unsupported) {moduleLog(Log.WARN,TAG,"Native notification icon customization unavailable",unsupported);return false;}
    }

    /** Same-app mini stack: its own group, never nested in the big-clock chain, and hooked only once it is switched on. */
    private boolean installNotificationMiniStack(ClassLoader loader) {
        lazyHook("notification_mini_stack", MINI_STACK::wanted, () -> installMiniStackHooks(loader));
        return true;
    }

    /** Hide the privacy dot and feed the capsule. Items come from the controller's per-callback notification; the dot is
     *  handled on whichever controller this ROM uses (AOSP and/or ColorOS), at least one must be present. */
    private void installPrivacyHooks(ClassLoader loader, List<XposedInterface.HookHandle> handles) throws Throwable {
        // The list setter is never called (Kotlin writes the field) and R8 inlines NotifyChangesToCallback.run() into the
        // controller's notify lambda. A registered callback is an interface dispatch that cannot be inlined:
        // PhoneStatusBarPolicy is registered from startup and receives every change on the main thread.
        Method changed = loader.loadClass("com.android.systemui.statusbar.phone.PhoneStatusBarPolicy")
                .getDeclaredMethod("onPrivacyItemsChanged", List.class);
        deoptimize(changed);
        handles.add(createHook(HookGroup.PRIVACY, changed, chain -> {
            Object result = chain.proceed();
            Object list = chain.getArg(0);
            if (list instanceof List) PRIVACY_CAPSULE.itemsChanged(context, (List<?>) list);
            return result;
        }));
        int controllers = 0;
        try {
            // AOSP controller: showing animates the corner container itself, so while hidden it is simply not shown.
            Class<?> aosp = loader.loadClass("com.android.systemui.statusbar.events.PrivacyDotViewControllerImpl");
            Method show = aosp.getDeclaredMethod("showDotView", View.class, Boolean.TYPE);
            deoptimize(show);
            handles.add(createHook(HookGroup.PRIVACY, show, chain -> PRIVACY_DOT.hidden() ? null : chain.proceed()));
            controllers++;
        } catch (ReflectiveOperationException absent) { /* not on this ROM */ }
        try {
            // ColorOS controller: keeps its own corner windows; their containers stay transparent while hidden.
            Class<?> oem = loader.loadClass(PrivacyDotHider.CONTROLLER);
            Method create = oem.getDeclaredMethod("createDotView", Integer.TYPE, Context.class);
            Method next = oem.getDeclaredMethod("setNextViewState", loader.loadClass("com.oplus.systemui.privacy.ViewState"));
            deoptimize(create); deoptimize(next);
            handles.add(createHook(HookGroup.PRIVACY, create, chain -> {
                Object view = chain.proceed(); PRIVACY_DOT.created(view); return view;
            }));
            handles.add(createHook(HookGroup.PRIVACY, next, chain -> {
                Object result = chain.proceed();
                PRIVACY_DOT.observed(chain.getThisObject());
                return result;
            }));
            controllers++;
        } catch (ReflectiveOperationException absent) { /* not on this ROM */ }
        try {
            // The status bar's privacy event: hides both sides, shows an orange camera/microphone chip, then shrinks it
            // into the corner dot. While the dot is hidden the whole event is dropped; battery and other events still run.
            Class<?> scheduler = loader.loadClass("com.android.systemui.statusbar.events.SystemStatusAnimationSchedulerImpl");
            Class<?> privacyEvent = loader.loadClass("com.android.systemui.statusbar.events.PrivacyEvent");
            Method onEvent = scheduler.getDeclaredMethod("onStatusEvent", loader.loadClass("com.android.systemui.statusbar.events.StatusEvent"));
            deoptimize(onEvent);
            handles.add(createHook(HookGroup.PRIVACY, onEvent, chain ->
                    PRIVACY_DOT.hidden() && privacyEvent.isInstance(chain.getArg(0)) ? null : chain.proceed()));
        } catch (ReflectiveOperationException absent) { /* not on this ROM */ }
        if (controllers == 0) throw new ClassNotFoundException("No privacy dot controller");
    }

    private void installMiniStackHooks(ClassLoader loader) throws Throwable {
        if (!NotificationMiniStack.supports(loader)) {
            MINI_STACK.unsupported("contract");
            ModuleDiagnostics.info("mini-stack", "ColorOS 17 group contract not found; native notification groups retained");
            return;
        }
        NotificationMiniStack.Targets targets;
        try {
            // Every member is resolved and type-checked here, before the first hook exists.
            targets = new NotificationMiniStack.Targets(loader);
            MINI_STACK.bind(targets);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            MINI_STACK.unsupported(unavailable.getClass().getSimpleName());
            ModuleDiagnostics.error("mini-stack", "Mini stack contract unavailable; native notification groups retained", unavailable);
            return;
        }
        try {
            Method badgeAlpha=View.class.getDeclaredMethod("setAlpha",Float.TYPE);
            deoptimize(badgeAlpha);
            installHook(HookGroup.MINI_STACK,badgeAlpha,chain->{
                float requested=(Float)chain.getArg(0);
                float effective=MINI_STACK.nativeBadgeAlpha((View)chain.getThisObject(),requested);
                return effective==requested?chain.proceed():chain.proceed(new Object[]{effective});
            });
            deoptMiniBadgeAlphaCallers(loader,targets);
            installMiniHierarchyHooks();
            MINI_STACK.visualHooksReady(false);
            try {
                for(Method contentMutation:new Method[]{targets.setGrouped,targets.selectLayout}){
                    deoptimize(contentMutation);
                    installHook(HookGroup.MINI_STACK,contentMutation,chain->{
                        try{return chain.proceed();}finally{MINI_STACK.contentUpdated(chain.getThisObject());}
                    });
                }
                MINI_STACK.visualHooksReady(true);
            }catch(Throwable optionalVisualCallbacks){MINI_STACK.visualHooksReady(false);}
            installHook(HookGroup.MINI_STACK, targets.collapsedHeight, chain -> MINI_STACK.height(chain.getThisObject(), false, chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.containerMinimum, chain -> MINI_STACK.height(chain.getThisObject(), false, chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.intrinsic, chain -> MINI_STACK.height(chain.getThisObject(), true, chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.updateChildren,
                    chain -> MINI_STACK.updateChildren(chain.getThisObject(),chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.rowDraw, chain -> MINI_STACK.rowDraw(chain.getThisObject(), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.visibleType, chain -> MINI_STACK.contentType(chain.getThisObject(), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.heightType, chain -> MINI_STACK.contentType(chain.getThisObject(), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.drawChild, chain -> MINI_STACK.drawChild(chain.getThisObject(),
                    (Canvas) chain.getArg(0), (View) chain.getArg(1), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.setExpanded, chain -> MINI_STACK.transition(NotificationMiniStack.SET_EXPANDED,
                    chain.getThisObject(), chain.getArg(0), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.setChanging, chain -> MINI_STACK.transition(NotificationMiniStack.SET_CHANGING,
                    chain.getThisObject(), chain.getArg(0), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.rowSetSwiping, chain -> MINI_STACK.transition(NotificationMiniStack.ROW_SWIPING,
                    chain.getThisObject(), chain.getArg(0), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.setSwiping, chain -> MINI_STACK.transition(NotificationMiniStack.CONTAINER_SWIPING,
                    chain.getThisObject(), chain.getArg(0), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.setAnimating, chain -> MINI_STACK.transition(NotificationMiniStack.SET_ANIMATING,
                    chain.getThisObject(), chain.getArg(0), chain::proceed));
            installHook(HookGroup.MINI_STACK, targets.updateBackground, chain -> {
                Object result = chain.proceed(); MINI_STACK.backgroundUpdated(chain.getThisObject()); return result;
            });
            installHook(HookGroup.MINI_STACK, targets.resetRoundness, chain -> {
                Object result = chain.proceed(); MINI_STACK.roundnessReset(chain.getThisObject(), chain.getArg(0)); return result;
            });
            installHook(HookGroup.MINI_STACK, targets.click, chain -> MINI_STACK.click(chain.getArg(0)) ? null : chain.proceed());
            MINI_STACK.hooksReady(true);
            ModuleDiagnostics.info("mini-stack", "Mini stack hooks ready");
        } catch (Throwable failure) {
            // Hooks that did install stay inert: each one passes straight through while the stack is not ready.
            MINI_STACK.hooksReady(false);
            ModuleDiagnostics.error("mini-stack", "Mini stack hooks incomplete; native notification groups retained", failure);
        }
    }

    private void deoptMiniBadgeAlphaCallers(ClassLoader loader,NotificationMiniStack.Targets targets) {
        // The audited OEM count setters call public TextView.setAlpha; compiled wrappers can inline it.
        String managerName="com.oplus.systemui.notification.row.oplusgroup.GroupIconManager";
        try {
            Class<?> manager=loader.loadClass(managerName);
            deoptimize(manager.getDeclaredMethod("initPillBgAndNumberColor",android.widget.FrameLayout.class,
                    loader.loadClass(managerName+"$IconInfo"),TextView.class));
            deoptimize(manager.getDeclaredMethod("updatePillBgAndNumberColor",android.widget.FrameLayout.class,TextView.class,
                    loader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")));
        }catch(ReflectiveOperationException|LinkageError unavailable) {
            ModuleDiagnostics.info("mini-stack","Optional native count color callers unavailable");
        }
        try {
            deoptimize(loader.loadClass(managerName+"$initEntryIconDrawable$1").getDeclaredMethod("invokeSuspend",Object.class));
        }catch(ReflectiveOperationException|LinkageError unavailable) { }
        for(Class<?> caller:new Class<?>[]{targets.groupExtension,targets.containerExtension})try {
            deoptimize(caller.getDeclaredMethod("updatePillBgAndNumberColor"));
        }catch(ReflectiveOperationException|LinkageError unavailable) { }
    }

    /** Observe the actual child arrays, including attach/detach and transient RemoteViews. */
    private void installMiniHierarchyHooks(){
        MINI_STACK.hierarchyHooksReady(false);
        try {
            Class<?> group=ViewGroup.class;
            Method[] mutations={group.getDeclaredMethod("addInArray",View.class,Integer.TYPE),
                    group.getDeclaredMethod("removeFromArray",Integer.TYPE),
                    group.getDeclaredMethod("removeFromArray",Integer.TYPE,Integer.TYPE),
                    group.getDeclaredMethod("detachAllViewsFromParent"),
                    group.getDeclaredMethod("removeAllViewsInLayout"),
                    group.getDeclaredMethod("addTransientView",View.class,Integer.TYPE),
                    group.getDeclaredMethod("removeTransientView",View.class)};
            // The verified framework callers can inline the private array operations.
            // These are structural entry points, never per-frame getChildAt/draw calls.
            for(Method caller:group.getDeclaredMethods())if(caller.getName().matches(
                    "addViewInner|removeViewInternal|removeViewsInternal|attachViewToParent|detachViewFromParent|detachViewsFromParent|bringChildToFront|removeAllViews"))
                deoptimize(caller);
            for(Method mutation:mutations){
                if(mutation.getReturnType()!=Void.TYPE)throw new NoSuchMethodException(mutation.toString());
                mutation.setAccessible(true);deoptimize(mutation);
                installHook(HookGroup.MINI_STACK,mutation,chain->{
                    ViewGroup owner=(ViewGroup)chain.getThisObject();
                    MINI_STACK.hierarchyChanged(owner);
                    try{return chain.proceed();}finally{MINI_STACK.hierarchyChanged(owner);}
                });
            }
            MINI_STACK.hierarchyHooksReady(true);
        }catch(Throwable unavailable){
            MINI_STACK.hierarchyHooksReady(false);
            ModuleDiagnostics.info("mini-stack","Hierarchy mutation contract unavailable; retaining structural checks");
        }
    }

    private boolean installNotificationRowIcons(ClassLoader loader) {
        try {
            NotificationRowIcons.Hooks access=NOTIFICATION_ROW_ICONS.resolve(loader);
            installHook(HookGroup.NOTIFICATION_ROW_ICONS,access.constructor,chain->{
                Object result=chain.proceed();NOTIFICATION_ROW_ICONS.wrapperChanged(chain.getThisObject());return result;
            });
            for(Method event:new Method[]{access.contentUpdated,access.reinflated,access.resolveHeaderViews}) {
                deoptimize(event);
                installHook(HookGroup.NOTIFICATION_ROW_ICONS,event,chain->{
                    Object result=chain.proceed();NOTIFICATION_ROW_ICONS.wrapperChanged(chain.getThisObject());return result;
                });
            }
            try {
                NotificationRowIcons.GroupHooks group=NOTIFICATION_ROW_ICONS.resolveGroup(loader);
                for(Method event:new Method[]{group.initIcon,group.recreate,group.attachBadge}) {
                    deoptimize(event);installHook(HookGroup.NOTIFICATION_ROW_ICONS,event,chain->{
                        Object result=chain.proceed();NOTIFICATION_ROW_ICONS.groupChanged(chain.getThisObject());return result;
                    });
                }
            }catch(ReflectiveOperationException|LinkageError unavailable) {
                moduleLog(Log.INFO,TAG,"Optional native stack app-icon contract unavailable",unavailable);
            }
            deoptimize(access.setEntryAdapter);
            installHook(HookGroup.NOTIFICATION_ROW_ICONS,access.setEntryAdapter,chain->{
                Object result=chain.proceed();NOTIFICATION_ROW_ICONS.rowChanged(chain.getThisObject());return result;
            });
            deoptimize(access.draw);
            installHook(HookGroup.NOTIFICATION_ROW_ICONS,access.draw,chain->NOTIFICATION_ROW_ICONS.draw(
                    (ImageView)chain.getThisObject(),(Canvas)chain.getArg(0),chain::proceed));
            Method detached=View.class.getDeclaredMethod("onDetachedFromWindow");deoptimize(detached);
            installHook(HookGroup.NOTIFICATION_ROW_ICONS,detached,chain->{
                Object result=chain.proceed();NOTIFICATION_ROW_ICONS.detached((View)chain.getThisObject());return result;
            });
            Method attached=View.class.getDeclaredMethod("onAttachedToWindow");deoptimize(attached);
            installHook(HookGroup.NOTIFICATION_ROW_ICONS,attached,chain->{
                Object result=chain.proceed();NOTIFICATION_ROW_ICONS.attached((View)chain.getThisObject());return result;
            });
            return true;
        } catch(Throwable unavailable) {
            moduleLog(Log.WARN,TAG,"Native notification header icon customization unavailable",unavailable);return false;
        }
    }

    private void installNotificationIconSpacing(ClassLoader loader,Class<?> container) {
        NOTIFICATION_ICONS.setSpacingReady(false);
        try {
            container.getDeclaredMethod("onMeasure",Integer.TYPE,Integer.TYPE);
            container.getDeclaredMethod("calculateIconXTranslations");
            Method advance=loader.loadClass("androidx.compose.material3.internal.colorUtil.Cam$$ExternalSyntheticOutline0")
                    .getDeclaredMethod("m",Float.TYPE,Float.TYPE,Float.TYPE,Float.TYPE);
            deoptimize(advance);
            installHook(HookGroup.NOTIFICATION_ICONS,advance,chain->{
                if(!NOTIFICATION_ICONS.needsSpacingAdvance())return chain.proceed();
                float nativeResult=(Float)chain.proceed();
                return NOTIFICATION_ICONS.spacingAdvance(nativeResult,(Float)chain.getArg(0),(Float)chain.getArg(1),(Float)chain.getArg(2));
            });
            Method overflow=container.getDeclaredMethod("isOverflowing",Boolean.TYPE,Float.TYPE,Float.TYPE,Float.TYPE);
            deoptimize(overflow);
            installHook(HookGroup.NOTIFICATION_ICONS,overflow,chain->{
                if(!NOTIFICATION_ICONS.needsSpacingAdvance())return chain.proceed();
                Object[] nativeArgs=chain.getArgs().toArray(),adjusted=NOTIFICATION_ICONS.adjustSpacingOverflow(nativeArgs);
                return adjusted==nativeArgs?chain.proceed():chain.proceed(adjusted);
            });
            NOTIFICATION_ICONS.setSpacingReady(true);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            NOTIFICATION_ICONS.setSpacingReady(false);
            moduleLog(Log.INFO,TAG,"Optional native notification spacing unavailable; preserving native layout",unavailable);
        }
    }

    private static String diagnosticMessage(String message) {
        if (message != null && message.startsWith("Radio changed:"))
            return "Native network state refreshed; WiFi icon " + (message.contains("wifiVisible=true") ? "visible" : "hidden")
                    + "; airplane mode " + (message.contains("airplane=true") ? "on" : "off");
        return message != null && message.startsWith("Active data label updated:")
                ? "Active data label refreshed from native network state" : message;
    }

    /** Native events drive both animation phases; there is no second gesture listener or ticker. */
    private boolean installNotificationBigClock(ClassLoader loader) {
        try {
            Class<?> controller = loader.loadClass("com.android.systemui.shade.NotificationPanelViewController");
            Class<?> qs = loader.loadClass("com.android.systemui.shade.QuickSettingsControllerImpl");
            Class<?> stack = loader.loadClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout");
            Class<?> ext = loader.loadClass("com.oplus.systemui.statusbar.notification.stack.NotificationStackScrollLayoutExtImpl");
            BigClockNativeAccess nativeAccess = new BigClockNativeAccess(controller, qs, stack, ext);
            BIG_CLOCK.setReboundReader(nativeAccess::rebound);
            CLEAR_MOTION.setReboundReader(nativeAccess::rebound);
            BIG_CLOCK.setVisibilityListener(TEXT::visibilityChanged);
            TEXT.setAdditionalClock(BIG_CLOCK::hasVisibleTime, BIG_CLOCK::usesSeconds,
                    () -> BIG_CLOCK.onNativeClockUpdated(null));
            for (Method event : controller.getDeclaredMethods()) {
                if (!event.getName().matches("setExpandedHeightInternal|onPanelStateChanged\\$1|onStateChanged|onConfigurationChanged")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    nativeAccess.panel(chain.getThisObject());
                    return result;
                });
            }
            installNotificationPanelMotionHooks(controller, nativeAccess);
            installNotificationVerticalMotionHook(loader, nativeAccess);
            installNotificationPageMotionHook(loader, nativeAccess);
            installNotificationBackdropMotionHook(loader, nativeAccess);
            for (Method event : qs.getDeclaredMethods()) {
                if (!event.getName().matches("setExpansionHeight|setExpanded|onStateChanged|updateExpansion")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    nativeAccess.panel(nativeAccess.lastController.get());
                    return result;
                });
            }
            Class<?> header = loader.loadClass("com.oplus.systemui.separate.OplusQSSimpleHeader");
            Field headerActivityStarter=header.getDeclaredField("activityStarter");
            if(!headerActivityStarter.getType().getName().equals("com.android.systemui.plugins.ActivityStarter"))
                throw new NoSuchFieldException("Native header ActivityStarter changed");
            headerActivityStarter.setAccessible(true);
            for (Method event : header.getDeclaredMethods()) {
                String name = event.getName();
                if (!name.matches("onInit|onAttachedToWindow|onDetachedFromWindow|onConfigurationChanged|updateClickAbilities")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    if(!name.equals("onDetachedFromWindow"))BIG_CLOCK.setActivityStarter(headerActivityStarter.get(view));
                    if (name.equals("onDetachedFromWindow")) BIG_CLOCK.onHeaderDetached(view);
                    else if (name.equals("onConfigurationChanged")) BIG_CLOCK.onConfigurationChanged();
                    else if (name.equals("updateClickAbilities")) BIG_CLOCK.onHeaderButtonVisibilityWritten(view);
                    else BIG_CLOCK.onHeaderInflated(view);
                    return result;
                });
            }
            Method scroll = stack.getDeclaredMethod("setOwnScrollY", Integer.TYPE, Boolean.TYPE);
            deoptimize(scroll);
            installHook(HookGroup.BIG_CLOCK, scroll, chain -> {
                Object result = chain.proceed();
                nativeAccess.scroll((View) chain.getThisObject());
                return result;
            });
            for (Method event : ext.getDeclaredMethods()) {
                if (!event.getName().matches("updateOverDistance|resetOverDistance")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    nativeAccess.scroll((View) nativeAccess.extView.invoke(chain.getThisObject()));
                    return result;
                });
            }
            Method intrinsic = stack.getDeclaredMethod("setIntrinsicPadding", Integer.TYPE);
            Method padding = stack.getDeclaredMethod("updateTopPadding", Float.TYPE, Boolean.TYPE);
            deoptimize(intrinsic); deoptimize(padding);
            installHook(HookGroup.BIG_CLOCK, intrinsic, chain -> chain.proceed(new Object[]{
                    BIG_CLOCK.adjustIntrinsicPadding((View) chain.getThisObject(), (Integer) chain.getArg(0))}));
            installHook(HookGroup.BIG_CLOCK, padding, chain -> chain.proceed(new Object[]{
                    BIG_CLOCK.adjustTopPadding((View) chain.getThisObject(), (Float) chain.getArg(0)), chain.getArg(1)}));
            Method range = stack.getDeclaredMethod("getScrollRange"); deoptimize(range);
            installHook(HookGroup.BIG_CLOCK, range, chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                int nativeRange = (Integer) result;
                int clockRange = bigClockEnabled ? BIG_CLOCK.adjustScrollRange(view, nativeRange) : nativeRange;
                return CLEAR_MOTION.adjustScrollRange(view, nativeRange, clockRange);
            });
            for (String drawName : new String[]{"dispatchDraw", "drawDispatchedChildren"}) {
                Method draw = stack.getDeclaredMethod(drawName, Canvas.class); deoptimize(draw);
                installHook(HookGroup.BIG_CLOCK, draw, chain -> {
                    if (!bigClockEnabled) return chain.proceed();
                    Canvas canvas = (Canvas) chain.getArg(0);
                    View view = (View) chain.getThisObject();
                    return BIG_CLOCK.drawNotifications(view, canvas, nativeAccess.horizontalSwitch(view),
                            target -> chain.proceed(new Object[]{target}));
                });
            }
            Method configuration = stack.getDeclaredMethod("onConfigurationChanged", android.content.res.Configuration.class);
            installHook(HookGroup.BIG_CLOCK, configuration, chain -> {
                Object result = chain.proceed();
                nativeAccess.scroll((View) chain.getThisObject());
                BIG_CLOCK.onConfigurationChanged();
                NATIVE_STACK.configurationChanged();
                return result;
            });
            Class<?> fake = loader.loadClass("com.oplus.systemui.separate.OplusSimpleQSFakeController");
            Class<?> listener = loader.loadClass("com.oplus.systemui.separate.OplusSimpleQSFakeController$fractionChangeListener$1");
            Field owner = accessibleField(listener, "this$0");
            Field fakeClock = optionalField(fake, "fakeClockViewContainer");
            Field fakeCarrier = optionalField(fake, "fakeCarrierContainer");
            Field fakeNotification = optionalField(fake, "fakeNotificationContainer");
            Field fakeStatusIcons = optionalField(fake, "fakeStatusBarIconsContainer");
            Field fakeHeader = optionalField(fake, "oplusQSSimpleHeader");
            try { phoneAnchorReader = new NativePhoneAnchorReader(loader); }
            catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional native fixed-status position anchor unavailable", unavailable);
            }
            installStatusIconCopyDrawHooks(loader);
            installSeparateQsStatusIconHooks(loader);
            installNotificationHeaderStyleHook(fake);
            Method fraction = listener.getDeclaredMethod("onFractionChanged", Float.TYPE); deoptimize(fraction);
            installHook(HookGroup.BIG_CLOCK, fraction, chain -> {
                Object result = chain.proceed();
                // Right-side icon ownership covers QS and notifications even when the big
                // notification clock is disabled. Only the replaced left labels depend on it.
                float displayedFraction = (Float) chain.getArg(0);
                Object fakeController = owner.get(chain.getThisObject());
                nativeAccess.panel(nativeAccess.lastController.get(), displayedFraction);
                // Source bindings remain independent of the large clock master; the exact
                // opacity owners themselves decide whether any shade feature is enabled.
                if (fakeClock != null)
                    BIG_CLOCK.onFakeClockChanged((View) fakeClock.get(fakeController), displayedFraction,
                            nativePhoneLeftAnchor((View) fakeClock.get(fakeController), true));
                if (fakeNotification != null)
                    BIG_CLOCK.onFakeNotificationChanged((View) fakeNotification.get(fakeController),
                            nativePhoneLeftAnchor((View) fakeNotification.get(fakeController), false), displayedFraction);
                if (bigClockEnabled) {
                    if (fakeCarrier != null)
                        BIG_CLOCK.onFakeCarrierChanged((View) fakeCarrier.get(fakeController), displayedFraction);
                }
                if (fakeStatusIcons != null && fakeHeader != null)
                    BIG_CLOCK.onFakeStatusChanged((View) fakeStatusIcons.get(fakeController), (View) fakeHeader.get(fakeController),
                            nativePhoneAnchor((View) fakeStatusIcons.get(fakeController)));
                return result;
            });
            Method fractionEnd = optionalMethod(listener, "onFractionEnd", Float.TYPE);
            if (fractionEnd != null) try {
                deoptimize(fractionEnd);
                installHook(HookGroup.BIG_CLOCK, fractionEnd, chain -> {
                    Object result = chain.proceed();
                    nativeAccess.panel(nativeAccess.lastController.get());
                    return result;
                });
            } catch (Throwable error) {
                moduleLog(Log.WARN, TAG, "Notification clock fraction completion unavailable", error);
            }
            for (String name : new String[]{"com.android.systemui.statusbar.policy.Clock",
                    "com.oplus.systemui.statusbar.widget.StatClock", "com.oplus.systemui.qs.widget.OplusQSClock",
                    "com.oplus.systemui.qs.widget.SimpleQsClock"}) {
                Class<?> clock = loader.loadClass(name);
                for (Method event : clock.getDeclaredMethods()) {
                    if (!event.getName().equals("updateClock")) continue;
                    deoptimize(event);
                    installHook(HookGroup.BIG_CLOCK, event, chain -> {
                        Object result = chain.proceed();
                        if (bigClockEnabled) BIG_CLOCK.onNativeClockUpdated((View) chain.getThisObject());
                        return result;
                    });
                }
            }
            installNotificationStackHooks(loader, stack);
            installNotificationCardTouchHook(loader, stack);
            installClockBackdropHooks(loader);
            // Weather must not depend on enabling unrelated status-clock or
            // carrier text controls. These are inflation/attach events only.
            for(Method event:new Method[]{View.class.getDeclaredMethod("onAttachedToWindow"),View.class.getDeclaredMethod("setId",Integer.TYPE)}) {
                deoptimize(event);installHook(HookGroup.BIG_CLOCK,event,chain->{Object result=chain.proceed();
                    observeLockscreenPlugin((View)chain.getThisObject());return result;});
            }
            moduleLog(Log.INFO, TAG, "Portrait notification big clock native events installed");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Notification big clock hooks incomplete; retry at next package phase", error);
            return false;
        }
    }

    /** Observe the existing behind-scrim blur from construction, before any
     * asynchronous image can contain server-side mixed colour. No blur is
     * registered, submitted or reconfigured by these observers. */
    private void installClockBackdropHooks(ClassLoader loader) {
        try {
            Class<?> helper=loader.loadClass("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurHelper");
            Class<?> wrapper=loader.loadClass("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable");
            Class<?> engine=loader.loadClass("com.oplus.posteffect.BlurDrawable");
            Class<?> base=loader.loadClass("com.oplus.posteffect.drawable.BaseDrawable");
            Class<?> continuous=loader.loadClass("com.oplus.posteffect.drawable.ContinuousBlurDrawable");
            Class<?> param=loader.loadClass("com.oplus.posteffect.BlurParam");
            Class<?> foreground=loader.loadClass("com.oplus.posteffect.ForegroundBlurParam");
            Class<?> image=loader.loadClass("com.oplus.posteffect.drawable.BaseDrawable$ImageInfo");
            Method ensure=requiredMethod(helper,"ensureBlurDrawable");
            java.lang.reflect.Constructor<?> construct=wrapper.getDeclaredConstructor(engine,loader.loadClass("kotlin.jvm.functions.Function0"));
            Method add=requiredMethod(continuous,"addBlurDrawable");
            Method params=requiredMethod(continuous,"setBlurParamsInternal",param,foreground,Boolean.TYPE);
            Method drawing=requiredMethod(base,"setDrawingParam",param);
            Method shader=requiredMethod(base,"setEnableBlurShader",Boolean.TYPE);
            Method rawShader=requiredMethod(base,"setEnableShader",Boolean.TYPE);
            Method arrived=requiredMethod(base,"setDrawingImageInfo",image);
            Method encoded=requiredMethod(param,"toFloatArray",Boolean.TYPE);
            // The 20-instruction override returns after image/matrix promotion;
            // leave the large shared Base implementation compiled normally.
            Method drawn=requiredMethod(continuous,"preDrawContent"),released=requiredMethod(base,"releaseDrawingBitmap");
            // The native RT draw callback enters through this tiny accessor.
            // Keep its virtual call visible to the observed override.
            deoptimize(requiredMethod(base,"access$drawContent",base));
            // These native call sites encode the real request; observing only
            // toFloatArray can miss an AOT-inlined copy of that encoder.
            deoptimize(requiredMethod(continuous,"syncBlurParams",Boolean.TYPE,Boolean.TYPE));
            deoptimize(requiredMethod(loader.loadClass("com.oplus.posteffect.manager.BlurDrawableManager"),"addBlurDrawable",
                    android.view.SurfaceControl.class,Integer.TYPE,param,Boolean.TYPE,
                    loader.loadClass("com.oplus.posteffect.manager.BlurStateListener"),Boolean.TYPE));
            for(Method method:new Method[]{ensure,add,params,drawing,shader,rawShader,arrived,encoded,drawn,released})deoptimize(method);
            installHook(HookGroup.BIG_CLOCK,ensure,chain->{
                try(ClockBackdropSource.Scope scope=ClockBackdropSource.beginHelper(chain.getThisObject())){return chain.proceed();}
            });
            installHook(HookGroup.BIG_CLOCK,construct,chain->{Object result=chain.proceed();ClockBackdropSource.created(chain.getThisObject());return result;});
            installHook(HookGroup.BIG_CLOCK,add,chain->{
                ClockBackdropSource.onRegistration(chain.getThisObject());
                Object result=chain.proceed();ClockBackdropSource.onRegistration(chain.getThisObject());return result;
            });
            for(Method method:new Method[]{params,drawing})installHook(HookGroup.BIG_CLOCK,method,chain->{
                ClockBackdropSource.onParams(chain.getThisObject(),chain.getArg(0));return chain.proceed();
            });
            for(Method method:new Method[]{shader,rawShader})installHook(HookGroup.BIG_CLOCK,method,chain->{
                ClockBackdropSource.onShaderChanged(chain.getThisObject(),(Boolean)chain.getArg(0));return chain.proceed();
            });
            installHook(HookGroup.BIG_CLOCK,arrived,chain->{Object result=chain.proceed();ClockBackdropSource.onImageChanged(chain.getThisObject());return result;});
            installHook(HookGroup.BIG_CLOCK,encoded,chain->{Object result=chain.proceed();
                if(result instanceof float[])ClockBackdropSource.onEncoded(chain.getThisObject(),(float[])result);return result;});
            installHook(HookGroup.BIG_CLOCK,drawn,chain->{
                try(ClockBackdropSource.DrawScope scope=ClockBackdropSource.beginDraw(chain.getThisObject())) {
                    Object result=chain.proceed();scope.complete();return result;
                }
            });
            installHook(HookGroup.BIG_CLOCK,released,chain->{Object result=chain.proceed();ClockBackdropSource.onImageChanged(chain.getThisObject());return result;});
        } catch(Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Existing clock backdrop source unavailable; retaining readable colour glyphs",unavailable);
        }
    }

    /** Separate QS owns a different native spring and header from the notification panel. */
    private void installSeparateQsStatusIconHooks(ClassLoader loader) {
        installInnerTileStatusIconHooks(loader);
        try {
            StatusBarQsIconAccess access = new StatusBarQsIconAccess(loader, BIG_CLOCK, this::nativePhoneAnchor);
            for (Method event : access.listenerType.getDeclaredMethods()) {
                if (!event.getName().matches("onFractionChanged|onFractionEnd")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    access.listenerChanged(chain.getThisObject());
                    return result;
                });
            }
            for (Method event : access.componentType().getDeclaredMethods()) {
                String name = event.getName();
                if (!name.matches("onViewAttached|onViewDetached|onDestroy|onTrackingStarted\\$1|onTrackingStopped\\$1"
                        + "|expandQSPanelWithoutAnimate|collapseQSPanelWithoutAnimate|updateStatusBarTopMargin")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    if (name.equals("onViewDetached") || name.equals("onDestroy")) access.detached(chain.getThisObject());
                    else access.changed(chain.getThisObject());
                    return result;
                });
            }
            for (Method event : access.fakeType.getDeclaredMethods()) {
                if (!event.getName().matches("setStatusBarAlpha|updateElementLoc\\$1|onSystemIconScaleChanged|resetState|updateLeftAreaState")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    access.fakeChanged(chain.getThisObject());
                    return result;
                });
            }
            moduleLog(Log.INFO, TAG, "Separate QS icon row connected to its native display spring");
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional separate QS icon display spring unavailable", unavailable);
        }
    }

    /** The control-center tile pager is separate from notification/QS horizontal switching. */
    private void installInnerTileStatusIconHooks(ClassLoader loader) {
        try {
            StatusBarTileIconMotion observer = new StatusBarTileIconMotion(loader);
            BIG_CLOCK.setStatusIconTileReader(observer);
            for (Method caller : observer.managerType.getDeclaredMethods())
                if (caller.getName().matches("scrollHorizontallyBy|scrollHorizontallyInternal")) deoptimize(caller);
            for (Method event : new Method[]{observer.managerType.getDeclaredMethod("onPageScrolled"),
                    observer.managerType.getDeclaredMethod("onScrollStateChanged", Integer.TYPE)}) {
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    observer.changed(chain.getThisObject());
                    BIG_CLOCK.onStatusIconHorizontalProgressChanged();
                    return result;
                });
            }
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional inner QS icon paging unavailable; outer transition retained", unavailable);
        }
    }

    /** A final fraction callback can precede the native spring's end notification. */
    private void installNotificationPanelMotionHooks(Class<?> controller, BigClockNativeAccess nativeAccess) {
        for (Method event : controller.getDeclaredMethods()) {
            if (!event.getName().matches("onFlingEnd|notifyExpandingFinished|instantCollapse|onTrackingStarted\\$2|onTrackingStopped")) continue;
            try {
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    nativeAccess.panel(chain.getThisObject());
                    return result;
                });
            } catch (Throwable error) {
                moduleLog(Log.WARN, TAG, "Notification clock motion completion unavailable: " + event.getName(), error);
            }
        }
    }

    /** Register the added child with the same native gesture session and release springs. */
    private void installNotificationVerticalMotionHook(ClassLoader loader, BigClockNativeAccess nativeAccess) {
        try {
            Class<?> value = loader.loadClass("com.oplus.systemui.panelanimation.OplusPanelAnimationExImpl$PanelTranslationYValue");
            Method setter = value.getDeclaredMethod("setTranslationYValue", Float.TYPE);
            deoptimize(setter);
            installHook(HookGroup.BIG_CLOCK, setter, chain -> {
                Object result = chain.proceed();
                nativeAccess.verticalChanged(chain.getThisObject());
                return result;
            });
            moduleLog(Log.INFO, TAG, "Notification clock native vertical spring connected");
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional notification vertical spring unavailable", unavailable);
        }
    }

    /** Register the added child with the same native gesture session and release springs. */
    private void installNotificationPageMotionHook(ClassLoader loader, BigClockNativeAccess nativeAccess) {
        try {
            Class<?> pager = loader.loadClass("com.oplus.systemui.separate.OplusPanelViewPagerController");
            nativeAccess.resolvePageMotion(pager);
            StatusBarIconPageMotion resolvedIcons = null;
            try {
                resolvedIcons = new StatusBarIconPageMotion(loader, pager);
                StatusBarIconPageMotion observer = resolvedIcons;
                BIG_CLOCK.setStatusIconHorizontalReader(observer);
                deoptimize(observer.animatedValue);
                installHook(HookGroup.BIG_CLOCK, observer.animatedValue, chain -> {
                    Object result = chain.proceed();
                    if (observer.owns(chain.getThisObject())) BIG_CLOCK.onStatusIconHorizontalProgressChanged();
                    return result;
                });
                for (Method event : pager.getDeclaredMethods()) {
                    if (!event.getName().matches("onInit|resetHorizontalTranslationView|syncHorizontalTranslationDown")) continue;
                    deoptimize(event);
                    installHook(HookGroup.BIG_CLOCK, event, chain -> {
                        Object result = chain.proceed();
                        observer.bind(chain.getThisObject());
                        BIG_CLOCK.onStatusIconHorizontalProgressChanged();
                        return result;
                    });
                }
                moduleLog(Log.INFO, TAG, "Shade icon row connected to native horizontal progressive spring");
            } catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional shade icon horizontal progress unavailable; vertical motion retained", unavailable);
            }
            final StatusBarIconPageMotion iconMotion = resolvedIcons;
            Method ready = optionalMethod(pager, "ensureProgressiveHelperReady");
            if (ready == null) throw new NoSuchMethodException("ensureProgressiveHelperReady");
            deoptimize(ready);
            installHook(HookGroup.BIG_CLOCK, ready, chain -> {
                Object result = chain.proceed();
                nativeAccess.pageReady(chain.getThisObject());
                if (iconMotion != null) iconMotion.bind(chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Notification clock native page motion unavailable", error);
        }
    }

    /** Native fake frames explicitly source.draw(), bypassing the source View's alpha. */
    private void installStatusIconCopyDrawHooks(ClassLoader loader) {
        NativePhoneAnchorReader reader = phoneAnchorReader;
        try {
            Method visibility = View.class.getDeclaredMethod("setVisibility", Integer.TYPE);
            deoptimize(visibility);
            installHook(HookGroup.BIG_CLOCK, visibility, chain -> {
                int original = (Integer) chain.getArg(0);
                int result = BIG_CLOCK.nativeNotificationHeaderVisibility((View) chain.getThisObject(), original);
                Object value = result == original ? chain.proceed() : chain.proceed(new Object[]{result});
                StatusBarOverlayVisibility.changed((View) chain.getThisObject());
                return value;
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional exact native header clock visibility observer unavailable", unavailable);
        }
        try {
            Method alpha = View.class.getDeclaredMethod("setAlpha", Float.TYPE);
            deoptimize(alpha);
            installHook(HookGroup.BIG_CLOCK, alpha, chain -> {
                float original = (Float) chain.getArg(0);
                float result = BIG_CLOCK.statusIconPhoneRightAlpha((View) chain.getThisObject(), original);
                Object value = result == original ? chain.proceed() : chain.proceed(new Object[]{result});
                StatusBarOverlayVisibility.changed((View) chain.getThisObject());
                return value;
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional exact Phone right opacity observer unavailable", unavailable);
        }
        if (reader == null) return;
        // Preview-only, bounded structural ownership trace; never reads notification text.
        BIG_CLOCK.setStatusIconTraceListener(message -> {
            if (ModuleDiagnostics.enabled()) Log.i("C17IconIdentity", message);
        });
        BIG_CLOCK.setStatusIconCopyInspector(reader);
        int copiesInstalled = 0;
        try {
            Class<?> base = loader.loadClass("com.oplus.systemui.seamless.widget.StatusBarFakeFrame");
            for (Class<?> type : new Class<?>[]{base, reader.systemCopy, reader.notificationCopy}) {
                if (type == null) continue;
                Method draw = type.getDeclaredMethod("dispatchDraw", Canvas.class);
                deoptimize(draw);
                installHook(HookGroup.BIG_CLOCK, draw, chain -> {
                    View copy = (View) chain.getThisObject();
                    int kind = reader.copyKind(copy);
                    // Only the notification scene's already bound fake subtree may hide RIGHT.
                    // The separate control center and every unknown source stay entirely native.
                    if (kind < 0 || kind == StatusIconTransition.RIGHT && !BIG_CLOCK.ownsNotificationRightCopy(copy))
                        return chain.proceed();
                    View original;
                    try { original = reader.copiedSource(copy); }
                    catch (Throwable unavailable) { return chain.proceed(); }
                    BIG_CLOCK.onStatusIconCopyBound(copy, original, kind);
                    boolean suppressed = BIG_CLOCK.suppressStatusIconCopy(copy, original, kind);
                    BIG_CLOCK.onStatusIconCopyDrawDecision(copy, original, kind, suppressed);
                    if (suppressed) { LEFT_COPY_SPACING.forget(copy); return null; }
                    if (kind != StatusIconTransition.RIGHT)
                        LEFT_COPY_SPACING.observe(copy, original, kind, reader.drawOffsetX(copy, kind));
                    observeLeftCopySibling(reader, copy, kind);
                    try (StatusBarLeftCopySpacing.DrawScope scope = LEFT_COPY_SPACING.beginDraw(copy, (Canvas) chain.getArg(0))) {
                        return chain.proceed();
                    }
                });
                copiesInstalled++;
            }
            for (Method event : base.getDeclaredMethods()) {
                if (!event.getName().matches("setMView|setFakeView|bindRealView|unbindRealView|clearFakeView")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    View copy = (View) chain.getThisObject();
                    int kind = reader.copyKind(copy);
                    if (kind >= 0) try {
                        View original = reader.copiedSource(copy);
                        BIG_CLOCK.onStatusIconCopyBound(copy, original, kind);
                        if (BIG_CLOCK.suppressStatusIconCopy(copy, original, kind)) {
                            LEFT_COPY_SPACING.forget(copy); return result;
                        }
                        if (kind != StatusIconTransition.RIGHT)
                            LEFT_COPY_SPACING.observe(copy, original, kind, reader.drawOffsetX(copy, kind));
                        observeLeftCopySibling(reader, copy, kind);
                    } catch (Throwable ignored) { }
                    return result;
                });
            }
            moduleLog(Log.INFO, TAG, "Exact native Phone icon/clock/notification copy draw ownership installed");
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native status icon copy drawing hook unavailable", unavailable);
            if (ModuleDiagnostics.enabled()) Log.w("C17IconIdentity", "copy dispatch hooks installed=" + copiesInstalled, unavailable);
        }
        if (ModuleDiagnostics.enabled()) Log.i("C17IconIdentity", "exact copy node ownership installed; dispatch hooks=" + copiesInstalled);
    }

    /** Bind the exact clock before a notification-first native traversal records its first display list. */
    private static void observeLeftCopySibling(NativePhoneAnchorReader reader, View copy, int kind) {
        if (kind != StatusIconTransition.NOTIFICATIONS || LEFT_COPY_SPACING.paired(copy)
                || !(copy.getParent() instanceof View)) return;
        View container = (View) copy.getParent();
        if (!(container.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) container.getParent();
        for (int i = 0; i < row.getChildCount(); i++) {
            View sibling = row.getChildAt(i);
            if (!(sibling instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) sibling;
            for (int j = 0; j < group.getChildCount(); j++) {
                View clock = group.getChildAt(j);
                if (reader.copyKind(clock) != StatusIconTransition.CLOCK) continue;
                try {
                    View source = reader.copiedSource(clock);
                    BIG_CLOCK.onStatusIconCopyBound(clock, source, StatusIconTransition.CLOCK);
                    if (BIG_CLOCK.suppressStatusIconCopy(clock, source, StatusIconTransition.CLOCK))
                        LEFT_COPY_SPACING.forget(clock);
                    else LEFT_COPY_SPACING.observe(clock, source, StatusIconTransition.CLOCK, 0f);
                } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Keep the native fallback. */ }
            }
        }
    }

    /** Exclude actual notification pixels from native page-start hot zones after our layout shifts. */
    private void installNotificationCardTouchHook(ClassLoader loader, Class<?> stack) {
        try {
            Class<?> pager = loader.loadClass("com.oplus.systemui.separate.OplusPanelViewPagerController");
            Class<?> row = loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            // mView belongs to ViewController, not to the pager's declared fields.
            Field view = optionalField(pager, "mView");
            Field detector = accessibleField(pager, "detector");
            Field targetPage = accessibleField(pager, "targetPage");
            Field animation = accessibleField(pager, "slidSwitchAnimation");
            Method busy = optionalMethod(detector.getType(), "isDraggingOrSettlingOrBlocking");
            Method translation = optionalMethod(animation.getType(), "getHorizontalTranslationValue");
            Method running = translation == null ? null : optionalMethod(translation.getReturnType(), "isLogicRunning");
            Method hotZone = optionalMethod(pager, "isInScrollXHotZone", PointF.class);
            if (view == null || busy == null || translation == null || running == null || hotZone == null)
                throw new NoSuchMethodException("Native notification page-start hot-zone contract");
            NotificationCardTouchBoundary boundary = new NotificationCardTouchBoundary(stack, row);
            boolean[] warned = {false};
            deoptimize(hotZone);
            installHook(HookGroup.BIG_CLOCK, hotZone, chain -> {
                Object result = chain.proceed();
                if (!bigClockEnabled || !Boolean.TRUE.equals(result)) return result;
                try {
                    Object controller = chain.getThisObject();
                    Object page = targetPage.get(controller);
                    Object swipe = detector.get(controller);
                    Object nativeAnimation = animation.get(controller);
                    Object horizontal = nativeAnimation == null ? null : translation.invoke(nativeAnimation);
                    // Never change an already owned native drag, settle, blocking or rubberband
                    // session. The native detector caches this exclusion only for its new DOWN.
                    if (!(page instanceof Enum) || !"NOTIFICATION".equals(((Enum<?>) page).name())
                            || swipe == null || Boolean.TRUE.equals(busy.invoke(swipe))
                            || horizontal == null || Boolean.TRUE.equals(running.invoke(horizontal))) return result;
                    Object nativeView = view.get(controller);
                    PointF point = (PointF) chain.getArg(0);
                    if (nativeView instanceof View && point != null
                            && BIG_CLOCK.notificationTouch((View) nativeView, point.x, point.y, boundary)) return false;
                } catch (Throwable unavailable) {
                    if (!warned[0]) {
                        warned[0] = true;
                        ModuleDiagnostics.error("bigclock", "Notification touch boundary unavailable; native routing retained", unavailable);
                    }
                }
                return result;
            });
            moduleLog(Log.INFO, TAG, "Notification card page-start exclusion installed");
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional notification card touch boundary unavailable", unavailable);
        }
    }

    /** Header reinitialization can reset its native transition outside the fraction listener. */
    private void installNotificationHeaderStyleHook(Class<?> fakeController) {
        try {
            Field header = optionalField(fakeController, "oplusQSSimpleHeader");
            Field clock = optionalField(fakeController, "fakeClockViewContainer");
            Field notifications = optionalField(fakeController, "fakeNotificationContainer");
            Field statusIcons = optionalField(fakeController, "fakeStatusBarIconsContainer");
            Method reset = optionalMethod(fakeController, "resetAllViewsTransitionAlpha", Float.TYPE);
            if (header == null || reset == null)
                throw new NoSuchMethodException("Native simple header transition reset unavailable");
            deoptimize(reset);
            installHook(HookGroup.BIG_CLOCK, reset, chain -> {
                Object result = chain.proceed();
                Object view = header.get(chain.getThisObject());
                if (view instanceof View) BIG_CLOCK.onHeaderNativeStyleWritten((View) view);
                if (clock != null)
                    BIG_CLOCK.onFakeClockChanged((View) clock.get(chain.getThisObject()), (Float) chain.getArg(0),
                            nativePhoneLeftAnchor((View) clock.get(chain.getThisObject()), true));
                if (notifications != null)
                    BIG_CLOCK.onFakeNotificationChanged((View) notifications.get(chain.getThisObject()),
                            nativePhoneLeftAnchor((View) notifications.get(chain.getThisObject()), false), (Float) chain.getArg(0));
                if (statusIcons != null && view instanceof View)
                    BIG_CLOCK.onFakeStatusChanged((View) statusIcons.get(chain.getThisObject()), (View) view,
                            nativePhoneAnchor((View) statusIcons.get(chain.getThisObject())));
                return result;
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification header transition hook unavailable", unavailable);
        }
    }

    private View nativePhoneAnchor(View copy) {
        NativePhoneAnchorReader reader = phoneAnchorReader;
        if (reader == null) return null;
        try { return reader.source(copy, 0); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private View nativePhoneLeftAnchor(View copy, boolean clock) {
        NativePhoneAnchorReader reader = phoneAnchorReader;
        if (reader == null) return null;
        try { return reader.leftSource(copy, clock, 0); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    /** Read the current source binding, even while its separate Phone window is visually hidden. */
    private static final class NativePhoneAnchorReader implements StatusBarFixedIcons.NativeCopyInspector {
        final Class<?> systemCopy;
        final Class<?> clockCopy, notificationCopy;
        final Method source, notificationOffset;
        NativePhoneAnchorReader(ClassLoader loader) throws ReflectiveOperationException {
            systemCopy = loader.loadClass("com.oplus.systemui.seamless.widget.SystemIconFakeFrame");
            clockCopy = optionalCopyClass(loader, "com.oplus.systemui.seamless.widget.ClockFakeFrame");
            notificationCopy = optionalCopyClass(loader, "com.oplus.systemui.seamless.widget.NotificationFakeFrame");
            source = loader.loadClass("com.oplus.systemui.seamless.widget.StatusBarFakeFrame").getDeclaredMethod("getMView");
            source.setAccessible(true);
            notificationOffset = notificationCopy == null ? null : optionalMethod(notificationCopy, "getOffsetX");
        }
        private static Class<?> optionalCopyClass(ClassLoader loader, String name) {
            try { return loader.loadClass(name); }
            catch (ClassNotFoundException | LinkageError unavailable) { return null; }
        }
        int copyKind(View copy) {
            if (systemCopy.isInstance(copy)) return StatusIconTransition.RIGHT;
            if (clockCopy != null && clockCopy.isInstance(copy)) return StatusIconTransition.CLOCK;
            if (notificationCopy != null && notificationCopy.isInstance(copy)) return StatusIconTransition.NOTIFICATIONS;
            return -1;
        }
        public int kind(View copy) { return copyKind(copy); }
        float drawOffsetX(View copy, int kind) {
            if (kind == StatusIconTransition.CLOCK) return 0f;
            if (kind != StatusIconTransition.NOTIFICATIONS || notificationOffset == null) return Float.NaN;
            try { return ((Number) notificationOffset.invoke(copy)).floatValue(); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return Float.NaN; }
        }
        public View copiedSource(View copy) throws ReflectiveOperationException {
            Object value = source.invoke(copy);
            return value instanceof View ? (View) value : null;
        }
        View leftSource(View copy, boolean clock, int depth) throws ReflectiveOperationException {
            if (copy == null || depth > 6) return null;
            Class<?> kind = clock ? clockCopy : notificationCopy;
            if (kind == null) return null;
            if (kind.isInstance(copy)) {
                Object value = source.invoke(copy);
                return value instanceof View ? (View) value : null;
            }
            if (copy instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) copy;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View value = leftSource(group.getChildAt(i), clock, depth + 1);
                    if (value != null) return value;
                }
            }
            return null;
        }
        View source(View copy, int depth) throws ReflectiveOperationException {
            if (copy == null || depth > 6) return null;
            if (systemCopy.isInstance(copy)) {
                Object value = source.invoke(copy);
                if (value instanceof View) {
                    View view = (View) value;
                    int id = view.getId();
                    if (id > 0 && "status_bar_end_side_container_for_fake".equals(view.getResources().getResourceEntryName(id)))
                        return view;
                }
                return null;
            }
            if (copy instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) copy;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View value = source(group.getChildAt(i), depth + 1);
                    if (value != null) return value;
                }
            }
            return null;
        }
    }

    /** Optional notification projection hooks must never disable the clock on a different OS build. */
    private void installNotificationStackHooks(ClassLoader loader, Class<?> stack) {
        BIG_CLOCK.setClearMotion(CLEAR_MOTION);
        installNotificationStackTapHook(loader, stack);
        installNotificationGroupHooks(loader);
        installNativeNotificationRuler(loader);
        try {
            Method measure = stack.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE);
            Method layout = stack.getDeclaredMethod("onLayout", Boolean.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            deoptimize(measure); deoptimize(layout);
            installHook(HookGroup.BIG_CLOCK, measure, chain -> chain.proceed(new Object[]{
                    BIG_CLOCK.notificationWidthSpec((View) chain.getThisObject(), (Integer) chain.getArg(0)), chain.getArg(1)}));
            installHook(HookGroup.BIG_CLOCK, layout, chain -> {
                Object result = chain.proceed();
                View host = (View) chain.getThisObject();
                BIG_CLOCK.onNotificationLayout(host); CLEAR_MOTION.onScrollChanged(host);
                return result;
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native landscape notification layout hook unavailable", unavailable);
        }
        installNotificationFooterMarginHook(stack);
        // Native rulers own target geometry. These AFTER callbacks only refresh our clock/footer.
        for (Method event : stack.getDeclaredMethods()) {
            if (event.getParameterCount() != 0 || !event.getName().matches(
                    "applyCurrentState(?:\\$[0-9]+)?|startAnimationToState(?:\\$[0-9]+)?|updateChildren(?:\\$[0-9]+)?")) continue;
            deoptimize(event);
            installHook(HookGroup.BIG_CLOCK, event, chain -> {
                View host=(View)chain.getThisObject();Object result=chain.proceed();
                BIG_CLOCK.onStackLayoutUpdated(host); CLEAR_MOTION.onScrollChanged(host);
                return result;
            });
        }
        Method detached = optionalMethod(stack, "onDetachedFromWindow");
        if (detached != null) {
            deoptimize(detached);
            installHook(HookGroup.BIG_CLOCK, detached, chain -> {
                Object result = chain.proceed(); BIG_CLOCK.onStackDetached((View) chain.getThisObject()); return result;
            });
        }
    }

    /** Native shade calculation is the sole owner of notification stack scale and clipping. */
    private void installNativeNotificationRuler(ClassLoader loader) {
        if (!NATIVE_STACK.resolve(loader)) {
            ModuleDiagnostics.info("native-stack", "Verified shade ruler unavailable; native geometry retained");
            return;
        }
        try {
            Method update=NATIVE_STACK.updateMethod(), current=NATIVE_STACK.currentMethod(), apply=NATIVE_STACK.applyMethod();
            deoptimize(update); deoptimize(current); deoptimize(apply);
            // The real ruler calls both tiny hooked getters; undo their cached inline bodies.
            Method rulerUpdate=loader.loadClass(NotificationNativeStack.SHADE_RULER).getDeclaredMethod(
                    "updateStackedState",java.util.List.class,loader.loadClass(NotificationNativeStack.AMBIENT),Float.TYPE);
            deoptimize(rulerUpdate);
            for(Method bridge:loader.loadClass(NotificationNativeStack.SHADE_RULER).getDeclaredMethods())
                if(bridge.getName().equals("access$onConfigChanged"))deoptimize(bridge);
            installHook(HookGroup.BIG_CLOCK,update,chain -> NATIVE_STACK.withNativeUpdate(
                    chain.getThisObject(),chain.getArg(1),()->chain.proceed()));
            installHook(HookGroup.BIG_CLOCK,current,chain -> NATIVE_STACK.currentStacked(
                    chain.getThisObject(),(Boolean)chain.proceed()));
            installHook(HookGroup.BIG_CLOCK,apply,chain -> chain.proceed(new Object[]{chain.getArg(0),
                    NATIVE_STACK.bottomBorder(chain.getThisObject(),(java.util.List<?>)chain.getArg(0),(Float)chain.getArg(1))}));
            try {
                Method groupStackState=loader.loadClass("com.oplus.systemui.statusbar.notification.stack.NotificationChildrenContainerExtImp").getDeclaredMethod("isStackState");
                deoptimize(groupStackState);
                deoptimize(loader.loadClass("com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer").getDeclaredMethod("updateChildrenClipping"));
                for(Method method:loader.loadClass(NotificationNativeStack.EXTENSION).getDeclaredMethods())
                    if(method.getName().equals("isStackedNotification$1"))deoptimize(method);
            }catch(ReflectiveOperationException absent){/* Optional native callers differ across ROMs. */}
            NATIVE_STACK.hooksReady(true);
        } catch (Throwable unavailable) {
            NATIVE_STACK.hooksReady(false);
            NATIVE_STACK.configure(null);
            ModuleDiagnostics.error("native-stack", "Native ruler hooks unavailable; original geometry retained", unavailable);
        }
    }

    /** Native per-app groups own their layout and animations, independently of either clock. */
    private void installNotificationGroupHooks(ClassLoader loader) {
        try {
            Class<?> children = loader.loadClass("com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer");
            Method measure=children.getDeclaredMethod("onMeasure",Integer.TYPE,Integer.TYPE);deoptimize(measure);
            installHook(HookGroup.BIG_CLOCK,measure,chain->{
                BIG_CLOCK.prepareGroupMeasure((View)chain.getThisObject());return chain.proceed();
            });
            for (Method event : children.getDeclaredMethods()) {
                if (event.getName().matches("updateState|applyState|startAnimationToState")) {
                    deoptimize(event);
                    installHook(HookGroup.BIG_CLOCK, event, chain -> {
                        BIG_CLOCK.prepareGroup((View) chain.getThisObject(), event.getName().equals("updateState"));
                        return chain.proceed();
                    });
                } else if (event.getName().equals("getMaxAllowedVisibleChildren") && event.getReturnType() == Integer.TYPE) {
                    deoptimize(event);
                    installHook(HookGroup.BIG_CLOCK, event, chain -> BIG_CLOCK.nativeGroupVisibleCount(
                            (View) chain.getThisObject(), (Integer) chain.proceed()));
                }
            }
            Method detached = View.class.getDeclaredMethod("onDetachedFromWindow");
            installHook(HookGroup.BIG_CLOCK, detached, chain -> {
                if (children.isInstance(chain.getThisObject())) BIG_CLOCK.detachGroup((View) chain.getThisObject());
                return chain.proceed();
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native same-kind notification grouping unavailable", unavailable);
        }
    }

    /** Replace just the final UP of a confirmed tap with native CANCEL, then expand the stack. */
    private void installNotificationStackTapHook(ClassLoader loader, Class<?> stack) {
        try {
            Class<?> row = loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            NotificationCardTouchBoundary boundary = new NotificationCardTouchBoundary(stack, row);
            Method dispatch = optionalMethod(stack, "dispatchTouchEvent", android.view.MotionEvent.class);
            if (dispatch == null) throw new NoSuchMethodException("Native stack touch dispatch");
            deoptimize(dispatch);
            installHook(HookGroup.BIG_CLOCK, dispatch, chain -> {
                if (!stack.isInstance(chain.getThisObject())) return chain.proceed();
                // Observe native upward scrolling; its original touch event continues unchanged.
                try { NATIVE_STACK.touch((View) chain.getThisObject(), (android.view.MotionEvent) chain.getArg(0)); }
                catch (RuntimeException unavailable) { /* the native touch still runs */ }
                boolean expand;
                try {
                    expand = BIG_CLOCK.foldedNotificationTap((View) chain.getThisObject(),
                            (android.view.MotionEvent) chain.getArg(0), boundary);
                } catch (ReflectiveOperationException | RuntimeException unavailable) { return chain.proceed(); }
                if (!expand) return chain.proceed();
                android.view.MotionEvent cancel = android.view.MotionEvent.obtain((android.view.MotionEvent) chain.getArg(0));
                try {
                    cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    chain.proceed(new Object[]{cancel});
                } finally { cancel.recycle(); }
                BIG_CLOCK.expandNotifications();
                return true;
            });
            moduleLog(Log.INFO, TAG, "Native notification stack tap expansion connected");
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional notification stack tap expansion unavailable", unavailable);
        }
    }

    /** The native margin bounds both the shelf and notification touch; absence hides optional text. */
    private void installNotificationFooterMarginHook(Class<?> stack) {
        boolean available = false;
        try {
            Method margin = stack.getDeclaredMethod("getEmptyBottomMarginInternal");
            margin.setAccessible(true);
            if (margin.getReturnType() != Integer.TYPE)
                throw new NoSuchMethodException("Unexpected native notification bottom margin type");
            deoptimize(margin);
            installHook(HookGroup.BIG_CLOCK, margin, chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                int nativeMargin = (Integer) result;
                int clockMargin = bigClockEnabled ? BIG_CLOCK.adjustEmptyBottomMargin(view, nativeMargin) : nativeMargin;
                return CLEAR_MOTION.adjustEmptyBottomMargin(view, nativeMargin, clockMargin);
            });
            available = true;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification bottom margin hook unavailable", unavailable);
        }
        BIG_CLOCK.onFooterMarginHookAvailable(available);
        CLEAR_MOTION.onMarginHookAvailable(available);
    }

    private static View notificationStackAncestor(View source) {
        View view = source;
        for (int depth = 0; view != null && depth < 20; depth++) {
            for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
                if (type.getName().equals("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"))
                    return view;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return null;
    }

    private static Field optionalField(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try { return accessibleField(type, name); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameters) {
        for (; type != null; type = type.getSuperclass()) try {
            Method member = type.getDeclaredMethod(name, parameters); member.setAccessible(true); return member;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Method requiredMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method result = optionalMethod(type, name, parameters);
        if (result == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return result;
    }

    private static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    /** Reflection is resolved once at installation, including on high-frequency native scroll events. */
    private static final class BigClockNativeAccess {
        final Field panelView, panelAnimation, barState, qsController, stackController, stackExt, ownScroll, over;
        final Field stackHorizontalSwitch;
        final Field stackDragging, stackScroller;
        final Field stackGridCount, stackParallax, stackSplitShade;
        final Method stackScrollerFinished;
        final Method panelTranslationValue, panelTranslationY, layeringRebound, layeredAnimation, visibleChildrenCount;
        final Method panelExpandedFraction, panelRawFraction, qsExpanded, qsFraction, qsSeparate, extView;
        final Method panelRunning, panelFinalPosition, panelTracking;
        final Field panelFlinging;
        Field pageAnimation;
        Method horizontalTranslation, horizontalReady, horizontalRegister, horizontalUnregister, horizontalRunning;
        NativePageMotion pageMotion;
        NativeBackdropMotion backdropMotion;
        WeakReference<Object> lastController = new WeakReference<>(null);
        WeakReference<Object> lastVerticalValue = new WeakReference<>(null);
        boolean reboundWarning, verticalWarning;
        BigClockNativeAccess(Class<?> controller, Class<?> qs, Class<?> stack, Class<?> ext) throws ReflectiveOperationException {
            panelView = accessibleField(controller, "mView"); panelAnimation = accessibleField(controller, "mPanelAnimationEx");
            panelExpandedFraction = panelAnimation.getType().getMethod("getPanelExpandFraction");
            panelRawFraction = panelExpandedFraction.getReturnType().getMethod("getRawFraction");
            panelTranslationValue = optionalMethod(panelAnimation.getType(), "getTranslationYValue");
            panelTranslationY = panelTranslationValue == null ? null
                    : optionalMethod(panelTranslationValue.getReturnType(), "getTranslationYValue");
            panelRunning = optionalMethod(panelAnimation.getType(), "isRunningAnimation");
            panelTracking = optionalMethod(controller, "isTracking");
            panelFlinging = optionalField(controller, "mIsFlinging");
            Method finalPosition = null;
            try {
                Class<?> expanded = controller.getClassLoader().loadClass(
                        "com.oplus.systemui.panelanimation.OplusPanelAnimationExImpl$PanelExpandedFraction");
                finalPosition = optionalMethod(expanded, "getFinalPosition");
            } catch (Throwable ignored) { }
            panelFinalPosition = finalPosition;
            barState = accessibleField(controller, "mBarState"); qsController = accessibleField(controller, "mQsController");
            stackController = accessibleField(stack, "mNotificationPanelController"); stackExt = accessibleField(stack, "mExt");
            ownScroll = accessibleField(stack, "mOwnScrollY"); over = accessibleField(ext, "overDistance");
            stackHorizontalSwitch = optionalField(ext, "isHorizontalSwitch");
            stackDragging = optionalField(stack, "mIsBeingDragged");
            stackScroller = optionalField(stack, "mScroller");
            stackScrollerFinished = stackScroller == null ? null : optionalMethod(stackScroller.getType(), "isFinished");
            stackGridCount = optionalField(ext, "scrollGridCount");
            stackParallax = optionalField(ext, "isParallax");
            stackSplitShade = optionalField(stack, "mShouldUseSplitNotificationShade");
            layeringRebound = optionalMethod(ext, "getLayeringReboundTrans", Integer.TYPE, Integer.TYPE);
            layeredAnimation = optionalMethod(ext, "useLayeredAnimation", Boolean.TYPE);
            visibleChildrenCount = optionalMethod(stack, "getVisibleChildrenCount");
            qsExpanded = qs.getDeclaredMethod("getExpanded"); qsFraction = qs.getDeclaredMethod("computeExpansionFraction");
            qsSeparate = optionalMethod(qs, "isSeparateQSEnable");
            extView = ext.getDeclaredMethod("getView");
        }
        void panel(Object controller) throws ReflectiveOperationException {
            if (controller == null) return;
            Object animation = panelAnimation.get(controller);
            Object displayed = animation == null ? null : panelExpandedFraction.invoke(animation);
            // Height can reach its target while the native display spring is still moving.
            float raw = displayed == null ? 0f : ((Number) panelRawFraction.invoke(displayed)).floatValue();
            panel(controller, raw, animation, displayed);
        }
        void panel(Object controller, float displayedFraction) throws ReflectiveOperationException {
            if (controller == null) return;
            Object animation = panelAnimation.get(controller);
            Object displayed = animation == null ? null : panelExpandedFraction.invoke(animation);
            panel(controller, displayedFraction, animation, displayed);
        }
        private void panel(Object controller, float displayedFraction, Object animation, Object displayed)
                throws ReflectiveOperationException {
            if (lastController.get() != controller) lastController = new WeakReference<>(controller);
            Object qs = qsController.get(controller);
            boolean separate = qsSeparate != null && Boolean.TRUE.equals(qsSeparate.invoke(qs));
            // Separate pages move their native panel; QS expansion begins before that switch settles.
            boolean inQs = false;
            if (!separate) {
                float expansion = ((Number) qsFraction.invoke(qs)).floatValue();
                inQs = (Boolean) qsExpanded.invoke(qs)
                        || (!Float.isNaN(expansion) && !Float.isInfinite(expansion) && expansion > .001f);
            }
            // A spring may cross zero or be canceled before its replacement fling is ready.
            // Ownership ends at a settled native close, independently of the clamped draw fraction.
            boolean running = false;
            Float target = null;
            try {
                running = animation != null && panelRunning != null
                        && Boolean.TRUE.equals(panelRunning.invoke(animation));
                running |= panelTracking != null && Boolean.TRUE.equals(panelTracking.invoke(controller));
                running |= panelFlinging != null && panelFlinging.getBoolean(controller);
                if (displayed != null && panelFinalPosition != null
                        && panelFinalPosition.getDeclaringClass().isInstance(displayed)) {
                    Object value = panelFinalPosition.invoke(displayed);
                    if (value instanceof Number) target = ((Number) value).floatValue();
                }
            } catch (Throwable ignored) { }
            boolean finite = !Float.isNaN(displayedFraction) && !Float.isInfinite(displayedFraction);
            boolean targetClosed = target == null || (!Float.isNaN(target) && !Float.isInfinite(target) && target <= 0f);
            boolean settledClosed = finite && displayedFraction <= 0f && targetClosed && !running;
            BIG_CLOCK.onPanelMotionState(running, settledClosed);
            BIG_CLOCK.onPanelChanged((View) panelView.get(controller), displayedFraction, barState.getInt(controller), inQs);
            CLEAR_MOTION.onPanelChanged((View) panelView.get(controller), displayedFraction,
                    barState.getInt(controller), settledClosed);
            WALLPAPER_PANEL.changed((View) panelView.get(controller), false, barState.getInt(controller),
                    displayedFraction, settledClosed);
            readVertical(controller, animation);
            if (backdropMotion != null) backdropMotion.finishIfIdle();
        }
        private void readVertical(Object controller, Object animation) {
            if (controller == null || animation == null || panelTranslationValue == null || panelTranslationY == null) return;
            try {
                Object value = panelTranslationValue.invoke(animation);
                if (lastVerticalValue.get() != value) lastVerticalValue = new WeakReference<>(value);
                Object offset = value == null ? null : panelTranslationY.invoke(value);
                if (offset instanceof Number) {
                    BIG_CLOCK.onPanelTranslationChanged((View) panelView.get(controller), ((Number) offset).floatValue());
                    CLEAR_MOTION.onPanelTranslationChanged((View) panelView.get(controller), ((Number) offset).floatValue());
                }
            } catch (Throwable unavailable) {
                if (!verticalWarning) {
                    verticalWarning = true;
                    ModuleDiagnostics.error("bigclock", "Native clock vertical motion unavailable; entry effect retained", unavailable);
                }
            }
        }
        void verticalChanged(Object value) {
            Object controller = lastController.get();
            if (controller == null || value != lastVerticalValue.get()) return;
            try { readVertical(controller, panelAnimation.get(controller)); }
            catch (Throwable unavailable) {
                if (!verticalWarning) {
                    verticalWarning = true;
                    ModuleDiagnostics.error("bigclock", "Native clock vertical source unavailable", unavailable);
                }
            }
        }
        void rebound(View stack, float[] offsets) {
            offsets[0] = offsets[1] = 0f;
            if (stack == null || layeringRebound == null || layeredAnimation == null
                    || visibleChildrenCount == null || stackSplitShade == null) return;
            try {
                Object ext = stackExt.get(stack);
                if (ext == null || !Boolean.TRUE.equals(layeredAnimation.invoke(ext, stackSplitShade.getBoolean(stack)))) return;
                boolean parallax = stackParallax != null && stackParallax.getBoolean(ext);
                int count = parallax && stackGridCount != null ? stackGridCount.getInt(ext)
                        : ((Number) visibleChildrenCount.invoke(stack)).intValue();
                if (count <= 0) return;
                offsets[0] = ((Number) layeringRebound.invoke(ext, 0, count)).floatValue();
                offsets[1] = count == 1 ? offsets[0]
                        : ((Number) layeringRebound.invoke(ext, count - 1, count)).floatValue();
            } catch (Throwable unavailable) {
                offsets[0] = offsets[1] = 0f;
                if (!reboundWarning) {
                    reboundWarning = true;
                    ModuleDiagnostics.error("bigclock", "Native row rebound unavailable; clock keeps its stable layout", unavailable);
                }
            }
        }
        void resolvePageMotion(Class<?> pager) throws ReflectiveOperationException {
            Field animation = accessibleField(pager, "slidSwitchAnimation");
            Method horizontal = optionalMethod(animation.getType(), "getHorizontalTranslationValue");
            if (horizontal == null) throw new NoSuchMethodException("getHorizontalTranslationValue");
            Class<?> value = horizontal.getReturnType();
            Method ready = optionalMethod(value, "hasProgressiveHelper");
            Method register = optionalMethod(value, "register", View.class);
            Method unregister = optionalMethod(value, "unRegister", View.class);
            if (ready == null || register == null || unregister == null)
                throw new NoSuchMethodException("Native horizontal child registration");
            pageAnimation = animation; horizontalTranslation = horizontal; horizontalReady = ready;
            horizontalRegister = register; horizontalUnregister = unregister;
            horizontalRunning = optionalMethod(value, "isRunning");
        }
        void pageReady(Object pager) {
            try {
                Object animation = pageAnimation.get(pager);
                Object value = animation == null ? null : horizontalTranslation.invoke(animation);
                if (value == null || !Boolean.TRUE.equals(horizontalReady.invoke(value))) {
                    BIG_CLOCK.onPageMotionReady(null);
                    return;
                }
                if (pageMotion == null || pageMotion.value.get() != value) {
                    pageMotion = new NativePageMotion(value, horizontalReady, horizontalRegister,
                            horizontalUnregister, horizontalRunning);
                }
                // Native resetHelper clears registrations; the wrapper remains stable across rebuilds.
                BIG_CLOCK.onPageMotionReady(pageMotion);
                if (backdropMotion != null) backdropMotion.finishIfIdle();
            } catch (Throwable error) {
                ModuleDiagnostics.error("hooks", "Notification clock page registration unavailable", error);
            }
        }
        boolean horizontalSwitch(View stack) {
            if (stack == null || stackHorizontalSwitch == null) return false;
            try {
                Object ext = stackExt.get(stack);
                return ext != null && stackHorizontalSwitch.getBoolean(ext);
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
        boolean notificationScrolling(View stack) {
            if (stack == null) return false;
            try {
                if (stackDragging != null && stackDragging.getBoolean(stack)) return true;
                Object scroller = stackScroller == null ? null : stackScroller.get(stack);
                return scroller != null && stackScrollerFinished != null
                        && Boolean.FALSE.equals(stackScrollerFinished.invoke(scroller));
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
        void scroll(View stack) throws ReflectiveOperationException {
            if (stack == null) return;
            Object ext = stackExt.get(stack);
            BIG_CLOCK.onScrollChanged(stack, ownScroll.getInt(stack), ext == null ? 0f : over.getFloat(ext));
            CLEAR_MOTION.onScrollChanged(stack);
            // Refresh geometry only after the complete scroll snapshot has been applied.
            panel(stackController.get(stack));
        }
    }

    /** Keep the owned notification backdrop on the same display fraction as its foreground. */
    private void installNotificationBackdropMotionHook(ClassLoader loader, BigClockNativeAccess nativeAccess) {
        try {
            NativeBackdropMotion motion = new NativeBackdropMotion(loader, nativeAccess);
            deoptimize(motion.setBlur); deoptimize(motion.setRaw);
            installHook(HookGroup.BIG_CLOCK, motion.setBlur, chain -> {
                float nativeFraction = (Float) chain.getArg(0);
                float displayedFraction = motion.blurFraction(chain.getThisObject(), nativeFraction);
                Object result = displayedFraction == nativeFraction ? chain.proceed()
                        : chain.proceed(new Object[]{displayedFraction});
                motion.finishIfIdle();
                return result;
            });
            installHook(HookGroup.BIG_CLOCK, motion.setRaw, chain -> {
                Object result = chain.proceed();
                motion.rawChanged(chain.getThisObject());
                return result;
            });
            nativeAccess.backdropMotion = motion;
            notificationBackdropMotion = motion;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Optional notification backdrop synchronization unavailable", error);
        }
    }

    /** Optional, scene-scoped bridge between the two native foreground/backdrop springs. */
    private static final class NativeBackdropMotion {
        final BigClockNativeAccess access;
        final Method setRaw, setBlur, getBlur, getBlurAnimation, blurRunning;
        final Field separateLazy, pagerLazy, targetPage, blurValue;
        final Method separateGet, pagerGet, separateEnabled, draggingX, qsCollapsed;
        WeakReference<Object> owned = new WeakReference<>(null);
        boolean synchronizing, failed;
        float latestNativeBlur, appliedBlur = Float.NaN;
        NativeBackdropMotion(ClassLoader loader, BigClockNativeAccess access) throws ReflectiveOperationException {
            this.access = access;
            Class<?> fraction = loader.loadClass("com.oplus.systemui.panelanimation.OplusPanelAnimationExImpl$PanelExpandedFraction");
            Class<?> separate = loader.loadClass("com.oplus.systemui.separate.OplusSeparateNotificationAndQSExImpl");
            Class<?> pager = loader.loadClass("com.oplus.systemui.separate.OplusPanelViewPagerController");
            setRaw = requiredMethod(fraction, "setRawFraction", Float.TYPE);
            setBlur = requiredMethod(fraction, "setBlurFraction", Float.TYPE);
            getBlur = requiredMethod(fraction, "getBlurFraction");
            blurValue = accessibleField(fraction, "blurFraction");
            getBlurAnimation = requiredMethod(fraction, "getBlurFractionAnimation");
            blurRunning = requiredMethod(getBlurAnimation.getReturnType(), "isRunning");
            separateLazy = accessibleField(fraction, "separateNotificationAndQSEx");
            pagerLazy = accessibleField(separate, "panelViewPagerController");
            targetPage = accessibleField(pager, "targetPage");
            separateGet = separateLazy.getType().getMethod("get");
            pagerGet = pagerLazy.getType().getMethod("get");
            separateEnabled = requiredMethod(separate, "enableSeparateNotificationAndQS");
            draggingX = requiredMethod(separate, "isDraggingX");
            qsCollapsed = requiredMethod(separate, "isFullyCollapsed");
        }
        private static Method requiredMethod(Class<?> type, String name, Class<?>... parameters)
                throws NoSuchMethodException {
            Method method = optionalMethod(type, name, parameters);
            if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
            return method;
        }
        /** A closing blur spring can outlive the clock; retain this exact native instance until idle. */
        private boolean owns(Object fraction) throws ReflectiveOperationException {
            if (failed || !bigClockEnabled || fraction == null) { release(); return false; }
            Object controller = access.lastController.get();
            if (controller == null || access.barState.getInt(controller) != 0) { release(); return false; }
            View host = (View) access.panelView.get(controller);
            if (host == null || !BIG_CLOCK.clockLayoutEnabled(host) || host.getResources().getConfiguration().orientation
                    != android.content.res.Configuration.ORIENTATION_PORTRAIT) { release(); return false; }
            Object panelAnimation = access.panelAnimation.get(controller);
            Object current = panelAnimation == null ? null : access.panelExpandedFraction.invoke(panelAnimation);
            if (current != fraction) { release(); return false; }
            Object lazy = separateLazy.get(fraction);
            Object separate = lazy == null ? null : separateGet.invoke(lazy);
            if (separate == null || !Boolean.TRUE.equals(separateEnabled.invoke(separate))
                    || !Boolean.TRUE.equals(qsCollapsed.invoke(separate))
                    || Boolean.TRUE.equals(draggingX.invoke(separate))
                    || access.pageMotion != null && access.pageMotion.isRunning()
                    || Math.abs(host.getTranslationX()) > .5f) { release(); return false; }
            Object lazyPager = pagerLazy.get(separate);
            Object pager = lazyPager == null ? null : pagerGet.invoke(lazyPager);
            Object page = pager == null ? null : targetPage.get(pager);
            if (!(page instanceof Enum) || !"NOTIFICATION".equals(((Enum<?>) page).name())) {
                release(); return false;
            }
            float raw = ((Number) access.panelRawFraction.invoke(fraction)).floatValue();
            if (!NotificationBigClockModel.finite(raw)) { release(); return false; }
            if (owned.get() != fraction) {
                if (raw <= 0f) return false;
                release();
                if (failed) return false;
                latestNativeBlur = blurValue.getFloat(fraction);
                appliedBlur = Float.NaN;
                owned = new WeakReference<>(fraction);
            }
            return true;
        }
        float blurFraction(Object fraction, float nativeValue) {
            if (synchronizing) return nativeValue;
            try {
                if (!owns(fraction)) return nativeValue;
                latestNativeBlur = nativeValue;
                appliedBlur = NotificationBigClockModel.clamp(((Number) access.panelRawFraction.invoke(fraction)).floatValue(), 0f, 1f);
                return appliedBlur;
            } catch (Throwable error) { failure(error); return nativeValue; }
        }
        void rawChanged(Object fraction) {
            if (synchronizing) return;
            try {
                if (!owns(fraction)) return;
                float raw = NotificationBigClockModel.clamp(((Number) access.panelRawFraction.invoke(fraction)).floatValue(), 0f, 1f);
                // Invoke the original setter/callback chain, so native alpha, blur and mirror scale
                // receive the same final zero even when the independent blur spring already ended.
                synchronizing = true;
                try { setBlur.invoke(fraction, raw); appliedBlur = raw; }
                finally { synchronizing = false; }
            } catch (Throwable error) { failure(error); }
        }
        void finishIfIdle() {
            Object fraction = owned.get();
            if (fraction == null || synchronizing || failed) return;
            try {
                if (!owns(fraction)) return;
                float raw = ((Number) access.panelRawFraction.invoke(fraction)).floatValue();
                float blur = ((Number) getBlur.invoke(fraction)).floatValue();
                Object animation = getBlurAnimation.invoke(fraction);
                if (raw <= 0f && blur <= 0f && (animation == null || !Boolean.TRUE.equals(blurRunning.invoke(animation))))
                    { owned.clear(); appliedBlur = Float.NaN; }
            } catch (Throwable error) { failure(error); }
        }
        private void failure(Throwable error) {
            if (!failed) ModuleDiagnostics.error("bigclock", "Native notification backdrop sync unavailable; native motion retained", error);
            failed = true; release();
        }
        /** Return only our last written value; a newer native writer always wins. */
        void release() {
            if (synchronizing) return;
            Object fraction = owned.get();
            float previous = latestNativeBlur, applied = appliedBlur;
            owned.clear(); appliedBlur = Float.NaN;
            if (fraction == null || !NotificationBigClockModel.finite(applied)) return;
            try {
                if (blurValue.getFloat(fraction) != applied) return;
                synchronizing = true;
                try { setBlur.invoke(fraction, previous); }
                finally { synchronizing = false; }
            } catch (Throwable error) {
                if (!failed) ModuleDiagnostics.error("bigclock", "Native notification backdrop restore unavailable", error);
                failed = true;
            }
        }
    }

    private static final class NativePageMotion implements NotificationBigClock.PageMotion {
        final WeakReference<Object> value;
        final Method ready, register, unregister, running;
        boolean reported;
        NativePageMotion(Object value, Method ready, Method register, Method unregister, Method running) {
            this.value = new WeakReference<>(value); this.ready = ready; this.register = register;
            this.unregister = unregister; this.running = running;
        }
        @Override public void register(View view) { invoke(register, view, true); }
        @Override public void unregister(View view) { invoke(unregister, view, false); }
        private void invoke(Method method, View view, boolean needsHelper) {
            Object nativeValue = value.get();
            if (nativeValue == null || view == null) return;
            try {
                if (!needsHelper || Boolean.TRUE.equals(ready.invoke(nativeValue))) method.invoke(nativeValue, view);
            } catch (Throwable error) {
                if (!reported) {
                    reported = true;
                    ModuleDiagnostics.error("hooks", "Notification clock native page child unavailable", error);
                }
            }
        }
        @Override public boolean isRunning() {
            Object nativeValue = value.get();
            if (nativeValue == null || running == null) return false;
            try { return Boolean.TRUE.equals(running.invoke(nativeValue)); }
            catch (Throwable ignored) { return false; }
        }
    }

    private boolean installDataBatterySpacing(ClassLoader loader) {
        try {
            Class<?> container = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
            Class<?> meter = loader.loadClass("com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView");
            Class<?> modern = loader.loadClass(MODERN_VIEW);
            Field measured = container.getDeclaredField("mMeasureViews"); measured.setAccessible(true);
            Method slot = modern.getMethod("getSlot");
            Method measure = container.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE);
            deoptimize(measure);
            installHook(HookGroup.DATA_SPACING, measure, chain -> {
                Object result = chain.proceed();
                Object nativeList = measured.get(chain.getThisObject());
                List<?> list = nativeList instanceof List ? (List<?>) nativeList : Collections.emptyList();
                for (Object item : list) if (modern.isInstance(item) && !DATA_BATTERY_SPACING.slotKnown((View) item)) {
                    try {
                        String nativeSlot = (String) slot.invoke(item);
                        if (nativeSlot == null) DATA_BATTERY_SPACING.unknownSlot((View) item);
                        else DATA_BATTERY_SPACING.mobileBound((View) item, nativeSlot);
                    }
                    catch (Throwable unreadable) { DATA_BATTERY_SPACING.unknownSlot((View) item); }
                }
                DATA_BATTERY_SPACING.measuredNative((ViewGroup) chain.getThisObject(), list, meter, container);
                return result;
            });
            for (String event : new String[]{"onViewAdded", "onViewRemoved"}) {
                Method method = container.getDeclaredMethod(event, View.class); deoptimize(method);
                installHook(HookGroup.DATA_SPACING, method, chain -> {
                    Object result = chain.proceed();
                    DATA_BATTERY_SPACING.containerChanged((ViewGroup) chain.getThisObject()); return result;
                });
            }
            Method init = modern.getDeclaredMethod("initView", String.class, loader.loadClass("kotlin.jvm.functions.Function0"));
            deoptimize(init);
            installHook(HookGroup.DATA_SPACING, init, chain -> {
                Object result = chain.proceed();
                DATA_BATTERY_SPACING.mobileBound((View) chain.getThisObject(), (String) chain.getArg(0)); return result;
            });
            for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
                final boolean attached = event.equals("onAttachedToWindow");
                Method method = View.class.getDeclaredMethod(event); deoptimize(method);
                installHook(HookGroup.DATA_SPACING, method, chain -> {
                    Object result = chain.proceed(); View view = (View) chain.getThisObject();
                    if (meter.isInstance(view)) {
                        if (attached) {
                            ViewGroup nativeContainer = DATA_BATTERY_SPACING.batteryAttached(view, container);
                            if (nativeContainer != null) {
                                Object nativeList = measured.get(nativeContainer);
                                List<?> list = nativeList instanceof List ? (List<?>) nativeList : Collections.emptyList();
                                Object last = list.isEmpty() ? null : list.get(list.size() - 1);
                                DATA_BATTERY_SPACING.measured(nativeContainer, last instanceof View ? (View) last : null);
                            }
                        }
                        else DATA_BATTERY_SPACING.batteryDetached(view);
                    } else if (modern.isInstance(view)) {
                        if (attached) DATA_BATTERY_SPACING.mobileBound(view, (String) slot.invoke(view));
                        else DATA_BATTERY_SPACING.mobileDetached(view);
                    }
                    return result;
                });
            }
            Method layoutSetter = View.class.getDeclaredMethod("setLayoutParams", ViewGroup.LayoutParams.class);
            deoptimize(layoutSetter);
            installHook(HookGroup.DATA_SPACING, layoutSetter, chain -> {
                DATA_BATTERY_SPACING.requestedLayout((View) chain.getThisObject(), (ViewGroup.LayoutParams) chain.getArg(0));
                return chain.proceed();
            });
            Class<?> model = loader.loadClass("com.oplus.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModelImpl");
            Field stackedOwner = model.getDeclaredField("stackedMobileIconViewModel"); stackedOwner.setAccessible(true);
            Class<?> textModel = loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel");
            Method nativeType = textModel.getMethod("getNetworkTypeText");
            Method typeName = loader.loadClass(NETWORK_TYPE).getMethod("getName");
            Method info = model.getDeclaredMethod("getNetworkTypeTextInfo"); deoptimize(info);
            installHook(HookGroup.DATA_SPACING, info, chain -> {
                Object result = chain.proceed(), owner = stackedOwner.get(chain.getThisObject());
                if (DATA_BATTERY_SPACING.labelKnown(owner, result)) return result;
                String raw;
                try {
                    Object type = result == null ? null : nativeType.invoke(result);
                    raw = type == null ? "" : (String) typeName.invoke(type);
                    if (raw == null) raw = "";
                } catch (Throwable unavailable) { raw = null; }
                DATA_BATTERY_SPACING.labelChanged(owner, result, raw); return result;
            });
            // Original NetworkTypeText returns before emitting any node when info or its
            // networkTypeText is null. This actual branch must establish absence even
            // when the strategy's hydrated getter was skipped or already inlined.
            for (Method text : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt").getDeclaredMethods())
                if (text.getName().startsWith("NetworkTypeText-") && text.getParameterCount() > 2
                        && text.getParameterTypes()[1] == textModel) {
                    deoptimize(text);
                    installHook(HookGroup.DATA_SPACING, text, chain -> {
                        Object owner = chain.getArg(0), nativeInfo = chain.getArg(1);
                        if (!DATA_BATTERY_SPACING.labelKnown(owner, nativeInfo)) {
                            String raw;
                            try {
                                Object type = nativeInfo == null ? null : nativeType.invoke(nativeInfo);
                                raw = type == null ? "" : (String) typeName.invoke(type);
                                if (raw == null) raw = "";
                            } catch (Throwable unreadable) { raw = null; }
                            DATA_BATTERY_SPACING.labelChanged(owner, nativeInfo, raw);
                        }
                        return chain.proceed();
                    });
                }
            // These are the native Compose callers of the hydrated text getter on this ROM.
            for (String strategy : new String[]{"BigType", "Default", "TigoFiveG"})
                for (Method caller : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable."
                        + strategy + "StackedMobileIconLayoutStrategy").getDeclaredMethods())
                    if (caller.getName().equals("StackedMobileIcon")) deoptimize(caller);
            return true;
        } catch (ClassNotFoundException unsupported) {
            moduleLog(Log.INFO, TAG, "Native cellular/battery spacing unsupported; preserving its layout"); return true;
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not bind native cellular/battery spacing", error); return false;
        }
    }

    private boolean installBatteryControls(ClassLoader loader) {
        try {
            String root = "com.oplus.systemui.statusbar.pipeline.battery.ui.";
            Class<?> meter = loader.loadClass(root + "view.StatBatteryMeterView");
            Class<?> horizontal = loader.loadClass(root + "drawable.HorizontalBatteryContentDrawable");
            ICON_PACK_DRAWING.bindBatteryClass(horizontal);
            Class<?> charge = loader.loadClass(root + "model.ChargeIcon");
            Class<?> binder = loader.loadClass(root + "binder.BatteryViewBinder");
            BatteryControls controls = BATTERY;
            if (controls == null) BATTERY = controls = new BatteryControls(MAIN, meter, horizontal, charge);
            final BatteryControls controller = controls;
            controller.onPercentStyleChanged(owner -> {
                ICON_PACK_DRAWING.syncBattery(owner,null);
                systemTextChanged(BigClockSystemIcon.nativeBatteryChanged(owner));
            });
            installHook(HookGroup.BATTERY,horizontal.getSuperclass().getDeclaredMethod("setColors",Integer.TYPE,Integer.TYPE,Integer.TYPE),chain -> {
                return chain.proceed(controller.nativeColors((Drawable)chain.getThisObject(),(Integer)chain.getArg(0),(Integer)chain.getArg(1),(Integer)chain.getArg(2)));
            });
            installHook(HookGroup.BATTERY,ViewGroup.class.getDeclaredMethod("dispatchDraw",Canvas.class),chain -> {
                View view=(View)chain.getThisObject();
                if(!meter.isInstance(view)||!controller.isOwner(view))return chain.proceed();
                Canvas canvas=(Canvas)chain.getArg(0);int saved=canvas.save();
                try {controller.beforeDraw(view,canvas);return chain.proceed();}
                finally {canvas.restoreToCount(saved);}
            });
            installHook(HookGroup.BATTERY, horizontal.getDeclaredMethod("drawContent", Canvas.class, android.graphics.RectF.class), chain -> {
                if (controller.drawContent((Drawable) chain.getThisObject(), (Canvas) chain.getArg(0),
                        (android.graphics.RectF) chain.getArg(1))) return null;
                return chain.proceed();
            });
            installHook(HookGroup.BATTERY,horizontal.getDeclaredMethod("draw",Canvas.class),chain->{
                Drawable drawable=(Drawable)chain.getThisObject();
                controller.prepareDraw(drawable);
                try(BatteryControls.ChargeDrawScope chargeScope=controller.beginChargeDraw(drawable);
                        IconPackDrawing.BatteryScope scope=ICON_PACK_DRAWING.beginBattery(drawable)){return chain.proceed();}
            });
            Method chargeGetter=horizontal.getMethod("getChargeIconId");
            deoptimize(chargeGetter);
            deoptimize(horizontal.getDeclaredMethod("draw",Canvas.class));
            deoptimize(horizontal.getSuperclass().getDeclaredMethod("drawChargingIcon",Canvas.class,android.graphics.RectF.class));
            installHook(HookGroup.BATTERY,chargeGetter,chain->{
                int nativeId=(Integer)chain.proceed();
                return controller.chargeIconId((Drawable)chain.getThisObject(),nativeId);
            });
            for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
                final boolean attached = event.equals("onAttachedToWindow");
                installHook(HookGroup.BATTERY, meter.getDeclaredMethod(event), chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    if (attached) { initializeContext(view.getContext()); controller.attach(view); ICON_PACK_DRAWING.syncBattery(view,null);
                        systemTextChanged(BigClockSystemIcon.nativeBatteryChanged(view)); }
                    else {controller.detach(view);systemTextChanged(BigClockSystemIcon.nativeBatteryDetached(view));}
                    return result;
                });
            }
            for (Method method : binder.getDeclaredMethods()) {
                if (method.getName().equals("bind$updateChargingView")) {
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed();
                        controller.sync((View) chain.getArg(1), chain.getArg(2), (View) chain.getArg(0));
                        ICON_PACK_DRAWING.syncBattery((View)chain.getArg(1),chain.getArg(2));
                        systemTextChanged(BigClockSystemIcon.nativeBatteryChanged((View)chain.getArg(1)));
                        return result;
                    });
                } else if (method.getName().equals("bind$updateBatteryContentView")
                        || method.getName().equals("bind$updateBatteryIconStyle")) {
                    final int ownerIndex = method.getName().equals("bind$updateBatteryContentView") ? 0 : 1;
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed();
                        controller.sync((View) chain.getArg(ownerIndex));
                        ICON_PACK_DRAWING.syncBattery((View)chain.getArg(ownerIndex),null);
                        systemTextChanged(BigClockSystemIcon.nativeBatteryChanged((View)chain.getArg(ownerIndex)));
                        return result;
                    });
                }
                deoptimize(method);
            }
            for (Method method : meter.getDeclaredMethods()) {
                if(method.getName().equals("onLayout")) {
                    deoptimize(method);
                    installHook(HookGroup.BATTERY,method,chain->{Object result=chain.proceed();
                        systemTextChanged(BigClockSystemIcon.nativeBatteryChanged((View)chain.getThisObject()));return result;});
                } else if (method.getName().equals("setBatteryCharge") || method.getName().equals("setBatteryStyleDrawable")) {
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed(); controller.sync((View) chain.getThisObject());
                        ICON_PACK_DRAWING.syncBattery((View)chain.getThisObject(),null);
                        systemTextChanged(BigClockSystemIcon.nativeBatteryChanged((View)chain.getThisObject()));return result;
                    });
                }
            }
            for (String event : new String[]{"onVisibilityChanged", "onWindowVisibilityChanged"}) {
                Method method = event.equals("onVisibilityChanged")
                        ? View.class.getDeclaredMethod(event, View.class, Integer.TYPE)
                        : View.class.getDeclaredMethod(event, Integer.TYPE);
                installHook(HookGroup.BATTERY, method, chain -> {
                    Object result = chain.proceed();
                    controller.visibilityChanged((View) chain.getThisObject()); return result;
                });
            }
            // The drawable calls its content method from this native superclass.
            deoptimize(horizontal.getSuperclass().getDeclaredMethod("draw", Canvas.class));
            deoptimize(horizontal.getDeclaredMethod("draw", Canvas.class));
            readStyleSettings();
            return true;
        } catch (ClassNotFoundException unsupported) {
            moduleLog(Log.INFO, TAG, "Native battery adaptation unavailable on this SystemUI; preserving its battery");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not install native battery content controls", error);
            return false;
        }
    }

    private boolean installNotificationClearAppearance(ClassLoader loader) {
        try {
            Class<?> controller = loader.loadClass(NotificationClearAppearance.CONTROLLER_CLASS);
            int installed = 0;
            for (Method event : controller.getDeclaredMethods()) {
                if (!event.getName().matches("bindViews|updatePlatformBlurDrawable(?:\\$default)?|onConfigChanged"
                        + "|onHeightUpdated|updateClearAllPosition|updateLayoutParams|setVisible|onStateChanged"
                        + "|updateQsExpansion|updateVisibility|setShowDismiss|setQsCustomizerShowing")) continue;
                deoptimize(event);
                final boolean clearMaterial = event.getName().matches("bindViews|updatePlatformBlurDrawable(?:\\$default)?|onConfigChanged");
                final boolean nativeFadeStart = event.getName().equals("setVisible") || event.getName().equals("updateQsExpansion");
                installHook(HookGroup.NOTIFICATION_CLEAR, event, chain -> {
                    Object owner = chain.getThisObject();
                    if (!controller.isInstance(owner) && event.getParameterTypes().length > 0
                            && controller.isAssignableFrom(event.getParameterTypes()[0])) owner = chain.getArg(0);
                    if (!nativeFadeStart || !CLEAR_MOTION.needsNativeScope()) {
                        Object result = chain.proceed();
                        if (clearMaterial) CLEAR_APPEARANCE.bindController(owner);
                        CLEAR_MOTION.onControllerChanged(owner, clearMaterial);
                        return result;
                    }
                    try (NotificationClearMotion.NativeScope scope = CLEAR_MOTION.beforeNative(owner)) {
                        Object result = chain.proceed();
                        CLEAR_MOTION.onControllerChanged(owner, false);
                        return result;
                    }
                });
                installed++;
            }
            for (String name : new String[]{"setAlpha", "setTranslationY"}) {
                Method setter = View.class.getDeclaredMethod(name, Float.TYPE);
                deoptimize(setter);
                installHook(HookGroup.NOTIFICATION_CLEAR, setter, chain -> {
                    View view = (View) chain.getThisObject();
                    if (!CLEAR_MOTION.tracks(view)) return chain.proceed();
                    float value = (Float) chain.getArg(0);
                    float result = name.equals("setAlpha") ? CLEAR_MOTION.nativeAlpha(view, value)
                            : CLEAR_MOTION.nativeTranslationY(view, value);
                    return result == value ? chain.proceed() : chain.proceed(new Object[]{result});
                });
            }
            Class<?> button = loader.loadClass(NotificationClearAppearance.BUTTON_CLASS);
            Method touch = button.getDeclaredMethod("onTouchEvent", android.view.MotionEvent.class);
            deoptimize(touch);
            installHook(HookGroup.NOTIFICATION_CLEAR, touch, chain ->
                    CLEAR_MOTION.suppressTouchDown((View) chain.getThisObject(),
                            (android.view.MotionEvent) chain.getArg(0)) ? false : chain.proceed());
            return installed > 0;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification clear appearance hooks unavailable", unavailable);
            return false;
        }
    }

    private static final int DRAW_MEDIA = 1, DRAW_DEVICE = 2, DRAW_CLEAR = 4;
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Integer> DRAW_KINDS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Which of the draw-time owners a view class can belong to; a pure function of the class, computed once. */
    private static int drawKinds(Class<?> type) {
        Integer known = DRAW_KINDS.get(type);
        if (known != null) return known;
        int kinds = (QsMediaAppearance.isMediaType(type) ? DRAW_MEDIA : 0)
                | (QsTileAppearance.isDeviceCardType(type) ? DRAW_DEVICE : 0)
                | (QsTileAppearance.typeOf(type, NotificationClearAppearance.BUTTON_CLASS) ? DRAW_CLEAR : 0);
        DRAW_KINDS.put(type, kinds);
        return kinds;
    }

    private static boolean isHighlightSurface(View view) {
        return C17_HIGHLIGHTS.isSurface(view);
    }

    /** Standard AOD media draws its backdrop in onDraw; media children are dispatched separately. */
    private void installAodNotificationMedia(ClassLoader loader) {
        synchronized (SYSTEMUI_HOOK_LOCK) { if (AOD_MEDIA_LOADERS.contains(loader)) return; }
        try {
            Class<?> media=loader.loadClass(AodNotificationBackground.MEDIA_HOST);
            Method draw=media.getDeclaredMethod("onDraw",Canvas.class);deoptimize(draw);
            installHook(HookGroup.FLUID_NOTIFICATION,draw,chain -> AOD_NOTIFICATION_BACKGROUND.drawMediaBackground(
                    (View)chain.getThisObject(),(Canvas)chain.getArg(0),canvas -> chain.proceed(new Object[]{canvas})));
            for(Constructor<?> constructor:media.getDeclaredConstructors())installHook(HookGroup.FLUID_NOTIFICATION,constructor,chain -> {
                Object result=chain.proceed();AOD_NOTIFICATION_BACKGROUND.observeMedia((View)chain.getThisObject());return result;
            });
            for(Method event:media.getDeclaredMethods()) {
                String name=event.getName();
                if(Modifier.isStatic(event.getModifiers())||Modifier.isAbstract(event.getModifiers())
                        ||!name.matches("onAttachedToWindow|onConfigurationChanged|setPanoramicAodDrawable|updateBgColor"))continue;
                deoptimize(event);installHook(HookGroup.FLUID_NOTIFICATION,event,chain -> {
                    Object result=chain.proceed();View view=(View)chain.getThisObject();
                    AOD_NOTIFICATION_BACKGROUND.observeMedia(view);
                    return result;
                });
            }
            Class<?> changer=loader.loadClass("com.oplus.systemui.notification.aod.notification.MediaAodBackgroundChanger");
            for(Method caller:changer.getDeclaredMethods())if(caller.getName().equals("changeAodViewColor"))deoptimize(caller);
            synchronized (SYSTEMUI_HOOK_LOCK) { AOD_MEDIA_LOADERS.add(loader); }
        }catch(ReflectiveOperationException|LinkageError unavailable){
            moduleLog(Log.INFO,TAG,"Optional native AOD media background unavailable; native media retained");
        }
    }

    /** Exact optical shader/draw contracts from the connected ColorOS 17 build. */
    private void installC17HighlightRemoval(ClassLoader loader) {
        CUSTOM_PLUGINS.rememberLoader(loader);
        installAodNotificationMedia(loader);
        synchronized (SYSTEMUI_HOOK_LOCK) { if (!HIGHLIGHT_LOADERS.add(loader)) return; }
        try {
            Method uniform = android.graphics.RuntimeShader.class.getDeclaredMethod("setFloatUniform", String.class, float[].class);
            deoptimize(uniform);
            installHook(HookGroup.C17_HIGHLIGHTS, uniform, chain -> !C17_HIGHLIGHTS.optionsActive() ? chain.proceed()
                    : C17_HIGHLIGHTS.writeUniform(
                    (android.graphics.RuntimeShader) chain.getThisObject(), (String) chain.getArg(0), (float[]) chain.getArg(1),
                    values -> chain.proceed(new Object[]{chain.getArg(0), values})));
            Class<?> base = loader.loadClass("com.oplus.posteffect.drawable.BaseDrawable");
            Method draw = base.getDeclaredMethod("draw", Canvas.class); deoptimize(draw);
            installHook(HookGroup.C17_HIGHLIGHTS, draw, chain -> {
                if (C17_HIGHLIGHTS.optionsActive()) C17_HIGHLIGHTS.bindDrawable((Drawable) chain.getThisObject());
                return chain.proceed();
            });
            for (String caller : new String[]{"com.oplus.posteffect.drawable.BaseDrawable",
                    "com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable"}) try {
                for (Method method : loader.loadClass(caller).getDeclaredMethods())
                    if (method.getName().matches("draw|access\\$drawContent|preDrawContent|applyDrawableShader")) deoptimize(method);
            } catch (ClassNotFoundException unavailable) { }
            for (String caller : new String[]{"com.oplus.posteffect.params.GradientStrokeLineParamsKt",
                    "com.oplus.posteffect.params.OpticsParamsKt", "com.oplus.posteffect.params.InnerShadowParamsKt",
                    "com.oplus.posteffect.agsl.effects.GradientStrokeEffect", "com.oplus.posteffect.agsl.effects.InnerShadowEffect",
                    "com.oplus.posteffect.agsl.effects.OpticsEffect", "com.oplus.posteffect.agsl.DrawableShader"}) try {
                for (Method method : loader.loadClass(caller).getDeclaredMethods())
                    if (method.getName().matches("setGradientStrokeLineUniform|setOpticsUniform|setInnerShadowUniform|pushUniforms|updateShaderBlendParams")) deoptimize(method);
            } catch (ClassNotFoundException unavailable) { }
            Method detached = View.class.getDeclaredMethod("onDetachedFromWindow");
            installHook(HookGroup.C17_HIGHLIGHTS, detached, chain -> {
                Object result = chain.proceed(); View host = (View) chain.getThisObject();
                if (isHighlightSurface(host)) C17_HIGHLIGHTS.detach(host);
                return result;
            });
            COMPLETED_HOOK_GROUPS.add(HookGroup.C17_HIGHLIGHTS);
        } catch (Throwable unavailable) { moduleLog(Log.WARN, TAG, "Optional C17 optical shader contract unavailable", unavailable); }
        lazyHook("device_gradient", () -> C17_HIGHLIGHTS.deviceBackgroundWanted() || QS_APPEARANCE.deviceBackgroundWanted(), () -> {
            Method draw = android.graphics.drawable.GradientDrawable.class.getDeclaredMethod("draw", Canvas.class);
            deoptimize(draw);
            installHook(HookGroup.C17_HIGHLIGHTS, draw, chain -> !C17_HIGHLIGHTS.deviceBackgroundWanted() && !QS_APPEARANCE.deviceBackgroundWanted()
                    ? chain.proceed() : C17_HIGHLIGHTS.drawDeviceBackground(
                    (Drawable) chain.getThisObject(), (Canvas) chain.getArg(0),
                    canvas -> {
                        QS_APPEARANCE.drawDeviceBackground((Drawable) chain.getThisObject(), canvas,
                                target -> chain.proceed(new Object[]{target}));
                        return null;
                    }));
        });
        try {
            Class<?> background = loader.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView");
            Method draw = background.getDeclaredMethod("draw", Canvas.class, Drawable.class); deoptimize(draw);
            installHook(HookGroup.C17_HIGHLIGHTS, draw, chain -> {
                return AOD_NOTIFICATION_BACKGROUND.drawNotificationBackground((View)chain.getThisObject(),(Canvas)chain.getArg(0),
                        whiteCanvas -> C17_HIGHLIGHTS.drawNotificationBackground((View)chain.getThisObject(),whiteCanvas,
                                canvas -> chain.proceed(new Object[]{canvas,chain.getArg(1)})));
            });
            Method keyguard=background.getDeclaredMethod("setOnKeyguard",Boolean.TYPE);deoptimize(keyguard);
            installHook(HookGroup.FLUID_NOTIFICATION,keyguard,chain -> {
                Object result=chain.proceed();AOD_NOTIFICATION_BACKGROUND.observeBody((View)chain.getThisObject());return result;
            });
        } catch (Throwable unavailable) { moduleLog(Log.WARN, TAG, "Optional C17 notification material scope unavailable", unavailable); }
        try {
            Class<?> extension=loader.loadClass("com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp");
            Method draw=extension.getDeclaredMethod("draw",Canvas.class,Drawable.class);deoptimize(draw);
            installHook(HookGroup.C17_HIGHLIGHTS,draw,chain -> AOD_NOTIFICATION_BACKGROUND.drawNotificationBackgroundExtension(
                    chain.getThisObject(),(Canvas)chain.getArg(0),whiteCanvas -> C17_HIGHLIGHTS.drawNotificationBackgroundExtension(
                            chain.getThisObject(),whiteCanvas,canvas -> chain.proceed(new Object[]{canvas,chain.getArg(1)}))));
            Method drawIcon=extension.getDeclaredMethod("drawIcon",Canvas.class);deoptimize(drawIcon);
            installHook(HookGroup.C17_HIGHLIGHTS,drawIcon,chain ->
                    AOD_NOTIFICATION_BACKGROUND.deferNotificationIcon(chain.getThisObject(),(Canvas)chain.getArg(0))
                            ||C17_HIGHLIGHTS.deferNotificationIcon(chain.getThisObject(),(Canvas)chain.getArg(0))?null:chain.proceed());
            Method changed=extension.getDeclaredMethod("onHeadsUpStateChanged",boolean.class);deoptimize(changed);
            installHook(HookGroup.C17_HIGHLIGHTS,changed,chain -> {
                Object result=chain.proceed(); C17_HIGHLIGHTS.notificationStateChanged(chain.getThisObject()); return result;
            });
            Class<?> background=loader.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView");
            for(Method method:background.getDeclaredMethods())if(method.getName().equals("onDraw"))deoptimize(method);
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Optional native notification color branches unavailable",unavailable);
        }
        try {
            Class<?> edge=loader.loadClass("com.oplus.systemui.notification.material.HeadsUpShadowEdgeController");
            Method draw=edge.getDeclaredMethod("draw",Canvas.class,View.class,android.graphics.Rect.class);deoptimize(draw);
            installHook(HookGroup.C17_HIGHLIGHTS,draw,chain -> C17_HIGHLIGHTS.skipHeadsUpEdge((View)chain.getArg(1))?null:chain.proceed());
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Optional heads-up highlight edge unavailable",unavailable);
        }
        for (String name : new String[]{"com.oplus.systemui.notification.material.NotificationSpotLightDelegate$Companion",
                "com.oplus.systemui.qs.base.spotlight.SharedSpotLightEffect", "com.coui.appcompat.spotlight.COUISpotLightEffectDrawable",
                "com.coui.appcompat.seekbar.effect.SpotLightEffectRenderer"}) try {
            Class<?> type = loader.loadClass(name);
            for (Method draw : type.getDeclaredMethods()) {
                Class<?>[] params = draw.getParameterTypes();
                boolean host = params.length >= 2 && params[0] == View.class && params[1] == Canvas.class;
                boolean scoped = params.length >= 1 && params[0] == Canvas.class &&
                        (name.endsWith("COUISpotLightEffectDrawable") || name.endsWith("SpotLightEffectRenderer"));
                if (!draw.getName().matches("draw|drawEffect|drawSpotLight") || !host && !scoped) continue;
                deoptimize(draw);
                installHook(HookGroup.C17_HIGHLIGHTS, draw, chain -> C17_HIGHLIGHTS.skipSpotlight(host ? (View) chain.getArg(0) : null)
                        ? null : chain.proceed());
            }
        } catch (ClassNotFoundException unavailable) { }
        for (String caller : new String[]{"com.oplus.systemui.statusbar.notification.row.OplusActivatableNotificationViewExImpl",
                "com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow",
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
                "com.oplus.systemui.qs.base.tile.OplusQSTileBaseView$SpotLightIconFrame",
                "com.oplus.systemui.qs.base.spotlight.SpotLightFrameLayout",
                "com.oplus.systemui.qs.base.spotlight.SpotLightImageButton",
                "com.oplus.systemui.qs.base.spotlight.SpotLightLinearLayout",
                "com.oplus.systemui.qs.base.spotlight.SpotLightConstraintLayout",
                "com.oplus.systemui.qs.OplusQsFooterSettingsSpotLightImageView",
                "com.oplus.systemui.qs.media.OplusQsMediaButton",
                "com.oplus.systemui.qs.media.OplusQsMediaOutputSpotlightLayout",
                "com.oplus.systemui.notification.clearall.OplusClearAllButton",
                "com.oplus.systemui.qs.widget.OplusQSMultiUserSwitch",
                "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView",
                "com.oplus.systemui.plugins.qs.customize.view.viewholder.EditableBrightnessVolumeViewHolder$bvLayoutContainer$2$1",
                "com.oplus.systemui.qs.media.QsMediaSpotLightHelper",
                QsPanelCorners.EDITABLE_HOLDER_CLASS + "$realPluginContainer$2$1"}) try {
            for (Method method : loader.loadClass(caller).getDeclaredMethods())
                if (method.getName().matches("drawNotificationSpotLightIfInstalled|dispatchDraw|drawSpotLightEffect|onDraw")) deoptimize(method);
        } catch (ClassNotFoundException unavailable) { }
        try {
            Class<?> light=loader.loadClass("com.oplus.systemui.qs.media.multilight.MultiLightDrawable");
            Method drawLight=light.getDeclaredMethod("draw",Canvas.class);deoptimize(drawLight);
            installHook(HookGroup.C17_HIGHLIGHTS,drawLight,chain ->
                    C17_HIGHLIGHTS.skipNativeMediaLight((Drawable)chain.getThisObject())?null:chain.proceed());
            Class<?> mediaBackground=loader.loadClass("com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable");
            for(Method method:mediaBackground.getDeclaredMethods())if(method.getName().equals("draw"))deoptimize(method);
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Optional native media light layer unavailable",unavailable);
        }
        try {
            Class<?> helper=loader.loadClass("com.oplus.systemui.qs.media.QsMediaSpotLightHelper");
            Method drawSpot=helper.getDeclaredMethod("drawSpotLightEffect",Canvas.class);deoptimize(drawSpot);
            installHook(HookGroup.C17_HIGHLIGHTS,drawSpot,chain ->
                    C17_HIGHLIGHTS.skipNativeMediaSpotlight(chain.getThisObject())?null:chain.proceed());
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Optional native media spotlight layer unavailable",unavailable);
        }
        try {
            Class<?> slider=loader.loadClass("com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar");
            Method stroke=slider.getDeclaredMethod("getStrokePaint");deoptimize(stroke);
            installHook(HookGroup.C17_HIGHLIGHTS,stroke,chain->{
                Object result=chain.proceed();
                return result instanceof Paint ? C17_HIGHLIGHTS.sliderStroke(
                        (View)chain.getThisObject(),(Paint)result) : result;
            });
            for(Method caller:slider.getDeclaredMethods())if(caller.getName().equals("onDraw"))deoptimize(caller);
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO,TAG,"Optional native QS slider highlight contract unavailable",unavailable);
        }
    }

    /** Owned wallpaper regions under the three audited keyguard/AOD root renderers. */
    private boolean installLockscreenBlur(ClassLoader loader) {
        boolean found = false;
        try {
            // This native controller observes lock wallpaper windows even when
            // the Engine lives in the external colorfulengine process.
            Class<?> controller = loader.loadClass(WallpaperSurfaceBlur.CONTROLLER);
            for (Constructor<?> constructor : controller.getDeclaredConstructors()) installHook(HookGroup.LOCKSCREEN_BLUR, constructor, chain -> {
                Object result = chain.proceed();
                LOCKSCREEN_BLUR.onNativeWallpaperController(chain.getThisObject());
                return result;
            });
            for (Method event : controller.getDeclaredMethods()) {
                if (Modifier.isStatic(event.getModifiers()) || Modifier.isAbstract(event.getModifiers())
                        || !event.getName().matches("onVisibilityChanged|onWallpaperSurfaceChanged|onPictorialWallpaperSurfaceChanged"
                            + "|onSystemWallpaperChange|checkIsWallpaperMatrixChanged")) continue;
                deoptimize(event);
                installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                    Object result = chain.proceed();
                    LOCKSCREEN_BLUR.onNativeWallpaperController(chain.getThisObject());
                    return result;
                });
            }
            // Native WindowInfo is published by these two suspend callbacks.
            // Never deoptimize/hook every StateFlow.setValue in SystemUI for it.
            for(String name:WallpaperSurfaceBlur.WINDOW_INFO_TASKS)try {
                Class<?> callback=loader.loadClass(name);
                Method update=callback.getDeclaredMethod("invokeSuspend",Object.class);
                deoptimize(update);
                installHook(HookGroup.LOCKSCREEN_BLUR,update,chain->{
                    Object result=chain.proceed();
                    LOCKSCREEN_BLUR.onNativeWallpaperTask(chain.getThisObject());return result;
                });
            }catch(ReflectiveOperationException unavailable){
                moduleLog(Log.INFO,TAG,"Optional native wallpaper callback unavailable; controller events retained");
            }
            found = true;
        } catch (ReflectiveOperationException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional external wallpaper-window bridge unavailable; native wallpaper retained");
        }
        try {
            // Observe the real wallpaper owner, not just callbacks overridden by CanvasEngine.
            Class<?> engine = loader.loadClass(WallpaperBlurLayer.ENGINE);
            for (Method event : engine.getDeclaredMethods()) {
                String name = event.getName();
                if (Modifier.isStatic(event.getModifiers()) || Modifier.isAbstract(event.getModifiers())
                        || !name.matches("initializeSurface|updateSurface|reportSurfaceDestroyed|detach|reportVisibility|doVisibilityChanged"
                            + "|doAmbientModeChanged|onScreenTurningOnChanged|showScreenshotOfWallpaper|freeze|cleanUpScreenshotSurfaceControl")) continue;
                deoptimize(event);
                installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                    Object owner = chain.getThisObject();
                    if (name.equals("reportSurfaceDestroyed") || name.equals("detach")) {
                        LOCKSCREEN_BLUR.onWallpaperDestroyed(owner);
                        return chain.proceed();
                    }
                    if (name.equals("showScreenshotOfWallpaper")) {
                        LOCKSCREEN_BLUR.beforeWallpaperSnapshot(owner);
                        try { return chain.proceed(); }
                        finally { LOCKSCREEN_BLUR.afterWallpaperSnapshot(owner); }
                    }
                    Object result = chain.proceed();
                    LOCKSCREEN_BLUR.onWallpaperEngine(owner);
                    return result;
                });
                found = true;
            }
        } catch (ClassNotFoundException unavailable) {
            moduleLog(Log.INFO, TAG, "Native wallpaper engine unavailable; foreground blur will not be substituted");
        }
        try {
            Class<?> canvasEngine = loader.loadClass(WallpaperBlurLayer.CANVAS_ENGINE);
            Method frame = canvasEngine.getDeclaredMethod("drawFrameOnCanvas", android.graphics.Bitmap.class);
            deoptimize(frame);
            installHook(HookGroup.LOCKSCREEN_BLUR, frame, chain -> {
                Object result = chain.proceed();
                LOCKSCREEN_BLUR.onWallpaperFrame(chain.getThisObject());
                return result;
            });
            found = true;
        } catch (ReflectiveOperationException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional native wallpaper frame refresh unavailable");
        }
        for (String name : new String[]{LockscreenStatusBlur.SHADE_ROOT, LockscreenStatusBlur.KEYGUARD_ROOT,
                LockscreenStatusBlur.AOD_ROOT}) try {
            Class<?> root = loader.loadClass(name);
            Method attach=nativeViewMethod(root,"onAttachedToWindow");deoptimize(attach);
            installHook(HookGroup.LOCKSCREEN_BLUR,attach,chain->{
                Object result=chain.proceed();LOCKSCREEN_BLUR.onHostAttached((View)chain.getThisObject());return result;
            });
            found = true;
        } catch (ReflectiveOperationException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional keyguard wallpaper blur root unavailable: " + name);
        }
        for (String name : new String[]{LockscreenStatusBlur.STATE_CONTROLLER, LockscreenStatusBlur.KEYGUARD_CONTROLLER}) try {
            Class<?> type = loader.loadClass(name);
            boolean state = name.equals(LockscreenStatusBlur.STATE_CONTROLLER);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) installHook(HookGroup.LOCKSCREEN_BLUR, constructor, chain -> {
                Object result = chain.proceed();
                if (state) { LOCKSCREEN_BLUR.onNativeState(chain.getThisObject()); FLUID_STYLE.onNativeState(chain.getThisObject()); AOD_NOTIFICATION_BACKGROUND.onNativeState(chain.getThisObject()); }
                else { LOCKSCREEN_BLUR.onNativeKeyguard(chain.getThisObject()); FLUID_STYLE.onNativeKeyguard(chain.getThisObject()); AOD_NOTIFICATION_BACKGROUND.onNativeKeyguard(chain.getThisObject()); }
                return result;
            });
            for (Method event : type.getDeclaredMethods()) {
                if (Modifier.isStatic(event.getModifiers()) || Modifier.isAbstract(event.getModifiers())) continue;
                if (!(state ? event.getName().matches("start|setState|setIsDozing|setDozeAmountInternal|setAndInstrumentDozeAmount")
                        : event.getName().matches("notifyKeyguardState|notifyKeyguardDismissAmountChanged|notifyKeyguardDoneFading"
                            + "|notifyKeyguardFadingAway|notifyKeyguardGoingAway|update"))) continue;
                deoptimize(event);
                installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                    Object result = chain.proceed();
                    if (state) { LOCKSCREEN_BLUR.onNativeState(chain.getThisObject()); FLUID_STYLE.onNativeState(chain.getThisObject()); AOD_NOTIFICATION_BACKGROUND.onNativeState(chain.getThisObject()); }
                    else { LOCKSCREEN_BLUR.onNativeKeyguard(chain.getThisObject()); FLUID_STYLE.onNativeKeyguard(chain.getThisObject()); AOD_NOTIFICATION_BACKGROUND.onNativeKeyguard(chain.getThisObject()); }
                    return result;
                });
            }
        } catch (ClassNotFoundException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional keyguard wallpaper state source unavailable: " + name);
        }
        try {
            Class<?> controller = loader.loadClass(LockscreenStatusBlur.SCRIM_CONTROLLER);
            for (Constructor<?> constructor : controller.getDeclaredConstructors()) installHook(HookGroup.LOCKSCREEN_BLUR, constructor, chain -> {
                Object result = chain.proceed();
                LOCKSCREEN_BLUR.onNativeScrimController(chain.getThisObject());
                return result;
            });
            for (Method event : controller.getDeclaredMethods()) {
                if (Modifier.isStatic(event.getModifiers()) || Modifier.isAbstract(event.getModifiers())
                        || !event.getName().matches("updateScrimColor|updateScrims|applyAndDispatchState")) continue;
                deoptimize(event);
                installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                    Object result = chain.proceed();
                    LOCKSCREEN_BLUR.onNativeScrimController(chain.getThisObject());
                    return result;
                });
            }
        } catch (ClassNotFoundException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional native wallpaper scrim controller unavailable");
        }
        try {
            Class<?> scrim = loader.loadClass(LockscreenStatusBlur.SCRIM_VIEW);
            for (Method event : scrim.getDeclaredMethods()) {
                if (Modifier.isStatic(event.getModifiers()) || Modifier.isAbstract(event.getModifiers())
                        || !event.getName().matches("setViewAlpha|setTint|updateColorWithTint|invalidateDrawable")) continue;
                deoptimize(event);
                installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                    Object result = chain.proceed();
                    LOCKSCREEN_BLUR.onNativeScrimView((View) chain.getThisObject());
                    return result;
                });
            }
        } catch (ClassNotFoundException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional native wallpaper scrim view unavailable");
        }
        for (Method event : View.class.getDeclaredMethods()) {
            String name = event.getName();
            if (!name.matches("onAttachedToWindow|onDetachedFromWindow|onVisibilityChanged|onWindowVisibilityChanged"
                    + "|setVisibility|setAlpha|setTransitionAlpha") || Modifier.isStatic(event.getModifiers())) continue;
            deoptimize(event);
            installHook(HookGroup.LOCKSCREEN_BLUR, event, chain -> {
                Object result = chain.proceed(); View view = (View) chain.getThisObject();
                if (name.equals("onAttachedToWindow")) LOCKSCREEN_BLUR.onHostAttached(view);
                else if (name.equals("onDetachedFromWindow")) LOCKSCREEN_BLUR.onHostDetached(view);
                else LOCKSCREEN_BLUR.onHostVisibilityChanged(view);
                return result;
            });
        }
        return found;
    }
    private static Method nativeViewMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            return current.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException inherited) { }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    /** Both true plugin loader paths run before Class.forName/instanceFactory/onCreate. */
    private boolean installFluidAccentLoaders(ClassLoader loader) {
        boolean found = installFluidAccent(loader);
        try {
            Class<?> factory = loader.loadClass("com.android.systemui.shared.plugins.PluginInstance$PluginFactory");
            Field info = factory.getDeclaredField("pluginAppInfo"); info.setAccessible(true);
            Method create = factory.getDeclaredMethod("createClassLoader"); deoptimize(create);
            installHook(HookGroup.FLUID_ACCENT, create, chain -> {
                Object result = chain.proceed(); Object app = info.get(chain.getThisObject());
                if (result instanceof ClassLoader && app instanceof android.content.pm.ApplicationInfo
                        && "com.oplus.systemui.plugins".equals(((android.content.pm.ApplicationInfo) app).packageName))
                    installFluidAccent((ClassLoader) result);
                return result;
            });
            // ART may inline the private loader accessor into this exact native caller.
            try { deoptimize(factory.getDeclaredMethod("createPlugin",
                    loader.loadClass("com.android.systemui.plugins.ProtectedPluginListener"))); }
            catch (ReflectiveOperationException optionalCaller) {
                moduleLog(Log.INFO, TAG, "Optional Live Alert loader caller unavailable");
            }
            found = true;
        } catch (ReflectiveOperationException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional initial Live Alert plugin loader unavailable");
        }
        try {
            Class<?> factory = loader.loadClass("com.android.systemui.shared.plugins.PluginInstance$Factory");
            Method cached = factory.getDeclaredMethod("access$getCacheClassLoader", factory, String.class); deoptimize(cached);
            installHook(HookGroup.FLUID_ACCENT, cached, chain -> {
                Object result = chain.proceed();
                if (result instanceof ClassLoader && "com.oplus.systemui.plugins".equals(chain.getArg(1)))
                    installFluidAccent((ClassLoader) result);
                return result;
            });
            try { deoptimize(loader.loadClass("com.android.systemui.shared.plugins.PluginInstance$Factory$create$4")
                    .getDeclaredMethod("invoke", Object.class)); }
            catch (ReflectiveOperationException optionalCaller) {
                moduleLog(Log.INFO, TAG, "Optional cached Live Alert loader caller unavailable");
            }
            found = true;
        } catch (ReflectiveOperationException unavailable) {
            moduleLog(Log.INFO, TAG, "Optional cached Live Alert plugin loader unavailable");
        }
        return found;
    }
    private boolean installFluidAccent(ClassLoader loader) {
        CUSTOM_PLUGINS.rememberLoader(loader);
        synchronized (SYSTEMUI_HOOK_LOCK) {
            // Both initial and cached QS plugin loaders must bind their own display entries.
            if (installWifiAlias(loader)) COMPLETED_HOOK_GROUPS.add(HookGroup.WIFI_ALIAS);
            installCapsuleMediaPersistence(loader);
            if (FLUID_LOADERS.contains(loader)) return true;
            installCapsulePersistence(loader);
            installFluidCapsule(loader);
            boolean mediaInstalled = installFluidMediaStyle(loader);
            try {
                FluidCloudAccent.Hooks accentAccess = null;
                FluidCloudStyle.Hooks styleAccess = null;
                try { accentAccess = FLUID_ACCENT.resolve(loader); }
                catch (ReflectiveOperationException | LinkageError unsupportedAccent) {
                    moduleLog(Log.INFO, TAG, "Optional native Live Alert accent contract unavailable");
                }
                try { styleAccess = FLUID_STYLE.resolve(loader); }
                catch (ReflectiveOperationException | LinkageError unsupportedStyle) {
                    moduleLog(Log.INFO, TAG, "Optional native Live Alert desktop material contract unavailable");
                }
                if (accentAccess == null && styleAccess == null) return mediaInstalled;
                Method background = accentAccess != null ? accentAccess.background : styleAccess.background;
                Method blurAlpha = accentAccess != null ? accentAccess.blurAlpha : styleAccess.blurAlpha;
                installHook(HookGroup.FLUID_ACCENT, background, chain -> {
                    try (FluidCloudAccent.BackgroundScope accentScope = FLUID_ACCENT.beginBackground(chain.getThisObject(),
                            (View) chain.getArg(0), chain.getArg(1));
                            FluidCloudStyle.BackgroundScope styleScope = FLUID_STYLE.beginBackground(chain.getThisObject(),
                                    (View) chain.getArg(0), chain.getArg(1))) {
                        Object result = chain.proceed();
                        if (accentScope != null) accentScope.completed();
                        // Native media recolours the static fallback first; the
                        // user's appearance fill/opacity is the final policy.
                        if (styleScope != null) styleScope.completed();
                        return result;
                    }
                });
                if (accentAccess != null) {
                    installHook(HookGroup.FLUID_ACCENT, accentAccess.mediaPrimaryEvent, chain -> {
                        Object result = chain.proceed(); FLUID_ACCENT.mediaChanged(chain.getArg(0));
                        FLUID_STYLE.refreshAfterAccent(); return result;
                });
                    installHook(HookGroup.FLUID_ACCENT, accentAccess.mediaConstructor, chain -> {
                        Object result = chain.proceed(); FLUID_ACCENT.mediaChanged(chain.getThisObject());
                        FLUID_STYLE.refreshAfterAccent(); return result;
                });
                    installHook(HookGroup.FLUID_ACCENT, accentAccess.mediaDispose, chain -> {
                        FLUID_ACCENT.mediaDisposed(chain.getThisObject()); FLUID_STYLE.refreshAfterAccent(); return chain.proceed();
                });
                    for (Method method : new Method[]{accentAccess.mediaPrimaryEvent, accentAccess.mediaDispose,
                            accentAccess.cardBind}) deoptimize(method);
                }
                Method cardBind=accentAccess!=null?accentAccess.cardBind:styleAccess.cardBind;
                installHook(HookGroup.FLUID_ACCENT,cardBind,chain->{
                    View card=(View)chain.getThisObject();
                    FLUID_STYLE.cardBound(card,chain.getArg(0));
                    try{return chain.proceed();}
                    finally{
                        FLUID_ACCENT.cardChanged(card);FLUID_STYLE.cardContentChanged(card);
                        FLUID_STYLE.refreshAfterAccent();
                    }
                });
                deoptimize(cardBind);
                installHook(HookGroup.FLUID_ACCENT, blurAlpha, chain -> {
                    FLUID_ACCENT.nativeBlurAlpha((View) chain.getThisObject(), (Float) chain.getArg(0));
                    FLUID_STYLE.nativeBlurAlpha((View) chain.getThisObject(), (Float) chain.getArg(0)); return chain.proceed();
                });
                deoptimize(background); deoptimize(blurAlpha);
                // Compiled native initialization must enter the scoped hook.
                // These are the two audited CardBackgroundView callers of b.
                Class<?> backgroundType = accentAccess != null ? accentAccess.backgroundType : styleAccess.backgroundType;
                for (Method caller : backgroundType.getDeclaredMethods()) {
                    if (caller.getName().equals("onAttachedToWindow") && caller.getParameterCount() == 0
                            || caller.getName().equals("onConfigurationChanged")
                            && java.util.Arrays.equals(caller.getParameterTypes(),
                                    new Class<?>[]{android.content.res.Configuration.class})) deoptimize(caller);
                }
                if (styleAccess != null) {
                    try {
                        Method bounds = FluidCloudAppearance.nativeBoundsMethod(loader);
                        Method draw = FluidCloudAppearance.nativeDrawMethod(loader);
                        deoptimize(bounds);
                        installHook(HookGroup.FLUID_ACCENT, bounds, chain -> {
                            Object result = chain.proceed();
                            FluidCloudAppearance.nativeBoundsChanged((Drawable) chain.getThisObject());
                            return result;
                        });
                        deoptimize(draw);
                        // The audited foreground is drawn by its native
                        // LayerDrawable. Keep this virtual call out of an old
                        // compiled body so it reaches the exact draw guard.
                        deoptimize(android.graphics.drawable.LayerDrawable.class.getDeclaredMethod("draw", Canvas.class));
                        installHook(HookGroup.FLUID_ACCENT, draw, chain -> {
                            FluidCloudAppearance.nativeDrawStarting((Drawable) chain.getThisObject());
                            Object result = chain.proceed();
                            FluidCloudAppearance.nativeDrawCompleted((Drawable) chain.getThisObject());
                            return result;
                        });
                    } catch (ReflectiveOperationException | LinkageError unsupportedGradient) {
                        moduleLog(Log.INFO, TAG, "Optional native expanded-card gradient unavailable");
                    }
                    deoptimize(styleAccess.setBlurParams);
                    installHook(HookGroup.FLUID_ACCENT, styleAccess.setBlurParams, chain -> {
                        Object nativeParam = chain.getArg(0);
                        Object styled = FLUID_STYLE.blurParams(chain.getThisObject(), nativeParam);
                        return styled == nativeParam ? chain.proceed() : chain.proceed(new Object[]{styled});
                    });
                }
                for (Method color : Resources.class.getDeclaredMethods()) if (color.getName().equals("getColor")
                        && color.getReturnType() == Integer.TYPE && color.getParameterCount() > 0
                        && color.getParameterTypes()[0] == Integer.TYPE) {
                    deoptimize(color);
                    installHook(HookGroup.FLUID_ACCENT, color, chain -> {
                        Resources resources = (Resources) chain.getThisObject(); int id = (Integer) chain.getArg(0);
                        int result = FLUID_ACCENT.resourceColor(resources, id, (Integer) chain.proceed());
                        return FLUID_STYLE.resourceColor(resources, id, result);
                    });
                }
                Method detach = View.class.getDeclaredMethod("onDetachedFromWindow");
                installHook(HookGroup.FLUID_ACCENT, detach, chain -> {
                    Object result = chain.proceed(); detachFluidBackground((View) chain.getThisObject()); return result;
                });
                Method attach = View.class.getDeclaredMethod("onAttachedToWindow");
                installHook(HookGroup.FLUID_ACCENT, attach, chain -> {
                    Object result = chain.proceed(); FLUID_ACCENT.cardChanged((View) chain.getThisObject()); return result;
                });
                FLUID_LOADERS.add(loader);
                moduleLog(Log.INFO, TAG, "Native expanded Live Alert accent/material hooks installed: accent="
                        + (accentAccess != null) + ", desktop=" + (styleAccess != null));
                Log.i(TAG, "Live Alert accent/material hooks installed: accent=" + (accentAccess != null) + ", desktop=" + (styleAccess != null));
                return true;
            } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
        }
    }

    /** Intercept only the audited automatic capsule-to-bubble callback; native slide and removal stay intact. */
    private void installCapsulePersistence(ClassLoader loader) {
        try {
            CapsulePersistence.Hooks access = CAPSULE_PERSISTENCE.resolve(loader);
            if (access == null) return;
            for (Method caller : access.inlineCallers) deoptimize(caller);
            deoptimize(access.timeout);
            installHook(HookGroup.FLUID_ACCENT, access.timeout, chain ->
                    CAPSULE_PERSISTENCE.beforeTimeout(access, chain.getThisObject(), chain.getArg(0))
                            ? access.completed : chain.proceed());
            moduleLog(Log.INFO, TAG, "Camera-area capsule persistence timer hook installed");
        } catch (LinkageError | RuntimeException unavailable) {
            moduleLog(Log.WARN, TAG, "Optional capsule persistence timer unavailable: " + unavailable);
        }
    }

    /** Music's source has its own status-bar timeout, separate from plugin capsule shrinking. */
    private void installCapsuleMediaPersistence(ClassLoader loader) {
        try {
            CapsuleMediaPersistence.Hooks access = CAPSULE_MEDIA_PERSISTENCE.resolve(loader);
            if (access == null) return;
            Set<Executable> installed = INSTALLED_HOOKS.get(HookGroup.FLUID_ACCENT);
            if (installed != null && installed.contains(access.timeout)) return;
            for (Method caller : access.inlineCallers) deoptimize(caller);
            deoptimize(access.timeout);
            installHook(HookGroup.FLUID_ACCENT, access.timeout, chain ->
                    CAPSULE_MEDIA_PERSISTENCE.beforeTimeout(access, chain.getThisObject()) ? null : chain.proceed());
            moduleLog(Log.INFO, TAG, "Camera-area media persistence timer hook installed");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            moduleLog(Log.WARN, TAG, "Optional capsule media persistence timer unavailable: " + unavailable);
        }
    }

    /** The camera-area capsule of the same plugin: its pill and outline follow the capsule settings. A capsule that is not
     * the audited one is left alone. Every hook runs after the native code, so the plugin's own state is never changed. */
    private void installFluidCapsule(ClassLoader loader) {
        try {
            FluidCloudCapsule.Hooks access = FLUID_CAPSULE.resolve(loader);
            if (access == null) return;
            installCapsuleTextFade(loader);
            for (Method method : new Method[]{access.onFinishInflate, access.onAttachedToWindow, access.setHidden, access.progress}) {
                deoptimize(method);
                installHook(HookGroup.FLUID_ACCENT, method, chain -> {
                    Object result = chain.proceed();
                    FLUID_CAPSULE.changed((View) chain.getThisObject(), method == access.progress ? (Float) chain.getArg(0) : Float.NaN);
                    if (method == access.onFinishInflate) CAPSULE_EFFECTS.capsuleSeen((View) chain.getThisObject());
                    return result;
                });
            }
            // The album cover: the plugin sets its circle again when it binds a new track; while our radius is wanted it stays.
            try {
                Class<?> lottie = loader.loadClass(CapsuleSpectrum.VIEW);
                Method getShape = lottie.getMethod("getCurrentShape");
                Method setShape = lottie.getMethod("setShape", getShape.getReturnType());
                Method setRadius = lottie.getMethod("setCornerRadius", Float.TYPE);
                deoptimize(setShape); deoptimize(setRadius);
                installHook(HookGroup.FLUID_ACCENT, setShape, chain -> {
                    Object requested = chain.getArg(0);
                    Object kept = FLUID_CAPSULE.coverShape((View) chain.getThisObject(), requested);
                    return kept == requested ? chain.proceed() : chain.proceed(new Object[]{kept});
                });
                installHook(HookGroup.FLUID_ACCENT, setRadius, chain -> {
                    float requested = (Float) chain.getArg(0);
                    float kept = FLUID_CAPSULE.coverRadius((View) chain.getThisObject(), requested);
                    return kept == requested ? chain.proceed() : chain.proceed(new Object[]{kept});
                });
            } catch (ReflectiveOperationException unavailable) {
                Log.w(TAG, "Capsule cover setters unavailable: " + unavailable);
            }
            // The appear bounce: the plugin reads its two overshoot sizes here each time it builds the appear animation. Every resource
            // read of SystemUI passes this method, so it is hooked only once a bounce other than the system's own is asked for (code102).
            lazyHook("capsule_bounce", CAPSULE_EFFECTS::active, () -> {
                Method offset = Resources.class.getDeclaredMethod("getDimensionPixelOffset", Integer.TYPE);
                installHook(HookGroup.FLUID_ACCENT, offset, chain -> {
                    int nativeValue = (Integer) chain.proceed();
                    if (!CAPSULE_EFFECTS.active()) return nativeValue;
                    return CAPSULE_EFFECTS.pixelOffset((Resources) chain.getThisObject(), (Integer) chain.getArg(0), nativeValue);
                });
            });
            deoptimize(access.draw);
            installHook(HookGroup.FLUID_ACCENT, access.draw, chain -> {
                FLUID_CAPSULE.beforeDraw((View) chain.getThisObject());
                return chain.proceed();
            });
            moduleLog(Log.INFO, TAG, "Camera-area capsule hooks installed");
            Log.i(TAG, "Camera-area capsule hooks installed");
        } catch (RuntimeException | LinkageError | XposedFrameworkError unavailable) {
            // A hook the framework refuses (an Error, not an exception) must not stop installFluidAccent, which installs the core
            // media and card hooks right after this optional one.
            moduleLog(Log.WARN, TAG, "Optional camera-area capsule hooks unavailable: " + unavailable);
            Log.w(TAG, "Optional camera-area capsule hooks unavailable", unavailable);
        }
        installCapsuleSpectrum(loader);
    }

    /** Only the audited plugin's text methods: retain its overflow logic and replace the black fade after native updates. */
    private void installCapsuleTextFade(ClassLoader loader) {
        try {
            CapsuleTextFade.Hooks access = FLUID_CAPSULE.resolveTextFade(loader);
            if (access == null) return;
            for (Method method : access.updates) {
                deoptimize(method);
                installHook(HookGroup.FLUID_ACCENT, method, chain -> {
                    Object result = chain.proceed();
                    FLUID_CAPSULE.textChanged((View) chain.getThisObject());
                    return result;
                });
            }
            deoptimize(access.detached);
            installHook(HookGroup.FLUID_ACCENT, access.detached, chain -> {
                FLUID_CAPSULE.textDetached((View) chain.getThisObject());
                return chain.proceed();
            });
        } catch (RuntimeException | LinkageError | XposedFrameworkError unavailable) {
            Log.w(TAG, "Optional capsule text fade hooks unavailable", unavailable);
        }
    }

    /** The plugin's canned six-bar spectrum (a Lottie view) shows bars driven by the real audio while the setting is on.
     * Both hooks run after the native code and only look at the view; the plugin's own animation comes back when it is off. */
    private void installCapsuleSpectrum(ClassLoader loader) {
        try {
            CapsuleSpectrum.Hooks access = CAPSULE_SPECTRUM.resolve(loader);
            if (access == null) return;
            deoptimize(access.setAnimation); deoptimize(access.setImageDrawable);
            installHook(HookGroup.FLUID_ACCENT, access.setAnimation, chain -> {
                Object result = chain.proceed();
                CAPSULE_SPECTRUM.animationSet((View) chain.getThisObject(), (Integer) chain.getArg(0));
                return result;
            });
            installHook(HookGroup.FLUID_ACCENT, access.setImageDrawable, chain -> {
                Object result = chain.proceed();
                CAPSULE_SPECTRUM.drawableSet((View) chain.getThisObject(), (android.graphics.drawable.Drawable) chain.getArg(0));
                return result;
            });
            // The plugin reads the raw animation itself and hands the view JSON text: that is how the spectrum is recognised.
            for (Method setter : access.json) {
                deoptimize(setter);
                installHook(HookGroup.FLUID_ACCENT, setter, chain -> {
                    Object result = chain.proceed();
                    Object text = chain.getArg(0);
                    if (text instanceof String) CAPSULE_SPECTRUM.animationJson((View) chain.getThisObject(), (String) text);
                    return result;
                });
            }
            moduleLog(Log.INFO, TAG, "Spectrum hooks installed");
            Log.i(TAG, "Spectrum hooks installed");
        } catch (RuntimeException | LinkageError | XposedFrameworkError unavailable) {
            moduleLog(Log.WARN, TAG, "Optional spectrum hooks unavailable: " + unavailable);
            Log.w(TAG, "Optional spectrum hooks unavailable", unavailable);
        }
    }

    /** The lock-screen/AOD capsule: one extra tint drawn after the native background. Not the audited class: stays native. */
    private void installLockCapsule(ClassLoader loader) {
        try {
            LockCapsuleTint.Hooks access = LOCK_CAPSULE.resolve(loader);
            if (access == null) return;
            deoptimize(access.draw);
            installHook(HookGroup.FLUID_NOTIFICATION, access.draw, chain -> {
                Object result = chain.proceed();
                LOCK_CAPSULE.drawn(chain.getThisObject(), (View) chain.getArg(0), (Canvas) chain.getArg(1));
                return result;
            });
            moduleLog(Log.INFO, TAG, "Lock-screen capsule tint hooks installed");
        } catch (RuntimeException | LinkageError | XposedFrameworkError unavailable) {
            moduleLog(Log.WARN, TAG, "Optional lock-screen capsule hooks unavailable: " + unavailable);
            Log.w(TAG, "Optional lock-screen capsule hooks unavailable", unavailable);
        }
    }

    private boolean installFluidNotificationStyle(ClassLoader loader) {
        synchronized (SYSTEMUI_HOOK_LOCK) {
            if (FLUID_NOTIFICATION_LOADERS.contains(loader)) return true;
            try {
                FluidCloudStyle.NotificationHooks access = FLUID_STYLE.resolveNotification(loader);
                if (access == null) return false;
                installFluidSurfaceRoots();
                installFluidSceneMaterial(loader);
                installLockCapsule(loader);
                // These real bind events cover direct native field writes as well as setters.
                // The getter is read only once per event, never intercepted every frame.
                for (Method method : new Method[]{access.backgroundSetter, access.inflate, access.attach,
                        access.updateResource, access.setEntry, access.setSeedlingView}) {
                    deoptimize(method);
                    installHook(HookGroup.FLUID_NOTIFICATION, method, chain -> {
                        Object result = chain.proceed();
                        if(method==access.inflate||method==access.updateResource||method==access.setSeedlingView)
                            FLUID_STYLE.notificationContentChanged(chain.getThisObject());
                        else FLUID_STYLE.notificationRow(chain.getThisObject());
                        try {
                            Object body=access.backgroundGetter.invoke(chain.getThisObject());
                            if(body instanceof View)AOD_NOTIFICATION_BACKGROUND.observeBody((View)body);
                        }catch(ReflectiveOperationException|RuntimeException unavailable){ }
                        return result;
                    });
                }
                Method detach = View.class.getDeclaredMethod("onDetachedFromWindow");
                installHook(HookGroup.FLUID_NOTIFICATION, detach, chain -> {
                    Object result = chain.proceed(); detachFluidBackground((View) chain.getThisObject()); return result;
                });
                FLUID_NOTIFICATION_LOADERS.add(loader);
                moduleLog(Log.INFO, TAG, "Native Live Alert notification body hooks installed");
                return true;
            } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
        }
    }

    /** Observe native scene inputs even while style is OFF. No scene uses another
     * card's drawable, observers or BlurProxy. Unknown contracts keep OEM inputs. */
    private void installFluidSceneMaterial(ClassLoader loader) {
        try {
            FluidCloudSceneMaterial.Hooks access=FLUID_STYLE.resolveScene(loader);
            deoptimize(access.motionEvent);installHook(HookGroup.FLUID_NOTIFICATION,access.motionEvent,chain->{
                Object result=chain.proceed();FLUID_STYLE.sceneMotionChanged(chain.getThisObject(),chain.getArg(0));return result;
            });
            if(access.aodMaskSetter!=null) {
                deoptimize(access.aodMaskSetter);installHook(HookGroup.FLUID_NOTIFICATION,access.aodMaskSetter,chain->{
                    Drawable original=(Drawable)chain.getArg(0);
                    Drawable styled=FLUID_STYLE.sceneAodMask(chain.getThisObject(),original);
                    return styled==original?chain.proceed():chain.proceed(new Object[]{styled});
                });
            }
            if(access.aodSetter!=null) {
                deoptimize(access.aodSetter);installHook(HookGroup.FLUID_NOTIFICATION,access.aodSetter,chain->{
                    Object repository=chain.getThisObject();float progress=(Float)chain.getArg(0);boolean completed=false;
                    // Native setter writes progress then synchronously notifies
                    // listeners. Arm continuity before those callbacks can submit
                    // the raw CUSTOM recipe; refresh only after native completion.
                    FLUID_STYLE.prepareSceneAodProgress(repository,progress);
                    try{Object result=chain.proceed();completed=true;return result;}
                    finally{FLUID_STYLE.sceneAodProgressChanged(repository,completed?progress:Float.NaN);}
                });
            }
            deoptimize(access.blur);
            installHook(HookGroup.FLUID_NOTIFICATION,access.blur,chain->{
                Object nativeMode=chain.getArg(0);
                Object mode=FLUID_STYLE.sceneBlurMode(chain.getThisObject(),nativeMode);
                Object result=chain.proceed(new Object[]{mode});
                FLUID_STYLE.sceneMaterialChanged(chain.getThisObject());AOD_NOTIFICATION_BACKGROUND.observeExtension(chain.getThisObject());return result;
            });
            for(Method method:new Method[]{access.palette,access.materialEvent,access.headsup}) {
                deoptimize(method);installHook(HookGroup.FLUID_NOTIFICATION,method,chain->{
                    Object result=chain.proceed();
                    if(method==access.materialEvent||method==access.headsup)FLUID_STYLE.scenePaletteChanged(chain.getThisObject());
                    else FLUID_STYLE.sceneMaterialChanged(chain.getThisObject());
                    return result;
                });
            }
            deoptimize(access.metaUpdate);
            installHook(HookGroup.FLUID_NOTIFICATION,access.metaUpdate,chain->{
                Object result=chain.proceed();Object[] args=chain.getArgs().toArray();
                FLUID_STYLE.sceneMetaChanged(chain.getThisObject(),(android.content.Context)args[0],args[1],(Drawable)args[2]);
                return result;
            });
            deoptimize(access.draw);
            installHook(HookGroup.FLUID_NOTIFICATION,access.draw,chain->{
                try(AutoCloseable policy=FLUID_STYLE.beginSceneDraw(chain.getThisObject())){return chain.proceed();}
            });
            for(Method submitted:new Method[]{access.platformApply,access.motionApply,access.wallpaperApply}) {
                deoptimize(submitted);
                installHook(HookGroup.FLUID_NOTIFICATION,submitted,chain->{
                    if(FLUID_STYLE.deferScenePlatform(chain.getThisObject()))return null;
                    try(AutoCloseable policy=FLUID_STYLE.beginSceneBackend(chain.getThisObject(),submitted)) {
                        Object result=chain.proceed();FLUID_STYLE.scenePlatformCompleted(policy);return result;
                    }
                });
            }
            deoptimize(access.proxyDrawable);
            installHook(HookGroup.FLUID_NOTIFICATION,access.proxyDrawable,chain->{
                FLUID_STYLE.sceneProxyChanged(chain.getThisObject());return chain.proceed();
            });
            deoptimize(access.needMeta);
            installHook(HookGroup.FLUID_NOTIFICATION,access.needMeta,chain->{
                Object result=chain.proceed();
                return result instanceof Boolean?FLUID_STYLE.sceneNeedsMeta(chain.getArg(1),(Boolean)result):result;
            });
            Method bodyDraw=loader.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView")
                    .getDeclaredMethod("draw",Canvas.class,Drawable.class);
            deoptimize(bodyDraw);
            installHook(HookGroup.FLUID_NOTIFICATION,bodyDraw,chain->{
                boolean previous=FLUID_STYLE.beginSceneFrame();
                try {
                    Drawable original=(Drawable)chain.getArg(1),effective=FLUID_STYLE.sceneDrawable((View)chain.getThisObject(),original);
                    return effective==original?chain.proceed():chain.proceed(new Object[]{chain.getArg(0),effective});
                } finally { FLUID_STYLE.endSceneFrame(previous); }
            });
            Class<?> bodyType=bodyDraw.getDeclaringClass();
            Method height=bodyType.getDeclaredMethod("setActualHeight",Integer.TYPE);deoptimize(height);
            installHook(HookGroup.FLUID_NOTIFICATION,height,chain->{
                Object result=chain.proceed();FLUID_STYLE.sceneGeometryChanged((View)chain.getThisObject());return result;
            });
            Method layout=View.class.getDeclaredMethod("onLayout",Boolean.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE);deoptimize(layout);
            installHook(HookGroup.FLUID_NOTIFICATION,layout,chain->{
                Object result=chain.proceed();if(Boolean.TRUE.equals(chain.getArg(0)))FLUID_STYLE.sceneGeometryChanged((View)chain.getThisObject());return result;
            });
        }catch(ReflectiveOperationException|LinkageError unavailable){
            moduleLog(Log.WARN,TAG,"Native Live Alert scene material unsupported; retaining OEM material",unavailable);
        }
    }

    private boolean installFluidMediaStyle(ClassLoader loader) {
        if (FLUID_MEDIA_LOADERS.contains(loader)) return true;
        try {
            FluidCloudStyle.MediaHooks access = FLUID_STYLE.resolveMedia(loader);
            if (access == null) return false;
            installHook(HookGroup.FLUID_MEDIA, access.constructor, chain -> {
                Object result = chain.proceed(); FLUID_STYLE.mediaChanged(chain.getThisObject()); return result;
            });
            deoptimize(access.primary); deoptimize(access.dispose); deoptimize(access.draw);
            installHook(HookGroup.FLUID_MEDIA, access.primary, chain -> {
                Object result = chain.proceed(); FLUID_STYLE.mediaChanged(chain.getArg(0)); return result;
            });
            installHook(HookGroup.FLUID_MEDIA, access.dispose, chain -> {
                FLUID_STYLE.mediaDisposed(chain.getThisObject()); return chain.proceed();
            });
            installHook(HookGroup.FLUID_MEDIA, access.draw, chain ->
                    FLUID_STYLE.suppressMediaLight((Drawable) chain.getThisObject()) ? null : chain.proceed());
            deoptimize(access.pageDraw);
            installHook(HookGroup.FLUID_MEDIA, access.pageDraw, chain ->
                    FLUID_STYLE.drawMediaPage((Drawable)chain.getThisObject(),(Canvas)chain.getArg(0),chain::proceed));
            FLUID_MEDIA_LOADERS.add(loader);
            moduleLog(Log.INFO, TAG, "Native Live Alert media decoration hooks installed");
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            moduleLog(Log.INFO, TAG, "Native Live Alert media decoration contract unavailable: " + unavailable.getClass().getSimpleName());
            return false;
        }
    }

    /** Associate only native in-process hosted roots; unowned/cross-process
     * SurfacePackages retain their original rendering. Bind events are rare.
     */
    private void installFluidSurfaceRoots() throws ReflectiveOperationException {
        for(Method event:new Method[]{View.class.getDeclaredMethod("onAttachedToWindow"),
                View.class.getDeclaredMethod("onVisibilityChanged",View.class,Integer.TYPE)}){
            deoptimize(event);
            installHook(HookGroup.FLUID_NOTIFICATION,event,chain->{
                Object result=chain.proceed();
                FLUID_STYLE.surfacePresentationChanged((View)chain.getThisObject());return result;
            });
        }
        Method effect=View.class.getDeclaredMethod("setRenderEffect",android.graphics.RenderEffect.class);
        installHook(HookGroup.FLUID_NOTIFICATION,effect,chain->{
            Object result=chain.proceed();NotificationRemoteClockEdge.nativeEffect((View)chain.getThisObject(),(android.graphics.RenderEffect)chain.getArg(0));return result;
        });
        NotificationRemoteClockEdge.trackingReady();
        Class<?> host=android.view.SurfaceControlViewHost.class;
        for(Method method:host.getDeclaredMethods()) {
            Class<?>[] parameters=method.getParameterTypes();
            if(!method.getName().equals("setView")||parameters.length<1||parameters[0]!=View.class)continue;
            installHook(HookGroup.FLUID_NOTIFICATION,method,chain->{
                Object result=chain.proceed();
                FluidCloudSurfaceRoots.hostView(chain.getThisObject(),(View)chain.getArg(0));
                return result;
            });
        }
        Method getPackage=host.getDeclaredMethod("getSurfacePackage");
        installHook(HookGroup.FLUID_NOTIFICATION,getPackage,chain->{
            Object result=chain.proceed();FluidCloudSurfaceRoots.hostPackage(chain.getThisObject(),result);
            FLUID_STYLE.mediaOwnershipChanged();return result;
        });
        Method setPackage=android.view.SurfaceView.class.getDeclaredMethod("setChildSurfacePackage",android.view.SurfaceControlViewHost.SurfacePackage.class);
        installHook(HookGroup.FLUID_NOTIFICATION,setPackage,chain->{
            Object result=chain.proceed();FluidCloudSurfaceRoots.childPackage((android.view.SurfaceView)chain.getThisObject(),chain.getArg(0));
            FLUID_STYLE.mediaOwnershipChanged();return result;
        });
        Method release=host.getDeclaredMethod("release");
        installHook(HookGroup.FLUID_NOTIFICATION,release,chain->{
            View content=((android.view.SurfaceControlViewHost)chain.getThisObject()).getView();
            if(content!=null)NotificationRemoteClockEdge.release(content);
            FluidCloudSurfaceRoots.releaseHost(chain.getThisObject());Object result=chain.proceed();
            FLUID_STYLE.mediaOwnershipChanged();return result;
        });
    }

    private static void detachFluidBackground(View view) {
        if(view instanceof android.view.SurfaceView) {
            View content=FluidCloudSurfaceRoots.childDetached((android.view.SurfaceView)view);
            if(content!=null)NotificationRemoteClockEdge.release(content);
            FLUID_STYLE.mediaOwnershipChanged();
        }
        try (FluidCloudAccent.NativeRestoreScope accentRestore = FLUID_ACCENT.beginNativeRestore();
                FluidCloudStyle.NativeRestoreScope styleRestore = FLUID_STYLE.beginNativeRestore()) {
            AOD_NOTIFICATION_BACKGROUND.detach(view);FLUID_STYLE.detach(view); FLUID_ACCENT.detach(view);
        }
    }

    private boolean installQsFullFill(ClassLoader loader) {
        try {
            Class<?> tile = loader.loadClass("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne");
            Class<?> base = loader.loadClass("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView");
            Class<?> manager = loader.loadClass("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSHighlightTileViewLabelColorManager");
            Class<?> state = loader.loadClass("com.android.systemui.plugins.qs.QSTile$State");
            Method colors = manager.getDeclaredMethod("getColorByTileState", Context.class, state);
            deoptimize(colors);
            installHook(HookGroup.QS_FULL_FILL, colors, chain -> QS_FULL_FILL.labelColors(
                    chain.getThisObject(), chain.getArg(1), chain.proceed()));
            for (Class<?> type : new Class<?>[]{base, tile}) for (Method event : type.getDeclaredMethods()) {
                String name = event.getName();
                boolean detach = name.equals("onRecycle") || name.equals("onDetachedFromWindow");
                boolean refresh = name.matches("onStateChanged|setStateImmediately|onAttachedToWindow|onThemeApplied|onConfigurationChanged|onQsColorStateChanged|onDrawableUpdate|onMeasure|handleCellLayoutConstraintChanged|setBeingDragged|setEditMode");
                if ((!detach && !refresh) || Modifier.isAbstract(event.getModifiers()) || Modifier.isStatic(event.getModifiers())) continue;
                deoptimize(event);
                installHook(HookGroup.QS_FULL_FILL, event, chain -> {
                    View owner = (View) chain.getThisObject();
                    if (detach) return QS_FULL_FILL.aroundDetach(owner, chain::proceed);
                    Object result = chain.proceed();
                    if (name.equals("onMeasure")) QS_FULL_FILL.measuredTile(owner);
                    else QS_FULL_FILL.refreshTile(owner);
                    return result;
                });
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException unsupported) {
            return false; // Retry when the control-center plugin loader becomes available.
        } catch (Throwable failure) {
            moduleLog(Log.WARN, TAG, "Native 2x1 tile fill unavailable", failure);
            return false;
        }
    }

    private boolean installWifiAlias(ClassLoader loader) {
        int bound=0;
        try {
            Class<?> state=loader.loadClass("com.android.systemui.plugins.qs.QSTile$State");
            for(String name:new String[]{"com.android.systemui.plugins.qs.QSTile$State","com.android.systemui.plugins.qs.QSTile$BooleanState"})try{
                Method copy=loader.loadClass(name).getMethod("copy");deoptimize(copy);
                installHook(HookGroup.WIFI_ALIAS,copy,chain->{Object result=chain.proceed();WIFI_ALIAS.copiedState(chain.getThisObject(),result);return result;});
            }catch(ClassNotFoundException|NoSuchMethodException absent){}catch(RuntimeException|XposedFrameworkError unavailable){
                moduleLog(Log.WARN,TAG,"Optional WiFi display copy tracking unavailable",unavailable);
            }
            for(String name:new String[]{"com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
                    "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView"})try{
                Class<?> type=loader.loadClass(name);
                for(String event:new String[]{"onStateChanged","setStateImmediately"})try{
                    Method method=type.getDeclaredMethod(event,state);deoptimize(method);
                    installHook(HookGroup.WIFI_ALIAS,method,chain->{Object original=chain.getArg(0);
                        Object display=WIFI_ALIAS.displayState((View)chain.getThisObject(),original,method);
                        return display==original?chain.proceed():chain.proceed(new Object[]{display});});bound++;
                }catch(NoSuchMethodException absent){}
                try{Method detach=type.getDeclaredMethod("onDetachedFromWindow");deoptimize(detach);
                    installHook(HookGroup.WIFI_ALIAS,detach,chain->{WIFI_ALIAS.detached((View)chain.getThisObject());return chain.proceed();});
                }catch(NoSuchMethodException absent){}
            }catch(ClassNotFoundException absent){}
            for(String name:new String[]{"com.oplus.systemui.qs.tiles.WifiTile","com.oplus.systemui.qs.tiles.OplusWifiTile"})try{
                Class<?> type=loader.loadClass(name);
                for(Method method:type.getDeclaredMethods())if(method.getName().equals("handleUpdateState")&&method.getParameterTypes().length==2
                        &&method.getParameterTypes()[0].getName().equals("com.android.systemui.plugins.qs.QSTile$BooleanState")){
                    deoptimize(method);installHook(HookGroup.WIFI_ALIAS,method,chain->{Object result=chain.proceed();String ssid=null;boolean connected=false;
                        try{Object nativeState=chain.getArg(0);connected=Boolean.TRUE.equals(QsWifiAlias.value(nativeState,"value"))
                                &&Integer.valueOf(2).equals(QsWifiAlias.value(nativeState,"state"));if(connected){
                            Object value=QsWifiAlias.value(chain.getThisObject(),"mCurConnectedSSID");if(value instanceof String)ssid=QsWifiAlias.networkKey((String)value);
                        }}catch(ReflectiveOperationException|RuntimeException unavailable){}
                        if(connected&&ssid==null&&context!=null)ssid=QsWifiAliasCatalog.connected(context);
                        WIFI_ALIAS.currentNetwork(ssid);QsWifiAliasCatalog.report(context,ssid);return result;});
                }
            }catch(ClassNotFoundException absent){}
            // The editable views can arrive through the later QS plugin loader.
            // Keep retrying until both the normal and editable native display paths are bound.
            return bound>=3;
        }catch(ReflectiveOperationException|RuntimeException unavailable){moduleLog(Log.WARN,TAG,"WiFi display names unavailable",unavailable);return false;}
    }

    private boolean installQsAppearance(ClassLoader loader) {
        boolean found=false;
        try {
            for(String name:new String[]{"com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable",
                    "com.oplus.systemui.qs.base.res.drawable.StateListTileDrawable",
                    "com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable"}) try {
                Class<?> type=loader.loadClass(name);Method draw=type.getDeclaredMethod("draw",Canvas.class);deoptimize(draw);
                installHook(HookGroup.QS_APPEARANCE,draw,chain->{
                    if(QS_FULL_FILL.drawPure((Drawable)chain.getThisObject(),(Canvas)chain.getArg(0)))return null;
                    QS_APPEARANCE.drawTile((Drawable)chain.getThisObject(),(Canvas)chain.getArg(0),canvas->chain.proceed(new Object[]{canvas}));return null;
                });found=true;
            } catch(ClassNotFoundException|NoSuchMethodException unsupported) { }
            for(String name:new String[]{"com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable",
                    "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable",
                    "com.oplus.posteffect.drawable.BlendDrawable"}) try {
                Class<?> type=loader.loadClass(name);boolean glass=name.endsWith(".BlendDrawable");
                Method draw=type.getDeclaredMethod(glass?"onDrawContent":"draw",Canvas.class);deoptimize(draw);
                installHook(HookGroup.QS_APPEARANCE,draw,chain->{
                    Drawable drawable=(Drawable)chain.getThisObject();Canvas canvas=(Canvas)chain.getArg(0);
                    if(glass) {
                        C17HighlightRemoval.RecordScope record = C17_HIGHLIGHTS.beginRecord(drawable);
                        try { CLEAR_APPEARANCE.drawGlassContent(drawable,canvas,
                                target->QS_APPEARANCE.drawGlassContent(drawable,target,
                                        mediaCanvas->QS_MEDIA.drawGlassContent(drawable,mediaCanvas,
                                                nativeCanvas->C17_HIGHLIGHTS.drawContent(drawable,nativeCanvas,
                                                        acrylicCanvas->chain.proceed(new Object[]{acrylicCanvas})))));
                        } finally { record.close(); }
                    }
                    else {
                        C17HighlightRemoval.SurfaceScope surface=C17_HIGHLIGHTS.beginBlur(drawable);
                        try { CLEAR_APPEARANCE.drawBlur(drawable,canvas,
                                target->QS_APPEARANCE.drawBlur(drawable,target,
                                        mediaCanvas->QS_MEDIA.drawBlur(drawable,mediaCanvas,nativeCanvas->chain.proceed(new Object[]{nativeCanvas}))));
                        } finally { surface.close(); }
                    }
                    return null;
                });found=true;
            } catch(ClassNotFoundException|NoSuchMethodException unsupported) { }
            for(String name:new String[]{"com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
                    "com.coui.appcompat.seekbar.COUIVerticalSeekBar"}) try {
                Class<?> type=loader.loadClass(name);String methodName=name.startsWith("com.oplus.")?"drawActiveMixColorTrack":"drawActiveTrack";
                Method draw=type.getDeclaredMethod(methodName,Canvas.class);deoptimize(draw);
                for(Method caller:type.getDeclaredMethods())if(caller.getName().equals("onDraw"))deoptimize(caller);
                installHook(HookGroup.QS_APPEARANCE,draw,chain->{
                    QS_APPEARANCE.drawSlider((View)chain.getThisObject(),(Canvas)chain.getArg(0),canvas->chain.proceed(new Object[]{canvas}));return null;
                });found=true;
            } catch(ClassNotFoundException|NoSuchMethodException unsupported) { }
            for(String name:new String[]{"com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
                    "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne",
                    "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne",
                    "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXTwo"}) try {
                Class<?> type=loader.loadClass(name);
                for(Method event:type.getDeclaredMethods()) {
                    String eventName=event.getName();boolean detach=eventName.equals("onRecycle")||eventName.equals("onDetachedFromWindow");
                    boolean refresh=eventName.equals("onStateChanged")||eventName.equals("setStateImmediately")||eventName.equals("onAttachedToWindow")
                            ||eventName.equals("onThemeApplied")||eventName.equals("onConfigurationChanged")||eventName.equals("onQsColorStateChanged")||eventName.equals("onDrawableUpdate");
                    if(!detach&&!refresh)continue;deoptimize(event);
                    installHook(HookGroup.QS_APPEARANCE,event,chain->{Object result=chain.proceed();View owner=(View)chain.getThisObject();
                        if(detach){QS_APPEARANCE.detach(owner);C17_HIGHLIGHTS.detach(owner);}else QS_APPEARANCE.refreshTile(owner);return result;
                    });
                }
            } catch(ClassNotFoundException unsupported) { }
            try {
                Class<?> highlight=loader.loadClass(QsTileCorners.HIGHLIGHT_CLASS);
                for(Method event:highlight.getDeclaredMethods()) {
                    String eventName=event.getName();boolean detach=eventName.equals("onDetachedFromWindow");
                    boolean refresh=eventName.matches("onStateChanged|handleStateChanged|onAttachedToWindow|onConfigurationChanged|onQsColorStateChanged|onDrawableUpdate|onOutlineUpdate");
                    if((!detach&&!refresh)||Modifier.isAbstract(event.getModifiers()))continue;deoptimize(event);
                    installHook(HookGroup.QS_APPEARANCE,event,chain->{Object result=chain.proceed();View owner=(View)chain.getThisObject();
                        if(detach)QS_APPEARANCE.detach(owner);else QS_APPEARANCE.refreshHighlightTile(owner);return result;
                    });
                }
            } catch(ClassNotFoundException unsupported) { }
            for(String name:new String[]{QsTileAppearance.DETAIL_TILE,
                    "com.oplus.systemui.qs.slider.detail.widget.StdQsDetailTileView",
                    "com.oplus.systemui.qs.slider.detail.widget.SepQsDetailTileView"}) try {
                Class<?> type=loader.loadClass(name);
                for(Method event:type.getDeclaredMethods()) {
                    String eventName=event.getName();boolean detach=eventName.equals("onDetachedFromWindow");
                    boolean refresh=eventName.matches("onStateChanged|setState|init|onDrawableUpdate|onOutlineUpdate");
                    if((!detach&&!refresh)||Modifier.isAbstract(event.getModifiers())||Modifier.isStatic(event.getModifiers()))continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_APPEARANCE,event,chain->{Object result=chain.proceed();Object owner=chain.getThisObject();
                        if(owner instanceof View){if(detach)QS_APPEARANCE.detach((View)owner);else QS_APPEARANCE.refreshDetailTile((View)owner);}
                        return result;
                    });
                }
            } catch(ClassNotFoundException unsupported) { }
            for(String name:new String[]{"com.oplus.deviceplugin.sdk.ui.view.separatecardview.d",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.NoDeviceEntranceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleEntranceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView"})try {
                Class<?> type=loader.loadClass(name);
                for(Method event:type.getDeclaredMethods()) {
                    String method=event.getName();boolean detach=method.equals("onDetachedFromWindow");
                    if(!detach&&!method.matches("g|onAttachedToWindow|onConfigurationChanged"))continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_APPEARANCE,event,chain->{
                        Object result=chain.proceed();View owner=(View)chain.getThisObject();
                        if(detach){QS_APPEARANCE.detach(owner);C17_HIGHLIGHTS.detach(owner);}
                        else {QS_APPEARANCE.refreshDeviceCard(owner);C17_HIGHLIGHTS.refreshDeviceCard(owner);}
                        return result;
                    });
                }
            }catch(ClassNotFoundException unsupported) { }
            moduleLog(Log.INFO,TAG,found?"QS background and active track appearance hooks installed":"QS appearance unavailable; preserving native backgrounds");
            return true;
        } catch(Throwable error) {moduleLog(Log.ERROR,TAG,"Could not install QS background appearance",error);return false;}
    }

    // Ordinary tile completion is independent of later SDK/plugin class loaders.
    private final Map<ClassLoader, Integer> qsDeviceIconLoaders = new WeakHashMap<>();
    private void installQsDeviceIconSize(ClassLoader loader) {
        // Native device-space/headphone cards are independent of OplusQSIconView. Register
        // their exact glyphs even when this loader has not created the ordinary tile plugin.
        if (loader == null || ModuleLifecycle.removed()) return;
        try {
            synchronized (SYSTEMUI_HOOK_LOCK) {
            int known = qsDeviceIconLoaders.containsKey(loader) ? qsDeviceIconLoaders.get(loader) : 0;
            if (known == 63) return;
            String[] names = {QsPanelCorners.DEVICE_BASE_CLASS, QsDeviceIconSize.DEVICE, QsDeviceIconSize.ENTRANCE};
            for (int index = 0; index < names.length; index++) {
                int bit = 1 << index; if ((known & bit) != 0) continue;
                try {
                    Class<?> type = loader.loadClass(names[index]);
                    for (Method event : type.getDeclaredMethods()) {
                        String method = event.getName();
                        if (java.lang.reflect.Modifier.isAbstract(event.getModifiers()) || !method.matches("onAttachedToWindow|onConfigurationChanged|g|i|setCustomConfig")) continue;
                        if (qsIconSizeHookInstalled(event)) continue;
                        deoptimize(event);
                        installHook(HookGroup.QS_ICON_SIZE, event, chain -> {
                            Object result = chain.proceed(); QS_ICON_SIZE.onDeviceUpdate((View) chain.getThisObject()); return result;
                        });
                    }
                    known |= bit;
                } catch (ClassNotFoundException absentVariant) { }
            }
            if ((known & 8) == 0) try {
                Class<?> progress = loader.loadClass(QsDeviceIconSize.BATTERY);
                for (Method event : progress.getDeclaredMethods()) {
                    if (!event.getName().matches("setDeviceIcon|setDeviceIconWithAnimation|onConfigurationChanged")) continue;
                    if (qsIconSizeHookInstalled(event)) continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_ICON_SIZE, event, chain -> {
                        Object result = chain.proceed(); QS_ICON_SIZE.onDeviceGlyphUpdate((View) chain.getThisObject()); return result;
                    });
                }
                known |= 8;
            } catch (ClassNotFoundException absentVariant) { }
            // Real classes8 callers of f.setAlpha during native drawable transitions.
            // Only deopt these two callbacks; neither charging nor battery-ring animation is altered.
            String[] alphaCallers = {"com.oplus.deviceplugin.sdk.ui.view.progressbar.b", "com.oplus.deviceplugin.sdk.ui.view.progressbar.d"};
            for (int index = 0; index < alphaCallers.length; index++) {
                int bit = 16 << index; if ((known & bit) != 0) continue;
                try { deoptimize(loader.loadClass(alphaCallers[index]).getDeclaredMethod("invoke", Object.class)); }
                catch (ClassNotFoundException | NoSuchMethodException absentVariant) { }
                known |= bit;
            }
            Method layout = View.class.getDeclaredMethod("setLayoutParams", ViewGroup.LayoutParams.class);
            if (!qsIconSizeHookInstalled(layout)) {
                deoptimize(layout);
                installHook(HookGroup.QS_ICON_SIZE, layout, chain -> {
                    QS_ICON_SIZE.deviceLayout((View) chain.getThisObject(), (ViewGroup.LayoutParams) chain.getArg(0));
                    return chain.proceed();
                });
            }
            Method alpha = View.class.getDeclaredMethod("setAlpha", float.class);
            if (!qsIconSizeHookInstalled(alpha)) {
                deoptimize(alpha);
                installHook(HookGroup.QS_ICON_SIZE, alpha, chain -> {
                    float original = (Float) chain.getArg(0), result = QS_ICON_SIZE.deviceAlpha((View) chain.getThisObject(), original);
                    return result == original ? chain.proceed() : chain.proceed(new Object[]{result});
                });
            }
            qsDeviceIconLoaders.put(loader, known);
            }
        } catch (Throwable unsupportedDevice) {
            moduleLog(Log.WARN, TAG, "Native square device glyph sizing unavailable", unsupportedDevice);
        }
    }
    private static boolean qsIconSizeHookInstalled(Method method) {
        Set<Executable> installed = INSTALLED_HOOKS.get(HookGroup.QS_ICON_SIZE);
        return installed != null && installed.contains(method);
    }
    private boolean installIconManagement(ClassLoader loader) {
        boolean controls=IconNativeControlCenterHooks.install(loader,ICON_NATIVE_CONTROLS,(method,hooker)->{
            deoptimize(method);installHook(HookGroup.ICON_MANAGEMENT,method,hooker);
        });
        boolean tiles=false;
        try{
            IconNativeQsHooks.install(loader,ICON_NATIVE_QS,(method,hooker)->{
                Class<?> owner=method.getDeclaringClass();
                // View and ImageView callbacks run for every view in SystemUI (about 41,000 calls in three
                // notification-shade cycles on the PJZ110). They exist only while icon management is on.
                if(owner==View.class||owner==ImageView.class)lazyHook("icon_management_"+method.getName(),IconManagementRuntime::active,()->{
                    deoptimize(method);installHook(HookGroup.ICON_MANAGEMENT,method,hooker);
                });
                else{deoptimize(method);installHook(HookGroup.ICON_MANAGEMENT,method,hooker);}
            });tiles=true;
        }catch(ReflectiveOperationException unavailable){
            moduleLog(Log.WARN,TAG,"Native QS glyph binding unavailable",unavailable);
        }
        return controls&&tiles;
    }
    private boolean installQsIconSize(ClassLoader loader) {
        installQsDeviceIconSize(loader);
        try {
            Class<?> icon = loader.loadClass(QsTileIconSize.ICON);
            Method dimen = icon.getDeclaredMethod("dimen", int.class);
            if (dimen.getReturnType() != int.class) return false;
            Method measure = icon.getDeclaredMethod("onMeasure", int.class, int.class);
            // ART can inline dimen() into this actual glyph measurement caller. Hooking and
            // deoptimizing only dimen() leaves the compiled caller using the native size.
            deoptimize(measure);
            installHook(HookGroup.QS_ICON_SIZE, measure, chain -> {
                QS_ICON_SIZE.onIconMeasure((View) chain.getThisObject());
                return chain.proceed();
            });
            deoptimize(dimen);
            installHook(HookGroup.QS_ICON_SIZE, dimen, chain -> {
                Object result = chain.proceed();
                return QS_ICON_SIZE.dimension((View) chain.getThisObject(), (Integer) chain.getArg(0), (Integer) result);
            });
            Class<?> base = loader.loadClass(QsTileCorners.TILE_CLASS);
            Class<?> small = loader.loadClass(QsTileIconSize.TILE);
            for (Class<?> type : new Class<?>[]{base, small}) for (Method event : type.getDeclaredMethods()) {
                String name = event.getName();
                boolean detach = name.equals("onDetachedFromWindow") || name.equals("onRecycle");
                if (!detach && !name.matches("onAttachedToWindow|onConfigurationChanged|onMeasure|handleCellLayoutConstraintChanged"
                        + "|onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged")) continue;
                // Newer OEM bases declare callbacks without a body. ART cannot deoptimize
                // abstract methods; the concrete tile override is handled separately.
                if (Modifier.isAbstract(event.getModifiers())) continue;
                deoptimize(event);
                installHook(HookGroup.QS_ICON_SIZE, event, chain -> {
                    View tile = (View) chain.getThisObject();
                    if (detach) QS_ICON_SIZE.detach(tile);
                    Object result = chain.proceed();
                    if (!detach) QS_ICON_SIZE.onNativeUpdate(tile);
                    return result;
                });
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException unsupported) {
            // Plugin types can appear in a later loader; do not mark this optional group complete.
            return false;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Native 1x1 glyph sizing unavailable", error);
            return false;
        }
    }

    private boolean installQsCorners(ClassLoader loader) {
        try {
            Class<?> base = loader.loadClass(QsTileCorners.TILE_CLASS);
            List<Class<?>> types = new ArrayList<>(); types.add(base);
            for (String name : QsTileCorners.TILE_SUBCLASSES) try { types.add(loader.loadClass(name)); }
            catch (ClassNotFoundException absentVariant) { }
            Class<?> outline = loader.loadClass("com.oplusos.systemui.common.outline.CornerOutlineProvider");
            Class<?> layer = loader.loadClass("com.oplus.systemui.qs.base.res.drawable.TileLayerDrawable");
            loader.loadClass("com.oplus.systemui.qs.base.res.util.QSConstant")
                    .getDeclaredMethod("getSmoothRoundRectOutlineProvider", Context.class, float.class);
            layer.getDeclaredMethod("setPathProvider", outline);
            layer.getDeclaredMethod("invalidatePath");
            Method block = layer.getDeclaredMethod("setBlockPathProvider", outline); deoptimize(block);
            QS_CORNERS.setFixedTransitionAvailable(false);
            try {
                Method fixed = loader.loadClass(QsTileCorners.FIXED_RADIUS_CLASS)
                        .getDeclaredMethod("setValue", View.class, Float.TYPE);
                deoptimize(fixed);
                installHook(HookGroup.QS_CORNERS, fixed, chain -> {
                    QS_CORNERS.beforeFixedTransitionValue(chain.getThisObject(), chain.getArg(1));
                    return chain.proceed();
                });
                QS_CORNERS.setFixedTransitionAvailable(true);
            } catch (ClassNotFoundException | NoSuchMethodException unsupported) {
                moduleLog(Log.INFO, TAG, "Native fixed-radius spring unavailable; retaining native press outline");
            }
            installHook(HookGroup.QS_CORNERS, block, chain -> {
                Drawable drawable = (Drawable) chain.getThisObject();
                Object result = chain.proceed(new Object[]{QS_CORNERS.adaptBlock(drawable, chain.getArg(0))});
                QS_PANEL_CORNERS.onDrawableUpdate(drawable);
                return result;
            });
            // Each size has its own native getter. Keep per-instance ownership rather than
            // changing shared resources, and include inherited getters via the base class.
            int updates = 0;
            for (Class<?> type : types) for (Method event : type.getDeclaredMethods()) {
                String name = event.getName();
                if ((name.equals("getCornerRadius") || name.equals("getViewRadius") || name.equals("getCornerWeight"))
                        && event.getParameterCount() == 0) {
                    deoptimize(event);
                    installHook(HookGroup.QS_CORNERS, event, chain -> {
                        Object result = chain.proceed(); View owner = (View) chain.getThisObject();
                        return name.equals("getCornerWeight") ? QS_CORNERS.cornerWeight(owner, result)
                                : name.equals("getViewRadius") ? QS_CORNERS.viewRadius(owner, result)
                                : QS_CORNERS.cornerRadius(owner, result);
                    });
                    continue;
                }
                boolean recycle = name.equals("onRecycle");
                boolean detach = name.equals("onDetachedFromWindow");
                boolean refresh = name.matches("onAttachedToWindow|onConfigurationChanged|onOutlineUpdate|onDrawableUpdate|onMeasure"
                        + "|onStateChanged|setStateImmediately|onThemeApplied|onQsColorStateChanged");
                if (!recycle && !detach && !refresh) continue;
                deoptimize(event);
                installHook(HookGroup.QS_CORNERS, event, chain -> {
                    View owner = (View) chain.getThisObject();
                    if (recycle) QS_CORNERS.detach(owner);
                    Object result = chain.proceed();
                    if (detach) QS_CORNERS.detach(owner);
                    else if (!recycle) QS_CORNERS.onNativeUpdate(owner);
                    return result;
                });
                updates++;
            }
            // The highlight tiles that open a secondary page: same ownership, plus their pressed spot-light path.
            try {
                Class<?> highlight = loader.loadClass(QsTileCorners.HIGHLIGHT_CLASS);
                for (Method event : highlight.getDeclaredMethods()) {
                    String name = event.getName();
                    boolean detach = name.equals("onDetachedFromWindow");
                    boolean refresh = name.matches("onAttachedToWindow|onConfigurationChanged|onOutlineUpdate|onDrawableUpdate|onStateChanged"
                            + "|handleStateChanged|onQsColorStateChanged|onMeasure");
                    if ((!detach && !refresh) || Modifier.isAbstract(event.getModifiers())) continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_CORNERS, event, chain -> {
                        View owner = (View) chain.getThisObject();
                        Object result = chain.proceed();
                        if (detach) QS_CORNERS.detach(owner); else QS_CORNERS.onNativeUpdate(owner);
                        return result;
                    });
                    updates++;
                }
                Method spot = highlight.getDeclaredMethod("updateSpotLightPath", android.graphics.Path.class, Object.class);
                deoptimize(spot);
                installHook(HookGroup.QS_CORNERS, spot, chain ->
                        QS_CORNERS.spotLight((View) chain.getThisObject(), chain.getArg(0), chain.getArg(1)) ? null : chain.proceed());
            } catch (ClassNotFoundException | NoSuchMethodException absent) {
                moduleLog(Log.INFO, TAG, "QS highlight tile not present; its shape stays native");
            }
            // The round buttons of the brightness / volume secondary page, and their pressed spot-light.
            try {
                Class<?> detail = loader.loadClass(QsTileCorners.DETAIL_CLASS);
                for (Method event : detail.getDeclaredMethods()) {
                    String name = event.getName();
                    boolean detach = name.equals("onDetachedFromWindow");
                    boolean refresh = name.matches("onStateChanged|setState|init|onDrawableUpdate|onOutlineUpdate");
                    if ((!detach && !refresh) || Modifier.isAbstract(event.getModifiers()) || Modifier.isStatic(event.getModifiers())) continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_CORNERS, event, chain -> {
                        Object result = chain.proceed(); Object owner = chain.getThisObject();
                        if (owner instanceof View) { if (detach) QS_CORNERS.detach((View) owner); else QS_CORNERS.onNativeUpdate((View) owner); }
                        return result;
                    });
                    updates++;
                }
                for (String name : new String[]{"com.oplus.systemui.qs.slider.detail.widget.StdQsDetailTileView",
                        "com.oplus.systemui.qs.slider.detail.widget.SepQsDetailTileView"}) try {
                    for (Method event : loader.loadClass(name).getDeclaredMethods()) {
                        String eventName = event.getName();
                        if (!eventName.matches("onDrawableUpdate|onOutlineUpdate") || Modifier.isAbstract(event.getModifiers())) continue;
                        deoptimize(event);
                        installHook(HookGroup.QS_CORNERS, event, chain -> {
                            Object result = chain.proceed(); Object owner = chain.getThisObject();
                            if (owner instanceof View) QS_CORNERS.onNativeUpdate((View) owner);
                            return result;
                        });
                        updates++;
                    }
                } catch (ClassNotFoundException absentVariant) { }
                Method spotRadius = loader.loadClass("com.oplus.systemui.qs.base.spotlight.SpotLightConstraintLayout")
                        .getDeclaredMethod("getSpotLightRadius");
                deoptimize(spotRadius);
                installHook(HookGroup.QS_CORNERS, spotRadius, chain -> {
                    Object result = chain.proceed(); Object owner = chain.getThisObject();
                    return owner instanceof View ? QS_CORNERS.detailSpotRadius((View) owner, result) : result;
                });
            } catch (ClassNotFoundException | NoSuchMethodException absent) {
                moduleLog(Log.INFO, TAG, "QS secondary page buttons not present; their shape stays native");
            }
            moduleLog(Log.INFO, TAG, "All native QS tile instance corner hooks installed; shared outline pool retained");
            return updates > 0;
        } catch (ClassNotFoundException | NoSuchMethodException unsupported) {
            moduleLog(Log.WARN, TAG, "QS tile corner API unavailable; preserving native geometry", unsupported);
            return true;
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not install QS tile instance corner controls", error);
            return false;
        }
    }

    private boolean installQsPanelCorners(ClassLoader loader) {
        synchronized (SYSTEMUI_HOOK_LOCK) { if (!QS_CORNER_LOADERS.add(loader)) return true; }
        boolean found = false;
        try {
            // Its blur-manager hooks live in this loader's own classes (the framework ones are de-duplicated by installHook),
            // so every loader that reaches this site registers its own on-demand install.
            lazyHook("qs_panel_shapes@" + System.identityHashCode(loader), QS_PANEL_CORNERS::active, () -> installQsSliderShapeHooks(loader));
            try {
                Class<?> holder = loader.loadClass(QsPanelCorners.EDITABLE_HOLDER_CLASS);
                for (Method event : holder.getDeclaredMethods()) {
                    String name = event.getName();
                    if (!name.matches("bindData|onPanelSizeChanged|updateColor|setListening|onRecycled")) continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> {
                        if (name.equals("onRecycled")) bindNativeQsPluginHolder(chain.getThisObject(), true);
                        Object result = chain.proceed();
                        if (!name.equals("onRecycled")) bindNativeQsPluginHolder(chain.getThisObject(), false);
                        return result;
                    });
                }
            } catch (ClassNotFoundException absentHolder) { }
            try {
                Class<?> service = loader.loadClass(QsPanelCorners.DEVICE_SERVICE_CLASS);
                for (Method event : service.getDeclaredMethods()) {
                    if (!event.getName().equals("onCreateView") || event.getParameterCount() != 3
                            || !ViewGroup.class.isAssignableFrom(event.getParameterTypes()[0])) continue;
                    deoptimize(event);
                    installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> {
                        Object result = chain.proceed();
                        Object root = chain.getArg(0);
                        if (root instanceof View) bindNativeQsPluginContainer((View) root, false);
                        return result;
                    });
                }
            } catch (ClassNotFoundException absentDeviceService) { }
            try {
                Method radius = loader.loadClass(QsPanelCorners.EDITABLE_CONTAINER_CLASS).getDeclaredMethod("getViewRadius");
                deoptimize(radius);
                installHook(HookGroup.QS_PANEL_CORNERS, radius, chain -> QS_PANEL_CORNERS.editableRadius(
                        (View) chain.getThisObject(), chain.proceed()));
                Method spotlight = radius.getDeclaringClass().getDeclaredMethod("updateSpotLightPath", Path.class, Object.class);
                deoptimize(spotlight);
                installHook(HookGroup.QS_PANEL_CORNERS, spotlight, chain -> {
                    QsPanelCorners.SpotlightScope scope = QS_PANEL_CORNERS.beginSpotlight((View) chain.getThisObject(),
                            (Path) chain.getArg(0), chain.getArg(1));
                    try { return chain.proceed(); } finally { scope.close(); }
                });
            } catch (ClassNotFoundException | NoSuchMethodException absentWrapper) { }
            List<Class<?>> types = new ArrayList<>();
            for (String name : new String[]{QsPanelCorners.SLIDER_BASE_CLASS, QsPanelCorners.SLIDER_CLASS,
                    QsPanelCorners.MEDIA_CLASS, "com.oplus.systemui.qs.media.OplusQsNormalMediaPanelView",
                    "com.oplus.systemui.qs.media.SepQsLargeMediaPanelView",
                    QsPanelCorners.DEVICE_BASE_CLASS,
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareDeviceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.NoDeviceEntranceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleEntranceCardView",
                    "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView"})
                try { types.add(loader.loadClass(name)); } catch (ClassNotFoundException absentVariant) { }
            for (Class<?> type : types) for (Method event : type.getDeclaredMethods()) {
                String name = event.getName();
                if (type.getName().equals(QsPanelCorners.SLIDER_BASE_CLASS) && name.equals("draw")
                        && java.util.Arrays.equals(event.getParameterTypes(), new Class<?>[]{Canvas.class})) {
                    deoptimize(event);
                    installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> {
                        QsPanelCorners.DrawScope scope = QS_PANEL_CORNERS.beginDraw(
                                (View) chain.getThisObject(), (Canvas) chain.getArg(0));
                        try { return chain.proceed(); } finally { scope.close(); }
                    });
                    found = true; continue;
                }
                if (type.getName().equals(QsPanelCorners.SLIDER_CLASS) && name.equals("updateBaseMixColorDrawableRadius")
                        && java.util.Arrays.equals(event.getParameterTypes(), new Class<?>[]{float.class})) {
                    deoptimize(event);
                    installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> {
                        QsPanelCorners.BlurScope scope = QS_PANEL_CORNERS.beginBlurUpdate(
                                (View) chain.getThisObject(), ((Number) chain.getArg(0)).floatValue());
                        try { return chain.proceed(new Object[]{scope.radius}); } finally { scope.close(); }
                    });
                    found = true; continue;
                }
                if (type.getName().startsWith("com.oplus.systemui.qs.media.")
                        && name.equals("getContainerCornerRadius") && event.getParameterCount() == 0) {
                    deoptimize(event);
                    installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> QS_PANEL_CORNERS.cornerRadius(
                            (View) chain.getThisObject(), chain.proceed()));
                    found = true; continue;
                }
                boolean detach = name.equals("onDetachedFromWindow") || name.equals("onRecycle");
                boolean device = type.getName().startsWith("com.oplus.deviceplugin.sdk.ui.view.separatecardview.");
                boolean refresh = name.matches("onAttachedToWindow|onConfigurationChanged|onFinishInflate|initView"
                        + "|onOutlineUpdate|onDrawableUpdate|onMeasure|onSizeChanged|setBackgroundRadius|setProgressRadius")
                        || type.getName().equals(QsPanelCorners.SLIDER_CLASS) && name.equals("updateColor")
                        || device && name.matches("g|h|setCornerRadiusToDrawable|setBlurDrawable");
                if (!detach && !refresh) continue;
                deoptimize(event);
                installHook(HookGroup.QS_PANEL_CORNERS, event, chain -> {
                    View owner = (View) chain.getThisObject();
                    if (name.equals("onRecycle")) QS_PANEL_CORNERS.detach(owner);
                    Object result = chain.proceed();
                    if (detach) QS_PANEL_CORNERS.detach(owner); else QS_PANEL_CORNERS.onNativeUpdate(owner);
                    return result;
                });
                found = true;
            }
            moduleLog(Log.INFO, TAG, found ? "Native QS slider, media and device corner hooks installed"
                    : "Optional QS panel corner APIs unavailable; preserving native geometry");
            return true;
        } catch (Throwable error) {
            synchronized (SYSTEMUI_HOOK_LOCK) { QS_CORNER_LOADERS.remove(loader); }
            moduleLog(Log.ERROR, TAG, "Could not install native QS panel corner controls", error); return false;
        }
    }

    /** The plugin can inflate its SDK in a different loader, or finish children after its first native bind. */
    private void bindNativeQsPluginHolder(Object holder, boolean detach) {
        Object actual = QsTileAppearance.call(holder, "getRealPluginContainer");
        if (!(actual instanceof View)) return;
        bindNativeQsPluginContainer((View) actual, detach);
    }
    private void bindNativeQsPluginContainer(View container, boolean detach) {
        bindNativeQsPluginChildren(container, detach);
        if (detach) { synchronized (QS_PLUGIN_REBINDS) { QS_PLUGIN_REBINDS.remove(container); } return; }
        synchronized (QS_PLUGIN_REBINDS) { if (!QS_PLUGIN_REBINDS.add(container)) return; }
        WeakReference<View> weak = new WeakReference<>(container);
        MAIN.post(() -> {
            View current = weak.get();
            if (current == null) return;
            synchronized (QS_PLUGIN_REBINDS) { if (!QS_PLUGIN_REBINDS.remove(current)) return; }
            if (current.isAttachedToWindow()) bindNativeQsPluginChildren(current, false);
        });
    }
    private void bindNativeQsPluginChildren(View container, boolean detach) {
        for (View child : QsPanelCorners.pluginControls(container)) {
            if (detach) { QS_PANEL_CORNERS.detach(child); QS_ICON_SIZE.detachDevice(child); continue; }
            ClassLoader actualLoader = child.getClass().getClassLoader();
            if (actualLoader != null) installC17HighlightRemoval(actualLoader);
            if (actualLoader != null) installQsPanelCorners(actualLoader);
            QS_PANEL_CORNERS.onNativeUpdate(child);
            if (QsDeviceIconSize.supported(child)) {
                installQsDeviceIconSize(actualLoader);
                QS_ICON_SIZE.onDeviceUpdate(child);
            }
        }
    }

    /** The OEM slider uses radius for geometry too; rewrite only its final owned shapes. */
    private void installQsSliderShapeHooks(ClassLoader loader) {
        for (String name : new String[]{"com.oplus.graphics.OplusPath", "com.oplus.graphics.OplusPathAdapter",
                "com.oplus.graphics.OplusCanvas", "android.graphics.Path", "android.graphics.Canvas"}) try {
            Class<?> type = loader.loadClass(name);
            if (name.startsWith("com.oplus.graphics.")) for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 0 || parameters[0] != android.graphics.Path.class && parameters[0] != Canvas.class) continue;
                deoptimize(constructor);
                installHook(HookGroup.QS_PANEL_CORNERS, constructor, chain -> {
                    Object source = chain.getArg(0);
                    Object result = chain.proceed();
                    QS_PANEL_CORNERS.onShapeWrapper(chain.getThisObject(), source);
                    return result;
                });
            }
            for (Method method : type.getDeclaredMethods()) {
                String event = method.getName();
                if (!event.matches("addSmoothRoundRect|addRoundRect|drawSmoothRoundRect|drawRoundRect")) continue;
                deoptimize(method);
                installHook(HookGroup.QS_PANEL_CORNERS, method, chain -> {
                    if (!QS_PANEL_CORNERS.isDrawingShapes()) return chain.proceed();
                    Object[] args = new Object[method.getParameterCount()];
                    for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
                    QsPanelCorners.ShapeScope scope = QS_PANEL_CORNERS.shape(chain.getThisObject(), event, args);
                    try { return scope.handled ? null : chain.proceed(scope.args); } finally { scope.close(); }
                });
            }
        } catch (ClassNotFoundException unavailableVariant) { }
        try {
            Class<?> type = loader.loadClass(QsPanelCorners.BLUR_MANAGER_CLASS);
            for (Method method : type.getDeclaredMethods()) {
                String event = method.getName();
                if (!event.matches("applySeekBarBgBlurConfig|applySeekBarActiveBlurConfig|createSeekBarBlurDrawable"
                        + "|getSeekBarBackground|getSeekBarActiveDrawable")) continue;
                deoptimize(method);
                installHook(HookGroup.QS_PANEL_CORNERS, method, chain -> {
                    if (!QS_PANEL_CORNERS.rewritesBlur(chain.getArg(0))) return chain.proceed();
                    Object[] args = new Object[method.getParameterCount()];
                    for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
                    return chain.proceed(QS_PANEL_CORNERS.blurShape(event, args));
                });
            }
        } catch (ClassNotFoundException unavailableVariant) { }
    }

    private boolean installQsMediaAppearance(ClassLoader loader) {
        try {
            Class<?> media=loader.loadClass("com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView");
            for(Method event:media.getDeclaredMethods()) {
                String name=event.getName();boolean detach=name.equals("onDetachedFromWindow");
                if(!detach&&!name.matches("onFinishInflate|onAttachedToWindow|bindCoverImg|resetCoverImg|bindMediaDataInner|onDrawableUpdate|onConfigurationChanged|updateColor|onThemeApplied|resetView|setCoverImg"))continue;
                deoptimize(event);
                installHook(HookGroup.QS_MEDIA,event,chain->{Object result=chain.proceed();View owner=(View)chain.getThisObject();
                    if(detach)QS_MEDIA.detach(owner);else QS_MEDIA.onNativeUpdate(owner,name);return result;
                });
            }
            Method cover=ImageView.class.getDeclaredMethod("setImageDrawable",Drawable.class);deoptimize(cover);
            installHook(HookGroup.QS_MEDIA,cover,chain->{Object result=chain.proceed();QS_MEDIA.onCoverChanged((View)chain.getThisObject());return result;});
            lazyHook("qs_media_child",QS_MEDIA::active,()->{
                Method child=ViewGroup.class.getDeclaredMethod("drawChild",Canvas.class,View.class,Long.TYPE);deoptimize(child);
                installHook(HookGroup.QS_MEDIA,child,chain->QS_MEDIA.drawCover((View)chain.getThisObject(),(View)chain.getArg(1),(Canvas)chain.getArg(0),
                        canvas->(Boolean)chain.proceed(new Object[]{canvas,chain.getArg(1),chain.getArg(2)})));
            });
            // Actual native call sites may inline getBlurDrawable/onDrawContent; preserve those
            // exact hooks without an extra provider query or a global Canvas hook.
            for(String callerName:new String[]{"com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable",
                    "com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable",
                    "com.oplus.posteffect.drawable.BaseDrawable",
                    "com.oplus.posteffect.drawable.BlendDrawable"}) try {
                Class<?> caller=loader.loadClass(callerName);
                for(Method method:caller.getDeclaredMethods())if(callerName.endsWith(".BaseDrawable")
                        ? method.getName().matches("access\\$drawContent|preDrawContent|releaseDrawableBitmapShader|releaseDrawingBitmap"
                                + "|setCornerParams|setEnableBlurShader|setGradientStrokeLineParams|setInnerShadowParams|setOpticsParams")
                        : callerName.endsWith(".BlendDrawable")
                                ? method.getName().matches("setBlendParams|setShaderBlendParams")
                                : method.getName().equals("draw")||method.getName().equals("getDrawable"))deoptimize(method);
            } catch(ClassNotFoundException absentCaller) { }
            try {
                Class<?> proxy=loader.loadClass("com.oplusos.systemui.common.blurability.ViewBlurProxy");
                for(Method event:proxy.getDeclaredMethods())if(event.getName().equals("getBlurDrawable")) {
                    deoptimize(event);
                    installHook(HookGroup.QS_MEDIA,event,chain->{Object result=chain.proceed();
                        if(result instanceof Drawable)C17_HIGHLIGHTS.onNativeBlurResult(chain.getThisObject(),(Drawable)result);
                        QS_MEDIA.onNativeBlurResult(chain.getThisObject(),result);return result;});
                }
                Class<?> shader=loader.loadClass("com.oplus.posteffect.agsl.DrawableShader");
                for(Method event:shader.getDeclaredMethods())if(event.getName().matches(
                        "copyAllEffectsFrom|setBgAndFgBlendParams|setBitmapShader|setCorner|setCustomClip|setCustomClipParams"
                        +"|setEnableBlend|setGradientStrokeParams|setInnerShadowParams|setMatrix|setMetaBallParams"
                        +"|setMultiBlendParams|setOpticsParams|setSize|setValid")) {
                    deoptimize(event);
                    String eventKey=event.toGenericString();int argumentCount=event.getParameterCount();
                    installHook(HookGroup.QS_MEDIA,event,chain->{Object result=chain.proceed();
                        Object owner=chain.getThisObject();
                        if(QS_MEDIA.tracksMaterial(owner)) {
                            Object[] arguments=new Object[argumentCount];
                            for(int i=0;i<argumentCount;i++)arguments[i]=chain.getArg(i);
                            QS_MEDIA.onNativeMaterialUpdate(owner,eventKey,arguments);
                        }
                        return result;});
                }
            } catch(ClassNotFoundException optionalMaterialUnavailable) {
                moduleLog(Log.INFO,TAG,"Optional media material cache unavailable; native background retained");
            }
            moduleLog(Log.INFO,TAG,"Native QS media artwork and cover glow hooks installed");
            return true;
        } catch(ClassNotFoundException unsupported) {
            moduleLog(Log.INFO,TAG,"Native QS media artwork unavailable; preserving original card");return true;
        } catch(Throwable unavailable) {
            moduleLog(Log.WARN,TAG,"Native QS media artwork hooks incomplete",unavailable);return false;
        }
    }

    private boolean installTilePageEffects(ClassLoader loader) {
        boolean drawing = false;
        try {
            for (String name : new String[]{
                    "com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView",
                    "androidx.viewpager.widget.ViewPager"}) try {
                Class<?> target = loader.loadClass(name);
                Method dispatch = target.getDeclaredMethod("dispatchDraw", Canvas.class);
                deoptimize(dispatch);
                installHook(HookGroup.TILES, dispatch, chain -> {
                    View view = (View) chain.getThisObject();
                    if (!tileDispatchLogged) { tileDispatchLogged = true;
                        moduleLog(Log.INFO, TAG, "Tile dispatch source: " + view.getClass().getName()); }
                    if (!TILE_EFFECTS.identifies(view)) return chain.proceed();
                    TILE_EFFECTS.draw(view, (Canvas) chain.getArg(0),
                            canvas -> { chain.proceed(new Object[]{canvas}); });
                    return null;
                });
                drawing = true;
            } catch (ClassNotFoundException | NoSuchMethodException unsupported) { }
            try {
                Class<?> manager = loader.loadClass("com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager");
                Method owner = manager.getMethod("getRecyclerView");
                for (Method caller : manager.getDeclaredMethods())
                    if (caller.getName().matches("scrollHorizontallyBy|scrollHorizontallyInternal|scrollVerticallyBy|scrollVerticallyInternal"))
                        deoptimize(caller);
                Method page = manager.getDeclaredMethod("onPageScrolled");
                deoptimize(page);
                installHook(HookGroup.TILES, page, chain -> {
                    Object result = chain.proceed();
                    if (!tileScrollLogged) { tileScrollLogged = true;
                        moduleLog(Log.INFO, TAG, "Tile page scroll callback: " + chain.getThisObject().getClass().getName()); }
                    TILE_EFFECTS.onLayoutManagerScroll(chain.getThisObject());
                    return result;
                });
                Method state = manager.getDeclaredMethod("onScrollStateChanged", Integer.TYPE);
                deoptimize(state);
                installHook(HookGroup.TILES, state, chain -> {
                    Object result = chain.proceed();
                    Object view = owner.invoke(chain.getThisObject());
                    if (!tileStateLogged && view instanceof View) { tileStateLogged = true;
                        moduleLog(Log.INFO, TAG, "Tile paging owner: " + view.getClass().getName() + " state=" + chain.getArg(0)); }
                    if (view instanceof View) TILE_EFFECTS.onScrollState((View) view, (Integer) chain.getArg(0));
                    return result;
                });
            } catch (ClassNotFoundException | NoSuchMethodException unsupported) { }
            for (String name : new String[]{"com.android.systemui.qs.deprecated.PagedTileLayout", "com.android.systemui.qs.PagedTileLayout"}) try {
                Class<?> target = loader.loadClass(name);
                for (Method event : target.getDeclaredMethods()) {
                    if (event.getName().equals("onPageScrolled") && event.getParameterCount() == 3
                            && event.getParameterTypes()[1] == Float.TYPE) {
                        deoptimize(event);
                        installHook(HookGroup.TILES, event, chain -> {
                            Object result = chain.proceed();
                            TILE_EFFECTS.onPageScroll((View) chain.getThisObject(), (Float) chain.getArg(1));
                            return result;
                        });
                    } else if (event.getName().equals("onPageScrollStateChanged") && event.getParameterCount() == 1) {
                        deoptimize(event);
                        installHook(HookGroup.TILES, event, chain -> {
                            Object result = chain.proceed();
                            TILE_EFFECTS.onScrollState((View) chain.getThisObject(), (Integer) chain.getArg(0));
                            return result;
                        });
                    }
                }
            } catch (ClassNotFoundException unsupported) { }
            moduleLog(Log.INFO, TAG, drawing ? "Tile page edge fade and blur hooks installed"
                    : "Tile paging effects unavailable on this SystemUI");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not install tile paging effects", error);
            return false;
        }
    }

    private boolean installTextControls(ClassLoader loader) {
        try {
            // Classification follows real identity/hierarchy changes; global draw reuses its
            // weak positive/negative cache instead of resolving resource names each frame.
            for (Method event : new Method[]{View.class.getDeclaredMethod("setId", int.class),
                    View.class.getDeclaredMethod("assignParent", ViewParent.class)}) {
                deoptimize(event);
                installHook(HookGroup.TEXT, event, chain -> {
                    Object result = chain.proceed();
                    TEXT.classificationChanged((View) chain.getThisObject());
                    LOCKSCREEN.classificationChanged((View) chain.getThisObject());
                    observeLockscreenPlugin((View) chain.getThisObject());
                    return result;
                });
            }
            Method setText = TextView.class.getDeclaredMethod("setText", CharSequence.class, TextView.BufferType.class);
            // The dynamically loaded OEM clock calls the one-argument setter. Its compiled
            // wrapper may inline this final overload, so both native entries must be deoptimized.
            deoptimize(TextView.class.getDeclaredMethod("setText", CharSequence.class));
            deoptimize(setText);
            installHook(HookGroup.TEXT, setText, chain -> {
                TextView view = (TextView) chain.getThisObject();
                CharSequence input = LOCKSCREEN.formatDate(view, (CharSequence) chain.getArg(0));
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) {
                    return input == chain.getArg(0) ? chain.proceed()
                            : chain.proceed(new Object[]{input, chain.getArg(1)});
                }
                Object result;
                try (TextControls.CarrierTextScope scope = TEXT.beginCarrierText(view)) {
                    result = chain.proceed(new Object[]{TEXT.nativeText(view, input), chain.getArg(1)});
                }
                TEXT.attach(view);
                return result;
            });
            installHook(HookGroup.TEXT, View.class.getDeclaredMethod("setContentDescription", CharSequence.class), chain -> {
                View view = (View) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                return chain.proceed(new Object[]{TEXT.nativeDescription((TextView) view, (CharSequence) chain.getArg(0))});
            });
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setTextColor", Integer.TYPE), chain -> {
                TextView view = (TextView) chain.getThisObject();
                Object result;
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) result=chain.proceed();
                else result=chain.proceed(new Object[]{TEXT.nativeColor(view, (Integer) chain.getArg(0))});
                return result;
            });
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setTextColor", ColorStateList.class), chain -> {
                TextView view = (TextView) chain.getThisObject();
                ColorStateList state = (ColorStateList) chain.getArg(0);
                Object result;
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE || state==null) result=chain.proceed();
                else {
                    int nativeColor = state.getColorForState(view.getDrawableState(), state.getDefaultColor());
                    int styledColor = TEXT.nativeColor(view, nativeColor);
                    result=FEATURES.color(TEXT.group(view))?chain.proceed(new Object[]{ColorStateList.valueOf(styledColor)}):chain.proceed();
                }
                return result;
            });
            final Method sizeMethod = TextView.class.getDeclaredMethod("setTextSize", Integer.TYPE, Float.TYPE);
            installHook(HookGroup.TEXT, sizeMethod, chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                float pixels = TypedValue.applyDimension((Integer) chain.getArg(0), (Float) chain.getArg(1), view.getResources().getDisplayMetrics());
                TEXT.nativeSize(view, pixels);
                if (!TEXT.sizeControlsEnabled(view)) return chain.proceed();
                return chain.proceed(new Object[]{TypedValue.COMPLEX_UNIT_PX, TEXT.styledSize(view)});
            });
            for (Method method : TextView.class.getDeclaredMethods()) {
                if (!method.getName().equals("setTypeface") || method.getParameterCount() < 1
                        || method.getParameterTypes()[0] != Typeface.class) continue;
                installHook(HookGroup.TEXT, method, chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                    TEXT.enter();
                    Object result;
                    try { result = chain.proceed(); } finally { TEXT.exit(); }
                    TEXT.nativeTypeface(view, view.getTypeface());
                    TEXT.beforeMeasure(view);
                    return result;
                });
            }
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setFontVariationSettings", String.class), chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                TEXT.enter();
                Object result;
                try { result = chain.proceed(); } finally { TEXT.exit(); }
                if (Boolean.TRUE.equals(result)) {
                    TEXT.nativeFontVariation(view);
                    TEXT.beforeMeasure(view);
                }
                return result;
            });
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setLetterSpacing", Float.TYPE), chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                TEXT.nativeSpacing(view, (Float) chain.getArg(0));
                Object result = chain.proceed(); TEXT.beforeMeasure(view); return result;
            });
            installHook(HookGroup.TEXT, View.class.getDeclaredMethod("draw", Canvas.class), chain -> {
                View view = (View) chain.getThisObject();
                // Almost every view SystemUI draws belongs to none of these: one cached class lookup and straight through.
                int kinds = drawKinds(view.getClass());
                boolean text = view instanceof TextView;
                boolean surfaceWanted = C17_HIGHLIGHTS.optionsActive() && isHighlightSurface(view);
                if (kinds == 0 && !text && !surfaceWanted) return chain.proceed();
                C17HighlightRemoval.SurfaceScope surface = surfaceWanted ? C17_HIGHLIGHTS.beginSurface(view) : null;
                try {
                if ((kinds & DRAW_MEDIA) != 0) {
                    QS_MEDIA.drawMedia(view,(Canvas)chain.getArg(0),canvas->chain.proceed(new Object[]{canvas}));
                    return null;
                }
                if ((kinds & DRAW_DEVICE) != 0) {
                    QS_APPEARANCE.drawDeviceCard(view, (Canvas) chain.getArg(0),
                            canvas -> chain.proceed(new Object[]{canvas}));
                    return null;
                }
                if ((kinds & DRAW_CLEAR) != 0) {
                    CLEAR_APPEARANCE.drawButton(view, (Canvas) chain.getArg(0),
                            target -> chain.proceed(new Object[]{target}));
                    return null;
                }
                if (TEXT.kind(view) == TextControls.NONE) {
                    if (NotificationNativeClock.ownsLayer(view)) {
                        NotificationNativeClock.drawOwned(view, (Canvas) chain.getArg(0),
                                target -> chain.proceed(new Object[]{target}));
                        return null;
                    }
                    return chain.proceed();
                }
                Canvas canvas = (Canvas) chain.getArg(0); int saved = canvas.save();
                TEXT.beforeDraw((TextView) view, canvas);
                try {
                    Object drawn = chain.proceed();
                    // The two-row clock's lower row is drawn after the native upper row, on the same canvas.
                    TEXT.afterDraw((TextView) view, canvas);
                    return drawn;
                } finally { canvas.restoreToCount(saved); }
                } finally { if (surface != null) surface.close(); }
            });
            for (String name : new String[]{"onVisibilityChanged", "onWindowVisibilityChanged"}) {
                Method method = name.equals("onVisibilityChanged")
                        ? View.class.getDeclaredMethod(name, View.class, Integer.TYPE)
                        : View.class.getDeclaredMethod(name, Integer.TYPE);
                installHook(HookGroup.TEXT, method, chain -> {
                    Object result = chain.proceed();
                    View changed = (View) chain.getThisObject();
                    STATUS_FLOW.visibilityChanged(changed);
                    if (changed instanceof TextView && TEXT.kind(changed) != TextControls.NONE) TEXT.viewVisibilityChanged((TextView) changed);
                    return result;
                });
            }
            for (String name : new String[]{"onAttachedToWindow","onDetachedFromWindow"}) {
                final boolean attach=name.equals("onAttachedToWindow");
                installHook(HookGroup.TEXT,View.class.getDeclaredMethod(name),chain->{
                    Object result=chain.proceed();View view=(View)chain.getThisObject();
                    if(attach)observeLockscreenPlugin(view);
                    if(attach)LOCKSCREEN.attach(view);else LOCKSCREEN.detach(view);
                    if(QsTileAppearance.isDeviceCard(view)) {
                        if(attach){QS_APPEARANCE.refreshDeviceCard(view);C17_HIGHLIGHTS.refreshDeviceCard(view);}
                        else {QS_APPEARANCE.detach(view);C17_HIGHLIGHTS.detach(view);}
                    }
                    if(QsTileAppearance.type(view, NotificationClearAppearance.BUTTON_CLASS)) {
                        if(attach)CLEAR_APPEARANCE.bind(view);else { CLEAR_APPEARANCE.forget(view); CLEAR_MOTION.detach(view); }
                    }
                    if(!attach) { if(OVERFLOW_OWNERS.containsKey(view))releaseOverflow(view);TILE_EFFECTS.detach(view); }
                    if(view instanceof TextView&&TEXT.kind(view)!=TextControls.NONE) {
                        if(attach){initializeContext(view.getContext());TEXT.attach((TextView)view);allowDrawableOverflow(view);}
                        else {TEXT.detach((TextView)view);releaseOverflow(view);}
                    }
                    return result;
                });
            }
            for(String headerName:new String[]{"com.oplus.systemui.qs.OplusQuickStatusBarHeader",
                    "com.oplus.systemui.separate.OplusQSSimpleHeader","com.android.systemui.statusbar.phone.KeyguardStatusBarView"})try {
                Class<?> header=loader.loadClass(headerName);
                for(Method event:header.getDeclaredMethods()) {
                    if(!event.getName().matches("onFinishInflate|onConfigurationChanged|onAttachedToWindow"))continue;
                    deoptimize(event);
                    installHook(HookGroup.TEXT,event,chain->{
                        Object result=chain.proceed();View view=(View)chain.getThisObject();
                        TEXT.classificationChanged(view);
                        MAIN.post(()->scanHeaderText(view));return result;
                    });
                }
            }catch(ClassNotFoundException unsupported){}
            for (String name : new String[]{"com.android.keyguard.CarrierText",
                    "com.oplus.systemui.statusbar.widget.OplusStatCarrierText",
                    "com.oplus.systemui.statusbar.widget.OplusStatCarrierTextController"}) try {
                Class<?> carrier = loader.loadClass(name);
                for (Method event : carrier.getDeclaredMethods()) {
                    if (event.getName().matches("updateCarrierInfo|setVisible|onDensityOrFontScaleChanged|onConfigurationChanged|onVisibilityChanged"))
                        deoptimize(event);
                    if (TextView.class.isAssignableFrom(carrier) && event.getName().equals("onVisibilityChanged")) {
                        installHook(HookGroup.TEXT, event, chain -> {
                            Object result = chain.proceed(); TEXT.refresh(); return result;
                        });
                    } else if (TextView.class.isAssignableFrom(carrier) && event.getName().equals("onConfigurationChanged")) {
                        installHook(HookGroup.TEXT, event, chain -> {
                            Object result = chain.proceed(); TextView view = (TextView) chain.getThisObject();
                            TEXT.attach(view); allowDrawableOverflow(view); return result;
                        });
                    }
                }
            } catch (ClassNotFoundException unsupported) { }
            for (String name : new String[]{"com.oplus.systemui.statusbar.widget.StatClock",
                    "com.android.systemui.statusbar.policy.Clock", "com.oplus.systemui.qs.widget.OplusQSClock",
                    "com.oplus.systemui.qs.widget.SimpleQsClock", "com.oplus.systemui.qs.fake.view.QsClock",
                    "com.oplus.systemui.qs.widget.OplusSecondCarrierText"}) {
                Class<?> target;
                try { target = loader.loadClass(name); } catch (ClassNotFoundException absent) { continue; }
                for (Method caller : target.getDeclaredMethods()) {
                    if (Modifier.isAbstract(caller.getModifiers()) || Modifier.isStatic(caller.getModifiers())) continue;
                    if (caller.getName().matches("updateClock|onDarkChanged|onConfigurationChanged|setFontTypeface|updateTextColor|updateEverything|reloadDimens|onMeasure|calculateSecondTextMaxWidth|updateMinWidth.*"))
                        deoptimize(caller);
                    if (caller.getName().matches("onConfigurationChanged|updateConfigurationChanged|setFontTypeface")) {
                        deoptimize(caller);
                        installHook(HookGroup.TEXT, caller, chain -> {
                            TextControls.NativeStyleScope scope = TEXT.beginNativeStyle((TextView) chain.getThisObject());
                            try { return chain.proceed(); }
                            finally { if (scope != null) scope.close(); }
                        });
                    }
                }
                for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
                    try {
                        installHook(HookGroup.TEXT, target.getDeclaredMethod(event), chain -> {
                            Object result = chain.proceed(); TextView view = (TextView) chain.getThisObject();
                            if (event.equals("onAttachedToWindow")) {
                                initializeContext(view.getContext()); allowDrawableOverflow(view); TEXT.attach(view);
                            } else {TEXT.detach(view);releaseOverflow(view);}
                            return result;
                        });
                    } catch (NoSuchMethodException inherited) { }
                }
                if (name.endsWith("StatClock")) try {
                    final Method nativeMeasure = TextView.class.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE);
                    final Field actualWidth = NativeClockMeasurement.widthField(target);
                    installHook(HookGroup.TEXT, target.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE), chain -> {
                        TextView view = (TextView) chain.getThisObject();
                        Object result;
                        try (TextControls.NativeStyleScope scope = TEXT.beginClockMeasure(view)) { result = chain.proceed(); }
                        int nativeActualWidth = -1;
                        if (actualWidth != null) try { nativeActualWidth = actualWidth.getInt(view); }
                        catch (IllegalAccessException unavailable) { /* Diagnostic field is optional. */ }
                        TEXT.onNativeClockMeasure(view, (Integer) chain.getArg(0), view.getMeasuredWidth(), nativeActualWidth);
                        if (TEXT.clockControlsEnabled(view)) {
                            int widthSpec = TEXT.prepareClockMeasure(view, (Integer) chain.getArg(0));
                            result = getInvoker(nativeMeasure).invokeSpecial(view, widthSpec, chain.getArg(1));
                            if (actualWidth != null) try { actualWidth.setInt(view, view.getMeasuredWidth()); }
                            catch (IllegalAccessException unavailable) { /* Optional OEM cache must not abort measurement. */ }
                        }
                        TEXT.afterClockMeasure(view);
                        return result;
                    });
                } catch (NoSuchMethodException inheritedMeasure) {
                    moduleLog(Log.INFO,TAG,"Optional legacy clock measurement unavailable; native measurement retained");
                }
                if (name.equals("com.oplus.systemui.qs.widget.OplusQSClock")) try {
                    Method calculateWidth = target.getDeclaredMethod("calculateSecondTextMaxWidth");
                    installHook(HookGroup.TEXT, calculateWidth, chain -> {
                        TextView view = (TextView) chain.getThisObject();
                        if (!TEXT.clockControlsEnabled(view)) return chain.proceed();
                        TEXT.beforeMeasure(view);
                        Object width = chain.proceed();
                        // Native QS measurement writes directly into Paint. Restore the shared
                        // selected clock style and retain native digit-width, font-scale and animation math.
                        return TEXT.restoreClockWidth(view, ((Number) width).floatValue(), view.getTextSize());
                    });
                } catch (NoSuchMethodException unsupportedWidth) { }
            }
            try {
                Class<?> animated = loader.loadClass("com.oplus.systemui.qs.widget.AnimateChangeTextView");
                Class<?> carrier = loader.loadClass("com.oplus.systemui.qs.widget.OplusSecondCarrierText");
                Field carrierAppliedSize = optionalField(carrier, "mAppliedSizePx");
                installHook(HookGroup.TEXT, animated.getDeclaredMethod("setTextSize", Integer.TYPE, Float.TYPE), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    boolean internal = TEXT.isInternal();
                    if (TEXT.kind(view) != TextControls.CARRIER || !internal && !TEXT.sizeControlsEnabled(view)) return chain.proceed();
                    if (!internal) TEXT.nativeSize(view, TypedValue.applyDimension((Integer) chain.getArg(0),
                            (Float) chain.getArg(1), view.getResources().getDisplayMetrics()));
                    TEXT.enter();
                    try { return getInvoker(sizeMethod).invokeSpecial(view, TypedValue.COMPLEX_UNIT_PX,
                            internal && (Integer) chain.getArg(0) == TypedValue.COMPLEX_UNIT_PX
                                    ? (Float) chain.getArg(1) : TEXT.styledSize(view)); }
                    finally { TEXT.exit(); }
                });
                installHook(HookGroup.TEXT, animated.getDeclaredMethod("setTextWithAnima", CharSequence.class, Float.TYPE), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (TEXT.isInternal() || !TEXT.replaces(view) || !TEXT.sizeControlsEnabled(view)) return chain.proceed();
                    float nativePixels = (Float) chain.getArg(1);
                    if ((!Float.isFinite(nativePixels) || nativePixels <= 0f) && carrier.isInstance(view)
                            && carrierAppliedSize != null) try { nativePixels = carrierAppliedSize.getFloat(view); }
                    catch (IllegalAccessException | RuntimeException unavailable) { /* Preserve the captured native baseline. */ }
                    if (Float.isFinite(nativePixels) && nativePixels > 0f) TEXT.nativeSize(view, nativePixels);
                    view.setText((CharSequence) chain.getArg(0)); TEXT.attach(view); return null;
                });
                installHook(HookGroup.TEXT, carrier.getDeclaredMethod("updateTextSize$1"), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (TEXT.isInternal()) return chain.proceed();
                    // OEM target size is the native baseline; its maximum is only an upper bound.
                    TextControls.NativeStyleScope scope = TEXT.beginNativeStyle(view);
                    try { return chain.proceed(); }
                    finally {
                        if (scope != null) {
                            if (carrierAppliedSize != null) try { scope.carrierPixels(carrierAppliedSize.getFloat(view)); }
                            catch (IllegalAccessException | RuntimeException unavailable) { /* Scope still releases in all cases. */ }
                            scope.close();
                        }
                    }
                });
            } catch (ClassNotFoundException | NoSuchMethodException absent) {
                moduleLog(Log.INFO,TAG,"Optional animated carrier measurement unavailable; common text hooks retained");
            }
            try {
                Class<?> callback=loader.loadClass("com.oplus.systemui.qs.widget.OplusSecondCarrierText$1");
                Class<?> carrier=loader.loadClass("com.oplus.systemui.qs.widget.OplusSecondCarrierText");
                Field owner=optionalField(callback,"this$0"),applied=optionalField(carrier,"mAppliedSizePx"),text=optionalField(carrier,"mCarrierText");
                for(Method method:callback.getDeclaredMethods())if(method.getName().equals("updateCarrierInfo")) {
                    deoptimize(method);
                    if(owner==null||applied==null||text==null)continue;
                    installHook(HookGroup.TEXT,method,chain->{
                        Object target=owner.get(chain.getThisObject());
                        if(!(target instanceof TextView)||TEXT.isInternal())return chain.proceed();
                        TextView view=(TextView)target;
                        TextControls.NativeStyleScope scope=TEXT.beginNativeStyle(view);
                        try{return chain.proceed();}
                        finally{
                            if(scope!=null){
                                try{
                                    scope.carrierPixels(applied.getFloat(view));
                                    Object nativeText=text.get(view);
                                    if(nativeText==null||nativeText instanceof CharSequence)scope.carrierText((CharSequence)nativeText);
                                }catch(IllegalAccessException|RuntimeException unavailable){/* Scope must still release. */}
                                scope.close();
                            }
                        }
                    });
                }
            }catch(ClassNotFoundException absent){}
            installLockscreenControls(loader);
            moduleLog(Log.INFO, TAG, "Status clock and carrier text controls installed");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Text controls incomplete; retry at next package phase", error); return false;
        }
    }

    private void installLockscreenControls(ClassLoader loader) throws NoSuchMethodException {
        // The lock is a native FrameLayout, not a TextView or the fingerprint target.
        // Suppress only its decorative children, retaining native input and unlocking state.
        lazyHook("lock_hide", LOCKSCREEN::hidesLock, () -> {
            Method drawChild = ViewGroup.class.getDeclaredMethod("drawChild", Canvas.class, View.class, Long.TYPE);
            deoptimize(drawChild);
            installHook(HookGroup.TEXT, drawChild, chain -> LOCKSCREEN.hideLockChild(
                    (ViewGroup) chain.getThisObject(), (View) chain.getArg(1)) ? false : chain.proceed());
        });
        for (String name : new String[]{LockscreenControls.SINGLE, LockscreenControls.DUAL,
                LockscreenControls.RED, LockscreenControls.CUSTOM, LockscreenControls.LOCK}) try {
            Class<?> owner = loader.loadClass(name);
            if (!View.class.isAssignableFrom(owner)) continue;
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                installHook(HookGroup.TEXT, constructor, chain -> {
                    Object result = chain.proceed(); LOCKSCREEN.attach((View) chain.getThisObject()); return result;
                });
            }
            for (Method event : owner.getDeclaredMethods()) {
                if (Modifier.isAbstract(event.getModifiers()) || Modifier.isStatic(event.getModifiers())) continue;
                if (!event.getName().matches("onFinishInflate|onAttachedToWindow|onConfigChanged|onConfigurationChanged"
                        + "|onMovedToDisplay|updateDate|updateTime|refresh|refreshDate|updateIcon|updateColorAndBackgroundVisibility")) continue;
                deoptimize(event);
                installHook(HookGroup.TEXT, event, chain -> {
                    Object result = chain.proceed(); LOCKSCREEN.attach((View) chain.getThisObject()); return result;
                });
            }
        } catch (ClassNotFoundException unsupportedClock) {
            // Native clocks differ between firmware/theme packages; leave missing variants native.
        }
    }

    private void observeLockscreenPlugin(View view) {
        if(view==null)return;
        String viewClass=view.getClass().getName();
        if(viewClass.startsWith("com.oplus.keyguard.style.widgets.")) return;
        if(!viewClass.startsWith("com.oplus.keyguard.clock.")) {
            if(viewClass.equals(LockscreenControls.SINGLE)||viewClass.equals(LockscreenControls.DUAL)
                    ||viewClass.equals(LockscreenControls.RED)||viewClass.equals(LockscreenControls.CUSTOM)) {
                synchronized(SYSTEMUI_HOOK_LOCK){if(weatherDiagnosticView.get()!=view)weatherDiagnosticView=new WeakReference<>(view);}
                if(!ModuleDiagnostics.enabled())return;
                boolean first;synchronized(SYSTEMUI_HOOK_LOCK){
                    first=WEATHER_DIAGNOSTIC_VIEWS.size()<16&&WEATHER_DIAGNOSTIC_VIEWS.add(viewClass);}
                if(first)weatherDiagnosticView(view,"native_attach");
            }
            return;
        }
        ClassLoader loader=view.getClass().getClassLoader();if(loader==null)return;
        synchronized(SYSTEMUI_HOOK_LOCK){if(weatherDiagnosticView.get()!=view)weatherDiagnosticView=new WeakReference<>(view);}
        if(ModuleDiagnostics.enabled()) {
            boolean first;synchronized(SYSTEMUI_HOOK_LOCK){
                first=WEATHER_DIAGNOSTIC_VIEWS.size()<16&&WEATHER_DIAGNOSTIC_VIEWS.add(view.getClass().getName());
            }
            if(first)weatherDiagnosticView(view,"attach");
        }
        synchronized(SYSTEMUI_HOOK_LOCK){if(!KEYGUARD_PLUGIN_LOADERS.add(loader))return;}
        int weatherHooks=0;
        try {
            try {
                Method weather=loader.loadClass(SystemWeatherText.CONTROLLER).getDeclaredMethod("sendWeatherData");
                deoptimize(weather);
                installHook(HookGroup.TEXT,weather,chain->{
                    Object result=chain.proceed();
                    boolean changed=SystemWeatherText.captureController(chain.getThisObject());
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();
                    weatherDiagnostic("send",chain.getThisObject(),changed);
                    return result;
                });
                weatherHooks|=1;
            }catch(ReflectiveOperationException optionalWeather){ /* Unknown weather shapes keep the text fallback. */ }
            try {
                Method cachedWeather=loader.loadClass(SystemWeatherText.CONTROLLER).getDeclaredMethod("getCurrentData");
                if(SystemWeatherText.DATA.equals(cachedWeather.getReturnType().getName())) {
                    deoptimize(cachedWeather);
                    installHook(HookGroup.TEXT,cachedWeather,chain->{
                        Object result=chain.proceed();
                        boolean changed=SystemWeatherText.captureRead(chain.getThisObject(),result);
                        if(changed)BIG_CLOCK.onSystemWeatherChanged();
                        weatherDiagnostic("read",chain.getThisObject(),changed);
                        return result;
                    });
                    weatherHooks|=2;
                }
            }catch(ReflectiveOperationException optionalCache){ /* Native update callback remains available. */ }
            try {
                Class<?> controller=loader.loadClass(SystemWeatherText.CONTROLLER);
                Method delivered=controller.getDeclaredMethod("sendWeatherData$lambda$15",controller);
                deoptimize(delivered);installHook(HookGroup.TEXT,delivered,chain->{Object result=chain.proceed();
                    boolean changed=SystemWeatherText.captureController(chain.getArg(0));
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("delivery",chain.getArg(0),changed);return result;});
                weatherHooks|=4;
            }catch(ReflectiveOperationException optionalDelivery){ /* Exact native sender/getter remain observed. */ }
            for(String managerName:new String[]{"com.oplus.keyguard.clock.common.util.ExtraInfoManager",
                    "com.oplus.keyguard.clock.base.util.ExtraInfoManager"})try {
                Class<?> manager=loader.loadClass(managerName);
                for(Constructor<?> constructor:manager.getDeclaredConstructors())installHook(HookGroup.TEXT,constructor,chain->{
                    Object result=chain.proceed();boolean changed=SystemWeatherText.captureManager(chain.getThisObject());
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("manager_ctor",chain.getThisObject(),changed);return result;});
                for(Method method:manager.getDeclaredMethods())if(!Modifier.isStatic(method.getModifiers())
                        &&method.getName().matches("getAndListenWeather|initWeatherEffectController")) {
                    deoptimize(method);installHook(HookGroup.TEXT,method,chain->{Object result=chain.proceed();
                        boolean changed=SystemWeatherText.captureManager(chain.getThisObject());
                        if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("manager",chain.getThisObject(),changed);return result;});
                }
                weatherHooks|=managerName.equals(SystemWeatherText.COMMON_MANAGER)?8:16;
            }catch(ReflectiveOperationException optionalManager){ /* No new native weather owner is created. */ }
            for(String modelName:new String[]{"com.oplus.keyguard.clock.base.domain.model.DateMessageViewModel",
                    "com.oplus.keyguard.clock.gallery.domain.model.ExtraMessageViewModel"})try {
                Class<?> model=loader.loadClass(modelName);
                for(Method method:model.getDeclaredMethods())if(!Modifier.isStatic(method.getModifiers())
                        &&method.getName().matches("getExtraInfoManager|setExtWeather")) {
                    deoptimize(method);installHook(HookGroup.TEXT,method,chain->{Object result=chain.proceed();
                        boolean changed=method.getName().equals("getExtraInfoManager")?SystemWeatherText.captureManager(result)
                                :SystemWeatherText.captureViewModel(chain.getThisObject());
                        if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("model",chain.getThisObject(),changed);return result;});
                }
                weatherHooks|=modelName.equals(SystemWeatherText.BASE_MODEL)?32:64;
            }catch(ReflectiveOperationException optionalModel){ /* Read only an existing native lazy value. */ }
            int updates=0;
            for(String name:new String[]{LockscreenControls.PLUGIN_DATE,LockscreenControls.PLUGIN_BASE_DATE})try {
                Class<?> type=loader.loadClass(name);
                for(Method method:type.getDeclaredMethods()) {
                    if(Modifier.isStatic(method.getModifiers())||Modifier.isAbstract(method.getModifiers()))continue;
                    if(!method.getName().matches("onFinishInflate|onAttachedToWindow|onConfigurationChanged|render|refreshDateMessageContent|updateDateContent"))continue;
                    deoptimize(method);
                    installHook(HookGroup.TEXT,method,chain->{
                        Object owner=chain.getThisObject();LOCKSCREEN.onPluginClockUpdated(owner);
                        Object result=chain.proceed();LOCKSCREEN.onPluginClockUpdated(owner);return result;
                    });
                    updates++;
                }
                // A suspended native render resumes in its generated continuation.
                try{for(Method caller:loader.loadClass(name+"$render$1").getDeclaredMethods())
                    if(caller.getName().equals("invokeSuspend"))deoptimize(caller);}
                catch(ClassNotFoundException synchronous){ }
            }catch(ClassNotFoundException optionalStyle){ }
            for(String name:new String[]{LockscreenControls.PLUGIN_DIGITAL_TEXT,LockscreenControls.PLUGIN_BASE_TEXT,
                    LockscreenControls.PLUGIN_HDR_TEXT})try {
                Class<?> type=loader.loadClass(name);boolean hdr=name.equals(LockscreenControls.PLUGIN_HDR_TEXT);
                Method writer=hdr?type.getDeclaredMethod("setText",CharSequence.class,TextView.BufferType.class)
                        :type.getDeclaredMethod("tryUpdateText",CharSequence.class);
                deoptimize(writer);
                installHook(HookGroup.TEXT,writer,chain->{
                    CharSequence input=(CharSequence)chain.getArg(0);
                    try(LockscreenControls.NativeDateWrite scope=LOCKSCREEN.beginPluginDateWrite((TextView)chain.getThisObject(),input)) {
                        if(scope==null||scope.text()==input)return chain.proceed();
                        Object[] args=chain.getArgs().toArray();args[0]=scope.text();return chain.proceed(args);
                    }
                });
            }catch(ClassNotFoundException optionalStyle){ }
            // The lock clock's own glass builder: remembered, never changed, so the notification page's clock can be given the same material.
            try {
                Method glassBuild=loader.loadClass(LockGlassBridge.BUILDER).getDeclaredMethod("buildRenderEffect");
                deoptimize(glassBuild);
                installHook(HookGroup.TEXT,glassBuild,chain->{
                    Object result=chain.proceed();LockGlassBridge.captured(chain.getThisObject());return result;
                });
            }catch(ReflectiveOperationException optionalGlass){ }
            moduleLog(Log.INFO,TAG,"Native keyguard plugin date writers connected; update entries "+updates);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            synchronized(SYSTEMUI_HOOK_LOCK){KEYGUARD_PLUGIN_LOADERS.remove(loader);}
            moduleLog(Log.WARN,TAG,"Native keyguard plugin date hooks unavailable; preserving native dates",unavailable);
        }
        synchronized(SYSTEMUI_HOOK_LOCK){WEATHER_DIAGNOSTIC_HOOKS.put(loader,weatherHooks);}
        if(ModuleDiagnostics.enabled())weatherDiagnosticView(view,"installed");
    }

    /** The weather card is a separate dynamically loaded widget APK. Its
     * SDK cache and pending native renderer payload are observed in place. */
    private void observeWeatherWidgets(View view) {
        ClassLoader loader=view.getClass().getClassLoader();if(loader==null)return;
        boolean install;synchronized(SYSTEMUI_HOOK_LOCK){install=WEATHER_WIDGET_LOADERS.add(loader);}
        if(install) {
            int hooks=0;
            try {
                Class<?> card=loader.loadClass(SystemWeatherText.WIDGET_CARD),data=loader.loadClass("pantanal.app.bean.PantanalUIData");
                Method delivered=requiredMethod(card,"a",card,data);deoptimize(delivered);
                deoptimize(requiredMethod(loader.loadClass("kswc.kH"),"invoke",Object.class));
                installHook(HookGroup.TEXT,delivered,chain->{Object result=chain.proceed();
                    boolean changed=SystemWeatherText.captureWidgetCard(chain.getArg(0));
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("widget_update",chain.getArg(0),changed);return result;});
                hooks|=1;
                Method consumed=requiredMethod(loader.loadClass(SystemWeatherText.WIDGET_ENGINE),"k");deoptimize(consumed);
                deoptimize(requiredMethod(loader.loadClass(SystemWeatherText.WIDGET_ENGINE),"f"));
                deoptimize(requiredMethod(loader.loadClass("kswc.DN"),"run"));
                installHook(HookGroup.TEXT,consumed,chain->{
                    boolean changed=SystemWeatherText.captureWidgetEngine(chain.getThisObject());
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();weatherDiagnostic("widget_render",chain.getThisObject(),changed);
                    return chain.proceed();});
                hooks|=2;
                Class<?> nativeView=loader.loadClass(SystemWeatherText.WIDGET_VIEW),widget=loader.loadClass("com.oplus.keyguard.style.widgets.container.data.InstantCardWidget");
                Method bound=requiredMethod(nativeView,"access$setInstantCardWidget$p",nativeView,widget);deoptimize(bound);
                deoptimize(requiredMethod(loader.loadClass(SystemWeatherText.WIDGET_VIEW+"$bindView$1$1"),"invoke",widget));
                installHook(HookGroup.TEXT,bound,chain->{Object result=chain.proceed();
                    boolean changed=SystemWeatherText.captureWidgetView(chain.getArg(0));
                    if(changed)BIG_CLOCK.onSystemWeatherChanged();
                    if(chain.getArg(0) instanceof View){installWeatherInstantHooks((View)chain.getArg(0));weatherWidgetDiagnostic((View)chain.getArg(0),"widget_bound",false);}return result;});
                hooks|=4;
            } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
                moduleLog(Log.INFO,TAG,"Existing lock-screen widget weather cache unavailable; retaining weather placeholder",unavailable);
            }
            try{
                // A bound card may not have its asynchronous frontend yet.
                // The native first successful render supplies the missing
                // lifecycle event; it is not a per-frame callback or a query.
                Method ready=requiredMethod(loader.loadClass("kswc.Ys"),"onRenderSuccess");deoptimize(ready);
                deoptimize(requiredMethod(loader.loadClass("com.oplus.utrace.utils.a"),"run"));
                installHook(HookGroup.TEXT,ready,chain->{Object result=chain.proceed();queueWeatherWidgetRefresh();return result;});hooks|=8;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){moduleLog(Log.INFO,TAG,"Native weather first-render observer unavailable; settings events retain cache recovery",unavailable);}
            synchronized(SYSTEMUI_HOOK_LOCK){WEATHER_WIDGET_HOOKS.put(loader,hooks);}
            if(ModuleDiagnostics.enabled())ModuleDiagnostics.fact("weather","weather","原生天气卡缓存入口",
                    "event","widget_install","owner_class",view.getClass().getName(),"loader_id",System.identityHashCode(loader),
                    "loader_known",true,"hook_mask",hooks,"snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty(),"revision",SystemWeatherText.revision());
        }
        if(view.getClass().getName().equals("com.oplus.keyguard.style.widgets.container.view.CustomConstraintLayout")) {
            synchronized(SYSTEMUI_HOOK_LOCK){if(WEATHER_WIDGET_ROOTS.size()<4||WEATHER_WIDGET_ROOTS.containsKey(view))WEATHER_WIDGET_ROOTS.put(view,Boolean.TRUE);}
        }
        if(view.getClass().getName().equals(SystemWeatherText.WIDGET_VIEW)) {
            synchronized(SYSTEMUI_HOOK_LOCK){if(WEATHER_WIDGET_VIEWS.size()<4||WEATHER_WIDGET_VIEWS.containsKey(view))WEATHER_WIDGET_VIEWS.put(view,Boolean.TRUE);}
            boolean changed=SystemWeatherText.captureWidgetView(view);if(changed)BIG_CLOCK.onSystemWeatherChanged();
            installWeatherInstantHooks(view);
            weatherWidgetDiagnostic(view,"widget_attach",false);
        }
    }

    /** Installed only from the already bound native engine's actual loader.
     * updateAttrs supplies its own document; unrelated cards are one identity
     * lookup and never trigger reflection or a tree walk. */
    private void installWeatherInstantHooks(View view){
        ClassLoader loader=SystemWeatherText.instantRendererLoader(view);if(loader==null)return;
        synchronized(SYSTEMUI_HOOK_LOCK){if(!WEATHER_INSTANT_LOADERS.add(loader))return;}
        int hooks=0;
        try{
            String prefix="a.a.a.v110017.";Class<?> applier=loader.loadClass(prefix+"ip0"),document=loader.loadClass(prefix+"bp0"),
                    action=loader.loadClass(prefix+"jp0"),renderContext=loader.loadClass(prefix+"YC"),callback=loader.loadClass(prefix+"w90");
            Method update=requiredMethod(applier,"g",document,action,renderContext,Context.class,callback);deoptimize(update);
            deoptimize(requiredMethod(applier,"b",renderContext,Context.class,loader.loadClass(prefix+"cB"),action,document,callback));
            installHook(HookGroup.TEXT,update,chain->{Object result=chain.proceed();
                if(SystemWeatherText.relevantInstantUpdate(chain.getArg(0),chain.getArg(1)))queueInstantWeather(chain.getArg(0));return result;});hooks=1;
            Method image=requiredMethod(loader.loadClass(prefix+"ey"),"u",android.graphics.drawable.Drawable.class);deoptimize(image);
            installHook(HookGroup.TEXT,image,chain->{Object result=chain.proceed();
                Object imageDocument=SystemWeatherText.instantImageDocument(chain.getThisObject());
                if(imageDocument!=null)queueInstantWeather(imageDocument);return result;});hooks|=2;
            // SVG recording is populated by its real bounds, possibly after
            // image delivery. Native theme tint can then replace the recording.
            Class<?> picture=loader.loadClass(prefix+"xi0");
            for(Method changed:new Method[]{requiredMethod(picture,"onBoundsChange",android.graphics.Rect.class),requiredMethod(picture,"a",Integer.TYPE)}) {
                deoptimize(changed);installHook(HookGroup.TEXT,changed,chain->{Object result=chain.proceed();
                    Object imageDocument=SystemWeatherText.instantPictureDocument(chain.getThisObject());
                    if(imageDocument!=null)queueInstantWeather(imageDocument);return result;});
            }
            hooks|=4;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){moduleLog(Log.INFO,TAG,"Native weather attributes unavailable; preserving last readable weather",unavailable);}
        synchronized(SYSTEMUI_HOOK_LOCK){WEATHER_INSTANT_HOOKS.put(loader,hooks);if(hooks==0)WEATHER_INSTANT_LOADERS.remove(loader);}
        if(ModuleDiagnostics.enabled())ModuleDiagnostics.fact("weather","weather","原生天气渲染属性入口",
                "event","instant_install","owner_class",view.getClass().getName(),"loader_id",System.identityHashCode(loader),"hook_mask",hooks,
                "snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty(),"native_icon_ready",SystemWeatherText.snapshot().weatherIcon!=null,"revision",SystemWeatherText.revision());
    }
    private static void queueInstantWeather(Object document){
        if(!SystemWeatherText.observedInstantDocument(document))return;
        synchronized(SYSTEMUI_HOOK_LOCK){
            if(WEATHER_INSTANT_PENDING.size()<4||WEATHER_INSTANT_PENDING.containsKey(document))WEATHER_INSTANT_PENDING.put(document,Boolean.TRUE);
            if(weatherInstantQueued)return;weatherInstantQueued=true;
        }
        MAIN.post(()->{
            java.util.ArrayList<Object> documents;synchronized(SYSTEMUI_HOOK_LOCK){documents=new java.util.ArrayList<>(WEATHER_INSTANT_PENDING.keySet());WEATHER_INSTANT_PENDING.clear();weatherInstantQueued=false;}
            boolean changed=false;for(Object pending:documents)if(pending!=null)changed|=SystemWeatherText.captureInstantDocument(pending);
            if(changed){BIG_CLOCK.onSystemWeatherChanged();if(ModuleDiagnostics.enabled())ModuleDiagnostics.fact("weather","weather","原生天气属性已更新",
                    "event","instant_update","snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty(),"revision",SystemWeatherText.revision());}
        });
    }
    private static void queueWeatherWidgetRefresh(){
        synchronized(SYSTEMUI_HOOK_LOCK){if(weatherWidgetRefreshQueued)return;weatherWidgetRefreshQueued=true;}
        MAIN.post(()->{synchronized(SYSTEMUI_HOOK_LOCK){weatherWidgetRefreshQueued=false;}weatherWidgetProbe("widget_render_ready");});
    }

    private static void weatherWidgetDiagnostic(View view,String event,boolean force) {
        if(!ModuleDiagnostics.enabled()||view==null)return;
        SystemWeatherText.WidgetDiagnostic state=SystemWeatherText.diagnoseWidgetView(view);
        ClassLoader renderer=SystemWeatherText.instantRendererLoader(view);int installed;
        synchronized(SYSTEMUI_HOOK_LOCK){Integer widgetHooks=WEATHER_WIDGET_HOOKS.get(view.getClass().getClassLoader()),nativeHooks=WEATHER_INSTANT_HOOKS.get(renderer);
            installed=(widgetHooks==null?0:widgetHooks)|(nativeHooks!=null&&nativeHooks==1?16:0);}
        int flags=(state.viewShape?1:0)|(state.bindingPresent?2:0)|(state.cardPresent?4:0)|(state.cardShape?8:0)|(state.infoPresent?16:0)
                |(state.serviceMatch?32:0)|(state.lockEntrance?64:0)|(state.localCard?128:0)|(state.cachePresent?256:0)
                |(state.enginePresent?512:0)|(state.pendingPresent?1024:0)|(state.renderReady?2048:0)|(state.payloadPresent?4096:0)
                |(state.jsonReady?8192:0)|(state.pageCurrent?16384:0)|(state.rootFields?32768:0)|(state.dataFields?65536:0);
        String signature=state.reason+":"+state.imageNodes+":"+state.iconNodes+":"+state.imageSource+":"+state.imageStage+":"+state.imageDetail+":"+state.cardClass+":"+flags+":"+state.payloadBytes+":"+view.isAttachedToWindow()
                +":"+state.engineClass+":"+state.innerClass+":"+state.serviceClass+":"+state.weatherPackage
                +":"+state.cardDataPresent+":"+state.initDataPresent+":"+state.instantUriPresent+":"+state.payloadSource
                +":"+state.rendererKnown+":"+state.preview+":"+state.pageKnown+":"+state.documentKnown+":"+state.nodeCount+":"+state.temperatureNode+":"+state.descriptionNode+":"+installed;
        synchronized(SYSTEMUI_HOOK_LOCK) {
            if(!force&&signature.equals(WEATHER_WIDGET_DIAGNOSTIC_STATES.get(view)))return;
            if(WEATHER_WIDGET_DIAGNOSTIC_STATES.size()<8||WEATHER_WIDGET_DIAGNOSTIC_STATES.containsKey(view))WEATHER_WIDGET_DIAGNOSTIC_STATES.put(view,signature);
        }
        ModuleDiagnostics.fact("weather","weather_widget","既有天气卡绑定与缓存状态",
                "event",event,"reason",state.reason,"owner_class",view.getClass().getName(),"card_class",state.cardClass,
                "hook_mask",installed,
                "loader_id",System.identityHashCode(view.getClass().getClassLoader()),"attached",view.isAttachedToWindow(),
                "widget_view_shape",state.viewShape,"widget_binding",state.bindingPresent,"widget_card",state.cardPresent,
                "widget_card_shape",state.cardShape,"widget_info",state.infoPresent,"widget_service",state.serviceMatch,
                "widget_lock",state.lockEntrance,"widget_local",state.localCard,"widget_cache",state.cachePresent,
                "widget_engine",state.enginePresent,"widget_pending",state.pendingPresent,"widget_render_ready",state.renderReady,
                "widget_payload",state.payloadPresent,"widget_payload_bytes",state.payloadBytes,"widget_json",state.jsonReady,
                "widget_current_page",state.pageCurrent,"widget_root_fields",state.rootFields,"widget_data_fields",state.dataFields,
                "engine_class",state.engineClass,"inner_class",state.innerClass,"service_class",state.serviceClass,
                "widget_weather_package",state.weatherPackage,"widget_card_data",state.cardDataPresent,"widget_init_data",state.initDataPresent,
                "widget_instant_uri",state.instantUriPresent,"widget_payload_source",state.payloadSource,
                "widget_image_nodes",state.imageNodes,"widget_icon_nodes",state.iconNodes,"widget_icon_source",state.imageSource,"widget_image_stage",state.imageStage,"widget_image_detail",state.imageDetail,
                "widget_renderer",state.rendererKnown,"widget_preview",state.preview,"widget_page_known",state.pageKnown,
                "widget_document",state.documentKnown,"widget_node_count",state.nodeCount,"widget_temperature_node",state.temperatureNode,"widget_description_node",state.descriptionNode,
                "snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty(),"native_icon_ready",SystemWeatherText.snapshot().weatherIcon!=null,"revision",SystemWeatherText.revision());
    }

    /** Native-ready/settings/probe events reconcile the already bound cache.
     * Diagnostics remain read-only reports after that normal cache delivery.
     * One bounded walk recovers views whose attach callback was optimized. */
    private static void weatherWidgetProbe(String event) {
        java.util.Set<View> seen=Collections.newSetFromMap(new java.util.IdentityHashMap<View,Boolean>());
        java.util.ArrayList<View> roots,views;
        synchronized(SYSTEMUI_HOOK_LOCK){roots=new java.util.ArrayList<>(WEATHER_WIDGET_ROOTS.keySet());views=new java.util.ArrayList<>(WEATHER_WIDGET_VIEWS.keySet());}
        for(View view:views)if(view!=null&&seen.add(view))refreshWeatherWidget(view,event);
        int[] budget={128};for(View root:roots)weatherWidgetProbe(root,event,seen,budget,0);
        if(seen.isEmpty()&&ModuleDiagnostics.enabled())ModuleDiagnostics.fact("weather","weather_widget","既有天气卡绑定与缓存状态",
                "event",event,"reason","widget_view_unobserved","count",roots.size(),"snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty());
    }
    private static void weatherWidgetProbe(View view,String event,java.util.Set<View> seen,int[] budget,int depth) {
        if(view==null||depth>12||budget[0]--<=0)return;
        if(view.getClass().getName().equals(SystemWeatherText.WIDGET_VIEW)) {
            if(seen.add(view))refreshWeatherWidget(view,event);return;
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount()&&budget[0]>0;i++)weatherWidgetProbe(group.getChildAt(i),event,seen,budget,depth+1);
        }
    }
    private static void refreshWeatherWidget(View view,String event){
        boolean changed=SystemWeatherText.captureWidgetView(view);
        StatusBarModule running=instance;if(running!=null)running.installWeatherInstantHooks(view);
        if(changed)BIG_CLOCK.onSystemWeatherChanged();
        weatherWidgetDiagnostic(view,event,true);
    }

    /** Opt-in event/probe diagnostics. These reads never capture weather or
     * construct a native owner. Repeated native cache reads do no reflection. */
    private static void weatherDiagnostic(String event,Object owner,boolean force) {
        if(!ModuleDiagnostics.enabled())return;
        Object selected=owner;
        synchronized(SYSTEMUI_HOOK_LOCK) {
            if(selected==null)selected=weatherDiagnosticOwner.get();
            long revision=SystemWeatherText.revision();
            Long seen=WEATHER_DIAGNOSTIC_REVISIONS.get(selected);
            if(!force&&seen!=null&&seen==revision)return;
            if(selected!=null) {
                if(WEATHER_DIAGNOSTIC_REVISIONS.size()>=8&&!WEATHER_DIAGNOSTIC_REVISIONS.containsKey(selected))return;
                WEATHER_DIAGNOSTIC_REVISIONS.put(selected,revision);
            } else if(!force&&weatherDiagnosticRevision==revision)return;
            if(selected!=null)weatherDiagnosticOwner=new WeakReference<>(selected);
            weatherDiagnosticRevision=revision;
        }
        SystemWeatherText.DiagnosticState state=SystemWeatherText.diagnose(selected);
        ModuleDiagnostics.fact("weather","weather","原生天气缓存状态",
                "event",event,"reason",state.reason,"owner_class",state.ownerClass,
                "controller_known",state.controllerKnown,"controller_shape",state.controllerShape,
                "located",state.located,"alive",state.alive,"data_present",state.dataPresent,"data_shape",state.dataShape,
                "description_present",state.descriptionPresent,"temperature_present",state.temperaturePresent,
                "snapshot_ready",state.snapshotReady,"native_icon_ready",state.nativeIconReady,"revision",state.revision,"snapshot_source",state.source);
        MAIN.post(()->weatherDiagnosticSlots(event));
    }

    private static void weatherDiagnosticView(View view,String event) {
        if(!ModuleDiagnostics.enabled()||view==null)return;
        ClassLoader loader=view.getClass().getClassLoader();int mask;boolean known;
        synchronized(SYSTEMUI_HOOK_LOCK){Integer installed=WEATHER_DIAGNOSTIC_HOOKS.get(loader);mask=installed==null?0:installed;known=KEYGUARD_PLUGIN_LOADERS.contains(loader);}
        ModuleDiagnostics.fact("weather","weather","原生活动时钟与天气入口",
                "event",event,"owner_class",view.getClass().getName(),"loader_id",System.identityHashCode(loader),
                "loader_known",known,"hook_mask",mask,"snapshot_ready",!SystemWeatherText.snapshot().description.isEmpty(),
                "revision",SystemWeatherText.revision());
    }

    private static Object weatherDiagnosticField(Object owner,String name)throws ReflectiveOperationException {
        if(owner==null)return null;
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
        } catch(NoSuchFieldException next) { }
        throw new NoSuchFieldException(name);
    }

    private static void weatherDiagnosticSlots(String event) {
        if(!ModuleDiagnostics.enabled())return;
        boolean overlayReady=false,componentsReady=false;boolean[] enabled=new boolean[2],weather=new boolean[2],placeholder=new boolean[2],matches=new boolean[2];
        String reason="overlay_empty";SystemWeatherText.Snapshot snapshot=SystemWeatherText.snapshot();
        try {
            Object overlay=weatherDiagnosticField(BIG_CLOCK,"overlay");
            overlayReady=overlay instanceof View&&((View)overlay).isAttachedToWindow();
            Object components=weatherDiagnosticField(overlay,"components");componentsReady=components instanceof View;
            reason=componentsReady?"ok":"components_empty";
            if(componentsReady) {
                Object[] slots=(Object[])weatherDiagnosticField(components,"slots");
                for(int i=0;i<Math.min(2,slots.length);i++) {
                    Object slot=slots[i];Object text=weatherDiagnosticField(slot,"view");enabled[i]=text instanceof TextView;
                    weather[i]=(((Number)weatherDiagnosticField(slot,"dependencies")).intValue()&BigClockTextContent.WEATHER)!=0;
                    if(text instanceof TextView) {
                        CharSequence value=((TextView)text).getText();String visible=value==null?"":value.toString();
                        placeholder[i]=visible.contains(SystemWeatherText.UNAVAILABLE)||visible.contains(SystemWeatherText.TEMPERATURE_UNAVAILABLE);
                        matches[i]=weather[i]&&!placeholder[i]&&!snapshot.description.isEmpty()
                                &&((Number)weatherDiagnosticField(slot,"renderedWeatherRevision")).longValue()==snapshot.revision;
                    }
                }
            }
        } catch(ReflectiveOperationException|RuntimeException unsupported){reason="slot_shape";}
        ModuleDiagnostics.fact("weather","weather_slots","左右文字组件天气状态",
                "event",event,"reason",reason,"overlay_ready",overlayReady,"components_ready",componentsReady,
                "left_enabled",enabled[0],"right_enabled",enabled[1],"left_weather",weather[0],"right_weather",weather[1],
                "left_placeholder",placeholder[0],"right_placeholder",placeholder[1],"left_matches",matches[0],"right_matches",matches[1],
                "snapshot_ready",!snapshot.description.isEmpty(),"revision",snapshot.revision);
    }

    private static void scanHeaderText(View view) {
        if(view instanceof TextView&&TEXT.kind(view)!=TextControls.NONE) {
            initializeContext(view.getContext());TEXT.attach((TextView)view);allowDrawableOverflow(view);
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)scanHeaderText(group.getChildAt(i));
        }
    }

    private boolean installSpeedControls(ClassLoader loader) {
        try {
            Class<?> speed = loader.loadClass("com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView");
            try {
                Class<?> sampler = loader.loadClass("com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl");
                Method delay = sampler.getDeclaredMethod("postUpdateNetworkSpeedDelay", Long.TYPE); deoptimize(delay);
                for (Method caller : sampler.getDeclaredMethods())
                    if (caller.getName().matches("access\\$updateNetworkSpeed|updateNetworkSpeed|updateNetworkSpeedLoop|notifyNetworkSpeed")) deoptimize(caller);
                  try {
                      Class<?> handler = loader.loadClass("com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl$bgHandler$1");
                      deoptimize(handler.getDeclaredMethod("handleMessage", android.os.Message.class));
                  } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
                      moduleLog(Log.WARN, TAG, "Optional native throughput sampling caller unavailable", unavailable);
                  }
                installHook(HookGroup.SPEED, delay, chain -> chain.proceed(new Object[]{NETWORK_SPEED.samplingDelay((Long) chain.getArg(0))}));
            } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
                moduleLog(Log.WARN, TAG, "Optional native throughput sampling interval unavailable", unavailable);
            }
            speedNumberField = speed.getField("mSpeedNumber");
            speedUnitField = speed.getField("mSpeedUnit");
            installSpeedPosition(loader,speed);
            for(Method caller:speed.getDeclaredMethods())
                if(caller.getName().matches("onDarkChanged|onLayout|setStaticDrawableColor|setFontTypeface|onConfigurationChanged"))deoptimize(caller);
            try {
                installHook(HookGroup.SPEED,speed.getDeclaredMethod("onConfigurationChanged",android.content.res.Configuration.class),chain->{
                    View view=(View)chain.getThisObject();SpeedStyle style=speedStyleFor(view);
                    NETWORK_SPEED.beforeNativeLayout(view);
                    // Restore the base sizes before the native configuration callback, which may early-return.
                    TextView number=(TextView)speedNumberField.get(view),unit=(TextView)speedUnitField.get(view);
                    if(number!=null&&style.numberSize>0)number.setTextSize(TypedValue.COMPLEX_UNIT_PX,style.numberSize);
                    if(unit!=null&&style.unitSize>0)unit.setTextSize(TypedValue.COMPLEX_UNIT_PX,style.unitSize);
                    Object result;
                    try { result=chain.proceed(); }
                    finally { NETWORK_SPEED.afterNativeLayout(view, (TextView)speedNumberField.get(view), (TextView)speedUnitField.get(view)); }
                    if(number!=null){style.numberSize=number.getTextSize();style.appliedNumberSize=style.numberSize;}
                    if(unit!=null){style.unitSize=unit.getTextSize();style.appliedUnitSize=style.unitSize;}
                    applySpeedStyle(view,style);SPEED_POSITION.nativeUpdated(view);return result;
                });
            }catch(NoSuchMethodException unsupported){}
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("onFinishInflate"), chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                applySpeedStyle(view, speedStyleFor(view));
                return result;
            });
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("onAttachedToWindow"), chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                MAIN.post(() -> {
                    SPEED_POSITION.attached(view);
                    allowDrawableOverflow(view);
                    applySpeedStyle(view, speedStyleFor(view));
                });
                return result;
            });
            installHook(HookGroup.SPEED,speed.getDeclaredMethod("onDetachedFromWindow"),chain->{
                Object result=chain.proceed();SPEED_POSITION.detached((View)chain.getThisObject());NETWORK_SPEED.onViewDetached((View)chain.getThisObject());releaseOverflow((View)chain.getThisObject());return result;
            });
            for (Method method : speed.getDeclaredMethods()) {
                if (!method.getName().matches("onLayout|applyNetworkState|setFontTypeface|onDarkChanged")) continue;
                deoptimize(method);
                installHook(HookGroup.SPEED, method, chain -> {
                    Object result=chain.proceed();View host=(View)chain.getThisObject();
                    applySpeedStyle(host,speedStyleFor(host));
                    SPEED_POSITION.nativeUpdated(host);
                    return result;
                });
            }
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("setIconTint", Integer.TYPE), chain -> {
                View view = (View) chain.getThisObject();
                SpeedStyle style = speedStyleFor(view);
                style.nativeTint = (Integer) chain.getArg(0);
                style.tintCaptured = true;
                return chain.proceed(new Object[]{styleColor("speed", style.nativeTint)});
            });
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("dispatchDraw", Canvas.class), chain -> {
                View view = (View) chain.getThisObject();
                Canvas canvas = (Canvas) chain.getArg(0);
                int save = canvas.save();
                float density = view.getResources().getDisplayMetrics().density;
                canvas.translate(NumericPolicy.pixels(SPEED_POSITION.drawOffsetXdp(view,speedOffsetXdp),density),NumericPolicy.pixels(speedOffsetYdp,density));
                float scale = NumericPolicy.scale(speedScalePercent / 100f,Math.max(view.getWidth(),view.getHeight()));
                canvas.scale(scale, scale, view.getWidth() / 2f, view.getHeight() / 2f);
                try { return chain.proceed(); }
                finally { canvas.restoreToCount(save); }
            });
            moduleLog(Log.INFO, TAG, "Live throughput position, typography, and color hooks installed");
            return true;
        } catch (ClassNotFoundException unsupportedSystem) {
            return true;
        } catch (Throwable error) {
            moduleLog(Log.ERROR, TAG, "Could not bind live throughput controls", error);
            return false;
        }
    }

    private void installSpeedPosition(ClassLoader loader,Class<?> speed) {
        try {
            SPEED_POSITION.setOrderReader(NETWORK_ORDER::orderedChildren);
            SPEED_POSITION.resolve(loader);
            Method paddingEnd=View.class.getDeclaredMethod("getPaddingEnd"); deoptimize(paddingEnd);
            installHook(HookGroup.SPEED,paddingEnd,chain->SPEED_POSITION.allocationPaddingEnd(
                    (View)chain.getThisObject(),(Integer)chain.proceed()));
            Method childMeasure=ViewGroup.class.getDeclaredMethod("measureChild",View.class,Integer.TYPE,Integer.TYPE);
            deoptimize(childMeasure);
            installHook(HookGroup.SPEED,childMeasure,chain->{
                try(SpeedPosition.ChildMeasureScope scope=SPEED_POSITION.childMeasurement(
                        (ViewGroup)chain.getThisObject(),(View)chain.getArg(0))){return chain.proceed();}
            });
            Method blocked=speed.getDeclaredMethod("isIconBlocked");deoptimize(blocked);
            installHook(HookGroup.SPEED,blocked,chain->SPEED_POSITION.iconBlocked((View)chain.getThisObject(),(Boolean)chain.proceed()));
            Class<?> container=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
            Class<?> state=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
            // All three allocation phases are required; never activate a partial mirror installation.
            container.getDeclaredMethod("onMeasure",Integer.TYPE,Integer.TYPE);
            container.getDeclaredMethod("onLayout",Boolean.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE);
            container.getDeclaredMethod("updateStates");
            Method apply=state.getDeclaredMethod("applyToView",View.class);deoptimize(apply);
            installHook(HookGroup.SPEED,apply,chain->{SPEED_POSITION.beforeApply(chain.getThisObject(),(View)chain.getArg(0));return chain.proceed();});
            try {
                // The base method is a no-op; hook the C17 override that reads targets
                // for the camera capsule and coordinated status-bar animations.
                Class<?> seeding=loader.loadClass("com.oplus.systemui.statusbar.seeding.OplusSeedlingCardController");
                Class<?> location=loader.loadClass("com.android.systemui.statusbar.phone.StatusBarLocation");
                Method coordinate=seeding.getDeclaredMethod("calculateIconTranslations",location,container,Boolean.TYPE);
                deoptimize(coordinate);
                installHook(HookGroup.SPEED,coordinate,chain->{
                    SPEED_POSITION.prepareTargets((ViewGroup)chain.getArg(1));return chain.proceed();
                });
            }catch(ClassNotFoundException|NoSuchMethodException olderStatusBar){
                moduleLog(Log.INFO,TAG,"Native throughput coordination unavailable; layout target fallback retained");
            }
            for(Method method:container.getDeclaredMethods()) {
                String name=method.getName();
                if(name.equals("onMeasure")||name.equals("updateStates")) {
                    final boolean targets=name.equals("updateStates");deoptimize(method);
                    installHook(HookGroup.SPEED,method,chain->{
                        try(SpeedPosition.Scope scope=SPEED_POSITION.begin((ViewGroup)chain.getThisObject(),targets)){return chain.proceed();}
                    });
                }else if(name.equals("onLayout")) {
                    deoptimize(method);installHook(HookGroup.SPEED,method,chain->{SPEED_POSITION.beforeLayout((ViewGroup)chain.getThisObject());return chain.proceed();});
                }else if(name.matches("onViewAdded|onViewRemoved|onConfigurationChanged|onAttachedToWindow|onDetachedFromWindow|addIgnoredSlot|addIgnoredSlots|removeIgnoredSlot|removeIgnoredSlots")) {
                    deoptimize(method);installHook(HookGroup.SPEED,method,chain->{
                        Object result=chain.proceed();ViewGroup parent=(ViewGroup)chain.getThisObject();
                        if(name.equals("onDetachedFromWindow"))SPEED_POSITION.detached(parent);else SPEED_POSITION.changed(parent);return result;
                    });
                }
            }
            try {
                Class<?> runnable=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$1");
                for(Method method:runnable.getDeclaredMethods())if(method.getName().equals("run"))deoptimize(method);
            }catch(ClassNotFoundException noDeferredLayout){}
            Method color=TextView.class.getDeclaredMethod("setTextColor",Integer.TYPE);deoptimize(color);
            installHook(HookGroup.SPEED,color,chain->{Object result=chain.proceed();SPEED_POSITION.clockTintChanged((View)chain.getThisObject());return result;});
            for(Method method:speed.getDeclaredMethods())if(method.getName().equals("setBlocked")) {
                deoptimize(method);installHook(HookGroup.SPEED,method,chain->{Object result=chain.proceed();View host=(View)chain.getThisObject();SPEED_POSITION.nativeUpdated(host);return result;});
            }
            Method changeSlot=speed.getDeclaredMethod("setSlot",String.class);deoptimize(changeSlot);
            installHook(HookGroup.SPEED,changeSlot,chain->{Object result=chain.proceed();View host=(View)chain.getThisObject();SPEED_POSITION.slotChanged(host);return result;});
            SPEED_POSITION.setHooksReady(true);
            SPEED_POSITION.setAllocationReady(true);
        }catch(Throwable unsupported){SPEED_POSITION.setAllocationReady(false);SPEED_POSITION.setHooksReady(false);moduleLog(Log.WARN,TAG,"Throughput placement unavailable; keeping native ownership",unsupported);}
    }

    private static SpeedStyle speedStyleFor(View view) {
        synchronized (SPEED_VIEWS) {
            SpeedStyle style = SPEED_VIEWS.get(view);
            if (style == null) {
                style = new SpeedStyle();
                SPEED_VIEWS.put(view, style);
            }
            return style;
        }
    }

    private static void applySpeedStyle(View view, SpeedStyle style) {
        if (speedNumberField == null || speedUnitField == null) return;
        try {
            TextView number = (TextView) speedNumberField.get(view);
            TextView unit = (TextView) speedUnitField.get(view);
            if (number == null || unit == null) return;
            if (!style.tintCaptured) { style.nativeTint=number.getCurrentTextColor();style.tintCaptured=true; }
            if (style.numberSize == 0 || Math.abs(number.getTextSize()-style.appliedNumberSize)>.001f) style.numberSize = number.getTextSize();
            if (style.unitSize == 0 || Math.abs(unit.getTextSize()-style.appliedUnitSize)>.001f) style.unitSize = unit.getTextSize();
            String numberAxes=number.getFontVariationSettings(),unitAxes=unit.getFontVariationSettings();
            if (!style.numberFontCaptured || number.getTypeface() != style.appliedNumberFace)
                style.numberFace=number.getTypeface();
            if (!style.numberFontCaptured || !java.util.Objects.equals(numberAxes,style.appliedNumberAxes))
                style.numberAxes=numberAxes;
            style.numberFontCaptured=true;
            if (!style.unitFontCaptured || unit.getTypeface() != style.appliedUnitFace)
                style.unitFace=unit.getTypeface();
            if (!style.unitFontCaptured || !java.util.Objects.equals(unitAxes,style.appliedUnitAxes))
                style.unitAxes=unitAxes;
            style.unitFontCaptured=true;
            boolean typeStyle=FEATURES.textStyle("speed");
            boolean useFont=FEATURES.enabled("speed")&&FEATURES.enabled("font")&&!FontRepository.systemMode(FontScopes.NETWORK_SPEED);
            if(typeStyle||useFont) {
                FontRepository.apply(number,style.numberFace,typeStyle?speedWeight:nativeWeight(style.numberFace),style.numberAxes,FontScopes.NETWORK_SPEED);
                FontRepository.apply(unit,style.unitFace,typeStyle?speedWeight:nativeWeight(style.unitFace),style.unitAxes,FontScopes.NETWORK_SPEED);
            } else {
                FontWeight.restore(number,style.numberFace,style.numberAxes);
                FontWeight.restore(unit,style.unitFace,style.unitAxes);
            }
            style.appliedNumberFace=number.getTypeface();style.appliedUnitFace=unit.getTypeface();
            style.appliedNumberAxes=number.getFontVariationSettings();style.appliedUnitAxes=unit.getFontVariationSettings();
            float numberSize = NumericPolicy.textPixels((double)style.numberSize * speedNumberScalePercent / 100f);
            float unitSize = NumericPolicy.textPixels((double)style.unitSize * speedUnitScalePercent / 100f);
            if (Math.abs(number.getTextSize() - numberSize) > .001f) number.setTextSize(TypedValue.COMPLEX_UNIT_PX, numberSize);
            if (Math.abs(unit.getTextSize() - unitSize) > .001f) unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, unitSize);
            style.appliedNumberSize=numberSize;style.appliedUnitSize=unitSize;
            float halfGap = NumericPolicy.pixels(speedLineGapDp,view.getResources().getDisplayMetrics().density) / 2f;
            if(!style.gapCaptured){style.numberY=number.getTranslationY();style.unitY=unit.getTranslationY();style.gapCaptured=true;}
            else {
                if(Math.abs(number.getTranslationY()-style.appliedNumberY)>.001f)style.numberY=number.getTranslationY();
                if(Math.abs(unit.getTranslationY()-style.appliedUnitY)>.001f)style.unitY=unit.getTranslationY();
            }
            style.appliedNumberY=NumericPolicy.pixels((double)style.numberY-halfGap);style.appliedUnitY=NumericPolicy.pixels((double)style.unitY+halfGap);
            number.setTranslationY(style.appliedNumberY);unit.setTranslationY(style.appliedUnitY);
            int tint = styleColor("speed", style.nativeTint);
            if (number.getCurrentTextColor() != tint) number.setTextColor(tint);
            if (unit.getCurrentTextColor() != tint) unit.setTextColor(tint);
            allowDrawableOverflow(view);
            NETWORK_SPEED.nativeSizes(view,style.numberSize,style.unitSize,typeStyle||useFont);
            NETWORK_SPEED.apply(view,number,unit);
        } catch (Throwable error) {
            logFrameworkStage(Log.WARN, "Could not apply throughput style", error);
        }
    }

    private static final class ComposeBridge {
        final ClassLoader loader;
        final Class<?> modifier, function;
        final Object empty, unit, filterCompanion;
        final Method layer, x, y, scaleX, scaleY, clip, height, width, widthIn;
        final Method color, argb, nativeFilter, tintFilter, androidView;
        ComposeBridge(ClassLoader loader) throws Exception {
            this.loader = loader;
            modifier = loader.loadClass("androidx.compose.ui.Modifier");
            function = loader.loadClass("kotlin.jvm.functions.Function1");
            empty = modifier.getField("Companion").get(null);
            unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            layer = loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerModifierKt").getMethod("graphicsLayer", modifier, function);
            Class<?> scope = loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerScope");
            x = scope.getMethod("setTranslationX", Float.TYPE);
            y = scope.getMethod("setTranslationY", Float.TYPE);
            scaleX = scope.getMethod("setScaleX", Float.TYPE);
            scaleY = scope.getMethod("setScaleY", Float.TYPE);
            clip = scope.getMethod("setClip", Boolean.TYPE);
            Class<?> sizes = loader.loadClass("androidx.compose.foundation.layout.SizeKt");
            height = sizes.getMethod("height-3ABfNKs", modifier, Float.TYPE);
            width = sizes.getMethod("width-3ABfNKs", modifier, Float.TYPE);
            widthIn = sizes.getMethod("widthIn-VpY3zN4", modifier, Float.TYPE, Float.TYPE);
            Class<?> colors = loader.loadClass("androidx.compose.ui.graphics.ColorKt");
            color = colors.getMethod("Color", Integer.TYPE);
            argb = colors.getMethod("toArgb-8_81llA", Long.TYPE);
            Class<?> filters = loader.loadClass("androidx.compose.ui.graphics.ColorFilter");
            nativeFilter = filters.getMethod("getNativeColorFilter$ui_graphics");
            filterCompanion = filters.getField("Companion").get(null);
            tintFilter = filterCompanion.getClass().getMethod("tint-xETnrds", Long.TYPE, Integer.TYPE);
            androidView = loader.loadClass("androidx.compose.ui.viewinterop.AndroidView_androidKt").getMethod("AndroidView",
                    function, modifier, function, loader.loadClass("androidx.compose.runtime.Composer"), Integer.TYPE, Integer.TYPE);
        }
        Object function(FunctionBody body) {
            return Proxy.newProxyInstance(loader, new Class<?>[]{function}, (proxy, method, args) -> {
                if ("invoke".equals(method.getName())) return body.call(args[0]);
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return "StatusBarPlacement";
            });
        }
        Object place(Object base, float dx, float dy, float scale) throws Exception {
            return layer.invoke(null, base, function(scope -> {
                x.invoke(scope, dx); y.invoke(scope, dy);
                scaleX.invoke(scope, scale); scaleY.invoke(scope, scale); clip.invoke(scope, false);
                return unit;
            }));
        }
    }
    private interface FunctionBody { Object call(Object value) throws Throwable; }

    /** AndroidView content in the native mobile Compose slot. Optional shared
     * Wi-Fi positioning is managed by the outer flow, not by hosting this text in Wi-Fi. */
    private static final class NetworkLabelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint measuredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect glyph = new Rect();
        private String label = "";
        private int nativeTint = Color.BLACK;
        private int weight;
        private int measuredNaturalWidth=-1, measuredNaturalHeight=-1;
        private NetworkLabelGeometry.Plan geometry;
        private NetworkLabelGeometry.Plan measuredGeometry;
        private String measuredLabel="";
        NetworkLabelView(Context context) {
            super(context);
            setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            setWillNotDraw(false);
        }
        private void prepare() {
            weight = FEATURES.textStyle("label")?fontWeight:nativeWeight(TEXT.nativeFamily());
            if(FEATURES.textStyle("label") || FEATURES.enabled("font")&&!FontRepository.systemMode(FontScopes.NETWORK_TYPE))
                FontRepository.apply(paint,TEXT.nativeFamily(),weight,null,FontScopes.NETWORK_TYPE);
            else FontWeight.restore(paint,TEXT.nativeFamily(),null);
            paint.setFakeBoldText(false);
            paint.setTextSize(13f * getResources().getDisplayMetrics().density);
            paint.setColor(styleColor("label", nativeTint));
            geometry=NetworkLabelGeometry.measure(paint,label,getResources().getDisplayMetrics().density,
                    labelScalePercent,geometry,glyph);
        }
        void update(String label, int nativeTint) {
            boolean changed = !this.label.equals(label);
            this.label = label; this.nativeTint = nativeTint;
            setContentDescription(label.isEmpty() ? "" : "移动数据网络 " + label);
            prepare();
            if (!isLayoutRequested()&&(changed || pendingGeometry())) requestLayout();
            invalidate();
        }
        private int naturalWidth(){return geometry.width;}
        private int naturalHeight(){return geometry.height;}
        private boolean pendingGeometry(){return measuredGeometry==null||naturalWidth()!=measuredNaturalWidth
                ||naturalHeight()!=measuredNaturalHeight||geometry.scale!=measuredGeometry.scale;}
        @Override protected void onMeasure(int width, int height) {
            prepare();
            measuredNaturalWidth=naturalWidth();measuredNaturalHeight=naturalHeight();
            measuredGeometry=geometry;measuredPaint.set(paint);measuredLabel=label;
            // Keep the native content clip at its original 20dp before the View's
            // Y transform. Only horizontal glyph size belongs inside this budget.
            if(getScaleY()!=geometry.scale)setScaleY(geometry.scale);
            setMeasuredDimension(resolveSize(measuredNaturalWidth,width),resolveSize(measuredNaturalHeight,height));
        }
        @Override protected void onDraw(Canvas canvas) {
            if (label.isEmpty()) return;
            prepare();
            // A font retry may resolve here after the Compose update. Invalidate the
            // old allocation once; a constrained measure still settles these metrics.
            boolean pending=pendingGeometry();
            if(!isLayoutRequested()&&pending)requestLayout();
            if(pending&&measuredGeometry!=null){
                // Keep the previous measured font/text until the native traversal
                // publishes its new allocation. Theme tint can still update immediately.
                measuredPaint.setColor(paint.getColor());
                measuredGeometry.drawHorizontal(canvas,measuredLabel,measuredPaint,getWidth()/2f,getHeight()/2f);
            }else geometry.drawHorizontal(canvas,label,paint,getWidth()/2f,getHeight()/2f);
        }
    }

    private boolean installNativeStatusIcons(ClassLoader loader) {
        try {
            STATUS_HINTS.resolve(loader);
            Class<?> container=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
            Class<?> icon=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
            Class<?> state=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
            // Native getter creates a glyph before storing the incoming StatusBarIcon model.
            // Retain its drawable dimensions and only replace the mask at draw time.
            for(Method method:icon.getDeclaredMethods())if(method.getName().equals("getIcon")
                    &&method.getParameterCount()==1&&Drawable.class.isAssignableFrom(method.getReturnType())
                    &&method.getParameterTypes()[0].getName().equals("com.android.internal.statusbar.StatusBarIcon")) {
                deoptimize(method);
                installHook(HookGroup.STATUS_HINTS,method,chain->{
                    Drawable nativeGraphic=(Drawable)chain.proceed();
                    return ICON_PACK_DRAWING.wrapHint((View)chain.getThisObject(),chain.getArg(0),nativeGraphic);
                });
            }
            // ViewGroup.getChildAt runs hundreds of thousands of times a second in SystemUI, and a hooked
            // call costs far more than the call itself. The index map only applies while the feature is on.
            lazyHook("status_hint_child_index",STATUS_HINTS::enabled,()->{
                Method childAt=ViewGroup.class.getDeclaredMethod("getChildAt",Integer.TYPE);
                deoptimize(childAt);
                installHook(HookGroup.STATUS_HINTS,childAt,chain->{
                    int original=(Integer)chain.getArg(0),mapped=STATUS_HINTS.childIndex(chain.getThisObject(),original);
                    return mapped==original?chain.proceed():chain.proceed(new Object[]{mapped});
                });
            });
            Method blocked=icon.getDeclaredMethod("isIconBlocked");deoptimize(blocked);
            installHook(HookGroup.STATUS_HINTS,blocked,chain->STATUS_HINTS.iconBlocked((View)chain.getThisObject(),(Boolean)chain.proceed()));
            Method apply=state.getDeclaredMethod("applyToView",View.class);deoptimize(apply);
            installHook(HookGroup.STATUS_HINTS,apply,chain->{
                STATUS_HINTS.beforeApply(chain.getThisObject(),(View)chain.getArg(0));return chain.proceed();
            });
            for(Method method:container.getDeclaredMethods()) {
                String name=method.getName();
                if(name.equals("onMeasure")||name.equals("updateStates")) {
                    final boolean targets=name.equals("updateStates");deoptimize(method);
                    installHook(HookGroup.STATUS_HINTS,method,chain->{
                        NativeStatusIcons.Scope scope=STATUS_HINTS.begin((ViewGroup)chain.getThisObject(),targets);
                        try{return chain.proceed();}finally{if(scope!=null)scope.close();}
                    });
                } else if(name.equals("onLayout")) {
                    deoptimize(method);installHook(HookGroup.STATUS_HINTS,method,chain->{
                        Object result=chain.proceed();STATUS_HINTS.onLayout((ViewGroup)chain.getThisObject());return result;
                    });
                } else if(name.matches("onViewAdded|onViewRemoved|onConfigurationChanged|onAttachedToWindow|onDetachedFromWindow")) {
                    deoptimize(method);installHook(HookGroup.STATUS_HINTS,method,chain->{
                        Object result=chain.proceed();ViewGroup host=(ViewGroup)chain.getThisObject();
                        if(name.equals("onDetachedFromWindow"))STATUS_HINTS.detached(host);else STATUS_HINTS.changed(host);
                        return result;
                    });
                }
            }
            for(Method method:icon.getDeclaredMethods())if(method.getName().equals("set")||method.getName().equals("onDensityOrFontScaleChanged")) {
                deoptimize(method);installHook(HookGroup.STATUS_HINTS,method,chain->{
                    Object result=chain.proceed();STATUS_HINTS.bound((View)chain.getThisObject());return result;
                });
            }
            Method detached=View.class.getDeclaredMethod("onDetachedFromWindow");deoptimize(detached);
            installHook(HookGroup.STATUS_HINTS,detached,chain->{
                Object result=chain.proceed();Object host=chain.getThisObject();
                if(container.isInstance(host))STATUS_HINTS.detached((ViewGroup)host);return result;
            });
            try {
                Class<?> deferred=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$1");
                for(Method method:deferred.getDeclaredMethods())if(method.getName().equals("run"))deoptimize(method);
            }catch(ClassNotFoundException absent){/* Direct native layout remains hooked. */}
            return true;
        } catch(Throwable unavailable){logFrameworkStage(Log.WARN,"Native system hint icon controls unavailable",unavailable);return false;}
    }

    private boolean installStatusBarFlow(ClassLoader loader) {
        try {
            STATUS_FLOW.resolve(loader);
            STATUS_FLOW.setHintRetention(STATUS_HINTS::retainedHint);
            try { NETWORK_SHARED_SLOT.resolve(loader); }
            catch (ReflectiveOperationException unsupportedSharedSlot) { ModuleDiagnostics.info("radio", "Shared network slot unavailable"); }
            STATUS_FLOW.setSharedSlots(NETWORK_SHARED_SLOT);
            NETWORK_ORDER.sharedLayout(NETWORK_SHARED_SLOT);
            SINGLE_LABEL.sharedLayout(NETWORK_SHARED_SLOT);
            for (String root : new String[]{"com.android.systemui.statusbar.phone.PhoneStatusBarView",
                    "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                    "com.oplus.systemui.statusbar.widget.FixedKeyguardStatusBarView",
                    "com.oplus.systemui.qs.OplusQuickStatusBarHeader"}) {
                try {
                    for (Constructor<?> constructor : loader.loadClass(root).getDeclaredConstructors())
                        installHook(HookGroup.STATUS_FLOW, constructor, chain -> {
                            Object result = chain.proceed();
                            STATUS_FLOW.observed((ViewGroup) chain.getThisObject()); return result;
                        });
                } catch (ClassNotFoundException sceneAbsent) { }
            }
            lazyHook("statusbar_flow_layout", STATUS_FLOW::wanted, () -> {
                // View.measure and View.layout reach onMeasure/onLayout through a virtual call, which always
                // enters the hooked method. Interpreting the two dispatchers for every view in SystemUI cost
                // about 5 microseconds per layout call on the PJZ110 and bought nothing.
                StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(STATUS_FLOW);
                Set<Method> geometry = new HashSet<>();
                for (String root : new String[]{"com.android.systemui.statusbar.phone.PhoneStatusBarView",
                        "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                        "com.oplus.systemui.statusbar.widget.FixedKeyguardStatusBarView",
                        "com.oplus.systemui.qs.OplusQuickStatusBarHeader"}) {
                    try {
                        Class<?> type = loader.loadClass(root);
                        Method measure = StatusBarFlowCallbacks.virtual(type, "onMeasure", Integer.TYPE, Integer.TYPE);
                        Method layout = StatusBarFlowCallbacks.virtual(type, "onLayout", Boolean.TYPE,
                                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
                        if (geometry.add(measure)) {
                            deoptimize(measure);
                            installHook(HookGroup.STATUS_FLOW, measure, chain -> callbacks.dispatch(
                                    (View) chain.getThisObject(), true, chain::proceed));
                        }
                        if (geometry.add(layout)) {
                            deoptimize(layout);
                            installHook(HookGroup.STATUS_FLOW, layout, chain -> callbacks.dispatch(
                                    (View) chain.getThisObject(), false, chain::proceed));
                        }
                    } catch (ClassNotFoundException sceneAbsent) { }
                }
                Class<?> container = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
                Class<?> state = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
                // ART may inline updateStates/applyToView into these callers.
                // Deoptimizing only the hooked callees misses layout-triggered
                // state updates, including our own structural transactions.
                deoptimize(container.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE));
                deoptimize(container.getDeclaredMethod("onLayout", Boolean.TYPE,
                        Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE));
                Method update = container.getDeclaredMethod("updateStates"); deoptimize(update);
                installHook(HookGroup.STATUS_FLOW, update, chain -> callbacks.updateStates(
                        (ViewGroup) chain.getThisObject(), chain::proceed));
                Method apply = state.getDeclaredMethod("applyToView", View.class); deoptimize(apply);
                installHook(HookGroup.STATUS_FLOW, apply, chain -> callbacks.applyState(
                        chain.getThisObject(), (View) chain.getArg(0), chain::proceed));
                try {
                    Class<?> seedling = loader.loadClass("com.oplus.systemui.statusbar.seeding.OplusSeedlingCardController");
                    Method coordinate = seedling.getDeclaredMethod("calculateIconTranslations",
                            loader.loadClass("com.android.systemui.statusbar.phone.StatusBarLocation"), container, Boolean.TYPE);
                    deoptimize(coordinate);
                    installHook(HookGroup.STATUS_FLOW, coordinate, chain -> {
                        ViewGroup row = (ViewGroup) chain.getArg(1);
                        if(STATUS_FLOW.deferCoordinator(row,chain.getThisObject(),coordinate,chain.getArg(0),(Boolean)chain.getArg(2)))return null;
                        STATUS_FLOW.capsule(row, StatusBarFlowLayout.capsuleAreas(chain.getThisObject(), chain.getArg(0)));
                        STATUS_FLOW.prepareTargets(row); return chain.proceed();
                    });
                } catch (ClassNotFoundException | NoSuchMethodException nativeAnimationAbsent) { }
                for (String root : new String[]{"com.android.systemui.statusbar.phone.PhoneStatusBarView",
                        "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                        "com.oplus.systemui.statusbar.widget.FixedKeyguardStatusBarView",
                        "com.oplus.systemui.qs.OplusQuickStatusBarHeader"}) {
                    try {
                        Class<?> type = loader.loadClass(root);
                        for (Method method : type.getDeclaredMethods()) {
                            if (method.getName().matches("onMeasure|onLayout|onApplyWindowInsets|onConfigurationChanged")) deoptimize(method);
                            if (method.getName().equals("onDetachedFromWindow")) installHook(HookGroup.STATUS_FLOW, method, chain -> {
                                STATUS_FLOW.detached((ViewGroup) chain.getThisObject()); return chain.proceed();
                            });
                        }
                    } catch (ClassNotFoundException sceneAbsent) { }
                }
            });
            return true;
        } catch (Throwable unsupported) {
            moduleLog(Log.WARN, TAG, "Native status-bar flow layout unavailable", unsupported); return false;
        }
    }

    private boolean installNetworkIconOrder(ClassLoader loader) {
        try {
            NETWORK_ORDER.resolve(loader);
            Class<?> container = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
            // Shared Compose placement never uses this legacy map. A global getChildAt hook costs a
            // native bridge on every SystemUI tree walk even when its Java result is unchanged.
            Method childAt = ViewGroup.class.getDeclaredMethod("getChildAt", Integer.TYPE);
            final XposedInterface.HookHandle[] indexHook = new XposedInterface.HookHandle[1];
            lazyHook("network_order_child_index", NETWORK_ORDER::nativeIndexWanted, () -> {
                deoptimize(childAt);
                indexHook[0] = createHook(HookGroup.NETWORK_ORDER, childAt, chain -> {
                    int original = (Integer) chain.getArg(0);
                    int mapped = NETWORK_ORDER.childIndex(chain.getThisObject(), original);
                    return mapped == original ? chain.proceed() : chain.proceed(new Object[]{mapped});
                });
            }, () -> {
                if (indexHook[0] != null) { indexHook[0].unhook(); indexHook[0] = null; }
            });
            for (Method method : container.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("onMeasure") || name.equals("updateStates")) {
                    deoptimize(method);
                    installHook(HookGroup.NETWORK_ORDER, method, chain -> {
                        NetworkIconOrder.IndexScope scope = NETWORK_ORDER.begin((ViewGroup) chain.getThisObject());
                        try { return chain.proceed(); } finally { if (scope != null) scope.close(); }
                    });
                } else if (name.matches("onViewAdded|onViewRemoved|onConfigurationChanged|onAttachedToWindow|onDetachedFromWindow")) {
                    deoptimize(method);
                    installHook(HookGroup.NETWORK_ORDER, method, chain -> {
                        Object result = chain.proceed(); ViewGroup parent = (ViewGroup) chain.getThisObject();
                        if (name.equals("onDetachedFromWindow")) NETWORK_ORDER.detached(parent); else NETWORK_ORDER.changed(parent);
                        return result;
                    });
                } else if (name.equals("onLayout")) deoptimize(method);
            }
            // updateStates is also called by the OEM's deferred layout runnable.
            try {
                Class<?> runnable = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$1");
                for (Method method : runnable.getDeclaredMethods()) if (method.getName().equals("run")) deoptimize(method);
            } catch (ClassNotFoundException noDeferredLayout) { }
            Class<?> modern = loader.loadClass(MODERN_VIEW);
            Method init = modern.getDeclaredMethod("initView", String.class, loader.loadClass("kotlin.jvm.functions.Function0"));
            deoptimize(init);
            installHook(HookGroup.NETWORK_ORDER, init, chain -> {
                Object result = chain.proceed(); NETWORK_ORDER.bound((View) chain.getThisObject()); return result;
            });
            try {
                NETWORK_ORDER.resolveCompose(loader);
                Method sharedPlacement = NETWORK_ORDER.placeHelperMethod();
                if (sharedPlacement != null) lazyHook("network_shared_native_placement", NETWORK_ORDER::wanted, () -> {
                    deoptimize(sharedPlacement);
                    for (Method nativeMeasure : sharedPlacement.getDeclaringClass().getDeclaredMethods())
                        if (nativeMeasure.getName().startsWith("measure")) deoptimize(nativeMeasure);
                    Class<?> rowAlgorithm = loader.loadClass("androidx.compose.foundation.layout.RowColumnMeasurePolicyKt");
                    for (Method nativeMeasure : rowAlgorithm.getDeclaredMethods())
                        if (nativeMeasure.getName().equals("measure")) deoptimize(nativeMeasure);
                    installHook(HookGroup.NETWORK_ORDER, sharedPlacement, chain -> {
                        Object[] original = chain.getArgs().toArray();
                        Object[] adjusted = NETWORK_ORDER.placeHelperArguments(chain.getThisObject(), original);
                        return adjusted == original ? chain.proceed() : chain.proceed(adjusted);
                    });
                });
                Class<?> node = loader.loadClass("androidx.compose.ui.node.LayoutNode");
                Method modifier = node.getDeclaredMethod("setModifier", loader.loadClass("androidx.compose.ui.Modifier"));
                deoptimize(modifier);
                installHook(HookGroup.NETWORK_ORDER, modifier, chain -> {
                    Object result = chain.proceed(); NETWORK_ORDER.nodeChanged(chain.getThisObject(), chain.getArg(0)); return result;
                });
                Class<?> setter = loader.loadClass("androidx.compose.ui.node.ComposeUiNode$Companion$SetModifier$1");
                for (Method method : setter.getDeclaredMethods()) if (method.getName().equals("invoke")) deoptimize(method);
                Class<?> row = loader.loadClass("androidx.compose.foundation.layout.RowKt");
                for (Method method : row.getDeclaredMethods()) if (method.getName().equals("rowMeasurePolicy")) {
                    deoptimize(method);
                    installHook(HookGroup.NETWORK_ORDER, method, chain -> NETWORK_ORDER.rowPolicy(chain.proceed()));
                }
                String prefix = "com.android.systemui.statusbar.pipeline.shared.ui.composable.";
                for (String strategy : new String[]{"BigTypeStackedMobileIconLayoutStrategy", "DefaultStackedMobileIconLayoutStrategy", "TigoFiveGStackedMobileIconLayoutStrategy"}) {
                    Class<?> layout = loader.loadClass(prefix + strategy);
                    for (Method method : layout.getDeclaredMethods()) if (method.getName().startsWith("SignalIcon-")) deoptimize(method);
                    for (Method method : layout.getDeclaredMethods()) if (method.getName().equals("StackedMobileIcon") && method.getParameterTypes().length == 8) {
                        deoptimize(method);
                        installHook(HookGroup.NETWORK_ORDER, method, chain -> {
                            // Subscribe in the caller's native restart group. Force the strategy to
                            // update its node policy when this independent setting changes.
                            boolean force = NETWORK_ORDER.forceStrategy(chain.getArg(6), chain.getArg(1), chain.getThisObject());
                            NetworkIconOrder.RowScope scope = NETWORK_ORDER.beginRow(chain.getArg(1));
                            if (force && readComposeRevision != null && composeStyleRevision != null) readComposeRevision.invoke(composeStyleRevision);
                            try {
                                if (!force) return chain.proceed();
                                Object[] args = chain.getArgs().toArray(); args[7] = ((Integer) args[7]) | 1;
                                return chain.proceed(args);
                            } finally { if (scope != null) scope.close(); }
                        });
                    }
                }
                for (String source : new String[]{"OplusStackedMobileIconLayoutStrategyKt", "TigoFiveGStackedMobileIconLayoutStrategyKt"}) {
                    Class<?> text = loader.loadClass(prefix + source);
                    for (Method method : text.getDeclaredMethods()) if (method.getName().contains("NetworkTypeText-") && method.getParameterTypes().length == 13) {
                        deoptimize(method);
                        installHook(HookGroup.NETWORK_ORDER, method, chain -> {
                            Object original = chain.getArg(3); Object tagged = NETWORK_ORDER.textModifier(original);
                            if (original == tagged) return chain.proceed();
                            Object[] args = chain.getArgs().toArray(); args[3] = tagged;
                            args[11] = ((Integer) args[11]) | 1; // Preserve the OEM changed/default slot branches.
                            return chain.proceed(args);
                        });
                    }
                }
                Class<?> signal = loader.loadClass(prefix + "OplusStackedMobileIconLayoutStrategy");
                for (Method method : signal.getDeclaredMethods()) if (method.getName().startsWith("SignalIcon-") && !java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                    deoptimize(method);
                    installHook(HookGroup.NETWORK_ORDER, method, chain -> {
                        systemTextChanged(BigClockSystemIcon.nativeStackedChanged(chain.getArg(0)));
                        Object original = chain.getArg(4); Object tagged = NETWORK_ORDER.signalModifier(original);
                        if (original == tagged) return chain.proceed();
                        Object[] args = chain.getArgs().toArray(); args[4] = tagged; return chain.proceed(args);
                    });
                }
                Class<?> defaultSignal = loader.loadClass(prefix + "OplusStackedMobileIconLayoutStrategy$ComposeDefaultImpls");
                for (Method method : defaultSignal.getDeclaredMethods()) if (method.getName().startsWith("SignalIcon$default-")) deoptimize(method);
                Class<?> caller = loader.loadClass(prefix + "OplusStackedMobileIconKt");
                for (Method method : caller.getDeclaredMethods()) if (method.getName().equals("OplusStackedMobileIcon")) deoptimize(method);
            } catch (ClassNotFoundException legacyViews) {
                moduleLog(Log.INFO, TAG, "Network icon ordering retained for native view slots");
            }
            return true;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Could not install native network icon ordering", error); return false;
        }
    }

    private boolean installComposeSupport(ClassLoader loader) {
        Class<?> factory;
        try {
            factory = loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyFactory");
            composeStatusBar = true;
        } catch (ClassNotFoundException legacySystem) { return true; }
        try {
            if (composeStyleRevision == null) {
                composeStyleRevision = loader.loadClass("androidx.compose.runtime.SnapshotIntStateKt").getMethod("mutableIntStateOf", Integer.TYPE).invoke(null, 0);
                readComposeRevision = loader.loadClass("androidx.compose.runtime.IntState").getMethod("getIntValue");
                writeComposeRevision = loader.loadClass("androidx.compose.runtime.MutableIntState").getMethod("setIntValue", Integer.TYPE);
            }
            ComposeBridge bridge = new ComposeBridge(loader);
            try { NATIVE_BADGE.resolve(loader); }
            catch (ReflectiveOperationException|LinkageError unavailable) {
                moduleLog(Log.INFO,TAG,"Optional native network badge controls unavailable",unavailable);
            }
            Object largeLayout = loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.BigTypeStackedMobileIconLayoutStrategy").getField("INSTANCE").get(null);
            Method getStrategy=factory.getDeclaredMethod("getStrategy");deoptimize(getStrategy);
            installHook(HookGroup.COMPOSE, getStrategy, chain -> {
                Object nativeLayout = chain.proceed();
                if(composeRenderingReady)readComposeRevision.invoke(composeStyleRevision);
                // Signal styling works with the original layout. Forcing BigType for signal
                // alone leaves a large network label behind after its independent page is off.
                return composeRenderingReady && FEATURES.enabled("label")
                        && !FEATURES.hideNetworkLabel() ? largeLayout : nativeLayout;
            });
            Class<?> model = loader.loadClass("com.oplus.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModelImpl");
            Method activityGetter=model.getDeclaredMethod("getActivityIndicatorIconResId");deoptimize(activityGetter);
            installHook(HookGroup.COMPOSE, activityGetter, chain -> {
                Object nativeActivity = chain.proceed();
                if(composeRenderingReady)readComposeRevision.invoke(composeStyleRevision);
                return FEATURES.effective("data","data_activity_hidden") ? 0 : nativeActivity;
            });
            Class<?> painterClass = loader.loadClass("com.android.compose.ui.graphics.painter.DrawablePainter");
            Method painterDrawable = painterClass.getMethod("getDrawable");
            installHook(HookGroup.COMPOSE, painterClass.getDeclaredConstructor(Drawable.class), chain -> {
                if (composeRenderingReady && chain.getArg(0) instanceof ScaledDrawable)
                    ((ScaledDrawable) chain.getArg(0)).composePlacement = true;
                return chain.proceed();
            });
            final Map<Object, Object> bases = new WeakHashMap<>();
            final Map<Object, Object> nativeFilters = new WeakHashMap<>();
            boolean imageInstalled = false;
            for (Method image : loader.loadClass("androidx.compose.foundation.ImageKt").getDeclaredMethods()) {
                if (!image.getName().equals("Image") || image.getParameterCount() != 10
                        || !image.getParameterTypes()[0].getName().equals("androidx.compose.ui.graphics.painter.Painter")) continue;
                deoptimize(image);
                installHook(HookGroup.COMPOSE, image, chain -> {
                    if (!composeRenderingReady) return chain.proceed();
                    Object painter = chain.getArg(0);
                    if (!painterClass.isInstance(painter)) return chain.proceed();
                    Object drawable = painterDrawable.invoke(painter);
                    if (!(drawable instanceof ScaledDrawable) && !(drawable instanceof NativeDataActivity.ArrowDrawable)) return chain.proceed();
                    // Only subscribe to visual revisions here. The native Image restart group
                    // must keep the exact dirty/default bits and remembered slot structure.
                    readComposeRevision.invoke(composeStyleRevision);
                    return chain.proceed();
                });
                imageInstalled = true;
            }
            if (!imageInstalled) throw new NoSuchMethodException("Compose Image renderer");
            Class<?> painterModifier=loader.loadClass("androidx.compose.ui.draw.PainterModifierKt");
            Method paint=painterModifier.getDeclaredMethod("paint$default",bridge.modifier,
                    loader.loadClass("androidx.compose.ui.graphics.painter.Painter"),loader.loadClass("androidx.compose.ui.Alignment"),
                    loader.loadClass("androidx.compose.ui.layout.ContentScale"),Float.TYPE,
                    loader.loadClass("androidx.compose.ui.graphics.ColorFilter"),Integer.TYPE);
            Method modifierThen=bridge.modifier.getMethod("then",bridge.modifier);
            deoptimize(paint);
            installHook(HookGroup.COMPOSE,paint,chain->{
                if(!composeRenderingReady)return chain.proceed();
                Object painter=chain.getArg(1);
                if(!painterClass.isInstance(painter))return chain.proceed();
                Object drawable=painterDrawable.invoke(painter);
                if(drawable instanceof NativeDataActivity.ArrowDrawable) {
                    readComposeRevision.invoke(composeStyleRevision);
                    NativeDataActivity.ArrowDrawable arrow=(NativeDataActivity.ArrowDrawable)drawable;arrow.composePlacement=true;
                    Object[] args=new Object[7];for(int index=0;index<args.length;index++)args[index]=chain.getArg(index);
                    Object base=args[0]==null?bridge.empty:args[0];Object original=bases.get(base);if(original!=null)base=original;
                    Object filter=args[5];if(filter!=null&&nativeFilters.containsKey(filter))filter=nativeFilters.get(filter);
                    args[0]=base;args[5]=filter;
                    if(DATA_ACTIVITY.enabled()) {
                        float density=arrow.density;
                        Object layer=bridge.place(bridge.empty,DATA_ACTIVITY.x(density),DATA_ACTIVITY.y(density),
                                DATA_ACTIVITY.scale(Math.max(arrow.getIntrinsicWidth(),arrow.getIntrinsicHeight())));
                        args[0]=modifierThen.invoke(layer,base);bases.put(args[0],base);
                    }
                    if(DATA_ACTIVITY.colorEnabled()) {
                        int nativeTint=filter==null?arrow.nativeTint:nativeFilterColor((ColorFilter)bridge.nativeFilter.invoke(filter),arrow.nativeTint);
                        arrow.nativeTint=nativeTint;
                        args[5]=bridge.tintFilter.invoke(bridge.filterCompanion,bridge.color.invoke(null,DATA_ACTIVITY.tint(nativeTint)),5);
                        if(args[5]!=null&&args[5]!=filter)nativeFilters.put(args[5],filter);
                    }
                    return args[0]==chain.getArg(0)&&args[5]==chain.getArg(5)?chain.proceed():chain.proceed(args);
                }
                if(!(drawable instanceof ScaledDrawable))return chain.proceed();
                // This call runs inside Image's native restart group, so unchanged Painter
                // arguments still redraw after a style revision without dirty-bit changes.
                readComposeRevision.invoke(composeStyleRevision);
                ScaledDrawable icon=(ScaledDrawable)drawable;icon.composePlacement=true;
                Object[] args=new Object[7];for(int i=0;i<args.length;i++)args[i]=chain.getArg(i);
                Object base=args[0]==null?bridge.empty:args[0];Object original=bases.get(base);
                if(original!=null)base=original;
                Object filter=args[5];
                if(filter!=null&&nativeFilters.containsKey(filter))filter=nativeFilters.get(filter);
                String item=icon.wifiIcon?"wifi":"data";
                args[0]=base;args[5]=filter;
                boolean legacyPlacement=FEATURES.enabled(item);
                if(legacyPlacement||IconManagementRuntime.sizeForRole(icon.sourceRole())!=1f){
                    float d=context==null?icon.density:context.getResources().getDisplayMetrics().density;
                    Object frame=legacyPlacement?bridge.height.invoke(null,bridge.empty,ICON_SLOT_DP):bridge.empty;
                    Object layer=bridge.place(frame,legacyPlacement?NumericPolicy.pixels(icon.wifiIcon?wifiOffsetXdp:dataOffsetXdp,d):0,
                            legacyPlacement?NumericPolicy.pixels(icon.wifiIcon?wifiOffsetYdp:dataOffsetYdp,d):0,
                            icon.composeScale(legacyPlacement?ICON_SLOT_DP*d:Math.max(icon.getIntrinsicWidth(),icon.getIntrinsicHeight())));
                    // Image already appended clipToBounds. Put placement outside the entire
                    // original chain so the native slot clip moves with the visible glyph.
                    args[0]=modifierThen.invoke(layer,base);bases.put(args[0],base);
                }
                if(FEATURES.color(item)){
                    int tint=filter==null?icon.nativeTint:nativeFilterColor((ColorFilter)bridge.nativeFilter.invoke(filter),icon.nativeTint);
                    args[5]=bridge.tintFilter.invoke(bridge.filterCompanion,bridge.color.invoke(null,styleColor(item,tint)),5);
                    if(args[5]!=null&&args[5]!=filter)nativeFilters.put(args[5],filter);
                }
                // Real C17 paint$default has no Composer. Its only mask branches are
                // alignment(4) and alpha(16); preserve both and every untouched argument.
                return args[0]==chain.getArg(0)&&args[5]==chain.getArg(5)?chain.proceed():chain.proceed(args);
            });
            Class<?> textModel = loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel");
            Method type = textModel.getMethod("getNetworkTypeText");
            Method name = loader.loadClass(NETWORK_TYPE).getMethod("getName");
            boolean labelInstalled = false;
            for (Method text : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt").getDeclaredMethods()) {
                if (!text.getName().startsWith("NetworkTypeText-") || text.getParameterCount() != 13) continue;
                deoptimize(text);
                installHook(HookGroup.COMPOSE, text, chain -> {
                    if (!composeRenderingReady) return chain.proceed();
                    readComposeRevision.invoke(composeStyleRevision);
                    // C17 emits the native badge as a Compose node, without mobile_type View IDs.
                    // A separately enabled custom network label still owns its own output below.
                    if (!FEATURES.enabled("label")) {
                        if(FEATURES.hideNativeNetworkBadge())return null;
                        return chain.proceed();
                    }
                    int nativeTint = (Integer) bridge.argb.invoke(null, chain.getArg(2));
                    // Native stacked info is firstOrNull, not necessarily the main traffic
                    // SIM. refreshLabel selects activeDataSubscription for both renderers.
                    String rawLabel = networkLabel;
                    final String shown = FEATURES.hideNetworkLabel() ? "" : RadioState.cellularLabel(FEATURES.enabled("label"),wifiConnected,airplaneMode,
                            rawLabel,networkLabel,false,wifiSwitchEnabled,mobileDataSwitchEnabled);
                    Object base = shown.isEmpty() || chain.getArg(3) == null ? bridge.empty : chain.getArg(3);
                    float d = context == null ? 1f : context.getResources().getDisplayMetrics().density;
                    Object sized = shown.isEmpty() ? bridge.width.invoke(null, base, 0f)
                            : bridge.widthIn.invoke(null, base, 0f,
                                    NumericPolicy.MAX_LAYOUT_PIXELS/(d>0&&Float.isFinite(d)?d:1f));
                    Object frame = bridge.height.invoke(null, sized, ICON_SLOT_DP);
                    Object placed = bridge.place(frame,NumericPolicy.pixels(labelOffsetXdp,d),NumericPolicy.pixels(labelOffsetYdp,d),
                            1f); // X is budgeted inside the glyph; Y stays on the real Android View.
                    if (!shown.isEmpty()) placed = NETWORK_ORDER.textModifier(placed);
                    Object create = bridge.function(ctx -> new NetworkLabelView((Context) ctx));
                    Object update = bridge.function(view -> { ((NetworkLabelView) view).update(shown, nativeTint); return bridge.unit; });
                    bridge.androidView.invoke(null, create, placed, update, chain.getArg(10), 0, 0);
                    if (!composeTextObserved) {
                        composeTextObserved = true;
                        logFrameworkStage(Log.INFO, "Unified status-bar frame, glyph alignment, and theme bridge active");
                    }
                    return null;
                });
                labelInstalled = true;
            }
            // Keep the outer NetworkTypeText slot table untouched. Style only the real
            // content renderer while its exact OEM content lambda is on the stack.
            Class<?> nativeContent=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda2");
            for(Method method:nativeContent.getDeclaredMethods())if(method.getName().equals("invoke")&&method.getParameterCount()==2) {
                deoptimize(method);
                installHook(HookGroup.COMPOSE,method,chain->{
                    try(NativeNetworkBadgeControls.Scope scope=NATIVE_BADGE.enterContent(chain.getThisObject())){return chain.proceed();}
                });
            }
            Class<?> badgeText=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt");
            for(Method text:badgeText.getDeclaredMethods())if(text.getName().startsWith("StackedMobileText-")&&text.getParameterCount()==13) {
                deoptimize(text);
                installHook(HookGroup.COMPOSE,text,chain->{
                    if(!composeRenderingReady)return chain.proceed();
                    readComposeRevision.invoke(composeStyleRevision);
                    if(!NATIVE_BADGE.needsAdjustment(chain.getArg(2),chain.getArg(10)))return chain.proceed();
                    Object[] nativeArgs=chain.getArgs().toArray();
                    Object[] adjusted=NATIVE_BADGE.adjustText(nativeArgs,context==null?1f:context.getResources().getDisplayMetrics().density,context);
                    try(NativeNetworkBadgeControls.TextScope scope=NATIVE_BADGE.enterText(adjusted,context)) {
                        return adjusted==nativeArgs?chain.proceed():chain.proceed(adjusted);
                    }
                });
            }
            installNativeBadgeParts(loader);
            Class<?> nativeRestart=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda5");
            for(Method method:nativeRestart.getDeclaredMethods())if(method.getName().equals("invoke"))deoptimize(method);
            if (!labelInstalled) throw new NoSuchMethodException("Compose network label");
            // Tigo's branded 5G image bypasses the text renderer. Its ordinary text delegates
            // to Oplus below, so only hide here; applying font/position twice would compound it.
            try {
                Class<?> tigo=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.TigoFiveGStackedMobileIconLayoutStrategyKt");
                for(Method badge:tigo.getDeclaredMethods()) {
                    if(!badge.getName().startsWith("TigoFiveGNetworkTypeText-")||badge.getParameterCount()!=13)continue;
                    deoptimize(badge);
                    installHook(HookGroup.COMPOSE,badge,chain->{
                        if(!composeRenderingReady)return chain.proceed();
                        readComposeRevision.invoke(composeStyleRevision);
                        if(FEATURES.enabled("label"))return FEATURES.hideNetworkLabel()?null:chain.proceed();
                        if(FEATURES.hideNativeNetworkBadge())return null;
                        return chain.proceed();
                    });
                }
            } catch (ClassNotFoundException unavailableVariant) { }
            for(String callerName:new String[]{"DefaultStackedMobileIconLayoutStrategy",
                    "BigTypeStackedMobileIconLayoutStrategy","TigoFiveGStackedMobileIconLayoutStrategy",
                    "OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda0",
                    "TigoFiveGStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda0"})try {
                for(Method caller:loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable."+callerName).getDeclaredMethods())
                    if(caller.getName().equals("OplusStackedMobileIcon")||caller.getName().equals("invoke"))deoptimize(caller);
            } catch (ClassNotFoundException unavailableVariant) { }
            for (Method caller : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconKt").getDeclaredMethods())
                if (caller.getName().equals("OplusStackedMobileIcon")) deoptimize(caller);
            for (Method caller : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategy").getDeclaredMethods())
                if (caller.getName().startsWith("SignalIcon-")) deoptimize(caller);
            Class<?> modern = loader.loadClass(MODERN_VIEW);
            final Method readSlot=modern.getMethod("getSlot");
            installHook(HookGroup.COMPOSE, modern.getDeclaredMethod("initView", String.class, loader.loadClass("kotlin.jvm.functions.Function0")), chain -> {
                Object result = chain.proceed();
                if(isNetworkSlot((String)chain.getArg(0)))MAIN.post(() -> allowDrawableOverflow((View) chain.getThisObject()));
                return result;
            });
            installHook(HookGroup.COMPOSE,modern.getDeclaredMethod("onLayout",Boolean.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE),chain -> {
                Object result=chain.proceed();
                if(isNetworkSlot((String)readSlot.invoke(chain.getThisObject())))allowDrawableOverflow((View)chain.getThisObject());
                return result;
            });
            installHook(HookGroup.COMPOSE,View.class.getDeclaredMethod("onAttachedToWindow"),chain -> {
                Object result=chain.proceed();View view=(View)chain.getThisObject();
                if(modern.isInstance(view)&&isNetworkSlot((String)readSlot.invoke(view)))MAIN.post(() -> allowDrawableOverflow(view));
                return result;
            });
            composeRenderingReady = true;
            moduleLog(Log.INFO, TAG, "Unified Compose rendering installed; legacy label layout is inactive");
            return true;
        } catch (Throwable error) {
            // A Compose system must never start the legacy Wi-Fi label renderer after partial setup.
            composeRenderingReady = false;
            moduleLog(Log.ERROR, TAG, "Could not bind unified Compose status bar", error);
            return false;
        }
    }

    /** The OEM builds one AnnotatedString from two native spans. Keep its node/slot
     * structure, and scope the platform glyph offsets to that text's drawWithContent. */
    private void installNativeBadgeParts(ClassLoader loader) {
        NATIVE_BADGE.setTypographyReady(false);
        NATIVE_BADGE.setDrawRunsReady(false);
        try {
            Class<?> node=loader.loadClass("androidx.compose.foundation.text.modifiers.TextAnnotatedStringNode");
            Field overflow=node.getField("overflow");
            Object companion=loader.loadClass("androidx.compose.ui.text.style.TextOverflow").getField("Companion").get(null);
            int visible=((Number)companion.getClass().getMethod("getVisible-gIe3tQ8").invoke(companion)).intValue();
            Method draw=node.getMethod("draw",loader.loadClass("androidx.compose.ui.graphics.drawscope.ContentDrawScope"));
            deoptimize(draw);
            installHook(HookGroup.COMPOSE,draw,chain->{
                if(!NATIVE_BADGE.needsRunAdjustment())return chain.proceed();
                Object owner=chain.getThisObject();int nativeOverflow=overflow.getInt(owner);
                overflow.setInt(owner,visible);
                try { return chain.proceed(); }
                finally { overflow.setInt(owner,nativeOverflow); }
            });
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            moduleLog(Log.INFO,TAG,"Optional native badge text overflow unavailable",unavailable);
        }
        try {
            Class<?> span=loader.loadClass("androidx.compose.ui.text.SpanStyle");
            Constructor<?> constructor=span.getDeclaredConstructor(Long.TYPE,Long.TYPE,
                    loader.loadClass("androidx.compose.ui.text.font.FontWeight"),
                    loader.loadClass("androidx.compose.ui.text.font.FontStyle"),
                    loader.loadClass("androidx.compose.ui.text.font.FontSynthesis"),
                    loader.loadClass("androidx.compose.ui.text.font.FontFamily"),String.class,Long.TYPE,
                    loader.loadClass("androidx.compose.ui.text.style.BaselineShift"),
                    loader.loadClass("androidx.compose.ui.text.style.TextGeometricTransform"),
                    loader.loadClass("androidx.compose.ui.text.intl.LocaleList"),Long.TYPE,
                    loader.loadClass("androidx.compose.ui.text.style.TextDecoration"),
                    loader.loadClass("androidx.compose.ui.graphics.Shadow"),Integer.TYPE);
            installHook(HookGroup.COMPOSE,constructor,chain->{
                if(!NATIVE_BADGE.needsSpanAdjustment())return chain.proceed();
                Object[] nativeArgs=chain.getArgs().toArray();
                Object[] adjusted=NATIVE_BADGE.adjustSpanStyle(nativeArgs);
                return adjusted==nativeArgs?chain.proceed():chain.proceed(adjusted);
            });
            moduleLog(Log.INFO,TAG,"Native badge typography "+(NATIVE_BADGE.setTypographyReady(true)?"connected":"unavailable"));
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            NATIVE_BADGE.setTypographyReady(false);
            moduleLog(Log.INFO,TAG,"Optional native badge typography unavailable",unavailable);
        }
        try {
            Set<Method> draws=new HashSet<>();
            boolean chars=false,sequence=false;
            for(String name:new String[]{"android.graphics.Canvas","android.graphics.BaseRecordingCanvas","android.graphics.RecordingCanvas"}) {
                Class<?> canvas;
                try{canvas=loader.loadClass(name);}catch(ClassNotFoundException optional){continue;}
                for(Class<?> input:new Class<?>[]{CharSequence.class,char[].class}) {
                    Method draw;
                    try{draw=canvas.getDeclaredMethod("drawTextRun",input,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
                            Float.TYPE,Float.TYPE,Boolean.TYPE,Paint.class);}
                    catch(NoSuchMethodException inherited){continue;}
                    if(Modifier.isAbstract(draw.getModifiers())||!draws.add(draw))continue;
                    final boolean array=input==char[].class;
                    deoptimize(draw);
                    installHook(HookGroup.COMPOSE,draw,chain->{
                        if(!NATIVE_BADGE.needsRunAdjustment())return chain.proceed();
                        try(NativeNetworkBadgeControls.RunScope scope=NATIVE_BADGE.enterRun()) {
                            Object[] nativeArgs=chain.getArgs().toArray();Object[] adjusted=scope.adjust(nativeArgs,array);
                            return adjusted==nativeArgs?chain.proceed():chain.proceed(adjusted);
                        }
                    });
                    if(array)chars=true;else sequence=true;
                }
            }
            if(!chars||!sequence)throw new NoSuchMethodException("Native badge drawTextRun");
            moduleLog(Log.INFO,TAG,"Native two-part network badge drawing "+(NATIVE_BADGE.setDrawRunsReady(true)?"connected":"unavailable"));
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable) {
            NATIVE_BADGE.setDrawRunsReady(false);
            moduleLog(Log.INFO,TAG,"Optional native badge offsets unavailable; retaining typography",unavailable);
        }
    }

    private static void invalidateComposeStyle() {
        if (composeStyleRevision == null || readComposeRevision == null || writeComposeRevision == null) {
            return;
        }
        try {
            int revision = ((Integer) readComposeRevision.invoke(composeStyleRevision)).intValue();
            writeComposeRevision.invoke(composeStyleRevision, revision + 1);
        } catch (Throwable error) {
            logFrameworkStage(Log.WARN, "Could not refresh Compose status-bar controls", error);
        }
    }

    private boolean installSystemUiContextHook(ClassLoader classLoader) {
        try {
            installHook(HookGroup.CONTEXT, classLoader.loadClass("android.app.Application").getDeclaredMethod("attach", Context.class), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda0
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installSystemUiContextHook$0(chain);
                }
            });
            moduleLog(Log.INFO, TAG, "Early SystemUI application context hook installed");
            return true;
        } catch (Throwable th) {
            moduleLog(Log.ERROR, TAG, "Could not bind early SystemUI context initialization", th);
            return false;
        }
    }

    /** Observe the concrete OS17 result; QuickSettingsController may return false for a missing Lazy. */
    private boolean installPanelModeHooks(ClassLoader loader) {
        boolean installed = false;
        String[][] readers = {
                {"com.oplus.systemui.separate.data.repository.OplusPanelModeRepository", "resolveSeparateMode"},
                {"com.oplus.systemui.separate.OplusSeparateNotificationAndQSExImpl", "enableSeparateNotificationAndQS"}};
        for (String[] reader : readers) try {
            Method method = loader.loadClass(reader[0]).getDeclaredMethod(reader[1]);
            if (method.getReturnType() != Boolean.TYPE) continue;
            deoptimize(method);
            installHook(HookGroup.PANEL_MODE, method, chain -> {
                Object result = chain.proceed();
                if (result instanceof Boolean && PanelMode.report((Boolean) result)) schedulePanelModeChanged();
                return result;
            });
            installed = true;
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO, TAG, "Optional native panel mode reader unavailable: " + reader[0]);
        }
        return installed;
    }

    /** One fixed shade window draws behind both horizontally moving native pages. */
    private boolean installShadeWallpaperHooks(ClassLoader loader) {
        if (!ShadeWallpaperSettings.available()) return true;
        try {
            Class<?> root = loader.loadClass("com.android.systemui.shade.NotificationShadeWindowView");
            Class<?> pager = loader.loadClass("com.oplus.systemui.separate.OplusPanelViewPager");
            Method dispatch = requiredMethod(root, "drawChild", Canvas.class, View.class, Long.TYPE);
            deoptimize(dispatch);
            // The picture lives in a window of its own under the shade, so the shade's glass is made of it; painting inside the shade is the fallback.
            SHADE_WALLPAPER.useLayer(new ShadeWallpaperWindow(SHADE_WALLPAPER));
            installHook(HookGroup.SHADE_WALLPAPER, dispatch, chain -> {
                // super_notification_shade places the native blur/scrims before this pager.
                // Paint after those siblings, before the pager's own transform and controls.
                if (root.isInstance(chain.getThisObject()) && pager.isInstance(chain.getArg(1)))
                    SHADE_WALLPAPER.draw((View) chain.getThisObject(), (Canvas) chain.getArg(0));
                return chain.proceed();
            });
            Method detached = requiredMethod(root, "onDetachedFromWindow");
            installHook(HookGroup.SHADE_WALLPAPER, detached, chain -> {
                SHADE_WALLPAPER.onDetached((View) chain.getThisObject());
                WALLPAPER_PANEL.detached((View) chain.getThisObject()); return chain.proceed();
            });
            installShadeWallpaperHorizontalHooks(loader);
            installShadeWallpaperQsHooks(loader);
            installShadeWallpaperScrimBlurHook(loader);
            return true;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Fixed native shade wallpaper host unavailable; retaining native background", unavailable);
            return false;
        }
    }

    /** The picture is a window behind the shade, so the system's full-screen blur of what is behind the shade gives up the picture's share. */
    private void installShadeWallpaperScrimBlurHook(ClassLoader loader) {
        try {
            Class<?> scrim = loader.loadClass(ShadeWallpaperScrimBlur.SCRIM);
            Class<?> base = loader.loadClass(ShadeWallpaperScrimBlur.SCRIM_BASE);
            Method setter = requiredMethod(scrim, "setBlurAmount", Float.TYPE, String.class);
            SHADE_SCRIM_BLUR.bind(base, setter);
            deoptimize(setter);
            installHook(HookGroup.SHADE_WALLPAPER, setter, chain -> {
                float asked = ((Number) chain.getArg(0)).floatValue();
                float used = SHADE_SCRIM_BLUR.amount(chain.getThisObject(), asked);
                return used == asked ? chain.proceed() : chain.proceed(new Object[]{used, chain.getArg(1)});
            });
        } catch (Throwable unavailable) {
            moduleLog(Log.INFO, TAG, "Native shade scrim blur unavailable; the picture keeps the system's blur on top", unavailable);
        }
    }

    private static View fixedShadeRoot(View panel) {
        if (panel == null) return null;
        View root = panel.getRootView();
        for (Class<?> type = root == null ? null : root.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.android.systemui.shade.NotificationShadeWindowView")) return root;
        return null;
    }

    /** Notification and separate-QS have independent vertical springs in the same fixed window. */
    private static final class WallpaperPanelState {
        WeakReference<View> root = new WeakReference<>(null);
        float notificationFraction, qsFraction;
        boolean notificationClosed = true, qsClosed = true;
        int state = -1;
        void changed(View panel, boolean qs, int barState, float fraction, boolean closed) {
            if (!ShadeWallpaperSettings.available()) return;
            View fixed = fixedShadeRoot(panel);
            if (fixed == null || !Float.isFinite(fraction)) return;
            if (root.get() != fixed) { reset(); root = new WeakReference<>(fixed); }
            state = barState;
            if (qs) { qsFraction = fraction; qsClosed = closed; }
            else { notificationFraction = fraction; notificationClosed = closed; }
            publish();
        }
        void qsDetached(View panel) {
            if (!ShadeWallpaperSettings.available()) return;
            if (fixedShadeRoot(panel) != root.get()) return;
            qsFraction = 0f; qsClosed = true; publish();
        }
        void publish() {
            if (!ShadeWallpaperSettings.available()) return;
            boolean separate = PanelMode.SEPARATE.equals(PanelMode.current());
            if (separate) {
                if (notificationClosed && !qsClosed) SHADE_WALLPAPER.onHorizontalProgress(1f);
                else if (qsClosed && !notificationClosed) SHADE_WALLPAPER.onHorizontalProgress(0f);
            }
            SHADE_WALLPAPER.onPanelChanged(root.get(), PanelMode.current(), state,
                    ShadeWallpaper.pullOf(separate, notificationFraction, qsFraction),
                    notificationClosed && (!separate || qsClosed));
        }
        void detached(View view) { if (root.get() == view) reset(); }
        void reset() { root.clear(); notificationFraction = qsFraction = 0f; notificationClosed = qsClosed = true; state = -1; }
    }

    private void installShadeWallpaperQsHooks(ClassLoader loader) {
        try {
            Class<?> component = loader.loadClass(StatusBarQsIconAccess.COMPONENT);
            Class<?> listener = loader.loadClass(StatusBarQsIconAccess.LISTENER);
            Field listenerOwner = accessibleField(listener, "this$0");
            WallpaperQsAccess access = new WallpaperQsAccess(component);
            for (Method event : listener.getDeclaredMethods()) {
                if (!event.getName().matches("onFractionChanged|onFractionEnd")) continue;
                deoptimize(event);
                installHook(HookGroup.SHADE_WALLPAPER, event, chain -> {
                    Object result = chain.proceed(); access.changed(listenerOwner.get(chain.getThisObject())); return result;
                });
            }
            for (Method event : component.getDeclaredMethods()) {
                String name = event.getName();
                if (!name.matches("onViewAttached|onViewDetached|onDestroy|onTrackingStarted\\$1|onTrackingStopped\\$1"
                        + "|expandQSPanelWithoutAnimate|collapseQSPanelWithoutAnimate|updateStatusBarTopMargin")) continue;
                deoptimize(event);
                installHook(HookGroup.SHADE_WALLPAPER, event, chain -> {
                    Object result = chain.proceed();
                    if (name.equals("onViewDetached") || name.equals("onDestroy")) access.detached(chain.getThisObject());
                    else access.changed(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Separate-QS wallpaper visibility unavailable; notification/combined panel retained", unavailable);
        }
    }

    private static final class WallpaperQsAccess {
        final Field view, fraction, stub;
        final Method keyguard;
        WeakReference<Object> current = new WeakReference<>(null);
        WeakReference<View> panel = new WeakReference<>(null);
        WallpaperQsAccess(Class<?> component) throws ReflectiveOperationException {
            view = accessibleField(component, "mView"); fraction = accessibleField(component, "curRawFraction");
            stub = accessibleField(component, "stub"); keyguard = requiredMethod(stub.getType(), "isKeyguardShowing");
        }
        void changed(Object component) {
            try {
                View nativePanel = (View) view.get(component);
                Object host = stub.get(component);
                if (nativePanel == null || !nativePanel.isAttachedToWindow() || host == null) { detached(component); return; }
                float displayed = fraction.getFloat(component);
                if (!Float.isFinite(displayed)) return;
                current = new WeakReference<>(component); panel = new WeakReference<>(nativePanel);
                WALLPAPER_PANEL.changed(nativePanel, true, Boolean.TRUE.equals(keyguard.invoke(host)) ? 1 : 0,
                        displayed, displayed <= 0f);
            } catch (ReflectiveOperationException | RuntimeException unavailable) { detached(component); }
        }
        void detached(Object component) {
            if (current.get() != component) return;
            WALLPAPER_PANEL.qsDetached(panel.get()); current.clear(); panel.clear();
        }
    }

    private void installShadeWallpaperHorizontalHooks(ClassLoader loader) {
        try {
            Class<?> pager = loader.loadClass("com.oplus.systemui.separate.OplusPanelViewPagerController");
            StatusBarIconPageMotion motion = new StatusBarIconPageMotion(loader, pager);
            WallpaperHorizontalAccess access = new WallpaperHorizontalAccess(pager, motion);
            deoptimize(motion.animatedValue);
            installHook(HookGroup.SHADE_WALLPAPER, motion.animatedValue, chain -> {
                Object result = chain.proceed();
                if (motion.owns(chain.getThisObject())) access.animated();
                return result;
            });
            for (Method event : pager.getDeclaredMethods()) {
                if (!event.getName().matches("onInit|ensureProgressiveHelperReady|resetHorizontalTranslationView|syncHorizontalTranslationDown")) continue;
                deoptimize(event);
                installHook(HookGroup.SHADE_WALLPAPER, event, chain -> {
                    Object result = chain.proceed(); access.bind(chain.getThisObject(),
                            event.getName().equals("onInit") || event.getName().equals("resetHorizontalTranslationView")); return result;
                });
            }
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Native wallpaper horizontal spring unavailable; static page background retained", unavailable);
        }
    }

    private static final class WallpaperHorizontalAccess {
        final Field origin;
        final Method readablePanel;
        final StatusBarIconPageMotion motion;
        WeakReference<Object> pager = new WeakReference<>(null);
        WallpaperHorizontalAccess(Class<?> type, StatusBarIconPageMotion motion) throws ReflectiveOperationException {
            this.motion = motion; origin = accessibleField(type, "downExpanded");
            readablePanel = requiredMethod(type, "resolveReadablePanelForTranslationX");
        }
        void bind(Object controller, boolean settledEndpoint) {
            pager = new WeakReference<>(controller); motion.bind(controller);
            if (!settledEndpoint) { animated(); return; }
            try {
                // Native reset sets both X values to zero. Its readable-page resolver
                // uses visible expansion and lastDownExpanded, including cancelled swipes.
                Object selected = readablePanel.invoke(controller);
                if (!(selected instanceof Enum)) return;
                String name = ((Enum<?>) selected).name();
                if ("QS".equals(name)) SHADE_WALLPAPER.onHorizontalProgress(1f);
                else if ("NOTIFICATION".equals(name)) SHADE_WALLPAPER.onHorizontalProgress(0f);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        void animated() {
            Object controller = pager.get(); if (controller == null) return;
            try {
                Object selected = origin.get(controller);
                if (!(selected instanceof Enum)) return;
                String name = ((Enum<?>) selected).name();
                if ("QS".equals(name)) SHADE_WALLPAPER.onHorizontalProgress(1f - motion.fraction());
                else if ("NOTIFICATION".equals(name)) SHADE_WALLPAPER.onHorizontalProgress(motion.fraction());
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }

    private static void schedulePanelModeChanged() {
        MAIN.removeCallbacks(PANEL_MODE_CHANGED); MAIN.post(PANEL_MODE_CHANGED);
    }

    private static void registerPanelModeObserver() {
        if (context == null || panelModeObserver != null || ModuleLifecycle.removed()) return;
        PanelMode.query(context);
        ContentObserver observer = new ContentObserver(MAIN) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                String before = PanelMode.current();
                PanelMode.query(context);
                if (!before.equals(PanelMode.current())) schedulePanelModeChanged();
            }
        };
        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(android.provider.Settings.System.getUriFor(PanelMode.SETTING), false, observer);
            resolver.registerContentObserver(android.provider.Settings.System.getUriFor(PanelMode.DEFAULT_SETTING), false, observer);
            panelModeObserver = observer;
        } catch (RuntimeException unavailable) {
            try { context.getContentResolver().unregisterContentObserver(observer); } catch (RuntimeException ignored) { }
            logFrameworkStage(Log.WARN, "Panel mode observer unavailable; native repository reporting retained", unavailable);
        }
    }

    static /* synthetic */ Object lambda$installSystemUiContextHook$0(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Object thisObject = chain.getThisObject();
        if (thisObject instanceof Context) {
            Context context2 = (Context) thisObject;
            if ("com.android.systemui".equals(context2.getPackageName())) {
                initializeContext(context2);
                logFrameworkStage(Log.INFO, "SystemUI context initialized before status-bar views");
            }
        }
        return objProceed;
    }

    private boolean installDrawableOverrides(ClassLoader classLoader) {
        try {
            Class<?> clsLoadClass = classLoader.loadClass("android.content.res.Resources");
            for(Method getter:clsLoadClass.getDeclaredMethods())if(getter.getName().equals("getDrawable")||getter.getName().equals("getDrawableForDensity"))deoptimize(getter);
            try {
                Method compat=classLoader.loadClass("androidx.appcompat.content.res.AppCompatResources").getDeclaredMethod("getDrawable",Context.class,Integer.TYPE);
                deoptimize(compat);
                installHook(HookGroup.DRAWABLE,compat,chain->{Object original=chain.proceed();return original instanceof Drawable
                        ?DATA_ACTIVITY.wrap(((Context)chain.getArg(0)).getResources(),(Integer)chain.getArg(1),(Drawable)original):original;});
            }catch(ClassNotFoundException|NoSuchMethodException legacy){/* Framework drawable path remains available. */}
            installHook(HookGroup.DRAWABLE, clsLoadClass.getDeclaredMethod("getDrawable", Integer.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda11
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installDrawableOverrides$1(chain);
                }
            });
            installHook(HookGroup.DRAWABLE, clsLoadClass.getDeclaredMethod("getDrawable", Integer.TYPE, Resources.Theme.class), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda12
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installDrawableOverrides$2(chain);
                }
            });
            installHook(HookGroup.DRAWABLE, clsLoadClass.getDeclaredMethod("getDrawableForDensity", Integer.TYPE, Integer.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda13
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installDrawableOverrides$3(chain);
                }
            });
            installHook(HookGroup.DRAWABLE, clsLoadClass.getDeclaredMethod("getDrawableForDensity", Integer.TYPE, Integer.TYPE, Resources.Theme.class), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda14
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installDrawableOverrides$4(chain);
                }
            });
            moduleLog(Log.INFO, TAG, "SystemUI drawable replacement hooks installed");
            return true;
        } catch (Throwable th) {
            moduleLog(Log.ERROR, TAG, "Could not bind SystemUI drawable resources", th);
            return false;
        }
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$1(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue(), (Drawable) objProceed);
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$2(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue(), (Drawable) objProceed);
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$3(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue(), (Drawable) objProceed);
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$4(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue(), (Drawable) objProceed);
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    private static Drawable replacementDrawable(Resources resources, int i, Drawable nativeDrawable) {
        if(BigClockSystemIcon.nativeLookupActive())return null;
        // DrawablePainter owns Animatable start/stop by runtime type. Do not hide
        // an unaudited animated native signal behind a static resource wrapper.
        if(nativeDrawable instanceof android.graphics.drawable.Animatable)return null;
        Drawable arrow=DATA_ACTIVITY.wrap(resources,i,nativeDrawable);if(arrow!=nativeDrawable)return arrow;
        if(nativeDrawable instanceof ScaledDrawable)return nativeDrawable;
        if (i == 0 || moduleResources == null) return null;
        try {
            if (!"com.android.systemui".equals(resources.getResourcePackageName(i))) return null;
            String source = resources.getResourceEntryName(i);
            String asset = SignalResources.moduleName(source, singleSignal);
            if (asset == null) return null;
            Drawable drawable = loadModuleDrawable(asset, resources);
            if (drawable == null) return null;
            ScaledDrawable result = new ScaledDrawable(drawable, resources, source.startsWith("stat_signal_wifi_signal_"), nativeDrawable);
            result.sourceName = source; result.assetName = asset; result.baseResources = resources;
            result.reportNetworkWidth();
            LIVE_DRAWABLES.add(new WeakReference<>(result));
            return result;
        } catch (Throwable failure) { return null; }
    }

    private static Drawable loadModuleDrawable(String name, Resources base) {
        if (moduleResources == null) return null;
        try {
            Drawable.ConstantState state = ICONS.get(name);
            if (state == null) {
                int id = moduleResources.getIdentifier(name, "drawable", MODULE_PACKAGE);
                if (id == 0) return null;
                Drawable drawable = moduleResources.getDrawable(id).mutate();
                state = drawable.getConstantState();
                if (state == null) return drawable;
                ICONS.put(name, state);
            }
            return state.newDrawable(base).mutate();
        } catch (Throwable missing) { return null; }
    }

    private static boolean isManagedDrawable(String str) {
        return SignalResources.moduleName(str, singleSignal) != null;
    }

    private boolean installBadgeHiding(ClassLoader loader) {
        try {
            Method wifiMeasure=View.class.getDeclaredMethod("measure",Integer.TYPE,Integer.TYPE);deoptimize(wifiMeasure);
            installHook(HookGroup.BADGE,wifiMeasure,chain->{
                WifiPlacement.beforeMeasure((View)chain.getThisObject(),BADGES);return chain.proceed();
            });
            Method wifiFrameMeasure=android.widget.FrameLayout.class.getDeclaredMethod("onMeasure",Integer.TYPE,Integer.TYPE);deoptimize(wifiFrameMeasure);
            installHook(HookGroup.BADGE,wifiFrameMeasure,chain->{
                WifiPlacement.beforeMeasure((View)chain.getThisObject(),BADGES);return chain.proceed();
            });
            deoptimize(ViewGroup.class.getDeclaredMethod("measureChild",View.class,Integer.TYPE,Integer.TYPE));
            deoptimize(ViewGroup.class.getDeclaredMethod("measureChildWithMargins",View.class,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE));
            deoptimize(android.widget.LinearLayout.class.getDeclaredMethod("onMeasure",Integer.TYPE,Integer.TYPE));
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("setVisibility", Integer.TYPE), chain -> {
                View view=(View)chain.getThisObject();
                int requested=(Integer)chain.getArg(0);
                int actual=BADGES.requestedVisibility(view,requested);
                return actual==requested?chain.proceed():chain.proceed(new Object[]{actual});
            });
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("setId", Integer.TYPE), chain -> {
                Object result=chain.proceed();BADGES.idChanged((View)chain.getThisObject());return result;
            });
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("onAttachedToWindow"), chain -> {
                Object result=chain.proceed();BADGES.apply((View)chain.getThisObject());return result;
            });
            installHook(HookGroup.BADGE, LayoutInflater.class.getDeclaredMethod("inflate", Integer.TYPE, ViewGroup.class, Boolean.TYPE), chain -> {
                Object result=chain.proceed();BADGES.tree((View)result);return result;
            });
            // Vendor coroutine code may have inlined visibility setters; do not let an activity image draw through.
            installHook(HookGroup.BADGE, ImageView.class.getDeclaredMethod("onDraw", Canvas.class), chain ->
                    BADGES.suppressDraw((View)chain.getThisObject())?null:chain.proceed());
            for(String owner:new String[]{"com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl",
                    "com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder"}) {
                try {
                    Class<?> type=loader.loadClass(owner);
                    for(Method method:type.getDeclaredMethods()) {
                        if(method.getName().equals("bindEx")||method.getName().equals("bind")) {
                            deoptimize(method);
                            if(method.getParameterCount()>0&&View.class.isAssignableFrom(method.getParameterTypes()[0]))
                                installHook(HookGroup.BADGE,method,chain->{Object result=chain.proceed();BADGES.tree((View)chain.getArg(0));return result;});
                        }
                    }
                }catch(ClassNotFoundException unsupported){}
            }
            String prefix="com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl";
            for(String suffix:new String[]{"$bindEx$1$1","$bindEx$1","$bindEx$2$1$1","$bindEx$2$1$2$1",
                    "$bindEx$2$1$2$2","$bindEx$2$1$2","$bindEx$2$1$3","$bindEx$2$1$4$1",
                    "$bindEx$2$1$4","$bindEx$2$1","$bindEx$2","$bindEx$3"}) {
                try {
                    for(Method method:loader.loadClass(prefix+suffix).getDeclaredMethods())
                        if(method.getName().equals("invokeSuspend")||method.getName().equals("emit"))deoptimize(method);
                }catch(ClassNotFoundException unsupported){}
            }
            for(String suffix:new String[]{"$bind$1$1$5","$bind$1$1$6","$bind$1$1$7"})try {
                for(Method method:loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder"+suffix).getDeclaredMethods())
                    if(method.getName().equals("invokeSuspend")||method.getName().equals("emit"))deoptimize(method);
            }catch(ClassNotFoundException unsupported) { }
            installQsEntranceGuards(loader);
            moduleLog(Log.INFO,TAG,"Independent network badge controls and vendor Wi-Fi activity guard installed");
            return true;
        }catch(Throwable error){moduleLog(Log.ERROR,TAG,"Could not bind network badge controls",error);return false;}
    }

    private void installQsEntranceGuards(ClassLoader loader) {
        try {
            QsEntrances.Access access=QsEntrances.bind(loader.loadClass(QsEntrances.COMPONENT));
            for(Method event:access.events)installHook(HookGroup.BADGE,event,chain->{
                QsEntrances.Update update=access.begin(chain.getThisObject(),BADGES);
                try{return chain.proceed();}finally{
                    try{access.capture(chain.getThisObject(),update);}finally{update.finish();}
                }
            });
            moduleLog(Log.INFO,TAG,"Control-centre entrance resource guards installed");
        }catch(ClassNotFoundException unsupported){ }
        catch(Throwable failure){moduleLog(Log.WARN,TAG,"Could not bind control-centre entrance resource guards",failure);}
        try {
            Method arrangement=loader.loadClass(QsEntrances.CONTAINER).getDeclaredMethod("applyViewArrangement",Boolean.TYPE);
            installHook(HookGroup.BADGE,arrangement,chain->{
                QsEntrances.Update update=QsEntrances.begin((View)chain.getThisObject(),BADGES);
                try{return chain.proceed();}finally{
                    try{QsEntrances.capture((View)chain.getThisObject(),update);}finally{update.finish();}
                }
            });
        }catch(ClassNotFoundException unsupported){ }
        catch(Throwable failure){moduleLog(Log.WARN,TAG,"Could not bind control-centre entrance arrangement guard",failure);}
        try {
            Method touch=loader.loadClass(QsEntrances.BUTTON).getDeclaredMethod("dispatchTouchEvent",android.view.MotionEvent.class);
            installHook(HookGroup.BADGE,touch,chain->QsEntrances.blockTouch((View)chain.getThisObject(),BADGES)?false:chain.proceed());
        }catch(ClassNotFoundException unsupported){ }
        catch(Throwable failure){moduleLog(Log.WARN,TAG,"Could not bind control-centre edit touch guard",failure);}
    }

    private boolean installNetworkModelHooks(ClassLoader classLoader) {
        try {
            Class<?> clsLoadClass = classLoader.loadClass(MOBILE_VM);
            getSubscriptionId = clsLoadClass.getMethod("getSubscriptionId", new Class[0]);
            getNetworkTypeFlow = clsLoadClass.getMethod("getNetworkTypeText", new Class[0]);
            getFlowValue = classLoader.loadClass("kotlinx.coroutines.flow.StateFlow").getMethod("getValue", new Class[0]);
            getTypeModel = classLoader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel").getMethod("getNetworkTypeText", new Class[0]);
            Class<?> clsLoadClass2 = classLoader.loadClass(NETWORK_TYPE);
            getTypeName = clsLoadClass2.getMethod("getName", new Class[0]);
            Class<?> cls = Class.forName("android.telephony.SubscriptionManager");
            getActiveDataSubId = cls.getMethod("getActiveDataSubscriptionId", new Class[0]);
            getDefaultDataSubId = cls.getMethod("getDefaultDataSubscriptionId", new Class[0]);
            installHook(HookGroup.MODEL, classLoader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconsViewModel").getDeclaredMethod("viewModelForSub", Integer.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda15
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installNetworkModelHooks$6(chain);
                }
            });
            installHook(HookGroup.MODEL, classLoader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusCellularIconViewModelImpl$networkTypeText$1").getDeclaredMethod("invokeSuspend", Object.class), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda16
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installNetworkModelHooks$7(chain);
                }
            });
            installSingleNetworkLabelHooks(classLoader);
            moduleLog(Log.INFO, TAG, "Subscription-aware network label hooks installed");
            return true;
        } catch (Throwable th) {
            moduleLog(Log.ERROR, TAG, "Could not bind the native network type model", th);
            return false;
        }
    }

    private void installSingleNetworkLabelHooks(ClassLoader loader) {
        installSingleMobileIconHooks(loader);
        try {
            try { SINGLE_LABEL.resolve(loader); }
            catch(ReflectiveOperationException|LinkageError unavailable) {
                moduleLog(Log.INFO,TAG,"Optional independent custom network label binding unavailable",unavailable);
            }
            try { NATIVE_BADGE_BINDINGS.resolve(loader); }
            catch(ReflectiveOperationException|LinkageError unavailable) {
                moduleLog(Log.INFO,TAG,"Optional independent native badge binding unavailable",unavailable);
            }
            Class<?> binding=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding");
            for(Method method:binding.getDeclaredMethods()) {
                String name=method.getName();
                boolean modelEvent=name.equals("bindNetworkTypeText")&&method.getParameterCount()==1;
                boolean styleEvent=(name.equals("initViews")||name.equals("updateTint"))&&method.getParameterCount()==1;
                boolean fontEvent=name.equals("access$setMobileTypeTextTypeface")&&method.getParameterCount()==4;
                if(!modelEvent&&!styleEvent&&!fontEvent)continue;deoptimize(method);
                installHook(HookGroup.MODEL,method,chain->{
                    Object owner=fontEvent?chain.getArg(0):chain.getThisObject();
                    try(NativeNetworkBadgeBindings.NativeScope badge=NATIVE_BADGE_BINDINGS.beforeNative(owner,modelEvent,modelEvent?chain.getArg(0):null);
                            SingleNetworkLabelControls.NativeScope scope=SINGLE_LABEL.beforeNative(owner,modelEvent,modelEvent?chain.getArg(0):null)) {
                        Object result=chain.proceed();
                        if(modelEvent)scheduleRefresh();
                        return result;
                    }
                });
            }
            // These real C17 flow collectors inline bind/style calls on release builds.
            for(String suffix:new String[]{"$bindSubclassFlows$1$1","$bindSubclassFlows$2$1","$bindSubclassFlows$2"})try {
                for(Method method:loader.loadClass(binding.getName()+suffix).getDeclaredMethods())
                    if(method.getName().equals("invokeSuspend")||method.getName().equals("emit"))deoptimize(method);
            }catch(ClassNotFoundException unsupported){ }
            moduleLog(Log.INFO,TAG,"Single-SIM primary data network label binder installed");
        }catch(ReflectiveOperationException|LinkageError unavailable){
            moduleLog(Log.INFO,TAG,"Optional single-SIM network label binder unavailable",unavailable);
        }
    }

    private void installSingleMobileIconHooks(ClassLoader loader) {
        try {
            SINGLE_SIGNAL.resolve(loader,drawable->drawable instanceof ScaledDrawable);
            Class<?> base=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.AbstractOplusStatusBarMobileViewBinder$Binding");
            final Class<?> textBinding=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding");
            for(String name:new String[]{base.getName(),"com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding",
                    "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.LegacyOplusStatusBarMobileViewBinder$Os16Binding"}) {
                Class<?> binding;
                try{binding=loader.loadClass(name);}catch(ClassNotFoundException absent){continue;}
                for(Method method:binding.getDeclaredMethods()) {
                    if(!method.getName().matches("initViews|updateSignalIcon|updateTint")||Modifier.isAbstract(method.getModifiers()))continue;
                    final boolean signalEvent=method.getName().equals("updateSignalIcon");
                    deoptimize(method);
                    installHook(HookGroup.MODEL,method,chain->{
                        Object owner=chain.getThisObject();
                        try(NativeNetworkBadgeBindings.NativeScope badge=textBinding.isInstance(owner)
                                        ?NATIVE_BADGE_BINDINGS.beforeNative(owner,false,null):null;
                                SingleMobileIconControls.NativeScope signal=SINGLE_SIGNAL.beforeNative(owner);
                                SingleNetworkLabelControls.NativeScope label=textBinding.isInstance(owner)
                                        ?SINGLE_LABEL.beforeNative(owner,false,null):null){
                            Object result=chain.proceed();
                            if(signalEvent)systemTextChanged(BigClockSystemIcon.nativeSingleChanged(owner,chain.getArg(0),activeDataSubscription()));
                            return result;
                        }
                    });
                }
            }
            for(String factoryName:new String[]{"com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
                    "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinder",
                    "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.LegacyOplusStatusBarMobileViewBinder"}) {
                Class<?> factory;
                try{factory=loader.loadClass(factoryName);}catch(ClassNotFoundException absent){continue;}
                for(Method method:factory.getDeclaredMethods())if(method.getName().equals("createBinding")&&!Modifier.isAbstract(method.getModifiers())) {
                    deoptimize(method);
                    // Hook installation is idempotent per executable: register both owners
                    // together, rather than losing one to a second hook on the same factory.
                    installHook(HookGroup.MODEL,method,chain->{
                        Object result=chain.proceed();
                        if(base.isInstance(result)) {
                            SINGLE_SIGNAL.bound(result);
                            SINGLE_SIGNAL.activeSubscription(activeDataSubscription());
                            if(chain.getArg(0) instanceof View)allowDrawableOverflow((View)chain.getArg(0));
                        }
                        if(textBinding.isInstance(result)){SINGLE_LABEL.bound(result);NATIVE_BADGE_BINDINGS.bound(result);scheduleRefresh();}
                        return result;
                    });
                }
            }
            installLegacySingleMobileIconHooks(loader);
            moduleLog(Log.INFO,TAG,"Native single-SIM signal placement connected");
        }catch(ReflectiveOperationException|LinkageError unavailable){
            moduleLog(Log.INFO,TAG,"Optional single-SIM signal placement unavailable",unavailable);
        }
    }

    private void installLegacySingleMobileIconHooks(ClassLoader loader) {
        // BigTypeLegacy is a separate native pipeline: unlike Os17/Os16 it
        // never creates Abstract...Binding. Its exact two-argument setter is
        // shared by the initial binding and the signal StateFlow collector.
        String factoryName="com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinderLegacy";
        try {
            Class<?> factory=loader.loadClass(factoryName);
            final Method subscription=loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModel").getMethod("getSubscriptionId");
            Class<?> signalModel=loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel");
            Method setter=factory.getDeclaredMethod("bindCustEx$updateSignalIcon",ImageView.class,signalModel);
            if(!Modifier.isStatic(setter.getModifiers())||setter.getReturnType()!=Void.TYPE)return;
            deoptimize(setter);
            installHook(HookGroup.MODEL,setter,chain->{
                Object icon=chain.getArg(0);
                if(icon instanceof ImageView)allowDrawableOverflow((ImageView)icon);
                try(SingleMobileIconControls.NativeScope signal=SINGLE_SIGNAL.beforeNative(icon)){return chain.proceed();}
            });
            // Deoptimize the real call sites as well as the setter, so an
            // already-compiled StateFlow collector cannot bypass the hook.
            for(Method method:factory.getDeclaredMethods())if(method.getName().equals("bindCustEx")&&method.getParameterCount()==5){
                deoptimize(method);
                installHook(HookGroup.MODEL,method,chain->{
                    Object result=chain.proceed();
                    if(chain.getArg(1) instanceof View){
                        View host=(View)chain.getArg(1);
                        int id=host.getResources().getIdentifier("mobile_signal","id","com.android.systemui");
                        View glyph=id==0?null:host.findViewById(id);
                        if(glyph instanceof ImageView)SINGLE_SIGNAL.bound((ImageView)glyph,
                                ((Number)subscription.invoke(chain.getArg(0))).intValue(),host);
                        SINGLE_SIGNAL.activeSubscription(activeDataSubscription());
                    }
                    return result;
                });
            }
            Class<?> collector=loader.loadClass(factoryName+"$bindCustEx$1$1$1$1$1");
            for(Method method:collector.getDeclaredMethods())if(method.getName().equals("emit"))deoptimize(method);
        }catch(ReflectiveOperationException|LinkageError unavailable){
            moduleLog(Log.INFO,TAG,"Optional legacy single-SIM signal placement unavailable",unavailable);
        }
    }

    static /* synthetic */ Object lambda$installNetworkModelHooks$6(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        NativeDataSource.capture(chain.getThisObject(),(Integer)chain.getArg(0));
        if (objProceed != null) {
            synchronized (MODELS) {
                MODELS.put((Integer) chain.getArg(0), new WeakReference<>(objProceed));
            }
        }
        scheduleRefresh();
        return objProceed;
    }

    static /* synthetic */ Object lambda$installNetworkModelHooks$7(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        scheduleRefresh();
        return objProceed;
    }

    static /* synthetic */ Object lambda$installNetworkModelHooks$8(XposedInterface.Chain chain) throws Throwable {
        return "";
    }

    private boolean installWifiSlot(ClassLoader classLoader) {
        try {
            Class<?> clsLoadClass = classLoader.loadClass(WIFI_VIEW);
            Class<?> clsLoadClass2 = classLoader.loadClass(MODERN_VIEW);
            getLastAppliedTint = clsLoadClass2.getMethod("getLastAppliedTint", new Class[0]);
            getVisibleState = clsLoadClass2.getMethod("getVisibleState", new Class[0]);
            getAppearAmount = clsLoadClass2.getMethod("getAppearAmount", new Class[0]);
            for (Executable executable : clsLoadClass.getDeclaredConstructors()) {
                installHook(HookGroup.WIFI, executable, new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda3
                    public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                        return StatusBarModule.lambda$installWifiSlot$10(chain);
                    }
                });
            }
            installHook(HookGroup.WIFI, clsLoadClass2.getDeclaredMethod("isIconVisible", new Class[0]), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda4
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installWifiSlot$11(chain);
                }
            });
            installHook(HookGroup.WIFI, View.class.getDeclaredMethod("setVisibility", Integer.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda5
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installWifiSlot$12(chain);
                }
            });
            installHook(HookGroup.WIFI, clsLoadClass2.getDeclaredMethod("setVisibleState", Integer.TYPE, Boolean.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda6
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installWifiSlot$13(chain);
                }
            });
            installHook(HookGroup.WIFI, clsLoadClass2.getDeclaredMethod("setAppearAmount", Float.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda7
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installWifiSlot$14(chain);
                }
            });
            for (Method method : clsLoadClass2.getDeclaredMethods()) {
                final boolean z = method.getName().equals("setStaticDrawableColor") && method.getParameterCount() == 2;
                if (method.getName().equals("onDarkChangedWithContrast") || z) {
                    installHook(HookGroup.WIFI, method, new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda9
                        public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                            return StatusBarModule.lambda$installWifiSlot$16(z, chain);
                        }
                    });
                }
            }
            installHook(HookGroup.WIFI, clsLoadClass.getDeclaredMethod("dispatchDraw", Canvas.class), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda10
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installWifiSlot$17(chain);
                }
            });
            moduleLog(Log.INFO, TAG, "Wi-Fi slot hooks installed with native visibility and tint");
            installNativeWifiSource(classLoader);
            return true;
        } catch (Throwable th) {
            moduleLog(Log.ERROR, TAG, "Could not bind the Wi-Fi slot", th);
            return false;
        }
    }

    static /* synthetic */ Object lambda$installWifiSlot$10(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        final View view = (View) chain.getThisObject();
        slotFor(view);
        MAIN.post(new Runnable() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                StatusBarModule.lambda$installWifiSlot$9(view);
            }
        });
        return objProceed;
    }

    static /* synthetic */ void lambda$installWifiSlot$9(View view) {
        allowDrawableOverflow(view);
        initializeContext(view.getContext());
        applyWifiVisibility(view, slotFor(view));
        scheduleRefresh();
    }

    private static void allowDrawableOverflow(View view) {
        if(view==null)return;
        String group;
        int kind=TEXT.kind(view);
        if(kind==TextControls.CLOCK)group=TEXT.group(view);
        else if(kind==TextControls.CARRIER)group=TEXT.group(view);
        else if(view.getClass().getName().endsWith("NetworkSpeedView"))group="speed";
        else {
            String slot="";
            try{slot=(String)view.getClass().getMethod("getSlot").invoke(view);}catch(Exception ignored){}
            group=isWifi(view)||(slot!=null&&slot.startsWith("wifi"))?"wifi":"cellular";
        }
        OVERFLOW_OWNERS.put(view,group);updateOverflow(view,group);
    }
    private static boolean needsOverflow(String group) {
        if(group.equals("cellular"))return NATIVE_BADGE.enabled()||needsOverflow("data")||needsOverflow("label");
        return FEATURES.position(group)||FEATURES.size(group)
                ||group.equals("speed")&&FEATURES.effective("speed","speed_lines_enabled")&&speedLineGapDp!=0;
    }
    private static void updateOverflow(View owner,String group) {
        if (!needsOverflow(group)) { OverflowControls.shared().release(owner); return; }
        if (group.equals("cellular") || group.equals("wifi") || group.equals("speed"))
            OverflowControls.shared().acquireLegacyNetwork(owner,true);
        else OverflowControls.shared().acquire(owner,false,true);
    }
    private static void releaseOverflow(View owner) {
        OVERFLOW_OWNERS.remove(owner);OverflowControls.shared().release(owner);
    }
    private static void refreshOverflow() {
        synchronized(OVERFLOW_OWNERS) {
            for(Map.Entry<View,String> entry:OVERFLOW_OWNERS.entrySet()) {
                if(TEXT.kind(entry.getKey())==TextControls.CARRIER||TEXT.kind(entry.getKey())==TextControls.CLOCK)entry.setValue(TEXT.group(entry.getKey()));
                updateOverflow(entry.getKey(),entry.getValue());
            }
        }
    }

    private static boolean isNetworkSlot(String slot) {
        return slot!=null&&(slot.equals("wifi")||slot.startsWith("wifi_")||slot.startsWith("mobile")
                ||slot.equals("stacked_mobile")||slot.startsWith("stacked_mobile_"));
    }

    static /* synthetic */ Object lambda$installWifiSlot$11(XposedInterface.Chain chain) throws Throwable {
        if (!isWifi(chain.getThisObject())) return chain.proceed();
        return WifiVisibilityPolicy.iconVisible((Boolean)chain.proceed(), FEATURES.hideWifiIcon(), cellularMode());
    }

    static /* synthetic */ Object lambda$installWifiSlot$12(XposedInterface.Chain chain) throws Throwable {
        if (!isWifi(chain.getThisObject())) {
            return chain.proceed();
        }
        Slot slotSlotFor = slotFor((View) chain.getThisObject());
        int requested = (Integer)chain.getArg(0);
        int target = slotSlotFor.visibility.requested(requested, FEATURES.hideWifiIcon(), cellularMode());
        return target == requested ? chain.proceed() : chain.proceed(new Object[]{target});
    }

    static /* synthetic */ Object lambda$installWifiSlot$13(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        if (isWifi(chain.getThisObject())) {
            View view = (View) chain.getThisObject();
            slotFor(view).visibleState = ((Integer) chain.getArg(0)).intValue();
            applyWifiVisibility(view, slotFor(view));
            view.invalidate();
        }
        return objProceed;
    }

    static /* synthetic */ Object lambda$installWifiSlot$14(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        if (isWifi(chain.getThisObject())) {
            View view = (View) chain.getThisObject();
            slotFor(view).appearAmount = ((Float) chain.getArg(0)).floatValue();
            applyWifiVisibility(view, slotFor(view));
        }
        return objProceed;
    }

    static /* synthetic */ Object lambda$installWifiSlot$16(boolean z, XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        if (!composeStatusBar && isWifi(chain.getThisObject())) {
            View view = (View) chain.getThisObject();
            slotFor(view).tint = ((Integer) (z ? chain.getArg(0) : getLastAppliedTint.invoke(view, new Object[0]))).intValue();
            view.invalidate();
        }
        return objProceed;
    }

    static /* synthetic */ Object lambda$installWifiSlot$17(XposedInterface.Chain chain) throws Throwable {
        // The legacy renderer borrows this host for cellular text. Hiding only
        // Wi-Fi must not remove that independently configured text output.
        if (FEATURES.hideWifiIcon() && !cellularMode()) return null;
        if (composeStatusBar) return chain.proceed();
        ViewGroup viewGroup = (ViewGroup) chain.getThisObject();
        Slot slotSlotFor = slotFor(viewGroup);
        if (cellularMode() && slotSlotFor.visibleState != 1) {
            if (slotSlotFor.visibleState == 0 && slotSlotFor.appearAmount > 0.0f && viewGroup.getWidth() > 0 && viewGroup.getHeight() > 0) {
                drawLabel(viewGroup, slotSlotFor, (Canvas) chain.getArg(0));
                return null;
            }
            return null;
        }
        return chain.proceed();
    }

    private static boolean isWifi(Object obj) {
        return obj != null && obj.getClass().getName().equals(WIFI_VIEW);
    }

    private void installNativeWifiSource(ClassLoader loader) throws Exception {
        Class<?> location;
        try {location=loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.LocationBasedWifiViewModel");}
        catch(ClassNotFoundException legacy){return;}
        nativeWifiFlow=location.getMethod("getWifiIcon");
        nativeWifiIconClass=loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon");
        nativeWifiVisibleClass=loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon$Visible");
        try {nativeWifiResource=nativeWifiVisibleClass.getMethod("getRes");}
        catch(NoSuchMethodException otherNativeModel){nativeWifiResource=null;}
        Class<?> binder=loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder");
        Method bind=binder.getDeclaredMethod("bind",ViewGroup.class,location);deoptimize(bind);
        installHook(HookGroup.WIFI,bind,chain->{
            View view=(View)chain.getArg(0);NATIVE_WIFI.put(view,new WeakReference<>(chain.getArg(1)));
            initializeContext(view.getContext());Object result=chain.proceed();refreshRadio();return result;
        });
        try {
            Class<?> collector=loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder$bind$1$1$1$1");
            for(Method method:collector.getDeclaredMethods())if(method.getName().equals("emit")&&method.getParameterCount()==2) {
                deoptimize(method);
                installHook(HookGroup.WIFI,method,chain->{
                    Object result=chain.proceed();
                    if(nativeWifiIconClass.isInstance(chain.getArg(0))){
                        captureSystemTextWifi(chain.getArg(0),context==null?null:context.getResources());refreshRadio();
                    }return result;
                });
            }
            for(Method method:loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder$bind$1$1$2").getDeclaredMethods())
                if(method.getName().equals("invokeSuspend"))deoptimize(method);
        }catch(ClassNotFoundException otherVersion){moduleLog(Log.INFO,TAG,"Wi-Fi flow collector differs; connectivity callbacks remain active");}
    }

    /** Exact native model/resource evidence only. Default connectivity cannot
     * tell the component which Wi-Fi signal glyph the system selected. */
    private static void captureSystemTextWifi(Object icon,Resources resources) {
        if(ModuleLifecycle.removed()||nativeWifiIconClass==null||!nativeWifiIconClass.isInstance(icon))return;
        boolean visible=nativeWifiVisibleClass!=null&&nativeWifiVisibleClass.isInstance(icon);
        Integer id=null;String name=null;
        if(visible&&nativeWifiResource!=null&&resources!=null)try {
            id=((Number)nativeWifiResource.invoke(icon)).intValue();
            if(id>0&&"com.android.systemui".equals(resources.getResourcePackageName(id)))name=resources.getResourceEntryName(id);
        }catch(ReflectiveOperationException|RuntimeException unavailable){id=null;name=null;}
        systemTextChanged(BigClockSystemText.wifiIcon(id,name,visible));
    }
    private static Boolean nativeWifiVisible() {
        if(nativeWifiFlow==null||nativeWifiVisibleClass==null||getFlowValue==null)return null;
        synchronized(NATIVE_WIFI) {
            for(Map.Entry<View,WeakReference<Object>> source:NATIVE_WIFI.entrySet()) {
                Object model=source.getValue().get();
                if(model==null||!source.getKey().isAttachedToWindow())continue;
                try {
                    Object flow=nativeWifiFlow.invoke(model);
                    Object icon=flow==null?null:getFlowValue.invoke(flow);
                    if(icon!=null&&nativeWifiIconClass.isInstance(icon)){
                        captureSystemTextWifi(icon,source.getKey().getResources());return nativeWifiVisibleClass.isInstance(icon);
                    }
                }catch(Exception unreadable){}
            }
        }
        return null;
    }

    private static void refreshRadio() {
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(StatusBarModule::refreshRadio);return;}
        boolean previouslyAllowed=RadioState.cellularLabelAllowed(true,wifiConnected,airplaneMode,mobileDataSwitchEnabled);
        updateRadioState();
        if(previouslyAllowed!=RadioState.cellularLabelAllowed(true,wifiConnected,airplaneMode,mobileDataSwitchEnabled)) {
            // A transport/switch boundary must retire or restore both native
            // text and the legacy slot before returning to the frame. Ordinary
            // RAT events still share the queued refresh below.
            synchronized(REFRESH_LOCK){refreshPending=false;}
            refreshLabel();
        } else scheduleRefresh();
    }

    private static Slot slotFor(View view) {
        Slot slot;
        synchronized (SLOTS) {
            slot = SLOTS.get(view);
            if (slot == null) {
                slot = new Slot(view);
                try {
                    slot.visibleState = ((Integer) getVisibleState.invoke(view, new Object[0])).intValue();
                    slot.appearAmount = ((Float) getAppearAmount.invoke(view, new Object[0])).floatValue();
                    slot.tint = ((Integer) getLastAppliedTint.invoke(view, new Object[0])).intValue();
                } catch (Throwable th) {
                }
                SLOTS.put(view, slot);
            }
        }
        return slot;
    }

    private static void applyWifiVisibility(View view, Slot slot) {
        int target = slot.visibility.target(FEATURES.hideWifiIcon(), cellularMode());
        if (view.getVisibility() == target) return;
        slot.visibility.beginModuleWrite();
        try { view.setVisibility(target); }
        finally { slot.visibility.endModuleWrite(); }
    }

    private static boolean cellularMode() {
        return !composeStatusBar && !FEATURES.hideNetworkLabel() && !networkLabel.isEmpty()
                && RadioState.cellularLabelAllowed(FEATURES.enabled("label"), wifiConnected, airplaneMode, mobileDataSwitchEnabled);
    }

    private static void initializeContext(Context context2) {
        if (context == null) {
            Context applicationContext = context2.getApplicationContext();
            if (applicationContext != null) {
                context2 = applicationContext;
            }
            context = context2;
            weatherContext = context2;
        }
        // The built-in cache starts before the first font/icon read, including before settings arrive.
        if (!ModuleLifecycle.removed()) {
            FontRepository.resident=RESIDENT_CACHE; ResidentCache.current=RESIDENT_CACHE;
            ModuleRuntimeStatus.residentReport=RESIDENT_CACHE::report; ModuleRuntimeStatus.hookReport=StatusBarModule::hookHealth;
            try { RESIDENT_CACHE.initialize(context); }
            catch(RuntimeException unavailable) { Log.w(TAG,"Resident cache unavailable",unavailable); }
        }
        ModuleLifecycle.bind(context, () -> MAIN.post(() -> {
            CUSTOM_PLUGINS.stop();
            if(settingsStartup!=null)settingsStartup.stop();
            if(frameworkSettings!=null)frameworkSettings.stop();
            MAIN.removeCallbacks(FRAMEWORK_SETTINGS_CHANGED);
            MAIN.removeCallbacks(PANEL_MODE_CHANGED);
            if (panelModeObserver != null) try { context.getContentResolver().unregisterContentObserver(panelModeObserver); }
            catch (RuntimeException ignored) { }
            panelModeObserver = null;
            applyStyleSettings(ModuleLifecycle.nativeSettings());
            NOTIFICATION_ROW_ICONS.releaseRuntime();NOTIFICATION_ICONS.releaseRuntime();
            ICON_NATIVE_QS.releaseRuntime();ICON_NATIVE_CONTROLS.releaseRuntime();
            IconManagementRuntime.releaseRuntime();ICON_PACK_DRAWING.releaseRuntime();BigClockSystemIcon.releaseRuntime();
            FontRepository.releaseRuntime();
            QS_MEDIA.releaseRuntime();
            WIFI_ALIAS.release();
            QS_FULL_FILL.release();
            SHADE_WALLPAPER.reset();
            WALLPAPER_PANEL.reset();
            C17_HIGHLIGHTS.release();
            QS_ICON_SIZE.releaseRuntime();
            DATA_BATTERY_SPACING.releaseRuntime();
            MAIN.removeCallbacks(SYSTEM_TEXT_CHANGED);
            synchronized(REFRESH_LOCK){systemTextRefreshPending=false;}
            BigClockSystemText.clear();
            ClockBackdropSource.clear();
            NETWORK_ORDER.releaseRuntime();
            STATUS_FLOW.releaseRuntime();
            NETWORK_SHARED_SLOT.releaseRuntime();
            SPEED_POSITION.releaseRuntime();
            STATUS_HINTS.releaseRuntime();
            NATIVE_STACK.configure(null); NATIVE_STACK.reset();
            MINI_STACK.configure(null);
            NATIVE_BADGE_BINDINGS.releaseRuntime();
            NATIVE_BADGE.releaseRuntime();
            DATA_ACTIVITY.releaseRuntime();
            WEATHER_SOURCE.close();
            SystemWeatherText.clear();
            SINGLE_LABEL.releaseRuntime();
            SINGLE_SIGNAL.releaseRuntime();
            LOCKSCREEN.releaseRuntime();
            LOCKSCREEN_BLUR.releaseRuntime();
            LOCK_CAPSULE.release();
            FLUID_CAPSULE.release();
            CAPSULE_PERSISTENCE.release();
            CAPSULE_MEDIA_PERSISTENCE.release();
            CAPSULE_EFFECTS.release();
            RESIDENT_CACHE.release(); FontRepository.resident = null; ResidentCache.current = null; ModuleRuntimeStatus.residentReport = null; ModuleRuntimeStatus.hookReport = null;
            CAPSULE_SPECTRUM.release();
            FLUID_ACCENT.release();
            AOD_NOTIFICATION_BACKGROUND.release();
            FLUID_STYLE.release();
            QsTileAppearance.releaseNativeMembers();
            moduleResources = null;
            ICONS.clear();
            invalidateLiveDrawables();
            refreshLabel();
        }));
        if (!ModuleVariant.DABING && instance != null && !ModuleLifecycle.removed()) CUSTOM_PLUGINS.configure(context,
                (method,hooker) -> {
                    instance.deoptimize(method);
                    return instance.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
                },() -> {
                    Bundle base = SettingsSnapshot.lastApplied(context);
                    if (base != null) applyStyleSnapshot(base,true);
                },new CustomPluginRuntime.Install() {
                    @Override public XposedInterface.HookHandle hook(java.lang.reflect.Executable executable,XposedInterface.Hooker hooker) throws Throwable {
                        instance.deoptimize(executable);
                        // Runtime owns a single-proceed chain and explicit failure handling.
                        return instance.hook(executable).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(hooker);
                    }
                    @Override public void deoptimize(java.lang.reflect.Executable executable) { instance.deoptimize(executable); }
                    @Override public android.content.SharedPreferences frameworkPreferences() {
                        return instance.getRemotePreferences(SettingsFrameworkMirror.GROUP);
                    }
                    @Override public android.os.ParcelFileDescriptor openFrameworkFile(String name) throws java.io.FileNotFoundException {
                        return instance.openRemoteFile(name);
                    }
                });
        if (frameworkLogger != null) try {
            ModuleRuntimeStatus.setVerifiedProbeListener(nonce -> MAIN.post(() -> {
                if (ModuleLifecycle.removed() || nonce.equals(lastIconTraceProbe)) return;
                lastIconTraceProbe = nonce;
                BIG_CLOCK.resetStatusIconTrace();
                // Explicit verified own-app probe only: fixed structural counters, no user text.
                Log.i("C17-Stack", NATIVE_STACK.diagnosticSummary() + "; " + MINI_STACK.diagnosticSummary() + "; "
                        + BIG_CLOCK.groupStackDiagnostics() + "; " + LOCKSCREEN.diagnosticSummary() + "; " + DATA_ACTIVITY.diagnosticSummary());
                Log.i("C17-Badge",NATIVE_BADGE_BINDINGS.diagnosticSummary()+"; "+SINGLE_SIGNAL.diagnosticSummary());
                Log.i("C17-Font", TEXT.summary());
                Log.i("C17-LockscreenBlur", LOCKSCREEN_BLUR.summary());
                Log.i("C17-Fluid", FLUID_STYLE.diagnosticSummary());
                WEATHER_SOURCE.refresh();
                weatherDiagnosticView(weatherDiagnosticView.get(),"probe");
                weatherDiagnostic("probe",null,true);
                String componentLayout=BIG_CLOCK.componentLayoutDiagnostics();
                ModuleDiagnostics.fact("clock","clock","日期与组件排列状态",
                        "reason",componentLayout,"applied","swapped".equals(componentLayout));
                reportBackdropFact("backdrop_source",ClockBackdropSource.diagnostics());
                reportBackdropFact("backdrop_material",ClockBackdropMaterial.diagnostics());
                reportBackdropFact("system_icons",BigClockSystemIcon.diagnostics());
            }));
            ModuleRuntimeStatus.registerSystemUiReceiver(context, frameworkLogger.getFrameworkName(),
                    frameworkLogger.getFrameworkVersion(), frameworkLogger.getApiVersion());
        } catch (RuntimeException unavailable) {
            logFrameworkStage(Log.WARN, "Module activation reporter unavailable", unavailable);
        }
        if (moduleResources == null) {
            try {
                moduleResources = context.createPackageContext(MODULE_PACKAGE, 2).getResources();
            } catch (Throwable th) {
                logFrameworkStage(Log.WARN, "Could not access bundled iOS vectors", th);
            }
        }
        registerPanelModeObserver();
        ensureSettingsStartup();
        registerMobileDataObserver();
        if (receiverRegistered) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED");
        intentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        intentFilter.addAction("android.intent.action.SERVICE_STATE");
        intentFilter.addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        intentFilter.addAction("android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED");
        intentFilter.addAction("android.telephony.action.CARRIER_CONFIG_CHANGED");
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        intentFilter.addAction("android.intent.action.USER_SWITCHED");
        intentFilter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        for (String action : new String[]{Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF})
            intentFilter.addAction(action);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { // from class: dev.puitheme.StatusBarModule.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context3, Intent intent) {
                if (intent != null) {
                    String action = intent.getAction();
                    if ("android.intent.action.USER_SWITCHED".equals(action)) {
                        switchWeatherUser(intent.getIntExtra("android.intent.extra.user_handle", -1));
                        return;
                    }
                    if(Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                        captureSystemTextBattery(intent);return;
                    }
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        ModuleRuntimeStatus.signalPerformance(context3, false);
                        ICON_NATIVE_QS.setInteractive(false);
                        TEXT.setInteractive(false); if (BATTERY != null) BATTERY.setInteractive(false); return;
                    }
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        WEATHER_SOURCE.refresh();
                        ModuleRuntimeStatus.signalPerformance(context3, true);
                        ICON_NATIVE_QS.setInteractive(true);
                        TEXT.setInteractive(true); if (BATTERY != null) BATTERY.setInteractive(true); return;
                    }
                    if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                            || Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                        WEATHER_SOURCE.refresh();
                        BIG_CLOCK.onTimeChanged();
                        TEXT.refresh(); return;
                    }
                }
                if (intent != null && (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())
                        || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
                        || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))) {
                    WEATHER_SOURCE.refresh();
                    if (!ModuleVariant.DABING) CUSTOM_PLUGINS.bootReady();
                    if(settingsStartup!=null)settingsStartup.signal(false);
                }
                StatusBarModule.refreshRadio();
            }
        };
        boolean registered = false;
        try {
            Object sticky=Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, Integer.TYPE).invoke(context, broadcastReceiver, intentFilter, 2);
            if(sticky instanceof Intent)captureSystemTextBattery((Intent)sticky);
            registered = true;
        } catch (Throwable th2) {
            try {
                Intent sticky=context.registerReceiver(broadcastReceiver, intentFilter);
                if(sticky!=null)captureSystemTextBattery(sticky);
                registered = true;
            } catch (Throwable th3) {
                logFrameworkStage(Log.WARN, "Could not register radio state receiver", th3);
            }
        }
        receiverRegistered = registered;
        registerNetworkObserver();
        refreshRadio();
    }

    /** A user switch must never reuse another user's located-city weather cache. */
    private static void switchWeatherUser(int userId) {
        SystemWeatherText.disableUnverifiedFallback();
        WEATHER_SOURCE.close();
        SystemWeatherText.clear();
        weatherContext = null;
        if (userId >= 0 && context != null) try {
            Class<?> userClass = Class.forName("android.os.UserHandle");
            Object user = userClass.getMethod("of", Integer.TYPE).invoke(null, userId);
            weatherContext = (Context) Context.class.getMethod("createContextAsUser", userClass, Integer.TYPE).invoke(context, user, 0);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            ModuleDiagnostics.error("weather", "Current user weather context unavailable", unavailable);
        }
        WEATHER_SOURCE.configure(weatherContext, weatherNeeded, BIG_CLOCK::onSystemWeatherChanged);
        BIG_CLOCK.onSystemWeatherChanged();
    }

    private static void registerMobileDataObserver() {
        if (mobileDataObserverRegistered || context == null) return;
        try {
            // Multi-SIM stores mobile_data or mobile_data<subscription>; read the actual active
            // TelephonyManager rather than interpreting the changed setting as the current SIM.
            context.getContentResolver().registerContentObserver(android.provider.Settings.Global.CONTENT_URI,true,new ContentObserver(MAIN) {
                @Override public void onChange(boolean selfChange,Uri uri) {
                    String key=uri==null?null:uri.getLastPathSegment();
                    if (key==null || "global".equals(key) || key.startsWith("mobile_data")) refreshRadio();
                }
            });
            mobileDataObserverRegistered=true;
        } catch (RuntimeException unavailable) {
            logFrameworkStage(Log.WARN,"Mobile data setting observer unavailable; radio callbacks retained",unavailable);
        }
    }

    private static void registerSettingsObserver() {
        if (context == null || settingsObserverRegistered || ModuleLifecycle.removed()) {
            return;
        }
        Uri uri = Uri.parse(StatusBarSettings.CONTENT_URI);
        try {
            context.getContentResolver().registerContentObserver(uri, false, new ContentObserver(MAIN) { // from class: dev.puitheme.StatusBarModule.2
                @Override // android.database.ContentObserver
                public void onChange(boolean z, Uri uri2) {
                    CUSTOM_PLUGINS.refresh();
                    // Real edits/20-second trials may precede their confirmed framework mirror.
                    preferSettingsProvider=true;
                    if(settingsStartup!=null)settingsStartup.signal(false);
                }
            });
            settingsObserverRegistered = true;
            logFrameworkStage(Log.INFO, "Live visual settings observer registered");
        } catch (Throwable th) {
            logFrameworkStage(Log.WARN, "Could not observe visual settings", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void readStyleSettings() {
        if(context==null||ModuleLifecycle.removed())return;
        // A newly installed native consumer needs the successful process snapshot,
        // even if the provider is temporarily unavailable or returns the same values.
        Runnable rebind=()->{
            if(ModuleLifecycle.removed())return;
            Bundle cached=SettingsSnapshot.lastApplied(context);
            boolean rebound=cached!=null&&applyStyleSnapshot(cached,true);
            ensureSettingsStartup();
            if(settingsStartup!=null)settingsStartup.signal(!rebound);
        };
        if(Looper.myLooper()==MAIN.getLooper())rebind.run();else MAIN.post(rebind);
    }

    private static synchronized void ensureSettingsStartup() {
        if(context==null||ModuleLifecycle.removed())return;
        if(frameworkSettings==null&&frameworkLogger!=null)frameworkSettings=new SettingsFrameworkReader(
                ()->frameworkLogger.getRemotePreferences(SettingsFrameworkMirror.GROUP),()->{
                    MAIN.removeCallbacks(FRAMEWORK_SETTINGS_CHANGED);
                    MAIN.post(FRAMEWORK_SETTINGS_CHANGED);
                },ModuleLifecycle::removed);
        if(settingsStartup==null)settingsStartup=new SettingsStartupLoader(MAIN,
                StatusBarModule::readStartupSettings,StatusBarModule::applyStyleSnapshot,ModuleLifecycle::removed,()->{
                    registerSettingsObserver();return settingsObserved();
                },StatusBarModule::startupSettingsReady);
        registerSettingsObserver();
        settingsStartup.start();
    }

    private static boolean settingsObserved() {
        return settingsObserverRegistered||frameworkSettings!=null&&frameworkSettings.observed();
    }

    private static Bundle readStartupSettings() {
        // ColorOS can reject a provider's cross-app auto-start, including after unlock.
        // A boot snapshot comes directly from the already-running framework daemon.
        return frameworkSettings==null?SettingsSnapshot.readFresh(context)
                :frameworkSettings.readStartup(preferSettingsProvider,()->SettingsSnapshot.readFresh(context));
    }
    /** A framework fallback can render immediately, but a bounded recovery
     * window still confirms the authoritative DE provider after boot/unlock. */
    private static boolean startupSettingsReady() {
        return moduleResources != null && settingsObserved()
                && (frameworkSettings == null || frameworkSettings.providerConfirmed());
    }

    private static boolean applyStyleSnapshot(Bundle bundleCall,boolean force) {
        if(context==null||ModuleLifecycle.removed()||!SettingsSnapshot.complete(bundleCall))return false;
        if (ModuleVariant.DABING) bundleCall = DabingCustomerPolicy.runtime(bundleCall);
        else CUSTOM_PLUGINS.setSafeMode(bundleCall.getBoolean(StatusBarSettings.SAFE_MODE,false));
        Bundle previous=SettingsSnapshot.lastApplied(context);
        try {
            boolean resourcesRecovered=false;
            if(moduleResources==null)try {
                moduleResources=context.createPackageContext(MODULE_PACKAGE,2).getResources();
                resourcesRecovered=moduleResources!=null;
            } catch(Exception unavailable){/* The existing bounded startup window retries package readiness. */}
            if(!force&&SettingsSnapshot.matchesApplied(context,bundleCall)) {
                installLazyHooks();
                if(resourcesRecovered){invalidateLiveDrawables();refreshLabel();}
                return true;
            }
            applyStyleSettings(ModuleVariant.DABING ? bundleCall : CUSTOM_PLUGINS.runtimeSettings(CustomPluginSettings.overlay(bundleCall,CUSTOM_PLUGINS.settings())));
            // Remember only the user's base snapshot; plugin removal must not leave an overlaid value behind.
            SettingsSnapshot.rememberApplied(context,bundleCall);
            invalidateLiveDrawables();
            refreshLabel();
            return true;
        } catch (Throwable failed) {
            boolean restored=false;
            if(previous!=null)try { applyStyleSettings(ModuleVariant.DABING ? previous : CUSTOM_PLUGINS.runtimeSettings(CustomPluginSettings.overlay(previous,CUSTOM_PLUGINS.settings())));restored=true; }
            catch(Throwable rollbackFailed) { ModuleDiagnostics.error("settings","Could not restore previous runtime configuration",rollbackFailed); }
            ModuleDiagnostics.error("settings",restored?"Settings application failed; previous configuration restored"
                    :"Settings application incomplete; no successful configuration could be restored",failed);
            Log.w(TAG,"Could not apply visual settings",failed);
            return false;
        }
    }

    private static void applyStyleSettings(Bundle bundleCall) {
            if (ModuleVariant.DABING) bundleCall = DabingCustomerPolicy.runtime(bundleCall);
            bundleCall = PanelMode.runtimeSettings(SafetyMode.runtimeSettings(bundleCall));
            bundleCall = NetworkDisplaySettings.runtime(bundleCall);
            bundleCall = new Bundle(bundleCall);
            bundleCall.putBoolean(StatusBarShadeIconSettings.MASTER, false);
            ModuleDiagnostics.configure(context, bundleCall);
            TEXT.observeLayoutAnchors(STATUS_FLOW::observedAnchor);
            NETWORK_SHARED_SLOT.configure(StatusBarLayoutSettings.Snapshot.parse(bundleCall).wifiLabelShared
                    && NetworkDisplaySettings.TEXT.equals(NetworkDisplaySettings.mode(bundleCall)));
            STATUS_FLOW.configure(bundleCall);
            SINGLE_LABEL.configureLayout(bundleCall);
            // Preserve the stored snapshot. The new flow owns outer X/allocation;
            // legacy painters retain Y and all independent inner adjustments.
            bundleCall = StatusBarLayoutSettings.runtime(bundleCall);
            // The resident cache first: the icon libraries and fonts configured below already read through it on this start.
            if (FontRepository.resident != RESIDENT_CACHE) {
                FontRepository.resident = RESIDENT_CACHE; ResidentCache.current = RESIDENT_CACHE; ModuleRuntimeStatus.residentReport = RESIDENT_CACHE::report; ModuleRuntimeStatus.hookReport = StatusBarModule::hookHealth;
            }
            try { RESIDENT_CACHE.configure(context, bundleCall); }
            catch (RuntimeException unavailable) { Log.w(TAG, "Resident cache unavailable", unavailable); }
            LOCKSCREEN_BLUR.configure(bundleCall);
            FLUID_ACCENT.configure(bundleCall);
            // The camera-area capsule and its spectrum are optional extras of the plugin: whatever they do on a real device, a failure
            // there must never stop the rest of this list (the fluid-cloud card colours, the big clock, the status bar) from being configured.
            final Bundle applied = bundleCall;
            optionalConfigure("camera-area capsule", () -> FLUID_CAPSULE.configure(applied));
            optionalConfigure("camera-area capsule effects", () -> CAPSULE_EFFECTS.configure(applied));
            optionalConfigure("capsule persistence", () -> CAPSULE_PERSISTENCE.configure(applied));
            optionalConfigure("capsule media persistence", () -> CAPSULE_MEDIA_PERSISTENCE.configure(applied));
            optionalConfigure("capsule spectrum", () -> CAPSULE_SPECTRUM.configure(applied));
            optionalConfigure("privacy indicator", () -> {
                PrivacyIndicatorSettings privacy = PrivacyIndicatorSettings.from(applied);
                privacySettings = privacy; PRIVACY_DOT.configure(privacy.hideDot); PRIVACY_CAPSULE.configure(privacy);
            });
            SHADE_WALLPAPER.configure(context, bundleCall);
            NOTIFICATION_ICONS.configure(context,bundleCall);
            NOTIFICATION_ROW_ICONS.configure(context,bundleCall);
            IconManagementRuntime.setInvalidationListener(() -> {
                ICON_PACK_DRAWING.invalidateAll();invalidateLiveDrawables();
                ICON_NATIVE_CONTROLS.refresh();ICON_NATIVE_QS.refresh();
                systemTextChanged(BigClockSystemIcon.assetsChanged());
            });
            systemTextChanged(BigClockSystemIcon.configure(context,bundleCall));
            IconManagementRuntime.configure(context,bundleCall);
            ICON_NATIVE_CONTROLS.configure(IconManagementRuntime.active());
            ICON_NATIVE_QS.configure(IconManagementRuntime.active());
            FEATURES=FeatureOptions.from(bundleCall);
            FontRepository.configure(context, FEATURES.enabled("font")?bundleCall.getString(StatusBarSettings.FONT_MODE, "system"):"system",
                    bundleCall.getString(StatusBarSettings.FONT_REVISION, ""));
            FontRepository.configureScopes(bundleCall);
            LABEL_FORMATS.configure(bundleCall);
            WIFI_ALIAS.configure(bundleCall);
            String currentWifiName=QsWifiAliasCatalog.connected(context);
            WIFI_ALIAS.seedNetwork(currentWifiName);QsWifiAliasCatalog.report(context,currentWifiName);
            LEFT_COPY_SPACING.configure(!FEATURES.safeMode()
                    && !StatusBarLayoutSettings.enabled(bundleCall));
            DATA_BATTERY_SPACING.configure(bundleCall);
            TILE_EFFECTS.configure(bundleCall);
            QS_APPEARANCE.configure(bundleCall);
            QS_FULL_FILL.configure(bundleCall);
            QS_CORNERS.configure(bundleCall);
            QS_ICON_SIZE.configure(bundleCall);
            QS_PANEL_CORNERS.configure(bundleCall);
            QS_MEDIA.configure(bundleCall);
            C17_HIGHLIGHTS.configure(bundleCall);
            FLUID_STYLE.configure(bundleCall);
            AOD_NOTIFICATION_BACKGROUND.configure(bundleCall);
            MEDIA_PLAYBACK.configure(context, FLUID_STYLE.wantsPlaybackState());
            FLUID_STYLE.refreshAfterAccent();
            NETWORK_SPEED.configure(bundleCall);
            SPEED_POSITION.configure(bundleCall);
            CLEAR_APPEARANCE.configure(bundleCall);
            CLEAR_MOTION.configure(bundleCall);
            BIG_CLOCK.configure(bundleCall);
            weatherNeeded = SystemWeatherProvider.needed(bundleCall);
            if (ModuleVariant.DABING) NATIVE_STACK.configureIndependent(bundleCall);
            else NATIVE_STACK.configure(bundleCall);
            optionalConfigure("mini stack", () -> MINI_STACK.configure(applied));
            bigClockEnabled = bundleCall.getBoolean(NotificationBigClockSettings.MASTER, false)
                    || bundleCall.getBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER, false)
                    || bundleCall.getBoolean(NotificationBigClockSettings.STACK_ENABLED, false)
                    || bundleCall.getBoolean(NotificationGroupStack.MASTER, false);
            NativeBackdropMotion backdrop = notificationBackdropMotion;
            if (!bigClockEnabled && backdrop != null) MAIN.post(() -> { if (!bigClockEnabled) backdrop.release(); });
            BADGES.configure(FEATURES);
            wifiOffsetXdp = FEATURES.position("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_X, 0):0;
            wifiOffsetYdp = FEATURES.position("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_Y, 0):0;
            wifiIconScalePercent = FEATURES.size("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_ICON_SCALE, 100):100;
            dataOffsetXdp = FEATURES.position("data")?settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_X, 0):0;
            dataOffsetYdp = FEATURES.position("data")?settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_Y, 0):0;
            dataIconScalePercent = FEATURES.size("data")?settingValue(bundleCall, StatusBarSettings.DATA_ICON_SCALE, 100):100;
            SPEED_POSITION.configureNetwork(wifiOffsetXdp,wifiIconScalePercent,dataOffsetXdp,dataIconScalePercent);
            singleSignal = FEATURES.singleSignal(bundleCall.getString(StatusBarSettings.SIGNAL_LAYOUT, "system"));
            SINGLE_SIGNAL.update(FEATURES,dataOffsetXdp,dataOffsetYdp,dataIconScalePercent,singleSignal,activeDataSubscription());
            labelOffsetXdp = FEATURES.position("label")?settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_X, 0):0;
            labelOffsetYdp = FEATURES.position("label")?settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_Y, 0):0;
            labelScalePercent = FEATURES.size("label")?settingValue(bundleCall, StatusBarSettings.LABEL_SCALE, 100):100;
            fontWeight = Math.round(settingValue(bundleCall, StatusBarSettings.FONT_WEIGHT, 600));
            speedOffsetXdp = FEATURES.position("speed") && !bundleCall.getBoolean(SpeedPosition.SAFE_GAP_ENABLED, false)
                    ? settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_X, 0) : 0;
            speedOffsetYdp = FEATURES.position("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_Y, 0):0;
            speedScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_SCALE, 100):100;
            speedNumberScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_NUMBER_SCALE, 100):100;
            speedUnitScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_UNIT_SCALE, 100):100;
            speedLineGapDp = FEATURES.effective("speed","speed_lines_enabled")?settingValue(bundleCall, StatusBarSettings.SPEED_LINE_GAP, 0):0;
            speedWeight = Math.round(settingValue(bundleCall, StatusBarSettings.SPEED_WEIGHT, 600));
            Map<String, Integer> colors = new HashMap<>();
            Map<String, Boolean> alphaModes = new HashMap<>();
            for (Map.Entry<String, Integer> color : StatusBarSettings.COLOR_DEFAULTS.entrySet()) {
                colors.put(color.getKey(), bundleCall.getInt(color.getKey(), color.getValue()));
                alphaModes.put(color.getKey(), bundleCall.getBoolean(StatusBarSettings.alphaKey(color.getKey()), false));
            }
            styleColors = colors;
            customColorAlpha = alphaModes;
            TEXT.configure(bundleCall, colors, alphaModes);
            LOCKSCREEN.configure(bundleCall);
            if (BATTERY != null) BATTERY.configure(bundleCall,colors,alphaModes);
            NETWORK_ORDER.configure(bundleCall);
            STATUS_HINTS.configure(bundleCall);
            NATIVE_BADGE.configure(bundleCall);
            NATIVE_BADGE_BINDINGS.configure(FEATURES,activeDataSubscription());
            DATA_ACTIVITY.configure(bundleCall);
            // Every feature has its new state by now: install the framework-wide draw hooks only the on features need.
            installLazyHooks();
            ModuleDiagnostics.runtimeApplied(context,bundleCall);
            MAIN.post(()->{
                WEATHER_SOURCE.configure(weatherContext, weatherNeeded, BIG_CLOCK::onSystemWeatherChanged);
                if(ModuleDiagnostics.enabled()){
                    weatherDiagnosticView(weatherDiagnosticView.get(),"configure");
                    weatherDiagnostic("configure",null,true);
                }
            });
            if(ModuleDiagnostics.enabled()) {
                ModuleDiagnostics.info("single_signal",SINGLE_SIGNAL.diagnosticSummary());
                ModuleDiagnostics.info("single_label",SINGLE_LABEL.diagnosticSummary());
                ModuleDiagnostics.info("native_badge",NATIVE_BADGE_BINDINGS.diagnosticSummary());
                ModuleDiagnostics.info("font",TEXT.summary());
                ModuleDiagnostics.info("status_hints", "Status flow " + STATUS_FLOW.summary());
                ModuleDiagnostics.info("lockscreen_blur",LOCKSCREEN_BLUR.summary());
                ModuleDiagnostics.info("mini_stack",MINI_STACK.diagnosticSummary());
            }
            ModuleDiagnostics.info("settings", "Visual settings applied; Android SDK " + android.os.Build.VERSION.SDK_INT
                    + "; active hooks " + COMPLETED_HOOK_GROUPS);
    }

    private static final java.util.Set<String> OPTIONAL_CONFIGURE_FAILED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** Runs one optional feature's configuration; a failure is recorded (once per feature) and does not reach the caller. */
    private static void optionalConfigure(String name, Runnable configure) {
        try { configure.run(); }
        catch (RuntimeException | LinkageError failed) {
            if (OPTIONAL_CONFIGURE_FAILED.add(name)) {
                ModuleDiagnostics.error("settings", "Optional " + name + " configuration failed; the other settings were still applied", failed);
                Log.w(TAG, "Optional " + name + " configuration failed", failed);
            }
        }
    }

    private static int clamp(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }

    private static float settingValue(Bundle settings, String key, float fallback) {
        return NumericPolicy.setting(key,settings.get(key),fallback);
    }

    private static int styleColor(String item, int nativeTint) {
        return FEATURES.color(item)?IconAppearance.color(item, nativeTint, styleColors, customColorAlpha):nativeTint;
    }

    private static int nativeWeight(Typeface face) {
        if(face==null)return 400;
        return android.os.Build.VERSION.SDK_INT>=28?face.getWeight():(face.isBold()?700:400);
    }

    private static int nativeFilterColor(ColorFilter filter, int fallback) {
        if (filter == null) return fallback;
        if (android.os.Build.VERSION.SDK_INT >= 29 && filter instanceof BlendModeColorFilter) {
            return ((BlendModeColorFilter) filter).getColor();
        }
        try {
            return (Integer) filter.getClass().getMethod("getColor").invoke(filter);
        } catch (Throwable ignored) { return fallback; }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void invalidateLiveDrawables() {
        TEXT.refresh();
        BADGES.refresh();
        refreshOverflow();
        invalidateComposeStyle();
        synchronized (SPEED_VIEWS) {
            for (Map.Entry<View, SpeedStyle> entry : SPEED_VIEWS.entrySet()) {
                applySpeedStyle(entry.getKey(), entry.getValue());
                entry.getKey().invalidate();
            }
        }
        synchronized (LIVE_DRAWABLES) {
            for (int size = LIVE_DRAWABLES.size() - 1; size >= 0; size--) {
                ScaledDrawable scaledDrawable = LIVE_DRAWABLES.get(size).get();
                if (scaledDrawable == null) {
                    LIVE_DRAWABLES.remove(size);
                } else {
                    scaledDrawable.reloadSignal();
                    scaledDrawable.invalidateSelf();

                }
            }
        }
    }

    /** Existing sources can report together; one main-thread pass refreshes
     * only the optional component row, never the status-bar model or clock. */
    private static void systemTextChanged(boolean changed) {
        if(!changed||ModuleLifecycle.removed())return;
        synchronized(REFRESH_LOCK) {
            if(systemTextRefreshPending)return;
            systemTextRefreshPending=true;
        }
        if(!MAIN.post(SYSTEM_TEXT_CHANGED))synchronized(REFRESH_LOCK){systemTextRefreshPending=false;}
    }
    private static void refreshSystemText() {
        synchronized(REFRESH_LOCK){systemTextRefreshPending=false;}
        if(!ModuleLifecycle.removed())BIG_CLOCK.onSystemTextChanged();
    }
    private static void captureSystemTextBattery(Intent intent) {
        if(ModuleLifecycle.removed()||intent==null||!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))return;
        systemTextChanged(BigClockSystemText.batteryRaw(intent.getIntExtra("level",-1),
                intent.getIntExtra("scale",-1),intent.getIntExtra("status",1)));
    }
    private static boolean captureSystemTextNetwork(boolean managerAvailable,boolean hasDefault,NetworkCapabilities capabilities) {
        if(ModuleLifecycle.removed())return false;
        BigClockSystemText.Transport transport=BigClockSystemText.Transport.UNKNOWN;
        Boolean validated=null;
        if(managerAvailable&&!hasDefault)transport=BigClockSystemText.Transport.NONE;
        else if(capabilities!=null) {
            if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))transport=BigClockSystemText.Transport.WIFI;
            else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))transport=BigClockSystemText.Transport.CELLULAR;
            else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))transport=BigClockSystemText.Transport.ETHERNET;
            else transport=BigClockSystemText.Transport.OTHER;
            validated=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return BigClockSystemText.network(transport,validated);
    }
    /** Voice registration alone can be out of service on a working data SIM.
     * Prefer already delivered WWAN registration records, excluding IWLAN. */
    private static Boolean systemTextService(ServiceState state) {
        if(state==null)return null;
        try {
            List<NetworkRegistrationInfo> registrations=state.getNetworkRegistrationInfoList();
            if(registrations!=null&&!registrations.isEmpty()) {
                for(NetworkRegistrationInfo registration:registrations)
                    if(registration!=null&&registration.getTransportType()==AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                            &&registration.isRegistered())return true;
                return false;
            }
        }catch(RuntimeException|LinkageError unavailable){ /* Older firmware exposes only aggregate state. */ }
        return state.getState()==ServiceState.STATE_IN_SERVICE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void updateRadioState() {
        if (context == null) {
            return;
        }
        boolean previousWifi = wifiConnected;
        boolean previousAirplane = airplaneMode;
        Boolean previousWifiSwitch = wifiSwitchEnabled, previousDataSwitch = mobileDataSwitchEnabled;
        Boolean nativeVisible = nativeWifiVisible();
        boolean systemChanged=false;
        wifiSwitchEnabled = null;
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
            boolean wifiEnabled=wifiManager!=null&&wifiManager.isWifiEnabled();
            wifiSwitchEnabled = wifiManager == null ? null : wifiEnabled;
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active=manager==null?null:manager.getActiveNetwork();
            NetworkCapabilities capabilities=active==null?null:manager.getNetworkCapabilities(active);
            boolean defaultWifi=capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            wifiConnected=RadioState.wifiInUse(wifiEnabled,nativeVisible,defaultWifi);
            airplaneMode = ((Integer) Class.forName("android.provider.Settings$Global").getMethod("getInt", ContentResolver.class, String.class, Integer.TYPE).invoke(null, context.getContentResolver(), "airplane_mode_on", 0)).intValue() != 0;
            if(!ModuleLifecycle.removed()) {
                systemChanged|=captureSystemTextNetwork(manager!=null,active!=null,capabilities);
                systemChanged|=BigClockSystemText.airplane(airplaneMode);
            }
        } catch (Throwable th) {
            wifiSwitchEnabled = null;
            ModuleDiagnostics.error("radio","Could not refresh native radio state; preserving last known state",th);
            Log.w(TAG, "Could not refresh radio state", th);
            if(!ModuleLifecycle.removed()) {
                systemChanged|=BigClockSystemText.network(BigClockSystemText.Transport.UNKNOWN,null);
                systemChanged|=BigClockSystemText.airplane(null);
            }
        }
        mobileDataSwitchEnabled = readMobileDataSwitchEnabled();
        DATA_BATTERY_SPACING.radioChanged(nativeVisible,wifiConnected,airplaneMode,wifiSwitchEnabled,mobileDataSwitchEnabled);
        if (previousWifi != wifiConnected || previousAirplane != airplaneMode
                || !java.util.Objects.equals(previousWifiSwitch,wifiSwitchEnabled)
                || !java.util.Objects.equals(previousDataSwitch,mobileDataSwitchEnabled)) {
            logFrameworkStage(Log.INFO,"Radio changed: wifiVisible="+wifiConnected+" airplane="+airplaneMode);
            invalidateComposeStyle();
        }
        systemTextChanged(systemChanged);
    }



    private static void registerNetworkObserver() {
        if (networkObserverRegistered || context == null) return;
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return;
            ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network){refreshRadio();}
                @Override public void onLost(Network network){refreshRadio();}
                @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){refreshRadio();}
                @Override public void onUnavailable(){refreshRadio();}
            };
            if (!wifiNetworkObserverRegistered) {
                manager.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback,MAIN);
                wifiNetworkObserverRegistered = true;
            }
            if (!defaultNetworkObserverRegistered) {
                manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network){refreshRadio();}
                @Override public void onLost(Network network){refreshRadio();}
                @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){refreshRadio();}
                },MAIN);
                defaultNetworkObserverRegistered = true;
            }
            networkObserverRegistered = wifiNetworkObserverRegistered && defaultNetworkObserverRegistered;
        } catch (Throwable error) { logFrameworkStage(Log.WARN, "Could not observe Wi-Fi connectivity", error); }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void scheduleRefresh() {
        synchronized (REFRESH_LOCK) {
            if (refreshPending) {
                return;
            }
            refreshPending = true;
            MAIN.post(new Runnable() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda18
                @Override // java.lang.Runnable
                public final void run() {
                    StatusBarModule.lambda$scheduleRefresh$18();
                }
            });
        }
    }

    static /* synthetic */ void lambda$scheduleRefresh$18() {
        synchronized (REFRESH_LOCK) {
            if (!refreshPending) return;
            refreshPending = false;
        }
        refreshLabel();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void refreshLabel() {
        String strNormalize = "";
        try {
            int iIntValue = ((Integer) getActiveDataSubId.invoke(null, new Object[0])).intValue();
            if (iIntValue < 0) {
                iIntValue = ((Integer) getDefaultDataSubId.invoke(null, new Object[0])).intValue();
            }
            if (iIntValue >= 0 && !airplaneMode) {
                synchronized (MODELS) {
                    WeakReference<Object> weakReference = MODELS.get(Integer.valueOf(iIntValue));
                    Object obj = weakReference == null ? null : weakReference.get();
                    if (obj != null && ((Integer) getSubscriptionId.invoke(obj, new Object[0])).intValue() == iIntValue) {
                        Object objInvoke = getNetworkTypeFlow.invoke(obj, new Object[0]);
                        Object objInvoke2 = objInvoke == null ? null : getFlowValue.invoke(objInvoke, new Object[0]);
                        Object objInvoke3 = objInvoke2 != null ? getTypeModel.invoke(objInvoke2, new Object[0]) : null;
                        String raw=objInvoke3==null?"":(String)getTypeName.invoke(objInvoke3,new Object[0]);
                        strNormalize=NetworkLabel.canonical(raw);
                    }
                }
            }
        } catch (Throwable th) {
            strNormalize = "";
        }
        int activeSub=activeDataSubscription();
        observeDataSubscription(activeSub);
        Boolean previousDataSwitch = mobileDataSwitchEnabled;
        mobileDataSwitchEnabled = readMobileDataSwitchEnabled();
        if (!java.util.Objects.equals(previousDataSwitch,mobileDataSwitchEnabled)) {
            DATA_BATTERY_SPACING.dataChanged(mobileDataSwitchEnabled); invalidateComposeStyle();
        }
        if(strNormalize.isEmpty()&&!airplaneMode&&cellularDataAvailable()) {
            strNormalize=NetworkLabel.canonical(NativeDataSource.label(activeSub));
        }
        if(strNormalize.isEmpty()&&!airplaneMode) {
            strNormalize=NetworkLabel.canonical(SINGLE_LABEL.nativeLabel(activeSub));
        }
        if (strNormalize.isEmpty() && !airplaneMode) strNormalize=NetworkLabel.canonical(readTelephonyNetworkLabel());
        // Component text uses the actual current RAT before status-bar hiding,
        // normalisation and user replacement words are applied below.
        if(!ModuleLifecycle.removed())systemTextChanged(BigClockSystemText.dataNetwork(
                BigClockSystemText.snapshot().subscriptionEpoch,activeSub,strNormalize));
        // Gates first (Wi-Fi in use, airplane, radios off), then the text of the generation: custom contents or unify, never both.
        strNormalize = RadioState.cellularLabel(true,wifiConnected,airplaneMode,strNormalize,"",
                false,wifiSwitchEnabled,mobileDataSwitchEnabled);
        strNormalize = LABEL_FORMATS.display(strNormalize,FEATURES.effective("label","label_normalize_enabled"),
                FEATURES.effective("label",NetworkLabelFormats.ENABLED));
        SINGLE_LABEL.update(FEATURES,strNormalize,activeSub,labelOffsetXdp,labelOffsetYdp,labelScalePercent,
                0f,fontWeight,styleColors,customColorAlpha);
        SINGLE_SIGNAL.activeSubscription(activeSub);
        NATIVE_BADGE_BINDINGS.activeSubscription(activeSub);
        if (!networkLabel.equals(strNormalize)) {
            networkLabel = strNormalize;
            DATA_BATTERY_SPACING.fallbackChanged(strNormalize);
            logFrameworkStage(Log.INFO,"Active data label updated: "+NetworkLabel.normalize(strNormalize));
            invalidateComposeStyle();
        }
        synchronized (SLOTS) {
            try {
                for (Map.Entry<View, Slot> entry : SLOTS.entrySet()) {
                    View key = entry.getKey();
                    Slot value = entry.getValue();
                    // Compose retains all native geometry and animation. Only
                    // the explicit hiding policy owns its root visibility.
                    if (composeStatusBar) { applyWifiVisibility(key, value); continue; }
                    boolean zCellularMode = cellularMode();
                    boolean z = (value.appliedCellularMode == zCellularMode && strNormalize.equals(value.appliedLabel)) ? false : true;
                    value.appliedCellularMode = zCellularMode;
                    value.appliedLabel = strNormalize;
                    int iRound = zCellularMode ? legacyLabelGeometry(key,value).width : value.originalMinimumWidth;
                    boolean z2 = key.getMinimumWidth() != iRound;
                    if (z2) {
                        key.setMinimumWidth(iRound);
                    }
                    applyWifiVisibility(key, value);
                    key.invalidate();
                    if (z2 || z) {
                        key.requestLayout();
                        if (key.getParent() instanceof View) {
                            ((View) key.getParent()).requestLayout();
                        }
                    }
                }
            } catch (Throwable th3) {
                throw th3;
            }
        }
    }

    private static int activeDataSubscription() {
        try {
            int sub=(Integer)getActiveDataSubId.invoke(null);
            return sub>=0?sub:(Integer)getDefaultDataSubId.invoke(null);
        }catch(Exception unavailable){return -1;}
    }

    private static final class DataRadioCallback extends TelephonyCallback implements
            TelephonyCallback.DisplayInfoListener,TelephonyCallback.DataConnectionStateListener,TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener {
        final int subscription;
        final long systemEpoch;
        TelephonyDisplayInfo display;
        DataRadioCallback(int sub,long epoch){subscription=sub;systemEpoch=epoch;}
        private boolean current(){return this==dataRadioCallback&&subscription==activeDataSubscription();}
        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
            if(!current())return;display=info;refreshRadio();
        }
        @Override public void onDataConnectionStateChanged(int state,int networkType){if(current())refreshRadio();}
        @Override public void onServiceStateChanged(ServiceState state){
            if(!current())return;captureService(state);refreshRadio();
        }
        @Override public void onSignalStrengthsChanged(SignalStrength strength){if(current())captureSignal(strength);}
        private void captureService(ServiceState state) {
            if(!ModuleLifecycle.removed())systemTextChanged(BigClockSystemText.service(systemEpoch,subscription,systemTextService(state)));
        }
        private void captureSignal(SignalStrength strength) {
            if(!ModuleLifecycle.removed())systemTextChanged(BigClockSystemText.signal(systemEpoch,subscription,strength==null?null:strength.getLevel()));
        }
    }

    private static void observeDataSubscription(int subscription) {
        long systemEpoch=BigClockSystemText.snapshot().subscriptionEpoch;
        if(!ModuleLifecycle.removed()) {
            long revision=BigClockSystemText.revision();systemEpoch=BigClockSystemText.selectSubscription(subscription);
            systemTextChanged(revision!=BigClockSystemText.revision());
        }
        if(subscription==observedDataSub&&observedTelephony!=null)return;
        if(observedTelephony!=null&&dataRadioCallback!=null)try{observedTelephony.unregisterTelephonyCallback(dataRadioCallback);}catch(Exception ignored){}
        observedDataSub=subscription;observedTelephony=null;dataRadioCallback=null;
        mobileDataSwitchEnabled = null;
        if(subscription<0||context==null)return;
        try {
            TelephonyManager manager=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            if(manager==null)return;
            observedTelephony=manager.createForSubscriptionId(subscription);
            if(android.os.Build.VERSION.SDK_INT>=31) {
                DataRadioCallback callback=new DataRadioCallback(subscription,systemEpoch);dataRadioCallback=callback;
                observedTelephony.registerTelephonyCallback(MAIN::post,callback);
                // One initial snapshot per observer lifecycle; subsequent data
                // comes from these same existing telephony callbacks.
                try{callback.captureService(observedTelephony.getServiceState());}catch(RuntimeException unavailable){ }
                try{callback.captureSignal(observedTelephony.getSignalStrength());}catch(RuntimeException unavailable){ }
            }
        }catch(Exception error){logFrameworkStage(Log.WARN,"Active data subscription observer unavailable",error);}
    }

    private static Boolean readMobileDataSwitchEnabled() {
        if (observedTelephony == null || observedDataSub != activeDataSubscription()) return null;
        try { return observedTelephony.isDataEnabled(); }
        catch (RuntimeException unavailable) { return null; }
    }

    private static String readTelephonyNetworkLabel() {
        if(!cellularDataAvailable())return "";
        try {
            int type=observedTelephony.getDataNetworkType();
            // Wi-Fi calling is not a cellular data generation.
            if(type==TelephonyManager.NETWORK_TYPE_UNKNOWN||type==TelephonyManager.NETWORK_TYPE_IWLAN)return "";
            TelephonyDisplayInfo info=dataRadioCallback==null?null:dataRadioCallback.display;
            return info==null?NetworkLabel.forDataNetwork(type,TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
                    :NetworkLabel.forDataNetwork(type,info.getNetworkType(),info.getOverrideNetworkType());
        }catch(Exception error){logFrameworkStage(Log.WARN,"Could not read current cellular data type",error);return "";}
    }

    /** A label as a short identifier-like word for the diagnostics report; anything else is just "other". */




    private static boolean cellularDataAvailable() {
        if(context==null||observedTelephony==null||observedDataSub!=activeDataSubscription())return false;
        try {
            int state=observedTelephony.getDataState();
            return observedTelephony.isDataEnabled()&&(state==TelephonyManager.DATA_CONNECTED||state==TelephonyManager.DATA_SUSPENDED)
                    &&observedTelephony.getDataNetworkType()!=TelephonyManager.NETWORK_TYPE_IWLAN;
        }catch(Exception unavailable){return false;}
    }

    private static String labelForNetworkType(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
            case 19: // LTE_CA
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            default:
                return "";
        }
    }

    /** Bounded scalar counters, sampled only by the verified own-app probe. */
    private static void reportBackdropFact(String topic,java.util.Map<String,Object> state) {
        Object[] pairs=new Object[state.size()*2];int index=0;
        for(java.util.Map.Entry<String,Object> entry:state.entrySet()) {
            pairs[index++]=entry.getKey();pairs[index++]=entry.getValue();
        }
        ModuleDiagnostics.fact("clock",topic,"大时钟背景刷新状态",pairs);
    }

    /** Pre-Compose ROMs keep their original glyph-height normalization, with automatic width. */
    private static NetworkLabelGeometry.Plan legacyLabelGeometry(View view,Slot slot) {
        float f = view.getResources().getDisplayMetrics().density;
        try {
            if(FEATURES.textStyle("label") || FEATURES.enabled("font")&&!FontRepository.systemMode(FontScopes.NETWORK_TYPE))
                FontRepository.apply(slot.paint,TEXT.nativeFamily(),FEATURES.textStyle("label")?fontWeight:nativeWeight(TEXT.nativeFamily()),null,FontScopes.NETWORK_TYPE);
            else FontWeight.restore(slot.paint,TEXT.nativeFamily(),null);
            slot.paint.setFakeBoldText(false);
        } catch (Throwable th) {
            slot.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            slot.paint.setFakeBoldText(false);
        }
        slot.paint.setTextSize(13.0f * f);
        slot.paint.setTextScaleX(1.0f);
        slot.paint.setColor(styleColor("label", slot.tint));
        slot.paint.getTextBounds(networkLabel, 0, networkLabel.length(), slot.textBounds);
        float glyphScale = NumericPolicy.scale(labelScalePercent / 100f,
                (NETWORK_GLYPH_HEIGHT_DP*f)/Math.max(1,slot.textBounds.height()),Math.max(slot.textBounds.width(),slot.textBounds.height()));
        slot.labelGeometry=NetworkLabelGeometry.measure(slot.paint,networkLabel,f,glyphScale*100f,slot.labelGeometry,slot.textBounds);
        return slot.labelGeometry;
    }

    private static void drawLabel(ViewGroup viewGroup, Slot slot, Canvas canvas) {
        float f = viewGroup.getResources().getDisplayMetrics().density;
        float centerX = viewGroup.getWidth() / 2.0f;
        float centerY = viewGroup.getHeight() / 2.0f;
        float f2 = NumericPolicy.pixels((double)centerX+NumericPolicy.pixels(labelOffsetXdp,f));
        float f3 = NumericPolicy.pixels((double)centerY+NumericPolicy.pixels((double)(labelOffsetYdp - 0.375f)*f));
        NetworkLabelGeometry.Plan geometry=legacyLabelGeometry(viewGroup,slot);
        int iSave = canvas.save();
        try {
            canvas.scale(slot.appearAmount, slot.appearAmount, centerX, centerY);
            geometry.draw(canvas,networkLabel,slot.paint,f2,f3);
        } finally { canvas.restoreToCount(iSave); }
    }
}
