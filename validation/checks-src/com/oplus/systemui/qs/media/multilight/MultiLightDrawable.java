package com.oplus.systemui.qs.media.multilight;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
/** Pure optical drawable called after TileTransitionDrawable by the native media parent. */
public class MultiLightDrawable extends Drawable {
    public int draws;
    public void draw(Canvas canvas){draws++;}
    public void setAlpha(int value){}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return -3;}
}
