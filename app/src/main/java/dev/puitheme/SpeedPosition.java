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
    /**
     * The width kept free for the signal and Wi-Fi icons at the status bar's edge (dp). The speed sits where a full set of them would
     * leave it and keeps the room of the ones that are off, so the number depends on this setting and the edge, and on nothing else.
     */
    public static final String EDGE_RESERVE="speed_edge_reserve";
    public static final float DEFAULT_RESERVE=49f,MAX_RESERVE=160f;
    public static Map<String,Float> numberDefaults(){
        Map<String,Float> numbers=new java.util.LinkedHashMap<>();numbers.put(SAFE_GAP,2f);numbers.put(EDGE_RESERVE,DEFAULT_RESERVE);
        return Collections.unmodifiableMap(numbers);
    }
    public static String position(Object value){
        return CELLULAR_LEFT.equals(value)||RIGHT_START.equals(value)||CLOCK_LEFT.equals(value)||CLOCK_RIGHT.equals(value)
                ?(String)value:NATIVE;
    }
    /** A frozen selection, replaced only by a configuration event; no preference access during layout/draw. */
    private static final class Selection {
        final String position;final float offset,gap,scale,reserve;final boolean safeGap,positioning;
        Selection(String position){this(position,0f,0f,false,1f,0f,false);}
        Selection(String position,float offset,float gap,boolean safeGap,float scale,float reserve,boolean positioning){this.position=position;this.offset=offset;this.gap=gap;this.safeGap=safeGap;this.scale=scale;this.reserve=reserve;this.positioning=positioning;}
        boolean clock(){return CLOCK_LEFT.equals(position)||CLOCK_RIGHT.equals(position);}
        boolean layout(){return positioning||safeGap;}
        boolean same(Selection other){return position.equals(other.position)&&offset==other.offset&&safeGap==other.safeGap&&positioning==other.positioning&&(!safeGap||gap==other.gap)&&scale==other.scale&&reserve==other.reserve;}
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
            b.planDirty=true;refreshCopy(b,false);
            ViewGroup parent=b.parent.get();if(parent!=null){parent.requestLayout();replay(parent);}
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
    @FunctionalInterface public interface OrderReader {List<View> orderedChildren(ViewGroup parent);}
    private OrderReader orderReader;
    interface CopyFactory {View create(View source,FrameLayout parent,int layout)throws Exception;}
    private final CopyFactory copyFactory;
    public SpeedPosition(){this((source,parent,layout)->LayoutInflater.from(source.getContext()).inflate(layout,parent,false));}
    SpeedPosition(CopyFactory factory){copyFactory=factory;}

    /** Explicit layout input; resolving native hook order is the reader's responsibility. */
    public void setOrderReader(OrderReader reader){
        if(orderReader==reader)return;orderReader=reader;
        for(Binding b:new ArrayList<>(containers.values())){b.dirty=true;b.planDirty=true;}
    }

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
        boolean enabled=!released&&b!=null&&!SafetyMode.enabled(b)&&b.getBoolean("speed_enabled",false);
        boolean flow=StatusBarLayoutSettings.enabled(b);
        // The page's switch is "垂直位置调整": by itself it only moves the speed vertically.
        // Horizontal placement (and its radio reserve, which showed as an empty gap after
        // the speed while Wi-Fi or data was missing) applies only to an explicitly saved,
        // now hidden, legacy position or horizontal offset.
        boolean legacyPlacement=b!=null&&(!NATIVE.equals(position(b.get(POSITION)))||number(b,"speed_offset_x",0f)!=0f);
        boolean positioning=enabled&&!flow&&legacyPlacement&&b.getBoolean("speed_position_enabled",true);
        boolean gap=enabled&&!flow&&b.getBoolean(SAFE_GAP_ENABLED,false);
        Selection next=enabled?new Selection(positioning?position(b.get(POSITION)):NATIVE,
                positioning&&!gap?number(b,"speed_offset_x",0f):0f,
                number(b,SAFE_GAP,2f),gap,
                b.getBoolean("speed_size_enabled",true)?Math.max(0f,number(b,"speed_scale",100f))/100f:1f,
                Math.max(0f,Math.min(MAX_RESERVE,number(b,EDGE_RESERVE,DEFAULT_RESERVE))),positioning):new Selection(NATIVE);
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
        if(!nativeContainer.isInstance(parent))return;
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
        Binding b=sources.get(view);if(b==null)b=copies.get(view);
        return selection.safeGap||b!=null&&(!selection.clock()||b.mirrorReady)?0f:configured;
    }
    /** A virtual allocation, never real View padding: no content, text size or OEM inset is modified. */
    public int allocationPaddingEnd(View child,int nativeValue){
        Scope scope=scopes.get();Binding b=sources.get(child);
        ChildMeasureScope measuring=childMeasurements.get();
        if(!allocationReady||scope==null||b==null||scope.binding!=b||b.samplingPadding||scope.policy.clock()
                ||measuring!=null&&measuring.binding==b)return nativeValue;
        return Math.min(65535,nativeValue+scope.reservation);
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
        // Each native row owns its own relative frame, including QS and keyguard.
        // Only clock mirroring needs the audited PhoneStatusBarView clock host.
        View phone=phone(parent);
        Binding binding=containers.get(parent);
        // Children can be inflated before the row is inserted into a Phone or
        // panel. Rebind its scene owner and listener after that insertion.
        if(binding!=null&&binding.phone.get()!=phone){detached(parent);binding=null;}
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
        ViewGroup parent=b.parent.get();if(parent==null)return;
        List<View> children=readChildren(parent);if(children==null)return;
        capture(b,children);
    }
    private List<View> readChildren(ViewGroup parent){
        try{
            List<View> children;
            if(orderReader!=null)children=orderReader.orderedChildren(parent);
            else{children=new ArrayList<>(parent.getChildCount());for(int i=0;i<parent.getChildCount();i++)children.add(parent.getChildAt(i));}
            if(children==null||children.size()!=parent.getChildCount())return null;
            java.util.IdentityHashMap<View,Boolean> seen=new java.util.IdentityHashMap<>();
            for(View child:children)if(child==null||child.getParent()!=parent||seen.put(child,Boolean.TRUE)!=null)return null;
            return children;
        }catch(RuntimeException unsupported){return null;}
    }
    /** Only order/state identity invalidates a plan; sampling never requests another native pass. */
    private boolean refreshOrder(Binding b){
        if(b.dirty){capture(b);return !b.dirty;}
        if(orderReader==null)return true;
        ViewGroup parent=b.parent.get();if(parent==null)return false;
        List<View> children=readChildren(parent);if(children==null)return false;
        Map<View,Node> previous=new WeakHashMap<>();for(Node n:b.nodes){View v=n.child.get();if(v!=null)previous.put(v,n);}
        ArrayList<Node> current=new ArrayList<>();View speed=null;
        try{
            for(View child:children){
                String name=slot.getDeclaringClass().isInstance(child)?(String)slot.invoke(child):null;
                Object state=stateTag==0?null:child.getTag(stateTag);
                if(state!=null&&nativeState.isInstance(state)){
                    Node node=previous.get(child);
                    if(node==null||node.state.get()!=state||!java.util.Objects.equals(node.slot,name)){b.dirty=true;capture(b,children);return !b.dirty;}
                    current.add(node);
                }
                if(nativeSpeed.isInstance(child)){if(speed!=null){b.dirty=true;capture(b,children);return !b.dirty;}speed=child;}
            }
            if(speed!=b.speed.get()||current.size()!=b.nodes.size()){b.dirty=true;capture(b,children);return !b.dirty;}
            if(!current.equals(b.nodes)){b.nodes.clear();b.nodes.addAll(current);b.planDirty=true;}
            return true;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    private void capture(Binding b,List<View> children){
        ViewGroup parent=b.parent.get();View phone=b.phone.get();if(parent==null)return;
        if(stateTag==0){
            android.content.res.Resources r=parent.getResources();String pkg="com.android.systemui";
            stateTag=r.getIdentifier("status_bar_view_state_tag","id",pkg);
            clockId=r.getIdentifier("clock","id",pkg);clockContainerId=r.getIdentifier("clock_for_fake","id",pkg);
            startSideId=r.getIdentifier("status_bar_start_side_container","id",pkg);
            layoutId=r.getIdentifier("network_speed_view","layout",pkg);
        }
        // Preserve state identity across a preference/layout recapture. Radio geometry is
        // sampled anew; no first-state or widest-state position is remembered.
        Map<View,Node> previousNodes=new WeakHashMap<>();
        for(Node node:b.nodes){View child=node.child.get();if(child!=null)previousNodes.put(child,node);}
        b.nodes.clear();View speed=null;
        try{
            for(View child:children){
                String name=slot.getDeclaringClass().isInstance(child)?(String)slot.invoke(child):null;
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
        View clock=phone==null||clockId==0?null:phone.findViewById(clockId),host=phone==null||clockContainerId==0?null:phone.findViewById(clockContainerId);
        View start=phone==null||startSideId==0?null:phone.findViewById(startSideId);
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
        int allocation,spacing,plannedWidth,plannedDirection,plannedEndPadding,plannedRightPadding;
        Object lastState;int lastTint=Integer.MIN_VALUE;
        final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->refreshCopy(this,false);
        Binding(ViewGroup parent,View phone){this.parent=new WeakReference<>(parent);this.phone=new WeakReference<>(phone);(phone==null?parent:phone).addOnLayoutChangeListener(layout);}
    }
    private static final class Node {
        final WeakReference<View> child;final WeakReference<Object> state;final String slot;
        int index;
        float target,width,nativeWidth,delta,paintLeft,paintRight;int visibility;
        boolean shown;
        Node(View child,Object state,String slot){this.child=new WeakReference<>(child);this.state=new WeakReference<>(state);this.slot=slot;}
    }
    /** Only native state/configuration/layout callbacks call this method, never dispatchDraw/preDraw. */
    public void nativeUpdated(View source){Binding b=sources.get(source);if(b!=null){refreshOrder(b);refreshCopy(b,true);}}
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
            int gap=selection.safeGap?Math.round(NumericPolicy.pixels(selection.gap,density)):0;
            float enlarged=visualExtra(source,selection);
            if(enlarged>NumericPolicy.MAX_LAYOUT_PIXELS){removeCopy(b);return;}
            int extra=(int)Math.ceil(enlarged),reserve=Math.max(0,width+extra+gap+Math.abs(offset));
            float inkLeft=-enlarged/2f,inkRight=width+enlarged/2f;
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
            // Allocation moves the clock's host edge. Place the copy from that current edge,
            // not from the allocation again, or +/- offsets are cancelled/doubled.
            int leftMargin=left?-(int)Math.ceil(inkRight)+offset:0,
                    rightMargin=left?0:(int)Math.floor(inkLeft)-width-offset,gravity=(left?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL;
            if(selection.safeGap){
                leftMargin=left?-gap-(int)Math.ceil(inkRight):0;
                rightMargin=left?0:(int)Math.floor(inkLeft)-gap-width;
            }
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
        if(!refreshOrder(b))return null;
        if(policy.clock()&&!b.mirrorReady)return null;
        int reservation=reservation(b,policy);
        if(reservation==Integer.MIN_VALUE)return null; // Negative adjacent gaps are a signed virtual allocation.
        if(b.allocation!=reservation){b.allocation=reservation;b.planDirty=true;if(targets)parent.requestLayout();}
        Scope scope=new Scope(b,targets,scopes.get(),policy,reservation);scopes.set(scope);return scope;
    }
    private int reservation(Binding b,Selection policy){
        if(policy.clock())return 0;
        View speed=b.speed.get();ViewGroup parent=b.parent.get();if(speed==null||parent==null||speed.getParent()!=parent)return Integer.MIN_VALUE;
        b.samplingPadding=true;
        try{
            Object ignoredSlots=ignored.get(parent);int spacing=iconSpacing==null?0:Math.max(0,iconSpacing.getInt(parent));
            if(spacing!=b.spacing){b.spacing=spacing;b.planDirty=true;}
            if(!eligible(speed,ignoredSlots))return 0;
            boolean knownSpeed=false;Node speedNode=null;
            for(Node node:b.nodes){
                View child=node.child.get();if(child==null||child.getParent()!=parent)return Integer.MIN_VALUE;
                boolean shown=eligible(child,ignoredSlots);
                if(shown!=node.shown){node.shown=shown;b.planDirty=true;}
                if(child==speed){knownSpeed=true;speedNode=node;}
                float nativeWidth=child.getWidth()+child.getPaddingStart()+child.getPaddingEnd();
                if(nativeWidth!=node.nativeWidth){node.nativeWidth=nativeWidth;b.planDirty=true;}
                float oldLeft=node.paintLeft,oldRight=node.paintRight;
                extents(node,child,policy,network,child==speed);
                if(oldLeft!=node.paintLeft||oldRight!=node.paintRight)b.planDirty=true;
                float width=node.paintRight-node.paintLeft;
                if(width>NumericPolicy.MAX_LAYOUT_PIXELS)return Integer.MIN_VALUE;
                if(width!=node.width){node.width=width;b.planDirty=true;}
            }
            if(!knownSpeed)return Integer.MIN_VALUE;
            boolean rtl=parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
            ArrayList<Node> ordered=new ArrayList<>(b.nodes);
            if(rtl&&RIGHT_START.equals(policy.position))Collections.reverse(ordered);
            order(ordered,speedNode,policy.position);
            int at=ordered.indexOf(speedNode);
            // Reserve missing radio space for native measurement. A wider real radio keeps its
            // native width; placement below normalises its target to the fixed relative frame.
            // Never make the speed's native allocation negative to compensate a wide radio.
            float radioNow=radioWidth(ordered,at,spacing);
            float hold=Math.max(0f,NumericPolicy.pixels(policy.reserve,speed.getResources().getDisplayMetrics().density)-radioNow);
            if(policy.safeGap){
                if(speedNode.width<=0f)return 0;
                float gap=NumericPolicy.pixels(policy.gap,speed.getResources().getDisplayMetrics().density);
                // Hold a stable native-width envelope. Native number/unit setters already
                // request text layout; sampling never shrinks/grows this row's allocation.
                float scale=NumericPolicy.scale(policy.scale,Math.max(speed.getWidth(),speed.getHeight()));
                float envelopeLeft=speed.getWidth()*(1f-scale)/2f;
                float envelopeRight=speed.getWidth()*(1f+scale)/2f;
                float extra=envelopeRight-speedNode.nativeWidth-referenceInk(reference(ordered,at),speed.getResources().getDisplayMetrics().density)+gap-spacing+hold;
                if(at==0)extra+=Math.max(0f,-envelopeLeft);
                return Math.max(-(int)Math.max(0,speedNode.nativeWidth-1f),Math.min(65535,(int)Math.ceil(extra)));
            }
            return Math.min(65535,(int)Math.ceil(hold));
        }catch(ReflectiveOperationException|RuntimeException unsupported){return Integer.MIN_VALUE;}
        finally{b.samplingPadding=false;}
    }
    /**
     * The gap uses the configured signal frame, not the current signal-level drawable's
     * intrinsic width. A radio-state resource change must not move the user's anchor.
     */
    private float referenceInk(Node next,float density){
        if(next!=null&&!radio(next.slot))return next.paintLeft;
        NetworkGeometry geometry=network;
        float frame=NumericPolicy.layoutPixels(20f,density);
        return frame*(1f-NumericPolicy.scale(geometry.dataScale,frame))/2f+NumericPolicy.pixels(geometry.dataX,density);
    }
    /** Non-radio tail geometry survives radio view removal/recreation as well as hiding. */
    private static Node reference(List<Node> ordered,int at){
        for(int i=at+1;i<ordered.size();i++){
            Node node=ordered.get(i);if(!radio(node.slot)&&node.shown)return node;
        }
        return null;
    }
    /** Width (with spacing) of the radios currently shown right of the speed. */
    private static float radioWidth(List<Node> ordered,int at,int spacing){
        float now=0f;
        for(int i=at+1;i<ordered.size();i++){
            Node node=ordered.get(i);
            if(radio(node.slot)&&node.shown&&node.nativeWidth>0f)now+=node.nativeWidth+spacing;
        }
        return now;
    }
    private boolean eligible(View child,Object ignoredSlots)throws ReflectiveOperationException{
        return Boolean.TRUE.equals(visible.invoke(child))&&!Boolean.TRUE.equals(blocked.invoke(child))
                &&!(ignoredSlots instanceof java.util.Set&&((java.util.Set<?>)ignoredSlots).contains(slot.invoke(child)));
    }
    private static float visualExtra(View view,Selection policy){
        float scale=NumericPolicy.scale(policy.scale,Math.max(view.getWidth(),view.getHeight()));
        return Math.max(0f,view.getWidth()*(scale-1f));
    }
    private static void extents(Node node,View view,Selection policy,NetworkGeometry network,boolean speed){
        node.paintLeft=0f;node.paintRight=node.nativeWidth;
        if(!policy.safeGap)return;
        if(speed){
            // The native host box, scaled about its centre: the OEM text stays centred inside it, so the
            // digits never change this edge and the gap does not breathe with the number.
            float scale=NumericPolicy.scale(policy.scale,Math.max(view.getWidth(),view.getHeight())),center=view.getWidth()/2f;
            if(view.getWidth()<=0){node.paintLeft=node.paintRight=0f;return;}
            node.paintLeft=center-center*scale;node.paintRight=center+center*scale;return;
        }
        boolean wifi=wifi(node.slot);if(!wifi&&!mobile(node.slot))return;
        float density=view.getResources().getDisplayMetrics().density;
        // C17 mobile host also contains independently-positioned labels. Signal is painted
        // around its 20dp frame centre, not the centre of that entire expanding host.
        float frame=networkFrame(view.getWidth(),density,wifi),half=networkHalf(frame,density,wifi,network);
        float offset=NumericPolicy.pixels(wifi?network.wifiX:network.dataX,density);
        node.paintLeft=frame/2f-half+offset;
        node.paintRight=frame/2f+half+offset;
    }
    private static float networkFrame(float width,float density,boolean wifi){return wifi?width:Math.min(width,NumericPolicy.layoutPixels(20f,density));}
    private static float networkHalf(float frame,float density,boolean wifi,NetworkGeometry network){
        float intrinsic=wifi?network.wifiInkDp:network.dataInkDp,ink=intrinsic>0f?NumericPolicy.layoutPixels(intrinsic,density):frame;
        return ink*NumericPolicy.scale(wifi?network.wifiScale:network.dataScale,ink)/2f;
    }
    public void beforeLayout(ViewGroup parent){Binding b=containers.get(parent);if(b!=null)b.planDirty=true;}
    public final class Scope implements AutoCloseable{
        boolean prepared;
        private Binding binding;private final boolean targets;private Scope previous;
        private final Selection policy;private final int reservation;
        private float[] nativeTargets,deltas;
        private Scope(Binding b,boolean targets,Scope previous,Selection policy,int reservation){binding=b;this.targets=targets;this.previous=previous;this.policy=policy;this.reservation=reservation;}
        @Override public void close(){if(scopes.get()==this){if(previous==null)scopes.remove();else scopes.set(previous);}binding=null;previous=null;nativeTargets=null;deltas=null;}
    }
    /** Publish relative targets before native coordination reads them. Its animation
     * remains in charge of visibility, alpha and additional per-frame movement. */
    public void prepareTargets(ViewGroup parent){
        Scope scope=scopes.get();
        if(scope==null||!scope.targets||scope.prepared||scope.policy.clock()||scope.binding.parent.get()!=parent)return;
        // Inner icon-policy scopes are now active. Refresh their visibility before
        // choosing the non-radio tail; hidden hints must not become a false anchor.
        reservation(scope.binding,scope.policy);
        for(Node node:scope.binding.nodes){
            View child=node.child.get();Object state=node.state.get();
            if(child!=null&&state!=null)beforeApply(state,child);
        }
        scope.prepared=true;
    }
    /** All OEM targets exist before applyToView begins. Touch targets once per native layout, never child indexes. */
    public void beforeApply(Object state,View child){
        Scope scope=scopes.get();if(scope==null||!scope.targets||scope.prepared||scope.policy.clock())return;
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
                ViewGroup parent=b.parent.get();
                if(b.plannedWidth!=parent.getWidth()||b.plannedDirection!=parent.getLayoutDirection()
                        ||b.plannedEndPadding!=parent.getPaddingEnd()||b.plannedRightPadding!=parent.getPaddingRight())b.planDirty=true;
                if(b.planDirty){plan(b,speed,scope);b.planDirty=false;}
                for(int i=0;i<count;i++)scope.deltas[i]=b.nodes.get(i).delta;
            }
            Node node=b.byView.get(child);
            if(node!=null&&node.state.get()==state&&node.index<scope.nativeTargets.length&&scope.deltas[node.index]!=0f&&stateVisible.getInt(state)==0)
                setX.invoke(state,scope.nativeTargets[node.index]+scope.deltas[node.index]);
        }catch(ReflectiveOperationException|RuntimeException unsupported){b.planDirty=true;}
    }
    private void plan(Binding b,View speed,Scope scope)throws ReflectiveOperationException{
        b.byView.clear();Node speedNode=null;
        ViewGroup parent=b.parent.get();if(parent==null)return;
        b.plannedWidth=parent.getWidth();b.plannedDirection=parent.getLayoutDirection();
        b.plannedEndPadding=parent.getPaddingEnd();b.plannedRightPadding=parent.getPaddingRight();
        for(Node node:b.nodes){
            View v=node.child.get();Object state=node.state.get();if(v==null||state==null||v.getParent()!=parent)return;
            node.delta=0f;b.byView.put(v,node);
            node.visibility=stateVisible.getInt(state);node.target=((Number)getX.invoke(state)).floatValue();
            if(v==speed)speedNode=node;
        }
        if(speedNode==null||!speedNode.shown||speedNode.visibility!=0||speedNode.width<=0f)return;
        Selection policy=scope.policy;
        boolean rtl=parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        // Set the local target before native coordination/animation. Do not infer an
        // animation displacement from a replay of slot widths: other scoped icon
        // policies may have excluded slots from the native allocation.
        ArrayList<Node> ordered=new ArrayList<>(b.nodes);
        boolean start=RIGHT_START.equals(policy.position);
        // "Right-side area leftmost" stays physical left in RTL too. Other selections
        // follow the native logical slot and mirror once, just like StatusIconContainer.
        if(rtl&&start)Collections.reverse(ordered);
        order(ordered,speedNode,policy.position);
        int at=ordered.indexOf(speedNode);
        float tail=0f;
        for(int i=at+1;i<ordered.size();i++){
            Node node=ordered.get(i);
            // Native DOT/HIDDEN is an animation/occlusion result. Keep the model's
            // allocated anchor while it animates; scoped blocking is already sampled.
            if(node.shown&&!radio(node.slot))tail+=node.nativeWidth+b.spacing;
        }
        float density=speed.getResources().getDisplayMetrics().density;
        float edge=parent.getWidth()-(start?parent.getPaddingRight():parent.getPaddingEnd())-tail-NumericPolicy.pixels(policy.reserve,density);
        Node anchor=reference(ordered,at);
        float desired=policy.safeGap
                ?edge+b.spacing+referenceInk(anchor,density)-NumericPolicy.pixels(policy.gap,density)-(float)Math.ceil(speedNode.paintRight)
                :edge-speedNode.nativeWidth;
        if(rtl&&!start)desired=parent.getWidth()-desired-speed.getWidth();
        if(!policy.safeGap)desired+=NumericPolicy.pixels(policy.offset,density);
        speedNode.delta=desired-speedNode.target;
        if(start){
            // Move the native speed slot to the selected end of the row without leaving
            // its IconManager parent. Real siblings use their real widths, not ghost space.
            float cursor=parent.getWidth()-parent.getPaddingRight();boolean previous=false;
            for(int i=ordered.size()-1;i>=0;i--){
                Node node=ordered.get(i);if(node==speedNode||!node.shown||node.visibility!=0)continue;
                if(previous)cursor-=b.spacing;previous=true;
                cursor-=node.nativeWidth;node.delta=cursor-node.target;
            }
        }
    }
    private static void order(List<Node> nodes,Node speed,String position){
        if(speed!=null&&RIGHT_START.equals(position)){nodes.remove(speed);nodes.add(0,speed);}
    }
    private static boolean radio(String slot){return wifi(slot)||mobile(slot);}
    private static boolean wifi(String slot){return slot!=null&&(slot.equals("wifi")||slot.startsWith("wifi_"));}
    private static boolean mobile(String slot){return slot!=null&&(slot.equals("mobile")||slot.startsWith("mobile_")||slot.equals("stacked_mobile")||slot.startsWith("stacked_mobile_"));}
    private void replay(ViewGroup parent){try{if(updateStates!=null)updateStates.invoke(parent);}catch(ReflectiveOperationException|RuntimeException ignored) {}}
    public void detached(View view){
        Binding b=sources.remove(view);if(b==null&&view instanceof ViewGroup)b=containers.remove(view);
        if(b==null)return;ViewGroup parent=b.parent.get();if(parent!=null)containers.remove(parent);
        View phone=b.phone.get();View listener=phone==null?parent:phone;if(listener!=null)listener.removeOnLayoutChangeListener(b.layout);
        View source=b.speed.get();if(source!=null)sources.remove(source);View clock=b.clock.get();if(clock!=null)clocks.remove(clock);
        removeCopy(b);if(parent!=null)parent.requestLayout();
    }
    public void releaseRuntime(){
        selection=new Selection(NATIVE);released=true;hooksReady=false;allocationReady=false;main.removeCallbacks(networkInvalidation);
        for(ViewGroup parent:new ArrayList<>(containers.keySet())){detached(parent);if(parent!=null)replay(parent);}
        containers.clear();sources.clear();copies.clear();clocks.clear();scopes.remove();childMeasurements.remove();
        orderReader=null;
    }
}
