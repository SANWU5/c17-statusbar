package com.oplusos.systemui.common.blurability.platformblur;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.oplus.posteffect.drawable.BlendDrawable;
public class PlatformBlurDrawable extends Drawable {
    public final BlendDrawable blurDrawable=new BlendDrawable();
    public void draw(Canvas canvas){blurDrawable.draw(canvas);}
    public void setAlpha(int value){}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
