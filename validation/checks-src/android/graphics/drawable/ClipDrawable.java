package android.graphics.drawable;
public class ClipDrawable extends Drawable {
    public static final int HORIZONTAL=1;
    private final Drawable drawable;
    public ClipDrawable(Drawable drawable,int gravity,int orientation){this.drawable=drawable;}
    public Drawable getWrapped(){return drawable;}
    public void draw(android.graphics.Canvas canvas){drawable.draw(canvas);}
    public void setAlpha(int value){drawable.setAlpha(value);}
    public void setColorFilter(android.graphics.ColorFilter filter){drawable.setColorFilter(filter);}
    public int getOpacity(){return 0;}
    public void setTint(int color){drawable.setTint(color);}
}
