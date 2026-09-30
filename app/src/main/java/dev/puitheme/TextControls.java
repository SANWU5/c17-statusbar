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

/** Tracks native text and styling per view; one visible-view scheduler serves all time locations. */
public final class TextControls {
    public static final int NONE = 0, CLOCK = 1, CARRIER = 2;
    private final Handler handler;
    private final Map<TextView,Entry> views = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Integer> internal = ThreadLocal.withInitial(() -> 0);
    private Context context;
    private boolean interactive = true, pending;
    private volatile boolean clockEnabled;
    private volatile String clockPattern = TimeFormat.CLOCK_DEFAULT;
    private volatile Map<String, CarrierStyle> carrierStyles = carrierStyles(new Bundle());
    private volatile Map<String,Integer> colors = StatusBarSettings.COLOR_DEFAULTS;
    private volatile Map<String,Boolean> alpha = Collections.emptyMap();
    private volatile FeatureOptions features = FeatureOptions.from(Collections.emptyMap());
    private float clockX, clockY, clockScale = 100, clockSpacing;
    private int clockWeight = 600;
    private final Runnable tick = () -> { pending = false; refresh(); };

    private static final class Entry {
        int kind, nativeTint;
        float nativeSize, nativeSpacing;
        Typeface nativeFace;
        CharSequence nativeText;
        CharSequence nativeDescription;
        long nativeTextVersion, appliedNativeTextVersion = -1;
        boolean textApplied, appliedReplacement, managed;
        String group;
        Entry(TextView view, int kind) {
            this.kind = kind; nativeTint = view.getCurrentTextColor();
            nativeSize = view.getTextSize(); nativeFace = view.getTypeface();
            nativeSpacing = view.getLetterSpacing(); nativeText = view.getText();
            nativeDescription = view.getContentDescription();
        }
    }

    private static final class CarrierStyle {
        final String mode, pattern, text, colorGroup;
        final float x, y, scale, spacing;
        final int weight;
        CarrierStyle(Bundle settings, String group) {
            Object storedMode = panelValue(settings, group, "mode");
            String selected = storedMode instanceof String ? (String) storedMode : "original";
            mode = selected.equals("time") || selected.equals("text") ? selected : "original";
            Object storedPattern = panelValue(settings, group, "pattern");
            pattern = validPattern(storedPattern instanceof String ? (String) storedPattern : null, TimeFormat.CARRIER_DEFAULT);
            Object storedText = panelValue(settings, group, "text");
            String input = storedText instanceof String ? ((String) storedText).replace('\n', ' ').replace('\r', ' ') : "";
            text = input.length() > TimeFormat.MAX_TEXT ? input.substring(0, TimeFormat.MAX_TEXT) : input;
            x = panelNumber(settings, group, "offset_x", 0, -80, 80);
            y = panelNumber(settings, group, "offset_y", 0, -24, 24);
            scale = panelNumber(settings, group, "scale", 100, 25, 250);
            weight = Math.round(panelNumber(settings, group, "weight", 600, 100, 900));
            spacing = panelNumber(settings, group, "spacing", 0, -2, 8);
            // Direct callers supplying only old keys keep their old palette. The provider sends
            // explicit panel keys (including migrated colors), so independently saved colors win.
            colorGroup = CarrierPanels.isPanel(group) && CarrierPanels.legacyKey(CarrierPanels.key(group, "mode")) != null
                    && !hasPanelConfiguration(settings, group) ? "carrier" : group;
        }
    }

    private static Map<String, CarrierStyle> carrierStyles(Bundle settings) {
        Map<String, CarrierStyle> result = new LinkedHashMap<>();
        result.put("carrier", new CarrierStyle(settings, "carrier"));
        for (String group : CarrierPanels.GROUPS) result.put(group, new CarrierStyle(settings, group));
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
        if (className.equals("com.android.systemui.statusbar.policy.Clock")) {
            String parent = parentClass.toLowerCase(Locale.ROOT);
            if (parent.contains("qs") || parent.contains("shade") || parent.contains("keyguard")) return NONE;
            return resourceName.matches("clock|clock_(left|right|center)|status_bar_clock") ? CLOCK : NONE;
        }
        if (className.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) return CARRIER;
        // OplusQSCarrierText is the data-usage field, despite its misleading class name.
        if (className.equals("com.oplus.systemui.qs.widget.OplusQSCarrierText")) return NONE;
        if (className.equals("com.oplus.systemui.statusbar.widget.OplusStatCarrierText")
                || className.equals("com.android.keyguard.CarrierText")) return CARRIER;
        if (resourceName.matches("qs_carrier_text|qs_header_carrier_text") && isShadeParent(parentClass)) return CARRIER;
        if (isLockscreenCarrierResource(resourceName) && isLockscreenParent(parentClass)) return CARRIER;
        if (parentClass.equals("com.android.systemui.shade.carrier.ShadeCarrier")
                && resourceName.matches("carrier_text|carrier_name")) return CARRIER;
        return NONE;
    }

    private static boolean isShadeParent(String className) {
        String name = className.toLowerCase(Locale.ROOT);
        return name.contains(".qs.") || name.contains(".shade.") || name.endsWith("qsheader")
                || name.contains("quickstatusbarheader") || name.contains("shadestatusbar");
    }

    private static boolean isShadeResource(String resourceName) {
        return resourceName.matches("qs_clock_container|qs_header|header_container|simple_qs_container|simple_qs_footer"
                + "|qs_container_area_layout|qs_status_bar_container_layout|qs_panel|quick_qs_panel");
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
        return resourceName.matches("keyguard_header|keyguard_status_bar_contents|keyguard_status_view|keyguard_status_area");
    }

    private static boolean isLockscreenCarrierResource(String resourceName) {
        return resourceName.matches("keyguard_carrier_text|oplus_keyguard_carrier_text|lockscreen_carrier_text|carrier_text|carrier_name");
    }

    private static boolean isLockscreenCarrier(View view) {
        return namedClass(view.getClass(), "com.android.keyguard.CarrierText")
                || namedClass(view.getClass(), "com.oplus.systemui.statusbar.widget.OplusStatCarrierText");
    }

    public int kind(View view) {
        if (!(view instanceof TextView)) return NONE;
        String name = view.getClass().getName();
        if (namedClass(view.getClass(), "com.oplus.systemui.qs.widget.OplusQSCarrierText")) return NONE;
        if (name.equals("com.oplus.systemui.statusbar.widget.StatClock")) return CLOCK;
        if (name.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) return CARRIER;
        if (isLockscreenCarrier(view)) return CARRIER;
        String parent = view.getParent() == null ? "" : view.getParent().getClass().getName();
        String resource = "";
        String resourcePackage = "";
        try { if (view.getId() != View.NO_ID) {
            resource = view.getResources().getResourceEntryName(view.getId());
            resourcePackage = view.getResources().getResourcePackageName(view.getId());
        } }
        catch (Exception ignored) { }
        int result = namedKind(name, resource, parent);
        if (result != NONE && (result == CLOCK || "com.android.systemui".equals(resourcePackage))) return result;
        // Both separated notification and settings headers can inflate ordinary TextViews.
        // Match the SystemUI ID and its header ancestry rather than every carrier-looking text.
        boolean shadeCarrier = resource.matches("qs_carrier_text|qs_header_carrier_text");
        boolean lockCarrier = isLockscreenCarrierResource(resource);
        if (!"com.android.systemui".equals(resourcePackage) || (!shadeCarrier && !lockCarrier)) return NONE;
        ViewParent ancestor = view.getParent();
        for (int depth = 0; ancestor != null && depth < 16; depth++) {
            if (shadeCarrier && isShadeParent(ancestor.getClass().getName())) return CARRIER;
            if (lockCarrier && isLockscreenParent(ancestor.getClass())) return CARRIER;
            if (!(ancestor instanceof View)) break;
            View owner = (View) ancestor;
            try {
                if (owner.getId() != View.NO_ID
                        && "com.android.systemui".equals(owner.getResources().getResourcePackageName(owner.getId()))) {
                    String ownerId = owner.getResources().getResourceEntryName(owner.getId());
                    if ((shadeCarrier && isShadeResource(ownerId)) || (lockCarrier && isLockscreenResource(ownerId))) return CARRIER;
                }
            } catch (Exception ignored) { }
            ancestor = owner.getParent();
        }
        return NONE;
    }

    /** Current owner, resolved again after header recreation or view reparenting. */
    public String group(View view) {
        int kind = kind(view);
        if (kind == CLOCK) return "clock";
        if (kind != CARRIER) return "";
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
                    if (id.matches("simple_qs_container|simple_qs_footer")) return CarrierPanels.NOTIFICATION;
                    if (id.matches("qs_status_bar_container_layout|qs_container_area_layout")) control = true;
                    if (isLockscreenResource(id)) lockscreen = true;
                }
            } catch (Exception ignored) { }
            ancestor = owner.getParent();
        }
        // A notification header contains a QuickStatusBarHeader too: the outer SimpleHeader wins.
        return control ? CarrierPanels.CONTROL : lockscreen ? CarrierPanels.LOCKSCREEN : "carrier";
    }

    private static boolean namedClass(Class<?> source, String name) {
        for (Class<?> type = source; type != null; type = type.getSuperclass()) if (name.equals(type.getName())) return true;
        return false;
    }

    private Entry entry(TextView view) {
        synchronized (views) {
            Entry entry = views.get(view);
            if (entry == null) {
                int kind = kind(view);
                if (kind == NONE) return null;
                entry = new Entry(view, kind); views.put(view, entry);
            }
            entry.group = group(view);
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
        if (entry != null) { apply(view, entry, System.currentTimeMillis()); schedule(); }
    }

    /** Recreated header copies may use ordinary TextViews instead of the original widget class. */
    public void attachTree(View header) { attachTree(header, 0); }

    private void attachTree(View view, int depth) {
        if (view == null || depth > 8) return;
        if (view instanceof TextView && kind(view) != NONE) attach((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) attachTree(group.getChildAt(i), depth + 1);
        }
    }

    public void detach(TextView view) {
        // Weak entries survive reattachment, preserving the last real carrier/clock text.
        restart();
    }

    public boolean replaces(TextView view) {
        int kind = kind(view);
        String group = group(view);
        return kind == CLOCK ? clockEnabled : kind == CARRIER && carrierReplacement(group) && !carrierStyle(group).mode.equals("original");
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
        double scale = (entry.kind == CLOCK ? clockScale : carrierStyle(entry.group).scale) / 100d;
        return NumericPolicy.textPixels(entry.nativeSize * scale);
    }

    public void beforeMeasure(TextView view) {
        Entry entry = entry(view);
        if (entry != null) applyStyle(view, entry);
    }

    public void setInteractive(boolean value) { interactive = value; if (value) refresh(); else cancel(); }

    public void configure(Bundle settings, Map<String,Integer> colors, Map<String,Boolean> alpha) {
        this.colors = colors; this.alpha = alpha;
        features = FeatureOptions.from(settings);
        clockEnabled = features.effective("clock", StatusBarSettings.CLOCK_ENABLED);
        clockPattern = validPattern(settings.getString(StatusBarSettings.CLOCK_PATTERN), TimeFormat.CLOCK_DEFAULT);
        carrierStyles = carrierStyles(settings);
        clockX = value(settings, StatusBarSettings.CLOCK_OFFSET_X, 0, -80, 80);
        clockY = value(settings, StatusBarSettings.CLOCK_OFFSET_Y, 0, -24, 24);
        clockScale = value(settings, StatusBarSettings.CLOCK_SCALE, 100, 25, 250);
        clockWeight = Math.round(value(settings, StatusBarSettings.CLOCK_WEIGHT, 600, 100, 900));
        clockSpacing = value(settings, StatusBarSettings.CLOCK_SPACING, 0, -2, 8);
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
        String colorGroup = entry.kind == CLOCK ? "clock" : carrierStyle(entry.group).colorGroup;
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

    public void nativeSpacing(TextView view, float spacing) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null && !Float.isNaN(spacing) && !Float.isInfinite(spacing)) entry.nativeSpacing = spacing;
    }

    private CharSequence replacement(Entry entry, long now) {
        if (entry.kind == CLOCK && clockEnabled) return TimeFormat.format(clockPattern, now);
        if (entry.kind == CARRIER && carrierReplacement(entry.group)) {
            CarrierStyle style = carrierStyle(entry.group);
            if (style.mode.equals("time")) return TimeFormat.format(style.pattern, now);
            if (style.mode.equals("text")) return style.text;
        }
        return entry.nativeText;
    }

    public void beforeDraw(TextView view, Canvas canvas) {
        Entry entry = entry(view);
        if (entry == null) return;
        applyStyle(view, entry);
        float density = view.getResources().getDisplayMetrics().density;
        CarrierStyle style = carrierStyle(entry.group);
        if (features.position(group(entry))) canvas.translate(NumericPolicy.pixels(entry.kind == CLOCK ? clockX : style.x, density),
                NumericPolicy.pixels(entry.kind == CLOCK ? clockY : style.y, density));
    }

    private void applyStyle(TextView view, Entry entry) {
        if (!features.enabled(group(entry)) && !entry.managed) return;
        if (features.enabled(group(entry))) entry.managed = true;
        enter();
        try {
            String group = group(entry);
            CarrierStyle carrier = carrierStyle(group);
            float size = textSize(entry);
            if (Math.abs(view.getTextSize() - size) > .001f) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            int nativeWeight = entry.nativeFace == null ? 400 : Build.VERSION.SDK_INT >= 28
                    ? entry.nativeFace.getWeight() : entry.nativeFace.isBold() ? 700 : 400;
            Typeface face = features.enabled(group) && (features.enabled("font") || features.textStyle(group))
                    ? FontRepository.typeface(entry.nativeFace, features.textStyle(group)
                            ? (entry.kind == CLOCK ? clockWeight : carrier.weight) : nativeWeight)
                    : entry.nativeFace;
            if (view.getTypeface() != face) view.setTypeface(face);
            float spacing = entry.nativeSpacing + NumericPolicy.pixels(features.textStyle(group)
                    ? (entry.kind == CLOCK ? clockSpacing : carrier.spacing) : 0,
                    view.getResources().getDisplayMetrics().density) / Math.max(1f, size);
            if (Math.abs(view.getLetterSpacing() - spacing) > .0001f) view.setLetterSpacing(spacing);
            int tint = features.color(group) ? IconAppearance.color(entry.kind == CLOCK ? "clock" : carrier.colorGroup, entry.nativeTint, colors, alpha) : entry.nativeTint;
            if (view.getCurrentTextColor() != tint) view.setTextColor(tint);
        } finally { exit(); }
    }

    private void apply(TextView view, Entry entry, long now) {
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
            view.setContentDescription(replaced ? text : entry.nativeDescription);
            view.invalidate();
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
        cancel();
        long now = System.currentTimeMillis();
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                if (item.getKey().isAttachedToWindow()) apply(item.getKey(), item.getValue(), now);
            }
        }
        schedule();
    }

    private void cancel() { if (pending) handler.removeCallbacks(tick); pending = false; }
    private void restart() { cancel(); schedule(); }
    public void visibilityChanged() { restart(); }

    private void schedule() {
        if (pending || !interactive) return;
        boolean active = false, seconds = false;
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                TextView view = item.getKey(); Entry entry = item.getValue();
                if (!view.isAttachedToWindow() || !view.isShown()) continue;
                entry.group = group(view);
                if (entry.kind == CLOCK && clockEnabled) { active = true; seconds |= TimeFormat.hasSeconds(clockPattern); }
                CarrierStyle style = carrierStyle(entry.group);
                if (entry.kind == CARRIER && carrierReplacement(entry.group) && style.mode.equals("time")) { active = true; seconds |= TimeFormat.hasSeconds(style.pattern); }
            }
        }
        if (active) { pending = true; handler.postDelayed(tick, TimeFormat.nextDelay(System.currentTimeMillis(), seconds)); }
    }
}
