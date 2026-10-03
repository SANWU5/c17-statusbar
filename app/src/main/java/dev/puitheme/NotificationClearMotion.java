// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Adds vertical entry and real notification clearance to the existing native clear button. */
public final class NotificationClearMotion {
    public static final String MOTION_ENABLED = "notification_clear_motion_enabled";
    public static final String SAFE_DISTANCE = "notification_clear_safe_distance";
    public static final String ENTRY_TRAVEL = "notification_clear_entry_travel";
    public static final String OFFSET_Y = "notification_clear_offset_y";
    public static final String LANDSCAPE_MASTER = "notification_clear_landscape_enabled";
    public static final String LANDSCAPE_OFFSET_X = "notification_clear_landscape_offset_x";
    public static final String LANDSCAPE_OFFSET_Y = "notification_clear_landscape_offset_y";
    public static Map<String, Boolean> booleanDefaults() { return Collections.singletonMap(LANDSCAPE_MASTER, false); }
    public static Map<String, Float> floatDefaults() {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(LANDSCAPE_OFFSET_X, 0f); values.put(LANDSCAPE_OFFSET_Y, 0f);
        return Collections.unmodifiableMap(values);
    }
    private static final String ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    public interface ReboundReader { void sample(View stack, float[] offsets); }
    private final Map<View, State> states = new WeakHashMap<>();
    private final ArrayList<State> liveStates = new ArrayList<>(2);
    private final Map<Class<?>, RowAccess> rowAccess = new HashMap<>();
    private final Map<View, Integer> nativeBottomMargins = new WeakHashMap<>();
    private final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private final ThreadLocal<Integer> nativeDepth = new ThreadLocal<>();
    private WeakReference<View> panel = new WeakReference<>(null);
    private ReboundReader rebound;
    private volatile boolean enabled, landscapeEnabled, propertyTracking, safeMode;
    private volatile float distance = 18f, travel = 32f, offset;
    private volatile float landscapeOffsetX, landscapeOffsetY;
    private float fraction, vertical;
    private int barState = -1;
    private boolean closed = true, marginAvailable, updating, warned;

    public void configure(Bundle settings) {
        safeMode = SafetyMode.enabled(settings);
        enabled = !safeMode && settings != null
                && settings.getBoolean(NotificationClearAppearance.MASTER, false)
                && settings.getBoolean(MOTION_ENABLED, false);
        distance = number(settings, SAFE_DISTANCE, 18f);
        travel = number(settings, ENTRY_TRAVEL, 32f);
        offset = number(settings, OFFSET_Y, 0f);
        landscapeEnabled = !safeMode && settings != null && settings.getBoolean(LANDSCAPE_MASTER, false);
        landscapeOffsetX = number(settings, LANDSCAPE_OFFSET_X, 0f);
        landscapeOffsetY = number(settings, LANDSCAPE_OFFSET_Y, 0f);
        propertyTracking = enabled || landscapeEnabled || ownedProperties();
        warned = false;
        for (int i = 0; i < liveStates.size(); i++) {
            State state = liveStates.get(i); state.dirty = true; state.unavailable = false;
            View button = state.button.get();
            if (button != null) button.post(() -> {
                if (enabled || landscapeEnabled) register(state); else unregister(state);
                update(state);
            });
        }
    }

    private static float number(Bundle settings, String key, float fallback) {
        return NumericPolicy.setting(key, settings == null ? null : settings.get(key), fallback);
    }

    public void setReboundReader(ReboundReader reader) { rebound = reader; }
    public void onMarginHookAvailable(boolean available) { marginAvailable = available; dirtyAll(); }

    /** Compatibility with the clock's content-bound reports. Landscape placement is
     * independent of clock visibility and notification geometry; it retains OEM motion. */
    public void onLandscapeRegion(View nativePanel, View coordinates, boolean active, float allowedLeft, float allowedRight) {
        // Do not re-anchor the button to a clock viewport on every draw.
    }

    private boolean landscapeScene() {
        View host = panel.get();
        if (host == null && !liveStates.isEmpty()) host = liveStates.get(0).panel.get();
        return host != null && host.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
    private boolean landscapeActive() { return landscapeEnabled && !safeMode && landscapeScene(); }
    private boolean portraitActive() { return enabled && !landscapeScene(); }
    private boolean sceneActive() { return landscapeActive() || portraitActive(); }

    public void onPanelChanged(View nativePanel, float displayed, int state, boolean settledClosed) {
        float next = finite(displayed) ? Math.max(0f, Math.min(1f, displayed)) : 0f;
        if (panel.get() == nativePanel && fraction == next && barState == state && closed == settledClosed) return;
        if (panel.get() != nativePanel) panel = new WeakReference<>(nativePanel);
        fraction = next; barState = state; closed = settledClosed;
        dirtyAll();
    }

    public void onPanelTranslationChanged(View nativePanel, float translation) {
        if (panel.get() != nativePanel) return;
        float next = finite(translation) ? translation : 0f;
        if (vertical == next) return;
        vertical = next; dirtyAll();
    }

    public void onScrollChanged(View stack) {
        for (int i = 0; i < liveStates.size(); i++) {
            State state = liveStates.get(i);
            if (state.stack.get() == stack) state.dirty = true;
        }
    }

    public void onControllerChanged(Object controller, boolean bindingChanged) {
        if (bindingChanged) bindController(controller);
        for (int i = 0; i < liveStates.size(); i++) {
            State state = liveStates.get(i);
            if (state.controller.get() == controller) state.dirty = true;
        }
    }

    /** The global setter hooks use identity lookup, never a reflective class walk. */
    public boolean tracks(View button) { return propertyTracking && states.containsKey(button); }
    public boolean needsNativeScope() { return propertyTracking && !liveStates.isEmpty(); }

    private boolean ownedProperties() {
        for (int i = 0; i < liveStates.size(); i++) if (liveStates.get(i).properties.owned) return true;
        return false;
    }

    private void dirtyAll() {
        for (int i = 0; i < liveStates.size(); i++) liveStates.get(i).dirty = true;
    }

    public void bindController(Object controller) {
        if (!QsTileAppearance.type(controller, NotificationClearAppearance.CONTROLLER_CLASS)) return;
        Object owner = QsTileAppearance.field(controller, "panelView");
        Object stack = QsTileAppearance.field(controller, "stackScroller");
        if (!(owner instanceof View) || !(stack instanceof ViewGroup)) return;
        for (String key : new String[]{"clearAll", "showingView"}) {
            Object value = QsTileAppearance.field(controller, key);
            if (!(value instanceof View) || !isButton((View) value)) continue;
            View button = (View) value;
            State previous = states.get(button);
            if (previous != null && previous.controller.get() == controller
                    && previous.stack.get() == stack && previous.panel.get() == owner) {
                previous.dirty = true; previous.unavailable = false; register(previous); continue;
            }
            if (previous != null) detach(button);
            State state = new State(button, controller, (View) owner, (ViewGroup) stack);
            states.put(button, state);
            liveStates.add(state);
            button.addOnAttachStateChangeListener(state.attachment);
            register(state);
        }
    }

    /** Controller getters must see their native properties when starting another animation. */
    public NativeScope beforeNative(Object controller) {
        int depth = nativeDepth.get() == null ? 0 : nativeDepth.get();
        nativeDepth.set(depth + 1);
        if (depth == 0) for (int i = 0; i < liveStates.size(); i++) {
            State state = liveStates.get(i);
            View button = state.button.get();
            if (state.controller.get() != controller || button == null || !state.properties.owned) continue;
            // Let a native layout write the same integer as our previous offset without
            // mistaking it for module-owned geometry. Reapply before the next draw below.
            if (landscapeActive()) { restoreHorizontal(state); state.dirty = true; }
            // The confirmed native controller reads getAlpha when starting its fade; it
            // never reads translationY. Restore just that input in these rare scopes.
            writing.set(Boolean.TRUE);
            try { if (button.getAlpha() == state.properties.appliedAlpha
                    && button.getAlpha() != state.properties.nativeAlpha) button.setAlpha(state.properties.nativeAlpha); }
            finally { writing.remove(); }
        }
        return new NativeScope(controller);
    }

    public final class NativeScope implements AutoCloseable {
        private final Object controller;
        private boolean finished;
        NativeScope(Object controller) { this.controller = controller; }
        @Override public void close() {
            if (finished) return;
            finished = true;
            int depth = nativeDepth.get() == null ? 0 : nativeDepth.get();
            if (depth <= 1) {
                nativeDepth.remove();
                try {
                    for (int i = 0; i < liveStates.size(); i++) {
                        State state = liveStates.get(i);
                        if (state.controller.get() != controller) continue;
                        View button = state.button.get();
                        if (button == null) continue;
                        if (!landscapeActive()) {
                            restoreHorizontal(state);
                        }
                        if (!state.properties.owned) continue;
                        state.properties.capture(button.getAlpha(), button.getTranslationY());
                        if (!sceneActive()) restore(state);
                        else {
                            state.properties.reapply();
                            writeComposed(state, button);
                            if (landscapeActive()) update(state);
                        }
                    }
                } catch (Throwable unavailable) {
                    for (int i = 0; i < liveStates.size(); i++) {
                        State state = liveStates.get(i);
                        if (state.controller.get() != controller) continue;
                        restoreFailedWrite(state); setReservation(state, 0); state.blocked = false;
                    }
                    reportUnavailable(unavailable);
                }
            } else nativeDepth.set(depth - 1);
        }
    }

    /** Precise native setters preserve even a new zero value while the module also owns zero. */
    public float nativeAlpha(View button, float value) {
        State state = states.get(button);
        if (state == null || Boolean.TRUE.equals(writing.get())) return value;
        return state.properties.alpha(value, nativeDepth.get() != null || !sceneActive());
    }

    public float nativeTranslationY(View button, float value) {
        State state = states.get(button);
        if (state == null || Boolean.TRUE.equals(writing.get())) return value;
        if (state.properties.nativeY != value) state.dirty = true;
        return state.properties.translation(value, nativeDepth.get() != null || !sceneActive());
    }

    public boolean suppressTouchDown(View button, MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        State state = states.get(button);
        return sceneActive() && state != null && state.properties.owned
                && (state.blocked || state.properties.appliedAlpha <= .08f);
    }

    public int adjustEmptyBottomMargin(View stack, int nativeMargin, int previousMargin) {
        nativeBottomMargins.put(stack, Math.max(0, nativeMargin));
        return Math.max(previousMargin, reservation(stack));
    }

    /** The captured OEM getScrollRange does not call getEmptyBottomMarginInternal. */
    public int adjustScrollRange(View stack, int nativeRange, int previousRange) {
        int reserve = reservation(stack);
        if (reserve <= 0) return previousRange;
        Integer empty = null;
        Object content = QsTileAppearance.call(stack, "getContentHeight");
        Object height = QsTileAppearance.field(stack, "mMaxLayoutHeight");
        if (content instanceof Number && height instanceof Number) {
            int contentHeight = ((Number) content).intValue();
            if (Boolean.TRUE.equals(QsTileAppearance.field(stack, "mShouldUseSplitNotificationShade"))) {
                Object minimum = QsTileAppearance.field(stack, "mSplitShadeMinContentHeight");
                if (!(minimum instanceof Number)) return previousRange;
                contentHeight = Math.max(contentHeight, ((Number) minimum).intValue());
            }
            empty = Math.max(0, ((Number) height).intValue() - contentHeight);
        }
        if (empty == null) empty = nativeBottomMargins.get(stack);
        if (empty == null) return previousRange;
        return Math.max(previousRange, scrollRange(nativeRange, reserve, empty));
    }

    public static int scrollRange(int nativeRange, int reservation, int nativeEmpty) {
        long result = (long) Math.max(0, nativeRange) + Math.max(0L, (long) reservation - Math.max(0, nativeEmpty));
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private int reservation(View stack) {
        if (!portraitActive() || closed || barState != 0 || !marginAvailable) return 0;
        int result = 0;
        for (int i = 0; i < liveStates.size(); i++) {
            State state = liveStates.get(i); View button = state.button.get();
            if (state.stack.get() == stack && button != null && button.isAttachedToWindow())
                result = Math.max(result, state.reservation);
        }
        return result;
    }

    public void detach(View button) {
        State state = states.remove(button);
        if (state == null) return;
        liveStates.remove(state);
        restore(state); restoreHorizontal(state); setReservation(state, 0); state.blocked = false; unregister(state);
        button.removeOnAttachStateChangeListener(state.attachment);
        View stack = state.stack.get();
        if (stack != null) nativeBottomMargins.remove(stack);
    }

    private static boolean isButton(View view) { return QsTileAppearance.type(view, NotificationClearAppearance.BUTTON_CLASS); }

    private void register(State state) {
        View button = state.button.get();
        if ((!enabled && !landscapeEnabled) || button == null || !button.isAttachedToWindow()) return;
        ViewTreeObserver current = button.getViewTreeObserver();
        if (state.observer == current || !current.isAlive()) return;
        unregister(state);
        state.observer = current;
        current.addOnPreDrawListener(state.preDraw);
    }

    private void unregister(State state) {
        try { if (state.observer != null && state.observer.isAlive()) state.observer.removeOnPreDrawListener(state.preDraw); }
        catch (Throwable ignored) { }
        state.observer = null;
    }

    private void update(State state) {
        if (nativeDepth.get() != null) return;
        View button = state.button.get(), host = state.panel.get();
        ViewGroup stack = state.stack.get();
        if (button == null || state.unavailable) return;
        if (!state.dirty && !state.geometryChanged(button, stack)) return;
        state.dirty = false;
        if (updating) { state.dirty = true; return; }
        updating = true;
        try { updateGeometry(state, button, host, stack); }
        finally { state.geometryChanged(button, stack); updating = false; }
    }

    private void updateGeometry(State state, View button, View host, ViewGroup stack) {
        state.properties.capture(button.getAlpha(), button.getTranslationY());
        state.blocked = false;
        if (landscapeScene()) {
            updateLandscape(state, button, host, stack);
            return;
        }
        restoreHorizontal(state);
        if (!portraitActive() || !marginAvailable
                || closed || barState != 0 || host == null || host != panel.get()
                || stack == null || !button.isAttachedToWindow() || !host.isAttachedToWindow()
                || !stack.isAttachedToWindow() || button.getVisibility() != View.VISIBLE
                || button.getWidth() <= 0 || button.getHeight() <= 0
                || !(button.getParent() instanceof View) || button.getWindowToken() == null
                || button.getWindowToken() != host.getWindowToken() || stack.getWindowToken() != host.getWindowToken()) {
            restore(state); setReservation(state, 0); return;
        }
        try {
            View parent = (View) button.getParent();
            state.geometry(parent);
            float density = button.getResources().getDisplayMetrics().density;
            float gap = NumericPolicy.layoutPixels(distance, density);
            float fadeBand = Math.max(1f, NumericPolicy.layoutPixels(12f, density));
            float userOffset = NumericPolicy.pixels(offset, density);
            float scaleY = button.getScaleY();
            if (!finite(scaleY) || scaleY <= 0f) { restore(state); setReservation(state, 0); return; }
            float buttonHeight = button.getHeight() * scaleY;
            float nativeTop = button.getTop() + state.properties.nativeY + button.getPivotY() * (1f - scaleY);
            float bottom = visibleBottom(stack, parent, state);
            float safeBottom = parent.getHeight() - parent.getPaddingBottom();
            WindowInsets insets = host.getRootWindowInsets();
            if (insets != null) {
                state.rectangle.set(0, 0, host.getWidth(), Math.max(0, host.getHeight() - insets.getSystemWindowInsetBottom()));
                state.map(host, parent, state.rectangle);
                safeBottom = Math.min(safeBottom, state.rectangle.bottom);
            }
            state.rebounds[0] = state.rebounds[1] = 0f;
            if (rebound != null) rebound.sample(stack, state.rebounds);
            float footerRebound = finite(state.rebounds[1]) ? state.rebounds[1] : 0f;
            Frame frame = state.frame;
            boolean customMotion = portraitActive();
            frameInto(frame, customMotion ? fraction : 1f, nativeTop, buttonHeight, bottom, safeBottom,
                    gap, customMotion ? NumericPolicy.pixels(travel, density) : 0f,
                    customMotion ? userOffset : 0f, customMotion ? vertical + footerRebound : 0f, fadeBand);
            // Reserve stable unpressed geometry. Spring/press transforms must never feed
            // a changing value back into the native stack's layout on each frame.
            int reserve = NumericPolicy.layoutPixels(button.getHeight() + gap + fadeBand + Math.max(0f, -userOffset));
            state.blocked = frame.blocked;
            state.properties.apply(frame.alpha, frame.top - nativeTop);
            writeComposed(state, button);
            setReservation(state, marginAvailable ? reserve : 0);
            state.geometryChanged(button, stack);
        } catch (Throwable unavailable) {
            restoreFailedWrite(state); setReservation(state, 0); state.blocked = false;
            state.unavailable = true;
            reportUnavailable(unavailable);
        }
    }

    /** Independent landscape placement composes with every native panel/page frame.
     * It never uses notification count, viewport anchors, or portrait entry springs. */
    private void updateLandscape(State state, View button, View host, ViewGroup stack) {
        setReservation(state, 0);
        if (!landscapeActive() || closed || barState != 0 || host == null || host != panel.get()
                || stack == null || !(button.getParent() instanceof View)
                || !button.isAttachedToWindow() || !host.isAttachedToWindow() || !stack.isAttachedToWindow()
                || button.getVisibility() != View.VISIBLE || button.getWidth() <= 0 || button.getHeight() <= 0
                || button.getWindowToken() == null || button.getWindowToken() != host.getWindowToken()
                || stack.getWindowToken() != host.getWindowToken()) {
            restoreHorizontal(state); restore(state); return;
        }
        try {
            float density = button.getResources().getDisplayMetrics().density;
            state.horizontal.capture(button.getLeft(), button.getTranslationX());
            float x = NumericPolicy.pixels(landscapeOffsetX, density);
            long left = (long) state.horizontal.nativeLeft + Math.round(x);
            if (left < Integer.MIN_VALUE || left > Integer.MAX_VALUE)
                throw new IllegalStateException("Native landscape button layout offset unavailable");
            state.horizontal.apply(button, (int) left);
            state.properties.apply(1f, NumericPolicy.pixels(landscapeOffsetY, density));
            writeComposed(state, button);
        } catch (Throwable unavailable) {
            restoreFailedWrite(state); state.blocked = false; state.unavailable = true;
            reportUnavailable(unavailable);
        }
    }

    private void restoreHorizontal(State state) {
        View button = state.button.get();
        if (button != null) state.horizontal.release(button);
    }

    private void writeComposed(State state, View button) {
        writing.set(Boolean.TRUE);
        try {
            if (button.getTranslationY() != state.properties.appliedY) button.setTranslationY(state.properties.appliedY);
            if (button.getAlpha() != state.properties.appliedAlpha) button.setAlpha(state.properties.appliedAlpha);
        } finally { writing.remove(); }
    }

    private void reportUnavailable(Throwable unavailable) {
        if (warned) return;
        warned = true;
        ModuleDiagnostics.error("clear-motion", "Native clear motion unavailable; original button retained", unavailable);
    }

    private void setReservation(State state, int value) {
        if (state.reservation == value) return;
        state.reservation = value;
        View stack = state.stack.get();
        if (stack != null && stack.isAttachedToWindow()) stack.requestLayout();
    }

    private float visibleBottom(ViewGroup stack, View parent, State state) throws Exception {
        float bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child == null) continue;
            RowAccess access = rowAccess.get(child.getClass());
            if (access == null) { access = new RowAccess(child.getClass()); rowAccess.put(child.getClass(), access); }
            if (!access.row || child.getVisibility() != View.VISIBLE || child.getAlpha() <= .001f
                    || child.getWidth() <= 0 || access.removed(child)) continue;
            Object height = access.height(child);
            if (!(height instanceof Number)) throw new IllegalStateException("Native row actual height unavailable");
            float top = access.clip(child, true);
            float end = ((Number) height).floatValue() - access.clip(child, false);
            if (!(end > top)) continue;
            state.rectangle.set(0, top, child.getWidth(), end);
            state.map(child, parent, state.rectangle);
            bottom = Math.max(bottom, state.rectangle.bottom);
        }
        return bottom;
    }

    private static final Object[] NO_ARGUMENTS = new Object[0];
    private static Field cachedField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field field = current.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (NoSuchFieldException ignored) { }
        return null;
    }
    private static final class RowAccess {
        final boolean row;
        final Method actualHeight, removed, dismissed;
        final Field topClip, bottomClip;
        RowAccess(Class<?> type) {
            boolean nativeRow = false;
            for (Class<?> current = type; current != null; current = current.getSuperclass())
                if (current.getName().equals(ROW)) { nativeRow = true; break; }
            row = nativeRow;
            actualHeight = row ? method(type, "getActualHeight") : null;
            removed = row ? method(type, "isRemoved") : null; dismissed = row ? method(type, "isDismissed") : null;
            topClip = row ? cachedField(type, "mClipTopAmount") : null;
            bottomClip = row ? cachedField(type, "mClipBottomAmount") : null;
        }
        static Method method(Class<?> type, String name) {
            try { Method method = type.getMethod(name); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { return null; }
        }
        Object height(View view) throws Exception { return actualHeight == null ? null : actualHeight.invoke(view, NO_ARGUMENTS); }
        boolean removed(View view) throws Exception {
            return removed != null && Boolean.TRUE.equals(removed.invoke(view, NO_ARGUMENTS))
                    || dismissed != null && Boolean.TRUE.equals(dismissed.invoke(view, NO_ARGUMENTS));
        }
        float clip(View view, boolean top) throws Exception {
            Field field = top ? topClip : bottomClip;
            return field == null ? 0f : Math.max(0, field.getInt(view));
        }
    }

    private void restore(State state) {
        View button = state.button.get();
        Properties properties = state.properties;
        if (button == null || !properties.owned) return;
        float alpha = button.getAlpha(), y = button.getTranslationY();
        properties.release(alpha, y);
        propertyTracking = enabled || landscapeEnabled || ownedProperties();
        writing.set(Boolean.TRUE);
        try {
            if (alpha == properties.appliedAlpha) button.setAlpha(properties.nativeAlpha);
            if (y == properties.appliedY) button.setTranslationY(properties.nativeY);
        } finally { writing.remove(); }
    }

    /** A setter may write and then throw; never recapture an earlier module alpha as native. */
    private void restoreFailedWrite(State state) {
        View button = state.button.get();
        restoreHorizontal(state);
        if (button == null || !state.properties.owned) return;
        state.properties.owned = false;
        propertyTracking = enabled || landscapeEnabled || ownedProperties();
        writing.set(Boolean.TRUE);
        try {
            try { button.setAlpha(state.properties.nativeAlpha); } catch (Throwable ignored) { }
            try { button.setTranslationY(state.properties.nativeY); } catch (Throwable ignored) { }
        } finally { writing.remove(); }
    }

    /** Native gesture/page writers retain translations, scale, visibility and transition alpha. */
    private final class State {
        final WeakReference<View> button, panel;
        final WeakReference<ViewGroup> stack;
        final WeakReference<Object> controller;
        final Properties properties;
        final Horizontal horizontal = new Horizontal();
        final float[] rebounds = new float[2];
        final Frame frame = new Frame(0f, 0f, true);
        final Field animationRunning;
        Matrix inverse, global, mapping;
        RectF rectangle;
        ViewTreeObserver observer;
        int reservation;
        boolean blocked, dirty = true, unavailable;
        int width, height, nativeTop, nativeLeft, parentWidth, parentHeight, parentPadding, parentLeftPadding, parentRightPadding, childCount, visibility, orientation;
        float scale, pivot,translationX;
        ViewParentKey parentKey;
        final ViewTreeObserver.OnPreDrawListener preDraw = () -> { update(State.this); return true; };
        final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { dirty = true; register(State.this); update(State.this); }
            @Override public void onViewDetachedFromWindow(View view) {
                restore(State.this); restoreHorizontal(State.this); setReservation(State.this, 0); blocked = false; unregister(State.this);
            }
        };
        State(View button, Object controller, View panel, ViewGroup stack) {
            this.button = new WeakReference<>(button); this.controller = new WeakReference<>(controller);
            this.panel = new WeakReference<>(panel); this.stack = new WeakReference<>(stack);
            properties = new Properties(button.getAlpha(), button.getTranslationY());
            animationRunning = cachedField(stack.getClass(), "mAnimationRunning");
        }
        boolean geometryChanged(View button, ViewGroup stack) {
            View parent = button.getParent() instanceof View ? (View) button.getParent() : null;
            int newWidth = button.getWidth(), newHeight = button.getHeight(), newTop = button.getTop();
            int newLeft = button.getLeft(), newParentWidth = parent == null ? 0 : parent.getWidth();
            int newParentHeight = parent == null ? 0 : parent.getHeight(), padding = parent == null ? 0 : parent.getPaddingBottom();
            int leftPadding = parent == null ? 0 : parent.getPaddingLeft(), rightPadding = parent == null ? 0 : parent.getPaddingRight();
            int children = stack == null ? 0 : stack.getChildCount(), shown = button.getVisibility();
            int newOrientation = button.getResources().getConfiguration().orientation;
            float newScale = button.getScaleY(), newPivot = button.getPivotY(),newTranslationX=button.getTranslationX();
            boolean changed = width != newWidth || height != newHeight || nativeTop != newTop || nativeLeft != newLeft
                    || parentWidth != newParentWidth || parentLeftPadding != leftPadding || parentRightPadding != rightPadding
                    || parentHeight != newParentHeight || parentPadding != padding || childCount != children
                    || orientation != newOrientation || visibility != shown || scale != newScale || pivot != newPivot||translationX!=newTranslationX
                    || parentKey == null || parentKey.parent.get() != parent;
            width = newWidth; height = newHeight; nativeTop = newTop; parentHeight = newParentHeight;
            nativeLeft = newLeft; parentWidth = newParentWidth; parentLeftPadding = leftPadding; parentRightPadding = rightPadding;
            orientation = newOrientation; parentPadding = padding; childCount = children; visibility = shown; scale = newScale; pivot = newPivot;translationX=newTranslationX;
            if (parentKey == null || parentKey.parent.get() != parent) parentKey = new ViewParentKey(parent);
            if (animationRunning != null && stack != null) try { changed |= animationRunning.getBoolean(stack); }
            catch (IllegalAccessException ignored) { }
            return changed;
        }
        void geometry(View parent) {
            if (inverse == null) { inverse = new Matrix(); global = new Matrix(); mapping = new Matrix(); rectangle = new RectF(); }
            global.reset(); parent.transformMatrixToGlobal(global);
            if (!global.invert(inverse)) throw new IllegalStateException("Native button parent matrix unavailable");
        }
        void map(View source, View parent, RectF bounds) {
            global.reset(); source.transformMatrixToGlobal(global);
            mapping.set(inverse); mapping.preConcat(global); mapping.mapRect(bounds);
        }
    }

    private static final class ViewParentKey {
        final WeakReference<View> parent;
        ViewParentKey(View parent) { this.parent = new WeakReference<>(parent); }
    }

    static final class Horizontal {
        int nativeLeft, appliedLeft;
        float nativeX;
        boolean owned;
        void capture(int left, float x) {
            if (!owned || left != appliedLeft)nativeLeft=left;
            // This owner never writes translationX; every observed value is a real OEM writer.
            // The layout offset remains constant while native page translations keep animating.
            nativeX=x;
        }
        void apply(View button, int left) {
            long movement = (long) left - button.getLeft();
            if (movement < Integer.MIN_VALUE || movement > Integer.MAX_VALUE)
                throw new IllegalStateException("Native clear button layout offset unavailable");
            appliedLeft = left; owned = true;
            if (movement != 0L) button.offsetLeftAndRight((int) movement);
        }
        void release(View button) {
            if (!owned) return;
            owned = false;
            // A later native layout owns its new position; do not replay an older baseline over it.
            long movement = (long) nativeLeft - button.getLeft();
            if (button.getLeft() == appliedLeft && movement != 0L
                    && movement >= Integer.MIN_VALUE && movement <= Integer.MAX_VALUE)
                button.offsetLeftAndRight((int) movement);
        }
    }

    static final class Properties {
        float nativeAlpha, nativeY, appliedAlpha, appliedY, fade = 1f, delta;
        boolean owned;
        Properties(float alpha, float y) { nativeAlpha = alpha; nativeY = y; }
        void apply(float alphaFactor, float yDelta) {
            fade = alphaFactor; delta = yDelta; owned = true;
            appliedAlpha = nativeAlpha * fade; appliedY = nativeY + delta;
        }
        void reapply() { appliedAlpha=nativeAlpha*fade;appliedY=nativeY+delta; }
        float alpha(float value, boolean nativeScope) {
            nativeAlpha = value;
            if (!owned || nativeScope) return value;
            return appliedAlpha = value * fade;
        }
        float translation(float value, boolean nativeScope) {
            nativeY = value;
            if (!owned || nativeScope) return value;
            return appliedY = value + delta;
        }
        void capture(float actualAlpha, float actualY) {
            if (!owned || actualAlpha != appliedAlpha) nativeAlpha = actualAlpha;
            if (!owned || actualY != appliedY) nativeY = actualY;
        }
        void release(float actualAlpha, float actualY) {
            if (owned) capture(actualAlpha, actualY);
            owned = false;
        }
    }

    public static final class Frame {
        public float top, alpha;
        public boolean blocked;
        Frame(float top, float alpha, boolean blocked) { this.top = top; this.alpha = alpha; this.blocked = blocked; }
    }

    public static Frame frame(float fraction, float nativeTop, float height, float rowBottom, float safeBottom,
            float distance, float travel, float offset, float motion, float fadeBand) {
        Frame result = new Frame(0f, 0f, true);
        frameInto(result, fraction, nativeTop, height, rowBottom, safeBottom, distance, travel, offset, motion, fadeBand);
        return result;
    }

    private static void frameInto(Frame result, float fraction, float nativeTop, float height, float rowBottom, float safeBottom,
            float distance, float travel, float offset, float motion, float fadeBand) {
        float reveal = smooth(clamp(fraction / .85f));
        float top = nativeTop + offset + motion - travel * (1f - reveal);
        float gap = Math.max(0f, distance), fade = 1f;
        boolean blocked = false;
        if (finite(rowBottom)) {
            float required = rowBottom + gap;
            float available = safeBottom - required - height;
            fade = smooth(clamp(available / Math.max(1f, fadeBand)));
            blocked = available <= 0f;
            top = Math.max(top, required);
        }
        float limit = safeBottom - height;
        if (top > limit) { blocked = true; fade = 0f; top = limit; }
        float alpha = reveal * fade;
        result.top = NumericPolicy.drawPixels(top); result.alpha = alpha; result.blocked = blocked || alpha <= .08f;
    }

    private static float clamp(float value) { return finite(value) ? Math.max(0f, Math.min(1f, value)) : 0f; }
    private static float smooth(float value) { return value * value * (3f - 2f * value); }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
