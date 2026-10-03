// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Styles the real NetworkTypeText content without changing its Compose slot contract. */
public final class NativeNetworkBadgeControls {
    public static final String MASTER="native_network_badge_enabled";
    public static final String X="native_network_badge_offset_x",Y="native_network_badge_offset_y";
    public static final String SCALE="native_network_badge_scale",WEIGHT="native_network_badge_weight",FONT="native_network_badge_font";
    public static Map<String,Boolean> booleanDefaults(){return Collections.singletonMap(MASTER,false);}
    public static Map<String,String> stringDefaults(){return Collections.singletonMap(FONT,"native");}
    public static Map<String,Float> floatDefaults(){
        LinkedHashMap<String,Float> values=new LinkedHashMap<>();
        values.put(X,0f);values.put(Y,0f);values.put(SCALE,100f);values.put(WEIGHT,400f);return Collections.unmodifiableMap(values);
    }
    interface Access {
        boolean modifier(Object value);
        Object translate(Object base,float x,float y)throws ReflectiveOperationException;
        int weight(Object value)throws ReflectiveOperationException;
        Object weight(int value)throws ReflectiveOperationException;
        Object family(Context context,Object nativeFamily,String mode,String revision,int weight)throws ReflectiveOperationException;
    }
    private volatile Access access;
    private volatile Settings settings=new Settings(null);
    private volatile boolean released;
    // Values never strongly retain their weak native modifier/composer keys or a native View/VM.
    private final Map<Object,Map<Object,Binding>> rows=new WeakHashMap<>();
    private final Map<Object,WeakReference<Binding>> outputs=new WeakHashMap<>();
    private final ThreadLocal<Object> content=new ThreadLocal<>();
    private volatile Field contentOwner;
    public NativeNetworkBadgeControls(){ }
    NativeNetworkBadgeControls(Access access){this.access=access;}
    private static final class Settings {
        final boolean enabled;final float x,y,scale;final int weight;final String font,revision;
        Settings(Bundle b){
            enabled=b!=null&&b.getBoolean(MASTER,false)&&!b.getBoolean("label_enabled",false)&&!SafetyMode.enabled(b);
            x=number(b,X,0f);y=number(b,Y,0f);scale=Math.max(0f,number(b,SCALE,100f));
            weight=Math.round(Math.max(1f,Math.min(1000f,number(b,WEIGHT,400f))));
            String selected=b==null?"native":b.getString(FONT,"native");
            font="global".equals(selected)||"system".equals(selected)||"pingfang".equals(selected)||"custom".equals(selected)?selected:"native";
            // Include the selected global source in the independent family cache identity.
            // A switch system→bundled can otherwise retain a cached "global" family.
            revision=b==null?"":b.getString(StatusBarSettings.FONT_REVISION,"")+':'
                    +(b.getBoolean("font_enabled",false)?b.getString(StatusBarSettings.FONT_MODE,"system"):"system");
        }
        boolean same(Settings other){return enabled==other.enabled&&x==other.x&&y==other.y&&scale==other.scale
                &&weight==other.weight&&font.equals(other.font)&&revision.equals(other.revision);}
    }
    private static float number(Bundle b,String key,float fallback){
        Object value=b==null?null:b.get(key);if(!(value instanceof Number))return fallback;
        float result=((Number)value).floatValue();return Float.isFinite(result)?result:fallback;
    }
    public boolean configure(Bundle b){
        if(released)return false;Settings next=new Settings(b);if(settings.same(next))return false;settings=next;return true;
    }
    public boolean enabled(){return !released&&settings.enabled&&!ModuleLifecycle.removed();}
    public synchronized void resolve(ClassLoader loader)throws ReflectiveOperationException{
        if(released)return;NativeAccess nativeAccess=new NativeAccess(loader);
        Field owner=loader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda2").getField("f$0");
        contentOwner=owner;access=nativeAccess;
    }
    /** Exact NetworkTypeText content scope. It excludes roaming and other global Text renderers. */
    public Scope enterContent(Object lambda){
        Object owner=null;Field field=contentOwner;
        if(field!=null&&lambda!=null&&field.getDeclaringClass().isInstance(lambda))try{owner=field.get(lambda);}
        catch(ReflectiveOperationException|RuntimeException unsupported){ }
        return enterOwner(owner);
    }
    Scope enterOwner(Object owner){Object previous=content.get();if(owner==null)content.remove();else content.set(owner);return new Scope(previous);}
    public final class Scope implements AutoCloseable {
        private Object previous;private boolean closed;
        private Scope(Object previous){this.previous=previous;}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)content.remove();else content.set(previous);previous=null;}
    }
    private static final class Binding {
        final WeakReference<?>[] original=new WeakReference<?>[3],applied=new WeakReference<?>[3];
        final boolean[] nullOriginal=new boolean[3],known=new boolean[3];
        final long[] units=new long[2],appliedUnits=new long[2];final WeakReference<Object> composer;
        // FontFamily is immutable and contains no View/composer/VM. Preserve it until
        // this weak native row expires, so OFF can restore after a saved callback
        // has replaced the native family's last external reference.
        Object originalFamily;
        int weight;float appliedX,appliedY;
        Binding(Object composer){this.composer=new WeakReference<>(composer);}
    }
    private static final int[] INDEX={2,3,4};
    private Binding binding(Object modifier,Object composer,boolean create){
        if(modifier==null||composer==null)return null;
        WeakReference<Binding> derived=outputs.get(modifier);Binding result=derived==null?null:derived.get();
        if(result!=null&&result.composer.get()==composer)return result;
        Map<Object,Binding> compositions=rows.get(modifier);
        if(compositions==null&&create){compositions=new WeakHashMap<>();rows.put(modifier,compositions);}
        if(compositions==null)return null;result=compositions.get(composer);
        if(result==null&&create){result=new Binding(composer);compositions.put(composer,result);}return result;
    }
    /** Upper renderer stays native. Clearing dirty flags creates extra ungrouped changed()
     * slots before rememberComposableLambda; code59 then read a TextUnit from the lambda slot. */
    public Object[] adjust(Object[] nativeArgs,float density){return nativeArgs;}
    public synchronized boolean needsAdjustment(Object modifier,Object composer){
        return !released&&(content.get()!=null&&enabled()||binding(modifier,composer,false)!=null);
    }
    /** Exact 13-argument StackedMobileText-wW3Lq_U: sizes are Long, not boxed TextUnits.
     * NetworkTypeText content passes changed=0/default=0. Keep its slot and default paths
     * unchanged on every call, including ON/OFF and the lower renderer's saved restarts. */
    public synchronized Object[] adjustText(Object[] nativeArgs,float density,Context context){
        if(released||access==null||nativeArgs==null||nativeArgs.length!=13
                ||!(nativeArgs[6] instanceof Long)||!(nativeArgs[7] instanceof Long)
                ||!(nativeArgs[11] instanceof Integer)||!(nativeArgs[12] instanceof Integer)
                ||((Integer)nativeArgs[12])!=0||nativeArgs[2]==null)return nativeArgs;
        boolean scoped=content.get()!=null;Binding state=binding(nativeArgs[2],nativeArgs[10],scoped&&enabled());
        if(state==null||!access.modifier(nativeArgs[2]))return nativeArgs;
        try {
            Object[] base=nativeArgs;
            // Fresh content owns its new native baseline. Saved lower callbacks first restore
            // our previous outputs, even after OFF, safe mode or a custom-label change.
            if(!scoped){
                for(int i=0;i<INDEX.length;i++){
                    Object previous=state.applied[i]==null?null:state.applied[i].get();
                    if(!state.known[i]||previous==null||previous!=nativeArgs[INDEX[i]])continue;
                    Object original=i==1?state.originalFamily:state.original[i]==null?null:state.original[i].get();
                    if(original==null&&!state.nullOriginal[i]){if(i==2)original=access.weight(state.weight);else return nativeArgs;}
                    if(base==nativeArgs)base=nativeArgs.clone();base[INDEX[i]]=original;
                }
                for(int i=0;i<2;i++)if((Long)nativeArgs[6+i]==state.appliedUnits[i]&&state.appliedUnits[i]!=state.units[i]){
                    if(base==nativeArgs)base=nativeArgs.clone();base[6+i]=state.units[i];
                }
            }
            if(!enabled())return base;if(!Float.isFinite(density)||density<=0f)density=1f;
            Settings current=settings;float dx=NumericPolicy.pixels(current.x,density),dy=NumericPolicy.pixels(current.y,density);
            Object[] result=base.clone();
            if(dx!=0f||dy!=0f){
                Object old=state.applied[0]==null?null:state.applied[0].get();Object original=state.original[0]==null?null:state.original[0].get();
                result[2]=old!=null&&original==base[2]&&state.appliedX==dx&&state.appliedY==dy?old:access.translate(base[2],dx,dy);
            }
            result[3]=access.family(context,base[3],current.font,current.revision,current.weight);result[4]=access.weight(current.weight);
            for(int i=0;i<2;i++){state.units[i]=(Long)base[6+i];result[6+i]=scaleUnit(state.units[i],current.scale);state.appliedUnits[i]=(Long)result[6+i];}
            state.weight=base[4]==null?700:access.weight(base[4]);boolean changed=!result[6].equals(base[6])||!result[7].equals(base[7]);
            for(int i=0;i<INDEX.length;i++){
                state.known[i]=true;state.nullOriginal[i]=base[INDEX[i]]==null;
                if(i==1)state.originalFamily=base[INDEX[i]];
                if(state.original[i]==null||state.original[i].get()!=base[INDEX[i]])state.original[i]=new WeakReference<>(base[INDEX[i]]);
                if(state.applied[i]==null||state.applied[i].get()!=result[INDEX[i]])state.applied[i]=new WeakReference<>(result[INDEX[i]]);
                changed|=result[INDEX[i]]!=base[INDEX[i]];
            }
            state.appliedX=dx;state.appliedY=dy;if(result[2]!=base[2])outputs.put(result[2],new WeakReference<>(state));return changed?result:base;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return nativeArgs;}
    }
    static long scaleUnit(long nativeValue,float percent){
        float value=Float.intBitsToFloat((int)nativeValue);long type=nativeValue&0xffffffff00000000L;
        if(type==0L||!Float.isFinite(value)||value<0f||!Float.isFinite(percent)||percent<0f)return nativeValue;
        float size=NumericPolicy.textPixels((double)value*percent/100d);return type|(Float.floatToRawIntBits(size)&0xffffffffL);
    }
    public synchronized void releaseRuntime(){released=true;rows.clear();outputs.clear();content.remove();contentOwner=null;access=null;settings=new Settings(null);}
    private final class NativeAccess implements Access {
        final Class<?> modifier,function,loadedFamily,wrapper;final Object kotlinUnit;
        final Method layer,setX,setY,familyFace,wrapperFace,wrapFamily;final Field weightValue;final Constructor<?> weightConstructor;
        final Map<Integer,Object> weights=new LinkedHashMap<>();final Map<Object,Map<String,Object>> families=new WeakHashMap<>();
        NativeAccess(ClassLoader loader)throws ReflectiveOperationException{
            modifier=loader.loadClass("androidx.compose.ui.Modifier");function=loader.loadClass("kotlin.jvm.functions.Function1");
            kotlinUnit=loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            layer=loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerModifierKt").getMethod("graphicsLayer",modifier,function);
            Class<?> scope=loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerScope");setX=scope.getMethod("setTranslationX",Float.TYPE);setY=scope.getMethod("setTranslationY",Float.TYPE);
            Class<?> weight=loader.loadClass("androidx.compose.ui.text.font.FontWeight");weightConstructor=weight.getConstructor(Integer.TYPE);weightValue=weight.getField("weight");
            loadedFamily=loader.loadClass("androidx.compose.ui.text.font.LoadedFontFamily");familyFace=loadedFamily.getMethod("getTypeface");
            wrapper=loader.loadClass("androidx.compose.ui.text.platform.AndroidTypefaceWrapper");wrapperFace=wrapper.getMethod("getTypeface");
            wrapFamily=loader.loadClass("androidx.compose.ui.text.font.AndroidTypeface_androidKt").getMethod("FontFamily",Typeface.class);
        }
        @Override public boolean modifier(Object value){return modifier.isInstance(value);}
        @Override public Object translate(Object base,float x,float y)throws ReflectiveOperationException{
            Object action=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
                switch(method.getName()){
                    case "invoke":setX.invoke(args[0],enabled()?x:0f);setY.invoke(args[0],enabled()?y:0f);return kotlinUnit;
                    case "hashCode":return System.identityHashCode(proxy);case "equals":return proxy==args[0];default:return "NativeNetworkBadgePlacement";
                }
            });return layer.invoke(null,base,action);
        }
        @Override public int weight(Object value)throws ReflectiveOperationException{return weightValue.getInt(value);}
        @Override public Object weight(int value)throws ReflectiveOperationException{
            Object result=weights.get(value);if(result==null){result=weightConstructor.newInstance(value);if(weights.size()>=16)weights.clear();weights.put(value,result);}return result;
        }
        @Override public Object family(Context context,Object nativeFamily,String mode,String revision,int requested)throws ReflectiveOperationException{
            // OEM AndroidTypefaceWrapper ignores FontWeight and returns a pre-varied Typeface.
            // Rebuild the real wght instance before wrapping, rather than changing metadata.
            Object cacheOwner=nativeFamily==null?modifier:nativeFamily;String key=mode+':'+revision+':'+requested;
            Map<String,Object> cache=families.get(cacheOwner);if(cache!=null&&cache.containsKey(key))return cache.get(key);
            Typeface nativeFace=null;if(loadedFamily.isInstance(nativeFamily)){
                Object loaded=familyFace.invoke(nativeFamily);if(wrapper.isInstance(loaded))nativeFace=(Typeface)wrapperFace.invoke(loaded);
            }
            Typeface selected="native".equals(mode)?FontWeight.typeface(nativeFace,requested):FontRepository.typefaceForMode(context,mode,nativeFace,requested);
            int actual=FontRepository.weightForMode(mode,requested);
            selected=NativeNetworkBadgeFont.axisFace(selected,actual);
            Object result=wrapFamily.invoke(null,selected);
            if(cache==null){if(families.size()>=32)families.clear();cache=new LinkedHashMap<>();families.put(cacheOwner,cache);}
            if(cache.size()>=48)cache.clear();cache.put(key,result);return result;
        }
    }
}
