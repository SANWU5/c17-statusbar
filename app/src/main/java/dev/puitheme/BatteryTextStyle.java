// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Typography is applied only to a copied paint; the native battery paint is never owned. */
public final class BatteryTextStyle {
    public static final String MASTER = "battery_text_style_enabled";
    public static final String X = "battery_text_offset_x", Y = "battery_text_offset_y";
    public static final String SIZE = "battery_text_size", WEIGHT = "battery_text_weight", SPACING = "battery_text_spacing";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    static {
        Map<String,Boolean> booleans=new LinkedHashMap<>();booleans.put(MASTER,false);
        BOOLEANS=Collections.unmodifiableMap(booleans);
        Map<String,Float> numbers=new LinkedHashMap<>();
        numbers.put(X,0f);numbers.put(Y,0f);numbers.put(SIZE,10f);numbers.put(WEIGHT,600f);numbers.put(SPACING,0f);
        NUMBERS=Collections.unmodifiableMap(numbers);
    }
    private static final class WeightedFont {
        final Typeface face;
        final String nativeSettings,drawSettings;
        // Old Paint implementations bake variations into Typeface. New ones store them on Paint.
        final boolean paintOverride;
        WeightedFont(Typeface face,String nativeSettings,String drawSettings,boolean paintOverride) {
            this.face=face;this.nativeSettings=nativeSettings;this.drawSettings=drawSettings;
            this.paintOverride=paintOverride;
        }
    }
    private final Map<Typeface,WeightedFont> weightedFaces=new WeakHashMap<>();
    private boolean enabled;
    private float x,y,size=10f,spacing;
    private int weight=600;

    public void configure(Bundle settings) {
        enabled=settings!=null&&FeatureOptions.from(settings).enabled("battery")
                &&Boolean.TRUE.equals(settings.get(MASTER))&&!ModuleLifecycle.removed();
        x=number(settings,X);y=number(settings,Y);size=number(settings,SIZE);spacing=number(settings,SPACING);
        int next=Math.max(1,Math.min(1000,Math.round(number(settings,WEIGHT))));
        if(next!=weight||!enabled)weightedFaces.clear();
        weight=next;
    }
    private static float number(Bundle settings,String key) {
        return NumericPolicy.finite(settings==null?null:settings.get(key),NUMBERS.get(key));
    }
    public boolean enabled() {return enabled&&!ModuleLifecycle.removed();}
    public float offsetX(float density) {return enabled()?NumericPolicy.pixels(x,density):0f;}
    public float offsetY(float density) {return enabled()?NumericPolicy.pixels(y,density):0f;}

    public void apply(Paint paint,DisplayMetrics metrics) {
        if(!enabled())return;
        // applyDimension follows Android's SP conversion, including nonlinear font scaling.
        float pixels=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,Math.max(0f,size),metrics);
        float textSize=NumericPolicy.textPixels(pixels);
        paint.setTextSize(textSize);
        float gap=NumericPolicy.pixels(spacing,metrics.density);
        paint.setLetterSpacing(textSize>0f?Math.max(-10f,Math.min(10f,gap/textSize)):0f);
        Typeface original=paint.getTypeface();
        Typeface base=original==null?Typeface.DEFAULT:original;
        String nativeSettings=paint.getFontVariationSettings();
        WeightedFont font=weightedFaces.get(base);
        if(font==null||!same(nativeSettings,font.nativeSettings)) {
            font=weightedFont(base,nativeSettings);
            if(weightedFaces.size()>8)weightedFaces.clear();
            weightedFaces.put(base,font);
        }
        paint.setTypeface(font.face);
        // setTypeface does not replace a modern Paint's independent wght override. On the older
        // implementation the cached face already contains the requested outlines; reapplying the
        // string here would create another variable Typeface on every battery frame.
        if(font.paintOverride)paint.setFontVariationSettings(font.drawSettings);
        paint.setFakeBoldText(false);
    }

    private WeightedFont weightedFont(Typeface base,String nativeSettings) {
        Typeface fallback;
        if(Build.VERSION.SDK_INT>=28)fallback=Typeface.create(base,weight,base.isItalic());
        else fallback=Typeface.create(base,(weight>=600?Typeface.BOLD:Typeface.NORMAL)
                |(base.isItalic()?Typeface.ITALIC:Typeface.NORMAL));
        String settings=withWeight(nativeSettings,weight);
        Paint probe=new Paint(Paint.ANTI_ALIAS_FLAG);
        probe.setTypeface(fallback);
        try {
            // SystemUI's native helper can supply a Typeface whose FontCollection has a fixed
            // wght axis. A style-only Typeface.create request cannot change that collection.
            // Use the public variation API to override the actual glyph axis, including API 26.
            if(probe.setFontVariationSettings(settings)) {
                Typeface face=probe.getTypeface();
                return new WeightedFont(face==null?fallback:face,nativeSettings,settings,face==fallback);
            }
        } catch(IllegalArgumentException ignored) {
            // Some OEM/static families reject variation settings; keep their native style match.
        }
        return new WeightedFont(fallback,nativeSettings,null,false);
    }

    private static String withWeight(String nativeSettings,int weight) {
        StringBuilder settings=new StringBuilder("'wght' ").append(weight);
        if(nativeSettings!=null&&!nativeSettings.isEmpty())try {
            FontVariationAxis[] axes=FontVariationAxis.fromFontVariationSettings(nativeSettings);
            if(axes!=null)for(FontVariationAxis axis:axes)if(!"wght".equals(axis.getTag()))
                settings.append(", '").append(axis.getTag()).append("' ").append(axis.getStyleValue());
        } catch(IllegalArgumentException ignored) { }
        return settings.toString();
    }
    private static boolean same(String left,String right) {
        return left==right||(left!=null&&left.equals(right));
    }
}
