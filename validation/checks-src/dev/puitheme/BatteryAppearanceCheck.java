package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;
import java.util.HashMap;
import java.util.Map;

/** Palette restoration and unified battery transform without replacing its native design. */
public final class BatteryAppearanceCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static void near(float expected,float actual) {checks++;if(Math.abs(expected-actual)>.0001f)throw new AssertionError(expected+" != "+actual);}
    public static final class Bar extends Drawable {
        public int progressColor,backgroundColor,outlineColor;
        public void setColors(int progress,int background,int outline) {progressColor=progress;backgroundColor=background;outlineColor=outline;}
        public void draw(Canvas canvas) { }
        public void setAlpha(int alpha) { }
        public void setColorFilter(ColorFilter filter) { }
        public int getOpacity() {return 0;}
    }
    private static void update(BatteryAppearance style,Bar bar,int progress,int background,int outline,boolean charging) {
        Object[] colors=style.nativeColors(bar,progress,background,outline,charging);
        bar.setColors((Integer)colors[0],(Integer)colors[1],(Integer)colors[2]);
    }
    public static void main(String[] args) throws Exception {
        BatteryAppearance style=new BatteryAppearance(Bar.class);Bar bar=new Bar();
        Map<String,Integer> colors=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        Map<String,Boolean> alpha=new HashMap<>();Bundle settings=new Bundle();
        style.configure(settings,colors,alpha);
        for(int scene:new int[]{0xe6000000,0xccffffff,0xaf808080}) {
            update(style,bar,0xff00bd13,0x4dffffff,scene,true);
            equal(0xff00bd13,bar.progressColor);equal(0x4dffffff,bar.backgroundColor);equal(scene,bar.outlineColor);
            style.apply(bar,true);equal(scene,bar.outlineColor);
        }
        colors.put("battery_color_light",0xff2869e8);colors.put("battery_color_dark",0xffffaabb);
        style.configure(settings,colors,alpha);
        update(style,bar,0xff00bd13,0x4d000000,0xe6000000,true);
        equal(0x4d000000,bar.backgroundColor);equal(0xe6000000,bar.outlineColor);
        settings.putBoolean("battery_enabled",true);style.configure(settings,colors,alpha);
        update(style,bar,0xff00bd13,0x4d000000,0xe6000000,true);
        equal(0xff00bd13,bar.progressColor);equal(0x4d2869e8,bar.backgroundColor);equal(0xe62869e8,bar.outlineColor);
        equal(0xcc2869e8,style.contentTint("battery",bar,0xccffffff));
        style.apply(bar,true);equal(0xe62869e8,bar.outlineColor);
        update(style,bar,0xccffffff,0x4dffffff,0xccffffff,false);
        equal(0xccffaabb,bar.progressColor);equal(0x4dffaabb,bar.backgroundColor);equal(0xccffaabb,bar.outlineColor);
        update(style,bar,0xfff5c400,0x4dffffff,0xccffffff,false);
        equal(0xfff5c400,bar.progressColor);
        colors.put("battery_alert_color_dark",0xffbf56ea);style.configure(settings,colors,alpha);style.apply(bar,false);
        equal(0xffbf56ea,bar.progressColor);
        colors.put("battery_charge_color_light",0x800066ff);alpha.put("battery_charge_color_light",true);
        style.configure(settings,colors,alpha);update(style,bar,0xff00bd13,0x4d000000,0xe6000000,true);
        equal(0x800066ff,bar.progressColor);equal(0xe62869e8,bar.outlineColor);
        colors.put("battery_color_light",0x55010203);alpha.put("battery_color_light",true);
        style.configure(settings,colors,alpha);style.apply(bar,true);
        equal(0x55010203,bar.outlineColor);equal(0x1c010203,bar.backgroundColor);equal(0x800066ff,bar.progressColor);
        equal(0x4b010203,style.contentTint("battery",bar,0xccffffff));
        equal(0xe6000000,style.nativeOutline(bar));
        equal(0xccffffff,style.contentTint("battery_text",bar,0xccffffff));
        equal(0xe6000000,style.contentTint("battery_bolt",bar,0xe6000000));
        colors.put("battery_text_color_light",0xff112233);colors.put("battery_bolt_color_light",0x88445566);
        alpha.put("battery_bolt_color_light",true);style.configure(settings,colors,alpha);
        equal(true,style.hasCustom("battery_text"));equal(true,style.hasCustom("battery_bolt"));
        equal(0xcc112233,style.contentTint("battery_text",bar,0xccffffff));
        equal(0x88445566,style.contentTint("battery_bolt",bar,0xe6000000));
        equal(0x55010203,bar.outlineColor);equal(0x800066ff,bar.progressColor);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());style.apply(bar,true);
        equal(0xff00bd13,bar.progressColor);equal(0x4d000000,bar.backgroundColor);equal(0xe6000000,bar.outlineColor);
        ViewGroup owner=new ViewGroup(new Context()),other=new ViewGroup(new Context()),parent=new ViewGroup(new Context());
        owner.parent=parent;other.parent=parent;
        Canvas canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(0,canvas.translateX);near(0,canvas.translateY);near(1,canvas.scaleX);near(1,canvas.scaleY);
        equal(true,owner.getClipChildren());equal(true,parent.getClipToPadding());
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,-3.27f);settings.putFloat(StatusBarSettings.BATTERY_OFFSET_Y,1.83f);
        settings.putFloat(StatusBarSettings.BATTERY_SCALE,112.37f);settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE,103.29f);settings.putFloat(StatusBarSettings.BATTERY_HEIGHT_SCALE,98.71f);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(-13.08f,canvas.translateX);near(7.32f,canvas.translateY);near(1.1237f*1.0329f,canvas.scaleX);near(1.1237f*.9871f,canvas.scaleY);
        near(62,canvas.pivotX);near(40,canvas.pivotY);equal(false,owner.getClipChildren());equal(false,parent.getClipToPadding());
        style.beforeDraw(other,new Canvas());style.detach(owner);equal(false,parent.getClipChildren());equal(true,owner.getClipChildren());
        style.detach(other);equal(true,parent.getClipChildren());equal(true,parent.getClipToPadding());
        style.beforeDraw(owner,new Canvas());style.configure(new Bundle(),StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());
        equal(true,owner.getClipChildren());equal(true,parent.getClipToPadding());
        settings=new Bundle();settings.putBoolean("battery_enabled",true);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,Float.NaN);settings.putFloat(StatusBarSettings.BATTERY_SCALE,Float.POSITIVE_INFINITY);
        settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE,300f);settings.putFloat(StatusBarSettings.BATTERY_HEIGHT_SCALE,0f);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(0,canvas.translateX);near(3f,canvas.scaleX);near(0f,canvas.scaleY);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,600.75f);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_Y,-120.25f);
        settings.putFloat(StatusBarSettings.BATTERY_SCALE,10f);
        settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE,100f);
        settings.putFloat(StatusBarSettings.BATTERY_HEIGHT_SCALE,100f);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(2403f,canvas.translateX);near(-481f,canvas.translateY);near(.1f,canvas.scaleX);near(.1f,canvas.scaleY);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,Float.MAX_VALUE);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_Y,-Float.MAX_VALUE);
        settings.putFloat(StatusBarSettings.BATTERY_SCALE,Float.MAX_VALUE);
        settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE,Float.MAX_VALUE);
        settings.putFloat(StatusBarSettings.BATTERY_HEIGHT_SCALE,Float.MAX_VALUE);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(NumericPolicy.MAX_DRAW_PIXELS,canvas.translateX);near(-NumericPolicy.MAX_DRAW_PIXELS,canvas.translateY);
        near(NumericPolicy.MAX_DRAW_PIXELS/owner.getWidth(),canvas.scaleX);
        near(NumericPolicy.MAX_DRAW_PIXELS/owner.getHeight(),canvas.scaleY);
        equal(0f,StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.BATTERY_OFFSET_X));
        equal(100f,StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.BATTERY_SCALE));
        independentSwitches();
        System.out.println("BatteryAppearanceCheck passed: "+checks);
    }
    private static void independentSwitches() throws Exception {
        BatteryAppearance style=new BatteryAppearance(Bar.class);Bar bar=new Bar();
        Map<String,Integer> colors=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        Map<String,Boolean> alpha=new HashMap<>();Bundle settings=new Bundle();
        for(String item:new String[]{"battery","battery_text","battery_bolt","battery_charge","battery_alert"}) {
            colors.put(item+"_color_light",0x80112233);alpha.put(item+"_color_light",true);
        }
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,7.25f);
        settings.putFloat(StatusBarSettings.BATTERY_OFFSET_Y,-1.5f);
        settings.putFloat(StatusBarSettings.BATTERY_SCALE,125f);
        style.configure(settings,colors,alpha);update(style,bar,0xff00bd13,0x4d000000,0xe6000000,true);
        equal(0xff00bd13,bar.progressColor);equal(0xe6000000,bar.outlineColor);
        settings.putBoolean("battery_enabled",true);style.configure(settings,colors,alpha);
        update(style,bar,0xff00bd13,0x4d000000,0xe6000000,true);
        equal(0x80112233,bar.progressColor);equal(0x80112233,bar.outlineColor);
        settings.putBoolean("battery_charge_color_enabled",false);style.configure(settings,colors,alpha);style.apply(bar,true);
        equal(0xff00bd13,bar.progressColor);equal(0x80112233,bar.outlineColor);
        settings.putBoolean("battery_text_color_enabled",false);settings.putBoolean("battery_bolt_color_enabled",false);
        style.configure(settings,colors,alpha);
        equal(false,style.hasCustom("battery_text"));equal(false,style.hasCustom("battery_bolt"));
        equal(0xccffffff,style.contentTint("battery_text",bar,0xccffffff));
        equal(0xe6000000,style.contentTint("battery_bolt",bar,0xe6000000));
        settings.putBoolean("battery_alert_color_enabled",false);style.configure(settings,colors,alpha);
        update(style,bar,0xffff3b30,0x4d000000,0xe6000000,false);equal(0xffff3b30,bar.progressColor);
        settings.putBoolean("battery_color_enabled",false);style.configure(settings,colors,alpha);style.apply(bar,false);
        equal(0xffff3b30,bar.progressColor);equal(0x4d000000,bar.backgroundColor);equal(0xe6000000,bar.outlineColor);
        ViewGroup owner=new ViewGroup(new Context()),parent=new ViewGroup(new Context());owner.parent=parent;
        Canvas canvas=new Canvas();style.beforeDraw(owner,canvas);near(29,canvas.translateX);near(1.25f,canvas.scaleX);
        settings.putBoolean("battery_position_enabled",false);style.configure(settings,colors,alpha);canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(0,canvas.translateX);near(0,canvas.translateY);near(1.25f,canvas.scaleX);
        settings.putBoolean("battery_size_enabled",false);style.configure(settings,colors,alpha);canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(1,canvas.scaleX);near(1,canvas.scaleY);equal(true,owner.getClipChildren());equal(true,parent.getClipChildren());
        settings.putBoolean("battery_position_enabled",true);settings.putBoolean("battery_size_enabled",true);
        settings.putBoolean("battery_color_enabled",true);settings.putBoolean("battery_charge_color_enabled",true);
        style.configure(settings,colors,alpha);style.apply(bar,true);canvas=new Canvas();style.beforeDraw(owner,canvas);
        equal(0x80112233,bar.progressColor);near(29,canvas.translateX);near(1.25f,canvas.scaleX);
        settings.putBoolean("battery_enabled",false);style.configure(settings,colors,alpha);style.apply(bar,true);canvas=new Canvas();style.beforeDraw(owner,canvas);
        equal(0xffff3b30,bar.progressColor);equal(0x4d000000,bar.backgroundColor);equal(0xe6000000,bar.outlineColor);
        near(0,canvas.translateX);near(1,canvas.scaleX);equal(true,parent.getClipChildren());equal(false,style.hasCustom("battery"));
        settings.putBoolean("battery_enabled",true);style.configure(settings,colors,alpha);style.apply(bar,true);canvas=new Canvas();style.beforeDraw(owner,canvas);
        equal(0x80112233,bar.progressColor);near(29,canvas.translateX);near(1.25f,canvas.scaleX);
        ViewGroup wifi=new ViewGroup(new Context());wifi.parent=parent;
        OverflowControls.shared().acquire(wifi,true);
        style.detach(owner);equal(false,parent.getClipChildren());
        OverflowControls.shared().release(wifi);equal(true,parent.getClipChildren());
    }
}
