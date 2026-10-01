package android.graphics.drawable;
import android.graphics.*;
/** Native solid/stroke separation and existing-gradient fixture for background isolation checks. */
public class GradientDrawable extends Drawable {
    public static final class GradientState {public int[] mColors;public Object mGradientColors;}
    public final GradientState mGradientState=new GradientState();
    public final Paint mFillPaint=new Paint(1),mStrokePaint=new Paint(1);
    public Shader seenShader,seenStroke;
    public int seenAlpha;
    public void setColor(int value){mFillPaint.setColor(value);}
    private void ensureValidRect(){}
    public void draw(Canvas canvas){seenShader=mFillPaint.getShader();seenAlpha=mFillPaint.getAlpha();seenStroke=mStrokePaint.getShader();}
    public void setAlpha(int value){mFillPaint.setAlpha(value);}
    public void setColorFilter(ColorFilter value){}
    public int getOpacity(){return 0;}
}
