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
        @Override public Resources getResources(){return RES;}
        @Override public int getWidth(){return 100;}
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
        String slot;boolean shown=true;final StatusIconContainer.StatusIconState state=new StatusIconContainer.StatusIconState();
        Icon(String slot){super(null);this.slot=slot;}
        public String getSlot(){return slot;}public boolean isIconVisible(){return shown;}public boolean isIconBlocked(){return false;}
        @Override public int getWidth(){return 20;}@Override public Object getTag(int id){return id==1?state:null;}
    }
    private static Bundle settings(String where){Bundle b=new Bundle();b.putBoolean("speed_enabled",true);b.putBoolean("speed_position_enabled",true);b.putString(SpeedPosition.POSITION,where);return b;}
    private static StatusIconContainer.StatusIconState state(View v){return (StatusIconContainer.StatusIconState)v.getTag(1);}
    /** Actual DEX allocates from the end, then mirrors RTL, then calls each native state.applyToView. */
    private static void targets(SpeedPosition h,Parent p,boolean rtl){
        float x=100;
        for(int i=p.getChildCount()-1;i>=0;i--){View v=p.getChildAt(i);StatusIconContainer.StatusIconState s=state(v);
            StatusIconDisplayable nativeIcon=(StatusIconDisplayable)v;
            boolean blocked=h.iconBlocked(v,nativeIcon.isIconBlocked())||p.mIgnoredSlots.contains(nativeIcon.getSlot())||!nativeIcon.isIconVisible();
            if(blocked){s.visibleState=2;continue;}x-=v.getWidth();s.visibleState=0;s.setXTranslation(x);}
        if(rtl)for(int i=0;i<p.getChildCount();i++){View v=p.getChildAt(i);StatusIconContainer.StatusIconState s=state(v);s.setXTranslation(100-s.getXTranslation()-v.getWidth());}
    }
    private static void layout(SpeedPosition h,Parent p,boolean rtl,boolean change){
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
        wifi.shown=false;layout(h,parent,false,false);equal(2,state(wifi).visibleState);equal(36f,speedState.getXTranslation());equal(60f,state(hint).getXTranslation());
        wifi.shown=true;layout(h,parent,false,false);equal(16f,speedState.getXTranslation());equal(40f,state(hint).getXTranslation());
        int reads=parent.childReads;for(int i=0;i<1000;i++)layout(h,parent,false,false);
        equal(reads+parent.getChildCount()*2000,parent.childReads); // Only the fixture's two native loops read children, helper does not.
        for(int i=0;i<parent.getChildCount();i++)equal(nativeChildren.get(i),parent.getChildAt(i));equal(identity,speed.getParent());
        h.configure(settings("cellular_left"));layout(h,parent,true,true);equal(0f,speedState.getXTranslation());equal(24f,state(mobile).getXTranslation());
        mobile.slot="other";h.changed(parent);layout(h,parent,false,true);equal(36f,speedState.getXTranslation());
        mobile.slot="stacked_mobile";h.changed(parent);layout(h,parent,false,true);equal(56f,speedState.getXTranslation());
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
        System.out.println("SpeedPositionCheck: "+checks+" checks passed");
    }
}
