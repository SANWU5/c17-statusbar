package com.oplus.systemui.qs.base.res.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
public class MixColorTileDrawable extends Drawable {
    public final Paint paint=new Paint(1),highlightPaint=new Paint(1);
    public int maskColor=0xffffffff,alpha=255,draws;
    public Shader seenShader,seenHighlight;
    public Drawable child;
    public Drawable getDrawable(){return child;}
    public int seenAlpha;
    public void draw(Canvas canvas){draws++;paint.setColor((maskColor&0xffffff)|((maskColor>>>24)*alpha/255<<24));seenShader=paint.getShader();seenAlpha=paint.getAlpha();seenHighlight=highlightPaint.getShader();canvas.drawPath(new Path(),paint);}
    public void setAlpha(int value){alpha=value;}
    public int getAlpha(){return alpha;}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
