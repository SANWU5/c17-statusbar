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
    private volatile Settings settings = new Settings(null);
    private final Map<View, HeaderState> headers = new WeakHashMap<>();
    private final Map<View, StackState> stacks = new WeakHashMap<>();
    private final Map<View, FakeState> fakeClocks = new WeakHashMap<>();
    private final NotificationBigClockStack notificationStack = new NotificationBigClockStack();
    private final StatusBarFixedIcons fixedStatusIcons = new StatusBarFixedIcons();
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
        closingStatusIcons.setFailureListener(this::restoreClosingSources);
    }
    private final View.OnLayoutChangeListener headerLayoutListener = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if (overlay != null) overlay.measuredSettings = null;
        refresh();
    };

    private static final class Settings {
        final boolean enabled, glass, seconds, dateEnabled, footerEnabled, safeMode;
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
        final float tailWidth1, tailWidth2, tailWidth3;
        final float footerSize, footerWeight, footerOffsetX, footerOffsetY, footerMargin;
        final int colorLight, colorDark, dateColorLight, dateColorDark, footerColorLight, footerColorDark;
        final boolean clockLightAlpha, clockDarkAlpha, dateLightAlpha, dateDarkAlpha, footerLightAlpha, footerDarkAlpha;
        Settings(Bundle source) {
            safeMode = SafetyMode.enabled(source);
            enabled = bool(source, PREFIX + "enabled", false);
            glass = bool(source, PREFIX + "glass", true);
            glassBorderEnabled = bool(source, PREFIX + "glass_border_enabled", true);
            glassBorderWidth = number(source, PREFIX + "glass_border_width", .65f, 0f, Float.MAX_VALUE);
            borderColorLight = color(source, PREFIX + "glass_border_color_light", Color.WHITE);
            borderColorDark = color(source, PREFIX + "glass_border_color_dark", Color.WHITE);
            borderLightAlpha = alpha(source, PREFIX + "glass_border_color_light", borderColorLight);
            borderDarkAlpha = alpha(source, PREFIX + "glass_border_color_dark", borderColorDark);
            entryEffectEnabled = bool(source, PREFIX + "entry_effect_enabled", true);
            entryBlurRadius = number(source, PREFIX + "entry_blur_radius", 12f, 0f, Float.MAX_VALUE);
            entryFadeStrength = number(source, PREFIX + "entry_fade_strength", 100f, 0f, 100f);
            entryCompletion = number(source, PREFIX + "entry_completion", 85f, 1f, 100f);
            entryTravel = number(source, PREFIX + "entry_travel", 32f, 0f, Float.MAX_VALUE);
            notificationEdgeEnabled = bool(source, PREFIX + "notification_edge_enabled", true);
            notificationEdgeSafeDistance = number(source, PREFIX + "notification_edge_safe_distance", 18f, 0f, Float.MAX_VALUE);
            notificationEdgeRange = number(source, PREFIX + "notification_edge_range", 24f, 0f, Float.MAX_VALUE);
            notificationEdgeBlurRadius = number(source, PREFIX + "notification_edge_blur_radius", 8f, 0f, Float.MAX_VALUE);
            pattern = pattern(source, PREFIX + "pattern", "HH:mm");
            datePattern = pattern(source, PREFIX + "date_pattern", NotificationBigClockSettings.DATE_DEFAULT);
            dateEnabled = bool(source, PREFIX + "date_enabled", true);
            footerEnabled = bool(source, PREFIX + "footer_enabled", false);
            String footerFormat = string(source, PREFIX + "footer_pattern", "{text}");
            footerPattern = TimeFormat.contentValidationError(footerFormat) == null ? footerFormat : "{text}";
            footerText = string(source, PREFIX + "footer_text", "");
            seconds = TimeFormat.hasSeconds(pattern) || (dateEnabled && TimeFormat.hasSeconds(datePattern))
                    || (footerEnabled && TimeFormat.contentHasSeconds(footerPattern));
            scale = number(source, PREFIX + "scale", 100f, 0f, Float.MAX_VALUE);
            compactScale = number(source, PREFIX + "compact_scale", 36f, 0f, Float.MAX_VALUE);
            weight = number(source, PREFIX + "weight", 600f, 100f, 900f);
            // Retain the legacy preference in exports; collapsing never changes the selected weight.
            compactWeight = weight;
            offsetY = number(source, PREFIX + "offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            offsetX = number(source, PREFIX + "offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            maxSize = number(source, PREFIX + "max_size", 180f, 0f, Float.MAX_VALUE);
            compactMaxSize = number(source, PREFIX + "compact_max_size", 64f, 0f, Float.MAX_VALUE);
            compactOffsetX = number(source, PREFIX + "compact_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            compactOffsetY = number(source, PREFIX + "compact_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            letterSpacing = number(source, PREFIX + "letter_spacing", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateSize = number(source, PREFIX + "date_size", 15f, 0f, Float.MAX_VALUE);
            dateWeight = number(source, PREFIX + "date_weight", 600f, 100f, 900f);
            dateOffsetX = number(source, PREFIX + "date_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateOffsetY = number(source, PREFIX + "date_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            dateGap = number(source, PREFIX + "date_gap", 12f, 0f, Float.MAX_VALUE);
            notificationGap = bool(source, PREFIX + "notification_gap_enabled", true)
                    ? number(source, PREFIX + "notification_gap", 18f, 0f, Float.MAX_VALUE) : 0f;
            tailWidth1 = number(source, NotificationBigClockSettings.TAIL_WIDTH_1, 96f, 0f, Float.MAX_VALUE);
            tailWidth2 = number(source, NotificationBigClockSettings.TAIL_WIDTH_2, 92f, 0f, Float.MAX_VALUE);
            tailWidth3 = number(source, NotificationBigClockSettings.TAIL_WIDTH_3, 88f, 0f, Float.MAX_VALUE);
            footerSize = number(source, PREFIX + "footer_size", 13f, 0f, Float.MAX_VALUE);
            footerWeight = number(source, PREFIX + "footer_weight", 400f, 100f, 900f);
            footerOffsetX = number(source, PREFIX + "footer_offset_x", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            footerOffsetY = number(source, PREFIX + "footer_offset_y", 0f, -Float.MAX_VALUE, Float.MAX_VALUE);
            footerMargin = number(source, PREFIX + "footer_margin", 24f, 0f, Float.MAX_VALUE);
            font = string(source, PREFIX + "font", "native");
            alignment = string(source, PREFIX + "alignment", "center");
            dateAlignment = string(source, PREFIX + "date_alignment", "center");
            footerAlignment = string(source, PREFIX + "footer_alignment", "center");
            colorLight = color(source, PREFIX + "color_light", Color.WHITE);
            colorDark = color(source, PREFIX + "color_dark", Color.WHITE);
            dateColorLight = color(source, PREFIX + "date_color_light", Color.WHITE);
            dateColorDark = color(source, PREFIX + "date_color_dark", Color.WHITE);
            footerColorLight = color(source, PREFIX + "footer_color_light", Color.WHITE);
            footerColorDark = color(source, PREFIX + "footer_color_dark", Color.WHITE);
            clockLightAlpha = alpha(source, PREFIX + "color_light", colorLight);
            clockDarkAlpha = alpha(source, PREFIX + "color_dark", colorDark);
            dateLightAlpha = alpha(source, PREFIX + "date_color_light", dateColorLight);
            dateDarkAlpha = alpha(source, PREFIX + "date_color_dark", dateColorDark);
            footerLightAlpha = alpha(source, PREFIX + "footer_color_light", footerColorLight);
            footerDarkAlpha = alpha(source, PREFIX + "footer_color_dark", footerColorDark);
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
        float clockAlpha, dateAlpha, carrierAlpha;
        int nativeButtonVisibility;
        HeaderState(View header) {
            clock = new WeakReference<>(textView(header, "qs_footer_clock"));
            date = new WeakReference<>(textView(header, "oplus_date"));
            carrier = new WeakReference<>(childView(header, "qs_carrier_text"));
            statusIcons = new WeakReference<>(childView(header, "quick_qs_status_icons"));
            settingsButton = new WeakReference<>(childView(header, "settings_button"));
        }
        void hide() {
            TextView c = clock.get(), d = date.get();
            View operator = carrier.get();
            // Native transition writers may change alpha after our last frame; retain their latest value.
            if (!hidden || (c != null && c.getAlpha() != 0f)) clockAlpha = c == null ? 1f : c.getAlpha();
            if (!hidden || (d != null && d.getAlpha() != 0f)) dateAlpha = d == null ? 1f : d.getAlpha();
            if (!hidden || (operator != null && operator.getAlpha() != 0f)) carrierAlpha = operator == null ? 1f : operator.getAlpha();
            hidden = true;
            if (c != null) c.setAlpha(0f);
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
            TextView c = clock.get(), d = date.get();
            if (c != null) c.setAlpha(clockAlpha);
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
        settings = new Settings(source);
        if (ModuleLifecycle.removed()) {
            fixedStatusIcons.releaseRuntime();
            clearSeparateQsStatus();
        }
        fixedStatusIcons.configureDiagnostics();
        notificationStack.configure(source);
        View host = panel.get();
        if (host == null) host = separateQsPanel.get();
        if (host != null) host.post(() -> {
            fixedStatusIcons.resetFailure();
            closingStatusIcons.resetFailure();
            if (overlay != null) overlay.clearStyle();
            boolean wasActive = active;
            refresh();
            if (wasActive == active) notifyVisibilityChanged();
        });
    }

    /** Hook OplusQSSimpleHeader.onInit / onFinishInflate after native child initialization. */
    public void onHeaderInflated(View header) {
        if (header == null) return;
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
        if (statusIconsSettledClosed()) fixedStatusIcons.hide();
        fixedStatusIcons.setPhoneCaptureAllowed(statusIconsSettledClosed() && statusIconsBarState() == 0);
    }

    public void setReboundReader(ReboundReader reader) { reboundReader = reader; }

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
        fraction = NotificationBigClockModel.bounded(expandedFraction, 0f, 0f, 1f);
        barState = nativeBarState; qsExpanded = inQs;
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
        android.graphics.Matrix mapping = new android.graphics.Matrix();
        host.transformMatrixToGlobal(mapping); stack.transformMatrixToLocal(mapping);
        float[] point = {host.getWidth() * .5f, panelY};
        mapping.mapPoints(point);
        return NotificationBigClockModel.finite(point[1]) ? point[1] : panelY;
    }

    /** Before the native stack's View.draw(Canvas), including its hardware RenderNode path. */
    public void clipNotifications(View stack, Canvas canvas) {
        if (canvas == null || stack == null) return;
        float top = headerBottomInStack(stack);
        if (NotificationBigClockModel.finite(top)) {
            Rect clip = new Rect();
            if (canvas.getClipBounds(clip)) canvas.clipRect(clip.left, top, clip.right, clip.bottom);
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
        float f = NotificationBigClockModel.bounded(presentFraction, 0f, 0f, 1f);
        if (!eligible()) {
            FakeState previous = fakeClocks.remove(fakeClock);
            if (previous != null) previous.restore(fakeClock, f > 0f && f < 1f);
            return;
        }
        FakeState state = fakeClocks.get(fakeClock);
        if (state == null) { state = new FakeState(fakeClock); fakeClocks.put(fakeClock, state); }
        else state.captureNative(fakeClock);
        showClosingSource(fakeClock, phoneClock, f, true);
        // Keep the replaced copy suppressed through both directions, including spring zero crossings.
        // Native spring and horizontal page transitions retain ownership of every transform.
        fakeClock.setAlpha(0f);
        state.markApplied(fakeClock);
    }

    /** Bind all native right-side sources independently of the notification clock feature. */
    public void onFakeStatusChanged(View group, View header) {
        onFakeStatusChanged(group, header, null);
    }

    public void onFakeStatusChanged(View group, View header, View phoneAnchor) {
        fixedStatusIcons.setPhoneCaptureAllowed(statusIconsSettledClosed() && statusIconsBarState() == 0);
        if (phoneAnchor != null) fixedStatusIcons.observePhoneAnchor(phoneAnchor);
        HeaderState source = headers.get(header);
        if (source != null && source.fakeStatusIcons.get() != group)
            source.fakeStatusIcons = new WeakReference<>(group);
        if (source != null && source.phoneStatusIcons.get() != phoneAnchor)
            source.phoneStatusIcons = new WeakReference<>(phoneAnchor);
        refreshStatusIcons();
    }

    /** Exact QS plugin callback. It updates only the shared right slot, never notification layout. */
    public void onSeparateQsStatusChanged(View nativePanel, View icons, View phone, View fake,
            float displayedFraction, int nativeBarState, boolean settledClosed) {
        if (ModuleLifecycle.removed()) { clearSeparateQsStatus(); fixedStatusIcons.releaseRuntime(); return; }
        if (nativePanel == null || !NotificationBigClockModel.finite(displayedFraction)) return;
        if (separateQsPanel.get() != nativePanel) separateQsPanel = new WeakReference<>(nativePanel);
        if (separateQsIcons.get() != icons) separateQsIcons = new WeakReference<>(icons);
        if (separateQsFakeIcons.get() != fake) separateQsFakeIcons = new WeakReference<>(fake);
        if (separateQsPhone.get() != phone) separateQsPhone = new WeakReference<>(phone);
        separateQsFraction = NotificationBigClockModel.clamp(displayedFraction, 0f, 1f);
        separateQsBarState = nativeBarState;
        separateQsSettledClosed = settledClosed;
        fixedStatusIcons.setPhoneCaptureAllowed(statusIconsSettledClosed() && statusIconsBarState() == 0);
        if (phone != null) fixedStatusIcons.observePhoneAnchor(phone);
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

    private boolean statusIconsSettledClosed() { return panelSettledClosed && separateQsSettledClosed; }

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

    public void onStatusIconHorizontalProgressChanged() { fixedStatusIcons.onHorizontalProgressChanged(); }

    public void onStatusIconCopyBound(View copy, View copiedSource, int kind) {
        fixedStatusIcons.onNativeCopyBound(copy, copiedSource, kind);
        if (kind == StatusIconTransition.RIGHT && copiedSource != null) try {
            if (copiedSource.getId() > 0 && "status_bar_end_side_container_for_fake".equals(
                    copiedSource.getResources().getResourceEntryName(copiedSource.getId()))) {
                fixedStatusIcons.setPhoneCaptureAllowed(statusIconsSettledClosed() && statusIconsBarState() == 0);
                fixedStatusIcons.observePhoneAnchor(copiedSource);
            }
        } catch (Throwable ignored) { }
    }

    public boolean suppressStatusIconCopy(View copy, View copiedSource, int kind) {
        boolean closed = statusIconsSettledClosed();
        int state = statusIconsBarState();
        String gate = settings.safeMode ? "safe" : state != 0 ? "keyguard" : closed ? "closed" : "open";
        return fixedStatusIcons.suppressNativeCopy(copy, copiedSource, kind,
                !settings.safeMode && state == 0 && !closed, gate);
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
        if (!eligible()) {
            FakeState previous = fakeClocks.remove(notificationCopy);
            if (previous != null) previous.restore(notificationCopy, false);
            return;
        }
        FakeState state = fakeState(notificationCopy);
        float f = NotificationBigClockModel.bounded(presentFraction, 0f, 0f, 1f);
        showClosingSource(notificationCopy, phoneNotifications, f, false);
        notificationCopy.setAlpha(0f);
        state.markApplied(notificationCopy);
    }

    private void showClosingSource(View copy, View source, float displayedFraction, boolean clock) {
        if (source == null || copy == null) return;
        View old = clock ? closingPhoneClock.get() : closingPhoneNotifications.get();
        if (old != null && old != source) {
            closingStatusIcons.removeSource(old);
            FakeState retired = closingStatusSources.remove(old);
            if (retired != null) retired.restore(old, false);
            closingStatusIcons.resetFailure();
        }
        FakeState previous = closingStatusSources.get(source);
        if (previous != null) previous.captureNative(source);
        if (!closingStatusIcons.show(copy.getRootView(), source, displayedFraction)) {
            restoreClosingSources();
            return;
        }
        if (clock) closingPhoneClock = new WeakReference<>(source);
        else closingPhoneNotifications = new WeakReference<>(source);
        if (previous == null) {
            previous = new FakeState(source);
            closingStatusSources.put(source, previous);
        }
        // The real Phone window may become visible before shade reaches zero. Render one
        // copy in the shade overlay, then hand its original alpha back at the closed boundary.
        source.setAlpha(0f);
        previous.markApplied(source);
    }

    private void restoreClosingSources() {
        closingStatusIcons.hide();
        for (Map.Entry<View, FakeState> entry : new ArrayList<>(closingStatusSources.entrySet()))
            if (entry.getKey() != null) entry.getValue().restore(entry.getKey(), false);
        closingStatusSources.clear();
        closingPhoneClock.clear();
        closingPhoneNotifications.clear();
    }

    /** Runs after native targets are calculated and before native application/animation. */
    public void onStackLayoutUpdated(View stack) {
        if (stack == null) return;
        boolean enabled = eligible();
        float reserve = enabled ? footerReservation(stack) : 0f;
        float bottomBoundary = reserve > 0f ? stack.getHeight() - reserve : Float.NaN;
        notificationStack.apply(stack, enabled ? geometry().progress : 0f, enabled, bottomBoundary);
        // Native parallax keeps requesting row layouts after overDistance is reset to zero.
        // Sample those live springs in this frame, including their release and return motion.
        if (enabled && overlay != null) overlay.scheduleWidgets();
    }

    public void onStackLayoutStarting(View stack) { notificationStack.beginNativeLayout(stack); }

    /** Superclass and per-row state writers share one native application transaction. */
    public void onStackApplicationStarting(View stack) { notificationStack.beginNativeApplication(stack); }

    public void onStackApplicationFinished(View stack) { notificationStack.endNativeApplication(stack); }

    public Object withNativeTailOutline(View row, NotificationBigClockStack.NativeOutlineAction nativeDraw)
            throws Throwable {
        return eligible() ? notificationStack.withNativeTailOutline(row, nativeDraw) : nativeDraw.draw();
    }

    public Object withNativeTailContent(View content, Canvas canvas,
            NotificationBigClockStack.NativeOutlineAction nativeDraw) throws Throwable {
        return eligible() ? notificationStack.withNativeTailContent(content, canvas, nativeDraw) : nativeDraw.draw();
    }

    public int nativeTailTargetClipWidth(Object stateExtension, int nativeWidth) {
        return eligible() ? notificationStack.nativeTailTargetClipWidth(stateExtension, nativeWidth) : nativeWidth;
    }

    public int nativeTailRowClipWidth(Object rowExtension, int nativeWidth) {
        return eligible() ? notificationStack.nativeTailRowClipWidth(rowExtension, nativeWidth) : nativeWidth;
    }

    public void onNativeTailWidthChanged(Object rowExtension) {
        if (eligible()) notificationStack.onNativeTailWidthChanged(rowExtension);
    }

    public void onStackLayoutFinished(View stack) { notificationStack.endNativeLayout(stack); }

    public void onStackDetached(View stack) {
        notificationStack.detach(stack);
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
        fixedStatusIcons.hide();
        restoreClock();
    }

    /** QS switches may release the clock while the shared right-side slot remains owned. */
    private void restoreClock() {
        boolean wasActive = active;
        active = false;
        nativeHeaderRebound = nativeFooterRebound = 0f;
        restoreClosingSources();
        appliedReservation = Float.NaN;
        notificationStack.restoreAll();
        if (overlay != null) {
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
            restorePadding(entry.getKey(), entry.getValue());
            NotificationClockEdge.detach(entry.getKey());
        }
        if (wasActive) notifyVisibilityChanged();
    }

    public void onConfigurationChanged() {
        fixedStatusIcons.resetFailure();
        closingStatusIcons.resetFailure();
        if (overlay != null) overlay.clearStyle();
        refresh();
    }

    private void detachOverlay() {
        fixedStatusIcons.hide();
        restoreClosingSources();
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
                host.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT,
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
        float reservation = geometry().reservedBottom;
        if (entering || reservation != appliedReservation) {
            appliedReservation = reservation;
            for (Map.Entry<View, StackState> entry : new ArrayList<>(stacks.entrySet())) applyPadding(entry.getKey(), entry.getValue());
        }
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

    /** Notification/QS/landscape share this slot; keyguard and a settled close restore all sources. */
    private void refreshStatusIcons() {
        View host = panel.get();
        if (host == null) host = separateQsPanel.get();
        boolean closed = statusIconsSettledClosed();
        int state = statusIconsBarState();
        fixedStatusIcons.setPhoneCaptureAllowed(closed && state == 0);
        if (settings.safeMode || host == null || state != 0 || closed) {
            fixedStatusIcons.traceRuntimeStage(settings.safeMode ? "gate safe mode" : host == null ? "gate panel missing"
                    : state != 0 ? "gate keyguard state" : "gate settled closed");
            fixedStatusIcons.hide();
            return;
        }
        float displayed = Math.max(panelSettledClosed ? 0f : fraction,
                separateQsSettledClosed ? 0f : separateQsFraction);
        View qsIcons = separateQsIcons.get();
        if (!separateQsSettledClosed && qsIcons != null && qsIcons.isAttachedToWindow()
                && qsIcons.getVisibility() != View.GONE) {
            View phone = separateQsPhone.get();
            if (phone == null) phone = fixedStatusIcons.observedPhoneAnchor();
            View companion = null;
            for (HeaderState header : headers.values()) {
                View other = header.statusIcons.get();
                if (other != null && other.isAttachedToWindow() && other.getRootView() == qsIcons.getRootView()) {
                    companion = other; break;
                }
            }
            fixedStatusIcons.traceRuntimeStage("gate separate QS sources available");
            if (phone != null && fixedStatusIcons.show(qsIcons.getRootView(), qsIcons, phone,
                    separateQsFakeIcons.get(), companion, displayed)) return;
        }
        for (HeaderState header : headers.values()) {
            View icons = header.statusIcons.get(), anchor = header.phoneStatusIcons.get();
            if (anchor == null) anchor = fixedStatusIcons.observedPhoneAnchor();
            if (icons == null || anchor == null || !icons.isAttachedToWindow()) {
                fixedStatusIcons.traceRuntimeStage(icons == null ? "gate status row missing" : anchor == null
                        ? "gate native anchor missing" : "gate status row detached");
                continue;
            }
            fixedStatusIcons.traceRuntimeStage("gate sources available");
            if (fixedStatusIcons.show(icons.getRootView(), icons, anchor,
                    header.fakeStatusIcons.get(), qsIcons, displayed)) {
                return;
            }
        }
        if (headers.isEmpty() && qsIcons == null) fixedStatusIcons.traceRuntimeStage("gate header missing");
        fixedStatusIcons.hide();
    }

    private void applyPadding(View stack, StackState state) {
        if (stack == null) return;
        if (state.rawIntrinsic != Integer.MIN_VALUE)
            invoke(stack, "setIntrinsicPadding", new Class<?>[]{Integer.TYPE}, state.rawIntrinsic);
        if (NotificationBigClockModel.finite(state.rawTop))
            invoke(stack, "updateTopPadding", new Class<?>[]{Float.TYPE, Boolean.TYPE}, state.rawTop, false);
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

    /** Three real text widgets retain shaping, emoji fallback and native clock proportions. */
    private final class ClockView extends FrameLayout {
        final ClockText clock, timeProbe;
        final NotificationNativeClock nativeClock;
        NotificationClockFont.Resolver heightFont;
        final FrameLayout headerMotion, footerMotion;
        final TextView dateLabel, footerLabel, probe;
        final Map<Integer, Typeface> faces = new java.util.LinkedHashMap<Integer, Typeface>(40, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Typeface> entry) { return size() > 40; }
        };
        NotificationClockStyle.Style nativeStyle;
        Settings textSettings, measuredSettings;
        long textBucket = Long.MIN_VALUE;
        String time = "", date = "", footer = "";
        int measuredWidth, measuredHeight;
        float expandedSize, compactSize, expandedWidth, expandedHeight, compactHeight, compactAxisRatio, dateHeight;
        float safeTop, safeBottom;
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
            view.setSingleLine(true); view.setIncludeFontPadding(false); view.setClickable(false);
            view.setFocusable(false); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            view.setFontFeatureSettings("tnum");
            int inset = Math.max(1, Math.round(density() * 2f));
            view.setPadding(inset, inset, inset, inset);
            return view;
        }
        void clearStyle() {
            faces.clear(); nativeStyle = null; measuredSettings = null; textSettings = null;
            statusHeightResource = -1;
            heightFont = NotificationClockFont.create(getContext(), NotificationClockStyle.nativeDigitTemplate(panel.get()));
            if (nativeClock != null) nativeClock.cancel();
        }
        void scheduleWidgets() {
            observeWidgets();
            if (widgetsScheduled) return;
            widgetsScheduled = true;
            invalidate();
        }
        void observeWidgets() {
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
            long now = System.currentTimeMillis();
            Settings s = settings;
            long bucket = now / (s.seconds ? 1000L : 60000L);
            if (!force && s == textSettings && bucket == textBucket) return false;
            boolean animate = textSettings == s && textBucket != Long.MIN_VALUE && bucket != textBucket && hasVisibleTime();
            String nextTime = TimeFormat.format(s.pattern, now);
            String nextDate = s.dateEnabled ? TimeFormat.format(s.datePattern, now) : "";
            String nextFooter = s.footerEnabled ? TimeFormat.formatContent(s.footerPattern, s.footerText, now) : "";
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
            return getWidth() > 0 ? getWidth() : host != null && host.getWidth() > 0 ? host.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
        }
        int hostHeight() {
            View host = panel.get();
            return getHeight() > 0 ? getHeight() : host != null && host.getHeight() > 0 ? host.getHeight()
                    : getResources().getDisplayMetrics().heightPixels;
        }
        Typeface face(int requestedWeight, boolean time) {
            return face(requestedWeight, time, 1f);
        }
        Typeface face(int requestedWeight, boolean time, float heightRatio) {
            int weight = Math.max(100, Math.min(900, requestedWeight));
            if (time && "native".equals(settings.font) && heightFont != null) {
                Typeface variable = heightFont.typeface(weight, heightRatio);
                if (variable != null) return variable;
            }
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
            Typeface wanted = face(weight, view instanceof ClockText, heightRatio);
            if (view.getTypeface() != wanted) view.setTypeface(wanted);
            if (view.getTextSize() != size) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            float em = Math.min(64f, Math.max(-.5f, spacing));
            if (view.getLetterSpacing() != em) view.setLetterSpacing(em);
            String features = nativeStyle.fontFeatureSettings == null ? "tnum" : nativeStyle.fontFeatureSettings;
            if (!features.equals(view.getFontFeatureSettings())) view.setFontFeatureSettings(features);
            if (!text.contentEquals(view.getText())) view.setText(text);
        }
        void measureText(View view) {
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
        }
        void prepareMeasurements() {
            refreshText(false);
            updateSafeInsets();
            int width = hostWidth(), height = hostHeight();
            Settings s = settings;
            if (s == measuredSettings && width == measuredWidth && height == measuredHeight) return;
            TextView nativeClock = sourceClock();
            NotificationClockStyle.Style style = NotificationClockStyle.resolve(panel.get(),
                    nativeClock == null ? Typeface.DEFAULT : nativeClock.getTypeface());
            if (style != nativeStyle) { nativeStyle = style; faces.clear(); }
            float widthLimit = Math.max(1f, width - 48f * density());
            float spacing = nativeStyle.letterSpacing + s.letterSpacing;
            float max = NumericPolicy.textPixels((double) s.maxSize * density() * s.scale / 100d);
            expandedSize = fit(timeProbe, time, max, (int) s.weight, spacing, widthLimit, height * .44f);
            textStyle(timeProbe, time, expandedSize, (int) s.weight, spacing); measureText(timeProbe);
            expandedWidth = timeProbe.getMeasuredWidth();
            expandedHeight = expandedSize > 0f ? timeProbe.getMeasuredHeight() : 0f;
            float compactMax = NumericPolicy.textPixels((double) s.compactMaxSize * density());
            compactSize = Math.min(compactMax, NumericPolicy.textPixels((double) expandedSize * s.compactScale / 100d));
            compactAxisRatio = expandedSize > 0f ? compactSize / expandedSize : 1f;
            if ("native".equals(s.font) && heightFont != null && expandedSize > 0f) {
                // HGHT includes a fixed stroke/height base. Measure its real outline, never assume H is pixels.
                textStyle(timeProbe, time, expandedSize, (int) s.weight, spacing, compactAxisRatio);
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
        }
        NotificationBigClockModel.Frame frame() {
            prepareMeasurements();
            Settings s = settings;
            return NotificationBigClockModel.measured(hostHeight(), density(), safeTop, expandedHeight,
                    compactHeight, dateHeight, s.dateGap, s.notificationGap, s.offsetY,
                    s.compactOffsetY, s.dateOffsetY, s.weight, s.compactWeight, scrollY, 0f, fraction);
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
            float inset = 24f * density(), width = hostWidth();
            float x = "left".equals(alignment) ? inset : "right".equals(alignment)
                    ? width - inset - textWidth : (width - textWidth) / 2f;
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
            if (updating || !active || !eligible()) return;
            updating = true;
            try {
                if (readRebound) sampleRebound();
                NotificationBigClockModel.Frame f = frame(); Settings s = settings;
                boolean variableHeight = "native".equals(s.font) && heightFont != null;
                float heightRatio = Math.max(.001f, 1f + (compactAxisRatio - 1f) * f.progress);
                float size = variableHeight ? expandedSize : expandedSize + (compactSize - expandedSize) * f.progress;
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
                headerMotion.setAlpha(entryAlpha); clock.setAlpha(1f); dateLabel.setAlpha(1f); footerLabel.setAlpha(entryAlpha);
                float blur = s.entryEffectEnabled ? Math.min(64f, NumericPolicy.pixels(s.entryBlurRadius, density())) * (1f - easedReveal) : 0f;
                blur = Math.round(blur * 4f) / 4f;
                if (Build.VERSION.SDK_INT >= 31 && !entryBlurFailed && blur != appliedEntryBlur) {
                    try { EntryBlur.apply(headerMotion, footerMotion, blur); appliedEntryBlur = blur; }
                    catch (Throwable unavailable) {
                        entryBlurFailed = true;
                        ModuleDiagnostics.error("bigclock", "Clock entry blur unavailable; gesture fade retained", unavailable);
                    }
                }
                float x = s.offsetX + s.compactOffsetX * f.progress;
                boolean nativeReady = nativeClock != null && nativeClock.isAvailable()
                        && nativeClock.setTypography(clock.getTypeface(), size, clock.getCurrentTextColor(),
                                nativeStyle.letterSpacing + s.letterSpacing);
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
        static void apply(View header, View footer, float radius) {
            android.graphics.RenderEffect effect = radius <= 0f ? null
                    : android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL);
            header.setRenderEffect(effect);
            footer.setRenderEffect(effect);
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
            super(context); setSingleLine(true); setHorizontallyScrolling(false);
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
