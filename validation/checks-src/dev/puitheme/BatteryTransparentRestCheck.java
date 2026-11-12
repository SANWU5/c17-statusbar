package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/** App289: a battery that is fully transparent through its parents does not run the charge animation (ColorOS fades
 * the status bar and panel headers to alpha 0 instead of hiding them). While one is charging and transparent a
 * re-check runs every second without drawing; it redraws once when it shows again. */
public final class BatteryTransparentRestCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static BatteryControlsCheck.Meter meter(Context context,FrameLayout parent){
        BatteryControlsCheck.Meter owner=new BatteryControlsCheck.Meter(context);
        BatteryControlsCheck.Horizontal drawable=new BatteryControlsCheck.Horizontal();
        drawable.chargeIconId=8;drawable.setCallback(owner);owner.drawable=drawable;owner.charge.iconId=8;
        parent.addView(owner);return owner;
    }
    public static void main(String[] args)throws Exception{
        android.provider.Settings.System.values.put("display_power_percent",1);
        SystemClock.uptime=100;
        Handler handler=new Handler(Looper.getMainLooper());
        BatteryControls controls=new BatteryControls(handler,BatteryControlsCheck.Meter.class,BatteryControlsCheck.Horizontal.class,BatteryControlsCheck.Charge.class);
        Context context=new Context();
        FrameLayout bar=new FrameLayout(context),header=new FrameLayout(context);
        BatteryControlsCheck.Meter shown=meter(context,bar),faded=meter(context,header);
        controls.sync(shown,shown.charge,null);controls.sync(faded,faded.charge,null);
        Bundle settings=new Bundle();settings.putBoolean("battery_enabled",true);settings.putBoolean(BatteryControls.HIDE_CHARGE,false);
        settings.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,true);settings.putBoolean("battery_color_enabled",false);
        settings.putBoolean(BatteryTextStyle.MASTER,false);settings.putFloat(BatteryTextStyle.SIZE,14);
        controls.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());

        Field ownersField=BatteryControls.class.getDeclaredField("owners");ownersField.setAccessible(true);
        Map<?,?> owners=(Map<?,?>)ownersField.get(controls);
        Method can=null;for(Method m:BatteryControls.class.getDeclaredMethods())if(m.getName().equals("canAnimate"))can=m;
        can.setAccessible(true);final Method canAnimate=can;
        Field tickField=BatteryControls.class.getDeclaredField("tick");tickField.setAccessible(true);
        Runnable tick=(Runnable)tickField.get(controls);
        java.util.function.Function<BatteryControlsCheck.Meter,Boolean> animates=view->{
            try{return (Boolean)canAnimate.invoke(controls,owners.get(view));}catch(ReflectiveOperationException e){throw new AssertionError(e);}};

        check(animates.apply(shown)&&animates.apply(faded),"both visible charging batteries animate");
        check(handler.delayed==tick&&handler.delay!=BatteryControls.HIDDEN_RECHECK_MS,"visible batteries wake at their own phase, not the re-check");
        SystemClock.uptime=500;tick.run();
        header.setAlpha(0f);
        check(!animates.apply(faded)&&animates.apply(shown),"a battery inside a transparent parent rests; the visible one keeps animating");
        bar.setAlpha(0f);SystemClock.uptime=700;
        int shownBefore=shown.invalidations,fadedBefore=faded.invalidations;
        tick.run();
        check(shown.invalidations==shownBefore&&faded.invalidations==fadedBefore,"transparent batteries are not redrawn");
        check(handler.delayed==tick&&handler.delay==BatteryControls.HIDDEN_RECHECK_MS,"a hidden charging battery is re-checked once a second");
        SystemClock.uptime=1700;tick.run();
        check(shown.invalidations==shownBefore&&faded.invalidations==fadedBefore&&handler.delay==BatteryControls.HIDDEN_RECHECK_MS,
                "re-checks while still hidden draw nothing");
        header.setAlpha(1f);SystemClock.uptime=2700;tick.run();
        check(faded.invalidations==fadedBefore+1,"a battery that shows again redraws once even in its hold phase");
        check(handler.delayed==tick&&handler.delay==400,"the next wake-up is the visible battery's own fade, sooner than the re-check");
        SystemClock.uptime=2800;tick.run();
        check(faded.invalidations==fadedBefore+1,"a re-check does not redraw a visible battery before its own deadline");
        bar.setAlpha(1f);shown.setTransitionAlpha(.005f);SystemClock.uptime=2900;tick.run();
        check(!animates.apply(shown),"transition alpha counts as fading out");
        shown.setTransitionAlpha(1f);
        SystemClock.uptime=3100;tick.run();
        check(animates.apply(shown)&&shown.invalidations>shownBefore,"the status bar battery resumes when it fades back in");
        check(handler.delay!=BatteryControls.HIDDEN_RECHECK_MS,"with every battery visible the re-check stops");
        settings.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,false);
        controls.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        header.setAlpha(0f);controls.visibilityChanged(faded);
        check(handler.delayed==null,"no charge cycle, no re-check");
        System.out.println("BatteryTransparentRestCheck: "+checks+" checks passed");
    }
}
