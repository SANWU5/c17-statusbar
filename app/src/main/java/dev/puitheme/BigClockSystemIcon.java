// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.style.ReplacementSpan;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owned inline glyphs. State/resource work happens at existing events, never by
 * polling, screen capture or making a bitmap in a text draw. The surrounding
 * TextView owns its material; these spans supply ordinary coloured coverage. */
final class BigClockSystemIcon {
    private static final String PACKAGE=ModuleVariant.PACKAGE;
    private static final ThreadLocal<Boolean> LOOKUP=new ThreadLocal<>();
    private static Context context;
    private static Resources moduleResources;
    private static WeakReference<View> nativeOwner;
    private static boolean single,customBattery,ios,percentage,inside,active,dataStyle,wifiStyle,resourceAttempted;
    private static int chargeIcon;
    private static int batteryWidth,batteryHeight;
    private static Typeface batteryTypeface;
    private static String batteryAxes;
    private static Map<String,Object> batteryValues=new LinkedHashMap<>();
    private static Bundle batterySettings=new Bundle();
    private static WeakReference<Object> stackedSignal,singleBinding,singleModel,seenSignal;
    private static int singleSubscription=-1;
    private static boolean signalStacked;
    private static final Map<Class<?>,CellAccess> CELL_ACCESS=new LinkedHashMap<>();
    private static String batteryClass="";
    private static long revision,policy;
    private static long nativeBatteryCreated,nativeBatteryFailures;
    private BigClockSystemIcon() { }

    static boolean configure(Context source,Bundle settings) {
        Bundle values=settings==null?new Bundle():settings;
        FeatureOptions options=FeatureOptions.from(values);
        boolean nextSingle=options.singleSignal(values.getString(StatusBarSettings.SIGNAL_LAYOUT,"system"));
        String style=values.getString(StatusBarSettings.BATTERY_STYLE,"pui");
        boolean nextIos=Ios27BatteryStyle.known(style);
        boolean nextBattery=options.effective("battery","battery_style_enabled")&&("pui".equals(style)||nextIos);
        int tokens=BigClockTextContent.CELLULAR|BigClockTextContent.NETWORK|BigClockTextContent.BATTERY;
        boolean nextActive=source!=null&&!options.safeMode()&&values.getBoolean(NotificationBigClockSettings.MASTER,false)
                &&((values.getBoolean(BigClockWidgetsSettings.LEFT_ENABLED,false)
                &&(BigClockTextContent.dependencies(values.getString(BigClockWidgetsSettings.LEFT_TEXT,BigClockWidgetsSettings.DEFAULT_LEFT_TEXT))&tokens)!=0)
                ||(values.getBoolean(BigClockWidgetsSettings.RIGHT_ENABLED,false)
                &&(BigClockTextContent.dependencies(values.getString(BigClockWidgetsSettings.RIGHT_TEXT,BigClockWidgetsSettings.DEFAULT_RIGHT_TEXT))&tokens)!=0));
        boolean nextData=options.effective("data","data_icon_enabled"),nextWifi=options.effective("wifi","wifi_icon_enabled");
        boolean nextPercent=nextActive&&source!=null&&BatteryNativeStyle.percentEnabled(source);
        Map<String,Object> nextBatteryValues=new LinkedHashMap<>();
        for(String key:values.keySet())if(key.startsWith("battery"))nextBatteryValues.put(key,values.get(key));
        if(context==source&&single==nextSingle&&customBattery==nextBattery&&ios==nextIos&&percentage==nextPercent
                &&active==nextActive&&dataStyle==nextData&&wifiStyle==nextWifi&&batteryValues.equals(nextBatteryValues))return false;
        boolean changedContext=context!=source;
        context=source;single=nextSingle;customBattery=nextBattery;ios=nextIos;percentage=nextPercent;
        active=nextActive;dataStyle=nextData;wifiStyle=nextWifi;
        batteryValues=nextBatteryValues;batterySettings=new Bundle(values);
        if(changedContext) {
            moduleResources=null;resourceAttempted=false;chargeIcon=0;batteryClass="";inside=nextPercent;
            batteryWidth=batteryHeight=0;batteryTypeface=null;batteryAxes=null;
        }
        policy++;revision++;
        View owner=nativeOwner==null?null:nativeOwner.get();
        if(active&&owner!=null&&owner.isAttachedToWindow())nativeBatteryChanged(owner);
        seenSignal=null;
        if(active) {
            Object dual=stackedSignal==null?null:stackedSignal.get();
            if(signalStacked&&dual!=null)nativeStackedChanged(dual);
            else if(singleBinding!=null&&singleModel!=null)nativeSingleChanged(singleBinding.get(),singleModel.get(),singleSubscription);
        }
        return true;
    }

    static long revision() { return revision; }
    static Map<String,Object> diagnostics() {
        Map<String,Object> result=new LinkedHashMap<>();BigClockSystemText.Snapshot state=BigClockSystemText.snapshot();
        result.put("icon_components_active",active);result.put("icon_signal_stacked",signalStacked);
        result.put("icon_primary_res",state.cellularResourceId);result.put("icon_secondary_res",state.secondaryCellularResourceId);
        result.put("icon_wifi_res",state.wifiResourceId);result.put("icon_wifi_level",state.wifiLevel);
        result.put("icon_battery_native",!batteryClass.isEmpty());
        String type=batteryClass.substring(batteryClass.lastIndexOf('.')+1);
        result.put("icon_battery_type",type);result.put("icon_battery_width_px",batteryWidth);result.put("icon_battery_height_px",batteryHeight);
        result.put("icon_battery_percent",percentage);result.put("icon_battery_inside",inside);result.put("icon_battery_charging",chargeIcon>0);
        result.put("icon_battery_created",nativeBatteryCreated);result.put("icon_battery_failures",nativeBatteryFailures);return result;
    }
    /** Called only by the existing icon-pack configuration/ready listener. */
    static boolean assetsChanged() { if(!active)return false;revision++;return true; }
    static void releaseRuntime() {
        context=null;moduleResources=null;nativeOwner=null;batteryClass="";chargeIcon=0;
        batteryWidth=batteryHeight=0;batteryTypeface=null;batteryAxes=null;
        batteryValues.clear();batterySettings=new Bundle();
        stackedSignal=singleBinding=singleModel=seenSignal=null;CELL_ACCESS.clear();singleSubscription=-1;signalStacked=false;
        nativeBatteryCreated=nativeBatteryFailures=0;
        single=customBattery=ios=percentage=inside=active=dataStyle=wifiStyle=resourceAttempted=false;LOOKUP.remove();policy++;revision++;
    }
    static boolean nativeLookupActive() { return Boolean.TRUE.equals(LOOKUP.get()); }
    static void observe(View view) { IconManagementRuntime.observe(view); }

    /** Existing native composition/binder events only; unchanged immutable
     * model identities do no reflection, resource lookup or row dispatch. */
    static boolean nativeStackedChanged(Object dual) {
        if(dual==null)return false;
        if(stackedSignal==null||stackedSignal.get()!=dual)stackedSignal=new WeakReference<>(dual);
        boolean wasStacked=signalStacked;signalStacked=true;
        if(!active||wasStacked&&seenSignal!=null&&seenSignal.get()==dual)return false;
        try {
            Object first=dual.getClass().getMethod("getPrimary").invoke(dual);
            Object second=dual.getClass().getMethod("getSecondary").invoke(dual);
            int primary=cellAccess(first).stacked(first,true),secondary=cellAccess(second).stacked(second,false);
            seenSignal=new WeakReference<>(dual);
            return BigClockSystemText.cellularIcons(primary,nativeResourceName(primary),secondary,nativeResourceName(secondary));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            seenSignal=new WeakReference<>(dual);return BigClockSystemText.cellularIcons(0,null,0,null);
        }
    }
    static boolean nativeSingleChanged(Object binding,Object model,int activeSubscription) {
        if(binding==null||model==null)return false;
        boolean accepted=false;
        try {
            Object vm=binding.getClass().getMethod("getViewModel").invoke(binding);
            int subscription=((Number)vm.getClass().getMethod("getSubscriptionId").invoke(vm)).intValue();
            if(subscription<0||subscription!=activeSubscription)return false;
            accepted=true;
            if(singleBinding==null||singleBinding.get()!=binding)singleBinding=new WeakReference<>(binding);
            if(singleModel==null||singleModel.get()!=model)singleModel=new WeakReference<>(model);
            singleSubscription=activeSubscription;boolean wasStacked=signalStacked;signalStacked=false;
            if(!active||!wasStacked&&seenSignal!=null&&seenSignal.get()==model)return false;
            int res=cellAccess(model).single(model);seenSignal=new WeakReference<>(model);
            return BigClockSystemText.cellularIcons(res,nativeResourceName(res),0,null);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            if(!accepted||!active)return false;
            seenSignal=new WeakReference<>(model);return BigClockSystemText.cellularIcons(0,null,0,null);
        }
    }
    private static String nativeResourceName(int id) {
        if(context==null||id<=0)return null;
        Resources resources=context.getResources();
        try {return "com.android.systemui".equals(resources.getResourcePackageName(id))?resources.getResourceEntryName(id):null;}
        catch(RuntimeException unavailable) {return null;}
    }
    private static CellAccess cellAccess(Object model)throws NoSuchMethodException {
        if(model==null)throw new NoSuchMethodException("No native cellular model");
        Class<?> type=model.getClass();CellAccess access=CELL_ACCESS.get(type);
        if(access==null){access=new CellAccess(type);CELL_ACCESS.put(type,access);}
        return access;
    }
    private static final class CellAccess {
        final Method single,stacked;
        CellAccess(Class<?> type)throws NoSuchMethodException {
            single=type.getMethod("toSignalDrawableState");stacked=type.getMethod("toStackedSignalDrawableState",Boolean.TYPE);
        }
        int single(Object model)throws ReflectiveOperationException{return ((Number)single.invoke(model)).intValue();}
        int stacked(Object model,boolean primary)throws ReflectiveOperationException{return ((Number)stacked.invoke(model,primary)).intValue();}
    }

    /** Read primitive native policy at the already installed battery binder and
     * percentage-setting events. Never retain a meter or its drawable. */
    static boolean nativeBatteryChanged(View owner) {
        if(owner==null)return false;
        if(nativeOwner==null||nativeOwner.get()!=owner)nativeOwner=new WeakReference<>(owner);
        if(!active)return false;
        try {
            Object drawable=owner.getClass().getMethod("getBatteryStyleDrawable").invoke(owner);
            if(drawable==null)return nativeBatteryDetached(owner);
            String name=drawable.getClass().getName();
            boolean nextInside;
            try {nextInside=field(drawable.getClass(),"isShowPercentIn").getBoolean(drawable);}
            catch(NoSuchFieldException otherNativeStyle) {nextInside=percentage;}
            int nextCharge=((Number)drawable.getClass().getMethod("getChargeIconId").invoke(drawable)).intValue();
            Object charge=owner.getClass().getMethod("getBatteryCharge").invoke(owner);
            if(charge!=null&&field(charge.getClass(),"isVisible").getBoolean(charge))
                nextCharge=field(charge.getClass(),"iconId").getInt(charge);
            boolean nextPercentage=BatteryNativeStyle.percentEnabled(owner.getContext());
            Rect bounds=((Drawable)drawable).getBounds();
            int nextWidth=Math.max(0,bounds.width()),nextHeight=Math.max(0,bounds.height());
            Typeface face=null;String axes=null;
            try {Paint paint=(Paint)field(drawable.getClass(),"percentInPaint").get(drawable);face=paint.getTypeface();axes=paint.getFontVariationSettings();}
            catch(NoSuchFieldException nonHorizontal) { /* Other styles have no percent paint. */ }
            if(name.equals(batteryClass)&&nextInside==inside&&nextCharge==chargeIcon&&nextPercentage==percentage
                    &&nextWidth==batteryWidth&&nextHeight==batteryHeight&&face==batteryTypeface&&Objects.equals(axes,batteryAxes))return false;
            batteryClass=name;inside=nextInside;chargeIcon=Math.max(0,nextCharge);percentage=nextPercentage;
            batteryWidth=nextWidth;batteryHeight=nextHeight;batteryTypeface=face;batteryAxes=axes;
            policy++;revision++;return true;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { return false; }
    }
    static boolean nativeBatteryDetached(View owner) {
        if(nativeOwner==null||nativeOwner.get()!=owner)return false;
        nativeOwner=null;boolean changed=!batteryClass.isEmpty()||batteryWidth!=0||batteryHeight!=0;
        batteryClass="";batteryWidth=batteryHeight=chargeIcon=0;batteryTypeface=null;batteryAxes=null;
        if(changed){policy++;revision++;}return changed&&active;
    }

    static Span obtain(int token,BigClockSystemText.Snapshot snapshot,Span previous) {
        if(token!=BigClockTextContent.CELLULAR&&token!=BigClockTextContent.NETWORK&&token!=BigClockTextContent.BATTERY)return null;
        BigClockSystemText.Snapshot state=snapshot==null?BigClockSystemText.snapshot():snapshot;
        Key key=new Key(token,state);
        if(previous!=null&&!previous.closed&&previous.key.equals(key))return previous;
        return new Span(key);
    }

    private static Field field(Class<?> type,String name)throws NoSuchFieldException {
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try {
            Field found=owner.getDeclaredField(name);found.setAccessible(true);return found;
        }catch(NoSuchFieldException absent) { /* Try this native type's parent. */ }
        throw new NoSuchFieldException(name);
    }
    private static Drawable load(Resources resources,int id) {
        if(resources==null||id==0)return null;
        Boolean before=LOOKUP.get();LOOKUP.set(Boolean.TRUE);
        try {Drawable result=resources.getDrawable(id,null);return result==null?null:result.mutate();}
        catch(RuntimeException|LinkageError missing) {return null;}
        finally {if(before==null)LOOKUP.remove();else LOOKUP.set(before);}
    }
    private static Drawable named(Resources resources,String name,String pkg) {
        if(resources==null||name==null)return null;
        try {return load(resources,ResourceIds.get(resources,name,"drawable",pkg));}
        catch(RuntimeException|LinkageError missing) {return null;}
    }
    private static Drawable signalGraphic(int id,String resource,boolean styled,boolean single) {
        Drawable result=styled?named(moduleResources(),SignalResources.moduleName(resource,single),PACKAGE):null;
        return result!=null?result:context==null?null:load(context.getResources(),id);
    }
    private static Resources moduleResources() {
        if(!resourceAttempted&&context!=null) {
            resourceAttempted=true;
            try {moduleResources=context.createPackageContext(PACKAGE,Context.CONTEXT_IGNORE_SECURITY).getResources();}
            catch(Exception|LinkageError unavailable) { /* A private native glyph remains an option. */ }
        }
        return moduleResources;
    }
    private static final int UNKNOWN=0,CELL=1,WIFI=2,POWER=3,OFFLINE=4,WIRED=5;
    private static final class Key {
        final int token,kind,level,res,charge;
        final boolean connected,charging,percent,inside,single,dataStyle,wifiStyle;
        final String resource,nativeClass;
        final int nativeWidth,nativeHeight;
        final int secondRes;
        final String secondResource;
        final Typeface nativeTypeface;
        final String nativeAxes;
        final long policy;
        Key(int token,BigClockSystemText.Snapshot state) {
            this.token=token;policy=BigClockSystemIcon.policy;single=BigClockSystemIcon.single;
            percent=percentage;inside=BigClockSystemIcon.inside;charge=chargeIcon;
            dataStyle=BigClockSystemIcon.dataStyle;wifiStyle=BigClockSystemIcon.wifiStyle;nativeClass=batteryClass;
            nativeWidth=batteryWidth;nativeHeight=batteryHeight;nativeTypeface=batteryTypeface;nativeAxes=batteryAxes;
            charging=nativeClass.isEmpty()?state.charging==BigClockSystemText.Charging.CHARGING||state.charging==BigClockSystemText.Charging.FULL:charge>0;
            int nextKind=UNKNOWN,nextLevel=-1,nextRes=0,nextSecond=0;boolean nextConnected=false;String name="",secondName="";
            if(token==BigClockTextContent.BATTERY) {nextKind=POWER;nextLevel=state.batteryPercent;}
            else if(token==BigClockTextContent.NETWORK&&state.transport==BigClockSystemText.Transport.WIFI) {
                if(Boolean.TRUE.equals(state.wifiVisible)&&state.wifiLevel>=0&&state.wifiResourceId>0) {
                    nextKind=WIFI;nextLevel=state.wifiLevel;nextRes=state.wifiResourceId;name=state.wifiResourceName;nextConnected=true;
                }
            } else if(token==BigClockTextContent.CELLULAR||state.transport==BigClockSystemText.Transport.CELLULAR) {
                if(state.cellularResourceId>0) {
                    nextKind=CELL;nextRes=state.cellularResourceId;name=state.cellularResourceName;
                    if(!single){nextSecond=state.secondaryCellularResourceId;secondName=state.secondaryCellularResourceName;}
                }
            } else if(state.transport==BigClockSystemText.Transport.NONE)nextKind=OFFLINE;
            else if(state.transport==BigClockSystemText.Transport.ETHERNET)nextKind=WIRED;
            kind=nextKind;level=nextLevel;res=nextRes;resource=name;connected=nextConnected;secondRes=nextSecond;secondResource=secondName;
        }
        @Override public boolean equals(Object value) {
            if(!(value instanceof Key))return false;Key other=(Key)value;
            return token==other.token&&kind==other.kind&&level==other.level&&res==other.res&&charge==other.charge
                    &&connected==other.connected&&charging==other.charging&&policy==other.policy&&resource.equals(other.resource)
                    &&secondRes==other.secondRes&&secondResource.equals(other.secondResource);
        }
        @Override public int hashCode() {return Objects.hash(token,kind,level,res,charge,connected,charging,policy,resource,secondRes,secondResource);}
    }

    static final class Span extends ReplacementSpan implements AutoCloseable {
        private final Key key;
        private final Paint.FontMetricsInt metrics=new Paint.FontMetricsInt();
        private Drawable graphic;
        private Drawable secondary;
        private final IconPackDrawing signalPack=new IconPackDrawing();
        private NativeBattery nativeBattery;
        private boolean closed;
        private int width=-1,height=-1;
        Span(Key key) {
            this.key=key;
            if(key.kind==POWER&&key.level>=0) {
                // The same OEM renderer owns body padding, percent placement,
                // native typography and charging. Scale its entire native box.
                nativeBattery=NativeBattery.create(context,key);
            } else if(key.kind==WIFI) {
                graphic=signalGraphic(key.res,key.resource,key.wifiStyle,key.single);
            } else if(key.kind==CELL) {
                graphic=signalGraphic(key.res,key.resource,key.dataStyle,key.single);
                if(key.secondRes>0)secondary=signalGraphic(key.secondRes,key.secondResource,key.dataStyle,false);
            }
        }
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt output) {
            if(output!=null)paint.getFontMetricsInt(output);
            if(graphic==null&&nativeBattery==null)return 0;
            return Math.max(1,(int)Math.ceil(size(paint)*ratio()+size(paint)*.12f));
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint) {
            if(closed||canvas==null||graphic==null&&nativeBattery==null)return;
            float side=size(paint);paint.getFontMetricsInt(metrics);
            float w=side*ratio(),y=baseline+(metrics.ascent+metrics.descent-side)*.5f;
            int saved=canvas.save();
            try {
                canvas.translate(x,y);
                int nextWidth=Math.max(1,Math.round(w)),nextHeight=Math.max(1,Math.round(side));
                if(nextWidth!=width||nextHeight!=height) {
                    width=nextWidth;height=nextHeight;
                    if(graphic!=null) {
                        float iw=Math.max(1,graphic.getIntrinsicWidth()),ih=Math.max(1,graphic.getIntrinsicHeight());
                        float fit=Math.min(width/iw,height/ih),gw=iw*fit,gh=ih*fit;
                        graphic.setBounds(Math.round((width-gw)*.5f),Math.round((height-gh)*.5f),Math.round((width+gw)*.5f),Math.round((height+gh)*.5f));
                        if(secondary!=null)secondary.setBounds(graphic.getBounds());
                    }
                    if(nativeBattery!=null&&!nativeBattery.bounds(width,height)) {nativeBattery.close();nativeBattery=null;}
                }
                if(graphic!=null) {
                    drawSignal(canvas,graphic,key.resource,paint);
                    if(secondary!=null)drawSignal(canvas,secondary,key.secondResource,paint);
                }
                else if(nativeBattery!=null&&!nativeBattery.draw(canvas,paint,w,side)) {
                    nativeBattery.close();nativeBattery=null;nativeBatteryFailures++;revision++;
                }
            } finally {canvas.restoreToCount(saved);}
        }
        private void drawSignal(Canvas canvas,Drawable glyph,String resource,Paint paint) {
            glyph.setTint(paint.getColor()|0xff000000);glyph.setAlpha(paint.getAlpha());
            if(!signalPack.drawSignal(resource,glyph,canvas,paint.getColor()|0xff000000,paint.getAlpha(),null,false))glyph.draw(canvas);
        }
        private float ratio() {return key.kind==POWER?(nativeBattery!=null?nativeBattery.ratio:29f/17f):graphic!=null?
                Math.max(1,graphic.getIntrinsicWidth())/(float)Math.max(1,graphic.getIntrinsicHeight()):1f;}
        private float size(Paint paint) {
            float em=paint.getTextSize();if(Float.isNaN(em)||Float.isInfinite(em)||em<=0)em=14f;
            paint.getFontMetricsInt(metrics);
            return Math.max(1f,Math.min(em*.94f,Math.max(1,metrics.descent-metrics.ascent)));
        }
        @Override public void close() {
            if(closed)return;closed=true;
            if(nativeBattery!=null){nativeBattery.close();nativeBattery=null;}
            graphic=secondary=null;signalPack.releaseRuntime();
        }
    }

    /** This private instance is never borrowed from, bound to, or registered as
     * a status-bar meter. Its native typeface callback is paired with close. */
    private static final class NativeBattery implements AutoCloseable {
        final Drawable drawable;
        final Method destroy,tint;
        final Field progress,background,outline;
        final Paint[] circle;
        final Field circleCharge;
        final RectF layer=new RectF();
        final PuiBatteryStyle pui;
        final IconPackDrawing iconPack;
        final BatteryTextStyle textStyle;
        final Context source;
        final Key key;
        final int nativeWidth,nativeHeight;
        final float ratio;
        int color;float scale;
        NativeBattery(Drawable drawable,Class<?> type,Context source,Key key)throws ReflectiveOperationException {
            this.source=source;this.key=key;
            this.drawable=drawable;destroy=type.getMethod("onDestroy");
            boolean round=type.getName().endsWith("CircleBatteryContentDrawable");
            nativeHeight=key.nativeHeight;nativeWidth=key.nativeWidth;
            ratio=nativeWidth/(float)nativeHeight;
            boolean horizontal=type.getName().endsWith(".HorizontalBatteryContentDrawable");
            pui=horizontal?new PuiBatteryStyle(type):null;
            if(pui!=null)pui.configure(batterySettings);
            iconPack=horizontal?new IconPackDrawing():null;
            if(iconPack!=null)iconPack.bindBatteryClass(type);
            textStyle=horizontal?new BatteryTextStyle():null;
            if(textStyle!=null)textStyle.configure(batterySettings);
            if(round) {
                progress=background=outline=null;tint=null;
                circle=new Paint[]{(Paint)field(type,"mCircleFrontPaint").get(drawable),
                        (Paint)field(type,"mCircleBackPaint").get(drawable),
                        (Paint)field(type,"mCircleChargingPaint").get(drawable),
                        (Paint)field(type,"circleFramePaint").get(drawable)};
                circleCharge=field(type,"chargingDrawable");
            } else {
                tint=type.getMethod("updateDrawableTint");
                progress=field(type,"progressColor");background=field(type,"backgroundColor");outline=field(type,"outlineColor");
                circle=null;circleCharge=null;
            }
        }
        static NativeBattery create(Context context,Key key) {
            // Binder delivery may precede the first native measure. onLayout
            // delivers real geometry; until then retain readable text only.
            if(context==null||key.nativeClass.isEmpty()||key.nativeWidth<=0||key.nativeHeight<=0)return null;
            Drawable owned=null;Method destroy=null;
            Boolean before=LOOKUP.get();LOOKUP.set(Boolean.TRUE);
            try {
                // ContextImpl itself is not public on Android. Call the public
                // Context API, rather than reflect an inaccessible concrete type.
                ClassLoader loader=context.getClassLoader();
                Class<?> type=loader.loadClass(key.nativeClass);Constructor<?> ctor=type.getConstructor(Context.class,Integer.TYPE);
                destroy=type.getMethod("onDestroy");owned=(Drawable)ctor.newInstance(context,0);
                NativeBattery result=new NativeBattery(owned,type,context,key);
                try {type.getMethod("setShowPercentIn",Boolean.TYPE).invoke(owned,key.inside&&key.percent);}
                catch(NoSuchMethodException nonHorizontal) { /* This native style owns its own percentage policy. */ }
                type.getMethod("setBatteryLevel",Integer.TYPE).invoke(owned,key.level);
                boolean hide=FeatureOptions.from(batterySettings).effective("battery",BatteryControls.HIDE_CHARGE);
                type.getMethod("setChargeIconId",Integer.TYPE).invoke(owned,key.charging&&!hide?key.charge:0);
                owned.setBounds(0,0,result.nativeWidth,result.nativeHeight);
                result.prepareTypography(type);
                if(result.iconPack!=null)result.iconPack.syncInlineBattery(owned,key.percent,key.charging);
                nativeBatteryCreated++;
                return result;
            }catch(Exception|LinkageError unsupported) {
                nativeBatteryFailures++;
                if(owned!=null&&destroy!=null)try {destroy.invoke(owned);}catch(ReflectiveOperationException|RuntimeException ignored) { }
                return null;
            }finally {if(before==null)LOOKUP.remove();else LOOKUP.set(before);}
        }
        boolean bounds(int width,int height) {
            scale=Math.min(width/(float)nativeWidth,height/(float)nativeHeight);
            return Float.isFinite(scale)&&scale>0f;
        }
        boolean draw(Canvas canvas,Paint paint,float width,float height) {
            try {
                int next=paint.getColor()|0xff000000;
                if(next!=color) {
                    color=next;
                    if(circle!=null) {
                        circle[0].setColor(next);circle[1].setColor((next&0xffffff)|0x4d000000);
                        circle[2].setColor(next);circle[3].setColor((next&0xffffff)|0x4d000000);
                        Drawable bolt=(Drawable)circleCharge.get(drawable);if(bolt!=null)bolt.setTint(next);
                    } else {progress.setInt(drawable,next);background.setInt(drawable,(next&0xffffff)|0x4d000000);outline.setInt(drawable,next);tint.invoke(drawable);}
                    if(pui!=null)prepareTypography(drawable.getClass());
                }
                layer.set(0,0,width,height);int saved=paint.getAlpha()==255?canvas.save():canvas.saveLayerAlpha(layer,paint.getAlpha());
                try {canvas.translate((width-nativeWidth*scale)*.5f,(height-nativeHeight*scale)*.5f);canvas.scale(scale,scale);
                    try(IconPackDrawing.BatteryScope scope=iconPack==null?null:iconPack.beginBattery(drawable)){drawable.draw(canvas);}
                }finally {canvas.restoreToCount(saved);}
                return true;
            }catch(Exception|LinkageError unsupported) {return false;}
        }
        private void prepareTypography(Class<?> type)throws Exception {
            if(pui!=null)pui.prepare(drawable,key.percent);
            if(textStyle==null)return;
            Paint paint=(Paint)field(type,"percentInPaint").get(drawable);
            if(key.nativeTypeface!=null)paint.setTypeface(key.nativeTypeface);
            if(key.nativeAxes!=null)paint.setFontVariationSettings(key.nativeAxes);
            if(textStyle.enabled())textStyle.apply(paint,source.getResources().getDisplayMetrics());
            type.getMethod("updatePercentInTextBounds").invoke(drawable);
        }
        @Override public void close() {drawable.setCallback(null);if(iconPack!=null)iconPack.releaseRuntime();if(pui!=null)pui.restore(drawable);try {destroy.invoke(drawable);}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) { }}
    }
}
