// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Parameters for the real ColorOS shade ruler. Never projects notification targets, clips,
 * heights, outlines or draw matrices. Scrolling, scale and group expansion remain native.
 * The verified entry is OplusStackedNotificationExImpl.updateStackedNotification; keyguard
 * rulers and the manager's other phases must not inherit this shade-only geometry scope.
 * The real shade ruler's enable getter is consistent for target calculation and clipping.
 */
public final class NotificationNativeStack {
    public static final String EXTENSION="com.oplus.systemui.notification.stackednotification.OplusStackedNotificationExImpl";
    public static final String SHADE_RULER="com.oplus.systemui.notification.stackednotification.ruler.ShadeNotificationStackedRuler";
    public static final String MANAGER="com.oplus.systemui.notification.lockscreen.stack.algorithm.StackAlgorithmManager";
    public static final String ALGORITHM="com.oplus.systemui.notification.lockscreen.stack.algorithm.DynamicStackAlgorithm";
    public static final String AMBIENT="com.android.systemui.statusbar.notification.stack.AmbientState";
    public static final String ROW="com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    public static final String EXPANDABLE="com.android.systemui.statusbar.notification.row.ExpandableView";
    private boolean enabled;
    private boolean landscapeEnabled;
    private boolean portraitClockEnabled,landscapeClockEnabled;
    private boolean hooksReady;
    private int completeCount=1;
    private Access access;
    // Primitive-only structural counters; no notification text/package or per-frame log.
    private int updateCalls,shadeCalls,currentCalls,applyCalls,borderChanges,lastRows,lastEligibleRows;
    private String lastStage="unresolved";
    /** Native upward scrolling or an upward pull opened the stack: every card is complete. Scrolling
     * back to the top, a downward pull at the top or closing the shade folds it again. */
    private boolean pulledOpen;
    /** The open stack was scrolled away from the top, so returning to the top folds it. */
    private boolean scrolledOpen;
    private float lastNativeBorder,lastAppliedBorder;
    /** Open/fold eases the border instead of jumping; frames come from the list's own update. */
    private static final long TRANSITION_MILLIS=320L;
    private float shownBorder=Float.NaN,transitionFrom=Float.NaN;
    private long transitionStart=-1L;
    private boolean framePosted;
    private java.lang.ref.WeakReference<View> stackView=new java.lang.ref.WeakReference<>(null);
    private final ThreadLocal<Scope> scenes=new ThreadLocal<>();
    private final Map<Object,FlowState> observedRulers=new WeakHashMap<>();
    private static final long FLOW_RETRY_MIN=250L,FLOW_RETRY_MAX=4000L;
    private android.os.Handler flowRetryHandler;
    private Runnable flowRetryTask;
    private long flowRetryAt=-1L;
    private boolean retryingFlows;
    private long flowFailures;
    private String lastFlowFailure="none";
    // The real ruler owns a Context. Resolve Resources once; its live Configuration follows
    // rotation without relying on the big-clock callback order.
    private final Map<Object,Resources> rulerResources=new WeakHashMap<>();
    private final Map<Object,ManagedScene> managedScenes=new WeakHashMap<>();
    private final Map<View,ManagedRow> managedRows=new WeakHashMap<>();
    private static final class ManagedScene {
        final java.lang.ref.WeakReference<Object> extension,ruler,ambient;
        long generation;boolean active;
        ManagedScene(Object extension,Object ruler,Object ambient){this.extension=new java.lang.ref.WeakReference<>(extension);
            this.ruler=new java.lang.ref.WeakReference<>(ruler);this.ambient=new java.lang.ref.WeakReference<>(ambient);}
    }
    private static final class ManagedRow {
        java.lang.ref.WeakReference<android.view.ViewParent> parent;
        final ManagedScene scene;long generation;
        ManagedRow(View view,ManagedScene scene){parent=new java.lang.ref.WeakReference<>(view.getParent());this.scene=scene;generation=scene.generation;}
    }
    private static final class FlowState {
        // The old contract must remain usable after reset/resolve replaces the current Access.
        // Neither this value nor the retry callback may strongly retain the weak-map ruler.
        final Access nativeAccess;
        boolean nativeValue,applied,restoring,waitingForFlow,syncing;
        long nextAttempt,retryDelay,revision;
        FlowState(boolean nativeValue,Access nativeAccess){this.nativeValue=nativeValue;this.nativeAccess=nativeAccess;}
    }
    private static final class FlowRetry implements Runnable {
        final java.lang.ref.WeakReference<NotificationNativeStack> owner;
        FlowRetry(NotificationNativeStack owner){this.owner=new java.lang.ref.WeakReference<>(owner);}
        @Override public void run(){NotificationNativeStack stack=owner.get();if(stack!=null)stack.retryPendingFlows();}
    }

    public interface NativeAction { Object run() throws Throwable; }
    private static final class Scope {
        final Object ruler,manager,extension,ambient;
        final int scroll;
        Scope(Object ruler,Object manager,int scroll,Object extension,Object ambient){this.ruler=ruler;this.manager=manager;this.scroll=scroll;this.extension=extension;this.ambient=ambient;}
    }
    /** Exact real-ROM signatures are resolved together; a partial match is not supported. */
    static final class Access {
        final Class<?> extension,ruler,manager,ambient,row;
        final Method update,current,apply,currentRuler,scroll,state,y,stableHeight,child;
        final Method removed,dismissed,guts,userLocked,expanding,groupChanging,dimens,scaleBase,translationBase;
        final Field managerField,algorithmField,nativeCurrent,flow,dismissedFlag,swiping,context,shadeExpanded;
        final Method flowValue,flowSet;
        Access(Class<?> extension,Class<?> ruler,Class<?> manager,Class<?> algorithm,
                Class<?> ambient,Class<?> row,Class<?> expandable)throws ReflectiveOperationException {
            this.extension=extension;this.ruler=ruler;this.manager=manager;this.ambient=ambient;this.row=row;
            update=exact(extension,"updateStackedNotification",List.class,ambient,Float.TYPE);
            current=exact(ruler,"getCurrentIsStackedNotification");
            apply=exact(manager,"applyDynamic",List.class,Float.TYPE);
            currentRuler=exact(extension,"getCurrentStackRuler");
            context=field(ruler,"context");
            if(context==null||!Context.class.isAssignableFrom(context.getType()))
                throw new NoSuchFieldException("Native shade ruler context");
            managerField=field(ruler,"stackAlgorithmManager");
            if(managerField==null||managerField.getType()!=manager||current.getReturnType()!=Boolean.TYPE)
                throw new NoSuchFieldException("Native shade ruler manager/current signature");
            nativeCurrent=field(ruler,"currentIsStackedNotification");
            flow=field(ruler,"_isStackedNotification");
            Method getValue=null,setValue=null;
            if(flow!=null)try {
                // getValue is inherited from StateFlow, not declared on MutableStateFlow.
                getValue=flow.getType().getMethod("getValue");
                setValue=flow.getType().getMethod("setValue",Object.class);
                getValue.setAccessible(true);setValue.setAccessible(true);
            }catch(ReflectiveOperationException|RuntimeException unsupported){getValue=null;setValue=null;}
            flowValue=getValue;flowSet=setValue;
            scroll=exact(ambient,"getScrollY");
            // Optional: when it is missing, a pulled-open stack closes again only when the module stack is switched off.
            shadeExpanded=booleanField(ambient,"mShadeExpanded");
            state=exact(expandable,"getViewState");
            y=exact(state.getReturnType(),"getYTranslation");
            stableHeight=exact(algorithm,"stableHeightOf",expandable);
            algorithmField=field(manager,"dynamicStackAlgorithm");
            if(algorithmField==null||algorithmField.getType()!=algorithm)
                throw new NoSuchFieldException("Native dynamic stack algorithm");
            dimens=exact(algorithm,"currentDynamicStackDimens");
            scaleBase=exact(dimens.getReturnType(),"getScaleDampingBaseHeight");
            translationBase=exact(dimens.getReturnType(),"getTranslationDampingBaseHeight");
            if(!Modifier.isStatic(stableHeight.getModifiers())||stableHeight.getReturnType()!=Float.TYPE
                    ||scroll.getReturnType()!=Integer.TYPE||y.getReturnType()!=Float.TYPE)
                throw new NoSuchMethodException("Native shade geometry signature");
            child=exact(row,"isChildInGroup");
            removed=exact(row,"isRemoved");
            // Current ColorOS removed these old AOSP getters: SystemUI reads the actual
            // inherited mDismissed and mIsUserSwipingToExpandRow fields directly.
            dismissedFlag=booleanField(row,"mDismissed");dismissed=optional(row,"isDismissed");
            swiping=booleanField(row,"mIsUserSwipingToExpandRow");userLocked=optional(row,"isUserLocked");
            if(dismissedFlag==null&&dismissed==null||swiping==null&&userLocked==null)
                throw new NoSuchFieldException("Native shade row interaction flags");
            guts=exact(row,"areGutsExposed");
            expanding=exact(row,"isExpandAnimationRunning");groupChanging=exact(row,"isGroupExpansionChanging");
        }
        boolean changing(Object row)throws ReflectiveOperationException {
            return yes(guts,row)||yes(swiping,row)||yes(userLocked,row)||yes(expanding,row)||yes(groupChanging,row);
        }
        boolean dismissed(Object row)throws ReflectiveOperationException {
            return dismissedFlag!=null?dismissedFlag.getBoolean(row):yes(dismissed,row);
        }
    }
    public void configure(Bundle source) {
        configure(source,NotificationBigClockSettings.enabled(source,false),NotificationBigClockSettings.enabled(source,true));
    }
    /** Standalone notification modules own no clock; both orientation policies are independent. */
    public void configureIndependent(Bundle source) {
        configure(source,true,true);
    }
    private void configure(Bundle source,boolean portraitAllowed,boolean landscapeAllowed) {
        portraitClockEnabled=portraitAllowed;
        landscapeClockEnabled=landscapeAllowed;
        landscapeEnabled=source!=null&&Boolean.TRUE.equals(source.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        enabled=source!=null&&!SafetyMode.enabled(source)
                &&Boolean.TRUE.equals(source.get(NotificationBigClockSettings.STACK_ENABLED))
                &&(portraitClockEnabled||landscapeClockEnabled&&landscapeEnabled);
        Object value=source==null?null:source.get(NotificationBigClockSettings.VISIBLE_COUNT);
        float count=value instanceof Number?((Number)value).floatValue():1f;
        // Standalone settings use exact integers; converting them through float would round large counts.
        completeCount=value instanceof Integer?Math.max(1,(Integer)value)
                :!finite(count)?1:Math.max(1,count>=Integer.MAX_VALUE?Integer.MAX_VALUE:Math.round(count));
        configurationChanged();
    }
    public boolean enabled(){return enabled&&hooksReady&&access!=null&&!ModuleLifecycle.removed();}
    /** Whether this actual row belongs to the last verified native shade-stack update. */
    public boolean manages(View summary){
        ManagedRow row=summary==null?null:managedRows.get(summary);Access nativeAccess=access;
        if(row==null||nativeAccess==null||!enabled()||!row.scene.active||row.generation!=row.scene.generation
                ||!summary.isAttachedToWindow()||summary.getParent()==null||summary.getParent()!=row.parent.get()
                ||summary.getVisibility()==View.GONE||!nativeAccess.row.isInstance(summary))return false;
        Object extension=row.scene.extension.get(),ruler=row.scene.ruler.get(),ambient=row.scene.ambient.get();
        if(extension==null||ambient==null||!enabledFor(ruler))return false;
        try{return nativeAccess.currentRuler.invoke(extension)==ruler
                &&(nativeAccess.shadeExpanded==null||nativeAccess.shadeExpanded.getBoolean(ambient))
                &&!yes(nativeAccess.child,summary)&&!yes(nativeAccess.removed,summary)
                &&!nativeAccess.dismissed(summary)&&!nativeAccess.changing(summary);}
        catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    private void clearManaged(){managedScenes.clear();managedRows.clear();}
    /** Read only on an explicit diagnostic/probe. Counting never allocates a frame log. */
    public String diagnosticSummary(){
        int pendingSync=0,pendingRestore=0;
        for(FlowState state:observedRulers.values())if(!state.applied){if(state.restoring)pendingRestore++;else pendingSync++;}
        return "whole requested="+enabled+" landscape="+landscapeEnabled+" ready="+hooksReady+" resolved="+(access!=null)
            +" portraitClock="+portraitClockEnabled+" landscapeClock="+landscapeClockEnabled
            +" update="+updateCalls+" shade="+shadeCalls+" current="+currentCalls+" apply="+applyCalls
            +" changed="+borderChanges+" rows="+lastRows+" eligible="+lastEligibleRows+" count="+completeCount+" native="+lastNativeBorder
            +" chosen="+lastAppliedBorder+" stage="+lastStage+" flowPendingSync="+pendingSync+" flowPendingRestore="+pendingRestore
            +" flowFailures="+flowFailures+" flowLastFailure="+lastFlowFailure
            +" flowRetryIn="+(flowRetryAt<0L?-1L:Math.max(0L,flowRetryAt-android.os.SystemClock.uptimeMillis()));}
    /** All three native callbacks must be installed before any saved request becomes active. */
    public void hooksReady(boolean ready){hooksReady=ready&&access!=null;if(!hooksReady){scenes.remove();clearManaged();restoreFlows();}}
    public boolean resolve(ClassLoader loader) {
        restoreFlows();
        hooksReady=false;access=null;scenes.remove();rulerResources.clear();clearManaged();
        try {
            access=new Access(loader.loadClass(EXTENSION),loader.loadClass(SHADE_RULER),loader.loadClass(MANAGER),
                    loader.loadClass(ALGORITHM),loader.loadClass(AMBIENT),loader.loadClass(ROW),loader.loadClass(EXPANDABLE));
            return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unknownRom){
            ModuleDiagnostics.error("native-stack","Native shade signature unavailable; original notification layout retained",unknownRom);
            return false;
        }
    }
    public Method updateMethod(){return access==null?null:access.update;}
    public Method currentMethod(){return access==null?null:access.current;}
    public Method applyMethod(){return access==null?null:access.apply;}

    /** The native active ruler already includes actual lockscreen-transition state. Clock
     * callbacks can run later (or never when that feature is off), so cannot gate this call. */
    public Object withNativeUpdate(Object extension,Object ambient,NativeAction nativeUpdate)throws Throwable {
        return withNativeUpdate(extension,ambient,true,nativeUpdate);
    }

    /** Runs synchronously around the verified native target-calculation call, never apply/draw. */
    public Object withNativeUpdate(Object extension,Object ambient,boolean notificationPage,
            NativeAction nativeUpdate)throws Throwable {
        updateCalls++;
        Scope previous=scenes.get(),next=null;
        ManagedScene old=managedScenes.get(extension);if(old!=null)old.active=false;
        if(enabled()&&notificationPage&&access!=null&&access.extension.isInstance(extension)
                &&access.ambient.isInstance(ambient))try {
            Object ruler=access.currentRuler.invoke(extension);
            if(access.ruler.isInstance(ruler)&&enabledFor(ruler)) {
                Object manager=access.managerField.get(ruler);
                int scroll=((Number)access.scroll.invoke(ambient)).intValue();
                // The shade closing ends a pulled-open stack; the next session starts folded again.
                if(access.shadeExpanded!=null&&!access.shadeExpanded.getBoolean(ambient))foldNow();
                if(access.manager.isInstance(manager)&&scroll>=0){next=new Scope(ruler,manager,scroll,extension,ambient);shadeCalls++;lastStage="shade-scope";}
                else lastStage="native-manager-or-scroll";
            }else if(access.ruler.isInstance(ruler)) {
                restoreFlow(ruler);lastStage="native-orientation";
            }else lastStage="native-keyguard-ruler";
        }catch(ReflectiveOperationException|RuntimeException unknownState){lastStage="scene-reflection";}
        else lastStage=enabled()?"native-page-or-type":"disabled-or-hooks";
        // An unrelated nested native scene must never inherit the outer shade parameters.
        if(next==null)scenes.remove();else scenes.set(next);
        boolean completed=false;
        try{Object result=nativeUpdate.run();completed=true;return result;}
        finally{if(!completed){ManagedScene failed=managedScenes.get(extension);if(failed!=null)failed.active=false;}
            if(previous==null)scenes.remove();else scenes.set(previous);}
    }
    /** Native clipping reads this getter after target calculation has left its scope.
     * One stable value is required for both phases; keyguard remains a different class. */
    public boolean currentStacked(Object ruler,boolean nativeValue) {
        currentCalls++;
        Access nativeAccess=access;
        FlowState state=observedRulers.get(ruler);
        boolean active=nativeAccess!=null&&nativeAccess.ruler.isInstance(ruler)&&enabledFor(ruler);
        if(active&&state==null){state=new FlowState(nativeValue,nativeAccess);observedRulers.put(ruler,state);}
        boolean result=active||nativeValue;
        // Native collectors drive appearance/clipping separately from target calculation.
        // Late enable must notify that same native StateFlow, and not just override a getter.
        if(state!=null) {
            if(state.nativeValue!=nativeValue){state.nativeValue=nativeValue;state.applied=false;}
            if(!active||!state.applied||state.restoring)attemptFlow(ruler,state,!active,true);
        }
        return result;
    }
    /** 1: synchronized (or no optional capability); 0: constructor not ready; -1: retryable failure. */
    private int syncFlow(Object ruler,boolean value,Access nativeAccess) {
        if(nativeAccess.flow==null||nativeAccess.flowValue==null||nativeAccess.flowSet==null)return 1;
        String stage="flow_access";
        try {
            Object flow=nativeAccess.flow.get(ruler);
            if(flow==null)return 0; // Getter can run during the ruler's constructor.
            stage="flow_read";
            if(!Boolean.valueOf(value).equals(nativeAccess.flowValue.invoke(flow))){stage="flow_write";
                nativeAccess.flowSet.invoke(flow,Boolean.valueOf(value));
            }
            return 1;
        }catch(ReflectiveOperationException|RuntimeException unavailable){recordFlowFailure(stage,unavailable);return -1;}
    }
    private void recordFlowFailure(String stage,Throwable failure) {
        if(failure instanceof java.lang.reflect.InvocationTargetException&&failure.getCause()!=null)failure=failure.getCause();
        String reason=failure.getClass().getName();reason=reason.substring(reason.lastIndexOf('.')+1);
        if(reason.length()>64)reason=reason.substring(0,64);
        flowFailures++;lastFlowFailure=stage+":"+reason; // Never retain exception/message or notification content.
    }
    private void restoreFlows() {
        for(Object ruler:new ArrayList<>(observedRulers.keySet()))restoreFlow(ruler);
        scheduleFlowRetry();
    }
    private void restoreFlow(Object ruler) {
        FlowState state=observedRulers.get(ruler);if(state!=null)attemptFlow(ruler,state,true,false);
    }
    private void attemptFlow(Object ruler,FlowState state,boolean restoring,boolean getter) {
        if(state.restoring!=restoring){state.restoring=restoring;state.applied=false;state.waitingForFlow=false;
            state.nextAttempt=0L;state.retryDelay=0L;state.revision++;}
        if(state.syncing)return; // A collector can synchronously enter the hooked getter again.
        long now=android.os.SystemClock.uptimeMillis();
        // A constructor's next getter may observe its newly assigned Flow immediately. Actual
        // invocation failures always wait, including repeated configuration/getter callbacks.
        if(now<state.nextAttempt&&!(getter&&state.waitingForFlow))return;
        long revision=state.revision;int result=-1;state.syncing=true;
        try {
            boolean value=true;Access nativeAccess=state.nativeAccess;
            if(restoring){value=state.nativeValue;
                if(nativeAccess.nativeCurrent!=null&&nativeAccess.nativeCurrent.getType()==Boolean.TYPE)
                    value=nativeAccess.nativeCurrent.getBoolean(ruler);}
            result=syncFlow(ruler,value,nativeAccess);
        }catch(ReflectiveOperationException|RuntimeException unavailable){recordFlowFailure("native_read",unavailable);}
        finally{state.syncing=false;}
        if(revision!=state.revision){state.applied=false;state.nextAttempt=now+FLOW_RETRY_MIN;}
        else if(result==1){
            state.applied=true;state.waitingForFlow=false;state.nextAttempt=0L;state.retryDelay=0L;
            if(restoring&&observedRulers.get(ruler)==state)observedRulers.remove(ruler);
        }else{
            state.applied=false;state.waitingForFlow=result==0;
            state.retryDelay=result==0?FLOW_RETRY_MIN:Math.min(FLOW_RETRY_MAX,Math.max(FLOW_RETRY_MIN,state.retryDelay*2L));
            state.nextAttempt=now+state.retryDelay;
        }
        scheduleFlowRetry();
    }
    /** One coalesced, bounded-backoff retry, including after hooks have been removed. */
    private void scheduleFlowRetry() {
        if(retryingFlows)return;
        long earliest=Long.MAX_VALUE;
        for(FlowState state:observedRulers.values())if(!state.applied&&!state.syncing)earliest=Math.min(earliest,state.nextAttempt);
        if(earliest==Long.MAX_VALUE){if(flowRetryHandler!=null&&flowRetryAt>=0L)flowRetryHandler.removeCallbacks(flowRetryTask);flowRetryAt=-1L;return;}
        if(flowRetryAt>=0L&&flowRetryAt<=earliest)return;
        if(flowRetryHandler==null){flowRetryHandler=new android.os.Handler(android.os.Looper.getMainLooper());flowRetryTask=new FlowRetry(this);}
        if(flowRetryAt>=0L)flowRetryHandler.removeCallbacks(flowRetryTask);
        flowRetryAt=earliest;
        if(!flowRetryHandler.postDelayed(flowRetryTask,Math.max(0L,earliest-android.os.SystemClock.uptimeMillis())))flowRetryAt=-1L;
    }
    private void retryPendingFlows() {
        flowRetryAt=-1L;retryingFlows=true;
        try{for(Object ruler:new ArrayList<>(observedRulers.keySet())){
            FlowState state=observedRulers.get(ruler);
            if(state!=null&&!state.applied)attemptFlow(ruler,state,!enabledFor(ruler),false);
        }}finally{retryingFlows=false;scheduleFlowRetry();}
    }
    /** Called after native configuration changes and on every saved-setting change. Native
     * collectors must not retain the module's enabled value when landscape is opted out. */
    public void configurationChanged() {
        clearManaged();
        Scope scene=scenes.get();
        if(scene!=null&&!enabledFor(scene.ruler))scenes.remove();
        if(!enabled()){scenes.remove();restoreFlows();foldNow();touchDownY=Float.NaN;return;}
        for(Object ruler:new ArrayList<>(observedRulers.keySet()))
            if(!enabledFor(ruler))restoreFlow(ruler);
    }
    private boolean enabledFor(Object ruler) {
        Access nativeAccess=access;
        if(!enabled()||nativeAccess==null||!nativeAccess.ruler.isInstance(ruler))return false;
        try {
            Resources resources=rulerResources.get(ruler);
            if(resources==null) {
                Object owner=nativeAccess.context.get(ruler);
                if(!(owner instanceof Context))return false;
                resources=((Context)owner).getResources();if(resources==null)return false;
                rulerResources.put(ruler,resources);
            }
            int orientation=resources.getConfiguration().orientation;
            return orientation==Configuration.ORIENTATION_PORTRAIT&&portraitClockEnabled
                    ||orientation==Configuration.ORIENTATION_LANDSCAPE&&landscapeEnabled&&landscapeClockEnabled;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    /**
     * Move the native stack's fixed border, not each row. Adding actual native scroll recovers
     * the unscrolled location: using only the moving nth row would keep it folded forever.
     * Expanded groups retain their native full height/header and remain a single top-level row.
     */
    public float bottomBorder(Object manager,List<?> nativeViews,float nativeBorder) {
        applyCalls++;lastNativeBorder=nativeBorder;lastAppliedBorder=nativeBorder;
        Scope scene=scenes.get();Access nativeAccess=access;
        if(scene==null||!enabledFor(scene.ruler)||scene.manager!=manager||nativeAccess==null
                ||nativeViews==null||!finite(nativeBorder)||nativeBorder<=0f){lastStage="apply-without-shade-scope";return nativeBorder;}
        // Every return below reports what the shade actually shows, so a transition starts from it.
        shownBorder=nativeBorder;
        lastRows=nativeViews.size();
        lastEligibleRows=0;
        int found=0;float chosen=Float.NaN;
        try {
            Object algorithm=nativeAccess.algorithmField.get(manager);
            if(algorithm==null)return nativeBorder;
            Object dimensions=nativeAccess.dimens.invoke(algorithm);
            if(dimensions==null)return nativeBorder;
            float scaleBase=((Number)nativeAccess.scaleBase.invoke(dimensions)).floatValue();
            float translationBase=((Number)nativeAccess.translationBase.invoke(dimensions)).floatValue();
            if(!finite(scaleBase)||!finite(translationBase)||scaleBase<0f||translationBase<0f)return nativeBorder;
            for(Object item:nativeViews) {
                boolean row=nativeAccess.row.isInstance(item);
                // The fluid cloud's cards in the shade list count as notifications too (user feedback code161).
                if(!row&&!fluidCard(item))continue;
                if(!(item instanceof View)||((View)item).getVisibility()==View.GONE)continue;
                if(row&&(yes(nativeAccess.child,item)||yes(nativeAccess.removed,item)||nativeAccess.dismissed(item)))continue;
                // Native gestures/expanded group spring must receive its original physical bounds.
                if(row&&nativeAccess.changing(item)){lastStage="native-row-interaction";return nativeBorder;}
                Object target=nativeAccess.state.invoke(item);
                if(target==null){lastStage="missing-row-target";return nativeBorder;}
                float y=((Number)nativeAccess.y.invoke(target)).floatValue();
                float height=((Number)nativeAccess.stableHeight.invoke(null,item)).floatValue();
                if(!finite(y)||!finite(height)||height<=0f){lastStage="pending-row-height";return nativeBorder;}
                chosen=y+scene.scroll+height;
                if(!finite(chosen))return nativeBorder;
                lastEligibleRows=++found;if(found>=completeCount)break;
            }
            // Native shrink begins before bottomBorder by these real-ROM damping margins.
            // Keeping them in the input border makes the requested complete cards genuinely full.
            chosen+=Math.max(scaleBase,translationBase);
        }catch(ReflectiveOperationException|RuntimeException unknownGeometry){lastStage="border-reflection";return nativeBorder;}
        // If all notifications fit, do not manufacture a tail or change native shelf constraints.
        if(found<completeCount||!finite(chosen)||chosen<=0f){lastStage="insufficient-full-rows";return nativeBorder;}
        // Normal upward native scrolling must reveal every notification. Scrolling back to the
        // top folds the stack again, so it never stays open just because it was once scrolled.
        if(scene.scroll>0){setOpen(true);scrolledOpen=true;}
        else if(pulledOpen&&scrolledOpen)setOpen(false);
        float target=pulledOpen?nativeBorder:Math.min(nativeBorder,chosen);
        lastAppliedBorder=transition(target);shownBorder=lastAppliedBorder;
        if(pulledOpen)lastStage="pulled-open";
        else if(lastAppliedBorder<nativeBorder){borderChanges++;lastStage="native-border-applied";}
        else lastStage="native-border-already-limited";
        recordManaged(scene,nativeViews,nativeAccess);
        return lastAppliedBorder;
    }
    /** Exact native list membership, not a global setting or a hierarchy search. */
    private void recordManaged(Scope scene,List<?> rows,Access nativeAccess){
        ManagedScene owner=managedScenes.get(scene.extension);
        if(owner==null||owner.ruler.get()!=scene.ruler||owner.ambient.get()!=scene.ambient){
            owner=new ManagedScene(scene.extension,scene.ruler,scene.ambient);managedScenes.put(scene.extension,owner);}
        owner.generation++;owner.active=true;
        for(Object item:rows)if(item instanceof View&&nativeAccess.row.isInstance(item)){
            View view=(View)item;ManagedRow previous=managedRows.get(view);
            if(previous==null||previous.scene!=owner)
                managedRows.put(view,new ManagedRow(view,owner));
            else {previous.generation=owner.generation;if(previous.parent.get()!=view.getParent())previous.parent=new java.lang.ref.WeakReference<>(view.getParent());}
        }
    }
    /** Toggle the open state. With a known list and border the change eases over TRANSITION_MILLIS. */
    private void setOpen(boolean open) {
        if(pulledOpen==open)return;
        pulledOpen=open;scrolledOpen=false;
        if(stackView.get()!=null&&finite(shownBorder)){transitionFrom=shownBorder;transitionStart=android.os.SystemClock.uptimeMillis();}
        else transitionStart=-1L;
    }
    private void foldNow(){pulledOpen=false;scrolledOpen=false;transitionStart=-1L;shownBorder=Float.NaN;}
    private float transition(float target) {
        if(transitionStart<0L||!finite(transitionFrom))return target;
        long elapsed=android.os.SystemClock.uptimeMillis()-transitionStart;
        if(elapsed<0L||elapsed>=TRANSITION_MILLIS){transitionStart=-1L;return target;}
        float progress=elapsed/(float)TRANSITION_MILLIS,remaining=1f-progress;
        // Decelerating cubic, close to the native panel curves.
        float eased=1f-remaining*remaining*remaining;
        requestFrame();
        return transitionFrom+(target-transitionFrom)*eased;
    }
    /** One native children update per display frame while easing; never a timer or a loop. */
    private void requestFrame() {
        View stack=stackView.get();
        if(stack==null||framePosted)return;
        framePosted=true;
        stack.postOnAnimation(()->{framePosted=false;nativeChildrenUpdate(stack);});
    }
    private void nativeChildrenUpdate(View stack) {
        if(!childrenUpdateResolved){childrenUpdateResolved=true;
            for(Class<?> type=stack.getClass();type!=null&&childrenUpdate==null;type=type.getSuperclass())
                try{childrenUpdate=type.getDeclaredMethod("requestChildrenUpdate");childrenUpdate.setAccessible(true);}catch(ReflectiveOperationException|RuntimeException absent){}
        }
        try{if(childrenUpdate!=null)childrenUpdate.invoke(stack);}catch(ReflectiveOperationException|RuntimeException ignored){}
    }
    public void reset(){hooksReady=false;scenes.remove();clearManaged();restoreFlows();rulerResources.clear();foldNow();touchDownY=Float.NaN;stackView.clear();framePosted=false;}
    private float touchDownY=Float.NaN;
    private java.lang.reflect.Method childrenUpdate;private boolean childrenUpdateResolved;
    /** Observe pulls at the top without consuming the native gesture: upward opens, downward folds. */
    public boolean touch(android.view.View stack,android.view.MotionEvent event){
        if(stack==null||event==null||!enabled())return false;
        if(stackView.get()!=stack)stackView=new java.lang.ref.WeakReference<>(stack);
        int action=event.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN){touchDownY=event.getPointerCount()==1&&!stack.canScrollVertically(-1)?event.getY():Float.NaN;return false;}
        if(action!=android.view.MotionEvent.ACTION_MOVE){touchDownY=Float.NaN;return false;}
        if(event.getPointerCount()!=1){touchDownY=Float.NaN;return false;}
        if(!finite(touchDownY))return false;
        float density=stack.getResources().getDisplayMetrics().density;
        float slop=8f*(density>0f?density:1f),upward=touchDownY-event.getY();
        boolean open;
        if(!pulledOpen&&upward>=slop)open=true;
        // Pulling down at the top of an open stack folds it back, like scrolling back to the top.
        else if(pulledOpen&&-upward>=slop&&!stack.canScrollVertically(-1))open=false;
        else return false;
        touchDownY=Float.NaN;setOpen(open);lastStage=open?"pulled-open-touch":"folded-touch";
        // The native list recalculates its targets on its next children update.
        nativeChildrenUpdate(stack);
        stack.requestLayout();stack.invalidate();
        return true;
    }
    public boolean pulledOpen(){return pulledOpen;}
    static final String FLUID_ROW="com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow";
    static final String SEEDING_CARD="com.oplus.systemui.statusbar.notification.stack.NotificationSeedingContainer";
    /** Fluid-cloud (Seedling) cards in the notification list, by their exact native classes. */
    static boolean fluidCard(Object item) {
        for(Class<?> type=item==null?null:item.getClass();type!=null;type=type.getSuperclass()) {
            String name=type.getName();
            if(FLUID_ROW.equals(name)||SEEDING_CARD.equals(name))return true;
        }
        return false;
    }
    private static boolean yes(Method method,Object receiver)throws ReflectiveOperationException {
        return method!=null&&Boolean.TRUE.equals(method.invoke(receiver));
    }
    private static boolean yes(Field field,Object receiver)throws ReflectiveOperationException {
        return field!=null&&field.getBoolean(receiver);
    }
    private static Field booleanField(Class<?> type,String name) {
        Field result=field(type,name);return result!=null&&result.getType()==Boolean.TYPE?result:null;
    }
    private static Method optional(Class<?> type,String name) {
        try {return exact(type,name);}catch(ReflectiveOperationException unsupported){return null;}
    }
    private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
    private static Method exact(Class<?> type,String name,Class<?>...parameters)throws ReflectiveOperationException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{
            Method method=c.getDeclaredMethod(name,parameters);method.setAccessible(true);return method;
        }catch(NoSuchMethodException missing){/* Parent. */}
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static Field field(Class<?> type,String name) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{
            Field field=c.getDeclaredField(name);field.setAccessible(true);return field;
        }catch(NoSuchFieldException missing){/* Parent. */}
        return null;
    }
}
