// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Changes confirmed QS base fills while native shapes, alpha, icons and glass effects draw normally. */
public final class QsTileAppearance {
    public interface DrawAction {void draw(Canvas canvas) throws Throwable;}
    public static final String MASTER="qs_appearance_enabled";
    public static final Map<String,Boolean> BOOLEANS;
    public static final Map<String,Float> NUMBERS;
    public static final Map<String,Integer> COLORS;
    static {
        Map<String,Boolean> flags=new LinkedHashMap<>();Map<String,Float> numbers=new LinkedHashMap<>();Map<String,Integer> colors=new LinkedHashMap<>();
        flags.put(MASTER,false);
        for(String scene:new String[]{"light","dark"}) {
            String key=prefix(scene);flags.put(key+"_gradient_enabled",false);
            numbers.put(key+"_opacity",100f);numbers.put(key+"_gradient_angle",0f);
            colors.put(key+"_color",0xffffffff);colors.put(key+"_gradient_color",0xffffffff);
        }
        BOOLEANS=Collections.unmodifiableMap(flags);NUMBERS=Collections.unmodifiableMap(numbers);COLORS=Collections.unmodifiableMap(colors);
    }
    private static final String TILE_BASE="com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView";
    private static final String SLIDER_BASE="com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar";
    private static final String MIX_TILE="com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable";
    private static final String GRADIENT_TILE="com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable";
    private static final String AUTO_BLUR="com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable";
    private static final String PLATFORM_BLUR="com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable";
    private static final String DEVICE_PACKAGE="com.oplus.deviceplugin.sdk.ui.view.separatecardview.";
    private static final String[][] DEVICE_BODIES={
        {"RectangleDeviceCardView","rectangleCoLayout"},
        {"SquareDeviceCardView","square_device_constraintlayout"},
        {"NoDeviceEntranceCardView","no_device_constraintLayout"},
        {"RectangleEntranceCardView","rectangle_entrance_constraintLayout"},
        {"SquareEntranceCardView","square_entrance_constraintLayout"}
    };
    private final Map<View,Integer> tiles=new WeakHashMap<>();
    private final Map<Drawable,WeakReference<View>> backgrounds=new WeakHashMap<>();
    private final Map<Drawable,WeakReference<Drawable>> fillSources=new WeakHashMap<>();
    private final Map<View,Boolean> sliders=new WeakHashMap<>();
    private final Map<View,Boolean> deviceCards=new WeakHashMap<>();
    private final Map<View,DeviceSource> deviceSources=new WeakHashMap<>();
    private final Map<Drawable,WeakReference<View>> glassTracks=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable,WeakReference<Drawable>> glassSources=Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Set<Paint>> activePaints=new ThreadLocal<>();
    private final ThreadLocal<DrawBuffers> drawBuffers=new ThreadLocal<>();
    private final ThreadLocal<View> activeSlider=new ThreadLocal<>();
    private final Set<String> traceStages=Collections.synchronizedSet(new HashSet<>());
    private volatile boolean enabled;
    private boolean loggedError;
    private volatile Style light=new Style(-1,-1,100f,0f,false),dark=light;
    private final QsNativeGlassFill glass=new QsNativeGlassFill();
    static final class Style {
        final int color,color2;final float opacity,angle;final boolean gradient;
        // A style is immutable. Native paint alpha and glass uniforms are deliberately
        // outside this cache, so press/color/optical animations remain live.
        private final GradientEntry[] shaders=new GradientEntry[16];
        private GradientEntry recent;
        private int nextShader;
        Style(int color,int color2,float opacity,float angle,boolean gradient){this.color=color;this.color2=color2;this.opacity=opacity;this.angle=angle;this.gradient=gradient;}
    }
    private static final class GradientEntry {
        final int left,top,right,bottom;final Shader shader;
        GradientEntry(Rect bounds,Shader shader){left=bounds.left;top=bounds.top;right=bounds.right;bottom=bounds.bottom;this.shader=shader;}
        boolean matches(Rect bounds){return left==bounds.left&&top==bounds.top&&right==bounds.right&&bottom==bounds.bottom;}
    }
    /** Reentrant native draw calls need separate scratch lists, but steady frames reuse them. */
    private static final class DrawBuffers {
        final List<Paint> paints=new ArrayList<>(),changed=new ArrayList<>();
        final Map<Object,Boolean> seen=new IdentityHashMap<>();
        DrawBuffers next;boolean busy;
        void clear(){paints.clear();changed.clear();seen.clear();busy=false;}
    }
    private static final class DeviceSource {
        final WeakReference<View> body;final WeakReference<Drawable> background;
        DeviceSource(View body,Drawable background){this.body=new WeakReference<>(body);this.background=new WeakReference<>(background);}
        boolean matches(View body,Drawable background){return this.body.get()==body&&this.background.get()==background;}
    }
    private DrawBuffers acquireBuffers() {
        DrawBuffers buffers=drawBuffers.get();
        if(buffers==null){buffers=new DrawBuffers();drawBuffers.set(buffers);}
        while(buffers.busy){if(buffers.next==null)buffers.next=new DrawBuffers();buffers=buffers.next;}
        buffers.busy=true;return buffers;
    }
    public static String prefix(String scene){return "qs_global_"+scene;}
    public void configure(Bundle settings) {
        light=readStyle(settings,"light");dark=readStyle(settings,"dark");enabled=settings.getBoolean(MASTER,false);loggedError=false;traceStages.clear();
        for(Drawable drawable:new ArrayList<>(backgrounds.keySet()))drawable.invalidateSelf();
        for(View view:new ArrayList<>(tiles.keySet()))view.invalidate();
        for(View view:new ArrayList<>(sliders.keySet()))view.invalidate();
        for(View view:new ArrayList<>(deviceCards.keySet()))view.invalidate();
        synchronized(glassTracks) {
            for(Drawable engine:new ArrayList<>(glassTracks.keySet()))dirtyGlass(engine);
        }
    }
    private static Style readStyle(Bundle settings,String scene) {
        String key=prefix(scene);
        return new Style(color(settings.get(key+"_color"),-1),color(settings.get(key+"_gradient_color"),-1),
                clamp(number(settings.get(key+"_opacity"),100f),0f,100f),angle(number(settings.get(key+"_gradient_angle"),0f)),settings.getBoolean(key+"_gradient_enabled",false));
    }
    /** Run after native tile state/theme/drawable updates. Big glass cards are deliberately excluded. */
    public void refreshTile(View view) {
        if(!isTile(view))return;
        onTileState(view,call(view,"getTileState"));
        if(!largeTile(view)) {
            register(view,call(view,"getBgDrawable"),0,new IdentityHashMap<>());
            register(view,call(view,"getThemeDrawable"),0,new IdentityHashMap<>());
            registerView(view,call(view,"getBg"));
        }
        // In 2x1/2x2 cards only the original active circle behind the icon is styled.
        Object icon=call(view,"getIconView");if(icon==null)icon=call(view,"getIcon");
        if(icon==null)icon=field(view,"iconView");
        if(icon!=null) {
            register(view,call(icon,"getIconBgDrawable"),0,new IdentityHashMap<>());
            register(view,call(icon,"getBgDrawable"),0,new IdentityHashMap<>());
            register(view,call(icon,"getThemeDrawable"),0,new IdentityHashMap<>());
            registerView(view,field(icon,"iconBgView"));
        }
        try {
            int id=view.getResources().getIdentifier("oplus_qs_tile_icon_bg","id","com.android.systemui");
            if(id!=0)registerView(view,view.findViewById(id));
        }catch(RuntimeException absent){ }
        if(tracing())trace("register:"+view.getClass().getSimpleName(),"tile="+view.getClass().getSimpleName()+", state="+tiles.get(view)+", large="+largeTile(view)+", icon="+(icon==null?"absent":icon.getClass().getSimpleName()));
    }
    private void registerView(View owner,Object object) {
        if(!(object instanceof View))return;
        register(owner,call(object,"getDrawable"),0,new IdentityHashMap<>());
        register(owner,call(object,"getBackground"),0,new IdentityHashMap<>());
        ((View)object).invalidate();
    }
    public static boolean isDeviceCard(View view){return deviceBodyName(view)!=null;}
    private static String deviceBodyName(View view) {
        for(String[] entry:DEVICE_BODIES)if(type(view,DEVICE_PACKAGE+entry[0]))return entry[1];
        return null;
    }
    private static View deviceBody(View view) {
        String name=deviceBodyName(view);if(name==null)return null;
        try {
            int id=view.getResources().getIdentifier(name,"id","com.android.systemui");
            View body=id==0?null:view.findViewById(id);
            return body!=null&&name.equals(resourceName(body))?body:null;
        }catch(RuntimeException absent){return null;}
    }
    /** Only the five native devices-row cards' inner base; spotlight foregrounds are not registered. */
    public void refreshDeviceCard(View view) {
        View body=deviceBody(view);if(body==null)return;
        Object background=call(body,"getBackground");
        registerDeviceSource(view,body,background instanceof Drawable?(Drawable)background:null);
    }
    private void registerDeviceSource(View view,View body,Drawable background) {
        deviceCards.put(view,Boolean.TRUE);
        deviceSources.put(view,new DeviceSource(body,background));
        register(view,background,0,new IdentityHashMap<>());
        if(tracing())trace("device:"+view.getClass().getName(),"device card="+view.getClass().getSimpleName()+", base="+resourceName(body));
    }
    /** Wrap View.draw, before its inner background draws, retaining native clipping and all children. */
    public void drawDeviceCard(View view,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(!enabled){nativeDraw.draw(canvas);return;}
        View body=deviceBody(view);if(body==null){nativeDraw.draw(canvas);return;}
        Object background=call(body,"getBackground");
        if(!(background instanceof Drawable)){nativeDraw.draw(canvas);return;}
        Drawable drawable=(Drawable)background;DeviceSource source=deviceSources.get(view);
        // g()/theme/config events still refresh nested drawable state. An unchanged
        // draw must not re-register its entire native graph or recreate weak references.
        if(source==null||!source.matches(body,drawable))registerDeviceSource(view,body,drawable);
        DrawBuffers buffers=acquireBuffers();
        try {
            collectFills(drawable,buffers.paints,0,buffers.seen,true);
            Rect bounds=drawable.getBounds();
            // View initializes a newly replaced background's bounds during its first draw.
            if(!valid(bounds))bounds=new Rect(0,0,body.getWidth(),body.getHeight());
            withFills(buffers,style(view),bounds,canvas,nativeDraw);
        }finally{buffers.clear();}
    }
    public void onTileState(View view,Object state) {
        if(isTile(view)){Object value=field(state,"state");tiles.put(view,value instanceof Number?((Number)value).intValue():-1);}
    }
    public void detach(View view) {
        tiles.remove(view);sliders.remove(view);deviceCards.remove(view);deviceSources.remove(view);
        backgrounds.entrySet().removeIf(item->item.getValue().get()==null||item.getValue().get()==view);
        fillSources.keySet().removeIf(drawable->!backgrounds.containsKey(drawable));
        synchronized(glassTracks) {
            glassTracks.entrySet().removeIf(item->{View owner=item.getValue().get();if(owner==null||owner==view){glassSources.remove(item.getKey());dirtyGlass(item.getKey());return true;}return false;});
        }
    }
    public void detachTile(View view){detach(view);}
    /** Hooks MixColorTileDrawable, GradientTileDrawable and StateListTileDrawable.draw(Canvas). */
    public void drawTile(Drawable drawable,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(!enabled){nativeDraw.draw(canvas);return;}
        View owner=owner(drawable);
        if(tracing())trace("draw:"+drawable.getClass().getName(),"drawable="+drawable.getClass().getName()+", owner="+(owner==null?"absent":owner.getClass().getSimpleName()));
        if(owner==null){nativeDraw.draw(canvas);return;}
        Object state=call(owner,"getTileState");if(state!=null)onTileState(owner,state);
        if(!Integer.valueOf(2).equals(tiles.get(owner))){nativeDraw.draw(canvas);return;}
        DrawBuffers buffers=acquireBuffers();
        try {
            collectFills(drawable,buffers.paints,0,buffers.seen);
            if(tracing())trace("fill:"+drawable.getClass().getName(),"drawable="+drawable.getClass().getName()+", state="+tiles.get(owner)+", eligiblePaints="+buffers.paints.size()+", maskWhite="+white(field(drawable,"maskColor"))+", colorWhite="+white(call(field(drawable,"colorDrawable"),"getColor")));
            withFills(buffers,style(owner),drawable.getBounds(),canvas,nativeDraw);
        }finally{buffers.clear();}
    }
    /** Native clipping, thumb paint, foreground labels and animation alpha stay untouched. */
    public void drawSlider(View view,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(!enabled||!isSlider(view)||!qsSlider(view)){nativeDraw.draw(canvas);return;}
        sliders.put(view,Boolean.TRUE);View previous=activeSlider.get();activeSlider.set(view);
        if(tracing())trace("slider:"+view.getClass().getName(),"active slider="+view.getClass().getName()+", progressWhite="+white(field(view,"mProgressColor")));
        DrawBuffers buffers=acquireBuffers();
        try {
            Object paint=field(view,"mProgressPaint"),nativeColor=field(view,"mProgressColor"),rect=field(view,"mClipProgressRect");
            if(paint instanceof Paint&&white(nativeColor))buffers.paints.add((Paint)paint);
            withFills(buffers,style(view),rect instanceof Rect?(Rect)rect:null,canvas,nativeDraw);
        }finally {buffers.clear();if(previous==null)activeSlider.remove();else activeSlider.set(previous);}
    }
    /** AutoBlur resolves the lazily created glass active track; background glass is never registered. */
    public void drawBlur(Drawable drawable,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        View slider=activeSlider.get();
        if(enabled&&tracing())trace("auto:"+drawable.getClass().getName(),"auto drawable="+drawable.getClass().getName()+", sliderScope="+(slider!=null)+", exactActive="+(slider!=null&&field(slider,"activeMixColorDrawable")==drawable));
        if(!enabled||!type(drawable,AUTO_BLUR)){nativeDraw.draw(canvas);return;}
        View target=slider!=null&&field(slider,"activeMixColorDrawable")==drawable?slider:owner(drawable);
        if(target==null||!activeFillOwner(target)){nativeDraw.draw(canvas);return;}
        if(tracing())trace("autoOwner:"+target.getClass().getName(),"glass active owner "+target.getClass().getSimpleName());
        Object proxy=call(drawable,"getViewBlurProxy");if(proxy==null)proxy=field(drawable,"viewBlurProxy");
        Object actual=call(proxy,"getBlurDrawable",field(drawable,"defaultDrawable"));
        if(tracing())trace("blurActual", "active glass resolved="+(actual==null?"absent":actual.getClass().getName()));
        if(type(actual,PLATFORM_BLUR)) {
            Object engine=field(actual,"blurDrawable");
            if(engine instanceof Drawable) {
                Drawable track=(Drawable)engine;WeakReference<View> old=glassTracks.get(track);
                if(old==null||old.get()!=target){glassTracks.put(track,new WeakReference<>(target));dirtyGlass(track);}
                WeakReference<Drawable> source=fillSources.get(drawable);
                if(source!=null){if(glassSources.get(track)!=source)glassSources.put(track,source);}
                else if(glassSources.containsKey(track))glassSources.remove(track);
            }
            nativeDraw.draw(canvas);
        }else if(actual instanceof Drawable) {
            DrawBuffers buffers=acquireBuffers();
            try {
                collectFills((Drawable)actual,buffers.paints,0,buffers.seen,isDeviceCard(target));
                withFills(buffers,style(target),drawable.getBounds(),canvas,nativeDraw);
            }finally{buffers.clear();}
        }else nativeDraw.draw(canvas);
    }
    /** Hook BlendDrawable.onDrawContent(Canvas), at the native RenderNode recording point. */
    public void drawGlassContent(Drawable engine,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        WeakReference<View> ref=glassTracks.get(engine);View view=ref==null?null:ref.get();
        if(enabled&&tracing())trace("record:"+engine.getClass().getName(),"glass recorder="+engine.getClass().getName()+", ownerKnown="+(view!=null));
        if(!enabled||view==null||!activeFillOwner(view)){nativeDraw.draw(canvas);return;}
        Object lock=field(engine,"dataLock");if(lock==null){nativeDraw.draw(canvas);return;}
        synchronized(lock) {
            QsNativeGlassFill.Swap swap=null;
            WeakReference<Drawable> source=glassSources.get(engine);
            boolean transition=isTile(view)&&source!=null&&animatedFill(source.get());
            try {swap=glass.prepare(engine,style(view),isDeviceCard(view),transition);}catch(ReflectiveOperationException|RuntimeException unavailable){error(unavailable);}
            if(tracing())trace("glassPrepared:"+view.getClass().getName(), "native glass base shader prepared="+(swap!=null)+", gate "+glass.lastReason());
            try {nativeDraw.draw(canvas);}finally {if(swap!=null)swap.restore();}
        }
    }
    private static void dirtyGlass(Drawable engine) {
        Object lock=field(engine,"dataLock");
        if(lock!=null)synchronized(lock){setField(engine,"contentDirty",true);}
        engine.invalidateSelf();
    }
    private boolean activeFillOwner(View view) {
        if(isSlider(view))return qsSlider(view);
        // Devices and entrances share the user's row style even when their native device state is inactive.
        if(isDeviceCard(view))return true;
        if(!isTile(view))return false;
        // Glass content recording may run off the UI thread; only read native state here.
        Object state=call(view,"getTileState"),value=field(state,"state");
        return value instanceof Number&&((Number)value).intValue()==2;
    }
    private void withFills(DrawBuffers buffers,Style style,Rect bounds,Canvas canvas,DrawAction nativeDraw) throws Throwable {
        if(buffers.paints.isEmpty()||!valid(bounds)){nativeDraw.draw(canvas);return;}
        Set<Paint> inUse=activePaints.get();if(inUse==null){inUse=Collections.newSetFromMap(new IdentityHashMap<>());activePaints.set(inUse);}
        try {
            Shader shader=null;
            for(int i=0;i<buffers.paints.size();i++){Paint paint=buffers.paints.get(i);if(!inUse.contains(paint)&&paint.getShader()==null) {
                if(shader==null)shader=shader(style,bounds);
                buffers.changed.add(paint);inUse.add(paint);paint.setShader(shader);
            }}
        }catch(RuntimeException unavailable){error(unavailable);}
        try {nativeDraw.draw(canvas);}
        finally {
            for(int i=buffers.changed.size()-1;i>=0;i--){Paint paint=buffers.changed.get(i);paint.setShader(null);inUse.remove(paint);}
        }
    }
    private static void collectFills(Drawable drawable,List<Paint> result,int depth,Map<Object,Boolean> seen) {
        collectFills(drawable,result,depth,seen,false);
    }
    private static void collectFills(Drawable drawable,List<Paint> result,int depth,Map<Object,Boolean> seen,boolean deviceBase) {
        if(drawable==null||depth>10||seen.put(drawable,true)!=null)return;
        Object value=null;
        if(type(drawable,MIX_TILE)) {if(deviceBase||eligibleTileColor(drawable,field(drawable,"maskColor")))value=field(drawable,"paint");}
        else if(type(drawable,GRADIENT_TILE)) {if(deviceBase||eligibleTileColor(drawable,call(field(drawable,"colorDrawable"),"getColor")))value=field(drawable,"paint");}
        else if(type(drawable,"com.oplusos.systemui.common.blurability.staticblur.StaticBlurDrawable")
                ||type(drawable,PLATFORM_BLUR)) {if(deviceBase||white(field(drawable,"blurMaskColor")))value=field(drawable,"maskPaint");}
        else if(type(drawable,"com.oplusos.systemui.common.view.SmoothCornersGradientDrawable")&&!Boolean.TRUE.equals(field(drawable,"isAnimation"))) {
            if(deviceBase||white(field(drawable,"colorValue")))value=field(drawable,"paint");
        }else if(type(drawable,"android.graphics.drawable.ColorDrawable")) {if(deviceBase||white(call(drawable,"getColor")))value=field(drawable,"mPaint");}
        else if(deviceBase&&type(drawable,"android.graphics.drawable.ShapeDrawable"))value=call(drawable,"getPaint");
        else if(type(drawable,"android.graphics.drawable.GradientDrawable")) {
            Object state=field(drawable,"mGradientState");
            // Preserve native gradients/texture; only the solid base fill Paint is eligible.
            if(field(state,"mColors")==null&&field(state,"mGradientColors")==null) {
                call(drawable,"ensureValidRect");Object fill=field(drawable,"mFillPaint");
                if(fill instanceof Paint&&(deviceBase||white(((Paint)fill).getColor())))value=fill;
            }
        }
        if(value instanceof Paint&&!result.contains(value))result.add((Paint)value);
        // Mix/Gradient private Paint is the only eligible fill of that primitive; do not recolor its glass child twice.
        if(type(drawable,MIX_TILE)||type(drawable,GRADIENT_TILE))return;
        collectChildFill(drawable,call(drawable,"getDrawable"),result,depth,seen,deviceBase);
        collectChildFill(drawable,call(drawable,"getCurrent"),result,depth,seen,deviceBase);
        collectChildFill(drawable,field(drawable,"backgroundDrawable"),result,depth,seen,deviceBase);
        if(!deviceBase)collectChildFill(drawable,field(drawable,"foregroundDrawable"),result,depth,seen,false);
    }
    private static void collectChildFill(Drawable parent,Object child,List<Paint> result,int depth,Map<Object,Boolean> seen,boolean deviceBase) {
        if(child instanceof Drawable&&child!=parent)collectFills((Drawable)child,result,depth+1,seen,deviceBase);
    }
    private Style style(View view) {
        boolean night=(view.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        return night?dark:light;
    }
    private static boolean eligibleTileColor(Drawable drawable,Object color) {
        if(white(color))return true;
        if(!(color instanceof Number)||!animatedFill(drawable))return false;
        int rgb=((Number)color).intValue()&0xffffff,r=(rgb>>16)&255;
        // Native ArgbEvaluator interpolates the confirmed neutral base during press/switch animation.
        return r==((rgb>>8)&255)&&r==(rgb&255);
    }
    private static boolean animatedFill(Drawable drawable) {
        if(!type(drawable,MIX_TILE)&&!type(drawable,GRADIENT_TILE))return false;
        return Boolean.TRUE.equals(call(drawable,"isDeforming"))||Boolean.TRUE.equals(call(field(drawable,"animator"),"isRunning"));
    }
    static Shader shader(Style style,Rect bounds) {
        synchronized(style) {
            GradientEntry recent=style.recent;
            if(recent!=null&&recent.matches(bounds))return recent.shader;
            for(int i=0;i<style.shaders.length;i++) {
                GradientEntry entry=style.shaders[i];
                if(entry!=null&&entry.matches(bounds)){style.recent=entry;return entry.shader;}
            }
            Shader shader=createShader(style,bounds);
            GradientEntry entry=new GradientEntry(bounds,shader);
            style.shaders[style.nextShader]=entry;style.nextShader=(style.nextShader+1)%style.shaders.length;style.recent=entry;
            return shader;
        }
    }
    private static Shader createShader(Style style,Rect bounds) {
        int a=withOpacity(style.color,style.opacity,255),b=withOpacity(style.gradient?style.color2:style.color,style.opacity,255);
        double radians=Math.toRadians(style.angle);float dx=(float)Math.cos(radians),dy=(float)Math.sin(radians);
        float cx=((float)bounds.left+bounds.right)/2f,cy=((float)bounds.top+bounds.bottom)/2f;
        float reach=(Math.abs(dx)*(bounds.right-bounds.left)+Math.abs(dy)*(bounds.bottom-bounds.top))/2f;
        return new LinearGradient(cx-dx*reach,cy-dy*reach,cx+dx*reach,cy+dy*reach,new int[]{a,b},new float[]{0f,1f},Shader.TileMode.CLAMP);
    }
    static int withOpacity(int color,float opacity,int nativeAlpha) {
        int alpha=Math.round((color>>>24)*clamp(opacity,0f,100f)/100f*clamp(nativeAlpha,0,255)/255f);return (color&0xffffff)|(alpha<<24);
    }
    static boolean white(Object value){return value instanceof Number&&(((Number)value).intValue()&0xffffff)==0xffffff;}
    static boolean valid(Rect bounds){return bounds!=null&&bounds.right>bounds.left&&bounds.bottom>bounds.top&&(long)bounds.right-bounds.left<=16384&&(long)bounds.bottom-bounds.top<=16384;}
    private static boolean qsSlider(View view) {
        View cursor=view;for(int i=0;i<20&&cursor!=null;i++) {
            String name=resourceName(cursor);
            if(name.equals("qs_volume_slider_layout")||name.equals("brightness_slider")||name.equals("brightness_seekbar_container"))return true;
            ViewParent parent=cursor.getParent();cursor=parent instanceof View?(View)parent:null;
        }return false;
    }
    private View owner(Drawable drawable) {
        WeakReference<View> known=backgrounds.get(drawable);View owner=known==null?null:known.get();if(owner!=null)return owner;
        Drawable.Callback callback=drawable.getCallback();
        for(int i=0;i<16&&callback!=null;i++) {
            if(callback instanceof Drawable) {
                Drawable parent=(Drawable)callback;known=backgrounds.get(parent);owner=known==null?null:known.get();if(owner!=null)return owner;callback=parent.getCallback();
            }else if(callback instanceof View) {
                View background=(View)callback;String name=resourceName(background);
                View device=background;
                for(int j=0;j<16&&device!=null;j++) {
                    if(isDeviceCard(device)&&name.equals(deviceBodyName(device))&&deviceBody(device)==background){refreshDeviceCard(device);return device;}
                    ViewParent parent=device.getParent();device=parent instanceof View?(View)parent:null;
                }
                if(!name.equals("qs_tile_bg")&&!name.equals("oplus_qs_tile_icon_bg"))return null;
                View cursor=background;for(int j=0;j<16&&cursor!=null;j++) {
                    if(isTile(cursor)){if(name.equals("qs_tile_bg")&&largeTile(cursor))return null;refreshTile(cursor);return cursor;}
                    ViewParent parent=cursor.getParent();cursor=parent instanceof View?(View)parent:null;
                }return null;
            }else return null;
        }return null;
    }
    private void register(View owner,Object candidate,int depth,Map<Object,Boolean> seen) {
        register(owner,candidate,depth,seen,null);
    }
    private void register(View owner,Object candidate,int depth,Map<Object,Boolean> seen,Drawable fillSource) {
        if(!(candidate instanceof Drawable)||depth>10||seen.put(candidate,true)!=null)return;
        Drawable drawable=(Drawable)candidate;backgrounds.put(drawable,new WeakReference<>(owner));
        if(type(drawable,MIX_TILE)||type(drawable,GRADIENT_TILE))fillSource=drawable;
        if(fillSource!=null)fillSources.put(drawable,new WeakReference<>(fillSource));
        for(Object child:new Object[]{call(drawable,"getDrawable"),call(drawable,"getCurrent"),field(drawable,"backgroundDrawable"),isDeviceCard(owner)?null:field(drawable,"foregroundDrawable")})
            if(child!=drawable)register(owner,child,depth+1,seen,fillSource);
    }
    private static boolean largeTile(View view) {
        for(Class<?> cls=view.getClass();cls!=null;cls=cls.getSuperclass())if(cls.getName().endsWith("TileViewTwoXOne")||cls.getName().endsWith("TileViewTwoXTwo"))return true;
        Object span=call(view,"getSpanSize");Object x=field(span,"spanX"),y=field(span,"spanY");
        return x instanceof Number&&((Number)x).intValue()>1||y instanceof Number&&((Number)y).intValue()>1;
    }
    private static String resourceName(View view) {
        try{return "com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId()))?view.getResources().getResourceEntryName(view.getId()):"";}catch(RuntimeException unknown){return "";}
    }
    private static boolean isTile(View view){return type(view,TILE_BASE);}
    private static boolean isSlider(View view){return type(view,SLIDER_BASE);}
    static boolean type(Object value,String name) {if(value==null)return false;for(Class<?> cls=value.getClass();cls!=null;cls=cls.getSuperclass())if(cls.getName().equals(name))return true;return false;}
    private static final Object[] NO_ARGUMENTS=new Object[0];
    static Object call(Object target,String name){return call(target,name,NO_ARGUMENTS);}
    static Object call(Object target,String name,Object... args) {
        if(target==null)return null;try{return invoke(target,name,args);}catch(ReflectiveOperationException|RuntimeException absent){return null;}
    }
    static Object invoke(Object target,String name,Object... args) throws ReflectiveOperationException {
        if(target==null)throw new NoSuchMethodException(name);
        Class<?> start=target instanceof Class?(Class<?>)target:target.getClass();
        for(NativeMethod method:nativeMembers(start).methods(name)) {
            if(method.parameters.length!=args.length)continue;
            boolean match=true;
            for(int i=0;i<method.parameters.length;i++)if(args[i]!=null&&!method.parameters[i].isInstance(args[i])){match=false;break;}
            if(match)return method.invoke(target instanceof Class?null:target,args);
        }throw new NoSuchMethodException(start.getName()+"."+name);
    }
    private static Class<?> boxed(Class<?> type){if(type==int.class)return Integer.class;if(type==float.class)return Float.class;if(type==boolean.class)return Boolean.class;if(type==long.class)return Long.class;return type;}
    static Object field(Object target,String name) {try{Field field=findField(target,name);return field==null?null:field.get(target);}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
    static boolean setField(Object target,String name,Object value) {try{Field field=findField(target,name);if(field==null)return false;field.set(target,value);return true;}catch(ReflectiveOperationException|RuntimeException unavailable){return false;}}
    private static Field findField(Object target,String name) throws ReflectiveOperationException {
        return target==null?null:nativeMembers(target.getClass()).field(name);
    }
    /** Bounded metadata only; actual native values remain live. ClassValue is API 34+, so
     * use a small access-ordered cache compatible with every supported Android version. */
    private static final Map<Class<?>,NativeMembers> NATIVE_MEMBERS=new LinkedHashMap<Class<?>,NativeMembers>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Class<?>,NativeMembers> entry){return size()>64;}
    };
    private static NativeMembers nativeMembers(Class<?> type) {
        synchronized(NATIVE_MEMBERS) {
            NativeMembers result=NATIVE_MEMBERS.get(type);
            if(result==null){result=new NativeMembers(type);NATIVE_MEMBERS.put(type,result);}
            return result;
        }
    }
    static void releaseNativeMembers(){synchronized(NATIVE_MEMBERS){NATIVE_MEMBERS.clear();}}
    private static final class NativeMembers {
        final Class<?> type;
        final Map<String,List<NativeMethod>> methods=new LinkedHashMap<>();
        final Map<String,Field> fields=new LinkedHashMap<>();
        NativeMembers(Class<?> type){this.type=type;}
        synchronized List<NativeMethod> methods(String name) {
            List<NativeMethod> found=methods.get(name);
            if(found!=null)return found;
            found=new ArrayList<>();
            for(Class<?> cls=type;cls!=null;cls=cls.getSuperclass())for(Method method:cls.getDeclaredMethods())
                if(method.getName().equals(name))found.add(new NativeMethod(method));
            methods.put(name,found);return found;
        }
        synchronized Field field(String name) {
            if(fields.containsKey(name))return fields.get(name);
            Field found=null;
            for(Class<?> cls=type;cls!=null;cls=cls.getSuperclass())try {
                found=cls.getDeclaredField(name);found.setAccessible(true);break;
            }catch(NoSuchFieldException absent){ }
            fields.put(name,found);return found;
        }
    }
    private static final class NativeMethod {
        final Method method;
        final Class<?>[] parameters;
        volatile boolean accessible;
        NativeMethod(Method method) {
            this.method=method;parameters=method.getParameterTypes();
            for(int i=0;i<parameters.length;i++)parameters[i]=boxed(parameters[i]);
        }
        Object invoke(Object target,Object[] args) throws ReflectiveOperationException {
            if(!accessible)synchronized(this){if(!accessible){method.setAccessible(true);accessible=true;}}
            return method.invoke(target,args);
        }
    }
    private void error(Throwable failure){if(!loggedError){loggedError=true;ModuleDiagnostics.error("qs_style","QS fill style unavailable; native material retained",failure);}}
    private boolean tracing(){return ModuleDiagnostics.enabled()&&traceStages.size()<64;}
    private void trace(String stage,String detail){if(tracing()&&traceStages.add(stage))ModuleDiagnostics.info("qs_style","QS fill "+detail.replace('=',' '));}
    static float number(Object value,float fallback){try{float result=value instanceof Number?((Number)value).floatValue():Float.parseFloat(String.valueOf(value));return Float.isFinite(result)?result:fallback;}catch(RuntimeException invalid){return fallback;}}
    static int color(Object value,int fallback) {
        if(value instanceof Number)return ((Number)value).intValue();if(!(value instanceof String))return fallback;String text=((String)value).trim();
        try{if(text.startsWith("#")){text=text.substring(1);if(text.length()==6)return (int)Long.parseLong(text,16)|0xff000000;if(text.length()==8)return (int)Long.parseLong(text,16);}else if(text.startsWith("0x")||text.startsWith("0X")){text=text.substring(2);if(text.length()<=8)return (int)Long.parseLong(text,16);}}catch(NumberFormatException invalid){ }return fallback;
    }
    static float angle(float value){float angle=value%360f;return angle<0?angle+360f:angle;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
