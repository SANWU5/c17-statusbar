// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** Replaces verified native glyphs, never their layout, transforms or battery text renderer. */
public final class IconPackDrawing {
    interface Assets {
        Bitmap image(String role);
        void observe(View view);
        boolean nativeAppearance(String role);
        boolean drawNative(String role,Drawable graphic,Canvas canvas,int tint);
        default Boolean draw(String role,Canvas canvas,RectF bounds,int tint,int alpha,ColorFilter filter){return null;}
        default Boolean draw(String role,Canvas canvas,RectF bounds,int tint,int alpha,ColorFilter filter,boolean hostScalePlaced){return draw(role,canvas,bounds,tint,alpha,filter);}
        default boolean drawNative(String role,Drawable graphic,Canvas canvas,int tint,boolean hostScalePlaced){return drawNative(role,graphic,canvas,tint);}
    }
    private static final Assets RUNTIME=new Assets(){
        public Bitmap image(String role){return IconManagementRuntime.bitmapForRole(role);}
        public void observe(View view){IconManagementRuntime.observe(view);}
        public boolean nativeAppearance(String role){return IconManagementRuntime.hasExplicitAppearance(role);}
        public boolean drawNative(String role,Drawable graphic,Canvas canvas,int tint){return IconManagementRuntime.drawNativeRole(role,graphic,canvas,tint);}
        public Boolean draw(String role,Canvas canvas,RectF bounds,int tint,int alpha,ColorFilter filter){return IconManagementRuntime.drawRole(role,canvas,bounds,tint,alpha,filter);}
        public Boolean draw(String role,Canvas canvas,RectF bounds,int tint,int alpha,ColorFilter filter,boolean hostScalePlaced){return IconManagementRuntime.drawRole(role,canvas,bounds,tint,alpha,filter,hostScalePlaced);}
        public boolean drawNative(String role,Drawable graphic,Canvas canvas,int tint,boolean hostScalePlaced){return IconManagementRuntime.drawNativeRole(role,graphic,canvas,tint,hostScalePlaced);}
    };
    private static final String HORIZONTAL="com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable";
    private static final String METER="com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView";
    private static final String STATUS_ICON="com.android.systemui.statusbar.StatusBarIconView";
    private final Assets assets;
    private final Map<Drawable,Mask> signals=new WeakHashMap<>();
    private final Map<Drawable,BatteryEntry> batteries=new WeakHashMap<>();
    private final Map<HintDrawable,Boolean> hints=new WeakHashMap<>();
    private final Map<Class<?>,HintAccess> hintAccess=new WeakHashMap<>();
    private final Map<Class<?>,MeterAccess> meterAccess=new WeakHashMap<>();
    private BatteryAccess batteryAccess;
    private boolean released;

    public IconPackDrawing(){this(RUNTIME);}
    IconPackDrawing(Assets assets){this.assets=assets;}

    /** Called inside ScaledDrawable's existing translated/scaled canvas, after native bounds/tint. */
    public boolean drawSignal(String resource,Drawable graphic,Canvas canvas,int tint,int alpha,
                              ColorFilter filter,boolean secondaryHidden){
        return drawSignal(resource,graphic,canvas,tint,alpha,filter,secondaryHidden,false);
    }
    public boolean drawSignal(String resource,Drawable graphic,Canvas canvas,int tint,int alpha,
                              ColorFilter filter,boolean secondaryHidden,boolean singleSignal){
        return drawSignal(resource,graphic,canvas,tint,alpha,filter,secondaryHidden,singleSignal,false);
    }
    public boolean drawSignal(String resource,Drawable graphic,Canvas canvas,int tint,int alpha,
                              ColorFilter filter,boolean secondaryHidden,boolean singleSignal,boolean hostScalePlaced){
        if(released||secondaryHidden||graphic==null||canvas==null||resource==null)return false;
        Mask mask=signals.get(graphic);
        if(mask==null||!resource.equals(mask.resource)||mask.singleSignal!=singleSignal){
            Signal role=Signal.read(resource,singleSignal);if(role==null)return false;
            mask=new Mask(resource,role,singleSignal);signals.put(graphic,mask);
        }
        Bitmap bitmap=assets.image(mask.signal.role);
        Rect bounds=graphic.getBounds();
        return mask.draw(assets,mask.signal.role,bitmap,canvas,bounds.left,bounds.top,bounds.right,bounds.bottom,tint,alpha,filter,hostScalePlaced)
                ||assets.drawNative(mask.signal.role,graphic,canvas,tint,hostScalePlaced);
    }

    /** The incoming model is used: getIcon runs before StatusBarIconView updates its own mIcon. */
    public Drawable wrapHint(View owner,Object incomingModel,Drawable graphic){
        // AnimatedImageView starts/stops only an actual AnimationDrawable. A generic
        // wrapper would erase that native type contract; dynamic hints stay native.
        if(graphic instanceof android.graphics.drawable.Animatable)return graphic;
        if(released||owner==null||graphic==null||!named(owner.getClass(),STATUS_ICON))return graphic;
        try{
            HintAccess access=hintAccess.get(owner.getClass());
            if(access==null){access=new HintAccess(owner.getClass());hintAccess.put(owner.getClass(),access);}
            if(access.notification.invoke(owner)!=null)return graphic;
            String slot=(String)access.slot.invoke(owner);
            if(!Arrays.asList(NativeStatusIcons.PRIORITY_SLOTS).contains(slot))return graphic;
            boolean on=true;
            // Both are visible system hints; these two real resource IDs distinguish Bluetooth connection.
            if("bluetooth".equals(slot)&&incomingModel!=null){
                Field icon=optionalField(incomingModel.getClass(),"icon");
                Object modelIcon=icon==null?null:icon.get(incomingModel);
                if(modelIcon!=null)try{
                    int id=((Number)modelIcon.getClass().getMethod("getResId").invoke(modelIcon)).intValue();
                    String name=owner.getResources().getResourceEntryName(id);
                    if("stat_sys_data_bluetooth".equals(name))on=false;
                    else if("stat_sys_data_bluetooth_connected".equals(name))on=true;
                }catch(ReflectiveOperationException|RuntimeException unsupported){/* Keep visible-state semantics. */}
            }
            if(graphic instanceof HintDrawable){
                HintDrawable existing=(HintDrawable)graphic;
                if(existing.helper==this){existing.slot=slot;existing.on=on;existing.role=hintRole(slot,on);assets.observe(owner);return existing;}
            }
            HintDrawable result=new HintDrawable(this,graphic,slot,on);
            hints.put(result,Boolean.TRUE);assets.observe(owner);return result;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return graphic;}
    }

    /** Bind once to the actual OEM class loaded by SystemUI; no class or field discovery per frame. */
    public void bindBatteryClass(Class<?> horizontal){
        if(released||horizontal==null||!HORIZONTAL.equals(horizontal.getName()))return;
        try{batteryAccess=new BatteryAccess(horizontal);}
        catch(ReflectiveOperationException unsupported){batteryAccess=null;}
    }

    /** Binder events provide charging state which may be rendered in a sibling, not inside the battery. */
    public void syncBattery(View owner,Object incomingCharge){
        if(released||owner==null||batteryAccess==null||!named(owner.getClass(),METER))return;
        try{
            MeterAccess access=meterAccess.get(owner.getClass());
            if(access==null){access=new MeterAccess(owner.getClass());meterAccess.put(owner.getClass(),access);}
            Object object=access.style.invoke(owner);
            if(!batteryAccess.type.isInstance(object))return;
            Drawable drawable=(Drawable)object;
            Object charge=incomingCharge!=null?incomingCharge:access.charge.invoke(owner);
            boolean charging=false;
            if(charge!=null){
                Field visible=optionalField(charge.getClass(),"isVisible"),id=optionalField(charge.getClass(),"iconId");
                charging=visible!=null&&id!=null&&visible.getBoolean(charge)&&id.getInt(charge)>0;
            }
            BatteryEntry entry=batteries.get(drawable);
            if(entry==null){entry=new BatteryEntry(drawable,batteryAccess,assets);batteries.put(drawable,entry);}
            entry.percentage=BatteryNativeStyle.percentEnabled(owner.getContext());
            entry.externalCharging=charging;assets.observe(owner);
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Draw still reads its native charge ID. */}
    }

    /** An inline component owns this independent OEM drawable. Its body uses
     * exactly the same icon-pack scope as a status-bar meter, with native
     * percent/charge drawing retained and no synthetic View or callback. */
    void syncInlineBattery(Drawable drawable,boolean percentage,boolean charging) {
        if(released||drawable==null||batteryAccess==null||!batteryAccess.type.isInstance(drawable))return;
        BatteryEntry entry=batteries.get(drawable);
        if(entry==null){entry=new BatteryEntry(drawable,batteryAccess,assets);batteries.put(drawable,entry);}
        entry.percentage=percentage;entry.externalCharging=charging;
    }

    /** Only shape fields change during this draw. Native drawContent/percent Paint/charging remain untouched. */
    public BatteryScope beginBattery(Drawable drawable){
        BatteryAccess access=batteryAccess;
        if(released||drawable==null||access==null||!access.type.isInstance(drawable))return null;
        BatteryEntry entry=batteries.get(drawable);
        if(entry==null)return null;
        if(entry.depth>0){if(entry.depth>=entry.scopes.length)return null;entry.depth++;return entry.scope();}
        try{
            int level=primitive(access.level,drawable);
            if(level<0||level>100)return null;
            boolean charging=primitive(access.charge,drawable)>0||entry.externalCharging;
            String role=BATTERY_ROLES[charging?1:0][level/10];
            Bitmap bitmap=assets.image(role);
            boolean image=valid(bitmap);
            if(!image&&!assets.nativeAppearance(role))return null;
            boolean inside=access.inside.getBoolean(drawable);
            RectF rect=(RectF)(inside?access.inner:access.outer).get(drawable);
            if(image&&(rect==null||!finite(rect.left)||!finite(rect.top)||!finite(rect.right)||!finite(rect.bottom)
                    ||rect.width()<=0f||rect.height()<=0f))return null;
            for(int i=0;i<access.shapes.length;i++){
                Object shape=access.shapes[i].get(drawable);
                if(shape!=null&&!(shape instanceof Drawable)){entry.clear();return null;}
                entry.originals[i]=(Drawable)shape;
            }
            entry.bitmap=image?bitmap:null;entry.role=role;if(image)entry.region.set(rect.left,rect.top,rect.right,rect.bottom);
            entry.tint=primitive(access.outline,drawable);entry.drawn=false;entry.failed=false;
            entry.depth=1;
            try{
                for(int i=0;i<access.shapes.length;i++)if(entry.originals[i]!=null)access.shapes[i].set(drawable,entry.proxies[i]);
            }catch(ReflectiveOperationException failed){entry.restore();return null;}
            return entry.scope();
        }catch(ReflectiveOperationException|RuntimeException unsupported){
            if(entry!=null)entry.restore();return null;
        }
    }

    public void invalidateAll(){
        for(Drawable drawable:signals.keySet().toArray(new Drawable[0]))if(drawable!=null)drawable.invalidateSelf();
        for(HintDrawable drawable:hints.keySet().toArray(new HintDrawable[0]))if(drawable!=null)drawable.invalidateSelf();
        for(Drawable drawable:batteries.keySet().toArray(new Drawable[0]))if(drawable!=null)drawable.invalidateSelf();
    }
    public void releaseRuntime(){
        released=true;
        for(BatteryEntry entry:batteries.values())entry.restore();
        invalidateAll();signals.clear();batteries.clear();hints.clear();hintAccess.clear();meterAccess.clear();
    }

    public static final class BatteryScope implements AutoCloseable {
        private BatteryEntry entry;
        private BatteryScope(BatteryEntry entry){this.entry=entry;}
        @Override public void close(){
            BatteryEntry value=entry;entry=null;
            if(value!=null&&value.depth>0&&--value.depth==0)value.restore();
        }
    }
    private static final String[][] BATTERY_ROLES=new String[2][11];
    static{for(int charge=0;charge<2;charge++)for(int level=0;level<=10;level++)BATTERY_ROLES[charge][level]="battery."+(charge==1?"charging.":"")+(level*10);}
    private static String hintRole(String slot,boolean on){return "hint."+slot+("bluetooth".equals(slot)?on?".on":".off":".default");}
    /** Exact audited resource identity; callers cache it until resource or row policy changes. */
    static String signalRole(String resource,boolean singleSignal){Signal signal=resource==null?null:Signal.read(resource,singleSignal);return signal==null?null:signal.role;}
    private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
    private static int primitive(MethodHandle accessor,Drawable drawable)throws ReflectiveOperationException{
        try{return (int)accessor.invokeExact((Object)drawable);}
        catch(RuntimeException failure){throw failure;}
        catch(Error failure){throw failure;}
        catch(Throwable unavailable){throw new ReflectiveOperationException(unavailable);}
    }
    private static boolean valid(Bitmap bitmap){return bitmap!=null&&!bitmap.isRecycled()&&bitmap.getWidth()>0&&bitmap.getHeight()>0;}
    private static boolean named(Class<?> type,String name){for(Class<?> c=type;c!=null;c=c.getSuperclass())if(name.equals(c.getName()))return true;return false;}
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException{
        Field result=optionalField(type,name);if(result==null)throw new NoSuchFieldException(name);return result;
    }
    private static Field optionalField(Class<?> type,String name){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field result=c.getDeclaredField(name);result.setAccessible(true);return result;}
        catch(NoSuchFieldException absent){ }return null;
    }
    private static final class HintAccess {
        final Method slot,notification;
        HintAccess(Class<?> type)throws ReflectiveOperationException{slot=type.getMethod("getSlot");notification=type.getMethod("getNotification");}
    }
    private static final class MeterAccess {
        final Method style,charge;
        MeterAccess(Class<?> type)throws ReflectiveOperationException{style=type.getMethod("getBatteryStyleDrawable");charge=type.getMethod("getBatteryCharge");}
    }
    private static final class BatteryAccess {
        final Class<?> type;
        final Field[] shapes;
        final Field inside,inner,outer;
        final MethodHandle level,charge,outline;
        BatteryAccess(Class<?> type)throws ReflectiveOperationException{
            this.type=type;
            shapes=new Field[]{field(type,"outsideDrawable"),field(type,"insideDrawable"),field(type,"frameDrawable"),field(type,"bgDrawable"),field(type,"progressDrawable")};
            for(int i=0;i<shapes.length;i++)if(shapes[i].getType()!=(i==1||i==4?LayerDrawable.class:Drawable.class))throw new NoSuchFieldException("Unsupported battery shape type");
            inside=field(type,"isShowPercentIn");inner=field(type,"rect");outer=field(type,"noPercentRectF");
            level=accessor(type,"getBatteryLevel");charge=accessor(type,"getChargeIconId");outline=accessor(type,"getOutlineColor");
        }
        private static MethodHandle accessor(Class<?> type,String name)throws ReflectiveOperationException{
            Method method=type.getMethod(name);if(method.getReturnType()!=Integer.TYPE)throw new NoSuchMethodException(name);
            return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(Integer.TYPE,Object.class));
        }
    }
    private static final class Signal {
        final boolean wifi,connected;final int level;final String role;
        Signal(boolean wifi,int level,boolean connected){this.wifi=wifi;this.level=level;this.connected=connected;this.role=(wifi?"wifi.":"cellular.")+(connected?Integer.toString(level):"none");}
        Signal(String row,int level,boolean connected){this.wifi=false;this.level=level;this.connected=connected;this.role="cellular.stacked_"+row+'.'+(connected?Integer.toString(level):"none");}
        static Signal read(String name,boolean singleSignal){
            // WifiIcon.Companion.fromModel returns Hidden for native Inactive/Unavailable.
            // Level zero is still Active. activity_wifi_none belongs to traffic arrows,
            // not a disconnected signal glyph, and must not consume the wifi.none artwork.
            if(name.matches("stat_signal_wifi_signal_[0-4](_os17)?"))return new Signal(true,digit(name),true);
            if(name.matches("stat_signal_(lte|soft)_signal_stacked_(primary|secondary)_([0-4]|noservice)")){
                boolean service=!name.endsWith("_noservice");if(singleSignal)return name.contains("_secondary_")?null:new Signal(false,service?digit(name):0,service);
                return new Signal(name.contains("_primary_")?"primary":"secondary",service?digit(name):0,service);
            }
            if(name.matches("stat_signal_(lte|soft)_signal_([0-4]|noservice)(_os17)?")
                    ||name.matches("stat_sys_signal_[0-4](_fully)?")
                    ||name.matches("stat_signal_signal_lte_single_[0-4](_os17)?")
                    ||name.matches("stat_signal_signal_novoice_[1-4](_os17)?")
                    ||name.matches("stat_signal_single_novoice_0(_os17)?")){
                boolean service=!name.contains("noservice");return new Signal(false,service?digit(name):0,service);
            }
            if(name.matches("stat_signal_(noservice_lte|soft_noservice)(_os17)?")||name.equals("stat_signal_signal_null_lte")
                    ||name.equals("stat_sys_signal_null")||name.equals("stat_sys_signal_noservice"))return new Signal(false,0,false);
            return null;
        }
        static int digit(String name){String normal=name.replace("_os17","").replace("_fully","");return normal.charAt(normal.length()-1)-'0';}
    }
    private static class Mask {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|2); // FILTER_BITMAP_FLAG; avoid creating Paint/RectF per frame.
        final RectF target=new RectF();
        String resource;Signal signal;boolean singleSignal;
        Mask(){ }
        Mask(String resource,Signal signal,boolean singleSignal){this.resource=resource;this.signal=signal;this.singleSignal=singleSignal;}
        boolean draw(Assets assets,String role,Bitmap bitmap,Canvas canvas,float left,float top,float right,float bottom,int color,int alpha,ColorFilter filter){
            return draw(assets,role,bitmap,canvas,left,top,right,bottom,color,alpha,filter,false);
        }
        boolean draw(Assets assets,String role,Bitmap bitmap,Canvas canvas,float left,float top,float right,float bottom,int color,int alpha,ColorFilter filter,boolean hostScalePlaced){
            if(canvas==null||right<=left||bottom<=top)return false;
            // V3 fits the visible glyph, so pass the original host region before any legacy canvas-size fit.
            // The production renderer selects one role snapshot for this draw. An old bitmap
            // must never fall through as legacy artwork after a background publication.
            target.set(left,top,right,bottom);Boolean v3=assets.draw(role,canvas,target,color,alpha,filter,hostScalePlaced);
            if(v3!=null)return v3;
            if(!valid(bitmap))return false;
            v3=IconManagementRuntime.draw(bitmap,canvas,target,color,alpha,filter);if(v3!=null)return v3;
            float scale=Math.min((right-left)/bitmap.getWidth(),(bottom-top)/bitmap.getHeight());
            float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;
            float x=(left+right)*.5f,y=(top+bottom)*.5f;target.set(x-width*.5f,y-height*.5f,x+width*.5f,y+height*.5f);
            // A native SRC_IN filter already carries tint alpha: do not multiply it a second time.
            paint.setColor(filter==null?color:0xffffffff);
            paint.setAlpha((filter==null?color>>>24:255)*Math.max(0,Math.min(255,alpha))/255);paint.setColorFilter(filter);
            try{canvas.drawBitmap(bitmap,null,target,paint);return true;}
            catch(RuntimeException unavailable){return false;}
        }
    }
    private static final class HintDrawable extends Drawable implements Drawable.Callback {
        final IconPackDrawing helper;final Drawable nativeGraphic;final Mask mask=new Mask();
        String slot,role;boolean on;
        int tint=0xffffffff;ColorStateList colors;
        HintDrawable(IconPackDrawing helper,Drawable nativeGraphic,String slot,boolean on){
            this.helper=helper;this.nativeGraphic=nativeGraphic;this.slot=slot;this.on=on;this.role=hintRole(slot,on);
            setBounds(nativeGraphic.getBounds());setState(nativeGraphic.getState());setLevel(nativeGraphic.getLevel());nativeGraphic.setCallback(this);
            setLayoutDirection(nativeGraphic.getLayoutDirection());setVisible(nativeGraphic.isVisible(),false);
        }
        @Override public void draw(Canvas canvas){
            if(helper.released){nativeGraphic.draw(canvas);return;}
            Rect r=getBounds();Bitmap bitmap=helper.assets.image(role);
            int color=colors==null?tint:colors.getColorForState(getState(),colors.getDefaultColor());
            if(!mask.draw(helper.assets,role,bitmap,canvas,r.left,r.top,r.right,r.bottom,color,nativeGraphic.getAlpha(),nativeGraphic.getColorFilter())
                    &&(helper.released||!helper.assets.drawNative(role,nativeGraphic,canvas,color)))nativeGraphic.draw(canvas);
        }
        @Override protected void onBoundsChange(Rect bounds){nativeGraphic.setBounds(bounds);}
        @Override protected boolean onStateChange(int[] state){return nativeGraphic.setState(state)||colors!=null;}
        @Override protected boolean onLevelChange(int level){return nativeGraphic.setLevel(level);}
        @Override public boolean isStateful(){return nativeGraphic.isStateful()||colors!=null;}
        @Override public void setAlpha(int value){nativeGraphic.setAlpha(value);invalidateSelf();}
        @Override public int getAlpha(){return nativeGraphic.getAlpha();}
        @Override public void setColorFilter(ColorFilter value){nativeGraphic.setColorFilter(value);invalidateSelf();}
        @Override public ColorFilter getColorFilter(){return nativeGraphic.getColorFilter();}
        @Override public void setTint(int value){tint=value;colors=null;nativeGraphic.setTint(value);invalidateSelf();}
        @Override public void setTintList(ColorStateList value){colors=value;if(value==null)tint=0xffffffff;nativeGraphic.setTintList(value);invalidateSelf();}
        @Override public void setTintMode(PorterDuff.Mode value){nativeGraphic.setTintMode(value);}
        @Override public int getOpacity(){return nativeGraphic.getOpacity();}
        @Override public int getIntrinsicWidth(){return nativeGraphic.getIntrinsicWidth();}
        @Override public int getIntrinsicHeight(){return nativeGraphic.getIntrinsicHeight();}
        @Override public int getMinimumWidth(){return nativeGraphic.getMinimumWidth();}
        @Override public int getMinimumHeight(){return nativeGraphic.getMinimumHeight();}
        @Override public boolean getPadding(Rect result){return nativeGraphic.getPadding(result);}
        @Override public boolean setVisible(boolean visible,boolean restart){return super.setVisible(visible,restart)|nativeGraphic.setVisible(visible,restart);}
        @Override public boolean onLayoutDirectionChanged(int direction){return nativeGraphic.setLayoutDirection(direction);}
        @Override public void setAutoMirrored(boolean mirrored){nativeGraphic.setAutoMirrored(mirrored);}
        @Override public boolean isAutoMirrored(){return nativeGraphic.isAutoMirrored();}
        @Override public void setHotspot(float x,float y){nativeGraphic.setHotspot(x,y);}
        @Override public void setHotspotBounds(int left,int top,int right,int bottom){nativeGraphic.setHotspotBounds(left,top,right,bottom);}
        @Override public void getHotspotBounds(Rect bounds){nativeGraphic.getHotspotBounds(bounds);}
        @Override public void jumpToCurrentState(){nativeGraphic.jumpToCurrentState();}
        @Override public void invalidateDrawable(Drawable who){invalidateSelf();}
        @Override public void scheduleDrawable(Drawable who,Runnable task,long when){scheduleSelf(task,when);}
        @Override public void unscheduleDrawable(Drawable who,Runnable task){unscheduleSelf(task);}
    }
    private static final class BatteryEntry extends Mask {
        final WeakReference<Drawable> owner;final BatteryAccess access;
        final Assets assets;final BatteryScope[] scopes=new BatteryScope[8];
        final Drawable[] originals=new Drawable[5],proxies=new Drawable[5];
        final RectF region=new RectF();Bitmap bitmap;
        String role;int depth,tint;boolean externalCharging,drawn,failed,percentage;
        BatteryEntry(Drawable owner,BatteryAccess access,Assets assets){
            this.owner=new WeakReference<>(owner);this.access=access;this.assets=assets;
            for(int i=0;i<proxies.length;i++)proxies[i]=i==1||i==4?new LayerProxy(this,i):new ShapeProxy(this,i);
            for(int i=0;i<scopes.length;i++)scopes[i]=new BatteryScope(null);
        }
        BatteryScope scope(){BatteryScope scope=scopes[depth-1];scope.entry=this;return scope;}
        void shape(Canvas canvas,int index){
            Drawable graphic=originals[index];if(graphic==null)return;
            if(depth==0||failed){graphic.draw(canvas);return;}
            if(bitmap==null){if(!assets.drawNative(role,graphic,canvas,tint))graphic.draw(canvas);return;}
            if(!drawn){
                drawn=true;
                if(!draw(assets,role,bitmap,canvas,region.left,region.top,region.right,region.bottom,tint,graphic.getAlpha(),null)){
                    failed=true;graphic.draw(canvas);
                }
            }
        }
        void restore(){
            Drawable drawable=owner.get();
            if(drawable!=null)for(int i=0;i<proxies.length;i++)try{
                if(access.shapes[i].get(drawable)==proxies[i])access.shapes[i].set(drawable,originals[i]);
            }catch(ReflectiveOperationException unsupported){ }
            clear();
        }
        void clear(){depth=0;bitmap=null;role=null;Arrays.fill(originals,null);drawn=false;failed=false;}
    }
    /** Do not assign native child callbacks to these temporary proxies or consume their constant state. */
    private static final class ShapeProxy extends Drawable {
        final BatteryEntry entry;final int index;
        ShapeProxy(BatteryEntry entry,int index){this.entry=entry;this.index=index;}
        @Override public void draw(Canvas canvas){entry.shape(canvas,index);}
        @Override public void setAlpha(int value){Drawable d=entry.originals[index];if(d!=null)d.setAlpha(value);}
        @Override public void setColorFilter(ColorFilter value){Drawable d=entry.originals[index];if(d!=null)d.setColorFilter(value);}
        @Override public int getOpacity(){return -3;}
    }
    private static final class LayerProxy extends LayerDrawable {
        final BatteryEntry entry;final int index;
        LayerProxy(BatteryEntry entry,int index){super(new Drawable[0]);this.entry=entry;this.index=index;}
        @Override public void draw(Canvas canvas){entry.shape(canvas,index);}
        @Override public void setAlpha(int value){Drawable d=entry.originals[index];if(d!=null)d.setAlpha(value);}
        @Override public void setColorFilter(ColorFilter value){Drawable d=entry.originals[index];if(d!=null)d.setColorFilter(value);}
        @Override public int getOpacity(){return -3;}
    }
}
