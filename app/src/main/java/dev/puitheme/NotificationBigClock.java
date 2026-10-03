// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Event-driven notification-page clock. SystemUI continues owning touch, list scroll and fling. */
public final class NotificationBigClock {
    public interface PageMotion {
        void register(View view);
        void unregister(View view);
        boolean isRunning();
    }
    /** Samples the existing first/last native row springs; it never creates an animator. */
    public interface ReboundReader {
        void sample(View stack, float[] offsets);
    }
    private static final String PREFIX = "notification_big_clock_";
    private volatile Settings portraitSettings = new Settings(null, PREFIX);
    private volatile Settings landscapeSettings = new Settings(null, NotificationBigClockSettings.LANDSCAPE_PREFIX);
    private volatile Settings settings = portraitSettings;
    private final NotificationGroupStack groupStack = new NotificationGroupStack();
    private final android.graphics.Matrix stackMapping = new android.graphics.Matrix();
    private final float[] stackPoint = new float[2];
    private final Rect notificationClip = new Rect();
    private final Map<View, HeaderState> headers = new WeakHashMap<>();
    private final Map<View, StackState> stacks = new WeakHashMap<>();
    private final Map<View, FakeState> fakeClocks = new WeakHashMap<>();
    private final NotificationLandscapeLayout landscapeLayout = new NotificationLandscapeLayout();
    private NotificationClearMotion clearMotion;
    // Compatibility retirement only: this instance never calls show() or owns a RIGHT copy.
    private final StatusBarFixedIcons fixedStatusIcons = new StatusBarFixedIcons();
    private final StatusBarPhoneRightIcons phoneRightIcons = new StatusBarPhoneRightIcons();
    private final StatusBarNotificationRightIcons notificationRightIcons = new StatusBarNotificationRightIcons();
    private final StatusBarPageLeftIcons pageLeftIcons = new StatusBarPageLeftIcons();
    private final StatusBarClosingIcons closingStatusIcons = new StatusBarClosingIcons();
    private final Map<View, FakeState> closingStatusSources = new WeakHashMap<>();
    private WeakReference<View> closingPhoneClock = new WeakReference<>(null);
    private WeakReference<View> closingPhoneNotifications = new WeakReference<>(null);
    private WeakReference<View> panel = new WeakReference<>(null);
    private ClockView overlay;
    private float fraction, overDistance;
    private float nativePanelTranslationY, nativeHeaderRebound, nativeFooterRebound;
    private ReboundReader reboundReader;
    private boolean panelSettledClosed = true;
    // The portrait QS plugin has its own spring and native header; it never drives the clock/footer.
    private WeakReference<View> separateQsPanel = new WeakReference<>(null);
    private WeakReference<View> separateQsIcons = new WeakReference<>(null);
    private WeakReference<View> separateQsFakeIcons = new WeakReference<>(null);
    private WeakReference<View> separateQsPhone = new WeakReference<>(null);
    private float separateQsFraction;
    private boolean separateQsSettledClosed = true;
    private int separateQsBarState = -1;
    private PageMotion pageMotion;
    private int barState = -1, scrollY;
    private boolean qsExpanded, active;
    private float appliedReservation = Float.NaN;
    private Runnable visibilityListener;
    private boolean footerMarginHookAvailable;
    public NotificationBigClock() {
        fixedStatusIcons.setFailureListener(this::refresh);
        fixedStatusIcons.setLeftCopyPolicy(pageLeftIcons);
        closingStatusIcons.setFailureListener(this::restoreClosingSources);
    }
    private final View.OnLayoutChangeListener headerLayoutListener = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if (overlay != null) overlay.measuredSettings = null;
        refresh();
    };

    private static final class Settings {
        final boolean enabled, glass, seconds, dateEnabled, footerEnabled, safeMode, statusIconsEnabled;
        final boolean rightStatusIconsEnabled;
        final boolean glassBorderEnabled, entryEffectEnabled, borderLightAlpha, borderDarkAlpha;
        final float glassBorderWidth, entryBlurRadius, entryFadeStrength, entryCompletion, entryTravel;
        final boolean notificationEdgeEnabled;
        final float notificationEdgeSafeDistance, notificationEdgeRange, notificationEdgeBlurRadius;
        final int borderColorLight, borderColorDark;
        final String pattern, datePattern, font, alignment, dateAlignment, footerAlignment;
        final String footerPattern, footerText;
        final float scale, compactScale, weight, compactWeight, offsetY, offsetX;
        final float maxSize, compactMaxSize, compactOffsetX, compactOffsetY, letterSpacing;
        final float dateSize, dateWeight, dateOffsetX, dateOffsetY, dateGap, notificationGap;
        final boolean notificationWidthEnabled;
        final float notificationWidth;
        final float tailWidth1, tailWidth2, tailWidth3;
        final float footerSize, footerWeight, footerOffsetX, footerOffsetY, footerMargin;
        final int colorLight, colorDark, dateColorLight, dateColorDark, footerColorLight, footerColorDark;
        final boolean clockLightAlpha, clockDarkAlpha, dateLightAlpha, dateDarkAlpha, footerLightAlpha, footerDarkAlpha;
        Settings(Bundle source, String prefix) {
            statusIconsEnabled = bool(source, StatusBarShadeIconSettings.MASTER, false);
            safeMode = SafetyMode.enabled(source);
            enabled = bool(source, prefix + "enabled", false);
            // Either layout policy hides the exact Phone and notification slots until both
            // native panels truly close. The control-center left-page policy is independent.
            rightStatusIconsEnabled = statusIconsEnabled || enabled;
            glass = bool(source, prefix + "glass", true);
            glassBorderEnabled = bool(source, prefix + "glass_border_enabled", true);
            glassBorderWidth = number(source, prefix + "glass_border_width", .65f, 0f, Float.MAX_VALUE);
            borderColorLight = color(source, prefix + "glass_border_color_light", Color.WHITE);
            borderColorDark = color(source, prefix + "glass_border_color_dark", Color.WHITE);
            borderLightAlpha = alpha(source, prefix + "glass_border_color_light", borderColorLight);
            borderDarkAlpha = alpha(source, prefix + "glass_border_color_dark", borderColorDark);
            entryEffectEnabled = bool(source, prefix + "entry_effect_enabled", true);
            entryBlurRadius = number(source, prefix + "entry_blur_radius", 12f, 0f, Float.MAX_VALUE);
            entryFadeStrength = number(source, prefix + "entry_fade_strength", 100f, 0f, 100f);
            entryCompletion = number(source, prefix + "entry_completion", 85f, 1f, 100f);
            entryTravel = number(source, prefix + "entry_travel", 32f, 0f, Float.MAX_VALUE);
            notificationEdgeEnabled = bool(source, prefix + "notification_edge_enabled", true);
            notificationEdgeSafeDistance = number(source, prefix + "notification_edge_safe_distance", 18f, 0f, Float.MAX_VALUE);
            notificationEdgeRange = number(source, prefix + "notification_edge_range", 24f, 0f, Float.MAX_VALUE);
            notificationEdgeBlurRadius = number(source, prefix + "notification_edge_blur_radius", 8f, 0f, Float.MAX_VALUE);
            pattern = pattern(source, prefix + "pattern", "HH:mm");
            datePattern = pattern(source, prefix + "date_pattern", NotificationBigClockSettings.DATE_DEFAULT);
            dateEnabled = bool(source, prefix + "date_enabled", true);
            footerEnabled = bool(source, prefix + "footer_enabled", false);
            String footerFormat = string(source, prefix + "footer_pattern", "{text}");
            footerPattern = TimeFormat.contentValidationError(footerFormat) == null ? footerFormat : "{text}";
            footerText = string(source, prefix + "footer_text", "");
            seconds = TimeFormat.hasSeconds(pattern) || (dateEnabled && TimeFormat.hasSeconds(datePattern))
                    || (footerEnabled && TimeFormat.contentHasSeconds(footerPattern));
            scale = number(source, prefix + "scale", 100f, 0f, Float.MAX_VALUE);
            compactScale = number(source, prefix + "compact_scale", 36f, 0f, Float.MAX_VALUE);
            weight = number(source, prefix + "weight", 600f, 1f, 1000f);
            // Retain the legacy preference in exports; collapsing never changes the selected weight.
            compactWeight = weight;
            offsetY = number(source, prefix + "offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            offsetX = number(source, prefix + "offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            maxSize = number(source, prefix + "max_size", 180f, 0f, Float.MAX_VALUE);
            compactMaxSize = number(source, prefix + "compact_max_size", 64f, 0f, Float.MAX_VALUE);
            compactOffsetX = number(source, prefix + "compact_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            compactOffsetY = number(source, prefix + "compact_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            letterSpacing = number(source, prefix + "letter_spacing", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateSize = number(source, prefix + "date_size", 15f, 0f, Float.MAX_VALUE);
            dateWeight = number(source, prefix + "date_weight", 600f, 1f, 1000f);
            dateOffsetX = number(source, prefix + "date_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateOffsetY = number(source, prefix + "date_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateGap = number(source, prefix + "date_gap", 12f, 0f, Float.MAX_VALUE);
            notificationGap = bool(source, prefix + "notification_gap_enabled", true)
                    ? number(source, prefix + "notification_gap", 18f, 0f, Float.MAX_VALUE) : 0f;
            notificationWidthEnabled=NotificationBigClockSettings.LANDSCAPE_PREFIX.equals(prefix)
                    &&bool(source,NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,false);
            notificationWidth=number(source,NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH,100f,0f,Float.MAX_VALUE);
            tailWidth1 = number(source, prefix + "tail_width_1", 96f, 0f, Float.MAX_VALUE);
            tailWidth2 = number(source, prefix + "tail_width_2", 92f, 0f, Float.MAX_VALUE);
            tailWidth3 = number(source, prefix + "tail_width_3", 88f, 0f, Float.MAX_VALUE);
            footerSize = number(source, prefix + "footer_size", 13f, 0f, Float.MAX_VALUE);
            footerWeight = number(source, prefix + "footer_weight", 400f, 1f, 1000f);
            footerOffsetX = number(source, prefix + "footer_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            footerOffsetY = number(source, prefix + "footer_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            footerMargin = number(source, prefix + "footer_margin", 24f, 0f, Float.MAX_VALUE);
            font = string(source, prefix + "font", "native");
            alignment = string(source, prefix + "alignment", "center");
            dateAlignment = string(source, prefix + "date_alignment", "center");
            footerAlignment = string(source, prefix + "footer_alignment", "center");
            colorLight = color(source, prefix + "color_light", Color.WHITE);
            colorDark = color(source, prefix + "color_dark", Color.WHITE);
            dateColorLight = color(source, prefix + "date_color_light", Color.WHITE);
            dateColorDark = color(source, prefix + "date_color_dark", Color.WHITE);
            footerColorLight = color(source, prefix + "footer_color_light", Color.WHITE);
            footerColorDark = color(source, prefix + "footer_color_dark", Color.WHITE);
            clockLightAlpha = alpha(source, prefix + "color_light", colorLight);
            clockDarkAlpha = alpha(source, prefix + "color_dark", colorDark);
            dateLightAlpha = alpha(source, prefix + "date_color_light", dateColorLight);
            dateDarkAlpha = alpha(source, prefix + "date_color_dark", dateColorDark);
            footerLightAlpha = alpha(source, prefix + "footer_color_light", footerColorLight);
            footerDarkAlpha = alpha(source, prefix + "footer_color_dark", footerColorDark);
        }
        private static Object value(Bundle source, String key) { return source == null ? null : source.get(key); }
        private static boolean bool(Bundle source, String key, boolean fallback) {
            Object value = value(source, key); return value instanceof Boolean ? (Boolean) value : fallback;
        }
        private static float number(Bundle source, String key, float fallback, float min, float max) {
            Object value = value(source, key);
            return NotificationBigClockModel.bounded(value instanceof Number ? ((Number) value).floatValue()
                    : fallback, fallback, min, max);
        }
        private static int color(Bundle source, String key, int fallback) {
            Object value = value(source, key); return value instanceof Number ? ((Number) value).intValue() : fallback;
        }
        private static boolean alpha(Bundle source, String key, int color) {
            return bool(source, StatusBarSettings.alphaKey(key), Color.alpha(color) != 255);
        }
        private static String pattern(Bundle source, String key, String fallback) {
            Object value = value(source, key);
            return value instanceof String && TimeFormat.validationError((String) value) == null ? (String) value : fallback;
        }
        private static String string(Bundle source, String key, String fallback) {
            Object value = value(source, key); return value instanceof String ? (String) value : fallback;
        }
    }

    private static final class HeaderState {
        final WeakReference<TextView> clock, date;
        final WeakReference<View> carrier;
        final WeakReference<View> statusIcons, settingsButton;
        WeakReference<View> fakeStatusIcons = new WeakReference<>(null);
        WeakReference<View> phoneStatusIcons = new WeakReference<>(null);
        boolean hidden, buttonHidden;
        float dateAlpha, carrierAlpha;
        int nativeButtonVisibility;
        HeaderState(View header) {
            clock = new WeakReference<>(textView(header, "qs_footer_clock"));
            date = new WeakReference<>(textView(header, "oplus_date"));
            carrier = new WeakReference<>(childView(header, "qs_carrier_text"));
            statusIcons = new WeakReference<>(childView(header, "quick_qs_status_icons"));
            settingsButton = new WeakReference<>(childView(header, "settings_button"));
        }
        void hide() {
            TextView d = date.get();
            View operator = carrier.get();
            // Native transition writers may change alpha after our last frame; retain their latest value.
            if (!hidden || (d != null && d.getAlpha() != 0f)) dateAlpha = d == null ? 1f : d.getAlpha();
            if (!hidden || (operator != null && operator.getAlpha() != 0f)) carrierAlpha = operator == null ? 1f : operator.getAlpha();
            hidden = true;
            if (d != null) d.setAlpha(0f);
            if (operator != null) operator.setAlpha(0f);
            View button = settingsButton.get();
            if (button != null) {
                if (!buttonHidden || button.getVisibility() != View.INVISIBLE)
                    nativeButtonVisibility = button.getVisibility();
                buttonHidden = true; button.setVisibility(View.INVISIBLE);
            }
        }
        void restore() {
            if (!hidden) return;
            TextView d = date.get();
            if (d != null) d.setAlpha(dateAlpha);
            View operator = carrier.get();
            if (operator != null) operator.setAlpha(carrierAlpha);
            View button = settingsButton.get();
            if (button != null && buttonHidden && button.getVisibility() == View.INVISIBLE)
                button.setVisibility(nativeButtonVisibility);
            buttonHidden = false;
            hidden = false;
        }
    }

    private static final class StackState {
        float rawTop = Float.NaN, appliedTop = Float.NaN;
        int rawIntrinsic = Integer.MIN_VALUE, appliedIntrinsic = Integer.MIN_VALUE;
        boolean restoring;
        float appliedFooter;
    }

    private static final class FakeState {
        float alpha, x, y, scaleX, scaleY;
        float appliedAlpha, appliedX, appliedY, appliedScaleX, appliedScaleY;
        boolean overridden;
        FakeState(View view) {
            alpha = view.getAlpha(); x = view.getTranslationX(); y = view.getTranslationY();
            scaleX = view.getScaleX(); scaleY = view.getScaleY();
        }
        void captureNative(View view) {
            if (!overridden || view.getAlpha() != appliedAlpha) alpha = view.getAlpha();
            if (!overridden || view.getTranslationX() != appliedX) x = view.getTranslationX();
            if (!overridden || view.getTranslationY() != appliedY) y = view.getTranslationY();
            if (!overridden || view.getScaleX() != appliedScaleX) scaleX = view.getScaleX();
            if (!overridden || view.getScaleY() != appliedScaleY) scaleY = view.getScaleY();
        }
        void markApplied(View view) {
            appliedAlpha = view.getAlpha(); appliedX = view.getTranslationX(); appliedY = view.getTranslationY();
            appliedScaleX = view.getScaleX(); appliedScaleY = view.getScaleY(); overridden = true;
        }
        void restore(View view, boolean transforms) {
            if (view.getAlpha() == appliedAlpha) view.setAlpha(alpha);
            if (transforms) {
                if (view.getTranslationX() == appliedX) view.setTranslationX(x);
                if (view.getTranslationY() == appliedY) view.setTranslationY(y);
                if (view.getScaleX() == appliedScaleX) view.setScaleX(scaleX);
                if (view.getScaleY() == appliedScaleY) view.setScaleY(scaleY);
            }
        }
    }

    /** May be called when settings are reloaded; view work is dispatched through the existing panel. */
    public void configure(Bundle source) {
        portraitSettings = new Settings(source, PREFIX);
        landscapeSettings = new Settings(source, NotificationBigClockSettings.LANDSCAPE_PREFIX);
        selectOrientation();
        groupStack.configure(source);
        // Retire the previous fixed-row policy before a native Phone-only alpha owner may run.
        fixedStatusIcons.setPhoneCaptureAllowed(false);
        fixedStatusIcons.hide();
        // The control-center fake/header already owns the real Phone RIGHT handoff.
        // A second force-zero owner breaks that native render transition at both endpoints.
        phoneRightIcons.configure(false);
        notificationRightIcons.configure(settings.rightStatusIconsEnabled && !settings.safeMode,
                settings.enabled && !settings.safeMode);
        restoreClosingSources();
        if (settings.safeMode || !settings.statusIconsEnabled && !settings.enabled)
            pageLeftIcons.restoreNotificationCopies();
        if (ModuleLifecycle.removed()) {
            fixedStatusIcons.releaseRuntime();
            phoneRightIcons.releaseRuntime();
            notificationRightIcons.releaseRuntime();
            pageLeftIcons.release();
            clearSeparateQsStatus();
        }
        fixedStatusIcons.configureDiagnostics();
        View host = panel.get();
        if (host == null) host = separateQsPanel.get();
        if (host != null) host.post(() -> {
            fixedStatusIcons.resetFailure();
            closingStatusIcons.resetFailure();
            if (overlay != null) overlay.clearStyle();
            for (View stack : stacks.keySet()) {
                if (!managesLandscape(stack)) landscapeLayout.release(stack);
                else stack.requestLayout();
            }
            boolean wasActive = active;
            refresh();
            if (wasActive == active) notifyVisibilityChanged();
        });
    }

    private void selectOrientation() {
        Settings previous=settings;
        View host=panel.get();
        settings=landscape(host)?landscapeSettings:portraitSettings;
        if(previous!=settings&&overlay!=null)overlay.clearStyle();
        if(previous!=settings)notificationRightIcons.configure(settings.rightStatusIconsEnabled&&!settings.safeMode,
                settings.enabled&&!settings.safeMode);
    }
    public boolean notificationPage() {
        return !settings.safeMode && !ModuleLifecycle.removed() && barState==0 && !qsExpanded && fraction>0f;
    }
    /** Native shade target calculation precedes the clock/fake-header visibility callbacks.
     * Retain its stack parameters for that whole scene, including a hidden/reopening page;
     * the real shade ruler identity still excludes keyguard inside NotificationNativeStack. */
    public boolean notificationStackScene() {
        return !settings.safeMode&&!ModuleLifecycle.removed()&&barState==0;
    }
    /** Backdrop/clock owners must not use the broader hook gate which also includes groups. */
    public boolean clockLayoutEnabled(View host) {
        Settings selected=landscape(host)?landscapeSettings:portraitSettings;
        return selected.enabled&&!selected.safeMode&&!ModuleLifecycle.removed();
    }
    public void prepareGroup(View container) { groupStack.prepareNative(container,true); }
    /** Order changes precede the native measurement of its cached first-card height. */
    public void prepareGroupMeasure(View container) { groupStack.prepareMeasure(container); }
    public void prepareGroup(View container,boolean calculatingTargets) {
        groupStack.prepareNative(container,calculatingTargets);
    }
    public int nativeGroupVisibleCount(View container,int count) { return groupStack.visibleChildren(container,count); }
    public void detachGroup(View container) { groupStack.detach(container); }
    public String groupStackDiagnostics() { return groupStack.diagnosticSummary(); }

    /** Hook OplusQSSimpleHeader.onInit / onFinishInflate after native child initialization. */
    public void onHeaderInflated(View header) {
        if (header == null) return;
        notificationRightIcons.detached(header);
        HeaderState previous = headers.remove(header);
        if (previous != null) {
            previous.restore();
            View icons = previous.statusIcons.get();
            fixedStatusIcons.hide();
            if (icons != null) icons.removeOnLayoutChangeListener(headerLayoutListener);
        }
        header.removeOnLayoutChangeListener(headerLayoutListener);
        HeaderState state = new HeaderState(header);
        if (previous != null) {
            state.fakeStatusIcons = previous.fakeStatusIcons;
            state.phoneStatusIcons = previous.phoneStatusIcons;
        }
        headers.put(header, state);
        notificationRightIcons.clock(header, state.clock.get());
        if (state.fakeStatusIcons.get() != null)
            notificationRightIcons.bind(header, state.statusIcons.get(), state.fakeStatusIcons.get(), state.phoneStatusIcons.get());
        header.addOnLayoutChangeListener(headerLayoutListener);
        View icons = state.statusIcons.get();
        if (icons != null) icons.addOnLayoutChangeListener(headerLayoutListener);
        if (overlay != null) overlay.measuredSettings = null;
        refresh();
    }

    /** Reapply only the replaced header labels after native transition/style writes. */
    public void onHeaderNativeStyleWritten(View header) {
        HeaderState state = headers.get(header);
        if (state != null) refresh();
    }

    /** Native click/power policy can write the same INVISIBLE value that the module owns. */
    public void onHeaderButtonVisibilityWritten(View header) {
        HeaderState state = headers.get(header);
        if (state != null) {
            View button = state.settingsButton.get();
            if (button != null) state.nativeButtonVisibility = button.getVisibility();
        }
        onHeaderNativeStyleWritten(header);
    }

    public void onHeaderDetached(View header) {
        notificationRightIcons.detached(header);
        HeaderState state = headers.remove(header);
        header.removeOnLayoutChangeListener(headerLayoutListener);
        if (state != null) {
            state.restore();
            View icons = state.statusIcons.get();
            fixedStatusIcons.hide();
            if (icons != null) icons.removeOnLayoutChangeListener(headerLayoutListener);
        }
        if (headers.isEmpty()) { restore(); detachOverlay(); }
        else refresh();
    }

    /** Only barState=0, portrait and the notification page may draw this clock. */
    public void onPanelMotionState(boolean running, boolean settledClosed) {
        panelSettledClosed = settledClosed && !running;
        refreshStatusIcons();
        if (statusIconsSettledClosed()) {
            groupStack.resetGesture();
        }
    }

    public void setReboundReader(ReboundReader reader) { reboundReader = reader; }
    public void setClearMotion(NotificationClearMotion motion) { clearMotion = motion; }

    private boolean landscape(View view) {
        return view != null && view.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
    private boolean managesLandscape(View stack) {
        return settings.enabled && !settings.safeMode && !ModuleLifecycle.removed()
                && landscape(stack) && (barState == 0 || barState == -1);
    }
    public int notificationWidthSpec(View stack, int nativeSpec) {
        float width=landscapeSettings.notificationWidthEnabled?landscapeSettings.notificationWidth:100f;
        return landscapeLayout.measure(stack, nativeSpec, managesLandscape(stack),width);
    }
    public void onNotificationLayout(View stack) {
        landscapeLayout.layout(stack, managesLandscape(stack));
        if (overlay != null) {
            overlay.measuredSettings = null;
            overlay.scheduleWidgets();
        }
    }

    /** Called after the native vertical spring has updated its existing notification/QS views. */
    public void onPanelTranslationChanged(View nativePanel, float translationY) {
        if (panel.get() != nativePanel) return;
        float next = NotificationBigClockModel.finite(translationY) ? translationY : 0f;
        if (nativePanelTranslationY == next) return;
        nativePanelTranslationY = next;
        if (active && overlay != null) overlay.scheduleWidgets();
    }

    /** Use the same progressive spring session as native notification and QS content. */
    public void onPageMotionReady(PageMotion motion) {
        if (pageMotion != null && pageMotion != motion && overlay != null) overlay.unregisterMotion(pageMotion);
        pageMotion = motion;
        if (overlay != null) {
            if (motion != null && active) overlay.registerMotion(motion);
            else overlay.resetMotion();
        }
    }

    /** Only barState=0, portrait and the notification page may draw this clock. */
    public void onPanelChanged(View nativePanel, float expandedFraction, int nativeBarState, boolean inQs) {
        View old = panel.get();
        if (old != nativePanel) {
            restore();
            detachOverlay();
            panel = new WeakReference<>(nativePanel);
            overlay = null; scrollY = 0; overDistance = 0f;
            nativePanelTranslationY = nativeHeaderRebound = nativeFooterRebound = 0f;
        }
        selectOrientation();
        fraction = NotificationBigClockModel.bounded(expandedFraction, 0f, 0f, 1f);
        boolean changedState = barState != nativeBarState;
        barState = nativeBarState; qsExpanded = inQs;
        if (changedState) for (View stack : stacks.keySet()) {
            if (!managesLandscape(stack)) landscapeLayout.release(stack);
            else stack.requestLayout();
        }
        refresh();
    }

    /** Read absolute ownScrollY and absolute Ext.overDistance after their native writers. */
    public void onScrollChanged(View stack, int ownScrollY, float absoluteOverDistance) {
        if (stack != null && !stacks.containsKey(stack)) stacks.put(stack, new StackState());
        scrollY = ownScrollY;
        overDistance = NotificationBigClockModel.bounded(absoluteOverDistance, 0f, -12000f, 12000f);
        if (active && overlay != null) {
            overlay.scheduleWidgets();
            // Native child RenderNode translations can otherwise reuse an earlier parent clip.
            if (stack != null) stack.invalidate();
        }
    }

    /** Before NSSL.updateTopPadding(float,boolean). The initial reservation stays fixed while scrolling. */
    public float adjustTopPadding(View stack, float nativePadding) {
        if (stack == null) return nativePadding;
        StackState state = stackState(stack);
        if (state.restoring) return nativePadding;
        float raw = nativePadding == state.appliedTop ? state.rawTop : nativePadding;
        if (!NotificationBigClockModel.finite(raw)) raw = nativePadding;
        state.rawTop = raw;
        state.appliedTop = eligible() ? Math.max(raw, reservationFor(stack)) : raw;
        return state.appliedTop;
    }

    /** Before NSSL.setIntrinsicPadding(int); the extra area also contributes to native scroll range. */
    public int adjustIntrinsicPadding(View stack, int nativePadding) {
        if (stack == null) return nativePadding;
        StackState state = stackState(stack);
        if (state.restoring) return nativePadding;
        int raw = nativePadding == state.appliedIntrinsic ? state.rawIntrinsic : nativePadding;
        if (raw == Integer.MIN_VALUE) raw = nativePadding;
        state.rawIntrinsic = raw;
        state.appliedIntrinsic = eligible() ? Math.max(raw, Math.round(reservationFor(stack))) : raw;
        return state.appliedIntrinsic;
    }

    /** After NSSL.getScrollRange(): even a short list can consume the clock's collapse area. */
    public int adjustScrollRange(View stack, int nativeRange) {
        if (stack == null || !eligible()) return nativeRange;
        int withFooter = (int) Math.min(Integer.MAX_VALUE, (long) nativeRange + Math.round(footerReservation(stack)));
        return Math.max(withFooter, (int) Math.ceil(geometry().collapseDistance));
    }

    /** Native shelf/scroll bounds reserve an actual bottom strip for custom content. */
    public int adjustEmptyBottomMargin(View stack, int nativeMargin) {
        return eligible() ? Math.max(nativeMargin, Math.round(footerReservation(stack))) : nativeMargin;
    }

    public void onFooterMarginHookAvailable(boolean available) { footerMarginHookAvailable = available; }

    private float footerReservation(View stack) {
        if (!eligible() || !footerMarginHookAvailable || !settings.footerEnabled || overlay == null) return 0f;
        overlay.prepareMeasurements();
        if (overlay.footer.isEmpty()) return 0f;
        View host = panel.get();
        float boundary = stackY(stack, overlay.desiredFooterTop() - 12f * density());
        float top = Math.max(clipTop(stack) + 48f * density(), boundary);
        return Math.max(0f, stack.getHeight() - top);
    }

    private float reservationFor(View stack) {
        View host = panel.get();
        if (host == null || stack == null) return geometry().reservedBottom;
        return Math.max(0f, NumericPolicy.drawPixels(stackY(stack, geometry().reservedBottom)));
    }

    /** Stack-local clipping keeps notifications underneath the visible clock without moving the list. */
    public float clipTop(View stack) {
        View host = panel.get();
        float hardTop = headerBottomInStack(stack);
        if (!NotificationBigClockModel.finite(hardTop) || host == null) return hardTop;
        // Map the safety gap through the same native panel transform as the hard boundary.
        return hardTop + stackY(stack, NumericPolicy.pixels(settings.notificationGap, density())) - stackY(stack, 0f);
    }

    /** Notifications may enter the clear safety band, but never draw over the clock itself. */
    private float headerBottomInStack(View stack) {
        View host = panel.get();
        if (stack == null || host == null || !eligible()) return Float.NaN;
        // Read the views that will actually draw this frame. Native row springs can update
        // after our pre-draw listener; the previous layout's cached edge must not cut new ink.
        float drawnBottom = overlay == null ? Float.NaN : overlay.headerBottomIn(stack);
        if (NotificationBigClockModel.finite(drawnBottom)) return Math.max(0f, drawnBottom);
        NotificationBigClockModel.Frame frame = geometry();
        float contentBottom = overlay != null && NotificationBigClockModel.finite(overlay.visibleHeaderBottom)
                ? overlay.visibleHeaderBottom
                : Math.max(frame.clockTop + frame.clockHeight, frame.dateTop + (overlay == null ? 0f : overlay.dateHeight))
                    + entryTranslation() + nativePanelTranslationY + nativeHeaderRebound;
        return Math.max(0f, stackY(stack, contentBottom));
    }

    /** Map the visible clock edge into stack-local coordinates through the native page transforms. */
    private float stackY(View stack, float panelY) {
        View host = panel.get();
        if (host == null || stack == null) return panelY;
        stackMapping.reset();
        host.transformMatrixToGlobal(stackMapping); stack.transformMatrixToLocal(stackMapping);
        stackPoint[0]=host.getWidth()*.5f;stackPoint[1]=panelY;
        stackMapping.mapPoints(stackPoint);
        return NotificationBigClockModel.finite(stackPoint[1]) ? stackPoint[1] : panelY;
    }

    /** Before the native stack's View.draw(Canvas), including its hardware RenderNode path. */
    public void clipNotifications(View stack, Canvas canvas) {
        if (canvas == null || stack == null) return;
        float top = headerBottomInStack(stack);
        if (NotificationBigClockModel.finite(top)) {
            if (canvas.getClipBounds(notificationClip)) canvas.clipRect(notificationClip.left, top, notificationClip.right, notificationClip.bottom);
        }
    }

    /** The spatial edge remains stable when scrolling stops, and only processes notification pixels. */
    public Object drawNotifications(View stack, Canvas canvas, boolean horizontalSwitch,
            NotificationClockEdge.DrawAction nativeDraw) throws Throwable {
        float top = headerBottomInStack(stack);
        View host = panel.get();
        // A native fling can keep row springs moving after the parent already reaches its target.
        boolean paging = horizontalSwitch || pageMotion != null && pageMotion.isRunning()
                || host != null && (Math.abs(host.getTranslationX()) > .5f
                    || host.getScaleX() != 1f || host.getScaleY() != 1f)
                || stack != null && (Math.abs(stack.getTranslationX()) > .5f
                    || stack.getScaleX() != 1f || stack.getScaleY() != 1f)
                || overlay != null && overlay.hasPageOffset();
        if (paging && scrollY <= 0) return nativeDraw.draw(canvas);
        if (!NotificationBigClockModel.finite(top)) return nativeDraw.draw(canvas);
        float d = density();
        float safeGap = stackY(stack, NumericPolicy.pixels(settings.notificationEdgeSafeDistance, d)) - stackY(stack, 0f);
        return NotificationClockEdge.draw(stack, canvas, top, top + Math.max(0f, safeGap),
                settings.notificationEdgeEnabled ? NumericPolicy.pixels(settings.notificationEdgeRange, d) : 0f,
                settings.notificationEdgeEnabled ? NumericPolicy.pixels(settings.notificationEdgeBlurRadius, d) : 0f,
                nativeDraw, !paging);
    }

    /** The native pager asks only about its start point; rows keep their own native swipe handler. */
    public boolean notificationTouch(View origin, float x, float y, NotificationCardTouchBoundary boundary)
            throws ReflectiveOperationException {
        View host = panel.get();
        if (!active || !eligible() || host == null || pageMotion != null && pageMotion.isRunning()
                || Math.abs(host.getTranslationX()) > .5f || host.getScaleX() != 1f || host.getScaleY() != 1f)
            return false;
        for (View stack : new ArrayList<>(stacks.keySet())) {
            boolean current = false;
            for (View ancestor = stack; ancestor != null;
                    ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null) {
                if (ancestor == host) { current = true; break; }
            }
            if (current && boundary.contains(stack, origin, x, y, headerBottomInStack(stack))) return true;
        }
        return false;
    }

    /** Called before native UP dispatch, only for the actual NSSL receiving the gesture. */
    public boolean foldedNotificationTap(View stack, android.view.MotionEvent event,
            NotificationCardTouchBoundary boundary) throws ReflectiveOperationException {
        return groupStack.tap(stack,event);
    }

    public void expandNotifications() {
        groupStack.expandPending();
    }

    /** Native clocks and the shared tick can update together; format each time bucket once. */
    public void onNativeClockUpdated(View clock) {
        if (active && overlay != null) {
            if (overlay.refreshText(false)) refresh();
            else overlay.scheduleWidgets(); // Native tint/style writes still reach this draw.
        }
    }

    /** Explicit calendar/locale events can change displayed text inside the same time bucket. */
    public void onTimeChanged() {
        if (active && overlay != null) { overlay.refreshText(true); refresh(); }
    }

    /** Existing SystemUI second-tick sources can share this value; this helper schedules no tasks. */
    public boolean usesSeconds() { return settings.enabled && settings.seconds; }

    public boolean hasVisibleTime() {
        View host = panel.get();
        return active && eligible() && host != null && host.isShown() && host.getAlpha() > 0f
                && (host.getWidth() <= 0 || Math.abs(host.getTranslationX()) < host.getWidth());
    }

    /** The shared clock scheduler is refreshed only by visibility transitions or new settings. */
    public void setVisibilityListener(Runnable listener) { visibilityListener = listener; }

    private void notifyVisibilityChanged() {
        Runnable listener = visibilityListener;
        if (listener != null) listener.run();
    }

    /** After the native fraction listener, pass fakeClockViewContainer and its present fraction. */
    public void onFakeClockChanged(View fakeClock, float presentFraction) {
        onFakeClockChanged(fakeClock, presentFraction, null);
    }

    public void onFakeClockChanged(View fakeClock, float presentFraction, View phoneClock) {
        if (fakeClock == null) return;
        if (eligible()) for (HeaderState header : new ArrayList<>(headers.values())) header.hide();
        refreshStatusIcons();
        pageLeftIcons.notification(fakeClock, phoneClock, hideNotificationLeft(), StatusIconTransition.CLOCK);
    }

    /** Bind all native right-side sources independently of the notification clock feature. */
    public void onFakeStatusChanged(View group, View header) {
        onFakeStatusChanged(group, header, null);
    }

    public void onFakeStatusChanged(View group, View header, View phoneAnchor) {
        if (phoneAnchor != null) phoneRightIcons.bind(phoneAnchor);
        HeaderState source = headers.get(header);
        if (source != null && source.fakeStatusIcons.get() != group)
            source.fakeStatusIcons = new WeakReference<>(group);
        if (source != null && source.phoneStatusIcons.get() != phoneAnchor)
            source.phoneStatusIcons = new WeakReference<>(phoneAnchor);
        if (source != null && source.statusIcons.get() != separateQsIcons.get()
                && group != separateQsFakeIcons.get())
            notificationRightIcons.bind(header, source.statusIcons.get(), group, phoneAnchor);
        refreshStatusIcons();
    }

    /** Exact QS plugin callback. It updates Phone opacity only, never native shade RIGHT/layout. */
    public void onSeparateQsStatusChanged(View nativePanel, View icons, View phone, View fake,
            float displayedFraction, int nativeBarState, boolean settledClosed) {
        if (ModuleLifecycle.removed()) {
            clearSeparateQsStatus(); fixedStatusIcons.releaseRuntime(); phoneRightIcons.releaseRuntime(); return;
        }
        if (nativePanel == null || !NotificationBigClockModel.finite(displayedFraction)) return;
        if (separateQsPanel.get() != nativePanel) separateQsPanel = new WeakReference<>(nativePanel);
        if (separateQsIcons.get() != icons) separateQsIcons = new WeakReference<>(icons);
        if (separateQsFakeIcons.get() != fake) separateQsFakeIcons = new WeakReference<>(fake);
        if (separateQsPhone.get() != phone) separateQsPhone = new WeakReference<>(phone);
        separateQsFraction = NotificationBigClockModel.clamp(displayedFraction, 0f, 1f);
        separateQsBarState = nativeBarState;
        separateQsSettledClosed = settledClosed;
        if (phone != null) phoneRightIcons.bind(phone);
        refreshStatusIcons();
    }

    public void onSeparateQsStatusDetached(View nativePanel) {
        if (separateQsPanel.get() != nativePanel) return;
        clearSeparateQsStatus();
        refreshStatusIcons();
    }

    private void clearSeparateQsStatus() {
        separateQsPanel.clear(); separateQsIcons.clear(); separateQsFakeIcons.clear(); separateQsPhone.clear();
        separateQsFraction = 0f; separateQsSettledClosed = true; separateQsBarState = -1;
    }

    private boolean statusIconsSettledClosed() {
        return panelSettledClosed && separateQsSettledClosed && (pageMotion == null || !pageMotion.isRunning());
    }

    private int statusIconsBarState() {
        if (!separateQsSettledClosed && separateQsBarState != 0) return separateQsBarState;
        if (barState > 0) return 1;
        return barState == 0 || separateQsBarState == 0 ? 0 : -1;
    }

    public void setStatusIconHorizontalReader(StatusBarFixedIcons.HorizontalProgressReader reader) {
        fixedStatusIcons.setHorizontalProgressReader(reader);
    }

    public void setStatusIconTileReader(StatusBarFixedIcons.HorizontalProgressReader reader) {
        fixedStatusIcons.setTileProgressReader(reader);
    }

    public void setStatusIconTraceListener(StatusBarFixedIcons.CopyTraceListener listener) {
        fixedStatusIcons.setCopyTraceListener(listener);
    }

    public void resetStatusIconTrace() { fixedStatusIcons.resetCopyTrace(); }
    public void setStatusIconCopyInspector(StatusBarFixedIcons.NativeCopyInspector inspector) {
        fixedStatusIcons.setNativeCopyInspector(inspector);
    }

    public void onStatusIconHorizontalProgressChanged() {
        refreshStatusIcons();
    }

    public void onSeparateQsLeftChanged(View nativePanel, View clockContainer, View clockCopy, View phoneClock,
            View notificationContainer, View notificationCopy, View phoneNotifications,
            int nativeBarState, boolean settledClosed) {
        // The original SeparateQSFakeStatusController owns LEFT exactly as it owns RIGHT.
    }
    public void onSeparateQsLeftDetached(View nativePanel) { }
    public float statusIconLeftTransitionAlpha(View target, float nativeAlpha) {
        return nativeAlpha;
    }
    public float statusIconNotificationTransitionAlpha(View target, float nativeAlpha) {
        return nativeAlpha;
    }

    public void onStatusIconCopyBound(View copy, View copiedSource, int kind) {
        if ((kind == StatusIconTransition.CLOCK || kind == StatusIconTransition.NOTIFICATIONS)
                && pageLeftIcons.notificationSourceBound(copy, copiedSource)) {
            refreshStatusIcons();
        }
        if (kind == StatusIconTransition.RIGHT) notificationRightIcons.copyBound(copy, copiedSource);
        if (kind == StatusIconTransition.RIGHT && copiedSource != null) try {
            if (copiedSource.getId() > 0 && "status_bar_end_side_container_for_fake".equals(
                    copiedSource.getResources().getResourceEntryName(copiedSource.getId()))) {
                phoneRightIcons.bind(copiedSource);
                refreshStatusIcons();
            }
        } catch (Throwable ignored) { }
    }

    public boolean suppressStatusIconCopy(View copy, View copiedSource, int kind) {
        if (kind == StatusIconTransition.RIGHT) return notificationRightIcons.suppressCopy(copy, copiedSource);
        int state = statusIconsBarState();
        if (settings.rightStatusIconsEnabled && !settings.safeMode && state == 0
                && pageLeftIcons.suppressNotification(copy, copiedSource)) return true;
        return false;
    }

    public float statusIconPhoneRightAlpha(View target, float nativeAlpha) {
        float value = phoneRightIcons.nativeAlpha(target, nativeAlpha);
        return pageLeftIcons.notificationAlpha(target, notificationRightIcons.nativeAlpha(target, value));
    }

    public boolean ownsNotificationRightCopy(View copy) { return notificationRightIcons.ownsCopy(copy); }
    public int nativeNotificationHeaderVisibility(View target, int value) {
        return notificationRightIcons.nativeVisibility(target, value);
    }
    public float statusIconPhoneLeftTranslationY(View target, float value) {
        return value;
    }
    public void onNotificationFakeParents(View moving, View fixed, boolean clockGone) {
        // Native parent visibility and the isFakeClockGone policy are never overridden.
    }

    public void onFakeCarrierChanged(View carrier, float presentFraction) {
        if (carrier == null) return;
        float f = NotificationBigClockModel.bounded(presentFraction, 0f, 0f, 1f);
        if (!eligible()) {
            FakeState previous = fakeClocks.remove(carrier);
            if (previous != null) previous.restore(carrier, false);
            return;
        }
        FakeState state = fakeState(carrier);
        carrier.setAlpha(0f);
        state.markApplied(carrier);
    }

    /** The animated left notification copy is replaced with the notification page, not the real icons. */
    public void onFakeNotificationChanged(View notificationCopy) {
        onFakeNotificationChanged(notificationCopy, null);
    }

    public void onFakeNotificationChanged(View notificationCopy, View phoneNotifications) {
        onFakeNotificationChanged(notificationCopy, phoneNotifications, fraction);
    }

    public void onFakeNotificationChanged(View notificationCopy, View phoneNotifications, float presentFraction) {
        if (notificationCopy == null) return;
        refreshStatusIcons();
        pageLeftIcons.notification(notificationCopy, phoneNotifications, hideNotificationLeft(), StatusIconTransition.NOTIFICATIONS);
    }

    private boolean hideNotificationLeft() {
        return settings.rightStatusIconsEnabled && !settings.safeMode && statusIconsBarState() == 0
                && !ModuleLifecycle.removed() && !statusIconsSettledClosed()
                && (panelSettledClosed || qsExpanded || fraction >= .2f || pageMotion != null && pageMotion.isRunning());
    }

    private void restoreClosingSources() {
        closingStatusIcons.hide();
        if (closingStatusSources.isEmpty()) return;
        for (Map.Entry<View, FakeState> entry : new ArrayList<>(closingStatusSources.entrySet()))
            if (entry.getKey() != null) entry.getValue().restore(entry.getKey(), false);
        closingStatusSources.clear();
        closingPhoneClock.clear();
        closingPhoneNotifications.clear();
    }

    /** Native layout/application only invalidates this clock's own widget snapshot. */
    public void onStackLayoutUpdated(View stack) {
        if(stack!=null&&active&&eligible()&&overlay!=null)overlay.scheduleWidgets();
    }

    public void onStackDetached(View stack) {
        landscapeLayout.detach(stack);
        groupStack.resetGesture();
        NotificationClockEdge.detach(stack);
        StackState previous = stacks.remove(stack);
        if (previous != null) restorePadding(stack, previous);
    }

    private FakeState fakeState(View view) {
        FakeState state = fakeClocks.get(view);
        if (state == null) { state = new FakeState(view); fakeClocks.put(view, state); }
        else state.captureNative(view);
        return state;
    }

    /** Configuration changes, page switches and feature disable all release our native overrides. */
    public void restore() {
        // Closing just the clock must not force active native groups into a measure loop.
        if(!groupStack.enabled())groupStack.restore();
        fixedStatusIcons.hide();
        if (!hideNotificationLeft()) {
            phoneRightIcons.restore();
            notificationRightIcons.restore();
        }
        restoreClock();
    }

    /** The Phone opacity policy is independent of releasing the notification clock layout. */
    private void restoreClock() {
        boolean wasActive = active;
        active = false;
        if (clearMotion != null) clearMotion.onLandscapeRegion(panel.get(), panel.get(), false, 0f, 0f);
        nativeHeaderRebound = nativeFooterRebound = 0f;
        restoreClosingSources();
        appliedReservation = Float.NaN;
        if (overlay != null) {
            overlay.unscheduleWidgets();
            if (pageMotion != null) overlay.unregisterMotion(pageMotion);
            overlay.resetMotion();
            overlay.headerMotion.setTranslationY(0f);
            overlay.footerMotion.setTranslationY(0f);
            overlay.setVisibility(View.GONE);
            if (overlay.nativeClock != null) overlay.nativeClock.cancel();
        }
        for (HeaderState header : new ArrayList<>(headers.values())) header.restore();
        for (Map.Entry<View, FakeState> entry : new ArrayList<>(fakeClocks.entrySet()))
            if (entry.getKey() != null) entry.getValue().restore(entry.getKey(), true);
        fakeClocks.clear();
        for (Map.Entry<View, StackState> entry : new ArrayList<>(stacks.entrySet())) {
            if (!managesLandscape(entry.getKey())) landscapeLayout.release(entry.getKey());
            restorePadding(entry.getKey(), entry.getValue());
            NotificationClockEdge.detach(entry.getKey());
        }
        if (wasActive) notifyVisibilityChanged();
    }

    public void onConfigurationChanged() {
        selectOrientation();
        fixedStatusIcons.resetFailure();
        closingStatusIcons.resetFailure();
        if (overlay != null) overlay.clearStyle();
        refresh();
    }

    private void detachOverlay() {
        fixedStatusIcons.hide();
        restoreClosingSources();
        if (overlay != null) overlay.unscheduleWidgets();
        if (overlay != null && pageMotion != null) overlay.unregisterMotion(pageMotion);
        if (overlay != null && overlay.nativeClock != null) overlay.nativeClock.detach();
        if (overlay != null && overlay.getParent() instanceof ViewGroup)
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        overlay = null;
    }

    private StackState stackState(View stack) {
        StackState state = stacks.get(stack);
        if (state == null) { state = new StackState(); stacks.put(stack, state); }
        return state;
    }

    private boolean eligible() {
        View host = panel.get();
        float displayed = fraction;
        if (active && !panelSettledClosed && displayed <= 0f) displayed = .0001f;
        return host != null && !headers.isEmpty() && NotificationBigClockModel.eligible(settings.enabled,
                settings.safeMode ? Configuration.ORIENTATION_UNDEFINED : host.getResources().getConfiguration().orientation,
                barState, qsExpanded, displayed);
    }

    private float density() {
        View host = panel.get(); return host == null ? 1f : host.getResources().getDisplayMetrics().density;
    }

    private float entryTranslation() {
        if (!settings.entryEffectEnabled) return 0f;
        float reveal = NotificationBigClockModel.clamp(fraction / (settings.entryCompletion / 100f), 0f, 1f);
        float eased = reveal * reveal * (3f - 2f * reveal);
        return -NumericPolicy.pixels(settings.entryTravel, density()) * (1f - eased);
    }

    private NotificationBigClockModel.Frame geometry() {
        View host = panel.get();
        float height = host == null ? 1280f : host.getHeight();
        if (height <= 0f && host != null) height = host.getResources().getDisplayMetrics().heightPixels;
        Settings s = settings;
        if (overlay != null) return overlay.frame();
        return NotificationBigClockModel.measured(height, density(), 32f * density(),
                120f * density(), 44f * density(), s.dateEnabled ? s.dateSize * density() : 0f,
                s.dateGap, s.notificationGap, s.offsetY, s.compactOffsetY, s.dateOffsetY,
                s.weight, s.compactWeight, scrollY, 0f, fraction);
    }

    private void refresh() {
        refreshStatusIcons();
        if (!eligible()) { if (active) restoreClock(); return; }
        View host = panel.get();
        if (!(host instanceof FrameLayout)) { restore(); return; }
        boolean entering = !active;
        active = true;
        for (View stack : stacks.keySet()) landscapeLayout.layout(stack, managesLandscape(stack));
        if (overlay == null) {
            overlay = new ClockView(host.getContext());
            ((FrameLayout) host).addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        overlay.setVisibility(View.VISIBLE);
        if (entering && pageMotion != null) overlay.registerMotion(pageMotion);
        for (HeaderState header : new ArrayList<>(headers.values())) {
            header.hide();
        }
        synchronizeReservation(geometry().reservedBottom);
        overlay.refreshText(false); overlay.scheduleWidgets();
        for (Map.Entry<View, StackState> entry : new ArrayList<>(stacks.entrySet())) {
            View stack = entry.getKey();
            if (stack == null) continue;
            StackState state = entry.getValue();
            float footer = footerReservation(stack);
            if (state.appliedFooter != footer) {
                state.appliedFooter = footer;
                updateNativePosition(stack);
            }
            stack.invalidate();
        }
        if (entering) notifyVisibilityChanged();
    }

    /** Hide obsolete notification copies only; all Phone/control-center handoff stays native. */
    private void refreshStatusIcons() {
        boolean closed = statusIconsSettledClosed();
        int state = statusIconsBarState();
        boolean managed = settings.rightStatusIconsEnabled && !settings.safeMode && state == 0;
        boolean hide = managed && !closed;
        if (!managed || closed) pageLeftIcons.restoreNotificationCopies();
        pageLeftIcons.progress(hideNotificationLeft());
        // A single page fraction can cross zero while another page/its native switch still owns
        // the shade. Release only at the real global settled close, never at a horizontal zero.
        phoneRightIcons.progress(1f, false);
        // LEFT and RIGHT keep the original OEM Phone/fake/header rendering chain.
        notificationRightIcons.progress(1f, hide);
    }

    private void applyPadding(View stack, StackState state) {
        if (stack == null) return;
        if (state.rawIntrinsic != Integer.MIN_VALUE)
            invoke(stack, "setIntrinsicPadding", new Class<?>[]{Integer.TYPE}, state.rawIntrinsic);
        if (NotificationBigClockModel.finite(state.rawTop))
            invoke(stack, "updateTopPadding", new Class<?>[]{Float.TYPE, Boolean.TYPE}, state.rawTop, false);
    }

    /** A real text/native layout can complete after the panel event. Commit that same
     * measured reservation before drawing, rather than waiting for another notification. */
    private void synchronizeReservation(float reservation) {
        if (!active || !NotificationBigClockModel.finite(reservation) || appliedReservation == reservation) return;
        appliedReservation = reservation;
        for (Map.Entry<View, StackState> entry : stacks.entrySet()) applyPadding(entry.getKey(), entry.getValue());
    }

    private void restorePadding(View stack, StackState state) {
        if (stack == null || state.restoring) return;
        state.restoring = true;
        try {
            if (state.rawIntrinsic != Integer.MIN_VALUE && state.appliedIntrinsic != state.rawIntrinsic)
                invoke(stack, "setIntrinsicPadding", new Class<?>[]{Integer.TYPE}, state.rawIntrinsic);
            if (NotificationBigClockModel.finite(state.rawTop) && state.appliedTop != state.rawTop)
                invoke(stack, "updateTopPadding", new Class<?>[]{Float.TYPE, Boolean.TYPE}, state.rawTop, false);
            state.appliedTop = state.rawTop; state.appliedIntrinsic = state.rawIntrinsic;
            if (state.appliedFooter != 0f) {
                state.appliedFooter = 0f;
                updateNativePosition(stack);
            }
            stack.invalidate();
        } finally { state.restoring = false; }
    }

    private static void updateNativePosition(View stack) {
        invoke(stack, "updateStackPosition", new Class<?>[]{Boolean.TYPE}, false);
        invoke(stack, "requestChildrenUpdate$1", new Class<?>[0]);
    }

    private static void invoke(View view, String name, Class<?>[] types, Object... args) {
        try { Method method = view.getClass().getMethod(name, types); method.invoke(view, args); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static TextView textView(View parent, String name) {
        View view = childView(parent, name);
        return view instanceof TextView ? (TextView) view : null;
    }

    private static View childView(View parent, String name) {
        int id = parent.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : parent.findViewById(id);
    }

    private TextView sourceClock() {
        for (HeaderState header : headers.values()) { TextView clock = header.clock.get(); if (clock != null) return clock; }
        return null;
    }

    /** Small owned-widget typography cache. Stable spring frames never reapply fonts or remeasure text. */
    private static final class Typography {
        Object settings,style;String text;float size,spacing,height;int weight;
        boolean matches(Object s,Object st,String t,float z,int w,float sp,float h) {
            return settings==s&&style==st&&t.equals(text)&&size==z&&weight==w&&spacing==sp&&height==h;
        }
        void record(Object s,Object st,String t,float z,int w,float sp,float h) {
            settings=s;style=st;text=t;size=z;weight=w;spacing=sp;height=h;
        }
    }

    /** Detached probes create a real TextView Layout too. Later text/font changes require
     * LayoutParams in checkForRelayout(), even though these probes have no parent. */
    static void ensureOwnedWidgetLayout(View widget) {
        if(widget.getLayoutParams()==null)
            widget.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    static boolean widgetHostReady(View widget,View currentOverlay,View host,boolean active) {
        return active&&widget==currentOverlay&&host!=null&&widget.getParent()==host
                &&widget.isAttachedToWindow()&&host.isAttachedToWindow()&&widget.getVisibility()==View.VISIBLE;
    }

    /** Three real text widgets retain shaping, emoji fallback and native clock proportions. */
    private final class ClockView extends FrameLayout {
        final ClockText clock, timeProbe;
        final NotificationNativeClock nativeClock;
        NotificationClockFont.Resolver heightFont;
        final FrameLayout headerMotion, footerMotion;
        final TextView dateLabel, footerLabel, probe;
        final Map<TextView, Typography> typography=new WeakHashMap<>();
        final Map<Integer, Typeface> faces = new java.util.LinkedHashMap<Integer, Typeface>(40, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Typeface> entry) { return size() > 40; }
        };
        NotificationClockStyle.Style nativeStyle;
        Settings textSettings, measuredSettings;
        NotificationBigClockModel.Frame cachedFrame;
        int measurementRevision,frameRevision=-1,frameScroll;
        float frameFraction;
        long textBucket = Long.MIN_VALUE;
        String time = "", date = "", footer = "";
        int measuredWidth, measuredHeight;
        float expandedSize, compactSize, expandedWidth, expandedHeight, compactHeight, compactAxisRatio, dateHeight;
        float expandedAxisRatio = 1f;
        float safeTop, safeBottom, safeLeft, safeRight, contentLeft, contentRight;
        float measuredContentLeft, measuredContentRight, measuredSafeTop, measuredSafeBottom;
        float visibleHeaderBottom = Float.NaN, appliedEntryBlur = -1f;
        final android.graphics.Matrix headerToStack = new android.graphics.Matrix();
        final android.graphics.RectF headerInk = new android.graphics.RectF();
        boolean entryBlurFailed;
        boolean updating, widgetsScheduled;
        final float[] reboundOffsets = new float[2];
        final int[] footerHostLocation = new int[2], footerControlLocation = new int[2];
        WeakReference<View> reboundStack = new WeakReference<>(null);
        int statusHeightResource = -1;
        ViewTreeObserver widgetObserver;
        final ViewTreeObserver.OnPreDrawListener widgetFrame = () -> {
            if (!widgetHostReady(this,overlay,panel.get(),active)) {unscheduleWidgets();return true;}
            if (!active || !eligible()) return true;
            float oldHeader = nativeHeaderRebound, oldFooter = nativeFooterRebound;
            sampleRebound();
            if (widgetsScheduled || oldHeader != nativeHeaderRebound || oldFooter != nativeFooterRebound) {
                widgetsScheduled = false;
                updateWidgets(false);
            }
            return true;
        };
        ClockView(Context context) {
            super(context);
            setClickable(false); setFocusable(false); setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            clock = new ClockText(context); dateLabel = label(context); footerLabel = label(context);
            probe = label(context); timeProbe = new ClockText(context);
            headerMotion = motionContainer(context); footerMotion = motionContainer(context);
            addView(headerMotion, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(footerMotion, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            View template = NotificationClockStyle.nativeDigitTemplate(panel.get());
            heightFont = NotificationClockFont.create(context, template);
            nativeClock = NotificationNativeClock.create(context, template);
            if (nativeClock != null) headerMotion.addView(nativeClock, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            headerMotion.addView(clock, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            headerMotion.addView(dateLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            footerMotion.addView(footerLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        FrameLayout motionContainer(Context context) {
            FrameLayout view = new FrameLayout(context);
            view.setClipChildren(false); view.setClipToPadding(false);
            view.setClickable(false); view.setFocusable(false);
            return view;
        }
        void registerMotion(PageMotion motion) {
            // Only visible ink ranges enter native hit-row ranking; the full-screen host never does.
            if (headerMotion.getHeight() > 0 && headerMotion.getVisibility() == View.VISIBLE) motion.register(headerMotion);
            else motion.unregister(headerMotion);
            if (footerMotion.getHeight() > 0 && footerMotion.getVisibility() == View.VISIBLE) motion.register(footerMotion);
            else motion.unregister(footerMotion);
        }
        void unregisterMotion(PageMotion motion) { motion.unregister(headerMotion); motion.unregister(footerMotion); }
        void resetMotion() {
            headerMotion.setTranslationX(0f); footerMotion.setTranslationX(0f);
        }
        boolean hasPageOffset() { return Math.abs(headerMotion.getTranslationX()) > .5f || Math.abs(footerMotion.getTranslationX()) > .5f; }
        TextView label(Context context) {
            TextView view = new TextView(context);
            ensureOwnedWidgetLayout(view);
            view.setSingleLine(true); view.setIncludeFontPadding(false); view.setClickable(false);
            view.setFocusable(false); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            view.setFontFeatureSettings("tnum");
            int inset = Math.max(1, Math.round(density() * 2f));
            view.setPadding(inset, inset, inset, inset);
            return view;
        }
        void clearStyle() {
            faces.clear(); typography.clear(); nativeStyle = null; measuredSettings = null; textSettings = null;
            statusHeightResource = -1;
            heightFont = NotificationClockFont.create(getContext(), NotificationClockStyle.nativeDigitTemplate(panel.get()));
            if (nativeClock != null) nativeClock.cancel();
        }
        void scheduleWidgets() {
            if(!widgetHostReady(this,overlay,panel.get(),active))return;
            observeWidgets();
            if (widgetsScheduled) return;
            widgetsScheduled = true;
            invalidate();
        }
        void observeWidgets() {
            if(!widgetHostReady(this,overlay,panel.get(),active))return;
            if (widgetObserver != null && widgetObserver.isAlive()) return;
            ViewTreeObserver observer = getViewTreeObserver();
            if (!observer.isAlive()) return;
            widgetObserver = observer;
            // The native animation can write several properties in the same vsync. Consume
            // their final snapshot before this draw, including parallax that only invalidates
            // the native rows. This observer never requests a continuous draw or timer.
            observer.addOnPreDrawListener(widgetFrame);
        }
        void unscheduleWidgets() {
            if (widgetObserver != null && widgetObserver.isAlive())
                widgetObserver.removeOnPreDrawListener(widgetFrame);
            widgetObserver = null;
            widgetsScheduled = false;
        }
        @Override protected void onDetachedFromWindow() {
            unscheduleWidgets();
            super.onDetachedFromWindow();
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            observeWidgets();
            scheduleWidgets();
        }
        boolean refreshText(boolean force) {
            return refreshText(force, System.currentTimeMillis());
        }
        boolean refreshText(boolean force, long now) {
            Settings s = settings;
            long bucket = now / (s.seconds ? 1000L : 60000L);
            if (!force && s == textSettings && bucket == textBucket) return false;
            boolean animate = textSettings == s && textBucket != Long.MIN_VALUE && bucket != textBucket && hasVisibleTime();
            java.util.TimeZone zone = java.util.TimeZone.getDefault();
            String nextTime = TimeFormat.format(s.pattern, now, zone);
            String nextDate = s.dateEnabled ? TimeFormat.format(s.datePattern, now, zone) : "";
            String nextFooter = s.footerEnabled ? TimeFormat.formatContent(s.footerPattern, s.footerText, now, zone) : "";
            boolean changed = !time.equals(nextTime) || !date.equals(nextDate) || !footer.equals(nextFooter);
            if (!time.equals(nextTime)) {
                time = nextTime;
                if (nativeClock != null) nativeClock.setTime(time, animate, now);
                clock.setText(time);
            }
            if (!date.equals(nextDate)) { date = nextDate; dateLabel.setText(date); }
            if (!footer.equals(nextFooter)) { footer = nextFooter; footerLabel.setText(footer); }
            boolean newStyle = textSettings != s;
            textSettings = s; textBucket = bucket;
            if (changed || newStyle || force) measuredSettings = null;
            return changed || newStyle;
        }
        int hostWidth() {
            View host = panel.get();
            return host != null && host.getWidth() > 0 ? host.getWidth() : getWidth() > 0 ? getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
        }
        int hostHeight() {
            View host = panel.get();
            return host != null && host.getHeight() > 0 ? host.getHeight() : getHeight() > 0 ? getHeight()
                    : getResources().getDisplayMetrics().heightPixels;
        }
        Typeface fallbackFace(int requestedWeight, boolean time) {
            int weight = Math.max(1, Math.min(1000, requestedWeight));
            int cacheKey = weight + (time ? 0 : 1000);
            Typeface result = faces.get(cacheKey);
            if (result == null) {
                // Lock-screen numeral families have unusual Latin metrics; labels need a text family.
                result = FontRepository.typefaceForMode(getContext(), settings.font,
                        time ? nativeStyle.typeface : Typeface.DEFAULT, weight);
                faces.put(cacheKey, result);
            }
            return result;
        }
        void textStyle(TextView view, String text, float size, int weight, float spacing) {
            textStyle(view, text, size, weight, spacing, 1f);
        }
        void textStyle(TextView view, String text, float size, int weight, float spacing, float heightRatio) {
            ensureOwnedWidgetLayout(view);
            Typography cached=typography.get(view);
            if(cached!=null&&cached.matches(settings,nativeStyle,text,size,weight,spacing,heightRatio))return;
            boolean time = view instanceof ClockText;
            Typeface nativeHeight = time && "native".equals(settings.font) && heightFont != null
                    ? heightFont.typeface(Math.max(1, Math.min(1000, weight)), heightRatio) : null;
            if (nativeHeight != null) FontWeight.restore(view, nativeHeight, null);
            else FontWeight.apply(view, fallbackFace(weight, time),
                    FontRepository.weightForMode(settings.font, weight),
                    time && ("native".equals(settings.font) || "global".equals(settings.font) && FontRepository.systemMode())
                            ? nativeStyle.fontVariationSettings : null);
            if (view.getTextSize() != size) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            float em = Math.min(64f, Math.max(-.5f, spacing));
            if (view.getLetterSpacing() != em) view.setLetterSpacing(em);
            String features = nativeStyle.fontFeatureSettings == null ? "tnum" : nativeStyle.fontFeatureSettings;
            if (!features.equals(view.getFontFeatureSettings())) view.setFontFeatureSettings(features);
            if (!text.contentEquals(view.getText())) view.setText(text);
            if(cached==null){cached=new Typography();typography.put(view,cached);}
            cached.record(settings,nativeStyle,text,size,weight,spacing,heightRatio);
        }
        void measureText(View view) {
            ensureOwnedWidgetLayout(view);
            if(!view.isLayoutRequested()&&view.getMeasuredWidth()>0&&view.getMeasuredHeight()>0)return;
            view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
        float fit(TextView probe, String text, float requestedSize, int weight, float spacing, float width, float height) {
            float size = NumericPolicy.textPixels(requestedSize);
            if (size <= 0f || text.isEmpty()) return 0f;
            textStyle(probe, text, size, weight, spacing); measureText(probe);
            float inset = probe.getPaddingLeft() + probe.getPaddingRight();
            float ratio = Math.min(1f, Math.min(Math.max(1f, width - inset) / Math.max(1f, probe.getMeasuredWidth() - inset),
                    Math.max(1f, height - inset) / Math.max(1f, probe.getMeasuredHeight() - inset)));
            size *= ratio;
            textStyle(probe, text, size, weight, spacing); measureText(probe);
            return size;
        }
        void updateSafeInsets() {
            WindowInsets insets = getRootWindowInsets();
            int top = insets == null ? 0 : insets.getSystemWindowInsetTop();
            int bottom = insets == null ? 0 : insets.getStableInsetBottom();
            if (statusHeightResource < 0)
                statusHeightResource = getResources().getIdentifier("status_bar_height", "dimen", "android");
            safeTop = Math.max(top, statusHeightResource == 0 ? 28f * density()
                    : getResources().getDimension(statusHeightResource));
            for (HeaderState header : headers.values()) {
                View icons = header.statusIcons.get();
                if (icons == null || icons.getHeight() <= 0) continue;
                // Reserve the final shared group bounds, independent of the pager's current transform.
                float iconTop = Math.max(6f * density(), (safeTop - icons.getHeight()) * .5f);
                safeTop = Math.max(safeTop, iconTop + icons.getHeight());
            }
            safeBottom = Math.max(bottom, 12f * density());
            safeLeft = insets == null ? 0f : insets.getSystemWindowInsetLeft();
            safeRight = insets == null ? 0f : insets.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28 && insets != null && insets.getDisplayCutout() != null) {
                safeLeft = Math.max(safeLeft, insets.getDisplayCutout().getSafeInsetLeft());
                safeRight = Math.max(safeRight, insets.getDisplayCutout().getSafeInsetRight());
            }
        }
        void updateContentBounds() {
            contentLeft = 24f * density(); contentRight = hostWidth() - contentLeft;
            if (!landscape(panel.get())) return;
            float portrait = Math.min(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            float column = Math.min(portrait, Math.max(1f, hostWidth() - safeLeft - safeRight));
            contentLeft = safeLeft + (hostWidth() - safeLeft - safeRight - column) / 2f + 16f * density();
            contentRight = contentLeft + Math.max(1f, column - 32f * density());
            View host = panel.get();
            for (View stack : stacks.keySet()) {
                if (!(stack instanceof ViewGroup) || !currentReboundStack(stack, host)) continue;
                float percentage = landscapeSettings.notificationWidthEnabled ? landscapeSettings.notificationWidth : 100f;
                int columnWidth = landscapeLayout.columnWidth(stack, Math.round(hostWidth() - safeLeft - safeRight), percentage);
                float left = landscapeLayout.columnLeft(stack, columnWidth);
                View ancestor = stack.getParent() instanceof View ? (View) stack.getParent() : null;
                while (ancestor != null && ancestor != host) {
                    left += ancestor.getLeft();
                    ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
                }
                if (ancestor != host) continue;
                int padding = Math.min(Math.max(0, (columnWidth - 1) / 2),
                        landscapeLayout.sidePadding(stack, Math.round(16f * density())));
                contentLeft = left + padding;
                contentRight = left + columnWidth - padding;
                return;
            }
        }
        void prepareMeasurements() {
            refreshText(false);
            updateSafeInsets();
            updateContentBounds();
            int width = hostWidth(), height = hostHeight();
            Settings s = settings;
            if (s == measuredSettings && width == measuredWidth && height == measuredHeight
                    && measuredContentLeft == contentLeft && measuredContentRight == contentRight
                    && measuredSafeTop == safeTop && measuredSafeBottom == safeBottom) return;
            TextView nativeClock = sourceClock();
            NotificationClockStyle.Style style = NotificationClockStyle.resolve(panel.get(),
                    nativeClock == null ? Typeface.DEFAULT : nativeClock.getTypeface());
            if (style != nativeStyle) { nativeStyle = style; faces.clear(); }
            boolean horizontal = landscape(panel.get());
            float widthLimit = Math.max(1f, contentRight - contentLeft);
            float spacing = nativeStyle.letterSpacing + s.letterSpacing;
            float max = NumericPolicy.textPixels((double) s.maxSize * density() * s.scale / 100d);
            expandedAxisRatio = 1f;
            // Landscape movement is position/opacity/blur only. A native group may
            // briefly narrow its measured column during an animation; that width is
            // an alignment constraint, never a reason to resize user-selected ink.
            // Do not compress the native HGHT axis to a compact header budget either.
            expandedSize = horizontal ? max
                    : fit(timeProbe, time, max, (int) s.weight, spacing, widthLimit, height * .44f);
            textStyle(timeProbe, time, expandedSize, (int) s.weight, spacing, expandedAxisRatio); measureText(timeProbe);
            expandedWidth = timeProbe.getMeasuredWidth();
            expandedHeight = expandedSize > 0f ? timeProbe.getMeasuredHeight() : 0f;
            float compactMax = NumericPolicy.textPixels((double) s.compactMaxSize * density());
            compactSize = horizontal ? expandedSize
                    : Math.min(compactMax, NumericPolicy.textPixels((double) expandedSize * s.compactScale / 100d));
            compactAxisRatio = expandedSize > 0f ? compactSize / expandedSize : 1f;
            if (horizontal) compactHeight = expandedHeight;
            else if ("native".equals(s.font) && heightFont != null && expandedSize > 0f) {
                // HGHT includes a fixed stroke/height base. Measure its real outline, never assume H is pixels.
                textStyle(timeProbe, time, expandedSize, (int) s.weight, spacing, expandedAxisRatio * compactAxisRatio);
                measureText(timeProbe); compactHeight = timeProbe.getMeasuredHeight();
            } else compactHeight = expandedSize > 0f ? Math.min(height * .22f,
                    expandedHeight * compactSize / expandedSize) : 0f;
            float dateSize = fit(probe, date, NumericPolicy.textPixels((double) s.dateSize * density()),
                    (int) s.dateWeight, 0f, widthLimit, height * .15f);
            textStyle(dateLabel, date, dateSize, (int) s.dateWeight, 0f); measureText(dateLabel);
            dateHeight = s.dateEnabled && dateSize > 0f && !date.isEmpty() ? dateLabel.getMeasuredHeight() : 0f;
            float footerSize = fit(probe, footer, NumericPolicy.textPixels((double) s.footerSize * density()),
                    (int) s.footerWeight, 0f, widthLimit, height * .15f);
            textStyle(footerLabel, footer, footerSize, (int) s.footerWeight, 0f); measureText(footerLabel);
            measuredSettings = s; measuredWidth = width; measuredHeight = height;
            measuredContentLeft = contentLeft; measuredContentRight = contentRight;
            measuredSafeTop = safeTop; measuredSafeBottom = safeBottom;
            ++measurementRevision;
        }
        NotificationBigClockModel.Frame frame() {
            prepareMeasurements();
            if(cachedFrame!=null&&frameRevision==measurementRevision&&frameScroll==scrollY
                    &&frameFraction==fraction)return cachedFrame;
            Settings s = settings;
            if (landscape(panel.get())) cachedFrame=NotificationBigClockModel.landscapeMeasured(density(), safeTop,
                    expandedHeight, compactHeight, dateHeight, s.dateGap, s.notificationGap, s.offsetY,
                    s.compactOffsetY, s.dateOffsetY, s.weight, scrollY, fraction);
            else cachedFrame=NotificationBigClockModel.measured(hostHeight(), density(), safeTop, expandedHeight,
                    compactHeight, dateHeight, s.dateGap, s.notificationGap, s.offsetY,
                    s.compactOffsetY, s.dateOffsetY, s.weight, s.compactWeight, scrollY, 0f, fraction);
            frameRevision=measurementRevision;frameScroll=scrollY;frameFraction=fraction;
            return cachedFrame;
        }
        float headerBottomIn(View stack) {
            if (headerMotion.getVisibility() != View.VISIBLE) return Float.NaN;
            float bottom = Float.NaN;
            View digits = nativeClock != null && nativeClock.getVisibility() == View.VISIBLE ? nativeClock : clock;
            float border = clock.glass && clock.glassBorder && clock.borderWidth > 0f
                    ? clock.borderWidth * .5f + 1f : 0f;
            float inkBottom = drawnBottomIn(digits, stack, border);
            if (NotificationBigClockModel.finite(inkBottom)) bottom = inkBottom;
            float dateBottom = drawnBottomIn(dateLabel, stack, 0f);
            if (NotificationBigClockModel.finite(dateBottom))
                bottom = NotificationBigClockModel.finite(bottom) ? Math.max(bottom, dateBottom) : dateBottom;
            return bottom;
        }

        float drawnBottomIn(View child, View stack, float outset) {
            if (child == null || child.getVisibility() != View.VISIBLE || child.getHeight() <= 0) return Float.NaN;
            try {
                headerToStack.reset();
                child.transformMatrixToGlobal(headerToStack);
                stack.transformMatrixToLocal(headerToStack);
                headerInk.set(-outset, -outset, child.getWidth() + outset, child.getHeight() + outset);
                headerToStack.mapRect(headerInk);
                return NotificationBigClockModel.finite(headerInk.bottom) ? headerInk.bottom : Float.NaN;
            } catch (RuntimeException unavailable) { return Float.NaN; }
        }

        void sampleRebound() {
            reboundOffsets[0] = reboundOffsets[1] = 0f;
            View host = panel.get();
            ReboundReader reader = reboundReader;
            if (host != null && reader != null) {
                View source = reboundStack.get();
                if (!currentReboundStack(source, host)) {
                    source = null;
                    for (View stack : new ArrayList<>(stacks.keySet())) {
                        if (currentReboundStack(stack, host)) { source = stack; break; }
                    }
                    reboundStack = new WeakReference<>(source);
                }
                if (source != null) reader.sample(source, reboundOffsets);
            }
            nativeHeaderRebound = NotificationBigClockModel.finite(reboundOffsets[0]) ? reboundOffsets[0] : 0f;
            nativeFooterRebound = NotificationBigClockModel.finite(reboundOffsets[1]) ? reboundOffsets[1] : 0f;
        }
        boolean currentReboundStack(View stack, View host) {
            if (stack == null || !stack.isAttachedToWindow() || !stacks.containsKey(stack)) return false;
            for (View ancestor = stack; ancestor != null;
                    ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null)
                if (ancestor == host) return true;
            return false;
        }
        float horizontal(TextView view, String alignment, float offset) {
            return horizontal(view.getMeasuredWidth(), alignment, offset);
        }
        float horizontal(float textWidth, String alignment, float offset) {
            float x = "left".equals(alignment) ? contentLeft : "right".equals(alignment)
                    ? contentRight - textWidth : (contentLeft + contentRight - textWidth) / 2f;
            return NumericPolicy.drawPixels(x + NumericPolicy.pixels(offset, density()));
        }
        void place(View view, float x, float y, boolean visible) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) return;
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            view.setTranslationX(x); view.setTranslationY(NumericPolicy.drawPixels(y));
        }
        float desiredFooterTop() {
            Settings s = settings;
            return hostHeight() - safeBottom - NumericPolicy.pixels(s.footerMargin, density())
                    - footerLabel.getMeasuredHeight() + NumericPolicy.pixels(s.footerOffsetY, density());
        }
        /** Reserve a fixed strip first; actual native clear controls retain priority inside it. */
        float footerTop() {
            float motion = nativePanelTranslationY + nativeFooterRebound;
            float top = desiredFooterTop() + motion, height = footerLabel.getMeasuredHeight();
            View host = panel.get();
            if (host == null) return top - motion;
            host.getLocationInWindow(footerHostLocation);
            for (View stack : new ArrayList<>(stacks.keySet())) {
                if (!(stack instanceof ViewGroup)) continue;
                ViewGroup group = (ViewGroup) stack;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (!child.getClass().getSimpleName().equals("FooterView") || !child.isShown()
                            || child.getAlpha() <= 0f || child.getHeight() <= 0) continue;
                    child.getLocationInWindow(footerControlLocation);
                    float controlTop = footerControlLocation[1] - footerHostLocation[1];
                    float controlBottom = controlTop + child.getHeight();
                    if (top < controlBottom + 8f * density() && top + height > controlTop - 8f * density()) {
                        float below = controlBottom + 8f * density();
                        if (below + height <= hostHeight() - safeBottom) top = below;
                        else return Float.NaN;
                    }
                }
            }
            // Collision checks use final coordinates; the motion container adds this delta once.
            return top - motion;
        }
        void updateWidgets() {
            updateWidgets(true);
        }
        void updateWidgets(boolean readRebound) {
            if (updating || !widgetHostReady(this,overlay,panel.get(),active) || !eligible()) return;
            updating = true;
            try {
                if (readRebound) sampleRebound();
                NotificationBigClockModel.Frame f = frame(); Settings s = settings;
                synchronizeReservation(f.reservedBottom);
                boolean variableHeight = "native".equals(s.font) && heightFont != null;
                boolean horizontalClock = landscape(panel.get());
                float sizeProgress=horizontalClock?0f:f.progress;
                float heightRatio = Math.max(.001f, expandedAxisRatio * (1f + (compactAxisRatio - 1f) * sizeProgress));
                float size = variableHeight ? expandedSize : expandedSize + (compactSize - expandedSize) * sizeProgress;
                int animatedWeight = Math.round(s.weight);
                textStyle(clock, time, size, animatedWeight, nativeStyle.letterSpacing + s.letterSpacing,
                        variableHeight ? heightRatio : 1f);
                measureText(clock);
                clock.setPivotX(0f); clock.setPivotY(0f);
                clock.setScaleX(1f); clock.setScaleY(1f);
                TextView source = sourceClock();
                int nativeColor = source == null ? Color.WHITE : source.getCurrentTextColor();
                boolean darkBackground = Color.red(nativeColor) * 299 + Color.green(nativeColor) * 587
                        + Color.blue(nativeColor) * 114 > 128000;
                clock.setTextColor(resolvedColor(darkBackground ? s.colorDark : s.colorLight,
                        darkBackground ? s.clockDarkAlpha : s.clockLightAlpha, nativeColor));
                dateLabel.setTextColor(resolvedColor(darkBackground ? s.dateColorDark : s.dateColorLight,
                        darkBackground ? s.dateDarkAlpha : s.dateLightAlpha, nativeColor));
                footerLabel.setTextColor(resolvedColor(darkBackground ? s.footerColorDark : s.footerColorLight,
                        darkBackground ? s.footerDarkAlpha : s.footerLightAlpha, nativeColor));
                int borderColor = resolvedColor(darkBackground ? s.borderColorDark : s.borderColorLight,
                        darkBackground ? s.borderDarkAlpha : s.borderLightAlpha, nativeColor);
                clock.glassBorder = s.glassBorderEnabled;
                clock.borderColor = borderColor;
                clock.borderWidth = NumericPolicy.pixels(s.glassBorderWidth, density());
                if (clock.glass != s.glass) { clock.glass = s.glass; clock.invalidate(); }
                float reveal = NotificationBigClockModel.clamp(fraction / (s.entryCompletion / 100f), 0f, 1f);
                float easedReveal = reveal * reveal * (3f - 2f * reveal);
                float entryAlpha = s.entryEffectEnabled ? 1f - (1f - easedReveal) * s.entryFadeStrength / 100f : f.entryAlpha;
                float scrollEffect = horizontalClock ? NotificationBigClockModel.scrollFade(f.progress) : 0f;
                headerMotion.setAlpha(horizontalClock
                        ? NotificationBigClockModel.landscapeHeaderAlpha(entryAlpha, f.progress) : entryAlpha);
                clock.setAlpha(1f); dateLabel.setAlpha(1f); footerLabel.setAlpha(entryAlpha);
                float blur = s.entryEffectEnabled ? Math.min(64f, NumericPolicy.pixels(s.entryBlurRadius, density())) * (1f - easedReveal) : 0f;
                if (s.entryEffectEnabled)
                    blur = Math.max(blur, Math.min(64f, NumericPolicy.pixels(s.entryBlurRadius, density())) * scrollEffect);
                blur = Math.round(blur * 4f) / 4f;
                if (Build.VERSION.SDK_INT >= 31 && !entryBlurFailed && blur != appliedEntryBlur) {
                    try { EntryBlur.apply(headerMotion, footerMotion, blur); appliedEntryBlur = blur; }
                    catch (Throwable unavailable) {
                        entryBlurFailed = true;
                        ModuleDiagnostics.error("bigclock", "Clock entry blur unavailable; gesture fade retained", unavailable);
                    }
                }
                float x = s.offsetX + s.compactOffsetX * sizeProgress;
                boolean nativeReady = nativeClock != null && nativeClock.isAvailable()
                        && nativeClock.setTypography(clock.getTypeface(), size, clock.getCurrentTextColor(),
                                nativeStyle.letterSpacing + s.letterSpacing, clock.getFontVariationSettings());
                if (nativeReady) {
                    nativeClock.setGlass(s.glass, density()); measureText(nativeClock);
                    nativeClock.setGlassBorder(s.glass && s.glassBorderEnabled, clock.borderWidth, borderColor);
                    nativeClock.setPivotX(0f); nativeClock.setPivotY(0f);
                    nativeClock.setScaleX(1f); nativeClock.setScaleY(1f);
                    nativeClock.setAlpha(1f);
                } else if (nativeClock != null) nativeClock.setVisibility(View.GONE);
                float visibleClockHeight = nativeReady ? nativeClock.getMeasuredHeight() : clock.getMeasuredHeight();
                boolean showClock = size > 0f && !time.isEmpty();
                boolean showDate = s.dateEnabled && dateHeight > 0f;
                headerMotion.setVisibility(showClock || showDate ? View.VISIBLE : View.GONE);
                float headerTop = showClock ? f.clockTop : f.dateTop;
                float headerBottom = showClock ? f.clockTop + visibleClockHeight : f.dateTop + dateHeight;
                if (showDate) { headerTop = Math.min(headerTop, f.dateTop); headerBottom = Math.max(headerBottom, f.dateTop + dateHeight); }
                float entryShift = entryTranslation();
                headerTop += entryShift; headerBottom += entryShift;
                float headerYMotion = nativePanelTranslationY + nativeHeaderRebound;
                headerMotion.setTranslationY(headerYMotion);
                footerMotion.setTranslationY(nativePanelTranslationY + nativeFooterRebound);
                visibleHeaderBottom = showClock || showDate ? headerBottom + headerYMotion : 0f;
                int headerY = Math.round(headerTop), headerEnd = Math.max(headerY + 1, Math.round(headerBottom));
                headerMotion.layout(0, headerY, hostWidth(), headerEnd);
                if (nativeReady) place(nativeClock, horizontal(nativeClock.getMeasuredWidth(), s.alignment, x),
                        f.clockTop + entryShift - headerY, showClock);
                place(clock, horizontal(clock, s.alignment, x), f.clockTop + entryShift - headerY, !nativeReady && showClock);
                place(dateLabel, horizontal(dateLabel, s.dateAlignment, s.dateOffsetX),
                        f.dateTop + entryShift - headerY, showDate);
                boolean footerReady = footerMarginHookAvailable && s.footerEnabled
                        && footerLabel.getTextSize() > 0f && !footer.isEmpty();
                float footerY = footerReady ? footerTop() : Float.NaN;
                boolean showFooter = footerReady && NotificationBigClockModel.finite(footerY);
                footerMotion.setVisibility(showFooter ? View.VISIBLE : View.GONE);
                if (showFooter) {
                    int footerStart = Math.round(footerY);
                    footerMotion.layout(0, footerStart, hostWidth(), footerStart + footerLabel.getMeasuredHeight());
                    place(footerLabel, horizontal(footerLabel, s.footerAlignment, s.footerOffsetX), footerY - footerStart, true);
                } else footerLabel.setVisibility(View.GONE);
                if (clearMotion != null) clearMotion.onLandscapeRegion(panel.get(), panel.get(), landscape(panel.get()),
                        contentRight + 16f * density(), hostWidth() - safeRight - 16f * density());
                if (pageMotion != null && active) registerMotion(pageMotion);
            } finally { updating = false; }
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            widgetsScheduled = false;
            updateWidgets();
        }
        @Override protected void dispatchDraw(Canvas canvas) { super.dispatchDraw(canvas); }
    }

    private static int resolvedColor(int selected, boolean customAlpha, int nativeColor) {
        return customAlpha ? selected : (selected & 0xffffff) | (Color.alpha(nativeColor) << 24);
    }

    /** Only the independent clock/date group receives entry blur; never the notification tree. */
    private static final class EntryBlur {
        private static final android.graphics.RenderEffect[] effects=new android.graphics.RenderEffect[257];
        static void apply(View header, View footer, float radius) {
            int index=Math.max(0,Math.min(256,Math.round(radius*4f)));
            android.graphics.RenderEffect effect=null;
            if(index>0) {
                effect=effects[index];
                if(effect==null)effects[index]=effect=android.graphics.RenderEffect.createBlurEffect(index/4f,index/4f,Shader.TileMode.DECAL);
            }
            header.setRenderEffect(effect);
            // Custom footer content is independent of the clock's scroll/entry blur.
            footer.setRenderEffect(null);
        }
    }

    /** Use the visible glyph box while retaining TextView's native shaping and width. */
    private final class ClockText extends TextView {
        boolean glass, glassBorder = true;
        float borderWidth = .65f;
        int borderColor = Color.WHITE, outlineColor;
        float outlineTop, outlineBottom;
        final Rect ink = new Rect();
        Shader gradient, outline;
        int gradientColor;
        float gradientTop, gradientBottom;
        ClockText(Context context) {
            super(context); ensureOwnedWidgetLayout(this);
            setSingleLine(true); setHorizontallyScrolling(false);
            setIncludeFontPadding(false); setClickable(false); setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); setFontFeatureSettings("tnum");
            int pad = Math.max(1, Math.round(density() * 2f)); setPadding(pad, pad, pad, pad);
        }
        void measureInk() {
            String text = getText().toString();
            ink.setEmpty();
            if (!text.isEmpty()) getPaint().getTextBounds(text, 0, text.length(), ink);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            measureInk();
            int height = Math.max(0, ink.height()) + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(getMeasuredWidthAndState(), resolveSizeAndState(height, heightSpec, 0));
        }
        @Override protected void onDraw(Canvas canvas) {
            Layout layout = getLayout();
            if (layout == null || layout.getLineCount() == 0) return;
            measureInk();
            if (ink.isEmpty()) return;
            Paint paint = getPaint();
            Shader oldShader = paint.getShader(); Paint.Style oldStyle = paint.getStyle();
            float oldStroke = paint.getStrokeWidth(); int oldColor = paint.getColor();
            int color = getCurrentTextColor();
            float y0 = layout.getLineBaseline(0) + ink.top, y1 = y0 + Math.max(1, ink.height());
            if (glass && (gradient == null || gradientColor != color || gradientTop != y0 || gradientBottom != y1)) {
                int rgb = color & 0xffffff;
                // Paint owns selected opacity; material opacity is applied once by the shader.
                gradient = new LinearGradient(0f, y0, 0f, y1,
                        new int[]{rgb | (Math.round(255 * .32f) << 24), rgb | (Math.round(255 * .12f) << 24),
                                rgb | (Math.round(255 * .26f) << 24)}, new float[]{0f, .6f, 1f}, Shader.TileMode.CLAMP);
                gradientColor = color; gradientTop = y0; gradientBottom = y1;
            }
            if (glass && (outline == null || outlineColor != borderColor || outlineTop != y0 || outlineBottom != y1)) {
                int rgb = borderColor & 0xffffff;
                outline = new LinearGradient(0f, y0, 0f, y1,
                        rgb | (Math.round(Color.alpha(borderColor) * .9f) << 24),
                        rgb | (Math.round(Color.alpha(borderColor) * .4f) << 24), Shader.TileMode.CLAMP);
                outlineColor = borderColor; outlineTop = y0; outlineBottom = y1;
            }
            int save = canvas.save();
            try {
                canvas.clipRect(0, 0, getWidth(), getHeight());
                canvas.translate(getCompoundPaddingLeft(), getPaddingTop() - y0);
                paint.setColor(color); paint.setShader(glass ? gradient : null); paint.setStyle(Paint.Style.FILL);
                layout.draw(canvas);
                if (glass && glassBorder && borderWidth > 0f) {
                    paint.setColor(Color.WHITE); paint.setShader(outline); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(borderWidth);
                    layout.draw(canvas);
                }
            } finally {
                paint.setShader(oldShader); paint.setStyle(oldStyle); paint.setStrokeWidth(oldStroke);
                paint.setColor(oldColor); canvas.restoreToCount(save);
            }
        }
    }
}
