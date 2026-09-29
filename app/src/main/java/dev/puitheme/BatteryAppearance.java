package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps one native palette and one transform for the complete battery on each surface. */
public final class BatteryAppearance {
    private final Field progress, background, outline;
    private final Method setColors;
    private final Map<Drawable, Palette> palettes = new WeakHashMap<>();
    private final Map<ViewGroup, Clip> clips = new WeakHashMap<>();
    private final Map<View, List<WeakReference<ViewGroup>>> leases = new WeakHashMap<>();
    private Map<String,Integer> colors = StatusBarSettings.COLOR_DEFAULTS;
    private Map<String,Boolean> alpha = Collections.emptyMap();
    private float x, y, scale = 1f, width = 1f, height = 1f;
    private boolean applying;

    private static final class Palette {
        final int progress, background, outline;
        Palette(int progress, int background, int outline) {
            this.progress=progress;this.background=background;this.outline=outline;
        }
    }
    private static final class Clip {
        final boolean children, padding;
        int count;
        Clip(ViewGroup view) {children=view.getClipChildren();padding=view.getClipToPadding();}
    }
    public BatteryAppearance(Class<?> bar) throws Exception {
        progress=field(bar,"progressColor");background=field(bar,"backgroundColor");outline=field(bar,"outlineColor");
        setColors=bar.getMethod("setColors",Integer.TYPE,Integer.TYPE,Integer.TYPE);
    }
    private static Field field(Class<?> type,String name) throws Exception {
        Field result=type.getDeclaredField(name);result.setAccessible(true);return result;
    }
    public boolean isApplying() {return applying;}
    public void configure(Bundle settings,Map<String,Integer> colors,Map<String,Boolean> alpha) {
        this.colors=colors;this.alpha=alpha;
        x=value(settings,StatusBarSettings.BATTERY_OFFSET_X,0f,-80,80);
        y=value(settings,StatusBarSettings.BATTERY_OFFSET_Y,0f,-24,24);
        scale=value(settings,StatusBarSettings.BATTERY_SCALE,100f,25,250)/100f;
        width=value(settings,StatusBarSettings.BATTERY_WIDTH_SCALE,100f,25,250)/100f;
        height=value(settings,StatusBarSettings.BATTERY_HEIGHT_SCALE,100f,25,250)/100f;
        if (!transformed()) for(View owner:leases.keySet().toArray(new View[0])) detach(owner);
    }
    private static float value(Bundle values,String key,float fallback,float min,float max) {
        Object stored=values.get(key);
        float result=stored instanceof Number?((Number)stored).floatValue():fallback;
        if(Float.isNaN(result)||Float.isInfinite(result))result=fallback;
        return Math.max(min,Math.min(max,result));
    }
    private boolean transformed() {return x!=0f||y!=0f||scale!=1f||width!=1f||height!=1f;}

    /** The scene is chosen from the original outline, never from a previously customized color. */
    public boolean hasCustom(String item) {
        String light=item+"_color_light",dark=item+"_color_dark";
        return (colors.get(light)&0xffffff)!=(StatusBarSettings.COLOR_DEFAULTS.get(light)&0xffffff)
                ||(colors.get(dark)&0xffffff)!=(StatusBarSettings.COLOR_DEFAULTS.get(dark)&0xffffff)
                ||Boolean.TRUE.equals(alpha.get(light))||Boolean.TRUE.equals(alpha.get(dark));
    }
    private int tint(String item,int nativeColor,int scene) {
        String light=item+"_color_light",dark=item+"_color_dark";
        Integer lightDefault=StatusBarSettings.COLOR_DEFAULTS.get(light),darkDefault=StatusBarSettings.COLOR_DEFAULTS.get(dark);
        int lightColor=colors.containsKey(light)?colors.get(light):lightDefault;
        int darkColor=colors.containsKey(dark)?colors.get(dark):darkDefault;
        if ((lightColor&0xffffff)==(lightDefault&0xffffff)&&(darkColor&0xffffff)==(darkDefault&0xffffff)
                &&!Boolean.TRUE.equals(alpha.get(light))&&!Boolean.TRUE.equals(alpha.get(dark))) return nativeColor;
        int result=IconAppearance.color(item,(nativeColor&0xff000000)|(scene&0xffffff),colors,alpha);
        if(item.equals("battery")&&(Boolean.TRUE.equals(alpha.get(light))||Boolean.TRUE.equals(alpha.get(dark)))) {
            float fraction=(.2126f*((scene>>>16)&255)+.7152f*((scene>>>8)&255)+.0722f*(scene&255))/255f;
            int nativeAlpha=nativeColor>>>24;
            float ratio=Math.min(1f,nativeAlpha/(float)Math.max(1,scene>>>24));
            float start=Boolean.TRUE.equals(alpha.get(light))?(lightColor>>>24)*ratio:nativeAlpha;
            float end=Boolean.TRUE.equals(alpha.get(dark))?(darkColor>>>24)*ratio:nativeAlpha;
            result=(result&0xffffff)|(Math.round(start+(end-start)*fraction)<<24);
        }
        return result;
    }
    private Object[] adjusted(Palette nativeColors,boolean charging) {
        String fill=charging?"battery_charge":(nativeColors.progress&0xffffff)!=(nativeColors.outline&0xffffff)?"battery_alert":"battery";
        return new Object[]{tint(fill,nativeColors.progress,nativeColors.outline),
                tint("battery",nativeColors.background,nativeColors.outline),
                tint("battery",nativeColors.outline,nativeColors.outline)};
    }
    public Object[] nativeColors(Drawable drawable,int progress,int background,int outline,boolean charging) {
        Palette nativeColors=new Palette(progress,background,outline);
        palettes.put(drawable,nativeColors);
        return adjusted(nativeColors,charging);
    }
    public void apply(Drawable drawable,boolean charging) throws Exception {
        if (drawable==null) return;
        Palette nativeColors=palettes.get(drawable);
        if (nativeColors==null) {
            nativeColors=new Palette(progress.getInt(drawable),background.getInt(drawable),outline.getInt(drawable));
            palettes.put(drawable,nativeColors);
        }
        Object[] next=adjusted(nativeColors,charging);
        if (progress.getInt(drawable)==(Integer)next[0]&&background.getInt(drawable)==(Integer)next[1]
                &&outline.getInt(drawable)==(Integer)next[2]) return;
        applying=true;
        try {setColors.invoke(drawable,next);} finally {applying=false;}
        drawable.invalidateSelf();
    }
    public int contentTint(String item,Drawable drawable,int nativeColor) throws Exception {
        Palette nativeColors=palettes.get(drawable);
        return tint(item,nativeColor,nativeColors!=null?nativeColors.outline:outline.getInt(drawable));
    }
    public int nativeOutline(Drawable drawable) throws Exception {
        Palette nativeColors=palettes.get(drawable);
        return nativeColors!=null?nativeColors.outline:outline.getInt(drawable);
    }
    public void updateView(View owner) {
        if (!transformed()) {detach(owner);return;}
        List<ViewGroup> parents=new ArrayList<>();
        ViewParent parent=owner instanceof ViewGroup?(ViewGroup)owner:owner.getParent();
        for(int depth=0;parent instanceof ViewGroup&&depth<12;depth++) {
            parents.add((ViewGroup)parent);parent=parent.getParent();
        }
        List<WeakReference<ViewGroup>> previous=leases.get(owner);
        boolean same=previous!=null&&previous.size()==parents.size();
        if (same) for(int i=0;i<parents.size();i++) if(previous.get(i).get()!=parents.get(i)) {same=false;break;}
        if (!same) {
            detach(owner);
            List<WeakReference<ViewGroup>> next=new ArrayList<>();
            for(ViewGroup view:parents) {
                Clip clip=clips.get(view);
                if(clip==null) {clip=new Clip(view);clips.put(view,clip);}
                clip.count++;next.add(new WeakReference<>(view));
            }
            leases.put(owner,next);
        }
        for(ViewGroup view:parents) {view.setClipChildren(false);view.setClipToPadding(false);}
    }
    public void beforeDraw(View owner,Canvas canvas) {
        updateView(owner);
        float density=owner.getResources().getDisplayMetrics().density;
        canvas.translate(x*density,y*density);
        canvas.scale(scale*width,scale*height,owner.getWidth()/2f,owner.getHeight()/2f);
    }
    public void detach(View owner) {
        List<WeakReference<ViewGroup>> previous=leases.remove(owner);
        if(previous==null)return;
        for(WeakReference<ViewGroup> reference:previous) {
            ViewGroup view=reference.get();Clip clip=view==null?null:clips.get(view);
            if(clip!=null&&--clip.count==0) {
                view.setClipChildren(clip.children);view.setClipToPadding(clip.padding);clips.remove(view);
            }
        }
    }
}
