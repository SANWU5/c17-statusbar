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
import android.graphics.PorterDuff;
import android.graphics.Rect;
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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.TypedValue;
import java.lang.reflect.Field;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes.dex */
public final class StatusBarModule extends XposedModule {
    private static final String MOBILE_VM = "com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconViewModel";
    private static final String MODERN_VIEW = "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView";
    private static final String MODULE_PACKAGE = "dev.puitheme.iosstatusbar";
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
    private static boolean wifiNetworkObserverRegistered, defaultNetworkObserverRegistered;
    private static volatile boolean airplaneMode;
    private static Context context;
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
    private static volatile boolean settingsObserverRegistered;
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
    private static volatile float slotWidthDp = 22;
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
    }
    private static volatile int fontWeight = StatusBarSettings.DEFAULT_FONT_WEIGHT;
    private static volatile String networkLabel = "";
    private static volatile boolean wifiConnected;
    private static final Map<View,WeakReference<Object>> NATIVE_WIFI=Collections.synchronizedMap(new WeakHashMap<>());
    private static Method nativeWifiFlow;
    private static Class<?> nativeWifiIconClass,nativeWifiVisibleClass;
    private static int observedDataSub=-1;
    private static TelephonyManager observedTelephony;
    private static DataRadioCallback dataRadioCallback;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final TextControls TEXT = new TextControls(MAIN);
    private static final TilePageEffects TILE_EFFECTS = new TilePageEffects();
    private static final QsTileAppearance QS_APPEARANCE = new QsTileAppearance();
    private static final QsMediaAppearance QS_MEDIA = new QsMediaAppearance();
    private static final NotificationBigClock BIG_CLOCK = new NotificationBigClock();
    private static final NotificationClearAppearance CLEAR_APPEARANCE = new NotificationClearAppearance();
    private static volatile boolean bigClockEnabled;
    private static volatile NativeBackdropMotion notificationBackdropMotion;
    private static boolean tileDispatchLogged, tileScrollLogged, tileStateLogged;
    private static final NetworkBadgeControls BADGES = new NetworkBadgeControls(MAIN);
    private static final Map<View,String> OVERFLOW_OWNERS=Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile BatteryControls BATTERY;
    private static final Map<View, Slot> SLOTS = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Integer, WeakReference<Object>> MODELS = new HashMap();
    private static final Object REFRESH_LOCK = new Object();
    private static final Object SYSTEMUI_HOOK_LOCK = new Object();
    private enum HookGroup {
        CONTEXT, MODEL, DRAWABLE, BADGE, WIFI, SPEED, COMPOSE, TEXT, BATTERY, TILES, QS_APPEARANCE, QS_MEDIA, BIG_CLOCK, NOTIFICATION_CLEAR
    }
    private static final Set<HookGroup> COMPLETED_HOOK_GROUPS = EnumSet.noneOf(HookGroup.class);
    private static final Map<HookGroup, Set<Executable>> INSTALLED_HOOKS = new EnumMap<>(HookGroup.class);
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
        int requestedVisibility;
        boolean settingVisibility;
        int visibleState = 2;
        int tint = -855638017;
        float appearAmount = 1.0f;
        final Paint paint = new Paint(129);
        final Rect textBounds = new Rect();

        Slot(View view) {
            this.originalMinimumWidth = view.getMinimumWidth();
            this.requestedVisibility = view.getVisibility();
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
            this.delegate.setCallback(this);
            onBoundsChange(getBounds());
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

        private void chooseDelegate() {
            String group=wifiIcon?"wifi":"data";
            Drawable wanted=FEATURES.effective(group,group+"_icon_enabled")||nativeDrawable==null?styled:nativeDrawable;
            if(delegate==wanted)return;
            delegate.setCallback(null);delegate=wanted;delegate.setCallback(this);
            delegate.setState(getState());delegate.setLevel(getLevel());delegate.setAlpha(nativeAlpha);
            restoreNativeFilter();setDelegateBounds(getBounds());tintApplied=false;
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
            Rect bounds = getBounds();
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float offsetX = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetXdp : StatusBarModule.dataOffsetXdp);
            float offsetY = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetYdp : StatusBarModule.dataOffsetYdp);
            float scalePercent = this.composePlacement ? 100 : (this.wifiIcon ? StatusBarModule.wifiIconScalePercent : StatusBarModule.dataIconScalePercent);
            if (!this.composePlacement && FEATURES.color(wifiIcon ? "wifi" : "data")) {
                int tint = styleColor(this.wifiIcon ? "wifi" : "data", this.nativeTint);
                if (!this.tintApplied || this.appliedTint != tint) {
                    this.tintApplied = true;
                    this.appliedTint = tint;
                    this.delegate.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
                }
            } else if(tintApplied) { restoreNativeFilter(); tintApplied=false; }
            int iSave = canvas.save();
            float scale = NumericPolicy.scale(scalePercent / 100.0f,Math.max(bounds.width(),bounds.height()));
            canvas.translate(NumericPolicy.pixels((double)centerX+NumericPolicy.pixels(offsetX,this.density)),NumericPolicy.pixels((double)centerY+NumericPolicy.pixels(offsetY,this.density)));
            canvas.scale(scale, scale);
            canvas.translate(-centerX, -centerY);
            setDelegateBounds(bounds);
            this.delegate.draw(canvas);
            canvas.restoreToCount(iSave);
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
        synchronized (SYSTEMUI_HOOK_LOCK) {
            if (COMPLETED_HOOK_GROUPS.size() == HookGroup.values().length) {
                moduleLog(Log.INFO, TAG, "All SystemUI hook groups already installed at " + phase);
                return;
            }
            moduleLog(Log.INFO, TAG, "Installing unfinished SystemUI hook groups at " + phase);
            try {
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.CONTEXT) && installSystemUiContextHook(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.CONTEXT);
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
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.SPEED) && installSpeedControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.SPEED);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.COMPOSE) && installComposeSupport(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.COMPOSE);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.TEXT) && installTextControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.TEXT);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.BATTERY) && installBatteryControls(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.BATTERY);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.TILES) && installTilePageEffects(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.TILES);
                }
                if (!COMPLETED_HOOK_GROUPS.contains(HookGroup.QS_APPEARANCE) && installQsAppearance(classLoader)) {
                    COMPLETED_HOOK_GROUPS.add(HookGroup.QS_APPEARANCE);
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
                int priority = COMPLETED_HOOK_GROUPS.size() == HookGroup.values().length ? Log.INFO : Log.WARN;
                moduleLog(priority, TAG, "SystemUI hook groups after " + phase + ": " + COMPLETED_HOOK_GROUPS);
            } catch (Throwable error) {
                moduleLog(Log.ERROR, TAG, "SystemUI hook setup failed at " + phase, error);
            }
        }
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
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
            installed.add(method);
        }
    }

    private static void logFrameworkStage(int priority, String message) {
        ModuleDiagnostics.info("systemui", diagnosticMessage(message));
        StatusBarModule logger = frameworkLogger;
        if (logger != null) {
            logger.log(priority, TAG, message);
        } else {
            Log.println(priority, TAG, message);
        }
    }

    private static void logFrameworkStage(int priority, String message, Throwable error) {
        ModuleDiagnostics.error("systemui", diagnosticMessage(message), error);
        StatusBarModule logger = frameworkLogger;
        if (logger != null) {
            logger.log(priority, TAG, message, error);
        } else {
            Log.println(priority, TAG, message + '\n' + Log.getStackTraceString(error));
        }
    }

    private void moduleLog(int priority, String tag, String message) {
        ModuleDiagnostics.info("hooks", diagnosticMessage(message));
        super.log(priority, tag, message);
    }

    private void moduleLog(int priority, String tag, String message, Throwable error) {
        ModuleDiagnostics.error("hooks", diagnosticMessage(message), error);
        super.log(priority, tag, message, error);
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
            BIG_CLOCK.setVisibilityListener(TEXT::visibilityChanged);
            TEXT.setAdditionalClock(BIG_CLOCK::hasVisibleTime, BIG_CLOCK::usesSeconds,
                    () -> BIG_CLOCK.onNativeClockUpdated(null));
            for (Method event : controller.getDeclaredMethods()) {
                if (!event.getName().matches("setExpandedHeightInternal|onPanelStateChanged\\$1|onStateChanged|onConfigurationChanged")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    if (bigClockEnabled) nativeAccess.panel(chain.getThisObject());
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
                    if (bigClockEnabled) nativeAccess.panel(nativeAccess.lastController.get());
                    return result;
                });
            }
            Class<?> header = loader.loadClass("com.oplus.systemui.separate.OplusQSSimpleHeader");
            for (Method event : header.getDeclaredMethods()) {
                String name = event.getName();
                if (!name.matches("onInit|onAttachedToWindow|onDetachedFromWindow|onConfigurationChanged|updateClickAbilities")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
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
                if (bigClockEnabled) nativeAccess.scroll((View) chain.getThisObject());
                return result;
            });
            for (Method event : ext.getDeclaredMethods()) {
                if (!event.getName().matches("updateOverDistance|resetOverDistance")) continue;
                deoptimize(event);
                installHook(HookGroup.BIG_CLOCK, event, chain -> {
                    Object result = chain.proceed();
                    if (bigClockEnabled) nativeAccess.scroll((View) nativeAccess.extView.invoke(chain.getThisObject()));
                    return result;
                });
            }
            Method intrinsic = stack.getDeclaredMethod("setIntrinsicPadding", Integer.TYPE);
            Method padding = stack.getDeclaredMethod("updateTopPadding", Float.TYPE, Boolean.TYPE);
            deoptimize(intrinsic); deoptimize(padding);
            installHook(HookGroup.BIG_CLOCK, intrinsic, chain -> !bigClockEnabled ? chain.proceed()
                    : chain.proceed(new Object[]{BIG_CLOCK.adjustIntrinsicPadding((View) chain.getThisObject(), (Integer) chain.getArg(0))}));
            installHook(HookGroup.BIG_CLOCK, padding, chain -> !bigClockEnabled ? chain.proceed()
                    : chain.proceed(new Object[]{BIG_CLOCK.adjustTopPadding((View) chain.getThisObject(), (Float) chain.getArg(0)), chain.getArg(1)}));
            Method range = stack.getDeclaredMethod("getScrollRange"); deoptimize(range);
            installHook(HookGroup.BIG_CLOCK, range, chain -> {
                Object result = chain.proceed();
                return bigClockEnabled ? BIG_CLOCK.adjustScrollRange((View) chain.getThisObject(), (Integer) result) : result;
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
                if (bigClockEnabled) { nativeAccess.scroll((View) chain.getThisObject()); BIG_CLOCK.onConfigurationChanged(); }
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
            installNotificationHeaderStyleHook(fake);
            Method fraction = listener.getDeclaredMethod("onFractionChanged", Float.TYPE); deoptimize(fraction);
            installHook(HookGroup.BIG_CLOCK, fraction, chain -> {
                Object result = chain.proceed();
                if (bigClockEnabled) {
                    // This callback carries the displayed spring fraction, not its height target.
                    float displayedFraction = (Float) chain.getArg(0);
                    nativeAccess.panel(nativeAccess.lastController.get(), displayedFraction);
                    Object fakeController = owner.get(chain.getThisObject());
                    if (fakeClock != null)
                        BIG_CLOCK.onFakeClockChanged((View) fakeClock.get(fakeController), displayedFraction,
                                nativePhoneLeftAnchor((View) fakeClock.get(fakeController), true));
                    if (fakeCarrier != null)
                        BIG_CLOCK.onFakeCarrierChanged((View) fakeCarrier.get(fakeController), displayedFraction);
                    if (fakeNotification != null)
                        BIG_CLOCK.onFakeNotificationChanged((View) fakeNotification.get(fakeController),
                                nativePhoneLeftAnchor((View) fakeNotification.get(fakeController), false), displayedFraction);
                    if (fakeStatusIcons != null && fakeHeader != null)
                        BIG_CLOCK.onFakeStatusChanged((View) fakeStatusIcons.get(fakeController), (View) fakeHeader.get(fakeController),
                                nativePhoneAnchor((View) fakeStatusIcons.get(fakeController)));
                }
                return result;
            });
            Method fractionEnd = optionalMethod(listener, "onFractionEnd", Float.TYPE);
            if (fractionEnd != null) try {
                deoptimize(fractionEnd);
                installHook(HookGroup.BIG_CLOCK, fractionEnd, chain -> {
                    Object result = chain.proceed();
                    if (bigClockEnabled) nativeAccess.panel(nativeAccess.lastController.get());
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
            moduleLog(Log.INFO, TAG, "Portrait notification big clock native events installed");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Notification big clock hooks incomplete; retry at next package phase", error);
            return false;
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
                    if (bigClockEnabled) nativeAccess.panel(chain.getThisObject());
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
                if (bigClockEnabled) nativeAccess.verticalChanged(chain.getThisObject());
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
            Method ready = optionalMethod(pager, "ensureProgressiveHelperReady");
            if (ready == null) throw new NoSuchMethodException("ensureProgressiveHelperReady");
            deoptimize(ready);
            installHook(HookGroup.BIG_CLOCK, ready, chain -> {
                Object result = chain.proceed();
                if (bigClockEnabled) nativeAccess.pageReady(chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Notification clock native page motion unavailable", error);
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
                if (bigClockEnabled) {
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
                }
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
    private static final class NativePhoneAnchorReader {
        final Class<?> systemCopy;
        final Class<?> clockCopy, notificationCopy;
        final Method source;
        NativePhoneAnchorReader(ClassLoader loader) throws ReflectiveOperationException {
            systemCopy = loader.loadClass("com.oplus.systemui.seamless.widget.SystemIconFakeFrame");
            clockCopy = optionalCopyClass(loader, "com.oplus.systemui.seamless.widget.ClockFakeFrame");
            notificationCopy = optionalCopyClass(loader, "com.oplus.systemui.seamless.widget.NotificationFakeFrame");
            source = loader.loadClass("com.oplus.systemui.seamless.widget.StatusBarFakeFrame").getDeclaredMethod("getMView");
            source.setAccessible(true);
        }
        private static Class<?> optionalCopyClass(ClassLoader loader, String name) {
            try { return loader.loadClass(name); }
            catch (ClassNotFoundException | LinkageError unavailable) { return null; }
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
        installNotificationFooterMarginHook(stack);
        installNotificationTailOutlineHooks(loader);
        installNotificationTailContentHook(loader);
        installNotificationTailWidthHooks(loader);
        int targetHooks = 0, calculationHooks = 0;
        try {
            for (Method event : stack.getDeclaredMethods()) {
                String name = event.getName();
                if (event.getParameterTypes().length != 0) continue;
                boolean targets = name.matches("applyCurrentState(?:\\$[0-9]+)?|startAnimationToState(?:\\$[0-9]+)?");
                boolean calculates = name.matches("updateChildren(?:\\$[0-9]+)?|updateViewStates");
                if (!targets && !calculates) continue;
                try {
                    deoptimize(event);
                    if (targets) {
                        installHook(HookGroup.BIG_CLOCK, event, chain -> {
                            if (!bigClockEnabled) return chain.proceed();
                            View view = (View) chain.getThisObject();
                            BIG_CLOCK.onStackApplicationStarting(view);
                            try {
                                BIG_CLOCK.onStackLayoutUpdated(view);
                                return chain.proceed();
                            } finally { BIG_CLOCK.onStackApplicationFinished(view); }
                        });
                        targetHooks++;
                    } else {
                        installHook(HookGroup.BIG_CLOCK, event, chain -> {
                            View view = (View) chain.getThisObject();
                            BIG_CLOCK.onStackLayoutStarting(view);
                            try {
                                Object result = chain.proceed();
                                if (bigClockEnabled) BIG_CLOCK.onStackLayoutUpdated(view);
                                return result;
                            } finally { BIG_CLOCK.onStackLayoutFinished(view); }
                        });
                        calculationHooks++;
                    }
                } catch (Throwable unavailable) {
                    moduleLog(Log.WARN, TAG, "Optional native notification stack target hook unavailable", unavailable);
                }
            }
            // Some native updates apply or animate a single row outside applyCurrentState.
            for (String name : new String[]{"com.android.systemui.statusbar.notification.stack.ViewState",
                    "com.android.systemui.statusbar.notification.stack.ExpandableViewState"}) {
                try {
                    Class<?> state = loader.loadClass(name);
                    for (Method event : state.getDeclaredMethods()) {
                        Class<?>[] parameters = event.getParameterTypes();
                        if (!event.getName().matches("applyToView|animateTo") || parameters.length == 0
                                || !View.class.isAssignableFrom(parameters[0])) continue;
                        deoptimize(event);
                        installHook(HookGroup.BIG_CLOCK, event, chain -> {
                            View host = notificationStackAncestor((View) chain.getArg(0));
                            if (host == null || !bigClockEnabled) return chain.proceed();
                            BIG_CLOCK.onStackApplicationStarting(host);
                            try {
                                BIG_CLOCK.onStackLayoutUpdated(host);
                                return chain.proceed();
                            } finally { BIG_CLOCK.onStackApplicationFinished(host); }
                        });
                    }
                } catch (Throwable unavailable) {
                    moduleLog(Log.WARN, TAG, "Optional per-row native notification state hooks unavailable", unavailable);
                }
            }
            Method detached = optionalMethod(stack, "onDetachedFromWindow");
            if (detached != null) try {
                deoptimize(detached);
                installHook(HookGroup.BIG_CLOCK, detached, chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    if (notificationStackAncestor(view) == view) BIG_CLOCK.onStackDetached(view);
                    return result;
                });
            } catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional notification stack detach hook unavailable", unavailable);
            }
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Native notification stacking unavailable; clock remains enabled", unavailable);
        }
        NotificationBigClockStack.setNativeHooksAvailable(targetHooks > 0 && calculationHooks > 0);
        moduleLog(Log.INFO, TAG, "Optional notification stack projection hooks " + targetHooks + "/" + calculationHooks
                + "; nested layout cycle cache enabled");
    }

    /** Rounded tail paths must be computed from the whole native card before its visible window clips. */
    private void installNotificationTailOutlineHooks(ClassLoader loader) {
        Class<?> outline;
        try {
            outline = loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableOutlineView");
            Method path = outline.getDeclaredMethod("getClipPath", Boolean.TYPE);
            deoptimize(path);
            // SystemUI's precompiled callers can retain an inlined clip calculation even
            // when the callee is hooked. Deoptimize only the two standard drawing callers.
            deoptimize(outline.getDeclaredMethod("drawChild", Canvas.class, View.class, Long.TYPE));
            try {
                Class<?> provider = loader.loadClass("com.android.systemui.statusbar.notification.row.ExpandableOutlineView$1");
                deoptimize(provider.getDeclaredMethod("getOutline", View.class, android.graphics.Outline.class));
            } catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional native notification outline provider deoptimization unavailable", unavailable);
            }
            installHook(HookGroup.BIG_CLOCK, path, chain -> !bigClockEnabled ? chain.proceed()
                    : BIG_CLOCK.withNativeTailOutline((View) chain.getThisObject(), () -> chain.proceed()));
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification tail outline unavailable", unavailable);
            return;
        }
        try {
            Class<?> glass = loader.loadClass("com.oplus.systemui.statusbar.notification.row.OplusExpandableOutlineViewExImpl");
            Method spotlight = glass.getDeclaredMethod("getSpotLightClipSpec", outline);
            deoptimize(spotlight);
            try {
                Class<?> provider = loader.loadClass("com.oplus.systemui.statusbar.notification.row.OplusActivatableNotificationViewExImpl$drawNotificationSpotLightIfInstalled$1");
                deoptimize(provider.getDeclaredMethod("getClipSpec"));
            } catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional native notification highlight provider deoptimization unavailable", unavailable);
            }
            installHook(HookGroup.BIG_CLOCK, spotlight, chain -> !bigClockEnabled ? chain.proceed()
                    : BIG_CLOCK.withNativeTailOutline((View) chain.getArg(0), () -> chain.proceed()));
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification tail highlight outline unavailable", unavailable);
        }
    }

    /** Folded tails retain native glass; only their owned text/icon content fades. */
    private void installNotificationTailContentHook(ClassLoader loader) {
        for (String name : new String[]{"com.android.systemui.statusbar.notification.row.NotificationContentView",
                "com.oplus.systemui.notification.row.oplusgroup.OplusNotificationChildrenContainer"}) {
            try {
                Class<?> content = loader.loadClass(name);
                Method draw = content.getDeclaredMethod("dispatchDraw", Canvas.class);
                deoptimize(draw);
                installHook(HookGroup.BIG_CLOCK, draw, chain -> !bigClockEnabled ? chain.proceed()
                        : BIG_CLOCK.withNativeTailContent((View) chain.getThisObject(),
                                (Canvas) chain.getArg(0), () -> chain.proceed()));
                moduleLog(Log.INFO, TAG, "Native notification tail content draw connected: " + name);
            } catch (Throwable unavailable) {
                moduleLog(Log.WARN, TAG, "Optional native notification tail content draw unavailable: " + name, unavailable);
            }
        }
    }

    /** Target and spring width stay scoped to the exact rows owned by our three tail layers. */
    private void installNotificationTailWidthHooks(ClassLoader loader) {
        boolean targetHook = false, rowHook = false;
        try {
            Class<?> type = loader.loadClass("com.oplus.systemui.statusbar.notification.stack.OplusExpandableViewStateExImpl");
            Method width = type.getDeclaredMethod("getClipWidth");
            if (width.getReturnType() != Integer.TYPE)
                throw new NoSuchMethodException("Unexpected native target clip width type");
            deoptimize(width);
            deoptimize(type.getDeclaredMethod("beforeApplyToView", View.class));
            deoptimize(type.getDeclaredMethod("beforeAnimateTo", View.class,
                    loader.loadClass("com.android.systemui.statusbar.notification.stack.AnimationProperties")));
            installHook(HookGroup.BIG_CLOCK, width, chain -> {
                Object value = chain.proceed();
                return bigClockEnabled ? BIG_CLOCK.nativeTailTargetClipWidth(chain.getThisObject(), (Integer) value) : value;
            });
            targetHook = true;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification target width unavailable", unavailable);
        }
        try {
            Class<?> type = loader.loadClass("com.oplus.systemui.statusbar.notification.row.OplusExpandableViewExImpl");
            Method width = type.getDeclaredMethod("setClipWidth", Integer.TYPE);
            Method readWidth = type.getDeclaredMethod("getClipWidth");
            readWidth.setAccessible(true);
            deoptimize(width);
            Class<?> property = loader.loadClass("com.oplus.systemui.statusbar.notification.stack.OplusExpandableViewStateExImpl$Companion$CLIP_WIDTH_PROPERTY$1");
            deoptimize(property.getDeclaredMethod("accept", Object.class, Object.class));
            installHook(HookGroup.BIG_CLOCK, width, chain -> {
                if (!bigClockEnabled) return chain.proceed();
                Object owner = chain.getThisObject();
                int previous = (Integer) readWidth.invoke(owner);
                Object result = chain.proceed(new Object[]{BIG_CLOCK.nativeTailRowClipWidth(owner, (Integer) chain.getArg(0))});
                if (previous != (Integer) readWidth.invoke(owner)) BIG_CLOCK.onNativeTailWidthChanged(owner);
                return result;
            });
            rowHook = true;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification row width unavailable", unavailable);
        }
        NotificationBigClockStack.setNativeWidthHooksAvailable(targetHook && rowHook);
        moduleLog(Log.INFO, TAG, "Optional native notification tail widths " + targetHook + "/" + rowHook);
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
                return bigClockEnabled ? BIG_CLOCK.adjustEmptyBottomMargin((View) chain.getThisObject(),
                        (Integer) result) : result;
            });
            available = true;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification bottom margin hook unavailable", unavailable);
        }
        BIG_CLOCK.onFooterMarginHookAvailable(available);
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
            lastController = new WeakReference<>(controller);
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
            BIG_CLOCK.onPanelMotionState(running, finite && displayedFraction <= 0f && targetClosed && !running);
            BIG_CLOCK.onPanelChanged((View) panelView.get(controller), displayedFraction, barState.getInt(controller), inQs);
            readVertical(controller, animation);
            if (backdropMotion != null) backdropMotion.finishIfIdle();
        }
        private void readVertical(Object controller, Object animation) {
            if (controller == null || animation == null || panelTranslationValue == null || panelTranslationY == null) return;
            try {
                Object value = panelTranslationValue.invoke(animation);
                lastVerticalValue = new WeakReference<>(value);
                Object offset = value == null ? null : panelTranslationY.invoke(value);
                if (offset instanceof Number)
                    BIG_CLOCK.onPanelTranslationChanged((View) panelView.get(controller), ((Number) offset).floatValue());
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
            if (host == null || host.getResources().getConfiguration().orientation
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

    private boolean installBatteryControls(ClassLoader loader) {
        try {
            String root = "com.oplus.systemui.statusbar.pipeline.battery.ui.";
            Class<?> meter = loader.loadClass(root + "view.StatBatteryMeterView");
            Class<?> horizontal = loader.loadClass(root + "drawable.HorizontalBatteryContentDrawable");
            Class<?> charge = loader.loadClass(root + "model.ChargeIcon");
            Class<?> binder = loader.loadClass(root + "binder.BatteryViewBinder");
            BatteryControls controls = BATTERY;
            if (controls == null) BATTERY = controls = new BatteryControls(MAIN, meter, horizontal, charge);
            final BatteryControls controller = controls;
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
                controller.prepareDraw((Drawable)chain.getThisObject());return chain.proceed();
            });
            for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
                final boolean attached = event.equals("onAttachedToWindow");
                installHook(HookGroup.BATTERY, meter.getDeclaredMethod(event), chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    if (attached) { initializeContext(view.getContext()); controller.attach(view); }
                    else controller.detach(view);
                    return result;
                });
            }
            for (Method method : binder.getDeclaredMethods()) {
                if (method.getName().equals("bind$updateChargingView")) {
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed();
                        controller.sync((View) chain.getArg(1), chain.getArg(2), (View) chain.getArg(0));
                        return result;
                    });
                } else if (method.getName().equals("bind$updateBatteryContentView")
                        || method.getName().equals("bind$updateBatteryIconStyle")) {
                    final int ownerIndex = method.getName().equals("bind$updateBatteryContentView") ? 0 : 1;
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed();
                        controller.sync((View) chain.getArg(ownerIndex));
                        return result;
                    });
                }
                deoptimize(method);
            }
            for (Method method : meter.getDeclaredMethods()) {
                if (method.getName().equals("setBatteryCharge") || method.getName().equals("setBatteryStyleDrawable")) {
                    installHook(HookGroup.BATTERY, method, chain -> {
                        Object result = chain.proceed(); controller.sync((View) chain.getThisObject()); return result;
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
                if (!event.getName().matches("bindViews|updatePlatformBlurDrawable(?:\\$default)?|onConfigChanged")) continue;
                deoptimize(event);
                installHook(HookGroup.NOTIFICATION_CLEAR, event, chain -> {
                    Object result = chain.proceed();
                    Object owner = chain.getThisObject();
                    if (!controller.isInstance(owner) && event.getParameterTypes().length > 0
                            && controller.isAssignableFrom(event.getParameterTypes()[0])) owner = chain.getArg(0);
                    CLEAR_APPEARANCE.bindController(owner);
                    return result;
                });
                installed++;
            }
            return installed > 0;
        } catch (Throwable unavailable) {
            moduleLog(Log.WARN, TAG, "Optional native notification clear appearance hooks unavailable", unavailable);
            return false;
        }
    }

    private boolean installQsAppearance(ClassLoader loader) {
        boolean found=false;
        try {
            for(String name:new String[]{"com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable",
                    "com.oplus.systemui.qs.base.res.drawable.StateListTileDrawable",
                    "com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable"}) try {
                Class<?> type=loader.loadClass(name);Method draw=type.getDeclaredMethod("draw",Canvas.class);deoptimize(draw);
                installHook(HookGroup.QS_APPEARANCE,draw,chain->{
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
                    if(glass)CLEAR_APPEARANCE.drawGlassContent(drawable,canvas,
                            target->QS_APPEARANCE.drawGlassContent(drawable,target,
                                    mediaCanvas->QS_MEDIA.drawGlassContent(drawable,mediaCanvas,nativeCanvas->chain.proceed(new Object[]{nativeCanvas}))));
                    else CLEAR_APPEARANCE.drawBlur(drawable,canvas,
                            target->QS_APPEARANCE.drawBlur(drawable,target,
                                    mediaCanvas->QS_MEDIA.drawBlur(drawable,mediaCanvas,nativeCanvas->chain.proceed(new Object[]{nativeCanvas}))));
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
                        if(detach)QS_APPEARANCE.detach(owner);else QS_APPEARANCE.refreshTile(owner);return result;
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
                        if(detach)QS_APPEARANCE.detach(owner);else QS_APPEARANCE.refreshDeviceCard(owner);
                        return result;
                    });
                }
            }catch(ClassNotFoundException unsupported) { }
            moduleLog(Log.INFO,TAG,found?"QS background and active track appearance hooks installed":"QS appearance unavailable; preserving native backgrounds");
            return true;
        } catch(Throwable error) {moduleLog(Log.ERROR,TAG,"Could not install QS background appearance",error);return false;}
    }

    private boolean installQsMediaAppearance(ClassLoader loader) {
        try {
            Class<?> media=loader.loadClass("com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView");
            for(Method event:media.getDeclaredMethods()) {
                String name=event.getName();boolean detach=name.equals("onDetachedFromWindow");
                if(!detach&&!name.matches("onFinishInflate|onAttachedToWindow|bindCoverImg|resetCoverImg|bindMediaDataInner|onDrawableUpdate|onConfigurationChanged|updateColor|onThemeApplied|resetView|setCoverImg"))continue;
                deoptimize(event);
                installHook(HookGroup.QS_MEDIA,event,chain->{Object result=chain.proceed();View owner=(View)chain.getThisObject();
                    if(detach)QS_MEDIA.detach(owner);else QS_MEDIA.refresh(owner);return result;
                });
            }
            Method cover=ImageView.class.getDeclaredMethod("setImageDrawable",Drawable.class);deoptimize(cover);
            installHook(HookGroup.QS_MEDIA,cover,chain->{Object result=chain.proceed();QS_MEDIA.onCoverChanged((View)chain.getThisObject());return result;});
            Method child=ViewGroup.class.getDeclaredMethod("drawChild",Canvas.class,View.class,Long.TYPE);deoptimize(child);
            installHook(HookGroup.QS_MEDIA,child,chain->QS_MEDIA.drawCover((View)chain.getThisObject(),(View)chain.getArg(1),(Canvas)chain.getArg(0),
                    canvas->(Boolean)chain.proceed(new Object[]{canvas,chain.getArg(1),chain.getArg(2)})));
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
            Method setText = TextView.class.getDeclaredMethod("setText", CharSequence.class, TextView.BufferType.class);
            installHook(HookGroup.TEXT, setText, chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                Object result = chain.proceed(new Object[]{TEXT.nativeText(view, (CharSequence) chain.getArg(0)), chain.getArg(1)});
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
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                return chain.proceed(new Object[]{TEXT.nativeColor(view, (Integer) chain.getArg(0))});
            });
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setTextColor", ColorStateList.class), chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                ColorStateList state = (ColorStateList) chain.getArg(0);
                if (state == null) return chain.proceed();
                int nativeColor = state.getColorForState(view.getDrawableState(), state.getDefaultColor());
                int styledColor = TEXT.nativeColor(view, nativeColor);
                if (!FEATURES.color(TEXT.group(view))) return chain.proceed();
                return chain.proceed(new Object[]{ColorStateList.valueOf(styledColor)});
            });
            final Method sizeMethod = TextView.class.getDeclaredMethod("setTextSize", Integer.TYPE, Float.TYPE);
            installHook(HookGroup.TEXT, sizeMethod, chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                float pixels = TypedValue.applyDimension((Integer) chain.getArg(0), (Float) chain.getArg(1), view.getResources().getDisplayMetrics());
                TEXT.nativeSize(view, pixels);
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
            installHook(HookGroup.TEXT, TextView.class.getDeclaredMethod("setLetterSpacing", Float.TYPE), chain -> {
                TextView view = (TextView) chain.getThisObject();
                if (TEXT.isInternal() || TEXT.kind(view) == TextControls.NONE) return chain.proceed();
                TEXT.nativeSpacing(view, (Float) chain.getArg(0));
                Object result = chain.proceed(); TEXT.beforeMeasure(view); return result;
            });
            installHook(HookGroup.TEXT, View.class.getDeclaredMethod("draw", Canvas.class), chain -> {
                View view = (View) chain.getThisObject();
                if (QsMediaAppearance.isMediaView(view)) {
                    QS_MEDIA.drawMedia(view,(Canvas)chain.getArg(0),canvas->chain.proceed(new Object[]{canvas}));
                    return null;
                }
                if (QsTileAppearance.isDeviceCard(view)) {
                    QS_APPEARANCE.drawDeviceCard(view, (Canvas) chain.getArg(0),
                            canvas -> chain.proceed(new Object[]{canvas}));
                    return null;
                }
                if (QsTileAppearance.type(view, NotificationClearAppearance.BUTTON_CLASS)) {
                    CLEAR_APPEARANCE.drawButton(view, (Canvas) chain.getArg(0),
                            target -> chain.proceed(new Object[]{target}));
                    return null;
                }
                if (TEXT.kind(view) == TextControls.NONE) {
                    Object result = chain.proceed();
                    NotificationNativeClock.drawOwnedBorder(view, (Canvas) chain.getArg(0));
                    return result;
                }
                Canvas canvas = (Canvas) chain.getArg(0); int saved = canvas.save();
                TEXT.beforeDraw((TextView) view, canvas);
                try { return chain.proceed(); } finally { canvas.restoreToCount(saved); }
            });
            for (String name : new String[]{"onVisibilityChanged", "onWindowVisibilityChanged"}) {
                Method method = name.equals("onVisibilityChanged")
                        ? View.class.getDeclaredMethod(name, View.class, Integer.TYPE)
                        : View.class.getDeclaredMethod(name, Integer.TYPE);
                installHook(HookGroup.TEXT, method, chain -> {
                    Object result = chain.proceed();
                    if (TEXT.kind((View) chain.getThisObject()) != TextControls.NONE) TEXT.refresh();
                    return result;
                });
            }
            for (String name : new String[]{"onAttachedToWindow","onDetachedFromWindow"}) {
                final boolean attach=name.equals("onAttachedToWindow");
                installHook(HookGroup.TEXT,View.class.getDeclaredMethod(name),chain->{
                    Object result=chain.proceed();View view=(View)chain.getThisObject();
                    if(QsTileAppearance.isDeviceCard(view)) {
                        if(attach)QS_APPEARANCE.refreshDeviceCard(view);else QS_APPEARANCE.detach(view);
                    }
                    if(QsTileAppearance.type(view, NotificationClearAppearance.BUTTON_CLASS)) {
                        if(attach)CLEAR_APPEARANCE.bind(view);else CLEAR_APPEARANCE.forget(view);
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
                    if(!event.getName().matches("onFinishInflate|onLayout|onConfigurationChanged|onAttachedToWindow"))continue;
                    deoptimize(event);
                    installHook(HookGroup.TEXT,event,chain->{
                        Object result=chain.proceed();View view=(View)chain.getThisObject();
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
                    if (caller.getName().matches("updateClock|onDarkChanged|onConfigurationChanged|setFontTypeface|updateTextColor|updateEverything|reloadDimens|onMeasure|calculateSecondTextMaxWidth|updateMinWidth.*"))
                        deoptimize(caller);
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
                if (name.endsWith("StatClock")) {
                    final Method nativeMeasure = TextView.class.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE);
                    final Field actualWidth = target.getDeclaredField("actualWidth"); actualWidth.setAccessible(true);
                    installHook(HookGroup.TEXT, target.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE), chain -> {
                        if(!FEATURES.enabled("clock")) {
                            Object result=chain.proceed();TEXT.nativeSize((TextView)chain.getThisObject(),((TextView)chain.getThisObject()).getTextSize());return result;
                        }
                        TextView view = (TextView) chain.getThisObject();
                        int dimension = view.getResources().getIdentifier("stat_clock_size", "dimen", "com.android.systemui");
                        // This ROM writes its clock size directly into Paint during the original onMeasure.
                        try { if (dimension != 0) TEXT.nativeSize(view, view.getResources().getDimension(dimension)); }
                        catch (Resources.NotFoundException otherRom) { }
                        TEXT.beforeMeasure(view);
                        Object result = getInvoker(nativeMeasure).invokeSpecial(view, chain.getArg(0), chain.getArg(1));
                        actualWidth.setInt(view, view.getMeasuredWidth());
                        return result;
                    });
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
                installHook(HookGroup.TEXT, animated.getDeclaredMethod("setTextSize", Integer.TYPE, Float.TYPE), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (TEXT.kind(view) != TextControls.CARRIER || !FEATURES.enabled(TEXT.group(view))) return chain.proceed();
                    boolean internal = TEXT.isInternal();
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
                    if (!TEXT.replaces(view)) return chain.proceed();
                    view.setText((CharSequence) chain.getArg(0)); TEXT.attach(view); return null;
                });
                Class<?> carrier = loader.loadClass("com.oplus.systemui.qs.widget.OplusSecondCarrierText");
                Field maxSize = carrier.getDeclaredField("mMaxTextSizePx"); maxSize.setAccessible(true);
                installHook(HookGroup.TEXT, carrier.getDeclaredMethod("updateTextSize$1"), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (!TEXT.replaces(view)) return chain.proceed();
                    TEXT.nativeSize(view, maxSize.getFloat(view)); TEXT.beforeMeasure(view); return null;
                });
            } catch (ClassNotFoundException absent) { }
            try {
                Class<?> callback=loader.loadClass("com.oplus.systemui.qs.widget.OplusSecondCarrierText$1");
                for(Method method:callback.getDeclaredMethods())if(method.getName().equals("updateCarrierInfo"))deoptimize(method);
            }catch(ClassNotFoundException absent){}
            moduleLog(Log.INFO, TAG, "Status clock and carrier text controls installed");
            return true;
        } catch (Throwable error) {
            moduleLog(Log.WARN, TAG, "Text controls incomplete; retry at next package phase", error); return false;
        }
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
            speedNumberField = speed.getField("mSpeedNumber");
            speedUnitField = speed.getField("mSpeedUnit");
            for(Method caller:speed.getDeclaredMethods())
                if(caller.getName().matches("onDarkChanged|onLayout|setStaticDrawableColor|setFontTypeface|onConfigurationChanged"))deoptimize(caller);
            try {
                installHook(HookGroup.SPEED,speed.getDeclaredMethod("onConfigurationChanged",android.content.res.Configuration.class),chain->{
                    View view=(View)chain.getThisObject();SpeedStyle style=speedStyleFor(view);
                    // Restore the base sizes before the native configuration callback, which may early-return.
                    TextView number=(TextView)speedNumberField.get(view),unit=(TextView)speedUnitField.get(view);
                    if(number!=null&&style.numberSize>0)number.setTextSize(TypedValue.COMPLEX_UNIT_PX,style.numberSize);
                    if(unit!=null&&style.unitSize>0)unit.setTextSize(TypedValue.COMPLEX_UNIT_PX,style.unitSize);
                    Object result=chain.proceed();
                    if(number!=null){style.numberSize=number.getTextSize();style.appliedNumberSize=style.numberSize;}
                    if(unit!=null){style.unitSize=unit.getTextSize();style.appliedUnitSize=style.unitSize;}
                    applySpeedStyle(view,style);return result;
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
                    allowDrawableOverflow(view);
                    applySpeedStyle(view, speedStyleFor(view));
                });
                return result;
            });
            installHook(HookGroup.SPEED,speed.getDeclaredMethod("onDetachedFromWindow"),chain->{
                Object result=chain.proceed();releaseOverflow((View)chain.getThisObject());return result;
            });
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("setIconTint", Integer.TYPE), chain -> {
                View view = (View) chain.getThisObject();
                SpeedStyle style = speedStyleFor(view);
                style.nativeTint = (Integer) chain.getArg(0);
                style.tintCaptured = true;
                return chain.proceed(new Object[]{styleColor("speed", style.nativeTint)});
            });
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("dispatchDraw", Canvas.class), chain -> {
                View view = (View) chain.getThisObject();
                SpeedStyle style = speedStyleFor(view);
                applySpeedStyle(view, style);
                Canvas canvas = (Canvas) chain.getArg(0);
                int save = canvas.save();
                float density = view.getResources().getDisplayMetrics().density;
                canvas.translate(NumericPolicy.pixels(speedOffsetXdp,density),NumericPolicy.pixels(speedOffsetYdp,density));
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
            if (style.numberFace == null || number.getTypeface() != style.appliedNumberFace) style.numberFace = number.getTypeface();
            if (style.unitFace == null || unit.getTypeface() != style.appliedUnitFace) style.unitFace = unit.getTypeface();
            boolean typeStyle=FEATURES.textStyle("speed");
            boolean useFont=FEATURES.enabled("speed")&&FEATURES.enabled("font");
            style.appliedNumberFace = typeStyle||useFont ? FontRepository.typeface(style.numberFace,
                    typeStyle?speedWeight:nativeWeight(style.numberFace)) : style.numberFace;
            style.appliedUnitFace = typeStyle||useFont ? FontRepository.typeface(style.unitFace,
                    typeStyle?speedWeight:nativeWeight(style.unitFace)) : style.unitFace;
            if (number.getTypeface() != style.appliedNumberFace) number.setTypeface(style.appliedNumberFace);
            if (unit.getTypeface() != style.appliedUnitFace) unit.setTypeface(style.appliedUnitFace);
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

    /** All labels use the same slot and are centred by their visible glyph bounds. */
    private static final class NetworkLabelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect glyph = new Rect();
        private String label = "";
        private int nativeTint = Color.BLACK;
        private int weight;
        NetworkLabelView(Context context) {
            super(context);
            setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            setWillNotDraw(false);
        }
        private void prepare() {
            weight = FEATURES.textStyle("label")?fontWeight:nativeWeight(TEXT.nativeFamily());
            paint.setTypeface(FEATURES.enabled("font")||FEATURES.textStyle("label")
                    ?FontRepository.typeface(TEXT.nativeFamily(),weight):TEXT.nativeFamily());
            paint.setTextSize(13f * getResources().getDisplayMetrics().density);
            paint.setColor(styleColor("label", nativeTint));
        }
        void update(String label, int nativeTint) {
            boolean changed = !this.label.equals(label);
            this.label = label; this.nativeTint = nativeTint;
            setContentDescription(label.isEmpty() ? "" : "移动数据网络 " + label);
            if (changed) requestLayout();
            invalidate();
        }
        @Override protected void onMeasure(int width, int height) {
            prepare();
            float density = getResources().getDisplayMetrics().density;
            setMeasuredDimension(resolveSize((int) Math.ceil(paint.measureText(label) + 2 * density), width),
                    resolveSize(Math.round(ICON_SLOT_DP * density), height));
        }
        @Override protected void onDraw(Canvas canvas) {
            if (label.isEmpty()) return;
            prepare();
            paint.getTextBounds(label, 0, label.length(), glyph);
            canvas.drawText(label, getWidth() / 2f - glyph.exactCenterX(), getHeight() / 2f - glyph.exactCenterY(), paint);
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
            Object largeLayout = loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.BigTypeStackedMobileIconLayoutStrategy").getField("INSTANCE").get(null);
            installHook(HookGroup.COMPOSE, factory.getDeclaredMethod("getStrategy"), chain -> {
                Object nativeLayout = chain.proceed();
                if(composeRenderingReady)readComposeRevision.invoke(composeStyleRevision);
                return composeRenderingReady && (FEATURES.enabled("data")
                        || FEATURES.enabled("label") && !FEATURES.hideNetworkLabel()) ? largeLayout : nativeLayout;
            });
            Class<?> model = loader.loadClass("com.oplus.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModelImpl");
            installHook(HookGroup.COMPOSE, model.getDeclaredMethod("getActivityIndicatorIconResId"), chain -> {
                Object nativeActivity = chain.proceed();
                if(composeRenderingReady)readComposeRevision.invoke(composeStyleRevision);
                return composeRenderingReady && FEATURES.effective("data","data_activity_hidden") ? 0 : nativeActivity;
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
                installHook(HookGroup.COMPOSE, image, chain -> {
                    if (!composeRenderingReady) return chain.proceed();
                    Object painter = chain.getArg(0);
                    if (!painterClass.isInstance(painter)) return chain.proceed();
                    Object drawable = painterDrawable.invoke(painter);
                    if (!(drawable instanceof ScaledDrawable)) return chain.proceed();
                    ScaledDrawable icon = (ScaledDrawable) drawable;
                    readComposeRevision.invoke(composeStyleRevision);
                    Object[] args = new Object[10];
                    for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
                    Object base = args[2] == null ? bridge.empty : args[2];
                    Object filter = args[6];
                    if (filter != null && nativeFilters.containsKey(filter)) filter = nativeFilters.get(filter);
                    int tint = filter == null ? icon.nativeTint
                            : nativeFilterColor((ColorFilter) bridge.nativeFilter.invoke(filter), icon.nativeTint);
                    Object original = bases.get(base);
                    if (original != null) base = original;
                    String item=icon.wifiIcon?"wifi":"data";
                    Object frame = FEATURES.enabled(item)?bridge.height.invoke(null, base, ICON_SLOT_DP):base;
                    float d = context == null ? icon.density : context.getResources().getDisplayMetrics().density;
                    args[2] = !FEATURES.enabled(item)?base:bridge.place(frame,NumericPolicy.pixels(icon.wifiIcon ? wifiOffsetXdp : dataOffsetXdp,d),
                            NumericPolicy.pixels(icon.wifiIcon ? wifiOffsetYdp : dataOffsetYdp,d),
                            NumericPolicy.scale((icon.wifiIcon ? wifiIconScalePercent : dataIconScalePercent) / 100f,ICON_SLOT_DP*d));
                    if(args[2]!=base)bases.put(args[2], base);
                    args[6] = FEATURES.color(item)?bridge.tintFilter.invoke(bridge.filterCompanion,
                            bridge.color.invoke(null, styleColor(item, tint)), 5):filter;
                    if(args[6]!=null&&args[6]!=filter)nativeFilters.put(args[6], filter);
                    args[8] = 0; args[9] = ((Integer) args[9]) & ~(4 | 64);
                    return chain.proceed(args);
                });
                imageInstalled = true;
            }
            if (!imageInstalled) throw new NoSuchMethodException("Compose Image renderer");
            Class<?> textModel = loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel");
            Method type = textModel.getMethod("getNetworkTypeText");
            Method name = loader.loadClass(NETWORK_TYPE).getMethod("getName");
            boolean labelInstalled = false;
            for (Method text : loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt").getDeclaredMethods()) {
                if (!text.getName().startsWith("NetworkTypeText-") || text.getParameterCount() != 13) continue;
                installHook(HookGroup.COMPOSE, text, chain -> {
                    if (!composeRenderingReady) return chain.proceed();
                    readComposeRevision.invoke(composeStyleRevision);
                    if (!FEATURES.enabled("label")) return chain.proceed();
                    int nativeTint = (Integer) bridge.argb.invoke(null, chain.getArg(2));
                    String rawLabel = "";
                    if (!wifiConnected && !airplaneMode && chain.getArg(1) != null) {
                        Object networkType = type.invoke(chain.getArg(1));
                        if (networkType != null) {
                            rawLabel=(String)name.invoke(networkType);
                        }
                    }
                    final String shown = FEATURES.hideNetworkLabel() ? "" : RadioState.cellularLabel(FEATURES.enabled("label"),wifiConnected,airplaneMode,
                            rawLabel,networkLabel,FEATURES.effective("label","label_normalize_enabled"));
                    Object base = shown.isEmpty() || chain.getArg(3) == null ? bridge.empty : chain.getArg(3);
                    float d = context == null ? 1f : context.getResources().getDisplayMetrics().density;
                    Object sized = shown.isEmpty() ? bridge.width.invoke(null, base, 0f)
                            : bridge.widthIn.invoke(null, base, NumericPolicy.layoutDp(slotWidthDp,d),
                                    Math.max(NumericPolicy.layoutDp(slotWidthDp,d),NumericPolicy.MAX_LAYOUT_PIXELS/(d>0&&Float.isFinite(d)?d:1f)));
                    Object frame = bridge.height.invoke(null, sized, ICON_SLOT_DP);
                    Object placed = bridge.place(frame,NumericPolicy.pixels(labelOffsetXdp,d),NumericPolicy.pixels(labelOffsetYdp,d),
                            NumericPolicy.scale(labelScalePercent / 100f,Math.max(ICON_SLOT_DP*d,NumericPolicy.layoutPixels(slotWidthDp,d))));
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
            if (!labelInstalled) throw new NoSuchMethodException("Compose network label");
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
            moduleLog(Log.INFO,TAG,"Independent network badge controls and vendor Wi-Fi activity guard installed");
            return true;
        }catch(Throwable error){moduleLog(Log.ERROR,TAG,"Could not bind network badge controls",error);return false;}
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
            moduleLog(Log.INFO, TAG, "Subscription-aware network label hooks installed");
            return true;
        } catch (Throwable th) {
            moduleLog(Log.ERROR, TAG, "Could not bind the native network type model", th);
            return false;
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
        if (!composeStatusBar) slotFor(view);
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
        if(group.equals("cellular"))return needsOverflow("data")||needsOverflow("label");
        return FEATURES.position(group)||FEATURES.size(group)
                ||group.equals("speed")&&FEATURES.effective("speed","speed_lines_enabled")&&speedLineGapDp!=0;
    }
    private static void updateOverflow(View owner,String group) {
        if(needsOverflow(group))OverflowControls.shared().acquire(owner,group.equals("cellular")||group.equals("wifi")||group.equals("speed"));
        else OverflowControls.shared().release(owner);
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
        if (isWifi(chain.getThisObject()) && cellularMode()) {
            return true;
        }
        return chain.proceed();
    }

    static /* synthetic */ Object lambda$installWifiSlot$12(XposedInterface.Chain chain) throws Throwable {
        if (composeStatusBar || !isWifi(chain.getThisObject())) {
            return chain.proceed();
        }
        Slot slotSlotFor = slotFor((View) chain.getThisObject());
        if (!slotSlotFor.settingVisibility) {
            slotSlotFor.requestedVisibility = ((Integer) chain.getArg(0)).intValue();
        }
        if (cellularMode() && ((Integer) chain.getArg(0)).intValue() == 8) {
            return chain.proceed(new Object[]{0});
        }
        return chain.proceed();
    }

    static /* synthetic */ Object lambda$installWifiSlot$13(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        if (!composeStatusBar && isWifi(chain.getThisObject())) {
            View view = (View) chain.getThisObject();
            slotFor(view).visibleState = ((Integer) chain.getArg(0)).intValue();
            view.invalidate();
        }
        return objProceed;
    }

    static /* synthetic */ Object lambda$installWifiSlot$14(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        if (!composeStatusBar && isWifi(chain.getThisObject())) {
            slotFor((View) chain.getThisObject()).appearAmount = ((Float) chain.getArg(0)).floatValue();
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
                    if(nativeWifiIconClass.isInstance(chain.getArg(0)))refreshRadio();return result;
                });
            }
            for(Method method:loader.loadClass("com.android.systemui.statusbar.pipeline.wifi.ui.binder.WifiViewBinder$bind$1$1$2").getDeclaredMethods())
                if(method.getName().equals("invokeSuspend"))deoptimize(method);
        }catch(ClassNotFoundException otherVersion){moduleLog(Log.INFO,TAG,"Wi-Fi flow collector differs; connectivity callbacks remain active");}
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
                    if(icon!=null&&nativeWifiIconClass.isInstance(icon))return nativeWifiVisibleClass.isInstance(icon);
                }catch(Exception unreadable){}
            }
        }
        return null;
    }

    private static void refreshRadio() {
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(StatusBarModule::refreshRadio);return;}
        updateRadioState();scheduleRefresh();
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

    private static boolean cellularMode() {
        return FEATURES.enabled("label") && !FEATURES.hideNetworkLabel() && !composeStatusBar
                && !wifiConnected && !airplaneMode && !networkLabel.isEmpty();
    }

    private static void initializeContext(Context context2) {
        if (context == null) {
            Context applicationContext = context2.getApplicationContext();
            if (applicationContext != null) {
                context2 = applicationContext;
            }
            context = context2;
        }
        if (frameworkLogger != null) try {
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
        if (!settingsObserverRegistered) {
            registerSettingsObserver();
        }
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
        intentFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        for (String action : new String[]{Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF})
            intentFilter.addAction(action);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { // from class: dev.puitheme.StatusBarModule.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context3, Intent intent) {
                if (intent != null) {
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        TEXT.setInteractive(false); if (BATTERY != null) BATTERY.setInteractive(false); return;
                    }
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        TEXT.setInteractive(true); if (BATTERY != null) BATTERY.setInteractive(true); return;
                    }
                    if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                            || Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                        BIG_CLOCK.onTimeChanged();
                        TEXT.refresh(); return;
                    }
                }
                if (intent != null && Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                    StatusBarModule.readStyleSettings();
                    if (!settingsObserverRegistered) {
                        StatusBarModule.registerSettingsObserver();
                    }
                    StatusBarModule.invalidateLiveDrawables();
                    StatusBarModule.refreshLabel();
                    logFrameworkStage(Log.INFO, "User unlocked; visual settings reapplied");
                }
                StatusBarModule.updateRadioState();
                StatusBarModule.scheduleRefresh();
            }
        };
        boolean registered = false;
        try {
            Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, Integer.TYPE).invoke(context, broadcastReceiver, intentFilter, 2);
            registered = true;
        } catch (Throwable th2) {
            try {
                context.registerReceiver(broadcastReceiver, intentFilter);
                registered = true;
            } catch (Throwable th3) {
                logFrameworkStage(Log.WARN, "Could not register radio state receiver", th3);
            }
        }
        receiverRegistered = registered;
        updateRadioState();
        registerNetworkObserver();
        scheduleRefresh();
    }

    private static void registerSettingsObserver() {
        if (context == null) {
            return;
        }
        Uri uri = Uri.parse(StatusBarSettings.CONTENT_URI);
        readStyleSettings();
        try {
            context.getContentResolver().registerContentObserver(uri, false, new ContentObserver(MAIN) { // from class: dev.puitheme.StatusBarModule.2
                @Override // android.database.ContentObserver
                public void onChange(boolean z, Uri uri2) {
                    if(StatusBarModule.readStyleSettings(false)) {
                        StatusBarModule.invalidateLiveDrawables();
                        StatusBarModule.refreshLabel();
                    }
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
        readStyleSettings(true);
    }

    private static boolean readStyleSettings(boolean force) {
        if (context == null) {
            return false;
        }
        Bundle previous=SettingsSnapshot.lastApplied(context);
        try {
            Bundle bundleCall = SettingsSnapshot.read(context);
            if (bundleCall == null) {
                ModuleDiagnostics.info("settings", "Settings provider returned no data; retaining current configuration");
                return false;
            }
            if(!force&&SettingsSnapshot.matchesApplied(context,bundleCall))return false;
            applyStyleSettings(bundleCall);
            SettingsSnapshot.rememberApplied(context,bundleCall);
            return true;
        } catch (Throwable failed) {
            boolean restored=false;
            if(previous!=null)try { applyStyleSettings(previous);restored=true; }
            catch(Throwable rollbackFailed) { ModuleDiagnostics.error("settings","Could not restore previous runtime configuration",rollbackFailed); }
            ModuleDiagnostics.error("settings",restored?"Settings application failed; previous configuration restored"
                    :"Settings application incomplete; no successful configuration could be restored",failed);
            Log.w(TAG,"Could not apply visual settings",failed);
            return restored;
        }
    }

    private static void applyStyleSettings(Bundle bundleCall) {
            ModuleDiagnostics.configure(context, bundleCall);
            FEATURES=FeatureOptions.from(bundleCall);
            TILE_EFFECTS.configure(bundleCall);
            QS_APPEARANCE.configure(bundleCall);
            QS_MEDIA.configure(bundleCall);
            CLEAR_APPEARANCE.configure(bundleCall);
            BIG_CLOCK.configure(bundleCall);
            bigClockEnabled = bundleCall.getBoolean(NotificationBigClockSettings.MASTER, false);
            NativeBackdropMotion backdrop = notificationBackdropMotion;
            if (!bigClockEnabled && backdrop != null) MAIN.post(() -> { if (!bigClockEnabled) backdrop.release(); });
            BADGES.configure(FEATURES);
            wifiOffsetXdp = FEATURES.position("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_X, 0):0;
            wifiOffsetYdp = FEATURES.position("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_Y, 0):0;
            wifiIconScalePercent = FEATURES.size("wifi")?settingValue(bundleCall, StatusBarSettings.WIFI_ICON_SCALE, 100):100;
            dataOffsetXdp = FEATURES.position("data")?settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_X, 0):0;
            dataOffsetYdp = FEATURES.position("data")?settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_Y, 0):0;
            dataIconScalePercent = FEATURES.size("data")?settingValue(bundleCall, StatusBarSettings.DATA_ICON_SCALE, 100):100;
            labelOffsetXdp = FEATURES.position("label")?settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_X, 0):0;
            labelOffsetYdp = FEATURES.position("label")?settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_Y, 0):0;
            labelScalePercent = FEATURES.size("label")?settingValue(bundleCall, StatusBarSettings.LABEL_SCALE, 100):100;
            slotWidthDp = FEATURES.size("label")?settingValue(bundleCall, StatusBarSettings.SLOT_WIDTH, 22):22;
            fontWeight = Math.round(settingValue(bundleCall, StatusBarSettings.FONT_WEIGHT, 600));
            speedOffsetXdp = FEATURES.position("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_X, 0):0;
            speedOffsetYdp = FEATURES.position("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_Y, 0):0;
            speedScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_SCALE, 100):100;
            speedNumberScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_NUMBER_SCALE, 100):100;
            speedUnitScalePercent = FEATURES.size("speed")?settingValue(bundleCall, StatusBarSettings.SPEED_UNIT_SCALE, 100):100;
            speedLineGapDp = FEATURES.effective("speed","speed_lines_enabled")?settingValue(bundleCall, StatusBarSettings.SPEED_LINE_GAP, 0):0;
            speedWeight = Math.round(settingValue(bundleCall, StatusBarSettings.SPEED_WEIGHT, 600));
            singleSignal = FEATURES.effective("data","data_single_enabled") && FEATURES.isEnabled("data_icon_enabled") && "single".equals(bundleCall.getString(StatusBarSettings.SIGNAL_LAYOUT, "system"));
            FontRepository.configure(context, FEATURES.enabled("font")?bundleCall.getString(StatusBarSettings.FONT_MODE, "system"):"system",
                    bundleCall.getString(StatusBarSettings.FONT_REVISION, ""));
            Map<String, Integer> colors = new HashMap<>();
            Map<String, Boolean> alphaModes = new HashMap<>();
            for (Map.Entry<String, Integer> color : StatusBarSettings.COLOR_DEFAULTS.entrySet()) {
                colors.put(color.getKey(), bundleCall.getInt(color.getKey(), color.getValue()));
                alphaModes.put(color.getKey(), bundleCall.getBoolean(StatusBarSettings.alphaKey(color.getKey()), false));
            }
            styleColors = colors;
            customColorAlpha = alphaModes;
            TEXT.configure(bundleCall, colors, alphaModes);
            if (BATTERY != null) BATTERY.configure(bundleCall,colors,alphaModes);
            ModuleDiagnostics.info("settings", "Visual settings applied; Android SDK " + android.os.Build.VERSION.SDK_INT
                    + "; active hooks " + COMPLETED_HOOK_GROUPS);
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

    /* JADX INFO: Access modifiers changed from: private */
    public static void updateRadioState() {
        if (context == null) {
            return;
        }
        boolean previousWifi = wifiConnected;
        boolean previousAirplane = airplaneMode;
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
            boolean wifiEnabled=wifiManager!=null&&wifiManager.isWifiEnabled();
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active=manager==null?null:manager.getActiveNetwork();
            NetworkCapabilities capabilities=active==null?null:manager.getNetworkCapabilities(active);
            boolean defaultWifi=capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            wifiConnected=RadioState.wifiInUse(wifiEnabled,nativeWifiVisible(),defaultWifi);
            airplaneMode = ((Integer) Class.forName("android.provider.Settings$Global").getMethod("getInt", ContentResolver.class, String.class, Integer.TYPE).invoke(null, context.getContentResolver(), "airplane_mode_on", 0)).intValue() != 0;
        } catch (Throwable th) {
            ModuleDiagnostics.error("radio","Could not refresh native radio state; preserving last known state",th);
            Log.w(TAG, "Could not refresh radio state", th);
        }
        if (previousWifi != wifiConnected || previousAirplane != airplaneMode) {
            logFrameworkStage(Log.INFO,"Radio changed: wifiVisible="+wifiConnected+" airplane="+airplaneMode);
            invalidateComposeStyle();
        }
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
                        strNormalize=FEATURES.effective("label","label_normalize_enabled")?NetworkLabel.normalize(raw):(raw==null?"":raw);
                    }
                }
            }
        } catch (Throwable th) {
            strNormalize = "";
        }
        int activeSub=activeDataSubscription();
        observeDataSubscription(activeSub);
        if(strNormalize.isEmpty()&&!airplaneMode&&cellularDataAvailable()) {
            String raw=NativeDataSource.label(activeSub);
            strNormalize=FEATURES.effective("label","label_normalize_enabled")?NetworkLabel.normalize(raw):raw;
        }
        if (strNormalize.isEmpty() && !airplaneMode) strNormalize=readTelephonyNetworkLabel();
        if (!networkLabel.equals(strNormalize)) {
            networkLabel = strNormalize;
            logFrameworkStage(Log.INFO,"Active data label updated: "+NetworkLabel.normalize(strNormalize));
            invalidateComposeStyle();
        }
        // Compose owns its icon widths and visibility; legacy slots must not remeasure them.
        if (composeStatusBar) return;
        synchronized (SLOTS) {
            try {
                for (Map.Entry<View, Slot> entry : SLOTS.entrySet()) {
                    View key = entry.getKey();
                    Slot value = entry.getValue();
                    boolean zCellularMode = cellularMode();
                    boolean z = (value.appliedCellularMode == zCellularMode && strNormalize.equals(value.appliedLabel)) ? false : true;
                    value.appliedCellularMode = zCellularMode;
                    value.appliedLabel = strNormalize;
                    int iRound = zCellularMode ? NumericPolicy.layoutPixels(((double)slotWidthDp+4)*key.getResources().getDisplayMetrics().density) : value.originalMinimumWidth;
                    boolean z2 = key.getMinimumWidth() != iRound;
                    if (z2) {
                        key.setMinimumWidth(iRound);
                    }
                    int i = (zCellularMode && value.requestedVisibility == 8) ? 0 : value.requestedVisibility;
                    if (key.getVisibility() != i) {
                        value.settingVisibility = true;
                        try {
                            key.setVisibility(i);
                            value.settingVisibility = false;
                        } catch (Throwable th2) {
                            value.settingVisibility = false;
                            throw th2;
                        }
                    }
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
            TelephonyCallback.DisplayInfoListener,TelephonyCallback.DataConnectionStateListener,TelephonyCallback.ServiceStateListener {
        final int subscription;
        TelephonyDisplayInfo display;
        DataRadioCallback(int sub){subscription=sub;}
        private boolean current(){return this==dataRadioCallback&&subscription==activeDataSubscription();}
        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
            if(!current())return;display=info;refreshRadio();
        }
        @Override public void onDataConnectionStateChanged(int state,int networkType){if(current())refreshRadio();}
        @Override public void onServiceStateChanged(ServiceState state){if(current())refreshRadio();}
    }

    private static void observeDataSubscription(int subscription) {
        if(subscription==observedDataSub&&observedTelephony!=null)return;
        if(observedTelephony!=null&&dataRadioCallback!=null)try{observedTelephony.unregisterTelephonyCallback(dataRadioCallback);}catch(Exception ignored){}
        observedDataSub=subscription;observedTelephony=null;dataRadioCallback=null;
        if(subscription<0||context==null)return;
        try {
            TelephonyManager manager=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            if(manager==null)return;
            observedTelephony=manager.createForSubscriptionId(subscription);
            if(android.os.Build.VERSION.SDK_INT>=31) {
                DataRadioCallback callback=new DataRadioCallback(subscription);dataRadioCallback=callback;
                observedTelephony.registerTelephonyCallback(MAIN::post,callback);
            }
        }catch(Exception error){logFrameworkStage(Log.WARN,"Active data subscription observer unavailable",error);}
    }

    private static String readTelephonyNetworkLabel() {
        if(!cellularDataAvailable())return "";
        try {
            int type=observedTelephony.getDataNetworkType();
            // Wi-Fi calling is not a cellular data generation.
            if(type==TelephonyManager.NETWORK_TYPE_UNKNOWN||type==TelephonyManager.NETWORK_TYPE_IWLAN)return "";
            TelephonyDisplayInfo info=dataRadioCallback==null?null:dataRadioCallback.display;
            if(info!=null&&(info.getOverrideNetworkType()==TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                    ||info.getOverrideNetworkType()==TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED))return "5G";
            return labelForNetworkType(type);
        }catch(Exception error){logFrameworkStage(Log.WARN,"Could not read current cellular data type",error);return "";}
    }

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

    private static void drawLabel(ViewGroup viewGroup, Slot slot, Canvas canvas) {
        float f = viewGroup.getResources().getDisplayMetrics().density;
        float centerX = viewGroup.getWidth() / 2.0f;
        float centerY = viewGroup.getHeight() / 2.0f;
        float f2 = NumericPolicy.pixels((double)centerX+NumericPolicy.pixels(labelOffsetXdp,f));
        float f3 = NumericPolicy.pixels((double)centerY+NumericPolicy.pixels((double)(labelOffsetYdp - 0.375f)*f));
        try {
            slot.paint.setTypeface(FEATURES.enabled("font")||FEATURES.textStyle("label")
                    ?FontRepository.typeface(TEXT.nativeFamily(),FEATURES.textStyle("label")?fontWeight:nativeWeight(TEXT.nativeFamily())):TEXT.nativeFamily());
            slot.paint.setFakeBoldText(false);
        } catch (Throwable th) {
            slot.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            slot.paint.setFakeBoldText(fontWeight >= 600);
        }
        slot.paint.setTextSize(13.0f * f);
        slot.paint.setTextScaleX(1.0f);
        slot.paint.setColor(styleColor("label", slot.tint));
        slot.paint.getTextBounds(networkLabel, 0, networkLabel.length(), slot.textBounds);
        float glyphScale = NumericPolicy.scale(labelScalePercent / 100f,
                (NETWORK_GLYPH_HEIGHT_DP*f)/Math.max(1,slot.textBounds.height()),Math.max(slot.textBounds.width(),slot.textBounds.height()));
        int iSave = canvas.save();
        canvas.scale(slot.appearAmount, slot.appearAmount, centerX, centerY);
        canvas.translate(f2, f3);
        canvas.scale(glyphScale, glyphScale);
        canvas.drawText(networkLabel, -slot.textBounds.exactCenterX(), -slot.textBounds.exactCenterY(), slot.paint);
        canvas.restoreToCount(iSave);
    }
}
