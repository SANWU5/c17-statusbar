package dev.puitheme;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState;
import com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView;

/** Native allocation replay. The assertion measures from the selected parent edge, never a cached
 * screen coordinate. A separate moving-parent control rejects screen pinning. */
public final class SpeedRelativePositionCheck {
    static int checks;
    static void check(boolean okay,String why){checks++;if(!okay)throw new AssertionError(why);}
    static void close(float expected,float actual,String why){check(Math.abs(expected-actual)<.02f,why+": "+expected+" != "+actual);}
    static final Resources RES = new Resources() {
        @Override public int getIdentifier(String name, String type, String pkg) {
            switch(name){case "status_bar_view_state_tag":return 1;case "clock":return 2;case "clock_for_fake":return 3;
                case "status_bar_start_side_container":return 4;case "network_speed_view":return 5;default:return 0;}
        }
    };
    static final class Phone extends PhoneStatusBarView {
        int moved;
        final Map<Integer,View> ids=new HashMap<>();
        @Override public int getLeft() { return moved; }
        @Override public View findViewById(int id){return ids.get(id);}
    }
    static final class Parent extends StatusIconContainer {
        int width, extra = 0, constrainedWidth,paddingLeft,paddingRight;
        boolean rtl;
        @Override public Resources getResources() { return RES; }
        @Override public int getWidth() { return width; }
        @Override public int getLeft() { return 400 - width; }
        @Override public int getLayoutDirection() { return rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR; }
        @Override public int getPaddingLeft(){return paddingLeft;}@Override public int getPaddingRight(){return paddingRight;}
        @Override public int getPaddingStart(){return rtl?paddingRight:paddingLeft;}
        @Override public int getPaddingEnd(){return rtl?paddingLeft:paddingRight;}
    }
    static final class Icon extends View implements StatusIconDisplayable {
        final String slot;
        boolean shown = true, blocked;
        int width;
        final StatusIconContainer.StatusIconState state = new StatusIconContainer.StatusIconState();
        Icon(String slot, int width) { super(null); this.slot = slot; this.width = width; }
        public String getSlot() { return slot; }
        public boolean isIconVisible() { return shown; }
        public boolean isIconBlocked() { return blocked; }
        @Override public int getWidth() { return width; }
        @Override public Object getTag(int id) { return id == 1 ? state : null; }
    }
    static final class Rig {
        final SpeedPosition h = new SpeedPosition();
        final Phone phone = new Phone();
        final Parent p = new Parent();
        final NetworkSpeedView speed = new NetworkSpeedView();
        final StatusIconContainer.StatusIconState source = new StatusIconContainer.StatusIconState();
        final Icon cell = new Icon("stacked_mobile", 80), wifi = new Icon("wifi", 88);
        final String mode;
        final Bundle options=new Bundle();
        int speedOuterWidth;
        float rawSpeedDisplacement,rawSpeedTarget,nativeAllocatedTarget;
        Rig(String mode, boolean adjacent, boolean rtl, float reserve) throws Exception {
            this.mode=mode;
            h.resolve(SpeedRelativePositionCheck.class.getClassLoader());
            phone.addView(p);
            speed.applyNativeConfiguration(88, 36, 44);
            speed.nativeHeight = 80; speed.nativePaddingEnd = 9;
            speed.getResources().getDisplayMetrics().density = 4f;
            speed.tags.put(1, source);
            NetworkSpeedIconState model = new NetworkSpeedIconState(); model.visible = true; model.speedText = 42;
            speed.applyNetworkState(model);
            p.addView(speed); p.addView(cell); p.addView(wifi);
            h.attached(speed); h.setHooksReady(true); h.setAllocationReady(true);
            options.putBoolean("speed_enabled", true); options.putBoolean("speed_position_enabled", true);
            options.putString(SpeedPosition.POSITION, mode); options.putFloat("speed_offset_x", -5.25f);
            options.putFloat(SpeedPosition.EDGE_RESERVE, reserve);
            options.putBoolean(SpeedPosition.SAFE_GAP_ENABLED, adjacent); options.putFloat(SpeedPosition.SAFE_GAP, -4.27f);
            h.configure(options); p.rtl = rtl;
        }
        void state(boolean wifiOn, boolean cellOn, int cellWidth) {
            wifi.shown = wifiOn; cell.shown = cellOn; cell.width = cellWidth;
            pass(); pass();
        }
        void pass() {
            int width = p.extra+p.getPaddingLeft()+p.getPaddingRight(), count = 0;
            try (SpeedPosition.Scope scope = h.begin(p, false)) {
                for (int i = 0; i < p.getChildCount(); i++) {
                    View child = p.getChildAt(i); StatusIconDisplayable icon = (StatusIconDisplayable) child;
                    if (!icon.isIconVisible() || h.iconBlocked(child, icon.isIconBlocked()) || p.mIgnoredSlots.contains(icon.getSlot())) continue;
                    int allocated = child.getMeasuredWidth() + child.getPaddingStart() + h.allocationPaddingEnd(child, child.getPaddingEnd());
                    if (child == speed) speedOuterWidth = allocated;
                    width += allocated; count++;
                }
            }
            p.width = p.constrainedWidth>0?p.constrainedWidth:width + Math.max(0, count - 1) * p.mIconSpacing;
            try (SpeedPosition.Scope scope = h.begin(p, true)) {
                float x = p.getWidth()-p.getPaddingEnd(); boolean previous = false;
                for (int i = p.getChildCount() - 1; i >= 0; i--) {
                    View child = p.getChildAt(i); StatusIconDisplayable icon = (StatusIconDisplayable) child;
                    StatusIconContainer.StatusIconState s = (StatusIconContainer.StatusIconState) child.getTag(1);
                    if (!icon.isIconVisible() || h.iconBlocked(child, icon.isIconBlocked()) || p.mIgnoredSlots.contains(icon.getSlot())) { s.visibleState = 2; continue; }
                    if (previous) x -= p.mIconSpacing; previous = true;
                    x -= child.getWidth() + child.getPaddingStart() + h.allocationPaddingEnd(child, child.getPaddingEnd());
                    s.visibleState = 0; s.setXTranslation(x);
                }
                if (p.rtl) for (int i = 0; i < p.getChildCount(); i++) {
                    View child = p.getChildAt(i);
                    StatusIconContainer.StatusIconState s = (StatusIconContainer.StatusIconState) child.getTag(1);
                    s.setXTranslation(p.getWidth() - s.getXTranslation() - child.getWidth());
                }
                nativeAllocatedTarget=source.getXTranslation();
                h.prepareTargets(p);
                source.setXTranslation(source.getXTranslation()+rawSpeedDisplacement);
                rawSpeedTarget=source.getXTranslation();
                for (int i = 0; i < p.getChildCount(); i++) {
                    View child = p.getChildAt(i); h.beforeApply(child.getTag(1), child);
                }
            }
        }
        float globalX() { return phone.getLeft() + p.getLeft() + source.getXTranslation(); }
        float parentEnd() { return phone.getLeft() + p.getLeft() + p.getWidth(); }
        float relativeEndDistance() { return parentEnd() - globalX(); }
        float relativeDistance(){return p.rtl&&!SpeedPosition.RIGHT_START.equals(mode)?globalX()-phone.getLeft()-p.getLeft():relativeEndDistance();}
        void close() { h.releaseRuntime(); speed.getResources().getDisplayMetrics().density = 1f; }
    }

    public static void main(String[] args) throws Exception {
        minimal();allRadioStates();removedAndUnavailable();nonradioTailAndSignalGeometry();changingSignalResources();constrainedParentAndNativeDisplacement();asymmetricParentPadding();animatedNeighbourKeepsAnchor();clockOffsets();
        System.out.println("SpeedRelativePositionCheck: "+checks+" checks passed (relative edges, moving parents, all radio states and widths, cached native targets, clock offsets)");
    }

    static void minimal() throws Exception {
        Rig r = new Rig(SpeedPosition.CELLULAR_LEFT, false, false, 49.25f);
        r.state(true, true, 80);
        float before = r.relativeEndDistance(), firstGlobal = r.globalX();
        r.phone.moved = 64; r.pass();
        if (Math.abs(r.globalX() - firstGlobal - 64f) > .01f || Math.abs(r.relativeEndDistance() - before) > .01f)
            throw new AssertionError("relative placement must follow a moving parent, never cache a screen coordinate");
        close(before,r.relativeEndDistance(),"parent movement preserves relative frame");
        r.state(false, true, 197);
        float after = r.relativeEndDistance();
        for (int frame = 0; frame < 4; frame++) {
            r.pass();
            close(before,r.relativeEndDistance(),"reported WiFi/mobile transition remains stable frame "+frame);
        }
        r.close();
        close(before,after,"reported selected position+offset no longer drifts 29px");
    }

    static void allRadioStates()throws Exception{
        for(String mode:new String[]{SpeedPosition.NATIVE,SpeedPosition.CELLULAR_LEFT,SpeedPosition.RIGHT_START})
            for(boolean adjacent:new boolean[]{false,true})for(boolean rtl:new boolean[]{false,true})
            for(float reserve:new float[]{0f,20f,49.25f,100f})for(float offset:new float[]{-13f,0f,17f})
            for(float scale:new float[]{75f,100f,200f})for(int initial:new int[]{0,1,2,3}){
                Rig r=new Rig(mode,adjacent,rtl,reserve);
                r.options.putFloat("speed_offset_x",offset);r.options.putFloat("speed_scale",scale);
                r.options.putFloat(SpeedPosition.SAFE_GAP,-9f);r.h.configure(r.options);
                // code284: the page's switch is vertical-only. A native position without a saved
                // horizontal offset is no horizontal placement, so the speed stays native here.
                if(SpeedPosition.NATIVE.equals(mode)&&offset==0f&&!adjacent){r.close();continue;}
                r.state((initial&1)!=0,(initial&2)!=0,initial==2?400:80);
                float home=r.relativeDistance(),world=r.globalX();
                String where=" mode="+mode+" gap="+adjacent+" rtl="+rtl+" reserve="+reserve+" offset="+offset+" scale="+scale+" initial="+initial;
                r.phone.moved=71;r.pass();close(world+71f,r.globalX(),"moving parent follows"+where);
                close(home,r.relativeDistance(),"moving parent preserves relative edge"+where);
                for(int state=0;state<4;state++)for(int width:new int[]{0,80,197,320,400}){
                    r.state((state&1)!=0,(state&2)!=0,width);
                    close(home,r.relativeDistance(),"radio visibility/width must not select a different relative anchor"+where+" state="+state+" width="+width);
                    check(r.speedOuterWidth>0,"native speed allocation cannot collapse"+where);
                    check(r.speed.getParent()==r.p&&r.speed.getTranslationX()==0f&&r.speed.getPaddingEnd()==9,"native source owner/padding/translation remain exact"+where);
                }
                r.close();
            }
    }

    static void removedAndUnavailable()throws Exception{
        for(String mode:new String[]{SpeedPosition.NATIVE,SpeedPosition.CELLULAR_LEFT,SpeedPosition.RIGHT_START})
            for(boolean adjacent:new boolean[]{false,true})for(boolean rtl:new boolean[]{false,true}){
                Rig r=new Rig(mode,adjacent,rtl,49.25f);r.state(true,true,80);float home=r.relativeDistance();
                r.cell.blocked=true;r.pass();close(home,r.relativeDistance(),"blocked mobile retains virtual reference");
                r.wifi.blocked=true;r.pass();close(home,r.relativeDistance(),"both blocked radios retain virtual reference");
                r.cell.blocked=r.wifi.blocked=false;r.p.mIgnoredSlots.add("stacked_mobile");r.pass();close(home,r.relativeDistance(),"ignored mobile retains virtual reference");
                r.p.mIgnoredSlots.add("wifi");r.pass();close(home,r.relativeDistance(),"both ignored radios retain virtual reference");
                r.p.mIgnoredSlots.clear();r.p.removeView(r.wifi);r.h.changed(r.p);r.pass();close(home,r.relativeDistance(),"removed WiFi node retains relative frame");
                r.p.removeView(r.cell);r.h.changed(r.p);r.pass();close(home,r.relativeDistance(),"removed radio nodes retain relative frame");
                r.p.addView(r.cell);r.p.addView(r.wifi);r.h.changed(r.p);r.state(true,true,400);close(home,r.relativeDistance(),"replaced wider radio model retains relative frame");
                r.h.detached(r.speed);r.h.attached(r.speed);r.h.configure(r.options);r.pass();close(home,r.relativeDistance(),"reattached source does not learn a new reference");
                r.close();
            }
    }

    static void constrainedParentAndNativeDisplacement()throws Exception{
        Rig r=new Rig(SpeedPosition.CELLULAR_LEFT,false,false,40f);r.p.constrainedWidth=640;r.state(true,true,80);
        float raw=r.rawSpeedTarget,placed=r.source.getXTranslation();
        r.rawSpeedDisplacement=117f;r.state(true,true,197);
        close(raw+117f,r.rawSpeedTarget,"native coordination reads the selected frame before applying its movement");
        close(placed+117f,r.source.getXTranslation(),"changed radio width invalidates cached normalisation even with same row width and raw target");
        r.rawSpeedDisplacement=-38f;r.state(false,true,400);
        close(placed-38f,r.source.getXTranslation(),"OEM movement after target preparation survives wider radio normalisation");
        r.rawSpeedDisplacement=0f;r.state(false,false,0);close(placed,r.source.getXTranslation(),"OEM zero displacement returns to chosen relative frame");
        r.close();
        Rig defaults=new Rig(SpeedPosition.NATIVE,false,false,49.25f);defaults.options.putFloat("speed_offset_x",0f);defaults.h.configure(defaults.options);
        // code284: the page's switch is vertical-only. A native position with no saved horizontal
        // offset is not horizontal placement and leaves the speed to the native layout.
        try(SpeedPosition.Scope scope=defaults.h.begin(defaults.p,true)){check(scope==null,"native zero offset leaves the speed to the native layout");}
        defaults.options.putFloat("speed_offset_x",5f);defaults.h.configure(defaults.options);
        try(SpeedPosition.Scope scope=defaults.h.begin(defaults.p,true)){check(scope!=null,"a saved legacy horizontal offset still activates its placement");}
        defaults.state(true,true,80);float home=defaults.relativeDistance();defaults.state(false,true,400);close(home,defaults.relativeDistance(),"a legacy offset still normalises a wider radio");
        defaults.close();
    }

    static void nonradioTailAndSignalGeometry()throws Exception{
        for(String mode:new String[]{SpeedPosition.NATIVE,SpeedPosition.CELLULAR_LEFT,SpeedPosition.RIGHT_START})for(boolean rtl:new boolean[]{false,true}){
            Rig r=new Rig(mode,true,rtl,49.25f);Icon tail=new Icon("bluetooth",20);r.p.addView(tail);r.h.changed(r.p);
            r.h.configureNetwork(0f,100f,3f,150f);r.h.networkDrawable(false,72f,4f);android.os.Handler.drainForCheck();
            r.state(true,true,80);float home=r.relativeDistance();
            r.state(false,false,400);close(home,r.relativeDistance(),"radio hide retains nonradio tail and signal geometry anchor");
            r.p.removeView(r.wifi);r.p.removeView(r.cell);r.h.changed(r.p);r.pass();
            close(home,r.relativeDistance(),"radio physical removal cannot change data-offset/reference-ink decision");
            r.p.removeView(tail);r.p.addView(r.cell);r.p.addView(r.wifi);r.p.addView(tail);r.h.changed(r.p);r.state(true,true,400);
            close(home,r.relativeDistance(),"radio reattachment and width retain the same nonradio tail anchor");r.close();
        }
    }

    static void changingSignalResources()throws Exception{
        for(String mode:new String[]{SpeedPosition.NATIVE,SpeedPosition.CELLULAR_LEFT,SpeedPosition.RIGHT_START})
            for(boolean adjacent:new boolean[]{false,true})for(boolean rtl:new boolean[]{false,true}){
                Rig r=new Rig(mode,adjacent,rtl,49.25f);
                r.h.configureNetwork(-3f,150f,3f,200f);
                r.h.networkDrawable(false,72f,4f);r.h.networkDrawable(true,88f,4f);android.os.Handler.drainForCheck();
                r.state(true,true,80);float home=r.relativeDistance();
                for(int width:new int[]{120,72}){
                    boolean invalidated=r.h.networkDrawable(false,width,4f);
                    check(invalidated==adjacent,"changed data delegate invalidates the active gap geometry only");
                    java.lang.reflect.Field networkField=SpeedPosition.class.getDeclaredField("network");networkField.setAccessible(true);
                    Object geometry=networkField.get(r.h);java.lang.reflect.Field inkField=geometry.getClass().getDeclaredField("dataInkDp");inkField.setAccessible(true);
                    close(width/4f,inkField.getFloat(geometry),"actual data drawable intrinsic width is sampled independently from the fixed anchor");
                    android.os.Handler.drainForCheck();r.pass();
                    close(home,r.relativeDistance(),"changing signal-level resource cannot move the selected relative anchor");
                    r.state(false,true,197);close(home,r.relativeDistance(),"signal resource plus WiFi disappearance retains relative anchor");
                    r.h.networkDrawable(true,width,4f);android.os.Handler.drainForCheck();r.state(true,true,80);
                    close(home,r.relativeDistance(),"WiFi drawable and visibility changes retain relative anchor");
                }
                r.close();
            }
    }

    static final class ClockFrame extends FrameLayout{
        int left=3,right=5,top=1,bottom=2,moved;
        ClockFrame(){super(null);}
        @Override public int getLeft(){return moved;}
        @Override public int getPaddingLeft(){return left;}@Override public int getPaddingRight(){return right;}
        @Override public int getPaddingTop(){return top;}@Override public int getPaddingBottom(){return bottom;}
        @Override public void setPadding(int l,int t,int rr,int b){left=l;top=t;right=rr;bottom=b;}
        @Override public int getMeasuredWidth(){return 50+left+right;}
        @Override public int getWidth(){return getMeasuredWidth();}
    }

    static void asymmetricParentPadding()throws Exception{
        Rig constrained=new Rig(SpeedPosition.RIGHT_START,false,true,49.25f);constrained.p.constrainedWidth=640;
        constrained.p.paddingLeft=3;constrained.p.paddingRight=7;constrained.state(true,true,80);
        float constrainedHome=constrained.relativeEndDistance(),sameNativeTarget=constrained.nativeAllocatedTarget;
        constrained.p.paddingRight=13;constrained.pass();
        close(sameNativeTarget,constrained.nativeAllocatedTarget,"fixed-width RTL native allocation does not observe changed physical right padding before target preparation");
        close(constrainedHome+6f,constrained.relativeEndDistance(),"changed physical right padding invalidates cached physical-edge placement with identical native targets");constrained.close();
        Rig start=new Rig(SpeedPosition.RIGHT_START,false,true,49.25f);start.p.paddingLeft=3;start.p.paddingRight=7;start.state(true,true,80);
        close(322f,start.relativeEndDistance(),"RTL physical-left selection budgets from physical right padding 7, not logical end 3");
        start.p.paddingLeft=19;start.state(false,true,400);close(322f,start.relativeEndDistance(),"other physical edge padding cannot move chosen right-side anchor");
        start.p.paddingRight=13;start.state(false,false,0);close(328f,start.relativeEndDistance(),"selected physical right padding moves anchor by exactly 6px");start.close();
        Rig nativeRtl=new Rig(SpeedPosition.NATIVE,false,true,49.25f);nativeRtl.p.paddingLeft=3;nativeRtl.p.paddingRight=7;nativeRtl.state(true,true,80);
        close(188f,nativeRtl.relativeDistance(),"native RTL follows the logical end with physical left padding");
        nativeRtl.p.paddingRight=21;nativeRtl.state(false,true,400);close(188f,nativeRtl.relativeDistance(),"native logical-edge placement does not switch to physical-right padding");nativeRtl.close();
    }
    /** DOT/HIDDEN during a native transition does not release a model-visible
     * neighbour's relative frame. This runs at the pre-seeding phase too. */
    static void animatedNeighbourKeepsAnchor()throws Exception{
        for(boolean rtl:new boolean[]{false,true})for(boolean adjacent:new boolean[]{false,true}){
            Rig r=new Rig(SpeedPosition.NATIVE,adjacent,rtl,49.25f);
            Icon hint=new Icon("alarm_clock",20);r.p.addView(hint);r.h.changed(r.p);r.state(true,true,80);
            float placed=r.source.getXTranslation();
            for(int nativeVisible:new int[]{1,2,0})try(SpeedPosition.Scope scope=r.h.begin(r.p,true)){
                r.source.setXTranslation(r.nativeAllocatedTarget);hint.state.visibleState=nativeVisible;
                r.h.prepareTargets(r.p);
                close(placed,r.source.getXTranslation(),"pre-seeding position retains the model-visible neighbour while native animation hides it");
                check(hint.state.visibleState==nativeVisible,"target preparation does not reveal or change the native neighbour state");
            }
            r.close();
        }
    }
    static final class Clock extends TextView{Clock(){super(null);}@Override public int getMeasuredWidth(){return 50;}}
    static final class Start extends FrameLayout{Start(){super(null);}@Override public int getWidth(){return 1000;}}
    static void clockOffsets()throws Exception{
        for(String mode:new String[]{SpeedPosition.CLOCK_LEFT,SpeedPosition.CLOCK_RIGHT})for(float offset:new float[]{-7f,0f,11f}){
            SpeedPosition h=new SpeedPosition((source,parent,layout)->new NetworkSpeedView());h.resolve(SpeedRelativePositionCheck.class.getClassLoader());
            Phone phone=new Phone();Parent p=new Parent();phone.addView(p);ClockFrame host=new ClockFrame();Clock clock=new Clock();Start start=new Start();
            phone.addView(start);start.addView(host);host.addView(clock);phone.ids.put(2,clock);phone.ids.put(3,host);phone.ids.put(4,start);
            NetworkSpeedView speed=new NetworkSpeedView();speed.applyNativeConfiguration(88,36,44);speed.nativeHeight=80;
            speed.getResources().getDisplayMetrics().density=4f;StatusIconContainer.StatusIconState state=new StatusIconContainer.StatusIconState();speed.tags.put(1,state);
            NetworkSpeedIconState model=new NetworkSpeedIconState();model.visible=true;speed.applyNetworkState(model);p.addView(speed);h.attached(speed);h.setHooksReady(true);h.setAllocationReady(true);
            Bundle options=new Bundle();options.putBoolean("speed_enabled",true);options.putBoolean("speed_position_enabled",true);options.putString(SpeedPosition.POSITION,mode);options.putFloat("speed_offset_x",offset);
            h.configure(options);check(host.getChildCount()==2,"clock copy is created in the clock host");
            View copy=host.getChildAt(1);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)copy.getLayoutParams();
            boolean left=SpeedPosition.CLOCK_LEFT.equals(mode);
            float local=left?host.left+lp.leftMargin+copy.getWidth():host.getWidth()-host.right-copy.getWidth()-lp.rightMargin;
            float anchor=left?host.left:host.getWidth()-host.right;
            close(offset*4f,local-anchor,"signed clock offset is applied exactly once relative to current clock edge");
            float world=phone.getLeft()+host.getLeft()+local;phone.moved=53;host.moved=31;h.nativeUpdated(speed);
            FrameLayout.LayoutParams after=(FrameLayout.LayoutParams)copy.getLayoutParams();
            float now=left?host.left+after.leftMargin+copy.getWidth():host.getWidth()-host.right-copy.getWidth()-after.rightMargin;
            close(world+84f,phone.getLeft()+host.getLeft()+now,"clock copy follows parent and clock host movement");
            check(copy.getParent()==host&&speed.getParent()==p&&speed.getTranslationX()==0f,"clock copy preserves native source registration");
            h.releaseRuntime();check(host.getChildCount()==1&&host.left==3&&host.right==5,"clock release returns exact OEM padding");
            speed.getResources().getDisplayMetrics().density=1f;
        }
    }
}
