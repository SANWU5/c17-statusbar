package com.oplus.systemui.qs.base.res.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
public class GradientTileDrawable extends Drawable {
    public boolean deforming;
    public final Animator animator=new Animator();
    public static final class Animator {public boolean running;public boolean isRunning(){return running;}}
    public boolean isDeforming(){return deforming;}
    public static final class Color {public int color=0xffffffff;public int getColor(){return color;}}
    public final Color colorDrawable=new Color();
    public final Paint paint=new Paint(1);
    public Shader seenShader;
    public void draw(Canvas canvas){paint.setColor(colorDrawable.getColor());seenShader=paint.getShader();canvas.drawPath(new Path(),paint);}
    public void setAlpha(int value){colorDrawable.color=(colorDrawable.color&0xffffff)|(value<<24);}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
