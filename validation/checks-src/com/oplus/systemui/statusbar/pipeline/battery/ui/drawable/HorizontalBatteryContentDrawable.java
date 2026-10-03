package com.oplus.systemui.statusbar.pipeline.battery.ui.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** Interior text and charge drawing stay independent of the native graphic shape fields. */
public final class HorizontalBatteryContentDrawable extends BatteryBarDrawableBase {
    public Drawable bgDrawable;
    public LayerDrawable progressDrawable;
    public boolean isShowPercentIn=true,failContent;
    public final RectF noPercentRectF=new RectF(2,7,60,33);
    public final Paint percentInPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public int textDraws,chargeDraws;
    public float customX=3f,customY=-2f;
    @Override public void draw(Canvas canvas){
        if(batteryLevel<0)return;
        if(isShowPercentIn)super.draw(canvas);
        else {bgDrawable.draw(canvas);progressDrawable.draw(canvas);if(chargeIconId>0)charge(canvas);}
    }
    @Override public void drawContent(Canvas canvas,RectF bounds){
        if(failContent)throw new IllegalStateException("Native content failed");
        textDraws++;canvas.drawText(Integer.toString(batteryLevel),bounds.centerX()+customX,bounds.centerY()+customY,percentInPaint);
        if(chargeIconId>0)charge(canvas);
    }
    private void charge(Canvas canvas){chargeDraws++;canvas.drawRect(0,0,2,2,percentInPaint);}
}
