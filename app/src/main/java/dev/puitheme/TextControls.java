// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/** Tracks native text and styling per view; one visible-view scheduler serves all time locations. */
public final class TextControls {
    public static final int NONE = 0, CLOCK = 1, CARRIER = 2;
    private final Handler handler;
    private final Map<TextView,Entry> views = Collections.synchronizedMap(new WeakHashMap<>());
    private final Object classificationLock = new Object();
    private final Map<TextView,Classification> classifications = new WeakHashMap<>();
    private final Map<View,Map<TextView,Boolean>> classificationDependents = new WeakHashMap<>();
    private final ThreadLocal<Integer> internal = ThreadLocal.withInitial(() -> 0);
    private Context context;
    private boolean interactive = true, pending;
    private volatile Map<String, ClockStyle> clockStyles = clockStyles(new Bundle(), FeatureOptions.from(Collections.emptyMap()));
    private volatile Map<String, CarrierStyle> carrierStyles = carrierStyles(new Bundle());
    private volatile Map<String,Integer> colors = StatusBarSettings.COLOR_DEFAULTS;
    private volatile Map<String,Boolean> alpha = Collections.emptyMap();
    private volatile FeatureOptions features = FeatureOptions.from(Collections.emptyMap());
    private BooleanSupplier additionalClockVisible, additionalClockSeconds;
    private Runnable additionalClockUpdate;
    private final Runnable tick = () -> { pending = false; refresh(false); };

    /** Values never retain their TextView key or any strong parent/ancestor reference. */
    private static final class Classification {
        final int id;
        final WeakReference<ViewParent> parent;
        final ArrayList<WeakReference<View>> ancestors = new ArrayList<>(8);
        int kind;
        boolean dirty, ancestry, shadeClock;
        String carrierGroup = "";
        Classification(TextView view) { id = view.getId(); parent = new WeakReference<>(view.getParent()); }
    }

    private static final class Entry {
        int kind, nativeTint;
        float nativeSize, nativeSpacing;
        float diagnosticNativeSize = Float.NaN, diagnosticAppliedSize = Float.NaN, diagnosticDensity = Float.NaN;
        Typeface nativeFace;
        String nativeAxes;
        CharSequence nativeText;
        CharSequence nativeDescription;
        long nativeTextVersion, appliedNativeTextVersion = -1;
        boolean textApplied, appliedReplacement, managed;
        String group;
        Entry(TextView view, int kind) {
            this.kind = kind; capture(view);
        }
        void captureStyle(TextView view) {
            float size = view.getTextSize();
            if (size > 0f && !Float.isNaN(size) && !Float.isInfinite(size)) nativeSize = size;
            nativeTint = view.getCurrentTextColor(); nativeFace = view.getTypeface();
            nativeAxes = view.getFontVariationSettings(); nativeSpacing = view.getLetterSpacing();
        }
        void capture(TextView view) {
            nativeTint = view.getCurrentTextColor();
            nativeSize = view.getTextSize(); nativeFace = view.getTypeface();
            nativeAxes = view.getFontVariationSettings();
            nativeSpacing = view.getLetterSpacing(); nativeText = view.getText();
            nativeDescription = view.getContentDescription();
            textApplied = appliedReplacement = managed = false;
            appliedNativeTextVersion = -1;
        }
    }

    private static final class CarrierStyle {
        final String mode, pattern, text, colorGroup;
        final boolean seconds;
        final float x, y, scale, spacing;
        final int weight;
        CarrierStyle(Bundle settings, String group) {
            Object storedMode = panelValue(settings, group, "mode");
            String selected = storedMode instanceof String ? (String) storedMode : "original";
            mode = selected.equals("time") || selected.equals("text") ? selected : "original";
            Object storedPattern = panelValue(settings, group, "pattern");
            pattern = validPattern(storedPattern instanceof String ? (String) storedPattern : null, TimeFormat.CARRIER_DEFAULT);
            seconds = TimeFormat.hasSeconds(pattern);
            Object storedText = panelValue(settings, group, "text");
            String input = storedText instanceof String ? ((String) storedText).replace('\n', ' ').replace('\r', ' ') : "";
            text = input.length() > TimeFormat.MAX_TEXT ? input.substring(0, TimeFormat.MAX_TEXT) : input;
            x = panelNumber(settings, group, "offset_x", 0, -80, 80);
            y = panelNumber(settings, group, "offset_y", 0, -24, 24);
            scale = panelNumber(settings, group, "scale", 100, 25, 250);
            weight = Math.round(panelNumber(settings, group, "weight", 600, 1, 1000));
            spacing = panelNumber(settings, group, "spacing", 0, -2, 8);
            // Direct callers supplying only old keys keep their old palette. The provider sends
            // explicit panel keys (including migrated colors), so independently saved colors win.
            colorGroup = CarrierPanels.isPanel(group) && CarrierPanels.legacyKey(CarrierPanels.key(group, "mode")) != null
                    && !hasPanelConfiguration(settings, group) ? "carrier" : group;
        }
    }

    private static final class ClockStyle {
        final boolean enabled, seconds;
        final String pattern;
        final float x, y, scale, spacing;
        final int weight;
        ClockStyle(Bundle settings, String group, FeatureOptions features) {
            enabled = features.effective(group, group + "_enabled");
            pattern = validPattern(settings.getString(group + "_pattern"), TimeFormat.CLOCK_DEFAULT);
            seconds = TimeFormat.hasSeconds(pattern);
            x = value(settings, group + "_offset_x", 0, -80, 80);
            y = value(settings, group + "_offset_y", 0, -24, 24);
            scale = value(settings, group + "_scale", 100, 25, 250);
            weight = Math.round(value(settings, group + "_weight", 600, 1, 1000));
            spacing = value(settings, group + "_spacing", 0, -2, 8);
        }
    }

    private static Map<String, ClockStyle> clockStyles(Bundle settings, FeatureOptions features) {
        Map<String, ClockStyle> result = new LinkedHashMap<>();
        for (String group : new String[]{"clock", "shade_clock", PanelMode.CLOCK}) result.put(group, new ClockStyle(settings, group, features));
        return Collections.unmodifiableMap(result);
    }

    private ClockStyle clockStyle(String group) {
        ClockStyle style = clockStyles.get(group);
        return style == null ? clockStyles.get("clock") : style;
    }

    private static Map<String, CarrierStyle> carrierStyles(Bundle settings) {
        Map<String, CarrierStyle> result = new LinkedHashMap<>();
        result.put("carrier", new CarrierStyle(settings, "carrier"));
        for (String group : CarrierPanels.ALL_GROUPS) result.put(group, new CarrierStyle(settings, group));
        return Collections.unmodifiableMap(result);
    }

    private CarrierStyle carrierStyle(String group) {
        CarrierStyle style = carrierStyles.get(group);
        return style == null ? carrierStyles.get("carrier") : style;
    }

    private static Object panelValue(Bundle settings, String group, String suffix) {
        String key = CarrierPanels.isPanel(group) ? CarrierPanels.key(group, suffix) : "carrier_" + suffix;
        Object value = settings.get(key);
        if (value == null && CarrierPanels.isPanel(group)) {
            String legacy = CarrierPanels.legacyKey(key);
            if (legacy != null) value = settings.get(legacy);
        }
        return value;
    }

    private static float panelNumber(Bundle settings, String group, String suffix, float fallback, float min, float max) {
        Object stored = panelValue(settings, group, suffix);
        String key = CarrierPanels.isPanel(group) ? CarrierPanels.key(group, suffix) : "carrier_" + suffix;
        return NumericPolicy.setting(key, stored, fallback);
    }

    private static boolean hasPanelConfiguration(Bundle settings, String group) {
        for (String suffix : new String[]{"mode", "pattern", "text", "enabled", "replace_enabled", "position_enabled",
                "size_enabled", "color_enabled", "text_style_enabled", "offset_x", "offset_y", "scale", "weight", "spacing",
                "color_light", "color_dark", "color_light_custom_alpha", "color_dark_custom_alpha"}) {
            if (settings.get(CarrierPanels.key(group, suffix)) != null) return true;
        }
        return false;
    }

    public TextControls(Handler handler) { this.handler = handler; }
    public boolean isInternal() { return internal.get() != 0; }
    public void enter() { internal.set(internal.get() + 1); }
    public void exit() { internal.set(Math.max(0, internal.get() - 1)); }

    public static int namedKind(String className, String resourceName, String parentClass) {
        if (className.equals("com.oplus.systemui.statusbar.widget.StatClock")) return CLOCK;
        if (isShadeClockClass(className)) return CLOCK;
        if (isShadeClockResource(resourceName) && isShadeParent(parentClass)) return CLOCK;
        if (className.equals("com.android.systemui.statusbar.policy.Clock")) {
            String parent = parentClass.toLowerCase(Locale.ROOT);
            if (parent.contains("qs") || parent.contains("shade") || parent.contains("keyguard")) return NONE;
            return oneOf(resourceName, "clock", "clock_left", "clock_right", "clock_center", "status_bar_clock") ? CLOCK : NONE;
        }
        if (className.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) return CARRIER;
        // OplusQSCarrierText is the data-usage field, despite its misleading class name.
        if (className.equals("com.oplus.systemui.qs.widget.OplusQSCarrierText")) return NONE;
        if (className.equals("com.oplus.systemui.statusbar.widget.OplusStatCarrierText")
                || className.equals("com.android.keyguard.CarrierText")) return CARRIER;
        if (isShadeCarrierResource(resourceName) && isShadeParent(parentClass)) return CARRIER;
        if (isLockscreenCarrierResource(resourceName) && isLockscreenParent(parentClass)) return CARRIER;
        if (parentClass.equals("com.android.systemui.shade.carrier.ShadeCarrier")
                && (resourceName.equals("carrier_text") || resourceName.equals("carrier_name"))) return CARRIER;
        return NONE;
    }

    private static boolean isShadeClockClass(String className) {
        return className.equals("com.oplus.systemui.qs.widget.OplusQSClock")
                || className.equals("com.oplus.systemui.qs.widget.SimpleQsClock")
                || className.equals("com.oplus.systemui.qs.fake.view.QsClock");
    }

    private static boolean isShadeClockResource(String resourceName) {
        return resourceName.equals("qs_footer_clock") || resourceName.equals("oplus_qs_clock");
    }
    private static boolean isShadeCarrierResource(String resourceName) {
        return resourceName.equals("qs_carrier_text") || resourceName.equals("qs_header_carrier_text");
    }
    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices) if (value.equals(choice)) return true;
        return false;
    }

    private static boolean isShadeClock(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
            if (isShadeClockClass(type.getName())) return true;
        return false;
    }

    private static boolean isShadeParent(String className) {
        String name = className.toLowerCase(Locale.ROOT);
        return className.equals("com.oplus.systemui.separate.OplusQSSimpleHeader")
                || name.contains(".qs.") || name.contains(".shade.") || name.endsWith("qsheader")
                || name.contains("quickstatusbarheader") || name.contains("shadestatusbar");
    }

    private static boolean isShadeResource(String resourceName) {
        return oneOf(resourceName, "qs_clock_container", "qs_header", "header_container", "simple_qs_container", "simple_qs_footer",
                "qs_container_area_layout", "qs_status_bar_container_layout", "qs_panel", "quick_qs_panel");
    }

    private static boolean isLockscreenParent(String className) {
        return className.equals("com.android.systemui.statusbar.phone.KeyguardStatusBarView")
                || className.equals("com.oplus.systemui.statusbar.widget.FixedKeyguardStatusBarView")
                || className.equals("com.oplus.systemui.statusbar.widget.KeyguardStartSideContentLayout")
                || className.equals("com.android.keyguard.KeyguardStatusView");
    }

    private static boolean isLockscreenParent(Class<?> source) {
        for (Class<?> type = source; type != null; type = type.getSuperclass())
            if (isLockscreenParent(type.getName())) return true;
        return false;
    }

    private static boolean isLockscreenResource(String resourceName) {
        return oneOf(resourceName, "keyguard_header", "keyguard_status_bar_contents", "keyguard_status_view", "keyguard_status_area");
    }

    private static boolean isLockscreenCarrierResource(String resourceName) {
        return oneOf(resourceName, "keyguard_carrier_text", "oplus_keyguard_carrier_text", "lockscreen_carrier_text", "carrier_text", "carrier_name");
    }

    private static boolean isLockscreenCarrier(View view) {
        return namedClass(view.getClass(), "com.android.keyguard.CarrierText")
                || namedClass(view.getClass(), "com.oplus.systemui.statusbar.widget.OplusStatCarrierText");
    }

    public int kind(View view) {
        if (!(view instanceof TextView)) return NONE;
        return classification((TextView) view).kind;
    }

    /** Call only after native ID/parent or real header lifecycle mutations, never from draw. */
    public void classificationChanged(View view) {
        if (view == null) return;
        synchronized (classificationLock) {
            Classification own = classifications.get(view);
            if (own != null) own.dirty = true;
            Map<TextView,Boolean> dependents = classificationDependents.get(view);
            if (dependents != null) for (TextView text : dependents.keySet()) {
                Classification value = classifications.get(text);
                if (value != null) value.dirty = true;
            }
        }
    }

    private Classification classification(TextView view) {
        Classification resolved;
        synchronized (classificationLock) {
            Classification previous = classifications.get(view);
            if (previous != null && !previous.dirty && previous.id == view.getId()
                    && previous.parent.get() == view.getParent()) return previous;
            if (previous != null) for (WeakReference<View> reference : previous.ancestors) {
                View ancestor = reference.get();
                Map<TextView,Boolean> dependents = ancestor == null ? null : classificationDependents.get(ancestor);
                if (dependents != null) dependents.remove(view);
            }
            resolved = new Classification(view);
            resolved.kind = resolveKind(view, resolved);
            if (resolved.kind == CLOCK) resolved.shadeClock = isShadeClockLocation(view);
            if (resolved.kind == CARRIER) resolved.carrierGroup = resolveCarrierGroup(view);
            if (resolved.ancestry) {
                ViewParent parent = view.getParent();
                for (int depth = 0; parent instanceof View && depth < 16; depth++) {
                    View ancestor = (View) parent;
                    resolved.ancestors.add(new WeakReference<>(ancestor));
                    Map<TextView,Boolean> dependents = classificationDependents.get(ancestor);
                    if (dependents == null) {
                        dependents = new WeakHashMap<>(); classificationDependents.put(ancestor, dependents);
                    }
                    dependents.put(view, Boolean.TRUE); parent = ancestor.getParent();
                }
            }
            classifications.put(view, resolved);
        }
        // No inverse lock ordering: the dependency index lock is released before native restoration.
        synchronized (views) {
            Entry entry = views.get(view);
            if (entry != null) {
                if (entry.kind == NONE && resolved.kind != NONE) entry.capture(view);
                entry.kind = resolved.kind; entry.group = selectedGroup(resolved);
                if (entry.managed) apply(view, entry, System.currentTimeMillis(), true);
            }
        }
        return resolved;
    }

    private int resolveKind(View view, Classification classification) {
        String name = view.getClass().getName();
        if (namedClass(view.getClass(), "com.oplus.systemui.qs.widget.OplusQSCarrierText")) return NONE;
        if (name.equals("com.oplus.systemui.statusbar.widget.StatClock")) return CLOCK;
        // Real and animation shade clocks belong to one clock family; group() selects
        // status-following or independent shade settings. Carrier replacements stay separate.
        if (isShadeClock(view)) return CLOCK;
        if (name.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) { classification.ancestry = true; return CARRIER; }
        if (isLockscreenCarrier(view)) { classification.ancestry = true; return CARRIER; }
        String parent = view.getParent() == null ? "" : view.getParent().getClass().getName();
        String resource = "";
        String resourcePackage = "";
        try { if (view.getId() != View.NO_ID) {
            resource = view.getResources().getResourceEntryName(view.getId());
            resourcePackage = view.getResources().getResourcePackageName(view.getId());
        } }
        catch (Exception ignored) { }
        int result = namedKind(name, resource, parent);
        boolean shadeClock = isShadeClockResource(resource);
        boolean shadeCarrier = isShadeCarrierResource(resource);
        boolean lockCarrier = isLockscreenCarrierResource(resource);
        classification.ancestry = name.equals("com.android.systemui.statusbar.policy.Clock")
                || "com.android.systemui".equals(resourcePackage) && (shadeClock || shadeCarrier || lockCarrier);
        if (result != NONE && ((result == CLOCK && !shadeClock) || "com.android.systemui".equals(resourcePackage))) return result;
        // Both separated notification and settings headers can inflate ordinary TextViews.
        // Match the SystemUI ID and its header ancestry rather than every carrier-looking text.
        if (!"com.android.systemui".equals(resourcePackage) || (!shadeClock && !shadeCarrier && !lockCarrier)) return NONE;
        ViewParent ancestor = view.getParent();
        for (int depth = 0; ancestor != null && depth < 16; depth++) {
            if (shadeClock && isShadeParent(ancestor.getClass().getName())) return CLOCK;
            if (shadeCarrier && isShadeParent(ancestor.getClass().getName())) return CARRIER;
            if (lockCarrier && isLockscreenParent(ancestor.getClass())) return CARRIER;
            if (!(ancestor instanceof View)) break;
            View owner = (View) ancestor;
            try {
                if (owner.getId() != View.NO_ID
                        && "com.android.systemui".equals(owner.getResources().getResourcePackageName(owner.getId()))) {
                    String ownerId = owner.getResources().getResourceEntryName(owner.getId());
                    if (shadeClock && (isShadeResource(ownerId)
                            || oneOf(ownerId, "qs_fake_clock_container", "oplus_fake_clock_container", "separateqs_fake_status_layout"))) return CLOCK;
                    if ((shadeCarrier && isShadeResource(ownerId)) || (lockCarrier && isLockscreenResource(ownerId))) return CARRIER;
                }
            } catch (Exception ignored) { }
            ancestor = owner.getParent();
        }
        return NONE;
    }

    /** Current owner, resolved again after header recreation or view reparenting. */
    public String group(View view) {
        return view instanceof TextView ? selectedGroup(classification((TextView) view)) : "";
    }
    private String selectedGroup(Classification classification) {
        if (PanelMode.classic()) {
            if (classification.kind == CLOCK && classification.shadeClock) return PanelMode.CLOCK;
            if (classification.kind == CARRIER && (CarrierPanels.NOTIFICATION.equals(classification.carrierGroup)
                    || CarrierPanels.CONTROL.equals(classification.carrierGroup))) return CarrierPanels.CLASSIC;
        }
        if (classification.kind == CLOCK) return features.enabled("shade_clock") && classification.shadeClock ? "shade_clock" : "clock";
        return classification.kind == CARRIER ? classification.carrierGroup : "";
    }
    private static String resolveCarrierGroup(View view) {
        boolean control = false, lockscreen = isLockscreenCarrier(view);
        ViewParent ancestor = view.getParent();
        for (int depth = 0; ancestor != null && depth < 16; depth++) {
            if (namedClass(ancestor.getClass(), "com.oplus.systemui.separate.OplusQSSimpleHeader")) return CarrierPanels.NOTIFICATION;
            if (namedClass(ancestor.getClass(), "com.oplus.systemui.qs.OplusQuickStatusBarHeader")) control = true;
            if (isLockscreenParent(ancestor.getClass())) lockscreen = true;
            if (!(ancestor instanceof View)) break;
            View owner = (View) ancestor;
            try {
                if (owner.getId() != View.NO_ID && "com.android.systemui".equals(owner.getResources().getResourcePackageName(owner.getId()))) {
                    String id = owner.getResources().getResourceEntryName(owner.getId());
                    if (id.equals("simple_qs_container") || id.equals("simple_qs_footer")) return CarrierPanels.NOTIFICATION;
                    if (id.equals("qs_status_bar_container_layout") || id.equals("qs_container_area_layout")) control = true;
                    if (isLockscreenResource(id)) lockscreen = true;
                }
            } catch (Exception ignored) { }
            ancestor = owner.getParent();
        }
        // A notification header contains a QuickStatusBarHeader too: the outer SimpleHeader wins.
        return control ? CarrierPanels.CONTROL : lockscreen ? CarrierPanels.LOCKSCREEN : "carrier";
    }

    private static boolean isShadeClockLocation(View view) {
        if (isShadeClock(view)) return true;
        try {
            return view.getId() != View.NO_ID
                    && "com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId()))
                    && isShadeClockResource(view.getResources().getResourceEntryName(view.getId()));
        } catch (Exception ignored) { return false; }
    }

    /** Measures follow the selected owner even when the status-bar clock group is disabled. */
    public boolean clockControlsEnabled(TextView view) {
        return kind(view) == CLOCK && features.enabled(group(view));
    }

    private static boolean namedClass(Class<?> source, String name) {
        for (Class<?> type = source; type != null; type = type.getSuperclass()) if (name.equals(type.getName())) return true;
        return false;
    }

    private Entry entry(TextView view) {
        Classification classification = classification(view);
        if (classification.kind == NONE) return null;
        synchronized (views) {
            Entry entry = views.get(view);
            if (entry == null) {
                entry = new Entry(view, classification.kind); views.put(view, entry);
            }
            entry.group = selectedGroup(classification);
            return entry;
        }
    }

    public void attach(TextView view) {
        if (context == null) {
            context = view.getContext().getApplicationContext();
            if (context == null) context = view.getContext();
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) interactive = power.isInteractive();
        }
        Entry entry = entry(view);
        if (entry != null && !isInternal()) { apply(view, entry, System.currentTimeMillis()); schedule(); }
    }

    /** Recreated header copies may use ordinary TextViews instead of the original widget class. */
    public void attachTree(View header) { classificationChanged(header); attachTree(header, 0); }

    private void attachTree(View view, int depth) {
        if (view == null || depth > 8) return;
        if (view instanceof TextView && kind(view) != NONE) attach((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) attachTree(group.getChildAt(i), depth + 1);
        }
    }

    public void detach(TextView view) {
        classificationChanged(view);
        // Weak entries survive reattachment, preserving the last real carrier/clock text.
        restart();
    }

    public boolean replaces(TextView view) {
        int kind = kind(view);
        String group = group(view);
        return kind == CLOCK ? clockStyle(group).enabled : kind == CARRIER && carrierReplacement(group) && !carrierStyle(group).mode.equals("original");
    }

    private boolean carrierReplacement(String group) { return features.effective(group, group + "_replace_enabled"); }
    private static String group(Entry entry) { return entry.group; }

    public Typeface nativeFamily() {
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                if (item.getValue().kind == CLOCK && item.getKey().isAttachedToWindow()
                        && item.getKey().isShown() && item.getValue().nativeFace != null) return item.getValue().nativeFace;
            }
        }
        return Typeface.DEFAULT;
    }

    public float styledSize(TextView view) {
        Entry entry = entry(view);
        return entry == null ? view.getTextSize() : textSize(entry);
    }

    private float textSize(Entry entry) {
        if (!features.size(group(entry))) return entry.nativeSize;
        double scale = (entry.kind == CLOCK ? clockStyle(entry.group).scale : carrierStyle(entry.group).scale) / 100d;
        return NumericPolicy.textPixels(entry.nativeSize * scale);
    }

    public void beforeMeasure(TextView view) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null) applyStyle(view, entry);
    }

    /** Vendor width probes write the native clock size into Paint; restore style and width together. */
    public float restoreClockWidth(TextView view, float nativeWidth, float measuredSize) {
        if (isInternal()) return nativeWidth;
        if (!clockControlsEnabled(view)) return nativeWidth;
        beforeMeasure(view);
        double ratio = measuredSize > 0f && !Float.isNaN(measuredSize) && !Float.isInfinite(measuredSize)
                ? (double) styledSize(view) / measuredSize : 1d;
        return Math.max(0f, NumericPolicy.drawPixels((double) nativeWidth * ratio));
    }

    public void setInteractive(boolean value) { interactive = value; if (value) refresh(); else cancel(); }

    public void configure(Bundle settings, Map<String,Integer> colors, Map<String,Boolean> alpha) {
        this.colors = colors; this.alpha = alpha;
        features = FeatureOptions.from(settings);
        clockStyles = clockStyles(settings, features);
        carrierStyles = carrierStyles(settings);
        refresh();
    }

    private static String validPattern(String pattern, String fallback) {
        return pattern != null && TimeFormat.validationError(pattern) == null ? pattern : fallback;
    }

    private static float value(Bundle settings, String key, float fallback, float min, float max) {
        return NumericPolicy.setting(key, settings.get(key), fallback);
    }

    public CharSequence nativeText(TextView view, CharSequence text) {
        if (isInternal()) return text;
        Entry entry = entry(view);
        if (entry == null) return text;
        entry.nativeText = text;
        entry.nativeTextVersion++;
        return replacement(entry, System.currentTimeMillis());
    }

    public int nativeColor(TextView view, int color) {
        if (isInternal()) return color;
        Entry entry = entry(view);
        if (entry == null) return color;
        entry.nativeTint = color;
        String colorGroup = entry.kind == CLOCK ? entry.group : carrierStyle(entry.group).colorGroup;
        return features.color(group(entry)) ? IconAppearance.color(colorGroup, color, colors, alpha) : color;
    }

    public CharSequence nativeDescription(TextView view, CharSequence description) {
        if (isInternal()) return description;
        Entry entry = entry(view);
        if (entry == null) return description;
        entry.nativeDescription = description;
        return replaces(view) ? replacement(entry, System.currentTimeMillis()) : description;
    }

    public void nativeSize(TextView view, float pixels) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null && pixels >= 0 && !Float.isInfinite(pixels) && !Float.isNaN(pixels)) entry.nativeSize = pixels;
    }

    public void nativeTypeface(TextView view, Typeface typeface) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null) entry.nativeFace = typeface;
    }

    /** Called after a real TextView variation setter, with its nested typeface writes scoped out. */
    public void nativeFontVariation(TextView view) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null) { entry.nativeFace = view.getTypeface(); entry.nativeAxes = view.getFontVariationSettings(); }
    }

    public void nativeSpacing(TextView view, float spacing) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null && !Float.isNaN(spacing) && !Float.isInfinite(spacing)) entry.nativeSpacing = spacing;
    }

    /** OEM config/auto-fit code must read native pixels, never our already scaled Paint. */
    public NativeStyleScope beginNativeStyle(TextView view) {
        if (isInternal()) return null;
        Entry entry = entry(view);
        if (entry == null || !entry.managed && !features.enabled(group(entry))) return null;
        enter();
        try {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, entry.nativeSize);
            FontWeight.restore(view, entry.nativeFace, entry.nativeAxes);
            view.setLetterSpacing(entry.nativeSpacing);
            view.setTextColor(entry.nativeTint);
            return new NativeStyleScope(view, entry);
        } catch (Throwable failure) {
            exit(); throw failure;
        }
    }

    public final class NativeStyleScope implements AutoCloseable {
        private final TextView view;
        private final Entry entry;
        private final CharSequence previousText, previousDescription;
        private boolean closed;
        private NativeStyleScope(TextView view, Entry entry) {
            this.view = view; this.entry = entry;
            previousText = view.getText(); previousDescription = view.getContentDescription();
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                entry.captureStyle(view);
                if (!sameCharacters(previousText, view.getText())) {
                    entry.nativeText = view.getText(); entry.nativeTextVersion++;
                }
                if (!sameCharacters(previousDescription, view.getContentDescription()))
                    entry.nativeDescription = view.getContentDescription();
            }
            finally { exit(); }
            apply(view, entry, System.currentTimeMillis(), false);
            if (ModuleDiagnostics.enabled()) ModuleDiagnostics.info("font",
                    "Native text style restored before OEM configuration; applied once after callback");
        }
    }

    private CharSequence replacement(Entry entry, long now) {
        ClockStyle clock = clockStyle(entry.group);
        if (entry.kind == CLOCK && clock.enabled) return TimeFormat.format(clock.pattern, now);
        if (entry.kind == CARRIER && carrierReplacement(entry.group)) {
            CarrierStyle style = carrierStyle(entry.group);
            if (style.mode.equals("time")) return TimeFormat.format(style.pattern, now);
            if (style.mode.equals("text")) return style.text;
        }
        return entry.nativeText;
    }

    public void beforeDraw(TextView view, Canvas canvas) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry == null) return;
        applyStyle(view, entry);
        float density = view.getResources().getDisplayMetrics().density;
        CarrierStyle style = carrierStyle(entry.group);
        // Following the status clock's text/style does not give a shade header the status
        // bar's coordinates. Its native anchor is different (and moves with the panel).
        // Both the real header and its animation copies use only the independent shade
        // position switch, so toggling that master cannot introduce a status-only offset.
        String positionGroup = entry.kind == CLOCK && classification(view).shadeClock && !PanelMode.classic() ? "shade_clock" : entry.group;
        ClockStyle clock = clockStyle(positionGroup);
        if (features.position(positionGroup)) canvas.translate(NumericPolicy.pixels(entry.kind == CLOCK ? clock.x : style.x, density),
                NumericPolicy.pixels(entry.kind == CLOCK ? clock.y : style.y, density));
    }

    private void applyStyle(TextView view, Entry entry) {
        if (isInternal()) return;
        if (!features.enabled(group(entry)) && !entry.managed) return;
        if (features.enabled(group(entry))) entry.managed = true;
        enter();
        try {
            String group = group(entry);
            CarrierStyle carrier = carrierStyle(group);
            ClockStyle clock = clockStyle(group);
            float size = textSize(entry);
            if (Math.abs(view.getTextSize() - size) > .001f) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            int nativeWeight = entry.nativeFace == null ? 400 : Build.VERSION.SDK_INT >= 28
                    ? entry.nativeFace.getWeight() : entry.nativeFace.isBold() ? 700 : 400;
            boolean textStyle = features.textStyle(group);
            boolean sourceChanged = features.enabled("font") && !FontRepository.systemMode();
            if (features.enabled(group) && (sourceChanged || textStyle))
                FontRepository.apply(view, entry.nativeFace, textStyle
                        ? (entry.kind == CLOCK ? clock.weight : carrier.weight) : nativeWeight, entry.nativeAxes);
            else FontWeight.restore(view, entry.nativeFace, entry.nativeAxes);
            float spacing = entry.nativeSpacing + NumericPolicy.pixels(features.textStyle(group)
                    ? (entry.kind == CLOCK ? clock.spacing : carrier.spacing) : 0,
                    view.getResources().getDisplayMetrics().density) / Math.max(1f, size);
            if (Math.abs(view.getLetterSpacing() - spacing) > .0001f) view.setLetterSpacing(spacing);
            int tint = features.color(group) ? IconAppearance.color(entry.kind == CLOCK ? group : carrier.colorGroup, entry.nativeTint, colors, alpha) : entry.nativeTint;
            if (view.getCurrentTextColor() != tint) view.setTextColor(tint);
            if (ModuleDiagnostics.enabled()) {
                float density = view.getResources().getDisplayMetrics().density;
                if (entry.diagnosticNativeSize != entry.nativeSize || entry.diagnosticAppliedSize != size
                        || entry.diagnosticDensity != density) {
                    entry.diagnosticNativeSize = entry.nativeSize; entry.diagnosticAppliedSize = size;
                    entry.diagnosticDensity = density;
                    ModuleDiagnostics.info("font", "Text metrics " + group + "; native pixels " + entry.nativeSize
                            + "; applied pixels " + size + "; density " + density
                            + "; scaled density " + view.getResources().getDisplayMetrics().scaledDensity);
                }
            }
        } finally { exit(); }
    }

    private void apply(TextView view, Entry entry, long now) {
        apply(view, entry, now, true);
    }

    private void apply(TextView view, Entry entry, long now, boolean redrawUnchanged) {
        entry.group = group(view);
        // A disabled location starts as a native pass-through, including spans, font and alpha.
        // Once enabled, one restoration pass is needed when it is disabled or moved elsewhere.
        if (!features.enabled(entry.group) && !entry.managed) return;
        applyStyle(view, entry);
        CharSequence text = replacement(entry, now);
        if (text == null) text = "";
        boolean replaced = replaces(view);
        // TextView can wrap a String or Spannable in a different CharSequence implementation.
        // Compare characters during stable refreshes; preserve native spans once on restoration.
        boolean force = !entry.textApplied || entry.appliedReplacement != replaced
                || (!replaced && entry.appliedNativeTextVersion != entry.nativeTextVersion);
        enter();
        try {
            if (force || !sameCharacters(view.getText(), text)) view.setText(text);
            entry.textApplied = true;
            entry.appliedReplacement = replaced;
            if (!replaced) entry.appliedNativeTextVersion = entry.nativeTextVersion;
            CharSequence description = replaced ? text : entry.nativeDescription;
            if (redrawUnchanged || force || !sameCharacters(view.getContentDescription(), description))
                view.setContentDescription(description);
            // Text/style setters invalidate actual changes. Explicit refreshes also redraw
            // unchanged text because position settings are applied only inside beforeDraw.
            if (redrawUnchanged) view.invalidate();
            if (!features.enabled(entry.group)) entry.managed = false;
        } finally { exit(); }
    }

    private static boolean sameCharacters(CharSequence first, CharSequence second) {
        if (first == second) return true;
        if (first == null || second == null || first.length() != second.length()) return false;
        for (int i = 0; i < first.length(); i++) if (first.charAt(i) != second.charAt(i)) return false;
        return true;
    }

    public void refresh() {
        refresh(true);
    }

    private void refresh(boolean redrawUnchanged) {
        if (isInternal()) return;
        cancel();
        long now = System.currentTimeMillis();
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                if (item.getKey().isAttachedToWindow()) apply(item.getKey(), item.getValue(), now, redrawUnchanged);
            }
        }
        if (additionalClockVisible != null && additionalClockVisible.getAsBoolean()
                && additionalClockUpdate != null) additionalClockUpdate.run();
        schedule();
    }

    /** Shares the existing visible-time scheduler with overlays; no extra timer is created. */
    public void setAdditionalClock(BooleanSupplier visible, BooleanSupplier seconds, Runnable update) {
        additionalClockVisible = visible; additionalClockSeconds = seconds; additionalClockUpdate = update;
        restart();
    }

    private void cancel() { if (pending) handler.removeCallbacks(tick); pending = false; }
    private void restart() { cancel(); schedule(); }
    public void visibilityChanged() { restart(); }

    private void schedule() {
        if (pending || !interactive) return;
        boolean active = additionalClockVisible != null && additionalClockVisible.getAsBoolean();
        boolean seconds = active && additionalClockSeconds != null && additionalClockSeconds.getAsBoolean();
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                TextView view = item.getKey(); Entry entry = item.getValue();
                if (!view.isAttachedToWindow() || !view.isShown()) continue;
                entry.group = group(view);
                ClockStyle clock = clockStyle(entry.group);
                if (entry.kind == CLOCK && clock.enabled) { active = true; seconds |= clock.seconds; }
                CarrierStyle style = carrierStyle(entry.group);
                if (entry.kind == CARRIER && carrierReplacement(entry.group) && style.mode.equals("time")) { active = true; seconds |= style.seconds; }
            }
        }
        if (active) { pending = true; handler.postDelayed(tick, TimeFormat.nextDelay(System.currentTimeMillis(), seconds)); }
    }
}
