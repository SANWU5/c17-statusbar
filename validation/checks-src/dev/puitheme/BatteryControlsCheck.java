package dev.puitheme;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

/** Real compiled controller against native field/method shapes and recording drawing stubs. */
public final class BatteryControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static class Charge {
        public boolean isVisible=true;
        public int iconId=1;
    }
    public static class Bar extends Drawable {
        public int outlineColor=0xccffffff,progressColor=0xff00bd13,backgroundColor=0x4dffffff,level=85,chargeIconId;
        public void draw(Canvas canvas) { }
        public void setAlpha(int alpha) { }
        public void setColorFilter(ColorFilter filter) { }
        public int getOpacity() {return 0;}
        public int getBatteryLevel() {return level;}
        public int getChargeIconId() {return chargeIconId;}
        public void setColors(int progress,int background,int outline) {progressColor=progress;backgroundColor=background;outlineColor=outline;}
        public void updatePaintXfermode(Paint paint) {paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));paint.setColor(0xccffffff);}
    }
    public static class Horizontal extends Bar {
        public boolean isShowPercentIn=true;
        private final Paint percentInPaint=new Paint(1);
        public RectF getBodyRectForLayout(RectF content) {return new RectF(0,0,46,20);}
    }
    public static class Meter extends View {
        public Drawable drawable;
        public Charge charge=new Charge();
        public Meter(Context context) {super(context);}
        public Drawable getBatteryStyleDrawable() {return drawable;}
        public Charge getBatteryCharge() {return charge;}
    }
    public static void main(String[] args) throws Exception {
        Handler handler=new Handler(Looper.getMainLooper());
        BatteryControls controller=new BatteryControls(handler,Meter.class,Horizontal.class,Charge.class);
        Meter meter=new Meter(new Context());Horizontal battery=new Horizontal();meter.drawable=battery;battery.setCallback(meter);
        View outside=new View(meter.getContext());
        controller.sync(meter,meter.charge,outside);
        equal(View.GONE,outside.getVisibility());equal(true,handler.delayed!=null);equal(3000L,handler.delay);
        RectF rect=new RectF(0,0,50,20);Canvas canvas=new Canvas();
        equal(true,controller.drawContent(battery,canvas,rect));equal(204,canvas.pathAlpha);equal(-1,canvas.textAlpha);equal(canvas.saves,canvas.restores);
        equal(255,canvas.maskAlpha);equal(Paint.Style.FILL_AND_STROKE,canvas.maskStyle);equal(true,canvas.maskStroke>0);equal(PorterDuff.Mode.SRC_OVER,canvas.foregroundMode);equal(0,canvas.clips);
        Object[] colors=controller.nativeColors(battery,0xff00bd13,0x4d000000,0xe6000000);
        battery.setColors((Integer)colors[0],(Integer)colors[1],(Integer)colors[2]);canvas=new Canvas();controller.drawContent(battery,canvas,rect);
        equal(230,canvas.pathAlpha);equal(0xe6000000,canvas.pathColor);equal(255,canvas.maskAlpha);
        colors=controller.nativeColors(battery,0xff00bd13,0x4dffffff,0xccffffff);
        battery.setColors((Integer)colors[0],(Integer)colors[1],(Integer)colors[2]);canvas=new Canvas();controller.drawContent(battery,canvas,rect);
        equal(204,canvas.pathAlpha);equal(0xccffffff,canvas.pathColor);equal(255,canvas.maskAlpha);
        SystemClock.uptime=3600;handler.delayed.run();canvas=new Canvas();controller.drawContent(battery,canvas,rect);
        equal(102,canvas.pathAlpha);equal(102,canvas.textAlpha);equal("85",canvas.text);equal(33L,handler.delay);
        equal(128,canvas.maskAlpha);equal(1,canvas.clips);equal(canvas.saves,canvas.restores);
        SystemClock.uptime=4100;handler.delayed.run();canvas=new Canvas();controller.drawContent(battery,canvas,rect);
        equal(-1,canvas.pathAlpha);equal(204,canvas.textAlpha);
        equal(-1,canvas.maskAlpha);equal(canvas.saves,canvas.restores);
        controller.configure(false,3,1);equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);equal(false,controller.drawContent(battery,new Canvas(),rect));
        controller.configure(true,3,1);equal(View.GONE,outside.getVisibility());equal(true,handler.delayed!=null);
        meter.shown=false;controller.visibilityChanged(meter);equal(true,handler.delayed==null);
        meter.shown=true;controller.visibilityChanged(meter);equal(true,handler.delayed!=null);
        meter.windowVisibility=View.GONE;controller.visibilityChanged(meter);equal(true,handler.delayed==null);
        meter.windowVisibility=View.VISIBLE;controller.visibilityChanged(meter);equal(true,handler.delayed!=null);
        controller.setInteractive(false);equal(true,handler.delayed==null);canvas=new Canvas();controller.drawContent(battery,canvas,rect);equal(204,canvas.pathAlpha);equal(-1,canvas.textAlpha);
        controller.setInteractive(true);equal(true,handler.delayed!=null);
        meter.charge.isVisible=false;controller.sync(meter);equal(View.GONE,outside.getVisibility());equal(true,handler.delayed==null);equal(false,controller.drawContent(battery,new Canvas(),rect));
        meter.charge.isVisible=true;battery.isShowPercentIn=false;outside.setVisibility(View.VISIBLE);controller.sync(meter);equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);
        battery.isShowPercentIn=true;controller.sync(meter);equal(View.GONE,outside.getVisibility());
        battery.level=-1;controller.sync(meter);equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);equal(false,controller.drawContent(battery,new Canvas(),rect));
        for(int level:new int[]{0,1,10,99,100}) {
            battery.level=level;controller.sync(meter);equal(View.GONE,outside.getVisibility());
            SystemClock.uptime+=4000;canvas=new Canvas();equal(true,controller.drawContent(battery,canvas,rect));
        }
        battery.isShowPercentIn=false;controller.sync(meter);equal(View.VISIBLE,outside.getVisibility());
        battery.isShowPercentIn=true;controller.sync(meter);equal(View.GONE,outside.getVisibility());
        controller.detach(meter);equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);equal(false,controller.drawContent(battery,new Canvas(),rect));
        controller.attach(meter);controller.sync(meter,meter.charge,outside);equal(View.GONE,outside.getVisibility());
        Horizontal replacement=new Horizontal();meter.drawable=replacement;replacement.setCallback(meter);controller.sync(meter);
        equal(false,controller.drawContent(battery,new Canvas(),rect));equal(true,controller.drawContent(replacement,new Canvas(),rect));
        meter.drawable=new Bar();controller.sync(meter);equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);
        independentColors();
        System.out.println("BatteryControlsCheck passed: "+checks);
    }
    private static void independentColors() throws Exception {
        Handler handler=new Handler(Looper.getMainLooper());
        BatteryControls controller=new BatteryControls(handler,Meter.class,Horizontal.class,Charge.class);
        Meter meter=new Meter(new Context());Horizontal battery=new Horizontal();meter.drawable=battery;battery.setCallback(meter);
        View outside=new View(meter.getContext());controller.sync(meter,meter.charge,outside);
        Map<String,Integer> colors=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        Map<String,Boolean> alpha=new HashMap<>();Bundle settings=new Bundle();
        colors.put("battery_color_dark",0xfff59e0b);
        colors.put("battery_text_color_dark",0x6633ccff);alpha.put("battery_text_color_dark",true);
        colors.put("battery_bolt_color_dark",0xffff4455);
        controller.configure(settings,colors,alpha);Canvas canvas=new Canvas();RectF rect=new RectF(0,0,50,20);
        equal(true,controller.drawContent(battery,canvas,rect));equal(0xccff4455,canvas.pathColor);equal(204,canvas.pathAlpha);
        equal(0xccf59e0b,battery.outlineColor);equal(0xff00bd13,battery.progressColor);equal(255,canvas.maskAlpha);
        SystemClock.uptime+=4000;canvas=new Canvas();controller.drawContent(battery,canvas,rect);
        equal(-1,canvas.pathAlpha);equal(102,canvas.textAlpha);equal(0x6633ccff,canvas.textColor);
        meter.charge.isVisible=false;controller.sync(meter);canvas=new Canvas();
        equal(true,controller.drawContent(battery,canvas,rect));equal("85",canvas.text);equal(-1,canvas.pathAlpha);equal(102,canvas.textAlpha);
        meter.charge.isVisible=true;controller.sync(meter);settings.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,false);
        controller.configure(settings,colors,alpha);canvas=new Canvas();
        equal(View.VISIBLE,outside.getVisibility());equal(true,controller.drawContent(battery,canvas,rect));equal(-1,canvas.pathAlpha);equal(102,canvas.textAlpha);
        settings.putBoolean("battery_enabled",false);controller.configure(settings,colors,alpha);canvas=new Canvas();
        equal(View.VISIBLE,outside.getVisibility());equal(true,handler.delayed==null);
        equal(false,controller.drawContent(battery,canvas,rect));equal(0xccffffff,battery.outlineColor);
        equal(0xff00bd13,battery.progressColor);equal(0x4dffffff,battery.backgroundColor);
        settings.putBoolean("battery_enabled",true);settings.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,true);
        controller.configure(settings,colors,alpha);canvas=new Canvas();
        equal(View.GONE,outside.getVisibility());equal(true,handler.delayed!=null);
        equal(true,controller.drawContent(battery,canvas,rect));equal(0xccff4455,canvas.pathColor);equal(255,canvas.maskAlpha);
        settings.putBoolean("battery_bolt_color_enabled",false);controller.configure(settings,colors,alpha);canvas=new Canvas();
        equal(true,controller.drawContent(battery,canvas,rect));equal(0xccffffff,canvas.pathColor);
        settings.putBoolean("battery_text_color_enabled",false);settings.putBoolean(StatusBarSettings.BATTERY_CHARGE_INSIDE,false);
        controller.configure(settings,colors,alpha);
        equal(false,controller.drawContent(battery,new Canvas(),rect));equal(View.VISIBLE,outside.getVisibility());
    }
}
