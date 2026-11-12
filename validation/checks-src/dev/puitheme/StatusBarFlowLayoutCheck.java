package dev.puitheme;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exercises the production scene coordinator through real measured/nested View geometry and native state transactions. */
public final class StatusBarFlowLayoutCheck {
    private static int checks, nextId = 1;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(float expected, float actual, String why) {
        check(Float.isFinite(actual) && Math.abs(expected - actual) < .02f, why + ": " + expected + " != " + actual);
    }
    private static final class NamedResources extends Resources {
        NamedResources() { getDisplayMetrics().density = 1f; }
        int id(String name) { int value = nextId++; entryNames.put(value, name); return value; }
    }
    private static final class Geometry {
        int left, top, right, bottom, measuredWidth, measuredHeight, measures;
        int[] original;
        Geometry(int l, int t, int r, int b) { set(l, t, r, b); original = new int[]{l, t, r, b}; }
        void set(int l, int t, int r, int b) { layout(l, t, r, b); measuredWidth = width(); measuredHeight = height(); }
        // Android layout never measures a view. In particular a 160px measured
        // clock can occupy a 120px row, and a newly added child can remain 0px.
        void layout(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
        int width() { return Math.max(0, right - left); } int height() { return Math.max(0, bottom - top); }
        void measure(int widthSpec, int heightSpec) {
            measuredWidth = View.resolveSize(width(), widthSpec); measuredHeight = View.resolveSize(height(), heightSpec); measures++;
        }
        void reset() { set(original[0], original[1], original[2], original[3]); }
    }
    private static final class Root extends PhoneStatusBarView {
        final Geometry geometry; final NamedResources resources;
        int direction, paddingLeft, paddingRight;
        boolean queuePosts;
        final List<Runnable> posted = new ArrayList<>();
        Root(NamedResources resources, int left, int width) { this.resources = resources; geometry = new Geometry(left, 9, left + width, 39); }
        @Override public Resources getResources() { return resources; }
        @Override public int getLayoutDirection() { return direction; }
        @Override public int getPaddingLeft() { return paddingLeft; }
        @Override public int getPaddingRight() { return paddingRight; }
        @Override public int getLeft() { return geometry.left; } @Override public int getRight() { return geometry.right; }
        @Override public int getTop() { return geometry.top; } @Override public int getBottom() { return geometry.bottom; }
        @Override public int getWidth() { return geometry.width(); } @Override public int getHeight() { return geometry.height(); }
        @Override public int getMeasuredWidth() { return geometry.measuredWidth; } @Override public int getMeasuredHeight() { return geometry.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { geometry.layout(l, t, r, b); }
        @Override protected void onMeasure(int w, int h) { geometry.measure(w, h); }
        @Override public boolean post(Runnable task) { if (!queuePosts) return super.post(task); posted.add(task); return true; }
        @Override public boolean removeCallbacks(Runnable task) { return posted.remove(task) | super.removeCallbacks(task); }
        void runPosts() { List<Runnable> due = new ArrayList<>(posted); posted.clear(); for (Runnable task : due) task.run(); }
    }
    private static class Box extends ViewGroup {
        final Geometry geometry; final NamedResources resources;
        int paddingLeft, paddingRight;
        boolean container; final Set<String> ignored = new HashSet<>();
        Box(NamedResources resources, String name, int l, int t, int r, int b) {
            super(null); this.resources = resources; geometry = new Geometry(l, t, r, b); setId(resources.id(name));
        }
        @Override public Resources getResources() { return resources; }
        @Override public int getPaddingLeft() { return paddingLeft; }
        @Override public int getPaddingRight() { return paddingRight; }
        @Override public int getLeft() { return geometry.left; } @Override public int getRight() { return geometry.right; }
        @Override public int getTop() { return geometry.top; } @Override public int getBottom() { return geometry.bottom; }
        @Override public int getWidth() { return geometry.width(); } @Override public int getHeight() { return geometry.height(); }
        @Override public int getMeasuredWidth() { return geometry.measuredWidth; } @Override public int getMeasuredHeight() { return geometry.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { geometry.layout(l, t, r, b); }
        @Override protected void onMeasure(int w, int h) { geometry.measure(w, h); }
    }
    private static class Leaf extends View {
        final Geometry geometry; final NamedResources resources;
        int translationWrites;
        Leaf(NamedResources resources, String name, int l, int t, int r, int b) {
            super(null); this.resources = resources; geometry = new Geometry(l, t, r, b); setId(resources.id(name));
        }
        @Override public Resources getResources() { return resources; }
        @Override public int getLeft() { return geometry.left; } @Override public int getRight() { return geometry.right; }
        @Override public int getTop() { return geometry.top; } @Override public int getBottom() { return geometry.bottom; }
        @Override public int getWidth() { return geometry.width(); } @Override public int getHeight() { return geometry.height(); }
        @Override public int getMeasuredWidth() { return geometry.measuredWidth; } @Override public int getMeasuredHeight() { return geometry.measuredHeight; }
        @Override public void layout(int l, int t, int r, int b) { geometry.layout(l, t, r, b); }
        @Override public void setTranslationX(float value) { super.setTranslationX(value); translationWrites++; }
        @Override protected void onMeasure(int w, int h) { geometry.measure(w, h); }
    }
    /** A pending first measure must not be consumed by a synthetic ancestor layout.
     * Real FrameLayout centers the child from its measured dimensions; layout() also
     * clears that child's FORCE_LAYOUT flag, even when both dimensions are still 0. */
    private static final class FirstMeasureLeaf extends Leaf {
        boolean pending = true;
        FirstMeasureLeaf(NamedResources resources) { super(resources, "fixture_pending_notification", 0, 0, 0, 0); }
        @Override public boolean isLayoutRequested() { return pending; }
        @Override public void layout(int l, int t, int r, int b) { super.layout(l, t, r, b); pending = false; }
    }
    private static final class NativeFrame extends Box {
        int nativeLayouts;
        NativeFrame(NamedResources resources, String name, int l, int t, int r, int b) { super(resources, name, l, t, r, b); }
        @Override public void layout(int l, int t, int r, int b) {
            super.layout(l, t, r, b); nativeLayouts++;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == View.GONE) continue;
                int top = (getHeight() - child.getMeasuredHeight()) / 2;
                child.layout(0, top, child.getMeasuredWidth(), top + child.getMeasuredHeight());
            }
        }
    }
    private static final class State { float x; int visible; State(float value) { x = value; } }
    private static final class KRoot extends com.android.systemui.statusbar.phone.KeyguardStatusBarView {
        final NamedResources resources;KRoot(NamedResources resources){super(null);this.resources=resources;}
        @Override public Resources getResources(){return resources;}
        @Override public int getWidth(){return 1440;}@Override public int getHeight(){return 160;}
        @Override public int getMeasuredWidth(){return 1440;}@Override public int getMeasuredHeight(){return 160;}
    }
    private static final class PaddedLeaf extends Leaf {
        final int padding;
        PaddedLeaf(NamedResources resources,String id,int width,int padding){super(resources,id,0,0,width,120);this.padding=padding;}
        @Override public int getPaddingLeft(){return padding;}
        @Override public void getLocationOnScreen(int[] result){result[0]=Math.round(screenX(this));result[1]=Math.round(screenY(this));}
    }
    private static final class QRoot extends com.oplus.systemui.qs.OplusQuickStatusBarHeader {
        final NamedResources resources;
        QRoot(NamedResources resources) { super(null); this.resources = resources; }
        @Override public Resources getResources() { return resources; }
        @Override public int getWidth() { return 300; }
        @Override public int getMeasuredWidth() { return 300; }
        @Override public int getHeight() { return 100; }
    }
    private static class Icon extends Leaf {
        final String slot; final State state; boolean eligible = true;
        int nativeApplications, nativeRebases;
        Icon(NamedResources resources, String slot, int width, float nativeX) {
            super(resources, "fixture_native_icon", 0, 0, width, 30); this.slot = slot; state = new State(nativeX);
            setTranslationX(nativeX);
        }
    }
    /** A slot icon whose first measure is still requested and 0x0. */
    private static final class PendingIcon extends Icon {
        boolean pending = true;
        PendingIcon(NamedResources resources, String slot) { super(resources, slot, 0, 0f); geometry.measuredHeight = 0; geometry.bottom = 0; }
        @Override public boolean isLayoutRequested() { return pending; }
        @Override public void layout(int l, int t, int r, int b) { super.layout(l, t, r, b); pending = false; }
        @Override public void forceLayout() { super.forceLayout(); pending = true; }
        @Override public boolean isShown() {
            if (!super.isShown()) return false;
            for (View current = this; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
                if (current.getVisibility() != View.VISIBLE) return false;
            return true;
        }
    }
    /** Same-spec native measurement is cached independently for every sibling. */
    private static final class CachedSizedLeaf extends Leaf {
        final int width, height;
        boolean force;
        CachedSizedLeaf(NamedResources resources, String id, int width, int height) {
            super(resources, id, 0, 0, 0, 0); this.width = width; this.height = height;
            setLayoutParams(new ViewGroup.LayoutParams(width, height));
        }
        @Override public void forceLayout() { super.forceLayout(); force = true; }
        @Override public void measure(int w, int h) {
            if (!force) return;
            force = false; geometry.measuredWidth = width; geometry.measuredHeight = height;
        }
    }
    private static final class CachedSizedHost extends Box {
        boolean force;
        CachedSizedHost(NamedResources resources, String id) { super(resources, id, 0, 0, 0, 30); }
        @Override public void forceLayout() { super.forceLayout(); force = true; }
        @Override public void measure(int w, int h) {
            if (!force) return;
            force = false; int width = 0;
            for (int i = 0; i < getChildCount(); i++) { View child = getChildAt(i); child.measure(w, h); width += child.getMeasuredWidth(); }
            geometry.measuredWidth = width; geometry.measuredHeight = 30;
        }
    }
    private static final class Native implements StatusBarFlowLayout.NativeAccess {
        int hintReads;
        public boolean container(View view) { return view instanceof Box && ((Box) view).container; }
        public String slot(View view) { return view instanceof Icon ? ((Icon) view).slot : null; }
        public boolean hint(View view){hintReads++;return view instanceof Icon&&!NativeStatusIcons.excludedSlot(((Icon)view).slot);}
        public boolean eligible(ViewGroup parent, View child) {
            return child instanceof Icon && ((Icon) child).eligible && child.getVisibility() != View.GONE
                    && !((Box) parent).ignored.contains(((Icon) child).slot);
        }
        public Object state(View view) { return view instanceof Icon ? ((Icon) view).state : null; }
        public float x(Object state) { return ((State) state).x; }
        public void x(Object state, float value) { ((State) state).x = value; }
        public int visible(Object state) { return ((State) state).visible; }
        public void visible(Object state, int value) { ((State) state).visible = value; }
        public void apply(Object state, View child) {
            ((Icon) child).nativeApplications++;
            child.setTranslationX(((State) state).x);
        }
        public boolean rebase(Object state, View child, float dx, float parentWidthDelta, float childWidthDelta) {
            ((State) state).x += dx;
            if (dx != 0f) {
                ((Icon) child).nativeRebases++;
                child.setTranslationX(child.getTranslationX() + dx);
            }
            return true;
        }
        public boolean canRebase(Object state, View child, float dx, float parentWidthDelta, float childWidthDelta) { return true; }
    }
    private static final class Rig {
        final NamedResources resources = new NamedResources();
        final Root root;
        final Box left, right, clockHost, icons, contents;
        final Leaf clock, battery;
        final Icon wifi, mobile;
        float wifiNativeX = 125, mobileNativeX = 145;
        final Native nativeAccess = new Native();
        final StatusBarFlowLayout flow = new StatusBarFlowLayout();
        final List<View> all = new ArrayList<>();
        final java.util.Map<Icon,Float> nativeHintXs=new java.util.IdentityHashMap<>();
        final List<ViewGroup.LayoutParams> nativeParams = new ArrayList<>();
        final List<Object> nativeParents = new ArrayList<>();
        Rig(int rootOffset, int width) { this(rootOffset, width, false); }
        Rig(int rootOffset, int width, boolean wrapped) {
            root = new Root(resources, rootOffset, width);
            contents = wrapped ? new Box(resources, "status_bar_contents", 0, 0, width, 30) : null;
            left = new Box(resources, "status_bar_start_side_container", 0, 0, 100, 30);
            right = new Box(resources, "status_bar_end_side_container", 100, 0, width, 30);
            clockHost = new Box(resources, "clock_for_fake", 0, 0, 60, 30);
            icons = new Box(resources, "statusIcons", 0, 0, 175, 30); icons.container = true;
            clock = new Leaf(resources, "clock", 0, 0, 60, 30);
            battery = new Leaf(resources, "battery", 175, 0, 200, 30);
            wifi = new Icon(resources, "wifi", 20, 125); mobile = new Icon(resources, "mobile", 30, 145);
            if (contents == null) { root.addView(left); root.addView(right); }
            else { root.addView(contents); contents.addView(left); contents.addView(right); all.add(contents); }
            left.addView(clockHost); clockHost.addView(clock);
            right.addView(icons); right.addView(battery); icons.addView(wifi); icons.addView(mobile);
            all.addAll(Arrays.asList(left, right, clockHost, icons, clock, battery, wifi, mobile));
            for (View view : all) {
                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(view.getWidth(), view.getHeight());
                view.setLayoutParams(params); nativeParams.add(params); nativeParents.add(view.getParent());
            }
            flow.setNativeAccess(nativeAccess);
        }
        void configure(Bundle bundle) { flow.configure(bundle); }
        void pass() { pass(flow); }
        void pass(StatusBarFlowLayout coordinator) {
            coordinator.beforeNativeLayout(root);
            root.geometry.reset();
            for (View view : all) if (view instanceof Box) ((Box) view).geometry.reset(); else ((Leaf) view).geometry.reset();
            wifi.state.x = wifiNativeX; mobile.state.x = mobileNativeX;
            // Native updateStates recalculates each hint after resetting its parent geometry too.
            for(java.util.Map.Entry<Icon,Float> hint:nativeHintXs.entrySet())hint.getKey().state.x=hint.getValue();
            coordinator.afterMeasure(root); coordinator.afterLayout(root);
        }
        void applyStates() {
            for (Icon icon : Arrays.asList(wifi, mobile)) { flow.beforeApply(icon.state, icon); nativeAccess.apply(icon.state, icon); }
            for(Icon icon:nativeHintXs.keySet()){flow.beforeApply(icon.state,icon);nativeAccess.apply(icon.state,icon);}
        }
        void ownership() {
            for (int i = 0; i < all.size(); i++) {
                check(all.get(i).getParent() == nativeParents.get(i), "controller parent unchanged");
                check(all.get(i).getLayoutParams() == nativeParams.get(i), "native layout-params identity unchanged");
            }
            check(icons.getChildAt(0) == wifi && icons.getChildAt(1) == mobile, "IconManager child indexes unchanged");
        }
    }
    private static Bundle enabled() { Bundle result = new Bundle(); result.putBoolean(StatusBarLayoutSettings.MASTER, true); return result; }
    private static Icon addHint(Rig rig,String slot,float x){
        Icon hint=new Icon(rig.resources,slot,10,x);rig.icons.addView(hint);rig.all.add(hint);
        ViewGroup.LayoutParams lp=new ViewGroup.MarginLayoutParams(10,30);hint.setLayoutParams(lp);
        rig.nativeParams.add(lp);rig.nativeParents.add(rig.icons);rig.nativeHintXs.put(hint,x);return hint;
    }
    /** Merely spelling out native/zero is neutral when no side, order or margin is changed. */
    private static void hintNeutralCase(){
        Rig rig=new Rig(0,400,true);Icon bluetooth=addHint(rig,"bluetooth",70),alarm=addHint(rig,"alarm_clock",100);
        Bundle values=enabled();rig.configure(values);rig.pass();
        float[] baseline={screenX(bluetooth),screenX(alarm),screenX(rig.wifi),screenX(rig.mobile),screenX(rig.battery)};
        values.putString(NativeStatusIcons.LAYOUT,"{\"alarm_clock\":{\"side\":\"native\",\"order\":0}}");
        rig.configure(values);rig.pass();
        View[] compared={bluetooth,alarm,rig.wifi,rig.mobile,rig.battery};
        for(int i=0;i<compared.length;i++)near(baseline[i],screenX(compared[i]),"explicit native zero preserves existing gap/inset "+i);
        rig.ownership();rig.flow.releaseRuntime();
    }
    /** A real native status-icon parent keeps all child/LP identities while hint slots split sides. */
    private static void hintPlacementCase() {
        Rig rig=new Rig(0,400,true);
        Icon bluetooth=new Icon(rig.resources,"bluetooth",10,70),location=new Icon(rig.resources,"location",10,85),alarm=new Icon(rig.resources,"alarm_clock",10,100),cast=new Icon(rig.resources,"cast",10,115);
        List<Icon> hints=Arrays.asList(bluetooth,location,alarm,cast);
        for(Icon hint:hints){rig.icons.addView(hint);rig.all.add(hint);ViewGroup.LayoutParams lp=new ViewGroup.MarginLayoutParams(10,30);
            hint.setLayoutParams(lp);rig.nativeParams.add(lp);rig.nativeParents.add(rig.icons);}
        Bundle values=enabled();values.putString(StatusBarLayoutSettings.key("system","side"),"right");
        values.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"left\",\"order\":2},\"location\":{\"side\":\"left\",\"order\":1},\"cast\":{\"side\":\"left\",\"order\":0},\"alarm_clock\":{\"side\":\"native\",\"order\":0}}");
        rig.configure(values);rig.pass();rig.applyStates();for(Icon hint:hints){rig.flow.beforeApply(hint.state,hint);rig.nativeAccess.apply(hint.state,hint);}
        check(screenX(location)<200,"independent location placement crosses to physical left");
        near(screenX(location)+10,screenX(bluetooth),"independent same-side order uses real leaf widths");
        near(screenX(bluetooth)+10,screenX(cast),"positive hint orders precede the native-order zero entry");
        check(screenX(alarm)>=200,"explicit native retains native side rather than inheriting another side");
        for(Icon hint:hints)check(hint.getParent()==rig.icons&&hint.getVisibility()==View.VISIBLE,"native parent and visibility remain owned by SystemUI");
        rig.ownership();rig.flow.releaseRuntime();
    }
    private static void hintZeroAndGateCases(){
        Rig rig=new Rig(0,400,true);Icon bluetooth=addHint(rig,"bluetooth",70),location=addHint(rig,"location",85);
        Icon alarm=addHint(rig,"alarm_clock",100),cast=addHint(rig,"cast",115),unknown=addHint(rig,"vendor_hint",130);
        Bundle values=enabled();values.putString(StatusBarLayoutSettings.key("system","side"),"right");
        values.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"native\",\"order\":0},\"alarm_clock\":{\"side\":\"right\",\"order\":2},\"cast\":{\"side\":\"right\",\"order\":1}}");
        rig.configure(values);rig.pass();
        near(screenX(cast)+10,screenX(alarm),"hint positive 1 then 2 within the same SYSTEM anchor");
        check(screenX(alarm)<screenX(bluetooth),"explicit native zero follows positive positions");
        check(screenX(bluetooth)<screenX(location)&&screenX(location)<screenX(unknown),"native zero, inherited zero and unknown zero keep native physical order");
        values.putString(StatusBarLayoutSettings.key("system","side"),"left");
        values.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"native\",\"order\":0}}");
        rig.configure(values);rig.pass();
        check(screenX(bluetooth)>=200&&screenX(location)<200&&screenX(unknown)<200,"explicit native follows native side while absent/unknown slots inherit SYSTEM side");
        values.putBoolean(StatusBarLayoutSettings.MASTER,false);rig.configure(values);rig.pass();rig.applyStates();
        check(!rig.flow.wanted(),"one sorting master pauses every stored hint placement");
        near(170,screenX(bluetooth),"master off restores the native hint target");near(185,screenX(location),"master off restores inherited target");
        values.putBoolean(StatusBarLayoutSettings.MASTER,true);rig.configure(values);rig.pass();
        check(screenX(bluetooth)>=200&&screenX(location)<200,"master on resumes the saved per-slot placement");
        rig.ownership();rig.flow.detached(rig.root);check(!StatusBarFlowLayout.owns(location),"detach releases hint ownership");
        rig.pass();check(screenX(location)<200,"reattach can recover the retained setting");rig.flow.releaseRuntime();

        Rig rtl=new Rig(0,400,true);rtl.root.direction=View.LAYOUT_DIRECTION_RTL;
        Icon nativeHint=addHint(rtl,"bluetooth",70),physicalRight=addHint(rtl,"location",85);
        Bundle reverse=enabled();reverse.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"native\",\"order\":0},\"location\":{\"side\":\"right\",\"order\":1}}");
        rtl.configure(reverse);rtl.pass();
        check(screenX(nativeHint)<200&&screenX(physicalRight)>=200,"explicit left/right stays physical in RTL while native follows OEM side");
        rtl.ownership();rtl.flow.releaseRuntime();
    }
    /** code284: without the hint layout (ordering or margins only), a system hint the native
     * container hides takes no width, so the following hint closes up instead of leaving an
     * empty slot; once shown natively it takes its slot again. */
    private static void nativeHiddenSystemGapCase(){
        Rig rig=new Rig(0,400,true);
        Icon bluetooth=addHint(rig,"bluetooth",70),location=addHint(rig,"location",85),alarm=addHint(rig,"alarm_clock",100);
        // Native hides location and closes up: alarm takes x=85, the hidden icon keeps its stale x.
        location.state.visible=2;rig.nativeHintXs.put(alarm,85f);
        Bundle values=enabled();rig.configure(values);rig.pass();
        near(screenX(bluetooth)+15,screenX(alarm),"a natively hidden system hint leaves no empty slot (10px icon + 5px native gap)");
        check(location.state.visible==2,"the native hidden decision is kept");
        near(85,location.state.x,"the hidden hint's native state is never written, so the native container alone decides when it returns");
        location.state.visible=0;rig.nativeHintXs.put(alarm,100f);rig.pass();
        near(screenX(bluetooth)+15,screenX(location),"once shown natively it takes its slot again");
        near(screenX(location)+15,screenX(alarm),"and the following hint moves after it");
        rig.ownership();rig.flow.releaseRuntime();
    }
    private static void hintRetentionAndNativeStateCase(){
        Rig rig=new Rig(0,400,true);Icon rejected=addHint(rig,"location",70),retained=addHint(rig,"bluetooth",85);
        rejected.state.visible=2;retained.setAlpha(.35f);retained.setScaleX(.8f);retained.setTranslationY(3);
        rig.flow.setHintRetention(child->child!=rejected);
        Bundle values=enabled();values.putString(NativeStatusIcons.LAYOUT,"{\"location\":{\"side\":\"left\",\"order\":1},\"bluetooth\":{\"side\":\"left\",\"order\":2}}");
        rig.configure(values);rig.pass();
        check(!StatusBarFlowLayout.owns(rejected)&&StatusBarFlowLayout.owns(retained),"native MAX/priority selection precedes the per-slot layout allocation");
        near(60,screenX(retained),"rejected hint consumes no gap before retained hint");check(rejected.state.visible==2,"layout never promotes a native rejected hint");
        near(.35f,retained.getAlpha(),"hint alpha remains native");near(.8f,retained.getScaleX(),"hint scale remains native");near(3,retained.getTranslationY(),"hint Y remains native");
        rig.flow.capsule(rig.icons,areas(100,200));rig.root.runDelayedForCheck();rig.pass();
        check(screenX(retained)<200&&rejected.state.visible==2,"capsule interaction keeps slot placement and native hidden state");
        rig.ownership();rig.flow.releaseRuntime();
    }
    private static void hintHeaderRootsCase(){
        StatusBarFlowLayout flow=new StatusBarFlowLayout();flow.setNativeAccess(new Native());
        Bundle values=enabled();values.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"left\",\"order\":1},\"location\":{\"side\":\"right\",\"order\":1}}");flow.configure(values);
        List<ViewGroup> roots=new ArrayList<>();List<Icon> leaves=new ArrayList<>();List<ViewGroup> parents=new ArrayList<>();
        for(boolean qs:new boolean[]{false,true}){
            NamedResources resources=new NamedResources();ViewGroup root=qs?new QRoot(resources):new KRoot(resources);roots.add(root);
            Box row=new Box(resources,qs?"quick_qs_status_icons":"status_bar_content_container",qs?80:0,0,qs?300:1440,30);root.addView(row);
            if(qs){Box carrierHost=new Box(resources,"qs_clock_container",0,0,80,30);carrierHost.addView(new Leaf(resources,"qs_carrier_text",0,0,80,30));root.addView(carrierHost);}
            Box icons=new Box(resources,"statusIcons",qs?0:1000,0,qs?180:1300,30);icons.container=true;row.addView(icons);
            Icon bluetooth=new Icon(resources,"bluetooth",10,100),location=new Icon(resources,"location",10,120);icons.addView(bluetooth);icons.addView(location);
            leaves.add(bluetooth);leaves.add(location);parents.add(icons);parents.add(icons);
            com.android.systemui.privacy.OngoingPrivacyChip chip=new com.android.systemui.privacy.OngoingPrivacyChip(null);
            chip.resourcesForCheck=resources;chip.setId(resources.id("privacy_chip"));row.addView(chip);
            flow.beforeNativeLayout(root);flow.afterMeasure(root);flow.afterLayout(root);
            check("ready".equals(flow.reason(root)),"per-slot layout supports native "+(qs?"QS":"keyguard")+" root");
            check(screenX(bluetooth)<root.getWidth()*.5f&&screenX(location)>=root.getWidth()*.5f,"header leaves split physical sides without copying");
            check(!StatusBarFlowLayout.owns(chip)&&chip.getParent()==row&&chip.getVisibility()==View.VISIBLE,"standalone privacy chip remains in its native subtree");
            if(qs)check(bluetooth.getLeft()+bluetooth.getTranslationX()>=0&&location.getLeft()+location.getTranslationX()+location.getWidth()<=icons.getWidth(),"QS hardware-layer hull contains both hint projections");
        }
        float other=screenX(leaves.get(2));flow.detached(roots.get(0));
        near(other,screenX(leaves.get(2)),"detaching keyguard does not change QS hint placement");
        for(int i=0;i<leaves.size();i++)check(leaves.get(i).getParent()==parents.get(i),"Phone/keyguard/QS managers retain native parent and indexes");
        flow.releaseRuntime();for(Icon icon:leaves)check(!StatusBarFlowLayout.owns(icon),"release clears every root's hint ownership");
    }
    private static void hintSteadyReadBudgetCase(){
        for(boolean configured:new boolean[]{false,true}){
            Rig rig=new Rig(0,400,true);addHint(rig,"bluetooth",70);Bundle values=enabled();
            if(configured)values.putString(NativeStatusIcons.LAYOUT,"{\"bluetooth\":{\"side\":\"left\",\"order\":1}}");
            rig.configure(values);rig.pass();
            if(!configured)check(rig.nativeAccess.hintReads==0,"default empty placement performs no new native hint classification");
            int reads=rig.nativeAccess.hintReads,requests=rig.root.layoutRequests;
            for(int i=0;i<1000;i++)rig.flow.afterStates(rig.icons);
            check(reads==rig.nativeAccess.hintReads,"measured steady callbacks add no hint reflection/classification for placement="+configured);
            check(requests==rig.root.layoutRequests,"unchanged hint placement requests no repeated layout for placement="+configured);
            rig.flow.releaseRuntime();
        }
    }
    private static Bundle cross() {
        Bundle result = enabled();
        result.putString(StatusBarLayoutSettings.key("clock", "side"), "right");
        result.putString(StatusBarLayoutSettings.key("battery", "side"), "left");
        result.putString(StatusBarLayoutSettings.key("wifi", "side"), "left");
        result.putString(StatusBarLayoutSettings.key("mobile", "side"), "right");
        result.putInt(StatusBarLayoutSettings.key("battery", "order"), 1);
        result.putInt(StatusBarLayoutSettings.key("wifi", "order"), 2);
        result.putInt(StatusBarLayoutSettings.key("mobile", "order"), 1);
        result.putInt(StatusBarLayoutSettings.key("clock", "order"), 2);
        return result;
    }
    /** A real screen position, including native animation translations through each ancestor. */
    private static float screenX(View view) {
        float result = 0; for (View current = view; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
            result += current.getLeft() + current.getTranslationX(); return result;
    }
    private static float screenY(View view) {
        float result=0f;for(View current=view;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)
            result+=current.getTop()+current.getTranslationY();return result;
    }
    /** Current APK dump: Phone's 78+10px clock padding and Keyguard's 64+24px both draw at screen X88.
     * Different native boxes are not evidence of a 14px visual shift. Native vertical motion stays additive. */
    private static void lockScreenBaselines()throws Exception{
        for(boolean locked:new boolean[]{false,true})for(float margin:new float[]{0f,4f}) {
            NamedResources resources=new NamedResources();ViewGroup root;
            if(locked)root=new KRoot(resources);else{Root phone=new Root(resources,0,1440);phone.geometry.set(0,0,1440,160);root=phone;}
            Box contents=new Box(resources,locked?"status_bar_content_container":"status_bar_contents",0,0,1440,160);
            contents.paddingLeft=locked?64:0;contents.paddingRight=locked?88:0;root.addView(contents);
            Box left=new Box(resources,locked?"keyguard_status_bar_contents":"status_bar_start_side_container",locked?64:0,20,666,140);
            left.paddingLeft=locked?0:78;contents.addView(left);
            PaddedLeaf clock=new PaddedLeaf(resources,locked?"keyguard_status_bar_clock":"clock",locked?169:175,locked?24:10);
            clock.geometry.set(locked?0:78,0,(locked?0:78)+clock.getWidth(),120);left.addView(clock);
            Box right=new Box(resources,locked?"status_icon_area":"status_bar_end_side_container",774,20,locked?1352:1440,140);
            right.paddingRight=locked?0:88;contents.addView(right);
            Box system=new Box(resources,"system_icons",200,20,578,100);right.addView(system);
            Leaf battery=new Leaf(resources,"battery",256,0,378,80);system.addView(battery);
            root.setTranslationY(5f);contents.setTranslationY(-2f);clock.setTranslationY(3f);system.setTranslationY(4f);battery.setTranslationY(2f);
            float clockY=screenY(clock),batteryY=screenY(battery);
            StatusBarFlowLayout flow=new StatusBarFlowLayout();flow.setNativeAccess(new Native());Bundle config=enabled();
            config.putString(StatusBarLayoutSettings.key("clock","side"),"left");config.putString(StatusBarLayoutSettings.key("battery","side"),"right");
            config.putFloat(StatusBarLayoutSettings.key("clock","margin_left"),margin);flow.configure(config);
            flow.beforeNativeLayout(root);flow.afterMeasure(root);flow.afterLayout(root);
            check("ready".equals(flow.reason(root)),"real locked/unlocked native padding hierarchy is supported");
            near(88f+margin,screenX(clock)+clock.getPaddingLeft(),"clock screen ink baseline matches across native roots");
            near(1230f,screenX(battery),"right native screen inset matches across roots");
            near(clockY,screenY(clock),"clock preserves nonzero native root/content/leaf Y");
            near(batteryY,screenY(battery),"battery preserves native nested Y and animation");
            java.lang.reflect.Method details=StatusBarFlowLayout.class.getDeclaredMethod("verticalLeaves",ViewGroup.class);details.setAccessible(true);
            String diagnostic=(String)details.invoke(null,root);
            check(diagnostic.contains("clock=sy:"+Math.round(clockY)+",t:0,w:"),"changed-only diagnostics include actual clock screen position");
            clock.geometry.measuredHeight=0;String missing=(String)details.invoke(null,root);
            check(missing.contains(",mh:0,"),"diagnostics distinguish a zero measured child from equal parent Y");
            clock.geometry.measuredHeight=120;battery.layout(battery.getLeft(),7,battery.getRight(),87);battery.setTranslationY(9f);
            float latest=screenY(battery);flow.configure(new Bundle());
            near(latest,screenY(battery),"release never restores stale locked/unlocked child Y");
            near(5f,root.getTranslationY(),"native root unlock animation is retained on release");
        }
    }
    private static void obstacleCases() {
        for (boolean group : new boolean[]{false, true}) {
            Rig obstacle = new Rig(0, 300); obstacle.configure(cross());
            View unknown = group ? new Box(obstacle.resources, "privacy_chip", 120, 0, 180, 30)
                    : new Leaf(obstacle.resources, "privacy_chip", 120, 0, 180, 30);
            obstacle.root.addView(unknown); int measures = obstacle.icons.geometry.measures;
            obstacle.pass();
            check("unsupported_structure".equals(obstacle.flow.reason(obstacle.root)), "visible unknown obstacle fails closed before ownership");
            check(obstacle.icons.geometry.measures == measures, "unsupported row is not partially remeasured");
            near(100f, obstacle.right.getLeft(), "unknown obstacle retains native wrapper bounds");
            near(125f, obstacle.wifi.state.x, "unknown obstacle retains original native state");
            check(obstacle.wifi.nativeApplications == 0, "unsupported row never applies a partial plan");
            obstacle.ownership();
        }
        Rig absent = new Rig(0, 300); absent.configure(cross());
        Leaf gone = new Leaf(absent.resources, "privacy_chip", 120, 0, 180, 30); gone.setVisibility(View.GONE); absent.root.addView(gone);
        absent.pass(); check("ready".equals(absent.flow.reason(absent.root)), "GONE obstacle consumes no layout budget"); absent.ownership();
        Rig animated = new Rig(0, 300); animated.configure(cross());
        Leaf fading = new Leaf(animated.resources, "privacy_chip", 120, 0, 180, 30); fading.setAlpha(0f); animated.root.addView(fading);
        animated.pass(); check("unsupported_structure".equals(animated.flow.reason(animated.root)),
                "VISIBLE obstacle with zero animated alpha is still reserved for its native fade-in"); animated.ownership();
    }
    private static void cutoutAndMarginCases() {
        Rig cutout = new Rig(0, 300); cutout.configure(cross());
        Leaf hole = new Leaf(cutout.resources, "cutout_space_view", 135, 0, 165, 30); cutout.root.addView(hole);
        cutout.pass(); check("ready".equals(cutout.flow.reason(cutout.root)), "native cutout spacer supports finite split flow");
        near(0f, screenX(cutout.battery), "split left interval starts at native safe edge");
        near(25f, screenX(cutout.wifi), "split left icons retain left order");
        near(210f, screenX(cutout.mobile), "split right group respects native cutout");
        near(240f, screenX(cutout.clock), "split right clock stays before safe edge");
        near(135f, hole.getLeft(), "cutout native obstacle is not moved"); cutout.ownership();

        Rig narrow = new Rig(0, 150); narrow.configure(cross());
        narrow.root.addView(new Leaf(narrow.resources, "cutout_space", 60, 0, 90, 30));
        narrow.pass(); check("width_overflow".equals(narrow.flow.reason(narrow.root)), "cutout shortage is explicit overflow");
        near(0f, screenX(narrow.battery), "overflow keeps battery priority");
        near(25f, screenX(narrow.wifi), "remaining left budget fits wifi");
        near(90f, screenX(narrow.clock), "clock anchors to finite right interval");
        check(narrow.mobile.state.visible == 2, "lower priority mobile overflow uses native hidden state");
        near(1f, narrow.mobile.getAlpha(), "overflow never rewrites native alpha");
        narrow.flow.beforeStates(narrow.icons); check(narrow.mobile.state.visible == 0, "overflow native hidden state is restored before calculation");
        narrow.ownership();

        for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
            Rig margins = new Rig(0, 300); margins.root.direction = direction; margins.root.paddingLeft = 9; margins.root.paddingRight = 17;
            ViewGroup.MarginLayoutParams batteryParams = (ViewGroup.MarginLayoutParams) margins.battery.getLayoutParams();
            batteryParams.leftMargin = 3; batteryParams.rightMargin = 4;
            ViewGroup.MarginLayoutParams wifiParams = (ViewGroup.MarginLayoutParams) margins.wifi.getLayoutParams();
            wifiParams.leftMargin = 2; wifiParams.rightMargin = 5;
            Bundle value = cross(); value.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), 11);
            value.putFloat(StatusBarLayoutSettings.key("wifi", "margin_right"), 13); margins.configure(value);
            margins.pass(); near(12f, screenX(margins.battery), "physical native left padding and margin remain physical under RTL");
            // Device code162: the innermost Wi-Fi's centre-facing right margin moves it left only
            // up to its neighbour's native gap (43), never over the battery (41 overlapped it).
            near(43f, screenX(margins.wifi), "physical user left margin and native slot margin sum without mirroring; the innermost Wi-Fi's centre-facing right margin moves it left");
            near(193f, screenX(margins.mobile), "native right safe padding is respected");
            near(223f, screenX(margins.clock), "right clock ends at native safe padding"); margins.ownership();
        }
        Rig huge = new Rig(0, 300); Bundle value = cross();
        value.putFloat(StatusBarLayoutSettings.key("wifi", "margin_left"), Float.MAX_VALUE); huge.configure(value); huge.pass();
        check("width_overflow".equals(huge.flow.reason(huge.root)), "huge user margin gives bounded explicit overflow");
        check(huge.wifi.state.visible == 2, "huge wifi budget never covers the battery");
        near(0f, screenX(huge.battery), "huge margin cannot evict higher priority battery");
        near(240f, screenX(huge.clock), "huge margin cannot evict higher priority time");
        huge.flow.configure(new Bundle()); check(huge.wifi.state.visible == 0, "disable restores native icon visibility"); huge.ownership();
    }
    private static void nativeSideCase() {
        Rig rtl = new Rig(0, 300); rtl.root.direction = View.LAYOUT_DIRECTION_RTL;
        // This models OEM native RTL layout rather than merely changing the direction flag.
        rtl.left.geometry.original = new int[]{200, 0, 300, 30};
        rtl.right.geometry.original = new int[]{0, 0, 200, 30};
        rtl.clockHost.geometry.original = new int[]{40, 0, 100, 30};
        rtl.icons.geometry.original = new int[]{25, 0, 200, 30};
        rtl.battery.geometry.original = new int[]{0, 0, 25, 30};
        rtl.wifiNativeX = 30; rtl.mobileNativeX = 0; rtl.configure(enabled()); rtl.pass();
        check("ready".equals(rtl.flow.reason(rtl.root)), "native RTL geometry remains supported");
        near(0f, screenX(rtl.battery), "native side follows actual battery zone rather than token kind");
        near(25f, screenX(rtl.mobile), "native side follows actual mobile zone");
        near(55f, screenX(rtl.wifi), "native side follows actual wifi zone and native ordinal");
        near(240f, screenX(rtl.clock), "native side follows actual right clock zone"); rtl.ownership();
    }
    private static void nativeLocalRectCase() {
        Rig inset = new Rig(0, 300); inset.wifi.geometry.original = new int[]{3, 0, 23, 30}; inset.configure(cross()); inset.pass();
        near(3f, inset.wifi.getLeft(), "nonzero native child local rect is not overwritten");
        near(25f, screenX(inset.wifi), "native state target includes the retained child local origin"); inset.ownership();
    }
    private static void nativeReflowResidualCase() {
        Rig reflow = new Rig(0, 300); reflow.configure(cross()); reflow.pass();
        reflow.flow.beforeNativeLayout(reflow.root); reflow.root.geometry.reset();
        for (View view : reflow.all) if (view instanceof Box) ((Box) view).geometry.reset(); else ((Leaf) view).geometry.reset();
        reflow.flow.afterMeasure(reflow.root);
        // Root native layout invokes child updateStates and the native Seedling coordinator before returning.
        reflow.flow.beforeStates(reflow.icons); reflow.wifi.state.x = 125; reflow.mobile.state.x = 145;
        reflow.flow.prepareTargets(reflow.icons); reflow.wifi.state.x += 9;
        reflow.flow.beforeApply(reflow.wifi.state, reflow.wifi);
        reflow.nativeAccess.apply(reflow.wifi.state, reflow.wifi);
        reflow.flow.afterLayout(reflow.root);
        near(34f, screenX(reflow.wifi), "fresh root scene retains the Seedling residual from this native layout transaction");
        reflow.ownership();
    }
    private static void unsupportedReflowCase() {
        for (boolean prepared : new boolean[]{false, true}) {
            Rig fallback = new Rig(0, 300); fallback.configure(cross()); fallback.pass();
            fallback.flow.beforeNativeLayout(fallback.root); fallback.root.geometry.reset();
            for (View view : fallback.all) if (view instanceof Box) ((Box) view).geometry.reset(); else ((Leaf) view).geometry.reset();
            fallback.flow.afterMeasure(fallback.root); fallback.flow.beforeStates(fallback.icons);
            fallback.wifi.state.x = 125; fallback.mobile.state.x = 145;
            if (prepared) { fallback.flow.prepareTargets(fallback.icons); fallback.wifi.state.x += 9; }
            else fallback.wifi.state.x = 77;
            fallback.flow.beforeApply(fallback.wifi.state, fallback.wifi);
            fallback.nativeAccess.apply(fallback.wifi.state, fallback.wifi);
            Leaf obstacle = new Leaf(fallback.resources, "privacy_chip", 120, 0, 180, 30); fallback.root.addView(obstacle);
            fallback.flow.afterLayout(fallback.root);
            check("unsupported_structure".equals(fallback.flow.reason(fallback.root)), "new obstacle aborts the native layout transaction");
            near(prepared ? 134f : 77f, fallback.wifi.state.x,
                    "abort restores only proven custom target basis and retains original native animation");
            near(prepared ? 234f : 177f, screenX(fallback.wifi), "native fallback applies the restored target immediately");
            near(145f, fallback.mobile.state.x, "native fallback leaves no unanimated custom mobile target");
            near(100f, fallback.right.getLeft(), "unsupported new row stays on its original physical area");
            fallback.ownership();
            obstacle.setVisibility(View.GONE); fallback.pass();
            check("ready".equals(fallback.flow.reason(fallback.root)), "row safely resumes after obstacle leaves native layout");
            near(25f, screenX(fallback.wifi), "resumed scene has no residual from aborted custom target"); fallback.ownership();
        }
    }
    private static void contentWrapperCases() {
        for (float alpha : new float[]{1f, 0f}) {
            Rig privateScene = new Rig(7, 300, true); privateScene.configure(cross());
            Leaf privacy = new Leaf(privateScene.resources, "privacy_chip", 120, 0, 180, 30); privacy.setAlpha(alpha);
            privateScene.root.addView(privacy); int measures = privateScene.icons.geometry.measures;
            privateScene.pass();
            check("unsupported_structure".equals(privateScene.flow.reason(privateScene.root)),
                    "active root sibling outside contents aborts packing even at animated alpha zero");
            check(privateScene.icons.geometry.measures == measures, "outer privacy sibling prevents even native container remeasure");
            near(0f, privateScene.contents.getLeft(), "outer obstacle leaves native content origin unchanged");
            near(300f, privateScene.contents.getWidth(), "outer obstacle leaves native content width unchanged");
            near(125f, privateScene.wifi.state.x, "outer obstacle leaves native slot target unchanged");
            near(120f, privacy.getLeft(), "outer privacy sibling never receives geometry ownership");
            check(!StatusBarFlowLayout.owns(privacy), "outer privacy sibling never receives transition ownership"); privateScene.ownership();
        }
        Rig holeScene = new Rig(7, 150, true); holeScene.configure(cross());
        Leaf cutout = new Leaf(holeScene.resources, "cutout_space_view", 60, 0, 90, 30); holeScene.root.addView(cutout);
        holeScene.pass();
        check("width_overflow".equals(holeScene.flow.reason(holeScene.root)), "cutout root sibling remains a finite reserved split interval");
        near(7f, screenX(holeScene.battery), "content scene left group uses root physical safe edge");
        near(32f, screenX(holeScene.wifi), "content scene left wifi fits before root sibling cutout");
        near(97f, screenX(holeScene.clock), "content scene right clock uses budget after root sibling cutout");
        check(holeScene.mobile.state.visible == 2, "root sibling cutout reduces capacity rather than being silently ignored");
        near(60f, cutout.getLeft(), "reserved cutout root sibling stays at its native layout position");
        near(90f, cutout.getRight(), "reserved cutout root sibling keeps its native width");
        check(!StatusBarFlowLayout.owns(cutout), "reserved cutout sibling never receives endpoint transition ownership");
        holeScene.ownership();
    }
    private static void inactiveNativeShellCases() {
        Rig nativeRig = new Rig(0, 300, true);
        Box dynamic = new Box(nativeRig.resources, "dynamic_icon_group", 160, 0, 220, 30);
        Box dynamicContent = new Box(nativeRig.resources, "dynamic_icon_layout", 0, 0, 60, 30);
        Leaf text = new Leaf(nativeRig.resources, "dynamic_icon_text", 0, 0, 60, 30); text.setVisibility(View.GONE);
        dynamic.addView(dynamicContent); dynamicContent.addView(text); nativeRig.root.addView(dynamic);
        Box privacyWindow = new Box(nativeRig.resources, "privacy_chip_window", 0, 0, 32, 30);
        dynamic.addView(privacyWindow);
        Bundle options = enabled(); options.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 11);
        nativeRig.configure(options); nativeRig.pass();
        check("ready".equals(nativeRig.flow.reason(nativeRig.root)), "native empty dynamic and privacy shells do not disable entire row");
        near(11, screenX(nativeRig.clock), "margin participates in native layout beside measured empty shells");
        check(!StatusBarFlowLayout.owns(dynamic) && !StatusBarFlowLayout.owns(privacyWindow), "inactive OEM shells remain entirely native");
        near(160, dynamic.getLeft(), "inactive shell geometry never taken over");
        text.setVisibility(View.VISIBLE); text.setAlpha(0); nativeRig.pass();
        check("unsupported_structure".equals(nativeRig.flow.reason(nativeRig.root)), "active native dynamic content still blocks even at alpha zero");
        near(0, screenX(nativeRig.clock), "active shell rolls margin back before native draw");
        text.setVisibility(View.GONE); nativeRig.pass();
        near(11, screenX(nativeRig.clock), "margin recovers when native dynamic shell becomes inactive again");

        Rig promptRig = new Rig(0, 300, true);
        com.oplus.systemui.statusbar.widget.HiddenReceiverFrameLayout prompt =
                new com.oplus.systemui.statusbar.widget.HiddenReceiverFrameLayout(null) {
                    @Override public Resources getResources() { return promptRig.resources; }
                    @Override public int getWidth() { return 80; }
                    @Override public int getMeasuredWidth() { return 80; }
                };
        prompt.setId(promptRig.resources.id("prompt_view_group"));
        Leaf hiddenPrompt = new Leaf(promptRig.resources, "system_prompt_view", 0, 0, 80, 30); hiddenPrompt.setVisibility(View.GONE);
        prompt.addView(hiddenPrompt); promptRig.left.addView(prompt);
        // Use the real class name; subclasses are deliberately not accepted as native shells.
        promptRig.configure(options); promptRig.pass();
        check("unsupported_structure".equals(promptRig.flow.reason(promptRig.root)), "unknown subclass cannot impersonate the verified shell");
        promptRig.left.removeView(prompt);
        prompt.removeView(hiddenPrompt);
        com.oplus.systemui.statusbar.widget.HiddenReceiverFrameLayout exactPrompt =
                new com.oplus.systemui.statusbar.widget.HiddenReceiverFrameLayout(null);
        exactPrompt.resourcesForCheck = promptRig.resources;
        exactPrompt.setId(promptRig.resources.id("prompt_view_group"));
        exactPrompt.layout(0, 0, 80, 30); exactPrompt.addView(hiddenPrompt); promptRig.left.addView(exactPrompt);
        promptRig.pass(); near(11, screenX(promptRig.clock), "empty native prompt does not disable margins");
        hiddenPrompt.setVisibility(View.VISIBLE); promptRig.pass();
        check("unsupported_structure".equals(promptRig.flow.reason(promptRig.root)), "active prompt continues to protect its native interactive contents");
        nativeRig.ownership(); promptRig.ownership();
    }
    private static void qsNativeRowCases() {
        NamedResources resources = new NamedResources(); QRoot root = new QRoot(resources);
        Box row = new Box(resources, "quick_qs_status_icons", 80, 0, 300, 30); row.setFocusable(true);
        Box carrierHost = new Box(resources, "qs_clock_container", 0, 0, 80, 30);
        Leaf carrier = new Leaf(resources, "qs_carrier_text", 0, 0, 80, 30); carrierHost.addView(carrier);
        Box icons = new Box(resources, "icons", 0, 0, 180, 30);
        Box nativeIcons = new Box(resources, "statusIcons", 0, 0, 180, 30); nativeIcons.container = true;
        Icon wifi = new Icon(resources, "wifi", 20, 100); nativeIcons.addView(wifi); icons.addView(nativeIcons);
        Leaf battery = new Leaf(resources, "battery", 180, 0, 205, 30);
        row.addView(icons); row.addView(battery); root.addView(carrierHost); root.addView(row);
        Leaf unrelatedButton = new Leaf(resources, "prompt_container", 0, 50, 200, 90); unrelatedButton.setClickable(true); root.addView(unrelatedButton);
        com.android.systemui.privacy.OngoingPrivacyChip chip = new com.android.systemui.privacy.OngoingPrivacyChip(null);
        chip.resourcesForCheck = resources; chip.setId(resources.id("privacy_chip")); icons.addView(chip);
        StatusBarFlowLayout flow = new StatusBarFlowLayout(); flow.setNativeAccess(new Native());
        Bundle marginOnly = new Bundle(); marginOnly.putFloat(StatusBarLayoutSettings.key("battery", "margin_right"), 9);
        marginOnly.putFloat(StatusBarLayoutSettings.key("carrier", "margin_left"), 7);
        // Persisted ordering remains inactive while margins are independently applied.
        marginOnly.putString(StatusBarLayoutSettings.key("battery", "side"), "left");
        marginOnly.putFloat(StatusBarLayoutSettings.key("battery", "order"), 1);
        flow.configure(marginOnly); flow.afterMeasure(root); flow.afterLayout(root);
        check("ready".equals(flow.reason(root)), "QS actual row excludes unrelated date/control siblings");
        near(300 - 25 - 9, screenX(battery), "QS battery physical margin active with sorting disabled");
        near(7, screenX(carrier), "QS carrier shares real header row despite narrower icons content box");
        near(50, unrelatedButton.getTop(), "other QS rows never move or lose click target");
        check(!StatusBarFlowLayout.owns(chip) && !StatusBarFlowLayout.owns(unrelatedButton), "empty privacy shell and other rows remain native");
        flow.beforeNativeLayout(root); chip.privacyList.add(new Object()); flow.afterLayout(root);
        check("unsupported_structure".equals(flow.reason(root)), "active QS privacy list cannot be ignored as empty shell");
        chip.privacyList.clear(); flow.beforeNativeLayout(root); flow.afterLayout(root);
        check("ready".equals(flow.reason(root)), "QS margin resumes after privacy closes");
        // Device code162: the notification page's icon row has a hardware layer of its own size; a battery
        // ordered to the left must stay inside the row, which widens over the header instead of cutting it.
        Bundle ordered = new Bundle(); ordered.putBoolean(StatusBarLayoutSettings.MASTER, true);
        ordered.putString(StatusBarLayoutSettings.key("battery", "side"), "left");
        ordered.putFloat(StatusBarLayoutSettings.key("battery", "order"), 1);
        flow.configure(ordered); flow.beforeNativeLayout(root); flow.afterLayout(root);
        check("ready".equals(flow.reason(root)), "QS ordering ready");
        near(0, screenX(battery), "QS battery ordered to the left edge of the header row");
        check(battery.getLeft() >= 0 && battery.getRight() <= row.getWidth(), "the icon row contains the moved battery: "
                + battery.getLeft() + ".." + battery.getRight() + " in " + row.getWidth());
        near(0, row.getLeft(), "the icon row widened to the header start");
        flow.releaseRuntime(); near(180, battery.getLeft(), "QS close restores original natural battery geometry");
        near(80, row.getLeft(), "QS close restores the native icon row");
    }
    /** Device code279: crowded header icons start left of the middle; they still belong to the right group. */
    private static void qsCrowdedSideCase() {
        NamedResources resources = new NamedResources(); QRoot root = new QRoot(resources);
        Box row = new Box(resources, "quick_qs_status_icons", 80, 0, 300, 30);
        Box carrierHost = new Box(resources, "qs_clock_container", 0, 0, 80, 30);
        Leaf carrier = new Leaf(resources, "qs_carrier_text", 0, 0, 80, 30); carrierHost.addView(carrier);
        Box icons = new Box(resources, "icons", 0, 0, 180, 30);
        Box nativeIcons = new Box(resources, "statusIcons", 0, 0, 180, 30); nativeIcons.container = true;
        Icon wifi = new Icon(resources, "wifi", 20, 0); nativeIcons.addView(wifi); icons.addView(nativeIcons);
        Leaf battery = new Leaf(resources, "battery", 180, 0, 205, 30);
        row.addView(icons); row.addView(battery); root.addView(carrierHost); root.addView(row);
        StatusBarFlowLayout flow = new StatusBarFlowLayout(); flow.setNativeAccess(new Native());
        Bundle marginOnly = new Bundle(); marginOnly.putFloat(StatusBarLayoutSettings.key("battery", "margin_right"), 9);
        flow.configure(marginOnly); flow.afterMeasure(root); flow.afterLayout(root);
        check("ready".equals(flow.reason(root)), "crowded QS row ready");
        near(300 - 25 - 9, screenX(battery), "QS battery keeps its margin");
        near(300 - 25 - 9 - 20, screenX(wifi), "an icon starting left of the header middle stays next to the battery on the right");
        near(0, screenX(carrier), "the carrier keeps the left side");
    }
    private static void paddedHeaderMarginCases() {
        NamedResources resources = new NamedResources(); QRoot root = new QRoot(resources);
        Box row = new Box(resources, "quick_qs_status_icons", 80, 0, 300, 30);
        row.paddingLeft = 12; row.paddingRight = 14;
        row.setClipChildren(true); row.setClipToPadding(true);
        Box icons = new Box(resources, "icons", 0, 0, 180, 30);
        Box nativeIcons = new Box(resources, "statusIcons", 0, 0, 180, 30); nativeIcons.container = true;
        nativeIcons.addView(new Icon(resources, "wifi", 20, 100)); icons.addView(nativeIcons);
        Leaf battery = new Leaf(resources, "battery", 180, 0, 205, 30);
        Box batteryHost = new Box(resources, "system_icons", 180, 0, 220, 30);
        battery.geometry.set(4, 0, 29, 30); battery.geometry.original = new int[]{4, 0, 29, 30};
        batteryHost.paddingLeft = 4; batteryHost.paddingRight = 8;
        batteryHost.setClipChildren(true); batteryHost.setClipToPadding(true); batteryHost.addView(battery);
        row.addView(icons); row.addView(batteryHost); root.addView(row);
        StatusBarFlowLayout flow = new StatusBarFlowLayout(); flow.setNativeAccess(new Native());
        for (float margin : new float[]{6, 12, 24, 6}) {
            Bundle config = new Bundle(); config.putFloat(StatusBarLayoutSettings.key("battery", "margin_right"), margin);
            flow.configure(config); flow.beforeNativeLayout(root); flow.afterMeasure(root); flow.afterLayout(root);
            check("ready".equals(flow.reason(root)), "padded notification header stays supported");
            near(300 - 14 - 25 - margin, screenX(battery), "repeated margin changes preserve the screen target");
            check(battery.getLeft() >= batteryHost.getPaddingLeft()
                            && battery.getRight() <= batteryHost.getWidth() - batteryHost.getPaddingRight(),
                    "a moved battery must fit the actual padded clipping viewport: " + battery.getLeft() + ".."
                            + battery.getRight() + " in " + batteryHost.getPaddingLeft() + ".." + (batteryHost.getWidth() - batteryHost.getPaddingRight()));
            check(row.getClipChildren() && row.getClipToPadding(), "native clipping ownership remains intact");
        }
        flow.releaseRuntime(); near(80, row.getLeft(), "release restores padded header origin");
        near(220, row.getWidth(), "release restores padded header width");
        near(4, battery.getLeft(), "release restores original battery coordinate");
        near(12, row.getPaddingLeft(), "native leading padding is unchanged");
        near(14, row.getPaddingRight(), "native trailing padding is unchanged");
    }
    private static void exactCallbackCases() throws Throwable {
        Rig rig = new Rig(0, 300, true);
        rig.flow.observed(rig.root); int requests = rig.root.layoutRequests;
        Bundle marginOnly = new Bundle(); marginOnly.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        rig.configure(marginOnly);
        check(rig.root.layoutRequests == requests + 1, "first margin activation requests layout on already-inflated native root");
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
        int[] runs = {0};
        Object result = callbacks.dispatch(rig.root, false, () -> {
            runs[0]++;
            return callbacks.dispatch(rig.root, false, () -> { runs[0]++; return "native-result"; });
        });
        check("native-result".equals(result) && runs[0] == 2, "nested native callbacks proceed once each and retain native return");
        check(rig.wifi.nativeApplications == 1, "same root and phase plans once even with hooked inherited super callback");
        near(12, screenX(rig.clock), "exact native onLayout callback applies standalone margin");
        try { callbacks.dispatch(rig.root, false, () -> { throw new IllegalStateException("native abort"); });
            throw new AssertionError("missing native exception");
        } catch (IllegalStateException expected) { check(expected.getMessage().equals("native abort"), "native exception survives callback cleanup"); }
        near(0, screenX(rig.clock), "native exception restores owned geometry");
        callbacks.dispatch(rig.root, false, () -> "recovered");
        near(12, screenX(rig.clock), "exception cannot leave callback depth stuck");
        check(StatusBarFlowCallbacks.virtual(Root.class, "onMeasure", Integer.TYPE, Integer.TYPE).getDeclaringClass() == Root.class,
                "resolver selects most specific native callback");
        check(StatusBarFlowCallbacks.virtual(Root.class, "onLayout", Boolean.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
                .getDeclaringClass() != Root.class, "resolver also binds inherited callback when Phone has no override");
        Leaf unrelated = new Leaf(rig.resources, "other_panel", 0, 0, 300, 30);
        check("transparent".equals(callbacks.dispatch(unrelated, false, () -> "transparent")), "nonstatus roots always proceed without flow");
        rig.configure(new Bundle()); int applications = rig.wifi.nativeApplications;
        check("off".equals(callbacks.dispatch(rig.root, false, () -> "off")), "disabled flow proceeds transparently");
        check(rig.wifi.nativeApplications == applications, "disabled root callback cannot pack native icons");
    }
    /** Phone dump: notification_icon_area -> anonymous OEM LinearLayout -> notificationIcons. */
    private static void anonymousNotificationWrapperCases() {
        for (String token : new String[]{StatusBarLayoutSettings.CLOCK, StatusBarLayoutSettings.NOTIFICATION}) {
            Rig rig = new Rig(0, 300, true);
            Box area = new Box(rig.resources, "notification_icon_area", 60, 0, 100, 30);
            com.android.keyguard.AlphaOptimizedLinearLayout wrapper =
                    new com.android.keyguard.AlphaOptimizedLinearLayout(rig.resources, 40, 30);
            Leaf notifications = new Leaf(rig.resources, "notificationIcons", 0, 0, 40, 30);
            rig.left.addView(area); area.addView(wrapper); wrapper.addView(notifications);
            ViewGroup.MarginLayoutParams wrapperParams = new ViewGroup.MarginLayoutParams(40, 30);
            ViewGroup.MarginLayoutParams iconParams = new ViewGroup.MarginLayoutParams(40, 30);
            wrapper.setLayoutParams(wrapperParams); notifications.setLayoutParams(iconParams);
            rig.configure(new Bundle()); rig.pass();
            near(0, screenX(rig.clock), "zero standalone gap preserves native clock before activation");
            near(60, screenX(notifications), "zero standalone gap preserves native notification position");
            Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key(token, "margin_left"), 12);
            rig.configure(margin); rig.pass();
            check("ready".equals(rig.flow.reason(rig.root)), "exact real anonymous notification wrapper allows scene collection");
            near(StatusBarLayoutSettings.CLOCK.equals(token) ? 12 : 0, screenX(rig.clock), "clock standalone gap is actual layout");
            near(72, screenX(notifications), "twelve dp reserves real space through native anonymous wrapper");
            check(wrapper.getParent() == area && notifications.getParent() == wrapper,
                    "anonymous native notification wrapper keeps original parents");
            check(wrapper.getLayoutParams() == wrapperParams && notifications.getLayoutParams() == iconParams,
                    "native notification layout params retain original identities");
            check(notifications.translationWrites == 0, "notification gap never writes a drawing translation");
            rig.configure(new Bundle());
            near(0, screenX(rig.clock), "return to zero restores native clock layout");
            near(60, screenX(notifications), "return to zero restores notification wrapper geometry");
            check(!rig.flow.wanted(), "zero margins with sorting off stop flow ownership");
            rig.ownership();

            // Same class elsewhere, a named wrapper, an interactive wrapper and a real
            // privacy obstacle must remain unsupported; never admit arbitrary anonymous groups.
            for (int obstacle = 0; obstacle < 4; obstacle++) {
                if (obstacle == 0) area.setId(rig.resources.id("system_icon_area"));
                if (obstacle == 1) { area.setId(rig.resources.id("notification_icon_area")); wrapper.setId(rig.resources.id("other_wrapper")); }
                if (obstacle == 2) { wrapper.setId(View.NO_ID); wrapper.setClickable(true); }
                if (obstacle == 3) { wrapper.setClickable(false); wrapper.addView(new Leaf(rig.resources, "active_privacy", 0, 0, 15, 30)); }
                rig.configure(margin); rig.pass();
                check("unsupported_structure".equals(rig.flow.reason(rig.root)), "strict wrapper boundary preserves obstacle " + obstacle);
                near(0, screenX(rig.clock), "unsupported wrapper never partially moves clock");
                rig.configure(new Bundle());
            }
        }
    }
    private static void existingAnchorCases() {
        Rig late = new Rig(0, 300, true);
        late.clock.setContentDescription("must-not-enter-summary");
        check(late.flow.summary().contains("knownroots=0"), "unobserved preinflated root starts unbound");
        late.flow.observedAnchor(late.clock); late.flow.observedAnchor(late.battery);
        check(late.flow.summary().contains("knownroots=1"), "existing native anchors bind their one ancestor root idempotently");
        int requests = late.root.layoutRequests;
        Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        late.configure(margin);
        check(late.root.layoutRequests == requests + 1, "settings activation requests layout on root found from preexisting clock");
        late.flow.beforeNativeMeasure(late.root); late.pass();
        String summary = late.flow.summary();
        check(summary.contains("enabled=true") && summary.contains("scenes=1") && summary.contains("reasons={ready=1}"),
                "diagnostics distinguish active planned scene from missing callbacks");
        check(summary.contains("measureCallbacks=1") && summary.contains("layoutCallbacks=1"), "diagnostics count exact native geometry callbacks");
        check(!summary.contains("must-not-enter-summary") && !summary.contains("notificationIcons"), "summary never includes contents or view IDs");
        near(12, screenX(late.clock), "discovered preinflated root applies requested gap");

        Rig deep = new Rig(0, 300);
        ViewGroup parent = deep.root;
        for (int i = 0; i < 16; i++) { Box box = new Box(deep.resources, "unrelated_shell", 0, 0, 300, 30); parent.addView(box); parent = box; }
        Leaf leaf = new Leaf(deep.resources, "unrelated_anchor", 0, 0, 10, 30); parent.addView(leaf);
        deep.flow.observedAnchor(leaf); deep.flow.observedAnchor(null);
        check(deep.flow.summary().contains("knownroots=0"), "anchor lookup is bounded to sixteen ancestors");
    }
    private static void firstNativeMeasureCases() {
        Rig rig = new Rig(0, 300, true);
        NativeFrame area = new NativeFrame(rig.resources, "notification_icon_area", 60, 0, 100, 30);
        NativeFrame notifications = new NativeFrame(rig.resources, "notificationIcons", 0, 0, 40, 30);
        FirstMeasureLeaf arriving = new FirstMeasureLeaf(rig.resources);
        rig.left.addView(area); area.addView(notifications); notifications.addView(arriving);
        Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        rig.configure(margin);
        // A binder can add the child after the native measure/layout walk. Its queued
        // first traversal still belongs to Android when our root afterLayout runs.
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check(arriving.isLayoutRequested(), "flow must not consume a new notification's pending first measure");
        check(area.nativeLayouts == 0 && notifications.nativeLayouts == 0,
                "pending zero-size descendants keep native ancestor layout ownership");
        check(arriving.getMeasuredHeight() == 0 && arriving.getHeight() == 0,
                "flow never invents the first notification measurement or height");

        // The next genuine native traversal supplies a height different from the row.
        arriving.geometry.measuredWidth = 20; arriving.geometry.measuredHeight = 40;
        area.layout(60, 0, 100, 30);
        check(arriving.getHeight() == 40 && arriving.getTop() == -5,
                "real native layout centers the measured 40px icon in its 30px row");
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)), "native completion makes the queued flow usable without polling");
        near(72f, screenX(notifications), "the same requested gap applies after the first real measurement");
        check(arriving.getMeasuredHeight() == 40 && arriving.getHeight() == 40 && arriving.getTop() == -5,
                "horizontal packing retains independent native measured and laid-out heights");

        FirstMeasureLeaf duringRestore = new FirstMeasureLeaf(rig.resources);
        notifications.addView(duringRestore);
        int beforeRestore = area.nativeLayouts;
        rig.configure(new Bundle());
        check(duringRestore.isLayoutRequested() && area.nativeLayouts == beforeRestore,
                "settings restoration also leaves the new child's first traversal to Android");
        check(duringRestore.getMeasuredHeight() == 0 && duringRestore.getHeight() == 0,
                "restoration does not substitute synthetic first-measure dimensions");
        duringRestore.geometry.measuredWidth = 20; duringRestore.geometry.measuredHeight = 40;
        area.layout(60, 0, 100, 30);
        near(60f, screenX(notifications), "the genuine native traversal restores the disabled gap");

        Rig hidden = new Rig(0, 300, true);
        NativeFrame hiddenArea = new NativeFrame(hidden.resources, "notification_icon_area", 60, 0, 100, 30);
        NativeFrame hiddenIcons = new NativeFrame(hidden.resources, "notificationIcons", 0, 0, 40, 30);
        FirstMeasureLeaf hiddenChild = new FirstMeasureLeaf(hidden.resources);
        hiddenChild.setVisibility(View.GONE); hidden.left.addView(hiddenArea); hiddenArea.addView(hiddenIcons); hiddenIcons.addView(hiddenChild);
        hidden.configure(margin); hidden.flow.beforeNativeLayout(hidden.root); hidden.flow.afterLayout(hidden.root);
        check("ready".equals(hidden.flow.reason(hidden.root)), "native GONE placeholders never hold a valid flow scene open");
        near(72f, screenX(hiddenIcons), "other native slots still get their configured gap beside an unmeasured GONE placeholder");
        hiddenChild.setVisibility(View.VISIBLE); hiddenChild.pending = false;
        hidden.flow.beforeNativeLayout(hidden.root); hidden.flow.afterLayout(hidden.root);
        check("ready".equals(hidden.flow.reason(hidden.root)), "an intentionally measured VISIBLE 0px placeholder does not block native flow");

        // Captured boot hierarchy: notificationIcons is 0x120 rather than 0x0,
        // so it is a recognized host but not yet a plannable width endpoint.
        Rig zeroWidth = new Rig(0, 300, true);
        NativeFrame zeroArea = new NativeFrame(zeroWidth.resources, "notification_icon_area", 60, 0, 60, 30);
        NativeFrame zeroIcons = new NativeFrame(zeroWidth.resources, "notificationIcons", 0, 0, 0, 30);
        FirstMeasureLeaf zeroChild = new FirstMeasureLeaf(zeroWidth.resources);
        zeroWidth.left.addView(zeroArea); zeroArea.addView(zeroIcons); zeroIcons.addView(zeroChild);
        zeroWidth.configure(margin); zeroWidth.flow.beforeNativeLayout(zeroWidth.root); zeroWidth.flow.afterLayout(zeroWidth.root);
        check("pending_native_measure".equals(zeroWidth.flow.reason(zeroWidth.root)) && !StatusBarFlowLayout.owns(zeroWidth.clock),
                "a recognized zero-width notification host still protects its pending first-measure descendants");
        check(zeroChild.pending && zeroChild.getMeasuredHeight() == 0,
                "isolated notification insertion preserves the real first-measure request without radio assistance");
        zeroChild.geometry.measuredWidth = 20; zeroChild.geometry.measuredHeight = 40;
        zeroIcons.geometry.measuredWidth = 20; zeroArea.geometry.measuredWidth = 20;
        zeroArea.layout(60, 0, 80, 30);
        zeroWidth.flow.beforeNativeLayout(zeroWidth.root); zeroWidth.flow.afterLayout(zeroWidth.root);
        check("ready".equals(zeroWidth.flow.reason(zeroWidth.root)), "real notification measurement releases the deferred zero-width host");
        near(72f, screenX(zeroIcons), "new notification measurement joins the requested gap naturally");

        // Device code158 reload: an earlier ancestor layout consumed FORCE_LAYOUT
        // above the new leaf, so the awaited traversal never measured it and the
        // icons stayed 0x0. The flow re-marks the chain and asks the window once.
        Rig stuck = new Rig(0, 300, true);
        NativeFrame stuckArea = new NativeFrame(stuck.resources, "notification_icon_area", 60, 0, 100, 30);
        NativeFrame stuckIcons = new NativeFrame(stuck.resources, "notificationIcons", 0, 0, 40, 30);
        FirstMeasureLeaf stuckChild = new FirstMeasureLeaf(stuck.resources);
        stuck.left.addView(stuckArea); stuckArea.addView(stuckIcons); stuckIcons.addView(stuckChild);
        stuck.configure(margin);
        int rootRequests = stuck.root.layoutRequests;
        stuck.flow.beforeNativeLayout(stuck.root); stuck.flow.afterLayout(stuck.root);
        check("pending_native_measure".equals(stuck.flow.reason(stuck.root)) && stuckChild.forcedLayouts == 1
                        && stuckIcons.forcedLayouts == 1 && stuckArea.forcedLayouts == 1 && stuck.contents.forcedLayouts == 1
                        && stuck.root.forcedLayouts == 1,
                "a stuck first measure re-marks its whole ancestor chain");
        check(stuck.root.layoutRequests == rootRequests + 1 && stuckArea.nativeLayouts == 0 && stuckChild.pending
                        && stuckChild.getMeasuredHeight() == 0,
                "the window gets one real traversal request; no synthetic layout or measurement");
        for (int i = 0; i < 5; i++) { stuck.flow.beforeNativeLayout(stuck.root); stuck.flow.afterLayout(stuck.root); }
        check(stuckChild.forcedLayouts == 3 && stuck.root.layoutRequests == rootRequests + 3,
                "re-requests are bounded per root");
        check("ready".equals(stuck.flow.reason(stuck.root)),
                "a leaf still 0x0 after every forced native traversal no longer holds margins and order off");
        stuckChild.geometry.measuredWidth = 20; stuckChild.geometry.measuredHeight = 40;
        stuckArea.layout(60, 0, 100, 30);
        stuck.flow.beforeNativeLayout(stuck.root); stuck.flow.afterLayout(stuck.root);
        check("ready".equals(stuck.flow.reason(stuck.root)), "the requested native measure releases the stuck scene");
        near(72f, screenX(stuckIcons), "configured gap applies once the stuck icons are measured");
        FirstMeasureLeaf later = new FirstMeasureLeaf(stuck.resources); stuckIcons.addView(later);
        stuck.flow.beforeNativeLayout(stuck.root); stuck.flow.afterLayout(stuck.root);
        check(later.forcedLayouts == 1, "a successful flow resets the per-root request budget");
        FirstMeasureLeaf detachedLeaf = new FirstMeasureLeaf(stuck.resources); detachedLeaf.attached = false;
        stuckIcons.addView(detachedLeaf);
        stuck.flow.beforeNativeLayout(stuck.root); stuck.flow.afterLayout(stuck.root);
        check(detachedLeaf.forcedLayouts == 0, "a detached pending leaf never touches its old window chain");

        Rig empty = new Rig(0, 300, true);
        NativeFrame emptyArea = new NativeFrame(empty.resources, "notification_icon_area", 60, 0, 60, 30);
        NativeFrame emptyIcons = new NativeFrame(empty.resources, "notificationIcons", 0, 0, 0, 30);
        empty.left.addView(emptyArea); emptyArea.addView(emptyIcons);
        empty.configure(margin); empty.flow.beforeNativeLayout(empty.root); empty.flow.afterLayout(empty.root);
        check("ready".equals(empty.flow.reason(empty.root)), "a native fully measured zero-width notification empty shell never blocks flow");
        near(12f, screenX(empty.clock), "an intentionally empty notification shell preserves unrelated clock spacing");
    }
    /** Device SystemUI bytecode: StatusIconContainer.onMeasure measures only icons that are
     * visible, unblocked and not ignored. Any other slot keeps its 0x0 layout request forever;
     * code158 waited for it and stayed in pending_native_measure, so margins/order never applied. */
    private static void skippedIconMeasureCases() {
        Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        for (int mode = 0; mode < 2; mode++) {
            Rig rig = new Rig(0, 300, true);
            PendingIcon skipped = new PendingIcon(rig.resources, mode == 0 ? "zen" : "alarm_clock");
            if (mode == 0) skipped.eligible = false; else rig.icons.ignored.add("alarm_clock");
            rig.icons.addView(skipped);
            rig.configure(margin); rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
            check("ready".equals(rig.flow.reason(rig.root)),
                    (mode == 0 ? "a hidden" : "an ignored") + " slot the native container never measures does not hold the flow");
            check(skipped.forcedLayouts == 0 && rig.root.forcedLayouts == 0, "no traversal is requested for a natively skipped slot");
            near(12f, screenX(rig.clock), "the clock gap applies beside a natively skipped slot");
        }
        Rig waiting = new Rig(0, 300, true);
        PendingIcon shown = new PendingIcon(waiting.resources, "zen");
        waiting.icons.addView(shown);
        waiting.configure(margin); waiting.flow.beforeNativeLayout(waiting.root); waiting.flow.afterLayout(waiting.root);
        check("pending_native_measure".equals(waiting.flow.reason(waiting.root)) && shown.pending && shown.forcedLayouts == 1,
                "a displayable slot still keeps its real first native measure");
        shown.geometry.measuredWidth = 16; shown.geometry.measuredHeight = 30; shown.pending = false;
        waiting.flow.beforeNativeLayout(waiting.root); waiting.flow.afterLayout(waiting.root);
        check("ready".equals(waiting.flow.reason(waiting.root)), "the measured slot releases the flow");
    }
    /** A capsule reserves space while the saved physical regions/order remain active. */
    private static void capsuleReservationCases() {
        Rig rig = new Rig(0, 300); rig.configure(cross()); rig.pass(); rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)), "custom layout before the capsule");
        near(0f, screenX(rig.battery), "battery moved to the left before the capsule");
        java.util.List<float[]> hole = new ArrayList<>(); hole.add(new float[]{130, 170});
        rig.flow.capsule(rig.icons, hole); rig.pass(); rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)), "a hidden area only over the camera hole keeps the custom layout");
        java.util.List<float[]> shown = new ArrayList<>(); shown.add(new float[]{100, 200});
        // Device code162: a capsule that flashes during an app switch never moves the row.
        rig.flow.capsule(rig.icons, shown); rig.flow.capsule(rig.icons, new ArrayList<>());
        rig.root.runDelayedForCheck(); rig.pass(); rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)), "a capsule flash keeps the custom layout");
        near(0f, screenX(rig.battery), "battery stays in its custom place through the flash");
        rig.flow.capsule(rig.icons, shown);
        check(rig.root.delayedTasks.size() == 1, "the capsule must last before its occupied space is committed");
        rig.flow.capsule(rig.icons, shown);
        check(rig.root.delayedTasks.size() == 1, "repeated reports keep counting, they never restart the wait");
        rig.root.runDelayedForCheck();
        rig.pass(); rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)), "an expanded capsule retains the custom region/order allocation");
        near(0f, screenX(rig.battery), "battery stays on its saved left side while the capsule shows");
        near(25f, screenX(rig.wifi), "Wi-Fi remains after the battery on the saved left side");
        near(210f, screenX(rig.mobile), "mobile remains before the clock on the saved right side");
        near(240f, screenX(rig.clock), "clock stays on its saved right side outside the capsule");
        check(StatusBarFlowLayout.owns(rig.clock), "the capsule never releases the whole custom row");
        rig.flow.prepareTargets(rig.icons); near(25f, screenX(rig.wifi), "the native capsule coordinator receives the custom projected target");
        rig.flow.capsule(rig.icons, shown); rig.pass(); check("ready".equals(rig.flow.reason(rig.root)), "repeated capsule passes retain custom placement");
        rig.flow.capsule(rig.icons, new ArrayList<>());
        rig.root.runDelayedForCheck(); rig.pass(); rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)), "the custom layout returns when the capsule leaves");
        near(0f, screenX(rig.battery), "battery returns to its custom place");
        rig.ownership();
        rig.flow.capsule(null, shown); rig.flow.capsule(new Box(rig.resources, "unrelated", 0, 0, 10, 10), shown);
        rig.pass(); check("ready".equals(rig.flow.reason(rig.root)), "a row outside any status bar root changes nothing");
        check(StatusBarFlowLayout.capsuleAreas(null, "home").isEmpty() && StatusBarFlowLayout.capsuleAreas(new Object(), "home").isEmpty(),
                "an unknown controller reports no capsule");
    }
    private static List<float[]> areas(float... edges) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i + 1 < edges.length; i += 2) result.add(new float[]{edges[i], edges[i + 1]});
        return result;
    }
    private static StatusBarFlowPlanner.Bounds bounds(StatusBarFlowLayout flow, ViewGroup root) throws Exception {
        java.lang.reflect.Field scenes = StatusBarFlowLayout.class.getDeclaredField("scenes"); scenes.setAccessible(true);
        Object scene = ((java.util.Map<?, ?>) scenes.get(flow)).get(root);
        java.lang.reflect.Field plan = scene.getClass().getDeclaredField("plan"); plan.setAccessible(true);
        return ((StatusBarFlowPlanner.Plan) plan.get(scene)).bounds;
    }
    private static void capsuleBoundsCases() throws Exception {
        Rig rig = new Rig(70, 300); rig.configure(cross()); rig.pass();
        for (List<float[]> sample : Arrays.asList(areas(170, 270), areas(110, 330), areas(-100, 1000), areas(),
                areas(-200, 0), areas(500, 800), areas(110, 110), areas(Float.NaN, 240), areas(90, Float.POSITIVE_INFINITY), areas(110, 140, 290, 320))) {
            rig.flow.capsule(rig.icons, sample); rig.root.runDelayedForCheck(); rig.pass(); rig.applyStates();
            check(bounds(rig.flow, rig.root).shared && "ready".equals(rig.flow.reason(rig.root)), "a cloud range is never turned into custom cutout/overflow capacity");
            check(rig.clock.getVisibility() == View.VISIBLE && rig.battery.getVisibility() == View.VISIBLE, "only a real native visibility update can hide native-visible non-icon renderers");
            near(70, screenX(rig.battery), "cloud range changes retain saved battery region");
            near(95, screenX(rig.wifi), "cloud range changes retain saved left-side Wi-Fi rank");
            near(280, screenX(rig.mobile), "cloud range changes retain saved right-side mobile rank");
            near(310, screenX(rig.clock), "cloud range changes retain saved clock region");
        }
        List<float[]> malformed = new ArrayList<>(); malformed.add(null); malformed.add(new float[]{90}); malformed.add(new float[]{240, 110});
        rig.flow.capsule(rig.icons, malformed); rig.root.runDelayedForCheck(); rig.pass();
        check(bounds(rig.flow, rig.root).shared, "unknown/malformed ranges retain the ordinary native-safe bounds");
        int requests = rig.root.layoutRequests;
        for (int i = 0; i < 50; i++) rig.flow.capsule(rig.icons, areas());
        check(rig.root.layoutRequests == requests, "identical native reports never request another layout");
        rig.ownership();

        Rig other = new Rig(500, 300); other.pass(rig.flow);
        check(bounds(rig.flow, other.root).shared, "another root has its own capsule occupancy");
        rig.flow.capsule(other.icons, areas(110, 140)); other.root.runDelayedForCheck(); other.pass(rig.flow);
        check(bounds(rig.flow, other.root).shared, "ranges entirely over the first root cannot swallow another root");
        rig.flow.capsule(other.icons, areas(600, 700)); other.root.runDelayedForCheck(); other.pass(rig.flow);
        check(bounds(rig.flow, other.root).shared, "the second root keeps native interaction and full custom regions");
        rig.flow.capsule(rig.icons, areas()); rig.root.runDelayedForCheck(); rig.pass();
        check(bounds(rig.flow, other.root).shared, "closing one root never rewrites another root's physical bounds");

        Rig moving = new Rig(70, 300); moving.configure(cross()); moving.pass();
        moving.flow.capsule(moving.icons, areas(170, 270)); moving.root.runDelayedForCheck(); moving.pass();
        moving.root.geometry.original = new int[]{100, 9, 400, 39}; moving.pass();
        check(bounds(moving.flow, moving.root).shared, "moving the root cannot turn a stored cloud range into a physical cutout");
        moving.flow.capsule(moving.icons, areas(200, 300)); moving.pass();
        check(bounds(moving.flow, moving.root).shared, "new native range samples do not replace the moved root's safe geometry");
    }
    private static float[] capsuleSnapshot(StatusBarFlowLayout flow, ViewGroup root) throws Exception {
        java.lang.reflect.Field ranges = StatusBarFlowLayout.class.getDeclaredField("capsuleRanges"); ranges.setAccessible(true);
        return (float[]) ((java.util.Map<?, ?>) ranges.get(flow)).get(root);
    }
    private static void capsuleLifecycleCases() throws Exception {
        Rig rig = new Rig(0, 300); rig.configure(cross()); rig.pass();
        rig.flow.capsule(rig.icons, areas(100, 200)); Runnable old = rig.root.delayedTasks.get(0);
        rig.flow.capsule(rig.icons, areas(90, 210));
        check(rig.root.delayedTasks.size() == 1 && rig.root.delayedTasks.get(0) == old, "geometry updates during a pending show do not restart its native display debounce");
        rig.root.runDelayedForCheck(); rig.pass();
        near(90, capsuleSnapshot(rig.flow, rig.root)[0], "pending show remembers the latest native snapshot instead of its first sample");
        rig.flow.capsule(rig.icons, areas()); rig.root.runDelayedForCheck(); rig.pass();
        rig.flow.capsule(rig.icons, areas(100, 200)); old = rig.root.delayedTasks.get(0);
        rig.flow.capsule(rig.icons, areas()); rig.flow.capsule(rig.icons, areas(90, 210));
        old.run(); check(rig.root.delayedTasks.size() == 1, "a canceled show cannot consume the replacement show task");
        rig.root.runDelayedForCheck(); rig.pass();
        near(90, capsuleSnapshot(rig.flow, rig.root)[0], "the replacement show remembers the latest native geometry");
        rig.flow.capsule(rig.icons, areas()); Runnable oldHide = rig.root.delayedTasks.get(0);
        rig.flow.capsule(rig.icons, areas(100, 200)); oldHide.run(); rig.pass();
        near(100, capsuleSnapshot(rig.flow, rig.root)[0], "show during a queued hide cancels the old release identity");
        Rig copied = new Rig(0, 300); copied.configure(cross()); copied.pass();
        float[] mutable = {100, 200}; List<float[]> incoming = new ArrayList<>(); incoming.add(mutable);
        copied.flow.capsule(copied.icons, incoming); mutable[0] = 0; mutable[1] = 300; incoming.clear();
        copied.root.runDelayedForCheck(); copied.pass();
        near(100, capsuleSnapshot(copied.flow, copied.root)[0], "the timer keeps numeric geometry independent from caller-owned arrays");
        near(200, capsuleSnapshot(copied.flow, copied.root)[1], "caller list/array mutation cannot overwrite the remembered native range");
        for (String exit : new String[]{"detach", "off", "release"}) {
            Rig ending = new Rig(0, 300); ending.configure(cross()); ending.pass();
            ending.flow.capsule(ending.icons, areas(100, 200)); Runnable late = ending.root.delayedTasks.get(0);
            if (exit.equals("detach")) ending.flow.detached(ending.root);
            else if (exit.equals("off")) ending.configure(new Bundle()); else ending.flow.releaseRuntime();
            int requests = ending.root.layoutRequests; late.run();
            check(ending.root.delayedTasks.isEmpty() && ending.root.layoutRequests == requests, "late capsule callbacks have no effect after " + exit);
            if (!exit.equals("release")) {
                ending.configure(cross()); ending.pass();
                check(bounds(ending.flow, ending.root).shared, "reattach/off-on starts with no stale capsule geometry: " + exit);
            }
        }
    }
    private static void capsuleNativeInteractionCase() {
        Rig rig = new Rig(0, 300); rig.clock.setClickable(true); rig.clock.setContentDescription("native clock action");
        rig.configure(cross()); rig.pass(); rig.flow.capsule(rig.icons, areas(100, 200)); rig.root.runDelayedForCheck();
        rig.flow.beforeNativeLayout(rig.root); rig.root.geometry.reset();
        for (View view : rig.all) if (view instanceof Box) ((Box) view).geometry.reset(); else ((Leaf) view).geometry.reset();
        rig.flow.afterMeasure(rig.root); rig.flow.beforeStates(rig.icons); rig.wifi.state.x = 125; rig.mobile.state.x = 145;
        rig.wifi.state.visible = 0; rig.flow.prepareTargets(rig.icons);
        rig.wifi.state.x += 9; rig.wifi.state.visible = 2;
        rig.wifi.setAlpha(.35f); rig.wifi.setScaleX(.8f); rig.wifi.setScaleY(.9f); rig.wifi.setTranslationY(3f);
        rig.flow.beforeApply(rig.wifi.state, rig.wifi); rig.nativeAccess.apply(rig.wifi.state, rig.wifi);
        rig.flow.afterLayout(rig.root);
        near(34, screenX(rig.wifi), "native cloud displacement survives projection into the configured capsule-safe side");
        check(rig.wifi.state.visible == 2, "native capsule hidden state is not force-promoted by custom sorting");
        near(.35f, rig.wifi.getAlpha(), "native capsule alpha remains owned by its native animation");
        near(.8f, rig.wifi.getScaleX(), "native capsule scale X is not reset"); near(.9f, rig.wifi.getScaleY(), "native capsule scale Y is not reset");
        near(3f, rig.wifi.getTranslationY(), "native cloud vertical displacement is not reset");
        check(rig.clock.isClickable() && "native clock action".contentEquals(rig.clock.getContentDescription()) && rig.clock.getWidth() == 60,
                "real native touch/accessibility owner and width survive capsule avoidance");
        near(240, screenX(rig.clock), "clock's native touch box follows its saved right-side placement"); rig.ownership();
    }
    /** Same native Seedling update, with custom placement: only native may decide
     * which surrounding renderers fade/hide. A geometric cloud hull is not a cutout. */
    private static void capsuleNativeOwnershipCase() {
        Rig rig = new Rig(0, 300); rig.configure(cross()); rig.pass();
        rig.flow.capsule(rig.icons, areas(40, 260)); rig.root.runDelayedForCheck();
        rig.flow.beforeNativeLayout(rig.root); rig.root.geometry.reset();
        for (View view : rig.all) if (view instanceof Box) ((Box) view).geometry.reset(); else ((Leaf) view).geometry.reset();
        rig.flow.afterMeasure(rig.root); rig.flow.beforeStates(rig.icons); rig.wifi.state.x = 125; rig.mobile.state.x = 145;
        rig.wifi.state.visible = rig.mobile.state.visible = 0; rig.flow.prepareTargets(rig.icons);
        // Real native coordinator owns icon state, its independent alpha animation,
        // and ancestor scale. It does not make the non-icon clock INVISIBLE here.
        rig.wifi.state.x += 9; rig.wifi.state.visible = 2; rig.wifi.setAlpha(.35f);
        rig.mobile.state.x -= 7; rig.mobile.state.visible = 2; rig.mobile.setAlpha(.45f);
        rig.clock.setAlpha(.7f); rig.battery.setAlpha(.8f); rig.right.setScaleX(.92f); rig.right.setScaleY(.92f);
        rig.flow.afterLayout(rig.root);
        check(rig.clock.getVisibility() == View.VISIBLE && rig.battery.getVisibility() == View.VISIBLE,
                "native-visible clock/battery cannot become custom cloud-overflow INVISIBLE");
        near(240, screenX(rig.clock), "native cloud interaction retains the configured clock region/order");
        near(0, screenX(rig.battery), "native cloud interaction retains the configured battery region/order");
        check(rig.wifi.state.visible == 2 && rig.mobile.state.visible == 2, "custom order retains native hidden decisions without forcing either radio visible");
        near(.35f, rig.wifi.getAlpha(), "native independent cloud alpha is unchanged"); near(.45f, rig.mobile.getAlpha(), "second native cloud alpha is unchanged");
        near(.7f, rig.clock.getAlpha(), "native text fade is unchanged"); near(.8f, rig.battery.getAlpha(), "native battery fade is unchanged");
        near(.92f, rig.right.getScaleX(), "native scaled ancestor X is unchanged"); near(.92f, rig.right.getScaleY(), "native scaled ancestor Y is unchanged");
        near(34, screenX(rig.wifi), "custom projection retains native X displacement");
        near(203, screenX(rig.mobile), "custom projection retains the opposite native X displacement"); rig.ownership();
    }
    /** Closing the shade keeps receiving native fluid-cloud reports between root layouts. */
    private static void capsuleStateFeedbackCase(){
        Rig rig=new Rig(0,300);rig.configure(cross());rig.pass();
        rig.flow.capsule(rig.icons,areas(100,200));rig.root.runDelayedForCheck();rig.pass();
        float wifi=screenX(rig.wifi);int requests=rig.root.layoutRequests;
        for(int frame=1;frame<=30;frame++){
            rig.flow.beforeStates(rig.icons);rig.wifi.state.x=125;rig.mobile.state.x=145;
            rig.flow.capsule(rig.icons,areas(100+frame,200+frame));rig.flow.prepareTargets(rig.icons);
            rig.wifi.state.x+=6;rig.wifi.state.visible=2;rig.wifi.setAlpha(.35f);rig.wifi.setScaleX(.8f);
            rig.flow.beforeApply(rig.wifi.state,rig.wifi);rig.nativeAccess.apply(rig.wifi.state,rig.wifi);
            rig.flow.beforeApply(rig.mobile.state,rig.mobile);rig.nativeAccess.apply(rig.mobile.state,rig.mobile);
            rig.flow.afterStates(rig.icons);
            near(wifi+6,screenX(rig.wifi),"animated capsule report never exposes an unprojected native target between root passes, frame="+frame);
            check(rig.root.layoutRequests==requests,"capsule reports unused by the flow plan must not feed root layout back, frame="+frame);
            check(rig.wifi.state.visible==2,"native capsule hidden remains native");near(.35f,rig.wifi.getAlpha(),"native capsule alpha remains native");near(.8f,rig.wifi.getScaleX(),"native capsule scale remains native");
        }
        rig.ownership();rig.flow.releaseRuntime();
    }
    /** The native closing header can lay out only its icon container, without root onLayout. */
    private static void localClosingFrameCase(){
        for(String change:new String[]{"parent_x","ancestor_x","parent_width","child_width"}){
            Rig rig=new Rig(0,300);rig.configure(cross());rig.pass();int requests=rig.root.layoutRequests;
            Geometry moved=change.equals("ancestor_x")?rig.right.geometry:change.equals("child_width")?rig.wifi.geometry:rig.icons.geometry;
            moved.layout(moved.left+(change.endsWith("_x")?8:0),moved.top,moved.right+8,moved.bottom);
            rig.flow.beforeStates(rig.icons);rig.wifi.state.x=125;rig.mobile.state.x=145;
            rig.flow.prepareTargets(rig.icons);rig.flow.afterStates(rig.icons);
            check(rig.root.layoutRequests==requests+1,"native local frame change invalidates the projected scene exactly once: "+change);
            for(int i=0;i<20;i++){rig.flow.afterStates(rig.icons);rig.flow.prepareTargets(rig.icons);}
            check(rig.root.layoutRequests==requests+1,"pending local geometry repair is deduplicated: "+change);
            rig.pass();near(25,screenX(rig.wifi),"one real root pass restores configured target after closing: "+change);
            rig.ownership();rig.flow.releaseRuntime();
        }
        Rig nativeMotion=new Rig(0,300);nativeMotion.configure(cross());nativeMotion.pass();int requests=nativeMotion.root.layoutRequests;
        nativeMotion.right.setTranslationX(8);nativeMotion.right.setTranslationY(3);nativeMotion.right.setAlpha(.6f);nativeMotion.right.setScaleX(.9f);
        nativeMotion.flow.beforeStates(nativeMotion.icons);nativeMotion.wifi.state.x=125;nativeMotion.mobile.state.x=145;
        nativeMotion.flow.prepareTargets(nativeMotion.icons);nativeMotion.applyStates();nativeMotion.flow.afterStates(nativeMotion.icons);
        check(nativeMotion.root.layoutRequests==requests,"native animated translation/alpha/scale do not invalidate the layout coordinate frame");
        near(33,screenX(nativeMotion.wifi),"native parent translation remains additive to custom position");
        near(3,nativeMotion.right.getTranslationY(),"native parent Y is retained");near(.6f,nativeMotion.right.getAlpha(),"native parent alpha is retained");
        near(.9f,nativeMotion.right.getScaleX(),"native parent scale is retained");nativeMotion.flow.releaseRuntime();
    }
    private static void closingHeaderToPhoneCase(){
        Rig phone=new Rig(0,300);phone.configure(cross());phone.pass();StatusBarFlowLayout flow=phone.flow;
        NamedResources resources=new NamedResources();QRoot header=new QRoot(resources);
        Box row=new Box(resources,"quick_qs_status_icons",80,0,300,30),carrier=new Box(resources,"qs_clock_container",0,0,80,30);
        carrier.addView(new Leaf(resources,"qs_carrier_text",0,0,80,30));header.addView(carrier);header.addView(row);
        Box icons=new Box(resources,"statusIcons",0,0,180,30);icons.container=true;row.addView(icons);
        Icon wifi=new Icon(resources,"wifi",20,100);icons.addView(wifi);row.addView(new Leaf(resources,"battery",180,0,205,30));
        flow.beforeNativeLayout(header);flow.afterMeasure(header);flow.afterLayout(header);
        check("ready".equals(flow.reason(header)),"closing replay starts from a real QS header scene");
        int requests=phone.root.layoutRequests;float target=screenX(phone.wifi);
        flow.capsule(phone.icons,areas(100,200));phone.root.runDelayedForCheck();
        for(int frame=0;frame<20;frame++){
            header.setAlpha(1f-frame/19f);if(frame==19)header.setVisibility(View.INVISIBLE);
            flow.beforeStates(icons);wifi.state.x=100;flow.prepareTargets(icons);flow.beforeApply(wifi.state,wifi);phone.nativeAccess.apply(wifi.state,wifi);flow.afterStates(icons);
            flow.beforeStates(phone.icons);phone.wifi.state.x=125;phone.mobile.state.x=145;
            flow.capsule(phone.icons,areas(101+frame,201+frame));flow.prepareTargets(phone.icons);phone.applyStates();flow.afterStates(phone.icons);
            near(target,screenX(phone.wifi),"closing QS to Phone keeps the Phone custom target, frame="+frame);
            check(phone.root.layoutRequests==requests,"closing header reports do not rebuild the independent Phone root, frame="+frame);
        }
        near(0,header.getAlpha(),"native header fade reaches its native final alpha");
        check(header.getVisibility()==View.INVISIBLE,"native header closing visibility is retained");
        flow.detached(header);near(target,screenX(phone.wifi),"QS detach keeps the live Phone scene");phone.ownership();flow.releaseRuntime();
    }
    /** Device code162: a native LayoutTransition must not slide owned elements back towards their native places. */
    private static void layoutTransitionCases() {
        Rig rig = new Rig(0, 300);
        android.animation.LayoutTransition transition = new android.animation.LayoutTransition();
        rig.right.setLayoutTransition(transition);
        Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key("battery", "margin_right"), 6);
        rig.configure(margin); rig.pass();
        check("ready".equals(rig.flow.reason(rig.root)), "margin layout ready");
        check(!transition.animateParentHierarchy, "an owned row's container never animates the parent hierarchy");
        rig.configure(new Bundle());
        check(transition.animateParentHierarchy, "the native parent animation returns when the flow lets go");
        rig.configure(margin); rig.pass(); rig.flow.releaseRuntime();
        check(transition.animateParentHierarchy, "and when the runtime is released");
    }
    private static void invisibleFirstMeasureCases() {
        Bundle margin = new Bundle(); margin.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
        Rig rig = new Rig(0, 300, true);
        NativeFrame area = new NativeFrame(rig.resources, "notification_icon_area", 60, 0, 100, 30);
        NativeFrame icons = new NativeFrame(rig.resources, "notificationIcons", 0, 0, 40, 30);
        FirstMeasureLeaf arriving = new FirstMeasureLeaf(rig.resources);
        arriving.setVisibility(View.INVISIBLE);
        rig.left.addView(area); area.addView(icons); icons.addView(arriving);
        rig.configure(margin); rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check(arriving.pending, "INVISIBLE first-measure leaf must not lose its request to synthetic ancestor layout");
        check("pending_native_measure".equals(rig.flow.reason(rig.root)) && area.nativeLayouts == 0,
                "INVISIBLE pending leaf defers geometry without inventing measurement");

        // Publish a real owned ancestor first, then hide its positive-width notification
        // host and insert a new leaf before settings restoration walks that ancestor.
        arriving.geometry.measuredWidth = 20; arriving.geometry.measuredHeight = 40;
        area.layout(60, 0, 100, 30);
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)), "measured INVISIBLE leaf no longer defers flow");
        FirstMeasureLeaf hiddenArrival = new FirstMeasureLeaf(rig.resources);
        icons.setVisibility(View.INVISIBLE); icons.addView(hiddenArrival);
        int layouts = area.nativeLayouts;
        rig.configure(enabled());
        check(hiddenArrival.pending && area.nativeLayouts == layouts,
                "restoration preserves requests inside an INVISIBLE positive-width notification host");
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("pending_native_measure".equals(rig.flow.reason(rig.root)),
                "fresh collection also checks an INVISIBLE positive-width notification host");
        hiddenArrival.geometry.measuredHeight = 80;
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)) && hiddenArrival.getMeasuredWidth() == 0
                        && hiddenArrival.getMeasuredHeight() == 80,
                "a legitimate pending 0x80 leaf does not trigger first-measure protection or synthetic sizing");
        hiddenArrival.geometry.measuredHeight = 0; hiddenArrival.pending = false;
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)), "an INVISIBLE host with an unrequested 0x0 leaf is not blocked");
        hiddenArrival.pending = true; icons.setVisibility(View.GONE);
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)), "a GONE notification host retains native opt-out");
        FirstMeasureLeaf unknown = new FirstMeasureLeaf(rig.resources);
        rig.left.addView(unknown);
        rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
        check("ready".equals(rig.flow.reason(rig.root)), "an unknown zero-sized shell is not promoted to a measured endpoint");
    }
    /** Device 1440px root: safe padding lives on side containers, not root/content. */
    private static void nativeSidePaddingCases() {
        for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
            Rig rig = new Rig(0, 1440, true); rig.root.direction = direction;
            rig.resources.getDisplayMetrics().density = 4f;
            boolean rtl = direction == View.LAYOUT_DIRECTION_RTL;
            if (!rtl) {
                rig.left.geometry.original = new int[]{0, 0, 666, 30}; rig.left.paddingLeft = 78;
                rig.right.geometry.original = new int[]{774, 0, 1440, 30}; rig.right.paddingRight = 88;
                rig.clockHost.geometry.original = new int[]{78, 0, 138, 30};
                rig.icons.geometry.original = new int[]{378, 0, 553, 30};
                rig.battery.geometry.original = new int[]{553, 0, 578, 30};
            } else {
                rig.left.geometry.original = new int[]{774, 0, 1440, 30}; rig.left.paddingRight = 88;
                rig.right.geometry.original = new int[]{0, 0, 666, 30}; rig.right.paddingLeft = 78;
                rig.clockHost.geometry.original = new int[]{518, 0, 578, 30};
                rig.icons.geometry.original = new int[]{103, 0, 278, 30};
                rig.battery.geometry.original = new int[]{78, 0, 103, 30};
                rig.mobileNativeX = 0; rig.wifiNativeX = 30;
            }
            rig.contents.addView(new Leaf(rig.resources, "cutout_space_view", 666, 0, 774, 30));
            rig.configure(new Bundle()); rig.pass();
            near(rtl ? 1292 : 78, screenX(rig.clock), "native zero gap retains physical side-container inset");
            Bundle margin = new Bundle();
            margin.putFloat(StatusBarLayoutSettings.key("clock", rtl ? "margin_right" : "margin_left"), 12);
            rig.configure(margin); rig.pass();
            check("ready".equals(rig.flow.reason(rig.root)), "real native side padding and cutout keep a finite valid plan");
            near(rtl ? 1244 : 126, screenX(rig.clock), "twelve dp is relative to native 78/88px safe inset at density four");
            near(rtl ? 78 : 1352, screenX(rig.battery) + (rtl ? 0 : rig.battery.getWidth()),
                    "opposite physical safe edge stays native without mirroring logical start/end twice");
            near(78, rtl ? rig.right.getPaddingLeft() : rig.left.getPaddingLeft(), "native physical left padding never rewritten");
            near(88, rtl ? rig.left.getPaddingRight() : rig.right.getPaddingRight(), "native physical right padding never rewritten");
            rig.configure(new Bundle());
            near(rtl ? 1292 : 78, screenX(rig.clock), "zero restores original layout with native side padding");
            rig.ownership();
        }
    }
    /** A recognized non-icon leaf has no endpoint until its first measure supplies a width.
     * Repacking an ancestor must not consume that pending layout merely because the leaf is not a notification. */
    private static void recognizedLeafFirstMeasureCases() {
        for (String id : new String[]{"clock", "battery", "carrier_label"}) for (int visibility : new int[]{View.VISIBLE, View.INVISIBLE}) {
            Rig rig = new Rig(0, 300, true);
            if (id.equals("clock")) rig.clockHost.removeView(rig.clock);
            if (id.equals("battery")) rig.right.removeView(rig.battery);
            NativeFrame shell = new NativeFrame(rig.resources, "status_bar_end_side_container_for_fake", 0, 0, 60, 30);
            FirstMeasureLeaf arriving = new FirstMeasureLeaf(rig.resources);
            arriving.setId(rig.resources.id(id)); arriving.setVisibility(visibility);
            rig.right.addView(shell); shell.addView(arriving);
            Bundle settings = enabled(); settings.putString(StatusBarLayoutSettings.key("wifi", "side"), "left");
            rig.configure(settings);
            rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
            check("pending_native_measure".equals(rig.flow.reason(rig.root)), id + " without an endpoint still defers horizontal packing");
            check(arriving.pending && shell.nativeLayouts == 0 && arriving.forcedLayouts == 1,
                    id + " keeps its real first traversal instead of consuming it in ancestor layout");
            rig.configure(settings);
            check(arriving.pending && shell.nativeLayouts == 0, id + " also retains its first traversal during settings refresh");
            arriving.geometry.measuredWidth = 20; arriving.geometry.measuredHeight = 40;
            shell.layout(shell.getLeft(), 0, shell.getRight(), 30);
            rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
            check("ready".equals(rig.flow.reason(rig.root)), id + " joins the flow after genuine native measurement");
            check(arriving.getMeasuredHeight() == 40 && arriving.getHeight() == 40 && arriving.getTop() == -5,
                    id + " retains independently measured native height and vertical centering");
        }
    }
    /** Captured fingerprint reload: native measure skips logical-hidden slots, then native
     * onLayout still lays them out at 0x0 and consumes their first layout request. A later
     * local state update must recover once they become eligible, without a root callback. */
    private static void consumedNativeMeasureCases() throws Throwable {
        for (int mode = 0; mode < 3; mode++) {
            Rig rig = new Rig(0, 300, true);
            PendingIcon arriving = new PendingIcon(rig.resources, "wifi_secondary");
            arriving.eligible = false; rig.icons.addView(arriving);
            arriving.layout(0, 15, 0, 15); // StatusIconContainer.onLayout ignores measurement eligibility.
            FirstMeasureLeaf notification = null;
            if (mode == 2) {
                NativeFrame host = new NativeFrame(rig.resources, "notificationIcons", 60, 0, 100, 30);
                notification = new FirstMeasureLeaf(rig.resources); host.addView(notification); rig.left.addView(host);
            }
            Bundle settings = new Bundle(); settings.putFloat(StatusBarLayoutSettings.key("clock", "margin_left"), 12);
            rig.configure(settings);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            if (mode != 0) { rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root); }
            callbacks.updateStates(rig.icons, () -> null);
            check(arriving.forcedLayouts == 0 && !arriving.pending, "native-hidden 0x0 slot is left native before unlock, mode " + mode);
            int rootRequests = rig.root.layoutRequests;
            arriving.eligible = true;
            callbacks.updateStates(rig.icons, () -> null);
            check(arriving.forcedLayouts == 1 && arriving.pending && rig.root.layoutRequests > rootRequests,
                    "eligible 0x0 with consumed request recovers through local states, mode " + mode);
            check(arriving.getMeasuredWidth() == 0 && arriving.getMeasuredHeight() == 0,
                    "recovery schedules native measurement without inventing icon dimensions, mode " + mode);
            arriving.geometry.measuredWidth = 20; arriving.geometry.measuredHeight = 30; arriving.layout(0, 0, 20, 30);
            if (notification != null) { notification.geometry.measuredWidth = 20; notification.geometry.measuredHeight = 30; notification.pending = false; }
            rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root);
            int forces = arriving.forcedLayouts, requests = rig.root.layoutRequests;
            for (int i = 0; i < 50; i++) callbacks.updateStates(rig.icons, () -> null);
            check(StatusBarFlowLayout.owns(rig.clock) && arriving.forcedLayouts == forces && rig.root.layoutRequests == requests,
                    "successful native completion stops recovery and repeated local states add no traversal, mode " + mode
                            + " reason=" + rig.flow.reason(rig.root) + " force=" + forces + "->" + arriving.forcedLayouts
                            + " requests=" + requests + "->" + rig.root.layoutRequests);
        }
        Rig bounded = new Rig(0, 300, true); bounded.configure(enabled());
        PendingIcon icon = new PendingIcon(bounded.resources, "wifi_secondary"); bounded.icons.addView(icon); icon.layout(0, 15, 0, 15);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(bounded.flow);
        for (int i = 0; i < 100; i++) callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 3, "local-state recovery retains the three-traversal bound");
        bounded.flow.beforeNativeLayout(bounded.root); bounded.flow.afterLayout(bounded.root);
        for (int i = 0; i < 100; i++) callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 3, "a natively skipped 0x0 slot does not restart recovery on every state update");
        icon.eligible = false; callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 3, "a blocked or logically hidden slot never forces measurement");
        icon.eligible = true; bounded.icons.ignored.add(icon.slot); callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 3, "an ignored slot never forces measurement");
        bounded.icons.ignored.remove(icon.slot); callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 4, "a newly eligible lifecycle releases only its expired skip and grants a fresh bounded traversal");
        icon.geometry.measuredHeight = 30; icon.pending = false;
        for (int i = 0; i < 50; i++) callbacks.updateStates(bounded.icons, () -> null);
        check(icon.forcedLayouts == 4, "a legitimate zero-width positive-height native placeholder is never forced");
        recoverySiblingCases(); recoveryPostLifecycleCases(); recoveryLateAndVisibleCases(); recoveryVisibilityOnlyCases(); recoveryOwnerCases();
    }
    private static void recoveryLateAndVisibleCases() throws Throwable {
        for (boolean rootPass : new boolean[]{false, true}) {
            Rig rig = new Rig(0, 300); rig.configure(enabled());
            PendingIcon old = new PendingIcon(rig.resources, "wifi_old"); rig.icons.addView(old); old.layout(0, 15, 0, 15);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            for (int i = 0; i < 100; i++) callbacks.updateStates(rig.icons, () -> null);
            if (rootPass) { rig.flow.beforeNativeLayout(rig.root); rig.flow.afterLayout(rig.root); }
            PendingIcon late = new PendingIcon(rig.resources, "wifi_late"); rig.icons.addView(late); late.layout(0, 15, 0, 15);
            for (int i = 0; i < 100; i++) callbacks.updateStates(rig.icons, () -> null);
            check(old.forcedLayouts == 3 && late.forcedLayouts == 3,
                    "a late native child gets its own bounded attempts while exhausted peers stay stopped, rootPass=" + rootPass);
        }
        Rig hidden = new Rig(0, 300); hidden.configure(enabled()); hidden.right.setVisibility(View.INVISIBLE);
        PendingIcon icon = new PendingIcon(hidden.resources, "wifi_hidden_parent"); hidden.icons.addView(icon); icon.layout(0, 15, 0, 15);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(hidden.flow);
        for (int i = 0; i < 100; i++) callbacks.updateStates(hidden.icons, () -> null);
        check(icon.forcedLayouts == 0, "an ancestor-hidden native slot does not spend active recovery attempts before fingerprint unlock");
        hidden.right.setVisibility(View.VISIBLE);
        callbacks.updateStates(hidden.icons, () -> null);
        check(icon.forcedLayouts == 1, "ancestor visibility alone grants one native recovery without a logical icon change or root layout");
    }
    private static void recoveryOwnerCases() throws Throwable {
        for (boolean otherRoot : new boolean[]{false, true}) {
            Rig rig = new Rig(0, 300); rig.configure(enabled());
            PendingIcon icon = new PendingIcon(rig.resources, "wifi_reparent"); rig.icons.addView(icon); icon.layout(0, 15, 0, 15);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            for (int i = 0; i < 50; i++) callbacks.updateStates(rig.icons, () -> null);
            check(icon.forcedLayouts == 3, "old native owner exhausts only its own first-measure budget");
            Rig other = otherRoot ? new Rig(0, 300) : rig;
            Box next = new Box(other.resources, "statusIcons", 0, 0, 120, 30); next.container = true; other.right.addView(next);
            rig.icons.removeView(icon); next.addView(icon); icon.layout(0, 15, 0, 15);
            for (int i = 0; i < 50; i++) callbacks.updateStates(next, () -> null);
            check(icon.forcedLayouts == 6, "a new native container/root starts a bounded first-measure lifecycle, otherRoot=" + otherRoot);
            for (int i = 0; i < 50; i++) callbacks.updateStates(next, () -> null);
            check(icon.forcedLayouts == 6, "the same replacement owner cannot renew the exhausted budget on every callback");
        }
    }
    /** Same receiver/entry as the existing View visibility observer. Android dispatches
     * an ancestor's visibility event to the native container after updating visibility. */
    private static void recoveryVisibilityOnlyCases() throws Throwable {
        for (boolean window : new boolean[]{false, true}) {
            Rig rig = new Rig(0, 300); rig.configure(enabled()); rig.root.queuePosts = true;
            PendingIcon icon = new PendingIcon(rig.resources, "wifi_visibility_only"); rig.icons.addView(icon); icon.layout(0, 15, 0, 15);
            if (window) icon.windowVisibility = View.INVISIBLE; else rig.right.setVisibility(View.INVISIBLE);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            for (int i = 0; i < 50; i++) callbacks.updateStates(rig.icons, () -> null);
            check(rig.root.posted.isEmpty(), "hidden content keeps its recovery budget, window=" + window);
            if (window) icon.windowVisibility = View.VISIBLE; else rig.right.setVisibility(View.VISIBLE);
            // No updateStates or root callback follows the native visibility change.
            rig.flow.visibilityChanged(rig.icons);
            check(rig.root.posted.size() == 1, "visibility observer alone queues native first measure, window=" + window);
            rig.root.runPosts();
            check(icon.forcedLayouts == 1, "visibility-only recovery forces the missing leaf once, window=" + window);
            for (int i = 0; i < 50; i++) rig.flow.visibilityChanged(rig.clock);
            check(rig.root.posted.isEmpty() && icon.forcedLayouts == 1, "unrelated visibility callbacks cannot scan or force the row");
            icon.geometry.measuredWidth = 28; icon.geometry.measuredHeight = 30; icon.pending = false;
            for (int i = 0; i < 50; i++) rig.flow.visibilityChanged(rig.icons);
            check(rig.root.posted.isEmpty(), "a successfully measured native row needs no visibility recovery");
        }
    }
    private static void recoverySiblingCases() throws Throwable {
        Rig rig = new Rig(0, 300, true); rig.configure(enabled());
        PendingIcon icon = new PendingIcon(rig.resources, "wifi_secondary"); rig.icons.addView(icon); icon.layout(0, 15, 0, 15);
        rig.right.removeView(rig.battery);
        CachedSizedHost battery = new CachedSizedHost(rig.resources, "battery");
        CachedSizedLeaf image = new CachedSizedLeaf(rig.resources, "battery_icon_view", 29, 17); battery.addView(image); rig.right.addView(battery);
        CachedSizedHost notifications = new CachedSizedHost(rig.resources, "notificationIcons");
        CachedSizedLeaf glyph = new CachedSizedLeaf(rig.resources, "fixture_notification_glyph", 16, 40); notifications.addView(glyph); rig.left.addView(notifications);
        Leaf carrier = new Leaf(rig.resources, "carrier_label", 0, 0, 0, 30); rig.left.addView(carrier);
        Icon label = new Icon(rig.resources, "network_type", 0, 0); rig.icons.addView(label);
        battery.forceLayout(); notifications.forceLayout();
        int w = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST), h = View.MeasureSpec.makeMeasureSpec(30, View.MeasureSpec.EXACTLY);
        battery.measure(w, h); notifications.measure(w, h);
        check(battery.getMeasuredWidth() == 0 && notifications.getMeasuredWidth() == 0,
                "forcing only cached sibling hosts cannot measure their unforced 0x0 content");
        new StatusBarFlowCallbacks(rig.flow).updateStates(rig.icons, () -> null);
        check(image.forcedLayouts == 1 && glyph.forcedLayouts == 1 && battery.forcedLayouts == 2 && notifications.forcedLayouts == 2,
                "recovery explicitly forces missing fixed-size battery and notification siblings as well as their ancestors");
        battery.measure(w, h); notifications.measure(w, h);
        check(battery.getMeasuredWidth() == 29 && notifications.getMeasuredWidth() == 16
                        && image.getMeasuredHeight() == 17 && glyph.getMeasuredHeight() == 40,
                "the actual same-spec native sibling traversal supplies their independent expected dimensions");
        check(carrier.forcedLayouts == 0 && label.forcedLayouts == 0,
                "legitimate 0xH carrier and network-label placeholders are not forced");
    }
    private static void recoveryPostLifecycleCases() throws Throwable {
        for (int mode = 0; mode < 7; mode++) {
            Rig rig = new Rig(0, 300); rig.root.queuePosts = true; rig.configure(enabled());
            PendingIcon icon = new PendingIcon(rig.resources, "wifi_secondary"); rig.icons.addView(icon); icon.layout(0, 15, 0, 15);
            StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(rig.flow);
            for (int i = 0; i < 50; i++) callbacks.updateStates(rig.icons, () -> null);
            check(rig.root.posted.size() == 1 && icon.forcedLayouts == 0, "queued local updates share one recovery post, mode " + mode);
            Runnable stale = rig.root.posted.get(0);
            if (mode == 0) rig.flow.detached(rig.root);
            else if (mode == 1) rig.flow.releaseRuntime();
            else if (mode == 2) rig.configure(new Bundle());
            else if (mode == 3) rig.root.attached = false;
            else if (mode == 4) { rig.icons.removeView(icon); new Root(rig.resources, 0, 300).addView(icon); }
            else if (mode == 5) icon.eligible = false;
            else icon.setVisibility(View.GONE);
            stale.run(); rig.root.runPosts();
            check(icon.forcedLayouts == 0, "stale recovery never forces a released/hidden/reparented view, mode " + mode);
        }
        Rig again = new Rig(0, 300); again.root.queuePosts = true; again.configure(enabled());
        PendingIcon icon = new PendingIcon(again.resources, "wifi_secondary"); again.icons.addView(icon); icon.layout(0, 15, 0, 15);
        StatusBarFlowCallbacks callbacks = new StatusBarFlowCallbacks(again.flow); callbacks.updateStates(again.icons, () -> null);
        Runnable stale = again.root.posted.get(0); again.configure(new Bundle()); again.configure(enabled());
        callbacks.updateStates(again.icons, () -> null); stale.run();
        check(again.root.posted.size() == 1 && icon.forcedLayouts == 0,
                "an old canceled post cannot consume a newer enable lifecycle's recovery post");
        again.root.runPosts(); check(icon.forcedLayouts == 1, "the current enable lifecycle still gets its one real native traversal");
    }
    private static final class FeatureReads extends Bundle {
        int reads;
        FeatureReads(Bundle source) { super(source); }
        @Override public Object get(String key) {
            if (FeatureOptions.DEFAULTS.containsKey(key)) reads++;
            return super.get(key);
        }
    }
    /** The same immutable feature switches used for each endpoint belong to the settings transaction. */
    private static void featureReadBudgetCase() throws Exception {
        Rig rig = new Rig(0, 300); Bundle settings = cross();
        settings.putBoolean("battery_enabled", true); settings.putBoolean("battery_size_enabled", true);
        settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE, 200);
        rig.configure(settings);
        java.lang.reflect.Field values = StatusBarFlowLayout.class.getDeclaredField("values"); values.setAccessible(true);
        FeatureReads counted = new FeatureReads((Bundle) values.get(rig.flow)); values.set(rig.flow, counted);
        for (int i = 0; i < 20; i++) rig.pass();
        check(counted.reads == 0, "native layout never reparses all feature switches per endpoint: " + counted.reads);
        near(51, screenX(rig.wifi), "cached battery size participates in real planned sibling placement, with its rounded half-width inset");
        settings.putBoolean("battery_size_enabled", false); rig.configure(settings); rig.pass();
        near(25, screenX(rig.wifi), "later settings refresh the feature snapshot and its effective scale");
    }
    private static void sharedWidthRecovery() {
        Rig rig=new Rig(0,400,true);
        NetworkSharedSlotLayout shared=new NetworkSharedSlotLayout();shared.configure(true);rig.flow.setSharedSlots(shared);
        Bundle values=enabled();values.putBoolean(StatusBarLayoutSettings.WIFI_LABEL_SHARED,true);
        for(String token:new String[]{"wifi","mobile"})values.putString(StatusBarLayoutSettings.key(token,"side"),"right");
        values.putFloat(StatusBarLayoutSettings.key("wifi","order"),1);values.putFloat(StatusBarLayoutSettings.key("mobile","order"),2);
        rig.configure(values);
        // Actual native view remeasures at a different density/style; the shared slot
        // remembers the latest size of each occupant, never the widest size ever seen.
        rig.wifi.geometry.original=new int[]{0,0,40,30};rig.pass();rig.applyStates();
        check("ready".equals(rig.flow.reason(rig.root)),"shared width fixture uses real finite flow");
        near(40,screenX(rig.mobile)-screenX(rig.wifi),"large native Wi-Fi reserves forty pixels");
        rig.wifi.geometry.original=new int[]{0,0,20,30};rig.pass();rig.applyStates();
        near(20,screenX(rig.mobile)-screenX(rig.wifi),"smaller current Wi-Fi drops stale historical shared width");
        for(int i=0;i<10;i++){rig.pass();rig.applyStates();near(20,screenX(rig.mobile)-screenX(rig.wifi),"stable shared width never accumulates space");}
        rig.ownership();rig.flow.releaseRuntime();
    }
    public static void main(String[] ignored) throws Throwable {
        if(ignored.length>0&&"shared-width".equals(ignored[0])){sharedWidthRecovery();System.out.println("StatusBarFlowLayoutCheck shared-width: "+checks+" checks passed");return;}
        paddedHeaderMarginCases();
        if(ignored.length==1&&"closing-capsule".equals(ignored[0])){capsuleStateFeedbackCase();System.out.println("Closing capsule checks: "+checks);return;}
        if(ignored.length==1&&"closing-local".equals(ignored[0])){localClosingFrameCase();System.out.println("Closing local-frame checks: "+checks);return;}
        if (ignored.length == 1 && "capsule-native".equals(ignored[0])) {
            capsuleNativeOwnershipCase(); System.out.println("Native capsule ownership checks: " + checks); return;
        }
        if (ignored.length == 1 && "consumed-native".equals(ignored[0])) {
            consumedNativeMeasureCases(); System.out.println("Consumed native first-measure checks: " + checks); return;
        }
        if (ignored.length == 1 && "pending-leaves".equals(ignored[0])) {
            recognizedLeafFirstMeasureCases(); System.out.println("Recognized leaf first-measure checks: " + checks); return;
        }
        if (ignored.length == 1 && "capsule".equals(ignored[0])) {
            capsuleReservationCases(); capsuleBoundsCases(); capsuleLifecycleCases(); capsuleNativeInteractionCase(); capsuleNativeOwnershipCase();
            System.out.println("Capsule custom-order checks: " + checks); return;
        }
        if (ignored.length == 1 && "lock-baseline".equals(ignored[0])) { lockScreenBaselines();System.out.println("Lock screen native baselines: "+checks);return; }
        capsuleStateFeedbackCase();localClosingFrameCase();closingHeaderToPhoneCase();featureReadBudgetCase();
        if (ignored.length == 1 && "shells".equals(ignored[0])) { inactiveNativeShellCases(); return; }
        if (ignored.length == 1 && "reflow".equals(ignored[0])) { nativeReflowResidualCase(); return; }
        Rig rig = new Rig(7, 300); check(StatusBarFlowLayout.root(rig.root), "known-root subclass supported");
        rig.clock.setClickable(true); rig.clock.setContentDescription("native clock"); rig.battery.setContentDescription("native battery");
        rig.clock.setTranslationX(4); rig.clock.setTranslationY(3); rig.clock.setAlpha(.7f); rig.battery.setAlpha(.6f);
        rig.configure(cross()); int clockWrites = rig.clock.translationWrites, wifiWrites = rig.wifi.translationWrites;
        int wifiApplications = rig.wifi.nativeApplications, wifiRebases = rig.wifi.nativeRebases;
        rig.pass(); rig.ownership(); check("ready".equals(rig.flow.reason(rig.root)), "verified scene planned");
        check(rig.clock.translationWrites == clockWrites, "coordinator never writes endpoint View translation");
        check(rig.wifi.translationWrites - wifiWrites == rig.wifi.nativeApplications - wifiApplications + rig.wifi.nativeRebases - wifiRebases,
                "icon translations are written only by native apply and coordinate-rebase seams");
        check(rig.wifi.nativeApplications == wifiApplications + 1, "new layout applies the native target before the next native frame");
        near(4f, rig.clock.getTranslationX(), "clock native animation preserved"); near(3f, rig.clock.getTranslationY(), "vertical animation preserved");
        near(.7f, rig.clock.getAlpha(), "clock alpha preserved"); near(.6f, rig.battery.getAlpha(), "battery alpha preserved");
        check("native clock".contentEquals(rig.clock.getContentDescription()), "native accessibility owner retained");
        near(32f, screenX(rig.wifi), "layout pass immediately projects native wifi state");
        rig.applyStates();
        near(7f, screenX(rig.battery), "battery truly lays out on left");
        near(32f, screenX(rig.wifi), "wifi crosses old parent with native state target only");
        near(217f, screenX(rig.mobile), "mobile right group order one is physical leftmost");
        near(251f, screenX(rig.clock), "clock on right plus its untouched animated residual");
        near(0f, rig.wifi.getLeft(), "native icon local layout remains zero; target is not doubled");
        check(rig.clock.getWidth() == 60 && rig.battery.getWidth() == 25, "click and accessibility endpoint widths remain natural");
        check(rig.clockHost.getWidth() == 60, "fake clock source retains natural bounding width");

        float target = rig.wifi.state.x;
        rig.flow.beforeStates(rig.icons); rig.wifi.state.x = 125; rig.mobile.state.x = 145;
        rig.flow.prepareTargets(rig.icons); near(target, rig.wifi.state.x, "new native state pass gets planned target");
        // An OEM animation may exactly cancel the layout delta; equality with an old native value is not a reset signal.
        rig.wifi.state.x = 125; rig.flow.beforeApply(rig.wifi.state, rig.wifi);
        near(125f, rig.wifi.state.x, "coordinate residual equal to old baseline is preserved");
        rig.flow.beforeStates(rig.icons); rig.wifi.state.x = 99; rig.mobile.state.x = 119;
        rig.flow.beforeApply(rig.wifi.state, rig.wifi); near(target, rig.wifi.state.x, "missing Seedling call still publishes exactly once");
        rig.wifi.state.x += 13; rig.flow.beforeApply(rig.wifi.state, rig.wifi); near(target + 13, rig.wifi.state.x, "repeated apply preserves native residual");

        for (int i = 0; i < 100; i++) { rig.pass(); rig.applyStates(); near(32f, screenX(rig.wifi), "repeated layouts do not accumulate offsets"); rig.ownership(); }
        rig.flow.beforeNativeLayout(rig.root); near(0f, rig.left.getLeft(), "original left wrapper restored");
        near(100f, rig.right.getLeft(), "original right wrapper restored"); near(175f, rig.battery.getLeft(), "battery original local bound restored");
        rig.configure(new Bundle()); check(!rig.flow.wanted(), "off stops the coordinator"); rig.ownership();

        Rig foreign = new Rig(0, 300); foreign.configure(cross()); foreign.pass();
        foreign.clock.layout(12, 4, 72, 34); foreign.flow.configure(new Bundle());
        near(12f, foreign.clock.getLeft(), "external new geometry not overwritten on disable");
        near(4f, foreign.clock.getTop(), "external vertical geometry not overwritten"); foreign.ownership();

        Rig vertical = new Rig(0, 300); vertical.configure(cross()); vertical.pass();
        vertical.battery.layout(vertical.battery.getLeft(), 5, vertical.battery.getRight(), 35);
        vertical.flow.configure(new Bundle()); near(5f, vertical.battery.getTop(), "native vertical-only update survives horizontal restoration");
        near(35f, vertical.battery.getBottom(), "native vertical-only height survives horizontal restoration"); vertical.ownership();

        Rig gone = new Rig(0, 300); gone.configure(cross()); gone.wifi.eligible = false; gone.wifi.setVisibility(View.GONE);
        gone.pass(); gone.applyStates(); check(gone.wifi.getVisibility() == View.GONE, "native hidden wifi remains hidden");
        near(0f, screenX(gone.battery), "absent wifi adds no phantom gap"); gone.ownership();
        gone.wifi.eligible = true; gone.wifi.setVisibility(View.VISIBLE); gone.pass(); gone.applyStates();
        near(25f, screenX(gone.wifi), "reappearing wifi restores requested order"); gone.ownership();
        gone.flow.releaseRuntime(); check(!gone.flow.wanted(), "release prevents new ownership"); gone.ownership();

        Rig blocked = new Rig(0, 300); blocked.configure(cross()); blocked.clockHost.setOnClickListener(view -> { });
        blocked.pass(); check(!"ready".equals(blocked.flow.reason(blocked.root)), "interactive structural ancestor not moved or silently ignored");
        near(0f, blocked.clockHost.getLeft(), "interactive source retained"); blocked.ownership();
        hintNeutralCase(); hintPlacementCase(); hintZeroAndGateCases(); hintRetentionAndNativeStateCase(); nativeHiddenSystemGapCase(); hintHeaderRootsCase(); hintSteadyReadBudgetCase(); obstacleCases(); cutoutAndMarginCases(); nativeSideCase(); nativeLocalRectCase(); nativeReflowResidualCase(); unsupportedReflowCase(); contentWrapperCases();
        inactiveNativeShellCases();lockScreenBaselines();sharedWidthRecovery();
        qsNativeRowCases(); qsCrowdedSideCase(); exactCallbackCases(); anonymousNotificationWrapperCases(); existingAnchorCases(); nativeSidePaddingCases(); firstNativeMeasureCases(); recognizedLeafFirstMeasureCases(); consumedNativeMeasureCases(); invisibleFirstMeasureCases(); skippedIconMeasureCases(); capsuleReservationCases(); capsuleBoundsCases(); capsuleLifecycleCases(); capsuleNativeInteractionCase(); capsuleNativeOwnershipCase(); layoutTransitionCases();
        System.out.println("StatusBarFlowLayoutCheck: " + checks + " checks passed");
    }
}
