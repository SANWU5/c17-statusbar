// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Projects native notification targets; SystemUI still applies, animates and hit-tests each row. */
public final class NotificationBigClockStack {
    private static final String PREFIX = "notification_big_clock_";
    private static final String ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String CONTENT = "com.android.systemui.statusbar.notification.row.NotificationContentView";
    private static final String CHILDREN = "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";
    private static final String SPOTLIGHT_SPEC = "com.oplus.systemui.notification.material.NotificationSpotLightDelegate$ClipSpec";
    private static final String SOURCE = "C17 notification stack";
    private static volatile boolean nativeHooksAvailable;
    private static volatile boolean nativeWidthHooksAvailable;
    private volatile Settings settings = new Settings(null);
    private final Map<View, StackState> stacks = new WeakHashMap<>();
    private final Map<Class<?>, StateAccess> access = new WeakHashMap<>();
    private final Map<Class<?>, RowAccess> rowAccess = new WeakHashMap<>();
    private final Map<Class<?>, StackAccess> stackAccess = new WeakHashMap<>();
    private final Map<View, SavedState> tailOutlines = new WeakHashMap<>();
    private final Map<View, SavedState> recentTailOutlines = new WeakHashMap<>();
    private final Map<View, TailShape> tailShapes = new WeakHashMap<>();
    private final Map<View, Boolean> restoringTailShapes = new WeakHashMap<>();
    private final Map<Class<?>, SpotlightAccess> spotlightAccess = new WeakHashMap<>();
    private final Map<Resources, NativeRenderAnimations> nativeRenderAnimations = new WeakHashMap<>();
    private final Map<View, String> outlineReasons = new WeakHashMap<>();
    private final Map<View, String> contentReasons = new WeakHashMap<>();
    private final Map<View, String> tailCutPathTuples = new WeakHashMap<>();
    private final Map<View, String> tailCutSpecTuples = new WeakHashMap<>();
    private final Map<View, Long> tailCutPathTimes = new WeakHashMap<>();
    private final Map<View, Long> tailCutSpecTimes = new WeakHashMap<>();
    private final List<String> nativeContentReasons = new ArrayList<>();
    private final Map<Resources, NativeGap> nativeGaps = new WeakHashMap<>();
    private final Map<Class<?>, WidthAccess> widthAccess = new WeakHashMap<>();
    private final Map<Object, SavedState> tailWidthTargets = new WeakHashMap<>();
    private final Map<Object, SavedState> tailWidthRows = new WeakHashMap<>();
    private boolean outlineDiagnostics;
    private int outlineDiagnosticEvents;
    private int contentDiagnosticEvents;
    private int tailCutEvents;
    private boolean tailCutSceneActive;
    private int warnings;

    public interface NativeOutlineAction { Object draw() throws Throwable; }

    private static final class Settings {
        final boolean enabled;
        final int visibleCount;
        final float width1, width2, width3;
        Settings(Bundle source) {
            // Retain the editor and old parameters, but never run the unresolved native stack path.
            enabled = false;
            Object countValue = source == null ? null : source.get(PREFIX + "visible_count");
            visibleCount = NotificationBigClockSettings.visibleCount(countValue);
            width1 = Math.max(0f, number(source == null ? null : source.get(PREFIX + "tail_width_1"), 96f));
            width2 = Math.max(0f, number(source == null ? null : source.get(PREFIX + "tail_width_2"), 92f));
            width3 = Math.max(0f, number(source == null ? null : source.get(PREFIX + "tail_width_3"), 88f));
        }
        float width(int depth) { return depth == 1 ? width1 : depth == 2 ? width2 : width3; }
        boolean sameAs(Settings other) {
            return enabled == other.enabled && visibleCount == other.visibleCount
                    && same(width1, other.width1) && same(width2, other.width2) && same(width3, other.width3);
        }
    }

    /** The exact native dimension used by NotificationChildrenContainer.initDimens. */
    private static final class NativeGap {
        final int resourceId;
        Configuration configuration;
        float pixels = Float.NaN;
        NativeGap(Resources resources) {
            resourceId = resources.getIdentifier("notification_children_collapsed_bottom_padding",
                    "dimen", "com.android.systemui");
        }
        float pixels(Resources resources) {
            Configuration current = resources.getConfiguration();
            if (configuration == null || !configuration.equals(current)) {
                configuration = new Configuration(current);
                pixels = Float.NaN;
                if (resourceId != 0) try {
                    pixels = resources.getDimensionPixelOffset(resourceId);
                } catch (RuntimeException unavailable) { /* Native fallback below. */ }
            }
            return pixels;
        }
    }

    /** Resource names verified in this SystemUI's ViewState/ExpandableViewState native animations. */
    private static final class NativeRenderAnimations {
        final int top, y, dynamicY, height;
        NativeRenderAnimations(Resources resources) {
            top = resources.getIdentifier("top_inset_animator_tag", "id", "com.android.systemui");
            y = resources.getIdentifier("translation_y_animator_tag", "id", "com.android.systemui");
            dynamicY = resources.getIdentifier("translation_y_dynamicanimation_tag", "id", "com.android.systemui");
            height = resources.getIdentifier("height_animator_tag", "id", "com.android.systemui");
        }
        boolean present(View row) {
            return tagged(row, top) || tagged(row, y) || tagged(row, dynamicY) || tagged(row, height);
        }
        private static boolean tagged(View row, int id) { return id != 0 && row.getTag(id) != null; }
    }

    private static final class StackState {
        final List<SavedState> saved = new ArrayList<>();
        final List<NativeInput> inputs = new ArrayList<>();
        int layoutDepth, applicationDepth;
        boolean failed, completed, lastProjectable, applicationProjected;
        float lastProgress, lastBottomBoundary;
        Settings lastSettings;
        Configuration lastConfiguration;
    }

    /** Only fields which this helper owns are recorded; original notification state remains intact. */
    private static final class SavedState {
        final WeakReference<View> row, stack;
        final WeakReference<Object> target;
        final StateAccess access;
        final Values nativeValues;
        final String nativeYSource, nativeAlphaReason;
        Values projected;
        WidthSnapshot width;
        int projectedClipWidth;
        float contentReveal = 1f;
        long outlineRetiredAt;
        boolean outlineRefreshPending;
        SavedState(RowState row, View stack) throws ReflectiveOperationException {
            this.row = new WeakReference<>(row.row);
            this.stack = new WeakReference<>(stack);
            target = new WeakReference<>(row.target);
            access = row.access;
            nativeValues = row.nativeValues;
            nativeYSource = row.nativeYSource;
            nativeAlphaReason = row.nativeAlphaReason;
        }
        void restore() throws ReflectiveOperationException {
            Object state = target.get();
            if (state == null || projected == null) return;
            Values current = access.read(state);
            // Native writers after our projection take precedence when an entry is released.
            if (same(current.y, projected.y) && access.y.owned(state))
                access.y.write(state, nativeValues.y, nativeYSource);
            if (same(current.alpha, projected.alpha) && access.alpha.owned(state))
                access.alpha.write(state, nativeValues.alpha, nativeAlphaReason);
            if (current.hidden == projected.hidden) access.hidden.setBoolean(state, nativeValues.hidden);
            if (current.top == projected.top) access.clipTop.setInt(state, nativeValues.top);
            if (current.bottom == projected.bottom) access.clipBottom.setInt(state, nativeValues.bottom);
            if (access.inShelf != null && current.inShelf == projected.inShelf)
                access.inShelf.setBoolean(state, nativeValues.inShelf);
        }
        RowState nativeBaseline(View view, Object state, Values current, int childIndex)
                throws ReflectiveOperationException {
            boolean ownY = projected != null && same(current.y, projected.y) && access.y.owned(state);
            boolean ownAlpha = projected != null && same(current.alpha, projected.alpha) && access.alpha.owned(state);
            Values baseline = projected == null ? current : new Values(
                    ownY ? nativeValues.y : current.y, ownAlpha ? nativeValues.alpha : current.alpha,
                    current.height, current.top == projected.top ? nativeValues.top : current.top,
                    current.bottom == projected.bottom ? nativeValues.bottom : current.bottom,
                    current.hidden == projected.hidden ? nativeValues.hidden : current.hidden, current.gone,
                    current.inShelf == projected.inShelf ? nativeValues.inShelf : current.inShelf, current.order);
            return new RowState(view, state, access, baseline, childIndex,
                    ownY ? nativeYSource : access.y.reason(state),
                    ownAlpha ? nativeAlphaReason : access.alpha.reason(state));
        }
    }

    private static final class Values {
        final float y, alpha;
        final int height, top, bottom, order;
        final boolean hidden, gone, inShelf;
        Values(float y, float alpha, int height, int top, int bottom,
                boolean hidden, boolean gone, boolean inShelf, int order) {
            this.y = y; this.alpha = alpha; this.height = height;
            this.top = top; this.bottom = bottom; this.hidden = hidden;
            this.gone = gone; this.inShelf = inShelf; this.order = order;
        }
    }

    /** ColorOS uses tagged Y/alpha setters; the one-argument AOSP variants are also accepted. */
    private static final class FloatProperty {
        final Method get, set;
        final Field field, reason;
        final boolean taggedSetter;
        FloatProperty(Class<?> type, String suffix, String fieldName) throws ReflectiveOperationException {
            Method getter = method(type, "get" + suffix);
            Method setter = method(type, "set" + suffix, Float.TYPE, String.class);
            if (setter == null) setter = method(type, "set" + suffix, Float.TYPE);
            get = getter; set = setter;
            taggedSetter = setter != null && setter.getParameterTypes().length == 2;
            field = getter == null || setter == null ? field(type, fieldName) : null;
            reason = "YTranslation".equals(suffix) ? field(type, "mYTranslationSource")
                    : "Alpha".equals(suffix) ? field(type, "mAlphaReason") : null;
            if ((get == null || set == null) && field == null)
                throw new NoSuchFieldException("Unsupported native notification " + suffix);
        }
        float read(Object state) throws ReflectiveOperationException {
            return get != null ? ((Number) get.invoke(state)).floatValue() : field.getFloat(state);
        }
        void write(Object state, float value) throws ReflectiveOperationException {
            write(state, value, SOURCE);
        }
        void write(Object state, float value, String source) throws ReflectiveOperationException {
            if (set == null) field.setFloat(state, value);
            else if (taggedSetter) set.invoke(state, value, source);
            else set.invoke(state, value);
        }
        String reason(Object state) throws ReflectiveOperationException {
            return reason == null ? null : (String) reason.get(state);
        }
        boolean owned(Object state) throws ReflectiveOperationException {
            return reason == null || !taggedSetter || SOURCE.equals(reason(state));
        }
    }

    private static final class StateAccess {
        final FloatProperty y, alpha;
        final Field height, clipTop, clipBottom, hidden, gone, inShelf, order, extension;
        StateAccess(Class<?> type) throws ReflectiveOperationException {
            y = new FloatProperty(type, "YTranslation", "yTranslation");
            alpha = new FloatProperty(type, "Alpha", "alpha");
            height = requiredField(type, "height");
            clipTop = requiredField(type, "clipTopAmount");
            clipBottom = requiredField(type, "clipBottomAmount");
            hidden = requiredField(type, "hidden");
            gone = field(type, "gone");
            inShelf = field(type, "inShelf");
            order = field(type, "notGoneIndex");
            extension = field(type, "mExpandableViewStateEx");
        }
        Values read(Object state) throws ReflectiveOperationException {
            return new Values(y.read(state), alpha.read(state),
                    height.getInt(state),
                    clipTop.getInt(state), clipBottom.getInt(state), hidden.getBoolean(state),
                    gone != null && gone.getBoolean(state), inShelf != null && inShelf.getBoolean(state),
                    order == null ? -1 : order.getInt(state));
        }
        void write(Object state, Values value) throws ReflectiveOperationException {
            y.write(state, value.y); alpha.write(state, value.alpha);
            hidden.setBoolean(state, value.hidden);
            clipTop.setInt(state, value.top); clipBottom.setInt(state, value.bottom);
            if (inShelf != null) inShelf.setBoolean(state, value.inShelf);
        }
        boolean matches(Object state, Values expected) throws ReflectiveOperationException {
            return same(y.read(state), expected.y) && same(alpha.read(state), expected.alpha)
                    && height.getInt(state) == expected.height && clipTop.getInt(state) == expected.top
                    && clipBottom.getInt(state) == expected.bottom && hidden.getBoolean(state) == expected.hidden
                    && (gone == null || gone.getBoolean(state) == expected.gone)
                    && (inShelf == null || inShelf.getBoolean(state) == expected.inShelf)
                    && (order == null || order.getInt(state) == expected.order);
        }
    }

    /** The native width source map stays untouched; only its effective result is read here. */
    private static final class WidthAccess {
        final Class<?> stateType;
        final Method stateBase, rowView, animation, nativeWidth, nativeSource;
        final Method getWidth, setWidth, getTargetWidth, getTargetHeight, getShowHeight, useShowHeight, getSource, setSource;
        final Method abortWidth;
        final int widthAnimatorTag;
        WidthAccess(Class<?> stateType, Class<?> rowType) throws ReflectiveOperationException {
            this.stateType = stateType;
            stateBase = requiredMethod(stateType, "getBase");
            rowView = requiredMethod(rowType, "getView");
            animation = requiredMethod(stateType, "getRowAnimationStateEx");
            Class<?> animationType = animation.getReturnType();
            nativeWidth = requiredMethod(animationType, "getClipWidth", Object.class);
            nativeSource = requiredMethod(animationType, "getEffectiveClipWidthSource");
            getWidth = requiredMethod(rowType, "getClipWidth");
            setWidth = requiredMethod(rowType, "setClipWidth", Integer.TYPE);
            getTargetWidth = requiredMethod(rowType, "getTargetClipWidth");
            getTargetHeight = requiredMethod(rowType, "getTargetClipHeight");
            getShowHeight = requiredMethod(rowType, "getTargetShowHeight");
            useShowHeight = requiredMethod(rowType, "canUseTargetShowHeight");
            getSource = requiredMethod(rowType, "getCurrentClipWidthSource");
            setSource = requiredMethod(rowType, "setCurrentClipWidthSource", Object.class);
            Field clipProperty = requiredField(stateType, "CLIP_WIDTH_PROPERTY");
            Object property = clipProperty.get(null);
            Method animatorTag = requiredMethod(clipProperty.getType(), "getAnimatorTag");
            widthAnimatorTag = ((Number) animatorTag.invoke(property)).intValue();
            Class<?> nativeState = Class.forName("com.android.systemui.statusbar.notification.stack.ViewState",
                    false, stateType.getClassLoader());
            // The native abort understands both Animator and the OEM property-data spring,
            // and uses this same CLIP_WIDTH_PROPERTY tag (also passed to toPhysicsProperty).
            abortWidth = requiredMethod(nativeState, "abortAnimation", View.class, Integer.TYPE);
        }
        int nativeWidth(Object extension) throws ReflectiveOperationException {
            Object sources = animation.invoke(extension);
            // Calling stateExtension.getClipWidth here would re-enter our own target hook.
            return Math.max(0, ((Number) nativeWidth.invoke(sources, new Object[] { null })).intValue());
        }
        Object nativeSource(Object extension) throws ReflectiveOperationException {
            return nativeSource.invoke(animation.invoke(extension));
        }
        int integer(Method method, Object row) throws ReflectiveOperationException {
            return ((Number) method.invoke(row)).intValue();
        }
    }

    /** Native width-to-height coupling verified in OplusExpandableViewExImpl.getActualClipHeight. */
    private static final class WidthSnapshot {
        final WeakReference<Object> stateExtension, rowExtension;
        final WidthAccess access;
        final int nativeClipWidth, targetWidth, targetHeight, showHeight;
        final boolean useShowHeight, touchSource;
        WidthSnapshot(Object state, Object row, WidthAccess access) throws ReflectiveOperationException {
            stateExtension = new WeakReference<>(state);
            rowExtension = new WeakReference<>(row);
            this.access = access;
            nativeClipWidth = access.nativeWidth(state);
            targetWidth = access.integer(access.getTargetWidth, row);
            targetHeight = access.integer(access.getTargetHeight, row);
            showHeight = access.integer(access.getShowHeight, row);
            useShowHeight = showHeight > 0 && Boolean.TRUE.equals(access.useShowHeight.invoke(row));
            touchSource = "clip_width_touch_scale".equals(access.nativeSource(state));
        }
        int cut(int height, int clipWidth) {
            if (targetHeight == 0 || clipWidth == 0 || targetWidth == 0) return 0;
            int pixels = useShowHeight ? Math.max(0, height - (int) lerp(height, showHeight, (float) clipWidth / targetWidth))
                    : Math.min(targetHeight, (int) ((float) targetHeight * clipWidth / targetWidth));
            return Math.max(0, Math.min(height, pixels));
        }
        boolean matches(Object state, Object row) throws ReflectiveOperationException {
            return stateExtension.get() == state && rowExtension.get() == row
                    && nativeClipWidth == access.nativeWidth(state)
                    && targetWidth == access.integer(access.getTargetWidth, row)
                    && targetHeight == access.integer(access.getTargetHeight, row)
                    && showHeight == access.integer(access.getShowHeight, row)
                    && useShowHeight == (showHeight > 0 && Boolean.TRUE.equals(access.useShowHeight.invoke(row)))
                    && touchSource == "clip_width_touch_scale".equals(access.nativeSource(state));
        }
    }

    private static final class RowState {
        final View row;
        final Object target;
        final StateAccess access;
        final Values nativeValues;
        final int childIndex;
        final String nativeYSource, nativeAlphaReason;
        RowState(View row, Object target, StateAccess access, Values nativeValues, int childIndex,
                String nativeYSource, String nativeAlphaReason) {
            this.row = row; this.target = target; this.access = access;
            this.nativeValues = nativeValues; this.childIndex = childIndex;
            this.nativeYSource = nativeYSource; this.nativeAlphaReason = nativeAlphaReason;
        }
    }

    /** Weak snapshots validate cached targets without retaining a notification or its parent stack. */
    private static final class NativeInput {
        final WeakReference<View> view;
        final WeakReference<Object> target;
        final RowAccess row;
        final StateAccess access;
        final Values expected;
        final boolean gone, excluded, interacting;
        final String ySource, alphaReason;
        final int viewWidth;
        final WidthSnapshot width;
        NativeInput(View view, Object target, RowAccess row, StateAccess access, Values expected,
                boolean interacting, WidthSnapshot width) throws ReflectiveOperationException {
            this.view = new WeakReference<>(view);
            this.target = new WeakReference<>(target);
            this.row = row; this.access = access; this.expected = expected;
            gone = view.getVisibility() == View.GONE;
            excluded = row.notification && row.excluded(view);
            this.interacting = interacting;
            ySource = access == null ? null : access.y.reason(target);
            alphaReason = access == null ? null : access.alpha.reason(target);
            viewWidth = view.getWidth();
            this.width = width;
        }
        boolean matches(View current, StackAccess stackAccess, View stack) throws ReflectiveOperationException {
            if (view.get() != current || viewWidth != current.getWidth() || gone != (current.getVisibility() == View.GONE)
                    || excluded != (row.notification && row.excluded(current))
                    || interacting != (row.notification && (row.interactionOwnsRow(current)
                            || stackAccess.interactionOwnsRow(stack, current)))) return false;
            Object state = row.state == null ? null : row.state.invoke(current);
            if (state != target.get()) return false;
            if (access == null) return state == null;
            if (width != null && !width.matches(access.extension.get(state), row.expandableViewEx.get(current))) return false;
            return access.matches(state, expected)
                    && sameText(ySource, access.y.reason(state))
                    && sameText(alphaReason, access.alpha.reason(state));
        }
    }

    private static final class DecorationState {
        final Values values;
        final boolean boundaryOnly;
        DecorationState(Values values, boolean boundaryOnly) {
            this.values = values; this.boundaryOnly = boundaryOnly;
        }
    }

    /** Cached once per native row/decor class, rather than searched during each scroll update. */
    private static final class RowAccess {
        final boolean notification;
        final Method state, actualHeight, childInGroup, removed, dismissed, guts, userLocked, expanding, groupChanging;
        final Method topRadius, bottomRadius, maxRadius, minimumClippingHeight, actualClipHeight;
        final Method roundedPath, nativePath, syncBackground;
        final Field removing, clipTop, clipBottom, topOverlap, bottomOverlap, customOutline, outlineRect, expandableViewEx, background;
        final Field alwaysRoundBothCorners;
        final Field outlineViewEx, emptyPath;
        final Class<?> spotlightType;
        RowAccess(Class<?> type) {
            notification = namedClass(type, ROW);
            state = method(type, "getViewState");
            actualHeight = notification ? method(type, "getActualHeight") : null;
            childInGroup = notification ? method(type, "isChildInGroup") : null;
            removed = notification ? method(type, "isRemoved") : null;
            dismissed = notification ? method(type, "isDismissed") : null;
            guts = notification ? method(type, "areGutsExposed") : null;
            userLocked = notification ? method(type, "isUserLocked") : null;
            expanding = notification ? method(type, "isExpandAnimationRunning") : null;
            groupChanging = notification ? method(type, "isGroupExpansionChanging") : null;
            removing = notification ? field(type, "mInRemovalAnimation") : null;
            clipTop = notification ? field(type, "mClipTopAmount") : null;
            clipBottom = notification ? field(type, "mClipBottomAmount") : null;
            topOverlap = notification ? field(type, "mTopOverlap") : null;
            bottomOverlap = notification ? field(type, "mBottomOverlap") : null;
            customOutline = notification ? field(type, "mHasCustomOutline") : null;
            outlineRect = notification ? field(type, "mOutlineRect") : null;
            expandableViewEx = notification ? field(type, "mExpandableViewEx") : null;
            background = notification ? field(type, "mBackgroundNormal") : null;
            alwaysRoundBothCorners = notification ? field(type, "mAlwaysRoundBothCorners") : null;
            topRadius = notification ? publicMethod(type, "getTopCornerRadius") : null;
            bottomRadius = notification ? publicMethod(type, "getBottomCornerRadius") : null;
            maxRadius = notification ? publicMethod(type, "getMaxRadius") : null;
            minimumClippingHeight = notification ? method(type, "getMinimumHeightForClipping") : null;
            actualClipHeight = expandableViewEx == null ? null
                    : method(expandableViewEx.getType(), "getActualClipHeight");
            outlineViewEx = notification ? field(type, "mOutlineViewEx") : null;
            emptyPath = notification ? field(type, "EMPTY_PATH") : null;
            roundedPath = notification ? method(type, "getRoundedRectPath", Integer.TYPE, Integer.TYPE,
                    Integer.TYPE, Integer.TYPE, Float.TYPE, Float.TYPE, Path.class) : null;
            nativePath = notification ? method(type, "getClipPath", Boolean.TYPE) : null;
            syncBackground = outlineViewEx == null ? null : method(outlineViewEx.getType(),
                    "syncClipCanvasToBgView", Float.TYPE, Integer.TYPE, Integer.TYPE,
                    Float.TYPE, Integer.TYPE, Integer.TYPE);
            Class<?> spotlight = null;
            if (notification) try { spotlight = type.getClassLoader().loadClass(SPOTLIGHT_SPEC); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { /* Exact native fallback. */ }
            spotlightType = spotlight;
        }
        boolean excluded(View row) {
            return bool(childInGroup, row) || bool(removed, row) || bool(dismissed, row) || bool(removing, row);
        }
        boolean interactionOwnsRow(View row) {
            return bool(guts, row) || bool(userLocked, row) || bool(expanding, row) || bool(groupChanging, row);
        }
    }

    private static final class StackAccess {
        final Method clearAll, request;
        final Field launching, expanding, expandingRow, paddingBetweenElements;
        StackAccess(Class<?> type) {
            clearAll = method(type, "getClearAllInProgress");
            launching = field(type, "mLaunchingNotification");
            expanding = field(type, "mExpandingNotification");
            expandingRow = field(type, "mExpandingNotificationRow");
            paddingBetweenElements = field(type, "mPaddingBetweenElements");
            Method update = method(type, "requestChildrenUpdate$1");
            request = update == null ? method(type, "requestChildrenUpdate") : update;
        }
        boolean interactionOwnsLayout(View stack) {
            return bool(clearAll, stack) || bool(launching, stack);
        }
        boolean interactionOwnsRow(View stack, View row) {
            if (!bool(expanding, stack) || expandingRow == null) return false;
            try { return expandingRow.get(stack) == row; }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
    }

    public static void setNativeHooksAvailable(boolean available) { nativeHooksAvailable = available; }
    public static void setNativeWidthHooksAvailable(boolean available) { nativeWidthHooksAvailable = available; }

    /** After the OEM target getter; this is an exact target identity, never a global width override. */
    public int nativeTailTargetClipWidth(Object stateExtension, int nativeValue) {
        SavedState entry = tailWidthTargets.get(stateExtension);
        return ownsTailWidth(entry, stateExtension, null) ? entry.projectedClipWidth : nativeValue;
    }

    /** Before the OEM row setter, including width spring frames; native roundness is still drawn normally. */
    public int nativeTailRowClipWidth(Object rowExtension, int nativeValue) {
        SavedState entry = tailWidthRows.get(rowExtension);
        return ownsTailWidth(entry, null, rowExtension) ? entry.projectedClipWidth : nativeValue;
    }

    /** After a real OEM width change; refresh only this active owned row's native outline cache. */
    public void onNativeTailWidthChanged(Object rowExtension) {
        SavedState entry = tailWidthRows.get(rowExtension);
        if (!ownsTailWidth(entry, null, rowExtension)) return;
        View row = entry.row.get();
        if (row != null) {
            entry.outlineRefreshPending = true;
            row.invalidateOutline();
        }
    }

    private boolean ownsTailWidth(SavedState entry, Object stateExtension, Object rowExtension) {
        if (!nativeHooksAvailable || !nativeWidthHooksAvailable || !settings.enabled || entry == null
                || entry.width == null || entry.projected == null) return false;
        try {
            View row = entry.row.get(), stack = entry.stack.get();
            Object target = entry.target.get();
            Object stateEx = entry.width.stateExtension.get(), rowEx = entry.width.rowExtension.get();
            if (row == null || stack == null || target == null || stateEx == null || rowEx == null
                    || row.getParent() != stack || stateExtension != null && stateExtension != stateEx
                    || rowExtension != null && rowExtension != rowEx || tailWidthTargets.get(stateEx) != entry
                    || tailWidthRows.get(rowEx) != entry) return false;
            RowAccess members = rowAccess(row);
            return members.notification && members.state != null && members.state.invoke(row) == target
                    && members.expandableViewEx != null && members.expandableViewEx.get(row) == rowEx
                    && entry.access.extension != null && entry.access.extension.get(target) == stateEx
                    && entry.width.access.stateBase.invoke(stateEx) == target
                    && entry.width.access.rowView.invoke(rowEx) == row
                    && !members.excluded(row) && !members.interactionOwnsRow(row)
                    && !bool(members.customOutline, row)
                    && !stackAccess(stack).interactionOwnsLayout(stack)
                    && !stackAccess(stack).interactionOwnsRow(stack, row)
                    && !"clip_width_touch_scale".equals(entry.width.access.nativeSource(stateEx))
                    && !"clip_width_touch_scale".equals(entry.width.access.getSource.invoke(rowEx))
                    && entry.access.y.owned(target) && same(entry.access.y.read(target), entry.projected.y)
                    && entry.access.clipTop.getInt(target) == entry.projected.top
                    && entry.access.clipBottom.getInt(target) == entry.projected.bottom;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            warn(unavailable, "Native notification tail width unavailable; original width retained");
            return false;
        }
    }

    private WidthSnapshot widthSnapshot(View row, Object target, StateAccess state) {
        if (!nativeWidthHooksAvailable || state == null || state.extension == null || target == null) return null;
        try {
            RowAccess members = rowAccess(row);
            Object stateEx = state.extension.get(target);
            Object rowEx = members.expandableViewEx == null ? null : members.expandableViewEx.get(row);
            if (stateEx == null || rowEx == null) return null;
            WidthAccess width = widthAccess.get(rowEx.getClass());
            if (width == null || width.stateType != stateEx.getClass()) {
                width = new WidthAccess(stateEx.getClass(), rowEx.getClass());
                widthAccess.put(rowEx.getClass(), width);
            }
            if (width.stateBase.invoke(stateEx) != target || width.rowView.invoke(rowEx) != row) return null;
            return new WidthSnapshot(stateEx, rowEx, width);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            warn(unavailable, "Native notification tail width unsupported; original width retained");
            return null;
        }
    }
    public void configure(Bundle source) {
        Settings next = new Settings(source);
        if (!settings.sameAs(next)) settings = next;
        if (!next.enabled) clearNativeTailShapes(null);
        boolean diagnostics = source != null && Boolean.TRUE.equals(source.get(ModuleDiagnostics.KEY_ENABLED));
        if (diagnostics != outlineDiagnostics) {
            resetTailCutSession();
            contentDiagnosticEvents = 0;
            outlineReasons.clear(); contentReasons.clear(); nativeContentReasons.clear();
        }
        outlineDiagnostics = diagnostics;
    }

    /** Before updateChildren: discard old target edits before SystemUI calculates the next state. */
    public void beginNativeLayout(View stack) {
        StackState state = stacks.get(stack);
        if (state == null) { state = new StackState(); stacks.put(stack, state); }
        // updateViewStates may call updateChildren; only the outer entry owns target release.
        if (state.layoutDepth++ == 0) releaseTargets(state);
    }

    /** Completes a native layout cycle; neither changes real views nor requests another frame. */
    public void endNativeLayout(View stack) {
        StackState state = stacks.get(stack);
        if (state == null || state.layoutDepth <= 0) return;
        --state.layoutDepth;
    }

    /** Whole-stack and per-row native apply/animate paths share one projection transaction. */
    public void beginNativeApplication(View stack) {
        StackState state = stacks.get(stack);
        if (state == null) { state = new StackState(); stacks.put(stack, state); }
        if (state.applicationDepth++ == 0) state.applicationProjected = false;
    }

    public void endNativeApplication(View stack) {
        StackState state = stacks.get(stack);
        if (state == null || state.applicationDepth <= 0 || --state.applicationDepth > 0) return;
        // Animation registration and native target writes have now finished. Refresh again
        // here if a provider ran during the earlier ownership transition and cached that path.
        for (SavedState entry : state.saved) {
            refreshTailWidth(entry);
            refreshTailOutline(stack, entry);
        }
        for (SavedState entry : new ArrayList<>(recentTailOutlines.values())) refreshTailOutline(stack, entry);
    }

    /** Projects targets before native apply/animate; rows retain their native rendering path. */
    public void apply(View stack, float progress, boolean eligible) {
        apply(stack, progress, eligible, Float.NaN);
    }

    /** bottomBoundary is stack-local; optional footer reserve also constrains the folded tail. */
    public void apply(View stack, float progress, boolean eligible, float bottomBoundary) {
        if (!(stack instanceof ViewGroup)) return;
        Settings options = settings;
        float p = finite(progress) ? Math.max(0f, Math.min(1f, progress)) : 1f;
        StackState saved = stacks.get(stack);
        if (saved == null) { saved = new StackState(); stacks.put(stack, saved); }
        ViewGroup host = (ViewGroup) stack;
        StackAccess stackMembers = stackAccess(stack);
        boolean sceneEligible = nativeHooksAvailable && eligible && options.enabled;
        if (sceneEligible && !tailCutSceneActive) {
            tailCutSceneActive = true;
            if (outlineDiagnostics) resetTailCutSession();
        } else if (!sceneEligible) tailCutSceneActive = false;
        boolean projectable = nativeHooksAvailable && eligible && options.enabled && p < 1f
                && !stackMembers.interactionOwnsLayout(stack);
        // ExpandableViewState invokes ViewState internally; a native application must not
        // repeatedly restore/project the whole list while its individual rows are being applied.
        if (saved.applicationDepth > 0 && saved.applicationProjected) return;
        if (saved.completed && saved.lastSettings == options
                && same(saved.lastProgress, p) && same(saved.lastBottomBoundary, bottomBoundary)
                && saved.lastProjectable == projectable
                && (!projectable || saved.lastConfiguration != null
                        && saved.lastConfiguration.equals(stack.getResources().getConfiguration()))
                && (!projectable || nativeInputsMatch(host, saved, stackMembers))) {
            if (saved.applicationDepth > 0) saved.applicationProjected = true;
            return;
        }
        if (!projectable) {
            // p==1 and row interactions can release projection while this scene remains open.
            restore(stack, !sceneEligible);
            completed(saved, options, p, false, bottomBoundary);
            return;
        }
        if (saved.failed) { completed(saved, options, p, true, bottomBoundary); return; }
        List<SavedState> next = new ArrayList<>();
        boolean committed = false;
        try {
            Map<Object, SavedState> previous = new IdentityHashMap<>();
            for (SavedState entry : saved.saved) {
                Object target = entry.target.get();
                if (target != null) previous.put(target, entry);
            }
            // Recover logical native baselines without first writing a whole native list into
            // live targets. A partial native update takes precedence for the fields it changed.
            List<RowState> rows = rows(host, previous);
            List<DecorationState> decorations = decorations(host);
            if (rows.size() > options.visibleCount) {
                RowState anchor = rows.get(options.visibleCount - 1);
                if (!anchor.nativeValues.hidden && anchor.nativeValues.alpha > 0f) {
                    float density = stack.getResources().getDisplayMetrics().density;
                    if (!finite(density) || density <= 0f) density = 1f;
                    float gap = nativeGap(stack, rows);
                    WidthSnapshot anchorWidth = widthSnapshot(anchor.row, anchor.target, anchor.access);
                    int anchorHeight = anchor.nativeValues.height - (anchorWidth == null ? 0
                            : anchorWidth.cut(anchor.nativeValues.height, anchorWidth.nativeClipWidth));
                    float anchorNativeWidth = Math.max(0f, anchor.row.getWidth()
                            - (anchorWidth == null ? 0 : anchorWidth.nativeClipWidth));
                    float anchorBottom = anchor.nativeValues.y + anchorHeight;
                    float previousVisibleBottom = anchorBottom;
                    float previousNativeBottom = anchorBottom;
                    float interactionEnd = Float.NaN;
                    for (int index = options.visibleCount; index < rows.size(); index++) {
                        RowState row = rows.get(index);
                        Values nativeValue = row.nativeValues;
                        RowAccess members = rowAccess(row.row);
                        WidthSnapshot width = widthSnapshot(row.row, row.target, row.access);
                        int nativeHeight = nativeValue.height - (width == null ? 0
                                : width.cut(nativeValue.height, width.nativeClipWidth));
                        if (members.interactionOwnsRow(row.row) || stackMembers.interactionOwnsRow(stack, row.row)) {
                            // A normal expansion belongs to this row, not to every other folded row.
                            // Following tails remain on the native side of this occupied region.
                            float end = nativeValue.y + nativeHeight;
                            interactionEnd = finite(interactionEnd) ? Math.max(interactionEnd, end) : end;
                            previousVisibleBottom = Math.max(previousVisibleBottom, end);
                            previousNativeBottom = end;
                            continue;
                        }
                        int depth = index - options.visibleCount + 1;
                        int visibleDepth = Math.min(depth, 3);
                        float foldedBottom = anchorBottom + visibleDepth * gap;
                        float boundary = decorationBoundary(decorations, anchorBottom, nativeValue.y + nativeHeight);
                        float decorationEnd = decorationEndBefore(decorations, anchorBottom, nativeValue.y);
                        if (finite(interactionEnd)) decorationEnd = finite(decorationEnd)
                                ? Math.max(decorationEnd, interactionEnd) : interactionEnd;
                        boolean afterDecoration = finite(decorationEnd);
                        if (finite(boundary)) foldedBottom = Math.min(foldedBottom, boundary - 2f * density);
                        if (finite(bottomBoundary)) foldedBottom = Math.min(foldedBottom, bottomBoundary - 2f * density);
                        // No available peek space means an empty tail, never a full native card leaking through.
                        foldedBottom = Math.max(anchorBottom, foldedBottom);
                        boolean peek = depth <= 3;
                        boolean fixedWidth = peek && !afterDecoration && width != null && !width.touchSource
                                && !bool(members.customOutline, row.row)
                                && row.row.getWidth() > 0 && anchorNativeWidth > 0f;
                        int projectedClipWidth = width == null ? 0 : width.nativeClipWidth;
                        int foldedClipWidth = projectedClipWidth;
                        if (fixedWidth) {
                            // Physical row width is the safe native limit; confirmed preferences remain
                            // unchanged in storage, including zero and values beyond the suggested range.
                            double desired = (double) anchorNativeWidth * options.width(depth) / 100d;
                            int foldedWidth = (int) Math.max(0d, Math.min(row.row.getWidth(), Math.round(desired)));
                            foldedClipWidth = row.row.getWidth() - foldedWidth;
                            projectedClipWidth = Math.max(0, Math.min(row.row.getWidth(),
                                    Math.round(lerp(foldedClipWidth, width.nativeClipWidth, p))));
                        }
                        int foldedHeight = nativeValue.height - (width == null ? 0
                                : width.cut(nativeValue.height, foldedClipWidth));
                        int effectiveHeight = nativeValue.height - (width == null ? 0
                                : width.cut(nativeValue.height, projectedClipWidth));
                        // Show the actual bottom of this native card, including its original roundness;
                        // never obtain a tail strip by clipping the card's middle at a fake bottom.
                        float foldedY = foldedBottom - foldedHeight;
                        // At rest exactly three bottom edges can show. Further rows join the native list gradually.
                        float delay = peek ? 0f : Math.min(.65f, (depth - 3) * .08f);
                        float reveal = Math.max(0f, Math.min(1f, (p - delay) / Math.max(.01f, 1f - delay)));
                        reveal = reveal * reveal * (3f - 2f * reveal);
                        float peekAlpha = peek && !afterDecoration ? anchor.nativeValues.alpha * (1f - visibleDepth * .045f) : 0f;
                        // A notification beyond an intervening media/section stays on its native side.
                        // Reveal it there rather than translating a translucent card across native controls.
                        float projectedY = afterDecoration ? nativeValue.y : lerp(foldedY, nativeValue.y, p);
                        float nativeGap = Math.max(0f, nativeValue.y - previousNativeBottom);
                        // Each translucent edge has a disjoint visible interval. OEM native style writes Z=0
                        // before apply/animate, so negative Z alone cannot protect the foreground card.
                        float visibleTop = previousVisibleBottom + nativeGap * p;
                        if (afterDecoration) visibleTop = Math.max(visibleTop, decorationEnd);
                        float foldedTop = peek && !afterDecoration
                                ? Math.max(0f, (float) Math.ceil(anchorBottom - foldedY)) : foldedHeight;
                        int bottomClip = Math.max(0, Math.min(effectiveHeight, Math.round(nativeValue.bottom * p)));
                        if (finite(bottomBoundary)) bottomClip = Math.max(bottomClip,
                                Math.max(0, Math.min(effectiveHeight,
                                        (int) Math.ceil(projectedY + effectiveHeight - bottomBoundary + 2f * density))));
                        int topClip = Math.max(0, Math.min(effectiveHeight - bottomClip,
                                Math.max(Math.round(lerp(foldedTop, nativeValue.top, peek ? p : reveal)),
                                        (int) Math.ceil(visibleTop - projectedY))));
                        float projectedAlpha = lerp(peekAlpha, nativeValue.alpha, peek ? p : reveal);
                        boolean noVisibleArea = topClip + bottomClip >= effectiveHeight;
                        Values projected = new Values(projectedY,
                                noVisibleArea ? 0f : projectedAlpha,
                                nativeValue.height,
                                topClip, bottomClip,
                                noVisibleArea || (!peek && reveal <= 0f), nativeValue.gone, false, nativeValue.order);
                        if (!finite(projected.y) || !finite(projected.alpha)) continue;
                        SavedState entry = new SavedState(row, stack);
                        entry.projected = projected;
                        if (fixedWidth) {
                            entry.width = width;
                            entry.projectedClipWidth = projectedClipWidth;
                        }
                        // A folded native bottom edge contains glass only. Reveal labels according
                        // to the same visible geometry as that edge, without a second animator.
                        float visibleHeight = effectiveHeight - topClip - bottomClip;
                        float contentFraction = p <= 0f ? 0f : Math.max(0f, Math.min(1f,
                                (visibleHeight - gap) / Math.max(1f, effectiveHeight - gap)));
                        entry.contentReveal = contentFraction * contentFraction * (3f - 2f * contentFraction);
                        next.add(entry);
                        row.access.write(row.target, projected);
                        if (!projected.hidden && projected.alpha > 0f)
                            previousVisibleBottom = Math.max(previousVisibleBottom,
                                    projected.y + effectiveHeight - projected.bottom);
                        previousNativeBottom = nativeValue.y + nativeHeight;
                    }
                }
            }
            commitTargets(saved, next);
            committed = true;
            captureNativeInputs(host, saved, stackMembers);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!committed) restoreEntries(next);
            releaseTargets(saved);
            saved.failed = true;
            warn(error);
            requestNativeUpdate(stack, saved);
        } finally { completed(saved, options, p, true, bottomBoundary); }
    }

    /** Restores only owned target edits; SystemUI reapplies them using its regular layout path. */
    public void restore(View stack) {
        restore(stack, true);
    }

    private void restore(View stack, boolean sceneExit) {
        if (sceneExit) tailCutSceneActive = false;
        StackState state = stacks.get(stack);
        if (state == null) {
            if (sceneExit) clearNativeTailShapes(stack);
            return;
        }
        // Retain the depth guard until the native outer finally, even when a feature is disabled.
        if (state.layoutDepth == 0 && state.applicationDepth == 0) stacks.remove(stack);
        boolean changed = !state.saved.isEmpty();
        releaseTargets(state);
        if (sceneExit) clearNativeTailShapes(stack);
        state.failed = false;
        if (changed) requestNativeUpdate(stack, state);
    }

    public void restoreAll() {
        tailCutSceneActive = false;
        for (View stack : new ArrayList<>(stacks.keySet())) if (stack != null) restore(stack);
        clearNativeTailShapes(null);
    }

    public void detach(View stack) {
        StackState state = stacks.remove(stack);
        if (stacks.isEmpty()) tailCutSceneActive = false;
        if (state != null) releaseTargets(state);
        for (View row : new ArrayList<>(recentTailOutlines.keySet())) {
            SavedState entry = recentTailOutlines.get(row);
            if (entry != null && entry.stack.get() == stack) forgetRecentOutline(row, entry);
        }
    }

    /**
     * Native whole-card lower arcs become pointed when a disjoint thin tail window cuts them.
     * Keep the original native call, then resolve only a registered row's actual thin window
     * as a complete native rounded layer. Its path, glass and spotlight share the same bounds.
     * Full rows, interactions and unsupported native implementations retain their native draw.
     */
    public Object withNativeTailOutline(View row, NativeOutlineAction nativeDraw) throws Throwable {
        if (row != null && restoringTailShapes.containsKey(row)) return nativeDraw.draw();
        Field clip = null;
        Field overlap = null;
        int previousTop = 0, previousOverlap = 0;
        boolean restoreShapeAfter = false;
        TailWindow thinWindow = null;
        SavedState entry = row == null ? null : tailOutlines.get(row);
        if (entry == null && row != null) entry = recentTailOutlines.get(row);
        TailCutSample tailCut = outlineDiagnostics && entry != null && row.getVisibility() == View.VISIBLE
                ? tailCutSample(row, entry) : null;
        if (nativeHooksAvailable && settings.enabled && entry != null
                && entry.projected != null && row.getVisibility() == View.VISIBLE) {
            try {
                RowAccess members = rowAccess(row);
                Object target = entry.target.get();
                View stack = entry.stack.get();
                if (members.notification && entry.row.get() == row && stack != null && row.getParent() == stack
                        && !stackAccess(stack).interactionOwnsLayout(stack)
                        && !members.excluded(row) && !members.interactionOwnsRow(row)
                        && !stackAccess(stack).interactionOwnsRow(stack, row)
                        && !bool(members.customOutline, row) && members.clipTop != null
                        && members.state != null && target != null && members.state.invoke(row) == target) {
                    clip = members.clipTop;
                    previousTop = clip.getInt(row);
                    overlap = members.topOverlap;
                    previousOverlap = overlap == null ? 0 : overlap.getInt(row);
                    int actualHeight = members.actualHeight == null ? row.getHeight()
                            : ((Number) members.actualHeight.invoke(row)).intValue();
                    Rect actualBounds = row.getClipBounds();
                    int actualBottom = actualBounds == null ? actualHeight : Math.min(actualHeight, actualBounds.bottom);
                    int actualTop = Math.max(previousTop, previousOverlap);
                    int targetTop = entry.access.clipTop.getInt(target);
                    boolean window = actualTop > 0 && actualTop < actualBottom;
                    boolean owned = tailOutlines.get(row) == entry && targetTop == entry.projected.top
                            && entry.access.y.owned(target)
                            && same(entry.access.y.read(target), entry.projected.y);
                    // Target hidden/alpha and p==1 may precede the native clip/Y animations.
                    // Keep only this previously owned row's still-extra tail window, while the
                    // native geometry animation is present. Native settled clipping stays native.
                    boolean recent = !owned && window && entry.outlineRetiredAt > 0
                            && SystemClock.uptimeMillis() - entry.outlineRetiredAt <= 4000L
                            && actualTop > Math.max(targetTop, entry.nativeValues.top)
                            && nativeRenderAnimations(row).present(row)
                            && (previousTop > Math.max(targetTop, entry.nativeValues.top)
                                    || !same(row.getTranslationY(), entry.access.y.read(target)));
                    // An active tail may briefly lose target ownership during native writes.
                    // This does not prove that its current top clip came from us. Only repair
                    // a physical top-radius overflow whose native bottom fits after top is zero.
                    boolean cornerOverflow = !owned && window && tailOutlines.get(row) == entry
                            && actualTop > entry.nativeValues.top
                            && cornerTopOverflow(row, members, actualTop, actualHeight, actualBottom);
                    boolean renderTail = window && (owned || recent || cornerOverflow);
                    // Active registration survives a brief native target write. The original
                    // row/target/parent and interaction guards still apply; no ordinary clipped
                    // notification can enter this branch. A tall scroll window stays native.
                    if (window && (tailOutlines.get(row) == entry || recent))
                        thinWindow = nativeTailWindow(row, entry, members, actualTop, actualHeight, actualBounds);
                    if (thinWindow != null) renderTail = true;
                    if (tailCut != null) tailCut.reason = cornerOverflow ? "CORNER_OVERFLOW" : owned
                            ? window ? "OWN_WIN" : actualTop <= 0 ? "OWN_FULL" : "OWN_EMPTY"
                            : recent ? "RECENT" : window ? "GUARD" : actualTop <= 0 ? "SETTLED" : "EMPTY";
                    outlineDiagnostic(row, entry, cornerOverflow ? "TAIL_DRAW_CORNER_OVERFLOW" : owned
                            ? window ? "TAIL_DRAW_OWNED_WINDOW" : actualTop <= 0 ? "TAIL_DRAW_OWNED_FULL" : "TAIL_DRAW_OWNED_EMPTY"
                            : recent ? "TAIL_DRAW_RECENT_WINDOW" : window ? "TAIL_DRAW_NATIVE_GUARD"
                            : actualTop <= 0 ? "TAIL_DRAW_SETTLED" : "TAIL_DRAW_NATIVE_EMPTY",
                            actualTop, actualHeight, actualBottom, targetTop);
                    if (!renderTail && recentTailOutlines.get(row) == entry) {
                        StackState cycle = stacks.get(stack);
                        if (!window || SystemClock.uptimeMillis() - entry.outlineRetiredAt > 4000L
                                || cycle == null || cycle.layoutDepth == 0 && cycle.applicationDepth == 0)
                            forgetRecentOutline(row, entry);
                    }
                    // The rounded thin window is calculated independently below. Keep the
                    // real native clip fields intact, including during unsupported/failing
                    // native calls, so their original Path, glass and spotlight stay aligned.
                } else {
                    if (tailCut != null) tailCut.reason = bool(members.customOutline, row) ? "CUSTOM" : "INTERACT";
                    outlineDiagnostic(row, entry, "TAIL_DRAW_NATIVE_INTERACTION", -1, -1, -1, -1);
                    forgetRecentOutline(row, entry);
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                if (tailCut != null) tailCut.reason = "NATIVE_ERROR";
                warn(unavailable, "Native notification tail outline unavailable; original outline retained");
            }
        } else if (entry != null) {
            forgetRecentOutline(row, entry);
        }
        try {
            Object result = nativeDraw.draw();
            boolean shaped = false;
            if (thinWindow != null) try {
                TailShape previousShape = tailShapes.get(row);
                Object resolved = resolveNativeTailShape(row, thinWindow, result);
                if (tailShapes.get(row) != null && tailShapes.get(row) != previousShape) {
                    shaped = true;
                    result = resolved;
                    if (tailCut != null) {
                        tailCut.reason = recentTailOutlines.get(row) == entry ? "THIN_RECENT" : "THIN";
                        TailShape shape = tailShapes.get(row);
                        if (shape != null) tailCut.overrideDetails = " ts " + shape.top + "," + shape.bottom + "," + shape.radius;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                restoreShapeAfter = tailShapes.containsKey(row);
                if (tailCut != null) tailCut.reason = "THIN_FALLBACK";
                warn(unavailable, "Native rounded notification tail unavailable; original shape retained");
            }
            if (!shaped && !restoreShapeAfter && tailShapes.containsKey(row)) {
                if (result instanceof Path) tailShapes.remove(row); // Native path already resynchronized its glass.
                else restoreShapeAfter = true; // A spotlight call does not restore the native glass cache.
            }
            if (tailCut != null) try { tailCutDiagnostic(row, tailCut, result); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { /* Diagnostic only. */ }
            return result;
        }
        finally {
            if (restoreShapeAfter) restoreNativeTailShape(row);
        }
    }

    private static final class TailWindow {
        final WeakReference<View> stack;
        final Rect bounds;
        final int top, bottom;
        final float radius;
        TailWindow(View stack, Rect bounds, int top, int bottom, float radius) {
            this.stack = new WeakReference<>(stack);
            this.bounds = bounds;
            this.top = top; this.bottom = bottom; this.radius = radius;
        }
    }

    /** A native glass tuple; it never owns the row's real height, clips or corner properties. */
    private static final class TailShape {
        final WeakReference<View> stack;
        final int left, top, right, bottom;
        final float radius;
        TailShape(View stack, int left, int top, int right, int bottom, float radius) {
            this.stack = new WeakReference<>(stack);
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.radius = radius;
        }
        void sync(RowAccess members, View row) throws ReflectiveOperationException {
            Object extension = members.outlineViewEx.get(row);
            if (extension == null) throw new IllegalStateException("Native notification outline extension unavailable");
            members.syncBackground.invoke(extension, radius, left, top, radius, right, bottom);
        }
    }

    private TailWindow nativeTailWindow(View row, SavedState entry, RowAccess members,
            int actualTop, int actualHeight, Rect actualBounds) throws ReflectiveOperationException {
        if (members.maxRadius == null || members.clipBottom == null || members.expandableViewEx == null
                || members.actualClipHeight == null || members.outlineViewEx == null
                || members.roundedPath == null || members.nativePath == null
                || members.emptyPath == null || members.syncBackground == null || members.spotlightType == null)
            throw new NoSuchMethodException("Native rounded notification tail geometry unsupported");
        // Preflight both native surfaces before changing either, so an unsupported spotlight
        // implementation cannot leave glass and highlight using different geometries.
        spotlightFor(members.spotlightType);
        Object rowEx = members.expandableViewEx.get(row);
        if (rowEx == null) return null;
        int cut = ((Number) members.actualClipHeight.invoke(rowEx)).intValue();
        int bottomClip = members.clipBottom.getInt(row);
        if (cut < 0 || bottomClip < 0 || actualHeight <= 0) return null;
        Rect bounds = actualBounds == null ? new Rect(0, 0, row.getWidth(), actualHeight) : new Rect(actualBounds);
        int top = Math.max(actualTop, bounds.top);
        long nativeBottom = (long) actualHeight - bottomClip - cut;
        if (nativeBottom <= top) return null;
        int bottom = (int) Math.min(bounds.bottom, nativeBottom);
        float radius = ((Number) members.maxRadius.invoke(row)).floatValue();
        long height = (long) bottom - top;
        if (top <= Math.max(0, entry.nativeValues.top) || height <= 0 || bounds.left >= bounds.right
                || !finite(radius) || radius <= 0f || height > 2d * radius) return null;
        return new TailWindow(entry.stack.get(), bounds, top, bottom, radius);
    }

    private Object resolveNativeTailShape(View row, TailWindow window, Object result)
            throws ReflectiveOperationException {
        RowAccess members = rowAccess(row);
        if (result == null || result instanceof Path && (((Path) result).isEmpty()
                || members.emptyPath.get(null) == result)) return result;
        NativeShape original = nativeResultShape(row, result);
        int left = Math.max(window.bounds.left, original.left);
        int right = Math.min(window.bounds.right, original.right);
        if (left >= right) return result;
        float radius = Math.min(window.radius, Math.min((window.bottom - window.top) / 2f, (right - left) / 2f));
        if (!finite(radius) || radius <= 0f) return result;
        TailShape shape = new TailShape(window.stack.get(), left, window.top, right, window.bottom, radius);
        Object resolved = result;
        Path path = result instanceof Path ? (Path) result : null;
        Path rounded = null;
        if (path != null) {
            // Construct into a private path first. EMPTY_PATH and the native returned path are
            // untouched if native geometry resolution or background synchronization fails.
            rounded = new Path();
            members.roundedPath.invoke(row, left, window.top, right, window.bottom, radius, radius, rounded);
            if (rounded.isEmpty()) return result;
        } else if (isSpotlightSpec(result)) {
            resolved = spotlightFor(result.getClass()).create(left, window.top, right, window.bottom,
                    radius, radius, original.smooth);
        } else return result;
        try { shape.sync(members, row); }
        catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Restore the original call's native tuple without calling nativeDraw a second time.
            try { original.sync(members, row); }
            catch (ReflectiveOperationException | RuntimeException restoreFailure) {
                tailShapes.put(row, shape);
                warn(restoreFailure, "Native notification tail shape fallback unavailable");
            }
            throw unavailable;
        }
        if (path != null) path.set(rounded);
        tailShapes.put(row, shape);
        return resolved;
    }

    private static final class NativeShape {
        final int left, top, right, bottom;
        final float topRadius, bottomRadius;
        final boolean smooth;
        NativeShape(int left, int top, int right, int bottom, float topRadius, float bottomRadius, boolean smooth) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.topRadius = topRadius; this.bottomRadius = bottomRadius; this.smooth = smooth;
        }
        void sync(RowAccess members, View row) throws ReflectiveOperationException {
            Object extension = members.outlineViewEx.get(row);
            if (extension == null) throw new IllegalStateException("Native notification outline extension unavailable");
            members.syncBackground.invoke(extension, topRadius, left, top, bottomRadius, right, bottom);
        }
    }

    private NativeShape nativeResultShape(View row, Object result) throws ReflectiveOperationException {
        if (result instanceof Path) {
            RectF bounds = new RectF();
            ((Path) result).computeBounds(bounds, true);
            RowAccess members = rowAccess(row);
            boolean roundBoth = bool(members.alwaysRoundBothCorners, row);
            Method topMethod = roundBoth ? members.maxRadius : members.topRadius;
            Method bottomMethod = roundBoth ? members.maxRadius : members.bottomRadius;
            if (topMethod == null || bottomMethod == null)
                throw new NoSuchMethodException("Native notification corner radii unavailable");
            float topRadius = ((Number) topMethod.invoke(row)).floatValue();
            float bottomRadius = ((Number) bottomMethod.invoke(row)).floatValue();
            if (!finite(topRadius) || !finite(bottomRadius) || !finite(bounds.left) || !finite(bounds.top)
                    || !finite(bounds.right) || !finite(bounds.bottom))
                throw new IllegalStateException("Native notification path bounds unavailable");
            return new NativeShape(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right),
                    Math.round(bounds.bottom), topRadius, bottomRadius, true);
        }
        if (isSpotlightSpec(result)) return spotlightFor(result.getClass()).read(result);
        throw new IllegalArgumentException("Unsupported native notification outline result");
    }

    private SpotlightAccess spotlightFor(Class<?> type) throws ReflectiveOperationException {
        SpotlightAccess members = spotlightAccess.get(type);
        if (members == null) { members = new SpotlightAccess(type); spotlightAccess.put(type, members); }
        return members;
    }

    private static boolean isSpotlightSpec(Object result) {
        return result != null && SPOTLIGHT_SPEC.equals(result.getClass().getName());
    }

    private static final class SpotlightAccess {
        final Constructor<?> constructor;
        final Field left, top, right, bottom, topRadius, bottomRadius, smooth;
        SpotlightAccess(Class<?> type) throws ReflectiveOperationException {
            if (!SPOTLIGHT_SPEC.equals(type.getName())) throw new ClassNotFoundException("Unsupported native spotlight clip");
            constructor = type.getDeclaredConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
                    Float.TYPE, Float.TYPE, Boolean.TYPE);
            constructor.setAccessible(true);
            left = requiredField(type, "left"); top = requiredField(type, "top");
            right = requiredField(type, "right"); bottom = requiredField(type, "bottom");
            topRadius = requiredField(type, "topCornerRadius"); bottomRadius = requiredField(type, "bottomCornerRadius");
            smooth = requiredField(type, "useSmoothRadius");
        }
        Object create(int left, int top, int right, int bottom, float topRadius, float bottomRadius, boolean smooth)
                throws ReflectiveOperationException {
            return constructor.newInstance(left, top, right, bottom, topRadius, bottomRadius, smooth);
        }
        NativeShape read(Object result) throws ReflectiveOperationException {
            return new NativeShape(left.getInt(result), top.getInt(result), right.getInt(result), bottom.getInt(result),
                    topRadius.getFloat(result), bottomRadius.getFloat(result), smooth.getBoolean(result));
        }
    }

    private void restoreNativeTailShape(View row) {
        if (row == null) return;
        TailShape owned = tailShapes.get(row);
        if (owned == null) return;
        try {
            RowAccess members = rowAccess(row);
            restoringTailShapes.put(row, Boolean.TRUE);
            // One fresh native calculation restores its own glass cache. The narrow bypass
            // prevents our registered row from applying its thin layer again during release.
            if (members.nativePath == null) throw new NoSuchMethodException("Native tail restoration unavailable");
            members.nativePath.invoke(row, false);
            if (tailShapes.get(row) == owned) tailShapes.remove(row);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Keep this weak ownership for the next native draw/release to retry. Dropping it
            // before a failed native calculation would strand our old glass clip tuple.
            warn(unavailable, "Native notification tail cache restoration unavailable");
        } finally {
            restoringTailShapes.remove(row);
            row.invalidateOutline();
            row.invalidate();
        }
    }

    private void clearNativeTailShapes(View stack) {
        for (View row : new ArrayList<>(recentTailOutlines.keySet())) {
            SavedState entry = recentTailOutlines.get(row);
            if (entry != null && (stack == null || entry.stack.get() == stack)) forgetRecentOutline(row, entry);
        }
        for (View row : new ArrayList<>(tailShapes.keySet())) {
            TailShape shape = tailShapes.get(row);
            if (shape != null && (stack == null || shape.stack.get() == stack)) restoreNativeTailShape(row);
        }
    }

    private static boolean cornerTopOverflow(View row, RowAccess members, int top, int height, int bottom)
            throws ReflectiveOperationException {
        if (members.alwaysRoundBothCorners == null || members.minimumClippingHeight == null
                || members.clipBottom == null || members.expandableViewEx == null
                || members.actualClipHeight == null) return false;
        Method radiusMethod = members.alwaysRoundBothCorners.getBoolean(row) ? members.maxRadius : members.topRadius;
        if (radiusMethod == null) return false;
        float radius = ((Number) radiusMethod.invoke(row)).floatValue();
        if (!finite(radius) || radius <= 0f || (int) (top + radius) <= bottom) return false;
        Object rowEx = members.expandableViewEx.get(row);
        if (rowEx == null) return false;
        int cut = ((Number) members.actualClipHeight.invoke(rowEx)).intValue();
        int minimum = ((Number) members.minimumClippingHeight.invoke(row)).intValue();
        if (cut < 0) return false;
        // Exact OEM path floor: max(minHeight, actualHeight-bottomClip-actualCut,
        // (int)(top+effectiveTopRadius)). A different bottom mismatch stays native.
        int nativeBottom = Math.max(minimum, height - members.clipBottom.getInt(row) - cut);
        return Math.max(nativeBottom, (int) radius) <= bottom;
    }

    /** Opt-in snapshots of existing geometry only; never requests a new native outline. */
    private static final class TailCutSample {
        final long time;
        final String geometry, shapeDetails;
        final int clipBottom;
        String reason = "INELIGIBLE";
        String overrideDetails = "";
        TailCutSample(long time, String geometry, String shapeDetails, int clipBottom) {
            this.time = time; this.geometry = geometry; this.shapeDetails = shapeDetails;
            this.clipBottom = clipBottom;
        }
    }

    private TailCutSample tailCutSample(View row, SavedState entry) {
        if (tailCutEvents >= 2400) return null;
        try {
            long time = SystemClock.uptimeMillis();
            RowAccess members = rowAccess(row);
            Object target = entry.target.get();
            int height = members.actualHeight == null ? row.getHeight()
                    : ((Number) members.actualHeight.invoke(row)).intValue();
            Object rowEx = members.expandableViewEx == null ? null : members.expandableViewEx.get(row);
            int cut = diagnosticInt(rowEx, "getActualClipHeight");
            int width = diagnosticInt(rowEx, "getClipWidth");
            int nativeWidth = -1, targetHeight = -1, targetTop = -1;
            float targetY = Float.NaN;
            boolean yOwned = false, yMatches = false;
            if (target != null) {
                targetHeight = entry.access.height.getInt(target);
                targetTop = entry.access.clipTop.getInt(target);
                targetY = entry.access.y.read(target);
                yOwned = entry.access.y.owned(target);
                yMatches = entry.projected != null && same(targetY, entry.projected.y);
                Object stateEx = entry.access.extension == null ? null : entry.access.extension.get(target);
                Method animation = stateEx == null ? null : method(stateEx.getClass(), "getRowAnimationStateEx");
                Object source = animation == null ? null : animation.invoke(stateEx);
                Method effectiveWidth = source == null ? null : method(source.getClass(), "getClipWidth", Object.class);
                if (effectiveWidth != null)
                    nativeWidth = ((Number) effectiveWidth.invoke(source, new Object[] { null })).intValue();
            }
            String geometry = "h " + height + "," + cut + " c " + diagnosticInt(members.clipTop, row)
                    + "," + diagnosticInt(members.clipBottom, row) + " o " + diagnosticInt(members.topOverlap, row)
                    + "," + diagnosticInt(members.bottomOverlap, row) + " w " + row.getWidth() + "," + width + "," + nativeWidth
                    + " t " + targetHeight + "," + (entry.projected == null ? -1 : entry.projected.top) + "," + targetTop
                    + " f " + (yOwned ? 1 : 0) + "," + (yMatches ? 1 : 0)
                    + " a " + (nativeRenderAnimations(row).present(row) ? 1 : 0);
            float topRadius = diagnosticFloat(row, bool(members.alwaysRoundBothCorners, row) ? "getMaxRadius" : "getTopCornerRadius");
            float bottomRadius = diagnosticFloat(row, bool(members.alwaysRoundBothCorners, row) ? "getMaxRadius" : "getBottomCornerRadius");
            String shapeDetails = "r " + topRadius + "," + bottomRadius;
            Rect bounds = row.getClipBounds();
            return new TailCutSample(time, geometry, shapeDetails, bounds == null ? height : bounds.bottom);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private void tailCutDiagnostic(View row, TailCutSample sample, Object result) throws ReflectiveOperationException {
        if (!outlineDiagnostics || tailCutEvents >= 2400) return;
        boolean pathResult = result instanceof Path;
        String path = "s";
        float pathBottom = Float.NaN;
        if (result instanceof Path) {
            RectF bounds = new RectF();
            ((Path) result).computeBounds(bounds, true);
            pathBottom = bounds.bottom;
            path = String.valueOf(pathBottom);
        }
        String background = "";
        RowAccess members = rowAccess(row);
        Object bg = members.background == null ? null : members.background.get(row);
        if (bg != null) {
            Field extension = field(bg.getClass(), "mExt");
            Object ext = extension == null ? null : extension.get(bg);
            background = " bg " + diagnosticInt(ext == null ? null : field(ext.getClass(), "clipBottom"), ext)
                    + "," + diagnosticInt(bg, "getActualHeight");
        }
        // Path and spotlight calls have independent fingerprints. Y spring motion is recorded
        // with a changed shape, but does not spend the diagnostic budget by itself each frame.
        Map<View, String> tuples = pathResult ? tailCutPathTuples : tailCutSpecTuples;
        Map<View, Long> times = pathResult ? tailCutPathTimes : tailCutSpecTimes;
        String tuple = sample.reason + " " + sample.geometry + " " + sample.shapeDetails
                + " cb " + sample.clipBottom + " pb " + path + background + sample.overrideDetails;
        if (tuple.equals(tuples.get(row))) return;
        boolean danger = "CORNER_OVERFLOW".equals(sample.reason);
        danger |= pathResult && pathBottom > sample.clipBottom;
        Long last = times.get(row);
        if (last != null && sample.time - last < (danger ? 16L : 120L)) return;
        tuples.put(row, tuple);
        times.put(row, sample.time);
        int event = ++tailCutEvents;
        String stamp = "[TAIL-CUT] " + event + "/" + groupedToken(Integer.toHexString(System.identityHashCode(row)))
                + " u " + sample.time + " " + (pathResult ? "P " : "S ");
        // One generated geometry record fits the app log's 240-character message budget.
        tailCutRecord(stamp + tuple);
    }

    private static void tailCutRecord(String message) {
        // The opt-in/cap is checked before this call. The app log's general sanitizer masks
        // long numbers, so logcat also retains these generated geometry values for correlation.
        String grouped = groupedDigits(message);
        Log.i("C17-TAIL-CUT", grouped);
        ModuleDiagnostics.info("bigclock", grouped);
    }

    private void resetTailCutSession() {
        tailCutEvents = outlineDiagnosticEvents = 0;
        tailCutPathTuples.clear(); tailCutSpecTuples.clear();
        tailCutPathTimes.clear(); tailCutSpecTimes.clear(); outlineReasons.clear();
    }

    /** Generated numeric groups stay readable without changing the shared privacy sanitizer. */
    private static String groupedDigits(String value) {
        StringBuilder result = new StringBuilder(value.length() + 24);
        for (int at = 0; at < value.length();) {
            char ch = value.charAt(at);
            if (ch < '0' || ch > '9') { result.append(ch); ++at; continue; }
            int end = at + 1;
            while (end < value.length() && value.charAt(end) >= '0' && value.charAt(end) <= '9') ++end;
            result.append(groupedToken(value.substring(at, end)));
            at = end;
        }
        return result.toString();
    }

    private static String groupedToken(String value) {
        StringBuilder result = new StringBuilder(value.length() + value.length() / 3);
        for (int at = 0; at < value.length(); ++at) {
            if (at > 0 && (value.length() - at) % 3 == 0) result.append('_');
            result.append(value.charAt(at));
        }
        return result.toString();
    }

    private static int diagnosticInt(Field member, Object owner) throws ReflectiveOperationException {
        return member == null || owner == null ? -1 : member.getInt(owner);
    }

    private static int diagnosticInt(Object owner, String name) throws ReflectiveOperationException {
        Method member = owner == null ? null : method(owner.getClass(), name);
        return member == null ? -1 : ((Number) member.invoke(owner)).intValue();
    }

    private static float diagnosticFloat(Object owner, String name) throws ReflectiveOperationException {
        Method member = owner == null ? null : method(owner.getClass(), name);
        // Roundable's public default methods can be inherited from an interface.
        if (member == null && owner != null) try { member = owner.getClass().getMethod(name); }
        catch (NoSuchMethodException unavailable) { return Float.NaN; }
        return member == null ? Float.NaN : ((Number) member.invoke(owner)).floatValue();
    }

    private static String diagnosticRect(Rect rect) {
        return rect == null ? "none" : rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    private NativeRenderAnimations nativeRenderAnimations(View row) {
        Resources resources = row.getResources();
        NativeRenderAnimations tags = nativeRenderAnimations.get(resources);
        if (tags == null) { tags = new NativeRenderAnimations(resources); nativeRenderAnimations.put(resources, tags); }
        return tags;
    }

    private void forgetRecentOutline(View row, SavedState entry) {
        if (row != null && recentTailOutlines.get(row) == entry) {
            recentTailOutlines.remove(row);
            restoreNativeTailShape(row);
            row.invalidateOutline();
        }
    }

    private static void refreshTailOutline(View stack, SavedState entry) {
        View row = entry.row.get();
        if (entry.outlineRefreshPending && entry.stack.get() == stack && row != null && row.getParent() == stack) {
            entry.outlineRefreshPending = false;
            row.invalidateOutline();
        }
    }

    private void outlineDiagnostic(View row, SavedState entry, String reason, int top, int height, int bottom, int targetTop) {
        if (!outlineDiagnostics || outlineDiagnosticEvents >= 24 || reason.equals(outlineReasons.get(row))) return;
        outlineReasons.put(row, reason);
        ++outlineDiagnosticEvents;
        int overlap = -1, bottomOverlap = -1, bottomClip = -1, backgroundTop = -1, backgroundOverlap = -1;
        int actualClipHeight = -1;
        try {
            RowAccess members = rowAccess(row);
            if (members.topOverlap != null) overlap = members.topOverlap.getInt(row);
            if (members.bottomOverlap != null) bottomOverlap = members.bottomOverlap.getInt(row);
            if (members.clipBottom != null) bottomClip = members.clipBottom.getInt(row);
            Object background = members.background == null ? null : members.background.get(row);
            if (background != null) {
                Field bgTop = field(background.getClass(), "mClipTopAmount");
                Field bgOverlap = field(background.getClass(), "mTopOverlap");
                if (bgTop != null) backgroundTop = bgTop.getInt(background);
                if (bgOverlap != null) backgroundOverlap = bgOverlap.getInt(background);
            }
            Object extension = members.expandableViewEx == null ? null : members.expandableViewEx.get(row);
            Method clipHeight = extension == null ? null : method(extension.getClass(), "getActualClipHeight");
            if (clipHeight != null) actualClipHeight = ((Number) clipHeight.invoke(extension)).intValue();
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Diagnostic only. */ }
        Rect bounds = row.getClipBounds();
        ModuleDiagnostics.info("bigclock", reason + " actualTop " + top + " actualHeight " + height
                + " actualBottom " + bottom + " overlap " + overlap + " actualClipHeight " + actualClipHeight
                + " bottomClip " + bottomClip + " bottomOverlap " + bottomOverlap
                + " backgroundTop " + backgroundTop + " backgroundOverlap " + backgroundOverlap
                + " boundsTop " + (bounds == null ? -1 : bounds.top) + " boundsBottom " + (bounds == null ? -1 : bounds.bottom)
                + " targetTop " + targetTop + " projectedHidden " + entry.projected.hidden);
    }

    /** Fades only owned tail content; the native row glass, layout and touch handling are retained. */
    public Object withNativeTailContent(View content, Canvas canvas, NativeOutlineAction nativeDraw) throws Throwable {
        int layer = -1;
        int opacity = -1;
        View contentRow = null;
        SavedState contentEntry = null;
        String reason = "TAIL_CONTENT_NATIVE_SURFACE";
        if (content != null && canvas != null && nativeContent(content)
                && content.getParent() instanceof View) {
            View row = (View) content.getParent();
            SavedState entry = tailOutlines.get(row);
            contentRow = row;
            contentEntry = entry;
            reason = entry == null ? "TAIL_CONTENT_NATIVE_NO_OWNER" : "TAIL_CONTENT_NATIVE_TARGET_VISIBILITY";
            if (nativeHooksAvailable && settings.enabled && entry != null && entry.projected != null
                    && !entry.projected.hidden && entry.projected.alpha > 0f
                    && row.getVisibility() == View.VISIBLE && entry.contentReveal < 1f) {
                reason = "TAIL_CONTENT_NATIVE_IDENTITY_OR_INTERACTION";
                try {
                    RowAccess members = rowAccess(row);
                    Object target = entry.target.get();
                    View stack = entry.stack.get();
                    if (members.notification && stack != null && row.getParent() == stack
                            && !stackAccess(stack).interactionOwnsLayout(stack)
                            && !members.excluded(row) && !members.interactionOwnsRow(row)
                            && !stackAccess(stack).interactionOwnsRow(stack, row)
                            && members.state != null && target != null && members.state.invoke(row) == target) {
                        reason = "TAIL_CONTENT_NATIVE_TARGET_META";
                        if (entry.access.y.owned(target)
                                && same(entry.access.y.read(target), entry.projected.y)
                                && entry.access.clipTop.getInt(target) == entry.projected.top
                                && entry.access.clipBottom.getInt(target) == entry.projected.bottom) {
                            Rect bounds = new Rect();
                            reason = "TAIL_CONTENT_NATIVE_EMPTY_CLIP";
                            if (canvas.getClipBounds(bounds) && !bounds.isEmpty()) {
                                opacity = Math.round(Math.max(0f, Math.min(1f, entry.contentReveal)) * 255f);
                                layer = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, opacity);
                                reason = opacity == 0 ? "TAIL_CONTENT_ZERO_LAYER" : "TAIL_CONTENT_FADE_LAYER";
                            }
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException unavailable) {
                    reason = "TAIL_CONTENT_NATIVE_ERROR";
                    warn(unavailable, "Native notification tail content fade unavailable; original content retained");
                }
            }
        }
        try { contentDiagnostic(content, contentRow, contentEntry, reason, opacity); }
        catch (RuntimeException unavailable) { /* Diagnostics must not change native drawing. */ }
        try { return nativeDraw.draw(); }
        finally { if (layer >= 0) canvas.restoreToCount(layer); }
    }

    /** Twelve opt-in guard transitions, never notification text or user-provided values. */
    private void contentDiagnostic(View content, View row, SavedState entry, String reason, int opacity) {
        if (!outlineDiagnostics || content == null || contentDiagnosticEvents >= 12) return;
        String parentClass = content.getParent() == null ? "none" : content.getParent().getClass().getName();
        String rowParentClass = row == null || row.getParent() == null ? "none" : row.getParent().getClass().getName();
        SavedState ancestorEntry = null;
        View ancestor = row;
        for (int depth = 0; depth < 4 && ancestor != null; depth++) {
            ancestorEntry = tailOutlines.get(ancestor);
            if (ancestorEntry != null) break;
            ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
        }
        if (entry == null) {
            String kind = reason + " " + content.getClass().getName() + " " + parentClass + " " + rowParentClass
                    + " " + (ancestorEntry != null);
            if (nativeContentReasons.contains(kind)) return;
            nativeContentReasons.add(kind);
        } else {
            if (reason.equals(contentReasons.get(content))) return;
            contentReasons.put(content, reason);
        }
        ++contentDiagnosticEvents;
        int targetTop = -1, projectedTop = -1, expectedOpacity = -1;
        boolean yOwned = false;
        if (entry != null && entry.projected != null) {
            projectedTop = entry.projected.top;
            expectedOpacity = Math.round(entry.contentReveal * 255f);
            Object target = entry.target.get();
            if (target != null) try {
                targetTop = entry.access.clipTop.getInt(target);
                yOwned = entry.access.y.owned(target);
            } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Diagnostic only. */ }
        }
        ModuleDiagnostics.info("bigclock", reason + " contentClass " + content.getClass().getName()
                + " parentClass " + parentClass + " rowParentClass " + rowParentClass
                + " ancestorOwner " + (ancestorEntry != null) + " opacity " + opacity + " expectedOpacity " + expectedOpacity
                + " targetTop " + targetTop + " projectedTop " + projectedTop + " yOwned " + yOwned
                + " recentOwner " + (row != null && recentTailOutlines.containsKey(row)));
    }

    private List<RowState> rows(ViewGroup stack, Map<Object, SavedState> previous)
            throws ReflectiveOperationException {
        List<RowState> result = new ArrayList<>();
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            RowAccess rowMembers = rowAccess(child);
            if (!rowMembers.notification || child.getVisibility() == View.GONE || rowMembers.excluded(child)
                    || rowMembers.state == null) continue;
            Object state = rowMembers.state.invoke(child);
            if (state == null) continue;
            StateAccess members = access.get(state.getClass());
            if (members == null) { members = new StateAccess(state.getClass()); access.put(state.getClass(), members); }
            Values current = members.read(state);
            SavedState old = previous.get(state);
            RowState row = old != null && old.row.get() == child && old.stack.get() == stack
                    ? old.nativeBaseline(child, state, current, i)
                    : new RowState(child, state, members, current, i,
                            members.y.reason(state), members.alpha.reason(state));
            Values nativeValue = row.nativeValues;
            if (nativeValue.gone || nativeValue.height <= 0 || !finite(nativeValue.y)
                    || !finite(nativeValue.alpha)) continue;
            result.add(row);
        }
        boolean ranked = true;
        for (RowState row : result) if (row.nativeValues.order < 0) { ranked = false; break; }
        final boolean nativeRanking = ranked;
        // NSSL's own notGoneIndex comparator stays stable during overlapping/spring positions.
        // An unranked new row must not let spring positions reshuffle the folded tail.
        result.sort((first, second) -> {
            int position = nativeRanking ? Integer.compare(first.nativeValues.order, second.nativeValues.order)
                    : Integer.compare(first.childIndex, second.childIndex);
            return position != 0 ? position : Integer.compare(first.childIndex, second.childIndex);
        });
        return result;
    }

    private boolean nativeInputsMatch(ViewGroup stack, StackState state, StackAccess members) {
        if (state.inputs.size() != stack.getChildCount()) return false;
        try {
            for (int i = 0; i < state.inputs.size(); i++)
                if (!state.inputs.get(i).matches(stack.getChildAt(i), members, stack)) return false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }

    private void captureNativeInputs(ViewGroup stack, StackState state, StackAccess members)
            throws ReflectiveOperationException {
        state.inputs.clear();
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            RowAccess row = rowAccess(child);
            Object target = row.state == null ? null : row.state.invoke(child);
            StateAccess properties = null;
            Values expected = null;
            if (target != null) {
                properties = access.get(target.getClass());
                if (properties == null) {
                    properties = new StateAccess(target.getClass());
                    access.put(target.getClass(), properties);
                }
                expected = properties.read(target);
            }
            state.inputs.add(new NativeInput(child, target, row, properties, expected,
                    row.notification && (row.interactionOwnsRow(child) || members.interactionOwnsRow(stack, child)),
                    row.notification ? widthSnapshot(child, target, properties) : null));
        }
        state.lastConfiguration = new Configuration(stack.getResources().getConfiguration());
    }

    private RowAccess rowAccess(View view) {
        RowAccess members = rowAccess.get(view.getClass());
        if (members == null) { members = new RowAccess(view.getClass()); rowAccess.put(view.getClass(), members); }
        return members;
    }

    private StackAccess stackAccess(View view) {
        StackAccess members = stackAccess.get(view.getClass());
        if (members == null) { members = new StackAccess(view.getClass()); stackAccess.put(view.getClass(), members); }
        return members;
    }

    /** Native resources follow density/theme configuration; user gap/inset preferences are ignored. */
    private float nativeGap(View stack, List<RowState> rows) {
        try {
            Resources resources = stack.getResources();
            NativeGap gap = nativeGaps.get(resources);
            if (gap == null) { gap = new NativeGap(resources); nativeGaps.put(resources, gap); }
            float pixels = gap.pixels(resources);
            if (finite(pixels) && pixels >= 0f) return pixels;
        } catch (RuntimeException unavailable) { /* Other OEMs may not expose this dimension. */ }
        // NSSL.initView loads this actual spacing from notification_divider_height.
        Field padding = stackAccess(stack).paddingBetweenElements;
        if (padding != null) try {
            int pixels = padding.getInt(stack);
            if (pixels >= 0) return pixels;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Native geometry below. */ }
        float spacing = Float.NaN;
        for (int i = 1; i < rows.size(); i++) {
            Values previous = rows.get(i - 1).nativeValues;
            float pixels = rows.get(i).nativeValues.y - previous.y - previous.height;
            if (finite(pixels) && pixels > 0f && (!finite(spacing) || pixels < spacing)) spacing = pixels;
        }
        if (finite(spacing)) return spacing;
        for (RowState row : rows) {
            int pixels = row.row.getPaddingBottom();
            if (pixels > 0 && (!finite(spacing) || pixels < spacing)) spacing = pixels;
        }
        // No native spacing is available: leave no artificial extra peek distance.
        return finite(spacing) ? spacing : 0f;
    }

    /** Read native decoration targets once, rather than rescanning the whole host for every tail. */
    private List<DecorationState> decorations(ViewGroup stack) throws ReflectiveOperationException {
        List<DecorationState> result = new ArrayList<>();
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            RowAccess rowMembers = rowAccess(child);
            if (child.getVisibility() != View.VISIBLE || rowMembers.notification || rowMembers.state == null) continue;
            Object state = rowMembers.state.invoke(child);
            if (state == null) continue;
            StateAccess members = access.get(state.getClass());
            if (members == null) { members = new StateAccess(state.getClass()); access.put(state.getClass(), members); }
            Values value = members.read(state);
            if (value.gone || value.hidden || value.alpha <= 0f || value.height <= 0 || !finite(value.y)) continue;
            result.add(new DecorationState(value,
                    namedClass(child.getClass(), "com.android.systemui.statusbar.NotificationShelf")
                            || namedClass(child.getClass(), "com.android.systemui.statusbar.notification.footer.ui.view.FooterView")));
        }
        return result;
    }

    /** Decorations keep native coordinates, order and click bounds; peeks never extend into them. */
    private static float decorationBoundary(List<DecorationState> decorations, float anchorBottom, float tailBottom) {
        float boundary = Float.NaN;
        for (DecorationState decoration : decorations) {
            Values value = decoration.values;
            if (value.y >= anchorBottom && value.y <= tailBottom
                    && (!finite(boundary) || value.y < boundary)) boundary = value.y;
        }
        return boundary;
    }

    /** Shelf/footer are bounds, while an intervening media or section is an occupied native region. */
    private static float decorationEndBefore(List<DecorationState> decorations, float anchorBottom, float tailTop) {
        float end = Float.NaN;
        for (DecorationState decoration : decorations) {
            if (decoration.boundaryOnly) continue;
            Values value = decoration.values;
            if (value.y >= anchorBottom && value.y < tailTop
                    && (!finite(end) || value.y + value.height > end)) end = value.y + value.height;
        }
        return end;
    }

    private void commitTargets(StackState state, List<SavedState> next) {
        Map<Object, SavedState> active = new IdentityHashMap<>();
        Map<Object, SavedState> previousTargets = new IdentityHashMap<>();
        for (SavedState entry : next) {
            Object target = entry.target.get();
            if (target != null) active.put(target, entry);
        }
        for (SavedState previous : state.saved) {
            Object target = previous.target.get();
            if (target != null) previousTargets.put(target, previous);
            SavedState replacement = target == null ? null : active.get(target);
            boolean sameWidthOwner = replacement != null && previous.width != null && replacement.width != null
                    && replacement.row.get() == previous.row.get()
                    && replacement.width.stateExtension.get() == previous.width.stateExtension.get()
                    && replacement.width.rowExtension.get() == previous.width.rowExtension.get();
            removeTailWidth(previous, !sameWidthOwner);
            if (replacement == null || !tailTarget(replacement) || replacement.row.get() != previous.row.get())
                removeTailOutline(previous);
            if (replacement == null || !same(previous.contentReveal, replacement.contentReveal))
                invalidateNativeContent(previous);
            if (target == null || !active.containsKey(target)) try { previous.restore(); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { warn(unavailable); }
        }
        state.saved.clear();
        state.saved.addAll(next);
        for (SavedState entry : next) {
            View row = entry.row.get();
            if (entry.width != null) {
                Object stateEx = entry.width.stateExtension.get(), rowEx = entry.width.rowExtension.get();
                if (stateEx != null && rowEx != null) {
                    tailWidthTargets.put(stateEx, entry);
                    tailWidthRows.put(rowEx, entry);
                }
            }
            if (!previousTargets.containsKey(entry.target.get())) invalidateNativeContent(entry);
            if (row != null && tailTarget(entry)) {
                SavedState old = tailOutlines.put(row, entry);
                recentTailOutlines.remove(row);
                if (old == null || old.target.get() != entry.target.get()) {
                    entry.outlineRefreshPending = true;
                    row.invalidateOutline();
                }
            }
            // A caller outside a native apply transaction still receives its new fixed width.
            // During native apply, its normal getter/setter path applies it before this refresh.
            if (state.applicationDepth == 0) refreshTailWidth(entry);
        }
    }

    private void refreshTailWidth(SavedState entry) {
        if (entry.width == null) return;
        Object rowEx = entry.width.rowExtension.get();
        if (!ownsTailWidth(entry, null, rowEx)) return;
        try {
            if (entry.width.access.integer(entry.width.access.getWidth, rowEx) != entry.projectedClipWidth) {
                entry.width.access.setWidth.invoke(rowEx, entry.projectedClipWidth);
                View row = entry.row.get();
                if (row != null) row.invalidateOutline();
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            removeTailWidth(entry, true);
            warn(unavailable, "Native notification tail width application failed; original width retained");
        }
    }

    private void removeTailWidth(SavedState entry, boolean restoreActual) {
        if (entry.width == null) return;
        Object stateEx = entry.width.stateExtension.get(), rowEx = entry.width.rowExtension.get();
        boolean ownedRow = rowEx != null && tailWidthRows.get(rowEx) == entry;
        if (ownedRow && restoreActual) try {
            View row = entry.row.get();
            // Cancel only our old width property's pending writes, while its owned setter
            // guard is still installed. Native Y/height/content animations keep running.
            if (row != null && entry.width.access.widthAnimatorTag != 0
                    && row.getTag(entry.width.access.widthAnimatorTag) != null)
                entry.width.access.abortWidth.invoke(null, row, entry.width.access.widthAnimatorTag);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            warn(unavailable, "Native notification tail width animation release unavailable");
        }
        if (stateEx != null && tailWidthTargets.get(stateEx) == entry) tailWidthTargets.remove(stateEx);
        if (ownedRow) tailWidthRows.remove(rowEx);
        if (!ownedRow || !restoreActual) return;
        try {
            View row = entry.row.get();
            if (row == null) return;
            RowAccess members = rowAccess(row);
            if (members.state == null || members.expandableViewEx == null || members.expandableViewEx.get(row) != rowEx) return;
            Object target = members.state.invoke(row);
            if (target == null) return;
            StateAccess properties = access.get(target.getClass());
            if (properties == null) {
                properties = new StateAccess(target.getClass());
                access.put(target.getClass(), properties);
            }
            // Rebinding can replace our old weak target. Restore from the
            // current native source map instead of replaying a stale saved clip width.
            Object currentEx = properties.extension == null ? null : properties.extension.get(target);
            if (currentEx == null || entry.width.access.stateBase.invoke(currentEx) != target
                    || entry.width.access.rowView.invoke(rowEx) != row) return;
            int nativeWidth = entry.width.access.nativeWidth(currentEx);
            Object source = entry.width.access.nativeSource(currentEx);
            entry.width.access.setSource.invoke(rowEx, source);
            if (entry.width.access.integer(entry.width.access.getWidth, rowEx) != nativeWidth)
                entry.width.access.setWidth.invoke(rowEx, nativeWidth);
            row.invalidateOutline();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            warn(unavailable, "Native notification tail width restoration unavailable");
        }
    }

    private static boolean tailTarget(SavedState entry) {
        return entry.projected != null && entry.projected.top > entry.nativeValues.top;
    }

    private void removeTailOutline(SavedState entry) {
        View row = entry.row.get();
        if (row != null && tailOutlines.get(row) == entry) {
            tailOutlines.remove(row);
            entry.outlineRetiredAt = SystemClock.uptimeMillis();
            entry.outlineRefreshPending = true;
            recentTailOutlines.put(row, entry);
            row.invalidateOutline();
        }
    }

    private void restoreEntries(List<SavedState> entries) {
        for (SavedState entry : entries) {
            removeTailWidth(entry, true);
            try { entry.restore(); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { warn(unavailable); }
            removeTailOutline(entry);
            invalidateNativeContent(entry);
        }
    }

    private static void invalidateNativeContent(SavedState entry) {
        View row = entry.row.get();
        if (!(row instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) row;
        // dispatchDraw may be recorded into a cached RenderNode. Opacity changes and release
        // must record the direct native content again, rather than retain an old faded node.
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (nativeContent(child)) child.invalidate();
        }
    }

    private void releaseTargets(StackState state) {
        state.completed = false;
        state.applicationProjected = false;
        state.inputs.clear();
        state.lastConfiguration = null;
        restoreEntries(state.saved);
        state.saved.clear();
    }

    private void requestNativeUpdate(View stack, StackState state) {
        if (stack == null || state.layoutDepth > 0 || !stack.isAttachedToWindow()) return;
        Method request = stackAccess(stack).request;
        if (request != null) try { request.invoke(stack); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private void warn(Throwable error) {
        warn(error, "Native notification stacking unavailable; original list retained");
    }

    private void warn(Throwable error, String message) {
        if (warnings++ < 3) ModuleDiagnostics.error("bigclock", message, error);
    }

    private static void completed(StackState state, Settings options, float progress, boolean projectable,
            float bottomBoundary) {
        state.lastSettings = options;
        state.lastProgress = progress;
        state.lastBottomBoundary = bottomBoundary;
        state.lastProjectable = projectable;
        state.completed = true;
        if (state.applicationDepth > 0) state.applicationProjected = true;
    }

    private static boolean bool(Method member, Object owner) {
        if (member != null) try { return Boolean.TRUE.equals(member.invoke(owner)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    private static boolean bool(Field member, Object owner) {
        if (member != null) try { return member.getBoolean(owner); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    private static boolean namedClass(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) if (name.equals(type.getName())) return true;
        return false;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        for (; type != null; type = type.getSuperclass()) try {
            Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Method publicMethod(Class<?> type, String name) {
        Method member = method(type, name);
        if (member != null) return member;
        try { return type.getMethod(name); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static Field field(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Field requiredField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = field(type, name);
        if (field == null) throw new NoSuchFieldException("Unsupported native notification " + name);
        return field;
    }

    private static boolean nativeContent(View view) {
        return namedClass(view.getClass(), CONTENT) || namedClass(view.getClass(), CHILDREN);
    }

    private static Method requiredMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = method(type, name, parameters);
        if (method == null) throw new NoSuchMethodException("Unsupported native notification " + name);
        return method;
    }

    private static float number(Object value, float fallback) {
        float number = value instanceof Number ? ((Number) value).floatValue() : fallback;
        return finite(number) ? number : fallback;
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    private static boolean same(float a, float b) { return Float.floatToIntBits(a) == Float.floatToIntBits(b); }
    private static boolean sameText(String a, String b) { return a == null ? b == null : a.equals(b); }
    private static float lerp(float from, float to, float progress) { return from + (to - from) * progress; }
}
