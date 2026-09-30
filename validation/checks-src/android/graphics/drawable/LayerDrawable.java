package android.graphics.drawable;
public class LayerDrawable extends Drawable {
    private final Drawable[] drawables;
    public LayerDrawable(Drawable[] drawables){this.drawables=drawables;}
    public void setId(int index,int id){ }
    public Drawable getDrawable(int index){return drawables[index];}
    public void draw(android.graphics.Canvas canvas){for(Drawable value:drawables)value.draw(canvas);}
    public void setAlpha(int value){for(Drawable drawable:drawables)drawable.setAlpha(value);}
    public void setColorFilter(android.graphics.ColorFilter filter){for(Drawable drawable:drawables)drawable.setColorFilter(filter);}
    public int getOpacity(){return 0;}
    public void setTint(int color){for(Drawable drawable:drawables)drawable.setTint(color);}
    public boolean setLevel(int level){super.setLevel(level);for(Drawable drawable:drawables)drawable.setLevel(level);return true;}
}
