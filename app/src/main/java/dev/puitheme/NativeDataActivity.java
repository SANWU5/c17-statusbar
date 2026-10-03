// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.reflect.Method;

/** Styles only verified cellular activity resources; native direction/animation stays intact. */
public final class NativeDataActivity {
    public static final String MASTER="native_data_activity_enabled",X="native_data_activity_offset_x",
            Y="native_data_activity_offset_y",SCALE="native_data_activity_scale",
            COLOR_ENABLED="native_data_activity_color_enabled",COLOR_LIGHT="native_data_activity_color_light",
            COLOR_DARK="native_data_activity_color_dark";
    private volatile boolean enabled,color;
    private volatile float x,y,scale=100f;
    private Map<String,Integer> colors=Collections.emptyMap();
    private Map<String,Boolean> alphas=Collections.emptyMap();
    private final Map<ArrowDrawable,Boolean> live=new WeakHashMap<>();
    public void configure(Bundle source) {
        enabled=source!=null&&!SafetyMode.enabled(source)&&source.getBoolean(MASTER,false)&&!ModuleLifecycle.removed();
        color=enabled&&source.getBoolean(COLOR_ENABLED,false);
        x=number(source,X,0f);y=number(source,Y,0f);scale=Math.max(0f,number(source,SCALE,100f));
        Map<String,Integer> nextColors=new LinkedHashMap<>();Map<String,Boolean> nextAlpha=new LinkedHashMap<>();
        for(String key:new String[]{COLOR_LIGHT,COLOR_DARK}) {
            nextColors.put(key,source==null?(key.equals(COLOR_LIGHT)?0xff000000:0xffffffff):source.getInt(key,key.equals(COLOR_LIGHT)?0xff000000:0xffffffff));
            nextAlpha.put(key,source!=null&&source.getBoolean(key+"_custom_alpha",false));
        }
        colors=Collections.unmodifiableMap(nextColors);alphas=Collections.unmodifiableMap(nextAlpha);
        for(ArrowDrawable drawable:new ArrayList<>(live.keySet()))if(drawable!=null)drawable.invalidateSelf();
    }
    private static float number(Bundle b,String key,float fallback){Object v=b==null?null:b.get(key);return v instanceof Number&&Float.isFinite(((Number)v).floatValue())?((Number)v).floatValue():fallback;}
    public boolean enabled(){return enabled&&!ModuleLifecycle.removed();}
    public boolean colorEnabled(){return enabled()&&color;}
    public float x(float density){return enabled()?NumericPolicy.pixels(x,density):0f;}
    public float y(float density){return enabled()?NumericPolicy.pixels(y,density):0f;}
    public float scale(float extent){return enabled()?NumericPolicy.scale(scale/100f,Math.max(1f,extent)):1f;}
    public int tint(int nativeTint){return colorEnabled()?IconAppearance.color("native_data_activity",nativeTint,colors,alphas):nativeTint;}
    static boolean source(String name){
        return name!=null&&name.matches("stat_signal_(activity|activity_soft|soft_stacked_activity|stacked_activity)_(default|in|out|inout)_public(?:_os17)?");
    }
    public Drawable wrap(Resources resources,int id,Drawable nativeDrawable) {
        if(nativeDrawable==null||nativeDrawable instanceof ArrowDrawable||id==0||id==-1)return nativeDrawable;
        try {
            if(!"com.android.systemui".equals(resources.getResourcePackageName(id))||!source(resources.getResourceEntryName(id)))return nativeDrawable;
            ArrowDrawable result=new ArrowDrawable(nativeDrawable.mutate(),resources.getDisplayMetrics().density);
            live.put(result,Boolean.TRUE);return result;
        }catch(RuntimeException unavailable){return nativeDrawable;}
    }
    public void releaseRuntime(){configure(null);live.clear();}
    public String diagnosticSummary(){int compose=0;for(ArrowDrawable drawable:new ArrayList<>(live.keySet()))if(drawable!=null&&drawable.composePlacement)compose++;return "arrows="+enabled()+",customColor="+colorEnabled()+",live="+live.size()+",compose="+compose;}
    public final class ArrowDrawable extends Drawable implements Drawable.Callback {
        private final Drawable delegate;public final float density;
        public boolean composePlacement;public int nativeTint=0xffffffff;
        private ColorFilter nativeFilter;private boolean customFilter;private int appliedTint;
        ArrowDrawable(Drawable drawable,float density){delegate=drawable;this.density=density;nativeFilter=drawable.getColorFilter();delegate.setCallback(this);}
        @Override public void draw(Canvas canvas) {
            if(colorEnabled()&&!composePlacement){int next=tint(nativeTint);if(!customFilter||appliedTint!=next){delegate.setColorFilter(next,PorterDuff.Mode.SRC_IN);customFilter=true;appliedTint=next;}}
            else if(customFilter){delegate.setColorFilter(nativeFilter);customFilter=false;}
            int saved=canvas.save();
            try {
                if(enabled()&&!composePlacement){Rect bounds=getBounds();float size=scale(Math.max(bounds.width(),bounds.height()));canvas.translate(x(density),y(density));canvas.scale(size,size,bounds.exactCenterX(),bounds.exactCenterY());}
                delegate.draw(canvas);
            }finally{canvas.restoreToCount(saved);}
        }
        @Override protected void onBoundsChange(Rect bounds){delegate.setBounds(bounds);}
        @Override public void setAlpha(int alpha){delegate.setAlpha(alpha);}
        @Override public int getAlpha(){return delegate.getAlpha();}
        @Override public void setColorFilter(ColorFilter filter){nativeFilter=filter;customFilter=false;delegate.setColorFilter(filter);try{if(filter instanceof PorterDuffColorFilter){Method getter=filter.getClass().getDeclaredMethod("getColor");getter.setAccessible(true);Object value=getter.invoke(filter);if(value instanceof Integer)nativeTint=(Integer)value;}}catch(ReflectiveOperationException|RuntimeException unsupported){/* Compose supplies its actual native tint separately. */}}
        @Override public ColorFilter getColorFilter(){return delegate.getColorFilter();}
        @Override public int getOpacity(){return delegate.getOpacity();}
        @Override public int getIntrinsicWidth(){return delegate.getIntrinsicWidth();}
        @Override public int getIntrinsicHeight(){return delegate.getIntrinsicHeight();}
        @Override public boolean isStateful(){return delegate.isStateful();}
        @Override protected boolean onStateChange(int[] state){return delegate.setState(state);}
        @Override protected boolean onLevelChange(int level){return delegate.setLevel(level);}
        @Override public boolean setVisible(boolean visible,boolean restart){return super.setVisible(visible,restart)|delegate.setVisible(visible,restart);}
        @Override public void invalidateDrawable(Drawable who){invalidateSelf();}
        @Override public void scheduleDrawable(Drawable who,Runnable task,long when){scheduleSelf(task,when);}
        @Override public void unscheduleDrawable(Drawable who,Runnable task){unscheduleSelf(task);}
    }
}
