// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** C17's unstacked binder uses iconView.setImageResource, independently of Compose. */
final class SingleMobileIconControls {
    interface Access {
        ImageView icon(Object binding)throws ReflectiveOperationException;
        boolean rendererOwnsPlacement(Drawable drawable);
        default int subscription(Object binding)throws ReflectiveOperationException{return -1;}
        default View host(Object binding)throws ReflectiveOperationException{return null;}
    }
    private Access access;
    private FeatureOptions options=FeatureOptions.from(Collections.emptyMap());
    private float x,y,scale=100;
    private boolean single;
    private int activeSubscription=-1;
    private boolean released;
    private long nativeEvents,applications;
    private final Map<Object,State> rows=new WeakHashMap<>();
    // A recreated native Binding can reuse an already-styled ImageView. The glyph,
    // not its transient wrapper, owns one native baseline and one transaction depth.
    private final Map<ImageView,State> glyphs=new WeakHashMap<>();
    private static final class State {
        final WeakReference<ImageView> icon;
        int depth,subscription=-1,visibility;boolean owned,hidden,detached;
        WeakReference<View> host=new WeakReference<>(null);
        View.OnAttachStateChangeListener attachment;
        float x,y,scaleX,scaleY;
        State(ImageView icon){this.icon=new WeakReference<>(icon);detached=!icon.isAttachedToWindow();capture(icon);}
        void capture(ImageView icon){x=icon.getTranslationX();y=icon.getTranslationY();scaleX=icon.getScaleX();scaleY=icon.getScaleY();visibility=icon.getVisibility();}
    }
    SingleMobileIconControls(){ }
    SingleMobileIconControls(Access access){this.access=access;}
    void resolve(ClassLoader loader,Predicate<Drawable> rendererOwnsPlacement)throws ReflectiveOperationException{
        Class<?> binding=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.AbstractOplusStatusBarMobileViewBinder$Binding");
        Method icon=binding.getMethod("getIconView");
        Method model=binding.getMethod("getViewModel"),host=binding.getMethod("getViewGroup");
        Method subscription=model.getReturnType().getMethod("getSubscriptionId");
        access=new Access(){
            public ImageView icon(Object owner)throws ReflectiveOperationException{return (ImageView)icon.invoke(owner);}
            public boolean rendererOwnsPlacement(Drawable drawable){return rendererOwnsPlacement.test(drawable);}
            public int subscription(Object owner)throws ReflectiveOperationException{return ((Number)subscription.invoke(model.invoke(owner))).intValue();}
            public View host(Object owner)throws ReflectiveOperationException{return (View)host.invoke(owner);}
        };
    }
    void update(FeatureOptions options,float x,float y,float scale){
        update(options,x,y,scale,false,-1);
    }
    void update(FeatureOptions options,float x,float y,float scale,boolean single,int activeSubscription){
        if(released)return;
        if(this.options.values().equals(options.values())&&this.options.safeMode()==options.safeMode()
                &&this.x==x&&this.y==y&&this.scale==scale&&this.single==single&&this.activeSubscription==activeSubscription)return;
        this.options=options;this.x=x;this.y=y;this.scale=scale;
        this.single=single;this.activeSubscription=activeSubscription;applyAll();
    }
    void activeSubscription(int subscription){if(released)return;activeSubscription=subscription;applyAll();}
    private void applyAll(){for(State state:new ArrayList<>(glyphs.values()))apply(state);}
    private State row(Object binding)throws ReflectiveOperationException{
        if(binding==null||access==null||released)return null;
        State found=rows.get(binding);if(found!=null)return found;
        // The legacy big-type binder has no Binding object: its real setter
        // receives the signal ImageView directly. It shares the same native
        // transaction without touching the other ImageViews in its tint call.
        ImageView icon=binding instanceof ImageView?(ImageView)binding:access.icon(binding);if(icon==null)return null;
        found=glyphs.get(icon);
        if(found==null){found=new State(icon);glyphs.put(icon,found);watch(icon,found);}
        if(!(binding instanceof ImageView)){
            try{found.subscription=access.subscription(binding);found.host=new WeakReference<>(access.host(binding));}
            catch(ReflectiveOperationException|RuntimeException unavailable){found.subscription=-1;found.host.clear();}
        }
        rows.put(binding,found);return found;
    }
    private void watch(ImageView icon,State state){
        state.attachment=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){state.detached=false;if(!released)applyAll();}
            public void onViewDetachedFromWindow(View view){
                state.detached=true;
                if(state.hidden){restoreVisibility((ImageView)view,state);state.hidden=false;}
                if(!released)applyAll();
            }
        };
        icon.addOnAttachStateChangeListener(state.attachment);
    }
    void bound(Object binding){
        try{if(row(binding)!=null)applyAll();}
        catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_signal","Native mobile icon binding unavailable",failure);}
    }
    /** BigTypeLegacy supplies the same verified model and mobile_signal view directly. */
    void bound(ImageView icon,int subscription,View host){
        try{State state=row(icon);if(state==null)return;state.subscription=subscription;state.host=new WeakReference<>(host);applyAll();}
        catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_signal","Legacy mobile subscription binding unavailable",failure);}
    }
    NativeScope beforeNative(Object binding){
        try{State state=row(binding);if(state==null)return new NativeScope(null);
            nativeEvents++;if(state.depth++==0&&(state.owned||state.hidden)){ImageView icon=state.icon.get();if(icon!=null)restore(icon,state);}
            return new NativeScope(state);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_signal","Native mobile icon transaction unavailable",failure);return new NativeScope(null);}
    }
    final class NativeScope implements AutoCloseable {
        private State state;
        NativeScope(State state){this.state=state;}
        public void close(){State current=state;state=null;if(current==null||--current.depth!=0||released||access==null)return;
            ImageView icon=current.icon.get();if(icon==null)return;current.capture(icon);applyAll();
        }
    }
    private void apply(State state){
        ImageView icon=state.icon.get();if(icon==null||state.depth!=0||released||access==null)return;applications++;
        boolean hide=hideSecondary(state);
        if(hide){if(icon.getVisibility()!=View.GONE)icon.setVisibility(View.GONE);state.hidden=true;}
        else if(state.hidden){restoreVisibility(icon,state);state.hidden=false;}
        // A resource wrapper may appear on the next native model event. It already owns
        // these transforms; do not apply the same setting again to the ImageView.
        boolean customize=options.enabled("data")&&!ModuleLifecycle.removed()
                &&!access.rendererOwnsPlacement(icon.getDrawable());
        if(!customize){if(state.owned){restorePlacement(icon,state);state.owned=false;}return;}
        float density=icon.getResources().getDisplayMetrics().density;
        float tx=state.x+NumericPolicy.pixels(options.position("data")?x:0,density);
        float ty=state.y+NumericPolicy.pixels(options.position("data")?y:0,density);
        float factor=NumericPolicy.scale((options.size("data")?scale:100)/100f,Math.max(icon.getWidth(),icon.getHeight()));
        if(icon.getTranslationX()!=tx)icon.setTranslationX(tx);if(icon.getTranslationY()!=ty)icon.setTranslationY(ty);
        if(icon.getScaleX()!=state.scaleX*factor)icon.setScaleX(state.scaleX*factor);
        if(icon.getScaleY()!=state.scaleY*factor)icon.setScaleY(state.scaleY*factor);
        state.owned=true;
    }
    /** The independent native badge shares its signal's verified subscription and host.
     * A stacked Compose badge is shared by the primary VM and must not use this API. */
    boolean hideSecondaryBadge(int subscription,View host){
        if(host==null||subscription<0||released||access==null)return false;
        for(State state:glyphs.values())
            if(state.subscription==subscription&&state.host.get()==host)return hideSecondary(state);
        return false;
    }
    private boolean hideSecondary(State state){
        ImageView icon=state.icon.get();
        return !released&&access!=null&&single&&options.enabled("data")&&!ModuleLifecycle.removed()
                &&!state.detached&&icon!=null&&icon.isAttachedToWindow()&&activeSubscription>=0
                &&state.subscription>=0&&state.subscription!=activeSubscription&&hasPrimary(state);
    }
    /** Only siblings in the same native status container can identify a primary row.
     * Missing/unknown/sole subscription evidence leaves every native signal visible. */
    private boolean hasPrimary(State target){
        View host=target.host.get();Object container=host==null?null:host.getParent();if(container==null)return false;
        for(State candidate:glyphs.values()){
            ImageView icon=candidate.icon.get();View other=candidate.host.get();
            if(candidate.subscription==activeSubscription&&candidate.visibility==View.VISIBLE&&!candidate.detached&&icon!=null&&icon.isAttachedToWindow()
                    &&other!=null&&other.getVisibility()==View.VISIBLE&&other.getParent()==container)return true;
        }
        return false;
    }
    private static void restore(ImageView icon,State state){restorePlacement(icon,state);restoreVisibility(icon,state);}
    private static void restoreVisibility(ImageView icon,State state){if(icon.getVisibility()!=state.visibility)icon.setVisibility(state.visibility);}
    private static void restorePlacement(ImageView icon,State state){
        if(icon.getTranslationX()!=state.x)icon.setTranslationX(state.x);if(icon.getTranslationY()!=state.y)icon.setTranslationY(state.y);
        if(icon.getScaleX()!=state.scaleX)icon.setScaleX(state.scaleX);if(icon.getScaleY()!=state.scaleY)icon.setScaleY(state.scaleY);
    }
    void releaseRuntime(){
        if(released)return;for(State state:new ArrayList<>(glyphs.values())){ImageView icon=state.icon.get();if(icon!=null){
            if(state.owned||state.hidden)restore(icon,state);if(state.attachment!=null)icon.removeOnAttachStateChangeListener(state.attachment);}}
        rows.clear();glyphs.clear();access=null;released=true;
    }
    String diagnosticSummary(){int live=0,owned=0,wrapped=0,subscribed=0,hidden=0;
        for(State state:glyphs.values()){ImageView icon=state.icon.get();if(icon==null)continue;live++;if(state.owned)owned++;
            if(state.subscription>=0)subscribed++;if(state.hidden)hidden++;
            if(access!=null&&access.rendererOwnsPlacement(icon.getDrawable()))wrapped++;}
        return "Single mobile icon rows "+live+", owned "+owned+", wrapped "+wrapped
                +", subscribed "+subscribed+", primary known "+(activeSubscription>=0)+", hidden "+hidden
                +", native events "+nativeEvents+", applications "+applications;
    }
}
