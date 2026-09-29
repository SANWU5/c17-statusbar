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
        settings=new Bundle();settings.putFloat(StatusBarSettings.BATTERY_OFFSET_X,Float.NaN);settings.putFloat(StatusBarSettings.BATTERY_SCALE,Float.POSITIVE_INFINITY);
        settings.putFloat(StatusBarSettings.BATTERY_WIDTH_SCALE,300f);settings.putFloat(StatusBarSettings.BATTERY_HEIGHT_SCALE,0f);
        style.configure(settings,StatusBarSettings.COLOR_DEFAULTS,new HashMap<>());canvas=new Canvas();style.beforeDraw(owner,canvas);
        near(0,canvas.translateX);near(2.5f,canvas.scaleX);near(.25f,canvas.scaleY);
        equal(0f,StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.BATTERY_OFFSET_X));
        equal(100f,StatusBarSettings.NUMERIC_DEFAULTS.get(StatusBarSettings.BATTERY_SCALE));
        System.out.println("BatteryAppearanceCheck passed: "+checks);
    }
}
