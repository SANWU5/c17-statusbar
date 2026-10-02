package android.graphics.drawable;
import android.graphics.*;
import android.content.res.Resources;
public class BitmapDrawable extends Drawable {
    private final Bitmap bitmap;
    public BitmapDrawable(Resources resources,Bitmap bitmap){this.bitmap=bitmap;}
    public Bitmap getBitmap(){return bitmap;}
    public void draw(Canvas canvas){canvas.drawBitmap(bitmap,null,new RectF(0,0,bitmap.getWidth(),bitmap.getHeight()),new Paint(1));}
    public void setAlpha(int alpha){} public void setColorFilter(ColorFilter filter){} public int getOpacity(){return 0;}
}
