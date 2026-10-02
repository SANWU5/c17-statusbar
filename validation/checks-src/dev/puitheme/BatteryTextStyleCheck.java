package dev.puitheme;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import java.lang.reflect.Field;

/** Records copied native paint and separates dp positioning from SP font scaling. */
public final class BatteryTextStyleCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static Bundle enabled(){Bundle b=new Bundle();b.putBoolean("battery_enabled",true);b.putBoolean(BatteryTextStyle.MASTER,true);return b;}
    public static void main(String[] args)throws Exception{
        BatteryTextStyle style=new BatteryTextStyle();Paint original=new Paint(1);
        original.setTextSize(13f);original.setLetterSpacing(.15f);original.setFakeBoldText(true);
        Typeface italic=Typeface.create(Typeface.DEFAULT,450,true);original.setTypeface(italic);
        DisplayMetrics metrics=new DisplayMetrics();metrics.density=2f;metrics.scaledDensity=3f;
        style.configure(new Bundle());style.apply(original,metrics);equal(false,style.enabled());equal(13f,original.getTextSize());equal(0f,style.offsetX(2f));
        Bundle b=enabled();b.putFloat(BatteryTextStyle.X,-3f);b.putFloat(BatteryTextStyle.Y,2.5f);
        b.putFloat(BatteryTextStyle.SIZE,8f);b.putFloat(BatteryTextStyle.SPACING,1.5f);b.putFloat(BatteryTextStyle.WEIGHT,835f);
        style.configure(b);Paint copy=new Paint(1);copy.set(original);style.apply(copy,metrics);
        equal(true,style.enabled());equal(-6f,style.offsetX(2f));equal(5f,style.offsetY(2f));
        equal(24f,copy.getTextSize());equal(.125f,copy.getLetterSpacing());equal(835,copy.getTypeface().getWeight());equal(true,copy.getTypeface().isItalic());equal(false,copy.fakeBold);
        equal(13f,original.getTextSize());equal(.15f,original.getLetterSpacing());equal(italic,original.getTypeface());equal(true,original.fakeBold);
        Typeface reused=copy.getTypeface();copy.set(original);style.apply(copy,metrics);equal(reused,copy.getTypeface());
        for(float weight:new float[]{-1000f,1f,1000f,100000f}){
            b.putFloat(BatteryTextStyle.WEIGHT,weight);style.configure(b);copy.set(original);style.apply(copy,metrics);
            equal(Math.max(1,Math.min(1000,Math.round(weight))),copy.getTypeface().getWeight());
        }
        b.putFloat(BatteryTextStyle.X,Float.MAX_VALUE);b.putFloat(BatteryTextStyle.SIZE,Float.MAX_VALUE);
        b.putFloat(BatteryTextStyle.SPACING,Float.MAX_VALUE);style.configure(b);copy.set(original);style.apply(copy,metrics);
        equal(NumericPolicy.MAX_DRAW_PIXELS,style.offsetX(2f));equal(NumericPolicy.MAX_TEXT_PIXELS,copy.getTextSize());equal(10f,copy.getLetterSpacing());
        b.putFloat(BatteryTextStyle.SIZE,0f);style.configure(b);style.apply(copy,metrics);equal(0f,copy.getTextSize());equal(0f,copy.getLetterSpacing());
        b.putFloat(BatteryTextStyle.SIZE,Float.NaN);b.putFloat(BatteryTextStyle.X,Float.POSITIVE_INFINITY);style.configure(b);style.apply(copy,metrics);
        equal(30f,copy.getTextSize());equal(0f,style.offsetX(2f));
        int api=Build.VERSION.SDK_INT;Build.VERSION.SDK_INT=26;
        try{b.putFloat(BatteryTextStyle.WEIGHT,700f);style.configure(b);copy.set(original);style.apply(copy,metrics);equal(true,copy.getTypeface().isBold());equal(true,copy.getTypeface().isItalic());}
        finally{Build.VERSION.SDK_INT=api;}
        b.putBoolean(StatusBarSettings.SAFE_MODE,true);style.configure(b);copy.set(original);style.apply(copy,metrics);equal(false,style.enabled());equal(13f,copy.getTextSize());equal(0f,style.offsetY(2f));
        b=enabled();b.putBoolean("battery_enabled",false);style.configure(b);equal(false,style.enabled());
        style.configure(enabled());Field removed=ModuleLifecycle.class.getDeclaredField("removed");removed.setAccessible(true);
        try{removed.setBoolean(null,true);equal(false,style.enabled());copy.set(original);style.apply(copy,metrics);equal(13f,copy.getTextSize());}
        finally{removed.setBoolean(null,false);}
        equal(false,BatteryTextStyle.BOOLEANS.get(BatteryTextStyle.MASTER));
        actualVariationOutlines(metrics,false);
        actualVariationOutlines(metrics,true);
        System.out.println("BatteryTextStyleCheck passed: "+checks);
    }
    private static void actualVariationOutlines(DisplayMetrics metrics,boolean onPaint) {
        boolean previous=Paint.fontVariationOnPaint;
        Paint.fontVariationOnPaint=onPaint;
        try {
            Typeface nativeFace=new Typeface();nativeFace.weight=450;nativeFace.italic=true;
            nativeFace.variableWeight=true;nativeFace.variationWeight=725;
            Paint nativePaint=new Paint(1);nativePaint.setTypeface(nativeFace);nativePaint.setTextSize(13f);
            nativePaint.setFontVariationSettings("'wght' 725, 'wdth' 92, 'rond' 30");
            Typeface frozen=nativePaint.getTypeface();
            // Reproduce the bug: style metadata changes, but the actual glyph axis remains fixed.
            Paint before=new Paint(1);before.set(nativePaint);
            before.setTypeface(Typeface.create(frozen,100,true));
            equal(100,before.getTypeface().getWeight());equal(725f,before.effectiveWeight());
            BatteryTextStyle style=new BatteryTextStyle();Bundle options=enabled();
            options.putFloat(BatteryTextStyle.SIZE,8f);options.putFloat(BatteryTextStyle.SPACING,1.5f);
            options.putFloat(BatteryTextStyle.X,-3f);options.putFloat(BatteryTextStyle.Y,2.5f);
            Paint copy=new Paint(1);
            for(int weight:new int[]{100,437,900,1,1000}) {
                options.putFloat(BatteryTextStyle.WEIGHT,weight);style.configure(options);
                copy.set(nativePaint);style.apply(copy,metrics);
                equal((float)weight,copy.effectiveWeight());equal(true,copy.getTypeface().isItalic());
                equal(24f,copy.getTextSize());equal(.125f,copy.getLetterSpacing());
                equal(-6f,style.offsetX(2f));equal(5f,style.offsetY(2f));
                equal(725f,nativePaint.effectiveWeight());equal(13f,nativePaint.getTextSize());
                equal(frozen,nativePaint.getTypeface());equal("'wght' 725, 'wdth' 92, 'rond' 30",nativePaint.getFontVariationSettings());
                Typeface cached=copy.getTypeface();int creations=Typeface.variationCreations;
                for(int frame=0;frame<50;frame++){copy.set(nativePaint);style.apply(copy,metrics);}
                equal(cached,copy.getTypeface());equal(creations,Typeface.variationCreations);
                if(onPaint) {
                    equal(true,copy.getFontVariationSettings().contains("'wdth' 92.0"));
                    equal(true,copy.getFontVariationSettings().contains("'rond' 30.0"));
                } else {equal(92f,copy.getTypeface().variationWidth);equal(30f,copy.getTypeface().variationRound);}
            }
            // Native axis changes with the same face must invalidate a modern Paint-state cache.
            if(onPaint) {
                nativePaint.setFontVariationSettings("'wght' 725, 'wdth' 86");
                copy.set(nativePaint);style.apply(copy,metrics);
                equal(1000f,copy.effectiveWeight());equal(true,copy.getFontVariationSettings().contains("'wdth' 86.0"));
            }
            int api=Build.VERSION.SDK_INT;Build.VERSION.SDK_INT=26;
            try {
                BatteryTextStyle old=new BatteryTextStyle();options.putFloat(BatteryTextStyle.WEIGHT,437f);old.configure(options);
                copy.set(nativePaint);old.apply(copy,metrics);equal(437f,copy.effectiveWeight());equal(true,copy.getTypeface().isItalic());
            } finally {Build.VERSION.SDK_INT=api;}
            options.putBoolean(BatteryTextStyle.MASTER,false);style.configure(options);
            copy.set(nativePaint);style.apply(copy,metrics);equal(725f,copy.effectiveWeight());equal(frozen,copy.getTypeface());
        } finally {Paint.fontVariationOnPaint=previous;}
    }
}
