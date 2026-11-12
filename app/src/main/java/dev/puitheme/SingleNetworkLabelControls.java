// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
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
    // OEM may rebuild a Binding around the same native text. Only the glyph owns
    // its native baseline, placement transaction and current binding/model.
    private final Map<TextView,Row> glyphs=new WeakHashMap<>();
    private Access access;
    private FeatureOptions features=FeatureOptions.from(Collections.emptyMap());
    private String label="";private int subscription=-1,weight=600;
    private float x,y,scale=100;
    private Map<String,Integer> colors=Collections.emptyMap();
    private Map<String,Boolean> alphas=Collections.emptyMap();
    private boolean replaying,released;
    private long nativeEvents,boundEvents,applications;
    private long fontRevision=-1;
    private StatusBarLayoutSettings.Snapshot layout=StatusBarLayoutSettings.Snapshot.parse(null);
    private boolean layoutDirty;
    private NetworkSharedSlotLayout sharedLayout;
    void sharedLayout(NetworkSharedSlotLayout value) {
        sharedLayout = value;
        for (Row row : glyphs.values()) row.placement.sharedLayout(value);
    }
    void configureLayout(android.os.Bundle values) {
        StatusBarLayoutSettings.Snapshot next=StatusBarLayoutSettings.Snapshot.parse(values);
        layoutDirty|=next.enabled!=layout.enabled||next.wifiLabelShared!=layout.wifiLabelShared||next.order(StatusBarLayoutSettings.LABEL)!=layout.order(StatusBarLayoutSettings.LABEL)
                ||next.left(StatusBarLayoutSettings.LABEL)!=layout.left(StatusBarLayoutSettings.LABEL)
                ||next.right(StatusBarLayoutSettings.LABEL)!=layout.right(StatusBarLayoutSettings.LABEL);
        layout=next;
        for(Row row:glyphs.values()) {
            row.placement.observeSharedHost();
            row.placement.configure(layout.enabled&&!layout.wifiLabelShared?layout.order(StatusBarLayoutSettings.LABEL):0,
                    layout.enabled&&!layout.wifiLabelShared?layout.left(StatusBarLayoutSettings.LABEL):0f,
                    layout.enabled&&!layout.wifiLabelShared?layout.right(StatusBarLayoutSettings.LABEL):0f);
        }
    }
    private static final class Row {
        final WeakReference<TextView> text;WeakReference<View> image;
        WeakReference<Object> binding=new WeakReference<>(null);
        int subscription;
        final SingleNetworkLabelPlacement placement;
        Object model;boolean modelKnown,owned;
        int depth,color,minWidth,padLeft,padTop,padRight,padBottom;
        float size,x,y,spacing,scaleX,scaleY;
        boolean fontPadding;
        Typeface face;String axes;
        CharSequence nativeText,nativeDescription,appliedText;
        GlyphSpan glyph;
        Row(TextView text,View image,int sub){this.text=new WeakReference<>(text);this.image=new WeakReference<>(image);subscription=sub;
            placement=new SingleNetworkLabelPlacement(text);capture(text);}
        void capture(TextView view){color=view.getCurrentTextColor();minWidth=view.getMinimumWidth();size=view.getTextSize();
            x=view.getTranslationX();y=view.getTranslationY();spacing=view.getLetterSpacing();face=view.getTypeface();axes=view.getFontVariationSettings();
            scaleX=view.getScaleX();scaleY=view.getScaleY();fontPadding=view.getIncludeFontPadding();
            padLeft=view.getPaddingLeft();padTop=view.getPaddingTop();padRight=view.getPaddingRight();padBottom=view.getPaddingBottom();
            nativeText=view.getText();nativeDescription=view.getContentDescription();}
    }
    /** Match the current Compose NetworkLabelView, not the OEM badge's font box.
     * A real TextView span retains its native binder/accessibility host while its
     * line metrics and drawing both use the same independent 20dp glyph slot.
     * The immutable drawing plan is built only on radio/style/binding events. */
    private static final class GlyphSpan extends ReplacementSpan {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final String label,axes;
        final Typeface face;
        final float density,scalePercent;
        final NetworkLabelGeometry.Plan geometry;
        final int advance,height,color;
        GlyphSpan(String label,float density,float scalePercent,TextView view){
            this.label=label;this.density=density;this.scalePercent=scalePercent;
            face=view.getTypeface();axes=view.getFontVariationSettings();color=view.getCurrentTextColor();
            FontWeight.restore(paint,face,axes);paint.setFakeBoldText(false);
            paint.setTextSize(13f*density);paint.setColor(color);
            geometry=NetworkLabelGeometry.measure(paint,label,density,scalePercent);
            advance=geometry.width;height=geometry.height;
        }
        boolean matches(String label,float density,float scalePercent,TextView view){return this.label.equals(label)&&this.density==density
                &&this.scalePercent==scalePercent
                &&face==view.getTypeface()&&Objects.equals(axes,view.getFontVariationSettings())&&color==view.getCurrentTextColor();}
        @Override public int getSize(Paint ignored,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
            if(metrics!=null){metrics.top=metrics.ascent=-height/2;metrics.bottom=metrics.descent=height-height/2;metrics.leading=0;}
            return advance;
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint ignored){
            geometry.drawHorizontal(canvas,label,paint,x+advance/2f,baseline+(height%2)/2f);
        }
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
            if(row.binding.get()!=binding)return;
            row.model=access.model(binding);row.modelKnown=true;apply(row);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network factory binding unavailable",failure);}
    }
    /** The current main-SIM model only. The caller still applies Wi-Fi/radio visibility gates. */
    String nativeLabel(int activeSub){
        if(released||access==null||activeSub<0)return "";
        for(Row row:new ArrayList<>(glyphs.values())){
            Object binding=row.binding.get();if(row.subscription!=activeSub||row.text.get()==null||binding==null)continue;
            try{String current=access.nativeLabel(binding);if(current!=null&&!NetworkLabel.normalize(current).isEmpty())return current;}
            catch(ReflectiveOperationException|RuntimeException unavailable){/* Preserve native state; never reuse a previous SIM's text. */}
        }
        return "";
    }
    void update(FeatureOptions options,String label,int activeSub,float x,float y,float scale,float ignoredWidth,int weight,
            Map<String,Integer> colors,Map<String,Boolean> alphas){
        if(released)return;label=label==null?"":label;long fontRevision=FontRepository.styleRevision();
        if(!layoutDirty&&this.features.values().equals(options.values())&&this.features.safeMode()==options.safeMode()
                &&this.label.equals(label)&&subscription==activeSub&&this.x==x&&this.y==y&&this.scale==scale
                &&this.weight==weight&&this.colors.equals(colors)&&this.alphas.equals(alphas)&&this.fontRevision==fontRevision)return;
        this.fontRevision=fontRevision;this.features=options;this.label=label;subscription=activeSub;
        this.x=x;this.y=y;this.scale=scale;this.weight=weight;this.colors=colors;this.alphas=alphas;
        layoutDirty=false;
        // Radio/configuration events only. Native view callbacks refresh their own row.
        for(Row row:new ArrayList<>(glyphs.values()))try{apply(row);}
        catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network label refresh failed",failure);}
    }
    private Row row(Object binding)throws ReflectiveOperationException{
        if(binding==null||access==null||released)return null;Row row=rows.get(binding);if(row!=null)return row;
        TextView text=access.text(binding);if(text==null)return null;
        View image=access.image(binding);int sub=access.subscription(binding);
        row=glyphs.get(text);
        if(row==null){row=new Row(text,image,sub);glyphs.put(text,row);row.placement.sharedLayout(sharedLayout);row.placement.observeSharedHost();}
        else{row.image=new WeakReference<>(image);row.subscription=sub;row.model=null;row.modelKnown=false;}
        row.binding=new WeakReference<>(binding);rows.put(binding,row);return row;
    }
    /** Restore native style before OEM code measures/rebuilds spans; nested init/bind is one transaction. */
    NativeScope beforeNative(Object binding,boolean modelEvent,Object model){
        if(replaying||released)return new NativeScope(null);
        try{Row row=row(binding);if(row==null)return new NativeScope(null);
            nativeEvents++;
            if(modelEvent&&row.binding.get()==binding){row.model=model;row.modelKnown=true;}
            if(row.depth++==0&&row.owned){TextView text=row.text.get();if(text!=null)restoreStyle(text,row);}
            return new NativeScope(row);
        }catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network binding unavailable",failure);return new NativeScope(null);}
    }
    final class NativeScope implements AutoCloseable {
        private Row row;
        NativeScope(Row row){this.row=row;}
        public void close(){Row current=row;row=null;if(current==null)return;
            if(--current.depth!=0||released||access==null)return;TextView text=current.text.get();if(text==null)return;
            current.capture(text);
            try{apply(current);}catch(ReflectiveOperationException|RuntimeException failure){ModuleDiagnostics.error("single_label","Native network style application failed",failure);}
        }
    }
    private void apply(Row row)throws ReflectiveOperationException{
        TextView text=row.text.get();if(text==null||row.depth!=0||released||access==null)return;
        applications++;
        row.placement.observeSharedHost();
        row.placement.configure(layout.enabled&&!layout.wifiLabelShared?layout.order(StatusBarLayoutSettings.LABEL):0,
                layout.enabled&&!layout.wifiLabelShared?layout.left(StatusBarLayoutSettings.LABEL):0f,
                layout.enabled&&!layout.wifiLabelShared?layout.right(StatusBarLayoutSettings.LABEL):0f);
        if(!features.enabled("label")||ModuleLifecycle.removed()){
            if(layout.enabled&&!layout.wifiLabelShared&&!features.safeMode()&&!ModuleLifecycle.removed()
                    &&(layout.order(StatusBarLayoutSettings.LABEL)!=0||layout.left(StatusBarLayoutSettings.LABEL)!=0f
                    ||layout.right(StatusBarLayoutSettings.LABEL)!=0f)) {
                if(row.owned){row.owned=false;MANAGED.remove(text);restoreStyle(text,row);}
                row.placement.apply(); return;
            }
            if(!row.owned)return;row.owned=false;MANAGED.remove(text);restoreStyle(text,row);
            // Rebuild genuine native spans, content description, Tigo image and visibility.
            Object binding=row.binding.get();
            if(row.modelKnown&&binding!=null){replaying=true;try{access.bind(binding,row.model);}finally{replaying=false;}row.capture(text);}
            return;
        }
        row.owned=true;MANAGED.put(text,true);
        boolean show=row.subscription==subscription&&subscription>=0&&!features.hideNetworkLabel()&&!label.isEmpty();
        View image=row.image.get();if(image!=null&&image.getVisibility()!=View.GONE)image.setVisibility(View.GONE);
        if(!show){row.placement.restore();if(text.getVisibility()!=View.GONE)text.setVisibility(View.GONE);return;}
        row.placement.apply();
        float density=text.getResources().getDisplayMetrics().density;
        // Native badge translation/scale belongs only to restoration. Compose
        // places the independent slot using the user's transform from zero.
        float tx=NumericPolicy.pixels(features.position("label")?x:0,density);
        float ty=NumericPolicy.pixels(features.position("label")?y:0,density);
        if(text.getTranslationX()!=tx)text.setTranslationX(tx);if(text.getTranslationY()!=ty)text.setTranslationY(ty);
        float size=NumericPolicy.textPixels(13d*density);
        if(text.getTextSize()!=size)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        float scalePercent=features.size("label")?scale:100;
        // The span reports its scaled painted width to the native text layout.
        // Horizontal View scaling would grow the ink outside that measured slot.
        if(text.getScaleX()!=1f)text.setScaleX(1f);
        // The actual font/text footprint owns its space; retired manual reserves
        // and the native badge's minimum width apply only when restoring native.
        if(text.getMinimumWidth()!=0)text.setMinimumWidth(0);
        if(text.getIncludeFontPadding())text.setIncludeFontPadding(false);
        if(text.getPaddingLeft()!=0||text.getPaddingTop()!=0||text.getPaddingRight()!=0||text.getPaddingBottom()!=0)text.setPadding(0,0,0,0);
        if(text.getLetterSpacing()!=0f)text.setLetterSpacing(0f);
        int nativeWeight=row.face==null?400:Build.VERSION.SDK_INT>=28?row.face.getWeight():row.face.isBold()?700:400;
        if(features.textStyle("label")||features.enabled("font")&&!FontRepository.systemMode(FontScopes.NETWORK_TYPE))
            FontRepository.apply(text,row.face,features.textStyle("label")?weight:nativeWeight,null,FontScopes.NETWORK_TYPE);
        else FontWeight.restore(text,row.face,null);
        int color=features.color("label")?IconAppearance.color("label",row.color,colors,alphas):row.color;
        if(text.getCurrentTextColor()!=color)text.setTextColor(color);
        // Strip OEM prefix/suffix spans. A different font's ascent/descent or
        // includeFontPadding must not turn the independent glyph into a badge.
        if(row.glyph==null||!row.glyph.matches(label,density,scalePercent,text)){
            row.glyph=new GlyphSpan(label,density,scalePercent,text);
            SpannableString styled=new SpannableString(label);styled.setSpan(row.glyph,0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.appliedText=styled;
        }
        // Preserve the original external vertical scale and its enlarged clip.
        // The span's native 20dp line draws without an internal Y transform.
        if(text.getScaleY()!=row.glyph.geometry.scale)text.setScaleY(row.glyph.geometry.scale);
        if(text.getText()!=row.appliedText){text.setText(row.appliedText);row.appliedText=text.getText();}
        if(!Objects.equals(text.getContentDescription(),label))text.setContentDescription(label);
        if(text.getVisibility()!=View.VISIBLE)text.setVisibility(View.VISIBLE);
    }
    private static void restoreStyle(TextView text,Row row){
        row.placement.restore();
        if(text.getTextSize()!=row.size)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,row.size);
        FontWeight.restore(text,row.face,row.axes);
        if(text.getTranslationX()!=row.x)text.setTranslationX(row.x);if(text.getTranslationY()!=row.y)text.setTranslationY(row.y);
        if(text.getScaleX()!=row.scaleX)text.setScaleX(row.scaleX);if(text.getScaleY()!=row.scaleY)text.setScaleY(row.scaleY);
        if(text.getMinimumWidth()!=row.minWidth)text.setMinimumWidth(row.minWidth);
        if(text.getIncludeFontPadding()!=row.fontPadding)text.setIncludeFontPadding(row.fontPadding);
        if(text.getPaddingLeft()!=row.padLeft||text.getPaddingTop()!=row.padTop||text.getPaddingRight()!=row.padRight||text.getPaddingBottom()!=row.padBottom)
            text.setPadding(row.padLeft,row.padTop,row.padRight,row.padBottom);
        if(text.getLetterSpacing()!=row.spacing)text.setLetterSpacing(row.spacing);
        if(text.getCurrentTextColor()!=row.color)text.setTextColor(row.color);
        if(text.getText()!=row.nativeText)text.setText(row.nativeText);
        if(!Objects.equals(text.getContentDescription(),row.nativeDescription))text.setContentDescription(row.nativeDescription);
    }
    void releaseRuntime(){configureLayout(null);update(FeatureOptions.from(Collections.emptyMap()),"",-1,0,0,100,22,400,Collections.emptyMap(),Collections.emptyMap());
        for(Row row:glyphs.values()){row.placement.sharedLayout(null);TextView text=row.text.get();if(text!=null)MANAGED.remove(text);}rows.clear();glyphs.clear();sharedLayout=null;access=null;released=true;}
    String diagnosticSummary(){int live=0,owned=0,visible=0,active=0;
        for(Row row:glyphs.values()){TextView text=row.text.get();if(text==null)continue;live++;if(row.owned)owned++;
            if(text.getVisibility()==View.VISIBLE)visible++;if(row.subscription==subscription)active++;}
        return "Single network label rows "+live+", owned "+owned+", visible "+visible+", active "+active
                +", factory events "+boundEvents+", native events "+nativeEvents+", applications "+applications;
    }
}
