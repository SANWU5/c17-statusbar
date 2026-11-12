package android.graphics.drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
public abstract class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable drawable);
        void scheduleDrawable(Drawable drawable, Runnable task, long when);
        void unscheduleDrawable(Drawable drawable, Runnable task);
    }
    private Callback callback;
    private final Rect bounds = new Rect();
    private int[] state=new int[0];
    private int level;
    private int layoutDirection;
    private boolean visibleForCheck=true;
    public abstract void draw(Canvas canvas);
    public abstract void setAlpha(int alpha);
    public abstract void setColorFilter(ColorFilter filter);
    public int getAlpha(){return 255;}
    public ColorFilter getColorFilter(){return null;}
    public void setColorFilter(int color,android.graphics.PorterDuff.Mode mode){setColorFilter(new android.graphics.PorterDuffColorFilter(color,mode));}
    protected boolean onStateChange(int[] value){return false;}
    protected boolean onLevelChange(int value){return false;}
    public boolean setVisible(boolean visible,boolean restart){boolean changed=visibleForCheck!=visible;visibleForCheck=visible;return changed;}
    public boolean isVisible(){return visibleForCheck;}
    public final boolean setLayoutDirection(int value){if(layoutDirection==value)return false;layoutDirection=value;return onLayoutDirectionChanged(value);}
    public int getLayoutDirection(){return layoutDirection;}
    public boolean onLayoutDirectionChanged(int value){return false;}
    public void setAutoMirrored(boolean value){}
    public boolean isAutoMirrored(){return false;}
    public void setHotspot(float x,float y){}
    public void setHotspotBounds(int left,int top,int right,int bottom){}
    public void getHotspotBounds(Rect out){out.set(bounds);}
    public void jumpToCurrentState(){}
    public void scheduleSelf(Runnable task,long when){if(callback!=null)callback.scheduleDrawable(this,task,when);}
    public void unscheduleSelf(Runnable task){if(callback!=null)callback.unscheduleDrawable(this,task);}
    public abstract int getOpacity();
    public int getIntrinsicWidth() { return 72; }
    public int getIntrinsicHeight() { return 56; }
    public void setCallback(Callback callback) { this.callback = callback; }
    public Callback getCallback() { return callback; }
    public Rect getBounds() { return bounds; }
    public final void copyBounds(Rect output) { output.set(bounds.left,bounds.top,bounds.right,bounds.bottom); }
    public void setBounds(int l, int t, int r, int b) { bounds.left=l; bounds.top=t; bounds.right=r; bounds.bottom=b;onBoundsChange(bounds); }
    public void setBounds(Rect value){setBounds(value.left,value.top,value.right,value.bottom);}
    protected void onBoundsChange(Rect value) { }
    public int[] getState() {return state;}
    public boolean setState(int[] value) {state=value;return false;}
    public int getLevel() {return level;}
    public boolean setLevel(int value) {level=value;return false;}
    public void setTint(int color) { }
    public Drawable mutate() {return this;}
    public void setTintList(android.content.res.ColorStateList colors) { }
    public void setTintMode(android.graphics.PorterDuff.Mode mode) { }
    public void setTintBlendMode(android.graphics.BlendMode mode) { }
    public int getMinimumWidth() {return getIntrinsicWidth();}
    public int getMinimumHeight() {return getIntrinsicHeight();}
    public boolean getPadding(Rect result) {return false;}
    public boolean isStateful() {return false;}
    public void invalidateSelf() { if (callback != null) callback.invalidateDrawable(this); }
    public Drawable getCurrent(){return this;}
    public void getOutline(android.graphics.Outline outline){}
    public ConstantState getConstantState(){return null;}
    public abstract static class ConstantState {
        public abstract Drawable newDrawable();
        public Drawable newDrawable(android.content.res.Resources resources){return newDrawable();}
    }
}
