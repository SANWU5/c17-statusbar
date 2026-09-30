package com.oplus.posteffect.drawable;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import com.oplus.posteffect.agsl.DrawableShader;
public class BlendDrawable extends Drawable {
    public final Object dataLock=new Object();
    public final DrawableShader drawableShader=new DrawableShader();
    public final Paint drawableShaderPaint=new Paint(1);
    public boolean enableShader=true,contentDirty;
    public RuntimeShader recorded;
    public void onDrawContent(Canvas canvas){drawableShaderPaint.setShader(drawableShader.shader);recorded=drawableShader.shader;}
    public void draw(Canvas canvas){}
    public void setAlpha(int value){}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
