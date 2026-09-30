package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;

/** Uses the actual native field shapes to exercise PUI resources and lossless switching. */
public final class PuiBatteryStyleCheck {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    private static void equal(int expected,int actual){check(expected==actual,expected+" != "+actual);}
    public static class Graphic extends Drawable {
        public int tint;
        public void draw(Canvas canvas){ }
        public void setAlpha(int alpha){ }
        public void setColorFilter(ColorFilter filter){ }
        public int getOpacity(){return 0;}
        public void setTint(int color){tint=color;}
    }
    public static class NativeResources extends Resources {
        public int getIdentifier(String name,String kind,String pkg){return "dimen".equals(kind)?1:0;}
        public float getDimension(int id){return 116;}
    }
    public static class PuiResources extends Resources {
        public int getIdentifier(String name,String kind,String pkg){return name.startsWith("c17_pui_battery_")?1:0;}
        public Drawable getDrawable(int id,Theme theme){return new Graphic();}
    }
    public static class NativeContext extends Context {
        public Resources getResources(){return new NativeResources();}
        public Context createPackageContext(String name,int flags){return new Context(){public Resources getResources(){return new PuiResources();}};}
    }
    public static class NativeBar extends Graphic {
        public Drawable outsideDrawable=new Graphic(),frameDrawable=new Graphic();
        public LayerDrawable insideDrawable=new LayerDrawable(new Drawable[]{new Graphic()});
        public RectF rect=new RectF(8,8,108,60);
        public int progressColor=0xff22c55e,backgroundColor=0x4d000000,outlineColor=0xe6000000,batteryLevel=85;
        public int tintCalls;
        public Context getContext(){return new NativeContext();}
        public void updateDrawableTint(){tintCalls++;}
    }
    public static class NativeHorizontal extends NativeBar {
        public Drawable bgDrawable=new Graphic();
        public LayerDrawable progressDrawable=new LayerDrawable(new Drawable[]{new Graphic()});
        public RectF noPercentRectF=new RectF(0,0,116,68);
        public Paint percentInPaint=new Paint(1);
        public int measures;
        public void updatePercentInTextBounds(){measures++;}
    }
    public static void main(String[] args) throws Exception {
        NativeHorizontal battery=new NativeHorizontal();battery.percentInPaint.setTextSize(38);
        Drawable outside=battery.outsideDrawable,frame=battery.frameDrawable,inside=battery.insideDrawable;
        Drawable outer=battery.bgDrawable,outerFill=battery.progressDrawable;
        PuiBatteryStyle style=new PuiBatteryStyle(NativeHorizontal.class);Bundle settings=new Bundle();style.configure(settings);
        check(style.prepare(battery),"default PUI style did not apply");
        check(battery.outsideDrawable!=outside&&battery.insideDrawable!=inside,"native assets were not replaced");
        equal(0x4d000000,((Graphic)battery.outsideDrawable).tint);
        equal(0xff22c55e,((Graphic)((android.graphics.drawable.ClipDrawable)battery.insideDrawable.getDrawable(0)).getWrapped()).tint);
        equal(8500,battery.insideDrawable.getLevel());equal(8500,battery.progressDrawable.getLevel());
        equal(8,battery.outsideDrawable.getBounds().left);equal(108,battery.outsideDrawable.getBounds().right);
        check(battery.percentInPaint.getTextSize()==40,"PUI 10 dp font not applied");equal(1,battery.measures);
        Drawable pui=battery.outsideDrawable;style.prepare(battery);check(battery.outsideDrawable==pui,"repeated draw rebuilt PUI vector");equal(1,battery.measures);
        battery.batteryLevel=7;style.prepare(battery);equal(700,battery.insideDrawable.getLevel());
        ViewGroup owner=new ViewGroup(new NativeContext());ImageView image=new ImageView(owner.getContext());owner.addView(image);
        image.setImageDrawable(battery);image.setLayoutParams(new ViewGroup.LayoutParams(116,64));
        style.updateView(owner,battery);equal(116,image.getLayoutParams().width);equal(68,image.getLayoutParams().height);
        settings.putString(StatusBarSettings.BATTERY_STYLE,"native");style.configure(settings);
        check(battery.outsideDrawable==outside&&battery.frameDrawable==frame&&battery.insideDrawable==inside,"native inside assets not restored");
        check(battery.bgDrawable==outer&&battery.progressDrawable==outerFill,"native outside assets not restored");
        check(battery.percentInPaint.getTextSize()==38,"native font not restored");equal(2,battery.measures);
        equal(64,image.getLayoutParams().height);check(!style.prepare(battery),"native mode still applies PUI");
        settings.putString(StatusBarSettings.BATTERY_STYLE,"pui");style.configure(settings);style.prepare(battery);style.updateView(owner,battery);
        Drawable latestNative=new Graphic();battery.outsideDrawable=latestNative;style.prepare(battery);
        settings.putBoolean("battery_style_enabled",false);style.configure(settings);
        check(battery.outsideDrawable==latestNative,"switching off restored an obsolete native theme");equal(64,image.getLayoutParams().height);
        settings.putBoolean("battery_style_enabled",true);style.configure(settings);style.prepare(battery);style.updateView(owner,battery);
        settings.putBoolean("battery_enabled",false);style.configure(settings);
        check(battery.outsideDrawable==latestNative,"group master did not restore native shape");equal(64,image.getLayoutParams().height);
        settings.putBoolean("battery_enabled",true);style.configure(settings);style.prepare(battery);style.updateView(owner,battery);
        style.detach(owner,battery);check(battery.outsideDrawable==latestNative,"detach retained PUI objects");equal(64,image.getLayoutParams().height);
        nativeScaleFactor();
        System.out.println("PuiBatteryStyleCheck passed: "+checks);
    }
    private static void nativeScaleFactor() throws Exception {
        NativeHorizontal battery=new NativeHorizontal();PuiBatteryStyle style=new PuiBatteryStyle(NativeHorizontal.class);
        style.configure(new Bundle());style.prepare(battery);
        ViewGroup owner=new ViewGroup(new NativeContext());ImageView image=new ImageView(owner.getContext());owner.addView(image);
        image.setImageDrawable(battery);image.setLayoutParams(new ViewGroup.LayoutParams(87,48));
        style.updateView(owner,battery);equal(87,image.getLayoutParams().width);equal(51,image.getLayoutParams().height);
        check(battery.percentInPaint.getTextSize()==40,"native scale factor incorrectly multiplies native percentage font");
        style.detach(owner,battery);equal(48,image.getLayoutParams().height);
    }
}
