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
    private boolean hooksReady;
    private int completeCount=1;
    private Access access;
    // Primitive-only structural counters; no notification text/package or per-frame log.
    private int updateCalls,shadeCalls,currentCalls,applyCalls,borderChanges,lastRows,lastEligibleRows;
    private String lastStage="unresolved";
    private float lastNativeBorder,lastAppliedBorder;
    private final ThreadLocal<Scope> scenes=new ThreadLocal<>();
    private final Map<Object,FlowState> observedRulers=new WeakHashMap<>();
    // The real ruler owns a Context. Resolve Resources once; its live Configuration follows
    // rotation even when the independent big-clock feature is disabled or has no callback.
    private final Map<Object,Resources> rulerResources=new WeakHashMap<>();
    private static final class FlowState {
        boolean nativeValue,applied;
        FlowState(boolean nativeValue){this.nativeValue=nativeValue;}
    }

    public interface NativeAction { Object run() throws Throwable; }
    private static final class Scope {
        final Object ruler,manager;
        final int scroll;
        Scope(Object ruler,Object manager,int scroll){this.ruler=ruler;this.manager=manager;this.scroll=scroll;}
    }
    /** Exact real-ROM signatures are resolved together; a partial match is not supported. */
    static final class Access {
        final Class<?> extension,ruler,manager,ambient,row;
        final Method update,current,apply,currentRuler,scroll,state,y,stableHeight,child;
        final Method removed,dismissed,guts,userLocked,expanding,groupChanging,dimens,scaleBase,translationBase;
        final Field managerField,algorithmField,nativeCurrent,flow,dismissedFlag,swiping,context;
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
        enabled=source!=null&&!SafetyMode.enabled(source)
                &&Boolean.TRUE.equals(source.get(NotificationBigClockSettings.STACK_ENABLED));
        landscapeEnabled=source!=null&&Boolean.TRUE.equals(source.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        Object value=source==null?null:source.get(NotificationBigClockSettings.VISIBLE_COUNT);
        float count=value instanceof Number?((Number)value).floatValue():1f;
        completeCount=!finite(count)?1:Math.max(1,count>=Integer.MAX_VALUE?Integer.MAX_VALUE:Math.round(count));
        configurationChanged();
    }
    public boolean enabled(){return enabled&&hooksReady&&access!=null&&!ModuleLifecycle.removed();}
    /** Read only on an explicit diagnostic/probe. Counting never allocates a frame log. */
    public String diagnosticSummary(){return "whole requested="+enabled+" landscape="+landscapeEnabled+" ready="+hooksReady+" resolved="+(access!=null)
            +" update="+updateCalls+" shade="+shadeCalls+" current="+currentCalls+" apply="+applyCalls
            +" changed="+borderChanges+" rows="+lastRows+" eligible="+lastEligibleRows+" count="+completeCount+" native="+lastNativeBorder
            +" chosen="+lastAppliedBorder+" stage="+lastStage;}
    /** All three native callbacks must be installed before any saved request becomes active. */
    public void hooksReady(boolean ready){hooksReady=ready&&access!=null;if(!hooksReady){scenes.remove();restoreFlows();}}
    public boolean resolve(ClassLoader loader) {
        restoreFlows();
        hooksReady=false;access=null;scenes.remove();rulerResources.clear();
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
        if(enabled()&&notificationPage&&access!=null&&access.extension.isInstance(extension)
                &&access.ambient.isInstance(ambient))try {
            Object ruler=access.currentRuler.invoke(extension);
            if(access.ruler.isInstance(ruler)&&enabledFor(ruler)) {
                Object manager=access.managerField.get(ruler);
                int scroll=((Number)access.scroll.invoke(ambient)).intValue();
                if(access.manager.isInstance(manager)&&scroll>=0){next=new Scope(ruler,manager,scroll);shadeCalls++;lastStage="shade-scope";}
                else lastStage="native-manager-or-scroll";
            }else if(access.ruler.isInstance(ruler)) {
                restoreFlow(ruler,access);lastStage="native-orientation";
            }else lastStage="native-keyguard-ruler";
        }catch(ReflectiveOperationException|RuntimeException unknownState){lastStage="scene-reflection";}
        else lastStage=enabled()?"native-page-or-type":"disabled-or-hooks";
        // An unrelated nested native scene must never inherit the outer shade parameters.
        if(next==null)scenes.remove();else scenes.set(next);
        try{return nativeUpdate.run();}
        finally{if(previous==null)scenes.remove();else scenes.set(previous);}
    }
    /** Native clipping reads this getter after target calculation has left its scope.
     * One stable value is required for both phases; keyguard remains a different class. */
    public boolean currentStacked(Object ruler,boolean nativeValue) {
        currentCalls++;
        Access nativeAccess=access;
        if(nativeAccess==null||!nativeAccess.ruler.isInstance(ruler))return nativeValue;
        boolean active=enabledFor(ruler);
        FlowState state=observedRulers.get(ruler);
        if(active&&state==null){state=new FlowState(nativeValue);observedRulers.put(ruler,state);}
        boolean result=active||nativeValue;
        // Native collectors drive appearance/clipping separately from target calculation.
        // Late enable must notify that same native StateFlow, and not just override a getter.
        if(state!=null&&(!active||!state.applied||state.nativeValue!=nativeValue)) {
            state.applied=syncFlow(ruler,result,nativeAccess);state.nativeValue=nativeValue;
        }
        if(!active)observedRulers.remove(ruler);
        return result;
    }
    private static boolean syncFlow(Object ruler,boolean value,Access nativeAccess) {
        if(nativeAccess.flow==null||nativeAccess.flowValue==null||nativeAccess.flowSet==null)return true;
        try {
            Object flow=nativeAccess.flow.get(ruler);
            if(flow==null)return false; // Getter can run during the ruler's constructor.
            if(!Boolean.valueOf(value).equals(nativeAccess.flowValue.invoke(flow)))
                nativeAccess.flowSet.invoke(flow,Boolean.valueOf(value));
            return true;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return true;/* Original collector retained. */}
    }
    private void restoreFlows() {
        Access nativeAccess=access;
        if(nativeAccess!=null)for(Object ruler:new ArrayList<>(observedRulers.keySet()))restoreFlow(ruler,nativeAccess);
        observedRulers.clear();
    }
    private void restoreFlow(Object ruler,Access nativeAccess) {
        FlowState previous=observedRulers.remove(ruler);if(previous==null)return;
        try {
            boolean nativeValue=previous.nativeValue;
            if(nativeAccess.nativeCurrent!=null&&nativeAccess.nativeCurrent.getType()==Boolean.TYPE)
                nativeValue=nativeAccess.nativeCurrent.getBoolean(ruler);
            syncFlow(ruler,nativeValue,nativeAccess);
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Native fallback. */}
    }
    /** Called after native configuration changes and on every saved-setting change. Native
     * collectors must not retain the module's enabled value when landscape is opted out. */
    public void configurationChanged() {
        Scope scene=scenes.get();
        if(scene!=null&&!enabledFor(scene.ruler))scenes.remove();
        if(!enabled()){scenes.remove();restoreFlows();return;}
        Access nativeAccess=access;
        for(Object ruler:new ArrayList<>(observedRulers.keySet()))
            if(!enabledFor(ruler))restoreFlow(ruler,nativeAccess);
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
            return orientation==Configuration.ORIENTATION_PORTRAIT
                    ||orientation==Configuration.ORIENTATION_LANDSCAPE&&landscapeEnabled;
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
                if(!nativeAccess.row.isInstance(item))continue;
                if(!(item instanceof View)||((View)item).getVisibility()==View.GONE
                        ||yes(nativeAccess.child,item)||yes(nativeAccess.removed,item)||nativeAccess.dismissed(item))continue;
                // Native gestures/expanded group spring must receive its original physical bounds.
                if(nativeAccess.changing(item)){lastStage="native-row-interaction";return nativeBorder;}
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
        lastAppliedBorder=Math.min(nativeBorder,chosen);
        if(lastAppliedBorder<nativeBorder){borderChanges++;lastStage="native-border-applied";}
        else lastStage="native-border-already-limited";
        return lastAppliedBorder;
    }
    public void reset(){hooksReady=false;scenes.remove();restoreFlows();rulerResources.clear();}
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
