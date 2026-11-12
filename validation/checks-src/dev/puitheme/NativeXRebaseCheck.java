package dev.puitheme;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.notification.stack.ViewState;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.phone.StatusIconContainer.StatusIconState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Resolve and exercise the actual production native reflection adapter, not an injected fake. */
public final class NativeXRebaseCheck {
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    private static void near(float expected,float actual,String why){check(Float.isFinite(actual)&&Math.abs(expected-actual)<.001f,why+": "+expected+" != "+actual);}
    private static boolean same(float a,float b){return Float.floatToIntBits(a)==Float.floatToIntBits(b);}

    private static final class Icon extends View implements StatusIconDisplayable {
        static final int STATE_TAG=0x7f0a1234;
        final Map<Integer,Object> tags=new HashMap<>();
        int resourceLookups, lookupTag=STATE_TAG;
        final Resources resources=new Resources(){
            @Override public int getIdentifier(String name,String type,String pkg){resourceLookups++;return "status_bar_view_state_tag".equals(name)?lookupTag:0;}
        };
        int direction,writes,renderedVisible,visibleWrites;
        boolean cachedVisible=true,blocked;
        Icon(){super(new Context());}
        public String getSlot(){return "wifi";}
        public boolean isIconVisible(){return cachedVisible;}
        public boolean isIconBlocked(){return blocked;}
        public int getVisibleState(){return renderedVisible;}
        public void setVisibleState(int state,boolean animate){if(animate)throw new AssertionError("partial visibility starts no icon animation");renderedVisible=state;visibleWrites++;}
        @Override public Object getTag(int key){return tags.get(key);}
        @Override public void setTag(int key,Object value){if(value==null)tags.remove(key);else tags.put(key,value);}
        @Override public Resources getResources(){return resources;}
        @Override public int getLayoutDirection(){return direction;}
        @Override public void setTranslationX(float value){super.setTranslationX(value);writes++;}
    }
    private static final class NativeParent extends StatusIconContainer {
        int direction;
        @Override public int getLayoutDirection(){return direction;}
    }
    private static final class Rig {
        final StatusBarFlowLayout flow=new StatusBarFlowLayout();
        final NativeParent parent=new NativeParent();
        final StatusIconState state=new StatusIconState();
        final Icon icon=new Icon();
        final Object access;
        final Method rebase,canRebase;
        ObjectAnimator animator;
        Rig()throws Exception{
            parent.addView(icon);icon.setTag(Icon.STATE_TAG,state);state.setXTranslation(205f);state.distanceToViewEnd=125f;icon.setTranslationX(12f);
            flow.resolve(NativeXRebaseCheck.class.getClassLoader());
            Field field=StatusBarFlowLayout.class.getDeclaredField("access");field.setAccessible(true);access=field.get(flow);
            check(access!=null,"production resolve creates its own adapter");
            rebase=StatusBarFlowLayout.NativeAccess.class.getMethod("rebase",Object.class,View.class,float.class,float.class,float.class);
            canRebase=StatusBarFlowLayout.NativeAccess.class.getMethod("canRebase",Object.class,View.class,float.class,float.class,float.class);
        }
        boolean shift(float dx,float parentWidthDelta,float childWidthDelta)throws Exception{
            return (Boolean)rebase.invoke(access,state,icon,dx,parentWidthDelta,childWidthDelta);
        }
        boolean preflight(float dx,float parentWidthDelta,float childWidthDelta)throws Exception{
            return (Boolean)canRebase.invoke(access,state,icon,dx,parentWidthDelta,childWidthDelta);
        }
        void retarget() {
            ((StatusBarFlowLayout.NativeAccess)access).retargetFromCurrent(state,icon);
        }
        void animate(long time){
            animator=ObjectAnimator.ofFloat(icon,View.TRANSLATION_X,10f,20f);animator.setDuration(1000);
            icon.setTag(ViewState.TAG_START_TRANSLATION_X,10f);icon.setTag(ViewState.TAG_END_TRANSLATION_X,20f);
            icon.setTag(ViewState.TAG_ANIMATOR_TRANSLATION_X,animator);animator.start();animator.setCurrentPlayTime(time);
        }
    }
    private static void missingGeometryContract() throws Exception {
        Rig missing=new Rig();
        ClassLoader noXHelper=new ClassLoader(NativeXRebaseCheck.class.getClassLoader()){
            @Override public Class<?> loadClass(String name)throws ClassNotFoundException{
                if(name.equals("com.android.systemui.statusbar.notification.stack.ViewState"))throw new ClassNotFoundException(name);
                return super.loadClass(name);
            }
        };
        missing.flow.resolve(noXHelper);
        Field accessField=StatusBarFlowLayout.class.getDeclaredField("access");accessField.setAccessible(true);
        StatusBarFlowLayout.NativeAccess nativeAccess=(StatusBarFlowLayout.NativeAccess)accessField.get(missing.flow);
        check(!nativeAccess.geometrySupported(),"missing native X-only contract explicitly declines Flow geometry");
        missing.icon.setTranslationX(missing.state.getXTranslation());
        missing.state.justAdded=true;missing.state.qsExpansionTransitioning=true;
        check(nativeAccess.canRebase(missing.state,missing.icon,0f,0f,0f),"a zero frame delta does not prove availability of a native X-only contract");
        try{nativeAccess.applyGeometry(missing.state,missing.icon);}catch(IllegalStateException unsupported){checks++;}
        check(missing.state.justAdded&&missing.state.qsExpansionTransitioning,"missing X helper cannot fall back to consuming a full native lifecycle even when X and widths already match");
        check(missing.state.nativeXCallsForCheck==0,"missing X helper invokes no full native apply as a fallback");
    }
    public interface NoRendererContract {
        String getSlot(); boolean isIconVisible(); boolean isIconBlocked();
    }
    private static void rendererVisibilityContract() throws Exception {
        Rig rendered=new Rig();StatusBarFlowLayout.NativeAccess nativeAccess=(StatusBarFlowLayout.NativeAccess)rendered.access;
        rendered.icon.setAlpha(0f);rendered.icon.setVisibility(View.INVISIBLE);
        rendered.state.justAdded=true;rendered.state.qsExpansionTransitioning=true;
        int expectedWrites=0,previous=0;
        for(int target:new int[]{2,2,0,0}){
            rendered.state.visibleState=target;nativeAccess.applyVisibility(rendered.state,rendered.icon);
            if(target!=previous)expectedWrites++;previous=target;
            check(rendered.icon.renderedVisible==target&&rendered.icon.visibleWrites==expectedWrites,"partial native visibility submits real renderer state and deduplicates equal destinations");
            near(0f,rendered.icon.getAlpha(),"partial native visibility retains exact native View alpha zero");
            check(rendered.icon.getVisibility()==View.INVISIBLE,"partial native visibility retains native View hidden state");
            check(rendered.state.justAdded&&rendered.state.qsExpansionTransitioning&&rendered.state.nativeXCallsForCheck==0,
                    "partial native visibility consumes no add/QS/X lifecycle");
        }
        Rig missing=new Rig();
        ClassLoader noRenderer=new ClassLoader(NativeXRebaseCheck.class.getClassLoader()){
            @Override public Class<?> loadClass(String name)throws ClassNotFoundException{
                if(name.equals("com.android.systemui.statusbar.StatusIconDisplayable"))return NoRendererContract.class;
                return super.loadClass(name);
            }
        };
        missing.flow.resolve(noRenderer);
        Field accessField=StatusBarFlowLayout.class.getDeclaredField("access");accessField.setAccessible(true);
        StatusBarFlowLayout.NativeAccess unavailable=(StatusBarFlowLayout.NativeAccess)accessField.get(missing.flow);
        check(!unavailable.geometrySupported(),"missing native renderer getter/setter declines Flow even if the X helper exists");
        missing.state.justAdded=true;missing.state.qsExpansionTransitioning=true;
        try{unavailable.applyVisibility(missing.state,missing.icon);throw new AssertionError("missing renderer contract accepted");}
        catch(IllegalStateException expected){checks++;}
        check(missing.state.justAdded&&missing.state.qsExpansionTransitioning&&missing.state.nativeXCallsForCheck==0&&missing.icon.visibleWrites==0,
                "missing renderer contract invokes no full apply as a fallback");
    }
    private static final class Snapshot {
        final float stateX,distance,current;
        final Object animator,start,end;
        final int calls,writes,starts,cancels,seeks,holderWrites;
        final long time;
        final float[] keyframes;
        Snapshot(Rig rig){
            stateX=rig.state.getXTranslation();distance=rig.state.distanceToViewEnd;current=rig.icon.getTranslationX();
            animator=rig.icon.getTag(ViewState.TAG_ANIMATOR_TRANSLATION_X);start=rig.icon.getTag(ViewState.TAG_START_TRANSLATION_X);end=rig.icon.getTag(ViewState.TAG_END_TRANSLATION_X);
            calls=rig.state.nativeXCallsForCheck;writes=rig.icon.writes;
            starts=rig.animator==null?0:rig.animator.startsForCheck;cancels=rig.animator==null?0:rig.animator.cancelsForCheck;
            seeks=rig.animator==null?0:rig.animator.seeksForCheck;time=rig.animator==null?0:rig.animator.getCurrentPlayTime();
            PropertyValuesHolder[] holders=rig.animator==null?new PropertyValuesHolder[0]:rig.animator.getValues();
            holderWrites=holders.length==0?0:holders[0].writesForCheck;keyframes=holders.length==0?new float[0]:holders[0].valuesForCheck();
        }
        void unchanged(Rig rig,String why){
            check(same(stateX,rig.state.getXTranslation())&&same(distance,rig.state.distanceToViewEnd)&&same(current,rig.icon.getTranslationX()),why+" leaves geometry intact");
            check(animator==rig.icon.getTag(ViewState.TAG_ANIMATOR_TRANSLATION_X)&&start==rig.icon.getTag(ViewState.TAG_START_TRANSLATION_X)&&end==rig.icon.getTag(ViewState.TAG_END_TRANSLATION_X),why+" preserves tag identities");
            check(calls==rig.state.nativeXCallsForCheck&&writes==rig.icon.writes,why+" makes no native/property call");
            if(rig.animator!=null){
                check(starts==rig.animator.startsForCheck&&cancels==rig.animator.cancelsForCheck&&seeks==rig.animator.seeksForCheck&&time==rig.animator.getCurrentPlayTime(),why+" preserves animator lifecycle/time");
                PropertyValuesHolder[] holders=rig.animator.getValues();
                check(holders.length==0||holderWrites==holders[0].writesForCheck&&java.util.Arrays.equals(keyframes,holders[0].valuesForCheck()),why+" does not rewrite keyframes");
            }
        }
    }
    private interface Change {void apply(Rig rig);}
    private static void reject(String why,Change change)throws Exception{
        Rig rig=new Rig();rig.animate(375);change.apply(rig);Snapshot saved=new Snapshot(rig);
        check(!rig.preflight(17,40,5),why+" rejected by read-only preflight");saved.unchanged(rig,why+" preflight");
        check(!rig.shift(17,40,5),why+" rejected");saved.unchanged(rig,why);
    }
    private static void acceptedPreflight(Rig rig,float dx,float parentDelta,float childDelta,String why)throws Exception{
        Snapshot saved=new Snapshot(rig);check(rig.preflight(dx,parentDelta,childDelta),why+" preflight accepted");saved.unchanged(rig,why+" preflight");
    }
    /** Device code163 profile: every native state read repeated the same full SystemUI string-pool lookup. */
    private static void stateLookupBudget()throws Exception{
        Rig rig=new Rig();StatusBarFlowLayout.NativeAccess access=(StatusBarFlowLayout.NativeAccess)rig.access;
        for(int i=0;i<2000;i++)check(access.state(rig.icon)==rig.state,"production state read retains its native tag identity");
        check(rig.icon.resourceLookups==1,"2000 native state reads resolve the stable resource id once, found "+rig.icon.resourceLookups);
        StatusIconState next=new StatusIconState();rig.icon.setTag(Icon.STATE_TAG,next);
        check(access.state(rig.icon)==next&&rig.icon.resourceLookups==1,"cache only the id; a reused icon reads its replacement state");
        Rig missing=new Rig();missing.icon.lookupTag=0;StatusBarFlowLayout.NativeAccess other=(StatusBarFlowLayout.NativeAccess)missing.access;
        check(other.state(missing.icon)==null,"an unavailable tag stays unavailable");
        missing.icon.lookupTag=Icon.STATE_TAG;
        check(other.state(missing.icon)==missing.state&&missing.icon.resourceLookups==2,"retry a missing resource when its package becomes ready");
        for(int i=0;i<100;i++)other.state(missing.icon);
        check(missing.icon.resourceLookups==2,"a recovered id has its own Resources cache entry");
    }
    public static void main(String[] ignored)throws Exception{
        missingGeometryContract();rendererVisibilityContract();
        stateLookupBudget();
        check(!ViewState.NO_NEW_ANIMATIONS.getAnimationFilter().animateX,"native no-new-animation filter has animateX false");
        for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL})
            for(float dx:new float[]{-17.125f,17.125f})for(float parentDelta:new float[]{-40,40})
                for(float childDelta:new float[]{-5,5})for(long time:new long[]{0,250,875}){
                    Rig rig=new Rig();rig.parent.direction=direction;rig.icon.direction=direction;rig.animate(time);
                    Object identity=rig.animator;float current=rig.icon.getTranslationX();long playTime=rig.animator.getCurrentPlayTime();
                    int starts=rig.animator.startsForCheck,cancels=rig.animator.cancelsForCheck,seeks=rig.animator.seeksForCheck;
                    float distance=125+(direction==View.LAYOUT_DIRECTION_RTL?dx:parentDelta-dx-childDelta);
                    acceptedPreflight(rig,dx,parentDelta,childDelta,"active native curve");
                    check(rig.shift(dx,parentDelta,childDelta),"exact active native contract accepted");
                    near(205+dx,rig.state.getXTranslation(),"pending state target translated independently from animator end");
                    near(current+dx,rig.icon.getTranslationX(),"in-flight current translates by the same delta");
                    near(10+dx,((Float)rig.icon.getTag(ViewState.TAG_START_TRANSLATION_X)),"native start tag translated");
                    near(20+dx,((Float)rig.icon.getTag(ViewState.TAG_END_TRANSLATION_X)),"native end tag translated");
                    near(distance,rig.state.distanceToViewEnd,"native distance rebased using RTL and both width deltas");
                    check(identity==rig.icon.getTag(ViewState.TAG_ANIMATOR_TRANSLATION_X)&&identity==rig.animator,"same native animator retained");
                    check(starts==rig.animator.startsForCheck&&cancels==rig.animator.cancelsForCheck&&seeks+1==rig.animator.seeksForCheck,"native in-place seek avoids restart/cancel");
                    check(playTime==rig.animator.getCurrentPlayTime()&&rig.animator.getDuration()==1000,"playtime and duration preserved");
                    check(rig.state.nativeXCallsForCheck==1,"actual reflected native API invoked exactly once");
                    float[] values=rig.animator.getValues()[0].valuesForCheck();near(10+dx,values[0],"holder start matches tag");near(20+dx,values[1],"holder end matches tag");
                }
        for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}){
            Rig rig=new Rig();rig.parent.direction=direction;rig.icon.direction=direction;acceptedPreflight(rig,-15,20,5,"native no-animator");check(rig.shift(-15,20,5),"no animator branch accepted");
            near(190,rig.state.getXTranslation(),"no animator future native state preserved separately");near(-3,rig.icon.getTranslationX(),"native API translates current instead of snapping to pending state");
            near(125+(direction==View.LAYOUT_DIRECTION_RTL?-15:20+15-5),rig.state.distanceToViewEnd,"no animator distance exact");
            check(rig.state.nativeXCallsForCheck==1&&rig.icon.getTag(ViewState.TAG_ANIMATOR_TRANSLATION_X)==null&&rig.icon.getTag(ViewState.TAG_START_TRANSLATION_X)==null&&rig.icon.getTag(ViewState.TAG_END_TRANSLATION_X)==null,"no animator path leaves native animation tags absent");
        }
        Rig sentinel=new Rig();sentinel.animate(375);sentinel.state.justAdded=true;sentinel.state.distanceToViewEnd=-1;
        check(sentinel.shift(13,20,5),"just-added native sentinel accepted");near(-1,sentinel.state.distanceToViewEnd,"justAdded -1 sentinel retained");
        Rig signed=new Rig();signed.state.distanceToViewEnd=-1;
        check(signed.shift(13,20,5),"signed native distance accepted after justAdded cleared");near(1,signed.state.distanceToViewEnd,"negative distance is a legitimate layout value after factory phase");
        Rig zero=new Rig();zero.animate(375);Snapshot beforeZero=new Snapshot(zero);
        acceptedPreflight(zero,0,20,5,"width-only native frame");
        check(zero.shift(0,20,5),"zero X shift still rebases parent/child width distance");near(140,zero.state.distanceToViewEnd,"zero X width correction exact");
        check(zero.state.nativeXCallsForCheck==beforeZero.calls&&zero.animator.seeksForCheck==beforeZero.seeks&&same(zero.icon.getTranslationX(),beforeZero.current),"zero X shift does not touch animation/current");
        Rig mismatchedDirection=new Rig();mismatchedDirection.parent.direction=View.LAYOUT_DIRECTION_RTL;mismatchedDirection.icon.direction=View.LAYOUT_DIRECTION_LTR;
        check(mismatchedDirection.shift(13,20,5),"native parent direction authoritative");near(138,mismatchedDirection.state.distanceToViewEnd,"parent RTL controls distance despite different child direction");

        reject("wrong native parent",rig->{rig.parent.removeView(rig.icon);new ViewGroup(null).addView(rig.icon);});
        reject("unbound native state",rig->rig.icon.setTag(Icon.STATE_TAG,new StatusIconState()));
        reject("wrong animator type",rig->rig.icon.setTag(ViewState.TAG_ANIMATOR_TRANSLATION_X,new ValueAnimator()));
        reject("wrong animator target",rig->rig.animator.setTarget(new View(null)));
        reject("wrong property",rig->{ObjectAnimator wrong=ObjectAnimator.ofFloat(rig.icon,"translationY",10,20);wrong.start();rig.icon.setTag(ViewState.TAG_ANIMATOR_TRANSLATION_X,wrong);});
        reject("wrong holder property",rig->rig.animator.setValues(PropertyValuesHolder.ofFloat("alpha",10,20)));
        reject("multiple holders",rig->rig.animator.setValues(PropertyValuesHolder.ofFloat("translationX",10,20),PropertyValuesHolder.ofFloat("alpha",0,1)));
        reject("missing holder",rig->rig.animator.setValues());
        reject("paused animator",rig->rig.animator.pause());
        reject("repeating animator",rig->rig.animator.setRepeatCount(1));
        reject("delayed animator",rig->{rig.animator.cancel();rig.animator.setStartDelay(100);rig.animator.start();});
        reject("ended animator",rig->rig.animator.cancel());
        reject("negative playtime",rig->rig.animator.setCurrentPlayTime(-1));
        reject("past-duration playtime",rig->rig.animator.setCurrentPlayTime(1001));
        reject("negative duration",rig->rig.animator.setDuration(-1));
        reject("missing start tag",rig->rig.icon.setTag(ViewState.TAG_START_TRANSLATION_X,null));
        reject("missing end tag",rig->rig.icon.setTag(ViewState.TAG_END_TRANSLATION_X,null));
        reject("wrong start tag type",rig->rig.icon.setTag(ViewState.TAG_START_TRANSLATION_X,"10"));
        reject("nonfinite start",rig->rig.icon.setTag(ViewState.TAG_START_TRANSLATION_X,Float.NaN));
        reject("nonfinite end",rig->rig.icon.setTag(ViewState.TAG_END_TRANSLATION_X,Float.POSITIVE_INFINITY));
        reject("nonfinite pending state",rig->rig.state.setXTranslation(Float.NaN));
        reject("nonfinite current",rig->rig.icon.setTranslationX(Float.NaN));
        reject("nonfinite distance",rig->rig.state.distanceToViewEnd=Float.NaN);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})for(int parameter=0;parameter<3;parameter++){
            Rig rig=new Rig();rig.animate(375);Snapshot snapshot=new Snapshot(rig);
            check(!rig.preflight(parameter==0?bad:17,parameter==1?bad:20,parameter==2?bad:5),"nonfinite delta rejected by preflight");snapshot.unchanged(rig,"nonfinite delta preflight");
            check(!rig.shift(parameter==0?bad:17,parameter==1?bad:20,parameter==2?bad:5),"nonfinite delta rejected");snapshot.unchanged(rig,"nonfinite delta");
        }
        Rig stale=new Rig();stale.icon.setTag(ViewState.TAG_START_TRANSLATION_X,10f);Snapshot beforeStale=new Snapshot(stale);
        check(!stale.shift(17,20,5),"orphan native start tag rejected");beforeStale.unchanged(stale,"orphan tag");
        Rig missingState=new Rig();Snapshot stateBefore=new Snapshot(missingState);
        check(!(Boolean)missingState.rebase.invoke(missingState.access,null,missingState.icon,17f,20f,5f),"null native state rejected");stateBefore.unchanged(missingState,"null native state");
        check(!(Boolean)missingState.rebase.invoke(missingState.access,new Object(),missingState.icon,17f,20f,5f),"foreign native state rejected");stateBefore.unchanged(missingState,"foreign native state");
        check(!(Boolean)missingState.rebase.invoke(missingState.access,missingState.state,null,17f,20f,5f),"null native child rejected");stateBefore.unchanged(missingState,"null child");
        Rig filter=new Rig();filter.animate(375);Snapshot beforeFilter=new Snapshot(filter);
        ViewState.NO_NEW_ANIMATIONS.getAnimationFilter().animateX=true;
        try{check(!filter.shift(17,20,5),"changed native filter rejected before invocation");beforeFilter.unchanged(filter,"changed filter");}
        finally{ViewState.NO_NEW_ANIMATIONS.getAnimationFilter().animateX=false;}

        Rig nested=new Rig();nested.animate(375);final boolean[] inner={true},innerPreflight={true};
        nested.state.nativeXCallbackForCheck=()->{try{innerPreflight[0]=nested.preflight(99,0,0);inner[0]=nested.shift(99,0,0);}catch(Exception failure){throw new AssertionError(failure);}};
        check(nested.shift(17,20,5),"outer native rebase succeeds despite callback");check(!inner[0],"native callback cannot recursively rebase the same adapter");
        check(!innerPreflight[0],"native callback cannot preflight another frame during its in-flight commit");
        near(222,nested.state.getXTranslation(),"reentry does not double-translate pending target");near(30.75f,nested.icon.getTranslationX(),"reentry does not double-translate in-flight current");
        check(nested.state.nativeXCallsForCheck==1&&nested.animator.seeksForCheck==2,"native reentry causes no second API invocation or seek");
        nested.state.nativeXCallbackForCheck=null;check(nested.shift(-17,-20,-5),"reentry guard cleared after native completion");
        near(205,nested.state.getXTranslation(),"reverse frame shift restores pending target exactly");near(13.75f,nested.icon.getTranslationX(),"reverse frame shift restores in-flight current exactly");
        Rig freshNativeTarget=new Rig();freshNativeTarget.animate(375);freshNativeTarget.state.nativeXCallbackForCheck=()->freshNativeTarget.state.setXTranslation(999f);
        check(freshNativeTarget.shift(17,20,5),"native callback may publish a new movement target");near(999,freshNativeTarget.state.getXTranslation(),"new native target survives temporary state restoration");
        near(30.75f,freshNativeTarget.icon.getTranslationX(),"new native target does not snap current X");
        Rig staleFilter=new Rig();ViewState.NO_NEW_ANIMATIONS.getAnimationFilter().animateX=true;
        try{staleFilter.flow.resolve(NativeXRebaseCheck.class.getClassLoader());}finally{ViewState.NO_NEW_ANIMATIONS.getAnimationFilter().animateX=false;}
        Field staleAccess=StatusBarFlowLayout.class.getDeclaredField("access");staleAccess.setAccessible(true);Object unavailable=staleAccess.get(staleFilter.flow);
        Snapshot unavailableBefore=new Snapshot(staleFilter);
        check(!(Boolean)staleFilter.rebase.invoke(unavailable,staleFilter.state,staleFilter.icon,17f,20f,5f),"unsupported no-new-animation filter at resolve disables only optional rebase");
        unavailableBefore.unchanged(staleFilter,"unsupported no-new-animation filter");
        for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}) {
            Rig moving=new Rig();moving.parent.direction=direction;moving.animate(375);
            float current=moving.icon.getTranslationX();int writes=moving.icon.writes;
            long play=moving.animator.getCurrentPlayTime();int nativeCalls=moving.state.nativeXCallsForCheck;
            moving.retarget();
            near(direction==View.LAYOUT_DIRECTION_RTL?current:moving.parent.getWidth()-current-moving.icon.getWidth(),
                    moving.state.distanceToViewEnd,"a real retarget uses current rendered X as native history");
            near(205,moving.state.getXTranslation(),"retarget retains the new native destination");
            near(current,moving.icon.getTranslationX(),"retarget never snaps current X");
            near(10,(Float)moving.icon.getTag(ViewState.TAG_START_TRANSLATION_X),"retarget leaves native curve start intact");
            near(20,(Float)moving.icon.getTag(ViewState.TAG_END_TRANSLATION_X),"retarget leaves native curve end intact");
            check(moving.icon.getTag(ViewState.TAG_ANIMATOR_TRANSLATION_X)==moving.animator,"retarget leaves animator identity intact");
            check(moving.animator.getCurrentPlayTime()==play&&moving.icon.writes==writes&&moving.state.nativeXCallsForCheck==nativeCalls,
                    "history handoff does not seek, write X or invoke native animation");
        }
        Rig unchangedTarget=new Rig();unchangedTarget.animate(375);unchangedTarget.state.setXTranslation(20);Snapshot sameTarget=new Snapshot(unchangedTarget);
        unchangedTarget.retarget();sameTarget.unchanged(unchangedTarget,"ongoing native destination retains original curve/history");
        Rig freshTarget=new Rig();freshTarget.animate(375);freshTarget.state.justAdded=true;freshTarget.state.distanceToViewEnd=-1;Snapshot freshBefore=new Snapshot(freshTarget);
        freshTarget.retarget();freshBefore.unchanged(freshTarget,"native added-icon sentinel remains native-owned");
        Rig pausedTarget=new Rig();pausedTarget.animate(375);pausedTarget.animator.pause();Snapshot pausedBefore=new Snapshot(pausedTarget);
        pausedTarget.retarget();pausedBefore.unchanged(pausedTarget,"paused unsupported target remains untouched");
        Rig noCurve=new Rig();Snapshot noCurveBefore=new Snapshot(noCurve);noCurve.retarget();noCurveBefore.unchanged(noCurve,"no animator needs no retarget history override");
        for(boolean qsPhase:new boolean[]{false,true})for(boolean addedIcon:new boolean[]{false,true}){
            Rig geometry=new Rig();StatusBarFlowLayout.NativeAccess nativeAccess=(StatusBarFlowLayout.NativeAccess)geometry.access;
            geometry.state.qsExpansionTransitioning=qsPhase;geometry.state.justAdded=addedIcon;
            geometry.state.setAlpha(.37f,"native coordinator");geometry.icon.setAlpha(0f);geometry.icon.setVisibility(View.INVISIBLE);
            float distance=geometry.state.distanceToViewEnd;
            nativeAccess.applyGeometry(geometry.state,geometry.icon);
            check(geometry.state.nativeXCallsForCheck==1,"root-only geometry invokes the inspected native X helper once");
            near(205,geometry.icon.getTranslationX(),"root-only geometry commits its actual native X target");
            near(0,geometry.icon.getAlpha(),"root-only geometry keeps exact native View alpha zero");
            check(geometry.icon.getVisibility()==View.INVISIBLE,"root-only geometry keeps native View visibility");
            near(.37f,geometry.state.getAlpha(),"root-only geometry keeps the native alpha state");
            check(geometry.state.qsExpansionTransitioning==qsPhase&&geometry.state.justAdded==addedIcon,
                    "root-only geometry cannot consume a native QS or add lifecycle");
            near(addedIcon ? distance : geometry.parent.getWidth()-205-geometry.icon.getWidth(),
                    geometry.state.distanceToViewEnd,"root-only geometry synchronizes its destination history but preserves an added-icon sentinel");
        }
        for(boolean previous:new boolean[]{false,true})for(boolean successor:new boolean[]{false,true}){
            Rig deferred=new Rig();StatusBarFlowLayout.NativeAccess nativeAccess=(StatusBarFlowLayout.NativeAccess)deferred.access;
            deferred.state.qsExpansionTransitioning=previous;deferred.state.justAdded=true;deferred.state.setAlpha(.37f,"native coordinator");
            Object context=nativeAccess.applyContext(deferred.state);deferred.state.qsExpansionTransitioning=successor;
            final boolean[] consumed={false};
            deferred.state.nativeXCallbackForCheck=()->{
                consumed[0]=true;
                check(deferred.state.qsExpansionTransitioning==previous,"reflected delayed native call sees its captured QS phase");
                check(deferred.state.justAdded,"native add flag survives until the real native apply consumes it");
                near(.37f,deferred.state.getAlpha(),"native alpha is never normalized while replaying a deferred call");
            };
            nativeAccess.apply(deferred.state,deferred.icon,context);
            check(consumed[0]&&deferred.state.nativeXCallsForCheck==1,"captured context invokes the actual reflected native apply exactly once");
            check(deferred.state.qsExpansionTransitioning==successor,"reflection adapter preserves the caller's successor QS phase");
            check(!deferred.state.justAdded,"native apply remains the owner of consuming justAdded");
            near(.37f,deferred.state.getAlpha(),"captured QS replay does not change native alpha state");
        }
        for(int visibility:new int[]{View.VISIBLE,View.INVISIBLE,View.GONE})for(boolean cached:new boolean[]{false,true})
            for(boolean blocked:new boolean[]{false,true})for(boolean ignoredSlot:new boolean[]{false,true}){
                Rig radio=new Rig();radio.icon.setVisibility(visibility);radio.icon.cachedVisible=cached;radio.icon.blocked=blocked;
                if(ignoredSlot)radio.parent.mIgnoredSlots.add("wifi");
                check(((StatusBarFlowLayout.NativeAccess)radio.access).eligible(radio.parent,radio.icon)
                                ==(visibility!=View.GONE&&cached&&!blocked&&!ignoredSlot),
                        "actual native eligibility follows cached logical visibility, blocked/ignored slots and GONE while retaining an eligible native fade");
            }
        System.out.println("NativeXRebaseCheck: "+checks+" checks passed");
    }
}
