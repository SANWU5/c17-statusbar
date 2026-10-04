// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    public static final String SAFE_GAP_ENABLED="speed_safe_gap_enabled",SAFE_GAP="speed_safe_gap";
    public static final String NATIVE="native",CELLULAR_LEFT="cellular_left",RIGHT_START="right_start",
            CLOCK_LEFT="clock_left",CLOCK_RIGHT="clock_right";
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(POSITION,NATIVE);}
    public static Map<String,Boolean> booleanDefaults(){return Collections.singletonMap(SAFE_GAP_ENABLED,false);}
    public static Map<String,Float> numberDefaults(){return Collections.singletonMap(SAFE_GAP,2f);}
    public static String position(Object value){
        return CELLULAR_LEFT.equals(value)||RIGHT_START.equals(value)||CLOCK_LEFT.equals(value)||CLOCK_RIGHT.equals(value)
                ?(String)value:NATIVE;
    }
    /** A frozen selection, replaced only by a configuration event; no preference access during layout/draw. */
    private static final class Selection {
        final String position;final float offset,gap,scale;final boolean safeGap;
        Selection(String position){this(position,0f,0f,false,1f);}
        Selection(String position,float offset,float gap,boolean safeGap,float scale){this.position=position;this.offset=offset;this.gap=gap;this.safeGap=safeGap;this.scale=scale;}
        boolean clock(){return CLOCK_LEFT.equals(position)||CLOCK_RIGHT.equals(position);}
        boolean layout(){return !NATIVE.equals(position)||offset!=0f||safeGap;}
        boolean same(Selection other){return position.equals(other.position)&&offset==other.offset&&safeGap==other.safeGap&&(!safeGap||gap==other.gap)&&scale==other.scale;}
    }
    private static volatile Selection selection=new Selection(NATIVE);
    private static final class NetworkGeometry {
        final float wifiX,wifiScale,dataX,dataScale,wifiInkDp,dataInkDp;
        NetworkGeometry(){this(0f,1f,0f,1f,0f,0f);}
        NetworkGeometry(float wx,float ws,float dx,float ds,float wi,float di){wifiX=wx;wifiScale=ws;dataX=dx;dataScale=ds;wifiInkDp=wi;dataInkDp=di;}
        boolean same(NetworkGeometry other){return wifiX==other.wifiX&&wifiScale==other.wifiScale&&dataX==other.dataX&&dataScale==other.dataScale&&wifiInkDp==other.wifiInkDp&&dataInkDp==other.dataInkDp;}
    }
    private volatile NetworkGeometry network=new NetworkGeometry();
    private volatile boolean released;
    private boolean hooksReady,allocationReady,networkPosted;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable networkInvalidation=this::invalidateNetwork;
    private void invalidateNetwork(){
        synchronized(SpeedPosition.this){networkPosted=false;}
        if(released||!selection.safeGap)return;
        for(Binding b:new ArrayList<>(containers.values())){
            b.planDirty=true;ViewGroup parent=b.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
        }
    }
    private Class<?> nativeContainer,nativeSpeed,nativePhone,nativeState;
    private Method slot,visible,blocked,applyState,setVisible,setTint,setSlot,getX,setX,updateStates;
    private Field stateField,blockedField,stateVisible,ignored,iconSpacing;
    private int stateTag,clockId,clockContainerId,startSideId,layoutId;
    private final Map<ViewGroup,Binding> containers=new WeakHashMap<>();
    private final Map<View,Binding> sources=new WeakHashMap<>(),copies=new WeakHashMap<>();
    private final Map<View,Binding> clocks=new WeakHashMap<>();
    private final ThreadLocal<Scope> scopes=new ThreadLocal<>();
    private final ThreadLocal<ChildMeasureScope> childMeasurements=new ThreadLocal<>();
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
        slot=displayable.getMethod("getSlot");visible=displayable.getMethod("isIconVisible");blocked=displayable.getMethod("isIconBlocked");
        stateField=nativeSpeed.getField("mState");blockedField=nativeSpeed.getField("mBlocked");
        applyState=nativeSpeed.getMethod("applyNetworkState",speedState);
        setVisible=nativeSpeed.getMethod("setVisibleState",Integer.TYPE,Boolean.TYPE);
        setTint=nativeSpeed.getMethod("setIconTint",Integer.TYPE);setSlot=nativeSpeed.getMethod("setSlot",String.class);
        stateVisible=nativeState.getDeclaredField("visibleState");stateVisible.setAccessible(true);
        getX=viewState.getDeclaredMethod("getXTranslation");setX=viewState.getDeclaredMethod("setXTranslation",Float.TYPE);
        updateStates=nativeContainer.getDeclaredMethod("updateStates");updateStates.setAccessible(true);
        ignored=nativeContainer.getDeclaredField("mIgnoredSlots");ignored.setAccessible(true);
        // Current C17 DEX uses a literal zero between allocated slots; AOSP-derived variants
        // expose mIconSpacing. Do not require an absent field and disable every C17 position hook.
        try{iconSpacing=nativeContainer.getDeclaredField("mIconSpacing");iconSpacing.setAccessible(true);}
        catch(NoSuchFieldException zeroSpacingC17){iconSpacing=null;}
    }
    public boolean configure(Bundle b){
        boolean enabled=!released&&b!=null&&!SafetyMode.enabled(b)&&b.getBoolean("speed_enabled",false)
                &&b.getBoolean("speed_position_enabled",true);
        Selection next=enabled?new Selection(position(b.get(POSITION)),number(b,"speed_offset_x",0f),
                Math.max(0f,number(b,SAFE_GAP,2f)),b.getBoolean(SAFE_GAP_ENABLED,false),
                b.getBoolean("speed_size_enabled",true)?Math.max(0f,number(b,"speed_scale",100f))/100f:1f):new Selection(NATIVE);
        if(selection.same(next))return false;
        selection=next;
        for(Binding binding:new ArrayList<>(containers.values())){
            binding.dirty=true;refreshCopy(binding,true);
            ViewGroup parent=binding.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
        }
        return true;
    }
    /** Effective renderer settings, after feature/position/size gates. No saved offset is rewritten. */
    public synchronized boolean configureNetwork(float wifiXdp,float wifiScalePercent,float dataXdp,float dataScalePercent){
        NetworkGeometry next=new NetworkGeometry(NumericPolicy.finite(wifiXdp,0f),Math.max(0f,NumericPolicy.finite(wifiScalePercent,100f))/100f,
                NumericPolicy.finite(dataXdp,0f),Math.max(0f,NumericPolicy.finite(dataScalePercent,100f))/100f,network.wifiInkDp,network.dataInkDp);
        return networkGeometry(next);
    }
    /** Called only when the selected delegate changes. Width remains separate from its 20dp Compose frame. */
    public synchronized boolean networkDrawable(boolean wifi,float intrinsicWidthPx,float density){
        if(!(intrinsicWidthPx>0f)||!Float.isFinite(intrinsicWidthPx)||!(density>0f)||!Float.isFinite(density))return false;
        float dp=intrinsicWidthPx/density;
        if(!(dp>0f)||!Float.isFinite(dp))return false;
        return networkGeometry(new NetworkGeometry(network.wifiX,network.wifiScale,network.dataX,network.dataScale,
                wifi?dp:network.wifiInkDp,wifi?network.dataInkDp:dp));
    }
    private boolean networkGeometry(NetworkGeometry next){
        if(released||network.same(next))return false;network=next;
        if(!selection.safeGap)return false;
        // Resource/delegate creation can run off-main. Coalesce geometry changes, and keep
        // every View/native target access on the main thread rather than that loading thread.
        if(!networkPosted){networkPosted=true;main.post(networkInvalidation);}
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
    /** Requires both scoped getPaddingEnd and measureChild hooks in addition to state hooks. */
    public void setAllocationReady(boolean ready){
        if(allocationReady==ready)return;allocationReady=ready;
        for(Binding b:new ArrayList<>(containers.values())){
            b.planDirty=true;refreshCopy(b,true);
            ViewGroup parent=b.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
        }
    }
    /** Horizontal movement is now allocated by native layout; only vertical movement remains in draw. */
    public boolean handlesHorizontalOffset(View view){
        if(!hooksReady||!allocationReady||released||ModuleLifecycle.removed()||!selection.layout())return false;
        Binding b=sources.get(view);if(b==null)b=copies.get(view);
        return b!=null&&(!selection.clock()||b.mirrorReady);
    }
    /** Unsupported phone allocation stays native too; do not reintroduce an unallocated draw offset. */
    public float drawOffsetXdp(View view,float configured){
        return sources.containsKey(view)||copies.containsKey(view)?0f:configured;
    }
    /** A virtual allocation, never real View padding: no content, text size or OEM inset is modified. */
    public int allocationPaddingEnd(View child,int nativeValue){
        Scope scope=scopes.get();Binding b=sources.get(child);
        ChildMeasureScope measuring=childMeasurements.get();
        if(!allocationReady||scope==null||b==null||scope.binding!=b||b.samplingPadding||scope.policy.clock()
                ||measuring!=null&&measuring.binding==b)return nativeValue;
        return Math.min(65535,Math.max(0,nativeValue)+scope.reservation);
    }
    /**
     * C17 measures each icon before adding its width and padding to the row. Virtual allocation
     * belongs only to that outer sum, never FrameLayout's inner child measurement. Install around
     * ViewGroup.measureChild(View,int,int); its exact owner/source pair makes other UI a no-op.
     */
    public ChildMeasureScope childMeasurement(ViewGroup parent,View child){
        Scope scope=scopes.get();Binding b=sources.get(child);
        if(scope==null||scope.targets||b==null||scope.binding!=b||b.parent.get()!=parent)return null;
        ChildMeasureScope guard=new ChildMeasureScope(b,childMeasurements.get());childMeasurements.set(guard);return guard;
    }
    public final class ChildMeasureScope implements AutoCloseable {
        private Binding binding;private ChildMeasureScope previous;
        private ChildMeasureScope(Binding b,ChildMeasureScope previous){binding=b;this.previous=previous;}
        @Override public void close(){if(childMeasurements.get()==this){if(previous==null)childMeasurements.remove();else childMeasurements.set(previous);}binding=null;previous=null;}
    }
    private static float number(Bundle bundle,String key,float fallback){
        Object value=bundle.get(key);float result=value instanceof Number?((Number)value).floatValue():fallback;
        return Float.isNaN(result)||Float.isInfinite(result)?fallback:result;
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
        // A preference/layout recapture is not a new OEM icon. Preserve its measured
        // Wi-Fi-on budget, while a replaced view/state/slot gets a fresh baseline.
        Map<View,Node> previousNodes=new WeakHashMap<>();
        for(Node node:b.nodes){View child=node.child.get();if(child!=null)previousNodes.put(child,node);}
        b.nodes.clear();View speed=null;
        try{
            for(int i=0;i<parent.getChildCount();i++){
                View child=parent.getChildAt(i);String name=slot.getDeclaringClass().isInstance(child)?(String)slot.invoke(child):null;
                Object state=stateTag==0?null:child.getTag(stateTag);
                if(state!=null&&nativeState.isInstance(state)){
                    Node prior=previousNodes.get(child);
                    b.nodes.add(prior!=null&&prior.state.get()==state&&java.util.Objects.equals(prior.slot,name)?prior:new Node(child,state,name));
                }
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
        boolean dirty=true,planDirty=true,ownedPadding,mirrorReady,syncing,samplingPadding;
        int left,top,right,bottom,addedLeft,addedRight;
        int allocation,spacing,plannedWidth,plannedDirection;
        float wifiOnBudget,ghostWifi;int wifiOnGapCount;boolean gapFallbackReported;
        Object lastState;int lastTint=Integer.MIN_VALUE;
        final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->refreshCopy(this,false);
        Binding(ViewGroup parent,View phone){this.parent=new WeakReference<>(parent);this.phone=new WeakReference<>(phone);phone.addOnLayoutChangeListener(layout);}
    }
    private static final class Node {
        final WeakReference<View> child;final WeakReference<Object> state;final String slot;
        int index;
        float target,width,nativeWidth,delta,paintLeft,paintRight;int visibility;
        float wifiOnWidth,wifiOnNativeWidth;boolean wifiOnVisible;
        boolean latentWifi;
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
            if(!hooksReady||!allocationReady||released||ModuleLifecycle.removed()||!selection.clock()||source==null||host==null||clock==null||start==null
                    ||!source.isAttachedToWindow()||!host.isAttachedToWindow()||layoutId==0){removeCopy(b);return;}
            int width=source.getLayoutParams()==null?source.getMeasuredWidth():source.getLayoutParams().width;
            if(width<=0)width=source.getMeasuredWidth();
            float density=source.getResources().getDisplayMetrics().density;
            int offset=Math.round(NumericPolicy.pixels(selection.offset,density));
            int gap=selection.safeGap?Math.max(0,Math.round(NumericPolicy.pixels(selection.gap,density))):0;
            float enlarged=visualExtra(source,selection);
            if(enlarged>NumericPolicy.MAX_LAYOUT_PIXELS){removeCopy(b);return;}
            int extra=(int)Math.ceil(enlarged),reserve=width+extra+gap+Math.abs(offset);
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
            int leftMargin=left?-reserve+offset+(int)Math.ceil(enlarged/2f):0,
                    rightMargin=left?0:-reserve-offset+(int)Math.ceil(enlarged/2f),gravity=(left?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL;
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
        Selection policy=selection;
        if(!hooksReady||!allocationReady||released||ModuleLifecycle.removed()||!policy.layout())return null;
        Binding b=containers.get(parent);if(b==null)return null;
        if(b.dirty)capture(b);
        int reservation=reservation(b,policy);
        if(reservation<0)return null; // Unknown OEM allocation keeps every native target and width intact.
        if(b.allocation!=reservation){b.allocation=reservation;b.planDirty=true;if(targets)parent.requestLayout();}
        Scope scope=new Scope(b,targets,scopes.get(),policy,reservation);scopes.set(scope);return scope;
    }
    private int reservation(Binding b,Selection policy){
        if(policy.clock())return 0;
        View speed=b.speed.get();ViewGroup parent=b.parent.get();if(speed==null||parent==null||speed.getParent()!=parent)return -1;
        b.samplingPadding=true;
        try{
            Object ignoredSlots=ignored.get(parent);int spacing=iconSpacing==null?0:Math.max(0,iconSpacing.getInt(parent));
            if(spacing!=b.spacing){b.spacing=spacing;b.planDirty=true;}
            if(!eligible(speed,ignoredSlots))return 0;
            float hiddenWifi=0,mobileSpan=0,wifiSpan=0,safetyExtra=0,radioInkExtra=0;boolean knownSpeed=false,hasMobile=false,shownWifi=false;
            ArrayList<Node> visibleNodes=new ArrayList<>();Node speedNode=null;
            for(Node node:b.nodes){
                View child=node.child.get();if(child==null||child.getParent()!=parent)return -1;
                boolean shown=eligible(child,ignoredSlots);
                if(child==speed){knownSpeed=true;speedNode=node;}
                if(mobile(node.slot)&&shown){hasMobile=true;mobileSpan+=child.getWidth()+child.getPaddingStart()+child.getPaddingEnd()+spacing;}
                float nativeWidth=child.getWidth()+child.getPaddingStart()+child.getPaddingEnd();
                node.nativeWidth=nativeWidth;
                float oldLeft=node.paintLeft,oldRight=node.paintRight;
                extents(node,child,policy,network,child==speed);
                if(oldLeft!=node.paintLeft||oldRight!=node.paintRight)b.planDirty=true;
                float width=node.paintRight-node.paintLeft;
                if(width>NumericPolicy.MAX_LAYOUT_PIXELS)return -1;
                if(width!=node.width){node.width=width;b.planDirty=true;}
                if(shown){
                    visibleNodes.add(node);float extra=Math.max(0f,width-nativeWidth);safetyExtra+=extra;
                    if(wifi(node.slot)||mobile(node.slot))radioInkExtra+=extra;
                }
                if(wifi(node.slot)&&shown&&nativeWidth>0f){shownWifi=true;wifiSpan+=nativeWidth+spacing;}
                // Wi-Fi's native view remains registered while the radio is off. Keep its known
                // allocation, rather than letting an end-aligned container change physical width.
                boolean latent=wifi(node.slot)&&!shown&&nativeWidth>0;
                if(latent!=node.latentWifi){node.latentWifi=latent;b.planDirty=true;}
            }
            if(!knownSpeed||CELLULAR_LEFT.equals(policy.position)&&!hasMobile)return -1;
            float density=speed.getResources().getDisplayMetrics().density;
            float gap=policy.safeGap?Math.max(0,NumericPolicy.pixels(policy.gap,density)-spacing):0f;
            if(RIGHT_START.equals(policy.position)){visibleNodes.remove(speedNode);visibleNodes.add(0,speedNode);}
            else if(CELLULAR_LEFT.equals(policy.position)){
                visibleNodes.remove(speedNode);int index=0;while(index<visibleNodes.size()&&!mobile(visibleNodes.get(index).slot))index++;
                visibleNodes.add(index,speedNode);
            }
            int gapCount=0;for(int i=1;i<visibleNodes.size();i++)
                if(networkNode(visibleNodes.get(i-1),speedNode)||networkNode(visibleNodes.get(i),speedNode))gapCount++;
            // The custom 4G/5G label may replace the Wi-Fi slot's width when Wi-Fi goes away.
            // Reserve only the net missing network budget, including the safety boundaries
            // that disappeared with that slot; never reserve Wi-Fi again over a grown mobile.
            float radioBudget=mobileSpan+wifiSpan+(policy.safeGap?radioInkExtra+gap*gapCount:0f);
            if(shownWifi){
                // Store the native on-state geometry separately from enabled safety. Changing
                // the option while Wi-Fi is off must not preserve obsolete safety padding.
                b.wifiOnBudget=mobileSpan+wifiSpan;b.wifiOnGapCount=gapCount;
                for(Node node:b.nodes)if(wifi(node.slot)||mobile(node.slot)){
                    View child=node.child.get();node.wifiOnVisible=visibleNodes.contains(node);
                    node.wifiOnWidth=child.getWidth();node.wifiOnNativeWidth=node.nativeWidth;
                }
            }
            if(!shownWifi&&(RIGHT_START.equals(policy.position)||NATIVE.equals(policy.position))){
                float onBudget=b.wifiOnBudget;
                if(policy.safeGap){
                    onBudget+=gap*b.wifiOnGapCount;
                    for(Node node:b.nodes)if(node.wifiOnVisible){
                        View child=node.child.get();
                        onBudget+=radioExtra(node.wifiOnWidth,node.wifiOnNativeWidth,wifi(node.slot),
                                child.getResources().getDisplayMetrics().density,network);
                    }
                }
                hiddenWifi=Math.max(0f,onBudget-radioBudget);
            }
            if(hiddenWifi!=b.ghostWifi){b.ghostWifi=hiddenWifi;b.planDirty=true;}
            // A free horizontal offset is a draw/layout translation, not permission to resize
            // the native row or move Wi-Fi/mobile. Only the explicit safety option allocates it.
            float safety=policy.safeGap?gap*gapCount+safetyExtra+Math.abs(NumericPolicy.pixels(policy.offset,density)):0f;
            return Math.min(65535,Math.max(0,(int)Math.ceil(hiddenWifi+safety)));
        }catch(ReflectiveOperationException|RuntimeException unsupported){return -1;}
        finally{b.samplingPadding=false;}
    }
    private boolean eligible(View child,Object ignoredSlots)throws ReflectiveOperationException{
        return Boolean.TRUE.equals(visible.invoke(child))&&!Boolean.TRUE.equals(blocked.invoke(child))
                &&!(ignoredSlots instanceof java.util.Set&&((java.util.Set<?>)ignoredSlots).contains(slot.invoke(child)));
    }
    private static float visualExtra(View view,Selection policy){
        float scale=NumericPolicy.scale(policy.scale,Math.max(view.getWidth(),view.getHeight()));
        return Math.max(0f,view.getWidth()*(scale-1f));
    }
    private static boolean networkNode(Node node,Node speed){return node==speed||wifi(node.slot)||mobile(node.slot);}
    private static void extents(Node node,View view,Selection policy,NetworkGeometry network,boolean speed){
        node.paintLeft=0f;node.paintRight=node.nativeWidth;
        if(!policy.safeGap)return;
        if(speed){float extra=visualExtra(view,policy);node.paintLeft=-extra/2f;node.paintRight+=extra/2f;return;}
        boolean wifi=wifi(node.slot);if(!wifi&&!mobile(node.slot))return;
        float density=view.getResources().getDisplayMetrics().density;
        // C17 mobile host also contains independently-positioned labels. Signal is painted
        // around its 20dp frame centre, not the centre of that entire expanding host.
        float frame=networkFrame(view.getWidth(),density,wifi),half=networkHalf(frame,density,wifi,network);
        float offset=NumericPolicy.pixels(wifi?network.wifiX:network.dataX,density);
        node.paintLeft=Math.min(0f,frame/2f-half+offset);
        node.paintRight=Math.max(node.nativeWidth,frame/2f+half+offset);
    }
    private static float networkFrame(float width,float density,boolean wifi){return wifi?width:Math.min(width,NumericPolicy.layoutPixels(20f,density));}
    private static float networkHalf(float frame,float density,boolean wifi,NetworkGeometry network){
        float intrinsic=wifi?network.wifiInkDp:network.dataInkDp,ink=intrinsic>0f?NumericPolicy.layoutPixels(intrinsic,density):frame;
        return ink*NumericPolicy.scale(wifi?network.wifiScale:network.dataScale,ink)/2f;
    }
    private static float radioExtra(float width,float nativeWidth,boolean wifi,float density,NetworkGeometry network){
        float frame=networkFrame(width,density,wifi),half=networkHalf(frame,density,wifi,network),offset=NumericPolicy.pixels(wifi?network.wifiX:network.dataX,density);
        return Math.max(0f,Math.max(nativeWidth,frame/2f+half+offset)-Math.min(0f,frame/2f-half+offset)-nativeWidth);
    }
    public void beforeLayout(ViewGroup parent){Binding b=containers.get(parent);if(b!=null)b.planDirty=true;}
    public final class Scope implements AutoCloseable{
        private Binding binding;private final boolean targets;private Scope previous;
        private final Selection policy;private final int reservation;
        private float[] nativeTargets,deltas;
        private Scope(Binding b,boolean targets,Scope previous,Selection policy,int reservation){binding=b;this.targets=targets;this.previous=previous;this.policy=policy;this.reservation=reservation;}
        @Override public void close(){if(scopes.get()==this){if(previous==null)scopes.remove();else scopes.set(previous);}binding=null;previous=null;nativeTargets=null;deltas=null;}
    }
    /** All OEM targets exist before applyToView begins. Touch targets once per native layout, never child indexes. */
    public void beforeApply(Object state,View child){
        Scope scope=scopes.get();if(scope==null||!scope.targets||scope.policy.clock())return;
        Binding b=scope.binding;View speed=b.speed.get();if(speed==null||speed.getParent()!=b.parent.get())return;
        try{
            if(scope.nativeTargets==null){
                // This reads already-bound native state objects, not the view tree. Native updateStates
                // has reset every target immediately before this hook; retain that baseline for repeat/
                // nested applyToView calls so a persistent state can never accumulate our offset.
                int count=b.nodes.size();scope.nativeTargets=new float[count];scope.deltas=new float[count];
                for(int i=0;i<count;i++){
                    Node n=b.nodes.get(i);View v=n.child.get();Object s=n.state.get();if(v==null||s==null)return;
                    int visibility=stateVisible.getInt(s);
                    n.index=i;scope.nativeTargets[i]=((Number)getX.invoke(s)).floatValue();
                    if(visibility!=n.visibility||scope.nativeTargets[i]!=n.target)b.planDirty=true;
                }
                if(b.plannedWidth!=b.parent.get().getWidth()||b.plannedDirection!=b.parent.get().getLayoutDirection())b.planDirty=true;
                if(b.planDirty){plan(b,speed,scope);b.planDirty=false;}
                for(int i=0;i<count;i++)scope.deltas[i]=b.nodes.get(i).delta;
            }
            Node node=b.byView.get(child);
            if(node!=null&&node.state.get()==state&&node.index<scope.nativeTargets.length&&scope.deltas[node.index]!=0f&&stateVisible.getInt(state)==0)
                setX.invoke(state,scope.nativeTargets[node.index]+scope.deltas[node.index]);
        }catch(ReflectiveOperationException|RuntimeException unsupported){b.planDirty=true;}
    }
    private void plan(Binding b,View speed,Scope scope)throws ReflectiveOperationException{
            b.byView.clear();
            Node speedNode=null;ArrayList<Node> shown=new ArrayList<>();
            ViewGroup parent=b.parent.get();if(parent==null)return;
            b.plannedWidth=parent.getWidth();b.plannedDirection=parent.getLayoutDirection();
            for(Node node:b.nodes){
                View v=node.child.get();Object s=node.state.get();if(v==null||s==null||v.getParent()!=parent)return;
                node.delta=0f;b.byView.put(v,node);
                node.visibility=stateVisible.getInt(s);node.target=((Number)getX.invoke(s)).floatValue();
                if(node.visibility==0){shown.add(node);if(v==speed)speedNode=node;}
            }
            if(speedNode==null||speedNode.width<=0)return;
            shown.sort((a,c)->Float.compare(a.target,c.target));
            Selection policy=scope.policy;
            if(NATIVE.equals(policy.position)&&!policy.safeGap){
                // Preserve OEM targets exactly, including margins, privacy reservations and
                // independent Wi-Fi/mobile adjustments. A hidden Wi-Fi allocation stabilises
                // the speed's world position without repacking the remaining native icons.
                speedNode.delta=NumericPolicy.pixels(policy.offset,speed.getResources().getDisplayMetrics().density);
                return;
            }
            if(CELLULAR_LEFT.equals(policy.position)){
                shown.remove(speedNode);int mobile=-1;
                for(int i=0;i<shown.size();i++)if(mobile(shown.get(i).slot)){mobile=i;break;}
                if(mobile<0)return;shown.add(mobile,speedNode);
            }else if(RIGHT_START.equals(policy.position)){shown.remove(speedNode);shown.add(0,speedNode);}
            float density=speed.getResources().getDisplayMetrics().density;
            float gap=policy.safeGap?Math.max(b.spacing,NumericPolicy.pixels(policy.gap,density)):b.spacing;
            float[] desired=new float[shown.size()];int at=shown.indexOf(speedNode);
            float ghost=b.ghostWifi;
            boolean rtl=parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
            if(rtl){
                float cursor=parent.getPaddingEnd();
                for(int i=0;i<shown.size();i++){
                    Node node=shown.get(i);desired[i]=cursor-node.paintLeft;cursor+=node.width;
                    if(i+1<shown.size())cursor+=networkNode(node,speedNode)||networkNode(shown.get(i+1),speedNode)?gap:b.spacing;
                }
            }else{
                float cursor=parent.getWidth()-parent.getPaddingEnd()-ghost;
                for(int i=shown.size()-1;i>=0;i--){
                    Node node=shown.get(i);cursor-=node.width;desired[i]=cursor-node.paintLeft;
                    if(i>0)cursor-=networkNode(node,speedNode)||networkNode(shown.get(i-1),speedNode)?gap:b.spacing;
                }
            }
            // Real slots include the OEM inter-icon spacing. Move neighbours only when the
            // requested speed offset would consume that spacing; no draw-time overlap is hidden.
            desired[at]+=NumericPolicy.pixels(policy.offset,density);
            if(policy.safeGap){
                for(int i=at-1;i>=0;i--){
                    Node node=shown.get(i),next=shown.get(i+1);
                    float required=desired[i+1]+next.paintLeft-node.paintRight-(networkNode(node,speedNode)||networkNode(next,speedNode)?gap:b.spacing);
                    desired[i]=Math.min(desired[i],required);
                }
                for(int i=at+1;i<shown.size();i++){
                    Node node=shown.get(i),previous=shown.get(i-1);
                    float required=desired[i-1]+previous.paintRight-node.paintLeft+(networkNode(node,speedNode)||networkNode(previous,speedNode)?gap:b.spacing);
                    desired[i]=Math.max(desired[i],required);
                }
            }
            // Enabling the optional safety constraint must not push icons into the neighbouring
            // battery/cutout slot. Insufficient native space keeps the OEM allocation for this pass.
            if(policy.safeGap&&(desired[0]+shown.get(0).paintLeft<parent.getPaddingStart()
                    ||desired[desired.length-1]+shown.get(shown.size()-1).paintRight>parent.getWidth()-parent.getPaddingEnd())){
                // Preserve a requested offset even when the native cutout/overflow budget
                // cannot accommodate safety. Do not silently turn a saved position into zero.
                speedNode.delta=NumericPolicy.pixels(policy.offset,density);
                if(!b.gapFallbackReported){b.gapFallbackReported=true;ModuleDiagnostics.info("speed_position","安全间距空间不足，保留原生图标顺序与用户偏移");}
                return;
            }
            b.gapFallbackReported=false;
            for(int i=0;i<shown.size();i++){
                Node node=shown.get(i);node.delta=desired[i]-node.target;
            }
    }
    private static boolean wifi(String slot){return slot!=null&&(slot.equals("wifi")||slot.startsWith("wifi_"));}
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
        selection=new Selection(NATIVE);released=true;hooksReady=false;allocationReady=false;main.removeCallbacks(networkInvalidation);
        for(ViewGroup parent:new ArrayList<>(containers.keySet())){detached(parent);if(parent!=null)replay(parent);}
        containers.clear();sources.clear();copies.clear();clocks.clear();scopes.remove();childMeasurements.remove();
    }
}
