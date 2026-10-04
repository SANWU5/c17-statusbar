// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.text.TextUtils;

/** The optional legacy width cache must not abort all text hooks on newer ROMs. */
final class NativeClockMeasurement {
    static final String SCALE_BASIS_VERSION = "clock_scale_basis_version";
    static final String LEGACY_SCALE_FACTOR = "clock_scale_legacy_factor";
    static final int NATIVE_PIXEL_BASIS = 2;
    private static final String NOTIFICATION_ICONS = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    private static final Map<Class<?>, NotificationAllocation> NOTIFICATION_ACCESS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, IconVisibility> ICON_ACCESS = new ConcurrentHashMap<>();
    private static final Method CLEAR_TEXT_LAYOUT = clearLayoutMethod();

    private static Method clearLayoutMethod() {
        try {
            Method method = TextView.class.getDeclaredMethod("nullLayouts");
            method.setAccessible(true); return method;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    /** The audited native setter can leave an ellipsized Layout when Paint changed directly. */
    static boolean clearTextLayout(TextView view) {
        if (view == null || view.getLayout() == null || CLEAR_TEXT_LAYOUT == null) return false;
        try { CLEAR_TEXT_LAYOUT.invoke(view); return true; }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    static Field widthField(Class<?> type) {
        if (type == null) return null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field field = current.getDeclaredField("actualWidth");
            if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())
                    || Modifier.isFinal(field.getModifiers())) return null;
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException inherited) { }
        catch (RuntimeException unavailable) { return null; }
        return null;
    }

    static boolean statClock(TextView view) {
        if (view == null) return false;
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
            if ("com.oplus.systemui.statusbar.widget.StatClock".equals(type.getName())) return true;
        return false;
    }

    /** StatClock's own DEX reads this resource from Context, in pixels, in every measure. */
    static float resourcePixels(TextView view) {
        if (!statClock(view)) return Float.NaN;
        Resources resources = view.getContext().getResources();
        try {
            int id = resources.getIdentifier("stat_clock_size", "dimen", "com.android.systemui");
            if (id == 0) id = resources.getIdentifier("stat_clock_size", "dimen", "com.oplus.systemui");
            if (id == 0) return Float.NaN;
            // PixelSize matches OEM's integer rounding. Older desktop fixtures lack it.
            Method getter;
            try { getter = resources.getClass().getMethod("getDimensionPixelSize", int.class); getter.setAccessible(true); }
            catch (NoSuchMethodException optional) { getter = null; }
            float pixels = getter == null ? Math.round(resources.getDimension(id)) : ((Number)getter.invoke(resources, id)).floatValue();
            return Float.isFinite(pixels) && pixels > 0f ? pixels : Float.NaN;
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return Float.NaN; }
    }

    /** Preserve a verified old missing-width-cache baseline, never guess from density or percent alone. */
    static float legacyFactor(int version, float explicitFactor, boolean oldMeasureMissing,
                              float capturedPixels, float nativePixels, float percent) {
        if (version >= NATIVE_PIXEL_BASIS) return 1f;
        if (Float.isFinite(explicitFactor) && explicitFactor > 0f) return explicitFactor;
        if (!oldMeasureMissing || !Float.isFinite(percent) || percent <= 250f
                || !Float.isFinite(capturedPixels) || capturedPixels <= 0f
                || !Float.isFinite(nativePixels) || nativePixels < capturedPixels * 2f) return 1f;
        return capturedPixels / nativePixels;
    }

    static int boundedWidth(int desired, int parentSpec, int budget) {
        int mode = View.MeasureSpec.getMode(parentSpec), size = View.MeasureSpec.getSize(parentSpec);
        int bound = mode == View.MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : size;
        if (budget >= 0) bound = Math.min(bound, budget);
        return Math.max(0, Math.min(Math.max(0, desired), bound));
    }

    /** Enabled StatClock follows its glyph width, like the OEM override, regardless of child spec. */
    static int clockWidth(TextView clock, int desired, int parentSpec, int budget) {
        if (statClock(clock)) return Math.max(0, desired);
        return boundedWidth(desired, parentSpec, budget);
    }

    /** Use the allocated outer start-side, never its WRAP_CONTENT descendants' historical width. */
    static int availableWidth(TextView clock) {
        View branch = clock;
        long occupied = 0;
        for (int depth = 0; depth < 10 && branch.getParent() instanceof ViewGroup; depth++) {
            ViewGroup parent = (ViewGroup)branch.getParent();
            // The audited StartSideExceptHeadsUpLayout inherits horizontal LinearLayout.
            // FrameLayout siblings are overlays, not additional horizontal allocation.
            if (!(parent instanceof FrameLayout) && !(parent instanceof LinearLayout)) return -1;
            occupied += (long)parent.getPaddingLeft() + parent.getPaddingRight() + margins(branch);
            if (horizontal(parent)) {
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View sibling = parent.getChildAt(i);
                    if (sibling == branch || sibling.getVisibility() == View.GONE) continue;
                    int reserved = reservedWidth(sibling, 0);
                    // An unmeasured custom notification host must not invent a display count.
                    if (reserved < 0) return -1;
                    occupied += (long)reserved + margins(sibling);
                }
            }
            if ("status_bar_start_side_container".equals(resourceName(parent))) {
                int width = parent.getMeasuredWidth();
                if (width <= 0) width = parent.getWidth();
                // No allocated slot yet: original MeasureSpec owns first/weighted measure.
                if (width <= 0) return -1;
                return Math.max(0, width - (int)Math.min(Integer.MAX_VALUE, occupied));
            }
            branch = parent;
        }
        return -1;
    }

    private static int reservedWidth(View view, int depth) {
        // Current native allocation is authoritative, even before layout catches up.
        // NotificationIconContainer retains every notification as a Java VISIBLE child;
        // its overflow/HIDDEN children must never enlarge this allocated slot.
        int measured = view.getMeasuredWidth();
        if (measured > 0) return measured;
        if (view instanceof ViewGroup && instanceOf(view, NOTIFICATION_ICONS))
            return notificationWidth((ViewGroup)view);
        int width = Math.max(0, view.getMinimumWidth());
        if (!(view instanceof ViewGroup)) width = Math.max(width, view.getWidth());
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.width > 0) width = Math.max(width, params.width);
        // A previously starved notification container can measure zero; preserve its native children.
        if (view instanceof ViewGroup && depth < 4) {
            ViewGroup group = (ViewGroup)view;
            long children = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.GONE) continue;
                int reserved = reservedWidth(child, depth + 1);
                if (reserved < 0) return -1;
                long childWidth = (long)reserved + margins(child);
                children = horizontal(group) ? children + childWidth : Math.max(children, childWidth);
            }
            children += (long)group.getPaddingLeft() + group.getPaddingRight();
            width = Math.max(width, (int)Math.min(Integer.MAX_VALUE, children));
        }
        return width;
    }

    /** Only recover a zero/starved native container from its real ICON/DOT states.
     * OEM onMeasure sums at most mMaxIcons plus one overflow slot. Java visibility
     * cannot distinguish that slot from the remaining hidden notification children.
     */
    private static int notificationWidth(ViewGroup group) {
        NotificationAllocation access = NOTIFICATION_ACCESS.computeIfAbsent(group.getClass(), NotificationAllocation::new);
        if (!access.ready) return -1;
        try {
            int maximum = access.maxIcons.getInt(group);
            if (maximum < 0) return -1;
            float start = ((Number)access.paddingStart.invoke(group)).floatValue();
            float end = ((Number)access.paddingEnd.invoke(group)).floatValue();
            if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0f || end < 0f) return -1;
            long width = (long)Math.ceil((double)start + end);
            int icons = 0; boolean dot = false;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.GONE) continue;
                IconVisibility visibility = ICON_ACCESS.computeIfAbsent(child.getClass(), IconVisibility::new);
                if (visibility.getter == null) return -1;
                int state = ((Number)visibility.getter.invoke(child)).intValue();
                if (state == 2) continue; // STATE_HIDDEN still has a measured child width.
                if (state == 0) { if (icons >= maximum) continue; icons++; }
                else if (state == 1) { if (dot) continue; dot = true; }
                else return -1;
                int childWidth = child.getMeasuredWidth();
                if (childWidth <= 0) childWidth = Math.max(0, child.getWidth());
                width += (long)childWidth + margins(child);
            }
            return (int)Math.min(Integer.MAX_VALUE, width);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return -1; }
    }

    private static final class NotificationAllocation {
        final Field maxIcons;
        final Method paddingStart, paddingEnd;
        final boolean ready;
        NotificationAllocation(Class<?> type) {
            Field maximum = null; Method start = null, end = null;
            try {
                Class<?> nativeClass = namedClass(type, NOTIFICATION_ICONS);
                if (nativeClass != null) {
                    maximum = nativeClass.getDeclaredField("mMaxIcons");
                    if (maximum.getType() != int.class || Modifier.isStatic(maximum.getModifiers())) maximum = null;
                    else maximum.setAccessible(true);
                    start = nativeClass.getMethod("getActualPaddingStart"); start.setAccessible(true);
                    end = nativeClass.getMethod("getActualPaddingEnd"); end.setAccessible(true);
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) { maximum = null; }
            maxIcons = maximum; paddingStart = start; paddingEnd = end;
            ready = maximum != null && start != null && end != null;
        }
    }

    private static final class IconVisibility {
        final Method getter;
        IconVisibility(Class<?> type) {
            Method method = null;
            try {
                Class<?> nativeClass = namedClass(type, "com.android.systemui.statusbar.StatusBarIconView");
                if (nativeClass != null) {
                    method = nativeClass.getMethod("getVisibleState");
                    if (method.getReturnType() != int.class) method = null;
                    else method.setAccessible(true);
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) { method = null; }
            getter = method;
        }
    }

    private static boolean instanceOf(Object view, String name) { return namedClass(view.getClass(), name) != null; }
    private static Class<?> namedClass(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (name.equals(current.getName())) return current;
        return null;
    }
    private static int margins(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return 0;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams)params;
        long total = margins.isMarginRelative() ? (long)Math.max(0, margins.getMarginStart()) + Math.max(0, margins.getMarginEnd())
                : (long)Math.max(0, margins.leftMargin) + Math.max(0, margins.rightMargin);
        return (int)Math.min(Integer.MAX_VALUE, total);
    }
    private static boolean horizontal(ViewGroup group) {
        if (group instanceof LinearLayout) return ((LinearLayout)group).getOrientation() == LinearLayout.HORIZONTAL;
        // The starved native notification container is a custom horizontal ViewGroup.
        return instanceOf(group, NOTIFICATION_ICONS);
    }
    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) return "";
        try { return "com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId()))
                ? view.getResources().getResourceEntryName(view.getId()) : ""; }
        catch (RuntimeException unsupported) { return ""; }
    }

    /** Capture/restore platform auto-size once; custom size must not be auto-fitted a second time. */
    static final class AutoSize {
        private int type;
        private int[] presets;
        private Method restore;
        private boolean owned;
        void configure(TextView view, boolean custom) {
            if (custom && (!owned || view.getAutoSizeTextType() != TextView.AUTO_SIZE_TEXT_TYPE_NONE)) {
                type = view.getAutoSizeTextType();
                if (type == TextView.AUTO_SIZE_TEXT_TYPE_NONE) return;
                try {
                    Method sizes = view.getClass().getMethod("getAutoSizeTextAvailableSizes");
                    restore = view.getClass().getMethod("setAutoSizeTextTypeUniformWithPresetSizes", int[].class, int.class);
                    presets = (int[])sizes.invoke(view);
                    if (presets == null || presets.length == 0) return;
                } catch (ReflectiveOperationException | RuntimeException unsupported) { return; }
                owned = true; view.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            } else if (!custom && owned) {
                owned = false;
                try { if (restore != null && presets != null) restore.invoke(view, presets, android.util.TypedValue.COMPLEX_UNIT_PX); }
                catch (ReflectiveOperationException | RuntimeException unsupported) { view.setAutoSizeTextTypeWithDefaults(type); }
            }
        }
    }

    /** Own only the enabled status clock's ellipsize, and restore native policy on disable. */
    static final class Ellipsize {
        private TextUtils.TruncateAt nativePolicy;
        private boolean owned;
        void configure(TextView view, boolean custom) {
            if (custom) {
                TextUtils.TruncateAt current = view.getEllipsize();
                if (!owned || current != null) { nativePolicy = current; owned = true; }
                if (current != null) view.setEllipsize(null);
            } else if (owned) {
                owned = false;
                if (view.getEllipsize() != nativePolicy) view.setEllipsize(nativePolicy);
            }
        }
    }
}
