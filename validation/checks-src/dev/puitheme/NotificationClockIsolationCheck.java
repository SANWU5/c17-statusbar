package dev.puitheme;
import android.os.Bundle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
/** Portrait and landscape never enable, rewrite, or borrow one another's saved typography. */
public final class NotificationClockIsolationCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Clock isolation "+checks);}
    private static Object read(Object settings,String name)throws Exception {Field f=settings.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(settings);}
    public static void main(String[] args)throws Exception {
        check(!NotificationBigClockSettings.enabled(null,false));check(!NotificationBigClockSettings.enabled(null,true));
        Bundle b=new Bundle();b.putBoolean(NotificationBigClockSettings.MASTER,true);b.putFloat(NotificationBigClockSettings.WEIGHT,824f);
        b.putString(NotificationBigClockSettings.FONT,"system");b.putFloat(NotificationBigClockSettings.MAX_SIZE,164f);
        check(NotificationBigClockSettings.enabled(b,false));check(!NotificationBigClockSettings.enabled(b,true));
        Class<?> type=Class.forName("dev.puitheme.NotificationBigClock$Settings");Constructor<?> ctor=type.getDeclaredConstructor(Bundle.class,String.class);ctor.setAccessible(true);
        Object portrait=ctor.newInstance(b,NotificationBigClockSettings.PORTRAIT_PREFIX);
        Object landscape=ctor.newInstance(b,NotificationBigClockSettings.LANDSCAPE_PREFIX);
        check((Boolean)read(portrait,"enabled"));check(!(Boolean)read(landscape,"enabled"));
        check((Float)read(portrait,"weight")==824f);check((Float)read(landscape,"weight")==600f);
        check("system".equals(read(portrait,"font")));check("native".equals(read(landscape,"font")));
        b.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);b.putFloat(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.WEIGHT),450f);
        landscape=ctor.newInstance(b,NotificationBigClockSettings.LANDSCAPE_PREFIX);check((Boolean)read(landscape,"enabled"));check((Float)read(landscape,"weight")==450f);
        check((Float)b.get(NotificationBigClockSettings.WEIGHT)==824f);
        b.putFloat(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.MAX_SIZE),180f);
        b.putFloat(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.SCALE),125f);
        b.putFloat(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.OFFSET_Y),-19.26f);
        b.putBoolean(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH_ENABLED,true);
        b.putFloat(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH,200f);
        landscape=ctor.newInstance(b,NotificationBigClockSettings.LANDSCAPE_PREFIX);
        check((Float)read(landscape,"maxSize")==180f);check((Float)read(landscape,"scale")==125f);
        check((Float)read(landscape,"notificationWidth")==200f);check((Float)read(landscape,"offsetY")==-19.26f);
        check((Float)b.get(NotificationBigClockSettings.MAX_SIZE)==164f);
        // Native space/clamping never rewrites a user's time size, position, or input trial.
        NotificationLandscapeLayout.width(1800,1440,(Float)read(landscape,"notificationWidth"));
        check((Float)b.get(NotificationBigClockSettings.LANDSCAPE_NOTIFICATION_WIDTH)==200f);
        check((Float)read(landscape,"maxSize")==180f);check((Float)read(landscape,"scale")==125f);
        for(Map.Entry<String,Float> item:NotificationBigClockSettings.NUMBERS.entrySet())if(!item.getKey().startsWith(NotificationBigClockSettings.LANDSCAPE_PREFIX)
                &&!item.getKey().equals(NotificationBigClockSettings.STACK_GAP)&&!item.getKey().equals(NotificationBigClockSettings.STACK_INSET))
            check(item.getValue().equals(NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.landscapeKey(item.getKey()))));
        check(NotificationBigClockSettings.positiveSize(NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.MAX_SIZE)));
        Class<?> cache=Class.forName("dev.puitheme.NotificationBigClock$Typography");Constructor<?> cacheCtor=cache.getDeclaredConstructor();cacheCtor.setAccessible(true);
        Object typography=cacheCtor.newInstance();java.lang.reflect.Method record=cache.getDeclaredMethod("record",Object.class,Object.class,String.class,float.class,int.class,float.class,float.class);
        java.lang.reflect.Method matches=cache.getDeclaredMethod("matches",Object.class,Object.class,String.class,float.class,int.class,float.class,float.class);record.setAccessible(true);matches.setAccessible(true);
        Object style=new Object();record.invoke(typography,portrait,style,"21:17",128f,824,0f,1f);
        for(int i=0;i<10000;i++)check((Boolean)matches.invoke(typography,portrait,style,"21:17",128f,824,0f,1f));
        check(!(Boolean)matches.invoke(typography,landscape,style,"21:17",128f,824,0f,1f));
        check(!(Boolean)matches.invoke(typography,portrait,style,"21:18",128f,824,0f,1f));
        check(!(Boolean)matches.invoke(typography,portrait,style,"21:17",127f,824,0f,1f));
        NotificationClockMeasurementCheck.run();
        System.out.println("Notification clock isolation checks passed: "+checks);
    }
}
