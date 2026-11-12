// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One finite status-bar flow. Native controllers retain every child, index, LP,
 * pipeline and animator. Only verified structural boxes and layout targets move;
 * the coordinator never writes a View translation, alpha, drawable or parent.
 */
public final class StatusBarFlowLayout {
    public interface NativeAccess {
        boolean container(View view);
        String slot(View view);
        /** Only original system-hint leaves, excluding notification models and standalone privacy chips. */
        default boolean hint(View view){return false;}
        boolean eligible(ViewGroup parent, View child);
        Object state(View view);
        float x(Object state);
        void x(Object state, float value);
        int visible(Object state);
        void visible(Object state, int value);
        default void apply(Object state, View child) { }
        default boolean geometrySupported() { return true; }
        /** Root-only geometry must not replay native alpha, hidden or add lifecycle. */
        default void applyGeometry(Object state, View child) { apply(state, child); }
        /** Commit only the native renderer's icon/dot/hidden state, without a ViewState lifecycle. */
        default void applyVisibility(Object state, View child) { }
        /** Capture caller-sensitive native flags before its apply call returns. */
        default Object applyContext(Object state) { return null; }
        default void apply(Object state, View child, Object context) { apply(state, child); }
        /** Side-effect-free capability check for a complete native frame transaction. */
        default boolean canRebase(Object state, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            return dx == 0f && parentWidthDelta == 0f && childWidthDelta == 0f;
        }
        /** A new native destination starts from the current frame, not a stale curve end. */
        default void retargetFromCurrent(Object state, View child) { }
        default float distance(Object state) { return Float.NaN; }
        default float curveEnd(View child) { return Float.NaN; }
        /** Translate a native coordinate frame while retaining its X animation curve. */
        default boolean rebase(Object state, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            return dx == 0f && parentWidthDelta == 0f && childWidthDelta == 0f;
        }
    }
    private StatusBarLayoutSettings.Snapshot settings = StatusBarLayoutSettings.Snapshot.parse(null);
    private Bundle values = new Bundle();
    private FeatureOptions features = FeatureOptions.from(values);
    private final Map<ViewGroup, Scene> scenes = new WeakHashMap<>();
    private final Map<ViewGroup, Boolean> roots = new WeakHashMap<>();
    private NativeAccess access;
    private java.util.function.Predicate<View> hintRetention;
    private NetworkSharedSlotLayout sharedSlots;
    private final Map<ViewGroup, SharedWidths> sharedWidths = new WeakHashMap<>();
    /** Per scene: the Wi-Fi + data width last planned with both present, so a missing one is held without a gap when both show.
     *  Like {@link SharedWidths} it belongs to one screen configuration. */
    private final Map<ViewGroup, KnownRadios> fullRadios = new WeakHashMap<>();
    private static final class KnownRadios {
        final String configuration; final float width;
        KnownRadios(String configuration, float width) { this.configuration = configuration; this.width = width; }
    }
    /** Keep the latest natural width of each alternative, not a lifetime maximum. */
    /** Wi-Fi and the network label share one slot width. It belongs to one screen configuration: after a rotation or a
     *  density/font change, a width measured in the old one (e.g. the portrait label while landscape shows Wi-Fi) is stale. */
    private static final class SharedWidths {
        final String configuration;
        int wifi, label;
        SharedWidths(String configuration) { this.configuration=configuration; }
        int width() { return Math.max(wifi,label); }
    }
    private static String sharedWidthKey(View root) {
        android.content.res.Configuration configuration = root.getResources().getConfiguration();
        return root.getResources().getDisplayMetrics().density + ":" + (configuration == null ? "" : configuration.orientation
                + ":" + configuration.screenWidthDp + ":" + configuration.fontScale);
    }
    private final Map<ViewGroup, Integer> nativeMeasureRetries = new WeakHashMap<>();
    private final Map<ViewGroup, Runnable> nativeMeasurePosts = new WeakHashMap<>();
    private final Map<View, Boolean> nativeMeasureEligibility = new WeakHashMap<>();
    private final Map<View, Integer> nativeMeasureAttempts = new WeakHashMap<>();
    private final Map<View, NativeMeasureOwner> nativeMeasureOwners = new WeakHashMap<>();
    private static final int NATIVE_MEASURE_RETRIES = 3;
    /** Roots whose native fluid-cloud capsule currently occupies status-bar space. */
    private final Map<ViewGroup, Boolean> capsuleRoots = new WeakHashMap<>();
    /** Screen-X snapshots reported by native; these never become synthetic cutout bounds. */
    private final Map<ViewGroup, float[]> capsuleReportedRanges = new WeakHashMap<>(), capsuleRanges = new WeakHashMap<>();
    /** A capsule change waiting to last: its target state and the posted switch. */
    private final Map<ViewGroup, Boolean> capsuleTargets = new WeakHashMap<>();
    private final Map<ViewGroup, Runnable> capsuleSwitches = new WeakHashMap<>();
    /** Device code162: switching apps flashed the capsule for a moment and the whole row jumped between the native and the custom
     * places ("icons twitch"). A capsule must show this long before its space is reserved, and be gone this long before it returns. */
    static final long CAPSULE_SHOW_MS = 120, CAPSULE_HIDE_MS = 500;
    /** Leaves still 0x0 after every forced native traversal: their native parent skips them. */
    private final Map<View, Boolean> nativeUnmeasured = new WeakHashMap<>();
    private final Map<ViewGroup, WifiNeighbors> wifiNeighbors = new WeakHashMap<>();
    private final Map<View, IconFrame> iconFrames = new WeakHashMap<>();
    private final Map<View, WeakReference<Object>> nativeHandoffs = new WeakHashMap<>();
    /**
     * Device code162 ("icons twitch when switching apps"): the native battery binder gives the battery view a LayoutTransition for
     * its own icon/percentage. By default that also animates the parent hierarchy, so for 200ms it slid the battery itself from the
     * native place back to the custom one, rewriting its bounds every frame. While the flow owns a row its containers keep their own
     * child animations but never animate the row's elements. Values: the native setting to put back.
     */
    private final Map<android.animation.LayoutTransition, Boolean> heldParentAnimations = new WeakHashMap<>();
    private Method privacyList;
    private boolean internal, released;
    private int revision;
    private int traceBudget;
    private long measureCallbacks, layoutCallbacks;
    private static final Map<View, Boolean> OWNED = Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean owns(View view) { return view != null && OWNED.containsKey(view); }

    public void resolve(ClassLoader loader) throws ReflectiveOperationException {
        try { privacyList = loader.loadClass("com.android.systemui.privacy.OngoingPrivacyChip").getMethod("getPrivacyList"); }
        catch (ClassNotFoundException | NoSuchMethodException optionalPrivacy) { privacyList = null; }
        Class<?> container = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
        Class<?> display = loader.loadClass("com.android.systemui.statusbar.StatusIconDisplayable");
        Class<?> state = loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
        Method slot = display.getMethod("getSlot"), shown = display.getMethod("isIconVisible"), blocked = display.getMethod("isIconBlocked");
        Method getVisible = null, setVisible = null;
        try {
            getVisible = display.getMethod("getVisibleState");
            setVisible = display.getMethod("setVisibleState", Integer.TYPE, Boolean.TYPE);
        } catch (NoSuchMethodException unsupportedVisibility) { getVisible = setVisible = null; }
        Class<?> hintIcon=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
        Method hintNotification=hintIcon.getMethod("getNotification");
        Method getX = state.getMethod("getXTranslation"), setX = state.getMethod("setXTranslation", Float.TYPE);
        Method apply = state.getDeclaredMethod("applyToView", View.class); apply.setAccessible(true);
        Field visibility = state.getDeclaredField("visibleState"); visibility.setAccessible(true);
        Field ignored = container.getDeclaredField("mIgnoredSlots"); ignored.setAccessible(true);
        Method shiftAnimation = null; Object noNewAnimations = null, noNewFilter = null;
        Field endDistance = null, added = null, animateX = null, qsTransition = null;
        try { qsTransition = state.getDeclaredField("qsExpansionTransitioning"); qsTransition.setAccessible(true); }
        catch (NoSuchFieldException ordinaryNativeState) { }
        int animatorTag = 0, startTag = 0, endTag = 0;
        try {
            Class<?> viewState = loader.loadClass("com.android.systemui.statusbar.notification.stack.ViewState");
            Class<?> properties = loader.loadClass("com.android.systemui.statusbar.notification.stack.AnimationProperties");
            Class<?> filter = loader.loadClass("com.android.systemui.statusbar.notification.stack.AnimationFilter");
            Field noNew = viewState.getDeclaredField("NO_NEW_ANIMATIONS"); noNew.setAccessible(true);
            noNewAnimations = noNew.get(null);
            noNewFilter = properties.getMethod("getAnimationFilter").invoke(noNewAnimations);
            animateX = filter.getDeclaredField("animateX"); animateX.setAccessible(true);
            if (noNewAnimations == null || noNewFilter == null || animateX.getBoolean(noNewFilter))
                throw new IllegalStateException("Native no-new-X filter unavailable");
            endDistance = state.getDeclaredField("distanceToViewEnd"); endDistance.setAccessible(true);
            added = state.getDeclaredField("justAdded"); added.setAccessible(true);
            animatorTag = viewState.getField("TAG_ANIMATOR_TRANSLATION_X").getInt(null);
            startTag = viewState.getField("TAG_START_TRANSLATION_X").getInt(null);
            endTag = viewState.getField("TAG_END_TRANSLATION_X").getInt(null);
            if (animatorTag == 0 || startTag == 0 || endTag == 0
                    || animatorTag == startTag || animatorTag == endTag || startTag == endTag)
                throw new IllegalStateException("Native X animation tags unavailable");
            shiftAnimation = viewState.getDeclaredMethod("startXTranslationAnimation", View.class, properties);
            shiftAnimation.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException optionalAnimationRebase) {
            // Preserve the ordinary native flow on ROMs with another animation
            // contract. Callers can retain their current frame when rebase=false.
            shiftAnimation = null;
        }
        final Method nativeShift = shiftAnimation;
        final Method nativeGetVisible = getVisible, nativeSetVisible = setVisible;
        final Object nativeNoNew = noNewAnimations, nativeFilter = noNewFilter;
        final Field nativeDistance = endDistance, nativeAdded = added, nativeAnimateX = animateX;
        final Field nativeQsTransition = qsTransition;
        final int nativeAnimatorTag = animatorTag, nativeStartTag = startTag, nativeEndTag = endTag;
        access = new NativeAccess() {
            private boolean rebasing;
            private int tag(View view) { return ResourceIds.get(view.getResources(), "status_bar_view_state_tag", "id", view.getContext().getPackageName()); }
            public boolean container(View view) { return container.isInstance(view); }
            public String slot(View view) { try { return display.isInstance(view) ? (String) slot.invoke(view) : null; } catch (ReflectiveOperationException e) { return null; } }
            public boolean hint(View view){try{return hintIcon.isInstance(view)&&hintNotification.invoke(view)==null;}catch(ReflectiveOperationException|RuntimeException unavailable){return false;}}
            public boolean eligible(ViewGroup parent, View child) {
                try {
                    Object names = ignored.get(parent); String name = slot(child);
                    return child.getVisibility() != View.GONE && Boolean.TRUE.equals(shown.invoke(child))
                            && !Boolean.TRUE.equals(blocked.invoke(child)) && (!(names instanceof Set) || !((Set<?>) names).contains(name));
                } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
            }
            public Object state(View view) { int id = tag(view); Object object = id == 0 ? null : view.getTag(id); return state.isInstance(object) ? object : null; }
            public float x(Object object) { try { return ((Number) getX.invoke(object)).floatValue(); } catch (ReflectiveOperationException e) { return Float.NaN; } }
            public void x(Object object, float value) { try { setX.invoke(object, value); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); } }
            public int visible(Object object) { try { return visibility.getInt(object); } catch (ReflectiveOperationException e) { return 2; } }
            public void visible(Object object, int value) { try { visibility.setInt(object, value); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); } }
            public void apply(Object object, View child) { try { apply.invoke(object, child); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); } }
            public boolean geometrySupported() { return nativeShift != null && nativeGetVisible != null && nativeSetVisible != null; }
            public void applyGeometry(Object object, View child) {
                if (!geometrySupported()) throw new IllegalStateException("Native partial geometry unavailable");
                try {
                    float target = x(object);
                    nativeShift.invoke(object, child, nativeNoNew);
                    // A root-only commit also replaces the last geometric destination.
                    // StatusIconState's next ordinary apply reconstructs its start from
                    // distanceToViewEnd; leaving that history at the old allocation
                    // jumps a returning Wi-Fi icon through the battery. This is geometry
                    // bookkeeping only: the add/QS/alpha lifecycle still belongs to OEM.
                    if (child != null && state(child) == object && container.isInstance(child.getParent())
                            && !nativeAdded.getBoolean(object) && Float.isFinite(target) && x(object) == target) {
                        View parent = (View) child.getParent();
                        float distance = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                                ? target : parent.getWidth() - target - child.getWidth();
                        if (Float.isFinite(distance)) nativeDistance.setFloat(object, distance);
                    }
                }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            public void applyVisibility(Object object, View child) {
                if (!geometrySupported()) throw new IllegalStateException("Native renderer visibility unavailable");
                try {
                    int target = visibility.getInt(object);
                    if (((Number) nativeGetVisible.invoke(child)).intValue() != target)
                        nativeSetVisible.invoke(child, target, false);
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            public Object applyContext(Object object) {
                try { return nativeQsTransition == null ? null : nativeQsTransition.getBoolean(object); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            public void apply(Object object, View child, Object context) {
                if (!(context instanceof Boolean) || nativeQsTransition == null) { apply(object, child); return; }
                try {
                    boolean successor = nativeQsTransition.getBoolean(object);
                    nativeQsTransition.setBoolean(object, (Boolean) context);
                    try { apply(object, child); }
                    finally {
                        // updateStates writes the NEXT transition flag after
                        // its original call. A delayed commit consumes only
                        // that call's captured flag; preserve the successor.
                        if (!nativeQsTransition.getBoolean(object)) nativeQsTransition.setBoolean(object, successor);
                    }
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            public float distance(Object object) { try { return nativeDistance == null ? Float.NaN : nativeDistance.getFloat(object); } catch (ReflectiveOperationException e) { return Float.NaN; } }
            public float curveEnd(View child) { Object end = child.getTag(nativeEndTag); return end instanceof Float ? (Float) end : Float.NaN; }
            public void retargetFromCurrent(Object object, View child) {
                if (rebasing || nativeShift == null || child == null || !state.isInstance(object)
                        || !container.isInstance(child.getParent())) return;
                try {
                    if (state(child) != object || nativeAdded.getBoolean(object)) return;
                    Object animator = child.getTag(nativeAnimatorTag), end = child.getTag(nativeEndTag);
                    if (!(animator instanceof android.animation.ObjectAnimator) || !(end instanceof Float)
                            || !Float.isFinite((Float) end)) return;
                    float target = x(object), current = child.getTranslationX();
                    if (!Float.isFinite(target) || !Float.isFinite(current) || target == (Float) end) return;
                    android.animation.ObjectAnimator running = (android.animation.ObjectAnimator) animator;
                    android.animation.PropertyValuesHolder[] holders = running.getValues();
                    Object first = child.getTag(nativeStartTag);
                    if (!(first instanceof Float) || !Float.isFinite((Float) first)
                            || running.getTarget() != child || !"translationX".equals(running.getPropertyName())
                            || holders == null || holders.length != 1 || holders[0] == null
                            || !"translationX".equals(holders[0].getPropertyName()) || !running.isStarted()
                            || !running.isRunning() || running.isPaused() || running.getRepeatCount() != 0
                            || running.getCurrentPlayTime() < 0 || running.getCurrentPlayTime() > running.getDuration()) return;
                    View parent = (View) child.getParent();
                    float currentDistance = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                            ? current : parent.getWidth() - current - child.getWidth();
                    if (Float.isFinite(currentDistance)) nativeDistance.setFloat(object, currentDistance);
                } catch (ReflectiveOperationException | RuntimeException unsupportedRetarget) { }
            }
            public boolean canRebase(Object object, View child, float dx, float parentWidthDelta, float childWidthDelta) {
                return frameRebase(object, child, dx, parentWidthDelta, childWidthDelta, false);
            }
            public boolean rebase(Object object, View child, float dx, float parentWidthDelta, float childWidthDelta) {
                return frameRebase(object, child, dx, parentWidthDelta, childWidthDelta, true);
            }
            private boolean frameRebase(Object object, View child, float dx, float parentWidthDelta, float childWidthDelta, boolean commit) {
                if (dx == 0f && parentWidthDelta == 0f && childWidthDelta == 0f) return true;
                if (rebasing || nativeShift == null || child == null || !state.isInstance(object)
                        || !container.isInstance(child.getParent()) || !Float.isFinite(dx)
                        || !Float.isFinite(parentWidthDelta) || !Float.isFinite(childWidthDelta)) return false;
                try {
                    if (nativeAnimateX.getBoolean(nativeFilter) || state(child) != object) return false;
                    float oldX = ((Number) getX.invoke(object)).floatValue(), nextX = oldX + dx;
                    float current = child.getTranslationX(), nativeTarget = current + dx;
                    float oldDistance = nativeDistance.getFloat(object);
                    // justAdded owns the factory's -1 sentinel. Once native
                    // apply has cleared that flag, a negative end distance can
                    // be a legitimate signed layout value and still rebases.
                    boolean distanceKnown = !nativeAdded.getBoolean(object);
                    boolean rtl = ((View) child.getParent()).getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    float nextDistance = oldDistance + (rtl ? dx : parentWidthDelta - dx - childWidthDelta);
                    if (!Float.isFinite(oldX) || !Float.isFinite(nextX) || !Float.isFinite(current)
                            || !Float.isFinite(nativeTarget) || distanceKnown && !Float.isFinite(nextDistance)) return false;
                    Object animator = child.getTag(nativeAnimatorTag);
                    if (dx != 0f && animator != null) {
                        if (!(animator instanceof android.animation.ObjectAnimator)) return false;
                        android.animation.ObjectAnimator running = (android.animation.ObjectAnimator) animator;
                        Object first = child.getTag(nativeStartTag), last = child.getTag(nativeEndTag);
                        android.animation.PropertyValuesHolder[] holders = running.getValues();
                        long playTime = running.getCurrentPlayTime(), duration = running.getDuration();
                        if (!(first instanceof Float) || !(last instanceof Float) || !Float.isFinite((Float) first)
                                || !Float.isFinite((Float) last) || running.getTarget() != child
                                || !"translationX".equals(running.getPropertyName()) || holders == null || holders.length != 1
                                || holders[0] == null || !"translationX".equals(holders[0].getPropertyName())
                                || !running.isStarted() || !running.isRunning() || running.isPaused() || running.getRepeatCount() != 0
                                || duration < 0 || playTime < 0 || playTime > duration) return false;
                        nativeTarget = (Float) last + dx;
                        if (!Float.isFinite(nativeTarget) || !Float.isFinite((Float) first + dx)) return false;
                    } else if (dx != 0f && (child.getTag(nativeStartTag) != null || child.getTag(nativeEndTag) != null)) {
                        return false;
                    }
                    if (!commit) return true;
                    boolean complete = false, temporaryX = false, distanceChanged = false;
                    float target = nativeTarget;
                    rebasing = true;
                    try {
                        if (distanceKnown) { nativeDistance.setFloat(object, nextDistance); distanceChanged = true; }
                        if (dx != 0f) {
                            setX.invoke(object, target); temporaryX = true;
                            // The exact native no-new-animation branch translates
                            // holder/start/end and seeks the SAME play time. It
                            // owns current X; no direct View/animator writes here.
                            nativeShift.invoke(object, child, nativeNoNew);
                            if (child.getTag(nativeAnimatorTag) != animator) return false;
                        }
                        complete = true; return true;
                    } finally {
                        try {
                            // Preserve a new target emitted by a reentrant native
                            // update rather than overwriting it with our snapshot.
                            if (temporaryX && ((Number) getX.invoke(object)).floatValue() == target)
                                setX.invoke(object, complete ? nextX : oldX);
                            if (!complete && distanceChanged && nativeDistance.getFloat(object) == nextDistance)
                                nativeDistance.setFloat(object, oldDistance);
                        } finally { rebasing = false; }
                    }
                } catch (ReflectiveOperationException | RuntimeException unavailableRebase) { return false; }
            }
        };
    }
    /** The same production algorithm is exercised with native-state fixtures. */
    public void setNativeAccess(NativeAccess value) { access = value; }
    public void setHintRetention(java.util.function.Predicate<View> value){hintRetention=value;}
    public void setSharedSlots(NetworkSharedSlotLayout value) { sharedSlots = value; }
    public boolean wanted() { return !released && settings.enabled; }
    public boolean internal() { return internal; }
    public void configure(Bundle bundle) {
        StatusBarLayoutSettings.Snapshot next = StatusBarLayoutSettings.Snapshot.parse(bundle);
        Bundle nextValues=bundle==null?new Bundle():new Bundle(bundle);FeatureOptions nextFeatures=FeatureOptions.from(nextValues);
        boolean same=settings.sameGeometry(next)&&sameSizing(values,features,nextValues,nextFeatures);
        if(settings.wifiLabelShared!=next.wifiLabelShared||!sameNetworkSizing(values,features,nextValues,nextFeatures))sharedWidths.clear();
        for(Scene scene:scenes.values())if(scene.invalid||scene.nativePass||scene.rebuildRequested){same=false;break;}
        values=nextValues;settings=next;features=nextFeatures;
        // Settings refreshes also include unrelated color/plugin changes. Releasing a
        // valid placement here exposes native positions before the next traversal.
        if(same)return;
        revision++;
        nativeMeasureRetries.clear();
        for (Scene scene : new ArrayList<>(scenes.values())) {
            restore(scene);
            restoreNativeTransaction(scene);
        }
        for (ViewGroup root : new ArrayList<>(roots.keySet())) if (root != null) root.requestLayout();
        if (!wanted()) { scenes.clear(); releaseParentAnimations(); cancelNativeMeasurePosts(); cancelCapsulePosts(); capsuleRoots.clear(); capsuleRanges.clear(); capsuleReportedRanges.clear(); nativeUnmeasured.clear(); nativeMeasureEligibility.clear(); nativeMeasureAttempts.clear(); nativeMeasureOwners.clear(); }
    }
    private static boolean sameSizing(Bundle first,FeatureOptions a,Bundle second,FeatureOptions b){
        if(!sameNetworkSizing(first,a,second,b))return false;
        for(String token:StatusBarLayoutSettings.TOKENS){String group=StatusBarLayoutSettings.MOBILE.equals(token)?"data":StatusBarLayoutSettings.NOTIFICATION.equals(token)?"notification_icons":token;
            if(a.size(group)!=b.size(group)||!java.util.Objects.equals(first.get(group+"_scale"),second.get(group+"_scale")))return false;}
        if(a.enabled("wifi")!=b.enabled("wifi")||a.enabled("label")!=b.enabled("label")||!NetworkDisplaySettings.mode(first).equals(NetworkDisplaySettings.mode(second)))return false;
        for(String key:new String[]{StatusBarSettings.DATA_ICON_SCALE,StatusBarSettings.BATTERY_WIDTH_SCALE,NativeStatusIcons.MASTER,NativeStatusIcons.SPACING})
            if(!java.util.Objects.equals(first.get(key),second.get(key)))return false;
        return true;
    }
    /** A hidden alternative cannot be remeasured yet. Drop its old width when
     * its typography/size changes; color and ordinary radio transitions retain it. */
    private static boolean sameNetworkSizing(Bundle first,FeatureOptions a,Bundle second,FeatureOptions b){
        if(a.enabled("wifi")!=b.enabled("wifi")||a.enabled("label")!=b.enabled("label")
                ||a.size("wifi")!=b.size("wifi")||a.size("label")!=b.size("label")
                ||a.textStyle("label")!=b.textStyle("label")
                ||!NetworkDisplaySettings.mode(first).equals(NetworkDisplaySettings.mode(second)))return false;
        if(a.size("wifi")&&!sameNumber(first,second,StatusBarSettings.WIFI_ICON_SCALE,100f)
                ||a.size("label")&&!sameNumber(first,second,StatusBarSettings.LABEL_SCALE,100f))return false;
        if(a.textStyle("label")&&!sameNumber(first,second,StatusBarSettings.FONT_WEIGHT,600f))return false;
        if(!a.enabled("label"))return true;
        if(a.enabled("font")!=b.enabled("font"))return false;
        if(a.enabled("font"))for(String key:new String[]{StatusBarSettings.FONT_MODE,StatusBarSettings.FONT_REVISION,FontScopes.KEY})
            if(!java.util.Objects.equals(first.get(key),second.get(key)))return false;
        return true;
    }
    private static boolean sameNumber(Bundle first,Bundle second,String key,float fallback){
        return Float.compare(NumericPolicy.finite(first.get(key),fallback),NumericPolicy.finite(second.get(key),fallback))==0;
    }
    /** Remember native roots before the optional geometry hooks are first requested. */
    public void observed(ViewGroup root) {
        if (!released && root(root)) roots.put(root, true);
    }
    /** Settings may arrive after a Compose AndroidView has already inflated its native root. */
    public void observedAnchor(View anchor) {
        View current = anchor;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (root(current)) { observed((ViewGroup) current); return; }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
    }
    /** Fixed structural diagnostics only: no IDs, notification contents or user strings. */
    public String summary() {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Scene scene : new ArrayList<>(scenes.values()))
            reasons.put(scene.reason, reasons.containsKey(scene.reason) ? reasons.get(scene.reason) + 1 : 1);
        return "enabled=" + wanted() + ", knownroots=" + roots.size() + ", scenes=" + scenes.size()
                + ", measureCallbacks=" + measureCallbacks + ", layoutCallbacks=" + layoutCallbacks + ", reasons=" + reasons;
    }
    public static boolean root(View view) {
        if (!(view instanceof ViewGroup)) return false;
        Class<?> type = view.getClass();
        for (int depth = 0; type != null && depth < 8; depth++, type = type.getSuperclass()) {
        String name = type.getName();
        if (name.equals("com.android.systemui.statusbar.phone.PhoneStatusBarView")
                || name.equals("com.android.systemui.statusbar.phone.KeyguardStatusBarView")
                || name.equals("com.oplus.systemui.statusbar.widget.FixedKeyguardStatusBarView")
                || name.equals("com.oplus.systemui.qs.OplusQuickStatusBarHeader")) return true;
        }
        return false;
    }
    public void beforeNativeLayout(ViewGroup root) {
        if (internal) return;
        layoutCallbacks++;
        observed(root);
        Scene scene = scenes.get(root);
        // The first root layout is also a transaction. Otherwise its native
        // add/alpha lifecycle runs once in onLayout and again at final commit.
        if (scene == null && wanted() && access != null && root(root)) { scene = new Scene(root, settings); scenes.put(root, scene); }
        if (scene != null) { restore(scene, false); scene.nativeLayout = true; scene.nativePass = true; scene.nativeCallback = true; }
    }
    public void beforeNativeMeasure(ViewGroup root) {
        if (internal) return;
        measureCallbacks++;
        observed(root);
        Scene scene = scenes.get(root); if (scene != null) {
            // A visibility/wake traversal can stop after measure. Its already-laid-out
            // row must finish the same target transaction without waiting for onLayout.
            scene.measureCommit = !scene.invalid && scene.plan != null && root.getWidth() > 0;
            restore(scene, false); scene.nativeLayout = false; scene.nativePass = true; scene.nativeCallback = true;
        }
    }
    /** Only the real root measure call may replace native content constraints.
     * Compose-only remeasurement can still carry our last compact hull size. */
    NetworkSharedSlotLayout beginNativeContentMeasure(ViewGroup root) {
        NetworkSharedSlotLayout bridge = sharedSlots;
        if (bridge != null) bridge.beginNativeMeasure(root);
        return bridge;
    }
    public void detached(ViewGroup root) {
        Runnable measure = nativeMeasurePosts.remove(root); if (measure != null) root.removeCallbacks(measure);
        nativeMeasureEligibility.keySet().removeIf(view -> nativeRoot(view) == root);
        nativeMeasureAttempts.keySet().removeIf(view -> nativeRoot(view) == root);
        nativeMeasureOwners.entrySet().removeIf(entry -> entry.getValue().root.get() == root);
        nativeUnmeasured.keySet().removeIf(view -> nativeRoot(view) == root);
        capsuleRoots.remove(root); capsuleTargets.remove(root); capsuleRanges.remove(root); capsuleReportedRanges.remove(root);
        fullRadios.remove(root);
        Runnable pendingCapsule = capsuleSwitches.remove(root);
        if (pendingCapsule != null) root.removeCallbacks(pendingCapsule); Scene scene = scenes.remove(root); if (scene != null) { restore(scene); restoreNativeTransaction(scene); } sharedWidths.remove(root); nativeMeasureRetries.remove(root); wifiNeighbors.remove(root); iconFrames.entrySet().removeIf(entry -> entry.getValue().root.get() == root); }

    /** Native measurement remains authoritative. A wider outer budget is not a
     * reason to remeasure every StatusIconContainer at full screen width. */
    public void afterMeasure(ViewGroup root) {
        Scene scene = scenes.get(root);
        // A measured root need not receive onLayout (for example while shade
        // visibility changes). Pending geometry is not an active native call.
        if (scene != null && !scene.nativeLayout) scene.nativeCallback = false;
        if (!wanted() || internal || !root(root) || access == null || root.getMeasuredWidth() <= 0) return;
        observed(root);
        if (sharedSlots != null) sharedSlots.nativeMeasured(root);
        if (scene != null && scene.measureCommit) afterLayout(root);
    }
    /** The hook finally owns this scope even on zero-size, nested or failed calls. */
    public void endNativeCallback(ViewGroup root, boolean active) {
        Scene scene = scenes.get(root);
        if (scene != null) scene.nativeCallback = active;
    }
    /** Native geometry callback, never a draw/preDraw observer. */
    public void afterLayout(ViewGroup root) {
        if (!internal) endNativeCallback(root, false);
        if (!internal) traceVertical(root);
        if (!wanted() || internal || !root(root) || access == null || root.getWidth() <= 0) {
            if (!internal) restoreNativeTransaction(scenes.get(root));
            return;
        }
        observed(root);
        if (sharedSlots != null) sharedSlots.nativeLaidOut(root);
        if (!access.geometrySupported()) {
            Scene unavailable = scenes.get(root);
            if (unavailable == null) { unavailable = new Scene(root, settings); scenes.put(root, unavailable); }
            restoreNativeTransaction(unavailable);
            unavailable.invalid = true; unavailable.reason = "native_animation_unavailable";
            return;
        }
        Scene previous = scenes.get(root);
        Scene scene = new Scene(root, settings); scenes.put(root, scene);
        internal = true;
        try {
            collect(scene, root, 0f, 0f, 0, new int[]{0});
            trace(scene, null, "layout_native");
            if (scene.endpoints.isEmpty() || scene.invalid) {
                restoreNativeTransaction(previous); scene.invalid = true; scene.reason = "unsupported_structure"; return;
            }
            // A binder may add a child after the native traversal. Relaying even
            // a horizontal ancestor layout would consume that child's pending
            // first layout while its measured size is still 0x0. Android's
            // already-requested traversal must supply the real geometry first.
            List<View> pending = new ArrayList<>();
            if (pendingNativeMeasure(scene, pending)) {
                boolean retry = false;
                for (View view : pending) if (nativeMeasureAttempts.getOrDefault(view, 0) < NATIVE_MEASURE_RETRIES) { retry = true; break; }
                if (retry) {
                    restoreNativeTransaction(previous); scene.invalid = true; scene.reason = "pending_native_measure";
                    requestNativeMeasure(root, pending); return;
                }
                // Every forced traversal re-measured the whole chain and these
                // leaves are still 0x0: their OEM parent deliberately skips them.
                // Waiting forever would keep margins and order off for good.
                for (View view : pending) nativeUnmeasured.put(view, Boolean.TRUE);
                ModuleDiagnostics.fact("status_hints", "scene", "状态栏相对布局", "backend", "native_flow",
                        "reason", "native_unmeasured_skipped", "count", pending.size());
            }
            nativeMeasureRetries.remove(root);
            if (normalizeReleasedSharedHulls(previous)) {
                // Compose can finish its inner native fragment measure without
                // remeasuring the Android host. Capture the corrected natural
                // host before that obsolete EXACT hull becomes a new baseline.
                scene = new Scene(root, settings); scenes.put(root, scene);
                collect(scene, root, 0f, 0f, 0, new int[]{0});
                if (scene.endpoints.isEmpty() || scene.invalid) {
                    restoreNativeTransaction(previous); scene.invalid = true; scene.reason = "unsupported_structure"; return;
                }
            }
            // A native child update normally reaches beforeStates. Also cover a
            // root-only layout where the icon container did not update its state.
            if (!syncIconFrames(scene, null, false, false)) { scene.invalid = true; scene.reason = "native_animation_unavailable"; restoreNativeTransaction(previous); return; }
            if (previous != null) for (Endpoint endpoint : scene.endpoints) {
                NativeTarget nativeTarget = previous.nativeTargets.get(endpoint.view.get());
                if (nativeTarget != null && nativeTarget.state.get() == endpoint.state.get()) {
                    endpoint.nativeX = nativeTarget.x;
                    endpoint.residualX = access.x(endpoint.state.get()) - nativeTarget.x;
                    endpoint.coordinatorHidden = nativeTarget.visible != access.visible(endpoint.state.get())
                            && access.visible(endpoint.state.get()) == 2;
                    endpoint.sortX = endpoint.box.x + nativeTarget.x;
                    endpoint.baselineKnown = true;
                } else {
                    // A root can lay out without another icon calculation. A
                    // hide observed after our last local projection still belongs
                    // to Seedling until that coordinator actually recalculates it.
                    for (Endpoint old : previous.endpoints) if (old.view.get() == endpoint.view.get()
                            && old.state.get() == endpoint.state.get()) {
                        endpoint.coordinatorHidden = old.coordinatorHidden && access.visible(endpoint.state.get()) == 2;
                        break;
                    }
                }
            }
            if (previous != null && previous.nativeLayout) for (Endpoint next : scene.endpoints) {
                for (Endpoint old : previous.endpoints) {
                    if (old.stateOwned && old.view.get() == next.view.get() && old.state.get() == next.state.get()) {
                        next.residualX = access.x(next.state.get()) - old.appliedX;
                        next.nativeX = old.nativeX; next.nativeVisible = old.nativeVisible;
                        next.baselineKnown = true; break;
                    }
                }
            }
            splitSharedSlots(scene);
            plan(scene);
            boolean sharedFallback = false;
            if (!applySharedSlots(scene)) {
                restore(scene);
                // A mobile backend must not disable unrelated clock/speed gaps.
                // Retry with the complete native mobile child and no fragments.
                scene = new Scene(root, settings); scenes.put(root, scene);
                collect(scene, root, 0f, 0f, 0, new int[]{0});
                if (scene.invalid || scene.endpoints.isEmpty()) { restoreNativeTransaction(previous); scene.invalid = true; scene.reason = "unsupported_structure"; return; }
                plan(scene); sharedFallback = true;
            }
            applyGeometry(scene);
            if (!scene.invalid && !syncIconFrames(scene, null, true, true)) {
                restore(scene); scene.invalid = true; scene.reason = "native_animation_unavailable"; restoreNativeTransaction(previous); return;
            }
            if (!scene.invalid) holdParentAnimations(scene);
            publish(scene, null);
            // Native occlusion reads the row's actual screen bounds and icon targets.
            // Run it only after those two coordinate systems agree, before native apply.
            coordinateNative(previous);
            // A later root-only layout may not run the row's coordinator again.
            // Retain its current hide just as beforeApply does for local updates.
            for(Endpoint endpoint:scene.endpoints)if(endpoint.icon&&endpoint.visible&&endpoint.stateOwned&&endpoint.appliedVisible!=2){
                Object state=endpoint.state.get();if(state!=null)endpoint.coordinatorHidden=access.visible(state)==2;
            }
            trace(scene, null, "layout_flow");
            if (sharedFallback && !scene.invalid) scene.reason = "shared_layout_unavailable";
            if (!scene.invalid) for (Endpoint endpoint : scene.endpoints) {
                View child = endpoint.view.get(); Object state = endpoint.state.get();
                if (child != null && state != null) {
                    access.retargetFromCurrent(state, child); nativeHandoffs.remove(child);
                    // A planned visibleState does not authorize replaying the
                    // native alpha/add lifecycle. Full apply belongs only to an
                    // actual native invocation deferred by this root transaction.
                    if (previous != null && previous.deferredNative.containsKey(child))
                        applyNative(previous, state, child);
                    else { access.applyGeometry(state, child); access.applyVisibility(state, child); }
                }
            }
            applyUnplannedNative(previous, scene);
            captureInputs(scene);
            trace(scene, null, "layout_applied");
            traceLayout(scene);
        } catch (RuntimeException unavailable) {
            scene.invalid = true; scene.reason = "unsupported_structure"; restore(scene); restoreNativeTransaction(previous);
        } finally {
            internal = false;
            if (previous == null || !previous.reason.equals(scene.reason))
                ModuleDiagnostics.fact("status_hints", "scene", "状态栏相对布局", "backend", "native_flow",
                        "host", root.getClass().getName().contains("Keyguard") ? "keyguard"
                                : root.getClass().getName().contains("QuickStatus") ? "qs" : "phone",
                        "reason", scene.reason, "enabled", wanted(), "count", scene.endpoints.size(),
                        "applied", !scene.invalid && scene.plan != null);
        }
    }
    /** Release only a host proven to have belonged to our last shared hull.
     * A zero-width native label removes that hull even in an onLayout-only
     * callback. Unrelated native EXACT widths and visibility remain native. */
    private boolean normalizeReleasedSharedHulls(Scene previous) {
        if (previous == null || sharedSlots == null || !sharedSlots.enabled()) return false;
        boolean changed = false;
        for (Fragments fragments : previous.fragments) {
            View host = fragments.frame.host();
            if (host == null || host.getParent() != fragments.signal.box.parent.get()) continue;
            NetworkSharedSlotLayout.Frame frame = sharedSlots.frame(host);
            if (frame == null || !frame.valid || frame.labelPresent || frame.nativeHostWidthPx <= 0
                    || frame.nativeHostWidthPx > 65535 || frame.signalWidthPx <= 0) continue;
            int width = frame.nativeHostWidthPx;
            sharedSlots.restore(host);
            int height = host.getMeasuredHeight();
            if (height <= 0) continue;
            if (host.getMeasuredWidth() != width) {
                measureSharedHull(host, width, height);
                changed = true;
            }
            if (host.getWidth() != width) {
                host.layout(host.getLeft(), host.getTop(), host.getLeft() + width, host.getBottom()); changed = true;
            }
        }
        return changed;
    }
    private static String name(View view) {
        try { return view.getId() == View.NO_ID ? "" : view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException unknown) { return ""; }
    }
    private boolean pendingNativeMeasure(Scene scene) { return pendingNativeMeasure(scene, null); }
    /** With {@code found}, every pending leaf is collected instead of stopping at the first. */
    private boolean pendingNativeMeasure(Scene scene, List<View> found) {
        int[] visits = {0};
        for (Endpoint endpoint : scene.endpoints) {
            View view = endpoint.view.get();
            if (!endpoint.labelFragment && view != null && view.getParent() == endpoint.box.parent.get()
                    && pendingNativeMeasure(view, 0, visits, found) && found == null) return true;
        }
        // Recognized leaves and notification hosts with zero width or INVISIBLE
        // visibility have no endpoint. Repacking an ancestor must still leave
        // their real first measure/layout to Android, including at reload.
        for (Box box : scene.boxes.values()) {
            View view = box.view.get();
            if (view != null && view.getParent() == box.parent.get()
                    && (view.getMeasuredWidth() == 0 || view.getVisibility() == View.INVISIBLE)
                    && token(view) != null
                    && pendingNativeMeasure(view, 0, visits, found) && found == null) return true;
        }
        for (WeakReference<ViewGroup> parent : scene.containers) {
            View view = parent.get(); Box box = scene.boxes.get(view);
            if (view != null && box != null && view.getParent() == box.parent.get()
                    && pendingNativeMeasure(view, 0, visits, found) && found == null) return true;
        }
        return found != null && !found.isEmpty();
    }
    private boolean pendingNativeMeasure(View view, int depth, int[] visits, List<View> found) {
        // INVISIBLE children still participate in native measure/layout; only GONE opts out.
        if (view == null || view.getVisibility() == View.GONE || depth > 12 || ++visits[0] > 384) return false;
        if (nativeSizePending(view)) {
            nativeMeasureOwner(view, nativeRoot(view));
            if (nativeUnmeasured.containsKey(view)) return false;
            if (found != null && !found.contains(view)) found.add(view);
            return true;
        }
        nativeUnmeasured.remove(view);
        if (view.getMeasuredWidth() != 0 || view.getMeasuredHeight() != 0) { nativeMeasureAttempts.remove(view); nativeMeasureOwners.remove(view); }
        boolean any = false;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            // StatusIconContainer.onMeasure measures only visible, unblocked and
            // non-ignored icons. Any other child keeps 0x0 and its layout request
            // by design; waiting for it held the whole flow in pending forever.
            boolean icons = access != null && access.container(parent);
            for (int index = 0; index < parent.getChildCount(); index++) {
                View child = parent.getChildAt(index);
                if (icons && child != null && (!access.eligible(parent, child)||!retainedHint(child))) continue;
                if (!pendingNativeMeasure(child, depth + 1, visits, found)) continue;
                any = true; if (found == null) return true;
            }
        }
        return any;
    }
    /** Native StatusIconContainer lays out even the logical-hidden children it did not measure.
     * That 0x0 layout consumes FORCE_LAYOUT. Once eligible, such an icon still needs its first
     * real measure; a positive-height zero-width label remains a legitimate native placeholder. */
    private boolean nativeSizePending(View view) {
        if (view == null || view.getMeasuredWidth() != 0 || view.getMeasuredHeight() != 0) return false;
        ViewParent parent = view.getParent();
        if (access != null && parent instanceof ViewGroup && access.container((View) parent))
            return access.eligible((ViewGroup) parent, view)&&retainedHint(view);
        return view.isLayoutRequested() || fixedNativeContentPending(view);
    }
    /** Captured missing siblings: a visible battery image and notification glyph have
     * positive native LP widths even while their 0x80/0x120 wrappers report no width. */
    private static boolean fixedNativeContentPending(View view) {
        if (view == null || view instanceof ViewGroup || view.getVisibility() == View.GONE
                || view.getMeasuredWidth() != 0 || view.getMeasuredHeight() != 0) return false;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.width <= 0) return false;
        View current = view;
        for (int depth = 0; current != null && depth < 8; depth++) {
            String kind = token(current);
            if (StatusBarLayoutSettings.BATTERY.equals(kind) || StatusBarLayoutSettings.NOTIFICATION.equals(kind)) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }
    private void appendFixedNativeContent(View view, List<View> pending, int depth, int[] visits) {
        if (view == null || view.getVisibility() == View.GONE || depth > 12 || ++visits[0] > 384) return;
        if (fixedNativeContentPending(view)) {
            nativeMeasureOwner(view, nativeRoot(view));
            if (!nativeUnmeasured.containsKey(view) && !pending.contains(view)) pending.add(view);
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            appendFixedNativeContent(((ViewGroup) view).getChildAt(i), pending, depth + 1, visits);
    }
    private static ViewGroup nativeRoot(View view) {
        for (int depth = 0; view != null && depth < 16; depth++) {
            if (root(view)) return (ViewGroup) view;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return null;
    }
    private boolean canForceNativeSize(View view) {
        if (!nativeSizePending(view)) return false;
        ViewParent parent = view.getParent();
        // INVISIBLE still participates in ordinary native measure and pending protection.
        // Active recovery waits until this native content is actually shown, so the locked
        // phone row cannot spend its whole budget before fingerprint wake-and-unlock.
        if ((parent instanceof View && access != null && access.container((View) parent)) || fixedNativeContentPending(view))
            return visibleForNativeRecovery(view);
        return true;
    }
    private static boolean visibleForNativeRecovery(View view) {
        // isShown follows ancestor visibility, but does not include the window state.
        return view.isShown() && view.getWindowVisibility() == View.VISIBLE;
    }
    /** Only 0x0 candidates retain weak ownership; reparenting starts a new native
     * first-measure lifecycle without making measured icons allocate or reflect. */
    private void nativeMeasureOwner(View view, ViewGroup root) {
        NativeMeasureOwner previous = nativeMeasureOwners.get(view);
        ViewParent parent = view.getParent();
        if (previous != null && previous.parent.get() == parent && previous.root.get() == root) return;
        nativeMeasureOwners.put(view, new NativeMeasureOwner(parent, root));
        nativeUnmeasured.remove(view); nativeMeasureAttempts.remove(view); nativeMeasureEligibility.remove(view);
        nativeMeasureRetries.remove(root);
    }
    /** Device code158: after a SystemUI reload an earlier ancestor layout had
     * already consumed the FORCE_LAYOUT flags above freshly added leaves, so the
     * awaited native traversal skipped them and they stayed 0x0 forever. After
     * this traversal, mark the whole chain again and ask the window for one
     * real native measure. Posts deduplicate per root; each missing leaf has a
     * three-attempt budget until native success or a new native lifecycle. */
    private void requestNativeMeasure(ViewGroup root, List<View> pending) {
        if (!wanted() || pending.isEmpty() || nativeMeasurePosts.containsKey(root)) return;
        // A forced ancestor does not force cached siblings. Include only fixed-size
        // native content proven missing; empty carrier/network labels keep their 0xH.
        pending = new ArrayList<>(pending);
        appendFixedNativeContent(root, pending, 0, new int[]{0});
        List<View> candidates = new ArrayList<>();
        for (View view : pending) {
            nativeMeasureOwner(view, root);
            if (nativeMeasureAttempts.getOrDefault(view, 0) >= NATIVE_MEASURE_RETRIES) { nativeUnmeasured.put(view, Boolean.TRUE); continue; }
            if (canForceNativeSize(view)) candidates.add(view);
        }
        if (candidates.isEmpty()) return;
        Integer tries = nativeMeasureRetries.get(root);
        // A late child must not inherit an exhausted peer's budget. Each actual leaf
        // keeps its own three-attempt cap; the root counter describes the current batch.
        if (tries != null && tries >= NATIVE_MEASURE_RETRIES) tries = null;
        nativeMeasureRetries.put(root, tries == null ? 1 : tries + 1);
        List<WeakReference<View>> leaves = new ArrayList<>();
        for (View view : candidates) leaves.add(new WeakReference<>(view));
        Runnable request = new Runnable() { @Override public void run() {
            if (nativeMeasurePosts.get(root) != this) return;
            nativeMeasurePosts.remove(root);
            if (!wanted() || !root.isAttachedToWindow()) return;
            List<View> tops = new ArrayList<>();
            Set<View> forced = Collections.newSetFromMap(new IdentityHashMap<>());
            for (WeakReference<View> reference : leaves) {
                View view = reference.get();
                if (view == null || !view.isAttachedToWindow() || nativeRoot(view) != root || !canForceNativeSize(view)) continue;
                nativeMeasureAttempts.put(view, nativeMeasureAttempts.getOrDefault(view, 0) + 1);
                View top = view;
                for (int depth = 0; top != null && depth < 64; depth++) {
                    if (forced.add(top)) top.forceLayout();
                    ViewParent parent = top.getParent();
                    if (!(parent instanceof View)) break;
                    top = (View) parent;
                }
                if (top != null && !tops.contains(top)) tops.add(top);
            }
            // The window root's parent is ViewRootImpl: this schedules a traversal.
            for (View top : tops) top.requestLayout();
        } };
        nativeMeasurePosts.put(root, request); root.post(request);
    }
    private void cancelNativeMeasurePosts() {
        for (Map.Entry<ViewGroup, Runnable> entry : new ArrayList<>(nativeMeasurePosts.entrySet()))
            if (entry.getKey() != null) entry.getKey().removeCallbacks(entry.getValue());
        nativeMeasurePosts.clear();
    }
    private void holdParentAnimations(Scene scene) {
        for (Box box : new ArrayList<>(scene.boxes.values())) {
            View view = box.view.get();
            if (!(view instanceof ViewGroup)) continue;
            android.animation.LayoutTransition transition = ((ViewGroup) view).getLayoutTransition();
            if (transition == null || heldParentAnimations.containsKey(transition)) continue;
            heldParentAnimations.put(transition, nativeParentAnimation(transition));
            transition.setAnimateParentHierarchy(false);
        }
    }
    private void releaseParentAnimations() {
        for (Map.Entry<android.animation.LayoutTransition, Boolean> held : new ArrayList<>(heldParentAnimations.entrySet()))
            if (held.getKey() != null) held.getKey().setAnimateParentHierarchy(held.getValue());
        heldParentAnimations.clear();
    }
    private static Field parentHierarchy;
    /** The native value (true unless the OEM turned it off); the platform has no public getter. */
    private static boolean nativeParentAnimation(android.animation.LayoutTransition transition) {
        try {
            if (parentHierarchy == null) {
                parentHierarchy = android.animation.LayoutTransition.class.getDeclaredField("mAnimateParentHierarchy");
                parentHierarchy.setAccessible(true);
            }
            return parentHierarchy.getBoolean(transition);
        } catch (ReflectiveOperationException | RuntimeException unknown) { return true; }
    }
    private static boolean structural(View view) {
        String id = name(view);
        if (view.isClickable() || view.hasOnClickListeners() || view.isFocusable() && !id.equals("quick_qs_status_icons")) return false;
        return id.equals("status_bar_contents") || id.equals("status_bar_start_side_container")
                || id.equals("status_bar_end_side_container") || id.equals("status_bar_left_side")
                || id.equals("status_bar_start_side_content") || id.equals("status_bar_start_side_content_for_fake")
                || id.equals("status_bar_start_side_except_heads_up") || id.equals("status_bar_end_side_content")
                || id.equals("status_bar_end_side_container_for_fake") || id.equals("system_icons_container")
                || id.equals("keyguard_status_bar_contents") || id.equals("status_bar_content_container")
                || id.equals("quick_qs_status_icons") || id.equals("icons") || id.equals("qs_clock_container")
                || id.equals("system_icon_area") || id.equals("system_icons") || id.equals("status_icon_area")
                || id.equals("clock_for_fake") || id.equals("notification_icon_area")
                || id.equals("notification_icon_area_inner") || id.equals("carrier_label_container")
                || id.equals("statusIcons") || id.equals("status_icons") || id.equals("status_bar")
                || id.equals("keyguard_status_bar") || id.equals("qs_status_icons_container")
                || id.isEmpty() && view.getClass().getName().equals("android.widget.RelativeLayout")
                    && view.getParent() instanceof View && name((View) view.getParent()).equals("keyguard_status_bar_contents")
                // Real C17 Phone hierarchy: one anonymous OEM notification wrapper.
                // Do not open arbitrary anonymous layouts or other parents; the interaction
                // guard above and normal unknown-child validation remain authoritative.
                || view.getId() == View.NO_ID && view.getClass().getName().equals("com.android.keyguard.AlphaOptimizedLinearLayout")
                    && view.getParent() instanceof View && name((View) view.getParent()).equals("notification_icon_area");
    }
    private static ViewGroup content(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i); String id = child == null ? "" : name(child);
            if (child instanceof ViewGroup && (id.equals("status_bar_contents") || id.equals("status_bar_content_container")
                    || id.equals("quick_qs_status_icons"))) return (ViewGroup) child;
        }
        return null;
    }
    /** Empty OEM shells reserve measured space even with no currently displayed contents. */
    private boolean inactiveShell(View view) {
        String id = name(view), type = view.getClass().getName();
        boolean known = id.equals("dynamic_icon_group") || id.equals("privacy_chip_window")
                || id.equals("prompt_view_group") && type.equals("com.oplus.systemui.statusbar.widget.HiddenReceiverFrameLayout");
        if (type.equals("com.android.systemui.privacy.OngoingPrivacyChip")) {
            try {
                if (privacyList == null) privacyList = view.getClass().getMethod("getPrivacyList");
                Object items = privacyList.invoke(view);
                if (!(items instanceof List) || !((List<?>) items).isEmpty()) return false;
                known = true;
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
        return known && emptyContents(view, 0, new int[]{0});
    }
    private static boolean emptyContents(View view, int depth, int[] visits) {
        if (depth > 8 || ++visits[0] > 128) return false;
        if (view.getVisibility() != View.VISIBLE) return true;
        // Alpha zero is still an active animation. Never use alpha to hide an obstacle.
        if (view.isClickable() || view.hasOnClickListeners() || view.isFocusable()
                || view.getBackground() != null || view.getForeground() != null) return false;
        if (!(view instanceof ViewGroup)) return Math.max(view.getWidth(), view.getMeasuredWidth()) == 0;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (!emptyContents(group.getChildAt(i), depth + 1, visits)) return false;
        return true;
    }
    private static boolean qsRow(View view) { return view != null && name(view).equals("quick_qs_status_icons"); }
    private static boolean sameRow(View first, View second) {
        return first.getTop() < second.getBottom() && second.getTop() < first.getBottom();
    }
    private static String token(View view) {
        String id = name(view), type = view.getClass().getName();
        if (id.equals("clock") || id.equals("keyguard_status_bar_clock") || type.equals("com.oplus.systemui.statusbar.widget.StatClock")) return StatusBarLayoutSettings.CLOCK;
        if (id.equals("battery") || type.equals("com.oplus.systemui.statusbar.widget.OplusBatteryMeterView")
                || type.equals("com.android.systemui.battery.BatteryMeterView")
                || type.equals("com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView")) return StatusBarLayoutSettings.BATTERY;
        if (id.equals("notificationIcons") || id.equals("notification_icons") || id.equals("notification_icon_area_inner")) return StatusBarLayoutSettings.NOTIFICATION;
        if (id.equals("carrier_label") || id.equals("operator_name") || id.equals("keyguard_carrier_text") || id.equals("qs_carrier_text")) return StatusBarLayoutSettings.CARRIER;
        return null;
    }
    private String slotToken(View view) {
        String slot = access.slot(view); if (slot == null) return null;
        if (slot.equals("wifi") || slot.startsWith("wifi_")) return StatusBarLayoutSettings.WIFI;
        if (slot.equals("mobile") || slot.startsWith("mobile_") || slot.equals("stacked_mobile") || slot.startsWith("stacked_mobile_")) return StatusBarLayoutSettings.MOBILE;
        if (slot.equals("network_speed") || slot.equals("net_speed") || slot.equals("network_speed_blacklist")
                || view.getClass().getName().endsWith(".NetworkSpeedView")) return StatusBarLayoutSettings.SPEED;
        return StatusBarLayoutSettings.SYSTEM;
    }
    private void collect(Scene scene, View view, float parentX, float parentY, int depth, int[] visits) {
        if (depth > 12 || ++visits[0] > 384) { scene.invalid = true; return; }
        if (view != scene.root.get() && inactiveShell(view)) return;
        Box box = new Box(view, parentX, parentY, depth);
        if (view == scene.root.get()) { box.desiredX = 0f; box.originX = 0f; box.originY = 0f; }
        scene.boxes.put(view, box);
        if (view == scene.root.get()) {
            ViewGroup content = content((ViewGroup) view);
            if (content != null) {
                ViewGroup root = (ViewGroup) view;
                for (int i = 0; i < root.getChildCount(); i++) {
                    View sibling = root.getChildAt(i); if (sibling == content || sibling == null || sibling.getVisibility() != View.VISIBLE) continue;
                    if (inactiveShell(sibling)) continue;
                    if (qsRow(content)) {
                        // The carrier occupies the same header row; dates/buttons on other rows stay native.
                        if (name(sibling).equals("qs_clock_container") && sameRow(sibling, content)) {
                            collect(scene, sibling, 0f, 0f, depth + 1, visits); continue;
                        }
                        if (!sameRow(sibling, content)) continue;
                    }
                    String id = name(sibling), type = sibling.getClass().getName();
                    if (id.equals("cutout_space_view") || id.equals("cutout_space")) {
                        scene.boxes.put(sibling, new Box(sibling, 0f, 0f, depth + 1)); continue;
                    }
                    // This exact native overlay has its own Seedling target coordinator.
                    // It is never repacked or widened. All other active siblings fail closed.
                    if (type.equals("com.oplus.systemui.statusbar.seeding.CapsulePluginContainer")
                            || type.equals("com.oplus.systemui.statusbar.seeding.KeyguardStatusBarCapsuleContainer")) continue;
                    if (Math.max(sibling.getWidth(), sibling.getMeasuredWidth()) > 0) scene.invalid = true;
                }
                collect(scene, content, 0f, 0f, depth + 1, visits); return;
            }
        }
        if (view != scene.root.get() && view.getVisibility() == View.GONE) return;
        String token = view == scene.root.get() ? null : token(view);
        if (token != null) { if (view.getVisibility() == View.VISIBLE) addEndpoint(scene, view, box, token, false); return; }
        if (!(view instanceof ViewGroup)) {
            String id = name(view);
            if (view.getVisibility() == View.VISIBLE && Math.max(box.width, view.getMeasuredWidth()) > 0f
                    && !id.equals("cutout_space_view") && !id.equals("cutout_space")) scene.invalid = true;
            return;
        }
        ViewGroup group = (ViewGroup) view;
        if (access.container(view)) {
            scene.containers.add(new WeakReference<>(group));
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i); if (child == null) continue;
                Box childBox = new Box(child, box.originX, box.originY, depth + 1); scene.boxes.put(child, childBox);
                if (!access.eligible(group, child)) continue;
                Object state = access.state(child); String kind = slotToken(child);
                if (state == null || kind == null || !Float.isFinite(access.x(state))) { scene.invalid = true; return; }
                String hintSlot=scene.settings.hintLayoutActive()&&StatusBarLayoutSettings.SYSTEM.equals(kind)&&access.hint(child)?access.slot(child):null;
                if(hintSlot!=null&&hintRetention!=null&&!hintRetention.test(child))continue;
                addEndpoint(scene, child, childBox, kind, true,hintSlot);
            }
        } else {
            // Unknown subtree is an obstacle; never recursively take over panel content.
            if (view != scene.root.get() && !structural(view)) {
                if (view.getVisibility() == View.VISIBLE && Math.max(box.width, view.getMeasuredWidth()) > 0f && !name(view).equals("cutout_space_view")
                        && !name(view).equals("cutout_space")) scene.invalid = true;
                return;
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i); if (child != null) collect(scene, child, box.originX, box.originY, depth + 1, visits);
            }
        }
    }
    private void addEndpoint(Scene scene, View view, Box box, String token, boolean icon) {
        addEndpoint(scene,view,box,token,icon,null);
    }
    private void addEndpoint(Scene scene,View view,Box box,String token,boolean icon,String hintSlot) {
        float width = view.getMeasuredWidth(); if (width <= 0f) return;
        Object state = icon ? access.state(view) : null;
        float target = icon ? access.x(state) : 0f;
        Endpoint endpoint = new Endpoint(view, token, box, state, target, icon ? access.visible(state) : -1);
        endpoint.hintSlot=hintSlot;endpoint.hintPlacement=hintSlot!=null&&scene.settings.hintSpecified(hintSlot);
        endpoint.hintOrder=hintSlot==null?0:scene.settings.hintOrder(hintSlot);
        endpoint.hintCustom=endpoint.hintPlacement&&(endpoint.hintOrder!=0
                ||!StatusBarLayoutSettings.NATIVE.equals(scene.settings.hintSide(hintSlot)));
        // Capture the native target before changing its coordinate frame. The
        // rebased local X is not the native-layout value used when disabling.
        endpoint.baselineKnown = icon;
        String group = token.equals(StatusBarLayoutSettings.MOBILE) ? "data" : token.equals(StatusBarLayoutSettings.NOTIFICATION) ? "notification_icons" : token;
        float scale = features.size(group) ? NumericPolicy.finite(values.get(group + "_scale"), 100f) / 100f : 1f;
        if (token.equals(StatusBarLayoutSettings.WIFI)) scale = features.size("wifi") ? NumericPolicy.finite(values.get(StatusBarSettings.WIFI_ICON_SCALE), 100f) / 100f : 1f;
        if (token.equals(StatusBarLayoutSettings.MOBILE)) scale = features.size("data") ? NumericPolicy.finite(values.get(StatusBarSettings.DATA_ICON_SCALE), 100f) / 100f : 1f;
        if (token.equals(StatusBarLayoutSettings.BATTERY) && features.size("battery")) scale *= NumericPolicy.finite(values.get(StatusBarSettings.BATTERY_WIDTH_SCALE), 100f) / 100f;
        // Text clocks have already been measured at their styled size.
        if (token.equals(StatusBarLayoutSettings.CLOCK) || token.equals(StatusBarLayoutSettings.CARRIER)) scale = 1f;
        scale = Float.isFinite(scale) && scale > 0f ? scale : 1f;
        endpoint.width = Math.min(1_000_000f, Math.max(width, width * scale));
        endpoint.paintInset = (endpoint.width - width) * .5f;
        scene.endpoints.add(endpoint);
    }
    /** One logical Wi-Fi slot has either a native Wi-Fi child or the original
     * mobile label fragment. Signal bars retain their own separate budget. */
    private void splitSharedSlots(Scene scene) {
        if (!scene.settings.wifiLabelShared || sharedSlots == null || !sharedSlots.enabled()) return;
        ViewGroup root = scene.root.get();
        if (root == null) return;
        Endpoint wifi = null, candidate = null;
        NetworkSharedSlotLayout.Frame frame = null;
        for (Endpoint endpoint : scene.endpoints) {
            if (StatusBarLayoutSettings.WIFI.equals(endpoint.token)) wifi = endpoint;
            if (StatusBarLayoutSettings.MOBILE.equals(endpoint.token)) {
                NetworkSharedSlotLayout.Frame next = sharedSlots.frame(endpoint.view.get());
                if (next != null && next.valid && next.labelPresent && next.labelWidthPx > 0) {
                    // An ambiguous pair of native labels is never assigned to the
                    // same slot, nor is a simultaneously visible Wi-Fi hidden.
                    if (candidate != null) return;
                    candidate = endpoint; frame = next;
                }
            }
        }
        String configuration=sharedWidthKey(root);
        SharedWidths measured=sharedWidths.get(root);
        if(measured==null||!measured.configuration.equals(configuration)){measured=new SharedWidths(configuration);sharedWidths.put(root,measured);}
        if(wifi!=null)measured.wifi=Math.round(wifi.width);
        if(frame!=null)measured.label=frame.labelWidthPx;
        int width=measured.width();
        if (wifi != null) {
            // The native Wi-Fi node owns this slot again. A retained data hull
            // must not constrain the next Compose/ConstraintLayout measurement.
            for (Endpoint endpoint : scene.endpoints) if (StatusBarLayoutSettings.MOBILE.equals(endpoint.token))
                sharedSlots.restore(endpoint.view.get());
            List<Endpoint> nativeOrder = new ArrayList<>(scene.endpoints);
            nativeOrder.sort(Comparator.comparingDouble(endpoint -> endpoint.sortX));
            int index = nativeOrder.indexOf(wifi);
            wifiNeighbors.put(root, new WifiNeighbors(index > 0 ? nativeOrder.get(index - 1).view.get() : null,
                    index + 1 < nativeOrder.size() ? nativeOrder.get(index + 1).view.get() : null));
            float extra = Math.max(0f, width - wifi.width);
            wifi.width += extra; wifi.paintInset += extra * .5f;
            return;
        }
        if (candidate == null || frame == null || frame.signalWidthPx <= 0) return;
        float scale = features.size("data") ? NumericPolicy.finite(values.get(StatusBarSettings.DATA_ICON_SCALE), 100f) / 100f : 1f;
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        candidate.width = Math.max(frame.signalWidthPx, frame.signalWidthPx * scale);
        candidate.paintInset = (candidate.width - frame.signalWidthPx) * .5f;
        candidate.sortX += frame.nativeSignalLeftPx;
        Endpoint label = new Endpoint(candidate.view.get(), StatusBarLayoutSettings.WIFI,
                candidate.box, null, candidate.nativeX + frame.nativeLabelLeftPx, -1);
        label.labelFragment = true;
        label.width = width;
        label.paintInset = (width - frame.labelWidthPx) * .5f;
        WifiNeighbors neighbors = wifiNeighbors.get(root);
        if (neighbors != null) label.sortX = neighbors.sortX(scene.endpoints, label.sortX);
        scene.endpoints.add(label);
        scene.fragments.add(new Fragments(candidate, label, frame));
    }
    private boolean applySharedSlots(Scene scene) {
        for (Fragments fragments : scene.fragments) {
            Endpoint signal = fragments.signal, label = fragments.label;
            View host = signal.view.get();
            if (host == null || host != fragments.frame.host()) return false;
            float signalX = signal.target, labelX = label.target;
            int start = (int) Math.floor(Math.min(signal.visible ? signalX : labelX, label.visible ? labelX : signalX));
            int end = (int) Math.ceil(Math.max(signal.visible ? signalX + fragments.frame.signalWidthPx : start,
                    label.visible ? labelX + fragments.frame.labelWidthPx : start));
            int width = Math.max(0, end - start);
            int height = host.getMeasuredHeight();
            if (height <= 0) {
                sharedFailure(fragments, width, labelX - start, signalX - start, host.getMeasuredWidth(), "native_height_pending");
                return false;
            }
            if (!sharedSlots.apply(fragments.frame, width, Math.round(labelX - start), Math.round(signalX - start),
                    label.visible, signal.visible,
                    scene.settings.left(StatusBarLayoutSettings.WIFI) < 0f || scene.settings.right(StatusBarLayoutSettings.WIFI) < 0f
                            || scene.settings.left(StatusBarLayoutSettings.MOBILE) < 0f || scene.settings.right(StatusBarLayoutSettings.MOBILE) < 0f)) {
                sharedFailure(fragments, width, labelX - start, signalX - start, host.getMeasuredWidth(), sharedSlots.failure()); return false;
            }
            measureSharedHull(host, width, height);
            signal.box.measureOwned = true; signal.box.appliedMeasuredWidth = width;
            if (host.getMeasuredWidth() != width) {
                sharedFailure(fragments, width, labelX - start, signalX - start, host.getMeasuredWidth(), "native_measure_mismatch"); return false;
            }
            host.layout(signal.box.left, signal.box.top, signal.box.left + width, signal.box.bottom);
            signal.box.appliedLeft = signal.box.left; signal.box.appliedRight = signal.box.left + width;
            signal.box.owned = true; signal.box.desiredWidth = width;
            signal.target = start; signal.hullWidth = width;
            signal.signalInset = Math.round(signalX - start);
        }
        return true;
    }
    /** This exact width belongs to our outer hull, not to the native Row's
     * next content budget. Keep that ownership scoped through native measure. */
    private void measureSharedHull(View host, int width, int height) {
        NetworkSharedSlotLayout bridge = sharedSlots;
        if (bridge != null) bridge.beginHullMeasure(host);
        try {
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        } finally {
            if (bridge != null) bridge.endHullMeasure(host);
        }
    }
    private void sharedFailure(Fragments fragments, int hull, float label, float signal, int measured, String reason) {
        ModuleDiagnostics.fact("radio", "label", "共享位置布局回退", "backend", "native_flow", "reason", reason,
                "hull_px", hull, "label_width_px", fragments.frame.labelWidthPx, "signal_width_px", fragments.frame.signalWidthPx,
                "label_left_px", label, "signal_left_px", signal, "native_width_px", fragments.frame.nativeHostWidthPx,
                "measured_width_px", measured);
    }
    private void plan(Scene scene) {
        ViewGroup root = scene.root.get(); if (root == null) return;
        float start = root.getPaddingLeft(), end = root.getWidth() - root.getPaddingRight();
        float gapStart = end, gapEnd = start;
        for (Box box : scene.boxes.values()) {
            View view = box.view.get(); if (view == null || view.getVisibility() != View.VISIBLE) continue;
            String id = name(view);
            if (id.equals("status_bar_contents") || id.equals("status_bar_content_container") || id.equals("quick_qs_status_icons")) {
                start = Math.max(start, box.x + view.getPaddingLeft()); end = Math.min(end, box.x + box.width - view.getPaddingRight());
            }
            if (id.equals("cutout_space_view") || id.equals("cutout_space") || id.equals("centered_icon_area")) {
                if (box.width > 0f) { gapStart = Math.min(gapStart, box.x); gapEnd = Math.max(gapEnd, box.x + box.width); }
            }
        }
        if (qsRow(content(root))) {
            for (Box box : scene.boxes.values()) {
                View view = box.view.get();
                if (view != null && name(view).equals("qs_clock_container")) {
                    start = Math.min(start, Math.max(root.getPaddingLeft(), box.x + view.getPaddingLeft()));
                    end = Math.max(end, Math.min(root.getWidth() - root.getPaddingRight(), box.x + box.width - view.getPaddingRight()));
                }
            }
        }
        // C17 stores the screen-safe 78/88px insets on its two native side
        // containers, not on PhoneStatusBarView or status_bar_contents. Use the
        // actual physical half, since logical start/end exchange sides in RTL.
        for (Box box : scene.boxes.values()) {
            View view = box.view.get();
            if (view == null || view.getVisibility() != View.VISIBLE || box.width <= 0f) continue;
            String id = name(view);
            if (!id.equals("status_bar_start_side_container") && !id.equals("status_bar_end_side_container")) continue;
            float center = box.x + box.width * .5f;
            if (center < root.getWidth() * .5f) start = Math.max(start, box.x + view.getPaddingLeft());
            else if (center > root.getWidth() * .5f) end = Math.min(end, box.x + box.width - view.getPaddingRight());
        }
        // A capsule is not a physical cutout. Seedling receives the projected targets
        // and owns hiding, alpha and scale; adding its hull here would hide additional
        // clock/battery/radio slots through our unrelated width-overflow policy.
        // Native insets/cutout are authoritative. Without a center obstacle both sides share one budget.
        StatusBarFlowPlanner.Bounds bounds = gapEnd > gapStart
                ? new StatusBarFlowPlanner.Bounds(start, Math.max(start, gapStart), Math.min(end, gapEnd), end)
                : new StatusBarFlowPlanner.Bounds(start, end, start, end);
        LinkedHashMap<String, Group> grouped = new LinkedHashMap<>();
        scene.endpoints.sort(Comparator.comparingDouble(endpoint -> endpoint.sortX));
        // A system hint the OEM container itself hides (or reduces to an overflow
        // dot) draws no icon. It keeps its endpoint, so a later native state
        // change still rebuilds, but it takes no width or spacing; publishing it
        // hidden repeats the native decision.
        List<Endpoint> placed = new ArrayList<>(scene.endpoints.size());
        for (Endpoint endpoint : scene.endpoints) {
            if (nativeHiddenHint(endpoint)) { endpoint.visible = false; endpoint.nativeHidden = true; } else placed.add(endpoint);
        }
        // An unrelated clock/carrier gap must not squeeze the native radio
        // spacing out of the other side. Preserve only measured gaps between
        // untouched, adjacent native endpoints; configured placements have
        // their own explicit spacing and shared fragments have their own hull.
        for (int index = 0; index + 1 < placed.size(); index++) {
            Endpoint endpoint = placed.get(index), next = placed.get(index + 1);
            if (!endpoint.icon || endpoint.labelFragment || next.labelFragment
                    || customPlacement(scene, endpoint.token) || customPlacement(scene, next.token)||endpoint.hintCustom||next.hintCustom
                    || !nativeSide(endpoint, root).equals(nativeSide(next, root))) continue;
            float gap = next.sortX - endpoint.sortX - endpoint.width;
            gap -= nativeMargin(endpoint, false) + nativeMargin(next, true);
            if (Float.isFinite(gap) && gap > 0f && gap <= root.getWidth()) endpoint.nativeRight = gap;
        }
        Endpoint firstLeft = null, lastRight = null;
        boolean nativeLeft = true, nativeRight = true;
        for (Endpoint endpoint : placed) {
            String side = nativeSide(endpoint, root), chosen = chosenSide(scene,endpoint,side);
            if (StatusBarLayoutSettings.LEFT.equals(side) && firstLeft == null) firstLeft = endpoint;
            if (StatusBarLayoutSettings.RIGHT.equals(side)) lastRight = endpoint;
            if (customPlacement(scene, endpoint.token)||endpoint.hintCustom) {
                if (StatusBarLayoutSettings.LEFT.equals(side) || StatusBarLayoutSettings.LEFT.equals(chosen)) nativeLeft = false;
                if (StatusBarLayoutSettings.RIGHT.equals(side) || StatusBarLayoutSettings.RIGHT.equals(chosen)) nativeRight = false;
            }
        }
        // A completely untouched side also keeps its measured outer inset.
        // Inner OEM padding need not live on the two known side containers.
        if (nativeLeft && firstLeft != null && firstLeft.sortX + firstLeft.width * .5f <= root.getWidth() * .5f) {
            float gap = firstLeft.sortX - start - nativeMargin(firstLeft, true);
            if (Float.isFinite(gap) && gap > 0f && gap <= root.getWidth()) firstLeft.nativeLeft = gap;
        }
        if (nativeRight && lastRight != null && lastRight.sortX + lastRight.width * .5f >= root.getWidth() * .5f) {
            float gap = end - lastRight.sortX - lastRight.width - nativeMargin(lastRight, false);
            if (Float.isFinite(gap) && gap > 0f && gap <= root.getWidth()) lastRight.nativeRight += gap;
        }
        int ordinal = 0;
        for (Endpoint endpoint : placed) {
            String nativeSide = nativeSide(endpoint, root);
            boolean splitHints=StatusBarLayoutSettings.SYSTEM.equals(endpoint.token)&&scene.settings.hintLayoutActive();
            String side=splitHints?chosenSide(scene,endpoint,nativeSide):nativeSide;
            String identity = endpoint.token + ":" + side;
            Group group = grouped.get(identity);
            if (group == null) {
                group = new Group(endpoint.token, side, ordinal++);group.sideOverride=splitHints?side:null;grouped.put(identity, group);
            }
            group.endpoints.add(endpoint); group.width += endpoint.width;
            View view = endpoint.view.get(); ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
            if (!endpoint.labelFragment && params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                endpoint.nativeLeft += Math.max(0, margins.leftMargin); endpoint.nativeRight += Math.max(0, margins.rightMargin);
            }
            group.width += endpoint.nativeLeft + endpoint.nativeRight;
        }
        List<StatusBarFlowPlanner.Node> nodes = new ArrayList<>();
        float hintGap = values.getBoolean(NativeStatusIcons.MASTER, false)
                ? Math.max(0f, NumericPolicy.pixels(NumericPolicy.finite(values.get(NativeStatusIcons.SPACING), 0f), root.getResources().getDisplayMetrics().density)) : 0f;
        for (Group group : grouped.values()) {
            if(group.sideOverride!=null)group.endpoints.sort((first,second)->{
                int explicit=Integer.compare(first.hintOrder==0?1:0,second.hintOrder==0?1:0);
                return explicit!=0?explicit:Integer.compare(first.hintOrder,second.hintOrder);
            });
            if (group.token.equals(StatusBarLayoutSettings.SYSTEM) && hintGap > 0f) {
                for (int i = 0; i + 1 < group.endpoints.size(); i++) { group.endpoints.get(i).nativeRight += hintGap; group.width += hintGap; }
            }
            boolean relativePosition=!StatusBarLayoutSettings.WIFI.equals(group.token)
                    ||features.enabled(group.endpoints.get(0).labelFragment?"label":"wifi");
            group.node = new StatusBarFlowPlanner.Node(group.token, group.side, group.ordinal, group.width, 0, 0,group.sideOverride,relativePosition); nodes.add(group.node);
        }
        String radioConfiguration = sharedWidthKey(root);
        KnownRadios knownRadios = fullRadios.get(root);
        scene.plan = StatusBarFlowPlanner.plan(bounds, nodes, scene.settings, root.getResources().getDisplayMetrics().density,
                knownRadios == null || !knownRadios.configuration.equals(radioConfiguration) ? Float.NaN : knownRadios.width);
        if (Float.isFinite(scene.plan.fullRadios)) fullRadios.put(root, new KnownRadios(radioConfiguration, scene.plan.fullRadios));
        for (Group group : grouped.values()) {
            StatusBarFlowPlanner.Placement placement = scene.plan.find(group.node);
            float cursor = placement.x;
            for (Endpoint endpoint : group.endpoints) {
                cursor += endpoint.nativeLeft; endpoint.visible = placement.visible;
                endpoint.target = cursor + endpoint.paintInset; cursor += endpoint.width + endpoint.nativeRight;
                if (!endpoint.icon && !endpoint.labelFragment) endpoint.box.desiredX = endpoint.target;
            }
        }
        scene.reason = scene.plan.overflowCount == 0 ? "ready" : "width_overflow";
    }
    /** The OEM container hides a shown system hint (or keeps only an overflow dot)
     * when its own limit is reached, e.g. after another icon appears. Counting its
     * full width left an empty slot and shifted the following elements whenever an
     * icon toggled. Hints placed by the hint layout keep their own visibility rules. */
    static boolean nativeHiddenHint(boolean icon, String token, String hintSlot, int nativeVisible) {
        return icon && hintSlot == null && StatusBarLayoutSettings.SYSTEM.equals(token) && nativeVisible != 0;
    }
    private static boolean nativeHiddenHint(Endpoint endpoint) {
        return nativeHiddenHint(endpoint.icon, endpoint.token, endpoint.hintSlot, endpoint.nativeVisible);
    }
    private static String nativeSide(Endpoint endpoint, ViewGroup root) {
        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        for (ViewParent parent = endpoint.box.parent.get(); parent instanceof View && parent != root; parent = ((View) parent).getParent()) {
            String id = name((View) parent);
            // Device code279: the control-center header has its own containers. Its icons start right after the carrier,
            // so with a few more icons (Bluetooth after Wi-Fi turns off) the network speed's centre fell left of the
            // middle and it jumped to the left group, unlike the status bar. The container decides, as it does there.
            if (id.startsWith("status_bar_start_side") || id.equals("keyguard_status_bar_contents") || id.equals("qs_clock_container"))
                return rtl ? StatusBarLayoutSettings.RIGHT : StatusBarLayoutSettings.LEFT;
            if (id.startsWith("status_bar_end_side") || id.equals("status_icon_area") || id.equals("quick_qs_status_icons"))
                return rtl ? StatusBarLayoutSettings.LEFT : StatusBarLayoutSettings.RIGHT;
        }
        float center = endpoint.box.x + endpoint.nativeX + endpoint.width * .5f;
        return center < root.getWidth() * .5f ? StatusBarLayoutSettings.LEFT : StatusBarLayoutSettings.RIGHT;
    }
    private static String chosenSide(Scene scene,Endpoint endpoint,String nativeSide){
        String chosen=endpoint.hintPlacement?scene.settings.hintSide(endpoint.hintSlot):scene.settings.side(endpoint.token);
        return StatusBarLayoutSettings.LEFT.equals(chosen)||StatusBarLayoutSettings.RIGHT.equals(chosen)?chosen:nativeSide;
    }
    private boolean retainedHint(View child){
        return !settings.hintLayoutActive()||access==null||!access.hint(child)||hintRetention==null||hintRetention.test(child);
    }
    private void applyGeometry(Scene scene) {
        List<Box> boxes = new ArrayList<>(scene.boxes.values());
        boxes.sort((a, b) -> Integer.compare(b.depth, a.depth));
        for (Box box : boxes) {
            View view = box.view.get(); if (!(view instanceof ViewGroup) || view == scene.root.get()) continue;
            if (!access.container(view) && !structural(view)) continue;
            // Keep native full-width rows/focus targets fixed; only their structural children pack.
            String id = name(view);
            if (id.equals("status_bar_contents") || id.equals("status_bar_content_container")) continue;
            // Device code162: the shade header's icon row is only as wide as its native icons and in the
            // notification page it has a hardware layer of exactly that size, so a battery moved left or a
            // speed pushed out by a margin was cut away. It widens over the planned header row instead.
            boolean qsIcons = id.equals("quick_qs_status_icons");
            float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
            if (access.container(view)) {
                for (Endpoint endpoint : scene.endpoints) if (endpoint.icon && endpoint.box.parent.get() == view && endpoint.visible) {
                    minimum = Math.min(minimum, endpoint.target - endpoint.paintInset - endpoint.nativeLeft);
                    maximum = Math.max(maximum, endpoint.target + (endpoint.hullWidth > 0 ? endpoint.hullWidth : endpoint.width - endpoint.paintInset) + endpoint.nativeRight);
                }
            } else for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Box child = scene.boxes.get(((ViewGroup) view).getChildAt(i));
                View childView = child == null ? null : child.view.get();
                if (childView != null && childView.getVisibility() != View.GONE) {
                    minimum = Math.min(minimum, child.desiredX); maximum = Math.max(maximum, child.desiredX + child.desiredWidth);
                }
            }
            if (Float.isFinite(minimum) && Float.isFinite(maximum)) {
                // dispatchDraw clips children to the padded viewport, including
                // hardware-layer headers. Keep that native padding/clip policy:
                // expand the ancestor's hull around the content, not through it.
                ViewGroup group = (ViewGroup) view;
                if (group.getClipToPadding()) {
                    minimum -= Math.max(0, view.getPaddingLeft());
                    maximum += Math.max(0, view.getPaddingRight());
                }
                // Keep the OEM's row/padding containers intact whenever the
                // desired contents still fit. Repacking every ancestor into a
                // tight hull feeds our temporary widths into native layouts.
                if (minimum >= box.x && maximum <= box.x + box.width) {
                    box.desiredX = box.x; box.desiredWidth = box.width;
                } else if (access.container(view) || view.getPaddingLeft() != 0 || view.getPaddingRight() != 0 || qsIcons
                        || id.equals("status_bar_start_side_container") || id.equals("status_bar_end_side_container")) {
                    box.desiredX = Math.min(box.x, minimum);
                    box.desiredWidth = Math.max(box.x + box.width, maximum) - box.desiredX;
                } else {
                    box.desiredX = minimum; box.desiredWidth = Math.max(0f, maximum - minimum);
                }
            }
        }
        // Complete validation before the first write: unknown/interactive ancestors never become full-row targets.
        for (Endpoint endpoint : scene.endpoints) {
            for (ViewParent parent = endpoint.box.parent.get(); parent instanceof View && parent != scene.root.get(); parent = ((View) parent).getParent()) {
                View view = (View) parent;
                if (!access.container(view) && !structural(view)) { scene.invalid = true; scene.reason = "unsupported_interactive_host"; return; }
            }
        }
        boxes.sort(Comparator.comparingInt(box -> box.depth));
        for (Box box : boxes) {
            View view = box.view.get(); if (view == null || view == scene.root.get()) continue;
            // StatusIconState is the ONLY placement owner for its direct icon child.
            // Keep the native local rect; adding a second layout displacement breaks animations.
            if (access.container(box.parent.get() instanceof View ? (View) box.parent.get() : null)) continue;
            Box parent = scene.boxes.get(box.parent.get()); if (parent == null) continue;
            int left = Math.round(box.desiredX - parent.desiredX), right = Math.round(box.desiredX + box.desiredWidth - parent.desiredX);
            if (view.getLeft() != left || view.getRight() != right) {
                view.layout(left, box.top, right, box.bottom); box.appliedLeft = left; box.appliedRight = right; box.owned = true;
            }
        }
        for (Endpoint endpoint : scene.endpoints) if (!endpoint.icon && !endpoint.labelFragment && !endpoint.visible) {
            View view = endpoint.view.get(); if (view != null && view.getVisibility() == View.VISIBLE) { view.setVisibility(View.INVISIBLE); endpoint.visibilityOwned = true; }
        }
        if (!scene.invalid) for (Endpoint endpoint : scene.endpoints) { View view = endpoint.view.get(); if (view != null) OWNED.put(view, true); }
    }
    /**
     * Native StatusIconContainer asks the fluid-cloud (Seedling) controller on every icon pass of a
     * registered row. Ranges beyond the camera-only hole identify a real native interaction;
     * the coordinator continues to own its visibility and animation, without a synthetic cutout.
     */
    public void capsule(ViewGroup row, List<float[]> areas) {
        if (row == null || !wanted()) return;
        ViewGroup root = nativeRoot(row);
        if (root == null) return;
        // The native coordinator consumes these reports in the same updateStates call.
        // The flow plan no longer uses capsule bounds: rebuilding it here would
        // suppress target publication and expose a native target for one frame.
        float[] range = capsuleRange(root, areas);
        boolean active = range != null;
        if (active) {
            capsuleReportedRanges.put(root, range);
            if (capsuleRoots.containsKey(root) && !sameRange(capsuleRanges.get(root), range)) {
                capsuleRanges.put(root, range);
            }
        } else capsuleReportedRanges.remove(root);
        Boolean waiting = capsuleTargets.get(root);
        if (waiting != null && waiting == active) return; // already counting towards this state
        Runnable posted = capsuleSwitches.remove(root);
        capsuleTargets.remove(root);
        if (posted != null) root.removeCallbacks(posted);
        if (capsuleRoots.containsKey(root) == active) return; // a flash that came back before it counted
        final ViewGroup target = root;
        Runnable change = new Runnable() { @Override public void run() {
            if (capsuleSwitches.get(target) != this) return;
            capsuleSwitches.remove(target); capsuleTargets.remove(target);
            if (!wanted() || !target.isAttachedToWindow() || capsuleRoots.containsKey(target) == active) return;
            if (active) {
                float[] latest = capsuleReportedRanges.get(target); if (latest == null) return;
                capsuleRoots.put(target, Boolean.TRUE); capsuleRanges.put(target, latest);
            } else { capsuleRoots.remove(target); capsuleRanges.remove(target); }
        } };
        capsuleTargets.put(root, active); capsuleSwitches.put(root, change);
        // Posted, so it never re-enters the row's own layout that usually reports the capsule.
        root.postDelayed(change, active ? CAPSULE_SHOW_MS : CAPSULE_HIDE_MS);
    }
    private static boolean sameRange(float[] first, float[] second) {
        return first != null && second != null && first[0] == second[0] && first[1] == second[1];
    }
    private void cancelCapsulePosts() {
        for (Map.Entry<ViewGroup, Runnable> entry : new ArrayList<>(capsuleSwitches.entrySet()))
            if (entry.getKey() != null) entry.getKey().removeCallbacks(entry.getValue());
        capsuleSwitches.clear(); capsuleTargets.clear();
    }
    /**
     * Device code161: the plugin can keep a hidden area over the camera hole even with no capsule
     * shown, which made every bar yield for good (ordering and margins "did nothing"). A capsule
     * counts only when an area reaches beyond the camera cutout into space elements can use.
     */
    private static float[] capsuleRange(ViewGroup root, List<float[]> areas) {
        if (areas == null || areas.isEmpty() || root.getWidth() <= 0) return null;
        int[] screen = new int[2];
        root.getLocationOnScreen(screen);
        float density = root.getResources().getDisplayMetrics().density;
        float slack = 8f * (density > 0f ? density : 1f);
        float cutoutStart = Float.NaN, cutoutEnd = Float.NaN;
        ViewGroup content = content(root);
        for (ViewGroup parent : new ViewGroup[]{root, content}) {
            for (int i = 0; parent != null && i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                String id = name(child);
                if (child.getVisibility() == View.GONE || child.getWidth() <= 0
                        || !id.equals("cutout_space_view") && !id.equals("cutout_space")) continue;
                float left = screen[0] + layoutOrigin(child, root);
                if (!Float.isFinite(left)) continue;
                cutoutStart = Float.isNaN(cutoutStart) ? left : Math.min(cutoutStart, left);
                cutoutEnd = Float.isNaN(cutoutEnd) ? left + child.getWidth() : Math.max(cutoutEnd, left + child.getWidth());
            }
        }
        if (Float.isNaN(cutoutStart)) {
            // No cutout spacer: a centred camera hole of about 64dp.
            float center = screen[0] + root.getWidth() / 2f, half = 32f * (density > 0f ? density : 1f);
            cutoutStart = center - half; cutoutEnd = center + half;
        }
        float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
        for (float[] area : areas) {
            if (area == null || area.length < 2 || !Float.isFinite(area[0]) || !Float.isFinite(area[1]) || area[1] <= area[0]) continue;
            float left = Math.max(screen[0], area[0]), right = Math.min(screen[0] + root.getWidth(), area[1]);
            if (right <= left || left >= cutoutStart - slack && right <= cutoutEnd + slack) continue;
            minimum = Math.min(minimum, left); maximum = Math.max(maximum, right);
        }
        return maximum > minimum ? new float[]{minimum, maximum} : null;
    }
    private static Method hiddenAreas;
    private static Field hiddenDispatcher;
    /** OplusSeedlingCardController.hiddenIconDispatcher.getHiddenAreas(location) as horizontal screen ranges. */
    public static List<float[]> capsuleAreas(Object controller, Object location) {
        List<float[]> result = new ArrayList<>();
        if (controller == null || location == null) return result;
        try {
            if (hiddenDispatcher == null || hiddenDispatcher.getDeclaringClass() != controller.getClass()) {
                hiddenDispatcher = controller.getClass().getDeclaredField("hiddenIconDispatcher"); hiddenDispatcher.setAccessible(true);
            }
            Object dispatcher = hiddenDispatcher.get(controller);
            if (dispatcher == null) return result;
            if (hiddenAreas == null || !hiddenAreas.getDeclaringClass().isInstance(dispatcher)) {
                hiddenAreas = null;
                for (Class<?> type = dispatcher.getClass(); type != null && hiddenAreas == null; type = type.getSuperclass())
                    for (Method method : type.getDeclaredMethods())
                        if (method.getName().equals("getHiddenAreas") && method.getParameterTypes().length == 1
                                && method.getParameterTypes()[0].isInstance(location)) { method.setAccessible(true); hiddenAreas = method; break; }
                if (hiddenAreas == null) return result;
            }
            Object areas = hiddenAreas.invoke(dispatcher, location);
            if (!(areas instanceof java.util.Collection)) return result;
            for (Object area : (java.util.Collection<?>) areas) {
                float[] range = range(area);
                if (range != null) result.add(range);
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) { result.clear(); }
        return result;
    }
    /** A kotlin Pair of numbers or a Rect/RectF; anything else is unknown and ignored. */
    private static float[] range(Object area) {
        try {
            if (area instanceof android.graphics.Rect) { android.graphics.Rect r = (android.graphics.Rect) area; return new float[]{r.left, r.right}; }
            if (area instanceof android.graphics.RectF) { android.graphics.RectF r = (android.graphics.RectF) area; return new float[]{r.left, r.right}; }
            if (area == null) return null;
            Object first = area.getClass().getMethod("getFirst").invoke(area), second = area.getClass().getMethod("getSecond").invoke(area);
            if (first instanceof Number && second instanceof Number) return new float[]{((Number) first).floatValue(), ((Number) second).floatValue()};
        } catch (ReflectiveOperationException | RuntimeException unknown) { }
        return null;
    }
    /** Publish real layout targets before the native Seedling coordinator adds its animated displacement. */
    public boolean deferCoordinator(ViewGroup parent,Object controller,Method method,Object location,boolean animate) {
        if(internal||access==null||controller==null||method==null)return false;
        Scene scene=scene(parent);
        if(scene==null||scene.invalid||!scene.nativePass||!(scene.nativeLayout||scene.measureCommit)||!scene.nativeCallback)return false;
        prepareTargets(parent);
        scene.coordinators.put(parent,new NativeCoordinator(controller,method,location,animate));
        return true;
    }
    private void coordinateNative(Scene scene){
        if(scene==null||scene.coordinators.isEmpty())return;
        Map<ViewGroup,NativeCoordinator> pending=new java.util.LinkedHashMap<>(scene.coordinators);
        scene.coordinators.clear();
        boolean wasInternal=internal;internal=true;
        try{for(Map.Entry<ViewGroup,NativeCoordinator> item:pending.entrySet())item.getValue().run(item.getKey());}
        finally{internal=wasInternal;}
    }
    public void prepareTargets(ViewGroup parent) {
        if (internal) return;
        Scene scene = scene(parent); if (scene == null || scene.invalid) return;
        trace(scene, parent, scene.nativePass ? "native_targets" : "targets_enter");
        if (scene.nativePass) {
            // Native has just computed targets in its CURRENT parent frame.
            // Record that basis, without publishing stale scene coordinates,
            // so the capsule coordinator's following displacement survives.
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i); Object state = access.state(child);
                if (state != null && Float.isFinite(access.x(state))) scene.nativeTargets.put(child, new NativeTarget(state, access.x(state), access.visible(state)));
            }
        } else if (inputsCurrent(scene)) { publish(scene, parent); trace(scene, parent, "targets_leave"); }
        else {
            // A sibling may disappear between complete root traversals. Its old
            // allocation is no longer reusable, but unchanged surviving children
            // still have the same verified local frame. Keep those destinations
            // until the requested root transaction replaces the complete plan;
            // exposing native X here would move them there for exactly one frame.
            for (Endpoint endpoint : scene.endpoints) {
                View child = endpoint.view.get();
                NativeInput input = scene.inputs.get(child);
                if (endpoint.icon && endpoint.box.parent.get() == parent && child != null
                        && access.eligible(parent, child) && input != null && inputCurrent(input, parent, child))
                    publishEndpoint(scene, endpoint);
            }
        }
    }
    public void beforeApply(Object state, View child) {
        if (internal || access == null) return;
        WeakReference<Object> handoff = nativeHandoffs.remove(child);
        if (handoff != null && handoff.get() == state) access.retargetFromCurrent(state, child);
        Scene scene = scene(child); if (scene == null || scene.invalid) return;
        access.retargetFromCurrent(state, child);
        if (scene.nativePass) return;
        for (Endpoint endpoint : scene.endpoints) if (endpoint.view.get() == child && endpoint.state.get() == state
                && endpoint.stateOwned && endpoint.visible && endpoint.appliedVisible != 2) {
            endpoint.coordinatorHidden = access.visible(state) == 2;
            break;
        }
        if (scene.rebuildRequested) return;
        NativeInput input = scene.inputs.get(child);
        if (!(child.getParent() instanceof ViewGroup) || input == null
                || !inputCurrent(input, (ViewGroup) child.getParent(), child)) { requestRebuild(scene); return; }
        for (Endpoint endpoint : scene.endpoints) if (endpoint.view.get() == child && endpoint.state.get() == state) {
            // A native animator/coordinator has adjusted an already-published target: retain that residual.
            if (!endpoint.stateOwned) publishEndpoint(scene, endpoint);
            return;
        }
    }
    /** Native still calculates its targets/occlusion. Commit once after all root
     * geometry is final, so no native X curve starts towards a temporary frame. */
    public boolean deferNativeApply(Object state, View child) {
        if (internal || access == null || state == null || child == null) return false;
        Scene scene = scene(child);
        // Only onLayout guarantees the matching final geometry commit. A
        // native visibility update during onMeasure must not wait for it.
        if (scene == null || scene.invalid || !scene.nativePass || !(scene.nativeLayout || scene.measureCommit) || !scene.nativeCallback || access.state(child) != state) return false;
        scene.deferredNative.put(child, new NativeApply(state, access.applyContext(state)));
        return true;
    }
    private void publish(Scene scene, ViewGroup filter) {
        if (scene.invalid) return;
        for (Endpoint endpoint : scene.endpoints) if (endpoint.icon && (filter == null || endpoint.box.parent.get() == filter)) publishEndpoint(scene, endpoint);
    }
    private void publishEndpoint(Scene scene, Endpoint endpoint) {
        // A natively hidden hint is never written: writing its hidden state back made the
        // hide outlive the native decision (Bluetooth stayed hidden after reconnecting). The
        // native container keeps deciding, and showing it relayouts and rebuilds this scene.
        if (endpoint.nativeHidden) return;
        Object state = endpoint.state.get(); Box parent = scene.boxes.get(endpoint.box.parent.get());
        View child = endpoint.view.get();
        if (state == null || parent == null || child == null || child.getParent() != endpoint.box.parent.get()) return;
        if (!endpoint.stateOwned && !endpoint.baselineKnown) { endpoint.nativeX = access.x(state); endpoint.nativeVisible = access.visible(state); }
        endpoint.baselineKnown = false;
        endpoint.appliedX = endpoint.target - parent.desiredX - endpoint.box.left + endpoint.residualX;
        access.x(state, endpoint.appliedX); endpoint.stateOwned = true;
        if (!endpoint.visible) { endpoint.appliedVisible = 2; access.visible(state, 2); }
        else if (!endpoint.coordinatorHidden && !StatusBarLayoutSettings.SYSTEM.equals(endpoint.token)
                && customPlacement(scene, endpoint.token) && access.visible(state) != 0) {
            // Eligible radio/speed nodes have a real flow allocation. The old local
            // container's overflow mode is replaced before Seedling adds animation.
            endpoint.appliedVisible = 0; access.visible(state, 0);
        }
    }
    private static int nativeMargin(Endpoint endpoint, boolean left) {
        View view = endpoint.view.get();
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return 0;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        return Math.max(0, left ? params.leftMargin : params.rightMargin);
    }
    private static boolean customPlacement(Scene scene, String token) {
        StatusBarLayoutSettings.Snapshot settings = scene.settings;
        return settings.wifiLabelShared && (StatusBarLayoutSettings.WIFI.equals(token) || StatusBarLayoutSettings.MOBILE.equals(token))
                || !StatusBarLayoutSettings.NATIVE.equals(settings.side(token)) || settings.order(token) != 0
                || settings.left(token) != 0f || settings.right(token) != 0f;
    }
    public void beforeStates(ViewGroup parent) {
        Scene scene = scene(parent); if (scene == null || internal) return;
        trace(scene, parent, "states_enter");
        boolean previous = internal; internal = true;
        try {
            for (int i = 0; i < parent.getChildCount(); i++) scene.nativeTargets.remove(parent.getChildAt(i));
            for (Endpoint endpoint : scene.endpoints) if (endpoint.box.parent.get() == parent) {
                restoreState(endpoint);
                // Seedling's previous hide belongs to that calculation only.
                // The current coordinator still runs AFTER prepareTargets.
                endpoint.coordinatorHidden = false;
            }
            if (!scene.invalid && !scene.nativePass) inputsCurrent(scene);
            if (!syncIconFrames(scene, parent, false, true)) { scene.invalid = true; scene.reason = "native_animation_unavailable"; }
            trace(scene, parent, "states_leave");
        } finally { internal = previous; }
    }
    /** The existing View observer receives the actual native container after an
     * ancestor/window visibility change, even if no state or root layout follows. */
    public void visibilityChanged(View view) {
        if (view instanceof ViewGroup) afterStates((ViewGroup) view);
    }
    /** Radio eligibility and Compose measurement can change without a root
     * layout. Never republish a stale allocation; ask for one finite rebuild. */
    public void afterStates(ViewGroup parent) {
        if (internal || !wanted() || access == null || parent == null || !access.container(parent)) return;
        ViewGroup root = nativeRoot(parent); if (root == null) return;
        observed(root);
        List<View> pending = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i); if (child == null) continue;
            if (child.getMeasuredWidth() != 0 || child.getMeasuredHeight() != 0) {
                if (nativeMeasureEligibility.remove(child) != null) nativeMeasureRetries.remove(root);
                nativeUnmeasured.remove(child); nativeMeasureAttempts.remove(child); nativeMeasureOwners.remove(child); continue;
            }
            nativeMeasureOwner(child, root);
            boolean eligible = access.eligible(parent, child) && retainedHint(child)&&visibleForNativeRecovery(child);
            Boolean previous = nativeMeasureEligibility.put(child, eligible);
            if (previous != null && previous != eligible) {
                // A logical visibility/block/ignored-slot edge starts a new native lifecycle.
                nativeUnmeasured.remove(child); nativeMeasureAttempts.remove(child); nativeMeasureRetries.remove(root);
            }
            if (eligible && !nativeUnmeasured.containsKey(child)) {
                if (pending == null) pending = new ArrayList<>(); pending.add(child);
            }
        }
        Scene scene = scene(parent);
        // Fingerprint wake-and-unlock can update only the icon states: no root scene or a
        // pending initial scene is still enough to request one bounded real native traversal.
        if (scene != null && scene.nativeCallback) return;
        if (pending != null) requestNativeMeasure(root, pending);
        if (scene == null || scene.invalid) return;
        if (scene.nativePass || !inputsCurrent(scene)) requestRebuild(scene);
    }
    private void requestRebuild(Scene scene) {
        if (scene.rebuildRequested) return;
        ViewGroup root = scene.root.get();
        if (root != null) { scene.rebuildRequested = true; root.requestLayout(); }
    }
    private void captureInputs(Scene scene) {
        scene.inputs.clear();
        for (WeakReference<ViewGroup> reference : scene.containers) {
            ViewGroup parent = reference.get(); if (parent == null) continue;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child != null) scene.inputs.put(child, input(parent, child));
            }
        }
    }
    private NativeInput input(ViewGroup parent, View child) {
        return new NativeInput(parent, access.state(child), access.eligible(parent, child), child.getMeasuredWidth(), inputFrame(child),iconFrames.get(child));
    }
    private NetworkSharedSlotLayout.Frame inputFrame(View child) {
        return sharedSlots != null && StatusBarLayoutSettings.MOBILE.equals(slotToken(child)) ? sharedSlots.frame(child) : null;
    }
    private boolean inputCurrent(NativeInput previous, ViewGroup parent, View child) {
        return previous.same(parent, access.state(child), access.eligible(parent, child), child.getMeasuredWidth(), inputFrame(child),iconFrames.get(child));
    }
    private boolean inputsCurrent(Scene scene) {
        if (scene.rebuildRequested) return false;
        int count = 0;
        for (WeakReference<ViewGroup> reference : scene.containers) {
            ViewGroup parent = reference.get(); if (parent == null) continue;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i); if (child == null) continue;
                count++;
                NativeInput previous = scene.inputs.get(child);
                if (previous == null || !inputCurrent(previous, parent, child)) { requestRebuild(scene); return false; }
            }
        }
        if (count != scene.inputs.size()) { requestRebuild(scene); return false; }
        return true;
    }
    /** Translate only the native X coordinate frame. Parent animations are not
     * layout origins and must remain untouched. Mobile hulls are anchored on
     * the same signal fragment even when their internal text changes sides. */
    private boolean syncIconFrames(Scene scene, ViewGroup filter, boolean planned, boolean shiftState) {
        ViewGroup root = scene.root.get(); if (root == null) return false;
        List<FrameUpdate> updates = new ArrayList<>();
        for (WeakReference<ViewGroup> reference : scene.containers) {
            ViewGroup parent = reference.get(); if (parent == null || filter != null && parent != filter) continue;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i); Object state = access.state(child);
                if (state == null || !access.eligible(parent, child)) continue;
                float origin = layoutOrigin(child, root); if (!Float.isFinite(origin)) return false;
                float inset = 0f;
                if (sharedSlots != null && StatusBarLayoutSettings.MOBILE.equals(slotToken(child))) {
                    NetworkSharedSlotLayout.Frame nativeFrame = sharedSlots.frame(child);
                    if (nativeFrame != null && nativeFrame.valid) inset = nativeFrame.currentSignalLeftPx;
                    if (planned) for (Endpoint endpoint : scene.endpoints)
                        if (endpoint.icon && endpoint.view.get() == child && endpoint.hullWidth > 0f) inset = endpoint.signalInset;
                }
                IconFrame next = new IconFrame(root, parent, origin, parent.getWidth(), child.getWidth(), inset);
                IconFrame old = iconFrames.get(child);
                FrameUpdate update = new FrameUpdate(child, state, old, next, access.x(state));
                if (old != null && old.root.get() == root && old.parent.get() == parent) {
                    update.dx = old.origin - origin + old.inset - inset;
                    update.parentDelta = next.parentWidth - old.parentWidth; update.childDelta = next.childWidth - old.childWidth;
                    update.changed = Math.abs(update.dx) > .001f || update.parentDelta != 0f || update.childDelta != 0f;
                    if (update.changed && !access.canRebase(state, child, update.dx, update.parentDelta, update.childDelta)) return false;
                }
                updates.add(update);
            }
        }
        // No native property is touched until every participating icon agrees
        // to the same transaction. A late native rejection rolls back earlier
        // curves before any geometry can be restored in the caller.
        for (int i = 0; i < updates.size(); i++) {
            FrameUpdate update = updates.get(i);
            if (update.changed && !access.rebase(update.state, update.child, update.dx, update.parentDelta, update.childDelta)) {
                for (int j = i - 1; j >= 0; j--) {
                    FrameUpdate applied = updates.get(j);
                    if (applied.changed) access.rebase(applied.state, applied.child, -applied.dx, -applied.parentDelta, -applied.childDelta);
                    access.x(applied.state, applied.target);
                }
                return false;
            }
            if (!shiftState) access.x(update.state, update.target);
        }
        for (FrameUpdate update : updates) {
            if (shiftState && update.changed) for (Endpoint endpoint : scene.endpoints)
                if (endpoint.view.get() == update.child && endpoint.stateOwned) {
                    endpoint.appliedX += update.dx; endpoint.nativeX += update.dx;
                }
            iconFrames.put(update.child, update.next);
        }
        return true;
    }
    private static float layoutOrigin(View child, ViewGroup root) {
        float result = 0f;
        for (int depth = 0; child != null && depth < 16; depth++) {
            if (child == root) return result;
            result += child.getLeft();
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        return Float.NaN;
    }
    private void trace(Scene scene, ViewGroup filter, String phase) {
        if (!ModuleDiagnostics.enabled() || traceBudget >= 192) return;
        ViewGroup root = scene.root.get();
        if (root == null || !root.getClass().getName().contains("PhoneStatusBarView")) return;
        for (Endpoint endpoint : scene.endpoints) {
            View child = endpoint.view.get(); Object state = endpoint.state.get();
            if (!endpoint.icon || child == null || state == null || !(child.getParent() instanceof ViewGroup)
                    || filter != null && child.getParent() != filter) continue;
            ViewGroup parent = (ViewGroup) child.getParent();
            ModuleDiagnostics.fact("status_hints", "scene", "状态栏坐标交接诊断", "backend", "flow_probe", "reason", phase,
                    "count", ++traceBudget, "group", endpoint.token, "parent_origin_px", layoutOrigin(parent, root),
                    "native_width_px", parent.getWidth(), "view_width", child.getWidth(), "current_x_px", child.getTranslationX(),
                    "target_x_px", access.x(state), "distance_end_px", access.distance(state), "curve_end_px", access.curveEnd(child),
                    "baseline_x_px", endpoint.nativeX, "applied_x_px", endpoint.appliedX, "residual_x_px", endpoint.residualX);
        }
    }
    private final Map<ViewGroup, String> layoutTrace = new WeakHashMap<>();
    private int layoutBudget;
    /** Device code162: shade header elements vanished. Record each changed plan with where every
     * element is really drawn (layout origin plus translation), for all hosts. */
    private void traceLayout(Scene scene) {
        ViewGroup root = scene.root.get();
        if (!ModuleDiagnostics.enabled() || layoutBudget >= 96 || root == null || scene.plan == null) return;
        List<Object> pairs = new ArrayList<>();
        StringBuilder text = new StringBuilder(scene.reason);
        int index = 0;
        for (Endpoint endpoint : scene.endpoints) {
            if (index >= 8) break;
            View view = endpoint.view.get();
            float drawn = view == null ? Float.NaN : layoutOrigin(view, root) + view.getTranslationX();
            View parent = view != null && view.getParent() instanceof View ? (View) view.getParent() : null;
            float parentAt = parent == null ? Float.NaN : layoutOrigin(parent, root);
            // token:target:width:visible:drawn:parent origin:parent identity (a re-parented slot shows up here)
            String value = endpoint.token + ":" + Math.round(endpoint.target) + ":" + Math.round(endpoint.width) + ":"
                    + (endpoint.visible ? 1 : 0) + ":" + (Float.isFinite(drawn) ? Math.round(drawn) : -1)
                    + ":" + (Float.isFinite(parentAt) ? Math.round(parentAt) : -1)
                    + ":" + (parent == null ? "0" : Integer.toHexString(System.identityHashCode(parent) & 0xffff))
                    + (view != null && view.getTranslationX() != 0f ? ":t" + Math.round(view.getTranslationX()) : "");
            pairs.add("e" + index++); pairs.add(value); text.append(';').append(value);
        }
        if (text.toString().equals(layoutTrace.get(root))) return;
        layoutTrace.put(root, text.toString()); layoutBudget++;
        StatusBarFlowPlanner.Bounds bounds = scene.plan.bounds;
        pairs.add("host"); pairs.add(host(root)); pairs.add("reason"); pairs.add(scene.reason);
        pairs.add("bounds_start"); pairs.add(Math.round(bounds.leftStart)); pairs.add("bounds_end"); pairs.add(Math.round(bounds.rightEnd));
        pairs.add("bounds_shared"); pairs.add(bounds.shared); pairs.add("capsule"); pairs.add(capsuleRoots.containsKey(root));
        ModuleDiagnostics.fact("status_hints", "scene", "状态栏布局明细", pairs.toArray());
    }
    private static String host(ViewGroup root) {
        String name = root.getClass().getName();
        return name.contains("Keyguard") ? "keyguard" : name.contains("QuickStatus") ? "qs" : "phone";
    }
    private final Map<ViewGroup, String> verticalTrace = new WeakHashMap<>();
    private int verticalBudget;
    /** Reload/unlock reports a whole row shifted up: record only changed vertical frames. */
    private void traceVertical(ViewGroup root) {
        if (!ModuleDiagnostics.enabled() || verticalBudget >= 96 || root == null) return;
        ViewGroup content = content(root);
        View start = null, end = null;
        for (int i = 0; content != null && i < content.getChildCount(); i++) {
            String id = name(content.getChildAt(i));
            if (id.equals("status_bar_start_side_container")) start = content.getChildAt(i);
            if (id.equals("status_bar_end_side_container")) end = content.getChildAt(i);
        }
        int[] frame = {root.getTop(), root.getHeight(), root.getPaddingTop(), Math.round(root.getTranslationY()),
                content == null ? -1 : content.getTop(), content == null ? -1 : content.getHeight(),
                content == null ? -1 : content.getPaddingTop(), content == null ? 0 : Math.round(content.getTranslationY()),
                start == null ? -1 : start.getTop(), start == null ? -1 : start.getHeight(), start == null ? 0 : Math.round(start.getTranslationY()),
                end == null ? -1 : end.getTop(), end == null ? -1 : end.getHeight(), end == null ? 0 : Math.round(end.getTranslationY())};
        String leaves = verticalLeaves(root);
        int contentScreenY = screenY(content);
        String text = java.util.Arrays.toString(frame) + contentScreenY + leaves + scenes.containsKey(root) + capsuleRoots.containsKey(root);
        if (text.equals(verticalTrace.get(root))) return;
        verticalTrace.put(root, text); verticalBudget++;
        ModuleDiagnostics.fact("status_hints", "scene", "状态栏纵向诊断", "backend", "flow_probe", "reason", "vertical",
                "host", root.getClass().getName().contains("Keyguard") ? "keyguard"
                        : root.getClass().getName().contains("QuickStatus") ? "qs" : "phone",
                "root_top", frame[0], "root_height", frame[1], "root_pad_top", frame[2], "root_ty", frame[3],
                "content_top", frame[4], "content_height", frame[5], "content_pad_top", frame[6], "content_ty", frame[7],
                "start_top", frame[8], "start_height", frame[9], "start_ty", frame[10],
                "end_top", frame[11], "end_height", frame[12], "end_ty", frame[13],
                "content_screen_y", contentScreenY, "leaves", leaves,
                "applied", scenes.containsKey(root), "capsule", capsuleRoots.containsKey(root));
    }
    /** Diagnostics only, on the existing root-layout callback: bounded structure, never text. */
    private static String verticalLeaves(ViewGroup root) {
        View[] leaves = new View[6];
        verticalLeaves(root, leaves, 0, new int[]{0});
        StringBuilder result = new StringBuilder(320);
        String[] roles = {"clock", "system_icons", "status_icons", "battery", "speed", "notifications"};
        for (int i = 0; i < leaves.length; i++) {
            View view = leaves[i];result.append(roles[i]).append('=');
            if (view == null) { result.append("absent;");continue; }
            result.append("sy:").append(screenY(view)).append(",t:").append(view.getTop())
                    .append(",w:").append(view.getWidth()).append(",h:").append(view.getHeight())
                    .append(",mw:").append(view.getMeasuredWidth()).append(",mh:").append(view.getMeasuredHeight())
                    .append(",ty:").append(view.getTranslationY()).append(",py:").append(view.getPaddingTop())
                    .append('/').append(view.getPaddingBottom()).append(",v:").append(view.getVisibility());
            if (view instanceof ViewGroup)result.append(",clip:").append(((ViewGroup)view).getClipChildren()).append('/')
                    .append(((ViewGroup)view).getClipToPadding());
            result.append(';');
        }
        return result.toString();
    }
    private static void verticalLeaves(View view, View[] found, int depth, int[] visits) {
        if (depth > 12 || ++visits[0] > 384) return;
        String id = name(view);int slot = id.equals("clock") || id.equals("keyguard_status_bar_clock") ? 0
                : id.equals("system_icons") ? 1 : id.equals("statusIcons") || id.equals("status_icons") ? 2
                : id.equals("battery") || id.equals("batteryRemainingIcon") ? 3 : id.equals("network_speed") ? 4
                : id.equals("notificationIcons") ? 5 : -1;
        if (slot >= 0 && found[slot] == null)found[slot] = view;
        if (view instanceof ViewGroup)for (int i = 0; i < ((ViewGroup)view).getChildCount() && visits[0] < 384; i++)
            verticalLeaves(((ViewGroup)view).getChildAt(i), found, depth + 1, visits);
    }
    private static int screenY(View view) { if (view == null)return -1;int[] location = new int[2];view.getLocationOnScreen(location);return location[1]; }
    private Scene scene(View child) {
        if (!wanted() || access == null) return null;
        View current = child;
        for (int i = 0; current != null && i < 16; i++) {
            Scene scene = current instanceof ViewGroup ? scenes.get(current) : null; if (scene != null) return scene;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }
    public String reason(ViewGroup root) { Scene scene = scenes.get(root); return scene == null ? "native" : scene.reason; }
    private void restoreState(Endpoint endpoint) {
        Object state = endpoint.state.get(); if (state == null || !endpoint.stateOwned || access == null) return;
        View child = endpoint.view.get(); if (child == null || child.getParent() != endpoint.box.parent.get()) { endpoint.stateOwned = false; return; }
        float residual = access.x(state) - endpoint.appliedX;
        if (Float.isFinite(residual)) access.x(state, endpoint.nativeX + endpoint.residualX + residual);
        if (endpoint.appliedVisible >= 0 && access.visible(state) == endpoint.appliedVisible) access.visible(state, endpoint.nativeVisible);
        endpoint.stateOwned = false; endpoint.appliedVisible = -1;
        endpoint.residualX = 0f; endpoint.baselineKnown = false;
    }
    /** Abort only a proven published layout transaction, preserving the OEM displacement. */
    private void restoreNativeTransaction(Scene scene) {
        if (scene == null || !(scene.nativeLayout || scene.measureCommit) || access == null) return;
        List<Endpoint> reverted=new ArrayList<>();
        for (Endpoint endpoint : scene.endpoints) {
            Object state = endpoint.state.get(); View child = endpoint.view.get();
            if (!endpoint.stateOwned || state == null || child == null || child.getParent() != endpoint.box.parent.get()) continue;
            float residual = access.x(state) - endpoint.appliedX;
            if (Float.isFinite(residual)) access.x(state, endpoint.nativeX + residual);
            if (endpoint.appliedVisible >= 0 && access.visible(state) == endpoint.appliedVisible) access.visible(state, endpoint.nativeVisible);
            endpoint.stateOwned = false; reverted.add(endpoint);
        }
        coordinateNative(scene);
        for(Endpoint endpoint:reverted)applyNative(scene,endpoint.state.get(),endpoint.view.get());
        for (Map.Entry<View, NativeApply> entry : new ArrayList<>(scene.deferredNative.entrySet())) {
            View child = entry.getKey(); Object state = entry.getValue().state.get();
            if (child != null && state != null && access.state(child) == state) applyNative(scene, state, child);
        }
        scene.deferredNative.clear();
    }
    private void applyUnplannedNative(Scene previous, Scene next) {
        if (previous == null) return;
        for (Map.Entry<View, NativeApply> entry : new ArrayList<>(previous.deferredNative.entrySet())) {
            View child = entry.getKey(); Object state = entry.getValue().state.get();
            if (child == null || state == null || access.state(child) != state) continue;
            boolean planned = false;
            for (Endpoint endpoint : next.endpoints) if (endpoint.view.get() == child && endpoint.state.get() == state) { planned = true; break; }
            // Removed/blocked native slots still receive their native hidden
            // state once; omission from the flow must not leave stale ink.
            if (!planned) applyNative(previous, state, child);
        }
        previous.deferredNative.clear();
    }
    private void applyNative(Scene source, Object state, View child) {
        NativeApply pending = source == null ? null : source.deferredNative.remove(child);
        Object context = pending != null && pending.state.get() == state ? pending.context : access.applyContext(state);
        access.apply(state, child, context);
    }
    private void restore(Scene scene) {
        restore(scene, true);
    }
    private void restore(Scene scene, boolean releaseSharedTarget) {
        boolean previous = internal; internal = true;
        try {
            boolean pendingMeasure = pendingNativeMeasure(scene);
            for (Endpoint endpoint : scene.endpoints) {
                restoreState(endpoint); View view = endpoint.view.get();
                if (view != null) OWNED.remove(view);
                if (view != null && endpoint.visibilityOwned && view.getVisibility() == View.INVISIBLE) view.setVisibility(View.VISIBLE);
            }
            if (releaseSharedTarget && sharedSlots != null)
                for (Fragments fragments : scene.fragments) sharedSlots.restore(fragments.frame.host());
            List<Box> boxes = new ArrayList<>(scene.boxes.values()); boxes.sort(Comparator.comparingInt(box -> box.depth));
            for (Box box : boxes) {
                // Restoration also calls native ancestor layout. Preserve the
                // pending request here, including repeated settings at boot.
                if (pendingMeasure) continue;
                View view = box.view.get(); if (view != null && box.owned && view.getParent() == box.parent.get()
                        && view.getLeft() == box.appliedLeft && view.getRight() == box.appliedRight) view.layout(box.left, view.getTop(), box.right, view.getBottom());
                // The shared flow owns width only. A later native measure may
                // have replaced height independently, including after boot.
                if (view != null && box.measureOwned && view.getMeasuredWidth() == box.appliedMeasuredWidth
                        && view.getMeasuredHeight() > 0)
                    measureSharedHull(view, box.measuredWidth, view.getMeasuredHeight());
                box.owned = false;
                box.measureOwned = false;
            }
            // Restoration changes the same coordinate frame as installation.
            // Move current/curve/history with it while keeping the separately
            // captured native target, including its real animation residual.
            syncIconFrames(scene, null, false, false);
            if (releaseSharedTarget) for (Endpoint endpoint : scene.endpoints) {
                View child = endpoint.view.get(); Object state = endpoint.state.get();
                if (child != null && state != null && child.getParent() == endpoint.box.parent.get()) {
                    access.retargetFromCurrent(state, child);
                    if (access.geometrySupported()) access.applyVisibility(state, child);
                    nativeHandoffs.put(child, new WeakReference<>(state));
                }
            }
        } finally { internal = previous; }
    }
    public void releaseRuntime() { released = true; releaseParentAnimations(); cancelNativeMeasurePosts(); cancelCapsulePosts(); nativeMeasureRetries.clear(); nativeMeasureEligibility.clear(); nativeMeasureAttempts.clear(); nativeMeasureOwners.clear(); nativeUnmeasured.clear(); capsuleRoots.clear(); capsuleRanges.clear(); capsuleReportedRanges.clear(); for (Scene scene : new ArrayList<>(scenes.values())) { restore(scene); restoreNativeTransaction(scene); } scenes.clear(); roots.clear(); sharedWidths.clear(); wifiNeighbors.clear(); iconFrames.clear(); }
    private static final class NativeMeasureOwner {
        final WeakReference<ViewParent> parent;
        final WeakReference<ViewGroup> root;
        NativeMeasureOwner(ViewParent parent, ViewGroup root) { this.parent = new WeakReference<>(parent); this.root = new WeakReference<>(root); }
    }
    private static final class IconFrame {
        final WeakReference<ViewGroup> root, parent;
        final float origin, parentWidth, childWidth, inset;
        IconFrame(ViewGroup root, ViewGroup parent, float origin, float parentWidth, float childWidth, float inset) {
            this.root = new WeakReference<>(root); this.parent = new WeakReference<>(parent);
            this.origin = origin; this.parentWidth = parentWidth; this.childWidth = childWidth; this.inset = inset;
        }
    }
    private static final class FrameUpdate {
        final View child; final Object state; final IconFrame old, next; final float target;
        float dx, parentDelta, childDelta; boolean changed;
        FrameUpdate(View child, Object state, IconFrame old, IconFrame next, float target) {
            this.child = child; this.state = state; this.old = old; this.next = next; this.target = target;
        }
    }
    private static final class NativeTarget {
        final WeakReference<Object> state; final float x; final int visible;
        NativeTarget(Object state, float x, int visible) { this.state = new WeakReference<>(state); this.x = x; this.visible = visible; }
    }
    private static final class NativeApply {
        final WeakReference<Object> state;
        final Object context;
        NativeApply(Object value, Object context) { state = new WeakReference<>(value); this.context = context; }
    }
    private static final class NativeInput {
        final WeakReference<ViewGroup> parent; final WeakReference<Object> state;
        final boolean eligible, fragments, label;
        final boolean framed;
        final int width, labelWidth, signalWidth, nativeWidth;
        // syncIconFrames already computed this layout basis. Comparing it adds
        // no ancestor walk and deliberately ignores native translation/alpha/scale.
        final float origin,parentWidth,childWidth;
        NativeInput(ViewGroup parent, Object state, boolean eligible, int width, NetworkSharedSlotLayout.Frame frame,IconFrame geometry) {
            this.parent = new WeakReference<>(parent); this.state = new WeakReference<>(state);
            this.eligible = eligible; this.width = width; fragments = frame != null && frame.valid;
            label = fragments && frame.labelPresent;
            labelWidth = fragments ? frame.labelWidthPx : 0; signalWidth = fragments ? frame.signalWidthPx : 0;
            nativeWidth = fragments ? frame.nativeHostWidthPx : 0;
            framed=geometry!=null;origin=framed?geometry.origin:0f;
            parentWidth=framed?geometry.parentWidth:0f;childWidth=framed?geometry.childWidth:0f;
        }
        boolean same(ViewGroup parent, Object state, boolean eligible, int width, NetworkSharedSlotLayout.Frame frame,IconFrame geometry) {
            boolean fragments = frame != null && frame.valid;
            return this.parent.get() == parent && this.state.get() == state && this.eligible == eligible
                    && this.width == width && this.fragments == fragments && label == (fragments && frame.labelPresent)
                    && labelWidth == (fragments ? frame.labelWidthPx : 0) && signalWidth == (fragments ? frame.signalWidthPx : 0)
                    && nativeWidth == (fragments ? frame.nativeHostWidthPx : 0)
                    && framed==(geometry!=null)&&(!framed||origin==geometry.origin&&parentWidth==geometry.parentWidth&&childWidth==geometry.childWidth);
        }
    }
    private static final class WifiNeighbors {
        final WeakReference<View> left, right;
        WifiNeighbors(View left, View right) { this.left = new WeakReference<>(left); this.right = new WeakReference<>(right); }
        float sortX(List<Endpoint> endpoints, float fallback) {
            Float before = null, after = null;
            for (Endpoint endpoint : endpoints) {
                if (endpoint.view.get() == left.get()) before = endpoint.sortX;
                if (endpoint.view.get() == right.get()) after = endpoint.sortX;
            }
            if (before != null && after != null && before < after) return before + (after - before) * .5f;
            if (before != null) return before + .125f;
            if (after != null) return after - .125f;
            return fallback;
        }
    }
    private static final class Fragments {
        final Endpoint signal, label;
        final NetworkSharedSlotLayout.Frame frame;
        Fragments(Endpoint signal, Endpoint label, NetworkSharedSlotLayout.Frame frame) {
            this.signal = signal; this.label = label; this.frame = frame;
        }
    }
    private static final class NativeCoordinator {
        final WeakReference<Object> controller,location;final Method method;final boolean animate;
        NativeCoordinator(Object controller,Method method,Object location,boolean animate){
            this.controller=new WeakReference<>(controller);this.location=new WeakReference<>(location);this.method=method;this.animate=animate;
        }
        void run(ViewGroup row){
            Object owner=controller.get(),where=location.get();if(owner==null||where==null||row==null)return;
            try{method.invoke(owner,where,row,animate);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Native status-bar occlusion unavailable",failure);}
        }
    }
    private static final class Scene {
        final WeakReference<ViewGroup> root;
        final Map<View, Box> boxes = new WeakHashMap<>();
        final List<Endpoint> endpoints = new ArrayList<>();
        final List<Fragments> fragments = new ArrayList<>();
        final List<WeakReference<ViewGroup>> containers = new ArrayList<>();
        final Map<View, NativeTarget> nativeTargets = new WeakHashMap<>();
        final Map<View, NativeApply> deferredNative = new WeakHashMap<>();
        final Map<ViewGroup,NativeCoordinator> coordinators=new WeakHashMap<>();
        final Map<View, NativeInput> inputs = new WeakHashMap<>();
        boolean invalid, nativeLayout, nativePass, nativeCallback, measureCommit, rebuildRequested; String reason = "native"; StatusBarFlowPlanner.Plan plan;
        /** Full author settings; capsule visibility/animation remains with the native coordinator. */
        final StatusBarLayoutSettings.Snapshot settings;
        Scene(ViewGroup value, StatusBarLayoutSettings.Snapshot active) { root = new WeakReference<>(value); settings = active; }
    }
    private static final class Box {
        final WeakReference<View> view; final WeakReference<ViewParent> parent;
        final int left, right, top, bottom, depth, measuredWidth, measuredHeight; final float x, y, width;
        float originX, originY;
        float desiredX, desiredWidth; int appliedLeft, appliedRight, appliedMeasuredWidth; boolean owned, measureOwned;
        Box(View value, float parentX, float parentY, int level) {
            view = new WeakReference<>(value); parent = new WeakReference<>(value.getParent()); depth = level;
            left = value.getLeft(); right = value.getRight(); top = value.getTop(); bottom = value.getBottom();
            measuredWidth = value.getMeasuredWidth(); measuredHeight = value.getMeasuredHeight();
            x = parentX + left; y = parentY + top; originX = x; originY = y;
            width = Math.max(0, right - left); desiredX = x; desiredWidth = width;
        }
    }
    private static final class Endpoint {
        final WeakReference<View> view; final WeakReference<Object> state; final String token; final Box box;
        final boolean icon; float nativeX; int nativeVisible;
        float width, paintInset, nativeLeft, nativeRight, target, appliedX, hullWidth, sortX, signalInset;
        float residualX; boolean baselineKnown;
        int appliedVisible = -1; boolean visible, stateOwned, visibilityOwned, labelFragment, coordinatorHidden, nativeHidden;
        String hintSlot;int hintOrder;boolean hintPlacement,hintCustom;
        Endpoint(View value, String kind, Box geometry, Object object, float x, int visibility) {
            view = new WeakReference<>(value); state = new WeakReference<>(object); token = kind; box = geometry;
            icon = object != null; nativeX = x; nativeVisible = visibility;
            sortX = geometry.x + x;
        }
    }
    private static final class Group {
        final String token, side; final int ordinal; final List<Endpoint> endpoints = new ArrayList<>();
        float width; StatusBarFlowPlanner.Node node;
        String sideOverride;
        Group(String kind, String location, int index) { token = kind; side = location; ordinal = index; }
    }
}
