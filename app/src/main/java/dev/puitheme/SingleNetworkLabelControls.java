// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** C17 single-SIM uses a native TextView binder, never the stacked Compose renderer. */
final class SingleNetworkLabelControls {
    interface Access {
        TextView text(Object binding)throws ReflectiveOperationException;
        View image(Object binding)throws ReflectiveOperationException;
        int subscription(Object binding)throws ReflectiveOperationException;
        void bind(Object binding,Object model)throws ReflectiveOperationException;
        default Object model(Object binding)throws ReflectiveOperationException{return null;}
        default String nativeLabel(Object binding)throws ReflectiveOperationException{return "";}
    }
    private static final Map<View,Boolean> MANAGED=Collections.synchronizedMap(new WeakHashMap<>());
    static boolean managed(View view){return view!=null&&MANAGED.containsKey(view);}
    private final Map<Object,Row> rows=new WeakHashMap<>();
    private Access access;
    private FeatureOptions features=FeatureOptions.from(Collections.emptyMap());
    private String label="";private int subscription=-1,weight=600;
    private float x,y,scale=100,width=22;
    private Map<String,Integer> colors=Collections.emptyMap();
    private Map<String,Boolean> alphas=Collections.emptyMap();
    private boolean replaying,released;
    private long nativeEvents,boundEvents,applications;
    private long fontRevision=-1;
    private static final class Row {
        final WeakReference<TextView> text;final WeakReference<View> image;
        final int subscription;
        Object model;boolean modelKnown,owned;
        int depth,color,minWidth;float size,x,y,spacing;
        Typeface face;String axes;
        Row(TextView text,View image,int sub){this.text=new WeakReference<>(text);this.image=new WeakReference<>(image);subscription=sub;capture(text);}
        void capture(TextView view){color=view.getCurrentTextColor();minWidth=view.getMinimumWidth();size=view.getTextSize();
            x=view.getTranslationX();y=view.getTranslationY();spacing=view.getLetterSpacing();face=view.getTypeface();axes=view.getFontVariationSettings();}
    }
    SingleNetworkLabelControls(){ }
    SingleNetworkLabelControls(Access access){this.access=access;}
    void resolve(ClassLoader loader)throws ReflectiveOperationException{
        Class<?> binder=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder$Os17Binding");
        Class<?> model=loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel");
        Method text=binder.getMethod("getMobileTypeText");Field image=binder.getDeclaredField("mobileTypeImage");image.setAccessible(true);
        Method vm=binder.getMethod("getOplusViewModel");
        Method sub=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconViewModel").getMethod("getSubscriptionId");
        Method bind=binder.getMethod("bindNetworkTypeText",model);
        Method flow=loader.loadClass("com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.OplusMobileIconViewModel").getMethod("getNetworkTypeText");
        Method value=loader.loadClass("kotlinx.coroutines.flow.StateFlow").getMethod("getValue");
        Method type=model.getMethod("getNetworkTypeText");
        Method name=loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.data.OplusNetworkTypeText").getMethod("getName");
        access=new Access(){
            public TextView text(Object owner)throws ReflectiveOperationException{return (TextView)text.invoke(owner);}
            public View image(Object owner)throws ReflectiveOperationException{return (View)image.get(owner);}
            public int subscription(Object owner)throws ReflectiveOperationException{return (Integer)sub.invoke(vm.invoke(owner));}
            public void bind(Object owner,Object value)throws ReflectiveOperationException{bind.invoke(owner,value);}
            public Object model(Object owner)throws ReflectiveOperationException{return value.invoke(flow.invoke(vm.invoke(owner)));}
            public String nativeLabel(Object owner)throws ReflectiveOperationException{
                Object current=model(owner),network=current==null?null:type.invoke(current);
                Object result=network==null?null:name.invoke(network);return result instanceof String?(String)result:"";
            }
        };
    }
    /** The concrete factory result is available even when OEM initViews was inlined. */
    void bound(Object binding){
        if(replaying||released)return;
        try{Row row=row(binding);if(row==null)return;boundEvents++;
            row.model=access.model(binding);row.modelKnown=true;apply(binding,row);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network factory binding unavailable",failure);}
    }
    /** The current main-SIM model only. The caller still applies Wi-Fi/radio visibility gates. */
    String nativeLabel(int activeSub){
        if(released||access==null||activeSub<0)return "";
        for(Map.Entry<Object,Row> entry:new ArrayList<>(rows.entrySet())){
            Row row=entry.getValue();if(row.subscription!=activeSub||row.text.get()==null)continue;
            try{String current=access.nativeLabel(entry.getKey());if(current!=null&&!NetworkLabel.normalize(current).isEmpty())return current;}
            catch(ReflectiveOperationException|RuntimeException unavailable){/* Preserve native state; never reuse a previous SIM's text. */}
        }
        return "";
    }
    void update(FeatureOptions options,String label,int activeSub,float x,float y,float scale,float width,int weight,
            Map<String,Integer> colors,Map<String,Boolean> alphas){
        if(released)return;label=label==null?"":label;long fontRevision=FontRepository.styleRevision();
        if(this.features.values().equals(options.values())&&this.features.safeMode()==options.safeMode()
                &&this.label.equals(label)&&subscription==activeSub&&this.x==x&&this.y==y&&this.scale==scale&&this.width==width
                &&this.weight==weight&&this.colors.equals(colors)&&this.alphas.equals(alphas)&&this.fontRevision==fontRevision)return;
        this.fontRevision=fontRevision;this.features=options;this.label=label;subscription=activeSub;
        this.x=x;this.y=y;this.scale=scale;this.width=width;this.weight=weight;this.colors=colors;this.alphas=alphas;
        // Radio/configuration events only. Native view callbacks refresh their own row.
        for(Map.Entry<Object,Row> entry:new ArrayList<>(rows.entrySet()))try{apply(entry.getKey(),entry.getValue());}
        catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network label refresh failed",failure);}
    }
    private Row row(Object binding)throws ReflectiveOperationException{
        if(binding==null||access==null||released)return null;Row row=rows.get(binding);if(row!=null)return row;
        TextView text=access.text(binding);if(text==null)return null;
        row=new Row(text,access.image(binding),access.subscription(binding));rows.put(binding,row);return row;
    }
    /** Restore native style before OEM code measures/rebuilds spans; nested init/bind is one transaction. */
    NativeScope beforeNative(Object binding,boolean modelEvent,Object model){
        if(replaying||released)return new NativeScope(null,null);
        try{Row row=row(binding);if(row==null)return new NativeScope(null,null);
            nativeEvents++;
            if(modelEvent){row.model=model;row.modelKnown=true;}
            if(row.depth++==0&&row.owned){TextView text=row.text.get();if(text!=null)restoreStyle(text,row);}
            return new NativeScope(binding,row);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network binding unavailable",failure);return new NativeScope(null,null);}
    }
    final class NativeScope implements AutoCloseable {
        private Object binding;private Row row;
        NativeScope(Object binding,Row row){this.binding=binding;this.row=row;}
        public void close(){Row current=row;Object owner=binding;row=null;binding=null;if(current==null)return;
            if(--current.depth!=0||released||access==null)return;TextView text=current.text.get();if(text==null)return;
            current.capture(text);
            try{apply(owner,current);}catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network style application failed",failure);}
        }
    }
    private void apply(Object binding,Row row)throws ReflectiveOperationException{
        TextView text=row.text.get();if(text==null||row.depth!=0||released||access==null)return;
        applications++;
        if(!features.enabled("label")||ModuleLifecycle.removed()){
            if(!row.owned)return;row.owned=false;MANAGED.remove(text);restoreStyle(text,row);
            // Rebuild genuine native spans, content description, Tigo image and visibility.
            if(row.modelKnown){replaying=true;try{access.bind(binding,row.model);}finally{replaying=false;}row.capture(text);}
            return;
        }
        row.owned=true;MANAGED.put(text,true);
        boolean show=row.subscription==subscription&&subscription>=0&&!features.hideNetworkLabel()&&!label.isEmpty();
        View image=row.image.get();if(image!=null&&image.getVisibility()!=View.GONE)image.setVisibility(View.GONE);
        if(!show){if(text.getVisibility()!=View.GONE)text.setVisibility(View.GONE);return;}
        float density=text.getResources().getDisplayMetrics().density;
        float tx=row.x+NumericPolicy.pixels(features.position("label")?x:0,density);
        float ty=row.y+NumericPolicy.pixels(features.position("label")?y:0,density);
        if(text.getTranslationX()!=tx)text.setTranslationX(tx);if(text.getTranslationY()!=ty)text.setTranslationY(ty);
        float size=NumericPolicy.textPixels((double)row.size*(features.size("label")?scale:100)/100d);
        if(text.getTextSize()!=size)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        int min=features.size("label")?Math.max(row.minWidth,Math.round(NumericPolicy.layoutPixels(width,density))):row.minWidth;
        if(text.getMinimumWidth()!=min)text.setMinimumWidth(min);
        int nativeWeight=row.face==null?400:Build.VERSION.SDK_INT>=28?row.face.getWeight():row.face.isBold()?700:400;
        if(features.textStyle("label")||features.enabled("font")&&!FontRepository.systemMode())
            FontRepository.apply(text,row.face,features.textStyle("label")?weight:nativeWeight,row.axes);
        else FontWeight.restore(text,row.face,row.axes);
        int color=features.color("label")?IconAppearance.color("label",row.color,colors,alphas):row.color;
        if(text.getCurrentTextColor()!=color)text.setTextColor(color);
        // Strip native prefix/suffix size spans only while this independent label owns output.
        if(!(text.getText() instanceof String)||!Objects.equals(text.getText(),label))text.setText(label);
        if(!Objects.equals(text.getContentDescription(),label))text.setContentDescription(label);
        if(text.getVisibility()!=View.VISIBLE)text.setVisibility(View.VISIBLE);
    }
    private static void restoreStyle(TextView text,Row row){
        if(text.getTextSize()!=row.size)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,row.size);
        FontWeight.restore(text,row.face,row.axes);
        if(text.getTranslationX()!=row.x)text.setTranslationX(row.x);if(text.getTranslationY()!=row.y)text.setTranslationY(row.y);
        if(text.getMinimumWidth()!=row.minWidth)text.setMinimumWidth(row.minWidth);
        if(text.getLetterSpacing()!=row.spacing)text.setLetterSpacing(row.spacing);
        if(text.getCurrentTextColor()!=row.color)text.setTextColor(row.color);
    }
    void releaseRuntime(){update(FeatureOptions.from(Collections.emptyMap()),"",-1,0,0,100,22,400,Collections.emptyMap(),Collections.emptyMap());
        for(Row row:rows.values()){TextView text=row.text.get();if(text!=null)MANAGED.remove(text);}rows.clear();access=null;released=true;}
    String diagnosticSummary(){int live=0,owned=0,visible=0,active=0;
        for(Row row:rows.values()){TextView text=row.text.get();if(text==null)continue;live++;if(row.owned)owned++;
            if(text.getVisibility()==View.VISIBLE)visible++;if(row.subscription==subscription)active++;}
        return "Single network label rows "+live+", owned "+owned+", visible "+visible+", active "+active
                +", factory events "+boundEvents+", native events "+nativeEvents+", applications "+applications;
    }
}
