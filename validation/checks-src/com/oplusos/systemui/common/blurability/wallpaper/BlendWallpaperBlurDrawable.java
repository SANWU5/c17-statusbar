package com.oplusos.systemui.common.blurability.wallpaper;
import android.graphics.*;
import android.graphics.drawable.Drawable;
public class BlendWallpaperBlurDrawable extends Drawable {
    public final com.oplus.posteffect.drawable.BlendDrawable blendDrawable=new com.oplus.posteffect.drawable.BlendDrawable();
    public void draw(Canvas c){blendDrawable.draw(c);}
    public void setAlpha(int v){}
    public void setColorFilter(ColorFilter c){}
    public int getOpacity(){return 0;}
}
