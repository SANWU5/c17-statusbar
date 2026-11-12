// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Parameter/scope checks. Real method signatures and animation ownership are separately DEX-audited. */
public final class NotificationNativeStackCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Native whole stack "+checks);}
    private static void equal(float expected,float actual){check(Math.abs(expected-actual)<.001f);}
    public static class State { public float y;public int height;public float getYTranslation(){return y;} }
    public static class Expandable extends View {
        public final State target=new State();
        Expandable(float y,int height){super(new Context());target.y=y;target.height=height;}
        public State getViewState(){return target;}
    }
    public static class Row extends Expandable {
        public boolean child,removed,mDismissed,mIsUserSwipingToExpandRow,changing;
        Row(float y,int height){super(y,height);}
        public boolean isChildInGroup(){return child;}
        public boolean isRemoved(){return removed;}
        public boolean areGutsExposed(){return changing;}
        public boolean isExpandAnimationRunning(){return false;}
        public boolean isGroupExpansionChanging(){return false;}
    }
    public static final class Dimens {
        public float scale=24,translation=18;
        public float getScaleDampingBaseHeight(){return scale;}
        public float getTranslationDampingBaseHeight(){return translation;}
    }
    public static final class Algorithm {
        public final Dimens dimens=new Dimens();
        public Dimens currentDynamicStackDimens(){return dimens;}
        public static float stableHeightOf(Expandable row){return row.target.height;}
    }
    public static final class Manager {
        private final Algorithm dynamicStackAlgorithm=new Algorithm();
        public void applyDynamic(List<?> rows,float border){}
    }
    public interface ReadFlow {Object getValue();}
    public interface WriteFlow extends ReadFlow {void setValue(Object value);}
    public static final class Flow implements WriteFlow {
        boolean value;int reads,writes;
        public Object getValue(){reads++;return value;}
        public void setValue(Object value){writes++;this.value=Boolean.TRUE.equals(value);}
    }
    public static final class FailingFlow implements WriteFlow {
        boolean value,alwaysFail,failAfterWrite;int readFailures,writeFailures,reads,attempts;
        Runnable onWrite;
        public Object getValue(){reads++;if(readFailures>0){readFailures--;throw new IllegalStateException("flow read");}return value;}
        public void setValue(Object next){attempts++;if(alwaysFail||writeFailures>0){if(writeFailures>0)writeFailures--;throw new IllegalStateException("flow write");}
            value=Boolean.TRUE.equals(next);if(onWrite!=null)onWrite.run();if(failAfterWrite){failAfterWrite=false;throw new IllegalStateException("flow collector");}}
    }
    public static final class NoFlowRuler {
        private final NativeContext context=new NativeContext();
        private final Manager stackAlgorithmManager=new Manager();
        public boolean currentIsStackedNotification;
        public boolean getCurrentIsStackedNotification(){return currentIsStackedNotification;}
    }
    public static final class NativeContext extends Context {
        final Resources resources=new Resources();
        int reads;
        NativeContext(){resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;}
        @Override public Resources getResources(){reads++;return resources;}
    }
    public static final class Ruler {
        private final NativeContext context=new NativeContext();
        private final Manager stackAlgorithmManager=new Manager();
        public boolean currentIsStackedNotification;
        private WriteFlow _isStackedNotification=new Flow();
        public boolean getCurrentIsStackedNotification(){return currentIsStackedNotification;}
    }
    public static final class Ambient { public int scroll;public boolean mShadeExpanded=true;public int getScrollY(){return scroll;} }
    public static final class Extension {
        public Object current;
        private Object getCurrentStackRuler(){return current;}
        public void updateStackedNotification(List<?> rows,Ambient ambient,float fraction){}
    }
    private static Bundle settings(float count){Bundle b=new Bundle();b.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);
        b.putBoolean(NotificationBigClockSettings.MASTER,true);b.putFloat(NotificationBigClockSettings.VISIBLE_COUNT,count);return b;}
    private static NotificationNativeStack helper(float count)throws Throwable {
        NotificationNativeStack nativeStack=new NotificationNativeStack();nativeStack.configure(settings(count));
        NotificationNativeStack.Access access=new NotificationNativeStack.Access(Extension.class,Ruler.class,
                Manager.class,Algorithm.class,Ambient.class,Row.class,Expandable.class);
        Field field=NotificationNativeStack.class.getDeclaredField("access");field.setAccessible(true);field.set(nativeStack,access);
        nativeStack.hooksReady(true);
        return nativeStack;
    }
    public static void main(String[] args)throws Throwable {
        if(args.length>0){if("flow-apply-failure".equals(args[0]))flowApplyFailure();
            else if("flow-restore-failure".equals(args[0]))flowRestoreFailure();
            else throw new AssertionError(args[0]);
            System.out.println("Notification native stack failure checks passed: "+checks);return;}
        upwardGesture();nativeScrollAndDimensions();scopesAndFallback();directionDependencies();nativeSceneAndFlow();liveOrientationAndFlow();savedClockDependency();
        foldBackAndTransition();managedRows();flowApplyFailure();flowRestoreFailure();flowRetryLifecycle();
        System.out.println("Notification native stack checks passed: "+checks);
    }
    private static void flowApplyFailure()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();FailingFlow flow=new FailingFlow();
        ruler._isStackedNotification=flow;flow.writeFailures=1;check(helper.currentStacked(ruler,false));
        check(!flow.value&&flow.attempts==1);
        for(int i=0;i<1000;i++)check(helper.currentStacked(ruler,false));
        check(flow.attempts==1); // A failed setter is not retried once per frame.
        android.os.SystemClock.uptime+=250;helper.currentStacked(ruler,false);
        check(flow.value&&flow.attempts==2); // Fails on the old false-success implementation.
        check(helper.diagnosticSummary().contains("flowPendingSync=0 flowPendingRestore=0"));
        check(helper.diagnosticSummary().contains("flowFailures=1 flowLastFailure=flow_write:IllegalStateException"));
        int reads=flow.reads;for(int i=0;i<1000;i++)helper.currentStacked(ruler,false);
        check(flow.reads==reads&&flow.attempts==2);helper.configure(null);check(!flow.value);
    }
    private static void flowRestoreFailure()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();FailingFlow flow=new FailingFlow();ruler._isStackedNotification=flow;
        helper.currentStacked(ruler,false);check(flow.value);flow.writeFailures=1;helper.configure(null);
        check(flow.value&&flow.attempts==2);
        for(int i=0;i<1000;i++){helper.configurationChanged();check(!helper.currentStacked(ruler,false));}
        check(flow.attempts==2);
        android.os.SystemClock.uptime+=250;helper.configurationChanged();
        check(!flow.value&&flow.attempts==3); // Old restore discarded this pending record.
    }
    private static java.util.Map<?,?> flowStates(NotificationNativeStack helper)throws Throwable {
        Field f=NotificationNativeStack.class.getDeclaredField("observedRulers");f.setAccessible(true);return (java.util.Map<?,?>)f.get(helper);
    }
    private static android.os.Handler retryHandler(NotificationNativeStack helper)throws Throwable {
        Field f=NotificationNativeStack.class.getDeclaredField("flowRetryHandler");f.setAccessible(true);return (android.os.Handler)f.get(helper);
    }
    private static void retryFlow(NotificationNativeStack helper)throws Throwable {
        android.os.Handler handler=retryHandler(helper);check(handler!=null&&handler.delayed!=null);
        android.os.SystemClock.uptime+=handler.delay;Runnable callback=handler.delayed;handler.delayed=null;callback.run();
    }
    private static ClassLoader nativeLoader(final Class<?> ruler){return new ClassLoader(NotificationNativeStackCheck.class.getClassLoader()){
        @Override public Class<?> loadClass(String name)throws ClassNotFoundException {
            if(NotificationNativeStack.EXTENSION.equals(name))return Extension.class;
            if(NotificationNativeStack.SHADE_RULER.equals(name))return ruler;
            if(NotificationNativeStack.MANAGER.equals(name))return Manager.class;
            if(NotificationNativeStack.ALGORITHM.equals(name))return Algorithm.class;
            if(NotificationNativeStack.AMBIENT.equals(name))return Ambient.class;
            if(NotificationNativeStack.ROW.equals(name))return Row.class;
            if(NotificationNativeStack.EXPANDABLE.equals(name))return Expandable.class;
            return super.loadClass(name);
        }
    };}
    private static void flowRetryLifecycle()throws Throwable {
        // Disabling, removing hooks, reset, resolve failure and a different ruler contract must
        // finish restoration without another native getter. Every pending record owns its Access.
        for(int boundary=0;boundary<6;boundary++){
            NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();FailingFlow flow=new FailingFlow();ruler._isStackedNotification=flow;
            helper.currentStacked(ruler,false);flow.writeFailures=1;
            switch(boundary){case 0:helper.configure(null);break;case 1:helper.hooksReady(false);break;
                case 2:helper.reset();break;case 3:check(!helper.resolve(new ClassLoader(null){}));break;
                case 4:check(helper.resolve(nativeLoader(NoFlowRuler.class)));helper.hooksReady(true);
                    check(helper.currentStacked(new NoFlowRuler(),false));break;
                default:ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;helper.configurationChanged();}
            check(flow.value&&flowStates(helper).containsKey(ruler));retryFlow(helper);
            check(!flow.value&&!flowStates(helper).containsKey(ruler));check(retryHandler(helper).delayed==null);
        }
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();FailingFlow flow=new FailingFlow();ruler._isStackedNotification=flow;
        flow.readFailures=1;helper.currentStacked(ruler,false);check(flow.reads==1&&flow.attempts==0);
        check(helper.diagnosticSummary().contains("flowPendingSync=1 flowPendingRestore=0"));
        check(helper.diagnosticSummary().contains("flowLastFailure=flow_read:IllegalStateException"));
        retryFlow(helper);check(flow.value&&flow.attempts==1);helper.configure(null);check(!flow.value);
        // A permanently throwing implementation retains bounded retries, no callback queue growth.
        helper=helper(1);ruler=new Ruler();flow=new FailingFlow();flow.alwaysFail=true;ruler._isStackedNotification=flow;
        helper.currentStacked(ruler,false);
        long[] delays={250,500,1000,2000,4000,4000};
        for(long delay:delays){android.os.Handler handler=retryHandler(helper);check(handler.delay==delay);int scheduled=handler.scheduled,attempts=flow.attempts;
            for(int i=0;i<1000;i++){helper.currentStacked(ruler,false);helper.configurationChanged();}
            check(flow.attempts==attempts&&handler.scheduled==scheduled);retryFlow(helper);check(flow.attempts==attempts+1);}
        // Changing the requested mode takes effect immediately, even during a long backoff.
        flow.alwaysFail=false;helper.configure(null);check(!flow.value&&flowStates(helper).isEmpty());
        check(retryHandler(helper).delayed==null);
        helper.configure(settings(1));helper.currentStacked(ruler,false);flow.writeFailures=1;helper.configure(null);
        check(flow.value);check(helper.diagnosticSummary().contains("flowPendingSync=0 flowPendingRestore=1"));
        helper.configure(settings(1));helper.currentStacked(ruler,false);check(flow.value);
        check(retryHandler(helper).delayed==null);helper.configure(null);check(!flow.value);
        // A later native change wins over the value captured before the failed restore.
        helper.configure(settings(1));helper.currentStacked(ruler,false);flow.writeFailures=1;helper.reset();
        ruler.currentIsStackedNotification=true;retryFlow(helper);check(flow.value&&flowStates(helper).isEmpty());
        // A live ruler can finish constructing after the first getter, with no lifecycle event.
        helper=helper(1);ruler=new Ruler();ruler._isStackedNotification=null;helper.currentStacked(ruler,false);
        Flow initialized=new Flow();ruler._isStackedNotification=initialized;retryFlow(helper);check(initialized.value);helper.configure(null);
        // Unsupported optional Flow capabilities create no retry or retained restoration obligation.
        helper=new NotificationNativeStack();helper.configure(settings(1));check(helper.resolve(nativeLoader(NoFlowRuler.class)));helper.hooksReady(true);
        NoFlowRuler noFlow=new NoFlowRuler();check(helper.currentStacked(noFlow,false));check(retryHandler(helper)==null);
        check(helper.diagnosticSummary().contains("flowFailures=0 flowLastFailure=none"));
        helper.configure(null);check(flowStates(helper).isEmpty());
        // Deterministic collection seam: removing all weak keys leaves the queued callback inert.
        helper=helper(1);ruler=new Ruler();flow=new FailingFlow();flow.alwaysFail=true;ruler._isStackedNotification=flow;helper.currentStacked(ruler,false);
        check(flowStates(helper) instanceof java.util.WeakHashMap);flowStates(helper).clear();int attempts=flow.attempts;
        retryFlow(helper);check(flow.attempts==attempts&&retryHandler(helper).delayed==null);
        Field task=NotificationNativeStack.class.getDeclaredField("flowRetryTask");task.setAccessible(true);
        Object callback=task.get(helper);int captures=0;for(Field capture:callback.getClass().getDeclaredFields())
            if(!java.lang.reflect.Modifier.isStatic(capture.getModifiers())){captures++;check(capture.getType()==java.lang.ref.WeakReference.class);}
        check(captures==1);
        // A collector may throw after its setter already took effect. Retrying verifies the
        // current value and must not emit a duplicate setter/collector notification.
        helper=helper(1);ruler=new Ruler();flow=new FailingFlow();ruler._isStackedNotification=flow;flow.failAfterWrite=true;
        helper.currentStacked(ruler,false);check(flow.value&&flow.attempts==1);retryFlow(helper);
        check(flow.value&&flow.attempts==1&&retryHandler(helper).delayed==null);helper.configure(null);
        // A setter can synchronously re-enter the getter and then disable the module.
        final NotificationNativeStack reentrant=helper(1);final Ruler reentrantRuler=new Ruler();FailingFlow reentrantFlow=new FailingFlow();
        reentrantRuler._isStackedNotification=reentrantFlow;reentrantFlow.onWrite=()->{
            check(reentrant.currentStacked(reentrantRuler,false));reentrant.configure(null);
        };
        reentrant.currentStacked(reentrantRuler,false);check(reentrantFlow.value&&reentrantFlow.attempts==1);
        reentrantFlow.onWrite=null;retryFlow(reentrant);check(!reentrantFlow.value&&flowStates(reentrant).isEmpty());
        // Two deadlines share one callback; the later failure is not retried early or starved.
        helper=helper(1);Ruler first=new Ruler(),second=new Ruler();FailingFlow firstFlow=new FailingFlow(),secondFlow=new FailingFlow();
        firstFlow.writeFailures=1;secondFlow.writeFailures=1;first._isStackedNotification=firstFlow;second._isStackedNotification=secondFlow;
        helper.currentStacked(first,false);android.os.SystemClock.uptime+=100;helper.currentStacked(second,false);
        android.os.Handler handler=retryHandler(helper);check(handler.scheduled==1);handler.delay-=100;retryFlow(helper);
        check(firstFlow.value&&!secondFlow.value&&secondFlow.attempts==1&&handler.delay==100);
        retryFlow(helper);check(secondFlow.value&&handler.delayed==null);helper.configure(null);
        check(!firstFlow.value&&!secondFlow.value&&flowStates(helper).isEmpty());
        // Fault injection at native-state access retains the same restoration obligation and
        // reports its own stage rather than mislabelling it as a Flow setter exception.
        helper=helper(1);ruler=new Ruler();flow=new FailingFlow();ruler._isStackedNotification=flow;helper.currentStacked(ruler,false);
        Field accessField=NotificationNativeStack.class.getDeclaredField("access");accessField.setAccessible(true);
        Object nativeAccess=accessField.get(helper);Field nativeCurrent=NotificationNativeStack.Access.class.getDeclaredField("nativeCurrent");
        nativeCurrent.setAccessible(true);Object original=nativeCurrent.get(nativeAccess);
        nativeCurrent.set(nativeAccess,NoFlowRuler.class.getDeclaredField("currentIsStackedNotification"));
        helper.configure(null);check(flow.value&&flowStates(helper).containsKey(ruler));
        check(helper.diagnosticSummary().contains("flowLastFailure=native_read:IllegalArgumentException"));
        nativeCurrent.set(nativeAccess,original);retryFlow(helper);check(!flow.value&&flowStates(helper).isEmpty());
    }
    public static final class GestureStack extends View {
        final Resources resources=new Resources();int updates;
        GestureStack(){super(new Context());resources.getDisplayMetrics().density=2;}
        @Override public Resources getResources(){return resources;}
        public void requestChildrenUpdate(){updates++;}
    }
    private static void managedRows()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;
        Ambient ambient=new Ambient();android.view.ViewGroup parent=new android.view.ViewGroup(new Context());
        List<Row> rows=new ArrayList<>();Row first=new Row(100,120),rear=new Row(232,120);rows.add(first);rows.add(rear);
        parent.addView(first);parent.addView(rear);
        check(!helper.manages(rear));helper.currentStacked(ruler,false);check(!helper.manages(rear));
        NotificationNativeStack.NativeAction update=()->{helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000);return null;};
        helper.withNativeUpdate(extension,ambient,update);check(helper.manages(first));check(helper.manages(rear));
        ambient.mShadeExpanded=false;check(!helper.manages(rear));ambient.mShadeExpanded=true;
        rear.child=true;check(!helper.manages(rear));rear.child=false;
        rear.mDismissed=true;check(!helper.manages(rear));rear.mDismissed=false;
        rear.changing=true;check(!helper.manages(rear));rear.changing=false;
        parent.removeView(rear);check(!helper.manages(rear));parent.addView(rear);
        rows.remove(rear);helper.withNativeUpdate(extension,ambient,update);check(!helper.manages(rear));
        rows.add(rear);helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));
        helper.withNativeUpdate(extension,ambient,false,update);check(!helper.manages(rear));
        helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));
        try{helper.withNativeUpdate(extension,ambient,()->{update.run();throw new IllegalStateException("native failure");});}
        catch(IllegalStateException expected){check(!helper.manages(rear));}
        helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));
        extension.current=new Object();check(!helper.manages(rear));
        extension.current=ruler;helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;check(!helper.manages(rear));
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        helper.configure(null);check(!helper.manages(rear));helper.configure(settings(1));check(!helper.manages(rear));
        helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));helper.hooksReady(false);check(!helper.manages(rear));
        helper.hooksReady(true);helper.withNativeUpdate(extension,ambient,update);check(helper.manages(rear));helper.reset();check(!helper.manages(rear));
    }
    private static android.view.MotionEvent gesture(int action,float y){return new android.view.MotionEvent(action,0,y,0);}
    private static void upwardGesture()throws Throwable {
        NotificationNativeStack helper=helper(1);GestureStack stack=new GestureStack();
        check(!helper.touch(stack,gesture(0,100)));check(!helper.touch(stack,gesture(2,300)));
        check(!helper.pulledOpen()&&stack.updates==0&&stack.layoutRequests==0); // Downward spring stays native.
        check(!helper.touch(stack,gesture(1,300)));check(!helper.touch(stack,gesture(2,0)));check(!helper.pulledOpen());
        helper.touch(stack,gesture(0,100));check(!helper.touch(stack,gesture(2,85)));check(!helper.pulledOpen());
        check(helper.touch(stack,gesture(2,84)));check(helper.pulledOpen()&&stack.updates==1&&stack.layoutRequests==1);
        check(!helper.touch(stack,gesture(2,20)));check(stack.updates==1); // One native recomputation, never per move.
        helper.reset();helper.hooksReady(true);check(!helper.pulledOpen());
        helper.touch(stack,gesture(0,100));helper.touch(stack,gesture(3,100));check(!helper.touch(stack,gesture(2,0)));
        helper.touch(stack,gesture(0,100));helper.touch(stack,gesture(5,100));check(!helper.touch(stack,gesture(2,0)));
        stack.canScrollUpForCheck=true;helper.touch(stack,gesture(0,100));check(!helper.touch(stack,gesture(2,0)));
        stack.canScrollUpForCheck=false;helper.touch(stack,gesture(0,100));helper.configure(null);
        check(!helper.touch(stack,gesture(2,0))&&!helper.pulledOpen());
    }
    /** User feedback: scrolling back did not fold the stack, and open/fold jumped instead of easing. */
    private static void foldBackAndTransition()throws Throwable {
        long clock=android.os.SystemClock.uptime;
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;
        Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        float[] border=new float[1];
        NotificationNativeStack.NativeAction update=()->{
            rows.get(0).target.y=100-ambient.scroll;rows.get(1).target.y=232-ambient.scroll;
            border[0]=helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000);return null;
        };
        // Without a known list there is nothing to drive frames: open/fold stay immediate.
        helper.withNativeUpdate(extension,ambient,true,update);equal(244,border[0]);
        ambient.scroll=30;helper.withNativeUpdate(extension,ambient,true,update);equal(1000,border[0]);check(helper.pulledOpen());
        ambient.scroll=4;helper.withNativeUpdate(extension,ambient,true,update);equal(1000,border[0]);check(helper.pulledOpen());
        ambient.scroll=0;helper.withNativeUpdate(extension,ambient,true,update);
        check(!helper.pulledOpen());equal(244,border[0]); // Scrolled back to the top: folded again.
        ambient.scroll=12;helper.withNativeUpdate(extension,ambient,true,update);check(helper.pulledOpen());equal(1000,border[0]);
        ambient.scroll=0;helper.withNativeUpdate(extension,ambient,true,update);check(!helper.pulledOpen());equal(244,border[0]);

        // A pull opened at the top (nothing scrolled) stays open at the top until pulled down.
        GestureStack stack=new GestureStack();
        helper.touch(stack,gesture(0,100));check(helper.touch(stack,gesture(2,80)));check(helper.pulledOpen());
        int updates=stack.updates;
        helper.withNativeUpdate(extension,ambient,true,update);equal(244,border[0]); // Eases from the folded border.
        check(stack.animationFrames.size()==1);
        helper.withNativeUpdate(extension,ambient,true,update);check(stack.animationFrames.size()==1); // One frame at a time.
        android.os.SystemClock.uptime=clock+160;
        helper.withNativeUpdate(extension,ambient,true,update);equal(244+(1000-244)*.875f,border[0]);
        stack.animationFrames.remove(0).run();check(stack.updates==updates+1);
        android.os.SystemClock.uptime=clock+400;
        helper.withNativeUpdate(extension,ambient,true,update);equal(1000,border[0]);check(stack.animationFrames.isEmpty());
        helper.withNativeUpdate(extension,ambient,true,update);equal(1000,border[0]);check(helper.pulledOpen());
        // Upward motion in an open stack is ordinary scrolling, not a fold.
        helper.touch(stack,gesture(0,100));check(!helper.touch(stack,gesture(2,40)));check(helper.pulledOpen());
        helper.touch(stack,gesture(1,40));
        // Not at the top: a downward drag is native scrolling.
        stack.canScrollUpForCheck=true;helper.touch(stack,gesture(0,100));check(!helper.touch(stack,gesture(2,160)));check(helper.pulledOpen());
        stack.canScrollUpForCheck=false;helper.touch(stack,gesture(1,160));
        helper.touch(stack,gesture(0,100));check(!helper.touch(stack,gesture(2,115)));check(helper.pulledOpen());
        check(helper.touch(stack,gesture(2,116)));check(!helper.pulledOpen());
        helper.withNativeUpdate(extension,ambient,true,update);equal(1000,border[0]);
        android.os.SystemClock.uptime=clock+400+400;
        helper.withNativeUpdate(extension,ambient,true,update);equal(244,border[0]);
        // Closing the shade or turning the stack off folds immediately, without a pending transition.
        helper.touch(stack,gesture(0,100));check(helper.touch(stack,gesture(2,60)));
        helper.configure(null);check(!helper.pulledOpen());
        helper.configure(settings(1));helper.withNativeUpdate(extension,ambient,true,update);equal(244,border[0]);
        android.os.SystemClock.uptime=clock;
    }
    private static void liveOrientationAndFlow()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();
        extension.current=ruler;Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
        rows.add(new Row(100,120));rows.add(new Row(232,120));Flow flow=(Flow)ruler._isStackedNotification;
        // No landscape opt-in in an old saved snapshot: only portrait is affected.
        check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==1);
        int resourceReads=ruler.context.reads;
        for(int i=0;i<1000;i++)check(helper.currentStacked(ruler,false));
        check(ruler.context.reads==resourceReads); // Cached handle, live configuration.
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        helper.configurationChanged();check(!flow.value&&flow.writes==2);
        check(!helper.currentStacked(ruler,false));check(helper.currentStacked(ruler,true));
        helper.withNativeUpdate(extension,ambient,()->{
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        Bundle optIn=settings(1);optIn.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        optIn.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        // Landscape clock/group switches cannot substitute the independent whole-stack opt-in.
        Bundle old=settings(1);old.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        old.putBoolean(NotificationGroupStack.MASTER,true);helper.configure(old);
        check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==3);
        helper.withNativeUpdate(extension,ambient,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        // A save that disables only landscape restores native collectors immediately.
        helper.configure(settings(1));check(!flow.value&&flow.writes==4);
        helper.configure(optIn);helper.currentStacked(ruler,false);check(flow.value);
        ruler.currentIsStackedNotification=true;helper.configure(settings(1));
        check(flow.value);check(helper.currentStacked(ruler,true)); // Native ON is preserved.
        ruler.currentIsStackedNotification=false;flow.value=false;
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        helper.configurationChanged();check(helper.currentStacked(ruler,false));check(flow.value);
        // Rotation without any clock callback is also enforced in both native calculation and getter.
        helper.withNativeUpdate(extension,ambient,()->{
            ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            check(!helper.currentStacked(ruler,false));check(!flow.value);return null;
        });
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        helper.currentStacked(ruler,false);check(flow.value);
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_UNDEFINED;
        helper.configurationChanged();check(!flow.value);check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);check(!helper.currentStacked(ruler,false)); // Unknown scene stays native.
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        Bundle masterOff=new Bundle();masterOff.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        helper.configure(masterOff);check(!helper.currentStacked(ruler,false));
        Bundle safe=settings(1);safe.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);check(!helper.currentStacked(ruler,false));
        helper.configure(optIn);helper.currentStacked(ruler,false);check(flow.value);
        helper.hooksReady(false);check(!flow.value);check(!helper.currentStacked(ruler,false));
    }
    private static void nativeSceneAndFlow()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();
        extension.current=ruler;Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
        rows.add(new Row(120,100));rows.add(new Row(232,100));
        // Actual native target update succeeds before any clock/state/fraction callback.
        helper.withNativeUpdate(extension,ambient,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;
        });
        Flow flow=(Flow)ruler._isStackedNotification;check(!flow.value);
        check(helper.currentStacked(ruler,false));check(flow.value&&flow.writes==1);
        int reads=flow.reads;
        for(int i=0;i<2000;i++)check(helper.currentStacked(ruler,false));
        check(flow.reads==reads&&flow.writes==1); // No stable-frame reflection/collector writes.
        ruler.currentIsStackedNotification=true;helper.configure(null);
        check(flow.value&&flow.writes==1); // Restore latest native state, not the old false.
        ruler.currentIsStackedNotification=false;
        helper.configure(settings(1));helper.currentStacked(ruler,false);helper.hooksReady(false);
        check(!flow.value&&flow.writes==2);
        helper.hooksReady(true);helper.currentStacked(ruler,false);check(flow.value);
        helper.reset();check(!flow.value);
        helper.hooksReady(true);ruler._isStackedNotification=null;
        helper.currentStacked(ruler,false); // Actual constructor reads before assigning its flow.
        Flow initialized=new Flow();ruler._isStackedNotification=initialized;
        helper.currentStacked(ruler,false);check(initialized.value&&initialized.writes==1);
        helper.configure(null);check(!initialized.value);
        extension.current=new Object();helper.configure(settings(1));
        helper.withNativeUpdate(extension,ambient,()->{
            equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            check(!helper.currentStacked(extension.current,false));return null;
        });
    }
    private static void nativeScrollAndDimensions()throws Throwable {
        for(int complete=1;complete<=5;complete++) {
            NotificationNativeStack helper=helper(complete);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;
            Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();
            float y=180;for(int i=0;i<8;i++){rows.add(new Row(y,100+i*8));y+=100+i*8+12;}
            final float expected=rows.get(complete-1).target.y+rows.get(complete-1).target.height+24;
            for(int scroll=0;scroll<2000;scroll+=7) {
                ambient.scroll=scroll;
                float nativeY=180;for(Row row:rows){row.target.y=nativeY-scroll;nativeY+=row.target.height+12;}
                helper.withNativeUpdate(extension,ambient,true,()->{
                    check(helper.currentStacked(ruler,false));
                    equal(ambient.scroll == 0 ? expected : 4000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,4000));
                    equal(ambient.scroll == 0 ? Math.min(600,expected) : 600,helper.bottomBorder(ruler.stackAlgorithmManager,rows,600));
                    for(int i=0;i<rows.size();i++) {
                        Row row=rows.get(i);equal(100+i*8,row.target.height);
                        equal(1f,row.getScaleY());
                        check(row.getClipBounds()==null);
                    }
                    return null;
                });
                check(helper.currentStacked(ruler,false));equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,4000));
            }
        }
        NotificationNativeStack helper=helper(2);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        Row first=new Row(200,100),summary=new Row(312,420),child=new Row(0,80);child.child=true;
        List<Row> nested=new ArrayList<>();nested.add(first);nested.add(child);nested.add(summary);nested.add(new Row(744,100));
        helper.withNativeUpdate(extension,ambient,true,()->{
            equal(756,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));
            equal(420,summary.target.height);equal(0,child.target.y);
            summary.changing=true;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.changing=false;
            summary.mIsUserSwipingToExpandRow=true;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.mIsUserSwipingToExpandRow=false;
            summary.mDismissed=true;equal(868,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.mDismissed=false;
            summary.target.height=0;equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));summary.target.height=420;
            ruler.stackAlgorithmManager.dynamicStackAlgorithm.dimens.scale=Float.NaN;
            equal(4000,helper.bottomBorder(ruler.stackAlgorithmManager,nested,4000));return null;
        });
    }
    private static void scopesAndFallback()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        helper.withNativeUpdate(extension,ambient,true,()->{
            equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
            equal(1000,helper.bottomBorder(new Manager(),rows,1000));
            check(helper.currentStacked(new Ruler(),false));check(helper.currentStacked(new Ruler(),true));
            check(!helper.currentStacked(new Object(),false));
            helper.withNativeUpdate(extension,ambient,false,()->{check(helper.currentStacked(ruler,false));equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
            check(helper.currentStacked(ruler,false));
            Extension keyguard=new Extension();keyguard.current=new Object();
            helper.withNativeUpdate(keyguard,ambient,true,()->{check(!helper.currentStacked(keyguard.current,false));check(helper.currentStacked(ruler,false));return null;});
            check(helper.currentStacked(ruler,false));return null;
        });
        try{helper.withNativeUpdate(extension,ambient,true,()->{throw new IllegalStateException("native failed");});throw new AssertionError("swallowed");}
        catch(IllegalStateException expected){check("native failed".equals(expected.getMessage()));}
        check(helper.currentStacked(ruler,false));
        ambient.scroll=-1;helper.withNativeUpdate(extension,ambient,true,()->{check(helper.currentStacked(ruler,false));equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        check(!helper.resolve(new ClassLoader(null){}));check(helper.updateMethod()==null&&helper.currentMethod()==null&&helper.applyMethod()==null);
        equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));
    }
    private static void directionDependencies()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;Ambient ambient=new Ambient();
        List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        for(boolean group:new boolean[]{false,true})for(boolean portrait:new boolean[]{false,true})for(boolean landscape:new boolean[]{false,true}){
            Bundle bundle=settings(1);bundle.putBoolean(NotificationGroupStack.MASTER,group);
            bundle.putBoolean(NotificationBigClockSettings.MASTER,portrait);bundle.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape);
            helper.configure(bundle);helper.withNativeUpdate(extension,ambient,true,()->{equal(portrait?244:1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        for(float count:new float[]{0,-20,Float.NaN,Float.POSITIVE_INFINITY,1}){
            helper.configure(settings(count));helper.withNativeUpdate(extension,ambient,true,()->{equal(244,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        for(float count:new float[]{5,100000,Float.MAX_VALUE}){
            helper.configure(settings(count));helper.withNativeUpdate(extension,ambient,true,()->{equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        }
        Bundle safe=settings(1);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        check(!helper.enabled());check(!helper.currentStacked(ruler,false));helper.withNativeUpdate(extension,ambient,true,()->{equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        helper.configure(null);check(!helper.enabled());
        helper.configure(settings(1));helper.hooksReady(false);check(!helper.enabled());check(!helper.currentStacked(ruler,false));
        helper.configure(settings(1));check(!helper.enabled()); // Partial hook failure cannot re-enable by saving.
        helper.hooksReady(true);check(helper.enabled());helper.reset();check(!helper.enabled());
    }
    private static void savedClockDependency()throws Throwable {
        NotificationNativeStack helper=helper(1);Ruler ruler=new Ruler();Extension extension=new Extension();extension.current=ruler;
        Ambient ambient=new Ambient();List<Row> rows=new ArrayList<>();rows.add(new Row(100,120));rows.add(new Row(232,120));
        Flow flow=(Flow)ruler._isStackedNotification;
        // An old or imported master=true snapshot cannot enable unsupported clock-less stacking.
        Bundle imported=new Bundle();imported.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);
        imported.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);imported.putFloat(NotificationBigClockSettings.VISIBLE_COUNT,4f);
        helper.configure(imported);check(!helper.enabled());check(!helper.currentStacked(ruler,false));check(!flow.value);
        helper.withNativeUpdate(extension,ambient,()->{equal(1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
        check(imported.getBoolean(NotificationBigClockSettings.STACK_ENABLED,false));check(((Number)imported.get(NotificationBigClockSettings.VISIBLE_COUNT)).floatValue()==4f);
        for(boolean master:new boolean[]{false,true})for(boolean portrait:new boolean[]{false,true})
                for(boolean landscape:new boolean[]{false,true})for(boolean landscapeStack:new boolean[]{false,true}) {
            Bundle saved=new Bundle();saved.putBoolean(NotificationBigClockSettings.STACK_ENABLED,master);
            saved.putBoolean(NotificationBigClockSettings.MASTER,portrait);saved.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape);
            saved.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,landscapeStack);
            helper.configure(saved);
            for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_UNDEFINED}) {
                ruler.context.resources.getConfiguration().orientation=orientation;helper.configurationChanged();
                boolean active=master&&(orientation==Configuration.ORIENTATION_PORTRAIT&&portrait
                        ||orientation==Configuration.ORIENTATION_LANDSCAPE&&landscape&&landscapeStack);
                check(helper.currentStacked(ruler,false)==active);check(flow.value==active);
                helper.withNativeUpdate(extension,ambient,()->{equal(active?244:1000,helper.bottomBorder(ruler.stackAlgorithmManager,rows,1000));return null;});
            }
            check(saved.getBoolean(NotificationBigClockSettings.STACK_ENABLED,false)==master);
        }
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        Bundle active=settings(1);helper.configure(active);check(helper.currentStacked(ruler,false));check(flow.value);
        active.putBoolean(NotificationBigClockSettings.MASTER,false);helper.configure(active);
        check(!flow.value);check(!helper.currentStacked(ruler,false)); // Closing the clock restores native collectors immediately.
        check(active.getBoolean(NotificationBigClockSettings.STACK_ENABLED,false));
        active.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        active.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);helper.configure(active);
        check(!helper.currentStacked(ruler,false));
        ruler.context.resources.getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;helper.configurationChanged();
        check(helper.currentStacked(ruler,false));check(flow.value);
        active.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,false);helper.configure(active);
        check(!flow.value);check(!helper.currentStacked(ruler,false));
        ruler.currentIsStackedNotification=true;check(helper.currentStacked(ruler,true)); // OEM's own value remains its responsibility.
    }
}
