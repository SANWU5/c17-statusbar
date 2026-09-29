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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
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
    private static final float ICON_SLOT_DP = 20f;
    private static volatile boolean composeStatusBar;
    private static volatile boolean composeRenderingReady;
    private static volatile boolean networkObserverRegistered;
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
    private static volatile boolean singleSignal;
    private static volatile int speedWeight = 600;
    private static Field speedNumberField, speedUnitField;
    private static final Map<View, SpeedStyle> SPEED_VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class SpeedStyle {
        float numberSize, unitSize;
        int nativeTint = Color.BLACK;
        Typeface numberFace, unitFace, appliedNumberFace, appliedUnitFace;
    }
    private static volatile int fontWeight = StatusBarSettings.DEFAULT_FONT_WEIGHT;
    private static volatile String networkLabel = "";
    private static volatile boolean wifiConnected;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final TextControls TEXT = new TextControls(MAIN);
    private static volatile BatteryControls BATTERY;
    private static final Map<View, Slot> SLOTS = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Integer, WeakReference<Object>> MODELS = new HashMap();
    private static final Object REFRESH_LOCK = new Object();
    private static final Object SYSTEMUI_HOOK_LOCK = new Object();
    private enum HookGroup {
        CONTEXT, MODEL, DRAWABLE, BADGE, WIFI, SPEED, COMPOSE, TEXT, BATTERY
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
        private String sourceName, assetName;
        private Resources baseResources;
        private int nativeAlpha = 255;
        private ColorFilter nativeFilter;
        private final float density;
        private final boolean wifiIcon;
        private final int originalWidth;
        private final int originalHeight;
        private boolean composePlacement;
        private int nativeTint = Color.BLACK;
        private int appliedTint;
        private boolean tintApplied;

        ScaledDrawable(Drawable drawable, Resources resources, boolean z) {
            this.delegate = drawable;
            this.density = resources.getDisplayMetrics().density;
            this.wifiIcon = z;
            this.originalWidth = Math.max(1, drawable.getIntrinsicWidth());
            this.originalHeight = Math.max(1, drawable.getIntrinsicHeight());
            this.delegate.setCallback(this);
            onBoundsChange(getBounds());
        }

        void reloadSignal() {
            if (sourceName == null || wifiIcon) return;
            String name = SignalResources.moduleName(sourceName, singleSignal);
            if (name == null || name.equals(assetName)) return;
            Drawable replacement = loadModuleDrawable(name, baseResources);
            if (replacement == null) return;
            delegate.setCallback(null);
            delegate = replacement; assetName = name;
            delegate.setCallback(this);
            delegate.setState(getState()); delegate.setLevel(getLevel());
            delegate.setAlpha(nativeAlpha); delegate.setColorFilter(nativeFilter);
            tintApplied = false; setDelegateBounds(getBounds());
        }

        @Override // android.graphics.drawable.Drawable
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float offsetX = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetXdp : StatusBarModule.dataOffsetXdp);
            float offsetY = this.composePlacement ? 0 : (this.wifiIcon ? StatusBarModule.wifiOffsetYdp : StatusBarModule.dataOffsetYdp);
            float scalePercent = this.composePlacement ? 100 : (this.wifiIcon ? StatusBarModule.wifiIconScalePercent : StatusBarModule.dataIconScalePercent);
            if (!this.composePlacement) {
                int tint = styleColor(this.wifiIcon ? "wifi" : "data", this.nativeTint);
                if (!this.tintApplied || this.appliedTint != tint) {
                    this.tintApplied = true;
                    this.appliedTint = tint;
                    this.delegate.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
                }
            }
            int iSave = canvas.save();
            float scale = scalePercent / 100.0f;
            canvas.translate(centerX + (offsetX * this.density), centerY + (offsetY * this.density));
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
            int left = centerX - (this.originalWidth / 2);
            int top = centerY - (this.originalHeight / 2);
            this.delegate.setBounds(left, top, left + this.originalWidth, top + this.originalHeight);
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
            return this.originalWidth;
        }

        @Override // android.graphics.drawable.Drawable
        public int getIntrinsicHeight() {
            return this.composePlacement ? Math.round(ICON_SLOT_DP * this.density) : this.originalHeight;
        }

        @Override // android.graphics.drawable.Drawable
        public int getMinimumWidth() {
            return this.delegate.getMinimumWidth();
        }

        @Override // android.graphics.drawable.Drawable
        public int getMinimumHeight() {
            return this.delegate.getMinimumHeight();
        }

        @Override // android.graphics.drawable.Drawable
        public int getOpacity() {
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
            this.tintApplied = false;
            invalidateSelf();
        }

        @Override public void setTintList(ColorStateList colors) {
            if (colors != null) this.nativeTint = colors.getColorForState(getState(), colors.getDefaultColor());
            this.tintApplied = false;
            this.delegate.setTintList(colors);
            invalidateSelf();
        }

        @Override // android.graphics.drawable.Drawable
        public void setTintMode(PorterDuff.Mode mode) {
            this.delegate.setTintMode(mode);
            invalidateSelf();
        }

        @Override // android.graphics.drawable.Drawable
        public boolean getPadding(Rect rect) {
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
        log(Log.INFO, TAG, "Module entry reached");
        frameworkLogger = this;
        try {
            log(Log.INFO, TAG, "Module loaded: process=" + param.getProcessName()
                    + " api=" + getApiVersion()
                    + " framework=" + getFrameworkName()
                    + " version=" + getFrameworkVersion());
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Could not read framework details after module entry", error);
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
            log(Log.WARN, TAG, "SystemUI callback had no class loader at " + phase);
            return;
        }
        synchronized (SYSTEMUI_HOOK_LOCK) {
            if (COMPLETED_HOOK_GROUPS.size() == HookGroup.values().length) {
                log(Log.INFO, TAG, "All SystemUI hook groups already installed at " + phase);
                return;
            }
            log(Log.INFO, TAG, "Installing unfinished SystemUI hook groups at " + phase);
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
                int priority = COMPLETED_HOOK_GROUPS.size() == HookGroup.values().length ? Log.INFO : Log.WARN;
                log(priority, TAG, "SystemUI hook groups after " + phase + ": " + COMPLETED_HOOK_GROUPS);
            } catch (Throwable error) {
                log(Log.ERROR, TAG, "SystemUI hook setup failed at " + phase, error);
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
        StatusBarModule logger = frameworkLogger;
        if (logger != null) {
            logger.log(priority, TAG, message);
        } else {
            Log.println(priority, TAG, message);
        }
    }

    private static void logFrameworkStage(int priority, String message, Throwable error) {
        StatusBarModule logger = frameworkLogger;
        if (logger != null) {
            logger.log(priority, TAG, message, error);
        } else {
            Log.println(priority, TAG, message + '\n' + Log.getStackTraceString(error));
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
            log(Log.INFO, TAG, "Native battery adaptation unavailable on this SystemUI; preserving its battery");
            return true;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Could not install native battery content controls", error);
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
                return chain.proceed(new Object[]{ColorStateList.valueOf(TEXT.nativeColor(view, nativeColor))});
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
                    TEXT.nativeTypeface(view, (Typeface) chain.getArg(0));
                    TEXT.enter();
                    Object result;
                    try { result = chain.proceed(); } finally { TEXT.exit(); }
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
                if (TEXT.kind(view) == TextControls.NONE) return chain.proceed();
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
            for (String name : new String[]{"com.oplus.systemui.statusbar.widget.StatClock",
                    "com.android.systemui.statusbar.policy.Clock", "com.oplus.systemui.qs.widget.OplusSecondCarrierText"}) {
                Class<?> target;
                try { target = loader.loadClass(name); } catch (ClassNotFoundException absent) { continue; }
                for (Method caller : target.getDeclaredMethods()) {
                    if (caller.getName().matches("updateClock|onDarkChanged|onConfigurationChanged|setFontTypeface|updateTextColor|updateEverything|reloadDimens"))
                        deoptimize(caller);
                }
                for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
                    try {
                        installHook(HookGroup.TEXT, target.getDeclaredMethod(event), chain -> {
                            Object result = chain.proceed(); TextView view = (TextView) chain.getThisObject();
                            if (event.equals("onAttachedToWindow")) {
                                initializeContext(view.getContext()); allowDrawableOverflow(view); TEXT.attach(view);
                            } else TEXT.detach(view);
                            return result;
                        });
                    } catch (NoSuchMethodException inherited) { }
                }
                if (name.endsWith("StatClock")) {
                    final Method nativeMeasure = TextView.class.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE);
                    final Field actualWidth = target.getDeclaredField("actualWidth"); actualWidth.setAccessible(true);
                    installHook(HookGroup.TEXT, target.getDeclaredMethod("onMeasure", Integer.TYPE, Integer.TYPE), chain -> {
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
            }
            try {
                Class<?> animated = loader.loadClass("com.oplus.systemui.qs.widget.AnimateChangeTextView");
                installHook(HookGroup.TEXT, animated.getDeclaredMethod("setTextSize", Integer.TYPE, Float.TYPE), chain -> {
                    TextView view = (TextView) chain.getThisObject();
                    if (TEXT.kind(view) != TextControls.CARRIER) return chain.proceed();
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
            log(Log.INFO, TAG, "Status clock and carrier text controls installed");
            return true;
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Text controls incomplete; retry at next package phase", error); return false;
        }
    }

    private boolean installSpeedControls(ClassLoader loader) {
        try {
            Class<?> speed = loader.loadClass("com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView");
            speedNumberField = speed.getField("mSpeedNumber");
            speedUnitField = speed.getField("mSpeedUnit");
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
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("setIconTint", Integer.TYPE), chain -> {
                View view = (View) chain.getThisObject();
                SpeedStyle style = speedStyleFor(view);
                style.nativeTint = (Integer) chain.getArg(0);
                return chain.proceed(new Object[]{styleColor("speed", style.nativeTint)});
            });
            installHook(HookGroup.SPEED, speed.getDeclaredMethod("dispatchDraw", Canvas.class), chain -> {
                View view = (View) chain.getThisObject();
                SpeedStyle style = speedStyleFor(view);
                applySpeedStyle(view, style);
                Canvas canvas = (Canvas) chain.getArg(0);
                int save = canvas.save();
                float density = view.getResources().getDisplayMetrics().density;
                canvas.translate(speedOffsetXdp * density, speedOffsetYdp * density);
                float scale = speedScalePercent / 100f;
                canvas.scale(scale, scale, view.getWidth() / 2f, view.getHeight() / 2f);
                try { return chain.proceed(); }
                finally { canvas.restoreToCount(save); }
            });
            log(Log.INFO, TAG, "Live throughput position, typography, and color hooks installed");
            return true;
        } catch (ClassNotFoundException unsupportedSystem) {
            return true;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Could not bind live throughput controls", error);
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
            if (style.numberSize == 0) style.numberSize = number.getTextSize();
            if (style.unitSize == 0) style.unitSize = unit.getTextSize();
            if (style.numberFace == null || number.getTypeface() != style.appliedNumberFace) style.numberFace = number.getTypeface();
            if (style.unitFace == null || unit.getTypeface() != style.appliedUnitFace) style.unitFace = unit.getTypeface();
            style.appliedNumberFace = FontRepository.typeface(style.numberFace, speedWeight);
            style.appliedUnitFace = FontRepository.typeface(style.unitFace, speedWeight);
            if (number.getTypeface() != style.appliedNumberFace) number.setTypeface(style.appliedNumberFace);
            if (unit.getTypeface() != style.appliedUnitFace) unit.setTypeface(style.appliedUnitFace);
            float numberSize = style.numberSize * speedNumberScalePercent / 100f;
            float unitSize = style.unitSize * speedUnitScalePercent / 100f;
            if (Math.abs(number.getTextSize() - numberSize) > .001f) number.setTextSize(TypedValue.COMPLEX_UNIT_PX, numberSize);
            if (Math.abs(unit.getTextSize() - unitSize) > .001f) unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, unitSize);
            float halfGap = speedLineGapDp * view.getResources().getDisplayMetrics().density / 2f;
            number.setTranslationY(-halfGap);
            unit.setTranslationY(halfGap);
            int tint = styleColor("speed", style.nativeTint);
            if (number.getCurrentTextColor() != tint) number.setTextColor(tint);
            if (unit.getCurrentTextColor() != tint) unit.setTextColor(tint);
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipChildren(false);
                ((ViewGroup) view).setClipToPadding(false);
            }
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
            weight = fontWeight;
            paint.setTypeface(FontRepository.typeface(TEXT.nativeFamily(), weight));
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
                return composeRenderingReady ? largeLayout : nativeLayout;
            });
            Class<?> model = loader.loadClass("com.oplus.systemui.statusbar.pipeline.ui.viewmodel.OplusStackedMobileIconViewModelImpl");
            installHook(HookGroup.COMPOSE, model.getDeclaredMethod("getActivityIndicatorIconResId"), chain -> {
                Object nativeActivity = chain.proceed();
                return composeRenderingReady ? 0 : nativeActivity;
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
                    Object frame = bridge.height.invoke(null, base, ICON_SLOT_DP);
                    float d = context == null ? icon.density : context.getResources().getDisplayMetrics().density;
                    args[2] = bridge.place(frame, (icon.wifiIcon ? wifiOffsetXdp : dataOffsetXdp) * d,
                            (icon.wifiIcon ? wifiOffsetYdp : dataOffsetYdp) * d,
                            (icon.wifiIcon ? wifiIconScalePercent : dataIconScalePercent) / 100f);
                    bases.put(args[2], base);
                    args[6] = bridge.tintFilter.invoke(bridge.filterCompanion,
                            bridge.color.invoke(null, styleColor(icon.wifiIcon ? "wifi" : "data", tint)), 5);
                    nativeFilters.put(args[6], filter);
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
                    int nativeTint = (Integer) bridge.argb.invoke(null, chain.getArg(2));
                    String label = "";
                    if (!wifiConnected && !airplaneMode && chain.getArg(1) != null) {
                        Object networkType = type.invoke(chain.getArg(1));
                        if (networkType != null) label = NetworkLabel.normalize((String) name.invoke(networkType));
                    }
                    final String shown = label;
                    Object base = shown.isEmpty() || chain.getArg(3) == null ? bridge.empty : chain.getArg(3);
                    Object sized = shown.isEmpty() ? bridge.width.invoke(null, base, 0f)
                            : bridge.widthIn.invoke(null, base, slotWidthDp, Float.POSITIVE_INFINITY);
                    Object frame = bridge.height.invoke(null, sized, ICON_SLOT_DP);
                    float d = context == null ? 1f : context.getResources().getDisplayMetrics().density;
                    Object placed = bridge.place(frame, labelOffsetXdp * d, labelOffsetYdp * d, labelScalePercent / 100f);
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
            log(Log.INFO, TAG, "Unified Compose rendering installed; legacy label layout is inactive");
            return true;
        } catch (Throwable error) {
            // A Compose system must never start the legacy Wi-Fi label renderer after partial setup.
            composeRenderingReady = false;
            log(Log.ERROR, TAG, "Could not bind unified Compose status bar", error);
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
            log(Log.INFO, TAG, "Early SystemUI application context hook installed");
            return true;
        } catch (Throwable th) {
            log(Log.ERROR, TAG, "Could not bind early SystemUI context initialization", th);
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
            log(Log.INFO, TAG, "SystemUI drawable replacement hooks installed");
            return true;
        } catch (Throwable th) {
            log(Log.ERROR, TAG, "Could not bind SystemUI drawable resources", th);
            return false;
        }
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$1(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue());
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$2(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue());
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$3(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue());
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    static /* synthetic */ Object lambda$installDrawableOverrides$4(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
        Drawable drawableReplacementDrawable = replacementDrawable((Resources) chain.getThisObject(), ((Integer) chain.getArg(0)).intValue());
        return drawableReplacementDrawable == null ? objProceed : drawableReplacementDrawable;
    }

    private static Drawable replacementDrawable(Resources resources, int i) {
        if (i == 0 || moduleResources == null) return null;
        try {
            if (!"com.android.systemui".equals(resources.getResourcePackageName(i))) return null;
            String source = resources.getResourceEntryName(i);
            String asset = SignalResources.moduleName(source, singleSignal);
            if (asset == null) return null;
            Drawable drawable = loadModuleDrawable(asset, resources);
            if (drawable == null) return null;
            ScaledDrawable result = new ScaledDrawable(drawable, resources, source.startsWith("stat_signal_wifi_signal_"));
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

    private boolean installBadgeHiding(ClassLoader classLoader) {
        boolean complete = true;
        try {
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("setVisibility", Integer.TYPE), new XposedInterface.Hooker() { // from class: dev.puitheme.StatusBarModule$$ExternalSyntheticLambda1
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installBadgeHiding$5(chain);
                }
            });
        } catch (Throwable th) {
            complete = false;
            log(Log.ERROR, TAG, "Could not hook badge visibility changes", th);
        }
        try {
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("setId", Integer.TYPE), new XposedInterface.Hooker() {
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installBadgeHiding$6(chain);
                }
            });
        } catch (Throwable th) {
            complete = false;
            log(Log.ERROR, TAG, "Could not hook badge ID assignment", th);
        }
        try {
            installHook(HookGroup.BADGE, View.class.getDeclaredMethod("onAttachedToWindow"), new XposedInterface.Hooker() {
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installBadgeHiding$7(chain);
                }
            });
        } catch (Throwable th) {
            complete = false;
            log(Log.ERROR, TAG, "Could not hook badge attachment", th);
        }
        try {
            installHook(HookGroup.BADGE, LayoutInflater.class.getDeclaredMethod("inflate", Integer.TYPE, ViewGroup.class, Boolean.TYPE), new XposedInterface.Hooker() {
                public final Object intercept(XposedInterface.Chain chain) throws Throwable {
                    return StatusBarModule.lambda$installBadgeHiding$8(chain);
                }
            });
        } catch (Throwable th) {
            complete = false;
            log(Log.ERROR, TAG, "Could not hook status-bar layout inflation", th);
        }
        if (complete) {
            log(Log.INFO, TAG, "Native network badges will be hidden on ID assignment, visibility changes, attachment, and layout inflation");
        }
        return complete;
    }

    static /* synthetic */ Object lambda$installBadgeHiding$5(XposedInterface.Chain chain) throws Throwable {
        return isSuppressedBadge((View) chain.getThisObject()) ? chain.proceed(new Object[]{8}) : chain.proceed();
    }

    static /* synthetic */ Object lambda$installBadgeHiding$6(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        hideSuppressedBadge((View) chain.getThisObject());
        return result;
    }

    static /* synthetic */ Object lambda$installBadgeHiding$7(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        hideSuppressedBadge((View) chain.getThisObject());
        return result;
    }

    static /* synthetic */ Object lambda$installBadgeHiding$8(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        hideSuppressedBadgeTree((View) result);
        return result;
    }

    private static void hideSuppressedBadgeTree(View view) {
        if (view == null) {
            return;
        }
        hideSuppressedBadge(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideSuppressedBadgeTree(group.getChildAt(i));
            }
        }
    }

    private static void hideSuppressedBadge(View view) {
        if (view != null && isSuppressedBadge(view) && view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }


    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:8:0x001c  */
    private static boolean isSuppressedBadge(View view) {
        int id = view.getId();
        if (id == -1 || id == 0) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(id);
            if (name.startsWith("mobile_type")) {
                return true;
            }
            switch (name) {
                case "wifi_left":
                case "wifi_left_tv":
                case "wifi_in":
                case "wifi_out":
                case "wifi_inout":
                case "mobile_in":
                case "mobile_out":
                case "mobile_inout":
                case "data_inout":
                case "data_inout_alone":
                    return true;
                case "network_speed":
                    // Keep the native throughput text; only suppress its activity arrows.
                    return false;
                default:
                    return false;
            }
        } catch (Throwable th) {
            return false;
        }
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
            log(Log.INFO, TAG, "Subscription-aware network label hooks installed");
            return true;
        } catch (Throwable th) {
            log(Log.ERROR, TAG, "Could not bind the native network type model", th);
            return false;
        }
    }

    static /* synthetic */ Object lambda$installNetworkModelHooks$6(XposedInterface.Chain chain) throws Throwable {
        Object objProceed = chain.proceed();
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
            log(Log.INFO, TAG, "Wi-Fi slot hooks installed with native visibility and tint");
            return true;
        } catch (Throwable th) {
            log(Log.ERROR, TAG, "Could not bind the Wi-Fi slot", th);
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
        unclipNetworkTree(view,0);
        View current = view;
        int depth = 0;
        while (current != null && depth < 12) {
            if (!(current instanceof ViewGroup)) {
                current = current.getParent() instanceof View ? (View) current.getParent() : null;
                depth++;
                continue;
            }
            ViewGroup group = (ViewGroup) current;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            current = group.getParent() instanceof View ? (View) group.getParent() : null;
            depth++;
        }
    }

    private static boolean isNetworkSlot(String slot) {
        return slot!=null&&(slot.equals("wifi")||slot.startsWith("wifi_")||slot.startsWith("mobile")
                ||slot.equals("stacked_mobile")||slot.startsWith("stacked_mobile_"));
    }

    /** ComposeView and its Android owner each have their own native clipping boundary. */
    private static void unclipNetworkTree(View view,int depth) {
        if(view==null||depth>8)return;
        view.setClipBounds(null);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;group.setClipChildren(false);group.setClipToPadding(false);
            for(int i=0;i<group.getChildCount();i++)unclipNetworkTree(group.getChildAt(i),depth+1);
        }
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
        return !composeStatusBar && !wifiConnected && !airplaneMode && !networkLabel.isEmpty();
    }

    private static void initializeContext(Context context2) {
        if (context == null) {
            Context applicationContext = context2.getApplicationContext();
            if (applicationContext != null) {
                context2 = applicationContext;
            }
            context = context2;
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
        try {
            Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, Integer.TYPE).invoke(context, broadcastReceiver, intentFilter, 2);
        } catch (Throwable th2) {
            try {
                context.registerReceiver(broadcastReceiver, intentFilter);
            } catch (Throwable th3) {
                logFrameworkStage(Log.WARN, "Could not register radio state receiver", th3);
            }
        }
        receiverRegistered = true;
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
                    StatusBarModule.readStyleSettings();
                    StatusBarModule.invalidateLiveDrawables();
                    StatusBarModule.refreshLabel();
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
        if (context == null) {
            return;
        }
        try {
            Bundle bundleCall = context.getContentResolver().call(Uri.parse(StatusBarSettings.CONTENT_URI), "read_statusbar_settings", (String) null, (Bundle) null);
            if (bundleCall == null) {
                return;
            }
            wifiOffsetXdp = clamp(settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_X, 0), -80f, 80f);
            wifiOffsetYdp = clamp(settingValue(bundleCall, StatusBarSettings.WIFI_OFFSET_Y, 0), -24f, 24f);
            wifiIconScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.WIFI_ICON_SCALE, 100), 25f, 250f);
            dataOffsetXdp = clamp(settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_X, 0), -80f, 80f);
            dataOffsetYdp = clamp(settingValue(bundleCall, StatusBarSettings.DATA_OFFSET_Y, 0), -24f, 24f);
            dataIconScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.DATA_ICON_SCALE, 100), 25f, 250f);
            labelOffsetXdp = clamp(settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_X, 0), -80f, 80f);
            labelOffsetYdp = clamp(settingValue(bundleCall, StatusBarSettings.LABEL_OFFSET_Y, 0), -24f, 24f);
            labelScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.LABEL_SCALE, 100), 25f, 250f);
            slotWidthDp = clamp(settingValue(bundleCall, StatusBarSettings.SLOT_WIDTH, 22), 0f, 80f);
            fontWeight = Math.round(clamp(settingValue(bundleCall, StatusBarSettings.FONT_WEIGHT, 600), 100f, 900f));
            speedOffsetXdp = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_X, 0), -80f, 80f);
            speedOffsetYdp = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_OFFSET_Y, 0), -24f, 24f);
            speedScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_SCALE, 100), 25f, 250f);
            speedNumberScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_NUMBER_SCALE, 100), 25f, 250f);
            speedUnitScalePercent = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_UNIT_SCALE, 100), 25f, 250f);
            speedLineGapDp = clamp(settingValue(bundleCall, StatusBarSettings.SPEED_LINE_GAP, 0), -8f, 8f);
            speedWeight = Math.round(clamp(settingValue(bundleCall, StatusBarSettings.SPEED_WEIGHT, 600), 100f, 900f));
            singleSignal = "single".equals(bundleCall.getString(StatusBarSettings.SIGNAL_LAYOUT, "system"));
            FontRepository.configure(context, bundleCall.getString(StatusBarSettings.FONT_MODE, "system"),
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
        } catch (Throwable th) {
            Log.w(TAG, "Could not read visual settings", th);
        }
    }

    private static int clamp(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }

    private static float settingValue(Bundle settings, String key, float fallback) {
        Object value = settings.get(key);
        if (value instanceof Number) {
            float number = ((Number) value).floatValue();
            if (!Float.isNaN(number) && !Float.isInfinite(number)) return number;
        }
        return fallback;
    }

    private static int styleColor(String item, int nativeTint) {
        return IconAppearance.color(item, nativeTint, styleColors, customColorAlpha);
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
            boolean connected = false;
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (manager != null) {
                    for (Network network : manager.getAllNetworks()) {
                        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            connected = true;
                            break;
                        }
                    }
                }
            }
            wifiConnected = connected;
            airplaneMode = ((Integer) Class.forName("android.provider.Settings$Global").getMethod("getInt", ContentResolver.class, String.class, Integer.TYPE).invoke(null, context.getContentResolver(), "airplane_mode_on", 0)).intValue() != 0;
        } catch (Throwable th) {
            Log.w(TAG, "Could not refresh radio state", th);
        }
        if (previousWifi != wifiConnected || previousAirplane != airplaneMode) {
            invalidateComposeStyle();
        }
    }

    private static void registerNetworkObserver() {
        if (networkObserverRegistered || context == null) return;
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return;
            manager.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                    new ConnectivityManager.NetworkCallback() {
                        private void refresh() { MAIN.post(() -> { updateRadioState(); scheduleRefresh(); }); }
                        @Override public void onAvailable(Network network) { refresh(); }
                        @Override public void onLost(Network network) { refresh(); }
                        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) { refresh(); }
                    });
            networkObserverRegistered = true;
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
                        strNormalize = objInvoke3 == null ? "" : NetworkLabel.normalize((String) getTypeName.invoke(objInvoke3, new Object[0]));
                    }
                }
            }
        } catch (Throwable th) {
            strNormalize = "";
        }
        if (strNormalize.isEmpty() && !airplaneMode) {
            strNormalize = readTelephonyNetworkLabel();
        }
        if (!networkLabel.equals(strNormalize)) {
            networkLabel = strNormalize;
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
                    float effectiveSlotWidthDp = slotWidthDp + 4;
                    int iRound = zCellularMode ? Math.round(effectiveSlotWidthDp * key.getResources().getDisplayMetrics().density) : value.originalMinimumWidth;
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

    private static String readTelephonyNetworkLabel() {
        Context currentContext = context;
        if (currentContext == null) {
            return "";
        }
        try {
            TelephonyManager telephony = (TelephonyManager) currentContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephony == null) {
                return "";
            }
            String label = labelForNetworkType(telephony.getDataNetworkType());
            if (label.isEmpty()) {
                label = labelForNetworkType(telephony.getVoiceNetworkType());
            }
            return label;
        } catch (Throwable th) {
            Log.w(TAG, "Could not read the fallback cellular network type", th);
            return "";
        }
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
            case TelephonyManager.NETWORK_TYPE_IWLAN:
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
        float f2 = centerX + (labelOffsetXdp * f);
        float f3 = centerY + ((labelOffsetYdp - 0.375f) * f);
        try {
            slot.paint.setTypeface(FontRepository.typeface(TEXT.nativeFamily(), fontWeight));
            slot.paint.setFakeBoldText(false);
        } catch (Throwable th) {
            slot.paint.setTypeface(Typeface.create("sans-serif-medium", 0));
            slot.paint.setFakeBoldText(fontWeight >= 600);
        }
        slot.paint.setTextSize(13.0f * f);
        slot.paint.setTextScaleX(1.0f);
        slot.paint.setColor(styleColor("label", slot.tint));
        slot.paint.getTextBounds(networkLabel, 0, networkLabel.length(), slot.textBounds);
        float glyphScale = ((NETWORK_GLYPH_HEIGHT_DP * f) * labelScalePercent / 100f)
                / Math.max(1, slot.textBounds.height());
        int iSave = canvas.save();
        canvas.scale(slot.appearAmount, slot.appearAmount, centerX, centerY);
        canvas.translate(f2, f3);
        canvas.scale(glyphScale, glyphScale);
        canvas.drawText(networkLabel, -slot.textBounds.exactCenterX(), -slot.textBounds.exactCenterY(), slot.paint);
        canvas.restoreToCount(iSave);
    }
}
