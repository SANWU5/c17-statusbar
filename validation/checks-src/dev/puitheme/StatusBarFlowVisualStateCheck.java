package dev.puitheme;

import android.os.Bundle;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

/** A root geometry transaction cannot replay an unrequested native alpha/hidden lifecycle. */
public final class StatusBarFlowVisualStateCheck {
    private static int checks;
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) try {
            Field value = type.getDeclaredField(name); value.setAccessible(true); return value.get(owner);
        } catch (NoSuchFieldException inherited) { }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static void near(float expected, float actual, String why) {
        check(Float.isFinite(actual) && Math.abs(expected - actual) < .02f, why + ": " + expected + " != " + actual);
    }
    private static Object rig() throws Exception {
        Constructor<?> constructor = Class.forName("dev.puitheme.StatusBarFlowLayoutCheck$Rig")
                .getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true); return constructor.newInstance(0, 300);
    }
    private static Bundle placement() {
        Bundle values = new Bundle(); values.putBoolean(StatusBarLayoutSettings.MASTER, true);
        values.putString(StatusBarLayoutSettings.key("wifi", "side"), "left");
        return values;
    }
    private static final class Visual {
        float alpha = 1f; boolean hidden; int calls, geometry, renderedVisible, visibleApplications;
    }
    private static final class KeyguardRoot extends com.android.systemui.statusbar.phone.KeyguardStatusBarView {
        final Resources resources; int width = 300;
        KeyguardRoot(Resources resources) { super(null); this.resources = resources; }
        @Override public Resources getResources() { return resources; }
        @Override public int getWidth() { return width; }
        @Override public int getMeasuredWidth() { return width; }
        @Override public int getHeight() { return 30; }
        @Override public int getMeasuredHeight() { return 30; }
    }
    /** Mirrors the inspected native apply's alpha/hidden side effects while
     * delegating all actual coordinates to the existing production fixture. */
    private static final class Lifecycle implements StatusBarFlowLayout.NativeAccess {
        final StatusBarFlowLayout.NativeAccess delegate;
        final IdentityHashMap<Object, Visual> values = new IdentityHashMap<>();
        boolean geometrySupported = true;
        Lifecycle(StatusBarFlowLayout.NativeAccess delegate) { this.delegate = delegate; }
        Visual visual(Object state) { return values.computeIfAbsent(state, key -> new Visual()); }
        public boolean container(View view) { return delegate.container(view); }
        public String slot(View view) { return delegate.slot(view); }
        public boolean eligible(ViewGroup parent, View child) { return delegate.eligible(parent, child); }
        public Object state(View view) { return delegate.state(view); }
        public float x(Object state) { return delegate.x(state); }
        public void x(Object state, float value) { delegate.x(state, value); }
        public int visible(Object state) { return delegate.visible(state); }
        public void visible(Object state, int value) { delegate.visible(state, value); }
        public boolean geometrySupported() { return geometrySupported; }
        public boolean canRebase(Object state, View view, float x, float parent, float child) { return delegate.canRebase(state, view, x, parent, child); }
        public boolean rebase(Object state, View view, float x, float parent, float child) { return delegate.rebase(state, view, x, parent, child); }
        public void applyGeometry(Object state, View child) { visual(state).geometry++; delegate.apply(state, child); }
        public void applyVisibility(Object state, View child) {
            Visual visual = visual(state);
            if (visual.renderedVisible != visible(state)) { visual.renderedVisible = visible(state); visual.visibleApplications++; }
        }
        public void apply(Object state, View child) {
            Visual visual = visual(state); visual.calls++;
            visual.renderedVisible = visible(state);
            child.setAlpha(visual.alpha); child.setVisibility(visual.hidden ? View.INVISIBLE : View.VISIBLE);
            delegate.apply(state, child);
        }
    }
    private static void rootOnlyVisualState() throws Exception {
        for (int initialVisible : new int[]{0, 2}) for (float nativeAlpha : new float[]{0f, .35f, 1f}) for (int nativeVisibility : new int[]{View.VISIBLE, View.INVISIBLE}) {
            Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
            Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
            ViewGroup root = (ViewGroup) field(fixture, "root"); View wifi = (View) field(fixture, "wifi");
            access.visible(access.state(wifi), initialVisible);
            flow.configure(placement()); flow.afterLayout(root);
            Visual stale = access.visual(access.state(wifi)); stale.alpha = nativeAlpha == 0f ? 1f : 0f;
            stale.hidden = nativeVisibility != View.INVISIBLE;
            // A real native animator/window transition has written only the View.
            // Neither this root onLayout nor its unchanged child invokes updateStates.
            wifi.setAlpha(nativeAlpha); wifi.setVisibility(nativeVisibility);
            int calls = stale.calls;
            try { new StatusBarFlowCallbacks(flow).dispatch(root, false, () -> null); }
            catch (Throwable failure) { throw new AssertionError(failure); }
            near(nativeAlpha, wifi.getAlpha(), "root-only geometry keeps the latest native alpha including exact zero");
            check(wifi.getVisibility() == nativeVisibility, "root-only geometry keeps the latest native View visibility");
            check(stale.calls == calls, "root-only geometry cannot consume a second native alpha/hidden lifecycle");
            flow.releaseRuntime();
        }
    }
    private static void requestedNativeLifecycle() throws Exception {
        Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
        ViewGroup root = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
        View wifi = (View) field(fixture, "wifi"); flow.configure(placement()); flow.afterLayout(root);
        Visual nativeState = access.visual(access.state(wifi)); int calls = nativeState.calls;
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try {
            callbacks.dispatch(root, false, () -> callbacks.updateStates(icons, () -> {
                nativeState.alpha = 0f; nativeState.hidden = true;
                flow.prepareTargets(icons);
                callbacks.applyState(access.state(wifi), wifi, () -> { access.apply(access.state(wifi), wifi); return null; });
                return null;
            }));
        } catch (Throwable failure) { throw new AssertionError(failure); }
        near(0f, wifi.getAlpha(), "a requested native alpha zero still applies at root commit");
        check(wifi.getVisibility() == View.INVISIBLE, "a requested native hidden state still applies at root commit");
        check(nativeState.calls == calls + 1, "a deferred real native lifecycle commits exactly once");
        flow.releaseRuntime();
    }
    private static void nativeCloudHideWithoutAnotherIconUpdate() throws Exception {
        Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
        ViewGroup root = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
        View wifi = (View) field(fixture, "wifi"); flow.configure(placement()); flow.afterLayout(root);
        Visual nativeState = access.visual(access.state(wifi)); StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try {
            callbacks.updateStates(icons, () -> {
                access.visible(access.state(wifi), 0); flow.prepareTargets(icons);
                access.visible(access.state(wifi), 2); nativeState.alpha = 0f; nativeState.hidden = true;
                callbacks.applyState(access.state(wifi), wifi, () -> { access.apply(access.state(wifi), wifi); return null; });
                return null;
            });
            callbacks.dispatch(root, false, () -> null);
        } catch (Throwable failure) { throw new AssertionError(failure); }
        check(access.visible(access.state(wifi)) == 2, "a root-only traversal after native cloud hide cannot force-promote its visibleState");
        near(0f, wifi.getAlpha(), "native cloud exact alpha zero remains zero until a real native update");
        check(wifi.getVisibility() == View.INVISIBLE, "native cloud View hidden state remains native");
        flow.releaseRuntime();
    }
    private static void width(ViewGroup root, int width) throws Exception {
        if (root instanceof KeyguardRoot) { ((KeyguardRoot) root).width = width; return; }
        Object geometry = field(root, "geometry");
        Field right = geometry.getClass().getDeclaredField("right"), measured = geometry.getClass().getDeclaredField("measuredWidth");
        right.setAccessible(true); measured.setAccessible(true);
        right.setInt(geometry, root.getLeft() + width); measured.setInt(geometry, width);
    }
    private static void nativeVisibility(StatusBarFlowLayout flow, StatusBarFlowCallbacks callbacks, Lifecycle access,
            ViewGroup icons, View wifi, boolean hidden) throws Throwable {
        callbacks.updateStates(icons, () -> {
            access.visible(access.state(wifi), 0); flow.prepareTargets(icons);
            Visual nativeState = access.visual(access.state(wifi)); nativeState.alpha = hidden ? 0f : 1f; nativeState.hidden = hidden;
            access.visible(access.state(wifi), hidden ? 2 : 0);
            callbacks.applyState(access.state(wifi), wifi, () -> { access.apply(access.state(wifi), wifi); return null; });
            return null;
        });
    }
    private static void keyguardAndPhoneRotationRecovery() throws Exception {
        for (boolean keyguard : new boolean[]{false, true}) {
            Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
            Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
            ViewGroup oldRoot = (ViewGroup) field(fixture, "root"), icons = (ViewGroup) field(fixture, "icons");
            View wifi = (View) field(fixture, "wifi");
            ViewGroup root = keyguard ? new KeyguardRoot(oldRoot.getResources()) : oldRoot;
            if (keyguard) for (String side : new String[]{"left", "right"}) {
                View view = (View) field(fixture, side); oldRoot.removeView(view); root.addView(view);
            }
            flow.configure(placement()); flow.afterLayout(root);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
            for (int frame = 0; frame < 50; frame++) try {
                nativeVisibility(flow, callbacks, access, icons, wifi, true);
                width(root, frame % 2 == 0 ? 260 : 400);
                callbacks.dispatch(root, false, () -> null);
                check(access.visible(access.state(wifi)) == 2, "width/orientation root transaction retains native hide, keyguard=" + keyguard);
                near(0f, wifi.getAlpha(), "width/orientation cannot resurrect native exact alpha zero");
                check(wifi.getVisibility() == View.INVISIBLE, "width/orientation cannot show a native hidden icon");
                if (frame % 2 == 0) nativeVisibility(flow, callbacks, access, icons, wifi, false);
                else callbacks.dispatch(root, false, () -> { nativeVisibility(flow, callbacks, access, icons, wifi, false); return null; });
                callbacks.dispatch(root, false, () -> null);
                check(access.visible(access.state(wifi)) == 0, "a real native show clears the prior coordinator hide, keyguard=" + keyguard);
                near(1f, wifi.getAlpha(), "native show restores alpha without retaining the old zero");
                check(wifi.getVisibility() == View.VISIBLE, "native show restores visibility without retaining the old hidden state");
            } catch (Throwable failure) { throw new AssertionError(failure); }
            flow.releaseRuntime();
        }
    }
    private static void missingGeometryLeavesNativeRoot() throws Exception {
        Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
        access.geometrySupported = false;
        ViewGroup root = (ViewGroup) field(fixture, "root"); View wifi = (View) field(fixture, "wifi");
        flow.configure(placement());
        Visual stale = access.visual(access.state(wifi)); stale.alpha = 1f;
        wifi.setAlpha(0f); wifi.setVisibility(View.INVISIBLE);
        float originalX = wifi.getTranslationX();
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try { callbacks.dispatch(root, false, () -> null); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        check("native_animation_unavailable".equals(flow.reason(root)), "a missing X-only contract exits Flow before publishing geometry");
        check(!StatusBarFlowLayout.owns(wifi), "a missing X-only contract acquires no Flow ownership");
        near(originalX, wifi.getTranslationX(), "a missing X-only contract keeps original native X");
        near(0f, wifi.getAlpha(), "a missing X-only contract cannot replay stale native alpha");
        check(wifi.getVisibility() == View.INVISIBLE && stale.calls == 0 && stale.geometry == 0,
                "a missing X-only contract applies neither full nor partial state to a root-only callback");
        // A real native callback still proceeds while this scene is declined.
        try { callbacks.dispatch(root, false, () -> { nativeVisibility(flow, callbacks, access, (ViewGroup) field(fixture, "icons"), wifi, false); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        check(stale.calls == 1, "declined Flow still permits each real native lifecycle once");
        near(1f, wifi.getAlpha(), "declined Flow still permits a real native show");
        flow.releaseRuntime();
    }
    private static void rootOnlyRenderedVisibility() throws Exception {
        Object fixture = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(fixture, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(fixture, "nativeAccess")); flow.setNativeAccess(access);
        ViewGroup root = (ViewGroup) field(fixture, "root"); View wifi = (View) field(fixture, "wifi");
        Visual visual = access.visual(access.state(wifi)); access.visible(access.state(wifi), 2); visual.renderedVisible = 2;
        wifi.setAlpha(.35f); wifi.setVisibility(View.INVISIBLE);
        flow.configure(placement()); flow.afterLayout(root);
        check(access.visible(access.state(wifi)) == 0, "initial custom placement promotes the native-overflow state");
        check(visual.renderedVisible == 0, "initial custom placement submits the promoted state to the native icon renderer");
        near(.35f, wifi.getAlpha(), "submitting native icon visibility cannot replay View alpha");
        check(wifi.getVisibility() == View.INVISIBLE && visual.calls == 0, "submitting native icon visibility cannot replay View hidden or full lifecycle");
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        for (int frame = 0; frame < 20; frame++) try {
            width(root, 15); callbacks.dispatch(root, false, () -> null);
            check(access.visible(access.state(wifi)) == 2 && visual.renderedVisible == 2, "root-only overflow hide commits actual renderer state");
            width(root, 300); callbacks.dispatch(root, false, () -> null);
            check(access.visible(access.state(wifi)) == 0 && visual.renderedVisible == 0, "root-only overflow recovery commits actual renderer state");
            near(.35f, wifi.getAlpha(), "renderer hide/show retains independent native View alpha");
            check(wifi.getVisibility() == View.INVISIBLE && visual.calls == 0, "renderer hide/show consumes no full native lifecycle");
        } catch (Throwable failure) { throw new AssertionError(failure); }
        flow.configure(new Bundle());
        check(visual.renderedVisible == access.visible(access.state(wifi)), "feature disable restores the native renderer visibility with its state");
        flow.releaseRuntime();
    }
    public static void main(String[] arguments) throws Exception {
        rootOnlyRenderedVisibility(); rootOnlyVisualState(); requestedNativeLifecycle(); nativeCloudHideWithoutAnotherIconUpdate(); keyguardAndPhoneRotationRecovery(); missingGeometryLeavesNativeRoot();
        System.out.println("StatusBarFlowVisualStateCheck: " + checks + " checks passed");
    }
}
