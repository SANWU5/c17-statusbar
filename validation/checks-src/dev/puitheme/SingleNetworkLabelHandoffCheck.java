package dev.puitheme;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.StatusIconContainer;

/** Production bridge -> single-SIM target application after a native binder rebuild.
 * This fixture verifies the real ConstraintLayout LP contract, not a second solver. */
public final class SingleNetworkLabelHandoffCheck {
    private static final int SIGNAL=71,LABEL=72,ROAM=73;
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    public static final class ParentParams extends ViewGroup.LayoutParams {
        public ParentParams(int width){super(width,24);}
        public ParentParams(ViewGroup.LayoutParams other){super(other.width,other.height);}
    }
    private static final class R extends Resources {
        @Override public int getIdentifier(String name,String type,String pkg){return name.equals("mobile_signal_data_outer")?SIGNAL:name.equals("mobile_roaming_text")?ROAM:0;}
    }
    private static final class Label extends TextView {
        final R resources=new R();int width=24;
        Label(){super(null);setId(LABEL);useLayoutBoundsForCheck=true;layout(20,0,44,24);}
        @Override public Resources getResources(){return resources;}
        @Override public int getMeasuredWidth(){return width;}
        @Override public int getMeasuredHeight(){return 24;}
    }
    private static final class Mobile extends ViewGroup implements StatusIconDisplayable {
        Mobile(){super(null);}
        public String getSlot(){return "mobile";}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return false;}
        @Override public int getMeasuredWidth(){return 124;}
        @Override public int getMeasuredHeight(){return 24;}
    }
    private static ConstraintLayout.LayoutParams nativeLabel(){
        ConstraintLayout.LayoutParams params=new ConstraintLayout.LayoutParams(-2,-2);
        params.endToEnd=0;params.bottomToBottom=0;params.bottomMargin=38;return params;
    }
    private static ConstraintLayout.LayoutParams nativeSignal(){
        ConstraintLayout.LayoutParams params=new ConstraintLayout.LayoutParams(-2,0);
        params.endToEnd=0;params.topToTop=0;params.bottomToBottom=0;return params;
    }
    private static final class Rig {
        final NetworkSharedSlotLayout bridge=new NetworkSharedSlotLayout();
        final StatusIconContainer container=new StatusIconContainer();
        final Mobile mobile=new Mobile();
        ConstraintLayout parent=new ConstraintLayout();
        final Label label=new Label();
        View signal=new View(null);
        final SingleNetworkLabelPlacement placement=new SingleNetworkLabelPlacement(label);
        ConstraintLayout.LayoutParams originalLabel=nativeLabel(),originalSignal=nativeSignal();
        Rig()throws Exception {
            container.addView(mobile);mobile.addView(parent,new ParentParams(124));
            parent.useLayoutBoundsForCheck=true;parent.layout(0,0,124,24);
            signal.setId(SIGNAL);signal.useLayoutBoundsForCheck=true;signal.layout(0,0,20,24);signal.measure(0,0);
            parent.addView(label,originalLabel);parent.addView(signal,originalSignal);
            bridge.resolve(getClass().getClassLoader());bridge.configure(true);placement.sharedLayout(bridge);placement.observeSharedHost();
        }
        void apply(int labelLeft,int signalLeft){
            NetworkSharedSlotLayout.Frame frame=bridge.frame(mobile);
            check(frame!=null&&frame.valid,"native frame is measurable");
            check(bridge.apply(frame,160,labelLeft,signalLeft,true,true),"production bridge accepts target");
        }
        void positioned(int labelLeft,int signalLeft,String why){
            ConstraintLayout.LayoutParams text=(ConstraintLayout.LayoutParams)label.getLayoutParams();
            ConstraintLayout.LayoutParams bars=(ConstraintLayout.LayoutParams)signal.getLayoutParams();
            check(text.leftToLeft==0&&text.leftMargin==labelLeft,why+" retains label physical target");
            check(bars.leftToLeft==0&&bars.leftMargin==signalLeft,why+" retains signal physical target");
            check(text.bottomMargin==0,why+" never flashes the native bottom badge margin");
        }
        void finish(){placement.sharedLayout(null);bridge.releaseRuntime();}
    }
    private static void sameTargetAfterNativeParams()throws Exception {
        Rig rig=new Rig();
        for(int width:new int[]{24,36,20,32,24}){
            rig.label.width=width;rig.apply(80,10);rig.positioned(80,10,"initial width "+width);
            // A native binding rebuild installs fresh LPs while text measurement
            // and the outer planned target remain exactly the same this frame.
            ConstraintLayout.LayoutParams fresh=nativeLabel();rig.label.setLayoutParams(fresh);
            rig.apply(80,10);rig.positioned(80,10,"same-width native rebuild "+width);
            int writes=rig.label.layoutRequests+rig.signal.layoutRequests+rig.parent.layoutRequests;
            for(int repeat=0;repeat<20;repeat++){rig.apply(80,10);rig.positioned(80,10,"steady rebuild "+repeat);}
            check(writes==rig.label.layoutRequests+rig.signal.layoutRequests+rig.parent.layoutRequests,"steady target does not cause continuous layout requests");
            rig.bridge.restore(rig.mobile);
            check(rig.label.getLayoutParams()==fresh,"restoration preserves latest OEM LP identity");
            rig.mobile.measure(0,0);rig.parent.measure(0,0);rig.signal.measure(0,0);
            rig.bridge.nativeMeasured(rig.container);rig.bridge.nativeLaidOut(rig.container);
        }
        rig.finish();
    }
    private static void parentHandoff()throws Exception {
        Rig rig=new Rig();rig.apply(80,10);
        ConstraintLayout old=rig.parent;
        old.removeView(rig.label);old.removeView(rig.signal);rig.mobile.removeView(old);
        rig.parent=new ConstraintLayout();rig.parent.useLayoutBoundsForCheck=true;rig.parent.layout(0,0,124,24);
        ParentParams newParent=new ParentParams(124);rig.mobile.addView(rig.parent,newParent);
        rig.parent.addView(rig.label);rig.parent.addView(rig.signal);
        rig.apply(80,10);rig.positioned(80,10,"new parent with reused leaf LPs");
        check(rig.parent.getLayoutParams()!=newParent&&rig.parent.getLayoutParams().width==160,"handoff expands the current parent, not the detached parent");
        rig.bridge.restore(rig.mobile);
        check(rig.parent.getLayoutParams()==newParent,"handoff restores the actual new native parent");
        rig.finish();
    }
    private static void signalHandoff(boolean shared)throws Exception {
        Rig rig=new Rig();if(!shared)rig.placement.sharedLayout(null);
        if(shared)rig.apply(80,10);else check(rig.placement.apply(),"ordinary native chain installed");
        View oldSignal=rig.signal;rig.parent.removeView(oldSignal);
        rig.signal=new View(null);rig.signal.setId(SIGNAL);rig.signal.useLayoutBoundsForCheck=true;
        rig.signal.layout(0,0,20,24);rig.signal.measure(0,0);
        ConstraintLayout.LayoutParams replacement=nativeSignal();rig.parent.addView(rig.signal,replacement);
        if(shared){rig.apply(80,10);rig.positioned(80,10,"replacement native signal");}
        else {
            check(rig.placement.apply(),"ordinary chain accepts replacement sibling");
            check(rig.signal.getLayoutParams()!=replacement,"ordinary chain does not keep using detached signal");
            check(((ConstraintLayout.LayoutParams)rig.signal.getLayoutParams()).endToStart==LABEL,"replacement participates in reciprocal chain");
        }
        check(oldSignal.getParent()==null&&oldSignal.getLayoutParams()==rig.originalSignal,"detached native sibling recovers original LP");
        rig.placement.restore();check(rig.signal.getLayoutParams()==replacement,"new sibling restores latest native LP");rig.finish();
    }
    private static void parentParamsAndNativeAnimation()throws Exception {
        Rig rig=new Rig();rig.apply(80,10);
        ParentParams fresh=new ParentParams(124);rig.parent.setLayoutParams(fresh);
        android.animation.ObjectAnimator animator=android.animation.ObjectAnimator.ofFloat(rig.label,View.TRANSLATION_X,4f,12f);
        animator.setDuration(1000);animator.start();animator.setCurrentPlayTime(375);
        float current=rig.label.getTranslationX();long time=animator.getCurrentPlayTime();
        int starts=animator.startsForCheck,cancels=animator.cancelsForCheck,seeks=animator.seeksForCheck;
        rig.label.setAlpha(.43f);rig.mobile.setTranslationX(17f);
        rig.apply(80,10);rig.positioned(80,10,"new parent parameters");
        check(rig.parent.getLayoutParams()!=fresh&&rig.parent.getLayoutParams().width==160,"same target reclaims replaced native parent LP once");
        for(int i=0;i<30;i++)rig.apply(80,10);
        check(animator.getCurrentPlayTime()==time&&animator.startsForCheck==starts&&animator.cancelsForCheck==cancels&&animator.seeksForCheck==seeks,"native animation is not canceled restarted or sought");
        check(rig.label.getTranslationX()==current&&rig.mobile.getTranslationX()==17f&&rig.label.getAlpha()==.43f,"native current coordinates and alpha are unchanged");
        rig.bridge.restore(rig.mobile);check(rig.parent.getLayoutParams()==fresh,"new parent parameters are the restoration baseline");rig.finish();
    }
    private static void hostHandoff()throws Exception {
        Rig rig=new Rig();rig.apply(80,10);
        Mobile replacement=new Mobile();rig.container.addView(replacement);
        rig.mobile.removeView(rig.parent);replacement.addView(rig.parent);
        rig.placement.observeSharedHost();
        check(rig.bridge.frame(replacement)==null,"new native host waits for its own real traversal after restoring former LPs");
        // Callback evidence from a different host tree cannot release the wait.
        StatusIconContainer foreign=new StatusIconContainer();
        rig.bridge.nativeMeasured(foreign);rig.bridge.nativeLaidOut(foreign);
        check(rig.bridge.frame(replacement)==null,"foreign lockscreen/root callbacks cannot unlock another host");
        rig.label.layout(20,0,44,24);rig.signal.layout(0,0,20,24);
        rig.bridge.nativeMeasured(rig.container);rig.bridge.nativeLaidOut(rig.container);
        NetworkSharedSlotLayout.Frame current=rig.bridge.frame(replacement);
        check(current!=null&&current.valid,"current host accepts its native measure/layout completion");
        check(rig.bridge.apply(current,160,80,10,true,true),"handoff can reapply the shared target after actual traversal");
        rig.positioned(80,10,"replacement native host");
        rig.finish();
    }
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("parent"))parentHandoff();
        else if(args.length==1&&args[0].equals("signal"))signalHandoff(false);
        else {sameTargetAfterNativeParams();parentHandoff();signalHandoff(true);signalHandoff(false);parentParamsAndNativeAnimation();hostHandoff();}
        System.out.println("SingleNetworkLabelHandoffCheck: "+checks+" checks passed");
    }
}
