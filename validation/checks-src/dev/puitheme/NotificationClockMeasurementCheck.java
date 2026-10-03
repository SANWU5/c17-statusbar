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

/** Runs the actual ClockText constructor and typography/fit methods against the native
 * measured-Layout relayout precondition which caused the captured SystemUI crash. */
final class NotificationClockMeasurementCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Measured clock "+checks);}
    private static void set(Object owner,String name,Object value)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
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
        System.out.println("Notification measured clock checks passed: "+checks);
    }
}
