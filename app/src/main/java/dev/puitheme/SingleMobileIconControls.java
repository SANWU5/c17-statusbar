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
    }
    private Access access;
    private FeatureOptions options=FeatureOptions.from(Collections.emptyMap());
    private float x,y,scale=100;
    private boolean released;
    private long nativeEvents,applications;
    private final Map<Object,State> rows=new WeakHashMap<>();
    private static final class State {
        final WeakReference<ImageView> icon;
        int depth;boolean owned;
        float x,y,scaleX,scaleY;
        State(ImageView icon){this.icon=new WeakReference<>(icon);capture(icon);}
        void capture(ImageView icon){x=icon.getTranslationX();y=icon.getTranslationY();scaleX=icon.getScaleX();scaleY=icon.getScaleY();}
    }
    SingleMobileIconControls(){ }
    SingleMobileIconControls(Access access){this.access=access;}
    void resolve(ClassLoader loader,Predicate<Drawable> rendererOwnsPlacement)throws ReflectiveOperationException{
        Class<?> binding=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.AbstractOplusStatusBarMobileViewBinder$Binding");
        Method icon=binding.getMethod("getIconView");
        access=new Access(){
            public ImageView icon(Object owner)throws ReflectiveOperationException{return (ImageView)icon.invoke(owner);}
            public boolean rendererOwnsPlacement(Drawable drawable){return rendererOwnsPlacement.test(drawable);}
        };
    }
    void update(FeatureOptions options,float x,float y,float scale){
        if(released)return;
        if(this.options.values().equals(options.values())&&this.options.safeMode()==options.safeMode()
                &&this.x==x&&this.y==y&&this.scale==scale)return;
        this.options=options;this.x=x;this.y=y;this.scale=scale;
        for(State state:new ArrayList<>(rows.values()))apply(state);
    }
    private State row(Object binding)throws ReflectiveOperationException{
        if(binding==null||access==null||released)return null;
        State found=rows.get(binding);if(found!=null)return found;
        ImageView icon=access.icon(binding);if(icon==null)return null;
        found=new State(icon);rows.put(binding,found);return found;
    }
    void bound(Object binding){
        try{State state=row(binding);if(state!=null)apply(state);}
        catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_signal","Native mobile icon binding unavailable",failure);}
    }
    NativeScope beforeNative(Object binding){
        try{State state=row(binding);if(state==null)return new NativeScope(null);
            nativeEvents++;if(state.depth++==0&&state.owned){ImageView icon=state.icon.get();if(icon!=null)restore(icon,state);}
            return new NativeScope(state);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_signal","Native mobile icon transaction unavailable",failure);return new NativeScope(null);}
    }
    final class NativeScope implements AutoCloseable {
        private State state;
        NativeScope(State state){this.state=state;}
        public void close(){State current=state;state=null;if(current==null||--current.depth!=0||released||access==null)return;
            ImageView icon=current.icon.get();if(icon==null)return;current.capture(icon);apply(current);
        }
    }
    private void apply(State state){
        ImageView icon=state.icon.get();if(icon==null||state.depth!=0||released||access==null)return;applications++;
        // A resource wrapper may appear on the next native model event. It already owns
        // these transforms; do not apply the same setting again to the ImageView.
        boolean customize=options.enabled("data")&&!ModuleLifecycle.removed()
                &&!access.rendererOwnsPlacement(icon.getDrawable());
        if(!customize){if(state.owned){restore(icon,state);state.owned=false;}return;}
        float density=icon.getResources().getDisplayMetrics().density;
        float tx=state.x+NumericPolicy.pixels(options.position("data")?x:0,density);
        float ty=state.y+NumericPolicy.pixels(options.position("data")?y:0,density);
        float factor=NumericPolicy.scale((options.size("data")?scale:100)/100f,Math.max(icon.getWidth(),icon.getHeight()));
        if(icon.getTranslationX()!=tx)icon.setTranslationX(tx);if(icon.getTranslationY()!=ty)icon.setTranslationY(ty);
        if(icon.getScaleX()!=state.scaleX*factor)icon.setScaleX(state.scaleX*factor);
        if(icon.getScaleY()!=state.scaleY*factor)icon.setScaleY(state.scaleY*factor);
        state.owned=true;
    }
    private static void restore(ImageView icon,State state){
        if(icon.getTranslationX()!=state.x)icon.setTranslationX(state.x);if(icon.getTranslationY()!=state.y)icon.setTranslationY(state.y);
        if(icon.getScaleX()!=state.scaleX)icon.setScaleX(state.scaleX);if(icon.getScaleY()!=state.scaleY)icon.setScaleY(state.scaleY);
    }
    void releaseRuntime(){
        if(released)return;for(State state:new ArrayList<>(rows.values())){ImageView icon=state.icon.get();if(icon!=null&&state.owned)restore(icon,state);}
        rows.clear();access=null;released=true;
    }
    String diagnosticSummary(){int live=0,owned=0,wrapped=0;
        for(State state:rows.values()){ImageView icon=state.icon.get();if(icon==null)continue;live++;if(state.owned)owned++;
            if(access!=null&&access.rendererOwnsPlacement(icon.getDrawable()))wrapped++;}
        return "Single mobile icon rows "+live+", owned "+owned+", wrapped "+wrapped
                +", native events "+nativeEvents+", applications "+applications;
    }
}
