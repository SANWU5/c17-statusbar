// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Native system hints only; notification icons and network/battery layout remain owned by SystemUI. */
public final class NativeStatusIcons {
    public static final String MASTER="status_hint_icons_enabled";
    public static final String MAX="status_hint_icons_max";
    public static final String SPACING="status_hint_icons_spacing";
    public static final String X="status_hint_icons_x",Y="status_hint_icons_y";
    public static final String PRIORITY="status_hint_icons_priority";
    public static final String[] PRIORITY_SLOTS={"bluetooth","location","alarm_clock","zen","volume","hotspot","headset","rotate","vpn","nfc","cast","screen_record","microphone","camera","privacy_call","usb","hd","airplane"};
    public static final String[] PRIORITY_LABELS={"蓝牙","定位","闹钟","勿扰","静音与振动","个人热点","耳机","屏幕旋转","VPN","NFC","投屏","屏幕录制","麦克风","相机","通话隐私","USB","高清通话","飞行模式"};
    /** UI uses names only; persisted slot IDs never become an editable code field. */
    public static List<String> editablePriority(String stored) {
        ArrayList<String> result=new ArrayList<>();
        List<String> available=Arrays.asList(PRIORITY_SLOTS);
        for(String slot:parsePriority(stored))if(available.contains(slot))result.add(slot);
        for(String slot:PRIORITY_SLOTS)if(!result.contains(slot))result.add(slot);
        return result;
    }
    public static String priorityLabel(String slot){
        for(int i=0;i<PRIORITY_SLOTS.length;i++)if(PRIORITY_SLOTS[i].equals(slot))return PRIORITY_LABELS[i];
        return "其他系统提示";
    }
    public static Map<String,Boolean> booleanDefaults(){return Collections.singletonMap(MASTER,false);}
    public static Map<String,Float> floatDefaults(){
        LinkedHashMap<String,Float> values=new LinkedHashMap<>();
        values.put(MAX,14f);values.put(SPACING,0f);values.put(X,0f);values.put(Y,0f);
        return Collections.unmodifiableMap(values);
    }
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(PRIORITY,"");}
    private Settings settings=new Settings(null);
    private boolean released;
    private int revision;
    private Class<?> nativeContainer,nativeIcon,nativeState,displayable;
    private Method slot,visible,blocked,notification,getX,setX;
    private Field ignored,stateVisible;
    private final Map<ViewGroup,Container> containers=new WeakHashMap<>();
    private final Map<View,Node> nodes=new WeakHashMap<>();
    private final ThreadLocal<Scope> scopes=new ThreadLocal<>();
    private final ThreadLocal<Boolean> readingNative=new ThreadLocal<>();

    private static final class Settings {
        final boolean enabled;
        final int max;
        final float spacing,x,y;
        final List<String> priority;
        Settings(Bundle b){
            enabled=b!=null&&b.getBoolean(MASTER,false)&&!SafetyMode.enabled(b);
            float count=number(b,MAX,14f);
            max=count<=0f?0:count>=Integer.MAX_VALUE?Integer.MAX_VALUE:(int)count;
            spacing=number(b,SPACING,0f);x=number(b,X,0f);y=number(b,Y,0f);
            Object source=b==null?null:b.get(PRIORITY);
            priority=parsePriority(source instanceof String?(String)source:"");
        }
        boolean same(Settings b){return enabled==b.enabled&&max==b.max&&spacing==b.spacing&&x==b.x&&y==b.y&&priority.equals(b.priority);}
    }
    private static float number(Bundle b,String key,float fallback){
        Object value=b==null?null:b.get(key);
        if(!(value instanceof Number))return fallback;
        float result=((Number)value).floatValue();return Float.isNaN(result)||Float.isInfinite(result)?fallback:result;
    }
    /** A slot list is data, never a class name or executable expression. */
    static List<String> parsePriority(String value){
        if(value==null||value.trim().isEmpty())return Collections.emptyList();
        String bounded=value.length()>2048?value.substring(0,2048):value;
        ArrayList<String> result=new ArrayList<>();
        for(String token:bounded.split("[,，;；\\s]+")){
            if(result.size()>=64)break;
            String translated=alias(token);
            for(String name:translated.split(",")){
                if(result.size()>=64)break;
                if(!name.matches("[A-Za-z0-9_]{1,64}")||excludedSlot(name)||result.contains(name))continue;
                result.add(name);
            }
        }
        return Collections.unmodifiableList(result);
    }
    private static String alias(String name){
        switch(name){
            case "蓝牙":return "bluetooth";case "定位":return "location";case "闹钟":return "alarm_clock";
            case "勿扰":return "zen";case "热点":return "hotspot";case "耳机":return "headset";
            case "静音":return "volume";case "麦克风":return "microphone";case "相机":return "camera";
            case "隐私":return "microphone,camera,privacy_call";default:return name;
        }
    }
    static boolean excludedSlot(String name){
        if(name==null||name.isEmpty())return true;
        for(String prefix:new String[]{"wifi","mobile","stacked_mobile","battery","network_type","network_speed",
                "netspeed","net_speed","internet_speed","data_connection","clock","time"})
            if(name.equals(prefix)||name.startsWith(prefix+"_"))return true;
        return name.equals("data")||name.equals("network")||name.equals("speed");
    }
    public void resolve(ClassLoader loader)throws ReflectiveOperationException{
        nativeContainer=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
        nativeIcon=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
        nativeState=loader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer$StatusIconState");
        displayable=loader.loadClass("com.android.systemui.statusbar.StatusIconDisplayable");
        slot=displayable.getMethod("getSlot");visible=displayable.getMethod("isIconVisible");blocked=displayable.getMethod("isIconBlocked");
        notification=nativeIcon.getMethod("getNotification");
        ignored=nativeContainer.getDeclaredField("mIgnoredSlots");ignored.setAccessible(true);
        Class<?> state=loader.loadClass("com.android.systemui.statusbar.notification.stack.ViewState");
        getX=state.getDeclaredMethod("getXTranslation");getX.setAccessible(true);
        setX=state.getDeclaredMethod("setXTranslation",Float.TYPE);setX.setAccessible(true);
        stateVisible=nativeState.getDeclaredField("visibleState");stateVisible.setAccessible(true);
    }
    public boolean configure(Bundle b){
        if(released)return false;
        Settings next=new Settings(b);if(settings.same(next))return false;
        settings=next;revision++;
        for(Map.Entry<ViewGroup,Container> entry:containers.entrySet()){
            entry.getValue().dirty=true;ViewGroup parent=entry.getKey();
            if(parent!=null){restoreY(parent);parent.requestLayout();}
        }
        return true;
    }
    public boolean enabled(){return !released&&settings.enabled&&!ModuleLifecycle.removed();}
    public void changed(ViewGroup parent){
        if(released||parent==null||nativeContainer==null||!nativeContainer.isInstance(parent))return;
        Container saved=containers.get(parent);
        if(saved==null)containers.put(parent,new Container());else saved.dirty=true;
    }
    /** Call after native set/icon binding, additions/removals and configuration changes. */
    public void bound(View child){if(child!=null&&child.getParent() instanceof ViewGroup)changed((ViewGroup)child.getParent());}
    private static final class Node {
        final WeakReference<ViewGroup> parent;
        final int index;
        final String name;
        final boolean hint;
        int nativeTop,appliedTop;
        boolean ownsTop;
        Node(ViewGroup parent,int index,String name,boolean hint){this.parent=new WeakReference<>(parent);this.index=index;this.name=name;this.hint=hint;}
    }
    private static final class Plan {
        final int revision;
        final int[] order;
        final boolean[] visible,selected;
        final boolean compatible;
        Plan(int revision,int[] order,boolean[] visible,boolean[] selected,boolean compatible){
            this.revision=revision;this.order=order;this.visible=visible;this.selected=selected;this.compatible=compatible;
        }
    }
    private static final class Container {
        boolean dirty=true;
        int count,tag;
        final List<WeakReference<View>> children=new ArrayList<>();
        Node[] metadata=new Node[0];
        boolean[] scratch=new boolean[0];
        Object[] targetBuffer=new Object[0];
        float[] positionBuffer=new float[0],desiredBuffer=new float[0];
        int[] widthBuffer=new int[0];
        Plan plan;
        boolean valid(ViewGroup parent){
            if(count!=parent.getChildCount())return false;
            for(WeakReference<View> ref:children){View child=ref.get();if(child==null||child.getParent()!=parent)return false;}
            return true;
        }
    }
    private Container container(ViewGroup parent)throws ReflectiveOperationException{
        Container saved=containers.get(parent);if(saved==null){saved=new Container();containers.put(parent,saved);}
        if(saved.dirty||!saved.valid(parent)){
            restoreY(parent);
            for(WeakReference<View> ref:saved.children){View child=ref.get();Node node=child==null?null:nodes.get(child);if(node!=null&&node.parent.get()==parent)nodes.remove(child);}
            saved.children.clear();saved.count=parent.getChildCount();
            saved.metadata=new Node[saved.count];saved.scratch=new boolean[saved.count];saved.plan=null;
            saved.targetBuffer=new Object[saved.count];saved.positionBuffer=new float[saved.count];
            saved.desiredBuffer=new float[saved.count];saved.widthBuffer=new int[saved.count];
            for(int i=0;i<saved.count;i++){
                View child=parent.getChildAt(i);saved.children.add(new WeakReference<>(child));
                String name=displayable.isInstance(child)?(String)slot.invoke(child):null;
                boolean hint=nativeIcon.isInstance(child)&&notification.invoke(child)==null&&!excludedSlot(name);
                Node node=new Node(parent,i,name,hint);saved.metadata[i]=node;nodes.put(child,node);
            }
            saved.tag=parent.getResources().getIdentifier("status_bar_view_state_tag","id","com.android.systemui");
            saved.dirty=false;
        }
        return saved;
    }
    private Plan plan(ViewGroup parent,Container saved)throws ReflectiveOperationException{
        Object source=ignored.get(parent);if(!(source instanceof Set))return null;
        Set<?> hidden=(Set<?>)source;
        for(int i=0;i<saved.count;i++){
            View child=saved.children.get(i).get();Node node=saved.metadata[i];
            boolean shown=child!=null&&displayable.isInstance(child)&&child.getVisibility()!=View.GONE
                    &&Boolean.TRUE.equals(visible.invoke(child))&&!Boolean.TRUE.equals(blocked.invoke(child))&&!hidden.contains(node.name);
            saved.scratch[i]=shown;
        }
        Plan current=saved.plan;
        if(current!=null&&current.revision==revision&&Arrays.equals(current.visible,saved.scratch))return current;
        int[] mapped=new int[saved.count];for(int i=0;i<mapped.length;i++)mapped[i]=i;
        boolean[] selected=new boolean[saved.count];
        {
            ArrayList<Integer> candidates=new ArrayList<>();
            for(int i=0;i<saved.count;i++)if(saved.metadata[i].hint)candidates.add(i);
            ArrayList<Integer> priority=new ArrayList<>();
            for(String name:settings.priority)for(int index:candidates)if(saved.metadata[index].name.equals(name)&&!priority.contains(index))priority.add(index);
            for(int index:candidates)if(!priority.contains(index))priority.add(index);
            int retained=0;
            for(int index:priority)if(saved.scratch[index]&&retained<settings.max){selected[index]=true;retained++;}
            // ColorOS config_statusBarIcons interleaves network_speed/mobile/wifi with
            // zen/privacy/etc. Keep those native barriers, rather than rejecting the row.
            for(int first=0;first<saved.count;){
                if(!saved.metadata[first].hint){first++;continue;}
                int end=first+1;while(end<saved.count&&saved.metadata[end].hint)end++;
                int output=first;
                for(int index:priority)if(index>=first&&index<end)mapped[output++]=index;
                first=end;
            }
        }
        saved.plan=new Plan(revision,mapped,saved.scratch.clone(),selected,true);return saved.plan;
    }
    /** Scope only the inspected onMeasure/updateStates. No draw/preDraw scans or persistent View flags. */
    public Scope begin(ViewGroup parent,boolean calculatingTargets){
        if(released||parent==null||nativeContainer==null||!nativeContainer.isInstance(parent))return null;
        if(!enabled()){changed(parent);return null;}
        Boolean prior=readingNative.get();readingNative.set(Boolean.TRUE);
        try{
            Container saved=container(parent);Plan plan=plan(parent,saved);
            if(plan==null||!plan.compatible)return null;
            Scope scope=new Scope(parent,saved,plan,calculatingTargets,settings,scopes.get());scopes.set(scope);return scope;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return null;}
        finally{if(prior==null)readingNative.remove();else readingNative.set(prior);}
    }
    public final class Scope implements AutoCloseable {
        private ViewGroup parent;
        private Scope previous;
        private final Container container;
        private final Plan plan;
        private final Settings settings;
        private final boolean targets;
        private boolean transformed;
        private Scope(ViewGroup parent,Container container,Plan plan,boolean targets,Settings settings,Scope previous){
            this.parent=parent;this.container=container;this.plan=plan;this.targets=targets;this.settings=settings;this.previous=previous;
        }
        @Override public void close(){
            if(scopes.get()==this){if(previous==null)scopes.remove();else scopes.set(previous);}
            parent=null;previous=null;
        }
    }
    public int childIndex(Object parent,int nativeIndex){
        Scope scope=scopes.get();return !Boolean.TRUE.equals(readingNative.get())&&enabled()&&scope!=null&&scope.parent==parent
                &&nativeIndex>=0&&nativeIndex<scope.plan.order.length?scope.plan.order[nativeIndex]:nativeIndex;
    }
    /** Hook only native StatusBarIconView.isIconBlocked; read real priority before applying the count. */
    public boolean iconBlocked(View child,boolean nativeValue){
        if(nativeValue||Boolean.TRUE.equals(readingNative.get())||!enabled())return nativeValue;
        Scope scope=scopes.get();Node node=nodes.get(child);
        return scope!=null&&node!=null&&node.hint&&node.parent.get()==scope.parent&&!scope.plan.selected[node.index];
    }
    /** Immediately before the exact native StatusIconState.applyToView. Native animation still runs once. */
    public void beforeApply(Object state,View child){
        Scope scope=scopes.get();
        if(!enabled()||scope==null||!scope.targets||child==null||child.getParent()!=scope.parent
                ||nativeState==null||!nativeState.isInstance(state))return;
        Node node=nodes.get(child);
        if(node!=null&&node.hint&&!scope.plan.selected[node.index]) {
            try{stateVisible.setInt(state,2);}catch(ReflectiveOperationException|RuntimeException unsupported){ }
        }
        if(scope.transformed)return;
        scope.transformed=true;
        if(scope.settings.spacing==0f&&scope.settings.x==0f&&scope.settings.priority.isEmpty())return;
        try{transform(scope);}catch(ReflectiveOperationException|RuntimeException unsupported){/* Native target remains. */}
    }
    private void transform(Scope scope)throws ReflectiveOperationException{
        Container saved=scope.container;ViewGroup parent=scope.parent;if(saved.tag==0||!saved.valid(parent))return;
        boolean rtl=parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        float density=parent.getResources().getDisplayMetrics().density;
        if(!Float.isFinite(density)||density<=0f)return;
        float gap=NumericPolicy.pixels(scope.settings.spacing,density);
        float offset=NumericPolicy.pixels(rtl?-scope.settings.x:scope.settings.x,density);
        int count=0;
        try {
            // Targets are complete here. Reorder their coordinates directly; OEM inherited
            // getChildAt/interface calls may have been inlined despite the index hooks.
            for(int first=0;first<saved.count;) {
                if(!saved.metadata[first].hint){first++;continue;}
                int end=first+1;while(end<saved.count&&saved.metadata[end].hint)end++;
                int begin=count;float left=Float.MAX_VALUE,right=-Float.MAX_VALUE,sumWidth=0f;
                for(int slot=first;slot<end;slot++) {
                    int physical=scope.plan.order[slot];
                    if(!scope.plan.visible[physical]||!scope.plan.selected[physical])continue;
                    View child=saved.children.get(physical).get();if(child==null)continue;
                    Object target=child.getTag(saved.tag);if(!nativeState.isInstance(target))continue;
                    if(stateVisible.getInt(target)!=0)continue; // Native blocked/overflow stays hidden.
                    float x=((Number)getX.invoke(target)).floatValue();if(!Float.isFinite(x))continue;
                    float logical=rtl?parent.getWidth()-x-child.getWidth():x;
                    saved.targetBuffer[count]=target;saved.positionBuffer[count]=logical;
                    saved.widthBuffer[count]=child.getWidth();count++;
                    left=Math.min(left,logical);right=Math.max(right,logical+child.getWidth());sumWidth+=child.getWidth();
                }
                int n=count-begin;
                if(n>0) {
                    float nativeGap=n>1?Math.max(0f,(right-left-sumWidth)/(n-1)):0f;
                    float requestedGap=Math.max(0f,nativeGap+gap);
                    float x=NumericPolicy.pixels((double)right-sumWidth-requestedGap*Math.max(0,n-1)+offset);
                    for(int i=begin;i<count;i++) {
                        saved.desiredBuffer[i]=x;
                        x=NumericPolicy.pixels((double)x+saved.widthBuffer[i]+requestedGap);
                    }
                }
                first=end;
            }
            // No network/battery target is changed. Existing unclipped OEM icon hosts
            // permit small horizontal translations outside their tight WRAP_CONTENT width.
            for(int i=0;i<count;i++) if(saved.desiredBuffer[i]!=saved.positionBuffer[i])
                setX.invoke(saved.targetBuffer[i],rtl?parent.getWidth()-saved.desiredBuffer[i]-saved.widthBuffer[i]:saved.desiredBuffer[i]);
        } finally {Arrays.fill(saved.targetBuffer,0,count,null);}
    }
    /** Native layout writes top each time. Keep its Y animation independent from this static offset. */
    public void onLayout(ViewGroup parent){
        if(parent==null||nativeContainer==null||!nativeContainer.isInstance(parent))return;
        // onLayout is also retained in safe mode. Register a native host so a later
        // enable can request its layout even if onMeasure was bypassed at safe boot.
        if(!enabled()){changed(parent);restoreY(parent);return;}
        Boolean prior=readingNative.get();readingNative.set(Boolean.TRUE);
        try{
            // This callback is AFTER the OEM has laid out every child, so the new top is native.
            Container old=containers.get(parent);
            if(old!=null)for(Node node:old.metadata)if(node!=null)node.ownsTop=false;
            if(settings.y==0f)return; // Native layout already reset top; no ancestor geometry work is needed.
            Container saved=container(parent);Plan plan=plan(parent,saved);if(plan==null||!plan.compatible)return;
            float density=parent.getResources().getDisplayMetrics().density;
            if(!Float.isFinite(density)||density<=0f)return;
            float[] vertical=verticalBounds(parent);
            for(int i=0;i<saved.count;i++){
                View child=saved.children.get(i).get();Node node=saved.metadata[i];
                if(child==null||!node.hint||!plan.selected[i])continue;
                int nativeTop=child.getTop();float nativeY=child.getTranslationY();
                float low=vertical[0]-nativeTop-nativeY;
                float high=vertical[1]-child.getHeight()-nativeTop-nativeY;
                if(high<low)continue;
                float wanted=settings.y*density;int delta=Math.round(Math.max(low,Math.min(wanted,high)));
                node.nativeTop=nativeTop;node.appliedTop=nativeTop+delta;node.ownsTop=delta!=0;
                if(delta!=0)child.offsetTopAndBottom(delta);
            }
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Native layout remains. */}
        finally{if(prior==null)readingNative.remove();else readingNative.set(prior);}
    }
    /** system_icons.xml explicitly disables clipping on both nested native containers.
     * A WRAP_CONTENT host can be exactly one icon high, without defining its drawable
     * limit. Read the existing ancestor clip instead; never change clipping/window size. */
    private static float[] verticalBounds(ViewGroup parent){
        float fallbackTop=parent.getPaddingTop(),fallbackBottom=parent.getHeight()-parent.getPaddingBottom();
        float top=-Float.MAX_VALUE,bottom=Float.MAX_VALUE;
        int[] origin=new int[2],location=new int[2];parent.getLocationOnScreen(origin);
        float[] matrixValues=null;
        View current=parent;
        for(int depth=0;depth<16&&current!=null;depth++){
            if(!current.getMatrix().isIdentity()){
                if(matrixValues==null)matrixValues=new float[9];current.getMatrix().getValues(matrixValues);
                if(matrixValues[0]!=1f||matrixValues[4]!=1f||matrixValues[1]!=0f||matrixValues[3]!=0f
                        ||matrixValues[6]!=0f||matrixValues[7]!=0f||matrixValues[8]!=1f)return new float[]{fallbackTop,fallbackBottom};
            }
            current.getLocationOnScreen(location);float y=(float)location[1]-origin[1];
            Rect clip=current.getClipBounds();
            if(clip!=null){top=Math.max(top,y+clip.top);bottom=Math.min(bottom,y+clip.bottom);}
            boolean root=!(current.getParent() instanceof View);
            if(current instanceof ViewGroup){
                ViewGroup group=(ViewGroup)current;
                if(group.getClipChildren()||group.getClipToPadding()||group.getClipToOutline()||root){
                    float insetTop=group.getClipToPadding()?group.getPaddingTop():0;
                    float insetBottom=group.getClipToPadding()?group.getPaddingBottom():0;
                    top=Math.max(top,y+insetTop);bottom=Math.min(bottom,y+group.getHeight()-insetBottom);
                }
            }else if(current.getClipToOutline()||root){top=Math.max(top,y);bottom=Math.min(bottom,y+current.getHeight());}
            if(root)break;
            current=(View)current.getParent();
        }
        if(top==-Float.MAX_VALUE||bottom==Float.MAX_VALUE||!Float.isFinite(top)||!Float.isFinite(bottom))
            return new float[]{fallbackTop,fallbackBottom};
        return new float[]{top,bottom};
    }
    private void restoreY(ViewGroup parent){
        Container saved=containers.get(parent);if(saved==null)return;
        for(int i=0;i<saved.children.size();i++){
            View child=saved.children.get(i).get();Node node=i<saved.metadata.length?saved.metadata[i]:null;
            if(child==null||node==null||!node.ownsTop)continue;
            if(child.getParent()==parent&&child.getTop()==node.appliedTop)child.offsetTopAndBottom(node.nativeTop-node.appliedTop);
            node.ownsTop=false;
        }
    }
    public void detached(ViewGroup parent){
        restoreY(parent);Container saved=containers.remove(parent);if(saved==null)return;
        for(WeakReference<View> ref:saved.children){View child=ref.get();if(child!=null)nodes.remove(child);}
    }
    public void releaseRuntime(){
        for(ViewGroup parent:new ArrayList<>(containers.keySet()))if(parent!=null){restoreY(parent);parent.requestLayout();}
        released=true;containers.clear();nodes.clear();scopes.remove();readingNative.remove();
        nativeContainer=null;nativeIcon=null;nativeState=null;displayable=null;
        slot=null;visible=null;blocked=null;notification=null;getX=null;setX=null;ignored=null;stateVisible=null;
    }
}
