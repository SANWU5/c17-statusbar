package com.oplus.systemui.statusbar.pipeline.battery.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** Typed native slots and draw ordering verified in the device's SystemUI DEX. */
public abstract class BatteryBarDrawableBase extends Drawable {
    public Drawable outsideDrawable,frameDrawable;
    public LayerDrawable insideDrawable;
    public final RectF rect=new RectF(4,3,62,29);
    public int batteryLevel=84,chargeIconId,outlineColor=0xffeeeeee;
    public int getBatteryLevel(){return batteryLevel;}
    public int getChargeIconId(){return chargeIconId;}
    public int getOutlineColor(){return outlineColor;}
    @Override public void setAlpha(int alpha){ }
    @Override public void setColorFilter(ColorFilter filter){ }
    @Override public int getOpacity(){return -3;}
    @Override public void draw(Canvas canvas){
        outsideDrawable.draw(canvas);insideDrawable.draw(canvas);frameDrawable.draw(canvas);
        drawContent(canvas,rect);
    }
    public abstract void drawContent(Canvas canvas,RectF bounds);
}
