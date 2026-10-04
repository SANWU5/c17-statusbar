package dev.puitheme;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.content.res.Resources;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView;
import com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState;
import java.util.*;

/** Executes native target allocation, state callbacks and FrameLayout reservations; never uses draw translations. */
public final class SpeedPositionCheck {
    private static int checks;
    private static void equal(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("SpeedPosition "+checks+": "+a+" != "+b);}
    private static final Resources RES=new Resources(){@Override public int getIdentifier(String name,String type,String pkg){
        switch(name){case "status_bar_view_state_tag":return 1;case "clock":return 2;case "clock_for_fake":return 3;
            case "status_bar_start_side_container":return 4;case "network_speed_view":return 5;default:return 0;}
    }};
    private static final class Phone extends PhoneStatusBarView {
        final Map<Integer,View> ids=new HashMap<>();
        @Override public View findViewById(int id){return ids.get(id);}
    }
    private static final class Parent extends StatusIconContainer {
        int width=100,extraWidth=16,requests;boolean rtl;
        @Override public void requestLayout(){requests++;super.requestLayout();}
        @Override public Resources getResources(){return RES;}
        @Override public int getWidth(){return width;}
        @Override public int getLeft(){return 400-width;} // Phone's real end-aligned wrap-content row.
        @Override public int getLayoutDirection(){return rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR;}
    }
    private static final class ClockFrame extends FrameLayout {
        int l=3,r=5,t=1,b=2;
        ClockFrame(){super(null);}
        @Override public void setPadding(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;requestLayout();}
        @Override public int getPaddingLeft(){return l;}@Override public int getPaddingTop(){return t;}
        @Override public int getPaddingRight(){return r;}@Override public int getPaddingBottom(){return b;}
        @Override public int getMeasuredWidth(){return 50+l+r;}
    }
    private static final class Clock extends TextView {Clock(){super(null);}@Override public int getMeasuredWidth(){return 50;}}
    private static final class Start extends FrameLayout {int width=150;Start(){super(null);}@Override public int getWidth(){return width;}}
    private static final class Icon extends View implements StatusIconDisplayable {
        String slot;boolean shown=true;int width=20;final StatusIconContainer.StatusIconState state=new StatusIconContainer.StatusIconState();
        Icon(String slot){super(null);this.slot=slot;}
        public String getSlot(){return slot;}public boolean isIconVisible(){return shown;}public boolean isIconBlocked(){return false;}
        @Override public int getWidth(){return width;}@Override public Object getTag(int id){return id==1?state:null;}
    }
    private static Bundle settings(String where){Bundle b=new Bundle();b.putBoolean("speed_enabled",true);b.putBoolean("speed_position_enabled",true);b.putString(SpeedPosition.POSITION,where);return b;}
    private static StatusIconContainer.StatusIconState state(View v){return (StatusIconContainer.StatusIconState)v.getTag(1);}
    /** Actual DEX allocates from the end, then mirrors RTL, then calls each native state.applyToView. */
    private static void targets(SpeedPosition h,Parent p,boolean rtl){
        p.rtl=rtl;float x=p.getWidth();boolean previous=false;
        for(int i=p.getChildCount()-1;i>=0;i--){View v=p.getChildAt(i);StatusIconContainer.StatusIconState s=state(v);
            StatusIconDisplayable nativeIcon=(StatusIconDisplayable)v;
            boolean blocked=h.iconBlocked(v,nativeIcon.isIconBlocked())||p.mIgnoredSlots.contains(nativeIcon.getSlot())||!nativeIcon.isIconVisible();
            if(blocked){s.visibleState=2;continue;}
            if(previous)x-=p.mIconSpacing;previous=true;
            x-=v.getWidth()+v.getPaddingStart()+h.allocationPaddingEnd(v,v.getPaddingEnd());s.visibleState=0;s.setXTranslation(x);}
        if(rtl)for(int i=0;i<p.getChildCount();i++){View v=p.getChildAt(i);StatusIconContainer.StatusIconState s=state(v);s.setXTranslation(p.getWidth()-s.getXTranslation()-v.getWidth());}
    }
    private static void measure(SpeedPosition h,Parent p){
        int width=p.extraWidth,count=0;
        try(SpeedPosition.Scope scope=h.begin(p,false)){
            for(int i=0;i<p.getChildCount();i++){
                View child=p.getChildAt(i);StatusIconDisplayable displayable=(StatusIconDisplayable)child;
                if(h.iconBlocked(child,displayable.isIconBlocked())||!displayable.isIconVisible()||p.mIgnoredSlots.contains(displayable.getSlot()))continue;
                width+=child.getMeasuredWidth()+child.getPaddingStart()+h.allocationPaddingEnd(child,child.getPaddingEnd());count++;
            }
        }
        p.width=width+Math.max(0,count-1)*p.mIconSpacing;
    }
    private static void layout(SpeedPosition h,Parent p,boolean rtl,boolean change){
        p.rtl=rtl;measure(h,p);
        if(change)h.beforeLayout(p);
        try(SpeedPosition.Scope scope=h.begin(p,true)){
            targets(h,p,rtl);
            for(int i=0;i<p.getChildCount();i++){View v=p.getChildAt(i);h.beforeApply(state(v),v);}
        }
    }
    public static void main(String[] args)throws Exception{
        equal("native",SpeedPosition.position("bad"));equal("native",SpeedPosition.position(null));
        equal("native",StatusBarSettings.string(Collections.emptyMap(),SpeedPosition.POSITION));
        SettingsCatalog.Item item=SettingsCatalog.item(SpeedPosition.POSITION);
        equal("网速位置",item.title);equal(5,item.values.length);equal(5,new HashSet<>(Arrays.asList(item.values)).size());
        int[] made={0};SpeedPosition h=new SpeedPosition((source,parent,layout)->{made[0]++;return new NetworkSpeedView();});h.resolve(SpeedPositionCheck.class.getClassLoader());
        Phone phone=new Phone();Parent parent=new Parent();phone.addView(parent);
        ClockFrame frame=new ClockFrame();Clock clock=new Clock();Start start=new Start();phone.addView(start);start.addView(frame);frame.addView(clock);
        phone.ids.put(2,clock);phone.ids.put(3,frame);phone.ids.put(4,start);clock.setTextColor(0xffabcdef);
        Icon hint=new Icon("bluetooth"),wifi=new Icon("wifi"),mobile=new Icon("stacked_mobile");NetworkSpeedView speed=new NetworkSpeedView();
        StatusIconContainer.StatusIconState speedState=new StatusIconContainer.StatusIconState();speed.tags.put(1,speedState);
        NetworkSpeedIconState value=new NetworkSpeedIconState();value.visible=true;value.speedText=42;speed.applyNetworkState(value);
        parent.addView(hint);parent.addView(speed);parent.addView(wifi);parent.addView(mobile);
        h.attached(speed);Object identity=speed.getParent();List<View> nativeChildren=Arrays.asList(hint,speed,wifi,mobile);
        equal(false,h.configure(settings("native")));layout(h,parent,false,true);equal(0,made[0]);equal(60f,state(wifi).getXTranslation());
        equal(true,h.configure(settings("cellular_left")));equal(null,h.begin(parent,true)); // Incomplete hook installation is native.
        h.setHooksReady(true);layout(h,parent,false,true);
        equal(null,h.begin(parent,true));equal(36f,speedState.getXTranslation()); // Allocation hook is mandatory too.
        h.setAllocationReady(true);layout(h,parent,false,true);
        equal(0f,h.drawOffsetXdp(speed,17f));equal(17f,h.drawOffsetXdp(new NetworkSpeedView(),17f));
        equal(56f,speedState.getXTranslation());equal(36f,state(wifi).getXTranslation());equal(80f,state(mobile).getXTranslation());
        equal(true,h.configure(settings("right_start")));layout(h,parent,false,true);
        equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());equal(60f,state(wifi).getXTranslation());
        h.beforeLayout(parent);
        try(SpeedPosition.Scope outer=h.begin(parent,true)){
            targets(h,parent,false);h.beforeApply(state(hint),hint);h.beforeApply(speedState,speed);
            for(int i=0;i<100;i++){h.beforeApply(speedState,speed);h.beforeApply(state(hint),hint);}
            equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());
            layout(h,parent,false,false); // Nested native update resets all targets before applying them.
            h.beforeApply(speedState,speed);h.beforeApply(state(hint),hint);
            equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());
        }
        float wifiOnSpeed=parent.getLeft()+speedState.getXTranslation();
        wifi.shown=false;layout(h,parent,false,false);equal(2,state(wifi).visibleState);equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());
        equal(wifiOnSpeed,parent.getLeft()+speedState.getXTranslation());equal(100,parent.getWidth());
        wifi.shown=true;layout(h,parent,false,false);equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());
        equal(0,speed.getPaddingEnd());equal(24,speed.getLayoutParams().width); // The virtual reservation is never real padding/size.
        int reads=parent.childReads;for(int i=0;i<1000;i++)layout(h,parent,false,false);
        equal(reads+parent.getChildCount()*3000,parent.childReads); // Only native measure/target/apply loops read children.
        for(int i=0;i<parent.getChildCount();i++)equal(nativeChildren.get(i),parent.getChildAt(i));equal(identity,speed.getParent());
        h.configure(settings("cellular_left"));layout(h,parent,true,true);equal(0f,speedState.getXTranslation());equal(24f,state(mobile).getXTranslation());
        wifi.shown=false;layout(h,parent,true,false);equal(0f,speedState.getXTranslation());wifi.shown=true;layout(h,parent,true,false);
        mobile.slot="other";h.changed(parent);layout(h,parent,false,true);equal(36f,speedState.getXTranslation());
        mobile.slot="stacked_mobile";h.changed(parent);layout(h,parent,false,true);equal(56f,speedState.getXTranslation());
        spacingAndOffsets(h,parent,speed,speedState,hint,wifi,mobile);
        h.configure(settings("clock_left"));equal(1,made[0]);equal(27,frame.l);equal(5,frame.r);equal(2,frame.getChildCount());
        NetworkSpeedView copy=(NetworkSpeedView)frame.getChildAt(1);equal(-1,copy.getId());equal("c17_network_speed_clock",copy.getSlot());
        equal(42L,copy.mState.speedText);equal(0xffabcdef,copy.tint);equal(identity,speed.getParent());equal(1f,copy.getAlpha());
        speed.setAlpha(0f);h.nativeUpdated(speed);equal(1f,copy.getAlpha()); // Source's native right mask is never copied.
        layout(h,parent,false,true);equal(2,speedState.visibleState);equal(false,h.iconBlocked(speed,false));
        value.speedText=123;speed.applyNetworkState(value);h.nativeUpdated(speed);equal(123L,copy.mState.speedText);
        clock.setTextColor(0xff112233);h.clockTintChanged(clock);equal(0xff112233,copy.tint);
        speed.setSlot("network_speed_alt");parent.mIgnoredSlots.add("network_speed_alt");h.slotChanged(speed);
        equal(View.GONE,copy.getVisibility());equal(3,frame.l);equal(5,frame.r);
        parent.mIgnoredSlots.clear();h.changed(parent);equal(View.VISIBLE,copy.getVisibility());equal(27,frame.l);
        value.visible=false;speed.applyNetworkState(value);h.nativeUpdated(speed);equal(View.GONE,copy.getVisibility());equal(3,frame.l);
        value.visible=true;speed.applyNetworkState(value);h.nativeUpdated(speed);equal(View.VISIBLE,copy.getVisibility());equal(27,frame.l);
        h.configure(settings("clock_right"));equal(copy,frame.getChildAt(1));equal(1,made[0]);equal(3,frame.l);equal(29,frame.r);
        frame.setPadding(7,4,31,6);h.nativeUpdated(speed); // A new OEM inset replaces our previous padding, not a saved stale value.
        h.configure(settings("native"));equal(7,frame.l);equal(31,frame.r);equal(4,frame.t);equal(6,frame.b);equal(1,frame.getChildCount());
        layout(h,parent,false,true);equal(0,speedState.visibleState);equal(36f,speedState.getXTranslation());
        start.width=65;h.configure(settings("clock_left"));equal(1,frame.getChildCount());equal(1,made[0]);
        start.width=150;phone.dispatchLayoutChange(150,80,65,80);equal(2,frame.getChildCount());equal(2,made[0]);
        h.setHooksReady(false);equal(1,frame.getChildCount());equal(null,h.begin(parent,true));equal(false,h.iconBlocked(speed,false));
        h.setHooksReady(true);equal(2,frame.getChildCount());
        Bundle safe=settings("clock_left");safe.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(safe);equal(1,frame.getChildCount());equal(null,h.begin(parent,true));
        Bundle positionOff=settings("clock_left");positionOff.putBoolean("speed_position_enabled",false);equal(false,h.configure(positionOff));
        Bundle masterOff=settings("right_start");masterOff.putBoolean("speed_enabled",false);equal(false,h.configure(masterOff));
        h.configure(settings("clock_right"));equal(2,frame.getChildCount());h.detached(speed);equal(1,frame.getChildCount());equal(0,phone.layoutListeners.size());
        h.attached(speed);h.configure(settings("native"));h.configure(settings("clock_left"));equal(2,frame.getChildCount());
        h.releaseRuntime();equal(1,frame.getChildCount());equal(0,phone.layoutListeners.size());equal(null,h.begin(parent,true));equal(false,h.configure(settings("clock_left")));equal(false,h.iconBlocked(speed,false));
        pjzNativeAllocation();
        System.out.println("SpeedPositionCheck: "+checks+" checks passed");
    }
    /** PJZ110 dump: density=4, speed=88(+9 native padding), mobile=80, Wi-Fi=88. */
    private static void pjzNativeAllocation()throws Exception{
        SpeedPosition h=new SpeedPosition();h.resolve(SpeedPositionCheck.class.getClassLoader());
        Phone phone=new Phone();Parent p=new Parent();p.extraWidth=0;phone.addView(p);
        NetworkSpeedView speed=new NetworkSpeedView();speed.applyNativeConfiguration(88,36,44);speed.nativeHeight=80;speed.nativePaddingEnd=9;
        speed.getResources().getDisplayMetrics().density=4f;
        StatusIconContainer.StatusIconState speedState=new StatusIconContainer.StatusIconState();speed.tags.put(1,speedState);
        NetworkSpeedIconState model=new NetworkSpeedIconState();model.visible=true;speed.applyNetworkState(model);
        Icon mobile=new Icon("stacked_mobile"),wifi=new Icon("wifi");mobile.width=80;wifi.width=88;
        p.addView(speed);p.addView(mobile);p.addView(wifi);h.attached(speed);h.setHooksReady(true);h.setAllocationReady(true);
        Bundle b=settings("native");b.putFloat("speed_offset_x",-3f);b.putBoolean("data_enabled",true);b.putFloat("data_offset_x",3f);
        h.configure(b);layout(h,p,false,true);
        equal(265,p.getWidth());equal(-12f,speedState.getXTranslation());equal(97f,state(mobile).getXTranslation());equal(177f,state(wifi).getXTranslation());
        float sourceWorld=p.getLeft()+speedState.getXTranslation();
        // With safety disabled, free offsets never reserve width or rewrite sibling targets.
        b.putFloat("speed_offset_x",5f);h.configure(b);layout(h,p,false,true);
        equal(265,p.getWidth());equal(20f,speedState.getXTranslation());equal(97f,state(mobile).getXTranslation());equal(177f,state(wifi).getXTranslation());
        b.putFloat("speed_offset_x",-3f);h.configure(b);layout(h,p,false,true);
        for(int i=0;i<32;i++){
            wifi.shown=i%2!=0;layout(h,p,false,false);
            equal(265,p.getWidth());equal(sourceWorld,p.getLeft()+speedState.getXTranslation());
            // Remaining mobile follows its native end alignment, not a synthetic Wi-Fi ghost slot.
            equal(wifi.shown?97f:185f,state(mobile).getXTranslation());
        }
        // The custom 4G/5G slot can replace all 88px of hidden Wi-Fi space. It must
        // not receive another Wi-Fi reservation on top of its expanded native host.
        for(int i=0;i<16;i++){
            wifi.shown=i%2!=0;mobile.width=wifi.shown?80:168;layout(h,p,false,false);
            equal(265,p.getWidth());equal(sourceWorld,p.getLeft()+speedState.getXTranslation());
            equal(97f,state(mobile).getXTranslation());
        }
        wifi.shown=false;mobile.width=208;layout(h,p,false,false);equal(305,p.getWidth());
        // A genuinely wider native label is never squeezed down to the saved network budget.
        mobile.width=80;wifi.shown=true;layout(h,p,false,false);
        wifi.shown=true;
        b.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,true);b.putFloat(SpeedPosition.SAFE_GAP,2f);h.configure(b);
        // Replay the actual DEX order: measureChild(speed), then measuredWidth + paddings.
        try(SpeedPosition.Scope row=h.begin(p,false)){
            int allocated=h.allocationPaddingEnd(speed,speed.getPaddingEnd());equal(37,allocated);
            try(SpeedPosition.ChildMeasureScope child=h.childMeasurement(p,speed)){
                equal(9,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));
                equal(null,h.childMeasurement(p,mobile));
                try(SpeedPosition.ChildMeasureScope nested=h.childMeasurement(p,speed)){
                    equal(9,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));
                }
                equal(9,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));
            }
            equal(allocated,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));
            try{
                try(SpeedPosition.ChildMeasureScope child=h.childMeasurement(p,speed)){throw new IllegalStateException("native measure failure");}
            }catch(IllegalStateException expected){}
            equal(allocated,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));
        }
        equal(9,h.allocationPaddingEnd(speed,speed.getPaddingEnd()));equal(88,speed.getLayoutParams().width);
        // State allocation is separate: child measurements cannot hide the outer native sum there.
        try(SpeedPosition.Scope row=h.begin(p,true)){equal(null,h.childMeasurement(p,speed));equal(37,h.allocationPaddingEnd(speed,9));}
        // Shared safety uses Wi-Fi's real 22dp delegate and data's 18dp delegate.
        // data remains centred on its 20dp Compose frame even when labels widen its host.
        h.networkDrawable(true,88f,4f);h.networkDrawable(false,72f,4f);
        h.configureNetwork(-3f,150f,3f,200f);android.os.Handler.drainForCheck();layout(h,p,false,true);
        equal(401,p.getWidth());
        float dataLeft=state(mobile).getXTranslation()+40f-72f+12f;
        float dataRight=state(mobile).getXTranslation()+40f+72f+12f;
        float wifiLeft=state(wifi).getXTranslation()+44f-66f-12f;
        equal(8f,wifiLeft-dataRight);equal(true,dataLeft-speedState.getXTranslation()-speed.getWidth()>=8f);
        float safeWorld=p.getLeft()+speedState.getXTranslation(),mobileOrigin=state(mobile).getXTranslation();
        wifi.shown=false;mobile.width=168;layout(h,p,false,false);
        equal(401,p.getWidth());equal(safeWorld,p.getLeft()+speedState.getXTranslation());equal(mobileOrigin,state(mobile).getXTranslation());
        // Changing/deleting drawables on a background resource thread caches numbers only.
        // A burst queues one main-thread invalidation and cannot touch Views from that thread.
        android.os.Handler.drainForCheck();int requests=p.requests,reads=p.childReads,queued=android.os.Handler.postedForCheck.size();
        Thread resourceThread=new Thread(()->{
            android.os.Looper.setCurrentForCheck(null);
            for(int i=0;i<80;i++)h.networkDrawable(false,76f+i%4,4f);
            android.os.Looper.resetCurrentForCheck();
        });resourceThread.start();resourceThread.join();
        equal(requests,p.requests);equal(reads,p.childReads);equal(queued+1,android.os.Handler.postedForCheck.size());
        equal(false,h.networkDrawable(false,Float.NaN,4f));equal(false,h.networkDrawable(true,-3f,4f));
        android.os.Handler.drainForCheck();equal(true,p.requests>requests);
        // Disabling shared safety while Wi-Fi is absent drops its old expanded safety
        // budget. Free placement again uses only the current native 168px mobile host.
        b.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,false);h.configure(b);layout(h,p,false,true);
        equal(265,p.getWidth());equal(-12f,speedState.getXTranslation());equal(97f,state(mobile).getXTranslation());
        b.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,true);h.configure(b);layout(h,p,false,true);
        // Final worker delegate is 79px; cached on-state frames, current scale and live
        // native mobile width must rebuild the budget instead of freezing an old total.
        equal(415,p.getWidth());equal(88,speed.getLayoutParams().width);equal(9,speed.getPaddingEnd());
        // If the native cutout forces a genuinely narrower allocation, no renderer
        // setting is silently reset: keep the requested speed offset and native states.
        p.width=120;h.beforeLayout(p);
        try(SpeedPosition.Scope row=h.begin(p,true)){
            targets(h,p,false);float nativeSpeed=speedState.getXTranslation(),nativeMobile=state(mobile).getXTranslation();
            h.beforeApply(speedState,speed);h.beforeApply(state(mobile),mobile);
            equal(nativeSpeed-12f,speedState.getXTranslation());equal(nativeMobile,state(mobile).getXTranslation());
        }
        wifi.shown=true;mobile.width=80;
        b.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(b);layout(h,p,false,true);
        equal(265,p.getWidth());equal(0f,speedState.getXTranslation());equal(9,speed.getPaddingEnd());
        h.releaseRuntime();equal(null,h.childMeasurement(p,speed));
        speed.getResources().getDisplayMetrics().density=1f;
    }
    private static void spacingAndOffsets(SpeedPosition h,Parent parent,NetworkSpeedView speed,
            StatusIconContainer.StatusIconState speedState,Icon hint,Icon wifi,Icon mobile)throws Exception{
        equal(false,SpeedPosition.booleanDefaults().get(SpeedPosition.SAFE_GAP_ENABLED));equal(2f,SpeedPosition.numberDefaults().get(SpeedPosition.SAFE_GAP));
        speed.getResources().getDisplayMetrics().density=1f;
        parent.mIconSpacing=3;
        Bundle s=settings("right_start");h.configure(s);layout(h,parent,false,true);
        float absolute=parent.getLeft()+speedState.getXTranslation();int width=parent.getWidth();
        equal(3f,state(hint).getXTranslation()-speedState.getXTranslation()-speed.getWidth());
        for(int i=0;i<40;i++){
            wifi.shown=i%2!=0;layout(h,parent,false,false);
            equal(absolute,parent.getLeft()+speedState.getXTranslation());equal(width,parent.getWidth());
            equal(3f,state(hint).getXTranslation()-speedState.getXTranslation()-speed.getWidth());
        }
        wifi.shown=true;
        s.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,true);s.putFloat(SpeedPosition.SAFE_GAP,7f);
        h.configure(s);layout(h,parent,false,true);
        equal(7f,state(hint).getXTranslation()-speedState.getXTranslation()-speed.getWidth());
        equal(7f,state(wifi).getXTranslation()-state(hint).getXTranslation()-hint.getWidth());
        equal(0,speed.getPaddingEnd());equal(24,speed.getLayoutParams().width);
        float gapAbsolute=parent.getLeft()+speedState.getXTranslation();
        wifi.shown=false;layout(h,parent,false,false);
        equal(gapAbsolute,parent.getLeft()+speedState.getXTranslation());
        equal(7f,state(hint).getXTranslation()-speedState.getXTranslation()-speed.getWidth());
        s.putFloat("speed_offset_x",-9f);h.configure(s);layout(h,parent,false,true);
        equal(gapAbsolute-9,parent.getLeft()+speedState.getXTranslation());
        equal(true,state(hint).getXTranslation()-speedState.getXTranslation()-speed.getWidth()>=7);
        // A speed between siblings reserves and maintains both local clearances, including OEM spacing.
        wifi.shown=true;s=settings("cellular_left");s.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,true);s.putFloat(SpeedPosition.SAFE_GAP,6f);
        s.putFloat("speed_offset_x",-4f);h.configure(s);layout(h,parent,false,true);
        equal(6f,speedState.getXTranslation()-state(wifi).getXTranslation()-wifi.getWidth());
        equal(true,state(mobile).getXTranslation()-speedState.getXTranslation()-speed.getWidth()>=6);
        float cellAbsolute=parent.getLeft()+speedState.getXTranslation();
        wifi.shown=false;layout(h,parent,false,false);equal(cellAbsolute,parent.getLeft()+speedState.getXTranslation());
        // draw uses a centre pivot: reserve the extra extent without changing the fixed native XML width.
        wifi.shown=true;s=settings("right_start");s.putBoolean(SpeedPosition.SAFE_GAP_ENABLED,true);
        s.putFloat(SpeedPosition.SAFE_GAP,6f);s.putFloat("speed_scale",200f);h.configure(s);layout(h,parent,false,true);
        float paintedLeft=speedState.getXTranslation()-12f,paintedRight=speedState.getXTranslation()+speed.getWidth()+12f;
        equal(6f,state(hint).getXTranslation()-paintedRight);equal(24,speed.getLayoutParams().width);equal(0,speed.getPaddingEnd());
        float enlargedAbsolute=parent.getLeft()+paintedLeft;
        wifi.shown=false;layout(h,parent,false,false);equal(enlargedAbsolute,parent.getLeft()+speedState.getXTranslation()-12f);
        // Native overflow/dot ownership wins; custom placement never resurrects a hidden native state.
        wifi.shown=true;measure(h,parent);h.beforeLayout(parent);
        try(SpeedPosition.Scope scope=h.begin(parent,true)){
            targets(h,parent,false);speedState.visibleState=1;
            float nativeDot=speedState.getXTranslation(),nativeHint=state(hint).getXTranslation();
            for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);h.beforeApply(state(child),child);}
            equal(1,speedState.visibleState);equal(nativeDot,speedState.getXTranslation());equal(nativeHint,state(hint).getXTranslation());
        }
        // Native selection with no offset/gap preserves the OEM's changing Wi-Fi allocation.
        h.configure(settings("native"));wifi.shown=true;layout(h,parent,false,true);
        float nativeOn=parent.getLeft()+speedState.getXTranslation();wifi.shown=false;layout(h,parent,false,false);
        equal(nativeOn+wifi.getWidth()+parent.mIconSpacing,parent.getLeft()+speedState.getXTranslation());
        // Unsupported owner/state hooks allocate nothing and leave native targets, even with a saved offset.
        Bundle partial=settings("right_start");partial.putFloat("speed_offset_x",-11f);
        h.configure(partial);h.setAllocationReady(false);wifi.shown=true;layout(h,parent,false,true);
        equal(null,h.begin(parent,true));equal(0f,h.drawOffsetXdp(speed,-11f));equal(0,h.allocationPaddingEnd(speed,0));
        h.setAllocationReady(true);
        // Parent measured width/RTL/density changes still recompute from native slots, not a stale pixel lock.
        h.configure(settings("right_start"));layout(h,parent,true,true);
        float rtl=parent.getLeft()+speedState.getXTranslation();wifi.shown=false;layout(h,parent,true,false);
        equal(rtl,parent.getLeft()+speedState.getXTranslation());
        // The audited C17 variant has no mIconSpacing field and uses zero, not a failed capability gate.
        java.lang.reflect.Field optionalSpacing=SpeedPosition.class.getDeclaredField("iconSpacing");optionalSpacing.setAccessible(true);optionalSpacing.set(h,null);
        parent.mIconSpacing=0;wifi.shown=true;h.configure(settings("right_start"));layout(h,parent,false,true);
        float c17=parent.getLeft()+speedState.getXTranslation();wifi.shown=false;layout(h,parent,false,false);
        equal(c17,parent.getLeft()+speedState.getXTranslation());equal(true,h.handlesHorizontalOffset(speed));
        wifi.shown=true;parent.mIconSpacing=0;h.configure(settings("cellular_left"));layout(h,parent,false,true);
    }
}
