package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actual settings codecs, measured header, edge draw and native row hit testing. No device. */
public final class NotificationNegativeSpacingCheck {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void near(float expected,float actual,String label){check(Math.abs(expected-actual)<.01f,label+": "+expected+" != "+actual);}
    private static Field field(Class<?> type,String name)throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object get(Object owner,String name)throws Exception{return field(owner.getClass(),name).get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{field(owner.getClass(),name).set(owner,value);}
    private static Method method(Class<?> type,String name,Class<?>...args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static Object settings(Bundle saved,String prefix)throws Exception {
        Constructor<?> ctor=Class.forName("dev.puitheme.NotificationBigClock$Settings").getDeclaredConstructor(Bundle.class,String.class);
        ctor.setAccessible(true);return ctor.newInstance(saved,prefix);
    }
    private static void settingsChain()throws Exception {
        for(boolean locked:new boolean[]{false,true})for(String prefix:new String[]{NotificationBigClockSettings.PORTRAIT_PREFIX,NotificationBigClockSettings.LANDSCAPE_PREFIX}) {
            Map<String,Object> saved=new LinkedHashMap<>();saved.put(NotificationBigClockSettings.REBOUND_LOCK,locked);
            saved.put(NotificationBigClockSettings.REBOUND_BLUR_ENABLED,true);
            for(String suffix:new String[]{"notification_gap","notification_edge_safe_distance"}) {
                String key=prefix+suffix;SettingsCatalog.Item item=SettingsCatalog.item(key);
                check(item.min==-100f&&item.max==100f,"signed slider recommendation: "+key);
                check(SettingEditor.forItem(item,saved).signed,"signed editor semantics: "+key);
                check(SettingsCatalog.unavailableReason(key,saved).isEmpty(),"rebound choice never locks signed spacing input");
                for(float value:new float[]{-1000.25f,-100f,-18.75f,-.01f,0f,18f,1000.25f}) {
                    near(value,SettingsCatalog.customNumber(item,Float.toString(value)),"manual input");
                    check(SettingsCatalog.validationError(item,value)==null,"validation accepts finite signed value");
                    near(value,NumericPolicy.setting(key,value,18f),"shared numeric policy");
                }
                saved.put(key,-18.75f);
            }
            Map<String,Object> imported=new LinkedHashMap<>(ConfigTransfer.prepare(ConfigTransfer.exportJson(saved),true).values());
            Bundle snapshot=SettingsSnapshot.fromPreferences(imported);
            check(SettingsSnapshot.complete(snapshot),"negative values keep snapshot complete");
            ActivationGuardPreferencesCheck.MemoryPreferences mirror=new ActivationGuardPreferencesCheck.MemoryPreferences();
            check(SettingsFrameworkMirror.write(mirror,snapshot,17L),"framework mirror durable write");
            Bundle decoded=SettingsFrameworkMirror.decode(mirror.getAll());check(decoded!=null,"framework mirror decode");
            Object runtime=settings(decoded,prefix);
            for(String suffix:new String[]{"notification_gap","notification_edge_safe_distance"})near(-18.75f,decoded.getFloat(prefix+suffix),"mirror preserves signed float");
            near(-18.75f,(Float)get(runtime,"notificationGap"),"runtime signed gap regardless of rebound");
            near(-18.75f,(Float)get(runtime,"notificationEdgeSafeDistance"),"runtime signed edge regardless of rebound");
            imported.put(prefix+"notification_gap_enabled",false);
            near(0f,(Float)get(settings(SettingsSnapshot.fromPreferences(imported),prefix),"notificationGap"),"gap switch pauses saved negative gap");
            near(-18.75f,((Number)imported.get(prefix+"notification_gap")).floatValue(),"pause retains saved value");
        }
    }
    private static void model() {
        for(float d:new float[]{.5f,1f,2f,4f})for(int scroll:new int[]{0,80,10000}) {
            NotificationBigClockModel.Frame zero=NotificationBigClockModel.measured(1280f*d,d,48f*d,180f*d,64f*d,20f*d,12f,0f,0f,0f,0f,600f,600f,scroll,0f,1f);
            NotificationBigClockModel.Frame negative=NotificationBigClockModel.measured(1280f*d,d,48f*d,180f*d,64f*d,20f*d,12f,-18.75f,0f,0f,0f,600f,600f,scroll,0f,1f);
            near(zero.reservedBottom-18.75f*d,negative.reservedBottom,"portrait signed reservation");
            near(zero.clockTop,negative.clockTop,"gap never moves clock");near(zero.collapseDistance,negative.collapseDistance,"gap never changes collapse progress");
            zero=NotificationBigClockModel.landscapeMeasured(d,48f*d,72f*d,25f*d,20f*d,12f,0f,0f,0f,0f,600f,scroll,1f);
            negative=NotificationBigClockModel.landscapeMeasured(d,48f*d,72f*d,25f*d,20f*d,12f,-18.75f,0f,0f,0f,600f,scroll,1f);
            near(zero.reservedBottom-18.75f*d,negative.reservedBottom,"landscape signed reservation");
            negative=NotificationBigClockModel.landscapeMeasured(d,48f*d,72f*d,25f*d,20f*d,12f,-Float.MAX_VALUE,0f,0f,0f,600f,scroll,1f);
            check(negative.reservedBottom>=48f*d,"extreme landscape gap keeps physical top reservation");
        }
    }
    public static class Stack extends ViewGroup {
        public Object mOplusDispatchDrawHook,mBlurEffect;
        ExpandableNotificationRow row;
        NotificationBigClock owner;
        boolean paging;
        int nativeDraws;
        Canvas nativeCanvas;
        float nativeClipTop;
        RuntimeException nativeFailure;
        final Object marker=new Object();
        Object result;
        Stack(Context context){super(context);}
        public View getChildAtPosition(float x,boolean ignored,boolean includeTail,float y){return row;}
        /** The same nested dispatchDraw/drawDispatchedChildren seam wrapped by StatusBarModule. */
        @Override public void dispatchDraw(Canvas canvas) {
            try { result=owner.drawNotifications(this,canvas,paging,this::drawDispatchedChildren); }
            catch(RuntimeException|Error failure){throw failure;}
            catch(Throwable failure){throw new AssertionError(failure);}
        }
        public Object drawDispatchedChildren(Canvas canvas)throws Throwable {
            return owner.drawNotifications(this,canvas,paging,target->{
                nativeDraws++;nativeCanvas=target;nativeClipTop=((PagingCanvas)target).activeTop;
                if(nativeFailure!=null)throw nativeFailure;
                return marker;
            });
        }
    }
    /** Property-only child motion reuses a parent's display list until invalidate dirties it. */
    private static final class CachedStack extends Stack {
        int recordedAt=-1,recordings;
        CachedStack(Context context){super(context);}
        void composeFrame(){
            if(recordedAt==invalidations)return;
            dispatchDraw(new PagingCanvas(0f));recordedAt=invalidations;recordings++;
        }
    }
    private static final class PageMotion implements NotificationBigClock.PageMotion {
        boolean running;
        public void register(View view){}
        public void unregister(View view){}
        public boolean isRunning(){return running;}
    }
    /** Track the real save/clip/restore contract locally; no shared Canvas stub changes. */
    private static final class PagingCanvas extends Canvas {
        float activeTop;
        private final ArrayDeque<float[]> saved=new ArrayDeque<>();
        PagingCanvas(float top){hardware=true;clipBounds=new Rect(-32,(int)top,432,1280);activeTop=top;}
        @Override public int save(){int count=super.save();saved.push(new float[]{count,activeTop});return count;}
        @Override public boolean clipRect(float left,float top,float right,float bottom){
            boolean result=super.clipRect(left,top,right,bottom);activeTop=Math.max(activeTop,top);return result;
        }
        @Override public void restoreToCount(int count){
            while(!saved.isEmpty()&&saved.peek()[0]>=count)activeTop=saved.pop()[1];
            super.restoreToCount(count);
        }
    }
    private static void dimensions(View view) {
        view.useLayoutBoundsForCheck=true;view.getResources().getDisplayMetrics().density=view.getResources().getDisplayMetrics().scaledDensity=1f;
        view.getResources().getDisplayMetrics().widthPixels=400;view.getResources().getDisplayMetrics().heightPixels=1280;
        view.getResources().getConfiguration().orientation=1;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)dimensions(((ViewGroup)view).getChildAt(i));
    }
    @SuppressWarnings("unchecked")
    private static void drawingAndTouch(int topInset,int cutoutTop)throws Throwable {
        Context context=new Context();NotificationBigClock owner=new NotificationBigClock();
        FrameLayout root=new FrameLayout(context),host=new FrameLayout(context);root.addView(host);root.layout(0,0,400,1280);host.layout(0,0,400,1280);
        set(owner,"panel",new WeakReference<View>(host));
        Class<?> type=Class.forName("dev.puitheme.NotificationBigClock$ClockView");
        Constructor<?> ctor=type.getDeclaredConstructor(NotificationBigClock.class,Context.class);ctor.setAccessible(true);
        FrameLayout overlay=(FrameLayout)ctor.newInstance(owner,context);host.addView(overlay);overlay.layout(0,0,400,1280);
        android.view.WindowInsets insets=new android.view.WindowInsets();insets.topForCheck=topInset;
        insets.cutoutForCheck=new android.view.DisplayCutout(new Rect(0,cutoutTop,0,0),java.util.Collections.emptyList());
        root.windowInsetsForCheck=overlay.windowInsetsForCheck=insets;
        set(owner,"overlay",overlay);set(owner,"active",true);set(owner,"fraction",1f);set(owner,"barState",0);
        Constructor<?> header=Class.forName("dev.puitheme.NotificationBigClock$HeaderState").getDeclaredConstructor(View.class);header.setAccessible(true);
        ((Map)get(owner,"headers")).put(host,header.newInstance(new FrameLayout(context)));
        Stack stack=new Stack(context);host.addView(stack);stack.layout(0,0,400,1280);dimensions(root);
        stack.row=new ExpandableNotificationRow(context,160,0f,0);stack.addView(stack.row);dimensions(stack.row);
        owner.adjustTopPadding(stack,0f);
        NotificationCardTouchBoundary touch=new NotificationCardTouchBoundary(Stack.class,ExpandableNotificationRow.class);
        Bundle saved=new Bundle();saved.putBoolean(NotificationBigClockSettings.MASTER,true);saved.putBoolean(NotificationBigClockSettings.DATE_ENABLED,false);
        saved.putBoolean(NotificationBigClockSettings.GLASS,false);saved.putBoolean(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,false);
        saved.putString(NotificationBigClockSettings.FONT,"system");saved.putFloat(NotificationBigClockSettings.NOTIFICATION_EDGE_RANGE,24f);
        for(boolean locked:new boolean[]{false,true})for(boolean edgeEnabled:new boolean[]{false,true})for(float gap:new float[]{18f,-18.75f})for(float safe:new float[]{18f,-18.75f,-10000f}) {
            saved.putBoolean(NotificationBigClockSettings.REBOUND_LOCK,locked);saved.putFloat(NotificationBigClockSettings.NOTIFICATION_GAP,gap);
            saved.putBoolean(NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED,edgeEnabled);
            saved.putFloat(NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE,safe);
            Object current=settings(saved,NotificationBigClockSettings.PORTRAIT_PREFIX);set(owner,"settings",current);set(owner,"portraitSettings",current);set(overlay,"cachedFrame",null);
            method(type,"updateWidgets",boolean.class).invoke(overlay,false);
            float h=(Float)method(NotificationBigClock.class,"headerBottomInStack",View.class).invoke(owner,stack);
            float physical=(Float)get(overlay,"safeTop"),bottom=Math.max(physical,h+safe);
            check(physical>=Math.max(topInset,cutoutTop),"unconsumed status/cutout inset is protected");
            float hard=Math.max(physical,Math.min(h,h+Math.min(0f,gap)));
            if(edgeEnabled&&safe<0f)hard=Math.max(physical,Math.min(hard,h+safe-24f));
            stack.row.setTranslationY(Math.max(hard,bottom-12f));
            Canvas canvas=new Canvas();canvas.hardware=true;canvas.clipBounds=new Rect(0,0,400,1280);
            int[] draws={0};owner.drawNotifications(stack,canvas,false,c->{draws[0]++;return null;});
            check(draws[0]==1,"one native draw");near(hard,canvas.clipTop,"hard edge permits overlap but protects physical top");
            if(edgeEnabled&&bottom>hard) {
                Object edge=((Map)getStatic(NotificationClockEdge.class,"ENTRIES")).get(stack);
                near(bottom,(Float)get(edge,"maskBottom"),"edge ends at signed real header geometry");
                near(Math.max(hard,bottom-24f),(Float)get(edge,"maskTop"),"existing pipeline keeps nonzero negative fade band");
            }
            if(!edgeEnabled)check(canvas.paintFills==0,"edge switch stops masks without discarding signed geometry");
            check(owner.notificationTouch(host,60f,stack.row.getTranslationY()+20f,touch),"visible overlap retains native notification touch");
            check(!owner.notificationTouch(host,60f,physical-1f,touch),"physical unavailable area never claims notification touch");
            host.setTranslationY(-200f);
            Canvas moving=new Canvas();moving.clipBounds=new Rect(0,0,400,1280);
            owner.clipNotifications(stack,moving);
            check(moving.clipTop>=physical+200f,"physical boundary stays in window while panel translates");host.setTranslationY(0f);
            NotificationClockEdge.detach(stack);
        }
        method(type,"clearMaterials").invoke(overlay);ClockBackdropSource.clear();NotificationClockEdge.releaseResources();
    }
    @SuppressWarnings("unchecked")
    private static void pagingDraw(int topInset,int cutoutTop)throws Throwable {
        Context context=new Context();NotificationBigClock owner=new NotificationBigClock();
        FrameLayout root=new FrameLayout(context),host=new FrameLayout(context);root.addView(host);
        root.layout(0,0,400,1280);host.layout(0,0,400,1280);set(owner,"panel",new WeakReference<View>(host));
        Class<?> type=Class.forName("dev.puitheme.NotificationBigClock$ClockView");
        Constructor<?> ctor=type.getDeclaredConstructor(NotificationBigClock.class,Context.class);ctor.setAccessible(true);
        FrameLayout overlay=(FrameLayout)ctor.newInstance(owner,context);host.addView(overlay);overlay.layout(0,0,400,1280);
        android.view.WindowInsets insets=new android.view.WindowInsets();insets.topForCheck=topInset;
        insets.cutoutForCheck=new android.view.DisplayCutout(new Rect(0,cutoutTop,0,0),java.util.Collections.emptyList());
        root.windowInsetsForCheck=overlay.windowInsetsForCheck=insets;
        set(owner,"overlay",overlay);set(owner,"active",true);set(owner,"fraction",1f);set(owner,"barState",0);
        Constructor<?> header=Class.forName("dev.puitheme.NotificationBigClock$HeaderState").getDeclaredConstructor(View.class);header.setAccessible(true);
        ((Map)get(owner,"headers")).put(host,header.newInstance(new FrameLayout(context)));
        Stack stack=new Stack(context);stack.owner=owner;host.addView(stack);stack.layout(0,0,400,1280);dimensions(root);
        Bundle saved=new Bundle();saved.putBoolean(NotificationBigClockSettings.MASTER,true);saved.putBoolean(NotificationBigClockSettings.DATE_ENABLED,false);
        saved.putBoolean(NotificationBigClockSettings.GLASS,false);saved.putBoolean(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,false);
        saved.putString(NotificationBigClockSettings.FONT,"system");
        for(boolean locked:new boolean[]{false,true})for(boolean edgeEnabled:new boolean[]{false,true})
        for(float[] spacing:new float[][]{{-18.75f,18f},{18f,-18.75f},{-10000f,-10000f}}) {
            saved.putBoolean(NotificationBigClockSettings.REBOUND_LOCK,locked);saved.putBoolean(NotificationBigClockSettings.NOTIFICATION_EDGE_ENABLED,edgeEnabled);
            saved.putFloat(NotificationBigClockSettings.NOTIFICATION_GAP,spacing[0]);saved.putFloat(NotificationBigClockSettings.NOTIFICATION_EDGE_SAFE_DISTANCE,spacing[1]);
            Object current=settings(saved,NotificationBigClockSettings.PORTRAIT_PREFIX);set(owner,"settings",current);set(owner,"portraitSettings",current);set(overlay,"cachedFrame",null);
            method(type,"updateWidgets",boolean.class).invoke(overlay,false);
            for(int motion=0;motion<7;motion++)for(int scroll:new int[]{0,-8})for(float panelY:new float[]{0f,-200f})for(float existingTop:new float[]{0f,320f}) {
                stack.paging=motion==0;host.setTranslationX(motion==1?80f:0f);host.setScaleX(motion==2?.9f:1f);host.setScaleY(motion==3?.9f:1f);
                stack.setTranslationX(motion==4?80f:0f);stack.setScaleX(motion==5?.9f:1f);stack.setScaleY(motion==6?.9f:1f);
                host.setTranslationY(panelY);set(owner,"scrollY",scroll);
                float physical=(Float)method(NotificationBigClock.class,"physicalTopInStack",View.class).invoke(owner,stack);
                check(physical>0f,"paging physical boundary remains meaningful");
                PagingCanvas canvas=new PagingCanvas(existingTop);stack.nativeDraws=0;stack.nativeCanvas=null;stack.result=null;
                stack.dispatchDraw(canvas);
                near(Math.max(existingTop,physical),stack.nativeClipTop,"paging dispatchDraw protects physical top before native children");
                if(motion==0)near(Math.max(existingTop,Math.max((Float)get(overlay,"safeTop"),Math.max(topInset,cutoutTop))-panelY),
                        stack.nativeClipTop,"unscaled paging physical clip matches independent window inset and panel translation");
                check(stack.result==stack.marker&&stack.nativeDraws==1,"nested native paging draws exactly once and preserves result");
                check(stack.nativeCanvas==canvas,"paging keeps original native canvas");
                check(canvas.layers==0&&canvas.nodeDraws==0&&canvas.paintFills==0,"top paging never captures or masks the native stack");
                check(canvas.clipLeft<=Integer.MIN_VALUE&&canvas.clipRight>=Integer.MAX_VALUE,
                        "paging records only a vertical clip, never the RecordingCanvas cull width");
                check(canvas.getSaveCount()==1&&canvas.saves==canvas.restores,"nested paging restores all canvas saves");
                near(existingTop,canvas.activeTop,"physical clip never escapes the native invocation");
            }
        }
        stack.paging=true;stack.nativeFailure=new IllegalStateException("native paging draw failure");stack.nativeDraws=0;
        PagingCanvas failing=new PagingCanvas(0f);
        try {stack.dispatchDraw(failing);throw new AssertionError("native failure was swallowed");}
        catch(RuntimeException failure){check(failure==stack.nativeFailure,"native paging failure remains exact");}
        check(stack.nativeDraws==1&&failing.getSaveCount()==1&&failing.saves==failing.restores,"native failure still draws once and restores physical clip");
        near(0f,failing.activeTop,"native failure leaves caller canvas intact");
        stack.nativeFailure=null;set(owner,"barState",0);set(owner,"qsExpanded",false);
        Map headers=(Map)get(owner,"headers");Object retainedHeader=headers.get(host),retainedSettings=get(owner,"settings");
        for(int unavailable=0;unavailable<5;unavailable++) {
            if(unavailable==0)set(owner,"barState",1);
            if(unavailable==1)set(owner,"qsExpanded",true);
            if(unavailable==2){saved.putBoolean(NotificationBigClockSettings.MASTER,false);set(owner,"settings",settings(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));}
            if(unavailable==3)headers.clear();
            if(unavailable==4)set(owner,"panel",new WeakReference<View>(null));
            check(Float.isNaN((Float)method(NotificationBigClock.class,"headerBottomInStack",View.class).invoke(owner,stack)),"fixture has no available custom header");
            PagingCanvas nativeOnly=new PagingCanvas(0f);stack.nativeDraws=0;stack.dispatchDraw(nativeOnly);
            check(nativeOnly.saves==0&&nativeOnly.clips==0,"unavailable header preserves native paging without canvas changes");
            check(stack.nativeDraws==1&&stack.nativeCanvas==nativeOnly&&stack.result==stack.marker,"unavailable header still invokes original native children once");
            near(0f,stack.nativeClipTop,"unavailable header leaves physical clip to native owner");
            set(owner,"barState",0);set(owner,"qsExpanded",false);set(owner,"settings",retainedSettings);
            headers.put(host,retainedHeader);set(owner,"panel",new WeakReference<View>(host));
        }
        method(type,"clearMaterials").invoke(overlay);ClockBackdropSource.clear();NotificationClockEdge.releaseResources();
    }
    @SuppressWarnings("unchecked")
    private static void horizontalDisplayList()throws Throwable {
        Context context=new Context();NotificationBigClock owner=new NotificationBigClock();
        FrameLayout root=new FrameLayout(context),host=new FrameLayout(context);root.addView(host);
        root.layout(0,0,400,1280);host.layout(0,0,400,1280);set(owner,"panel",new WeakReference<View>(host));
        Class<?> type=Class.forName("dev.puitheme.NotificationBigClock$ClockView");
        Constructor<?> ctor=type.getDeclaredConstructor(NotificationBigClock.class,Context.class);ctor.setAccessible(true);
        FrameLayout overlay=(FrameLayout)ctor.newInstance(owner,context);host.addView(overlay);overlay.layout(0,0,400,1280);
        set(owner,"overlay",overlay);set(owner,"active",true);set(owner,"fraction",1f);set(owner,"barState",0);
        Constructor<?> header=Class.forName("dev.puitheme.NotificationBigClock$HeaderState").getDeclaredConstructor(View.class);header.setAccessible(true);
        ((Map)get(owner,"headers")).put(host,header.newInstance(new FrameLayout(context)));
        Bundle saved=new Bundle();saved.putBoolean(NotificationBigClockSettings.MASTER,true);
        saved.putBoolean(NotificationBigClockSettings.DATE_ENABLED,false);saved.putBoolean(NotificationBigClockSettings.GLASS,false);
        saved.putBoolean(NotificationBigClockSettings.ENTRY_EFFECT_ENABLED,false);saved.putString(NotificationBigClockSettings.FONT,"system");
        Object current=settings(saved,NotificationBigClockSettings.PORTRAIT_PREFIX);set(owner,"settings",current);set(owner,"portraitSettings",current);
        CachedStack stack=new CachedStack(context);stack.owner=owner;host.addView(stack);stack.layout(0,0,400,1280);dimensions(root);
        method(type,"updateWidgets",boolean.class).invoke(overlay,false);owner.adjustTopPadding(stack,0f);
        PageMotion motion=new PageMotion();owner.onPageMotionReady(motion);
        stack.composeFrame();int resting=stack.recordings;float oldTop=stack.nativeClipTop;
        float physical=(Float)method(NotificationBigClock.class,"physicalTopInStack",View.class).invoke(owner,stack);
        check(oldTop>physical,"resting display list contains the custom header edge");
        motion.running=true;host.setTranslationX(-400f);stack.setTranslationX(40f);stack.composeFrame();
        check(stack.recordings==resting,"native property-only paging reuses the parent's existing display list");
        near(oldTop,stack.nativeClipTop,"reused recording cannot switch to the native paging clip by itself");
        owner.onStatusIconHorizontalProgressChanged();stack.composeFrame();
        check(stack.recordings==resting+1,"horizontal callback dirties the current notification stack display list");
        near(physical,stack.nativeClipTop,"re-recording selects the paging physical edge instead of the stale header edge");
        host.setTranslationX(120f);stack.setTranslationX(-40f);owner.onStatusIconHorizontalProgressChanged();stack.composeFrame();
        check(stack.recordings==resting+2,"reversing an unfinished native spring records its current frame");
        motion.running=false;host.setTranslationX(0f);stack.setTranslationX(0f);owner.onStatusIconHorizontalProgressChanged();stack.composeFrame();
        check(stack.recordings==resting+3,"the settled callback also retires the paging display list");
        near(oldTop,stack.nativeClipTop,"settled drawing restores the custom header edge");
        Stack foreign=new Stack(context),detached=new Stack(context);new FrameLayout(context).addView(foreign);host.addView(detached);
        owner.adjustTopPadding(foreign,0f);owner.adjustTopPadding(detached,0f);detached.attached=false;
        int foreignBefore=foreign.invalidations,detachedBefore=detached.invalidations;
        owner.onStatusIconHorizontalProgressChanged();
        check(foreign.invalidations==foreignBefore&&detached.invalidations==detachedBefore,"foreign and detached stacks are never dirtied");
        int before=stack.invalidations;set(owner,"active",false);owner.onStatusIconHorizontalProgressChanged();
        check(stack.invalidations==before,"inactive custom clock leaves native display-list ownership unchanged");
        set(owner,"active",true);set(owner,"qsExpanded",true);owner.onStatusIconHorizontalProgressChanged();
        check(stack.invalidations==before,"the control-center scene does not dirty notification drawing");
        method(type,"clearMaterials").invoke(overlay);ClockBackdropSource.clear();NotificationClockEdge.releaseResources();
    }
    private static Object getStatic(Class<?> type,String name)throws Exception{return field(type,name).get(null);}
    public static void main(String[] args)throws Throwable {
        if(args.length==0||"settings".equals(args[0]))settingsChain();
        if(args.length==0||"model".equals(args[0]))model();
        if(args.length==0||"runtime".equals(args[0])){drawingAndTouch(0,0);drawingAndTouch(64,96);}
        if(args.length==0||"paging".equals(args[0])){pagingDraw(0,0);pagingDraw(64,96);}
        if(args.length==0||"cache".equals(args[0]))horizontalDisplayList();
        System.out.println("Notification negative spacing checks passed: "+checks);
    }
}
