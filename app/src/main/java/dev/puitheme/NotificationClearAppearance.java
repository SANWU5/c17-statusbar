// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Recolors only the confirmed native clear button's base material during its own draw. */
public final class NotificationClearAppearance {
    public interface DrawAction { void draw(Canvas canvas) throws Throwable; }
    public static final String BUTTON_CLASS="com.oplus.systemui.notification.clearall.OplusClearAllButton";
    public static final String CONTROLLER_CLASS="com.oplus.systemui.notification.clearall.ClearAllController";
    public static final String MASTER="notification_clear_custom_enabled";
    public static final String GRADIENT_ENABLED="notification_clear_gradient_enabled";
    public static final String COLOR_LIGHT="notification_clear_color_light";
    public static final String COLOR_DARK="notification_clear_color_dark";
    public static final String GRADIENT_COLOR_LIGHT="notification_clear_gradient_color_light";
    public static final String GRADIENT_COLOR_DARK="notification_clear_gradient_color_dark";
    public static final String OPACITY="notification_clear_opacity";
    public static final String GRADIENT_ANGLE="notification_clear_gradient_angle";
    public static final Map<String,Boolean> BOOLEANS;
    public static final Map<String,Float> NUMBERS;
    public static final Map<String,Integer> COLORS;
    static {
        Map<String,Boolean> flags=new LinkedHashMap<>();flags.put(MASTER,false);flags.put(GRADIENT_ENABLED,false);
        flags.put(NotificationClearMotion.MOTION_ENABLED,false);
        Map<String,Float> numbers=new LinkedHashMap<>();numbers.put(OPACITY,100f);numbers.put(GRADIENT_ANGLE,90f);
        numbers.put(NotificationClearMotion.SAFE_DISTANCE,18f);numbers.put(NotificationClearMotion.ENTRY_TRAVEL,32f);
        numbers.put(NotificationClearMotion.OFFSET_Y,0f);
        Map<String,Integer> colors=new LinkedHashMap<>();
        colors.put(COLOR_LIGHT,0xffffffff);colors.put(COLOR_DARK,0xffffffff);
        colors.put(GRADIENT_COLOR_LIGHT,0xffffffff);colors.put(GRADIENT_COLOR_DARK,0xffffffff);
        BOOLEANS=Collections.unmodifiableMap(flags);NUMBERS=Collections.unmodifiableMap(numbers);COLORS=Collections.unmodifiableMap(colors);
    }
    private static final String AUTO_BLUR="com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable";
    private static final String MASK_BLUR="com.oplusos.systemui.common.blurability.drawable.MaskBlurDrawable";
    private static final String PLATFORM_BLUR="com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable";
    private static final String WALLPAPER_BLUR="com.oplusos.systemui.common.blurability.wallpaper.BlendWallpaperBlurDrawable";
    private static final String MULTI_CONFIG="com.oplusos.systemui.common.blurability.BlurMixConfig$BlurMixMultiWithShader";
    private final Map<View,WeakReference<Object>> buttons=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,WeakReference<View>> backgrounds=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,WeakReference<View>> engines=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,WeakReference<Drawable>> engineSources=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,WeakReference<Drawable>> engineMaterials=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,Boolean> wallpaperEngines=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object,CachedShader> shaders=Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<String> traceStages=Collections.synchronizedSet(new HashSet<>());
    private final ThreadLocal<Set<Paint>> drawingPaints=new ThreadLocal<>();
    private final ThreadLocal<Set<Drawable>> drawingEngines=new ThreadLocal<>();
    private volatile boolean enabled;
    private volatile QsTileAppearance.Style light=new QsTileAppearance.Style(-1,-1,100f,90f,false),dark=light;
    private boolean loggedError;

    public void configure(Bundle settings) {
        light=readStyle(settings,false);dark=readStyle(settings,true);
        enabled=settings.getBoolean(MASTER,false);loggedError=false;traceStages.clear();
        synchronized(backgrounds) { for(Drawable background:new ArrayList<>(backgrounds.keySet()))background.invalidateSelf(); }
        List<Drawable> refresh;
        synchronized(engines) { refresh=new ArrayList<>(engines.keySet()); }
        for(Drawable engine:refresh)dirty(engine);
        synchronized(buttons) { for(View button:new ArrayList<>(buttons.keySet()))button.invalidate(); }
    }
    /** Called after native binding/material changes; clearAll is separate from AOSP FooterView. */
    public void bindController(Object controller) {
        if(!QsTileAppearance.type(controller,CONTROLLER_CLASS))return;
        Object manager=QsTileAppearance.field(controller,"viewBlurManager");
        for(String field:new String[]{"clearAll","showingView"}) {
            Object button=QsTileAppearance.field(controller,field);
            if(button instanceof View)bind((View)button,manager);
        }
    }
    public void bind(View button) { bind(button,null); }
    private void bind(View button,Object manager) {
        if(!QsTileAppearance.type(button,BUTTON_CLASS))return;
        synchronized(buttons) {
            if(manager!=null||!buttons.containsKey(button))buttons.put(button,new WeakReference<>(manager));
        }
        Drawable background=button.getBackground();
        boolean changed=false;
        synchronized(backgrounds) {
            for(Map.Entry<Drawable,WeakReference<View>> item:backgrounds.entrySet())
                if(item.getValue().get()==button&&item.getKey()!=background){changed=true;break;}
            backgrounds.entrySet().removeIf(item->item.getValue().get()==button&&item.getKey()!=background);
            if(background!=null)backgrounds.put(background,new WeakReference<>(button));
        }
        if(changed)retireEngines(button,null);
        if(enabled)trace("background:"+(background==null?"absent":background.getClass().getName()),
                "clear background "+(background==null?"absent":background.getClass().getName()));
    }
    public void forget(View button) {
        buttons.remove(button);
        synchronized(backgrounds) { backgrounds.entrySet().removeIf(item->item.getValue().get()==null||item.getValue().get()==button); }
        retireEngines(button,null);
    }
    /** Optional exact-button View.draw hook for native low-blur/static drawable fallback. */
    public void drawButton(View button,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(!QsTileAppearance.type(button,BUTTON_CLASS)){nativeDraw.draw(canvas);return;}
        bind(button);
        if(!enabled){nativeDraw.draw(canvas);return;}
        List<Paint> paints=new ArrayList<>();List<Drawable> tints=new ArrayList<>();
        collectStaticFills(button.getBackground(),paints,tints,0,new IdentityHashMap<>());
        withPaints(paints,tints,style(button),button.getBackground()==null?null:button.getBackground().getBounds(),canvas,nativeDraw);
    }
    /** Both native wrappers have their own draw method; MaskBlurDrawable does not extend AutoBlur. */
    public void drawBlur(Drawable background,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        View button=owner(backgrounds,background);
        boolean mask=QsTileAppearance.type(background,MASK_BLUR);
        if(!enabled||button==null||(!mask&&!QsTileAppearance.type(background,AUTO_BLUR))||button.getBackground()!=background){nativeDraw.draw(canvas);return;}
        Object proxy=QsTileAppearance.call(background,"getViewBlurProxy");
        if(proxy==null)proxy=QsTileAppearance.field(background,"viewBlurProxy");
        Object fallback=QsTileAppearance.field(background,"defaultDrawable");
        // Match MaskBlurDrawable.getDrawable(): its fallback is drawn only when the proxy returns null.
        Object actual=QsTileAppearance.call(proxy,"getBlurDrawable",mask?null:fallback);
        if(!(actual instanceof Drawable))actual=fallback;
        boolean wallpaper=QsTileAppearance.type(actual,WALLPAPER_BLUR);
        trace("resolved:"+(actual==null?"absent":actual.getClass().getName()),
                "clear material "+(actual==null?"absent":actual.getClass().getName()));
        if(QsTileAppearance.type(actual,PLATFORM_BLUR)||wallpaper) {
            Object engine=QsTileAppearance.field(actual,wallpaper?"blendDrawable":"blurDrawable");
            if(engine instanceof Drawable) {
                Drawable target=(Drawable)engine;WeakReference<View> old=engines.get(target);
                WeakReference<Drawable> oldSource=engineSources.get(target);
                WeakReference<Drawable> oldMaterial=engineMaterials.get(target);
                boolean changed=old==null||old.get()!=button||oldSource==null||oldSource.get()!=background
                        ||oldMaterial==null||oldMaterial.get()!=actual
                        ||wallpaper!=Boolean.TRUE.equals(wallpaperEngines.get(target));
                if(changed) {
                    retireEngines(button,target);
                    engines.put(target,new WeakReference<>(button));engineSources.put(target,new WeakReference<>(background));
                    engineMaterials.put(target,new WeakReference<>((Drawable)actual));
                    wallpaperEngines.put(target,wallpaper);dirty(target);
                }
            }
            nativeDraw.draw(canvas);
        } else if(actual instanceof Drawable) {
            List<Paint> paints=new ArrayList<>();List<Drawable> tints=new ArrayList<>();collectStaticFills((Drawable)actual,paints,tints,0,new IdentityHashMap<>());
            withPaints(paints,tints,style(button),background.getBounds(),canvas,nativeDraw);
        } else nativeDraw.draw(canvas);
    }
    /** Chain at BlendDrawable.onDrawContent, preserving native geometry, texture and all effects. */
    public void drawGlassContent(Drawable engine,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        View button=owner(engines,engine);
        WeakReference<Drawable> sourceRef=engineSources.get(engine);Drawable source=sourceRef==null?null:sourceRef.get();
        if(!enabled||button==null||source==null||button.getBackground()!=source||owner(backgrounds,source)!=button||Build.VERSION.SDK_INT<33){nativeDraw.draw(canvas);return;}
        Set<Drawable> active=drawingEngines.get();
        if(active==null){active=Collections.newSetFromMap(new IdentityHashMap<>());drawingEngines.set(active);}
        if(!active.add(engine)){nativeDraw.draw(canvas);return;}
        Object lock=QsTileAppearance.field(engine,"dataLock");
        try {
            if(lock==null){nativeDraw.draw(canvas);return;}
            synchronized(lock) {
                ShaderSwap swap=null;
                try { swap=prepareGlass(engine,style(button),Boolean.TRUE.equals(wallpaperEngines.get(engine))); }
                catch(ReflectiveOperationException|RuntimeException unavailable){error(unavailable);}
                trace("prepared:"+(swap!=null),"clear native base shader prepared "+(swap!=null));
                try { nativeDraw.draw(canvas); } finally { if(swap!=null)swap.restore(); }
            }
        } finally { active.remove(engine);if(active.isEmpty())drawingEngines.remove(); }
    }
    private ShaderSwap prepareGlass(Drawable engine,QsTileAppearance.Style style,boolean wallpaper) throws ReflectiveOperationException {
        Object owner=QsTileAppearance.field(engine,"drawableShader"),original=QsTileAppearance.field(owner,"shader");
        if(!(original instanceof RuntimeShader)||!Boolean.TRUE.equals(QsTileAppearance.field(engine,"enableShader")))return null;
        Object multi=QsTileAppearance.field(owner,"multiBlendParam"),summary=QsTileAppearance.field(owner,"summaryBlendParam");
        if(!(summary instanceof List)||(!wallpaper&&!(multi instanceof List)))return null;
        List<?> base=multi instanceof List?(List<?>)multi:Collections.emptyList(),all=(List<?>)summary;
        WeakReference<Drawable> materialRef=engineMaterials.get(engine);Drawable nativeMaterial=materialRef==null?null:materialRef.get();
        // Read the selected wrapper's plain config; never re-enter proxy.ensure while recording.
        Object config=QsTileAppearance.field(nativeMaterial,"currentBlurMixConfig");
        int[] slots=wallpaper?wallpaperSlots(owner,all):clearSlots(base,all,config);
        traceMaterial(config,base,all,slots);
        if(slots==null)trace("slots:"+wallpaper+":"+base.size()+":"+all.size(),
                "clear unsupported native base slots wallpaper "+wallpaper+" multi "+base.size()+" summary "+all.size());
        if(slots==null)return null;
        Object corner=QsTileAppearance.call(QsTileAppearance.field(owner,"mCornerParams"),"getType");
        Object meta=QsTileAppearance.field(owner,"metaBallParams");
        if(corner==null||Boolean.TRUE.equals(QsTileAppearance.field(meta,"valid")))return null;
        Rect bounds=engine.getBounds();if(!QsTileAppearance.valid(bounds))return null;
        Object effects=QsTileAppearance.invoke(owner,"getAllEffects");if(!(effects instanceof List))return null;
        Class<?> builder=Class.forName("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt",false,owner.getClass().getClassLoader());
        StringBuilder source=new StringBuilder();
        QsTileAppearance.invoke(builder,"buildShaderString",source,QsTileAppearance.field(owner,"blendAlgorithmMask"),all.size(),corner,false,effects);
        // Reuse the proven single-argument substitution, not a second material implementation.
        String material=QsNativeGlassFill.decorate(source.toString());if(material==null)return null;
        CachedShader cached=shaders.get(owner);
        if(cached==null||!cached.source.equals(material)){cached=new CachedShader(material,new RuntimeShader(material));shaders.put(owner,cached);}
        RuntimeShader replacement=cached.shader;
        QsTileAppearance.invoke(owner,"setBaseUniform",replacement);
        replacement.setIntUniform("uEnableBlend",Boolean.TRUE.equals(QsTileAppearance.field(owner,"enableBlend"))?1:0);
        replacement.setFloatUniform("u_multiBlendParams",QsNativeGlassFill.uniforms(all));
        for(Object effect:(List<?>)effects)if(Boolean.TRUE.equals(QsTileAppearance.call(effect,"isEnabled")))QsTileAppearance.invoke(effect,"pushUniforms",replacement);
        replacement.setInputShader("c17QsFill",QsTileAppearance.shader(style,new Rect(0,0,bounds.width(),bounds.height())));
        replacement.setFloatUniform("c17QsSlots",slots[0]*5f,slots[1]*5f);
        Object nativePaint=QsTileAppearance.field(engine,"drawableShaderPaint");
        ShaderSwap swap=new ShaderSwap(owner,(RuntimeShader)original,replacement,nativePaint instanceof Paint?(Paint)nativePaint:null);
        return QsTileAppearance.setField(owner,"shader",replacement)?swap:null;
    }
    /** CLOSE_ALL has two items; actual CLEAR_ALL has haze first, then its own decorators. */
    private static int[] clearSlots(List<?> base,List<?> summary,Object config) {
        int offset=0;
        if(base.size()==4) {
            if(!QsTileAppearance.type(config,MULTI_CONFIG)||QsTileAppearance.field(config,"mixMultiShaderParams")!=base)return null;
            Object foreground=QsTileAppearance.field(config,"foregroundShaderParam"),background=QsTileAppearance.field(config,"backgroundShaderParam");
            if(!modes(foreground,5,3)||!modes(background,5,2)
                    ||!mode(base.get(0),5)||!mode(base.get(1),2)
                    ||!sameParam(base.get(2),5,QsTileAppearance.field(foreground,"topLayerColor"))
                    ||!sameParam(base.get(3),3,QsTileAppearance.field(foreground,"bottomLayerColor")))return null;
            // Native helper updates background alpha with blur amount; foreground decorators
            // retain their config colors. Do not recolor that shared background haze.
            offset=2;
        } else if(base.size()!=2)return null;
        Object top=base.get(offset),bottom=base.get(offset+1),a=QsTileAppearance.field(top,"mode"),b=QsTileAppearance.field(bottom,"mode");
        if(!(a instanceof Number)||!(b instanceof Number)||((Number)a).intValue()!=5||((Number)b).intValue()!=3)return null;
        if(!(QsTileAppearance.field(top,"color") instanceof Number)||!(QsTileAppearance.field(bottom,"color") instanceof Number))return null;
        int i=-1,j=-1;for(int n=0;n<summary.size();n++){if(summary.get(n)==top)i=n;if(summary.get(n)==bottom)j=n;}
        return i>=0&&j>=0&&i!=j?new int[]{i,j}:null;
    }
    private static boolean modes(Object config,int top,int bottom) {
        Object a=QsTileAppearance.field(config,"topMode"),b=QsTileAppearance.field(config,"bottomMode");
        return a instanceof Number&&b instanceof Number&&((Number)a).intValue()==top&&((Number)b).intValue()==bottom;
    }
    private static boolean mode(Object param,int expected) {
        Object value=QsTileAppearance.field(param,"mode");return value instanceof Number&&((Number)value).intValue()==expected;
    }
    /** BP material emits bg first, then foreground/multi. Admit only the confirmed clear bg modes. */
    private static int[] wallpaperSlots(Object owner,List<?> summary) {
        if(summary.size()<2)return null;
        Object background=QsTileAppearance.field(owner,"bgBlendParam"),mode=QsTileAppearance.field(background,"blendMode");
        if(!(mode instanceof Number))return null;
        int value=((Number)mode).intValue();
        if(value!=2&&value!=4)return null;
        Object first=summary.get(0),second=summary.get(1);
        if(!sameParam(first,value==2?5:1,QsTileAppearance.field(background,"blendColorA"))
                ||!sameParam(second,value==2?3:2,QsTileAppearance.field(background,"blendColorB")))return null;
        return new int[]{0,1};
    }
    private static boolean sameParam(Object param,int expectedMode,Object expectedColor) {
        Object mode=QsTileAppearance.field(param,"mode"),color=QsTileAppearance.field(param,"color");
        return mode instanceof Number&&color instanceof Number&&expectedColor instanceof Number
                &&((Number)mode).intValue()==expectedMode&&((Number)color).intValue()==((Number)expectedColor).intValue();
    }
    private static final class CachedShader {
        final String source;final RuntimeShader shader;
        CachedShader(String source,RuntimeShader shader){this.source=source;this.shader=shader;}
    }
    private static final class ShaderSwap {
        final Object owner;final RuntimeShader original,replacement;final Paint paint;final Shader paintShader;
        ShaderSwap(Object owner,RuntimeShader original,RuntimeShader replacement,Paint paint){this.owner=owner;this.original=original;this.replacement=replacement;this.paint=paint;paintShader=paint==null?null:paint.getShader();}
        void restore(){if(QsTileAppearance.field(owner,"shader")==replacement)QsTileAppearance.setField(owner,"shader",original);if(paint!=null)paint.setShader(paintShader);}
    }
    private void withPaints(List<Paint> paints,List<Drawable> tints,QsTileAppearance.Style style,Rect bounds,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(paints.isEmpty()||!QsTileAppearance.valid(bounds)){nativeDraw.draw(canvas);return;}
        Set<Paint> active=drawingPaints.get();if(active==null){active=Collections.newSetFromMap(new IdentityHashMap<>());drawingPaints.set(active);}
        List<Paint> changed=new ArrayList<>();List<Shader> shaders=new ArrayList<>();
        List<ColorFilter> filters=new ArrayList<>();
        List<Drawable> changedTints=new ArrayList<>();List<Object> originalTints=new ArrayList<>();
        try {
            // The native B/static resource is a one-item solid layer-list; its material tint
            // would hide an input gradient. Temporarily remove only this fill's tint filter.
            for(Drawable drawable:tints) {
                Object tint=QsTileAppearance.field(drawable,"mTintFilter");
                if(tint!=null&&QsTileAppearance.setField(drawable,"mTintFilter",null)){changedTints.add(drawable);originalTints.add(tint);}
            }
            for(Paint paint:paints)if(!active.contains(paint)&&paint.getShader()==null) {
                Shader shader=QsTileAppearance.shader(style,bounds);changed.add(paint);shaders.add(paint.getShader());filters.add(paint.getColorFilter());active.add(paint);paint.setShader(shader);
            }
        } catch(RuntimeException unavailable){error(unavailable);}
        try { nativeDraw.draw(canvas); }
        finally {
            for(int i=changed.size()-1;i>=0;i--){changed.get(i).setShader(shaders.get(i));changed.get(i).setColorFilter(filters.get(i));active.remove(changed.get(i));}
            for(int i=changedTints.size()-1;i>=0;i--)QsTileAppearance.setField(changedTints.get(i),"mTintFilter",originalTints.get(i));
            if(active.isEmpty())drawingPaints.remove();
        }
    }
    /** Only an owned background's solid fill; never touch image, spotlight, stroke or shadows. */
    private static void collectStaticFills(Drawable drawable,List<Paint> result,List<Drawable> tints,int depth,Map<Object,Boolean> seen) {
        if(drawable==null||depth>8||seen.put(drawable,true)!=null)return;
        Object fill=null;
        if(QsTileAppearance.type(drawable,"android.graphics.drawable.GradientDrawable")) {
            Object state=QsTileAppearance.field(drawable,"mGradientState");
            if(QsTileAppearance.field(state,"mColors")==null&&QsTileAppearance.field(state,"mGradientColors")==null) {
                QsTileAppearance.call(drawable,"ensureValidRect");fill=QsTileAppearance.field(drawable,"mFillPaint");
                if(fill instanceof Paint)tints.add(drawable);
            }
        } else if(QsTileAppearance.type(drawable,"com.oplusos.systemui.common.view.SmoothCornersGradientDrawable"))fill=QsTileAppearance.field(drawable,"paint");
        else if(QsTileAppearance.type(drawable,"com.oplusos.systemui.common.blurability.staticblur.StaticBlurDrawable"))fill=QsTileAppearance.field(drawable,"maskPaint");
        else if(QsTileAppearance.type(drawable,"android.graphics.drawable.ColorDrawable"))fill=QsTileAppearance.field(drawable,"mPaint");
        else if(QsTileAppearance.type(drawable,"android.graphics.drawable.ShapeDrawable"))fill=QsTileAppearance.call(drawable,"getPaint");
        if(fill instanceof Paint&&!result.contains(fill))result.add((Paint)fill);
        // A blur wrapper's default is a fallback, not the active native glass fill.
        if(QsTileAppearance.type(drawable,AUTO_BLUR)||QsTileAppearance.type(drawable,MASK_BLUR))return;
        if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)collectStaticFills(layers.getDrawable(i),result,tints,depth+1,seen);
        }
        for(Object child:new Object[]{QsTileAppearance.call(drawable,"getDrawable"),QsTileAppearance.call(drawable,"getCurrent")})
            if(child instanceof Drawable&&child!=drawable)collectStaticFills((Drawable)child,result,tints,depth+1,seen);
    }
    private QsTileAppearance.Style style(View button) {
        WeakReference<Object> ref=buttons.get(button);Object manager=ref==null?null:ref.get();
        Object night=QsTileAppearance.field(manager,"isNightMode");
        boolean dark=night instanceof Boolean?(Boolean)night:(button.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        return dark?this.dark:light;
    }
    private static QsTileAppearance.Style readStyle(Bundle settings,boolean dark) {
        String colorKey=dark?COLOR_DARK:COLOR_LIGHT,secondKey=dark?GRADIENT_COLOR_DARK:GRADIENT_COLOR_LIGHT;
        return new QsTileAppearance.Style(effectiveColor(settings,colorKey),effectiveColor(settings,secondKey),
                Math.max(0f,Math.min(100f,NumericPolicy.finite(settings.get(OPACITY),100f))),
                NumericPolicy.finite(settings.get(GRADIENT_ANGLE),90f)%360f,settings.getBoolean(GRADIENT_ENABLED,false));
    }
    private static int effectiveColor(Bundle values,String key) {
        Object value=values.get(key);int color=value instanceof Number?((Number)value).intValue():0xffffffff;
        boolean own=values.containsKey(StatusBarSettings.alphaKey(key))?values.getBoolean(StatusBarSettings.alphaKey(key),false):(color>>>24)!=255;
        return own?color:(color&0xffffff)|0xff000000;
    }
    /** Schematic preview only; native glass effects and clear glyph still come from SystemUI. */
    public static Shader previewShader(Map<String,?> values,boolean dark,Rect bounds) {
        String a=dark?COLOR_DARK:COLOR_LIGHT,b=dark?GRADIENT_COLOR_DARK:GRADIENT_COLOR_LIGHT;
        int first=StatusBarSettings.color(values,a),second=StatusBarSettings.color(values,b);
        if(!StatusBarSettings.customAlpha(values,a))first=(first&0xffffff)|0xff000000;
        if(!StatusBarSettings.customAlpha(values,b))second=(second&0xffffff)|0xff000000;
        return QsTileAppearance.shader(new QsTileAppearance.Style(first,second,Math.max(0f,Math.min(100f,StatusBarSettings.number(values,OPACITY,100f)))*.36f,
                StatusBarSettings.number(values,GRADIENT_ANGLE,90f)%360f,StatusBarSettings.bool(values,GRADIENT_ENABLED)),bounds);
    }
    private static View owner(Map<Drawable,WeakReference<View>> owners,Drawable drawable) {
        WeakReference<View> ref=owners.get(drawable);View view=ref==null?null:ref.get();return QsTileAppearance.type(view,BUTTON_CLASS)?view:null;
    }
    private static void dirty(Drawable engine) {
        Object lock=QsTileAppearance.field(engine,"dataLock");if(lock!=null)synchronized(lock){QsTileAppearance.setField(engine,"contentDirty",true);}engine.invalidateSelf();
    }
    /** Re-record obsolete private engines without leaving their previous custom RenderNode fill cached. */
    private void retireEngines(View button,Drawable keep) {
        List<Drawable> retired=new ArrayList<>();
        synchronized(engines) {
            engines.entrySet().removeIf(item->{
                View owner=item.getValue().get();Drawable engine=item.getKey();
                if(engine!=keep&&(owner==null||owner==button)) {
                    engineSources.remove(engine);engineMaterials.remove(engine);wallpaperEngines.remove(engine);retired.add(engine);return true;
                }
                return false;
            });
        }
        // Native recording can already hold dataLock before entering our ownership lookup.
        // Do not take that lock while holding the weak ownership map monitor.
        for(Drawable engine:retired)dirty(engine);
    }
    private void trace(String stage,String detail) {
        synchronized(traceStages) {if(traceStages.size()>=16||!traceStages.add(stage))return;}
        ModuleDiagnostics.info("notification_clear",detail);
    }
    /** One bounded snapshot per material shape; no object identifiers or user settings are logged. */
    private void traceMaterial(Object config,List<?> base,List<?> summary,int[] slots) {
        String name=config==null?"absent":config.getClass().getSimpleName();
        String stage="schema:"+name+":"+base.size()+":"+summary.size()+":"+(slots==null?"native":slots[0]+":"+slots[1]);
        synchronized(traceStages) {if(traceStages.size()>=16||!traceStages.add(stage))return;}
        StringBuilder detail=new StringBuilder("clear native schema ").append(name)
                .append(" config list same ").append(QsTileAppearance.field(config,"mixMultiShaderParams")==base)
                .append(" multi ").append(base.size()).append(" summary ").append(summary.size())
                .append(" selected ").append(slots==null?"native":slots[0]+"/"+slots[1]);
        Object foreground=QsTileAppearance.field(config,"foregroundShaderParam"),background=QsTileAppearance.field(config,"backgroundShaderParam");
        detail.append(" config foreground modes ").append(QsTileAppearance.field(foreground,"topMode"))
                .append('/').append(QsTileAppearance.field(foreground,"bottomMode"))
                .append(" background modes ").append(QsTileAppearance.field(background,"topMode"))
                .append('/').append(QsTileAppearance.field(background,"bottomMode"));
        if(base.size()==4)detail.append(" foreground colors same ")
                .append(sameParam(base.get(2),5,QsTileAppearance.field(foreground,"topLayerColor"))
                        &&sameParam(base.get(3),3,QsTileAppearance.field(foreground,"bottomLayerColor")));
        for(int n=0;n<Math.min(base.size(),4);n++) {
            Object param=base.get(n),mode=QsTileAppearance.field(param,"mode"),color=QsTileAppearance.field(param,"color");
            int index=-1;for(int s=0;s<Math.min(summary.size(),16);s++)if(summary.get(s)==param){index=s;break;}
            detail.append(" item ").append(n).append(" mode ").append(mode instanceof Number?((Number)mode).intValue():-1)
                    .append(" summary item ").append(index);
            if(color instanceof Number) {
                int argb=((Number)color).intValue();
                detail.append(" native RGBA ").append((argb>>>16)&255).append('/').append((argb>>>8)&255)
                        .append('/').append(argb&255).append('/').append(argb>>>24);
            }
        }
        ModuleDiagnostics.info("notification_clear",detail.toString());
    }
    private void error(Throwable error) {
        if(!loggedError){loggedError=true;ModuleDiagnostics.error("notification_clear","Clear button fill unavailable; original material retained",error);}
    }
}
