// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Reuses the OEM sampler and its two native text views. Never formats traffic or creates a timer. */
public final class NetworkSpeedControls {
    public static final String INTERVAL_ENABLED = "speed_refresh_enabled";
    public static final String INTERVAL_SECONDS = "speed_refresh_seconds";
    public static final String INTERVAL_MILLIS = "speed_refresh_millis";
    public static final String STYLE_ENABLED = "speed_style_enabled";
    public static final String DISPLAY_STYLE = "speed_display_style";
    public static final String CONTROLLER = "com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl";
    public static final String VIEW = "com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView";
    public static final String SCHEDULE_METHOD = "postUpdateNetworkSpeedDelay";
    public static final float DEFAULT_SECONDS = 4f;
    public static final long MIN_INTERVAL_MILLIS = 1L;
    public static final float DEFAULT_MILLIS = 250f;
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put(INTERVAL_ENABLED, false); flags.put(STYLE_ENABLED, false);
        Map<String, Float> numbers = new LinkedHashMap<>(); numbers.put(INTERVAL_SECONDS, DEFAULT_SECONDS);
        numbers.put(INTERVAL_MILLIS, DEFAULT_MILLIS);
        Map<String, String> strings = new LinkedHashMap<>(); strings.put(DISPLAY_STYLE, "system");
        BOOLEANS = Collections.unmodifiableMap(flags); NUMBERS = Collections.unmodifiableMap(numbers);
        STRINGS = Collections.unmodifiableMap(strings);
    }

    private static final class Binding {
        final WeakReference<TextView> number, unit;
        final Paint.FontMetrics metrics = new Paint.FontMetrics();
        FrameLayout.LayoutParams nativeNumber, nativeUnit;
        int nativeWidth, nativeUnitVisibility, nativeDepth;
        boolean owned;
        Binding(View host, TextView number, TextView unit) {
            this.number = new WeakReference<>(number); this.unit = new WeakReference<>(unit);
            capture(host, number, unit);
        }
        void capture(View host, TextView number, TextView unit) {
            nativeNumber = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) number.getLayoutParams());
            nativeUnit = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) unit.getLayoutParams());
            nativeWidth = host.getLayoutParams().width; nativeUnitVisibility = unit.getVisibility();
        }
    }

    private final Map<View, Binding> views = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean intervalEnabled;
    private volatile long intervalMillis = 4000L;
    private volatile String style = "system";

    public void configure(Bundle settings) {
        boolean active = settings != null && !SafetyMode.enabled(settings)
                && Boolean.TRUE.equals(settings.get("speed_enabled"));
        intervalMillis = settings != null && settings.containsKey(INTERVAL_MILLIS)
                ? millisValue(settings.get(INTERVAL_MILLIS))
                : settings != null && settings.containsKey(INTERVAL_SECONDS)
                    ? intervalMillis(settings.get(INTERVAL_SECONDS)) : millisValue(DEFAULT_MILLIS);
        intervalEnabled = active && Boolean.TRUE.equals(settings.get(INTERVAL_ENABLED));
        // Legacy layout keys remain portable, but 1.7.0 always uses the OEM layout.
        // Font, color, size, placement and sampling remain independent controls.
        String next = "system";
        if (style.equals(next)) return;
        style = next;
        runOnMain(() -> {
            for (View host : new ArrayList<>(views.keySet())) {
                Binding binding = views.get(host);
                if (binding != null) apply(host, binding.number.get(), binding.unit.get());
            }
        });
    }

    public static long intervalMillis(Object value) {
        float seconds = NumericPolicy.finite(value, DEFAULT_SECONDS);
        return Math.max(MIN_INTERVAL_MILLIS, NumericPolicy.milliseconds(seconds, DEFAULT_SECONDS));
    }
    /** A positive sampling interval never becomes a zero-delay busy loop. */
    public static long millisValue(Object value) {
        double millis = NumericPolicy.finite(value, DEFAULT_MILLIS);
        return Math.max(MIN_INTERVAL_MILLIS, Math.min(Long.MAX_VALUE / 8, Math.round(millis)));
    }

    public static String displayStyle(Object value) {
        return "stacked".equals(value) || "inline".equals(value) || "number".equals(value)
                ? (String) value : "system";
    }

    /** Hook the OEM postUpdateNetworkSpeedDelay(long) argument, keeping initial/resume delay 0. */
    public long samplingDelay(long nativeDelay) {
        return nativeDelay > 0L && intervalEnabled && !ModuleLifecycle.removed() ? intervalMillis : nativeDelay;
    }

    /** Before callbacks that rewrite LayoutParams, such as onConfigurationChanged. Not before onLayout. */
    public void beforeNativeLayout(View host) {
        if (host == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        Binding binding = views.get(host);
        if (binding != null && binding.nativeDepth++ == 0) restore(host, binding);
    }

    /** Pair with beforeNativeLayout, or call once after initial inflation. */
    public void afterNativeLayout(View host, TextView number, TextView unit) {
        if (host == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { enqueue(host, number, unit); return; }
        Binding binding = views.get(host);
        if (binding != null) {
            if (binding.nativeDepth > 0 && --binding.nativeDepth > 0) return;
        }
        if (!valid(host, number, unit)) { views.remove(host); return; }
        if (binding != null) {
            if (!binding.owned && binding.number.get() == number && binding.unit.get() == unit)
                binding.capture(host, number, unit);
        }
        apply(host, number, unit);
    }

    /** After native layout/state updates and existing font/size/color controls. Idempotent across draws. */
    public void apply(View host, TextView number, TextView unit) {
        if (!valid(host, number, unit)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { enqueue(host, number, unit); return; }
        Binding binding = views.get(host);
        if (binding != null && (binding.number.get() != number || binding.unit.get() != unit)) {
            restore(host, binding); views.remove(host); binding = null;
        }
        String current = ModuleLifecycle.removed() ? "system" : style;
        if ("system".equals(current)) {
            if (binding != null) restore(host, binding);
            return;
        }
        if (binding == null) { binding = new Binding(host, number, unit); views.put(host, binding); }
        if (binding.nativeDepth > 0) return;
        if (!binding.owned) binding.capture(host, number, unit);
        int numberWidth = textWidth(number), unitWidth = textWidth(unit);
        int gap = Math.max(1, Math.round(NumericPolicy.layoutPixels(2f, host.getResources().getDisplayMetrics().density)));
        int hostPadding = host.getPaddingLeft() + host.getPaddingRight();
        int width;
        if ("inline".equals(current)) {
            width = NumericPolicy.layoutPixels((double) numberWidth + unitWidth + gap + hostPadding);
            install(number, binding.nativeNumber, numberWidth, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START | Gravity.CENTER_VERTICAL, 0, 0, 0);
            install(unit, binding.nativeUnit, unitWidth, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START | Gravity.CENTER_VERTICAL, numberWidth + gap, 0, 0);
        } else if ("number".equals(current)) {
            width = NumericPolicy.layoutPixels((double) numberWidth + hostPadding);
            install(number, binding.nativeNumber, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER, 0, 0, 0);
            installOriginal(unit, binding.nativeUnit);
        } else {
            width = NumericPolicy.layoutPixels((double) Math.max(numberWidth, unitWidth) + hostPadding);
            int numberHeight = textHeight(number, binding.metrics), unitHeight = textHeight(unit, binding.metrics);
            install(number, binding.nativeNumber, ViewGroup.LayoutParams.MATCH_PARENT, numberHeight,
                    Gravity.CENTER, 0, 0, (unitHeight + 1) / 2);
            install(unit, binding.nativeUnit, ViewGroup.LayoutParams.MATCH_PARENT, unitHeight,
                    Gravity.CENTER, 0, (numberHeight + 1) / 2, 0);
        }
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (params.width != width) { params.width = width; host.setLayoutParams(params); }
        int unitVisibility = "number".equals(current) ? View.GONE : binding.nativeUnitVisibility;
        if (unit.getVisibility() != unitVisibility) unit.setVisibility(unitVisibility);
        binding.owned = true;
    }

    public void onViewDetached(View host) {
        if (host == null || Looper.myLooper() != Looper.getMainLooper()) return;
        Binding binding = views.remove(host);
        if (binding != null) restore(host, binding);
    }

    private static boolean valid(View host, TextView number, TextView unit) {
        return host != null && number != null && unit != null && number != unit
                && number.getParent() == host && unit.getParent() == host && host.getLayoutParams() != null
                && number.getLayoutParams() instanceof FrameLayout.LayoutParams
                && unit.getLayoutParams() instanceof FrameLayout.LayoutParams;
    }

    private void enqueue(View host, TextView number, TextView unit) {
        WeakReference<View> owner = new WeakReference<>(host);
        WeakReference<TextView> digits = new WeakReference<>(number), suffix = new WeakReference<>(unit);
        main.post(() -> apply(owner.get(), digits.get(), suffix.get()));
    }

    private void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run(); else main.post(task);
    }

    private static int textWidth(TextView view) {
        CharSequence text = view.getText();
        double width = text == null || text.length() == 0 ? 0d : view.getPaint().measureText(text, 0, text.length());
        return Math.max(1, NumericPolicy.layoutPixels(Math.ceil(width) + view.getPaddingLeft() + view.getPaddingRight()));
    }

    private static int textHeight(TextView view, Paint.FontMetrics metrics) {
        view.getPaint().getFontMetrics(metrics);
        return Math.max(1, NumericPolicy.layoutPixels(Math.ceil(metrics.descent - metrics.ascent)
                + view.getPaddingTop() + view.getPaddingBottom()));
    }

    private static void install(TextView view, FrameLayout.LayoutParams nativeParams, int width, int height,
            int gravity, int start, int top, int bottom) {
        FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (current.width == width && current.height == height && current.gravity == gravity
                && current.getMarginStart() == start && current.getMarginEnd() == 0
                && current.topMargin == top && current.bottomMargin == bottom) return;
        FrameLayout.LayoutParams next = new FrameLayout.LayoutParams(nativeParams);
        next.width = width; next.height = height; next.gravity = gravity;
        next.leftMargin = next.rightMargin = 0; next.topMargin = top; next.bottomMargin = bottom;
        next.setMarginStart(start); next.setMarginEnd(0); view.setLayoutParams(next);
    }

    private static void installOriginal(TextView view, FrameLayout.LayoutParams original) {
        if (!(view.getLayoutParams() instanceof FrameLayout.LayoutParams)
                || !same((FrameLayout.LayoutParams) view.getLayoutParams(), original))
            view.setLayoutParams(new FrameLayout.LayoutParams(original));
    }

    private static boolean same(FrameLayout.LayoutParams a, FrameLayout.LayoutParams b) {
        return a.width == b.width && a.height == b.height && a.gravity == b.gravity
                && a.leftMargin == b.leftMargin && a.topMargin == b.topMargin
                && a.rightMargin == b.rightMargin && a.bottomMargin == b.bottomMargin
                && a.isMarginRelative() == b.isMarginRelative()
                && a.getMarginStart() == b.getMarginStart() && a.getMarginEnd() == b.getMarginEnd();
    }

    private static void restore(View host, Binding binding) {
        if (!binding.owned) return;
        TextView number = binding.number.get(), unit = binding.unit.get();
        if (number != null && number.getParent() == host) installOriginal(number, binding.nativeNumber);
        if (unit != null && unit.getParent() == host) {
            installOriginal(unit, binding.nativeUnit);
            if (unit.getVisibility() != binding.nativeUnitVisibility) unit.setVisibility(binding.nativeUnitVisibility);
        }
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (params != null && params.width != binding.nativeWidth) {
            params.width = binding.nativeWidth; host.setLayoutParams(params);
        }
        binding.owned = false;
    }
}
