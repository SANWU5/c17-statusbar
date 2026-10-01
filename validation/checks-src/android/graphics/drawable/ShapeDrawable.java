package android.graphics.drawable;
import android.graphics.*;
/** Native light-blur fallback keeps its shape while exposing only its base Paint. */
public class ShapeDrawable extends Drawable {
    private final Paint paint=new Paint(1);
    public Shader seenShader;
    public int seenAlpha;
    public Paint getPaint(){return paint;}
    public void draw(Canvas canvas){seenShader=paint.getShader();seenAlpha=paint.getAlpha();}
    public void setAlpha(int value){paint.setAlpha(value);}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
