// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.os.Build;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Changes the real wght axis, rather than only the Typeface's requested style metadata. */
public final class FontWeight {
    private static final Map<Typeface,Map<Integer,Plan>> CACHE=new WeakHashMap<>();
    private FontWeight(){ }
    private static final class Plan {
        final Typeface face;
        final String nativeAxes,axes;
        final boolean onPaint;
        Plan(Typeface face,String nativeAxes,String axes,boolean onPaint){
            this.face=face;this.nativeAxes=nativeAxes;this.axes=axes;this.onPaint=onPaint;
        }
    }
    public static synchronized void release(){CACHE.clear();}
    private static boolean same(String a,String b){return a==b||(a!=null&&a.equals(b));}
    private static synchronized Plan plan(Typeface family,int requested,String nativeAxes){
        Typeface base=family==null?Typeface.DEFAULT:family;
        int weight=Math.max(1,Math.min(1000,requested));
        Map<Integer,Plan> weights=CACHE.get(base);
        if(weights==null){
            if(CACHE.size()>=32)CACHE.clear();
            weights=new LinkedHashMap<Integer,Plan>(16,.75f,true){
                @Override protected boolean removeEldestEntry(Map.Entry<Integer,Plan> item){return size()>48;}
            };
            CACHE.put(base,weights);
        }
        Plan result=weights.get(weight);
        if(result!=null&&same(result.nativeAxes,nativeAxes))return result;
        result=null;
        Typeface fallback=Build.VERSION.SDK_INT>=28?Typeface.create(base,weight,base.isItalic())
                :Typeface.create(base,(weight>=600?Typeface.BOLD:Typeface.NORMAL)
                        |(base.isItalic()?Typeface.ITALIC:Typeface.NORMAL));
        Paint probe=new Paint(Paint.ANTI_ALIAS_FLAG);probe.setTypeface(fallback);
        String axes=withWeight(nativeAxes,weight);
        try{
            if(probe.setFontVariationSettings(axes)){
                Typeface face=probe.getTypeface();
                result=new Plan(face==null?fallback:face,nativeAxes,axes,face==fallback);
            }
        }catch(IllegalArgumentException unsupported){ /* Static/OEM families retain their real style match. */ }
        if(result==null)result=new Plan(fallback,nativeAxes,null,false);
        weights.put(weight,result);return result;
    }
    private static String withWeight(String original,int weight){
        StringBuilder result=new StringBuilder("'wght' ").append(weight);
        if(original!=null&&!original.isEmpty())try{
            FontVariationAxis[] axes=FontVariationAxis.fromFontVariationSettings(original);
            if(axes!=null)for(FontVariationAxis axis:axes)if(!"wght".equals(axis.getTag()))
                result.append(", '").append(axis.getTag()).append("' ").append(axis.getStyleValue());
        }catch(IllegalArgumentException invalid){ /* A native invalid setting is not forwarded. */ }
        return result.toString();
    }
    /** Useful for callers that own a Typeface; modern independent Paint axes still need apply(). */
    public static Typeface typeface(Typeface family,int weight){return plan(family,weight,null).face;}
    public static void apply(Paint paint,Typeface family,int weight,String nativeAxes){
        Plan plan=plan(family,weight,nativeAxes);
        if(paint.getTypeface()!=plan.face)paint.setTypeface(plan.face);
        // Old Android bakes the variation in the cached face. Repeating the setter there would
        // construct another face on every copied-Paint draw. New Android overrides it on Paint.
        if(plan.onPaint&&!same(paint.getFontVariationSettings(),plan.axes))paint.setFontVariationSettings(plan.axes);
    }
    public static void apply(TextView view,Typeface family,int weight,String nativeAxes){
        Plan plan=plan(family,weight,nativeAxes);
        if(view.getTypeface()!=plan.face)view.setTypeface(plan.face);
        if(!same(view.getFontVariationSettings(),plan.axes))try{view.setFontVariationSettings(plan.axes);}
        catch(IllegalArgumentException unsupported){ /* Keep the cached static/native style match. */ }
        // Keep the old framework's Paint settings string aligned, then reuse the prebuilt face.
        // This also avoids its newly created face becoming a different target every next frame.
        if(!plan.onPaint&&view.getTypeface()!=plan.face)view.setTypeface(plan.face);
    }
    public static void restore(Paint paint,Typeface nativeFace,String nativeAxes){
        if(paint.getTypeface()!=nativeFace)paint.setTypeface(nativeFace);
        if(!same(paint.getFontVariationSettings(),nativeAxes))try{paint.setFontVariationSettings(nativeAxes);}
        catch(IllegalArgumentException unsupported){ }
        // Clearing an old Paint's settings can itself replace a baked native HGHT/width face.
        if(paint.getTypeface()!=nativeFace)paint.setTypeface(nativeFace);
    }
    public static void restore(TextView view,Typeface nativeFace,String nativeAxes){
        if(view.getTypeface()!=nativeFace)view.setTypeface(nativeFace);
        if(!same(view.getFontVariationSettings(),nativeAxes))try{view.setFontVariationSettings(nativeAxes);}
        catch(IllegalArgumentException unsupported){ }
        if(view.getTypeface()!=nativeFace)view.setTypeface(nativeFace);
    }
}
