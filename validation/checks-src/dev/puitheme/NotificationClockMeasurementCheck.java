package dev.puitheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Map;
import java.lang.ref.WeakReference;

/** Runs the actual ClockText constructor and typography/fit methods against the native
 * measured-Layout relayout precondition which caused the captured SystemUI crash. */
final class NotificationClockMeasurementCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Measured clock "+checks);}
    private static void set(Object owner,String name,Object value)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static Object get(Object owner,String name)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static Object instance(String name)throws Exception {
        Constructor<?> ctor=Class.forName(name).getDeclaredConstructor();ctor.setAccessible(true);return ctor.newInstance();
    }
    private static Method method(Class<?> type,String name,Class<?>...args)throws Exception {
        Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private static Object call(Method method,Object owner,Object...args)throws Exception {
        try{return method.invoke(owner,args);}catch(InvocationTargetException error){
            Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;throw error;
        }
    }
    static void run()throws Exception {
        Context context=new Context();int free=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        TextView oldProbe=new TextView(context);oldProbe.strictMeasuredRelayout=true;oldProbe.setText("15:14");oldProbe.measure(free,free);
        boolean reproduced=false;
        try{oldProbe.setText("15:15");}catch(NullPointerException crash){reproduced=crash.getMessage().contains("LayoutParams.width");}
        check(reproduced); // Proves the fixture catches the exact old second-update failure.

        NotificationBigClock parent=new NotificationBigClock();
        Class<?> clockText=Class.forName("dev.puitheme.NotificationBigClock$ClockText");
        Constructor<?> timeCtor=clockText.getDeclaredConstructor(NotificationBigClock.class,Context.class);timeCtor.setAccessible(true);
        TextView time=(TextView)timeCtor.newInstance(parent,context);
        check(time.getParent()==null);check(time.getLayoutParams() instanceof FrameLayout.LayoutParams);
        check(time.getLayoutParams().width==ViewGroup.LayoutParams.WRAP_CONTENT);
        check(time.getLayoutParams().height==ViewGroup.LayoutParams.WRAP_CONTENT);
        time.strictMeasuredRelayout=true;time.setText("15:14");time.measure(free,free);
        time.setText("15:15");time.setTextSize(0,180f);time.setTypeface(Typeface.create(Typeface.DEFAULT,824,false));time.measure(free,free);

        // Allocate just the ClockView method receiver; its real detached time probe and
        // label constructors are still executed. Native plugin inflation is out of scope.
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)singleton.get(null);
        Class<?> clockView=Class.forName("dev.puitheme.NotificationBigClock$ClockView");
        Object receiver=unsafe.allocateInstance(clockView);set(receiver,"this$0",parent);
        set(receiver,"typography",new WeakHashMap<TextView,Object>());set(receiver,"faces",new HashMap<Integer,Typeface>());
        Constructor<?> styleCtor=NotificationClockStyle.Style.class.getDeclaredConstructor(Context.class,Typeface.class);styleCtor.setAccessible(true);
        set(receiver,"nativeStyle",styleCtor.newInstance(context,Typeface.DEFAULT));
        TextView label=(TextView)call(method(clockView,"label",Context.class),receiver,context);
        check(label.getParent()==null);check(label.getLayoutParams()!=null);label.strictMeasuredRelayout=true;
        Method style=method(clockView,"textStyle",TextView.class,String.class,float.class,int.class,float.class,float.class);
        Method measure=method(clockView,"measureText",View.class);
        Method fit=method(clockView,"fit",TextView.class,String.class,float.class,int.class,float.class,float.class,float.class);
        for(int i=0;i<300;i++) {
            call(style,receiver,time,i%2==0?"15:16":"15:17",128f+i%5,i%2==0?450:824,0f,1f);
            call(measure,receiver,time);
            call(fit,receiver,label,i%2==0?"2026年10月3日 星期六":"自定义底部内容",32f+i%3,600,0f,300f,100f);
            check(time.getLayoutParams()!=null&&label.getLayoutParams()!=null);
            check(time.getParent()==null&&label.getParent()==null);
        }
        // Rotation selects another saved font/style while the already-measured widgets
        // survive. Exercise a real family change, not just the initial constructor.
        android.os.Bundle landscape=new android.os.Bundle();
        landscape.putString(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT),"system");
        Class<?> settings=Class.forName("dev.puitheme.NotificationBigClock$Settings");
        Constructor<?> settingsCtor=settings.getDeclaredConstructor(android.os.Bundle.class,String.class);settingsCtor.setAccessible(true);
        set(parent,"settings",settingsCtor.newInstance(landscape,NotificationBigClockSettings.LANDSCAPE_PREFIX));
        set(receiver,"faces",new HashMap<Integer,Typeface>());
        call(style,receiver,time,"16:01",225f,600,0f,1f);call(measure,receiver,time);
        check(time.getTextSize()==225f);check(time.getLayoutParams()!=null);
        call(style,receiver,label,"横屏底部内容",24f,400,0f,1f);call(measure,receiver,label);
        check(label.getText().toString().equals("横屏底部内容"));
        ViewGroup.LayoutParams existing=time.getLayoutParams();int requests=time.layoutRequests;
        NotificationBigClock.ensureOwnedWidgetLayout(time);check(time.getLayoutParams()==existing);check(time.layoutRequests==requests);
        // A lifecycle-reset null parameter cannot survive the actual style/measure boundary.
        time.setLayoutParams(null);call(style,receiver,time,"16:00",180f,703,0f,1f);call(measure,receiver,time);
        check(time.getLayoutParams()!=null);
        label.setLayoutParams(null);call(measure,receiver,label);label.setText("next native-layout update");check(label.getLayoutParams()!=null);

        View widget=(View)receiver;widget.attached=true;FrameLayout host=new FrameLayout(context);host.addView(widget);
        check(NotificationBigClock.widgetHostReady(widget,widget,host,true));
        check(!NotificationBigClock.widgetHostReady(widget,new View(context),host,true));
        check(!NotificationBigClock.widgetHostReady(widget,widget,host,false));
        widget.attached=false;check(!NotificationBigClock.widgetHostReady(widget,widget,host,true));widget.attached=true;
        host.attached=false;check(!NotificationBigClock.widgetHostReady(widget,widget,host,true));host.attached=true;
        widget.setVisibility(View.GONE);check(!NotificationBigClock.widgetHostReady(widget,widget,host,true));widget.setVisibility(View.VISIBLE);
        host.removeView(widget);new FrameLayout(context).addView(widget);check(!NotificationBigClock.widgetHostReady(widget,widget,host,true));
        typographyRecovery(receiver,clockView,time,style,measure);
        expandedBasis(parent,receiver,clockView,time,settingsCtor,context);
        heightSelections(parent,receiver,clockView,time,settingsCtor);
        scrollSession(context);
        System.out.println("Notification measured clock checks passed: "+checks);
    }

    private static void typographyRecovery(Object receiver,Class<?> clockView,TextView time,
            Method style,Method measure)throws Exception {
        call(style,receiver,time,"16:00",180f,600,0f,1f);call(measure,receiver,time);
        Typeface expected=time.getTypeface();int requests=time.layoutRequests;
        for(int i=0;i<1000;i++)call(style,receiver,time,"16:00",180f,600,0f,1f);
        check(time.layoutRequests==requests); // Stable frames keep the existing measured Layout.
        time.setTextSize(0,44f);time.measure(0,0);
        call(style,receiver,time,"16:00",180f,600,0f,1f);call(measure,receiver,time);
        check(time.getTextSize()==180f&&time.getMeasuredHeight()==180);
        time.setTypeface(new Typeface());time.setLetterSpacing(.25f);time.setFontFeatureSettings("kern");
        call(style,receiver,time,"16:00",180f,600,0f,1f);call(measure,receiver,time);
        check(time.getTypeface()==expected);check(time.getLetterSpacing()==0f);
        check("tnum".equals(time.getFontFeatureSettings()));
        time.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        time.setTextSize(0,32f);
        call(style,receiver,time,"16:00",180f,600,0f,1f);call(measure,receiver,time);
        check(time.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_NONE);check(time.getTextSize()==180f);
    }

    private static final class AnimatedHost extends FrameLayout {
        int width=1000,height=2000;
        AnimatedHost(Context context){super(context);getResources().getDisplayMetrics().density=1f;
            getResources().getDisplayMetrics().widthPixels=1000;getResources().getDisplayMetrics().heightPixels=2000;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
    }
    private static void expandedBasis(NotificationBigClock parent,Object receiver,Class<?> clockView,
            TextView time,Constructor<?> settingsCtor,Context context)throws Exception {
        AnimatedHost host=new AnimatedHost(context);set(parent,"panel",new WeakReference<View>(host));
        android.os.Bundle saved=new android.os.Bundle();
        saved.putString(NotificationBigClockSettings.FONT,"system");
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        set(receiver,"portraitBasis",instance("dev.puitheme.NotificationBigClock$ExpandedBasis"));
        set(receiver,"timeProbe",time);set(receiver,"time","16:00");set(receiver,"heightFont",null);
        Method expanded=method(clockView,"expandedTimeSize",boolean.class,float.class,float.class,
                int.class,int.class,float.class,boolean.class);
        float basis=(Float)call(expanded,receiver,false,180f,0f,1000,2000,952f,true);
        check(basis==180f);
        // Realistic quick-pull frames remain positive, but do not describe the font's viewport.
        for(int h:new int[]{1,40,180,500,1400,2000}) {
            host.width=Math.max(1,h/2);host.height=h;
            check(NotificationBigClock.clockMeasurementDimension(host,false,true)==1000);
            check(NotificationBigClock.clockMeasurementDimension(host,false,false)==2000);
            check((Float)call(expanded,receiver,false,180f,0f,1000,2000,952f,true)==basis);
        }
        int requests=time.layoutRequests;
        set(receiver,"time","16:01");
        for(int i=0;i<50;i++)check((Float)call(expanded,receiver,false,180f,0f,1000,2000,952f,true)==basis);
        check(time.layoutRequests==requests); // A minute refresh cannot refit compact ink into the expanded basis.
        check((Float)call(expanded,receiver,false,180f,0f,1,20,1f,false)==basis);
        check(NotificationBigClock.clockMeasurementDimension(host,true,true)==2000);
        check(NotificationBigClock.clockMeasurementDimension(host,true,false)==1000);
        check((Float)call(expanded,receiver,true,225f,0f,20,10,1f,true)==225f);
        check((Float)call(expanded,receiver,false,180f,0f,1000,2000,952f,true)==basis);
        host.getResources().getDisplayMetrics().heightPixels=0;
        check(NotificationBigClock.clockMeasurementDimension(host,false,false)==0);
        set(receiver,"portraitBasis",instance("dev.puitheme.NotificationBigClock$ExpandedBasis"));
        check((Float)call(expanded,receiver,false,180f,0f,1,20,1f,false)==180f);
        check(get(get(receiver,"portraitBasis"),"settings")!=null); // Direct height no longer depends on provisional geometry.
        host.getResources().getDisplayMetrics().heightPixels=2000;
        check((Float)call(expanded,receiver,false,180f,0f,1000,2000,952f,true)==180f);
        saved.putFloat(NotificationBigClockSettings.MAX_SIZE,220f);
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        check((Float)call(expanded,receiver,false,220f,0f,1000,2000,952f,true)==220f);
        // Window constraints align the clock; they cannot silently change its selected height.
        float small=(Float)call(expanded,receiver,false,220f,0f,1000,200,952f,true);
        check(small==220f);
        check((Float)call(expanded,receiver,false,220f,0f,1000,2000,952f,true)==220f);
        nativeHeightRestore(parent,receiver,clockView,time,settingsCtor);
    }

    @SuppressWarnings("unchecked")
    private static void heightSelections(NotificationBigClock parent,Object receiver,Class<?> clockView,
            TextView time,Constructor<?> settingsCtor)throws Exception {
        TextView.measureTextWidth=true;
        check(NotificationBigClockSettings.nativeHeightFont("native"));
        for(Object mode:new Object[]{"system","pingfang","custom","global",null})
            check(!NotificationBigClockSettings.nativeHeightFont(mode));
        Class<?> metadata=Class.forName("dev.puitheme.NotificationClockFont$Metadata");
        Constructor<?> metadataCtor=metadata.getDeclaredConstructor(String.class,int.class,int.class);metadataCtor.setAccessible(true);
        Class<?> range=Class.forName("dev.puitheme.NotificationClockFont$WeightRange");
        Constructor<?> rangeCtor=range.getDeclaredConstructor(int.class,int.class);rangeCtor.setAccessible(true);
        Constructor<?> resolverCtor=NotificationClockFont.Resolver.class.getDeclaredConstructor(
                android.content.res.AssetManager.class,String.class,metadata,boolean.class,range);resolverCtor.setAccessible(true);
        NotificationClockFont.Resolver resolver=(NotificationClockFont.Resolver)resolverCtor.newInstance(null,
                "fixture-native-HGHT.ttf",metadataCtor.newInstance("fixture-native-HGHT.ttf",70,600),false,rangeCtor.newInstance(1,1000));
        // Real Resolver and real ClockText measurement; only the font rasterizer is a
        // fixture. Its fixed base exposes the old incorrect percentage assumption.
        set(resolver,"faces",new HashMap<Integer,Typeface>() {
            @Override public Typeface get(Object key) {
                Typeface face=super.get(key);if(face!=null)return face;
                int value=(Integer)key;face=new Typeface();face.weight=value/1001;
                face.variationHeight=value%1001;put((Integer)key,face);return face;
            }
        });
        set(receiver,"heightFont",resolver);set(receiver,"time","16:00");
        set(receiver,"portraitBasis",instance("dev.puitheme.NotificationBigClock$ExpandedBasis"));
        Method expanded=method(clockView,"expandedTimeSize",boolean.class,float.class,float.class,
                int.class,int.class,float.class,boolean.class);
        Method style=method(clockView,"textStyle",TextView.class,String.class,float.class,int.class,float.class,float.class);
        Method measure=method(clockView,"measureText",View.class);
        android.os.Bundle saved=new android.os.Bundle();saved.putFloat(NotificationBigClockSettings.MAX_SIZE,252f);
        saved.putFloat(NotificationBigClockSettings.COMPACT_MAX_SIZE,64f);
        saved.putFloat(NotificationBigClockSettings.SCALE,250f);saved.putFloat(NotificationBigClockSettings.COMPACT_SCALE,95f);
        saved.putFloat(NotificationBigClockSettings.COMPACT_WEIGHT,300f);
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        float size=(Float)call(expanded,receiver,false,252f,0f,1000,2000,540f,true);
        float axis=(Float)get(receiver,"expandedAxisRatio"),compact=(Float)get(receiver,"compactAxisRatio");
        check(size==180f);check((Boolean)get(receiver,"measuredNativeHeight"));
        call(style,receiver,time,"16:00",size,600,0f,axis);call(measure,receiver,time);
        check(Math.abs(time.getMeasuredHeight()-252)<=1);check(time.getTypeface().getWeight()==600);
        call(style,receiver,time,"16:00",size,600,0f,axis*compact);call(measure,receiver,time);
        check(Math.abs(time.getMeasuredHeight()-64)<=1);check(time.getTextSize()==size);
        check(time.getTypeface().getWeight()==600);
        int writes=time.typefaceWrites,requests=time.layoutRequests;
        set(receiver,"time","16:01");
        for(int i=0;i<1000;i++)check((Float)call(expanded,receiver,false,252f,0f,1,20,1f,false)==size);
        check(time.typefaceWrites==writes&&time.layoutRequests==requests);
        check((Float)get(receiver,"expandedAxisRatio")==axis&&(Float)get(receiver,"compactAxisRatio")==compact);
        // A compact target is independent and may be taller than expanded.
        saved.putFloat(NotificationBigClockSettings.COMPACT_MAX_SIZE,320f);
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        size=(Float)call(expanded,receiver,false,252f,0f,1000,2000,540f,true);
        compact=(Float)get(receiver,"compactAxisRatio");check(compact>1f);
        call(style,receiver,time,"16:01",size,600,0f,(Float)get(receiver,"expandedAxisRatio")*compact);
        call(measure,receiver,time);check(Math.abs(time.getMeasuredHeight()-320)<=1);
        // Same native family in landscape: both endpoints keep the selected height.
        call(expanded,receiver,true,252f,0f,10,10,1f,true);
        check((Float)get(receiver,"compactAxisRatio")==1f);
        check((Float)get(get(parent,"settings"),"compactWeight")==600f);
        // Left/right screen padding determines one native em width. Panel and list
        // gestures then change only HGHT, keeping that width and font weight stable.
        saved.putFloat(NotificationBigClockSettings.COMPACT_MAX_SIZE,64f);
        saved.putFloat(NotificationBigClockSettings.SCREEN_PADDING,50f);
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        set(receiver,"safeLeft",10f);set(receiver,"safeRight",20f);
        Method padding=method(clockView,"portraitSidePadding",int.class);
        check((Float)call(padding,receiver,1030)==50f);
        size=(Float)call(expanded,receiver,false,252f,0f,1030,2000,900f,true);
        check(Math.abs(size-300f)<.01f);
        axis=(Float)get(receiver,"expandedAxisRatio");compact=(Float)get(receiver,"compactAxisRatio");
        float lastHeight=0f;int fixedWidth=-1;
        for(int i=0;i<=20;i++) {
            float panel=i/20f,progress=NotificationBigClockModel.nativeHeightProgress(0f,panel);
            float ratio=axis*(1f+(compact-1f)*progress);
            call(style,receiver,time,"16:01",size,600,0f,ratio);call(measure,receiver,time);
            if(fixedWidth<0)fixedWidth=time.getMeasuredWidth();
            check(time.getMeasuredWidth()==fixedWidth&&Math.abs(fixedWidth-900)<=1);check(time.getTextSize()==size);check(time.getTypeface().getWeight()==600);
            check(time.getMeasuredHeight()>=lastHeight);lastHeight=time.getMeasuredHeight();
        }
        check(Math.abs(lastHeight-252)<=1);
        float scrollProgress=NotificationBigClockModel.nativeHeightProgress(1f,1f);
        call(style,receiver,time,"16:01",size,600,0f,axis*(1f+(compact-1f)*scrollProgress));call(measure,receiver,time);
        check(time.getMeasuredWidth()==fixedWidth&&Math.abs(time.getMeasuredHeight()-64)<=1);
        // A genuine width change recalibrates once, unlike a narrow animated panel.
        check(Math.abs((Float)call(expanded,receiver,false,252f,0f,1030,2000,600f,true)-200f)<.01f);
        // Every non-native family has one unchanged size and one unchanged face.
        for(String font:new String[]{"system","pingfang","custom","global"}) {
            saved.putString(NotificationBigClockSettings.FONT,font);
            set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
            size=(Float)call(expanded,receiver,false,252f,0f,1,20,1f,false);
            check(size==252f);check(!(Boolean)get(receiver,"measuredNativeHeight"));
            check((Float)get(receiver,"expandedAxisRatio")==1f&&(Float)get(receiver,"compactAxisRatio")==1f);
        }
        set(receiver,"heightFont",null);
        saved.putString(NotificationBigClockSettings.FONT,"native");
        set(parent,"settings",settingsCtor.newInstance(saved,NotificationBigClockSettings.PORTRAIT_PREFIX));
        check((Float)call(expanded,receiver,false,252f,0f,1000,2000,952f,true)==252f);
        check(!(Boolean)get(receiver,"measuredNativeHeight"));check((Float)get(receiver,"compactAxisRatio")==1f);
        TextView.measureTextWidth=false;
    }

    @SuppressWarnings("unchecked")
    private static void nativeHeightRestore(NotificationBigClock parent,Object receiver,Class<?> clockView,
            TextView time,Constructor<?> settingsCtor)throws Exception {
        set(parent,"settings",settingsCtor.newInstance(new android.os.Bundle(),NotificationBigClockSettings.PORTRAIT_PREFIX));
        Class<?> metadata=Class.forName("dev.puitheme.NotificationClockFont$Metadata");
        Constructor<?> metadataCtor=metadata.getDeclaredConstructor(String.class,int.class,int.class);metadataCtor.setAccessible(true);
        Class<?> range=Class.forName("dev.puitheme.NotificationClockFont$WeightRange");
        Constructor<?> rangeCtor=range.getDeclaredConstructor(int.class,int.class);rangeCtor.setAccessible(true);
        Constructor<?> resolverCtor=NotificationClockFont.Resolver.class.getDeclaredConstructor(
                android.content.res.AssetManager.class,String.class,metadata,boolean.class,range);resolverCtor.setAccessible(true);
        NotificationClockFont.Resolver resolver=(NotificationClockFont.Resolver)resolverCtor.newInstance(null,
                "fixture-native-HGHT.ttf",metadataCtor.newInstance("fixture-native-HGHT.ttf",70,600),false,rangeCtor.newInstance(1,1000));
        Typeface full=new Typeface(),compact=new Typeface();
        Map<Integer,Typeface> faces=(Map<Integer,Typeface>)get(resolver,"faces");
        faces.put(600*1001+70,full);faces.put(600*1001+25,compact);
        set(receiver,"heightFont",resolver);
        Method style=method(clockView,"textStyle",TextView.class,String.class,float.class,int.class,float.class,float.class);
        Method measure=method(clockView,"measureText",View.class);
        for(int i=0;i<20;i++) {
            call(style,receiver,time,"16:01",180f,600,0f,1f);call(measure,receiver,time);
            check(time.getTypeface()==full&&time.getTextSize()==180f);
            call(style,receiver,time,"16:01",180f,600,0f,.36f);call(measure,receiver,time);
            check(time.getTypeface()==compact);check(resolver.baseHeight==70);
            call(style,receiver,time,"16:01",180f,600,0f,1f);call(measure,receiver,time);
            check(time.getTypeface()==full&&time.getMeasuredHeight()==180);
            // Same requested full style, but the live view was changed by another native writer.
            // An input-only cache skipped this restoration; actual style identity must be checked.
            time.setTypeface(compact);time.setTextSize(0,64f);call(measure,receiver,time);
            check(time.getMeasuredHeight()==64);
            call(style,receiver,time,"16:01",180f,600,0f,1f);call(measure,receiver,time);
            check(time.getTypeface()==full&&time.getMeasuredHeight()==180);
        }
        set(receiver,"heightFont",null);
    }

    private static void scrollSession(Context context)throws Exception {
        NotificationBigClock parent=new NotificationBigClock();
        FrameLayout host=new FrameLayout(context),own=new FrameLayout(context),foreign=new FrameLayout(context);
        host.addView(own);set(parent,"panel",new WeakReference<View>(host));
        parent.onPanelMotionState(true,false);parent.onScrollChanged(own,300,20f);
        check((Integer)get(parent,"scrollY")==300);check((Float)get(parent,"overDistance")==20f);
        parent.onScrollChanged(foreign,900,99f);
        check((Integer)get(parent,"scrollY")==300);check((Float)get(parent,"overDistance")==20f);
        parent.onPanelMotionState(true,false);parent.onPanelMotionState(false,false);
        check((Integer)get(parent,"scrollY")==300); // Interrupted closing still uses the actual list scroll.
        FrameLayout qs=new FrameLayout(context);
        parent.onSeparateQsStatusChanged(qs,null,null,null,1f,0,false);
        parent.onPanelMotionState(false,true);check((Integer)get(parent,"scrollY")==300);
        parent.onSeparateQsStatusChanged(qs,null,null,null,0f,0,true);
        check((Integer)get(parent,"scrollY")==0);check((Float)get(parent,"overDistance")==0f);
        parent.onScrollChanged(own,300,20f);check((Integer)get(parent,"scrollY")==0);
        parent.onPanelMotionState(true,false);parent.onScrollChanged(own,0,0f);
        check((Integer)get(parent,"scrollY")==0);
        parent.onScrollChanged(own,300,0f);
        NotificationBigClockModel.Frame compact=NotificationBigClockModel.measured(2000,1,24,180,64,24,8,8,0,0,0,600,600,(Integer)get(parent,"scrollY"),0,1);
        check(compact.progress>0f);check(compact.clockHeight<180f);
        parent.onScrollChanged(own,0,0f);
        NotificationBigClockModel.Frame full=NotificationBigClockModel.measured(2000,1,24,180,64,24,8,8,0,0,0,600,600,(Integer)get(parent,"scrollY"),0,1);
        check(full.progress==0f&&full.clockHeight==180f&&full.textSizeRatio==1f);
        own.attached=false;parent.onScrollChanged(own,300,0f);check((Integer)get(parent,"scrollY")==0);
        check(!NotificationBigClock.clockStackUnder(foreign,host));
        check(!NotificationBigClock.clockStackUnder(own,host));
    }
}
