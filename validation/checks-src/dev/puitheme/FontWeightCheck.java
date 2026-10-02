package dev.puitheme;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.Collections;

/** Exercises the glyph axis itself, including both framework variation implementations. */
public final class FontWeightCheck {
    private static int checks;
    private static final String NATIVE="'wght' 725, 'wdth' 92, 'rond' 30, 'HGHT' 70";
    private static void equal(Object expected,Object actual){checks++;if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);}
    private static Typeface frozen(){
        Typeface face=new Typeface();face.weight=450;face.italic=true;face.variableWeight=true;
        face.variationWeight=725;face.variationWidth=92;face.variationRound=30;return face;
    }
    private static TextView nativeView(Context context){TextView view=new TextView(context);initialize(view);return view;}
    private static void initialize(TextView view){view.setTypeface(frozen());view.setFontVariationSettings(NATIVE);view.setLetterSpacing(.15f);}
    private static void helpers(boolean onPaint){
        Paint.fontVariationOnPaint=onPaint;FontRepository.releaseRuntime();Context context=new Context();
        TextView original=nativeView(context);Typeface nativeFace=original.getTypeface();
        Typeface oldStyle=Typeface.create(nativeFace,100,true);
        equal(100,oldStyle.getWeight());near(725,oldStyle.effectiveWeight());
        for(int weight:new int[]{100,437,900,1,1000}){
            TextView view=nativeView(context);Typeface baseline=view.getTypeface();
            FontRepository.apply(view,baseline,weight,NATIVE);
            near(weight,view.getPaint().effectiveWeight());equal(true,view.getTypeface().isItalic());
            near(.15f,view.getLetterSpacing());near(13,view.getTextSize());
            equal(true,view.getFontVariationSettings().contains("'wdth' 92.0"));
            equal(true,view.getFontVariationSettings().contains("'HGHT' 70.0"));
            int creations=Typeface.variationCreations,layouts=view.layoutRequests,axes=view.variationWrites,faces=view.typefaceWrites;
            for(int i=0;i<1000;i++)FontRepository.apply(view,baseline,weight,NATIVE);
            equal(creations,Typeface.variationCreations);equal(layouts,view.layoutRequests);
            equal(axes,view.variationWrites);equal(faces,view.typefaceWrites);
            FontWeight.restore(view,baseline,NATIVE);equal(baseline,view.getTypeface());near(725,view.getPaint().effectiveWeight());equal(NATIVE,view.getFontVariationSettings());
            layouts=view.layoutRequests;for(int i=0;i<1000;i++)FontWeight.restore(view,baseline,NATIVE);equal(layouts,view.layoutRequests);
            Paint copy=new Paint(1);copy.set(original.getPaint());FontRepository.apply(copy,nativeFace,weight,NATIVE);
            near(weight,copy.effectiveWeight());equal(true,copy.getTypeface().isItalic());
            near(725,original.getPaint().effectiveWeight());equal(NATIVE,original.getFontVariationSettings());
            creations=Typeface.variationCreations;for(int i=0;i<1000;i++)FontRepository.apply(copy,nativeFace,weight,NATIVE);equal(creations,Typeface.variationCreations);
            FontWeight.restore(copy,nativeFace,NATIVE);equal(nativeFace,copy.getTypeface());near(725,copy.effectiveWeight());
        }
        FontRepository.apply(original,nativeFace,900,"'wght' 500, 'wdth' 86");
        near(900,original.getPaint().effectiveWeight());equal(true,original.getFontVariationSettings().contains("'wdth' 86.0"));equal(false,original.getFontVariationSettings().contains("HGHT"));
        int sdk=Build.VERSION.SDK_INT;Build.VERSION.SDK_INT=26;FontWeight.release();
        TextView api26=nativeView(context);FontRepository.apply(api26,api26.getTypeface(),437,NATIVE);
        near(437,api26.getPaint().effectiveWeight());equal(true,api26.getTypeface().isItalic());
        Build.VERSION.SDK_INT=sdk;FontWeight.release();
        Typeface staticFace=new Typeface();staticFace.italic=true;TextView staticView=new TextView(context);staticView.setTypeface(staticFace);
        FontWeight.apply(staticView,staticFace,900,null);equal(true,staticView.getTypeface().isBold());equal(true,staticView.getTypeface().isItalic());
        int writes=staticView.typefaceWrites;for(int i=0;i<1000;i++)FontWeight.apply(staticView,staticFace,900,null);equal(writes,staticView.typefaceWrites);
    }
    private static void textScenes(boolean onPaint){
        Paint.fontVariationOnPaint=onPaint;FontRepository.releaseRuntime();Context context=new Context();
        TextControls control=new TextControls(new Handler(Looper.getMainLooper()));
        TextView status=new com.oplus.systemui.statusbar.widget.StatClock(context);
        TextView shade=new com.oplus.systemui.qs.widget.SimpleQsClock(context);
        TextView notification=new com.oplus.systemui.qs.widget.OplusSecondCarrierText(context);
        TextView qs=new com.oplus.systemui.qs.widget.OplusSecondCarrierText(context);
        TextView lock=new com.android.keyguard.CarrierText(context);
        com.oplus.systemui.separate.OplusQSSimpleHeader notificationHeader=new com.oplus.systemui.separate.OplusQSSimpleHeader(context);
        notificationHeader.addView(shade);notificationHeader.addView(notification);
        new com.oplus.systemui.qs.OplusQuickStatusBarHeader(context).addView(qs);
        new com.android.systemui.statusbar.phone.KeyguardStatusBarView(context).addView(lock);
        TextView[] views={status,shade,notification,qs,lock};Typeface[] originals=new Typeface[views.length];
        for(int i=0;i<views.length;i++){initialize(views[i]);originals[i]=views[i].getTypeface();control.attach(views[i]);near(725,views[i].getPaint().effectiveWeight());}
        Bundle settings=new Bundle();settings.putBoolean("clock_controls_enabled",true);settings.putBoolean("shade_clock_controls_enabled",true);
        for(String group:CarrierPanels.GROUPS)settings.putBoolean(CarrierPanels.key(group,"enabled"),true);
        for(int weight:new int[]{100,437,900}){
            settings.putFloat("clock_weight",weight);settings.putFloat("shade_clock_weight",weight);
            for(String group:CarrierPanels.GROUPS)settings.putFloat(CarrierPanels.key(group,"weight"),weight);
            control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
            for(TextView view:views){
                near(weight,view.getPaint().effectiveWeight());equal(true,view.getTypeface().isItalic());
                int layouts=view.layoutRequests,axes=view.variationWrites,creations=Typeface.variationCreations;
                for(int i=0;i<1000;i++)control.beforeMeasure(view);
                equal(layouts,view.layoutRequests);equal(axes,view.variationWrites);equal(creations,Typeface.variationCreations);
            }
        }
        control.enter();status.setFontVariationSettings("'wght' 510, 'wdth' 86");control.exit();
        control.nativeFontVariation(status);control.beforeMeasure(status);near(900,status.getPaint().effectiveWeight());equal(true,status.getFontVariationSettings().contains("'wdth' 86.0"));
        settings.putBoolean(StatusBarSettings.SAFE_MODE,true);control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        near(510,status.getPaint().effectiveWeight());equal("'wght' 510, 'wdth' 86",status.getFontVariationSettings());
        for(int i=1;i<views.length;i++){equal(originals[i],views[i].getTypeface());near(725,views[i].getPaint().effectiveWeight());equal(NATIVE,views[i].getFontVariationSettings());}
        settings.putBoolean(StatusBarSettings.SAFE_MODE,false);control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());near(900,status.getPaint().effectiveWeight());
        control.configure(new Bundle(),StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());near(510,status.getPaint().effectiveWeight());
        Bundle nativeSystem=new Bundle();nativeSystem.putBoolean("font_enabled",true);nativeSystem.putString(StatusBarSettings.FONT_MODE,"system");nativeSystem.putBoolean("clock_controls_enabled",true);nativeSystem.putBoolean("clock_text_style_enabled",false);
        control.configure(nativeSystem,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());near(510,status.getPaint().effectiveWeight());control.setInteractive(false);
    }
    private static void nativeClockPaint(boolean onPaint)throws Exception{
        Paint.fontVariationOnPaint=onPaint;FontRepository.releaseRuntime();Context context=new Context();
        Method apply=NotificationNativeClock.class.getDeclaredMethod("applyLayerFont",TextView.class,Typeface.class,String.class,Paint.class);apply.setAccessible(true);
        TextView source=nativeView(context),visible=nativeView(context),outgoing=nativeView(context);
        Typeface selected=FontRepository.typeface(source.getTypeface(),100);Paint rt=new Paint(1);rt.set(source.getPaint());
        String axes="'wght' 100, 'wdth' 92";
        apply.invoke(null,visible,selected,axes,rt);apply.invoke(null,outgoing,selected,axes,outgoing.getPaint());
        near(100,visible.getPaint().effectiveWeight());near(100,outgoing.getPaint().effectiveWeight());near(100,rt.effectiveWeight());
        near(725,source.getPaint().effectiveWeight());equal(NATIVE,source.getFontVariationSettings());
        Typeface hght=frozen();hght.variationWeight=703;apply.invoke(null,visible,hght,null,rt);
        equal(hght,visible.getTypeface());equal(hght,rt.getTypeface());equal(null,visible.getFontVariationSettings());equal(null,rt.getFontVariationSettings());near(703,visible.getPaint().effectiveWeight());near(703,rt.effectiveWeight());
    }
    public static void main(String[] args)throws Exception{
        boolean old=Paint.fontVariationOnPaint;int sdk=Build.VERSION.SDK_INT;
        try{for(boolean mode:new boolean[]{false,true}){helpers(mode);textScenes(mode);nativeClockPaint(mode);}}
        finally{Paint.fontVariationOnPaint=old;Build.VERSION.SDK_INT=sdk;FontRepository.releaseRuntime();}
        System.out.println(checks+" real glyph weight, scene ownership, native axes, RT Paint and steady-cache checks passed");
    }
}
