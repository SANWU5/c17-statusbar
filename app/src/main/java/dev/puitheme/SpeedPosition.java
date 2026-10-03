// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Event-driven placement. The original icon never leaves its IconManager-owned parent/index. */
public final class SpeedPosition {
    public static final String POSITION="speed_position";
    public static final String NATIVE="native",CELLULAR_LEFT="cellular_left",RIGHT_START="right_start",
            CLOCK_LEFT="clock_left",CLOCK_RIGHT="clock_right";
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(POSITION,NATIVE);}
    public static String position(Object value){
        return CELLULAR_LEFT.equals(value)||RIGHT_START.equals(value)||CLOCK_LEFT.equals(value)||CLOCK_RIGHT.equals(value)
                ?(String)value:NATIVE;
    }
    /** A frozen selection, replaced only by a configuration event; no preference access during layout/draw. */
    private static final class Selection {
        final String position;
        Selection(String position){this.position=position;}
        boolean clock(){return CLOCK_LEFT.equals(position)||CLOCK_RIGHT.equals(position);}
    }
    private static volatile Selection selection=new Selection(NATIVE);
    private boolean released,hooksReady;
    private Class<?> nativeContainer,nativeSpeed,nativePhone,nativeState;
    private Method slot,visible,applyState,setVisible,setTint,setSlot,getX,setX,updateStates;
    private Field stateField,blockedField,stateVisible,ignored;
    private int stateTag,clockId,clockContainerId,startSideId,layoutId;
    private final Map<ViewGroup,Binding> containers=new WeakHashMap<>();
    private final Map<View,Binding> sources=new WeakHashMap<>(),copies=new WeakHashMap<>();
    private final Map<View,Binding> clocks=new WeakHashMap<>();
    private final ThreadLocal<Scope> scopes=new ThreadLocal<>();
    interface CopyFactory {View create(View source,FrameLayout parent,int layout)throws Exception;}
    private final CopyFactory copyFactory;
    public SpeedPosition(){this((source,parent,layout)->LayoutInflater.from(source.getContext()).inflate(layout,parent,false));}
    SpeedPosition(CopyFactory factory){copyFactory=factory;}

    public void resolve(ClassLoader loader)throws ReflectiveOperationException{
        nativeContainer=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
        nativePhone=loader.loadClass("com.android.systemui.statusbar.phone.PhoneStatusBarView");
        nativeSpeed=loader.loadClass(NetworkSpeedControls.VIEW);
        Class<?> displayable=loader.loadClass("com.android.systemui.statusbar.StatusIconDisplayable");
        Class<?> speedState=loader.loadClass("com.oplus.systemui.statusbar.phone.netspeed.NetworkSpeedIconState");
        nativeState=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
        Class<?> viewState=loader.loadClass("com.android.systemui.statusbar.notification.stack.ViewState");
        slot=displayable.getMethod("getSlot");visible=displayable.getMethod("isIconVisible");
        stateField=nativeSpeed.getField("mState");blockedField=nativeSpeed.getField("mBlocked");
        applyState=nativeSpeed.getMethod("applyNetworkState",speedState);
        setVisible=nativeSpeed.getMethod("setVisibleState",Integer.TYPE,Boolean.TYPE);
        setTint=nativeSpeed.getMethod("setIconTint",Integer.TYPE);setSlot=nativeSpeed.getMethod("setSlot",String.class);
        stateVisible=nativeState.getDeclaredField("visibleState");stateVisible.setAccessible(true);
        getX=viewState.getDeclaredMethod("getXTranslation");setX=viewState.getDeclaredMethod("setXTranslation",Float.TYPE);
        updateStates=nativeContainer.getDeclaredMethod("updateStates");updateStates.setAccessible(true);
        ignored=nativeContainer.getDeclaredField("mIgnoredSlots");ignored.setAccessible(true);
    }
    public boolean configure(Bundle b){
        String next=!released&&b!=null&&!SafetyMode.enabled(b)&&b.getBoolean("speed_enabled",false)
                &&b.getBoolean("speed_position_enabled",true)?position(b.get(POSITION)):NATIVE;
        if(selection.position.equals(next))return false;
        selection=new Selection(next);
        for(Binding binding:new ArrayList<>(containers.values())){
            binding.dirty=true;refreshCopy(binding,true);
            ViewGroup parent=binding.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
        }
        return true;
    }
    public void attached(View source){
        if(released||source==null||nativeSpeed==null||!nativeSpeed.isInstance(source)||copies.containsKey(source)
                ||!(source.getParent() instanceof ViewGroup))return;
        ViewGroup parent=(ViewGroup)source.getParent();
        if(!nativeContainer.isInstance(parent)||phone(parent)==null)return;
        changed(parent);
    }
    /** Partial/unsupported hook installation must not reserve space or mask the native source. */
    public void setHooksReady(boolean ready){
        if(hooksReady==ready)return;hooksReady=ready;
        for(Binding b:new ArrayList<>(containers.values())){
            b.planDirty=true;refreshCopy(b,true);
            ViewGroup parent=b.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
        }
    }
    public void changed(ViewGroup parent){
        if(released||parent==null||nativeContainer==null||!nativeContainer.isInstance(parent))return;
        View phone=phone(parent);if(phone==null)return; // Never borrow a QS/lockscreen clock.
        Binding binding=containers.get(parent);
        if(binding==null){binding=new Binding(parent,phone);containers.put(parent,binding);}
        binding.dirty=true;capture(binding);refreshCopy(binding,true);
    }
    private View phone(View view){
        for(int depth=0;view!=null&&depth<16;depth++){
            if(nativePhone!=null&&nativePhone.isInstance(view))return view;
            view=view.getParent() instanceof View?(View)view.getParent():null;
        }
        return null;
    }
    private void capture(Binding b){
        ViewGroup parent=b.parent.get();View phone=b.phone.get();if(parent==null||phone==null)return;
        if(stateTag==0){
            android.content.res.Resources r=parent.getResources();String pkg="com.android.systemui";
            stateTag=r.getIdentifier("status_bar_view_state_tag","id",pkg);
            clockId=r.getIdentifier("clock","id",pkg);clockContainerId=r.getIdentifier("clock_for_fake","id",pkg);
            startSideId=r.getIdentifier("status_bar_start_side_container","id",pkg);
            layoutId=r.getIdentifier("network_speed_view","layout",pkg);
        }
        b.nodes.clear();View speed=null;
        try{
            for(int i=0;i<parent.getChildCount();i++){
                View child=parent.getChildAt(i);String name=slot.getDeclaringClass().isInstance(child)?(String)slot.invoke(child):null;
                Object state=stateTag==0?null:child.getTag(stateTag);
                if(state!=null&&nativeState.isInstance(state))b.nodes.add(new Node(child,state,name));
                if(nativeSpeed.isInstance(child)){if(speed!=null){speed=null;break;}speed=child;}
            }
        }catch(ReflectiveOperationException|RuntimeException unsupported){speed=null;}
        View previous=b.speed.get();
        if(previous!=speed){removeCopy(b);if(previous!=null)sources.remove(previous);b.speed=new WeakReference<>(speed);}
        if(speed!=null)sources.put(speed,b);
        View clock=clockId==0?null:phone.findViewById(clockId),host=clockContainerId==0?null:phone.findViewById(clockContainerId);
        View start=startSideId==0?null:phone.findViewById(startSideId);
        if(!(clock instanceof TextView)||!(host instanceof FrameLayout)||clock.getParent()!=host||start==null){clock=null;host=null;start=null;}
        if(b.host.get()!=host){removeCopy(b);View oldClock=b.clock.get();if(oldClock!=null)clocks.remove(oldClock);}
        b.clock=new WeakReference<>((TextView)clock);b.host=new WeakReference<>((FrameLayout)host);b.start=new WeakReference<>(start);
        if(clock!=null)clocks.put(clock,b);
        b.dirty=false;b.planDirty=true;
    }
    private final class Binding {
        final WeakReference<ViewGroup> parent;final WeakReference<View> phone;
        WeakReference<View> speed=new WeakReference<>(null),copy=new WeakReference<>(null),start=new WeakReference<>(null);
        WeakReference<TextView> clock=new WeakReference<>(null);WeakReference<FrameLayout> host=new WeakReference<>(null);
        final List<Node> nodes=new ArrayList<>();
        final Map<View,Node> byView=new WeakHashMap<>();
        boolean dirty=true,planDirty=true,ownedPadding,mirrorReady,syncing;
        int left,top,right,bottom,addedLeft,addedRight;
        Object lastState;int lastTint=Integer.MIN_VALUE;
        final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->refreshCopy(this,false);
        Binding(ViewGroup parent,View phone){this.parent=new WeakReference<>(parent);this.phone=new WeakReference<>(phone);phone.addOnLayoutChangeListener(layout);}
    }
    private static final class Node {
        final WeakReference<View> child;final WeakReference<Object> state;final String slot;
        int index;
        float target,width,delta;int visibility;
        Node(View child,Object state,String slot){this.child=new WeakReference<>(child);this.state=new WeakReference<>(state);this.slot=slot;}
    }
    /** Only native state/configuration/layout callbacks call this method, never dispatchDraw/preDraw. */
    public void nativeUpdated(View source){Binding b=sources.get(source);if(b!=null)refreshCopy(b,true);}
    public void slotChanged(View source){
        Binding b=sources.get(source);ViewGroup parent=b==null?null:b.parent.get();
        if(parent!=null)changed(parent);
    }
    public void clockTintChanged(View clock){Binding b=clocks.get(clock);if(b!=null)refreshCopy(b,false);}
    private void refreshCopy(Binding b,boolean stateChanged){
        if(b.syncing)return;b.syncing=true;
        try{
            View source=b.speed.get();FrameLayout host=b.host.get();TextView clock=b.clock.get();View start=b.start.get();
            if(!hooksReady||released||ModuleLifecycle.removed()||!selection.clock()||source==null||host==null||clock==null||start==null
                    ||!source.isAttachedToWindow()||!host.isAttachedToWindow()||layoutId==0){removeCopy(b);return;}
            int width=source.getLayoutParams()==null?source.getMeasuredWidth():source.getLayoutParams().width;
            if(width<=0)width=source.getMeasuredWidth();
            int reserve=width;
            if(source.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){
                ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)source.getLayoutParams();reserve+=Math.max(0,p.leftMargin)+Math.max(0,p.rightMargin);
            }
            int originalWidth=clock.getMeasuredWidth()+(b.ownedPadding?b.left+b.right:host.getPaddingLeft()+host.getPaddingRight());
            // Native start-side width ends before the cutout. Unknown/narrow space keeps the original slot.
            if(width<=0||start.getWidth()<=0||originalWidth<=0||originalWidth+reserve>start.getWidth()){removeCopy(b);return;}
            View copy=b.copy.get();
            if(copy==null){
                copy=copyFactory.create(source,host,layoutId);
                if(!nativeSpeed.isInstance(copy)){removeCopy(b);return;}
                copy.setId(View.NO_ID);copy.setClickable(false);copy.setFocusable(false);
                setSlot.invoke(copy,"c17_network_speed_clock");b.copy=new WeakReference<>(copy);copies.put(copy,b);
                b.left=host.getPaddingLeft();b.top=host.getPaddingTop();b.right=host.getPaddingRight();b.bottom=host.getPaddingBottom();
                host.addView(copy,new FrameLayout.LayoutParams(width,ViewGroup.LayoutParams.MATCH_PARENT,Gravity.LEFT|Gravity.CENTER_VERTICAL));
                b.lastState=null;b.lastTint=Integer.MIN_VALUE;
            }
            Object nativeValue=stateField.get(source);
            boolean shown=nativeValue!=null&&Boolean.TRUE.equals(visible.invoke(source))&&!blockedField.getBoolean(source);
            Object ignoredSlots=ignored.get(b.parent.get());String name=(String)slot.invoke(source);
            if(ignoredSlots instanceof java.util.Set&&((java.util.Set<?>)ignoredSlots).contains(name))shown=false;
            if(stateChanged||nativeValue!=b.lastState){if(nativeValue!=null)applyState.invoke(copy,nativeValue);b.lastState=nativeValue;}
            int tint=clock.getCurrentTextColor();if(tint!=b.lastTint){setTint.invoke(copy,tint);b.lastTint=tint;}
            setVisible.invoke(copy,shown?0:2,false);
            if(copy.getVisibility()!=(shown?View.VISIBLE:View.GONE))copy.setVisibility(shown?View.VISIBLE:View.GONE);
            boolean left=CLOCK_LEFT.equals(selection.position);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)copy.getLayoutParams();
            int leftMargin=left?-reserve:0,rightMargin=left?0:-reserve,gravity=(left?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL;
            if(lp.width!=width||lp.gravity!=gravity||lp.leftMargin!=leftMargin||lp.rightMargin!=rightMargin){
                lp.width=width;lp.gravity=gravity;lp.leftMargin=leftMargin;lp.rightMargin=rightMargin;copy.setLayoutParams(lp);
            }
            int addLeft=shown&&left?reserve:0,addRight=shown&&!left?reserve:0;
            if(addLeft!=b.addedLeft||addRight!=b.addedRight){
                capturePadding(b,host);b.addedLeft=addLeft;b.addedRight=addRight;b.ownedPadding=true;
                host.setPadding(b.left+addLeft,b.top,b.right+addRight,b.bottom);
                ViewGroup parent=b.parent.get();if(parent!=null)parent.requestLayout();
            }
            b.mirrorReady=true;
        }catch(Exception|LinkageError unsupported){removeCopy(b);}
        finally{b.syncing=false;}
    }
    private void capturePadding(Binding b,FrameLayout host){
        // A new OEM inset/padding value wins; subtract our reservation only from the exact owned value.
        if(!b.ownedPadding||host.getPaddingLeft()!=b.left+b.addedLeft)b.left=host.getPaddingLeft();
        if(!b.ownedPadding||host.getPaddingRight()!=b.right+b.addedRight)b.right=host.getPaddingRight();
        b.top=host.getPaddingTop();b.bottom=host.getPaddingBottom();
    }
    private void removeCopy(Binding b){
        b.mirrorReady=false;FrameLayout host=b.host.get();View copy=b.copy.get();
        if(host!=null&&b.ownedPadding){capturePadding(b,host);host.setPadding(b.left,b.top,b.right,b.bottom);}
        b.ownedPadding=false;b.addedLeft=b.addedRight=0;
        b.copy=new WeakReference<>(null);b.lastState=null;b.lastTint=Integer.MIN_VALUE;
        if(copy!=null){copies.remove(copy);if(copy.getParent() instanceof ViewGroup)((ViewGroup)copy.getParent()).removeView(copy);}
    }
    /** Mask only the right-side native allocation, not controller updates or the copy's own native state. */
    public boolean iconBlocked(View child,boolean nativeValue){
        Scope scope=scopes.get();Binding b=sources.get(child);
        return nativeValue||hooksReady&&scope!=null&&scope.binding==b&&b!=null&&b.mirrorReady&&selection.clock();
    }
    public Scope begin(ViewGroup parent,boolean targets){
        if(!hooksReady||released||ModuleLifecycle.removed()||NATIVE.equals(selection.position))return null;
        Binding b=containers.get(parent);if(b==null)return null;
        if(b.dirty)capture(b);
        Scope scope=new Scope(b,targets,scopes.get());scopes.set(scope);return scope;
    }
    public void beforeLayout(ViewGroup parent){Binding b=containers.get(parent);if(b!=null)b.planDirty=true;}
    public final class Scope implements AutoCloseable{
        private Binding binding;private final boolean targets;private Scope previous;
        private float[] nativeTargets,deltas;
        private Scope(Binding b,boolean targets,Scope previous){binding=b;this.targets=targets;this.previous=previous;}
        @Override public void close(){if(scopes.get()==this){if(previous==null)scopes.remove();else scopes.set(previous);}binding=null;previous=null;nativeTargets=null;deltas=null;}
    }
    /** All OEM targets exist before applyToView begins. Touch targets once per native layout, never child indexes. */
    public void beforeApply(Object state,View child){
        Scope scope=scopes.get();if(scope==null||!scope.targets||selection.clock())return;
        Binding b=scope.binding;View speed=b.speed.get();if(speed==null||speed.getParent()!=b.parent.get())return;
        try{
            if(scope.nativeTargets==null){
                // This reads already-bound native state objects, not the view tree. Native updateStates
                // has reset every target immediately before this hook; retain that baseline for repeat/
                // nested applyToView calls so a persistent state can never accumulate our offset.
                int count=b.nodes.size();scope.nativeTargets=new float[count];scope.deltas=new float[count];
                for(int i=0;i<count;i++){
                    Node n=b.nodes.get(i);View v=n.child.get();Object s=n.state.get();if(v==null||s==null)return;
                    int visibility=stateVisible.getInt(s);float width=v.getWidth()+v.getPaddingStart()+v.getPaddingEnd();
                    if(visibility!=n.visibility||width!=n.width)b.planDirty=true;
                    n.index=i;scope.nativeTargets[i]=((Number)getX.invoke(s)).floatValue();
                }
                if(b.planDirty){plan(b,speed);b.planDirty=false;}
                for(int i=0;i<count;i++)scope.deltas[i]=b.nodes.get(i).delta;
            }
            Node node=b.byView.get(child);
            if(node!=null&&node.state.get()==state&&node.index<scope.nativeTargets.length&&scope.deltas[node.index]!=0f&&stateVisible.getInt(state)==0)
                setX.invoke(state,scope.nativeTargets[node.index]+scope.deltas[node.index]);
        }catch(ReflectiveOperationException|RuntimeException unsupported){b.planDirty=true;}
    }
    private void plan(Binding b,View speed)throws ReflectiveOperationException{
            b.byView.clear();
            Node speedNode=null;ArrayList<Node> shown=new ArrayList<>();
            for(Node node:b.nodes){
                View v=node.child.get();Object s=node.state.get();if(v==null||s==null||v.getParent()!=b.parent.get())return;
                node.delta=0f;b.byView.put(v,node);
                node.visibility=stateVisible.getInt(s);node.target=((Number)getX.invoke(s)).floatValue();
                node.width=v.getWidth()+v.getPaddingStart()+v.getPaddingEnd();
                if(node.visibility==0){shown.add(node);if(v==speed)speedNode=node;}
            }
            if(speedNode==null||shown.size()<2)return;
            shown.sort((a,c)->Float.compare(a.target,c.target));
            int old=shown.indexOf(speedNode),at=0;
            if(CELLULAR_LEFT.equals(selection.position)){
                int mobile=-1;for(int i=0;i<shown.size();i++)if(mobile(shown.get(i).slot)){mobile=i;break;}
                if(mobile<0)return;at=old<mobile?mobile-1:mobile;
            }else if(!RIGHT_START.equals(selection.position))return;
            if(old==at)return;
            float newTarget;
            if(at<old){newTarget=shown.get(at).target;for(int i=at;i<old;i++)shown.get(i).delta=speedNode.width;}
            else{newTarget=shown.get(at).target+shown.get(at).width-speedNode.width;for(int i=old+1;i<=at;i++)shown.get(i).delta=-speedNode.width;}
            speedNode.delta=newTarget-speedNode.target;
    }
    private static boolean mobile(String slot){return slot!=null&&(slot.equals("mobile")||slot.startsWith("mobile_")||slot.equals("stacked_mobile")||slot.startsWith("stacked_mobile_"));}
    private void replay(ViewGroup parent){try{if(updateStates!=null)updateStates.invoke(parent);}catch(ReflectiveOperationException|RuntimeException ignored) {}}
    public void detached(View view){
        Binding b=sources.remove(view);if(b==null&&view instanceof ViewGroup)b=containers.remove(view);
        if(b==null)return;ViewGroup parent=b.parent.get();if(parent!=null)containers.remove(parent);
        View phone=b.phone.get();if(phone!=null)phone.removeOnLayoutChangeListener(b.layout);
        View source=b.speed.get();if(source!=null)sources.remove(source);View clock=b.clock.get();if(clock!=null)clocks.remove(clock);
        removeCopy(b);if(parent!=null)parent.requestLayout();
    }
    public void releaseRuntime(){
        selection=new Selection(NATIVE);released=true;hooksReady=false;
        for(ViewGroup parent:new ArrayList<>(containers.keySet())){detached(parent);if(parent!=null)replay(parent);}
        containers.clear();sources.clear();copies.clear();clocks.clear();scopes.remove();
    }
}
