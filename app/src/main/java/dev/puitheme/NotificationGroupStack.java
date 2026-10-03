// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Native groups only. Never synthesizes, reparents or folds unrelated notification rows. */
public final class NotificationGroupStack {
    public static final String MASTER="notification_group_stack_enabled";
    private boolean enabled;
    private int prepareCalls,measureCalls,visibleCalls,rankChanges,expandCalls,lastChildren,overlayRefreshes;
    private String lastStage="unbound";
    private final Map<View,Order> orders=new WeakHashMap<>();
    private final Map<Class<?>,ContainerAccess> containers=new WeakHashMap<>();
    private final Map<Class<?>,RowAccess> rows=new WeakHashMap<>();
    private final Map<View,Gesture> gestures=new WeakHashMap<>();
    private final Map<View,Boolean> pendingMeasurements=new WeakHashMap<>();
    private WeakReference<View> pendingExpand=new WeakReference<>(null);
    private final Matrix matrix=new Matrix();
    private final RectF bounds=new RectF();
    private final Rect localClip=new Rect();
    public void configure(Bundle source) {
        // This legacy option has been withdrawn. Old preferences/imports cannot reactivate
        // it; retain the restoration path for a native group already owned before upgrade.
        enabled=false;
        restore();
    }
    public boolean enabled(){return enabled&&!ModuleLifecycle.removed();}
    /** Fixed structural evidence only; never includes notification text, app ID or message time. */
    public String diagnosticSummary(){return "group requested="+enabled+" prepare="+prepareCalls+" measure="+measureCalls
            +" visible="+visibleCalls+" ranked="+rankChanges+" expand="+expandCalls+" children="+lastChildren
            +" tracked="+orders.size()+" pending="+pendingMeasurements.size()+" overlay="+overlayRefreshes+" stage="+lastStage;}
    private static final class Order {
        final WeakReference<List<View>> original;
        WeakReference<ArrayList<View>> applied=new WeakReference<>(null);
        final ArrayList<WeakReference<View>> originalRank=new ArrayList<>();
        final ArrayList<WeakReference<View>> members=new ArrayList<>();
        final ArrayList<Long> receipts=new ArrayList<>();
        boolean restorePending,measurePending;
        Order(List<View> original){
            this.original=new WeakReference<>(original);
            for(View child:original)originalRank.add(new WeakReference<>(child));
        }
        ArrayList<View> nativeOrder(){
            ArrayList<View> result=new ArrayList<>();
            for(WeakReference<View> reference:originalRank){View child=reference.get();if(child!=null)result.add(child);}
            return result;
        }
    }
    private static final class ContainerAccess {
        final Field children,summary,expanded,swiping,extension,headerClick,overlay;
        final Method nativeHeader,expandedHeader,recreateHeader,overlayUpdated;
        ContainerAccess(Class<?> type) {
            children=field(type,"mAttachedChildren");summary=field(type,"mContainingNotification");
            expanded=field(type,"mChildrenExpanded");swiping=field(type,"mIsUserSwipingToExpandRow");
            extension=field(type,"mExt");headerClick=field(type,"mHeaderClickListener");
            nativeHeader=method(type,"getGroupHeader");
            expandedHeader=extension==null?null:method(extension.getType(),"getOplusExpandGroupHeaderView");
            recreateHeader=extension==null?null:headerMethod(extension.getType(),type);
            // ColorOS renders collapsed content in a separate native overlay/cache. Updating
            // mAttachedChildren alone does not rebuild its latest-message content.
            overlay=field(type,"mOverlayEx");
            overlayUpdated=overlay==null?null:method(overlay.getType(),"onChildUpdated");
        }
    }
    private static final class RowAccess {
        final Method entry,sbn,postTime,adapter,adapterSbn,adapterPostTime,removed,dismissed,guts,userLocked,expanding,groupChanging;
        final Method actualHeight,attachedChildren,clipTop,clipBottom,actualClipHeight;
        final Field extension;
        final Field container,click,swiping,rowExtension,onKeyguard,dismissedFlag;
        private Class<?> rowExtensionClass;
        private Method childrenAnimating;
        RowAccess(Class<?> type) {
            entry=method(type,"getEntry");sbn=entry==null?null:method(entry.getReturnType(),"getSbn");
            postTime=sbn==null?null:method(sbn.getReturnType(),"getPostTime");
            // Current ColorOS rows may hold another PipelineEntry. getEntry() then returns
            // null by design; SystemUI itself retrieves the SBN through EntryAdapter.
            adapter=method(type,"getEntryAdapter");
            adapterSbn=adapter==null?null:method(adapter.getReturnType(),"getSbn");
            adapterPostTime=adapterSbn==null?null:method(adapterSbn.getReturnType(),"getPostTime");
            removed=method(type,"isRemoved");dismissed=method(type,"isDismissed");
            dismissedFlag=field(type,"mDismissed");
            guts=method(type,"areGutsExposed");userLocked=method(type,"isUserLocked");
            expanding=method(type,"isExpandAnimationRunning");groupChanging=method(type,"isGroupExpansionChanging");
            actualHeight=method(type,"getActualHeight");attachedChildren=method(type,"getAttachedChildren");
            clipTop=method(type,"getClipTopAmount");clipBottom=method(type,"getClipBottomAmount");
            extension=field(type,"mExpandableViewEx");
            actualClipHeight=extension==null?null:method(extension.getType(),"getActualClipHeight");
            container=field(type,"mChildrenContainer");click=field(type,"mExpandClickListener");
            swiping=field(type,"mIsUserSwipingToExpandRow");rowExtension=field(type,"mRowEx");
            onKeyguard=field(type,"mOnKeyguard");
        }
        boolean nativeShade(View row) {
            return onKeyguard!=null&&onKeyguard.getType()==Boolean.TYPE&&!yes(onKeyguard,row);
        }
        boolean dismissed(View row){return dismissedFlag!=null?yes(dismissedFlag,row):yes(dismissed,row);}
        boolean interaction(View row){
            if(yes(guts,row)||yes(userLocked,row)||yes(expanding,row)||yes(groupChanging,row)||yes(swiping,row))return true;
            if(rowExtension!=null)try {
                Object extension=rowExtension.get(row);
                if(extension!=null){
                    if(rowExtensionClass!=extension.getClass()){
                        rowExtensionClass=extension.getClass();childrenAnimating=method(rowExtensionClass,"isChildrenExpandedAnimating");
                    }
                    if(yes(childrenAnimating,extension))return true;
                }
            }catch(ReflectiveOperationException|RuntimeException unsupported){return true;}
            return false;
        }
        long receipt(View row)throws ReflectiveOperationException {
            if(adapter!=null&&adapterSbn!=null&&adapterPostTime!=null) {
                Object nativeAdapter=adapter.invoke(row);
                if(nativeAdapter!=null) {
                    Object nativeSbn=adapterSbn.invoke(nativeAdapter);
                    if(nativeSbn!=null) {
                        Object time=adapterPostTime.invoke(nativeSbn);
                        if(time instanceof Number)return ((Number)time).longValue();
                    }
                }
            }
            if(entry==null||sbn==null||postTime==null)return Long.MIN_VALUE;
            Object nativeEntry=entry.invoke(row);if(nativeEntry==null)return Long.MIN_VALUE;
            Object nativeSbn=sbn.invoke(nativeEntry);if(nativeSbn==null)return Long.MIN_VALUE;
            Object time=postTime.invoke(nativeSbn);return time instanceof Number?((Number)time).longValue():Long.MIN_VALUE;
        }
    }
    /** Native onKeyguard belongs to the actual owner row, independently of clock visibility. */
    public void prepareNative(View view,boolean calculatingTargets) {
        prepare(view,true,calculatingTargets);
    }
    public void prepareMeasure(View view){prepareMeasure(view,true);}
    public int visibleChildren(View view,int nativeCount){return visibleChildren(view,nativeCount,true);}
    private ContainerAccess container(View view) {
        ContainerAccess result=containers.get(view.getClass());
        if(result==null){result=new ContainerAccess(view.getClass());containers.put(view.getClass(),result);}return result;
    }
    private RowAccess row(View view) {
        RowAccess result=rows.get(view.getClass());
        if(result==null){result=new RowAccess(view.getClass());rows.put(view.getClass(),result);}return result;
    }
    /** Update/apply callbacks never exchange the first child after native overlay measurement. */
    public void prepare(View view,boolean notificationPage,boolean calculatingTargets) {
        if(calculatingTargets){prepare(view,notificationPage);return;}
        if(view==null||!orders.containsKey(view))return;
        try {
            ContainerAccess access=container(view);Object owner=access.summary.get(view);
            if(!enabled()||!notificationPage||!(owner instanceof View)||!row((View)owner).nativeShade((View)owner))release(view);
            else if(access.children.get(view)!=orders.get(view).applied.get())orders.remove(view);
            // Never change rank between native target calculation and native application.
        }catch(ReflectiveOperationException|RuntimeException unsupported){release(view);}
    }
    public void prepare(View view,boolean notificationPage){prepareInternal(view,notificationPage,false);}
    /** Before the actual native onMeasure: its overlay cache must see the same first child. */
    public void prepareMeasure(View view,boolean notificationPage){prepareInternal(view,notificationPage,true);}
    @SuppressWarnings("unchecked")
    private void prepareInternal(View view,boolean notificationPage,boolean measuring) {
        prepareCalls++;if(measuring)measureCalls++;
        if(view==null)return;
        if(measuring)pendingMeasurements.remove(view);
        if(!enabled()) {
            lastStage="forced-off";
            if(measuring)releaseNow(view);else release(view);
            return;
        }
        ContainerAccess access=container(view);
        try {
            if(access.children==null||access.summary==null||access.expanded==null){lastStage="native-container-fields-missing";return;}
            Object owner=access.summary.get(view);
            boolean shade=owner instanceof View&&row((View)owner).nativeShade((View)owner);
            boolean allowed=enabled()&&notificationPage&&shade
                    &&!access.expanded.getBoolean(view)&&!yes(access.swiping,view)
                    &&!row((View)owner).interaction((View)owner);
            Object current=access.children.get(view);
            if(!(current instanceof List)){if(measuring)releaseNow(view);else release(view);return;}
            List<View> children=(List<View>)current;
            lastChildren=children.size();
            if(!enabled()||!notificationPage||!shade||children.size()<2){
                lastStage=!enabled()?"disabled":!shade?"native-keyguard":"fewer-than-two-native-children";
                if(measuring)releaseNow(view);else release(view);return;
            }
            Order saved=orders.get(view);
            if(saved!=null&&children!=saved.applied.get()){orders.remove(view);saved=null;} // Native writer wins.
            if(saved!=null)saved.restorePending=false;
            if(saved!=null&&measuring)saved.measurePending=false;
            // Native group springs cache the first child's geometry and expanded header. Restoring
            // the original list during expansion switches that identity halfway through the spring.
            if(!allowed){lastStage="native-expanded-or-interaction";return;}
            boolean unchanged=saved!=null&&saved.members.size()==children.size();
            for(int i=0;i<children.size();i++) {
                Object value=children.get(i);
                if(!(value instanceof View)){release(view);return;}
                View child=(View)value;RowAccess members=row(child);
                if(child.getParent()!=view||child.getVisibility()==View.GONE||yes(members.removed,child)
                        ||members.dismissed(child)||members.interaction(child)){lastStage="native-child-not-ready";release(view);return;}
                long receipt=members.receipt(child);
                if(receipt==Long.MIN_VALUE){lastStage="native-entry-adapter-pending";release(view);return;} // Never invent receipt order.
                if(unchanged&&(saved.members.get(i).get()!=child||saved.receipts.get(i)!=receipt))unchanged=false;
            }
            if(unchanged){lastStage="native-group-stable";return;}
            if(!measuring){
                if(!pendingMeasurements.containsKey(view)){
                    pendingMeasurements.put(view,Boolean.TRUE);view.requestLayout();
                    if(saved!=null)saved.measurePending=true;
                }
                lastStage="waiting-native-measure";return;
            }
            if(saved==null){saved=new Order(children);orders.put(view,saved);}
            else reconcile(saved,children); // Add/remove events must survive release.
            ArrayList<View> ordered=saved.nativeOrder();
            // Stable native rank for equal receipt times; the complete real list remains available.
            for(int i=1;i<ordered.size();i++) {
                View child=ordered.get(i);long receipt=row(child).receipt(child);int j=i-1;
                while(j>=0&&row(ordered.get(j)).receipt(ordered.get(j))<receipt){
                    ordered.set(j+1,ordered.get(j));--j;
                }
                ordered.set(j+1,child);
            }
            saved.members.clear();saved.receipts.clear();
            for(View child:ordered){saved.members.add(new WeakReference<>(child));saved.receipts.add(row(child).receipt(child));}
            boolean firstChanged=children.get(0)!=ordered.get(0);
            access.children.set(view,ordered);saved.applied=new WeakReference<>(ordered);
            rankChanges++;lastStage="native-group-ranked";
            saved.measurePending=false;
            ensureNativeHeader(view,(View)owner,access);
            if(firstChanged)refreshNativeOverlay(view,access);
        }catch(ReflectiveOperationException|RuntimeException unsupported){lastStage="native-group-reflection";release(view);}
    }
    private static void reconcile(Order saved,List<View> current) {
        for(int i=saved.originalRank.size()-1;i>=0;i--)if(!current.contains(saved.originalRank.get(i).get()))saved.originalRank.remove(i);
        for(View child:current) {
            boolean known=false;
            for(WeakReference<View> reference:saved.originalRank)if(reference.get()==child){known=true;break;}
            if(!known)saved.originalRank.add(new WeakReference<>(child));
        }
    }
    /** Native expanded animation keeps all rows; collapsed native card plus at most three tails. */
    public int visibleChildren(View view,int nativeCount,boolean notificationPage) {
        visibleCalls++;
        if(!enabled()||!notificationPage||!orders.containsKey(view))return nativeCount;
        try{
            ContainerAccess access=container(view);Object owner=access.summary.get(view);
            return access.expanded.getBoolean(view)||yes(access.swiping,view)||!(owner instanceof View)
                    ||!row((View)owner).nativeShade((View)owner)
                    ||row((View)owner).interaction((View)owner)?nativeCount:Math.min(nativeCount,4);
        }
        catch(ReflectiveOperationException|RuntimeException unsupported){return nativeCount;}
    }
    public void release(View view) {
        Order saved=orders.get(view);if(saved==null||saved.restorePending)return;
        saved.restorePending=true;view.requestLayout();
    }
    private void releaseNow(View view) {
        pendingMeasurements.remove(view);
        Order saved=orders.remove(view);if(saved==null)return;
        try {
            ContainerAccess access=container(view);
            if(access.children.get(view)!=saved.applied.get())return;
            List<View> current=saved.applied.get();if(current==null)return;
            View previousFirst=current.isEmpty()?null:current.get(0);
            reconcile(saved,current);List<View> original=saved.original.get();
            if(original==null)original=saved.nativeOrder();
            else {original.clear();original.addAll(saved.nativeOrder());}
            access.children.set(view,original);view.requestLayout();
            if(previousFirst!=(original.isEmpty()?null:original.get(0)))refreshNativeOverlay(view,access);
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Native writer retained. */}
    }
    public void detach(View view){releaseNow(view);gestures.remove(view);}
    public void restore(){
        pendingMeasurements.clear();
        for(View view:new ArrayList<>(orders.keySet())){
            if(ModuleLifecycle.removed())releaseNow(view);else release(view);
        }
        resetGesture();
    }
    public void resetGesture(){gestures.clear();pendingExpand.clear();}
    private static final class Gesture {
        final NotificationStackTap tap=new NotificationStackTap();
        final WeakReference<View> summary;
        Gesture(View summary){this.summary=new WeakReference<>(summary);}
    }
    /** Confirm a real short card tap; long press, swipes, multi-touch remain native. */
    public boolean tap(View stack,MotionEvent event){return tap(stack,event,true);}
    public boolean tap(View stack,MotionEvent event,boolean notificationPage) {
        if(stack==null||event==null)return false;
        int action=event.getActionMasked();
        if(!enabled()||!notificationPage){gestures.remove(stack);return false;}
        Gesture gesture=gestures.get(stack);
        if(action==MotionEvent.ACTION_DOWN){
            View summary=hitSummary(stack,event.getX(),event.getY());
            gesture=summary==null?null:new Gesture(summary);
            if(gesture==null)gestures.remove(stack);else gestures.put(stack,gesture);
        }
        if(gesture==null)return false;
        View summary=gesture.summary.get();
        boolean hit=summary!=null&&summary==hitSummary(stack,event.getX(),event.getY());
        ViewConfiguration config=ViewConfiguration.get(stack.getContext());
        boolean expand=gesture.tap.event(action,event.getPointerCount(),event.getX(),event.getY(),
                event.getEventTime(),hit,config.getScaledTouchSlop(),ViewConfiguration.getLongPressTimeout());
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)gestures.remove(stack);
        if(expand)pendingExpand=new WeakReference<>(summary);
        return expand;
    }
    private View hitSummary(View stack,float x,float y) {
        for(View view:orders.keySet())try {
            ContainerAccess access=container(view);Object value=access.summary.get(view);
            if(!(value instanceof View)||access.expanded.getBoolean(view))continue;
            View summary=(View)value;RowAccess members=row(summary);
            if(!members.nativeShade(summary)||!summary.isAttachedToWindow()||!summary.isShown()||summary.getAlpha()<=0f||members.interaction(summary)
                    ||yes(members.removed,summary)||members.dismissed(summary)
                    ||members.click==null||!(members.click.get(summary) instanceof View.OnClickListener))continue;
            boolean ancestor=false;
            for(View v=summary;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null)
                if(v==stack){ancestor=true;break;}
            if(!ancestor)continue;
            int height=members.actualHeight==null?summary.getHeight():((Number)members.actualHeight.invoke(summary)).intValue();
            float top=number(members.clipTop,summary,0f),bottom=height-number(members.clipBottom,summary,0f);
            if(members.actualClipHeight!=null&&members.extension!=null){
                Object extension=members.extension.get(summary);
                if(extension!=null)bottom-=number(members.actualClipHeight,extension,0f);
            }
            float left=0f,right=summary.getWidth();
            if(summary.getClipBounds(localClip)){left=Math.max(left,localClip.left);top=Math.max(top,localClip.top);
                right=Math.min(right,localClip.right);bottom=Math.min(bottom,localClip.bottom);}
            if(!finite(top)||!finite(bottom)||bottom<=top||right<=left)continue;
            matrix.reset();summary.transformMatrixToGlobal(matrix);stack.transformMatrixToLocal(matrix);
            bounds.set(left,top,right,bottom);matrix.mapRect(bounds);
            if(bounds.contains(x,y))return summary;
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Native tap remains. */}
        return null;
    }
    /** Invoke the existing expand callback; SystemUI owns group manager, accessibility and animation. */
    public boolean expandPending() {
        View summary=pendingExpand.get();pendingExpand.clear();if(summary==null||!enabled())return false;
        try {
            RowAccess access=row(summary);
            if(!access.nativeShade(summary)||access.interaction(summary))return false;
            Object nativeContainer=access.container==null?null:access.container.get(summary);
            if(nativeContainer instanceof View)ensureNativeHeader((View)nativeContainer,summary,container((View)nativeContainer));
            Object listener=access.click.get(summary);
            if(!(listener instanceof View.OnClickListener))return false;
            ((View.OnClickListener)listener).onClick(summary);expandCalls++;lastStage="native-expand-click";return true;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    /** Reuse ColorOS' actual app-name/collapse-arrow header; no custom text, alpha or height. */
    private void ensureNativeHeader(View view,View summary,ContainerAccess access)throws ReflectiveOperationException {
        if(access.extension==null||access.nativeHeader==null||access.expandedHeader==null||access.recreateHeader==null)return;
        Object extension=access.extension.get(view);
        if(extension==null||access.nativeHeader.invoke(view)==null)return;
        Object header=access.expandedHeader.invoke(extension);
        if(header instanceof View&&((View)header).getParent()==view)return;
        Object listener=access.headerClick==null?null:access.headerClick.get(view);
        if(!(listener instanceof View.OnClickListener))listener=row(summary).click.get(summary);
        if(!(listener instanceof View.OnClickListener))return;
        access.recreateHeader.invoke(extension,view,listener,access.expanded.getBoolean(view));
        view.requestLayout();
    }
    private void refreshNativeOverlay(View view,ContainerAccess access) {
        if(access.overlay==null||access.overlayUpdated==null)return;
        try {
            Object overlay=access.overlay.get(view);
            if(overlay!=null){access.overlayUpdated.invoke(overlay);overlayRefreshes++;}
        }catch(ReflectiveOperationException|RuntimeException unsupported){lastStage="native-overlay-refresh-unavailable";}
    }
    private static Method headerMethod(Class<?> extension,Class<?> container) {
        for(Class<?> type=extension;type!=null;type=type.getSuperclass())for(Method method:type.getDeclaredMethods()){
            Class<?>[] parameters=method.getParameterTypes();
            if(method.getName().equals("recreateOplusNotificationHeader")&&parameters.length==3
                    &&parameters[0].isAssignableFrom(container)&&parameters[1]==View.OnClickListener.class
                    &&parameters[2]==Boolean.TYPE){method.setAccessible(true);return method;}
        }
        return null;
    }
    private static float number(Method method,Object receiver,float fallback)throws ReflectiveOperationException {
        if(method==null)return fallback;Object value=method.invoke(receiver);
        return value instanceof Number?((Number)value).floatValue():fallback;
    }
    private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
    private static boolean yes(Field field,Object receiver){try{return field!=null&&field.getBoolean(receiver);}catch(Exception e){return true;}}
    private static boolean yes(Method method,Object receiver){try{return method!=null&&Boolean.TRUE.equals(method.invoke(receiver));}catch(Exception e){return true;}}
    private static Field field(Class<?> type,String name){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){/* Parent. */}
        return null;
    }
    private static Method method(Class<?> type,String name){
        try {Method m=type.getMethod(name);m.setAccessible(true);return m;}
        catch(NoSuchMethodException missing){/* Non-public native implementation. */}
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name);m.setAccessible(true);return m;}catch(NoSuchMethodException e){/* Parent. */}
        return null;
    }
}
