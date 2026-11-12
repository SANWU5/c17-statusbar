package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

/** Replays the inspected native updateStates -> applyToView -> next-QS-flag
 * order through the production root and container callbacks. */
public final class StatusBarFlowNativeLifecycleCheck {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result.get(owner); }
            catch (NoSuchFieldException inherited) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object rig() throws Exception {
        Constructor<?> constructor = Class.forName("dev.puitheme.StatusBarFlowLayoutCheck$Rig")
                .getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true); return constructor.newInstance(0, 300);
    }
    private static final class Flags {
        boolean qsTransitioning, justAdded = true, hidden;
        float alpha = 1;
        int calls, animations;
    }
    private static final class Lifecycle implements StatusBarFlowLayout.NativeAccess {
        final StatusBarFlowLayout.NativeAccess delegate;
        final IdentityHashMap<Object, Flags> values = new IdentityHashMap<>();
        Lifecycle(StatusBarFlowLayout.NativeAccess delegate) { this.delegate = delegate; }
        Flags flags(Object state) { return values.computeIfAbsent(state, key -> new Flags()); }
        public boolean container(View view) { return delegate.container(view); }
        public String slot(View view) { return delegate.slot(view); }
        public boolean eligible(ViewGroup parent, View child) { return delegate.eligible(parent, child); }
        public Object state(View view) { return delegate.state(view); }
        public float x(Object state) { return delegate.x(state); }
        public void x(Object state, float value) { delegate.x(state, value); }
        public int visible(Object state) { return delegate.visible(state); }
        public void visible(Object state, int value) { delegate.visible(state, value); }
        public boolean canRebase(Object state, View view, float x, float parent, float child) { return delegate.canRebase(state, view, x, parent, child); }
        public boolean rebase(Object state, View view, float x, float parent, float child) { return delegate.rebase(state, view, x, parent, child); }
        public void applyGeometry(Object state,View child){delegate.applyGeometry(state,child);}
        public void applyVisibility(Object state,View child){delegate.applyVisibility(state,child);}
        // Keep native caller flags separate from the real production geometry.
        public Object applyContext(Object state) { return flags(state).qsTransitioning; }
        public void apply(Object state, View child, Object context) {
            Flags flags = flags(state); boolean successor = flags.qsTransitioning;
            flags.qsTransitioning = (Boolean) context;
            try { apply(state, child); } finally { flags.qsTransitioning = successor; }
        }
        public void apply(Object state, View child) {
            Flags flags = flags(state); flags.calls++;
            // StatusIconState offset 0xaf: QS suppresses the selected native
            // animation; 0xef/0xf1 clear its consumed lifecycle flags.
            if (!flags.qsTransitioning) flags.animations++;
            child.setAlpha(flags.alpha); child.setVisibility(flags.hidden ? View.INVISIBLE : View.VISIBLE);
            flags.qsTransitioning = false; flags.justAdded = false;
            delegate.apply(state, child);
        }
    }
    private static Bundle gap() {
        Bundle result = new Bundle(); result.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12); return result;
    }
    private static void nativeCycle(Object rig, StatusBarFlowCallbacks callbacks, Lifecycle access, boolean nextTransition) throws Exception {
        nativeCycle(rig,callbacks,access,nextTransition,false);
    }
    private static void nativeCycle(Object rig, StatusBarFlowCallbacks callbacks, Lifecycle access, boolean nextTransition,boolean resetTargets) throws Exception {
        StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
        ViewGroup icons = (ViewGroup) field(rig, "icons");
        try {
            callbacks.updateStates(icons, () -> {
                for (String key : new String[]{"wifi", "mobile"}) {
                    View child = (View) field(rig, key); Object state = access.state(child); Flags flags = access.flags(state);
                    flags.alpha = 1; flags.hidden = false; // Native reset, before the Seedling boundary.
                    if(resetTargets)access.x(state,((Number)field(rig,key+"NativeX")).floatValue());
                }
                flow.prepareTargets(icons);
                for (String key : new String[]{"wifi", "mobile"}) {
                    View child = (View) field(rig, key); Object state = access.state(child);
                    callbacks.applyState(state, child, () -> { access.apply(state, child); return null; });
                    access.flags(state).qsTransitioning = nextTransition; // DEX updateStates 0x145.
                }
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static void lifecycle(boolean established, boolean previousTransition, boolean nextTransition) throws Exception {
        Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess"));
        flow.setNativeAccess(access); flow.configure(gap());
        if (established) flow.afterLayout((ViewGroup) field(rig, "root"));
        View wifi = (View) field(rig, "wifi"); Flags flags = access.flags(access.state(wifi));
        flags.qsTransitioning = previousTransition;
        int calls = flags.calls, animations = flags.animations;
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try {
            callbacks.dispatch((ViewGroup) field(rig, "root"), false, () -> {
                nativeCycle(rig, callbacks, access, nextTransition); return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
        check(flags.calls == calls + 1, "one native container update commits exactly one native lifecycle, including the first root layout");
        check(flags.animations == animations + (previousTransition ? 0 : 1), "root deferral consumes the same previous-QS flag as the original native apply");
        check(flags.qsTransitioning == nextTransition, "the flag written after the native call remains pending for the following update");
        check(!flags.justAdded && !flags.hidden && flags.alpha == 1 && wifi.getAlpha() == 1, "native add, hidden and alpha lifecycle remains native");
    }
    private static void closingFrames() throws Exception {
        Object rig=rig();StatusBarFlowLayout flow=(StatusBarFlowLayout)field(rig,"flow");
        Lifecycle access=new Lifecycle((StatusBarFlowLayout.NativeAccess)field(rig,"nativeAccess"));flow.setNativeAccess(access);
        Bundle values=new Bundle();values.putBoolean(StatusBarLayoutSettings.MASTER,true);
        values.putString(StatusBarLayoutSettings.key("wifi","side"),"left");values.putString(StatusBarLayoutSettings.key("battery","side"),"left");
        flow.configure(values);ViewGroup root=(ViewGroup)field(rig,"root");flow.afterLayout(root);
        View wifi=(View)field(rig,"wifi");Flags flags=access.flags(access.state(wifi));flags.qsTransitioning=true;
        float target=0;for(View at=wifi;at!=null;at=at.getParent() instanceof View?(View)at.getParent():null)target+=at.getLeft()+at.getTranslationX();
        StatusBarFlowCallbacks callbacks=new StatusBarFlowCallbacks(flow);
        for(int frame=0;frame<30;frame++){
            int calls=flags.calls,animations=flags.animations;boolean previous=flags.qsTransitioning,next=frame<15;
            try {
                if(frame%3==0){
                    callbacks.dispatch(root,true,()->null);
                    callbacks.dispatch(root,false,()->{nativeCycle(rig,callbacks,access,next,true);return null;});
                } else nativeCycle(rig,callbacks,access,next,true);
            }catch(Throwable failure){throw new AssertionError(failure);}
            check(flags.calls==calls+1,"closing frame commits native apply once across measure/layout/local states: "+frame);
            check(flags.animations==animations+(previous?0:1),"closing frame consumes original QS animation flag once: "+frame);
            check(flags.qsTransitioning==next,"closing frame keeps successor QS phase: "+frame);
            float x=0;for(View at=wifi;at!=null;at=at.getParent() instanceof View?(View)at.getParent():null)x+=at.getLeft()+at.getTranslationX();
            check(Math.abs(x-target)<.02f,"closing frame retains configured left-side Wi-Fi target: "+frame+" x="+x);
        }
        flow.releaseRuntime();
    }
    private static void unrelatedNetworkVisibility() throws Exception {
        Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
        StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess");
        View wifi = (View) field(rig, "wifi"); Object state = access.state(wifi);
        access.visible(state, 2); wifi.setAlpha(.35f);
        flow.configure(gap()); flow.afterLayout((ViewGroup) field(rig, "root"));
        check(access.visible(state) == 2, "a clock-only gap cannot promote a native-hidden radio to visible");
        check(wifi.getAlpha() == .35f, "a clock-only gap cannot replace a native animated alpha");
    }
    private static void unrelatedNetworkSpacing() throws Exception {
        for (boolean margins : new boolean[]{false, true}) for (int nativeInset : new int[]{0, 7}) {
            Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
            StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess");
            View wifi = (View) field(rig, "wifi"), mobile = (View) field(rig, "mobile");
            if (!margins) wifi.setLayoutParams(new ViewGroup.LayoutParams(20, 30));
            access.x(access.state(wifi), 120 - nativeInset); // Native five-pixel inter-icon spacing.
            access.x(access.state(mobile), 145 - nativeInset);
            View battery = (View) field(rig, "battery"); Object geometry = field(battery, "geometry");
            Field left = geometry.getClass().getDeclaredField("left"), right = geometry.getClass().getDeclaredField("right");
            left.setAccessible(true); right.setAccessible(true);
            left.setInt(geometry, 175 - nativeInset); right.setInt(geometry, 200 - nativeInset);
            flow.configure(gap()); flow.afterLayout((ViewGroup) field(rig, "root"));
            check(access.x(access.state(wifi)) == 120 - nativeInset && access.x(access.state(mobile)) == 145 - nativeInset
                            && battery.getLeft() == 175 - nativeInset,
                    "a clock-only gap retains unrelated native spacing, edge inset and targets with either native LP shape");
        }
    }
    private static void nativeFallback(boolean zeroWidth, boolean established) throws Exception {
        Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess"));
        flow.setNativeAccess(access); flow.configure(gap());
        ViewGroup root = (ViewGroup) field(rig, "root");
        if (established) flow.afterLayout(root);
        View wifi = (View) field(rig, "wifi"); Flags flags = access.flags(access.state(wifi));
        int calls = flags.calls, animations = flags.animations;
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        boolean aborted = false;
        try {
            callbacks.dispatch(root, false, () -> {
                nativeCycle(rig, callbacks, access, true);
                if (!zeroWidth) throw new IllegalStateException("native layout abort");
                Object geometry = field(root, "geometry"); Field right = geometry.getClass().getDeclaredField("right");
                right.setAccessible(true); right.setInt(geometry, 0); return null;
            });
        } catch (IllegalStateException expected) { aborted = true; }
        catch (Throwable failure) { throw new AssertionError(failure); }
        check(zeroWidth != aborted, "native abort is propagated while a zero-width callback returns normally");
        check(flags.calls == calls + 1, "an aborted or zero-width layout releases its queued native apply exactly once");
        check(flags.animations == animations + 1 && flags.qsTransitioning, "native fallback retains the original QS phase and its successor");
    }
    private static void absentNativeRadio() throws Exception {
        for (int visibility : new int[]{View.VISIBLE, View.INVISIBLE, View.GONE}) for (boolean zeroWidth : new boolean[]{false, true}) {
            Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
            StatusBarFlowLayout.NativeAccess access = (StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess");
            View wifi = (View) field(rig, "wifi"), mobile = (View) field(rig, "mobile");
            Field available = wifi.getClass().getDeclaredField("eligible"); available.setAccessible(true); available.setBoolean(wifi, false);
            wifi.setVisibility(visibility); access.visible(access.state(wifi), 2);
            if (zeroWidth) {
                Object geometry = field(wifi, "geometry");
                Field measured = geometry.getClass().getDeclaredField("measuredWidth"); measured.setAccessible(true); measured.setInt(geometry, 0);
            }
            flow.configure(gap()); flow.afterLayout((ViewGroup) field(rig, "root"));
            check(!StatusBarFlowLayout.owns(wifi), "a logically unavailable native radio never acquires a flow slot, regardless of animated visibility or stale measured width");
            check(access.x(access.state(mobile)) == 145 && access.visible(access.state(wifi)) == 2,
                    "both-off keeps the native signal against the battery without an absent Wi-Fi budget");
        }
    }
    private static void fullyNativeDefaults() throws Exception {
        Object rig = rig(); StatusBarFlowLayout flow = (StatusBarFlowLayout) field(rig, "flow");
        Lifecycle access = new Lifecycle((StatusBarFlowLayout.NativeAccess) field(rig, "nativeAccess"));
        flow.setNativeAccess(access); flow.configure(new Bundle());
        ViewGroup root = (ViewGroup) field(rig, "root"); View wifi = (View) field(rig, "wifi");
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(flow);
        try { callbacks.dispatch(root, false, () -> { nativeCycle(rig, callbacks, access, true); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        check(!flow.wanted() && "native".equals(flow.reason(root)) && !StatusBarFlowLayout.owns(wifi),
                "all-default settings retain the ordinary native callback without a flow allocation");
        check(access.flags(access.state(wifi)).calls == 1 && access.flags(access.state(wifi)).qsTransitioning,
                "disabled geometry callbacks leave native call count and its following QS flag intact");
    }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("network")) { unrelatedNetworkVisibility(); unrelatedNetworkSpacing(); }
        else if (arguments.length == 1 && arguments[0].equals("spacing")) unrelatedNetworkSpacing();
        else if (arguments.length == 1 && arguments[0].equals("fallback")) { nativeFallback(false, true); nativeFallback(true, true); }
        else if (arguments.length == 1 && arguments[0].equals("first")) lifecycle(false, false, false);
        else if (arguments.length == 1 && arguments[0].equals("qs")) lifecycle(true, false, true);
        else {
            closingFrames();
            unrelatedNetworkVisibility(); unrelatedNetworkSpacing(); absentNativeRadio(); fullyNativeDefaults();
            for (boolean established : new boolean[]{false, true}) { nativeFallback(false, established); nativeFallback(true, established); }
            for (boolean established : new boolean[]{false, true}) for (boolean previous : new boolean[]{false, true})
                for (boolean next : new boolean[]{false, true}) lifecycle(established, previous, next);
        }
        System.out.println("StatusBarFlowNativeLifecycleCheck: " + checks + " checks passed");
    }
}
