package com.oplusos.systemui.common.blurability.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
public class MaskBlurDrawable extends Drawable {
    public final com.oplusos.systemui.common.blurability.ViewBlurProxy viewBlurProxy;
    public MaskBlurDrawable(com.oplusos.systemui.common.blurability.ViewBlurProxy p){viewBlurProxy=p;}
    public void draw(Canvas c){Drawable d=viewBlurProxy.getBlurDrawable(null);if(d!=null)d.draw(c);}
    public void setAlpha(int v){}
    public void setColorFilter(ColorFilter c){}
    public int getOpacity(){return 0;}
}
