// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Read-only lockscreen typography snapshots; no lockscreen view is changed or retained. */
public final class NotificationClockStyle {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final long SEARCH_INTERVAL = 3000L;
    private static final Map<View, Cache> CACHE = new WeakHashMap<>();
    private NotificationClockStyle() { }

    public static final class Style {
        public final Typeface typeface;
        public final String fontVariationSettings, fontFeatureSettings;
        public final float letterSpacing, textSizePx, shadowRadius, shadowDx, shadowDy;
        public final int color, shadowColor;
        public final boolean hasColor, includeFontPadding, fromNativeView, fromSystemResources;

        private Style(TextView sample, Typeface fallback, boolean nativeView, boolean resources) {
            typeface = sample.getTypeface() == null ? face(fallback) : sample.getTypeface();
            fontVariationSettings = sample.getFontVariationSettings();
            fontFeatureSettings = sample.getFontFeatureSettings();
            letterSpacing = sample.getLetterSpacing(); textSizePx = sample.getTextSize();
            color = sample.getCurrentTextColor(); hasColor = true;
            includeFontPadding = sample.getIncludeFontPadding();
            shadowRadius = sample.getShadowRadius(); shadowDx = sample.getShadowDx(); shadowDy = sample.getShadowDy();
            shadowColor = sample.getShadowColor(); fromNativeView = nativeView; fromSystemResources = resources;
        }

        private Style(Context context, Typeface fallback) {
            typeface = face(fallback); fontVariationSettings = null; fontFeatureSettings = "tnum";
            letterSpacing = 0f; textSizePx = 96f * context.getResources().getDisplayMetrics().scaledDensity;
            color = 0xffffffff; hasColor = false; includeFontPadding = false;
            shadowRadius = shadowDx = shadowDy = 0f; shadowColor = 0;
            fromNativeView = fromSystemResources = false;
        }

        private boolean matches(TextView view) {
            return typeface == view.getTypeface() && textSizePx == view.getTextSize()
                    && letterSpacing == view.getLetterSpacing() && color == view.getCurrentTextColor()
                    && includeFontPadding == view.getIncludeFontPadding()
                    && shadowRadius == view.getShadowRadius() && shadowDx == view.getShadowDx()
                    && shadowDy == view.getShadowDy() && shadowColor == view.getShadowColor()
                    && Objects.equals(fontVariationSettings, view.getFontVariationSettings())
                    && Objects.equals(fontFeatureSettings, view.getFontFeatureSettings());
        }
    }

    private static final class Cache {
        WeakReference<TextView> clock = new WeakReference<>(null);
        Style style, resourceStyle;
        Typeface fallback;
        int configuration;
        long nextSearch;
    }

    /** Prefer an existing keyguard clock; otherwise read SystemUI's clock_time_style once. */
    public static synchronized Style resolve(View panel, Typeface fallback) {
        Objects.requireNonNull(panel);
        View root = panel.getRootView();
        if (root == null) root = panel;
        Cache entry = CACHE.get(panel);
        if (entry == null) { entry = new Cache(); CACHE.put(panel, entry); }
        TextView clock = entry.clock.get();
        if (clock != null && clock.getRootView() == root) {
            if (entry.style == null || !entry.style.matches(clock)) entry.style = new Style(clock, fallback, true, false);
            return entry.style;
        }
        int configuration = panel.getResources().getConfiguration().hashCode();
        long now = SystemClock.uptimeMillis();
        if (entry.style == null || now >= entry.nextSearch || entry.configuration != configuration || entry.fallback != fallback) {
            clock = findClock(root, false, 0, new int[]{4096});
            entry.clock = new WeakReference<>(clock);
            if (clock == null) {
                if (entry.resourceStyle == null || entry.configuration != configuration || entry.fallback != fallback)
                    entry.resourceStyle = resourceStyle(panel.getContext(), fallback);
                entry.style = entry.resourceStyle;
            } else entry.style = new Style(clock, fallback, true, false);
            entry.configuration = configuration; entry.fallback = fallback; entry.nextSearch = now + SEARCH_INTERVAL;
        }
        return entry.style;
    }

    private static Style resourceStyle(Context context, Typeface fallback) {
        try {
            Context system = SYSTEM_UI.equals(context.getPackageName()) ? context
                    : context.createPackageContext(SYSTEM_UI, Context.CONTEXT_IGNORE_SECURITY);
            Resources resources = system.getResources();
            int style = resources.getIdentifier("clock_time_style", "style", SYSTEM_UI);
            if (style != 0) {
                TextView sample = new TextView(system, null, 0, style);
                // SingleClockView's XML excludes font padding even though the shared style does not.
                sample.setIncludeFontPadding(false);
                if (sample.getFontFeatureSettings() == null) sample.setFontFeatureSettings("tnum");
                return new Style(sample, fallback, false, true);
            }
        } catch (Exception unavailable) { /* Unavailable OEM resources use natural fallback metrics. */ }
        return new Style(context, fallback);
    }

    /** Reuse the loaded plugin's decorated context without changing its lockscreen views. */
    public static View nativeDigitTemplate(View panel) {
        if (panel == null) return null;
        View root = panel.getRootView();
        return findNativeDigit(root == null ? panel : root, 0, new int[]{4096});
    }

    private static View findNativeDigit(View view, int depth, int[] remaining) {
        if (view == null || depth > 40 || remaining[0]-- <= 0) return null;
        if (view.getClass().getName().equals("com.oplus.keyguard.clock.digital.ui.view.DigitalTimeView"))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findNativeDigit(group.getChildAt(i), depth + 1, remaining);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private static TextView findClock(View view, boolean inClock, int depth, int[] remaining) {
        if (view == null || depth > 40 || remaining[0]-- <= 0) return null;
        String name = view.getClass().getName();
        String id = resourceName(view);
        inClock |= name.contains("OplusKeyguardStyleClock") || name.contains("SingleClockView")
                || name.contains("DualClockView") || name.contains("KeyguardClock") && !name.contains("StatusBar");
        if (view instanceof TextView && (id.equals("clock_time_hour_txt") || id.equals("clock_time_minutes_txt")
                || id.equals("dual_clock_located_time_hour_txt") || id.equals("dual_clock_resident_time_hour_txt")))
            return (TextView) view;
        if (view instanceof TextView && id.equals("visible_digital_time_text_view")
                && hasDigits(((TextView) view).getText())) return (TextView) view;
        TextView best = view instanceof TextView && inClock && hasDigits(((TextView) view).getText()) ? (TextView) view : null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView candidate = findClock(group.getChildAt(i), inClock, depth + 1, remaining);
                if (candidate != null && (best == null || candidate.getTextSize() > best.getTextSize())) best = candidate;
            }
        }
        return best;
    }

    private static boolean hasDigits(CharSequence text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) if (Character.isDigit(text.charAt(i))) return true;
        return false;
    }

    private static String resourceName(View view) {
        try { return view.getId() == View.NO_ID ? "" : view.getResources().getResourceEntryName(view.getId()); }
        catch (Resources.NotFoundException unavailable) { return ""; }
    }

    private static Typeface face(Typeface fallback) { return fallback == null ? Typeface.DEFAULT : fallback; }
}
