package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.Objects;

/** Replays C17 unstacked setImageResource events without any resource-wrapper/Compose path. */
public final class SingleMobileIconControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Single mobile "+checks+": "+expected+" != "+actual);}
    private static class Glyph extends Drawable {
        public void draw(Canvas canvas){ }public void setAlpha(int alpha){ }public void setColorFilter(ColorFilter filter){ }public int getOpacity(){return -3;}
    }
    private static final class Wrapped extends Glyph { }
    private static final class Binding {
        final ImageView icon=new ImageView(new Context());
        final ViewGroup host=new ViewGroup(new Context());
        Binding(){host.addView(icon);host.setTranslationX(41);host.setTranslationY(17);host.setAlpha(.7f);
            icon.setImageDrawable(new Glyph());icon.setTranslationX(3);icon.setTranslationY(-2);icon.setScaleX(.8f);icon.setScaleY(.9f);}
    }
    private static final class Native implements SingleMobileIconControls.Access {
        public ImageView icon(Object owner){return ((Binding)owner).icon;}
        public boolean rendererOwnsPlacement(Drawable drawable){return drawable instanceof Wrapped;}
    }
    private static FeatureOptions options(boolean enabled){Bundle values=new Bundle();values.putBoolean("data_enabled",enabled);return FeatureOptions.from(values);}
    public static void main(String[] ignored){
        Native nativeCode=new Native();SingleMobileIconControls helper=new SingleMobileIconControls(nativeCode);Binding row=new Binding();
        helper.bound(row);equal(3f,row.icon.getTranslationX());equal(-2f,row.icon.getTranslationY());equal(.8f,row.icon.getScaleX());
        helper.update(options(true),5,-3,150);
        equal(23f,row.icon.getTranslationX());equal(-14f,row.icon.getTranslationY());equal(1.2f,row.icon.getScaleX());equal(1.3499999f,row.icon.getScaleY());
        // Native slot animation/visibility/touch layout are not owned by the glyph setting.
        equal(41f,row.host.getTranslationX());equal(17f,row.host.getTranslationY());equal(.7f,row.host.getAlpha());equal(View.VISIBLE,row.icon.getVisibility());
        for(int i=0;i<200;i++)try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){
            equal(3f,row.icon.getTranslationX());equal(-2f,row.icon.getTranslationY());row.icon.setImageDrawable(new Glyph());
        }
        equal(23f,row.icon.getTranslationX());equal(1.2f,row.icon.getScaleX());
        // A density/native layout change is captured without feeding our previous offset back.
        try(SingleMobileIconControls.NativeScope outer=helper.beforeNative(row)){
            try(SingleMobileIconControls.NativeScope nested=helper.beforeNative(row)){
                row.icon.setTranslationX(7);row.icon.setTranslationY(9);row.icon.setScaleX(1);row.icon.setScaleY(1);
            }
            equal(7f,row.icon.getTranslationX());
        }
        equal(27f,row.icon.getTranslationX());equal(-3f,row.icon.getTranslationY());equal(1.5f,row.icon.getScaleX());
        // Same icon becoming wrapped must not receive the setting twice.
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){row.icon.setImageDrawable(new Wrapped());}
        equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.update(options(true),-2,4,200);equal(7f,row.icon.getTranslationX());equal(1f,row.icon.getScaleX());
        try(SingleMobileIconControls.NativeScope event=helper.beforeNative(row)){row.icon.setImageDrawable(new Glyph());}
        equal(-1f,row.icon.getTranslationX());equal(25f,row.icon.getTranslationY());equal(2f,row.icon.getScaleX());
        // Position and size sub-switches are independent.
        Bundle noPosition=new Bundle();noPosition.putBoolean("data_enabled",true);noPosition.putBoolean("data_position_enabled",false);
        helper.update(FeatureOptions.from(noPosition),100,100,200);equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(2f,row.icon.getScaleX());
        noPosition.putBoolean("data_size_enabled",false);helper.update(FeatureOptions.from(noPosition),100,100,200);equal(1f,row.icon.getScaleX());
        helper.update(options(false),0,0,100);equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.update(options(true),1,1,120);Bundle safe=new Bundle();safe.putBoolean("data_enabled",true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);
        helper.update(FeatureOptions.from(safe),1,1,120);equal(7f,row.icon.getTranslationX());equal(1f,row.icon.getScaleX());
        helper.update(options(true),1,1,120);helper.releaseRuntime();equal(7f,row.icon.getTranslationX());equal(9f,row.icon.getTranslationY());equal(1f,row.icon.getScaleX());
        helper.bound(row);equal(true,helper.diagnosticSummary().startsWith("Single mobile icon rows 0"));
        // Exact feedback configuration at PLK110 density; saving a negative x must
        // move the native single glyph and never the animated parent status slot.
        Binding actualRow=new Binding();float oldDensity=actualRow.icon.getResources().getDisplayMetrics().density;
        try{
            actualRow.icon.getResources().getDisplayMetrics().density=3.40625f;
            SingleMobileIconControls actualDensity=new SingleMobileIconControls(nativeCode);actualDensity.bound(actualRow);
            actualDensity.update(options(true),-5.39f,0,100);
            equal(3f+NumericPolicy.pixels(-5.39f,3.40625f),actualRow.icon.getTranslationX());equal(-2f,actualRow.icon.getTranslationY());
            equal(41f,actualRow.host.getTranslationX());equal(1f,actualRow.icon.getAlpha());
            for(float requested:new float[]{-80,80,-300,300,-5.39f}){
                actualDensity.update(options(true),requested,0,100);
                equal(3f+NumericPolicy.pixels(requested,3.40625f),actualRow.icon.getTranslationX());
                try(SingleMobileIconControls.NativeScope event=actualDensity.beforeNative(actualRow)){equal(3f,actualRow.icon.getTranslationX());}
                equal(3f+NumericPolicy.pixels(requested,3.40625f),actualRow.icon.getTranslationX());
            }
            actualDensity.releaseRuntime();equal(3f,actualRow.icon.getTranslationX());equal(-2f,actualRow.icon.getTranslationY());
        }finally{actualRow.icon.getResources().getDisplayMetrics().density=oldDensity;}
        SingleMobileIconControls removed=new SingleMobileIconControls(nativeCode);Binding removedRow=new Binding();removed.bound(removedRow);
        removed.update(options(true),10,10,200);
        SingleMobileIconControls.NativeScope pending=removed.beforeNative(removedRow);
        removed.releaseRuntime();pending.close();pending.close();
        equal(3f,removedRow.icon.getTranslationX());equal(-2f,removedRow.icon.getTranslationY());equal(.8f,removedRow.icon.getScaleX());
        equal(true,removed.diagnosticSummary().startsWith("Single mobile icon rows 0"));
        System.out.println("SingleMobileIconControls checks passed: "+checks);
    }
}
