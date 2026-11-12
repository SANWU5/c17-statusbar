package dev.puitheme;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Integrates the production finite flow and native shared-slot bridge. Geometry
 * keeps measurement separate from layout, as Android does. The backend only
 * consumes production fragment targets; it contains no outer planning logic. */
public final class StatusBarSharedSlotFlowCheck {
    private static int checks, nextId = 1;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(float expected, float actual, String why) {
        check(Float.isFinite(actual) && Math.abs(expected - actual) < .02f, why + ": " + expected + " != " + actual);
    }
    private static final class NamedResources extends Resources {
        NamedResources() { getDisplayMetrics().density = 1; }
        int id(String name) { int result = nextId++; entryNames.put(result, name); return result; }
    }
    private static final class Rect {
        int left, top, right, bottom, measuredWidth, measuredHeight;
        int nativeLeft, nativeWidth;
        Rect(int left, int width) { nativeLeft = left; nativeWidth = width; reset(); }
        void layout(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
        void measure(int width, int height) { measuredWidth = View.resolveSize(nativeWidth, width); measuredHeight = View.resolveSize(30, height); }
        void reset() { layout(nativeLeft, 0, nativeLeft + nativeWidth, 30); measuredWidth = nativeWidth; measuredHeight = 30; }
        int width() { return right - left; }
    }
    private static final class Root extends PhoneStatusBarView {
        final NamedResources resources; final Rect rect; int direction;
        Root(NamedResources resources, int width) { this.resources = resources; rect = new Rect(0, width); }
        @Override public Resources getResources() { return resources; }
        @Override public int getLayoutDirection() { return direction; }
        @Override public int getLeft() { return rect.left; } @Override public int getRight() { return rect.right; }
        @Override public int getTop() { return rect.top; } @Override public int getBottom() { return rect.bottom; }
        @Override public int getWidth() { return rect.width(); } @Override public int getHeight() { return 30; }
        @Override public int getMeasuredWidth() { return rect.measuredWidth; } @Override public int getMeasuredHeight() { return rect.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { rect.layout(l, t, r, b); }
        @Override protected void onMeasure(int w, int h) { rect.measure(w, h); }
    }
    private static class Box extends ViewGroup {
        final NamedResources resources; final Rect rect;
        Box(NamedResources resources, String name, int left, int width) {
            super(null); this.resources = resources; rect = new Rect(left, width); setId(resources.id(name));
        }
        @Override public Resources getResources() { return resources; }
        @Override public int getLeft() { return rect.left; } @Override public int getRight() { return rect.right; }
        @Override public int getTop() { return rect.top; } @Override public int getBottom() { return rect.bottom; }
        @Override public int getWidth() { return rect.width(); } @Override public int getHeight() { return 30; }
        @Override public int getMeasuredWidth() { return rect.measuredWidth; } @Override public int getMeasuredHeight() { return rect.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { rect.layout(l, t, r, b); }
        @Override protected void onMeasure(int w, int h) { rect.measure(w, h); }
    }
    private static final class Container extends StatusIconContainer {
        final NamedResources resources; final Rect rect = new Rect(0, 175);
        Runnable nativeLayout; int nativeLayoutCalls;
        Container(NamedResources resources) { this.resources = resources; setId(resources.id("statusIcons")); }
        @Override public Resources getResources() { return resources; }
        @Override public int getLeft() { return rect.left; } @Override public int getRight() { return rect.right; }
        @Override public int getTop() { return rect.top; } @Override public int getBottom() { return rect.bottom; }
        @Override public int getWidth() { return rect.width(); } @Override public int getHeight() { return 30; }
        @Override public int getMeasuredWidth() { return rect.measuredWidth; } @Override public int getMeasuredHeight() { return rect.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) {
            boolean changed = rect.left != l || rect.right != r || rect.top != t || rect.bottom != b;
            rect.layout(l, t, r, b);
            if (changed && nativeLayout != null) { nativeLayoutCalls++; nativeLayout.run(); }
        }
        @Override protected void onMeasure(int w, int h) { rect.measure(w, h); }
    }
    private static class Leaf extends View {
        final NamedResources resources; final Rect rect; int translationWrites;
        Leaf(NamedResources resources, String name, int left, int width) {
            super(null); this.resources = resources; rect = new Rect(left, width); setId(resources.id(name));
        }
        @Override public Resources getResources() { return resources; }
        @Override public int getLeft() { return rect.left; } @Override public int getRight() { return rect.right; }
        @Override public int getTop() { return rect.top; } @Override public int getBottom() { return rect.bottom; }
        @Override public int getWidth() { return rect.width(); } @Override public int getHeight() { return 30; }
        @Override public int getMeasuredWidth() { return rect.measuredWidth; } @Override public int getMeasuredHeight() { return rect.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { rect.layout(l, t, r, b); }
        @Override protected void onMeasure(int w, int h) { rect.measure(w, h); }
        @Override public void setTranslationX(float value) { translationWrites++; super.setTranslationX(value); }
    }
    private static final class Icon extends Box implements StatusIconDisplayable {
        final String slot; final State state = new State(); int translations, nativeApplications, nativeRebases, nativeFrames; Backend backend;
        View nativeContent;
        Icon(NamedResources resources, String slot, int width, int nativeX) { super(resources, "native_" + slot, 0, width); this.slot = slot; state.x = nativeX; }
        @Override public String getSlot() { return slot; }
        @Override public boolean isIconVisible() { return getVisibility() == View.VISIBLE; }
        @Override public boolean isIconBlocked() { return false; }
        @Override public int getLayoutDirection() { View root = getRootView(); return root == this ? View.LAYOUT_DIRECTION_LTR : root.getLayoutDirection(); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, h); if (backend != null) backend.measure(); }
        @Override public void setTranslationX(float value) { translations++; super.setTranslationX(value); }
    }
    private static final class State { float x; int visible; }
    private static final class Native implements StatusBarFlowLayout.NativeAccess {
        public boolean container(View value) { return value instanceof Container; }
        public String slot(View value) { return value instanceof Icon ? ((Icon) value).slot : null; }
        public boolean eligible(ViewGroup parent, View child) { return child instanceof Icon && child.getVisibility() == View.VISIBLE; }
        public Object state(View value) { return value instanceof Icon ? ((Icon) value).state : null; }
        public float x(Object value) { return ((State) value).x; }
        public void x(Object value, float x) { ((State) value).x = x; }
        public int visible(Object value) { return ((State) value).visible; }
        public void visible(Object value, int visible) { ((State) value).visible = visible; }
        public void apply(Object value, View child) {
            Icon icon = (Icon) child; icon.nativeApplications++; child.setTranslationX(((State) value).x);
            // A native modern binding changes its content group separately
            // from the outer logical View visibility, as in the captured ZIP.
            if (icon.nativeContent != null) icon.nativeContent.setVisibility(((State) value).visible == 0 ? View.VISIBLE : View.INVISIBLE);
        }
        public boolean canRebase(Object value, View child, float dx, float parentWidthDelta, float childWidthDelta) { return true; }
        public boolean rebase(Object value, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            ((State) value).x += dx;
            if (dx != 0) { ((Icon) child).nativeRebases++; child.setTranslationX(child.getTranslationX() + dx); }
            return true;
        }
    }
    /** Replays the inspected OEM StatusIconState.applyToView X-animation seam:
     * old distanceToViewEnd seeds a real interpolated animation. In particular,
     * changing a target does not immediately make View.translationX that target.
     * Evidence: audit-speed131/StatusIconState.txt offsets 0x73..0x8f, 0xe8, 0xf3. */
    private static final class AnimatedNative implements StatusBarFlowLayout.NativeAccess {
        final Native delegate = new Native();
        final java.util.Map<Object, Float> distances = new java.util.IdentityHashMap<>();
        final java.util.Map<Icon, float[]> animations = new java.util.IdentityHashMap<>();
        int mobileRebaseCalls, ignoreMobileRebase;
        boolean rejectMobileRebase;
        float expectedMobileAnchor = Float.NaN;
        public boolean container(View view) { return delegate.container(view); }
        public String slot(View view) { return delegate.slot(view); }
        public boolean eligible(ViewGroup parent, View child) { return delegate.eligible(parent, child); }
        public Object state(View view) { return delegate.state(view); }
        public float x(Object value) { return delegate.x(value); }
        public void x(Object value, float x) { delegate.x(value, x); }
        public int visible(Object value) { return delegate.visible(value); }
        public void visible(Object value, int visible) { delegate.visible(value, visible); }
        public void apply(Object value, View child) {
            Icon icon = (Icon) child; View parent = (View) child.getParent();
            if ("mobile".equals(icon.slot) && Float.isFinite(expectedMobileAnchor))
                near(expectedMobileAnchor, screenX(icon.backend.signal), "native animator handoff already has the correct physical signal before applying its next target");
            boolean rtl = child.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            float target = x(value), distance = rtl ? target : parent.getWidth() - target - child.getWidth();
            Float previous = distances.put(value, distance);
            float[] existing = animations.get(icon);
            if (previous != null && Math.abs(previous - distance) < .001f && existing != null && existing[2] < 1f) {
                // Actual ViewState.applyToView uses NO_NEW_ANIMATIONS here:
                // an existing curve is TRANSLATED, not retargeted from current.
                float delta = target - existing[1]; existing[0] += delta; existing[1] = target;
                icon.nativeApplications++; child.setTranslationX(child.getTranslationX() + delta); return;
            }
            float start = previous == null || Math.abs(previous - distance) < .001f ? target : rtl ? previous : target - (previous - distance);
            animations.put(icon, new float[]{start, target, 0});
            icon.nativeApplications++; child.setTranslationX(start);
        }
        public boolean canRebase(Object value, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            return !(rejectMobileRebase && "mobile".equals(((Icon) child).slot));
        }
        public void retargetFromCurrent(Object value, View child) {
            float[] tween = animations.get((Icon) child);
            if (tween != null && tween[1] != x(value)) distances.put(value,
                    child.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? child.getTranslationX()
                    : ((View) child.getParent()).getWidth() - child.getTranslationX() - child.getWidth());
        }
        public boolean rebase(Object value, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            Icon icon = (Icon) child;
            if (!canRebase(value, child, dx, parentWidthDelta, childWidthDelta)) return false;
            if ("mobile".equals(icon.slot) && (dx != 0 || parentWidthDelta != 0 || childWidthDelta != 0)
                    && ++mobileRebaseCalls == ignoreMobileRebase) return true;
            x(value, x(value) + dx);
            Float distance = distances.get(value);
            if (distance != null) distances.put(value, distance + (child.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? dx : parentWidthDelta - dx - childWidthDelta));
            float[] tween = animations.get(icon);
            if (tween != null) { tween[0] += dx; tween[1] += dx; }
            if (dx != 0) { icon.nativeRebases++; child.setTranslationX(child.getTranslationX() + dx); }
            return true;
        }
        void frame(float fraction) {
            for (java.util.Map.Entry<Icon, float[]> entry : animations.entrySet()) {
                float[] tween = entry.getValue(); tween[2] = fraction;
                entry.getKey().nativeFrames++;
                entry.getKey().setTranslationX(tween[0] + fraction * (tween[1] - tween[0]));
            }
        }
    }
    private static final class Backend implements NetworkSharedSlotLayout.Backend {
        final Icon host; final Leaf label, signal; boolean present, valid = true, accept = true, labelBefore = true;
        NetworkSharedSlotLayout.Target target; int targets, restores, labelWidth = 12;
        int hullDepth, ownedMeasures, restoredMeasures;
        Backend(Icon host, Leaf label, Leaf signal) { this.host = host; this.label = label; this.signal = signal; }
        public NetworkSharedSlotLayout.Frame frame(View actual) {
            return new NetworkSharedSlotLayout.Frame(actual, this, valid, present, present ? labelWidth : 0, 18,
                    present && !labelBefore ? 18 : 0, present && labelBefore ? labelWidth : 0,
                    present ? labelWidth + 18 : 18, signal.getLeft());
        }
        public boolean target(NetworkSharedSlotLayout.Target value) { if (!accept) return false; target = value; targets++; return true; }
        public void beginHullMeasure() { hullDepth++; }
        public void endHullMeasure() { check(hullDepth > 0, "hull measure scopes remain balanced"); hullDepth--; }
        void measure() {
            check(hullDepth > 0, "flow-owned exact measure does not become a real native content constraint");
            ownedMeasures++;
            if (target == null) restoredMeasures++;
            if (target == null) return;
            label.layout(target.labelLeft, 0, target.labelLeft + labelWidth, 30);
            signal.layout(target.signalLeft, 0, target.signalLeft + 18, 30);
        }
        /** A native fragment measure may finish while the host retains its old EXACT width. */
        void captureNativeFragments(boolean nextPresent, int nextLabelWidth) {
            if (target != null && target.labelVisible != nextPresent) target = null;
            present = nextPresent; labelWidth = nextLabelWidth;
            host.rect.nativeWidth = present ? labelWidth + 18 : 18;
            label.rect.nativeWidth = present ? labelWidth : 0;
            signal.rect.nativeLeft = present && labelBefore ? labelWidth : 0;
            label.rect.nativeLeft = present && !labelBefore ? 18 : 0;
            label.setVisibility(present ? View.VISIBLE : View.GONE);
            signal.rect.reset(); label.rect.reset();
        }
        public void restore() { target = null; restores++; label.rect.reset(); signal.rect.reset(); }
    }
    private static final class Rig {
        final NamedResources resources = new NamedResources();
        final Root root; final Box left, right, clockHost;
        final Container icons = new Container(resources);
        final Leaf clock = new Leaf(resources, "clock", 0, 60), battery = new Leaf(resources, "battery", 175, 25);
        final Icon wifi = new Icon(resources, "wifi", 20, 125), mobile = new Icon(resources, "mobile", 18, 145);
        final Leaf label = new Leaf(resources, "mobile_type_text", 0, 12), signal = new Leaf(resources, "mobile_signal", 12, 18);
        final Backend backend = new Backend(mobile, label, signal);
        final Native nativeAccess = new Native();
        final StatusBarFlowLayout flow = new StatusBarFlowLayout();
        final NetworkSharedSlotLayout bridge = new NetworkSharedSlotLayout();
        final List<View> views = new ArrayList<>(); final List<Object> parents = new ArrayList<>(), params = new ArrayList<>();
        float wifiNativeX = 125, mobileNativeX = 145;
        Rig(int width) throws Exception { this(width, false); }
        Rig(int width, boolean nativeNetworksLeft) throws Exception {
            int division = nativeNetworksLeft ? 150 : 100;
            root = new Root(resources, width); left = new Box(resources, "status_bar_start_side_container", 0, division);
            right = new Box(resources, "status_bar_end_side_container", division, Math.max(0, width - division));
            clockHost = new Box(resources, "clock_for_fake", 0, 60);
            root.addView(left); root.addView(right); left.addView(clockHost); clockHost.addView(clock);
            (nativeNetworksLeft ? left : right).addView(icons); right.addView(battery);
            if (nativeNetworksLeft) {
                wifiNativeX = 60; mobileNativeX = 80; icons.rect.nativeWidth = 100; icons.rect.reset();
                battery.rect.nativeLeft = 125; battery.rect.reset();
            }
            icons.addView(wifi); icons.addView(mobile); mobile.addView(label); mobile.addView(signal);
            mobile.backend = backend;
            views.addAll(Arrays.asList(left, right, clockHost, icons, clock, battery, wifi, mobile, label, signal));
            for (View view : views) { ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(view.getWidth(), 30); view.setLayoutParams(lp); parents.add(view.getParent()); params.add(lp); }
            bridge.resolve(StatusBarSharedSlotFlowCheck.class.getClassLoader()); bridge.configure(true); bridge.register(mobile, backend);
            flow.setNativeAccess(nativeAccess); flow.setSharedSlots(bridge); mode(true);
        }
        void mode(boolean wifiOn) {
            wifi.setVisibility(wifiOn ? View.VISIBLE : View.GONE); backend.present = !wifiOn;
            mobile.rect.nativeWidth = wifiOn ? 18 : 30;
            signal.rect.nativeLeft = !wifiOn && backend.labelBefore ? 12 : 0;
            label.rect.nativeLeft = !wifiOn && !backend.labelBefore ? 18 : 0;
            label.setVisibility(wifiOn ? View.GONE : View.VISIBLE);
        }
        void pass() { pass(flow); }
        void pass(StatusBarFlowLayout coordinator) {
            coordinator.beforeNativeLayout(root);
            root.rect.reset();
            for (View value : views) if (value instanceof Box) ((Box) value).rect.reset();
                else if (value instanceof Container) ((Container) value).rect.reset(); else ((Leaf) value).rect.reset();
            wifi.state.x = wifiNativeX; mobile.state.x = mobileNativeX;
            coordinator.afterMeasure(root); coordinator.afterLayout(root);
        }
        void ownership() {
            near(0, backend.hullDepth, "native content constraints are restored outside each flow measure");
            for (int i = 0; i < views.size(); i++) { check(views.get(i).getParent() == parents.get(i), "native parent retained"); check(views.get(i).getLayoutParams() == params.get(i), "native LP identity retained"); }
            check(icons.getChildAt(0) == wifi && icons.getChildAt(1) == mobile, "IconManager native child order retained");
            near(wifi.nativeApplications + wifi.nativeRebases + wifi.nativeFrames, wifi.translations, "only native apply/rebase/animator seams write Wi-Fi animation translation");
            near(mobile.nativeApplications + mobile.nativeRebases + mobile.nativeFrames, mobile.translations, "only native apply/rebase/animator seams write mobile animation translation");
            near(0, clock.translationWrites + battery.translationWrites + label.translationWrites + signal.translationWrites, "shared layout never adds View drawing translations");
        }
    }
    private static Bundle settings(String side, boolean order) { return settings(side, side, order); }
    private static Bundle settings(String side, String signalSide, boolean order) {
        Bundle result = new Bundle(); result.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED, true);
        result.putBoolean(StatusBarLayoutSettings.MASTER, order);
        result.putString(StatusBarLayoutSettings.key("wifi", "side"), side);
        result.putString(StatusBarLayoutSettings.key("mobile", "side"), signalSide);
        result.putInt(StatusBarLayoutSettings.key("wifi", "order"), 1);
        result.putInt(StatusBarLayoutSettings.key("mobile", "order"), 2);
        result.putInt(StatusBarLayoutSettings.key("clock", "order"), 3);
        result.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), 7);
        result.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), 9);
        return result;
    }
    private static float screenX(View value) {
        float result = 0;
        for (View current = value; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
            result += current.getLeft() + current.getTranslationX();
        return result;
    }
    private static void switching(String side, int direction, boolean order) throws Exception { switching(side, side, direction, order); }
    private static void switching(String side, String signalSide, int direction, boolean order) throws Exception {
        Rig rig = new Rig(300); rig.root.direction = direction; rig.flow.configure(settings(side, signalSide, order));
        rig.pass(); check("ready".equals(rig.flow.reason(rig.root)), "Wi-Fi native scene ready");
        float center = screenX(rig.wifi) + 10, signalStart = screenX(rig.mobile), batteryStart = screenX(rig.battery);
        rig.mode(false); rig.pass(); check("ready".equals(rig.flow.reason(rig.root)), "data split scene ready");
        check(rig.backend.target != null, "production flow reaches measured native backend");
        near(center, screenX(rig.label) + 6, "Wi-Fi and label occupy one logical slot center on network switch");
        near(signalStart, screenX(rig.signal), "signal uses its own budget once, never counting the whole label host twice");
        near(batteryStart, screenX(rig.battery), "splitting label never shifts unrelated battery budget");
        check(screenX(rig.label) + 12 <= screenX(rig.signal) || screenX(rig.signal) + 18 <= screenX(rig.label), "visible fragments never overlap");
        rig.ownership();
        for (int i = 0; i < 12; i++) { rig.pass(); near(center, screenX(rig.label) + 6, "repeated native layouts have no shared-slot drift"); }
        rig.mode(true); rig.pass(); near(center, screenX(rig.wifi) + 10, "returning to Wi-Fi preserves the slot and margins"); rig.ownership();
    }
    private static void sharedHorizontalPosition() throws Exception {
        for(String side:new String[]{"left","right"})for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}) {
            Rig rig=new Rig(300);rig.root.direction=direction;Bundle raw=settings(side,"right",true);
            raw.putBoolean("wifi_enabled",true);raw.putBoolean("label_enabled",true);
            rig.flow.configure(raw);rig.pass();
            float center=screenX(rig.wifi)+10,signal=screenX(rig.mobile);
            raw.putFloat(StatusBarSettings.WIFI_OFFSET_X,6f);raw.putFloat(StatusBarSettings.LABEL_OFFSET_X,-18);
            rig.flow.configure(raw);rig.pass();
            near(center+6f,screenX(rig.wifi)+10,"shared X moves visible Wi-Fi once through native layout");
            near(signal,screenX(rig.mobile),"shared X leaves independent signal budget fixed");
            rig.mode(false);rig.pass();
            near(center+6f,screenX(rig.label)+6,"network switch inherits the identical shared horizontal center");
            near(signal,screenX(rig.signal),"network switch retains independent signal position with shared X");
            for(int i=0;i<4;i++){rig.pass();near(center+6f,screenX(rig.label)+6,"shared X remains stable after repeated native layout");}
            rig.mode(true);rig.pass();near(center+6f,screenX(rig.wifi)+10,"returning Wi-Fi retains shared X");
            raw.putBoolean("wifi_position_enabled",false);rig.flow.configure(raw);rig.pass();
            near(center,screenX(rig.wifi)+10,"closing common position restores the unshifted slot");rig.ownership();
        }
    }
    private static void sharedHorizontalMasters() throws Exception {
        for(int masters=0;masters<4;masters++)for(boolean wifiOn:new boolean[]{false,true}) {
            Rig rig=new Rig(300);Bundle raw=settings("left","right",true);
            raw.putBoolean("wifi_enabled",(masters&1)!=0);raw.putBoolean("label_enabled",(masters&2)!=0);
            rig.mode(wifiOn);rig.flow.configure(raw);rig.pass();
            View content=wifiOn?rig.wifi:rig.label;float center=screenX(content)+content.getWidth()/2f;
            raw.putFloat(StatusBarSettings.WIFI_OFFSET_X,6f);rig.flow.configure(raw);rig.pass();
            boolean ownMaster=wifiOn?(masters&1)!=0:(masters&2)!=0;
            near(center+(ownMaster?6f:0f),screenX(content)+content.getWidth()/2f,"shared X honors the visible endpoint's independent customization master");
            rig.ownership();
        }
    }
    static void measurementRegression() throws Exception {
        Rig rig = new Rig(300); Bundle gap = new Bundle(); gap.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        rig.flow.configure(gap); rig.flow.afterMeasure(rig.root);
        near(175, rig.icons.getMeasuredWidth(), "clock-only gap never forces unrelated native icon measure to full root width");
        rig.flow.afterLayout(rig.root); rig.flow.configure(new Bundle());
        near(175, rig.icons.getMeasuredWidth(), "disable retains native measured width independently of View.layout");
    }
    private static void nativeAnimation() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); rig.flow.configure(settings("left", "right", true)); rig.pass();
        float original = screenX(rig.label); float planned = rig.mobile.state.x;
        rig.mobile.state.x += 9;
        rig.flow.beforeApply(rig.mobile.state, rig.mobile); rig.nativeAccess.apply(rig.mobile.state, rig.mobile);
        near(original + 9, screenX(rig.label), "native animation displacement remains shared by both fragments");
        near(planned + 9, rig.mobile.state.x, "repeated native apply preserves the animated state target");
        rig.flow.configure(new Bundle()); near(154, rig.mobile.state.x, "disable subtracts its layout basis and retains native nine-pixel animation"); rig.ownership();
    }
    private static void overflow() throws Exception {
        Rig rig = new Rig(105); rig.mode(false); rig.flow.configure(settings("right", true)); rig.pass();
        check("width_overflow".equals(rig.flow.reason(rig.root)) || "shared_layout_unavailable".equals(rig.flow.reason(rig.root)),
                "narrow rows explicitly report finite overflow or a safe backend fallback");
        if (rig.backend.target != null) {
            NetworkSharedSlotLayout.Target target = rig.backend.target;
            if (target.labelVisible) check(target.labelLeft >= 0 && target.labelLeft + 12 <= target.width, "visible overflow label stays inside its finite hull");
            if (target.signalVisible) check(target.signalLeft >= 0 && target.signalLeft + 18 <= target.width, "visible overflow signal stays inside its finite hull");
        }
        near(1, rig.mobile.getAlpha(), "overflow does not rewrite native animated alpha"); rig.ownership();
    }
    private static void disableAndMeasure() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); rig.flow.configure(settings("left", true)); rig.pass();
        check(rig.mobile.getWidth() > 30, "crossing fragments really resizes the existing host hull");
        rig.flow.configure(new Bundle());
        near(30, rig.mobile.getWidth(), "disable restores native mobile host layout width");
        near(30, rig.mobile.getMeasuredWidth(), "disable restores native mobile host measured width");
        near(175, rig.icons.getMeasuredWidth(), "relative flow never leaves a full-root measured icon width");
        check(rig.backend.target == null, "disable restores backend placement"); rig.ownership();
    }
    private static void unresolvedSharedHostHeight() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); rig.flow.configure(settings("left", true));
        rig.mobile.rect.reset(); rig.mobile.rect.measuredHeight = 0;
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check(rig.backend.targets == 0 && rig.backend.target == null,
                "native fragment width cannot authorize an EXACTLY 0px Android host height");
        near(0, rig.mobile.getMeasuredHeight(), "a pending native height is not fabricated by the outer shared flow");
        rig.pass();
        check("ready".equals(rig.flow.reason(rig.root)) && rig.backend.target != null,
                "the next genuine native host measure enables the same shared layout without polling");
        near(30, rig.mobile.getMeasuredHeight(), "the shared hull keeps the newly measured native height");
    }
    private static void restoringOwnedWidthKeepsNativeHeight() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); rig.flow.configure(settings("left", true)); rig.pass();
        check(rig.mobile.getWidth() > 30, "the production shared bridge owns a real widened Android host");
        // A native font/configuration pass may replace only the height before a
        // settings refresh restores the independently owned horizontal hull.
        rig.mobile.rect.measuredHeight = 40;
        rig.flow.configure(new Bundle());
        near(30, rig.mobile.getMeasuredWidth(), "width restoration returns the native thirty-pixel host");
        near(40, rig.mobile.getMeasuredHeight(), "width restoration never rewinds a newer native measured height");
    }
    private static void fallback() throws Exception {
        for (boolean refuse : new boolean[]{false, true}) {
            Rig rig = new Rig(300); rig.mode(false); rig.backend.valid = refuse; rig.backend.accept = !refuse;
            Bundle values = settings("native", false); values.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
            rig.flow.configure(values); rig.pass();
            if (refuse) { check("shared_layout_unavailable".equals(rig.flow.reason(rig.root)), "backend refusal falls back only the shared fragment split"); near(30, rig.mobile.getWidth(), "failed shared transaction leaves native host width"); }
            else { check("ready".equals(rig.flow.reason(rig.root)), "unmeasured backend keeps whole native mobile component"); check(rig.backend.target == null, "invalid frame never receives speculative fragment layout"); }
            near(12, screenX(rig.clock), "unsupported shared backend never disables an independent clock margin");
            near(30, rig.mobile.getWidth(), "native whole mobile host width remains intact during shared fallback");
            check(rig.backend.target == null && rig.label.getVisibility() == View.VISIBLE,
                    "shared fallback retains native label visibility without a speculative fragment target");
            rig.ownership();
        }
    }
    private static void signedMargins() throws Exception {
        for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL})
            for (String side : new String[]{"left", "right"}) {
                Rig rig = new Rig(300); rig.root.direction = direction;
                Bundle values = settings(side, true);
                values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -5);
                values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -7);
                values.putFloat(StatusBarLayoutSettings.key("mobile", "margin_left"), -3);
                values.putFloat(StatusBarLayoutSettings.key("mobile", "margin_right"), -2);
                rig.flow.configure(values); rig.pass();
                check("ready".equals(rig.flow.reason(rig.root)), "negative shared Wi-Fi gaps remain an active native flow");
                float center = screenX(rig.wifi) + 10, signalX = screenX(rig.mobile);
                rig.mode(false); rig.pass();
                check("ready".equals(rig.flow.reason(rig.root)) && rig.backend.target != null, "negative shared label gaps produce a real backend target: " + side + "/" + direction + "/" + rig.flow.reason(rig.root));
                near(center, screenX(rig.label) + 6, "signed physical margins stay shared across Wi-Fi/data and LTR/RTL");
                near(signalX, screenX(rig.signal), "signed label gaps reserve their reduced width once");
                check(screenX(rig.label) < screenX(rig.signal) + 18 && screenX(rig.signal) < screenX(rig.label) + 12,
                        "user-requested negative gaps deliberately permit local label/signal overlap");
                near(-5, ((Number) values.get(StatusBarLayoutSettings.key("wifi", "margin_left"))).floatValue(), "native rendering never rewrites a saved negative shared gap");
                rig.ownership();
                for (int cycle = 0; cycle < 8; cycle++) { rig.pass(); near(center, screenX(rig.label) + 6, "negative shared gaps have no accumulating layout feedback"); }
                rig.mode(true); rig.pass(); near(center, screenX(rig.wifi) + 10, "returning to Wi-Fi retains the same signed shared slot");
                rig.flow.configure(new Bundle());
                near(18, rig.mobile.getWidth(), "disabling signed shared gaps restores native Wi-Fi-mode host width");
                rig.ownership();
            }
    }
    private static void oppositeNativeOrder() throws Exception {
        for (boolean nativeNetworksLeft : new boolean[]{false, true})
            for (boolean wifiBefore : new boolean[]{false, true})
                for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
                    Rig rig = new Rig(300, nativeNetworksLeft); rig.root.direction = direction;
                    if (!wifiBefore) { float first = rig.wifiNativeX; rig.wifiNativeX = rig.mobileNativeX; rig.mobileNativeX = first; }
                    // The OEM's data text order deliberately opposes its original
                    // Wi-Fi slot. Native row order must not replace that slot.
                    rig.backend.labelBefore = !wifiBefore;
                    rig.flow.configure(settings("native", false)); rig.pass();
                    float center = screenX(rig.wifi) + 10, signalX = screenX(rig.mobile);
                    check(wifiBefore ? screenX(rig.wifi) < signalX : screenX(rig.wifi) > signalX,
                            "fixture exposes both physical native Wi-Fi orders without custom sorting");
                    rig.mode(false); rig.pass();
                    check("ready".equals(rig.flow.reason(rig.root)) && rig.backend.target != null,
                            "opposing OEM data text order still reaches a measured shared slot");
                    near(center, screenX(rig.label) + 6, "canonical Wi-Fi order wins over the opposite OEM text order in either native region");
                    near(signalX, screenX(rig.signal), "opposite OEM text order does not exchange the signal slot");
                    for (int cycle = 0; cycle < 6; cycle++) { rig.pass(); near(center, screenX(rig.label) + 6, "canonical neighbor ordering never drifts across native layouts"); }
                    rig.mode(true); rig.pass(); near(center, screenX(rig.wifi) + 10, "native Wi-Fi returns to its canonical region and order"); rig.ownership();
                }
    }
    private static java.util.Map<?, ?> neighborCache(StatusBarFlowLayout flow) throws Exception {
        java.lang.reflect.Field field = StatusBarFlowLayout.class.getDeclaredField("wifiNeighbors"); field.setAccessible(true);
        return (java.util.Map<?, ?>) field.get(flow);
    }
    private static void neighborLifecycle() throws Exception {
        Rig first = new Rig(300), second = new Rig(300, true);
        first.backend.labelBefore = false; // Wi-Fi before signal, OEM text after.
        float original = second.wifiNativeX; second.wifiNativeX = second.mobileNativeX; second.mobileNativeX = original;
        second.backend.labelBefore = true; // Wi-Fi after signal, OEM text before.
        first.bridge.register(second.mobile, second.backend);
        first.flow.configure(settings("native", false)); first.pass(); float firstCenter = screenX(first.wifi) + 10;
        second.pass(first.flow); float secondCenter = screenX(second.wifi) + 10;
        java.util.Map<?, ?> cache = neighborCache(first.flow);
        check(cache instanceof java.util.WeakHashMap && cache.size() == 2, "neighbor cache keys are weak and distinct for each native root");
        for (Object neighbors : cache.values()) for (java.lang.reflect.Field field : neighbors.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            check(field.get(neighbors) instanceof java.lang.ref.WeakReference,
                    "cached neighbors cannot retain a native View/parent/root through a strong reference cycle");
        }
        first.mode(false); second.mode(false); first.pass(); second.pass(first.flow);
        near(firstCenter, screenX(first.label) + 6, "one root retains its own canonical ordering");
        near(secondCenter, screenX(second.label) + 6, "a second root cannot borrow the first root's neighbor identities");
        first.flow.detached(first.root);
        check(!cache.containsKey(first.root) && cache.containsKey(second.root), "detach discards only that root's canonical neighbors");
        check(first.backend.target == null, "detach restores the detached shared host backend");
        first.flow.releaseRuntime();
        check(cache.isEmpty() && second.backend.target == null, "release clears remaining weak neighbors and restores native shared placement");
        first.ownership(); second.ownership();
    }
    private static void animatedNativeParentRegression(boolean reverse) throws Exception { animatedNativeParentRegression(reverse, true); }
    private static void animatedNativeParentRegression(boolean reverse, boolean parentMoves) throws Exception {
        animatedNativeParentRegression(reverse, parentMoves, 0);
    }
    private static void animatedNativeParentRegression(boolean reverse, boolean parentMoves, int ignoredRebase) throws Exception {
        Rig rig = new Rig(300); AnimatedNative nativeAnimation = new AnimatedNative();
        rig.flow.setNativeAccess(nativeAnimation); rig.flow.configure(settings("right", true));
        rig.pass(); nativeAnimation.frame(1);
        if (reverse) {
            if (parentMoves) { rig.icons.rect.nativeLeft = 88; rig.icons.rect.nativeWidth = 87; }
            rig.mode(false); rig.pass(); nativeAnimation.frame(1);
        }
        float stableSignal = screenX(rig.signal);
        nativeAnimation.mobileRebaseCalls = 0; nativeAnimation.ignoreMobileRebase = ignoredRebase;
        rig.mode(reverse); rig.flow.beforeNativeLayout(rig.root);
        // Native right-aligned container consumes the Wi-Fi disappearance. It
        // moves from x=100,width175 to x=188,width87, retaining its end=275.
        // Do not reset the whole rig or snap its native animation after this.
        rig.icons.rect.layout(parentMoves && !reverse ? 88 : 0, 0, 175, 30);
        rig.icons.rect.measuredWidth = parentMoves && !reverse ? 87 : 175;
        rig.mobile.rect.reset(); rig.label.rect.reset(); rig.signal.rect.reset();
        rig.flow.beforeStates(rig.icons);
        near(stableSignal, screenX(rig.signal), "native coordinate rebase retains the current physical signal before any new targets are applied");
        rig.mobile.state.x = reverse ? 157 : parentMoves ? 57 : 145; // native signal [257,275] in either mode
        rig.flow.prepareTargets(rig.icons);
        try { new StatusBarFlowCallbacks(rig.flow).applyState(rig.mobile.state, rig.mobile,
                () -> { nativeAnimation.apply(rig.mobile.state, rig.mobile); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        near(stableSignal, screenX(rig.signal), "OEM native layout retains the physical signal position before the outer callback finishes");
        nativeAnimation.expectedMobileAnchor = stableSignal;
        rig.flow.afterLayout(rig.root);
        for (float fraction : new float[]{0, .125f, .25f, .5f, .75f, 1}) {
            nativeAnimation.frame(fraction);
            near(stableSignal, screenX(rig.signal), "native frame/semantic offset changes never seed an extra animation (reverse="
                    + reverse + ", parentMoves=" + parentMoves + ") at fraction " + fraction);
        }
        if (!reverse) check(nativeAnimation.mobileRebaseCalls >= 2, "native layout and final shared hull both rebase their separate coordinate changes");
    }
    private static void bothRebaseStagesRequired() throws Exception {
        for (int stage : new int[]{1, 2}) {
            boolean caught = false;
            try { animatedNativeParentRegression(false, true, stage); }
            catch (AssertionError missingCoordinateChange) { caught = true; }
            check(caught, "omitting native rebase stage " + stage + " must expose the original interpolated position failure");
        }
    }
    private static void returningWifiBackend() throws Exception {
        Rig rig = new Rig(300); rig.flow.configure(settings("right", true)); rig.pass();
        rig.mode(false); rig.pass(); check(rig.backend.target != null, "data split owns its real native fragment target");
        rig.mode(true); rig.pass();
        check(rig.backend.target == null, "returning native Wi-Fi restores the previous data-fragment backend before another native measurement");
    }
    /** Captured code154 pattern: the native label is already zero-width while
     * its Compose host still has the former separated label/signal EXACT hull.
     * Android may next dispatch onLayout without measuring that host again. */
    private static void returningWifiWithoutHostMeasure() throws Exception {
        Rig rig = new Rig(300);
        Bundle values = settings("right", true);
        values.putInt(StatusBarLayoutSettings.key("wifi", "order"), 4);
        values.putInt(StatusBarLayoutSettings.key("battery", "order"), 6);
        values.putInt(StatusBarLayoutSettings.key("mobile", "order"), 7);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -1);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -4);
        rig.flow.configure(values); rig.mode(false); rig.pass();
        int hull = rig.mobile.getMeasuredWidth();
        check(hull > 30 && rig.backend.target != null, "custom order really separates the shared label from signal across battery");
        rig.wifi.setVisibility(View.VISIBLE);
        rig.backend.captureNativeFragments(false, 0);
        near(hull, rig.mobile.getMeasuredWidth(), "captured natural fragment update leaves the former EXACT host measurement in place");
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        try { callbacks.dispatch(rig.root, false, () -> { callbackNativeCycle(rig, callbacks, 2, false); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        near(18, rig.mobile.getMeasuredWidth(), "layout-only Wi-Fi return releases the proven former shared host's obsolete EXACT hull");
        near(18, rig.mobile.getWidth(), "native host layout follows its current measured signal-only width");
        near(0, rig.wifi.state.visible, "current logical Wi-Fi is not hidden by the obsolete shared hull's overflow budget");
        check(rig.backend.target == null, "Wi-Fi return keeps the original native backend released");
        for (int cycle = 0; cycle < 4; cycle++) {
            try { callbacks.dispatch(rig.root, false, () -> { callbackNativeCycle(rig, callbacks, 2, false); return null; }); }
            catch (Throwable failure) { throw new AssertionError(failure); }
            near(18, rig.mobile.getMeasuredWidth(), "settled layout-only callbacks cannot restore the former hull");
            near(0, rig.wifi.state.visible, "settled layout-only callbacks retain the real Wi-Fi allocation");
        }
    }
    private static void staleSharedHullMustNotHideWifi() throws Exception {
        Rig rig = new Rig(250); rig.mode(false);
        Leaf group = new Leaf(rig.resources, "wifi_group", 0, 20);
        rig.wifi.nativeContent = group; rig.wifi.addView(group);
        Bundle values = settings("right", true);
        values.putInt(StatusBarLayoutSettings.key("wifi", "order"), 4);
        values.putInt(StatusBarLayoutSettings.key("battery", "order"), 6);
        values.putInt(StatusBarLayoutSettings.key("mobile", "order"), 7);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -1);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -4);
        rig.flow.configure(values);
        rig.mobileNativeX = 125; rig.wifiNativeX = 145;
        rig.mobile.state.x = rig.mobileNativeX; rig.wifi.state.x = rig.wifiNativeX;
        // Replay a host whose last Android baseline itself still came from a
        // preceding separated shared hull. Its real Row frame remains 30px.
        rig.mobile.rect.measuredWidth = 160; rig.mobile.rect.layout(0, 0, 160, 30);
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check(rig.backend.target != null, "native Row has an active split before its label disappears");
        rig.wifi.setVisibility(View.VISIBLE); rig.backend.captureNativeFragments(false, 0);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        try { callbacks.dispatch(rig.root, false, () -> { callbackNativeCycle(rig, callbacks, 2, false); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        check(rig.wifi.getVisibility() == View.VISIBLE && group.getVisibility() == View.VISIBLE,
                "native final apply leaves Wi-Fi content visible inside its logically visible outer host after the obsolete hull is released");
        near(0, rig.wifi.state.visible, "a logically available Wi-Fi is not overflow-hidden by the former shared EXACT hull");
        near(18, rig.mobile.getMeasuredWidth(), "the absent native label contributes no old Android hull budget");
        check("ready".equals(rig.flow.reason(rig.root)), "the measured native fragments fit without a stale overflow");
    }
    private static void nativeWideHostWithoutSharedOwnership() throws Exception {
        Rig rig = new Rig(300); rig.flow.configure(settings("right", true));
        rig.mobile.rect.measuredWidth = 120; rig.mobile.rect.layout(0, 0, 120, 30);
        rig.flow.afterLayout(rig.root);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        try { callbacks.dispatch(rig.root, false, () -> { callbackNativeCycle(rig, callbacks, 0, false); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        near(120, rig.mobile.getMeasuredWidth(), "an Android EXACT host never owned by a shared fragment remains entirely native");
        near(120, rig.mobile.getWidth(), "the natural signal width alone cannot authorize shrinking an unrelated host");
    }
    private static void genuineNativeMotion() throws Exception {
        Rig rig = new Rig(300); AnimatedNative nativeAnimation = new AnimatedNative(); rig.flow.setNativeAccess(nativeAnimation);
        Bundle values = settings("right", true); rig.flow.configure(values); rig.pass(); nativeAnimation.frame(1);
        float start = screenX(rig.signal);
        values.putFloat(StatusBarLayoutSettings.key("mobile", "margin_right"), 16);
        rig.flow.configure(values); rig.pass();
        near(start, screenX(rig.signal), "a real margin change retains the native X-animation starting point");
        nativeAnimation.frame(.5f); near(start - 8, screenX(rig.signal), "a real margin change still interpolates through its native middle frame");
        nativeAnimation.frame(1); near(start - 16, screenX(rig.signal), "a real margin change reaches the requested relative target");
    }
    private static void restoringNativeFrame() throws Exception {
        for (boolean disable : new boolean[]{false, true}) {
            Rig rig = new Rig(300); rig.mode(false); AnimatedNative nativeAnimation = new AnimatedNative();
            rig.flow.setNativeAccess(nativeAnimation); rig.flow.configure(settings("left", "right", true)); rig.pass(); nativeAnimation.frame(1);
            float physicalSignal = screenX(rig.signal);
            check(rig.backend.target != null && rig.backend.target.signalLeft > 100, "fixture has a genuinely expanded cross-region native host");
            Bundle next = disable ? new Bundle() : settings("left", "right", true);
            if (!disable) next.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
            rig.flow.configure(next);
            near(145, rig.mobile.state.x, "configure/disable preserves the independent canonical native X baseline");
            near(physicalSignal, screenX(rig.signal), "restoring native parent and semantic offsets also restores the current animator coordinate frame");
            for (float fraction : new float[]{0, .25f, .5f, 1}) {
                nativeAnimation.frame(fraction);
                near(physicalSignal, screenX(rig.signal), "restored native animation endpoints remain in the restored frame at fraction " + fraction);
            }
            near(30, rig.mobile.getWidth(), "restored animator uses the original thirty-pixel mobile hull");
            check(rig.backend.target == null, "configure/disable releases the expanded shared backend target"); rig.ownership();
        }
    }
    private static void rejectedSecondRebase() throws Exception {
        Rig rig = new Rig(300); AnimatedNative nativeAnimation = new AnimatedNative(); rig.flow.setNativeAccess(nativeAnimation);
        rig.flow.configure(settings("right", true)); rig.pass(); nativeAnimation.frame(.5f);
        rig.flow.configure(settings("left", "right", true));
        float[] originalWifi = nativeAnimation.animations.get(rig.wifi), originalMobile = nativeAnimation.animations.get(rig.mobile);
        float[] wifiValues = originalWifi.clone(), mobileValues = originalMobile.clone();
        float oldWifiDistance = nativeAnimation.distances.get(rig.wifi.state), oldMobileDistance = nativeAnimation.distances.get(rig.mobile.state);
        float oldWifiX = screenX(rig.wifi), oldMobileX = screenX(rig.mobile);
        int wifiRebases = rig.wifi.nativeRebases, mobileRebases = rig.mobile.nativeRebases;
        nativeAnimation.rejectMobileRebase = true; rig.pass();
        check("native_animation_unavailable".equals(rig.flow.reason(rig.root)), "an unsupported second native icon rejects the coordinate-frame transaction");
        check(nativeAnimation.animations.get(rig.wifi) == originalWifi && nativeAnimation.animations.get(rig.mobile) == originalMobile,
                "rejected batch retains both original native animator identities");
        for (int i = 0; i < 3; i++) {
            near(wifiValues[i], originalWifi[i], "rejected second icon cannot leave the first animator partly translated");
            near(mobileValues[i], originalMobile[i], "rejected batch cannot alter the unsupported animator or play time");
        }
        near(oldWifiDistance, nativeAnimation.distances.get(rig.wifi.state), "rejected batch leaves the first native distance cache unchanged");
        near(oldMobileDistance, nativeAnimation.distances.get(rig.mobile.state), "rejected batch leaves the second native distance cache unchanged");
        near(oldWifiX, screenX(rig.wifi), "rejected second icon preserves the first icon's rendered frame");
        near(oldMobileX, screenX(rig.mobile), "rejected batch preserves the second icon's rendered frame");
        near(wifiRebases, rig.wifi.nativeRebases, "preflight refuses the batch before mutating the first native coordinate frame");
        near(mobileRebases, rig.mobile.nativeRebases, "preflight never mutates the unsupported second frame");
        near(125, rig.wifi.state.x, "rejected batch restores the first canonical native target");
        near(145, rig.mobile.state.x, "rejected batch restores the second canonical native target");
        check(!StatusBarFlowLayout.owns(rig.wifi) && !StatusBarFlowLayout.owns(rig.mobile), "rejected batch leaves no partial flow ownership");
        rig.ownership();
    }
    private static void movingDisableFrame() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); AnimatedNative nativeAnimation = new AnimatedNative(); rig.flow.setNativeAccess(nativeAnimation);
        Bundle values = settings("left", "right", true); rig.flow.configure(values); rig.pass(); nativeAnimation.frame(1);
        float nativeSignalTarget = 100 + 145 + 12; // unmodified native parent + host + signal inset
        values.putFloat(StatusBarLayoutSettings.key("mobile", "margin_right"), 16);
        rig.flow.configure(values); rig.pass(); nativeAnimation.frame(.5f);
        float currentSignal = screenX(rig.signal);
        near(nativeSignalTarget - 8, currentSignal, "fixture holds a real half-finished native X animation before disable");
        float[] running = nativeAnimation.animations.get(rig.mobile); float fraction = running[2];
        rig.flow.configure(new Bundle());
        check(nativeAnimation.animations.get(rig.mobile) == running, "disable retains the active native animator identity");
        near(fraction, running[2], "coordinate restore retains the active animator play time");
        near(currentSignal, screenX(rig.signal), "disabling while moving preserves the currently rendered physical signal");
        // Native receives its canonical destination on the requested root pass.
        // Handoff must use the current frame, not jump to the old custom end.
        rig.flow.beforeApply(rig.mobile.state, rig.mobile);
        nativeAnimation.apply(rig.mobile.state, rig.mobile);
        near(currentSignal, screenX(rig.signal), "the next native target begins from the current restored frame");
        nativeAnimation.frame(.5f); near((currentSignal + nativeSignalTarget) / 2, screenX(rig.signal), "native retarget remains a continuous interpolation");
        nativeAnimation.frame(1); near(nativeSignalTarget, screenX(rig.signal), "disabled flow finally reaches the canonical native destination");
        rig.ownership();
    }
    private static void wifiRemainsEnabled() throws Exception {
        Rig rig = new Rig(300); AnimatedNative animation = new AnimatedNative();
        rig.flow.setNativeAccess(animation);
        Bundle values = settings("native", false);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -1.79f);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -4.22f);
        rig.flow.configure(values); rig.pass(); animation.frame(1);
        float wifiX = screenX(rig.wifi), signalX = screenX(rig.signal);
        for (int shift : new int[]{12, 0, 5, 0, 17, 0, 3, 0, 12, 0}) {
            rig.flow.beforeNativeLayout(rig.root);
            rig.icons.rect.layout(shift, 0, 175, 30); rig.icons.rect.measuredWidth = 175 - shift;
            rig.mobile.rect.reset(); rig.signal.rect.reset(); rig.label.rect.reset();
            rig.flow.beforeStates(rig.icons);
            rig.wifi.state.x = rig.wifiNativeX - shift; rig.mobile.state.x = rig.mobileNativeX - shift;
            rig.flow.prepareTargets(rig.icons);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            try {
                callbacks.applyState(rig.wifi.state, rig.wifi, () -> { animation.apply(rig.wifi.state, rig.wifi); return null; });
                callbacks.applyState(rig.mobile.state, rig.mobile, () -> { animation.apply(rig.mobile.state, rig.mobile); return null; });
            } catch (Throwable failure) { throw new AssertionError(failure); }
            near(wifiX, screenX(rig.wifi), "enabled Wi-Fi remains at its current physical frame during a native-only state update");
            near(signalX, screenX(rig.signal), "enabled Wi-Fi does not jerk the neighbouring native signal during a state update");
            rig.flow.afterLayout(rig.root);
            for (float fraction : new float[]{0, .125f, .25f, .5f, .75f, 1}) {
                animation.frame(fraction);
                near(wifiX, screenX(rig.wifi), "Wi-Fi stays enabled throughout repeated native updates without a layout-basis tween");
                near(signalX, screenX(rig.signal), "Wi-Fi-only updates retain the separate signal anchor throughout every native frame");
            }
            check(rig.wifi.getVisibility() == View.VISIBLE && rig.label.getVisibility() == View.GONE,
                    "this regression never switches radio modes or makes the hidden label occupy a slot");
            near(20, rig.wifi.getMeasuredWidth(), "enabled Wi-Fi intrinsic width is unchanged throughout this regression");
        }
        rig.ownership();
    }
    private static void layoutReentersNativeState() throws Exception {
        Rig rig = new Rig(300); AnimatedNative animation = new AnimatedNative();
        rig.wifiNativeX = 145; rig.mobileNativeX = 125; rig.flow.setNativeAccess(animation);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        final int[] updates = {0};
        StatusBarFlowCallbacks.NativeCall nativeUpdate = () -> {
            updates[0]++;
            rig.wifi.state.x = rig.icons.getWidth() - rig.wifi.getWidth();
            rig.mobile.state.x = rig.wifi.state.x - rig.mobile.getWidth();
            rig.flow.prepareTargets(rig.icons);
            rig.flow.beforeApply(rig.wifi.state, rig.wifi); animation.apply(rig.wifi.state, rig.wifi);
            rig.flow.beforeApply(rig.mobile.state, rig.mobile); animation.apply(rig.mobile.state, rig.mobile);
            return null;
        };
        // Unlike the older pure-rect fixture, a real StatusIconContainer.layout
        // calls onLayout -> updateStates synchronously even inside Flow writes.
        rig.icons.nativeLayout = () -> {
            try { callbacks.updateStates(rig.icons, nativeUpdate); }
            catch (Throwable failure) { throw new AssertionError(failure); }
        };
        Bundle values = settings("native", false);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -1.79f);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -4.22f);
        rig.flow.configure(values); rig.pass(); animation.frame(1);
        float wifiX = screenX(rig.wifi), signalX = screenX(rig.signal);
        check(rig.icons.nativeLayoutCalls > 0, "negative Wi-Fi end margin really invokes the native container layout callback");
        near(0, updates[0], "structural transactions never start native animation on their intermediate widths");
        for (int i = 0; i < 12; i++) {
            rig.pass();
            near(0, updates[0], "restore and final hull each defer their nested native state update");
            for (float fraction : new float[]{0, .25f, .5f, 1}) {
                animation.frame(fraction);
                near(wifiX, screenX(rig.wifi), "enabled Wi-Fi does not restart a tween from an intermediate container width");
                near(signalX, screenX(rig.signal), "Wi-Fi layout reentry cannot animate neighbouring signals away from their anchor");
            }
        }
        try { callbacks.updateStates(rig.icons, nativeUpdate); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        near(1, updates[0], "a genuine native state update still executes outside a structural transaction");
        animation.frame(1); near(wifiX, screenX(rig.wifi), "real native updates retain the planned relative Wi-Fi position");
        check(rig.wifi.getVisibility() == View.VISIBLE, "Wi-Fi was enabled throughout the reentrant-layout regression");
        rig.ownership();
    }
    private static void deferredNativePass(boolean defer) throws Exception {
        Rig rig = new Rig(300); AnimatedNative animation = new AnimatedNative();
        rig.wifiNativeX = 145; rig.mobileNativeX = 125; rig.flow.setNativeAccess(animation);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        Bundle values = settings("native", false);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), -1.79f);
        values.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), -4.22f);
        rig.flow.configure(values); rig.pass(); animation.frame(1);
        float wifiX = screenX(rig.wifi), signalX = screenX(rig.signal);
        for (int cycle = 0; cycle < 8; cycle++) {
            rig.flow.beforeNativeMeasure(rig.root); rig.flow.beforeNativeLayout(rig.root);
            rig.icons.rect.reset(); rig.mobile.rect.reset(); rig.signal.rect.reset(); rig.label.rect.reset();
            rig.flow.beforeStates(rig.icons); rig.wifi.state.x = rig.wifiNativeX; rig.mobile.state.x = rig.mobileNativeX;
            rig.flow.prepareTargets(rig.icons);
            try {
                StatusBarFlowCallbacks.NativeCall wifi = () -> { animation.apply(rig.wifi.state, rig.wifi); return null; };
                StatusBarFlowCallbacks.NativeCall mobile = () -> { animation.apply(rig.mobile.state, rig.mobile); return null; };
                if (defer) {
                    callbacks.applyState(rig.wifi.state, rig.wifi, wifi); callbacks.applyState(rig.mobile.state, rig.mobile, mobile);
                } else {
                    rig.flow.beforeApply(rig.wifi.state, rig.wifi); wifi.proceed();
                    rig.flow.beforeApply(rig.mobile.state, rig.mobile); mobile.proceed();
                }
            } catch (Throwable failure) { throw new AssertionError(failure); }
            rig.flow.afterLayout(rig.root);
            for (float fraction : new float[]{0, .125f, .25f, .5f, .75f, 1}) {
                animation.frame(fraction);
                near(wifiX, screenX(rig.wifi), "one native transaction cannot start and translate a temporary Wi-Fi curve");
                near(signalX, screenX(rig.signal), "native traffic refresh cannot restart an adjacent signal curve");
            }
        }
        rig.ownership();
    }
    private static void nativePassRegression() throws Exception {
        boolean reproduced = false;
        try { deferredNativePass(false); } catch (AssertionError failure) { reproduced = true; }
        check(reproduced, "the inspected NO_NEW_ANIMATIONS branch reproduces the old repeated native-pass bounce");
        deferredNativePass(true);
    }
    private static void deferredFallbackAndHidden() throws Exception {
        for (boolean obstacle : new boolean[]{true, false}) {
            Rig rig = new Rig(300); rig.flow.configure(settings("native", false)); rig.pass();
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            int before = rig.wifi.nativeApplications;
            rig.flow.beforeNativeLayout(rig.root); rig.flow.beforeStates(rig.icons);
            rig.wifi.state.x = 125; rig.mobile.state.x = 145; rig.flow.prepareTargets(rig.icons);
            if (!obstacle) { rig.wifi.state.visible = 2; rig.wifi.setVisibility(View.INVISIBLE); }
            try { callbacks.applyState(rig.wifi.state, rig.wifi,
                    () -> { rig.nativeAccess.apply(rig.wifi.state, rig.wifi); return null; }); }
            catch (Throwable failure) { throw new AssertionError(failure); }
            near(before, rig.wifi.nativeApplications, "native application waits for final geometry");
            if (obstacle) rig.root.addView(new Leaf(rig.resources, "privacy_chip", 120, 40));
            rig.flow.afterLayout(rig.root);
            near(before + 1, rig.wifi.nativeApplications, "fallback or removed native slot commits its deferred native state exactly once");
            if (obstacle) {
                check("unsupported_structure".equals(rig.flow.reason(rig.root)), "an unsupported new obstacle keeps the native transaction");
                near(125, rig.wifi.state.x, "unsupported structure retains the canonical native target");
            } else near(2, rig.wifi.state.visible, "a native hidden icon stays hidden when excluded from the final plan");
        }
        Rig capsule = new Rig(300); capsule.flow.configure(settings("native", false)); capsule.pass();
        capsule.flow.beforeNativeLayout(capsule.root); capsule.flow.beforeStates(capsule.icons);
        capsule.wifi.state.visible = 0; capsule.flow.prepareTargets(capsule.icons);
        capsule.wifi.state.visible = 2;
        try { new StatusBarFlowCallbacks(capsule.flow).applyState(capsule.wifi.state, capsule.wifi,
                () -> { capsule.nativeAccess.apply(capsule.wifi.state, capsule.wifi); return null; }); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        capsule.flow.afterLayout(capsule.root);
        near(2, capsule.wifi.state.visible, "deferred commit preserves the native capsule coordinator's hidden result");
    }
    /** Real hook order: container reset/calculate, Seedling boundary, then native apply. */
    private static void callbackNativeCycle(Rig rig, StatusBarFlowCallbacks callbacks,
            int nativeWifiVisible, boolean coordinatorHidesWifi) {
        try {
            callbacks.updateStates(rig.icons, () -> {
                rig.wifi.state.x = rig.wifiNativeX; rig.mobile.state.x = rig.mobileNativeX;
                rig.wifi.state.visible = nativeWifiVisible; rig.mobile.state.visible = 0;
                rig.flow.prepareTargets(rig.icons);
                if (coordinatorHidesWifi) rig.wifi.state.visible = 2;
                callbacks.applyState(rig.wifi.state, rig.wifi,
                        () -> { rig.nativeAccess.apply(rig.wifi.state, rig.wifi); return null; });
                callbacks.applyState(rig.mobile.state, rig.mobile,
                        () -> { rig.nativeAccess.apply(rig.mobile.state, rig.mobile); return null; });
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static void callbackRootCycle(Rig rig, StatusBarFlowCallbacks callbacks, int nativeWifiVisible) {
        try {
            callbacks.dispatch(rig.root, true, () -> {
                rig.root.rect.reset();
                for (View value : rig.views) if (value instanceof Box) ((Box) value).rect.reset();
                    else if (value instanceof Container) ((Container) value).rect.reset(); else ((Leaf) value).rect.reset();
                return null;
            });
            callbacks.dispatch(rig.root, false, () -> {
                callbackNativeCycle(rig, callbacks, nativeWifiVisible, false);
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static void measureWithoutLayoutCompletesNativeScope() throws Exception {
        Rig rig = new Rig(300); rig.flow.configure(settings("native", false)); rig.pass();
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        try {
            Object result = callbacks.dispatch(rig.root, true, () -> "measure-only");
            check("measure-only".equals(result), "measure-only native callback completes and retains its result");
        } catch (Throwable failure) { throw new AssertionError(failure); }
        int applications = rig.wifi.nativeApplications;
        callbackNativeCycle(rig, callbacks, 0, false);
        near(applications + 1, rig.wifi.nativeApplications,
                "a completed measure without root layout cannot defer a later container native apply");
        callbackNativeCycle(rig, callbacks, 0, false);
        near(applications + 2, rig.wifi.nativeApplications,
                "successive native-only updates cannot remain trapped in the previous measure scope");
        int beforeMeasure = rig.wifi.nativeApplications;
        try {
            callbacks.dispatch(rig.root, true, () -> {
                callbackNativeCycle(rig, callbacks, 2, true);
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
        near(beforeMeasure + 1, rig.wifi.nativeApplications,
                "a native hide inside measurement is not queued for an optional later layout");
        try {
            callbacks.dispatch(rig.root, true, () -> { rig.root.rect.measuredWidth = 0; return null; });
        } catch (Throwable failure) { throw new AssertionError(failure); }
        callbackNativeCycle(rig, callbacks, 0, false);
        near(beforeMeasure + 2, rig.wifi.nativeApplications,
                "zero-size measurement still closes its real callback scope");
        rig.root.rect.measuredWidth = 300;
    }
    private static void coordinatorVisibilityHasOneNativeCycle() throws Exception {
        Rig rig = new Rig(300); rig.flow.configure(settings("native", false)); rig.pass();
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        try {
            callbacks.dispatch(rig.root, false, () -> {
                callbackNativeCycle(rig, callbacks, 0, true);
                return null;
            });
        } catch (Throwable failure) { throw new AssertionError(failure); }
        near(2, rig.wifi.state.visible, "root native commit retains its current Seedling hide");
        callbackNativeCycle(rig, callbacks, 0, true);
        near(2, rig.wifi.state.visible, "a following native-only Seedling hide is still authoritative");
        callbackNativeCycle(rig, callbacks, 0, false);
        near(0, rig.wifi.state.visible, "a later native-only reveal recovers without another root layout");
        // The old narrow native container can subsequently report local overflow.
        // Its real flow allocation still fits; only a CURRENT coordinator hide may win.
        callbackNativeCycle(rig, callbacks, 2, false);
        near(0, rig.wifi.state.visible,
                "a previous Seedling hide cannot remain attached to a later local-overflow calculation");
    }
    private static void nativeOnlyEligibilityRequestsOneRootLayout() throws Exception {
        Rig rig = new Rig(300); rig.flow.configure(settings("native", false)); rig.pass();
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        int requests = rig.root.layoutRequests, applications = rig.wifi.nativeApplications;
        rig.wifi.setVisibility(View.GONE);
        callbackNativeCycle(rig, callbacks, 2, false);
        near(2, rig.wifi.state.visible,
                "a stale endpoint never promotes a newly ineligible native Wi-Fi icon to visible");
        near(applications + 1, rig.wifi.nativeApplications,
                "the removed icon still receives its native hide during a container-only update");
        near(requests + 1, rig.root.layoutRequests,
                "changed eligibility requests the root's finite flow plan exactly once");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(2, rig.wifi.state.visible, "repeated pending updates keep the ineligible native Wi-Fi hidden");
        near(requests + 1, rig.root.layoutRequests,
                "pending eligibility refresh never schedules a second identical root layout");
        try { callbacks.dispatch(rig.root, false, () -> null); }
        catch (Throwable failure) { throw new AssertionError(failure); }
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests,
                "a completed rebuilt plan accepts its absent Wi-Fi without a request loop");
        near(2, rig.wifi.state.visible, "the rebuilt scene omits the absent native Wi-Fi endpoint");
    }
    private static void bothRadiosOffRemovesSharedBudget() throws Exception {
        Rig rig = new Rig(300);
        Bundle values = settings("right", true);
        // Put the shared Wi-Fi/label slot AFTER signal so its removal must move signal right.
        values.putInt(StatusBarLayoutSettings.key("mobile", "order"), 1);
        values.putInt(StatusBarLayoutSettings.key("wifi", "order"), 2);
        rig.flow.configure(values);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        callbackRootCycle(rig, callbacks, 0);
        rig.mode(false);
        callbackRootCycle(rig, callbacks, 2);
        check(rig.backend.target != null, "data first owns a real split native label/signal hull");
        float dataSignal = screenX(rig.signal), sharedCenter = screenX(rig.label) + 6;
        int requests = rig.root.layoutRequests, oldExact = rig.mobile.getMeasuredWidth();
        rig.wifi.setVisibility(View.GONE);
        rig.backend.captureNativeFragments(false, 0);
        near(oldExact, rig.mobile.getMeasuredWidth(), "native label absence is observed before the old host EXACT width changes");
        near(18, rig.mobile.rect.nativeWidth, "both-off native content retains only the signal's eighteen-pixel width");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "both-off native fragment change requests one root rebuild");
        near(2, rig.wifi.state.visible, "both-off update preserves the original Wi-Fi hide");
        check(rig.backend.target == null, "native label absence releases its former shared hull before root layout");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "repeated both-off updates share the pending rebuild");
        callbackRootCycle(rig, callbacks, 2);
        near(18, rig.mobile.getMeasuredWidth(), "completed both-off root measure drops the former EXACT label hull");
        near(dataSignal + 36, screenX(rig.signal), "removing the twenty-pixel shared slot and its seven/nine margins moves signal right");
        near(screenX(rig.battery) - 18, screenX(rig.signal), "remaining signal packs directly against battery with no phantom Wi-Fi budget");
        NetworkSharedSlotLayout.Frame absent = rig.bridge.frame(rig.mobile);
        check(absent != null && !absent.labelPresent && absent.labelWidthPx == 0 && rig.backend.target == null,
                "both-off scene has a native signal frame and no synthetic shared label target");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "settled both-off scene does not request another root layout");
        rig.mode(true);
        callbackNativeCycle(rig, callbacks, 0, false);
        near(requests + 2, rig.root.layoutRequests, "returning Wi-Fi schedules its own one-time root rebuild");
        callbackRootCycle(rig, callbacks, 0);
        near(dataSignal, screenX(rig.signal), "returning Wi-Fi restores the separate signal allocation");
        near(sharedCenter, screenX(rig.wifi) + 10, "returning Wi-Fi recovers the existing shared slot center");
        near(0, rig.wifi.state.visible, "returning Wi-Fi is visible after its native root transaction");
        callbackNativeCycle(rig, callbacks, 0, false);
        near(requests + 2, rig.root.layoutRequests, "settled returning Wi-Fi does not create a root-layout loop");
        rig.ownership();
    }
    private static void nativeFragmentWidthRefreshesUnchangedHost() throws Exception {
        Rig rig = new Rig(300); rig.mode(false); rig.flow.configure(settings("right", true));
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        callbackRootCycle(rig, callbacks, 2);
        int requests = rig.root.layoutRequests, oldExact = rig.mobile.getMeasuredWidth();
        rig.backend.captureNativeFragments(true, 8);
        near(oldExact, rig.mobile.getMeasuredWidth(), "fragment-only content measurement retains the host's prior EXACT width");
        NetworkSharedSlotLayout.Frame changed = rig.bridge.frame(rig.mobile);
        check(changed != null && changed.labelPresent && changed.labelWidthPx == 8 && changed.nativeHostWidthPx == 26,
                "a still-present narrower label changes its natural frame independently of host measurement");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "changed label frame schedules a rebuild despite identical host measured width");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "repeated narrower-label updates retain only one pending rebuild");
        callbackRootCycle(rig, callbacks, 2);
        check(rig.backend.target != null && rig.backend.target.labelVisible,
                "rebuilding a narrower present label retains its real shared target");
        near(8, rig.bridge.frame(rig.mobile).labelWidthPx, "rebuilt frame uses the current native label width");
        check(rig.mobile.getMeasuredWidth() < oldExact, "new native hull releases the obsolete label width");
        callbackNativeCycle(rig, callbacks, 2, false);
        near(requests + 1, rig.root.layoutRequests, "settled narrower label does not repeatedly request root layout");
        rig.ownership();
    }
    private static void hiddenLabelWidthInvalidation() throws Exception {
        for(String key:new String[]{"label_text_style_enabled","label_size_enabled","font_enabled",StatusBarSettings.FONT_WEIGHT,
                StatusBarSettings.FONT_MODE,StatusBarSettings.FONT_REVISION,FontScopes.KEY,StatusBarSettings.LABEL_SCALE}) {
            Rig rig=new Rig(400);Bundle values=settings("right",true);
            values.putFloat(StatusBarLayoutSettings.key("wifi","margin_left"),0);values.putFloat(StatusBarLayoutSettings.key("wifi","margin_right"),0);
            values.putBoolean("label_enabled",true);values.putBoolean("label_text_style_enabled",true);values.putBoolean("label_size_enabled",true);
            values.putBoolean("label_color_enabled",false);values.putFloat(StatusBarSettings.FONT_WEIGHT,600);values.putFloat(StatusBarSettings.LABEL_SCALE,100);
            if(key.equals(StatusBarSettings.FONT_MODE)||key.equals(StatusBarSettings.FONT_REVISION)||key.equals(FontScopes.KEY))values.putBoolean("font_enabled",true);
            rig.flow.configure(values);rig.mode(false);rig.backend.captureNativeFragments(true,40);rig.pass();
            check("ready".equals(rig.flow.reason(rig.root))&&rig.backend.target!=null,"visible label enters real shared-slot flow: "+key);
            near(40,screenX(rig.signal)-screenX(rig.label),"visible forty-pixel label has its full native allocation: "+key);
            rig.mode(true);rig.pass();near(20,screenX(rig.mobile)-(screenX(rig.wifi)+10),"Wi-Fi keeps the forty-pixel shared slot after hiding label: "+key);
            values.putBoolean("label_color_enabled",true);rig.flow.configure(values);rig.pass();
            near(20,screenX(rig.mobile)-(screenX(rig.wifi)+10),"color-only update preserves hidden label width: "+key);
            if(key.equals("label_text_style_enabled")||key.equals("label_size_enabled"))values.putBoolean(key,false);
            else if(key.equals("font_enabled"))values.putBoolean(key,true);
            else if(key.equals(StatusBarSettings.FONT_WEIGHT))values.putFloat(key,900);
            else if(key.equals(StatusBarSettings.FONT_MODE))values.putString(key,"pingfang");
            else if(key.equals(StatusBarSettings.FONT_REVISION))values.putString(key,"b".repeat(64));
            else if(key.equals(FontScopes.KEY))values.putString(key,"{\"network_type\":\"pingfang\"}");
            else values.putFloat(key,150);
            rig.flow.configure(values);rig.pass();
            near(10,screenX(rig.mobile)-(screenX(rig.wifi)+10),"hidden label typography/size change releases obsolete forty-pixel budget: "+key);
            for(int i=0;i<3;i++){rig.pass();near(10,screenX(rig.mobile)-(screenX(rig.wifi)+10),"released hidden budget remains stable: "+key);}
            rig.mode(false);rig.backend.captureNativeFragments(true,26);rig.pass();
            near(26,screenX(rig.signal)-screenX(rig.label),"new visible label supplies its current measured width: "+key);
            rig.mode(true);rig.pass();near(13,screenX(rig.mobile)-(screenX(rig.wifi)+10),"Wi-Fi retains only the newly measured label width: "+key);
            rig.ownership();rig.flow.releaseRuntime();
        }
    }
    private static void hiddenLabelInactiveEdits() throws Exception {
        for(boolean active:new boolean[]{false,true}){
            Rig rig=new Rig(400);Bundle values=settings("right",true);
            values.putFloat(StatusBarLayoutSettings.key("wifi","margin_left"),0);values.putFloat(StatusBarLayoutSettings.key("wifi","margin_right"),0);
            values.putBoolean("label_enabled",true);values.putBoolean("wifi_enabled",true);
            values.putBoolean("label_text_style_enabled",active);values.putBoolean("label_size_enabled",active);values.putBoolean("wifi_size_enabled",active);values.putBoolean("font_enabled",false);
            rig.flow.configure(values);rig.mode(false);rig.backend.captureNativeFragments(true,40);rig.pass();
            check("ready".equals(rig.flow.reason(rig.root))&&rig.backend.target!=null,"saved-value fixture enters real shared flow");
            rig.mode(true);rig.pass();near(20,screenX(rig.mobile)-(screenX(rig.wifi)+10),"saved-value fixture retains hidden forty-pixel label");
            for(String key:new String[]{StatusBarSettings.SLOT_WIDTH,StatusBarSettings.FONT_WEIGHT,StatusBarSettings.LABEL_SCALE,StatusBarSettings.WIFI_ICON_SCALE,
                    StatusBarSettings.FONT_MODE,StatusBarSettings.FONT_REVISION,FontScopes.KEY}){
                if(key.equals(StatusBarSettings.SLOT_WIDTH))values.putFloat(key,active?80:60);
                else if(key.equals(StatusBarSettings.FONT_WEIGHT))values.putInt(key,active?600:900);
                else if(key.equals(StatusBarSettings.LABEL_SCALE)||key.equals(StatusBarSettings.WIFI_ICON_SCALE))values.putInt(key,active?100:250);
                else if(key.equals(StatusBarSettings.FONT_MODE))values.putString(key,"pingfang");
                else if(key.equals(StatusBarSettings.FONT_REVISION))values.putString(key,"c".repeat(64));
                else values.putString(key,"{\"network_type\":\"pingfang\"}");
                rig.flow.configure(values);rig.pass();
                near(20,screenX(rig.mobile)-(screenX(rig.wifi)+10),(active?"equivalent effective default or disabled font":"disabled size/style/font saved edit")+" preserves hidden width: "+key);
                rig.pass();near(20,screenX(rig.mobile)-(screenX(rig.wifi)+10),"unchanged effective sizing stays stable: "+key);
            }
            rig.ownership();rig.flow.releaseRuntime();
        }
    }
    private static void nativeContentMeasureScope() throws Exception {
        Rig rig = new Rig(400); rig.flow.configure(settings("right", true)); rig.mode(false); rig.pass();
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        for (boolean fail : new boolean[]{false, true}) {
            RuntimeException expected = new RuntimeException("native measure failed");
            try {
                callbacks.dispatch(rig.root, true, () -> {
                    check(rig.bridge.nativeMeasuring(rig.mobile), "actual root measure owns fresh native constraints");
                    callbacks.dispatch(rig.root, true, () -> {
                        check(rig.bridge.nativeMeasuring(rig.mobile), "inherited nested measure keeps the outer native scope"); return null;
                    });
                    check(rig.bridge.nativeMeasuring(rig.mobile), "nested return does not release the real outer measure");
                    if (fail) throw expected;
                    return null;
                });
                check(!fail, "failing native call propagates its original exception");
            } catch (Throwable failure) { check(fail && failure == expected, "original native failure is preserved"); }
            check(!rig.bridge.nativeMeasuring(rig.mobile), "native constraint ownership ends on normal and exceptional returns");
            near(0, rig.backend.hullDepth, "native callback leaves no owned hull measure open");
        }
        try { callbacks.dispatch(rig.root, false, () -> {
            check(!rig.bridge.nativeMeasuring(rig.mobile), "layout-only capsule callbacks cannot overwrite native content constraints"); return null;
        }); } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("disable")) disableAndMeasure();
        else if (arguments.length == 1 && arguments[0].equals("fallback")) fallback();
        else if (arguments.length == 1 && arguments[0].equals("measurement")) measurementRegression();
        else if (arguments.length == 1 && arguments[0].equals("native")) switching("native", View.LAYOUT_DIRECTION_LTR, false);
        else if (arguments.length == 1 && arguments[0].equals("animation")) nativeAnimation();
        else if (arguments.length == 1 && arguments[0].equals("overflow")) overflow();
        else if (arguments.length == 1 && arguments[0].equals("signed")) signedMargins();
        else if (arguments.length == 1 && arguments[0].equals("opposite")) oppositeNativeOrder();
        else if (arguments.length == 1 && arguments[0].equals("neighbors")) neighborLifecycle();
        else if (arguments.length == 1 && arguments[0].equals("animated-parent")) animatedNativeParentRegression(false);
        else if (arguments.length == 1 && arguments[0].equals("animated-parent-reverse")) animatedNativeParentRegression(true);
        else if (arguments.length == 1 && arguments[0].equals("wifi-backend")) returningWifiBackend();
        else if (arguments.length == 1 && arguments[0].equals("wifi-layout-only")) { returningWifiWithoutHostMeasure(); staleSharedHullMustNotHideWifi(); nativeWideHostWithoutSharedOwnership(); }
        else if (arguments.length == 1 && arguments[0].equals("wifi-hidden-hull")) staleSharedHullMustNotHideWifi();
        else if (arguments.length == 1 && arguments[0].equals("native-motion")) genuineNativeMotion();
        else if (arguments.length == 1 && arguments[0].equals("semantic-offset")) {
            animatedNativeParentRegression(false, false); animatedNativeParentRegression(true, false);
        }
        else if (arguments.length == 1 && arguments[0].equals("rebase-stages")) bothRebaseStagesRequired();
        else if (arguments.length == 1 && arguments[0].equals("restore-frame")) restoringNativeFrame();
        else if (arguments.length == 1 && arguments[0].equals("batch-rebase")) rejectedSecondRebase();
        else if (arguments.length == 1 && arguments[0].equals("moving-disable")) movingDisableFrame();
        else if (arguments.length == 1 && arguments[0].equals("wifi-enabled")) wifiRemainsEnabled();
        else if (arguments.length == 1 && arguments[0].equals("layout-reentry")) layoutReentersNativeState();
        else if (arguments.length == 1 && arguments[0].equals("native-pass")) nativePassRegression();
        else if (arguments.length == 1 && arguments[0].equals("deferred-fallback")) deferredFallbackAndHidden();
        else if (arguments.length == 1 && arguments[0].equals("measure-only")) measureWithoutLayoutCompletesNativeScope();
        else if (arguments.length == 1 && arguments[0].equals("coordinator-lifecycle")) coordinatorVisibilityHasOneNativeCycle();
        else if (arguments.length == 1 && arguments[0].equals("eligibility-refresh")) nativeOnlyEligibilityRequestsOneRootLayout();
        else if (arguments.length == 1 && arguments[0].equals("both-off")) bothRadiosOffRemovesSharedBudget();
        else if (arguments.length == 1 && arguments[0].equals("fragment-refresh")) nativeFragmentWidthRefreshesUnchangedHost();
        else if (arguments.length == 1 && arguments[0].equals("height-zero")) unresolvedSharedHostHeight();
        else if (arguments.length == 1 && arguments[0].equals("height-restore")) restoringOwnedWidthKeepsNativeHeight();
        else if (arguments.length == 1 && arguments[0].equals("shared-x")) { sharedHorizontalPosition(); sharedHorizontalMasters(); }
        else if (arguments.length == 1 && arguments[0].equals("shared-x-masters")) sharedHorizontalMasters();
        else if (arguments.length == 1 && arguments[0].equals("hidden-label-width")) { hiddenLabelWidthInvalidation();hiddenLabelInactiveEdits(); }
        else if (arguments.length == 1 && arguments[0].equals("hidden-label-inactive")) hiddenLabelInactiveEdits();
        else if (arguments.length == 1 && arguments[0].equals("content-scope")) nativeContentMeasureScope();
        else {
            for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL})
                for (String side : new String[]{"left", "right"}) switching(side, direction, true);
            switching("left", "right", View.LAYOUT_DIRECTION_LTR, true);
            switching("right", "left", View.LAYOUT_DIRECTION_RTL, true);
            switching("native", View.LAYOUT_DIRECTION_LTR, false);
            disableAndMeasure(); fallback(); measurementRegression(); nativeAnimation(); overflow(); signedMargins();
            oppositeNativeOrder(); neighborLifecycle();
            animatedNativeParentRegression(false); animatedNativeParentRegression(true);
            animatedNativeParentRegression(false, false); animatedNativeParentRegression(true, false);
            returningWifiBackend(); returningWifiWithoutHostMeasure(); staleSharedHullMustNotHideWifi(); nativeWideHostWithoutSharedOwnership(); genuineNativeMotion(); bothRebaseStagesRequired();
            restoringNativeFrame(); rejectedSecondRebase(); movingDisableFrame(); wifiRemainsEnabled(); layoutReentersNativeState(); nativePassRegression(); deferredFallbackAndHidden();
            measureWithoutLayoutCompletesNativeScope(); coordinatorVisibilityHasOneNativeCycle(); nativeOnlyEligibilityRequestsOneRootLayout();
            bothRadiosOffRemovesSharedBudget(); nativeFragmentWidthRefreshesUnchangedHost();
            unresolvedSharedHostHeight(); restoringOwnedWidthKeepsNativeHeight();
            sharedHorizontalPosition(); sharedHorizontalMasters();hiddenLabelWidthInvalidation();hiddenLabelInactiveEdits();nativeContentMeasureScope();
        }
        System.out.println("StatusBarSharedSlotFlowCheck: " + checks + " checks passed");
    }
}
